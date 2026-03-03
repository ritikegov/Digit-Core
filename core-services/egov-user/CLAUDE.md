# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build the project
mvn clean install

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=UserControllerTest

# Run the application locally
mvn spring-boot:run
```

## Architecture Overview

This is a **Spring Boot 1.5.x microservice** (Java 8) that manages user lifecycle, authentication, and authorization for the DIGIT platform. It is an **OAuth2 authorization server** backed by PostgreSQL with Redis for token storage.

### Layered Structure

```
web/controller/     → REST endpoints (UserController, PasswordController, LogoutController)
domain/service/     → Business logic (UserService, TokenService, MobileNumberValidator)
persistence/        → Data access via JDBC (UserRepository, RoleRepository, etc.)
repository/         → Query builders and ResultSet extractors
security/           → OAuth2 config and custom authentication providers
```

### Key Design Decisions

- **OAuth2 Token Storage:** Redis (`spring.redis.host`), not JWT — tokens are stateful.
- **PII Encryption:** All sensitive fields (mobile, email, Aadhaar) are encrypted via `egov-enc-service` before persistence. `EncryptionDecryptionUtil` wraps all encrypt/decrypt calls. ABAC-based decryption is controlled by `decryption.abac.enabled`.
- **Dual Login Flow:** Citizens use OTP-based login; employees use password-based login. `CustomAuthenticationProvider` branches on user type. Controlled by `citizen.login.password.otp.enabled` and `employee.login.password.otp.enabled` properties.
- **Database Migrations:** Flyway manages schema under `src/main/resources/db/migration/`. Flyway is **disabled by default** (`flyway.enabled=false`) — migrations must be run manually or enabled explicitly.
- **Multi-tenancy:** `tenantId` is a first-class field on users and roles. `MultiStateInstanceUtil` from the tracer library handles central-instance vs. state-level routing.
- **Audit Logging:** User decryption calls are published to the `audit_data` Kafka topic.
- **Mobile Validation:** `MobileNumberValidator` calls MDMS-v2 to validate mobile numbers against state-configured regex patterns.

### External Service Dependencies

| Service | Property | Purpose |
|---------|----------|---------|
| egov-enc-service | `egov.enc.host` | PII field encryption/decryption |
| egov-mdms-service | `egov.mdms.host` / `egov.mdms.v2.host` | Roles, mobile validation rules |
| egov-otp | `egov.otp.host` | OTP generation and validation |
| egov-accesscontrol | `egov.services.accesscontrol.host` | Role-based access control |
| egov-filestore | `egov.filestore.host` | File storage |

For local development, port-forward these from a running cluster:
```bash
function kgpt(){kubectl get pods -n egov --selector=app=$1 --no-headers=true | head -n1 | awk '{print $1}'}
kubectl port-forward -n egov $(kgpt egov-enc-service) 8087:8080 &
kubectl port-forward -n egov $(kgpt egov-mdms-service) 8088:8080 &
kubectl port-forward -n egov $(kgpt egov-otp) 8089:8080
kubectl port-forward -n egov $(kgpt egov-accesscontrol) 8090:8080
```

Then update `application.properties`:
```ini
egov.enc.host=http://localhost:8087/
egov.mdms.host=http://localhost:8088/
egov.otp.host=http://localhost:8089/
egov.services.accesscontrol.host=http://localhost:8090/
```

### Infrastructure Requirements

- **PostgreSQL** on `localhost:5432` (default DB: `postgres`, user: `postgres`, password: `postgres`)
- **Redis** on `localhost:6379`
- **Kafka** on `localhost:9092` with Zookeeper running

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/citizen/_create` | Create citizen (with OTP validation) |
| POST | `/users/_createnovalidate` | Create user (no OTP) |
| POST | `/_search` | Search users (defaults to active only) |
| POST | `/v1/_search` | Search users (no active default) |
| POST | `/_details` | Get user by access token |
| POST | `/users/_updatenovalidate` | Update user (no OTP; username/type/tenantId ignored) |
| POST | `/profile/_update` | Partial profile update |
| POST | `/password/_update` | Change password (logged-in user) |
| POST | `/password/nologin/_update` | Reset password via OTP |
| POST | `/_logout` | Logout session |
| POST | `/user/oauth/token` | Login (OTP for citizens, password for employees) |

### Test Configuration

Tests use H2 in-memory database. Test SQL fixtures are in `src/test/resources/sql/` for setup (`createUsers.sql`, etc.) and teardown (`clearUsers.sql`, etc.). The test `application.properties` points to a local PostgreSQL on port 5433.
