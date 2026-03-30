package com.example.tradelicense.client;

import com.example.tradelicense.web.models.TradeLicense;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digit.exception.DigitClientException;
import org.digit.services.registry.RegistryClient;
import org.digit.services.registry.model.RegistryData;
import org.digit.services.registry.model.RegistryDataResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wraps the DIGIT RegistryClient with Trade License specific operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeLicenseRegistryClient {

    private final RegistryClient registryClient;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${tl.registry.schema-code}")
    private String schemaCode;

    @Value("${digit.services.registry.base-url}")
    private String registryBaseUrl;

    public RegistryDataResponse createTradeLicense(TradeLicense tradeLicense) {
        log.info("Creating TL registry record applicationNumber={}, tenantId={}",
                tradeLicense.getApplicationNumber(), tradeLicense.getTenantId());

        com.fasterxml.jackson.databind.JsonNode dataNode = objectMapper.valueToTree(tradeLicense);
        RegistryData registryData = RegistryData.builder()
                .schemaCode(schemaCode)
                .data(dataNode)
                .build();

        RegistryDataResponse response = registryClient.createRegistryData(schemaCode, registryData);
        log.info("Registry create response: success={}", response != null && Boolean.TRUE.equals(response.getSuccess()));
        return response;
    }

    /**
     * Updates trade license by first searching for the record to get registryId and version,
     * then issuing a PUT request directly (avoids the library's "contains" filter matching multiple records).
     */
    public RegistryDataResponse updateTradeLicense(TradeLicense tradeLicense) {
        log.info("Updating TL registry record applicationNumber={}", tradeLicense.getApplicationNumber());

        RegistryDataResponse searchResponse = registryClient.searchRegistryData(
                schemaCode, "applicationNumber", tradeLicense.getApplicationNumber());

        String[] idAndVersion = extractRegistryIdAndVersionByApplicationNumber(
                searchResponse, tradeLicense.getApplicationNumber());
        String registryId = idAndVersion[0];
        String version = idAndVersion[1];

        if (registryId == null || registryId.isBlank()) {
            throw new DigitClientException(
                    "Could not find registry record for applicationNumber=" + tradeLicense.getApplicationNumber());
        }

        log.info("Found registryId={}, version={} for applicationNumber={}",
                registryId, version, tradeLicense.getApplicationNumber());

        com.fasterxml.jackson.databind.JsonNode dataNode = objectMapper.valueToTree(tradeLicense);
        Integer versionInt = version != null ? Integer.valueOf(version) : null;
        RegistryData registryData = RegistryData.builder()
                .schemaCode(schemaCode)
                .data(dataNode)
                .version(versionInt)
                .build();

        String url = registryBaseUrl + "/registry/v1/schema/" + schemaCode + "/data?id=" + registryId;
        log.info("Updating registry at: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<RegistryData> entity = new HttpEntity<>(registryData, headers);

        var response = restTemplate.exchange(url, HttpMethod.PUT, entity, RegistryDataResponse.class);
        RegistryDataResponse result = response.getBody();
        log.info("Registry update response: success={}", result != null && Boolean.TRUE.equals(result.getSuccess()));
        return result;
    }

    /**
     * Searches the registry response for a record whose applicationNumber exactly matches.
     * Returns [registryId, version] or [null, null] if not found.
     */
    @SuppressWarnings("unchecked")
    private String[] extractRegistryIdAndVersionByApplicationNumber(RegistryDataResponse response, String applicationNumber) {
        if (response == null || response.getData() == null) return new String[]{null, null};

        List<Map<String, Object>> items = new ArrayList<>();
        if (response.getData() instanceof List) {
            items = (List<Map<String, Object>>) response.getData();
        } else if (response.getData() instanceof Map) {
            items.add((Map<String, Object>) response.getData());
        }

        for (Map<String, Object> item : items) {
            Object dataObj = item.get("data");
            if (dataObj instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                if (applicationNumber.equals(dataMap.get("applicationNumber"))) {
                    String rid = item.get("registryId") != null ? item.get("registryId").toString() : null;
                    String ver = item.get("version") != null ? item.get("version").toString() : null;
                    return new String[]{rid, ver};
                }
            }
        }
        return new String[]{null, null};
    }

    public TradeLicense findByApplicationNumber(String applicationNumber, String tenantId) {
        log.info("Searching TL by applicationNumber={}, tenantId={}", applicationNumber, tenantId);
        RegistryDataResponse response = registryClient.searchRegistryData(
                schemaCode, "applicationNumber", applicationNumber);
        return extractSingleLicense(response, "applicationNumber", applicationNumber);
    }

    public List<TradeLicense> findByLicenseNumber(String licenseNumber, String tenantId) {
        log.info("Searching TL by licenseNumber={}, tenantId={}", licenseNumber, tenantId);
        RegistryDataResponse response = registryClient.searchRegistryData(
                schemaCode, "licenseNumber", licenseNumber);
        return extractLicenseList(response, "licenseNumber", licenseNumber);
    }

    public List<TradeLicense> findByTradeName(String tradeName, String tenantId) {
        log.info("Searching TL by tradeName={}, tenantId={}", tradeName, tenantId);
        RegistryDataResponse response = registryClient.searchRegistryData(
                schemaCode, "tradeName", tradeName);
        return extractLicenseList(response, "tradeName", tradeName);
    }

    public List<TradeLicense> findByMobileNumber(String mobileNumber, String tenantId) {
        log.info("Searching TL by mobileNumber={}, tenantId={}", mobileNumber, tenantId);
        RegistryDataResponse response = registryClient.searchRegistryData(
                schemaCode, "owners.mobileNumber", mobileNumber);
        return extractLicenseList(response, "mobileNumber", mobileNumber);
    }

    public List<TradeLicense> findByTenantId(String tenantId) {
        log.info("Searching all TL for tenantId={}", tenantId);
        RegistryDataResponse response = registryClient.searchRegistryData(
                schemaCode, "tenantId", tenantId);
        return extractLicenseList(response, "tenantId", tenantId);
    }

    private TradeLicense extractSingleLicense(RegistryDataResponse response, String searchField, String searchValue) {
        if (response == null || !Boolean.TRUE.equals(response.getSuccess()) || response.getData() == null) {
            log.warn("No TL found with {}={}", searchField, searchValue);
            return null;
        }

        if (response.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) response.getData();
            Object tradeLicenseData = dataMap.get("data");
            if (tradeLicenseData != null) {
                return objectMapper.convertValue(tradeLicenseData, TradeLicense.class);
            }
        } else if (response.getData() instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.getData();
            if (!dataList.isEmpty()) {
                Object tradeLicenseData = dataList.get(0).get("data");
                if (tradeLicenseData != null) {
                    return objectMapper.convertValue(tradeLicenseData, TradeLicense.class);
                }
            }
        }

        log.warn("No valid TL data found with {}={}", searchField, searchValue);
        return null;
    }

    private List<TradeLicense> extractLicenseList(RegistryDataResponse response, String searchField, String searchValue) {
        List<TradeLicense> licenses = new ArrayList<>();

        if (response == null || !Boolean.TRUE.equals(response.getSuccess()) || response.getData() == null) {
            log.warn("No TL found with {}={}", searchField, searchValue);
            return licenses;
        }

        if (response.getData() instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.getData();
            for (Map<String, Object> item : dataList) {
                Object tradeLicenseData = item.get("data");
                if (tradeLicenseData != null) {
                    licenses.add(objectMapper.convertValue(tradeLicenseData, TradeLicense.class));
                }
            }
        } else if (response.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) response.getData();
            Object tradeLicenseData = dataMap.get("data");
            if (tradeLicenseData != null) {
                licenses.add(objectMapper.convertValue(tradeLicenseData, TradeLicense.class));
            }
        }

        log.info("Found {} TL records with {}={}", licenses.size(), searchField, searchValue);
        return licenses;
    }
}
