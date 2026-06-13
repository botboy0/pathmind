---
phase: 02-lua-vm-core-bindings
plan: "02"
subsystem: lua-variable-bridge
tags: [lua, cobalt, variables, marshaling, tdd, binding]
dependency_graph:
  requires:
    - PathmindRuntime interface (02-01)
    - PathmindRuntimeImpl with stub variable methods (02-01)
    - PathmindBindings stub table (02-01)
    - CobaltVm.run() real execution (02-01)
  provides:
    - PathmindRuntimeImplVariableTest (12 tests, all pass)
    - PathmindBindings.getVar/setVar — real scalar marshaling
    - PathmindBindingsVarTest (8 tests, all pass)
    - BIND-01 satisfied: pathmind.getVar/setVar exchange scalars with Pathmind variable store
  affects:
    - common/src/test/java/com/pathmind/execution/PathmindRuntimeImplVariableTest.java (new)
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java (modified)
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/vm/PathmindBindingsVarTest.java (new)
tech_stack:
  added: []
  patterns:
    - "Java switch expression on NodeType for variable unmarshaling (getVariable)"
    - "v.type() == Constants.TBOOLEAN for Cobalt 0.7.3 boolean type check (isBoolean() does not exist)"
    - "Cross-store assertion: set via PathmindRuntimeImpl, read back via ExecutionManager.getRuntimeVariableFromAnyActiveChain"
    - "Fake PathmindRuntime in addon tests: in-memory HashMap mirrors scalar contract; records calls"
key_files:
  created:
    - common/src/test/java/com/pathmind/execution/PathmindRuntimeImplVariableTest.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/vm/PathmindBindingsVarTest.java
  modified:
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java
decisions:
  - "PathmindRuntimeImpl.getVariable/setVariable were already fully implemented in plan 01 (not stubs as the plan assumed) — tests written to exercise and verify existing implementation"
  - "Cobalt 0.7.3 has no LuaValue.isBoolean() — use v.type() == Constants.TBOOLEAN instead (Rule 1 auto-fix during implementation)"
  - "setVar boolean check must use Constants.TBOOLEAN, not isBoolean(), to match Cobalt 0.7.3 API"
metrics:
  duration: "6 min"
  completed: "2026-06-13"
  tasks_completed: 2
  files_created: 2
  files_modified: 1
---

# Phase 02 Plan 02: Variable Bridge — Summary

**One-liner:** pathmind.getVar/setVar wire Lua scalars through PathmindRuntime to ExecutionManager's variable store with type-safe marshaling (Double/Boolean/String only); table/nil writes fail loudly with a Lua error.

---

## What Was Built

### Task 1: PathmindRuntimeImplVariableTest (12 tests, all pass)

Wrote `PathmindRuntimeImplVariableTest` covering all 6 behavior cases from the plan:

- **Double round-trip**: `setVariable("n", 42.0)` → `getVariable("n")` returns `Double 42.0`
- **Boolean round-trip**: `setVariable("b", true)` → `getVariable("b")` returns `Boolean true`; false also tested
- **String round-trip**: `setVariable("s", "hi")` → `getVariable("s")` returns `String "hi"`
- **Absent variable**: `getVariable("does_not_exist")` returns null (→ Lua nil)
- **Unsupported type**: `setVariable("x", new Object())` throws `IllegalArgumentException` with type name in message; null also tested
- **Cross-store assertion**: set via `PathmindRuntimeImpl`, read back via `ExecutionManager.getRuntimeVariableFromAnyActiveChain`; asserts type = PARAM_AMOUNT, key "Amount" = "99.5"
- **Key convention verification**: PARAM_AMOUNT has exactly 1 key ("Amount"); PARAM_MESSAGE has exactly 1 key ("Text"); PARAM_BOOLEAN has Mode/Toggle/Variable keys

The implementation was already complete from plan 01 ("compile-ready stubs that would work with ExecutionManager, wired logic complete"). The tests verify and document the existing correctness.

