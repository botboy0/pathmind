---
sidebar_position: 3
title: Planning History
---

# Planning History (v1 milestone)

A condensed, human-readable trace of everything planned and shipped so far, distilled from the retired GSD planning archive. Dates are as recorded in the original artifacts.

**The project in one line:** refactor Pathmind (visual node-based Minecraft automation editor) to expose a real addon API, and prove it with a sibling-repo addon — a Lua Script node (`pathmind-lua`, Cobalt VM, Fabric / MC 1.21.4) — while Pathmind stays fully functional standalone across 1.21–1.21.11.

---

## Phase 1 — API Foundation + Script Node Registration ✅

**Completed 2026-06-13 · 15 plans · verification passed, final in-game UAT 5/5**

**Goal:** a consumable addon API — entrypoint discovery, typed node registration, addon-agnostic JSON persistence (`_schema_version`), async executor contract, minimal node-body render hook — published as a local Maven artifact, with the sibling repo shipping a Script node that is palette-visible, placeable, persistable, and executes as a graceful no-op.

**How it actually went:** the first three plans built the happy path; the remaining twelve were successive gap-closure rounds driven by code review and five rounds of in-game UAT.

- **01-01 → 01-03** — API contract package (`com.pathmind.api.addon`: entrypoint, registrar, definition, executor, serializer, context, renderer), `NodeType.ADDON` with async dispatch and placeholder persistence, `maven-publish` to mavenLocal, and the `pathmind-lua` scaffold compiling against the API jar only (zero impl classes — the honest-external-consumer proof).
- **01-04 → 01-06** — code review found 3 blockers: addon fields were silently dropped in three data-conversion sites (editor load, clipboard/undo, execution snapshot), and the sidebar never actually drew the addon category. Fixed via a shared `AddonNodeDataCopy` helper + real sidebar render/hit-test, locked with a round-trip regression test.
- **01-07 → 01-10** — in-game UAT found 5 gaps: category overflow had no scrollbar, nodes showed "Addon Node" instead of "Script", fresh nodes were empty until reopened, the body rendered on the wrong z-layer, and orphaned nodes gave no hint about the missing addon. All fixed (registry-aware display names, constructor-time field seeding, scissor clipping, a "⚠ addon missing" indicator), plus map-aliasing hardening.
- **01-11 → 01-13** — re-verification round: freshly-placed never-edited nodes lost their default script on close/reopen (blocker, fixed), `AddonLoader.failedAddons` made thread-safe (synchronized `LinkedHashMap` to keep insertion order for the failure UI), and six no-op Java `assert`s converted to real JUnit assertions. A post-merge fix added a shared `AddonTestRegistry` because the install-once registry singleton broke test isolation (full suite: 230 tests, 0 failures).
- **01-14 → 01-15** — final UAT rounds: drag-preview titles routed through `getDisplayName()`, and two earlier fixes re-diagnosed — the body-blanking-over-sidebar suppression was deleted outright (frame discoloration is the only invalid-drop cue, matching built-ins), and the scroll fix was moved to the correct surface (the category **icon bar** gets its own scroll axis, not the node list).

**Durable decisions:** JEI/REI-style registrar-passed-to-entrypoint pattern, sealed post-init; addons declare their own palette categories; failed registration disables that addon but never crashes the game; missing-addon nodes load as inert placeholders that preserve their JSON; the API versions independently (semver 0.x, started at 0.1.0).

**Known accepted limitations carried forward:** the zero-impl-class API boundary is convention-only (not type-enforced); public `seal()` means one bad addon can block later ones; the node-id regex permits path-traversal characters in the name segment; Fabric-only imports in `common` are a latent NeoForge risk; `Builder.build()` throws NPE instead of `IllegalArgumentException` on blank fields (flagged again in Phase 3, still unfixed).

---

## Phase 2 — Lua VM + Core Bindings ✅

**Completed 2026-06-13 · 4 plans · per-plan tests green (32/32); ⚠ end-of-phase in-game UAT was recorded as *pending* and never logged as completed**

**Goal:** the Script node executes real Lua. Cobalt 0.7.3 (the CC:Tweaked fork), shadow-relocated into the addon jar, runs scripts on a worker thread with fresh sandboxed globals per execution; async-sync bridging lets the worker block on awaitable Pathmind actions while the game keeps ticking.

