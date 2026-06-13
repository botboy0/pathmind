# Phase 2: Lua VM + Core Bindings - Pattern Map

**Mapped:** 2026-06-13
**Files analyzed:** 8 (2 Pathmind, 6 addon)
**Analogs found:** 7 / 8 (1 has no close analog — `PathmindRuntime` interface is a new API surface)

---

## File Classification

| New/Modified File | Repo | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|------|-----------|----------------|---------------|
| `common/.../api/addon/PathmindRuntime.java` | Pathmind | API interface | request-response | `AddonNodeExecutor.java` (same package, same style) | role-match |
| `common/.../api/addon/AddonNodeContext.java` | Pathmind | API data holder | request-response | itself (add `getRuntime()`) | modify |
| `common/.../execution/PathmindRuntimeImpl.java` | Pathmind | service | CRUD + request-response | `ExecutionManager.java` (variable access idiom) | role-match |
| `pathmind-lua/.../LuaNodeExecutor.java` | Addon | executor (replace no-op) | event-driven | itself (Phase 1 stub, replace body) | modify |
| `pathmind-lua/.../vm/CobaltVm.java` | Addon | utility | event-driven | `BackgroundStartRunner.java` (worker-thread launch idiom) | partial-match |
| `pathmind-lua/.../vm/LuaGlobals.java` | Addon | utility/config | transform | no direct analog — Cobalt-specific | none |
| `pathmind-lua/.../vm/PathmindBindings.java` | Addon | service | request-response | `PathmindRuntimeImpl.java` (mirrors its API surface) | partial-match |
| `pathmind-lua/build.gradle.kts` | Addon | config | — | itself (add Cobalt dep + shadowJar block) | modify |

---

## Pattern Assignments

### `common/src/main/java/com/pathmind/api/addon/PathmindRuntime.java` (new, API interface)

**Analog:** `common/src/main/java/com/pathmind/api/addon/AddonNodeExecutor.java`

**Imports pattern** (copy exact package/import style from AddonNodeExecutor.java lines 1-4):
```java
package com.pathmind.api.addon;

import java.util.concurrent.CompletableFuture;
```

**Interface declaration style** (lines 7-26 of AddonNodeExecutor.java — minimal, Javadoc on every method, no implementation leakage):
```java
/**
 * Runtime services provided to addon node executors by Pathmind.
 *
 * <p>Obtained via {@link AddonNodeContext#getRuntime()}. Implementations are
 * provided by Pathmind — addons must not implement this interface.
 *
 * <p>Part of the Pathmind addon API — Phase 2 runtime bridge.
 */
public interface PathmindRuntime {

    /**
     * Reads a node-tree variable by name. Returns null if the variable does not exist.
     * Supported return types: Double, Boolean, String.
     */
    Object getVariable(String name);

    /**
     * Writes a node-tree variable. Supported value types: Double, Boolean, String.
     * Throws {@link IllegalArgumentException} if the value type is unsupported.
     */
    void setVariable(String name, Object value);

    /**
     * Starts navigation to the given coordinates. Returns a future that completes
     * when the player arrives or fails exceptionally if navigation is aborted.
     * The caller (Lua worker thread) may block on the future safely.
     */
    CompletableFuture<Void> moveTo(double x, double y, double z);

    /** Returns player position as [x, y, z]. Dispatched to the client thread. */
    double[] getPosition();

    /**
     * Returns non-empty inventory slots as an array of {slot, item, count} records.
     * Dispatched to the client thread.
     */
    Object[] getInventory();

    /**
     * Returns the namespaced block id at the given world coordinates, or null if the
     * chunk is not loaded. Dispatched to the client thread.
     */
    String getBlock(double x, double y, double z);

    /**
     * Sends an error message to the player chat. Must be called from any thread —
     * dispatches to the client thread internally.
     */
    void sendErrorToChat(String message);
}
```

---

### `common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java` (modify — add `getRuntime()`)

**Analog:** itself (lines 1-63 in full). Read the file before editing.

