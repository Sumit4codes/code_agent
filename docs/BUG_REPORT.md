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
| BUG-003 | S3 | Fixed | Sync-vault PBKDF2 CPU denial of service |
| BUG-004 | S2 | Fixed | Duplicate persisted user message on task start |
| BUG-005 | S3 | Fixed | Deferred message write can land in the wrong session |
| BUG-006 | S1 | Fixed | Commands run on the Android host shell when PRoot is missing |

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

---

## BUG-003: Untrusted sync-vault iteration count allows PBKDF2 CPU denial of service

- Severity: S3
- Status: fixed
- Area: `core/data` encrypted sync vault import/restore
- Files:
  - `core/data/src/main/java/com/koda/dev/core/data/sync/VaultCrypto.kt`
  - `core/data/src/test/java/com/koda/dev/core/data/sync/VaultCryptoTest.kt`

### Repro

1. Export an encrypted vault, then edit the JSON so `"iterations": 100000` becomes a very large value (the regression test uses `1_000_001` to keep the pre-fix run bounded).
2. Import the payload with `importEncryptedPayloadJson` or restore it from a Gist.
3. Before the fix, `decryptVault` passed the attacker-chosen value straight to `PBEKeySpec` and spent that many PBKDF2 rounds on the IO dispatcher before discovering that the payload could not be authenticated.

At `Int.MAX_VALUE` iterations, a device would burn a CPU core for days; the sync worker stays busy and the UI reports only a generic failure after the work completes.

### Expected

- The payload's KDF cost is bounded before any key derivation.
- Non-positive values are rejected with a clear message instead of leaking a `PBEKeySpec` exception.

### Actual

- `iterations` came from `EncryptedSyncPayload`, which is deserialized from an untrusted Gist/imported JSON.
- It was used unvalidated as the PBKDF2 iteration count.

### Root cause

The encrypted payload is attacker-controlled input, but its cost parameter was trusted. Encryption always writes `100_000`, so any other value is either a future format or hostile input and should be bounded.

### Fix

- Validate `payload.iterations` against `1..1_000_000` at the start of `decryptVault`, before Base64 decoding and key derivation.
- Keep the existing encryption output (`100_000`) unchanged.

### Regression tests

Added to `VaultCryptoTest`:

- `decrypt rejects excessive iteration counts` (`1_000_001`)
- `decrypt rejects non-positive iteration counts` (`0`)

Both failed before the fix: the excessive value derived a key and then failed with `AEADBadTagException`, and the zero value surfaced the low-level `PBEKeySpec` error.

### Verification

```text
./test.sh -c VaultCryptoTest  -> 5 passed
./test.sh -m core:data        -> 25 passed
./test.sh                     -> 257 passed, 0 failed
./build.sh                    -> BUILD SUCCESSFUL
```

### Residual risk

- The upper bound still permits roughly 0.6 s of PBKDF2 work per import on a fast JVM, and more on low-end devices. That is acceptable for a deliberate sync operation but is not free.
- Ciphertext, salt, and IV sizes are still unbounded; a very large payload is limited mainly by the Gist/import size.
- The `algorithm` and `kdf` fields are informational and ignored; only the hard-coded AES-GCM/PBKDF2 combination is supported.

---

## BUG-004: Starting an agent task can persist the user message twice

- Severity: S2
- Status: fixed
- Area: `feature/chat` send path; `core/agent` task history reconstruction
- Files:
  - `feature/chat/src/main/java/com/koda/dev/feature/chat/ChatViewModel.kt`
  - `core/agent/src/main/java/com/koda/dev/core/agent/service/AgentWorkManager.kt`
  - `core/agent/src/test/java/com/koda/dev/core/agent/service/AgentWorkManagerTest.kt`

### Repro

1. Send a normal chat message.
2. `ChatViewModel.sendMessage` updates the UI and starts two independent `Dispatchers.IO` coroutines: one calls `persistMessage(userMsg)`, the other calls `agentWorkManager.startTask`.
3. If `AgentWorkManager.executeTask` reads the session's messages before the persist coroutine inserts the user row, its content check does not find the turn.
4. `executeTask` inserts its own copy with a fresh UUID, then the delayed `persistMessage` inserts the original row.
5. Reloading the session shows the same user message twice, and the next agent turn receives a duplicated user turn in its history.