- **02-01** — VM + runtime bridge: sandboxed Cobalt (no io/os/require), `PathmindRuntime` services interface (deliberately separate from `AddonNodeContext`), worker-thread executor, Lua errors surfaced to chat with line numbers, runaway scripts interrupted by timeout. Build gotcha: the shadowed jar exceeds 65,535 entries → `isZip64=true`.
- **02-02** — variable bridge (`pathmind.getVar/setVar`): scalars only (number/string/boolean); tables/userdata raise a clear Lua error by design.
- **02-03** — awaitable `pathmind.moveTo(x,y,z)` wrapping `PathmindNavigator`; this plan upgraded the timeout from wall-clock to **compute-time** (the clock pauses while a script waits on navigation). Accepted v1 quirks: the one-shot timer won't re-interrupt an infinite loop that starts *after* a `moveTo`, and a concurrent `moveTo` cancels the previous navigation.
- **02-04** — game-state bindings: `getPosition` / `getInventory` / `getBlock` dispatched to the main thread via `MinecraftClient.execute` with bounded waits, marshaled to idiomatic Lua tables.

**Deferred to v2 by explicit decision:** generic `invokeAction(name, args)`, per-node timeout override, table/userdata marshaling, and a real instruction-budget sandbox beyond the timeout.

**Follow-up quick fix (260613-u9f, 2026-06-13, commit `ca8c1fd`):** `pathmind.getBlock()` returned a spurious `minecraft:void_air` for unloaded chunks because client-side `isChunkLoaded` lies for far chunks; replaced with a `getChunk` + `EmptyChunk` check so it correctly returns `nil`.

---

## Phase 3 — Script Node Editor + Autosuggestions 🟡

**Code complete 2026-06-25 · 5 plans · deep code review "fixes applied" (4 criticals fixed) · ⚠ the SC#5 in-game UAT gate was deferred and is not recorded as done**

**Goal:** a functional in-game code editor inline in the node body — multiline editing with cursor/selection/scroll/copy-paste, a synchronized line-number gutter, a persisted co-located error strip, and prefix-match autosuggestions for Lua + `pathmind.*`. This phase co-evolved both repos: Pathmind's render-only addon API grew **input routing** (focus lifecycle + key/char/mouse/scroll forwarding), and the addon built the editor on top of it.

- **03-01** — test-first scaffold: live gutter-width test plus disabled contract tests for the suggestion engine and error persistence, enabled as later waves landed.
- **03-02** — the enabling API on Pathmind's side: `AddonNodeInputHandler` (6 methods), focus tracking and input forwarding in `NodeGraph`, `bodyHeight` on definitions, per-node identity via a persisted `_node_id` UUID, and a post-scissor overlay render pass.
- **03-03** — the interactive editor (`EditBoxWidget`-backed, per-node `EditorState`), leak-proof by design: while focused, all keys are consumed; Esc blurs.
- **03-04** — line-number gutter synced to scroll, persisted `⚠ Line N: message` error strip (serializer bumped to schema v2, backward compatible), thread-safe error write-back from the Lua worker to the game thread.
- **03-05** — in-process `SuggestionEngine` (deep research ruled out embedding a real Lua LSP as over-engineering): `pathmind.*` names derived from the live bindings table plus Lua keywords/stdlib, triggered by `pathmind.` or Ctrl+Space, full keyboard/mouse navigation with no key leaks.

**Deep review (2026-06-25) found and fixed 4 criticals:** a `ThreadLocal` error-slot leak across threads; all fresh unsaved nodes sharing one editor state (typing in one edited the other); clicking another node not blurring the focused editor (keyboard leak); and suggestion acceptance corrupting multi-line scripts when the cursor wasn't at the end.

**Known v1 limitations:** `EditBoxWidget` exposes no cursor API in 1.21.4, so mid-script completion moves the cursor to the end (a mixin accessor is the tracked v2 fix); the per-node editor-state map is never evicted.

---

## Where that leaves v1

All 24 plans across 3 phases are code complete with the review findings addressed. The one genuinely open v1 item is the **Phase 3 end-of-phase in-game UAT** (and Phase 2's UAT checkpoint was likewise never formally logged — in practice the Phase 2 surface has since been exercised, e.g. the `getBlock` quick fix came from real use). Everything else outstanding is tracked as v2 backlog on the [Roadmap](./roadmap.md).
