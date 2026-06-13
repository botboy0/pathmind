---
phase: 02-lua-vm-core-bindings
plan: "04"
subsystem: lua-game-state-bindings
tags: [lua, cobalt, game-state, inventory, position, block, main-thread-dispatch, CompletableFuture, tdd, binding]
dependency_graph:
  requires:
    - PathmindRuntimeImpl stubs getPosition/getInventory/getBlock (02-01)
    - PathmindBindings stubs getPosition/getInventory/getBlock (02-01)
    - CobaltVm compute-time infrastructure (02-01 / 02-03)
    - PathmindRuntimeImpl.moveTo real impl (02-03)
  provides:
    - PathmindRuntimeImpl.getPosition — real double[]{x,y,z} via MinecraftClient.execute
    - PathmindRuntimeImpl.getInventory — real Object[] of {slot,item,count} carriers via MinecraftClient.execute
    - PathmindRuntimeImpl.getBlock — real 'ns:id' String or null via MinecraftClient.execute
    - PathmindBindings.getPosition — real Lua {x=,y=,z=} table marshaling
    - PathmindBindings.getInventory — real 1-indexed Lua array of {slot=,item=,count=} tables
    - PathmindBindings.getBlock — real Lua string or nil
    - PathmindBindingsGameStateTest (11 tests, all pass)
    - BIND-03 implementation complete (Pathmind side + addon side)
    - In-game UAT: PENDING human verification (Task 3 checkpoint)
  affects:
    - common/src/main/java/com/pathmind/execution/PathmindRuntimeImpl.java (modified)
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java (modified)
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/vm/PathmindBindingsGameStateTest.java (new)
tech_stack:
  added: []
  patterns:
    - "Main-thread dispatch for game-state reads: CompletableFuture + MinecraftClient.getInstance().execute + result.get(30, TimeUnit.SECONDS) with safe default on timeout/null (T-02-13, T-02-14)"
    - "Inventory carrier: Object[]{Integer slot, String itemId, Integer count} scalar array — no shared Pathmind types, version-agnostic addon contract (CONTEXT decision)"
    - "getBlock nil-safety: isChunkLoaded check before getBlockState; null returned for unloaded chunk, binding maps null to Constants.NIL"
    - "1-indexed Lua array: arr.rawset(i+1, entry) loop over runtime inventory carriers"
    - "tableOf() + rawset for all Lua table construction in bindings (getPosition, getInventory entries)"
key_files:
  created:
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/vm/PathmindBindingsGameStateTest.java
  modified:
    - common/src/main/java/com/pathmind/execution/PathmindRuntimeImpl.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java
decisions:
  - "Inventory slot iteration uses inventory.getStack(i) with Math.min(PlayerInventory.MAIN_SIZE, inventory.size()) to avoid the private .main field — consistent with NodeInventoryCommandExecutor pattern in Node.java"
  - "Player position uses getX()/getY()/getZ() entity methods (confirmed from PathmindNavigator.java line 723) — getPos() does not exist in Yarn mappings for 1.21.4"
  - "Inventory carrier element shape: Object[3] positional array (not named class) — addon reads positionally; no shared types across repos; version-agnostic"
requirements-completed: [BIND-03]
duration: "10 min"
completed: "2026-06-13"
---

# Phase 02 Plan 04: Game-State Bindings (getPosition/getInventory/getBlock) — Summary

**PathmindRuntimeImpl game-state reads implemented with MinecraftClient.execute + CompletableFuture(30s timeout); PathmindBindings wires real Lua {x,y,z}/{slot,item,count}/string marshaling; 11 new tests pass; in-game UAT pending human verification.**

---

## Performance

- **Duration:** ~10 min
- **Started:** 2026-06-13
- **Completed:** 2026-06-13
- **Tasks:** 2 of 3 complete (Task 3 = human-verify checkpoint, not executable by agent)
- **Files modified (Pathmind repo):** 1
- **Files modified (sibling repo — not committed, orchestrator handles):** 2

---

## Accomplishments

- `PathmindRuntimeImpl.getPosition()`, `getInventory()`, `getBlock()` implemented with correct main-thread dispatch (3x `MinecraftClient.getInstance().execute`) and 30-second bounded `CompletableFuture.get()` — no indefinite hang on game pause (T-02-13, T-02-14)
- `PathmindBindings.getPosition()` marshals `double[]` to Lua `{x=,y=,z=}` table; `getInventory()` marshals `Object[]` carriers to 1-indexed Lua array of `{slot=,item=,count=}` tables; `getBlock()` returns Lua string or `nil`
- 11 new tests in `PathmindBindingsGameStateTest` covering position field types, inventory 1-indexing, empty inventory, multi-slot ordering, getBlock string/nil, coordinate marshaling, and delegation constraint
- Full addon test suite: 32/32 tests passing (6 CobaltVmSmokeTest + 11 GameState + 7 MoveTo + 8 Var)

---

## Task Commits

| Task | Name | Commit | Repo |
|------|------|--------|------|
| 1 | Implement getPosition/getInventory/getBlock in PathmindRuntimeImpl | `34c405d` | Pathmind |
| 2 | Wire game-state bindings + addon marshaling test | (sibling repo — orchestrator commits) | pathmind-lua |
| 3 | End-of-phase in-game UAT | PENDING HUMAN VERIFICATION | — |

