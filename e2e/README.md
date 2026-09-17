# e2e suites

Black-box tests that hit real running services over HTTP with `curl`, as
opposed to the in-process unit/`@WebMvcTest` tests inside each module. These
live at the repo root (not inside `url-shortener-service/`) because they're
meant to grow into a cross-module suite — today they cover the shortener,
and the plan is to add `maestro-orchestrator.e2e.sh` here once that module
exists, driven the same way.

## Layout

- `lib/assert.sh` — shared curl-result assertion helpers (`assert_equals`,
  `assert_contains`, `assert_not_empty`, `print_summary`). Sourced by suite
  scripts, not run directly.
- `url-shortener.e2e.sh` — the shortener suite: create → redirect happy
  path, plus corner cases (unknown code, blank URL, expired link). Assumes
  the service is already reachable at `BASE_URL`; doesn't manage its
  lifecycle.
- `run-all.sh` — self-contained runner. Builds and starts any service that
  isn't already up (in-memory H2, so it never touches `./data/`), runs its
  suite, tears down what it started, and exits non-zero if anything failed.

## Usage

Run everything, letting the script manage the service lifecycle:

```bash
./e2e/run-all.sh
```

Or, against a service you already have running (e.g. via
`mvn spring-boot:run` in another terminal):

```bash
BASE_URL=http://localhost:8080 ./e2e/url-shortener.e2e.sh
```

## Requirements

`curl`, `jq`, `mvn`, and a JDK 21 (via `JAVA_HOME`, or Homebrew's
`openjdk@21`, which `run-all.sh` will pick up automatically).
