package org.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class BillAccountDetail {

    @JsonProperty("id")
    private String id;

    @JsonProperty("billDetailId")
    private String billDetailId;

    @JsonProperty("lineItemId")
    private String lineItemId;

    @JsonProperty("taxHeadCode")
    private String taxHeadCode;

    @JsonProperty("order")
    private Integer order;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("adjustedAmount")
    private Double adjustedAmount;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
