---
phase: 01-api-foundation-script-node-registration
reviewed: 2026-06-13T00:00:00Z
depth: standard
files_reviewed: 39
files_reviewed_list:
  - common/src/main/java/com/pathmind/api/PathmindApiVersion.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeBodyRenderer.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeCategory.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeDefinition.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeExecutor.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeSerializer.java
  - common/src/main/java/com/pathmind/api/addon/NodeResult.java
  - common/src/main/java/com/pathmind/api/addon/NodeTypeRegistrar.java
  - common/src/main/java/com/pathmind/api/addon/PathmindAddonEntrypoint.java
  - common/src/main/java/com/pathmind/data/NodeGraphData.java
  - common/src/main/java/com/pathmind/data/NodeGraphPersistence.java
  - common/src/main/java/com/pathmind/execution/AddonLoader.java
  - common/src/main/java/com/pathmind/nodes/Node.java
  - common/src/main/java/com/pathmind/nodes/NodeType.java
  - common/src/main/java/com/pathmind/nodes/NodeTypeDefinition.java
  - common/src/main/java/com/pathmind/nodes/NodeTypeRegistry.java
  - common/src/main/java/com/pathmind/screen/PathmindScreens.java
  - common/src/main/java/com/pathmind/ui/graph/NodeGraph.java
  - common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java
  - common/src/main/resources/assets/pathmind/lang/en_us.json
  - common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java
  - common/src/test/java/com/pathmind/nodes/NodeTypeRegistryTest.java
  - common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarTest.java
  - docs/addon-api-getting-started.md
  - fabric/build.gradle.kts
  - fabric/src/main/java/com/pathmind/PathmindMod.java
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/.gitignore
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/build.gradle.kts
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/docs/dev-loop.md
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/gradle.properties
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/settings.gradle.kts
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaAddonEntrypoint.java
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeExecutor.java
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeSerializer.java
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java
  - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/resources/fabric.mod.json
findings:
  critical: 3
  warning: 11
  info: 5
  total: 19
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-06-13
**Depth:** standard
**Files Reviewed:** 39 (27 Pathmind + 12 pathmind-lua sibling repo)
**Status:** issues_found

## Summary

The API surface itself (`com.pathmind.api.addon`) is clean, well-documented, and the sibling pathmind-lua repo correctly imports only `com.pathmind.api.*` types (verified — zero impl imports). The registrar/registry seal pattern, per-addon failure isolation, and GSON persistence model are soundly designed.

However, the integration into Pathmind's existing impl is critically incomplete. The phase's headline claims — "sidebar addon-category palette, drag-to-canvas" (commit a6032d3) and LUA-01 "node is placeable and runnable" — are **not functional**:

1. The sidebar addon palette is dead code: nothing renders the addon categories and nothing ever sets the hover state, so an addon node can never be created from the UI.
2. The new `addonTypeId`/`extraFields` were added to only one of the four `NodeData` conversion paths in the codebase. Execution snapshots, editor preset loads, undo/redo, and copy/paste all silently strip the addon identity — and the save path then **silently deletes** the stripped node, destroying the user's script (data loss).
3. Because the execution path clones the graph through a conversion that drops `addonTypeId`, an ADDON node never reaches its registered executor in a real run.

These are integration-wiring defects, not design defects — the fix surface is narrow and well-localized.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: Addon sidebar palette is dead code — addon nodes can never be placed from the UI

**File:** `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java:71` (also 127-130, 1025, 1032-1035)
**Issue:** `hoveredAddonDefinition` is declared (line 71), cleared (line 129), and read (lines 1025, 1032) — but **never assigned a non-null value anywhere in the codebase**. Likewise, `addonCategoryNodes` is populated by `initializeAddonCategoryNodes()` (line 130) but never consumed by any rendering or hit-testing code; its only other reader is the test accessor `getAddonCategoryNodesForTest()` (line 164). Sidebar rendering and hover detection still iterate only the built-in `NodeCategory` enum. Consequently:
- No addon category ("Scripting") ever appears in the sidebar (D-05 not delivered).
- `createNodeFromSidebar`'s addon branch (line 1032) is unreachable; drag-to-canvas for addon nodes (D-06, LUA-01) cannot occur.
- The dev-loop verification step in `pathmind-lua/docs/dev-loop.md` ("The 'Scripting' category should appear with a 'Lua Script' node") will fail in-game.

