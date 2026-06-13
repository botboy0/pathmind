---
phase: 01-api-foundation-script-node-registration
reviewed: 2026-06-13T00:19:44Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java
  - common/src/main/java/com/pathmind/ui/graph/NodeGraph.java
  - common/src/main/java/com/pathmind/ui/graph/NodeGraphClipboardSupport.java
  - common/src/main/java/com/pathmind/execution/ExecutionManager.java
  - common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java
  - common/src/test/java/com/pathmind/data/AddonNodeConversionRoundTripTest.java
  - common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java
  - common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarTest.java
  - docs/addon-api-getting-started.md
findings:
  critical: 0
  warning: 6
  info: 3
  total: 9
status: issues_found
---

# Phase 01: Code Review Report (Gap-Closure Pass)

**Reviewed:** 2026-06-13T00:19:44Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

This pass reviews the gap-closure commits (`6eaead9..37211a8` on top of diff base `eea2c3b`) that closed CR-01/CR-02/CR-03 from the prior review: the new `AddonNodeDataCopy` helper and its wiring into `NodeGraph.applyLoadedData`, `NodeGraphClipboardSupport`, and `ExecutionManager.createGraphSnapshot` (plan 01-04), the addon sidebar tab render + hit-test wiring (plan 01-05), and the regression tests plus doc version fix (plan 01-06).

The core gap closures are real and verified:
- `restoreAddonFieldsToNode` is invoked at the correct point in both `applyLoadedData` (NodeGraph.java:15701) and clipboard paste (NodeGraphClipboardSupport.java:148), matching the ordering of the canonical `NodeGraphPersistence.convertToNodes` path (restore after `recalculateDimensions()`).
- `copyAddonFieldsToNodeData` is invoked in `createGraphSnapshot` (ExecutionManager.java:3011) and clipboard copy (NodeGraphClipboardSupport.java:241), and snapshots round-trip correctly through `convertToNodes` (ExecutionManager.java:2825, 2853).
- The sidebar drag pipeline is complete end-to-end: addon tab render/hit-test -> `selectedAddonCategory` panel -> `hoveredAddonDefinition` -> `mouseClicked` drag signal -> `createNodeFromSidebar` builds an ADDON node via `new Node(addonTypeId, x, y)` (Sidebar.java:1183-1186, Node.java:391).
- All three test classes were executed during this review (`gradlew :common:test`): AddonNodeConversionRoundTripTest 4/4, AddonSidebarTest 6/6, AddonNodePersistenceTest all green; 0 failures, 0 errors. The two test classes' synthetic serializers are behaviorally identical, so the install-once "whichever class runs first wins" guard is order-independent.
- The doc fix (`fabric-api 0.119.4+1.21.4`) resolves prior WR-08.

However, the new code introduces six warnings: a documented-but-unfulfilled caller contract for null-`addonTypeId` nodes (divergence from the canonical persistence policy), map aliasing in the placeholder/fallback branches of `AddonNodeDataCopy`, a dead scrollbar for the addon sidebar panel, max-scroll underestimation for wrapped addon names, a test whose name claims coverage it does not provide, and acknowledged-but-already-diverged code duplication between `AddonNodeDataCopy` and `NodeGraphPersistence`.

## Warnings

### WR-01: `AddonNodeDataCopy` javadoc asserts a null-addonTypeId caller contract no caller fulfills; snapshot/clipboard policy diverges from persistence

**File:** `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java:49-52`, `common/src/main/java/com/pathmind/execution/ExecutionManager.java:3011`, `common/src/main/java/com/pathmind/ui/graph/NodeGraphClipboardSupport.java:241`
**Issue:** The canonical save path (`NodeGraphPersistence.buildNodeGraphData`) **drops** ADDON nodes with null `addonTypeId` entirely (`continue`, "Pitfall 5" / T-01-09). `copyAddonFieldsToNodeData` cannot drop the record, so its javadoc states: "Callers in ExecutionManager that skip null-addonTypeId nodes must continue to handle that case themselves." No caller does this — `createGraphSnapshot` and the clipboard copy loop both add the degenerate `NodeData` (type ADDON, no addonTypeId, no extraFields) to their collections. The result is three different policies for the same broken node: persistence drops it, snapshot/clipboard keep it, and `restoreAddonFieldsToNode` no-ops on it, producing a live ADDON node with null identity. Execution is safe (`executeAddonNode` at Node.java:3812 skips with a warning), so this is not a crash, but the documented contract is false and the divergence will confuse the next maintainer.
**Fix:** Either align the policy (skip null-addonTypeId ADDON nodes in `createGraphSnapshot` and clipboard copy, matching persistence):
```java
if (node.getType() == NodeType.ADDON && node.getAddonTypeId() == null) {
    System.err.println("[Pathmind] Skipping ADDON node with null addonTypeId during snapshot (T-01-09)");
    continue;
}
```
or correct the javadoc to state that callers deliberately retain such nodes and that execution skips them gracefully.

