# Phase 2: Lua VM + Core Bindings - Research

**Researched:** 2026-06-13
**Domain:** Cobalt 0.7.3 Lua VM integration, PathmindRuntime API extension, async-sync bridging
**Confidence:** MEDIUM (Cobalt API verified via GitHub source; Pathmind internals verified via codebase grep)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**VM:** Cobalt 0.7.3 (CC:Tweaked fork), shadow-relocated into the addon jar. Manually-built globals — NO luajava, NO standardGlobals().

**Runtime bridge:** A separate `PathmindRuntime` services interface added to `com.pathmind.api.addon`, reachable from `AddonNodeContext`. It exposes:
- `Object getVariable(String)` / `setVariable(String, Object)` backed by ExecutionManager global runtime vars
- A typed awaitable `CompletableFuture<?> moveTo(x, y, z)` wrapping PathmindNavigator/Baritone
- Game-state accessors (position/inventory/block) with main-thread-safe dispatch handled Pathmind-side

**Lua API:** Single global `pathmind` table. `getPosition` → `{x,y,z}` table; `getInventory` → array of `{slot,item="ns:id",count}`; `getBlock` → namespaced id string or nil; vars marshal number/string/boolean only.

**Timeout:** Clock PAUSES while awaiting an action (measures Lua compute time, not navigation wait); ~5s default; global setting; Cobalt debug-hook/instruction abort + Thread.interrupt() safety net.

**Errors:** Surface message+line to chat (v1); traceback to log; stop graph at the Script node; binding-call failures throw a Lua error; getBlock out-of-range returns nil. NO node-level error persistence in Phase 2.

### Claude's Discretion

- Exact name/package of the runtime services interface (`PathmindRuntime` is a working name) and how `AddonNodeContext` references it.
- Worker-thread pooling strategy (dedicated thread per execution vs small cached pool).
- Exact main-thread dispatch idiom (MinecraftClient.execute vs BackgroundStartRunner pattern) for the Pathmind-side game-state accessors.
- Exact Cobalt 0.7.3 interrupt/debug-hook API — verified against source in this research.
- Exact PathmindNavigator/Baritone completion-callback API for awaitable `moveTo` — verified in this research.

### Deferred Ideas (OUT OF SCOPE)

- Node-level error persistence + co-located in-node error display → Phase 3 (EDIT-03).
- Generic `invokeAction(name, args)` dispatch → v2.
- Per-node timeout override → v2.
- Lua table / userdata variable marshaling → v2.
- Robust sandboxing / instruction budget beyond the wall-clock timeout → v2 (LUA-V2-01).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LUA-02 | When the Script node executes, its Lua script runs on a worker thread and the node tree continues only after the script finishes | Cobalt `LuaThread.runMain` runs synchronously on worker; `LuaNodeExecutor.execute()` returns a CF immediately and completes it when the Lua thread exits |
| LUA-03 | Each execution gets a fresh, sandboxed Lua environment (manually-built globals — no luajava, no standardGlobals()) | `CoreLibraries.standardGlobals(state)` gives base/table/string/math/coroutine; exclude SystemBaseLib/PackageLib/IoLib/OsLib; add `pathmind` table manually |
| LUA-04 | A runaway script cannot hang the game — wall-clock timeout with thread interrupt as a safety net | `LuaState.interrupt()` from a timer on the worker; `InterruptHandler.interrupted()` returns `InterruptAction.SUSPEND` which surfaces as LuaError |
| BIND-01 | Script can read/write node-tree variables via `pathmind.getVar`/`pathmind.setVar` | `ExecutionManager.getRuntimeVariableFromAnyActiveChain` / `setRuntimeVariableForAnyActiveChain`; scalar values stored as String under well-known keys |
| BIND-02 | Script can invoke Pathmind actions (moveTo) and block until the action completes | `PathmindNavigator.startGoto(BlockPos, label, CompletableFuture<Void>)` — future completed/exceptionally when nav done; worker blocks on the CF |
| BIND-03 | Script can query game state (position/inventory/block) with main-thread-safe dispatch | `MinecraftClient.execute(Runnable)` on client thread + `CompletableFuture` hand-back; worker blocks for result |
| BIND-04 | Script errors surface to the user with message and line number (never silently swallowed) | `LuaError.getMessage()` contains message+line; `client.player.sendMessage(Text.literal(...))` for chat; `System.err.println` for traceback |
</phase_requirements>

---

## Summary

Phase 2 wires the stub `LuaNodeExecutor` (currently a no-op pass-through) into a real Cobalt 0.7.3 Lua VM, adds the `PathmindRuntime` interface to the upstream API, and implements the full `pathmind.*` binding surface. Two repositories are in play and both must remain loadable after every plan: Pathmind (Cobalt-free) and the addon (shadow-bundles Cobalt).

The critical design centre-point is the async-sync bridge: the game tick thread must never block, but Lua is inherently synchronous. The solution is that `LuaNodeExecutor.execute()` returns a `CompletableFuture<NodeResult>` immediately, then spins a Java worker thread that runs Cobalt's `LuaThread.runMain(...)` synchronously. When the Lua script calls a blocking action such as `moveTo`, the worker suspends by blocking on a `CompletableFuture<Void>` that PathmindNavigator completes from the tick thread when navigation ends. The timeout clock only measures Lua compute time; it is paused while the worker is blocked on an action future.

