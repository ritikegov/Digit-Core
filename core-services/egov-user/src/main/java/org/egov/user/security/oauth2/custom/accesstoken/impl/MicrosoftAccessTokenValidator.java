package org.egov.user.security.oauth2.custom.accesstoken.impl;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.egov.user.config.AuthProperties;
import org.egov.user.config.OidcConfigConstants;
import org.egov.user.security.oauth2.custom.accesstoken.AccessTokenValidationResult;
import org.egov.user.security.oauth2.custom.accesstoken.AccessTokenValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Microsoft-specific access token validator.
 * Validates Microsoft Entra ID access tokens using JWKS endpoint.
 */
@Slf4j
@Component
public class MicrosoftAccessTokenValidator implements AccessTokenValidator {
    
    private final RestTemplate restTemplate;
    private final long jwksCacheTtlMs;
    
    /**
     * Atomic cache entry holding both JWKS set and timestamp to prevent race conditions
     */
    private static class CacheEntry {
        final ConcurrentHashMap<String, JWKSet> jwkSetCache;
        final long timestamp;
        
        CacheEntry(ConcurrentHashMap<String, JWKSet> jwkSetCache, long timestamp) {
            this.jwkSetCache = jwkSetCache;
            this.timestamp = timestamp;
        }
    }
    
    private static final AtomicReference<CacheEntry> cache = new AtomicReference<>(null);
    
    /**
     * Creates a cache key for tenant+URI combination
     */
    private static String cacheKey(String tenantId, String jwksUri) {
        String normalizedTenant = (tenantId != null && !tenantId.isEmpty()) ? tenantId : "default";
        return normalizedTenant + ":" + jwksUri;
    }
    
    @Autowired
    public MicrosoftAccessTokenValidator(RestTemplate restTemplate, AuthProperties authProperties) {
        this.restTemplate = restTemplate;
        Long configuredTtl = null;
        if (authProperties != null && authProperties.getOidc() != null) {
            configuredTtl = authProperties.getOidc().getJwksCacheTtlMs();
        }
        if (configuredTtl == null || configuredTtl <= 0L) {
            this.jwksCacheTtlMs = OidcConfigConstants.DEFAULT_JWKS_CACHE_TTL_MS;
        } else {
            this.jwksCacheTtlMs = configuredTtl;
        }
    }
    
    @Override
    public boolean supports(AuthProperties.Provider provider) {
        return OidcConfigConstants.PROVIDER_TYPE_MICROSOFT.equals(provider.getProviderType());
    }
    
    @Override
    public AccessTokenValidationResult validate(String accessToken, AuthProperties.Provider provider) {
        try {
            log.debug("Validating Microsoft access token for provider: {}", provider.getId());
            
            // Parse the JWT
            SignedJWT signedJWT = SignedJWT.parse(accessToken);
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            
            // 1. Validate token signature (simplified - in production, fetch from JWKS)
            if (!validateSignature(signedJWT, provider)) {
                return AccessTokenValidationResult.failure("Invalid token signature", "INVALID_SIGNATURE");
            }
            
            // 2. Validate expiration
            Date expirationTime = claimsSet.getExpirationTime();
            if (expirationTime != null && expirationTime.before(new Date())) {
                return AccessTokenValidationResult.failure("Token expired", "TOKEN_EXPIRED");
            }
            
            // 3. Validate not before time
            Date notBeforeTime = claimsSet.getNotBeforeTime();
            if (notBeforeTime != null && notBeforeTime.after(new Date())) {
                return AccessTokenValidationResult.failure("Token not yet valid", "TOKEN_NOT_YET_VALID");
            }
            
            // 4. Validate audience
            List<String> audiences = claimsSet.getAudience();
            if (audiences == null || audiences.isEmpty()) {
                return AccessTokenValidationResult.failure("Missing audience claim", "MISSING_AUDIENCE");
            }
            
            boolean validAudience = provider.getAudiences().stream()
                    .anyMatch(audiences::contains);
            if (!validAudience) {
                return AccessTokenValidationResult.failure("Invalid audience", "INVALID_AUDIENCE");
            }
            
            // 5. Validate issuer
            String issuer = claimsSet.getIssuer();
            if (issuer == null || !issuer.equals(provider.getIssuerUri())) {
                return AccessTokenValidationResult.failure("Invalid issuer", "INVALID_ISSUER");
            }
            
            log.debug("Microsoft access token validation successful for provider: {}", provider.getId());
            return AccessTokenValidationResult.success(claimsSet);
            
        } catch (Exception e) {
            log.error("Microsoft access token validation failed for provider: {}", provider.getId(), e);
            return AccessTokenValidationResult.failure("Token validation failed: " + e.getMessage(), "VALIDATION_ERROR");
        }
    }
    
