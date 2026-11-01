# 04. Security and Risk

## 1. Security posture in one paragraph

The app is **public by design**: anyone can read and add entries. Security work here is not about keeping data private. It is about (a) not misleading people about what the demo guarantees, (b) preventing the demo from being used to attack visitors (XSS, CSRF), (c) keeping the process from being trivially knocked over or run into provider quotas, and (d) keeping secrets out of everything that is public. Public access alone makes real-data use unacceptable, even over HTTPS.

## 2. Trust boundary

| Actor | Can | Cannot (by app design) |
|---|---|---|
| Any visitor | Read all history/detail; insert arbitrary synthetic entries; replay a known request ID | Edit/delete/reset via the app |
| Motivated visitor | Obtain a CSRF token and script inserts (spam) | (Not prevented: CSRF is not access control) |
| App runtime DB role | `SELECT`, `INSERT` on `count_entries` | DDL, update, delete (when D3-A is used) |
| Owner / DB admin | Anything, including altering or deleting rows | n/a. "Append-only" does not bind admins |
| Hosting providers | Restart, suspend, lose data, change limits | n/a. No SLA on free tiers |

## 3. Threats and controls (STRIDE-lite)

| ID | Threat | Control | Verified by |
|---|---|---|---|
| TH-01 | **Stored XSS** via item name | `th:text` only; CSP `script-src 'self'`; no inline JS; ban `th:utext` via CI grep | T-SEC-01, T-SEC-02 |
| TH-02 | **CSRF** (cross-site form post) | Spring Security CSRF, session-bound token | T-SEC-03 |
| TH-03 | CSRF token expiry causes lost input | Friendly 403, `sessionStorage` restore flow | T-SEC-04, T-RET-03 |
| TH-04 | **Public spam / quota burn** | Global rate limit, 20 POST/min cap, Neon autoscaling cap (0.25 CU), no keep-alive pings | T-SEC-08, T-OPS-05 |
| TH-05 | **Oversized/chunked body** | Pre-parse cap at 4 KiB in a filter ordered before Spring Security; Tomcat limits | T-SEC-09 |
| TH-06 | **SQL injection** | Parameterized statements only; fixed query set; no client SQL fragments | Code review; T-SEC-10 |
| TH-07 | **Parameter pollution / mass assignment** | Strict parameter whitelist; reject repeated/unknown | T-VAL-12 |
| TH-08 | **Clickjacking** | `frame-ancestors 'none'` / `X-Frame-Options` | T-SEC-05 |
| TH-09 | **Secret leakage** | Env vars only; non-root image; `.dockerignore`/`.gitignore`; logs exclude bodies/URLs/creds; secret scan before push | T-REPO-01..03 |
| TH-10 | **Stack trace / SQL leakage** | `ErrorHandler`, generic messages, correlation ID | T-ERR-01 |
| TH-11 | **Idempotency-key misuse** | Key is explicitly non-secret; conflict → 409; detail page exposes UUID in a data attribute only for cleanup | T-CON-02 |
| TH-12 | **Trusting forwarded headers** | No per-IP logic; forwarded handling scoped to Render hop | Config review |
| TH-13 | **Real data entered by a visitor** | Warning on every page; documented takedown procedure (runbook §7) | T-UI-01 |
| TH-14 | **Overprivileged runtime DB role** | SELECT/INSERT-only role; Flyway not run by the web service (D3-A) | T-REPO-04 |
| TH-15 | **Dependency vulnerabilities** | Use supported Boot patch; run dependency check at build; pin versions; wait before adopting brand-new releases | CI step |

## 4. Explicit non-guarantees (must appear in README and UI copy)

No authentication · no tenant isolation · no anti-spam guarantee · no audit integrity · no retention or backup · no availability · no cost ceiling if a provider account permits paid overages · no promise a saved count remains available · no DDoS protection · idempotency only while the row exists.

## 5. Risk register

Likelihood (L) and Impact (I): H/M/L. These are planning judgements, not measurements.