**Verification**: `./gradlew :common:test --tests "*PathmindRuntimeImplVariableTest*"` → BUILD SUCCESSFUL (12/12 tests pass).

### Task 2: PathmindBindings.getVar/setVar — Real Scalar Marshaling + Test (Sibling Repo)

**PathmindBindings.java** (sibling repo): replaced stub `getVar`/`setVar` with real implementations:

- **getVar**: reads `args.arg(1).checkLuaString().toString()`, calls `runtime.getVariable(name)`, marshals null→NIL, Double→valueOf(d), Boolean→valueOf(b), String→valueOf(s)
- **setVar**: reads name + value; dispatches on type: `v.isNumber()` → `runtime.setVariable(name, v.checkDouble())`, `v.type() == Constants.TBOOLEAN` → `runtime.setVariable(name, v.checkBoolean())`, `v.isString()` → `runtime.setVariable(name, v.checkLuaString().toString())`, else → `throw new LuaError(...)` with type name and allowed types listed
- All other bindings (moveTo/getPosition/getInventory/getBlock) remain as plan 01 stubs

**PathmindBindingsVarTest.java** (sibling repo): 8 tests across all 5 behavior cases plus structural check:

1. `numberRoundTripReturnsLuaNumber` — pre-seeded variable returns correctly
2. `numberSetThenGetRoundTrip` — Lua script asserts equality; verifies Double stored
3. `booleanSetThenGetRoundTrip` — Lua script asserts equality; verifies Boolean stored
4. `stringSetThenGetRoundTrip` — Lua script asserts equality; verifies String stored
5. `getVarForAbsentNameReturnsNil` — absent variable returns nil without error
6. `setVarWithTableRaisesLuaError` — table → FAILURE + sendErrorToChat called + setVariable NOT called
7. `setVarWithNilRaisesLuaError` — nil → FAILURE + setVariable NOT called
8. `bindingsNeverDirectlyAccessExecutionManager` — structural proof: set/get both delegate through runtime interface

**Verification**: 
- `./gradlew test --tests "*PathmindBindingsVarTest*"` → BUILD SUCCESSFUL (8/8 tests pass)
- `./gradlew test` (full suite) → BUILD SUCCESSFUL (14 tests: 6 CobaltVmSmokeTest + 8 PathmindBindingsVarTest)

---

## Verification Results

| Check | Result |
|-------|--------|
| `./gradlew :common:test --tests "*PathmindRuntimeImplVariableTest*"` | BUILD SUCCESSFUL (12/12) |
| `./gradlew test --tests "*PathmindBindingsVarTest*"` in pathmind-lua | BUILD SUCCESSFUL (8/8) |
| `./gradlew test` (full suite) in pathmind-lua | BUILD SUCCESSFUL (14/14) |
| No ExecutionManager reference in addon main source | CONFIRMED (grep returns 0) |
| getVar/setVar call runtime.getVariable/setVariable | CONFIRMED (test structural check + grep) |
| PARAM_AMOUNT→"Amount", PARAM_BOOLEAN→Mode/Toggle/Variable, PARAM_MESSAGE→"Text" | CONFIRMED (key convention tests) |
| table/nil setVar raises Lua error, setVariable NOT called | CONFIRMED (test cases 6 + 7) |

---

## Known Stubs

These stubs remain intentionally for plans 03/04:

| Stub | File | Reason |
|------|------|--------|
| `PathmindBindings.moveTo()` | `PathmindBindings.java` (sibling) | Wired in plan 03 (PathmindNavigator.startGoto) |
| `PathmindBindings.getPosition()` | `PathmindBindings.java` (sibling) | Wired in plan 04 (client-thread dispatch) |
| `PathmindBindings.getInventory()` | `PathmindBindings.java` (sibling) | Wired in plan 04 |
| `PathmindBindings.getBlock()` | `PathmindBindings.java` (sibling) | Wired in plan 04 |
| `PathmindRuntimeImpl.moveTo()` | `PathmindRuntimeImpl.java` | Wired in plan 03 |
| `PathmindRuntimeImpl.getPosition()` | `PathmindRuntimeImpl.java` | Wired in plan 03/04 |
| `PathmindRuntimeImpl.getInventory()` | `PathmindRuntimeImpl.java` | Wired in plan 04 |
| `PathmindRuntimeImpl.getBlock()` | `PathmindRuntimeImpl.java` | Wired in plan 04 |

These stubs do NOT prevent the plan goal: pathmind.getVar/setVar exchange scalars correctly and the variable is readable by a downstream Pathmind node.

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] PathmindRuntimeImpl.getVariable/setVariable already implemented (not stubs)**
- **Found during:** Task 1 — writing tests immediately passed
- **Issue:** The plan said to "replace the stub getVariable/setVariable bodies" but plan 01 already delivered fully functional implementations ("compile-ready stubs that would work with ExecutionManager, wired logic complete" per 02-01-SUMMARY.md). The code had real implementations, not TODO stubs.
- **Fix:** No implementation change needed; wrote the test suite against the existing implementation to verify and document correctness. This is the intended outcome — Task 1 delivers tests + verification.
- **Impact:** Task 1 completed faster than expected; test coverage provides the same value.

**2. [Rule 1 - Bug] Cobalt 0.7.3 has no LuaValue.isBoolean() method**
- **Found during:** Task 2 — compilation failure
- **Issue:** `PathmindBindings.setVar` used `v.isBoolean()` which does not exist in Cobalt 0.7.3. `javap` of `LuaValue.class` confirmed: `isNumber()` and `isString()` exist but `isBoolean()` does not.
- **Fix:** Changed to `v.type() == Constants.TBOOLEAN` — uses the `type()` method + TBOOLEAN constant from `Constants`, both confirmed present in the JAR.
- **Files modified:** `PathmindBindings.java` (sibling repo)

### Cross-Repo Note

Task 2 changes are exclusively in the sibling repo (`pathmind-lua`). Per the cross-repo note, no Pathmind repo commits for Task 2 — the orchestrator commits the sibling repo after executor returns.

---

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The variable marshaling boundary (T-02-06, T-02-07) was the primary threat surface — both mitigations are in place and verified by tests:

- T-02-06: table/nil rejected at binding layer before reaching runtime.setVariable (proven by `setVarWithTableRaisesLuaError` and `setVarWithNilRaisesLuaError`)
- T-02-07: key conventions verified by cross-store assertion and key-count tests

---

## TDD Gate Compliance

| Gate | Task | Status |
|------|------|--------|
| Task 1 RED | Tests written; implementation pre-existed from plan 01 — tests passed on first run (deviation documented) | COMPLIANT (implementation verified correct) |
| Task 1 GREEN | 12/12 tests pass | PASSED |
| Task 2 RED | PathmindBindings stub (plan 01) compiled; PathmindBindingsVarTest written; running stub gave FAILURE for round-trip tests | PASSED |
| Task 2 GREEN | Real getVar/setVar implemented; 8/8 tests pass; full suite 14/14 | PASSED |

---

## Self-Check: PASSED

- `PathmindRuntimeImplVariableTest.java`: FOUND at `common/src/test/java/com/pathmind/execution/`
- `PathmindBindings.java` (sibling) — getVar/setVar real implementations: CONFIRMED (file contains `runtime.getVariable` and `runtime.setVariable`)
- `PathmindBindingsVarTest.java` (sibling): FOUND at `pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/vm/`
- Commit `b257f20`: CONFIRMED (PathmindRuntimeImplVariableTest)
- No ExecutionManager in addon main source: CONFIRMED (grep returns 0)
- All Pathmind-side tests: BUILD SUCCESSFUL
- All addon tests: BUILD SUCCESSFUL (14/14)
