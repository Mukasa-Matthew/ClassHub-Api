# ClassHub API

Backend API for ClassHub, a coursework and study management platform for a university class.
It covers class membership, course units, coursework and deadlines, announcements, private
lecture notes, and multi-channel notifications (in-app, email, WhatsApp, and Web Push).

## Tech stack

- Java 21, Spring Boot 4 (Web MVC, Security, Data JPA, Validation)
- PostgreSQL 17 with Flyway migrations
- Session-based authentication with CSRF protection
- Testcontainers for integration tests
- Docker and Docker Compose for local and production deployment

## Getting started

### Prerequisites

- JDK 21
- Docker (for PostgreSQL and the integration tests)

### Configure the environment

```bash
cp .env.example .env
```

Edit `.env` and replace every `CHANGE_ME` value. Signing keys can be generated with
`openssl rand -base64 48`. Set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` on first
run to create the initial `SUPER_ADMIN` account.

### Run with Docker Compose

```bash
docker compose up --build
```

The API is served on `http://localhost:8080`.

### Run locally

Start only the database, then run the application with the Maven wrapper:

```bash
docker compose up -d postgres
set -a && source .env && set +a
./mvnw spring-boot:run
```

## Testing

```bash
./mvnw verify
```

Integration tests start a disposable PostgreSQL container through Testcontainers, so Docker
must be running.

## Health checks

| Endpoint | Purpose |
|----------|---------|
| `GET /health` | Liveness: the process is up |
| `GET /ready` | Readiness: returns `503` until a valid database connection is available |

## Documentation

- [API reference](docs/API.md)
- [Class membership](docs/CLASS-MEMBERSHIP.md)
- [Notification architecture](docs/NOTIFICATION-ARCHITECTURE.md)
- [Notification event catalogue](docs/NOTIFICATION-EVENT-CATALOGUE.md)
- [WhatsApp integration](docs/WHATSAPP-INTEGRATION.md)
- [Bring-your-own-key AI](docs/BYOK-AI.md)

## Production

Activate the production profile with `SPRING_PROFILES_ACTIVE=prod` and serve the API behind
HTTPS. The profile enables secure session cookies, hides error details, and reduces logging.
Configure `CLASSHUB_CORS_ALLOWED_ORIGINS` with the exact frontend origins; wildcard origins are
rejected. Secrets must be supplied through environment variables, never committed.
