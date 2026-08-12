#!/bin/bash
set -euo pipefail

: "${BACKUP_RETENTION_DAYS:=30}"

BACKUP_DIR="/backups"

if [ ! -d "${BACKUP_DIR}" ]; then
    printf '{"timestamp":"%s","status":"skipped","message":"backup directory does not exist"}\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    exit 0
fi

# Find and delete .dump files older than BACKUP_RETENTION_DAYS
DELETED=0
while IFS= read -r -d '' file; do
    rm -f "${file}"
    DELETED=$((DELETED + 1))
done < <(find "${BACKUP_DIR}" -maxdepth 1 -name '*.dump' -mtime "+${BACKUP_RETENTION_DAYS}" -print0 2>/dev/null)

printf '{"timestamp":"%s","status":"success","message":"retention cleanup complete","deleted_count":%d,"retention_days":%s}\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${DELETED}" "${BACKUP_RETENTION_DAYS}"
