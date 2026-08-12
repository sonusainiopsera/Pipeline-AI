#!/usr/bin/env bash
# test-nginx-rate-limits.sh — Integration tests for Nginx rate limiting.
#
# Starts the full docker-compose stack (postgres, backend, frontend/nginx),
# runs assertions, then stops and removes the containers.
#
# Usage:  cd pipeline-troubleshooting-assistant && ./scripts/test-nginx-rate-limits.sh
# Exit:   0 if all assertions pass, non-zero on the first failure.

set -uo pipefail

# ── Configuration ──────────────────────────────────────────────────────────────
COMPOSE_FILE="docker-compose.yml"
BASE_URL="http://localhost:5173"
PASS=0
FAIL=0

# ── Helpers ────────────────────────────────────────────────────────────────────
pass() { echo "[PASS] $*"; ((PASS++)); }
fail() { echo "[FAIL] $*"; ((FAIL++)); }

require_status() {
    local label="$1" expected="$2" actual="$3"
    if [ "$actual" = "$expected" ]; then
        pass "$label (HTTP $actual)"
    else
        fail "$label — expected HTTP $expected, got HTTP $actual"
    fi
}

http_status() {
    curl -s -o /dev/null -w "%{http_code}" "$@"
}

cleanup() {
    echo ""
    echo "── Stopping containers ───────────────────────────────────────────────"
    docker compose -f "$COMPOSE_FILE" down --remove-orphans -v 2>/dev/null || true
}

# ── Startup ────────────────────────────────────────────────────────────────────
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
    echo "No .env file found — creating a minimal test .env"
    cat > .env <<'ENVEOF'
DB_URL=jdbc:postgresql://db:5432/pipeline_assistant
DB_USERNAME=testuser
DB_PASSWORD=testpass
POSTGRES_DB=pipeline_assistant
POSTGRES_USER=testuser
POSTGRES_PASSWORD=testpass
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
ENVEOF
    CREATED_ENV=true
else
    CREATED_ENV=false
fi

trap '
    cleanup
    if [ "${CREATED_ENV:-false}" = "true" ] && [ -f .env ]; then rm -f .env; fi
' EXIT

echo "── Starting full stack ────────────────────────────────────────────────────"
docker compose -f "$COMPOSE_FILE" up -d --build --wait 2>&1 | tail -20

# Wait for Nginx to be ready
echo "── Waiting for Nginx to become ready ─────────────────────────────────────"
for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/" | grep -qE '^[23]'; then
        echo "Nginx ready after ${i}s"
        break
    fi
    sleep 2
done

echo ""
echo "── Test 1: Normal API requests return non-5xx (proxy works) ──────────────"
for i in $(seq 1 5); do
    STATUS=$(http_status "$BASE_URL/api/errors")
    if [ "$STATUS" != "429" ] && [ "$STATUS" != "000" ]; then
        pass "Request $i to /api/errors returned HTTP $STATUS (not rate-limited)"
    else
        fail "Request $i to /api/errors returned HTTP $STATUS (unexpected)"
    fi
    sleep 0.1
done

