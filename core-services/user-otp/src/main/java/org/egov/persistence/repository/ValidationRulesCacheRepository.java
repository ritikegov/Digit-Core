package org.egov.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.domain.model.MobileValidationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * Repository for caching MDMS validation rules in Redis.
 * Caches individual validation configs keyed by tenant:countryCode.
 */
@Repository
@Slf4j
public class ValidationRulesCacheRepository {

    private static final String VALIDATION_RULES_HASH_KEY = "validationRules";
    private static final String CACHE_KEY_PREFIX = "validation:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${egov.validation.cache.ttl.seconds:3600}")
    private long cacheTtlSeconds;

    @Autowired
    public ValidationRulesCacheRepository(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Clear validation rules cache on service startup to ensure fresh data from MDMS.
     */
    @PostConstruct
    public void clearCacheOnStartup() {
        try {
            clearAllCache();
            log.info("Cleared validation rules cache on service startup");
        } catch (Exception e) {
            log.warn("Failed to clear validation rules cache on startup: {}", e.getMessage());
        }
    }

    /**
     * Get cached validation rules for a tenant and countryCode combination.
     *
     * @param tenantId    the tenant ID
     * @param countryCode the country code (e.g. "+91"), or "default" for the default config
     * @return cached MobileValidationConfig or null if not found
     */
    public MobileValidationConfig getValidationRules(String tenantId, String countryCode) {
        try {
            String cacheKey = getCacheKey(tenantId, countryCode);
            Object cachedValue = stringRedisTemplate.opsForHash().get(VALIDATION_RULES_HASH_KEY, cacheKey);

            if (cachedValue != null) {
                log.debug("Cache hit for validation rules, tenantId: {}, countryCode: {}", tenantId, countryCode);
                MobileValidationConfig config = objectMapper.readValue(cachedValue.toString(), MobileValidationConfig.class);
                if (config.getRules() == null) {
                    log.warn("Cached config has null rules (stale/invalid format), clearing cache for tenantId: {}, countryCode: {}", tenantId, countryCode);
                    clearCacheEntry(tenantId, countryCode);
                    return null;
                }
                return config;
            }

            log.debug("Cache miss for validation rules, tenantId: {}, countryCode: {}", tenantId, countryCode);
            return null;
        } catch (JsonProcessingException e) {
            log.error("Error deserializing cached validation rules for tenantId: {}, countryCode: {}", tenantId, countryCode, e);
            return null;
        } catch (Exception e) {
            log.error("Error retrieving validation rules from cache for tenantId: {}, countryCode: {}", tenantId, countryCode, e);
            return null;
        }
    }

    /**
     * Cache validation rules for a tenant and countryCode combination.
     *
     * @param tenantId    the tenant ID
     * @param countryCode the country code (e.g. "+91"), or "default" for the default config
     * @param config      the validation config to cache
     */
    public void cacheValidationRules(String tenantId, String countryCode, MobileValidationConfig config) {
        try {
            String cacheKey = getCacheKey(tenantId, countryCode);
            String cacheValue = objectMapper.writeValueAsString(config);

            stringRedisTemplate.opsForHash().put(VALIDATION_RULES_HASH_KEY, cacheKey, cacheValue);

            if (cacheTtlSeconds > 0) {
                stringRedisTemplate.expire(VALIDATION_RULES_HASH_KEY, cacheTtlSeconds, TimeUnit.SECONDS);
            }

            log.info("Cached validation rules for tenantId: {}, countryCode: {}, TTL: {} seconds", tenantId, countryCode, cacheTtlSeconds);
        } catch (JsonProcessingException e) {
            log.error("Error serializing validation rules for caching, tenantId: {}, countryCode: {}", tenantId, countryCode, e);
        } catch (Exception e) {
            log.error("Error caching validation rules for tenantId: {}, countryCode: {}", tenantId, countryCode, e);
        }
    }

    /**
     * Clear a specific cache entry for tenant and countryCode.
     */
    public void clearCacheEntry(String tenantId, String countryCode) {
        try {
            String cacheKey = getCacheKey(tenantId, countryCode);
            stringRedisTemplate.opsForHash().delete(VALIDATION_RULES_HASH_KEY, cacheKey);
            log.info("Cleared cache for tenantId: {}, countryCode: {}", tenantId, countryCode);
        } catch (Exception e) {
            log.error("Error clearing cache for tenantId: {}, countryCode: {}", tenantId, countryCode, e);
        }
    }

    /**
     * Clear all cached validation rules.
     */
    public void clearAllCache() {
        try {
            stringRedisTemplate.delete(VALIDATION_RULES_HASH_KEY);
            log.info("Cleared all validation rules cache");
        } catch (Exception e) {
            log.error("Error clearing all validation rules cache", e);
        }
    }

    private String getCacheKey(String tenantId, String countryCode) {
        return CACHE_KEY_PREFIX + tenantId + ":" + countryCode;
    }
}
