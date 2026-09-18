# e2e suites

Black-box tests that hit real running services over HTTP with `curl`, as
opposed to the in-process unit/`@WebMvcTest` tests inside each module. These
live at the repo root (not inside either module's own directory) because
they're a cross-module suite: they cover both `url-shortener-service` and
`maestro-orchestrator`.

## Layout

- `lib/assert.sh` — shared curl-result assertion helpers (`assert_equals`,
  `assert_contains`, `assert_not_empty`, `print_summary`). Sourced by suite
  scripts, not run directly.
- `url-shortener.e2e.sh` — the shortener suite: create → redirect happy
  path, plus corner cases (unknown code, blank URL, expired link).
- `maestro.e2e.sh` — the orchestrator suite, covering all three demo
  scenarios end to end through the real REST API plus rollback/re-plan:
  - **greenfield**: full run through all 6 stages, both approval gates,
    parallel TESTING/DOCUMENTATION completing before RELEASE_READINESS
    becomes ready, 100% success rate in the resulting metrics.
  - **brownfield**: a requirement about existing behavior (expired links)
    produces a DESIGN with non-empty `impactedFiles` naming the real
    `ShortUrl` code, vs. the greenfield run's empty list.
  - **ambiguous**: a vague requirement gates dynamically at
    REQUIREMENT_ANALYSIS with generated clarifying questions, before any
    downstream stage runs.
  - **rejection**: rejecting a gated stage rolls it back, skips every
    downstream stage, safe-stops the run, and is counted in
    `rollbackCount`.
  - **revise**: reviving a completed run's DESIGN stage re-executes only
    that stage and its downstream subgraph — REQUIREMENT_ANALYSIS stays
    untouched — proving the dynamic re-plan mechanism.
  - Error paths: 404 (unknown run), 409 (approving a stage that isn't
    awaiting approval), 400 (blank requirement).

  Runs with no `ANTHROPIC_API_KEY` set, so it always exercises
  `DeterministicAgentStrategy` — reproducible, no network dependency, no
  cost.
- `run-all.sh` — self-contained runner. Builds and starts any service that
  isn't already up (in-memory DB for both, so it never touches
  `url-shortener-service/data/`), runs its suite, tears down what it
  started, and exits non-zero if anything failed.

## Usage

Run everything, letting the script manage both services' lifecycle:

```bash
./e2e/run-all.sh
```

Or, against services you already have running (e.g. via
`mvn spring-boot:run` in other terminals):

```bash
BASE_URL=http://localhost:8080 ./e2e/url-shortener.e2e.sh
BASE_URL=http://localhost:8081 ./e2e/maestro.e2e.sh
```

## Requirements

`curl`, `jq`, `mvn`, and a JDK 21 (via `JAVA_HOME`, or Homebrew's
`openjdk@21`, which `run-all.sh` will pick up automatically).
