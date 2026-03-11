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
                    + "(id, tenantid, uuid, idptokenexp, lastssologinat, tokenid, "
                    + "mfaenabled, mfadevicename, mfaphonelast4, mfaregisteredon, mfadetails, "
                    + "createddate, lastmodifieddate, createdby, lastmodifiedby) "
                    + "VALUES (:id, :tenantid, :uuid, :idptokenexp, :lastssologinat, :tokenid, "
                    + ":mfaenabled, :mfadevicename, :mfaphonelast4, :mfaregisteredon, :mfadetails, "
                    + ":createddate, :lastmodifieddate, :createdby, :lastmodifiedby) "
                    + "ON CONFLICT (id, tenantid) DO UPDATE SET "
                    + "uuid = COALESCE(EXCLUDED.uuid, eg_user_idp_details.uuid), "
                    + "idptokenexp = EXCLUDED.idptokenexp, "
                    + "lastssologinat = EXCLUDED.lastssologinat, "
                    + "tokenid = EXCLUDED.tokenid, "
                    + "mfaenabled = EXCLUDED.mfaenabled, "
                    + "mfadevicename = EXCLUDED.mfadevicename, "
                    + "mfaphonelast4 = EXCLUDED.mfaphonelast4, "
                    + "mfaregisteredon = EXCLUDED.mfaregisteredon, "
                    + "mfadetails = EXCLUDED.mfadetails, "
                    + "lastmodifieddate = EXCLUDED.lastmodifieddate, "
                    + "lastmodifiedby = EXCLUDED.lastmodifiedby";

    /** Use id, uuid (nullable), tenantid. When uuid is null, matches by id and tenantid only. */
    public static final String SELECT_IDP_DETAILS_BY_ID_UUID_TENANT =
            "SELECT id, tenantid, uuid, idptokenexp, lastssologinat, tokenid, "
                    + "mfaenabled, mfadevicename, mfaphonelast4, mfaregisteredon, mfadetails, "
                    + "createddate, lastmodifieddate, createdby, lastmodifiedby "
                    + "FROM " + SCHEMA_REPLACE_STRING + ".eg_user_idp_details "
                    + "WHERE id = :id AND tenantid = :tenantid AND (:uuid IS NULL OR uuid = :uuid)";

    public static final String INSERT_IDP_AUDIT =
            "INSERT INTO " + SCHEMA_REPLACE_STRING + ".eg_user_idp_details_audit_table "
                    + "(id, userid, tenantid, uuid, idptokenexp, lastssologinat, tokenid, "
                    + "mfaenabled, mfadevicename, mfaphonelast4, mfaregisteredon, mfadetails, "
                    + "createddate, lastmodifieddate, createdby, lastmodifiedby) "
                    + "VALUES (:id, :userid, :tenantid, :uuid, :idptokenexp, :lastssologinat, :tokenid, "
                    + ":mfaenabled, :mfadevicename, :mfaphonelast4, :mfaregisteredon, :mfadetails, "
                    + ":createddate, :lastmodifieddate, :createdby, :lastmodifiedby)";

    /** Query to check if a tokenId has already been used (token replay protection). */
    public static final String CHECK_TOKEN_REPLAY =
            "SELECT COUNT(*) FROM " + SCHEMA_REPLACE_STRING + ".eg_user_idp_details "
            + "WHERE tokenid = :tokenid AND tenantid = :tenantid";
}
