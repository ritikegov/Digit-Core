package org.egov.pg.clients.individual.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * AdditionalFields model representing additional custom fields.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdditionalFields {

	@JsonProperty("fields")
	private List<Field> fields;

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Field {

		@JsonProperty("key")
		private String key;

		@JsonProperty("value")
		private String value;
	}
}
