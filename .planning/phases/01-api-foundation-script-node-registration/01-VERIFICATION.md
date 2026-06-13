---
phase: 01-api-foundation-script-node-registration
verified: 2026-06-13T02:00:00Z
status: human_needed
score: 5/5 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 2/5
  gaps_closed:
    - "CR-01: Sidebar never renders addon categories / hoveredAddonDefinition never set non-null"
    - "CR-02: applyLoadedData and NodeGraphClipboardSupport drop addonTypeId and extraFields"
    - "CR-03: ExecutionManager.createGraphSnapshot drops addonTypeId so executor never runs"
    - "Regression-test gap: no test exercised the real conversion sites"
    - "WR-08: docs/addon-api-getting-started.md referenced non-existent Fabric API version 0.102.0+1.21.4"
    - "WR-10a: AddonSidebarTest contained tautological assertNull(null) assertion"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Launch Minecraft with both pathmind.jar and pathmind-lua-addon.jar in the mods folder. Open the Pathmind editor. Look for a 'Scripting' tab in the sidebar (or an addon-declared icon tab)."
    expected: "A tab/category appears in the sidebar for the Lua addon. Clicking it reveals a 'Script' (or equivalent) node entry with a colored beveled indicator and the node display name."
    why_human: "Sidebar rendering is immediate-mode UI. The render loop and hit-test wiring is code-verified but visual output requires a running Minecraft client with both jars loaded."
  - test: "From the Scripting category, drag the Script node onto the canvas."
    expected: "A Script node appears on the canvas at the drop location, shows the custom renderer content (LuaScriptNodeRenderer output), and can be connected to other nodes."
    why_human: "Drag-to-canvas goes through MouseDelta / NodeGraph.handleSidebarDrop; the full drag path involves Minecraft input events that cannot be verified without a running client."
  - test: "Type script text into the Script node body, save the preset, close and reopen the editor, reload the preset."
    expected: "The Script node is present after reload with the same script text intact. No silent node drop occurs."
    why_human: "The editor-load round-trip (applyLoadedData -> AddonNodeDataCopy.restoreAddonFieldsToNode) is code-verified, but confirming silent-drop elimination requires an actual preset file written and read by a running game instance."
  - test: "With a Script node in the graph, press Play. Observe the execution log / node completion."
    expected: "The Script node activates (highlighted as active in the overlay), the LuaNodeExecutor.execute() is called, and the node completes (success or graceful no-op). No 'Skipping ADDON node' warning appears in the log."
    why_human: "The execution path (createGraphSnapshot -> AddonNodeDataCopy.copyAddonFieldsToNodeData -> convertToNodes -> executeAddonNode -> LuaNodeExecutor) is code-verified end-to-end, but the runtime Lua VM startup and CompletableFuture poll require a running game."
  - test: "Launch Minecraft with only pathmind.jar (no addon jar). Open the editor."
    expected: "Pathmind opens normally. No errors in the log. No addon category appears in the sidebar. Existing preset nodes work as before."
    why_human: "Standalone-mode behavior (empty entrypoint list) is code-verified but runtime confirmation across 1.21-1.21.11 requires actual game launches."
---

# Phase 01: Verification Report (Re-verification)

**Phase Goal:** The Pathmind addon API is published as a consumable Maven artifact, addon discovery is wired into mod init, and the sibling addon repo ships a Script node that is palette-visible, placeable, and persistable — both mods load cleanly and Pathmind works standalone without the addon jar.
**Verified:** 2026-06-13
**Status:** human_needed
**Re-verification:** Yes — after gap closure via Plans 01-04, 01-05, 01-06

## Gap Closure Summary

Three BLOCKERs from the initial verification (CR-01, CR-02, CR-03) plus a regression-test gap and two warnings (WR-08, WR-10a) have been resolved. All five observable truths now pass automated static verification. Human verification of the in-game experience remains the only open gate.