Commit a6032d3 claims this feature was implemented; the render/hover wiring is missing entirely.
**Fix:** In the sidebar render loop, after the built-in categories, render one collapsible section per `addonCategoryNodes` key (header from `AddonNodeCategory.getDisplayName()/getColor()/getIcon()`, entries from `AddonNodeDefinition.getDisplayName()`), and in the existing hover hit-test set `hoveredAddonDefinition = def` / clear it alongside `hoveredNodeType`. Add a render-path regression test or manual checklist item that the category appears with one registered definition.

### CR-02: Editor load, undo/redo, and clipboard all strip `addonTypeId`/`extraFields`; the save path then silently deletes the node — user data loss

**File:** `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java:15644-15703` (applyLoadedData), `common/src/main/java/com/pathmind/ui/graph/NodeGraphClipboardSupport.java:222-292` (buildGraphData), `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java:853-858`
**Issue:** The ADDON restore logic was added only to `NodeGraphPersistence.convertToNodes` (line 340) and the ADDON save logic only to `buildNodeGraphData` (line 853). But the editor does **not** use `convertToNodes` — it loads presets through `NodeGraph.applyLoadedData` (called from preset load at lines 15470/15485/15872 and from undo/redo via `restoreFromSnapshot` at line 1549), which constructs nodes with `new Node(nodeData.getType(), ...)` and never calls `setAddonTypeId`/`setAddonExtraFields`. Similarly, `NodeGraphClipboardSupport.buildGraphData` — used for undo snapshots (`pushUndoState`), copy, cut, and duplicate — copies ~20 NodeData fields but not `addonTypeId`/`extraFields` (verified: zero references to these fields in that file).

The failure chain: load a preset containing a Lua Script node into the editor (or press undo once) → the in-memory ADDON node has `addonTypeId == null` → on the next save, `buildNodeGraphData` hits the null-id guard at NodeGraphPersistence.java:856-858 and executes `continue`, **silently dropping the node and its script from the preset file**. The D-09 promise in `docs/addon-api-getting-started.md` ("placeholders preserve all stored data ... data intact") is violated in the most common path. Workspace autosave makes this destructive without any user action beyond opening the editor.
**Fix:** (1) In `applyLoadedData`, replicate (or extract and share) the ADDON branch from `convertToNodes` lines 340-368. (2) In `NodeGraphClipboardSupport.buildGraphData`, copy `addonTypeId` and a deep copy of `addonExtraFields` into NodeData; in its instantiate path, restore them. (3) Defense in depth: in `buildNodeGraphData`, never silently drop — preserve a node whose type is ADDON even with null `addonTypeId` (write it back with retained `extraFields`) and log loudly. The root cause is four duplicated Node↔NodeData converters; extract a single shared copy helper for the addon fields so a future field cannot be missed three times again.

### CR-03: ExecutionManager's graph snapshot drops `addonTypeId` — ADDON nodes are never executed, executor is unreachable in a real run

**File:** `common/src/main/java/com/pathmind/execution/ExecutionManager.java:2936-2976` (createGraphSnapshot), `common/src/main/java/com/pathmind/nodes/Node.java:3812-3815`
**Issue:** Every execution path clones the live graph via `createGraphSnapshot(...)` → `NodeGraphPersistence.convertToNodes(snapshot)` (lines 2824, 2852). `createGraphSnapshot` builds `NodeData` manually and does not set `addonTypeId`/`extraFields`. The cloned NodeData therefore has `addonTypeId == null`, so `convertToNodes`' ADDON branch (guarded by `nodeData.getAddonTypeId() != null`) is skipped, and at run time `Node.executeAddonNode` hits the `addonTypeId == null` guard (Node.java:3812) and completes as a silent no-op with only a log warning. Net effect: **the API-06 execution contract is never exercised in-game** — `LuaNodeExecutor.execute` is dead code in a real run, and LUA-01 "runnable" is not met. The unit tests cannot catch this because they call the serializer/executor directly rather than through the execution clone path.
**Fix:** In `createGraphSnapshot`, add:
```java
if (node.getType() == NodeType.ADDON) {
    nodeData.setAddonTypeId(node.getAddonTypeId());
    nodeData.setExtraFields(node.getAddonExtraFields() != null
        ? new java.util.HashMap<>(node.getAddonExtraFields()) : null);
}
```
(or use the shared copy helper from CR-02). Add an integration-shaped test that round-trips an ADDON node through `createGraphSnapshot` + `convertToNodes` and asserts `getAddonTypeId()` is non-null.

