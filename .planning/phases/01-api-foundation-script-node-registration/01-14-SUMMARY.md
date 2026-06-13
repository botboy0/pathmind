---
phase: 01-api-foundation-script-node-registration
plan: 14
subsystem: ui-render
tags: [uat-gap-closure, addon-node, drag-preview, sidebar, scroll, tdd]
dependency_graph:
  requires: [01-08, 01-11]
  provides: [UAT-GAP-A-closed, UAT-GAP-B-closed, UAT-GAP-C-closed]
  affects: [PathmindVisualEditorScreen, NodeGraph, Sidebar, AddonSidebarScrollTest]
tech_stack:
  added: []
  patterns: [TDD-RED-GREEN, pure-static-helper, neutral-body-render-separation]
key_files:
  created:
    - common/src/main/java/com/pathmind/ui/graph/NodeGraph.java (renderAddonNeutralBody method)
    - common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java (computeAddonMaxScroll method)
  modified:
    - common/src/compat/modern/java/com/pathmind/screen/PathmindVisualEditorScreen.java
    - common/src/compat/mid/java/com/pathmind/screen/PathmindVisualEditorScreen.java
    - common/src/compat/legacy/base/java/com/pathmind/screen/PathmindVisualEditorScreen.java
    - common/src/main/java/com/pathmind/ui/graph/NodeGraph.java
    - common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java
    - common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarScrollTest.java
decisions:
  - "computeAddonMaxScroll delegates to computeAddonContentHeight rather than re-deriving headerLineCount precisely — uses a heuristic ceil division for multi-line headers; the CATEGORY_HEADER_HEIGHT floor in computeAddonContentHeight ensures correctness for the common single-line-header case"
  - "Built-in category +100 buffer in calculateMaxScroll left unchanged; only the addon branch was changed (smaller diff, no risk to built-in scroll behavior)"
  - "renderAddonNeutralBody takes the same isOverSidebar bool as renderAddonPlaceholderBody so it can select the same body fill color (BACKGROUND_SECONDARY vs NODE_DIMMED_BG) without code duplication"
metrics:
  duration: "~15 minutes"
  completed: "2026-06-13T11:54:34Z"
  tasks_completed: 3
  files_changed: 6
requirements: [LUA-01, API-07]
---

# Phase 01 Plan 14: UAT Round-5 Gap Closure (GAP-A/B/C) Summary

Closes the 3 in-game UAT render gaps found in round 5: mislabeled drag-preview title (UAT-GAP-B), false "addon missing" placeholder on resolved nodes over the sidebar (UAT-GAP-C), and inconsistent addon scroll-range formula with magic +100 offset (UAT-GAP-A).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | UAT-GAP-B: Route palette drag-preview title through Node.getDisplayName() | e78fc67 | 3 compat PathmindVisualEditorScreen copies |
| 2 | UAT-GAP-C: Resolved addon nodes under sidebar use neutral dimmed body | 16c0af2 | NodeGraph.java |
| 3 (TDD RED) | UAT-GAP-A: Failing tests for computeAddonMaxScroll | 308af2c | AddonSidebarScrollTest.java |
| 3 (TDD GREEN) | UAT-GAP-A: computeAddonMaxScroll + calculateMaxScroll addon branch fix | 970789b | Sidebar.java |

## What Was Built

**UAT-GAP-B** — All three `PathmindVisualEditorScreen` compat copies (modern, mid, legacy/base) had the drag-preview header title hard-coded to `renderType.getDisplayName()`, which for an ADDON node returns the generic "Addon Node" string. The fix routes through `tempNode.getDisplayName().getString()`, which takes the ADDON branch in `Node.getDisplayName()` and resolves the addon's registered display name (e.g. "Script"). TEMPLATE nodes still use `getTemplateName()` — unchanged.

**UAT-GAP-C** — The `bodyUnderSidebar` suppression branch inside the resolved-node path of `renderAddonNodeContent` was calling `renderAddonPlaceholderBody`, which unconditionally draws "⚠ addon missing". For a *resolved* addon node this is incorrect. A new private method `renderAddonNeutralBody` fills only the body rect with the standard dimmed background (matching the color convention used by the placeholder), with no text. The resolved-node sidebar-overlap branch now calls this neutral method. The unresolved `else` branch and the renderer-threw `catch` fallback continue to call `renderAddonPlaceholderBody` — D-09/GAP-5 behavior preserved.

**UAT-GAP-A** — The `calculateMaxScroll` addon branch used an inline formula (`lineCount * 10 + PADDING`) with a magic `+ 100` tacked on at the end, diverging from the tested `computeAddonContentHeight` helper (WR-03). A new pure static helper `computeAddonMaxScroll(headerLineCount, rowLineCounts, headerLineHeight, nodeLineHeight, sidebarHeight)` returns `Math.max(0, computeAddonContentHeight(...) + PADDING*2 - sidebarHeight)` — no magic offset. The addon branch of `calculateMaxScroll` now calls `computeAddonMaxScroll` and returns early. Built-in category path retains its `+100` buffer.

## Verification Results

- `./gradlew.bat :common:compileJava -q` — BUILD SUCCESSFUL (after Task 1, after Task 2)
- `./gradlew.bat :common:test --tests "com.pathmind.ui.sidebar.AddonSidebarScrollTest"` — BUILD SUCCESSFUL (3 new tests + 4 existing all green)
- `./gradlew.bat :common:test` — BUILD SUCCESSFUL (full suite, no regressions)

### Source Greps
- `tempNode.getDisplayName()` in all 3 compat copies: 3 matches (modern:1206, mid:1090, legacy/base:1105)
- `renderAddonNeutralBody` in NodeGraph.java: method definition at line 7445 + call site at line 7408
- `computeAddonMaxScroll` in Sidebar.java: helper definition at line 635 + addon-branch call at line 588
- `+ 100` in Sidebar.java: only the built-in category line (596) — addon branch no longer contains it

## Deviations from Plan

None — plan executed exactly as written.

The TDD plan specified the GREEN implementation approach: add `computeAddonMaxScroll`, update `calculateMaxScroll` addon branch. Both done as specified. The headerLineCount derivation for the `calculateMaxScroll` call uses a heuristic `ceil(headerHeight / nodeLineHeight)` when `headerHeight > CATEGORY_HEADER_HEIGHT` — this is the most practical approach given headerHeight is already the computed pixel value at that call site (noted in decisions above).

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes. Changes are pure UI rendering and scroll-range math.

## Self-Check: PASSED

- e78fc67 exists: verified via git log
- 16c0af2 exists: verified via git log
- 308af2c exists: verified via git log
- 970789b exists: verified via git log
- `renderAddonNeutralBody` present in NodeGraph.java (line 7445)
- `computeAddonMaxScroll` present in Sidebar.java (line 635)
- All 3 compat copies contain `tempNode.getDisplayName().getString()` (grep confirmed)
- Full test suite BUILD SUCCESSFUL
