# 03. Technical Specification

> Names, versions and property keys below are the **intent**. Confirm against the generated project and current docs at implementation time (Boot 4 renamed several starters and properties).

## 1. Repository layout (proposed, not yet created)

```
countcheck/
├─ README.md
├─ Dockerfile
├─ .dockerignore
├─ .gitignore
├─ pom.xml
├─ mvnw, mvnw.cmd, .mvn/wrapper/
├─ .github/workflows/ci.yml            ← addition: runs Testcontainers ITs (decision D5)
├─ src/main/java/<package>/
│   ├─ CountCheckApplication.java
│   ├─ config/SecurityConfig.java
│   ├─ web/CountController.java, RequestLimitFilter.java, ErrorHandler.java
│   ├─ domain/CountEntry.java, CountSubmission.java
│   ├─ persistence/CountRepository.java, IdempotentInsertRepository.java
│   └─ service/CountService.java, SubmissionFacade.java   ← facade is an addition
├─ src/main/resources/
│   ├─ application.yml (+ application-local.yml, application-demo.yml)
│   ├─ db/migration/V1__create_count_entries.sql
│   ├─ db/seed/V2__seed_demo.sql       ← only on demo profile (decision D6)
│   ├─ templates/ fragments/warning.html, counts.html, count-form.html, count-detail.html, error.html
│   └─ static/css/app.css, static/js/pending-count.js
└─ src/test/java/<package>/
    ├─ CountValidationTest.java, CountControllerTest.java
    ├─ CountServiceIntegrationTest.java, PaginationIntegrationTest.java
    └─ RequestLimitFilterTest.java, SecurityHeadersTest.java   ← additions
```

Never commit: `target/`, `.env*`, IDE files, local credentials.

## 2. Dependencies (intent)

| Purpose | Boot 4 artifact (verify) |
|---|---|
| Web MVC | `spring-boot-starter-webmvc` |
| Templates | `spring-boot-starter-thymeleaf` |
| Validation | `spring-boot-starter-validation` |
| Security | `spring-boot-starter-security` |
| JPA | `spring-boot-starter-data-jpa` |
| JDBC | `spring-boot-starter-jdbc` (usually transitive) |
| Driver | `org.postgresql:postgresql` (runtime) |
| Migrations | `spring-boot-starter-flyway` **+** `org.flywaydb:flyway-database-postgresql` |
| Tests | `spring-boot-starter-webmvc-test`, `spring-security-test`, Testcontainers PostgreSQL + JUnit module (Testcontainers 2.x renamed some module artifacts; verify names), `spring-boot-starter-flyway-test` if used |
| **Not included** | Actuator (unless a minimal, static-only need arises), multipart, Python anything |

## 3. Routes and HTTP contract

| Method & path | Behaviour | Success | Errors |
|---|---|---|---|
| `GET /` | Redirect to history | **303** → `/counts` | |
| `GET /counts?mismatch=true\|false&beforeTimestamp=<UTC ISO instant>&beforeId=<positive long>` | History, max 20 rows. Defaults: all rows, first page | 200 | 400 invalid/half cursor, bad filter, unknown or repeated params; 429; 503 read failure |
| `GET /counts/new` | Blank form, new UUID v4, CSRF token (creates session) | 200 | 429; 503 |
| `POST /counts` | Create/replay. `application/x-www-form-urlencoded` only, ≤ 4 KiB, no query string | **303** → `/counts/{id}` | 400 validation (field errors, values and request ID preserved); 403 CSRF; 409 key reuse with different payload; 413 oversize; 415 other content types; 429; 503 unknown/unavailable |
| `GET /counts/{id:[0-9]+}` | Immutable detail | 200 | 404 absent or id out of `long` range; 503 |
| `GET /css/**`, `GET /js/**` | Static assets (public; excluded from rate counting) | 200 | |
| everything else | Denied | | 404/403/405, never 2xx |

Rules:
- No edit, delete, reset, export, admin, schema, or API endpoints. `anyRequest().denyAll()` as the final matcher.
- **Allowed GET `/counts` parameters:** exactly `mismatch`, `beforeTimestamp`, `beforeId`. Anything else → 400. No page-size or sort parameters.
- `beforeTimestamp` and `beforeId` must appear **together** or not at all.
- `beforeTimestamp` must match `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$` (UTC only; no offsets) then parse as `Instant`. `beforeId` is a positive `long`.
- **Allowed POST parameters:** exactly `_csrf`, `requestId`, `itemName`, `recordedCount`, `physicalCount`; each exactly once. Reject unknown, repeated, and query-string parameters.
- All HTML is escaped; the warning fragment is on every page including error pages.

