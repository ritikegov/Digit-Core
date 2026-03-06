# Tracer Library - Detailed Documentation

## Table of Contents
1. [Overview](#overview)
2. [Error Handling](#error-handling)
3. [Logging](#logging)
4. [OpenTelemetry Integration](#opentelemetry-integration)
5. [Configuration](#configuration)
6. [Integration Guide](#integration-guide)
7. [API Reference](#api-reference)

## Overview

The Tracer library is a comprehensive distributed tracing solution for Spring Boot applications that provides:

- **Correlation ID Management**: Automatic correlation ID extraction, generation, and propagation across HTTP requests and Kafka messages
- **Comprehensive Logging**: Detailed logging of HTTP requests/responses, Kafka messages, and database operations
- **Error Handling**: Centralized exception handling with Dead Letter Queue (DLQ) support
- **OpenTelemetry Integration**: Distributed tracing capabilities with Jaeger
- **Request/Response Tracing**: Full request/response body logging with configurable detail levels

### Key Features
- Automatic correlation ID management across microservices
- Structured logging with correlation ID context
- Error queue publishing for failed operations
- Kafka message tracing (producer/consumer)
- HTTP request/response tracing
- OpenTelemetry integration for distributed tracing
- Configurable logging levels and patterns

## Error Handling

### Overview
The tracer library provides comprehensive error handling capabilities through centralized exception management and Dead Letter Queue (DLQ) integration.

### Exception Handling Architecture

#### 1. Global Exception Handler (`ExceptionAdvise`)
The library uses a `@ControllerAdvice` annotated class that catches all exceptions thrown in the application:

```java
@ControllerAdvice
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class ExceptionAdvise {
    // Handles all exceptions globally
}
```

#### 2. Supported Exception Types

The library handles the following exception types:

- **HttpMediaTypeNotSupportedException**: Unsupported media type errors
- **ResourceAccessException**: Service connectivity issues
- **HttpMessageNotReadableException**: JSON parsing errors
- **JsonParseException**: JSON parsing failures
- **JsonMappingException**: Object mapping errors
- **MethodArgumentNotValidException**: Validation errors
- **BindException**: Binding errors
- **CustomException**: Custom business logic exceptions
- **General Exception**: All other unhandled exceptions

#### 3. Error Response Structure

All errors are structured using the `ErrorRes` model:

```java
public class ErrorRes {
    private List<Error> errors;
    // ... other fields
}

public class Error {
    private String code;
    private String message;
    private String description;
    private String params;
}
```

### Dead Letter Queue (DLQ) Integration

#### 1. DLQ Configuration

To enable DLQ functionality, set the following properties:

```properties
# Enable error publishing to DLQ
tracer.errors.publish=true

# DLQ topic name
tracer.errors.topic=egov-error

# Error details topic for indexing
tracer.errorDetails.topic=error-details-indexer-topic

# Enable error details publishing
tracer.errorDetails.publishFlag=true
```

#### 2. Error Queue Contract

Errors are published to DLQ using the `ErrorQueueContract` structure:

```java
public class ErrorQueueContract {
    private String id;                    // Unique error ID (UUID)
    private String source;                // Source of the error
    private Object body;                  // Request body that caused the error
    private Long ts;                      // Timestamp
    private ErrorRes errorRes;            // Structured error response
    private List<StackTraceElement> exception; // Stack trace
    private String message;               // Error message
    private String correlationId;         // Correlation ID for tracing
}
```

#### 3. DLQ Publishing Process

The error publishing process works as follows:

1. **Exception Detection**: Global exception handler catches all exceptions
2. **Error Context Collection**: 
   - Request body extraction
   - Correlation ID retrieval
   - Stack trace capture
   - Error response structure creation
3. **DLQ Publishing**: Error details are sent to configured Kafka topic
4. **Fallback Handling**: If serialization fails, JSON string fallback is used

```java
void sendErrorMessage(String body, Exception ex, String source, boolean isJsonContentType) {
    if (tracerProperties.isErrorsPublish()) {
        ErrorQueueContract errorQueueContract = ErrorQueueContract.builder()
                .id(UUID.randomUUID().toString())
                .correlationId(MDC.get(CORRELATION_ID_MDC))
                .body(requestBody)
                .source(source)
                .ts(new Date().getTime())
                .exception(Arrays.asList(elements))
                .message(ex.getMessage())
                .build();
        
        errorQueueProducer.sendMessage(errorQueueContract);
    }
}
```

#### 4. Error Queue Producer

The `ErrorQueueProducer` handles sending errors to Kafka topics:

```java
@Component
public class ErrorQueueProducer {
    
    public void sendMessage(ErrorQueueContract errorQueueContract) {
        try {
            kafkaTemplate.send(tracerProperties.getErrorsTopic(), errorQueueContract);
        } catch (SerializationException e) {
            // Fallback to JSON string
            kafkaTemplate.send(tracerProperties.getErrorsTopic(), 
                objectMapper.writeValueAsString(errorQueueContract));
        }
    }
}
```

### Custom Exception Handling

#### 1. CustomException Usage

The library supports custom exceptions with error codes and messages:

```java
// Single error
throw new CustomException("usr_001", "Invalid user role");

// Multiple errors
Map<String, String> errors = new HashMap<>();
errors.put("asset_001", "Invalid user");
errors.put("asset_002", "Invalid name");
throw new CustomException(errors);
```

#### 2. Validation Error Handling

For validation errors, the library automatically extracts binding errors:

```java
private List<Error> getBindingErrors(BindingResult bindingResult, List<Error> errors) {
    for (ObjectError objectError : bindingResult.getAllErrors()) {
        Error error = new Error();
        error.setCode(objectError.getCodes()[0]);
        error.setMessage(objectError.getDefaultMessage());
        errors.add(error);
    }
    return errors;
}
```

### Error Handling Configuration Parameters

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `tracer.errors.publish` | Enable error publishing to DLQ | `false` | No |
| `tracer.errors.topic` | DLQ topic name | `egov-error` | Yes (if DLQ enabled) |
| `tracer.errorDetails.topic` | Error details topic | `error-details-indexer-topic` | Yes (if DLQ enabled) |
| `tracer.errorDetails.publishFlag` | Enable error details publishing | `false` | No |
| `tracer.errors.provideExceptionInDetails` | Include exception details in response | `false` | No |

## Logging

### Overview
The tracer library provides comprehensive logging capabilities for HTTP requests, Kafka operations, and database calls with correlation ID context.

### HTTP Request/Response Logging

#### 1. Request Logging

The `TracerFilter` handles incoming HTTP request logging:

```java
    private void logRequestBodyAndParams(HttpServletRequest requestWrapper) {
        try {
            //final String requestBody = IOUtils.toString(requestWrapper.getInputStream(), UTF_8);
            final ServletInputStream inputStream = requestWrapper.getInputStream();
            String requestBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            String requestParams = requestWrapper.getQueryString();

            if (!isEmpty(requestParams))
                log.info(REQUEST_PARAMS_LOG_MESSAGE, requestParams);

            if (!isEmpty(requestBody))
                log.info(REQUEST_BODY_LOG_MESSAGE, requestBody);

        } catch (IOException e) {
            log.error(FAILED_TO_LOG_REQUEST_MESSAGE, e);
        }
    }
```

#### 2. Response Logging

Response logging captures HTTP status codes:

```java
private void logResponse(ServletResponse servletResponse) {
    HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
    log.info("Response code sent: {}", httpResponse.getStatus());
}
```

#### 3. RestTemplate Logging

Outgoing HTTP requests are logged using `RestTemplateLoggingInterceptor`:

```java
    
    private void logRequest(HttpRequest httpRequest, byte[] body) {
        if (tracerProperties.isRestTemplateDetailedLoggingEnabled() && 
            isBodyCompatibleForParsing(httpRequest)) {
            log.info("Sending request to {} with verb {} with body {}", 
                httpRequest.getURI(), httpRequest.getMethod().name(), getBody(body));
        } else {
            log.info("Sending request to {} with verb {}", 
                httpRequest.getURI(), httpRequest.getMethod().name());
        }
    }
    
    private void logResponse(ClientHttpResponse response, HttpRequest httpRequest) {
        if (tracerProperties.isRestTemplateDetailedLoggingEnabled() && 
            isBodyCompatibleForParsing(httpRequest)) {
            String body = getBodyString(response);
            log.info("Received from {} response code {} and body {}", 
                httpRequest.getURI(), response.getStatusCode(), body);
        } else {
            log.info("Received response from {}", httpRequest.getURI());
        }
    }

```

#### Example: RestTemplate.postForObject

```java
@Autowired
private RestTemplate restTemplate;

@PostMapping("/external")
public ResponseEntity<String> callExternal(@RequestBody MyRequest req) {
    String url = "http://external-service/api/resource";
    MyResponse res = restTemplate.postForObject(url, req, MyResponse.class);
    return ResponseEntity.ok("OK: " + res.getResult());
}
```

### Kafka Logging

#### 1. Producer Logging

Kafka message production is logged using `LogAwareKafkaTemplate`:

```java
    private void logSuccessMessage(V message, SendResult<K, V> result) {
        final String topic = result.getProducerRecord().topic();
        final Integer partition = result.getProducerRecord().partition();
        final String key = ObjectUtils.nullSafeToString(result.getProducerRecord().key());
        if (tracerProperties.isKafkaMessageLoggingEnabled()) {
            final String bodyAsJsonString = getMessageBodyAsJsonString(message);
            log.info(SEND_SUCCESS_MESSAGE_WITH_BODY, topic, partition, bodyAsJsonString, key);
        } else {
            log.info(SEND_SUCCESS_MESSAGE, topic, partition, key);
        }
    }
```

#### 2. Consumer Logging

Kafka message consumption is logged using `KafkaTemplateLoggingInterceptors`:

```java
    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> consumerRecords) {
        for (ConsumerRecord<K, V> consumerRecord : consumerRecords) {
            final String keyAsString = ObjectUtils.nullSafeToString(consumerRecord.key());
            String correlationId = getCorrelationIdFromBody(consumerRecord.value());

            if (!isEmpty(correlationId))
                MDC.put(CORRELATION_ID_MDC, correlationId);

            if (log.isDebugEnabled()) {
                final String bodyAsJsonString = getMessageBodyAsJsonString(consumerRecord.value());
                log.debug(RECEIVED_MESSAGE_WITH_BODY, consumerRecord.topic(), consumerRecord.partition(), bodyAsJsonString,
                        keyAsString);
            } else {
                log.info(RECEIVED_MESSAGE, consumerRecord.topic(), consumerRecord.topic(), consumerRecord.key());
            }
        }
        return consumerRecords;
    }
```

#### 3. Kafka Error Logging

Kafka operation failures are logged with detailed error information:

```java
    @Override
    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {
        if (!isNull(e)) {
            final String message =
                    String.format(SEND_FAILURE_MESSAGE, recordMetadata.topic(), recordMetadata.partition());
            log.error(message, e);
        }
    }
```

### Database Call Logging

Database operations are logged using RestTemplateLoggingInterceptor



### Correlation ID Context

All logging automatically includes correlation ID context through MDC (Mapped Diagnostic Context)



### Logging Configuration Parameters

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `tracer.requestLoggingEnabled` | Enable HTTP request body logging | `false` | No |
| `tracer.kafkaMessageLoggingEnabled` | Enable Kafka message body logging | `false` | No |
| `tracer.restTemplateDetailedLoggingEnabled` | Enable RestTemplate detailed logging | `false` | No |
| `tracer.filterSkipPattern` | URL patterns to skip tracing | `/health,/metrics` | No |
| `logging.pattern.console` | Logging pattern with correlation ID | See integration guide | Yes |

### Logging Patterns

Recommended logging pattern for correlation ID inclusion:

```properties
logging.pattern.console=%clr(%X{CORRELATION_ID:-}) %clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(${LOG_LEVEL_PATTERN:-%5p}) %clr(${PID:- }){magenta} %clr(---){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}
```


## OpenTelemetry Integration

### Why OpenTelemetry and Micrometer
- Jaeger client libraries are deprecated and not compatible with JDK 17.
- OpenTelemetry provides end-to-end tracing across HTTP and Kafka boundaries.
- Micrometer adds standard service metrics (Tomcat threads, DB pools, Kafka clients) for better operational insights.

### Version Note
- OpenTelemetry and Micrometer integrations are available from tracer `v2.9.1-SNAPSHOT`.

### pom.xml Changes
1) Use tracer `2.9.1-SNAPSHOT`.
2) Import OpenTelemetry BOMs to align versions of OTEL artifacts used transitively by services.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-bom</artifactId>
      <version>1.35.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry.instrumentation</groupId>
      <artifactId>opentelemetry-instrumentation-bom-alpha</artifactId>
      <version>2.1.0-alpha</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### Application Properties (Tracing and Micrometer)
Add these properties to enable OTEL tracing and Kafka instrumentation. Adjust values per service.

```properties
# Core OTEL exporters and service identity
otel.traces.exporter=otlp
otel.service.name=your-service-name
otel.logs.exporter=none
otel.metrics.exporter=none

# OTLP exporter target (Jaeger collector)
otel.exporter.otlp.endpoint=http://jaeger-collector.tracing:4318
otel.exporter.otlp.protocol=http/protobuf

# Instrumentation
otel.instrumentation.kafka.enabled=true
otel.instrumentation.kafka.experimental-span-attributes=true

# Reduce noise by ignoring health/metrics endpoints (customize per service)
otel.instrumentation.http.server.ignore-urls=/your-service/health,/your-service/prometheus
```

Notes
- Set `otel.service.name` to the exact service name for correct grouping in Jaeger/Grafana Tempo.
- Health and metrics endpoints should be excluded via `otel.instrumentation.http.server.ignore-urls` to reduce trace noise.

### Database Query Tracing
To enable JDBC tracing:

1) Update the JDBC URL to the OTEL variant (env/values file):

```yaml
db-otel-url: "jdbc:otel:postgresql://<host>:<port>/<database>"
```

2) Point your service's `SPRING_DATASOURCE_URL` to this OTEL URL.

Note
- Prepending `jdbc:otel:` enables OTEL JDBC instrumentation for DB spans.

### Property Reference
- otel.traces.exporter: Use `otlp` to send traces to an OTLP-compatible backend (e.g., Jaeger collector).
- otel.service.name: Logical service name used to group and search traces.
- otel.logs.exporter: Set to `none` when logs are not exported via OTEL.
- otel.metrics.exporter: Set to `none` when metrics are handled by Micrometer/Prometheus.
- otel.exporter.otlp.endpoint: HTTP endpoint of the collector.
- otel.exporter.otlp.protocol: Usually `http/protobuf` for Jaeger OTLP.
- otel.instrumentation.kafka.enabled: Enables Kafka producer/consumer spans.
- otel.instrumentation.kafka.experimental-span-attributes: Adds richer Kafka span attributes.
- otel.instrumentation.http.server.ignore-urls: Comma-separated list of paths to exclude from tracing.


## Configuration

### Application Properties

Complete configuration example:

```properties
# Tracer Configuration
tracer.filter.enabled=true
tracer.opentracing.enabled=false

# Logging Configuration
tracer.requestLoggingEnabled=false
tracer.kafkaMessageLoggingEnabled=false
tracer.restTemplateDetailedLoggingEnabled=false

# Error Handling Configuration
tracer.errors.publish=false
tracer.errors.topic=egov-error
tracer.errorDetails.topic=error-details-indexer-topic
tracer.errorDetails.publishFlag=false
tracer.errors.provideExceptionInDetails=false

# Filter Configuration
tracer.filterSkipPattern=/api-docs.*|/autoconfig|/configprops|/dump|/health|/info|/metrics.*|/mappings|/swagger.*|.*\.png|.*\.css|.*\.js|.*\.html|/favicon.ico|/hystrix.stream|/prometheus|/manage/*

# Logging Pattern
logging.pattern.console=%clr(%X{CORRELATION_ID:-}) %clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(${LOG_LEVEL_PATTERN:-%5p}) %clr(${PID:- }){magenta} %clr(---){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}

# Kafka Configuration
spring.kafka.properties.interceptor.classes=org.egov.tracer.kafka.KafkaTemplateLoggingInterceptors
spring.kafka.producer.acks=all
spring.kafka.producer.linger.ms=100



# Timezone Configuration
app.timezone=UTC
```

### Configuration Properties Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracer.filter.enabled` | boolean | `true` | Enable tracer filter |
| `tracer.opentracing.enabled` | boolean | `false` | Enable OpenTelemetry tracing |
| `tracer.requestLoggingEnabled` | boolean | `false` | Enable HTTP request body logging |
| `tracer.kafkaMessageLoggingEnabled` | boolean | `false` | Enable Kafka message body logging |
| `tracer.restTemplateDetailedLoggingEnabled` | boolean | `false` | Enable RestTemplate detailed logging |
| `tracer.errors.publish` | boolean | `false` | Enable error publishing to DLQ |
| `tracer.errors.topic` | String | `egov-error` | DLQ topic name |
| `tracer.errorDetails.topic` | String | `error-details-indexer-topic` | Error details topic |
| `tracer.errorDetails.publishFlag` | boolean | `false` | Enable error details publishing |
| `tracer.errors.provideExceptionInDetails` | boolean | `false` | Include exception details in response |
| `tracer.filterSkipPattern` | String | `/health,/metrics` | URL patterns to skip tracing |

## Integration Guide

### Maven Dependency

Add the tracer library to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>repo.egovernments.org</id>
        <name>eGov ERP Releases Repository</name>
        <url>http://repo.egovernments.org/nexus/content/repositories/releases/</url>
    </repository>
</repositories>

<dependency>
    <groupId>org.egov.services</groupId>
    <artifactId>tracer</artifactId>
    <version>2.9.1-SNAPSHOT</version>
</dependency>
```

### Spring Boot Integration

#### 1. Import Tracer Configuration

Add the `@Import` annotation to your main application class:

```java
@SpringBootApplication
@Import({TracerConfiguration.class})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

#### 2. Configure Application Properties

Add the required configuration to `application.properties`:

```properties
# Required logging pattern
logging.pattern.console=%clr(%X{CORRELATION_ID:-}) %clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(${LOG_LEVEL_PATTERN:-%5p}) %clr(${PID:- }){magenta} %clr(---){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}

# Enable desired features
tracer.requestLoggingEnabled=true
tracer.kafkaMessageLoggingEnabled=true
tracer.restTemplateDetailedLoggingEnabled=true
tracer.errors.publish=true
```

#### 3. Use Tracer Components

**For HTTP Requests:**
```java
@Autowired
private RestTemplate restTemplate;

@PostMapping("/external")
public ResponseEntity<String> callExternalService(@RequestBody MyRequest request) {
    // The correlation ID will be automatically added to headers and the request/response will be logged by the tracer library
    String url = "http://external-service/api/resource";
    MyResponse response = restTemplate.postForObject(url, request, MyResponse.class);

    // Do something with the response
    return ResponseEntity.ok("External call successful: " + response.getResult());
}
```
Notes:
    The tracer library will automatically log the outgoing request, response, and correlation ID.
    No extra code is needed for correlation ID propagation or logging if the tracer is properly integrated.

**For Kafka Operations:**
```java

 @Autowired
private CustomKafkaTemplate<String, Object> kafkaTemplate;

public void sendMessage(String topic, Object message) {
    kafkaTemplate.send(topic, message);
}

@KafkaListener(topics = "my-topic")
public void receiveMessage(@Payload Object message, 
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    // Process message
}
```



## API Reference

### Core Classes

#### TracerConfiguration
Main configuration class that sets up all tracer components.

#### TracerProperties
Configuration properties holder for all tracer settings.

#### ExceptionAdvise
Global exception handler that processes all exceptions and manages DLQ publishing.

#### ErrorQueueProducer
Component responsible for publishing errors to Kafka DLQ topics.

#### LogAwareKafkaTemplate
Enhanced Kafka template with automatic logging and correlation ID management.

#### RestTemplateLoggingInterceptor
Interceptor for RestTemplate that adds correlation IDs and logs requests/responses.

#### TracerFilter
HTTP filter that manages correlation IDs and request/response logging.

### Models

#### ErrorQueueContract
Structure for error messages sent to DLQ:
```java
public class ErrorQueueContract {
    private String id;                    // Unique error ID
    private String source;                // Error source
    private Object body;                  // Request body
    private Long ts;                      // Timestamp
    private ErrorRes errorRes;            // Error response
    private List<StackTraceElement> exception; // Stack trace
    private String message;               // Error message
    private String correlationId;         // Correlation ID
}
```

#### ErrorRes
Standard error response structure:
```java
public class ErrorRes {
    private List<Error> errors;
    // ... other fields
}
```

#### Error
Individual error structure:
```java
public class Error {
    private String code;
    private String message;
    private String description;
    private String params;
}
```

### Constants

#### TracerConstants
Key constants used throughout the library:
```java
public class TracerConstants {
    public static final String CORRELATION_ID_HEADER = "x-correlation-id";
    public static final String CORRELATION_ID_MDC = "CORRELATION_ID";
    public static final String TENANT_ID_HEADER = "tenantId";
    public static final String TENANTID_MDC = "TENANTID";
    // ... other constants
}
```

### Annotations

#### @Import({TracerConfiguration.class})
Required annotation to import tracer configuration into Spring Boot application.

### Dependencies

#### Required Dependencies
- Spring Boot Web Starter
- Spring Kafka
- Apache Kafka Clients
- Jackson (JSON processing)
- Logback (logging)
- Lombok (code generation)

#### Optional Dependencies
- OpenTelemetry (for distributed tracing)
- Jaeger (tracing backend)

---



