package org.egov.keycloak.auth.config;

import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;

/**
 * Runtime configuration resolved from environment variables.
 * <p>
 * Instantiated ONCE per factory in {@code init()} — not per request.
 * <p>
 * Channel configs are accessed via {@link #getEmail()} and {@link #getSms()}.
 * Each factory passes only its own {@link ChannelConfig} into the authenticator,
 * so the authenticator never needs to know which channel it is.
 */
@Getter
@JBossLog
public class OtpConfig {

	// OTP service base URL
	private final String otpHost;

	// Full API paths (host + path = complete URL)
	private final String otpGeneratePath;
	private final String otpResendPath;
	private final String otpVerifyPath;
	private final String otpInvalidatePath;

	// Per-channel configs
	private final ChannelConfig email;
	private final ChannelConfig sms;

	// Keycloak user attribute names
	private final String emailDestinationAttr;
	private final String smsDestinationAttr;

	public OtpConfig() {
		this.otpHost = getEnv("OTP_HOST", "http://localhost:8081");
		this.otpGeneratePath = getEnv("OTP_GENERATE_PATH", "/otp/v3/generate");
		this.otpResendPath = getEnv("OTP_RESEND_PATH", "/otp/v3/resend");
		this.otpVerifyPath = getEnv("OTP_VERIFY_PATH", "/otp/v3/verify");
		this.otpInvalidatePath = getEnv("OTP_INVALIDATE_PATH", "/otp/v3/invalidate");

		this.email = new ChannelConfig(
				getEnvInt("OTP_EMAIL_LENGTH", 6),
				getEnv("OTP_EMAIL_DESTINATION_TYPE", "email"),
				getEnv("OTP_EMAIL_PURPOSE", "login")
		);

		this.sms = new ChannelConfig(
				getEnvInt("OTP_SMS_LENGTH", 6),
				getEnv("OTP_SMS_DESTINATION_TYPE", "phone"),
				getEnv("OTP_SMS_PURPOSE", "login")
		);

		this.emailDestinationAttr = getEnv("KEYCLOAK_EMAIL_DESTINATION_ATTRIBUTE", "email");
		this.smsDestinationAttr = getEnv("KEYCLOAK_SMS_DESTINATION_ATTRIBUTE", "mobileNumber");

		log.infof("OtpConfig loaded: host=%s emailLen=%d smsLen=%d",
				otpHost, email.length(), sms.length());
	}

	private static String getEnv(String name, String defaultValue) {
		String v = System.getenv(name);
		return (v != null && !v.isBlank()) ? v.trim() : defaultValue;
	}

	// -----------------------------------------------------------------------
	// Private helpers — only this class reads env vars
	// -----------------------------------------------------------------------

	private static int getEnvInt(String name, int defaultValue) {
		String v = System.getenv(name);
		if (v == null || v.isBlank()) return defaultValue;
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			log.warnf("Invalid int for %s='%s', using default=%d", name, v, defaultValue);
			return defaultValue;
		}
	}

	/**
	 * Immutable per-channel settings.
	 *
	 * @param length          number of OTP digits
	 * @param destinationType value forwarded to the OTP service (e.g. "email", "phone")
	 * @param purpose         value forwarded to the OTP service (e.g. "login")
	 */
	public record ChannelConfig(int length, String destinationType, String purpose) {
	}
}