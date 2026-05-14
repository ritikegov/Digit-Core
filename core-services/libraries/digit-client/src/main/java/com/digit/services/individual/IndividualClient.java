package org.egov.services.individual;

import org.egov.config.ApiProperties;
import org.egov.services.individual.model.Individual;
import org.egov.services.individual.model.IndividualResponse;
import org.egov.services.individual.model.IndividualSearchResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.Getter;

@Getter
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
        String url = this.apiProperties.getIndividualServiceUrl() + "/individual/v3/individuals";
        ResponseEntity response = this.restTemplate.postForEntity(url, (Object)individual, IndividualResponse.class, new Object[0]);
        return response.getBody() != null ? ((IndividualResponse)response.getBody()).getIndividual() : null;
    }

    public Individual getIndividualById(String individualId) {
        String url = this.apiProperties.getIndividualServiceUrl() + "/individual/v3/individuals/" + individualId;
        ResponseEntity response = this.restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(new HttpHeaders()), IndividualResponse.class, new Object[0]);
        return response.getBody() != null ? ((IndividualResponse)response.getBody()).getIndividual() : null;
    }

    public IndividualSearchResponse searchIndividualsByName(String individualName) {
        return this.searchIndividualsByName(individualName, 10, 0);
    }

    public IndividualSearchResponse searchIndividualsByName(String individualName, Integer limit, Integer offset) {
        int finalLimit = limit != null && limit > 0 ? limit : 10;
        int finalOffset = offset != null && offset >= 0 ? offset : 0;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString((String)(this.apiProperties.getIndividualServiceUrl() + "/individual/v3/individuals")).queryParam("limit", new Object[]{finalLimit}).queryParam("offset", new Object[]{finalOffset});
        if (individualName != null && !individualName.trim().isEmpty()) {
            builder.queryParam("name", new Object[]{individualName});
        }
        ResponseEntity response = this.restTemplate.exchange(builder.toUriString(), HttpMethod.GET, new HttpEntity(new HttpHeaders()), IndividualSearchResponse.class, new Object[0]);
        return (IndividualSearchResponse)response.getBody();
    }

    public IndividualSearchResponse searchAllIndividuals() {
        return this.searchIndividualsByName(null, 10, 0);
    }

    public IndividualSearchResponse searchAllIndividuals(Integer limit, Integer offset) {
        return this.searchIndividualsByName(null, limit, offset);
    }

    public boolean isIndividualExist(String individualId) {
        return this.isIndividualExistsById(individualId, 10, 0);
    }

    public boolean isIndividualExistsById(String individualId, Integer limit, Integer offset) {
        try {
            String url = this.apiProperties.getIndividualServiceUrl() + "/individual/v3/individuals/" + individualId;
            ResponseEntity response = this.restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(new HttpHeaders()), IndividualResponse.class, new Object[0]);
            return response.getBody() != null && ((IndividualResponse)response.getBody()).getIndividual() != null;
        }
        catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }
}