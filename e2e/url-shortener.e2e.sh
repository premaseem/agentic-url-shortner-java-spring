#!/usr/bin/env bash
# Black-box e2e suite for url-shortener-service. Assumes the service is
# already reachable at BASE_URL — it does not start or stop anything itself.
# Use run-all.sh if you want the service's lifecycle managed for you.
#
# Usage: BASE_URL=http://localhost:8080 ./e2e/url-shortener.e2e.sh
#
# Requires: curl, jq

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/assert.sh
source "$SCRIPT_DIR/lib/assert.sh"

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "== url-shortener-service e2e suite (BASE_URL=$BASE_URL) =="

# --- happy path: create a short URL, then redirect through it ---
create_response=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/api/urls" \
    -H "Content-Type: application/json" \
    -d '{"originalUrl":"https://github.com/premaseem","ownerName":"premaseem"}')
create_status=$(echo "$create_response" | tail -n1)
create_body=$(echo "$create_response" | sed '$d')

assert_equals "create: happy path returns 201" "201" "$create_status"

code=$(echo "$create_body" | jq -r '.code // empty')
assert_not_empty "create: response includes a non-empty code" "$code"
assert_equals "create: response echoes originalUrl" \
    "https://github.com/premaseem" "$(echo "$create_body" | jq -r '.originalUrl')"
assert_equals "create: response echoes ownerName" \
    "premaseem" "$(echo "$create_body" | jq -r '.ownerName')"

redirect_headers=$(curl -s -o /dev/null -D - "$BASE_URL/$code")
redirect_status=$(echo "$redirect_headers" | head -n1 | tr -d '\r')
location=$(echo "$redirect_headers" | grep -i '^Location:' | sed 's/^[Ll]ocation: *//' | tr -d '\r')

assert_contains "redirect: happy path returns 302" "$redirect_status" "302"
assert_equals "redirect: Location header matches originalUrl" \
    "https://github.com/premaseem" "$location"

# --- corner case: unknown code -> 404 ---
not_found_status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/doesNotExist")
assert_equals "redirect: unknown code returns 404" "404" "$not_found_status"

# --- corner case: blank originalUrl -> 400 ---
blank_status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/urls" \
    -H "Content-Type: application/json" \
    -d '{"originalUrl":""}')
assert_equals "create: blank originalUrl returns 400" "400" "$blank_status"

# --- corner case: expired link -> 410, does not redirect ---
expired_response=$(curl -s -X POST "$BASE_URL/api/urls" \
    -H "Content-Type: application/json" \
    -d '{"originalUrl":"https://example.com/old","ownerName":"premaseem","expiresAt":"2020-01-01T00:00:00Z"}')
expired_code=$(echo "$expired_response" | jq -r '.code // empty')
expired_status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/$expired_code")
assert_equals "redirect: expired code returns 410" "410" "$expired_status"

print_summary "url-shortener-service"
