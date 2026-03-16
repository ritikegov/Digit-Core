package org.egov.pg.clients.individual.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Request wrapper for Individual operations.
 * Based on the actual API structure from Go service.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualRequest {

	@JsonProperty("Individual")
	private Individual individual;
}
