package org.egov.user.config;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private Oidc oidc = new Oidc();

    private Forward forward = new Forward();
    private Sso sso = new Sso();
    private List<Provider> providers = new ArrayList<>();
    private String defaultBoundaryCode;

    @Getter
    @Setter
    public static class Sso {
        /** Override default password per tenant+provider. Key: tenantId_providerId, e.g. tg_oidc_azure (no dots so Spring binds reliably). */
        private Map<String, String> defaultPasswordOverride = new HashMap<>();
    }

    @Getter
    @Setter
    public static class Oidc {
        private boolean enabled = false;
        /**
         * Source of OIDC provider list: "static" (from auth.providers) or "mdms" (from MDMS master).
         * When "mdms", provider config is fetched from MDMS and works for any OIDC IdP (Azure, Google, etc.).
         */
        private String providersSource = OidcConfigConstants.PROVIDERS_SOURCE_STATIC;
    }

    @Getter
    @Setter
    public static class Forward {
        private boolean authorizationHeader = true;
        private boolean requestinfoAuthtoken = false;
    }

    @Getter
    @Setter
    public static class Provider {
        private String id;
        private String issuerUri;
        /**
         * Optional alternate issuer values for the same provider.
         * Useful when an IdP can emit tokens with multiple valid issuer formats
         * (e.g. Azure AD v1 vs v2 issuer strings).
         *
         * Property: auth.providers[i].issuer-aliases[0..n]
         */
        private List<String> issuerAliases = new ArrayList<>();
        private String jwkSetUri;
        private List<String> audiences = new ArrayList<>();
        private String tenantId;
        private String userType = "EMPLOYEE";
        private String defaultRoleCodes;
        private String roleClaimKey = OidcConfigConstants.DEFAULT_ROLE_CLAIM_KEY;
        private Map<String, String> roleMapping;
        private String defaultPassword;
        private Long defaultDob;
        private String defaultEmployeeStatus = OidcConfigConstants.DEFAULT_EMPLOYED_STATUS;
        private String rolePrefix = OidcConfigConstants.DEFAULT_ROLE_PREFIX;
        private String decryptionPurpose = OidcConfigConstants.DEFAULT_DECRYPTION_PURPOSE;
        private String graphClientId;
        private String graphClientSecret;
        private String graphTenantId;
        private String graphMethodsUrl = OidcConfigConstants.DEFAULT_GRAPH_METHODS_URL;
        private String graphUsersUrl = OidcConfigConstants.DEFAULT_GRAPH_USERS_URL;
        private String graphTokenUrl = OidcConfigConstants.DEFAULT_GRAPH_TOKEN_URL;
        private String graphScope = OidcConfigConstants.DEFAULT_GRAPH_SCOPE;
        /**
         * IdP-specific graph/MFA enrichment service type: "azure" (Microsoft Graph), "none", or custom.
         * Property: auth.providers[i].graph-service-type
         */
        private String graphServiceType = OidcConfigConstants.GRAPH_SERVICE_TYPE_AZURE;

        @JsonSetter("roleMapping")
        public void setRoleMapping(String roleMappingString) throws IOException {
            roleMapping = (Map<String, String>) new ObjectMapper().readValue(roleMappingString, Map.class);
        }
    }
}
