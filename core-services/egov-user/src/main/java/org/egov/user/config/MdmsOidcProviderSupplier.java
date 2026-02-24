package org.egov.user.config;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsCriteria;
import org.egov.mdms.model.MdmsCriteriaReq;
import org.egov.mdms.model.ModuleDetail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Supplies OIDC providers from MDMS. Add provider config in MDMS master (any IdP: Azure, Google, etc.).
 * List is cached and refreshed periodically.
 * <p>
 * In a <b>central instance</b> (multiple tenants), set {@code mdms.oidcproviders.tenantId} to a
 * <b>comma-separated list</b> of state-level tenant IDs (e.g. {@code tg,pb,pg}). The supplier
 * fetches from MDMS for each tenant and merges the provider lists so each tenant's OIDC config
 * is applied. Each provider's {@code tenantId} in MDMS defines which DIGIT tenant the user
 * belongs to.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.oidc.providers-source", havingValue = OidcConfigConstants.PROVIDERS_SOURCE_MDMS)
public class MdmsOidcProviderSupplier implements OidcProviderSupplier {

    private final RestTemplate restTemplate;
    private final String mdmsHost;
    private final String mdmsEndpoint;
    private final String moduleName;
    private final String masterName;
    /** Comma-separated tenant IDs for central instance; single tenant otherwise. */
    private final List<String> tenantIds;

    private final AtomicReference<List<AuthProperties.Provider>> cache = new AtomicReference<>(null);
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    private volatile long lastFetchTime = 0;

    public MdmsOidcProviderSupplier(
            RestTemplate restTemplate,
            @Value("${egov.mdms.host}") String mdmsHost,
            @Value("${egov.mdms.search.endpoint}") String mdmsEndpoint,
            @Value("${mdms.oidcproviders.moduleName}") String moduleName,
            @Value("${mdms.oidcproviders.masterName}") String masterName,
            @Value("${mdms.oidcproviders.tenantId}") String tenantIdConfig) {
        this.restTemplate = restTemplate;
        this.mdmsHost = mdmsHost;
        this.mdmsEndpoint = mdmsEndpoint;
        this.moduleName = moduleName;
        this.masterName = masterName;
        this.tenantIds = parseTenantIds(tenantIdConfig);
    }

