package com.example.tradelicense.web.models;

import org.digit.services.common.model.AuditDetails;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeUnit {

@JsonProperty("id")
private String id;

@JsonProperty("tenantId")
private String tenantId;

@JsonProperty("tradeType")
private String tradeType;

@JsonProperty("uom")
private String uom;

@JsonProperty("uomValue")
private Double uomValue;

@JsonProperty("active")
private Boolean active;

@JsonProperty("auditDetails")
private AuditDetails auditDetails;

}
