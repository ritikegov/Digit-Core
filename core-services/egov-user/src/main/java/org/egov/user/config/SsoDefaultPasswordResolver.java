package org.egov.user.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves default password for new SSO-created users by tenantId and provider id.
 * Order: (1) env var SSO_DEFAULT_PASSWORD_&lt;TENANT&gt;_&lt;PROVIDER&gt;, (2) config override map, (3) global default.
 * No password is read from MDMS.
 */
@Slf4j
@Component
public class SsoDefaultPasswordResolver {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]");

    private final Map<String, String> defaultPasswordOverride;
    private final String globalDefaultPassword;

    public SsoDefaultPasswordResolver(
            AuthProperties authProperties,
            @Value("${auth.user.default.password}") String globalDefaultPassword) {
        this.defaultPasswordOverride = authProperties.getSso() != null && authProperties.getSso().getDefaultPasswordOverride() != null
                ? authProperties.getSso().getDefaultPasswordOverride()
                : Collections.emptyMap();
        this.globalDefaultPassword = globalDefaultPassword;
    }

    @PostConstruct
    public void logConfig() {
        log.info("SsoDefaultPasswordResolver: override map keys={}, globalDefault set={}",
                defaultPasswordOverride.keySet(), globalDefaultPassword != null);
    }

    /**
     * Resolves the default password for a new SSO user. Uses env var first, then config override, then global default.
     *
     * @param tenantId   DIGIT tenant id (e.g. tg, gg)
     * @param providerId OIDC provider id (e.g. oidc-azure)
     * @return password to use (never null)
     */
    public String resolveDefaultPassword(String tenantId, String providerId) {
        if (tenantId == null) tenantId = "";
        if (providerId == null) providerId = "";

        String envKey = OidcConfigConstants.SSO_DEFAULT_PASSWORD_ENV_PREFIX + normalizeForEnv(tenantId) + "_" + normalizeForEnv(providerId);
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isEmpty()) {
            log.debug("SSO password resolved from env var: key={}", envKey);
            return fromEnv;
        }

        String mapKey = buildOverrideMapKey(tenantId, providerId);
        log.debug("SSO password lookup: mapKey={}, overrideMapKeys={}", mapKey, defaultPasswordOverride.keySet());
        String fromMap = defaultPasswordOverride.get(mapKey);
        if (fromMap != null && !fromMap.isEmpty()) {
            log.debug("SSO password resolved from override map: key={}", mapKey);
            return fromMap;
        }

        log.debug("SSO password falling back to global default: tenantId={}, providerId={}", tenantId, providerId);
        return globalDefaultPassword;
    }

    /**
     * Builds the override map key. Uses underscore (no dots) so Spring binds
     * auth.sso.default-password-override.tg_oidc_azure=... correctly.
     */
    private static String buildOverrideMapKey(String tenantId, String providerId) {
        if (tenantId == null) tenantId = "";
        if (providerId == null) providerId = "";
        String safeProvider = providerId.replace("-", "_");
        return tenantId + "_" + safeProvider;
    }

    /**
     * Uppercase and replace non-alphanumeric with underscore for env var name.
     */
    private static String normalizeForEnv(String value) {
        if (value == null || value.isEmpty()) return "";
        return NON_ALPHANUMERIC.matcher(value.toUpperCase()).replaceAll("_");
    }
}
