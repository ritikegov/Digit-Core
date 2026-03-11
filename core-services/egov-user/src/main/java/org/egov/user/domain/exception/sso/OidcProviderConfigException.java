package org.egov.user.domain.exception.sso;

import org.egov.user.security.oauth2.custom.jwt.SsoErrorCodes;
import org.springframework.http.HttpStatus;

/** Thrown when OIDC provider configuration is missing or invalid (issuer, JWKS, provider resolution). */
public class OidcProviderConfigException extends SsoException {

    public OidcProviderConfigException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNAUTHORIZED);
    }

    public static OidcProviderConfigException jwksMissing(String providerId) {
        return new OidcProviderConfigException(SsoErrorCodes.OIDC_JWKS_MISSING,
                "jwk-set-uri is not configured for providerId=" + providerId);
    }

    public static OidcProviderConfigException issuerMissing(String providerId) {
        return new OidcProviderConfigException(SsoErrorCodes.OIDC_ISSUER_MISSING,
                "issuer-uri is not configured for providerId=" + providerId);
    }

    public static OidcProviderConfigException audiencesMissing(String providerId) {
        return new OidcProviderConfigException(SsoErrorCodes.OIDC_AUDIENCES_MISSING,
                "audiences are not configured for providerId=" + providerId + " - audience validation is mandatory");
    }

    public static OidcProviderConfigException issuerMissingInToken() {
        return new OidcProviderConfigException(SsoErrorCodes.OIDC_ISSUER_MISSING_IN_TOKEN,
                "Missing issuer (iss) claim in JWT token");
    }

    public static OidcProviderConfigException providerNotFound(String issuer) {
        return new OidcProviderConfigException(SsoErrorCodes.OIDC_PROVIDER_NOT_FOUND,
                "No OIDC provider configured for issuer: " + issuer);
    }

    public static OidcProviderConfigException providerAmbiguous(String issuer) {
        return new OidcProviderConfigException(SsoErrorCodes.OIDC_PROVIDER_AMBIGUOUS,
                "Multiple OIDC providers match issuer: " + issuer + " — unable to disambiguate");
    }

    public static OidcProviderConfigException issuerMismatch(String tokenIssuer, String providerId) {
        return new OidcProviderConfigException(SsoErrorCodes.OIDC_ISSUER_MISMATCH,
                "JWT issuer does not match provider configuration: tokenIssuer=" + tokenIssuer + ", providerId=" + providerId);
    }
}
