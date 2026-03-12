package org.egov.user.web.controller;

import org.egov.common.contract.response.Error;
import org.egov.common.contract.response.ErrorResponse;
import org.egov.common.contract.response.ResponseInfo;
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

    @PostMapping("/_clear")
    public ResponseInfo clearAllDecoders() {
        idpJwtValidator.clearDecoderCache();
        return new ResponseInfo("", "", System.currentTimeMillis(), "", "",
                "SSO decoder cache cleared successfully");
    }

    @PostMapping("/{providerId}/_clear")
    public ResponseInfo clearDecoderForProvider(@PathVariable("providerId") String providerId) {
        idpJwtValidator.clearDecoderForProvider(providerId);
        return new ResponseInfo("", "", System.currentTimeMillis(), "", "",
                "SSO decoder cache cleared successfully for provider: " + providerId);
    }

}