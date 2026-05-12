package com.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class PaymentCreate {

    @JsonProperty("totalAmountPaid")
    private Double totalAmountPaid;

    @JsonProperty("transactionNumber")
    private String transactionNumber;

    @JsonProperty("transactionDate")
    private Long transactionDate;

    @JsonProperty("paymentMode")
    private String paymentMode;

    @JsonProperty("instrumentNumber")
    private String instrumentNumber;

    @JsonProperty("instrumentDate")
    private Long instrumentDate;

    @JsonProperty("ifscCode")
    private String ifscCode;

    @JsonProperty("paidBy")
    private String paidBy;

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

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("paymentDetails")
    private List<PaymentDetailCreate> paymentDetails;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