| ID | Risk | L | I | Mitigation / contingency | Trigger / signal |
|---|---|---|---|---|---|
| R-01 | Spam or abuse fills the table and degrades the demo | H | M | Rate limits; owner purge via SQL; read-only switch (`REVOKE INSERT`) | Row count / storage near 1 GB |
| R-02 | Real or personal data entered despite warning | M | H | Warning everywhere; purge procedure; consider removing any free-text hint that invites real names | Reports / inspection |
| R-03 | **Idempotency race implemented incorrectly** | M | H | Real PostgreSQL concurrency tests with barriers; forbid check-then-insert/CTE | T-CON-* failing |
| R-04 | **False success on commit failure** | M | H | Facade outside transaction proxy; fault-injection test | T-ERR-03 |
| R-05 | Neon cold start exceeds pool timeout on first request | H | M | Tune timeouts; friendly 503 with retry; measure | T-OPS-02 |
| R-06 | Neon compute (100 CU-h) or egress (5 GB) exhausted → DB suspended until next period | M | H | Cap autoscaling at 0.25 CU; no pings; honest outage page; do not upgrade silently | Neon console usage |
| R-07 | Render Free: spin-down (~1 min wake), restarts at any time, 750 shared hours | H | M | Documented; sessions/CSRF tokens lost on restart → restore flow | Observed wake times |
| R-08 | Render may suspend a free service with unusually high **service-initiated** traffic (e.g. external DB access) | L | H | Keep queries small and few; no polling; monitor | Suspension notice |
| R-09 | Render bandwidth/build overage billing if a payment method is on file | L | M | Check billing/usage settings **before** deploying; no paid add-ons; disable surprise rebuilds (D10) | Usage emails |
| R-10 | TLS verification to Neon fails in the runtime image (CA bundle path) | M | M | Verify early (T-OPS-04); fall back to mounted CA file; never disable verification silently | Handshake error |
| R-11 | Pooler (PgBouncer transaction mode) breaks an assumption (isolation `SET`, prepared statements) | M | M | ADR-004/012; assert `SHOW transaction_isolation`; `prepareThreshold=0` fallback | IT failures on Neon only |
| R-12 | Flyway fails against the pooled endpoint | M | M | Use **direct** endpoint for migrations | Migration error |
| R-13 | Memory pressure on 512 MB (Hibernate + Tomcat + Security) | M | M | Conservative heap, small pools/threads; consider dropping JPA (D4) | OOM kills / slow start |
| R-14 | Render's proxy alters body/chunk behaviour so the 413 cap is untestable | M | L | Test through the public URL; document actual behaviour | T-SEC-09 |
| R-15 | Estimate (14 h) is optimistic | H | M | Risk-adjusted range in `07`; never drop High tests to hit time | Burn-down |
| R-16 | Boot 4 ecosystem/documentation churn (starters, test annotations, Jackson 3) | M | L | Generate from start.spring.io; follow the official migration guide | Compile errors |
| R-17 | Provider terms, limits, or pricing change after 1 Nov 2026 | M | M | Re-verify on deployment day (runbook §2) | Doc diffs |
| R-18 | Docker build cannot run Testcontainers ITs | H | L | D5: ITs in CI/local; image build runs unit tests | Build failure if forced |
| R-19 | Demand/benefit claims implied by copy | L | M | Copy review: no claims of demand, savings, or accuracy improvement | README/UI review |

## 6. Security review checklist (run before any public deploy)

- [ ] Warning on every route including 400/403/404/409/413/429/503 pages
- [ ] `grep -R "th:utext" src/` returns nothing
- [ ] CSRF: missing/invalid → 403, zero rows
- [ ] No route for edit/delete/reset/export/admin; unsupported methods denied
- [ ] CSP header present; page works with it (no inline script/style)
- [ ] Cookies: HttpOnly, SameSite=Lax, Secure (on HTTPS profile)
- [ ] Rate limiter and body cap run **before** Spring Security (verified by test)
- [ ] Runtime DB role cannot DDL/UPDATE/DELETE (verified by attempting each as that role)
- [ ] `sslmode=verify-full` actually verified (try a wrong hostname → must fail)
- [ ] No secrets in git history, image layers (`docker history`), logs, README, JS, HTML
- [ ] Container runs as non-root
- [ ] Render health check path is unset or static; no pinger hits DB routes
- [ ] Neon autoscaling max capped; billing/usage alerts reviewed
