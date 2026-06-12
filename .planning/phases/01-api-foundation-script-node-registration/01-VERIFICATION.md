---
phase: 01-api-foundation-script-node-registration
verified: 2026-06-13T00:00:00Z
status: gaps_found
score: 2/5 must-haves verified
overrides_applied: 0
gaps:
  - truth: "User can drag a Script node from the editor palette, connect it in a graph, run the graph, and the Script node passes through execution without error (no Lua yet — graceful no-op completion)"
    status: failed
    reason: "Two independent wiring defects prevent this. CR-01: Sidebar.hoveredAddonDefinition is declared null and cleared to null but NEVER assigned a non-null value anywhere in the codebase. The sidebar render loop never iterates addonCategoryNodes for hit-testing, so no addon category ('Scripting') is ever displayed and no hover event for an addon entry is ever generated. The createNodeFromSidebar addon branch (line 1032) is structurally unreachable. CR-03: Even if a node were placed programmatically, ExecutionManager.createGraphSnapshot (lines 2936-3025) builds NodeData manually for every node but never sets addonTypeId or extraFields — the cloned NodeData has addonTypeId=null, so convertToNodes skips the ADDON deserialize branch, and Node.executeAddonNode hits the null-addonTypeId guard and completes as a silent SKIPPED warning log. The registered LuaNodeExecutor.execute() is dead code in any real graph run."
    artifacts:
      - path: "common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java"
        issue: "hoveredAddonDefinition assigned at lines 71 (null init) and 129 (null clear only) — zero non-null assignments in the entire file. addonCategoryNodes populated by initializeAddonCategoryNodes() but consumed only by getAddonCategoryNodesForTest() (test accessor). No render or hit-test code iterates the map."
      - path: "common/src/main/java/com/pathmind/execution/ExecutionManager.java"
        issue: "createGraphSnapshot (lines 2936-3025) never sets nodeData.setAddonTypeId() or nodeData.setExtraFields() despite setting 20+ other NodeData fields. All execution paths clone the graph through this method."
    missing:
      - "In Sidebar's render loop, after built-in categories, iterate addonCategoryNodes to draw category header and entry rows, and set hoveredAddonDefinition = def during hit-testing (mirroring how hoveredNodeType is set for built-in entries)."
      - "In ExecutionManager.createGraphSnapshot, add: if (node.getType() == NodeType.ADDON) { nodeData.setAddonTypeId(node.getAddonTypeId()); nodeData.setExtraFields(node.getAddonExtraFields() != null ? new HashMap<>(node.getAddonExtraFields()) : null); }"

  - truth: "A saved preset containing a Script node round-trips through save/load with script text and _schema_version intact"
    status: failed
    reason: "CR-02: The ADDON persistence logic exists only in NodeGraphPersistence.convertToNodes (load, lines 339-368) and buildNodeGraphData (save, lines 853-882). The editor does NOT use convertToNodes for its load path — it uses NodeGraph.applyLoadedData (lines 15644-15820), which constructs new Node(nodeData.getType(), x, y) and never calls setAddonTypeId or setAddonExtraFields. After a preset load into the editor, the in-memory ADDON node has addonTypeId=null. On the next save, buildNodeGraphData hits the null-addonTypeId guard (line 856-858) and silently drops the node via continue. Additionally, NodeGraphClipboardSupport.buildGraphData — used for undo snapshots, copy, cut, and duplicate — contains zero references to addonTypeId or extraFields (verified by grep). Any undo operation after editing destroys the ADDON node identity. The D-09 promise ('placeholders preserve all stored data') is violated by the most common editor workflow."
    artifacts:
      - path: "common/src/main/java/com/pathmind/ui/graph/NodeGraph.java"
        issue: "applyLoadedData (line 15644) constructs nodes via new Node(nodeData.getType(), x, y) and has no ADDON branch across its entire body (lines 15644-15820). NodeType.ADDON nodes lose their identity immediately upon editor load."
      - path: "common/src/main/java/com/pathmind/ui/graph/NodeGraphClipboardSupport.java"
        issue: "Zero references to addonTypeId or addonExtraFields anywhere in the file (grep confirmed). Undo/redo, copy, cut, paste all strip ADDON fields."
    missing:
      - "In NodeGraph.applyLoadedData, add an ADDON branch mirroring NodeGraphPersistence.convertToNodes lines 339-368: after constructing the node, if nodeData.getType() == NodeType.ADDON, call node.setAddonTypeId(nodeData.getAddonTypeId()) and restore extraFields/addonUnresolved."
      - "In NodeGraphClipboardSupport.buildGraphData, copy addonTypeId and a deep copy of addonExtraFields into NodeData; in its instantiate path, restore them."
      - "Consider extracting a shared NodeData<->Node copy helper for ADDON fields to prevent this class of miss recurring across conversion sites."
