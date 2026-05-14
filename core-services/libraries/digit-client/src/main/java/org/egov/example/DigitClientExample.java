package org.egov.example;

import org.egov.config.ApiConfig;
import org.egov.services.boundary.BoundaryClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DigitClientExample {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(new Class[]{ApiConfig.class});
        BoundaryClient boundaryClient = (BoundaryClient)context.getBean(BoundaryClient.class);
        DigitClientExample.demonstrateBoundaryOperations(boundaryClient);
        context.close();
    }

    private static void demonstrateBoundaryOperations(BoundaryClient boundaryClient) {
    }
}

