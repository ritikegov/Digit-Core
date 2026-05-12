package org.digit.services.config;

import org.digit.services.util.HeaderStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Map;

/**
 * RestTemplate interceptor that automatically propagates headers from the current
 * request context to outbound API calls.
 */
public class HeaderPropagationInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HeaderPropagationInterceptor.class);
    private final PropagationProperties propagationProperties;

    public HeaderPropagationInterceptor(PropagationProperties propagationProperties) {
        this.propagationProperties = propagationProperties;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, 
            byte[] body, 
            ClientHttpRequestExecution execution) throws IOException {
        
        System.out.println("🔍 HeaderPropagationInterceptor called for request: " + request.getMethod() + " " + request.getURI());
        
        try {
            // Get headers from current request context
            Map<String, String> headersToPropagate = HeaderStore.getHeadersToPropagate(propagationProperties);
            
            System.out.println("🔍 Headers to propagate: " + headersToPropagate);
            System.out.println("🔍 PropagationProperties allow list: " + propagationProperties.getAllow());
            System.out.println("🔍 PropagationProperties prefixes: " + propagationProperties.getPrefixes());
            System.out.println("🔍 PropagationProperties object: " + propagationProperties);
            System.out.println("🔍 PropagationProperties class: " + propagationProperties.getClass());
            
            if (headersToPropagate != null && !headersToPropagate.isEmpty()) {
                HttpHeaders headers = request.getHeaders();
                int propagatedCount = 0;
                
                for (Map.Entry<String, String> entry : headersToPropagate.entrySet()) {
                    String headerName = entry.getKey();
                    String headerValue = entry.getValue();
                    
                    System.out.println("🔍 Checking header: " + headerName + " = " + headerValue);
                    System.out.println("🔍 Should propagate? " + propagationProperties.shouldPropagate(headerName));
                    
                    if (propagationProperties.shouldPropagate(headerName)) {
                        headers.add(headerName, headerValue);
                        propagatedCount++;
                        System.out.println("✅ Added header: " + headerName + " = " + headerValue);
                    } else {
                        System.out.println("❌ Skipped header: " + headerName + " (not in allow list)");
                    }
                }
                
                System.out.println("🔍 Propagated " + propagatedCount + " headers to outbound request to " + request.getURI());
            } else {
                System.out.println("⚠️ No headers to propagate found in request context");
            }
            
        } catch (Exception e) {
            log.error("🔍 Failed to propagate headers: {}", e.getMessage(), e);
        }
        
        return execution.execute(request, body);
    }
}
