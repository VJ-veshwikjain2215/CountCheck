# 02. Architecture and Decisions

## 1. Technology stack

| Layer | Choice | Notes |
|---|---|---|
| Language/runtime | **Java 21** (JDK build, JRE runtime) | Brief mandates 21. Boot 4.1 supports Java 17–26, so 21 is fine. |
| Framework | **Spring Boot 4.1.x** (latest patch at build time) | Spring Framework 7, Spring Security 7.1, Hibernate 7.x, Jackson 3, Tomcat 11. Boot 3.5 is out of OSS support (ended 30 Jun 2026). Boot 4.0 support ends 31 Dec 2026. |
| Web/UI | Spring MVC + **Thymeleaf** (server-rendered) + one small static JS file + one CSS file | No SPA, no frontend build chain |
| Security | Spring Security 7 (CSRF, headers, deny-by-default) + custom servlet filters | |
| Persistence | **PostgreSQL** via Spring Data JPA (reads) + `JdbcTemplate`/`JdbcClient` (idempotent insert) | |
| Migrations | **Flyway** (`spring-boot-starter-flyway` + `flyway-database-postgresql`) | Boot 4 requires the starter; `flyway-core` alone no longer auto-runs migrations |
| Build | Maven + wrapper | No local Maven needed |
| Container | Multi-stage Docker, non-root runtime | |
| Hosting | **Render Free** web service (Docker) | 512 MB instance |
| Database host | **Neon Free** PostgreSQL | |
| Tests | JUnit 5, Spring MockMvc, Testcontainers PostgreSQL | H2 is **not** acceptable for concurrency tests |

> Dependency artifact names change between Boot lines and Testcontainers versions. Generate the project from start.spring.io at implementation time and confirm names against the generated `pom.xml`. The list in `03` is the intent, not a lockfile.

## 2. System context

```
 Browser (any public visitor)
     │  HTTPS (TLS terminated by Render)
     ▼
 ┌────────────────────────── Render Free web service (Docker, 512 MB) ───────────────────────────┐
 │ Tomcat → [RequestLimitFilter: rate + body cap]  (runs BEFORE Spring Security)                │
 │        → Spring Security chain (CSRF, headers, deny-by-default)                              │
 │        → CountController → SubmissionFacade (non-transactional; maps failures)               │
 │                              → CountService (@Transactional) → IdempotentInsertRepository    │
 │                              → CountRepository (JPA read-only queries)                       │
 │        → Thymeleaf templates (warning fragment on every page)                                │
 └─────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                           │ JDBC over TLS, Hikari pool (max 3)
                                           ▼
                             Neon Free PostgreSQL (pooled endpoint at runtime;
                             direct endpoint for migrations); scales to zero after 5 min idle
```

Single instance, single database, one table. No cache, queue, session store, or external API.

## 3. Component responsibilities

| Component | Responsibility | Must not |
|---|---|---|
| `RequestLimitFilter` | Global fixed-window rate limit; 413 on oversize/chunked-oversize POST bodies; runs ahead of Spring Security | Trust forwarded headers for limiting; log bodies |
| `SecurityConfig` | Explicit `SecurityFilterChain`: permitted routes, CSRF on, headers/CSP, custom `AccessDeniedHandler` (friendly 403) | Enable login/basic/logout |
| `CountController` | Parse/validate request shape, render views, 303 redirect, map exceptions to status pages | Contain SQL or transaction logic |
| `CountSubmission` (domain) | Normalized, validated submission value object (name, two counts, request UUID) | Accept client-computed difference/timestamp |
| `SubmissionFacade` | **Outside** the transactional proxy; calls `CountService`; catches transaction/commit exceptions; classifies outcome as known/unknown | Ever turn an exception into "success" |
| `CountService` | `@Transactional`: runs the idempotent insert algorithm (see `03` §6) | Check-then-insert; `DO UPDATE` |
| `IdempotentInsertRepository` | Parameterized `INSERT … ON CONFLICT (request_id) DO NOTHING RETURNING id` and follow-up `SELECT` by `request_id` | Concatenate SQL |
| `CountRepository` | Keyset history queries, detail by id | Expose sort/size parameters |
| `ErrorHandler` | Uniform, safe error pages with the warning and a correlation ID | Leak internals |
| `pending-count.js` | `sessionStorage` save/restore/clear of pending entry | Store CSRF token; auto-submit; handle real data |

