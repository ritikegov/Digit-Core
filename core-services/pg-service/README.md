# Payment Gateway (pg-service)

`pg-service` acts as a liaison between DIGIT modules and external payment gateways. It handles transaction initiation, callback/update processing, transaction lookup, and payment registration in Billing.

## Runtime and Tech Stack

- Java 17
- Spring Boot 3.2.2
- PostgreSQL + Flyway migrations
- Quartz scheduler (reconciliation jobs)
- Optional Redis/Kafka message broker integration

## Service Endpoints (v3)

Base context path:

- `/pg-service`

Primary APIs:

- `POST /transaction/v3/_create`
- `GET /transaction/v3/_search`
- `PUT /transaction/v3/_update`
- `GET /gateway/v3/_search`

### Required headers

- `X-Tenant-ID` (required on create/search/update)
- `X-Client-ID` (required on create/update)

## Request/Response Notes

### Create Transaction

- Request body contains only `Transaction` (no `RequestInfo` envelope in v3).
- `tenantId` is read from `X-Tenant-ID` header and set by the service.
- `txnAmount` must equal sum of all `taxAndPayments[].amountPaid`.
- If `txnAmount = 0`, gateway redirect is skipped and payment is registered directly.

### Update Transaction

- Expects gateway callback params as query parameters.
- Transaction ID is discovered from gateway-specific transaction-id keys (case-insensitive).
- If transaction is successful and amount matches original amount, payment is registered.

### Search Transaction

- Supports filters like `txnId`, `billId`, `userUuid`, `receipt`, `consumerCode`, `txnStatus`.
- Current implementation applies fixed pagination (`offset=0`, `limit=5`).

### Available Gateways

Returns active gateways discovered from configured gateway implementations.

## Supported Gateway Integrations

- AXIS
- PAYTM
- PHONEPE
- PAYU

Gateway activation is controlled via `*.active` flags in `application.properties`.

## Reconciliation Jobs

Quartz-based jobs are available for pending-transaction reconciliation:

- Early reconciliation (interval-based; default every 15 minutes)
- Daily reconciliation

## Messaging Topics (when broker enabled)

When `message.broker.enabled=true`, transaction create/update events are pushed to:

- `messaging.broker.topic.create.txn`
- `messaging.broker.topic.update.txn`

Default values in this repo:

- `create-pg-txn`
- `update-pg-txn`

## API Contracts and Collection

- Swagger/OpenAPI contract: `pg-service/egov-pg-service.yml`
- Postman collection: `pg-service/postman/PaymentGateway-3.0.postman_collection.json`