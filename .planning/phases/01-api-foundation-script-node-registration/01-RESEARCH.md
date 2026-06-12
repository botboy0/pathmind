# Phase 1: API Foundation + Script Node Registration — Research

**Researched:** 2026-06-12
**Domain:** Fabric mod addon API retrofit (Java 21 / Architectury / Loom 1.14); addon-consumed Maven artifact; string-keyed node registry; JSON preset persistence; Fabric sidebar extension
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Addon mod ID is `pathmind-lua`, jar `pathmind-lua-x.y.z.jar`.
- **D-02:** Sibling repo lives at `C:\Users\Trynda\Desktop\Dev\sidequests\pathmind-lua`.
- **D-03:** Ownership split: API code stays under `com.pathmind` (upstream fork). Lua addon is user's own project under namespace `com.mrmysterium` (e.g., `com.mrmysterium.pathmindlua`), author metadata `mr_mysterium`.
- **D-04:** Entrypoint key in `fabric.mod.json` is `pathmind`.
- **D-05:** Addons declare their own palette categories via the registration API. No generic "Addons" bucket.
- **D-06:** Phase 1 node body shows a read-only script preview (first lines of stored script text / default placeholder) — proves persistence without editor functionality.
- **D-07:** Addon-provided nodes carry a subtle visual provenance marker (badge or accent color indicating the providing addon).
- **D-08:** Failed registration (throw, duplicate node ID, null executor, etc.) → whole addon disabled, game keeps running. Full error logged; in-game warning via `NodeErrorNotificationOverlay` when editor opens.
- **D-09:** Presets containing addon nodes whose addon is missing/failed load as inert grayed-out placeholders that preserve JSON data — preset round-trips losslessly and works again when addon returns.
- **D-10:** Addon API has its own independent semver, starting at 0.x; 1.0 marks stable API milestone.
- **D-11:** Two-layer compatibility enforcement: addon declares API version range in `fabric.mod.json` (Fabric loader blocks hard mismatches) **plus** runtime check at registration — incompatible addons disabled via standard failure UX (D-08).

### Claude's Discretion

- Exact Maven coordinates/artifact naming for the API (`com.pathmind:pathmind-api` vs per-MC-version variants) — decide during planning based on how the Architectury multi-version build emits artifacts.
- Exact addon Java package layout under `com.mrmysterium`.
- API package boundary mechanics (package inside `common` rather than a separate Gradle module for v1).
- NodeType enum integration strategy (ADDON sentinel + string-keyed `NodeTypeRegistry`).

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope. (v2 items already tracked in REQUIREMENTS.md: full UI extension points, lifecycle hooks, NeoForge addon loading, Modrinth Maven publishing.)
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| API-01 | Addon declares `pathmind` entrypoint in `fabric.mod.json`, discovered via `FabricLoader.getEntrypointContainers` | Fabric entrypoint pattern confirmed; `PathmindMod.onInitialize` is the insertion point |
| API-02 | Addon registers custom node types (definition, executor, serializer) through a typed registrar | Registrar-passed-to-plugin pattern (JEI/REI); `NodeTypeRegistrar` holds triples until sealed |
| API-03 | Registration validated at load time with informative errors naming the offending addon | Try/catch per container; log with `container.getProvider().getMetadata().getId()`; surface via `NodeErrorNotificationOverlay` |
| API-04 | Lifecycle ordering guaranteed; addon registration safe regardless of Fabric entrypoint init order | Deferred-registration guard or `PATHMIND_READY` custom event; queued registrations flushed after Pathmind init completes |
| API-05 | Addon nodes persist addon-declared data inside Pathmind's JSON presets as opaque, schema-versioned blob | `NodeData.extraFields` map (new field); `AddonNodeSerializer.serialize/deserialize` contract; `_schema_version` in first commit |
| API-06 | Addon node executors run asynchronously (`CompletableFuture` polled per tick by ExecutionManager), never block game thread | Phase 1 uses graceful no-op completion; `CompletableFuture` polling established in `ExecutionManager.advanceTick()` |
| API-07 | Addon nodes can render custom content in node body via minimal UI widget hook | Phase 1: read-only script preview via `AddonNodeBodyRenderer` functional interface called from `NodeGraph`/`Sidebar` rendering |
| API-08 | Separate API artifact published to local Maven; sibling repo compiles with zero impl classes on classpath | `maven-publish` + `publishToMavenLocal`; Loom auto-wires `remapJar`; API package discipline enforced by what's published |
| API-09 | Pathmind fully functional standalone across MC 1.21–1.21.11 with no addons installed | `AddonLoader` calls `getEntrypointContainers` → returns empty list when no addon present; all new code paths guarded by `if (type == NodeType.ADDON)` |
| API-10 | Addon API documented (javadoc + getting-started guide) | Javadoc on all `com.pathmind.api` types; `docs/addon-api-getting-started.md` written in Phase 1 |
| LUA-01 | User can drag Script node from editor palette and place it | `AddonNodeDefinition` registered with category "Scripting"; `Sidebar` reads `AddonCategoryRegistry`; `NodeGraph` handles drag from addon entries |
| LUA-05 | Script text persists with node through preset save/load with `_schema_version` | `LuaNodeSerializer` writes `{ "script": "...", "_schema_version": 1 }` into `NodeData.extraFields`; deserialized on load |
</phase_requirements>

---

## Summary

Phase 1 is entirely about infrastructure: establishing the addon API contract, wiring up the discovery mechanism, publishing a consumable artifact, and proving it works by registering a real (but execution-inert) Script node from the sibling repo. All three moving pieces — Pathmind's API plumbing, the Maven dev loop, and the addon skeleton — must land before Phase 2 can run.

The existing codebase is well-understood. The three integration points that require concrete code changes are: (1) `PathmindMod.onInitialize` gains an `AddonLoader` call; (2) `NodeType` enum gains one sentinel value `ADDON`; (3) `Sidebar.initializeCategoryNodes()` gains a second population pass that reads from `AddonCategoryRegistry`. Everything else in Phase 1 is new code placed in new files under `com.pathmind.api` and in the sibling repo.

