package org.egov.pg.clients.individual.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Skill model representing individual skill.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Skill {

	@JsonProperty("id")
	private String id;

	@JsonProperty("clientReferenceId")
	private String clientReferenceId;

	@JsonProperty("individualId")
	private String individualId;

	@JsonProperty("type")
	private String type;

	@JsonProperty("level")
	private String level;

	@JsonProperty("experience")
	private String experience;

	@JsonProperty("createdBy")
	private String createdBy;

	@JsonProperty("lastModifiedBy")
	private String lastModifiedBy;

	@JsonProperty("createdTime")
	private Long createdTime;

	@JsonProperty("lastModifiedTime")
	private Long lastModifiedTime;
}

