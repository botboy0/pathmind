---
slug: /
sidebar_position: 1
sidebar_label: Overview
---

# Pathmind Addon API + Lua Scripting Addon

## What This Is

Two co-developed pieces of work: (1) refactoring Pathmind — a visual node-based Minecraft automation editor — to expose a proper addon API, and (2) building the first addon against that API: a Lua Script node that ships a Java-based Lua VM with access to Pathmind's actions, states, and node-tree variables. The addon lives in a sibling repo and is a pure addon mod: Pathmind works with or without it, but the addon requires Pathmind — the same relationship Fabric API or Roughly Enough Items has with mods that build on them.

## Core Value

A third party can drop `pathmind.jar` + `pathmind-lua-addon.jar` into a mods folder and get a working Lua script node — proving the addon API is real, stable, and consumable by external developers.

## The User Story (Lua Addon)

> I want to open the Pathmind visual scripting editor, grab a Script node the way I'd grab any other node. It has a small but functional code editor inside. When the node executes, it runs the Lua script (timeouts/bounds/limitations may apply, but those are a problem for later), and once the script finishes the node tree continues. I should be able to call variables defined by other nodes just as any other node would.

## Requirements

### Validated

<!-- Existing Pathmind capabilities, inferred from codebase map -->

- ✓ Visual node-graph editor with immediate-mode rendering (NodeGraph) — existing
- ✓ Graph execution engine with lifecycle/state management (ExecutionManager) — existing
- ✓ Node model with parameter evaluation and Minecraft action dispatch — existing
- ✓ Graph validation (structure, cycles, type checking) — existing
- ✓ JSON-based preset/settings persistence (PresetManager, SettingsManager, NodeGraphData) — existing
- ✓ Cross-platform builds (Fabric + NeoForge via Architectury) across MC 1.21–1.21.11 — existing
- ✓ Baritone integration for pathfinding/movement actions — existing
- ✓ Supabase-backed marketplace for preset sharing — existing
- ✓ Loose mixin scaffolding (starting point for addon attachment) — existing

### Active

**Addon API (Pathmind refactor):**

- [ ] Addon discovery/entrypoint system so addon mods can register against Pathmind (REI/Fabric API entrypoint pattern)
- [ ] Node registration API: addons register custom node types with parameters and execution behavior
- [ ] UI extension points: addons add node categories, panels, and editor widgets
- [ ] Execution hooks: addons participate in graph execution lifecycle
- [ ] Addon-agnostic JSON persistence: addons declare what they need; Pathmind validates and integrates it
- [ ] Pathmind runs unchanged when no addons are installed
- [ ] API documented and clean enough for a third party to build a different addon

**Lua Scripting Addon (sibling repo):**

- [ ] Standalone addon mod jar that requires Pathmind and registers via the addon API
- [ ] Script node placeable in the editor like any other node
- [ ] Embedded Java-based Lua VM executes the node's script during graph execution; tree continues when the script finishes
- [ ] Integrated Lua library exposing Pathmind: read/write node-tree variables, invoke actions (and await completion), query game state (position, inventory, blocks)
- [ ] In-node code editor: functional plain-text editing with line numbers
- [ ] Simplest reliable form of LSP-style autosuggestions for the Pathmind Lua API

### Out of Scope

- Script-driven control flow signals (branch/steer the node tree from Lua return values) — Pathmind already has branching/execution nodes; superfluous and a needless complication at this point
- Script sandboxing/timeouts/resource bounds — acknowledged as needed eventually, explicitly deferred ("a problem for later")
- NeoForge support for the addon — v1 targets Fabric only
- MC versions beyond 1.21.4 for the addon — single dev version until the API stabilizes
- Blind upfront refactor of Pathmind — refactor is driven by the addon's concrete needs and researched patterns, not speculation
- Syntax highlighting / IDE-grade editor — v1 is plain text + line numbers + basic autosuggestions

## Context

- Pathmind codebase is mapped in `.planning/codebase/` (ARCHITECTURE, STACK, STRUCTURE, CONVENTIONS, INTEGRATIONS, TESTING, CONCERNS — refreshed 2026-06-12).
- Architecture is multi-layered (platform entry → UI → execution → data → MC bridge) with Architectury for cross-platform abstraction. Mixin scaffolding exists but is loose; a significant architecture shift is needed to make internals accessible for addon registration.
- **Strategy: co-evolution, not big-bang refactor.** The addon and Pathmind's addon-support refactor are built up at the same time whenever reasonable — the addon's real needs drive which internals get API surface.
- **Deep research is a first-class requirement.** Before/while designing the API, extract and learn patterns from established open-source addon-supported mods: Roughly Enough Items (plugin/entrypoint + registry model) and Fabric API (modular API + entrypoint model). Mixins may bridge gaps temporarily, but the end-state is a formal API.
- Lua VM choice (e.g., LuaJ or alternatives) is an open research question — must run on the JVM inside a Fabric mod on MC 1.21.4 / Java 21.
- **Workflow note:** YOLO mode, but the user wants human-driven UAT (`/gsd-verify-work`) after any slice that majorly affects in-game behavior — pause for manual in-game testing at those checkpoints.

## Constraints

- **Tech stack**: Java 21, Gradle 8.x, Fabric Loader, Architectury Loom — addon must match Pathmind's build ecosystem
- **Target platform**: Fabric on Minecraft 1.21.4 for addon v1 — single version to keep API iteration fast
- **Repo layout**: Addon is a sibling repo (true external consumer) — proves the API honestly; Pathmind must publish/expose a consumable API artifact
- **Compatibility**: Pathmind must remain fully functional standalone (no addon installed) across its existing 1.21–1.21.11 range
- **Dependency direction**: Addon depends on Pathmind, never the reverse

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Pure addon model (Fabric API / REI style) | Two separate jars; Pathmind standalone-capable, addon requires Pathmind | — Pending |
| Sibling repo for addon | Forces honest API boundaries; addon is a true external consumer | — Pending |
| Co-evolve API + addon | Addon's concrete needs drive refactor scope; avoids speculative big-bang refactor | — Pending |
| Pattern-mine REI + Fabric API first | Don't invent addon architecture blind; deep research extracts proven registry/entrypoint patterns | — Pending |
| Fabric + MC 1.21.4 only for addon v1 | Single target keeps API iteration fast; broaden after stabilization | — Pending |
| Lua as the scripting language | User-facing scripting power with an embeddable JVM VM; exact VM library chosen via research | — Pending |
| No script-driven control flow in v1 | Pathmind's existing branching nodes cover it; avoids needless complication | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-12 after initialization*
