# Security policy

## Report privately

Use [GitHub private vulnerability reporting](https://github.com/VJ-veshwikjain2215/CountCheck/security/advisories/new). Include the affected route or file, steps using synthetic data, impact, and a suggested fix if available.

Do not publish exploit details, real business records, passwords, connection strings, recovery codes or tokens in an issue, pull request or screenshot. Do not access another user's workspace to collect evidence.

There is no guaranteed response deadline or bug bounty.

## Maintained version and scope

The current `main` branch is maintained. This policy covers CountCheck's application and deployment helpers. Embarko and Neon infrastructure issues should also be reported through the relevant provider's security channel.

## Security boundaries

- Authorization is enforced by the application on business-scoped reads and writes.
- Passwords and recovery codes are stored as hashes. A recovery code can reset access; treat it as a credential.
- Runtime database credentials are server-side. The runtime role lacks schema DDL and count-update privileges; account deletion requires the documented delete permissions.
- CSRF, same-origin CSP, output escaping, body limits and coarse in-memory request limits are implemented. They are not a substitute for operational monitoring or provider controls.
- Sessions and traffic limits are in memory; multi-instance deployment needs coordinated session/limit handling.
- Independently scheduled backups, alerting and browser accessibility checks remain follow-up work. Do not claim this is an audited security product.

Use an isolated database and deployment for investigation. Do not run automated security scans, denial-of-service tests or destructive tests against the public website without arranging them with the owner.
