# Pipeline Troubleshooting Assistant

[![CI](https://github.com/sonusainiopsera/Pipeline-AI/actions/workflows/ci.yml/badge.svg)](https://github.com/sonusainiopsera/Pipeline-AI/actions/workflows/ci.yml)

An internal DevOps support tool that enables support engineers to diagnose CI/CD pipeline failures by matching pasted failure logs against a curated knowledge base, producing explainable root-cause analyses with confidence scoring, and generating customer-ready communications.

## Architecture

| Component | Technology | Port |
|-----------|-----------|------|
| Backend API | Spring Boot 3.3.5 + Java 17 | 8080 |
| Frontend SPA | React 18 + TypeScript | 5173 |
| Database | PostgreSQL 16 | 5432 |

## Project Structure

```
pipeline-troubleshooting-assistant/
├── backend/          # Spring Boot Maven project
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/opsera/pipelineassistant/
│       └── test/java/com/opsera/pipelineassistant/
├── frontend/         # React TypeScript SPA
└── docker-compose.yml
```

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### Environment Setup (required before first run)

All credentials are stored in a git-ignored `.env` file — no secrets are committed to version control.

```bash
cd pipeline-troubleshooting-assistant
cp .env.example .env
# Edit .env and replace placeholder values with real credentials
```

The `.env` file must define:

| Variable | Description |
|----------|-------------|
| `POSTGRES_DB` | PostgreSQL database name |
| `POSTGRES_USER` | PostgreSQL username |
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `DB_URL` | JDBC URL for the backend (e.g. `jdbc:postgresql://db:5432/pipelinedb`) |
| `DB_USERNAME` | Spring datasource username (matches `POSTGRES_USER`) |
| `DB_PASSWORD` | Spring datasource password (matches `POSTGRES_PASSWORD`) |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed CORS origins (e.g. `http://localhost:5173`) |

Docker Compose reads `.env` automatically from the same directory as `docker-compose.yml`. If any required variable is missing, the startup will fail with an explicit error message.

For CI/CD pipelines without a `.env` file, set the variables directly in the runner environment instead.

### Running with Docker Compose

```bash
cd pipeline-troubleshooting-assistant
docker-compose up --build
```

### Running the Backend Locally

```bash
cd pipeline-troubleshooting-assistant/backend
mvn spring-boot:run
```

### Running Tests

```bash
cd pipeline-troubleshooting-assistant/backend
mvn test
```

### Running Tests with Coverage Report

```bash
cd pipeline-troubleshooting-assistant/backend
mvn verify
```

The JaCoCo coverage report is generated at `target/site/jacoco/index.html`.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/analyze` | Analyze a pipeline failure log |
| GET | `/api/history` | Retrieve analysis history (last 50) |
| GET | `/api/dashboard` | Dashboard statistics |
| GET | `/api/errors` | List knowledge base entries |
| POST | `/api/errors` | Create a knowledge base entry |
| PUT | `/api/errors/{id}` | Update a knowledge base entry |
| DELETE | `/api/errors/{id}` | Delete a knowledge base entry |
