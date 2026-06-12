# Project Research Summary

**Project:** Pathmind Addon API + Lua Scripting Addon
**Domain:** Minecraft mod addon API retrofit + embedded Lua scripting node (Fabric / Architectury / Java 21)
**Researched:** 2026-06-12
**Confidence:** HIGH

## Executive Summary

Pathmind needs to expose a formal addon API (modeled on REI, Fabric API, and JEI) and simultaneously build the first addon against that API — a Lua Script node backed by an embedded JVM Lua VM. The recommended approach follows the Fabric entrypoint discovery pattern: Pathmind defines a `PathmindAddonEntrypoint` interface in an `api` package within the existing `common` module, addons declare their implementing class under a `"pathmind"` key in `fabric.mod.json`, and Pathmind calls `FabricLoader.getEntrypointContainers("pathmind", ...)` during `onInitialize`. A `NodeTypeRegistrar` object is passed to each addon, sealed after all entrypoints run, and installed into a string-keyed `NodeTypeRegistry`. The API is published as a separate Maven artifact via `publishToMavenLocal` during development and Modrinth Maven after release. The Lua VM of choice is Cobalt (`org.squiddev:Cobalt:0.7.3`) — a pure-JVM, Java-21-proven, re-entrant fork of LuaJ maintained by the CC:Tweaked project, shadow-relocated into the addon JAR.

The single highest-risk element is the async-sync bridging model: Lua scripts must run on a worker thread (never the main game thread), and calls to Pathmind actions that are inherently async (Baritone pathfinding, timed delays) must block the worker thread via `CompletableFuture.join()` while the main game thread continues ticking. `ExecutionManager` polls `future.isDone()` each tick; when true, it advances the graph. This pattern is proven by CC:Tweaked's blocking turtle API and the tarasyk.ca LuaJ case study. Getting this bridging model correct in Phase 3 is the technical prerequisite for everything that makes the Script node useful.

The secondary risks are all addressable with upfront discipline: the API/impl boundary must be established before any addon code is written, the Lua `Globals` instance must be created fresh per execution, `JsePlatform.standardGlobals()` must never be used (it exposes `luajava` — full JVM access), Fabric's weak entrypoint ordering guarantee requires a queued-registration guard or a `PATHMIND_READY` custom event, and the Lua node's JSON output must include a `_schema_version` field from the first serialization commit. None of these are exotic — every comparable project has hit them, and the mitigations are well-documented.

## Key Findings

### Recommended Stack

The existing Pathmind base requires no changes. Three new technology decisions are needed.

**Core technologies:**
- `org.squiddev:Cobalt:0.7.3` (SquidDev Maven): Lua 5.2 VM — pure JVM, Java 21 proven, re-entrant coroutine model; shadow-relocate to avoid CC:T conflicts
- `FabricLoader.getEntrypointContainers(...)`: Addon discovery — standard Fabric mechanism; no classpath scanning, loader-aware
- `maven-publish` + `publishToMavenLocal`: API artifact publishing — standard Loom-compatible; Loom auto-wires `remapJar`
- `net.minecraft.client.gui.widget.EditBoxWidget`: In-game multiline editor — built into MC 1.21.4, no third-party dep needed
- `com.gradleup.shadow` (already used): Cobalt relocation into addon JAR

### Expected Features

**Must have (table stakes):**
- Addon entrypoint discovery via `fabric.mod.json`
- Node registration API (`NodeTypeRegistrar.register(definition, executor, serializer)`)
- Addon-agnostic JSON persistence (opaque blob per node type)
- Pathmind standalone-capable (no addons = unchanged behavior)
- Separate API artifact published to Maven (`com.pathmind.api` package only)
- Lifecycle ordering guarantee (discovery → registerNodes → game ready)
- Registration-time validation with informative errors
- Functional plain-text editor with line numbers in node body
- Cobalt VM execution with per-execution fresh `Globals`
- Graph variable read/write from Lua (`pathmind.getVar` / `pathmind.setVar`)
- At least one awaitable action binding (`pathmind.moveTo`) proving async-sync model
- Error display co-located with the node (message + line number)

