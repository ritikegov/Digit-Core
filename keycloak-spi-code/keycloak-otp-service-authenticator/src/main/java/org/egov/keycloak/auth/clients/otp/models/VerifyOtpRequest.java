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
public class VerifyOtpRequest {

	@JsonProperty("request_id")
	public String requestId;

	@JsonProperty("otp")
	public String otp;

	@JsonProperty("client_metadata")
	public Map<String, Object> clientMetadata;
}