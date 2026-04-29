package org.egov.internalgatewayscg.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.internalgatewayscg.utils.CustomException;
import org.egov.internalgatewayscg.utils.RoutingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import org.springframework.util.ObjectUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RequestRouteFilter extends RouteToRequestUrlFilter {

    private static final String TENANT_ID_KEY = "tenantId";

    @Autowired
    private RoutingConfig routingConfig;

    @Autowired
    private ObjectMapper objectMapper;

    private record ResolvedContext(String tenantId, ServerWebExchange exchange) {}

    // -------------------------------------------------------------------------
    // Filter entry point — resolution and routing are independent steps
    // -------------------------------------------------------------------------

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestURI = exchange.getRequest().getURI().getPath();
        String existingCorrelationId = exchange.getRequest().getHeaders().getFirst("x-correlation-id");
        String correlationId = (existingCorrelationId != null && !existingCorrelationId.isBlank())
                ? existingCorrelationId : UUID.randomUUID().toString();

        return resolveTenantId(exchange)
                .flatMap(ctx -> {
                    if (ObjectUtils.isEmpty(ctx.tenantId())) {
                        throw new CustomException(HttpStatus.BAD_REQUEST, "TenantId is mandatory");
                    }
                    if (exchange.getRequest().getHeaders().getFirst(TENANT_ID_KEY) == null) {
                        log.debug("tenantId absent in request header, resolved from body/query-params: {}", ctx.tenantId());
                    }
                    MDC.put("TENANTID", ctx.tenantId());
                    MDC.put("CORRELATION_ID", correlationId);
                    ServerWebExchange enriched = ctx.exchange().mutate()
                            .request(r -> r.headers(h -> {
                                h.set(TENANT_ID_KEY, ctx.tenantId());
                                h.set("x-correlation-id", correlationId);
                            }))
                            .build();
                    return performRouting(enriched, chain, requestURI, ctx.tenantId());
                });
    }
    // MDC note: "HTTP POST" is logged by HttpWebHandlerAdapter before any filter runs,
    // so it will show the previous request's TENANTID/CORRELATION_ID on a reused Netty thread.
    // Accepted trade-off: doFinally fires before HttpWebHandlerAdapter.doOnSuccess ("Completed" log),
    // so clearing there would empty "Completed". Setting MDC here gives correct "Routing" + "Completed".

    // -------------------------------------------------------------------------
    // Tenant ID resolution — header → body → query params, returns Mono<ResolvedContext>
    // -------------------------------------------------------------------------

    private Mono<ResolvedContext> resolveTenantId(ServerWebExchange exchange) {
        String headerTenantId = exchange.getRequest().getHeaders().getFirst(TENANT_ID_KEY);
        if (!ObjectUtils.isEmpty(headerTenantId)) {
            return Mono.just(new ResolvedContext(headerTenantId, exchange));
        }

        HttpMethod method = exchange.getRequest().getMethod();
        String contentType = getContentType(exchange);

        if (isBodyReadable(method, contentType)) {
            if (contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
                return resolveTenantIdFromRawBody(exchange, this::extractTenantIdFromJson);
            }
            if (contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
                return resolveTenantIdFromFormData(exchange);
            }
            if (contentType.contains(MediaType.MULTIPART_FORM_DATA_VALUE)) {
                // Use original (non-lowercased) Content-Type — boundary values are case-sensitive
                String originalContentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
                String boundary = extractMultipartBoundary(originalContentType);
                if (boundary != null) {
                    return resolveTenantIdFromRawBody(exchange, bytes -> extractTenantIdFromMultipart(bytes, boundary));
                }
            }
        }

        return Mono.just(new ResolvedContext(queryParamTenantId(exchange), exchange));
    }

    /**
     * Reads the full body into a byte[], applies the extractor, then restores the body
     * via ServerHttpRequestDecorator so downstream services receive it intact.
     */
    private Mono<ResolvedContext> resolveTenantIdFromRawBody(ServerWebExchange exchange,
                                                              java.util.function.Function<byte[], String> extractor) {
        DataBuffer emptyMarker = exchange.getResponse().bufferFactory().wrap(new byte[0]);

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(emptyMarker)
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String tenantId = bytes.length > 0 ? extractor.apply(bytes) : null;

                    // Restore body for downstream since the stream is consumed
                    DataBuffer restoredBuffer = exchange.getResponse().bufferFactory().wrap(bytes);
                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return bytes.length > 0 ? Flux.just(restoredBuffer) : Flux.empty();
                        }
                    };
                    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

                    String resolved = !ObjectUtils.isEmpty(tenantId) ? tenantId : queryParamTenantId(mutatedExchange);
                    return new ResolvedContext(resolved, mutatedExchange);
                });
    }

    private Mono<ResolvedContext> resolveTenantIdFromFormData(ServerWebExchange exchange) {
        return exchange.getFormData()
                .map(formData -> {
                    String tenantId = formData.getFirst(TENANT_ID_KEY);
                    String resolved = !ObjectUtils.isEmpty(tenantId) ? tenantId : queryParamTenantId(exchange);
                    return new ResolvedContext(resolved, exchange);
                });
    }

