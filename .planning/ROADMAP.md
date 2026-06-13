# Roadmap: Pathmind Addon API + Lua Scripting Addon

## Overview

Three phases co-evolve Pathmind and its first external addon. Phase 1 builds the addon API and ships the Script node as a registerable, persistent, palette-visible node (no Lua execution yet — but real: it saves, loads, and passes through graph execution gracefully). Phase 2 wires the Cobalt VM, establishes the async-sync bridging model, and exposes the full Pathmind Lua binding surface. Phase 3 completes the in-game editor UX with a functional multiline editor, line numbers, inline error display, and autosuggestions. Each phase leaves both repos in a working, loadable state.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: API Foundation + Script Node Registration** - Publish the Pathmind addon API artifact, wire addon discovery into PathmindMod, and ship the Script node as a registered, persistent, palette-visible node in the sibling addon repo (gap closure in progress — verification found 3 BLOCKERs) (completed 2026-06-13)
- [ ] **Phase 2: Lua VM + Core Bindings** - Wire Cobalt VM on a worker thread with per-execution fresh globals, establish the async-sync bridging model, and expose the full Pathmind Lua binding surface (variables, actions, game state, error reporting)
- [ ] **Phase 3: Script Node Editor + Autosuggestions** - Complete the in-game editor UX: functional multiline editor with line numbers, inline error display co-located with the node, and prefix-match autosuggestions for the pathmind.* API

## Phase Details

### Phase 1: API Foundation + Script Node Registration

**Goal**: The Pathmind addon API is published as a consumable Maven artifact, addon discovery is wired into mod init, and the sibling addon repo ships a Script node that is palette-visible, placeable, and persistable — both mods load cleanly and Pathmind works standalone without the addon jar
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: API-01, API-02, API-03, API-04, API-05, API-06, API-07, API-08, API-09, API-10, LUA-01, LUA-05
**Success Criteria** (what must be TRUE):

  1. A developer can declare a `"pathmind"` entrypoint in `fabric.mod.json` and have their `PathmindAddonEntrypoint.register(registrar)` called at mod init with informative load-time errors if registration is malformed
  2. The `com.pathmind.api` Maven artifact can be added to the sibling addon repo's `build.gradle` via `modCompileOnly` with zero impl classes on the classpath — addon compiles cleanly against only the API jar
  3. User can drag a Script node from the editor palette, connect it in a graph, run the graph, and the Script node passes through execution without error (no Lua yet — graceful no-op completion)
  4. A saved preset containing a Script node round-trips through save/load with script text and `_schema_version` intact
  5. Launching Minecraft without the addon jar installed leaves Pathmind fully functional across its existing 1.21–1.21.11 range with no errors or missing behavior

**Plans**: 14 plans (3 + 3 code-review gap-closure + 4 UAT gap-closure + 3 re-verification round-3 gap-closure + 1 UAT round-5 editor-render gap-closure)
**Wave 1**

- [x] 01-01-PLAN.md — API contract package (com.pathmind.api.addon), NodeTypeRegistry singleton, AddonLoader entrypoint discovery wired into PathmindMod, registration round-trip/failure unit tests [Wave 1]

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md — NodeType.ADDON + async execution branch, opaque schema-versioned persistence (save/load + missing-addon placeholder), sidebar palette population + drag-to-canvas, editor-open failure surfacing [Wave 2]
- [x] 01-03-PLAN.md — maven-publish to mavenLocal, pathmind-lua sibling repo scaffold, Lua Script node impl (no-op executor + serializer + preview renderer), getting-started doc, end-of-phase in-game verification [Wave 2]

**Gap closure** *(verification found 3 BLOCKERs — these plans close them)*

**Wave 1 (gaps)**

- [x] 01-04-PLAN.md — shared AddonNodeDataCopy helper; wire ADDON fields through applyLoadedData, clipboard, and createGraphSnapshot (CR-02 + CR-03) [Wave 1]
- [x] 01-05-PLAN.md — render addon categories in the sidebar + set hoveredAddonDefinition in the hit-test so the Script node is placeable from the palette (CR-01) [Wave 1]

**Wave 2 (gaps)** *(blocked on 01-04)*

- [x] 01-06-PLAN.md — ADDON conversion round-trip regression test (createGraphSnapshot + convertToNodes); fix WR-08 doc Fabric API version + WR-10 test scaffolding [Wave 2]

**UAT gap closure (round 2)** *(in-game UAT found GAP-1..GAP-5 — these plans close them; all parallel, disjoint files)*

**Wave 1 (UAT gaps)**

- [x] 01-07-PLAN.md — GAP-1: addon-aware scroll metrics + wrap-aware addon content height + addon-pass scrollbar so overflowing addon categories scroll (WR-03/WR-04) [Wave 1]
- [x] 01-08-PLAN.md — GAP-2 + GAP-3: ADDON node display name from the registry + default-field (script) seeding in the addon constructor so placed nodes are fully formed at drop time [Wave 1]
- [x] 01-09-PLAN.md — GAP-4 + GAP-5: scissor-clip + sidebar-overlap suppression for the addon body preview, and a missing-addon indicator on orphaned ADDON nodes [Wave 1]
- [x] 01-10-PLAN.md — WR-01/WR-02/WR-06 hardening: align null-addonTypeId skip policy across snapshot/clipboard, defensive-copy extraFields maps, delegate persistence ADDON branches to AddonNodeDataCopy [Wave 1]

