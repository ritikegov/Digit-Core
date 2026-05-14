package org.egov.services.individual.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown=true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndividualSearchResponse {
    @JsonProperty(value="Individuals")
    private List<Individual> individuals;
    @JsonProperty(value="totalCount")
    private Long totalCount;
}