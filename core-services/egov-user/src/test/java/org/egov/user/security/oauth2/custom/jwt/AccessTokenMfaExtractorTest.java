package org.egov.user.security.oauth2.custom.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.*;

public class AccessTokenMfaExtractorTest {

    private AccessTokenMfaExtractor extractor;

    @Before
    public void setup() {
        extractor = new AccessTokenMfaExtractor();
    }

    @Test
    public void extractFromValidatedClaims_WithMfaClaims_ReturnsMfaEnabled() throws Exception {
        // Create claims set similar to what would come from validated token
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .claim("amr", new String[]{"pwd", "mfa"})
                .claim("mfa_phone_last4", "1234")
                .claim("mfa_device_name", "device1")
                .claim("mfa_details", "details")
                .claim("mfa_registered_on", System.currentTimeMillis())
                .build();

        AccessTokenMfaDetails details = extractor.extractFromValidatedClaims(claimsSet);

        assertTrue(details.getMfaEnabled());
        assertEquals("1234", details.getMfaPhoneLast4());
        assertEquals("device1", details.getMfaDeviceName());
        assertEquals("details", details.getMfaDetails());
        assertNotNull(details.getMfaRegisteredOn());
    }

    @Test
    public void extractFromValidatedClaims_NullClaims_ReturnsMfaDisabled() {
        AccessTokenMfaDetails details = extractor.extractFromValidatedClaims(null);

        assertFalse(details.getMfaEnabled());
        assertNull(details.getMfaPhoneLast4());
        assertNull(details.getMfaDeviceName());
        assertNull(details.getMfaDetails());
        assertNull(details.getMfaRegisteredOn());
    }

    @Test
    public void extractFromValidatedClaims_NoMfaClaims_ReturnsMfaDisabled() throws Exception {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .claim("sub", "user123")
                .claim("iss", "https://example.com")
                .build();

        AccessTokenMfaDetails details = extractor.extractFromValidatedClaims(claimsSet);

        assertFalse(details.getMfaEnabled());
        assertNull(details.getMfaPhoneLast4());
        assertNull(details.getMfaDeviceName());
        assertNull(details.getMfaDetails());
        assertNull(details.getMfaRegisteredOn());
    }

}

