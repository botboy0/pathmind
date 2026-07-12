# Mission Primitives Research

**Domain:** Script-facing action surface (envelopes, Baritone integration, completion contracts)
**Researched:** 2026-07-13 (Codex, read-only, every claim cited as path:line against pathmind@385226d)
**Confidence:** HIGH for code-derived claims; Baritone-internal behavior (chat-command interception) explicitly unverifiable from these repos

Commissioned for the Survival Bootstrap rewrite; Part 1 is a durable reference for
what each primitive actually guarantees. The wait_ defect it found is fixed
(pathmind 385226d); the remaining open questions feed the Baritone-integration
milestone.

---

# Survival Bootstrap primitives inventory and redesign report

## Executive conclusion

A much smaller mission is possible:

- Use `collect_` for all resource mining.
- Use `goto_block` to approach grass and return to the crafting table.
- Let `place_` select the table item and validate placement; remove `hotbar_`, distance math, and post-placement block polling.
- Retry crafting based on `precondition`, `transient`, and `missing_resource`.
- Retain inventory verification and final-Y verification because the native completion contracts are weaker than the current specs assume.

Three source-level limitations prevent a fully reliable “Baritone mines down to Y 30” mission today:

1. `goto_` may resolve successfully without verifying arrival, including when Baritone never becomes active.
2. GOTO disables Baritone breaking and placing according to global settings, both defaulting to `false`; Lua cannot override them per call.
3. `wait_` appears to complete immediately for synthetic addon actions because it tests whether the synthetic WAIT node is the active graph node.

These limitations mean the proposed mission is intentionally bounded and diagnostic: it can succeed when terrain/settings permit, otherwise it emits the vision spec’s required phase-specific predicted stop.

---

# Part 1 — Verified primitives inventory

## Common action-call contract

Catalog actions are registered only with a trailing underscore, accept one optional argument table, and block the Lua worker on the Java action future. Bare aliases do not exist. `pathmind.goto_({...})`, for example, dispatches the underlying `goto` action. `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/ApiSymbol.java:107-118`; `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java:83-88`

Argument keys are case-insensitive. Values can only be number, string, or boolean; nested Lua tables cannot be action arguments. `Mode` is a synthetic special argument applied before ordinary parameters. `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:108-125`; `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java:327-355`

Expected node failures are converted to `{ok=false,status=...,message=...}`. Unclassified failures become `status="failed"`; caller or internal exceptions raise a Lua error. `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:180-215`; `pathmind/common/src/main/java/com/pathmind/api/addon/ActionResult.java:7-22`

| Primitive | Lua call shape | Completion behavior | Envelope | Important pitfalls |
|---|---|---|---|---|
| GOTO XYZ | `pathmind.goto_({Mode="goto_xyz", X=x, Y=y, Z=z})`; `Mode` may be omitted because XYZ is default. | With Baritone API, sets `GoalBlock` and waits for `PreciseCompletionTracker`. | Success includes final `x`, `y`, `z`. | Tracker watches process/path inactivity, not position. |
| GOTO XZ | `pathmind.goto_({Mode="goto_xz", X=x, Z=z})` | Uses `GoalXZ`; tracked like XYZ. | Final `x/y/z`. | Y is unconstrained. |
| GOTO Y | `pathmind.goto_({Mode="goto_y", Y=y})` | Uses `GoalYLevel`; tracked like XYZ. | Final `x/y/z`. | Cannot be relied upon to mine downward under default settings. |
| GOTO block | `pathmind.goto_({Mode="goto_block", Block="crafting_table"})` | Scans locally, then uses `GoalNear(foundPos,1)`; tracked. | Final `x/y/z`, or generic `failed` for target-resolution failures. | The inline-block path searches only a 64-block radius; it is not an unlimited Baritone block search. |
| COLLECT single | `pathmind.collect_({Block="oak_log", Amount=3})` | `Amount=1` plus a locally found target uses a manual single-block breaker; otherwise uses tracked Baritone `mineByName`. | Success may include `collected`. | Neither path proves requested drops are in inventory. |
| COLLECT multiple | `pathmind.collect_({Mode="collect_multiple", Blocks="stone,deepslate"})` | Tracked Baritone `mineByName(String[])`. | Usually no useful `collected` field because success-field extraction looks for `Block` or `Item`, not `Blocks`. | No `Amount` parameter in multiple mode. Lists must be comma-separated strings. |
| PLACE | `pathmind.place_({Block="crafting_table", X=x, Y=y, Z=z})` | Waits for vanilla interaction and polls up to about one second for the desired block to appear. | Success has only `ok=true`; ordinary placement failures are generic `failed`. | Requires explicit coordinates and a valid nearby support face. |
| INTERACT | `pathmind.interact_({Block="crafting_table"})` | Completes immediately after sending the interaction call. | Usually empty success; targeting failures are generic `failed`. | Does not wait for a GUI and does not validate that vanilla accepted/opened it. |
| CRAFT | `pathmind.craft_({Mode="craft_player_gui", Item="oak_planks", Amount=12})` or `Mode="craft_crafting_table"` | Asynchronous crafting routine; waits for result slot, moves output, and completes after crafting work. | Success includes inventory-delta `produced`; classified failure statuses described below. | Requires the appropriate GUI and uses the current screen handler’s inventory view. |
| WAIT | `pathmind.wait_({Duration=1})`, optionally `Mode="wait_ticks"`, `wait_minutes`, etc. | Intended to wait asynchronously. For script-created synthetic nodes it appears to complete immediately. | Empty success unless interrupted. | Not currently a dependable GUI delay. |