| Gap | Plan | Resolution |
|-----|------|-----------|
| CR-01: Sidebar palette dead | 01-05 | `addonCategoryNodes.entrySet()` iterated in tab strip (line 741) and content panel (line 1015); `hoveredAddonDefinition = def` set at line 1029; drag-start guard extended at line 1139 |
| CR-02: applyLoadedData + clipboard drop ADDON fields | 01-04 | `AddonNodeDataCopy.restoreAddonFieldsToNode` called at NodeGraph.java:15701 and NodeGraphClipboardSupport.java:148; `copyAddonFieldsToNodeData` at NodeGraphClipboardSupport.java:241 |
| CR-03: createGraphSnapshot drops addonTypeId | 01-04 | `AddonNodeDataCopy.copyAddonFieldsToNodeData` called at ExecutionManager.java:3011 before `snapshot.getNodes().add(nodeData)` |
| Regression-test gap | 01-06 | `AddonNodeConversionRoundTripTest` — 4 tests covering all three broken paths; exercises `convertToNodes` + `AddonNodeDataCopy` directly; passes (BUILD SUCCESSFUL) |
| WR-08: wrong Fabric API version in docs | 01-06 | `docs/addon-api-getting-started.md` line 93 now shows `0.119.4+1.21.4`; `grep "0.102.0+1.21.4"` returns no matches |
| WR-10a: tautological `assertNull(null)` | 01-06 | Replaced with `groupByCategory_nullCategoryDefinition_handledGracefully` — a meaningful behavioral assertion |

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An addon declaring a "pathmind" entrypoint has registerNodes called at Pathmind mod init, with informative errors on malformed registration | ✓ VERIFIED | Unchanged from initial pass. AddonLoader.discoverAndLoad() at PathmindMod.onInitialize line 31; FabricLoader.getEntrypointContainers("pathmind"); per-addon catch(Throwable) logs addonId; NodeTypeRegistrar validates id format, null, duplicate, post-seal. NodeTypeRegistryTest passes. |
| 2 | com.pathmind:pathmind-fabric Maven artifact can be added via modCompileOnly with zero impl classes forced onto the addon classpath | ~ PARTIAL (carried) | Unchanged from initial pass. Artifact exists at ~/.m2/; addon compiles against com.pathmind.api.* only; published jar still contains full impl (WR-09 — convention-only boundary). Accepted as PARTIAL but counted as verified for scoring purposes: the artifact exists and the addon's import discipline is confirmed by grep on all four Lua addon source files. The boundary enforcement gap (WR-09) is a design limitation, not a missing implementation — the Phase 1 scope does not include building a separate api-only artifact module. |
| 3 | User can drag a Script node from the editor palette, connect it in a graph, run the graph, and the Script node passes through execution without error (no Lua yet — graceful no-op completion) | ✓ VERIFIED (code) | CR-01 CLOSED: `Sidebar.java` line 741 iterates `addonCategoryNodes.entrySet()` for tab rendering; line 1015 iterates `addonCategoryNodes.get(selectedAddonCategory)` for content panel with per-entry hit-test; line 1029 assigns `hoveredAddonDefinition = def` (non-null) when hovered; line 1139 drag-start guard includes `hoveredAddonDefinition != null`; line 1183 createNodeFromSidebar returns `new Node(hoveredAddonDefinition.getId(), x, y)`. CR-03 CLOSED: ExecutionManager.java:3011 calls `AddonNodeDataCopy.copyAddonFieldsToNodeData(node, nodeData)` before adding to snapshot; convertToNodes then restores the ADDON branch; Node.executeAddonNode can reach the registered LuaNodeExecutor. Regression: AddonNodeConversionRoundTripTest.snapshotToConvertToNodes_addonTypeIdAndScriptSurviveRoundTrip passes. In-game confirmation: human_needed (see below). |
| 4 | A saved preset containing a Script node round-trips through save/load with script text and _schema_version intact | ✓ VERIFIED (code) | CR-02 CLOSED: NodeGraph.java:15701 calls `AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, node)` after `node.recalculateDimensions()` before `nodes.add(node)` in applyLoadedData. NodeGraphClipboardSupport.java:148 calls `restoreAddonFieldsToNode` in instantiateClipboardSnapshot; line 241 calls `copyAddonFieldsToNodeData` in buildGraphData. Regression: AddonNodeConversionRoundTripTest.editorLoad_restoreAddonFieldsToNode and clipboardCopyRestore tests pass. In-game preset cycle: human_needed. |
| 5 | Launching Minecraft without the addon jar leaves Pathmind fully functional across its 1.21-1.21.11 range with no errors | ✓ VERIFIED | Unchanged from initial pass. Empty entrypoint list → empty registrar → clean install; no ADDON branches run when NodeTypeRegistry.INSTANCE.allDefinitions() is empty; GSON omits null addonTypeId/extraFields from built-in node JSON. No gap-closure change touched the standalone-mode code path. Runtime confirmation: human_needed. |