private String queryParamTenantId(ServerWebExchange exchange) {
        return exchange.getRequest().getQueryParams().getFirst(TENANT_ID_KEY);
    }

    // -------------------------------------------------------------------------
    // Routing — rewrites the gateway URL attribute and passes to chain
    // -------------------------------------------------------------------------

    private Mono<Void> performRouting(ServerWebExchange exchange, GatewayFilterChain chain,
                                      String requestURI, String tenantId) {
        URI uri = exchange.getRequest().getURI();
        String incomingBase = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
        String requestPath = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        String method = exchange.getRequest().getMethod().name();

        boolean routeMatched = false;
        for (Map.Entry<String, Map<String, String>> entry : routingConfig.getTeanantRoutingConfigWrapper().entrySet()) {
            if (requestURI.matches(entry.getKey())) {
                routeMatched = true;
                String routingHost = findRoutingHost(entry.getValue(), tenantId);
                if (routingHost == null) {
                    log.error("Routing failed | tenantId={} | {} {} from: {} to: unknown | reason=no routing host configured",
                            tenantId, method, requestPath, incomingBase);
                    throw new CustomException(HttpStatus.BAD_GATEWAY,
                            "No routing configuration found for tenantId: " + tenantId);
                }
                boolean encoded = ServerWebExchangeUtils.containsEncodedParts(uri);
                try {
                    URI routeUri = new URI(routingHost);
                    URI mergedUrl = UriComponentsBuilder.fromUri(uri)
                            .scheme(routeUri.getScheme())
                            .host(routeUri.getHost())
                            .port(routeUri.getPort())
                            .build(encoded)
                            .toUri();
                    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, mergedUrl);
                    log.info("Routing | tenantId={} | {} {} from: {} to: {}",
                            tenantId, method, requestPath, incomingBase, routingHost);
                } catch (URISyntaxException e) {
                    log.error("Routing failed | tenantId={} | {} {} from: {} to: {} | reason=invalid host config",
                            tenantId, method, requestPath, incomingBase, routingHost);
                    throw new CustomException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Invalid routing URI configured for tenantId: " + tenantId, e);
                }
                break;
            }
        }
        if (!routeMatched) {
            log.error("Routing failed | tenantId={} | {} {} from: {} to: unknown | reason=no route pattern matched",
                    tenantId, method, requestPath, incomingBase);
            throw new CustomException(HttpStatus.NOT_FOUND, "No route configured for URI: " + requestURI);
        }
        return chain.filter(exchange);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractTenantIdFromJson(byte[] bytes) {
        try {
            JsonNode root = objectMapper.readTree(bytes);
            List<JsonNode> nodes = root.findValues(TENANT_ID_KEY);
            for (JsonNode node : nodes) {
                if (node.isTextual() && !node.asText().equalsIgnoreCase("null") && !node.asText().isBlank()) {
                    return node.asText();
                }
                if (node.isArray()) {
                    for (JsonNode element : node) {
                        if (element.isTextual() && !element.asText().equalsIgnoreCase("null") && !element.asText().isBlank()) {
                            return element.asText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse request body for tenantId extraction: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Parses tenantId from raw multipart bytes without Spring's multipart parser,
     * so the byte array can be fully restored for downstream forwarding.
     * Uses ISO-8859-1 for safe 1:1 byte↔char mapping over binary file data.
     */
    private String extractTenantIdFromMultipart(byte[] bytes, String boundary) {
        try {
            String body = new String(bytes, StandardCharsets.ISO_8859_1);
            String[] parts = body.split(Pattern.quote("--" + boundary));
            for (String part : parts) {
                if (part.contains("name=\"" + TENANT_ID_KEY + "\"")
                        || part.contains("name='" + TENANT_ID_KEY + "'")) {
                    int valueStart = part.indexOf("\r\n\r\n");
                    if (valueStart != -1) {
                        String value = part.substring(valueStart + 4);
                        // Cut at first CR or LF — robust against endsWith misses and binary data bleed
                        int lineEnd = value.indexOf('\r');
                        if (lineEnd == -1) lineEnd = value.indexOf('\n');
                        if (lineEnd != -1) value = value.substring(0, lineEnd);
                        value = value.trim();
                        if (!value.isEmpty() && !value.equalsIgnoreCase("null")) {
                            return value;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse multipart body for tenantId extraction: {}", e.getMessage());
        }
        return null;
    }

    private String extractMultipartBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                return trimmed.substring("boundary=".length()).trim();
            }
        }
        return null;
    }

    private boolean isBodyReadable(HttpMethod method, String contentType) {
        return (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.PATCH.equals(method))
                && (contentType.contains(MediaType.APPLICATION_JSON_VALUE)
                || contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                || contentType.contains(MediaType.MULTIPART_FORM_DATA_VALUE));
    }

    private String getContentType(ServerWebExchange exchange) {
        String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
        return contentType != null ? contentType.toLowerCase() : "";
    }

    private String findRoutingHost(Map<String, String> tenantRoutingMap, String reqTenantId) {
        String tmpTenantId = reqTenantId;
        while (!tmpTenantId.isEmpty()) {
            if (tenantRoutingMap.containsKey(tmpTenantId)) {
                return tenantRoutingMap.get(tmpTenantId);
            }
            int dotIndex = tmpTenantId.lastIndexOf(".");
            if (dotIndex == -1) {
                break;
            }
            tmpTenantId = tmpTenantId.substring(0, dotIndex);
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
