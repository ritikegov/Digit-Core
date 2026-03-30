package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeLicenseSearchCriteria {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("applicationNumber")
    private String applicationNumber;

    @JsonProperty("licenseNumbers")
    private List<String> licenseNumbers;

    @JsonProperty("status")
    private List<String> status;

    @JsonProperty("mobileNumber")
    private String mobileNumber;

    @JsonProperty("tradeName")
    private String tradeName;

    @JsonProperty("ownerName")
    private String ownerName;

    @JsonProperty("fromDate")
    private Long fromDate;

    @JsonProperty("toDate")
    private Long toDate;

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("locality")
    private String locality;

    @JsonProperty("tradeType")
    private String tradeType;

    @JsonProperty("sortBy")
    private String sortBy;

    @JsonProperty("sortOrder")
    private String sortOrder; // ASC | DESC

}
