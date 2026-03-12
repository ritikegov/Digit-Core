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
    public void testClearAllDecoders_Success() throws Exception {
        // Verify the method call
        ssoCacheAdminController.clearAllDecoders();
        
        verify(idpJwtValidator, times(1)).clearDecoderCache();
    }

    @Test
    public void testClearAllDecoders_ReturnsCorrectResponse() {
        // Call the method and verify response
        ResponseInfo response = ssoCacheAdminController.clearAllDecoders();
        
        assertNotNull(response);
        assertEquals("SSO decoder cache cleared successfully", response.getStatus());
    }

    @Test
    public void testClearDecoderForProvider_ValidProviderId_Success() throws Exception {
        String providerId = "azure";
        
        // Verify the method call
        ssoCacheAdminController.clearDecoderForProvider(providerId);
        
        verify(idpJwtValidator, times(1)).clearDecoderForProvider(providerId);
    }

    @Test
    public void testClearDecoderForProvider_ReturnsCorrectResponse() {
        String providerId = "microsoft";
        
        // Call the method and verify response
        ResponseInfo response = ssoCacheAdminController.clearDecoderForProvider(providerId);
        
        assertNotNull(response);
        assertEquals("SSO decoder cache cleared successfully for provider: " + providerId, response.getStatus());
    }

    @Test
    public void testClearDecoderForProvider_NullProviderId_Success() throws Exception {
        String providerId = null;
        
        // Verify the method call
        ssoCacheAdminController.clearDecoderForProvider(providerId);
        
        verify(idpJwtValidator, times(1)).clearDecoderForProvider(providerId);
    }

    @Test
    public void testClearDecoderForProvider_EmptyProviderId_Success() throws Exception {
        String providerId = "";
        
        // Verify the method call
        ssoCacheAdminController.clearDecoderForProvider(providerId);
        
        verify(idpJwtValidator, times(1)).clearDecoderForProvider(providerId);
    }

    @Test
    public void testClearAllDecoders_Endpoint_Success() throws Exception {
        mockMvc.perform(post("/sso/_clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SSO decoder cache cleared successfully"));
        
        verify(idpJwtValidator, times(1)).clearDecoderCache();
    }

    @Test
    public void testClearDecoderForProvider_Endpoint_Success() throws Exception {
        String providerId = "azure";
        
        mockMvc.perform(post("/sso/{providerId}/_clear", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SSO decoder cache cleared successfully for provider: " + providerId));
        
        verify(idpJwtValidator, times(1)).clearDecoderForProvider(providerId);
    }

    @Test
    public void testClearDecoderForProvider_Endpoint_WithSpecialCharacters_Success() throws Exception {
        String providerId = "azure-ad-prod";
        
        mockMvc.perform(post("/sso/{providerId}/_clear", providerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SSO decoder cache cleared successfully for provider: " + providerId));
        
        verify(idpJwtValidator, times(1)).clearDecoderForProvider(providerId);
    }

    @Test
    public void testClearAllDecoders_MultipleCalls_VerifyMultipleInvocations() throws Exception {
        // Call the method multiple times
        ssoCacheAdminController.clearAllDecoders();
        ssoCacheAdminController.clearAllDecoders();
        ssoCacheAdminController.clearAllDecoders();
        
        // Verify the method was called 3 times
        verify(idpJwtValidator, times(3)).clearDecoderCache();
    }

    @Test
    public void testClearDecoderForProvider_DifferentProviders_VerifyCorrectInvocations() throws Exception {
        // Call the method with different provider IDs
        ssoCacheAdminController.clearDecoderForProvider("azure");
        ssoCacheAdminController.clearDecoderForProvider("microsoft");
        ssoCacheAdminController.clearDecoderForProvider("google");
        
        // Verify each provider was cleared once
        verify(idpJwtValidator, times(1)).clearDecoderForProvider("azure");
        verify(idpJwtValidator, times(1)).clearDecoderForProvider("microsoft");
        verify(idpJwtValidator, times(1)).clearDecoderForProvider("google");
    }

    @Test
    public void testClearAllDecoders_ResponseInfoFields() {
        ResponseInfo response = ssoCacheAdminController.clearAllDecoders();
        
        assertNotNull(response);
        assertNotNull(response.getTs()); // timestamp should not be null
        assertEquals("SSO decoder cache cleared successfully", response.getStatus());
    }

    @Test
    public void testClearDecoderForProvider_ResponseInfoFields() {
        String providerId = "test-provider";
        ResponseInfo response = ssoCacheAdminController.clearDecoderForProvider(providerId);
        
        assertNotNull(response);
        assertNotNull(response.getTs()); // timestamp should not be null
        assertEquals("SSO decoder cache cleared successfully for provider: " + providerId, response.getStatus());
    }
}
