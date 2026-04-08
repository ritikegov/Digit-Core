package org.egov.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.domain.exception.InvalidOtpRequestException;
import org.egov.domain.model.MobileValidationConfig;
import org.egov.domain.model.MobileValidationRules;
import org.egov.domain.model.OtpRequest;
import org.egov.domain.model.OtpRequestType;

import java.util.List;
import org.egov.persistence.repository.MdmsRepository;
import org.egov.persistence.repository.ValidationRulesCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.springframework.util.ObjectUtils.isEmpty;

/**
 * Validator for OTP requests. Uses MDMS-based validation only.
 * Uses Redis cache for MDMS validation config to avoid repeated API calls.
 */
@Component
@Slf4j
public class OtpRequestValidator {

    private final MdmsRepository mdmsRepository;
    private final ValidationRulesCacheRepository cacheRepository;

    @Autowired
    public OtpRequestValidator(MdmsRepository mdmsRepository,
                               ValidationRulesCacheRepository cacheRepository) {
        this.mdmsRepository = mdmsRepository;
        this.cacheRepository = cacheRepository;
    }

    /**
     * Validates the OTP request. Fetches MDMS config and performs validation.
     * Throws InvalidOtpRequestException if validation fails.
     */
    public void validate(OtpRequest otpRequest) {
        // Fetch and set MDMS validation config
        fetchAndSetMdmsValidationConfig(otpRequest);

        // Perform validation
        if (isTenantIdAbsent(otpRequest)
                || isMobileNumberAbsent(otpRequest)
                || !isMobileNumberValid(otpRequest)
                || isInvalidType(otpRequest)) {
            throw new InvalidOtpRequestException(otpRequest);
        }
    }

    public boolean isTenantIdAbsent(OtpRequest otpRequest) {
        return isEmpty(otpRequest.getTenantId());
    }

    public boolean isMobileNumberAbsent(OtpRequest otpRequest) {
        return isEmpty(otpRequest.getMobileNumber());
    }

    public boolean isInvalidType(OtpRequest otpRequest) {
        return isEmpty(otpRequest.getType());
    }

    /**
     * Validates the mobile number using MDMS config.
     * If no MDMS config is found, validation fails with an appropriate error message.
     */
    public boolean isMobileNumberValid(OtpRequest otpRequest) {

        // Skip validation for PASSWORD_RESET type to allow existing users to reset password
        // even if their mobile number doesn't conform to new validation rules
        if (otpRequest.getType() != null
                && OtpRequestType.PASSWORD_RESET.equals(otpRequest.getType())) {
            return true;
        }

        MobileValidationConfig mdmsConfig = otpRequest.getMdmsValidationConfig();

        // If no MDMS config found, validation fails
        if (mdmsConfig == null || mdmsConfig.getRules() == null) {
            String prefix = otpRequest.getPrefix();
            if (prefix != null && !prefix.isEmpty()) {
                otpRequest.setMdmsValidationErrorMessage(
                        "Mobile number validation failed. No validation pattern found for country code "
                                + prefix + " and no default validation pattern is configured in MDMS");
            } else {
                otpRequest.setMdmsValidationErrorMessage(
                        "Mobile number validation failed. No default validation pattern is configured in MDMS");
            }
            return false;
        }

        return validateWithMdmsConfig(otpRequest, mdmsConfig.getRules());
    }

    /**
     * Validates mobile number using MDMS configuration rules.
     */
    private boolean validateWithMdmsConfig(OtpRequest otpRequest, MobileValidationRules rules) {
        String mobileNumber = otpRequest.getMobileNumber();

        // Validate minimum length
        if (rules.getMinLength() != null && mobileNumber.length() < rules.getMinLength()) {
            otpRequest.setMdmsValidationErrorMessage(rules.getErrorMessage());
            return false;
        }

        // Validate maximum length
        if (rules.getMaxLength() != null && mobileNumber.length() > rules.getMaxLength()) {
            otpRequest.setMdmsValidationErrorMessage(rules.getErrorMessage());
            return false;
        }

        // Validate pattern (includes starting character validation via regex)
        if (rules.getPattern() != null && !rules.getPattern().isEmpty()) {
            if (!mobileNumber.matches(rules.getPattern())) {
                otpRequest.setMdmsValidationErrorMessage(rules.getErrorMessage());
                return false;
            }
        }

        return true;
    }

