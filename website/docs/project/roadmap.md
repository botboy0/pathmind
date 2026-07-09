---
sidebar_position: 2
title: Roadmap
---

# Roadmap

The living plan. Completed work is summarized in one line each — the full trace lives in [Planning History](./history.md). New work gets appended here as it's planned; keep entries short and move detail into their own docs when they grow.

_Last updated: 2026-07-10_

## Status at a glance

| Milestone | Status |
|---|---|
| **v1 — Addon API + Lua Script node** | Code complete (24/24 plans). Phase 2 UAT closed with automated evidence. One open gate: Phase 3 in-game UAT. |
| **v2 — Hardening + richer API/editor** | Backlog below, not yet scheduled. |

## v1 milestone

- ✅ **Phase 1 — API Foundation + Script Node Registration** (2026-06-13): addon API published as a Maven artifact, entrypoint discovery, `NodeType.ADDON` persistence, sidebar palette; Script node placeable, persistent, graceful no-op. Verified + in-game UAT 5/5.
- ✅ **Phase 2 — Lua VM + Core Bindings** (2026-06-13): sandboxed Cobalt 0.7.3 on a worker thread, compute-time timeout, `pathmind.*` bindings (variables, awaitable `moveTo`, position/inventory/block queries), errors to chat with line numbers.
- 🟡 **Phase 3 — Script Node Editor + Autosuggestions** (code complete 2026-06-25): inline `EditBoxWidget` editor with leak-proof focus, synced line-number gutter, persisted error strip, prefix-match autosuggestions. Deep review criticals fixed.

### Open gates before calling v1 done

- [ ] **Phase 3 in-game UAT (SC#5)** — the 6-point editor checklist: typing/navigation/copy-paste without key leaks, gutter sync, error strip on failure and cleared on success, `pathmind.` and Ctrl+Space suggestion triggers, suggestion accept, Esc blur.
- [x] **Phase 2 UAT formally confirmed** (2026-07-10) — full `pathmind.*` surface exercised in-game via the automated harness (`testing/testruns/20260710-001707/`, RESULT: PASS, 7/7 presets): variable round-trips for number/string/boolean + absent→nil + table rejection (`uat-lua-vars`), `getPosition`/`getBlock` loaded + unloaded→nil/`getInventory` shape (`uat-lua-gamestate`), awaitable `moveTo` with exact arrival 12 blocks out (`uat-lua-moveto`), uncaught error stops the graph with `script:3:` line number in chat (`uat-lua-error-expectfail`), and runaway loop killed by the 5s compute budget with `script:2:` in chat (`uat-lua-timeout-expectfail`).

## Infrastructure

- ✅ **Automated in-game test flow via HeadlessMC** (2026-07-09): `testing/run-tests.ps1` in the sidequests workspace builds all jars, launches MC 1.21.4 headless (or rendered with screenshots), auto-creates a fixed-seed superflat world via the `pathmind-test-harness` sibling mod, executes fixture presets, and writes logs + `results.json` per run to `testing/testruns/<timestamp>/`. See `testing/README.md`. Useful for closing the v1 UAT gates with reproducible evidence.
- ✅ **Node-failure observability + expected-fail presets** (2026-07-10): node failures complete their futures normally and only surfaced as UI notifications, making them invisible to automation — `ExecutionManager.recordNodeFailure` (fed by `NodeExecutionCompletion.fail`) now exposes a queryable failure count/message, the harness fails a preset when one is recorded, and presets named `*-expectfail` invert pass/fail for negative-path fixtures. This immediately exposed the silently-broken `print` call in `smoke-lua` and the `moveTo` timeout bug.

## v2 backlog

Carried over from explicit deferrals and accepted limitations in v1. Roughly grouped; nothing here is committed or ordered yet.

### API hardening
- [ ] `AddonNodeDefinition.Builder.build()` — throw `IllegalArgumentException` with a useful message instead of NPE on blank fields (flagged in both Phase 1 and Phase 3 reviews, never fixed).
- [ ] Restrict path-traversal characters in the node-id regex name segment.
- [ ] Make one failed addon unable to block subsequent addon loading (public `seal()` issue).
- [ ] Rate-limit the per-frame warn log when an addon renderer throws.
- [ ] NeoForge addon loading support (currently Fabric-only imports in `common` are a latent NeoForge break).
- [ ] Type-enforce the API/impl boundary (currently convention-only).
- [ ] Full UI extension points: panels, categories, overlays; lifecycle hooks; Modrinth Maven publishing.

### Lua runtime
- [ ] Generic `invokeAction(name, args)` dispatch (open the full action surface to scripts).
- [ ] Table/userdata variable marshaling (scalars only in v1).
- [ ] Per-node timeout override.
- [ ] Real instruction-budget sandboxing beyond the compute-time timeout.
- [x] Re-arm the timeout timer after awaitable actions (2026-07-10) — `CobaltVm` now runs a periodic compute-aware watchdog with a `blocked` flag raised around action futures; also fixes the safety-net `Thread.interrupt()` killing legitimate `moveTo` waits longer than ~5.5s wall-clock.
- [ ] Fix the off-thread `whenComplete` touching Minecraft client state (latent race flagged in Phase 1 review).
- [ ] Provide a `print` binding in the Lua sandbox (routed to chat or log) — Cobalt's `print` lives in the excluded `SystemBaseLib`, so scripts calling it today get `attempt to call global 'print' (a nil value)`.
- [ ] Navigator: short `moveTo` hops (≲6 blocks) can be rejected as "very short partial path" (`MIN_PARTIAL_PATH_LENGTH` floor) even on flat ground — seen intermittently in headless test runs.

### Editor
- [ ] Cursor-aware suggestion acceptance via an `EditBoxWidget` cursor-accessor mixin (mid-script completion currently jumps the cursor to end).
- [ ] Evict stale entries from the per-node editor-state map.
- [ ] Syntax highlighting.
- [ ] Script hot-reload without graph restart.
- [ ] Handle `\n` in error tooltips (Lua stack traces wrap incorrectly).

---

## Appending to this roadmap

When new work is planned: add it as a checklist item under the right milestone (or start a new `## v3` section), link out to a design doc under `docs/project/` if it needs more than two sentences, and check items off with a date when they land. When a milestone closes, compress it to the one-line-per-phase style above and move the narrative into [Planning History](./history.md).