## 1. GOTO

### Modes and routing

The four modes are `goto_xyz`, `goto_xz`, `goto_y`, and `goto_block`; XYZ is the default. Their parameters are respectively `{X,Y,Z}`, `{X,Z}`, `{Y}`, and `{Block}`. `pathmind/common/src/main/java/com/pathmind/nodes/NodeMode.java:11-15`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeMode.java:124-128`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeMode.java:186-190`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeParameterDefinitionRegistry.java:20-30`

With the Baritone API available:

- XYZ uses `createGoalBlock` and `setGoalAndPath`.
- XZ uses `createGoalXZ`.
- Y uses `createGoalYLevel`.
- Block mode first resolves a nearby block, then uses `createGoalNear(...,1)`. `pathmind/common/src/main/java/com/pathmind/nodes/NodeNavigationCommandExecutor.java:91-172`

The nearby block resolver searches within `PARAMETER_SEARCH_RADIUS`, which is 64 blocks. If none is found it records a normal node failure before the nominal `getToBlock` branch can run. `pathmind/common/src/main/java/com/pathmind/nodes/Node.java:303`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeNavigationCommandExecutor.java:718-741`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeNavigationCommandExecutor.java:1472-1500`

If the Baritone mod exists but its API is unavailable, GOTO sends `#goto ...` and immediately completes. That path is fire-and-forget. `pathmind/common/src/main/java/com/pathmind/nodes/NodeNavigationCommandExecutor.java:581-658`

### Does it block until arrival?

The API path blocks while `PreciseCompletionTracker` observes Baritone. However, the tracker declares success when the process/path becomes inactive; it never compares the final player position with the requested goal. `pathmind/common/src/main/java/com/pathmind/nodes/NodeNavigationCommandExecutor.java:325-329`; `pathmind/common/src/main/java/com/pathmind/execution/PreciseCompletionTracker.java:222-267`

If Baritone never becomes active, after eight seconds the tracker displays a warning and completes the future normally. This produces an `ok=true` envelope, not an error. `pathmind/common/src/main/java/com/pathmind/execution/PreciseCompletionTracker.java:240-249`; `pathmind/common/src/main/java/com/pathmind/execution/PreciseCompletionTracker.java:446-458`

Thus “no path” has no single reliable outcome:

- It can become a false success if the process never starts or stops without arrival.
- A tracker-level hard error or 60-minute timeout completes exceptionally and raises in Lua.
- Target-resolution failures such as no nearby `goto_block` target return an unclassified `failed` envelope.

The 60-minute tracker ceiling is explicit. `pathmind/common/src/main/java/com/pathmind/execution/PreciseCompletionTracker.java:41-46`; `pathmind/common/src/main/java/com/pathmind/execution/PreciseCompletionTracker.java:166-170`

On successful completion the invoker snapshots the actual final position into `x/y/z`. `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:143-156`; `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:248-254`

### Breaking during GOTO

GOTO temporarily applies global `gotoAllowBreakWhileExecuting` and `gotoAllowPlaceWhileExecuting` guards. Both settings default to `false`. `pathmind/common/src/main/java/com/pathmind/nodes/NodeNavigationCommandExecutor.java:325-359`; `pathmind/common/src/main/java/com/pathmind/nodes/Node.java:1044-1065`; `pathmind/common/src/main/java/com/pathmind/data/SettingsManager.java:47-50`

Therefore a Lua `goto_y` cannot be assumed to mine a staircase to Y 30 unless the user has enabled the breaking setting or a naturally traversable route exists.

## 2. COLLECT

### Call shapes and target syntax

Single mode is the default:

```lua
pathmind.collect_({Block="oak_log", Amount=3})
```

Multiple mode is:

```lua
pathmind.collect_({
  Mode="collect_multiple",
  Blocks="stone,deepslate,coal_ore"
})
```

Single mode has `Block` and `Amount`; multiple mode has only `Blocks`. `pathmind/common/src/main/java/com/pathmind/nodes/NodeParameterDefinitionRegistry.java:40-45`

Both `Block` and `Blocks` are split on commas, trimmed, normalized, de-duplicated, and registry-validated. Because Lua action arguments reject table values, a Lua array is not accepted here. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:137-189`; `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java:338-354`

### Amount 1

When `Amount==1` and a preferred target is found locally, COLLECT first approaches it if needed, then manually breaks that exact block. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:90-115`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:220-253`

The break future completes after breaking progress and the `STOP_DESTROY_BLOCK` packet; it does not wait for an item entity to be picked up or for inventory to change. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:291-332`

If no local preferred target is found, even `Amount=1` falls through to the tracked `mineByName` path. Therefore the break-specific shortcut is conditional, not universal. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:90-115`

### Amount 2 or more

For `Amount>=2`, COLLECT invokes Baritone’s reflected `mineByName(int amount,String[] targets)` and registers `TASK_COLLECT`. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:105-115`; `pathmind/common/src/main/java/com/pathmind/util/BaritoneApiProxy.java:204-209`

The tracker condition is:

- Mine or related pathing becomes active.
- All mine/path processes become inactive.
- They remain inactive for 750 ms.

It never reads inventory. `pathmind/common/src/main/java/com/pathmind/execution/PreciseCompletionTracker.java:281-329`