The biggest planning risk is the Sidebar extension. `Sidebar` is a concrete class with `Map<NodeCategory, ...>` fields keyed on the `NodeCategory` enum. Addon-provided categories cannot be added to a Java enum at runtime, so the Sidebar must be extended to also hold `Map<AddonNodeCategory, ...>` parallel maps — or the sidebar's internal model must be generalized to a common interface. The research recommends a parallel `addonCategories` map rather than attempting to generalize the entire Sidebar class in Phase 1.

**Primary recommendation:** Implement the `com.pathmind.api` package boundary and `AddonLoader` in Pathmind, publish via `maven-publish` to `mavenLocal`, then build the sibling repo skeleton against it. Every Phase 1 task is testable with the existing `./gradlew :common:test` suite plus one new unit test confirming `NodeTypeRegistry` round-trips an addon registration.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Addon entrypoint discovery | Platform Layer (Fabric mod init) | Common/API layer | `FabricLoader.getEntrypointContainers` is Fabric-specific; result is handed to `AddonLoader` in common |
| Addon node type registration | Common/API layer (`AddonLoader` + `NodeTypeRegistry`) | — | Registration state lives in a singleton registry in common; platform layer triggers it |
| Node palette display (sidebar) | Client/UI layer (`Sidebar.java`) | Common/API layer (provides `AddonNodeDefinition` metadata) | Rendering always client-side; definition data comes from the API layer |
| JSON persistence of addon nodes | Data layer (`NodeGraphData`, `NodeGraphPersistence`) | Common/API layer (`AddonNodeSerializer`) | File I/O owned by data layer; addon-specific serialization delegated through API contract |
| Addon failure UX | Client/UI layer (`NodeErrorNotificationOverlay`) | Common/API layer (failure recorded in `AddonLoader`) | Error must be displayed in editor context; underlying cause stored by loader |
| Maven artifact publishing | Build system (`fabric/build.gradle.kts`) | — | Loom wires `remapJar` to publication; no runtime concern |
| Placeholder node rendering | Client/UI layer (`NodeGraph`, `Sidebar`) | Data layer (NodeData stores `addonTypeId`) | Visual placeholder is a rendering decision; the data that triggers it lives in NodeData |

---

## Standard Stack

### Core (Pathmind changes)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Fabric Loader custom entrypoints | 0.17.3 (already used) | `getEntrypointContainers("pathmind", ...)` for addon discovery | The documented Fabric mechanism; used by JEI, REI, Fabric API itself [ASSUMED: based on prior project research — confirmed in `.planning/research/STACK.md`] |
| `maven-publish` Gradle plugin | Built-in (Gradle 9.2.0) | Publish `com.pathmind:pathmind-fabric:VERSION` to mavenLocal | Standard Gradle; Loom auto-wires `remapJar` to the publication artifact [ASSUMED] |
| `com.google.code.gson:gson:2.10.1` | 2.10.1 (already declared) | Serialize/deserialize `NodeData.extraFields` map for addon blob | Already the project's JSON library; no new dependency [VERIFIED: common/build.gradle.kts line 41] |
| JUnit Jupiter 5.11.4 | 5.11.4 (already declared) | Unit tests for `NodeTypeRegistry` and `AddonNodeSerializer` | Already the project's test framework [VERIFIED: common/build.gradle.kts line 43] |

### Supporting (Addon repo only — pathmind-lua)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `modCompileOnly "com.pathmind:pathmind-fabric:VERSION"` | same as Pathmind | API-only compile dependency | In `pathmind-lua/build.gradle.kts`; zero impl classes at compile time |
| Architectury Loom 1.14.473 | 1.14.473 | Addon build toolchain matching Pathmind | Addon must use the same Loom version to produce a compatible jar |
| Fabric Loader 0.17.3 | 0.17.3 | Addon mod loader | Must match Pathmind's loader version range |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Package-level API boundary in `common` | Separate `api` Gradle subproject | Subproject adds build complexity with no benefit at this scale; skip until a second external addon exists |
| `maven-publish` to mavenLocal | File-system `implementation(files(...))` | Files path is brittle and not version-tracked; maven coordinates are the correct approach |
| Addon categories as new enum constants | Parallel `AddonNodeCategory` class/POJO | Enum cannot be extended at runtime; POJO with id/name/color/icon fields is the correct model |

**Installation (Pathmind fabric build.gradle.kts addition):**
```kotlin
plugins {
    // existing plugins
    id("maven-publish")
}
publishing {
    publications {
        create<MavenPublication>("mavenFabric") {
            artifactId = "pathmind-fabric"
            from(components["java"])
        }
    }
}
// Dev loop: ./gradlew :fabric:publishToMavenLocal
```

**Installation (pathmind-lua build.gradle.kts):**
```kotlin
repositories {
    mavenLocal()
}
dependencies {
    modCompileOnly("com.pathmind:pathmind-fabric:${pathmind_version}")
}
```

---

## Package Legitimacy Audit

> This phase introduces no new external library dependencies for Pathmind. All dependencies (Fabric Loader, Fabric API, Architectury, GSON, JUnit) are already present in the codebase. The sibling addon repo (`pathmind-lua`) will add `modCompileOnly` against Pathmind itself — that is a first-party dependency, not an external package.
>
> Phase 2 (Lua VM integration) will introduce `org.squiddev:Cobalt:0.7.3`. That package legitimacy check is deferred to Phase 2 research.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| com.google.code.gson:gson:2.10.1 | Maven Central | 15+ yrs | billions | github.com/google/gson | OK | Already present — no action |
| org.junit.jupiter:junit-jupiter:5.11.4 | Maven Central | 7+ yrs | billions | github.com/junit-team/junit5 | OK | Already present — no action |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious SUS:** none

---

## Architecture Patterns

### System Architecture Diagram

