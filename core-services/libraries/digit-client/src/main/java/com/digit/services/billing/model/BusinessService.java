package com.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class BusinessService {
    @JsonProperty("id") private String id;
    @JsonProperty("code") private String code;
    @JsonProperty("name") private String name;
    @JsonProperty("description") private String description;
    @JsonProperty("collectionMode") private String collectionMode;
    @JsonProperty("allowedPaymentModes") private List<String> allowedPaymentModes;
    @JsonProperty("billExpiryDays") private Integer billExpiryDays;
    @JsonProperty("currency") private String currency;
    @JsonProperty("effectiveFrom") private Long effectiveFrom;
    @JsonProperty("effectiveTo") private Long effectiveTo;
    @JsonProperty("partialPaymentAllowed") private Boolean partialPaymentAllowed;
    @JsonProperty("minPayableAmount") private Double minPayableAmount;
    @JsonProperty("roundingRuleCode") private String roundingRuleCode;
    @JsonProperty("isActive") private Boolean isActive;
    @JsonProperty("version") private Integer version;
    @JsonProperty("metadata") private Map<String, Object> metadata;
}