Therefore the testing spec’s statement that `Amount=2` “blocks until the items are actually in the inventory” is not supported by the implementation. The same spec defensively polls inventory afterward, which is the safe behavior. `testing/specs/lua-envelope-statuses.yaml:80-101`

### Envelope

`collected` is an inventory-count delta for the literal `Block` argument, falling back to `Item`. It does not translate a block id into its drop item id, and multiple mode’s `Blocks` argument is not inspected. `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:239-247`; `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:287-308`

Consequences:

- `collect_({Block="oak_log",...})` can report a meaningful log delta.
- Any block whose inventory drop id differs from the requested block id can report `collected=0` despite successful pickup.
- `collect_multiple` generally has no `collected` field.

Manual-break reach and breakability failures are unclassified, so they return `status="failed"`. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:268-289`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeExecutionCompletion.java:14-30`

The no-API fallback issues `#mine` and completes immediately, including for amounts greater than one and multiple targets. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:53-72`

## 3. PLACE

### Call and item selection

```lua
pathmind.place_({
  Block="crafting_table",
  X=x,
  Y=y,
  Z=z
})
```

Coordinates and block id are mandatory in practice; their declared defaults are zero/stone. `pathmind/common/src/main/java/com/pathmind/nodes/NodeParameterDefinitionRegistry.java:104-108`

No prior `hotbar_` is required. PLACE:

1. Accepts an already-held matching item.
2. Searches the hotbar.
3. Searches main inventory.
4. Swaps a main-inventory stack into an empty or selected hotbar slot.
5. Selects that slot. `pathmind/common/src/main/java/com/pathmind/nodes/NodeWorldActionCommandExecutor.java:610-655`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeWorldActionCommandExecutor.java:709-776`

### Reach and vanilla rejection rules

PLACE requires:

- Target center within the player’s current block-interaction range.
- Replaceable target space.
- A non-air neighboring block with a non-empty collision shape.
- A reachable face sample on that support block.
- A held `BlockItem`.
- `ItemPlacementContext.canPlace()`.
- A non-null placement state whose `canPlaceAt` succeeds. `pathmind/common/src/main/java/com/pathmind/nodes/NodeWorldActionCommandExecutor.java:779-832`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeWorldActionCommandExecutor.java:835-917`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeWorldActionCommandExecutor.java:930-947`

The reach value comes from `player.getBlockInteractionRange()`. `pathmind/common/src/main/java/com/pathmind/nodes/Node.java:5992-6001`

After an accepted interaction, PLACE polls the world 20 times at 50 ms intervals and succeeds only if the requested block appears. `pathmind/common/src/main/java/com/pathmind/nodes/NodeWorldActionCommandExecutor.java:657-673`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeWorldActionCommandExecutor.java:1180-1214`

### Envelope statuses

PLACE does not attach any `FailureDetail`. Missing inventory, obstruction, no support, out of reach, vanilla rejection, and “block did not appear” all become generic `status="failed"`. Successful PLACE has no detail fields. The status spec explicitly documents PLACE as unclassified. `testing/specs/lua-envelope-statuses.yaml:11-15`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeWorldActionCommandExecutor.java:1168-1212`

## 4. INTERACT

### Block targeting

```lua
pathmind.interact_({Block="crafting_table"})
```

The inline `Block` field selects a block independently of the current crosshair. INTERACT scans for a matching block, builds reachable face samples, orients the player, and sends `interactBlock`. `pathmind/common/src/main/java/com/pathmind/nodes/NodeEntityActionCommandExecutor.java:104-148`

The search is limited by vanilla interaction reach. A broader 64-block candidate scan exists, but candidates still need at least one hit sample within reach, so INTERACT does not navigate toward them. `pathmind/common/src/main/java/com/pathmind/nodes/NodeEntityActionCommandExecutor.java:193-225`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeEntityActionCommandExecutor.java:228-274`

### LOS and completion

The hit builder samples block faces and checks distance. It does not perform a world raycast or otherwise reject an occluded face. `pathmind/common/src/main/java/com/pathmind/nodes/NodeEntityActionCommandExecutor.java:303-356`

The returned vanilla `ActionResult` is only inspected to decide whether to swing. The future is completed normally regardless of whether the result was accepted, `PASS`, or rejected. `pathmind/common/src/main/java/com/pathmind/nodes/NodeEntityActionCommandExecutor.java:145-190`

Therefore `ok=true` means the interaction call was issued, not that a crafting GUI opened. The mission spec correctly identifies a 1–2 tick GUI gap and retries around it. `testing/specs/lua-mission-survival-bootstrap.yaml:219-227`

Target-resolution failures are unclassified `failed`. Client-unavailable errors are exceptional.

## 5. CRAFT

### Modes

```lua
pathmind.craft_({
  Mode="craft_player_gui",
  Item="oak_planks",
  Amount=12
})

pathmind.craft_({
  Mode="craft_crafting_table",
  Item="stone_pickaxe",
  Amount=1
})
```

Both modes expose `Item` and `Amount`; player-grid mode is the default. `pathmind/common/src/main/java/com/pathmind/nodes/NodeMode.java:43-45`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeParameterDefinitionRegistry.java:61-66`

Player mode accepts either an inventory screen or crafting-table screen. If player mode encounters a crafting-table handler, it upgrades the effective mode to table mode. Explicit table mode requires a crafting-table screen. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:120-143`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:272-285`

`Amount` is desired output-item count, not recipe executions. The executor divides it by per-craft output and rounds up. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:214-216`

### Completion and `produced`

CRAFT places ingredients, polls the result slot up to 20 times with 75 ms delays, quick-moves the result, clears the grid, and completes afterward. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:1601-1664`

