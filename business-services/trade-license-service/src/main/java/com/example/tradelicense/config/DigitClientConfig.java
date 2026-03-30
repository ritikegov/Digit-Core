package com.example.tradelicense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Configuration for DIGIT client library to properly propagate JWT tokens.
 * Ensures JWT tokens from HTTP requests are automatically passed
 * to all DIGIT service calls (Registry, MDMS, Workflow, etc.).
 */
@Configuration
public class DigitClientConfig {

    private static final Pattern REALM_PATTERN = Pattern.compile(".*/realms/([^/]+)");

    /**
     * RestTemplate bean for Billing Service calls.
     * Named separately to avoid conflict with digit-client's restTemplate bean.
     */
    @Bean("billingRestTemplate")
    public RestTemplate billingRestTemplate() {
        return new RestTemplate();
    }

    /**
     * JWT Token Supplier for digit-client library.
     * Called automatically to get the current JWT token for DIGIT service calls.
     */
    @Bean
    @Primary
    public java.util.function.Supplier<String> jwtTokenSupplier() {
        return () -> {
            // Primary: Get JWT from Spring Security context
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                if (principal instanceof Jwt jwt) {
                    return jwt.getTokenValue();
                }
            }

            // Fallback: Get from HTTP request Authorization header
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
            }

            return null;
        };
    }

    /**
     * Tenant ID Supplier for digit-client library.
     * Provides the current tenant ID for multi-tenant operations.
     */
    @Bean
    public java.util.function.Supplier<String> tenantIdSupplier() {
        return () -> {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                if (principal instanceof Jwt jwt) {
                    return getTenantIdFromJwt(jwt);
                }
            }
            return null;
        };
    }

    private String getTenantIdFromJwt(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null) return null;

        String iss = jwt.getIssuer().toString();
        Matcher m = REALM_PATTERN.matcher(iss);
        if (m.matches()) return m.group(1);

        try {
            String path = java.net.URI.create(iss).getPath();
            Matcher m2 = REALM_PATTERN.matcher(path);
            if (m2.matches()) return m2.group(1);
        } catch (Exception ignored) {}

        return null;
    }
}
