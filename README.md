# Personal Finance Hub — Microservices Portfolio Project

A microservices-based personal finance management system built with Java and Spring Boot,
demonstrating enterprise-grade design patterns including effective dating,
audit trails, and RESTful API design.

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
| Spring Security Crypto | BCrypt password hashing |
| PostgreSQL | Persistent relational database |
| Hibernate | ORM and schema management |
| Lombok | Boilerplate reduction |
| Bean Validation | Input validation |
| Maven | Dependency management |

---

## Design Decisions

### Effective Dating
Rather than overwriting user records on update, the system creates a new version
of the record with a new effective date. The previous version is closed off with
an end date. This provides a complete, immutable history of all changes.

- `endDate = null` identifies the current active record
- `effectiveDate` marks when each version became active
- Updates close the current record and insert a new version with `effectiveDate + 1 microsecond`
- Deletes are soft — the record is closed, never physically removed

### Audit Fields
Every record captures:
- `createdAt` / `createdBy` — set once on creation, never updated
- `updatedAt` / `updatedBy` — updated on every change

### Password Security
Passwords are hashed using BCrypt before storage. Plain text passwords
are never persisted.

### Validation
Input validation is enforced at the API layer using Bean Validation:
- Email format validated with regex including domain checking
- Password complexity enforced — uppercase, lowercase and special character required
- Field length constraints enforced at both API and database level

### Error Handling
A global exception handler (`GlobalExceptionHandler`) returns consistent
JSON error responses for all error types — validation failures, business
logic errors and unexpected exceptions. Raw stack traces are never exposed.

### CQRS Consideration
In a production environment this service would be complemented by a CQRS pattern,
separating the operational write database from a reporting database optimised
for historical queries and audit reporting. This is a conscious architectural
decision deferred for this portfolio implementation.

---

## User Service API

Base URL: `http://localhost:8081/api/users`

### Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/register` | Register a new user |
| `GET` | `/` | Get all current users |
| `GET` | `/{id}` | Get current user by ID |
| `GET` | `/email/{email}` | Get current user by email |
| `GET` | `/{id}/history` | Get full version history for a user |
| `GET` | `/{id}/at?queryDate=` | Get user record at a point in time |
| `GET` | `/search` | Search users by name |
| `PUT` | `/{id}` | Update user — creates a new version |
| `PATCH` | `/{id}/verify-email` | Mark email as verified |
| `PATCH` | `/{id}/status` | Update user status |
| `DELETE` | `/{id}` | Soft delete user |

### Search Parameters

The `/search` endpoint supports flexible name searching:

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

### Example — Error Response

```json
{
  "timestamp": "2026-06-11T09:30:00",
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
2. Create the sequence:
```sql
CREATE SEQUENCE user_sequence START WITH 1 INCREMENT BY 1;
```
3. Hibernate will create the `users` table automatically on first startup

### Configuration
Update `src/main/resources/application.yml` with your PostgreSQL credentials:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/userService
    username: postgres
    password: your_password
```

### Start the Service
```bash
./mvnw spring-boot:run
```

Service starts on port `8081`.

---

## What's Next

- [ ] account-service — accounts and balances linked to users
- [ ] transaction-service — income and expense recording
- [ ] notification-service — budget threshold alerts
- [ ] API Gateway — unified entry point with routing
- [ ] Docker — containerise all services
- [ ] Docker Compose — run the full stack locally with one command
- [ ] Spring Security — JWT authentication across services

---

## Author

Built as a portfolio project to demonstrate microservices architecture,
Spring Boot development, and enterprise design patterns.