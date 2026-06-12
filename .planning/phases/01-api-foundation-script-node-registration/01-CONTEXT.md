# Phase 1: API Foundation + Script Node Registration - Context

**Gathered:** 2026-06-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Pathmind exposes a consumable addon API — entrypoint discovery, typed node registration, addon-agnostic JSON persistence, async executor contract, minimal node-body UI hook — published as a Maven artifact (local Maven for the dev loop). The sibling addon repo (`pathmind-lua`) ships a real Script node: palette-visible, placeable, persistable with `_schema_version`, executing as a graceful no-op pass-through. Both mods load cleanly together and Pathmind remains fully functional standalone across MC 1.21–1.21.11. No Lua execution (Phase 2), no editor widget (Phase 3).

Requirements: API-01..API-10, LUA-01, LUA-05.

</domain>

<decisions>
## Implementation Decisions

### Addon identity & ownership
- **D-01:** Addon mod is **"Pathmind Lua"** — mod ID `pathmind-lua`, jar `pathmind-lua-x.y.z.jar`.
- **D-02:** Sibling repo lives at `C:\Users\Trynda\Desktop\Dev\sidequests\pathmind-lua` (true sibling of the pathmind repo).
- **D-03:** Ownership split: **the addon API is an upstream contribution** — it stays under Pathmind's original Maven/Java identity (`com.pathmind`). The user maintains this Pathmind fork with the original author's permission and was tasked with helping addon support. **The Lua addon is the user's own project** (greenlit, user-controlled): namespace `com.mrmysterium` (e.g., `com.mrmysterium.pathmindlua`), author metadata `mr_mysterium`.
- **D-04:** Entrypoint key in `fabric.mod.json` is `pathmind`.

### Palette presentation
- **D-05:** Addons declare their own palette categories via the registration API — Pathmind Lua registers a scripting category (e.g., "Scripting"/"Lua"). No generic "Addons" bucket, no slotting into built-in categories.
- **D-06:** Phase 1 node body shows a **read-only script preview** (first lines of stored script text / default placeholder script) — proves persistence visibly without claiming editor functionality.
- **D-07:** Addon-provided nodes carry a **subtle visual provenance marker** (badge or accent color indicating the providing addon).

### Addon failure UX
- **D-08:** Failed registration (throw, duplicate node ID, null executor, etc.) → **whole addon disabled, game keeps running**. Full error logged with addon mod ID; in-game warning surfaced via the existing error-notification overlay (NodeErrorNotificationOverlay infrastructure) when the editor opens.
- **D-09:** Presets containing addon nodes whose addon is missing/failed load those nodes as **inert grayed-out placeholders that preserve their JSON data** — preset round-trips losslessly and works again when the addon returns.

### API versioning
- **D-10:** The addon API has its **own independent semver**, decoupled from Pathmind's mod version. Starts at 0.x during co-evolution; 1.0 marks the "stable API" milestone (project Definition of Done).
- **D-11:** Compatibility enforcement is two-layer: addon declares an API version range in `fabric.mod.json` (Fabric loader blocks hard mismatches) **plus** a runtime check at registration — incompatible addons are disabled via the standard failure UX (D-08).

### Claude's Discretion
- Exact Maven coordinates/artifact naming for the API (`com.pathmind:pathmind-api` vs per-MC-version variants) — decide during planning based on how the Architectury multi-version build emits artifacts.
- Exact addon Java package layout under `com.mrmysterium`.
- API package boundary mechanics (research recommends `com.pathmind.api` package inside `common` rather than a separate Gradle module for v1).
- NodeType enum integration strategy (research recommends `ADDON` sentinel + string-keyed `NodeTypeRegistry`).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Research (this milestone)
- `.planning/research/SUMMARY.md` — synthesized stack/architecture/pitfalls decisions; phase-mapped pitfall avoidance
- `.planning/research/ARCHITECTURE.md` — entrypoint/registrar/executor component design, NodeType enum blocker, build order
- `.planning/research/PITFALLS.md` — init-ordering trap, impl-coupling, mavenLocal staleness (all Phase 1 gates)
- `.planning/research/STACK.md` — entrypoint mechanism, maven-publish setup, API/impl boundary recommendation

### Codebase maps
- `.planning/codebase/ARCHITECTURE.md` — existing layer structure, ExecutionManager/Node/NodeGraph responsibilities
- `.planning/codebase/STRUCTURE.md` — module layout, key files (NodeType.java, NodeBehaviorDefinitionRegistry, NodeGraphData, NodeGraphPersistence)
- `.planning/codebase/CONVENTIONS.md` — code style to match for upstream-bound API code

### Project
- `.planning/REQUIREMENTS.md` — API-01..10, LUA-01, LUA-05 definitions
- `.planning/PROJECT.md` — co-evolution strategy, ownership context, constraints

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `NodeBehaviorDefinitionRegistry` / `NodeBehaviorDefinition`: existing node metadata registry — the addon registry wraps or parallels this rather than replacing it
- `NodeGraphData` + `NodeGraphPersistence` (common/src/main/java/com/pathmind/data/): JSON serialization path where the opaque addon blob + `_schema_version` lands
- `ui/overlay/NodeErrorNotificationOverlay`: reuse for addon-failure warnings (D-08)
- `ui/theme/`: theming infrastructure for the provenance badge/accent (D-07)
- `BackgroundStartRunner` (execution/): proven main-thread-dispatch idiom, relevant to the async executor contract design

### Established Patterns
- `NodeType` is a Java enum (99 types) — cannot be extended at runtime; addon nodes need the ADDON-sentinel + string-ID pattern (research)
- Immediate-mode UI rendering in `NodeGraph.java` (3000+ lines) — the minimal node-body widget hook (API-07) must integrate with per-frame rendering; keep the Phase 1 hook narrow (read-only preview only needs text rendering)
- Architectury `common`/`fabric`/`neoforge` split with `src/compat/` version layers — API surface must stay version-agnostic (API-09)

### Integration Points
- `fabric/src/main/java/com/pathmind/PathmindMod.java` (and NeoForge twin): where `AddonLoader` / entrypoint discovery wires into `onInitialize` (Fabric-only discovery is fine for v1; NeoForge addon loading is v2)
- `common/src/main/java/com/pathmind/nodes/`: NodeType, Node, registries — primary refactor target
- Palette/category data feeding `NodeGraph` sidebar — extension point for addon-declared categories (D-05)

</code_context>

<specifics>
## Specific Ideas

- Model the registration lifecycle on REI/JEI registrar objects passed to entrypoints (user explicitly wants REI/Fabric API patterns mirrored).
- The fork relationship matters for code style: API code is destined for upstream Pathmind, so match existing Pathmind conventions closely; the addon repo is the user's own and can set its own conventions.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. (v2 items already tracked in REQUIREMENTS.md: full UI extension points, lifecycle hooks, NeoForge addon loading, Modrinth Maven publishing.)

</deferred>

---

*Phase: 1-API Foundation + Script Node Registration*
*Context gathered: 2026-06-12*
