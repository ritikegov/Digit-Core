package com.digit.services.mdms;

import com.digit.config.ApiProperties;
import com.digit.exception.DigitClientException;
import com.digit.services.mdms.model.Mdms;
import com.digit.services.mdms.model.MdmsResponseV2;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class MdmsClient {
    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public MdmsClient(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public boolean isMdmsDataValid(String schemaCode, Set<String> uniqueIdentifiers) {
        if (schemaCode == null || schemaCode.trim().isEmpty()) {
            throw new DigitClientException("Schema code cannot be null or empty");
        }
        if (uniqueIdentifiers == null || uniqueIdentifiers.isEmpty()) {
            throw new DigitClientException("Unique identifiers set cannot be null or empty");
        }
        try {
            log.debug("Validating MDMS data for schemaCode: {}, uniqueIdentifiers: {}", (Object)schemaCode, uniqueIdentifiers);
            List<Mdms> mdmsList = this.searchMdmsData(schemaCode, uniqueIdentifiers);
            int foundCount = mdmsList != null ? mdmsList.size() : 0;
            boolean allValid = foundCount == uniqueIdentifiers.size();
            log.debug("MDMS validation result: {} ({} out of {} found)", new Object[]{allValid ? "valid" : "invalid", foundCount, uniqueIdentifiers.size()});
            return allValid;
        }
        catch (Exception e) {
            if (e instanceof DigitClientException) {
                throw e;
            }
            throw new DigitClientException("Failed to validate MDMS data: " + e.getMessage(), e);
        }
    }

    public List<Mdms> searchMdmsData(String schemaCode, Set<String> uniqueIdentifiers) {
        if (schemaCode == null || schemaCode.trim().isEmpty()) {
            throw new DigitClientException("Schema code cannot be null or empty");
        }
        if (uniqueIdentifiers == null || uniqueIdentifiers.isEmpty()) {
            throw new DigitClientException("Unique identifiers set cannot be null or empty");
        }
        try {
            log.debug("Searching MDMS data for schemaCode: {}, uniqueIdentifiers: {}", (Object)schemaCode, uniqueIdentifiers);
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString((String)(this.apiProperties.getMdmsServiceUrl() + "/mdms/v3/data")).queryParam("schemaCode", new Object[]{schemaCode});
            for (String uid : uniqueIdentifiers) {
                uriBuilder.queryParam("uniqueIdentifiers", new Object[]{uid});
            }
            ResponseEntity response = this.restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.GET, new HttpEntity(new HttpHeaders()), MdmsResponseV2.class, new Object[0]);
            List<Mdms> mdmsList = response.getBody() != null ? ((MdmsResponseV2)response.getBody()).getMdms() : null;
            log.debug("Successfully retrieved {} MDMS entries", (Object)(mdmsList != null ? mdmsList.size() : 0));
            return mdmsList;
        }
        catch (Exception e) {
            if (e instanceof DigitClientException) {
                throw e;
            }
            throw new DigitClientException("Failed to search MDMS data: " + e.getMessage(), e);
        }
    }
}