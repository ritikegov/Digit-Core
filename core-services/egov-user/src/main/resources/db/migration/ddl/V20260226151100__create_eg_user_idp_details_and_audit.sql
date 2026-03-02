-- =====================================================
-- Create eg_user_idp_details and eg_user_idp_details_audit_table (consolidated final schema)
-- Replaces: create idp_details, create audit, add audit fields, rename audit id/add uuid, token_id
-- =====================================================

-- =====================================================
-- Table: eg_user_idp_details
-- =====================================================

CREATE TABLE eg_user_idp_details (
    id bigint NOT NULL,
    tenantid character varying(256) NOT NULL,
    uuid character(128),

    idp_token_exp timestamp,
    last_sso_login_at timestamp,
    token_id character varying(128),

    mfa_enabled boolean DEFAULT FALSE NOT NULL,
    mfa_device_name character varying(256),
    mfa_phone_last4 character varying(4),
    mfa_registered_on timestamp,
    mfa_details character varying(256),

    created_date timestamp DEFAULT CURRENT_TIMESTAMP,
    lastmodifieddate timestamp DEFAULT CURRENT_TIMESTAMP,
    createdby bigint,
    lastmodifiedby bigint,

    CONSTRAINT eg_user_idp_details_pkey PRIMARY KEY (id, tenantid),
    CONSTRAINT fk_eg_user_idp_details_user FOREIGN KEY (id, tenantid)
        REFERENCES eg_user (id, tenantid)
);

CREATE INDEX idx_eg_user_idp_tenant_uuid_id
    ON eg_user_idp_details (tenantid, uuid, id);

-- =====================================================
-- Table: eg_user_idp_details_audit_table
-- =====================================================

CREATE TABLE eg_user_idp_details_audit_table (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id bigint NOT NULL,
    tenantid character varying(256) NOT NULL,
    uuid character(128),

    idp_token_exp timestamp,
    last_sso_login_at timestamp,
    token_id character varying(128),

    mfa_enabled boolean DEFAULT FALSE,
    mfa_device_name character varying(256),
    mfa_phone_last4 character varying(4),
    mfa_registered_on timestamp,
    mfa_details character varying(256),

    createddate timestamp DEFAULT CURRENT_TIMESTAMP,
    lastmodifieddate timestamp DEFAULT CURRENT_TIMESTAMP,
    createdby bigint,
    lastmodifiedby bigint,

    CONSTRAINT eg_user_idp_details_audit_table_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_eg_user_idp_details_audit_tenant_uuid_id
    ON eg_user_idp_details_audit_table (tenantid, uuid, user_id);
