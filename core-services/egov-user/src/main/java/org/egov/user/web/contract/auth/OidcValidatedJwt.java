package org.egov.user.web.contract.auth;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

@Getter
public class OidcValidatedJwt {
    private final Set<String> roles;
    private final Map<String, Object> claims;
    private final Date expirationTime;
    private final Date issuanceTime;
    private final String projectName;
    private final String hierarchy;
    private final String boundary;
    /*
     * FIXME [Severity: HIGH - Security]
     * Carrying the raw JWT token through the object graph and into the DB is a security risk.
     * JWTs are bearer credentials — if the DB is breached, tokens can be replayed until expiry.
     * Store only a hash or token metadata (jti, exp) instead.
     */
    private final String rawToken;
    private final String providerId;

    public OidcValidatedJwt(Set<String> roles, Map<String, Object> claims, Date expirationTime, Date issuanceTime,
            String projectName, String hierarchy, String boundary, String rawToken, String providerId) {
        this.claims = claims;
        this.roles = roles;
        this.expirationTime = expirationTime;
        this.issuanceTime = issuanceTime;
        this.projectName = projectName;
        this.hierarchy = hierarchy;
        this.boundary = boundary;
        this.rawToken = rawToken;
        this.providerId = providerId;
    }

    public String getIssuer() {
        return (String) claims.get("iss");
    }

    public String getSubject() {
        return (String) claims.get("sub");
    }

    public String getEmail() {
        return (String) claims.get("email");
    }

    public String getName() {
        return (String) claims.getOrDefault("name", getSubject());
    }

    public String getPreferredUsername() {
        return (String) claims.getOrDefault("preferred_username", getSubject());
    }

    public String getTenantId() {
        return (String) claims.getOrDefault("tenantId", (String) claims.get("tenant_id"));
    }

    public String getUserType() {
        return (String) claims.getOrDefault("userType", (String) claims.get("user_type"));
    }

    public String getExternalUserId() {
        // Use sub as stable ID
        return getSubject();
    }

    /**
     * Azure/Entra object id (oid). Required for Microsoft Graph API user lookup.
     * Falls back to sub if oid is not present.
     */
    public String getOid() {
        Object oid = claims.get("oid");
        return oid != null ? oid.toString() : getSubject();
    }

    public String getRawToken() {
        return rawToken;
    }

    public String getProviderId() {
        return providerId;
    }
}
