package org.egov.user.security.oauth2.custom.jwt;

/**
 * Machine-readable error codes for SSO and MFA flows.
 * Used in token endpoint responses (via CustomWebResponseExceptionTranslator) and ErrorResponse fields.
 */
public final class SsoErrorCodes {

    private SsoErrorCodes() {}

    public static final String JWT_INVALID = "sso.jwt.invalid";
    public static final String JWT_EXPIRED = "sso.jwt.expired";
    public static final String JWT_PARSE_FAILED = "sso.jwt.parse_failed";

    public static final String OIDC_JWKS_MISSING = "sso.oidc.jwks_missing";
    public static final String OIDC_ISSUER_MISSING = "sso.oidc.issuer_missing";
    public static final String OIDC_ISSUER_MISSING_IN_TOKEN = "sso.oidc.issuer_missing_in_token";
    public static final String OIDC_PROVIDER_NOT_FOUND = "sso.oidc.provider_not_found";
    public static final String OIDC_PROVIDER_AMBIGUOUS = "sso.oidc.provider_ambiguous";
    public static final String OIDC_ISSUER_MISMATCH = "sso.oidc.issuer_mismatch";

    public static final String TENANT_ID_MISSING = "sso.param.tenant_id_missing";
    public static final String USER_TYPE_MISSING = "sso.param.user_type_missing";

    public static final String USER_NOT_FOUND = "sso.user.not_found";
    public static final String USER_DUPLICATE = "sso.user.duplicate";
    public static final String USER_CONTACT_ADMIN = "sso.user.contact_admin";
    public static final String USER_INACTIVE = "sso.user.inactive";
    public static final String USER_LOCKED = "sso.user.locked";
    public static final String IDP_USER_ACCESS_REVOKED = "sso.user.idp_access_revoked";

    public static final String MFA_ENRICHMENT_FAILED = "sso.mfa.enrichment_failed";

    public static final String ROLE_MAPPING_FAILED = "sso.role.mapping_failed";
}
