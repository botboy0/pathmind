# Design: Baritone Integration

**Status:** implemented (2026-07-13). The [Lua Scripting Reference](../guides/lua-scripting.md)
documents the user-facing contract ("Baritone actions" section); this page keeps the
design rationale. Builds on the [action-result envelopes](./action-result-envelopes.md)
model and the findings of the [mission-primitives research](../research/mission-primitives.md).

## The problem

Baritone is an optional, reflection-bridged dependency (compile-only, not declared in
`fabric.mod.json`). Before this milestone the script surface handled its absence and
its failure modes dishonestly:

1. **Missing Baritone raised.** With no Baritone installed, `goto_`/`collect_` completed
   exceptionally (`RuntimeException("Baritone not available")`) — a Lua *raise* for what
   is clearly a class-2 "the world says no" condition in the envelope model.
2. **GOTO could false-succeed.** `PreciseCompletionTracker` treats process inactivity as
   completion and never compares positions. A goto whose path calculation failed
   ("never became active") was *gracefully* completed after an 8-second grace — an
   `ok = true` envelope for a navigation that never moved.
3. **No supported Baritone command channel.** `message_("#goto …")` bypasses
   `ClientMessageSender` and the Fabric send events; whether Baritone intercepts it is
   unverifiable from these repos (research §6).
4. **`goto_y` could not descend.** Break/place during GOTO is guarded by two global
   settings defaulting to `false`, with no per-call override — "mine down to Y 30" was
   terrain-dependent.

## Dependency states

`BaritoneDependencyChecker` distinguishes three states; the integration keys on
**API-present** everywhere the addon surface is concerned:

| State | `isBaritonePresent` | `isBaritoneApiPresent` | Script actions | Graph nodes |
|---|---|---|---|---|
| A — not installed | false | false | `unsupported` envelope | hidden in sidebar, `missing_baritone` validation ERROR, runtime error notification |
| B — mod present, API unresolvable | true | false | `unsupported` envelope | editor popup + validation ERROR; runtime falls back to `#…` chat commands |
| C — fully available | true | true | full contract below | normal |

Scripts that never touch Baritone actions run completely clean in states A/B — no
warning, no toast, no raise. That asymmetry is deliberate: a *graph author* placed a
Baritone node in the editor and deserves loud feedback; a *script* gets a
machine-readable envelope it can plan around.

## Architecture

Everything lives in the fork layer (invoker + fork-only classes); upstream executors
gained only additive one-liners.

```mermaid
flowchart TB
  subgraph invoker [AddonActionInvoker — fork layer]
    GATE["missing-Baritone gate<br/>requiresBaritone && !apiPresent → unsupported"]
    ARGS["arg loop<br/>Mode + AllowBreak/AllowPlace interception"]
    POST["post-success check<br/>GracefulTaskStops.consume → no_route<br/>verifyGotoArrival → off_target"]
    CMD["baritone_command<br/>synthetic action"]
  end
  subgraph upstream [Upstream (additive touches)]
    EXE["NodeNavigationCommandExecutor<br/>setAddonGotoGoal(goal) at goal creation"]
    TRK["PreciseCompletionTracker<br/>failTaskGracefully → GracefulTaskStops.mark"]
    NODE["Node<br/>addonGotoGoal + guard overrides"]
  end
  subgraph baritone [Baritone (reflection)]
    GOAL["Goal.isInGoal(x,y,z)"]
    MGR["ICommandManager.execute(String)"]
  end
  ARGS --> NODE
  EXE --> NODE
  TRK -. side channel .-> POST
  NODE --> POST
  POST --> GOAL
  CMD --> MGR
```

### GOTO arrival verification — ask Baritone's own criterion

