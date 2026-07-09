---
phase: 03-script-node-editor-autosuggestions
plan: 03
subsystem: addon-interactive-editor
tags: [editor, editboxwidget, input-routing, per-node-state, leak-proof, addon, mavenlocal]
dependency_graph:
  requires: [03-02]
  provides: [EditorState, interactive-LuaScriptNodeRenderer, bodyHeight-128, inputHandler-wired]
  affects: [LuaScriptNodeRenderer, LuaAddonEntrypoint, EditorState]
tech_stack:
  added: [EditBoxWidget per-node state, AddonNodeInputHandler implementation, per-node editorStates map]
  patterns: [computeIfAbsent state map keyed by nodeId UUID, renderOverlay ambiguity override, draw.fill 4-edge border]
key_files:
  created:
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/EditorState.java
  modified:
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaAddonEntrypoint.java
decisions:
  - renderOverlay explicit no-op override required when implementing two interfaces with identical default method signature (Java ambiguity)
  - draw.fill 4-call border used instead of DrawContextBridge.drawBorderInLayer (not in addon API jar)
  - Loom remapped_mods cache cleared to pick up refreshed 1.1.5+mc1.21.4 mavenLocal jar
  - keyPressed returns true for Esc-blur path (consumes Esc; second Esc closes screen normally per UI-SPEC)
metrics:
  duration: "~30 min"
  completed: "2026-06-25"
  tasks_completed: 2
  files_modified: 2
  files_created: 1
---

# Phase 03 Plan 03: Script Node Interactive Editor Summary

**One-liner:** Per-node `EditBoxWidget` editor replacing the read-only 3-line preview; implements `AddonNodeBodyRenderer + AddonNodeInputHandler` with leak-proof focus (all keys consumed while focused); Script node registered with `bodyHeight(128)` + shared renderer-as-inputHandler.

## Tasks Completed

| # | Task | Commit (addon repo) | Files |
|---|------|---------------------|-------|
| 1 | Create EditorState + rewrite LuaScriptNodeRenderer as interactive editor | 6a3919f | EditorState.java (new), LuaScriptNodeRenderer.java (rewrite) |
| 2 | Register Script node with inputHandler + bodyHeight(128) | f4ef117 | LuaAddonEntrypoint.java |

## What Was Built

### EditorState (new)

Fields: `EditBoxWidget widget` (Yarn 1.21.4 constructor), `boolean focused`, `String lastError`, `int lastErrorLine`, `boolean suggestionOpen`, `int suggestionSelectedIndex`, `List<CompletionEntry> suggestions` (reserved for Plan 03-05).

Constructor: `new EditBoxWidget(tr, 0, 0, w, h, Text.empty(), Text.of(initial))` with `setMaxLength(Integer.MAX_VALUE)` and `setFocused(false)`. Position `(0,0)` is intentional — repositioned per-frame in render (Pitfall 1 compliance).

Helpers: `getText()`, `setText(String)`, `lineCount()` (count '\n' + 1), `hasError()` (lastError != null), `consumeDirty()`.

### LuaScriptNodeRenderer (rewritten)

**Declaration:** `implements AddonNodeBodyRenderer, AddonNodeInputHandler`

**Per-node state map:** `private final Map<String, EditorState> editorStates = new HashMap<>()`

**getOrCreate(ctx, tr, w, h):** Keys on `ctx.getNodeId()` (fallback `"default"` if null/blank); `computeIfAbsent` seeds from `ctx.getScriptText()`, `ctx.getLastError()`, `ctx.getLastErrorLine()` on first creation.

**render():** Layout: `GUTTER_WIDTH_RESERVED = 0` (Plan 03-04 slots in gutter here), editor body at `y + HEADER_HEIGHT`. Editor background filled with `UITheme.NODE_INPUT_BG_ACTIVE` (focused) or `UITheme.BACKGROUND_INPUT` (unfocused). Border drawn with `UITheme.BORDER_FOCUS` / `UITheme.BORDER_SUBTLE` via 4-edge `draw.fill` calls. Widget positioned (`setX`, `setY`, `setWidth`) BEFORE `renderWidget`. Placeholder `-- click to edit` in `UITheme.TEXT_TERTIARY` when empty + unfocused. Error strip at bottom when `state.hasError()`.

**keyPressed():** Returns `false` when `!state.focused`. When focused: Esc (no popup) blurs the editor (`state.focused = false`, `widget.setFocused(false)`) and returns `true` (consumed — prevents screen close). All other keys forwarded to widget + return `true`. No reachable `return false` path while focused.

**charTyped():** Returns `true` while focused (forwards to widget), `false` otherwise.

**mouseScrolled():** Returns `true` while focused (forwards to widget), `false` otherwise.

**mouseClicked():** Forwards to widget (position already set by render), returns `true`.

**onFocusGained/onFocusLost:** Set `state.focused` and `widget.setFocused(...)` accordingly.

