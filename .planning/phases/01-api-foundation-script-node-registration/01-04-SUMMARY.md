---
phase: 01-api-foundation-script-node-registration
plan: "04"
subsystem: addon-persistence
tags: [gap-closure, addon-api, persistence, CR-02, CR-03]
dependency_graph:
  requires: [01-01, 01-02, 01-03]
  provides: [addon-field-round-trip, addon-field-execution-snapshot]
  affects: [NodeGraph, NodeGraphClipboardSupport, ExecutionManager]
tech_stack:
  added: []
  patterns: [shared-copy-helper, save-restore-symmetry]
key_files:
  created:
    - common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java
  modified:
    - common/src/main/java/com/pathmind/ui/graph/NodeGraph.java
    - common/src/main/java/com/pathmind/ui/graph/NodeGraphClipboardSupport.java
    - common/src/main/java/com/pathmind/execution/ExecutionManager.java
decisions:
  - "Shared helper (AddonNodeDataCopy) rather than inlining identical logic at each of the three broken sites — prevents this class of miss from recurring"
  - "NodeGraphPersistence retains its own inline ADDON branches (primary save path); the helper is for the three previously-broken secondary sites only"
metrics:
  duration: ~15 minutes
  completed: 2026-06-12T23:53:29Z
  tasks_completed: 3
  tasks_total: 3
  files_created: 1
  files_modified: 3
---

# Phase 01 Plan 04: AddonNodeDataCopy Gap Closure (CR-02 + CR-03) Summary

**One-liner:** Shared `AddonNodeDataCopy` helper wires ADDON field copy/restore into three previously-broken Node<->NodeData conversion sites (editor load, clipboard, execution snapshot) closing CR-02 and CR-03.

## What Was Built

The ADDON identity (`addonTypeId` + `extraFields`) was only preserved by `NodeGraphPersistence` (on-disk save/load). Three other conversion sites silently dropped it, causing a Script node to lose its identity and become unreachable after editor open, undo/copy/paste, or graph execution.

This plan created one shared helper and wired all three broken sites through it:

1. **`AddonNodeDataCopy.java`** — new package-public final utility in `com.pathmind.data` with two static methods:
   - `copyAddonFieldsToNodeData(Node, NodeData)` — save direction; mirrors `NodeGraphPersistence.buildNodeGraphData` lines 854–882 including serializer call, Throwable fallback to retained blob, and absent-addon verbatim passthrough
   - `restoreAddonFieldsToNode(NodeData, Node)` — load direction; mirrors `NodeGraphPersistence.convertToNodes` lines 339–368 including deserializer round-trip, `"script"` key injection, Throwable fallback to raw blob + `addonUnresolved=true`, and missing-addon placeholder branch

2. **`NodeGraph.applyLoadedData`** — added `AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, node)` after `node.recalculateDimensions()` before `nodes.add(node)` — closes the editor-load drop (CR-02)

3. **`NodeGraphClipboardSupport.buildGraphData`** — added `AddonNodeDataCopy.copyAddonFieldsToNodeData(node, nodeData)` immediately after `nodeData.setType(node.getType())` — clipboard/undo snapshots now preserve ADDON identity (CR-02)

4. **`NodeGraphClipboardSupport.instantiateClipboardSnapshot`** — added `AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, newNode)` after `newNode.recalculateDimensions()` before `nodes.add(newNode)` — pasted/undone ADDON nodes regain their identity (CR-02)

5. **`ExecutionManager.createGraphSnapshot`** — added `AddonNodeDataCopy.copyAddonFieldsToNodeData(node, nodeData)` before `snapshot.getNodes().add(nodeData)` — execution snapshot now carries `addonTypeId` so `convertToNodes` restores the ADDON node and the registered executor runs (CR-03)

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create AddonNodeDataCopy helper | 6eaead9 | AddonNodeDataCopy.java (new) |
| 2 | Wire applyLoadedData + clipboard (CR-02) | c9c928d | NodeGraph.java, NodeGraphClipboardSupport.java |
| 3 | Wire createGraphSnapshot (CR-03) | 0121772 | ExecutionManager.java |

## Verification Results

- `./gradlew.bat :common:compileJava` exits 0 after all three tasks
- `grep -rn "AddonNodeDataCopy\." common/src/main/java` returns 4 call-site matches:
  - `NodeGraph.java:15701` — restoreAddonFieldsToNode (applyLoadedData)
  - `NodeGraphClipboardSupport.java:148` — restoreAddonFieldsToNode (instantiateClipboardSnapshot)
  - `NodeGraphClipboardSupport.java:241` — copyAddonFieldsToNodeData (buildGraphData)
  - `ExecutionManager.java:3011` — copyAddonFieldsToNodeData (createGraphSnapshot)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. The WR-07 ("script" magic-key) and WR-04 (off-thread completion) warnings are pre-existing and explicitly out of scope for this gap-closure plan.

## Threat Flags

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The helper re-routes existing, already-reviewed serialization logic through a shared class without changing the trust model. Threat items T-01-12 through T-01-14 from the plan's threat model are all mitigated by the try/catch(Throwable) + verbatim-blob fallback in both helper methods.

## Self-Check: PASSED
