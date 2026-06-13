---
phase: 01-api-foundation-script-node-registration
verified: 2026-06-13T08:00:00Z
status: gaps_found
score: 4/5 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 5/5
  gaps_closed:
    - "GAP-1: Addon category scrollbar now renders via computeAddonContentHeight + getCategoryScrollMetrics guard fix (Plan 01-07)"
    - "GAP-2: ADDON node display name resolved from registry in Node.getDisplayName() ADDON branch (Plan 01-08)"
    - "GAP-3 (partial): Default addonExtraFields seeded at constructor time via null-fields serializer path (Plan 01-08)"
    - "GAP-4: Scissor-clipped addon body preview + sidebar-overlap suppression in renderAddonNodeContent (Plan 01-09)"
    - "GAP-5: Missing-addon indicator with addonTypeId label in renderAddonPlaceholderBody (Plan 01-09)"
    - "WR-01/WR-02/WR-06 hardening: defensive copies, null-addonTypeId skip guards, NodeGraphPersistence delegation to AddonNodeDataCopy (Plan 01-10)"
  gaps_remaining:
    - "NEW-CR-02: restoreAddonFieldsToNode discards default-seeded extraFields when nodeData.getExtraFields() is null — breaks LUA-05 save/reload for freshly-placed nodes"
    - "NEW-CR-01: AddonLoader.failedAddons is a non-thread-safe LinkedHashMap — ConcurrentModificationException possible on concurrent init/UI-thread reads"
  regressions:
    - "NEW-WR-01: AddonNodePersistenceTest uses Java assert (not JUnit assertions) at lines 179-182, 221-222 — silently passes without -ea; WR-05: addonUnresolved never cleared in success branch"
gaps:
  - truth: "Script text persists with the node through preset save/load cycles (LUA-05)"
    status: failed
    reason: "restoreAddonFieldsToNode (AddonNodeDataCopy.java:121-126) discards default-seeded extraFields when nodeData.getExtraFields() is null. A freshly-placed node saved before any user edit has null extraFields on disk. On reload, ser.deserialize seeds DEFAULT_SCRIPT into ctx (null-fields path), but the guard at line 121 blocks setAddonExtraFields and the double-condition at line 124 evaluates false. The node reloads with addonExtraFields==null instead of the seeded default. This is the close-and-reopen failure path identified in 01-REVIEW.md CR-02."
    artifacts:
      - path: "common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java"
        issue: "Lines 121-126: null-extraFields guard blocks setAddonExtraFields even when ser.deserialize produced data via the null-fields path. Also: line 118-133 never calls node.setAddonUnresolved(false) in the success branch (WR-05), leaving nodes that were previously unresolved permanently showing the missing-addon indicator even after the addon becomes available."
    missing:
      - "Replace the null-extraFields guard with an unconditional base-map initialization: Map<String, Object> base = nodeData.getExtraFields() != null ? new HashMap<>(nodeData.getExtraFields()) : new HashMap<>(); node.setAddonExtraFields(base);"
      - "Add node.setAddonUnresolved(false) inside the try block of the success branch"
      - "Add a regression test asserting that restoreAddonFieldsToNode with null extraFields produces non-null addonExtraFields containing the seeded default"
