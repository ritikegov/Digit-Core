package com.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class Payment {
    @JsonProperty("id") private String id;
    @JsonProperty("totalAmountPaid") private Double totalAmountPaid;
    @JsonProperty("totalAmountDue") private Double totalAmountDue;
    @JsonProperty("transactionNumber") private String transactionNumber;
    @JsonProperty("transactionDate") private Long transactionDate;
    @JsonProperty("paymentMode") private String paymentMode;
    @JsonProperty("instrumentDate") private Long instrumentDate;
    @JsonProperty("instrumentNumber") private String instrumentNumber;
    @JsonProperty("instrumentStatus") private String instrumentStatus;
    @JsonProperty("paymentStatus") private String paymentStatus;
    @JsonProperty("paidBy") private String paidBy;
    @JsonProperty("payerId") private String payerId;
    @JsonProperty("payerName") private String payerName;
    @JsonProperty("payerAddress") private String payerAddress;
    @JsonProperty("payerMobileNumber") private String payerMobileNumber;
    @JsonProperty("payerEmail") private String payerEmail;
    @JsonProperty("fileStoreId") private String fileStoreId;
    @JsonProperty("paymentDetails") private List<PaymentDetail> paymentDetails;
    @JsonProperty("metadata") private Map<String, Object> metadata;
}