**renderOverlay():** Explicit no-op override (see Deviations #1).

### LuaAddonEntrypoint (updated)

Single `LuaScriptNodeRenderer renderer = new LuaScriptNodeRenderer()` instance passed to both `.bodyRenderer(renderer)` and `.inputHandler(renderer)`. Added `.bodyHeight(128)`. No other registration fields changed.

## Verification Results

- `./gradlew build` (pathmind-lua, MC 1.21.4): **BUILD SUCCESSFUL** (10 tasks, 8 executed, 2 up-to-date)
- `grep "implements AddonNodeBodyRenderer, AddonNodeInputHandler"`: confirmed
- No `0x` hex literals in LuaScriptNodeRenderer: confirmed (all UITheme.* tokens)
- `getOrCreate` keys on `ctx.getNodeId()`: confirmed
- `setMaxLength(Integer.MAX_VALUE)` + `setFocused(false)` in EditorState constructor: confirmed
- `.inputHandler(renderer)` + `.bodyHeight(128)` in LuaAddonEntrypoint: confirmed
- Wave 0 tests (GutterWidthTest): PASS (confirmed via build)
- Wave 0 @Disabled tests (SuggestionEngineTest, LuaNodeSerializerLastErrorTest): PASS (disabled, compile-only)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Ambiguity] Explicit renderOverlay override required for dual-interface implementation**
- **Found during:** Task 1 first compile attempt
- **Issue:** `AddonNodeBodyRenderer` and `AddonNodeInputHandler` both declare `default void renderOverlay(AddonNodeContext, DrawContext, int, int, int, int)`. Java requires an explicit override in any class implementing both interfaces — otherwise the compiler reports "inherits unrelated defaults."
- **Fix:** Added `@Override public void renderOverlay(...)` as an explicit no-op in `LuaScriptNodeRenderer`. Plan 03-05 will replace it with the autosuggestion popup render.
- **Files modified:** `LuaScriptNodeRenderer.java`
- **Commit:** 6a3919f

**2. [Rule 3 - Blocking] Loom remapped_mods cache contained stale Pathmind jar**
- **Found during:** Task 1 first compile — errors reported `AddonNodeInputHandler`, `getNodeId()`, `getLastError()` not found despite being present in the published jar.
- **Issue:** Loom's local remapping cache (`/.gradle/loom-cache/remapped_mods/`) held a pre-03-02 remapped version of the Pathmind jar that lacked the new API. Loom uses the cached version and does not re-remap on mavenLocal updates.
- **Fix:** Deleted `.gradle/loom-cache/remapped_mods/` directory; subsequent build re-remapped from the current mavenLocal jar (`1.1.5+mc1.21.4`) and picked up all new API methods.
- **Note:** Per 03-02 SUMMARY, the correct dev-loop procedure is to clear `remapped_mods` after a `publishToMavenLocal` in the Pathmind repo. This deviation is expected; the 03-03 plan's verify step should include this cache-clear step.

**3. [Rule 2 - Missing API] DrawContextBridge not in addon API jar**
- **Found during:** Task 1 implementation
- **Issue:** `com.pathmind.util.DrawContextBridge.drawBorderInLayer` is a Pathmind internal utility not published in the `com.pathmind.api` surface. The plan references it, but the addon cannot import it.
- **Fix:** Replaced with a private `drawBorder(DrawContext, int, int, int, int, int)` helper that calls `draw.fill` four times (top/bottom/left/right edges). Functionally equivalent; cosmetically identical (1 px border via fill).
- **Files modified:** `LuaScriptNodeRenderer.java`
- **Commit:** 6a3919f

### Intentional Adjustments

**4. GUTTER_WIDTH_RESERVED = 0 (gutter deferred to Plan 03-04)**
- Per plan spec: "this plan draws the EDITOR ONLY (gutter + error strip are Plan 04)." Gutter width is explicitly held at 0 px so Plan 03-04 can slot in `computeGutterWidth(state.lineCount())` without touching the layout logic.

## Known Stubs

None. The `GUTTER_WIDTH_RESERVED = 0` constant is an intentional 03-04 placeholder, not a data-flow stub that blocks this plan's goal (the editor works fully at width=0 gutter). The suggestion fields in `EditorState` (`suggestionOpen`, `suggestionSelectedIndex`, `suggestions`) are reserved for 03-05 and do not block EDIT-01.

## Threat Surface Scan

Implementing T-03-03-01..04 as planned:
- **T-03-03-01 (key leak)**: Mitigated — `keyPressed` returns `true` for ALL keys while focused; Esc-blur consumes the key before NodeGraph sees it.
- **T-03-03-02 (clipboard paste)**: Mitigated — `setMaxLength(Integer.MAX_VALUE)` is set; EditBoxWidget handles clipboard natively; no execution on type.
- **T-03-03-03 (format string injection)**: Accept — `drawTextWithShadow` renders literal text; no format-string interpretation.
- **T-03-03-04 (stale widget position)**: Mitigated — `widget.setX/setY/setWidth` called before every `renderWidget` and before `mouseClicked`.

No new threat surface beyond what the threat model covers.

## Self-Check: PASSED

- EditorState.java: FOUND
- LuaScriptNodeRenderer.java: FOUND (rewritten)
- LuaAddonEntrypoint.java: FOUND (updated)
- 03-03-SUMMARY.md: FOUND
- Addon repo commits: 6a3919f, f4ef117 — verified in `git log --oneline`
- Pathmind repo commit: 37ced24 — docs commit with SUMMARY, STATE, ROADMAP
- Build: `./gradlew build` SUCCESSFUL (pathmind-lua, MC 1.21.4, 10 tasks)
- Pathmind API version: com.pathmind:pathmind-fabric:1.1.5+mc1.21.4 (mavenLocal, no version bump needed)
