# Contributing to CountCheck

CountCheck compares manual inventory counts inside private business workspaces. Keep contributions focused on an observable problem and preserve workspace isolation, safe retries and accurate product claims.

## Before you start

- Search existing issues; open a focused improvement request before a large feature or new dependency.
- Use a fork and a branch for your change.
- Keep unrelated formatting, product changes and documentation changes out of a bug fix.
- The original demo documents are historical. Use current implementation, source and release documents when requirements conflict.

## Local workflow

1. Install Java 21+ and use the Maven wrapper in `code/`.
2. Set up a disposable PostgreSQL database. Read `code/README.md` or use `start-local.ps1` on Windows.
3. Export `DB_JDBC_URL`, `DB_USERNAME`, and `DB_PASSWORD` for your test database.
4. Run `./mvnw verify` from `code/` (`./mvnw.cmd verify` on Windows). With no database URL, integration tests are skipped; do not describe them as passed.
5. Run `node --check src/main/resources/static/js/app.js` when changing browser logic. Check Python helper syntax if relevant.
6. For UI changes, check the affected flow at desktop and phone widths with keyboard navigation. Include before/after screenshots and the viewports used.

The Windows helper stores disposable local PostgreSQL data under `code/target/local-postgres`. Do not run Maven `clean` while relying on it.

## Data and deployment safety

- Use synthetic inventory and accounts. Do not submit test messages or bulk traffic to the live site.
- Never run mutation/integration tests against production or reuse someone else's deployment identity.
- Never commit `.env` files, tokens, database dumps, recovery codes or local app data.
- Preserve every business-ID predicate and server-side authorization check.
- Counts remain observations. A retry under the same request ID must not create duplicates or change an earlier payload.
- Add a new forward migration for schema changes; do not rewrite an already-applied migration. Test it on an isolated database branch first.

## Pull requests

Describe the trigger, previous behavior, resulting behavior, and tests actually run. Link an issue when applicable. Mark unrun checks explicitly and document remaining limitations.

Documentation and implementation notes belong in `documents/`; application code and tooling belong in `code/`.

The owner has kept the current license status. Do not introduce or change licensing files as part of an unrelated contribution. Only contribute material you have the right to share.

## Protected main branch

Submit changes through a pull request. The GitHub Actions `verify` check must pass, the branch must be up to date with `main`, and review conversations must be resolved. These rules also apply to repository administrators. Force pushes and branch deletion are disabled. Like the owner's other protected repositories, no independent approval is mandatory, but stale approvals are dismissed when a change is pushed.

CI runs for documentation-only changes too, so the required check is always reported. Do not skip CI with a commit-message directive on a pull request.
