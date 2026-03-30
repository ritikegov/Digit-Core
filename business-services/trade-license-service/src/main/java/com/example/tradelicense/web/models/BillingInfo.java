package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Billing information wrapper for Trade License responses.
 * 
 * This is NOT part of the Trade License domain model.
 * It's used to enrich API responses with billing data from Billing Service.
 * 
 * Following DIGIT 3.0 pattern:
 * - Trade License service manages license domain
 * - Billing Service manages demands, bills, payments
 * - Response enrichment provides integrated view without tight coupling
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillingInfo {

    // Demand information
    @JsonProperty("demandId")
    private String demandId;
    
    @JsonProperty("demandStatus")
    private String demandStatus; // ACTIVE, PAID, CANCELLED
    
    @JsonProperty("totalAmount")
    private BigDecimal totalAmount;
    
    @JsonProperty("paidAmount")
    private BigDecimal paidAmount;
    
    @JsonProperty("pendingAmount")
    private BigDecimal pendingAmount;
    
    // Bill information (if bill exists)
    @JsonProperty("billId")
    private String billId;
    
    @JsonProperty("billNumber")
    private String billNumber;
    
    @JsonProperty("billDate")
    private Long billDate;
    
    @JsonProperty("billStatus")
    private String billStatus; // ACTIVE, PAID, EXPIRED
    
    // Payment information
    @JsonProperty("payments")
    private List<Map<String, Object>> payments;
    
    // Fee breakdown
    @JsonProperty("feeDetails")
    private List<Map<String, Object>> feeDetails;
}