### WR-02: Map aliasing in placeholder/fallback branches — copied nodes can share one mutable `extraFields` map

**File:** `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java:81,85,127,133`
**Issue:** Four branches store a shared reference instead of a copy:
- copy direction: `nodeData.setExtraFields(node.getAddonExtraFields())` (serializer-throws fallback, line 81; addon-absent placeholder, line 85)
- restore direction: `node.setAddonExtraFields(nodeData.getExtraFields())` (deserializer-throws catch, line 127; addon-absent placeholder, line 133)

Unlike the on-disk persistence path (where the `NodeData` is immediately serialized to JSON and discarded), clipboard `NodeData` and execution snapshots live in memory and are restored multiple times. Copying an unresolved placeholder ADDON node and pasting it twice leaves the original node, the clipboard record, and both pasted nodes all sharing a single mutable `HashMap` — any later mutation of one node's `addonExtraFields` (e.g. the `put("script", ...)` in the restore success path, or a script edit after the addon resolves) silently corrupts the others. Note that the round-trip test's deep-copy assertion (`AddonNodeConversionRoundTripTest.java:233`) only proves the installed-addon path, which goes through `ser.serialize()` / `new HashMap<>()`; the four aliasing branches are untested.
**Fix:** Defensive-copy in all four branches:
```java
nodeData.setExtraFields(node.getAddonExtraFields() != null
    ? new HashMap<>(node.getAddonExtraFields()) : null);
// ...and...
node.setAddonExtraFields(nodeData.getExtraFields() != null
    ? new HashMap<>(nodeData.getExtraFields()) : null);
```
Add a regression test for the placeholder copy->restore path asserting `assertNotSame`.

### WR-03: Addon category panel has no scrollbar — `getCategoryScrollMetrics()` ignores `selectedAddonCategory`

**File:** `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java:1443-1446`
**Issue:** `getCategoryScrollMetrics()` returns null whenever `selectedCategory == null`. When an addon category is open (`selectedAddonCategory != null`, `selectedCategory == null`) and content overflows, no scrollbar is rendered for the addon panel (the addon content pass at lines 980-1058 never draws one), and the scroll-thumb drag path in `mouseClicked` (line ~1097) and `mouseDragged` (line ~1159) is dead. Only mouse-wheel scrolling works. The built-in panel gets a full scrollbar via the same metrics call; the addon panel was wired into `calculateMaxScroll` and `categoryOpenAnimation` but not into the scrollbar.
**Fix:**
```java
private ScrollbarHelper.Metrics getCategoryScrollMetrics() {
    if ((selectedCategory == null && selectedAddonCategory == null) || maxScroll <= 0) {
        return null;
    }
    ...
}
```
and render the scrollbar in the addon content pass (mirroring the built-in pass), including the `nodeBackgroundRight = trackLeft() - 2` exclusion so row hover/hit areas do not extend under the track.

### WR-04: `calculateMaxScroll` underestimates addon content height for wrapped display names — bottom rows can become unreachable

**File:** `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java:571-578`
**Issue:** The addon branch adds a flat `CATEGORY_HEADER_HEIGHT` plus `addonDefs.size() * NODE_HEIGHT`, but the render pass sizes each addon row as `Math.max(NODE_HEIGHT, lines.size() * nodeLineHeight + PADDING)` (line ~1018) and the header as `Math.max(CATEGORY_HEADER_HEIGHT, headerLines.size() * headerLineHeight)` (line ~651). For addon definitions or category names long enough to wrap, total rendered height exceeds the computed `totalHeight`; once the cumulative deficit exceeds the +100px scroll slack, the last rows cannot be scrolled into view. The built-in path avoids this by passing measured `headerHeight` / `NodeRowInfo` heights into `calculateMaxScroll`; the addon branch ignores the `headerHeight` parameter that `render` already computes for it.
**Fix:** In the addon branch, use the measured header height and wrap-aware row heights (compute `wrapText(def.getDisplayName(), ...)` row heights the same way the render pass does, or precompute `NodeRowInfo`-style records for addon defs and pass them in).

