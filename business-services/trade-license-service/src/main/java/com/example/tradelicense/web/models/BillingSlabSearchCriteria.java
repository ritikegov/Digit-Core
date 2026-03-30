package com.example.tradelicense.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Search criteria for billing slabs (DIGIT 2.9 style - simple and focused)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSlabSearchCriteria {
    
    private String tenantId;
    private String licenseType;
    private String applicationType;
    private String structureType;
    private String tradeType;
    private String accessoryCategory;
    private String uom;
    private Double uomValue;  // Used to match against fromUom/toUom range
}
