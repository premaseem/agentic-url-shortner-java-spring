# Spec — Agentic URL Shortener (Schwab Interview Assignment)

Source requirements: [`../Requirements.pdf`](../Requirements.pdf) (official assignment brief) and
[`../project-requirement.md`](../project-requirement.md) (restated synopsis). This document is the
build spec derived from both, refined through discussion.

## 1. What we're building

A Maven multi-module mono-repo with two modules:

| Module | Role |
|---|---|
| `url-shortener-service` | The real product. A Spring Boot URL shortener with Google OAuth2 login, H2 file-backed persistence, redirect + analytics APIs. This is the "food." |
| `maestro-orchestrator` | The agentic SDLC orchestration layer. Takes a requirement (feature request, bug fix, refactor), interprets it, decomposes it into a dependency-graphed SDLC workflow, and executes that workflow — with approval gates, retries/rollback, policy guardrails, and metrics — against `url-shortener-service`. This is what "eats" the food. |

The PDF is explicit that **Maestro is the critical differentiator** for evaluation — the shortener itself
just needs to be a credible, working target for Maestro to operate on.

## 2. Repo layout

```
agentic-url-shortner-java-spring/
├── pom.xml                          # parent POM (Java 21, Spring Boot 3.x, shared deps/versions)
├── url-shortener-service/
│   ├── pom.xml
│   └── src/main/java/.../shortener/
│       ├── web/                     # controllers
│       ├── service/
│       ├── repository/
│       ├── domain/                  # entities: User, ShortUrl, ClickEvent
│       ├── security/                # OAuth2 + session config
│       └── config/
├── maestro-orchestrator/
│   ├── pom.xml
│   └── src/main/java/.../maestro/
│       ├── engine/                  # WorkflowGraph, StageExecutor, gates, retry/rollback
│       ├── agent/                   # AgentStrategy interface + Llm/Deterministic impls
│       ├── policy/                  # guardrails
│       ├── audit/                   # decision lineage, metrics
│       └── web/                     # Maestro's own REST API
└── docs/
    ├── SPEC.md                      # this file
    ├── ARCHITECTURE.md
    ├── TESTING_AND_TRADEOFFS.md
    └── scenarios/
        ├── 01-greenfield.md
        ├── 02-brownfield.md
        └── 03-ambiguous.md
```

## 3. Module 1 — `url-shortener-service`

**Stack**: Spring Boot 3.x, Java 21, Maven. Standard layered template (Web + Security + Data JPA +
Validation) — no third-party scaffolding framework.

**Persistence**: H2 in **file mode** (`jdbc:h2:file:./data/urlshortener`), so state survives restarts.
H2 console enabled at `/h2-console`, secured behind auth, for demo inspection.

**Auth**: Spring Security `oauth2Login()` against Google as the primary flow, session-based
(`HttpSession`) state tracking of the signed-in user. A local dev-login fallback (username only, no
Google) will be included behind a Spring profile, since Google OAuth needs a live client
ID/secret that may not be available at review time — this is called out as an assumption, not a
silent substitution.

**Domain model**:
- `User` — googleSub, email, name
- `ShortUrl` — code, originalUrl, owner, createdAt, expiresAt (nullable), clickCount
- `ClickEvent` — urlId, timestamp, referrer, userAgent

**Core APIs**:
- `POST /api/urls` — create short URL (owned by current session user)
- `GET /{code}` — redirect; `410 Gone` if expired, `404` if unknown
- `GET /api/urls` — list current user's URLs
- `GET /api/urls/{code}/stats` — click analytics
- `DELETE /api/urls/{code}`
- Scheduled job to sweep/mark expired links

**Docs**: springdoc-openapi / Swagger UI.

**Tests**: service-layer unit tests, `MockMvc` controller tests, repository tests against H2.

## 4. Module 2 — `maestro-orchestrator`

**Purpose**: a standalone Spring Boot app implementing the orchestration layer the PDF calls out as
the critical differentiator: non-linear, stateful SDLC execution with governance — not a linear task
chain.

### 4.1 Engine (deterministic, no LLM involved)

- **WorkflowGraph** — DAG of stages: `REQUIREMENT_ANALYSIS → DESIGN → IMPLEMENTATION →
  {TESTING, DOCUMENTATION} → RELEASE_READINESS`. `TESTING` and `DOCUMENTATION` run in parallel
  after `IMPLEMENTATION`'s exit gate and synchronize before `RELEASE_READINESS`.
