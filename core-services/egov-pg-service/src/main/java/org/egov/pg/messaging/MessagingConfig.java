package org.egov.pg.messaging;

import lombok.RequiredArgsConstructor;
import org.egov.pg.config.AppProperties;
import org.egov.pg.messaging.producer.KafkaProducer;
import org.egov.pg.messaging.producer.NoOpMessageProducer;
import org.egov.pg.messaging.producer.Producer;
import org.egov.pg.messaging.producer.RedisProducer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@RequiredArgsConstructor
public class MessagingConfig {

	private final AppProperties appProperties;

	private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;
	private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

	@Bean
	public Producer producer() {

		if (Boolean.FALSE.equals(appProperties.getMessageBrokerEnabled())) {
			return new NoOpMessageProducer();
		}

		String type = appProperties.getMessageBrokerType();

		return switch (type.toUpperCase()) {
			case "KAFKA" -> {
				KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
				if (kafkaTemplate == null) {
					throw new IllegalStateException("KafkaTemplate bean not found but broker type is KAFKA");
				}
				yield new KafkaProducer(kafkaTemplate);
			}

			case "REDIS" -> {
				RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
				if (redisTemplate == null) {
					throw new IllegalStateException("RedisTemplate bean not found but broker type is REDIS");
				}
				yield new RedisProducer(redisTemplate);
			}

			default -> throw new IllegalArgumentException("Unsupported broker type: " + type);
		};
	}
}
