# Requirements: Pathmind Addon API + Lua Scripting Addon

**Defined:** 2026-06-12
**Core Value:** A third party can drop `pathmind.jar` + `pathmind-lua-addon.jar` into a mods folder and get a working Lua script node — proving the addon API is real, stable, and consumable by external developers.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Addon API (Pathmind refactor)

- [x] **API-01**: Addon mod can register against Pathmind by declaring a `pathmind` entrypoint in its `fabric.mod.json` (discovered via `FabricLoader.getEntrypointContainers`)
- [x] **API-02**: Addon can register custom node types (definition, executor, serializer) through a typed registrar object passed at the registration lifecycle callback
- [x] **API-03**: Registration is validated at load time with informative errors that name the offending addon mod
- [x] **API-04**: Lifecycle ordering is guaranteed and addon registration is safe regardless of Fabric entrypoint init order (deferred-registration guard or ready event)
- [x] **API-05**: Addon nodes persist addon-declared data inside Pathmind's JSON presets as an opaque, schema-versioned blob — Pathmind validates and integrates without knowing the schema
- [x] **API-06**: Addon node executors run asynchronously (`CompletableFuture` polled per tick by ExecutionManager) and never block the game thread
- [x] **API-07**: Addon nodes can render custom content in the node body via a minimal UI widget hook
- [x] **API-08**: A separate API artifact (only `com.pathmind.api` types) is published to local Maven and the sibling addon repo compiles against it with zero impl classes on its classpath
- [x] **API-09**: Pathmind runs completely unchanged when no addons are installed, across its existing MC 1.21–1.21.11 range
- [x] **API-10**: The addon API is documented (javadoc + getting-started guide) well enough for a third party to build a different addon

### Lua Addon — Node & Execution

- [x] **LUA-01**: User can grab a Script node from the editor palette and place it like any other node (node provided by the separate addon jar)
- [x] **LUA-02**: When the Script node executes, its Lua script runs on a worker thread and the node tree continues only after the script finishes
- [x] **LUA-03**: Each execution gets a fresh, sandboxed Lua environment (manually-built globals — no `luajava`, no `standardGlobals()`)
- [x] **LUA-04**: A runaway script cannot hang the game — wall-clock timeout with thread interrupt as a safety net
- [x] **LUA-05**: Script text persists with the node through preset save/load cycles (with `_schema_version` field)

### Lua Bindings

- [ ] **BIND-01**: Script can read and write node-tree variables shared with other Pathmind nodes (`pathmind.getVar` / `pathmind.setVar`)
- [ ] **BIND-02**: Script can invoke Pathmind actions (movement/Baritone, interaction) and block until the action completes
- [ ] **BIND-03**: Script can query game state Pathmind already exposes (player position, inventory, blocks) with main-thread-safe dispatch
- [x] **BIND-04**: Script errors surface to the user with message and line number (never silently swallowed)

### In-Node Editor

- [ ] **EDIT-01**: Script node body contains a functional plain-text multiline editor (cursor movement, selection, scrolling, copy/paste)
- [ ] **EDIT-02**: Editor shows line numbers in a gutter
- [ ] **EDIT-03**: Last-run error (message + line) is displayed co-located with the node
- [ ] **EDIT-04**: Editor offers simple prefix-match autosuggestions for the `pathmind.*` Lua API (list generated from binding annotations or a static registry)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Addon API

- **API-V2-01**: Full UI extension points (custom panels, categories, overlays)
- **API-V2-02**: Execution lifecycle hooks (pre/post graph run) for addons
- **API-V2-03**: NeoForge addon loading support
- **API-V2-04**: Public Maven distribution (Modrinth Maven) of the API artifact

### Lua Addon

- **LUA-V2-01**: Robust sandboxing and resource limits (memory, instruction budget) beyond the v1 timeout safety net
- **LUA-V2-02**: Syntax highlighting in the editor
- **LUA-V2-03**: Script hot-reload without graph restart
- **LUA-V2-04**: Addon support for MC versions beyond 1.21.4

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Script-driven control flow (Lua return steers node-tree branching) | Pathmind's existing branch/condition nodes cover it; Lua sets a variable a Condition node reads. Adding this makes the visual graph lie about actual flow |
| External LSP / VSCode editor integration | Separate long-running process and protocol — a substantial separate project; in-game autosuggestions cover v1 |
| KubeJS-style free-floating lifecycle scripts | The graph IS the script lifecycle; free-floating scripts break the visual programming model |
| Big-bang upfront refactor of Pathmind internals | Refactor is driven by the addon's concrete needs (co-evolution strategy) |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| API-01 | Phase 1 | Complete |
| API-02 | Phase 1 | Complete |
| API-03 | Phase 1 | Complete |
| API-04 | Phase 1 | Complete |
| API-05 | Phase 1 | Complete |
| API-06 | Phase 1 | Complete |
| API-07 | Phase 1 | Complete |
| API-08 | Phase 1 | Complete |
| API-09 | Phase 1 | Complete |
| API-10 | Phase 1 | Complete |
| LUA-01 | Phase 1 | Complete |
| LUA-05 | Phase 1 | Complete |
| LUA-02 | Phase 2 | Complete |
| LUA-03 | Phase 2 | Complete |
| LUA-04 | Phase 2 | Complete |
| BIND-01 | Phase 2 | Pending |
| BIND-02 | Phase 2 | Pending |
| BIND-03 | Phase 2 | Pending |
| BIND-04 | Phase 2 | Complete |
| EDIT-01 | Phase 3 | Pending |
| EDIT-02 | Phase 3 | Pending |
| EDIT-03 | Phase 3 | Pending |
| EDIT-04 | Phase 3 | Pending |

**Coverage:**

- v1 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0 ✓

---
*Requirements defined: 2026-06-12*
*Last updated: 2026-06-12 — traceability filled by roadmap creation*
