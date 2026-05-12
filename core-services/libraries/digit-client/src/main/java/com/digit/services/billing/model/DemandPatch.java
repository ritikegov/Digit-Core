package com.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class DemandPatch {

    @JsonProperty("consumerCode")
    private String consumerCode;

    @JsonProperty("payer")
    private List<String> payer;

    @JsonProperty("lineItems")
    private List<LineItemCreate> lineItems;

    @JsonProperty("status")
    private String status;
}
