package com.digit.services.workflow.model;

import com.digit.services.common.model.AuditDetails;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkflowTransitionResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("processId")
    private String processId;

    @JsonProperty("entityId")
    private String entityId;

    @JsonProperty("action")
    private String action;

    @JsonProperty("status")
    private String status;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("documents")
    private List<String> documents;

    @JsonProperty("assigner")
    private String assigner;

    @JsonProperty("assignees")
    private List<String> assignees;

    @JsonProperty("currentState")
    private String currentState;

    @JsonProperty("stateSla")
    private Long stateSla;

    @JsonProperty("processSla")
    private Long processSla;

    @JsonProperty("attributes")
    private Map<String, List<String>> attributes;

    @JsonProperty("nextActions")
    private List<String> nextActions;

    @JsonProperty("parentInstanceId")
    private String parentInstanceId;

    @JsonProperty("branchId")
    private String branchId;

    @JsonProperty("isParallelBranch")
    private Boolean isParallelBranch;

    @JsonProperty("escalated")
    private Boolean escalated;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}
