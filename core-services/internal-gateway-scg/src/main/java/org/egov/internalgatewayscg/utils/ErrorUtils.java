package org.egov.internalgatewayscg.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ErrorUtils {

    private static final ThreadLocal<ObjectMapper> om = ThreadLocal.withInitial(() -> {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    });

    public static Mono<Void> raiseErrorFilterException(ServerWebExchange exchange, Throwable e) {
        log.error("Gateway error for [{}] {}: {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getURI(), e.getMessage(), e);

        if (exchange.getResponse().isCommitted()) {
            return Mono.error(e);
        }

        HttpStatus status;
        String code;
        String message;

        if (e instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = status.name();
            message = rse.getReason() != null ? rse.getReason() : rse.getMessage();
        } else if (isConnectionError(e)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "SERVICE_UNAVAILABLE";
            message = "Downstream service is unreachable";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_GATEWAY_ERROR";
            message = e.getMessage();
        }

        try {
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory()
                            .wrap(om.get().writeValueAsBytes(errorBody(code, message)))));
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize error response", ex);
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

    private static boolean isConnectionError(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConnectException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private static Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }
}
