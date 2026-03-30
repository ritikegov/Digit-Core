package com.example.tradelicense.service.impl;

import com.example.tradelicense.service.TradeLicenseRegistryService;
import com.example.tradelicense.service.TradeLicenseService;
import com.example.tradelicense.service.enrichment.TradeLicenseEnrichmentService;
import com.example.tradelicense.web.models.*;
import com.example.tradelicense.validation.CreateTradeLicenseValidator;
import com.example.tradelicense.validation.UpdateTradeLicenseValidator;
import com.example.tradelicense.client.TradeLicenseWorkflowClient;
import com.example.tradelicense.client.TradeLicenseNotificationClient;
import com.example.tradelicense.client.TradeLicenseBillingCalculatorClient;
import com.example.tradelicense.client.TradeLicenseIdGenClient;
import com.example.tradelicense.config.TradeLicenseConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digit.services.registry.model.RegistryDataResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradeLicenseServiceImpl implements TradeLicenseService {

    private final TradeLicenseRegistryService registryService;
    private final TradeLicenseEnrichmentService enrichmentService;
    private final CreateTradeLicenseValidator createValidator;
    private final UpdateTradeLicenseValidator updateValidator;

    private final TradeLicenseWorkflowClient workflowClient;
    private final TradeLicenseNotificationClient notificationClient;
    private final TradeLicenseBillingCalculatorClient billingCalculatorClient;
    private final TradeLicenseIdGenClient idGenClient;

    @Value("${tl.workflow.state.pendingPayment:PENDING_PAYMENT}")
    private String statePendingPayment;

    @Value("${tl.workflow.state.licenseIssued:LICENSE_ISSUED}")
    private String stateLicenseIssued;

    @Override
    public TradeLicenseResponse create(TradeLicenseRequest request) {

        log.info("TL CREATE request received");

        List<TradeLicense> licenses = request.getLicenses();

        enrichmentService.enrichCreate(request);

        for (TradeLicense license : licenses) {

            createValidator.validate(request);

            // Start workflow and update license with workflow response
            var workflowResponse = workflowClient.startWorkflow(license);

            RegistryDataResponse registryResponse = registryService.createRegistryData(license);
            if (registryResponse == null || !Boolean.TRUE.equals(registryResponse.getSuccess())) {
                log.warn("Registry creation failed for application {}", license.getApplicationNumber());
            }

            notificationClient.sendNotification(TradeLicenseConstants.EVENT_TL_CREATE, license.getTenantId(), license);
        }

        return TradeLicenseResponse.builder()
                .licenses(licenses)
                .count(licenses.size())
                .build();
    }

    @Override
    public TradeLicenseResponse update(TradeLicenseRequest request) {

        log.info("TL UPDATE request received");

        List<TradeLicense> licenses = request.getLicenses();

        enrichmentService.enrichUpdate(request);

        for (TradeLicense license : licenses) {

            // Clean architecture: Search by application number to get existing license
            TradeLicense existing = registryService.findByApplicationNumber(
                    license.getApplicationNumber(),
                    license.getTenantId()
            );
            
            if (existing == null) {
                throw new RuntimeException("Trade License not found: " + license.getApplicationNumber());
            }

            log.info("Found existing record for applicationNumber={}", license.getApplicationNumber());

            // Merge existing data with update request BEFORE validation
            license.setId(existing.getId());
            
            // Merge audit details from existing record
            if (license.getAuditDetails() != null && existing.getAuditDetails() != null) {
                // Keep existing creation details, update modification details
                license.getAuditDetails().setCreatedBy(existing.getAuditDetails().getCreatedBy());
                license.getAuditDetails().setCreatedTime(existing.getAuditDetails().getCreatedTime());
            } else if (existing.getAuditDetails() != null) {
                // Use existing audit details if none provided in update
                license.setAuditDetails(existing.getAuditDetails());
                license.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());
            }
            
            // Preserve other system fields if not provided in update
            if (license.getBusinessService() == null) {
                license.setBusinessService(existing.getBusinessService());
            }
            if (license.getStatus() == null) {
                license.setStatus(existing.getStatus());
            }
            
            // Preserve required fields if not provided in update
            if (license.getTradeUnits() == null || license.getTradeUnits().isEmpty()) {
                license.setTradeUnits(existing.getTradeUnits());
                log.info("Merged tradeUnits from existing record: {}", 
                        license.getTradeUnits() != null ? license.getTradeUnits().size() : "null");
            }
            if (license.getOwners() == null || license.getOwners().isEmpty()) {
                license.setOwners(existing.getOwners());
            }
            if (license.getAddress() == null) {
                license.setAddress(existing.getAddress());
            }
            if (license.getDocuments() == null) {
                license.setDocuments(existing.getDocuments());
            }
            if (license.getVerificationDocuments() == null) {
                license.setVerificationDocuments(existing.getVerificationDocuments());
            }
            if (license.getLicenseType() == null) {
                license.setLicenseType(existing.getLicenseType());
            }
            if (license.getStructureType() == null) {
                license.setStructureType(existing.getStructureType());
            }
            if (license.getApplicationType() == null) {
                license.setApplicationType(existing.getApplicationType());
            }
            if (license.getTradeName() == null) {
                license.setTradeName(existing.getTradeName());
            }
            if (license.getFinancialYear() == null) {
                license.setFinancialYear(existing.getFinancialYear());
            }

            // Debug: Log what we have after merge
            log.debug("After merge - tradeUnits: {}, owners: {}, address: {}, licenseType: {}, structureType: {}, applicationType: {}",
                    license.getTradeUnits() != null ? license.getTradeUnits().size() : "null",
                    license.getOwners() != null ? license.getOwners().size() : "null",
                    license.getAddress() != null ? "present" : "null",
                    license.getLicenseType(),
                    license.getStructureType(),
                    license.getApplicationType());
        }

        // Validate AFTER merging existing data for all licenses
        updateValidator.validate(request);

        // Process each license after validation
        for (TradeLicense license : licenses) {

            // Capture the action before workflow call (action drives post-processing)
            String workflowAction = license.getAction();

            // Update workflow and get response
            var workflowResponse = workflowClient.updateWorkflow(license);

            log.info("Processing workflow transition - ApplicationNumber: {}, Action: {}",
                    license.getApplicationNumber(), workflowAction);

            // Set the license status based on the action taken, using configured state names
            // This is the source of truth since the workflow service response doesn't return state codes
            if ("APPROVE".equalsIgnoreCase(workflowAction)) {
                license.setStatus(statePendingPayment);
            } else if ("PAY".equalsIgnoreCase(workflowAction)) {
                license.setStatus(stateLicenseIssued);
            }

            String newStatus = license.getStatus();
            log.info("Status after workflow transition - ApplicationNumber: {}, Status: {}",
                    license.getApplicationNumber(), newStatus);

            // When action is APPROVE -> status is now PENDING_PAYMENT
            if ("CANCEL".equalsIgnoreCase(workflowAction)) {
                log.info("Cancel action received, cancelling demand for license: {}", license.getApplicationNumber());
                try {
                    billingCalculatorClient.cancelDemand(license, "CANCELLED", "Trade license cancelled by officer");
                } catch (Exception e) {
                    log.warn("Demand cancellation failed for {}: {}", license.getApplicationNumber(), e.getMessage());
                }
                license.setStatus("CANCELLED");
            }
            else if (statePendingPayment.equalsIgnoreCase(newStatus)) {
                log.info("License approved, generating demand for payment. ApplicationNumber: {}", license.getApplicationNumber());
                
                // DIGIT 3.0: Generate demand in Billing Service
                var calculation = billingCalculatorClient.calculateTradeLicenseFee(license);
                log.info("Calculated fee for approved license {}: Total={}, Tax={}", 
                        license.getApplicationNumber(), 
                        calculation.getTotalAmount(), 
                        calculation.getTaxAmount());
                
                String demandId = billingCalculatorClient.generateDemand(license, calculation);
                log.info("✅ Generated demand {} for approved license {} - Ready for payment", 
                        demandId, license.getApplicationNumber());
                
                // Note: demandId is NOT stored in Trade License domain model
                // Billing Service manages demand-to-application mapping internally
                // Bills are generated on-demand when user initiates payment
            }
            
            // When action is PAY -> status is now LICENSE_ISSUED
            else if (stateLicenseIssued.equalsIgnoreCase(newStatus)) {                log.info("Payment action received, generating bill and recording payment. ApplicationNumber: {}", license.getApplicationNumber());

                // Step 1: Fetch the active demand for this application
                List<Map<String, Object>> demands = billingCalculatorClient.searchDemandsWithFilters(
                        "TL", license.getApplicationNumber(), "ACTIVE", null, null,
                        license.getTenantId(), 1, 0);

                if (!demands.isEmpty()) {
                    Map<String, Object> demand = demands.get(0);
                    Object totalAmountObj = demand.get("totalAmount");
                    java.math.BigDecimal totalAmount = totalAmountObj != null
                            ? new java.math.BigDecimal(totalAmountObj.toString())
                            : java.math.BigDecimal.ZERO;

                    // Step 2: Generate a bill from the demand (DIGIT fetchBill pattern)
                    String billId = null;
                    try {
                        billId = billingCalculatorClient.generateBill(license, null, null, null);
                        log.info("Generated bill {} for application {}", billId, license.getApplicationNumber());
                    } catch (Exception e) {
                        log.warn("Bill generation failed for {}: {} — proceeding without bill", 
                                license.getApplicationNumber(), e.getMessage());
                    }

                    // Step 3: Record payment against the bill
                    if (billId != null && totalAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        try {
                            String ownerName = license.getOwners() != null && !license.getOwners().isEmpty()
                                    ? license.getOwners().get(0).getName() : "Unknown";
                            String rawMobile = license.getOwners() != null && !license.getOwners().isEmpty()
                                    ? license.getOwners().get(0).getMobileNumber() : null;
                            // Normalize to E.164 format required by billing service
                            String ownerMobile;
                            if (rawMobile != null && !rawMobile.isBlank()) {
                                String digits = rawMobile.replaceAll("[^\\d]", "");
                                ownerMobile = "+" + (digits.startsWith("91") && digits.length() == 12 ? digits : "91" + digits);
                            } else {
                                ownerMobile = "+919999999999";
                            }
                            String ownerEmail = license.getOwners() != null && !license.getOwners().isEmpty()
                                    ? license.getOwners().get(0).getEmailId() : null;

                            String paymentId = billingCalculatorClient.createPayment(
                                    billId, totalAmount, "CASH",
                                    ownerName, ownerMobile, ownerEmail,
                                    license.getTenantId());
                            log.info("✅ Recorded payment {} for bill {} (amount: {}) — demand will be marked PAID",
                                    paymentId, billId, totalAmount);
                        } catch (Exception e) {
                            log.warn("Payment recording failed for {}: {} — license will still be issued",
                                    license.getApplicationNumber(), e.getMessage());
                        }
                    }
                } else {
                    log.warn("No active demand found for {} during PAY action — skipping bill/payment generation",
                            license.getApplicationNumber());
                }

                // Step 4: Generate license number
                if (license.getLicenseNumber() == null) {
                    String licenseNumber = idGenClient.generateLicenseNumber(license);
                    license.setLicenseNumber(licenseNumber);
                    log.info("Generated license number {} for issued license {}",
                            licenseNumber, license.getApplicationNumber());
                }
            }
            else {
                // Send specific notifications for REJECT and REOPEN actions
                if ("REJECT".equalsIgnoreCase(workflowAction)) {
                    notificationClient.sendNotification(TradeLicenseConstants.EVENT_TL_REJECT, license.getTenantId(), license);
                    log.info("Sent rejection notification for application: {}", license.getApplicationNumber());
                } else if ("REOPEN".equalsIgnoreCase(workflowAction)) {
                    notificationClient.sendNotification(TradeLicenseConstants.EVENT_TL_REOPEN, license.getTenantId(), license);
                    log.info("Sent reopen notification for application: {}", license.getApplicationNumber());
                } else {
                    log.info("No special processing required for action: {} / status: {} (ApplicationNumber: {})",
                            workflowAction, newStatus, license.getApplicationNumber());
                }
            }
            registryService.updateRegistryData(license);

            notificationClient.sendNotification(TradeLicenseConstants.EVENT_TL_UPDATE, license.getTenantId(), license);
        }

        return TradeLicenseResponse.builder()
                .licenses(licenses)
                .count(licenses.size())
                .build();
    }

    @Override
    public TradeLicenseResponse search(TradeLicenseSearchCriteria criteria) {

        log.info("TL SEARCH request received applicationNumber={}, mobileNumber={}, status={}, tradeName={}, ownerName={}",
                criteria.getApplicationNumber(), criteria.getMobileNumber(), criteria.getStatus(), 
                criteria.getTradeName(), criteria.getOwnerName());

        List<TradeLicense> result = new ArrayList<>();

        // Search by application number (most specific - single result expected)
        if (criteria.getApplicationNumber() != null && !criteria.getApplicationNumber().isBlank()) {
            TradeLicense license = registryService.findByApplicationNumber(
                    criteria.getApplicationNumber(),
                    criteria.getTenantId()
            );

            if (license != null && matchesCriteria(license, criteria)) {
                result.add(license);
            }
        }
        // Search by license number (if provided)
        else if (criteria.getLicenseNumbers() != null && !criteria.getLicenseNumbers().isEmpty()) {
            for (String licenseNumber : criteria.getLicenseNumbers()) {
                if (licenseNumber != null && !licenseNumber.isBlank()) {
                    List<TradeLicense> licenses = registryService.findByLicenseNumber(licenseNumber, criteria.getTenantId());
                    for (TradeLicense license : licenses) {
                        if (matchesCriteria(license, criteria)) {
                            result.add(license);
                        }
                    }
                }
            }
        }
        // Search by trade name (if provided)
        else if (criteria.getTradeName() != null && !criteria.getTradeName().isBlank()) {
            List<TradeLicense> licenses = registryService.findByTradeName(criteria.getTradeName(), criteria.getTenantId());
            for (TradeLicense license : licenses) {
                if (matchesCriteria(license, criteria)) {
                    result.add(license);
                }
            }
        }
        // Search by mobile number (if provided)
        else if (criteria.getMobileNumber() != null && !criteria.getMobileNumber().isBlank()) {
            List<TradeLicense> licenses = registryService.findByMobileNumber(criteria.getMobileNumber(), criteria.getTenantId());
            for (TradeLicense license : licenses) {
                if (matchesCriteria(license, criteria)) {
                    result.add(license);
                }
            }
        }
        // If no specific search field provided, but we have filters like status, 
        // search all records for the tenant and then filter
        else if (hasFilterCriteria(criteria)) {
            log.info("No specific search field provided, but filters present. Searching all records for tenant and filtering.");
            
            // Get all records for the tenant and then apply filters
            List<TradeLicense> allLicenses = registryService.findByTenantId(criteria.getTenantId());
            
            for (TradeLicense license : allLicenses) {
                if (matchesCriteria(license, criteria)) {
                    result.add(license);
                }
            }
            
            log.info("Found {} licenses after filtering from {} total licenses for tenant {}", 
                    result.size(), allLicenses.size(), criteria.getTenantId());
        }
        // If no search criteria provided at all, return all records for the tenant
        else {
            log.info("No search criteria provided. Returning all records for tenant: {}", criteria.getTenantId());
            
            // Get all records for the tenant
            result = registryService.findByTenantId(criteria.getTenantId());
            
            log.info("Found {} total licenses for tenant {}", result.size(), criteria.getTenantId());
        }

        // Remove duplicates (in case multiple search criteria returned the same record)
        result = result.stream()
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        // Apply sorting
        if (criteria.getSortBy() != null && !criteria.getSortBy().isBlank()) {
            result = sortResults(result, criteria.getSortBy(), criteria.getSortOrder());
        }

        // Get total count before pagination
        int totalCount = result.size();

        // Apply pagination
        result = applyPagination(result, criteria.getOffset(), criteria.getLimit());

        return TradeLicenseResponse.builder()
                .licenses(result)
                .count(result.size())
                .totalCount(totalCount)
                .offset(criteria.getOffset())
                .limit(criteria.getLimit())
                .build();
    }

    /**
     * Check if the criteria has filter conditions (like status) that would require broad search
     */
    private boolean hasFilterCriteria(TradeLicenseSearchCriteria criteria) {
        return (criteria.getStatus() != null && !criteria.getStatus().isEmpty()) ||
               (criteria.getOwnerName() != null && !criteria.getOwnerName().isBlank()) ||
               (criteria.getLocality() != null && !criteria.getLocality().isBlank()) ||
               (criteria.getTradeType() != null && !criteria.getTradeType().isBlank()) ||
               (criteria.getFromDate() != null) ||
               (criteria.getToDate() != null);
    }

    /**
     * Applies pagination to the result list.
     */
    private List<TradeLicense> applyPagination(List<TradeLicense> licenses, Integer offset, Integer limit) {
        if (licenses == null || licenses.isEmpty()) {
            return licenses;
        }

        int start = (offset != null && offset > 0) ? offset : 0;
        int end = (limit != null && limit > 0) ? Math.min(start + limit, licenses.size()) : licenses.size();

        if (start >= licenses.size()) {
            return new ArrayList<>();
        }

        return licenses.subList(start, end);
    }

    /**
     * Sorts the result list based on sortBy and sortOrder.
     */
    private List<TradeLicense> sortResults(List<TradeLicense> licenses, String sortBy, String sortOrder) {
        if (licenses == null || licenses.isEmpty()) {
            return licenses;
        }

        boolean ascending = !"DESC".equalsIgnoreCase(sortOrder);

        return licenses.stream()
                .sorted((l1, l2) -> {
                    int comparison = 0;
                    
                    switch (sortBy.toLowerCase()) {
                        case "applicationnumber":
                            comparison = compareStrings(l1.getApplicationNumber(), l2.getApplicationNumber());
                            break;
                        case "licensenumber":
                            comparison = compareStrings(l1.getLicenseNumber(), l2.getLicenseNumber());
                            break;
                        case "tradename":
                            comparison = compareStrings(l1.getTradeName(), l2.getTradeName());
                            break;
                        case "applicationdate":
                            comparison = compareLongs(l1.getApplicationDate(), l2.getApplicationDate());
                            break;
                        case "status":
                            comparison = compareStrings(l1.getStatus(), l2.getStatus());
                            break;
                        default:
                            comparison = 0;
                    }
                    
                    return ascending ? comparison : -comparison;
                })
                .collect(Collectors.toList());
    }

    private int compareStrings(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return -1;
        if (s2 == null) return 1;
        return s1.compareTo(s2);
    }

    private int compareLongs(Long l1, Long l2) {
        if (l1 == null && l2 == null) return 0;
        if (l1 == null) return -1;
        if (l2 == null) return 1;
        return l1.compareTo(l2);
    }

    /**
     * Checks if a license matches the search criteria.
     */
    private boolean matchesCriteria(TradeLicense license, TradeLicenseSearchCriteria criteria) {
        // Filter by status if provided
        if (criteria.getStatus() != null && !criteria.getStatus().isEmpty()) {
            boolean statusMatches = criteria.getStatus().stream()
                    .anyMatch(status -> status.equalsIgnoreCase(license.getStatus()));
            if (!statusMatches) {
                return false;
            }
        }

        // Filter by mobile number if provided
        if (criteria.getMobileNumber() != null && !criteria.getMobileNumber().isBlank()) {
            boolean mobileMatches = license.getOwners() != null && license.getOwners().stream()
                    .anyMatch(owner -> criteria.getMobileNumber().equals(owner.getMobileNumber()));
            if (!mobileMatches) {
                return false;
            }
        }

        // Filter by owner name (partial, case-insensitive)
        if (criteria.getOwnerName() != null && !criteria.getOwnerName().isBlank()) {
            boolean ownerMatches = license.getOwners() != null && license.getOwners().stream()
                    .anyMatch(owner -> owner.getName() != null &&
                            owner.getName().toLowerCase().contains(criteria.getOwnerName().toLowerCase()));
            if (!ownerMatches) {
                return false;
            }
        }

        // Filter by locality
        if (criteria.getLocality() != null && !criteria.getLocality().isBlank()) {
            boolean localityMatches = license.getAddress() != null &&
                    criteria.getLocality().equalsIgnoreCase(license.getAddress().getLocality());
            if (!localityMatches) {
                return false;
            }
        }

        // Filter by trade type
        if (criteria.getTradeType() != null && !criteria.getTradeType().isBlank()) {
            boolean tradeTypeMatches = license.getTradeUnits() != null && license.getTradeUnits().stream()
                    .anyMatch(unit -> criteria.getTradeType().equalsIgnoreCase(unit.getTradeType()));
            if (!tradeTypeMatches) {
                return false;
            }
        }

        // Filter by fromDate/toDate (epoch millis) against applicationDate
        if (criteria.getFromDate() != null && license.getApplicationDate() != null) {
            if (license.getApplicationDate() < criteria.getFromDate()) {
                return false;
            }
        }

        if (criteria.getToDate() != null && license.getApplicationDate() != null) {
            if (license.getApplicationDate() > criteria.getToDate()) {
                return false;
            }
        }

        return true;
    }

}