---

# Phase 01: Verification Report

**Phase Goal:** The Pathmind addon API is published as a consumable Maven artifact, addon discovery is wired into mod init, and the sibling addon repo ships a Script node that is palette-visible, placeable, and persistable — both mods load cleanly and Pathmind works standalone without the addon jar.
**Verified:** 2026-06-13
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An addon declaring a "pathmind" entrypoint has registerNodes called at Pathmind mod init, with informative errors on malformed registration | ✓ VERIFIED | AddonLoader.discoverAndLoad() is in PathmindMod.onInitialize line 31 before the success log. FabricLoader.getEntrypointContainers("pathmind") at AddonLoader:63. Per-addon catch(Throwable) at line 80 logs addonId and calls markFailed. D-11 check in checkApiCompatibility. NodeTypeRegistrar validates id format, null checks, duplicate ids, and post-seal. NodeTypeRegistryTest covers all failure modes. |
| 2 | com.pathmind:pathmind-fabric Maven artifact can be added via modCompileOnly with zero impl classes forced onto the addon classpath | ~ PARTIAL | Artifact exists at ~/.m2/repository/com/pathmind/pathmind-fabric/1.1.5+mc1.21.4/. fabric/build.gradle.kts contains maven-publish and mavenFabric publication. pathmind-lua/build.gradle.kts uses modCompileOnly and mavenLocal(). pathmind-lua Java source confirmed zero impl imports (LuaAddonEntrypoint, LuaNodeExecutor, LuaNodeSerializer, LuaScriptNodeRenderer import only com.pathmind.api.*). CAVEAT (WR-09): the published jar contains the full impl (ExecutionManager, NodeGraph, etc.) — the "zero impl classes" claim is convention-only; the published artifact does not enforce it. docs/addon-api-getting-started.md has wrong Fabric API version 0.102.0 (WR-08; sibling repo correctly uses 0.119.4+1.21.4). Accepted as PARTIAL — the artifact exists and the addon compiles, but the boundary claim is weaker than stated. |
| 3 | User can drag a Script node from the editor palette, connect, run, and the Script node passes through execution without error | ✗ FAILED | CR-01 CONFIRMED: Sidebar.hoveredAddonDefinition has exactly two assignments — both null (line 71 init, line 129 clear). addonCategoryNodes is populated by initializeAddonCategoryNodes() but never iterated in render or hit-test code. No addon category ever appears in the sidebar. The createNodeFromSidebar addon branch (line 1032) is unreachable. CR-03 CONFIRMED: ExecutionManager.createGraphSnapshot (lines 2936-3025) sets 20+ NodeData fields but never calls setAddonTypeId or setExtraFields. The cloned nodes have addonTypeId=null; executeAddonNode hits the null guard at Node.java:3812 and returns a SKIPPED completedFuture with a warning. The LuaNodeExecutor is dead code in a real run. |
| 4 | A saved preset containing a Script node round-trips through save/load with script text and _schema_version intact | ✗ FAILED | CR-02 CONFIRMED: NodeGraphPersistence.convertToNodes (lines 339-368) correctly handles ADDON nodes — but the editor loads presets via NodeGraph.applyLoadedData (line 15644), not convertToNodes. applyLoadedData constructs nodes with new Node(nodeData.getType(), x, y) and has no ADDON branch across its entire body (lines 15644-15820). NodeGraphClipboardSupport has zero references to addonTypeId or addonExtraFields (grep on entire file: 0 matches). After any editor open, undo, redo, copy, or paste, addonTypeId is null. The next save silently drops the node via the null-addonTypeId guard at NodeGraphPersistence:856-858. |
| 5 | Launching Minecraft without the addon jar leaves Pathmind fully functional across its 1.21-1.21.11 range with no errors | ✓ VERIFIED | Empty entrypoint list produces empty registrar, installs cleanly (NodeTypeRegistry logs no errors). No ADDON branch in execution, persistence, or sidebar runs when NodeTypeRegistry.INSTANCE.allDefinitions() returns empty. GSON omits null addonTypeId/extraFields from built-in node JSON (no serializeNulls). NodeTypeDefinition registers ADDON under CUSTOM with NON_DRAGGABLE_FROM_SIDEBAR guard so it does not appear in built-in sidebar categories. CAVEAT (WR-11): AddonLoader imports net.fabricmc.loader.api.* from the common module — latent NoClassDefFoundError risk on NeoForge when PathmindScreens calls AddonLoader.getFailedAddons() (lazy symbolic reference resolution currently protects this). |

