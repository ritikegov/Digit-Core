package org.egov.keycloak.auth.clients.otp;

import org.egov.keycloak.auth.clients.otp.models.ErrorResponse;

public class OtpClientException extends RuntimeException {
	private final int statusCode;
	private final ErrorResponse error;

	public OtpClientException(int statusCode, String message, ErrorResponse error) {
		super(message);
		this.statusCode = statusCode;
		this.error = error;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public ErrorResponse getError() {
		return error;
	}
}
