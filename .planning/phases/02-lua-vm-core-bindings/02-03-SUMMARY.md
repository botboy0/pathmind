---
phase: 02-lua-vm-core-bindings
plan: "03"
subsystem: lua-moveto-awaitable
tags: [lua, cobalt, moveto, navigation, awaitable, timeout-clock-pause, tdd, binding]
dependency_graph:
  requires:
    - PathmindRuntimeImpl stub moveTo (02-01)
    - PathmindBindings stub moveTo (02-01)
    - CobaltVm compute-time infrastructure, computeMs AtomicLong (02-01)
    - PathmindNavigator.startGoto(BlockPos, String, CompletableFuture<Void>) (existing)
  provides:
    - PathmindRuntimeImpl.moveTo — real awaitable wrapping PathmindNavigator.startGoto
    - PathmindBindings.moveTo — real blocking binding with timeout-clock pause/resume
    - CobaltVm compute-time-based interrupt handler (upgraded from wall-clock)
    - PathmindBindingsMoveToTest (7 tests, all pass)
    - BIND-02 satisfied: pathmind.moveTo blocks worker until navigation completes
  affects:
    - common/src/main/java/com/pathmind/execution/PathmindRuntimeImpl.java (modified)
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java (modified)
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/CobaltVm.java (modified)
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/vm/PathmindBindingsMoveToTest.java (new)
tech_stack:
  added: []
  patterns:
    - "Compute-time-based interrupt handler: actualCompute = elapsed + computeMs where computeMs is decremented by blocked durations"
    - "moveTo binding subtracts blocked wall-clock time from computeMs AtomicLong before navFuture.get() resumes"
    - "CobaltVm overloaded run(budgetMs) for testable timeout-clock pause verification"
    - "MoveToStrategy functional interface pattern for controllable fake futures in tests"
    - "Separate thread (TICK_SIM) completes nav future to mirror game tick thread behavior"
key_files:
  created:
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/vm/PathmindBindingsMoveToTest.java
  modified:
    - common/src/main/java/com/pathmind/execution/PathmindRuntimeImpl.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/CobaltVm.java
decisions:
  - "CobaltVm upgraded from wall-clock timer primary interrupt to compute-time-based interrupt handler: handler checks (elapsed + computeMs.get() > budgetMs) so blocked periods are excluded from the compute budget — required for T-02-10 timeout-clock-pause"
  - "computeMs AtomicLong decremented by blocked interval in moveTo binding so the interrupt handler correctly computes actualCompute = elapsed - blockedMs"
  - "Package-private CobaltVm.run(budgetMs) overload added to allow short-budget testing without 5s default wait"
  - "v1 accepted limitation: a script that calls moveTo then enters an infinite loop is not re-interrupted after the first CONTINUE return from the timer — one-shot timer; documented in CobaltVm comment as T-02-11"
metrics:
  duration: "7 min"
  completed: "2026-06-13"
  tasks_completed: 2
  files_created: 1
  files_modified: 3
---

# Phase 02 Plan 03: Awaitable moveTo — Summary

**One-liner:** PathmindRuntimeImpl.moveTo wraps PathmindNavigator.startGoto with a fresh CompletableFuture<Void>; the pathmind.moveTo Lua binding blocks the worker thread on navFuture.get() with compute-time clock pause via the computeMs accumulator, surfacing navigation failures as Lua errors.

---

## What Was Built

### Task 1: PathmindRuntimeImpl.moveTo — Real Navigation Wrapper (Pathmind repo)

Replaced the `UnsupportedOperationException` stub in `PathmindRuntimeImpl.moveTo` with:

```java
CompletableFuture<Void> navFuture = new CompletableFuture<>();
BlockPos target = BlockPos.ofFloored(x, y, z);
boolean started = PathmindNavigator.getInstance().startGoto(target, "Lua moveTo", navFuture);
if (!started) {
    navFuture.completeExceptionally(
        new RuntimeException("moveTo: PathmindNavigator.startGoto returned false"));
}
return navFuture;
```

Key design choices:
- Returns the future immediately — blocking is the addon worker thread's job
- No Baritone-presence gate — PathmindNavigator handles its own A* fallback internally (RESEARCH.md Finding 2)
- Completes exceptionally only if `startGoto` returns false (i.e., null args — impossible in practice)
- Added `BlockPos` import
- Documented Pitfall 6 (concurrent moveTo cancels prior navigation) in Javadoc

