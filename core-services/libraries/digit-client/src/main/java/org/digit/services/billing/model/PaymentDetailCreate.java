package org.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class PaymentDetailCreate {

    @JsonProperty("totalAmountPaid")
    private Double totalAmountPaid;

    @JsonProperty("manualReceiptNumber")
    private String manualReceiptNumber;

    @JsonProperty("manualReceiptDate")
    private Long manualReceiptDate;

    @JsonProperty("billId")
    private String billId;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
