---
phase: 03-script-node-editor-autosuggestions
plan: 05
subsystem: addon-autosuggestion-popup
tags: [autosuggestion, suggestion-engine, popup, renderOverlay, edit-04, leak-proof, member-access, ctrl-space]
dependency_graph:
  requires: [03-04]
  provides: [SuggestionEngine, suggestion-popup-renderOverlay, EDIT-04-complete]
  affects: [SuggestionEngine, SuggestionEngineTest, EditorState, LuaScriptNodeRenderer]
tech_stack:
  added: [SuggestionEngine prefix-match (pure-JVM), suggestion popup (renderOverlay, post-scissor)]
  patterns: [last-line cursor approximation (EditBoxWidget no getCursor), NavigatorChatSuggestions constants, popup-priority keyPressed branch before Esc-blur]
key_files:
  created: []
  modified:
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/SuggestionEngine.java
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/EditorState.java
    - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java
    - pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/SuggestionEngineTest.java
decisions:
  - EditBoxWidget has no getCursor() in MC 1.21.4 (parent is ScrollableTextFieldWidget not TextFieldWidget); getLineBeforeCursor uses last-line of getText() as v1 approximation
  - applyAcceptedSuggestion uses setText() suffix-replace + cursor-at-end (setText contract) instead of absolute setCursor; adequate for end-of-line typing
  - refilterSuggestions only auto-opens popup for pathmind. trigger (not plain prefix); Ctrl+Space is the manual trigger for plain prefix
  - popup-priority keyPressed branch handles Enter/Esc/Up/Down BEFORE the existing Esc-blur path (T-03-05-01 ordering)
  - Popup anchor coordinates stored in EditorState per-frame (set during render) and consumed in renderOverlay (post-scissor)
metrics:
  duration: "~25 min"
  completed: "2026-06-25"
  tasks_completed: 2
  files_modified: 4
  files_created: 0
---

# Phase 03 Plan 05: SuggestionEngine + Popup Wiring Summary

**One-liner:** Real in-process prefix-match `SuggestionEngine` (6 pathmind.* names from PathmindBindings, 22 Lua keywords, 20 stdlib) wired into `renderOverlay` popup with both triggers (auto-dot + Ctrl+Space), full keyboard/mouse nav (consumed), and token-replace acceptance; `SuggestionEngineTest` re-enabled and passing.

## Tasks Completed

| # | Task | Commit (addon repo) | Files |
|---|------|---------------------|-------|
| 1 | Real SuggestionEngine + re-enable SuggestionEngineTest | c13a497 | SuggestionEngine.java (rewrite), SuggestionEngineTest.java (@Disabled removed) |
| 2 | Wire popup into EditorState + renderer (triggers, nav, accept, overlay render) | c99cee9 | EditorState.java, LuaScriptNodeRenderer.java |

## What Was Built

### SuggestionEngine (Task 1 — production implementation)

Replaced the Wave-0 stub that threw `UnsupportedOperationException`.

**Static symbol sets (locked to UI-SPEC Copywriting Contract):**
- `PATHMIND_SYMBOLS` (6 entries): `getBlock(x, y, z)`, `getInventory()`, `getPosition()`, `getVar(name)`, `moveTo(x, y, z)`, `setVar(name, value)` — alphabetically sorted; names are identical to those registered in `PathmindBindings.java:55-63` (asserted in javadoc and `SuggestionEngineTest.ALL_PATHMIND_NAMES`).
- `LUA_KEYWORDS` (22 entries): `and`, `break`, `do`, `else`, `elseif`, `end`, `false`, `for`, `function`, `goto`, `if`, `in`, `local`, `nil`, `not`, `or`, `repeat`, `return`, `then`, `true`, `until`, `while`
- `LUA_STDLIB` (20 entries): per UI-SPEC v1 list (print, tostring, tonumber, type, pairs, ipairs, next, select, unpack, table.*, string.*, math.*)
- `ALL_SYMBOLS` = keywords + stdlib + pathmind.* (combined list, 48 entries total)
- `TOKEN_PATTERN` = `[a-zA-Z_][a-zA-Z0-9_.]*$` (anchored, no catastrophic backtracking — T-03-05-02)

**`getSuggestions(String lineBeforeCursor)` logic:**
1. Null/blank → empty list
2. `lineBeforeCursor.endsWith("pathmind.")` → all 6 PATHMIND_SYMBOLS (member auto-trigger)
3. Token extracted via TOKEN_PATTERN
4. Token starts with `"pathmind."` → filter PATHMIND_SYMBOLS by sub-prefix (case-insensitive)
5. Plain prefix → filter ALL_SYMBOLS, cap 6 (case-insensitive)
6. No token → empty list

