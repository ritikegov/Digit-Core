package org.egov.user.security.oauth2.custom.jwt;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.user.domain.model.SecureUser;
import org.egov.user.domain.model.User;
import org.egov.user.domain.model.enums.UserType;
import org.egov.user.domain.service.UserService;
import org.egov.user.domain.service.utils.EncryptionDecryptionUtil;
import org.egov.user.config.AuthProperties;
import org.egov.user.config.OidcProviderSupplier;
import org.egov.user.config.SsoDefaultPasswordResolver;
import org.egov.user.security.oauth2.custom.service.EmployeeCreationProfile;
import org.egov.user.security.oauth2.custom.service.IdpGraphService;
import org.egov.user.security.oauth2.custom.service.impl.MsGraphService;
import org.egov.user.security.oauth2.custom.service.impl.NoOpGraphService;
import org.egov.user.utils.ProjectEmployeeStaffUtil;
import org.egov.user.web.contract.auth.OidcValidatedJwt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.user.domain.model.UserSearchCriteria;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.egov.user.domain.exception.sso.SsoException;
import org.egov.user.domain.exception.sso.SsoMissingParamException;
import org.egov.user.domain.exception.sso.SsoUserMappingException;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JwtExchangeAuthenticationProviderTest {

    private static final String TENANT_PB = "pb";

        @Mock
        private JwtValidationService jwtValidationService;

        @Mock
        private UserService userService;

        @Mock
        private MultiStateInstanceUtil multiStateInstanceUtil;

        @Mock
        private ProjectEmployeeStaffUtil projectEmployeeStaffUtil;

        @Mock
        private AuthProperties authProperties;

        @Mock
        private OidcProviderSupplier oidcProviderSupplier;

        @Mock
        private AuthProperties.Provider provider;

        @Mock
        private MsGraphService msGraphService;

        @Mock
        private SsoDefaultPasswordResolver ssoDefaultPasswordResolver;

        private AccessTokenMfaExtractor accessTokenMfaExtractor = new AccessTokenMfaExtractor(new ObjectMapper());

        private JwtExchangeAuthenticationProvider authenticationProvider;

        @Before
        public void setup() {
                List<IdpGraphService> graphServices = Collections.singletonList(msGraphService);
                authenticationProvider = new JwtExchangeAuthenticationProvider(
                                jwtValidationService, userService, multiStateInstanceUtil,
                                projectEmployeeStaffUtil, oidcProviderSupplier, graphServices,
                                accessTokenMfaExtractor, ssoDefaultPasswordResolver, new NoOpGraphService());
                when(authProperties.getProviders()).thenReturn(Collections.singletonList(provider));
                when(oidcProviderSupplier.getProviders()).thenReturn(Collections.singletonList(provider));
                when(provider.getId()).thenReturn("oidc-azure");
                when(provider.getIssuerUri()).thenReturn("issuer");
                when(ssoDefaultPasswordResolver.generatePassword()).thenReturn("eGov@123");
                when(provider.getDefaultDob()).thenReturn(1157328000000L);
                when(provider.getDefaultEmployeeStatus()).thenReturn("EMPLOYED");
                when(provider.getRolePrefix()).thenReturn("ROLE_");
                when(provider.getTenantId()).thenReturn(TENANT_PB);
                when(msGraphService.supports(any())).thenReturn(true);
                when(msGraphService.getEmployeeCreationProfile(any(), anyString())).thenReturn(Optional.empty());
        }

        private static OidcValidatedJwt oidcJwt(Map<String, Object> claims, String token) {
                return new OidcValidatedJwt(
                                Collections.singleton("ROLE"), claims, new Date(), new Date(), token, "oidc-azure");
        }

        @Test
        public void testAuthenticate_ExistingUser() {
                String token = "jwt-token";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, null, TENANT_PB);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");
                claims.put("name", "John Doe");
                claims.put("email", "john@example.com");
                claims.put("preferred_username", "john@example.com");

                OidcValidatedJwt jwt = oidcJwt(claims, token);

                User user = User.builder().uuid("uuid").type(UserType.EMPLOYEE).active(true).password("password")
                                .tenantId(TENANT_PB)
                                .roles(Collections.emptySet()).build();

                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any())).thenReturn(user);
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(user);

                Authentication result = authenticationProvider.authenticate(authenticationToken);

                assertNotNull(result);
                assertTrue(result instanceof UsernamePasswordAuthenticationToken);
                SecureUser secureUser = (SecureUser) result.getPrincipal();
                assertEquals("uuid", secureUser.getUser().getUuid());

                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                verify(userService).updateWithoutOtpValidation(userCaptor.capture(), any());
                User updatedUser = userCaptor.getValue();
                assertEquals("John Doe", updatedUser.getName());
                assertEquals("john@example.com", updatedUser.getEmailId());
                assertEquals(1, updatedUser.getRoles().size());
                assertEquals("ROLE", updatedUser.getRoles().iterator().next().getCode());
        }

        @Test
        public void testAuthenticate_NewUserCreation() {
                String token = "jwt-token";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, null, TENANT_PB);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");
                claims.put("name", "John Doe");
                claims.put("preferred_username", "johndoe");
                claims.put("email", "john@example.com");

                OidcValidatedJwt jwt = oidcJwt(claims, token);

                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any()))
                                .thenThrow(new org.egov.user.domain.exception.UserNotFoundException(
                                                new UserSearchCriteria()));

                org.egov.user.domain.model.hrms.User hrmsUser = org.egov.user.domain.model.hrms.User.builder()
                                .userServiceUuid("new-uuid")
                                .userName("johndoe")
                                .name("John Doe")
                                .roles(Collections.emptyList())
                                .tenantId(TENANT_PB)
                                .build();

                when(projectEmployeeStaffUtil.createEmployeeAndProjectStaff(
                                any(org.egov.user.domain.model.hrms.User.class), anyString(), anyString(), anyString(),
                                anyString(), anyString(), anyString(), any(RequestInfo.class)))
                                .thenReturn(hrmsUser);

                User createdUser = User.builder().uuid("new-uuid").username("johndoe").type(UserType.EMPLOYEE)
                                .active(true)
                                .password("password").roles(Collections.emptySet()).build();
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(createdUser);

                Authentication result = authenticationProvider.authenticate(authenticationToken);

                assertNotNull(result);
                verify(projectEmployeeStaffUtil).createEmployeeAndProjectStaff(
                                any(org.egov.user.domain.model.hrms.User.class), eq("PERMANENT"),
                                eq("1f3572c4-07ce-4d58-86d3-7b6e2458e812"), eq("NMCP"), eq("EMPLOYED"), eq(TENANT_PB),
                                anyString(), any(RequestInfo.class));
                verify(userService).updateWithoutOtpValidation(any(), any());
        }

        @Test
        public void testAuthenticate_NewUserCreation_UsesGraphEmployeeProfile() {
                String token = "jwt-token";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, null, TENANT_PB);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("oid", "26f0a779-36d5-4360-bd5f-954568d301f6");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");
                claims.put("name", "John Doe");
                claims.put("preferred_username", "johndoe");
                claims.put("email", "john@example.com");

                OidcValidatedJwt jwt = oidcJwt(claims, token);

                EmployeeCreationProfile profile = EmployeeCreationProfile.builder()
                                .employeeType("CONTRACT")
                                .designation("design-uuid-123")
                                .department("IT")
                                .build();
                when(msGraphService.getEmployeeCreationProfile(any(), eq("26f0a779-36d5-4360-bd5f-954568d301f6")))
                                .thenReturn(Optional.of(profile));

                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any()))
                                .thenThrow(new org.egov.user.domain.exception.UserNotFoundException(
                                                new UserSearchCriteria()));

                org.egov.user.domain.model.hrms.User hrmsUser = org.egov.user.domain.model.hrms.User.builder()
                                .userServiceUuid("new-uuid")
                                .userName("johndoe")
                                .name("John Doe")
                                .roles(Collections.emptyList())
                                .tenantId(TENANT_PB)
                                .build();
                when(projectEmployeeStaffUtil.createEmployeeAndProjectStaff(
                                any(org.egov.user.domain.model.hrms.User.class), anyString(), anyString(), anyString(),
                                anyString(), anyString(), anyString(), any(RequestInfo.class)))
                                .thenReturn(hrmsUser);

                User createdUser = User.builder().uuid("new-uuid").username("johndoe").type(UserType.EMPLOYEE)
                                .active(true)
                                .password("password").roles(Collections.emptySet()).build();
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(createdUser);

                Authentication result = authenticationProvider.authenticate(authenticationToken);

                assertNotNull(result);
                verify(projectEmployeeStaffUtil).createEmployeeAndProjectStaff(
                                any(org.egov.user.domain.model.hrms.User.class), eq("CONTRACT"),
                                eq("design-uuid-123"), eq("IT"), eq("EMPLOYED"), eq(TENANT_PB),
                                anyString(), any(RequestInfo.class));
        }

        @Test
        public void testAuthenticate_MfaEnable_AuthTokenJwt() {
                String token = "jwt-assertion";
                String authToken = "eyJhbGciOiJub25lIn0.eyJhbXIiOlsicHdkIiwibWZhIl19.";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, authToken, TENANT_PB);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");

                OidcValidatedJwt jwt = oidcJwt(claims, token);

                User user = User.builder().uuid("uuid").type(UserType.EMPLOYEE).active(true)
                                .roles(Collections.emptySet()).build();

                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any())).thenReturn(user);
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(user);

                authenticationProvider.authenticate(authenticationToken);

                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                verify(userService).updateWithoutOtpValidation(userCaptor.capture(), any());
                assertTrue(userCaptor.getValue().getMfaEnabled());
        }

        @Test
        public void testAuthenticate_MfaDisable_AuthTokenJwt() {
                String token = "jwt-assertion";
                String authToken = "eyJhbGciOiJub25lIn0.eyJhbXIiOlsicHdkIl19.";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, authToken, TENANT_PB);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");

                OidcValidatedJwt jwt = oidcJwt(claims, token);

                User user = User.builder().uuid("uuid").type(UserType.EMPLOYEE).active(true)
                                .roles(Collections.emptySet()).build();

                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any())).thenReturn(user);
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(user);

                authenticationProvider.authenticate(authenticationToken);

                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                verify(userService).updateWithoutOtpValidation(userCaptor.capture(), any());
                assertFalse(userCaptor.getValue().getMfaEnabled());
        }

        @Test
        public void testAuthenticate_MfaEnable_AuthTokenJson_Amr() {
                String token = "jwt-assertion";
                String authToken = "{\"amr\": [\"pwd\", \"mfa\"]}";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, authToken, TENANT_PB);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");

                OidcValidatedJwt jwt = oidcJwt(claims, token);

                User user = User.builder().uuid("uuid").type(UserType.EMPLOYEE).active(true)
                                .roles(Collections.emptySet()).build();

                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any())).thenReturn(user);
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(user);

                authenticationProvider.authenticate(authenticationToken);

                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                verify(userService).updateWithoutOtpValidation(userCaptor.capture(), any());
                assertTrue(userCaptor.getValue().getMfaEnabled());
        }

        @Test
        public void testAuthenticate_MfaEnable_AuthTokenJson_MfaEnable() {
                String token = "jwt-assertion";
                String authToken = "{\"mfaenable\": true}";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, authToken, TENANT_PB);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");

                OidcValidatedJwt jwt = oidcJwt(claims, token);

                User user = User.builder().uuid("uuid").type(UserType.EMPLOYEE).active(true)
                                .roles(Collections.emptySet()).build();

                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any())).thenReturn(user);
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(user);

                authenticationProvider.authenticate(authenticationToken);

                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                verify(userService).updateWithoutOtpValidation(userCaptor.capture(), any());
                assertTrue(userCaptor.getValue().getMfaEnabled());
        }

        @Test
        public void testAuthenticate_RawAuthToken_DefaultsToFalse() {
                String token = "jwt-token";
                String authToken = "not-a-jwt-or-json";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, authToken, TENANT_PB);

                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");

                OidcValidatedJwt jwt = oidcJwt(claims, token);

                User user = User.builder().uuid("uuid").type(UserType.EMPLOYEE).active(true)
                                .roles(Collections.emptySet()).build();

                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any())).thenReturn(user);
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(user);

                authenticationProvider.authenticate(authenticationToken);

                // Should default to false because parsing fails
                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                verify(userService).updateWithoutOtpValidation(userCaptor.capture(), any());
                assertFalse(userCaptor.getValue().getMfaEnabled());
        }

        @Test
        public void testSupports() {
                assertTrue(authenticationProvider.supports(JwtExchangeAuthenticationToken.class));
        }

        @Test(expected = SsoMissingParamException.class)
        public void testAuthenticate_MissingTenantId_Throws() {
                String token = "jwt-token";
                JwtExchangeAuthenticationToken authenticationToken = new JwtExchangeAuthenticationToken(token);
                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");
                OidcValidatedJwt jwt = oidcJwt(claims, token);
                when(jwtValidationService.validate(anyString(), any())).thenReturn(jwt);
                authenticationProvider.authenticate(authenticationToken);
        }

        @Test(expected = SsoException.class)
        public void testAuthenticate_InvalidJwt_Throws() {
                String token = "jwt-token";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, null, TENANT_PB);
                when(jwtValidationService.validate(anyString(), anyString()))
                                .thenThrow(org.egov.user.domain.exception.sso.IdpJwtValidationException.invalid("Invalid signature", null));
                authenticationProvider.authenticate(authenticationToken);
        }

        @Test(expected = SsoUserMappingException.class)
        public void testAuthenticate_InactiveUser_Throws() {
                String token = "jwt-token";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, null, TENANT_PB);
                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");
                OidcValidatedJwt jwt = oidcJwt(claims, token);
                User user = User.builder().uuid("uuid").type(UserType.EMPLOYEE).active(false).tenantId(TENANT_PB)
                                .roles(Collections.emptySet()).build();
                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any())).thenReturn(user);
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(user);
                authenticationProvider.authenticate(authenticationToken);
        }

        @Test(expected = SsoUserMappingException.class)
        public void testAuthenticate_AccountLocked_NotUnlockable_Throws() {
                String token = "jwt-token";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, null, TENANT_PB);
                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");
                OidcValidatedJwt jwt = oidcJwt(claims, token);
                User user = User.builder().uuid("uuid").type(UserType.EMPLOYEE).active(true).accountLocked(true)
                                .tenantId(TENANT_PB).roles(Collections.emptySet()).build();
                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any())).thenReturn(user);
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(user);
                when(userService.isAccountUnlockAble(user)).thenReturn(false);
                authenticationProvider.authenticate(authenticationToken);
        }

        @Test
        public void testAuthenticate_MissingTenantId_ReturnsCorrectErrorCode() {
                String token = "jwt-token";
                JwtExchangeAuthenticationToken authenticationToken = new JwtExchangeAuthenticationToken(token);
                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");
                OidcValidatedJwt jwt = oidcJwt(claims, token);
                when(jwtValidationService.validate(anyString(), any())).thenReturn(jwt);
                try {
                        authenticationProvider.authenticate(authenticationToken);
                        org.junit.Assert.fail("Expected SsoMissingParamException");
                } catch (SsoMissingParamException e) {
                        assertEquals(org.egov.user.security.oauth2.custom.jwt.SsoErrorCodes.TENANT_ID_MISSING, e.getErrorCode());
                }
        }

        @Test
        public void testAuthenticate_InactiveUser_ReturnsCorrectErrorCode() {
                String token = "jwt-token";
                JwtExchangeAuthenticationToken authenticationToken =
                                new JwtExchangeAuthenticationToken(token, null, TENANT_PB);
                Map<String, Object> claims = new HashMap<>();
                claims.put("iss", "issuer");
                claims.put("sub", "subject");
                claims.put("tenantId", TENANT_PB);
                claims.put("userType", "EMPLOYEE");
                OidcValidatedJwt jwt = oidcJwt(claims, token);
                User user = User.builder().uuid("uuid").type(UserType.EMPLOYEE).active(false).tenantId(TENANT_PB)
                                .roles(Collections.emptySet()).build();
                when(jwtValidationService.validate(anyString(), anyString())).thenReturn(jwt);
                when(userService.getUniqueUser(anyString(), anyString(), anyString(), any())).thenReturn(user);
                when(userService.updateWithoutOtpValidation(any(), any())).thenReturn(user);
                try {
                        authenticationProvider.authenticate(authenticationToken);
                        org.junit.Assert.fail("Expected SsoUserMappingException");
                } catch (SsoUserMappingException e) {
                        assertEquals(org.egov.user.security.oauth2.custom.jwt.SsoErrorCodes.USER_INACTIVE, e.getErrorCode());
                }
        }
}
