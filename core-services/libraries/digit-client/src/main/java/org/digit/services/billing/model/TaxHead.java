package org.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class TaxHead {
    @JsonProperty("id") private String id;
    @JsonProperty("businessServiceCode") private String businessServiceCode;
    @JsonProperty("code") private String code;
    @JsonProperty("name") private String name;
    @JsonProperty("description") private String description;
    @JsonProperty("order") private Integer order;
    @JsonProperty("category") private String category;
    @JsonProperty("isActive") private Boolean isActive;
    @JsonProperty("effectiveFrom") private Long effectiveFrom;
    @JsonProperty("effectiveTo") private Long effectiveTo;
    @JsonProperty("version") private Integer version;
    @JsonProperty("metadata") private Map<String, Object> metadata;
}