    private static List<String> parseTenantIds(String tenantIdConfig) {
        if (tenantIdConfig == null || tenantIdConfig.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tenantIdConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public List<AuthProperties.Provider> getProviders() {
        long now = System.currentTimeMillis();
        if (cache.get() != null && (now - lastFetchTime) < CACHE_TTL_MS) {
            return cache.get();
        }
        List<AuthProperties.Provider> list = fetchFromMdms();
        if (list != null) {
            cache.set(list);
            lastFetchTime = now;
            return list;
        }
        // Keep previous cache on failure
        List<AuthProperties.Provider> existing = cache.get();
        return existing != null ? existing : Collections.emptyList();
    }

    /**
     * Fetches OIDC providers from MDMS. If multiple tenant IDs are configured (central instance),
     * fetches for each tenant and merges the lists (MDMS fallback applies per request).
     */
    private List<AuthProperties.Provider> fetchFromMdms() {
        if (tenantIds.isEmpty()) {
            log.warn("MDMS OIDC providers: no tenant ID configured");
            return null;
        }
        List<AuthProperties.Provider> merged = new ArrayList<>();
        for (String tenantId : tenantIds) {
            List<AuthProperties.Provider> forTenant = fetchFromMdmsForTenant(tenantId);
            if (forTenant != null) {
                merged.addAll(forTenant);
            }
        }
        if (merged.isEmpty()) {
            log.warn("MDMS OIDC providers: no providers loaded for any tenant");
            return null;
        }
        log.info("MDMS OIDC providers loaded: {} provider(s) across {} tenant(s)", merged.size(), tenantIds.size());
        return merged;
    }

    private List<AuthProperties.Provider> fetchFromMdmsForTenant(String tenantId) {
        String url = mdmsHost + mdmsEndpoint;
        try {
            MasterDetail masterDetail = MasterDetail.builder().name(masterName).build();
            ModuleDetail moduleDetail = ModuleDetail.builder()
                    .moduleName(moduleName)
                    .masterDetails(Collections.singletonList(masterDetail))
                    .build();
            MdmsCriteria mdmsCriteria = new MdmsCriteria();
            mdmsCriteria.setTenantId(tenantId);
            mdmsCriteria.setModuleDetails(Collections.singletonList(moduleDetail));
            MdmsCriteriaReq req = new MdmsCriteriaReq();
            req.setRequestInfo(new RequestInfo());
            req.setMdmsCriteria(mdmsCriteria);

            JsonNode response = restTemplate.postForObject(url, req, JsonNode.class);
            if (response == null || !response.has(OidcConfigConstants.MDMS_RES)) {
                log.debug("MDMS OIDC providers: no MdmsRes for tenant {}", tenantId);
                return null;
            }
            JsonNode moduleNode = response.get(OidcConfigConstants.MDMS_RES).get(moduleName);
            if (moduleNode == null || !moduleNode.has(masterName)) {
                log.debug("MDMS OIDC providers: missing {}.{} for tenant {}", moduleName, masterName, tenantId);
                return null;
            }
            JsonNode masterNode = moduleNode.get(masterName);
            if (!masterNode.isArray()) {
                log.warn("MDMS OIDC providers: {} is not an array for tenant {}", masterName, tenantId);
                return null;
            }
            List<AuthProperties.Provider> providers = new ArrayList<>();
            for (JsonNode node : masterNode) {
                try {
                    if (!isProviderActive(node)) {
                        continue;
                    }
                    AuthProperties.Provider p = mapNodeToProvider(node);
                    if (p != null && p.getId() != null && p.getIssuerUri() != null) {
                        providers.add(p);
                    }
                } catch (Exception e) {
                    log.warn("MDMS OIDC provider entry parse error for tenant {}: {}", tenantId, e.getMessage());
                }
            }
            return providers;
        } catch (Exception e) {
            log.error("Failed to fetch OIDC providers from MDMS for tenant {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    /** When true, provider is included; when false, provider is skipped (deactivated in MDMS). Missing = active. */
    private static boolean isProviderActive(JsonNode n) {
        if (n == null) return true;
        if (n.has(OidcConfigConstants.KEY_ACTIVE) && !n.get(OidcConfigConstants.KEY_ACTIVE).isNull())
            return n.get(OidcConfigConstants.KEY_ACTIVE).asBoolean(true);
        if (n.has(OidcConfigConstants.KEY_IS_ACTIVE) && !n.get(OidcConfigConstants.KEY_IS_ACTIVE).isNull())
            return n.get(OidcConfigConstants.KEY_IS_ACTIVE).asBoolean(true);
        return true;
    }

    private static String textOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }

    private AuthProperties.Provider mapNodeToProvider(JsonNode n) throws Exception {
        AuthProperties.Provider p = new AuthProperties.Provider();
        if (n.has(OidcConfigConstants.KEY_ID)) p.setId(textOrNull(n.get(OidcConfigConstants.KEY_ID)));
        if (n.has(OidcConfigConstants.KEY_ISSUER_URI)) p.setIssuerUri(textOrNull(n.get(OidcConfigConstants.KEY_ISSUER_URI)));
        if (n.has(OidcConfigConstants.KEY_ISSUER_ALIASES) && n.get(OidcConfigConstants.KEY_ISSUER_ALIASES).isArray()) {
            List<String> aliases = new ArrayList<>();
            n.get(OidcConfigConstants.KEY_ISSUER_ALIASES).forEach(a -> aliases.add(a.asText()));
            p.setIssuerAliases(aliases);
        }
        if (n.has(OidcConfigConstants.KEY_JWK_SET_URI)) p.setJwkSetUri(textOrNull(n.get(OidcConfigConstants.KEY_JWK_SET_URI)));
        if (n.has(OidcConfigConstants.KEY_AUDIENCES) && n.get(OidcConfigConstants.KEY_AUDIENCES).isArray()) {
            List<String> aud = new ArrayList<>();
            n.get(OidcConfigConstants.KEY_AUDIENCES).forEach(a -> aud.add(a.asText()));
            p.setAudiences(aud);
        }
        if (n.has(OidcConfigConstants.KEY_TENANT_ID)) p.setTenantId(textOrNull(n.get(OidcConfigConstants.KEY_TENANT_ID)));
        if (n.has(OidcConfigConstants.KEY_USER_TYPE)) p.setUserType(textOrNull(n.get(OidcConfigConstants.KEY_USER_TYPE)));
        if (n.has(OidcConfigConstants.KEY_DEFAULT_ROLE_CODES)) p.setDefaultRoleCodes(textOrNull(n.get(OidcConfigConstants.KEY_DEFAULT_ROLE_CODES)));
        if (n.has(OidcConfigConstants.KEY_ROLE_CLAIM_KEY)) p.setRoleClaimKey(n.get(OidcConfigConstants.KEY_ROLE_CLAIM_KEY).asText(OidcConfigConstants.DEFAULT_ROLE_CLAIM_KEY));
        if (n.has(OidcConfigConstants.KEY_ROLE_MAPPING)) {
            JsonNode rm = n.get(OidcConfigConstants.KEY_ROLE_MAPPING);
            p.setRoleMapping(rm.isTextual() ? rm.asText() : rm.toString());
        }
        if (n.has(OidcConfigConstants.KEY_DEFAULT_DOB) && !n.get(OidcConfigConstants.KEY_DEFAULT_DOB).isNull()) p.setDefaultDob(n.get(OidcConfigConstants.KEY_DEFAULT_DOB).asLong());
        if (n.has(OidcConfigConstants.KEY_DEFAULT_EMPLOYEE_STATUS)) p.setDefaultEmployeeStatus(n.get(OidcConfigConstants.KEY_DEFAULT_EMPLOYEE_STATUS).asText(OidcConfigConstants.DEFAULT_EMPLOYED_STATUS));
        if (n.has(OidcConfigConstants.KEY_ROLE_PREFIX)) p.setRolePrefix(n.get(OidcConfigConstants.KEY_ROLE_PREFIX).asText(OidcConfigConstants.DEFAULT_ROLE_PREFIX));
        if (n.has(OidcConfigConstants.KEY_DECRYPTION_PURPOSE)) p.setDecryptionPurpose(n.get(OidcConfigConstants.KEY_DECRYPTION_PURPOSE).asText(OidcConfigConstants.DEFAULT_DECRYPTION_PURPOSE));
        if (n.has(OidcConfigConstants.KEY_GRAPH_CLIENT_ID)) p.setGraphClientId(textOrNull(n.get(OidcConfigConstants.KEY_GRAPH_CLIENT_ID)));
        if (n.has(OidcConfigConstants.KEY_GRAPH_CLIENT_SECRET)) p.setGraphClientSecret(textOrNull(n.get(OidcConfigConstants.KEY_GRAPH_CLIENT_SECRET)));
        if (n.has(OidcConfigConstants.KEY_GRAPH_TENANT_ID)) p.setGraphTenantId(textOrNull(n.get(OidcConfigConstants.KEY_GRAPH_TENANT_ID)));
        if (n.has(OidcConfigConstants.KEY_GRAPH_METHODS_URL)) p.setGraphMethodsUrl(textOrNull(n.get(OidcConfigConstants.KEY_GRAPH_METHODS_URL)));
        if (n.has(OidcConfigConstants.KEY_GRAPH_TOKEN_URL)) p.setGraphTokenUrl(textOrNull(n.get(OidcConfigConstants.KEY_GRAPH_TOKEN_URL)));
        if (n.has(OidcConfigConstants.KEY_GRAPH_SCOPE)) p.setGraphScope(textOrNull(n.get(OidcConfigConstants.KEY_GRAPH_SCOPE)));
        if (n.has(OidcConfigConstants.KEY_GRAPH_SERVICE_TYPE)) p.setGraphServiceType(textOrNull(n.get(OidcConfigConstants.KEY_GRAPH_SERVICE_TYPE)));
        return p;
    }
}
