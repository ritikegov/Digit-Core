package com.example.gateway.ratelimiters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

import com.example.gateway.config.ApplicationProperties;

import static com.example.gateway.constants.GatewayConstants.REQUEST_INFO_FIELD_NAME_PASCAL_CASE;


@Configuration
public class RateLimiterConfiguration {

    private ModifyRequestBodyGatewayFilterFactory modifyRequestBodyFilter;

    private ObjectMapper objectMapper;

    private ApplicationProperties applicationProperties;

    public RateLimiterConfiguration(ModifyRequestBodyGatewayFilterFactory modifyRequestBodyFilter, ObjectMapper objectMapper, ApplicationProperties applicationProperties) {
        this.modifyRequestBodyFilter = modifyRequestBodyFilter;
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
    }

    /**
     * Sets denyEmptyKey=false on the RequestRateLimiterGatewayFilterFactory so that
     * when rate limiting is disabled (KeyResolver returns Mono.empty()), requests are
     * allowed through instead of getting 403 Forbidden.
     * When rate limiting is enabled, the KeyResolver always returns a valid key,
     * so this setting has no effect.
     */
    @Bean
    public BeanPostProcessor rateLimiterFactoryPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof RequestRateLimiterGatewayFilterFactory factory) {
                    factory.setDenyEmptyKey(false);
                }
                return bean;
            }
        };
    }

    /**
     * IP limit
     * @return
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            if (!applicationProperties.getRateLimitEnabled()) {
                return Mono.empty();
            }
            return Mono.just(Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress());
        };
    }


    /**
     * user limit
     * @return
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            if (!applicationProperties.getRateLimitEnabled()) {
                return Mono.empty();
            }
            return Mono.just(modifyRequestBodyFilter.apply(
                    new ModifyRequestBodyGatewayFilterFactory
                            .Config()
                            .setRewriteFunction(Map.class, String.class, (serverWebExchange, s) -> {
                                RequestInfo requestInfo = objectMapper.convertValue(s.get(REQUEST_INFO_FIELD_NAME_PASCAL_CASE), RequestInfo.class);
                                return Mono.just(requestInfo.getUserInfo().getUuid());
                            })).toString());
        };
    }

}