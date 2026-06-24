---
phase: 03-script-node-editor-autosuggestions
plan: 04
subsystem: addon-gutter-error-strip
tags: [gutter, error-strip, serializer-v2, lastError, persistence, executor-writeback, thread-safety]
dependency_graph:
  requires: [03-03]
  provides: [computeGutterWidth, drawGutter, drawErrorStrip, renderOverlay-tooltip, LuaNodeSerializer-v2, lastError-writeback]
  affects: [LuaScriptNodeRenderer, LuaNodeSerializer, LuaNodeExecutor, EditorState, LuaLastError, CobaltVm]
tech_stack:
  added: [LuaLastError thread-local carrier, computeGutterWidth static method, drawGutter, drawErrorStrip, renderOverlay tooltip]
  patterns: [thread-local error capture before future completion, MinecraftClient.execute game-thread write-back, immediate-mode mouse hover detection via mc.mouse + getScaleFactor, word-wrap tooltip inline without TooltipRenderer]
key_files:
  created:
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaLastError.java
  modified:
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeSerializer.java
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeExecutor.java
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/CobaltVm.java
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/EditorState.java
    - pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/LuaNodeSerializerLastErrorTest.java
    - pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/GutterWidthTest.java
decisions:
  - LuaLastError thread-local (not NodeResult metadata) chosen to carry error message from CobaltVm worker to LuaNodeExecutor.whenComplete; avoids changing NodeResult enum or adding callback field to AddonNodeContext
  - getScrollY() confirmed valid on ScrollableWidget in MC 1.21.4 Yarn (Assumption A1 verified via javap bytecode of minecraft-merged jar) — returns double; divided by LINE_HEIGHT for first-visible-line index
  - TooltipRenderer (com.pathmind.ui.tooltip) is a Pathmind internal not on the addon API surface; replicated inline in LuaScriptNodeRenderer with UITheme.TOOLTIP_BG / TOOLTIP_BORDER tokens
  - Hover detection in drawErrorStrip via MinecraftClient.mouse.getX/Y() divided by Window.getScaleFactor() — standard immediate-mode approach; no event system available in body renderer
  - EditorState.pendingTooltipMessage/MouseX/MouseY fields used to bridge render() → renderOverlay() tooltip staging (per-frame: cleared at render start, set when mouse over strip)
metrics:
  duration: "~35 min"
  completed: "2026-06-25"
  tasks_completed: 2
  files_modified: 7
  files_created: 1
---

# Phase 03 Plan 04: Gutter + Error Strip Summary

**One-liner:** Line-number gutter (EDIT-02) and persisted last-run error strip (EDIT-03) with schema-versioned v2 serializer, thread-safe executor write-back via LuaLastError thread-local, and full-message hover tooltip via renderOverlay.

## Tasks Completed

| # | Task | Commit (addon repo) | Files |
|---|------|---------------------|-------|
| 1 | lastError persistence (serializer v2) + executor write-back + re-enable serializer test | 493fb3d | LuaNodeSerializer.java, LuaNodeExecutor.java, LuaLastError.java (new), CobaltVm.java, LuaNodeSerializerLastErrorTest.java |
| 2 | Gutter draw pass + error strip draw pass + hover tooltip + re-enable gutter test | 236fb47 | LuaScriptNodeRenderer.java, EditorState.java, GutterWidthTest.java |

## What Was Built

### LuaLastError (new)

Thread-local carrier (`ThreadLocal<String>`) that bridges the Cobalt worker thread to `LuaNodeExecutor.whenComplete`. `CobaltVm.run` calls `LuaLastError.set(msg)` immediately before `resultFuture.complete(NodeResult.FAILURE)`. `LuaNodeExecutor`'s `whenComplete` reads it and clears it after writing to `AddonNodeContext` on the game thread.

This approach was chosen over extending `NodeResult` or adding a callback field to `AddonNodeContext` because: (1) `NodeResult` is a Pathmind API enum that can't be changed from the addon, (2) `whenComplete` fires on the same worker thread that set the thread-local, so the read is always thread-local-coherent.

### LuaNodeSerializer (v2)

- `CURRENT_SCHEMA_VERSION` bumped from 1 to 2.
- `serialize`: adds `fields.put("lastError", ctx.getLastError())` + `fields.put("lastErrorLine", ctx.getLastErrorLine())`. `null` lastError serializes as JSON null; round-trips to null on deserialize (cleared-on-success contract).
- `deserialize`: reads `lastError` via `instanceof String` pattern match (null-safe for absent key) and `lastErrorLine` via `instanceof Number n ? n.intValue() : 0` (GSON double-cast guard, API Pitfall 4). v1 blobs (no error keys) produce null/0 cleanly.

