# Live CountCheck verification

URL: https://countcheck.embarko.app

- PASS: public HTTPS landing page
- PASS: content-versioned static assets
- PASS: static assets have long public caching
- PASS: page renders /register
- PASS: workspace registration A
- PASS: page renders /login
- PASS: workspace login A
- PASS: page renders /register
- PASS: workspace registration B
- PASS: page renders /login
- PASS: workspace login B
- PASS: private pages remain no-store
- PASS: authenticated page /app
- PASS: authenticated page /products
- PASS: authenticated page /counts
- PASS: authenticated page /counts/new
- PASS: authenticated page /settings
- PASS: authenticated page /privacy
- PASS: page renders /products
- PASS: product saved
- PASS: page renders /counts/new
- PASS: save returns committed detail redirect
- PASS: detail shows signed difference
- PASS: same-key retry returns same record
- PASS: other workspace cannot read record
- PASS: other workspace history isolated
- PASS: history and filter show observation
- PASS: CSV export works
- PASS: missing CSRF is rejected
- PASS: CSP header present
- PASS: secure session cookie
- PASS: page renders /settings
- PASS: page renders /settings

Synthetic test workspace cleanup: completed
