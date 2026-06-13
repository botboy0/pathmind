---
phase: 01-api-foundation-script-node-registration
plan: 11
subsystem: data
tags: [addon-api, persistence, node-serialization, regression-test, tdd]

# Dependency graph
requires:
  - phase: 01-api-foundation-script-node-registration
    provides: AddonNodeDataCopy centralised ADDON field copy/restore utility (plans 01-04, 01-08)
provides:
  - restoreAddonFieldsToNode unconditionally seeds non-null addonExtraFields and clears addonUnresolved on success
  - AddonNodeReloadRegressionTest gating null-extraFields default-survival path (NEW-CR-02) and unresolved-clear path (WR-05)
affects:
  - LUA-05 preset save/load observable truth #4
  - API-05 addon-agnostic JSON persistence contract
  - plans 01-12, 01-13 (downstream gap-closure plans)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Unconditional base-map initialization pattern: build HashMap<> from existing fields or empty, then setAddonExtraFields, mirrors Node(String,int,int) constructor seeding"
    - "TDD RED/GREEN: write failing regression test first, fix production code second, commit each gate separately"

key-files:
  created:
    - common/src/test/java/com/pathmind/data/AddonNodeReloadRegressionTest.java
  modified:
    - common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java

key-decisions:
  - "Unconditional base-map init (not guarded by null-check) mirrors Node constructor seeding pattern — consistent mental model for the null-fields path"
  - "setAddonUnresolved(false) placed immediately after setAddonExtraFields in success branch — clear ordering: map first, then flag"

patterns-established:
  - "restoreAddonFieldsToNode success branch: always set addonExtraFields (never null), always clear addonUnresolved"

requirements-completed: [LUA-05, API-05]

# Metrics
duration: 3min
completed: 2026-06-13
---

# Phase 01 Plan 11: Fix restoreAddonFieldsToNode null-extraFields + unresolved-clear Summary

**Null-extraFields reload gap (NEW-CR-02) and stale unresolved-flag bug (WR-05) fixed in restoreAddonFieldsToNode success branch via unconditional base-map initialization and setAddonUnresolved(false), gated by new AddonNodeReloadRegressionTest suite.**

## Performance

- **Duration:** 3 min
- **Started:** 2026-06-13T09:56:57Z
- **Completed:** 2026-06-13T10:00:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Fixed NEW-CR-02 (BLOCKER): `restoreAddonFieldsToNode` now builds a base map unconditionally from `getExtraFields()` (or a fresh `new HashMap<>()` when null), calls `node.setAddonExtraFields(base)` unconditionally, and then injects the serializer-seeded default under "script" — freshly-placed never-edited Script nodes now survive close-and-reopen cycles with non-null `addonExtraFields` containing the default script.
- Fixed WR-05: Added `node.setAddonUnresolved(false)` in the success branch so nodes previously marked unresolved (loaded while addon was absent) correctly clear the missing-addon indicator after a successful restore.
- Created `AddonNodeReloadRegressionTest` with three tests gating both fixed paths and a no-regression guard for the pre-populated extraFields path; all three pass against the patched code.

## Task Commits

Each task was committed atomically following TDD RED/GREEN order:

1. **RED — Task 2: Add AddonNodeReloadRegressionTest** - `427f8c5` (test)
2. **GREEN — Task 1: Fix restoreAddonFieldsToNode success branch** - `389f7dd` (feat)

_TDD flow: regression tests written and committed first (RED, 2 failures confirmed), then production fix committed (GREEN, all 3 tests pass)._

## Files Created/Modified

- `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java` — `restoreAddonFieldsToNode` success try-block: replaced null-guarded conditional `setAddonExtraFields` and double-conditioned script injection with unconditional base-map init + `setAddonUnresolved(false)`
- `common/src/test/java/com/pathmind/data/AddonNodeReloadRegressionTest.java` — New regression test class with `@BeforeAll` synthetic registry install (same install-once guard as `AddonNodeConversionRoundTripTest`), inline serializer that seeds DEFAULT_SCRIPT on null-fields, and three `@Test` methods

## Decisions Made

