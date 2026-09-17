#!/usr/bin/env bash
# Shared curl + assertion helpers for the black-box e2e suites under e2e/.
# Sourced by suite scripts (e.g. url-shortener.e2e.sh) — not run directly.

PASS_COUNT=0
FAIL_COUNT=0

log_pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "  PASS - $1"
}

log_fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "  FAIL - $1"
}

assert_equals() {
    local description="$1" expected="$2" actual="$3"
    if [[ "$expected" == "$actual" ]]; then
        log_pass "$description"
    else
        log_fail "$description (expected [$expected], got [$actual])"
    fi
}

assert_contains() {
    local description="$1" haystack="$2" needle="$3"
    if [[ "$haystack" == *"$needle"* ]]; then
        log_pass "$description"
    else
        log_fail "$description (expected to contain [$needle], got [$haystack])"
    fi
}

assert_not_empty() {
    local description="$1" value="$2"
    if [[ -n "$value" ]]; then
        log_pass "$description"
    else
        log_fail "$description (value was empty)"
    fi
}

# Call at the end of a suite script. Prints a summary and returns non-zero
# (via $?) if any assertion failed, so callers can propagate suite failure.
print_summary() {
    local suite_name="$1"
    echo
    echo "== $suite_name: $PASS_COUNT passed, $FAIL_COUNT failed =="
    [[ "$FAIL_COUNT" -eq 0 ]]
}