**Re-verification round-3 gap closure** *(round-3 re-verification + fresh code review found NEW-CR-02 BLOCKER re-opening LUA-05, plus NEW-CR-01 and NEW-WR-01 warnings — these plans close them; all parallel, disjoint files)*

**Wave 1 (round-3 gaps)**

- [x] 01-11-PLAN.md — NEW-CR-02 + WR-05: unconditional base-map init + setAddonUnresolved(false) in AddonNodeDataCopy.restoreAddonFieldsToNode so freshly-placed never-edited Script nodes keep their default script across close-and-reopen; new AddonNodeReloadRegressionTest gates both paths (LUA-05, API-05) [Wave 1]
- [x] 01-12-PLAN.md — NEW-CR-01: make AddonLoader.failedAddons thread-safe via Collections.synchronizedMap(new LinkedHashMap<>()) preserving insertion order for the D-08 failure-surface UI (API-05) [Wave 1]
- [x] 01-13-PLAN.md — NEW-WR-01: replace 6 Java assert JSON-field presence/absence checks in AddonNodePersistenceTest with executable JUnit assertTrue/assertFalse so the API-05/LUA-05 persistence-JSON contract fails the build on regression (API-05, LUA-05) [Wave 1]

**UAT round-5 editor-render gap closure** *(in-game UAT round 5 found 3 editor-render gaps on the addon-node render surface — one consolidated plan closes all three)*

**Wave 1 (UAT round-5 gaps)**

- [x] 01-14-PLAN.md — UAT-GAP-B (drag-preview title routed through Node.getDisplayName in 3 compat layers) + UAT-GAP-C (resolved addon nodes under the sidebar use a neutral dimmed body, never the missing-addon placeholder) + UAT-GAP-A (addon scroll range via computeAddonContentHeight, magic +100 removed; tests for no-overflow→no-scrollbar and overflow→scrollbar) (LUA-01, API-07) [Wave 1]

### Phase 2: Lua VM + Core Bindings

**Goal**: The Script node executes real Lua code: Cobalt VM runs on a worker thread with a fresh-per-execution globals object, the async-sync bridging model handles awaitable actions, and the full Pathmind Lua binding surface (variables, movement, game state, error surfacing) is accessible from scripts — both repos remain in a working, loadable state
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: LUA-02, LUA-03, LUA-04, BIND-01, BIND-02, BIND-03, BIND-04
**Success Criteria** (what must be TRUE):

  1. A Script node with `pathmind.setVar("x", 42); pathmind.getVar("x")` executes on a worker thread, the tree advances only after the script finishes, and the variable is readable by a downstream Pathmind node
  2. A script that calls `pathmind.moveTo(x, y, z)` blocks the worker thread until Baritone reports navigation complete, while the game tick thread continues ticking normally
  3. A script that calls `pathmind.getPosition()`, `pathmind.getInventory()`, or `pathmind.getBlock(x,y,z)` returns correct values dispatched safely from the main thread
  4. A script that runs an infinite loop (or any runaway code) is interrupted within the configured wall-clock timeout — the game does not freeze or hang
  5. A script that throws a Lua error surfaces the error message and line number visibly to the user; the graph stops at the Script node rather than silently continuing
  6. UAT checkpoint: human in-game testing required — Lua execution and Baritone integration directly affect game behavior

**Plans**: 4 plans (vertical slices S0–S4, sequential waves — each later plan extends the shared PathmindRuntimeImpl + PathmindBindings)

Plans:
- [ ] 02-01-PLAN.md — Cobalt build wiring + shadow relocation; PathmindRuntime interface + AddonNodeContext.getRuntime(); real worker-thread CobaltVm executor (fresh sandboxed globals, compute-time timeout, Lua error → chat + graph-halt) [Wave 1]
- [ ] 02-02-PLAN.md — getVar/setVar: real PathmindRuntimeImpl variable marshaling + pathmind.getVar/setVar scalar round-trip (BIND-01) [Wave 2]
- [ ] 02-03-PLAN.md — moveTo awaitable: PathmindRuntimeImpl.moveTo wraps PathmindNavigator.startGoto + pathmind.moveTo blocks worker with timeout-clock pause (BIND-02) [Wave 3]
- [ ] 02-04-PLAN.md — game state: getPosition/getInventory/getBlock with main-thread dispatch + bindings + end-of-phase in-game UAT (BIND-03, all six success criteria) [Wave 4]

### Phase 3: Script Node Editor + Autosuggestions

**Goal**: The Script node has a functional in-game code editor with line numbers, inline error display, and prefix-match autosuggestions — the complete UX for authoring Lua scripts without leaving Minecraft
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: EDIT-01, EDIT-02, EDIT-03, EDIT-04
**Success Criteria** (what must be TRUE):

  1. User can type, navigate with arrow keys, select text, copy/paste, and scroll within the Script node's editor area — all standard plain-text editing behaviors work without leaking keypresses to the node graph
  2. A line-number gutter is visible alongside the editor and stays synchronized with the text as lines are added or removed
  3. The most recent script error (message + line number) is displayed directly on the node body without opening any separate UI — absent if the last run succeeded
  4. Typing `pathmind.` in the editor triggers a prefix-match suggestion list of `pathmind.*` API names that can be selected to complete the token
  5. UAT checkpoint: human in-game testing required — editor keyboard routing and EditBoxWidget shortcut behavior need in-game verification

**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. API Foundation + Script Node Registration | 15/15 | Complete    | 2026-06-13 |
| 2. Lua VM + Core Bindings | 0/4 | Planned     | - |
| 3. Script Node Editor + Autosuggestions | 0/TBD | Not started | - |