**Score: 2/5 truths verified** (SC-1 and SC-5 verified; SC-2 partial — counted as not-verified due to boundary weaknesses; SC-3 and SC-4 failed)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `common/src/main/java/com/pathmind/api/addon/PathmindAddonEntrypoint.java` | Contract interface with registerNodes(NodeTypeRegistrar) | ✓ VERIFIED | @FunctionalInterface, void registerNodes(NodeTypeRegistrar registrar) |
| `common/src/main/java/com/pathmind/api/addon/NodeTypeRegistrar.java` | Mutable collector with validation + seal guard | ✓ VERIFIED | register(), seal(), id-format regex, null checks, duplicate check, post-seal guard |
| `common/src/main/java/com/pathmind/nodes/NodeTypeRegistry.java` | Singleton registry | ✓ VERIFIED | public static final NodeTypeRegistry INSTANCE; install(), hasType(), definitionFor(), executorFor(), serializerFor(), allDefinitions() |
| `common/src/main/java/com/pathmind/execution/AddonLoader.java` | Entrypoint discovery + D-11 check + failure isolation | ✓ VERIFIED | getEntrypointContainers("pathmind"), checkApiCompatibility with PathmindApiVersion.MIN_COMPATIBLE, per-addon catch(Throwable), markFailed |
| `common/src/main/java/com/pathmind/api/PathmindApiVersion.java` | API semver constants | ✓ VERIFIED | VERSION = "0.1.0", MIN_COMPATIBLE = "0.1.0" |
| `common/src/main/java/com/pathmind/nodes/NodeType.java` | ADDON sentinel constant | ✓ VERIFIED | ADDON("pathmind.node.type.addon", 0xFF888888, "pathmind.node.type.addon.desc") |
| `common/src/main/java/com/pathmind/nodes/Node.java` | addonTypeId field + ADDON execution branch | ✓ VERIFIED | addonTypeId field + getter/setter, Node(String addonTypeId, int x, int y) constructor, execute() returns early on ADDON, executeAddonNode() dispatches via NodeTypeRegistry. NOTE: the executor is unreachable in real runs due to CR-03. |
| `common/src/main/java/com/pathmind/data/NodeGraphData.java` | addonTypeId + extraFields on NodeData | ✓ VERIFIED | private String addonTypeId, private Map<String,Object> extraFields with getters/setters |
| `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` | ADDON save/load branches with serializerFor | ✓ VERIFIED (partial) | save: serializerFor branch at line 855+; load: convertToNodes branch at line 339+. NOT used by the editor's applyLoadedData path (CR-02). |
| `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java` | addonCategoryNodes + initializeAddonCategoryNodes + render/hit-test | ✗ STUB | addonCategoryNodes map is populated, hoveredAddonDefinition field exists, createNodeFromSidebar branch exists — but the render loop and hit-test loop NEVER iterate addonCategoryNodes. hoveredAddonDefinition is never set non-null. The addon sidebar category is visually absent and structurally dead. |
| `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` | ADDON renderNode branch + drag wiring | ✓ PARTIAL | renderAddonNodeContent() exists and is called from the renderNode else-if chain (line 4084). Drag wiring in createNodeFromSidebar exists but is unreachable (depends on hoveredAddonDefinition which is never set). |
| `common/src/main/java/com/pathmind/screen/PathmindScreens.java` | D-08 failure surfacing | ✓ VERIFIED | surfaceAddonLoadFailures() iterates AddonLoader.getFailedAddons() and calls NodeErrorNotificationOverlay.getInstance().show() |
| `fabric/src/main/java/com/pathmind/PathmindMod.java` | AddonLoader.discoverAndLoad() in onInitialize | ✓ VERIFIED | Line 31: com.pathmind.execution.AddonLoader.discoverAndLoad(); before the success log at line 33 |
| `fabric/build.gradle.kts` | maven-publish + mavenFabric publication | ✓ VERIFIED | id("maven-publish") in plugins, create<MavenPublication>("mavenFabric") block with artifactId = "pathmind-fabric" |
| `docs/addon-api-getting-started.md` | Third-party getting-started guide | ✓ VERIFIED (with caveat) | Contains modCompileOnly, "pathmind" entrypoint key, node-id regex. Caveat: Fabric API version 0.102.0 at line 93 does not exist for 1.21.4 (WR-08). |
| `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/resources/fabric.mod.json` | "pathmind" entrypoint + dependency | ✓ VERIFIED | "pathmind": ["com.mrmysterium.pathmindlua.LuaAddonEntrypoint"], "pathmind": ">=0.1.0" |
| `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaAddonEntrypoint.java` | implements PathmindAddonEntrypoint, registers node | ✓ VERIFIED | implements PathmindAddonEntrypoint, registrar.register(...) with LuaNodeExecutor, LuaNodeSerializer, LuaScriptNodeRenderer. Zero impl imports. |
| `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeSerializer.java` | _schema_version + script persistence | ✓ VERIFIED | _schema_version=1, "script" field, ((Number) rawVersion).intValue() — no (Integer) cast |
| `common/src/test/java/com/pathmind/nodes/NodeTypeRegistryTest.java` | Registration round-trips + failure modes | ✓ VERIFIED | Exists. Covers duplicate-id, null executor, post-seal, malformed id, double-install. |
| `common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java` | Save/load round-trip test | ✗ PARTIAL | Exists and tests serializer behavior, GSON Double erasure, NodeData GSON cycle. Does NOT test NodeGraphPersistence.convertToNodes or buildNodeGraphData — the actual persistence code under test is never exercised. WR-10: installSyntheticRegistry @BeforeAll installs a global singleton that is unused by any test. |
| `common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarTest.java` | Sidebar grouping test | ✗ PARTIAL | Exists and tests groupByCategory() logic directly. Does not detect CR-01 because it tests the helper in isolation — it never verifies that addonCategoryNodes entries are rendered or that hoveredAddonDefinition is set. WR-10: assertNull(null, ...) tautological test. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `PathmindMod.onInitialize` | `AddonLoader.discoverAndLoad()` | call at line 31 | ✓ WIRED | Verified in source |
| `AddonLoader.java` | `PathmindApiVersion.MIN_COMPATIBLE` | D-11 checkApiCompatibility | ✓ WIRED | SemanticVersion.parse(PathmindApiVersion.MIN_COMPATIBLE) at line 122 |
| `AddonLoader.java` | `NodeTypeRegistry.INSTANCE.install` | line 88 after loop | ✓ WIRED | Verified |
| `Node.java execute(int)` | `NodeTypeRegistry.INSTANCE.executorFor` | executeAddonNode() via ADDON branch | ✓ WIRED | Node.java:3812-3848 — correctly structured but UNREACHABLE in real runs because createGraphSnapshot (CR-03) drops addonTypeId before convertToNodes |
| `NodeGraphPersistence save` | `AddonNodeSerializer.serialize` via `serializerFor` | buildNodeGraphData line 862 | ✓ WIRED | Verified |
| `NodeGraphPersistence load (convertToNodes)` | `AddonNodeSerializer.deserialize` via `serializerFor` | line 345 | ✓ WIRED | Exists but this path not used by the editor (CR-02) |
| `NodeGraph.applyLoadedData` | ADDON field restoration | should call setAddonTypeId | ✗ NOT WIRED | applyLoadedData never calls setAddonTypeId or setAddonExtraFields — CR-02 |
| `ExecutionManager.createGraphSnapshot` | `nodeData.setAddonTypeId` | should set before convertToNodes | ✗ NOT WIRED | createGraphSnapshot has no ADDON branch — CR-03 |
| `Sidebar render loop` | `addonCategoryNodes` iteration | should draw category headers + entries + set hoveredAddonDefinition | ✗ NOT WIRED | addonCategoryNodes populated but never consumed by any render or hit-test code — CR-01 |
| `pathmind-lua/build.gradle.kts` | `com.pathmind:pathmind-fabric` via `modCompileOnly` | mavenLocal() first | ✓ WIRED | modCompileOnly("com.pathmind:pathmind-fabric:$pathmindVersion") |
| `fabric.mod.json (pathmind-lua)` | `LuaAddonEntrypoint` via "pathmind" entrypoint | entrypoints block | ✓ WIRED | Verified |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `Sidebar.addonCategoryNodes` | addon category map | NodeTypeRegistry.INSTANCE.allDefinitions() | Yes — when addon is installed | ✓ FLOWING (to the map) — but data DOES NOT FLOW to the render output (CR-01: map is never rendered) |
| `Node.addonTypeId` | addon type identity | Set by Node(String,int,int) constructor or setAddonTypeId() | Yes in test/new-node path | ✗ DISCONNECTED in editor load path (applyLoadedData never sets it) and in execution clone path (createGraphSnapshot never preserves it) |
| `NodeGraphData.NodeData.extraFields` | addon blob | AddonNodeSerializer.serialize() | Yes | ✓ FLOWING in save path — ✗ DISCONNECTED in editor-load path (applyLoadedData drops it) |

