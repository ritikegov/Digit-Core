package org.egov.user.security.oauth2.custom.accesstoken;

import org.egov.user.config.AuthProperties;

/**
 * Interface for validating access tokens from different identity providers.
 * Implementations provide provider-specific validation logic for access tokens
 * received during JWT exchange flow.
 */
public interface AccessTokenValidator {
    
    /**
     * Checks if this validator supports the given provider.
     * 
     * @param provider the OIDC provider configuration
     * @return true if this validator can handle access tokens from this provider
     */
    boolean supports(AuthProperties.Provider provider);
    
    /**
     * Validates an access token for the given provider.
     * This method must throw an exception if validation fails.
     * 
     * @param accessToken the access token to validate
     * @param provider the OIDC provider configuration
     * @return AccessTokenValidationResult with validation outcome
     * @throws InvalidAccessTokenException if token is invalid
     */
    AccessTokenValidationResult validate(String accessToken, AuthProperties.Provider provider);
}
