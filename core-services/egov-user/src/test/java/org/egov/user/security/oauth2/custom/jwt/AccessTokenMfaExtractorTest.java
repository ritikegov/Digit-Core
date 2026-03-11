package org.egov.user.security.oauth2.custom.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.Assert.*;

public class AccessTokenMfaExtractorTest {

    private AccessTokenMfaExtractor extractor;

    @Before
    public void setup() {
        extractor = new AccessTokenMfaExtractor(new ObjectMapper());
    }

    @Test
    public void extract_NullOrEmpty_ReturnsMfaDisabled() {
        AccessTokenMfaDetails fromNull = extractor.extract(null);
        AccessTokenMfaDetails fromEmpty = extractor.extract("");

        assertFalse(fromNull.getMfaEnabled());
        assertNull(fromNull.getMfaPhoneLast4());
        assertNull(fromNull.getMfaDeviceName());
        assertNull(fromNull.getMfaDetails());
        assertNull(fromNull.getMfaRegisteredOn());

        assertFalse(fromEmpty.getMfaEnabled());
        assertNull(fromEmpty.getMfaPhoneLast4());
        assertNull(fromEmpty.getMfaDeviceName());
        assertNull(fromEmpty.getMfaDetails());
        assertNull(fromEmpty.getMfaRegisteredOn());
    }

    @Test
    public void extract_JwtWithAmrMfa_ReturnsMfaEnabled() throws Exception {
        String token = buildJwt("{\"amr\":[\"pwd\",\"mfa\"]}");

        AccessTokenMfaDetails details = extractor.extract(token);

        assertTrue(details.getMfaEnabled());
    }

    @Test
    public void extract_JwtWithAmrNgcmfa_ReturnsMfaEnabled() throws Exception {
        String token = buildJwt("{\"amr\":[\"ngcmfa\"]}");

        AccessTokenMfaDetails details = extractor.extract(token);

        assertTrue(details.getMfaEnabled());
    }

    @Test
    public void extract_JwtWithAmrPwdOnly_ReturnsMfaDisabled() throws Exception {
        String token = buildJwt("{\"amr\":[\"pwd\"]}");

        AccessTokenMfaDetails details = extractor.extract(token);

        assertFalse(details.getMfaEnabled());
    }

    @Test
    public void extract_JwtWithOptionalClaims_SetsAllFields() throws Exception {
        long now = System.currentTimeMillis();
        String payloadJson = String.format("{\"amr\":[\"mfa\"],\"mfa_phone_last4\":\"1234\",\"mfa_device_name\":\"device1\",\"mfa_details\":\"details\",\"mfa_registered_on\":%d}", now);
        String token = buildJwt(payloadJson);

        AccessTokenMfaDetails details = extractor.extract(token);

        assertTrue(details.getMfaEnabled());
        assertEquals("1234", details.getMfaPhoneLast4());
        assertEquals("device1", details.getMfaDeviceName());
        assertEquals("details", details.getMfaDetails());
        Date registeredOn = details.getMfaRegisteredOn();
        assertNotNull(registeredOn);
        assertEquals(now, registeredOn.getTime());
    }

    @Test
    public void extract_JsonWithMfaEnable_ReturnsMfaEnabled() {
        String json = "{\"mfaenable\": true}";

        AccessTokenMfaDetails details = extractor.extract(json);

        assertTrue(details.getMfaEnabled());
    }

    @Test
    public void extract_JsonWithAmrArray_ReturnsMfaEnabled() {
        String json = "{\"amr\":[\"mfa\"]}";

        AccessTokenMfaDetails details = extractor.extract(json);

        assertTrue(details.getMfaEnabled());
    }

    @Test
    public void extract_InvalidToken_ReturnsMfaDisabled() {
        String notJwtOrJson = "this-is-not-a-valid-token";

        AccessTokenMfaDetails details = extractor.extract(notJwtOrJson);

        assertFalse(details.getMfaEnabled());
        assertNull(details.getMfaPhoneLast4());
        assertNull(details.getMfaDeviceName());
        assertNull(details.getMfaDetails());
        assertNull(details.getMfaRegisteredOn());
    }

    @Test
    public void extract_JsonMfaRegisteredOnTimestamp_SetsDate() {
        long ts = System.currentTimeMillis();
        String json = "{\"mfaenable\": true,\"mfa_registered_on\":" + ts + "}";

        AccessTokenMfaDetails details = extractor.extract(json);

        assertTrue(details.getMfaEnabled());
        assertNotNull(details.getMfaRegisteredOn());
        assertEquals(ts, details.getMfaRegisteredOn().getTime());
    }

    private String buildJwt(String payloadJson) {
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url(payloadJson);
        return header + "." + payload + ".";
    }

    private String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}

