#!/bin/bash
# Weekly restore verification: restore the latest backup to a temporary database,
# validate data integrity, then drop the temporary database.
#
# The temporary database is NEVER the production database.
# A trap ensures cleanup on any exit path.
#
# Required env: DB_HOST, DB_USER, DB_PASSWORD
# Optional env: DB_PORT (default 5432), DB_NAME (for PGPASSFILE scope)
#
# Exit code: number of failed checks (0 = all pass). Each failure is logged before exit.

set -uo pipefail

: "${DB_HOST:?DB_HOST must be set}"
: "${DB_PORT:=5432}"
: "${DB_USER:?DB_USER must be set}"
: "${DB_PASSWORD:?DB_PASSWORD must be set}"

VERIFY_DB="pipeline_assistant_verify"
BACKUP_DIR="/backups"
START_EPOCH="$(date +%s)"
FAILURES=0

# ─── Logging helpers ──────────────────────────────────────────────────────────
log_check() {
    local check_name="$1"
    local status="$2"
    local detail="${3:-}"
    printf '{"timestamp":"%s","check":"%s","status":"%s","detail":"%s"}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${check_name}" "${status}" "${detail}"
}

# ─── .pgpass setup ────────────────────────────────────────────────────────────
PGPASSFILE="/tmp/.pgpass_verify_$$"
printf '%s:%s:*:%s:%s\n' \
    "${DB_HOST}" "${DB_PORT}" "${DB_USER}" "${DB_PASSWORD}" \
    > "${PGPASSFILE}"
chmod 600 "${PGPASSFILE}"
export PGPASSFILE

# ─── Cleanup trap: always drop the verify database on exit ───────────────────
cleanup() {
    dropdb \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --username="${DB_USER}" \
        --if-exists \
        "${VERIFY_DB}" \
        2>/dev/null || true
    rm -f "${PGPASSFILE}"
}
trap cleanup EXIT

# ─── Check that at least one backup file exists ───────────────────────────────
LATEST_DUMP="$(ls -t "${BACKUP_DIR}"/*.dump 2>/dev/null | head -1 || true)"
if [ -z "${LATEST_DUMP}" ]; then
    printf '{"timestamp":"%s","status":"skipped","message":"no backup files found in %s — nothing to verify"}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${BACKUP_DIR}"
    exit 0
fi

FILENAME="$(basename "${LATEST_DUMP}")"
printf '{"timestamp":"%s","status":"info","message":"verifying backup","filename":"%s"}\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${FILENAME}"

# ─── Drop verify database if it already exists (previous failed run) ──────────
dropdb \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --username="${DB_USER}" \
    --if-exists \
    "${VERIFY_DB}" \
    2>/dev/null || true

# ─── Create temporary verify database ────────────────────────────────────────
if ! createdb \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --username="${DB_USER}" \
        "${VERIFY_DB}" \
        2>/dev/null; then
    log_check "create_verify_db" "FAIL" "createdb ${VERIFY_DB} failed"
    FAILURES=$((FAILURES + 1))
    exit "${FAILURES}"
fi
log_check "create_verify_db" "PASS" "created ${VERIFY_DB}"

# ─── Restore latest backup into verify database ───────────────────────────────
RESTORE_STDERR="/tmp/verify_restore_stderr_$$"
pg_restore \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --username="${DB_USER}" \
    --dbname="${VERIFY_DB}" \
    --no-owner \
    "${LATEST_DUMP}" \
    2>"${RESTORE_STDERR}"
PG_RESTORE_EXIT=$?

if [ "${PG_RESTORE_EXIT}" -ne 0 ]; then
    SANITIZED_ERR="$(sed 's/password=[^ ]*/password=[REDACTED]/gi; s/:[^:@]*@/:[REDACTED]@/g' \
        "${RESTORE_STDERR}" 2>/dev/null | head -3 || echo "pg_restore failed")"
    rm -f "${RESTORE_STDERR}"
    log_check "pg_restore" "FAIL" "${SANITIZED_ERR}"
    FAILURES=$((FAILURES + 1))
    exit "${FAILURES}"
fi
rm -f "${RESTORE_STDERR}"
log_check "pg_restore" "PASS" "restored ${FILENAME} into ${VERIFY_DB}"

# ─── Helper: run a psql query and capture output ──────────────────────────────
run_sql() {
    psql \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --username="${DB_USER}" \
        --dbname="${VERIFY_DB}" \
        --no-align \
        --tuples-only \
        --command="$1" \
        2>/dev/null
}

