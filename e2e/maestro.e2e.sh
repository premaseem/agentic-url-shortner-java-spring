#!/usr/bin/env bash
# Black-box e2e suite for maestro-orchestrator. Assumes the service is
# already reachable at BASE_URL -- it does not start or stop anything
# itself. Use run-all.sh if you want the service's lifecycle managed for
# you. Runs with no ANTHROPIC_API_KEY set, so it always exercises
# DeterministicAgentStrategy -- deterministic, reproducible output, no
# network dependency, no cost.
#
# Usage: BASE_URL=http://localhost:8081 ./e2e/maestro.e2e.sh
#
# Requires: curl, jq

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/assert.sh
source "$SCRIPT_DIR/lib/assert.sh"

BASE_URL="${BASE_URL:-http://localhost:8081}"

echo "== maestro-orchestrator e2e suite (BASE_URL=$BASE_URL) =="

start_run() {
    local requirement="$1"
    curl -s -X POST "$BASE_URL/api/runs" \
        -H "Content-Type: application/json" \
        -d "{\"requirement\":\"$requirement\"}"
}

status_of() {
    local run_id="$1" stage="$2"
    curl -s "$BASE_URL/api/runs/$run_id" | jq -r ".statuses.$stage"
}

# --- Scenario 1: greenfield ---------------------------------------------
echo "-- scenario: greenfield --"
run1=$(start_run "Add an endpoint that generates a QR code image for an existing short URL, requested by premaseem")
run1_id=$(echo "$run1" | jq -r '.id')

assert_not_empty "greenfield: run id assigned" "$run1_id"
assert_equals "greenfield: REQUIREMENT_ANALYSIS auto-completes" "COMPLETED" "$(echo "$run1" | jq -r '.statuses.REQUIREMENT_ANALYSIS')"
assert_equals "greenfield: DESIGN auto-completes" "COMPLETED" "$(echo "$run1" | jq -r '.statuses.DESIGN')"
assert_equals "greenfield: IMPLEMENTATION gates for approval" "AWAITING_APPROVAL" "$(echo "$run1" | jq -r '.statuses.IMPLEMENTATION')"

design_artifact=$(curl -s "$BASE_URL/api/runs/$run1_id/artifacts" | jq -c '.DESIGN.output.impactedFiles')
assert_equals "greenfield: DESIGN identifies no impacted files (net-new)" "[]" "$design_artifact"

curl -s -X POST "$BASE_URL/api/runs/$run1_id/stages/IMPLEMENTATION/approve" > /dev/null
assert_equals "greenfield: TESTING completes after IMPLEMENTATION approval" "COMPLETED" "$(status_of "$run1_id" TESTING)"
assert_equals "greenfield: DOCUMENTATION completes in parallel with TESTING" "COMPLETED" "$(status_of "$run1_id" DOCUMENTATION)"
assert_equals "greenfield: RELEASE_READINESS gates for approval" "AWAITING_APPROVAL" "$(status_of "$run1_id" RELEASE_READINESS)"

run1_final=$(curl -s -X POST "$BASE_URL/api/runs/$run1_id/stages/RELEASE_READINESS/approve")
assert_equals "greenfield: run reaches terminal" "true" "$(echo "$run1_final" | jq -r '.terminal')"
assert_equals "greenfield: run did not fail" "false" "$(echo "$run1_final" | jq -r '.failed')"

metrics1=$(curl -s "$BASE_URL/api/runs/$run1_id/metrics")
assert_equals "greenfield: 100% success rate" "1.0" "$(echo "$metrics1" | jq -r '.successRate')"

# --- Scenario 2: brownfield ----------------------------------------------
echo "-- scenario: brownfield --"
run2=$(start_run "Add handling so expired short links return a clear error instead of redirecting, requested by premaseem")
run2_id=$(echo "$run2" | jq -r '.id')

impacted=$(curl -s "$BASE_URL/api/runs/$run2_id/artifacts" | jq -r '.DESIGN.output.impactedFiles | join(",")')
assert_contains "brownfield: DESIGN identifies impacted ShortUrl files" "$impacted" "ShortUrl"

# --- Scenario 3: ambiguous requirement, then rejection --------------------
echo "-- scenario: ambiguous requirement, then rejection --"
run3=$(start_run "make it better")
run3_id=$(echo "$run3" | jq -r '.id')

assert_equals "ambiguous: REQUIREMENT_ANALYSIS gates dynamically" "AWAITING_APPROVAL" "$(echo "$run3" | jq -r '.statuses.REQUIREMENT_ANALYSIS')"
assert_equals "ambiguous: DESIGN has not started" "PENDING" "$(echo "$run3" | jq -r '.statuses.DESIGN')"

