#!/bin/bash
# Integration test for the full off-site backup and restore lifecycle.
#
# Tests: upload to MinIO, verify S3 object, run verify-restore.sh, remote
# retention pruning, and restore.sh round-trip.
#
# Prerequisites: Docker, docker compose, aws CLI on the host.
# Usage: cd pipeline-troubleshooting-assistant && ./backup/test-offsite.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "${SCRIPT_DIR}")"
COMPOSE_BASE="${PROJECT_DIR}/docker-compose.yml"
COMPOSE_TEST="${PROJECT_DIR}/docker-compose.test.yml"

S3_BUCKET="pipeline-backups-test"
MINIO_ENDPOINT="http://localhost:9000"
MINIO_ROOT_USER="minioadmin"
MINIO_ROOT_PASSWORD="minioadmin"

PASS=0
FAIL=0

# ─── Helpers ──────────────────────────────────────────────────────────────────
log() { printf '[%s] %s\n' "$(date -u +%T)" "$*"; }
pass() { log "  PASS: $*"; PASS=$((PASS + 1)); }
fail() { log "  FAIL: $*"; FAIL=$((FAIL + 1)); }

# Run a command against the backup container
exec_backup() {
    docker compose \
        -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" \
        exec -T backup "$@"
}

# Run SQL against the database container
exec_psql() {
    docker compose \
        -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" \
        exec -T db psql \
        -U pipeline_user \
        -d pipeline_assistant \
        -t -A \
        -c "$1"
}

# ─── Teardown ─────────────────────────────────────────────────────────────────
teardown() {
    log "Tearing down test containers..."
    docker compose \
        -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" \
        down -v --remove-orphans 2>/dev/null || true
}
trap teardown EXIT

# ─── Start services ───────────────────────────────────────────────────────────
log "Starting postgres, backup, and minio services..."
docker compose \
    -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" \
    up -d db minio backup

log "Waiting for PostgreSQL to be healthy..."
MAX_WAIT=60
ELAPSED=0
until docker compose \
        -f "${COMPOSE_BASE}" -f "${COMPOSE_TEST}" \
        exec -T db pg_isready -U pipeline_user -q 2>/dev/null; do
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    if [ "${ELAPSED}" -ge "${MAX_WAIT}" ]; then
        log "ERROR: PostgreSQL did not become ready within ${MAX_WAIT}s"
        exit 1
    fi
done
log "PostgreSQL is ready."

log "Waiting for MinIO to be healthy..."
ELAPSED=0
until curl -sf "${MINIO_ENDPOINT}/minio/health/live" >/dev/null 2>&1; do
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    if [ "${ELAPSED}" -ge "${MAX_WAIT}" ]; then
        log "ERROR: MinIO did not become ready within ${MAX_WAIT}s"
        exit 1
    fi
done
log "MinIO is ready."

# ─── Create S3 bucket in MinIO ────────────────────────────────────────────────
log "Creating S3 bucket '${S3_BUCKET}' in MinIO..."
AWS_ACCESS_KEY_ID="${MINIO_ROOT_USER}" \
AWS_SECRET_ACCESS_KEY="${MINIO_ROOT_PASSWORD}" \
AWS_DEFAULT_REGION="us-east-1" \
    aws --endpoint-url "${MINIO_ENDPOINT}" s3 mb "s3://${S3_BUCKET}" 2>/dev/null || true
pass "S3 bucket created (or already exists)"

# ─── Seed database with 6 knowledge-base entries and 3 analyzed logs ──────────
log "Seeding database with 6 knowledge-base entries and 3 analyzed logs..."
exec_psql "
INSERT INTO error_knowledge_base (error_pattern, category, root_cause, solution, severity)
VALUES
  ('OutOfMemoryError, heap space, java.lang.OutOfMemoryError', 'Memory',
   'Java heap space exhausted during pipeline execution',
   'Increase JVM heap size using -Xmx flag or optimize memory usage in the build', 'HIGH'),
  ('connection refused, ECONNREFUSED, connection timeout', 'Network',
   'Service or dependency is unreachable during pipeline execution',
   'Verify network connectivity and ensure all required services are running', 'HIGH'),
  ('permission denied, access denied, EACCES, unauthorized', 'Permissions',
   'Insufficient permissions to access a resource or execute an operation',
   'Review and update file/directory permissions or IAM roles', 'MEDIUM'),
  ('npm ERR, node_modules, package.json, dependency resolution', 'Dependency',
   'Node.js package installation or dependency resolution failed',
   'Clear npm cache and delete node_modules, then reinstall dependencies', 'MEDIUM'),
  ('docker build failed, Dockerfile, image pull, container', 'Docker',
   'Docker image build or container operation failed during pipeline',
   'Review Dockerfile for errors and ensure base images are accessible', 'HIGH'),
  ('test failed, assertion error, junit, test suite', 'Testing',
   'One or more automated tests failed during the pipeline execution',
   'Review test output, fix failing tests, and ensure test environment is properly configured', 'MEDIUM')
ON CONFLICT DO NOTHING;
" >/dev/null

exec_psql "
INSERT INTO analyzed_logs (log_text, category, root_cause, suggested_fix, customer_update, severity, confidence)
VALUES
  ('java.lang.OutOfMemoryError: heap space', 'Memory',
   'Java heap space exhausted', 'Increase -Xmx',
   'Root cause identified: heap exhausted. Action: increase -Xmx', 'HIGH', 98),
  ('docker build failed in Dockerfile step', 'Docker',
   'Docker build failed', 'Review Dockerfile',
   'Root cause identified: Docker failure. Action: review Dockerfile', 'HIGH', 98),
  ('test failed: assertion error in junit', 'Testing',
   'Test assertions failed', 'Fix failing tests',
   'Root cause identified: test failure. Action: fix tests', 'MEDIUM', 76)
