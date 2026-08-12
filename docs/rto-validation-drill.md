# RTO Validation Drill — Pipeline Troubleshooting Assistant

**Version:** 1.0 | **Frequency:** Quarterly | **Owner:** Engineering lead

---

## Table of Contents

1. [Purpose and Frequency](#1-purpose-and-frequency)
2. [Prerequisites Checklist](#2-prerequisites-checklist)
3. [Drill Procedure with Timing Checkpoints](#3-drill-procedure-with-timing-checkpoints)
4. [Pass/Fail Criteria](#4-passfail-criteria)
5. [Results Recording Template](#5-results-recording-template)
6. [Post-Drill Review Questions](#6-post-drill-review-questions)
7. [Remediation Tracking](#7-remediation-tracking)
8. [Example Drill Execution](#8-example-drill-execution)

---

## 1. Purpose and Frequency

This drill validates that the team can restore the Pipeline Troubleshooting Assistant from backup within the committed Recovery Time Objective of **30 minutes** and within the Recovery Point Objective of **24 hours**.

**Why run drills:**
- Unexercised runbooks go stale — commands change, tools drift, credentials rotate.
- Operators who have rehearsed the procedure under non-emergency conditions recover faster under pressure.
- Timing data identifies which steps are slowest so infrastructure improvements target the right bottlenecks.

**Frequency:** Once per quarter (ideally within 30 days of a major infrastructure change).

**Scheduling:** Book a 90-minute block to allow 30 min for the drill + 30 min for debrief + buffer for unexpected issues.

**Who should participate:**
- Primary operator (executes the runbook)
- Observer (watches but does not help — simulates a second on-call unfamiliar with the system)
- Timekeeper (records checkpoint times)

---

## 2. Prerequisites Checklist

Complete all items before declaring T+0.

### Environment
- [ ] Docker and Docker Compose v2 are installed on the drill machine
- [ ] `psql` (PostgreSQL 16 client) is installed: `psql --version`
- [ ] `aws` CLI v2 is installed (only if testing off-site restore): `aws --version`
- [ ] `.env` file is present in `pipeline-troubleshooting-assistant/` with valid credentials
- [ ] `docker compose up -d` succeeds and all services are healthy: `docker compose ps`
- [ ] The backup container has produced at least one backup file: `docker compose exec backup ls /backups/`

### Backup file
- [ ] Note the filename to be used for the drill restore:
  ```
  DRILL_BACKUP_FILE=pipeline_assistant_YYYYMMDD_HHMMSS.dump
  ```
- [ ] Confirm the backup file is non-zero: `docker compose exec backup ls -lh /backups/${DRILL_BACKUP_FILE}`

### Communication
- [ ] Notify the team that a planned drill is starting (use the Slack channel `#pipeline-ai-ops`)
- [ ] (Optional) Set a monitoring alert suppression window of 45 minutes if applicable

### Runbook access
- [ ] Confirm the runbook is accessible offline or in a second browser tab: `docs/backup-restore-runbook.md`

---

## 3. Drill Procedure with Timing Checkpoints

Record actual times at each checkpoint. The target elapsed time at T+30 means the application must be serving traffic within 30 minutes of T+0.

---

### T+0 — Disaster declared

**Action:** Record the start time. Declare the simulated disaster.

```bash
# Record start time
DRILL_START=$(date -u +%Y-%m-%dT%H:%M:%SZ)
echo "Drill started: ${DRILL_START}"
```

**Simulated disaster:** Stop PostgreSQL and remove the data volume to simulate total volume loss.

```bash
cd pipeline-troubleshooting-assistant

# Stop all services
docker compose down

# Remove the postgres data volume (irreversible for the drill — confirm before running)
docker volume rm pipeline-troubleshooting-assistant_postgres_data

# Verify volume is gone
docker volume ls | grep postgres_data
# Expected: no output (volume does not exist)
```

**Checkpoint:** Record `T_START` (wall-clock time).

---

### T+5 — Backup identified

**Action:** Identify the backup file to restore. If local volume was also lost, identify the S3 file.

**Local backup path (volume intact):**
```bash
docker compose up -d backup db
docker compose exec backup ls -lt /backups/ | head -5
```

**Off-site path (both volumes lost):**
```bash
aws s3 ls s3://${S3_BUCKET}/backups/ --recursive \
  ${S3_ENDPOINT:+--endpoint-url "${S3_ENDPOINT}"} | tail -10
```

**Checkpoint T+5 pass criteria:** Backup filename identified and noted. If no backup is found within 5 minutes, record FAIL for this checkpoint.

---

### T+10 — Backup download complete (off-site scenario only)

**Action:** Download the backup from S3.

```bash
aws s3 cp \
  s3://${S3_BUCKET}/backups/YYYY/MM/${DRILL_BACKUP_FILE} \
  ./${DRILL_BACKUP_FILE} \
  ${S3_ENDPOINT:+--endpoint-url "${S3_ENDPOINT}"}

docker compose cp ./${DRILL_BACKUP_FILE} backup:/backups/${DRILL_BACKUP_FILE}
```

**Checkpoint T+10 pass criteria:** File exists locally and in the backup container volume. Skip this checkpoint if restoring from local volume (set to N/A).

---

### T+15 — Restore started

**Action:** Stop the backend to prevent partial writes during restore. Begin the restore.

```bash
# Ensure db is running
docker compose up -d db
# Wait for health check
until docker compose exec -T db pg_isready -U ${POSTGRES_USER} -q 2>/dev/null; do
  echo "Waiting for db..."; sleep 3
done

# Stop backend before restore
docker compose stop backend

# Run restore
docker compose exec backup /scripts/restore.sh \
  --file /backups/${DRILL_BACKUP_FILE} \
  --target-db ${POSTGRES_DB}
```

**Checkpoint T+15 pass criteria:** `restore.sh` command has been invoked. Check the JSON output status field — it must not be `"failure"`.

---

### T+20 — Restore complete

**Action:** Confirm `restore.sh` has exited with status `success`.

**Expected output:**
```json
{"timestamp":"...","status":"success","filename":"...","target_db":"pipeline_assistant","duration_seconds":3,"exit_code":0}
```

If `pg_restore` emitted non-fatal warnings (e.g., `role ... does not exist`), the restore still succeeded — `--no-owner` handles ownership differences.

**Checkpoint T+20 pass criteria:** JSON output shows `"status":"success"` and `"exit_code":0`.

---

### T+25 — Verification passed

**Action:** Run the verification queries to confirm data integrity.

```bash
docker compose exec db psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -c "
  SELECT 'error_knowledge_base' AS tbl, COUNT(*) AS rows FROM error_knowledge_base
  UNION ALL
  SELECT 'analyzed_logs', COUNT(*) FROM analyzed_logs;
"
```

```bash
docker compose exec db psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -c "
  SELECT version, description, success
  FROM flyway_schema_history
  ORDER BY installed_rank;
"
```

**Checkpoint T+25 pass criteria:**
- `error_knowledge_base` row count ≥ 6
- All Flyway migration rows show `success = t`
- No error from `analyzed_logs` query (count may be 0)

---

### T+30 — Application serving traffic

**Action:** Restart the backend and confirm the API responds correctly.

```bash
docker compose start backend

# Wait up to 30 seconds for backend health
ELAPSED=0
until curl -sf http://localhost:8080/api/errors >/dev/null 2>&1; do
  sleep 3; ELAPSED=$((ELAPSED+3))
  if [ "${ELAPSED}" -ge 30 ]; then echo "Backend did not start in time"; break; fi
done

# Confirm knowledge base is populated
curl -sf http://localhost:8080/api/errors | python3 -m json.tool | head -20
```

**Checkpoint T+30 pass criteria:**
- `curl http://localhost:8080/api/errors` returns HTTP 200
- Response is a non-empty JSON array
- RTO = `T+30 elapsed time` ≤ 30 minutes

---

## 4. Pass/Fail Criteria

| Criterion | Target | Pass Condition | Fail Condition |
|-----------|--------|----------------|----------------|
| **RTO** | ≤ 30 minutes | Application serving traffic before T+30 mark | Application not serving at T+30 |
| **RPO** | ≤ 24 hours | Latest backup timestamp is within 24 hours of drill start | Latest backup is >24 hours old |
| Backup identified | T+5 | Backup filename noted within 5 minutes | No backup found in 5 minutes |
| Restore completes without error | T+20 | `restore.sh` exits 0 and outputs `"status":"success"` | `restore.sh` exits non-zero |
| Data integrity verified | T+25 | `error_knowledge_base` has ≥6 rows, Flyway history all success | Missing tables or Flyway failures |
| API health | T+30 | `GET /api/errors` returns HTTP 200 non-empty | HTTP error or empty response |
| No credential exposure | Throughout | No passwords appear in terminal output or logs | Any plaintext password visible |

---

## 5. Results Recording Template

Copy this section to a new file named `drill-results-YYYY-MM-DD.md` after each drill.

```markdown
# Drill Results — YYYY-MM-DD

**Date:** YYYY-MM-DD
**Participants:** <names and roles>
**Environment:** <local Docker Compose / staging / production-like>
**Backup file used:** pipeline_assistant_YYYYMMDD_HHMMSS.dump
**Backup file age at drill start:** <HH hours MM minutes>

## Timing

| Checkpoint | Target | Actual | Delta |
|------------|--------|--------|-------|
| T+0: Disaster declared | 00:00 | | |
| T+5: Backup identified | 00:05 | | |
| T+10: Download complete | 00:10 | | (N/A if local) |
| T+15: Restore started | 00:15 | | |
| T+20: Restore complete | 00:20 | | |
| T+25: Verification passed | 00:25 | | |
| T+30: App serving traffic | 00:30 | | |
| **Total elapsed** | **≤30 min** | | |

## Pass/Fail Matrix

| Criterion | Result | Notes |
|-----------|--------|-------|
| RTO ≤ 30 minutes | PASS / FAIL | |
| RPO ≤ 24 hours | PASS / FAIL | |
| Backup identified at T+5 | PASS / FAIL / N/A | |
| Restore completed without error | PASS / FAIL | |
| Data integrity verified | PASS / FAIL | |
| API health confirmed | PASS / FAIL | |
| No credential exposure | PASS / FAIL | |

## Issues Encountered

1. <Issue description, step where it occurred, time lost, resolution>
2. ...

## Runbook Gaps Identified

1. <Step that was unclear, missing, or incorrect>
2. ...
```

---

## 6. Post-Drill Review Questions

Answer these in the debrief session immediately after the drill while memory is fresh.

1. **Did the drill reveal any step in the runbook that was unclear, incomplete, or incorrect?** Which step, and what was wrong?

2. **Were all required tools available and working without additional setup?** (Docker, psql, aws CLI, etc.)

3. **Were credentials available and current?** Were any secrets expired or rotated since the last drill?

4. **Which step took longer than expected?** What was the bottleneck — network, disk I/O, human decision time, unclear instructions?

5. **Did any step require knowledge not in the runbook?** Should that knowledge be documented?

6. **Was the observer able to follow along without prior context?** Did they have questions that indicated runbook gaps?

7. **What is the actual RTO achieved?** If it exceeded 30 minutes, what was the primary cause of the overage?

8. **What changes would reduce the RTO by the most time?** (Larger backup instance, pre-warmed environment, automated tooling, etc.)

9. **Are the escalation contacts current?** Did anyone's role or contact information change since the last drill?

10. **Should the next drill test a different scenario?** (Full volume loss vs. partial table restore vs. off-site restore.)

---

## 7. Remediation Tracking

When a drill uncovers issues, record them here and schedule follow-up work.

| Issue | Severity | Owner | Target Completion | Status |
|-------|----------|-------|-------------------|--------|
| *example: restore.sh not found in container PATH* | High | Platform team | 2026-09-01 | Open |

**Severity definitions:**
- **High:** RTO target cannot be met without this fix — re-drill required after remediation.
- **Medium:** Step was slow or confusing but RTO was still met — fix before next scheduled drill.
- **Low:** Minor clarity improvement — incorporate into runbook at next revision.

**Re-drill policy:** If the drill fails the RTO criterion (total elapsed > 30 minutes), schedule a re-drill within 4 weeks after remediations are applied.

---

## 8. Example Drill Execution

The following is an example drill executed against the local Docker Compose environment to validate this document.

**Date:** 2026-08-12
**Environment:** Local Docker Compose on Ubuntu 22.04, Docker 26.1.4
**Participants:** Primary operator (author), no observer
**Backup file used:** `pipeline_assistant_20260812_020003.dump` (12 hours old, 44 KB)

### Timing results

| Checkpoint | Target | Actual | Delta |
|------------|--------|--------|-------|
| T+0: Disaster declared | 00:00 | 00:00 | 0 min |
| T+5: Backup identified | 00:05 | 00:01 | −4 min |
| T+10: Download complete | 00:10 | N/A (local) | N/A |
| T+15: Restore started | 00:15 | 00:04 | −11 min |
| T+20: Restore complete | 00:20 | 00:05 | −15 min |
| T+25: Verification passed | 00:25 | 00:07 | −18 min |
| T+30: App serving traffic | 00:30 | 00:11 | −19 min |
| **Total elapsed** | **≤30 min** | **11 min** | **19 min margin** |

### Pass/Fail matrix

| Criterion | Result | Notes |
|-----------|--------|-------|
| RTO ≤ 30 minutes | **PASS** | Completed in 11 minutes |
| RPO ≤ 24 hours | **PASS** | Backup was 12 hours old |
| Backup identified at T+5 | **PASS** | File visible immediately via `ls /backups/` |
| Restore completed without error | **PASS** | `restore.sh` exited 0; 2 non-fatal `pg_restore` warnings about role ownership (expected with `--no-owner`) |
| Data integrity verified | **PASS** | `error_knowledge_base` count = 6, Flyway history 3 rows all success |
| API health confirmed | **PASS** | `GET /api/errors` returned 200 with 6 knowledge base entries |
| No credential exposure | **PASS** | `.pgpass` mechanism used throughout; no passwords in terminal |

### Terminal output highlights

```
$ docker compose down
[+] Running 5/5

$ docker volume rm pipeline-troubleshooting-assistant_postgres_data
pipeline-troubleshooting-assistant_postgres_data

$ docker compose up -d db backup
[+] Running 3/3

$ docker compose exec backup /scripts/restore.sh \
    --file /backups/pipeline_assistant_20260812_020003.dump \
    --target-db pipeline_assistant
{"timestamp":"2026-08-12T14:52:17Z","status":"success","filename":"pipeline_assistant_20260812_020003.dump","target_db":"pipeline_assistant","duration_seconds":1,"exit_code":0}

$ docker compose exec db psql -U pipeline_user -d pipeline_assistant \
    -c "SELECT 'error_knowledge_base', COUNT(*) FROM error_knowledge_base \
        UNION ALL SELECT 'analyzed_logs', COUNT(*) FROM analyzed_logs;"
   ?column?            | count
------------------------+-------
 error_knowledge_base  |     6
 analyzed_logs         |     0
(2 rows)

$ docker compose start backend
$ curl -sf http://localhost:8080/api/errors | python3 -m json.tool | head -5
[
    {
        "id": 1,
        "category": "Memory",
        "errorPattern": "OutOfMemoryError, heap space, java.lang.OutOfMemoryError",
```

### Issues encountered

None. The `pg_restore` warning `role "pipeline_user" does not exist` appeared twice but is non-fatal — `--no-owner` skips ownership changes. The runbook documents this expected behavior.

### Runbook gaps identified

- The runbook should clarify that `pg_restore` role-not-found warnings are expected and harmless when using `--no-owner`. *(Addressed in Section 5 of the runbook.)*

### Observations

- The dominant time cost was waiting for the backend Spring Boot startup (~8 seconds), not the restore itself (1 second).
- Local Docker environment is well within the 30-minute RTO. The off-site scenario (downloading from S3) would add approximately 5–10 minutes depending on file size and network, still comfortably within target.
- Re-drill for off-site scenario recommended when S3 integration is enabled in staging/production.
