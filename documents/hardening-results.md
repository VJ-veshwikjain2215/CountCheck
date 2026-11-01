# Hardening and Embarko preparation

Observed locally on 1 November 2026:

- 19 Java tests passed, with zero failures, errors or skips: RulesTest (4), TrafficLimitsTest (3), RequestGuardTest (6), StoreIntegrationTest (3), FailureSemanticsTest (3).
- The updated application passed 41 HTTP workflow checks and 11 account/pagination checks.
- PostgreSQL held-lock tests verified conflicting requests when the first transaction commits and when it rolls back.
- Commit acknowledgement fault injection threw after a real commit. Retrying the same entry recovered the single saved record.
- Filter tests rejected oversized unknown-length bodies, duplicate and unexpected fields, malformed form encoding and POST query strings.
- A PostgreSQL dump restored into a fresh disposable database. All four application table counts matched and both migrations were present; the source database was unchanged.
- The packaged application ran with a 96 MiB heap and explicit JVM limits. After HTTP tests, Windows/JDK 25 reported about 184.9 MiB working set and 214.4 MiB private memory. This does not prove capacity on Embarko/Linux or under sustained load.

Added per-address request and account-attempt limits, strict form field allowlists and history query validation. Forwarding headers remain untrusted; actual proxy client addressing must be checked in staging. Java sources were formatted, and individual product lookups now query by business and product ID.

## Hosting decision

The owner selected **Embarko**. Storage selection is pending: the application currently uses PostgreSQL and must have an external database before publication, unless the owner chooses a storage adaptation.

Embarko documents one process, 256 MB memory, persistent files under DATA_DIR, and no managed database or backups. A source-only archive and bounded Java start configuration are prepared. No CountCheck service has been created or deployed.

References checked: [Embarko deployment](https://embarko.ai/docs/deploy), [storage](https://embarko.ai/docs/data), and [Railpack configuration](https://railpack.com/config/file).

Browser visual/accessibility/storage QA, Docker execution, remote CI, production TLS/permissions, scheduled backups and deployment remain unverified. The local restore test does not establish production backup coverage.
