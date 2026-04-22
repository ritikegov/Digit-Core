package org.egov.domain.service;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import org.egov.domain.exception.InvalidOtpRequestException;
import org.egov.domain.model.MobileValidationAttributes;
import org.egov.domain.model.MobileValidationConfig;
import org.egov.domain.model.MobileValidationRules;
import org.egov.domain.model.OtpRequest;
import org.egov.domain.model.OtpRequestType;
import org.egov.persistence.repository.MdmsRepository;
import org.egov.persistence.repository.ValidationRulesCacheRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class OtpRequestValidatorTest {

    @Mock
    private MdmsRepository mdmsRepository;

    @Mock
    private ValidationRulesCacheRepository cacheRepository;

    private OtpRequestValidator validator;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        validator = new OtpRequestValidator(mdmsRepository, cacheRepository);
    }

    @Test(expected = InvalidOtpRequestException.class)
    public void test_should_throw_exception_when_tenant_id_is_not_present() {
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId(null)
                .mobileNumber("1234567890")
                .type(OtpRequestType.REGISTER)
                .build();

        validator.validate(otpRequest);
    }

    @Test(expected = InvalidOtpRequestException.class)
    public void test_should_throw_exception_when_mobile_number_is_not_present() {
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber(null)
                .type(OtpRequestType.REGISTER)
                .build();

        validator.validate(otpRequest);
    }

    @Test(expected = InvalidOtpRequestException.class)
    public void test_should_throw_exception_when_type_is_not_present() {
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("1234567890")
                .type(null)
                .build();

        validator.validate(otpRequest);
    }

    @Test
    public void test_should_validate_with_mdms_config_matching_countryCode() {
        MobileValidationConfig indiaConfig = MobileValidationConfig.builder()
                .rules(MobileValidationRules.builder()
                        .pattern("^[6-9][0-9]{9}$")
                        .minLength(10)
                        .maxLength(10)
                        .errorMessage("Invalid Indian mobile number")
                        .build())
                .fieldType("ind.mobile")
                .isDefault(true)
                .attributes(MobileValidationAttributes.builder().countryCode("+91").build())
                .build();

        MobileValidationConfig ethiopiaConfig = MobileValidationConfig.builder()
                .rules(MobileValidationRules.builder()
                        .pattern("^[79][0-9]{8}$")
                        .minLength(9)
                        .maxLength(9)
                        .errorMessage("Invalid Ethiopian mobile number")
                        .build())
                .fieldType("etpmo.mobile")
                .attributes(MobileValidationAttributes.builder().countryCode("+251").build())
                .build();

        when(mdmsRepository.fetchMobileValidationConfigs(anyString(), any()))
                .thenReturn(Arrays.asList(indiaConfig, ethiopiaConfig));

        // Send countryCode +91, mobile matches Indian pattern
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("9123456789")
                .countryCode("+91")
                .type(OtpRequestType.REGISTER)
                .build();

        validator.validate(otpRequest);
    }

    @Test(expected = InvalidOtpRequestException.class)
    public void test_should_fail_when_countryCode_matches_but_number_invalid_for_that_pattern() {
        MobileValidationConfig ethiopiaConfig = MobileValidationConfig.builder()
                .rules(MobileValidationRules.builder()
                        .pattern("^[79][0-9]{8}$")
                        .minLength(9)
                        .maxLength(9)
                        .errorMessage("Invalid Ethiopian mobile number")
                        .build())
                .fieldType("etpmo.mobile")
                .attributes(MobileValidationAttributes.builder().countryCode("+251").build())
                .build();

        when(mdmsRepository.fetchMobileValidationConfigs(anyString(), any()))
                .thenReturn(Collections.singletonList(ethiopiaConfig));

        // Send countryCode +251 but mobile doesn't match Ethiopian pattern
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("1234567890")
                .countryCode("+251")
                .type(OtpRequestType.REGISTER)
                .build();

        validator.validate(otpRequest);
    }

    @Test
    public void test_should_use_default_config_when_no_countryCode_sent() {
        MobileValidationConfig indiaConfig = MobileValidationConfig.builder()
                .rules(MobileValidationRules.builder()
                        .pattern("^[6-9][0-9]{9}$")
                        .minLength(10)
                        .maxLength(10)
                        .errorMessage("Invalid Indian mobile number")
                        .build())
                .fieldType("ind.mobile")
                .isDefault(true)
                .attributes(MobileValidationAttributes.builder().countryCode("+91").build())
                .build();

        MobileValidationConfig ethiopiaConfig = MobileValidationConfig.builder()
                .rules(MobileValidationRules.builder()
                        .pattern("^[79][0-9]{8}$")
                        .minLength(9)
                        .maxLength(9)
                        .errorMessage("Invalid Ethiopian mobile number")
                        .build())
                .fieldType("etpmo.mobile")
                .attributes(MobileValidationAttributes.builder().countryCode("+251").build())
                .build();

        when(mdmsRepository.fetchMobileValidationConfigs(anyString(), any()))
                .thenReturn(Arrays.asList(indiaConfig, ethiopiaConfig));

        // No countryCode sent, should use default (India) config
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("9123456789")
                .type(OtpRequestType.REGISTER)
                .build();

        validator.validate(otpRequest);
    }

    @Test
    public void test_should_fallback_to_default_when_countryCode_not_found_in_mdms() {
        MobileValidationConfig indiaConfig = MobileValidationConfig.builder()
                .rules(MobileValidationRules.builder()
                        .pattern("^[6-9][0-9]{9}$")
                        .minLength(10)
                        .maxLength(10)
                        .errorMessage("Invalid Indian mobile number")
                        .build())
                .fieldType("ind.mobile")
                .isDefault(true)
                .attributes(MobileValidationAttributes.builder().countryCode("+91").build())
                .build();

        when(mdmsRepository.fetchMobileValidationConfigs(anyString(), any()))
                .thenReturn(Collections.singletonList(indiaConfig));

        // Send unknown countryCode +44, should fall back to default (India) config
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("9123456789")
                .countryCode("+44")
                .type(OtpRequestType.REGISTER)
                .build();

        validator.validate(otpRequest);
    }

    @Test(expected = InvalidOtpRequestException.class)
    public void test_should_fail_when_no_mdms_config_found() {
        when(mdmsRepository.fetchMobileValidationConfigs(anyString(), any()))
                .thenReturn(Collections.emptyList());

        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("1234567890")
                .type(OtpRequestType.REGISTER)
                .build();

        validator.validate(otpRequest);
    }

    @Test(expected = InvalidOtpRequestException.class)
    public void test_should_fail_when_countryCode_sent_but_no_matching_or_default_config() {
        MobileValidationConfig ethiopiaConfig = MobileValidationConfig.builder()
                .rules(MobileValidationRules.builder()
                        .pattern("^[79][0-9]{8}$")
                        .minLength(9)
                        .maxLength(9)
                        .errorMessage("Invalid Ethiopian mobile number")
                        .build())
                .fieldType("etpmo.mobile")
                .attributes(MobileValidationAttributes.builder().countryCode("+251").build())
                .build();

        when(mdmsRepository.fetchMobileValidationConfigs(anyString(), any()))
                .thenReturn(Collections.singletonList(ethiopiaConfig));

        // Send countryCode +44, no match and no default config
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("1234567890")
                .countryCode("+44")
                .type(OtpRequestType.REGISTER)
                .build();

        validator.validate(otpRequest);
    }

    @Test(expected = InvalidOtpRequestException.class)
    public void test_should_fail_mdms_validation_for_invalid_pattern() {
        MobileValidationConfig config = MobileValidationConfig.builder()
                .rules(MobileValidationRules.builder()
                        .pattern("^[79][0-9]{8}$")
                        .minLength(9)
                        .maxLength(9)
                        .errorMessage("Invalid mobile number")
                        .build())
                .fieldType("mobile")
                .isDefault(true)
                .attributes(MobileValidationAttributes.builder().countryCode("+251").build())
                .build();

        when(mdmsRepository.fetchMobileValidationConfigs(anyString(), any()))
                .thenReturn(Collections.singletonList(config));

        // Mobile starts with 1, which doesn't match pattern ^[79]
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("123456789")
                .type(OtpRequestType.REGISTER)
                .build();

        validator.validate(otpRequest);
    }

    @Test
    public void test_should_skip_validation_for_password_reset() {
        // No MDMS config available, but PASSWORD_RESET should still pass
        when(mdmsRepository.fetchMobileValidationConfigs(anyString(), any()))
                .thenReturn(Collections.emptyList());

        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("1234567890")
                .type(OtpRequestType.PASSWORD_RESET)
                .build();

        validator.validate(otpRequest);
    }

    @Test
    public void test_isMobileNumberValid_returns_true_when_mobile_absent() {
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber(null)
                .type(OtpRequestType.REGISTER)
                .build();

        assertTrue(validator.isMobileNumberValid(otpRequest));
    }

    @Test
    public void test_isMobileNumberValid_returns_false_when_no_mdms_config() {
        final OtpRequest otpRequest = OtpRequest.builder()
                .tenantId("tenantId")
                .mobileNumber("1234567890")
                .type(OtpRequestType.REGISTER)
                .build();

        assertFalse(validator.isMobileNumberValid(otpRequest));
        assertTrue(otpRequest.hasMdmsValidationError());
    }
}
