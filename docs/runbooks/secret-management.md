# Secret Management Runbook

**Owner:** Platform / DevOps Engineering  
**Last reviewed:** 2026-08-12  
**Classification:** Internal — do not share publicly

This runbook is the single source of truth for all credential-related operational procedures for the Pipeline Troubleshooting Assistant. It covers secret inventory, rotation procedures, incident response, environment-specific guidance, and the production secret management roadmap.

---

## Table of Contents

1. [Secret Inventory](#1-secret-inventory)
2. [Rotation Procedures](#2-rotation-procedures)
3. [Incident Response Playbook](#3-incident-response-playbook)
4. [Environment-Specific Guidance](#4-environment-specific-guidance)
5. [Future-State Recommendation](#5-future-state-recommendation)

---

## 1. Secret Inventory

All secrets and environment-specific values managed by this system. Variables marked **Secret** must never be committed to version control.

| Variable | Type | Storage Location | Rotation Cadence | Owner | Notes |
|----------|------|-----------------|-----------------|-------|-------|
| `POSTGRES_PASSWORD` | Secret | `.env` (local), runner env (CI), managed store (prod) | 90 days or on suspected compromise | Platform Engineer | Used by the PostgreSQL container; must match `DB_PASSWORD` |
| `POSTGRES_USER` | Config | `.env` (local), runner env (CI) | On personnel change | Platform Engineer | PostgreSQL superuser name; low sensitivity |
| `POSTGRES_DB` | Config | `.env` (local), runner env (CI) | Rarely | Platform Engineer | Database name; not secret |
| `DB_URL` | Config | `.env` (local), runner env (CI) | On infrastructure change | Platform Engineer | JDBC connection URL; contains host/port but not credentials |
| `DB_USERNAME` | Config | `.env` (local), runner env (CI) | On personnel change | Platform Engineer | Must match `POSTGRES_USER` |
| `DB_PASSWORD` | Secret | `.env` (local), runner env (CI), managed store (prod) | 90 days or on suspected compromise | Platform Engineer | Must match `POSTGRES_PASSWORD` |
| `APP_CORS_ALLOWED_ORIGINS` | Config | `.env` (local), runner env (CI) | On frontend URL change | Platform Engineer | Not a secret; environment-specific allowed origins |
| `JWT_SECRET` *(future)* | Secret | `.env` (local), managed store (prod) | 90 days or on suspected compromise | Security Engineer | Signs JWT access tokens; rotation invalidates all active sessions |
| `MFA_ENCRYPTION_KEY` *(future)* | Secret | Managed secret store only | 180 days or on suspected compromise | Security Engineer | Encrypts TOTP seeds in the database; rotation requires re-encryption of all stored MFA secrets |

**Template:** `pipeline-troubleshooting-assistant/.env.example`  
**Active file (never commit):** `pipeline-troubleshooting-assistant/.env`

---

## 2. Rotation Procedures

### 2.1 PostgreSQL Password Rotation

**Estimated time:** 5–10 minutes  
**Blast radius:** Backend API unavailable for ~30 seconds during container restart  
**Required access:** Docker host with `docker compose` access; write access to `.env`

#### Pre-rotation checklist

- [ ] Confirm no long-running database migrations or bulk operations are in progress
- [ ] Notify the team in `#engineering` before rotating (brief window of downtime)
- [ ] Have the current password recorded in a temporary secure location for rollback

#### Step-by-step procedure

```bash
# 1. Navigate to the project directory
cd pipeline-troubleshooting-assistant

# 2. Generate a strong new password (Linux/macOS)
NEW_PASS=$(openssl rand -base64 32 | tr -d '=+/' | head -c 32)
echo "New password generated (copy to secure storage): $NEW_PASS"

# 3. Update .env — replace the old POSTGRES_PASSWORD and DB_PASSWORD values
#    Edit .env and set both variables to the new password:
#      POSTGRES_PASSWORD=<new-password>
#      DB_PASSWORD=<new-password>
#    (POSTGRES_USER and DB_USERNAME should match; DB_URL does not contain the password)

# 4. Apply the new password to the running PostgreSQL container
#    (Updates the postgres superuser password without data loss)
docker compose exec db psql -U <current-POSTGRES_USER> -c \
  "ALTER USER <POSTGRES_USER> PASSWORD '<new-password>';"

# 5. Restart only the backend to pick up the new DB_PASSWORD from .env
docker compose up -d --no-deps backend

# 6. Verify connectivity (wait up to 30s for the backend to come up)
sleep 10
curl -sf http://localhost:8080/actuator/health | grep -q '"status":"UP"' && \
  echo "VERIFICATION PASSED" || echo "VERIFICATION FAILED — see rollback"

# 7. Confirm the database itself is reachable
docker compose exec db pg_isready -U <POSTGRES_USER> -d <POSTGRES_DB>
```

#### Verification

- `GET /actuator/health` returns `{"status":"UP"}` (HTTP 200)
- `GET /api/history` returns HTTP 200 (confirms database read path)
- No `Connection refused` or `authentication failed` errors in `docker compose logs backend`

#### Rollback procedure

If verification fails within 5 minutes:

```bash
# 1. Revert .env to the previous password
#    Set POSTGRES_PASSWORD and DB_PASSWORD back to the old value

# 2. Revert the password in PostgreSQL
docker compose exec db psql -U <POSTGRES_USER> -c \
  "ALTER USER <POSTGRES_USER> PASSWORD '<old-password>';"

# 3. Restart the backend
docker compose up -d --no-deps backend

# 4. Verify health again
curl -sf http://localhost:8080/actuator/health
```

**Common failure modes:**

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `password authentication failed` | `.env` change not picked up | Run `docker compose up -d --no-deps backend` to force env reload |
| `FATAL: remaining connection slots reserved` | Active connections prevent ALTER USER | Run `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='<POSTGRES_DB>';` then retry |
| Backend container crash-loops | `.env` has mismatched `DB_PASSWORD` vs `POSTGRES_PASSWORD` | Ensure both variables are identical in `.env` |

---

### 2.2 JWT Signing Key Rotation *(future — P1 Authentication Epic)*

> **Status:** Placeholder. Full procedure will be written when `JWT_SECRET` is introduced in the authentication epic.

**Estimated time:** 2–3 minutes  
**Blast radius:** All active user sessions are invalidated; users must re-login

#### Key points

1. Update `JWT_SECRET` in `.env` (and in the managed secret store for staging/production)
2. Restart the backend service: `docker compose up -d --no-deps backend`
3. Verify health endpoint returns UP
4. **User impact:** Every active JWT becomes invalid immediately. Issue a pre-rotation announcement to users at least 15 minutes in advance so they can save work.
5. For zero-downtime rotation, implement key versioning in the JWT validator (accepts both old and new key for a grace period) before removing the old key.

---

### 2.3 MFA Encryption Key Rotation *(future — P2 MFA Epic)*

> **Status:** Placeholder. Full procedure will be written when `MFA_ENCRYPTION_KEY` is introduced.

**Estimated time:** 30–60 minutes (includes database re-encryption)  
**Blast radius:** MFA login unavailable during re-encryption; no data loss

#### Key points

1. Generate a new `MFA_ENCRYPTION_KEY`
2. Run the migration script to re-encrypt all `mfa_secret` values in the `users` table using the new key
3. Update `MFA_ENCRYPTION_KEY` in `.env` / managed secret store
4. Restart the backend
5. Verify MFA login for a test account
6. This is the highest-impact rotation — schedule during a maintenance window

---

### 2.4 Rotating Multiple Secrets Simultaneously

If a broad credential leak requires rotating several secrets at once:

1. **Rotate `POSTGRES_PASSWORD` / `DB_PASSWORD` first** — database credentials carry the highest blast radius
2. **Rotate `JWT_SECRET` second** — session invalidation is disruptive but recoverable immediately
3. **Rotate `MFA_ENCRYPTION_KEY` last** — requires the most time due to database re-encryption

Update all values in `.env` before restarting any services, then restart in order: `db` → `backend` → confirm health → proceed.

---

## 3. Incident Response Playbook

Use this playbook whenever a secret is suspected or confirmed to have been exposed (committed to git, logged, printed to stdout, etc.).

### Severity classification

| Severity | Criteria | Response Time |
|----------|---------|--------------|
| P0 — Critical | Secret confirmed in a public repository, log aggregator, or external service | 15 minutes |
| P1 — High | Secret confirmed in a private repository commit reachable by the whole team | 1 hour |
| P2 — Medium | Secret found locally but not yet pushed; pre-commit hook would have blocked | 4 hours |

### Step 1 — Immediately rotate the compromised credential

Do not wait for investigation. Rotate first, investigate second.

```bash
# Follow the appropriate rotation procedure (Section 2) for the affected secret.
# Record the time of rotation in the incident log.
```

### Step 2 — Determine exposure scope

```bash
# Find all commits that introduced or touched the file containing the secret
git log --all --full-history -- path/to/affected/file

# Search the full git history for the secret value pattern
git log --all -p | grep -i '<pattern-describing-the-secret>'

# Check if the commit was pushed to any remotes
git log --oneline --remotes='*' | head -20
```

If the commit was pushed to a **public repository or fork**, proceed to Step 3 immediately. GitHub caches content even after force-push removal; contact GitHub Support via the Security Advisory process (see Step 4).

### Step 3 — Remove the secret from git history (if pushed)

> **Warning:** History rewriting is destructive and requires coordination with everyone who has cloned the repository.

#### Option A: BFG Repo-Cleaner (recommended — faster than filter-branch)

```bash
# 1. Download BFG
curl -Lo bfg.jar https://repo1.maven.org/maven2/com/madgicaltechdom/bfg/2.14.0/bfg-2.14.0.jar

# 2. Create a file containing the secret string to scrub (one per line)
echo '<the-secret-value>' > secrets-to-remove.txt

# 3. Run BFG against a bare clone of the repository
git clone --mirror git@github.com:<org>/<repo>.git
java -jar bfg.jar --replace-text secrets-to-remove.txt <repo>.git

# 4. Clean the clone and force-push
cd <repo>.git
git reflog expire --expire=now --all && git gc --prune=now --aggressive
git push --force

# 5. Notify all contributors to re-clone or run:
#    git fetch origin && git reset --hard origin/main
```

#### Option B: git filter-repo (modern alternative)

```bash
pip install git-filter-repo
git filter-repo --replace-text secrets-to-remove.txt --force
git push --force-with-lease
```

#### After history rewrite

- Invalidate all GitHub deploy keys and personal access tokens that had access during the exposure window
- Ask GitHub Support to purge cached views of the affected commits (use the Security Advisory contact form)
- Rotate the GitHub Actions secrets that CI uses for the repository

### Step 4 — Notify affected parties

| Condition | Action | Timeline |
|-----------|--------|---------|
| Secret exposes internal infrastructure only | Notify `#engineering` and `#security` Slack channels | Within 1 hour of detection |
| Secret exposes customer data (database credentials that may have been used) | Notify security lead; initiate customer data breach assessment | Within 15 minutes |
| Public repository exposure | Notify GitHub Security, rotate all tokens with repo access, consider public disclosure | Within 30 minutes |
| Compliance-regulated data involved (PII, PCI) | Escalate to Legal/DPO immediately; do not wait for technical containment | Immediately |

### Step 5 — Post-incident review

Complete within 5 business days of the incident.

**Checklist:**

- [ ] Timeline documented: when was the secret introduced, when detected, when rotated
- [ ] Root cause identified: why did the pre-commit hook not catch it (e.g., `--no-verify` used, hook not installed, new machine)
- [ ] Detection gap closed: update hook patterns, add CI rule, or update onboarding docs
- [ ] Blast radius confirmed: confirm no unauthorized access occurred using database audit logs or cloud provider access logs
- [ ] `.gitignore` / `.pre-commit-config.yaml` updated if new file type needs exclusion
- [ ] Runbook updated if any procedure in this document was found to be incomplete

---

## 4. Environment-Specific Guidance

### 4.1 Local Development

- **Storage:** `pipeline-troubleshooting-assistant/.env` (git-ignored)
- **Setup:** `cp .env.example .env` then fill in placeholder values
- **Security level:** Lowest — use non-production credentials only; never reuse staging/production passwords locally
- **Rotation:** Not required on a fixed schedule; rotate if the machine is compromised or the developer leaves the team

```bash
# Verify .env is not tracked
git status pipeline-troubleshooting-assistant/.env
# Expected: nothing (file should not appear in git status output)
```

### 4.2 CI/CD (GitHub Actions)

- **Storage:** GitHub Actions encrypted secrets (`Settings → Secrets and variables → Actions`)
- **Variables used:**
  - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — set as repository secrets for the `backend` CI job
  - `POSTGRES_PASSWORD` — set as a service container env var in the workflow (see `.github/workflows/ci.yml`)
- **Rotation:** Update via the GitHub UI under repository secrets; rotation takes effect on the next workflow run
- **Access control:** Limit secret access to required environments; use environment-scoped secrets for staging/production jobs

```yaml
# Example: referencing secrets in ci.yml
env:
  DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
  DB_USERNAME: ${{ secrets.DB_USERNAME }}
```

### 4.3 Staging

- **Recommended approach:** Docker Secrets (if using Docker Swarm) or environment variables injected at deploy time by the CI/CD pipeline
- **Do not** commit staging credentials to any file in the repository
- Staging secrets should be distinct from production secrets — never share passwords between environments
- Use a dedicated PostgreSQL user with the minimum required privileges (not the superuser)

```bash
# Docker Swarm secret (staging)
echo '<staging-db-password>' | docker secret create db_password -
# Reference in docker-compose.yml:
#   secrets:
#     - db_password
#   environment:
#     DB_PASSWORD_FILE: /run/secrets/db_password
```

### 4.4 Production

See [Section 5](#5-future-state-recommendation) for the recommended managed secret store approach. Until a managed store is in place, use the CI/CD pipeline's native secret injection (e.g., GitHub Environments with environment protection rules and required reviewers).

**Minimum requirements for production:**

- [ ] Production secrets are distinct from staging secrets
- [ ] Secrets are never stored in any file that could be committed to git
- [ ] Access to production secrets is audited and limited to `platform-engineer` role
- [ ] MFA is enforced for any account that can read or update production secrets
- [ ] Secret values are rotated within 90 days or immediately upon personnel change

---

## 5. Future-State Recommendation

The current `.env` file approach is suitable for local development and early-stage deployments but does not meet the operational and audit requirements of a production system. Below is a comparison of the three most viable managed secret solutions for this project's Docker Compose deployment model.

### 5.1 Options Comparison

| Criteria | HashiCorp Vault | AWS Secrets Manager | Docker Secrets |
|----------|----------------|---------------------|----------------|
| **Deployment model** | Self-hosted or HCP Vault | AWS-managed SaaS | Docker Swarm built-in |
| **Complexity** | High — requires Vault server, policies, auth methods | Low — API-based, fully managed | Low — native Docker primitive |
| **Cost** | Free (OSS) or $0.03/secret/month (HCP) | $0.40/secret/month + $0.05/10k API calls | Free |
| **Secret rotation** | Dynamic secrets (auto-rotated Postgres creds) | Native rotation with Lambda hooks | Manual |
| **Audit log** | Full audit log (paid tier or OSS with backend) | CloudTrail integration | None |
| **App changes required** | SDK or agent sidecar needed | AWS SDK or sidecar | Read from `/run/secrets/<name>` |
| **Works with Docker Compose** | Yes (Vault agent sidecar or envoy) | Yes (AWS SDK in app or sidecar) | Docker Swarm only; not Compose standalone |
| **Best fit** | Teams wanting centralized secrets with dynamic rotation | Teams already on AWS | Teams fully committed to Docker Swarm |

### 5.2 Recommendation

**For the current Docker Compose deployment model: AWS Secrets Manager** (if the team is on AWS) or **HashiCorp Vault OSS** (if cloud-agnostic).

**Rationale:**

- Docker Secrets requires Docker Swarm, which this project does not currently use
- AWS Secrets Manager offers the lowest operational overhead if the infrastructure is AWS-hosted, with native RDS integration for automatic PostgreSQL password rotation
- HashiCorp Vault OSS provides the richest feature set (dynamic secrets, fine-grained policies, full audit trail) and is the best long-term choice if the team grows beyond a single cloud provider

### 5.3 Migration Path from `.env` to Managed Secrets

The migration can be completed in a single sprint with no application downtime:

```
Phase 1 — Parallel run (Week 1)
  ├── Deploy chosen secret store alongside existing .env setup
  ├── Store all secrets in the managed store without changing the application
  └── Verify secrets are readable from the managed store

Phase 2 — Switch over (Week 2)
  ├── Update application startup to read secrets from the managed store
  │   (e.g., Spring Boot property source backed by Vault/SecretsManager)
  ├── Remove secrets from .env for all non-local environments
  └── Update CI/CD to inject secrets from the managed store, not GitHub secrets

Phase 3 — Cleanup (Week 3)
  ├── Remove .env.example entries for secrets now in the managed store
  ├── Update this runbook with new rotation procedures
  └── Update onboarding documentation
```

**For AWS Secrets Manager with Spring Boot**, use `spring-cloud-aws-secrets-manager-config` which maps secrets directly to Spring properties, requiring zero application code changes.

**For HashiCorp Vault with Spring Boot**, use `spring-cloud-vault-config` with the `database` secrets engine for dynamic short-lived PostgreSQL credentials.

---

## Appendix: Quick Reference

### Key file locations

| File | Purpose |
|------|---------|
| `pipeline-troubleshooting-assistant/.env` | Active secrets (git-ignored, never commit) |
| `pipeline-troubleshooting-assistant/.env.example` | Placeholder template for onboarding |
| `.githooks/pre-commit` | Blocks commits containing hardcoded secrets |
| `scripts/install-hooks.sh` | Installs the pre-commit hook for new developers |
| `docs/runbooks/secret-management.md` | This document |

### Emergency contacts

| Role | Contact | Escalation trigger |
|------|---------|-------------------|
| Platform Engineer (on-call) | See PagerDuty rotation | P0/P1 incident |
| Security Lead | See internal directory | Any confirmed secret leak |
| Legal / DPO | See internal directory | Customer PII or regulated data exposure |
| GitHub Support | https://support.github.com | Public repository cache purge needed |