The Lua envelope’s `produced` is not the internal planned count. It is the before/after main-inventory delta for the requested `Item`, floored at zero. Thus requesting one plank may report four if that recipe yields four. `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:221-237`; `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:262-290`

### Classified statuses

- `precondition`: required inventory/table GUI not open. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:120-125`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:1408-1413`
- `transient`: crafting screen or compatible handler closed/disappeared during the action. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:130-135`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:1416-1420`
- `unsupported`: recipe needs a 3×3 table while player-grid mode is effective. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:177-188`
- `missing_resource`: ingredient planning cannot source required stacks. The `missing` field is a de-duplicated list of candidate item ids. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:1423-1429`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:1882-1934`
- `failed`: unknown item, no recipe, empty output, or other unclassified failure.
- CRAFT itself does not produce `not_found`; the deterministic `not_found` example belongs to `hotbar_`. `testing/specs/lua-envelope-statuses.yaml:6-20`

### Fresh-item synchronization pitfall

Ingredient availability is derived from `PlayerInventory` slots exposed through the current `ScreenHandler`, not directly from a fresh `getInventory()` snapshot. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:1890-1900`

The mission spec documents that a just-opened handler can temporarily report `missing_resource` for freshly mined items and therefore retries table interaction/crafting. `testing/specs/lua-mission-survival-bootstrap.yaml:219-247`

That retry remains necessary.

## 6. Baritone chat commands

### Internal fallback behavior

Pathmind’s internal `executeCommand("#mine ...")` routes through `ClientMessageSender`. On Fabric, that utility invokes `ClientSendMessageEvents` before forwarding uncancelled chat, which gives client mods an interception point. `pathmind/common/src/main/java/com/pathmind/nodes/Node.java:7615-7621`; `pathmind/common/src/main/java/com/pathmind/util/ClientMessageSender.java:16-27`; `pathmind/common/src/main/java/com/pathmind/util/ClientMessageSender.java:30-55`

COLLECT’s non-API fallback uses that path and then immediately completes. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:53-72`

### Can Lua use `message_("#goto ...")`?

`message_` is not merely client-side display by default. Synthetic MESSAGE nodes default `messageClientSide=false`, and that branch calls `networkHandler.sendChatMessage`. `pathmind/common/src/main/java/com/pathmind/nodes/Node.java:334-371`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeTextIoCommandExecutor.java:681-706`

However, MESSAGE bypasses `ClientMessageSender` and its Fabric send-event dispatch. These repositories do not contain Baritone’s command-listener implementation, so they cannot establish that a direct `sendChatMessage("#goto ...")` is intercepted as a Baritone command rather than sent to the server.

Conclusion: scripts do not currently have a supported or source-verifiable Baritone command escape hatch. `message_` should not be used for `#goto`, `#mine`, or `#follow`.

### Minimal exposure required

A minimal fork-owned exposure would need:

1. A public addon-runtime method or catalog action such as `baritone_command`.
2. Client-thread dispatch through the same `ClientMessageSender.send` path used by internal fallbacks, or preferably a direct Baritone command-manager proxy.
3. Validation of nonblank `#...` input.
4. An explicit completion contract:
   - dispatch-only `{ok=true}` for arbitrary commands, or
   - tracked completion for the small subset where Pathmind can observe the responsible Baritone process.
5. `unsupported` when Baritone is absent and a generic/structured failure when dispatch is rejected.

No upstream node semantics need to change for this; it belongs in the fork API/invoker layer.

## 7. Sensors and reads

### Curated reads

| Call | Return/behavior |
|---|---|
| `pathmind.getPosition()` | `{x=<double>,y=<double>,z=<double>}`. Returns zeroes on unavailable client or a 30-second read timeout. |
| `pathmind.getBlock(x,y,z)` | Namespaced block id; coordinates are floored. Returns `nil` when the world/chunk is unavailable or the read times out. |
| `pathmind.getInventory()` | 1-indexed array of `{slot=<0-based>,item="namespace:id",count=n}` for non-empty main-inventory slots 0–35. |
| `pathmind.getVar(name)` | Scalar, coordinate table, runtime-list table, or `nil`. |
| `pathmind.setVar(name,value)` | Writes number/string/boolean, `{x,y,z}`, or a homogeneous non-empty list; returns nothing and raises on unsupported shapes/types. |

Sources: `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java:390-480`; `pathmind/common/src/main/java/com/pathmind/execution/PathmindRuntimeImpl.java:449-587`; `pathmind/common/src/main/java/com/pathmind/execution/PathmindRuntimeImpl.java:68-145`; `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java:142-223`

### Sensor node invocability

No graph sensor node is script-invocable. The invoker allows only WORLD, PLAYER, INTERFACE, plus WAIT, while all `SENSOR_*` nodes are in the SENSORS category. `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:29-37`; `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:315-325`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeTypeDefinition.java:44-77`

Relevant mission helpers that are invocable but are actions rather than sensors include `open_inventory_`, `close_gui_`, `hotbar_`, `interact_`, and `message_`. The curated reads above are the only direct world/player reads needed by this mission.

## 8. Waiting, timing, and watchdogs

### Intended WAIT modes

WAIT supports seconds, ticks, minutes, and hours. Tick duration is converted with 0.05 seconds per tick. `pathmind/common/src/main/java/com/pathmind/nodes/NodeMode.java:80-84`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeFlowCommandExecutor.java:142-170`

