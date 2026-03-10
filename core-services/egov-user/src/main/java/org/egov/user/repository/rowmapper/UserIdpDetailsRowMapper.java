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
                .idpTokenExp(rs.getTimestamp("idptokenexp"))
                .lastSsoLoginAt(rs.getTimestamp("lastssologinat"))
                .tokenId(rs.getString("tokenid"))
                .mfaEnabled(rs.getBoolean("mfaenabled"))
                .mfaDeviceName(rs.getString("mfadevicename"))
                .mfaPhoneLast4(rs.getString("mfaphonelast4"))
                .mfaRegisteredOn(rs.getTimestamp("mfaregisteredon"))
                .mfaDetails(rs.getString("mfadetails"))
                .createdDate(rs.getTimestamp("createddate"))
                .lastModifiedDate(rs.getTimestamp("lastmodifieddate"))
                .createdBy(rs.getObject("createdby", Long.class))
                .lastModifiedBy(rs.getObject("lastmodifiedby", Long.class))
                .build();
    }
}
