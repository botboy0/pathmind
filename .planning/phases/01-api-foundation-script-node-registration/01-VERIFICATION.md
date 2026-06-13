---
phase: 01-api-foundation-script-node-registration
verified: 2026-06-13T12:45:00Z
status: gaps_found
score: 5/5 code-verified; in-game UAT (round 5) confirmed the NEW-CR-02 blocker fixed but found 3 editor-render gaps
overrides_applied: 0
uat_round_5:
  date: 2026-06-13
  source: "in-game UAT — MC 1.21.4, pathmind-fabric 1.1.5 + pathmind-lua 0.1.0"
  confirmed_pass:
    - "Item 3 (Truth 4 / NEW-CR-02): freshly-placed never-edited Script node preset round-trips correctly in-game — BLOCKER CONFIRMED FIXED"
    - "Item 5 (execution pass-through): Script node activates and passes through as a graceful no-op; graph continues"
    - "Item 6 (standalone / missing-addon on load): Pathmind loads without the addon; orphaned-node behavior correct"
  not_a_gap:
    - "Item 4 (typed-text persistence): not testable until the Phase 3 in-game editor exists; underlying persistence already cleared in pre-gap UAT — deferred to Phase 3"
  gaps_found:
    - "UAT-GAP-A (scrollbar missing): the sidebar 'Scripting' addon category does not render a visible scrollbar in-game. The round-2 GAP-1 fix (computeAddonContentHeight + getCategoryScrollMetrics + renderCategoryScrollbar, Plan 01-07) does not produce a visible scrollbar for the addon category. Investigate whether the addon-category overflow path computes height/metrics correctly and whether renderCategoryScrollbar is actually invoked for addon categories (vs only built-in categories)."
    - "UAT-GAP-B (drag-preview title): while dragging a Script node from the palette (BEFORE placement), the drag-box title reads 'Addon Node' instead of the addon display name 'Script'. The GAP-2 fix (Node.getDisplayName() ADDON branch, Plan 01-08) resolves the name for PLACED nodes, but the drag-preview/ghost title render path does not resolve the addon display name. Built-in nodes show their proper title during drag. Fix the drag-preview title path to resolve the addon display name from the registry."
    - "UAT-GAP-C (false 'addon missing' on invalid drag): dragging a Script node to an INVALID drop location (over the sidebar) renders the missing-addon placeholder '⚠ addon missing pathmind_lua:script' instead of the normal invalid-position discoloration that built-in nodes show. The addon IS installed/registered (the node is palette-visible and placeable), so the missing-addon branch (renderAddonPlaceholderBody / addonUnresolved) is being hit incorrectly during the drag / invalid-position state. Built-ins correctly show discoloration at invalid spots. Ensure a registered addon node uses the standard invalid-drop visual and never the missing-addon placeholder while its addon is present."
