package org.egov.tracer;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.tracer.model.Error;
import org.egov.tracer.model.ErrorRes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Collections;

/**
 * Global exception handler for {@link DataAccessException}.
 *
 * <p>Catches any unhandled {@code DataAccessException} that propagates to the
 * controller level (e.g., from repository/service layers when the database is
 * down, a query times out, or a constraint is violated) and returns a
 * standardised DIGIT error response with the {@code QUERY_EXECUTION_ERROR}
 * error code and HTTP 500 status.</p>
 *
 * <p>Uses {@code @Order(Ordered.HIGHEST_PRECEDENCE)} so that it takes priority
 * over {@link ExceptionAdvise}'s {@code @Order(Ordered.LOWEST_PRECEDENCE)}
 * generic {@code Exception.class} catch-all handler.</p>
 *
 * <p>Auto-discovered by {@link org.egov.tracer.config.TracerConfiguration}'s
 * {@code @ComponentScan(basePackages = {"org.egov.tracer"})}.</p>
 */
@ControllerAdvice
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataAccessExceptionHandler {

    private static final String ERROR_CODE = "QUERY_EXECUTION_ERROR";

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorRes> handleDataAccessException(DataAccessException ex) {
        log.error("DataAccessException caught at controller level", ex);

        Throwable rootCause = ex.getMostSpecificCause();
        String errorMessage = "Database query failed: "
                + (rootCause != null ? rootCause.getMessage() : ex.getMessage());

        Error error = new Error();
        error.setCode(ERROR_CODE);
        error.setMessage(errorMessage);
        error.setDescription(errorMessage);

        ErrorRes errorRes = new ErrorRes();
        errorRes.setResponseInfo(ResponseInfo.builder()
                .status("failed")
                .build());
        errorRes.setErrors(Collections.singletonList(error));

        return new ResponseEntity<>(errorRes, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
