package org.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class DemandCreate {

    @JsonProperty("businessServiceCode")
    private String businessServiceCode;

    @JsonProperty("periodFrom")
    private Long periodFrom;

    @JsonProperty("periodTo")
    private Long periodTo;

    @JsonProperty("consumerCode")
    private String consumerCode;

    @JsonProperty("billExpiryDays")
    private Integer billExpiryDays;

    @JsonProperty("payer")
    private List<String> payer;

    @JsonProperty("arrearSourceDemandIds")
    private List<String> arrearSourceDemandIds;

    @JsonProperty("lineItems")
    private List<LineItemCreate> lineItems;

    @JsonProperty("status")
    private String status;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
