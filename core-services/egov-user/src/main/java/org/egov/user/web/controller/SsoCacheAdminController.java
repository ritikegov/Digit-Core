package org.egov.user.web.controller;

import org.egov.common.contract.response.Error;
import org.egov.common.contract.response.ErrorResponse;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.user.security.oauth2.custom.accesstoken.impl.MicrosoftAccessTokenValidator;
import org.egov.user.security.oauth2.custom.jwt.IDPJwtValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal admin endpoints for managing SSO decoder caches.
 *
 * <p>These endpoints are intended for operational use only (e.g. during key
 * rotation or incident response) and must be protected at the API gateway
 * or via dedicated security configuration. They SHOULD NOT be exposed to
 * untrusted callers.</p>
 */
@RestController
@RequestMapping("/sso")
public class SsoCacheAdminController {

    private final IDPJwtValidator idpJwtValidator;

    public SsoCacheAdminController(IDPJwtValidator idpJwtValidator) {
        this.idpJwtValidator = idpJwtValidator;
    }

    @PostMapping("/decoders/_clear")
    public ResponseInfo clearDecodersForTenantAndProvider(@RequestParam("tenantId") String tenantId,
                                                         @RequestParam(value = "providerId", required = false) String providerId) {
        idpJwtValidator.clearDecoderCacheFor(tenantId, providerId);
        if (providerId != null && !providerId.isEmpty()) {
            return new ResponseInfo("", "", System.currentTimeMillis(), "", "",
                    "SSO decoder cache cleared successfully for tenant: " + tenantId + ", provider: " + providerId);
        } else {
            return new ResponseInfo("", "", System.currentTimeMillis(), "", "",
                    "SSO decoder cache cleared successfully for tenant: " + tenantId);
        }
    }

    @PostMapping("/jwks/_clear")
    public ResponseInfo clearJwks(@RequestParam("tenantId") String tenantId,
                                  @RequestParam("jwksUri") String jwksUri) {
        boolean cleared = MicrosoftAccessTokenValidator.clearJwkCacheFor(tenantId, jwksUri);
        String message = cleared 
                ? "SSO JWKS cache cleared successfully for tenant: " + tenantId + ", jwksUri: " + jwksUri
                : "SSO JWKS cache entry not found for tenant: " + tenantId + ", jwksUri: " + jwksUri;
        return new ResponseInfo("", "", System.currentTimeMillis(), "", "", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleError(Exception ex) {
        ErrorResponse response = new ErrorResponse();
        ResponseInfo responseInfo = new ResponseInfo("", "", System.currentTimeMillis(), "", "", "SSO cache operation failed");
        response.setResponseInfo(responseInfo);
        Error error = new Error();
        error.setCode(400);
        error.setDescription("SSO cache operation failed");
        response.setError(error);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

}