```
Fabric Loader (onInitialize)
         │
         ▼
PathmindMod.onInitialize()                     [fabric/ platform layer]
         │
         ├── AddonLoader.discoverAndLoad()      [new — common/execution or common/addon]
         │       │
         │       └── FabricLoader.getEntrypointContainers("pathmind", PathmindAddonEntrypoint.class)
         │               for each container {
         │                   try {
         │                       container.getEntrypoint().registerNodes(registrar)
         │                   } catch (Throwable t) {
         │                       log error; mark addon as failed; queue warning for overlay
         │                   }
         │               }
         │
         └── NodeTypeRegistry.INSTANCE.install(registrar)   [new — com.pathmind.nodes or api]
                 └── seals registrar; stores definitions/executors/serializers by namespaced ID
                     (e.g. "pathmind_lua:script")

User opens editor
         │
         ▼
Sidebar.initializeCategoryNodes()              [existing — com.pathmind.ui.sidebar]
         ├── existing: for (NodeCategory category : NodeCategory.values()) { ... }
         └── NEW: for (AddonNodeCategory category : AddonCategoryRegistry.getCategories()) { ... }
                       └── reads AddonNodeDefinition list from NodeTypeRegistry

User drags Script node to graph canvas
         │
         ▼
NodeGraph: creates Node with NodeType.ADDON + addonTypeId = "pathmind_lua:script"
                                                              [modified — existing Node constructor]

Graph saved:
NodeGraphPersistence.saveGraph()
         └── for each ADDON node:
                 serializer = NodeTypeRegistry.INSTANCE.serializerFor(addonTypeId)
                 extraFields = serializer.serialize(node)   → { "script": "...", "_schema_version": 1 }
                 NodeData.extraFields = extraFields          [new field on NodeData]

Graph loaded:
NodeGraphPersistence.loadGraph()
         └── for each NodeData where type == ADDON:
                 if NodeTypeRegistry.INSTANCE.has(addonTypeId) → restore normally
                 else → create placeholder node (grayed out, data preserved)

ExecutionManager.advanceTick()               [modified — existing]
         └── if (node.getType() == NodeType.ADDON) {
                 if (pendingAddonFuture == null) {
                     executor = NodeTypeRegistry.INSTANCE.executorFor(node.getAddonTypeId())
                     ctx = AddonNodeContext.from(node, ...)
                     pendingAddonFuture = executor.execute(ctx)   // Phase 1: returns completed future immediately
                 }
                 if (pendingAddonFuture.isDone()) {
                     result = pendingAddonFuture.join()
                     pendingAddonFuture = null
                     advanceToNextNode(result)
                 }
             }
```

### Recommended New File Layout

**Pathmind (`common/src/main/java/com/pathmind/`):**
```
api/
├── addon/
│   ├── PathmindAddonEntrypoint.java   (interface — the contract addon mods implement)
│   ├── NodeTypeRegistrar.java         (mutable collector; sealed after all entrypoints run)
│   ├── AddonNodeDefinition.java       (POJO: id, displayName, category, color, icon, provenance label)
│   ├── AddonNodeExecutor.java         (functional interface: execute(ctx) -> CompletableFuture<NodeResult>)
│   ├── AddonNodeSerializer.java       (interface: serialize/deserialize node state)
│   ├── AddonNodeContext.java          (runtime context: script text, variable accessor stub)
│   ├── AddonNodeCategory.java         (POJO: id, displayName, color, icon — NOT an enum)
│   └── NodeResult.java                (enum/sealed: SUCCESS, FAILURE, SKIPPED)
└── PathmindApiVersion.java             (semver string constant: "0.1.0")

nodes/
├── NodeType.java                       (MODIFIED: gains ADDON sentinel)
├── NodeTypeRegistry.java               (NEW: Map<String, AddonNodeDefinition> + executors + serializers)
└── ... (existing files unchanged)

execution/
├── AddonLoader.java                    (NEW: FabricLoader discovery + registrar lifecycle)
└── ExecutionManager.java              (MODIFIED: ADDON branch in advanceTick)

data/
└── NodeGraphData.NodeData             (MODIFIED: gains String addonTypeId + Map<String,Object> extraFields)

ui/
├── sidebar/
│   └── Sidebar.java                   (MODIFIED: second population pass for addon categories)
└── overlay/
    └── NodeErrorNotificationOverlay   (EXISTING: reuse for addon-failure warnings)
```

**Addon (`pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/`):**
```
LuaAddonEntrypoint.java        (implements PathmindAddonEntrypoint)
LuaNodeExecutor.java           (implements AddonNodeExecutor — Phase 1: returns CompletableFuture.completedFuture(NodeResult.SUCCESS))
LuaNodeSerializer.java         (implements AddonNodeSerializer — persists "script" + "_schema_version": 1)
LuaScriptNodeRenderer.java     (implements AddonNodeBodyRenderer — Phase 1: renders first 3 lines of script text)
```

### Pattern 1: Fabric Entrypoint Discovery

**What:** Host mod calls `FabricLoader.getEntrypointContainers("pathmind", PathmindAddonEntrypoint.class)` in `onInitialize`. Addon mods list their entrypoint class under `"pathmind"` key in `fabric.mod.json`.

**When to use:** Any Fabric mod wanting to offer a plugin point to third-party mods.

**Example:**
```java
// PathmindMod.onInitialize (fabric/)
List<EntrypointContainer<PathmindAddonEntrypoint>> containers =
    FabricLoader.getInstance()
        .getEntrypointContainers("pathmind", PathmindAddonEntrypoint.class);
NodeTypeRegistrar registrar = new NodeTypeRegistrar();
for (EntrypointContainer<PathmindAddonEntrypoint> container : containers) {
    try {
        container.getEntrypoint().registerNodes(registrar);
    } catch (Throwable t) {
        PathmindCommon.LOGGER.error(
            "[Pathmind] Addon '{}' failed node registration — addon disabled",
            container.getProvider().getMetadata().getId(), t);
        AddonLoader.markFailed(container.getProvider().getMetadata().getId(), t);
    }
}
registrar.seal();
NodeTypeRegistry.INSTANCE.install(registrar);

// Addon's fabric.mod.json:
"entrypoints": {
  "pathmind": ["com.mrmysterium.pathmindlua.LuaAddonEntrypoint"]
}
```

