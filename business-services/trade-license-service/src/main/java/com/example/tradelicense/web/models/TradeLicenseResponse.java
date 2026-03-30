package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeLicenseResponse {

    @JsonProperty("Licenses")
    private List<TradeLicense> licenses;

    @JsonProperty("Count")
    private Integer count;

    @JsonProperty("TotalCount")
    private Integer totalCount;

    @JsonProperty("Offset")
    private Integer offset;

    @JsonProperty("Limit")
    private Integer limit;

    // DIGIT 3.0: Billing information enrichment
    // This is included when licenses have associated billing data
    @JsonProperty("BillingInfo")
    private List<BillingInfo> billingInfo;

}
