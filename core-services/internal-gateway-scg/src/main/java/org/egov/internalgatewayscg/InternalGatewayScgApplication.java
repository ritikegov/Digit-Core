package org.egov.internalgatewayscg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

@SpringBootApplication
public class InternalGatewayScgApplication {

	public static void main(String[] args) {
		// Propagates MDC (TENANTID, CORRELATION_ID) across Reactor thread switches
		Hooks.enableAutomaticContextPropagation();
		SpringApplication.run(InternalGatewayScgApplication.class, args);
	}

}
