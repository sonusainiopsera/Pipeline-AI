#!/usr/bin/env bash
# flyway-baseline.sh — Apply Flyway baseline to an existing PostgreSQL database
#
# Usage:
#   DB_HOST=db.example.com DB_PORT=5432 DB_NAME=pipelinedb \
#   DB_USERNAME=pipelineassistant DB_PASSWORD=secret \
#   ./scripts/flyway-baseline.sh
#
# What it does:
#   1. Checks that the application tables exist (pre-Flyway schema is present)
#   2. Checks that flyway_schema_history does NOT already exist (prevents double-baseline)
#   3. Runs flyway baseline -baselineVersion=1
#   4. Verifies the baseline entry was recorded in flyway_schema_history
#
# Requires: flyway CLI on $PATH, psql on $PATH
#
# This script is idempotent only in the sense that it exits early if baseline
# is already applied.  It does NOT support re-baselining.

set -euo pipefail

# ─── Configuration via environment variables ────────────────────────────────

DB_HOST="${DB_HOST:?Set DB_HOST (e.g. localhost)}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:?Set DB_NAME (e.g. pipelinedb)}"
DB_USERNAME="${DB_USERNAME:?Set DB_USERNAME}"
DB_PASSWORD="${DB_PASSWORD:?Set DB_PASSWORD}"

JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
export PGPASSWORD="${DB_PASSWORD}"

# ─── Helper ──────────────────────────────────────────────────────────────────

psql_query() {
  psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" \
       -tAc "$1" 2>&1
}

log() { echo "[$(date '+%Y-%m-%dT%H:%M:%S')] $*"; }
fail() { log "ERROR: $*" >&2; exit 1; }

# ─── Pre-flight: verify application tables exist ─────────────────────────────

log "Checking that application tables exist..."
TABLE_COUNT=$(psql_query \
  "SELECT COUNT(*) FROM information_schema.tables \
   WHERE table_schema = 'public' \
     AND table_name IN ('error_knowledge_base', 'analyzed_logs');" \
  | tr -d '[:space:]')

if [ "${TABLE_COUNT}" -ne 2 ]; then
  fail "Expected 2 application tables but found ${TABLE_COUNT}. \
This is a new database — do not baseline it; let Flyway apply V1 normally."
fi
log "Both application tables found."

# ─── Pre-flight: check if baseline already applied ───────────────────────────

log "Checking if flyway_schema_history already exists..."
HISTORY_EXISTS=$(psql_query \
  "SELECT COUNT(*) FROM information_schema.tables \
   WHERE table_schema = 'public' AND table_name = 'flyway_schema_history';" \
  | tr -d '[:space:]')

if [ "${HISTORY_EXISTS}" -ne 0 ]; then
  BASELINE_ROWS=$(psql_query \
    "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'BASELINE';" \
    | tr -d '[:space:]')
  if [ "${BASELINE_ROWS}" -gt 0 ]; then
    log "flyway_schema_history already contains a BASELINE entry — nothing to do."
    exit 0
  fi
  fail "flyway_schema_history exists but has no BASELINE entry. \
Inspect the table manually before proceeding."
fi

# ─── Backup reminder ──────────────────────────────────────────────────────────

log "REMINDER: Ensure you have taken a schema backup before proceeding."
log "  pg_dump -h ${DB_HOST} -U ${DB_USERNAME} -d ${DB_NAME} \\"
log "    --schema-only -f schema_backup_\$(date +%Y%m%d_%H%M%S).sql"
read -r -p "Have you taken a backup? [yes/no]: " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
  fail "Aborted. Please take a backup and re-run."
fi

# ─── Run flyway baseline ──────────────────────────────────────────────────────

log "Running: flyway baseline -baselineVersion=1 ..."
flyway \
  -url="${JDBC_URL}" \
  -user="${DB_USERNAME}" \
  -password="${DB_PASSWORD}" \
  -baselineVersion=1 \
  -baselineDescription="Pre-Flyway schema baseline" \
  baseline

if [ $? -ne 0 ]; then
  fail "flyway baseline command failed. See output above."
fi

# ─── Post-baseline verification ───────────────────────────────────────────────

log "Verifying baseline entry in flyway_schema_history..."
RESULT=$(psql_query \
  "SELECT version, description, type, success \
   FROM flyway_schema_history \
   WHERE type = 'BASELINE';" \
  | tr -d '[:space:]')

if [ -z "${RESULT}" ]; then
  fail "No BASELINE entry found in flyway_schema_history after baseline command."
fi

log "Baseline applied successfully."
log "flyway_schema_history BASELINE row: ${RESULT}"
log ""
log "Next steps:"
log "  1. Start the application with SPRING_PROFILES_ACTIVE=prod"
log "  2. Flyway will skip V1 (already baselined) and apply V2+ if present"
log "  3. Verify: curl https://\${APP_HOST}/actuator/health"
