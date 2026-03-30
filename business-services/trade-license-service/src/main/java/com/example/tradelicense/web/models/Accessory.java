package com.example.tradelicense.web.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.digit.services.common.model.AuditDetails;

/**
 * Accessory model for Trade License.
 * Accessories are additional items like signboards, display boards, etc.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Accessory {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("accessoryCategory")
    private String accessoryCategory;  // SIGNBOARD, DISPLAY_BOARD, etc.

    @JsonProperty("uom")
    private String uom;  // Unit of measurement

    @JsonProperty("uomValue")
    private Double uomValue;  // Value in the specified UOM

    @JsonProperty("count")
    private Integer count;  // Number of accessories

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}
