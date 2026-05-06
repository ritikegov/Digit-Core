package org.egov.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.egov.domain.model.MobileValidationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
@Slf4j
public class ValidationRulesCacheRepository {

    private static final String HASH_KEY = "validationRules";
    private static final String CACHE_KEY_PREFIX = "validation:";
    private static final String DEFAULT_COUNTRY_KEY = "default";

    @Value("${egov.validation.cache.ttl.seconds:3600}")
    private long cacheTtlSeconds;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void clearCacheOnStartup() {
        try {
            redisTemplate.delete(HASH_KEY);
            log.info("Cleared validation rules cache on startup");
        } catch (Exception e) {
            log.warn("Could not clear validation rules cache on startup: {}", e.getMessage());
        }
    }

    public MobileValidationConfig getValidationRules(String tenantId, String countryCode) {
        String field = buildField(tenantId, countryCode);
        try {
            String cached = (String) redisTemplate.opsForHash().get(HASH_KEY, field);
            if (cached != null) {
                return objectMapper.readValue(cached, MobileValidationConfig.class);
            }
        } catch (Exception e) {
            log.warn("Error reading from validation rules cache for key {}: {}", field, e.getMessage());
        }
        return null;
    }

    public void setValidationRules(String tenantId, String countryCode, MobileValidationConfig config) {
        String field = buildField(tenantId, countryCode);
        try {
            String value = objectMapper.writeValueAsString(config);
            redisTemplate.opsForHash().put(HASH_KEY, field, value);
            redisTemplate.expire(HASH_KEY, cacheTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Error writing to validation rules cache for key {}: {}", field, e.getMessage());
        }
    }

    private String buildField(String tenantId, String countryCode) {
        String key = (countryCode == null || countryCode.isBlank()) ? DEFAULT_COUNTRY_KEY : countryCode;
        return CACHE_KEY_PREFIX + tenantId + ":" + key;
    }
}
