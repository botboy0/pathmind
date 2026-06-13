# Phase 2: Lua VM + Core Bindings - Context

**Gathered:** 2026-06-13
**Status:** Ready for planning

<domain>
## Phase Boundary

The Script node executes real Lua. Cobalt 0.7.3 (CC:Tweaked fork, shadow-relocated into the addon jar) runs on a worker thread with a fresh, manually-built globals object per execution. The async-sync bridging model lets the worker thread block on awaitable Pathmind actions while the game tick thread keeps ticking; ExecutionManager observes completion through the Phase 1 async executor contract (`AddonNodeExecutor` returning a `CompletableFuture<NodeResult>` polled per tick — API-06). The full `pathmind.*` binding surface is exposed: node-tree variables (read/write), movement (awaitable `moveTo`), game-state queries (position/inventory/block), and error surfacing. A wall-clock timeout interrupts runaway scripts without freezing the game. Lua errors surface to the user with message + line number and stop the graph at the Script node.

This phase extends `com.pathmind.api` with a runtime-bridge surface (co-evolution — the addon's concrete binding needs drive the new API). The addon (sibling repo `pathmind-lua`) implements the Cobalt executor and the Lua bindings against that API.

Requirements: LUA-02, LUA-03, LUA-04, BIND-01, BIND-02, BIND-03, BIND-04.

Out of scope: in-node code editor, line-number gutter, autosuggestions, co-located in-node error display (all Phase 3).

</domain>

<decisions>
## Implementation Decisions

### Runtime Bridge API Shape (added to `com.pathmind.api` — goes upstream to Pathmind)
- **Separate `PathmindRuntime` services interface** reachable from `AddonNodeContext`, rather than piling methods onto the context. `AddonNodeContext` stays a pure data holder (currently `scriptText` + `addonTypeId`); the runtime services are a distinct interface — cleaner upstream API surface.
- **Variable access:** `Object getVariable(String)` / `setVariable(String, Object)` on the services interface, backed by ExecutionManager's global runtime variables.
- **Action invocation:** a **typed, awaitable** method for the v1 action surface — `CompletableFuture<?> moveTo(x, y, z)` — wrapping Pathmind's existing PathmindNavigator/Baritone completion path. (Generic `invokeAction(name, args)` dispatch is deferred to v2.)
- **Game state routed through the Pathmind API** (NOT hand-rolled in the addon): position/inventory/block accessors live on the `PathmindRuntime` services interface with main-thread-safe dispatch handled Pathmind-side. Rationale (user): hand-rolling MC reads in the addon duplicates dispatch logic and couples the addon to MC version internals — it would bite us when the API broadens past 1.21.4. The API is the single version-stable contract.

### Lua Binding Surface — names, return shapes, marshaling
- **Single global `pathmind` table** holds all bindings (`pathmind.getVar`, `pathmind.setVar`, `pathmind.moveTo`, `pathmind.getPosition`, `pathmind.getInventory`, `pathmind.getBlock`) — matches success criteria verbatim.
- **`getPosition()` → Lua table `{x=, y=, z=}`** (doubles) — extensible and idiomatic.
- **`getInventory()` → 1-indexed Lua array of `{slot=, item="ns:id", count=}`**, non-empty slots only.
- **`getBlock(x,y,z)` → namespaced block id string** (e.g. `"minecraft:stone"`), `nil` if the chunk is unloaded.
- **Variable marshaling:** number / string / boolean only. Tables and userdata are not marshaled in v1 — `setVar` with an unsupported type raises a clear Lua error.

### Timeout & Runaway Protection (LUA-04)
- **Timeout clock PAUSES while a script awaits an action** (e.g. `moveTo`). It measures continuous Lua *compute* time, not navigation wait — otherwise any navigating script would be killed mid-path.
- **Default budget ~5 s** of continuous Lua execution.
- **Global setting** (config-level) in v1 — no per-node override.
- **Interrupt mechanism:** Cobalt debug-hook / instruction-count abort, **plus `Thread.interrupt()` as a safety net** (per LUA-04 wording).

### Error Surfacing & Failure Semantics (BIND-04)
- **v1 surfacing channel: chat message.** Keep the broader error-surfacing architecture (the existing NodeErrorNotificationOverlay path stays available), but **do NOT persist `lastError` on the node in Phase 2** — node-level persistence and co-located in-node display are Phase 3 territory (EDIT-03).
- **Error content:** Lua message + line number shown to the user; full traceback goes to the log (System.err).
- **On script error: stop the graph at the Script node** (success criterion 5), marked errored — never silently continue.
- **Binding-call failures throw a Lua error** with a clear message (e.g. `moveTo` when Baritone is absent); `getBlock` for an out-of-range/unloaded position returns `nil` rather than erroring.

### Claude's Discretion
- Exact name/package of the runtime services interface (`PathmindRuntime` is a working name) and how `AddonNodeContext` references it — finalize during planning to match Pathmind API conventions, since this is upstream-bound code.
- Worker-thread pooling strategy (dedicated thread per execution vs small cached pool) — must support concurrent Script-node executions each with its own fresh globals.
- Exact main-thread dispatch idiom (e.g. `MinecraftClient.execute` vs the BackgroundStartRunner pattern) for the Pathmind-side game-state accessors.
- Exact Cobalt 0.7.3 interrupt/debug-hook API — flagged in STATE.md as needing verification against CC:Tweaked source during planning research.
- Exact PathmindNavigator/Baritone completion-callback API for awaitable `moveTo` — flagged in STATE.md as needing verification during planning research.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 1 API contract (the executor plugs into this)
- `common/src/main/java/com/pathmind/api/addon/AddonNodeExecutor.java` — async execution contract (`CompletableFuture<NodeResult> execute(AddonNodeContext)`; must never block the game thread)
- `common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java` — current minimal context (`scriptText`, `addonTypeId`); the new `PathmindRuntime` surface attaches here
- `common/src/main/java/com/pathmind/api/addon/` — `NodeResult`, `AddonNodeSerializer`, `AddonNodeBodyRenderer`, `PathmindAddonEntrypoint`

### Project
- `.planning/REQUIREMENTS.md` — LUA-02/03/04, BIND-01..04 definitions
- `.planning/PROJECT.md` — co-evolution strategy, ownership (D-03: addon under `com.mrmysterium`, API under `com.pathmind`), Fabric/MC 1.21.4-only addon target
- `.planning/STATE.md` — logged decisions (Cobalt 0.7.3 shadow-relocate; synchronized LinkedHashMap for ordering) + Phase 2 blockers to verify

### Codebase maps
- `.planning/codebase/ARCHITECTURE.md` — ExecutionManager (global runtime variables, async background executor), PathmindNavigator, BackgroundStartRunner, NodeErrorNotificationOverlay
- `.planning/codebase/CONVENTIONS.md` — code style to match for upstream-bound API code

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AddonNodeExecutor` / `AddonNodeContext` / `NodeResult`** (Phase 1, `com.pathmind.api.addon`): the contract the Lua executor implements — return a `CompletableFuture<NodeResult>` immediately, complete it off-thread when the script finishes.
- **ExecutionManager global runtime variables**: existing per-run variable store that `getVar`/`setVar` map onto (BIND-01). Insertion order preserved via `Collections.synchronizedMap(new LinkedHashMap<>())` per NEW-CR-01.
- **PathmindNavigator** (`execution/`): existing Baritone-backed movement with completion detection — wrap for the awaitable `moveTo` (BIND-02). `BaritoneApiProxy` / `BaritoneDependencyChecker` gate Baritone availability.
- **BackgroundStartRunner** (`execution/`): proven main-thread-dispatch idiom — reference for the Pathmind-side game-state accessors (BIND-03).
- **NodeErrorNotificationOverlay** (`ui/overlay/`): existing error-surface infra; keep available for the error path (chat is the v1 primary channel).

### Established Patterns
- **Async executor contract (API-06):** executors never block the game thread; ExecutionManager polls the returned `CompletableFuture` per tick and advances the tree on completion. The Lua worker thread runs/blocks off the game thread; main-thread-only reads are marshaled and the worker blocks for the result.
- **Main-thread dispatch:** game state must be read on the client thread (`MinecraftClient.execute` / BackgroundStartRunner idiom), with the result handed back to the worker.
- **Architectury common/fabric split + `src/compat/` version layers** in Pathmind; the new API surface must stay version-agnostic (API-09) — another reason game state is brokered by Pathmind, not the addon.

### Integration Points
- **`com.pathmind.api.addon`**: add the `PathmindRuntime` services interface (variables + awaitable `moveTo` + game-state accessors) and wire it to `AddonNodeContext`. Upstream-bound — match Pathmind conventions.
- **ExecutionManager**: expose global-variable get/set and the main-thread dispatch the runtime services need; surface script errors (stop-at-node) and route the message to chat.
- **PathmindNavigator**: expose an awaitable completion handle for `moveTo`.
- **Addon repo** `C:\Users\Trynda\Desktop\Dev\sidequests\pathmind-lua`: implement the Cobalt VM lifecycle (fresh manual globals, no `luajava`/`standardGlobals` — LUA-03), the `pathmind.*` binding table, the worker-thread + timeout machinery, and the `AddonNodeExecutor` implementation.

</code_context>

<specifics>
## Specific Ideas

- **Cobalt 0.7.3** (CC:Tweaked fork) is the VM (locked at roadmap creation), shadow-relocated into the addon jar.
- **Fresh manual globals per execution** — explicitly no `luajava`, no `standardGlobals()` (LUA-03). Build only the safe subset plus the `pathmind` table.
- **Error surfacing is chat-based in v1.** Keep the surfacing architecture general enough that Phase 3 can add co-located in-node display (EDIT-03) without rework, but no node-level error persistence yet.
- **Concurrent Script nodes** must each get their own fresh globals + worker — no shared mutable Lua state across executions.

</specifics>

<deferred>
## Deferred Ideas

- **Node-level error persistence + co-located in-node error display** → Phase 3 (EDIT-03). v1 surfaces to chat only.
- **Generic `invokeAction(name, args)` dispatch** → v2. v1 uses a typed awaitable `moveTo`.
- **Per-node timeout override** → v2. v1 uses a single global timeout setting.
- **Lua table / userdata variable marshaling** → v2. v1 marshals scalars only.
- **Robust sandboxing / instruction budget beyond the wall-clock timeout** → v2 (already tracked: LUA-V2-01).

</deferred>

---

*Phase: 2-Lua VM + Core Bindings*
*Context gathered: 2026-06-13*
