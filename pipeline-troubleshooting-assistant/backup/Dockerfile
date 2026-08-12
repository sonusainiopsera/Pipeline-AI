FROM postgres:16-alpine

# Install dcron (Alpine cron daemon) and bash
RUN apk add --no-cache dcron bash

# Copy all scripts into /scripts and make executable
COPY backup.sh retention.sh entrypoint.sh /scripts/
COPY crontab /scripts/crontab.tmpl
RUN chmod +x /scripts/backup.sh /scripts/retention.sh /scripts/entrypoint.sh

# Backup volume mount point
VOLUME ["/backups"]

ENTRYPOINT ["/scripts/entrypoint.sh"]
