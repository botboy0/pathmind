---
phase: 02-lua-vm-core-bindings
plan: "01"
subsystem: lua-vm-runtime-bridge
tags: [lua, cobalt, vm, runtime-bridge, shadow, tdd]
dependency_graph:
  requires: []
  provides:
    - PathmindRuntime interface (com.pathmind.api.addon)
    - AddonNodeContext.getRuntime()/setRuntime()
    - PathmindRuntimeImpl (sendErrorToChat implemented, stubs for 02-04)
    - CobaltVm.run() — real Lua execution on worker thread
    - PathmindBindings.build() — stub pathmind.* table
    - LuaNodeExecutor — real Cobalt worker lifecycle
    - CobaltVmSmokeTest — four behavioral cases passing
  affects:
    - common/src/main/java/com/pathmind/nodes/Node.java (ctx.setRuntime wire point)
    - pathmind-lua/build.gradle.kts (Cobalt dependency + shadowJar)
tech_stack:
  added:
    - "org.squiddev:Cobalt:0.7.3 (shadow-relocated into addon jar)"
    - "com.gradleup.shadow:9.4.1 (shadow plugin for addon)"
    - "JUnit Jupiter 5.11.4 (test dep in addon)"
  patterns:
    - "TDD RED/GREEN per task (test(02-01) then feat(02-01))"
    - "Shadow JAR relocation: org.squiddev.cobalt → com.mrmysterium.pathmindlua.shadow.cobalt"
    - "MinecraftClient.execute dispatch for chat (client-thread safety)"
    - "AtomicBoolean + ScheduledExecutorService timeout + Thread.interrupt() safety net"
key_files:
  created:
    - common/src/main/java/com/pathmind/api/addon/PathmindRuntime.java
    - common/src/main/java/com/pathmind/execution/PathmindRuntimeImpl.java
    - common/src/test/java/com/pathmind/execution/PathmindRuntimeImplTest.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/CobaltVm.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/vm/PathmindBindings.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/test/java/com/mrmysterium/pathmindlua/vm/CobaltVmSmokeTest.java
  modified:
    - common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java
    - common/src/main/java/com/pathmind/nodes/Node.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeExecutor.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/build.gradle.kts
decisions:
  - "Source imports use org.squiddev.cobalt (original); shadow relocation to com.mrmysterium.pathmindlua.shadow.cobalt occurs at JAR packaging time — this is intentional and correct"
  - "isZip64=true required for shadowJar because Architectury Loom + Cobalt archive exceeds 65535 entries"
  - "testImplementation for Pathmind artifact needed in addon build so PathmindRuntime is on test classpath"
  - "Cobalt 0.7.3 has no Class.forName usage — relocation is safe; mergeServiceFiles() added as precaution"
metrics:
  duration: "16 min"
  completed: "2026-06-13"
  tasks_completed: 3
  files_created: 6
  files_modified: 4
---

# Phase 02 Plan 01: Lua VM + Runtime Bridge — Summary

**One-liner:** Cobalt 0.7.3 shadow-relocated into addon jar with fresh-globals sandbox + PathmindRuntime interface wired through Node.executeAddonNode; smoke tests prove VM execution, error surfacing, sandbox, and timeout interrupt.

---

## What Was Built

### Task 1: Cobalt + Shadow Relocation (Sibling Repo Build)

Added to `pathmind-lua/build.gradle.kts`:
- Shadow plugin `com.gradleup.shadow:9.4.1`
- SquidDev maven repository (`https://squiddev.cc/maven`)
- `implementation("org.squiddev:Cobalt:0.7.3")`
- `shadowJar` task: `relocate("org.squiddev.cobalt", "com.mrmysterium.pathmindlua.shadow.cobalt")`, `isZip64 = true`, `mergeServiceFiles()`
- JUnit Jupiter 5.11.4 test dependencies
- `testImplementation("com.pathmind:pathmind-fabric:$pathmindVersion")` for test classpath

Verification: `./gradlew shadowJar` BUILD SUCCESSFUL; jar contains `com/mrmysterium/pathmindlua/shadow/cobalt/LuaState.class` (171 relocated classes); no `org/squiddev/cobalt/` paths remain.

**Note:** Task 1 is sibling-repo-only. No Pathmind repo commit for this task (per cross-repo note).

### Task 2: PathmindRuntime Interface + PathmindRuntimeImpl + Node Wire Point (TDD)

