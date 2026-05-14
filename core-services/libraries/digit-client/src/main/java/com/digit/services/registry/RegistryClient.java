package org.egov.services.registry;

import org.egov.config.ApiProperties;
import org.egov.exception.DigitClientException;
import org.egov.services.registry.model.RegistryData;
import org.egov.services.registry.model.RegistryDataResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class RegistryClient {
    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public RegistryClient(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public RegistryDataResponse createRegistryData(String schemaCode, RegistryData registryData) {
        if (registryData == null) {
            throw new DigitClientException("Registry data cannot be null");
        }
        if (schemaCode == null || schemaCode.trim().isEmpty()) {
            throw new DigitClientException("Schema code cannot be null or empty");
        }
        if (registryData.getData() == null) {
            throw new DigitClientException("Data cannot be null");
        }
        try {
            log.debug("Creating registry data with schema code: {}", (Object)schemaCode);
            String url = this.apiProperties.getRegistryServiceUrl() + "/registry/v3/schema/" + schemaCode + "/data";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity response = this.restTemplate.postForEntity(url, (Object)new HttpEntity((Object)registryData, headers), RegistryDataResponse.class, new Object[0]);
            log.debug("Successfully created registry data with schema code: {}", (Object)schemaCode);
            return (RegistryDataResponse)response.getBody();
        }
        catch (Exception e) {
            if (e instanceof DigitClientException) {
                throw e;
            }
            throw new DigitClientException("Failed to create registry data: " + e.getMessage(), e);
        }
    }

    public RegistryDataResponse searchRegistryData(String schemaCode, String registryId, boolean history) {
        if (schemaCode == null || schemaCode.trim().isEmpty()) {
            throw new DigitClientException("Schema code cannot be null or empty");
        }
        if (registryId == null || registryId.trim().isEmpty()) {
            throw new DigitClientException("Registry ID cannot be null or empty");
        }
        try {
            log.debug("Searching registry data with schema code: {}, registry ID: {}, history: {}", new Object[]{schemaCode, registryId, history});
            String url = this.apiProperties.getRegistryServiceUrl() + "/registry/v3/schema/" + schemaCode + "/data/_registry?registryId=" + registryId + "&history=" + history;
            ResponseEntity response = this.restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(new HttpHeaders()), RegistryDataResponse.class, new Object[0]);
            log.debug("Successfully retrieved registry data with schema code: {}, registry ID: {}", (Object)schemaCode, (Object)registryId);
            return (RegistryDataResponse)response.getBody();
        }
        catch (Exception e) {
            if (e instanceof DigitClientException) {
                throw e;
            }
            throw new DigitClientException("Failed to retrieve registry data: " + e.getMessage(), e);
        }
    }

    public RegistryDataResponse searchRegistryData(String schemaCode, String registryId) {
        return this.searchRegistryData(schemaCode, registryId, false);
    }

    public RegistryDataResponse searchRegistryData(String schemaCode, String key, String value) {
        return this.searchRegistryData(schemaCode, key, value, null, null);
    }

    public RegistryDataResponse searchRegistryData(String schemaCode, String key, String value, Integer limit, Integer offset) {
        if (schemaCode == null || schemaCode.trim().isEmpty()) {
            throw new DigitClientException("Schema code cannot be null or empty");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new DigitClientException("Search key cannot be null or empty");
        }
        if (value == null || value.trim().isEmpty()) {
            throw new DigitClientException("Search value cannot be null or empty");
        }
        try {
            int actualLimit = limit != null ? limit : 5;
            int actualOffset = offset != null ? offset : 0;
            log.debug("Searching registry data with schema code: {}, key: {}, value: {}, limit: {}, offset: {}", new Object[]{schemaCode, key, value, actualLimit, actualOffset});
            String url = this.apiProperties.getRegistryServiceUrl() + "/registry/v3/schema/" + schemaCode + "/data/_search";
            Map<String, Object> searchRequest = new HashMap<>();
            searchRequest.put("contains", Map.of(key, value));
            searchRequest.put("limit", actualLimit);
            searchRequest.put("offset", actualOffset);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity response = this.restTemplate.exchange(url, HttpMethod.POST, new HttpEntity(searchRequest, headers), RegistryDataResponse.class, new Object[0]);
            log.debug("Successfully searched registry data with schema code: {}, key: {}, value: {}", new Object[]{schemaCode, key, value});
            return (RegistryDataResponse)response.getBody();
        }
        catch (Exception e) {
            if (e instanceof DigitClientException) {
                throw e;
            }
            throw new DigitClientException("Failed to search registry data: " + e.getMessage(), e);
        }
    }

    public RegistryDataResponse updateRegistryData(String schemaCode, RegistryData registryData, String key, String value) {
        if (schemaCode == null || schemaCode.trim().isEmpty()) {
            throw new DigitClientException("Schema code cannot be null or empty");
        }
        if (registryData == null) {
            throw new DigitClientException("Registry data cannot be null");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new DigitClientException("Search key cannot be null or empty");
        }
        if (value == null || value.trim().isEmpty()) {
            throw new DigitClientException("Search value cannot be null or empty");
        }
        if (registryData.getData() == null) {
            throw new DigitClientException("Data cannot be null");
        }
        try {
            log.debug("Updating registry data with schema code: {}, key: {}, value: {}", new Object[]{schemaCode, key, value});
            RegistryDataResponse searchResponse = this.searchRegistryData(schemaCode, key, value);
            if (searchResponse == null || searchResponse.getData() == null) {
                throw new DigitClientException("Registry data not found for key: " + key + " and value: " + value);
            }
            Integer currentVersion = this.extractVersionFromResponse(searchResponse);
            if (currentVersion == null) {
                throw new DigitClientException("Could not extract version from existing registry data");
            }
            String registryId = this.extractRegistryIdFromResponse(searchResponse);
            if (registryId == null || registryId.trim().isEmpty()) {
                throw new DigitClientException("Could not extract registry ID from existing registry data");
            }
            registryData.setVersion(currentVersion);
            String url = this.apiProperties.getRegistryServiceUrl() + "/registry/v3/schema/" + schemaCode + "/data?id=" + registryId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity response = this.restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity((Object)registryData, headers), RegistryDataResponse.class, new Object[0]);
            log.debug("Successfully updated registry data with schema code: {}, key: {}, value: {}", new Object[]{schemaCode, key, value});
            return (RegistryDataResponse)response.getBody();
        }
        catch (Exception e) {
            if (e instanceof DigitClientException) {
                throw e;
            }
            throw new DigitClientException("Failed to update registry data: " + e.getMessage(), e);
        }
    }

    private Integer extractVersionFromResponse(RegistryDataResponse response) {
        try {
            List dataList;
            if (response.getData() instanceof Map) {
                Map dataMap = (Map)response.getData();
                Object versionObj = dataMap.get("version");
                if (versionObj instanceof Integer) {
                    return (Integer)versionObj;
                }
                if (versionObj instanceof Number) {
                    return ((Number)versionObj).intValue();
                }
            } else if (response.getData() instanceof List && !(dataList = (List)response.getData()).isEmpty()) {
                Object versionObj = ((Map)dataList.get(0)).get("version");
                if (versionObj instanceof Integer) {
                    return (Integer)versionObj;
                }
                if (versionObj instanceof Number) {
                    return ((Number)versionObj).intValue();
                }
            }
            return null;
        }
        catch (Exception e) {
            log.warn("Error extracting version from response: {}", (Object)e.getMessage());
            return null;
        }
    }

    private String extractRegistryIdFromResponse(RegistryDataResponse response) {
        try {
            List dataList;
            if (response.getData() instanceof Map) {
                Map dataMap = (Map)response.getData();
                Object idObj = dataMap.get("registryId");
                if (idObj instanceof String) {
                    return (String)idObj;
                }
                if (idObj != null) {
                    return idObj.toString();
                }
            } else if (response.getData() instanceof List && !(dataList = (List)response.getData()).isEmpty()) {
                Object idObj = ((Map)dataList.get(0)).get("registryId");
                if (idObj instanceof String) {
                    return (String)idObj;
                }
                if (idObj != null) {
                    return idObj.toString();
                }
            }
            return null;
        }
        catch (Exception e) {
            log.warn("Error extracting registry ID from response: {}", (Object)e.getMessage());
            return null;
        }
    }
}