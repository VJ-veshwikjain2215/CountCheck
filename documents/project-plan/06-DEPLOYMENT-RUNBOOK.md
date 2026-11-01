# 06. Deployment Runbook

> **Do not execute any step here until the owner has explicitly authorized implementation and deployment (Gate G1 in `07-PROJECT-PLAN.md`).** If provider signup, plan eligibility, network, proxy body limits, or TLS validation block a step, **stop and report**. Do not select a paid fallback or change the architecture silently.

## 1. Free-tier facts (checked 1 Nov 2026)

Re-verify on deployment day; these change.

### Render Free web service ([render.com/docs/free](https://render.com/docs/free))

| Fact | Consequence for CountCheck |
|---|---|
| Spins down after **15 min** without inbound traffic; wake takes **about one minute**, and browsers see a loading page | First visitor after idle waits ~1 min; sessions/CSRF tokens do not survive a restart |
| **750 free instance-hours per workspace per month**, shared by all Free web services; spun-down time doesn't count; exhausting them suspends all Free web services until next month | One always-awake service uses ~720–744 h; other Free services in the same workspace compete |
| Ephemeral filesystem; Render may restart a Free service at any time | Nothing stored locally; DB is the only state |
| Outbound **bandwidth** and **build pipeline minutes** are metered. Over the limit: Render **bills** a supplementary amount if a payment method exists; with none, Free services are suspended (bandwidth) or new builds disabled (build minutes) | "Free compute" is **not** an unconditional zero-cost guarantee. Check billing settings first |
| May suspend a Free service with uncommonly high **service-initiated** traffic (e.g. accessing an external database) | Keep DB traffic modest; no polling |
| No scaling beyond one instance, no persistent disk, no shell/SSH on Free, no one-off jobs, rollbacks only to the last two deploys | Migrations cannot be run from the Render shell; run them from a trusted machine/CI (D3-A) |
| Workspace plan upgrades do **not** remove Free-instance limits | n/a |
| Render's own Free Postgres expires after 30 days | Reason Neon is used instead |

### Neon Free ([neon.com/docs/introduction/plans](https://neon.com/docs/introduction/plans))

| Fact | Consequence |
|---|---|
| **1 GB** Postgres storage/project | Storage exhaustion makes inserts fail → 503 unknown/known paths must be tested |
| **100 CU-hours/project/month** compute; autoscaling up to 2 CU; **scale-to-zero after 5 min, cannot be disabled** | At 0.25 CU that's ~400 compute-hours; at 2 CU only ~50. **Cap max autoscaling at 0.25 CU** |
| **5 GB public egress/project/month** | History pages are small; fine unless abused |
| When CU-hours or egress run out: compute **suspended** until next billing period (or upgrade) | Expected outage mode; the app shows 503 |
| Storage cap hit: storage-increasing operations fail; data is not deleted | |
| 6-hour restore window, 1 manual snapshot, 1-day monitoring, community support, **no uptime SLA** | No backup promise |
| Free plan allowances and the limits can change | Re-verify |

## 2. Preflight checklist (deployment day)

- [ ] Owner authorization recorded (Gate G1)
- [ ] Render account eligible for Free web service (**unverified**, A1)
- [ ] Neon account eligible for Free project (**unverified**, A1)
- [ ] Render **Billing** page reviewed: payment method present? usage included amounts noted
- [ ] No other Free web services consuming the workspace's 750 h (or accepted)
- [ ] Neon plan = Free; max autoscaling set to **0.25 CU**
- [ ] Regions chosen: Render and Neon in the same/nearby region (D9)
- [ ] Re-read both free-tier pages for changes since 1 Nov 2026
- [ ] All High/Medium tests run locally with results recorded; unresolved items listed
- [ ] Secret scan clean; `.env*` ignored
- [ ] Decisions D1–D10 confirmed or defaults accepted

## 3. Local development setup (for README)

```bash
# 1. Local PostgreSQL (match Neon's major version; throwaway, local-only credentials)
docker run --name countcheck-pg -e POSTGRES_PASSWORD=<local-only-placeholder> \
  -e POSTGRES_DB=countcheck -p 5432:5432 -d postgres:<major>

# 2. Run with the local profile (no Secure cookie, plain local JDBC URL)
export DB_JDBC_URL="jdbc:postgresql://localhost:5432/countcheck"
export DB_USERNAME="postgres"
export DB_PASSWORD="<local-only-placeholder>"
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3. Tests (integration tests need a running Docker daemon for Testcontainers)
./mvnw test          # unit + slice tests
./mvnw verify        # also runs Testcontainers integration tests
```
README examples use **placeholders only**; never real hosts or credentials.

## 4. Docker image

Multi-stage, reproducible, non-root.

```dockerfile
# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Unit/slice tests only: Testcontainers ITs cannot run here (no Docker daemon). See D5.
RUN ./mvnw -B -q package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=60","-XX:+UseSerialGC","-XX:+ExitOnOutOfMemoryError","-jar","/app/app.jar"]
```