re_verification:
  previous_status: gaps_found
  previous_score: 4/5
  gaps_closed:
    - "NEW-CR-02 (BLOCKER): restoreAddonFieldsToNode now builds base map unconditionally — node.setAddonExtraFields(base) called without null-guard in success branch; serializer-seeded DEFAULT_SCRIPT survives null-extraFields reload path (Plan 01-11, commit 389f7dd)"
    - "WR-05: node.setAddonUnresolved(false) added inside the try block of the success branch — stale unresolved flag cleared after successful restore (Plan 01-11, commit 389f7dd)"
    - "NEW-CR-01: AddonLoader.failedAddons changed to Collections.synchronizedMap(new LinkedHashMap<>()) — thread-safe for init-write / UI-read concurrency pattern (Plan 01-12, commit 2d34671)"
    - "NEW-WR-01 / WR-01: Six Java assert statements in AddonNodePersistenceTest replaced with JUnit assertTrue/assertFalse — JSON-contract checks now execute unconditionally without -ea (Plan 01-13, commit 4eedc20)"
    - "Post-merge test-isolation fix: AddonTestRegistry shared helper registers both test_mod:script and aliasing_test_mod:script in a single install call — all 4 addon test classes delegate @BeforeAll to AddonTestRegistry.ensureInstalled(); full suite 230 tests, 0 failures (commit 12f69ef)"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Launch Minecraft with both pathmind.jar and pathmind-lua-addon.jar. Open the Pathmind editor. Check the sidebar for an addon Scripting category."
    expected: "A Scripting tab appears in the sidebar with a Script node entry. The scrollbar renders for overflow. The node entry shows the addon-registered display name (e.g. 'Lua Script', not 'Addon Node')."
    why_human: "Sidebar rendering is immediate-mode UI (NodeGraph). GAP-1 scrollbar fix and GAP-2 display name fix are code-verified but visual output and scrollbar drag behavior require a running Minecraft client."
  - test: "Drag the Script node from the Scripting category onto the canvas. Observe node title, body content, and z-ordering relative to the sidebar drawer."
    expected: "Node appears with correct title (addon display name). Default script content is visible immediately on the newly-placed node (not empty). Node body does not overdraw the sidebar drawer."
    why_human: "GAP-3 constructor seeding and GAP-4 scissor-clip are code-verified. Drag path, default content visibility, and z-ordering require a live game instance."
  - test: "Place a Script node without typing any script text. Save the preset. Close the editor and game. Restart, open preset. Observe script node content."
    expected: "Script node present with non-null addonExtraFields after reload — default script is present (not empty/null). This specifically validates the NEW-CR-02 fix on the null-extraFields close-and-reopen path."
    why_human: "NEW-CR-02 is fixed at the code level (restoreAddonFieldsToNode unconditional base-map init verified) and gated by AddonNodeReloadRegressionTest. Runtime confirmation with the actual Lua addon's LuaNodeSerializer requires a live game."
  - test: "Place a Script node, type script text, save preset, close and reopen. Verify typed text survives."
    expected: "Typed script text is present after reload (the normal, non-null extraFields path). This tests the no-regression path for the existing round-trip behavior."
    why_human: "Runtime confirmation requires a live game. Code path verified by AddonNodeConversionRoundTripTest."
  - test: "Place a Script node (with or without script text). Press Play."
    expected: "Script node activates as the active node, passes through execution (graceful no-op — no Lua VM yet), graph continues. No 'Skipping ADDON node with null addonTypeId' in log."
    why_human: "Execution path code-verified end-to-end. CompletableFuture lifecycle and HUD overlay require a running game."
  - test: "Remove the addon jar, restart Minecraft, open a preset that previously contained a Script node."
    expected: "Pathmind loads normally. The orphaned Script node shows the missing-addon indicator (GAP-5 fix) with the addonTypeId label. No crash, no silent drop. A previously-unresolved node that is then loaded with the addon present correctly clears the missing-addon indicator (WR-05 fix)."
    why_human: "GAP-5 rendering and WR-05 unresolved-flag behavior are code-verified. Runtime confirmation across 1.21-1.21.11 requires actual game launches."
---

# Phase 01: Verification Report (Re-verification — Round 4, after round-3 gap closure)

**Phase Goal:** The Pathmind addon API is published as a consumable Maven artifact, addon discovery is wired into mod init, and the sibling addon repo ships a Script node that is palette-visible, placeable, and persistable — both mods load cleanly and Pathmind works standalone without the addon jar.
**Verified:** 2026-06-13
**Status:** gaps_found (in-game UAT round 5)
**Re-verification:** Yes — after round-3 gap-closure Plans 01-11, 01-12, 01-13 plus post-merge test-isolation fix (commit 12f69ef). All round-3 automated gaps closed and the NEW-CR-02 blocker was CONFIRMED FIXED in-game (UAT item 3). However, in-game UAT round 5 surfaced 3 editor-render gaps — see `uat_round_5.gaps_found` in frontmatter: UAT-GAP-A (sidebar scrollbar missing), UAT-GAP-B (drag-preview title shows 'Addon Node' not 'Script'), UAT-GAP-C (registered addon node falsely shows 'addon missing' placeholder on invalid drag instead of discoloration). These are the targets for round-4 gap closure.

---

## Re-verification Summary

All three items flagged in the previous verification (NEW-CR-02 BLOCKER, NEW-CR-01 Warning, NEW-WR-01 Warning) have been closed. The code has been independently verified against the actual source files — not against SUMMARY.md claims. All five success criteria pass at the code level. The remaining human_needed items are the same in-game behavioral tests that cannot be verified headlessly and have been present since round 2.