### Expected

- A single user message row per sent turn, regardless of which coroutine writes first.

### Actual

- Two rows with different IDs and identical content.

### Root cause

The persistence protocol relied on a check-then-insert by content in `AgentWorkManager`, while `ChatViewModel` wrote the same row asynchronously. The two writers had no shared identity, so an interleaving between the check and the insert produced duplicates.

### Fix

- Add `userMessageId` to `startTask` and `AgentTaskState`.
- `ChatViewModel` passes the optimistic message's ID to `startTask`.
- `executeTask` looks up the persisted turn by that ID (falling back to the old content check for callers without an ID) and inserts with the shared ID when it is missing.
- Because `MessageDao.insert` uses `OnConflictStrategy.REPLACE`, the shared primary key keeps the row single no matter which writer lands first.

### Regression test

`AgentWorkManagerTest.startTask does not duplicate a concurrently persisted user message`

The test DAO snapshots the message list and then blocks in `getBySessionList`, letting the test insert the ChatViewModel copy before `executeTask` resumes. Pre-fix the assertion found 2 USER rows; post-fix it finds 1 with the shared ID.

### Verification

```text
./test.sh -c AgentWorkManagerTest  -> 8 passed
./test.sh -m core:agent            -> 76 passed
./test.sh -m feature:chat          -> 20 passed
./test.sh                          -> 258 passed, 0 failed
./build.sh                         -> BUILD SUCCESSFUL
```

### Residual risk

- If both the ChatViewModel persist and the AgentWorkManager insert fail, the optimistic UI still shows the turn while the database has no row. The UI is not reconciled from the DB after a failed write.
- `persistMessage` still reads `_state.value.sessionId` instead of `message.sessionId`. In the deferred `/goal` guidance path, switching sessions before that coroutine runs can persist the turn into the newly selected session. This is a separate misattribution race and is the next candidate.
- Callers that do not pass `userMessageId` still use the content-based fallback, which remains race-prone.

---

## BUG-005: A deferred message write can land in the wrong session

- Severity: S3
- Status: fixed
- Area: `feature/chat` message persistence
- Files:
  - `feature/chat/src/main/java/com/koda/dev/feature/chat/ChatViewModel.kt`
  - `feature/chat/src/test/java/com/koda/dev/feature/chat/ChatViewModelTest.kt`
  - `feature/chat/build.gradle.kts` (test dependency on `core:testing`)

### Repro

1. Send `/goal` with no objective. The view model updates the active session and queues a coroutine that creates the session and persists the user prompt plus the guidance message.
2. Before that coroutine runs, start a new session or switch to another one.
3. The queued `persistMessage` calls read `_state.value.sessionId` at write time, so both messages are inserted into the newly active session instead of the session where they were composed.

The regression test models the same contract directly: persist a message whose `sessionId` differs from the active session.

### Expected

- A persisted message always belongs to `message.sessionId`, independent of what is currently on screen.

### Actual

- `persistMessage` ignored the message's own session and read the global UI state.
- The session lookup/creation used the same mutable state.

### Root cause

The write path treated the current UI session as the source of truth instead of the immutable session ID already carried by the message.

### Fix

- Bind the inserted row and the session-existence check to `message.sessionId`.
- Capture `projectId` and `sessionTitle` when the write starts instead of re-reading state during it.
- Split persistence into an `internal suspend fun persistMessage(message)` and a `private fun persistMessageAsync(message)` wrapper. The main send path keeps the fire-and-forget behavior; the deferred `/goal` path now calls the suspend function directly.

### Regression test

`ChatViewModelTest.persistMessage writes to the message session instead of the active session`

Pre-fix the row was written to the active session's UUID; post-fix it is written to the message's session.

### Verification

```text
./test.sh -c ChatViewModelTest  -> 1 passed
./test.sh -m feature:chat       -> 21 passed
./test.sh -m core:agent         -> 76 passed
./test.sh                       -> 259 passed, 0 failed
./build.sh                      -> BUILD SUCCESSFUL
```

