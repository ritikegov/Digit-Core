package org.egov.services.workflow.model;

import com.digit.services.common.model.AuditDetails;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTransitionResponse {
    @JsonProperty(value="id")
    private String id;
    @JsonProperty(value="processId")
    private String processId;
    @JsonProperty(value="entityId")
    private String entityId;
    @JsonProperty(value="action")
    private String action;
    @JsonProperty(value="status")
    private String status;
    @JsonProperty(value="comment")
    private String comment;
    @JsonProperty(value="documents")
    private List<String> documents;
    @JsonProperty(value="assigner")
    private String assigner;
    @JsonProperty(value="assignees")
    private List<String> assignees;
    @JsonProperty(value="currentState")
    private String currentState;
    @JsonProperty(value="stateSla")
    private Long stateSla;
    @JsonProperty(value="processSla")
    private Long processSla;
    @JsonProperty(value="attributes")
    private Map<String, List<String>> attributes;
    @JsonProperty(value="nextActions")
    private List<String> nextActions;
    @JsonProperty(value="parentInstanceId")
    private String parentInstanceId;
    @JsonProperty(value="branchId")
    private String branchId;
    @JsonProperty(value="isParallelBranch")
    private Boolean isParallelBranch;
    @JsonProperty(value="escalated")
    private Boolean escalated;
    @JsonProperty(value="auditDetails")
    private AuditDetails auditDetails;
}