### LuaNodeExecutor (write-back)

`whenComplete` added to the returned future:
- **Success path**: `MinecraftClient.getInstance().execute(() -> { ctx.setLastError(null); ctx.setLastErrorLine(0); })` — clears error on the game thread.
- **Failure path**: reads `LuaLastError.get()`, matches `^script:(\d+):(.*)$` to parse the Cobalt line number, marshals `setLastError(msg) + setLastErrorLine(N)` to the game thread via `MinecraftClient.execute`. Line falls back to 0 if the pattern doesn't match (RESEARCH A3 — message still displayed, line is 0).

Phase 2 chat-error channel in `CobaltVm.run` is fully preserved.

### LuaScriptNodeRenderer (gutter + error strip)

**computeGutterWidth(int lineCount):** Static, package-visible (test-callable). Formula `digitCount * 6 + 8`: 14 / 20 / 26 px for 1/2/3-digit counts.

**drawGutter:** Fills `UITheme.BACKGROUND_SECONDARY`; draws 1 px `UITheme.BORDER_SUBTLE` right divider; draws right-aligned line numbers in `UITheme.TEXT_TERTIARY` via `drawTextWithShadow`. Scroll-synced: `scrollOffsetLines = (int)(widget.getScrollY() / LINE_HEIGHT)`. `getScrollY()` confirmed present as `public double` on `ScrollableWidget` in MC 1.21.4 Yarn (see getScrollY Resolution below).

**drawErrorStrip:** When `state.hasError()`, draws 18 px strip with `UITheme.BACKGROUND_SECONDARY` bg, `UITheme.BORDER_DANGER_MUTED` 1 px top border, `UITheme.STATE_ERROR` text in format `⚠ Line N: <message>` truncated to `width-8` with `"..."`. Detects mouse hover via `MinecraftClient.mouse.getX/Y() / window.getScaleFactor()`; sets `state.pendingTooltipMessage` when hovering.

**renderOverlay:** Reads `pendingTooltipMessage`; draws word-wrapped tooltip panel using `UITheme.TOOLTIP_BG` + `UITheme.TOOLTIP_BORDER`. Tooltip is rendered after `disableScissor` (above the node scissor clip). Capped at 6 lines (T-03-04-03).

**Zero hex literals** in all new gutter/error/tooltip code — all colors via `UITheme.*`.

### EditorState (tooltip staging fields)

Added `pendingTooltipMessage`, `pendingTooltipMouseX`, `pendingTooltipMouseY` fields. Reset at the start of each `render()` call; set in `drawErrorStrip` when mouse is over the strip.

### Tests Re-enabled

- **LuaNodeSerializerLastErrorTest** (`@Disabled` removed): 4 tests — serialize_includesLastErrorAndSchemaV2, deserialize_readsLastErrorWithGsonDoubleCast, deserialize_v1MapWithoutLastError_isBackwardCompatible, roundTrip_nullLastError_clearedOnSuccess. All PASS.
- **GutterWidthTest** (local mirror removed, now calls `LuaScriptNodeRenderer.computeGutterWidth` directly): 4 tests. All PASS.

### Build Verification

`./gradlew build` (pathmind-lua, MC 1.21.4): **BUILD SUCCESSFUL** (10 tasks)

## getScrollY Resolution (Assumption A1)

Assumption A1 in RESEARCH.md: "`widget.getScrollY()` (inherited from `ScrollableWidget`) returns the current vertical scroll in pixels."

**Resolution: CONFIRMED.** Bytecode inspection of the MC 1.21.4 Yarn-mapped `ScrollableWidget.class` (via `javap -c`) shows:

```
public double getScrollY();
```

The method is `public` and returns `double` (not `int`). Division by `LINE_HEIGHT` and cast to `int` gives the first-visible-line index. No fallback was needed — the assumed accessor is present exactly as expected. No deviation recorded.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Architecture] LuaLastError thread-local chosen over nodal error callback**

