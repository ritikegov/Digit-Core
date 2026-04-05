package org.egov.keycloak.auth.clients.otp.models;

import java.util.List;

public class ErrorResponse {
	public String code;
	public String message;
	public String description;
	public List<String> params;
}