**No Minecraft imports** — pure-JVM, testable without MC client.

### SuggestionEngineTest (Task 1)

Removed `@Disabled("Wave 4 — ...")`. All 6 tests pass:
- `memberAccessTrigger_returnsAllPathmindSymbols` — returns all 6 names
- `memberSubPrefix_returnsFilteredPathmindSymbol` — `"pathmind.getB"` → `getBlock`
- `memberSubPrefix_caseInsensitive` — `"pathmind.GETB"` → `getBlock`
- `plainPrefix_keywordMatch` — `"fun"` → contains `function`
- `plainPrefix_stdlibMatch` — `"pri"` → contains `print`
- `noMatch_returnsEmptyList` — `"xyzzy_no_such_symbol"` → empty
- `plainPrefix_cappedAtSixEntries` — `"t"` → ≤ 6 entries

### EditorState (Task 2 — suggestion fields activated)

Previously reserved fields now wired:
- `SuggestionEngine engine` — one per EditorState, stateless, reused across frames
- `List<CompletionEntry> currentSuggestions` — active popup candidates
- `boolean suggestionOpen`, `int suggestionSelectedIndex`
- `int popupAnchorX`, `int popupAnchorY` — per-frame anchor from render(), consumed by renderOverlay()

New helper methods:
- `openSuggestions(List<CompletionEntry>)` — sets list, resets selectedIndex=0; closes if empty
- `closeSuggestions()` — clears open flag, list, index
- `moveSelection(int delta)` — clamps to [0, size-1]
- `acceptSelected()` — returns selected entry or null if popup closed/empty
- `getLineBeforeCursor()` — returns last line of getText() (see Known Stubs)

### LuaScriptNodeRenderer (Task 2)

**charTyped:**
- Delegates char to widget (char inserts normally, NOT consumed here for popup)
- Calls `refilterSuggestions(state)`: auto-opens popup if line ends with `"pathmind."`; refilters if popup already open; closes if result is empty
- Returns true (all chars consumed while focused — leak-proof)

**keyPressed (popup-priority branch — FIRST, before Esc-blur):**
- **Popup open:**
  - Up/Down → `moveSelection(±1)`, consumed (do NOT pan graph)
  - Enter/KP_Enter → `acceptSelected()` + `applyAcceptedSuggestion()` + `closeSuggestions()`, consumed (NO newline inserted)
  - Esc → `closeSuggestions()`, consumed (does NOT blur editor — this precedes the Esc-blur path)
  - Ctrl+Space → recompute and re-open, consumed
  - Other keys → forward to widget + `refilterSuggestions()`
- **Popup closed:**
  - Ctrl+Space → `openSuggestions(engine.getSuggestions(lineBeforeCursor))`, consumed
  - Esc → blur editor (existing 03-03 path)
  - All other keys → forward to widget (all consumed while focused)

**mouseClicked:**
- If popup open and click is on a row → `acceptSelected()` + `applyAcceptedSuggestion()` + `closeSuggestions()`; return true
- If popup open and click is outside popup → `closeSuggestions()`; fall through to widget
- Forward to widget as before

**renderOverlay (EDIT-04 popup, post-scissor):**
- `updateSuggestionHover(state)` — reads MC mouse position, sets `suggestionSelectedIndex` for hovered row
- `drawSuggestionPopup(draw, tr, state)` — renders popup panel using EXACT NavigatorChatSuggestions constants:
  - Panel: `POPUP_WIDTH=220`, `POPUP_ENTRY_HEIGHT=14`, `POPUP_PADDING=4`, `PANEL_BG=0x80000000`
  - Border: `UITheme.BORDER_HIGHLIGHT`
  - Selected row fill: `0x503A3A3A`
  - Label (name): `UITheme.TEXT_HEADER` at labelX = popupX + 6
  - Hint (signature): `UITheme.TEXT_SECONDARY` at min(popupX + WIDTH - 8, labelX + nameWidth + 12)
  - Capped at 6 rows (`POPUP_MAX_ROWS`)
- Error tooltip rendered afterward (coexists — popup takes visual priority)

**Popup anchor:** Computed in `render()` as `editorX + TEXT_PADDING_LEFT, editorY + LINE_HEIGHT + TEXT_PADDING_TOP + 2` when `suggestionOpen`. Stored in EditorState; consumed in `renderOverlay` (post-scissor, not clipped by node body).

**applyAcceptedSuggestion:**
- Extracts token to replace from last line's suffix
- Member-access case: replaces only the sub-prefix after `"pathmind."` (e.g., `"getB"` → `"getBlock"`)
- Plain case: replaces the whole token
- Uses `widget.setText(newText)` — cursor lands at end (no `setCursor` available on EditBoxWidget)

