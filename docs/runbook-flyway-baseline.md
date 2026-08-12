# Flyway Baseline Runbook

**Purpose:** Apply Flyway baseline to an existing PostgreSQL database that was created by Hibernate's `ddl-auto: update` before Flyway was introduced. The baseline marks V1 as already applied without running V1__baseline.sql, preventing re-creation of tables that already exist.

**When to use:** Once only, for each environment (staging, production) that has the pre-Flyway schema. New environments (created after this runbook was executed) start fresh — Flyway applies V1 automatically and no baselining is needed.

---

## Pre-flight Checks

Before running the baseline command, verify:

1. **Application is stopped.** The baseline operation modifies `flyway_schema_history`; running it while the app is active risks race conditions.
2. **Both tables exist.** Run:
   ```sql
   SELECT table_name
   FROM information_schema.tables
   WHERE table_schema = 'public'
     AND table_name IN ('error_knowledge_base', 'analyzed_logs');
   ```
   Expected: 2 rows. If 0 rows, the database is empty — do not baseline, let Flyway apply V1 normally.

3. **No existing Flyway history.** Run:
   ```sql
   SELECT COUNT(*) FROM flyway_schema_history;
   ```
   If the table exists and has rows, baselining is already done — skip this runbook.
   If the table does not exist, proceed.

4. **Take a database backup.**
   ```bash
   pg_dump -h ${DB_HOST} -U ${DB_USERNAME} -d ${DB_NAME} \
     --schema-only -f schema_backup_$(date +%Y%m%d_%H%M%S).sql
   ```

---

## Baseline Command

```bash
flyway \
  -url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME} \
  -user=${DB_USERNAME} \
  -password=${DB_PASSWORD} \
  -baselineVersion=1 \
  -baselineDescription="Pre-Flyway schema baseline" \
  baseline
```

**Environment variable reference:**

| Variable       | Description                          | Example                           |
|----------------|--------------------------------------|-----------------------------------|
| `DB_HOST`      | PostgreSQL host                      | `db.internal.example.com`         |
| `DB_PORT`      | PostgreSQL port                      | `5432`                            |
| `DB_NAME`      | Database name                        | `pipelinedb`                      |
| `DB_USERNAME`  | Database user                        | `pipelineassistant`               |
| `DB_PASSWORD`  | Database password                    | *(from Secrets Manager)*          |

---

## Post-baseline Verification

After the baseline command completes:

1. Confirm `flyway_schema_history` contains the baseline entry:
   ```sql
   SELECT version, description, type, success, installed_on
   FROM flyway_schema_history
   ORDER BY installed_rank;
   ```
   Expected result: one row with `version='1'`, `type='BASELINE'`, `success=true`.

2. Start the application with `SPRING_PROFILES_ACTIVE=prod`. It will:
   - See V1 already in flyway_schema_history (marked as baseline) → skip V1__baseline.sql
   - Run any V2+ migrations if present
   - Hibernate `ddl-auto: validate` confirms schema matches entity definitions

3. Verify application health:
   ```bash
   curl https://${APP_HOST}/actuator/health
   ```
   Expected: `{"status":"UP"}`

---

## Rollback Steps

If the baseline command fails or the application fails to start after baselining:

1. **Drop the flyway_schema_history table** to return to the pre-Flyway state:
   ```sql
   DROP TABLE IF EXISTS flyway_schema_history;
   ```
2. Restore the schema from the pre-flight backup if tables were accidentally modified.
3. Investigate the error in the Flyway output or Spring Boot startup logs.
4. Fix the root cause (e.g., update `V1__baseline.sql` to match the actual schema) and re-run pre-flight checks.

---

## Notes

- `baseline-on-migrate: true` is set in `application.yml`. This means Flyway will auto-baseline if it finds an empty history table but non-empty schema. This is a safety net for environments missed by this runbook, but do not rely on it — run this runbook explicitly to ensure a clean baseline entry.
- V1__baseline.sql is **immutable**. Never modify it after applying to any environment. All future schema changes must use V2__, V3__, etc.
- The RPO for schema changes is controlled by the migration version sequence. Do not skip version numbers.
