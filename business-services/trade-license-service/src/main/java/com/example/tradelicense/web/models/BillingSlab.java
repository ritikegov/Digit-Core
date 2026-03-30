package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.services.common.model.AuditDetails;

import java.math.BigDecimal;

/**
 * Billing slab model for Trade License fee calculation.
 * 
 * STORAGE: Stored in MDMS (Master Data Management Service) as configuration data.
 * 
 * BillingSlabs define the "menu" of fees - the rates and rules for calculating
 * license fees. They are master/configuration data that changes infrequently
 * (typically annually when government updates rates).
 * 
 * MDMS is appropriate for BillingSlabs because:
 * - They are configuration data (fee rates/rules), not transactional data
 * - They change infrequently (typically annually)
 * - Changes need approval and audit trail (Git version control)
 * - They are tenant-specific (different cities have different rates)
 * 
 * This follows DIGIT 2.9 patterns where BillingSlabs were stored in MDMS.
 * 
 * HOW IT WORKS:
 * 1. Government admin configures slabs in MDMS JSON files
 * 2. Files are deployed to MDMS service
 * 3. Calculator fetches slabs from MDMS at runtime
 * 4. Slabs are matched against license attributes (trade type, structure type, etc.)
 * 5. Matching slab's rate is used to calculate fee
 * 
 * MATCHING LOGIC:
 * A slab matches a license if ALL specified criteria match:
 * - licenseType (PERMANENT/TEMPORARY)
 * - applicationType (NEW/RENEWAL)
 * - structureType (IMMOVABLE/MOVABLE)
 * - tradeType (RETAIL/WHOLESALE/etc.) - for trade unit fees
 * - accessoryCategory (SIGNBOARD/HOARDING/etc.) - for accessory fees
 * - uom value falls within fromUom to toUom range
 * 
 * If multiple slabs match, the most specific one is used.
 * 
 * NOTE: No id or registryId fields needed since MDMS is file-based, not database-backed.
 * MDMS data is identified by the combination of matching criteria fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillingSlab {
    
    // Unique identifier for this billing slab (from MDMS)
    @JsonProperty("id")
    private String id;

    // Geographic/administrative boundary (e.g., "AMARAVATI")
    // This determines which city/realm this slab applies to
    @JsonProperty("tenantId")
    private String tenantId;
    
    // ========== Matching Criteria (used to find the right slab) ==========
    
    // License type filter: PERMANENT, TEMPORARY (optional - if null, applies to all)
    // Example: Some cities charge different rates for permanent vs temporary licenses
    @JsonProperty("licenseType")
    private String licenseType;
    
    // Application type filter: NEW, RENEWAL (optional - if null, applies to all)
    // Example: Renewal applications might have different rates than new applications
    @JsonProperty("applicationType")
    private String applicationType;
    
    // Structure type filter: IMMOVABLE, MOVABLE (optional - if null, applies to all)
    // Example: Movable structures (carts) might have lower rates than fixed shops
    @JsonProperty("structureType")
    private String structureType;
    
    // Trade type code for trade units (e.g., "RETAIL", "WHOLESALE", "MANUFACTURING")
    // Used when calculating trade unit fees
    // Example: RETAIL might be ₹3000/year, WHOLESALE ₹5000/year
    @JsonProperty("tradeType")
    private String tradeType;
    
    // Accessory category for accessories (e.g., "SIGNBOARD", "HOARDING", "GENERATOR")
    // Used when calculating accessory fees
    // Example: SIGNBOARD might be ₹500, GENERATOR ₹2000
    @JsonProperty("accessoryCategory")
    private String accessoryCategory;
    
    // ========== Calculation Fields ==========
    
    // Calculation type: "FLAT" (fixed amount) or "RATE" (multiply by UOM)
    // FLAT: Fee is fixed regardless of size/quantity (e.g., ₹500 for any generator)
    // RATE: Fee is rate × UOM value (e.g., ₹10/sqft × 500 sqft = ₹5000)
    @JsonProperty("type")
    private String type;
    
    // Unit of measurement: "SQFT", "SQMETER", "KW", "NUMBER", etc.
    // Defines what unit the rate is based on
    // Example: "SQFT" for area-based fees, "KW" for generator capacity
    @JsonProperty("uom")
    private String uom;
    
    // UOM range for tiered pricing (optional)
    // Example: 0-500 sqft = ₹10/sqft, 501-1000 sqft = ₹8/sqft (volume discount)
    // If null, applies to any UOM value
    @JsonProperty("fromUom")
    private Double fromUom;
    
    @JsonProperty("toUom")
    private Double toUom;
    
    // The fee amount (BigDecimal for precise financial calculations)
    // For FLAT type: This is the total fee (e.g., ₹500.00)
    // For RATE type: This is the rate per UOM (e.g., ₹10.00 per sqft)
    @JsonProperty("rate")
    private BigDecimal rate;
    
    // Flat amount (alternative to rate for FLAT type billing)
    @JsonProperty("flatAmount")
    private BigDecimal flatAmount;
    
    // Area-based billing fields (for area-based calculations)
    @JsonProperty("minArea")
    private Double minArea;
    
    @JsonProperty("maxArea")
    private Double maxArea;
    
    // Whether this billing slab is active/enabled (from MDMS)
    @JsonProperty("active")
    private Boolean active;

    // Audit trail: who created/modified this slab and when
    // In MDMS, this is typically managed by Git commits, but kept for compatibility
    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
    
    /**
     * Billing slab type enum for reference
     */
    public enum SlabType {
        FLAT,   // Fixed rate regardless of UOM (e.g., ₹500 for generator)
        RATE    // Rate multiplied by UOM value (e.g., ₹10 × 500 sqft = ₹5000)
    }
}

