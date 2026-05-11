package org.egov.wf;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.cache2k.extra.spring.SpringCache2kCacheManager;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableCaching
@EnableAsync
@Import({ TracerConfiguration.class })
public class Main {

    @Value("${app.timezone}")
    private String timeZone;

    @Value("${cache.expiry.workflow.minutes}")
    private int workflowExpiry;

    @Bean
    public ObjectMapper objectMapper(){
        return new ObjectMapper()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .setTimeZone(TimeZone.getTimeZone(timeZone));
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    @Profile("!test")
    public CacheManager cacheManager(){
        return new SpringCache2kCacheManager().addCaches(b->b.name("businessService").expireAfterWrite(workflowExpiry, TimeUnit.MINUTES)
                .entryCapacity(10)).addCaches(b->b.name("roleTenantAndStatusesMapping").expireAfterWrite(workflowExpiry, TimeUnit.MINUTES)
                .entryCapacity(10));
    }

    @Bean
    @Autowired
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

}
