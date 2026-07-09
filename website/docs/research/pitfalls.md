# Pitfalls Research

**Domain:** Minecraft mod addon API retrofit + embedded Lua scripting + in-game code editor
**Researched:** 2026-06-12
**Confidence:** HIGH (cross-referenced LuaJ docs, Fabric Loader issues, Architectury Loom issues, OpenComputers post-mortems, Fabric API design docs)

---

## Critical Pitfalls

### Pitfall 1: Addon Compiled Against Implementation Classes Instead of API Jar

**What goes wrong:**
The sibling-repo addon (pathmind-lua-addon) imports and compiles against Pathmind's full mod jar rather than a dedicated API-only artifact. When Pathmind refactors internal class names, moves packages, or splits the monoliths (NodeGraph, PathmindNavigator, Node are all 7,000–15,000 lines), the addon breaks at compile time with `NoClassDefFoundError` or `SymbolNotFound` because the classes it referenced are no longer where they were.

**Why it happens:**
The path of least resistance during early development is `modImplementation(files("../pathmind/build/libs/pathmind-1.0.jar"))`. This works immediately but couples the addon to the full impl. Developers defer extracting the API surface until it hurts — by which point the coupling is entrenched.

**How to avoid:**
Create a dedicated `pathmind-api` source set or subproject from the start of the API phase. Publish only that artifact to mavenLocal. The addon declares `modCompileOnly "com.pathmind:pathmind-api:..."` — it gets compile-time types but cannot reference impl classes at all (they are absent). When the addon compiles cleanly, the API boundary is honest.

**Warning signs:**
- Addon `import` statements reference `com.pathmind.ui.graph.NodeGraph` or `com.pathmind.execution.PathmindNavigator` (the monolith classes, not API interfaces)
- Addon tests can only run with the full Pathmind jar on the classpath, never with the API jar alone
- Any internal Pathmind refactor causes the addon to fail compilation

**Phase to address:**
API Foundation phase (the first phase that extracts the addon API). Define the api/impl split before writing a single line of addon code.

---

### Pitfall 2: mavenLocal Staleness — Addon Uses Stale Remapped Jar After Pathmind Rebuild

**What goes wrong:**
Fabric Loom's `ModConfigurationRemapper` remaps dependency jars once and caches them in `.gradle/loom-cache/remapped_mods`. If you rebuild Pathmind's API jar (fixing a bug, adding a method) without bumping its version string, the addon's Gradle build finds the stale remapped jar in cache and uses it. The addon's runtime behavior diverges silently from the Pathmind version installed in the mods folder.