**Primary recommendation:** Build the phase as four vertical slices — (1) bare Cobalt VM executes a return-arithmetic script and returns SUCCESS/FAILURE; (2) `getVar`/`setVar` round-trip works; (3) `moveTo` awaitable with Baritone absent/present paths; (4) game-state queries (`getPosition`, `getInventory`, `getBlock`). Each slice leaves both repos loadable.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Cobalt VM lifecycle (fresh globals, run, interrupt) | Addon (worker thread) | — | Only the addon knows Cobalt types; Cobalt is shadow-relocated so Pathmind never sees it |
| `pathmind.*` Lua binding table | Addon | — | Bindings call `PathmindRuntime` methods; no MC API access |
| `PathmindRuntime` interface definition | Pathmind API (`com.pathmind.api.addon`) | — | Upstream-bound; must be version-stable |
| `PathmindRuntime` implementation | Pathmind impl (`com.pathmind.execution`) | — | Accesses ExecutionManager, PathmindNavigator, MinecraftClient |
| Variable store (get/set) | Pathmind impl → ExecutionManager | Addon (marshaling) | ExecutionManager owns the authoritative variable map |
| `moveTo` awaitable | Pathmind impl → PathmindNavigator | Addon (blocks on CF) | PathmindNavigator already has `startGoto(…, CompletableFuture<Void>)` |
| Game-state reads (position/inventory/block) | Pathmind impl (dispatches to client thread) | — | MC API reads must be on the render/client thread |
| Error surfacing (chat + log) | Pathmind impl (chat dispatch) | Addon (catch LuaError, populate NodeResult.FAILURE) | Chat dispatch requires client thread; Lua error is caught addon-side |
| Timeout enforcement | Addon (interrupt via LuaState.interrupt()) | Pathmind settings (timeout value) | VM reference lives in the addon; setting lives in SettingsManager |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.squiddev:Cobalt` | 0.7.3 | Lua 5.1-compatible VM, re-entrant, interruptible | CC:Tweaked fork; battle-tested in Minecraft mods; supports `LuaState.interrupt()` from outside the VM thread; locked at roadmap creation |
| `com.gradleup.shadow` | 9.4.1 | Shadow-JAR the Cobalt runtime into the addon JAR | Already in Pathmind build ecosystem; prevents class collision with other mods that might bundle Cobalt |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `java.util.concurrent.CompletableFuture` | JDK 21 | Async-sync bridge between worker and game tick thread | All awaitable actions; already used by ExecutionManager / PathmindNavigator |
| `java.util.concurrent.Executors.newCachedThreadPool()` | JDK 21 | Worker thread per Lua execution | Recommended over ForkJoinPool; Lua thread blocks on I/O-like futures, not CPU-bound |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Cobalt 0.7.3 | LuaJ 3.x | No re-entrancy; cannot interrupt from outside thread; Cobalt is the locked decision |
| `Executors.newCachedThreadPool()` | `ForkJoinPool.commonPool()` | ForkJoin is for non-blocking compute; blocking a FJP worker during navigation starves other tasks |
| `client.execute(r)` for main-thread dispatch | BackgroundStartRunner | BackgroundStartRunner is for full graph-launch flows; `client.execute` is the idiom for single fire-and-forget main-thread operations in Node.java (line 3819) |

**Installation (addon build.gradle.kts — add to dependencies block):**
```kotlin
// Cobalt Lua VM — shadow-bundled and relocated
implementation("org.squiddev:Cobalt:0.7.3")

// Shadow plugin already in addon's plugins block for Architectury;
// add relocation rule in shadowJar task:
tasks.shadowJar {
    relocate("org.squiddev.cobalt", "com.mrmysterium.pathmindlua.shadow.cobalt")
}
```

**Repository (addon build.gradle.kts — add to repositories block):**
```kotlin
maven {
    name = "SquidDev"
    url = uri("https://squiddev.cc/maven")
}
```

---

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `org.squiddev:Cobalt:0.7.3` | squiddev.cc/maven (custom Maven) | Aug 27, 2023 (~2 yrs) | N/A (non-central, mod ecosystem) | github.com/cc-tweaked/Cobalt | OK | Approved |

**Packages removed due to SLOP verdict:** none

**Packages flagged as suspicious SUS:** none

**Notes:** Cobalt is not on Maven Central — it is hosted at `https://squiddev.cc/maven`. The artifact was confirmed present at `https://squiddev.cc/maven/org/squiddev/Cobalt/0.7.3/` with published date August 27, 2023. The source repository is actively maintained under the `cc-tweaked` GitHub org. The addon build already declares `mavenCentral()` and `mavenLocal()` — the SquidDev repository must be added explicitly. [CITED: https://squiddev.cc/maven/org/squiddev/Cobalt/0.7.3/]

---

## Architecture Patterns

### System Architecture Diagram

```
Minecraft tick thread                    Lua worker thread
────────────────────                     ─────────────────
PathmindClientMod.tick()
  └─ ExecutionManager.advanceGraph()
       └─ Node.execute() [ADDON type]
            └─ LuaNodeExecutor.execute(ctx)──► returns CF<NodeResult> immediately
                                               │
                                               ▼
                                         [Thread.start: LuaWorker]
                                               │
                                         LuaState + fresh globals
                                         LuaThread.runMain(state, closure)
                                               │
                                         ┌─────▼──────────────┐
                                         │ Lua script runs     │
                                         │ pathmind.getVar()   │──► PathmindRuntime.getVariable(name)
                                         │                     │      └─ ExecutionManager.getRuntimeVariableFromAnyActiveChain
                                         │ pathmind.moveTo()   │──► PathmindRuntime.moveTo(x,y,z)
                                         │                     │      └─ PathmindNavigator.startGoto(BlockPos, label, CF)
                                         │ [worker BLOCKS      │         ◄── CF.get() blocks here
                                         │  on nav CF]         │         ◄── Nav tick completes CF
                                         │                     │
                                         │ pathmind.getPos()   │──► PathmindRuntime.getPosition()
                                         │                     │      └─ client.execute(r) + CF result
                                         │                     │         [blocks worker until tick thread responds]
                                         └─────────────────────┘
                                               │
                              LuaError?  ──────┤
                              │                │ SUCCESS
                              ▼                ▼
                         CF<NodeResult>.  CF<NodeResult>.
                         complete(FAILURE) complete(SUCCESS)
                              │
                              ▼
                     (Pathmind-side impl)
                     client.execute(() ->
                       player.sendMessage(chat))
                     System.err.println(traceback)

Timeout path:
  ScheduledExecutor fires after 5s of compute
    → luaState.interrupt()
    → InterruptHandler.interrupted() → InterruptAction.SUSPEND → LuaError
    → CF<NodeResult>.complete(FAILURE) + chat error message
  Thread.interrupt() as safety net if Cobalt does not surface the error in time
```

### Recommended Project Structure

**Pathmind (upstream API additions, `common/src/main/java/`):**
```
com/pathmind/api/addon/
├── AddonNodeContext.java          (add getRuntime() method)
├── PathmindRuntime.java           (NEW — the runtime services interface)
└── [existing Phase 1 files unchanged]

com/pathmind/execution/
└── PathmindRuntimeImpl.java       (NEW — implements PathmindRuntime, accesses ExecutionManager/PathmindNavigator/MC)
```

**Addon repo (`src/main/java/com/mrmysterium/pathmindlua/`):**
```
LuaNodeExecutor.java               (replace no-op with Cobalt lifecycle)
vm/
├── CobaltVm.java                  (LuaState creation, globals builder, run/interrupt lifecycle)
├── LuaGlobals.java                (builds the sandboxed globals + pathmind table)
└── PathmindBindings.java          (all pathmind.* function implementations)
```

### Pattern 1: Cobalt VM Lifecycle (per execution)

**What:** Create a fresh `LuaState`, build globals manually (no SystemLibraries), load the script string, call `LuaThread.runMain`, handle `LuaError`.

**When to use:** Every call to `LuaNodeExecutor.execute()` — always fresh, never reuse state between executions.