## 4. Key flows

### 4.1 Successful save
1. `GET /counts/new` → session + CSRF token created; UUID v4 generated; form rendered.
2. Script stores pending entry (values + UUID) on submit.
3. `POST /counts` → limiter/size cap → CSRF check → parameter whitelist → validation → `SubmissionFacade` → `CountService` tx: insert returns id → **commit** → controller returns **303** to `/counts/{id}`.
4. Detail page renders, script clears matching pending entry.

### 4.2 Retry after lost response (commit succeeded)
Same UUID + same payload → insert returns no row → second statement (new snapshot under READ COMMITTED) finds the winner row → compare → identical → return original id → 303. One row total.

### 4.3 Conflict
Same UUID, different normalized payload → winner row differs → rollback, **409** page explains; user opens a fresh form (new UUID). Never overwrite, never silently mint a new key.

### 4.4 Database unavailable on write
Pool acquisition fails before any statement → *known not saved*. Any failure after the transaction began, including commit errors → *unknown outcome*. Both return 503 with retry guidance; form and UUID preserved.

### 4.5 Database unavailable on read
503 generic page (never an empty list that looks like "no records").

## 5. Architecture Decision Records

Format: **Decision**, **Why**, **Trade-off / consequence**. Status "Proposed" items need owner confirmation (see README §5).

**ADR-001: Spring Boot 4.1.x on Java 21.** *Why:* 3.5 is out of OSS support; 4.0 support ends 31 Dec 2026; 4.1 is the current GA line and supports 21. *Trade-off:* Boot 4 modularization changes starter names and test annotations (`@MockitoBean`, new `@WebMvcTest` package, `spring-boot-starter-webmvc-test`); fewer community snippets than Boot 3. *Status:* Proposed (D2).

**ADR-002: Server-rendered Thymeleaf, no SPA.** *Why:* fewer moving parts, native CSRF integration, minimal JS (one file), small memory footprint. *Trade-off:* full page loads; acceptable for a four-page demo.

**ADR-003: Idempotency via UNIQUE(request_id) + `INSERT … ON CONFLICT DO NOTHING RETURNING id` + separate `SELECT`, all in one READ COMMITTED transaction.** *Why:* removes the check-then-insert race; the second statement gets a fresh snapshot and can see the committed winner; `DO UPDATE` would break append-only. *Trade-off:* behaviour depends on PostgreSQL READ COMMITTED semantics, so it **must** be proven with real concurrent PostgreSQL tests (T-CON-*). A single-statement CTE combining insert and lookup is **rejected** until its snapshot behaviour is proven.

**ADR-004: Do not set isolation explicitly over the Neon pooler; rely on PostgreSQL's default READ COMMITTED and assert it in a test.** *Why:* the pooler runs PgBouncer in transaction mode where session-level `SET` is unreliable; a Spring `isolation=READ_COMMITTED` request may issue session-level isolation changes. Spring only changes isolation when it differs from the connection's current level, so it should be a no-op, but this is unverified on the pooler. *Mitigation:* startup/IT check `SHOW transaction_isolation` = `read committed`; optional role-level `ALTER ROLE … SET default_transaction_isolation`. *Status:* Proposed; validate in M6.

