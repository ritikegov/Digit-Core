package org.egov.user.security.oauth2.custom.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.user.config.AuthProperties;
import org.egov.user.config.OidcConfigConstants;
import org.egov.user.security.oauth2.custom.service.EmployeeCreationProfile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MsGraphServiceTest {

    private static final String USER_OID = "26f0a779-36d5-4360-bd5f-954568d301f6";
    private static final String TOKEN_RESPONSE = "{\"access_token\":\"mock-token\"}";
    private static final String USER_RESPONSE = "{\"department\":\"IT\",\"jobTitle\":\"Engineer\",\"employeeType\":\"CONTRACT\"}";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AuthProperties.Provider provider;

    private MsGraphService msGraphService;

    @Before
    public void setup() {
        msGraphService = new MsGraphService(restTemplate, new ObjectMapper());
    }

    @Test
    public void getEmployeeCreationProfile_WhenProviderNull_ReturnsEmpty() {
        Optional<org.egov.user.security.oauth2.custom.service.EmployeeCreationProfile> result =
                msGraphService.getEmployeeCreationProfile(null, USER_OID);
        assertFalse(result.isPresent());
    }

    @Test
    public void getEmployeeCreationProfile_WhenUserOidBlank_ReturnsEmpty() {
        when(provider.getGraphClientId()).thenReturn("client");
        Optional<org.egov.user.security.oauth2.custom.service.EmployeeCreationProfile> result =
                msGraphService.getEmployeeCreationProfile(provider, "");
        assertFalse(result.isPresent());
    }

    @Test
    public void getEmployeeCreationProfile_WhenGraphNotConfigured_ReturnsEmpty() {
        when(provider.getGraphClientId()).thenReturn(null);
        Optional<org.egov.user.security.oauth2.custom.service.EmployeeCreationProfile> result =
                msGraphService.getEmployeeCreationProfile(provider, USER_OID);
        assertFalse(result.isPresent());
    }

    @Test
    public void getEmployeeCreationProfile_WhenTokenFails_ReturnsEmpty() {
        when(provider.getGraphClientId()).thenReturn("client");
        when(provider.getGraphClientSecret()).thenReturn("secret");
        when(provider.getGraphTenantId()).thenReturn("tenant");
        when(provider.getGraphTokenUrl()).thenReturn("https://login.microsoftonline.com/%s/oauth2/v2.0/token");
        when(provider.getGraphUsersUrl()).thenReturn("https://graph.microsoft.com/v1.0/users/%s");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok().body(null));

        Optional<org.egov.user.security.oauth2.custom.service.EmployeeCreationProfile> result =
                msGraphService.getEmployeeCreationProfile(provider, USER_OID);

        assertFalse(result.isPresent());
    }

    @Test
    public void getEmployeeCreationProfile_WhenGraphReturnsUser_ParsesProfile() {
        when(provider.getGraphClientId()).thenReturn("client");
        when(provider.getGraphClientSecret()).thenReturn("secret");
        when(provider.getGraphTenantId()).thenReturn("tenant");
        when(provider.getGraphTokenUrl()).thenReturn("https://login.microsoftonline.com/%s/oauth2/v2.0/token");
        when(provider.getGraphScope()).thenReturn(null);
        when(provider.getGraphUsersUrl()).thenReturn("https://graph.microsoft.com/v1.0/users/%s");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(TOKEN_RESPONSE));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(USER_RESPONSE));

        Optional<org.egov.user.security.oauth2.custom.service.EmployeeCreationProfile> result =
                msGraphService.getEmployeeCreationProfile(provider, USER_OID);

        assertTrue(result.isPresent());
        assertEquals("CONTRACT", result.get().getEmployeeType());
        assertEquals("Engineer", result.get().getDesignation());
        assertEquals("IT", result.get().getDepartment());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        assertTrue(urlCaptor.getValue().contains(USER_OID));
        assertTrue(urlCaptor.getValue().contains("$select="));
    }

    @Test
    public void getEmployeeCreationProfile_WhenGraphReturnsEmptyBody_ReturnsEmpty() {
        when(provider.getGraphClientId()).thenReturn("client");
        when(provider.getGraphClientSecret()).thenReturn("secret");
        when(provider.getGraphTenantId()).thenReturn("tenant");
        when(provider.getGraphTokenUrl()).thenReturn("https://login.microsoftonline.com/%s/oauth2/v2.0/token");
        when(provider.getGraphUsersUrl()).thenReturn("https://graph.microsoft.com/v1.0/users/%s");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(TOKEN_RESPONSE));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok().body(null));

        Optional<EmployeeCreationProfile> result =
                msGraphService.getEmployeeCreationProfile(provider, USER_OID);

        assertFalse(result.isPresent());
    }

    @Test
    public void supports_WhenAzureType_ReturnsTrue() {
        when(provider.getGraphServiceType()).thenReturn(OidcConfigConstants.GRAPH_SERVICE_TYPE_AZURE);
        assertTrue(msGraphService.supports(provider));
    }

    @Test
    public void supports_WhenNotAzure_ReturnsFalse() {
        when(provider.getGraphServiceType()).thenReturn("other");
        assertFalse(msGraphService.supports(provider));
    }
}
