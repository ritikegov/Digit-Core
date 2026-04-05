package org.egov.keycloak.auth.clients.otp;

import org.egov.keycloak.auth.clients.otp.models.*;

import java.io.IOException;

/**
 * Client contract for OTP service APIs.
 */
public interface OtpClient {

	GenerateOtpResponse generate(String tenantId, GenerateOtpRequest request) throws IOException;

	ResendOtpResponse resend(String tenantId, ResendOtpRequest request) throws IOException;

	VerifyOtpResponse verify(String tenantId, VerifyOtpRequest request) throws IOException;

	InvalidateOtpResponse invalidate(String tenantId, InvalidateOtpRequest request) throws IOException;
}
