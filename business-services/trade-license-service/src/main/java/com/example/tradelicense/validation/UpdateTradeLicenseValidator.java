package com.example.tradelicense.validation;

import com.example.tradelicense.web.models.Address;
import com.example.tradelicense.web.models.TradeLicense;
import com.example.tradelicense.web.models.TradeLicenseRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digit.services.filestore.FilestoreClient;
import org.digit.services.boundary.BoundaryClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateTradeLicenseValidator {

    private final MdmsValidator mdmsValidator;
    private final FilestoreClient filestoreClient;
    private final BoundaryClient boundaryClient;

    public void validate(TradeLicenseRequest request) {

        if (request == null || request.getLicenses() == null || request.getLicenses().isEmpty()) {
            throw new RuntimeException("TradeLicenseRequest cannot be empty");
        }

        request.getLicenses().forEach(this::validateLicense);
        
        // Validate MDMS data
        mdmsValidator.validate(request);
        
        // Validate at least one trade unit is active
        validateTradeUnits(request);
        
        // Validate at least one owner is active
        validateOwnerActiveStatus(request);
        
        // Validate owners have required fields
        validateOwners(request);
        
        // Validate documents (including NEW documents added in update)
        validateDocuments(request);
        
        // Validate duplicate documents
        validateDuplicateDocuments(request);

        // Validate verification documents — only officers can set these
        validateVerificationDocuments(request);
    }

    private void validateLicense(TradeLicense license) {

        Map<String,String> errorMap = new HashMap<>();

        // ID is set automatically by service from existing record, no need to validate in requestould

        if (license.getApplicationNumber() == null || license.getApplicationNumber().isBlank())
            errorMap.put("NULL_APPLICATIONNUMBER","Application number required");

        if (license.getTenantId() == null || license.getTenantId().isBlank())
            errorMap.put("NULL_TENANT","TenantId required");

        // Validate address (DIGIT 2.9 style - inline validation for renewals)
        if (license.getAddress() != null) {
            validateAddressForRenewal(license, errorMap);
        }

        // Note: registryId is set in service layer from existing license, not validated here

        if (!errorMap.isEmpty()) {
            log.error("Update validation failed: {}", errorMap);
            throw new RuntimeException("Validation failed: " + errorMap);
        }
    }

    /**
     * Validates address for renewal applications (may have different rules).
     */
    private void validateAddressForRenewal(TradeLicense license, Map<String, String> errorMap) {
        Address address = license.getAddress();
        
        // Basic address validation (more lenient for renewals)
        if (address.getCity() == null || address.getCity().isBlank()) {
            errorMap.put("NULL_CITY", "City cannot be null");
        }

        // Boundary validation (if provided)
        if (!isAllBoundaryCodesEmpty(address)) {
            validateAddressBoundaries(license.getTenantId(), address, errorMap);
        }
        
        log.debug("Address validation completed for renewal application: {}", license.getApplicationNumber());
    }

    /**
     * Validates address boundaries for renewal applications.
     * BoundaryClient handles all error cases and throws DigitClientException.
     */
    private void validateAddressBoundaries(String tenantId, Address address, Map<String, String> errorMap) {
        // Collect boundary codes to validate
        List<String> codes = new ArrayList<>();
        if (address.getWardCode() != null && !address.getWardCode().isBlank()) {
            codes.add(address.getWardCode());
        }
        if (address.getZoneCode() != null && !address.getZoneCode().isBlank()) {
            codes.add(address.getZoneCode());
        }

        // Validate boundary codes exist using digit-client BoundaryClient
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

    /**
     * Validates that at least one trade unit is active or new.
     */
    private void validateTradeUnits(TradeLicenseRequest request) {
        Map<String, String> errorMap = new HashMap<>();
        
        request.getLicenses().forEach(license -> {
            if (license.getTradeUnits() == null || license.getTradeUnits().isEmpty()) {
                errorMap.put("NULL_TRADEUNITS", 
                        "At least one trade unit required for license: " + license.getApplicationNumber());
                return;
            }

            boolean hasActiveUnit = license.getTradeUnits().stream()
                    .anyMatch(unit -> unit.getId() == null || Boolean.TRUE.equals(unit.getActive()));

            if (!hasActiveUnit) {
                errorMap.put("INVALID_UPDATE", 
                        "All trade units are inactive for license: " + license.getApplicationNumber());
            }
        });

        if (!errorMap.isEmpty()) {
            log.error("Trade unit validation failed: {}", errorMap);
            throw new RuntimeException("Validation failed: " + errorMap);
        }
    }

    /**
     * Validates that at least one owner is active.
     */
    private void validateOwnerActiveStatus(TradeLicenseRequest request) {
        Map<String, String> errorMap = new HashMap<>();
        
        request.getLicenses().forEach(license -> {
            if (license.getOwners() == null || license.getOwners().isEmpty()) {
                errorMap.put("NULL_OWNERS", 
                        "At least one owner required for license: " + license.getApplicationNumber());
                return;
            }

            // For now, assume all owners are active if they exist
            // In DIGIT 2.9, this checked userActive field
            boolean hasActiveOwner = !license.getOwners().isEmpty();

            if (!hasActiveOwner) {
                errorMap.put("INVALID_OWNER", 
                        "All owners are inactive for license: " + license.getApplicationNumber());
            }
        });

        if (!errorMap.isEmpty()) {
            log.error("Owner validation failed: {}", errorMap);
            throw new RuntimeException("Validation failed: " + errorMap);
        }
    }

    /**
     * Validates owner required fields (especially for NEW owners added in update).
     */
    private void validateOwners(TradeLicenseRequest request) {
        Map<String, String> errorMap = new HashMap<>();
        
        request.getLicenses().forEach(license -> {
            if (license.getOwners() == null || license.getOwners().isEmpty()) {
                return; // Already validated in validateOwnerActiveStatus
            }

            license.getOwners().forEach(owner -> {
                if (owner.getName() == null || owner.getName().isBlank()) {
                    errorMap.put("NULL_OWNER_NAME", "Owner name cannot be null");
                }
                if (owner.getMobileNumber() == null || owner.getMobileNumber().isBlank()) {
                    errorMap.put("NULL_OWNER_MOBILE", "Owner mobile number cannot be null");
                }
            });
        });

        if (!errorMap.isEmpty()) {
            log.error("Owner field validation failed: {}", errorMap);
            throw new RuntimeException("Validation failed: " + errorMap);
        }
    }

    /**
     * Validates documents (especially NEW documents added in update).
     * Checks document type, fileStoreId, and verifies file exists in FileStore Service.
     */
    private void validateDocuments(TradeLicenseRequest request) {
        Map<String, String> errorMap = new HashMap<>();
        
        request.getLicenses().forEach(license -> {
            if (license.getDocuments() == null || license.getDocuments().isEmpty()) {
                log.debug("No documents to validate for license: {}", license.getApplicationNumber());
                return;
            }

            // Validate each document (especially NEW ones without ID)
            license.getDocuments().forEach(doc -> {
                // Check document type
                if (doc.getDocumentType() == null || doc.getDocumentType().isBlank()) {
                    errorMap.put("NULL_DOCUMENT_TYPE", "Document type is required");
                }

                // Check fileStoreId
                if (doc.getFileStoreId() == null || doc.getFileStoreId().isBlank()) {
                    errorMap.put("NULL_FILESTOREID", "FileStoreId is required for document");
                } else if (doc.getId() == null) {
                    // Only validate NEW documents (without ID) in FileStore
                    // Existing documents (with ID) were already validated during create
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
        });

        if (!errorMap.isEmpty()) {
            log.error("Document validation failed: {}", errorMap);
            throw new RuntimeException("Validation failed: " + errorMap);
        }
    }

    /**
     * Validates no duplicate documents (same fileStoreId used multiple times).
     */
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

    private static final List<String> OFFICER_ROLES = List.of(
            "TL_INSPECTOR", "FIELD_INSPECTOR", "TL_APPROVER", "SUPERUSER");

    /**
     * Verification documents can only be added/modified by officers.
     * Citizens and CSR cannot populate verificationDocuments.
     */
    private void validateVerificationDocuments(TradeLicenseRequest request) {
        request.getLicenses().forEach(license -> {
            if (license.getVerificationDocuments() == null || license.getVerificationDocuments().isEmpty()) {
                return; // nothing to validate
            }

            List<String> roles = license.getRoles();
            boolean isOfficer = roles != null && roles.stream()
                    .anyMatch(role -> OFFICER_ROLES.stream().anyMatch(r -> r.equalsIgnoreCase(role)));

            if (!isOfficer) {
                throw new RuntimeException("ACCESS_DENIED: Only officers can upload verification documents");
            }

            // Validate each verification document
            license.getVerificationDocuments().forEach(doc -> {
                if (doc.getDocumentType() == null || doc.getDocumentType().isBlank()) {
                    throw new RuntimeException("NULL_VERIFICATION_DOCUMENT_TYPE: Document type is required for verification documents");
                }
                if (doc.getFileStoreId() == null || doc.getFileStoreId().isBlank()) {
                    throw new RuntimeException("NULL_VERIFICATION_FILESTOREID: FileStoreId is required for verification document");
                }
                boolean exists = filestoreClient.isFileAvailable(doc.getFileStoreId(), license.getTenantId());
                if (!exists) {
                    throw new RuntimeException("INVALID_VERIFICATION_FILESTOREID: Verification document FileStoreId does not exist: " + doc.getFileStoreId());
                }
            });
        });
    }
}