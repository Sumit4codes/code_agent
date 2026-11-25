# Bug Hunt Log

## Baseline

- Date: 2026-11-25
- Commit: `ca5fc85`
- `./test.sh`: 246 passed, 0 failed
- `./build.sh`: successful
- Working tree at baseline: untracked `AGENTS.md`, `docs/BUG_HUNT_PLAN.md`, `.claude/`

## Summary

| ID | Severity | Status | Area |
|---|---|---|---|
| BUG-001 | S2 | Fixed | Pending-change approval/rollback |
| BUG-002 | S1 | Fixed | Web fetch SSRF redirect bypass |

---

## BUG-001: Failed file operations are reported as successful change approvals/rejections

- Severity: S2
- Status: fixed
- Area: `core/agent` pending changes; `feature/chat` approval UI

### Files

- `core/agent/src/main/java/com/koda/dev/core/agent/PendingChangeManager.kt`
- `feature/chat/src/main/java/com/koda/dev/feature/chat/ChatViewModel.kt`
- `core/agent/src/test/java/com/koda/dev/core/agent/PendingChangeManagerTest.kt`

### Repro

1. Stage a pending `EDIT`, then make the rollback write fail (for example, disk full/read-only file).
2. Reject the change.
3. Observe that the database row becomes `REJECTED` and the UI removes the card, even though the file still contains the proposed content.

The same happens when:

- rejecting a `CREATE` and the delete returns `false`;
- approving a `DELETE` and the delete returns `false`.

### Expected

- A failed rollback/delete returns a failure.
- The change remains `PENDING` so the user can retry.
- The UI keeps the failed card visible and shows the error.

### Actual

- `PendingChangeManager.reject()` marked the change `REJECTED` in its catch block even when rollback threw.
- `deleteFile()` return values were ignored for `CREATE` rollback and `DELETE` approval.
- `ChatViewModel` ignored rejection results and cleared all cards on bulk operations.

### Root cause

The filesystem API reports failure both by throwing and by returning `false`. The manager handled only the throwing case, and the UI treated every operation as successful without inspecting the `Result`.

### Fix

- Check `deleteFile()` results and fail the operation when it returns `false`.
- Keep the change `PENDING` when rollback fails; only write `REJECTED` after a successful rollback.
- Require original content when rolling back `EDIT`/`DELETE`, and fail instead of silently skipping the restore.
- Return per-change results from `approveAll()` / `rejectAll()`.
- In `ChatViewModel`:
  - remove only changes whose operation succeeded;
  - keep failed changes visible;
  - surface the first failure with a `(+N more)` suffix.
- Single reject now checks the result before removing the card.

### Regression tests

Added to `PendingChangeManagerTest`:

- `reject edit keeps change pending when rollback write fails`
- `reject create keeps change pending when delete fails`
- `approve delete keeps change pending when delete fails`
- `reject fails when unbound and keeps change pending`

The first three failed before the fix and pass after it; the unbound case covers the removed false-success path.

### Verification

```text
./test.sh -c PendingChangeManagerTest  -> 11 passed
./test.sh -m core:agent                -> 75 passed
./test.sh -m feature:chat              -> 20 passed
./test.sh                              -> 250 passed, 0 failed
./build.sh                             -> BUILD SUCCESSFUL
```

### Residual risk

- The file write and the database status update are still not atomic. A process death between them can leave an applied file with a `PENDING` row. This is a separate recovery/transaction design issue.
- The bulk UI state behavior is covered indirectly by the manager API change but does not yet have a dedicated `ChatViewModel` test.

---

## BUG-002: Web fetch SSRF protection can be bypassed with an HTTP redirect

- Severity: S1
- Status: fixed
- Area: `core/data` web page fetching (agent `fetch_web_page` tool)

### Files

- `core/data/src/main/java/com/koda/dev/core/data/web/WebPageFetcher.kt`
- `core/data/src/test/java/com/koda/dev/core/data/web/WebPageFetcherTest.kt`

### Repro

1. Start a local HTTP server that returns `302 Found` with `Location: http://127.0.0.1:<port>/secret`.
2. Fetch the redirect URL through `DefaultWebPageFetcher`.
3. The fetcher returns the contents of the loopback-only `/secret` endpoint.

The test uses a `.invalid` hostname plus a custom test DNS entry to simulate a host that passes the initial check, then reaches the local redirect server.

### Expected

- Every redirect hop is validated against the SSRF rules, not just the first URL.
- Redirects to loopback, link-local, private, unspecified, multicast, IPv6 ULA, and CGNAT addresses are rejected.
- Redirect chains are bounded.

### Actual

- `validateSsrfSafety` ran once on the original URL.
- The client had `followRedirects(true)`, so OkHttp followed any subsequent `Location` without revalidation.
- `0.0.0.0`, IPv6 ULA (`fc00::/7`), and CGNAT (`100.64.0.0/10`) also passed the address check.

### Root cause

The SSRF check was applied only to the caller-supplied URL. Redirect handling was delegated to OkHttp, which has no knowledge of the application's address policy.

### Fix

- Disable automatic redirects for the fetch call and follow `301`, `302`, `303`, `307`, and `308` manually.
- Re-run protocol and SSRF validation on every redirect target.
- Resolve relative `Location` values against the current URL.
- Limit redirect chains to 10 hops.
- Expand blocked-address classification:
  - unspecified (`0.0.0.0`, `::`)
  - loopback, link-local, site-local
  - multicast
  - IPv4 `0.0.0.0/8`
  - CGNAT `100.64.0.0/10`
  - IPv6 unique-local `fc00::/7`

### Regression tests

Added to `WebPageFetcherTest`:

- `fetch blocks redirects to loopback addresses`
- `fetch follows redirects to allowed hosts`
- `fetch rejects unspecified addresses`
- `fetch rejects IPv6 unique local addresses`
- `fetch rejects carrier-grade NAT addresses`

The redirect-bypass and three address-classification tests failed before the fix. The safe-redirect test passed before and after and guards against breaking legitimate redirects.

### Verification

```text
./test.sh -c WebPageFetcherTest  -> 10 passed
./test.sh -m core:data           -> 23 passed
./test.sh                        -> 255 passed, 0 failed
./build.sh                       -> BUILD SUCCESSFUL
```

### Residual risk

- **DNS rebinding remains possible.** `validateSsrfSafety` resolves the host once, then OkHttp resolves it again when connecting. A custom `Dns` that validates the addresses actually used for the connection would close this gap.
- DNS resolution failures are still fail-open (the code lets OkHttp produce the connect error). This is intentional for transient resolver failures but means validation and connection can disagree.
- The final redirect URL is not shown in the fetched output; only the original URL is reported.
- Redirect-chain length is not covered by a dedicated test.
