# Feature Research

**Domain:** Minecraft mod addon API + embedded Lua scripting node
**Researched:** 2026-06-12
**Confidence:** HIGH (addon API patterns), MEDIUM (script node UX specifics)

---

## Feature Landscape

### Table Stakes — Addon API (Users Expect These)

Features that developers building on top of Pathmind will expect by analogy with REI, Fabric API, and JEI. Missing any of these makes the API feel amateur or untrustworthy.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Entrypoint discovery via `fabric.mod.json` | Every Fabric-targeting mod uses this. Addons declare their plugin class under a named key; Pathmind reads it via `FabricLoader#getEntrypointContainers`. No bespoke discovery mechanism needed. | LOW | Direct reuse of Fabric Loader's built-in mechanism. Pathmind declares an entrypoint contract interface (e.g. `PathmindPlugin`); addons implement it and declare it in `fabric.mod.json`. |
| Typed node-registration callback | JEI's `registerCategories`, REI's plugin registration callbacks. Addons get a typed registration object, not raw reflection. | MEDIUM | A `PathmindNodeRegistry` or similar passed into a lifecycle callback; addons call `registry.registerNode(...)`. |
| Graceful no-op when no addons are installed | Pathmind must start and run fully without any addons present. JEI and REI both work without any registered plugins. | LOW | Guard all plugin calls with null/empty list checks; initialize built-in nodes before any addon registration phase. |
| Separate API artifact / compile dependency | REI, JEI, Fabric API all publish an `-api` jar to Maven. Addon authors add it as a `compileOnly` or `modCompileOnly` dep. If Pathmind ships one fat jar with no API boundary, addon authors depend on internals. | MEDIUM | Extract a `pathmind-api` module or `api` source set. Publish to local/remote Maven. The sibling repo proves it is consumable. |
| Lifecycle ordering guarantee | Fabric guarantees only: `main` → `client` → entrypoints called per-mod in declaration order. JEI calls `registerCategories` before `registerRecipes`. Addons need to know when it is safe to call what. | MEDIUM | Document and enforce: discovery → `onRegisterNodes()` → `onRegisterUI()` → game ready. Do not call addon hooks mid-execution. |
| Registration-time validation with clear errors | JEI throws and logs a clear error if a plugin is malformed (null plugin UID, duplicate registration). Addons should get informative errors at load time, not cryptic NPEs during gameplay. | LOW | Validate required fields (node type ID, non-null executor) at registration time; log with mod ID context. |
| Addon-agnostic JSON persistence | Addons declare what data their nodes persist; Pathmind stores and restores it without knowing the schema. REI/JEI save per-category state without hardcoding each plugin's format. | HIGH | NodeGraphData must store an opaque blob per custom node type, keyed by the addon's registered type ID. GSON's `JsonElement` or a raw `Map<String, Object>` envelope works. Custom nodes must provide a serialize/deserialize pair. |
| Pathmind runs unchanged across MC 1.21–1.21.11 | The addon API must not break Pathmind's existing multi-version Architectury build. | MEDIUM | API surface must only use Architectury-safe or version-agnostic classes; no platform-specific types in the public API. |

### Table Stakes — Script Node UX (Users Expect These)

