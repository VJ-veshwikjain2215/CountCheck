# CountCheck

[![Verify CountCheck](https://github.com/VJ-veshwikjain2215/CountCheck/actions/workflows/ci.yml/badge.svg)](https://github.com/VJ-veshwikjain2215/CountCheck/actions/workflows/ci.yml)

A website for comparing recorded inventory with physical counts, keeping private business workspaces and a timestamped observation history.

**Live website:** https://countcheck.embarko.app

[Optimization notes](documents/optimization.md) · [Application setup](code/README.md)

## What it does

- Business registration, sign-in, recovery codes, password changes and workspace deletion.
- Product catalog with SKU, unit, location, search and bounded pagination.
- Count entry, signed differences, immutable observations and safe duplicate submission handling.
- Filtered history and CSV export, with access scoped to the signed-in business.

## Project structure

- [`code/`](code/): Java 21 / Spring Boot application, Thymeleaf interface, PostgreSQL migrations, tests and deployment helpers.
- [`documents/`](documents/): requirements, implementation decisions, verification results and operational notes. The original demo brief is retained as historical context; current implementation and release documents supersede conflicting assumptions.

## Run and test

See the [application README](code/README.md) for setup. From `code/`, use `./mvnw test` for unit checks and configure a **disposable PostgreSQL database** with `DB_JDBC_URL`, `DB_USERNAME` and `DB_PASSWORD` to run the integration tests. `./mvnw package` builds the application. On the configured Windows workstation, `./start-local.ps1` starts a local development database and website.

The production website runs on Embarko with Neon PostgreSQL. Credentials, local databases, deployment identity and generated build files are excluded from this repository. Configure your own account and environment when deploying a fork.

## Release status

See [verification results](documents/verification-results.md), [live checks](documents/live-verification.md), and the [release checklist](documents/release-checklist.md). Browser visual/accessibility QA, independently scheduled backups and monitoring remain operational follow-up. The app does not automatically adjust stock.
