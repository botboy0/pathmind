# Architecture Research

**Domain:** Minecraft mod addon API + embedded Lua scripting node
**Researched:** 2026-06-12
**Confidence:** HIGH (core patterns confirmed against REI source structure, Fabric Loader API docs, JEI wiki, AE2 addon guide, CC:Tweaked internals)

---

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                  ADDON MOD JAR (e.g. pathmind-lua-addon)                │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  PathmindAddonEntrypoint impl  (fabric.mod.json "pathmind" key) │    │
│  │  LuaNodeRegistrar  ──────────────────────────────────────┐      │    │
│  │  LuaScriptNodeRenderer (UI extension)                    │      │    │
│  └──────────────────────────────────────────────────────────┼──────┘    │
│  compileOnly ────────────────────────────────────────────   │           │
└──────────────────────────────────────────────────────────── │ ──────────┘
                                                              │
                  ┌───────────────────────────────────────────▼──────┐
                  │              pathmind-api JAR (stable surface)    │
                  │  PathmindAddonEntrypoint (interface)              │
                  │  NodeTypeRegistrar  (passed to entrypoint)        │
                  │  AddonNodeDefinition (what an addon node looks like│
                  │  AddonNodeExecutor  (execute contract)            │
                  │  AddonNodeSerializer (JSON contract)              │
                  │  PathmindActionContext  (MC actions for scripts)  │
                  │  PathmindVariableContext (read/write variables)   │
                  └──────────────────────────────────────────────────┘
                                    │ implementation
┌───────────────────────────────────▼──────────────────────────────────────┐
│                     pathmind (common / fabric / neoforge)                │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │  Platform Layer  (PathmindMod.onInitialize)                      │    │
│  │    └── AddonLoader: FabricLoader.getEntrypointContainers(        │    │
│  │             "pathmind", PathmindAddonEntrypoint.class)           │    │
│  │         for each: entrypoint.registerNodes(nodeTypeRegistrar)    │    │
│  ├──────────────────────────────────────────────────────────────────┤    │
│  │  NodeTypeRegistry  (extended from NodeBehaviorDefinitionRegistry)│    │
│  │    Map<String, AddonNodeDefinition>  keyed by namespaced ID      │    │
│  │    Map<String, AddonNodeExecutor>    keyed by namespaced ID      │    │
│  │    Map<String, AddonNodeSerializer>  keyed by namespaced ID      │    │
│  ├──────────────────────────────────────────────────────────────────┤    │
│  │  ExecutionManager (tick-driven state machine)                    │    │
│  │    handles ADDON_SCRIPT node type: delegates to AddonNodeExecutor│    │
│  │    for async nodes: holds CompletableFuture<NodeResult>          │    │
│  │    per tick: check future.isDone(); advance graph on completion  │    │
│  ├──────────────────────────────────────────────────────────────────┤    │
│  │  NodeGraph (UI) — renders addon-registered UI extensions         │    │
│  └──────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `PathmindAddonEntrypoint` | Interface addon mods implement; called once at mod init with a `NodeTypeRegistrar` | Interface in pathmind-api JAR; discovered via `FabricLoader.getEntrypointContainers` |
| `NodeTypeRegistrar` | Mutable registry object passed to each addon entrypoint; collects `AddonNodeDefinition` + executor + serializer triples | Passed by Pathmind's `AddonLoader`; closed after all entrypoints have run |
| `AddonNodeDefinition` | Describes the node: display name, category, parameter descriptors, color | POJO / builder in pathmind-api |
| `AddonNodeExecutor` | Executes the node given a runtime context; may return a `CompletableFuture<NodeResult>` for async nodes | Functional interface in pathmind-api; Lua addon implements by spinning up Cobalt VM |
| `AddonNodeSerializer` | Reads/writes addon-specific node state (e.g. script text) to `NodeData` extra-fields map | Interface in pathmind-api; Lua addon persists script source |
| `AddonNodeContext` | Runtime context given to executor: MC client handle, variable read/write, action dispatch | Constructed by `ExecutionManager`; wraps existing `RuntimeParameterData` |
| `AddonLoader` | Invokes `FabricLoader.getEntrypointContainers("pathmind", ...)` at mod init; feeds registrar to each plugin | Internal to Pathmind; runs during `onInitialize` |
| `ExecutionManager` (modified) | After routing to an addon node, calls `executor.execute(context)`; holds the returned future; polls completion each tick | Modified from existing singleton; async node stays "active" until future resolves |
| `NodeTypeRegistry` (new) | Holds all registered addon types post-entrypoint phase; queried by editor UI and serializer | Replaces / augments existing `NodeBehaviorDefinitionRegistry` for the addon surface |

---

## Recommended Project Structure

The Pathmind source does **not** need to be split into separate Gradle subprojects (no `pathmind-api` Gradle module needed). Instead, the API surface is a package boundary inside the existing `common` module, published as a separate artifact classifier:

```
pathmind/
├── common/
│   └── src/main/java/com/pathmind/
│       ├── api/                          ← NEW: public API surface (becomes api jar)
│       │   ├── addon/
│       │   │   ├── PathmindAddonEntrypoint.java   (interface)
│       │   │   ├── NodeTypeRegistrar.java          (passed to plugins)
│       │   │   ├── AddonNodeDefinition.java        (node descriptor)
│       │   │   ├── AddonNodeExecutor.java          (functional interface)
│       │   │   ├── AddonNodeSerializer.java        (persistence contract)
│       │   │   └── AddonNodeContext.java           (execution context)
│       │   └── script/
│       │       ├── PathmindActionContext.java      (MC actions usable from scripts)
│       │       └── PathmindVariableContext.java    (variable read/write)
│       ├── nodes/                        ← EXISTING (internal, non-API)
│       │   ├── Node.java
│       │   ├── NodeType.java             (gains ADDON_SCRIPT sentinel or open registry)
│       │   └── NodeBehaviorDefinitionRegistry.java
│       └── execution/                    ← EXISTING (internal)
│           └── ExecutionManager.java     (modified to handle async addon nodes)
├── fabric/
│   └── src/main/resources/fabric.mod.json   (gains "pathmind" entrypoint key declaration)
└── pathmind-lua-addon/  (sibling repo)
    └── src/main/java/com/pathmind/lua/
        ├── LuaAddonEntrypoint.java       (implements PathmindAddonEntrypoint)
        ├── LuaNodeExecutor.java          (AddonNodeExecutor impl; runs Cobalt VM)
        ├── LuaNodeSerializer.java        (AddonNodeSerializer impl; persists script text)
        └── ui/
            └── LuaScriptEditorWidget.java  (text editor UI component)
```

### Structure Rationale

- **`com.pathmind.api`** is a package-level boundary, not a Gradle-level boundary. This is the REI and AE2 approach: one multi-module build, one published jar per platform, but a documented `api/` package that addons are expected to compile against and internals are expected never to expose. A second `pathmind-api` Gradle subproject is possible but adds build complexity with no real benefit at this scale — skip it until a third-party developer demands a separate artifact.
- **`NodeTypeRegistrar` object passed to plugins** follows the REI/JEI pattern exactly: the host constructs a mutable registrar, passes it to every plugin during init, then seals it. Addons never hold a reference to Pathmind's internal registry directly.
- **Sibling repo `pathmind-lua-addon`** depends on `pathmind` using `compileOnly` (or `modCompileOnly` in Loom) so the API classes are on the compile classpath but not shipped in the addon JAR.

---

## Architectural Patterns

### Pattern 1: Fabric Entrypoint Discovery

**What:** The host mod declares a custom entrypoint key in its own `fabric.mod.json` (as documentation/convention — Fabric Loader does not require it). Addon mods list an implementing class under that key. The host calls `FabricLoader.getInstance().getEntrypointContainers("pathmind", PathmindAddonEntrypoint.class)` during `onInitialize`, iterates the results, and calls `entrypoint.register(registrar)` on each.

**When to use:** Any time you want third-party mods to hook into your mod without a hard dependency. The pattern is used by REI (`rei_client`, `rei_server`, `rei_common` keys), AE2 (`ae2:addon` custom entrypoint via `IAEAddonEntrypoint`), and many others.

**Trade-offs:** Initialization ordering is "declaration order within a single mod.json only" — no cross-mod ordering guarantee. Addons that depend on each other's registrations must handle ordering themselves (rare for a node registry). Works identically on Fabric and NeoForge (NeoForge has its own registration event, but Architectury provides the `@ExpectPlatform` escape hatch to unify).

**Example:**

```java
// In pathmind-api: the contract
public interface PathmindAddonEntrypoint {
    void registerNodes(NodeTypeRegistrar registrar);
}

// In Pathmind's platform layer (PathmindMod.onInitialize):
List<EntrypointContainer<PathmindAddonEntrypoint>> containers =
    FabricLoader.getInstance()
        .getEntrypointContainers("pathmind", PathmindAddonEntrypoint.class);
NodeTypeRegistrar registrar = new NodeTypeRegistrar();
for (EntrypointContainer<PathmindAddonEntrypoint> container : containers) {
    try {
        container.getEntrypoint().registerNodes(registrar);
    } catch (Throwable t) {
        LOGGER.error("Addon {} failed to register nodes", container.getProvider().getMetadata().getId(), t);
    }
}
registrar.seal(); // freeze registry; further registrations throw
NodeTypeRegistry.INSTANCE.install(registrar);

// In the addon's fabric.mod.json:
"entrypoints": {
  "pathmind": ["com.pathmind.lua.LuaAddonEntrypoint"]
}

// In the addon:
public class LuaAddonEntrypoint implements PathmindAddonEntrypoint {
    @Override
    public void registerNodes(NodeTypeRegistrar registrar) {
        registrar.register(
            AddonNodeDefinition.builder("pathmind_lua:script")
                .displayName("Lua Script")
                .category("pathmind_lua.category.scripting")
                .color(0xFF7986CB)
                .build(),
            new LuaNodeExecutor(),
            new LuaNodeSerializer()
        );
    }
}
```

---

### Pattern 2: Registry Object (Registrar-Passed-to-Plugin)

**What:** The host creates a fresh `NodeTypeRegistrar` instance per init cycle, passes it to each plugin, then seals it. Plugins never interact with the internal registry directly. This is the JEI pattern (`IRecipeCategoryRegistration`, `IRecipeRegistration` etc. — a distinct registrar object per phase) and the REI pattern (a `CategoryRegistry`, `DisplayRegistry` passed per phase).

**When to use:** Whenever the host needs to maintain control over what the registry contains, detect duplicate registrations, and prevent post-init modifications.

**Trade-offs:** Slightly more verbose than a static `NodeTypeRegistry.register(...)` call, but prevents addons from calling internal methods they should not access.

**Example:**

```java
// pathmind-api
public final class NodeTypeRegistrar {
    private final Map<String, AddonNodeDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AddonNodeExecutor> executors = new LinkedHashMap<>();
    private final Map<String, AddonNodeSerializer> serializers = new LinkedHashMap<>();
    private boolean sealed = false;

    public void register(AddonNodeDefinition def, AddonNodeExecutor exec, AddonNodeSerializer ser) {
        if (sealed) throw new IllegalStateException("NodeTypeRegistrar is sealed; registration phase is over");
        if (definitions.containsKey(def.id())) throw new IllegalArgumentException("Duplicate node type: " + def.id());
        definitions.put(def.id(), def);
        executors.put(def.id(), exec);
        serializers.put(def.id(), ser);
    }

    // package-private: only NodeTypeRegistry.install() calls this
    void seal() { this.sealed = true; }
}
```

---

### Pattern 3: Async Script Node with Tick-Driven Completion Polling

**What:** When `ExecutionManager` reaches an addon node whose executor returns a `CompletableFuture<NodeResult>` (rather than an immediate result), the manager parks the future in a field and returns control to the game tick. Each subsequent tick, it calls `future.isDone()`. When true, it reads the result and advances the graph. The script executor runs on a dedicated worker thread (a `ThreadPoolExecutor` with a small fixed pool). Calls to Minecraft actions from that thread go through `MinecraftClient.getInstance().execute(Runnable)`, which queues work on the main thread; the script thread then blocks on a `CompletableFuture` that the queued action completes when finished.

**When to use:** Any long-running node (Lua script, HTTP call, complex search). Pathmind's existing `BackgroundStartRunner` / `ForkJoinPool` already establishes this pattern; this formalizes it for addon nodes.

**Trade-offs:**
- Polling per tick (50 polls/sec) is negligible overhead. An alternative is a Fabric `ClientTickEvents.END_CLIENT_TICK` callback that checks a set of pending futures — same approach.
- The script thread must never call Minecraft APIs directly (thread-safety violation). All MC calls must route through `MinecraftClient.execute()`. `PathmindActionContext` in the API surface handles this marshaling so addon authors cannot accidentally call MC APIs on the wrong thread.
- Script runs to completion with no game-tick preemption by default. This is acceptable for v1 (sandboxing / timeout is explicitly out of scope). If timeouts are added later, Cobalt's VM interruption API can cancel mid-execution.

**Example (conceptual flow):**

```java
// In ExecutionManager.advanceTick() — simplified
if (currentNode.isAddonNode()) {
    if (pendingAddonFuture == null) {
        AddonNodeExecutor executor = NodeTypeRegistry.INSTANCE.executorFor(currentNode.addonTypeId());
        AddonNodeContext ctx = AddonNodeContext.from(currentNode, variableContext, actionContext);
        pendingAddonFuture = executor.execute(ctx);  // kicks off worker thread
    }
    if (pendingAddonFuture.isDone()) {
        NodeResult result = pendingAddonFuture.join();
        pendingAddonFuture = null;
        advanceToNextNode(result);
    }
    // else: do nothing this tick; game continues normally
}

// In LuaNodeExecutor (addon impl):
public CompletableFuture<NodeResult> execute(AddonNodeContext ctx) {
    return CompletableFuture.supplyAsync(() -> {
        LuaTable pathmindLib = buildPathmindLib(ctx);  // wraps ctx methods as Lua globals
        LuaValue chunk = globals.load(ctx.scriptSource());
        chunk.call();  // blocks worker thread until Lua script finishes
        return NodeResult.SUCCESS;
    }, SCRIPT_EXECUTOR);  // fixed-thread pool, not ForkJoinPool (avoids starving Minecraft)
}

// In PathmindActionContext.moveToPosition(BlockPos pos):
// (called from Lua via Java bridge, running on worker thread)
public void moveToPosition(BlockPos pos) throws InterruptedException {
    CompletableFuture<Void> done = new CompletableFuture<>();
    MinecraftClient.getInstance().execute(() -> {
        pathmindNavigator.goTo(pos, () -> done.complete(null));  // schedules on main thread
    });
    done.join();  // blocks worker thread until navigator reports done
}
```

---

### Pattern 4: API vs Implementation Boundary (Package Discipline)

**What:** All public types under `com.pathmind.api.*` are the stable surface addons compile against. Everything else (`com.pathmind.nodes`, `com.pathmind.execution`, `com.pathmind.ui`, etc.) is internal. No types from internal packages appear in any `api.*` method signature. This is the JEI principle: "If you make the mistake of compiling against the full mod jar and then use classes that are not in the API, your mod could break any time JEI updates."

**When to use:** From day one. Discipline is cheap; a poorly-bounded API is expensive to fix once the addon is built.

**Trade-offs:** Requires `AddonNodeContext` to expose proxy methods for everything the addon legitimately needs (variable access, action dispatch, game state queries). This is worth it — it also documents what the addon API promises to keep stable.

**Build enforcement (optional):** A Gradle `sourceSets.api` configuration that compiles only the `com.pathmind.api` package and publishes a `pathmind-VERSION-api.jar`. The addon's build.gradle uses `modCompileOnly "com.pathmind:pathmind:VERSION:api"`. This enforces the boundary at compile time. For v1, it is sufficient to use package discipline and document the convention — the Gradle enforcement can be added when a second external addon exists.

---

## Data Flow

### Addon Registration Flow (mod init, one-time)

```
Fabric Loader: onInitialize
    │
    ▼
PathmindMod.onInitialize()
    │
    ├── AddonLoader.discoverAndLoad()
    │       └── FabricLoader.getEntrypointContainers("pathmind", PathmindAddonEntrypoint.class)
    │               for each container:
    │                   container.getEntrypoint().registerNodes(registrar)
    │                       └── registrar.register(definition, executor, serializer)
    │
    └── NodeTypeRegistry.INSTANCE.install(registrar)  // seals registry
            └── merges addon definitions into master lookup
                (addon node IDs prefixed "modid:typename" to avoid collisions)
```

### Graph Execution Flow (existing, with addon node path added)

```
User presses play
    │
    ▼
ExecutionManager.startExecution()
    │   validates graph (GraphValidator)
    ▼
ExecutionManager.advanceTick()   ← called each game tick
    │
    ├── [built-in node type]  →  Node.execute()  → synchronous result  →  advanceGraph()
    │
    └── [addon node type]
            │
            ├── [first tick]  →  executor.execute(ctx)  →  CompletableFuture stored
            │                    └── worker thread: LuaNodeExecutor runs Cobalt VM
            │                        └── Lua→Java bridge calls:
            │                            ctx.variables().get("x")   // direct, thread-safe
            │                            ctx.actions().moveTo(pos)  // dispatches to main thread,
            │                                                        // blocks worker until done
            │
            └── [subsequent ticks]  →  future.isDone()?
                    │
                    ├── NO:   game tick proceeds normally (no stall)
                    └── YES:  read NodeResult → advanceGraph() → clear pendingAddonFuture
```

### JSON Persistence Flow (addon node)

```
Graph save:
    NodeGraphPersistence.saveGraph()
        └── for each addon node:
                serializer = NodeTypeRegistry.INSTANCE.serializerFor(node.addonTypeId())
                extraFields = serializer.serialize(node)   // e.g. {"script": "...lua code..."}
                NodeData.extraFields = extraFields
                → written to JSON as part of node object

Graph load:
    NodeGraphPersistence.loadGraph()
        └── for each NodeData with addonTypeId:
                if NodeTypeRegistry.INSTANCE.has(addonTypeId):
                    def = registry.definitionFor(addonTypeId)
                    node = AddonNodeInstance.create(def)
                    serializer.deserialize(node, nodeData.extraFields)  // restores script text
                else:
                    // addon not installed: create placeholder node, mark as "unresolved"
                    // graph still loads; execution refuses to run until issue resolved
```

---

## Recommended Build Order

Dependencies must be built before what depends on them. Co-evolution means alternating between Pathmind and the addon, but the dependency direction is fixed:

```
Phase 1: API surface in Pathmind (no addon yet)
    └── Define com.pathmind.api.addon interfaces (empty impls ok)
    └── Implement AddonLoader + NodeTypeRegistry in Pathmind
    └── Wire AddonLoader into PathmindMod.onInitialize
    └── Pathmind runs standalone; addon registry is empty but code path exists

Phase 2: Stub addon (proves entrypoint wiring works)
    └── Create sibling repo with fabric.mod.json listing "pathmind" entrypoint
    └── LuaAddonEntrypoint registers a no-op stub node
    └── Stub node appears in editor (proves NodeGraph reads from NodeTypeRegistry)
    └── Stub node executes (proves ExecutionManager routes to AddonNodeExecutor)
    └── JSON save/load of stub node works (proves serialization path)

Phase 3: Async execution contract
    └── ExecutionManager: implement CompletableFuture polling for addon nodes
    └── LuaNodeExecutor: bare-minimum Cobalt/LuaJ VM runs "return 1" from a worker thread
    └── Async test: script node pauses graph for N ticks then completes

Phase 4: Pathmind Lua library (PathmindActionContext / PathmindVariableContext)
    └── Variable read/write exposed to Lua (simplest useful feature)
    └── One action (e.g. chat message) exposed as Lua function, dispatched via main thread
    └── Script can call variables and trigger an action

Phase 5: In-node script editor UI
    └── LuaScriptEditorWidget (plain text, line numbers)
    └── AddonNodeSerializer persists script text
    └── Editor visible and editable in NodeGraph

Phase 6: Full Lua library surface
    └── Movement/navigation actions
    └── Inventory queries
    └── Block/world state queries
    └── Autosuggestion hint map (simple static string list)
```

Each phase produces something testable in-game, and the addon cannot progress past Phase 2 until Pathmind's Phase 1 is stable.

---

## Anti-Patterns

### Anti-Pattern 1: Enum-Locked Node Type Registry

**What people do:** Keep `NodeType` as a plain Java `enum` and add `ADDON_SCRIPT` as the one addon sentinel, routing all addons through it. Or try to add addon types to the enum at runtime (impossible in Java).

**Why it's wrong:** Enums cannot be extended at runtime. A single `ADDON_SCRIPT` sentinel means all addon nodes share one type ID — the serializer cannot distinguish them, the UI cannot give them distinct appearances, and the graph cannot route them to different executors. This is the trap Pathmind will fall into if the existing `NodeType` enum is not replaced or augmented.

**Do this instead:** Keep `NodeType` enum for the fixed built-in types. Add a parallel `String addonTypeId` field to `Node` (namespaced: `"modid:name"`). The `NodeType` enum gains one value `ADDON` that signals "look up addonTypeId for actual behavior." `NodeTypeRegistry` holds the addon definitions indexed by string ID, not by enum constant.

---

### Anti-Pattern 2: Exposing Internal Types in the API Surface

**What people do:** Return `NodeBehaviorDefinition`, `RuntimeParameterData`, or `Node` from `AddonNodeContext` methods because it's convenient — these are already in the codebase.

**Why it's wrong:** The addon now compiles against internal classes. Any refactor of `Node.java` (already identified as a god-object needing refactoring) breaks the addon. The "stable API" promise is immediately broken.

**Do this instead:** The `AddonNodeContext` API exposes only types that live in `com.pathmind.api.*`. Internal types are wrapped behind API-surface proxy methods. If the addon needs variable access, expose `VariableContext.get(String name)` — not `RuntimeParameterData.getVariableMap()`.

---

### Anti-Pattern 3: Running Lua (or any script) on the Main Thread Synchronously

**What people do:** Call `luaVm.execute(script)` directly inside `ExecutionManager.advanceTick()`, blocking the main game thread until the script finishes.

**Why it's wrong:** Lua scripts can loop, sleep, or call waiting actions. Any non-trivial script freezes the game client for its entire duration. Even a fast script (< 1 ms) called at 20 TPS on a complex graph creates unpredictable frame timing.

**Do this instead:** Always run the VM on a worker thread. Return a `CompletableFuture<NodeResult>` immediately. Poll `.isDone()` each tick. The main thread is never blocked.

---

### Anti-Pattern 4: One Global Lua VM Instance

**What people do:** Create a single `Globals` (LuaJ) or `LuaState` (Cobalt) for the addon and reuse it across script node executions.

**Why it's wrong:** LuaJ/Cobalt's `Globals` is not thread-safe between concurrent executions. Even in a single-script-at-a-time model, residual global state from one execution leaks into the next (functions, variables the script defined globally). Scripts that define `function helper()` will pollute later executions.

**Do this instead:** Create a fresh `Globals` per execution (LuaJ's `JsePlatform.standardGlobals()` is cheap). Load and call the Pathmind library table into it, then load and run the script. Discard the `Globals` when the future completes. See LuaJ docs: "each thread must be given its own, distinct Globals instance."

---

### Anti-Pattern 5: Hard Dependency in the Wrong Direction

**What people do:** Import `LuaAddonEntrypoint` or any class from `pathmind-lua-addon` into Pathmind's common module to "check if the Lua addon is loaded."

**Why it's wrong:** This inverts the dependency. Pathmind must compile against the addon, which breaks standalone operation and couples the two release cycles.

**Do this instead:** Use the entrypoint discovery pattern for registration, and `FabricLoader.getInstance().isModLoaded("pathmind_lua_addon")` for any runtime conditional checks in Pathmind that need to behave differently when the addon is present (should be rare — design the API so Pathmind needs no knowledge of what addons are registered).

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Fabric Loader entrypoint API | `FabricLoader.getEntrypointContainers("pathmind", PathmindAddonEntrypoint.class)` | Runs during `onInitialize`; no ordering guarantee cross-mod |
| Cobalt / LuaJ VM | Embedded as a shaded dependency in the addon JAR | Must not conflict with any Cobalt version shipped by CC:Tweaked if both are present; use jar relocation in the addon's shadow build |
| `MinecraftClient.execute(Runnable)` | Queues work on the main game thread from a worker thread | The only safe way for the script thread to touch Minecraft state; each call returns a `CompletableFuture` the script thread blocks on |
| Pathmind `PathmindNavigator` | Pathmind's existing navigator exposed via `PathmindActionContext.moveTo()` | The action context wraps navigator calls behind the main-thread dispatch pattern |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `AddonLoader` → `NodeTypeRegistry` | Direct call: `registry.install(registrar)` | Happens once, synchronously, during `onInitialize` |
| `ExecutionManager` → `NodeTypeRegistry` | Lookup by string ID each execution | Read-only after sealing; no synchronization needed |
| `ExecutionManager` → `AddonNodeExecutor` | `executor.execute(ctx)` returns `CompletableFuture`; polling per tick | Executor starts worker thread; future polled on main thread |
| `LuaNodeExecutor` (worker thread) → `PathmindActionContext` (main thread dispatch) | `MinecraftClient.execute(task)` + `CompletableFuture.join()` blocking | Worker blocks; main thread processes MC action normally; navigator signals completion |
| Addon → Pathmind-api | `compileOnly` / `modCompileOnly` in Loom | Addon must not ship API classes; Pathmind provides them at runtime |
| NodeGraph UI → `NodeTypeRegistry` | Reads `AddonNodeDefinition` for display name, color, category, parameter descriptors | UI queries registry the same way it currently reads `NodeBehaviorDefinitionRegistry` |
| `NodeGraphPersistence` → `AddonNodeSerializer` | `serializer.serialize(node)` / `deserialize(node, fields)` | Called during save and load; "unresolved addon type" placeholder handles missing addons gracefully |

---

## Sources

- Fabric Loader entrypoint docs (wiki.fabricmc.net, redirected from fabricmc.net): `FabricLoader.getEntrypointContainers` API, `EntrypointContainer` wrapper, ordering guarantees
- JEI Getting Started wiki (github.com/mezz/JustEnoughItems/wiki): `compileOnly` API jar + `runtimeOnly` full jar pattern; risk of compiling against full jar
- JEI Creating Plugins (deepwiki.com/mezz/JustEnoughItems/3.2): `IModPlugin`, `@JeiPlugin`, registration phases (ingredients → categories → recipes → runtime), `onRuntimeAvailable(IJeiRuntime)`
- REI entrypoint keys and plugin interfaces (github.com/shedaniel/RoughlyEnoughItems issues): `rei_client` / `rei_server` / `rei_common` entrypoint keys; `REIClientPlugin` / `REIServerPlugin` split; registry objects passed per phase
- AE2 Addon API guide (guide.appliedenergistics.org/1.19.2/api): `IAEAddonEntrypoint`, thread-safe registry classes, post-init modification is undefined behavior
- CC:Tweaked internals (squiddev.cc/2019/03/08): Cobalt VM, exception-based yield/coroutine model, VM interruption/suspension, thread-count reduction from per-coroutine threads to pooled approach
- Cobalt GitHub (github.com/cc-tweaked/Cobalt): "allows yielding anywhere within a Lua program"; v0.7.0 latest; API stability caveated against CC:Tweaked sync
- LuaJ thread-safety docs (luaj.org): each thread needs its own `Globals` instance; `OrphanedThread` coroutine GC
- Architectury build artifacts (deepwiki.com/architectury/architectury-api/10.1): no built-in API/impl split; manual subproject approach required
- Pathmind codebase (existing): `NodeBehaviorDefinitionRegistry` (EnumMap on NodeType enum), `NodeBehaviorDefinition` (behavior strategy per type), `fabric.mod.json` (existing entrypoints: `main` + `client`), `PathmindMod.onInitialize` (where `AddonLoader` must be called)

---
*Architecture research for: Minecraft mod addon API + embedded Lua scripting node*
*Researched: 2026-06-12*
