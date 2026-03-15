package org.egov.pg.clients.notification.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {

	@JsonProperty("templateId")
	private String templateId;

	@JsonProperty("version")
	private String version;

	@JsonProperty("emailIds")
	private List<String> emailIds;

	@JsonProperty("enrich")
	private Boolean enrich;

	@JsonProperty("payload")
	private Map<String, Object> payload;

	@JsonProperty("attachments")
	private List<String> attachments;
}
