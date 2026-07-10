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
- [x] Provide a `print` binding in the Lua sandbox (2026-07-10) — sandbox-safe global `print` in `CobaltVm`/`PathmindBindings.buildPrint`, tab-joins arguments via Cobalt's `toStringDirect` and routes the line to player chat; the serializer default script now runs cleanly on a fresh node (unit-tested in `CobaltVmSmokeTest`).
- [ ] Navigator: short `moveTo` hops (≲6 blocks) can be rejected as "very short partial path" (`MIN_PARTIAL_PATH_LENGTH` floor) even on flat ground — seen intermittently in headless test runs.

### Editor

_The items closed on 2026-07-10 (print binding, strip first-line, strip dimming, focus re-gain, popup anchor) are verified in-game by the mc-testkit specs `lua-v2-print-dim.yaml` (run `20260710-180204`) and `lua-editor-uat.yaml` (run `20260710-183026`), alongside the unit tests in `CobaltVmSmokeTest` / `LuaScriptNodeRendererTextTest` / `PopupAnchorTest`._

- [ ] Cursor-aware suggestion acceptance via an `EditBoxWidget` cursor-accessor mixin (mid-script completion currently jumps the cursor to end).
- [ ] Evict stale entries from the per-node editor-state map.
- [ ] Syntax highlighting.
- [ ] Script hot-reload without graph restart.
- [ ] Handle `\n` in error tooltips (Lua stack traces wrap incorrectly).
- [x] Suggestion popup anchored one row too high (2026-07-10) — the anchor was a fixed offset from the editor top; it now sits one row below the line being typed using the gutter's pixel model (last line × 9 px, scroll-corrected; `computePopupAnchorY`, unit-tested in `PopupAnchorTest`). Verified in-game: `lua-editor-uat` run `20260710-183026`, `suggestions-dot.png` shows all three script lines clear of the popup.
- [x] ~~Editor `=` glyph rendering artifact~~ resolved as **not a bug** (2026-07-10) — the "crossed box" is the X11 mouse pointer captured in the container screenshots, parked wherever the vision model last clicked (usually mid-line, next to `=`). Cross-run evidence: the identical glyph appears between `local` and `x` (no `=` nearby) in run `20260710-175701` step 34, and run `20260710-183026` `suggestions-dot.png` shows crisp `=` glyphs with the pointer visible beside them. Vision-commentary reports of this shape can be disregarded.
- [x] Error strip multi-line message → box glyph (2026-07-10) — the strip now renders only the first line of the error (`LuaScriptNodeRenderer.firstLine`, unit-tested); the full traceback stays available in the hover tooltip.
- [x] Editor focus lost after Esc-blur (2026-07-10, found by the new `lua-v2-print-dim` vision spec) — after blurring the editor with Esc, clicking back into it never re-focused: NodeGraph's WR-01 guard skips `focusAddonNode` when it still considers the node focused, and the addon's Esc self-blur is invisible to NodeGraph, so `onFocusGained` never re-fired and keystrokes leaked to the graph (Esc closed the whole screen). The addon now takes focus directly in its `mouseClicked` handler (idempotent with `onFocusGained`).
- [x] Error strip stale after editing (2026-07-10) — the strip now dims (subtle border + tertiary text) once the script differs from the snapshot taken when the error was recorded (`EditorState.isErrorStale`); it stays red while the script matches the failing run. Known limit: a re-run of an edited script producing a byte-identical error message keeps the strip dimmed (errors arrive value-only via the context, so a new run with the same message is indistinguishable).

---

## Appending to this roadmap

When new work is planned: add it as a checklist item under the right milestone (or start a new `## v3` section), link out to a design doc under `docs/project/` if it needs more than two sentences, and check items off with a date when they land. When a milestone closes, compress it to the one-line-per-phase style above and move the narrative into [Planning History](./history.md).