[ASSUMED — based on `.planning/research/STACK.md` and `.planning/research/ARCHITECTURE.md` which cite Fabric Wiki and FabricLoader Javadoc]

### Pattern 2: NodeType.ADDON Sentinel + String-Keyed Registry

**What:** The existing `NodeType` enum gains one new constant `ADDON`. When a node's type is `ADDON`, execution and serialization look up behavior by `node.getAddonTypeId()` (a `String` in `"modid:name"` format). The `NodeTypeRegistry` singleton holds all addon definitions, executors, and serializers keyed by these string IDs.

**Why not extend the enum:** Java enums cannot be extended or populated at runtime. All 99 existing node types are enum constants because they are known at compile time. Addon types are known only at mod-load time.

**Example:**
```java
// NodeType.java (existing enum) — add one constant at the end:
ADDON("pathmind.node.type.addon", 0xFF888888, "pathmind.node.type.addon.desc");

// Node.java — gains one new field:
private String addonTypeId; // non-null only when type == NodeType.ADDON

// NodeTypeRegistry.java (new):
public class NodeTypeRegistry {
    public static final NodeTypeRegistry INSTANCE = new NodeTypeRegistry();
    private final Map<String, AddonNodeDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AddonNodeExecutor> executors = new LinkedHashMap<>();
    private final Map<String, AddonNodeSerializer> serializers = new LinkedHashMap<>();
    private boolean installed = false;

    void install(NodeTypeRegistrar registrar) {
        if (installed) throw new IllegalStateException("NodeTypeRegistry already installed");
        definitions.putAll(registrar.getDefinitions());
        executors.putAll(registrar.getExecutors());
        serializers.putAll(registrar.getSerializers());
        installed = true;
    }

    public boolean hasType(String addonTypeId) { return definitions.containsKey(addonTypeId); }
    public AddonNodeDefinition definitionFor(String id) { return definitions.get(id); }
    public AddonNodeExecutor executorFor(String id) { return executors.get(id); }
    public AddonNodeSerializer serializerFor(String id) { return serializers.get(id); }
    public Collection<AddonNodeDefinition> allDefinitions() { return Collections.unmodifiableCollection(definitions.values()); }
}
```

[ASSUMED]

### Pattern 3: Sidebar Addon Category Extension

**What:** `Sidebar.java` currently holds `Map<NodeCategory, List<NodeType>> categoryNodes` and iterates `NodeCategory.values()`. Since `NodeCategory` is an enum and cannot be extended at runtime, addon categories must be stored in parallel maps keyed on a new `AddonNodeCategory` POJO.

**Key insight from reading the code:** `Sidebar.initializeCategoryNodes()` (line 96–107) iterates `NodeCategory.values()` then calls `refreshCustomNodes()`. A second init method `initializeAddonCategoryNodes()` called after `NodeTypeRegistry.INSTANCE` is installed can populate the parallel `addonCategoryNodes` map. The Sidebar render/input methods need a second iteration over these addon entries.

**Scope boundary for Phase 1:** The Sidebar extension only needs to support the list display (node names visible in the panel, draggable to the canvas). The full rendering fidelity (icons, badges, hover states) can start minimal and match what built-in categories show.

**Example (sketch):**
```java
// Sidebar.java — new parallel state:
private final Map<AddonNodeCategory, List<AddonNodeDefinition>> addonCategoryNodes = new LinkedHashMap<>();

// Called after NodeTypeRegistry is installed (e.g., triggered by AddonLoader event):
public void initializeAddonCategoryNodes() {
    addonCategoryNodes.clear();
    for (AddonNodeDefinition def : NodeTypeRegistry.INSTANCE.allDefinitions()) {
        addonCategoryNodes
            .computeIfAbsent(def.getCategory(), k -> new ArrayList<>())
            .add(def);
    }
}
```

[ASSUMED]

### Pattern 4: Opaque Addon Blob in NodeData (Persistence)

**What:** `NodeGraphData.NodeData` gains two new fields: `String addonTypeId` (populated when `type == ADDON`) and `Map<String, Object> extraFields` (the addon-specific JSON data returned by the serializer). GSON serializes `Map<String, Object>` as a nested JSON object, so the addon blob becomes a first-class field in the preset JSON.

**`_schema_version` rule:** The serializer MUST write a `_schema_version` integer key in `extraFields`. In Phase 1 this is always `1`. This field is what enables schema migrations in future phases without data loss.

**Placeholder behavior when addon is absent:** If `addonTypeId` is present but `NodeTypeRegistry.INSTANCE.hasType(addonTypeId)` returns false during load, create a placeholder `Node` with type `ADDON`, retain the `extraFields` map as-is, and mark the node as `unresolved`. The placeholder renders grayed-out. When the graph is re-saved, the placeholder's `extraFields` are written back verbatim — the data round-trips losslessly.

**Example:**
```java
// NodeGraphData.NodeData — new fields (add to existing class):
private String addonTypeId;           // "pathmind_lua:script" — only set when type == ADDON
private Map<String, Object> extraFields;  // addon blob; GSON handles nested JSON automatically

// LuaNodeSerializer.java (addon):
@Override
public Map<String, Object> serialize(AddonNodeContext ctx) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("_schema_version", 1);
    fields.put("script", ctx.getScriptText());
    return fields;
}

@Override
public void deserialize(AddonNodeContext ctx, Map<String, Object> fields) {
    Object script = fields.get("script");
    ctx.setScriptText(script instanceof String s ? s : "");
}
```

[ASSUMED]

### Pattern 5: Deferred-Registration Guard (Init Ordering)

**What:** Fabric Loader does not guarantee entrypoint call order across mods. An addon's `onInitialize` may run before Pathmind's `onInitialize`. To prevent a `NullPointerException` when the addon tries to register before Pathmind's registry is live, `AddonLoader` uses a queued-registration guard: if called before Pathmind has finished its own init, registrations are queued and flushed at the end of `PathmindMod.onInitialize`.