**Change:** add one field and getter/setter following the exact pattern of the existing `scriptText` field (lines 19-62):
```java
// Add field (after existing scriptText field, line 19):
private PathmindRuntime runtime;

// Add getter (after existing getScriptText/setScriptText methods):
/**
 * Returns the runtime services for this execution context.
 * Set by Pathmind before invoking the executor.
 *
 * @return the runtime services, or null if not yet wired
 */
public PathmindRuntime getRuntime() {
    return runtime;
}

/**
 * Sets the runtime services for this execution context.
 *
 * @param runtime the runtime services
 */
public void setRuntime(PathmindRuntime runtime) {
    this.runtime = runtime;
}
```

**Wire point in Node.java:** `executeAddonNode()` at line 3846 — after `ctx.setScriptText(...)` on line 3851, add:
```java
// Wire runtime services (Phase 2 — PathmindRuntime bridge)
ctx.setRuntime(new PathmindRuntimeImpl(ExecutionManager.getInstance()));
```

---

### `common/src/main/java/com/pathmind/execution/PathmindRuntimeImpl.java` (new, service)

**Analog — variable access:** `ExecutionManager.java` lines 354-390 (verified real signatures).

**Analog — main-thread dispatch:** `Node.java` lines 3818-3827 (verified real `client.execute` idiom).

**Analog — chat error send:** `Node.java` lines 541-555 (verified `sendNodeInfoMessage` pattern).

**Imports pattern** (match execution package conventions):
```java
package com.pathmind.execution;

import com.pathmind.api.addon.PathmindRuntime;
import com.pathmind.nodes.NodeType;
import com.pathmind.util.BaritoneDependencyChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;
```

**Variable read pattern** — `getVariable(String name)` — backed by `ExecutionManager.getRuntimeVariableFromAnyActiveChain` (verified line 369). RuntimeVariable structure (verified lines 136-207):
- `PARAM_AMOUNT` → key `"Amount"` → parse as Double
- `PARAM_BOOLEAN` → key `"Toggle"` → parse as Boolean
- `PARAM_MESSAGE` → key `"Text"` → return as String (verified line 975: `putRuntimeValue(values, "Text", safeValue)`)

```java
@Override
public Object getVariable(String name) {
    ExecutionManager.RuntimeVariable rv =
        executionManager.getRuntimeVariableFromAnyActiveChain(name);
    if (rv == null) return null;
    Map<String, String> values = rv.getValues();
    return switch (rv.getType()) {
        case PARAM_AMOUNT -> {
            String raw = values.get("Amount");
            yield raw != null ? Double.parseDouble(raw) : null;
        }
        case PARAM_BOOLEAN -> {
            String raw = values.get("Toggle");
            yield raw != null ? Boolean.parseBoolean(raw) : null;
        }
        case PARAM_MESSAGE -> values.get("Text");
        default -> null;
    };
}
```

**Variable write pattern** — `setVariable(String name, Object value)` — backed by `ExecutionManager.setRuntimeVariableForAnyActiveChain` (verified line 354):
```java
@Override
public void setVariable(String name, Object value) {
    Map<String, String> vals = new HashMap<>();
    NodeType type;
    if (value instanceof Double d) {
        type = NodeType.PARAM_AMOUNT;
        vals.put("Amount", d.toString());
    } else if (value instanceof Boolean b) {
        type = NodeType.PARAM_BOOLEAN;
        vals.put("Mode", "literal");
        vals.put("Toggle", b.toString());
        vals.put("Variable", "");
    } else if (value instanceof String s) {
        type = NodeType.PARAM_MESSAGE;
        vals.put("Text", s);
    } else {
        throw new IllegalArgumentException(
            "Unsupported variable type: " + (value == null ? "null" : value.getClass().getSimpleName()));
    }
    executionManager.setRuntimeVariableForAnyActiveChain(
        name, new ExecutionManager.RuntimeVariable(type, vals));
}
```

