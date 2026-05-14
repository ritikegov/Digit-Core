package com.digit.services.workflow.model;

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
public class WorkflowTransitionRequest {
    @JsonProperty(value="processId")
    private String processId;
    @JsonProperty(value="entityId")
    private String entityId;
    @JsonProperty(value="action")
    private String action;
    @JsonProperty(value="comment")
    private String comment;
    @JsonProperty(value="attributes")
    private Map<String, List<String>> attributes;
}