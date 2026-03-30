package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Represents the fee calculation result for a trade license.
 * 
 * This is a TRANSIENT model (not stored in Registry) that shows the breakdown
 * of fees calculated for a trade license application. It's returned by the
 * /_calculate endpoint to show citizens the fee breakdown BEFORE they submit
 * the application.
 * 
 * The calculation is performed by TradeLicenseBillingCalculatorClient using:
 * - BillingSlabs (configuration/rates from MDMS or database)
 * - TradeLicense data (trade units, accessories, application type)
 * - Business rules (renewal rebates, penalties, adhoc adjustments)
 * 
 * Example usage:
 * 1. Citizen fills trade license form
 * 2. Frontend calls POST /trade-license/_calculate
 * 3. Backend calculates fees and returns Calculation
 * 4. Citizen sees fee breakdown and decides to proceed
 * 5. Citizen submits application (POST /trade-license/_create)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Calculation {

    // Reference to the trade license application this calculation is for
    // Example: "TL-AMARAVATI-20260310-0001-A1"
    // This links the calculation back to the license (for tracking/logging)
    @JsonProperty("applicationNumber")
    private String applicationNumber;

    // Keycloak realm name (geographic/administrative boundary)
    // Example: "AMARAVATI", "BANGALORE", "DELHI"
    // Used to fetch tenant-specific billing slabs and tax rates
    @JsonProperty("tenantId")
    private String tenantId;

    // TOTAL amount citizen needs to pay (sum of all fees + taxes)
    // This is the final payable amount shown to citizen
    // Example: 5900.00 (trade fee 3000 + accessory 1000 + penalty 1000 + tax 900)
    // BigDecimal used for precise financial calculations (no floating point errors)
    @JsonProperty("totalAmount")
    private BigDecimal totalAmount;

    // Tax amount (GST/VAT) calculated on the base fees
    // Example: If base fee is 5000 and tax rate is 18%, taxAmount = 900
    // This is INCLUDED in totalAmount (not added on top)
    // Can be 0 if no taxes apply or tax-exempt license type
    @JsonProperty("taxAmount")
    private BigDecimal taxAmount;

    // Detailed breakdown of all fee components
    // Shows citizen exactly what they're paying for
    // Example: [
    //   { feeType: "TL_TRADE_UNIT_FEE", amount: 3000, description: "RETAIL trade fee" },
    //   { feeType: "TL_ACCESSORY_FEE", amount: 1000, description: "SIGNBOARD fee" },
    //   { feeType: "TL_RENEWAL_REBATE", amount: -500, description: "Early renewal discount" },
    //   { feeType: "TL_ADHOC_PENALTY", amount: 1500, description: "Late payment penalty" }
    // ]
    // Negative amounts represent discounts/rebates/exemptions
    @JsonProperty("feeDetails")
    private List<FeeDetail> feeDetails;

    /**
     * Represents a single fee component in the calculation breakdown.
     * 
     * Each FeeDetail explains one line item in the total fee calculation.
     * Multiple FeeDetails are aggregated to get the totalAmount.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FeeDetail {

        // Type/category of this fee component
        // Standard DIGIT fee types:
        // - "TL_TRADE_UNIT_FEE" - Base fee for the trade type (RETAIL, WHOLESALE, etc.)
        // - "TL_ACCESSORY_FEE" - Fee for accessories (SIGNBOARD, HOARDING, etc.)
        // - "TL_RENEWAL_REBATE" - Discount for early renewal (negative amount)
        // - "TL_RENEWAL_PENALTY" - Penalty for late renewal (positive amount)
        // - "TL_ADHOC_PENALTY" - Manual penalty added by employee
        // - "TL_ADHOC_EXEMPTION" - Manual exemption/discount (negative amount)
        // - "TL_TAX" - Tax component (GST/VAT)
        // These codes are used by frontend to display localized labels
        @JsonProperty("feeType")
        private String feeType;

        // Amount for this fee component (can be positive or negative)
        // Positive: Fees, penalties, taxes
        // Negative: Rebates, discounts, exemptions
        // Example: 3000.00 for trade fee, -500.00 for rebate
        // BigDecimal for precise financial calculations
        @JsonProperty("amount")
        private BigDecimal amount;

        // Human-readable description of this fee component
        // Helps citizen understand what this fee is for
        // Example: "RETAIL trade fee for 100 sqm area"
        // Example: "SIGNBOARD accessory fee"
        // Example: "Early renewal discount (10%)"
        // Optional - can be null if feeType is self-explanatory
        @JsonProperty("description")
        private String description;
    }
}
