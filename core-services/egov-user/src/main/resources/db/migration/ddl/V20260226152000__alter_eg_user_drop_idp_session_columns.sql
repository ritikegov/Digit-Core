-- Drop IDP session and MFA columns from eg_user; these are now in eg_user_idp_details.
-- idp_issuer, idp_subject, auth_provider remain in eg_user for identity linkage.
ALTER TABLE eg_user
    DROP COLUMN IF EXISTS idp_token_exp,
    DROP COLUMN IF EXISTS last_sso_login_at,
    DROP COLUMN IF EXISTS jwt_token,
    DROP COLUMN IF EXISTS mfa_enabled,
    DROP COLUMN IF EXISTS mfa_device_name,
    DROP COLUMN IF EXISTS mfa_phone_last4,
    DROP COLUMN IF EXISTS mfa_registered_on,
    DROP COLUMN IF EXISTS mfa_details;
