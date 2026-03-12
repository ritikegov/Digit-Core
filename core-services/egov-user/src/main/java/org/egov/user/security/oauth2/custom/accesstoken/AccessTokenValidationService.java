package org.egov.user.security.oauth2.custom.accesstoken;

import lombok.extern.slf4j.Slf4j;
import org.egov.user.config.AuthProperties;
import org.egov.user.config.OidcProviderSupplier;
import org.egov.user.domain.exception.InvalidAccessTokenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Service for validating access tokens during JWT exchange flow.
 * Provides mandatory validation when access token is present.
 */
@Slf4j
@Component
public class AccessTokenValidationService {
    
    private final List<AccessTokenValidator> validators;
    private final OidcProviderSupplier oidcProviderSupplier;
    
    @Autowired
    public AccessTokenValidationService(List<AccessTokenValidator> validators, 
                               OidcProviderSupplier oidcProviderSupplier) {
        this.validators = validators;
        this.oidcProviderSupplier = oidcProviderSupplier;
    }
    
    /**
     * Validates access token for the given provider.
     * This method performs MANDATORY validation - no fallback to NoOp.
     * 
     * @param accessToken the access token to validate
     * @param providerId the provider ID from JWT
     * @param tenantId the tenant ID
     * @return AccessTokenValidationResult with validated claims if successful
     * @throws InvalidAccessTokenException if validation fails or no validator available
     */
    public AccessTokenValidationResult validate(String accessToken, String providerId, String tenantId) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new InvalidAccessTokenException();
        }
        
        // Find provider configuration
        AuthProperties.Provider provider = oidcProviderSupplier.getProviders().stream()
                .filter(p -> p.getId().equals(providerId) && p.getTenantId().equals(tenantId))
                .findFirst()
                .orElseThrow(() -> new InvalidAccessTokenException(
                ));
        
        // Find appropriate validator - MANDATORY, no fallback
        AccessTokenValidator validator = validators.stream()
                .filter(v -> v.supports(provider))
                .findFirst()
                .orElseThrow(() -> new InvalidAccessTokenException());
        
        log.debug("Validating access token for provider: {} with validator: {}", 
                providerId, validator.getClass().getSimpleName());
        
        // Perform validation
        AccessTokenValidationResult result = validator.validate(accessToken, provider);
        
        if (!result.isValid()) {
            log.warn("Access token validation failed for provider: {}, error: {}, code: {}", 
                    providerId, result.getError(), result.getErrorCode());
            throw new InvalidAccessTokenException();
        }
        
        log.debug("Access token validation successful for provider: {}", providerId);
        return result;
    }
}
