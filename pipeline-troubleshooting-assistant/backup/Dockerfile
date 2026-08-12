FROM postgres:16-alpine

# Install dcron (Alpine cron daemon), bash, and AWS CLI v2 for S3 off-site push
RUN apk add --no-cache dcron bash aws-cli

# Copy all scripts into /scripts and make executable
COPY backup.sh retention.sh entrypoint.sh \
     push-offsite.sh remote-retention.sh restore.sh verify-restore.sh \
     /scripts/
COPY crontab /scripts/crontab.tmpl
RUN chmod +x \
    /scripts/backup.sh \
    /scripts/retention.sh \
    /scripts/entrypoint.sh \
    /scripts/push-offsite.sh \
    /scripts/remote-retention.sh \
    /scripts/restore.sh \
    /scripts/verify-restore.sh

# Backup volume mount point
VOLUME ["/backups"]

ENTRYPOINT ["/scripts/entrypoint.sh"]