ON CONFLICT DO NOTHING;
" >/dev/null

KB_COUNT="$(exec_psql "SELECT COUNT(*) FROM error_knowledge_base;")"
AL_COUNT="$(exec_psql "SELECT COUNT(*) FROM analyzed_logs;")"
if [ "${KB_COUNT}" -ge 6 ] && [ "${AL_COUNT}" -ge 3 ]; then
    pass "Database seeded (kb=${KB_COUNT}, logs=${AL_COUNT})"
else
    fail "Database seed incomplete (kb=${KB_COUNT}, logs=${AL_COUNT})"
fi

# ─── Trigger manual backup ────────────────────────────────────────────────────
log "Triggering manual backup via backup container..."
exec_backup /scripts/backup.sh
pass "backup.sh completed with exit code 0"

# ─── Verify local dump file was created ──────────────────────────────────────
DUMP_COUNT="$(exec_backup sh -c 'ls /backups/*.dump 2>/dev/null | wc -l' | tr -d ' ')"
if [ "${DUMP_COUNT}" -ge 1 ]; then
    pass "Local dump file created (count=${DUMP_COUNT})"
else
    fail "No local dump file found in /backups"
fi

# ─── Verify S3 upload ─────────────────────────────────────────────────────────
log "Verifying S3 upload in MinIO..."
S3_LIST="$(AWS_ACCESS_KEY_ID="${MINIO_ROOT_USER}" \
           AWS_SECRET_ACCESS_KEY="${MINIO_ROOT_PASSWORD}" \
           AWS_DEFAULT_REGION="us-east-1" \
           aws --endpoint-url "${MINIO_ENDPOINT}" s3 ls "s3://${S3_BUCKET}/" --recursive 2>/dev/null || true)"

if printf '%s' "${S3_LIST}" | grep -q '\.dump'; then
    S3_KEY="$(printf '%s' "${S3_LIST}" | awk '{print $NF}' | grep '\.dump' | head -1)"
    pass "Dump file found in MinIO S3 bucket: ${S3_KEY}"

    # ── Compare sizes: local vs S3 ────────────────────────────────────────────
    LOCAL_SIZE="$(exec_backup sh -c 'wc -c < "$(ls -t /backups/*.dump | head -1)" | tr -d " "')"
    S3_SIZE="$(AWS_ACCESS_KEY_ID="${MINIO_ROOT_USER}" \
               AWS_SECRET_ACCESS_KEY="${MINIO_ROOT_PASSWORD}" \
               AWS_DEFAULT_REGION="us-east-1" \
               aws --endpoint-url "${MINIO_ENDPOINT}" s3api head-object \
               --bucket "${S3_BUCKET}" --key "${S3_KEY}" \
               --query ContentLength --output text 2>/dev/null || echo "0")"
    if [ "${LOCAL_SIZE}" = "${S3_SIZE}" ]; then
        pass "S3 object size matches local file (${LOCAL_SIZE} bytes)"
    else
        fail "Size mismatch: local=${LOCAL_SIZE} S3=${S3_SIZE}"
    fi
else
    fail "No dump files found in MinIO S3 bucket after backup"
fi

# ─── Run verify-restore.sh ────────────────────────────────────────────────────
log "Running verify-restore.sh..."
if exec_backup /scripts/verify-restore.sh; then
    pass "verify-restore.sh completed successfully (exit 0)"
else
    fail "verify-restore.sh failed with non-zero exit"
fi

# ─── Test remote retention (REMOTE_RETENTION_DAYS=0 should delete everything) ─
log "Testing remote retention with REMOTE_RETENTION_DAYS=0..."
exec_backup sh -c "REMOTE_RETENTION_DAYS=0 /scripts/remote-retention.sh" || true
S3_AFTER_RETENTION="$(AWS_ACCESS_KEY_ID="${MINIO_ROOT_USER}" \
                      AWS_SECRET_ACCESS_KEY="${MINIO_ROOT_PASSWORD}" \
                      AWS_DEFAULT_REGION="us-east-1" \
                      aws --endpoint-url "${MINIO_ENDPOINT}" s3 ls "s3://${S3_BUCKET}/" --recursive 2>/dev/null | wc -l | tr -d ' ')"
if [ "${S3_AFTER_RETENTION}" -eq 0 ]; then
    pass "Remote retention pruned all objects (bucket empty)"
else
    fail "Remote retention left ${S3_AFTER_RETENTION} objects in bucket (expected 0)"
fi

# ─── Test restore.sh round-trip ───────────────────────────────────────────────
log "Testing restore.sh round-trip..."
# Drop and recreate the database to verify restore populates it from scratch
exec_backup sh -c "
    dropdb --host=db --port=5432 --username=\"\${DB_USER}\" --if-exists pipeline_assistant_restore_test 2>/dev/null || true
    createdb --host=db --port=5432 --username=\"\${DB_USER}\" pipeline_assistant_restore_test
    LATEST=\$(ls -t /backups/*.dump | head -1)
    /scripts/restore.sh --file \"\${LATEST}\" --target-db pipeline_assistant_restore_test
    dropdb --host=db --port=5432 --username=\"\${DB_USER}\" --if-exists pipeline_assistant_restore_test
"
pass "restore.sh successfully restored backup to a test database"

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "────────────────────────────────────────────────────────────────"
printf 'Results: %d passed, %d failed\n' "${PASS}" "${FAIL}"
echo "────────────────────────────────────────────────────────────────"

if [ "${FAIL}" -gt 0 ]; then
    exit 1
fi
exit 0
