---
status: diagnosed
phase: 01-api-foundation-script-node-registration
source: [01-VERIFICATION.md, .planning/user-feedback/01-UAT.md]
started: 2026-06-13T00:28:17Z
updated: 2026-06-13T00:55:00Z
---

## Current Test

number: complete
name: all tests executed
awaiting: gap closure

## Tests

### 1. Addon category visible in sidebar
expected: With both jars installed, the addon-declared category (Scripting) appears as a tab/section in the editor sidebar.
result: issue
notes: |
  Script category IS added to the sidebar (core behavior works), but vertical
  overflow makes it partly inaccessible — no scrollbar appears for the open
  addon category. Matches code review WR-03 (getCategoryScrollMetrics gates on
  selectedCategory == null, so addon categories never get a scrollbar) and
  WR-04 (calculateMaxScroll uses flat heights vs wrap-aware render heights).

### 2. Script node drag-to-canvas
expected: Dragging the Script node entry from the Scripting category places an ADDON node on the graph canvas.
result: issue
notes: |
  Drag-to-canvas works and the category shows the correct title with the Lua
  Script node entry. Three defects observed:
  (a) The placed node is titled "Addon Node" in the visual editor instead of
      the addon definition's display name.
  (b) The default script content ("-- Lua script print(\"Hello from ...\")")
      is empty on a freshly created node and only appears after closing and
      reopening the visual editor — the serializer's defaults are applied on
      the load path but not on the node-creation path.
  (c) The script preview content renders on the wrong z-layer — it draws on
      top of the slideover drawer.

### 3. Preset round-trip
expected: A placed Script node survives save → close → reopen → reload without being silently dropped; script text intact.
result: passed
notes: |
  Survives the full round-trip including a complete game restart; node is
  immediately visible after reload. Confirms CR-02 fix. Also confirms that
  issue 2(b) is a creation-path defect, not a persistence defect.

### 4. Execution pass-through
expected: Running a graph containing the Script node activates and completes the node gracefully with no "null addonTypeId" warning.
result: passed
notes: |
  Tested with the placeholder Script node followed by a normal workflow —
  execution passed through without any error. Confirms CR-03 fix.

### 5. Standalone mode
expected: Pathmind loads cleanly and the editor works normally with no addon jar installed.
result: passed
notes: |
  Game starts normally without the addon; a graph containing the addon node
  shows it as a blank addon node (graceful degradation) as designed.
  Enhancement request: add metadata / a visual indicator on orphaned addon
  nodes showing WHICH addon is missing and that the node is a no-op without it.

## Summary

total: 5
passed: 3
issues: 2
pending: 0
skipped: 0
blocked: 0

## Gaps

### GAP-1: Addon category vertical overflow — no scrollbar
status: failed
test: 1
severity: major
detail: Open addon category with enough entries overflows the sidebar; bottom entries are inaccessible. Scrollbar never renders for addon categories (Sidebar.getCategoryScrollMetrics gates on selectedCategory == null; calculateMaxScroll uses flat NODE_HEIGHT vs wrap-aware render heights). Pre-identified as WR-03/WR-04 in 01-REVIEW.md.

### GAP-2: Placed ADDON node shows generic "Addon Node" title
status: failed
test: 2
severity: minor
detail: Node placed from the palette displays "Addon Node" in the editor instead of the addon definition's display name (e.g. "Lua Script").

### GAP-3: Default script content not applied at node creation
status: failed
test: 2
severity: major
detail: Freshly created Script node has empty content; the serializer's default script only appears after closing and reopening the editor. Defaults are applied on the load/deserialize path but not on the creation path (createNodeFromSidebar / new Node(addonTypeId, x, y)).

### GAP-4: Script preview renders on wrong z-layer
status: failed
test: 2
severity: minor
detail: The node body preview (script text) draws on top of the slideover drawer instead of underneath it — renderer z-ordering / draw-pass placement issue.

### GAP-5: No missing-addon indicator on orphaned addon nodes (enhancement)
status: failed
test: 5
severity: cosmetic
detail: With the addon jar absent, its nodes render as blank addon nodes. Add a visual indicator + metadata showing which addon is missing and that the node is a no-op without it, so the degradation is legible to users.
