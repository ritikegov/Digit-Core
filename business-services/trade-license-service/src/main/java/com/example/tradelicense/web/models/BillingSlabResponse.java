package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response wrapper for billing slab operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSlabResponse {
    
    @JsonProperty("billingSlabs")
    private List<BillingSlab> billingSlabs;
    
    @JsonProperty("count")
    private Integer count;
}
