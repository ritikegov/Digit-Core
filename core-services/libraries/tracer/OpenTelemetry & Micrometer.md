## Limitations in current Monitoring and Tracing:

### 1\. Only partial tracing is available

* **Description**: While some traces may exist for incoming requests, they stop once the request finishes writing a message to Kafka. Any subsequent handling on the consumer side remains untraced.  
* **Why it’s a problem**:  
  * **Missing End-to-End Visibility**: Without tracing beyond the Kafka boundary, it’s impossible to see how a request flows through the entire system—from one microservice into Kafka, and from there to any consuming service. This gap in the trace makes root cause analysis difficult when issues arise.  
  * **Incomplete Latency Analysis**: You can’t accurately measure the time spent in different stages (producer vs. consumer) if traces end before consumer processing begins.  
  * **Harder Debugging**: When investigating performance bottlenecks or errors, you won’t have a full picture of where a request failed or got delayed.

### 2\. Service-related metrics like DB connections, Tomcat threads, Kafka producers, and consumers are not available

* **Description**: The current setup does not provide standard service metrics, such as database connection pool usage, thread pool usage for Tomcat, or Kafka producer/consumer metrics (e.g., queue size, lag, or processing rates).  
* **Why it’s a problem**:  
  * **Limited Operational Insights**: Without these metrics, you cannot easily detect resource saturation (e.g., running out of database connections or Tomcat threads).  
  * **Performance Tuning Difficulty**: Metrics on DB connections, threading, and Kafka help in understanding where bottlenecks occur—tuning thread pools, adjusting Kafka consumer concurrency, or scaling databases.  
  * **Proactive Issue Detection**: Having no insight into Kafka producer/consumer metrics means you can’t see if the consumers are falling behind (consumer lag) or if producers are experiencing frequent retries.

### 3\. No unified view of tracing, logs, and metrics

* **Description**: Traces, logs, and metrics are collected (if at all) in separate silos, making it hard to correlate data for debugging or analysis.  
* **Why it’s a problem**:  
  * **Inefficient Troubleshooting**: Engineers must hop between different tools (e.g., one for logs, another for traces, yet another for metrics), manually correlating data using timestamps or other context.  
  * **Lack of Context**: A single transaction might produce relevant logs, metrics, and a distributed trace; having them in disconnected places means you lose the complete narrative of what happened during the request.  
  * **Slower Incident Response**: When an issue occurs, time to resolution is increased because teams cannot quickly unify the relevant telemetry data to identify cause and effect.

### 

### **How Adopting OpenTelemetry and Micrometer Helps**

* **End-to-End Tracing**: By instrumenting Kafka producers and consumers, you can maintain the trace context across message boundaries, ensuring full visibility from the first service to any downstream services.  
* **Detailed Metrics**: Micrometer provides out-of-the-box instrumentation for common components (Tomcat threads, DB connection pools, Kafka, etc.), giving you critical performance insights.  
* **Unified Observability**: OpenTelemetry’s approach—along with backends like Jaeger, Grafana, or Prometheus—allows you to bring traces, metrics, and even logs into a single pane of glass. This unified view accelerates troubleshooting and root cause analysis.

## Migrating from Jaeger Client to OpenTelemetry and Micrometer