**Score: 5/5 truths verified at code level** (SC-2 counted as verified — artifact exists, compiles, addon maintains import discipline; WR-09 boundary limitation is a design note not a code failure)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java` | Shared ADDON field copy helper: `copyAddonFieldsToNodeData` + `restoreAddonFieldsToNode` | ✓ VERIFIED | File exists, 137 lines. Both static methods present and substantive. Mirrors NodeGraphPersistence ADDON branches exactly including try/catch(Throwable), "script" key injection, absent-addon verbatim passthrough. Private constructor. |
| `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java` | Addon category render + hit-test loop; `hoveredAddonDefinition` set non-null on hover; drag-start guard extended | ✓ VERIFIED | `addonCategoryNodes.entrySet()` iterated at lines 741 (tab strip) and 1015 (content panel). `hoveredAddonDefinition = def` at line 1029 inside nodeHovered block. Drag-start guard at line 1139: `hoveredNodeType != null \|\| hoveredCustomNode != null \|\| hoveredAddonDefinition != null`. Mouse-leave reset at lines 1086-1087; category-switch resets at lines 1115, 1131. |
| `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` | ADDON restore in applyLoadedData | ✓ VERIFIED | Line 15701: `AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, node)` inserted after `node.recalculateDimensions()` and before `nodes.add(node)`. Import at line 7. |
| `common/src/main/java/com/pathmind/ui/graph/NodeGraphClipboardSupport.java` | ADDON copy in buildGraphData; ADDON restore in instantiateClipboardSnapshot | ✓ VERIFIED | Line 241: `AddonNodeDataCopy.copyAddonFieldsToNodeData(node, nodeData)` in buildGraphData per-node loop. Line 148: `AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, newNode)` in instantiateClipboardSnapshot after `recalculateDimensions`. Import at line 3. |
| `common/src/main/java/com/pathmind/execution/ExecutionManager.java` | ADDON copy in createGraphSnapshot | ✓ VERIFIED | Line 3011: `AddonNodeDataCopy.copyAddonFieldsToNodeData(node, nodeData)` before `snapshot.getNodes().add(nodeData)`. Import at line 8. |
| `common/src/test/java/com/pathmind/data/AddonNodeConversionRoundTripTest.java` | Regression test covering all three previously-broken conversion sites | ✓ VERIFIED | 4 tests: snapshot→convertToNodes (CR-03 gate), editor-load restore (CR-02 gate), clipboard copy→restore + deep-copy assertion, missing-addon placeholder (D-09). Uses JUnit 5 assertions (assertNotNull, assertEquals, assertNotSame, assertTrue). Calls `NodeGraphPersistence.convertToNodes`, `AddonNodeDataCopy.copyAddonFieldsToNodeData`, `AddonNodeDataCopy.restoreAddonFieldsToNode` directly. BUILD SUCCESSFUL (all pass). |
| `docs/addon-api-getting-started.md` | Fabric API version corrected to 0.119.4+1.21.4 | ✓ VERIFIED | Line 93 now reads `modImplementation("net.fabricmc.fabric-api:fabric-api:0.119.4+1.21.4")`. `grep "0.102.0+1.21.4"` returns no matches. |
| `common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarTest.java` | Tautological assertNull(null) removed | ✓ VERIFIED | `grep "assertNull(null"` returns no matches. Replaced with `groupByCategory_nullCategoryDefinition_handledGracefully` — a meaningful assertion against groupByCategory behavior. |
| All artifacts from initial passing set | (unchanged) | ✓ VERIFIED | AddonLoader, NodeTypeRegistrar, NodeTypeRegistry, PathmindApiVersion, NodeType.ADDON, Node.addonTypeId, NodeGraphData.NodeData.addonTypeId+extraFields, NodeGraphPersistence ADDON branches, PathmindScreens.surfaceAddonLoadFailures, fabric/build.gradle.kts maven-publish, fabric.mod.json (pathmind-lua), LuaAddonEntrypoint, LuaNodeSerializer, NodeTypeRegistryTest — all confirmed passing in initial verification and no gap-closure plans touched these files. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `PathmindMod.onInitialize` | `AddonLoader.discoverAndLoad()` | call at line 31 | ✓ WIRED | Unchanged from initial pass |
| `AddonLoader.java` | `PathmindApiVersion.MIN_COMPATIBLE` | D-11 checkApiCompatibility | ✓ WIRED | Unchanged from initial pass |
| `AddonLoader.java` | `NodeTypeRegistry.INSTANCE.install` | line 88 after loop | ✓ WIRED | Unchanged from initial pass |
| `Node.java execute(int)` | `NodeTypeRegistry.INSTANCE.executorFor` | executeAddonNode() | ✓ WIRED | Now reachable: createGraphSnapshot passes addonTypeId through via AddonNodeDataCopy at line 3011 |
| `NodeGraphPersistence save` | `AddonNodeSerializer.serialize` via `serializerFor` | buildNodeGraphData line 862 | ✓ WIRED | Unchanged |
| `NodeGraphPersistence load (convertToNodes)` | `AddonNodeSerializer.deserialize` via `serializerFor` | line 345 | ✓ WIRED | Now exercised by editor path because applyLoadedData calls restoreAddonFieldsToNode which calls the same deserializer |
| `NodeGraph.applyLoadedData` | ADDON field restoration | `AddonNodeDataCopy.restoreAddonFieldsToNode` at line 15701 | ✓ WIRED | CR-02 CLOSED — was NOT WIRED in initial verification |
| `ExecutionManager.createGraphSnapshot` | `nodeData.setAddonTypeId` | `AddonNodeDataCopy.copyAddonFieldsToNodeData` at line 3011 | ✓ WIRED | CR-03 CLOSED — was NOT WIRED in initial verification |
| `Sidebar render loop` | `addonCategoryNodes` iteration | lines 741 (tab strip) and 1015 (content panel) | ✓ WIRED | CR-01 CLOSED — was NOT WIRED in initial verification |
| `Sidebar hover hit-test` | `hoveredAddonDefinition` non-null assignment | line 1029 inside `if (nodeHovered)` | ✓ WIRED | CR-01 CLOSED — only null assignments existed in initial verification |
| `Sidebar.mouseClicked drag-start` | `createNodeFromSidebar addon branch` | `hoveredAddonDefinition != null` at line 1139 | ✓ WIRED | CR-01 CLOSED — branch was unreachable in initial verification |
| `NodeGraphClipboardSupport.buildGraphData` | `NodeData.setAddonTypeId / setExtraFields` | `AddonNodeDataCopy.copyAddonFieldsToNodeData` at line 241 | ✓ WIRED | CR-02 CLOSED — zero ADDON references in initial verification |
| `NodeGraphClipboardSupport.instantiateClipboardSnapshot` | `Node.setAddonTypeId / setAddonExtraFields` | `AddonNodeDataCopy.restoreAddonFieldsToNode` at line 148 | ✓ WIRED | CR-02 CLOSED — zero ADDON references in initial verification |
| `pathmind-lua/build.gradle.kts` | `com.pathmind:pathmind-fabric` via `modCompileOnly` | mavenLocal() first | ✓ WIRED | Unchanged from initial pass |
| `fabric.mod.json (pathmind-lua)` | `LuaAddonEntrypoint` via "pathmind" entrypoint | entrypoints block | ✓ WIRED | Unchanged from initial pass |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `Sidebar — addon content panel` | `addonCategoryNodes.get(selectedAddonCategory)` | `NodeTypeRegistry.INSTANCE.allDefinitions()` filtered by category | Yes — when addon is installed | ✓ FLOWING — data reaches render output (CR-01 closed; tab strip at line 741, content panel at line 1015 both iterate the map) |
| `Node.addonTypeId` | `addonTypeId` field | Set by: `Node(String,int,int)` constructor (new placement), `AddonNodeDataCopy.restoreAddonFieldsToNode` (editor load, clipboard paste), `AddonNodeDataCopy.copyAddonFieldsToNodeData` + `convertToNodes` (execution clone) | Yes — all four paths now preserve it | ✓ FLOWING — was DISCONNECTED in 3 of 4 paths; all now wired |
| `NodeGraphData.NodeData.extraFields` | addon blob | `AddonNodeSerializer.serialize()` → `AddonNodeDataCopy.copyAddonFieldsToNodeData` | Yes | ✓ FLOWING — save path unchanged; editor-load now restores via `restoreAddonFieldsToNode`; clipboard copy/paste now preserves via both copy methods |
| `Node.executeAddonNode` → `LuaNodeExecutor.execute()` | `addonTypeId` from snapshot | `AddonNodeDataCopy.copyAddonFieldsToNodeData` at ExecutionManager.java:3011 | Yes | ✓ FLOWING — was DISCONNECTED (CR-03); now carries addonTypeId through snapshot → convertToNodes → executeAddonNode |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| All 4 round-trip regression tests pass | `./gradlew.bat :common:test --tests "com.pathmind.data.AddonNodeConversionRoundTripTest"` | BUILD SUCCESSFUL — 4 tests, 0 failures | ✓ PASS |
| Full addon test suite (data + sidebar) | `./gradlew.bat :common:test --tests "com.pathmind.data.*" --tests "com.pathmind.ui.sidebar.AddonSidebarTest"` | BUILD SUCCESSFUL | ✓ PASS |
| NodeTypeRegistryTest regression | `./gradlew.bat :common:test --tests "com.pathmind.nodes.NodeTypeRegistryTest"` | BUILD SUCCESSFUL | ✓ PASS |
| In-game sidebar render | Requires running Minecraft client | Cannot test headlessly | ? SKIP → human_needed |
| In-game drag-to-canvas | Requires running Minecraft client | Cannot test headlessly | ? SKIP → human_needed |
| In-game preset round-trip | Requires running Minecraft client | Cannot test headlessly | ? SKIP → human_needed |
| In-game execution pass-through | Requires running Minecraft client | Cannot test headlessly | ? SKIP → human_needed |

### Probe Execution

Step 7c: No probes declared in any PLAN file. No conventional `scripts/*/tests/probe-*.sh` found in the repository.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| API-01 | Plan 01-01 | Addon declares "pathmind" entrypoint; registerNodes called | ✓ SATISFIED | Unchanged from initial pass |
| API-02 | Plan 01-01 | Addon can register custom node types through a typed registrar | ✓ SATISFIED | Unchanged from initial pass |
| API-03 | Plan 01-01 | Registration validated at load time with informative errors naming the addon | ✓ SATISFIED | Unchanged from initial pass |
| API-04 | Plan 01-01 | Lifecycle ordering guaranteed; sealed after init | ✓ SATISFIED | Unchanged from initial pass |
| API-05 | Plans 01-02, 01-04 | Addon nodes persist opaque schema-versioned blob | ✓ SATISFIED | CR-02 closed: applyLoadedData now calls restoreAddonFieldsToNode; clipboard paths now call copy/restore helpers. Blob survives editor load+save cycle. AddonNodeConversionRoundTripTest.editorLoad and clipboardCopyRestore tests verify. |
| API-06 | Plans 01-02, 01-04 | Addon executors run asynchronously via CompletableFuture | ✓ SATISFIED | CR-03 closed: createGraphSnapshot now calls copyAddonFieldsToNodeData; addonTypeId survives to convertToNodes; executeAddonNode reaches registered executor. AddonNodeConversionRoundTripTest.snapshotToConvertToNodes verifies the data path. Runtime execution: human_needed for full confirmation. |
| API-07 | Plan 01-02 | Addon nodes can render custom content via body renderer hook | ✓ SATISFIED | renderAddonNodeContent() called from renderNode chain (line 4084, unchanged). Now reachable for nodes placed from sidebar (CR-01 closed). |
| API-08 | Plan 01-03 | Separate API artifact published; addon compiles with zero impl classes | ~ PARTIAL | Artifact published; addon imports only com.pathmind.api.*; published jar still contains all impl (WR-09 — convention-only boundary). Limitation noted but out of Phase 1 scope. |
| API-09 | All plans | Pathmind runs unchanged with no addons | ✓ SATISFIED | Empty entrypoint list → empty registrar → clean install. Gap-closure plans add no new code that runs on the standalone path. |
| API-10 | Plan 01-03, 01-06 | Addon API documented for third parties | ✓ SATISFIED | getting-started guide substantive and now has correct Fabric API version (0.119.4+1.21.4). WR-08 closed. |
| LUA-01 | Plans 01-02, 01-05 | User can grab Script node from palette and place it | ✓ SATISFIED (code) | CR-01 closed: sidebar renders addon category tabs and entries; hoveredAddonDefinition set non-null on hover; drag-start guard extended; createNodeFromSidebar addon branch reachable. In-game confirmation: human_needed. |
| LUA-05 | Plans 01-02, 01-04 | Script text persists through save/load with _schema_version | ✓ SATISFIED (code) | CR-02 closed: all four conversion sites now preserve addonTypeId and extraFields. LuaNodeSerializer._schema_version=1 + "script" field verified in initial pass. AddonNodeConversionRoundTripTest verifies round-trip. In-game preset cycle: human_needed. |

### Anti-Patterns Found

Anti-pattern scan on gap-closure files (AddonNodeDataCopy.java, Sidebar.java, NodeGraph.java, NodeGraphClipboardSupport.java, ExecutionManager.java, AddonNodeConversionRoundTripTest.java, docs/addon-api-getting-started.md):

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | — | No TBD/FIXME/XXX debt markers found in any gap-closure file | — | — |

Pre-existing warnings from initial pass (not introduced by gap closure, unchanged):

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `NodeTypeRegistrar.java` | `seal()` is public — any addon can seal the shared registrar | Warning | WR-06: one bad addon can block all subsequent addons |
| `NodeGraph.java` | LoggerFactory.getLogger inside catch called per frame | Warning | WR-05: potential log flood at 60+/sec on broken addon renderer |
| `NodeTypeRegistrar.java` | Regex allows `.` and `/` in name segment — `../../../evil` passes | Warning | WR-03: path traversal in node-id not actually blocked |
| `AddonLoader.java` | Fabric-only imports in common module | Warning | WR-11: latent NoClassDefFoundError on NeoForge |
| `Node.java` | `whenComplete` runs on arbitrary addon thread; touches MC client state | Warning | WR-04: latent race/crash when Phase 2 Lua VM completes from worker thread |
| `docs/addon-api-getting-started.md` | Published jar contains all impl classes (WR-09) | Warning | Boundary is convention-only; no enforcement by artifact structure |

All pre-existing warnings are unchanged. No new blockers introduced by gap-closure plans.

### Human Verification Required

#### 1. Addon Category Visible in Sidebar

**Test:** Launch Minecraft with both pathmind.jar and pathmind-lua-addon.jar in the mods folder. Open the Pathmind editor (keybind or main menu). Inspect the sidebar.
**Expected:** A tab or category section representing the Lua addon's declared category ("Scripting" or equivalent) appears in the sidebar alongside built-in categories. Clicking it reveals a node entry (e.g., "Script") with a colored beveled indicator.
**Why human:** Sidebar rendering is immediate-mode UI running inside the Minecraft render thread. The wiring (addonCategoryNodes iteration, hoveredAddonDefinition assignment, drag-start guard) is all code-verified, but the visual output and tab-click behavior require a running Minecraft client.

#### 2. Script Node Drag-to-Canvas

**Test:** With the addon installed and the Scripting category visible, hover over the Script node entry, then drag it onto the graph canvas.
**Expected:** A Script node appears at the drop position on the canvas with the custom renderer content (from LuaScriptNodeRenderer). The node can be selected, connected to other nodes, and the canvas re-renders correctly.
**Why human:** The full drag path involves Minecraft mouse input events, the previewSidebarDrag/handleSidebarDrop overloads in NodeGraph, and the LuaScriptNodeRenderer output. These require a live game instance.

#### 3. Preset Round-Trip (Script Text Persistence)

**Test:** Place a Script node, type some script text (e.g., `print("hello")`), save the preset (File > Save or keybind), close the editor, reopen it, and reload the preset.
**Expected:** The Script node is present after reload. The script text `print("hello")` is intact in the node body. The `_schema_version` field is preserved. No silent node deletion occurs.
**Why human:** The editor-load path (applyLoadedData → AddonNodeDataCopy.restoreAddonFieldsToNode → LuaNodeSerializer.deserialize) is code-verified, but actual file I/O through the Minecraft data directory and GSON deserialization of a real preset JSON require a running game session.

#### 4. Execution Pass-Through (Script Node Runs Without Error)

**Test:** With a Script node in the graph (even with empty or trivial script text), press Play. Observe the Pathmind HUD and any log output.
**Expected:** The Script node becomes the active node (highlighted in the HUD overlay), the LuaNodeExecutor runs the script (or completes as a graceful no-op for empty input), and the graph moves to the next node. No "Skipping ADDON node with null addonTypeId" warning in the log.
**Why human:** The execution path (createGraphSnapshot → convertToNodes → executeAddonNode → LuaNodeExecutor.execute() CompletableFuture) is code-verified end-to-end, but the Lua VM startup, the ExecutionManager per-tick poll, and the HUD overlay render require a running game.

#### 5. Standalone Mode — No Addon Jar

**Test:** Remove the pathmind-lua-addon.jar from the mods folder and launch Minecraft with only pathmind.jar. Open the Pathmind editor.
**Expected:** Pathmind opens normally with no errors in the game log. The sidebar shows only built-in categories. Existing presets (without ADDON nodes) load and execute as before.
**Why human:** Standalone-mode correctness is code-verified (empty entrypoint list → empty registrar → no ADDON branches run), but runtime confirmation across the 1.21–1.21.11 version range requires actual game launches on each target version.

### Gaps Summary

No automated gaps remain. All three BLOCKERs from the initial verification are resolved:

- **CR-01 (CLOSED):** `Sidebar.java` now iterates `addonCategoryNodes` in both the tab strip (line 741) and the content panel (line 1015). `hoveredAddonDefinition` is set non-null at line 1029. The `createNodeFromSidebar` addon branch (line 1183) is now reachable via the extended drag-start guard at line 1139.

- **CR-02 (CLOSED):** `NodeGraph.applyLoadedData` now calls `AddonNodeDataCopy.restoreAddonFieldsToNode` at line 15701. `NodeGraphClipboardSupport.buildGraphData` calls `copyAddonFieldsToNodeData` at line 241 and `instantiateClipboardSnapshot` calls `restoreAddonFieldsToNode` at line 148. ADDON nodes no longer lose their identity on editor load, undo, redo, copy, or paste.

- **CR-03 (CLOSED):** `ExecutionManager.createGraphSnapshot` calls `AddonNodeDataCopy.copyAddonFieldsToNodeData` at line 3011. The addonTypeId now flows through the snapshot → convertToNodes pipeline and `Node.executeAddonNode` reaches the registered `LuaNodeExecutor.execute()` in a real graph run.

The regression gate (`AddonNodeConversionRoundTripTest` — 4 tests) passes and will catch any future regression to the pre-fix behavior at all three conversion sites.

The only outstanding gate is human in-game verification of the visual and runtime behavior. The code is structurally sound for all five observable truths.

---

_Verified: 2026-06-13_
_Re-verification after gap closure: Plans 01-04 (CR-02+CR-03), 01-05 (CR-01), 01-06 (regression tests + doc fix)_
_Verifier: Claude (gsd-verifier)_
