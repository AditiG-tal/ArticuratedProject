# ArtiCurated — Order & Returns Management System

A production-quality Spring Boot backend that manages the complete lifecycle of orders and returns for ArtiCurated, a boutique artisanal goods marketplace.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2, Java 17 |
| Database | PostgreSQL 15 |
| Message Broker | RabbitMQ 3.12 |
| PDF Generation | iText 7 |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Mockito, H2 (in-memory) |
| Coverage | JaCoCo |
| Containerisation | Docker + Docker Compose |

---

## Quick Start (Docker)

### Prerequisites
- Docker Engine 24+
- Docker Compose v2+

### Run everything with one command

```bash
git clone <your-repo-url>
cd articurated
docker compose up --build
```

Services:
- **API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)
- **PostgreSQL**: localhost:5432

---

## Local Development Setup

### Prerequisites
- Java 17+, Maven 3.9+
- PostgreSQL 15, RabbitMQ 3.12 running locally

### 1. Start infrastructure only

```bash
docker compose up postgres rabbitmq -d
```

### 2. Run the application

```bash
mvn spring-boot:run
```

---

## Running Tests

```bash
mvn test
```

Tests use H2 in-memory DB and mocked RabbitMQ — no external services needed.

### Generate coverage report

```bash
mvn test jacoco:report
open target/site/jacoco/index.html
```

---

## API Overview

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/orders` | Create order |
| GET | `/api/v1/orders/{id}` | Get order |
| PATCH | `/api/v1/orders/{id}/status` | Transition status |
| GET | `/api/v1/orders/{id}/history` | Audit trail |
| POST | `/api/v1/orders/{id}/returns` | Initiate return |
| PATCH | `/api/v1/returns/{id}/status` | Transition return |
| GET | `/api/v1/returns/{id}/history` | Return audit trail |

Full interactive docs at http://localhost:8080/swagger-ui.html