**ADR-005: JPA for reads, JDBC for the conflict-aware insert.** *Why:* the brief's design; JPA's `save()` cannot express `ON CONFLICT DO NOTHING RETURNING`. *Trade-off:* two persistence styles in a tiny app, plus Hibernate startup/memory cost on 512 MB. *Alternative to consider (D4):* `JdbcClient` for everything, dropping JPA. Hibernate is set to `validate`, never `create/update`.

**ADR-006: Keyset pagination on `(created_at DESC, id DESC)`, page size fixed at 20, fetch 21.** *Why:* stable ordering, no `OFFSET` cost, no client-controlled size/sort. *Trade-off:* not a frozen snapshot; `created_at` is `now()` (transaction start), so timestamp order is not strict commit order, and late commits can appear "above" what a user already saw. Cursor must preserve microsecond precision.

**ADR-007: Flyway owns the schema; runtime role is least-privilege.** *Why:* Hibernate must not mutate schema; a web-exposed process should not hold DDL rights. *Default (D3-A):* migrate from a trusted machine/CI against Neon's **direct** endpoint with an owner role; the app runs with `spring.flyway.enabled=false` and a role limited to `SELECT, INSERT` on `count_entries`. *Fallback (D3-B):* auto-migrate at startup with elevated credentials, with the residual risk documented. Flyway needs the direct endpoint because it uses session-level locking, which a transaction-mode pooler does not support.

**ADR-008: Session-bound CSRF (default `HttpSessionCsrfTokenRepository`) with a friendly `AccessDeniedHandler`.** *Why:* default, minimal, integrates with Thymeleaf. *Consequence:* a session expiry or Render restart invalidates tokens, so recovery relies on the `sessionStorage` restore flow (FR-5.4). Cookie-based CSRF was considered but would not fix expiry-by-restart and adds JS complexity. CSRF does **not** stop deliberate public spam.

**ADR-009: Global in-memory fixed-window rate limiter + body-size cap in a servlet filter ordered before Spring Security.** *Why:* CSRF resolution reads `_csrf` from the body, so an unbounded body would be parsed first. *Trade-off:* single instance only; resets on restart; can lock out all users if abused; not DDoS protection; does not cover bandwidth consumed before the filter. Static assets are excluded from the dynamic-request count.

**ADR-010: Real PostgreSQL in tests (Testcontainers), never H2.** *Why:* H2 cannot establish PostgreSQL concurrency/conflict semantics. *Consequence:* integration tests need a Docker daemon, so they cannot run inside the Docker **image build**; run them locally and in CI (D5). Match the Postgres major version of the Neon project.

**ADR-011: `sessionStorage` for pending-entry recovery, never `localStorage`, never the CSRF token.** *Why:* tab-scoped, short-lived, synthetic-only data; explicit user action required to resubmit. *Trade-off:* not durable, not private; documented as such.

**ADR-012: Neon pooled endpoint for runtime; direct endpoint for migrations; Hikari max 3, min idle 0.** *Why:* Neon recommends pooling for web apps; free compute is small; PgBouncer supports protocol-level prepared statements (SQL-level `PREPARE` is not supported). *Consequence:* avoid session state in app code; set `statement_timeout`/`lock_timeout` at **role level** (startup `options` parameters are not reliable through the pooler). If prepared-statement issues appear, set the pgjdbc `prepareThreshold=0` as a fallback.

**ADR-013: No health-check/uptime pings against DB-backed routes.** *Why:* each ping wakes Neon compute and burns CU-hours; keeping Render awake with pings burns instance hours. Leave Render's health check path unset or point it at a static asset excluded from the limiter.

## 6. Cross-cutting design rules

- **Fail honestly:** known vs unknown outcome; never "success" on exception; never "empty" on read failure.
- **No client-trusted derived data:** difference and timestamp are server/DB-computed.
- **Escaping:** `th:text` only; `th:utext` is banned (add a build-time grep check).
- **Logging:** error category + correlation ID only; no bodies, tokens, JDBC URL, or credentials.
- **Time:** all stored and displayed times are UTC `Z`.
