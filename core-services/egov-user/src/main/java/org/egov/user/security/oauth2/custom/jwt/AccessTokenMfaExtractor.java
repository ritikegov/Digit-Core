package org.egov.user.security.oauth2.custom.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

/**
 * Extracts MFA-related details from the IdP access_token (JWT or JSON).
 * Microsoft Entra ID puts MFA info in the access_token's "amr" (Authentication
 * Methods References) claim; optional claims may include phone/device info.
 */
@Slf4j
@Component
public class AccessTokenMfaExtractor {

    private static final String CLAIM_AMR = "amr";
    private static final String MFA_VALUE = "mfa";
    private static final String MFA_VALUE_NGCMFA = "ngcmfa";
    private static final String CLAIM_MFA_PHONE_LAST4 = "mfa_phone_last4";
    private static final String CLAIM_MFA_DEVICE = "mfa_device_name";
    private static final String CLAIM_MFA_DETAILS = "mfa_details";
    private static final String CLAIM_MFA_REGISTERED_ON = "mfa_registered_on";
    /** JSON key for MFA enabled flag (e.g. in non-JWT token payload). */
    private static final String KEY_MFA_ENABLE = "mfaenable";

    public AccessTokenMfaExtractor() {
        // Default constructor for Spring
    }
    /**
     * Extracts MFA details from pre-validated JWT claims set.
     * This is the SECURE method that uses claims from already-validated tokens.
     * 
     * @param claimsSet the validated JWT claims set
     * @return AccessTokenMfaDetails with extracted MFA information
     */
    public AccessTokenMfaDetails extractFromValidatedClaims(JWTClaimsSet claimsSet) {
        if (claimsSet == null) {
            return AccessTokenMfaDetails.builder().mfaEnabled(false).build();
        }
        
        boolean mfaEnabled = isMfaFromAmr(claimsSet.getClaim(CLAIM_AMR));
        
        // Also check for mfaenable claim (from JSON tokens)
        if (!mfaEnabled) {
            Object mfaEnableClaim = claimsSet.getClaim(KEY_MFA_ENABLE);
            if (mfaEnableClaim instanceof Boolean) {
                mfaEnabled = (Boolean) mfaEnableClaim;
            } else if (mfaEnableClaim != null) {
                mfaEnabled = Boolean.parseBoolean(mfaEnableClaim.toString());
            }
        }
        
        String mfaPhoneLast4 = getStringClaim(claimsSet, CLAIM_MFA_PHONE_LAST4);
        String mfaDeviceName = getStringClaim(claimsSet, CLAIM_MFA_DEVICE);
        String mfaDetails = getStringClaim(claimsSet, CLAIM_MFA_DETAILS);
        Date mfaRegisteredOn = getDateClaim(claimsSet, CLAIM_MFA_REGISTERED_ON);
        
        return AccessTokenMfaDetails.builder()
                .mfaEnabled(mfaEnabled)
                .mfaPhoneLast4(mfaPhoneLast4)
                .mfaDeviceName(mfaDeviceName)
                .mfaDetails(mfaDetails)
                .mfaRegisteredOn(mfaRegisteredOn)
                .build();
    }


    /**
     * Determines if MFA was used based on the "amr" (Authentication Methods Reference) claim.
     * Microsoft sends amr as array; Nimbus may return List or other collection. Also handles single string.
     *
     * @param amrClaim the amr claim value from the JWT
     * @return true if MFA is indicated in the amr claim, false otherwise
     */
    private boolean isMfaFromAmr(Object amrClaim) {
        if (amrClaim == null) return false;
        if (amrClaim instanceof Collection) {
            for (Object item : (Collection<?>) amrClaim) {
                if (isMfaValue(item)) return true;
            }
            return false;
        }
        if (amrClaim instanceof Iterable) {
            Iterator<?> it = ((Iterable<?>) amrClaim).iterator();
            while (it.hasNext()) {
                if (isMfaValue(it.next())) return true;
            }
            return false;
        }
        if (amrClaim.getClass().isArray()) {
            Object[] array = (Object[]) amrClaim;
            for (Object item : array) {
                if (isMfaValue(item)) return true;
            }
            return false;
        }
        return isMfaValue(amrClaim);
    }

    /**
     * Checks if an item represents an MFA authentication method.
     * Recognizes "mfa" and "ngcmfa" values (case-insensitive).
     *
     * @param item the item to check
     * @return true if the item represents MFA, false otherwise
     */
    private boolean isMfaValue(Object item) {
        if (item == null) return false;
        String s = item.toString().trim();
        return MFA_VALUE.equalsIgnoreCase(s) || MFA_VALUE_NGCMFA.equalsIgnoreCase(s);
    }

    /**
     * Safely extracts a string claim from JWT claims set.
     *
     * @param claims the JWT claims set
     * @param name the claim name to extract
     * @return the claim value as string, or null if not present or extraction fails
     */
    private String getStringClaim(JWTClaimsSet claims, String name) {
        try {
            Object v = claims.getClaim(name);
            return v != null ? v.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Safely extracts a date claim from JWT claims set.
     * Handles both Date objects and numeric timestamps.
     *
     * @param claims the JWT claims set
     * @param name the claim name to extract
     * @return the claim value as Date, or null if not present or extraction fails
     */
    private Date getDateClaim(JWTClaimsSet claims, String name) {
        try {
            Object v = claims.getClaim(name);
            if (v == null) return null;
            if (v instanceof Date) return (Date) v;
            if (v instanceof Number) return new Date(((Number) v).longValue());
            return null;
        } catch (Exception e) {
            return null;
        }
    }

}