```java
// Source: cc-tweaked/Cobalt v0.7.3 src/test/java/.../ScriptHelper.java + LuaThread.java
LuaState state = LuaState.builder()
    .interruptHandler(interruptHandler)   // see Pattern 3 for impl
    .build();

LuaTable globals = CoreLibraries.standardGlobals(state);  // base/table/string/math/coroutine/utf8
// DO NOT add SystemBaseLib, PackageLib, IoLib, OsLib — excluded for sandbox

LuaTable pathmindTable = buildPathmindTable(state, runtime); // see Pattern 2
globals.rawset("pathmind", pathmindTable);

// Load from String via InputStream
LuaClosure closure = LoadState.load(
    state,
    new ByteArrayInputStream(scriptText.getBytes(StandardCharsets.UTF_8)),
    "@script",    // chunk name (shown in error messages)
    globals
);

try {
    LuaThread.runMain(state, closure);  // blocks this worker thread until done
    resultFuture.complete(NodeResult.SUCCESS);
} catch (LuaError e) {
    // e.getMessage() contains "script:3: my error message"
    resultFuture.complete(NodeResult.FAILURE);
    // dispatch chat + log on main thread (see Pattern 5)
}
```
[CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/test/java/org/squiddev/cobalt/ScriptHelper.java]
[CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/LuaThread.java]

### Pattern 2: Building the `pathmind` Table with RegisteredFunction

**What:** All Lua API functions live in a single `pathmind` global table; Java methods are registered via `RegisteredFunction.bind`.

**When to use:** In `buildPathmindTable()` called once per execution.

```java
// Source: cc-tweaked/Cobalt v0.7.3 src/main/java/org/squiddev/cobalt/function/RegisteredFunction.java
// Source: cc-tweaked/Cobalt v0.7.3 src/test/java/org/squiddev/cobalt/TestLib.java
import static org.squiddev.cobalt.function.RegisteredFunction.ofV;
import static org.squiddev.cobalt.function.RegisteredFunction.bind;
import static org.squiddev.cobalt.ValueFactory.*;

LuaTable buildPathmindTable(LuaState state, PathmindRuntime runtime) {
    return bind(new RegisteredFunction[]{
        ofV("getVar",       (s, args) -> getVar(s, args, runtime)),
        ofV("setVar",       (s, args) -> setVar(s, args, runtime)),
        ofV("moveTo",       (s, args) -> moveTo(s, args, runtime)),
        ofV("getPosition",  (s, args) -> getPosition(s, args, runtime)),
        ofV("getInventory", (s, args) -> getInventory(s, args, runtime)),
        ofV("getBlock",     (s, args) -> getBlock(s, args, runtime)),
    });
}

// Example: getVar implementation
static Varargs getVar(LuaState state, Varargs args, PathmindRuntime runtime) throws LuaError {
    String name = args.arg(1).checkLuaString().toString();
    Object value = runtime.getVariable(name);
    if (value == null)       return Constants.NIL;
    if (value instanceof Double d)  return valueOf(d);
    if (value instanceof String s)  return valueOf(s);
    if (value instanceof Boolean b) return valueOf(b);
    return Constants.NIL;
}
```
[CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/function/RegisteredFunction.java]

### Pattern 3: Timeout Interrupt

**What:** A `ScheduledExecutorService` fires after N seconds of Lua compute time; it calls `state.interrupt()`. The `InterruptHandler` registered on the state decides whether to raise a `LuaError`.

**When to use:** Immediately after starting the Lua worker thread; cancelled when the script completes normally.

```java
// Source: cc-tweaked/Cobalt v0.7.3 src/main/java/org/squiddev/cobalt/LuaState.java
// Source: cc-tweaked/Cobalt v0.7.3 src/main/java/org/squiddev/cobalt/interrupt/InterruptHandler.java
// Source: cc-tweaked/Cobalt v0.7.3 src/main/java/org/squiddev/cobalt/interrupt/InterruptAction.java
import org.squiddev.cobalt.interrupt.InterruptHandler;
import org.squiddev.cobalt.interrupt.InterruptAction;

// Time budget: only Lua compute; paused while worker is blocked on an action future.
// Implemented via tracking elapsed time across resumed segments.
AtomicBoolean timedOut = new AtomicBoolean(false);

InterruptHandler handler = () -> {
    if (timedOut.get()) {
        throw new LuaError("Script timeout: exceeded maximum compute time");
    }
    return InterruptAction.CONTINUE;
};

LuaState state = LuaState.builder()
    .interruptHandler(handler)
    .build();

// Before blocking on an action future, note the start time:
long computeStartMs = System.currentTimeMillis();
long computedBeforePauseMs = 0; // accumulated across resumed segments

// After resuming from a blocked action:
computedBeforePauseMs += System.currentTimeMillis() - computeStartMs;
computeStartMs = System.currentTimeMillis();

// Timer fires if (computedBeforePauseMs + current elapsed) > budget
// Simplified: use a single ScheduledFuture that fires after budget ms;
// reset it every time the worker resumes from an action.
// OR: use LuaState.interrupt() from a separate timer thread; the handler checks elapsed compute time.

// Safety net: Thread.interrupt() if LuaError does not propagate within a grace period
workerThread.interrupt();
```
[CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/interrupt/InterruptHandler.java]
[CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/LuaState.java]

### Pattern 4: Awaitable `moveTo` (worker blocks, tick thread drives navigation)

**What:** The Lua binding calls `PathmindRuntime.moveTo(x,y,z)` which returns a `CompletableFuture<Void>`. The worker calls `.get()` to block. PathmindNavigator completes the future from the tick loop when navigation ends.

**When to use:** `pathmind.moveTo(x, y, z)` in Lua script.

```java
// Source: PathmindNavigator.java (verified in codebase)
// PathmindNavigator.startGoto(BlockPos, String, CompletableFuture<Void>):
//   stores the CF as activeFuture; completes it via complete(State.ARRIVED) or
//   completeExceptionally(reason) from within the tick-driven navigation update.

// PathmindRuntime implementation (Pathmind-side, PathmindRuntimeImpl.java):
@Override
public CompletableFuture<Void> moveTo(double x, double y, double z) {
    if (!BaritoneDependencyChecker.isBaritoneApiPresent()
            && PathmindNavigator.getInstance().getState() == PathmindNavigator.State.IDLE) {
        // Fallback: use PathmindNavigator own pathfinding
    }
    CompletableFuture<Void> navFuture = new CompletableFuture<>();
    BlockPos target = BlockPos.ofFloored(x, y, z);
    boolean started = PathmindNavigator.getInstance().startGoto(target, "Lua moveTo", navFuture);
    if (!started) {
        navFuture.completeExceptionally(
            new RuntimeException("moveTo: navigation failed to start"));
    }
    return navFuture;
}

// Addon-side binding (pauses timeout clock while blocked):
static Varargs moveTo(LuaState state, Varargs args, PathmindRuntime runtime) throws LuaError {
    double x = args.arg(1).checkDouble();
    double y = args.arg(2).checkDouble();
    double z = args.arg(3).checkDouble();
    CompletableFuture<Void> navFuture = runtime.moveTo(x, y, z);
    // Pause compute timer here
    try {
        navFuture.get();  // blocks worker thread; tick thread keeps running
    } catch (ExecutionException e) {
        throw new LuaError("moveTo failed: " + e.getCause().getMessage());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new LuaError("moveTo interrupted");
    }
    // Resume compute timer here
    return Constants.NONE;
}
```
[CITED: pathmind/common/src/main/java/com/pathmind/execution/PathmindNavigator.java — verified startGoto(BlockPos, String, CompletableFuture<Void>)]