Features that users expect from any embedded code editor in a game, by analogy with CC:Tweaked's in-game terminal, KubeJS's console output, and Figura's error feedback.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Functional plain-text editing | Without basic text entry (cursor movement, selection, delete, paste) the editor is unusable. CC:Tweaked's terminal is the floor. | MEDIUM | Minecraft's immediate-mode rendering makes multi-line text editing non-trivial. Requires a text buffer model, cursor tracking, and scroll support. No off-the-shelf Fabric UI widget does this well. |
| Visible line numbers | Every code editor users have seen (VS Code, IntelliJ, CC:Tweaked's edit program) shows line numbers. Lua errors reference line numbers; without display, error messages are unactionable. | LOW | Rendered as a fixed-width gutter left of the text area. |
| Error display co-located with the node | KubeJS routes errors to `ConsoleJS` (a log stream). CC:Tweaked prints tracebacks to the terminal in-place. Figura shows badge indicators. Users expect to see the error without leaving the editor or hunting logs. | MEDIUM | Display last-run error (message + line number) below or beside the code area inside the node widget. Red highlight on the error line if feasible. |
| Script persists with the node | Pathmind uses JSON presets. The Lua source must survive save/load cycles as part of the node's data. If the script is lost on reload, the node is useless. | LOW | Serialize script text as a string field in the node's addon-agnostic JSON blob. |
| Execution blocks the node tree until script finishes | The user story says "once the script finishes the node tree continues." This is table stakes for a Script node to be useful in a sequential automation flow. Any async leak means the next node fires before the script is done. | HIGH | The async-sync bridging problem: Lua runs synchronously but Pathmind actions are asynchronous (Baritone pathfinding, timed delays). Need a coroutine or Future-based suspension model. LuaJ supports coroutine yield; PathmindActions can resolve futures. See PITFALLS.md. |
| Read/write Pathmind graph variables from Lua | The user story explicitly requires this. Without it, the Script node cannot interoperate with the rest of the graph (no input, no output). | HIGH | A `pathmind.getVar("name")` / `pathmind.setVar("name", value)` Lua global binding backed by ExecutionManager's runtime variable map. |
| Invoke Pathmind actions from Lua | "Invoke actions and await completion" is in the requirements. Actions include movement (Baritone) and inventory ops. Without this, the node is a pure compute node with no game effect. | HIGH | Each Pathmind action exposed as a Lua function that blocks the Lua coroutine until the action completes (Future.get() or coroutine yield on an event). |
| Autosuggestion for Pathmind Lua API | "Simplest reliable form of LSP-style autosuggestions" is in requirements. Users cannot memorize `pathmind.*` API names; at minimum a prefix-match popup on `.` is expected for any scriptable game system in 2025+. | MEDIUM | Does not need full LSP. A static list of known `pathmind.*` names with prefix matching shown in a small dropdown. Figura's `@LuaWhitelist` / `@LuaMethodDoc` annotation pattern generates this list from source. |

---

### Differentiators (Competitive Advantage)

Features that go beyond what comparable products provide and create distinctive value for Pathmind's specific use case.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Co-located node editor (script lives inside the node) | CC:Tweaked and Figura both use external editors (VSCode with plugins) or a separate in-game terminal screen. Pathmind's Script node editing inline in the graph editor keeps the user in their workflow context. | HIGH | The Script node's visual body expands to show the editor widget inline. This is novel vs all surveyed comparable products. |
| Await-capable Lua bindings for Pathmind async actions | CC:Tweaked's turtle API is synchronous but can't call arbitrary modded actions. KubeJS bindings are fire-and-forget or event-driven, not coroutine-awaitable. A `pathmind.moveTo(x,y,z)` call that blocks Lua until arrival is cleaner than any comparable in-mod API. | HIGH | Requires LuaJ coroutine integration with Pathmind's async execution model. The tarasyk.ca case study confirms this is feasible with LuaJ + Futures. High complexity but high payoff. |
| API generated from annotations (Figura pattern) | Figura generates its Lua API reference and autosuggestion list from `@LuaWhitelist` / `@LuaMethodDoc` Java annotations. This means documentation and autocomplete stay in sync with code. No separate documentation maintenance. | MEDIUM | Apply the same pattern to Pathmind's Lua binding classes. The addon's `pathmind` Lua global has all its methods annotated; a static list is generated at build time or startup reflection. |
| Addon proves API honesty (sibling repo true consumer) | REI's plugin API and Fabric API are both consumed by thousands of mods. Pathmind's sibling-repo addon is designed to be an honest external consumer that proves the API is real. This is a design differentiator vs. internal test plugins or same-repo addons. | LOW (design) | The sibling repo setup is a process/architecture choice, not a feature in itself, but it differentiates Pathmind's addon API from superficial "API-ish" hooks. |
| Game-state queries from Lua (position, inventory, blocks) | CC:Tweaked limits queries to what the turtle can sense (three adjacent blocks). KubeJS queries are event-driven. A `pathmind.getPlayerPos()`, `pathmind.getInventory()`, `pathmind.getBlock(x,y,z)` surface makes Lua scripts a first-class automation tool. | MEDIUM | Wrap existing Pathmind MC bridge utility classes as Lua globals. The binding layer is thin once the bridge classes exist. |

---

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Script-driven control flow (Lua return steers next node) | "My script returns true/false, I want the graph to branch on that" — sounds natural. | Pathmind already has branch/conditional nodes. Adding Lua-driven control flow means the graph visual representation no longer reflects actual flow; debugging becomes opaque. PROJECT.md explicitly calls this out-of-scope. | Use existing branching nodes; the Script node sets a graph variable that a downstream Condition node reads. |
| Syntax highlighting in v1 | Users see syntax highlighting in every editor they use daily. | Requires a full tokenizer/lexer pass per frame (or cached) on the displayed text, integrated into Minecraft's immediate-mode renderer. Significant complexity for marginal UX gain over line numbers + error display. No comparable in-game Minecraft tool does this. | Ship plain text + line numbers + error indicators in v1. Syntax highlighting is a v1.x enhancement. |
| Full sandboxing / resource limits in v1 | Users with a security mindset (or multiplayer operators) will ask "what stops a bad script from hanging the game?" | Sandboxing Lua on the JVM (preventing `os.execute`, limiting memory, enforcing timeouts) is a non-trivial implementation. LuaJ provides some primitives but a robust sandbox is a project in itself. PROJECT.md explicitly defers this. | Accept scripts are trusted user content for v1 (same trust model as KubeJS). Log a warning in the node UI. Add timeout/sandbox in a later milestone. |
| NeoForge support for the addon v1 | Pathmind itself supports both Fabric and NeoForge via Architectury. Addon users on NeoForge will ask why their loader is excluded. | Expanding to NeoForge before the API is stable means any breaking API change requires two simultaneous fixes. The sibling repo would need dual build targets from day one, significantly increasing complexity. | Target Fabric 1.21.4 exclusively for addon v1; document that NeoForge is planned post-stabilization. |
| External editor integration (VSCode LSP server) | Figura users have an external VSCode extension for full IDE support. It's a popular pattern in scripting mods. | An LSP server is a separate long-running process outside Minecraft. The integration surface (file watching, socket protocol, in-game file sync) is a substantial separate project. | Ship the in-game editor with autosuggestions. External editing is possible by users who edit the JSON preset manually; a proper LSP extension is a post-v1 community contribution target. |
| Per-node script hot-reload without graph restart | "Change my script, see results without stopping the whole graph" — developer quality-of-life. | Hot-reloading a single node mid-execution while other nodes are running creates undefined state: the LuaJ runtime for that node has local variables, the graph has live connections. Race conditions are severe. | Support re-running the node (and graph) from the editor normally. Hot-reload is a v2 feature with a well-defined lifecycle contract. |
| Full KubeJS-style script lifecycle (startup/server/client) | KubeJS maps scripts to game lifecycle phases. Some Pathmind users may want persistent scripts. | Pathmind's Script node is embedded in an explicit execution graph — the graph IS the lifecycle. Adding free-floating scripts outside the graph breaks the visual programming model and makes Pathmind a second KubeJS. | Keep scripts in nodes. The graph execution lifecycle is the script lifecycle. |

---

## Feature Dependencies

```
[Addon API: Entrypoint Discovery]
    └──requires──> [fabric.mod.json registration contract defined]
    └──enables──> [Lua Addon: Script Node Registration]

[Addon API: Node Registration API]
    └──requires──> [Addon API: Entrypoint Discovery]
    └──requires──> [Addon API: Separate API Artifact published]
    └──enables──> [Lua Addon: Script Node placed in editor]

[Addon API: Addon-agnostic JSON Persistence]
    └──requires──> [Addon API: Node Registration API]
    └──enables──> [Lua Addon: Script text persists with preset]

[Addon API: UI Extension Points]
    └──requires──> [Addon API: Node Registration API]
    └──enables──> [Lua Addon: Inline code editor widget in node body]

[Addon API: Execution Hooks]
    └──requires──> [Addon API: Node Registration API]
    └──enables──> [Lua Addon: LuaJ VM invoked during graph execution]

[Lua Addon: LuaJ VM execution]
    └──requires──> [Addon API: Execution Hooks]
    └──requires──> [Lua Addon: Async-sync bridging model resolved]
    └──enables──> [Lua Addon: Pathmind action bindings (moveTo, etc.)]
    └──enables──> [Lua Addon: Graph variable read/write bindings]

[Lua Addon: Graph variable read/write bindings]
    └──requires──> [Lua Addon: LuaJ VM execution]
    └──requires──> [ExecutionManager runtime variable map accessible from addon layer]

[Lua Addon: Pathmind action bindings]
    └──requires──> [Lua Addon: LuaJ VM execution]
    └──requires──> [Async-sync bridging: LuaJ coroutine + Java Future pattern]

[Lua Addon: In-node code editor widget]
    └──requires──> [Addon API: UI Extension Points]
    └──requires──> [Multi-line text buffer with cursor + scroll]
    └──enables──> [Lua Addon: Error display co-located with node]

[Lua Addon: Autosuggestions]
    └──requires──> [Lua Addon: In-node code editor widget]
    └──requires──> [Static Pathmind Lua API name list (annotation-generated or hardcoded)]

[Lua Addon: Error display]
    └──requires──> [Lua Addon: In-node code editor widget]
    └──requires──> [LuaJ error capture (LuaError.getMessage, line number)]
```

### Dependency Notes

- **Addon API: Separate API Artifact** must be the first concrete output — without a published artifact the sibling repo cannot even compile.
- **Async-sync bridging** is the single highest-risk dependency. Everything that makes the Script node useful for automation (moveTo, wait, timed actions) depends on it working correctly. The tarasyk.ca implementation and CC:Tweaked's blocking API both demonstrate this is solvable with LuaJ coroutines + Java Futures, but it requires upfront design.
- **ExecutionManager runtime variable map** must be accessible from the addon API layer. Currently ExecutionManager is a singleton with package-private or tightly coupled state. The refactor to expose it cleanly is driven by this dependency.
- **UI Extension Points** can be phased: a minimal v1 may ship with a fixed-size editor widget in the node body; richer panel/dock extension can come later.

---

## MVP Definition

### Launch With (v1)

Minimum viable product — what's needed to prove the addon API is real and the Lua Script node is usable.

- [ ] Addon entrypoint discovery via `fabric.mod.json` — without this the addon cannot load at all
- [ ] Node registration API (`PathmindNodeRegistry.registerNode(...)`) — the core addon capability
- [ ] Addon-agnostic JSON persistence for custom nodes — without it, scripted presets are destroyed on save/load
- [ ] Pathmind standalone-capable (no addons = no change in behavior) — required by the core constraint
- [ ] Separate API artifact (`pathmind-api`) publishable to local Maven — the sibling repo must be able to compile
- [ ] Script node placed in editor via addon registration — proves the API end-to-end
- [ ] Plain-text code editor with line numbers in node body — minimum usable editor
- [ ] LuaJ VM executes script on node execution — core behavior
- [ ] Graph variable read/write from Lua (`pathmind.getVar`, `pathmind.setVar`) — mandatory for interop
- [ ] At least one awaitable action binding (e.g. `pathmind.moveTo`) — proves async-sync model works
- [ ] Error display in node UI (message + line number) — without this, debugging is impossible

### Add After Validation (v1.x)

Features to add once core is working and the API has been used against at least one complete real-world workflow.

- [ ] Full game-state query bindings (position, inventory, block inspection) — adds significant utility, builds on v1 bindings
- [ ] Autosuggestions for Pathmind Lua API — high UX value once the API surface is stable enough to enumerate
- [ ] UI Extension Points for custom panels/categories — enables richer addons; v1 only needs node body widget
- [ ] Execution hooks for pre/post-graph-run lifecycle — enables more complex addon behaviors

### Future Consideration (v2+)

Features to defer until the API has proven stable across at least one full public release cycle.

- [ ] Script sandboxing / timeout enforcement — acknowledged as needed eventually; PROJECT.md explicitly defers
- [ ] NeoForge addon support — adds build complexity; wait for API stability
- [ ] Syntax highlighting — meaningful complexity for aesthetic gain; feasible once editor widget is stable
- [ ] Hot-reload for script nodes — requires careful lifecycle design to avoid mid-execution state corruption
- [ ] External LSP / VSCode extension — community contribution target post-v1

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Addon entrypoint discovery | HIGH | LOW | P1 |
| Node registration API | HIGH | MEDIUM | P1 |
| Addon-agnostic JSON persistence | HIGH | HIGH | P1 |
| Pathmind standalone-capable | HIGH | LOW | P1 |
| Separate API artifact (`pathmind-api`) | HIGH | MEDIUM | P1 |
| Script node in editor | HIGH | LOW (once API exists) | P1 |
| Plain-text editor + line numbers | HIGH | MEDIUM | P1 |
| LuaJ VM execution | HIGH | MEDIUM | P1 |
| Graph variable read/write from Lua | HIGH | HIGH | P1 |
| Awaitable action bindings (moveTo etc.) | HIGH | HIGH | P1 |
| Error display in node UI | HIGH | MEDIUM | P1 |
| Game-state query bindings | MEDIUM | MEDIUM | P2 |
| Autosuggestions for Lua API | MEDIUM | MEDIUM | P2 |
| UI Extension Points (panels/categories) | MEDIUM | HIGH | P2 |
| Execution lifecycle hooks | MEDIUM | MEDIUM | P2 |
| Syntax highlighting | LOW | HIGH | P3 |
| Script sandboxing / timeouts | MEDIUM | HIGH | P3 |
| NeoForge addon support | LOW | HIGH | P3 |
| Hot-reload script nodes | MEDIUM | HIGH | P3 |
| External LSP / VSCode extension | LOW | HIGH | P3 |

---

## Competitor Feature Analysis

| Feature | REI (REIClientPlugin) | Fabric API | JEI (@JeiPlugin) | KubeJS | CC:Tweaked | Pathmind Plan |
|---------|----------------------|------------|------------------|--------|------------|---------------|
| Plugin discovery | `fabric.mod.json` entrypoint key (`rei_client`, `rei_server`) | `fabric.mod.json` entrypoint (`main`, `client`) | `@JeiPlugin` annotation scanned at startup | `kubejs.plugins.txt` + Java `ServiceLoader` | N/A (it is the host) | `fabric.mod.json` entrypoint key (`pathmind`) |
| Registration lifecycle | `registerCategories` → `registerRecipes` → `onRuntimeAvailable` | `onInitialize()` (main) + `onInitializeClient()` (client) | `registerCategories` → `registerRecipes` → `onRuntimeAvailable` | 20+ typed registration hooks | N/A | `onRegisterNodes()` → `onRegisterUI()` → game ready |
| Versioned API artifact | Yes (`rei-api` jar on Maven) | Yes (Fabric API modules on Maven) | Yes (`jei-api` jar) | Yes (`kubejs-forge/fabric` on Maven) | Yes (cc-tweaked API jar) | Plan: `pathmind-api` module |
| Graceful no-op | Yes (REI works without plugins) | Yes (Fabric API works standalone) | Yes (JEI works without plugins) | Yes | N/A | Yes (required) |
| Addon persistence | Not applicable (REI state is per-category) | Not applicable | Not applicable | Scripts are files on disk | Scripts on disk | Addon-agnostic JSON blob in NodeGraphData |
| Script editor | N/A | N/A | N/A | External (VSCode recommended) | In-game terminal (`edit` program) | Inline in node body (novel) |
| Error reporting | Not applicable | Not applicable | Startup log only | `ConsoleJS` log stream + WebSocket | In-terminal traceback | Inline in node UI |
| Async action await | N/A | N/A | N/A | Event callbacks, not coroutine-await | Blocking synchronous | LuaJ coroutine + Future (novel) |
| API generated from annotations | No | No | No | Partial (event type metadata) | No | Plan: `@LuaWhitelist`/`@LuaMethodDoc` (Figura pattern) |

---

## Sources

- [REI GitHub (shedaniel/RoughlyEnoughItems)](https://github.com/shedaniel/RoughlyEnoughItems) — entrypoint keys, plugin type separation
- [Fabric Entrypoints Wiki](https://wiki.fabricmc.net/documentation:entrypoint) — `fabric.mod.json` format, `FabricLoader#getEntrypointContainers`, lifecycle guarantees
- [JEI IModPlugin source (1.21.x)](https://github.com/mezz/JustEnoughItems/blob/1.21.x/CommonApi/src/main/java/mezz/jei/api/IModPlugin.java) — registration lifecycle methods
- [DeepWiki: Creating JEI Plugins](https://deepwiki.com/mezz/JustEnoughItems/3.2-creating-jei-plugins) — plugin annotation, registration sequence, `IJeiHelpers`
- [KubeJS Plugin Architecture (DeepWiki)](https://deepwiki.com/KubeJS-Mods/KubeJS) — `KubeJSPlugin` interface, 20+ hooks, `ConsoleJS` error routing, `ServiceLoader` discovery
- [Figura Lua Scripting System (DeepWiki)](https://deepwiki.com/FiguraMC/Figura/4-lua-scripting-system) — isolated runtimes, `@LuaWhitelist`/`@LuaMethodDoc` annotation system, `WHITELISTED_CLASSES`, sandboxing
- [CC:Tweaked Turtle API](https://tweaked.cc/module/turtle.html) — synchronous blocking Lua API for game actions, error-return convention
- [How I Added Lua Scripting to Minecraft (tarasyk.ca)](https://tarasyk.ca/2019/10/28/tasky-blog-01.html) — LuaJ + Minecraft, async-sync bridging with Futures, `InterruptLib` for halting, timeout pattern
- [Microsoft Minecraft Scripting Versioning](https://learn.microsoft.com/en-us/minecraft/creator/documents/scripting/versioning?view=minecraft-bedrock-stable) — semantic versioning for game scripting APIs
- [REI Plugin Compatibilities (Modrinth)](https://modrinth.com/mod/roughly-enough-items-hacks) — evidence that API versioning instability causes ecosystem fragmentation
- [Unreal Blueprints Custom Node Registration](https://unrealist.org/custom-blueprint-nodes/) — `UK2Node` base class, `GetMenuActions`, visual scripting node extension patterns

---

*Feature research for: Minecraft mod addon API + embedded Lua scripting node*
*Researched: 2026-06-12*
