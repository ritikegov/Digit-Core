package com.digit.services.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowProcessResponse {
    @JsonProperty(value="id")
    private String id;
    @JsonProperty(value="name")
    private String name;
    @JsonProperty(value="code")
    private String code;
    @JsonProperty(value="description")
    private String description;
    @JsonProperty(value="version")
    private String version;
    @JsonProperty(value="sla")
    private Long sla;
    @JsonProperty(value="auditDetail")
    private AuditDetail auditDetail;

    public static class AuditDetail {
        @JsonProperty(value="createdBy")
        private String createdBy;
        @JsonProperty(value="createdTime")
        private Long createdTime;
        @JsonProperty(value="modifiedBy")
        private String modifiedBy;
        @JsonProperty(value="modifiedTime")
        private Long modifiedTime;
    }
}