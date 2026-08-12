# Pipeline Troubleshooting Assistant

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
