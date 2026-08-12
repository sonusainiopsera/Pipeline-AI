#!/bin/bash
# Prune S3 objects in the backups/ prefix that are older than REMOTE_RETENTION_DAYS.
#
# Required env: S3_BUCKET
# Optional env: S3_ENDPOINT, S3_REGION (default us-east-1), S3_ACCESS_KEY,
#               S3_SECRET_KEY, REMOTE_RETENTION_DAYS (default 90)
#
# SECURITY: credentials are NEVER echoed or logged.

set -uo pipefail

: "${S3_BUCKET:?S3_BUCKET must be set}"
: "${S3_REGION:=us-east-1}"
: "${REMOTE_RETENTION_DAYS:=90}"

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

# ─── Compute cutoff date as YYYY-MM-DD string (busybox-compatible) ────────────
CUTOFF_EPOCH=$((START_EPOCH - REMOTE_RETENTION_DAYS * 86400))
CUTOFF_DATE="$(date -u -d "@${CUTOFF_EPOCH}" +%Y-%m-%d)"

printf '{"timestamp":"%s","status":"info","message":"scanning for objects older than %s days (before %s)"}\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${REMOTE_RETENTION_DAYS}" "${CUTOFF_DATE}"

# ─── List objects and delete those older than the cutoff ──────────────────────
DELETED=0
ERRORS=0

# aws s3 ls output format: "YYYY-MM-DD HH:MM:SS  SIZE  KEY"
# The date in column 1 is lexicographically comparable with CUTOFF_DATE.
while IFS= read -r line; do
    [ -z "${line}" ] && continue

    LINE_DATE="$(printf '%s' "${line}" | awk '{print $1}')"
    LINE_KEY="$(printf '%s' "${line}" | awk '{print $NF}')"

    # Skip if we can't parse the line or it's a directory entry (ends with /)
    [ -z "${LINE_DATE}" ] && continue
    [ -z "${LINE_KEY}" ] && continue
    case "${LINE_KEY}" in */) continue ;; esac

    # Lexicographic date comparison: LINE_DATE < CUTOFF_DATE means it is older
    if [ "${LINE_DATE}" \< "${CUTOFF_DATE}" ]; then
        DELETE_STDERR="/tmp/s3_del_stderr_$$"
        if aws "${AWS_CLI_ARGS[@]}" s3 rm "s3://${S3_BUCKET}/${LINE_KEY}" \
                >/dev/null 2>"${DELETE_STDERR}"; then
            DELETED=$((DELETED + 1))
            printf '{"timestamp":"%s","status":"deleted","key":"%s"}\n' \
                "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${LINE_KEY}"
        else
            ERRORS=$((ERRORS + 1))
            SANITIZED_ERR="$(sed 's/AKIA[A-Z0-9]*/[KEY_REDACTED]/g; s/Credential=[^,&]*/Credential=[REDACTED]/g' \
                "${DELETE_STDERR}" 2>/dev/null | head -2 || echo "delete failed")"
            printf '{"timestamp":"%s","status":"error","key":"%s","error_message":"%s"}\n' \
                "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${LINE_KEY}" "${SANITIZED_ERR}"
        fi
        rm -f "${DELETE_STDERR}"
    fi
done < <(aws "${AWS_CLI_ARGS[@]}" s3 ls "s3://${S3_BUCKET}/backups/" 2>/dev/null || true)

printf '{"timestamp":"%s","status":"success","message":"remote retention complete","deleted_count":%d,"error_count":%d,"retention_days":%s,"cutoff_date":"%s"}\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${DELETED}" "${ERRORS}" "${REMOTE_RETENTION_DAYS}" "${CUTOFF_DATE}"
