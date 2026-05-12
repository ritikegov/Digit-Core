package com.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class CancelDemandRequest {

    @JsonProperty("reasonCode")
    private String reasonCode;

    @JsonProperty("note")
    private String note;
}