### Synthetic-action WAIT defect

The WAIT executor captures the current execution id, then completes immediately if its own node id is not the active node for that execution. `pathmind/common/src/main/java/com/pathmind/nodes/NodeFlowCommandExecutor.java:172-198`; `pathmind/common/src/main/java/com/pathmind/execution/ExecutionManager.java:1269-1275`

The addon invoker creates a fresh synthetic node and dispatches it directly; that synthetic WAIT node is not the active Lua Script graph node. `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:101-170`

Therefore `pathmind.wait_` appears to hit the “not active on node” branch and complete without waiting. This should be confirmed with an integration test, but it follows directly from the node-id comparison and synthetic dispatch model. The current mission’s one-second WAIT cannot be treated as effective.

### Lua compute budget

Generated actions call `future.get()` with `blocked=true`. While blocked, the VM watchdog skips checks, and the blocked wall time is subtracted from compute accounting. `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java:359-387`; `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/CobaltVm.java:150-172`

The pure-Lua compute budget is five seconds and is checked every 250 ms. `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/CobaltVm.java:59-72`

Long Baritone actions therefore do not consume Lua compute budget. The primary GOTO/COLLECT tracker has a 60-minute ceiling, but a different action future that never completes could leave the worker blocked indefinitely because the watchdog deliberately ignores blocked calls.

## 9. Known pitfalls to design around

1. **COLLECT Amount 1 can complete before pickup.** The single-block path completes after block breaking, not inventory pickup. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCollectCommandExecutor.java:291-332`
2. **Amount 2+ is not inventory-confirmed either.** It waits for Baritone process inactivity plus 750 ms, not an inventory count. `pathmind/common/src/main/java/com/pathmind/execution/PreciseCompletionTracker.java:281-329`
3. **Freshly mined items may be missing from a just-opened crafting handler.** CRAFT reads handler slots and returns `missing_resource`; the spec already calls this out. `pathmind/common/src/main/java/com/pathmind/nodes/NodeCraftCommandExecutor.java:1882-1920`; `testing/specs/lua-mission-survival-bootstrap.yaml:219-247`
4. **INTERACT completes at click dispatch, not GUI-open.** It unconditionally completes after `interactBlock`. `pathmind/common/src/main/java/com/pathmind/nodes/NodeEntityActionCommandExecutor.java:145-190`
5. **INTERACT has no real LOS validation and does not classify vanilla rejection.** `pathmind/common/src/main/java/com/pathmind/nodes/NodeEntityActionCommandExecutor.java:303-356`
6. **GOTO completion is not arrival verification.** Inactive Baritone processes are treated as success and a never-started goal is gracefully completed. `pathmind/common/src/main/java/com/pathmind/execution/PreciseCompletionTracker.java:240-263`
7. **GOTO block search is local to 64 blocks.** `pathmind/common/src/main/java/com/pathmind/nodes/Node.java:303`; `pathmind/common/src/main/java/com/pathmind/nodes/NodeNavigationCommandExecutor.java:1498-1500`
8. **GOTO mining is disabled by default.** `pathmind/common/src/main/java/com/pathmind/data/SettingsManager.java:47-50`
9. **`collected` reflects the literal requested id, not drop translation.** `pathmind/common/src/main/java/com/pathmind/nodes/AddonActionInvoker.java:239-247`
10. **`wait_` is not a dependable script delay under the current synthetic-node dispatch.**
11. **`message_` is outgoing chat by default.** Mission logs may be visible to a multiplayer server rather than being purely local. `pathmind/common/src/main/java/com/pathmind/nodes/NodeTextIoCommandExecutor.java:681-706`

---

# Part 2 — Redesign proposal

## Proposed flow

### Phase 1: logs

Call tracked `collect_({Block="oak_log",Amount=3})`, then verify main inventory. Retry once if the envelope or inventory check indicates failure.

Inventory remains the authority because COLLECT’s completion and `collected` field do not guarantee pickup.

### Phase 2: player-grid supplies

Open inventory and explicitly use `Mode="craft_player_gui"` for:

- 12 oak planks
- 1 crafting table
- 4 sticks

Handle statuses as follows:

- `precondition` or `transient`: close/reopen the GUI and retry.
- `missing_resource`: inspect `missing`; collect logs or craft the named intermediate.
- `unsupported`: stop immediately with a phase-specific diagnosis.
- Other `failed`/raised results: stop after bounded retries.

### Phase 3: table and wooden pickaxe

Use `goto_block grass_block` to leave a tree canopy.

Placement still needs explicit coordinates, but all manual distance and world-state scanning can be deleted. Try a short fixed list of adjacent offsets and let PLACE enforce reach, replaceability, support, inventory selection, vanilla placement validity, and block appearance.

Do not call `hotbar_`: PLACE selects or moves the crafting table itself.

After placement:

1. `goto_block crafting_table`
2. `interact_({Block="crafting_table"})`
3. Emit a phase log line; MESSAGE’s future has a built-in 120 ms completion delay.
4. Attempt explicit table-mode crafting.
5. Retry on `precondition`, `transient`, or `missing_resource`.

The log delay is a temporary workaround, not a replacement for fixing `wait_`.

### Phase 4: stone upgrade

Call `collect_({Block="stone",Amount=8})`, verify at least three cobblestone, then use `goto_block crafting_table`.

This replaces the saved-home coordinate return loop and horizontal distance math. If `goto_block` false-succeeds, subsequent INTERACT/CRAFT preconditions provide a second check.

### Phase 5: descent

Call `goto_({Mode="goto_y",Y=30})` up to three times and use the envelope’s final `y` for verification.

This is the best current native action, but success is conditional:

- With GOTO breaking enabled, Baritone can mine toward the Y goal.
- With default settings, it can only use naturally traversable routes.
- A false-success GOTO is caught by the final-Y check.

A robust arbitrary-terrain descent requires either a script-exposed per-call `AllowBreak` option or a supported Baritone-command action.

## Current hand-rolled code that can be deleted

| Delete | Why |
|---|---|
| `tableSpots()` 7×7 scan | PLACE already validates reach, replaceability, support, held item, vanilla placement state, and block appearance. |
| Manual square-root placement-distance calculation | Native PLACE uses the actual player interaction range. |
| Manual `getBlock` post-placement verification | PLACE polls until the desired block appears. |
| `hotbar_({Item="crafting_table"})` | PLACE searches inventory, moves the item to hotbar, and selects it. |
| Stored `tx/ty/tz` table coordinates | Later navigation can use `goto_block crafting_table`. |
| Saved-home return loop and XZ distance calculation | `goto_block crafting_table` plus subsequent INTERACT/CRAFT status is a simpler bounded confirmation. |
| Final validation-stone collection | Stone tools and final inventory/Y verification already establish the mission result. |
| Assumption that `wait_` bridges GUI ticks | Current synthetic WAIT is not dependable. |

Current manual placement and return logic occupies `pathmind-lua/examples/survival-bootstrap.lua:125-205` and `pathmind-lua/examples/survival-bootstrap.lua:243-263`.

## Logic that is still genuinely needed

- A small adjacent-coordinate list, because PLACE exposes no “place at a suitable nearby location” primitive.
- Inventory snapshots after collection and crafting, because COLLECT is not pickup-confirmed and GOTO/COLLECT success fields are incomplete.
- Crafting-table retries, because INTERACT does not wait for or verify GUI opening.
- Status-directed recovery.
- Final-Y verification, because GOTO does not verify arrival.
- Bounded retry counts and phase-specific `[fail]` lines, because expected world failures must not become silent or infinite loops.

---

# DRAFT — replacement `survival-bootstrap.lua`

```lua
-- DRAFT: Survival Bootstrap using native Pathmind/Baritone primitives.
-- Review before replacing the shipped example and embedded vision-spec copy.

