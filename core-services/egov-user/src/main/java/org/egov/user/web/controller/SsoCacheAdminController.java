package org.egov.user.web.controller;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.Error;
import org.egov.common.contract.response.ErrorResponse;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.user.security.oauth2.custom.accesstoken.impl.MicrosoftAccessTokenValidator;
import org.egov.user.security.oauth2.custom.jwt.IDPJwtValidator;
import org.egov.user.web.contract.SsoCacheClearRequest;
import org.egov.user.web.contract.SsoJwksCacheClearRequest;
import org.egov.user.web.contract.factory.ResponseInfoFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

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
    private final ResponseInfoFactory responseInfoFactory;

    public SsoCacheAdminController(IDPJwtValidator idpJwtValidator, ResponseInfoFactory responseInfoFactory) {
        this.idpJwtValidator = idpJwtValidator;
        this.responseInfoFactory = responseInfoFactory;
    }

    @PostMapping("/decoders/_clear")
    public ResponseInfo clearDecodersForTenantAndProvider(@RequestBody @Valid SsoCacheClearRequest request) {
        idpJwtValidator.clearDecoderCacheFor(request.getTenantId(), request.getProviderId());
        return responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true);
    }

    @PostMapping("/jwks/_clear")
    public ResponseInfo clearJwks(@RequestBody @Valid SsoJwksCacheClearRequest request) {
        MicrosoftAccessTokenValidator.clearJwkCacheFor(request.getTenantId(), request.getJwksUri());
        return responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(), true);
    }

}