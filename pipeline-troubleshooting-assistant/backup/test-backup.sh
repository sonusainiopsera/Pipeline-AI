#!/bin/bash
# Integration test script for the backup container.
# Run from the pipeline-troubleshooting-assistant/ directory:
#   cd pipeline-troubleshooting-assistant && ./backup/test-backup.sh
set -euo pipefail

COMPOSE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${COMPOSE_DIR}"

PASS=0
FAIL=0
DUMP_FILE=""

log_step() { printf '\n[TEST] %s\n' "$1"; }
pass()      { printf '  PASS: %s\n' "$1"; PASS=$((PASS + 1)); }
fail()      { printf '  FAIL: %s\n' "$1"; FAIL=$((FAIL + 1)); }

cleanup() {
    log_step "Tearing down services"
    docker compose down -v --remove-orphans 2>/dev/null || true
}
trap cleanup EXIT

# ─── 1. Start postgres and backup services ────────────────────────────────────
log_step "Starting postgres (db) and backup services"
docker compose up -d db backup

# ─── 2. Wait for postgres health check to pass ────────────────────────────────
log_step "Waiting for PostgreSQL to become healthy"
TIMEOUT=60
ELAPSED=0
until docker compose exec -T db pg_isready -U "${POSTGRES_USER:-pipeline_user}" -q 2>/dev/null; do
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    if [ "${ELAPSED}" -ge "${TIMEOUT}" ]; then
        fail "PostgreSQL did not become healthy within ${TIMEOUT}s"
        exit 1
    fi
done
pass "PostgreSQL is healthy"

# ─── 3. Seed the database with test data ──────────────────────────────────────
log_step "Seeding test data"
docker compose exec -T db psql \
    -U "${POSTGRES_USER:-pipeline_user}" \
    -d "${POSTGRES_DB:-pipeline_assistant}" \
    <<'SQL'
INSERT INTO error_knowledge_base (category, keywords, root_cause, suggested_fix, customer_update, severity)
VALUES
  ('Test Category A', 'connection refused timeout', 'Test root cause A', 'Test fix A', 'Test update A', 'HIGH'),
  ('Test Category B', 'out of memory heap', 'Test root cause B', 'Test fix B', 'Test update B', 'MEDIUM')
ON CONFLICT DO NOTHING;

INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence)
VALUES ('Test log entry for backup validation', 'Test Category A', 'root cause', 'fix', 'update', 'HIGH', 90)
ON CONFLICT DO NOTHING;
SQL
pass "Test data seeded"

# ─── 4. Trigger a manual backup ───────────────────────────────────────────────
log_step "Triggering manual backup"
docker compose exec -T backup /scripts/backup.sh
BACKUP_EXIT=$?

if [ "${BACKUP_EXIT}" -eq 0 ]; then
    pass "backup.sh exited with code 0"
else
    fail "backup.sh exited with code ${BACKUP_EXIT}"
fi

# ─── 5. Verify dump file exists and is non-empty ─────────────────────────────
log_step "Verifying dump file exists in backup volume"
DUMP_FILE="$(docker compose exec -T backup sh -c 'ls -t /backups/pipeline_assistant_*.dump 2>/dev/null | head -1')"
DUMP_FILE="${DUMP_FILE%$'\r'}"  # strip Windows line ending if any

if [ -n "${DUMP_FILE}" ]; then
    pass "Dump file found: ${DUMP_FILE}"
else
    fail "No dump file found in /backups"
    exit 1
fi

# Check file size > 0
DUMP_SIZE="$(docker compose exec -T backup sh -c "wc -c < '${DUMP_FILE}' | tr -d ' '")"
DUMP_SIZE="${DUMP_SIZE%$'\r'}"
if [ "${DUMP_SIZE:-0}" -gt 0 ]; then
    pass "Dump file is non-empty (${DUMP_SIZE} bytes)"
else
    fail "Dump file is empty or size could not be determined"
fi

# ─── 6. Verify pg_restore --list can read the file header ────────────────────
log_step "Verifying pg_restore --list can read dump file"
RESTORE_OUTPUT="$(docker compose exec -T backup pg_restore --list "${DUMP_FILE}" 2>&1)"

if echo "${RESTORE_OUTPUT}" | grep -q "TABLE DATA"; then
    pass "pg_restore --list successfully read the dump and found TABLE DATA entries"
else
    fail "pg_restore --list did not find TABLE DATA entries"
    echo "  pg_restore output: ${RESTORE_OUTPUT}"
fi

# ─── 7. Check backup logs for JSON success message ────────────────────────────
log_step "Verifying JSON success log from backup container"
BACKUP_LOGS="$(docker compose logs backup 2>/dev/null)"
if echo "${BACKUP_LOGS}" | grep -q '"status":"success"'; then
    pass "Backup container logged JSON success message"
else
    fail "No JSON success message found in backup logs"
    echo "  Backup logs: ${BACKUP_LOGS}"
fi

# Verify no credentials appear in logs
if echo "${BACKUP_LOGS}" | grep -qiP '(password|passwd|secret)\s*=\s*\S+(?<!=\[REDACTED\])'; then
    fail "Potential credential found in backup logs"
else
    pass "No raw credentials detected in backup logs"
fi

# ─── 8. Test retention.sh with BACKUP_RETENTION_DAYS=0 ───────────────────────
log_step "Testing retention cleanup with BACKUP_RETENTION_DAYS=0"
docker compose exec -T -e BACKUP_RETENTION_DAYS=0 backup /scripts/retention.sh

# After retention with 0 days, the file created above should be deleted
REMAINING="$(docker compose exec -T backup sh -c 'ls /backups/pipeline_assistant_*.dump 2>/dev/null | wc -l | tr -d " "')"
REMAINING="${REMAINING%$'\r'}"
if [ "${REMAINING:-1}" -eq 0 ]; then
    pass "Retention cleanup deleted backup files with BACKUP_RETENTION_DAYS=0"
else
    fail "Retention cleanup did not delete files (${REMAINING} remaining)"
fi

# ─── Results ─────────────────────────────────────────────────────────────────
printf '\n──────────────────────────────────────\n'
printf 'Results: %d passed, %d failed\n' "${PASS}" "${FAIL}"
if [ "${FAIL}" -gt 0 ]; then
    exit 1
fi