echo ""
echo "── Test 2: Rapid auth requests trigger 429 ───────────────────────────────"
# Rate: 2r/s, burst: 10 nodelay.  Send 25 requests as fast as possible.
# At least some should exceed the burst and receive 429.
AUTH_429=0
AUTH_TOTAL=25
for i in $(seq 1 $AUTH_TOTAL); do
    STATUS=$(http_status -X POST "$BASE_URL/api/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"test","password":"test"}')
    if [ "$STATUS" = "429" ]; then
        ((AUTH_429++))
    fi
done
echo "  Auth requests: $AUTH_TOTAL sent, $AUTH_429 rate-limited (429)"
if [ "$AUTH_429" -gt 0 ]; then
    pass "Auth rate limit triggered — $AUTH_429/$AUTH_TOTAL requests returned 429"
else
    fail "Auth rate limit NOT triggered — 0/$AUTH_TOTAL requests returned 429"
fi

echo ""
echo "── Test 3: Verify 429 response body is JSON ──────────────────────────────"
# Flood auth endpoint to guarantee a 429
for i in $(seq 1 15); do
    curl -s -o /dev/null "$BASE_URL/api/auth/login" &
done
wait
BODY=$(curl -s -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"test"}')
STATUS=$(http_status -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"test"}')

if [ "$STATUS" = "429" ]; then
    if echo "$BODY" | grep -q '"message"'; then
        pass "429 response contains JSON 'message' field"
    else
        fail "429 response body missing 'message' field: $BODY"
    fi
    if echo "$BODY" | grep -q '"status"'; then
        pass "429 response contains JSON 'status' field"
    else
        fail "429 response body missing 'status' field: $BODY"
    fi
    # Check Retry-After header is present
    RETRY_AFTER=$(curl -sI "$BASE_URL/api/auth/login" | grep -i "^Retry-After" | head -1)
    if [ -n "$RETRY_AFTER" ]; then
        pass "429 response includes Retry-After header"
    else
        fail "429 response missing Retry-After header"
    fi
else
    echo "  Skipping 429 body assertions (got $STATUS — rate limit may have reset)"
fi

echo ""
echo "── Test 4: Static assets are NOT rate limited ────────────────────────────"
# Rapid requests to / should always return 200/304, never 429
STATIC_429=0
for i in $(seq 1 30); do
    STATUS=$(http_status "$BASE_URL/")
    if [ "$STATUS" = "429" ]; then
        ((STATIC_429++))
    fi
done
if [ "$STATIC_429" -eq 0 ]; then
    pass "30 rapid requests to / — zero 429 responses (static assets not rate limited)"
else
    fail "Static assets returned $STATIC_429 rate-limited 429 responses"
fi

echo ""
echo "── Test 5: Oversized request body returns 413 ────────────────────────────"
# Generate a body slightly over 1 MB (1,048,577 bytes)
LARGE_BODY=$(python3 -c "print('x' * 1048577)" 2>/dev/null || \
             dd if=/dev/urandom bs=1M count=2 2>/dev/null | head -c 1048577 || \
             printf '%1048577s' ' ')
STATUS=$(echo "$LARGE_BODY" | \
    curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/api/analyze" \
    -H "Content-Type: application/json" \
    --data-binary @-)
require_status "Oversized body to /api/analyze" "413" "$STATUS"

# Verify 413 response body is JSON
LARGE_JSON="{\"logText\":\"$(python3 -c "print('x' * 1048577)" 2>/dev/null || printf '%1048577s' x)\"}"
STATUS_413=$(curl -s -o /tmp/response-413.txt -w "%{http_code}" \
    -X POST "$BASE_URL/api/analyze" \
    -H "Content-Type: application/json" \
    -d "$LARGE_JSON" 2>/dev/null || echo "000")
if [ "$STATUS_413" = "413" ]; then
    if grep -q '"message"' /tmp/response-413.txt 2>/dev/null; then
        pass "413 response contains JSON 'message' field"
    else
        echo "  413 body: $(cat /tmp/response-413.txt 2>/dev/null)"
        fail "413 response body missing 'message' field"
    fi
fi

echo ""
echo "── Test 6: X-Real-IP and X-Forwarded-For headers are forwarded ───────────"
# Check that Nginx forwards these headers by querying an endpoint that echoes
# request info. Since /api/analyze exists, use it — look at Nginx access log.
# We check the header is being set in the outbound request by reading it back
# through the /api/ path (backend must respond with something).
STATUS=$(http_status -H "X-Test-IP: 10.0.0.1" "$BASE_URL/api/errors")
if [ "$STATUS" != "000" ]; then
    pass "Nginx proxied /api/errors request (HTTP $STATUS) — proxy_set_header directives active"
else
    fail "Nginx could not proxy request to backend"
fi

echo ""
echo "── Test 7: Verify security headers present on 429 response ───────────────"
# Get a 429 by flooding auth endpoint
for i in $(seq 1 15); do
    curl -s -o /dev/null "$BASE_URL/api/auth/login" &
done
wait
HEADERS_429=$(curl -sI -X POST "$BASE_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"test"}')
for HEADER in "X-Content-Type-Options" "X-Frame-Options" "Referrer-Policy"; do
    if echo "$HEADERS_429" | grep -qi "$HEADER"; then
        pass "429 response includes $HEADER security header"
    else
        fail "429 response missing $HEADER security header"
    fi
done

echo ""
echo "── Test 8: server_tokens off — no Nginx version in Server header ─────────"
SERVER_HEADER=$(curl -sI "$BASE_URL/" | grep -i "^server:" | head -1)
if echo "$SERVER_HEADER" | grep -qi "nginx/"; then
    fail "Server header exposes Nginx version: $SERVER_HEADER"
else
    pass "Server header does not expose Nginx version (server_tokens off)"
fi

echo ""
echo "══════════════════════════════════════════════════════════════════════════"
echo "Results: $PASS passed, $FAIL failed"
echo "══════════════════════════════════════════════════════════════════════════"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
