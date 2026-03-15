package org.egov.pg.clients.notification;

import org.egov.pg.clients.notification.models.EmailRequest;
import org.egov.pg.clients.notification.models.SmsRequest;

public interface NotificationClient {

	void sendEmail(String tenantId, EmailRequest request);

	void sendSms(String tenantId, SmsRequest request);
}