- **Entry/exit gates** — each stage declares preconditions to start and postconditions to exit
  (e.g. `DESIGN` can't exit until impacted modules are identified for brownfield work).
- **ApprovalGate** — high-impact stages (`IMPLEMENTATION` merge, `RELEASE_READINESS`) pause for
  human approval via Maestro's REST API before continuing.
- **RetryPolicy** — bounded retries with backoff, per stage.
- **RollbackHandler** — safe-stop and revert on failure or human rejection (reverts the working
  git branch Maestro generated changes on; never touches `main` directly).
- **PolicyGuardrails** — rule checks around each stage: no secrets in generated code, disallowed
  dependency additions, diff-size limits, etc. Violations block the gate rather than silently
  passing.
- **ContextStore** — a shared, versioned context object carrying requirement text, decisions,
  and intermediate outputs across stages (decision lineage), persisted for audit.
- **MetricsCollector** — success rate, retry/rollback frequency, MTTR, end-to-end latency, per
  run and in aggregate.
- **Re-planner** — if upstream output changes mid-run (e.g. a requirement clarification arrives
  after `DESIGN` has started), downstream nodes are marked stale and only the affected subgraph
  re-executes.

### 4.2 Agent strategy layer (pluggable — the hybrid approach)

```java
interface AgentStrategy {
    RequirementAnalysis interpretRequirement(String rawRequirement, CodebaseContext ctx);
    List<Task> decomposeTasks(RequirementAnalysis analysis);
    DesignProposal generateDesign(List<Task> tasks, CodebaseContext ctx);
    CodePatch generateCode(DesignProposal design, CodebaseContext ctx);
    TestSuite generateTests(CodePatch patch);
    Documentation generateDocs(CodePatch patch, TestSuite tests);
    RiskAssessment assessRisk(CodePatch patch);
}
```

- **`LlmAgentStrategy`** — calls the Claude API (Anthropic SDK) with stage-specific prompts plus
  `ContextStore` state, returns structured output. This is the real "agentic reasoning" path.
- **`DeterministicAgentStrategy`** — rule-based/templated fallback that produces structurally
  valid but canned outputs. Used when no API key is configured, and makes scenario runs
  reproducible for a live demo without depending on network/LLM variance.
- Selected via `maestro.agent.strategy=llm|deterministic` in `application.yml`, or auto-falls-back
  to deterministic if `ANTHROPIC_API_KEY` is unset — logged clearly either way so it's never a
  silent substitution during a demo.

### 4.3 Maestro's own REST API

- `POST /api/runs` — start a workflow run: `{ requirement: string, mode: greenfield|brownfield|ambiguous|auto }`
- `GET /api/runs/{id}` — status, current stage, graph state, decision lineage
- `POST /api/runs/{id}/approve` / `POST /api/runs/{id}/reject` — human gate actions
- `GET /api/runs/{id}/artifacts` — generated diff/tests/docs for the run
- `GET /api/runs/{id}/metrics` — reliability metrics for that run
- `GET /api/metrics` — aggregate metrics across all runs

### 4.4 How Maestro touches the shortener

Maestro never commits directly to `main`. Each run creates a working git branch inside
`url-shortener-service`, applies its generated patch there, and stops at the `RELEASE_READINESS`
approval gate for a human to review/merge. This is the "controlled autonomy" boundary from the
PDF: agents execute multi-step work, humans own final merge/quality control.

## 5. Required demo scenarios

1. **Greenfield** — e.g. "add a QR code endpoint for a short URL." Full pipeline from a clean
   slate.
2. **Brownfield** — "add expired-link handling to existing short URLs." Maestro must reason over
   the existing `ShortUrl` entity/controller, identify impacted files, and propose a scoped diff.
3. **Ambiguous** — e.g. "make the links safer." Maestro must surface the ambiguity (explicit
   clarification/assumption log) before decomposing, rather than guessing silently — this
   directly demonstrates requirement-understanding rigor, which is a named evaluation criterion.

Each scenario gets a `docs/scenarios/0N-*.md` write-up plus the actual recorded run (artifacts +
metrics) captured from a real Maestro execution.

## 6. Deliverables mapping

| PDF deliverable | Where it lives |
|---|---|
| Working prototype | Both modules, runnable via `mvn spring-boot:run` per module |
| Architecture overview | `docs/ARCHITECTURE.md` |
| 3 scenarios | `docs/scenarios/*.md` + captured run artifacts |
| Setup instructions | Root `README.md` + per-module `README.md` |
| Testing approach / limitations / trade-offs | `docs/TESTING_AND_TRADEOFFS.md` |

## 7. Known assumptions / open items

- Google OAuth needs a live client ID/secret; a no-Google dev-login profile is included so the
  demo doesn't hard-depend on it.
- The LLM strategy needs `ANTHROPIC_API_KEY`; absent that, Maestro runs fully deterministically —
  this is a deliberate fallback, not a workaround to hide.
- Scope is bounded to the assignment's 2–3 day window; this spec is MVP-first (the engine +
  pluggable strategy + 3 scenarios), with rate limiting, multi-user quotas, and a UI called out
  as explicit stretch/out-of-scope items in `TESTING_AND_TRADEOFFS.md` rather than silently
  dropped.