### Pattern 5: Main-Thread Dispatch for Game-State Reads (BIND-03)

**What:** Game-state reads (position, inventory, block) must run on the client thread. The worker thread submits a task via `MinecraftClient.execute(Runnable)` and waits for the result via a `CompletableFuture`.

**When to use:** All three game-state accessors in `PathmindRuntimeImpl`. Do NOT access `MinecraftClient.getInstance()` directly from the worker thread.

```java
// Source: Node.java line 3819 — the established Pathmind idiom
// Source: BackgroundStartRunner.java — shows the CF-handback pattern

// PathmindRuntimeImpl:
@Override
public double[] getPosition() {
    CompletableFuture<double[]> result = new CompletableFuture<>();
    MinecraftClient.getInstance().execute(() -> {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            result.complete(null);
            return;
        }
        Vec3d pos = client.player.getPos();
        result.complete(new double[]{ pos.x, pos.y, pos.z });
    });
    try {
        double[] pos = result.get(5, TimeUnit.SECONDS);
        return pos != null ? pos : new double[]{0, 0, 0};
    } catch (Exception e) {
        return new double[]{0, 0, 0};
    }
}
```
[CITED: pathmind/common/src/main/java/com/pathmind/nodes/Node.java line 3819]

### Anti-Patterns to Avoid

- **Sharing a LuaState across executions:** Cobalt's global table is mutable; sharing state between concurrent script nodes would cause cross-contamination and race conditions. Always create a fresh `LuaState` per `execute()` call.
- **Calling `SystemLibraries.standardGlobals()` or `debugGlobals()`:** These load `IoLib` (file I/O), `OsLib` (process execution), and `PackageLib` (arbitrary module loading) — all dangerous in a Minecraft client context. Use `CoreLibraries.standardGlobals(state)` and add only the `pathmind` table.
- **Reading `MinecraftClient` from the Lua worker thread:** MC state is not thread-safe for reading outside the render thread. Always bounce through `MinecraftClient.execute(Runnable)` with a `CompletableFuture` result.
- **Completing the returned `CompletableFuture<NodeResult>` on the Lua worker thread before sending the chat error message:** Chat dispatch requires the client thread — fire the chat dispatch as a separate `client.execute(...)` call; complete the CF only after that is scheduled (not after it runs).
- **Using ForkJoinPool for Lua workers:** Lua workers block on navigation futures (potentially seconds). Blocking FJP threads starves the common pool used by ExecutionManager's `thenCompose` chains.
- **`Thread.interrupt()` as primary timeout:** Cobalt's `LuaState.interrupt()` + `InterruptHandler` is the correct primary mechanism. `Thread.interrupt()` is only the safety net for when Cobalt does not surface the error quickly enough.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Lua VM | Custom Lua interpreter | `org.squiddev:Cobalt:0.7.3` | Lua parsing, coroutine semantics, and all standard library edge cases are a multi-year implementation effort |
| String-to-Lua-value marshaling | Parse strings manually | `ValueFactory.valueOf(String/int/double/boolean)` | Cobalt already handles NaN, -0, integer vs double distinction per Lua 5.1 spec |
| Lua standard library (math, string, table) | Reimplement Lua builtins | `CoreLibraries.standardGlobals(state)` | String pattern matching alone has dozens of edge cases |
| Shadow relocation | Class rename scripts | `com.gradleup.shadow` `relocate()` | Shadow rewrites all class references including constant pool entries; manual rename misses reflection calls |
| Main-thread dispatch | Polling flags on the tick thread | `MinecraftClient.execute(Runnable)` + `CompletableFuture` | The established Pathmind idiom (Node.java line 3819); polling would busy-wait |
| Navigation completion signal | Polling PathmindNavigator state | `PathmindNavigator.startGoto(target, label, CompletableFuture)` | PathmindNavigator already completes/exceptionally-completes the CF on arrival/failure |

**Key insight:** Cobalt wraps 10+ years of Lua spec compliance. Every standard library function has edge cases (string.format, pattern matching, number parsing) that are entirely wrong to rebuild. Keep the VM as a black box; control its environment only through globals.

---

## Critical Research Findings (blockers resolved)

### Finding 1: Cobalt 0.7.3 API — verified class/method names

**Interrupt mechanism** [CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/LuaState.java]:
- `LuaState.interrupt()` — signals interruption from any thread (safe to call from timer thread)
- `LuaState.Builder.interruptHandler(InterruptHandler h)` — register the handler at state build time
- `InterruptHandler` (interface) has a single method: `InterruptAction interrupted() throws LuaError`
- `InterruptAction` enum: `CONTINUE` (keep running) or `SUSPEND` (pause execution — triggers `LuaError` path)
- Throwing `LuaError` directly from `interrupted()` is the recommended abort pattern

**Script loading** [CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/compiler/LoadState.java]:
- `LoadState.load(LuaState state, InputStream stream, String name, LuaTable env)` — compiles Lua source to `LuaClosure`
- Chunk name convention: prefix with `@` to indicate file source (e.g. `"@script"`) — used in error messages

**Execution** [CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/LuaThread.java]:
- `LuaThread.runMain(LuaState state, LuaFunction function)` — runs a function on the main thread context; blocks until done; throws `LuaError` on script errors
- `LuaThread.runMain(LuaState state, LuaFunction function, Varargs args)` — overload with args

**Value marshaling** [CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/ValueFactory.java]:
- `ValueFactory.valueOf(String)` → `LuaString`
- `ValueFactory.valueOf(int)` → `LuaInteger`
- `ValueFactory.valueOf(double)` → `LuaDouble` or `LuaInteger` (Cobalt normalizes)
- `ValueFactory.valueOf(boolean)` → `Constants.TRUE` / `Constants.FALSE`
- `ValueFactory.tableOf()` → empty `LuaTable`
- `ValueFactory.tableOf(LuaValue... namedValues)` → `{key, value, key, value, ...}` pairs

**Function creation** [CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/function/RegisteredFunction.java]:
- `RegisteredFunction.ofV(name, fn)` — variable-args function; signature `Varargs fn(LuaState, Varargs) throws LuaError`
- `RegisteredFunction.bind(RegisteredFunction[])` → new `LuaTable` populated with functions
- `RegisteredFunction.bind(LuaTable table, RegisteredFunction[])` → add to existing table

**Globals setup** [CITED: https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/lib/CoreLibraries.java]:
- `CoreLibraries.standardGlobals(LuaState state)` → `LuaTable` with: `BaseLib`, `TableLib`, `StringLib`, `CoroutineLib`, `MathLib`, `Utf8Lib`
- Returns the globals table — set it as the environment for `LoadState.load`
- **EXCLUDE**: `SystemBaseLib`, `PackageLib`, `IoLib`, `OsLib` (these are in `SystemLibraries`, never loaded)

