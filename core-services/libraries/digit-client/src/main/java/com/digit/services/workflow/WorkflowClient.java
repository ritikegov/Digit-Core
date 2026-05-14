package com.digit.services.workflow;

import com.digit.config.ApiProperties;
import com.digit.exception.DigitClientException;
import com.digit.services.workflow.model.WorkflowProcessResponse;
import com.digit.services.workflow.model.WorkflowTransitionRequest;
import com.digit.services.workflow.model.WorkflowTransitionResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class WorkflowClient {
    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public WorkflowClient(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public WorkflowTransitionResponse executeTransition(WorkflowTransitionRequest transitionRequest) {
        if (transitionRequest == null) {
            throw new DigitClientException("WorkflowTransitionRequest cannot be null");
        }
        if (transitionRequest.getProcessId() == null || transitionRequest.getProcessId().trim().isEmpty()) {
            throw new DigitClientException("Process ID cannot be null or empty");
        }
        if (transitionRequest.getEntityId() == null || transitionRequest.getEntityId().trim().isEmpty()) {
            throw new DigitClientException("Entity ID cannot be null or empty");
        }
        if (transitionRequest.getAction() == null || transitionRequest.getAction().trim().isEmpty()) {
            throw new DigitClientException("Action cannot be null or empty");
        }
        try {
            log.debug("Executing workflow transition for processId: {}, entityId: {}, action: {}", new Object[]{transitionRequest.getProcessId(), transitionRequest.getEntityId(), transitionRequest.getAction()});
            String url = this.apiProperties.getWorkflowServiceUrl() + "/workflow/v3/transition";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity response = this.restTemplate.exchange(url, HttpMethod.POST, new HttpEntity((Object)transitionRequest, headers), WorkflowTransitionResponse.class, new Object[0]);
            WorkflowTransitionResponse transitionResponse = (WorkflowTransitionResponse)response.getBody();
            log.debug("Successfully executed workflow transition. Response ID: {}", (Object)(transitionResponse != null ? transitionResponse.getId() : "null"));
            return transitionResponse;
        }
        catch (Exception e) {
            if (e instanceof DigitClientException) {
                throw e;
            }
            throw new DigitClientException("Failed to execute workflow transition: " + e.getMessage(), e);
        }
    }

    public WorkflowTransitionResponse executeTransition(String processId, String entityId, String action, String comment) {
        WorkflowTransitionRequest request = WorkflowTransitionRequest.builder().processId(processId).entityId(entityId).action(action).comment(comment).build();
        return this.executeTransition(request);
    }

    public WorkflowTransitionResponse executeTransition(String processId, String entityId, String action, String comment, Map<String, List<String>> attributes) {
        WorkflowTransitionRequest request = WorkflowTransitionRequest.builder().processId(processId).entityId(entityId).action(action).comment(comment).attributes(attributes).build();
        return this.executeTransition(request);
    }

    public WorkflowProcessResponse getProcessById(String processId) {
        if (processId == null || processId.trim().isEmpty()) {
            throw new DigitClientException("Process ID cannot be null or empty");
        }
        try {
            log.debug("Retrieving workflow process with ID: {}", (Object)processId);
            String url = this.apiProperties.getWorkflowServiceUrl() + "/workflow/v3/process/" + processId;
            ResponseEntity response = this.restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(new HttpHeaders()), WorkflowProcessResponse.class, new Object[0]);
            log.debug("Successfully retrieved workflow process: {}", (Object)processId);
            return (WorkflowProcessResponse)response.getBody();
        }
        catch (Exception e) {
            if (e instanceof DigitClientException) {
                throw e;
            }
            throw new DigitClientException("Failed to retrieve workflow process: " + e.getMessage(), e);
        }
    }

    public String getProcessByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new DigitClientException("Process code cannot be null or empty");
        }
        try {
            log.debug("Retrieving workflow process with code: {}", (Object)code);
            String url = this.apiProperties.getWorkflowServiceUrl() + "/workflow/v3/process?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
            ResponseEntity response = this.restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(new HttpHeaders()), new ParameterizedTypeReference<List<WorkflowProcessResponse>>(){}, new Object[0]);
            List processes = (List)response.getBody();
            if (processes == null || processes.isEmpty()) {
                throw new DigitClientException("No workflow process found for code: " + code);
            }
            String processId = ((WorkflowProcessResponse)processes.get(0)).getId();
            log.debug("Successfully retrieved workflow process ID: {}", (Object)processId);
            return processId;
        }
        catch (Exception e) {
            if (e instanceof DigitClientException) {
                throw e;
            }
            throw new DigitClientException("Failed to retrieve workflow process: " + e.getMessage(), e);
        }
    }
}