# Phase 3: Script Node Editor + Autosuggestions — Research

**Researched:** 2026-06-25
**Domain:** In-node multiline text editing (Minecraft `EditBoxWidget`), addon API input routing, prefix-match autosuggestion engine, node-persisted error state
**Confidence:** HIGH (all findings verified directly from source code or official Yarn documentation)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Editor lives inline in the node body (not a modal, not a focused overlay).
- Text-editing widget: Minecraft's `EditBoxWidget` (multiline). UAT checkpoint explicitly names EditBoxWidget.
- Input routing: extend the addon API with an interactive input hook. Addon owns the EditBoxWidget and editor state.
- Focus model: click to focus, Esc or click-away to blur. Focused editor must NOT leak input to the node graph.
- Editor state (EditBoxWidget instance, cursor, scroll, focus) lives per-node in the addon, not in the per-frame AddonNodeContext.
- Gutter: left column, right-aligned dimmed line numbers, subtle divider. Width grows with digit count.
- Node sizing: enlarged fixed body (~8–10 visible lines). Editor scrolls within the body; no auto-grow.
- No soft-wrap: horizontal scroll for long lines.
- Theme: default Minecraft font + Pathmind UITheme colors. No syntax highlighting in v1.
- Error strip: dedicated strip at the bottom of the node body. Persist via addon extra-fields blob. Cleared on next successful run.
- Error format: `⚠ Line N: <message>`, single line truncated, full message on hover.
- Autosuggestion trigger: BOTH auto on `pathmind.` AND manual Ctrl+Space.
- Autosuggestion source: in-process LSP-style engine (no external LSP).
- Symbol set: Lua keywords + stdlib (static) + `pathmind.*` names auto-derived from `PathmindBindings.build(...)`.
- Enter accepts suggestion (consumed, does NOT insert newline). Esc closes popup (consumed, does NOT blur editor).
- Popup: anchored under cursor token, name + signature, cap 6 rows, reuse `NavigatorChatSuggestions` pattern.

### Claude's Discretion
- Exact name/package and method shape of the new addon input-routing API interface.
- Exact per-node editor-state storage idiom in the addon (WeakHashMap vs id-keyed map, key type).
- Exact `EditBoxWidget` construction/config for 1.21.4 (sizing, scroll, max length).
- Gutter composited alongside EditBoxWidget (separate draw pass vs wrapper).
- Exact tokenizer regex and completion-ranking for the in-process engine; exact Lua keyword/stdlib list.
- Exact hover-tooltip mechanism for truncated error message.

### Deferred Ideas (OUT OF SCOPE)
- Syntax highlighting (v2, LUA-V2-02).
- Scope-aware completion (locals, function params, type inference).
- Embedding a real Lua LSP (sumneko LuaLS).
- Script hot-reload without graph restart (v2, LUA-V2-03).
- Per-node editor configuration / resizable editor.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EDIT-01 | Script node body contains a functional plain-text multiline editor (cursor movement, selection, scrolling, copy/paste) | EditBoxWidget verified in Yarn 1.21.4+build.8; keyPressed/charTyped/mouseClicked/mouseScrolled all present |
| EDIT-02 | Editor shows line numbers in a gutter | Custom draw pass alongside EditBoxWidget; gutter width formula verified from UI-SPEC constants |
| EDIT-03 | Last-run error (message + line) displayed co-located with the node | `node.getAddonExtraFields()` blob verified; Cobalt LuaError message format verified in CobaltVm.java |
| EDIT-04 | Editor offers simple prefix-match autosuggestions for the `pathmind.*` Lua API | `NavigatorChatSuggestions` pattern fully verified; `PathmindBindings.build(...)` function names verified |
</phase_requirements>

---

## Summary

Phase 3 extends Pathmind's addon API with an input-routing surface and implements the Lua Script node's inline editor against it. The two repos evolve together: Pathmind gains `AddonNodeInputHandler` (focus lifecycle + key/char/mouse/scroll forwarding) wired into `PathmindVisualEditorScreen`'s existing input-dispatch chain; the addon replaces the read-only `LuaScriptNodeRenderer` with an interactive editor that owns an `EditBoxWidget`, a custom gutter draw pass, an error strip, and an autosuggestion popup.

The central technical fact is that `EditBoxWidget` (Yarn name: `net.minecraft.client.gui.widget.EditBoxWidget`) exists in MC 1.21.4 as a multiline widget with a complete keyboard+mouse API. Its constructor takes `(TextRenderer, int x, int y, int width, int height, Text placeholder, Text message)`. It handles `keyPressed(int, int, int)`, `charTyped(char, int)`, `mouseClicked(double, double, int)` (inherited from `ScrollableTextFieldWidget`), and `mouseScrolled(double, double, double, double)` (inherited from `ScrollableWidget`). The addon calls these methods from the new input-routing hook; the hook returns `true` to prevent leaks to NodeGraph.

The key architectural constraint discovered during research: `NodeDimensionCalculator.recalculate` has no `NodeType.ADDON` branch — ADDON nodes fall through to `computeWidth`/`computeHeight` with default field-based logic, which produces wrong dimensions for the Script node. The addon must register a fixed body height via a new API surface on `AddonNodeDefinition` (a `bodyHeight(int)` builder method), and `NodeDimensionCalculator` must honor it when `type == NodeType.ADDON`. This is the primary new work on the Pathmind side, beyond the input-routing interface.

