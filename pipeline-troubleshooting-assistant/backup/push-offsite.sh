#!/bin/bash
# Upload the latest local backup to S3-compatible off-site storage.
#
# Required env: S3_BUCKET
# Optional env: S3_ENDPOINT (non-AWS e.g. MinIO), S3_REGION (default us-east-1),
#               S3_ACCESS_KEY, S3_SECRET_KEY, PUSH_TIMEOUT_SECONDS (default 60),
#               BACKUP_S3_AUTO_CREATE_BUCKET (default false)
#
# Exit code: non-zero on failure. Callers (backup.sh) treat this as a warning,
# not a fatal error — local backup success is the primary success criterion.
#
# SECURITY: credentials are NEVER echoed or logged. AWS CLI reads them from
# the exported environment variables AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY.
# Error messages from the aws cli are sanitized before being included in JSON.

set -uo pipefail

: "${S3_BUCKET:?S3_BUCKET must be set}"
: "${S3_REGION:=us-east-1}"
: "${PUSH_TIMEOUT_SECONDS:=60}"
: "${BACKUP_S3_AUTO_CREATE_BUCKET:=false}"

BACKUP_DIR="/backups"
START_EPOCH="$(date +%s)"

# ─── Export credentials to AWS CLI environment (never logged) ─────────────────
if [ -n "${S3_ACCESS_KEY:-}" ]; then
    export AWS_ACCESS_KEY_ID="${S3_ACCESS_KEY}"
fi
if [ -n "${S3_SECRET_KEY:-}" ]; then
    export AWS_SECRET_ACCESS_KEY="${S3_SECRET_KEY}"
fi
export AWS_DEFAULT_REGION="${S3_REGION}"

# ─── Build optional --endpoint-url argument array ────────────────────────────
AWS_CLI_ARGS=()
if [ -n "${S3_ENDPOINT:-}" ]; then
    AWS_CLI_ARGS+=("--endpoint-url" "${S3_ENDPOINT}")
fi
S3_ENDPOINT_DISPLAY="${S3_ENDPOINT:-aws}"

# ─── Find the most-recently-modified .dump file ───────────────────────────────
LATEST_DUMP="$(ls -t "${BACKUP_DIR}"/*.dump 2>/dev/null | head -1 || true)"
if [ -z "${LATEST_DUMP}" ]; then
    printf '{"timestamp":"%s","status":"skipped","message":"no backup files found in %s"}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${BACKUP_DIR}"
    exit 0
fi

FILENAME="$(basename "${LATEST_DUMP}")"
YEAR="$(date -u +%Y)"
MONTH="$(date -u +%m)"
S3_KEY="backups/${YEAR}/${MONTH}/${FILENAME}"
S3_URI="s3://${S3_BUCKET}/${S3_KEY}"

# ─── Sanitize aws cli stderr (strip any credential-like strings) ──────────────
sanitize_err() {
    sed 's/AKIA[A-Z0-9]*/[KEY_REDACTED]/g;
         s/Credential=[^,&]*/Credential=[REDACTED]/g;
         s/Signature=[^&]*/Signature=[REDACTED]/g;
         s/AWS_SECRET[^=]*=[^ ]*/[SECRET_REDACTED]/g' 2>/dev/null | head -3 || echo "aws cli error"
}

# ─── Verify bucket is accessible (create if auto-create enabled) ──────────────
STDERR_FILE="/tmp/s3_push_stderr_$$"
if ! timeout "${PUSH_TIMEOUT_SECONDS}" aws "${AWS_CLI_ARGS[@]}" s3 ls "s3://${S3_BUCKET}" \
        >/dev/null 2>"${STDERR_FILE}"; then
    if [ "${BACKUP_S3_AUTO_CREATE_BUCKET}" = "true" ]; then
        if ! timeout "${PUSH_TIMEOUT_SECONDS}" aws "${AWS_CLI_ARGS[@]}" s3 mb "s3://${S3_BUCKET}" \
                >/dev/null 2>>"${STDERR_FILE}"; then
            SANITIZED_ERR="$(sanitize_err < "${STDERR_FILE}")"
            rm -f "${STDERR_FILE}"
            printf '{"timestamp":"%s","status":"warning","message":"S3 bucket creation failed","endpoint":"%s","error_message":"%s"}\n' \
                "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${S3_ENDPOINT_DISPLAY}" "${SANITIZED_ERR}"
            exit 1
        fi
        printf '{"timestamp":"%s","status":"info","message":"created S3 bucket %s"}\n' \
            "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${S3_BUCKET}"
    else
        SANITIZED_ERR="$(sanitize_err < "${STDERR_FILE}")"
        rm -f "${STDERR_FILE}"
        printf '{"timestamp":"%s","status":"warning","message":"S3 bucket not accessible and auto-create is disabled","endpoint":"%s","bucket":"%s","error_message":"%s"}\n' \
            "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${S3_ENDPOINT_DISPLAY}" "${S3_BUCKET}" "${SANITIZED_ERR}"
        exit 1
    fi
fi
rm -f "${STDERR_FILE}"

# ─── Upload backup file ────────────────────────────────────────────────────────
FILE_SIZE="$(wc -c < "${LATEST_DUMP}" | tr -d ' ')"
UPLOAD_STDERR="/tmp/s3_upload_stderr_$$"

if ! timeout "${PUSH_TIMEOUT_SECONDS}" aws "${AWS_CLI_ARGS[@]}" s3 cp \
        "${LATEST_DUMP}" "${S3_URI}" >/dev/null 2>"${UPLOAD_STDERR}"; then
    UPLOAD_EXIT=$?
    SANITIZED_ERR="$(sanitize_err < "${UPLOAD_STDERR}")"
    rm -f "${UPLOAD_STDERR}"
    END_EPOCH="$(date +%s)"
    DURATION=$((END_EPOCH - START_EPOCH))
    printf '{"timestamp":"%s","status":"warning","message":"S3 upload failed","endpoint":"%s","filename":"%s","duration_seconds":%d,"error_message":"%s","exit_code":%d}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${S3_ENDPOINT_DISPLAY}" "${FILENAME}" \
        "${DURATION}" "${SANITIZED_ERR}" "${UPLOAD_EXIT}"
    exit 1
fi

rm -f "${UPLOAD_STDERR}"
END_EPOCH="$(date +%s)"
DURATION=$((END_EPOCH - START_EPOCH))

printf '{"timestamp":"%s","status":"success","filename":"%s","s3_uri":"%s","size_bytes":%s,"duration_seconds":%d}\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${FILENAME}" "${S3_URI}" "${FILE_SIZE}" "${DURATION}"
