# 08. References, Research Log, and Claims Ledger

Research performed 1 Nov 2026. "Primary" = the provider's own documentation. "Secondary" = third-party summaries, used only to orient and to be re-checked.

## 1. Sources

### Problem evidence
| Source | Type | Used for |
|---|---|---|
| DeHoratius & Raman (2008), *Inventory Record Inaccuracy: An Empirical Analysis*, Management Science 54(4):627–641. https://pubsonline.informs.org/doi/10.1287/mnsc.1070.0789 | Primary (publisher abstract) | ~370,000 records, 37 stores, one retailer, 65% inaccurate |

### Hosting and database
| Source | Type | Used for |
|---|---|---|
| Render, Deploy for Free: https://render.com/docs/free | Primary | Spin-down, 750 hours, bandwidth/build metering, service-initiated traffic threshold, Free Postgres 30-day expiry |
| Render, web services / port binding: https://render.com/docs/web-services | Primary (from brief; not re-fetched) | `PORT`, bind `0.0.0.0` |
| Render, FAQ (Docker for Java): https://render.com/docs/faq | Primary (from brief; not re-fetched) | Docker deployment |
| Neon plans: https://neon.com/docs/introduction/plans (also https://neon.com/pricing) | Primary | Free-plan limits, scale-to-zero, suspension behaviour |
| Neon connection pooling: https://neon.com/docs/connect/connection-pooling | Primary | PgBouncer transaction mode, pooled vs direct, unsupported features |
| Neon Java guide: https://neon.com/docs/guides/java | Primary (from brief; not re-fetched) | JDBC connection details |

### Framework and security
| Source | Type | Used for |
|---|---|---|
| Spring Security reference, CSRF (7.1.2): https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html | Primary | Session-stored token default, deferred loading, BREACH protection, Thymeleaf integration, `AccessDeniedHandler`, session-timeout caveat |
| Spring Boot 4.0 Migration Guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide | Primary | Starter renames, Flyway starter |
| "Modularizing Spring Boot" (spring.io blog, 28 Oct 2025): https://spring.io/blog/2025/10/28/modularizing-spring-boot | Primary | `webmvc`, `flyway`, new test starters |
| Boot version/EOL summaries (herodevs.com Apr 2026; issoh.co.jp Oct 2026; marcobehler.com May 2026) | **Secondary** | Boot 4.0 → 4.1 line, support-end dates, 4.2 expected Nov 2026 |
| PostgreSQL, transaction isolation: https://www.postgresql.org/docs/current/transaction-iso.html | Primary (from brief; not re-fetched) | READ COMMITTED per-statement snapshots |
| PostgreSQL, INSERT / ON CONFLICT: https://www.postgresql.org/docs/current/sql-insert.html | Primary (from brief; not re-fetched) | `DO NOTHING … RETURNING` semantics |

## 2. Research log: what was checked and what it showed

| # | Question | Finding | Effect on plan |
|---|---|---|---|
| 1 | Do Render Free limits in the brief still hold? | Yes: 15-min spin-down, ~1 min wake, 750 h/workspace, ephemeral FS, bandwidth/build metering with billing if a payment method exists. **Additional:** service-initiated-traffic suspension rule; Free Postgres expires in 30 days | R-08, R-09; runbook §1 |
| 2 | Do Neon Free limits still hold? | Yes: 1 GB, 100 CU-h, 5 GB egress, scale-to-zero 5 min (not disableable), suspension on exhaustion, no uptime SLA. **Additional:** autoscaling max can be capped; 6-h restore window; 1 manual snapshot | Cap at 0.25 CU; no-backup stance reinforced |
| 3 | Is the pooled endpoint safe for this design? | PgBouncer **transaction mode**: `SET`, session advisory locks, SQL `PREPARE` unsupported; protocol-level prepared statements supported; migrations should use direct | ADR-004, ADR-007, ADR-012; R-11, R-12 |
| 4 | Which Spring Boot line? | Boot 4.x current (4.1 GA mid-2026); 3.5 OSS ended 30 Jun 2026; 4.0 ends 31 Dec 2026. Security 7.1.2 docs current | ADR-001; D2 |
| 5 | Any Boot 4 changes that break the brief's dependency list? | Yes: `webmvc` starter; **Flyway needs its own starter**; new test starters; `@MockBean` removed | `03` §2 |
| 6 | CSRF behaviour with Thymeleaf and sessions | Thymeleaf auto-inserts token; token lives in session by default; expiry leaves nothing to validate; custom `AccessDeniedHandler` is the supported hook | ADR-008; T-SEC-04 |
| 7 | Does the cited study say what the brief says? | Yes (abstract + secondary citations agree) | `01-PRD` §1 |
| 8 | Can tests run inside a Docker image build? | Not with Testcontainers (no Docker daemon in build stage); reasoning, not a provider doc | D5; R-18 |

## 3. Claims ledger

### Verified on 1 Nov 2026 (against primary docs, at that date)
- Render Free spin-down, wake time, 750 h/workspace, ephemeral filesystem, bandwidth/build overage behaviour
- Neon Free storage/compute/egress limits, scale-to-zero, suspension behaviour, restore/snapshot limits, no SLA on Free
- Neon pooler is PgBouncer transaction mode with the listed unsupported features
- Spring Security 7.1.2 CSRF defaults (session repository, deferred token, BREACH handling, Thymeleaf integration)
- Boot 4 starter renames incl. `spring-boot-starter-webmvc` and `spring-boot-starter-flyway`
- DeHoratius & Raman headline figures (65%, ~370,000 records, 37 stores, one retailer)

### Reported by secondary sources only (re-check before relying)
- Exact current Boot patch numbers (4.1.x), 4.2 GA timing, 4.0 support end date
- Exact Testcontainers 2.x artifact names

### From the brief, **not verified** here
- PostgreSQL behaviour claims (second statement sees the committed winner under READ COMMITTED): standard documented behaviour but **must be proven by T-CON-03/04 on real PostgreSQL**
- Render proxy behaviour for oversize/chunked bodies
- Neon cold-start duration and TLS `verify-full` with the runtime image's CA bundle
- That a runtime role with only `INSERT` can use an identity column (expected, to be confirmed)
- That roles created through the Neon console are privileged while SQL-created roles are not (confirm in Neon's role docs)

### Explicitly **unverifiable / unproven** (never claim)
- Market demand; willingness to input counts; time savings; accuracy improvement
- Free-tier eligibility for the owner's accounts
- Actual deploy behaviour and the 14-hour estimate
- That this demo reduces inventory record inaccuracy
- Durability, backups, availability, tamper-resistance of stored entries

## 4. Re-verification schedule

Re-read the Render and Neon free-tier pages and the Boot support page: (a) at G0, (b) on deployment day, (c) before reporting results. Record the date and any differences in the README.
