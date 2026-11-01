# 07. Project Plan

## 1. Approach

Small, test-first, risk-first. The riskiest parts (idempotent insert under concurrency, safe failure semantics, filter ordering behind a proxy, Neon pooler behaviour) are built and tested **before** the UI polish. Everything is built locally against real PostgreSQL first; Render/Neon come last and only with explicit authorization.

## 2. Gates

| Gate | Condition | Blocks |
|---|---|---|
| **G0: Plan approved** | Owner reviewed this doc set; D1–D10 confirmed or defaults accepted | Any implementation |
| **G1: External resources authorized** | Owner explicitly authorizes creating the GitHub repo and provisioning Neon/Render; accounts verified eligible; billing/overage settings checked | Repo creation, provisioning, deploy |
| **G2: Release candidate** | High + Medium tests pass or unresolved items documented; security checklist (`04` §6) done | Public deploy |
| **G3: Live verified** | Smoke test observed; free-plan selection confirmed | Reporting a live URL as "done" |

## 3. Estimate

### 3.1 Brief's estimate: 14 h (not a commitment)

| Work package | Hours |
|---|---|
| Project/schema/setup | 2 |
| Validation, persistence, transaction/idempotency | 3 |
| Templates, history paging, retry handling, warnings | 3 |
| Automated QA and fixes | 3 |
| Docker and free-tier deployment smoke test | 2 |
| README/repository preparation | 1 |
| **Total** | **14** |

Assumes the developer already knows Spring, PostgreSQL, Docker, has eligible accounts, and no provider delays.

### 3.2 Planner's risk-adjusted view (judgement, not data)

The brief itself calls 14 h optimistic for real-PostgreSQL concurrency tests, CSRF/session recovery, request-body caps, migrations/roles, and a cold deploy. Plausible overrun drivers:

| Driver | Why | Added hours (rough) |
|---|---|---|
| Boot 4 modular starters/test-annotation learning curve | Fewer references; changed packages | +1–2 |
| Concurrency tests (barriers, held-open transactions, fault injection) | Fiddly to make deterministic | +2–3 |
| Filter ordering + 4 KiB cap incl. chunked, and behaviour behind Render's proxy | Needs real-URL experiments | +1–2 |
| Neon pooler/TLS/role setup, cold-start tuning | Unverified assumptions A2, A4 | +1–3 |
| Pending-retry JS and manual browser testing | Cross-tab/session scenarios | +1–2 |
| **Plausible range** | | **≈ 18–26 h** |

Plan with **14 h as the target and ~22 h as the reporting threshold**. If it exceeds the estimate, report the overrun. Never drop High tests to hit the number.

## 4. Work breakdown structure

Dependencies in brackets. Check boxes are for tracking.

### M0. Preflight and decisions *(before G0)*
- [ ] M0.1 Owner review of docs; confirm D1–D10 (or accept defaults)
- [ ] M0.2 Confirm Spring Boot version on start.spring.io; note patch; confirm Testcontainers artifact names
- [ ] M0.3 Decide Postgres major version (match Neon at creation)

### M1. Skeleton *(G0)*, ~1.5–2 h
- [ ] M1.1 Generate project (Boot 4.1.x, Java 21, Maven wrapper, deps per `03` §2)
- [ ] M1.2 `application.yml` + `local`/`demo` profiles; `server.port=${PORT:8080}`
- [ ] M1.3 Flyway V1 migration; `ddl-auto=validate`
- [ ] M1.4 Testcontainers PostgreSQL base test class; migration applied in tests
- [ ] M1.5 `.gitignore`, `.dockerignore`, skeleton README
- **Exit:** app boots against local Postgres; empty test suite green; CI skeleton drafted.

### M2. Domain and validation *(M1)*, ~1 h
- [ ] M2.1 `CountSubmission` factory: normalization + strict validation (`03` §4)
- [ ] M2.2 Difference formatting; request-ID strict parse
- [ ] M2.3 Unit tests T-VAL-01..13, T-ARI-01..03/05
- **Exit:** unit tests green.

### M3. Persistence and idempotency *(M1, M2)*, ~2–3 h  ← **highest risk**
- [ ] M3.1 `IdempotentInsertRepository` (insert + select), `CountService` tx, `SubmissionFacade`
- [ ] M3.2 Write T-CON-01..06 **first**, then implement until green
- [ ] M3.3 Fault-injection tests T-ERR-02..04
- [ ] M3.4 `CountRepository` keyset queries; T-PAG-01..07, T-ARI-04
- [ ] M3.5 Constraint tests T-VAL-14
- **Exit:** all High persistence tests green on real PostgreSQL.