## Warnings

### WR-01: D-11 compatibility check conflates the mod version space with the API version space

**File:** `common/src/main/java/com/pathmind/execution/AddonLoader.java:122-133`, `common/src/main/java/com/pathmind/api/PathmindApiVersion.java:9-13`, `docs/addon-api-getting-started.md:122-129`
**Issue:** The addon's `"pathmind"` dependency in fabric.mod.json is resolved by the Fabric loader against the **mod** version (`1.1.5+mc1.21.4` per gradle.properties), but `checkApiCompatibility` tests the same declared range against the **API** version (`SemanticVersion.parse("0.1.0")`). These are different version spaces. Consequences: an addon that follows normal Fabric convention and declares `"pathmind": ">=1.1.5"` passes the loader but fails `dep.matches(0.1.0)` and is **wrongly disabled**; an addon that declares the semver-correct cautious range for a 0.x API (e.g. `">=0.1.0 <0.2.0"`) is **hard-blocked by the Fabric loader** because 1.1.5 does not satisfy it. Only ranges that happen to span both 0.1.0 and 1.1.5 (like the shipped `">=0.1.0"`) work — the two-layer check functions today by coincidence, and the lower layer provides near-zero protection (every plausible range accepts 0.1.0).
**Fix:** Separate the channels: keep the fabric.mod.json `pathmind` dependency for mod-presence/mod-version gating, and declare the API requirement in a custom metadata field (e.g. `"custom": { "pathmind:api": ">=0.1.0" }`) that AddonLoader parses and checks against `PathmindApiVersion.VERSION`. Update the getting-started guide accordingly.

### WR-02: AddonNodeDefinition displayName, color, and provenance label are never rendered — D-07 unimplemented, all addon nodes look identical

**File:** `common/src/main/java/com/pathmind/api/addon/AddonNodeDefinition.java:81`, `common/src/main/java/com/pathmind/nodes/Node.java:695-696`, `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java:7389`
**Issue:** `getProvenanceLabel()` has zero call sites in Pathmind (verified by grep across `common/src/main`); the provenance badge (D-07, claimed in commit a6032d3) does not exist. Node headers come from `Node.getDisplayName()` → `type.getDisplayName()`, so every addon node from every addon renders the generic gray "Addon Node" title with `NodeType.ADDON`'s `0xFF888888` color instead of `def.getDisplayName()` ("Lua Script") and `def.getColor()` (indigo). Two different addon node types are visually indistinguishable, and nothing marks the node as third-party-provided.
**Fix:** In the node header render path, when `type == NodeType.ADDON`, resolve `NodeTypeRegistry.INSTANCE.definitionFor(node.getAddonTypeId())` and use its displayName/color; render `getProvenanceLabel()` as a small badge when non-empty. For unresolved nodes fall back to "Addon Node (missing)" styling.

### WR-03: ID regex does not block path traversal despite the security claim; the "path traversal" test actually tests uppercase

**File:** `common/src/main/java/com/pathmind/api/addon/NodeTypeRegistrar.java:45-49`, `common/src/test/java/com/pathmind/nodes/NodeTypeRegistryTest.java:170-183`
**Issue:** The regex `^[a-z0-9_-]+:[a-z0-9_/.-]+$` permits `.` and `/` in the name segment, so `"mod:../../../evil"` and `"mod:a/../../b"` pass validation — directly contradicting the inline claim "blocks path-traversal ... (ASVS V5, T-01-01)" and the doc example in `addon-api-getting-started.md:177`. The ids are not currently used to build filesystem paths, so this is not exploitable today, but the control documented as a security boundary is ineffective, and any future use of the id in a path (e.g. per-addon config dirs) inherits the hole. Compounding this, the test named `pathTraversalIdWithColonIsRejected` registers `"UPPERCASE:id"` — it never tests traversal with a colon, so the gap is invisible to the suite.
**Fix:** Reject `..` sequences and leading/trailing separators, e.g. validate segments: split the name part on `/` and require each segment to match `^[a-z0-9_.-]+$` and not equal `.`/`..` — or simply drop `/` and `.` from the allowed set unless there is a concrete need. Fix the test to use `"valid_mod:../../../evil"` and assert rejection.

