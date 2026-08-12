#!/bin/bash
set -euo pipefail

# ─── Required environment variables ──────────────────────────────────────────
: "${DB_HOST:?DB_HOST must be set}"
: "${DB_PORT:=5432}"
: "${DB_NAME:?DB_NAME must be set}"
: "${DB_USER:?DB_USER must be set}"
: "${DB_PASSWORD:?DB_PASSWORD must be set}"
: "${BACKUP_RETENTION_DAYS:=30}"

BACKUP_DIR="/backups"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
FILENAME="pipeline_assistant_${TIMESTAMP}.dump"
FILEPATH="${BACKUP_DIR}/${FILENAME}"
START_EPOCH="$(date +%s)"

# ─── Logging helpers ──────────────────────────────────────────────────────────
log_json() {
    printf '%s\n' "$1"
}

# ─── .pgpass setup (credentials never appear in process list or logs) ─────────
PGPASSFILE="/tmp/.pgpass_backup"
printf '%s:%s:%s:%s:%s\n' \
    "${DB_HOST}" "${DB_PORT}" "${DB_NAME}" "${DB_USER}" "${DB_PASSWORD}" \
    > "${PGPASSFILE}"
chmod 600 "${PGPASSFILE}"
export PGPASSFILE

# ─── Wait for PostgreSQL to be ready ─────────────────────────────────────────
MAX_RETRIES=3
RETRY_INTERVAL=10
attempt=0
until pg_isready -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -q; do
    attempt=$((attempt + 1))
    if [ "${attempt}" -ge "${MAX_RETRIES}" ]; then
        log_json "$(printf '{"timestamp":"%s","status":"failure","error_message":"PostgreSQL not ready after %d attempts","exit_code":1}' \
            "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${MAX_RETRIES}")"
        rm -f "${PGPASSFILE}"
        exit 1
    fi
    log_json "$(printf '{"timestamp":"%s","status":"waiting","message":"PostgreSQL not ready, retry %d/%d in %ds"}' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${attempt}" "${MAX_RETRIES}" "${RETRY_INTERVAL}")"
    sleep "${RETRY_INTERVAL}"
done

# ─── Ensure backup directory exists with restricted permissions ───────────────
mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"

# ─── Execute pg_dump ──────────────────────────────────────────────────────────
DUMP_STDERR_FILE="/tmp/pg_dump_stderr_${TIMESTAMP}"
pg_dump \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --username="${DB_USER}" \
    --dbname="${DB_NAME}" \
    --format=custom \
    --compress=9 \
    --file="${FILEPATH}" \
    2>"${DUMP_STDERR_FILE}"
PG_EXIT=$?

END_EPOCH="$(date +%s)"
DURATION=$((END_EPOCH - START_EPOCH))

if [ "${PG_EXIT}" -ne 0 ]; then
    # Sanitize stderr — strip anything that looks like a connection string with credentials
    SANITIZED_ERR="$(sed 's/password=[^ ]*/password=[REDACTED]/gi; s/:[^:@]*@/:[REDACTED]@/g' "${DUMP_STDERR_FILE}" 2>/dev/null || echo "pg_dump failed")"
    rm -f "${FILEPATH}" "${DUMP_STDERR_FILE}" "${PGPASSFILE}"
    log_json "$(printf '{"timestamp":"%s","status":"failure","filename":"%s","duration_seconds":%d,"error_message":"%s","exit_code":%d}' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${FILENAME}" "${DURATION}" \
        "${SANITIZED_ERR}" "${PG_EXIT}")"
    exit 1
fi

rm -f "${DUMP_STDERR_FILE}" "${PGPASSFILE}"

# ─── Gather file metadata ─────────────────────────────────────────────────────
SIZE_BYTES=0
if [ -f "${FILEPATH}" ]; then
    SIZE_BYTES="$(wc -c < "${FILEPATH}" | tr -d ' ')"
fi

# Count tables using pg_restore --list (count lines that reference TABLE DATA)
TABLES_COUNT=0
if command -v pg_restore >/dev/null 2>&1; then
    TABLES_COUNT="$(pg_restore --list "${FILEPATH}" 2>/dev/null | grep -c "TABLE DATA" || true)"
fi

log_json "$(printf '{"timestamp":"%s","status":"success","filename":"%s","size_bytes":%s,"duration_seconds":%d,"tables_count":%d}' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${FILENAME}" "${SIZE_BYTES}" "${DURATION}" "${TABLES_COUNT}")"

# ─── Run retention cleanup on success ────────────────────────────────────────
if [ -f "/scripts/retention.sh" ]; then
    /scripts/retention.sh
fi

# ─── Push to off-site S3 storage (warning-only, does not fail the backup) ─────
# S3_BUCKET must be set in the environment to enable off-site push.
if [ -n "${S3_BUCKET:-}" ] && [ -f "/scripts/push-offsite.sh" ]; then
    /scripts/push-offsite.sh || {
        PUSH_EXIT=$?
        log_json "$(printf '{"timestamp":"%s","status":"warning","message":"off-site push failed with exit code %d — local backup is intact","filename":"%s"}' \
            "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${PUSH_EXIT}" "${FILENAME}")"
    }
fi