**Phase 1 implementation:** The simplest version is to call `AddonLoader.discoverAndLoad()` at the very end of `PathmindMod.onInitialize`, after all Pathmind internal state is set up. This sidesteps the ordering problem because Pathmind never calls its own registry before init completes, and addon entrypoints are only invoked from within `onInitialize`.

**Why this works:** `FabricLoader.getEntrypointContainers` does not call the addon's constructor — it instantiates and calls it on demand when `getEntrypoint()` is called. Pathmind calls this from within its own `onInitialize`, so by the time the addon's `registerNodes` runs, Pathmind's internal state is already initialized.

[ASSUMED]

### Anti-Patterns to Avoid

- **Addon compiled against impl classes:** Never publish the full `common` or `fabric` jar from `pathmind-lua`'s `modImplementation`. Use `modCompileOnly` against the published artifact. If `import com.pathmind.ui.graph.NodeGraph` appears in the addon, it is wrong.
- **NodeType enum extension:** Do not attempt to add addon node types as enum constants. The enum is compiled into Pathmind; addons cannot modify it at runtime.
- **Registering categories as NodeCategory enum values:** NodeCategory is also an enum. Addon categories are `AddonNodeCategory` POJOs, not enum constants.
- **Exposing `Node`, `ExecutionManager`, or `NodeGraph` in the API surface:** These monolith classes (~10,000+ lines each) must not appear in any `com.pathmind.api` method signature. Wrap what the addon legitimately needs behind narrow API-surface methods on `AddonNodeContext`.
- **Calling `NodeTypeRegistry` from the addon's `onInitialize`:** The addon does not call the registry directly. It implements `PathmindAddonEntrypoint.registerNodes(NodeTypeRegistrar)` and registers through the registrar object passed by Pathmind.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Addon discovery | Custom classpath scanner / `ServiceLoader` | `FabricLoader.getEntrypointContainers` | Classpath scanning fails across Fabric's per-mod classloaders; ServiceLoader is not loader-aware |
| Mod version metadata in error messages | String parsing of mod files | `container.getProvider().getMetadata().getId()` | FabricLoader already exposes this; string parsing is brittle |
| Addon-failure warning display | Custom overlay class | `NodeErrorNotificationOverlay` (existing) | The overlay is already wired into `PathmindHud`; reuse it |
| JSON serialization of `Map<String, Object>` | Custom serializer | GSON (already used) | GSON handles `Map<String, Object>` transparently; it serializes nested maps as JSON objects |
| Dev-loop cache busting | Git hooks or IDE task | Shell command: `./gradlew :fabric:publishToMavenLocal && del /s /q pathmind-lua\.gradle\loom-cache\remapped_mods` | Loom caches by version coordinate; content-hash check does not exist in Loom 1.14 |

**Key insight:** The most tempting hand-rolled solution is the addon discovery mechanism. `ServiceLoader` looks like a clean fit but fails across Fabric classloader boundaries. `FabricLoader.getEntrypointContainers` is the one correct tool.

---

## Runtime State Inventory

> SKIPPED — Phase 1 is greenfield code addition (new files, minor modifications to existing files). No rename, rebrand, refactor, or migration is involved. No existing runtime state carries names that need changing.

---

## Common Pitfalls

### Pitfall 1: mavenLocal Stale Remapped Jar

**What goes wrong:** Rebuild Pathmind API, publish to mavenLocal without bumping the version. The addon's Loom build uses the cached remapped jar from `.gradle/loom-cache/remapped_mods`. The addon silently uses the old API, causing runtime `NoSuchMethodError` when a new method was added.

**Why it happens:** Loom 1.14 caches by version coordinate, not content hash. Documented in Fabric Loom issue #1290.

**How to avoid:** Every Pathmind API rebuild for the addon dev loop must be accompanied by deleting `pathmind-lua/.gradle/loom-cache/remapped_mods`. Document this as a Makefile target or a PowerShell alias in `pathmind-lua/docs/dev-loop.md`.

**Warning signs:** Added a method to `AddonNodeContext`, it builds on the Pathmind side, but the addon gets `NoSuchMethodError` at runtime. The addon's remapped jar timestamp predates the Pathmind rebuild.

### Pitfall 2: Impl Classes in the Published API Artifact

**What goes wrong:** The `maven-publish` block publishes `components["java"]`, which includes all classes from the `common` shadow jar — including all of `com.pathmind.execution`, `com.pathmind.ui.graph`, and `com.pathmind.nodes`. The addon can `import com.pathmind.execution.ExecutionManager` and it compiles cleanly. The API boundary is broken from the first commit.

**Why it happens:** Loom's `remapJar` output (which Fabric Loom auto-configures as the publication artifact) includes the full common + fabric jars. Publishing `components["java"]` on the fabric project publishes everything.

**How to avoid:** Two approaches:
1. **Publish a filtered api-classifier jar:** Configure a separate `sourceSets.apiSurface` that compiles only `src/main/java/com/pathmind/api/**` and publish that artifact with classifier `api`. The addon uses `modCompileOnly("com.pathmind:pathmind-fabric:VERSION:api")`.
2. **Package discipline + compile check:** Publish the full jar but document that the addon must only import from `com.pathmind.api.*`. Enforce via a CI compilation job that runs with only the `api` package on the classpath. This is the lower-effort Phase 1 approach given there is currently one addon.

**Recommended for Phase 1:** Option 2 (package discipline + documented convention). Option 1 can be added in Phase 2 when the API shape stabilizes.

**Warning signs:** `import com.pathmind.execution.ExecutionManager` compiles successfully in the addon without error.

### Pitfall 3: NodeCategory Enum in Sidebar Cannot Accept Addon Categories

**What goes wrong:** Pathmind 1 tries to add an addon-provided category to `Sidebar.categoryNodes` (a `Map<NodeCategory, ...>`). Since `NodeCategory` is a sealed enum, this fails at compile time. If the developer tries to cast an `AddonNodeCategory` POJO to `NodeCategory`, it fails at runtime with `ClassCastException`.

