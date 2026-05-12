package org.digit.services.example;

import org.digit.services.config.ApiConfig;
import org.digit.services.exception.DigitClientException;
import org.digit.services.boundary.BoundaryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DigitClientExample {

    public static void main(String[] args) {
        ApplicationContext context = new AnnotationConfigApplicationContext(ApiConfig.class);
        BoundaryClient boundaryClient = context.getBean(BoundaryClient.class);
        demonstrateBoundaryOperations(boundaryClient);
        ((AnnotationConfigApplicationContext) context).close();
    }

    private static void demonstrateBoundaryOperations(BoundaryClient boundaryClient) {
        try {
            // boundary search operations
        } catch (DigitClientException e) {
            // handle error
        }
    }
}
