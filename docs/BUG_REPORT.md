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