**moveTo pattern** — wraps `PathmindNavigator.startGoto(BlockPos, String, CompletableFuture<Void>)` (verified line 361, signature: `public synchronized boolean startGoto(BlockPos targetPos, String commandLabel, CompletableFuture<Void> future)`):
```java
@Override
public CompletableFuture<Void> moveTo(double x, double y, double z) {
    CompletableFuture<Void> navFuture = new CompletableFuture<>();
    BlockPos target = BlockPos.ofFloored(x, y, z);
    boolean started = PathmindNavigator.getInstance().startGoto(target, "Lua moveTo", navFuture);
    if (!started) {
        navFuture.completeExceptionally(
            new RuntimeException("moveTo: PathmindNavigator.startGoto returned false"));
    }
    return navFuture;
}
```

**Main-thread dispatch pattern for game-state reads** — copy idiom from `Node.java` lines 3818-3827:
```java
@Override
public double[] getPosition() {
    CompletableFuture<double[]> result = new CompletableFuture<>();
    MinecraftClient.getInstance().execute(() -> {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            result.complete(null);
            return;
        }
        var pos = client.player.getPos();
        result.complete(new double[]{ pos.x, pos.y, pos.z });
    });
    try {
        double[] pos = result.get(30, TimeUnit.SECONDS);
        return pos != null ? pos : new double[]{0, 0, 0};
    } catch (Exception e) {
        return new double[]{0, 0, 0};
    }
}
```

**Chat error dispatch pattern** — copy idiom from `Node.java` lines 541-555. The prefix constant is `"§4[§cPathmind§4] §7"` (verified line 169):
```java
private static final String CHAT_MESSAGE_PREFIX = "§4[§cPathmind§4] §7";

@Override
public void sendErrorToChat(String message) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client == null) return;
    client.execute(() -> {
        if (client.player == null) return;
        client.player.sendMessage(
            Text.literal(CHAT_MESSAGE_PREFIX + message), false);
    });
}
```

---

### `pathmind-lua/.../LuaNodeExecutor.java` (modify — replace no-op body)

**Analog:** itself (lines 1-26). The class structure, package, and imports remain; only the `execute()` body changes.

**Existing class skeleton to keep** (lines 1-18):
```java
package com.mrmysterium.pathmindlua;

import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeExecutor;
import com.pathmind.api.addon.NodeResult;

import java.util.concurrent.CompletableFuture;
```

**New imports to add** (Cobalt + JDK concurrency):
```java
import com.mrmysterium.pathmindlua.vm.CobaltVm;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
```

**New execute() body** — returns CF immediately, spins worker thread via `CobaltVm`:
```java
private static final ExecutorService WORKER_POOL = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "pathmind-lua-worker");
    t.setDaemon(true);
    return t;
});

@Override
public CompletableFuture<NodeResult> execute(AddonNodeContext ctx) {
    CompletableFuture<NodeResult> resultFuture = new CompletableFuture<>();
    String script = ctx.getScriptText();
    if (script == null || script.isBlank()) {
        return CompletableFuture.completedFuture(NodeResult.SUCCESS);
    }
    WORKER_POOL.submit(() ->
        CobaltVm.run(script, ctx.getRuntime(), resultFuture));
    return resultFuture;
}
```

---

### `pathmind-lua/.../vm/CobaltVm.java` (new, utility)

**Analog:** no direct analog in either repo. Closest structural reference is RESEARCH.md Pattern 1 (Cobalt VM lifecycle). The `BackgroundStartRunner.java` pattern (worker thread launches off-tick) is the structural reference for the "submit work, complete future" shape.

**Role:** Encapsulates a single Cobalt `LuaState` lifecycle — create, load, run, interrupt, catch errors, complete the result future. One `static void run(...)` method, called from `LuaNodeExecutor`'s worker thread.

**Class skeleton** (all Cobalt imports relocated — prefix `com.mrmysterium.pathmindlua.shadow.cobalt`):
```java
package com.mrmysterium.pathmindlua.vm;

import com.mrmysterium.pathmindlua.shadow.cobalt.LuaState;
import com.mrmysterium.pathmindlua.shadow.cobalt.LuaError;
import com.mrmysterium.pathmindlua.shadow.cobalt.LuaThread;
import com.mrmysterium.pathmindlua.shadow.cobalt.LuaTable;
import com.mrmysterium.pathmindlua.shadow.cobalt.compiler.LoadState;
import com.mrmysterium.pathmindlua.shadow.cobalt.lib.CoreLibraries;
import com.mrmysterium.pathmindlua.shadow.cobalt.interrupt.InterruptAction;
import com.pathmind.api.addon.NodeResult;
import com.pathmind.api.addon.PathmindRuntime;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
```

