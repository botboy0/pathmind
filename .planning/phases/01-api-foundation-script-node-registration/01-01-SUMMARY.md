---
phase: 01-api-foundation-script-node-registration
plan: "01"
subsystem: addon-api
tags: [addon-api, fabric-entrypoint, registry, tdd, security]
dependency_graph:
  requires: []
  provides:
    - com.pathmind.api.addon (PathmindAddonEntrypoint, NodeTypeRegistrar, AddonNodeDefinition,
        AddonNodeCategory, AddonNodeExecutor, AddonNodeSerializer, AddonNodeContext,
        AddonNodeBodyRenderer, NodeResult)
    - com.pathmind.api.PathmindApiVersion
    - com.pathmind.nodes.NodeTypeRegistry
    - com.pathmind.execution.AddonLoader
  affects:
    - fabric/src/main/java/com/pathmind/PathmindMod.java (AddonLoader.discoverAndLoad call)
tech_stack:
  added: []
  patterns:
    - Registrar-passed-to-plugin pattern (JEI/REI style) for addon registration
    - Sealed registrar write guard (ASVS V4) for post-init isolation
    - Per-container catch(Throwable) for addon failure isolation (D-08)
    - D-11 runtime API-version check via FabricLoader ModDependency.matches()
    - TDD RED/GREEN cycle for NodeTypeRegistryTest
key_files:
  created:
    - common/src/main/java/com/pathmind/api/addon/PathmindAddonEntrypoint.java
    - common/src/main/java/com/pathmind/api/addon/NodeTypeRegistrar.java
    - common/src/main/java/com/pathmind/api/addon/AddonNodeDefinition.java
    - common/src/main/java/com/pathmind/api/addon/AddonNodeCategory.java
    - common/src/main/java/com/pathmind/api/addon/AddonNodeExecutor.java
    - common/src/main/java/com/pathmind/api/addon/AddonNodeSerializer.java
    - common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java
    - common/src/main/java/com/pathmind/api/addon/AddonNodeBodyRenderer.java
    - common/src/main/java/com/pathmind/api/addon/NodeResult.java
    - common/src/main/java/com/pathmind/api/PathmindApiVersion.java
    - common/src/main/java/com/pathmind/nodes/NodeTypeRegistry.java
    - common/src/main/java/com/pathmind/execution/AddonLoader.java
    - common/src/test/java/com/pathmind/nodes/NodeTypeRegistryTest.java
  modified:
    - fabric/src/main/java/com/pathmind/PathmindMod.java
decisions:
  - "seal() made public (idempotent; called by AddonLoader and NodeTypeRegistry.install for defense-in-depth)"
  - "NodeTypeRegistry.install() also seals the registrar before reading it"
  - "AddonLoader.isApiCompatible() extracted as package-private helper for testability without FabricLoader containers"
  - "Build and test run from worktree path (not main repo) — worktree has independent file system"
metrics:
  duration: "~35 minutes"
  completed: "2026-06-12T22:33:00Z"
  tasks_completed: 2
  files_created: 13
  files_modified: 1
---

# Phase 01 Plan 01: Addon API Foundation and Node Type Registry Summary

**One-liner:** Fabric entrypoint-based addon discovery with registrar-passed-to-plugin pattern, sealed post-init, D-11 runtime API-version check, and per-addon failure isolation.

## What Was Built

Plan 01 establishes the complete addon API contract and runtime discovery backbone:

**API contract package (`com.pathmind.api.addon`):**
- `PathmindAddonEntrypoint` — `@FunctionalInterface` contract addon mods implement; declare under `"pathmind"` entrypoint key in `fabric.mod.json`
- `NodeTypeRegistrar` — sealed collector enforcing id-format validation (`^[a-z0-9_-]+:[a-z0-9_/.-]+$`), null checks, duplicate-id rejection, and post-seal write guard (ASVS V4/V5, T-01-01 through T-01-05)
- `AddonNodeDefinition` + nested `Builder` — immutable POJO with id, displayName, category, color, provenanceLabel (D-07), bodyRenderer
- `AddonNodeCategory` — runtime POJO (not enum) for sidebar categories (D-05, Pitfall 3)
- `AddonNodeExecutor` — `@FunctionalInterface CompletableFuture<NodeResult>` contract (API-06, must not block game thread)
- `AddonNodeSerializer` — persist/restore interface with documented GSON Double-erasure pitfall and `_schema_version` requirement (API-05)
- `AddonNodeContext` — narrow runtime context with only `addonTypeId` and `scriptText`; no `Node`, `ExecutionManager`, or `NodeGraph` leakage
- `AddonNodeBodyRenderer` — `@FunctionalInterface` render hook; sole API type importing `DrawContext` (API-07)
- `NodeResult` — `SUCCESS`, `FAILURE`, `SKIPPED` enum

**API version (`com.pathmind.api`):**
- `PathmindApiVersion` — `VERSION = "0.1.0"` and `MIN_COMPATIBLE = "0.1.0"` (D-10 independent semver)