**Should have (differentiators):**
- Await-capable Lua bindings for all Pathmind async actions
- Full game-state query bindings (position, inventory, blocks)
- Autosuggestions for Pathmind Lua API (annotation-generated prefix-match, Figura pattern)
- Co-located inline node editor (script inside the node body — novel vs CC:Tweaked/KubeJS)

**Defer (v2+):**
- Script sandboxing / timeout enforcement (use wall-clock thread interrupt as minimum safety net in v1)
- NeoForge addon support
- Syntax highlighting
- Hot-reload for script nodes
- External LSP / VSCode extension

### Architecture Approach

Three-layer separation: stable `com.pathmind.api` package → internal Pathmind implementation with thin adapter layer → sibling-repo addon consuming only the API via `modCompileOnly`. The existing `NodeType` enum gains an `ADDON` sentinel; a parallel string-keyed `NodeTypeRegistry` holds addon definitions. `ExecutionManager` detects `NodeType.ADDON` nodes, delegates to `AddonNodeExecutor.execute(ctx)` which returns a `CompletableFuture<NodeResult>`, holds the future, and polls `.isDone()` each tick.

**Major components:**
1. `PathmindAddonEntrypoint` (interface, api package) — contract addon mods implement; called once at mod init
2. `NodeTypeRegistrar` (passed to each plugin) — mutable collector, sealed after all entrypoints run
3. `AddonNodeExecutor` (functional interface) — returns `CompletableFuture<NodeResult>` for async nodes
4. `AddonNodeSerializer` (interface) — reads/writes addon-specific state to JSON extra-fields map
5. `AddonNodeContext` (runtime context) — only api-package types in its method signatures
6. `ExecutionManager` (modified) — polls pending addon futures each tick; never blocks
7. `LuaNodeExecutor` (addon impl) — fresh Cobalt VM on a dedicated fixed-thread pool per execution
8. `LuaScriptEditorWidget` (addon UI) — `EditBoxWidget` + line-number gutter overlay

### Critical Pitfalls

1. **Addon compiled against impl classes** — once entrenched, extracting the API boundary is a multi-day refactor. Publish only the `com.pathmind.api` package to Maven from day one; addon must compile with zero impl classes on classpath.
2. **Cobalt/LuaJ blocking the main thread** — any synchronous `chunk.call()` on the game tick thread freezes the client. Always run on a worker thread with `CompletableFuture` + wall-clock thread-interrupt timeout. Not optional even with sandboxing deferred.
3. **`luajava` sandbox escape** — `JsePlatform.standardGlobals()` loads `luajava`, giving scripts full JVM access. Build `Globals` manually; verify `globals.get("luajava").isnil()` in a unit test.
4. **Fabric entrypoint ordering undefined** — addons may run before Pathmind's `onInitialize` completes. Use a queued-registration guard or a `PATHMIND_READY` custom Fabric event.
5. **`Globals` reuse across executions** — state leaks between runs. Create fresh `Globals` per execution; share pre-built read-only library tables via factory.
6. **mavenLocal stale remapped jar** — Loom caches by version coordinate, not content hash. Dev-loop script must increment build counter suffix and delete `.gradle/loom-cache/remapped_mods` before each addon build.
7. **Preset serialization without version field** — missing `_schema_version` makes schema migrations impossible. Include the field in the first serialization commit.

## Implications for Roadmap

### Phase 1: API Foundation
**Rationale:** Everything is blocked on a consumable API artifact. This phase also resolves the two most expensive pitfalls (impl-coupling and init-ordering) before any addon code exists.
**Delivers:** `com.pathmind.api` package with all entrypoint/registrar/executor/serializer/context interfaces; `AddonLoader` wired into `PathmindMod.onInitialize`; `NodeTypeRegistry` with string-keyed storage; `publishToMavenLocal` configured; dev-loop tooling documented.
**Addresses:** Addon entrypoint discovery, node registration API, separate API artifact, lifecycle ordering, registration validation, standalone capability.
**Avoids:** P1 (impl coupling), P3 (init ordering), P4 (API surface leaking internals), P13 (Multi-Release JAR remapping), P2 (mavenLocal staleness — establish dev loop here).
**Research flag:** Standard patterns — no additional research needed.

