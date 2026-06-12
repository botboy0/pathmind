---
phase: 01-api-foundation-script-node-registration
plan: "03"
subsystem: addon-api-external-consumer
tags: [addon-api, maven-publish, pathmind-lua, sibling-repo, external-consumer, lua-script-node]
dependency_graph:
  requires:
    - plan-01 (com.pathmind.api.addon package, PathmindAddonEntrypoint, NodeTypeRegistrar, all API interfaces)
  provides:
    - com.pathmind:pathmind-fabric published to mavenLocal at 1.1.5+mc1.21.4 and 1.1.5+mc1.21.11
    - pathmind-lua sibling repo scaffolded at C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua
    - LuaAddonEntrypoint, LuaNodeExecutor, LuaNodeSerializer, LuaScriptNodeRenderer
    - docs/addon-api-getting-started.md
  affects:
    - fabric/build.gradle.kts (maven-publish plugin + publishing block)
tech_stack:
  added:
    - maven-publish (built-in Gradle plugin) — wires publishToMavenLocal for the Pathmind fabric artifact
    - Architectury Loom 1.14.473 (in pathmind-lua) — addon build toolchain matching Pathmind
    - architectury-plugin 3.4.161 (in pathmind-lua)
    - Fabric API 0.119.4+1.21.4 (in pathmind-lua)
  patterns:
    - modCompileOnly against mavenLocal for zero impl-class API consumption
    - Package-discipline check (grep for impl imports as API-08 proof)
    - Loom cache-bust dev loop (rm remapped_mods after every Pathmind rebuild)
    - No-op CompletableFuture executor pattern (Phase 1 graceful pass-through)
    - LinkedHashMap serializer with mandatory _schema_version and GSON Number cast
    - Immediate-mode per-frame renderer with ellipsis truncation
key_files:
  created:
    - fabric/build.gradle.kts (modified — maven-publish plugin + publishing block)
    - docs/addon-api-getting-started.md
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/settings.gradle.kts
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/build.gradle.kts
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/gradle.properties
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/.gitignore
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/resources/fabric.mod.json
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaAddonEntrypoint.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeExecutor.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeSerializer.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java
    - C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/docs/dev-loop.md
  modified:
    - fabric/build.gradle.kts
decisions:
  - "pathmind_version in addon gradle.properties is 1.1.5+mc1.21.4 (not 1.1.5) — the project version inherits the +mc<version> suffix from the root build.gradle.kts version expression; using bare 1.1.5 would fail artifact resolution"
  - "settings.gradle.kts for pathmind-lua includes NeoForge maven (maven.neoforged.net) in pluginManagement — required for Loom transitive deps (mcinjector, DiffPatch) not on mavenCentral"
  - "build.gradle.kts property access uses val variables extracted before tasks block to avoid 'unknown property' errors in processResources configuration"
  - "Gradle wrapper copied from Pathmind repo rather than bootstrapping via gradle wrapper command — ensures version consistency (8.x)"
  - "LuaScriptNodeRenderer uses '...' (three dots) instead of the ellipsis unicode character to avoid non-ASCII rendering issues in Minecraft"
metrics:
  duration: "~10 minutes"
  completed: "2026-06-12T22:51:15Z"
  tasks_completed: 3
  files_created: 12
  files_modified: 1
---

# Phase 01 Plan 03: External Consumer Vertical Slice Summary

**One-liner:** maven-publish wired into Pathmind fabric build; pathmind-lua sibling repo scaffolded with Loom + modCompileOnly; four Lua addon classes implement the full API contract with zero impl-class imports; addon builds and jars successfully.

## What Was Built

### Task 1: Pathmind API published to mavenLocal + getting-started guide

**`fabric/build.gradle.kts`** gained:
- `id("maven-publish")` in the plugins block
- A `publishing { publications { create<MavenPublication>("mavenFabric") { ... } } }` block at the end of the file
- groupId = `com.pathmind`, artifactId = `pathmind-fabric`, version from project (yields `1.1.5+mc<version>`)
- Inline dev-loop comment documenting `./gradlew :fabric:publishToMavenLocal -Pmc_version=<version>` and the Loom cache-bust requirement

**`docs/addon-api-getting-started.md`** (API-10):
- Covers the full addon-authoring path: entrypoint declaration, modCompileOnly dependency, executor, serializer, category, body renderer, node-id format rule, GSON Double-erasure caveat, cache-bust dev loop
- References all relevant API types in a summary table
- Sufficient for a third party to build a non-Lua addon

**Verification:** `./gradlew :fabric:publishToMavenLocal -Pmc_version=1.21.4` installs `com.pathmind:pathmind-fabric:1.1.5+mc1.21.4` to `~/.m2/repository/com/pathmind/pathmind-fabric/1.1.5+mc1.21.4/`

### Task 2: pathmind-lua sibling repo scaffolded

