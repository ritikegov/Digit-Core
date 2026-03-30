package com.example.tradelicense.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Configuration
public class SecurityConfig {

    /** Map of issuer -> AuthenticationManager (each with its own JwtDecoder) */
    private final Map<String, AuthenticationManager> managersByIssuer = new ConcurrentHashMap<>();

    /** Optional: base URL for Keycloak realm mapping */
    private static final String KEYCLOAK_BASE = "https://digit-lts.digit.org/keycloak/realms/";

    /** Extract JWT token */
    private static String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null) return null;
        if (auth.startsWith("Bearer ")) return auth.substring(7);
        if (auth.startsWith("bearer ")) return auth.substring(7);
        return null;
    }

    /** Decode JWT payload (without validating) to read issuer */
    private static Map<String, Object> unsafeDecodePayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return Map.of();

            String payloadJson =
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            return new ObjectMapper().readValue(payloadJson, Map.class);

        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Convert realm roles to Spring authorities */
    private Converter<Jwt, Collection<? extends GrantedAuthority>> realmRolesConverter() {

        return jwt -> {

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");

            if (realmAccess == null) return List.of();

            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());

            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        };
    }

    /** Combine scope and realm roles */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            Collection<? extends GrantedAuthority> scopeAuthorities =
                    scopeConverter.convert(jwt);

            Collection<? extends GrantedAuthority> realmAuthorities =
                    realmRolesConverter().convert(jwt);

            return Stream.concat(
                    scopeAuthorities == null ? Stream.empty() : scopeAuthorities.stream(),
                    realmAuthorities == null ? Stream.empty() : realmAuthorities.stream()
            ).toList();
        });

        return converter;
    }

    /** Dynamic issuer resolver */
    @Bean
    public AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver() {

        return request -> {

            String token = resolveToken(request);

            if (token == null) {
                return authentication -> {
                    throw new org.springframework.security.core.AuthenticationException("Missing token") {};
                };
            }

            Map<String, Object> payload = unsafeDecodePayload(token);

            String issuer = (String) payload.get("iss");

            if (issuer == null || issuer.isBlank()) {
                return authentication -> {
                    throw new org.springframework.security.core.AuthenticationException("Missing issuer") {};
                };
            }

            return managersByIssuer.computeIfAbsent(issuer, this::buildAuthManagerForIssuer);
        };
    }

    private AuthenticationManager buildAuthManagerForIssuer(String issuer) {

        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);

        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);

        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter());

        return provider::authenticate;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManagerResolver<HttpServletRequest> resolver
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/tradelicense/**").permitAll()  // Allow all for testing
                        .requestMatchers("/trade-license/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationManagerResolver(resolver)
                );

        return http.build();
    }
}