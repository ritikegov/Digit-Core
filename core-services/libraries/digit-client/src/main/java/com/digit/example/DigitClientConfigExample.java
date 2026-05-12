package com.digit.example;

import com.digit.factory.DigitClientFactory;
import com.digit.services.boundary.BoundaryClient;
import com.digit.services.workflow.WorkflowClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

//@Configuration
public class DigitClientConfigExample {

    @Bean
    public BoundaryClient boundaryClient(@Value("${digit.services.boundary.base-url:http://localhost:8080}") String baseUrl) {
        return DigitClientFactory.createBoundaryClient(baseUrl);
    }

    @Bean
    public WorkflowClient workflowClient(@Value("${digit.services.workflow.base-url:http://localhost:8085}") String baseUrl) {
        return DigitClientFactory.createWorkflowClient(baseUrl);
    }
}
