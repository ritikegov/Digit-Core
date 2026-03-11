package org.egov.user.domain.exception.sso;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a JWT token replay is detected.
 * This prevents the same JWT from being used multiple times for OAuth2 token exchange.
 */
public class TokenReplayException extends SsoException {
    
    private static final String ERROR_CODE = "token_replay";
    private static final HttpStatus HTTP_STATUS = HttpStatus.UNAUTHORIZED;
    
    public TokenReplayException(String tokenId) {
        super(ERROR_CODE, 
            "Token replay detected: tokenId '" + tokenId + "' has already been used", 
            HTTP_STATUS);
    }
}
