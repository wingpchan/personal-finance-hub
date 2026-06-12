# Personal Finance Hub — Microservices Portfolio Project

A microservices-based personal finance management system built with Java and Spring Boot,
demonstrating enterprise-grade design patterns including effective dating, audit trails,
JWT authentication, role based access control and RESTful API design.

---

## Architecture

The system is composed of independent microservices, each owning its own data and
exposing a REST API:

- **user-service** — User registration, authentication and profile management *(complete)*
- **account-service** — Bank accounts and balances *(planned)*
- **transaction-service** — Income and expense tracking *(planned)*
- **notification-service** — Budget alerts and notifications *(planned)*
- **api-gateway** — Single entry point routing to all services *(planned)*

---

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 17 | Core language |
| Spring Boot 3.5 | Microservice framework |
| Spring Data JPA | Database access layer |
| Spring Security | JWT authentication and role based access control |
| Spring Boot DevTools | Automatic restart on code changes |
| PostgreSQL | Persistent relational database |
| Hibernate | ORM and schema management |
| Lombok | Boilerplate reduction |
| Bean Validation | Input validation |
| JJWT | JWT token generation and validation |
| Maven | Dependency management |

---

## Development Approach

This microservice was developed collaboratively with Claude (Anthropic's AI assistant)
as a learning and portfolio exercise.

The development process involved:
- **AI assisted code generation** — Claude produced initial implementations
- **Architectural review** — design decisions challenged, questioned and refined
- **Feature driven development** — requirements evolved through conversation
- **Debugging and testing** — issues identified and resolved through active testing
- **Security hardening** — vulnerabilities identified and addressed iteratively

This reflects the modern reality of software development where experienced developers
leverage AI tools to accelerate delivery while applying their domain knowledge,
architectural thinking and quality standards to validate and improve the output.

### Developer Driven Decisions

The following features and improvements were specifically driven by developer
input, challenge and domain knowledge during the development process:

**Architecture & Data Design**
- Proposed effective dating pattern over simple record overwrite — providing
  complete immutable audit history of all user changes
- Challenged timestamp boundary precision — identified that plusNanos(1) was
  insufficient for PostgreSQL microsecond precision, leading to plusNanos(1000)
- Proposed separation of operational and reporting databases (CQRS pattern)
  for production audit reporting
- Identified need for composite key (ID + effectiveDate) to support
  versioned records

**Security**
- Drove addition of role based access control (ROLE_USER, ROLE_ADMIN)
  beyond basic authentication
- Identified admin bootstrapping security risk of hardcoded credentials,
  leading to environment variable based configuration via Spring profiles
- Proposed admin user lifecycle process — seed, create human admin,
  delete seeded admin, rotate credentials
- Challenged password validation to cover full history across all previous
  versions, not just the current record

**Data Quality**
- Identified email case sensitivity issue — drove normalisation to lowercase
  on storage and case insensitive querying
- Proposed user lifecycle status model (PENDING_VERIFICATION, ACTIVE,
  SUSPENDED, INACTIVE, CLOSED) reflecting real world financial system requirements
- Drove addition of effective dating to password reset and change password
  flows for complete audit trail
- Identified ambiguous search parameter combinations, leading to explicit
  validation and rejection of conflicting parameters

**Code Quality**
- Identified double negative in isTokenExpired() — refactored to
  isTokenStillValid() for clarity and maintainability
- Drove addition of JavaDoc to all important classes and methods
- Challenged YAGNI principle — removed unused extractRole() method
- Proposed package restructuring into logical sub-packages for
  maintainability and professionalism

### Future Services Methodology

Subsequent microservices in this project will deliberately use different
development methodologies to demonstrate broader capability:

| Service | Methodology | Purpose |
|---|---|---|
| `user-service` | AI assisted, iterative | Microservice foundations |
| `account-service` | TDD | Test driven development with JUnit 5 and Mockito |
| `transaction-service` | SDD with Speckit | Spec driven development |
| `notification-service` | Event driven | Async messaging with Kafka or RabbitMQ |
--- 

## Design Decisions

### Effective Dating
Rather than overwriting user records on update, the system creates a new version
of the record with a new effective date. The previous version is closed off with
an end date. This provides a complete, immutable history of all changes.

- `endDate = null` identifies the current active record
- `effectiveDate` marks when each version became active (LocalDateTime precision)
- Updates close the current record and insert a new version with `effectiveDate + 1 microsecond`
- Deletes are soft — the record is closed off, never physically removed
- Reinstatement creates a new active version from the latest closed record

### Audit Fields
Every record captures:
- `createdAt` / `createdBy` — set once on creation, never updated
- `updatedAt` / `updatedBy` — updated on every change

### Password Security
- Passwords are hashed using BCrypt before storage
- Plain text passwords are never persisted
- Password history is validated across all previous versions
- Users cannot reuse any previously used password

### Email Handling
- Emails are normalised to lowercase before storage
- All email lookups use case insensitive matching
- Email uniqueness is enforced on current active records only
- Allows the same email to exist on historical closed records

### JWT Authentication
- Stateless JWT token based authentication
- Tokens contain userId, email and role
- Token expiry configurable via environment variable (default 24 hours)
- Custom 401/403 JSON responses for clear error handling

### Role Based Access Control
- `ROLE_USER` — can manage their own profile
- `ROLE_ADMIN` — full access to all users and admin operations

| Endpoint | ROLE_USER | ROLE_ADMIN |
|---|---|---|
| POST /register | ✅ public | ✅ public |
| POST /login | ✅ public | ✅ public |
| GET /{id} | ✅ | ✅ |
| PUT /{id} | ✅ | ✅ |
| DELETE /{id} | ✅ | ✅ |
| GET / all users | ❌ | ✅ |
| GET /search | ❌ | ✅ |
| PATCH /{id}/status | ❌ | ✅ |
| PATCH /{id}/reinstate | ❌ | ✅ |
| POST /register/admin | ❌ | ✅ |

### Admin Bootstrapping
A default admin user is seeded on application startup via Spring Boot's
`CommandLineRunner` if no admin exists. Credentials are loaded from environment
variables — never hardcoded in source code.

In a production deployment:
1. System seeds default admin from environment variables
2. First human admin logs in and creates their own admin account
3. Default seeded admin is deleted
4. Environment variables are rotated

### Error Handling
A global exception handler returns consistent JSON error responses:
- Validation failures → `400 Bad Request` with field by field error messages
- Business logic errors → `409 Conflict` with clear message
- No token provided → `401 Unauthorized`
- Insufficient role → `403 Forbidden`
- Unexpected errors → `500 Internal Server Error` with safe generic message

### CQRS Consideration
In a production environment this service would be complemented by a CQRS pattern,
separating the operational write database from a reporting database optimised
for historical queries and audit reporting. This is a conscious architectural
decision deferred for this portfolio implementation.

---

## User Service API

Base URL: `http://localhost:8081/api/users`

### Authentication
All endpoints except register, login and password reset require a valid JWT token:

```
Authorization: Bearer <token>
```

### Endpoints

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/register` | Public | Register a new user |
| `POST` | `/register/admin` | ROLE_ADMIN | Register an admin user |
| `POST` | `/login` | Public | Login and receive JWT token |
| `GET` | `/` | ROLE_ADMIN | Get all current users |
| `GET` | `/{id}` | Authenticated | Get current user by ID |
| `GET` | `/email/{email}` | Authenticated | Get current user by email |
| `GET` | `/{id}/history` | Authenticated | Get full version history |
| `GET` | `/{id}/at?queryDate=` | Authenticated | Get user at point in time |
| `GET` | `/search` | ROLE_ADMIN | Search users by name |
| `PUT` | `/{id}` | Authenticated | Update user — creates new version |
| `PATCH` | `/{id}/verify-email` | Authenticated | Mark email as verified |
| `PATCH` | `/{id}/status` | ROLE_ADMIN | Update user status |
| `PATCH` | `/{id}/reinstate` | ROLE_ADMIN | Reinstate deleted user |
| `PATCH` | `/{id}/change-password` | Authenticated | Change password |
| `DELETE` | `/{id}` | Authenticated | Soft delete user |
| `POST` | `/password-reset/request` | Public | Request password reset token |
| `POST` | `/password-reset/confirm` | Public | Confirm password reset |

### Search Parameters

| Parameters | Behaviour |
|---|---|
| `?name=smi` | Wildcard search across first and last name |
| `?firstName=john` | Search first name only |
| `?lastName=smith` | Search last name only |
| `?firstName=john&lastName=smith` | Precise match on both |
| `?activeOnly=false` | Include latest version of inactive users |

### Request Headers

| Header | Used by | Purpose |
|---|---|---|
| `Authorization` | All protected endpoints | Bearer JWT token |
| `X-Created-By` | POST /register | Audit — who created the record |
| `X-Updated-By` | PUT, PATCH | Audit — who made the change |
| `X-Deleted-By` | DELETE | Audit — who deleted the record |

### Example — Register User

```json
POST /api/users/register
X-Created-By: system

{
  "title": "MR",
  "firstName": "John",
  "middleName": "William",
  "lastName": "Smith",
  "dateOfBirth": "1985-06-15",
  "email": "john.smith@example.com",
  "phoneNumber": "+441234567890",
  "password": "Password1!"
}
```

### Example — Login Response

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9......",
  "tokenType": "Bearer",
  "expiresAt": "2026-06-12T13:00:00",
  "userId": 1,
  "email": "john.smith@example.com",
  "firstName": "John",
  "lastName": "Smith",
  "status": "ACTIVE"
}
```

### Example — Validation Error Response

```json
{
  "timestamp": "2026-06-12T09:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": {
    "email": "Must be a valid email address with a proper domain",
    "password": "Password must be at least 8 characters"
  }
}
```

---

## Running Locally

### Prerequisites
- Java 17+
- PostgreSQL 17
- Maven (or use the included `mvnw` wrapper)

### Database Setup
1. Create a PostgreSQL database named `userService`
2. Create the user ID sequence:
```sql
CREATE SEQUENCE user_sequence START WITH 1 INCREMENT BY 1;
```
3. Hibernate will create the `users` table automatically on first startup

### Environment Configuration
Create `src/main/resources/application-local.yml` — this file is gitignored
and must never be committed to version control:

```yaml
spring:
  datasource:
    password: your_postgres_password

jwt:
  secret: "YourSecretKeyHereMustBeAtLeast256BitsLongForSecurity"

admin:
  email: "admin@financehub.com"
  password: "Admin1!"
  firstName: "System"
  lastName: "Admin"
```

### Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `JWT_SECRET` | Secret key for signing JWT tokens | Set in application-local.yml |
| `JWT_EXPIRATION` | Token expiry in milliseconds | 86400000 (24 hours) |
| `ADMIN_EMAIL` | Bootstrap admin email | Set in application-local.yml |
| `ADMIN_PASSWORD` | Bootstrap admin password | Set in application-local.yml |
| `ADMIN_FIRSTNAME` | Bootstrap admin first name | Set in application-local.yml |
| `ADMIN_LASTNAME` | Bootstrap admin last name | Set in application-local.yml |

**Never hardcode credentials in source code or commit them to version control.**

### Activate Local Profile
In IntelliJ:
1. Edit Configurations → UserServiceApplication
2. Environment → Active profiles → type `local`
3. Click OK

Or via command line:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Start the Service
```bash
./mvnw spring-boot:run
```

Service starts on port `8081`.

---

## Package Structure

```
com.financehub.user_service
├── config          — Security configuration, JWT filter
├── controller      — REST API endpoints
├── dto             — Data transfer objects (LoginRequest, LoginResponse etc)
├── entity          — JPA entities and composite keys
├── enums           — Title, UserStatus, Role
├── exception       — Global exception handler
├── repository      — Data access interfaces
└── service         — Business logic
```
---

## What's Next

- [ ] account-service — accounts and balances linked to users
- [ ] transaction-service — income and expense recording
- [ ] notification-service — budget threshold alerts and password reset emails
- [ ] API Gateway — unified entry point with routing
- [ ] Docker — containerise all services
- [ ] Docker Compose — run the full stack locally with one command

---

## Author

Built as a portfolio project to demonstrate microservices architecture,
Spring Boot development, enterprise design patterns and security best practices.
