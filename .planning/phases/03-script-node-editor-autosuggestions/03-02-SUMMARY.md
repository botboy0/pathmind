---
phase: 03-script-node-editor-autosuggestions
plan: 02
subsystem: addon-api-input-routing
tags: [api, input-routing, addon, node-graph, dimension-fix, mavenlocal]
dependency_graph:
  requires: [03-01]
  provides: [AddonNodeInputHandler, bodyHeight-dimension-fix, focus-tracking, overlay-pass, mavenlocal-1.1.5+mc1.21.4]
  affects: [NodeGraph, NodeDimensionCalculator, PathmindVisualEditorScreen, AddonNodeDefinition, AddonNodeContext]
tech_stack:
  added: [AddonNodeInputHandler interface, UUID-based node identity, renderOverlay default hook]
  patterns: [boolean-gate dispatch, guard-return forwarder, try-catch-log addon isolation, UUID extraFields persistence]
key_files:
  created:
    - common/src/main/java/com/pathmind/api/addon/AddonNodeInputHandler.java
  modified:
    - common/src/main/java/com/pathmind/api/addon/AddonNodeBodyRenderer.java
    - common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java
    - common/src/main/java/com/pathmind/api/addon/AddonNodeDefinition.java
    - common/src/main/java/com/pathmind/nodes/NodeDimensionCalculator.java
    - common/src/main/java/com/pathmind/ui/graph/NodeGraph.java
    - common/src/compat/legacy/base/java/com/pathmind/screen/PathmindVisualEditorScreen.java
decisions:
  - renderOverlay added as default no-op to both AddonNodeBodyRenderer AND AddonNodeInputHandler for maximum flexibility; NodeGraph calls both
  - NodeGraph.buildAddonContext generates UUID _node_id on first access and persists to extraFields; stable across frames
  - AddonNodeInputHandler.renderOverlay has a DrawContext import (not a pure-types interface) — accepted as consistent with AddonNodeBodyRenderer pattern
  - handleAddonInputKeyPressed returns handler's boolean verbatim; no key whitelisting in NodeGraph
metrics:
  duration: "~35 min"
  completed: "2026-06-25"
  tasks_completed: 3
  files_modified: 6
  files_created: 1
---

# Phase 03 Plan 02: Addon Input Routing API + NodeDimensionCalculator Fix Summary

**One-liner:** Interactive addon API surface with AddonNodeInputHandler (6-method focus/input interface), NodeType.ADDON dimension branch (bodyHeight override), NodeGraph focus tracking + input forwarding (4 forwarders), overlay post-scissor pass, and mavenLocal republish — co-evolution foundation for EDIT-01..04.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Add AddonNodeInputHandler + extend AddonNodeContext/Definition/BodyRenderer | ed34296 | 4 modified, 1 created |
| 2 | NodeDimensionCalculator ADDON branch + NodeGraph focus tracking, input forwarding, overlay pass | 8b8f017 | 2 modified |
| 3 | Wire PathmindVisualEditorScreen dispatch chain + republish API artifact | da2a73c | 1 modified |

## What Was Built

### Task 1 — API Surface

**`AddonNodeInputHandler.java`** (new): Interface with 6 methods — `mouseClicked`, `keyPressed`, `charTyped`, `mouseScrolled` (boolean returns), `onFocusGained`, `onFocusLost` (void), plus `default void renderOverlay(...)` no-op for the post-scissor popup pass. All methods take `AddonNodeContext ctx` as first parameter. No Minecraft types except `DrawContext` (same as `AddonNodeBodyRenderer`).

**`AddonNodeBodyRenderer.java`**: Added `default void renderOverlay(AddonNodeContext ctx, DrawContext draw, int x, int y, int width, int height)` — backward-compatible no-op for existing renderers.

**`AddonNodeContext.java`**: Added `nodeId`, `lastError` (String), `lastErrorLine` (int) with getters/setters. `nodeId` enables per-node EditorState maps; `lastError`/`lastErrorLine` carry Lua error info across the serialize/render cycle.

**`AddonNodeDefinition.java`**: Added `inputHandler` (AddonNodeInputHandler) and `bodyHeight` (int, sentinel -1 = default) to outer class and Builder. Builder methods `inputHandler(v)` and `bodyHeight(int px)` return `this`. Getters added.

### Task 2 — Dimension Fix + NodeGraph Wiring

**`NodeDimensionCalculator.java`**: New `NodeType.ADDON` branch after the TEMPLATE/CUSTOM_NODE branch. Looks up definition via `NodeTypeRegistry.INSTANCE.definitionFor(addonTypeId)`, reads `def.getBodyHeight()`. If `> 0` uses it; otherwise falls back to `Node.TEMPLATE_NODE_HEIGHT` (108 px). Returns `false`. Fixes the RESEARCH-identified gap where ADDON nodes fell through to field-based height computation.