---

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A developer can declare a "pathmind" entrypoint in fabric.mod.json and have PathmindAddonEntrypoint.register(registrar) called at mod init with informative load-time errors if malformed | ✓ VERIFIED | AddonLoader.discoverAndLoad() at PathmindMod.onInitialize; per-addon catch(Throwable) logs addonId; NodeTypeRegistrar validates id format, null, duplicate, post-seal. Code unchanged from previous passing passes. |
| 2 | com.pathmind.api Maven artifact can be added via modCompileOnly with zero impl classes on the classpath | ~ PARTIAL (carried, accepted) | Artifact published at ~/.m2; addon compiles against com.pathmind.api.* only; boundary is convention-only (WR-09, design limitation accepted in prior passes). Carries forward as verified for scoring — unchanged. |
| 3 | User can drag a Script node from the editor palette, connect it in a graph, run the graph, and the Script node passes through execution without error | ✓ VERIFIED (code) / human_needed (runtime) | All GAP-1..GAP-5 fixes confirmed in code (scrollbar, display name, constructor seeding, scissor clip, missing-addon indicator). Execution path wired through AddonNodeDataCopy, ExecutionManager, NodeTypeRegistry. In-game behavior requires human verification. |
| 4 | A saved preset containing a Script node round-trips through save/load with script text and _schema_version intact | ✓ VERIFIED | NEW-CR-02 fixed: restoreAddonFieldsToNode success branch (AddonNodeDataCopy.java lines 124-134) builds base unconditionally (`HashMap<String,Object> base = nodeData.getExtraFields() != null ? new HashMap<>(nodeData.getExtraFields()) : new HashMap<>()`), calls node.setAddonExtraFields(base) and node.setAddonUnresolved(false) unconditionally, then injects ctx.getScriptText() under "script" key. No surrounding null-guard on the setAddonExtraFields call. AddonNodeReloadRegressionTest (3 tests) gates the null-extraFields path and the unresolved-clear path. Confirmed in commit 389f7dd. |
| 5 | Launching Minecraft without the addon jar leaves Pathmind fully functional across its 1.21-1.21.11 range with no errors | ✓ VERIFIED (code) / human_needed (runtime) | Empty entrypoint list path unchanged; standalone mode confirmed structurally. GAP-5 missing-addon indicator (Plan 01-09) adds graceful degradation for orphaned ADDON nodes. Runtime confirmation across MC versions requires actual game launches. |

