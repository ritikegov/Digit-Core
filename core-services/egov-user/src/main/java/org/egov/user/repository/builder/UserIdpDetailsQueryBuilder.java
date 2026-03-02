package org.egov.user.repository.builder;

import static org.egov.user.utils.DatabaseSchemaUtils.SCHEMA_REPLACE_STRING;

/**
 * SQL constants for eg_user_idp_details and eg_user_idp_details_audit_table.
 * Schema placeholder {schema} must be replaced via DatabaseSchemaUtils.replaceSchemaPlaceholder.
 */
public final class UserIdpDetailsQueryBuilder {

    private UserIdpDetailsQueryBuilder() {
    }

    public static final String UPSERT_IDP_DETAILS =
            "INSERT INTO " + SCHEMA_REPLACE_STRING + ".eg_user_idp_details "
                    + "(id, tenantid, uuid, idp_token_exp, last_sso_login_at, token_id, "
                    + "mfa_enabled, mfa_device_name, mfa_phone_last4, mfa_registered_on, mfa_details, "
                    + "created_date, lastmodifieddate, createdby, lastmodifiedby) "
                    + "VALUES (:id, :tenantid, :uuid, :idp_token_exp, :last_sso_login_at, :token_id, "
                    + ":mfa_enabled, :mfa_device_name, :mfa_phone_last4, :mfa_registered_on, :mfa_details, "
                    + ":created_date, :lastmodifieddate, :createdby, :lastmodifiedby) "
                    + "ON CONFLICT (id, tenantid) DO UPDATE SET "
                    + "uuid = EXCLUDED.uuid, "
                    + "idp_token_exp = EXCLUDED.idp_token_exp, "
                    + "last_sso_login_at = EXCLUDED.last_sso_login_at, "
                    + "token_id = EXCLUDED.token_id, "
                    + "mfa_enabled = EXCLUDED.mfa_enabled, "
                    + "mfa_device_name = EXCLUDED.mfa_device_name, "
                    + "mfa_phone_last4 = EXCLUDED.mfa_phone_last4, "
                    + "mfa_registered_on = EXCLUDED.mfa_registered_on, "
                    + "mfa_details = EXCLUDED.mfa_details, "
                    + "lastmodifieddate = EXCLUDED.lastmodifieddate, "
                    + "lastmodifiedby = EXCLUDED.lastmodifiedby";

    /** Use id, uuid (nullable), tenantid. When uuid is null, matches by id and tenantid only. */
    public static final String SELECT_IDP_DETAILS_BY_ID_UUID_TENANT =
            "SELECT id, tenantid, uuid, idp_token_exp, last_sso_login_at, token_id, "
                    + "mfa_enabled, mfa_device_name, mfa_phone_last4, mfa_registered_on, mfa_details, "
                    + "created_date, lastmodifieddate, createdby, lastmodifiedby "
                    + "FROM " + SCHEMA_REPLACE_STRING + ".eg_user_idp_details "
                    + "WHERE id = :id AND tenantid = :tenantid AND (:uuid IS NULL OR uuid = :uuid)";

    public static final String INSERT_IDP_AUDIT =
            "INSERT INTO " + SCHEMA_REPLACE_STRING + ".eg_user_idp_details_audit_table "
                    + "(id, user_id, tenantid, uuid, idp_token_exp, last_sso_login_at, token_id, "
                    + "mfa_enabled, mfa_device_name, mfa_phone_last4, mfa_registered_on, mfa_details, "
                    + "createddate, lastmodifieddate, createdby, lastmodifiedby) "
                    + "VALUES (:id, :user_id, :tenantid, :uuid, :idp_token_exp, :last_sso_login_at, :token_id, "
                    + ":mfa_enabled, :mfa_device_name, :mfa_phone_last4, :mfa_registered_on, :mfa_details, "
                    + ":createddate, :lastmodifieddate, :createdby, :lastmodifiedby)";
}
