package org.egov.keycloak.auth.clients.otp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifyOtpResponse {

	@JsonProperty("request_id")
	public String requestId;

	@JsonProperty("status")
	public String status;

	@JsonProperty("verified_at")
	public String verifiedAt;

	@JsonProperty("message")
	public String message;
}