## 4. Validation rules (centralized in `CountSubmission` factory)

Order: shape (parameter whitelist) → normalize → validate → only then open a transaction.

| Field | Rule | Reject example |
|---|---|---|
| `requestId` | Strict regex `^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$`, then `UUID.fromString`, canonicalize lowercase. **Do not** rely on `UUID.fromString` alone (it accepts malformed input). | `1-1-1-1-1`, UUID v1/v7, empty |
| `itemName` | `strip()` → `codePointCount` ∈ [1, 80] → reject `Character.isISOControl`, NUL, unpaired surrogates. No NFC normalization (D-note: normalization = `strip()` only, preserving case/internal spaces). | blank, 81 code points, `a\nb`, lone surrogate |
| `recordedCount`, `physicalCount` | Regex `^(0\|[1-9][0-9]{0,5})$` (ASCII `[0-9]` explicitly, not `\d` or `Character.isDigit`), then parse and range-check 0…100000 | `-1`, `1.0`, `1e3`, ` 5`, `007`, `٣`, `100001`, `""` |
| difference | `physical − recorded` computed from validated ints; display `+n`, `−n` (U+2212 or `-`; pick one and test), or `0` | never accepted from client |

Idempotent "same payload" means equal **normalized** `itemName`, `recordedCount`, `physicalCount`. A different payload with an equal difference is still a conflict.

## 5. Schema (Flyway `V1__create_count_entries.sql`)

```sql
CREATE TABLE count_entries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id      UUID         NOT NULL,
    item_name       VARCHAR(80)  NOT NULL,
    recorded_count  INTEGER      NOT NULL,
    physical_count  INTEGER      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_count_entries_request_id UNIQUE (request_id),
    CONSTRAINT ck_count_entries_name_len   CHECK (char_length(item_name) BETWEEN 1 AND 80),
    CONSTRAINT ck_count_entries_recorded   CHECK (recorded_count BETWEEN 0 AND 100000),
    CONSTRAINT ck_count_entries_physical   CHECK (physical_count BETWEEN 0 AND 100000)
);

CREATE INDEX idx_count_entries_created_id
    ON count_entries (created_at DESC, id DESC);
```

Notes:
- `char_length` counts characters (≈ code points under UTF-8), matching the Java code-point rule.
- `now()` is **transaction start time**, not commit time. This is why timestamp order ≠ commit order.
- No `difference` column (derived). No update/delete grants to the runtime role.
- Optional partial index for the filter (`WHERE physical_count <> recorded_count`) is **not needed** at demo scale; revisit only if measured.
- JPA entity is `@Immutable`/read-only; `spring.jpa.hibernate.ddl-auto=validate`.
- Optional seed (D6): `db/seed/V2__seed_demo.sql` with fictional items, fixed UUIDs, `INSERT … ON CONFLICT (request_id) DO NOTHING`; loaded only via a demo-profile `spring.flyway.locations`. Never wipes rows. Tests either use a clean database or account for seed rows.

## 6. Idempotent write algorithm (authoritative)

```
submit(sub):                      // sub is already normalized + validated
  @Transactional(READ COMMITTED effective; see ADR-004)     // inside CountService
    ids = jdbc.query(
        "INSERT INTO count_entries (request_id,item_name,recorded_count,physical_count)
         VALUES (?,?,?,?)
         ON CONFLICT (request_id) DO NOTHING
         RETURNING id", …)
    if ids not empty:
        return Created(ids[0])                              // commit happens on method exit
    row = jdbc.query("SELECT id,item_name,recorded_count,physical_count
                      FROM count_entries WHERE request_id = ?", sub.requestId)   // NEW statement → NEW snapshot
    if row absent:      throw TemporaryError                // never invent success
    if row.payload == sub.payload:  return Replayed(row.id) // same id, no new row
    else:               throw KeyConflict                   // → rollback → 409
```

`SubmissionFacade` (non-transactional) wraps the call:

```
try   { outcome = countService.submit(sub); }
catch (KeyConflict)                  → 409 (known: nothing written)
catch (CannotAcquireConnection …)    → 503 KNOWN not saved (no statement was sent)
catch (TemporaryError, DataAccessException, TransactionException,
       any commit-time exception)    → 503 UNKNOWN outcome; preserve UUID + values
return 303 → /counts/{outcome.id}    // only reached after the transaction proxy returned normally (= committed)
```

Properties required:
1. Two simultaneous identical requests → one row, same id for both.
2. Same key, different payload, concurrent → one winner, one 409, one row, no update.
3. If the winner **rolls back**, the waiting request's insert proceeds and may become the winner.
4. If a conflicting row cannot be read afterwards → temporary error.
5. Lock/DB timeouts are *unknown outcome*.
6. Idempotency lasts only while the row exists in the same database (no promise after data loss/replacement or key loss).

**Do not** use `DO UPDATE`, check-then-insert, or a single-statement CTE for insert+lookup.

## 7. History query (keyset)

Four fixed, parameterized statements (cursor present/absent × mismatch on/off). No SQL fragments from clients.

```sql
-- first page, all rows
SELECT id, request_id, item_name, recorded_count, physical_count, created_at
FROM count_entries
ORDER BY created_at DESC, id DESC
LIMIT 21;

-- next page, mismatch only
SELECT id, request_id, item_name, recorded_count, physical_count, created_at
FROM count_entries
WHERE physical_count <> recorded_count
  AND (created_at, id) < (CAST(? AS timestamptz), ?)
ORDER BY created_at DESC, id DESC
LIMIT 21;
```

- Display the first 20; show **Next** only if a 21st row came back.
- Build the next link from the **last displayed** row's `(created_at, id)`, URL-encoded, preserving `mismatch`.
- Cursor timestamp is serialized with **full microsecond precision**, UTC `Z`. Truncating to millis is a bug (T-PAG-06).
- Mismatch predicate is applied *before* `LIMIT`.
- Empty page → explain clearly ("No entries match" / "No more entries"), never a silent blank.

## 8. Security configuration

### 8.1 Filter order (critical)
1. `RequestLimitFilter`: registered **before** Spring Security's filter chain (Boot's default security filter order is −100, so use a lower number). Does: rate limit; for `POST /counts` enforce `Content-Type: application/x-www-form-urlencoded`, `Content-Length` ≤ 4096 → else 413, and wrap the request stream with a counting limiter so chunked bodies are cut at 4096 bytes; reject multipart.
2. Spring Security chain: headers → CSRF (reads `_csrf` param) → authorization (deny-by-default).
3. MVC.

Reason: CSRF token resolution parses the body, so unbounded bodies must be stopped first.

### 8.2 Rate limiter
Thread-safe in-memory **global** fixed window: ≤ 120 dynamic requests/min and ≤ 20 `POST /counts`/min (tunable via config). 429 with `Retry-After` (seconds to window end). No per-IP logic and no trust in `X-Forwarded-For`. Exclude `/css/**`, `/js/**`. Honest limits: resets on restart, can deny everyone, not DDoS protection.

### 8.3 Spring Security
- `SecurityFilterChain` bean: `formLogin` off, `httpBasic` off, `logout` off, CSRF defaults on, `anyRequest().denyAll()` after explicit permits.
- Sessions: created only when a CSRF token is rendered (`/counts/new`); timeout ≈ 15 min; cookie-only tracking.
- Custom `AccessDeniedHandler`: 403 page explaining token/session expiry, with link to `/counts/new` and mention of "restore pending entry".
- Headers: `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `X-Frame-Options`/`frame-ancestors 'none'`, `Cache-Control: no-store` for dynamic pages (decide and test), HSTS on HTTPS.
- **CSP (starting point):** `default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'`. No inline scripts or styles. Adjust only if a test proves breakage.
- Cookies: `HttpOnly`, `SameSite=Lax`, `Secure` on the deployed HTTPS profile (local HTTP profile must not set `Secure` or the cookie will not stick).
- Proxy scheme: configure forwarded-header handling **only** for the Render hop; never use client-supplied forwarded headers for authorization or limiting.
- Tomcat limits (verify property names for Boot 4): form POST size ≈ 4 KB, max parameter count ≈ 20, request header/URL size ≈ 8 KB, small thread pool (≈ 20).
- Actuator, multipart, file upload: **off**.

## 9. Configuration contract

