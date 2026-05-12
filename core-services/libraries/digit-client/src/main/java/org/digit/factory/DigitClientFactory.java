package org.digit.factory;

import org.digit.config.ApiProperties;
import org.digit.config.PropagationProperties;
import org.digit.exception.DigitClientErrorHandler;
import org.digit.services.billing.BillingClient;
import org.digit.services.boundary.BoundaryClient;
import org.digit.services.filestore.FilestoreClient;
import org.digit.services.idgen.IdGenClient;
import org.digit.services.individual.IndividualClient;
import org.digit.services.mdms.MdmsClient;
import org.digit.services.notification.NotificationClient;
import org.digit.services.registry.RegistryClient;
import org.digit.services.workflow.WorkflowClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestTemplate;

public class DigitClientFactory {

    public static BoundaryClient createBoundaryClient() {
        return createBoundaryClient("http://localhost:8080");
    }

    public static BoundaryClient createBoundaryClient(String baseUrl) {
        validate(baseUrl, "Boundary");
        return new BoundaryClient(restTemplate(), props("boundaryServiceUrl", baseUrl));
    }

    public static WorkflowClient createWorkflowClient() {
        return createWorkflowClient("http://localhost:8085");
    }

    public static WorkflowClient createWorkflowClient(String baseUrl) {
        validate(baseUrl, "Workflow");
        return new WorkflowClient(restTemplate(), props("workflowServiceUrl", baseUrl));
    }

    public static IdGenClient createIdGenClient() {
        return createIdGenClient("http://localhost:8100");
    }

    public static IdGenClient createIdGenClient(String baseUrl) {
        ApiProperties p = new ApiProperties();
        setField(p, "idgenServiceUrl", baseUrl);
        return new IdGenClient(restTemplate(), p);
    }

    public static NotificationClient createNotificationClient() {
        return createNotificationClient("http://localhost:8091");
    }

    public static NotificationClient createNotificationClient(String baseUrl) {
        ApiProperties p = new ApiProperties();
        setField(p, "notificationServiceUrl", baseUrl);
        return new NotificationClient(restTemplate(), p);
    }

    public static IndividualClient createIndividualClient() {
        return createIndividualClient("http://localhost:8999");
    }

    public static IndividualClient createIndividualClient(String baseUrl) {
        ApiProperties p = new ApiProperties();
        setField(p, "individualServiceUrl", baseUrl);
        return new IndividualClient(restTemplate(), p);
    }

    public static FilestoreClient createFilestoreClient() {
        return createFilestoreClient("http://localhost:8080");
    }

    public static FilestoreClient createFilestoreClient(String baseUrl) {
        ApiProperties p = new ApiProperties();
        setField(p, "filestoreServiceUrl", baseUrl);
        PropagationProperties propagationProperties = new PropagationProperties();
        return new FilestoreClient(restTemplate(), p, propagationProperties);
    }

    public static MdmsClient createMdmsClient() {
        return createMdmsClient("http://localhost:8080");
    }

    public static MdmsClient createMdmsClient(String baseUrl) {
        ApiProperties p = new ApiProperties();
        setField(p, "mdmsServiceUrl", baseUrl);
        return new MdmsClient(restTemplate(), p);
    }

    public static RegistryClient createRegistryClient() {
        return createRegistryClient("http://localhost:8085");
    }

    public static RegistryClient createRegistryClient(String baseUrl) {
        ApiProperties p = new ApiProperties();
        setField(p, "registryServiceUrl", baseUrl);
        return new RegistryClient(restTemplate(), p);
    }

    public static BillingClient createBillingClient() {
        return createBillingClient("http://localhost:8080");
    }

    public static BillingClient createBillingClient(String baseUrl) {
        ApiProperties p = new ApiProperties();
        setField(p, "billingServiceUrl", baseUrl);
        return new BillingClient(restTemplate(), p, new ObjectMapper());
    }

    private static RestTemplate restTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DigitClientErrorHandler());
        return rt;
    }

    private static ApiProperties props(String field, String url) {
        ApiProperties p = new ApiProperties();
        setField(p, field, url);
        setField(p, "connectTimeout", 5000);
        setField(p, "readTimeout", 30000);
        setField(p, "maxRetryAttempts", 3);
        setField(p, "retryDelay", 1000L);
        return p;
    }

    private static void validate(String url, String service) {
        if (url == null || url.trim().isEmpty())
            throw new IllegalArgumentException(service + " service base URL cannot be null or empty");
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    public static class DigitClients {
        private final BoundaryClient boundaryClient;
        private final WorkflowClient workflowClient;
        private final IdGenClient idGenClient;
        private final IndividualClient individualClient;
        private final MdmsClient mdmsClient;
        private final RegistryClient registryClient;
        private final BillingClient billingClient;
        private final NotificationClient notificationClient;
        private final FilestoreClient filestoreClient;

        public DigitClients(BoundaryClient boundaryClient, WorkflowClient workflowClient,
                IdGenClient idGenClient, IndividualClient individualClient,
                MdmsClient mdmsClient, RegistryClient registryClient,
                BillingClient billingClient, NotificationClient notificationClient,
                FilestoreClient filestoreClient) {
            this.boundaryClient = boundaryClient;
            this.workflowClient = workflowClient;
            this.idGenClient = idGenClient;
            this.individualClient = individualClient;
            this.mdmsClient = mdmsClient;
            this.registryClient = registryClient;
            this.billingClient = billingClient;
            this.notificationClient = notificationClient;
            this.filestoreClient = filestoreClient;
        }

        public BoundaryClient getBoundaryClient() { return boundaryClient; }
        public WorkflowClient getWorkflowClient() { return workflowClient; }
        public IdGenClient getIdGenClient() { return idGenClient; }
        public IndividualClient getIndividualClient() { return individualClient; }
        public MdmsClient getMdmsClient() { return mdmsClient; }
        public RegistryClient getRegistryClient() { return registryClient; }
        public BillingClient getBillingClient() { return billingClient; }
        public NotificationClient getNotificationClient() { return notificationClient; }
        public FilestoreClient getFilestoreClient() { return filestoreClient; }
    }
}
