package org.egov.pg.clients.billing;

import org.egov.pg.clients.billing.models.Payment;
import org.egov.pg.clients.billing.models.PaymentCreate;

public interface BillingClient {

	Payment createPayment(String tenantId, String clientId, PaymentCreate payment);

	Payment validatePayment(String tenantId, String clientId, PaymentCreate payment);
}