ambiguous_flag=$(curl -s "$BASE_URL/api/runs/$run3_id/artifacts" | jq -r '.REQUIREMENT_ANALYSIS.output.ambiguous')
questions_count=$(curl -s "$BASE_URL/api/runs/$run3_id/artifacts" | jq -r '.REQUIREMENT_ANALYSIS.output.clarifyingQuestions | length')
assert_equals "ambiguous: flagged ambiguous" "true" "$ambiguous_flag"
if [[ "$questions_count" -gt 0 ]]; then
    log_pass "ambiguous: clarifying questions were generated"
else
    log_fail "ambiguous: expected clarifying questions, got none"
fi

curl -s -X POST "$BASE_URL/api/runs/$run3_id/stages/REQUIREMENT_ANALYSIS/approve" > /dev/null
assert_equals "ambiguous: IMPLEMENTATION reaches approval gate after clarification" "AWAITING_APPROVAL" "$(status_of "$run3_id" IMPLEMENTATION)"

run3_rejected=$(curl -s -X POST "$BASE_URL/api/runs/$run3_id/stages/IMPLEMENTATION/reject" \
    -H "Content-Type: application/json" -d '{"reason":"not needed after all"}')
assert_equals "rejection: IMPLEMENTATION rolls back" "ROLLED_BACK" "$(echo "$run3_rejected" | jq -r '.statuses.IMPLEMENTATION')"
assert_equals "rejection: TESTING is skipped downstream" "SKIPPED" "$(echo "$run3_rejected" | jq -r '.statuses.TESTING')"
assert_equals "rejection: DOCUMENTATION is skipped downstream" "SKIPPED" "$(echo "$run3_rejected" | jq -r '.statuses.DOCUMENTATION')"
assert_equals "rejection: RELEASE_READINESS is skipped downstream" "SKIPPED" "$(echo "$run3_rejected" | jq -r '.statuses.RELEASE_READINESS')"
assert_equals "rejection: run is marked failed (safe-stopped)" "true" "$(echo "$run3_rejected" | jq -r '.failed')"

metrics3=$(curl -s "$BASE_URL/api/runs/$run3_id/metrics")
assert_equals "rejection: rollback counted in metrics" "1" "$(echo "$metrics3" | jq -r '.rollbackCount')"

# --- Scenario 4: dynamic re-plan on the completed greenfield run ---------
echo "-- scenario: revise (dynamic re-plan) --"
curl -s -X POST "$BASE_URL/api/runs/$run1_id/stages/DESIGN/revise" \
    -H "Content-Type: application/json" -d '{"note":"reconsider the approach"}' > /dev/null

assert_equals "revise: upstream REQUIREMENT_ANALYSIS is untouched" "COMPLETED" "$(status_of "$run1_id" REQUIREMENT_ANALYSIS)"
assert_equals "revise: DESIGN re-executed and completed" "COMPLETED" "$(status_of "$run1_id" DESIGN)"
assert_equals "revise: IMPLEMENTATION gates again" "AWAITING_APPROVAL" "$(status_of "$run1_id" IMPLEMENTATION)"

curl -s -X POST "$BASE_URL/api/runs/$run1_id/stages/IMPLEMENTATION/approve" > /dev/null
run1_reapproved=$(curl -s -X POST "$BASE_URL/api/runs/$run1_id/stages/RELEASE_READINESS/approve")
assert_equals "revise: run reaches terminal again after re-plan" "true" "$(echo "$run1_reapproved" | jq -r '.terminal')"

# --- Error paths -----------------------------------------------------------
echo "-- error paths --"
not_found_status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/runs/does-not-exist")
assert_equals "get: unknown run id returns 404" "404" "$not_found_status"

invalid_transition_status=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    "$BASE_URL/api/runs/$run1_id/stages/IMPLEMENTATION/approve")
assert_equals "approve: stage not awaiting approval returns 409" "409" "$invalid_transition_status"

blank_status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/runs" \
    -H "Content-Type: application/json" -d '{"requirement":""}')
assert_equals "start: blank requirement returns 400" "400" "$blank_status"

# --- List ------------------------------------------------------------------
run_count=$(curl -s "$BASE_URL/api/runs" | jq 'length')
if [[ "$run_count" -ge 3 ]]; then
    log_pass "list: all started runs are listed ($run_count found)"
else
    log_fail "list: expected at least 3 runs, found $run_count"
fi

print_summary "maestro-orchestrator"
