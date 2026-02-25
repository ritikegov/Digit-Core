package org.egov.infra.mdms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Validated
public class MdmsTenantListRequest {

    @JsonProperty("RequestInfo")
    @Valid
    private RequestInfo requestInfo = null;
}