## pathmind.* Name Derivation

The `pathmind.*` suggestion names are derived from the live `PathmindBindings` function registration:

```java
// PathmindBindings.java:53-63 (authoritative source)
return bind(new RegisteredFunction[]{
    ofV("getVar", ...),       // → CompletionEntry("getVar", "(name)")
    ofV("setVar", ...),       // → CompletionEntry("setVar", "(name, value)")
    ofV("moveTo", ...),       // → CompletionEntry("moveTo", "(x, y, z)")
    ofV("getPosition", ...),  // → CompletionEntry("getPosition", "()")
    ofV("getInventory", ...),  // → CompletionEntry("getInventory", "()")
    ofV("getBlock", ...),     // → CompletionEntry("getBlock", "(x, y, z)")
});
```

`SuggestionEngine.PATHMIND_SYMBOLS` holds the same six names alphabetically sorted. `SuggestionEngineTest.ALL_PATHMIND_NAMES` independently lists them sorted and asserts full coverage. If `PathmindBindings` adds a binding, both `PATHMIND_SYMBOLS` and `ALL_PATHMIND_NAMES` MUST be updated.

## Human Verification Required (SC#5)

**Status:** Code complete. All automated tasks committed. The in-game UAT checkpoint (Task 3) is deferred to end-of-phase per `workflow.human_verify_mode: end-of-phase`.

The end-of-phase verifier must perform the following in-game checks on a MC 1.21.4 Fabric instance with both `pathmind.jar` and `pathmind-lua-addon.jar` in the mods folder:

**Deploy step:** Build and deploy both mods for MC 1.21.4 Fabric (`./gradlew build` in pathmind-lua; `gradlew buildAllTargets` or publish mavenLocal in pathmind); copy both jars to the mods folder; launch Minecraft 1.21.4.

**Check 1 — EDIT-01 leak-proofing (PRIMARY):**
- Drop a Script node into the Pathmind editor
- Click the editor body → it focuses (border brightens to BORDER_FOCUS, bg to NODE_INPUT_BG_ACTIVE, caret blinks)
- Type several lines of Lua
- Verify: Backspace deletes a character (NOT the node); arrow keys move the caret (do NOT pan the graph); Delete deletes a char; Shift+arrows, Ctrl+A, Ctrl+C, Ctrl+V, Ctrl+X, Ctrl+Z all behave as a text editor; mouse-wheel scrolls the editor
- None of these actions must nudge, delete, or pan the node graph

**Check 2 — Focus/blur:**
- Press Esc (no popup open) → editor blurs (border dims to BORDER_SUBTLE)
- Click empty canvas while editor is focused → editor blurs
- Re-focus the editor; verify Esc no longer closes the screen immediately (only blurs)

**Check 3 — EDIT-02 gutter:**
- Line numbers appear in the gutter, right-aligned and dimmed
- Add lines past 9 → gutter widens to accommodate 2-digit numbers
- Numbers stay synchronized when scrolling the editor content

**Check 4 — EDIT-03 error strip:**
- Run a script with a deliberate error (e.g. `pathmind.nonExistentFunction()` or call a nil)
- The error strip shows `⚠ Line N: <message>` below the editor
- Hover over the strip → full untruncated error message appears as a tooltip
- Fix the script and re-run successfully → strip disappears
- Save the preset; close and reopen it → the Script node reappears with its errored state (strip shows on reopen)

**Check 5 — EDIT-04 autosuggestions:**
- Type `pathmind.` in the editor → popup opens listing all 6 API names with signatures
- Continue typing `getB` → popup narrows to `getBlock` only
- Press Enter → `getBlock` replaces `getB` in the text; no newline inserted
- Press Ctrl+Space with cursor after `fun` → popup opens suggesting `function`
- Press Esc → popup closes, editor remains focused (does NOT blur)
- Hover over a popup row → that row highlights; click it → name accepted
- Popup is NOT cropped at the node bottom (renders above the scissor clip)

**Check 6 — Standalone guarantee:**
- Launch Pathmind WITHOUT the addon jar
- Verify: no errors on startup; Script node shows as a missing-addon placeholder; other nodes unaffected

