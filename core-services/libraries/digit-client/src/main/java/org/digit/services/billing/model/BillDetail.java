package org.digit.services.billing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class BillDetail {
    @JsonProperty("id") private String id;
    @JsonProperty("billId") private String billId;
    @JsonProperty("demandId") private String demandId;
    @JsonProperty("periodFrom") private Long periodFrom;
    @JsonProperty("periodTo") private Long periodTo;
    @JsonProperty("amount") private Double amount;
    @JsonProperty("amountPaid") private Double amountPaid;
    @JsonProperty("billAccountDetails") private List<BillAccountDetail> billAccountDetails;
    @JsonProperty("metadata") private Map<String, Object> metadata;
}
