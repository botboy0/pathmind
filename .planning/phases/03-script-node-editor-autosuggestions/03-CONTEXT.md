# Phase 3: Script Node Editor + Autosuggestions - Context

**Gathered:** 2026-06-13
**Status:** Ready for planning

<domain>
## Phase Boundary

The Script node gains a functional in-game code editor rendered **inline in the node body**: a multiline plain-text editor (cursor movement, selection, scrolling, copy/paste), a synchronized line-number gutter, co-located display of the last-run error, and LSP-style prefix-match autosuggestions for Lua + the `pathmind.*` API. This is the complete authoring UX — no separate editor screen.

This phase is a **co-evolution point**: Pathmind's addon API is currently render-only (`AddonNodeBodyRenderer.render(...)` — no input hooks; `NodeGraph` rebuilds a stateless `AddonNodeContext` each frame and never routes input to the addon). To support an interactive editor inside the node body, the addon API must be **extended with an input-routing surface** (focus lifecycle + keyboard/char/mouse/scroll forwarding to the focused addon node). The addon (sibling repo `pathmind-lua`) then implements the editor — EditBoxWidget-backed text area, gutter, error strip, and autosuggestion popup — against that new API surface. This is the honest co-evolution: the addon's concrete editor need drives the new Pathmind input API.

Requirements: EDIT-01 (plain-text multiline editor), EDIT-02 (line-number gutter), EDIT-03 (co-located last-run error), EDIT-04 (prefix-match autosuggestions).

Out of scope: syntax highlighting (v2 — LUA-V2-02), scope-aware completion of locals/params/type inference (v2 — needs an AST), script hot-reload (v2), per-node editor config, an external/embedded LSP process.

