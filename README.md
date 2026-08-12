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

### Installing Pre-commit Hooks (security guardrails)

A pre-commit hook scans staged files for hardcoded secrets before every commit. Run once after cloning:

```bash
bash scripts/install-hooks.sh
```

The hook detects: AWS access keys, Bearer tokens, quoted password assignments, private key headers, and JDBC URLs with embedded credentials. If a secret is found, the commit is blocked with a message identifying the file and line number.

To bypass for a known false positive (e.g., a test fixture):
```bash
git commit --no-verify
```

**Requirement:** GNU `grep` (the default on Linux). On macOS:
```bash
brew install grep
export PATH="/opt/homebrew/opt/grep/libexec/gnubin:$PATH"
```

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

## Backup and Recovery

The application runs a `backup` sidecar container that takes nightly `pg_dump` snapshots of the PostgreSQL database (default: 02:00 UTC). Backups are stored on the `backup_data` Docker volume (retained 30 days) and optionally pushed to S3-compatible off-site storage (retained 90 days). A weekly verify-restore job restores the latest backup to a temporary database and checks row counts to confirm integrity.

- **RPO:** 24 hours (nightly backup cadence)
- **RTO:** 30 minutes (validated quarterly drill)

For full restore procedures, environment variable reference, troubleshooting, and escalation contacts, see the runbook. To schedule and track quarterly RTO drills, use the drill template.

| Document | Description |
|----------|-------------|
| [Backup and Restore Runbook](docs/backup-restore-runbook.md) | Step-by-step restore procedures for all disaster scenarios, environment variable reference, and troubleshooting guide |
| [RTO Validation Drill](docs/rto-validation-drill.md) | Quarterly drill template with timing checkpoints, pass/fail criteria, results template, and example execution |

## Documentation

| Document | Description |
|----------|-------------|
| [Secret Management Runbook](docs/runbooks/secret-management.md) | Secret inventory, rotation procedures, incident response playbook, environment guidance, and production secret store roadmap |

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
