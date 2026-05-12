package com.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class GenerateBillCriteria {

    @JsonProperty("businessServiceCode")
    private String businessServiceCode;

    @JsonProperty("consumerCode")
    private String consumerCode;

    @JsonProperty("payerId")
    private String payerId;

    @JsonProperty("payerName")
    private String payerName;

    @JsonProperty("payerAddress")
    private String payerAddress;

    @JsonProperty("payerMobileNumber")
    private String payerMobileNumber;

    @JsonProperty("payerEmail")
    private String payerEmail;
}
