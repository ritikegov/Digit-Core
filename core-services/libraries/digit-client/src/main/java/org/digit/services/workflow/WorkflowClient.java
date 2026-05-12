package org.digit.services.workflow;

import org.digit.services.config.ApiProperties;
import org.digit.services.exception.DigitClientException;
import org.digit.services.workflow.model.WorkflowProcessResponse;
import org.digit.services.workflow.model.WorkflowTransitionRequest;
import org.digit.services.workflow.model.WorkflowTransitionResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Setter
public class WorkflowClient {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public WorkflowClient(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public WorkflowTransitionResponse executeTransition(WorkflowTransitionRequest transitionRequest) {
        if (transitionRequest == null)
            throw new DigitClientException("WorkflowTransitionRequest cannot be null");
        if (transitionRequest.getProcessId() == null || transitionRequest.getProcessId().trim().isEmpty())
            throw new DigitClientException("Process ID cannot be null or empty");
        if (transitionRequest.getEntityId() == null || transitionRequest.getEntityId().trim().isEmpty())
            throw new DigitClientException("Entity ID cannot be null or empty");
        if (transitionRequest.getAction() == null || transitionRequest.getAction().trim().isEmpty())
            throw new DigitClientException("Action cannot be null or empty");
        try {
            log.debug("Executing workflow transition for processId: {}, entityId: {}, action: {}",
                    transitionRequest.getProcessId(), transitionRequest.getEntityId(), transitionRequest.getAction());
            String url = apiProperties.getWorkflowServiceUrl() + "/workflow/v3/transition";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity<WorkflowTransitionResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(transitionRequest, headers), WorkflowTransitionResponse.class);
            WorkflowTransitionResponse transitionResponse = response.getBody();
            log.debug("Successfully executed workflow transition. Response ID: {}",
                    transitionResponse != null ? transitionResponse.getId() : "null");
            return transitionResponse;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to execute workflow transition: " + e.getMessage(), e);
        }
    }

    public WorkflowTransitionResponse executeTransition(String processId, String entityId, String action, String comment) {
        WorkflowTransitionRequest request = WorkflowTransitionRequest.builder()
                .processId(processId)
                .entityId(entityId)
                .action(action)
                .comment(comment)
                .build();
        return executeTransition(request);
    }

    public WorkflowTransitionResponse executeTransition(String processId, String entityId, String action,
                                                        String comment, Map<String, List<String>> attributes) {
        WorkflowTransitionRequest request = WorkflowTransitionRequest.builder()
                .processId(processId)
                .entityId(entityId)
                .action(action)
                .comment(comment)
                .attributes(attributes)
                .build();
        return executeTransition(request);
    }

    public WorkflowProcessResponse getProcessById(String processId) {
        if (processId == null || processId.trim().isEmpty())
            throw new DigitClientException("Process ID cannot be null or empty");
        try {
            log.debug("Retrieving workflow process with ID: {}", processId);
            String url = apiProperties.getWorkflowServiceUrl() + "/workflow/v3/process/" + processId;
            ResponseEntity<WorkflowProcessResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), WorkflowProcessResponse.class);
            log.debug("Successfully retrieved workflow process: {}", processId);
            return response.getBody();
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to retrieve workflow process: " + e.getMessage(), e);
        }
    }

    public String getProcessByCode(String code) {
        if (code == null || code.trim().isEmpty())
            throw new DigitClientException("Process code cannot be null or empty");
        try {
            log.debug("Retrieving workflow process with code: {}", code);
            String url = apiProperties.getWorkflowServiceUrl() + "/workflow/v3/process?code="
                    + URLEncoder.encode(code, StandardCharsets.UTF_8);
            ResponseEntity<List<WorkflowProcessResponse>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<List<WorkflowProcessResponse>>() {});
            List<WorkflowProcessResponse> processes = response.getBody();
            if (processes == null || processes.isEmpty())
                throw new DigitClientException("No workflow process found for code: " + code);
            String processId = processes.get(0).getId();
            log.debug("Successfully retrieved workflow process ID: {}", processId);
            return processId;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to retrieve workflow process: " + e.getMessage(), e);
        }
    }
}