human_verification:
  - test: "Launch Minecraft with both pathmind.jar and pathmind-lua-addon.jar. Open the Pathmind editor. Check the sidebar for an addon Scripting category."
    expected: "A Scripting tab appears in the sidebar with a Script node entry. The scrollbar renders for overflow. The node entry shows the display name from the addon definition (not 'Addon Node')."
    why_human: "Sidebar rendering is immediate-mode UI. GAP-1 scrollbar fix and GAP-2 display name fix are code-verified but visual output requires a running Minecraft client."
  - test: "Drag the Script node from the Scripting category onto the canvas."
    expected: "Node appears with correct title and default script content visible immediately (not empty). Node body renders within the node border and does not overdraw the sidebar drawer."
    why_human: "GAP-3 constructor seeding and GAP-4 scissor-clip are code-verified. Full drag path and visual z-ordering require a live game instance."
  - test: "Place a Script node, type script text, save preset, close and reopen the editor, reload the preset."
    expected: "Script node present after reload with script text intact. Note: this test should specifically cover the case of a freshly-placed node that has NOT been edited — the NEW-CR-02 bug means the default script may be lost on reload for never-edited nodes."
    why_human: "The CR-02 regression specifically affects freshly-placed nodes. Only in-game preset cycle can confirm whether the bug is triggered in practice with the Lua addon's specific serializer behavior."
  - test: "With a Script node in the graph, press Play. Observe HUD and log."
    expected: "Script node activates as the active node, executes (or graceful no-op), graph continues. No 'Skipping ADDON node with null addonTypeId' in log."
    why_human: "Execution path code-verified end-to-end. Runtime Lua VM, CompletableFuture poll, and HUD overlay require a running game."
  - test: "Launch Minecraft with only pathmind.jar. Open editor. Load a preset that previously contained a Script node."
    expected: "Pathmind opens normally. The orphaned Script node shows the missing-addon indicator (GAP-5 fix) with the addonTypeId label. No crash, no silent drop."
    why_human: "GAP-5 rendering code-verified. Runtime confirmation of standalone-mode behavior across 1.21-1.21.11 requires actual game launches."
---

# Phase 01: Verification Report (Re-verification — Round 3)

**Phase Goal:** The Pathmind addon API is published as a consumable Maven artifact, addon discovery is wired into mod init, and the sibling addon repo ships a Script node that is palette-visible, placeable, and persistable — both mods load cleanly and Pathmind works standalone without the addon jar.
**Verified:** 2026-06-13
**Status:** gaps_found
**Re-verification:** Yes — after gap-closure wave Plans 01-07 through 01-10; fresh code review 01-REVIEW.md introduces NEW-CR-02 as a blocker

---

## Gap-Closure Wave Assessment (Plans 01-07..01-10)

The five UAT gaps and the three hardening items were all addressed by Plans 01-07 through 01-10. All code-level changes are substantive and correctly wired. The `AddonSidebarScrollTest` (4 tests), `AddonNodeCreationTest` (4 tests), and `AddonNodeAliasingTest` (4 tests) were added and pass. However, the fresh code review (01-REVIEW.md) landing simultaneously with this verification identifies a residual correctness bug (CR-02) in the gap-closure code itself that re-opens LUA-05.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An addon declaring a "pathmind" entrypoint has registerNodes called at Pathmind mod init, with informative errors on malformed registration | ✓ VERIFIED | Unchanged from previous passes. AddonLoader.discoverAndLoad() at PathmindMod.onInitialize line 31; FabricLoader entrypoint discovery; per-addon catch(Throwable) logs addonId; NodeTypeRegistrar validates id format, null, duplicate, post-seal. |
| 2 | com.pathmind:pathmind-fabric Maven artifact can be added via modCompileOnly with zero impl classes forced onto the addon classpath | ~ PARTIAL (carried) | Artifact published at ~/.m2; addon compiles against com.pathmind.api.* only; boundary is convention-only (WR-09). Accepted as verified for scoring purposes — unchanged from previous pass. |
| 3 | User can drag a Script node from the editor palette, connect it in a graph, run the graph, and the Script node passes through execution without error | ✓ VERIFIED (code) | Sidebar.java CR-01 fix (prev pass) still in place. GAP-1 scrollbar: Plan 01-07 added computeAddonContentHeight + getCategoryScrollMetrics guard fix + renderCategoryScrollbar call. GAP-2 display name: Plan 01-08 ADDON branch in Node.getDisplayName(). GAP-3 constructor seeding: Plan 01-08 Node(String,int,int) calls ser.deserialize(ctx,null). GAP-4 scissor: Plan 01-09 enableScissor/disableScissor in renderAddonNodeContent + sidebar-overlap suppression. All code-verified. In-game: human_needed. |
| 4 | A saved preset containing a Script node round-trips through save/load with script text and _schema_version intact | ✗ FAILED | NEW-CR-02 (01-REVIEW.md): restoreAddonFieldsToNode (AddonNodeDataCopy.java:121-126) discards default-seeded extraFields when nodeData.getExtraFields() is null. A freshly-placed node saved before any user edit reloads with addonExtraFields==null. GAP-3 constructor seeding (Plan 01-08) only fixes the at-creation path; the close-and-reopen path through restoreAddonFieldsToNode is broken for the null-extraFields edge case. See also WR-05: addonUnresolved never cleared in success branch, potentially leaving restored nodes displaying as missing-addon permanently. |
| 5 | Launching Minecraft without the addon jar leaves Pathmind fully functional across its 1.21-1.21.11 range with no errors | ✓ VERIFIED | Empty entrypoint list path unchanged. GAP-5 missing-addon indicator (Plan 01-09) added visual feedback on orphaned ADDON nodes without breaking standalone mode. Runtime confirmation: human_needed. |