**UAT checkpoint:** human in-game testing required — editor keyboard routing and `EditBoxWidget` shortcut behavior (copy/paste, selection, arrow nav, leak-proofing) need in-game verification (SC#5).

</domain>

<decisions>
## Implementation Decisions

### Editing Surface & Input Routing (the core architecture)
- **Editor lives inline in the node body** (matches SC#1 "within the Script node's editor area"). Not a focused overlay, not a modal.
- **Text-editing widget: Minecraft's `EditBoxWidget`** (multiline) for native cursor, selection, scrolling, copy/paste, and keyboard shortcut handling. The UAT checkpoint explicitly names EditBoxWidget. Available in MC 1.21.4 (addon target).
- **Input routing: extend the addon API with an interactive input hook.** Pathmind gains an addon-facing surface that forwards focus + `keyPressed` / `charTyped` / `mouseClicked` / `mouseScrolled` to the focused addon node; the **addon owns the EditBoxWidget** and editor state. (Exact interface name/shape is Claude's discretion during planning — match Pathmind API conventions; e.g. a new `AddonNodeInputHandler` or an interactive extension of the body-renderer contract.)
- **Focus model: click the editor area to focus** (captures keystrokes), **Esc or click-away to blur**. While focused, keypresses must NOT leak to the node graph (no node nudge/delete/pan from typing) — leak-proofing is a primary UAT concern.
- **Editor state persistence:** the per-frame `AddonNodeContext` is stateless and rebuilt each frame, so mutable editor state (EditBoxWidget instance, cursor, scroll, focus) must live **per-node** in the addon, keyed by node identity — not in the per-frame context.

### Editor Layout & Line-Number Gutter
- **Gutter: left column, right-aligned dimmed line numbers, subtle divider** between gutter and text. Gutter width grows with digit count and stays synchronized as lines are added/removed (SC#2).
- **Node sizing: enlarged fixed body sized for ~8–10 visible lines**; the editor scrolls within that body (vertical scroll). Node does not auto-grow to content.
- **Wrapping: no soft-wrap — horizontal scroll** for long lines (code-editor convention; keeps the gutter's line count honest).
- **Theme: default Minecraft font + Pathmind `UITheme` colors** (background, text, selection). No syntax highlighting in v1.

### Error Display (EDIT-03)
- **Placement: a dedicated error strip at the bottom of the node body**, below the editor — co-located, no separate UI (SC#3).
- **Format: `⚠ Line N: <message>`**, single line truncated to width; full message available on hover (tooltip).
- **Persistence: persist `lastError` on the node** via the addon extra-fields blob (the same mechanism that persists `script` — API-05 schema-versioned opaque blob). Survives node reopen / preset save-load.
- **Lifecycle: cleared on the next successful run.** Absent entirely when the last run succeeded.
- **Styling: red text + `⚠` icon**, matching the existing `NodeErrorNotificationOverlay` palette.
- Note: Phase 2 deliberately deferred node-level error persistence + co-located display to here (chat was the Phase 2 channel). This phase adds the node-persisted `lastError` and the in-body strip without reworking the Phase 2 surfacing architecture.

### Autosuggestions (EDIT-04)
- **Trigger: BOTH** — auto-trigger on typing `pathmind.` (satisfies SC#4 verbatim) AND manual **Ctrl+Space** anywhere in the editor.
- **Source: in-process, LSP-*style* completion engine (NOT an external/embedded LSP).** Deep-research (2026-06-13, 25/25 claims verified) confirmed there is no embeddable JVM Lua LSP — a real one (sumneko LuaLS) is Lua-written, ships as 6 per-platform native binaries, and runs as a subprocess via LSP4J (over-engineering for v1); pure-JVM parsers (luaj stale 2019; dingyi222666/lua-parser Apache-2.0 but completion is "work in progress") give parsing, not completion. The recommended and locked approach: a hand-rolled in-process engine that tokenizes the current line, detects **identifier-prefix vs member-access (`table.`)**, and completes from a symbol set of **Lua keywords + stdlib names + the `pathmind.*` binding names**.
- **Symbol set sync: `pathmind.*` names auto-derived from the live binding table** (`PathmindBindings.build(...)`), not a hardcoded list — prevents drift as the API grows. Lua keywords/stdlib come from a static constant.
- **Selection UX:** arrow keys navigate the list **and** mouse-click selects; **Enter confirms** (and the keypress is **consumed** so it does not leak / insert a newline). **Esc closes** the popup (also **consumed**, no leak). The accepting press completes the token.
- **Rendering:** popup list anchored under the cursor token; show **name + signature** (e.g. `getBlock(x, y, z)`); cap ~6 rows. Reuse the `NavigatorChatSuggestions` prefix-match + `selectedIndex` + arrow-nav pattern as the analog.
- **Known v1 ceiling:** no scope-aware completion (locals, function params, nested table member resolution beyond the static set). Acceptable for v1; revisit on an AST in v2 if needed.

### Claude's Discretion
- Exact name/package and method shape of the new addon input-routing API interface — finalize during planning to match Pathmind API conventions (upstream-bound code).
- Exact per-node editor-state storage idiom in the addon (e.g. a `WeakHashMap<Node-identity, EditorState>` vs an id-keyed map), and the node-identity key used.
- Exact `EditBoxWidget` construction/config for 1.21.4 (sizing, scroll, max length) and how the gutter is composited alongside it (separate draw pass vs wrapper widget).
- Exact tokenizer regex and completion-ranking for the in-process engine; exact Lua keyword/stdlib symbol list.
- Exact hover-tooltip mechanism for the truncated error message within the node body.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Addon API surface (extended this phase)
- `common/src/main/java/com/pathmind/api/addon/AddonNodeBodyRenderer.java` — current render-only hook; the new input-routing surface joins it here.
- `common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java` — per-frame stateless context (`scriptText`, `addonTypeId`); editor mutable state must NOT live here.
- `common/src/main/java/com/pathmind/api/addon/` — `AddonNodeDefinition`, `NodeTypeRegistrar`, `PathmindRuntime`, etc.

### NodeGraph (Pathmind-side wiring)
- `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` — `renderAddonNodeContent` (~line 7384: stateless per-frame addon body render with scissor clip); existing in-node field editing (`eventNameEditingNode`, parameter-field editing with caret blink + selection, ~lines 3768–3934) as the proven in-node editing pattern; screen-level input entry points to route focus/key/char/mouse to the focused addon node.

### Reusable UI assets (Pathmind)
- `common/src/main/java/com/pathmind/ui/overlay/NavigatorChatSuggestions.java` — prefix-match suggestion popup: `selectedIndex`, arrow-key nav, render-above pattern. Direct analog for EDIT-04.
- `common/src/main/java/com/pathmind/ui/control/PathmindTextField.java` + `common/src/main/java/com/pathmind/mixin/TextFieldWidgetAccessor.java` — single-line field with selection/caret/suggestion rendering; reference for caret/selection rendering idioms.
- `UITheme` — colors for editor bg/text/selection and error red.

### Addon repo (`C:\Users\Trynda\Desktop\Dev\sidequests\pathmind-lua`)
- `src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java` — current **read-only 3-line preview**; replace with the interactive editor implementation against the new input API.
- `src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java` — the live `pathmind.*` binding table (`getVar, setVar, moveTo, getPosition, getInventory, getBlock`); autosuggestion names auto-derive from here.
- `src/main/java/com/mrmysterium/pathmindlua/LuaNodeExecutor.java` / `LuaNodeSerializer.java` — error origin (line number) and persistence wiring for `lastError`.

### Project
- `.planning/REQUIREMENTS.md` — EDIT-01..04 definitions.
- `.planning/PROJECT.md` — co-evolution strategy; addon under `com.mrmysterium`, API under `com.pathmind`; Fabric / MC 1.21.4-only addon target.
- `.planning/phases/02-lua-vm-core-bindings/02-CONTEXT.md` — error-surfacing architecture deferred node-level persistence + co-located display to this phase.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`EditBoxWidget` (Minecraft, 1.21.4):** native multiline editor — cursor, selection, scroll, copy/paste, keyboard shortcuts. The editor text area is built on this rather than hand-rolled.
- **`NavigatorChatSuggestions`:** existing prefix-match suggestion menu with `selectedIndex` + arrow-key navigation — the pattern to mirror for the autosuggestion popup.
- **NodeGraph in-node field editing:** Pathmind already does in-node single-line editing (event-name field, parameter fields) with caret blink, selection highlight, and key capture — proves the in-node editing + key-capture pattern; the multiline editor generalizes it.
- **`node.getAddonExtraFields()`:** the JSON extra-fields blob already persisting `script` (API-05). `lastError` persists the same way.
- **`PathmindBindings.build(...)`:** the authoritative `pathmind.*` function table — single source of truth for autosuggestion names (auto-derive, don't hardcode).

### Established Patterns
- **Immediate-mode rendering** with per-frame `AddonNodeContext` rebuild; addon body is **scissor-clipped** to node bounds (`NodeGraph` ~7419). Editor state must persist outside the per-frame context.
- **Addon extra-fields persistence:** schema-versioned opaque blob validated by Pathmind without knowing the schema (API-05).
- **`UITheme` color tokens** for consistent editor/error styling.
- **Render-only addon API today:** input routing is the new surface; no existing addon input hook to extend — it is net-new API (co-evolution).

### Integration Points
- **`com.pathmind.api.addon`:** add the input-routing interface (focus + key/char/mouse/scroll). Upstream-bound — match Pathmind conventions, keep version-agnostic (API-09).
- **`NodeGraph`:** route screen-level input to the focused addon node's input handler; track which addon node (if any) holds editor focus; ensure focused-editor input does not leak to graph interactions.
- **Addon `LuaScriptNodeRenderer`:** replace the read-only preview with the interactive editor (EditBoxWidget + gutter + error strip + autosuggestion popup) implementing the new input API; hold per-node editor state.
- **Addon executor/serializer:** surface the last-run error (message + line) to the node's persisted `lastError`; clear on next success.

</code_context>

<specifics>
## Specific Ideas

- **EditBoxWidget is the intended text widget** — the UAT checkpoint names it explicitly; in-game verification of its shortcut/keyboard-routing behavior is required (SC#5).
- **Leak-proofing is a first-class requirement:** while the editor is focused, typing/shortcuts must never reach the node graph (no accidental node move/delete/pan). This is the central UAT risk.
- **Autosuggestions are LSP-*style*, not an LSP:** in-process engine; deep-research (2026-06-13) ruled out embedding a real Lua LSP as over-engineering. Cover Lua keywords + stdlib + `pathmind.*` (auto-derived).
- **Both autosuggest triggers:** auto on `pathmind.` and manual Ctrl+Space. Enter accepts (consumed), Esc closes (consumed) — both consume the key to prevent leak.
- **Error strip is co-located in the node body** and node-persisted — no chat dependency (chat was Phase 2's channel).

</specifics>

<deferred>
## Deferred Ideas

- **Syntax highlighting** → v2 (LUA-V2-02).
- **Scope-aware completion** (locals, function params, nested-table member resolution, type inference) → v2; would build on a Lua AST (luaj/dingyi parser) rather than the v1 static-symbol engine.
- **Embedding a real Lua LSP (LuaLS) / external language server** → out of scope (research-confirmed over-engineering; native multi-binary subprocess).
- **Script hot-reload without graph restart** → v2 (LUA-V2-03).
- **Per-node editor configuration / resizable editor** → not pursued in v1 (fixed enlarged body).

</deferred>

---

*Phase: 3 — Script Node Editor + Autosuggestions*
*Context gathered: 2026-06-13*
