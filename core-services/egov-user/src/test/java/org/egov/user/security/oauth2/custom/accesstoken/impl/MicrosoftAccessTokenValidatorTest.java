package org.egov.user.security.oauth2.custom.accesstoken.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.egov.user.config.AuthProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

public class MicrosoftAccessTokenValidatorTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AuthProperties authProperties;
    @Mock
    private AuthProperties.Oidc oidcProperties;
    private MicrosoftAccessTokenValidator validator;
    private AuthProperties.Provider provider;
    private RSAKey testRSAKey;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(authProperties.getOidc()).thenReturn(oidcProperties);
        when(oidcProperties.getJwksCacheTtlMs()).thenReturn(null);
        validator = new MicrosoftAccessTokenValidator(restTemplate, authProperties);
        
        // Generate test RSA key
        testRSAKey = new RSAKeyGenerator(2048).keyID("test-key-id").generate();
        
        // Setup provider using constructor
        provider = new AuthProperties.Provider(
                "microsoft",                                    // id
                "https://login.microsoftonline.com/common/v2.0", // issuerUri
                Collections.emptyList(),                        // issuerAliases
                "https://login.microsoftonline.com/common/discovery/v2.0/keys", // jwkSetUri
                Arrays.asList("api://test-client"),             // audiences
                "default-tenant",                               // tenantId
                "EMPLOYEE",                                     // userType
                null,                                           // defaultRoleCodes
                "roles",                                        // roleClaimKey
                Collections.emptyMap(),                         // roleMapping
                Collections.emptyMap(),                         // designationMapping
                null,                                           // defaultDesignationCode
                null,                                           // designationClaimKey
                null,                                           // defaultBoundaryHierarchyType
                null,                                           // defaultDob (Long)
                "ACTIVE",                                       // defaultEmployeeStatus
                null,                                           // rolePrefix
                "DECRYPTION",                                   // decryptionPurpose
                null,                                           // graphClientId
                null,                                           // graphTenantId
                null,                                           // graphMethodsUrl
                null,                                           // graphUsersUrl
                null,                                           // graphTokenUrl
                null,                                           // graphScope
                null,                                           // graphAppRoleAssignmentUrl
                "azure",                                        // graphServiceType
                null,                                           // graphAppResourceId
                "none",                                         // idpUserValidatorType
                "microsoft"                                     // providerType
        );
        
        // Mock RestTemplate to return JWKS with proper format
        String publicKeyJson = testRSAKey.toPublicJWK().toJSONString();
        String jwksJson = "{\"keys\":[" + publicKeyJson + "]}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jwksJson);
        
        // Clear cache to ensure clean test state
        MicrosoftAccessTokenValidator.clearJwkCacheFor("default-tenant", "https://login.microsoftonline.com/common/discovery/v2.0/keys");
    }
    
    @After
    public void tearDown() {
        // Cache is now managed per tenant/URI, no global clear needed
    }

    @Test
    public void validateSignature_ValidSignature_ReturnsTrue() throws Exception {
        // Create signed JWT
        SignedJWT signedJWT = createSignedJWT(testRSAKey);
        
        boolean result = validator.validateSignature(signedJWT, provider);
        
        assertTrue("Valid signature should pass verification", result);
    }

    @Test
    public void validateSignature_InvalidSignature_ReturnsFalse() throws Exception {
        // Create JWT with different key (invalid signature)
        RSAKey differentKey = new RSAKeyGenerator(2048).keyID("different-key").generate();
        SignedJWT signedJWT = createSignedJWT(differentKey);
        
        boolean result = validator.validateSignature(signedJWT, provider);
        
        assertFalse("Invalid signature should fail verification", result);
    }

    @Test
    public void validateSignature_JwksFetchFailure_UsesCachedKey() throws Exception {
        // First successful fetch to populate cache
        SignedJWT signedJWT = createSignedJWT(testRSAKey);
        boolean result1 = validator.validateSignature(signedJWT, provider);
        assertTrue("First validation should succeed", result1);
        
        // Mock fetch failure
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new RuntimeException("Network error"));
        
        // Second validation should use cached key
        boolean result2 = validator.validateSignature(signedJWT, provider);
        assertTrue("Second validation should use cached key and succeed", result2);
    }

    @Test
    public void validateSignature_RespectsShortTtl() throws Exception {
        // Use a very short TTL so that a second call forces refresh
        when(oidcProperties.getJwksCacheTtlMs()).thenReturn(1L);
        validator = new MicrosoftAccessTokenValidator(restTemplate, authProperties);

        SignedJWT signedJWT = createSignedJWT(testRSAKey);

        // First call populates cache
        boolean first = validator.validateSignature(signedJWT, provider);
        assertTrue(first);

        // After sleeping past TTL, a new RestTemplate call should be made
        Thread.sleep(5L);
        validator.validateSignature(signedJWT, provider);

        // Verify at least two invocations (initial + post-expiry)
        verify(restTemplate, atLeast(2)).getForObject(anyString(), eq(String.class));
    }

    private SignedJWT createSignedJWT(RSAKey signingKey) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer("https://login.microsoftonline.com/common/v2.0")
                .audience("api://test-client")
                .subject("test-user")
                .issueTime(java.util.Date.from(Instant.now()))
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(3600)))
                .claim("amr", Arrays.asList("pwd", "mfa"))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsSet);
        RSASSASigner signer = new RSASSASigner(signingKey);
        signedJWT.sign(signer);
        
        return signedJWT;
    }
}
