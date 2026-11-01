# CountCheck publication checklist

## Built locally

- [x] Public landing and authentication pages.
- [x] Private business workspaces, product catalog, count workflow and export.
- [x] Database migrations, immutable count observations and safe retries.
- [x] Recovery codes, password changes, session revocation and workspace deletion.
- [x] Unit, PostgreSQL integration and HTTP workflow checks with observed results.
- [x] Dockerfile, CI definition and development setup instructions.

## Remaining before official customer launch

- [x] Owner selected Embarko hosting and an external PostgreSQL database.
- [x] Connect Neon PostgreSQL in Singapore and create the CountCheck app in the existing Embarko account. Provider URL selected; no paid resources were added.
- [x] Configure HTTPS, verified database TLS, server-side settings and separate migration/runtime privileges; validate against an isolated hosted branch.
- [x] Publish factual operator/contact, hosting, six-hour database recovery history, deletion and current backup limitations on the privacy page.
- [ ] Configure monitored backups; test restoration and rollback. Local development data does not constitute a backup strategy.
- [ ] Run browser checks at desktop/mobile widths, keyboard and assistive-technology checks, and pending-entry recovery with unavailable storage/session expiry.
- [ ] Validate production proxy behavior, chunked form limits, cold starts, memory use, and rate limits. Configure suitable network/edge abuse controls.
- [x] Verify lost commit acknowledgement and conflicting inserts when the first transaction commits or rolls back, using real local PostgreSQL.
- [x] Restore a local PostgreSQL dump into a new disposable database and verify application table counts and migrations.
- [ ] Build/run the Docker image and execute CI on the chosen remote repository. Docker's engine was unavailable locally; those paths are prepared but not reported as tested.
- [x] Verify restricted runtime database permissions.
- [ ] Configure live alerting and establish support/on-call procedures.
- [ ] Reassess one-owner scope and recovery-code UX before inviting customers. Email verification/recovery, integrations, teams and multi-instance session coordination are not part of this first release.
- [x] Publish https://countcheck.embarko.app and pass 30 live HTTP workflow/security checks.
- [x] Verify a saved product/count survives redeployment and fresh sign-in; remove the synthetic persistence workspace.
