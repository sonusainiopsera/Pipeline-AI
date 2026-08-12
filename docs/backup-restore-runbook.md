# Backup and Restore Runbook — Pipeline Troubleshooting Assistant

**RPO target:** 24 hours | **RTO target:** 30 minutes

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Backup Schedule and Retention](#2-backup-schedule-and-retention)
3. [Prerequisites](#3-prerequisites)
4. [Manual Backup Trigger](#4-manual-backup-trigger)
5. [Restore from Local Backup](#5-restore-from-local-backup)
6. [Restore from Off-site Backup](#6-restore-from-off-site-backup)
7. [Partial Table Restore](#7-partial-table-restore)
8. [Fresh Environment Bootstrap](#8-fresh-environment-bootstrap)
9. [Verification](#9-verification)
10. [Troubleshooting](#10-troubleshooting)
11. [Escalation](#11-escalation)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                       Docker Compose Stack                       │
│                                                                  │
│  ┌──────────┐   reads   ┌─────────────┐   pg_dump   ┌────────┐ │
│  │ backend  │ ────────▶ │  PostgreSQL  │ ──────────▶ │ backup │ │
│  │ :8080    │           │  (db)        │             │ sidecar│ │
│  └──────────┘           │  :5432       │             └───┬────┘ │
│                         └─────────────┘                  │      │
│                                                           │      │
│                                                    writes │      │
│                                                           ▼      │
│                                               ┌────────────────┐ │
│                                               │  backup_data   │ │
│                                               │  Docker volume │ │
│                                               └───────┬────────┘ │
└───────────────────────────────────────────────────────│──────────┘
                                                        │
                                                 aws s3 cp
                                                        │
                                                        ▼
                                              ┌──────────────────┐
                                              │  S3-compatible   │
                                              │  off-site bucket │
                                              │  (optional)      │
                                              └──────────────────┘
```

**Backup flow:**
1. The `backup` sidecar container runs `pg_dump` against the `db` service on the configured cron schedule.
2. Dump files (custom format, compressed) are written to the `backup_data` Docker volume mounted at `/backups` inside the container.
3. On success, retention cleanup removes local files older than `BACKUP_RETENTION_DAYS` (default 30 days).
4. If `S3_BUCKET` is set, `push-offsite.sh` uploads the latest dump to S3 under `backups/YYYY/MM/`. S3 push failures emit a warning but do **not** fail the backup job.
5. Weekly, `verify-restore.sh` restores the latest dump to a temporary database (`pipeline_assistant_verify`), runs integrity checks, and drops the verify database.

**RPO (Recovery Point Objective):** 24 hours — nightly backups at 02:00 UTC.
**RTO (Recovery Time Objective):** 30 minutes — from incident declaration to application serving traffic.

---

## 2. Backup Schedule and Retention

| Parameter | Default | Description |
|-----------|---------|-------------|
| `BACKUP_CRON_SCHEDULE` | `0 2 * * *` | When the backup job runs (cron syntax, UTC) |
| `VERIFY_CRON_SCHEDULE` | `0 4 * * 0` | When the verify-restore check runs (default: Sunday 04:00 UTC) |
| `BACKUP_RETENTION_DAYS` | `30` | Local dump files older than this are deleted |
| `REMOTE_RETENTION_DAYS` | `90` | S3 objects older than this are deleted by `remote-retention.sh` |

**File naming convention:**

```
pipeline_assistant_YYYYMMDD_HHMMSS.dump
```

Examples:
```
pipeline_assistant_20260812_020003.dump   ← nightly backup, 2026-08-12 at 02:00:03 UTC
pipeline_assistant_20260812_143721.dump   ← manual backup, 2026-08-12 at 14:37:21 UTC
```

**S3 key structure:**

```
s3://${S3_BUCKET}/backups/YYYY/MM/pipeline_assistant_YYYYMMDD_HHMMSS.dump
```

---

## 3. Prerequisites

### Required tools

| Tool | Version | Install |
|------|---------|---------|
| `docker` | 24+ | https://docs.docker.com/get-docker/ |
| `docker compose` | v2 plugin | Included with Docker Desktop |
| `psql` | 16 (matches DB image) | `apt install postgresql-client-16` |
| `pg_restore` | 16 (matches DB image) | Included with `postgresql-client-16` |
| `aws` CLI | v2 | https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html |

> `psql` and `aws` CLI are only required for the operator machine when running restore operations outside Docker. For Docker-internal operations they are available inside the backup container.

### Environment variables

Copy `.env.example` to `.env` in the `pipeline-troubleshooting-assistant/` directory and fill in all values before starting the stack.

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `POSTGRES_DB` | Yes | — | PostgreSQL database name (e.g. `pipeline_assistant`) |
| `POSTGRES_USER` | Yes | — | PostgreSQL superuser login |
| `POSTGRES_PASSWORD` | Yes | — | PostgreSQL superuser password |
| `DB_URL` | Yes | — | JDBC URL for the backend (`jdbc:postgresql://db:5432/${POSTGRES_DB}`) |
| `DB_USERNAME` | Yes | — | Must match `POSTGRES_USER` |
| `DB_PASSWORD` | Yes | — | Must match `POSTGRES_PASSWORD` |
| `BACKUP_CRON_SCHEDULE` | No | `0 2 * * *` | Nightly backup cron (UTC) |
| `BACKUP_RETENTION_DAYS` | No | `30` | Days to keep local backup files |
| `VERIFY_CRON_SCHEDULE` | No | `0 4 * * 0` | Weekly verify-restore cron (UTC) |
| `S3_BUCKET` | No | — | S3 bucket name; omit to disable off-site push |
| `S3_ENDPOINT` | No | — | Override S3 endpoint URL (MinIO, Ceph, etc.) |
| `S3_REGION` | No | `us-east-1` | AWS region |
| `S3_ACCESS_KEY` | No | — | AWS / S3 access key ID |
| `S3_SECRET_KEY` | No | — | AWS / S3 secret access key |
| `REMOTE_RETENTION_DAYS` | No | `90` | Days to keep objects in S3 |
| `PUSH_TIMEOUT_SECONDS` | No | `60` | S3 upload timeout in seconds |

> **Security:** credentials are passed via `.pgpass` file inside the container; they never appear in process listings or log output. The pre-commit hook in `scripts/install-hooks.sh` prevents committing real credentials.

---

## 4. Manual Backup Trigger

Use this when you need a backup outside the scheduled window (e.g., before a schema migration or configuration change).

```bash
# From the pipeline-troubleshooting-assistant/ directory:
docker compose exec backup /scripts/backup.sh
```

**Expected output** (JSON, one object per log line):

```json
{"timestamp":"2026-08-12T14:37:21Z","status":"success","filename":"pipeline_assistant_20260812_143721.dump","size_bytes":45312,"duration_seconds":2,"tables_count":2}
```

If `S3_BUCKET` is set, a second line appears:

```json
{"timestamp":"2026-08-12T14:37:23Z","status":"success","filename":"pipeline_assistant_20260812_143721.dump","s3_uri":"s3://${S3_BUCKET}/backups/2026/08/pipeline_assistant_20260812_143721.dump","size_bytes":45312,"duration_seconds":2}
```

**Verify the file was created:**

```bash
docker compose exec backup ls -lh /backups/
```

---

## 5. Restore from Local Backup

> **Caution:** this procedure overwrites the target database. Stop the backend first to prevent writes during restore.

### Step 1 — Stop the backend

```bash
docker compose stop backend
```

### Step 2 — Identify the backup file to restore

```bash
docker compose exec backup ls -lt /backups/
```

Note the filename you want to restore (e.g. `pipeline_assistant_20260812_020003.dump`).

### Step 3 — Run the restore

```bash
docker compose exec backup /scripts/restore.sh \
  --file /backups/pipeline_assistant_YYYYMMDD_HHMMSS.dump \
  --target-db ${POSTGRES_DB}
```

Replace `pipeline_assistant_YYYYMMDD_HHMMSS.dump` with the actual filename.

**Expected output:**

```json
{"timestamp":"2026-08-12T14:41:05Z","status":"success","filename":"pipeline_assistant_20260812_020003.dump","target_db":"pipeline_assistant","duration_seconds":3,"exit_code":0}
```

### Step 4 — Verify the restore

Run the [verification queries](#9-verification).

### Step 5 — Restart the backend

```bash
docker compose start backend
```

### Step 6 — Confirm the application is serving traffic

```bash
curl -sf http://localhost:8080/api/errors | head -c 200
```

A non-empty JSON array confirms the knowledge base is intact.

---

## 6. Restore from Off-site Backup

Use this when the local backup volume has been lost or the backup container has been destroyed.

### Step 1 — Download the backup from S3

```bash
# List available backups:
aws s3 ls s3://${S3_BUCKET}/backups/ --recursive \
  ${S3_ENDPOINT:+--endpoint-url "${S3_ENDPOINT}"}

# Download the desired file:
aws s3 cp \
  s3://${S3_BUCKET}/backups/YYYY/MM/pipeline_assistant_YYYYMMDD_HHMMSS.dump \
  ./pipeline_assistant_YYYYMMDD_HHMMSS.dump \
  ${S3_ENDPOINT:+--endpoint-url "${S3_ENDPOINT}"}
```

### Step 2 — Copy the file into the backup container volume

```bash
docker compose cp \
  ./pipeline_assistant_YYYYMMDD_HHMMSS.dump \
  backup:/backups/pipeline_assistant_YYYYMMDD_HHMMSS.dump
```

### Step 3 — Proceed with local restore

Follow [Section 5](#5-restore-from-local-backup) from Step 1 onward, using the filename you just copied.

---

## 7. Partial Table Restore

Use this when only one table needs to be recovered (e.g., `error_knowledge_base` was accidentally truncated but `analyzed_logs` is intact).

### Step 1 — List tables in the backup

```bash
docker compose exec backup \
  pg_restore --list /backups/pipeline_assistant_YYYYMMDD_HHMMSS.dump \
  | grep "TABLE DATA"
```

### Step 2 — Restore a single table

```bash
docker compose exec backup \
  pg_restore \
    --host=${DB_HOST:-db} \
    --port=${DB_PORT:-5432} \
    --username=${DB_USER} \
    --dbname=${POSTGRES_DB} \
    --table=error_knowledge_base \
    --data-only \
    --no-owner \
    /backups/pipeline_assistant_YYYYMMDD_HHMMSS.dump
```

> `--data-only` skips DDL so existing table structure is preserved. Omit it only if the table was dropped entirely.

### Step 3 — Verify row count

```bash
docker compose exec db \
  psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} \
  -c "SELECT COUNT(*) FROM error_knowledge_base;"
```

---

## 8. Fresh Environment Bootstrap

Use this procedure when standing up the application for the first time or in a new environment.

### Step 1 — Clone the repository and configure .env

```bash
git clone <repo-url>
cd pipeline-troubleshooting-assistant
cp .env.example .env
# Edit .env: fill in POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD,
# DB_URL, DB_USERNAME, DB_PASSWORD, APP_CORS_ALLOWED_ORIGINS
```

### Step 2 — Start all services

```bash
docker compose up -d
```

Flyway runs automatically at backend startup and applies all migrations in `src/main/resources/db/migration/`. Schema is created on the first run without manual intervention.

### Step 3 — Confirm seed data populated

The application seeds the `error_knowledge_base` table with 6 initial patterns on first startup.

```bash
docker compose exec db \
  psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} \
  -c "SELECT id, category FROM error_knowledge_base ORDER BY id;"
```

Expected: 6 rows (Memory, Network, Permissions, Dependency, Docker, Testing).

### Step 4 — (Optional) Restore a backup on top

If you have a backup from a previous environment that contains `analyzed_logs` history, restore it now:

```bash
docker compose stop backend
docker compose exec backup /scripts/restore.sh \
  --file /backups/pipeline_assistant_YYYYMMDD_HHMMSS.dump \
  --target-db ${POSTGRES_DB}
docker compose start backend
```

> The restore uses `--clean --if-exists`, which drops and recreates tables before restoring data. Flyway migrations must be re-applied after a `--clean` restore if the dump pre-dates any migration. Re-starting the backend triggers Flyway automatically.

---

## 9. Verification

Run these queries after any restore to confirm data integrity.

```bash
# Connect to the database:
docker compose exec db psql -U ${POSTGRES_USER} -d ${POSTGRES_DB}
```

### Check table existence

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
```

Expected tables: `analyzed_logs`, `error_knowledge_base`, `flyway_schema_history`.

### Check row counts

```sql
SELECT 'error_knowledge_base' AS tbl, COUNT(*) FROM error_knowledge_base
UNION ALL
SELECT 'analyzed_logs', COUNT(*) FROM analyzed_logs;
```

`error_knowledge_base` must have at least 6 rows (seed data). `analyzed_logs` may be 0 on a fresh restore.

### Check categoryCounts query (mirrors application behavior)

```sql
SELECT category, COUNT(*) AS count
FROM analyzed_logs
GROUP BY category
ORDER BY category;
```

### Check most recent analysis timestamp

```sql
SELECT id, category, confidence, created_at
FROM analyzed_logs
ORDER BY created_at DESC
LIMIT 5;
```

### Confirm Flyway migration history

```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

All rows must have `success = true`.

---

## 10. Troubleshooting

| Symptom | Likely Cause | Resolution |
|---------|-------------|------------|
| Backup container exits immediately at startup | Missing required env var (`DB_HOST`, `DB_NAME`, `DB_USER`, or `DB_PASSWORD`) | Check `docker compose logs backup`; verify all variables are set in `.env` and `docker-compose.yml` |
| `pg_dump: error: connection to server on socket` or `pg_dump: error: FATAL: password authentication failed` | `DB_USER` or `DB_PASSWORD` incorrect, or `.pgpass` permissions wrong | Verify `POSTGRES_USER`/`POSTGRES_PASSWORD` match `DB_USER`/`DB_PASSWORD`; the backup container writes `/tmp/.pgpass_backup` with mode 600 — check no root-owned file conflicts |
| S3 push times out (`"status":"warning","message":"S3 bucket not accessible"`) | Network blocked, wrong endpoint URL, or credentials expired | Check outbound 443 access; verify `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`; test with `aws s3 ls s3://${S3_BUCKET}` locally |
| `pg_restore: error: invalid magic number` or `pg_restore: [archiver] unsupported version` | Backup was created by a different major version of PostgreSQL | Use a `pg_restore` binary that matches the `pg_dump` version; the dump version is visible with `pg_restore --list <file> | head -5` |
| Verify-restore fails with `"check":"table_exists_error_knowledge_base","status":"FAIL"` | Flyway migrations did not run inside the verify database after restore | The verify database is used with `--no-owner` restore only; missing tables indicate the dump pre-dates the migration that created them — restore to the production DB and restart backend to trigger Flyway |
| `docker compose exec backup ls /backups/` shows no files after backup | Backup volume not mounted, or disk full on the host | Check `docker volume inspect pipeline-troubleshooting-assistant_backup_data`; verify available disk space with `df -h` on the host |
| S3 remote-retention deletes objects unexpectedly early | `REMOTE_RETENTION_DAYS` set too low, or clock skew between container and S3 | Verify the `REMOTE_RETENTION_DAYS` value in `.env`; object metadata `LastModified` date is set by S3 on upload, not by the container |

---

## 11. Escalation

> **Replace the placeholders below with your team's actual contacts and channels.**

| Situation | Contact | Channel |
|-----------|---------|---------|
| RTO breach risk (>20 min elapsed, no progress) | On-call engineer | `#pipeline-ai-incidents` (Slack) |
| Data loss confirmed | Engineering lead | PagerDuty policy `pipeline-ai-critical` |
| S3 credential rotation needed | Platform / secrets owner | `#platform-secrets` (Slack) |
| Recurring backup failures | Backend team | `#pipeline-ai-ops` (Slack) |

**Incident template for Slack:**

```
🔴 Pipeline AI — Restore in progress
Time declared: <YYYY-MM-DD HH:MM UTC>
Incident type: <data loss / service unavailable / backup failure>
Backup file: <filename or "unknown">
Current step: <step number from runbook>
ETA to resolution: <estimated time>
Owner: <your name>
```