### Behavioral Spot-Checks

Step 7b: SKIPPED — no runnable entry points available without launching Minecraft. The critical defects are confirmed by static analysis (code reading) without needing runtime verification.

### Probe Execution

Step 7c: No probes declared in PLAN files. No conventional probe scripts found.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| API-01 | Plan 01 | Addon declares "pathmind" entrypoint; registerNodes called | ✓ SATISFIED | AddonLoader.discoverAndLoad() wired, getEntrypointContainers("pathmind") calls registerNodes |
| API-02 | Plan 01 | Addon can register custom node types through a typed registrar | ✓ SATISFIED | NodeTypeRegistrar.register(def, exec, ser) with validation |
| API-03 | Plan 01 | Registration validated at load time with informative errors naming the addon | ✓ SATISFIED | catch(Throwable) logs addonId, markFailed records it; D-11 also names the addon |
| API-04 | Plan 01 | Lifecycle ordering guaranteed; sealed after init | ✓ SATISFIED | registrar.seal() + NodeTypeRegistry.install(registrar) at end of discoverAndLoad |
| API-05 | Plan 02 | Addon nodes persist as opaque schema-versioned blob | ✗ BLOCKED | NodeGraphPersistence save/load branches exist but are bypassed by applyLoadedData in the editor (CR-02). The blob does not survive a real editor open+save cycle. |
| API-06 | Plan 02 | Addon executors run asynchronously via CompletableFuture | ✗ BLOCKED | Executor dispatch code in executeAddonNode() is correct but dead — createGraphSnapshot drops addonTypeId so the executor is never reached in a real run (CR-03) |
| API-07 | Plan 02 | Addon nodes can render custom content via body renderer hook | ~ PARTIAL | renderAddonNodeContent() is correctly wired in the renderNode chain (line 4084). The renderer IS called for ADDON nodes on the canvas. However, since nodes cannot be placed from the sidebar (CR-01) and lose their identity after editor load (CR-02), the renderer only fires for nodes placed by other means with addonTypeId intact. |
| API-08 | Plan 03 | Separate API artifact published; addon compiles with zero impl classes | ~ PARTIAL | Artifact published; addon compiles without impl imports; but published jar contains all impl classes (WR-09) — boundary is convention-only |
| API-09 | All plans | Pathmind runs unchanged with no addons | ✓ SATISFIED | Empty entrypoint list → empty registrar → clean install; no ADDON branches run; built-in JSON unchanged |
| API-10 | Plan 03 | Addon API documented for third parties | ~ PARTIAL | getting-started guide exists and is substantive; wrong Fabric API version (WR-08) breaks copy-paste build |
| LUA-01 | Plan 02 | User can grab Script node from palette and place it | ✗ BLOCKED | CR-01: Sidebar never renders addon categories; hoveredAddonDefinition never set; Script node cannot be placed from the UI |
| LUA-05 | Plan 02 | Script text persists through save/load with _schema_version | ✗ BLOCKED | CR-02: applyLoadedData loses addonTypeId and extraFields; next save silently drops the node |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `Sidebar.java` | 71, 129 | hoveredAddonDefinition only ever assigned null — field reads at 1025/1032 are permanently null | Blocker | CR-01: addon palette is dead code; LUA-01 undelivered |
| `ExecutionManager.java` | 2936-3025 | createGraphSnapshot builds NodeData without addonTypeId/extraFields for ADDON nodes | Blocker | CR-03: executor unreachable in real runs; API-06 and API-06 contract not exercised |
| `NodeGraph.java` | 15644-15820 | applyLoadedData builds nodes without ADDON field restoration | Blocker | CR-02: editor load destroys ADDON identity; next save drops node silently |
| `NodeGraphClipboardSupport.java` | whole file | Zero references to addonTypeId or addonExtraFields | Blocker | CR-02 extension: undo/redo, copy/paste all strip ADDON fields |
| `AddonNodePersistenceTest.java` | 65-86 | @BeforeAll installs global NodeTypeRegistry singleton unused by any test | Warning | WR-10: pollutes JVM-wide singleton; future tests order-dependent |
| `AddonSidebarTest.java` | 136-148 | assertNull(null, ...) — tautological assertion | Warning | WR-10: tests nothing; hides that CR-01 was not caught |
| `NodeTypeRegistrar.java` | 66-68 | seal() is public — any addon can seal the shared registrar, disabling all subsequent addons | Warning | WR-06: one bad addon defeats failure isolation of all others |
| `NodeGraph.java` | 7411-7413 | LoggerFactory.getLogger inside catch called per frame on renderer failure | Warning | WR-05: log flood (60+/sec per broken addon renderer) |
| `docs/addon-api-getting-started.md` | 93 | Fabric API version 0.102.0+1.21.4 does not exist (correct: 0.119.4+1.21.4) | Warning | WR-08: third-party dev following the guide gets resolution failure on first try |
| `NodeTypeRegistrar.java` | 45-49 | Regex allows . and / in name segment — ../../../evil passes; security claim is false | Warning | WR-03: path traversal not actually blocked |
| `AddonLoader.java` | 8-12 | Fabric-only imports in common module | Warning | WR-11: latent NoClassDefFoundError on NeoForge; lazy JVM ref resolution currently hides it |
| `Node.java` | 3829-3843 | whenComplete runs on arbitrary addon thread; failure path touches Minecraft client state | Warning | WR-04: latent race/crash when Phase 2 Lua VM completes from worker thread |

