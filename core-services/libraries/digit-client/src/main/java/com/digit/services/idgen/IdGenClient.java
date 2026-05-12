package com.digit.services.idgen;

import com.digit.config.ApiProperties;
import com.digit.exception.DigitClientException;
import com.digit.services.idgen.model.GenerateIDResponse;
import com.digit.services.idgen.model.IdGenGenerateRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Getter
@Setter
public class IdGenClient {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public IdGenClient(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public String generateId(IdGenGenerateRequest request) {
        if (request == null) throw new DigitClientException("IdGenGenerateRequest cannot be null");
        if (request.getTemplateCode() == null || request.getTemplateCode().trim().isEmpty())
            throw new DigitClientException("Template code cannot be null or empty");
        try {
            String url = apiProperties.getIdgenServiceUrl() + "/idgen/v3/generate";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity<GenerateIDResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request, headers), GenerateIDResponse.class);
            GenerateIDResponse idResponse = response.getBody();
            String generatedId = idResponse != null ? idResponse.getId() : null;
            if (generatedId == null || generatedId.trim().isEmpty())
                throw new DigitClientException("Generated ID is null or empty");
            return generatedId;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to generate ID: " + e.getMessage(), e);
        }
    }

    public String generateId(String templateCode, Map<String, String> variables) {
        return generateId(IdGenGenerateRequest.builder().templateCode(templateCode).variables(variables).build());
    }

    public String generateId(String templateCode) {
        return generateId(IdGenGenerateRequest.builder().templateCode(templateCode).build());
    }
}
