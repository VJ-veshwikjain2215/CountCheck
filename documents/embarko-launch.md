# Embarko launch preparation

CountCheck is live at https://countcheck.embarko.app in the owner’s Embarko account, backed by Neon PostgreSQL in Singapore. This document retains the preparation steps and records their execution below.

## Database connection

In `../code`, copy `.env.example` to `.env.local` and enter the provider's JDBC URL, username and password. Keep the password out of chat and documentation. The local file is ignored and excluded from uploads. Use a provider URL that verifies TLS; the example uses the Java trust store with hostname verification.

Run `check-database.ps1` to check the connection without changing any tables. The check prints no credentials. Do not run application integration or HTTP tests against a customer database: they are for disposable test environments.

## Release sequence

1. Verify the hosted database, migration strategy and runtime-role permissions. Apply the two committed Flyway migrations using a trusted migration role against a direct connection.
2. Package with `python package-embarko.py`. Inspect the archive: it must contain source/configuration only, with no Dockerfile, local data, credentials or build outputs.
3. Create a new CountCheck app through Embarko, using the existing local Embarko credential if authorized and valid. Persist the returned app ID. Do not copy another project's app ID.
4. Set DB_JDBC_URL, DB_USERNAME and DB_PASSWORD as server-side settings through Embarko's environment API or its App setup page. Keep startup migrations disabled after the schema is prepared. The Railpack configuration selects the production profile, public bind address, Secure cookies and bounded Java memory.
5. Deploy, verify HTTPS and the complete synthetic account/product/count/history/recovery flow, and test persistence after redeployment.
6. Confirm production backup scheduling and restoration, publish operator/support/retention information, and resolve the remaining release checklist before customer onboarding.

The database credentials are configured server-side and the deployment is verified. Keep all local credential files private.

References: [Embarko quickstart](https://embarko.ai/docs/quickstart.md), [deployment](https://embarko.ai/docs/deploy), [server settings](https://embarko.ai/docs/env-vars), [storage limitations](https://embarko.ai/docs/data).

## Neon connection established — 1 November 2026

The project is linked to the owner's specified Neon project and production branch. Postgres connection details were fetched into ignored local files and converted into CountCheck JDBC settings. Read-only preflight passed with verified TLS, a negative hostname-verifier test, and READ COMMITTED isolation. No application tables, migrations or test fixtures were written to production. Test the committed migrations on an isolated Neon branch before production rollout; retain a restricted runtime role for the deployed application.


## Release execution — 1 November 2026

- App created in the existing Embarko account: https://countcheck.embarko.app (claimed/permanent app ID cmvxws9im0dm8ygo8fx2hzrh9).
- Applied Flyway V1/V2 first to the expiring validation branch, ran the Java suite, then applied them to production.
- Created countcheck_runtime separately on validation and production. Verified its reads and DML permissions while denying schema DDL, count updates and migration-history access.
- Stored production runtime credentials as server-side settings and disabled runtime migrations. Owner credentials remain local and were not included in the source upload.
- Fixed a pooler-incompatible startup options parameter and enforced statement timeouts through JdbcTemplate and role defaults.
- Explicitly applied the saved environment settings after initial live registration failures. The complete live smoke flow subsequently passed 30 checks, and its synthetic workspaces were removed.
- The database recovery history reports 21600 seconds (six hours); separately scheduled backups and browser visual/accessibility QA remain pending.
- Final redeployment persistence verification is tracked in live-verification.md.