### Human Verification Required

None — all critical defects are structurally verifiable from code. End-of-phase in-game verification (from Plan 03 Task 3 human-check) CANNOT be meaningfully performed until CR-01/CR-02/CR-03 are fixed, since the observable behaviors (sidebar category visible, node placeable, preset round-trip) are blocked by the code defects.

### Gaps Summary

Three critical review findings are independently confirmed as BLOCKERS:

**CR-01 (BLOCKER — LUA-01 and API-07 partially):** The sidebar addon category palette is structurally dead. `addonCategoryNodes` is populated at construction but the sidebar render and hit-test loops never iterate it. `hoveredAddonDefinition` is never assigned a non-null value in the entire 1,000+ line Sidebar.java file — the only two assignments are the null initializer (line 71) and the null clear in `initializeAddonCategoryNodes` (line 129). The `createNodeFromSidebar` addon branch (line 1032) is therefore permanently unreachable. No addon category ever appears to the user. The Script node cannot be placed from the UI. Success criterion 3 (drag from palette) is entirely unmet. The SUMMARY.md commit `a6032d3` claims "sidebar addon-category palette, drag-to-canvas" — this is false; the data structures exist but none of the UI wiring does.

**CR-02 (BLOCKER — LUA-05 and API-05):** Editor preset loading goes through `NodeGraph.applyLoadedData`, which constructs nodes with `new Node(nodeData.getType(), x, y)` and never restores `addonTypeId` or `addonExtraFields`. The ADDON persistence code in `NodeGraphPersistence.convertToNodes` is correct but is not used by the editor. After any editor open — including automatic preset loads on screen open — all ADDON nodes have `addonTypeId=null`. On the next save (including workspace autosave), the null-addonTypeId guard silently drops the node via `continue`. This is silent data loss on a normal workflow. Undo/redo, copy, and paste compound the issue — `NodeGraphClipboardSupport` has zero awareness of ADDON fields. Success criterion 4 (preset round-trip) is entirely unmet.

