package org.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class Demand {
    public enum DemandStatus { ACTIVE, CANCELLED, INACTIVE, ADJUSTED }

    @JsonProperty("id") private String id;
    @JsonProperty("businessServiceCode") private String businessServiceCode;
    @JsonProperty("consumerCode") private String consumerCode;
    @JsonProperty("payer") private List<String> payer;
    @JsonProperty("periodFrom") private Long periodFrom;
    @JsonProperty("periodTo") private Long periodTo;
    @JsonProperty("lineItems") private List<LineItem> lineItems;
    @JsonProperty("status") private DemandStatus status;
    @JsonProperty("totalAmount") private Double totalAmount;
    @JsonProperty("totalCollectedAmount") private Double totalCollectedAmount;
    @JsonProperty("isDemandPaid") private Boolean isDemandPaid;
    @JsonProperty("version") private Integer version;
    @JsonProperty("metadata") private Map<String, Object> metadata;
}
