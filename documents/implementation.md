# CountCheck implementation plan

Latest progress: [hardening and verification](<hardening-results.md>) and [Embarko launch preparation](<embarko-launch.md>). The first release is live at https://countcheck.embarko.app with external Neon PostgreSQL. See the release documents for completed checks and operational follow-up.

**Status:** First release published; production follow-up tracked · **Updated:** 1 November 2026

## Direction and relationship to earlier documents

CountCheck is intended to become an **official website and published product**, not a public synthetic-data demo. Its starting idea is inventory count reconciliation: record an item's expected and physical quantities and show the difference. The product should be designed to support real users and real business data once its requirements, security, and operations are ready.

The documents in [project-plan](<project-plan/00-README.md>) were written for a public, synthetic-only demo. They provide useful starting material on count validation, idempotent writes, pagination, security, and testing, but their demo-only scope, warning, shared data model, free-tier restriction, and release assumptions **do not define the production product**. Reconcile and update those documents before treating them as implementation requirements. Do not represent unbuilt capabilities as completed.

Code belongs in `CountCheck/code/`; designs, changes, implementation notes, and other documentation belong in `CountCheck/documents/`.

## Product decisions to define

- [ ] Identify intended users, business types, initial geography, and the problem the first release solves.
- [ ] Define accounts and organization membership, roles and permissions, invitations, and isolation of each organization's data.
- [ ] Define the inventory model: locations, products, units, who records counts, correction/approval workflows, history, and any import/export requirements.
- [ ] Decide whether integrations, barcode scanning, notifications, analytics, and mobile functionality belong in the first release or later; the earlier demo exclusions are not automatic product decisions.
- [ ] Define privacy expectations, data ownership, retention, deletion, audit history, accessibility, and applicable legal obligations with the owner.
- [ ] Set availability, backup and recovery targets, expected traffic, support responsibilities, hosting budget, and launch criteria.
- [ ] Decide the domain, branding, deployment provider, and current technology versions. Java 21, Spring Boot, Thymeleaf, PostgreSQL, and Docker are an initial proposal inherited from the earlier plan, subject to product requirements and technical review.

## Implementation milestones

### 1. Product and technical foundation

- [ ] Rewrite the PRD around real users, use cases, workflows, acceptance criteria, and a clearly bounded first release.
- [ ] Define the data and access model, organization boundaries, authentication and authorization flows, and key security threats.
- [ ] Produce an architecture and deployment design, choosing technologies and hosting against the product requirements and budget.
- [ ] Document a phased backlog, dependencies, risks, and decisions; update the earlier planning documents or clearly supersede them.

**Exit:** a coherent first-release scope and architecture exist, with unresolved decisions visible.

### 2. Local project foundation

- [ ] Build the application foundation in `code/`, including version-controlled configuration, local development instructions, dependency management, and a suitable database migration system.
- [ ] Set up separate local, test, staging, and production configurations; keep credentials outside the repository.
- [ ] Establish automated checks and a repeatable local setup, including a real database for integration tests.

**Exit:** a clean local setup starts successfully and baseline checks produce recorded results.

### 3. Accounts and business data boundaries

- [ ] Implement the agreed authentication, account recovery, session management, organization membership, and roles.
- [ ] Enforce authorization and organization isolation on every relevant read and write, including background work and exports if included.
- [ ] Design audit and change history around the actual product requirements and data-retention policy.
- [ ] Test unauthorized access, cross-organization isolation, session handling, and common abuse cases.

**Exit:** users can access only data and actions they are authorized to use; tests verify the boundary.

### 4. Core inventory counting workflows

- [ ] Implement the approved product, location, and count data models and their migrations.
- [ ] Build the count-entry and review flow, calculate `physical − recorded` server-side, and clearly explain its meaning.
- [ ] Implement the approved history, filtering, pagination, and correction or approval behavior.
- [ ] Preserve safe retry and idempotency behavior where needed; test concurrent submissions and uncertain save outcomes against PostgreSQL.
- [ ] Add other first-release capabilities only after they are specified and prioritized.

**Exit:** first-release workflows satisfy their acceptance criteria with accurate audit and authorization behavior.

### 5. Experience and quality

- [ ] Design responsive, accessible pages and clear loading, empty, error, and recovery states.
- [ ] Run unit, integration, end-to-end, accessibility, and security tests appropriate to the final scope; record actual results.
- [ ] Review performance, dependencies, input validation, request limits, error handling, and sensitive-data exposure.
- [ ] Test backups and restoration and document support and incident procedures.

**Exit:** launch risks and test results are reviewed and outstanding issues are recorded.

### 6. Staging and publication

- [ ] Select hosting, database, domain, pricing, and operational ownership with the user; verify current provider limits and costs before provisioning.
- [ ] Prepare secure production configuration, HTTPS, monitoring, backup schedules, restore procedures, and rollback procedures.
- [ ] Deploy to a staging environment and test the complete workflow using appropriate test data.
- [ ] Address launch blockers, publish the official website when authorized, and verify its live workflows and operational controls.
- [ ] Document the published address, known limitations, maintenance responsibilities, and observed outcomes.

**Exit:** the live website is verified against agreed launch criteria and handed over with operational documentation.

## Tracking

- Record decisions, designs, implementation changes, and test evidence in `documents/`; log workspace activity in the shared Codex log without secrets.
- Report tests as passed only after they actually run. Track unimplemented capabilities and unresolved decisions explicitly.
- Do not provision paid services or publish until the required choices, cost, and release authorization are clear.

## Implementation progress — 1 November 2026

A working local first release has been built in `../code/`. See [development decisions](<development.md>), [observed verification](<verification-results.md>), and [release checklist](<release-checklist.md>) for concrete completed scope and remaining publication work. The broad milestone checkboxes above are retained for the full production launch and should not be interpreted as tests or deployment already completed.


## Published release progress

CountCheck has been deployed to Embarko with the hosted Neon production schema and a restricted runtime database login. See [live verification](<live-verification.md>) and [release execution](<embarko-launch.md>) for observed results and [remaining operational work](<release-checklist.md>). This update supersedes the earlier pending-database statements above.
