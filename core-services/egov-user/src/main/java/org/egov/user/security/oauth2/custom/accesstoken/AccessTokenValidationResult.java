package org.egov.user.security.oauth2.custom.accesstoken;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.Builder;
import lombok.Getter;

/**
 * Result of access token validation containing validation outcome and error details.
 */
@Getter
@Builder
public class AccessTokenValidationResult {
    
    /**
     * Whether the access token is valid
     */
    private final boolean valid;
    
    /**
     * Error message if validation failed
     */
    private final String error;
    
    /**
     * Error code for failed validation
     */
    private final String errorCode;
    
    /**
     * Validated JWT claims set (only available when valid=true)
     */
    private final JWTClaimsSet claimsSet;
    
    /**
     * Creates a successful validation result with claims
     */
    public static AccessTokenValidationResult success(JWTClaimsSet claimsSet) {
        return AccessTokenValidationResult.builder()
                .valid(true)
                .error(null)
                .errorCode(null)
                .claimsSet(claimsSet)
                .build();
    }
    
    /**
     * Creates a successful validation result
     */
    public static AccessTokenValidationResult success() {
        return AccessTokenValidationResult.builder()
                .valid(true)
                .error(null)
                .errorCode(null)
                .build();
    }
    
    /**
     * Creates a failed validation result with error details
     */
    public static AccessTokenValidationResult failure(String error, String errorCode) {
        return AccessTokenValidationResult.builder()
                .valid(false)
                .error(error)
                .errorCode(errorCode)
                .build();
    }
}
