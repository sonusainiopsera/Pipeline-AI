#!/usr/bin/env bash
# test-nginx-headers.sh — validates that the frontend Nginx container returns
# all required security headers on every response type.
#
# Usage:
#   ./scripts/test-nginx-headers.sh
#
# Requirements: docker, curl
# The script builds the frontend image, starts a temporary container, runs
# assertions, then cleans up regardless of pass/fail.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
FRONTEND_DIR="${REPO_DIR}/frontend"

IMAGE_NAME="pipeline-frontend-header-test"
CONTAINER_NAME="nginx-header-test-$$"
PORT=18080
BASE_URL="http://localhost:${PORT}"

PASS=0
FAIL=0

# ── Helpers ────────────────────────────────────────────────────────────────────

log()  { echo "[INFO]  $*"; }
ok()   { echo "[PASS]  $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL]  $*"; FAIL=$((FAIL + 1)); }

cleanup() {
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

assert_header() {
    local headers="$1"
    local header_name="$2"
    local expected_value="$3"

    local actual
    actual=$(echo "${headers}" | grep -i "^${header_name}:" | head -1 | sed 's/^[^:]*: //' | tr -d '\r')

    if [ -z "${actual}" ]; then
        fail "${header_name} — MISSING (expected: ${expected_value})"
    elif [ "${actual}" = "${expected_value}" ]; then
        ok "${header_name}: ${actual}"
    else
        fail "${header_name} — wrong value\n       expected: ${expected_value}\n       actual:   ${actual}"
    fi
}

assert_header_absent_pattern() {
    local headers="$1"
    local header_name="$2"
    local forbidden_pattern="$3"
    local description="$4"

    local actual
    actual=$(echo "${headers}" | grep -i "^${header_name}:" | head -1 | tr -d '\r')

    if echo "${actual}" | grep -qi "${forbidden_pattern}"; then
        fail "${description} — '${actual}' contains forbidden pattern '${forbidden_pattern}'"
    else
        ok "${description} — no version disclosure in: '${actual}'"
    fi
}

# ── Build image ────────────────────────────────────────────────────────────────

log "Building frontend Docker image: ${IMAGE_NAME}"
docker build \
    --quiet \
    --tag "${IMAGE_NAME}" \
    "${FRONTEND_DIR}"

# ── Start container ────────────────────────────────────────────────────────────

log "Starting Nginx container on port ${PORT}"
docker run \
    --detach \
    --name "${CONTAINER_NAME}" \
    --publish "${PORT}:80" \
    "${IMAGE_NAME}" >/dev/null

# Wait for Nginx to become healthy (up to 15 seconds)
log "Waiting for Nginx to become ready..."
for i in $(seq 1 15); do
    if curl -sf "${BASE_URL}" >/dev/null 2>&1; then
        log "Nginx ready after ${i}s"
        break
    fi
    sleep 1
done

# ── Fetch headers ──────────────────────────────────────────────────────────────

log "Fetching headers from ${BASE_URL}/"
ROOT_HEADERS=$(curl -sI "${BASE_URL}/")

log "Fetching headers from a SPA sub-route ${BASE_URL}/analyze"
ANALYZE_HEADERS=$(curl -sI "${BASE_URL}/analyze")

log "Fetching headers from a static asset path"
# Any .js file at root — Vite always emits at least one
ASSET_PATH=$(curl -sf "${BASE_URL}/" | grep -o 'src="[^"]*\.js"' | head -1 | sed 's/src="//;s/"//')
if [ -n "${ASSET_PATH}" ]; then
    ASSET_HEADERS=$(curl -sI "${BASE_URL}${ASSET_PATH}")
else
    log "Could not detect asset path — skipping static-asset header check"
    ASSET_HEADERS=""
fi

log "Fetching headers from a non-existent path (404 test)"
NOTFOUND_HEADERS=$(curl -sI "${BASE_URL}/does-not-exist-xyz")

# ── Assertions: root document ──────────────────────────────────────────────────

echo ""
echo "── Root (/) ─────────────────────────────────────────────────────────────"

assert_header "${ROOT_HEADERS}" \
    "Content-Security-Policy" \
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'"

assert_header "${ROOT_HEADERS}" \
    "Strict-Transport-Security" \
    "max-age=31536000; includeSubDomains"

assert_header "${ROOT_HEADERS}" \
    "X-Content-Type-Options" \
    "nosniff"

assert_header "${ROOT_HEADERS}" \
    "X-Frame-Options" \
    "DENY"

assert_header "${ROOT_HEADERS}" \
    "X-XSS-Protection" \
    "0"

assert_header "${ROOT_HEADERS}" \
    "Referrer-Policy" \
    "strict-origin-when-cross-origin"

assert_header "${ROOT_HEADERS}" \
    "Permissions-Policy" \
    "camera=(), microphone=(), geolocation=()"

assert_header_absent_pattern "${ROOT_HEADERS}" \
    "Server" \
    "nginx/[0-9]" \
    "Server header must not expose Nginx version"

# ── Assertions: SPA sub-route ─────────────────────────────────────────────────

echo ""
echo "── SPA sub-route (/analyze) ─────────────────────────────────────────────"

assert_header "${ANALYZE_HEADERS}" \
    "Content-Security-Policy" \
    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'"

assert_header "${ANALYZE_HEADERS}" \
    "X-Frame-Options" \
    "DENY"

assert_header "${ANALYZE_HEADERS}" \
    "X-Content-Type-Options" \
    "nosniff"

# ── Assertions: static assets (only if path detected) ─────────────────────────

if [ -n "${ASSET_HEADERS}" ]; then
    echo ""
    echo "── Static asset (${ASSET_PATH}) ─────────────────────────────────────────"

    assert_header "${ASSET_HEADERS}" \
        "Content-Security-Policy" \
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'"

    assert_header "${ASSET_HEADERS}" \
        "X-Content-Type-Options" \
        "nosniff"

    assert_header "${ASSET_HEADERS}" \
        "Cache-Control" \
        "public, immutable"
fi

# ── Assertions: SPA fallback on unknown route (should return 200 + index.html) ─

echo ""
echo "── 404 / SPA fallback (/does-not-exist-xyz) ─────────────────────────────"

# SPA: try_files falls back to index.html, so status is 200, not 404
NOTFOUND_STATUS=$(echo "${NOTFOUND_HEADERS}" | head -1 | awk '{print $2}')
if [ "${NOTFOUND_STATUS}" = "200" ]; then
    ok "SPA fallback — unknown route returns 200 (index.html)"
else
    fail "SPA fallback — expected 200, got ${NOTFOUND_STATUS}"
fi

assert_header "${NOTFOUND_HEADERS}" \
    "X-Frame-Options" \
    "DENY"

assert_header "${NOTFOUND_HEADERS}" \
    "X-Content-Type-Options" \
    "nosniff"

# ── Summary ────────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "  Results: ${PASS} passed, ${FAIL} failed"
echo "═══════════════════════════════════════════════════════════════════════════"

if [ "${FAIL}" -gt 0 ]; then
    echo "FAILURE — ${FAIL} assertion(s) did not pass."
    exit 1
else
    echo "SUCCESS — all security header assertions passed."
    exit 0
fi