**Error information** [ASSUMED — from LuaError API structure]:
- `LuaError.getMessage()` typically returns `"chunkname:line: message"` when Cobalt has decorated the error
- Cobalt does not expose a separate `getLine()` accessor — parse the message string to extract line number
- `LuaError.getCause()` may be set for Java exceptions thrown from Java-backed functions

### Finding 2: PathmindNavigator awaitable moveTo — verified API

[CITED: pathmind/common/src/main/java/com/pathmind/execution/PathmindNavigator.java — verified in codebase]

- `PathmindNavigator.getInstance()` — singleton
- `PathmindNavigator.startGoto(BlockPos targetPos, String commandLabel, CompletableFuture<Void> future)` — returns `boolean` (false if args null); stores `future` as `activeFuture`; sets state to `PATHING`
- `PathmindNavigator.startGotoNearBlock(BlockPos targetBlockPos, String commandLabel, CompletableFuture<Void> future)` — navigates to a standable block adjacent to the target
- **Completion:** `PathmindNavigator.complete(State.ARRIVED)` is called by the tick-driven navigation update; it calls `activeFuture.complete(null)` — the worker's `.get()` unblocks
- **Failure:** An internal `fail(message)` path calls `activeFuture.completeExceptionally(new RuntimeException(message))` — the worker's `.get()` throws `ExecutionException`
- **Baritone absent:** `BaritoneDependencyChecker.isBaritoneApiPresent()` gates Baritone; PathmindNavigator has its own A* pathfinding fallback (used when Baritone unavailable). When the PathmindRuntime's `moveTo` is called with Baritone absent, PathmindNavigator still works (its own pathfinding runs)
- **Already navigating:** `startGoto` calls `stopInternal(false, "replaced")` first — a new call cancels the previous navigation. For the v1 scripting use case (one moveTo at a time in the script), this is correct.

### Finding 3: ExecutionManager variable store — verified structure

[CITED: pathmind/common/src/main/java/com/pathmind/execution/ExecutionManager.java — verified in codebase]

- `globalRuntimeVariables` field type: `ConcurrentHashMap<String, RuntimeVariable>` (NOT `Collections.synchronizedMap(LinkedHashMap)` — STATE.md note about LinkedHashMap applies to `AddonLoader.failedAddons`, not this field)
- `RuntimeVariable` is a public static final inner class with fields:
  - `NodeType type` — the variable's type (e.g. `NodeType.PARAM_AMOUNT` for numbers, `NodeType.PARAM_BOOLEAN` for booleans)
  - `Map<String, String> values` — a map of well-known string keys to string-encoded values
- Numeric values are stored as `NodeType.PARAM_AMOUNT` with keys `"Amount"`, `"Count"`, `"Threshold"`, `"Value"` all set to the same string (e.g. `"42.0"`)
- Boolean values are stored as `NodeType.PARAM_BOOLEAN` with key `"Toggle"` = `"true"` or `"false"`
- String values are stored as `NodeType.PARAM_MESSAGE` with key `"Text"` (verified — see Open Questions Q1 RESOLVED: `ExecutionManager.buildPresetInputValueMap` lines ~960-999 calls `putRuntimeValue(values, "Text", safeValue)` for message/string parameters)
- **Public accessors:**
  - `getRuntimeVariableFromAnyActiveChain(String name)` — reads from current chain scope, then falls back to `globalRuntimeVariables`
  - `setRuntimeVariableForAnyActiveChain(String name, RuntimeVariable value)` — writes to current chain scope AND mirrors to `globalRuntimeVariables`
- **The `PathmindRuntime` implementation** must call these two methods. It does NOT directly touch `globalRuntimeVariables`.
- **Marshaling strategy for `getVariable`:** Read the `RuntimeVariable.type` to determine what to return to Lua. For `PARAM_AMOUNT`: parse `values.get("Amount")` as Double. For `PARAM_BOOLEAN`: parse `values.get("Toggle")` as Boolean. For `PARAM_MESSAGE`: return `values.get("Text")` as String. For unknown type: return nil.
- **Marshaling strategy for `setVariable`:** Create a `RuntimeVariable` with the appropriate `NodeType` based on the Lua value's Java type (Double → `PARAM_AMOUNT` with key `"Amount"`; Boolean → `PARAM_BOOLEAN` with keys `"Mode"="literal"`/`"Toggle"`/`"Variable"=""`; String → `PARAM_MESSAGE` with key `"Text"`).

### Finding 4: Main-thread dispatch idiom — verified

[CITED: pathmind/common/src/main/java/com/pathmind/nodes/Node.java lines 3818-3828]

The established Pathmind pattern is:
```java
client.execute(() -> {
    // run on client thread
    someWork();
    future.complete(result);
});
// caller blocks on future.get() from a worker thread
```
This is exactly what `PathmindRuntimeImpl.getPosition()`, `getInventory()`, `getBlock()` should use. `BackgroundStartRunner` is for launching entire graph executions — not for single-shot MC reads.

**Deadlock risk:** `client.execute(r)` queues `r` for the next tick. The worker thread calling `future.get()` will unblock when the next tick processes the queue. This is safe: the tick thread is not blocked on the worker (it polls the `CompletableFuture<NodeResult>` returned by `execute()` per tick, which is separate from the inner game-state futures). Maximum wait is one tick (~50 ms at 20 TPS) — well within the worker's blocking budget.

### Finding 5: Error surfacing to chat — verified idiom

[CITED: pathmind/common/src/main/java/com/pathmind/nodes/Node.java line 554]

The `sendNodeInfoMessage` method (which sends to player chat, not overlay) uses:
```java
client.player.sendMessage(Text.literal(CHAT_MESSAGE_PREFIX + message), false);
```
where `CHAT_MESSAGE_PREFIX = "[Pathmind] "`. The chat error from a Lua script should follow the same pattern. This must be dispatched to the client thread via `client.execute(...)`.

