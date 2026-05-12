package org.digit.services.individual;

import org.digit.config.ApiProperties;
import org.digit.services.individual.model.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Getter
@Setter
public class IndividualClient {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_OFFSET = 0;

    public IndividualClient(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public Individual createIndividual(Individual individual) {
        String url = apiProperties.getIndividualServiceUrl() + "/individual/v3/individuals";
        ResponseEntity<IndividualResponse> response = restTemplate.postForEntity(url, individual, IndividualResponse.class);
        return response.getBody() != null ? response.getBody().getIndividual() : null;
    }

    public Individual getIndividualById(String individualId) {
        String url = apiProperties.getIndividualServiceUrl() + "/individual/v3/individuals/" + individualId;
        ResponseEntity<IndividualResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), IndividualResponse.class);
        return response.getBody() != null ? response.getBody().getIndividual() : null;
    }

    public IndividualSearchResponse searchIndividualsByName(String individualName) {
        return searchIndividualsByName(individualName, DEFAULT_LIMIT, DEFAULT_OFFSET);
    }

    public IndividualSearchResponse searchIndividualsByName(String individualName, Integer limit, Integer offset) {
        int finalLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
        int finalOffset = (offset != null && offset >= 0) ? offset : DEFAULT_OFFSET;
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(apiProperties.getIndividualServiceUrl() + "/individual/v3/individuals")
                .queryParam("limit", finalLimit)
                .queryParam("offset", finalOffset);
        if (individualName != null && !individualName.trim().isEmpty()) {
            builder.queryParam("name", individualName);
        }
        ResponseEntity<IndividualSearchResponse> response = restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), IndividualSearchResponse.class);
        return response.getBody();
    }

    public IndividualSearchResponse searchAllIndividuals() {
        return searchIndividualsByName(null, DEFAULT_LIMIT, DEFAULT_OFFSET);
    }

    public IndividualSearchResponse searchAllIndividuals(Integer limit, Integer offset) {
        return searchIndividualsByName(null, limit, offset);
    }

    public boolean isIndividualExist(String individualId) {
        return isIndividualExistsById(individualId, DEFAULT_LIMIT, DEFAULT_OFFSET);
    }

    public boolean isIndividualExistsById(String individualId, Integer limit, Integer offset) {
        try {
            String url = apiProperties.getIndividualServiceUrl() + "/individual/v3/individuals/" + individualId;
            ResponseEntity<IndividualResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), IndividualResponse.class);
            return response.getBody() != null && response.getBody().getIndividual() != null;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }
}