**Core static run() method** (RESEARCH.md Pattern 1 + Pattern 3 — compute-only timeout):
```java
private static final long DEFAULT_BUDGET_MS = 5_000L;

public static void run(String script, PathmindRuntime runtime,
                       CompletableFuture<NodeResult> resultFuture) {
    AtomicBoolean timedOut = new AtomicBoolean(false);
    // computeMs tracks accumulated Lua compute time; paused during action futures
    AtomicLong computeMs = new AtomicLong(0);

    LuaState state = LuaState.builder()
        .interruptHandler(() -> {
            if (timedOut.get()) {
                throw new LuaError("Script timed out (exceeded " + DEFAULT_BUDGET_MS + "ms compute time)");
            }
            return InterruptAction.CONTINUE;
        })
        .build();

    try {
        LuaTable globals = CoreLibraries.standardGlobals(state);
        // DO NOT add SystemBaseLib, PackageLib, IoLib, OsLib
        globals.rawset("pathmind",
            PathmindBindings.build(state, runtime, timedOut, computeMs));

        LuaThread.runMain(state,
            LoadState.load(
                state,
                new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                "@script",   // prefix '@' → file-style error messages "script:N: msg"
                globals
            )
        );
        resultFuture.complete(NodeResult.SUCCESS);

    } catch (LuaError e) {
        String msg = e.getMessage();
        if (msg == null) msg = "(no message)";
        System.err.println("[Pathmind Lua] Script error:\n" + msg);
        // Dispatch chat on client thread — runtime handles the thread hop
        runtime.sendErrorToChat("Lua error: " + msg);
        resultFuture.complete(NodeResult.FAILURE);

    } catch (Exception e) {
        System.err.println("[Pathmind Lua] Unexpected error: " + e.getMessage());
        resultFuture.complete(NodeResult.FAILURE);
    }
}
```

---

### `pathmind-lua/.../vm/LuaGlobals.java` (no close analog — Cobalt-specific)

**No analog found.** See RESEARCH.md Pattern 2 for the Cobalt `RegisteredFunction.bind` API. This class is a pure utility builder — no pattern to copy from the existing codebase.

**Role:** Static helper `build(LuaState, PathmindRuntime, AtomicBoolean, AtomicLong)` → `LuaTable` containing only `CoreLibraries.standardGlobals` safe subset. Extracted from `CobaltVm` for testability. In practice the planner may choose to inline this into `CobaltVm` — keeping them separate aids unit-testing the globals wiring independently.

---

### `pathmind-lua/.../vm/PathmindBindings.java` (new, service)

**Analog:** `PathmindRuntimeImpl.java` (mirrors the same API surface from the Lua side). No exact match exists yet since `PathmindRuntimeImpl` is also new — both are created in this phase.

**Role:** Builds the `pathmind.*` Lua function table. Each function marshals Lua args, calls the corresponding `PathmindRuntime` method, and returns Lua values. The timeout clock pause/resume wraps any blocking call.

**Imports** (Cobalt value types — all relocated):
```java
package com.mrmysterium.pathmindlua.vm;

import static com.mrmysterium.pathmindlua.shadow.cobalt.ValueFactory.*;
import com.mrmysterium.pathmindlua.shadow.cobalt.*;
import com.mrmysterium.pathmindlua.shadow.cobalt.function.RegisteredFunction;
import static com.mrmysterium.pathmindlua.shadow.cobalt.function.RegisteredFunction.ofV;
import static com.mrmysterium.pathmindlua.shadow.cobalt.function.RegisteredFunction.bind;
import com.pathmind.api.addon.PathmindRuntime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
```