### Phase 2: Stub Addon (End-to-End Wiring Proof)
**Rationale:** Prove the full pipeline before adding VM complexity. Validates the API boundary is honest.
**Delivers:** Sibling repo with `fabric.mod.json` declaring `"pathmind"` entrypoint; no-op stub node visible in editor, executable, and serializable.
**Avoids:** P1 (compile check), P3 (init ordering under real conditions), P12 (`_schema_version` established in first serialization commit).
**Research flag:** Standard patterns.

### Phase 3: Async Execution Contract
**Rationale:** The async-sync bridging model is the highest-risk technical element. Establish with a trivial Lua payload before adding real bindings.
**Delivers:** `ExecutionManager` polling `CompletableFuture<NodeResult>`; `LuaNodeExecutor` running bare-minimum Cobalt VM on fixed-thread pool; wall-clock thread-interrupt timeout.
**Avoids:** P5 (main thread blocking), P6 (luajava — safe Globals construction established here), P7 (xpcall bypass — thread-interrupt-first), P10 (Globals reuse — per-execution pattern established).
**Research flag:** Needs careful implementation — Cobalt 0.7.3 thread-interrupt API needs verification against CC:Tweaked source before design is finalized.

### Phase 4: Pathmind Lua Library
**Rationale:** Variable read/write and one awaitable action are the minimum viable scripting surface. Proves main-thread dispatch under real conditions.
**Delivers:** `PathmindVariableContext`, `PathmindActionContext` with `moveTo`, argument size guards on all Lua API entry points.
**Avoids:** P8 (heap exhaustion — size guards), P9 (game state thread safety — main-thread dispatch established for all subsequent API functions).
**Research flag:** Standard patterns for dispatch; Baritone completion callback needs verification against existing navigator code.

### Phase 5: In-Node Script Editor UI
**Rationale:** With execution working, the UI is the remaining gap to a usable v1.
**Delivers:** `LuaScriptEditorWidget` with line-number gutter; script text persisted; error display in node body; Escape and Tab key routing corrected.
**Avoids:** P11 (Escape/Tab key routing — handle in first editor commit).
**Research flag:** Medium — `EditBoxWidget` keyboard shortcut behavior needs in-game verification before finalizing UX.

### Phase 6: Full Lua Library Surface + Autosuggestions
**Rationale:** Expand to the full game-state query surface and add autosuggestions once the API is stable enough to enumerate.
**Delivers:** Full `pathmind.*` binding surface (position, inventory, blocks); annotation-generated or static API name list for prefix-match autosuggestion popup.
**Research flag:** Standard patterns — follows Figura `@LuaWhitelist` pattern.

### Phase Ordering Rationale

- API artifact before everything: it is the compile-time prerequisite for the addon.
- Stub addon before Lua VM: separates wiring bugs from VM bugs cheaply.
- Async contract before bindings: failure modes must be isolated before adding binding complexity.
- Bindings before UI: VM must execute something meaningful before building an editor around it.
- UI after execution: decoupled concern; can be iterated independently.
- Surface expansion last: polish after core is validated against real usage.

### Research Flags

Needing deeper research during planning:
- **Phase 3 (Async Execution):** Cobalt 0.7.3 thread-interrupt and `LuaState` builder API — examine CC:Tweaked's `CobaltLuaMachine` source.
- **Phase 5 (Editor UI):** `EditBoxWidget` keyboard shortcut behavior needs in-game verification.

