package org.egov.pg.clients.individual.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Identifier model representing individual identifier (Aadhaar, system-generated, etc.).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Identifier {

	@JsonProperty("id")
	private String id;

	@JsonProperty("identifierType")
	private String identifierType;

	@JsonProperty("identifierId")
	private String identifierId;

	@JsonProperty("individualId")
	private String individualId;

	@JsonProperty("createdBy")
	private String createdBy;

	@JsonProperty("lastModifiedBy")
	private String lastModifiedBy;

	@JsonProperty("createdTime")
	private Long createdTime;

	@JsonProperty("lastModifiedTime")
	private Long lastModifiedTime;

	@JsonProperty("active")
	private Boolean active;
}