**Table build method** (RESEARCH.md Pattern 2 — `RegisteredFunction.bind`):
```java
public static LuaTable build(LuaState state, PathmindRuntime runtime,
                              AtomicBoolean timedOut, AtomicLong computeMs) {
    return bind(new RegisteredFunction[]{
        ofV("getVar",       (s, args) -> getVar(args, runtime)),
        ofV("setVar",       (s, args) -> setVar(args, runtime)),
        ofV("moveTo",       (s, args) -> moveTo(args, runtime, computeMs)),
        ofV("getPosition",  (s, args) -> getPosition(runtime)),
        ofV("getInventory", (s, args) -> getInventory(runtime)),
        ofV("getBlock",     (s, args) -> getBlock(args, runtime)),
    });
}
```

**getVar implementation** (RESEARCH.md Pattern 2 — value marshaling):
```java
private static Varargs getVar(Varargs args, PathmindRuntime runtime) throws LuaError {
    String name = args.arg(1).checkLuaString().toString();
    Object value = runtime.getVariable(name);
    if (value == null)            return Constants.NIL;
    if (value instanceof Double d) return valueOf(d);
    if (value instanceof Boolean b) return valueOf(b);
    if (value instanceof String s)  return valueOf(s);
    return Constants.NIL;
}
```

**moveTo with timeout clock pause/resume** (RESEARCH.md Pattern 4 — blocking call):
```java
private static Varargs moveTo(Varargs args, PathmindRuntime runtime,
                               AtomicLong computeMs) throws LuaError {
    double x = args.arg(1).checkDouble();
    double y = args.arg(2).checkDouble();
    double z = args.arg(3).checkDouble();
    CompletableFuture<Void> navFuture = runtime.moveTo(x, y, z);
    // Pause compute timer while blocking on nav future
    long pauseStart = System.currentTimeMillis();
    try {
        navFuture.get();   // blocks this worker thread; game tick thread keeps running
    } catch (ExecutionException e) {
        throw new LuaError("moveTo failed: " + e.getCause().getMessage());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new LuaError("moveTo interrupted");
    }
    // Resume: do NOT add the blocked wall-clock time to the compute budget
    return Constants.NONE;
}
```

**getPosition return shape** (`{x=, y=, z=}` Lua table):
```java
private static Varargs getPosition(PathmindRuntime runtime) throws LuaError {
    double[] pos = runtime.getPosition();
    LuaTable t = tableOf();
    t.rawset("x", valueOf(pos[0]));
    t.rawset("y", valueOf(pos[1]));
    t.rawset("z", valueOf(pos[2]));
    return t;
}
```

---

### `pathmind-lua/build.gradle.kts` (modify — add Cobalt + shadowJar)

**Analog:** itself (lines 1-71, read in full above).

**Changes to make** (additions only, do not alter existing content):

Add SquidDev repository to the `repositories` block (after line 29 `mavenCentral()`):
```kotlin
maven {
    name = "SquidDev"
    url = uri("https://squiddev.cc/maven")
}
```

Add Shadow plugin to the `plugins` block (after line 3):
```kotlin
id("com.gradleup.shadow") version "9.4.1"
```

Add Cobalt dependency to the `dependencies` block (after the `modCompileOnly` line):
```kotlin
// Cobalt Lua VM — shadow-bundled to avoid classpath collision with other mods
implementation("org.squiddev:Cobalt:0.7.3")
```

Add shadowJar configuration block (after the `tasks.withType<JavaCompile>` block):
```kotlin
tasks.shadowJar {
    // Relocate Cobalt so it does not collide with other mods bundling Cobalt
    relocate("org.squiddev.cobalt", "com.mrmysterium.pathmindlua.shadow.cobalt")
    // Ensure the relocated jar is the output artifact
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
```

**Pre-implementation check:** Before finalizing relocation, run `grep -r "Class.forName" <cobalt-sources>/` to verify no dynamic class loading strings that shadow would miss (RESEARCH.md Pitfall 2).

---

## Shared Patterns

### Chat Error Message Dispatch
**Source:** `common/src/main/java/com/pathmind/nodes/Node.java` lines 541-555
**Apply to:** `PathmindRuntimeImpl.sendErrorToChat()`, error surface in `CobaltVm.run()`
```java
// Verified prefix constant (Node.java line 169):
private static final String CHAT_MESSAGE_PREFIX = "§4[§cPathmind§4] §7";

// Dispatch pattern (Node.java lines 546, 554):
client.execute(() -> {
    if (client.player == null) return;
    client.player.sendMessage(Text.literal(CHAT_MESSAGE_PREFIX + message), false);
});
```