Standard patterns (skip research-phase):
- **Phase 1, 2, 4, 6:** Fabric entrypoints, `maven-publish`, main-thread dispatch, Figura annotation pattern — all well-documented with HIGH-confidence sources.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Cobalt confirmed from Maven + CC:Tweaked production; Fabric entrypoint and maven-publish have official docs; EditBoxWidget confirmed in Yarn API docs |
| Features | HIGH (API) / MEDIUM (script UX) | Addon API features from REI/JEI/Fabric API analysis; script node UX specifics need in-game UAT |
| Architecture | HIGH | Confirmed against REI source, Fabric Loader docs, JEI wiki, AE2 addon guide, CC:Tweaked internals; async pattern validated by tarasyk.ca |
| Pitfalls | HIGH | Cross-referenced LuaJ docs, Fabric Loader issues, Architectury Loom issues, OpenComputers post-mortems |

**Overall confidence:** HIGH

### Gaps to Address

- **Cobalt 0.7.3 exact API surface:** README warns about API stability outside CC:T; examine CC:Tweaked's `CobaltLuaMachine` during Phase 3 planning before committing to the thread-interrupt design.
- **`EditBoxWidget` keyboard shortcuts:** "Basic keyboard shortcuts" is vague; in-game verification needed before Phase 5 finalizes the editor UX.
- **ExecutionManager refactor scope:** The monolith size means the exact surface needed to expose `AddonNodeContext` cleanly may be larger than expected; assess during Phase 1 planning.
- **Baritone completion callback:** Exact event/callback API Baritone exposes for navigation completion needs verification against existing Pathmind navigator code before Phase 4 design.

## Sources

### Primary (HIGH confidence)
- Fabric Wiki — Entrypoints: https://wiki.fabricmc.net/documentation:entrypoint
- FabricLoader Javadoc — `getEntrypointContainers`: https://maven.fabricmc.net/docs/fabric-loader-0.14.22/net/fabricmc/loader/api/FabricLoader.html
- Cobalt Maven — org.squiddev:Cobalt:0.7.3: https://mvnrepository.com/artifact/org.squiddev/Cobalt/0.7.3
- EditBoxWidget 1.21.4 Yarn API: https://maven.fabricmc.net/docs/yarn-1.21.4-rc3+build.3/net/minecraft/client/gui/widget/EditBoxWidget.html
- Modrinth Maven: https://support.modrinth.com/en/articles/8801191-modrinth-maven
- modmuss50/mod-publish-plugin: https://github.com/modmuss50/mod-publish-plugin
- Fabric API CONTRIBUTING.md: https://github.com/FabricMC/fabric-api/blob/1.21/CONTRIBUTING.md
- LuaJ sandbox example: https://github.com/gelldur/luaj/blob/master/examples/jse/SampleSandboxed.java

### Secondary (MEDIUM confidence)
- Cobalt GitHub: https://github.com/cc-tweaked/Cobalt
- REI GitHub: https://github.com/shedaniel/RoughlyEnoughItems
- JEI Creating Plugins (DeepWiki): https://deepwiki.com/mezz/JustEnoughItems/3.2-creating-jei-plugins
- KubeJS Plugin Architecture (DeepWiki): https://deepwiki.com/KubeJS-Mods/KubeJS
- Figura Lua Scripting System (DeepWiki): https://deepwiki.com/FiguraMC/Figura/4-lua-scripting-system
- LuaJ + Minecraft async-sync bridging (tarasyk.ca): https://tarasyk.ca/2019/10/28/tasky-blog-01.html
- AE2 Addon API guide: https://guide.appliedenergistics.org/1.19.2/api

### Tertiary (known issues / security advisories)
- OpenComputers xpcall DoS advisory GHSA-54j4-xpgj-cq4g — timeout bypass via nested error handlers
- OpenComputers heap exhaustion issue #1774 — LuaTable-to-Java conversion OOM
- Fabric Loom remapping stale cache issue #1290 — mavenLocal SNAPSHOT staleness
- Architectury Loom remapping issue #68 — Multi-Release JAR META-INF/versions stripped
- Fabric Loader entrypoint ordering issue #459 — `depends` does not control init order

---
*Research completed: 2026-06-12*
*Ready for roadmap: yes*