**PathmindRuntime interface** (`com.pathmind.api.addon`):
- 7 methods: `getVariable`, `setVariable`, `moveTo`, `getPosition`, `getInventory`, `getBlock`, `sendErrorToChat`
- Only `java.util.concurrent.CompletableFuture` imported — no MC types (API-09 version-agnostic)

**AddonNodeContext**: added `getRuntime()`/`setRuntime(PathmindRuntime)` mirroring existing `scriptText` accessors.

**PathmindRuntimeImpl** (`com.pathmind.execution`):
- `sendErrorToChat`: fully implemented — `MinecraftClient.execute` dispatch, two null-guards (client + player), `CHAT_MESSAGE_PREFIX` constant
- `getVariable`/`setVariable`: compile-ready stubs that would work with ExecutionManager (wired logic complete, not exercised by plan 01 tests)
- `moveTo`/`getPosition`/`getInventory`/`getBlock`: stubbed with TODO markers for plans 03/04

**Node.java wire point** at line 3855: `ctx.setRuntime(new PathmindRuntimeImpl(ExecutionManager.getInstance()))` added after `ctx.setScriptText`.

TDD commits: `test(02-01)` RED → `feat(02-01)` GREEN. Tests pass: constructor null-safety, sendErrorToChat headless null-guard, AddonNodeContext round-trip.

### Task 3: CobaltVm + PathmindBindings + LuaNodeExecutor + Smoke Test (TDD, Sibling Repo)

**CobaltVm.java** (`pathmind-lua/vm/`):
- `static void run(String, PathmindRuntime, CompletableFuture<NodeResult>)`
- Fresh `LuaState` per call via `LuaState.builder().interruptHandler(...).build()`
- `CoreLibraries.standardGlobals(state)` — excludes IoLib/OsLib/PackageLib/SystemBaseLib (LUA-03)
- `LoadState.load(state, ByteArrayInputStream, "@script", globals)` — file-style error decoration
- `LuaThread.runMain(state, closure)` — blocks worker thread
- Timeout: `ScheduledExecutorService` sets `AtomicBoolean timedOut` + calls `state.interrupt()` after 5s; `Thread.interrupt()` safety net 500ms later
- `LuaError` catch: logs to System.err, calls `runtime.sendErrorToChat`, completes FAILURE

**PathmindBindings.java**: stub `pathmind.*` table with 6 functions (`getVar/setVar/moveTo/getPosition/getInventory/getBlock`) returning nil/NONE — ensures `pathmind` global exists for plan 01.

**LuaNodeExecutor** replaced: `newCachedThreadPool` with daemon "pathmind-lua-worker" threads; null/blank script → immediate SUCCESS; otherwise creates CF, submits `CobaltVm.run(...)` to pool, returns CF.

**CobaltVmSmokeTest**: 6 tests across 4 behavioral cases:
1. `return 1 + 1` → SUCCESS (print-independent VM proof, LUA-02)
2. `error('boom')` → FAILURE + chat message contains line number (BIND-04)
3. `io.read()` → FAILURE (sandbox, LUA-03)
4. `os.exit()` → FAILURE (sandbox, LUA-03)
5. `require('io')` → FAILURE (sandbox, LUA-03)
6. `while true do end` → FAILURE within 8s budget (LUA-04 timeout)

All 6 tests pass.

---

## Verification Results

| Check | Result |
|-------|--------|
| `./gradlew shadowJar` in pathmind-lua | BUILD SUCCESSFUL |
| Jar contains `com/mrmysterium/pathmindlua/shadow/cobalt/LuaState.class` | CONFIRMED (171 relocated classes) |
| No `org/squiddev/cobalt/` paths in jar | CONFIRMED |
| `./gradlew :common:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :common:test --tests "*PathmindRuntimeImpl*"` | BUILD SUCCESSFUL (3 tests pass) |
| `./gradlew test --tests "*CobaltVmSmokeTest*"` in pathmind-lua | BUILD SUCCESSFUL (6 tests pass) |
| `ctx.setRuntime(...)` present in Node.executeAddonNode | CONFIRMED (line 3855) |

---

## Known Stubs

These stubs are intentional per plan scope — they are the wiring targets for plans 02–04:

