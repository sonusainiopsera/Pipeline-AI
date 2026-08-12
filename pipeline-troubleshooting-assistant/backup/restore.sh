#!/bin/bash
# Restore a pg_dump backup file to a PostgreSQL database.
#
# Usage: restore.sh --file <backup_path> [--target-db <db_name>]
#
# Required env: DB_HOST, DB_USER, DB_PASSWORD
# Optional env: DB_PORT (default 5432)
#
# SECURITY: credentials are passed via PGPASSFILE, never via CLI args or logs.

set -euo pipefail

# ─── Argument parsing ─────────────────────────────────────────────────────────
BACKUP_FILE=""
TARGET_DB="pipeline_assistant"

while [ $# -gt 0 ]; do
    case "$1" in
        --file)
            BACKUP_FILE="$2"
            shift 2
            ;;
        --target-db)
            TARGET_DB="$2"
            shift 2
            ;;
        *)
            printf '{"timestamp":"%s","status":"failure","error_message":"unknown argument: %s"}\n' \
                "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1"
            exit 1
            ;;
    esac
done

if [ -z "${BACKUP_FILE}" ]; then
    printf '{"timestamp":"%s","status":"failure","error_message":"--file argument is required"}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    exit 1
fi

# ─── Validate required environment variables ─────────────────────────────────
: "${DB_HOST:?DB_HOST must be set}"
: "${DB_PORT:=5432}"
: "${DB_USER:?DB_USER must be set}"
: "${DB_PASSWORD:?DB_PASSWORD must be set}"

# ─── Validate backup file ─────────────────────────────────────────────────────
if [ ! -f "${BACKUP_FILE}" ]; then
    printf '{"timestamp":"%s","status":"failure","error_message":"backup file not found: %s"}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${BACKUP_FILE}"
    exit 1
fi

FILE_SIZE="$(wc -c < "${BACKUP_FILE}" | tr -d ' ')"
if [ "${FILE_SIZE}" -eq 0 ]; then
    printf '{"timestamp":"%s","status":"failure","error_message":"backup file is empty: %s"}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${BACKUP_FILE}"
    exit 1
fi

FILENAME="$(basename "${BACKUP_FILE}")"
START_EPOCH="$(date +%s)"

# ─── .pgpass setup (credentials never appear in process list or logs) ─────────
PGPASSFILE="/tmp/.pgpass_restore_$$"
printf '%s:%s:*:%s:%s\n' \
    "${DB_HOST}" "${DB_PORT}" "${DB_USER}" "${DB_PASSWORD}" \
    > "${PGPASSFILE}"
chmod 600 "${PGPASSFILE}"
export PGPASSFILE

# ─── Execute pg_restore ───────────────────────────────────────────────────────
RESTORE_STDERR="/tmp/pg_restore_stderr_$$"
pg_restore \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --username="${DB_USER}" \
    --dbname="${TARGET_DB}" \
    --clean \
    --if-exists \
    --no-owner \
    "${BACKUP_FILE}" \
    2>"${RESTORE_STDERR}"
PG_EXIT=$?

END_EPOCH="$(date +%s)"
DURATION=$((END_EPOCH - START_EPOCH))

rm -f "${PGPASSFILE}"

if [ "${PG_EXIT}" -ne 0 ]; then
    SANITIZED_ERR="$(sed 's/password=[^ ]*/password=[REDACTED]/gi;
                          s/:[^:@]*@/:[REDACTED]@/g' \
        "${RESTORE_STDERR}" 2>/dev/null | head -5 || echo "pg_restore failed")"
    rm -f "${RESTORE_STDERR}"
    printf '{"timestamp":"%s","status":"failure","filename":"%s","target_db":"%s","duration_seconds":%d,"error_message":"%s","exit_code":%d}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${FILENAME}" "${TARGET_DB}" \
        "${DURATION}" "${SANITIZED_ERR}" "${PG_EXIT}"
    exit 1
fi

rm -f "${RESTORE_STDERR}"

printf '{"timestamp":"%s","status":"success","filename":"%s","target_db":"%s","duration_seconds":%d,"exit_code":0}\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${FILENAME}" "${TARGET_DB}" "${DURATION}"
