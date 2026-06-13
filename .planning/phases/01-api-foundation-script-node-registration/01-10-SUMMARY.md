---
phase: 01-api-foundation-script-node-registration
plan: 10
subsystem: data/execution/ui
tags: [addon-api, serialization, defensive-copy, wr-01, wr-02, wr-06, hardening]
dependency_graph:
  requires: [01-09]
  provides: [canonical-addon-encoding, null-addonTypeId-skip-policy, map-aliasing-fix]
  affects: [AddonNodeDataCopy, NodeGraphPersistence, ExecutionManager, NodeGraphClipboardSupport]
tech_stack:
  added: []
  patterns: [defensive-copy, delegation-refactor, null-identity-skip-policy]
key_files:
  created:
    - common/src/test/java/com/pathmind/data/AddonNodeAliasingTest.java
  modified:
    - common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java
    - common/src/main/java/com/pathmind/data/NodeGraphPersistence.java
    - common/src/main/java/com/pathmind/execution/ExecutionManager.java
    - common/src/main/java/com/pathmind/ui/graph/NodeGraphClipboardSupport.java
decisions:
  - "WR-01: Snapshot and clipboard callers skip null-addonTypeId ADDON nodes via continue guard, matching on-disk save path policy — defensive fallback in AddonNodeDataCopy.copyAddonFieldsToNodeData is secondary"
  - "WR-02: All four aliasing branches use new HashMap<>() defensive copies; serializer-produced maps are already new so only the fallback/placeholder branches were affected"
  - "WR-06: NodeGraphPersistence delegates both ADDON branches entirely to AddonNodeDataCopy; only the null-addonTypeId continue skip stays inline at the save call site"
  - "IN-03: Stale line-number javadoc refs replaced with method-name refs to prevent future confusion"
  - "Unused imports removed from NodeGraphPersistence (AddonNodeContext, AddonNodeSerializer, NodeTypeRegistry) after delegation"
metrics:
  duration: ~12 minutes
  completed: 2026-06-13
  tasks_completed: 2
  files_modified: 5
---

# Phase 01 Plan 10: ADDON Encoding Hardening (WR-01, WR-02, WR-06) Summary

One-liner: Defensive HashMap copies in all four AddonNodeDataCopy branches, null-addonTypeId skip guards in snapshot/clipboard callers, and NodeGraphPersistence ADDON branches delegated to AddonNodeDataCopy as the single canonical encoding.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Defensive-copy extraFields + align null-addonTypeId skip policy | 4aef072 | AddonNodeDataCopy.java, ExecutionManager.java, NodeGraphClipboardSupport.java, AddonNodeAliasingTest.java |
| 2 | Delegate NodeGraphPersistence ADDON branches to AddonNodeDataCopy (WR-06) | 24989b6 | NodeGraphPersistence.java, AddonNodeDataCopy.java |

## What Was Built

### WR-01: Null-addonTypeId skip policy aligned across all four conversion sites

Before this plan, `ExecutionManager.createGraphSnapshot` and `NodeGraphClipboardSupport` retained ADDON nodes with null `addonTypeId` in their output collections, diverging from the on-disk save path (`NodeGraphPersistence.buildNodeGraphData`) which drops them via `continue`. This created degenerate in-memory records that could not be correctly restored.

Added skip guards immediately before the `AddonNodeDataCopy.copyAddonFieldsToNodeData` call in both callers:
- `ExecutionManager.createGraphSnapshot` (before `snapshot.getNodes().add(nodeData)`)
- `NodeGraphClipboardSupport` copy loop (before `NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData()` construction)

The method-level fallback in `AddonNodeDataCopy.copyAddonFieldsToNodeData` was also corrected in the javadoc: the false "must continue to handle that case themselves" sentence was replaced with the accurate guidance that callers SHOULD skip null-addonTypeId nodes before calling the method.

### WR-02: Defensive copies in all four aliasing branches

Before this plan, four branches in `AddonNodeDataCopy` stored a shared map reference instead of a copy:
- Copy direction (lines 81, 85): serializer-throw fallback and placeholder re-save called `setExtraFields(node.getAddonExtraFields())` — direct reference
- Restore direction (lines 127, 133): catch block and missing-addon placeholder called `setAddonExtraFields(nodeData.getExtraFields())` — direct reference

For in-memory clipboard/snapshot records restored multiple times, this aliased one mutable `HashMap` across the original node, the record, and every paste — a later mutation corrupts the others.

All four replaced with `new HashMap<>(source)` (null-safe wrapping). The serializer-produced map in the installed-addon success paths (`ser.serialize(ctx)` and the `new HashMap<>(nodeData.getExtraFields())` in the deserialize success path) was already a fresh instance and did not require changes.

### WR-06: Single canonical ADDON encoding — NodeGraphPersistence delegates to AddonNodeDataCopy

Before this plan, `NodeGraphPersistence.buildNodeGraphData` and `convertToNodes` each contained a full inline ADDON serialize/deserialize branch (30 lines each), duplicating logic that had already diverged from `AddonNodeDataCopy` (the WR-01 skip policy inconsistency). Any future schema change required editing two places.

After this plan:
- `buildNodeGraphData` ADDON branch: retains only the null-addonTypeId `continue` skip inline, then calls `AddonNodeDataCopy.copyAddonFieldsToNodeData(node, nodeData)`
- `convertToNodes` ADDON branch: replaced with single `AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, node)` call
- Removed unused imports from NodeGraphPersistence: `AddonNodeContext`, `AddonNodeSerializer`, `NodeTypeRegistry`

### IN-03: Stale line-number javadoc references removed

The `restoreAddonFieldsToNode` javadoc contained `lines 339–368` which became stale when the inline block was removed. Replaced with method-name reference `convertToNodes`.

## Verification

- `./gradlew.bat :common:test --tests "com.pathmind.data.AddonNodeAliasingTest" -Pmc_version=1.21.4` — passes (4 tests)
- `./gradlew.bat :common:test -Pmc_version=1.21.4` — passes (all: AddonNodeConversionRoundTripTest, AddonNodePersistenceTest, AddonNodeAliasingTest, NodeGraphPersistenceTest)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed unused imports from NodeGraphPersistence**
- **Found during:** Task 2
- **Issue:** After delegating ADDON branches to AddonNodeDataCopy, `NodeGraphPersistence` retained unused imports for `AddonNodeContext`, `AddonNodeSerializer`, and `NodeTypeRegistry` — would cause unused-import warnings in IDEs
- **Fix:** Removed the three unused imports
- **Files modified:** `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java`
- **Commit:** 24989b6

No architectural deviations. Plan executed as written.

## Known Stubs

None. All branches produce correct behavior. No placeholder text or empty data flows introduced.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. All changes are internal in-memory copy semantics — no new trust boundaries crossed.

## Self-Check: PASSED

All created files exist on disk. Both task commits (4aef072, 24989b6) verified in git log. No missing items.
