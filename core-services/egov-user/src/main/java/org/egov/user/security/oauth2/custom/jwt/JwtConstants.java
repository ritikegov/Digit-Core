package org.egov.user.security.oauth2.custom.jwt;

/**
 * Constants for JWT: grant type, request param keys, claim keys, MDMS keys, OIDC error codes,
 * OAuth2Error code, username format placeholders.
 */
public final class JwtConstants {

    private JwtConstants() {}

    public static final String GRANT_TYPE_JWT_EXCHANGE = "jwt_exchange";

    /** Message when AccessTokenMfaExtractor is missing (required for JWT exchange). */
    public static final String MFA_EXTRACTOR_REQUIRED_MESSAGE =
            "AccessTokenMfaExtractor is required for JWT exchange authentication";

    /** Request parameter keys. */
    public static final String PARAM_ASSERTION = "assertion";
    public static final String PARAM_AUTH_TOKEN = "auth_token";
    public static final String PARAM_ACCESS_TOKEN = "access_token";
    public static final String PARAM_TENANT_ID = "tenantId";

    /** JWT claim keys. */
    public static final String CLAIM_USER_TYPE = "userType";
    public static final String CLAIM_USER_TYPE_ALT = "user_type";
    public static final String CLAIM_TENANT_ID = "tenantId";

    /** MDMS / role mapping keys. */
    public static final String MDMS_RES = "MdmsRes";
    public static final String KEY_SSO_ROLE = "ssoRole";
    public static final String KEY_DIGIT_ROLES = "digitRoles";
    public static final String KEY_DIGIT_ROLE = "digitRole";

    /** OAuth2Error error code. */
    public static final String OAUTH2_ERROR_INVALID_TOKEN = "invalid_token";

}