**CR-03 (BLOCKER — API-06):** Every execution path in `ExecutionManager` clones the live graph via `createGraphSnapshot` before passing it to `convertToNodes` and running it. `createGraphSnapshot` builds `NodeData` objects without ever setting `addonTypeId` or `extraFields`. The cloned nodes have `addonTypeId=null`, so `convertToNodes` skips the ADDON deserialize branch, and at run time `Node.executeAddonNode` hits the null check at line 3812 and returns `completedFuture(null)` with a warning. The registered `LuaNodeExecutor.execute()` is never called in a real graph run. Success criterion 3 ("Script node passes through execution") cannot be met even if CR-01 were fixed independently, because execution uses the snapshot path.

**Root cause:** Four separate `Node ↔ NodeData` conversion sites exist in the codebase (`buildNodeGraphData`, `convertToNodes`, `applyLoadedData`, `createGraphSnapshot`). The ADDON field handling was added to only two of them. The unit tests (AddonNodePersistenceTest, AddonSidebarTest) test the correct paths in isolation and cannot detect that the editor uses different paths.

The fix surface is narrow and well-localized as noted in CR-02 and CR-03. Plan the gap closure as:
1. Add ADDON branch to `applyLoadedData` (mirrors `convertToNodes` lines 339-368)
2. Add ADDON fields to `createGraphSnapshot` (3 lines)
3. Add ADDON fields to `NodeGraphClipboardSupport.buildGraphData` and its instantiate path
4. Add sidebar render + hit-test iteration over `addonCategoryNodes` with `hoveredAddonDefinition` assignment
5. Add a regression test that round-trips through `createGraphSnapshot + convertToNodes` and asserts `addonTypeId` is non-null

---

_Verified: 2026-06-13_
_Verifier: Claude (gsd-verifier)_