For the overlay (secondary channel, not the primary v1 channel), `NodeErrorNotificationOverlay.getInstance().show(message, color)` is already thread-safe (called from PathmindNavigator's non-client thread).

---

## Common Pitfalls

### Pitfall 1: LuaError message format varies by Cobalt version
**What goes wrong:** Code assumes `e.getMessage()` returns `"chunk:line: message"` but Cobalt may return just the message string if the chunk name or line is unavailable.
**Why it happens:** Cobalt only decorates the error with location info if the chunk was loaded with a proper name and the error originated in Lua source (not a Java-thrown LuaError without decoration).
**How to avoid:** Parse defensively — try to split on `:` to extract line number; if parsing fails, surface the whole message string. Prefix chunk name with `@` in `LoadState.load` to ensure file-style error messages.
**Warning signs:** Error message in chat shows no line number even though the script has an obvious runtime error on line N.

### Pitfall 2: Shadow relocation breaks Cobalt's internal class loading
**What goes wrong:** After relocation, Cobalt's `LuaC` compiler or `LoadState` may fail to find internal classes via `Class.forName`.
**Why it happens:** Cobalt uses reflection or `ServiceLoader` internally for some paths. Shadow only rewrites bytecode constant pool entries — dynamic `Class.forName("org.squiddev.cobalt.XYZ")` strings are NOT rewritten.
**How to avoid:** Inspect Cobalt 0.7.3 for `Class.forName` calls before finalizing relocation. If found, configure shadow with explicit `include` or use `relocate` with exclusion patterns.
**Warning signs:** `ClassNotFoundException: org.squiddev.cobalt.XYZ` at runtime even though the relocated jar contains the class at the new package.

### Pitfall 3: Worker thread blocked on game-state CF when game tick thread is stopped
**What goes wrong:** If the Minecraft game pauses (singleplayer pause, screen opened) the tick thread stops processing `client.execute()` queues. The worker's `result.get(timeout)` times out.
**Why it happens:** `MinecraftClient.execute(Runnable)` posts to the pending-tasks queue; the queue is drained by the game loop which stops when paused.
**How to avoid:** Use `result.get(TIMEOUT, TimeUnit.SECONDS)` with a reasonable timeout (e.g. 30s); on timeout, surface a Lua error to the script. Alternatively check `ExecutionManager.singleplayerPaused` before submitting and spin-wait briefly.
**Warning signs:** Script hangs indefinitely on `getPosition()` when the game is paused.

### Pitfall 4: RuntimeVariable marshaling — string type (RESOLVED)
**What goes wrong:** `setVar("name", "hello")` from Lua creates a `RuntimeVariable` with an unknown `NodeType`; other Pathmind nodes (e.g. OPERATOR_EQUALS) read the variable and fail type-check.
**Why it happens:** `RuntimeVariable.type` encodes Pathmind's visual type system (`NodeType.PARAM_AMOUNT`, `NodeType.PARAM_BOOLEAN`, etc.).
**Resolution:** String variables use `NodeType.PARAM_MESSAGE` with key `"Text"` — verified in `ExecutionManager.buildPresetInputValueMap` (lines ~960-999) and recorded in `02-PATTERNS.md`. The marshaling convention is now PARAM_AMOUNT→"Amount", PARAM_BOOLEAN→Mode/Toggle/Variable, PARAM_MESSAGE→"Text".
**Warning signs:** `getVar("name")` returns nil or wrong type after `setVar` with a string value — would indicate the "Text" key assumption is wrong at impl time; the round-trip unit test in 02-02 catches this empirically.

### Pitfall 5: Timeout clock not actually pausing during action
**What goes wrong:** The "compute-only" timeout requirement means the clock must stop while the worker is blocked on `moveTo` / game-state futures. A naive `ScheduledFuture` that fires after 5s total wall-clock time will kill scripts that navigate for more than 5s.
**Why it happens:** Wall-clock scheduling doesn't know the Lua worker is blocked vs computing.
**How to avoid:** The interrupt handler (`InterruptHandler.interrupted()`) is called by Cobalt during computation (long operations, debug hooks). Track cumulative compute time: record `System.nanoTime()` when the worker enters Lua (resumes from an action), record the elapsed when it pauses (calls an action). The interrupt handler checks total accumulated compute time, not wall-clock time. The `LuaState.interrupt()` mechanism only fires during Lua execution — it naturally does not fire while the worker is blocked in Java.
**Warning signs:** Short navigating scripts are killed mid-path; time-measuring test shows wall-clock time instead of compute time.

### Pitfall 6: `startGoto` replaces any existing navigation
**What goes wrong:** If two Lua nodes run concurrently (parallel branches), each calling `moveTo`, the second `startGoto` cancels the first navigation silently.
**Why it happens:** `startGotoInternal` always calls `stopInternal(false, "replaced")` first.
**How to avoid:** In v1 this is acceptable — document that concurrent Script nodes sharing the navigation resource have undefined navigation results. Do not attempt to queue navigations in Phase 2.
**Warning signs:** `moveTo` completes immediately in a concurrent execution scenario.

---

## Code Examples

### Minimal Cobalt "Hello World" executor skeleton
```java
// Source: Derived from cc-tweaked/Cobalt ScriptHelper + RegisteredFunction patterns
// [CITED] and [ASSUMED] mixed — verify exact imports at implementation time

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.RegisteredFunction;
import org.squiddev.cobalt.lib.CoreLibraries;
import org.squiddev.cobalt.interrupt.InterruptAction;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public CompletableFuture<NodeResult> execute(AddonNodeContext ctx) {
    CompletableFuture<NodeResult> resultFuture = new CompletableFuture<>();
    String script = ctx.getScriptText();
    if (script == null || script.isBlank()) {
        return CompletableFuture.completedFuture(NodeResult.SUCCESS);
    }

    PathmindRuntime runtime = ctx.getRuntime();
    AtomicBoolean timedOut = new AtomicBoolean(false);

    Thread worker = new Thread(() -> {
        LuaState state = LuaState.builder()
            .interruptHandler(() -> {
                if (timedOut.get()) throw new LuaError("Script timed out");
                return InterruptAction.CONTINUE;
            })
            .build();
        try {
            LuaTable globals = CoreLibraries.standardGlobals(state);
            globals.rawset("pathmind", PathmindBindings.build(state, runtime, timedOut));
            LuaClosure closure = LoadState.load(
                state,
                new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                "@script",
                globals
            );
            LuaThread.runMain(state, closure);
            resultFuture.complete(NodeResult.SUCCESS);
        } catch (LuaError e) {
            // surface to chat, log traceback (Pattern 5 dispatch)
            dispatchErrorToChat(runtime, e.getMessage());
            System.err.println("[Pathmind Lua] Script error:\n" + e.getMessage());
            resultFuture.complete(NodeResult.FAILURE);
        } catch (Exception e) {
            resultFuture.complete(NodeResult.FAILURE);
        }
    });
    worker.setDaemon(true);
    worker.setName("pathmind-lua-worker");
    worker.start();

    // Timeout safety net
    long budgetMs = 5000L; // from SettingsManager
    scheduleTimeout(state, timedOut, worker, budgetMs);
    return resultFuture;
}
```

### Lua script that exercises all bindings
```lua
-- Test script for all Phase 2 bindings
local x = pathmind.getVar("target_x")
pathmind.setVar("status", "moving")
pathmind.moveTo(x or 100, 64, 200)
pathmind.setVar("status", "arrived")
local pos = pathmind.getPosition()
pathmind.setVar("current_x", pos.x)
local inv = pathmind.getInventory()
for _, slot in ipairs(inv) do
    -- slot.slot, slot.item, slot.count
end
local block = pathmind.getBlock(pos.x, pos.y - 1, pos.z)
pathmind.setVar("ground_block", block or "unknown")
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| LuaJ 2.x (no interrupt) | Cobalt 0.7.3 (interruptible, re-entrant) | CC:Tweaked fork circa 2017+ | Can interrupt runaway scripts from outside the VM thread |
| `standardGlobals()` (includes IoLib, OsLib) | `CoreLibraries.standardGlobals()` (safe subset) | Cobalt split since ~0.6.x | Eliminates file I/O and process execution from script environment |
| Global `LuaState` (shared across calls) | Per-execution fresh `LuaState` | Always the right pattern for re-entrant VMs | Eliminates cross-execution state leaks |

**Deprecated/outdated:**
- `luajava`: Cobalt does NOT include luajava bindings. Do not attempt to use it — the decision to exclude it is explicit (LUA-03).
- `LuaState.standardGlobals()` (if it exists from older LuaJ era): Not present in Cobalt 0.7.3 — use `CoreLibraries.standardGlobals(state)`.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Cobalt compilation, addon build | ✓ | OpenJDK 21.0.11 | — |
| Gradle 9.x | Addon build | ✓ | 9.2.0 | — |
| Cobalt 0.7.3 JAR | VM execution | Must fetch from squiddev.cc/maven | Not yet in addon build | — |
| Baritone API | `moveTo` Baritone path | Runtime-optional | Unknown (not in dev environment) | PathmindNavigator own pathfinding (already implemented) |
| `MinecraftClient` (Minecraft running) | Game-state reads | ✓ (in-game) | Minecraft 1.21.4 | Null-guard + return empty/zero |

**Missing dependencies with no fallback:**
- Cobalt 0.7.3 — must add SquidDev repository and `implementation` dependency to `pathmind-lua/build.gradle.kts` before any VM code compiles.

**Missing dependencies with fallback:**
- Baritone: PathmindNavigator's own A* pathfinding is the fallback and is already implemented. `moveTo` must not throw when Baritone is absent.

---

## Security Domain

### Applicable ASVS Categories (ASVS Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Script execution requires no auth; user already in-game |
| V3 Session Management | No | No web sessions involved |
| V4 Access Control | Yes | Scripts run as the current player only; no server-side code modification; client-side only mod (enforced by existing arch) |
| V5 Input Validation | Yes | Lua script text is user-supplied; Cobalt compiler will reject malformed syntax; runtime sandbox prevents file I/O and process execution |
| V6 Cryptography | No | No cryptographic operations |

### Known Threat Patterns for This Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Infinite loop / CPU exhaustion | Denial of Service | `LuaState.interrupt()` + `InterruptHandler` timeout; `Thread.interrupt()` safety net |
| File system access from Lua script | Elevation of Privilege | Exclude `IoLib` and `PackageLib` from globals |
| Process execution from Lua script | Elevation of Privilege | Exclude `OsLib` from globals |
| Arbitrary class loading via `require` | Elevation of Privilege | Exclude `PackageLib`; no `require` function in sandbox |
| Stack overflow via deep recursion | Denial of Service | Cobalt has internal stack depth limits; `LuaError` is thrown when exceeded |
| Memory exhaustion | Denial of Service | Deferred to LUA-V2-01 (instruction budget); v1 uses wall-clock timeout only — acceptable for trusted-user client-side mod |

**Note:** This is a client-side Minecraft mod. The threat model is a user running their own scripts against their own game client. There is no remote code execution vector — scripts are authored and run by the same user. ASVS Level 1 controls (input validation, no dangerous library access) are appropriate.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `LuaError.getMessage()` returns a string in the format `"chunkname:line: message"` | Finding 1, Pitfall 1 | Chat message shows wrong format or no line number; low risk — parse defensively. RESOLVED-BY-PLAN: 02-01 smoke test asserts the format empirically (Open Questions Q2). |
| A2 | String variables use `NodeType.PARAM_MESSAGE` with key `"Text"` | Finding 3 | RESOLVED — verified via codebase read of `ExecutionManager.buildPresetInputValueMap` (lines ~960-999), recorded in 02-PATTERNS.md. If the key is wrong at impl time, marshaling silently breaks — the 02-02 round-trip unit test catches it (Open Questions Q1). |
| A3 | `LuaState.interrupt()` is safe to call from any thread (e.g. a ScheduledExecutorService thread) | Pattern 3 | If not thread-safe, the timeout mechanism is broken; mitigated by Cobalt's stated design ("interruptible at arbitrary points") |
| A4 | `CoreLibraries.standardGlobals(state)` returns a fully self-contained table with no reference to the state's main thread identity | Standard Stack | If it captures thread identity, concurrent executions on different states might interfere |
| A5 | The LuaClosure returned by `LoadState.load(...)` is associated with the `globals` table passed and does not share state with other closures | Pattern 1 | Fresh-globals-per-execution guarantee fails; concurrent script nodes pollute each other |

**If this table is empty:** All claims in this research were verified or cited — no user confirmation needed. (Not the case — A1/A3/A4/A5 carry residual risk verified by plan smoke tests.)

---

## Open Questions (RESOLVED)

1. **String variable NodeType for `setVar`** — RESOLVED
   - Resolution: String variables are stored as `NodeType.PARAM_MESSAGE` with key `"Text"`. Verified via codebase read of `ExecutionManager.buildPresetInputValueMap` (lines ~960-999) — it calls `putRuntimeValue(values, "Text", safeValue)` for message/string parameters. Recorded in `02-PATTERNS.md` (PathmindRuntimeImpl variable read/write patterns + RuntimeVariable Construction shared pattern). This is a verified codebase read, not an assumption — but confidence on long-term stability is LOW: if the key is wrong at implementation time, marshaling silently breaks. The 02-02 round-trip + cross-store-read unit test exercises it empirically (set via PathmindRuntimeImpl, read back via `ExecutionManager.getRuntimeVariableFromAnyActiveChain`) so a wrong key surfaces as a failing test rather than a silent production bug.

2. **`LuaError` line number extraction** — RESOLVED-BY-PLAN
   - Resolution: 02-01 includes a smoke/unit test (`CobaltVmSmokeTest`) that intentionally throws a Lua error and asserts the `chunk:line: message` format as an early acceptance criterion. The executor confirms the format empirically (the test documents the real format and adjusts the message-parsing to surface message+line defensively) rather than assuming it. The chunk is loaded with the `@script` name so Cobalt decorates errors file-style. This gates the BIND-04 implementation: the chat-error case in the smoke test asserts the captured `sendErrorToChat` message contains a line number.

3. **Shadow relocation and Cobalt internal class loading** — RESOLVED-BY-PLAN
   - Resolution: 02-01 Task 1 includes a grep/source check of the resolved Cobalt jar (cached under the Gradle caches dir) for reflective `Class.forName(` usage referencing `org.squiddev.cobalt.*` string literals before finalizing the shadow relocation rules. If any are found, the task adds a `mergeServiceFiles()` call and a build-file comment documenting the literals so the relocated service-loader/reflection paths are handled rather than silently broken. The shadowJar verification (jar inspection confirming `com/mrmysterium/pathmindlua/shadow/cobalt/LuaState.class` exists and no `org/squiddev/cobalt/` paths remain) is the empirical gate.

---

## Sibling Repo State

[CITED: pathmind-lua repo — verified in codebase]

**Current state of `C:\Users\Trynda\Desktop\Dev\sidequests\pathmind-lua`:**

| File | Status | Phase 2 action |
|------|--------|---------------|
| `LuaNodeExecutor.java` | No-op pass-through (returns `SUCCESS` immediately) | Replace with real Cobalt VM lifecycle |
| `LuaAddonEntrypoint.java` | Registers `pathmind_lua:script` with `LuaNodeExecutor` | No change needed |
| `LuaNodeSerializer.java` | Persists/restores `script` text, schema version 1 | No change needed |
| `LuaScriptNodeRenderer.java` | Renders script preview in node body | No change needed |
| `build.gradle.kts` | Has Architectury Loom, compiles against `pathmind-fabric` | Add Cobalt dependency + shadowJar task |
| `fabric.mod.json` | Declares `pathmind` entrypoint, `>=0.1.0` dep | No change needed |
| Java source dir (`vm/`, `bindings/`) | Not yet created | Phase 2 creates these |

**No Cobalt dependency in the addon yet.** The Cobalt dependency and shadow task are both missing from `build.gradle.kts` — this is the first task of Plan 1.

---

## MVP Vertical Slices (guidance for planner)

The planner should organize tasks as independent vertical slices, each leaving both repos loadable:

| Slice | Pathmind change | Addon change | Done when |
|-------|----------------|-------------|-----------|
| **S0: Build wiring** | Publish API artifact to mavenLocal | Add Cobalt dependency, shadow task, verify it compiles | `./gradlew shadowJar` succeeds in addon |
| **S1: Bare VM executes** | Add `getRuntime()` to `AddonNodeContext`; add `PathmindRuntime` interface stub (no impl yet); add `PathmindRuntimeImpl` stub | Replace no-op executor with Cobalt VM lifecycle; `pathmind` table has no-op stubs; script `return 1 + 1` executes and node completes SUCCESS | Script node executes real Lua; errors surface to chat |
| **S2: getVar/setVar** | Implement `getVariable`/`setVariable` in `PathmindRuntimeImpl` (wrap `getRuntimeVariableFromAnyActiveChain` / `setRuntimeVariableForAnyActiveChain`) | Wire `pathmind.getVar`/`pathmind.setVar` to `PathmindRuntime` | Script can exchange scalars with other Pathmind nodes |
| **S3: moveTo awaitable** | Implement `moveTo(x,y,z)` in `PathmindRuntimeImpl` (wrap `PathmindNavigator.startGoto`) | Wire `pathmind.moveTo`; worker blocks on nav CF; timeout clock pauses | Script navigates and resumes after arrival; Baritone absent path tested |
| **S4: Game-state reads** | Implement `getPosition`, `getInventory`, `getBlock` in `PathmindRuntimeImpl` with `client.execute` dispatch | Wire all three bindings | All `pathmind.*` functions work in a complete test script |

---

## Sources

### Primary (verified via codebase read)
- `pathmind/common/src/main/java/com/pathmind/execution/ExecutionManager.java` — RuntimeVariable structure, variable access methods, worker thread model (ForkJoinPool for chain execution)
- `pathmind/common/src/main/java/com/pathmind/execution/PathmindNavigator.java` — `startGoto(BlockPos, String, CompletableFuture<Void>)`, completion via `complete(State.ARRIVED)`, failure via `completeExceptionally`
- `pathmind/common/src/main/java/com/pathmind/nodes/Node.java` — `executeAddonNode`, `AddonNodeContext` wiring, `client.execute` idiom
- `pathmind/common/src/main/java/com/pathmind/api/addon/` — all Phase 1 API types
- `pathmind-lua/` — LuaNodeExecutor stub, build.gradle.kts, fabric.mod.json

### Secondary (cited from official Cobalt GitHub source)
- [cc-tweaked/Cobalt v0.7.3 LuaState.java](https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/LuaState.java) — Builder, `interrupt()`, `interruptHandler()`
- [cc-tweaked/Cobalt v0.7.3 LuaThread.java](https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/LuaThread.java) — `runMain(state, fn)`, `runMain(state, fn, args)`
- [cc-tweaked/Cobalt v0.7.3 LoadState.java](https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/compiler/LoadState.java) — `load(LuaState, InputStream, String, LuaTable)`
- [cc-tweaked/Cobalt v0.7.3 CoreLibraries.java](https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/lib/CoreLibraries.java) — `standardGlobals(state)`, libraries included
- [cc-tweaked/Cobalt v0.7.3 SystemLibraries.java](https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/lib/system/SystemLibraries.java) — what gets loaded when NOT using `CoreLibraries` (excluded set)
- [cc-tweaked/Cobalt v0.7.3 RegisteredFunction.java](https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/function/RegisteredFunction.java) — `ofV`, `bind`
- [cc-tweaked/Cobalt v0.7.3 ValueFactory.java](https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/ValueFactory.java) — `valueOf(String/int/double/boolean)`, `tableOf`
- [cc-tweaked/Cobalt v0.7.3 InterruptHandler.java](https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/main/java/org/squiddev/cobalt/interrupt/InterruptHandler.java) — interface, `InterruptAction` enum
- [cc-tweaked/Cobalt v0.7.3 ScriptHelper.java](https://github.com/cc-tweaked/Cobalt/blob/v0.7.3/src/test/java/org/squiddev/cobalt/ScriptHelper.java) — canonical usage pattern
- [squiddev.cc Maven — Cobalt 0.7.3 artifact listing](https://squiddev.cc/maven/org/squiddev/Cobalt/0.7.3/) — confirmed artifact exists, published 2023-08-27

### Tertiary (LOW confidence — from web search)
- mvnrepository.com search results for `org.squiddev:Cobalt` — artifact name/group confirmed; coordinates sourced from squiddev.cc/maven

---

## Metadata

**Confidence breakdown:**
- Cobalt 0.7.3 class/method names: MEDIUM — sourced from GitHub source file contents via WebFetch; not locally compiled/tested this session
- Pathmind codebase internals (ExecutionManager, PathmindNavigator): HIGH — read directly from source files in this repo
- Addon repo state: HIGH — read directly from source files in pathmind-lua repo
- Shadow relocation pattern: MEDIUM — standard Gradle Shadow plugin behavior, well-documented
- String variable NodeType: RESOLVED (was LOW) — verified via codebase read of `ExecutionManager.buildPresetInputValueMap`; recorded in 02-PATTERNS.md; round-trip unit test in 02-02 gates it empirically

**Research date:** 2026-06-13
**Valid until:** 2026-07-13 (Cobalt is stable; Pathmind internals change with every plan)
