package org.egov.pg.clients.billing.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentStatus {
	NEW("NEW"),
	DEPOSITED("DEPOSITED"),
	CANCELLED("CANCELLED"),
	DISHONOURED("DISHONOURED"),
	RECONCILED("RECONCILED");

	private final String value;

	PaymentStatus(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}
}