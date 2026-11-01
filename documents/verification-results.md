# CountCheck verification

Observed local HTTP checks: 41 passed. Tested against PostgreSQL 18.6 on 1 November 2026.

- PASS: GET /register
- PASS: register A
- PASS: GET /login
- PASS: login A
- PASS: GET /register
- PASS: register B
- PASS: GET /login
- PASS: login B
- PASS: page renders /
- PASS: page renders /app
- PASS: page renders /products
- PASS: page renders /counts
- PASS: page renders /counts/new
- PASS: page renders /settings
- PASS: page renders /privacy
- PASS: CSRF rejects missing token
- PASS: GET /products
- PASS: add product
- PASS: GET /counts/new
- PASS: count commit returns 303
- PASS: detail renders escaped input and signed difference
- PASS: idempotent replay returns same record
- PASS: changed key payload returns conflict
- PASS: cross business detail denied
- PASS: cross business history isolated
- PASS: cross business product isolated
- PASS: GET /counts/new
- PASS: cross business product write denied
- PASS: invalid count -1
- PASS: invalid count 100001
- PASS: invalid count 007
- PASS: invalid count 1.5
- PASS: invalid count  5
- PASS: concurrent retries produce same record
- PASS: CSV export neutralizes formulas
- PASS: CSP is present
- PASS: oversize POST rejected
- PASS: JSON POST rejected
- PASS: malformed cursor rejected
- PASS: anonymous protected page requires login
- PASS: no public reset route

Test accounts and rows are synthetic. Browser layout verification is tracked separately.

## Account and pagination checks

- PASS: recovery code provided once
- PASS: history page capped at 20
- PASS: next page has remaining 2 rows
- PASS: mismatch filtering selects 11 differences
- PASS: account recovery succeeds
- PASS: recovery revokes previous session
- PASS: old recovery code cannot be reused
- PASS: new password signs in
- PASS: delete rejects wrong password
- PASS: password-confirmed deletion succeeds
- PASS: deleted workspace session is revoked


## Optimization release

22 Java tests passed on the hosted validation branch, plus 52 validation HTTP checks and 33 live HTTP checks. Catalog pagination/search, multi-batch CSV export, versioned static assets and cache headers were verified. See [optimization notes](optimization.md) for measured CSS transfer sizes and the scope of improvements.
