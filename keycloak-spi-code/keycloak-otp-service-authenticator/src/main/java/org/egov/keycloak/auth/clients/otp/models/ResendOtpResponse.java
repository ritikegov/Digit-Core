package org.egov.keycloak.auth.clients.otp.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResendOtpResponse {

	@JsonProperty("request_id")
	public String requestId;

	@JsonProperty("resend_count")
	public int resendCount;

	@JsonProperty("resend_allowed_after")
	public String resendAllowedAfter;

	@JsonProperty("expires_at")
	public String expiresAt;

	@JsonProperty("status")
	public String status;
}