**Why it happens:** The Sidebar was designed only for built-in categories. The `Map<NodeCategory, ...>` type parameter forces a compile-time contract.

**How to avoid:** The Sidebar must hold a parallel `Map<AddonNodeCategory, List<AddonNodeDefinition>> addonCategoryNodes` (or a shared `Map<Object, ...>` using a common marker interface). The simplest clean approach: introduce a `SidebarCategory` interface with `getDisplayName()`, `getColor()`, `getIcon()` methods; have both `NodeCategory` (adapter wrapper or direct implementation) and `AddonNodeCategory` implement it. Sidebar becomes `Map<SidebarCategory, ...>`. This does not require refactoring all 99 NodeType usages — only the sidebar map type changes.

**Warning signs:** Compilation error `incompatible types: AddonNodeCategory cannot be converted to NodeCategory` in Sidebar.java.

### Pitfall 4: Serialization GSON Type Erasure for `Map<String, Object>`

**What goes wrong:** When loading a preset, GSON deserializes `extraFields` as `Map<String, Object>`. For JSON numbers, GSON produces `Double` (not `Integer`). Code that does `(Integer) fields.get("_schema_version")` throws `ClassCastException`.

**Why it happens:** GSON's type erasure: JSON number `1` becomes `Double(1.0)` when deserialized into `Object` type.

**How to avoid:** When reading `_schema_version` from `extraFields`, use `((Number) fields.get("_schema_version")).intValue()` rather than direct cast to `Integer`. Document this in the `AddonNodeSerializer` API javadoc.

**Warning signs:** `ClassCastException: class java.lang.Double cannot be cast to class java.lang.Integer` in `LuaNodeSerializer.deserialize`.

### Pitfall 5: `addonTypeId` Not Written to JSON When Saving ADDON Nodes

**What goes wrong:** `NodeType.ADDON` is a new enum value. GSON serializes `NodeType` as a string. But `addonTypeId` is a new field on `NodeData` that may not be included in GSON's output if it is null when the node type is ADDON (because GSON skips null fields by default with `GsonBuilder.serializeNulls(false)`).

**Why it happens:** The existing `NodeGraphPersistence` may use `GsonBuilder` with default null-skipping. A freshly created ADDON node with a null `addonTypeId` would serialize to `{ "type": "ADDON" }` with no `addonTypeId` field. On reload, `addonTypeId` comes back null, and the registry lookup fails silently.

**How to avoid:** The `addonTypeId` field on `NodeData` must be set before serialization. `NodeGraph` and any code that creates ADDON nodes must always set `addonTypeId` immediately. Add a validation check in `NodeGraphPersistence.saveGraph()`: if `type == ADDON && addonTypeId == null`, log a warning and skip the node rather than write a broken record.

**Warning signs:** After save/load cycle, Script node appears in the graph but behaves as an unresolved placeholder even though the addon is installed.

---

## Code Examples

### Entrypoint discovery in PathmindMod.onInitialize

```java
// Source: based on Fabric Loader API pattern (fabric.mod.json + FabricLoader.getEntrypointContainers)
// File: fabric/src/main/java/com/pathmind/PathmindMod.java

@Override
public void onInitialize() {
    LOGGER.info("Initializing Pathmind mod");

    String minecraftVersion = FabricLoader.getInstance()
        .getModContainer("minecraft")
        .map(c -> c.getMetadata().getVersion().getFriendlyString())
        .orElse("unknown");
    if (!VersionSupport.isSupported(minecraftVersion)) {
        LOGGER.warn("Pathmind targets {} but detected {}", VersionSupport.SUPPORTED_RANGE, minecraftVersion);
    }

    // NEW: discover and load addons (must be last — all Pathmind internal state is ready here)
    AddonLoader.discoverAndLoad();

    LOGGER.info("Pathmind mod initialized successfully");
}
```

[ASSUMED]

### NodeTypeRegistrar sealed collector

```java
// Source: JEI/REI registrar-passed-to-plugin pattern — see .planning/research/ARCHITECTURE.md
// File: common/src/main/java/com/pathmind/api/addon/NodeTypeRegistrar.java

public final class NodeTypeRegistrar {
    private final Map<String, AddonNodeDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AddonNodeExecutor> executors = new LinkedHashMap<>();
    private final Map<String, AddonNodeSerializer> serializers = new LinkedHashMap<>();
    private boolean sealed = false;

    /** Called by addon entrypoint implementations. */
    public void register(AddonNodeDefinition def, AddonNodeExecutor exec, AddonNodeSerializer ser) {
        if (sealed) throw new IllegalStateException(
            "NodeTypeRegistrar is sealed — registration phase has ended");
        if (def == null || exec == null || ser == null) throw new NullPointerException(
            "All three registration arguments must be non-null");
        if (definitions.containsKey(def.id())) throw new IllegalArgumentException(
            "Duplicate addon node type ID: " + def.id());
        definitions.put(def.id(), def);
        executors.put(def.id(), exec);
        serializers.put(def.id(), ser);
    }

    // Package-private: only AddonLoader calls this.
    void seal() { this.sealed = true; }

    Map<String, AddonNodeDefinition> getDefinitions() { return Collections.unmodifiableMap(definitions); }
    Map<String, AddonNodeExecutor> getExecutors() { return Collections.unmodifiableMap(executors); }
    Map<String, AddonNodeSerializer> getSerializers() { return Collections.unmodifiableMap(serializers); }
}
```

[ASSUMED]

### Addon entrypoint (sibling repo)