### WR-04: Addon executor completion runs Pathmind continuation and chat/UI calls on an arbitrary addon thread

**File:** `common/src/main/java/com/pathmind/nodes/Node.java:3829-3843`
**Issue:** `exec.execute(ctx).whenComplete(...)` runs the completion callback on whatever thread the addon completes its future on (the API explicitly tells addons to complete off-thread). The FAILURE branch calls `NodeExecutionCompletion.fail(...)` → `owner.sendNodeErrorMessage(client, message)`, which touches Minecraft client/chat state — not safe off the game thread. The SUCCESS branch `future.complete(null)` likewise resumes the downstream execution chain synchronously on the addon's worker thread, while every built-in node completes on the game thread; downstream node `execute()` implementations assume game-thread invariants (they call `MinecraftClient.getInstance()` and mutate client state directly). This is a latent race/crash that will surface the moment a real Lua VM (Phase 2) completes futures from its own executor.
**Fix:** Marshal completion back to the game thread:
```java
exec.execute(ctx).whenComplete((result, throwable) ->
    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
        ... existing handling ...
    }));
```

### WR-05: Addon body renderer failure logs every frame, contradicting its own "log once" comment

**File:** `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java:7409-7414`
**Issue:** `renderAddonNodeContent` is called per node per frame. When a renderer throws persistently (the realistic failure mode — e.g. NPE on every call), the catch block logs a WARN **every frame** (~60+/sec per node), flooding the log, despite the comment "renderer threw — log once". It also calls `org.slf4j.LoggerFactory.getLogger(NodeGraph.class)` inside the catch on every failure instead of using a static logger.
**Fix:** Use a static `Logger` and a `Set<String>` of addon type ids already warned (or a `markFailed`-style flag on the definition) so each failing renderer logs once; optionally disable the renderer for the session after first failure and fall back to the placeholder body permanently.

### WR-06: `NodeTypeRegistrar.seal()` is public API — any addon can seal the registrar and knock out all subsequently-loaded addons

**File:** `common/src/main/java/com/pathmind/api/addon/NodeTypeRegistrar.java:66-68`
**Issue:** The same registrar instance is passed sequentially to every entrypoint (`AddonLoader.discoverAndLoad`). Because `seal()` is public on the API surface, a buggy or hostile addon can call `registrar.seal()` inside `registerNodes`, causing every later addon's `register()` to throw `IllegalStateException` — those addons are then marked failed with a misleading "sealed" error. The getters (`getDefinitions()` etc.) are similarly exposed with only a Javadoc admonition ("Addons must not call"). Failure isolation (API-03) is defeated by a one-line addon bug.
**Fix:** Make `seal()` (and ideally the getters) package-private and move `NodeTypeRegistrar` consumption behind an internal accessor, or give each entrypoint its own registrar and merge them in `AddonLoader` (duplicate detection at merge time). Per-addon registrars also let a duplicate-id collision disable only the second addon rather than aborting its remaining registrations.

### WR-07: Hardcoded magic key `"script"` couples core persistence/execution to the Lua addon's storage convention

**File:** `common/src/main/java/com/pathmind/nodes/Node.java:3820-3823`, `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java:355-360, 868-871`, `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java:7405-7408`
**Issue:** Core Pathmind reads/writes the literal key `"script"` inside the supposedly *opaque* addon blob in three impl locations to shuttle `AddonNodeContext.scriptText` between serializer, executor, and renderer. This breaks the documented contract that `extraFields` is an opaque blob owned by the addon: (a) on save, the context passed to `serialize(ctx)` is reconstructed **solely** from the `"script"` key, so any context state an addon establishes in `deserialize` that is not the script text is silently dropped; (b) an addon that legitimately stores a user field named `"script"` with different semantics gets it overwritten by core (NodeGraphPersistence.java:356-358 injects `ctx.getScriptText()` under `"script"` into the retained blob); (c) the convention is undocumented in the API, so third parties cannot know that `scriptText` round-trips only via this key.
**Fix:** Keep the canonical context on the Node: store the deserialized `AddonNodeContext` itself (or its fields) on the Node instead of round-tripping through a reserved blob key, and pass that context to executor/renderer/serializer. If the blob bridge must stay for Phase 1, namespace the key (`"__pathmind_script"`), document it in `AddonNodeSerializer` Javadoc, and stop injecting it into the addon's own map.