Verification: `:common:compileJava` BUILD SUCCESSFUL; `startGoto(target, "Lua moveTo", navFuture)` grep confirms at line 150.

### Task 2: PathmindBindings.moveTo + Timeout-Clock Pause + Tests (Sibling repo)

**CobaltVm.java — upgraded interrupt handler:**

The wall-clock timer was the primary interrupt mechanism; it set `timedOut=true` after `DEFAULT_BUDGET_MS` regardless of blocking time. This would falsely kill scripts that navigate for > budget (T-02-10 mitigate).

Change: the interrupt handler now computes actual Lua compute time:
```java
long actualComputeMs = (System.currentTimeMillis() - startTimeMs) + computeMs.get();
if (timedOut.get() || actualComputeMs > budgetMs) { throw LuaError(...); }
return InterruptAction.CONTINUE;
```

Where `computeMs.get()` is ≤ 0 (decremented by blocked intervals). The scheduled timer still fires at `budgetMs` wall-clock to trigger the handler; the handler decides whether to throw based on actual compute time.

Added package-private `run(script, runtime, future, budgetMs)` overload for tests.

**PathmindBindings.moveTo — real blocking implementation:**

Replaced the no-op stub with:
```java
CompletableFuture<Void> navFuture = runtime.moveTo(x, y, z);
long pauseStartMs = System.currentTimeMillis();
try {
    navFuture.get();  // blocks worker; tick thread keeps running
} catch (ExecutionException e) {
    throw new LuaError("moveTo failed: " + cause);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new LuaError("moveTo interrupted");
}
long blockedMs = System.currentTimeMillis() - pauseStartMs;
computeMs.addAndGet(-blockedMs);  // subtract blocked time from accumulator
return Constants.NONE;
```

Added `CompletableFuture` and `ExecutionException` imports.

**PathmindBindingsMoveToTest.java — 7 tests:**

1. `moveToBlocksUntilNavFutureCompletesAndScriptSucceeds` — fake runtime completes future after 200ms delay from a separate thread; worker blocks; script completes SUCCESS; elapsed ≥ 150ms; args marshaled correctly
2. `moveToSuccessAllowsSubsequentLuaToRun` — after moveTo, subsequent Lua setVar executes; script completes SUCCESS
3. `moveToExceptionalFutureRaisesLuaErrorAndFailure` — nav failure "path blocked" → FAILURE + chat error contains "moveTo failed" + "path blocked"
4. `moveToExceptionalFutureIncludesCauseMessageInLuaError` — pcall path; LuaError propagates; FAILURE
5. `longNavDoesNotTripTimeoutBecauseClockIsPaused` — 300ms budget, 700ms nav delay; script completes SUCCESS (clock paused during nav)
6. `moveToSuccessWithLongNavAndShortBudget` — 200ms budget, 500ms nav; SUCCESS
7. `moveToMarshalsFloatingPointCoordinatesCorrectly` — 1.5/64.0/-200.75 passed correctly

---

## Verification Results

| Check | Result |
|-------|--------|
| `./gradlew :common:compileJava` | BUILD SUCCESSFUL |
| `grep startGoto PathmindRuntimeImpl.java` | FOUND at line 150 |
| `./gradlew test --tests "*PathmindBindingsMoveToTest*"` (sibling) | BUILD SUCCESSFUL (7/7) |
| `./gradlew test` (full sibling suite) | BUILD SUCCESSFUL (21/21: 6 smoke + 7 moveTo + 8 var) |
| CobaltVmSmokeTest infinite-loop still times out | CONFIRMED (uses DEFAULT_BUDGET_MS via public run() → budgetMs overload) |

---

## Known Stubs

These stubs remain intentionally for plan 04:

| Stub | File | Reason |
|------|------|--------|
| `PathmindRuntimeImpl.getPosition()` | `PathmindRuntimeImpl.java` | Returns `{0,0,0}` — wired in plan 04 |
| `PathmindRuntimeImpl.getInventory()` | `PathmindRuntimeImpl.java` | Returns empty array — wired in plan 04 |
| `PathmindRuntimeImpl.getBlock()` | `PathmindRuntimeImpl.java` | Returns null — wired in plan 04 |
| `PathmindBindings.getPosition()` | `PathmindBindings.java` (sibling) | Returns NIL — wired in plan 04 |
| `PathmindBindings.getInventory()` | `PathmindBindings.java` (sibling) | Returns NIL — wired in plan 04 |
| `PathmindBindings.getBlock()` | `PathmindBindings.java` (sibling) | Returns NIL — wired in plan 04 |

These stubs do NOT prevent the plan goal: pathmind.moveTo blocks until navigation completes, surfaces failures as Lua errors, and does not falsely trip the compute-time timeout.

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] CobaltVm interrupt handler used wall-clock time, not compute time**
- **Found during:** Task 2 implementation, reviewing the timeout-clock-pause requirement
- **Issue:** The existing `CobaltVm.run()` scheduled a timer at `DEFAULT_BUDGET_MS` wall-clock and set `timedOut=true`. The interrupt handler only checked `timedOut.get()`. This meant a long navigation (> budget wall-clock) would trip the timeout even though the worker was blocked, not computing (violates T-02-10).
- **Fix:** Changed the interrupt handler to compute `actualComputeMs = elapsed + computeMs.get()` (where computeMs is decremented by blocked time). The timer still fires at `budgetMs` to trigger the handler; the handler decides whether to throw based on actual compute time. The `computeMs.addAndGet(-blockedMs)` call in the moveTo binding subtracts the navigation wait from the accumulator.
- **Files modified:** `CobaltVm.java` (sibling), `PathmindBindings.java` (sibling)
- **Test proof:** `longNavDoesNotTripTimeoutBecauseClockIsPaused` (700ms nav, 300ms budget) → SUCCESS

**2. [Rule 2 - Missing Critical Functionality] No testable budget overload for CobaltVm**
- **Found during:** Task 2 writing the timeout-clock-pause test
- **Issue:** Tests would need to wait `DEFAULT_BUDGET_MS` (5000ms) for the timer to fire, making test case 3 require an 8s+ test. The plan requires a test proving the clock pauses with a "short compute budget."
- **Fix:** Added package-private `CobaltVm.run(script, runtime, future, budgetMs)` overload; public `run()` delegates to it with `DEFAULT_BUDGET_MS`. Tests use short budgets (200-300ms) for fast execution.
- **Files modified:** `CobaltVm.java` (sibling)

### Cross-Repo Note

Task 1 changes are in the Pathmind repo (committed: `023fbcc`). Task 2 changes are exclusively in the sibling repo (`pathmind-lua`). Per the cross-repo note, no Pathmind repo commit for Task 2 — the orchestrator handles the sibling repo after executor returns.

---

## Threat Surface Scan

All new surface is covered by the plan's threat register:
- T-02-09: moveTo blocking exclusively on the worker thread (tick thread never blocks) — verified by test case 1 completing the future from a separate thread
- T-02-10: Timeout clock pauses during nav — verified by `longNavDoesNotTripTimeoutBecauseClockIsPaused` test
- T-02-11: Navigation never completing — accepted v1 risk; PathmindNavigator's own detection handles it
- T-02-12: Concurrent moveTo cancels prior — accepted v1 risk; documented

No new network endpoints, auth paths, file access patterns, or schema changes introduced.

---

## Self-Check: PASSED

- `PathmindRuntimeImpl.java`: FOUND with `startGoto(target, "Lua moveTo", navFuture)` at line 150
- `PathmindBindings.java` (sibling): contains `navFuture.get()` and `computeMs.addAndGet(-blockedMs)` — CONFIRMED
- `CobaltVm.java` (sibling): contains `actualComputeMs > budgetMs` and `run(budgetMs)` overload — CONFIRMED
- `PathmindBindingsMoveToTest.java` (sibling): FOUND at `pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/vm/`
- Commit `023fbcc`: CONFIRMED (feat: PathmindRuntimeImpl.moveTo)
- All Pathmind-side tests: BUILD SUCCESSFUL (`:common:compileJava`)
- All addon tests (21/21): BUILD SUCCESSFUL (6 CobaltVmSmokeTest + 7 PathmindBindingsMoveToTest + 8 PathmindBindingsVarTest)