**Registry and loader (`com.pathmind.nodes`, `com.pathmind.execution`):**
- `NodeTypeRegistry` — public singleton `INSTANCE`; `install()` seals the registrar then copies definitions/executors/serializers; double-install guard; `hasType`, `definitionFor`, `executorFor`, `serializerFor`, `allDefinitions()`
- `AddonLoader` — discovers `"pathmind"` Fabric entrypoints via `FabricLoader.getEntrypointContainers`; runs D-11 runtime API-version check via `ModDependency.matches(SemanticVersion.parse(MIN_COMPATIBLE))`; per-container `catch (Throwable)` isolation; `markFailed`, `getFailure`, `getFailedAddons` for D-08 failure UX; standalone path (empty list) installs cleanly (API-09)

**PathmindMod wiring:**
- `AddonLoader.discoverAndLoad()` inserted before the final success log in `onInitialize`, after all Pathmind internal state is ready (Pattern 5: deferred-registration guard)

**Test coverage (`NodeTypeRegistryTest`):**
- 10 test methods covering: round-trip retrieval, duplicate-id rejection, null executor/definition/serializer rejection, post-seal registration rejection, malformed id rejection (path traversal style), uppercase id rejection, double-install guard, empty-registrar clean install, unmodifiable allDefinitions

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Critical functionality] seal() made public**
- **Found during:** Task 2 implementation
- **Issue:** Plan specified `seal()` as package-private in `com.pathmind.api.addon`, but `AddonLoader` is in `com.pathmind.execution` — a different package. Package-private access would prevent AddonLoader from sealing the registrar, breaking API-04.
- **Fix:** Made `seal()` public with documentation stating it is idempotent and safe to call multiple times. Added defense-in-depth call in `NodeTypeRegistry.install()` before reading maps, so the seal guarantee holds even if AddonLoader forgets to call it.
- **Files modified:** `NodeTypeRegistrar.java`, `NodeTypeRegistry.java`
- **Commit:** `519649c`

**2. [Rule 3 - Blocking] Gradle invocation must run from worktree path**
- **Found during:** Task 2 verification
- **Issue:** The plan's verify commands specify `cd C:/Users/Trynda/Desktop/Dev/sidequests/pathmind` (main repo). The worktree is at `.claude/worktrees/agent-acad1bcd02dcadf8e/` — a separate file system hierarchy. Main repo Gradle does not see worktree files.
- **Fix:** All Gradle commands run from the worktree root. Test reports confirmed in worktree's `common/build/reports/`.
- **Commit:** N/A (execution fix, not a code change)

## TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| RED (test commit) | `d6c2e9b` — `test(01-01): add failing NodeTypeRegistryTest` | PASS |
| GREEN (impl commit) | `519649c` — `feat(01-01): add NodeTypeRegistry, AddonLoader, wire PathmindMod` | PASS |
| REFACTOR | Not needed — code was clean at GREEN | N/A |

## Verification Results

| Check | Result |
|-------|--------|
| `./gradlew :common:compileJava` (from worktree) | PASS |
| `./gradlew :common:test --tests NodeTypeRegistryTest` (from worktree) | PASS (10/10 methods) |
| `./gradlew :common:test` full suite (from worktree) | PASS (26 test classes, no regressions) |
| AddonNodeContext contains no impl class imports | PASS |
| AddonNodeBodyRenderer contains DrawContext import | PASS |
| NodeTypeRegistrar regex literal present | PASS |
| PathmindMod.discoverAndLoad() before success log | PASS (line 31 < line 33) |
| AddonLoader contains MIN_COMPATIBLE + getDependencies | PASS |
| AddonLoader contains catch(Throwable) with markFailed | PASS |

## Known Stubs

None — all implementations are complete for their Phase 1 contracts. `AddonNodeBodyRenderer` references `DrawContext` which requires Minecraft at runtime (not at compile time for tests); this is expected and documented.

## Self-Check: PASSED

Files verified to exist:
- `common/src/main/java/com/pathmind/api/addon/PathmindAddonEntrypoint.java` — FOUND
- `common/src/main/java/com/pathmind/api/addon/NodeTypeRegistrar.java` — FOUND
- `common/src/main/java/com/pathmind/api/addon/AddonNodeDefinition.java` — FOUND
- `common/src/main/java/com/pathmind/api/addon/AddonNodeCategory.java` — FOUND
- `common/src/main/java/com/pathmind/api/addon/AddonNodeExecutor.java` — FOUND
- `common/src/main/java/com/pathmind/api/addon/AddonNodeSerializer.java` — FOUND
- `common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java` — FOUND
- `common/src/main/java/com/pathmind/api/addon/AddonNodeBodyRenderer.java` — FOUND
- `common/src/main/java/com/pathmind/api/addon/NodeResult.java` — FOUND
- `common/src/main/java/com/pathmind/api/PathmindApiVersion.java` — FOUND
- `common/src/main/java/com/pathmind/nodes/NodeTypeRegistry.java` — FOUND
- `common/src/main/java/com/pathmind/execution/AddonLoader.java` — FOUND
- `common/src/test/java/com/pathmind/nodes/NodeTypeRegistryTest.java` — FOUND

Commits verified:
- `46314e3` — feat(01-01): create com.pathmind.api.addon package — FOUND
- `d6c2e9b` — test(01-01): add failing NodeTypeRegistryTest (TDD RED phase) — FOUND
- `519649c` — feat(01-01): add NodeTypeRegistry, AddonLoader, wire PathmindMod — FOUND
