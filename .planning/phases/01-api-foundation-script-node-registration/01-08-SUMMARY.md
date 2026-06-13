---
phase: 01-api-foundation-script-node-registration
plan: "08"
subsystem: nodes
tags: [gap-closure, addon-api, node-display-name, default-fields, tdd]
dependency_graph:
  requires: []
  provides: [addon-node-display-name-resolution, addon-node-default-field-seeding]
  affects: [Node.java, AddonNodeCreationTest.java]
tech_stack:
  added: []
  patterns: [registry-lookup-in-display-name, constructor-seeding-via-serializer]
key_files:
  created:
    - common/src/test/java/com/pathmind/nodes/AddonNodeCreationTest.java
  modified:
    - common/src/main/java/com/pathmind/nodes/Node.java
decisions:
  - "Use reflection-based registry injection in test to avoid NodeTypeRegistry install-once constraint crossing test class boundaries"
  - "Match AddonNodeDataCopy.restoreAddonFieldsToNode pattern exactly in constructor seeding (try/catch Throwable, null on failure)"
  - "Unique test addon type ID (test_mod:creation_test_type) to avoid serializer conflicts with sibling test classes"
metrics:
  duration: "~15 minutes"
  completed: "2026-06-13T01:47:00Z"
  tasks_completed: 1
  files_changed: 2
---

# Phase 01 Plan 08: ADDON Node Display Name and Default Field Seeding Summary

**One-liner:** Registry-aware `getDisplayName()` ADDON branch plus constructor-time `addonExtraFields` seeding via null-fields serializer path, closing GAP-2 and GAP-3.

## What Was Built

### GAP-2: ADDON Node Display Name Resolution

`Node.getDisplayName()` now contains an ADDON-type branch: when `type == NodeType.ADDON && addonTypeId != null`, it looks up `NodeTypeRegistry.INSTANCE.definitionFor(addonTypeId)`. If a definition is found, it returns `Text.literal(def.getDisplayName())` — the addon-registered display name (e.g. "Lua Script"). If the addonTypeId is null or unregistered, it falls through to `Text.literal(type.getDisplayName())` (the generic "Addon Node" label), so no NullPointerException is possible.

### GAP-3: Default addonExtraFields Seeding at Construction

`Node(String addonTypeId, int x, int y)` now seeds `addonExtraFields` at construction time. After setting `this.addonTypeId = addonTypeId`, it checks `NodeTypeRegistry.INSTANCE.hasType(addonTypeId)`, fetches `serializerFor(addonTypeId)`, builds an `AddonNodeContext`, calls `ser.deserialize(ctx, null)` (the null-fields path returns serializer defaults), then stores `ctx.getScriptText()` into a new `HashMap` as `addonExtraFields["script"]`. The deserialize call is wrapped in `try/catch(Throwable)` — a throwing serializer leaves `addonExtraFields` null rather than propagating the error, exactly mirroring `AddonNodeDataCopy.restoreAddonFieldsToNode`.

### Imports Added

`com.pathmind.api.addon.AddonNodeDefinition` and `com.pathmind.api.addon.AddonNodeSerializer` added to Node.java imports (these were used but not imported).

### Unit Coverage (AddonNodeCreationTest)

Four `@Test` methods covering:
- (a) Registered addon node returns the definition's display name, not "Addon Node"
- (b) Null/unregistered addonTypeId falls back to `NodeType.ADDON.getDisplayName()` without throwing
- (c) Constructor seeds non-null `addonExtraFields` with `"script"` key at creation
- (d) Non-ADDON node `getDisplayName()` is unchanged (START, MESSAGE, GOTO all tested)

## TDD Gate Compliance

- **RED commit** `d060721`: `AddonNodeCreationTest.java` added; tests (a) and (c) fail, (b) and (d) pass — correct RED state for the two behaviors not yet implemented.
- **GREEN commit** `d27fae1`: `Node.java` updated with ADDON display-name branch and constructor seeding; all 4 tests pass; full `:common:test` suite passes (219 tests, 0 failures).
- No REFACTOR needed — implementation is clean as written.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] NodeTypeRegistry test isolation — reflection-based injection**

- **Found during:** Task 1 (GREEN phase, full suite run)
- **Issue:** `NodeTypeRegistry.INSTANCE` is a JVM-wide install-once singleton. When sibling test classes (`AddonNodePersistenceTest`, `AddonNodeConversionRoundTripTest`) run before `AddonNodeCreationTest`, the `INSTANCE.install()` slot is exhausted. The `@BeforeAll` install-once guard catches `IllegalStateException` silently — so `test_mod:creation_test_type` never gets registered. Tests (a) and (c) fail because `definitionFor()` returns null.
- **Fix:** Changed `@BeforeAll` to use reflection to inject directly into `NodeTypeRegistry.INSTANCE`'s private `definitions`, `executors`, `serializers` maps, bypassing the install-once guard. This is test-only code with no production impact. Used a unique addon type ID `test_mod:creation_test_type` to avoid serializer conflicts with sibling test classes.
- **Files modified:** `AddonNodeCreationTest.java`
- **Commits:** `d27fae1` (combined with GREEN implementation)

## Commits

| Commit | Type | Description |
|--------|------|-------------|
| `d060721` | test | Add failing tests for GAP-2 display-name and GAP-3 default-field seeding (RED) |
| `d27fae1` | feat | Close GAP-2/GAP-3 — ADDON display name and default-field seeding at construction (GREEN) |

## Acceptance Criteria Verification

| Criterion | Status |
|-----------|--------|
| `Node.getDisplayName()` contains ADDON branch referencing `NodeType.ADDON` and `definitionFor` | PASS — line 718-726 |
| Addon constructor calls `serializerFor` and `deserialize` | PASS — lines 400-405 |
| AddonNodeCreationTest has >= 4 `@Test` methods | PASS — exactly 4 |
| `:common:test --tests AddonNodeCreationTest` exits 0 | PASS |
| Full `:common:test` suite still passes (no regressions) | PASS — 219 tests, 0 failures |

## Known Stubs

None. The implementation is complete — no placeholder values or hardcoded stubs.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The serializer invocation at construction was analyzed in the plan's threat model (T-01-08-01): the `try/catch(Throwable)` wrapper ensures a misbehaving third-party serializer cannot crash node construction.

## Self-Check: PASSED

| Item | Result |
|------|--------|
| `Node.java` exists | FOUND |
| `AddonNodeCreationTest.java` exists | FOUND |
| `01-08-SUMMARY.md` exists | FOUND |
| Commit `d060721` (RED) in git log | FOUND |
| Commit `d27fae1` (GREEN) in git log | FOUND |
