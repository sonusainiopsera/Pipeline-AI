#!/bin/bash
set -euo pipefail

# ─── Validate required environment variables ─────────────────────────────────
MISSING=""
for var in DB_HOST DB_NAME DB_USER DB_PASSWORD; do
    eval "val=\${${var}:-}"
    if [ -z "${val}" ]; then
        MISSING="${MISSING} ${var}"
    fi
done
if [ -n "${MISSING}" ]; then
    echo "ERROR: required environment variables not set:${MISSING}" >&2
    exit 1
fi

: "${DB_PORT:=5432}"
: "${BACKUP_CRON_SCHEDULE:=0 2 * * *}"
: "${BACKUP_RETENTION_DAYS:=30}"

# ─── Create .pgpass for non-interactive pg_dump authentication ─────────────────
# Written fresh on each startup so it always reflects the current credentials.
PGPASSFILE_GLOBAL="/root/.pgpass"
printf '%s:%s:%s:%s:%s\n' \
    "${DB_HOST}" "${DB_PORT}" "${DB_NAME}" "${DB_USER}" "${DB_PASSWORD}" \
    > "${PGPASSFILE_GLOBAL}"
chmod 600 "${PGPASSFILE_GLOBAL}"

# ─── Install crontab with substituted schedule ────────────────────────────────
CRONTAB_SRC="/scripts/crontab.tmpl"
CRONTAB_INSTALLED="/etc/crontabs/root"

# Replace placeholder with actual schedule value
sed "s|BACKUP_CRON_SCHEDULE_PLACEHOLDER|${BACKUP_CRON_SCHEDULE}|g" \
    "${CRONTAB_SRC}" > "${CRONTAB_INSTALLED}"

chmod 600 "${CRONTAB_INSTALLED}"

echo "Backup container started. Schedule: ${BACKUP_CRON_SCHEDULE}  Retention: ${BACKUP_RETENTION_DAYS} days"

# ─── Run crond in foreground ──────────────────────────────────────────────────
exec crond -f -l 2
