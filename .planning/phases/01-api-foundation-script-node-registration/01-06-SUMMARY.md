---
phase: 01-api-foundation-script-node-registration
plan: 06
subsystem: test-infrastructure
tags: [regression-tests, addon-api, test-cleanup, docs]
dependency_graph:
  requires: ["01-04"]
  provides: ["regression-gate-CR02-CR03", "corrected-docs-WR08", "clean-test-suite-WR10"]
  affects: ["AddonNodeDataCopy", "NodeGraphPersistence", "AddonNodePersistenceTest", "AddonSidebarTest"]
tech_stack:
  added: []
  patterns: ["install-once registry guard (hasType + catch IllegalStateException)", "integration-shaped round-trip test (exercises real conversion sites not serializer isolation)"]
key_files:
  created:
    - common/src/test/java/com/pathmind/data/AddonNodeConversionRoundTripTest.java
  modified:
    - common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java
    - common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarTest.java
    - docs/addon-api-getting-started.md
decisions:
  - "Retained AddonNodePersistenceTest @BeforeAll install even though none of its own tests use NodeTypeRegistry.INSTANCE — it supports cross-class JVM ordering with AddonNodeConversionRoundTripTest; the hasType guard makes it safely idempotent"
  - "Replaced tautological assertNull(null) in AddonSidebarTest with a meaningful groupByCategory defensive-guard assertion instead of deleting the test slot — maintains the test count and exercises real behavior"
metrics:
  duration: "~35 minutes"
  completed: "2026-06-13T00:08:58Z"
  tasks_completed: 2
  files_changed: 4
---

# Phase 01 Plan 06: Regression Test Gap Closure + Doc/Test Cleanup Summary

**One-liner:** Integration-shaped round-trip regression tests locking CR-02/CR-03 fixes under an automated gate, plus corrected Fabric API version (0.119.4+1.21.4) and removal of tautological test assertion.

## Objective

Close the regression-test gap identified by the phase-01 verifier: the existing `AddonNodePersistenceTest` and `AddonSidebarTest` tested helpers in isolation and would not have caught CR-02 or CR-03 because they never exercised the actual conversion sites. This plan adds an integration-shaped regression test exercising the real paths, and folds in two verifier warnings (WR-08 wrong Fabric API version, WR-10 tautological/dead test scaffolding).

## Tasks Completed

### Task 1: Add ADDON conversion round-trip regression test

**Commit:** `59c7249`

Created `AddonNodeConversionRoundTripTest.java` with four tests:

1. **snapshot→convertToNodes (CR-03 gate):** Builds a live `Node("test_mod:script", x, y)`, calls `AddonNodeDataCopy.copyAddonFieldsToNodeData`, wraps in `NodeGraphData`, drives through `NodeGraphPersistence.convertToNodes`. Asserts `getAddonTypeId()` is non-null after the round-trip and script text survives.

2. **editor-load restore (CR-02 gate):** Builds a `NodeData` with `addonTypeId` set, constructs a fresh `Node(NodeType.ADDON, x, y)`, calls `AddonNodeDataCopy.restoreAddonFieldsToNode`. Asserts `getAddonTypeId()` non-null and `"script"` key present in `getAddonExtraFields()`.

3. **clipboard copy→restore (CR-02 + deep-copy):** Full clipboard round-trip via `copyAddonFieldsToNodeData` then `restoreAddonFieldsToNode`. Asserts identity check (`assertNotSame`) proving `extraFields` is deep-copied, not aliased.

4. **missing-addon placeholder (D-09):** Uses `test_mod:unknown_addon_type` (deliberately unregistered). Asserts `isAddonUnresolved()` is `true` and `extraFields` blob is preserved verbatim.

The install-once registry guard uses `NodeTypeRegistry.INSTANCE.hasType()` + `catch (IllegalStateException)`, tolerating whichever of `AddonNodePersistenceTest` or `AddonNodeConversionRoundTripTest` runs first.

All four tests pass: `./gradlew.bat :common:test --tests "com.pathmind.data.*"` exits 0.

### Task 2: Fix WR-08 doc version and WR-10 test scaffolding

**Commit:** `f81abb2`

**WR-08 — `docs/addon-api-getting-started.md`:**
Changed line 93 from `0.102.0+1.21.4` to `0.119.4+1.21.4`. The old version coordinate does not resolve for MC 1.21.4; the corrected value matches the sibling repo's `gradle.properties` and will allow a third party's first build to succeed.

**WR-10a — `AddonSidebarTest`:**
Replaced the tautological `assertNull(null, "...")` test (`getHoveredAddonDefinition_returnsNullByDefault`) with a meaningful `groupByCategory_nullCategoryDefinition_handledGracefully` test. The new test verifies that `groupByCategory` handles input correctly without crashing — a genuine behavioral assertion against the D-06 grouping contract.

**WR-10b — `AddonNodePersistenceTest`:**
Retained the `@BeforeAll installSyntheticRegistry` but added a clarifying comment documenting why it must stay: `AddonNodeConversionRoundTripTest` requires `test_mod:script` installed in `NodeTypeRegistry.INSTANCE`, and the whichever-runs-first ordering between the two classes is handled by the existing `hasType` guard.

Full test run passes: `./gradlew.bat :common:test --tests "com.pathmind.data.*" --tests "com.pathmind.ui.sidebar.AddonSidebarTest"` exits 0.

## Verification Results

| Check | Result |
|-------|--------|
| `AddonNodeConversionRoundTripTest` all 4 tests pass | PASS |
| `com.pathmind.data.*` + `AddonSidebarTest` full suite exits 0 | PASS |
| No install-once IllegalStateException in cross-class JVM run | PASS |
| `grep "0.102.0+1.21.4" docs/addon-api-getting-started.md` returns no matches | PASS |
| `grep "0.119.4+1.21.4" docs/addon-api-getting-started.md` returns 1 match | PASS |
| `assertNull(null` no longer present in AddonSidebarTest | PASS |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this plan adds test code and a documentation edit only; no production stubs introduced.

## Threat Flags

None — no new runtime code, no new dependencies, no new trust boundaries.

## Self-Check: PASSED

- `common/src/test/java/com/pathmind/data/AddonNodeConversionRoundTripTest.java` — FOUND
- `docs/addon-api-getting-started.md` (0.119.4 version) — FOUND
- Commit `59c7249` — confirmed via git log
- Commit `f81abb2` — confirmed via git log
