---
sidebar_position: 3
title: Lua Scripting Reference
---

# Lua Scripting Reference

The **Lua Script node** (from the [pathmind-lua addon](https://github.com/botboy0/pathmind-lua)) runs a sandboxed Lua script as a step in your node graph. This page documents everything a script can do.

## The sandbox

Scripts run on **Cobalt** (a Lua 5.2-family VM) on a background worker thread — a running script never blocks the game. The environment is deliberately restricted:

- **Available:** the Lua base library, `table`, `string`, `math`, `coroutine`, `utf8`, and a Pathmind-provided `print`.
- **Not available:** `io`, `os`, `require`/`package`, and any file or network access. Calling them is a runtime error.
- **Compute budget:** a script gets 5 seconds of *pure compute time*. Time spent waiting inside a blocking call (`moveTo`, `invokeAction`) does not count — a 30-second navigation is fine, but a runaway `while true do end` loop is killed with a timeout error.

## Global functions

### `print(...)`

Prints to the player chat (with the Pathmind prefix). Multiple arguments are joined with tabs, standard Lua-style:

```lua
print("position:", pathmind.getPosition().x)
```

## The `pathmind.*` API

### Variables — `getVar(name)`, `setVar(name, value)`

Read and write **node-tree variables** — the same variables Set Variable / Change Variable nodes use, so scripts and graph nodes can hand data to each other.

```lua
local runs = pathmind.getVar("runs") or 0
pathmind.setVar("runs", runs + 1)
```

- Supported value types: **number, string, boolean** (scalars only in v1 — tables are rejected).
- `getVar` returns `nil` when the variable does not exist.

### Navigation — `moveTo(x, y, z)`

Navigates the player to the given coordinates and **blocks until arrival** (or errors when no route exists). The compute-budget clock is paused while waiting.

```lua
local p = pathmind.getPosition()
pathmind.moveTo(p.x + 10, p.y, p.z)
```

### Game state — `getPosition()`, `getInventory()`, `getBlock(x, y, z)`

```lua
local p = pathmind.getPosition()      -- { x=, y=, z= }
local inv = pathmind.getInventory()   -- array of { slot=, item="ns:id", count= }
local id = pathmind.getBlock(p.x, p.y - 1, p.z)  -- "minecraft:stone", or nil if unloaded
```

All three are safe snapshots brokered by Pathmind on the game thread — scripts never touch Minecraft state directly.

### Actions — `invokeAction(name, args)`

Invokes **any Pathmind action node** by name and blocks until it completes — the full world/player action surface, without wiring extra nodes into the graph:

```lua
pathmind.invokeAction('message', { text = 'hello from Lua' })
pathmind.invokeAction('jump')
pathmind.invokeAction('press_key', { Key = 'GLFW_KEY_W', Duration = 0.5 })
pathmind.invokeAction('goto', { X = 100, Y = 64, Z = -20 })
```

- **`name`** is a Pathmind node-type name, case-insensitive: `jump`, `look`, `walk`, `message`, `equip_hand`, `drop_item`, `use`, `break`, `goto`, and every other world/player action you see in the sidebar.
- **`args`** is an optional table mapping the node's **parameter names** (as shown on the node in the editor, case-insensitive) to number/string/boolean values. For `message`, the key `text` sets the message text.
- **Errors are loud:** an unknown action name or an argument that matches no parameter raises a Lua error listing the valid parameter names — typos never silently no-op.
- **Not invocable:** flow control (`control_if`, `start_chain`, …), sensors, data/list operations, and parameter nodes. Scripts have Lua's own control flow and `getVar`/`setVar` instead.

## Error handling

An uncaught script error stops the graph at the Script node, prints `Lua error: script:LINE: message` to chat, and shows a persistent red **error strip** on the node (`⚠ Line N: …`). The strip:

- shows the first line of the error; hover it for the full message including the stack traceback,
- **dims to gray** once you edit the script (the line number may no longer match),
- clears on the next successful run.

Use `pcall` for errors you want to handle inside the script:

```lua
local ok, err = pcall(function()
  pathmind.invokeAction('goto', { X = 0, Y = 64, Z = 0 })
end)
if not ok then
  print('navigation failed: ' .. err)
end
```

## Editor

The Script node body is an inline editor with a line-number gutter and autosuggestions: typing `pathmind.` (or pressing **Ctrl+Space**) opens a popup with the function list — Up/Down to select, Enter to accept, Esc to close. Esc with no popup open blurs the editor; a second Esc closes the node editor screen.