- **Found during:** Task 1 implementation
- **Issue:** The plan says "write lastError back to the node's extra-fields ON THE GAME THREAD" via `MinecraftClient.execute`. But `AddonNodeContext` is already the right target — `setLastError`/`setLastErrorLine` exist (added in 03-02). The problem is that `LuaNodeExecutor.whenComplete` runs on the worker thread and doesn't have the Lua error message — `NodeResult.FAILURE` is a plain enum. The plan mentioned a thread-local as the recommended mechanism (RESEARCH Pattern 8 "Pitfall 6") but didn't spell out the exact class.
- **Fix:** Created `LuaLastError` thread-local carrier. `CobaltVm.run` sets it before completing FAILURE (on the same worker thread). `LuaNodeExecutor.whenComplete` reads it from the thread-local and clears it after the game-thread dispatch.
- **Why safe:** `CompletableFuture.complete(NodeResult.FAILURE)` returns synchronously on the worker thread. The `whenComplete` callback fires on the completing thread (the same worker) immediately after. The thread-local read in `whenComplete` is guaranteed to see the value set by `CobaltVm` on the same thread.
- **Files modified:** `LuaLastError.java` (new), `CobaltVm.java`, `LuaNodeExecutor.java`
- **Commits:** 493fb3d

**2. [Rule 2 - Missing API] TooltipRenderer not on addon API surface**

- **Found during:** Task 2 implementation
- **Issue:** `com.pathmind.ui.tooltip.TooltipRenderer` is a Pathmind internal class not published in the `com.pathmind.api` surface. The plan says to use "the popup-above-scissor overlay pass OR Pathmind's TooltipRenderer if reachable from the addon."
- **Fix:** Replicated the tooltip pattern inline in `LuaScriptNodeRenderer` using `UITheme.TOOLTIP_BG` + `UITheme.TOOLTIP_BORDER` tokens (consistent with the existing border-drawing deviation from 03-03). Word-wrap capped at 6 lines per T-03-04-03.
- **Files modified:** `LuaScriptNodeRenderer.java`
- **Commit:** 236fb47

**3. [Rule 2 - Missing API] DrawContextBridge not on addon API surface (inherited from 03-03)**

- No change needed — the `drawBorder` helper from 03-03 was already in place and reused for the tooltip panel border.

## Known Stubs

None. All plan goals are fully implemented:
- Gutter renders and is scroll-synced.
- Error strip shows/hides per `lastError`.
- `lastError` persists round-trip (blob v2, backward-compatible with v1).
- Both Wave 0 tests are live and passing.

## Threat Surface Scan

Implementing all T-03-04-* mitigations:

| Threat | Status |
|--------|--------|
| T-03-04-01 (Injection: error rendered as format string) | Mitigated — `drawTextWithShadow(tr, displayMsg, ...)` renders literal text. No `String.format`. |
| T-03-04-02 (Tampering: malformed lastErrorLine in preset) | Mitigated — `instanceof Number ? intValue() : 0` in deserialize; renderer only uses it as a label string. |
| T-03-04-03 (DoS: pathologically long error message) | Mitigated — strip truncates to `width-8 + "..."`; tooltip word-wraps to 220 px max and caps at 6 lines. |
| T-03-04-04 (Race: worker thread writes node state) | Mitigated — write-back marshaled via `MinecraftClient.getInstance().execute(Runnable)` on game thread. |
| T-03-04-05 (v2 blob loaded by older addon) | Accepted — schema version read tolerantly; v1 code ignores unknown keys; additive change. |

No new threat surface introduced beyond what the plan's threat model covers.

## Self-Check: PASSED

- LuaLastError.java: FOUND (`C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaLastError.java`)
- LuaNodeSerializer.java: FOUND (CURRENT_SCHEMA_VERSION == 2)
- LuaNodeExecutor.java: FOUND (whenComplete with MinecraftClient.execute write-back)
- CobaltVm.java: FOUND (LuaLastError.set before resultFuture.complete FAILURE)
- LuaScriptNodeRenderer.java: FOUND (computeGutterWidth, drawGutter, drawErrorStrip, renderOverlay tooltip)
- EditorState.java: FOUND (pendingTooltipMessage/MouseX/MouseY fields)
- GutterWidthTest.java: FOUND (calls LuaScriptNodeRenderer.computeGutterWidth directly)
- LuaNodeSerializerLastErrorTest.java: FOUND (@Disabled removed)
- Addon repo commits: 493fb3d (Task 1), 236fb47 (Task 2) — verified via `git log --oneline`
- Build: `./gradlew build` BUILD SUCCESSFUL (10 tasks, pathmind-lua MC 1.21.4)
- GutterWidthTest: 4/4 PASS
- LuaNodeSerializerLastErrorTest: 4/4 PASS
- No hex literals in gutter/error/tooltip code: confirmed (grep returns empty)
- getScrollY confirmed present in MC 1.21.4 Yarn: CONFIRMED via javap bytecode