---

## Files Created/Modified

**Pathmind repo (committed):**
- `common/src/main/java/com/pathmind/execution/PathmindRuntimeImpl.java` — replaced 3 stubs with real main-thread-safe implementations of getPosition/getInventory/getBlock

**Sibling repo (pathmind-lua — written to disk, orchestrator commits):**
- `src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java` — replaced 3 stubs with real Lua table/array/string marshaling; added `tableOf` static import
- `src/test/java/com/mrmysterium/pathmindlua/vm/PathmindBindingsGameStateTest.java` — NEW: 11 tests verifying marshaling correctness with fake runtime

---

## Decisions Made

- **Player position API:** `getX()`, `getY()`, `getZ()` entity methods (verified from `PathmindNavigator.java:723`); `getPos()` does not exist in Yarn mappings for MC 1.21.4.
- **Inventory iteration:** `inventory.getStack(i)` with `Math.min(PlayerInventory.MAIN_SIZE, inventory.size())` — matches `NodeInventoryCommandExecutor` pattern; avoids the private `.main` field (Yarn access restriction).
- **Carrier shape:** `Object[]{Integer slot, String itemId, Integer count}` positional array. The addon reads positionally by index cast — no shared Pathmind types needed, contract is stable across API versions.

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `getPos()` method does not exist in Yarn-mapped Minecraft 1.21.4 ClientPlayerEntity**
- **Found during:** Task 1 compilation
- **Issue:** PATTERNS.md uses `client.player.getPos()` which matches Mojang mappings, but the project compiles against Yarn mappings where the method is `getX()/getY()/getZ()`. Compilation failed with "Symbol not found: getPos()".
- **Fix:** Replaced `var pos = client.player.getPos(); result.complete(new double[]{pos.x, pos.y, pos.z});` with `result.complete(new double[]{client.player.getX(), client.player.getY(), client.player.getZ()});` — confirmed from `PathmindNavigator.java:723`.
- **Files modified:** `PathmindRuntimeImpl.java`
- **Verification:** `:common:compileJava` BUILD SUCCESSFUL

**2. [Rule 1 - Bug] `PlayerInventory.main` is a private field in Yarn mappings (access violation)**
- **Found during:** Task 1 compilation
- **Issue:** PLAN.md action block suggested iterating `inventory.main` but this field is private in `PlayerInventory` under Yarn mappings. Compilation failed with "main has private access in PlayerInventory".
- **Fix:** Replaced `inventory.main.size()` / `inventory.main.get(i)` with `inventory.size()` / `inventory.getStack(i)` — the public `Inventory` interface methods, consistent with `NodeInventoryCommandExecutor` and `NodeGuiSensorEvaluator` usage across the codebase.
- **Files modified:** `PathmindRuntimeImpl.java`
- **Verification:** `:common:compileJava` BUILD SUCCESSFUL; `grep -c "getStack(i)" PathmindRuntimeImpl.java` = 1

---

**Total deviations:** 2 auto-fixed (Rule 1 — bugs in plan's pseudocode for Yarn mappings)
**Impact on plan:** Both fixes required for correct compilation. No scope change. Behavior unchanged from plan intent.

---

## Known Stubs

None — all six game-state bindings (3 Pathmind-side + 3 addon-side) are real implementations.

---

## Pending Human Verification

**Task 3: End-of-phase in-game UAT — full `pathmind.*` surface in a live world**

This task is a `checkpoint:human-verify` — the agent cannot execute it. The human must:
1. Build both JARs (Pathmind + addon shadow JAR)
2. Launch Minecraft 1.21.4 Fabric with both JARs in mods/
3. Verify all six Phase 2 success criteria in-game (see checkpoint details below)

The SUMMARY reflects the automated work completed; UAT outcome is not self-certified.

---

## Threat Surface Scan

All game-state reads are covered by the plan's threat register:
- T-02-13: All three reads dispatch via `MinecraftClient.getInstance().execute` — worker never touches MC directly. Verified by source (`grep -c` = 3).
- T-02-14: All three use `result.get(30, TimeUnit.SECONDS)` — bounded wait, safe default on timeout.
- T-02-15: Position/inventory/block expose player's own data to player's own script — accepted (same person, client-side only).

No new network endpoints, auth paths, file access patterns, or schema changes introduced.

---

## Self-Check: PASSED

- `PathmindRuntimeImpl.java` (Pathmind repo): FOUND — 3x `MinecraftClient.getInstance().execute`, 3x `result.get(30, TimeUnit.SECONDS)`, `inventory.getStack(i)`, `isChunkLoaded`
- `PathmindBindings.java` (sibling): FOUND — `tableOf()` import, `t.rawset("x"`, `arr.rawset(i + 1`, `Constants.NIL : valueOf(id)`
- `PathmindBindingsGameStateTest.java` (sibling): FOUND — 11 tests, 0 failures
- Commit `34c405d`: Task 1 (Pathmind repo) — CONFIRMED
- `:common:compileJava` BUILD SUCCESSFUL — CONFIRMED
- Full addon test suite (`./gradlew test`): 32/32 — BUILD SUCCESSFUL — CONFIRMED
