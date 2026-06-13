---
phase: 01-api-foundation-script-node-registration
plan: 15
subsystem: ui
tags: [sidebar, node-render, scroll, scrollbar, addon, gap-closure]

# Dependency graph
requires:
  - phase: 01-api-foundation-script-node-registration
    provides: "Addon node body renderer + sidebar addon-category tabs (plans 01-13/01-14); content-panel scroll pattern (ScrollbarHelper)"
provides:
  - "Resolved addon nodes render their real body via renderer.render in ALL positions (incl. over the sidebar), matching built-in nodes — invalid-drop indicator is the discolored frame only"
  - "Category icon bar scrolls vertically with a visible scrollbar when built-in + addon tabs overflow; tabs no longer shrink toward single-digit px"
  - "Icon-bar hit-testing offset by scroll so scrolled-into-view tabs select the correct category"
affects: [phase-02, phase-03, sidebar-ui, addon-node-render]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Icon-bar scroll mirrors the content-panel scroll (offset + maxScroll + clamp + ScrollbarHelper metrics), as a SEPARATE axis from the content scroll"
    - "Render Y and hit-test Y share a single scroll-offset variable so visual position and click target never diverge"

key-files:
  created: []
  modified:
    - common/src/main/java/com/pathmind/ui/graph/NodeGraph.java
    - common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java

key-decisions:
  - "GAP-C: deleted bodyUnderSidebar suppression + renderAddonNeutralBody rather than tweaking it — resolved addon nodes must always render via renderer.render, exactly like built-ins; the frame discoloration in renderNode is the sole invalid-drop indicator"
  - "GAP-A: scroll the icon strip instead of shrinking tabs; reuse ScrollbarHelper + the existing content-panel scrollbar visual style for a separate icon-bar scroll axis"
  - "Icon-bar scissor clip spans the full inner-strip width and the [TOP_PADDING, TOP_PADDING+availableTabHeight] viewport so scrolled tabs clip without bleeding into the content panel"

patterns-established:
  - "Separate-axis scroll: a second independent scroll (iconBarScrollOffset/iconBarMaxScroll) lives beside the existing scrollOffset/maxScroll, each owning its own column; mouseScrolled routes by cursor X"
  - "Hover-derived selection: tab hover flags computed from scroll-offset tabY automatically carry the offset into mouseClicked selection (no separate hit-test math)"

requirements-completed: [LUA-01, API-07]

# Metrics
duration: 4min
completed: 2026-06-13
---

# Phase 01 Plan 15: Correct UAT round-6 gaps (GAP-C body-blanking, GAP-A icon-bar scroll) Summary

**Resolved addon nodes now render their real body in every position (frame-only invalid-drop indicator, matching built-ins), and the category icon bar gained an independent vertical scroll with a scrollbar so tabs stay readable instead of shrinking to 8px.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-06-13T12:14:06Z
- **Completed:** 2026-06-13T12:17:30Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- **GAP-C fixed:** Removed the `bodyUnderSidebar` early-return and the `renderAddonNeutralBody` method from `renderAddonNodeContent`. A resolved addon node (`!unresolved && renderer != null`) now always flows through the scissor-clipped `renderer.render(...)` path regardless of X — identical to a built-in node always rendering its body. The over-sidebar indicator is the discolored frame already applied by `renderNode`. The unresolved `else` branch and the renderer-threw `catch` still call `renderAddonPlaceholderBody` (D-09/GAP-5 missing-addon indicator preserved).
- **GAP-A fixed:** Added a separate icon-bar scroll axis (`iconBarScrollOffset`/`iconBarMaxScroll`). Tabs keep `TAB_SIZE` instead of shrinking to single digits; the strip scrolls when built-in + addon tabs overflow `availableTabHeight`. Render Y for both built-in and addon tabs is offset by the scroll (and the hover/click hit-tests inherit the offset because they derive from the same `tabY`/`addonTabY`). The strip is scissor-clipped to its viewport, a scrollbar draws when `iconBarMaxScroll > 0`, and the mouse wheel over the icon column scrolls the strip (not the content panel).

## Task Commits

Each task was committed atomically:

1. **Task 1: GAP-C — addon node keeps content + frame discoloration** - `1aa3a93` (fix)
2. **Task 2: GAP-A — category icon-bar vertical scroll + scrollbar** - `15dac7a` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified
- `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` - Removed `bodyUnderSidebar` suppression and `renderAddonNeutralBody`; resolved addon nodes always render via `renderer.render` (GAP-C)
- `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java` - Added icon-bar scroll fields, scroll-based overflow (no tab-shrink), scroll-offset render + hit-test, scissor clip, `renderIconBarScrollbar`/`getIconBarScrollMetrics`, and wheel routing (GAP-A)

## Decisions Made
- GAP-C: deleted the suppression path entirely rather than substituting a different neutral body — the grounded analog is the built-in `renderNode`, which never blanks the body; the frame is the invalid-drop cue.
- GAP-A: kept `TAB_SIZE` and scrolled on overflow; reused `ScrollbarHelper.metrics` + `renderSettingsStyle` with the content-panel scrollbar colors for visual consistency.
- Icon-bar hover/click hit-tests were left to derive from the scroll-offset `tabY`/`addonTabY` so render position and click target cannot diverge — no duplicated offset math.

## Deviations from Plan

None - plan executed exactly as written. The plan's step 3 wraps the tab drawing in a scissor clip and offsets `tabY`/`addonTabY`; because the existing hover hit-tests (plan step 5) are computed from those same offset variables, no separate change to the hit-test expressions was needed — the offset is inherited, which satisfies the acceptance criterion that render and hit-test are both offset.

## Issues Encountered
None.

## TDD Gate Compliance
N/A — plan type is `execute` (not `tdd`); tasks were not marked `tdd="true"`. The existing automated suite (`:common:test`) was run as the regression gate.

## Known Stubs
None.

## Threat Flags
None — changes are client-side UI rendering and input routing only; no new network, auth, file, or schema surface.

## Verification
- `./gradlew.bat :common:compileJava -q` → exit 0 (both tasks)
- `./gradlew.bat :common:test` (full common suite) → BUILD SUCCESSFUL, 0 failures / 0 errors across 33 test classes (~242 tests), including `AddonSidebarScrollTest` and `AddonSidebarTest` (no regression)
- Grep acceptance: `renderAddonNeutralBody`/`bodyUnderSidebar` → 0 matches; `renderAddonPlaceholderBody` → 2 call sites (catch + unresolved) + definition; `iconBarScrollOffset` → 6 occurrences (field, clamp, currentY offset, scrollbar metrics, wheel, render)
- In-game re-UAT (visual gate, recorded in 01-VERIFICATION.md): dragging a registered Script node over the sidebar shows its body + discolored frame (never blank); with enough categories to overflow, the icon bar shows a scrollbar, the wheel scrolls it, and clicking a scrolled category selects the right one.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Both round-6 UAT gaps (GAP-C, GAP-A) corrected and unit-test-green; awaits in-game re-UAT confirmation as the visual gate in 01-VERIFICATION.md.

## Self-Check: PASSED

- FOUND: `.planning/phases/01-api-foundation-script-node-registration/01-15-SUMMARY.md`
- FOUND: `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java`
- FOUND: `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java`
- FOUND commit: `1aa3a93` (Task 1, GAP-C)
- FOUND commit: `15dac7a` (Task 2, GAP-A)

---
*Phase: 01-api-foundation-script-node-registration*
*Completed: 2026-06-13*