Instead of hand-rolled, mode-aware tolerance math, the navigation executor stores the
goal object it already creates (`GoalBlock`/`GoalXZ`/`GoalYLevel`/`GoalNear`) on the
node (`setAddonGotoGoal`, fork-only field). After a nominally successful run the
invoker calls `goal.isInGoal(finalBlockPos)` reflectively — the exact predicate
Baritone itself stops on, covering every mode including `goto_block`'s
"near the block" semantics for free. Outcomes:

- **`no_route`** — the tracker recorded a graceful stop ("task never became active"):
  path calculation failed, the player never moved. Signalled through
  `GracefulTaskStops`, a fork-only consume-once side channel keyed on the node future
  (the tracker completes such futures *normally* for graph semantics, so the failure
  counter cannot carry this).
- **`off_target`** — Baritone ran and went idle, but the final position does not
  satisfy the goal. Note that an unreachable goal often lands here rather than in
  `no_route`: Baritone's custom-goal process can flicker active during the failed
  path calculation (observed 2026-07-13), which counts as "started" for the tracker.
  Scripts should treat both statuses as "not going to arrive by waiting"; which one
  fires is Baritone timing.
- The `getToBlock` fallback branch has no goal object; verification skips rather than
  false-fails (documented non-guarantee).

The same graceful-stop marker classifies COLLECT's "mine task never became active" as
`not_found` (the target block wasn't found nearby).

### `baritone_command` — a synthetic catalog action

Not a `NodeType`: the invoker special-cases the name before enum resolution, and
`AddonActionCatalog` appends a hand-built entry — the addon then generates
`pathmind.baritone_command_` automatically from the catalog. Dispatch goes through
`BaritoneApiProxy.executeCommand` → `IBaritone.getCommandManager().execute(String)`,
verified against the Baritone sources: the boolean means "a command matched and ran";
`CommandException`s are handled inside Baritone (printed to its chat), never thrown.
Hence the **dispatch-only** contract: `ok = true` = accepted, observation is the
script's job. Chat (`ClientMessageSender`) is deliberately *not* used — the command
manager is the same entry point Baritone's own chat interception feeds.

### Per-call `AllowBreak`/`AllowPlace`

The invoker intercepts the two boolean arguments (GOTO/TRAVEL only) and sets fork-only
per-node overrides; `isGotoAllowBreakWhileExecuting()`/`…Place…()` consult the override
before the global settings. Graph nodes never set the override, and the existing
guard/restore machinery (`applyBaritoneMovementGuardsDuringGoto`) works unchanged —
it already reads those getters per node.

### Graph-side consistency fixes

- `GraphValidator` finally *uses* its `baritoneAvailable` parameter: Baritone nodes
  produce a `missing_baritone` ERROR, mirroring `missing_ui_utils`.
- The sidebar seeds its availability from `isBaritoneApiPresent()` (previously
  `isBaritonePresent()`, which kept nodes visible in state B while the editor popup
  said Baritone was missing).

## Contract pinning

- Unit: `FailureDetailBaritoneVocabularyTest`, `GracefulTaskStopsTest`,
  `AddonActionInvokerBaritoneSurfaceTest`, `AddonActionInvokerGotoVerificationTest`,
  `AddonGotoGuardOverrideTest`, `AddonCollectSuccessFieldsTest`,
  `GraphValidatorBaritoneTest` (red-first).
- Vision: `testing/specs/lua-baritone-integration.yaml` — deterministic triggers for
  `no_route` (goto straight down, break disabled), `baritone_command` `#goto` with a
  position check, unknown-command `failed`, and an `AllowBreak = true` descent.

## Future options (deliberately not v1)

- **Event-driven tracking:** `IBaritone.getGameEventHandler().registerEventListener`
  delivers `PathEvent`s (`CALC_FAILED`, `AT_GOAL`, `CANCELED`, …) — a
  `java.lang.reflect.Proxy` listener could replace the polling tracker's heuristics
  with exact signals, and would let `baritone_command` offer tracked completion for
  the process-backed subset.
- Drop translation for `collected` (stone → cobblestone) if scripts ever need it —
  today the field is documented as informational.
