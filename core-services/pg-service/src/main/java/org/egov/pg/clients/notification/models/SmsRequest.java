package org.egov.pg.clients.notification.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.pg.clients.notification.models.enums.SmsCategory;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsRequest {

	@JsonProperty("templateId")
	private String templateId;

	@JsonProperty("version")
	private String version;

	@JsonProperty("mobileNumbers")
	private List<String> mobileNumbers;

	@JsonProperty("enrich")
	private Boolean enrich;

	@JsonProperty("payload")
	private Map<String, Object> payload;

	@JsonProperty("category")
	private SmsCategory category;
}
