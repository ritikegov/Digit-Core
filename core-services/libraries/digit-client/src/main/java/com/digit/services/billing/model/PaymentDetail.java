package com.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class PaymentDetail {
    @JsonProperty("id") private String id;
    @JsonProperty("paymentId") private String paymentId;
    @JsonProperty("billId") private String billId;
    @JsonProperty("totalAmountPaid") private Double totalAmountPaid;
    @JsonProperty("totalAmountDue") private Double totalAmountDue;
    @JsonProperty("businessServiceCode") private String businessServiceCode;
    @JsonProperty("manualReceiptNumber") private String manualReceiptNumber;
    @JsonProperty("manualReceiptDate") private Long manualReceiptDate;
    @JsonProperty("receiptNumber") private String receiptNumber;
    @JsonProperty("receiptDate") private Long receiptDate;
    @JsonProperty("receiptType") private String receiptType;
    @JsonProperty("bill") private Bill bill;
    @JsonProperty("metadata") private Map<String, Object> metadata;
}