**Standalone Gradle/Loom project** at `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/`:
- `settings.gradle.kts`: pluginManagement with architectury, fabric, neoforged, mavenCentral, gradlePluginPortal repos + `rootProject.name = "pathmind-lua"`
- `gradle.properties`: `maven_group=com.mrmysterium`, `mod_version=0.1.0`, `minecraft_version=1.21.4`, `loader_version=0.17.3`, `pathmind_version=1.1.5+mc1.21.4`, yarn `1.21.4+build.8`, fabric-api `0.119.4+1.21.4`
- `build.gradle.kts`: Loom 1.14.473 + architectury-plugin 3.4.161; `mavenLocal()` first in repositories; `modCompileOnly("com.pathmind:pathmind-fabric:$pathmindVersion")`; Java 21 toolchain
- `.gitignore`: standard Gradle/Loom + IDE ignores
- `src/main/resources/fabric.mod.json`: id=`pathmind-lua`, entrypoints `"pathmind": ["com.mrmysterium.pathmindlua.LuaAddonEntrypoint"]`, depends includes `"pathmind": ">=0.1.0"` (D-11)
- `docs/dev-loop.md`: step-by-step publish + cache-bust loop with explanation of why Loom #1290 forces this

Git repo initialized (no initial commit — left for the developer per plan).
Gradle wrapper copied from Pathmind repo.

### Task 3: Lua Script node implemented (entrypoint, executor, serializer, renderer)

All four classes import only from `com.pathmind.api.addon.*` and `com.pathmind.api.*` (plus `net.minecraft.client.*` in the renderer). Zero impl imports.

**`LuaAddonEntrypoint`** (implements `PathmindAddonEntrypoint`):
- Declares `AddonNodeCategory("pathmind_lua.scripting", "Scripting", 0xFF7986CB, "*")` (D-05)
- Calls `registrar.register(AddonNodeDefinition.builder("pathmind_lua:script").displayName("Lua Script").category(...).color(...).provenanceLabel("Pathmind Lua").bodyRenderer(new LuaScriptNodeRenderer()).build(), new LuaNodeExecutor(), new LuaNodeSerializer())` (D-07)

**`LuaNodeExecutor`** (implements `AddonNodeExecutor`):
- Returns `CompletableFuture.completedFuture(NodeResult.SUCCESS)` (Phase 1 graceful no-op pass-through, LUA-01)
- Comment documents Phase 2 replacement with Cobalt VM

**`LuaNodeSerializer`** (implements `AddonNodeSerializer`):
- `serialize`: writes `_schema_version=1` (mandatory) and `script` field (LUA-05)
- `deserialize`: null-safe; reads script via instanceof pattern; reads schema version via `((Number) rawVersion).intValue()` — no `(Integer)` cast (Pitfall 4 compliance)

**`LuaScriptNodeRenderer`** (implements `AddonNodeBodyRenderer`):
- Renders first up to 3 lines of script text with ellipsis truncation (D-06 read-only preview)
- Returns early if script is null/blank
- Uses `MinecraftClient.getInstance().textRenderer` and `DrawContext.drawTextWithShadow`

**Build result:** `./gradlew build` exits 0 in pathmind-lua. Output: `pathmind-lua-0.1.0.jar` and `pathmind-lua-0.1.0-sources.jar`.

**Impl leak check:** `grep -rn "com.pathmind.execution|com.pathmind.ui|com.pathmind.nodes.Node|com.pathmind.data" src/main/java` → `NO IMPL LEAK` (API-08 proof).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Incorrect Maven version coordinate in plan**
- **Found during:** Task 1 verification
- **Issue:** The plan specified `pathmind_version=1.1.5` in the addon's `gradle.properties`, but the Pathmind root build appends `+mc<version>` to the project version. The published artifact is `com.pathmind:pathmind-fabric:1.1.5+mc1.21.4`, not `1.1.5`. Using `1.1.5` would fail artifact resolution.
- **Fix:** Used `pathmind_version=1.1.5+mc1.21.4` in `gradle.properties`. Updated the getting-started doc to show the correct coordinate. Updated the `fabric:publishToMavenLocal` command to include `-Pmc_version=1.21.4`.
- **Files modified:** `pathmind-lua/gradle.properties`, `docs/addon-api-getting-started.md`