***Deprecation Note***  
*As announced in the official [Jaeger Client Deprecation Notice](https://www.jaegertracing.io/docs/1.17/client-libraries/#deprecating-jaeger-clients), the Jaeger client is deprecated and does not support JDK 17\.*

***New Approach***  
*OpenTelemetry and Micrometer libraries, along with their configurations, have been integrated into the tracer and are available in `v2.9.1-SNAPSHOT`.*

***Enabling Tracing and Metrics***  
*To enable tracing and Micrometer metrics in any service, you must:*

1. *Update your `pom.xml` to include the required dependencies.*  
2. *Add specific configuration properties to your `application.properties` file.*

### Changes in pom.xml:

1. Update the tracer library version to 2.9.1-SNAPSHOT.

   ![][image1]  
2. Add dependency management for otel libraries to set the correct version of child libraries. Add the below :

|  \<dependencyManagement\>    \<dependencies\>      \<dependency\>        \<groupId\>io.opentelemetry\</groupId\>        \<artifactId\>opentelemetry-bom\</artifactId\>        \<version\>1.35.0\</version\>        \<type\>pom\</type\>        \<scope\>import\</scope\>      \</dependency\>      \<dependency\>        \<groupId\>io.opentelemetry.instrumentation\</groupId\>        \<artifactId\>opentelemetry-instrumentation-bom-alpha\</artifactId\>        \<version\>2.1.0-alpha\</version\>        \<type\>pom\</type\>        \<scope\>import\</scope\>      \</dependency\>    \</dependencies\>  \</dependencyManagement\> |
| :---- |

### Changes in application.properties:

New parameters need to be added in application.properties to provide the required configuration for micrometer and open telemetry. Following are the parameters that needs to be added:

| otel.traces.exporter\=otlpotel.service.name\=egov-idgenotel.logs.exporter\=noneotel.metrics.exporter\=noneotel.exporter.otlp.endpoint\=http://jaeger-collector.tracing:4318otel.exporter.otlp.protocol\=http/protobufotel.instrumentation.kafka.enabled\=trueotel.instrumentation.kafka.experimental-span-attributes\=trueotel.instrumentation.http.server.ignore-urls\=/egov-idgen/health,/egov-idgen/promethus |
| :---- |

*Note: Changes the otel.service.name and otel.instrumentation.http.server.ignore-urls based on your service name. Other parameters can be added in helm charts*

Add the otel DB url in env file:

| db-otel-url: "jdbc:otel:postgresql://postgresql-lts.egov:5432/postgres" |
| :---- |

Point the SPRING\_DATASOURCE\_URL of the service which has tracing enabled to the above url.

Note: For DB queries tracing the DB url needs to be updated by adding otel after jdbc in the url as shown above.

Following is the definition of the parameters:

| Property | Description |
| ----- | ----- |
| otel.traces.exporter | **What it does**: Assigns a service name to the application for trace grouping. **Why it's used:** Backends (e.g., Jaeger, Zipkin) use this service name to organize and display telemetry data. |
| otel.service.name | **What it does**: Assigns a service name to the application for trace grouping. **Why it's used**: Backends (e.g., Jaeger, Zipkin) use this service name to organize and display telemetry data. |
| otel.logs.exporter | **What it does**: Disables the export of log data. **Why it's used**: If you do not want to send logs via OpenTelemetry (or do not have a suitable collector/log management configuration), you can disable log exporting. |
| otel.metrics.exporter | **What it does**: Disables the export of metric data through OpenTelemetry. **Why it's used**: In some setups, metrics may be handled by Micrometer or another system. This configuration ensures no metric data is sent via OTLP. |
| otel.exporter.otlp.endpoint | **What it does**: Sets the endpoint for sending OTLP data. In this case, it points to the Jaeger collector’s OTLP endpoint. **Why it's used**: The traces (and any other OTLP data if enabled) will be sent to Jaeger for storage and visualization. |
| otel.exporter.otlp.protocol | **What it does**: Specifies the protocol and serialization format (protobuf) for OTLP exports. **Why it's used**: Ensures that data sent to the collector is in the correct format (HTTP over Protobuf) expected by the Jaeger or OTLP-compatible endpoint. |
| otel.instrumentation.kafka.enabled=true	 | **What it does**: Enables instrumentation for Kafka producers and consumers. **Why it's used**: Automatically creates traces for Kafka interactions (producing, consuming messages), which can help you understand messaging flow in distributed systems |
| otel.instrumentation.kafka.experimental-span-attributes | **What it does**: Includes additional, possibly more detailed or experimental, span attributes for Kafka instrumentation. **Why it's used**: Provides more visibility into Kafka operations for debugging or performance analysis |
| otel.instrumentation.http.server.ignore-urls | **What it does**: Configures the HTTP server instrumentation to skip tracing for the specified endpoints (health check, Prometheus endpoint, etc.). **Why it's used**: Health and metrics endpoints often create unneeded trace noise, so they’re excluded |

[image1]: <data:image/png;base64,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>