### Residual risk

- When the target session row does not exist yet, it is still created with the active project's ID and title. A project switch during the deferred `/goal` path can therefore create the session under the wrong project even though the message's session ID is correct.
- The main send path is still fire-and-forget: a failed insert is only logged, and the optimistic UI is not reconciled with the database.

---

## BUG-006: Commands silently run on the Android host shell when PRoot is missing

- Severity: S1
- Status: fixed
- Area: `core/terminal` command execution
- Files:
  - `core/terminal/src/main/java/com/koda/dev/core/terminal/PosixTerminalExecutor.kt`
  - `core/terminal/src/main/java/com/koda/dev/core/terminal/TermuxSessionManager.kt`
  - `core/terminal/src/test/java/com/koda/dev/core/terminal/PosixTerminalExecutorTest.kt`

### Repro

1. Run Koda Dev on a build where the PRoot native libraries are unavailable (`NativeBinaryManager.resolvePRootExecutable()` returns null).
2. Execute a command through the agent's terminal tool, for example `echo host-fallback-marker`.
3. Before the fix, the executor returned `Success(output=host-fallback-marker)` by running `/system/bin/sh -c ...` on the Android host.

The regression test constructs exactly that state with a `NativeBinaryManager` whose native library directory is null.

### Expected

- Production command execution never leaves the Linux userland.
- If PRoot is unavailable, return a clear error instead of silently using Android Toybox.
- The interactive terminal keeps showing its Ubuntu setup notice rather than launching a broken PRoot command.

### Actual

- `PosixTerminalExecutor.execute` fell back to `executeHostProcess` whenever no distro was active and PRoot was unavailable.
- `executeUbuntuProcess` and `executeAlpineProcess` did the same when `resolvePRootExecutable()` returned null.
- `buildProcessBuilder`, `buildUbuntuProcessBuilder`, and `buildAlpineProcessBuilder` returned host-shell builders, so background tasks launched by `ManagedTaskManager` also ran outside Linux.
- `TermuxSessionManager` passed `/system/bin/sh` as the `prootExec` executable while still supplying PRoot arguments.

This directly contradicts D16 ("completely eliminate the Android Toybox shell fallback").

### Root cause

The host shell was treated as a universal fallback for every missing-PRoot condition. That fallback is useful for JVM tests and legacy callers without Android context, but it was not restricted to them.

### Fix

- Add `allowsHostShellFallback()`: true only when both `context` and `nativeBinaryManager` are null (unit tests / legacy lightweight instances).
- Add `linuxUnavailable()` returning `TerminalResult.Error("Linux environment is unavailable: PRoot was not found...", 127)`.
- `execute`, `executeUbuntuProcess`, and `executeAlpineProcess` now return that error in production instead of running host commands.
- `buildProcessBuilder` and the distro builders throw `IllegalStateException` instead of returning a host builder; `ManagedTaskManager` already converts launch failures into `TaskLaunchResult.Error`.
- `TermuxSessionManager` throws when PRoot is missing, so its existing catch falls through to the intentional Ubuntu setup notice instead of starting `/system/bin/sh` with PRoot arguments.

### Regression test

`PosixTerminalExecutorTest.execute refuses host shell when PRoot is unavailable`

Pre-fix it returned the host `echo` output with exit code 0. Post-fix it returns a Linux-unavailable error and never exposes the host command output.

### Verification

```text
./test.sh -c PosixTerminalExecutorTest  -> 11 passed
./test.sh -m core:terminal              -> 35 passed
./test.sh                               -> 260 passed, 0 failed
./build.sh                              -> BUILD SUCCESSFUL
```

### Residual risk

- Instances created without Android context and without a `NativeBinaryManager` still use the host shell by design; this is limited to JVM tests and legacy lightweight callers.
- The interactive terminal's setup notice still runs `/system/bin/sh` to render the "install Ubuntu" message. It ignores input and exits, so it cannot execute user commands.
- `CliGitOperations` still resolves `git` from the process `PATH` rather than routing through the Linux userland or the bundled native git. This is a separate candidate.
