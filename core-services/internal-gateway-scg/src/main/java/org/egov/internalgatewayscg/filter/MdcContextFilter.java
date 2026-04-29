package org.egov.internalgatewayscg.filter;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class MdcContextFilter implements WebFilter, Ordered {

    private static final String TENANT_ID_KEY = "tenantId";
    private static final String CORRELATION_ID_HEADER = "x-correlation-id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String tenantId = exchange.getRequest().getQueryParams().getFirst(TENANT_ID_KEY);
        if (tenantId == null) {
            tenantId = exchange.getRequest().getHeaders().getFirst(TENANT_ID_KEY);
        }

        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        if (tenantId != null) {
            MDC.put("TENANTID", tenantId);
        }
        MDC.put("CORRELATION_ID", correlationId);

        final String finalCorrelationId = correlationId;
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.header(CORRELATION_ID_HEADER, finalCorrelationId))
                .build();

        return chain.filter(mutated)
                .doFinally(signal -> MDC.clear());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