**2. [Rule 3 - Blocking] settings.gradle.kts missing NeoForge maven for Loom transitive deps**
- **Found during:** Task 3 build attempt
- **Issue:** Architectury Loom 1.14.473 has transitive dependencies (`de.oceanlabs.mcp:mcinjector:3.8.0`, `net.minecraftforge:DiffPatch:2.0.7`) that are hosted on `maven.neoforged.net`, not on mavenCentral or the Architectury/Fabric mavens. The initial `settings.gradle.kts` (which matched the plan's skeleton) was missing this repo in `pluginManagement`.
- **Fix:** Added `maven { name = "NeoForge"; url = uri("https://maven.neoforged.net/releases/") }` to `pluginManagement.repositories` in `settings.gradle.kts`, matching Pathmind's own `settings.gradle.kts`.
- **Files modified:** `pathmind-lua/settings.gradle.kts`

**3. [Rule 3 - Blocking] build.gradle.kts processResources property lookup error**
- **Found during:** Task 3 build attempt (after fixing settings.gradle.kts)
- **Issue:** Using `property("minecraft_version")` inside `tasks.processResources { }` was interpreted as a task property lookup rather than the project property, causing "Could not get unknown property 'minecraft_version'".
- **Fix:** Extracted all property values into `val` variables at the top of `build.gradle.kts` (`val minecraftVersion = project.property("minecraft_version") as String` etc.) and used those variables in the dependencies block and processResources block.
- **Files modified:** `pathmind-lua/build.gradle.kts`

## Verification Results

| Check | Result |
|-------|--------|
| `./gradlew :fabric:publishToMavenLocal -Pmc_version=1.21.4` exits 0 | PASS |
| `~/.m2/repository/com/pathmind/pathmind-fabric/1.1.5+mc1.21.4/pathmind-fabric-1.1.5+mc1.21.4.jar` exists | PASS |
| `fabric/build.gradle.kts` contains `id("maven-publish")` | PASS |
| `fabric/build.gradle.kts` contains `create<MavenPublication>("mavenFabric")` with `artifactId = "pathmind-fabric"` | PASS |
| `docs/addon-api-getting-started.md` contains `modCompileOnly`, `"pathmind"` entrypoint key, node-id regex | PASS |
| `pathmind-lua/build.gradle.kts` contains `modCompileOnly` referencing `com.pathmind:pathmind-fabric` | PASS |
| `pathmind-lua/build.gradle.kts` lists `mavenLocal()` first in repositories | PASS |
| `pathmind-lua/src/main/resources/fabric.mod.json` has `"pathmind"` entrypoint at `LuaAddonEntrypoint` | PASS |
| `pathmind-lua/src/main/resources/fabric.mod.json` has `"pathmind": ">=0.1.0"` dependency (D-11) | PASS |
| `pathmind-lua/gradle.properties` has `maven_group=com.mrmysterium` and `minecraft_version=1.21.4` | PASS |
| `pathmind-lua/docs/dev-loop.md` contains `publishToMavenLocal` and `remapped_mods` | PASS |
| `LuaAddonEntrypoint.java` contains `implements PathmindAddonEntrypoint` and `registrar.register(` | PASS |
| `LuaNodeExecutor.java` contains `CompletableFuture.completedFuture(NodeResult.SUCCESS)` | PASS |
| `LuaNodeSerializer.java` contains `"_schema_version"` and writes `"script"`; no `(Integer)` cast | PASS |
| Impl-class import leak check: zero matches in pathmind-lua/src/main/java | PASS (NO IMPL LEAK) |
| `./gradlew build` in pathmind-lua exits 0; produces `pathmind-lua-0.1.0.jar` | PASS |
| Human in-game check (Parts A + B) | PENDING — requires Plan 02 completion before end-of-phase verification |

## Known Stubs

- `LuaNodeExecutor.execute()` returns `CompletableFuture.completedFuture(NodeResult.SUCCESS)` — graceful no-op pass-through. **Intentional:** Plan 02 wires the in-Pathmind execution path; Phase 2 replaces this with Cobalt VM execution. The no-op is the correct Phase 1 behavior (LUA-01 success criterion #3).
- Human in-game verification (Task 3 human-check) depends on Plan 02 completing the in-Pathmind sidebar/execution/persistence wiring. Not a stub in the addon code — a dependency on the sibling plan.

## Threat Flags

None — this plan introduces no new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries. The `modCompileOnly` artifact is first-party (Pathmind itself). Threat T-01-10 (impl class leak) is mitigated — grep confirms no impl imports. T-01-11 (stale Loom cache) is documented in dev-loop.md.

## Self-Check: PASSED

Files verified to exist:
- `fabric/build.gradle.kts` — FOUND (contains maven-publish and mavenFabric publication)
- `docs/addon-api-getting-started.md` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/build.gradle.kts` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/settings.gradle.kts` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/gradle.properties` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/.gitignore` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/resources/fabric.mod.json` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaAddonEntrypoint.java` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeExecutor.java` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeSerializer.java` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/docs/dev-loop.md` — FOUND
- `~/.m2/repository/com/pathmind/pathmind-fabric/1.1.5+mc1.21.4/pathmind-fabric-1.1.5+mc1.21.4.jar` — FOUND
- `C:/Users/Trynda/Desktop/Dev/sidequests/pathmind-lua/build/libs/pathmind-lua-0.1.0.jar` — FOUND

Commits verified:
- `a8f134d` — feat(01-03): publish Pathmind API to mavenLocal + add getting-started guide — FOUND
