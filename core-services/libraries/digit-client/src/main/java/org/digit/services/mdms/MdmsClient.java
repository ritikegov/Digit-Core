package org.digit.services.mdms;

import org.digit.config.ApiProperties;
import org.digit.exception.DigitClientException;
import org.digit.services.mdms.model.Mdms;
import org.digit.services.mdms.model.MdmsResponseV2;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Set;

@Slf4j
@Getter
@Setter
public class MdmsClient {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public MdmsClient(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public boolean isMdmsDataValid(String schemaCode, Set<String> uniqueIdentifiers) {
        if (schemaCode == null || schemaCode.trim().isEmpty())
            throw new DigitClientException("Schema code cannot be null or empty");
        if (uniqueIdentifiers == null || uniqueIdentifiers.isEmpty())
            throw new DigitClientException("Unique identifiers set cannot be null or empty");
        try {
            log.debug("Validating MDMS data for schemaCode: {}, uniqueIdentifiers: {}", schemaCode, uniqueIdentifiers);
            List<Mdms> mdmsList = searchMdmsData(schemaCode, uniqueIdentifiers);
            int foundCount = mdmsList != null ? mdmsList.size() : 0;
            boolean allValid = foundCount == uniqueIdentifiers.size();
            log.debug("MDMS validation result: {} ({} out of {} found)", allValid ? "valid" : "invalid", foundCount, uniqueIdentifiers.size());
            return allValid;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to validate MDMS data: " + e.getMessage(), e);
        }
    }

    public List<Mdms> searchMdmsData(String schemaCode, Set<String> uniqueIdentifiers) {
        if (schemaCode == null || schemaCode.trim().isEmpty())
            throw new DigitClientException("Schema code cannot be null or empty");
        if (uniqueIdentifiers == null || uniqueIdentifiers.isEmpty())
            throw new DigitClientException("Unique identifiers set cannot be null or empty");
        try {
            log.debug("Searching MDMS data for schemaCode: {}, uniqueIdentifiers: {}", schemaCode, uniqueIdentifiers);
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromUriString(apiProperties.getMdmsServiceUrl() + "/mdms/v3/data")
                    .queryParam("schemaCode", schemaCode);
            for (String uid : uniqueIdentifiers)
                uriBuilder.queryParam("uniqueIdentifiers", uid);
            ResponseEntity<MdmsResponseV2> response = restTemplate.exchange(
                    uriBuilder.toUriString(), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), MdmsResponseV2.class);
            List<Mdms> mdmsList = response.getBody() != null ? response.getBody().getMdms() : null;
            log.debug("Successfully retrieved {} MDMS entries", mdmsList != null ? mdmsList.size() : 0);
            return mdmsList;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to search MDMS data: " + e.getMessage(), e);
        }
    }
}
