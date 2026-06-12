---
phase: 01-api-foundation-script-node-registration
plan: 05
subsystem: sidebar-ui
tags: [sidebar, addon-api, cr-01, d-05, lua-01, api-07]
dependency_graph:
  requires: []
  provides: [addon-sidebar-render, hoveredAddonDefinition-wiring, drag-start-guard]
  affects: [Sidebar.java, createNodeFromSidebar, mouseClicked-drag-guard]
tech_stack:
  added: []
  patterns: [immediate-mode-render, hit-test-loop, per-frame-hover-reset]
key_files:
  created: []
  modified:
    - common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java
decisions:
  - "Addon categories rendered as tabs in the inner strip (after built-in tabs), with a parallel content panel — mirrors the existing NodeCategory tab/selected-category model"
  - "selectedAddonCategory field added to drive content panel visibility; selecting an addon tab collapses any open built-in category and vice versa"
  - "hoveredAddonDefinition reset at start of addon content pass (mirrors hoveredNodeType = null pattern) rather than at render method start, so non-addon frames do not interfere with the addon render path"
metrics:
  duration: ~10 minutes
  completed: "2026-06-12T23:57:59Z"
  tasks_completed: 1
  tasks_total: 1
  files_modified: 1
---

# Phase 01 Plan 05: Addon Sidebar Palette Wiring Summary

Addon category render + hit-test loop wired into Sidebar so addon-declared categories appear as tabs, their entries are hoverable, and the existing drag-to-canvas path is reachable.

## What Was Built

CR-01 (BLOCKER) closed: `Sidebar.addonCategoryNodes` was populated at construction time but no render or hit-test code ever iterated it, and `hoveredAddonDefinition` was only ever assigned `null`. This plan adds the missing wiring.

### Changes to `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java`

1. **New fields** (lines 72–73):
   - `hoveredAddonCategory` — which addon tab the cursor is over (mirrors `hoveredCategory`)
   - `selectedAddonCategory` — which addon tab is currently expanded (mirrors `selectedCategory`)

2. **`initializeAddonCategoryNodes`** updated to reset both new fields on re-init.

3. **`categoryOpenAnimation` target** updated: animates open when either `selectedCategory != null` OR `selectedAddonCategory != null`.

4. **Addon tab strip** (after built-in tab loop): iterates `addonCategoryNodes.entrySet()`, draws a beveled tab per `AddonNodeCategory` using `getIcon()`, `getColor()`, and `getDisplayName()`. Sets `hoveredAddonCategory` during hit-test. Tab is drawn darkened when selected.

5. **Addon content panel** (parallel block after built-in content `if` block): active when `selectedAddonCategory != null && openProgress > 0.001f`. Draws:
   - Category header with `selectedAddonCategory.getColor()`
   - Per-entry rows from `addonCategoryNodes.get(selectedAddonCategory)` with beveled indicator (`def.getColor()`) and text label (`def.getDisplayName()`)
   - Hit-test sets `hoveredAddonDefinition = def` on hover; background filled with `UITheme.BACKGROUND_TERTIARY`
   - `hoveredAddonDefinition = null` reset at start of pass (stale state guard)

6. **`calculateMaxScroll`** updated: when `selectedAddonCategory != null`, adds `CATEGORY_HEADER_HEIGHT + addonDefs.size() * NODE_HEIGHT` to total content height.

7. **`mouseClicked`**:
   - Addon tab click handler: selects/collapses `selectedAddonCategory`; selecting an addon tab clears `selectedCategory` and vice versa
   - Category-switch hover reset: clears `hoveredAddonDefinition = null` on both built-in and addon tab clicks
   - **Drag-start guard extended** from `hoveredNodeType != null || hoveredCustomNode != null` to `... || hoveredAddonDefinition != null` — addon entries now start a drag and reach `createNodeFromSidebar` (line 1183, which already returns `new Node(hoveredAddonDefinition.getId(), x, y)`)

8. **Mouse-leave reset** (lines 1086–1087): added `hoveredAddonDefinition = null` and `hoveredAddonCategory = null`.

## Verification Results

- `./gradlew.bat :common:compileJava` exits 0 (no errors)
- `grep "hoveredAddonDefinition = "` shows non-null assignment at line 1029 inside hover branch
- `grep "addonCategoryNodes"` shows render/hit-test consumers at lines 741 (tab strip) and 1015 (content panel) — outside `initializeAddonCategoryNodes` and `getAddonCategoryNodesForTest`
- `grep "hoveredAddonDefinition != null"` at line 1139 confirms drag-start guard includes addon entries
- Mouse-leave reset (lines 1086–1087) and category-switch resets (lines 1115, 1131) both clear `hoveredAddonDefinition`

## Deviations from Plan

### Auto-added Features

**1. [Rule 2 - Missing Critical Functionality] Added `selectedAddonCategory` field and tab selection model**
- **Found during:** Task 1 implementation
- **Issue:** The plan describes rendering addon categories "after the built-in content" but the built-in content panel is only visible when `selectedCategory != null`. Without a parallel addon selection field, addon entries would only be visible when a built-in category is open — defeating the purpose.
- **Fix:** Added `selectedAddonCategory` / `hoveredAddonCategory` fields mirroring the existing tab model. Addon tabs appear in the inner strip and open their own content panel independently of built-in categories.
- **Files modified:** `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java`
- **Commit:** 37c9d34

No other deviations — plan executed as written except for the structural extension above which is required for correct operation.

## Known Stubs

None. Addon entries render their actual `def.getDisplayName()` and `def.getColor()` from registered definitions. No placeholders.

## Threat Flags

None. Changes are render + hit-test only. No new network endpoints, auth paths, file access, or persistence introduced. Threat model entries T-01-15 (long display names clipped by scissor region) and T-01-16 (arbitrary color int harmless in render call) apply and are accepted.

## Self-Check: PASSED

- `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java` — exists, modified
- Commit `37c9d34` — exists (confirmed via `git log`)
- `./gradlew.bat :common:compileJava` exits 0
