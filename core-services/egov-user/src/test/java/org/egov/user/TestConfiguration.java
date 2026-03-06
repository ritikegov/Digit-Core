package org.egov.user;

import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.egov.encryption.util.MdmsFetcher;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@Configuration
public class TestConfiguration {

    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return mock(KafkaTemplate.class);
    }

    @Bean
    public RestTemplateBuilder restTemplateBuilder() {
        return new RestTemplateBuilder();
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    @Bean
    public MdmsFetcher mdmsFetcher() {
        MdmsFetcher mock = mock(MdmsFetcher.class);

        when(mock.getSecurityMdmsForFilter(any()))
                .thenReturn(Resources.getFileJSONArrayContents("getSecurityMdmsForFilterResponse.json"));

        when(mock.getMaskingMdmsForFilter(any()))
                .thenReturn(Resources.getFileJSONArrayContents("getMaskingMdmsForFilterResponse.json"));

        return mock;
    }

}
