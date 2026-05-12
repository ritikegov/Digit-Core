package org.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class Bill {
    @JsonProperty("id") private String id;
    @JsonProperty("consumerCode") private String consumerCode;
    @JsonProperty("businessServiceCode") private String businessServiceCode;
    @JsonProperty("payerId") private String payerId;
    @JsonProperty("payerName") private String payerName;
    @JsonProperty("payerAddress") private String payerAddress;
    @JsonProperty("payerMobileNumber") private String payerMobileNumber;
    @JsonProperty("payerEmail") private String payerEmail;
    @JsonProperty("billNumber") private String billNumber;
    @JsonProperty("billIssueAt") private Long billIssueAt;
    @JsonProperty("billExpiryAt") private Long billExpiryAt;
    @JsonProperty("totalAmount") private Double totalAmount;
    @JsonProperty("totalCollectedAmount") private Double totalCollectedAmount;
    @JsonProperty("billDetails") private List<BillDetail> billDetails;
    @JsonProperty("status") private String status;
    @JsonProperty("metadata") private Map<String, Object> metadata;
}