    /**
     * Fetches validation config from cache first, falls back to MDMS API if not cached.
     * Caches the result for subsequent requests (cache-aside pattern).
     *
     * Selection logic:
     * - If prefix is sent in request -> find config where attributes.prefix matches
     *   - If no match for that prefix -> fall back to config where default=true
     * - If prefix is not sent -> find config where default=true
     */
    private void fetchAndSetMdmsValidationConfig(OtpRequest otpRequest) {
        String tenantId = otpRequest.getTenantId();

        // Skip if tenantId is not provided
        if (tenantId == null || tenantId.isEmpty()) {
            log.debug("TenantId is null or empty, skipping MDMS config fetch");
            return;
        }

        // Extract state-level tenant ID for caching (same as egov-user)
        String stateLevelTenantId = tenantId;
        if (tenantId.contains(".")) {
            stateLevelTenantId = tenantId.split("\\.")[0];
        }

        String prefix = otpRequest.getPrefix();
        String cacheKeyPrefix = (prefix != null && !prefix.isEmpty()) ? prefix : "default";

        try {
            // Check cache first
            MobileValidationConfig cachedConfig = cacheRepository.getValidationRules(stateLevelTenantId, cacheKeyPrefix);
            if (cachedConfig != null) {
                log.debug("Using cached validation config for tenantId: {}, prefix: {}", stateLevelTenantId, cacheKeyPrefix);
                otpRequest.setMdmsValidationConfig(cachedConfig);
                return;
            }

            // Cache miss - fetch all configs from MDMS
            List<MobileValidationConfig> configs = mdmsRepository.fetchMobileValidationConfigs(
                    tenantId, otpRequest.getRequestInfo());

            if (configs.isEmpty()) {
                log.info("No MDMS mobile validation configs found for tenantId: {} and prefix: {}", tenantId, cacheKeyPrefix);
                return;
            }

            // Select the right config based on prefix
            MobileValidationConfig selectedConfig = selectConfig(configs, prefix);

            if (selectedConfig != null) {
                log.info("MDMS mobile validation config selected for tenantId: {}, prefix: {}, caching...", tenantId, cacheKeyPrefix);
                cacheRepository.cacheValidationRules(stateLevelTenantId, cacheKeyPrefix, selectedConfig);
                otpRequest.setMdmsValidationConfig(selectedConfig);
            } else {
                log.info("No matching MDMS config found for tenantId: {} and prefix: {}", tenantId, cacheKeyPrefix);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch MDMS validation config: {}", e.getMessage());
        }
    }

    /**
     * Selects the appropriate validation config from the list.
     * If prefix is provided, selects the config where attributes.prefix matches.
     * If no match found for that prefix, falls back to the default config.
     * If prefix is not provided, selects the config where default is true.
     */
    private MobileValidationConfig selectConfig(List<MobileValidationConfig> configs, String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            // Find config matching the given prefix
            MobileValidationConfig prefixConfig = configs.stream()
                    .filter(c -> c.getAttributes() != null
                            && prefix.equals(c.getAttributes().getPrefix()))
                    .findFirst()
                    .orElse(null);

            if (prefixConfig != null) {
                return prefixConfig;
            }

            // Prefix config not found, fall back to default
            log.info("No MDMS config found for prefix: {}, falling back to default config", prefix);
        }

        // Find the default config
        return configs.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsDefault()))
                .findFirst()
                .orElse(null);
    }
}