Requirements:
- Copy **only** the executable JAR; no `.env`, `.git`, `target/` leftovers (use `.dockerignore`).
- Integration tests are configured to run in the `verify` phase (Failsafe), so `package` runs only unit/slice tests.
- Confirm the runtime image has a CA bundle at the path used by `sslrootcert` (or mount one); test `verify-full` (T-OPS-04).
- Tune JVM flags by measurement on a 512 MB instance (T-OPS-07).
- `docker build` and `docker run` locally against local PostgreSQL before touching Render.

## 5. Neon setup

1. Create a Neon **Free** project (choose region per D9; note the PostgreSQL major version and match it in Testcontainers).
2. **Compute settings:** set autoscaling **maximum to 0.25 CU**. Scale-to-zero stays at 5 min (not changeable on Free).
3. **Roles:**
   - *Owner/migration role*: used only for migrations from a trusted machine/CI, via the **direct** endpoint. Never put it on Render (D3-A).
   - *Runtime role* `countcheck_app`: create **via SQL** (roles created through the console/API may be members of a privileged Neon role; verify in Neon's role docs). Grant only what is needed:
   ```sql
   CREATE ROLE countcheck_app LOGIN PASSWORD '<generated-secret>';
   GRANT CONNECT ON DATABASE <db> TO countcheck_app;
   GRANT USAGE ON SCHEMA public TO countcheck_app;
   GRANT SELECT, INSERT ON count_entries TO countcheck_app;   -- after V1 runs
   ALTER ROLE countcheck_app SET statement_timeout = '5s';
   ALTER ROLE countcheck_app SET lock_timeout = '3s';
   ALTER ROLE countcheck_app SET idle_in_transaction_session_timeout = '10s';
   ```
   Verify that `INSERT` alone suffices for the identity column (no sequence grant needed for `GENERATED … AS IDENTITY`); if not, grant the minimum needed.
4. **Connection strings:** copy Java/JDBC details from the console without exposing secrets. Runtime uses the **pooled** host (`-pooler`); migrations use the **direct** host.
5. **Migrate (D3-A):** from a trusted machine, run Flyway against the direct endpoint as the owner role, then apply the grants. Do **not** run a destructive reset.
6. Verify: connect as `countcheck_app`, confirm it can `SELECT`/`INSERT` and **cannot** `UPDATE`/`DELETE`/DDL (T-REPO-04); confirm `SHOW transaction_isolation` (T-CON-05).

## 6. Render setup

1. Push the reviewed repo to GitHub (`countcheck`; create new, never overwrite an existing repo; visibility per D1).
2. Render → **New → Web Service** → connect the repo → **Language: Docker**.
3. **Instance type: Free** (verify the selection before creating; no paid add-ons).
4. **Environment variables (server-side secrets only):** `DB_JDBC_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE=demo`, optional rate-limit values. Render supplies `PORT`; the app binds `0.0.0.0:$PORT`.
5. **Health check path:** leave unset, or use a static asset (e.g. `/css/app.css`) that bypasses the DB and the limiter. Never `/counts`.
6. **Auto-deploy:** Off or "after CI passes" (D10).
7. Deploy; watch the build log for secrets (there should be none) and for build-minute usage.
8. Do **not** add uptime pingers or keep-alive jobs.

## 7. Post-deploy smoke test (synthetic data only)

Record observed results in `05-TEST-PLAN.md` §4.

1. HTTPS loads; warning banner visible on every page.
2. Create an entry → 303 → detail with correct signed difference and UTC time.
3. Double-submit / back-and-resubmit with the same form → still one row.
4. History pagination and mismatch filter work.
5. CSRF: stale tab (wait past session timeout or restart) → friendly 403 → restore flow.
6. Observe and record: Render wake time, Neon cold-start time, first-request behaviour.
7. Oversize and chunked-oversize POST through the public URL (T-SEC-09).
8. Check Render plan = Free, Neon usage, no unexpected billing.
9. Confirm no secrets in logs.
10. Only after the above: report the live URL and results. **Do not include a real URL in the README before it exists.**

## 8. Operations and incident playbook

| Situation | Action |
|---|---|
| Spam flood / table growth | Tighten limiter via env; `REVOKE INSERT ON count_entries FROM countcheck_app` to go read-only; purge rows with the owner role (an operator action, not an app feature) |
| Real/personal data appears | Purge affected rows immediately via the owner role; consider read-only mode; do not keep copies |
| Neon compute/egress exhausted | Expect 503; wait for next period or stop. **Do not upgrade silently**; ask the owner |
| Render hours/bandwidth exhausted | Service suspended until next month; same rule |
| DB credentials suspected leaked | Rotate runtime role password in Neon, update Render env var, redeploy |
| Suspected abuse of the service itself | Suspend the Render service from its dashboard |
| Need to retire the demo | Delete Render service and Neon project; update README to say the demo is offline |

## 9. Rollback

Render keeps only the last two prior deploys for Free services. Because the database is append-only and migrations are forward-only (expand/contract), prefer redeploying a previous commit over data changes. Never roll back by dropping tables.
