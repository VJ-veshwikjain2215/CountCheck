# 01. Product Requirements Document (PRD)

## 1. Problem and evidence

Inventory records can differ from physical stock. DeHoratius & Raman (*Management Science*, 2008) examined nearly 370,000 inventory records across 37 stores of **one US retailer** and found **65% inaccurate** ([DOI 10.1287/mnsc.1070.0789](https://pubsonline.informs.org/doi/10.1287/mnsc.1070.0789); figure confirmed against the publisher abstract on 1 Nov 2026).

**What this evidence supports:** the problem exists in that sample.
**What it does not support:** current prevalence among small shops, prevalence in India, or that this demo reduces the problem. No claim about market demand is made.

## 2. Purpose of the demo

Demonstrate, in a small publicly viewable app, the mechanics of **manual stock-count comparison**: enter two independent numbers, see the signed gap, keep a timestamped history. The demo is a technical and UX demonstration, not a validated product.

## 3. Users and context

| Persona | Need | Note |
|---|---|---|
| Curious visitor / reviewer | Try the flow with fictional items | Primary audience of a public demo |
| Project owner | Show a careful, honest, small system | Success = clarity and robustness, not scale |

Not targeted: real shop owners entering real stock (explicitly unsafe on a public shared demo).

## 4. Scope

### In scope (four features)

| # | Feature | Summary |
|---|---|---|
| **F1** | Enter a count | Item name, recorded count, physical count |
| **F2** | Compare | Signed difference display; mismatch-only history filter |
| **F3** | History | Timestamped, append-only, newest first, 20 per page, detail page |
| **F4** | Validate and warn | Server-side validation; conspicuous public-demo warning on every page |

### Explicitly out of scope

Login/accounts · real shop or personal data · POS integration · barcode scanning · live inventory · automatic stock correction · analytics · notifications · public reset · production readiness · edit/delete/export/admin endpoints.

### Not verified (must not be claimed)

User demand · willingness to enter counts · time savings · free-tier eligibility for the owner's accounts · real deploy behaviour · the 14-hour estimate.

## 5. Requirements

### 5.1 Functional

| ID | Requirement | Feature |
|---|---|---|
| FR-1.1 | User can submit item name, recorded count, physical count via a form | F1 |
| FR-1.2 | Each new form carries a server-generated UUID v4 request ID, created per form load | F1 |
| FR-1.3 | A valid submission is saved once; response is **303 See Other** to the saved record's detail page, issued only after commit | F1 |
| FR-2.1 | Difference = physical − recorded, computed server-side from stored integers; displayed `+3`, `−3`, or `0` | F2 |
| FR-2.2 | History offers an optional **mismatch-only** filter (difference ≠ 0, including negatives) applied *before* the page limit | F2 |
| FR-3.1 | Records are append-only: the application exposes no update/delete/reset | F3 |
| FR-3.2 | Each record has a database-generated UTC creation instant, rendered ISO 8601 with `Z` | F3 |
| FR-3.3 | History is ordered newest first by `(created_at DESC, id DESC)`, 20 per page, keyset paged | F3 |
| FR-3.4 | `GET /counts/{id}` shows an immutable detail view with values, signed difference, UTC time, link back | F3 |
| FR-4.1 | Server validates every field and query parameter independently of browser validation | F4 |
| FR-4.2 | The public-demo warning is shown on **every** page, including errors | F4 |
| FR-4.3 | Errors never leak SQL, stack traces, hostnames, connection strings, or secrets | F4 |
| FR-5.1 | Resubmitting the same request ID with an identical normalized payload returns the original record, creating no new row | F1 |
| FR-5.2 | Reusing a request ID with a *different* normalized payload returns **409** and writes nothing | F1 |
| FR-5.3 | On slow/failed saves, the entry is shown again with the **same** request ID and fields, stating whether the outcome is *known* or *unknown* | F1 |
| FR-5.4 | A small same-origin script offers to restore a pending entry (same UUID) after session loss; never auto-submits | F1 |

### 5.2 Non-functional

| ID | Requirement |
|---|---|
| NFR-S1 | Spring Security with explicit `SecurityFilterChain`; CSRF on for POST; form login, HTTP Basic, logout disabled; unexpected routes/methods denied |
| NFR-S2 | Output escaped (`th:text` only); strict same-origin CSP; no inline scripts |
| NFR-S3 | Global in-memory rate limit (e.g. 120 dynamic req/min, ≤20 POST/min) → 429 + `Retry-After`; POST body ≤ 4 KiB → 413 before form parsing |
| NFR-S4 | Secrets only in server-side env vars; runtime DB role is least-privilege where feasible |
| NFR-R1 | Reads that fail return 503, never an empty-success page |
| NFR-R2 | Bounded connection pool with short, explicit acquisition timeout |
| NFR-O1 | Runs on Render Free + Neon Free with no paid add-ons; no paid fallback |
| NFR-A1 | Labels associated with inputs; keyboard operable; warning visible at mobile width; understandable errors |
| NFR-D1 | README documents setup, config names (placeholders only), limitations, and *observed* test results |

## 6. The public-demo warning (verbatim)

> **PUBLIC SHARED DEMO: synthetic data only. Anyone can view or add entries. No real business or personal data. Counts are manual snapshots; history may change or disappear.**

Rendered in a shared Thymeleaf fragment included by every template (`counts`, `count-form`, `count-detail`, `error`). Release is blocked if any page lacks it.

## 7. User flow

1. **GET** `/counts` (shared history). Read warning; click **New count**.
2. **GET** `/counts/new`. Server creates request UUID and a session-bound CSRF token. User enters a fictional item and the two independent counts.
3. **POST** `/counts`. Server validates; if valid, saves **once** and redirects (303, after commit) to `/counts/{id}`. Detail shows values, signed difference, UTC time, and a link back.
4. **History** shows newest first, 20 per page, with an optional mismatch-only toggle. Other people's additions appear on refresh.
5. **Slow/failed save:** the same form returns with the **same** request ID and fields and says whether the outcome is *known* (e.g. validation failed, CSRF rejected, pool exhausted before any statement) or *unknown* (anything after the transaction began without a definitive response). It never says "not saved" when commit may have succeeded.

### Pending-retry recovery

A tiny same-origin script (`static/js/pending-count.js`) writes the submitted synthetic values and request UUID (never the CSRF token) to `sessionStorage` before POST. On a fresh form it **offers** to restore the entry, including the same UUID; the user reviews and explicitly submits with the new CSRF token. The detail page exposes the saved UUID in a non-secret `data-request-id` attribute; the script clears the pending entry only on an **exact** match. The UUID is not an access credential. If JavaScript/storage is unavailable, same-page retry still keeps the key; after tab close or session loss, key recovery is not guaranteed: *check shared history before a new submission*.

**Design note DN-02 (proposed, decision D7):** on an unknown outcome, render the pending entry read-only with a "Retry same entry" button, so users cannot edit the payload under a key that may already be committed (which would yield a 409).

## 8. Field definitions

| Field | Rule |
|---|---|
| **Item name** | Required Unicode text. `String.strip()`, then 1–80 **code points**, no control characters (also reject NUL and unpaired surrogates). Preserve internal spaces and case. Names are not unique; no item master. Reject (do not truncate) on violation. Render as text, never HTML. |
| **Recorded count / Physical count** | Required plain base-10 non-negative integer **strings**, 0–100000 inclusive. Reject signs, decimals, exponents, empty/whitespace-only, oversized, missing. Empty ≠ 0. Proposed strictness (D8): ASCII digits only, no whitespace, no leading zeros except `0`. Counts are unit quantities, not money or weight. |
| **Difference** | `physical − recorded`; range −100000…+100000. Never accepted from the client. Physical `0` is a valid stock-out observation. |
| **Request ID** | Hidden UUID v4, server-created per form, strictly validated. Idempotency key, **not** an authentication secret. |
| **Timestamp** | Database-generated `TIMESTAMPTZ`; UTC; ISO 8601 with `Z`. Not a claim of real-world counting time. |

## 9. Acceptance criteria (per feature)

**F1: Enter a count**
- Valid submission → exactly one row, 303 to detail, detail shows the submitted values.
- Same request ID + same payload (double-click, retry, lost response) → still one row, same record ID.
- Same request ID + different payload → 409, no change.
- Missing/stale CSRF → 403 with recovery instructions, no row.

**F2: Compare**
- (5,8) → `+3`; (8,5) → `−3`; (0,0) → `0`.
- Mismatch-only shows only non-zero rows (including negatives); filter applies before the 20-row limit.

**F3: History**
- Newest first; 20 per page; `Next` only if a 21st row exists; no duplicates across pages in a static dataset; invalid/half cursors → friendly 400.
- No edit/delete/reset route exists (absent or denied).

**F4: Validate and warn**
- Every boundary in §8 behaves as specified; invalid input yields 400 (or 413 for oversize) with zero rows inserted.
- Warning present on all pages; no real-data claims; no login.

## 10. Success criteria for the *demo*

Not adoption. Success is: the four features work on a clean build; High and Medium tests pass or are honestly reported as unresolved; documentation states every limitation; no secrets or real data in the repo; free-tier plan selection and overage settings were verified; deployment claims are made only after being observed.

## 11. Assumptions and open questions

| # | Assumption | Status |
|---|---|---|
| A1 | Owner's Render and Neon accounts are eligible for Free plans | **Unverified** |
| A2 | Neon cold start + Hikari timeouts allow a friendly first request | **Unverified**: test T-OPS-02 |
| A3 | Render's proxy passes chunked and oversize bodies in a way the app-level cap can observe | **Unverified**: test T-SEC-09 |
| A4 | CA bundle in the runtime image allows `sslmode=verify-full` to Neon | **Unverified**: test T-OPS-04 |
| A5 | 14 h estimate | Optimistic; see `07-PROJECT-PLAN.md` §3 |
