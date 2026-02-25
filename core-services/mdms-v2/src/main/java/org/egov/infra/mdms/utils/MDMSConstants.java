package org.egov.infra.mdms.utils;

import org.springframework.stereotype.Component;


@Component
public class MDMSConstants {

    public static final String X_UNIQUE_KEY = "x-unique";
    public static final String X_REFERENCE_SCHEMA_KEY = "x-ref-schema";
    public static final String REQUIRED_KEY = "required";
    public static final String DOT_SEPARATOR = ".";
    public static final String DOT_REGEX = "\\.";
    public static final String FORWARD_SLASH = "/";
    public static final String DOLLAR_DOT = "$.";
    public static final String FIELD_PATH_KEY = "fieldPath";
    public static final String SCHEMA_CODE_KEY = "schemaCode";

    public static final String EG_MDMS_DATA_TABLE = "eg_mdms_data";
    public static final String TENANTID_COLUMN = "tenantid";

    public static final String INFO_SCHEMA_TABLES = "information_schema.tables";
    public static final String TABLE_SCHEMA_COLUMN = "table_schema";
    public static final String TABLE_NAME_COLUMN = "table_name";
    public static final String SCHEMA_PG_CATALOG = "pg_catalog";
    public static final String SCHEMA_INFO_SCHEMA = "information_schema";

    /** TenantId passed for schema placeholder replacement in single-schema (non-central) mode; no dot so no schema prefix. */
    public static final String DEFAULT_TENANT_ID_FOR_SCHEMA_REPLACE = "public";

}