### WR-08: Getting-started guide pins a Fabric API version that does not exist for 1.21.4 — copy-paste build fails for third parties

**File:** `docs/addon-api-getting-started.md:93`
**Issue:** The build script template declares `modImplementation("net.fabricmc.fabric-api:fabric-api:0.102.0+1.21.4")`. Fabric API `0.102.0` is the 1.21/1.21.1 line; there is no `0.102.0+1.21.4` artifact on the Fabric maven, so a third-party developer following the guide verbatim gets a dependency resolution failure. The sibling repo itself uses the correct `0.119.4+1.21.4` (pathmind-lua/gradle.properties:15), proving the doc was not exercised. Since this doc is the deliverable for API-08/API-10 ("a third party can consume the API"), a broken first-run experience undermines the phase's core value.
**Fix:** Change to `0.119.4+1.21.4` (or parameterize via `gradle.properties` like the rest of the template, which already shows `fabric_api_version` missing from the doc's properties block — add it there).

### WR-09: API boundary is convention-only — the published "API artifact" is the full impl jar, and the leak-check grep misses half the impl packages

**File:** `fabric/build.gradle.kts:170-179`, `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/build.gradle.kts:44-47`, `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/docs/dev-loop.md:77-84`
**Issue:** The publication ships the entire shadowed mod jar (common + fabric impl) under `com.pathmind:pathmind-fabric`. The comment in the addon build script — "Zero impl classes end up on this addon's classpath" — is false: every impl class (`ExecutionManager`, `NodeGraph`, `MarketplaceService`, ...) is on the addon's compile classpath and nothing fails at compile time if imported. The only guard is the manual grep in dev-loop.md, whose pattern omits `com.pathmind.screen`, `com.pathmind.util`, `com.pathmind.marketplace`, `com.pathmind.validation`, and `com.pathmind.compat` entirely (and uses unescaped dots, so it is also imprecise). The getting-started doc concedes this ("will fail at compile time **once** the API boundary is locked down") — i.e., API-08 enforcement is deferred, while the phase's stated core value is that the boundary is "real, stable, and consumable."
**Fix:** Short term: correct the misleading comments and extend the grep to a whitelist check (`grep -rn "import com\.pathmind\." src/main/java | grep -v "com\.pathmind\.api\."` must be empty) and wire it into the addon's `check` task. Medium term: publish a separate `pathmind-api` artifact containing only `com.pathmind.api.**` (a second jar task with an include filter is sufficient).

### WR-10: Test suite contains a tautological test and an unused global-singleton install that pollutes the JVM-wide registry

**File:** `common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarTest.java:136-148`, `common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java:65-86`
**Issue:** (a) `getHoveredAddonDefinition_returnsNullByDefault` ends in `assertNull(null, ...)` — it asserts a literal and can never fail; it tests nothing while inflating the count of "passing" D-06 tests (D-06 is in fact unimplemented, see CR-01 — a real test would have caught it). (b) `AddonNodePersistenceTest.installSyntheticRegistry` installs a registrar into the global `NodeTypeRegistry.INSTANCE` in `@BeforeAll`, but **none of the tests in the class use the INSTANCE registry** — they call `TEST_SERIALIZER` directly. The install is dead setup whose only effect is permanently consuming the install-once singleton for the whole test JVM: any future test that needs to install into `INSTANCE` (e.g. a real persistence round-trip through `convertToNodes`) will throw or be order-dependent. Note also that the class comment admits the actual changed production code (`NodeGraphPersistence` ADDON branches, lines 340-368 and 853-882) is never executed by any test.
**Fix:** Delete the tautological test (or implement a real one once CR-01 wiring exists). Remove the `@BeforeAll` install, or convert the suite to exercise `convertToNodes`/`buildNodeGraphData` with a fresh `NodeTypeRegistry` instance injected (consider making the registry reference used by persistence swappable for tests).

### WR-11: Fabric-loader API used directly in the `common` module breaks the platform abstraction; NeoForge silently has no addon support

**File:** `common/src/main/java/com/pathmind/execution/AddonLoader.java:8-12`, `common/src/main/java/com/pathmind/screen/PathmindScreens.java:39-58`
**Issue:** `AddonLoader` imports `net.fabricmc.loader.api.*` yet lives in `common`, which is shared with the NeoForge build (architecture doc: compat/abstraction layers exist precisely to keep loader-specific APIs out of common). On NeoForge: (a) `discoverAndLoad` is never called (only fabric's `PathmindMod` calls it), so `NodeTypeRegistry` is never installed and every ADDON node in a shared preset is silently unresolved — acceptable for Phase 1 but undocumented; (b) `PathmindScreens.surfaceAddonLoadFailures` calls `AddonLoader.getFailedAddons()` on every editor open, loading a class whose other methods reference Fabric-only types. This currently survives only because HotSpot resolves symbolic references lazily — a verifier/AOT/different-JVM change, or any future code touching `discoverAndLoad`'s signature from common, produces `NoClassDefFoundError: net/fabricmc/loader/api/FabricLoader` at runtime on NeoForge.
**Fix:** Split AddonLoader: keep the loader-agnostic state (`failedAddons`, `markFailed`, `getFailedAddons`) in a common class, and move Fabric entrypoint discovery (`discoverAndLoad`, `checkApiCompatibility`) into the `fabric` module (or behind an Architectury `@ExpectPlatform` hook with a NeoForge no-op). Document that addon support is Fabric-only in v1.

## Info

### IN-01: Tests use bare `assert` statements instead of JUnit assertions

**File:** `common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java:175-178, 217-218`
**Issue:** `assert json.contains(...)` only fails when JVM assertions are enabled. Gradle's Test task enables `-ea` by default, so they currently run — but any execution environment without `-ea` (IDE configs, custom runners) silently skips these checks, and they are inconsistent with the `assertEquals`-style assertions in the same file.
**Fix:** Replace with `assertTrue(json.contains(...), "...")`.

### IN-02: Freshly placed addon node has no script until a save/load cycle; deserializer defaults are dropped when `extraFields` is null

**File:** `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java:350-360`, `common/src/main/java/com/pathmind/nodes/Node.java:3820`
**Issue:** A node created via `new Node(addonTypeId, x, y)` has `addonExtraFields == null`, so the renderer/executor see `scriptText == null` and `LuaNodeSerializer.DEFAULT_SCRIPT` only materializes after the first save→load round trip. Relatedly, in the load path, when `nodeData.getExtraFields()` is null but the deserializer sets context defaults, the `node.getAddonExtraFields() != null` guard drops the default (`addonExtraFields` stays null).
**Fix:** After successful `deserialize`, create the retained map when null: `if (node.getAddonExtraFields() == null) node.setAddonExtraFields(new HashMap<>());` before injecting the script; and on sidebar placement, run the serializer/deserializer once to seed defaults.

### IN-03: Null `NodeResult` from an addon future is treated as SUCCESS

**File:** `common/src/main/java/com/pathmind/nodes/Node.java:3831-3839`
**Issue:** If a buggy addon completes its future with `null`, the `result == NodeResult.FAILURE` check is false and the node silently succeeds. The API Javadoc only forbids a null *future*, not a null result value.
**Fix:** Treat `result == null` as FAILURE (or log a warning naming the addon) so contract violations are visible.

### IN-04: D-08 failure notifications replay on every editor open with no acknowledgement or clearing

**File:** `common/src/main/java/com/pathmind/screen/PathmindScreens.java:47-58`, `common/src/main/java/com/pathmind/execution/AddonLoader.java:48, 167-189`
**Issue:** `failedAddons` is a static map that is never cleared, so the same failure toast re-fires every single time the editor is opened for the entire session. The Javadoc ("Each failure is shown once per editor open") matches the code, but the UX of a permanent, unacknowledgeable warning will train users to ignore it. Minor: the map is an unsynchronized `LinkedHashMap` written at init and read from the render thread.
**Fix:** Track a `shown` flag (per session or until the user dismisses) or surface failures in a dedicated panel instead of repeated toasts.

### IN-05: Impl-leak grep in dev-loop.md is imprecise

**File:** `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/docs/dev-loop.md:77-84`
**Issue:** Pattern uses unescaped dots and omits several impl packages (see WR-09); also `grep ... && echo FOUND || echo NO` inverts cleanly only because grep's exit code semantics happen to align — a file-read error also prints "NO IMPL LEAK".
**Fix:** Use the whitelist form from WR-09 with `grep -E "import com\.pathmind\."` and an explicit `api` exclusion.

---

_Reviewed: 2026-06-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