| Stub | File | Reason |
|------|------|--------|
| `PathmindRuntimeImpl.moveTo()` | `PathmindRuntimeImpl.java` | Wired in plan 03 (PathmindNavigator.startGoto) |
| `PathmindRuntimeImpl.getPosition()` returns [0,0,0] | `PathmindRuntimeImpl.java` | Wired in plan 03/04 (client-thread dispatch) |
| `PathmindRuntimeImpl.getInventory()` returns empty | `PathmindRuntimeImpl.java` | Wired in plan 04 |
| `PathmindRuntimeImpl.getBlock()` returns null | `PathmindRuntimeImpl.java` | Wired in plan 04 |
| `PathmindBindings.getVar/setVar/moveTo/getPosition/getInventory/getBlock` | `PathmindBindings.java` | Stubs return nil; wired in plans 02–04 |

These stubs do NOT prevent the plan's goal: a Script node with `return 1 + 1` executes and returns SUCCESS, errors surface to chat, sandbox is enforced, timeout works.

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] shadowJar zip64 requirement**
- **Found during:** Task 1 verification
- **Issue:** `./gradlew shadowJar` failed with `Zip64RequiredException: archive contains more than 65535 entries`
- **Fix:** Added `isZip64 = true` to the `shadowJar` task in `build.gradle.kts`
- **Files modified:** `pathmind-lua/build.gradle.kts`
- **Commit:** Sibling repo file (not committed to Pathmind repo)

**2. [Rule 1 - Bug] Wrong package prefix in VM source files**
- **Found during:** Task 3 compilation
- **Issue:** Source imports used `com.mrmysterium.pathmindlua.shadow.cobalt` (the runtime-relocated package) but compilation needs the original `org.squiddev.cobalt` — shadow relocation happens at JAR packaging time, not at compile time
- **Fix:** Updated all imports in `CobaltVm.java` and `PathmindBindings.java` to use `org.squiddev.cobalt`
- **Files modified:** `pathmind-lua/vm/CobaltVm.java`, `pathmind-lua/vm/PathmindBindings.java`

**3. [Rule 1 - Bug] PathmindRuntime not on test classpath**
- **Found during:** Task 3 test execution
- **Issue:** `ClassNotFoundException: com.pathmind.api.addon.PathmindRuntime` during test discovery — `modCompileOnly` does not contribute to test runtime classpath
- **Fix:** Added `testImplementation("com.pathmind:pathmind-fabric:$pathmindVersion")` to `build.gradle.kts`
- **Files modified:** `pathmind-lua/build.gradle.kts`

### Cross-Repo Note

Task 1 and Task 3 changes are exclusively in the sibling repo (`pathmind-lua`). Per the cross-repo note, no Pathmind repo commits were made for these tasks — the orchestrator commits the sibling repo after executor returns.

---

## Threat Surface Scan

| Flag | File | Description |
|------|------|-------------|
| threat_flag: sandbox-enforcement | `CobaltVm.java` | CoreLibraries.standardGlobals excludes IoLib/OsLib/PackageLib/SystemBaseLib (T-02-02); verified by smoke tests |
| threat_flag: timeout-enforcement | `CobaltVm.java` | LuaState.interrupt() primary + Thread.interrupt() safety net (T-02-01); verified by infinite-loop smoke test |
| threat_flag: shadow-collision | `build.gradle.kts` | relocate("org.squiddev.cobalt", "...shadow.cobalt") applied (T-02-05); verified by jar inspection |
| threat_flag: client-thread-dispatch | `PathmindRuntimeImpl.sendErrorToChat` | MinecraftClient.execute hop implemented (T-02-03) |

All T-02-* mitigations from the threat register are implemented.

---

## TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| Task 2 RED | `7e8180c` test(02-01): add failing test... | PASSED |
| Task 2 GREEN | `1ced6da` feat(02-01): add PathmindRuntime interface... | PASSED |
| Task 3 RED | verified by compile failure (CobaltVm not found) | PASSED |
| Task 3 GREEN | pathmind-lua tests pass (6/6 CobaltVmSmokeTest) | PASSED |

---

## Self-Check: PASSED

- PathmindRuntime.java: FOUND
- PathmindRuntimeImpl.java: FOUND
- AddonNodeContext.java (getRuntime/setRuntime): FOUND
- Node.java ctx.setRuntime at line 3855: FOUND
- CobaltVm.java (sibling): FOUND
- PathmindBindings.java (sibling): FOUND
- CobaltVmSmokeTest.java (sibling): FOUND
- Commits 7e8180c, 1ced6da: VERIFIED in git log
