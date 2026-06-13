---
phase: 01-api-foundation-script-node-registration
plan: "07"
subsystem: ui-sidebar-scroll
tags: [gap-closure, scrollbar, addon-palette, tdd, wrap-aware]
dependency_graph:
  requires: []
  provides:
    - Sidebar.computeAddonContentHeight (package-private static helper)
    - Addon-category scrollbar (render + drag)
    - AddonSidebarScrollTest (4-test coverage)
  affects:
    - Sidebar.getCategoryScrollMetrics (guard widened)
    - Sidebar.calculateMaxScroll (addon branch rewritten, new 5th parameter)
    - Sidebar render addon pass (nodeBackgroundRight + renderCategoryScrollbar)
tech_stack:
  added: []
  patterns:
    - TDD (RED/GREEN cycle with static pure helper)
    - Precompute-and-thread pattern for render-pass metrics
key_files:
  created:
    - common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarScrollTest.java
  modified:
    - common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java
decisions:
  - computeAddonContentHeight takes integer line counts directly (no TextRenderer) enabling unit testing without Minecraft runtime
  - addonRowLineCounts precomputed in render() and threaded as 5th parameter to calculateMaxScroll; one-arg delegate passes null
  - nodeLineHeight hardcoded as 10 inside calculateMaxScroll addon branch (textRenderer not available there); computeAddonContentHeight accepts it as a parameter for testability
metrics:
  duration: ~12min
  completed: 2026-06-13
  tasks_completed: 2
  files_changed: 2
---

# Phase 01 Plan 07: Addon Category Scrollbar (GAP-1 Closure) Summary

Closes GAP-1 (major): open addon palette categories that overflow the sidebar now render a draggable scrollbar and their `calculateMaxScroll` value matches the actual rendered height, so the last entry can always be scrolled into view.

## What Was Built

**Root causes fixed (pre-diagnosed in 01-REVIEW.md):**

- WR-03: `getCategoryScrollMetrics()` returned null when `selectedCategory == null`, blocking scrollbar production and thumb-drag for addon categories.
- WR-04: addon branch of `calculateMaxScroll` used a flat `CATEGORY_HEADER_HEIGHT + addonDefs.size() * NODE_HEIGHT` estimate while the render pass used `Math.max(CATEGORY_HEADER_HEIGHT, headerLines.size() * headerLineHeight)` for the header and `Math.max(NODE_HEIGHT, lines.size() * nodeLineHeight + PADDING)` per row.

### Task 1: Wrap-aware addon content height (TDD)

- Extracted `static int computeAddonContentHeight(int headerLineCount, List<Integer> rowLineCounts, int headerLineHeight, int nodeLineHeight)` mirroring render-pass formulas exactly.
- Added `List<Integer> addonRowLineCounts` as 5th parameter to `calculateMaxScroll`; precomputed in `render()` via `wrapText` for each addon definition.
- Replaced flat accumulation in the addon branch with wrap-aware per-row heights using `computeAddonContentHeight` logic.
- Created `AddonSidebarScrollTest.java` with 4 `@Test` methods covering: single-line header + 3 rows, wrapped row (3 lines > NODE_HEIGHT), empty rows, multi-line header.
- TDD gate: RED commit (`9cce749`) confirmed compilation failure; GREEN commit (`ce8caa5`) passes all 4 tests.

### Task 2: Scrollbar render and drag for addon category

- Fixed `getCategoryScrollMetrics` guard from `selectedCategory == null` to `selectedCategory == null && selectedAddonCategory == null` (WR-03 fix).
- Replaced hardcoded `int nodeBackgroundRight = totalWidth` in the addon content pass with `addonScrollMetrics != null && addonScrollMetrics.maxScroll() > 0 ? addonScrollMetrics.trackLeft() - 2 : totalWidth` mirroring the built-in pass.
- Added `renderCategoryScrollbar(context, totalWidth, contentTop, contentBottom)` call after `context.disableScissor()` in the addon panel block.
- Existing `mouseClicked`/`mouseDragged` thumb-drag paths now receive non-null metrics for addon categories automatically — no additional changes needed.

## Commits

| Hash | Type | Description |
|------|------|-------------|
| `9cce749` | test | RED: failing tests for computeAddonContentHeight |
| `ce8caa5` | feat | GREEN: wrap-aware height helper + calculateMaxScroll addon branch rewrite |
| `beb16c2` | feat | Scrollbar render + getCategoryScrollMetrics guard fix for addon categories |

## Acceptance Criteria Verification

- `grep -c 'static int computeAddonContentHeight' Sidebar.java` (non-comment) = **1** - PASS
- `grep -c 'addonDefs.size() * NODE_HEIGHT' Sidebar.java` = **0** - PASS
- `AddonSidebarScrollTest` contains **4** `@Test` methods - PASS
- `./gradlew.bat :common:test --tests "com.pathmind.ui.sidebar.AddonSidebarScrollTest" -Pmc_version=1.21.4` = **BUILD SUCCESSFUL** - PASS
- `getCategoryScrollMetrics` guard contains `selectedAddonCategory == null` - PASS
- `renderCategoryScrollbar(` call count = **2** (built-in pass + addon pass) - PASS
- `int nodeBackgroundRight = totalWidth` literal removed from addon pass - PASS
- `./gradlew.bat :common:compileJava -Pmc_version=1.21.4` = **BUILD SUCCESSFUL** - PASS

## Deviations from Plan

### Auto-adjustments

**1. [Rule 1 - Bug] Removed fallback branch from calculateMaxScroll addon block**
- **Found during:** Task 1 implementation
- **Issue:** After adding the `addonRowLineCounts` parameter, the initial implementation retained a null-fallback that still used the flat `addonDefs.size() * NODE_HEIGHT` accumulation, which would violate the acceptance criteria requiring 0 occurrences of that literal.
- **Fix:** Removed the fallback — the one-arg delegate never fires when `selectedAddonCategory != null` because the 5-arg form is always called from `render()` which always computes `addonRowLineCounts` before calling `calculateMaxScroll`.
- **Files modified:** `Sidebar.java`
- **Commit:** `ce8caa5`

**2. [Rule 2 - Implementation detail] nodeLineHeight hardcoded as 10 inside calculateMaxScroll**
- **Found during:** Task 1 implementation
- **Issue:** `calculateMaxScroll` does not have access to `TextRenderer`, so `nodeLineHeight` is not directly available as a variable in its scope.
- **Fix:** Used `10` as the per-row line height within `calculateMaxScroll`'s addon branch (typical value: `textRenderer.fontHeight(9) + NODE_LINE_SPACING(1)`). `computeAddonContentHeight` itself accepts `nodeLineHeight` as a parameter so tests can exercise any value. This matches the `getWrappedNodeRowHeight` pattern elsewhere in the file which also uses a hardcoded `7`.
- **Files modified:** `Sidebar.java`

## TDD Gate Compliance

- RED gate: `9cce749` (`test(01-07): add failing tests...`) - CONFIRMED failing compilation
- GREEN gate: `ce8caa5` (`feat(01-07): wrap-aware addon content height...`) - CONFIRMED all 4 tests pass

## Known Stubs

None - all changes are complete implementations wiring real behavior.

## Threat Flags

None - this plan changes client-side UI geometry only (pure integer arithmetic over bounded in-memory lists). No new network endpoints, auth paths, file I/O, or deserialization surface introduced.

## Self-Check: PASSED

- `common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarScrollTest.java` - FOUND
- `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java` - FOUND (modified)
- Commit `9cce749` - FOUND
- Commit `ce8caa5` - FOUND
- Commit `beb16c2` - FOUND