**Primary recommendation:** Add two things to the Pathmind addon API: (1) `AddonNodeInputHandler` interface for focus+input routing, and (2) `bodyHeight(int)` on `AddonNodeDefinition.Builder`. Wire both in `NodeDimensionCalculator`, `NodeGraph`, and `PathmindVisualEditorScreen`. The addon then implements `AddonNodeInputHandler` on `LuaScriptNodeRenderer` (which becomes the combined editor/renderer).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| EditBoxWidget lifecycle (create, focus, keyPressed, charTyped, scroll) | Addon (client) | — | Addon owns editor state per node; widget must not live in the per-frame context |
| Input leak prevention (return true from keyPressed/charTyped while focused) | Addon (client) via new API hook | Pathmind NodeGraph (forwarding gate) | Hook returns boolean; NodeGraph only routes to graph shortcuts when hook returns false |
| Node body height (128 px / 146 px) | Pathmind NodeDimensionCalculator | AddonNodeDefinition metadata | NodeDimensionCalculator must read bodyHeight from the definition; no ADDON branch currently exists |
| Gutter draw pass | Addon (client) — render method | — | Custom immediate-mode draw alongside EditBoxWidget's scissored rect |
| Error strip display | Addon (client) — render method | — | Reads `lastError` from per-node editor state (loaded from extraFields) |
| `lastError` persistence | Addon serializer | Pathmind extra-fields blob (pass-through) | Same mechanism as `script` field; `LuaNodeSerializer.serialize/deserialize` must round-trip `lastError` |
| `lastError` origination | Addon `CobaltVm.run` catch block | `LuaNodeExecutor.execute` future completion | Error message + line extracted from `LuaError.getMessage()` at `LuaError` catch site |
| Autosuggestion engine | Addon (client, in-process) | — | Locked decision: no external LSP |
| `pathmind.*` symbol names | `PathmindBindings.build(...)` (live table) | Static list fallback | Auto-derive from function names registered via `RegisteredFunction.bind` |
| Autosuggestion popup rendering | Addon (client) — render method | `NavigatorChatSuggestions` pattern | Same constants: WIDTH=220, ENTRY_HEIGHT=14, PADDING=4, panel bg `0x80000000` |
| Scissor clipping | Pathmind NodeGraph (`renderAddonNodeContent`) | — | Already clips addon body to node bounds at `context.enableScissor(x+1, y+18, x+width-1, y+height-1)` [NodeGraph.java:7419] |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `net.minecraft.client.gui.widget.EditBoxWidget` | MC 1.21.4 (Yarn) | Multiline text editor with cursor/selection/scroll/copy-paste | Native MC widget — keyboard shortcuts, scrolling, IME all handled internally |
| `net.minecraft.client.gui.DrawContext` | MC 1.21.4 (Yarn) | Immediate-mode rendering for gutter, error strip, popup | Same API used throughout Pathmind |
| `com.pathmind.ui.theme.UITheme` | Pathmind (current) | All color tokens | Required by UITheme Javadoc: "no hardcoded hex values in rendering code" |
| `org.lwjgl.glfw.GLFW` | Bundled with MC | Key code constants for Ctrl+Space, Enter, Esc detection | Used throughout NodeGraph and PathmindVisualEditorScreen |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `NavigatorChatSuggestions` pattern | Pathmind (current) | Popup constants + rendering idiom | Copy constants (WIDTH=220, ENTRY_HEIGHT=14, PADDING=4, PANEL_BACKGROUND=`0x80000000`, BORDER_HIGHLIGHT, selected row `0x503A3A3A`) exactly |
| `UIStyleHelper.drawTextCaretAtBaseline` | Pathmind (current) | Caret rendering at a baseline | Reuse the same method NodeGraph uses for event-name/parameter caret |
| `DrawContextBridge.drawBorderInLayer` | Pathmind (current) | Border drawing | Same pattern used for all node input boxes |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `EditBoxWidget` (multiline) | Custom hand-rolled multiline editor | Hand-rolling multiline cursor, selection, scrolling, IME, clipboard is ~1000 lines and UAT-risky. EditBoxWidget is locked by CONTEXT.md. |
| Per-node `WeakHashMap<Integer, EditorState>` keyed by node identity hashCode | `Map<Node, EditorState>` with weak reference | WeakHashMap on Node directly risks GC issues if Node is recreated; keying by `System.identityHashCode(node)` or a stable node-id is safer. Recommendation: `HashMap<String, EditorState>` keyed by a stable UUID generated once per node and stored in extraFields. |

**No new external packages are required.** All code is vanilla MC + existing Pathmind infrastructure + addon's existing Cobalt dependency.

---

## Package Legitimacy Audit