### M4. Web layer, security, UI *(M3)*, ~3–4 h
- [ ] M4.1 `SecurityConfig` (deny-by-default, CSRF, headers, CSP, cookies, `AccessDeniedHandler`)
- [ ] M4.2 `RequestLimitFilter` (rate + body cap), ordered before Spring Security; T-SEC-08/09/VAL-11 (local)
- [ ] M4.3 `CountController` + templates + shared warning fragment; error pages for every status
- [ ] M4.4 Parameter whitelisting (GET and POST); T-VAL-12/13, T-PAG-03
- [ ] M4.5 `pending-count.js` + detail `data-request-id`; manual T-RET-*
- [ ] M4.6 Controller/slice tests: T-SEC-01..07, T-UI-01, T-RET-01
- **Exit:** all High/Medium web tests green locally; manual browser pass.

### M5. QA hardening *(M4)*, ~1.5–3 h
- [ ] M5.1 Accessibility and responsive pass (T-UI-02/03)
- [ ] M5.2 Security checklist (`04` §6); `th:utext` grep; log inspection (T-ERR-01)
- [ ] M5.3 Fix findings; re-run suites; record results

### M6. Containerization *(M5)*, ~1–2 h
- [ ] M6.1 Dockerfile per runbook §4; build and run locally against local Postgres
- [ ] M6.2 T-REPO-02, T-OPS-01, T-OPS-07 (locally); measure RSS/startup
- [ ] M6.3 CI workflow: `verify` with Testcontainers (D5)

### M7. Deploy *(G1 + G2)*, ~1.5–3 h
- [ ] M7.1 Preflight checklist (runbook §2)
- [ ] M7.2 Create GitHub repo `countcheck` (new; do not overwrite); push reviewed code
- [ ] M7.3 Neon project, roles, cap autoscaling, migrate (direct), grants
- [ ] M7.4 Render Free web service; env vars; deploy
- [ ] M7.5 Smoke test (runbook §7); T-OPS-02..06, T-SEC-04, T-SEC-09, T-RET-03
- **Exit:** G3.

### M8. Docs and handover *(M7)*, ~1 h
- [ ] M8.1 Finalize README (outline below); add observed test results and limitations
- [ ] M8.2 Add live URL **only if it exists and was observed**
- [ ] M8.3 Report overruns, unresolved items, and anything unverified

## 5. Critical path and sequencing notes

M1 → M3 → M4 → M6 → M7. M2 can overlap M1. Do **not** start UI polish before M3's concurrency tests pass. Spike early (M1) on two unknowns: (a) Boot 4 starter/test setup, (b) Neon TLS + pooler connectivity from a throwaway local run (**only after G1**).

## 6. Definition of Done

1. All four features work on a clean build **and** on an explicitly authorized public demo using only synthetic data.
2. High and Medium tests pass, **or** unresolved items are prominently reported (never marked as pass).
3. README lets a newcomer set up locally and documents every limitation and non-guarantee.
4. No credentials or real data in the repo, image, logs, or screenshots.
5. Free plans and possible overages verified (not assumed).
6. Actual deployment, repo URL, and test results are reported **only after observation**.
7. Warning present on every page; no claim of privacy, tamper-proofing, durability, demand, or benefit.

*This documentation set does not itself satisfy implementation or deployment.*

## 7. Repository README outline (to be written in M8)

1. Purpose and the **public-demo warning**
2. What it is / is not (manual snapshots; cannot verify physical stock; difference ≠ adjustment recommendation)
3. Stack and architecture summary
4. Prerequisites (Java 21, Docker; Maven wrapper included)
5. Local PostgreSQL setup (placeholder credentials only)
6. Configuration names table (placeholders only)
7. Test and run commands (`./mvnw test`, `./mvnw verify`, run, Docker build/run)
8. Migration strategy (D3 choice, role model, residual risk)
9. Render/Neon setup summary and plan checks
10. Usage walkthrough
11. **Limitations and non-guarantees** (from `04` §4)
12. **Observed test results** (date, commit, counts; unresolved items)
13. Provider/free-tier notes with check date
14. Live URL (only if it exists)

## 8. Reporting cadence

At each milestone exit report: what was done, tests run **with observed results**, hours spent vs plan, new risks, and any decision needed. Surface blockers immediately (account eligibility, TLS, proxy behaviour, quota).
