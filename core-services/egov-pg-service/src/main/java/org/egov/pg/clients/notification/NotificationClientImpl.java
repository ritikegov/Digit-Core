package org.egov.pg.clients.notification;

import lombok.extern.slf4j.Slf4j;
import org.egov.pg.clients.notification.models.EmailRequest;
import org.egov.pg.clients.notification.models.SmsRequest;
import org.egov.pg.config.AppProperties;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ServiceCallException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class NotificationClientImpl implements NotificationClient {

	private final RestClient restClient;
	private final AppProperties appProperties;

	public NotificationClientImpl(RestClient restClient, AppProperties appProperties) {
		this.restClient = restClient;
		this.appProperties = appProperties;
	}

	@Override
	public void sendEmail(String tenantId, EmailRequest request) {
		try {
			restClient.post()
					.uri(appProperties.getNotificationHost() + appProperties.getNotificationEmailPath())
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.header("X-Tenant-ID", tenantId)
					.body(request)
					.retrieve()
					.toBodilessEntity();

			log.info("Email notification sent for templateId: {}", request.getTemplateId());

		} catch (HttpClientErrorException e) {
			log.error("Notification client error sending email: status={}", e.getStatusCode(), e);
			throw new ServiceCallException(e.getResponseBodyAsString());
		} catch (Exception e) {
			log.error("Notification unknown error sending email", e);
			throw new CustomException("NOTIFICATION_ERROR", "Failed to send email: " + e.getMessage());
		}
	}

	@Override
	public void sendSms(String tenantId, SmsRequest request) {
		try {
			restClient.post()
					.uri(appProperties.getNotificationHost() + appProperties.getNotificationSmsPath())
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.header("X-Tenant-ID", tenantId)
					.body(request)
					.retrieve()
					.toBodilessEntity();

			log.info("SMS notification sent for templateId: {}", request.getTemplateId());

		} catch (HttpClientErrorException e) {
			log.error("Notification client error sending SMS: status={}", e.getStatusCode(), e);
			throw new ServiceCallException(e.getResponseBodyAsString());
		} catch (Exception e) {
			log.error("Notification unknown error sending SMS", e);
			throw new CustomException("NOTIFICATION_ERROR", "Failed to send SMS: " + e.getMessage());
		}
	}
}
