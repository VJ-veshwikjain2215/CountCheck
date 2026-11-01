# CountCheck: Project Documentation Set

| | |
|---|---|
| **Document status** | Pre-development planning. Nothing has been built, deployed, or tested. |
| **Prepared** | 1 Nov 2026 |
| **Source** | Approved demo brief ("CountCheck") plus a research pass on 1 Nov 2026 (see `08-REFERENCES.md`) |
| **Gate** | No code, repo, database, or hosting resource is created until the owner explicitly authorizes it (see Gates in `07-PROJECT-PLAN.md`) |

## 1. What this project is

A **public, single shared, synthetic-data-only** web demo of one narrow idea: a person manually types an item name, a *recorded* count, and a *physical* count, and the app shows the signed difference (`physical − recorded`) in an append-only, timestamped history that can be filtered to mismatches only.

It is deliberately small: four features, one table, no accounts, no integrations. Most of the engineering effort goes into doing the small thing *honestly and safely*: idempotent writes, safe failure behaviour, public-exposure controls, and not over-claiming what a free-tier demo can guarantee.

## 2. Document map

| File | Purpose | Read it when |
|---|---|---|
| `00-README.md` | This index, constraints, open decisions | First |
| `01-PRD.md` | Problem, scope, requirements (with IDs), user flow, field rules, acceptance criteria | Deciding *what* to build |
| `02-ARCHITECTURE-AND-DECISIONS.md` | Stack, components, request flows, ADRs | Deciding *how* it fits together |
| `03-TECHNICAL-SPEC.md` | Routes, validation, schema/DDL, SQL, idempotency algorithm, security config, config contract | Writing code |
| `04-SECURITY-AND-RISK.md` | Threat model, controls, non-guarantees, risk register | Reviewing safety and exposure |
| `05-TEST-PLAN.md` | Test matrix with IDs, traceability, results log (all **NOT RUN**) | Writing/running tests |
| `06-DEPLOYMENT-RUNBOOK.md` | Docker, Neon, Render, free-tier limits, preflight, incident playbook | Deploying (after authorization) |
| `07-PROJECT-PLAN.md` | WBS, estimate, milestones, gates, definition of done, repo layout, README outline | Planning and tracking |
| `08-REFERENCES.md` | Sources, research log, claims ledger (verified / unverified) | Checking a claim |

## 3. Hard constraints (non-negotiable, from the approved brief)

1. **Synthetic data only.** The public-demo warning (exact text in `01-PRD.md` §6) appears on **every** page.
2. **Four features only.** Out of scope: login/accounts, real shop or personal data, POS integration, barcode scanning, live inventory, automatic stock correction, analytics, notifications, public reset, production readiness.
3. **Stack:** Java 21, Spring Boot, Thymeleaf, PostgreSQL, Docker, Render Free + Neon Free. **No Python.**
4. **Append-only means "no update/delete exposed by the app"**, not tamper-proof, backed up, or durable.
5. **Both counts are manual snapshots.** The app cannot verify physical stock; the difference is not a stock-adjustment recommendation.
6. **No paid fallback.** If a free tier blocks deployment, stop and report.
7. **Tests are specifications until run.** Never report a pass that was not observed.

## 4. What the research changed or added (summary)

Full detail in `02` (ADRs) and `08`. The brief held up well; these are the items a planner should surface before coding:

| # | Finding | Impact |
|---|---|---|
| 1 | **Spring Boot 4.x is the current line** (4.1 GA in 2026; 3.5 OSS support ended 30 Jun 2026; 4.0 OSS support ends 31 Dec 2026). Boot 4 **renamed/split starters**: `webmvc`, and **Flyway now needs `spring-boot-starter-flyway`**. | Use Boot 4.1.x; dependency list in `03` is updated. Re-check the exact version on start.spring.io at build time. |
| 2 | **Neon's pooled endpoint is PgBouncer in transaction mode.** `SET`, session advisory locks and SQL-level `PREPARE` do not work on it. | Do not rely on `SET TRANSACTION ISOLATION` over the pooler; PostgreSQL's default *is* READ COMMITTED. **Flyway must use the direct endpoint.** |
| 3 | **A Docker build cannot run Testcontainers integration tests** (no Docker daemon in the build stage), which conflicts with "run tests in the build stage". | Image build runs unit tests only; Postgres integration tests run locally and in CI. Needs owner acceptance (Decision D5). |
| 4 | **Neon Free compute budget is 100 CU-hours/month** and scale-to-zero is fixed at 5 min. Traffic that pings the DB every few minutes keeps compute awake. | Cap autoscaling max at 0.25 CU; never point health checks or uptime monitors at DB-backed routes. |
| 5 | **Render Free web services share 750 instance-hours per workspace**, spin down after 15 min idle (~1 min wake), and Render may suspend a free service with unusually high *service-initiated* traffic (e.g. external DB access). | Documented as risks R-07/R-08; health check path stays unset or points to a static asset. |
| 6 | **Neon Free has a 6-hour restore window and 1 manual snapshot**, no uptime SLA. | Reinforces "history may disappear"; no backup promise. |
| 7 | **Keyset cursors must keep microsecond precision** (PostgreSQL `timestamptz` is microsecond). Truncating to milliseconds skips or duplicates rows. | Explicit test T-PAG-06. |
| 8 | **`UUID.fromString` is lenient** (accepts malformed strings). | Validate with a strict v4 regex before parsing. |
| 9 | **The body-size and rate-limit filters must run before Spring Security's CSRF filter**, because CSRF resolution reads the `_csrf` form parameter (which parses the body). | Filter order is specified in `03`. |

## 5. Open decisions for the owner

Defaults are proposed so work is not blocked; each can be overridden.

| ID | Decision | Proposed default | Why it matters |
|---|---|---|---|
| D1 | GitHub repo visibility | Public (no secrets by design; free CI) | Affects CI minutes and exposure of code |
| D2 | Spring Boot line | 4.1.x latest patch (not 3.5: EOL) | Dependency names, security API |
| D3 | Schema migration strategy | **A:** run Flyway from a trusted machine/CI against Neon's *direct* endpoint with an owner role; runtime app uses a SELECT/INSERT-only role with Flyway disabled. **B (fallback):** auto-migrate at startup with elevated role, documented residual risk | Least privilege vs. simplicity |
| D4 | Keep Spring Data JPA? | Keep, per brief. *Optional simplification:* use `JdbcClient` only to cut memory/startup on a 512 MB instance | Cold-start time and heap |
| D5 | Where integration tests run | Unit tests in Docker build; Testcontainers ITs locally + GitHub Actions | Brief says "tests in build stage" |
| D6 | Seed data | A few fictional rows via a separate Flyway location used only on the demo profile, fixed UUIDs, `ON CONFLICT DO NOTHING` | Empty-history UX vs. test determinism |
| D7 | Unknown-outcome UX | Show the pending entry **read-only** with "Retry same entry"; offer "New entry" only with a "check history first" notice | Prevents edit-after-unknown causing a 409 |
| D8 | Count string strictness | Reject any whitespace and leading zeros (except `0`) in counts; ASCII digits only | Brief says "plain base-10" |
| D9 | Regions | Render and Neon in the same or nearby region (verify choices at creation) | Latency and cold-start feel |
| D10 | Auto-deploy on Render | Off, or "after CI passes" | Avoid surprise rebuilds eating build minutes |

## 6. Glossary

- **Recorded count**: manually typed number representing "what the book/system says".
- **Physical count**: manually typed number representing "what I counted".
- **Difference**: `physical − recorded`, signed. **Mismatch** means difference ≠ 0.
- **Request ID**: client-visible UUID v4 used as an idempotency key (not a secret).
- **Unknown outcome**: a save where commit may or may not have happened and the client did not receive a definitive answer.
- **Keyset paging**: paging by the last seen `(created_at, id)` instead of `OFFSET`.
