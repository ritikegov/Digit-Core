package com.example.tradelicense.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Billing service initialization config.
 * Auto-initialization is deferred to first request with proper JWT context.
 */
@Configuration
@Slf4j
public class BillingServiceConfig implements CommandLineRunner {

    @Value("${tl.billing.auto-initialize:true}")
    private boolean autoInitialize;

    @Override
    public void run(String... args) {
        if (!autoInitialize) {
            log.info("Billing service auto-initialization is disabled");
            return;
        }
        log.info("Billing service initialization deferred to first request with JWT context");
    }
}
