package org.digit.services.registry;

import org.digit.config.ApiProperties;
import org.digit.exception.DigitClientException;
import org.digit.services.registry.model.RegistryData;
import org.digit.services.registry.model.RegistryDataResponse;
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
public class RegistryClient {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public RegistryClient(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public RegistryDataResponse createRegistryData(String schemaCode, RegistryData registryData) {
        if (registryData == null)
            throw new DigitClientException("Registry data cannot be null");
        if (schemaCode == null || schemaCode.trim().isEmpty())
            throw new DigitClientException("Schema code cannot be null or empty");
        if (registryData.getData() == null)
            throw new DigitClientException("Data cannot be null");
        try {
            log.debug("Creating registry data with schema code: {}", schemaCode);
            String url = apiProperties.getRegistryServiceUrl() + "/registry/v3/schema/" + schemaCode + "/data";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity<RegistryDataResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(registryData, headers), RegistryDataResponse.class);
            log.debug("Successfully created registry data with schema code: {}", schemaCode);
            return response.getBody();
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to create registry data: " + e.getMessage(), e);
        }
    }

    public RegistryDataResponse searchRegistryData(String schemaCode, String registryId, boolean history) {
        if (schemaCode == null || schemaCode.trim().isEmpty())
            throw new DigitClientException("Schema code cannot be null or empty");
        if (registryId == null || registryId.trim().isEmpty())
            throw new DigitClientException("Registry ID cannot be null or empty");
        try {
            log.debug("Searching registry data with schema code: {}, registry ID: {}, history: {}", schemaCode, registryId, history);
            String url = apiProperties.getRegistryServiceUrl() + "/registry/v3/schema/" + schemaCode
                    + "/data/_registry?registryId=" + registryId + "&history=" + history;
            ResponseEntity<RegistryDataResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), RegistryDataResponse.class);
            log.debug("Successfully retrieved registry data with schema code: {}, registry ID: {}", schemaCode, registryId);
            return response.getBody();
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to retrieve registry data: " + e.getMessage(), e);
        }
    }

    public RegistryDataResponse searchRegistryData(String schemaCode, String registryId) {
        return searchRegistryData(schemaCode, registryId, false);
    }

    public RegistryDataResponse searchRegistryData(String schemaCode, String key, String value) {
        return searchRegistryData(schemaCode, key, value, null, null);
    }

    public RegistryDataResponse searchRegistryData(String schemaCode, String key, String value, Integer limit, Integer offset) {
        if (schemaCode == null || schemaCode.trim().isEmpty())
            throw new DigitClientException("Schema code cannot be null or empty");
        if (key == null || key.trim().isEmpty())
            throw new DigitClientException("Search key cannot be null or empty");
        if (value == null || value.trim().isEmpty())
            throw new DigitClientException("Search value cannot be null or empty");
        try {
            int actualLimit = (limit != null) ? limit : 5;
            int actualOffset = (offset != null) ? offset : 0;
            log.debug("Searching registry data with schema code: {}, key: {}, value: {}, limit: {}, offset: {}",
                    schemaCode, key, value, actualLimit, actualOffset);
            String url = apiProperties.getRegistryServiceUrl() + "/registry/v3/schema/" + schemaCode + "/data/_search";
            Map<String, Object> searchRequest = Map.of(
                    "contains", Map.of(key, value),
                    "limit", actualLimit,
                    "offset", actualOffset
            );
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity<RegistryDataResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(searchRequest, headers), RegistryDataResponse.class);
            log.debug("Successfully searched registry data with schema code: {}, key: {}, value: {}", schemaCode, key, value);
            return response.getBody();
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to search registry data: " + e.getMessage(), e);
        }
    }

    public RegistryDataResponse updateRegistryData(String schemaCode, RegistryData registryData, String key, String value) {
        if (schemaCode == null || schemaCode.trim().isEmpty())
            throw new DigitClientException("Schema code cannot be null or empty");
        if (registryData == null)
            throw new DigitClientException("Registry data cannot be null");
        if (key == null || key.trim().isEmpty())
            throw new DigitClientException("Search key cannot be null or empty");
        if (value == null || value.trim().isEmpty())
            throw new DigitClientException("Search value cannot be null or empty");
        if (registryData.getData() == null)
            throw new DigitClientException("Data cannot be null");
        try {
            log.debug("Updating registry data with schema code: {}, key: {}, value: {}", schemaCode, key, value);
            RegistryDataResponse searchResponse = searchRegistryData(schemaCode, key, value);
            if (searchResponse == null || searchResponse.getData() == null)
                throw new DigitClientException("Registry data not found for key: " + key + " and value: " + value);
            Integer currentVersion = extractVersionFromResponse(searchResponse);
            if (currentVersion == null)
                throw new DigitClientException("Could not extract version from existing registry data");
            String registryId = extractRegistryIdFromResponse(searchResponse);
            if (registryId == null || registryId.trim().isEmpty())
                throw new DigitClientException("Could not extract registry ID from existing registry data");
            registryData.setVersion(currentVersion);
            String url = apiProperties.getRegistryServiceUrl() + "/registry/v3/schema/" + schemaCode + "/data?id=" + registryId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity<RegistryDataResponse> response = restTemplate.exchange(
                    url, HttpMethod.PUT, new HttpEntity<>(registryData, headers), RegistryDataResponse.class);
            log.debug("Successfully updated registry data with schema code: {}, key: {}, value: {}", schemaCode, key, value);
            return response.getBody();
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to update registry data: " + e.getMessage(), e);
        }
    }

    private Integer extractVersionFromResponse(RegistryDataResponse response) {
        try {
            if (response.getData() instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) response.getData();
                Object versionObj = dataMap.get("version");
                if (versionObj instanceof Integer) return (Integer) versionObj;
                else if (versionObj instanceof Number) return ((Number) versionObj).intValue();
            } else if (response.getData() instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> dataList = (java.util.List<java.util.Map<String, Object>>) response.getData();
                if (!dataList.isEmpty()) {
                    Object versionObj = dataList.get(0).get("version");
                    if (versionObj instanceof Integer) return (Integer) versionObj;
                    else if (versionObj instanceof Number) return ((Number) versionObj).intValue();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Error extracting version from response: {}", e.getMessage());
            return null;
        }
    }

    private String extractRegistryIdFromResponse(RegistryDataResponse response) {
        try {
            if (response.getData() instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) response.getData();
                Object idObj = dataMap.get("registryId");
                if (idObj instanceof String) return (String) idObj;
                else if (idObj != null) return idObj.toString();
            } else if (response.getData() instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> dataList = (java.util.List<java.util.Map<String, Object>>) response.getData();
                if (!dataList.isEmpty()) {
                    Object idObj = dataList.get(0).get("registryId");
                    if (idObj instanceof String) return (String) idObj;
                    else if (idObj != null) return idObj.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Error extracting registry ID from response: {}", e.getMessage());
            return null;
        }
    }
}
