# 05. Test Plan

> **These are specifications, not results.** Every status below is `NOT RUN`. Record observed outcomes only; never infer a pass from the brief. If a High test cannot be completed, report it as unresolved. Do not drop it.

## 1. Strategy

| Level | Tooling | Runs where | Covers |
|---|---|---|---|
| Unit | JUnit 5 | Local, CI, **Docker image build** | Validation, difference arithmetic, cursor building, limiter logic |
| Controller/slice | Spring MockMvc + Spring Security Test | Local, CI | Routes, status codes, CSRF, headers, escaping, error pages |
| **Integration (real PostgreSQL)** | **Testcontainers PostgreSQL** (match Neon's major version) | **Local and CI only** (needs Docker daemon) | Idempotency, concurrency, keyset paging, constraints, role privileges, isolation |
| Manual smoke (deployed) | Browser + curl | Public demo, synthetic data | CSRF/session, cold start, proxy behaviour, TLS, quotas, free-plan checks |

H2 is never used for anything involving constraints, conflicts, or concurrency.

**Concurrency technique:** separate connections/transactions per thread; `CyclicBarrier`/`CountDownLatch` to line up start; deterministic interleavings using held-open transactions (see T-CON-03/04). Repeat randomized races N times (e.g. 50–200) to expose flakiness.

**Fault injection:** a `DataSource`/transaction-manager decorator in tests that can fail before insert, fail on commit *after* executing the insert, and simulate pool exhaustion.

## 2. Priority matrix

### 2.1 High

| ID | Req | Level | Scenario | Expected | Status |
|---|---|---|---|---|---|
| T-UI-01 | FR-4.2 | Controller + manual | Request every route and every error status (200/400/403/404/409/413/429/503) | Public-demo warning present on all; no login, no "private/secure/durable" claims | NOT RUN |
| T-SEC-06 | FR-3.1 | Controller | Probe `PUT/PATCH/DELETE` on `/counts`, `/counts/{id}`; probe `/reset`, `/admin`, `/export`, `/actuator/**` | Absent or denied; never 2xx | NOT RUN |
| T-SEC-07 | NFR-S1 | Controller | Unsupported methods/paths | 404/403/405 only | NOT RUN |
| T-ERR-02 | FR-5.3 | Integration | DB unavailable **before** insert (pool exhausted/connection refused) | 503, *known not saved*; zero rows; form + UUID preserved | NOT RUN |
| T-ERR-03 | FR-5.3 | Integration (fault injection) | Insert executes, **commit throws** | 503 *unknown outcome*; **never** 303/success; retry with same key/payload → exactly one row | NOT RUN |
| T-ERR-04 | FR-5.1 | Integration | Commit succeeds, response "lost" (client retries) | Retry returns **original id**, one row | NOT RUN |
| T-ERR-05 | NFR-R1 | Integration | Quota-like failure on read and write | 503 generic; no empty-success history | NOT RUN |
| T-CON-01 | FR-5.1 | Integration | N threads, same UUID + same payload, barrier start | Exactly 1 row; all callers get same id | NOT RUN |
| T-CON-02 | FR-5.2 | Integration | Same UUID, **different** payload (including equal difference), concurrent | 1 winner, others 409, 1 row, no update | NOT RUN |
| T-CON-03 | FR-5.1 | Integration (interleaving) | Tx A inserts and holds uncommitted; Tx B same key blocks; A commits | B gets no row from insert, then SELECT sees A's row, returns A's id; still 1 row | NOT RUN |
| T-CON-04 | FR-5.1 | Integration (interleaving) | As above but **A rolls back** | B's insert succeeds; 1 row, from B | NOT RUN |
| T-CON-05 | ADR-004 | Integration | `SHOW transaction_isolation` on the service's connection | `read committed` (also on Neon pooled endpoint in smoke test) | NOT RUN |
| T-CON-06 | ADR-003 | Code review + test | Search for `DO UPDATE`, check-then-insert, CTE-combined lookup | None present | NOT RUN |
| T-SEC-01 | NFR-S2 | Controller | Submit `<script>alert(1)</script>`, `"><img src=x onerror=…>` as item name | Stored as text; rendered escaped on history and detail; no execution | NOT RUN |
| T-SEC-02 | NFR-S2 | Controller + manual | Inspect CSP; load pages with CSP active | Header present; pages function; no inline script/style; `grep th:utext` empty | NOT RUN |
| T-SEC-03 | NFR-S1 | Controller | POST with no `_csrf`, invalid `_csrf` | 403; **zero rows** | NOT RUN |
| T-SEC-04 | FR-5.4 | Manual | Let session expire / restart service, then POST | 403 with recovery instructions; restore flow available | NOT RUN |

### 2.2 Medium

| ID | Req | Level | Scenario | Expected | Status |
|---|---|---|---|---|---|
| T-VAL-01 | FR-4.1 | Unit/controller | Counts `0` and `100000` | Accepted | NOT RUN |
| T-VAL-02 | FR-4.1 | Unit | Counts `-1`, `100001`, `1000000` | 400; 0 rows | NOT RUN |
| T-VAL-03 | FR-4.1 | Unit | `1.0`, `1,000`, `1e3`, `+5`, `0x10` | 400 | NOT RUN |
| T-VAL-04 | FR-4.1 | Unit | `""`, `"   "`, missing parameter | 400 (empty ≠ 0) | NOT RUN |
| T-VAL-05 | D8 | Unit | `" 5"`, `"5 "`, `"007"`, Arabic-Indic digit `٣`, full-width `５` | 400 | NOT RUN |
| T-VAL-06 | FR-4.1 | Unit | Name: 1 and 80 code points (incl. emoji/astral chars) | Accepted | NOT RUN |
| T-VAL-07 | FR-4.1 | Unit | Name: 0 (after strip), 81 code points | 400, no truncation | NOT RUN |
| T-VAL-08 | FR-4.1 | Unit | Name with `\n`, `\t`, NUL, other control chars; lone surrogate | 400 | NOT RUN |
| T-VAL-09 | FR-4.1 | Unit | Name `"  Widget  A "` | Stored `Widget  A` (strip only; internal spacing and case preserved) | NOT RUN |
| T-VAL-10 | FR-1.2 | Unit | `requestId`: malformed, `1-1-1-1-1`, v1, v7, upper-case v4, empty | Malformed/non-v4 → 400; upper-case v4 → accepted, canonicalized | NOT RUN |
| T-VAL-11 | NFR-S3 | Controller | Body > 4 KiB (Content-Length and chunked), multipart, JSON | 413 / 415; 0 rows | NOT RUN |
| T-VAL-12 | TH-07 | Controller | Repeated `itemName`, unknown field, query string on POST | 400; 0 rows | NOT RUN |
| T-VAL-13 | FR-4.1 | Controller | Client-supplied `difference`/`createdAt` fields | Rejected (unknown field); never used | NOT RUN |
| T-VAL-14 | FR-4.1 | DB | Direct insert violating each CHECK/UNIQUE as the app role | Rejected by constraints | NOT RUN |
| T-ARI-01 | FR-2.1 | Unit | recorded 5, physical 8 | `+3` | NOT RUN |
| T-ARI-02 | FR-2.1 | Unit | 8, 5 | `−3` | NOT RUN |
| T-ARI-03 | FR-2.1 | Unit | 0, 0 | `0` | NOT RUN |
| T-ARI-04 | FR-2.2 | Integration | Mixed rows incl. negatives; mismatch filter | Only non-zero rows, negatives included | NOT RUN |
| T-ARI-05 | FR-2.1 | Unit | Extremes (0,100000), (100000,0) | `+100000`, `−100000` | NOT RUN |
| T-PAG-01 | FR-3.3 | Integration | Seed > 40 rows incl. **equal timestamps** and mixed matches; page through | Deterministic `(created_at,id)` order; ≤ 20/page; no duplicates/gaps in static data | NOT RUN |
| T-PAG-02 | FR-2.2 | Integration | Filter on; 20+ mismatches interleaved with matches | Filter applied **before** limit; full pages of mismatches | NOT RUN |
| T-PAG-03 | FR-3.3 | Controller | Cursor with only one of the two params; malformed timestamp; offset form `+01:00`; negative/zero/overflow id; unknown `mismatch`; extra param; `size=` | 400 friendly | NOT RUN |
| T-PAG-04 | FR-3.3 | Integration | Exactly 20, exactly 21 rows | 20 → no Next; 21 → Next present and page 2 has 1 | NOT RUN |
| T-PAG-05 | FR-3.3 | Integration | Insert while browsing | No crash; honest behaviour (no promise of frozen snapshot); empty page explained | NOT RUN |
| T-PAG-06 | ADR-006 | Integration | Rows with microsecond-distinct timestamps; follow `Next` links | No skipped/duplicated rows (cursor keeps µs precision) | NOT RUN |
| T-PAG-07 | NFR-R1 | Controller | Empty database | Clear empty-state message | NOT RUN |
| T-RET-01 | FR-5.3 | Controller | POST fails validation | Same form, same request ID, values retained | NOT RUN |
| T-RET-02 | FR-5.3 | Manual | Unknown-outcome page | Shows read-only pending entry + "Retry same entry"; UUID unchanged | NOT RUN |
| T-RET-03 | FR-5.4 | Manual (browser) | Session lost → fresh form → Restore → submit | Same UUID; user explicitly submits; one row | NOT RUN |
| T-RET-04 | FR-5.4 | Manual | Detail page for a *different* record while a pending entry exists | Pending entry **not** cleared (exact-match only) | NOT RUN |
| T-RET-05 | FR-5.4 | Manual | Storage disabled/unavailable | No JS errors; same-page retry still works | NOT RUN |
| T-SEC-08 | NFR-S3 | Integration/controller | > 120 dynamic req/min; > 20 POST/min | 429 with `Retry-After`; static assets not counted; concurrency-safe counters | NOT RUN |
| T-SEC-09 | NFR-S3 | Manual via public URL | Oversize and chunked-oversize POST **through Render's proxy** | 413 or observed proxy behaviour documented | NOT RUN |
| T-SEC-10 | TH-06 | Integration | Item names such as `'; DROP TABLE …--`, `%`, `_`, backslash | Stored literally; table intact | NOT RUN |
| T-OPS-02 | R-05 | Manual | First request after Neon scale-to-zero | Friendly 503 or success within timeouts; observed time recorded | NOT RUN |
| T-OPS-03 | R-05 | Manual | Request after Render spin-down (~1 min) | App recovers; DB reconnects; CSRF/session loss handled | NOT RUN |

### 2.3 Low

| ID | Scenario | Expected | Status |
|---|---|---|---|
| T-UI-02 | Labels associated; keyboard-only submit and filter; error messages understood; screen-reader smoke | Pass | NOT RUN |
| T-UI-03 | Mobile width (~360 px): warning visible, table readable | Pass | NOT RUN |
| T-SEC-05 | Framing the site in an iframe | Blocked | NOT RUN |
| T-ERR-01 | Trigger errors; inspect pages and logs | No SQL/stack/host/JDBC/secret; correlation ID only | NOT RUN |
| T-REPO-01 | `.gitignore` / `.dockerignore` cover `target/`, `.env*`, `.git` | Pass | NOT RUN |
| T-REPO-02 | `docker history` and image filesystem inspection | No secrets; non-root user | NOT RUN |
| T-REPO-03 | Secret scan on repo + history | None | NOT RUN |
| T-REPO-04 | As the runtime role attempt DDL, UPDATE, DELETE | All denied | NOT RUN |
| T-REPO-05 | Follow README on a clean machine | Local run + tests succeed as documented | NOT RUN |
| T-OPS-01 | Clean build + start locally and in container | Starts; migrations applied once; app serves | NOT RUN |
| T-OPS-04 | `sslmode=verify-full` to Neon from the container; negative test with wrong host | Succeeds; wrong host **fails** | NOT RUN |
| T-OPS-05 | Confirm Neon autoscaling max = 0.25 CU; confirm no health check/pinger hits DB routes; read usage page | As configured | NOT RUN |
| T-OPS-06 | Confirm Render plan = Free, no paid add-ons, billing/overage settings reviewed | Recorded | NOT RUN |
| T-OPS-07 | Measure RSS and startup time on the 512 MB instance | Within instance; numbers recorded | NOT RUN |

## 3. Traceability (requirement → tests)

| Requirement | Tests |
|---|---|
| FR-1.1/1.2/1.3 | T-VAL-*, T-RET-01, T-CON-01, manual happy path |
| FR-2.1/2.2 | T-ARI-01..05, T-PAG-02 |
| FR-3.1 | T-SEC-06, T-REPO-04, T-VAL-14 |
| FR-3.2/3.3 | T-PAG-01..07 |
| FR-3.4 | Controller test for 200/404/503 on detail |
| FR-4.1 | T-VAL-01..14 |
| FR-4.2 | T-UI-01 |
| FR-4.3 | T-ERR-01 |
| FR-5.1/5.2 | T-CON-01..06, T-ERR-04 |
| FR-5.3 | T-ERR-02/03/05, T-RET-01/02 |
| FR-5.4 | T-RET-03/04/05, T-SEC-04 |
| NFR-S1..S4 | T-SEC-01..10, T-REPO-* |
| NFR-R1/R2 | T-ERR-05, T-OPS-02 |
| NFR-O1 | T-OPS-05, T-OPS-06 |
| NFR-A1 | T-UI-02, T-UI-03 |
| NFR-D1 | T-REPO-05 |

## 4. Results log (fill in only with observed outcomes)

| Date | Build/commit | Environment | Suite | Passed | Failed | Not run | Notes / unresolved items |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## 5. Exit criteria

- All **High** tests pass **or** unresolved items are prominently reported in the README (never marked pass).
- All **Medium** tests pass or are reported as above.
- Low tests run where feasible; skipped ones listed.
- Deployed smoke test (manual) completed with synthetic data only.
- Overruns in estimate are reported rather than silently dropping High tests.
