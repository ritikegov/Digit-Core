package org.digit.services.filestore;

import org.digit.config.ApiProperties;
import org.digit.config.PropagationProperties;
import org.digit.exception.DigitClientException;
import org.digit.util.HeaderStore;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Getter
@Setter
public class FilestoreClient {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;
    private final PropagationProperties propagationProperties;

    public FilestoreClient(RestTemplate restTemplate, ApiProperties apiProperties, PropagationProperties propagationProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
        this.propagationProperties = propagationProperties;
    }

    public boolean isFileAvailable(String fileId, String tenantId) {
        if (fileId == null || fileId.trim().isEmpty())
            throw new DigitClientException("File ID cannot be null or empty");
        if (tenantId == null || tenantId.trim().isEmpty())
            throw new DigitClientException("Tenant ID cannot be null or empty");
        try {
            log.debug("Checking file availability for fileId: {}, tenantId: {}", fileId, tenantId);
            String url = apiProperties.getFilestoreServiceUrl() + "/filestore/v3/files/" + fileId + "?tenantId=" + tenantId;
            
            // Create a simple GET request with minimal headers
            // The HeaderPropagationInterceptor will add the auth headers automatically
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "*/*");
            headers.set("User-Agent", "DigitClient/1.0.0");
            
            log.debug("Making GET request to filestore URL: {}", url);
            log.debug("Request headers before interceptor: {}", headers);
            
            // Use GET request - the interceptor will add Authorization, X-Tenant-Id, X-Client-Id
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            boolean available = response.getStatusCode().is2xxSuccessful();
            log.debug("File {} availability: {}", fileId, available);
            return available;
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            log.warn("Access forbidden for file {}: {} - This may indicate missing authentication or insufficient permissions", 
                    fileId, e.getMessage());
            return false;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.debug("File {} not found: {}", fileId, e.getMessage());
            return false;
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
            log.error("Bad request for file {}: {} - Response: {}", fileId, e.getMessage(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            log.error("Error checking file {} availability: {}", fileId, e.getMessage(), e);
            return false;
        }
    }

    public boolean validateFileAvailability(String fileId, String tenantId) {
        if (fileId == null || fileId.trim().isEmpty())
            throw new DigitClientException("File ID cannot be null or empty");
        if (tenantId == null || tenantId.trim().isEmpty())
            throw new DigitClientException("Tenant ID cannot be null or empty");
        try {
            log.debug("Validating file availability for fileId: {}, tenantId: {}", fileId, tenantId);
            String url = apiProperties.getFilestoreServiceUrl() + "/filestore/v3/files/" + fileId + "?tenantId=" + tenantId;
            
            // Create a simple GET request with minimal headers
            // The HeaderPropagationInterceptor will add the auth headers automatically
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "*/*");
            headers.set("User-Agent", "DigitClient/1.0.0");
            
            log.debug("Making GET request to filestore URL: {}", url);
            log.debug("Request headers before interceptor: {}", headers);
            
            // Use GET request - the interceptor will add Authorization, X-Tenant-Id, X-Client-Id
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            log.debug("File {} is available", fileId);
            return response.getStatusCode().is2xxSuccessful();
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            log.error("Access forbidden for file {}: {} - Missing authentication or insufficient permissions", 
                    fileId, e.getMessage());
            throw new DigitClientException("Access forbidden for file: " + fileId + " - Check authentication headers", e);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.error("File not found: {}", fileId);
            throw new DigitClientException("File not found: " + fileId, e);
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
            log.error("Bad request for file {}: {} - Response: {}", fileId, e.getMessage(), e.getResponseBodyAsString());
            throw new DigitClientException("Bad request for file: " + fileId + " - " + e.getMessage(), e);
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("File not available: " + fileId + " - " + e.getMessage(), e);
        }
    }
}
