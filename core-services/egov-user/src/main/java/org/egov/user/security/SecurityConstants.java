package org.egov.user.security;

/**
 * Constants for security: request param keys, detail map keys, map keys used in put/get, headers.
 * Does not include log or exception message strings.
 */
public final class SecurityConstants {

    private SecurityConstants() {}

    /** Microsoft Graph: form/JSON keys and headers. */
    public static final String KEY_GRANT_TYPE = "grant_type";
    public static final String KEY_CLIENT_ID = "client_id";
    public static final String KEY_CLIENT_SECRET = "client_secret";
    public static final String KEY_SCOPE = "scope";
    public static final String KEY_ACCESS_TOKEN = "access_token";
    public static final String KEY_VALUE = "value";
    public static final String KEY_ODATA_TYPE = "@odata.type";
    public static final String KEY_DISPLAY_NAME = "displayName";
    public static final String KEY_CREATION_DATE_TIME = "creationDateTime";
    public static final String KEY_CREATED_DATE_TIME = "createdDateTime";
    public static final String KEY_PHONE_NUMBER = "phoneNumber";
    /** Microsoft Graph user resource: employee creation fields. */
    public static final String KEY_DEPARTMENT = "department";
    public static final String KEY_JOB_TITLE = "jobTitle";
    public static final String KEY_EMPLOYEE_TYPE = "employeeType";
    public static final String KEY_DESIGNATION = "designation";
    /** Query suffix for GET user to fetch employee creation fields. */
    public static final String GRAPH_USERS_SELECT_QUERY = "?$select=department,jobTitle,employeeType";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    public static final String DEFAULT_GRAPH_SCOPE = "https://graph.microsoft.com/.default";
}
