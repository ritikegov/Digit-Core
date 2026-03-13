package org.egov.user.web.controller;

import org.egov.common.contract.response.ResponseInfo;
import org.egov.user.security.oauth2.custom.jwt.IDPJwtValidator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(MockitoJUnitRunner.class)
public class SsoCacheAdminControllerTest {

    @Mock
    private IDPJwtValidator idpJwtValidator;

    @InjectMocks
    private SsoCacheAdminController ssoCacheAdminController;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(ssoCacheAdminController).build();
    }

    @Test
    public void testClearDecodersForTenantAndProvider_WithProviderId_Success() throws Exception {
        String tenantId = "pb.amritsar";
        String providerId = "azure";
        
        // Verify the method call
        ssoCacheAdminController.clearDecodersForTenantAndProvider(tenantId, providerId);
        
        verify(idpJwtValidator, times(1)).clearDecoderCacheFor(tenantId, providerId);
    }

    @Test
    public void testClearDecodersForTenantAndProvider_WithoutProviderId_Success() throws Exception {
        String tenantId = "pb.amritsar";
        
        // Verify the method call with null providerId
        ssoCacheAdminController.clearDecodersForTenantAndProvider(tenantId, null);
        
        verify(idpJwtValidator, times(1)).clearDecoderCacheFor(tenantId, null);
    }

    @Test
    public void testClearDecodersForTenantAndProvider_WithProviderId_ReturnsCorrectResponse() {
        String tenantId = "pb.amritsar";
        String providerId = "azure";
        
        // Call the method and verify response
        ResponseInfo response = ssoCacheAdminController.clearDecodersForTenantAndProvider(tenantId, providerId);
        
        assertNotNull(response);
        assertEquals("SSO decoder cache cleared successfully for tenant: " + tenantId + ", provider: " + providerId, response.getStatus());
    }

    @Test
    public void testClearDecodersForTenantAndProvider_WithoutProviderId_ReturnsCorrectResponse() {
        String tenantId = "pb.amritsar";
        
        // Call the method and verify response
        ResponseInfo response = ssoCacheAdminController.clearDecodersForTenantAndProvider(tenantId, null);
        
        assertNotNull(response);
        assertEquals("SSO decoder cache cleared successfully for tenant: " + tenantId, response.getStatus());
    }

    @Test
    public void testClearDecodersForTenantAndProvider_Endpoint_WithoutProviderId_Success() throws Exception {
        String tenantId = "pb.amritsar";
        
        mockMvc.perform(post("/sso/decoders/_clear")
                .param("tenantId", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SSO decoder cache cleared successfully for tenant: " + tenantId));
        
        verify(idpJwtValidator, times(1)).clearDecoderCacheFor(tenantId, null);
    }

    @Test
    public void testClearJwks_Endpoint_Success() throws Exception {
        String tenantId = "pb.amritsar";
        String jwksUri = "https://login.microsoftonline.com/common/discovery/v2.0/keys";
        
        mockMvc.perform(post("/sso/jwks/_clear")
                .param("tenantId", tenantId)
                .param("jwksUri", jwksUri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists()); // Just check that status exists, content depends on cache state
    }

    @Test
    public void testClearJwks_ReturnsCorrectResponse() {
        String tenantId = "pb.amritsar";
        String jwksUri = "https://login.microsoftonline.com/common/discovery/v2.0/keys";
        
        // Call the method and verify response
        ResponseInfo response = ssoCacheAdminController.clearJwks(tenantId, jwksUri);
        
        assertNotNull(response);
        assertTrue(response.getStatus().contains("SSO JWKS cache")); // Check that it contains relevant text
        assertTrue(response.getStatus().contains(tenantId));
        assertTrue(response.getStatus().contains(jwksUri));
    }
}
