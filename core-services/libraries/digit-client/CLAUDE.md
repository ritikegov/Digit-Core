# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Compile
mvn clean compile

# Run tests (when added)
mvn test

# Package JAR
mvn clean package

# Deploy to Nexus (requires credentials)
mvn -B test verify deploy -s settings.xml \
  -Dnexus.user=USERNAME -Dnexus.password=PASSWORD

# Run example class
mvn exec:java -Dexec.mainClass="com.digit.example.DigitClientExample"

# Docker build
docker build --build-arg WORK_DIR=. \
  --build-arg nexusUsername=USERNAME \
  --build-arg nexusPassword=PASSWORD .
```

**Artifact**: `org.egov.services:digit-client:1.0.7-SNAPSHOT` — deployed to Nexus at `nexus-repo.egovernments.org`.

## Architecture Overview

This is a **Spring Boot auto-configurable client library** for DIGIT microservices. Consumer services add it as a Maven dependency and get type-safe HTTP clients for 10+ platform services (Boundary, Workflow, Individual, MDMS, IdGen, Filestore, Notification, Billing, Registry, etc.).

### Core Abstractions

**Service Clients** (`src/main/java/com/digit/services/`): Each microservice has a dedicated client class (e.g., `BoundaryClient`, `WorkflowClient`) that wraps RestTemplate calls with typed request/response models. Clients are Spring beans; inject them directly in Spring apps, or create them via `DigitClientFactory` in non-Spring contexts.

**Auto-Configuration** (`config/`): Two auto-configurations registered in `META-INF/spring.factories`:
- `HeaderPropagationAutoConfiguration` — registers all service clients and the header interceptor as Spring beans when the library is on the classpath.
- `OpenTelemetryAutoConfiguration` — conditionally creates the OTel SDK when `digit.opentelemetry.enabled=true`.

**Header Propagation**: `HeaderPropagationInterceptor` intercepts every outbound RestTemplate call and copies headers from the current servlet request (via `HeaderStore` → `RequestContextHolder`). Which headers to propagate is controlled by `PropagationProperties` (allow-list + prefix matching).

**OpenTelemetry Tracing**: `OpenTelemetryRestTemplateInterceptor` creates a `CLIENT` span for each RestTemplate call, injects W3C `traceparent`/`tracestate` headers, and exports spans via OTLP. All OTel code is optional-dependency-guarded.

**Error Handling**: `DigitClientErrorHandler` maps HTTP 4xx/5xx responses to `DigitClientException` with typed error codes (e.g., `RESOURCE_NOT_FOUND`, `UNAUTHORIZED`, `INTERNAL_SERVER_ERROR`).

### Key Configuration Properties

```properties
# Service base URLs
digit.services.boundary.base-url=http://localhost:8080
digit.services.workflow.base-url=http://localhost:8085
digit.services.individual.base-url=http://localhost:8999
digit.services.idgen.base-url=http://localhost:8100
# (plus account, filestore, mdms, notification, registry, billing)

# Timeouts
digit.services.timeout.connect=5000
digit.services.timeout.read=30000

# Header propagation
digit.propagate.headers.allow=authorization,x-correlation-id,x-request-id,x-tenant-id,x-client-id
digit.propagate.headers.prefixes=x-ctx-,x-trace-

# OpenTelemetry (all optional)
digit.opentelemetry.enabled=true
digit.opentelemetry.service-name=my-service
digit.opentelemetry.endpoint=http://localhost:4317
digit.opentelemetry.sampling-ratio=1.0
```

### Java & Dependency Notes

- Java 17, Spring Framework 6.2, Spring Boot 3.x
- Lombok for boilerplate — ensure annotation processing is enabled in IDE
- All OTel dependencies are `optional`; Spring Boot auto-config dependencies are `optional`; Jakarta Servlet API is `provided`
- No test sources exist yet (`src/test/` is absent); Surefire plugin is configured for when tests are added

### Adding a New Service Client

1. Create a package under `src/main/java/com/digit/services/<service>/`
2. Add request/response model POJOs with `@JsonIgnoreProperties(ignoreUnknown = true)` and Lombok annotations
3. Create the client bean using `RestTemplate` injected from `ApiConfig`
4. Add a `@Bean` method for the new client in `HeaderPropagationAutoConfiguration`
5. Add a corresponding `base-url` property to `ApiProperties`
