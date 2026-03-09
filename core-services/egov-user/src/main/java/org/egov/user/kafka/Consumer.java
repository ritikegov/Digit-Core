package org.egov.user.kafka;

import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * Placeholder Kafka consumer for egov-user. Uncomment and configure
 * {@link org.springframework.kafka.annotation.KafkaListener} when a consumer topic is needed.
 * Set the topic in application.properties (e.g. kafka.topics.user.consumer) and reference it here.
 */
@Component
public class Consumer {

    /*
     * Uncomment to consume from a topic. Override kafka.topics.user.consumer in application.properties.
     */
    // @KafkaListener(topics = "${kafka.topics.user.consumer:egov.user.consumer}")
    public void listen(HashMap<String, Object> record) {
        // TODO: handle consumed record
    }
}
