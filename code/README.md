# CountCheck

A business inventory-counting website built with Java 21, Spring Boot 4.1.2, Thymeleaf, and PostgreSQL. The first release is live at https://countcheck.embarko.app, hosted on Embarko with Neon PostgreSQL in Singapore.

## Features

- Separate business accounts and private workspaces.
- Password sign-in, one-time recovery codes, session revocation on password reset, password changes, and password-confirmed workspace deletion.
- Product catalog with SKU, unit, location, and reference quantity.
- Immutable count observations, signed differences, idempotent submissions, history, mismatch filtering, keyset pagination, and CSV export (up to 10,000 records).
- Responsive pages, escaped output, CSRF protection, CSP, bounded form bodies, and a coarse global request limiter.

The first release supports one owner per workspace. Counts do not automatically adjust inventory. Recovery codes are displayed once and stored hashed; email-based verification/recovery and team invitations are not implemented.

## Run locally on this Windows workstation

Run `./start-local.ps1` in PowerShell. Requires Java 21+, PostgreSQL tools on PATH; a Maven wrapper is included. It creates a loopback-only development PostgreSQL cluster under `target/local-postgres`, database `countcheck`, port 55432, and starts the website at http://localhost:8080. This local cluster uses trust authentication for local development only; do not use it for deployment. Do not run `mvn clean` while relying on this disposable local data.

For another PostgreSQL instance set `DB_JDBC_URL`, `DB_USERNAME`, and `DB_PASSWORD`, then run `mvn spring-boot:run`. Docker users can set `DB_PASSWORD` and use `docker compose up -d` for a local database on port 5432.

## Checks

`./mvnw test` runs unit tests; PostgreSQL integration tests also run when `DB_JDBC_URL` is set. Use a disposable test database, since those tests create and delete synthetic workspaces. `./mvnw package` builds the executable JAR. `node --check src/main/resources/static/js/app.js` checks script syntax.

Optional HTTP checks require Python with `pip install -r requirements-test.txt`, the server running on port 8080, and a disposable local database: `python smoke_test.py` and `python lifecycle_test.py`. They create synthetic test accounts; the lifecycle suite deletes its workspace and the smoke suite leaves fixtures for inspection. The website itself has no Python runtime dependency.

## Deployment

Build the Docker image from this folder. Set `SPRING_PROFILES_ACTIVE=production`, `DB_JDBC_URL` (TLS-verifying PostgreSQL URL), `DB_USERNAME`, `DB_PASSWORD`, and the platform-supplied `PORT`. Production enables Secure session cookies and binds to 0.0.0.0. Use HTTPS. `/health` does not access the database.

Apply Flyway migrations from `src/main/resources/db/migration` through a trusted migration job before starting production. The production profile disables startup migrations by default; `RUN_MIGRATIONS=true` is an explicit elevated-role alternative. Use a separate runtime role with table DML permissions for the implemented account/workspace operations and required sequence permissions, without schema DDL. Test permissions in staging.

See `../documents/development.md`, `release-checklist.md`, and `verification-results.md` for decisions, observed checks, and remaining publication work. The live provider address is https://countcheck.embarko.app. Independent scheduled backups and availability guarantees are not configured.