    /**
     * Validates token signature using provider's JWKS endpoint.
     * Implements proper JWKS fetching, caching, and signature verification.
     */
    boolean validateSignature(SignedJWT signedJWT, AuthProperties.Provider provider) {
        try {
            // Implement proper JWKS fetching and signature verification
            String jwksUri = provider.getJwkSetUri();
            if (jwksUri == null || jwksUri.trim().isEmpty()) {
                log.error("JWKS URI not configured for provider: {}", provider.getId());
                return false;
            }
            
            // Get JWKS set with caching
            JWKSet jwkSet = getJwkSet(provider.getTenantId(), jwksUri);
            
            // Get the key ID from the JWT header
            String keyId = signedJWT.getHeader().getKeyID();
            
            // Find the matching key
            RSAKey rsaKey = null;
            if (keyId != null) {
                // Try to find key by ID
                rsaKey = (RSAKey) jwkSet.getKeyByKeyId(keyId);
            } else {
                // If no key ID, try the first RSA key
                for (com.nimbusds.jose.jwk.JWK key : jwkSet.getKeys()) {
                    if (key instanceof RSAKey) {
                        rsaKey = (RSAKey) key;
                        break;
                    }
                }
            }
            
            if (rsaKey == null) {
                log.error("No matching RSA key found for token signature verification. Key ID: {}", keyId);
                return false;
            }
            
            // Verify signature
            JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            boolean verified = signedJWT.verify(verifier);
            
            if (verified) {
                log.debug("Token signature verified successfully for provider: {}", provider.getId());
            } else {
                log.warn("Token signature verification failed for provider: {}", provider.getId());
            }
            
            return verified;
        } catch (Exception e) {
            log.error("Signature validation failed", e);
            return false;
        }
    }
    
    /**
     * Gets JWKS set with caching. Fetches from remote if cache is expired or not present.
     * Uses atomic operations to prevent race conditions.
     */
    private JWKSet getJwkSet(String tenantId, String jwksUri) {
        String cacheKey = cacheKey(tenantId, jwksUri);
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get();
        
        // Check if cache is valid and contains the URI
        if (entry != null && (now - entry.timestamp) < jwksCacheTtlMs) {
            JWKSet cached = entry.jwkSetCache.get(cacheKey);
            if (cached != null) {
                log.debug("Using cached JWKS for tenant: {}, URI: {}", tenantId, jwksUri);
                return cached;
            }
        }
        
        // Cache miss or expired - fetch fresh data
        try {
            log.debug("Fetching JWKS from remote URI: {}", jwksUri);
            
            // Fetch JWKS using RestTemplate
            String jwksJson = restTemplate.getForObject(jwksUri, String.class);
            if (jwksJson == null || jwksJson.trim().isEmpty()) {
                throw new RuntimeException("Empty response from JWKS URI: " + jwksUri);
            }
            
            // Parse JWKS
            JWKSet jwkSet = JWKSet.parse(jwksJson);
            
            // Update cache atomically
            CacheEntry newEntry;
            if (entry != null && (now - entry.timestamp) < jwksCacheTtlMs) {
                // Update existing cache entry
                ConcurrentHashMap<String, JWKSet> updatedCache = new ConcurrentHashMap<>(entry.jwkSetCache);
                updatedCache.put(cacheKey, jwkSet);
                newEntry = new CacheEntry(updatedCache, entry.timestamp);
            } else {
                // Create new cache entry
                ConcurrentHashMap<String, JWKSet> newCache = new ConcurrentHashMap<>();
                newCache.put(cacheKey, jwkSet);
                newEntry = new CacheEntry(newCache, now);
            }
            
            cache.set(newEntry);
            log.debug("JWKS fetched and cached successfully for tenant: {}, URI: {}", tenantId, jwksUri);
            return jwkSet;
            
        } catch (Exception e) {
            log.error("Failed to fetch JWKS from tenant: {}, URI: {}", tenantId, jwksUri, e);
            
            // Return cached version if available, even if expired
            if (entry != null) {
                JWKSet cached = entry.jwkSetCache.get(cacheKey);
                if (cached != null) {
                    log.warn("Using expired cached JWKS due to fetch failure for tenant: {}, URI: {}", tenantId, jwksUri);
                    return cached;
                }
            }
            
            // No cached version available - fail the validation
            throw new RuntimeException("Unable to fetch JWKS and no cached version available", e);
        }
    }
    
    /**
     * Clears JWKS cache for a specific tenant and JWKS URI.
     * 
     * @param tenantId the tenant ID (for logging purposes)
     * @param jwksUri the JWKS URI to clear
     * @return true if cache entry was removed, false if not found
     */
    public static boolean clearJwkCacheFor(String tenantId, String jwksUri) {
        if (jwksUri == null) {
            log.info("JWKS URI is null, cannot clear cache for tenant: {}", tenantId);
            return false;
        }
        
        String cacheKey = cacheKey(tenantId, jwksUri);
        CacheEntry entry = cache.get();
        if (entry != null) {
            JWKSet removed = entry.jwkSetCache.remove(cacheKey);
            if (removed != null) {
                log.info("JWKS cache cleared for tenant: {}, jwksUri: {}", tenantId, jwksUri);
                return true;
            }
        }
        log.info("No JWKS cache entry found for tenant: {}, jwksUri: {}", tenantId, jwksUri);
        return false;
    }
}