**`NodeGraph.java`**: 
- Field: `private Node focusedAddonNode = null`
- Methods: `isFocusedAddonNode`, `focusAddonNode` (blur-then-set + onFocusGained), `blurFocusedAddonNode` (onFocusLost + null)
- Forwarders: `handleAddonInputKeyPressed`, `handleAddonInputCharTyped`, `handleAddonInputMouseScrolled`, `handleAddonNodeMouseClicked` — all guard-return false when no focused node; delegate via `resolveInputHandler` + `buildAddonContext`
- Helper `buildAddonContext(Node)`: populates addonTypeId, scriptText, stable nodeId (UUID generated once, persisted in extraFields as `_node_id`), lastError, lastErrorLine
- Helper `resolveInputHandler(Node)`: null-safe def lookup → getInputHandler()
- Overlay pass in `renderAddonNodeContent`: after `disableScissor()`, calls `handler.renderOverlay(...)` (input handler) and `renderer.renderOverlay(...)` (body renderer) both wrapped in try-catch-log

### Task 3 — Screen Wiring + Publish

**`PathmindVisualEditorScreen.java`**:
- `keyPressed`: `handleAddonInputKeyPressed` gate before `handleStopTargetKeyPressed` (before ALL existing handlers + Esc-close + Delete/Backspace)
- `charTyped`: `handleAddonInputCharTyped` gate before `handleStopTargetCharTyped`
- `mouseScrolled`: `handleAddonInputMouseScrolled` gate before `zoomByScroll`
- ADDON click routing: after `handleEventNameFieldClick`, if `clickedNode.getType() == NodeType.ADDON` → routes to `handleAddonNodeMouseClicked`, then `focusAddonNode` + `selectNode`
- Click-away blur: empty-space click calls `blurFocusedAddonNode()` alongside existing `selectNode(null)`

**mavenLocal publish**: Rebuilt from clean for `mc_version=1.21.4` (correct property name). `AddonNodeInputHandler.class` verified present in both `pathmind-fabric-1.1.5+mc1.21.4-dev-shadow.jar` and remapped `pathmind-fabric-1.1.5+mc1.21.4.jar`.

## Verification Results

- `./gradlew :common:compileJava` — PASS (all three tasks)
- `./gradlew :common:test` — PASS (existing tests green; no regression)
- `grep -n "NodeType.ADDON" NodeDimensionCalculator.java` — line 31 confirmed
- `grep -c "handleAddonInput" NodeGraph.java` — 3 occurrences (declaration + 2 call sites in forwarders; count ≥ 3 satisfied)
- `renderOverlay` after `disableScissor()` — confirmed at NodeGraph.java:7436
- `_node_id` UUID generation in `buildAddonContext` — confirmed
- `publishToMavenLocal` (`-Pmc_version=1.21.4`) — BUILD SUCCESSFUL; `AddonNodeInputHandler.class` in published jar

## Deviations from Plan

### Auto-fixed Issues

None — plan executed as written.

### Intentional Adjustments

**1. [Rule 2 - Enhancement] renderOverlay called on both handler AND renderer**
- **Context:** Plan specified calling `handler.renderOverlay(...)` (input handler). Body renderer also has `renderOverlay` as a default no-op method.
- **Decision:** NodeGraph calls `renderOverlay` on both the `AddonNodeInputHandler` (if set) AND the `AddonNodeBodyRenderer` after `disableScissor()`. This lets a renderer-only addon (no input handler) still use `renderOverlay` for unclipped content. Backward-compatible due to both being default no-ops.

**2. [Rule 2 - Security] handleAddonInputKeyPressed returns handler's boolean verbatim**
- Consistent with T-03-02-01: the handler is responsible for returning `true` for all keys while focused. No key whitelisting added in NodeGraph per plan spec.

**3. Publish required `-Pmc_version=1.21.4` not `-PrequestedMinecraftVersion=1.21.4`**
- The root `build.gradle.kts` reads from `mc_version` or `minecraft_version` gradle properties. The plan's verify step used `publishToMavenLocal` (correct), but the correct property override is `-Pmc_version=1.21.4`. Required a clean + rebuild cycle to force Gradle to pick up the new class (Loom build caching had stale intermediate jars from a prior build).

## Known Stubs

None. All API surface is real (no no-op stubs that block plan goal achievement). The `default renderOverlay` no-ops in both interfaces are intentional API extension points, not stubs — they preserve backward compatibility for existing renderers.

## Threat Surface Scan

Implementing T-03-02-01..04 as planned:
- **T-03-02-01 (key leak)**: Mitigated — `handleAddonInputKeyPressed` inserted before ALL existing graph shortcuts.
- **T-03-02-02 (hostile addon swallows input when unfocused)**: Mitigated — NodeGraph only routes when `focusedAddonNode != null`; click routing gated to `NodeType.ADDON` only.
- **T-03-02-03 (buggy handler crashes editor)**: Mitigated — all handler calls wrapped in try-catch-log-and-skip.
- **T-03-02-04 (malformed extraFields blob)**: Mitigated — `_node_id` read with `instanceof String s` guard; `lastErrorLine` read with `instanceof Number n` + intValue(); `_node_id` regenerated if absent/blank.

No new threat surface beyond what the threat model covers.

## Self-Check: PASSED

- AddonNodeInputHandler.java: FOUND
- NodeDimensionCalculator.java: FOUND
- Commits: ed34296, 8b8f017, da2a73c — all present
- Build: `./gradlew :common:compileJava :common:test` PASSED
- Publish: `com.pathmind:pathmind-fabric:1.1.5+mc1.21.4` in mavenLocal; AddonNodeInputHandler.class verified in jar
