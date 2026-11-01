# CountCheck development record

## Implemented first release — 1 November 2026

The website is implemented in `../code/`. The first release serves a single owner per business workspace. It includes a public landing page, registration, password sign-in, private product catalog, count entry, immutable count detail, mismatch history, 20-row keyset pagination, CSV export, password changes, one-time recovery codes, and password-confirmed account/workspace deletion.

## Decisions made for this implementation

- Java 21 target, Spring Boot 4.1.2, server-rendered Thymeleaf, PostgreSQL and Flyway. The installed JDK 25 was used for local execution, compiling to Java 21. Boot compatibility was checked against https://docs.spring.io/spring-boot/system-requirements.html.
- JDBC replaces the earlier JPA proposal to keep authorization predicates and idempotency queries explicit. Every application business-data query carries the authenticated business ID; posted business IDs are not trusted.
- One owner per business for the initial release. Shared teams, invitations, roles and approval workflows are future scope rather than implied implemented features.
- Products have a SKU, unit, location and reference quantity. Each count records an independently entered expected quantity and observed quantity; counts never automatically modify inventory. Observations snapshot product details.
- Account recovery uses a high-entropy, one-time code shown at registration and rotated after recovery. The code is hashed with BCrypt. Password recovery/change revokes current registered sessions on this app instance.
- Count idempotency is scoped by business and UUID. Insert-on-conflict and separate payload comparison run within a transaction; 303 is returned after the transaction returns successfully.
- Count validation failures preserve fields and key. Uncertain database outcomes render a read-only retry page using the same payload and key. Optional sessionStorage recovery is scoped to the business and clears on matching detail view or explicit signout.
- Session state and coarse request limits are in memory: deploy only a single app instance until a shared session/rate-limiting design is introduced.
- Local PostgreSQL is a disposable cluster under `code/target/local-postgres`, bound to loopback port 55432. This is development infrastructure, separate from any future production database.

## Documentation status

`implementation.md` defines the new production direction. The nine earlier files in `project-plan/` are historical demo requirements; requirements conflicting with this development record and the revised direction are superseded. Do not reuse their demo-only warning or shared-data architecture in the official release.

## Verification

See [verification results](<verification-results.md>) for observed local tests and [publication work](<release-checklist.md>). Browser automation reported that its browser host was unavailable and native Chrome access was not approved; visual, keyboard, screen-reader and real browser storage recovery checks remain unverified. HTTP tests verify rendered HTML, not visual quality.
