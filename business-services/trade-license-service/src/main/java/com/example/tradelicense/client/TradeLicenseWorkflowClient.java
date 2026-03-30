package com.example.tradelicense.client;

import org.digit.services.workflow.WorkflowClient;
import org.digit.services.workflow.model.WorkflowTransitionRequest;
import org.digit.services.workflow.model.WorkflowTransitionResponse;
import com.example.tradelicense.web.models.TradeLicense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TradeLicenseWorkflowClient {

    private final WorkflowClient workflowClient;

    @Value("${tl.workflow.processCode}")
    private String processCode;

    @Value("${tl.workflow.defaultAction:APPLY}")
    private String defaultAction;

    @Value("${tl.workflow.defaultRole:CITIZEN}")
    private String defaultRole;

    public TradeLicenseWorkflowClient(WorkflowClient workflowClient,
                                      @Qualifier("billingRestTemplate") RestTemplate restTemplate) {
        this.workflowClient = workflowClient;
    }

    public WorkflowTransitionResponse startWorkflow(TradeLicense license) {
        log.info("Starting workflow for applicationNumber={}", license.getApplicationNumber());

        WorkflowTransitionRequest request = WorkflowTransitionRequest.builder()
                .processId(workflowClient.getProcessByCode(processCode))
                .entityId(license.getApplicationNumber())
                .action(license.getAction() != null ? license.getAction() : defaultAction)
                .comment("Trade License application submitted")
                .attributes(buildAttributes(license))
                .build();

        WorkflowTransitionResponse response = workflowClient.executeTransition(request);
        if (response == null) {
            throw new RuntimeException("Workflow service returned null response for application: "
                    + license.getApplicationNumber());
        }

        log.info("Workflow started for applicationNumber={}", license.getApplicationNumber());
        return response;
    }

    public WorkflowTransitionResponse updateWorkflow(TradeLicense license) {
        String action = license.getAction();
        if (action == null || action.isBlank()) {
            throw new RuntimeException("Workflow action is required for update operation");
        }

        log.info("Updating workflow for applicationNumber={}, action={}", license.getApplicationNumber(), action);

        WorkflowTransitionRequest request = WorkflowTransitionRequest.builder()
                .processId(workflowClient.getProcessByCode(processCode))
                .entityId(license.getApplicationNumber())
                .action(action)
                .comment("Trade License workflow transition")
                .attributes(buildAttributes(license))
                .build();

        WorkflowTransitionResponse response = workflowClient.executeTransition(request);
        if (response == null) {
            throw new RuntimeException("Workflow service returned null response for application: "
                    + license.getApplicationNumber());
        }

        log.info("Workflow updated for applicationNumber={}, action={}", license.getApplicationNumber(), action);
        return response;
    }

    private Map<String, List<String>> buildAttributes(TradeLicense license) {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("tenantId", Collections.singletonList(license.getTenantId()));
        if (license.getRoles() != null && !license.getRoles().isEmpty()) {
            attributes.put("roles", license.getRoles());
        } else {
            attributes.put("roles", Collections.singletonList(defaultRole));
        }
        if (license.getAssignee() != null && !license.getAssignee().isEmpty()) {
            attributes.put("assignees", license.getAssignee());
        }
        return attributes;
    }
}
