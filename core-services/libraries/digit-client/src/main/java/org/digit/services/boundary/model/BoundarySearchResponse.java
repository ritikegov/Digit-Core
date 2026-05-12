package org.digit.services.boundary.model;

import org.digit.services.common.model.AuditDetails;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BoundarySearchResponse {

    @JsonProperty("tenantBoundary")
    private List<HierarchyRelation> tenantBoundary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HierarchyRelation {

        @JsonProperty("hierarchyType")
        private String hierarchyType;

        @JsonProperty("boundary")
        private List<EnrichedBoundary> boundary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnrichedBoundary {

        @JsonProperty("id")
        private String id;

        @JsonProperty("code")
        private String code;

        @JsonProperty("boundaryType")
        private String boundaryType;

        @JsonProperty("children")
        private List<EnrichedBoundary> children;

        @JsonProperty("auditDetails")
        private AuditDetails auditDetails;
    }
}
