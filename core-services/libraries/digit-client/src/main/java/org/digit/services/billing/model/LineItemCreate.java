package org.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class LineItemCreate {

    @JsonProperty("taxHeadCode")
    private String taxHeadCode;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("collectedAmount")
    private Double collectedAmount;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