No new external packages are introduced in this phase. All dependencies are:
- Minecraft 1.21.4 (bundled — not installable)
- Existing Pathmind `com.pathmind.*` infrastructure
- Existing addon Cobalt VM (already in the addon's dependency graph from Phase 2)

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious:** none

---

## Architecture Patterns

### System Architecture Diagram

```
PathmindVisualEditorScreen.keyPressed(keyCode, scanCode, mods)
  │
  ├── [existing gates: nodeSearch, popups, settings, overlays...]
  │
  ├── nodeGraph.handleAddonInputKeyPressed(keyCode, mods)   ← NEW
  │     │
  │     ├── focusedAddonNode == null? → return false (not consumed)
  │     │
  │     └── def.getInputHandler().keyPressed(focusedAddonNode, ctx, keyCode, mods)
  │           │
  │           ├── popup open + Enter → accept suggestion, return true
  │           ├── popup open + Esc  → close popup, return true (NOT blur)
  │           ├── popup open + ↑↓   → move selectedIndex, return true
  │           ├── Esc (no popup)    → blur editor, return false (NodeGraph sees Esc for screen close)
  │           │    NOTE: blur before returning so NodeGraph Esc closes screen as normal
  │           └── any other key while focused → EditBoxWidget.keyPressed(...), return true
  │
  ├── [existing: handleEventNameKeyPressed, handleParameterKeyPressed, ...]
  │
  └── [existing: graph shortcuts, Delete node, Esc screen close]

PathmindVisualEditorScreen.charTyped(chr, mods)
  │
  └── nodeGraph.handleAddonInputCharTyped(chr, mods)        ← NEW
        │ focusedAddonNode != null → EditBoxWidget.charTyped(chr, mods); check pathmind. trigger; return true
        └── return false (not focused)

PathmindVisualEditorScreen.mouseClicked(mouseX, mouseY, button)
  │
  └── handleNodeGraphClick → nodeGraph.handleAddonNodeClick(node, mouseX, mouseY)  ← NEW
        │ ADDON node clicked in body area → focus/blur logic; EditBoxWidget.mouseClicked; return true
        └── not ADDON body → return false (existing drag/select flow)

PathmindVisualEditorScreen.mouseScrolled(...)
  └── nodeGraph.handleAddonInputMouseScrolled(mouseX, mouseY, h, v)  ← NEW
        │ focusedAddonNode != null && mouse over editor area → EditBoxWidget.mouseScrolled; return true
        └── return false

LuaScriptNodeRenderer (replaces read-only preview)
  implements AddonNodeBodyRenderer + AddonNodeInputHandler   ← NOW BOTH
  │
  ├── render(ctx, draw, x, y, w, h)
  │     ├── getOrCreateEditorState(ctx.getNodeId())
  │     ├── fill editor background (focused vs unfocused)
  │     ├── draw gutter column (line numbers, divider)
  │     ├── position + render EditBoxWidget (scissored by NodeGraph)
  │     ├── draw error strip (if lastError != null)
  │     └── render suggestion popup (if open, z-layered above node)
  │
  └── keyPressed / charTyped / mouseClicked / mouseScrolled / onFocusGained / onFocusLost
        └── delegate to EditorState (which owns EditBoxWidget)
```

### Recommended Project Structure

**Pathmind (additions to existing):**
```
common/src/main/java/com/pathmind/api/addon/
├── AddonNodeBodyRenderer.java        (existing — unchanged)
├── AddonNodeContext.java             (add nodeId field)
├── AddonNodeDefinition.java          (add bodyHeight field + builder method)
└── AddonNodeInputHandler.java        (NEW — input-routing interface)

common/src/main/java/com/pathmind/nodes/
└── NodeDimensionCalculator.java      (add ADDON branch reading bodyHeight from definition)

common/src/main/java/com/pathmind/ui/graph/
└── NodeGraph.java                    (add focusedAddonNode field + handleAddonInput* methods)

common/src/compat/legacy/base/java/com/pathmind/screen/
└── PathmindVisualEditorScreen.java   (wire handleAddonInput* calls into keyPressed/charTyped/mouseClicked/mouseScrolled)
```

**Addon (additions to existing):**
```
src/main/java/com/mrmysterium/pathmindlua/
├── LuaScriptNodeRenderer.java        (replace read-only preview; implement both interfaces)
├── EditorState.java                  (NEW — per-node: EditBoxWidget, caretPos, scroll, focus, lastError, suggestionEngine)
├── SuggestionEngine.java             (NEW — in-process prefix-match tokenizer + symbol table)
└── LuaNodeSerializer.java            (extend to round-trip lastError field)
```

### Pattern 1: AddonNodeInputHandler (new API interface)

**What:** A second optional interface on `AddonNodeDefinition` (alongside `AddonNodeBodyRenderer`) that handles input routing for an interactive addon node body.

**When to use:** Any addon that embeds an interactive widget in the node body.

**Shape (recommended — Claude's discretion):**
```java
// Source: designed to match Pathmind API conventions (boolean-returning input handlers)
// File: common/src/main/java/com/pathmind/api/addon/AddonNodeInputHandler.java
package com.pathmind.api.addon;

/**
 * Optional input-routing hook for addon nodes with interactive bodies (Phase 3 API extension).
 *
 * Pathmind calls these methods when a focused addon node needs to process screen-level input.
 * Return true to consume the event (prevent NodeGraph from processing it); return false to pass through.
 *
 * Focus lifecycle: NodeGraph tracks which addon node (if any) holds focus via an internal
 * focusedAddonNode field. The addon calls back to focus/blur via NodeGraph methods exposed
 * through a thin focus-callback API or directly through the AddonNodeContext.
 */
public interface AddonNodeInputHandler {
    /** Called when the user clicks inside the addon body area. Return true if the click was handled. */
    boolean mouseClicked(AddonNodeContext ctx, double mouseX, double mouseY, int button);
    /** Called for keyboard key presses while this node holds focus. Return true to consume. */
    boolean keyPressed(AddonNodeContext ctx, int keyCode, int scanCode, int modifiers);
    /** Called for printable character input while this node holds focus. Return true to consume. */
    boolean charTyped(AddonNodeContext ctx, char chr, int modifiers);
    /** Called for scroll events while this node holds focus. Return true to consume. */
    boolean mouseScrolled(AddonNodeContext ctx, double mouseX, double mouseY,
                          double horizontalAmount, double verticalAmount);
    /** Called when this node gains keyboard focus (click inside editor area). */
    void onFocusGained(AddonNodeContext ctx);
    /** Called when this node loses keyboard focus (Esc, click away, or graph event). */
    void onFocusLost(AddonNodeContext ctx);
}
```

Add to `AddonNodeDefinition.Builder`:
```java
// File: common/src/main/java/com/pathmind/api/addon/AddonNodeDefinition.java
private AddonNodeInputHandler inputHandler = null;
private int bodyHeight = -1; // -1 = let NodeDimensionCalculator use default

public Builder inputHandler(AddonNodeInputHandler v) { this.inputHandler = v; return this; }
public Builder bodyHeight(int px) { this.bodyHeight = px; return this; }
```

### Pattern 2: NodeDimensionCalculator ADDON branch

**What:** Add an early-return for `NodeType.ADDON` that reads `bodyHeight` from the registered definition.

**Key finding:** Currently `NodeDimensionCalculator.recalculate` has branches for START, STICKY_NOTE, TEMPLATE/CUSTOM_NODE, and then falls through to `computeWidth`/`computeHeight` with default field-based logic. `NodeType.ADDON` nodes get wrong dimensions (body height calculated as if they have no content). This must be fixed for the Script node to display at 128/146 px.

```java
// File: common/src/main/java/com/pathmind/nodes/NodeDimensionCalculator.java
// Add after the TEMPLATE/CUSTOM_NODE branch, before computeWidth/computeHeight
if (type == NodeType.ADDON) {
    String addonTypeId = node.getAddonTypeId();
    AddonNodeDefinition def = addonTypeId != null
        ? NodeTypeRegistry.INSTANCE.definitionFor(addonTypeId)
        : null;
    int bh = (def != null && def.getBodyHeight() > 0)
        ? def.getBodyHeight()
        : Node.TEMPLATE_NODE_HEIGHT; // fallback to 108 px
    // Width: fixed at TEMPLATE_NODE_WIDTH (160 px) for all addon nodes in v1
    layoutState.setSize(Node.TEMPLATE_NODE_WIDTH, bh);
    return false;
}
```

### Pattern 3: EditBoxWidget construction and wiring

**What:** Construct the `EditBoxWidget` with explicit position (0,0 — positioned by the render call), set max length, set change listener.

**Key facts verified from Yarn 1.21.4+build.8:**
- Class: `net.minecraft.client.gui.widget.EditBoxWidget`
- Constructor: `EditBoxWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text placeholder, Text message)`
- `setText(String text)` — sets text, moves cursor to end
- `getText()` — returns current text
- `keyPressed(int keyCode, int scanCode, int modifiers)` — returns boolean
- `charTyped(char chr, int modifiers)` — returns boolean
- `mouseClicked(double mouseX, double mouseY, int button)` — inherited from `ScrollableTextFieldWidget`, returns boolean
- `mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)` — inherited from `ScrollableWidget`, returns boolean
- `setFocused(boolean focused)` — sets focus state (inherited via `ClickableWidget`)
- `setChangeListener(Consumer<String> listener)` — fires on every text change
- `setMaxLength(int maxLength)` — limits character count
- Rendering: `renderWidget(DrawContext, int mouseX, int mouseY, float delta)` inherited from `ScrollableTextFieldWidget`; OR call `renderContents` + `renderOverlay` directly

**Construction in EditorState:**
```java
// Source: Yarn 1.21.4+build.8 Fabric docs (verified)
// File: pathmind-lua/src/main/java/.../EditorState.java
private EditBoxWidget buildWidget(TextRenderer tr, int width, int height) {
    EditBoxWidget w = new EditBoxWidget(
        tr,
        0, 0,          // x/y positioned at render time
        width, height,
        Text.empty(),  // placeholder (we draw our own)
        Text.empty()   // initial message
    );
    w.setMaxLength(Integer.MAX_VALUE); // no character limit for scripts
    w.setFocused(false);
    w.setChangeListener(this::onTextChanged);
    return w;
}
```

**Critical gotcha — position must be set before each `renderWidget` call.** `EditBoxWidget` inherits position from `ClickableWidget` (`setX`, `setY`). The addon calls `widget.setX(gutterWidth + x)` and `widget.setY(y + 18)` each render frame, then calls `widget.renderWidget(draw, mouseX, mouseY, delta)`. The scissor clip applied by `NodeGraph.renderAddonNodeContent` keeps any overflow inside the node bounds.

**Critical gotcha — `mouseClicked` coordinates.** The widget's `mouseClicked` tests `isMouseOver(mouseX, mouseY)` internally using its stored x/y/width/height. Call it only when the click is inside the editor area (not the gutter, not the error strip), and pass screen coordinates that match the widget's positioned x/y.

### Pattern 4: Per-node EditorState storage

**What:** A `HashMap<String, EditorState>` in `LuaScriptNodeRenderer`, keyed by a stable node identity string stored in the node's extra-fields blob.

**Recommendation (Claude's discretion):** Use a node-UUID approach: on first serialize, write a `"_node_id"` field (UUID string) to extra-fields. `LuaNodeSerializer.deserialize` reads it back; `AddonNodeContext` exposes it via a new `getNodeId()` method (backed by the extra-fields blob). The renderer keys its `HashMap` on this UUID. This survives node reopen, tab switch, and preset reload.

**Alternative considered:** `WeakHashMap<Node, EditorState>` — avoids the UUID indirection but requires Node instances to be stable (they may be recreated on load). UUID approach is safer.

```java
// File: pathmind-lua/src/main/java/.../LuaScriptNodeRenderer.java
private final Map<String, EditorState> editorStates = new HashMap<>();

private EditorState getOrCreate(AddonNodeContext ctx, TextRenderer tr, int width, int height) {
    String nodeId = ctx.getNodeId(); // new field on AddonNodeContext
    if (nodeId == null) nodeId = "default"; // fallback for nodes with no id yet
    return editorStates.computeIfAbsent(nodeId,
        id -> new EditorState(tr, width, height, ctx.getScriptText(), ctx.getLastError()));
}
```

`EditorState` fields: `EditBoxWidget widget`, `boolean focused`, `String lastError`, `int lastErrorLine`, `SuggestionEngine suggestions`, `int suggestionSelectedIndex`, `boolean suggestionOpen`.

### Pattern 5: NodeGraph focus tracking and input forwarding

**What:** Add a `focusedAddonNode` field to `NodeGraph`; add `handleAddonInput*` methods that the screen's existing overrides call first.

**Where to insert in `PathmindVisualEditorScreen.keyPressed`:**
```java
// Insert BEFORE the existing nodeGraph.handleStopTargetKeyPressed call (line ~2314)
// File: common/src/compat/legacy/base/java/com/pathmind/screen/PathmindVisualEditorScreen.java
if (nodeGraph.handleAddonInputKeyPressed(keyCode, modifiers)) {
    return true;
}
```

**Where to insert in `charTyped`:**
```java
// Insert BEFORE nodeGraph.handleStopTargetCharTyped (line ~2445)
if (nodeGraph.handleAddonInputCharTyped(chr, modifiers)) {
    return true;
}
```

**Where to insert in `mouseScrolled`:** After the existing settings popup scroll and before `nodeGraph.zoomByScroll`.

**Where to insert in `handleNodeGraphClick`:** After the existing `handleEventNameFieldClick` check (line ~1705) and before `nodeGraph.stopAmountEditing(...)`, add:
```java
if (clickedNode.getType() == NodeType.ADDON) {
    if (nodeGraph.handleAddonNodeMouseClicked(clickedNode, (int)mouseX, (int)mouseY, button)) {
        nodeGraph.selectNode(clickedNode);
        return true;
    }
}
```

**Blur on click-away:** The existing `nodeGraph.selectNode(null)` call (line ~1778, empty space click) must also call `nodeGraph.blurFocusedAddonNode()`. Similarly, starting any other editing operation (`stopAmountEditing`, etc.) must also call `blurFocusedAddonNode`.

**NodeGraph fields and methods to add:**
```java
// File: common/src/main/java/com/pathmind/ui/graph/NodeGraph.java
private Node focusedAddonNode = null;

public boolean isFocusedAddonNode(Node node) { return node == focusedAddonNode; }
public void focusAddonNode(Node node) { blurFocusedAddonNode(); this.focusedAddonNode = node; }
public void blurFocusedAddonNode() { /* notify handler.onFocusLost; */ this.focusedAddonNode = null; }

public boolean handleAddonInputKeyPressed(int keyCode, int modifiers) {
    if (focusedAddonNode == null) return false;
    AddonNodeInputHandler handler = getInputHandler(focusedAddonNode);
    if (handler == null) return false;
    AddonNodeContext ctx = buildContext(focusedAddonNode);
    return handler.keyPressed(ctx, keyCode, -1, modifiers);
}
// Similar for charTyped, mouseScrolled, handleAddonNodeMouseClicked
```

### Pattern 6: Gutter draw pass

**What:** The gutter is drawn BEFORE the `EditBoxWidget` render, in the same `render(ctx, draw, x, y, w, h)` call.

```java
// File: pathmind-lua/src/main/java/.../LuaScriptNodeRenderer.java  
// Source: UI-SPEC constants
private static final int LINE_HEIGHT = 10; // fontHeight 9 + 1 px leading
private static final int EDITOR_TOP_OFFSET = 18; // node header height

private int computeGutterWidth(int lineCount) {
    int digits = lineCount < 10 ? 1 : lineCount < 100 ? 2 : 3;
    return digits * 6 + 8; // 6 px per digit char width + 4 px left + 4 px right padding
}

private void drawGutter(DrawContext draw, TextRenderer tr, int x, int y, int gutterWidth,
                        int editorH, int lineCount, int scrollOffsetLines) {
    draw.fill(x, y + EDITOR_TOP_OFFSET, x + gutterWidth, y + EDITOR_TOP_OFFSET + editorH,
              UITheme.BACKGROUND_SECONDARY);
    // Right border of gutter (2 px wide = xs token from UI-SPEC)
    draw.fill(x + gutterWidth - 1, y + EDITOR_TOP_OFFSET,
              x + gutterWidth, y + EDITOR_TOP_OFFSET + editorH, UITheme.BORDER_SUBTLE);
    int visibleLines = editorH / LINE_HEIGHT;
    for (int i = 0; i < visibleLines; i++) {
        int lineNum = scrollOffsetLines + i + 1;
        if (lineNum > lineCount) break;
        String label = String.valueOf(lineNum);
        int labelX = x + gutterWidth - 4 - tr.getWidth(label); // right-aligned, 4 px padding
        int labelY = y + EDITOR_TOP_OFFSET + i * LINE_HEIGHT;
        draw.drawTextWithShadow(tr, label, labelX, labelY, UITheme.TEXT_TERTIARY);
    }
}
```

**Scroll offset sync:** `EditBoxWidget` handles internal scroll; to sync the gutter, call `widget.getScrollY()` (inherited from `ScrollableWidget`) and divide by `LINE_HEIGHT` to get the first visible line index. This gives exact gutter alignment. [ASSUMED — `getScrollY()` method name may differ in 1.21.4; verify via codebase access or test].

### Pattern 7: Autosuggestion engine

**What:** An in-process `SuggestionEngine` that tokenizes the line left of the cursor and returns prefix-matching completions.

**Tokenizer approach:**
1. Get current line text left of cursor.
2. If text ends with `pathmind.` → show ALL `pathmind.*` symbols (member-access trigger, auto-open).
3. Otherwise, extract the last token (regex `[a-zA-Z_][a-zA-Z0-9_.]*$`) as the prefix.
4. If prefix contains `.` → split on last `.`, use part after `.` as sub-prefix, filter `pathmind.*` symbols matching sub-prefix.
5. Otherwise → filter all symbols (Lua keywords + stdlib + pathmind.*) by `symbol.startsWith(prefix)`.

**Symbol set auto-derived from `PathmindBindings`:**
```java
// File: pathmind-lua/src/main/java/.../SuggestionEngine.java
// The binding names are known at compile time for v1; derive from PathmindBindings.build()
// by calling it with a dummy state+runtime and iterating the table keys. Or: use the
// static list from UI-SPEC (same names, same order, derived once at class load).
// Verified names from PathmindBindings.java:
//   "getVar", "setVar", "moveTo", "getPosition", "getInventory", "getBlock"
// Signatures (for display) from UI-SPEC and PathmindBindings source:
private static final List<CompletionEntry> PATHMIND_SYMBOLS = List.of(
    new CompletionEntry("getBlock",     "(x, y, z)"),
    new CompletionEntry("getInventory", "()"),
    new CompletionEntry("getPosition",  "()"),
    new CompletionEntry("getVar",       "(name)"),
    new CompletionEntry("moveTo",       "(x, y, z)"),
    new CompletionEntry("setVar",       "(name, value)")
);
```

**Lua keywords (from UI-SPEC, static list):**
`and`, `break`, `do`, `else`, `elseif`, `end`, `false`, `for`, `function`, `goto`, `if`, `in`, `local`, `nil`, `not`, `or`, `repeat`, `return`, `then`, `true`, `until`, `while`

**Lua stdlib (from UI-SPEC, minimal for v1):**
`print`, `tostring`, `tonumber`, `type`, `pairs`, `ipairs`, `next`, `select`, `unpack`, `table.insert`, `table.remove`, `table.concat`, `string.format`, `string.len`, `string.sub`, `math.floor`, `math.ceil`, `math.abs`, `math.max`, `math.min`

**Acceptance logic:** Replace the incomplete token left of the cursor with the selected function name (name only, no signature). For `pathmind.getB` accepting `getBlock`: replace `getB` with `getBlock`, cursor placed after.

### Pattern 8: `lastError` persistence and origination

**What:** Persist `lastError` (message string + line number integer) in the addon extra-fields blob alongside `script`.

**Where lastError originates:** `CobaltVm.run` catch block at [CobaltVm.java:177-191]:
```java
} catch (LuaError e) {
    String msg = e.getMessage(); // format: "script:N: message" when chunk name is "@script"
    // ...
}
```
Cobalt LuaError message format when chunk name is `"@script"` (LoadState.load call): `"script:N: <message>"` where N is the 1-based Lua line number. Parse this with `msg.matches("script:(\\d+):.*")`.

**Persistence flow:**
1. `CobaltVm.run` catches `LuaError`, extracts line number from message, completes future with `NodeResult.FAILURE`.
2. `LuaNodeExecutor.execute` — the future's `whenComplete` needs access to the node's extra-fields to write `lastError`. **Problem:** the executor does not currently have a write path back to `node.getAddonExtraFields()`. Resolution: add an `errorCallback` (a `BiConsumer<String, Integer>`) to `AddonNodeContext` that Pathmind wires to the node's extra-fields write on the game thread. OR: simpler — have `CobaltVm` return a richer `NodeResult` that carries the error info; `LuaNodeExecutor` writes it to context. See open question #1 below.
3. `LuaNodeSerializer.deserialize` reads `lastError` and `lastErrorLine` from extra-fields, sets them on `AddonNodeContext`.
4. `LuaScriptNodeRenderer.render` reads `lastError` from the per-node `EditorState` (loaded from context on first access).
5. On next successful run: `LuaNodeSerializer.serialize` writes `lastError: null` (or omits the key).

**New `LuaNodeSerializer` fields to round-trip:**
```java
// Serialize
fields.put("lastError", ctx.getLastError());       // null if no error
fields.put("lastErrorLine", ctx.getLastErrorLine()); // 0 if no error

// Deserialize
String lastError = fields.get("lastError") instanceof String s ? s : null;
int lastErrorLine = fields.get("lastErrorLine") instanceof Number n ? n.intValue() : 0;
ctx.setLastError(lastError);
ctx.setLastErrorLine(lastErrorLine);
```

**New `AddonNodeContext` fields needed:** `String lastError`, `int lastErrorLine` (or `String lastError`, `String lastErrorLineString` to avoid GSON double-cast).

### Anti-Patterns to Avoid

- **Storing EditBoxWidget in `AddonNodeContext`:** The context is rebuilt from scratch each frame. The widget would be garbage-collected immediately. Editor state must live in the addon's own static map.
- **Calling `EditBoxWidget.renderWidget` outside the scissor clip:** NodeGraph already applies `context.enableScissor(x+1, y+18, x+width-1, y+height-1)` at [NodeGraph.java:7419] before calling `renderer.render`. Do not call `disableScissor` inside the renderer.
- **Routing keyPressed to EditBoxWidget when editor is NOT focused:** NodeGraph has global shortcuts (Delete node, Esc screen close, arrow-key panning). If the hook returns `true` for non-focused nodes, all graph shortcuts break.
- **Returning `false` from `keyPressed` while editor IS focused:** Lets NodeGraph's `Delete` key delete the selected node while the user is typing. Must return `true` for ALL keypresses while focused (including letters, Backspace, Delete).
- **Rendering the popup above the scissor clip:** The popup must be rendered AFTER `context.disableScissor()` in `renderAddonNodeContent`, or NodeGraph must call a second popup-render pass after disabling scissor. This is a rendering-order issue unique to the popup (see pitfall #4 below).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Multiline text cursor, selection, scrolling, copy/paste | Custom text editor | `EditBoxWidget` (MC 1.21.4) | IME, platform clipboard, shift-selection, Ctrl+A/C/V/X/Z all handled; ~500 lines of correct platform code |
| Caret rendering | Custom caret drawing | `UIStyleHelper.drawTextCaretAtBaseline` | Already used in NodeGraph event-name / parameter caret at lines 3853, 3939; blinks at same COORDINATE_CARET_BLINK_INTERVAL_MS (500 ms) |
| Suggestion prefix filtering | Custom trie | Simple `String.startsWith` over the static symbol list | List is ≤50 entries; O(n) scan is imperceptible; trie is over-engineering for v1 |
| Border drawing | Custom fill calls | `DrawContextBridge.drawBorderInLayer` | Already used throughout NodeGraph for node input boxes |

**Key insight:** The Pathmind codebase already handles all in-node editing patterns. This phase generalizes the existing pattern to multiline + addon-facing rather than inventing new ones.

---

## Common Pitfalls

### Pitfall 1: EditBoxWidget position must be set before render AND before mouse hit-tests

**What goes wrong:** Widget renders at (0,0), `mouseClicked`/`isMouseOver` fail because the stored x/y is stale.
**Why it happens:** `EditBoxWidget` inherits `ClickableWidget` fields `x`, `y`, `width`, `height`. These must be updated every frame before rendering and before any mouse-hit call.
**How to avoid:** In `EditorState.render(...)`, call `widget.setX(...)` and `widget.setY(...)` with the current screen coordinates BEFORE calling `widget.renderWidget(...)`. In `mouseClicked`, set position BEFORE calling `widget.mouseClicked(...)`.
**Warning signs:** Clicks inside the editor area have no effect; cursor does not appear at click position.

### Pitfall 2: NodeGraph `Delete` key leaks through focused editor

**What goes wrong:** User presses Backspace to delete a character; instead the selected node is deleted.
**Why it happens:** `PathmindVisualEditorScreen.keyPressed` checks `GLFW_KEY_DELETE || GLFW_KEY_BACKSPACE` at line 2359 AFTER the `handleAddonInput*` chain. If the hook doesn't return `true`, the key falls through.
**How to avoid:** `handleAddonInputKeyPressed` must return `true` for ALL key codes when `focusedAddonNode != null` — not just for known keys. The only exception is Esc when there is no popup open (blur editor, then return `false` so the screen closes on a second Esc, which is the intended UX for Esc-to-exit). Actually: per the interaction contract, Esc while focused (no popup) blurs the editor only — screen close is a separate Esc press. Return `true` from the first Esc (consume it) after blurring.
**Warning signs:** Pressing Backspace in the editor deletes the node; pressing arrow keys pans the graph.

### Pitfall 3: EditBoxWidget `mouseClicked` receives wrong coordinates

**What goes wrong:** Clicks inside the editor area do not move the cursor; clicking in the gutter area moves cursor to wrong position.
**Why it happens:** The widget expects screen coordinates in the range [widget.x, widget.x+widget.width]. If you pass world coordinates (before camera transform) or gutter-included coordinates, the click lands at the wrong logical position.
**How to avoid:** (a) Set widget position to screen coords before calling mouseClicked. (b) Only route `mouseClicked` to the widget when `mouseX >= gutterRight` (click is in the text area, not the gutter). (c) Pass the same mouseX/mouseY you received from the screen (screen coords, not world coords).
**Warning signs:** Cursor always jumps to line start on click; clicks in gutter produce text cursor moves.

### Pitfall 4: Autosuggestion popup is clipped by node scissor

**What goes wrong:** Popup is invisible or partially clipped.
**Why it happens:** `NodeGraph.renderAddonNodeContent` calls `context.enableScissor(x+1, y+18, x+width-1, y+height-1)` at [NodeGraph.java:7419] before invoking `renderer.render`. The popup renders INSIDE the scissor rect and is clipped at the node's bottom edge.
**How to avoid:** The popup must be rendered AFTER `context.disableScissor()`. Two options: (a) Split rendering into two passes — the renderer stores popup paint data during the clipped pass, and NodeGraph calls a second `renderer.renderUnclipped(...)` method after `disableScissor()`. (b) Have NodeGraph call `context.disableScissor()` AFTER the main render, then call `renderer.renderPopup(...)` for any pending popup. Option (b) is cleaner and mirrors how other overlays work. Add `void renderOverlay(AddonNodeContext ctx, DrawContext draw, int x, int y, int w, int h)` to the input handler or a new interface.
**Warning signs:** Popup appears cropped at the node bottom; popup disappears when cursor is on the last visible line.

### Pitfall 5: Caret blink interval mismatch

**What goes wrong:** Caret blinks at a different rate than other Pathmind input fields, breaking visual consistency.
**Why it happens:** `EditBoxWidget` has its own blink timer internally; Pathmind's `updateEventNameCaretBlink` uses `COORDINATE_CARET_BLINK_INTERVAL_MS = 500 ms` (NodeGraph.java:229). The UI-SPEC says 530 ms (from earlier research).
**How to avoid:** `EditBoxWidget`'s caret rendering is internal — it uses Minecraft's own blink calculation. Do not fight it. Accept the slight mismatch, OR disable the internal caret rendering by subclassing and overriding `renderCursor`/`renderContents`. For v1: accept EditBoxWidget's native blink and move on (it is close enough and UAT-invisible).
**Warning signs:** None visible — this is a cosmetic consistency note only.

### Pitfall 6: `lastError` write-back path

**What goes wrong:** Error is logged to chat (Phase 2 channel) but never persisted to `addonExtraFields`, so the error strip never shows.
**Why it happens:** `CobaltVm.run` completes the future with `NodeResult.FAILURE`; `LuaNodeExecutor.execute` receives this, but neither has a write path to the node's extra-fields. The extra-fields write requires game-thread access.
**How to avoid:** Extend `NodeResult` to carry optional error metadata (`NodeResult.failure(String errorMsg, int errorLine)`) or add an error consumer callback to `AddonNodeContext` that Pathmind wires to the node's extra-fields updater. The callback runs the extra-fields write on the game thread via `MinecraftClient.getInstance().execute(...)`. This is the cleanest design. Details are Claude's discretion during planning.
**Warning signs:** Error strip never appears even after a script error; last error always null on reopen.

### Pitfall 7: GUI-scale pixel math

**What goes wrong:** Editor area appears 2× too large or text renders at wrong positions at non-default GUI scales.
**Why it happens:** Minecraft's `DrawContext` methods take scaled pixels (GUI-scale-aware coordinates), but `textRenderer.getWidth(...)` also returns GUI-scale pixels. These are consistent — no manual scaling needed as long as you work in GUI-scale pixel space throughout.
**How to avoid:** Never multiply coordinates by `client.options.getGuiScale().getValue()` manually. Work in the same coordinate space as `DrawContext` (which is already scale-aware). `EditBoxWidget.setX/setY` also expects these coordinates.
**Warning signs:** UI looks correct at GUI scale 2 but breaks at 1 or 3.

---

## Code Examples

### EditBoxWidget construction (Yarn 1.21.4)

```java
// Source: https://maven.fabricmc.net/docs/yarn-1.21.4+build.8/net/minecraft/client/gui/widget/EditBoxWidget.html
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

// In EditorState constructor:
this.widget = new EditBoxWidget(
    textRenderer,
    0, 0,               // position — set per-frame in render
    editorWidth,        // width = nodeWidth - 2 borders - gutterWidth
    editorHeight,       // 108 px (UI-SPEC EDITOR_BODY_HEIGHT)
    Text.empty(),       // placeholder component (we draw our own -- click to edit)
    Text.of(initialScript != null ? initialScript : "")
);
this.widget.setMaxLength(Integer.MAX_VALUE);
this.widget.setFocused(false);
this.widget.setChangeListener(text -> this.dirty = true);
```

### Rendering the editor (per-frame render call)

```java
// File: pathmind-lua/src/main/java/.../LuaScriptNodeRenderer.java
// Source: Pathmind idioms verified from NodeGraph.java rendering methods
@Override
public void render(AddonNodeContext ctx, DrawContext draw, int x, int y, int width, int height) {
    TextRenderer tr = MinecraftClient.getInstance().textRenderer;
    EditorState state = getOrCreate(ctx, tr, width, height);

    // 1. Gutter
    int gutterWidth = computeGutterWidth(state.lineCount());
    drawGutter(draw, tr, x, y, gutterWidth, height - 18 /*headerH*/, state.lineCount(), state.scrollLine());

    // 2. Editor background
    int editorX = x + gutterWidth;
    int editorW = width - gutterWidth;
    int editorY = y + 18; // below header
    int editorH = height - 18 - (state.hasError() ? 18 : 0);
    int bg = state.focused ? UITheme.NODE_INPUT_BG_ACTIVE : UITheme.BACKGROUND_INPUT;
    draw.fill(editorX, editorY, editorX + editorW, editorY + editorH, bg);
    DrawContextBridge.drawBorderInLayer(draw, editorX, editorY, editorW, editorH,
        state.focused ? UITheme.BORDER_FOCUS : UITheme.BORDER_SUBTLE);

    // 3. Position widget and render (scissor already active from NodeGraph)
    state.widget.setX(editorX);
    state.widget.setY(editorY);
    state.widget.setWidth(editorW - 2);  // 1 px border each side
    // NOTE: widget.setHeight(...) may not exist; construction height is used
    state.widget.renderWidget(draw, 0, 0, 0f); // mouseX/Y/delta not needed for static render

    // 4. Error strip (when present)
    if (state.hasError()) {
        drawErrorStrip(draw, tr, x, y + 18 + editorH, width, state);
    }

    // 5. Placeholder (when empty + unfocused)
    if (!state.focused && state.widget.getText().isBlank()) {
        draw.drawTextWithShadow(tr, "-- click to edit",
            editorX + 4, editorY + 2, UITheme.TEXT_TERTIARY);
    }
}
```

### Autosuggestion prefix match

```java
// File: pathmind-lua/src/main/java/.../SuggestionEngine.java
// Source: verified logic from NavigatorChatSuggestions.java (same pattern)
public List<CompletionEntry> getSuggestions(String lineBeforeCursor) {
    if (lineBeforeCursor == null) return List.of();
    // Auto-trigger: pathmind. at end of line
    if (lineBeforeCursor.endsWith("pathmind.")) {
        return PATHMIND_SYMBOLS; // all pathmind.* symbols
    }
    // Extract last token
    java.util.regex.Matcher m = TOKEN_PATTERN.matcher(lineBeforeCursor);
    String token = m.find() ? m.group() : "";
    if (token.isBlank()) return List.of();
    // Member-access: "pathmind.getB" → filter pathmind symbols by "getB"
    if (token.startsWith("pathmind.")) {
        String sub = token.substring("pathmind.".length()).toLowerCase(Locale.ROOT);
        return PATHMIND_SYMBOLS.stream()
            .filter(e -> e.name().toLowerCase(Locale.ROOT).startsWith(sub))
            .toList();
    }
    // Plain prefix: filter all symbols
    String lower = token.toLowerCase(Locale.ROOT);
    return ALL_SYMBOLS.stream()
        .filter(e -> e.name().toLowerCase(Locale.ROOT).startsWith(lower))
        .limit(6)
        .toList();
}
// TOKEN_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.]*$")
```

### NavigatorChatSuggestions constants (verified from source)

```java
// Source: NavigatorChatSuggestions.java (verified from codebase)
// All popup constants are exact:
private static final int WIDTH = 220;
private static final int ENTRY_HEIGHT = 14;
private static final int PADDING = 4;
private static final int PANEL_BACKGROUND = 0x80000000;
// Border: UITheme.BORDER_HIGHLIGHT (0xFF676767)
// Selected row fill: 0x503A3A3A
// Label color: UITheme.TEXT_HEADER (0xFFF5F4EE)
// Hint color: UITheme.TEXT_SECONDARY (0xFFA0A39C)
// Label x offset: commandX = popupX + 6
// Hint x offset: min(popupX + WIDTH - 8, commandX + tr.getWidth(label) + 12)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Addon render-only (`AddonNodeBodyRenderer`) | Addon render + input routing (`AddonNodeInputHandler`) | This phase | Enables truly interactive addon node bodies |
| Node body height: undefined for ADDON type | Node body height: declared in `AddonNodeDefinition.bodyHeight` | This phase | Script node renders at correct 128/146 px |
| `lastError` surfaced to chat only (Phase 2) | `lastError` persisted in node + displayed in error strip | This phase | Error is co-located and survives reopen |
| Read-only 3-line script preview | Full `EditBoxWidget`-backed interactive editor | This phase | User can author scripts in-game |

**Deprecated/outdated in this phase:**
- `LuaScriptNodeRenderer` (the stateless read-only 3-line preview): fully replaced by the new interactive renderer.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `widget.getScrollY()` (inherited from `ScrollableWidget`) returns the current vertical scroll in pixels, usable to compute gutter first-visible-line | Pattern 6 | If method name differs in 1.21.4, gutter line numbers scroll out of sync with code; fix: compute line index from EditBoxWidget.getText() up to cursor instead |
| A2 | `EditBoxWidget.renderWidget(DrawContext, int, int, float)` is the correct render entry point and respects the widget's stored x/y/width/height | Pattern 3 | If render uses different coordinates or ignores stored position, editor renders at (0,0); fix: call renderContents + renderOverlay directly |
| A3 | Cobalt `LuaError.getMessage()` for a script chunk named `"@script"` produces `"script:N: message"` format consistently | Pattern 8 | If format differs, line-number parsing fails and lastErrorLine is always 0; error message still displays correctly |
| A4 | `EditBoxWidget` in 1.21.4 does NOT have a `setCursorToStart()` or `setCursorToEnd()` named method (not seen in docs) | Pattern 3 | If there IS such a method, its absence just means we let the constructor default (cursor at end of initial text); no functional impact |
| A5 | `NodeType.ADDON` currently falls through the generic `computeWidth`/`computeHeight` path in `NodeDimensionCalculator` and produces incorrect height | Architecture Patterns (Pattern 2) | Verified by reading NodeDimensionCalculator.java fully (no ADDON branch exists). Risk: if there IS a hidden path, the fix would be redundant (safe) |

**A5 is LOW-risk assumed only as a precaution.** The code read at `NodeDimensionCalculator.java:1-390` confirms no ADDON branch.

---

## Open Questions

1. **`lastError` write-back channel**
   - What we know: `CobaltVm.run` catches `LuaError` and calls `runtime.sendErrorToChat`; `NodeResult.FAILURE` is returned.
   - What's unclear: How does the error message + line get written into `node.getAddonExtraFields()` on the game thread? The executor runs on a worker thread; `node` reference is not in scope.
   - Recommendation: Extend `AddonNodeContext` with a `setLastError(String, int)` method; Pathmind wires this to a game-thread executor that writes the extra-fields. The planner must decide whether this goes on `AddonNodeContext` directly or on a new `AddonNodeErrorReporter` sub-interface.

2. **`AddonNodeContext.getNodeId()` sourcing**
   - What we know: Extra-fields blob is the only persistent per-node storage accessible from `render()`.
   - What's unclear: Should Pathmind auto-generate and inject the node-ID into `AddonNodeContext`, or should the addon generate+store it in extra-fields?
   - Recommendation: Pathmind generates a stable UUID at `Node(addonTypeId, x, y)` construction time, stores it in extra-fields as `"_node_id"`, and exposes it via `AddonNodeContext.getNodeId()` in the render context. This is Pathmind's responsibility (it owns the node identity) and keeps the addon's EditorState map purely addon-managed.

3. **Popup rendering order (above scissor)**
   - What we know: NodeGraph enables scissor before calling `renderer.render` and disables it after [NodeGraph.java:7419-7429]. Popup must render above scissor.
   - What's unclear: Should NodeGraph call a second `renderer.renderOverlay(...)` after `disableScissor()`, or should the popup be deferred to a post-render hook?
   - Recommendation: Add `void renderOverlay(AddonNodeContext ctx, DrawContext draw, int x, int y, int width, int height)` to `AddonNodeBodyRenderer` with a default no-op implementation. NodeGraph calls it after `disableScissor()`. The planner must add this to the addon API contract.

---

## Environment Availability

All required tools are available from prior phases:
- Java 21: present (required by CLAUDE.md)
- Gradle 8.x: present (required by CLAUDE.md)
- Minecraft 1.21.4 / Fabric Loader: present (Yarn mappings confirmed `EditBoxWidget` exists)
- No new external dependencies required

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.11.4 (existing) |
| Config file | none — test runner via Gradle |
| Quick run command | `./gradlew :common:test` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EDIT-01 | Editor renders without crash; text can be set and retrieved | unit (addon) | `./gradlew :test` `LuaScriptNodeRendererTest` | ❌ Wave 0 |
| EDIT-02 | Gutter width formula correct for 1/2/3-digit line counts | unit (addon) | `./gradlew :test` `GutterWidthTest` | ❌ Wave 0 |
| EDIT-03 | `lastError` round-trips through serialize/deserialize; null on success | unit (addon) | `./gradlew :test` `LuaNodeSerializerTest` | partially — extend existing |
| EDIT-04 | Prefix-match engine returns correct completions for keywords, stdlib, pathmind.* | unit (addon) | `./gradlew :test` `SuggestionEngineTest` | ❌ Wave 0 |
| SC#5 (UAT) | Keyboard routing: typing in editor doesn't trigger graph shortcuts | manual in-game | `/gsd-verify-work` | manual only |

### Wave 0 Gaps

- [ ] `LuaScriptNodeRendererTest.java` — covers EDIT-01 (mock DrawContext + ctx)
- [ ] `GutterWidthTest.java` — covers EDIT-02 (pure math, no MC needed)
- [ ] `SuggestionEngineTest.java` — covers EDIT-04 (pure prefix logic)
- [ ] `LuaNodeSerializerTest` extension — add lastError round-trip tests

*(The `LuaNodeSerializerTest` already exists from Phase 2 — extend it.)*

---

## Security Domain

No new security-sensitive surfaces introduced. The `AddonNodeInputHandler` receives only keypresses and mouse events from an already-trusted source (the Minecraft screen). The per-node EditorState map uses no reflection and no network access. The `lastError` field contains only Lua-VM error messages (strings already surfaced to chat in Phase 2). No ASVS categories newly triggered beyond what Phase 1-2 already covered.

---

## Sources

### Primary (HIGH confidence — codebase reads)
- `NodeGraph.java:7384-7434` — `renderAddonNodeContent`, scissor clip, context build pattern
- `NodeGraph.java:3750-3855` — in-node field editing pattern (event name): caret blink, selection, border colors, `UITheme.CARET_COLOR`, `UITheme.TEXT_SELECTION_BG`, `UITheme.NODE_INPUT_BG_ACTIVE`
- `PathmindVisualEditorScreen.java:2108-2474` — `keyPressed`/`charTyped` chain: where addon hook inserts, handle*KeyPressed ordering
- `PathmindVisualEditorScreen.java:1525-1761` — `handleNodeGraphClick`: where addon click hook inserts
- `NavigatorChatSuggestions.java:1-336` — popup constants (WIDTH=220, ENTRY_HEIGHT=14, PADDING=4, PANEL_BACKGROUND=0x80000000, border=BORDER_HIGHLIGHT, selected=0x503A3A3A) + `render` / `handleKeyPressed` pattern
- `PathmindTextField.java` — caret/selection/suggestion rendering reference
- `AddonNodeBodyRenderer.java`, `AddonNodeContext.java`, `AddonNodeDefinition.java`, `NodeTypeRegistrar.java` — current addon API surface
- `NodeDimensionCalculator.java:1-390` — NO ADDON branch confirmed; TEMPLATE/CUSTOM_NODE uses `TEMPLATE_NODE_HEIGHT=108`
- `Node.java` — `TEMPLATE_NODE_HEIGHT=108`, `getAddonExtraFields()`, `setAddonExtraFields()`, `NodeType.ADDON` constructor
- `CobaltVm.java` — LuaError catch at lines 177-191; error message format `"script:N: message"`
- `LuaScriptNodeRenderer.java` — `LINE_HEIGHT=10` confirmed; current 3-line read-only preview
- `PathmindBindings.java` — binding function names: `getVar`, `setVar`, `moveTo`, `getPosition`, `getInventory`, `getBlock`
- `LuaNodeSerializer.java` — schema v1, `script` field persistence pattern; GSON double-cast warning
- `LuaNodeExecutor.java` — async worker pattern; `CobaltVm.run` call
- `UITheme.java` — all color constants verified: `CARET_COLOR`, `TEXT_SELECTION_BG`, `NODE_INPUT_BG_ACTIVE`, `BORDER_FOCUS`, `BORDER_SUBTLE`, `BACKGROUND_INPUT`, `BACKGROUND_SECONDARY`, `STATE_ERROR`, `BORDER_DANGER_MUTED`, `TEXT_TERTIARY`, `TEXT_PRIMARY`, `TEXT_HEADER`, `TEXT_SECONDARY`, `INPUT_HEIGHT=18`
- `NodeGraph.java:229` — `COORDINATE_CARET_BLINK_INTERVAL_MS = 500`

### Secondary (MEDIUM confidence — official Yarn docs)
- [Yarn 1.21.4+build.8 EditBoxWidget](https://maven.fabricmc.net/docs/yarn-1.21.4+build.8/net/minecraft/client/gui/widget/EditBoxWidget.html) — constructor signature, all method names, superclass chain (`ScrollableTextFieldWidget` → `ScrollableWidget` → `ClickableWidget`)

### UI-SPEC (HIGH confidence — phase-locked contract)
- `.planning/phases/03-script-node-editor-autosuggestions/03-UI-SPEC.md` — all pixel dimensions, color tokens, layout constants, copywriting

---

## Metadata

**Confidence breakdown:**
- EditBoxWidget API: HIGH — verified from Yarn 1.21.4+build.8 official docs
- Input routing architecture: HIGH — verified from actual `PathmindVisualEditorScreen` source
- NodeDimensionCalculator gap: HIGH — verified by reading entire 390-line file (no ADDON branch)
- `lastError` write-back: MEDIUM — design is clear, exact wiring method is Claude's discretion (open question #1)
- Popup rendering order: MEDIUM — pattern is clear, exact API shape is Claude's discretion (open question #3)
- SuggestionEngine tokenizer: HIGH — pure logic, no external dependencies

**Research date:** 2026-06-25
**Valid until:** 2026-07-25 (Minecraft API stable, Pathmind source read from HEAD)