- Unconditional base-map initialization (no null guard) mirrors the `Node(String,int,int)` constructor seeding pattern established in plan 01-08 — consistent mental model, both paths always produce non-null `addonExtraFields` on success.
- `setAddonUnresolved(false)` placed immediately after `setAddonExtraFields` in the success branch for clear ordering: map is established first, then the resolution flag is cleared.

## Deviations from Plan

None from original plan execution — plan executed exactly as written. TDD order was: RED (test commit `427f8c5`) before GREEN (fix commit `389f7dd`), matching the plan's `tdd="true"` annotations.

---

### Post-merge gate fix (2026-06-13) — commit `12f69ef`

**Defect found:** After the plan-01-11 merge, `./gradlew.bat :common:test` failed with 2 tests in `AddonNodeReloadRegressionTest` failing (`restoreWithNullExtraFields_seedsNonNullDefaultScript` and `restoreSuccess_clearsAddonUnresolved`), but both passed when the class ran in isolation.

**Root cause — install-once singleton exhausted by first test class:**
`NodeTypeRegistry.INSTANCE.install()` is install-once (throws `IllegalStateException` on a second call; this is an intentional security property — no weakening applied). All 4 addon test classes had a `@BeforeAll` that tried to register `test_mod:script`, guarded by a `hasType` check with the `IllegalStateException` silently caught. JUnit runs test classes sequentially in one JVM in alphabetical-package order. `AddonNodeAliasingTest` ran first and consumed the install slot with `aliasing_test_mod:script`. Every later class's install attempt for `test_mod:script` hit the slot limit, the `IllegalStateException` was swallowed, and `test_mod:script` was never registered. At runtime, `NodeTypeRegistry.INSTANCE.hasType("test_mod:script")` returned `false`, causing `restoreAddonFieldsToNode` to take the absent-addon branch (sets `addonExtraFields` to null for null-fields input, keeps `addonUnresolved = true`) — exactly the two assertion failures observed.

**Fix — shared registry consolidation:**
Created `AddonTestRegistry` (test-only utility class in `com.pathmind.data`) that:
- Registers **both** `test_mod:script` and `aliasing_test_mod:script` in a **single** `NodeTypeRegistrar` install call.
- Exposes `ensureInstalled()` — idempotent: first call installs, subsequent calls short-circuit on the `hasType` check.
- The shared serializer for `test_mod:script` seeds `DEFAULT_SCRIPT` on null-fields (NEW-CR-02 requirement).
- `AddonNodePersistenceTest` retains a class-local `TEST_SERIALIZER` for its `deserialize_toleratesNullFields` test (which calls the serializer directly, not via the registry — the shared null-seeding behavior would break that assertion if used directly).
All 4 addon test classes' `@BeforeAll` now delegate to `AddonTestRegistry.ensureInstalled()` instead of building inline registrars.

**Production code:** `AddonNodeDataCopy.restoreAddonFieldsToNode` was correct and unchanged. `NodeTypeRegistry` install-once property preserved — no reset or weakening.

**Verified:** `./gradlew.bat :common:test --rerun-tasks` — BUILD SUCCESSFUL, 230 tests completed, 0 failed.

## Issues Encountered

None — both tests failed in RED as expected (2 of 3 failed), and all 3 passed in GREEN after the fix.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes. The fix is entirely within the `restoreAddonFieldsToNode` in-memory success branch. The base map is built via `new HashMap<>(...)` defensive copy (T-01-11-01 mitigated). No new untrusted-input surface introduced.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- NEW-CR-02 closed: LUA-05 / API-05 observable truth #4 (save/load with null extraFields) now holds at the code level.
- WR-05 closed: unresolved-flag correctly cleared on successful restore.
- `AddonNodeConversionRoundTripTest` and `AddonNodeReloadRegressionTest` both pass — no regressions.
- Plans 01-12 and 01-13 can proceed; this was the last remaining automated gap blocking LUA-05 / API-05.

## Self-Check

- [x] `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java` — FOUND
- [x] `common/src/test/java/com/pathmind/data/AddonNodeReloadRegressionTest.java` — FOUND
- [x] Commit `427f8c5` (test RED) — FOUND
- [x] Commit `389f7dd` (feat GREEN) — FOUND

## Self-Check: PASSED

---
*Phase: 01-api-foundation-script-node-registration*
*Completed: 2026-06-13*
