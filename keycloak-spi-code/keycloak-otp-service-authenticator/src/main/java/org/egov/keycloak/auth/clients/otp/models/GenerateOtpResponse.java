package org.egov.keycloak.auth.clients.otp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateOtpResponse {

	@JsonProperty("request_id")
	public String requestId;

	@JsonProperty("destination")
	public String destination;

	@JsonProperty("resend_allowed_after")
	public String resendAllowedAfter;

	@JsonProperty("expires_at")
	public String expiresAt;

	@JsonProperty("status")
	public String status;

	@JsonProperty("message")
	public String message;
}