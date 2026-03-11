-- =====================================================
-- SSO / IDP schema initialisation
-- =====================================================


-- =====================================================
-- eg_user: identity-linkage columns only
-- (session/MFA state lives in eg_user_idp_details)
-- =====================================================
ALTER TABLE eg_user
    ADD COLUMN IF NOT EXISTS idpissuer    character varying(512),
    ADD COLUMN IF NOT EXISTS idpsubject   character varying(512),
    ADD COLUMN IF NOT EXISTS authprovider character varying(64) DEFAULT 'LOCAL';

ALTER TABLE eg_user
    ADD CONSTRAINT IF NOT EXISTS eg_user_idpissuer_idpsubject_tenantid_key
        UNIQUE (idpissuer, idpsubject, tenantid);


-- =====================================================
-- eg_user_audit_table: mirror identity-linkage columns
-- =====================================================
ALTER TABLE eg_user_audit_table
    ADD COLUMN IF NOT EXISTS idpissuer    character varying(512),
    ADD COLUMN IF NOT EXISTS idpsubject   character varying(512),
    ADD COLUMN IF NOT EXISTS authprovider character varying(64);


-- =====================================================
-- Table: eg_user_idp_details
-- Stores per-user SSO session state and MFA metadata
-- =====================================================
CREATE TABLE IF NOT EXISTS eg_user_idp_details (
    id                bigint                    NOT NULL,
    tenantid          character varying(256)    NOT NULL,
    uuid              character varying(300),

    idptokenexp       timestamp,
    lastssologinat    timestamp,
    tokenid           character varying(128),

    mfaenabled        boolean   DEFAULT FALSE   NOT NULL,
    mfadevicename     character varying(256),
    mfaphonelast4     character varying(4),
    mfaregisteredon   timestamp,
    mfadetails        character varying(256),

    createddate       timestamp DEFAULT CURRENT_TIMESTAMP,
    lastmodifieddate  timestamp DEFAULT CURRENT_TIMESTAMP,
    createdby         bigint,
    lastmodifiedby    bigint,

    CONSTRAINT eg_user_idp_details_pkey
        PRIMARY KEY (id, tenantid),
    CONSTRAINT fk_eg_user_idp_details_user
        FOREIGN KEY (id, tenantid) REFERENCES eg_user (id, tenantid)
);

CREATE INDEX IF NOT EXISTS idx_eg_user_idp_tenantid_uuid_id
    ON eg_user_idp_details (tenantid, uuid, id);

-- Additional indexes for JOIN performance and UUID lookups
CREATE INDEX IF NOT EXISTS idx_eg_user_idp_details_userid_tenantid 
    ON eg_user_idp_details (id, tenantid);

CREATE INDEX IF NOT EXISTS idx_eg_user_idp_details_uuid 
    ON eg_user_idp_details (uuid);


-- =====================================================
-- Table: eg_user_idp_details_audit_table
-- =====================================================
CREATE TABLE IF NOT EXISTS eg_user_idp_details_audit_table (
    id                uuid      NOT NULL DEFAULT gen_random_uuid(),
    userid            bigint    NOT NULL,
    tenantid          character varying(256) NOT NULL,
    uuid              character varying(300),

    idptokenexp       timestamp,
    lastssologinat    timestamp,
    tokenid           character varying(128),

    mfaenabled        boolean DEFAULT FALSE,
    mfadevicename     character varying(256),
    mfaphonelast4     character varying(4),
    mfaregisteredon   timestamp,
    mfadetails        character varying(256),

    createddate       timestamp DEFAULT CURRENT_TIMESTAMP,
    lastmodifieddate  timestamp DEFAULT CURRENT_TIMESTAMP,
    createdby         bigint,
    lastmodifiedby    bigint,

    CONSTRAINT eg_user_idp_details_audit_table_pkey
        PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_eg_user_idp_details_audit_tenantid_uuid_userid
    ON eg_user_idp_details_audit_table (tenantid, uuid, userid);

-- Additional indexes for audit table JOIN performance and UUID lookups
CREATE INDEX IF NOT EXISTS idx_eg_user_idp_details_audit_userid_tenantid 
    ON eg_user_idp_details_audit_table (userid, tenantid);

CREATE INDEX IF NOT EXISTS idx_eg_user_idp_details_audit_uuid 
    ON eg_user_idp_details_audit_table (uuid);