# ─── Check 1: error_knowledge_base table exists ───────────────────────────────
TABLE_CHECK_KB="$(run_sql \
    "SELECT COUNT(*) FROM information_schema.tables \
     WHERE table_schema='public' AND table_name='error_knowledge_base';" \
    || echo "0")"
TABLE_CHECK_KB="$(printf '%s' "${TABLE_CHECK_KB}" | tr -d ' ')"
if [ "${TABLE_CHECK_KB}" = "1" ]; then
    log_check "table_exists_error_knowledge_base" "PASS" "table found in information_schema"
else
    log_check "table_exists_error_knowledge_base" "FAIL" "table not found (count=${TABLE_CHECK_KB})"
    FAILURES=$((FAILURES + 1))
fi

# ─── Check 2: analyzed_logs table exists ─────────────────────────────────────
TABLE_CHECK_AL="$(run_sql \
    "SELECT COUNT(*) FROM information_schema.tables \
     WHERE table_schema='public' AND table_name='analyzed_logs';" \
    || echo "0")"
TABLE_CHECK_AL="$(printf '%s' "${TABLE_CHECK_AL}" | tr -d ' ')"
if [ "${TABLE_CHECK_AL}" = "1" ]; then
    log_check "table_exists_analyzed_logs" "PASS" "table found in information_schema"
else
    log_check "table_exists_analyzed_logs" "FAIL" "table not found (count=${TABLE_CHECK_AL})"
    FAILURES=$((FAILURES + 1))
fi

# ─── Check 3: error_knowledge_base row count > 0 ─────────────────────────────
ROW_COUNT_KB="$(run_sql "SELECT COUNT(*) FROM error_knowledge_base;" || echo "0")"
ROW_COUNT_KB="$(printf '%s' "${ROW_COUNT_KB}" | tr -d ' ')"
if [ -n "${ROW_COUNT_KB}" ] && [ "${ROW_COUNT_KB}" -gt 0 ] 2>/dev/null; then
    log_check "row_count_error_knowledge_base" "PASS" "count=${ROW_COUNT_KB}"
else
    log_check "row_count_error_knowledge_base" "FAIL" "expected >0 rows, got: ${ROW_COUNT_KB}"
    FAILURES=$((FAILURES + 1))
fi

# ─── Check 4: analyzed_logs row count (warn if 0, do not fail — may be empty) ─
ROW_COUNT_AL="$(run_sql "SELECT COUNT(*) FROM analyzed_logs;" || echo "error")"
ROW_COUNT_AL="$(printf '%s' "${ROW_COUNT_AL}" | tr -d ' ')"
if [ "${ROW_COUNT_AL}" = "error" ]; then
    log_check "row_count_analyzed_logs" "FAIL" "query failed"
    FAILURES=$((FAILURES + 1))
else
    log_check "row_count_analyzed_logs" "PASS" "count=${ROW_COUNT_AL}"
fi

# ─── Check 5: aggregate query matching categoryCounts() behavior ─────────────
# Mirrors: SELECT a.category, COUNT(a) FROM AnalyzedLog a GROUP BY a.category
AGGREGATE_RESULT="$(run_sql \
    "SELECT category, COUNT(*) FROM analyzed_logs GROUP BY category ORDER BY category;" \
    || echo "error")"
if printf '%s' "${AGGREGATE_RESULT}" | grep -q "error"; then
    log_check "category_aggregate_query" "FAIL" "aggregate query failed"
    FAILURES=$((FAILURES + 1))
else
    # Collapse multi-line result to a single-line summary for JSON
    RESULT_SUMMARY="$(printf '%s' "${AGGREGATE_RESULT}" | tr '\n' ';' | sed 's/;$//')"
    log_check "category_aggregate_query" "PASS" "${RESULT_SUMMARY:-no rows}"
fi

# ─── Final summary ────────────────────────────────────────────────────────────
END_EPOCH="$(date +%s)"
DURATION=$((END_EPOCH - START_EPOCH))

if [ "${FAILURES}" -eq 0 ]; then
    printf '{"timestamp":"%s","status":"success","filename":"%s","duration_seconds":%d,"checks_passed":5,"checks_failed":0}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${FILENAME}" "${DURATION}"
else
    printf '{"timestamp":"%s","status":"failure","filename":"%s","duration_seconds":%d,"checks_passed":%d,"checks_failed":%d}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${FILENAME}" "${DURATION}" \
        "$((5 - FAILURES))" "${FAILURES}"
fi

exit "${FAILURES}"
