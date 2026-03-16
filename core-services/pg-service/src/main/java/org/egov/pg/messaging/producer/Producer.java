package org.egov.pg.messaging.producer;

public interface Producer {

	void push(String topic, Object message);
}
