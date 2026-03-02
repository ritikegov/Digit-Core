package org.egov.user.repository.rowmapper;

import org.egov.user.domain.model.UserIdpDetails;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class UserIdpDetailsRowMapper implements RowMapper<UserIdpDetails> {

    @Override
    public UserIdpDetails mapRow(ResultSet rs, int rowNum) throws SQLException {
        return UserIdpDetails.builder()
                .id(rs.getLong("id"))
                .tenantId(rs.getString("tenantid"))
                .uuid(rs.getString("uuid"))
                .idpTokenExp(rs.getTimestamp("idp_token_exp"))
                .lastSsoLoginAt(rs.getTimestamp("last_sso_login_at"))
                .tokenId(rs.getString("token_id"))
                .mfaEnabled(rs.getBoolean("mfa_enabled"))
                .mfaDeviceName(rs.getString("mfa_device_name"))
                .mfaPhoneLast4(rs.getString("mfa_phone_last4"))
                .mfaRegisteredOn(rs.getTimestamp("mfa_registered_on"))
                .mfaDetails(rs.getString("mfa_details"))
                .createdDate(rs.getTimestamp("created_date"))
                .lastModifiedDate(rs.getTimestamp("lastmodifieddate"))
                .createdBy(rs.getObject("createdby", Long.class))
                .lastModifiedBy(rs.getObject("lastmodifiedby", Long.class))
                .build();
    }
}