| Name | Purpose | Notes |
|---|---|---|
| `PORT` | HTTP port | `server.port=${PORT:8080}`, bind `0.0.0.0` |
| `DB_JDBC_URL` | JDBC URL **without credentials** | e.g. `jdbc:postgresql://<host>/<db>?sslmode=verify-full&sslrootcert=/etc/ssl/certs/ca-certificates.crt&connectTimeout=10&socketTimeout=15`. Verify the CA bundle exists in the runtime image and that `verify-full` works with Neon; adjust the path if not. |
| `DB_USERNAME`, `DB_PASSWORD` | Runtime role credentials | Environment secrets only; never logged |
| `SPRING_PROFILES_ACTIVE` | `local` / `demo` | `demo` enables Secure cookies, seed location |
| `RATE_LIMIT_GLOBAL_PER_MIN`, `RATE_LIMIT_POST_PER_MIN` | Limiter thresholds | Defaults 120 / 20 |
| Flyway (D3-A) | Not set on Render | `spring.flyway.enabled=false` in `demo` profile; migration run separately |
| Flyway (D3-B fallback) | `FLYWAY_URL` (direct endpoint), `FLYWAY_USER`, `FLYWAY_PASSWORD` | Elevated credentials on the web service = residual risk, document it |

Hikari (starting values, validate against Neon cold start): `maximum-pool-size=3`, `minimum-idle=0`, `connection-timeout≈8000 ms` (cold start may need more than 5 s; measure), `validation-timeout≈2000 ms`, `idle-timeout≈30000 ms`, `max-lifetime` comfortably below any provider-side limits.

Role-level DB timeouts (set once by an admin; survive the pooler):
```sql
ALTER ROLE countcheck_app SET statement_timeout = '5s';
ALTER ROLE countcheck_app SET lock_timeout = '3s';
ALTER ROLE countcheck_app SET idle_in_transaction_session_timeout = '10s';
```

JVM (starting point for a 512 MB instance; verify actual RSS): container-aware heap (`-XX:MaxRAMPercentage≈60`), `-XX:+UseSerialGC`, small thread stacks, `-XX:+ExitOnOutOfMemoryError`. Avoid `-Xmx` hard-coding unless measured.

## 10. Error → response matrix

| Condition | Status | Outcome class | Page behaviour |
|---|---|---|---|
| Validation failure | 400 | Known: not saved | Field errors, values and request ID kept |
| Unknown/repeated/extra params, bad cursor/filter | 400 | n/a | Friendly explanation |
| Missing/invalid CSRF | 403 | Known: not saved | Recovery instructions, restore offer |
| Key reuse, different payload | 409 | Known: nothing written | Explain; link to fresh form |
| Body too large | 413 | Known: not saved | Short explanation |
| Wrong content type / multipart | 415 | Known: not saved | |
| Rate limited | 429 + `Retry-After` | Known: not saved | Retry guidance |
| Pool/connection acquisition failed (no statement sent) | 503 | Known: not saved | Retry same entry |
| DB error after tx began, commit failure, timeout, conflict row unreadable | 503 | **Unknown** | "Save status unknown; retry this same entry"; check history |
| Read failure | 503 | n/a | Generic; never empty-success |
| Detail id absent | 404 | n/a | |
| Startup migration/DB failure | none | n/a | Deployment fails before any page is served (honest limitation) |

Logging per error: category + correlation ID only (no bodies, tokens, JDBC URL, credentials).

## 11. Pending-retry script contract (`pending-count.js`)

- Same-origin, external file (no inline script).
- On form `submit`: write `{requestId, itemName, recordedCount, physicalCount}` to `sessionStorage["countcheck.pending"]`. **Never** the CSRF token.
- On `/counts/new` load: if a pending entry exists and its UUID differs from the freshly rendered one, show a banner with **Restore** and **Discard**. Restore fills the fields and sets the hidden `requestId` to the stored UUID. Never auto-submit.
- On detail page: read `data-request-id`; clear pending only if it exactly equals the stored UUID.
- Wrap all storage access in try/catch; degrade silently if storage is unavailable.
- Never regenerate the UUID on retry, validation error, timeout, CSRF refresh, or restoration.

## 12. Accessibility and UI notes

Labels bound to inputs (`for`/`id`); errors associated via `aria-describedby`; keyboard-submittable form and filter toggle; the warning is a persistent banner that stays visible at ~360 px width; difference is conveyed by text (`+3`/`−3`/`0`), not color alone; empty history has an explanatory message; table scrolls or stacks on narrow screens.