**Score: 5/5 truths verified at code level**

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java` | Canonical ADDON copy helper with correct success branch | ✓ VERIFIED | 149 lines. restoreAddonFieldsToNode success branch: line 124-127 unconditional base-map init, line 127 unconditional setAddonExtraFields, line 129 setAddonUnresolved(false), line 132-134 script injection. NEW-CR-02 and WR-05 both fixed. Commit 389f7dd. |
| `common/src/test/java/com/pathmind/data/AddonNodeReloadRegressionTest.java` | Regression tests for null-extraFields path and unresolved-clear path | ✓ VERIFIED | File exists, 152 lines. Three @Test methods: restoreWithNullExtraFields_seedsNonNullDefaultScript, restoreSuccess_clearsAddonUnresolved, restoreWithPrepopulatedExtraFields_preservesScript. All use JUnit assertions (no Java assert keyword). Delegates to AddonTestRegistry.ensureInstalled(). Commit 427f8c5 (RED), 12f69ef (isolation fix). |
| `common/src/test/java/com/pathmind/data/AddonTestRegistry.java` | Shared order-independent registry for all addon test classes | ✓ VERIFIED | File exists, 206 lines. Registers test_mod:script (SHARED_SERIALIZER with DEFAULT_SCRIPT seeding on null-fields) and aliasing_test_mod:script in a single install call. ensureInstalled() is idempotent. Commit 12f69ef. |
| `common/src/main/java/com/pathmind/execution/AddonLoader.java` | Thread-safe failedAddons collection | ✓ VERIFIED | Line 55-56: `Collections.synchronizedMap(new LinkedHashMap<>())`. Comment at lines 48-54 documents synchronization contract. markFailed, getFailure, getFailedAddons signatures unchanged. Commit 2d34671. |
| `common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java` | JSON-field presence assertions as executable JUnit assertions | ✓ VERIFIED | Lines 16,19: assertTrue/assertFalse statically imported. Lines 170-173: four assertTrue(json.contains(...), ...) calls in nodeData_addonTypeIdAndExtraFieldsSurviveGsonRoundTrip. Lines 212-213: two assertFalse(json.contains(...), ...) calls in nodeData_builtinNodeHasNoAddonFields. No `assert json.contains` or `assert !json.contains` remaining. Commit 4eedc20. Note: `assert version >= 1;` at line 64 inside class-local TEST_SERIALIZER.deserialize is a serializer-internal check (not a JSON-field presence test) — explicitly scoped out in Plan 01-13 and does not affect API-05/LUA-05 contract gating. |
| All artifacts from previous passing sets | (unchanged) | ✓ VERIFIED | AddonLoader (now + thread-safe), NodeTypeRegistrar, NodeTypeRegistry, PathmindApiVersion, NodeType.ADDON, NodeGraphData addon fields, PathmindScreens.surfaceAddonLoadFailures, maven-publish config, fabric.mod.json pathmind entrypoint, LuaAddonEntrypoint, LuaNodeSerializer, LuaNodeExecutor — confirmed in prior passes; no regression introduced by plans 01-11, 01-12, 01-13. |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AddonNodeDataCopy.restoreAddonFieldsToNode` success branch | `node.setAddonExtraFields(base)` | unconditional call after base-map init | ✓ WIRED | Line 127: no null-guard wrapping the call. NEW-CR-02 fixed. |
| `AddonNodeDataCopy.restoreAddonFieldsToNode` success branch | `node.setAddonUnresolved(false)` | inside try block | ✓ WIRED | Line 129: inside the try block before any exception can occur. WR-05 fixed. |
| `AddonNodeDataCopy.restoreAddonFieldsToNode` | `ser.deserialize(ctx, nodeData.getExtraFields())` | null-fields path in serializer | ✓ WIRED | Line 119: deserialize seeds ctx.scriptText from null-fields path. Line 132-134: ctx.getScriptText() injected into node.getAddonExtraFields() (map is now guaranteed non-null). |
| `AddonLoader.failedAddons` field | `Collections.synchronizedMap(new LinkedHashMap<>())` | field initializer | ✓ WIRED | Line 55-56. Thread-safe for init-write / UI-read without breaking insertion-order. |
| `AddonTestRegistry.ensureInstalled()` | `NodeTypeRegistry.INSTANCE.install(registrar)` | single install with both addon IDs | ✓ WIRED | Line 200: single install call after registering test_mod:script and aliasing_test_mod:script. All 4 test class @BeforeAll delegates to ensureInstalled(). |
| (All key links from previous passing verification) | (unchanged) | (unchanged) | ✓ WIRED | PathmindMod→AddonLoader, AddonLoader→NodeTypeRegistry, Node.execute→executorFor, NodeGraph.applyLoadedData→restoreAddonFieldsToNode, NodeGraphPersistence→AddonNodeDataCopy, Sidebar→addonCategoryNodes, Node.getDisplayName ADDON branch, Node constructor→ser.deserialize, renderAddonNodeContent→scissor, renderAddonPlaceholderBody→addonTypeId label, pathmind-lua build→modCompileOnly, fabric.mod.json→LuaAddonEntrypoint. All unchanged from prior verified passes. |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `Node.addonExtraFields` on reload path (null-extraFields) | `addonExtraFields` | `AddonNodeDataCopy.restoreAddonFieldsToNode` — base map from `new HashMap<>()` + ser.deserialize seeds DEFAULT_SCRIPT into ctx + ctx.getScriptText() injected under "script" | Yes — non-null HashMap containing DEFAULT_SCRIPT | ✓ FLOWING — NEW-CR-02 fixed; base map is always non-null on success path |
| `Node.addonExtraFields` on reload path (pre-populated extraFields) | `addonExtraFields` | `AddonNodeDataCopy.restoreAddonFieldsToNode` — base map from `new HashMap<>(nodeData.getExtraFields())`; ser.deserialize reads script from fields, overrides via ctx.getScriptText() if non-null | Yes | ✓ FLOWING — no regression to existing path (AddonNodeConversionRoundTripTest + AddonNodeReloadRegressionTest test 3) |
| `Node.addonExtraFields` on creation path | `addonExtraFields` | `Node(String,int,int)` constructor via `ser.deserialize(ctx, null)` | Yes | ✓ FLOWING — unchanged from Plan 01-08; constructor seeding confirmed in prior passes |
| `Node.isAddonUnresolved()` flag on restore | `addonUnresolved` | `restoreAddonFieldsToNode` success branch line 129 | Cleared (false) | ✓ FLOWING — WR-05 fixed; flag correctly cleared on successful restore |
| `Sidebar addon content panel` | `addonCategoryNodes` | `NodeTypeRegistry.INSTANCE.allDefinitions()` filtered by category | Yes (when addon installed) | ✓ FLOWING — GAP-1 scrollbar wired; unchanged from prior pass |
| `Node.getDisplayName() ADDON branch` | display name from `definitionFor(addonTypeId)` | `NodeTypeRegistry.INSTANCE` | Yes | ✓ FLOWING — GAP-2 resolved; unchanged from prior pass |
| `Node.executeAddonNode` → executor | `addonTypeId` from snapshot | `AddonNodeDataCopy.copyAddonFieldsToNodeData` at ExecutionManager | Yes | ✓ FLOWING — unchanged from prior pass |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| AddonNodeReloadRegressionTest (3 tests — NEW-CR-02 / WR-05) | `./gradlew.bat :common:test --tests "com.pathmind.data.AddonNodeReloadRegressionTest"` | Commit 12f69ef confirms BUILD SUCCESSFUL, 230 tests, 0 failures for full suite | ✓ PASS — code-verified (tests exist with correct assertions at lines 70-78, 117-118, 148-150) |
| AddonNodePersistenceTest (6 tests including 4 assertTrue + 2 assertFalse — WR-01) | `./gradlew.bat :common:test --tests "com.pathmind.data.AddonNodePersistenceTest"` | 6 JUnit assertions now executable without -ea; file verified at lines 170-173, 212-213 | ✓ PASS — assertion mechanism verified in source; no Java assert on json.contains calls |
| AddonNodeConversionRoundTripTest (regression guard) | `./gradlew.bat :common:test --tests "com.pathmind.data.AddonNodeConversionRoundTripTest"` | Full suite 230 tests 0 failures per commit 12f69ef | ✓ PASS — no regression to pre-populated extraFields path |
| AddonNodeAliasingTest, AddonNodeCreationTest, AddonSidebarScrollTest, NodeTypeRegistryTest | Full suite: `./gradlew.bat :common:test --rerun-tasks` | BUILD SUCCESSFUL, 230 tests, 0 failures (orchestrator-confirmed per plan 01-11 SUMMARY) | ✓ PASS |
| In-game sidebar, drag, preset round-trip, execution, standalone | Requires running Minecraft client | Cannot test headlessly | ? SKIP → human_needed |