**Score: 4/5 truths verified at code level** (Truth 4 fails due to NEW-CR-02)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java` | Canonical ADDON copy helper | ✗ DEFECTIVE | File exists, 141 lines, both methods present and called at all four sites. CR-02: restoreAddonFieldsToNode lines 121-126 discard default-seeded ctx data when extraFields is null. WR-05: addonUnresolved never cleared in success branch (line 118-132 try block). These are correctness bugs in an otherwise substantive file. |
| `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java` | Addon scrollbar, display name lookup, hover/drag wiring | ✓ VERIFIED | computeAddonContentHeight static helper added. calculateMaxScroll addon branch rewritten with wrap-aware heights. getCategoryScrollMetrics guard fixed (selectedAddonCategory == null). renderCategoryScrollbar called in addon pass. Plan 01-08 display name from registry wired in Node.getDisplayName(), not Sidebar. |
| `common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarScrollTest.java` | 4 tests for computeAddonContentHeight | ✓ VERIFIED | File exists, 4 @Test methods. Tests cover single-line header, wrapped row, empty rows, multi-line header. |
| `common/src/main/java/com/pathmind/nodes/Node.java` | ADDON getDisplayName branch + constructor seeding | ✓ VERIFIED | getDisplayName() ADDON branch at line 718-726; constructor Node(String,int,int) seeds addonExtraFields via ser.deserialize(ctx,null) at lines 400-405 with try/catch(Throwable). |
| `common/src/test/java/com/pathmind/nodes/AddonNodeCreationTest.java` | 4 tests for display name and constructor seeding | ✓ VERIFIED | File exists, 4 @Test methods covering registered display name, unregistered fallback, constructor seeding, non-ADDON unchanged. Uses reflection-based registry injection to bypass install-once guard. |
| `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` | Scissor-clipped addon body; missing-addon indicator | ✓ VERIFIED | renderAddonNodeContent: enableScissor/disableScissor wrapping renderer.render call; sidebar-overlap suppression via x < sidebarWidthForRendering guard. renderAddonPlaceholderBody: accepts TextRenderer + addonTypeId, draws "warning addon missing" and addonTypeId label. |
| `common/src/test/java/com/pathmind/data/AddonNodeAliasingTest.java` | 4 tests for defensive-copy and null-skip | ✓ VERIFIED | File exists. Tests cover aliasing regression for placeholder copy/restore using assertNotSame. |
| `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` | ADDON branches delegated to AddonNodeDataCopy | ✓ VERIFIED | Plan 01-10: buildNodeGraphData and convertToNodes ADDON branches now delegate to AddonNodeDataCopy. Unused imports (AddonNodeContext, AddonNodeSerializer, NodeTypeRegistry) removed. |
| `common/src/main/java/com/pathmind/execution/ExecutionManager.java` | Null-addonTypeId skip guard before addToSnapshot | ✓ VERIFIED | Skip guard before snapshot.getNodes().add(nodeData); AddonNodeDataCopy.copyAddonFieldsToNodeData call retained. |
| `common/src/main/java/com/pathmind/ui/graph/NodeGraphClipboardSupport.java` | Null-addonTypeId skip guard in copy loop | ✓ VERIFIED | Skip guard before NodeData construction in clipboard copy loop. |
| All artifacts from previous passing sets | (unchanged) | ✓ VERIFIED | AddonLoader, NodeTypeRegistrar, NodeTypeRegistry, PathmindApiVersion, NodeType.ADDON, NodeGraphData addon fields, PathmindScreens.surfaceAddonLoadFailures, maven-publish config, fabric.mod.json pathmind entrypoint, LuaAddonEntrypoint, LuaNodeSerializer, LuaNodeExecutor — confirmed passing in previous passes; no gap-closure plan touched these files. |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `PathmindMod.onInitialize` | `AddonLoader.discoverAndLoad()` | call at line 31 | ✓ WIRED | Unchanged |
| `AddonLoader.java` | `PathmindApiVersion.MIN_COMPATIBLE` | checkApiCompatibility | ✓ WIRED | Unchanged |
| `AddonLoader.java` | `NodeTypeRegistry.INSTANCE.install` | line 88 | ✓ WIRED | Unchanged |
| `Node.java execute(int)` | `NodeTypeRegistry.INSTANCE.executorFor` | executeAddonNode() | ✓ WIRED | addonTypeId survives snapshot via AddonNodeDataCopy at ExecutionManager line 3011 |
| `NodeGraph.applyLoadedData` | `AddonNodeDataCopy.restoreAddonFieldsToNode` | line 15701 | ✓ WIRED (defective) | Call is wired; CR-02 bug within restoreAddonFieldsToNode discards data in null-extraFields case |
| `ExecutionManager.createGraphSnapshot` | `AddonNodeDataCopy.copyAddonFieldsToNodeData` | line 3011 | ✓ WIRED | null-addonTypeId skip guard added before this call |
| `NodeGraphPersistence.buildNodeGraphData` | `AddonNodeDataCopy.copyAddonFieldsToNodeData` | delegation (Plan 01-10) | ✓ WIRED | Single canonical encoding |
| `NodeGraphPersistence.convertToNodes` | `AddonNodeDataCopy.restoreAddonFieldsToNode` | delegation (Plan 01-10) | ✓ WIRED (defective) | Delegates correctly; CR-02 bug is inside restoreAddonFieldsToNode |
| `Sidebar render loop` | `addonCategoryNodes` iteration + scrollbar | lines 741, 1015; renderCategoryScrollbar | ✓ WIRED | GAP-1 closed |
| `Node.getDisplayName() ADDON branch` | `NodeTypeRegistry.INSTANCE.definitionFor` | lines 718-726 | ✓ WIRED | GAP-2 closed |
| `Node(String,int,int) constructor` | `ser.deserialize(ctx, null)` seeding | lines 400-405 | ✓ WIRED | GAP-3 (creation path) closed |
| `NodeGraph.renderAddonNodeContent` | scissor + sidebar-overlap guard | enableScissor/disableScissor + sidebarWidthForRendering check | ✓ WIRED | GAP-4 closed |
| `NodeGraph.renderAddonPlaceholderBody` | missing-addon text with addonTypeId | draws "warning addon missing" + addonTypeId | ✓ WIRED | GAP-5 closed |
| `pathmind-lua/build.gradle.kts` | `com.pathmind:pathmind-fabric` via `modCompileOnly` | mavenLocal() | ✓ WIRED | Unchanged |
| `fabric.mod.json (pathmind-lua)` | `LuaAddonEntrypoint` via "pathmind" entrypoint | entrypoints block | ✓ WIRED | Unchanged |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `Node.addonExtraFields` on reload path | `addonExtraFields` | `AddonNodeDataCopy.restoreAddonFieldsToNode` | **No** when extraFields is null (newly-placed node before any edit) | ✗ HOLLOW — restoreAddonFieldsToNode null-extraFields guard discards ctx data; node reloads with addonExtraFields==null (NEW-CR-02) |
| `Node.addonExtraFields` on creation path | `addonExtraFields` | `Node(String,int,int)` constructor via `ser.deserialize(ctx, null)` | Yes | ✓ FLOWING — constructor seeding (Plan 01-08) correctly seeds default extraFields at node creation |
| `Sidebar addon content panel` | `addonCategoryNodes.get(selectedAddonCategory)` | `NodeTypeRegistry.INSTANCE.allDefinitions()` filtered by category | Yes (when addon installed) | ✓ FLOWING — GAP-1 scrollbar wired; wrap-aware heights computed |
| `Node.getDisplayName() ADDON branch` | display name from `definitionFor(addonTypeId)` | `NodeTypeRegistry.INSTANCE` | Yes (when registered) | ✓ FLOWING — GAP-2 resolved |
| `Node.executeAddonNode` → executor | `addonTypeId` from snapshot | `AddonNodeDataCopy.copyAddonFieldsToNodeData` at ExecutionManager line 3011 | Yes | ✓ FLOWING — unchanged from previous pass |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| AddonNodeAliasingTest (4 tests — WR-01/WR-02/WR-06) | `./gradlew.bat :common:test --tests "com.pathmind.data.AddonNodeAliasingTest"` | BUILD SUCCESSFUL (per Plan 01-10 SUMMARY) | ✓ PASS (claimed by SUMMARY) |
| AddonNodeCreationTest (4 tests — GAP-2/GAP-3) | `./gradlew.bat :common:test --tests "com.pathmind.nodes.AddonNodeCreationTest"` | BUILD SUCCESSFUL (per Plan 01-08 SUMMARY — 219 tests, 0 failures) | ✓ PASS (claimed by SUMMARY) |
| AddonSidebarScrollTest (4 tests — GAP-1) | `./gradlew.bat :common:test --tests "com.pathmind.ui.sidebar.AddonSidebarScrollTest"` | BUILD SUCCESSFUL (per Plan 01-07 SUMMARY) | ✓ PASS (claimed by SUMMARY) |
| AddonNodePersistenceTest — null extraFields reload path | Not covered by existing tests | No test exercises restoreAddonFieldsToNode with null extraFields | ✗ FAIL — CR-02 regression has no test gate |
| In-game sidebar, drag, preset, execution, standalone | Requires running Minecraft client | Cannot test headlessly | ? SKIP → human_needed |

---

### Probe Execution

Step 7c: No probes declared in any PLAN file. No conventional `scripts/*/tests/probe-*.sh` found in the repository.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| API-01 | Plan 01-01 | Addon declares "pathmind" entrypoint; registerNodes called | ✓ SATISFIED | Unchanged from previous passes |
| API-02 | Plan 01-01 | Addon can register custom node types through a typed registrar | ✓ SATISFIED | Unchanged |
| API-03 | Plan 01-01 | Registration validated at load time with informative errors naming the addon | ✓ SATISFIED | Unchanged |
| API-04 | Plan 01-01 | Lifecycle ordering guaranteed; sealed after init | ✓ SATISFIED | Unchanged |
| API-05 | Plans 01-02, 01-04, 01-10 | Addon nodes persist opaque schema-versioned blob | ✗ PARTIALLY BROKEN | restoreAddonFieldsToNode CR-02 breaks LUA-05 for null-extraFields edge case. Plan 01-10 delegation and defensive copies are correct; the residual bug is in restoreAddonFieldsToNode itself. |
| API-06 | Plans 01-02, 01-04 | Addon executors run asynchronously via CompletableFuture | ✓ SATISFIED | Execution path unchanged; snapshot data flow verified |
| API-07 | Plans 01-02, 01-09 | Addon nodes can render custom content via body renderer hook | ✓ SATISFIED | GAP-4 scissor fix ensures renderer content stays within node bounds |
| API-08 | Plan 01-03 | Separate API artifact published; convention-only boundary | ~ PARTIAL | Unchanged — artifact exists, WR-09 design limitation |
| API-09 | All plans | Pathmind runs unchanged with no addons | ✓ SATISFIED | GAP-5 indicator: graceful degradation improved; standalone path unaffected |
| API-10 | Plans 01-03, 01-06 | Addon API documented for third parties | ✓ SATISFIED | getting-started.md correct Fabric API version; unchanged |
| LUA-01 | Plans 01-02, 01-05, 01-07..01-09 | User can grab Script node from palette and place it | ✓ SATISFIED (code) | GAP-1/GAP-2/GAP-3/GAP-4/GAP-5 all addressed. In-game: human_needed |
| LUA-05 | Plans 01-02, 01-04, 01-08, 01-10 | Script text persists through save/load with _schema_version | ✗ PARTIALLY BROKEN | CR-02: freshly-placed node reloads with addonExtraFields==null when saved before any user edit (null extraFields on disk). Plan 01-08 constructor seeding fixes creation; restoreAddonFieldsToNode null-extraFields path is not fixed. |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AddonNodeDataCopy.java` | 121-126 | null-extraFields guard discards ctx data after successful deserialize | Blocker | CR-02: LUA-05 broken for freshly-placed nodes on reload |
| `AddonNodeDataCopy.java` | 118-132 | No `node.setAddonUnresolved(false)` in success branch | Warning | WR-05: nodes that were ever unresolved permanently display as missing-addon even after addon becomes available |
| `AddonLoader.java` | 48 | `static final LinkedHashMap` shared across init and UI threads | Warning | CR-01 (review): ConcurrentModificationException possible if addon init and UI-thread getFailedAddons overlap |
| `AddonNodePersistenceTest.java` | 179-182, 221-222 | Java `assert` instead of JUnit assertions | Warning | WR-01 (review): critical JSON-field presence checks silently pass without `-ea`; test passes even if JSON is missing addonTypeId, _schema_version, or extraFields |

No TBD/FIXME/XXX debt markers found in any gap-closure file.

Pre-existing warnings from previous passes (not introduced by gap closure):

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `NodeTypeRegistrar.java` | `seal()` is public | Warning | WR-06 (original): one bad addon can block all subsequent addons |
| `NodeGraph.java` | Warn logged unconditionally per frame on renderer failure | Warning | WR-04: log flood at 60+/sec; no rate limiting |
| `NodeTypeRegistrar.java` | Regex allows path-traversal characters in name segment | Warning | WR-03: `../../../evil` passes id validation |
| `AddonLoader.java` | Fabric-only imports in common module | Warning | WR-11: latent NoClassDefFoundError on NeoForge |
| `Node.java` | whenComplete on arbitrary addon thread touches MC client state | Warning | WR-04: latent race/crash when Phase 2 Lua VM completes from worker thread |

---

### Human Verification Required

#### 1. Addon Category Scrollbar and Display Name

**Test:** Launch Minecraft with both jars. Open the editor sidebar. Expand the addon Scripting category. If there are more entries than fit in the sidebar, scroll the category.
**Expected:** Scripting tab appears; entries show the addon-registered display name (e.g. "Lua Script" not "Addon Node"). Scrollbar renders and is draggable. Bottom entries are accessible.
**Why human:** GAP-1 and GAP-2 fixes are code-verified (computeAddonContentHeight, getCategoryScrollMetrics guard, Node.getDisplayName ADDON branch). Visual output and scrollbar drag behavior require a running Minecraft client.

#### 2. Script Node Drag with Default Content and Scissor Rendering

**Test:** Hover over the Script node entry and drag it to the canvas. Observe the node title, body content, and z-ordering relative to the sidebar drawer.
**Expected:** Node title shows addon display name (not "Addon Node"). Default script content is visible immediately on the newly-placed node (not empty). Node body does not overdraw the sidebar drawer.
**Why human:** GAP-3 constructor seeding and GAP-4 scissor-clip are code-verified. Drag path, default content visibility, and z-ordering require a live game.

#### 3. Preset Round-Trip — Specifically for Freshly-Placed Never-Edited Node

**Test:** Place a Script node WITHOUT typing any script text. Save the preset immediately. Close the editor and the game. Restart the game, open the preset. Observe the script node content.
**Expected (with CR-02 unfixed):** The script node may reload with empty/null addonExtraFields instead of the default script. This is the edge case CR-02 describes. Note whether the default script is present or absent after reload — this confirms whether CR-02 is triggered in practice.
**Test also:** Place a Script node, type some text, save, reload.
**Expected:** Typed text survives reload (the normal case works — only null-extraFields edge case is broken).
**Why human:** CR-02 is specifically about the null-extraFields code path. Need to confirm whether LuaNodeSerializer.deserialize with null fields does in fact produce non-null ctx scriptText (i.e., whether DEFAULT_SCRIPT is set), and whether that data loss manifests as an observable defect in the game.

#### 4. Execution Pass-Through

**Test:** Place a Script node (with or without script text). Press Play.
**Expected:** Script node activates, runs (or graceful no-op), graph continues. No "Skipping ADDON node with null addonTypeId" in log.
**Why human:** Execution path code-verified. CompletableFuture lifecycle and HUD overlay require a running game.

#### 5. Standalone Mode — Missing-Addon Indicator

**Test:** Remove the addon jar, restart Minecraft, open a preset that contained a Script node.
**Expected:** Pathmind loads normally. The orphaned Script node shows the "warning addon missing" indicator with the addon type id label (GAP-5 fix). No crash, no silent drop.
**Why human:** GAP-5 code-verified. Runtime confirmation across 1.21-1.21.11 requires actual game launches.

---

### Gaps Summary

**1 automated gap remains, blocking LUA-05:**

**NEW-CR-02 (BLOCKER) — restoreAddonFieldsToNode discards default-seeded extraFields**

`AddonNodeDataCopy.restoreAddonFieldsToNode` (lines 121-126) contains a null-extraFields guard that blocks `node.setAddonExtraFields()` when `nodeData.getExtraFields()` is null. This is the case for any node saved to disk before the user types any script text — `ser.serialize(ctx)` with an empty/default script may produce a map, but if the node was serialized before the addon was ever called, the on-disk extraFields can be null.

On reload, `ser.deserialize(ctx, null)` correctly seeds DEFAULT_SCRIPT into ctx via the null-fields path. But step (2) blocks `setAddonExtraFields` and step (3)'s double-condition evaluates false, so `addonExtraFields` stays null. The LuaScriptNodeRenderer then renders empty content until the user triggers a re-serialization by editing the node.

This breaks the GAP-3 guarantee on the close-and-reopen path (only creation-time seeding was fixed by Plan 01-08, not the reload path). There is no regression test for this path — the existing AddonNodeConversionRoundTripTest.editorLoad_restoreAddonFieldsToNode uses a NodeData with pre-populated extraFields.

Additionally, WR-05 (addonUnresolved never cleared in success branch) means any node that was ever marked unresolved (e.g., loaded while the addon was absent, then the addon is installed) will permanently show the "missing-addon" indicator after reload. Both issues are in `restoreAddonFieldsToNode` and can be fixed in the same targeted pass.

**Supporting warning (not blocking by itself):**

CR-01 (`AddonLoader.failedAddons` LinkedHashMap) affects the error-display path only. The happy-path load sequence is single-threaded (Fabric mod init is sequenced). This is a correctness issue for failure UX, not for the phase goal. It is surfaced as a WARNING — a targeted fix is recommended before Phase 2 increases concurrency.

WR-01 (Java `assert` in `AddonNodePersistenceTest`) means 6 critical JSON-field assertions silently pass without `-ea`. These assertions are the only automated proof that addonTypeId and extraFields appear in serialized JSON. The assertions must be replaced with JUnit assertions (`assertTrue`/`assertFalse`) to make them executable.

---

_Verified: 2026-06-13_
_Re-verification round 3: after gap-closure Plans 01-07 (scrollbar), 01-08 (display name + default seeding), 01-09 (scissor clip + missing-addon indicator), 01-10 (defensive copies + null-skip + persistence delegation); fresh code review 01-REVIEW.md (CR-02 re-opens LUA-05)_
_Verifier: Claude (gsd-verifier)_
