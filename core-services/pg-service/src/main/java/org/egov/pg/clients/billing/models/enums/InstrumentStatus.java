package org.egov.pg.clients.billing.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum InstrumentStatus {
	APPROVED("APPROVED"),
	APPROVAL_PENDING("APPROVAL_PENDING"),
	TO_BE_SUBMITTED("TO_BE_SUBMITTED"),
	CANCELLED("CANCELLED"),
	DISHONOURED("DISHONOURED"),
	REMITTED("REMITTED"),
	REJECTED("REJECTED");

	private final String value;

	InstrumentStatus(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	public boolean isOpen() {
		return this == APPROVED || this == APPROVAL_PENDING
				|| this == TO_BE_SUBMITTED || this == REMITTED;
	}

	public boolean isClosed() {
		return this == REJECTED || this == CANCELLED || this == DISHONOURED;
	}
}