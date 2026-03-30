package com.example.tradelicense.client;

import com.example.tradelicense.service.BillingSlabService;
import com.example.tradelicense.service.RenewalCalculationService;
import com.example.tradelicense.config.TradeLicenseConstants;
import com.example.tradelicense.web.models.BillingSlab;
import com.example.tradelicense.web.models.BillingSlabSearchCriteria;
import com.example.tradelicense.web.models.Calculation;
import com.example.tradelicense.web.models.TradeLicense;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for integrating with DIGIT 3.0 Billing Service.
 * Handles fee calculation, demand generation, bill creation, and payment processing.
 * 
 * Assumes business service and tax heads are pre-configured via admin setup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeLicenseBillingCalculatorClient {

    private final RestTemplate restTemplate;
    private final BillingSlabService billingSlabService;
    private final RenewalCalculationService renewalCalculationService;

    @Value("${billing.service.host}")
    private String billingServiceHost;

    @Value("${tl.business.service}")
    private String businessService;

    /**
     * Calculate trade license fee using MDMS billing slabs.
     * This method handles the fee calculation logic locally for performance.
     */
    public Calculation calculateTradeLicenseFee(TradeLicense license) {
        log.info("Calculating TL fee for license: {}", license.getApplicationNumber());

        Calculation calculation = new Calculation();
        calculation.setApplicationNumber(license.getApplicationNumber());
        calculation.setTenantId(license.getTenantId());

        List<Calculation.FeeDetail> feeDetails = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // Calculate trade unit fees
        if (license.getTradeUnits() != null && !license.getTradeUnits().isEmpty()) {
            BigDecimal tradeUnitFees = calculateTradeUnitFees(license, feeDetails);
            totalAmount = totalAmount.add(tradeUnitFees);
        }

        // Calculate accessory fees
        if (license.getAccessories() != null && !license.getAccessories().isEmpty()) {
            BigDecimal accessoryFees = calculateAccessoryFees(license, feeDetails);
            totalAmount = totalAmount.add(accessoryFees);
        }

        calculation.setFeeDetails(feeDetails);
        calculation.setTotalAmount(totalAmount);
        calculation.setTaxAmount(totalAmount.multiply(new BigDecimal("0.1"))); // 10% tax

        // For renewal applications, apply rebates/penalties on top of base fee
        if (TradeLicenseConstants.APPLICATION_TYPE_RENEWAL.equalsIgnoreCase(license.getApplicationType())) {
            List<Calculation.FeeDetail> renewalAdjustments = renewalCalculationService.calculateRenewalFees(license, totalAmount);
            if (!renewalAdjustments.isEmpty()) {
                feeDetails.addAll(renewalAdjustments);
                BigDecimal adjustmentTotal = renewalAdjustments.stream()
                        .map(Calculation.FeeDetail::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalAmount = totalAmount.add(adjustmentTotal);
                calculation.setTotalAmount(totalAmount);
                calculation.setTaxAmount(totalAmount.multiply(new BigDecimal("0.1")));
                log.info("Applied renewal adjustments: {} for license: {}", adjustmentTotal, license.getApplicationNumber());
            }
        }

        // Apply adhoc penalty/exemption set by officer
        if (license.getAdhocPenalty() != null && license.getAdhocPenalty().compareTo(java.math.BigDecimal.ZERO) > 0) {
            Calculation.FeeDetail penalty = new Calculation.FeeDetail();
            penalty.setFeeType("TL_ADHOC_PENALTY");
            penalty.setAmount(license.getAdhocPenalty());
            penalty.setDescription("Adhoc penalty applied by officer");
            feeDetails.add(penalty);
            totalAmount = totalAmount.add(license.getAdhocPenalty());
        }
        if (license.getAdhocExemption() != null && license.getAdhocExemption().compareTo(java.math.BigDecimal.ZERO) > 0) {
            Calculation.FeeDetail exemption = new Calculation.FeeDetail();
            exemption.setFeeType("TL_ADHOC_EXEMPTION");
            exemption.setAmount(license.getAdhocExemption().negate());
            exemption.setDescription("Adhoc exemption applied by officer");
            feeDetails.add(exemption);
            totalAmount = totalAmount.subtract(license.getAdhocExemption());
        }

        calculation.setTotalAmount(totalAmount);
        calculation.setTaxAmount(totalAmount.multiply(new BigDecimal("0.1")));
        log.info("Calculated TL fee: {} for license: {}", totalAmount, license.getApplicationNumber());
        return calculation;
    }

    private BigDecimal calculateTradeUnitFees(TradeLicense license, List<Calculation.FeeDetail> feeDetails) {
        BigDecimal totalTradeUnitFees = BigDecimal.ZERO;

        for (var tradeUnit : license.getTradeUnits()) {
            if (!tradeUnit.getActive()) continue;

            try {
                // Create search criteria for billing slab service
                BillingSlabSearchCriteria criteria = new BillingSlabSearchCriteria();
                criteria.setTenantId(license.getTenantId());
                criteria.setTradeType(tradeUnit.getTradeType());
                criteria.setStructureType(license.getStructureType());
                criteria.setLicenseType(license.getLicenseType());
                criteria.setApplicationType(license.getApplicationType());
                
                List<BillingSlab> slabs = billingSlabService.searchBillingSlabs(criteria);

                if (slabs.isEmpty()) {
                    log.warn("No billing slabs found for trade unit: {}", tradeUnit.getTradeType());
                    continue;
                }

                BillingSlab applicableSlab = slabs.stream()
                    .filter(slab -> tradeUnit.getUomValue() >= slab.getFromUom() && 
                                   tradeUnit.getUomValue() <= slab.getToUom())
                    .findFirst()
                    .orElse(slabs.get(0));

                BigDecimal unitFee = calculateFeeFromSlab(applicableSlab, tradeUnit.getUomValue());
                totalTradeUnitFees = totalTradeUnitFees.add(unitFee);

                // Add fee detail
                Calculation.FeeDetail feeDetail = new Calculation.FeeDetail();
                feeDetail.setFeeType("TL_TRADE_UNIT_FEE");
                feeDetail.setAmount(unitFee);
                feeDetail.setDescription("Trade unit fee for " + tradeUnit.getTradeType());
                feeDetails.add(feeDetail);

                log.debug("Trade unit fee calculated: {} for type: {}", unitFee, tradeUnit.getTradeType());

            } catch (Exception e) {
                log.error("Error calculating fee for trade unit: {}", tradeUnit.getTradeType(), e);
            }
        }

        return totalTradeUnitFees;
    }

    private BigDecimal calculateAccessoryFees(TradeLicense license, List<Calculation.FeeDetail> feeDetails) {
        BigDecimal totalAccessoryFees = BigDecimal.ZERO;

        for (var accessory : license.getAccessories()) {
            if (!accessory.getActive()) continue;

            try {
                // Create search criteria for billing slab service
                BillingSlabSearchCriteria criteria = new BillingSlabSearchCriteria();
                criteria.setTenantId(license.getTenantId());
                criteria.setAccessoryCategory(accessory.getAccessoryCategory());
                criteria.setStructureType(license.getStructureType());
                criteria.setLicenseType(license.getLicenseType());
                criteria.setApplicationType(license.getApplicationType());
                
                List<BillingSlab> slabs = billingSlabService.searchBillingSlabs(criteria);

                if (slabs.isEmpty()) {
                    log.warn("No billing slabs found for accessory: {}", accessory.getAccessoryCategory());
                    continue;
                }

                BillingSlab applicableSlab = slabs.stream()
                    .filter(slab -> accessory.getUomValue() >= slab.getFromUom() && 
                                   accessory.getUomValue() <= slab.getToUom())
                    .findFirst()
                    .orElse(slabs.get(0));

                BigDecimal accessoryFee = calculateFeeFromSlab(applicableSlab, accessory.getUomValue());
                totalAccessoryFees = totalAccessoryFees.add(accessoryFee);

                // Add fee detail
                Calculation.FeeDetail feeDetail = new Calculation.FeeDetail();
                feeDetail.setFeeType("TL_ACCESSORY_FEE");
                feeDetail.setAmount(accessoryFee);
                feeDetail.setDescription("Accessory fee for " + accessory.getAccessoryCategory());
                feeDetails.add(feeDetail);

                log.debug("Accessory fee calculated: {} for category: {}", accessoryFee, accessory.getAccessoryCategory());

            } catch (Exception e) {
                log.error("Error calculating fee for accessory: {}", accessory.getAccessoryCategory(), e);
            }
        }

        return totalAccessoryFees;
    }

    private BigDecimal calculateFeeFromSlab(BillingSlab slab, Double uomValue) {
        if (slab.getRate() == null || uomValue == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = slab.getRate(); // Already a BigDecimal
        BigDecimal uom = BigDecimal.valueOf(uomValue);

        return rate.multiply(uom);
    }

    /**
     * Generate demand for trade license using pre-configured business service and tax heads.
     * Maps calculated fees to appropriate tax heads and creates demand.
     */
    public String generateDemand(TradeLicense license, Calculation calculation) {
        log.info("Generating demand for license: {}", license.getApplicationNumber());

        // Build demand request using proper field names and validation
        Map<String, Object> demand = new HashMap<>();
        demand.put("businessServiceCode", businessService);
        demand.put("periodFrom", license.getValidFrom() != null ? license.getValidFrom() : System.currentTimeMillis());
        demand.put("periodTo", license.getValidTo() != null ? license.getValidTo() : System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000));
        demand.put("billExpiryDays", 30);
        demand.put("consumerCode", license.getApplicationNumber());
        demand.put("payer", List.of(license.getAccountId() != null ? license.getAccountId() : "TL-USER"));
        demand.put("status", "ACTIVE");
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("taxPeriod", license.getFinancialYear());
        metadata.put("licenseType", license.getLicenseType());
        metadata.put("applicationType", license.getApplicationType());
        demand.put("metadata", metadata);

        // Build line items from calculation using configured tax heads
        List<Map<String, Object>> lineItems = new ArrayList<>();
        
        if (calculation.getFeeDetails() != null && !calculation.getFeeDetails().isEmpty()) {
            // Map fee details to tax heads
            for (Calculation.FeeDetail feeDetail : calculation.getFeeDetails()) {
                Map<String, Object> lineItem = new HashMap<>();
                lineItem.put("taxHeadCode", feeDetail.getFeeType()); // TL_TRADE_UNIT_FEE, TL_ACCESSORY_FEE
                lineItem.put("amount", feeDetail.getAmount().toString());
                lineItem.put("collectedAmount", "0");
                lineItems.add(lineItem);
            }
        } else {
            // Fallback: Create a single base tax line item
            Map<String, Object> baseTaxItem = new HashMap<>();
            baseTaxItem.put("taxHeadCode", "TL_BASE_TAX");
            baseTaxItem.put("amount", calculation.getTotalAmount().toString());
            baseTaxItem.put("collectedAmount", "0");
            lineItems.add(baseTaxItem);
        }
        
        demand.put("lineItems", lineItems);

        // Create request array - DIGIT expects array format
        List<Map<String, Object>> demandRequest = List.of(demand);

        // Set headers - use JWT token context
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", license.getTenantId());

        HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(demandRequest, headers);

        try {
            String url = billingServiceHost + "/billing/v3/demands";
            log.info("Creating demand at: {}", url);
            
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.POST, entity, List.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && !response.getBody().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> createdDemand = (Map<String, Object>) response.getBody().get(0);
                String demandId = (String) createdDemand.get("id");
                
                log.info("✅ Generated demand: {} for license: {} with total amount: {}", 
                        demandId, license.getApplicationNumber(), calculation.getTotalAmount());

                return demandId;
            }
        } catch (Exception e) {
            log.error("❌ Failed to generate demand for license: {}", license.getApplicationNumber(), e);
            throw new RuntimeException("Failed to generate demand: " + e.getMessage(), e);
        }

        throw new RuntimeException("Failed to generate demand - no response from billing service");
    }

    /**
     * Generate bill for trade license demand using correct billing service endpoint.
     * Note: Bill generation requires IDGEN service to be accessible for bill number generation.
     */
    public String generateBill(TradeLicense license, String payerName, String payerMobile, String payerEmail) {
        log.info("Generating bill for license: {}", license.getApplicationNumber());

        Map<String, Object> billRequest = new HashMap<>();
        billRequest.put("businessServiceCode", businessService);
        billRequest.put("consumerCode", license.getApplicationNumber());
        billRequest.put("payerId", license.getAccountId() != null ? license.getAccountId() : "TL-USER");
        billRequest.put("payerName", payerName != null ? payerName : getOwnerName(license));
        billRequest.put("payerAddress", getOwnerAddress(license));
        billRequest.put("payerMobileNumber", payerMobile != null ? payerMobile : getOwnerMobile(license));
        billRequest.put("payerEmail", payerEmail != null ? payerEmail : getOwnerEmail(license));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", license.getTenantId());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(billRequest, headers);

        try {
            String url = billingServiceHost + "/billing/v3/bills/generate";
            log.info("Generating bill at: {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String billId = (String) response.getBody().get("id");
                String billNumber = (String) response.getBody().get("billNumber");
                log.info("✅ Generated bill: {} (Number: {}) for license: {}", billId, billNumber, license.getApplicationNumber());
                return billId;
            }

        } catch (Exception e) {
            log.error("Failed to generate bill for license: {}", license.getApplicationNumber(), e);
            
            // Check if it's an IDGEN issue
            if (e.getMessage() != null && e.getMessage().contains("idgen failed")) {
                throw new RuntimeException("Bill generation failed: IDGEN service unavailable or TL_BILL_NUMBER template not configured. " +
                        "Please ensure IDGEN service is running and has the required bill number template.", e);
            }
            
            throw new RuntimeException("Failed to generate bill: " + e.getMessage(), e);
        }

        throw new RuntimeException("Failed to generate bill - no response from billing service");
    }

    /**
     * Search bills for trade license using correct endpoint.
     */
    public List<Map<String, Object>> searchBills(TradeLicense license) {
        log.info("Searching bills for license: {}", license.getApplicationNumber());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", license.getTenantId());

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(billingServiceHost + "/billing/v3/bills")
                    .queryParam("businessServiceCode", businessService)
                    .queryParam("consumerCodes", license.getApplicationNumber())
                    .queryParam("limit", 25)
                    .queryParam("offset", 0)
                    .build(true).toUri();
            log.info("Searching bills at: {}", uri);
            
            ResponseEntity<List> response = restTemplate.exchange(uri, HttpMethod.GET, entity, List.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ Found {} bills for license: {}", response.getBody().size(), license.getApplicationNumber());
                return response.getBody();
            }

        } catch (Exception e) {
            log.error("Failed to search bills for license: {}", license.getApplicationNumber(), e);
        }

        return new ArrayList<>();
    }

    /**
     * Search payments for trade license using correct endpoint.
     */
    public List<Map<String, Object>> searchPayments(TradeLicense license) {
        log.info("Searching payments for license: {}", license.getApplicationNumber());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", license.getTenantId());

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(billingServiceHost + "/billing/v3/payments")
                    .queryParam("businessServiceCode", businessService)
                    .queryParam("consumerCodes", license.getApplicationNumber())
                    .queryParam("limit", 25)
                    .queryParam("offset", 0)
                    .build(true).toUri();
            log.info("Searching payments at: {}", uri);
            
            ResponseEntity<List> response = restTemplate.exchange(uri, HttpMethod.GET, entity, List.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ Found {} payments for license: {}", response.getBody().size(), license.getApplicationNumber());
                return response.getBody();
            }

        } catch (Exception e) {
            log.error("Failed to search payments for license: {}", license.getApplicationNumber(), e);
        }

        return new ArrayList<>();
    }

    /**
     * Create payment for trade license bill.
     */
    public String createPayment(String billId, BigDecimal amount, String paymentMode, String payerName, String payerMobile, String payerEmail, String tenantId) {
        log.info("Creating payment for bill: {} with amount: {}", billId, amount);

        Map<String, Object> paymentRequest = new HashMap<>();
        paymentRequest.put("totalAmountPaid", amount);
        paymentRequest.put("paymentMode", paymentMode != null ? paymentMode : "CASH");
        paymentRequest.put("paidBy", payerName);
        paymentRequest.put("payerName", payerName);
        paymentRequest.put("payerMobileNumber", payerMobile);
        paymentRequest.put("payerEmail", payerEmail);

        // Payment details array
        Map<String, Object> paymentDetail = new HashMap<>();
        paymentDetail.put("totalAmountPaid", amount);
        paymentDetail.put("billId", billId);
        paymentRequest.put("paymentDetails", List.of(paymentDetail));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paymentRequest, headers);

        String url = billingServiceHost + "/billing/v3/payments";
        log.info("Creating payment at: {}", url);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String paymentId = (String) response.getBody().get("id");
            log.info("Created payment: {} for bill: {}", paymentId, billId);
            return paymentId;
        }

        throw new RuntimeException("Failed to create payment - no response from billing service");
    }

    private String getOwnerName(TradeLicense license) {
        return license.getOwners() != null && !license.getOwners().isEmpty() 
            ? license.getOwners().get(0).getName() : "Unknown";
    }

    private String getOwnerMobile(TradeLicense license) {
        if (license.getOwners() != null && !license.getOwners().isEmpty()) {
            String mobile = license.getOwners().get(0).getMobileNumber();
            if (mobile != null && !mobile.isBlank()) {
                // Strip any non-digit characters except leading +
                String digits = mobile.replaceAll("[^\\d]", "");
                if (!digits.isEmpty()) {
                    return "+" + (digits.startsWith("91") && digits.length() == 12 ? digits : "91" + digits);
                }
            }
        }
        return "+919999999999"; // Default valid E.164 format
    }

    private String getOwnerEmail(TradeLicense license) {
        return license.getOwners() != null && !license.getOwners().isEmpty() 
            ? license.getOwners().get(0).getEmailId() : null;
    }

    private String getOwnerAddress(TradeLicense license) {
        if (license.getAddress() != null) {
            return license.getAddress().getBuildingName() + ", " + 
                   license.getAddress().getStreet() + ", " + 
                   license.getAddress().getLocality() + ", " + 
                   license.getAddress().getCity();
        }
        return "Address not provided";
    }

    /**
     * Searches for demands by consumer code (application number).
     * Used to fetch billing information for trade licenses.
     */
    public List<Map<String, Object>> searchDemands(String consumerCode, String tenantId) {
        log.info("Searching demands for consumer code: {}", consumerCode);

        // Use the enhanced search method with default parameters
        return searchDemandsWithFilters(businessService, consumerCode, "ACTIVE", null, null, tenantId, 25, 0);
    }

    /**
     * Gets a specific demand by its ID using the correct billing v3 API.
     */
    public Map<String, Object> getDemandById(String demandId, String tenantId) {
        log.info("Getting demand by ID: {}", demandId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        try {
            String url = billingServiceHost + "/billing/v3/demands/" + demandId;
            log.info("Getting demand at: {}", url);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ Retrieved demand with ID: {}", demandId);
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get demand by ID: {} - {}", demandId, e.getMessage());
        }

        return null;
    }

    /**
     * Searches for business services using the correct billing v3 API.
     */
    public List<Map<String, Object>> searchBusinessServices(String code, String tenantId) {
        log.info("Searching business services for code: {}", code);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        try {
            String url = billingServiceHost + "/billing/v3/business-services?code=" + code + 
                        "&isActive=true&effectiveOn=" + System.currentTimeMillis() + "&limit=25&offset=0";
            log.info("Searching business services at: {}", url);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> businessServices = (List<Map<String, Object>>) response.getBody().get("businessServices");
                
                log.info("✅ Found {} business services for code: {}", 
                        businessServices != null ? businessServices.size() : 0, code);
                
                return businessServices != null ? businessServices : new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Failed to search business services for code: {} - {}", code, e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * Searches for tax heads using the correct billing v3 API.
     */
    public List<Map<String, Object>> searchTaxHeads(String code, String businessServiceCode, String category, String tenantId) {
        log.info("Searching tax heads for code: {}, businessService: {}, category: {}", code, businessServiceCode, category);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        try {
            String url = billingServiceHost + "/billing/v3/tax-heads?code=" + code + 
                        "&businessServiceCode=" + businessServiceCode + 
                        "&category=" + category + 
                        "&isActive=true&limit=25&offset=0";
            log.info("Searching tax heads at: {}", url);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> taxHeads = (List<Map<String, Object>>) response.getBody().get("taxHeads");
                
                log.info("✅ Found {} tax heads for code: {}", 
                        taxHeads != null ? taxHeads.size() : 0, code);
                
                return taxHeads != null ? taxHeads : new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Failed to search tax heads for code: {} - {}", code, e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * Updates an existing demand using the correct billing v3 API.
     */
    public Map<String, Object> updateDemand(Map<String, Object> demandData, String tenantId) {
        log.info("Updating demand with ID: {}", demandData.get("id"));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        // Wrap in array as expected by the API
        List<Map<String, Object>> demandRequest = List.of(demandData);
        HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(demandRequest, headers);

        try {
            String url = billingServiceHost + "/billing/v3/demands";
            log.info("Updating demand at: {}", url);
            
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.PUT, entity, List.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && !response.getBody().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> updatedDemand = (Map<String, Object>) response.getBody().get(0);
                log.info("✅ Updated demand with ID: {}", updatedDemand.get("id"));
                return updatedDemand;
            }
        } catch (Exception e) {
            log.error("Failed to update demand with ID: {} - {}", demandData.get("id"), e.getMessage());
            throw new RuntimeException("Failed to update demand: " + e.getMessage(), e);
        }

        throw new RuntimeException("Failed to update demand - no response from billing service");
    }

    /**
     * Patches an existing demand using the correct billing v3 API.
     */
    public Map<String, Object> patchDemand(String demandId, Map<String, Object> patchData, String tenantId) {
        log.info("Patching demand with ID: {}", demandId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(patchData, headers);

        try {
            String url = billingServiceHost + "/billing/v3/demands/" + demandId;
            log.info("Patching demand at: {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PATCH, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ Patched demand with ID: {}", demandId);
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to patch demand with ID: {} - {}", demandId, e.getMessage());
            throw new RuntimeException("Failed to patch demand: " + e.getMessage(), e);
        }

        throw new RuntimeException("Failed to patch demand - no response from billing service");
    }

    /**
     * Freezes a demand using the correct billing v3 API.
     */
    public boolean freezeDemand(String demandId, String tenantId) {
        log.info("Freezing demand with ID: {}", demandId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            String url = billingServiceHost + "/billing/v3/demands/" + demandId + "/freeze";
            log.info("Freezing demand at: {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Frozen demand with ID: {}", demandId);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to freeze demand with ID: {} - {}", demandId, e.getMessage());
            throw new RuntimeException("Failed to freeze demand: " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Cancels a demand using the correct billing v3 API.
     */
    public boolean cancelDemand(String demandId, String reasonCode, String note, String tenantId) {
        log.info("Canceling demand with ID: {}, reason: {}", demandId, reasonCode);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        Map<String, Object> cancelRequest = new HashMap<>();
        if (reasonCode != null) {
            cancelRequest.put("reasonCode", reasonCode);
        }
        if (note != null) {
            cancelRequest.put("note", note);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(cancelRequest, headers);

        try {
            String url = billingServiceHost + "/billing/v3/demands/" + demandId + "/cancel";
            log.info("Canceling demand at: {}", url);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Canceled demand with ID: {}", demandId);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to cancel demand with ID: {} - {}", demandId, e.getMessage());
            throw new RuntimeException("Failed to cancel demand: " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Cancels a demand using the correct billing v3 API (overloaded method for backward compatibility).
     */
    public boolean cancelDemand(String demandId, String tenantId) {
        return cancelDemand(demandId, TradeLicenseConstants.DEMAND_CANCEL_REASON_SYSTEM, "Canceled by trade license service", tenantId);
    }

    /**
     * Enhanced search for demands with additional filters using the correct billing v3 API.
     */
    public List<Map<String, Object>> searchDemandsWithFilters(String businessServiceCode, String consumerCode, 
            String status, Long createdFrom, Long createdTo, String tenantId, int limit, int offset) {
        log.info("Searching demands with filters - businessService: {}, consumerCode: {}, status: {}", 
                businessServiceCode, consumerCode, status);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        try {
            StringBuilder urlBuilder = new StringBuilder(billingServiceHost + "/billing/v3/demands?");
            
            if (businessServiceCode != null) {
                urlBuilder.append("businessServiceCode=").append(businessServiceCode).append("&");
            }
            if (consumerCode != null) {
                urlBuilder.append("consumerCode=").append(consumerCode).append("&");
            }
            if (status != null) {
                urlBuilder.append("status=").append(status).append("&");
            }
            if (createdFrom != null) {
                urlBuilder.append("createdFrom=").append(createdFrom).append("&");
            }
            if (createdTo != null) {
                urlBuilder.append("createdTo=").append(createdTo).append("&");
            }
            
            urlBuilder.append("limit=").append(limit).append("&offset=").append(offset);
            
            String url = urlBuilder.toString();
            log.info("Searching demands with filters at: {}", url);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> demands = response.getBody();
                
                log.info("✅ Found {} demands with filters", 
                        demands != null ? demands.size() : 0);
                
                return demands != null ? demands : new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Failed to search demands with filters - {}", e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * Gets a bill by its ID using the correct billing v3 API.
     */
    public Map<String, Object> getBillById(String billId, String tenantId) {
        log.info("Getting bill by ID: {}", billId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        try {
            String url = billingServiceHost + "/billing/v3/bills/" + billId;
            log.info("Getting bill at: {}", url);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ Retrieved bill with ID: {}", billId);
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get bill by ID: {} - {}", billId, e.getMessage());
        }

        return null;
    }

    /**
     * Cancels an active demand for a trade license.
     * Fetches the demand by consumerCode first, then calls the cancel endpoint.
     */
    public void cancelDemand(TradeLicense license, String reasonCode, String note) {
        log.info("Cancelling demand for license: {}", license.getApplicationNumber());

        // First fetch the active demand to get its ID
        List<Map<String, Object>> demands = searchDemandsWithFilters(
                businessService, license.getApplicationNumber(), "ACTIVE",
                null, null, license.getTenantId(), 1, 0);

        if (demands.isEmpty()) {
            log.warn("No active demand found for {} — skipping demand cancellation", license.getApplicationNumber());
            return;
        }

        String demandId = (String) demands.get(0).get("id");
        if (demandId == null) {
            log.warn("Demand has no ID for {} — skipping cancellation", license.getApplicationNumber());
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", license.getTenantId());

        Map<String, Object> body = new HashMap<>();
        body.put("reasonCode", reasonCode != null ? reasonCode : "CANCELLED");
        body.put("note", note != null ? note : "Trade license cancelled");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            String url = billingServiceHost + "/billing/v3/demands/" + demandId + "/cancel";
            log.info("Cancelling demand at: {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Cancelled demand {} for license {}", demandId, license.getApplicationNumber());
            }
        } catch (Exception e) {
            log.warn("Failed to cancel demand {} for license {}: {}", demandId, license.getApplicationNumber(), e.getMessage());
        }
    }
    public Map<String, Object> getPaymentById(String paymentId, String tenantId) {
        log.info("Getting payment by ID: {}", paymentId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Client-ID", "trade-license-service");
        headers.set("X-Tenant-ID", tenantId);

        try {
            String url = billingServiceHost + "/billing/v3/payments/" + paymentId;
            log.info("Getting payment at: {}", url);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("✅ Retrieved payment with ID: {}", paymentId);
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get payment by ID: {} - {}", paymentId, e.getMessage());
        }

        return null;
    }
}