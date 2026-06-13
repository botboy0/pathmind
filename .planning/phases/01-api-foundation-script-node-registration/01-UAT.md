---
status: passed
phase: 01-api-foundation-script-node-registration
source: [01-VERIFICATION.md, .planning/user-feedback/01-UAT.md]
started: 2026-06-13T00:28:17Z
updated: 2026-06-13T14:30:00Z
---

## Current Test

number: complete
name: all tests passed in-game
awaiting: none

## Tests

### 1. Addon category visible in sidebar
expected: With both jars installed, the addon-declared category (Scripting) appears as a tab/section in the editor sidebar, and the category icon bar scrolls (with a scrollbar) when categories overflow.
result: passed
notes: |
  Scripting category appears. Category icon-bar overflow now scrolls with a visible
  scrollbar (UAT-GAP-A, plan 01-15) instead of shrinking icons off-screen. Confirmed in-game.

### 2. Script node drag-to-canvas
expected: Dragging the Script node entry places an ADDON node on the canvas; drag-preview title shows the addon display name; default content visible; correct z-layer; invalid-drop shows frame discoloration without blanking content or showing "addon missing".
result: passed
notes: |
  All sub-issues resolved and confirmed in-game:
  (a) drag-preview + placed node show "Script" (GAP-2 / UAT-GAP-B, plans 01-08, 01-14).
  (b) default script content present at creation (GAP-3, plan 01-08; NEW-CR-02 reload path, plan 01-11).
  (c) z-layer / scissor-clip correct (GAP-4, plan 01-09).
  (d) dragging a registered node over the sidebar keeps body content + discolors frame like
      built-ins; no blank body, no false "addon missing" (UAT-GAP-C, plan 01-15).

### 3. Preset round-trip
expected: A placed Script node survives save → close → reopen → reload without being dropped; script text intact, including freshly-placed never-edited nodes.
result: passed
notes: |
  Survives full round-trip including game restart, including the freshly-placed never-edited
  case (NEW-CR-02 blocker fix, plan 01-11). Confirmed in-game.

### 4. Execution pass-through
expected: Running a graph containing the Script node activates and completes the node gracefully with no "null addonTypeId" warning.
result: passed
notes: |
  Execution passes through without error (graceful no-op — no Lua VM until Phase 2). Confirmed in-game.

### 5. Standalone mode
expected: Pathmind loads cleanly with no addon jar; orphaned addon nodes show a missing-addon indicator naming the absent addon.
result: passed
notes: |
  Game starts normally without the addon; orphaned nodes show the missing-addon indicator
  with the addonTypeId label (GAP-5, plan 01-09; WR-05 unresolved-clear, plan 01-11). Confirmed in-game.

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

### GAP-1: Addon category vertical overflow — no scrollbar
status: resolved
test: 1
severity: major
detail: Resolved. Root cause was the category ICON BAR lacking scroll support (not the node list). Plan 01-15 added iconBarScrollOffset/iconBarMaxScroll + renderIconBarScrollbar + wheel handling. Confirmed in-game.

### GAP-2: Placed ADDON node shows generic "Addon Node" title
status: resolved
test: 2
severity: minor
detail: Resolved. Node.getDisplayName() ADDON branch (plan 01-08) for placed nodes; drag-preview title routed through tempNode.getDisplayName() (plan 01-14). Confirmed in-game.

### GAP-3: Default script content not applied at node creation
status: resolved
test: 2
severity: major
detail: Resolved. Constructor seeding (plan 01-08) + null-extraFields reload path (NEW-CR-02, plan 01-11). Confirmed in-game.

### GAP-4: Script preview renders on wrong z-layer
status: resolved
test: 2
severity: minor
detail: Resolved. Scissor-clip in renderAddonNodeContent (plan 01-09); resolved nodes render real body in all positions (plan 01-15). Confirmed in-game.

### GAP-5: No missing-addon indicator on orphaned addon nodes
status: resolved
test: 5
severity: cosmetic
detail: Resolved. renderAddonPlaceholderBody draws "⚠ addon missing" + addonTypeId label (plan 01-09); preserved for genuinely-orphaned nodes only (plan 01-15). Confirmed in-game.