**Why it happens:**
SNAPSHOT versions are common during co-development. Loom checks the version coordinate, not the jar content hash, for cache invalidation. This is a documented bug in fabric-loom (issue #1290): "the `:remapping N mods` step is only done once" — content changes without version changes are invisible to Loom.

**How to avoid:**
During active co-development, use a dev loop script that (1) increments a build counter suffix in the version (`1.0-dev.N`), (2) publishes to mavenLocal, (3) deletes `.gradle/loom-cache/remapped_mods` in the addon project, and (4) runs the addon build. Alternatively, use the `-dev` classifier jar (named-mapped artifact) via `artifact(jar) { builtBy jar }` — though Loom's support for this path is also imperfect. A simple `./gradlew publishToMavenLocal && rm -rf addon/.gradle/loom-cache/remapped_mods` Makefile target eliminates 90% of the pain.

**Warning signs:**
- Addon calls a method that you just added to the API but gets `NoSuchMethodError` at runtime
- Pathmind and the addon show different behavior than expected given the current source state
- Addon `build/` contains a remapped jar with an older timestamp than Pathmind's current output jar

**Phase to address:**
API Foundation phase — establish the dev loop tooling before the addon writes any code against the API.

---

### Pitfall 3: Fabric Entrypoint Initialization Order is Undefined — Addon Calls Pathmind Before It Is Ready

**What goes wrong:**
The addon's entrypoint runs and calls `PathmindAddonRegistry.register(...)`. But Pathmind's `onInitialize` has not run yet (or has run but has not reached the point where the registry is live). The call crashes with `NullPointerException` or a custom "registry not initialized" error, and because Fabric Loader gives no guarantee on entrypoint call order across mods, the crash is nondeterministic — it may only appear on specific MC versions or JVM startup timings.

**Why it happens:**
Fabric Loader explicitly does not order entrypoints by the `depends` field in `fabric.mod.json`. The `depends` key controls whether the dependency is present, not when it initializes (Fabric Loader issue #459, closed unresolved as of 2021). Developers assume `depends = ["pathmind"]` means Pathmind initializes first.

**How to avoid:**
Design the Pathmind registry to be registerable at any point before the first graph execution, not only after `onInitialize` completes. Pattern: use a lazy initialization guard in `PathmindAddonRegistry` — if called before internal state is live, queue registrations and flush them when Pathmind finishes its init. Alternatively, use a Fabric event (e.g., a `PATHMIND_READY` custom event fired at the end of Pathmind's init) that the addon listens to before registering nodes.

**Warning signs:**
- Addon registration works in a solo dev environment but crashes in a pack with 30+ other mods
- Stack trace originates in the addon's `onInitialize`, pointing to a Pathmind registry method returning null
- Removing an unrelated mod makes the crash disappear (it shifted the init order)

**Phase to address:**
API Foundation phase — the registry design must explicitly handle out-of-order init from the first commit.

---

### Pitfall 4: API Surface Leaking Internals — Monolith Classes Exposed Directly

**What goes wrong:**
The fastest path to "addon can register a node" is exposing `NodeGraph`, `ExecutionManager`, or `Node` directly in the API. These classes are 7,000–15,000 lines each (per CONCERNS.md), have no stable contract, and change whenever Pathmind evolves. Every internal refactor becomes an API-breaking change. Addon developers (including the Lua addon) must update code constantly. Version coordination becomes mandatory for every Pathmind release.

**Why it happens:**
Retrofitting an API on a monolith naturally reveals the monolith as the "simplest API." Fabric API itself learned this lesson — its `.api` / `.impl` / `.mixin` package separation with `@ApiStatus.Internal` on impl packages is the direct answer to this failure mode.

**How to avoid:**
Define narrow interface types for everything the addon API exposes: `NodeDefinition`, `NodeExecutionContext`, `AddonRegistrar`, `GraphVariableAccessor`. These are thin interfaces backed by the monolith's internals but with stable method signatures. The addon compiles against interfaces only. When the monolith refactors, only the thin adapter between interface and impl must change — not the addon.

**Warning signs:**
- API changelog has entries like "renamed `NodeGraph.getActiveNode()` to `NodeGraph.getCurrentNode()` — update addons"
- The addon's `import` list contains classes from `com.pathmind.ui.graph` or `com.pathmind.execution` (not from `com.pathmind.api`)
- Every Pathmind version bump breaks the addon's compile

**Phase to address:**
API Foundation phase — design interface types before exposing anything.

---

### Pitfall 5: LuaJ Blocking the Minecraft Main Thread During Script Execution

**What goes wrong:**
Script node execution is called from Pathmind's `ExecutionManager` which runs on or near the game's main tick thread. The Lua script runs synchronously. Any script with a loop (`while true do ... end`), heavy computation, or an `await`-style call that polls game state will block the thread for multiple ticks. Frame rate drops to 0, the client freezes, the OS reports "not responding," and the only recovery is a hard kill. Because sandbox/timeouts are explicitly out of scope for v1, this is a real risk.

**Why it happens:**
The simplest integration calls `chunk.call()` from the node's `execute()` method and returns when Lua returns. LuaJ's `LuaThread` coroutine model requires explicit yielding — it does not preempt. A non-yielding script owns the thread forever.

**How to avoid:**
Even without full sandboxing, run Lua in a separate thread with a configurable wall-clock timeout enforced by the calling Java thread (interrupt + join with timeout). The script's result is passed back via a `CompletableFuture` or a thread-safe queue. If the timeout fires, interrupt the thread and surface a user-visible error on the node. This is the minimum safe integration; it prevents client freeze even when sandboxing is deferred.

**Warning signs:**
- The node's `execute()` method directly calls `chunk.call()` without any timeout wrapper
- Test scripts with `while true do end` freeze the game rather than showing an error
- Execution time is not bounded anywhere in the call path

**Phase to address:**
Lua VM integration phase (the phase that first runs Lua scripts). The thread wrapper is not optional even in v1; it is the minimum viable safety net.

---

### Pitfall 6: LuaJ luajava Bindings — Full JVM Access Is a Sandbox Escape

**What goes wrong:**
LuaJ ships a `luajava` library that, when loaded, gives Lua scripts full access to the Java VM: they can call `luajava.newInstance("java.lang.Runtime")`, execute OS commands, read arbitrary files, or crash the JVM. Even without malicious intent, a script that accidentally calls a Minecraft internal method from the wrong thread can corrupt game state.

**Why it happens:**
`JsePlatform.standardGlobals()` loads `luajava` by default. Developers use it without reading the sandbox notes, or they forget to strip it when constructing a `Globals` instance for user scripts.

**How to avoid:**
Never use `JsePlatform.standardGlobals()` for user scripts. Build `Globals` manually using `JsePlatform.debugGlobals()` as a starting point, then explicitly remove `luajava`, `CoroutineLib`, `JseIoLib`, `JseOsLib`, and `LoadState`/`LuaC`. The LuaJ sandbox example (`examples/jse/SampleSandboxed.java`) demonstrates this exact approach. Expose game state through a controlled Pathmind Lua library that you write — never through raw luajava.

**Warning signs:**
- `Globals` is constructed via `JsePlatform.standardGlobals()` anywhere in the Lua addon
- `luajava` appears in the globals table when a script is executed (check `globals.get("luajava") != LuaValue.NIL`)
- A test script can call `luajava.newInstance("java.io.File")` without error

**Phase to address:**
Lua VM integration phase — the Globals construction happens once and must be correct from day one.

---

### Pitfall 7: xpcall-Based Infinite Loop Bypass — Timeout Caught by Error Handler

**What goes wrong:**
Even with a debug-hook instruction counter in place (the standard LuaJ timeout approach), a Lua script that wraps everything in `xpcall` with a recursive message handler can catch the `Error` thrown by the hook, nest another `xpcall`, and catch the next timeout too — indefinitely. This is the exact DoS vector that OpenComputers patched in version 1.8.4 (security advisory GHSA-54j4-xpgj-cq4g). The debug hook throws a Java `Error` (not a `LuaError`) specifically to bypass `pcall`/`xpcall`, but nested handlers can still intercept it under certain conditions.

**Why it happens:**
The LuaJ sandbox documentation recommends using `Error` subclasses specifically because Lua cannot catch Java `Error` — but the nesting of error handlers creates a re-entry path that repeatedly resets the hook counter.

**How to avoid:**
Use the wall-clock timeout (Java thread interrupt) described in Pitfall 5 as the primary enforcement mechanism. The debug hook instruction counter is a secondary defense. The thread-level timeout cannot be bypassed by Lua code. Keep track of a `volatile boolean timedOut` flag set by the controlling thread; check it in the hook and in every Pathmind callback callable from Lua.

**Warning signs:**
- Timeout is implemented solely via a debug hook instruction count, with no thread-level enforcement
- Test script `while true do xpcall(function() while true do end end, function() end) end` does not terminate within the configured timeout
- The phrase "instruction count" appears in the timeout logic but "thread interrupt" does not

**Phase to address:**
Lua VM integration phase — design the timeout as thread-interrupt-first, hook-second.

---

### Pitfall 8: Lua-to-Java Object Conversion Heap Exhaustion

**What goes wrong:**
A Lua script passes a large table (thousands of entries) or deeply nested tables into a Pathmind API function. The LuaJ binding converts this to a Java array or varargs parameter, materializing the entire structure in heap memory at once. OpenComputers issue #1774 documented this: a single computer can exhaust the entire Java heap via `component.invoke()` with an oversized table argument. On a modded MC instance already under memory pressure, this triggers GC pauses or OOM.

**Why it happens:**
LuaJ's varargs conversion is unbounded. `LuaValue.checktable()` will follow any table regardless of its depth or entry count before the Java side ever sees the argument.

**How to avoid:**
Every Pathmind API function callable from Lua must validate argument sizes before conversion: maximum table depth (2–3 levels), maximum table entry count (e.g., 64 entries), maximum string length (e.g., 4KB). Enforce these in the Java method before touching the `LuaTable` contents. Reject oversized arguments with a `LuaError` so the script sees a clear error message.

**Warning signs:**
- Pathmind Lua API functions accept `LuaTable` arguments without checking `.length()` first
- A test script `local t = {}; for i=1,10000 do t[i] = i end; pathmind.someFunction(t)` causes a GC pause or crash
- No argument size constants are defined anywhere in the Lua addon

**Phase to address:**
Lua API design phase (the phase that defines the Pathmind Lua library).

---

### Pitfall 9: Lua Game State Access from Wrong Thread — Silent Corruption

**What goes wrong:**
A Lua script calls a Pathmind API function (e.g., `pathmind.getPlayerPosition()`) that reads from Minecraft's `ClientLevel` or `LocalPlayer`. If the script runs off the main game thread (which is the correct approach per Pitfall 5), this read is a data race. Minecraft's level and entity objects are not thread-safe. The result is either a stale value, a `ConcurrentModificationException`, or a crash with "Accessing LegacyRandomSource from multiple threads" — the same class of error seen in Forge bug reports.

**Why it happens:**
Threading Lua correctly (off main thread) and reading game state naively creates a race. The tension between the two correct decisions produces this error.

**How to avoid:**
All Pathmind API functions that touch game state must schedule the read onto the main thread and block on the result (using `Minecraft.getInstance().submit(callable).get()` with a short timeout). This is safe because the main thread is not blocked by the Lua thread — they are running concurrently. The API layer absorbs the cross-thread marshalling; the Lua script sees a synchronous call.

**Warning signs:**
- Pathmind Lua API functions call `Minecraft.getInstance().player` or `level.getBlockState()` directly without `submit()`
- Intermittent crashes with `ConcurrentModificationException` in level data structures during script execution
- Tests pass when the game is idle but fail during active chunk loading or entity updates

**Phase to address:**
Lua API design phase — establish the main-thread dispatch pattern in the first API function and apply it to all subsequent functions.

---

### Pitfall 10: LuaJ Globals Reuse Across Script Executions — State Leaks Between Runs

**What goes wrong:**
A single `Globals` instance is created once and reused across all script node executions. Script run A defines a global variable `x = 42`. Script run B (the same script, or a different one) finds `x` already defined from run A. Side-effects from one execution contaminate the next. In a node graph that loops (using Pathmind's existing branching nodes), this becomes a subtle debugging nightmare.

**Why it happens:**
`Globals` creation involves classloading and is perceived as expensive. Developers reuse the instance to avoid the overhead. LuaJ's README warns: "Each thread created by client code must be given its own, distinct Globals instance" — but this applies to Java threads; the warning about state isolation across invocations is less visible.

**How to avoid:**
Create a fresh `Globals` instance per script execution. The Pathmind Lua library bindings should be registered once as a shared read-only table and injected into each fresh `Globals` via a factory method, so the overhead is minimized (library code is not re-parsed per execution, only the state container is new).

**Warning signs:**
- A test that runs the same script twice and modifies a global in the first run sees the modified value in the second run
- The Lua addon holds a `static final Globals globals` field
- The same `LuaTable` object is returned from successive calls to the Pathmind binding factory

**Phase to address:**
Lua VM integration phase — establish the per-execution Globals pattern before writing any script tests.

---

### Pitfall 11: In-Game Editor Escape Key Captured by MC Instead of Editor

**What goes wrong:**
The in-game code editor is implemented as a custom `Screen` (or a panel within a Screen). The user presses Escape to cancel a Lua expression or exit a modal dialog inside the editor. Instead, MC's `Screen.keyPressed` superclass implementation intercepts Escape and closes the entire Screen, losing unsaved script changes. Similarly, Tab (used for indentation) cycles focus between widgets rather than inserting a tab character.

**Why it happens:**
Minecraft's `Screen` base class calls `this.onClose()` on Escape and moves widget focus on Tab. Custom screens that call `super.keyPressed()` unconditionally inherit this behavior.

**How to avoid:**
Override `keyPressed` in the editor screen/widget and consume Escape and Tab before calling super. For Escape: check if the editor has an active "inner context" (e.g., an autocomplete popup open) — if yes, close the popup and return `true` without calling super. Only let Escape reach super when the editor has no inner state to dismiss. For Tab: insert a literal tab or spaces into the cursor position and return `true`.

**Warning signs:**
- Pressing Escape while the cursor is in the text area closes the entire node editor screen
- Pressing Tab jumps focus to the next widget rather than indenting code
- The screen's `keyPressed` has `return super.keyPressed(key, scanCode, modifiers)` as its only line

**Phase to address:**
In-game editor phase — handle key routing in the first commit that creates the editor Screen.

---

### Pitfall 12: Addon Preset Serialization Is Not Versioned — Breaking Changes Corrupt Saves

**What goes wrong:**
The Lua Script node serializes its script content as part of the Pathmind JSON preset. In a future Pathmind version, the node parameter structure changes (e.g., a new required field is added, or the script field moves). Old presets load, find no migration logic, and either crash (null dereference — a known existing fragility per CONCERNS.md `Node Parameter Serialization` fragile area) or silently discard the script. Users lose saved automations.

**Why it happens:**
The existing Node serialization in Pathmind already lacks version headers and migration layers (CONCERNS.md line 127). Adding the Lua addon's parameters to this fragile system without addressing the gap compounds the problem.

**How to avoid:**
The Lua Script node definition must include a `_schema_version` field in its serialized output from the first build. Define an explicit migration function (`migrateFromV1`, `migrateFromV2`) before any parameter structure change. Hook into Pathmind's deserialization path (which is the right place to intercept) rather than migrating post-hoc. Even if Pathmind's own nodes don't have versioning yet, the Lua addon must not regress its own saves.

**Warning signs:**
- The Lua Script node's `toJson()` / `fromJson()` methods have no `version` field
- Loading a preset saved by an older addon version throws a `NullPointerException` in parameter access (consistent with the CONCERNS.md existing bug pattern)
- No migration tests exist that load a v1-format preset with a v2 addon installed

**Phase to address:**
Lua Script node implementation phase, first serialization commit.

---

### Pitfall 13: Architectury Remapping Strips Multi-Release Jar META-INF/versions

**What goes wrong:**
If pathmind-api (or any dependency it bundles) is a Multi-Release JAR (`Multi-Release: true` in MANIFEST.MF), Architectury Loom's `ModCompileRemapper::shouldRemapMod` remaps it without checking whether the file is actually a mod. The remapping process removes `META-INF/versions`, breaking the JAR. Runtime crash: `java.nio.file.NoSuchFileException: .../META-INF/versions` (Architectury Loom issue #68).

**Why it happens:**
Architectury remaps all Forge dependencies indiscriminately. This hits any library bundled into the API jar that uses Multi-Release JARs (common in modern Java utility libraries).

**How to avoid:**
Avoid bundling Multi-Release JARs into the remapped API artifact. If LuaJ or any supporting library uses Multi-Release, depend on it via `implementation` (not `modImplementation`) and `include` it in the final jar without remapping. Verify this by inspecting the built jar: `jar -tf pathmind-api.jar | grep META-INF/versions`.

**Warning signs:**
- Build succeeds but runtime throws `NoSuchFileException` referencing `META-INF/versions`
- A dependency in the API or addon pom declares `Multi-Release: true`
- The crash appears only in the NeoForge build variant, not Fabric

**Phase to address:**
Build system and artifact publishing phase (part of API Foundation).

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Expose existing NodeGraph/ExecutionManager classes as API directly | Zero extraction work | Every internal refactor is an API breaking change; addon breaks on each Pathmind update | Never — creates a permanent coupling tax |
| Reuse single `Globals` instance across Lua executions | ~5ms savings per execution | State leaks between runs; debugging becomes impossible; looping graphs behave nondeterministically | Never |
| Run Lua on the main thread synchronously | Simplest first integration | Any user script with a loop freezes the client; no recovery without hard kill | Only in a single isolated local test, never in shipped code |
| Use `JsePlatform.standardGlobals()` for user scripts | One-liner setup | Full JVM access via luajava; unacceptable even with sandboxing deferred | Never for user-facing scripts |
| Skip version field in Lua node's serialized output | Saves ~5 lines per save/load | First schema change orphans all saved presets; no migration path | Never — add it in the first commit |
| Hard-code Pathmind API registration calls in addon's `onInitialize` without deferred-init guard | Simplest addon init | Nondeterministic crash if Pathmind's init runs after the addon's | Never — the guard is 10 lines |
| Depend on full Pathmind impl jar from addon | Works immediately | Forces full Pathmind rebuild on any dependency change; couples addon to internals | Acceptable only for a temporary local spike/proof-of-concept, not merged code |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| LuaJ + Minecraft main thread | Call `chunk.call()` from the game tick thread directly | Run Lua in a daemon thread; use `CompletableFuture` + wall-clock timeout to return result to tick thread |
| LuaJ + game state reads | Read `Minecraft.getInstance().player` inside Lua API binding directly | `Minecraft.getInstance().submit(() -> player.getPos()).get(50, MILLISECONDS)` |
| Fabric entrypoint + Pathmind registry | Register nodes in `onInitialize` assuming Pathmind is ready | Listen to a `PATHMIND_READY` custom event or use a queued-registration guard in the registry |
| mavenLocal + Loom remapping | Rebuild Pathmind API jar and expect addon to pick it up | Delete `.gradle/loom-cache/remapped_mods` in addon project after every Pathmind API rebuild |
| LuaJ Globals + library setup | `JsePlatform.standardGlobals()` then remove dangerous libs | Build Globals from scratch: `new Globals()` + safe libs only; verify with `globals.get("luajava").isnil()` |
| Lua node + JSON persistence | Use Pathmind's existing unversioned parameter serialization | Add `_schema_version` field to the Lua node's JSON and write migration functions before any schema change |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| LuaJ `Globals` construction per-execution with full standard library | 50–200ms delay per script node execution; noticeable on node graphs that re-execute frequently | Instantiate a lean `Globals` skeleton once; create per-execution instances that inherit pre-built library tables | First time a script node is in a looping graph (~10+ executions/sec) |
| Lua-to-Java table conversion without size guard | GC pause or OOM when a script passes large tables to the API | Add entry count and depth checks at API entry points | Scripts with tables >1000 entries; heavily modded instances with small heap |
| NodeGraph full re-render per frame (existing concern) amplified by Lua node state | Editor frame rate drops further when Lua nodes show execution state (error badge, output values) | Implement dirty-flag on Lua node state; only redraw when script output changes | Graphs with ~50+ nodes where Lua nodes update frequently |
| Synchronous main-thread dispatch in Lua API (`submit().get()`) under heavy tick load | API calls take longer than 50ms when the main thread is busy with A* pathfinding (MAX_EXPANSIONS 64000) | Enforce a tight timeout on `submit().get()`; surface "game state unavailable" error to Lua rather than blocking | Coincidence of pathfinding computation and script execution |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Loading `luajava` in user script Globals | Full JVM access: execute OS commands, read files, crash JVM | Never use `standardGlobals()`; build Globals manually without `LuajavaLib` |
| Loading `CoroutineLib` in sandboxed Globals | Creates uncontrolled Java threads outside game supervision | Exclude `CoroutineLib` from the Globals; Pathmind's async model is Java-managed |
| Instruction-count-only timeout (no thread interrupt) | `xpcall` with recursive error handlers bypasses the debug hook; game freezes (OpenComputers GHSA-54j4-xpgj-cq4g) | Primary timeout is thread interrupt; instruction count is secondary |
| Unbounded table argument conversion in Lua API | Heap exhaustion via large table arguments (OpenComputers issue #1774) | Size-guard every `LuaTable` argument before iteration |
| Loading `JseOsLib` / `JseIoLib` | File system read/write from scripts; ability to read `marketplace_auth.json` | Exclude from Globals; these have zero legitimate use in an automation scripting context |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Escape in editor closes the screen | User loses unsaved script edits | Consume Escape at editor level; close screen only when editor has no dismissible state |
| Tab in editor cycles widget focus | Tab-indentation impossible; user must use spaces manually | Intercept Tab in `keyPressed` and insert indentation character(s) before calling super |
| Silent Lua error (exception swallowed) | Script fails, node tree halts for no apparent reason | Surface Lua errors as visible node state (red badge, error message overlay); log full stack trace with `LOGGER.error()` — the existing generic exception-swallowing pattern in CONCERNS.md must not be repeated here |
| No indication script is running | User triggers node, nothing visible happens during long scripts | Show a "running" state on the node; enforce timeout so "running" cannot last forever |
| Autocomplete popup blocks code view | User cannot see the code they are writing while navigating suggestions | Position popup below or beside cursor; dismiss on Escape (per Pitfall 11 — consume Escape before super) |

---

## "Looks Done But Isn't" Checklist

- [ ] **Addon API entrypoint:** Often missing the deferred-init guard — verify by loading the addon with Pathmind in a pack where another mod forces Pathmind to init last.
- [ ] **Lua sandbox:** Often missing `luajava` exclusion — verify with `globals.get("luajava").isnil()` assertion in a unit test.
- [ ] **Script timeout:** Often missing thread-interrupt path (instruction count only) — verify that `while true do xpcall(function() while true do end end, function() end) end` terminates within 2× the configured timeout.
- [ ] **API jar separation:** Often shipping full impl classes in the API artifact — verify by compiling the addon against only the API jar (no impl classes on classpath).
- [ ] **Lua node serialization:** Often missing `_schema_version` field — verify by checking the JSON of a saved preset containing a Lua script node.
- [ ] **Key event routing:** Often leaking Escape and Tab to MC's screen superclass — verify in-game that Escape inside an open autocomplete popup does not close the editor screen.
- [ ] **Game state thread safety:** Often reading `player` or `level` directly in API bindings — verify by running a script while pathfinding is active (heavy main-thread load) without `ConcurrentModificationException`.
- [ ] **mavenLocal dev loop:** Often relying on stale remapped jar — verify by adding a new method to the API, rebuilding without version bump, and confirming the addon picks up the change (it should not without cache deletion; document the correct dev loop command).

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Addon compiled against impl, now coupling is entrenched | HIGH | Extract API interfaces from monolith; write adapter layer between interface and impl; update all addon import sites — this is a multi-day refactor |
| mavenLocal stale jar causing wrong behavior | LOW | Delete `.gradle/loom-cache/remapped_mods` in addon; rebuild |
| Lua globals state leak between runs | MEDIUM | Replace `static Globals` with a factory; audit all tests for globals state assumptions; re-run suite |
| xpcall timeout bypass discovered in production | MEDIUM | Add thread-interrupt timeout wrapper around all `chunk.call()` invocations; the instruction-count hook becomes secondary |
| Preset corruption from missing schema version | HIGH | Implement best-effort migration: detect missing `_schema_version`, attempt to read the old format, log unrecoverable presets; user manually re-enters lost scripts — some data loss is unavoidable |
| API surface too wide — breaking changes every version | HIGH | Introduce deprecation cycle: mark old API `@Deprecated`, keep for 1 version, remove in next — communicate via API changelog |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Addon compiled against impl (P1) | API Foundation | CI build of addon using only `-api` jar artifact |
| mavenLocal staleness (P2) | API Foundation | Dev loop script documented and tested |
| Entrypoint init order (P3) | API Foundation | Load addon in pack with 20+ other mods; no NPE on startup |
| API surface leaking internals (P4) | API Foundation | No `import com.pathmind.ui.graph.*` or `com.pathmind.execution.*` in addon source |
| Main thread blocking (P5) | Lua VM integration | `while true do end` script terminates within 2× timeout without freeze |
| luajava sandbox escape (P6) | Lua VM integration | `globals.get("luajava").isnil()` unit test passes |
| xpcall timeout bypass (P7) | Lua VM integration | Nested `xpcall` DoS test terminates within timeout |
| Heap exhaustion from table conversion (P8) | Lua API design | Test: pass 10,000-entry table to every API function; no OOM |
| Game state thread safety (P9) | Lua API design | Stress test: run script during heavy pathfinding; no CME |
| Globals state leak (P10) | Lua VM integration | Two sequential runs of a globals-mutating script produce isolated results |
| Escape/Tab key routing (P11) | In-game editor | Manual UAT: Escape dismisses inner state only; Tab indents |
| Preset versioning (P12) | Lua Script node implementation | Load a v1-format preset with a v2 addon; script content preserved |
| Multi-Release JAR remapping (P13) | Build system / API Foundation | Build all variants; no `NoSuchFileException` at runtime |

---

## Sources

- [LuaJ sandboxed example (SampleSandboxed.java)](https://github.com/gelldur/luaj/blob/master/examples/jse/SampleSandboxed.java) — sandbox library exclusion and instruction hook pattern
- [LuaJ README / Getting Started](http://luaj.org/luaj/3.0/README.html) — Globals threading rules, OrphanedThread, weak key memory leak
- [OpenComputers xpcall DoS advisory GHSA-54j4-xpgj-cq4g](https://github.com/MightyPirates/OpenComputers/security/advisories/GHSA-54j4-xpgj-cq4g) — timeout bypass via nested error handlers
- [OpenComputers heap exhaustion issue #1774](https://github.com/MightyPirates/OpenComputers/issues/1774) — LuaTable-to-Java conversion OOM
- [Fabric Loom remapping stale cache issue #1290](https://github.com/FabricMC/fabric-loom/issues/1290) — mavenLocal SNAPSHOT stale remapped jar
- [Architectury Loom remapping issue #68](https://github.com/architectury/architectury-loom/issues/68) — Multi-Release JAR META-INF/versions stripped
- [Fabric Loader entrypoint ordering issue #459](https://github.com/FabricMC/fabric-loader/issues/459) — `depends` does not control init order
- [Fabric API CONTRIBUTING.md — api/impl/mixin separation](https://github.com/FabricMC/fabric-api/blob/1.21/CONTRIBUTING.md) — package separation with `@ApiStatus.Internal`
- [Fabric Entrypoints documentation](https://wiki.fabricmc.net/documentation:entrypoint) — separate client/server entrypoint classes recommendation
- [IMBlockerFabric](https://modrinth.com/mod/imblocker) — IME conflict evidence
- Pathmind codebase CONCERNS.md — existing NodeGraph/ExecutionManager/Node fragility, exception swallowing patterns

---
*Pitfalls research for: Minecraft mod addon API retrofit + embedded Lua scripting + in-game code editor*
*Researched: 2026-06-12*
