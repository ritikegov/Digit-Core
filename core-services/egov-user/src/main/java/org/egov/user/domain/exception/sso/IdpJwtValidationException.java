package org.egov.user.domain.exception.sso;

import org.egov.user.security.oauth2.custom.jwt.SsoErrorCodes;
import org.springframework.http.HttpStatus;

/** Thrown when IdP JWT validation fails (parse, signature, issuer, expiry). */
public class IdpJwtValidationException extends SsoException {

    public IdpJwtValidationException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.UNAUTHORIZED);
    }

    public IdpJwtValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, HttpStatus.UNAUTHORIZED, cause);
    }

    public static IdpJwtValidationException parseFailed(Throwable cause) {
        return new IdpJwtValidationException(SsoErrorCodes.JWT_PARSE_FAILED,
                SsoErrorCodes.MSG_JWT_PARSE_FAILED, cause);
    }

    public static IdpJwtValidationException invalid(String detail, Throwable cause) {
        return new IdpJwtValidationException(SsoErrorCodes.JWT_INVALID,
                "JWT validation failed: " + detail, cause);
    }

    public static IdpJwtValidationException expired(String detail, Throwable cause) {
        return new IdpJwtValidationException(SsoErrorCodes.JWT_EXPIRED,
                "JWT token has expired: " + detail, cause);
    }

    public static IdpJwtValidationException invalid(Throwable cause) {
        return new IdpJwtValidationException(SsoErrorCodes.JWT_INVALID,
                SsoErrorCodes.MSG_JWT_INVALID, cause);
    }

    public static IdpJwtValidationException expired(Throwable cause) {
        return new IdpJwtValidationException(SsoErrorCodes.JWT_EXPIRED,
                SsoErrorCodes.MSG_JWT_EXPIRED, cause);
    }
}
