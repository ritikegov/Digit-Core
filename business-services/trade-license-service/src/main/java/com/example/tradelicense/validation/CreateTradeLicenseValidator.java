package com.example.tradelicense.validation;

import com.example.tradelicense.web.models.Address;
import com.example.tradelicense.web.models.TradeLicense;
import com.example.tradelicense.web.models.TradeLicenseRequest;
import com.example.tradelicense.config.TradeLicenseConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digit.services.filestore.FilestoreClient;
import org.digit.services.boundary.BoundaryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateTradeLicenseValidator {

    private final MdmsValidator mdmsValidator;
    private final FilestoreClient filestoreClient;
    private final BoundaryClient boundaryClient;

    @Value("${tl.validation.min.period:2592000000}") // 30 days in millis
    private Long minPeriod;

    @Value("${tl.validation.allow.previous:false}")
    private Boolean isPreviousTLAllowed;

    public void validate(TradeLicenseRequest request) {

        if (request == null || request.getLicenses() == null || request.getLicenses().isEmpty()) {
            throw new RuntimeException("TradeLicenseRequest cannot be empty");
        }

        // Check if this is a renewal application
        boolean isRenewal = request.getLicenses().stream()
                .anyMatch(license -> "RENEWAL".equalsIgnoreCase(license.getApplicationType()));

        if (isRenewal) {
            validateRenewal(request);
        }

        request.getLicenses().forEach(this::validateLicense);
        
        // Validate MDMS data
        mdmsValidator.validate(request);
        
        // Validate duplicate documents
        validateDuplicateDocuments(request);
        
        // Validate verification documents if present (officer-uploaded)
        request.getLicenses().forEach(license -> {
            if (license.getVerificationDocuments() != null && !license.getVerificationDocuments().isEmpty()) {
                Map<String, String> errorMap = new HashMap<>();
                license.getVerificationDocuments().forEach(doc -> {
                    if (doc.getDocumentType() == null || doc.getDocumentType().isBlank()) {
                        errorMap.put("NULL_VERIFICATION_DOCUMENT_TYPE", "Verification document type is required");
                    }
                    if (doc.getFileStoreId() == null || doc.getFileStoreId().isBlank()) {
                        errorMap.put("NULL_VERIFICATION_FILESTOREID", "FileStoreId is required for verification document");
                    } else {
                        boolean exists = filestoreClient.isFileAvailable(doc.getFileStoreId(), license.getTenantId());
                        if (!exists) {
                            errorMap.put("INVALID_VERIFICATION_FILESTOREID", "Verification document FileStoreId does not exist: " + doc.getFileStoreId());
                        }
                    }
                });
                if (!errorMap.isEmpty()) {
                    throw new RuntimeException("Verification document validation failed: " + errorMap);
                }
            }
        });
    }

    private void validateLicense(TradeLicense license) {

        Map<String,String> errorMap = new HashMap<>();

        // Required fields validation
        if (license.getTenantId() == null || license.getTenantId().isBlank())
            errorMap.put("NULL_TENANTID","TenantId is mandatory");

        if (license.getTradeName() == null || license.getTradeName().isBlank())
            errorMap.put("NULL_TRADENAME","TradeName cannot be null");

        if (license.getLicenseType() == null)
            errorMap.put("NULL_LICENSETYPE","LicenseType cannot be null");

        // MANDATORY for billing slab matching
        if (license.getStructureType() == null || license.getStructureType().isBlank())
            errorMap.put("NULL_STRUCTURETYPE","StructureType is mandatory for billing calculation");

        if (license.getApplicationType() == null || license.getApplicationType().isBlank())
            errorMap.put("NULL_APPLICATIONTYPE","ApplicationType is mandatory for billing calculation");

        if (license.getFinancialYear() == null || license.getFinancialYear().isBlank())
            errorMap.put("NULL_FINANCIALYEAR","Financial year cannot be null");

        if (license.getOwners() == null || license.getOwners().isEmpty())
            errorMap.put("NULL_OWNERS","At least one owner required");

        if (license.getTradeUnits() == null || license.getTradeUnits().isEmpty())
            errorMap.put("NULL_TRADEUNITS","At least one trade unit required");

        // Validate address (DIGIT 2.9 style - inline validation)
        validateAddress(license, errorMap);

        // Validate dates
        validateDates(license, errorMap);

        // Validate owners
        validateOwners(license, errorMap);

        // Validate documents
        validateDocuments(license, errorMap);

        if (!errorMap.isEmpty()) {
            log.error("Create validation failed: {}", errorMap);
            throw new RuntimeException("Validation failed: " + errorMap);
        }
    }

    /**
     * Validates address including boundary validation (DIGIT 2.9 style).
     */
    private void validateAddress(TradeLicense license, Map<String, String> errorMap) {
        if (license.getAddress() == null) {
            errorMap.put("NULL_ADDRESS", "Address is mandatory");
            return;
        }

        Address address = license.getAddress();
        
        // Basic address validation
        if (address.getCity() == null || address.getCity().isBlank()) {
            errorMap.put("NULL_CITY", "City cannot be null");
        }

        // Pincode validation
        if (address.getPincode() != null && !address.getPincode().isBlank()) {
            if (!address.getPincode().matches("\\d{6}")) {
                errorMap.put("INVALID_PINCODE", "Pincode must be 6 digits");
            }
        }

        // Boundary validation (DIGIT 2.9 compatibility)
        validateAddressBoundaries(license.getTenantId(), address, errorMap);
    }

    /**
     * Validates address boundaries using Boundary Service.
     * BoundaryClient handles all error cases and throws DigitClientException.
     */
    private void validateAddressBoundaries(String tenantId, Address address, Map<String, String> errorMap) {
        // Skip if no boundary codes provided
        if (isAllBoundaryCodesEmpty(address)) {
            return;
        }

        // Collect all boundary codes to validate
        List<String> codes = new ArrayList<>();
        if (address.getWardCode() != null && !address.getWardCode().isBlank()) {
            codes.add(address.getWardCode());
        }
        if (address.getZoneCode() != null && !address.getZoneCode().isBlank()) {
            codes.add(address.getZoneCode());
        }
        if (address.getLocalityCode() != null && !address.getLocalityCode().isBlank()) {
            codes.add(address.getLocalityCode());
        }

        // Validate all boundary codes exist using digit-client BoundaryClient
        if (!codes.isEmpty()) {
            var boundaries = boundaryClient.searchBoundariesByCodes(codes);
            Set<String> foundCodes = new HashSet<>();
            if (boundaries != null) {
                boundaries.forEach(boundary -> foundCodes.add(boundary.getCode()));
            }

            // Check which codes are missing
            if (address.getWardCode() != null && !foundCodes.contains(address.getWardCode())) {
                errorMap.put("INVALID_WARD_CODE", "Ward code does not exist: " + address.getWardCode());
            }
            if (address.getZoneCode() != null && !foundCodes.contains(address.getZoneCode())) {
                errorMap.put("INVALID_ZONE_CODE", "Zone code does not exist: " + address.getZoneCode());
            }
            if (address.getLocalityCode() != null && !foundCodes.contains(address.getLocalityCode())) {
                errorMap.put("INVALID_LOCALITY_CODE", "Locality code does not exist: " + address.getLocalityCode());
            }
        }
    }



    /**
     * Checks if all boundary codes are empty.
     */
    private boolean isAllBoundaryCodesEmpty(Address address) {
        return (address.getWardCode() == null || address.getWardCode().isBlank()) &&
               (address.getZoneCode() == null || address.getZoneCode().isBlank()) &&
               (address.getLocalityCode() == null || address.getLocalityCode().isBlank());
    }



    private void validateDates(TradeLicense license, Map<String, String> errorMap) {
        if (license.getValidTo() == null) {
            errorMap.put("NULL_VALIDTO", "Valid to date cannot be null");
            return;
        }

        // For temporary licenses, validate date range
        if ("TEMPORARY".equalsIgnoreCase(license.getLicenseType())) {
            Long startOfDay = getStartOfDay();

            // Check if validFrom is in the past (if not allowed)
            if (!isPreviousTLAllowed && license.getValidFrom() != null 
                    && license.getValidFrom() < startOfDay) {
                errorMap.put("INVALID_VALIDFROM", 
                        "Valid from date cannot be less than current date");
            }

            // Check minimum period (30 days)
            if (license.getValidFrom() != null && license.getValidTo() != null) {
                long period = license.getValidTo() - license.getValidFrom();
                if (period < minPeriod) {
                    errorMap.put("INVALID_PERIOD", 
                            "License should be applied for minimum of 30 days");
                }
            }
        }

        // Validate validFrom is before validTo
        if (license.getValidFrom() != null && license.getValidTo() != null 
                && license.getValidFrom() > license.getValidTo()) {
            errorMap.put("INVALID_DATE_RANGE", 
                    "Valid from date cannot be greater than valid to date");
        }
    }

    private void validateOwners(TradeLicense license, Map<String, String> errorMap) {
        license.getOwners().forEach(owner -> {
            if (owner.getName() == null || owner.getName().isBlank()) {
                errorMap.put("NULL_OWNER_NAME", "Owner name cannot be null");
            }
            if (owner.getMobileNumber() == null || owner.getMobileNumber().isBlank()) {
                errorMap.put("NULL_OWNER_MOBILE", "Owner mobile number cannot be null");
            }
            // Institutional owner requires extra fields
            if ("INSTITUTIONAL".equalsIgnoreCase(owner.getOwnerType())) {
                if (owner.getInstitutionName() == null || owner.getInstitutionName().isBlank()) {
                    errorMap.put("NULL_INSTITUTION_NAME", "Institution name is required for institutional owners");
                }
                if (owner.getNameOfAuthorizedPerson() == null || owner.getNameOfAuthorizedPerson().isBlank()) {
                    errorMap.put("NULL_AUTHORIZED_PERSON", "Name of authorized person is required for institutional owners");
                }
                if (owner.getDesignationOfAuthorizedPerson() == null || owner.getDesignationOfAuthorizedPerson().isBlank()) {
                    errorMap.put("NULL_DESIGNATION", "Designation of authorized person is required for institutional owners");
                }
            }
        });
    }

    private void validateDocuments(TradeLicense license, Map<String, String> errorMap) {
        // Check if documents are provided
        if (license.getDocuments() == null || license.getDocuments().isEmpty()) {
            errorMap.put("NULL_DOCUMENTS", "At least one document is required");
            return;
        }

        // Validate each document
        license.getDocuments().forEach(doc -> {
            // Check document type
            if (doc.getDocumentType() == null || doc.getDocumentType().isBlank()) {
                errorMap.put("NULL_DOCUMENT_TYPE", "Document type is required");
            }

            // Check fileStoreId
            if (doc.getFileStoreId() == null || doc.getFileStoreId().isBlank()) {
                errorMap.put("NULL_FILESTOREID", "FileStoreId is required for document");
            } else {
                // Validate fileStoreId exists in FileStore Service
                // digit-client FilestoreClient handles null checks, API call, and error handling
                boolean exists = filestoreClient.isFileAvailable(
                        doc.getFileStoreId(),
                        license.getTenantId()
                );

                if (!exists) {
                    errorMap.put("INVALID_FILESTOREID",
                            "FileStoreId does not exist: " + doc.getFileStoreId());
                }
            }
        });
    }

    private void validateDuplicateDocuments(TradeLicenseRequest request) {
        request.getLicenses().forEach(license -> {
            if (license.getDocuments() == null || license.getDocuments().isEmpty()) {
                log.debug("No documents to validate for license: {}", license.getApplicationNumber());
                return;
            }

            Set<String> documentIds = new HashSet<>();
            
            // Check for duplicate fileStoreIds
            license.getDocuments().forEach(doc -> {
                if (doc.getFileStoreId() != null) {
                    if (documentIds.contains(doc.getFileStoreId())) {
                        throw new RuntimeException("DUPLICATE_DOCUMENT: Same document cannot be used multiple times");
                    }
                    documentIds.add(doc.getFileStoreId());
                }
            });
        });
    }

    private Long getStartOfDay() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Validates renewal applications.
     */
    private void validateRenewal(TradeLicenseRequest request) {
        log.info("Validating renewal applications");

        request.getLicenses().forEach(license -> {
            if (!"RENEWAL".equalsIgnoreCase(license.getApplicationType())) {
                return; // Skip non-renewal applications
            }

            Map<String, String> errorMap = new HashMap<>();

            // License number is mandatory for renewal
            if (license.getLicenseNumber() == null || license.getLicenseNumber().isBlank()) {
                errorMap.put("NULL_LICENSENUMBER", 
                        "License number is mandatory for renewal applications");
            }

            // Cannot renew cancelled licenses
            if (TradeLicenseConstants.STATUS_CANCELLED.equalsIgnoreCase(license.getStatus())) {
                errorMap.put("INVALID_RENEWAL", "Cancelled licenses cannot be renewed");
            }

            // Cannot renew manually expired licenses that are still valid
            if (TradeLicenseConstants.STATUS_MANUALLY_EXPIRED.equalsIgnoreCase(license.getStatus())
                    && license.getValidTo() != null
                    && license.getValidTo() > System.currentTimeMillis()) {
                errorMap.put("INVALID_RENEWAL", "Manually expired licenses that are still valid cannot be renewed");
            }

            // ValidFrom should be after previous ValidTo
            if (license.getValidFrom() != null && license.getValidTo() != null) {
                // This validation would require fetching the previous license
                // For now, just validate that validFrom < validTo
                if (license.getValidFrom() >= license.getValidTo()) {
                    errorMap.put("INVALID_DATE_RANGE", 
                            "Valid from must be before valid to for renewal");
                }
            }

            if (!errorMap.isEmpty()) {
                log.error("Renewal validation failed: {}", errorMap);
                throw new RuntimeException("Renewal validation failed: " + errorMap);
            }
        });
    }
}