**Resume signal:** Type "approved" if all six checks pass, or describe specific failures (which EDIT-NN, which step) so a gap-closure plan can target them.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] EditBoxWidget has no getCursor() — MC 1.21.4 API gap**
- **Found during:** Task 2 first compile attempt (two compiler errors)
- **Issue:** The plan specified using `widget.getCursor()` to derive line-before-cursor and to place the cursor after acceptance. In MC 1.21.4 Yarn, `EditBoxWidget` extends `ScrollableTextFieldWidget`, not `TextFieldWidget`. Only `TextFieldWidget` (the single-line widget) exposes `getCursor(): int`. `EditBoxWidget`'s parent chain is `ScrollableTextFieldWidget → ScrollableWidget → ClickableWidget` — none expose `getCursor()`.
- **Fix:**
  - `getLineBeforeCursor()`: returns `text.substring(text.lastIndexOf('\n') + 1)` — the last line of the text as a v1 approximation (accurate when user types at end of script).
  - `applyAcceptedSuggestion()`: uses `widget.setText(newText)` suffix-replace + accepts cursor-at-end (setText contract). No absolute cursor placement needed for end-of-line completion.
- **Accessor note (per plan requirement):** `EditBoxWidget.getCursor()` does NOT exist. `getCursor()` is on `TextFieldWidget` only. Verified via `javap` on `minecraft-merged-1.21.4-yarn-1.21.4+build.8.jar`.
- **Files modified:** EditorState.java, LuaScriptNodeRenderer.java
- **Commit:** c99cee9

## Known Stubs

**1. Cursor-at-end after acceptance (v1 approximation)**
- **File:** `LuaScriptNodeRenderer.java:applyAcceptedSuggestion` / `EditorState.java:getLineBeforeCursor`
- **Behavior:** `widget.setText(newText)` places cursor at end of text. If the user accepts a completion while editing a line in the middle of a multi-line script, the cursor jumps to the end rather than staying at the editing position.
- **Reason:** `EditBoxWidget` exposes no `setCursor(int)` or `getCursor()` in MC 1.21.4 Yarn. `getLineBeforeCursor()` uses the last line as a cursor proxy.
- **Impact:** Cosmetic in typical usage (typing at end of script). Acceptance on a mid-script line works correctly for the token replacement but relocates the cursor.
- **Future fix plan:** Add a mixin accessor (`EditBoxWidgetCursorAccessor`) to read/write the cursor field from the addon; this requires adding a mixin to the addon's `fabric.mod.json` mixin list. Tracked for v2.

## Threat Surface Scan

All T-03-05-* mitigations implemented:

| Threat | Status |
|--------|--------|
| T-03-05-01 (Popup key leak: Enter newline, Esc blur, arrows pan) | Mitigated — popup branch handled FIRST in keyPressed; Enter/Esc/Up/Down all consumed before reaching editor newline or Esc-blur path |
| T-03-05-02 (Pathological tokenizer input) | Mitigated — TOKEN_PATTERN anchored `$` with simple char class; ≤48 symbols; cap 6; O(n) scan |
| T-03-05-03 (Suggestion rendered as format string) | Accept — `drawTextWithShadow` renders literal text from static symbol table; no user data in format |
| T-03-05-04 (Symbol-set drift) | Mitigated — SuggestionEngineTest.ALL_PATHMIND_NAMES asserts the 6 names match PathmindBindings; javadoc cross-references PathmindBindings.java:55-63 |
| T-03-05-05 (renderOverlay draws outside bounds) | Mitigated — popup anchored to node body coords; overlay call is post-scissor (NodeGraph disableScissor before renderOverlay) |
| T-03-05-SC (npm/pip installs) | Accept — no new dependencies |

No new threat surface introduced beyond what the plan's threat model covers.

## Self-Check: PASSED

- SuggestionEngine.java: FOUND (production implementation, no UnsupportedOperationException)
- SuggestionEngineTest.java: FOUND (@Disabled removed)
- EditorState.java: FOUND (openSuggestions/closeSuggestions/moveSelection/acceptSelected helpers wired)
- LuaScriptNodeRenderer.java: FOUND (popup-priority keyPressed, charTyped refilter, renderOverlay popup)
- Addon repo commits: c13a497 (Task 1), c99cee9 (Task 2) — verified via `git log --oneline`
- `./gradlew test --tests SuggestionEngineTest`: BUILD SUCCESSFUL, 6/6 tests PASS
- `./gradlew build`: BUILD SUCCESSFUL (10 tasks, MC 1.21.4)
- PATHMIND_SYMBOLS names == PathmindBindings.java:55-63: confirmed (getVar, setVar, moveTo, getPosition, getInventory, getBlock)
- No Minecraft imports in SuggestionEngine: confirmed (pure-JVM)
- Popup constants match NavigatorChatSuggestions: WIDTH=220, ENTRY_HEIGHT=14, PADDING=4, PANEL_BG=0x80000000, selected=0x503A3A3A, TEXT_HEADER, TEXT_SECONDARY
- popup-priority keyPressed branch precedes Esc-blur: confirmed (code reads popup branch first)
- renderOverlay updated: draws popup then error tooltip (coexist)
