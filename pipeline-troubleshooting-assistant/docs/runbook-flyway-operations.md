# Flyway Operations Runbook

**Audience:** Operations engineers, DBAs, on-call SREs  
**Scope:** Pipeline Troubleshooting Assistant — PostgreSQL schema management via Flyway

---

## Overview

This application uses [Flyway](https://flywaydb.org/) for versioned, auditable database schema migrations. All schema changes go through numbered SQL scripts (`V<N>__description.sql`) in `backend/src/main/resources/db/migration/`. Flyway records every applied migration in `flyway_schema_history`, making it possible to verify exactly what version of the schema is running in any environment.

**Key settings (application.yml / application-prod.yml):**
| Setting | Value | Effect |
|---|---|---|
| `spring.flyway.enabled` | `true` | Flyway runs on every application startup |
| `spring.flyway.clean-disabled` | `true` | `flyway clean` is permanently blocked (data-safe) |
| `spring.flyway.baseline-on-migrate` | `true` | Auto-baseline if schema exists but history is absent |
| `spring.jpa.hibernate.ddl-auto` | `validate` (prod/ci) | Hibernate confirms JPA entities match migrated schema |

---

## Current Migration History

| Version | File | Description |
|---|---|---|
| V1 | `V1__baseline.sql` | Initial schema: `error_knowledge_base`, `analyzed_logs` |
| V2 | `V2__add_users_and_auth_tables.sql` | Users and authentication tables |
| V3 | `V3__add_history_pagination_index.sql` | Index on `analyzed_logs.created_at DESC` |

---

## Pre-Baseline Checklist

Run this checklist **once per environment** that was created before Flyway was introduced (i.e., the schema already exists from `ddl-auto: update` but has no `flyway_schema_history`).

**New environments** (created after Flyway adoption) do not need baselining — Flyway applies V1 automatically.

1. **Stop the application.** Baseline modifies `flyway_schema_history` and must not race with the running app.

2. **Verify both application tables exist:**
   ```sql
   SELECT table_name
   FROM information_schema.tables
   WHERE table_schema = 'public'
     AND table_name IN ('error_knowledge_base', 'analyzed_logs');
   ```
   Expected: 2 rows. If 0 rows, the database is empty — skip baselining and let Flyway apply V1 normally.

3. **Confirm no Flyway history exists:**
   ```sql
   SELECT COUNT(*) FROM flyway_schema_history;
   ```
   If the table exists with rows, baselining is already complete — skip this runbook.

4. **Take a schema backup before making any changes:**
   ```bash
   pg_dump -h ${DB_HOST} -U ${DB_USERNAME} -d ${DB_NAME} \
     --schema-only \
     -f schema_backup_$(date +%Y%m%d_%H%M%S).sql
   ```

5. **Compare the existing schema against V1__baseline.sql** to confirm the tables match. Any discrepancy indicates drift — investigate before baselining.

---

## Baseline Procedure

Use the provided helper script for interactive baselining with built-in safety checks:

```bash
DB_HOST=${DB_HOST} DB_PORT=${DB_PORT} DB_NAME=${DB_NAME} \
DB_USERNAME=${DB_USERNAME} DB_PASSWORD=${DB_PASSWORD} \
./scripts/flyway-baseline.sh
```

**Or run the Flyway CLI directly:**

```bash
flyway \
  -url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
  -user="${DB_USERNAME}" \
  -password="${DB_PASSWORD}" \
  -baselineVersion=1 \
  -baselineDescription="Pre-Flyway schema baseline" \
  baseline
```

**Connection parameter reference:**

| Variable | Description | Example |
|---|---|---|
| `DB_HOST` | PostgreSQL hostname | `db.internal.example.com` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `pipelinedb` |
| `DB_USERNAME` | Database user | `pipelineassistant` |
| `DB_PASSWORD` | Database password | *(from Secrets Manager)* |

---

## Post-Baseline Verification

After baseline completes:

1. **Confirm the BASELINE entry in `flyway_schema_history`:**
   ```sql
   SELECT version, description, type, success, installed_on
   FROM flyway_schema_history
   ORDER BY installed_rank;
   ```
   Expected: one row with `version='1'`, `type='BASELINE'`, `success=true`.

2. **Start the application with the prod profile:**
   ```bash
   SPRING_PROFILES_ACTIVE=prod java -jar pipeline-assistant.jar
   ```
   Flyway will:
   - Skip V1 (already in history as BASELINE)
   - Apply V2, V3 in sequence (if not yet applied)
   - Hibernate `ddl-auto: validate` confirms entity alignment

3. **Verify the health endpoint:**
   ```bash
   curl https://${APP_HOST}/actuator/health
   ```
   Expected: `{"status":"UP"}`

4. **Check application startup logs** for Flyway migration output:
   ```
   Flyway Community Edition ... by Redgate
   Database: jdbc:postgresql://... (PostgreSQL ...)
   Successfully validated X migrations ...
   Current version of schema "public": N
   Flyway Community Edition ... finished. Successfully applied N migrations ...
   ```

---

## Flyway CLI Command Reference

All commands use the same connection flags:

```bash
FLYWAY_OPTS="-url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME} \
             -user=${DB_USERNAME} -password=${DB_PASSWORD}"
```

| Command | Purpose |
|---|---|
| `flyway ${FLYWAY_OPTS} info` | List all migrations and their status (pending/applied/failed) |
| `flyway ${FLYWAY_OPTS} validate` | Verify checksums match applied migration files; fails on mismatch |
| `flyway ${FLYWAY_OPTS} baseline -baselineVersion=1` | Mark V1 as applied without running it (existing databases only) |
| `flyway ${FLYWAY_OPTS} repair` | Clear failed migration records and recalculate checksums |
| `flyway ${FLYWAY_OPTS} migrate` | Apply all pending migrations (Flyway also runs on app startup) |

---

## Migration Failure Troubleshooting

### Checksum Mismatch

**Symptom:** Application fails to start with `Validate failed: Migration checksum mismatch for migration version N`.

**Cause:** A migration file was modified after it was applied to the database.

**Resolution:**
```bash
# 1. Identify the mismatched migration
flyway ${FLYWAY_OPTS} info

# 2. Repair: resets the stored checksum to match the current file
flyway ${FLYWAY_OPTS} repair

# 3. Restart the application
```

> **Warning:** Use `repair` only if the file change was intentional and safe (e.g., a comment was added). If the SQL logic was changed, a new versioned migration is required instead.

### SQL Syntax Error in a Migration

**Symptom:** Application fails to start with `Migration V<N>__<name>.sql failed`.

**Cause:** The new migration file has a SQL error.

**Resolution:**
1. Fix the SQL in the migration file.
2. Clear the failed record from Flyway history:
   ```bash
   flyway ${FLYWAY_OPTS} repair
   ```
3. Re-run the application (or `flyway migrate`).

### Out-of-Order Migration

**Symptom:** `Detected resolved migration not applied to database: V<N>` where N is less than the current version.

**Cause:** Two developers created migrations with version numbers that arrived in the wrong order.

**Resolution options (choose one):**
- **Preferred:** Rename the conflicting migration to the next available version number and create a new PR.
- **Last resort (non-prod only):** Enable `spring.flyway.out-of-order=true` temporarily to apply the migration, then remove the flag.

### Duplicate Version Number

**Symptom:** `Found more than one migration with version N`.

**Cause:** Two migration files have the same version prefix.

**Resolution:** Rename one of the conflicting files to use the next available version number.

---

## Rollback Procedure

Flyway does not support transactional rollback of applied DDL migrations (PostgreSQL DDL is transactional per statement, but cross-migration rollback is not built into Flyway). The rollback procedure reverts the application to `ddl-auto: update` mode (the pre-Flyway behaviour), which is safe to use as a temporary fallback while a bug is fixed.

**Estimated RTO: under 5 minutes**

1. **Stop the application.**

2. **Set `ddl-auto: update` in the application configuration:**
   ```yaml
   # application.yml or environment override
   spring:
     jpa:
       hibernate:
         ddl-auto: update
     flyway:
       enabled: false
   ```
   Or set environment variables before starting:
   ```bash
   SPRING_JPA_HIBERNATE_DDL_AUTO=update \
   SPRING_FLYWAY_ENABLED=false \
   java -jar pipeline-assistant.jar
   ```

3. **Restart the application.** Hibernate `ddl-auto: update` will reconcile any schema differences. The application is now running without Flyway.

4. **Investigate the migration failure.** Fix the migration file and re-test in a staging environment before re-enabling Flyway.

5. **Re-enable Flyway** by reverting the configuration change in step 2 and redeploying.

> If a migration has partially applied DDL changes that left the schema corrupt, restore from the pre-baseline backup taken during the pre-flight checklist before starting step 2.

---

## Monitoring Migration Status

### Check Applied Migrations

```sql
SELECT installed_rank, version, description, type, success,
       installed_on, execution_time
FROM flyway_schema_history
ORDER BY installed_rank;
```

Expected output shows all applied migrations with `success = true`.

### Detect Failed Migrations

```sql
SELECT version, description, installed_on
FROM flyway_schema_history
WHERE success = false;
```

A non-empty result means there is a failed migration blocking startup. Use `flyway repair` after fixing the underlying SQL.

### Application Startup Log Patterns

On successful startup, look for these log lines:
```
Flyway Community Edition ... by Redgate
Successfully validated X migrations (execution time ...)
Current version of schema "public": N
Flyway Community Edition ... finished. Successfully applied Y migrations to schema "public" (execution time ...)
```

On validation failure, the application terminates with an error:
```
FlywayException: Validate failed: Migration checksum mismatch for migration version N
```

### CI Validation

Every push to `main` and every pull request triggers the CI pipeline (`.github/workflows/ci.yml`) which:
1. Starts a clean PostgreSQL 16 service container
2. Runs `mvn clean verify` with `SPRING_PROFILES_ACTIVE=ci` (activates `application-ci.yml`: `ddl-auto: validate`)
3. Flyway applies all migrations in sequence
4. Hibernate validates JPA entity alignment
5. All unit and Testcontainers integration tests run

A CI failure on the `Build and verify with Maven` step indicates a migration or schema validation problem.

---

## Notes

- `V1__baseline.sql` is **immutable**. Never modify it after applying to any environment. All future schema changes must use `V2__`, `V3__`, etc.
- `flyway.clean-disabled: true` in `application.yml` permanently blocks `flyway clean`, which would drop all tables.
- The RPO for schema changes is controlled by the migration version sequence. Do not skip version numbers.
- `baseline-on-migrate: true` in `application.yml` is a safety net: if Flyway finds an empty history table but non-empty schema, it auto-baselines. Prefer running this runbook explicitly for controlled environments.
