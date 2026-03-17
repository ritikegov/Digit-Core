# Local Setup

This guide helps you run `pg-service` locally with minimum required dependencies.

## Prerequisites

- Java 17
- Maven 3.8+
- Postgres DB

Optional (only when messaging broker is enabled):

- Redis/Kafka

## 1) Start infrastructure

Clone the [Digit-Core repository](https://github.com/egovernments/Digit-Core).

## 2) Start infrastructure

Start PostgreSQL locally and ensure the credentials in `src/main/resources/application.properties` are valid.

Default properties already present:

```ini
spring.datasource.url=jdbc:otel:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=postgres

spring.flyway.url=jdbc:postgresql://localhost:5432/postgres
spring.flyway.user=postgres
spring.flyway.password=postgres
```

## 2) Configure dependent services

By default, service clients point to local endpoints:

```ini
idgen.host=http://localhost:8100/
billing.host=http://localhost:
registry.host=http://localhost:8085/
individual.host=http://localhost:8999/
```

Update these hosts/ports as per your running environment.

## 3) Configure gateway flags for local testing

At least one gateway should be active for non-zero amount transactions:

```ini
axis.active=true
paytm.active=false
phonepe.active=false
payu.active=true
```

> For zero amount transactions (`txnAmount=0`), gateway redirect is skipped.

## 4) Run the service

From `pg-service` folder:

```bash
mvn clean spring-boot:run
```

Service starts at:

- `http://localhost:9000/pg-service`

## 5) Verify APIs quickly

Use the updated postman collection at:

- `postman/PaymentGateway-3.0.postman_collection.json`

Or call directly:

- `GET http://localhost:9000/pg-service/gateway/v3/_search`
