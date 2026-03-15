package org.egov.pg.clients.billing.models.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReceiptType {
	ADHOC("ADHOC"),
	BILLBASED("BILLBASED"),
	CHALLAN("CHALLAN");

	private final String value;

	ReceiptType(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}
}