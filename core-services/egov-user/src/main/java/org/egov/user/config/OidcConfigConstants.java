package org.egov.user.config;

/**
 * Constants for OIDC config: MDMS/JSON keys and default values used in code (get/has/put, defaults).
 * Does not include log or exception message strings.
 */
public final class OidcConfigConstants {

    private OidcConfigConstants() {}

    public static final String MDMS_RES = "MdmsRes";

    /** Provider JSON keys (MDMS / config). */
    public static final String KEY_ACTIVE = "active";
    public static final String KEY_IS_ACTIVE = "isActive";
    public static final String KEY_ID = "id";
    public static final String KEY_ISSUER_URI = "issuerUri";
    public static final String KEY_ISSUER_ALIASES = "issuerAliases";
    public static final String KEY_JWK_SET_URI = "jwkSetUri";
    public static final String KEY_AUDIENCES = "audiences";
    public static final String KEY_TENANT_ID = "tenantId";
    public static final String KEY_USER_TYPE = "userType";
    public static final String KEY_DEFAULT_ROLE_CODES = "defaultRoleCodes";
    public static final String KEY_ROLE_CLAIM_KEY = "roleClaimKey";
    public static final String KEY_ROLE_MAPPING = "roleMapping";
    public static final String KEY_DEFAULT_DOB = "defaultDob";
    public static final String KEY_DEFAULT_EMPLOYEE_STATUS = "defaultEmployeeStatus";
    public static final String KEY_ROLE_PREFIX = "rolePrefix";
    public static final String KEY_DECRYPTION_PURPOSE = "decryptionPurpose";
    public static final String KEY_GRAPH_CLIENT_ID = "graphClientId";
    public static final String KEY_GRAPH_CLIENT_SECRET = "graphClientSecret";
    public static final String KEY_GRAPH_TENANT_ID = "graphTenantId";
    public static final String KEY_GRAPH_METHODS_URL = "graphMethodsUrl";
    public static final String KEY_GRAPH_TOKEN_URL = "graphTokenUrl";
    public static final String KEY_GRAPH_SCOPE = "graphScope";
    public static final String KEY_GRAPH_SERVICE_TYPE = "graphServiceType";
    public static final String KEY_GRAPH_USERS_URL = "graphUsersUrl";

    /** Default values used in code. */
    public static final String DEFAULT_ROLE_CLAIM_KEY = "roles";
    public static final String DEFAULT_USER_TYPE = "EMPLOYEE";
    public static final String DEFAULT_EMPLOYED_STATUS = "EMPLOYED";
    public static final String DEFAULT_ROLE_PREFIX = "ROLE_";
    public static final String DEFAULT_DECRYPTION_PURPOSE = "UserSelf";

    public static final String PROVIDERS_SOURCE_STATIC = "static";
    public static final String PROVIDERS_SOURCE_MDMS = "mdms";

    public static final String GRAPH_SERVICE_TYPE_AZURE = "azure";
    public static final String GRAPH_SERVICE_TYPE_NONE = "none";

    public static final String DEFAULT_GRAPH_METHODS_URL = "https://graph.microsoft.com/v1.0/users/%s/authentication/methods";
    public static final String DEFAULT_GRAPH_USERS_URL = "https://graph.microsoft.com/v1.0/users/%s";
    public static final String DEFAULT_GRAPH_TOKEN_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    public static final String DEFAULT_GRAPH_SCOPE = "https://graph.microsoft.com/.default";

    /** SSO default employee creation values when Graph profile is not available. */
    public static final String DEFAULT_EMPLOYEE_TYPE = "PERMANENT";
    public static final String DEFAULT_DESIGNATION_ID = "1f3572c4-07ce-4d58-86d3-7b6e2458e812";
    public static final String DEFAULT_DEPARTMENT_CODE = "NMCP";

    /** Graph client secret: env var prefix (e.g. GRAPH_CLIENT_SECRET_OIDC_AZURE_TG). */
    public static final String GRAPH_CLIENT_SECRET_ENV_PREFIX = "GRAPH_CLIENT_SECRET_";
}