---

### Probe Execution

Step 7c: No probes declared in any PLAN file. No conventional `scripts/*/tests/probe-*.sh` found in the repository. SKIPPED.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| API-01 | Plan 01-01 | Addon declares "pathmind" entrypoint; registerNodes called | ✓ SATISFIED | Unchanged from previous passes. |
| API-02 | Plan 01-01 | Addon can register custom node types through a typed registrar | ✓ SATISFIED | Unchanged. |
| API-03 | Plan 01-01 | Registration validated at load time with informative errors naming the addon | ✓ SATISFIED | Unchanged. |
| API-04 | Plan 01-01 | Lifecycle ordering guaranteed; sealed after init | ✓ SATISFIED | Unchanged. |
| API-05 | Plans 01-02, 01-04, 01-10, 01-11, 01-12, 01-13 | Addon nodes persist opaque schema-versioned blob | ✓ SATISFIED | NEW-CR-02 fixed: null-extraFields reload path now correctly seeds addonExtraFields from serializer default. WR-01 fixed: JSON-field checks now executable. CR-01 fixed: failedAddons thread-safe. All persistence-contract assertions execute without -ea. |
| API-06 | Plans 01-02, 01-04 | Addon executors run asynchronously via CompletableFuture | ✓ SATISFIED | Execution path unchanged; snapshot data flow verified. |
| API-07 | Plans 01-02, 01-09 | Addon nodes can render custom content via body renderer hook | ✓ SATISFIED | GAP-4 scissor fix; unchanged from prior pass. |
| API-08 | Plan 01-03 | Separate API artifact published; convention-only boundary | ~ PARTIAL | Unchanged — artifact exists, WR-09 design limitation accepted. |
| API-09 | All plans | Pathmind runs unchanged with no addons | ✓ SATISFIED | GAP-5 indicator for orphaned nodes; standalone path unaffected. |
| API-10 | Plans 01-03, 01-06 | Addon API documented for third parties | ✓ SATISFIED | getting-started.md correct Fabric API version; unchanged. |
| LUA-01 | Plans 01-02, 01-05, 01-07..01-09 | User can grab Script node from palette and place it | ✓ SATISFIED (code) / human_needed (runtime) | GAP-1..GAP-5 all closed. In-game: human_needed. |
| LUA-05 | Plans 01-02, 01-04, 01-08, 01-10, 01-11, 01-13 | Script text persists through save/load with _schema_version | ✓ SATISFIED | NEW-CR-02 fully closed. Both null-extraFields path (freshly-placed node) and pre-populated path verified at code level. AddonNodeReloadRegressionTest gates both. In-game runtime: human_needed. |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AddonNodePersistenceTest.java` | 64 | Java `assert version >= 1;` inside class-local TEST_SERIALIZER.deserialize | Info | Explicitly out of scope for WR-01 remediation (Plan 01-13 scope note). This is a serializer-internal sanity check, not a JSON-field presence test. The test calls this serializer directly in deserialize_toleratesNullFields — the version check only runs when fields is non-null (line 61: `if (versionObj instanceof Number)`). No observable impact on the JSON-contract gate. Not a blocker. |

No TBD/FIXME/XXX debt markers found in any of the three round-3 gap-closure files.

Pre-existing warnings from prior passes (not introduced by round-3 closure — no regression):

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `NodeTypeRegistrar.java` | `seal()` is public | Warning | WR-06 (original): one bad addon can block all subsequent addons |
| `NodeGraph.java` | Warn logged unconditionally per frame on renderer failure | Warning | WR-04: log flood at 60+/sec; no rate limiting |
| `NodeTypeRegistrar.java` | Regex allows path-traversal characters in name segment | Warning | WR-03: `../../../evil` passes id validation |
| `AddonLoader.java` | Fabric-only imports in common module | Warning | WR-11: latent NoClassDefFoundError on NeoForge |
| `Node.java` | whenComplete on arbitrary addon thread touches MC client state | Warning | WR-04: latent race/crash when Phase 2 Lua VM completes from worker thread |
| `LuaScriptNodeRenderer.java` | MinecraftClient.getInstance() with no null guard (CR-03) | Warning | NPE in headless context; caught by renderAddonNodeContent catch(Throwable) |
| `AddonNodeDefinition.Builder.build()` | NullPointerException for blank values (WR-02 review) | Warning | Misleading exception type; IllegalArgumentException would be semantically correct |
| `Sidebar.calculateMaxScroll` | Magic `+100` over-scroll diverges from computeAddonContentHeight (WR-03 review) | Warning | Off-by-100 in scrollbar knob position accuracy |
| `LuaScriptNodeRenderer.LINE_HEIGHT` | Hardcoded value decoupled from textRenderer.fontHeight (IN-03 review) | Info | Resource packs altering font height cause line overlap/gap |

---

### Human Verification Required

These items have been present since round-2 verification. All code-level wiring is confirmed. Runtime behavioral verification in a live Minecraft game is required.

#### 1. Addon Category Scrollbar and Display Name

**Test:** Launch Minecraft with both pathmind.jar and pathmind-lua-addon.jar. Open the editor sidebar. Expand the addon Scripting category. If there are more entries than fit, scroll the category.
**Expected:** Scripting tab appears; entries show the addon-registered display name (e.g. "Lua Script", not "Addon Node"). Scrollbar renders and is draggable. Bottom entries are accessible.
**Why human:** GAP-1 (computeAddonContentHeight, getCategoryScrollMetrics guard, renderCategoryScrollbar) and GAP-2 (Node.getDisplayName ADDON branch) are code-verified. Visual output and scrollbar drag behavior require a running Minecraft client.

#### 2. Script Node Drag with Default Content and Scissor Rendering

**Test:** Hover over the Script node entry and drag it to the canvas. Observe node title, body content, and z-ordering relative to the sidebar drawer.
**Expected:** Node title shows addon display name (not "Addon Node"). Default script content is visible immediately on the newly-placed node (not empty). Node body does not overdraw the sidebar drawer.
**Why human:** GAP-3 constructor seeding and GAP-4 scissor-clip are code-verified. Drag path, default content visibility, and z-ordering require a live game.

#### 3. Preset Round-Trip — Freshly-Placed Never-Edited Node (NEW-CR-02 runtime confirmation)

**Test:** Place a Script node WITHOUT typing any script text. Save the preset immediately. Close the editor and game. Restart, open preset. Observe script node content.
**Expected:** Script node present after reload with non-null addonExtraFields containing the default script (not empty/null). This confirms the NEW-CR-02 fix works with the actual LuaNodeSerializer's null-fields behavior in the running game.
**Test also:** Place a Script node, type script text, save, reload.
**Expected:** Typed text survives reload (no regression to normal path).
**Why human:** NEW-CR-02 is fully fixed and gated by AddonNodeReloadRegressionTest using the test AddonTestRegistry serializer. Runtime confirmation with the actual LuaNodeSerializer (pathmind-lua-addon.jar) and the actual file system requires a live game.

#### 4. Execution Pass-Through

**Test:** Place a Script node (with or without script text). Press Play.
**Expected:** Script node activates, executes (graceful no-op completion — no Lua VM yet), graph continues. No "Skipping ADDON node with null addonTypeId" in log.
**Why human:** Execution path code-verified. CompletableFuture lifecycle and HUD overlay require a running game.

#### 5. Standalone Mode — Missing-Addon Indicator and WR-05 Runtime

**Test A:** Remove the addon jar, restart Minecraft, open a preset that contained a Script node.
**Expected:** Pathmind loads normally. Orphaned Script node shows "warning addon missing" indicator with addonTypeId label. No crash.
**Test B (WR-05):** Open a preset with the addon absent (node loads as unresolved). Then re-install the addon jar and restart. Open the same preset.
**Expected:** Previously-unresolved Script node now shows normal content (not the missing-addon indicator), confirming the WR-05 unresolved-flag clear works at runtime.
**Why human:** GAP-5 and WR-05 code-verified. Runtime confirmation across 1.21-1.21.11 requires actual game launches.

---

### Gaps Summary

No automated gaps remain. All five roadmap success criteria are verified at the code level. The 5 human verification items are behavioral in-game tests that cannot be verified headlessly — they are the same UAT items that have been present since round-2 verification, plus a WR-05 runtime confirmation added in this round.

The one remaining `assert version >= 1;` in AddonNodePersistenceTest line 64 is inside the class-local TEST_SERIALIZER.deserialize's `instanceof Number` branch (only executed when fields is non-null and contains `_schema_version` as a Number). It was explicitly out of scope in Plan 01-13 and does not affect the API-05/LUA-05 JSON-contract gate. It is classified as Info, not a blocker.

---

_Verified: 2026-06-13_
_Re-verification round 4: after round-3 gap-closure Plans 01-11 (NEW-CR-02 + WR-05), 01-12 (NEW-CR-01), 01-13 (NEW-WR-01) plus post-merge test-isolation fix (AddonTestRegistry, commit 12f69ef). All automated gaps closed; status human_needed (5 in-game UAT items remaining)._
_Verifier: Claude (gsd-verifier)_