```java
// Source: Fabric entrypoint pattern — fabric.mod.json "pathmind" key
// File: pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaAddonEntrypoint.java

public class LuaAddonEntrypoint implements PathmindAddonEntrypoint {
    @Override
    public void registerNodes(NodeTypeRegistrar registrar) {
        registrar.register(
            AddonNodeDefinition.builder("pathmind_lua:script")
                .displayName("Lua Script")
                .category(new AddonNodeCategory("pathmind_lua.scripting", "Scripting", 0xFF7986CB, "✦"))
                .color(0xFF7986CB)
                .provenanceLabel("Pathmind Lua")
                .bodyRenderer(new LuaScriptNodeRenderer())
                .build(),
            new LuaNodeExecutor(),     // Phase 1: returns CompletableFuture.completedFuture(NodeResult.SUCCESS)
            new LuaNodeSerializer()    // Phase 1: persists script text + _schema_version
        );
    }
}
```

[ASSUMED]

### Phase 1 graceful no-op executor

```java
// File: pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeExecutor.java

public class LuaNodeExecutor implements AddonNodeExecutor {
    @Override
    public CompletableFuture<NodeResult> execute(AddonNodeContext ctx) {
        // Phase 1: graceful pass-through. Phase 2 will replace with Cobalt VM execution.
        return CompletableFuture.completedFuture(NodeResult.SUCCESS);
    }
}
```

[ASSUMED]

### ExecutionManager ADDON branch (core modification)

```java
// Modified section of ExecutionManager.advanceTick() — conceptual
// File: common/src/main/java/com/pathmind/execution/ExecutionManager.java

// Existing tick loop; new branch for ADDON nodes:
if (currentNode.getType() == NodeType.ADDON) {
    String addonTypeId = currentNode.getAddonTypeId();
    if (addonTypeId == null || !NodeTypeRegistry.INSTANCE.hasType(addonTypeId)) {
        // Unresolved addon node — skip, log warning, advance graph
        LOGGER.warn("[Pathmind] Skipping unresolved addon node: {}", addonTypeId);
        advanceToNextNode(NodeResult.SKIPPED);
        return;
    }
    if (pendingAddonFuture == null) {
        AddonNodeExecutor executor = NodeTypeRegistry.INSTANCE.executorFor(addonTypeId);
        AddonNodeContext ctx = buildAddonNodeContext(currentNode);
        pendingAddonFuture = executor.execute(ctx);
    }
    if (pendingAddonFuture.isDone()) {
        NodeResult result = pendingAddonFuture.join();
        pendingAddonFuture = null;
        advanceToNextNode(result);
    }
    // else: continue this tick without advancing — game runs normally
    return;
}
// ... existing built-in node dispatch follows ...
```

