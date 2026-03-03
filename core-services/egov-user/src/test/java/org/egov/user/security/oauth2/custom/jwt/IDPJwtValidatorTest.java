package org.egov.user.security.oauth2.custom.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.user.config.AuthProperties;
import org.egov.user.config.OidcProviderSupplier;
import org.egov.user.domain.exception.sso.IdpJwtValidationException;
import org.egov.user.domain.exception.sso.OidcProviderConfigException;
import org.egov.user.web.contract.auth.OidcValidatedJwt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IDPJwtValidatorTest {

        @Mock
        private AuthProperties authProperties;

        @Mock
        private OidcProviderSupplier oidcProviderSupplier;

        @Mock
        private ObjectMapper objectMapper;

        @Mock
        private JwtDecoder jwtDecoder;

        private IDPJwtValidator idpJwtValidator;

        @Before
        public void setup() {
                idpJwtValidator = new IDPJwtValidator(authProperties, oidcProviderSupplier);
        }

        @Test
        public void testSupports_Enabled() {
                AuthProperties.Oidc oidc = new AuthProperties.Oidc();
                oidc.setEnabled(true);
                when(authProperties.getOidc()).thenReturn(oidc);

                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setIssuerUri("any");
                when(oidcProviderSupplier.getProviders()).thenReturn(Collections.singletonList(provider));

                assertEquals(true, idpJwtValidator.supports("any"));
        }

        @Test
        public void testValidate_Success() {
                String token = "header.payload.signature";
                String issuer = "https://sts.windows.net/tenant-id/";

                String tenantId = "pb.amritsar";
                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setIssuerUri(issuer);
                provider.setJwkSetUri("http://jwks");
                provider.setRoleClaimKey("roles");
                provider.setTenantId(tenantId);
                Map<String, String> roleMapping = new HashMap<>();
                roleMapping.put("AZURE_ROLE_1", "DIGIT_ROLE_1");
                ReflectionTestUtils.setField(provider, "roleMapping", roleMapping);

                when(authProperties.getProviders()).thenReturn(Collections.singletonList(provider));
                when(oidcProviderSupplier.getProviders()).thenReturn(Collections.singletonList(provider));

                // Inject mocked decoder into the decoders map
                @SuppressWarnings("unchecked")
                Map<String, JwtDecoder> decoders = (Map<String, JwtDecoder>) ReflectionTestUtils.getField(
                                idpJwtValidator,
                                "decoders");
                decoders.put("azure", jwtDecoder);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", issuer);
                claims.put("sub", "user-guid");
                claims.put("roles", Collections.singletonList("AZURE_ROLE_1"));
                claims.put("tenantId", "pb.amritsar");
                claims.put("userType", "EMPLOYEE");

                Jwt jwt = new Jwt(token, Instant.now(), Instant.now().plusSeconds(3600),
                                Collections.singletonMap("alg", "RS256"), claims);

                // We need to bypass extractIssuerUnverified because it's hard to mock static
                // JWTParser
                // Instead of mocking static, let's use a token that has the issuer in its
                // payload if we can,
                // but extractIssuerUnverified uses JWTParser.parse(token).
                // Since I cannot mock static JWTParser easily without PowerMock,
                // and I don't want to use PowerMock, I will use a real-looking JWT token for
                // parsing if possible,
                // or I'll just accept that I might need to mock it.

                // Actually, JWTParser.parse(token) doesn't verify the signature.
                // I'll create a simple base64 encoded payload for the "issuer" field.
                String header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"; // {"alg":"HS256","typ":"JWT"}
                String payload = "eyJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC90ZW5hbnQtaWQvIn0"; // {"iss":"https://sts.windows.net/tenant-id/"}
                String realLookingToken = header + "." + payload + ".signature";

                when(jwtDecoder.decode(realLookingToken)).thenReturn(jwt);

                OidcValidatedJwt result = idpJwtValidator.validate(realLookingToken, tenantId);

                assertNotNull(result);
                assertEquals("azure", result.getProviderId());
                assertEquals(tenantId, result.getTenantId());
                assertEquals(1, result.getRoles().size());
                assertEquals("DIGIT_ROLE_1", result.getRoles().iterator().next());
        }

        @Test
        public void testExtractRoles_MultiRoleSupport() {
                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setRoleClaimKey("roles");

                Map<String, String> roleMapping = new HashMap<>();
                roleMapping.put("AZURE_ADMIN", "SYSTEM_ADMINISTRATOR,SUPERUSER");
                roleMapping.put("AZURE_USER", "CITIZEN");

                ReflectionTestUtils.setField(provider, "roleMapping", roleMapping);

                Map<String, Object> claims = new HashMap<>();
                claims.put("tenantId", "pb.amritsar");
                claims.put("roles", Collections.singletonList("AZURE_ADMIN"));

                Set<String> roles = (Set<String>) ReflectionTestUtils.invokeMethod(idpJwtValidator, "extractRoles",
                                provider, claims);

                assertEquals(2, roles.size());
                org.junit.Assert.assertTrue(roles.contains("SYSTEM_ADMINISTRATOR"));
                org.junit.Assert.assertTrue(roles.contains("SUPERUSER"));
        }

        @Test
        public void testValidate_InvalidToken_ParseFailed_HasCorrectErrorCode() {
                AuthProperties.Oidc oidc = new AuthProperties.Oidc();
                oidc.setEnabled(true);
                when(authProperties.getOidc()).thenReturn(oidc);
                when(oidcProviderSupplier.getProviders()).thenReturn(Collections.emptyList());

                try {
                        idpJwtValidator.validate("not-a-valid-jwt", "pb.amritsar");
                        org.junit.Assert.fail("Expected IdpJwtValidationException");
                } catch (IdpJwtValidationException e) {
                        assertEquals(SsoErrorCodes.JWT_PARSE_FAILED, e.getErrorCode());
                }
        }

        @Test
        public void testValidate_UnknownIssuer_HasCorrectErrorCode() {
                String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL3Vua25vd24uaXNzdWVyLmNvbS8ifQ.x";
                AuthProperties.Oidc oidc = new AuthProperties.Oidc();
                oidc.setEnabled(true);
                when(authProperties.getOidc()).thenReturn(oidc);
                when(oidcProviderSupplier.getProviders()).thenReturn(Collections.emptyList());

                try {
                        idpJwtValidator.validate(token, "pb.amritsar");
                        org.junit.Assert.fail("Expected OidcProviderConfigException");
                } catch (OidcProviderConfigException e) {
                        assertEquals(SsoErrorCodes.OIDC_PROVIDER_NOT_FOUND, e.getErrorCode());
                }
        }

        @Test
        public void testSupports_Disabled() {
                AuthProperties.Oidc oidc = new AuthProperties.Oidc();
                oidc.setEnabled(false);
                when(authProperties.getOidc()).thenReturn(oidc);

                assertEquals(false, idpJwtValidator.supports("any"));
        }

        @Test
        public void testValidate_ExpiredToken_ThrowsExpiredError() {
                String issuer = "https://sts.windows.net/tenant-id/";
                String tenantId = "pb.amritsar";
                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setIssuerUri(issuer);
                provider.setJwkSetUri("http://jwks");
                provider.setRoleClaimKey("roles");
                provider.setTenantId(tenantId);
                when(authProperties.getProviders()).thenReturn(Collections.singletonList(provider));
                when(oidcProviderSupplier.getProviders()).thenReturn(Collections.singletonList(provider));

                @SuppressWarnings("unchecked")
                Map<String, JwtDecoder> decoders = (Map<String, JwtDecoder>) ReflectionTestUtils.getField(
                                idpJwtValidator,
                                "decoders");
                decoders.put("azure", jwtDecoder);

                OAuth2Error error = new OAuth2Error("invalid_token", "Token expired", null);
                JwtValidationException expired = new JwtValidationException("expired", Collections.singletonList(error));

                String header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
                String payload = "eyJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC90ZW5hbnQtaWQvIn0";
                String realLookingToken = header + "." + payload + ".signature";

                when(jwtDecoder.decode(realLookingToken)).thenThrow(expired);

                try {
                        idpJwtValidator.validate(realLookingToken, tenantId);
                        org.junit.Assert.fail("Expected IdpJwtValidationException");
                } catch (IdpJwtValidationException e) {
                        assertEquals(SsoErrorCodes.JWT_EXPIRED, e.getErrorCode());
                }
        }

        @Test
        public void testValidate_AudienceValidationFails_ThrowsIdpJwtValidationException() {
            String issuer = "https://sts.windows.net/tenant-id/";
            String tenantId = "pb.amritsar";
            AuthProperties.Provider provider = new AuthProperties.Provider();
            provider.setId("azure");
            provider.setIssuerUri(issuer);
            provider.setJwkSetUri("http://jwks");
            provider.setRoleClaimKey("roles");
            provider.setTenantId(tenantId);
            when(authProperties.getProviders()).thenReturn(Collections.singletonList(provider));
            when(oidcProviderSupplier.getProviders()).thenReturn(Collections.singletonList(provider));

            @SuppressWarnings("unchecked")
            Map<String, JwtDecoder> decoders = (Map<String, JwtDecoder>) ReflectionTestUtils.getField(
                            idpJwtValidator,
                            "decoders");
            decoders.put("azure", jwtDecoder);

            OAuth2Error error = new OAuth2Error("invalid_token", "audience invalid", null);
            JwtValidationException invalidAud = new JwtValidationException("invalid audience", Collections.singletonList(error));

            String header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
            String payload = "eyJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC90ZW5hbnQtaWQvIn0";
            String realLookingToken = header + "." + payload + ".signature";

            when(jwtDecoder.decode(realLookingToken)).thenThrow(invalidAud);

            try {
                    idpJwtValidator.validate(realLookingToken, tenantId);
                    org.junit.Assert.fail("Expected IdpJwtValidationException");
            } catch (IdpJwtValidationException e) {
                    // Only type assertion; specific error code is not required here
                    assertEquals(SsoErrorCodes.JWT_INVALID, e.getErrorCode());
            }
        }

        @Test
        public void testExtractRoles_UnmappedRole_FallsBackToDefaultRoleCodes() {
                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setRoleClaimKey("roles");
                provider.setDefaultRoleCodes("DEFAULT_ROLE");

                Map<String, String> roleMapping = new HashMap<>();
                roleMapping.put("AZURE_ROLE", "DIGIT_ROLE");
                ReflectionTestUtils.setField(provider, "roleMapping", roleMapping);

                Map<String, Object> claims = new HashMap<>();
                claims.put("tenantId", "pb.amritsar");
                claims.put("roles", Collections.singletonList("UNMAPPED_ROLE"));

                @SuppressWarnings("unchecked")
                Set<String> roles = (Set<String>) ReflectionTestUtils.invokeMethod(idpJwtValidator, "extractRoles",
                                provider, claims);

                assertEquals(1, roles.size());
                org.junit.Assert.assertTrue(roles.contains("DEFAULT_ROLE"));
        }

        @Test
        public void testExtractRoles_NoRoleClaim_FallsBackToDefaultRoleCodes() {
                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setRoleClaimKey("roles");
                provider.setDefaultRoleCodes("DEFAULT_ROLE");

                Map<String, Object> claims = new HashMap<>();
                claims.put("tenantId", "pb.amritsar");

                @SuppressWarnings("unchecked")
                Set<String> roles = (Set<String>) ReflectionTestUtils.invokeMethod(idpJwtValidator, "extractRoles",
                                provider, claims);

                assertEquals(1, roles.size());
                org.junit.Assert.assertTrue(roles.contains("DEFAULT_ROLE"));
        }

        @Test
        public void testValidate_UserTypeFallback_UsesProviderDefault() {
                String token = "header.payload.signature";
                String issuer = "https://sts.windows.net/tenant-id/";

                String tenantId = "pb.amritsar";
                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setIssuerUri(issuer);
                provider.setJwkSetUri("http://jwks");
                provider.setRoleClaimKey("roles");
                provider.setTenantId(tenantId);
                provider.setUserType("EMPLOYEE");
                Map<String, String> roleMapping = new HashMap<>();
                roleMapping.put("AZURE_ROLE_1", "DIGIT_ROLE_1");
                ReflectionTestUtils.setField(provider, "roleMapping", roleMapping);

                when(authProperties.getProviders()).thenReturn(Collections.singletonList(provider));
                when(oidcProviderSupplier.getProviders()).thenReturn(Collections.singletonList(provider));

                @SuppressWarnings("unchecked")
                Map<String, JwtDecoder> decoders = (Map<String, JwtDecoder>) ReflectionTestUtils.getField(
                                idpJwtValidator,
                                "decoders");
                decoders.put("azure", jwtDecoder);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", issuer);
                claims.put("sub", "user-guid");
                claims.put("roles", Collections.singletonList("AZURE_ROLE_1"));
                claims.put("tenantId", tenantId);

                Jwt jwt = new Jwt(token, Instant.now(), Instant.now().plusSeconds(3600),
                                Collections.singletonMap("alg", "RS256"), claims);

                String header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"; // {"alg":"HS256","typ":"JWT"}
                String payload = "eyJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC90ZW5hbnQtaWQvIn0"; // {"iss":"https://sts.windows.net/tenant-id/"}
                String realLookingToken = header + "." + payload + ".signature";

                when(jwtDecoder.decode(realLookingToken)).thenReturn(jwt);

                OidcValidatedJwt result = idpJwtValidator.validate(realLookingToken, tenantId);

                assertNotNull(result);
                assertEquals("EMPLOYEE", result.getUserType());
        }

        @Test
        public void testExtractRoles_RoleClaimNotList_FallsBackToDefaultRoleCodes() {
                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setRoleClaimKey("roles");
                provider.setDefaultRoleCodes("DEFAULT_ROLE");

                Map<String, String> roleMapping = new HashMap<>();
                roleMapping.put("AZURE_ROLE", "DIGIT_ROLE");
                ReflectionTestUtils.setField(provider, "roleMapping", roleMapping);

                Map<String, Object> claims = new HashMap<>();
                claims.put("tenantId", "pb.amritsar");
                claims.put("roles", "not-a-list");

                @SuppressWarnings("unchecked")
                Set<String> roles = (Set<String>) ReflectionTestUtils.invokeMethod(idpJwtValidator, "extractRoles",
                                provider, claims);

                assertEquals(1, roles.size());
                assertTrue(roles.contains("DEFAULT_ROLE"));
        }

        @Test
        public void testValidate_JwksUriMissing_ThrowsOidcProviderConfigExceptionWithCorrectErrorCode() {
                String issuer = "https://sts.windows.net/tenant-id/";
                String tenantId = "pb.amritsar";

                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setIssuerUri(issuer);
                provider.setJwkSetUri("");
                provider.setRoleClaimKey("roles");
                provider.setTenantId(tenantId);
                when(authProperties.getProviders()).thenReturn(Collections.singletonList(provider));
                when(oidcProviderSupplier.getProviders()).thenReturn(Collections.singletonList(provider));

                String header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
                String payload = "eyJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC90ZW5hbnQtaWQvIn0";
                String realLookingToken = header + "." + payload + ".signature";

                try {
                        idpJwtValidator.validate(realLookingToken, tenantId);
                        org.junit.Assert.fail("Expected OidcProviderConfigException");
                } catch (OidcProviderConfigException e) {
                        assertEquals(SsoErrorCodes.OIDC_JWKS_MISSING, e.getErrorCode());
                }
        }

        @Test
        public void testValidate_MultipleProvidersSameIssuer_DisambiguatedByAudience_Success() throws Exception {
                String issuer = "https://sts.windows.net/tenant-id/";
                String tenantId = "pb.amritsar";

                AuthProperties.Provider provider1 = new AuthProperties.Provider();
                provider1.setId("azure1");
                provider1.setIssuerUri(issuer);
                provider1.setJwkSetUri("http://jwks1");
                provider1.setRoleClaimKey("roles");
                provider1.setTenantId(tenantId);
                provider1.setAudiences(Collections.singletonList("aud1"));

                AuthProperties.Provider provider2 = new AuthProperties.Provider();
                provider2.setId("azure2");
                provider2.setIssuerUri(issuer);
                provider2.setJwkSetUri("http://jwks2");
                provider2.setRoleClaimKey("roles");
                provider2.setTenantId(tenantId);
                provider2.setAudiences(Collections.singletonList("aud2"));

                List<AuthProperties.Provider> providers = Arrays.asList(provider1, provider2);
                when(authProperties.getProviders()).thenReturn(providers);
                when(oidcProviderSupplier.getProviders()).thenReturn(providers);

                @SuppressWarnings("unchecked")
                Map<String, JwtDecoder> decoders = (Map<String, JwtDecoder>) ReflectionTestUtils.getField(
                                idpJwtValidator, "decoders");
                decoders.put("azure1", jwtDecoder);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", issuer);
                claims.put("sub", "user-guid");
                claims.put("roles", Collections.singletonList("ROLE"));
                claims.put("tenantId", tenantId);
                claims.put("userType", "EMPLOYEE");

                Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600),
                                Collections.singletonMap("alg", "RS256"), claims);

                String payloadJson = "{\"iss\":\"https://sts.windows.net/tenant-id/\",\"aud\":[\"aud1\"]}";
                String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
                String header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
                String tokenWithAud = header + "." + payloadB64 + ".sig";

                when(jwtDecoder.decode(tokenWithAud)).thenReturn(jwt);

                OidcValidatedJwt result = idpJwtValidator.validate(tokenWithAud, tenantId);

                assertNotNull(result);
                assertEquals("azure1", result.getProviderId());
        }

        @Test
        public void testValidate_MultipleProvidersSameIssuer_NoAudienceInToken_ThrowsProviderAmbiguous() throws Exception {
                String issuer = "https://sts.windows.net/tenant-id/";
                String tenantId = "pb.amritsar";

                AuthProperties.Provider provider1 = new AuthProperties.Provider();
                provider1.setId("azure1");
                provider1.setIssuerUri(issuer);
                provider1.setTenantId(tenantId);
                provider1.setAudiences(Collections.singletonList("aud1"));

                AuthProperties.Provider provider2 = new AuthProperties.Provider();
                provider2.setId("azure2");
                provider2.setIssuerUri(issuer);
                provider2.setTenantId(tenantId);
                provider2.setAudiences(Collections.singletonList("aud2"));

                List<AuthProperties.Provider> providers = Arrays.asList(provider1, provider2);
                when(authProperties.getProviders()).thenReturn(providers);
                when(oidcProviderSupplier.getProviders()).thenReturn(providers);

                String header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
                String payload = "eyJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC90ZW5hbnQtaWQvIn0";
                String tokenNoAud = header + "." + payload + ".signature";

                try {
                        idpJwtValidator.validate(tokenNoAud, tenantId);
                        org.junit.Assert.fail("Expected OidcProviderConfigException");
                } catch (OidcProviderConfigException e) {
                        assertEquals(SsoErrorCodes.OIDC_PROVIDER_AMBIGUOUS, e.getErrorCode());
                }
        }

        @Test
        public void testValidate_IssuerWithTrailingSlash_MatchesProviderWithoutSlash() {
                String issuerWithSlash = "https://sts.windows.net/tenant-id/";
                String issuerNoSlash = "https://sts.windows.net/tenant-id";
                String tenantId = "pb.amritsar";

                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setIssuerUri(issuerNoSlash);
                provider.setJwkSetUri("http://jwks");
                provider.setRoleClaimKey("roles");
                provider.setTenantId(tenantId);
                Map<String, String> roleMapping = new HashMap<>();
                roleMapping.put("AZURE_ROLE_1", "DIGIT_ROLE_1");
                ReflectionTestUtils.setField(provider, "roleMapping", roleMapping);

                when(authProperties.getProviders()).thenReturn(Collections.singletonList(provider));
                when(oidcProviderSupplier.getProviders()).thenReturn(Collections.singletonList(provider));

                @SuppressWarnings("unchecked")
                Map<String, JwtDecoder> decoders = (Map<String, JwtDecoder>) ReflectionTestUtils.getField(
                                idpJwtValidator, "decoders");
                decoders.put("azure", jwtDecoder);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", issuerWithSlash);
                claims.put("sub", "user-guid");
                claims.put("roles", Collections.singletonList("AZURE_ROLE_1"));
                claims.put("tenantId", tenantId);
                claims.put("userType", "EMPLOYEE");

                Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600),
                                Collections.singletonMap("alg", "RS256"), claims);

                String header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
                String payload = "eyJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC90ZW5hbnQtaWQvIn0";
                String realLookingToken = header + "." + payload + ".signature";

                when(jwtDecoder.decode(realLookingToken)).thenReturn(jwt);

                OidcValidatedJwt result = idpJwtValidator.validate(realLookingToken, tenantId);

                assertNotNull(result);
                assertEquals("azure", result.getProviderId());
                assertEquals(tenantId, result.getTenantId());
        }

        @Test
        public void testExtractRoles_MultipleDefaultRoleCodes_CommaDelimited() {
                AuthProperties.Provider provider = new AuthProperties.Provider();
                provider.setId("azure");
                provider.setRoleClaimKey("roles");
                provider.setDefaultRoleCodes("ROLE_A, ROLE_B");

                Map<String, Object> claims = new HashMap<>();
                claims.put("tenantId", "pb.amritsar");

                @SuppressWarnings("unchecked")
                Set<String> roles = (Set<String>) ReflectionTestUtils.invokeMethod(idpJwtValidator, "extractRoles",
                                provider, claims);

                assertEquals(2, roles.size());
                assertTrue(roles.contains("ROLE_A"));
                assertTrue(roles.contains("ROLE_B"));
        }
}