### WR-05: Test name claims null-category handling but never exercises it

**File:** `common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarTest.java:136-148`
**Issue:** `groupByCategory_nullCategoryDefinition_handledGracefully` and its comments claim to verify that "a definition without a category is excluded from the result rather than causing a NullPointerException (D-06 defensive grouping contract)" — but the test body only builds `buildDef("test_mod:script", SCRIPTING_CATEGORY)` (a definition **with** a category) and asserts it groups. No null-category definition is ever constructed; the defensive contract the test is named after remains completely unverified. This replaced a previous tautological `assertNull(null)` test, so it is an improvement, but it still advertises coverage that does not exist — a future regression in `groupByCategory`'s null handling would pass this suite.
**Fix:** Add a definition with a null category to the input list and assert the graceful behavior:
```java
AddonNodeDefinition defWithoutCategory = buildDef("test_mod:no_category", null);
Map<AddonNodeCategory, List<AddonNodeDefinition>> grouped =
    Sidebar.groupByCategory(List.of(defWithCategory, defWithoutCategory));
assertEquals(1, grouped.size(), "Null-category definition must be excluded, not throw");
```
If `AddonNodeDefinition.builder` rejects a null category at construction time, the scenario is unreachable — in that case rename the test to what it actually verifies (e.g. `groupByCategory_singleDefinition_groupsUnderItsCategory`) and delete the misleading comments.

### WR-06: `AddonNodeDataCopy` duplicates `NodeGraphPersistence`'s inline ADDON branches — and the two copies have already diverged

**File:** `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java:19-21`, `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java:339-369,853-883`
**Issue:** The class javadoc declares itself "the single canonical encoding" while simultaneously documenting that `NodeGraphPersistence` "keeps its own inline ADDON branches." Two copies of the same non-trivial serialize/deserialize logic now exist, and they already differ behaviorally (the null-`addonTypeId` drop policy, WR-01). Any future change to the addon field schema, the `"script"` key convention, or the D-09 placeholder contract must now be made in two places, and the round-trip tests exercise the helper plus `convertToNodes` but not `buildNodeGraphData` — divergence in the save path would not be caught by the new regression suite.
**Fix:** Refactor `NodeGraphPersistence.buildNodeGraphData` and `convertToNodes` to call `AddonNodeDataCopy`, keeping only the skip/`continue` decision inline at the persistence call site:
```java
if (node.getType() == NodeType.ADDON && node.getAddonTypeId() == null) {
    System.err.println("[Pathmind] Skipping ADDON node with null addonTypeId during save (T-01-09)");
    continue;
}
AddonNodeDataCopy.copyAddonFieldsToNodeData(node, nodeData);
```

## Info

### IN-01: Addon tab hover skips the hover animation used by built-in tabs

**File:** `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java:752`
**Issue:** `AnimationHelper.lerpColor(normalColor, hoverColor, 1f)` hardcodes the lerp factor to 1 — equivalent to using `hoverColor` directly — so addon tabs snap to the hover color instead of animating via a per-tab `AnimatedValue` like built-in tabs (lines 710-713). Visually inconsistent with the rest of the strip.
**Fix:** Add a `Map<AddonNodeCategory, AnimatedValue> addonTabHoverAnimations` mirroring `tabHoverAnimations`, or drop the no-op `lerpColor` call and use `hoverColor` directly with a comment that animation is intentionally omitted.

### IN-02: Addon tab strip has no overflow handling

**File:** `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java:740-768`
**Issue:** Addon tabs render at `currentY + visibleTabIndex * (tabSize + tabSpacing)` and keep stacking downward with no clipping or scrolling; with enough addon categories installed they will render past the sidebar bottom (and remain hit-testable there). Built-in tabs share the limitation but have a fixed, known-to-fit count. Low impact for v1 (one addon), but the strip will need clipping or pagination once multiple addons register categories.
**Fix:** Clamp rendering/hit-testing at `sidebarStartY + sidebarHeight`, or document the supported category budget.

### IN-03: Javadoc cites hardcoded line numbers in another file

**File:** `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java:25-26,42,94`
**Issue:** The javadoc references `NodeGraphPersistence` "lines 854-882" and "lines 339-368" three times. These were already off by the time of this review (the actual branches sit at 339-369 / 853-883) and will rot further with any edit to that 1000+ line file.
**Fix:** Reference method names only (`buildNodeGraphData` / `convertToNodes`), not line numbers. Becomes moot if WR-06 is implemented.

---

_Reviewed: 2026-06-13T00:19:44Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