[ASSUMED]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ServiceLoader` for plugin discovery in Java mods | Fabric `getEntrypointContainers` | Fabric Loader 0.12+ (~2021) | Solves classloader isolation; classpath scanning is dead in modded MC |
| `EnumMap<NodeType, ...>` for all node behavior | Enum for built-in types + String-keyed map for addon types | Pattern established by JEI/REI circa 2019 | Required because Java enums cannot be extended at runtime |
| SNAPSHOT Maven coordinates during dev | Version-bumped dev build (`1.0-dev.N`) + cache deletion | Loom issue #1290 documented 2022 | SNAPSHOT + Loom is a known bad combination; incremental version is safer |

**Deprecated/outdated:**
- `java.util.ServiceLoader` for Minecraft mod plugin points: fails across Fabric's per-mod classloaders. Use `FabricLoader.getEntrypointContainers`.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Fabric entrypoint discovery pattern works as described and `getEntrypointContainers` returns an empty list (not null/exception) when no addon is installed | Code Examples / Standard Stack | Graceful no-addon path fails; Pathmind init crashes instead of proceeding normally |
| A2 | `maven-publish` + Loom 1.14 auto-wires `remapJar` to the publication artifact without manual `artifact(remapJar)` configuration | Standard Stack — Installing | Publication produces a dev-mapped (non-remapped) jar; addon cannot compile against it |
| A3 | `NodeData.extraFields` field added as `Map<String, Object>` will serialize/deserialize correctly with GSON without additional type adapters | Pattern 4 / Pitfall 4 | Schema version reads fail with ClassCastException; addon node data silently lost on load |
| A4 | `Sidebar.java` can be extended to accept addon categories via a parallel `Map<AddonNodeCategory, ...>` without a full rewrite | Pattern 3 | Full Sidebar refactor needed to support addon categories; larger task than planned |
| A5 | `NodeGraph.java` can create `Node` instances with `NodeType.ADDON` + `addonTypeId` via the existing Node constructor pattern | Architecture Patterns | Node creation for addon nodes requires a new factory method; ripple changes to NodeGraph |
| A6 | `NodeErrorNotificationOverlay` has a public API to queue error messages from outside the UI layer | Don't Hand-Roll | New overlay class needed for addon failures; extra work not in scope for Phase 1 |
| A7 | The `AddonNodeBodyRenderer` functional interface hook can be called from within `NodeGraph`'s existing node-body rendering path without touching NodeGraph's core rendering loop | Pattern — API-07 | Deeper NodeGraph integration needed; risk of destabilizing the 3000+ line rendering class |

**Items A4–A7 should be verified by reading the actual file code before finalizing the plan.** These are the only unverified claims that directly affect the planner's task scope estimates.

---

## Open Questions

1. **`NodeErrorNotificationOverlay` public API surface**
   - What we know: The class exists at `common/src/main/java/com/pathmind/ui/overlay/NodeErrorNotificationOverlay.java` and is used for existing validation errors.
   - What's unclear: Whether it has a public static method like `NodeErrorNotificationOverlay.queueAddonError(String modId, Throwable t)` or requires the error to be queued through some other mechanism.
   - Recommendation: Read the class file before writing the AddonLoader task. If no public enqueue method exists, add one in the same task that creates AddonLoader.

2. **`NodeGraph.java` addon node body rendering hook**
   - What we know: NodeGraph renders node bodies in immediate mode (~3000 lines). The D-06 decision requires a read-only script preview in Phase 1.
   - What's unclear: Exactly where in the per-node rendering loop the addon body renderer callback should be injected. The rendering is immediate-mode; there may be a `renderNodeBody(Node, DrawContext, ...)` method or it may be inline in a large switch/if-chain.
   - Recommendation: Read the node body rendering section of NodeGraph.java before writing the NodeGraph modification task.

3. **Drag-from-sidebar for addon nodes**
   - What we know: The Sidebar renders `NodeType` items that NodeGraph converts to `Node` instances on drag. Addon nodes use `NodeType.ADDON` + a string ID.
   - What's unclear: Whether the drag-and-drop code path in NodeGraph.java can accept a non-`NodeType` identifier (i.e., `NodeType.ADDON + addonTypeId`) through the existing drag mechanism, or whether a new drag payload type is needed.
   - Recommendation: Read NodeGraph's drag-start handling before writing the drag-from-sidebar task.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Build + runtime | Yes | OpenJDK 21.0.11 (Temurin-21.0.11+10) | — |
| Gradle (via wrapper) | Build | Yes | 9.2.0 | — |
| `./gradlew :common:test` | Test baseline verification | Yes | Tests pass (BUILD SUCCESSFUL) | — |
| Sibling repo `pathmind-lua` directory | Addon code tasks | Not yet created | — | Must be created as part of Phase 1 |
| `mavenLocal()` repository | Addon compilation | Yes (Gradle built-in) | — | — |
| `maven-publish` plugin | API artifact publishing | Yes (Gradle built-in) | — | — |

**Missing dependencies with no fallback:**
- Sibling repo `C:\Users\Trynda\Desktop\Dev\sidequests\pathmind-lua` does not yet exist. The plan must include a task that scaffolds the sibling repo (Gradle project, `fabric.mod.json`, `src/` layout, `.gitignore`) before any addon code tasks run.

**Missing dependencies with fallback:**
- None beyond the sibling repo.

---

## Security Domain

> `security_enforcement: true` in `.planning/config.json`. ASVS Level 1 applies.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No auth surface in Phase 1 (addon registration is local, no credentials) |
| V3 Session Management | No | Client-side mod, no sessions |
| V4 Access Control | Partial | Addon registration should not be callable after init; `NodeTypeRegistrar.seal()` enforces this |
| V5 Input Validation | Yes | Addon-supplied `AddonNodeDefinition.id()` must be validated (non-null, non-empty, namespaced format `"modid:name"`, no path traversal chars) before insertion into the registry |
| V6 Cryptography | No | No cryptographic operations in Phase 1 |

### Known Threat Patterns for Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malicious addon registering a duplicate node ID to shadow a built-in behavior | Tampering | `NodeTypeRegistrar.register()` throws `IllegalArgumentException` on duplicate; whole addon is disabled via D-08 failure UX |
| Addon supplying `null` executor (execution skipped silently) | Tampering | Null check in `NodeTypeRegistrar.register()`; NullPointerException caught and converted to `IllegalArgumentException` with informative message |
| Addon injecting path traversal chars in node ID (e.g., `"../../../evil:id"`) | Tampering | Validate node ID against regex `^[a-z0-9_-]+:[a-z0-9_/.-]+$` in `NodeTypeRegistrar.register()` |
| Stale mavenLocal jar exposing a version mismatch at runtime | Information Disclosure | Dev loop doc + cache-deletion command prevents silently running wrong code |
| `extraFields` in NodeData containing arbitrary data written by a malicious addon | Tampering | Pathmind only reads `extraFields` back through the addon's own `AddonNodeSerializer.deserialize`; Pathmind itself does not interpret the blob |

---

## Sources

### Primary (MEDIUM confidence from training/prior research)
- `.planning/research/STACK.md` — Fabric entrypoint discovery, maven-publish + Loom, Cobalt VM selection; all sourced from official Fabric Wiki, FabricLoader Javadoc, and Cobalt Maven
- `.planning/research/ARCHITECTURE.md` — Component design, registrar pattern, async executor model; sourced from REI/JEI/AE2 analysis and CC:Tweaked internals
- `.planning/research/PITFALLS.md` — mavenLocal staleness (Loom #1290), impl coupling, init ordering (Fabric Loader #459); cross-referenced against issue trackers

### Codebase — Verified Directly (HIGH confidence)
- `common/src/main/java/com/pathmind/nodes/NodeType.java` — 99 enum constants, no ADDON sentinel yet
- `common/src/main/java/com/pathmind/nodes/NodeBehaviorDefinitionRegistry.java` — `EnumMap<NodeType, NodeBehaviorDefinition>`; confirms enum-keyed design
- `common/src/main/java/com/pathmind/nodes/NodeCategory.java` — enum with 9 categories; confirms sidebar cannot accept runtime categories without parallel map approach
- `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java` — `Map<NodeCategory, List<NodeType>>` confirmed; `initializeCategoryNodes()` iterates `NodeCategory.values()`; parallel map extension is feasible
- `common/src/main/java/com/pathmind/data/NodeGraphData.java` — `NodeData` class; no `addonTypeId` or `extraFields` fields yet
- `fabric/src/main/java/com/pathmind/PathmindMod.java` — `onInitialize()` body confirmed; `AddonLoader` call will be appended at the end
- `fabric/src/main/resources/fabric.mod.json` — existing `entrypoints` block; `"pathmind"` key does not yet exist
- `fabric/build.gradle.kts` — no `maven-publish` plugin yet; `shadowJar` + `remapJar` pipeline confirmed
- `build.gradle.kts` — `maven_group=com.pathmind` confirmed; Gradle 9.2.0 (wrapper)
- `./gradlew :common:test` — BUILD SUCCESSFUL; 24 existing test files; test baseline is green

### Tertiary (LOW confidence — training knowledge)
- GSON null field serialization behavior with `Map<String, Object>` — [ASSUMED]; risk documented in Pitfall 4

---

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM — key patterns confirmed via prior research citing official sources; dev-loop mechanics are ASSUMED until first build
- Architecture: MEDIUM — component design confirmed against actual codebase; API shape is ASSUMED (no existing API to verify against)
- Pitfalls: HIGH — all pitfalls sourced from documented issues or confirmed code patterns in existing codebase

**Research date:** 2026-06-12
**Valid until:** 2026-07-12 (API shape may drift; re-verify if planning is delayed)
