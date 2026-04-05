package org.egov.keycloak.auth.clients.otp.models;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerateOtpRequest {

	@JsonProperty("destination")
	public String destination;

	@JsonProperty("destination_type")
	public String destinationType;

	@JsonProperty("purpose")
	public String purpose;

	@JsonProperty("otp_length")
	public Integer otpLength;

	@JsonProperty("otp_charset")
	public String otpCharset;

	@JsonProperty("client_metadata")
	public Map<String, Object> clientMetadata;
}