local targetDepth = 30

local function log(text)
  local called = pcall(pathmind.message_, { text = text })
  if not called then
    print(text)
  end
end

local function invoke(label, fn)
  local called, r = pcall(fn)
  if not called then
    log("[error] " .. label .. " raised: " .. tostring(r))
    return { ok = false, status = "raised", message = tostring(r) }
  end
  if type(r) ~= "table" then
    log("[error] " .. label .. " returned no envelope (" .. type(r) .. ")")
    return { ok = false, status = "raised", message = "no result envelope" }
  end
  if not r.ok then
    local detail = ""
    if type(r.missing) == "table" and #r.missing > 0 then
      detail = " (missing: " .. table.concat(r.missing, ", ") .. ")"
    end
    log("[error] " .. label .. ": [" .. tostring(r.status) .. "] "
      .. tostring(r.message) .. detail)
  end
  return r
end

local function closeGui()
  pcall(pathmind.close_gui_)
end

local function inventory(label, items)
  local called, inv = pcall(pathmind.getInventory)
  if not called or type(inv) ~= "table" then
    log("[error] inventory " .. label .. ": " .. tostring(inv))
    return {}
  end

  local counts = {}
  for i = 1, #inv do
    counts[inv[i].item] = (counts[inv[i].item] or 0) + inv[i].count
  end

  local parts = {}
  for i = 1, #items do
    local id = "minecraft:" .. items[i]
    parts[#parts + 1] = items[i] .. "=" .. tostring(counts[id] or 0)
  end
  log("[inventory] " .. label .. ": " .. table.concat(parts, ", "))
  return counts
end

local function count(inv, item)
  return inv["minecraft:" .. item] or 0
end

local function stop(phase, reason)
  log("[fail] [" .. phase .. "] predicted stop: " .. reason)
end

local function ensureCollected(block, amount, inventoryItem, required, phase, label)
  local found = 0
  local lastMessage = "collection did not complete"

  for attempt = 1, 2 do
    local r = invoke("collect " .. block .. " (attempt " .. attempt .. ")", function()
      return pathmind.collect_({ Block = block, Amount = amount })
    end)

    if r.status == "unsupported" then
      return false, "collect unsupported: " .. tostring(r.message)
    end

    -- MESSAGE waits briefly before its action future completes. This gives drops
    -- and the client inventory a small synchronization window without wait_.
    log("[" .. phase .. "] collection returned; verifying inventory")

    local inv = inventory(label, { inventoryItem })
    found = count(inv, inventoryItem)
    if found >= required then
      return true
    end

    lastMessage = inventoryItem .. "=" .. found .. ", required " .. required
    log("[" .. phase .. "] collection retry: " .. lastMessage)
  end

  return false, lastMessage
end

local craftItem

local function recoverMissing(missing, phase)
  if type(missing) ~= "table" or #missing == 0 then
    return false, "craft reported missing resources without item ids"
  end

  for i = 1, #missing do
    local id = string.gsub(missing[i], "^minecraft:", "")

    if id == "oak_log" then
      local r = invoke("gather missing oak logs", function()
        return pathmind.collect_({ Block = "oak_log", Amount = 3 })
      end)
      if not r.ok then
        return false, tostring(r.message)
      end

    elseif id == "cobblestone" then
      local r = invoke("gather missing cobblestone", function()
        return pathmind.collect_({ Block = "stone", Amount = 3 })
      end)
      if not r.ok then
        return false, tostring(r.message)
      end

    elseif id == "oak_planks" then
      local ok, reason = craftItem("oak_planks", 12, "craft_player_gui", phase)
      if not ok then
        return false, reason
      end

    elseif id == "stick" then
      local ok, reason = craftItem("stick", 4, "craft_player_gui", phase)
      if not ok then
        return false, reason
      end

    else
      return false, "no recovery rule for " .. tostring(missing[i])
    end
  end

  return true
end

local function openCrafting(mode, phase)
  if mode == "craft_player_gui" then
    return invoke("open player inventory", function()
      return pathmind.open_inventory_()
    end)
  end

  local moved = invoke("go to crafting table", function()
    return pathmind.goto_({
      Mode = "goto_block",
      Block = "crafting_table"
    })
  end)
  if not moved.ok then
    return moved
  end

  local interacted = invoke("interact with crafting table", function()
    return pathmind.interact_({ Block = "crafting_table" })
  end)

  -- INTERACT only sends the click. This contract-preserving log also supplies
  -- the MESSAGE executor's short completion delay before CRAFT inspects the GUI.
  log("[" .. phase .. "] crafting-table click sent; checking GUI")
  return interacted
end

craftItem = function(item, amount, mode, phase)
  local lastReason = "craft did not complete"

  for attempt = 1, 3 do
    closeGui()
    local ready = openCrafting(mode, phase)

    if ready.status == "unsupported" then
      return false, tostring(ready.message)
    end

    if ready.ok then
      local r = invoke("craft " .. item .. " (attempt " .. attempt .. ")", function()
        return pathmind.craft_({
          Mode = mode,
          Item = item,
          Amount = amount
        })
      end)
      closeGui()

      if r.ok then
        return true
      end

      if r.status == "unsupported" then
        return false, tostring(r.message)

      elseif r.status == "missing_resource" then
        local recovered, reason = recoverMissing(r.missing, phase)
        if not recovered then
          return false, reason
        end
        lastReason = "ingredients gathered; retrying craft"

      elseif r.status == "precondition" or r.status == "transient" then
        lastReason = tostring(r.status) .. ": " .. tostring(r.message)

      else
        return false, "[" .. tostring(r.status) .. "] " .. tostring(r.message)
      end
    else
      lastReason = "[" .. tostring(ready.status) .. "] " .. tostring(ready.message)
    end

    log("[" .. phase .. "] craft retry " .. attempt .. ": " .. lastReason)
  end

  return false, lastReason
end

log("[mission] Survival Bootstrap started; bounded descent target y<=" .. targetDepth)

-- Phase 1 --------------------------------------------------------------------

log("[1/5] chopping wood: collecting 3 oak logs")
local ok, reason = ensureCollected(
  "oak_log", 3, "oak_log", 3, "1/5", "after logs"
)
if not ok then
  stop("1/5", reason .. "; no oak in range or collection path blocked")
  return
end
log("[1/5] complete: enough wood collected")

-- Phase 2 --------------------------------------------------------------------

log("[2/5] crafting supplies: planks, table, and sticks")

ok, reason = craftItem("oak_planks", 12, "craft_player_gui", "2/5")
if not ok then
  stop("2/5", "oak planks: " .. reason)
  return
end

ok, reason = craftItem("crafting_table", 1, "craft_player_gui", "2/5")
if not ok then
  stop("2/5", "crafting table: " .. reason)
  return
end

ok, reason = craftItem("stick", 4, "craft_player_gui", "2/5")
if not ok then
  stop("2/5", "sticks: " .. reason)
  return
end

local inv = inventory(
  "after supplies",
  { "oak_planks", "crafting_table", "stick" }
)
if count(inv, "crafting_table") < 1 or count(inv, "stick") < 4 then
  stop("2/5", "crafted supplies were not present in inventory")
  return
end
log("[2/5] complete: basic crafting supplies verified")

-- Phase 3 --------------------------------------------------------------------

log("[3/5] wooden tools: placing table and crafting a wooden pickaxe")

local ground = invoke("reposition to grass", function()
  return pathmind.goto_({
    Mode = "goto_block",
    Block = "grass_block"
  })
end)
if not ground.ok then
  stop("3/5", "could not reach nearby grass: " .. tostring(ground.message))
  return
end

local p = pathmind.getPosition()
local px = math.floor(p.x)
local py = math.floor(p.y)
local pz = math.floor(p.z)

-- PLACE still requires coordinates. Try a fixed adjacent ring and let the
-- native action decide reach, obstruction, support, item selection, and success.
local offsets = {
  { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
  { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
}

local tablePlaced = false
for i = 1, #offsets do
  local r = invoke("place crafting table (attempt " .. i .. ")", function()
    return pathmind.place_({
      Block = "crafting_table",
      X = px + offsets[i][1],
      Y = py,
      Z = pz + offsets[i][2]
    })
  end)

  if r.ok then
    tablePlaced = true
    break
  end
  if r.status == "unsupported" then
    stop("3/5", "table placement unsupported: " .. tostring(r.message))
    return
  end
end

if not tablePlaced then
  stop("3/5", "native PLACE rejected all adjacent table positions")
  return
end

ok, reason = craftItem("wooden_pickaxe", 1, "craft_crafting_table", "3/5")
if not ok then
  stop("3/5", "wooden pickaxe: " .. reason)
  return
end

inv = inventory("after wooden pickaxe", { "wooden_pickaxe" })
if count(inv, "wooden_pickaxe") < 1 then
  stop("3/5", "craft returned but wooden_pickaxe was not in inventory")
  return
end
log("[3/5] complete: wooden pickaxe verified")

-- Phase 4 --------------------------------------------------------------------

log("[4/5] stone upgrade: mining stone and crafting a stone pickaxe")

ok, reason = ensureCollected(
  "stone", 8, "cobblestone", 3, "4/5", "after stone"
)
if not ok then
  stop("4/5", reason .. "; stone route or mining failed")
  return
end

ok, reason = craftItem("stone_pickaxe", 1, "craft_crafting_table", "4/5")
if not ok then
  stop("4/5", "stone pickaxe: " .. reason)
  return
end

inv = inventory("after stone pickaxe", { "cobblestone", "stone_pickaxe" })
if count(inv, "stone_pickaxe") < 1 then
  stop("4/5", "craft returned but stone_pickaxe was not in inventory")
  return
end
log("[4/5] complete: stone pickaxe verified")

-- Phase 5 --------------------------------------------------------------------

log("[5/5] descent: Baritone pathing toward y=" .. targetDepth)

local reached = false
local final = pathmind.getPosition()

for attempt = 1, 3 do
  local r = invoke("descend to target depth (attempt " .. attempt .. ")", function()
    return pathmind.goto_({
      Mode = "goto_y",
      Y = targetDepth
    })
  end)

  if r.status == "unsupported" then
    stop("5/5", "descent unsupported: " .. tostring(r.message))
    return
  end

  if r.x ~= nil and r.y ~= nil and r.z ~= nil then
    final = { x = r.x, y = r.y, z = r.z }
  else
    final = pathmind.getPosition()
  end

  log("[position] after descent attempt " .. attempt
    .. ": x=" .. math.floor(final.x)
    .. ", y=" .. math.floor(final.y)
    .. ", z=" .. math.floor(final.z))

  if final.y <= targetDepth then
    reached = true
    break
  end

  log("[5/5] descent retry: current y=" .. math.floor(final.y))
end

if not reached then
  stop("5/5", "descent ended above target at y=" .. math.floor(final.y)
    .. "; enable GOTO block breaking or expose a tracked Baritone command")
  return
end

inventory("at mining depth", { "cobblestone", "stone_pickaxe" })
log("[5/5] complete: reached bounded mining depth with verified stone tools")
log("[success] Survival Bootstrap complete at y=" .. math.floor(final.y))
```

---

# Open questions and risks

1. **Should addon WAIT receive a standalone time source?**  
   WAIT currently depends on graph active-node timing, which does not match synthetic action invocation. A fork-layer wait implementation or synthetic execution context is needed before scripts can rely on `wait_`.

2. **Should COLLECT’s tracked completion include an inventory predicate?**  
   The implementation contradicts the `lua-envelope-statuses` comment. Either the tracker should wait for an inventory delta/required count, or the spec and API documentation should explicitly say “Baritone process ended, pickup not guaranteed.”

3. **Should GOTO verify its goal before success?**  
   Final `x/y/z` exposes enough information for scripts, but every mission must currently reimplement goal verification. A strict addon-layer arrival check would preserve upstream graph semantics while making script GOTO dependable.

4. **How should no-route GOTO be classified?**  
   `transient` or `not_found` would be actionable. Current graceful completion reports `ok=true`, while hard tracker errors raise.

5. **Should GOTO expose per-action break/place options?**  
   `goto_y` cannot reliably descend under the default `gotoAllowBreakWhileExecuting=false`. A fork-only `AllowBreak`/`AllowPlace` argument or dedicated strict navigation API would solve the mission without altering graph defaults.

6. **Should `goto_block` fall through to Baritone’s global get-to-block process?**  
   The current local resolver records failure when no block is found within 64 blocks, making the later `getToBlock` branch effectively unavailable for that case.

7. **Should INTERACT validate vanilla acceptance and screen opening?**  
   At minimum it should classify a rejected click. A table-specific completion option could wait for `CraftingScreen` and eliminate every script’s GUI retry loop.

8. **Is MESSAGE intended for public/server chat?**  
   Synthetic MESSAGE defaults to network chat, while `print` is local Pathmind-prefixed display. The current mission and draft preserve the existing contract, but multiplayer privacy/noise should be reviewed.

9. **Should Baritone commands become a supported addon primitive?**  
   `message_` is not equivalent to internal `executeCommand`. A narrow fork-owned command API would unlock advanced Baritone behavior without exposing graph internals.

10. **Placement remains the last unavoidable coordinate calculation.**  
    The fixed adjacent ring is substantially simpler than the current scan, but a future `place_near_({Block=...})` or optional-coordinate PLACE mode would eliminate it completely.

11. **The accepted mission must be updated in two places.**  
    The vision spec embeds the shipped Lua source specifically to prevent drift, so any eventual replacement must update both `pathmind-lua/examples/survival-bootstrap.lua` and `testing/specs/lua-mission-survival-bootstrap.yaml`. `testing/specs/lua-mission-survival-bootstrap.yaml:1-2`