---
phase: 01-api-foundation-script-node-registration
plan: 09
subsystem: ui-graph
tags: [addon-rendering, scissor-clip, gap-closure, gap-4, gap-5]
dependency_graph:
  requires: []
  provides: [scissor-clipped-addon-body-preview, missing-addon-indicator]
  affects: [NodeGraph.renderAddonNodeContent, NodeGraph.renderAddonPlaceholderBody]
tech_stack:
  added: []
  patterns: [scissor-clip-render-guard, sidebar-overlap-suppression, graceful-degradation-indicator]
key_files:
  created: []
  modified:
    - common/src/main/java/com/pathmind/ui/graph/NodeGraph.java
decisions:
  - "Suppress live addon preview entirely (show placeholder) when node x < sidebarWidthForRendering, rather than trying to clip the sidebar gap — simpler and consistent with how other nodes degrade when over the drawer"
  - "Pass TextRenderer and addonTypeId to renderAddonPlaceholderBody rather than accessing node directly, keeping the method self-contained and testable"
metrics:
  duration: "~10 minutes"
  completed: "2026-06-13"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 1
requirements: [API-07, LUA-01, LUA-05, API-09]
---

# Phase 01 Plan 09: ADDON Node Rendering Gap Closure (GAP-4, GAP-5) Summary

Scissor-clipped addon body preview with sidebar-overlap suppression (GAP-4) and a "⚠ addon missing" indicator on orphaned ADDON nodes (GAP-5), both isolated to `renderAddonNodeContent`/`renderAddonPlaceholderBody` in NodeGraph.java.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Clip the addon body preview and suppress it under the open sidebar drawer (GAP-4) | 0bbe093 | NodeGraph.java |
| 2 | Render a missing-addon indicator on orphaned ADDON nodes (GAP-5) | 0bbe093 | NodeGraph.java |

Note: Tasks 1 and 2 were committed together as an atomic unit because Task 2's signature change to `renderAddonPlaceholderBody` was required by Task 1's call sites — splitting into two commits would have produced a broken intermediate state.

## What Was Built

**GAP-4 — Scissor-clip + sidebar-overlap suppression:**

In `renderAddonNodeContent`, two changes:
1. Before invoking `renderer.render(...)`, call `context.enableScissor(x + 1, y + 18, x + width - 1, y + height - 1)` to confine the addon renderer to the node body content rect. The scissor is released in a `finally` block so the guard is always removed even if the renderer throws (existing T-01-08 catch still falls back to the placeholder).
2. Check `boolean bodyUnderSidebar = x < sidebarWidthForRendering` before attempting any renderer call. When true, skip the renderer entirely and show the placeholder — this prevents live preview text from painting over the animating sidebar panel.

**GAP-5 — Missing-addon indicator:**

`renderAddonPlaceholderBody` now accepts `TextRenderer textRenderer` and `String addonTypeId` parameters. After filling the grayed body rect, it draws two lines in `UITheme.TEXT_TERTIARY`:
- Line 1: literal `"⚠ addon missing"`
- Line 2: the `addonTypeId` (or `"unknown"` if null), trimmed to the body width via `trimTextToWidth`

All three call sites in `renderAddonNodeContent` were updated to pass `textRenderer` and `node.getAddonTypeId()`.

## Verification

- `./gradlew.bat :common:compileJava -Pmc_version=1.21.4` exits 0 (BUILD SUCCESSFUL, 3 tasks executed)
- `context.enableScissor(` present in `renderAddonNodeContent` with matching `context.disableScissor()` in `finally`
- `sidebarWidthForRendering` comparison present inside `renderAddonNodeContent`
- `renderer.render(ctx, context, x + 1, y + 18, width - 2, height - 19)` exists exactly once (offsets unchanged)
- `renderAddonPlaceholderBody` signature includes `TextRenderer` and `String addonTypeId`
- All three call sites pass `node.getAddonTypeId()`

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — both rendering paths are fully implemented with live data.

## Threat Flags

No new security-relevant surface introduced. The scissor guard (T-01-09-01) contains the existing addon renderer boundary. The `addonTypeId` exposed in the placeholder (T-01-09-02) is the addon's own public namespaced id already persisted in plaintext preset JSON — no new information disclosed.

## Self-Check: PASSED

- `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` exists and was modified
- Commit `0bbe093` exists in git log
- Build exits 0
