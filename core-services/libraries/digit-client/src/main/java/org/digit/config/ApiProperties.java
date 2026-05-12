package org.digit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

@Getter
@Setter
public class ApiProperties {

    @Value("${digit.services.account.base-url:http://localhost:8080}")
    private String accountServiceUrl;

    @Value("${digit.services.boundary.base-url:http://localhost:8080}")
    private String boundaryServiceUrl;

    @Value("${digit.services.workflow.base-url:http://localhost:8085}")
    private String workflowServiceUrl;

    @Value("${digit.services.idgen.base-url:http://localhost:8100}")
    private String idgenServiceUrl;

    @Value("${digit.services.notification.base-url:http://localhost:8091}")
    private String notificationServiceUrl;

    @Value("${digit.services.individual.base-url:http://localhost:8999}")
    private String individualServiceUrl;

    @Value("${digit.services.filestore.base-url:http://localhost:8080}")
    private String filestoreServiceUrl;

    @Value("${digit.services.mdms.base-url:http://localhost:8080}")
    private String mdmsServiceUrl;

    @Value("${digit.services.registry.base-url:http://localhost:8085}")
    private String registryServiceUrl;

    @Value("${digit.services.accesscontrol.base-url:http://localhost:8080}")
    private String accessControlServiceUrl;

    @Value("${digit.services.localization.base-url:http://localhost:8080}")
    private String localizationServiceUrl;

    @Value("${digit.services.otp.base-url:http://localhost:8080}")
    private String otpServiceUrl;

    @Value("${digit.services.urlshortener.base-url:http://localhost:8080}")
    private String urlShortenerServiceUrl;

    @Value("${digit.services.employee.base-url:http://localhost:8080}")
    private String employeeServiceUrl;

    @Value("${digit.services.billing.base-url:http://localhost:8080}")
    private String billingServiceUrl;

    @Value("${digit.services.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${digit.services.timeout.read:30000}")
    private int readTimeout;

    @Value("${digit.services.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${digit.services.retry.delay:1000}")
    private long retryDelay;
}
