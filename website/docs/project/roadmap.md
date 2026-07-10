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
| **v1 — Addon API + Lua Script node** | ✅ **Complete (2026-07-10).** 24/24 plans, both UAT gates closed with automated evidence. |
| **v2 — Hardening + richer API/editor** | Backlog below, not yet scheduled. |

## v1 milestone

- ✅ **Phase 1 — API Foundation + Script Node Registration** (2026-06-13): addon API published as a Maven artifact, entrypoint discovery, `NodeType.ADDON` persistence, sidebar palette; Script node placeable, persistent, graceful no-op. Verified + in-game UAT 5/5.
- ✅ **Phase 2 — Lua VM + Core Bindings** (2026-06-13): sandboxed Cobalt 0.7.3 on a worker thread, compute-time timeout, `pathmind.*` bindings (variables, awaitable `moveTo`, position/inventory/block queries), errors to chat with line numbers.
- ✅ **Phase 3 — Script Node Editor + Autosuggestions** (code complete 2026-06-25, in-game UAT 6/6 2026-07-10): inline `EditBoxWidget` editor with leak-proof focus, synced line-number gutter, persisted error strip, prefix-match autosuggestions. Deep review criticals fixed; the UAT found and fixed three more cross-repo blockers (script write-back, runtime-error routing, widget content seeding — see [Planning History](./history.md)).

### Open gates before calling v1 done

- [x] **Phase 3 in-game UAT (SC#5)** — the 6-point editor checklist, **6/6 automated & passing** (2026-07-10). Points 1–5 via `specs/lua-editor-uat.yaml` (run `20260710-095656`); point 6 — error strip on failure, cleared on success — via the new `specs/lua-error-strip.yaml` (run `20260710-090058`, 35 steps: place node, replace default script with one failing on line 2, wire start→node by port drag, run with `K`, assert chat `Lua error: script:2:` + red `⚠ Line 2` strip, fix script, rerun, assert strip cleared). The spec exposed **three real cross-repo blockers**, all fixed same-day: (1) editor input handlers got a throwaway context and nothing wrote script edits back to the node — typed scripts were silently dropped and execution ran the serializer default; (2) execution runs on branch clones, so executor error write-backs never reached the workspace node — errors now route via a `_node_id`-keyed `AddonRuntimeErrors` bridge; (3) `EditBoxWidget`'s constructor `message` param is narration, not content — loaded scripts rendered as an empty editor (plus the serializer round-trip dropped Pathmind-managed `_node_id`). Two testkit robustness fixes fell out too (drag-coordinate parsing, held keypresses for tick-polled keybinds).
- [x] **Phase 2 UAT formally confirmed** (2026-07-10) — full `pathmind.*` surface exercised in-game via the automated harness (`testing/testruns/20260710-001707/`, RESULT: PASS, 7/7 presets): variable round-trips for number/string/boolean + absent→nil + table rejection (`uat-lua-vars`), `getPosition`/`getBlock` loaded + unloaded→nil/`getInventory` shape (`uat-lua-gamestate`), awaitable `moveTo` with exact arrival 12 blocks out (`uat-lua-moveto`), uncaught error stops the graph with `script:3:` line number in chat (`uat-lua-error-expectfail`), and runaway loop killed by the 5s compute budget with `script:2:` in chat (`uat-lua-timeout-expectfail`).

## Infrastructure

- ✅ **Automated in-game test flow via HeadlessMC** (2026-07-09, retired 2026-07-10): built all jars, launched MC 1.21.4 headless via a harness mod, executed fixture presets, wrote `results.json` per run — used to close the Phase 2 UAT gate. Removed on 2026-07-10 together with the play/drive scripts; GUI testing now runs exclusively through the containerized vision testkit below.
- ✅ **Containerized vision-driven GUI test pipeline** (2026-07-10): `testing/` (own repo, [botboy0/mc-testkit](https://github.com/botboy0/mc-testkit)) runs the dev client GPU-rendered in Docker on WSL2 (Mesa d3d12 → host RTX via `/dev/dxg`, hard renderer assertion, no llvmpipe) and executes natural-language YAML test steps via a vision model over OpenRouter (screenshot → grounded click/key via xdotool). Per run: `run.mp4` + `results.json` with per-step cost/tokens/timing. E2E proven: inventory + Pathmind editor opened and visually asserted, ~$0.002/run, warm boot ~20 s. This is the sole GUI test flow; the mod repo keeps only standard unit tests. Docs: `testing/README.md` + the in-game testing guide.
- ✅ **Node-failure observability + expected-fail presets** (2026-07-10): node failures complete their futures normally and only surfaced as UI notifications, making them invisible to automation — `ExecutionManager.recordNodeFailure` (fed by `NodeExecutionCompletion.fail`) now exposes a queryable failure count/message, the harness fails a preset when one is recorded, and presets named `*-expectfail` invert pass/fail for negative-path fixtures. This immediately exposed the silently-broken `print` call in `smoke-lua` and the `moveTo` timeout bug.

- ✅ **Testkit: loadouts, commentary mode, run dashboard** (2026-07-10): mc-testkit gained per-run persistent artifacts + a web dashboard (run list, live status, per-step video seeking, `https://` LAN access); **loadouts** (mount a mods/config overlay tree incl. `.wipe` state reset — the `pathmind-lua` jar now loads in the containerized dev client via Fabric runtime remapping, closing the "no addon in runClient" gap); and **commentary mode** (model reports visual oddities independent of verdicts). Commentary immediately surfaced the two editor rendering bugs now in the v2 backlog. Docs: `testing/README.md` + the [in-game testing guide](../guides/in-game-testing.md).

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
- [ ] Suggestion popup anchors one row too high: its first entry overlaps the current editor line's text (reported consistently by vision-run commentary, e.g. mc-testkit run `20260710-100816` steps 15/16).
- [ ] Editor text rendering artifact around the `=` glyph / cursor on active lines ("crossed box" between variable and value; multiple independent commentary reports across runs) — verify whether the editor font atlas or caret rendering corrupts the glyph.
- [ ] Error strip renders the raw multi-line Lua message on one line — the newline before `stack traceback` shows as a box glyph (commentary, run `20260710-090058` step 23). Truncate at the first line / sanitize control chars.
- [ ] Error strip persists while the script is edited (by design: last-run state until the next run) — but the line number can then point at code that no longer exists (commentary, run `20260710-090058` step 28). Consider clearing or dimming the strip on edit.

---

## Appending to this roadmap

When new work is planned: add it as a checklist item under the right milestone (or start a new `## v3` section), link out to a design doc under `docs/project/` if it needs more than two sentences, and check items off with a date when they land. When a milestone closes, compress it to the one-line-per-phase style above and move the narrative into [Planning History](./history.md).
