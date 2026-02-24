package org.egov.user.web.adapters.errors;

import org.egov.common.contract.response.Error;
import org.egov.common.contract.response.ErrorField;
import org.egov.common.contract.response.ErrorResponse;
import org.egov.user.domain.exception.sso.SsoException;

import java.util.Collections;
import java.util.List;

/**
 * Adapts SSO/MFA domain exceptions to the standard ErrorResponse format for non-token endpoints.
 * Token endpoint errors are handled by CustomWebResponseExceptionTranslator.
 */
public class SsoExceptionErrorHandler implements ErrorAdapter<SsoException> {

    private static final String FIELD_ERROR_CODE = "errorCode";

    @Override
    public ErrorResponse adapt(SsoException ex) {
        Error error = Error.builder()
                .code(ex.getHttpStatus().value())
                .message(ex.getMessage())
                .description(ex.getCause() != null ? ex.getCause().getMessage() : null)
                .fields(getErrorFields(ex))
                .build();
        return new ErrorResponse(null, error);
    }

    private List<ErrorField> getErrorFields(SsoException ex) {
        return Collections.singletonList(
                ErrorField.builder()
                        .code(ex.getErrorCode())
                        .field(FIELD_ERROR_CODE)
                        .message(ex.getMessage())
                        .build()
        );
    }
}
