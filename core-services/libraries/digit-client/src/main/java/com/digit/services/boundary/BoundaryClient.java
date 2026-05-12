package com.digit.services.boundary;

import com.digit.config.ApiProperties;
import com.digit.exception.DigitClientException;
import com.digit.services.boundary.model.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Getter
@Setter
public class BoundaryClient {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public BoundaryClient(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public List<Boundary> createBoundaries(List<Boundary> boundaries) {
        if (boundaries == null || boundaries.isEmpty())
            throw new DigitClientException("Boundaries list cannot be null or empty");
        try {
            log.debug("Creating {} boundaries", boundaries.size());
            String url = apiProperties.getBoundaryServiceUrl() + "/boundary/v3/boundaries";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            BoundaryRequest request = BoundaryRequest.builder().boundary(boundaries).build();
            ResponseEntity<BoundaryResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request, headers), BoundaryResponse.class);
            List<Boundary> created = response.getBody() != null ? response.getBody().getBoundary() : null;
            log.debug("Successfully created {} boundaries", created != null ? created.size() : 0);
            return created;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to create boundaries: " + e.getMessage(), e);
        }
    }

    public List<Boundary> searchBoundariesByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty())
            throw new DigitClientException("Codes list cannot be null or empty");
        try {
            log.debug("Searching boundaries with codes: {}", codes);
            StringBuilder urlBuilder = new StringBuilder(apiProperties.getBoundaryServiceUrl() + "/boundary/v3/boundaries?");
            for (String code : codes) {
                urlBuilder.append("codes=").append(code).append("&");
            }
            String url = urlBuilder.toString().replaceAll("&$", "");
            ResponseEntity<BoundaryResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), BoundaryResponse.class);
            List<Boundary> boundaries = response.getBody() != null ? response.getBody().getBoundary() : null;
            log.debug("Successfully retrieved {} boundaries", boundaries != null ? boundaries.size() : 0);
            return boundaries;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to search boundaries: " + e.getMessage(), e);
        }
    }

    public boolean isValidBoundariesByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty())
            throw new DigitClientException("Codes list cannot be null or empty");
        try {
            log.debug("Validating boundaries with codes: {}", codes);
            StringBuilder urlBuilder = new StringBuilder(apiProperties.getBoundaryServiceUrl() + "/boundary/v3/boundaries?");
            for (String code : codes) {
                urlBuilder.append("codes=").append(code).append("&");
            }
            String url = urlBuilder.toString().replaceAll("&$", "");
            ResponseEntity<BoundaryResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), BoundaryResponse.class);
            List<Boundary> boundaries = response.getBody() != null ? response.getBody().getBoundary() : null;
            int validCount = boundaries != null ? boundaries.size() : 0;
            boolean allValid = validCount == codes.size();
            log.debug("Boundary validation result: {} ({} out of {} found)", allValid ? "valid" : "invalid", validCount, codes.size());
            return allValid;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to validate boundaries: " + e.getMessage(), e);
        }
    }

    public Boundary updateBoundary(String boundaryId, Boundary boundary) {
        if (boundaryId == null || boundaryId.trim().isEmpty())
            throw new DigitClientException("Boundary ID cannot be null or empty");
        if (boundary == null)
            throw new DigitClientException("Boundary cannot be null");
        try {
            log.debug("Updating boundary with ID: {}", boundaryId);
            String url = apiProperties.getBoundaryServiceUrl() + "/boundary/v3/boundaries/" + boundaryId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity<BoundaryResponse> response = restTemplate.exchange(
                    url, HttpMethod.PUT, new HttpEntity<>(boundary, headers), BoundaryResponse.class);
            Boundary updated = null;
            if (response.getBody() != null && response.getBody().getBoundary() != null && !response.getBody().getBoundary().isEmpty())
                updated = response.getBody().getBoundary().get(0);
            log.debug("Successfully updated boundary: {}", boundaryId);
            return updated;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to update boundary: " + e.getMessage(), e);
        }
    }

    public BoundaryHierarchy createBoundaryHierarchy(BoundaryHierarchy boundaryHierarchy) {
        if (boundaryHierarchy == null)
            throw new DigitClientException("BoundaryHierarchy cannot be null");
        try {
            log.debug("Creating boundary hierarchy: {}", boundaryHierarchy.getHierarchyType());
            String url = apiProperties.getBoundaryServiceUrl() + "/boundary/v3/hierarchy";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            BoundaryHierarchyRequest request = BoundaryHierarchyRequest.builder().boundaryHierarchy(boundaryHierarchy).build();
            ResponseEntity<BoundaryHierarchyResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, headers), BoundaryHierarchyResponse.class);
            List<BoundaryHierarchy> list = response.getBody() != null ? response.getBody().getHierarchy() : null;
            BoundaryHierarchy created = (list != null && !list.isEmpty()) ? list.get(0) : null;
            log.debug("Successfully created boundary hierarchy: {}", created != null ? created.getId() : "null");
            return created;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to create boundary hierarchy: " + e.getMessage(), e);
        }
    }

    public BoundaryHierarchy searchBoundaryHierarchy(String hierarchyType) {
        if (hierarchyType == null || hierarchyType.trim().isEmpty())
            throw new DigitClientException("Hierarchy type cannot be null or empty");
        try {
            log.debug("Searching boundary hierarchy with type: {}", hierarchyType);
            String url = apiProperties.getBoundaryServiceUrl() + "/boundary/v3/hierarchy?hierarchyType=" + hierarchyType;
            ResponseEntity<BoundaryHierarchyResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), BoundaryHierarchyResponse.class);
            List<BoundaryHierarchy> list = response.getBody() != null ? response.getBody().getHierarchy() : null;
            BoundaryHierarchy hierarchy = (list != null && !list.isEmpty()) ? list.get(0) : null;
            log.debug("Successfully retrieved boundary hierarchy: {}", hierarchyType);
            return hierarchy;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to search boundary hierarchy: " + e.getMessage(), e);
        }
    }

    public BoundaryRelationship createBoundaryRelationship(BoundaryRelationship boundaryRelationship) {
        if (boundaryRelationship == null)
            throw new DigitClientException("BoundaryRelationship cannot be null");
        try {
            log.debug("Creating boundary relationship: {}", boundaryRelationship.getCode());
            String url = apiProperties.getBoundaryServiceUrl() + "/boundary/v3/relationship";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            BoundaryRelationshipRequest request = BoundaryRelationshipRequest.builder().boundaryRelationship(boundaryRelationship).build();
            ResponseEntity<BoundaryRelationshipResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, headers), BoundaryRelationshipResponse.class);
            BoundaryRelationship created = null;
            if (response.getBody() != null && response.getBody().getRelationship() != null && !response.getBody().getRelationship().isEmpty())
                created = response.getBody().getRelationship().get(0);
            log.debug("Successfully created boundary relationship: {}", created != null ? created.getId() : "null");
            return created;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to create boundary relationship: " + e.getMessage(), e);
        }
    }

    public List<BoundarySearchResponse.HierarchyRelation> searchBoundaryRelationships(String hierarchyType, String boundaryType, boolean includeChildren) {
        if (hierarchyType == null || hierarchyType.trim().isEmpty())
            throw new DigitClientException("Hierarchy type cannot be null or empty");
        try {
            log.debug("Searching boundary relationships with hierarchy type: {}", hierarchyType);
            StringBuilder urlBuilder = new StringBuilder(apiProperties.getBoundaryServiceUrl() + "/boundary/v3/relationship?");
            urlBuilder.append("hierarchyType=").append(hierarchyType);
            if (boundaryType != null && !boundaryType.trim().isEmpty())
                urlBuilder.append("&boundaryType=").append(boundaryType);
            urlBuilder.append("&includeChildren=").append(includeChildren);
            ResponseEntity<BoundarySearchResponse> response = restTemplate.exchange(
                    urlBuilder.toString(), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), BoundarySearchResponse.class);
            List<BoundarySearchResponse.HierarchyRelation> relationships = response.getBody() != null ? response.getBody().getTenantBoundary() : null;
            log.debug("Successfully retrieved {} boundary relationships", relationships != null ? relationships.size() : 0);
            return relationships;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to search boundary relationships: " + e.getMessage(), e);
        }
    }

    public BoundaryRelationship updateBoundaryRelationship(String relationshipId, BoundaryRelationship boundaryRelationship) {
        if (relationshipId == null || relationshipId.trim().isEmpty())
            throw new DigitClientException("Relationship ID cannot be null or empty");
        if (boundaryRelationship == null)
            throw new DigitClientException("BoundaryRelationship cannot be null");
        try {
            log.debug("Updating boundary relationship with ID: {}", relationshipId);
            String url = apiProperties.getBoundaryServiceUrl() + "/boundary/v3/relationship/" + relationshipId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            ResponseEntity<BoundaryRelationshipResponse> response = restTemplate.exchange(
                    url, HttpMethod.PUT, new HttpEntity<>(boundaryRelationship, headers), BoundaryRelationshipResponse.class);
            BoundaryRelationship updated = null;
            if (response.getBody() != null && response.getBody().getRelationship() != null && !response.getBody().getRelationship().isEmpty())
                updated = response.getBody().getRelationship().get(0);
            log.debug("Successfully updated boundary relationship: {}", relationshipId);
            return updated;
        } catch (Exception e) {
            if (e instanceof DigitClientException) throw e;
            throw new DigitClientException("Failed to update boundary relationship: " + e.getMessage(), e);
        }
    }
}
