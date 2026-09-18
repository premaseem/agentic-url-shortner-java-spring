#!/usr/bin/env bash
# Self-contained e2e runner: builds/starts each module's service (unless one
# is already reachable), runs its e2e suite, and tears down anything this
# script started. Requires: curl, jq, mvn, and a JDK 21 (via JAVA_HOME or
# Homebrew's openjdk@21).
#
# Usage: ./e2e/run-all.sh
#   URL_SHORTENER_BASE_URL=http://localhost:9090 ./e2e/run-all.sh   # custom port
#   MAESTRO_BASE_URL=http://localhost:9091 ./e2e/run-all.sh         # custom port

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

URL_SHORTENER_BASE_URL="${URL_SHORTENER_BASE_URL:-http://localhost:8080}"
URL_SHORTENER_JAR="$REPO_ROOT/url-shortener-service/target/url-shortener-service.jar"
URL_SHORTENER_LOG="$(mktemp -t url-shortener-e2e-XXXXXX.log)"
URL_SHORTENER_PID=""

MAESTRO_BASE_URL="${MAESTRO_BASE_URL:-http://localhost:8081}"
MAESTRO_JAR="$REPO_ROOT/maestro-orchestrator/target/maestro-orchestrator.jar"
MAESTRO_LOG="$(mktemp -t maestro-e2e-XXXXXX.log)"
MAESTRO_PID=""

OVERALL_EXIT=0

resolve_java_home() {
    if [[ -n "${JAVA_HOME:-}" ]]; then
        return
    fi
    if [[ -x /opt/homebrew/opt/openjdk@21/bin/java ]]; then
        export JAVA_HOME=/opt/homebrew/opt/openjdk@21
    elif command -v /usr/libexec/java_home >/dev/null 2>&1 && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
    else
        echo "JAVA_HOME is not set and no JDK 21 was found. Install one (e.g. 'brew install openjdk@21') or set JAVA_HOME." >&2
        exit 1
    fi
}

is_up() {
    curl -s -o /dev/null "$1"
}

wait_for_up() {
    local url="$1" attempts="${2:-60}"
    for ((i = 0; i < attempts; i++)); do
        is_up "$url" && return 0
        sleep 1
    done
    return 1
}

start_url_shortener_if_needed() {
    if is_up "$URL_SHORTENER_BASE_URL"; then
        echo "url-shortener-service already reachable at $URL_SHORTENER_BASE_URL — reusing it"
        return
    fi

    resolve_java_home
    export PATH="$JAVA_HOME/bin:$PATH"

    echo "Building url-shortener-service..."
    (cd "$REPO_ROOT" && mvn -q -pl url-shortener-service -am -DskipTests package)

    echo "Starting url-shortener-service for the e2e run (log: $URL_SHORTENER_LOG)..."
    SPRING_DATASOURCE_URL="jdbc:h2:mem:e2e-test;DB_CLOSE_DELAY=-1" \
        java -jar "$URL_SHORTENER_JAR" > "$URL_SHORTENER_LOG" 2>&1 &
    URL_SHORTENER_PID=$!

    if ! wait_for_up "$URL_SHORTENER_BASE_URL" 60; then
        echo "url-shortener-service did not come up within 60s — see $URL_SHORTENER_LOG"
        kill "$URL_SHORTENER_PID" 2>/dev/null
        exit 1
    fi
}

stop_url_shortener_if_started() {
    if [[ -n "$URL_SHORTENER_PID" ]]; then
        echo "Stopping url-shortener-service (pid $URL_SHORTENER_PID)..."
        kill "$URL_SHORTENER_PID" 2>/dev/null
        wait "$URL_SHORTENER_PID" 2>/dev/null
    fi
}

start_maestro_if_needed() {
    if is_up "$MAESTRO_BASE_URL"; then
        echo "maestro-orchestrator already reachable at $MAESTRO_BASE_URL — reusing it"
        return
    fi

    resolve_java_home
    export PATH="$JAVA_HOME/bin:$PATH"

    echo "Building maestro-orchestrator..."
    (cd "$REPO_ROOT" && mvn -q -pl maestro-orchestrator -am -DskipTests package)

    # No ANTHROPIC_API_KEY is passed on purpose: the e2e suite is meant to
    # exercise the always-available DeterministicAgentStrategy path.
    echo "Starting maestro-orchestrator for the e2e run (log: $MAESTRO_LOG)..."
    java -jar "$MAESTRO_JAR" > "$MAESTRO_LOG" 2>&1 &
    MAESTRO_PID=$!

    if ! wait_for_up "$MAESTRO_BASE_URL" 60; then
        echo "maestro-orchestrator did not come up within 60s — see $MAESTRO_LOG"
        kill "$MAESTRO_PID" 2>/dev/null
        exit 1
    fi
}

stop_maestro_if_started() {
    if [[ -n "$MAESTRO_PID" ]]; then
        echo "Stopping maestro-orchestrator (pid $MAESTRO_PID)..."
        kill "$MAESTRO_PID" 2>/dev/null
        wait "$MAESTRO_PID" 2>/dev/null
    fi
}

stop_all_started_services() {
    stop_url_shortener_if_started
    stop_maestro_if_started
}

trap stop_all_started_services EXIT

start_url_shortener_if_needed
BASE_URL="$URL_SHORTENER_BASE_URL" "$SCRIPT_DIR/url-shortener.e2e.sh"
OVERALL_EXIT=$((OVERALL_EXIT + $?))

start_maestro_if_needed
BASE_URL="$MAESTRO_BASE_URL" "$SCRIPT_DIR/maestro.e2e.sh"
OVERALL_EXIT=$((OVERALL_EXIT + $?))

exit "$OVERALL_EXIT"