### Main-Thread Dispatch for MC Reads
**Source:** `common/src/main/java/com/pathmind/nodes/Node.java` lines 3818-3827
**Apply to:** All three game-state methods in `PathmindRuntimeImpl` (`getPosition`, `getInventory`, `getBlock`)
```java
// Verified idiom:
client.execute(() -> {
    // work on client thread
    result.complete(value);
});
double[] pos = result.get(30, TimeUnit.SECONDS);  // worker blocks; safe (separate from tick CF)
```

### AddonNodeExecutor Async Contract
**Source:** `common/src/main/java/com/pathmind/api/addon/AddonNodeExecutor.java` + `Node.java` lines 3840-3878
**Apply to:** `LuaNodeExecutor.execute()` — must return `CompletableFuture<NodeResult>` immediately; complete it off-thread
```java
// Node.java wires result via whenComplete (lines 3862-3872):
exec.execute(ctx).whenComplete((result, throwable) -> {
    if (throwable != null) {
        NodeExecutionCompletion.completeExceptionally(future, throwable);
    } else if (result == NodeResult.FAILURE) {
        NodeExecutionCompletion.fail(this, client, future, "...");
    } else {
        future.complete(null);
    }
});
```

### RuntimeVariable Construction
**Source:** `common/src/main/java/com/pathmind/execution/ExecutionManager.java` lines 136-207, 960-999
**Apply to:** `PathmindRuntimeImpl.setVariable()` — must match the key conventions used by `buildPresetInputValueMap`
```
PARAM_AMOUNT  → key "Amount"   → Double.toString(value)
PARAM_BOOLEAN → keys "Mode"="literal", "Toggle"=Boolean.toString(value), "Variable"=""
PARAM_MESSAGE → key "Text"     → String value
```

### PathmindNavigator moveTo
**Source:** `common/src/main/java/com/pathmind/execution/PathmindNavigator.java` line 361
**Apply to:** `PathmindRuntimeImpl.moveTo()`
```java
// Verified real signature:
public synchronized boolean startGoto(BlockPos targetPos, String commandLabel, CompletableFuture<Void> future)
// Returns false only if targetPos or future is null.
// Internally calls stopInternal(false, "replaced") — cancels any active navigation first.
// Completes future via complete(null) on arrival, completeExceptionally on failure.
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `pathmind-lua/.../vm/LuaGlobals.java` | utility | transform | No Cobalt VM setup exists anywhere in either repo. Use RESEARCH.md Pattern 2 (RegisteredFunction.bind) directly. |

---

## Key Wire Points (for planner)

These are the exact lines in existing files where Phase 2 code must be hooked in — verified by direct source read:

| Location | Line | What to add |
|----------|------|-------------|
| `Node.java` `executeAddonNode()` | after line 3851 (`ctx.setScriptText(...)`) | `ctx.setRuntime(new PathmindRuntimeImpl(ExecutionManager.getInstance()));` |
| `Node.java` imports | top of file | `import com.pathmind.execution.PathmindRuntimeImpl;` |
| `pathmind-lua/build.gradle.kts` | `repositories` block | SquidDev maven repo |
| `pathmind-lua/build.gradle.kts` | `plugins` block | Shadow plugin |
| `pathmind-lua/build.gradle.kts` | `dependencies` block | Cobalt 0.7.3 `implementation` |
| `pathmind-lua/build.gradle.kts` | end of file | `shadowJar` task with relocation rule |

---

## Metadata

**Analog search scope:** `common/src/main/java/com/pathmind/api/addon/`, `common/src/main/java/com/pathmind/execution/`, `common/src/main/java/com/pathmind/nodes/Node.java` (targeted reads), `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/`
**Source files read:** 8 (AddonNodeContext, AddonNodeExecutor, NodeResult, PathmindNavigator excerpt, ExecutionManager excerpt, Node.java excerpts ×3, LuaNodeExecutor, LuaAddonEntrypoint, build.gradle.kts)
**Pattern extraction date:** 2026-06-13
