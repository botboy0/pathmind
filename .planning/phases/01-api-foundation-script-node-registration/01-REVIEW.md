---
phase: 01-api-foundation-script-node-registration
reviewed: 2026-06-13T00:00:00Z
depth: standard
files_reviewed: 43
files_reviewed_list:
  - common/src/main/java/com/pathmind/api/PathmindApiVersion.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeBodyRenderer.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeCategory.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeDefinition.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeExecutor.java
  - common/src/main/java/com/pathmind/api/addon/AddonNodeSerializer.java
  - common/src/main/java/com/pathmind/api/addon/NodeResult.java
  - common/src/main/java/com/pathmind/api/addon/NodeTypeRegistrar.java
  - common/src/main/java/com/pathmind/api/addon/PathmindAddonEntrypoint.java
  - common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java
  - common/src/main/java/com/pathmind/data/NodeGraphData.java
  - common/src/main/java/com/pathmind/data/NodeGraphPersistence.java
  - common/src/main/java/com/pathmind/execution/AddonLoader.java
  - common/src/main/java/com/pathmind/execution/ExecutionManager.java
  - common/src/main/java/com/pathmind/nodes/Node.java
  - common/src/main/java/com/pathmind/nodes/NodeType.java
  - common/src/main/java/com/pathmind/nodes/NodeTypeDefinition.java
  - common/src/main/java/com/pathmind/nodes/NodeTypeRegistry.java
  - common/src/main/java/com/pathmind/screen/PathmindScreens.java
  - common/src/main/java/com/pathmind/ui/graph/NodeGraph.java
  - common/src/main/java/com/pathmind/ui/graph/NodeGraphClipboardSupport.java
  - common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java
  - common/src/main/resources/assets/pathmind/lang/en_us.json
  - common/src/test/java/com/pathmind/data/AddonNodeAliasingTest.java
  - common/src/test/java/com/pathmind/data/AddonNodeConversionRoundTripTest.java
  - common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java
  - common/src/test/java/com/pathmind/nodes/AddonNodeCreationTest.java
  - common/src/test/java/com/pathmind/nodes/NodeTypeRegistryTest.java
  - common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarScrollTest.java
  - common/src/test/java/com/pathmind/ui/sidebar/AddonSidebarTest.java
  - docs/addon-api-getting-started.md
  - fabric/build.gradle.kts
  - fabric/src/main/java/com/pathmind/PathmindMod.java
  - pathmind-lua/.gitignore
  - pathmind-lua/build.gradle.kts
  - pathmind-lua/docs/dev-loop.md
  - pathmind-lua/gradle.properties
  - pathmind-lua/settings.gradle.kts
  - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaAddonEntrypoint.java
  - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeExecutor.java
  - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeSerializer.java
  - pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java
  - pathmind-lua/src/main/resources/fabric.mod.json
findings:
  critical: 3
  warning: 5
  info: 3
  total: 11
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-06-13
**Depth:** standard
**Files Reviewed:** 43
**Status:** issues_found

## Summary

This round reviews the gap-closure wave (plans 01-07 through 01-10) covering the addon sidebar scrollbar, ADDON node display-name/default-field seeding, scissor-clipped addon body rendering, missing-addon indicator, and defensive-copy/null-skip hardening across `AddonNodeDataCopy`, `NodeGraphPersistence`, `ExecutionManager`, and `NodeGraphClipboardSupport`. The full API surface, Lua addon, and all test files are also reviewed as context.

The implementation is substantially correct and well-thought-out. The defensive copies, null-skip guards, and scissor clipping all work as described. Three correctness issues require attention: a thread-safety hole in `AddonLoader.failedAddons`, a silent state-loss bug in the successful deserialization branch of `AddonNodeDataCopy.restoreAddonFieldsToNode`, and a test-reliability problem where Java `assert` statements are used in JUnit tests instead of JUnit assertions (they are silently disabled at runtime without `-ea`).

---

## Critical Issues

### CR-01: `AddonLoader.failedAddons` is a non-thread-safe `LinkedHashMap` shared across threads

**File:** `common/src/main/java/com/pathmind/execution/AddonLoader.java:48`

**Issue:** `failedAddons` is a `static final LinkedHashMap` — a non-synchronized data structure. `markFailed` writes to it during mod initialization; `getFailedAddons` and `getFailure` read from it from the UI thread when the editor opens (D-08 UX). `markFailed` is also `public static`, enabling addon code to call it from arbitrary threads. A `LinkedHashMap` is not thread-safe: concurrent reads and writes can corrupt internal structure and cause `ConcurrentModificationException` when `getFailedAddons` iterates the unmodifiable view while another thread writes. This is a production correctness issue, not just a theoretical race.

**Fix:**
```java
// Replace LinkedHashMap with ConcurrentHashMap (loses insertion order,
// which is irrelevant for error display):
private static final Map<String, Throwable> failedAddons = new ConcurrentHashMap<>();

// Or preserve insertion order:
private static final Map<String, Throwable> failedAddons =
    Collections.synchronizedMap(new LinkedHashMap<>());
```

`getFailedAddons()` already returns an unmodifiable view, so no callers require changes.

---

### CR-02: Silent state loss in `restoreAddonFieldsToNode` — default-seeded fields discarded when `extraFields` is null

**File:** `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java:119-126`

**Issue:** In the successful deserialization branch (addon installed, no exception), the code is:

```java
ser.deserialize(ctx, nodeData.getExtraFields());        // (1) may seed ctx from null-fields path
if (nodeData.getExtraFields() != null) {               // (2) guard
    node.setAddonExtraFields(new HashMap<>(nodeData.getExtraFields()));
}
if (ctx.getScriptText() != null && node.getAddonExtraFields() != null) { // (3) both conditions
    node.getAddonExtraFields().put("script", ctx.getScriptText());
}
```

When `nodeData.getExtraFields()` is null (a freshly-placed node serialized before any user edit), step (1) still runs and the Lua serializer correctly seeds `DEFAULT_SCRIPT` into `ctx` via its null-fields path. But step (2) is false, so `node.getAddonExtraFields()` remains null. Step (3)'s second condition is then false, so the script value from `ctx` is discarded. The node ends up with `addonExtraFields == null` even though the serializer produced data.

This breaks the GAP-3 guarantee on the close-and-reopen path: after a save/reload cycle of a freshly-placed node, the script field is lost until the user opens the node and triggers a new serialization.

**Fix:** Always initialize `addonExtraFields` from the result of deserialization, regardless of whether `nodeData.getExtraFields()` was null:

```java
ser.deserialize(ctx, nodeData.getExtraFields());
// Start from the on-disk blob or empty; always non-null after this
Map<String, Object> base = nodeData.getExtraFields() != null
    ? new HashMap<>(nodeData.getExtraFields())
    : new HashMap<>();
node.setAddonExtraFields(base);
node.setAddonUnresolved(false);  // see WR-05 below
if (ctx.getScriptText() != null) {
    node.getAddonExtraFields().put("script", ctx.getScriptText());
}
```

---

### CR-03: `LuaScriptNodeRenderer.render` calls `MinecraftClient.getInstance()` with no null guard

**File:** `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java:34`

**Issue:** `MinecraftClient.getInstance()` can return `null` in headless or server-side contexts. Even with `"environment": "client"` in `fabric.mod.json`, deserialization code (triggered by node construction) may be exercised in CI test contexts or early in the loading pipeline before the client is initialized. When `getInstance()` returns null, accessing `.textRenderer` on line 34 throws `NullPointerException`. The `catch (Throwable t)` in `NodeGraph.renderAddonNodeContent` prevents a game crash, but it logs a warning every frame (see WR-04) and permanently shows the placeholder body instead of the renderer output for as long as the error persists.

**Fix:**
```java
@Override
public void render(AddonNodeContext ctx, DrawContext draw, int x, int y, int width, int height) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client == null || client.textRenderer == null) {
        return;
    }
    var textRenderer = client.textRenderer;
    // ... rest unchanged
}
```

---

## Warnings

### WR-01: Java `assert` statements used inside JUnit test methods — silently pass without `-ea`

**File:** `common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java:56,179-182,221-222`

**Issue:** Multiple critical correctness checks in JUnit tests use Java's `assert` keyword rather than JUnit assertions. Java `assert` statements are disabled by default (unless the JVM is launched with `-ea`, which Gradle test runners do not do). Lines 179-182 verify the serialized JSON contains `"addonTypeId"`, `"_schema_version"`, `"extraFields"`, and the addon ID — if these assertions fail, the test passes silently. Lines 221-222 verify built-in nodes do NOT contain addon fields — also silently pass if the check is false. Line 56 inside the test serializer checks `version >= 1`.

**Fix:** Replace all `assert expr : "msg"` with JUnit assertions:

```java
// Replace:
assert json.contains("addonTypeId") : "JSON must contain addonTypeId";
assert json.contains(TEST_ADDON_ID) : "JSON must contain the addon type id";
assert json.contains("_schema_version") : "JSON must contain _schema_version";
assert json.contains("extraFields") : "JSON must contain extraFields";

// With:
assertTrue(json.contains("addonTypeId"), "JSON must contain addonTypeId");
assertTrue(json.contains(TEST_ADDON_ID), "JSON must contain the addon type id");
assertTrue(json.contains("_schema_version"), "JSON must contain _schema_version");
assertTrue(json.contains("extraFields"), "JSON must contain extraFields");

// Replace:
assert !json.contains("addonTypeId") : "Built-in node JSON must not contain addonTypeId";
// With:
assertFalse(json.contains("addonTypeId"), "Built-in node JSON must not contain addonTypeId");
```

---

### WR-02: `AddonNodeDefinition.Builder.build()` throws `NullPointerException` for blank values — wrong exception type

**File:** `common/src/main/java/com/pathmind/api/addon/AddonNodeDefinition.java:183-188`

**Issue:** When `id` or `displayName` is blank (not null), `build()` throws `new NullPointerException("id is required")`. The value is not null — it's blank. `NullPointerException` is semantically incorrect for a blank-but-non-null argument; `IllegalArgumentException` is the correct type and what addon developers will expect when validating their registration code. A non-null blank string throwing an NPE is actively misleading.

**Fix:**
```java
if (id == null || id.isBlank()) {
    throw new IllegalArgumentException("id is required and must not be blank");
}
if (displayName == null || displayName.isBlank()) {
    throw new IllegalArgumentException("displayName is required and must not be blank");
}
```

---

### WR-03: `Sidebar.calculateMaxScroll` adds a hardcoded magic `+100` that diverges from `computeAddonContentHeight`

**File:** `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java:586`

**Issue:** `maxScroll = Math.max(0, totalHeight - sidebarHeight + 100)` appends a magic 100-pixel over-scroll buffer. This value is not a named constant and is not justified by any geometry. More importantly, `computeAddonContentHeight` (the pure helper tested in `AddonSidebarScrollTest`) does NOT include this padding, so the scroll range computed in `calculateMaxScroll` for addon content (via `addonRowLineCounts`) is 100px wider than what `computeAddonContentHeight` would compute for the same content. This discrepancy means the scrollbar knob position computed from `maxScroll` does not correspond to the actual content height returned by the pure helper, creating a systematic off-by-100 in scroll indicator accuracy when content fits within the sidebar.

**Fix:** Use a named constant and ensure consistency with `computeAddonContentHeight`:
```java
private static final int SCROLL_BOTTOM_BUFFER = 0; // or PADDING*2 if intentional
// ...
maxScroll = Math.max(0, totalHeight - sidebarHeight + SCROLL_BOTTOM_BUFFER);
```

---

### WR-04: `NodeGraph.renderAddonNodeContent` warns unconditionally per frame on renderer failure — no rate limiting

**File:** `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java:7425-7427`

**Issue:** The catch block for a failing body renderer logs at `warn` level without rate limiting. At 60 fps with a visible ADDON node whose renderer is broken, this emits 3,600+ warn-level log lines per minute. This floods `latest.log` and degrades game performance in logging configurations that write synchronously to disk.

**Fix:** Add a per-type warn-once guard using a class-level set:
```java
// Field:
private final Set<String> addonRendererWarnedIds = new HashSet<>();

// In catch block:
if (addonRendererWarnedIds.add(addonTypeId)) {
    LOGGER.warn("[Pathmind] Addon body renderer threw for {} (further warnings suppressed): {}",
        addonTypeId, t.getMessage());
}
```

---

### WR-05: `restoreAddonFieldsToNode` never clears `addonUnresolved` in the successful-deserialization branch

**File:** `common/src/main/java/com/pathmind/data/AddonNodeDataCopy.java:112-133`

**Issue:** The successful-deserialization branch (addon installed, no exception) never calls `node.setAddonUnresolved(false)`. If a node object is created, marked unresolved (e.g., missing addon placeholder), and later the same node undergoes a restore (e.g., the addon becomes available and the graph is reloaded), `addonUnresolved` remains `true`. `NodeGraph.renderAddonNodeContent` checks `node.isAddonUnresolved()` at line 7399 and will perpetually show the placeholder body instead of the live renderer.

The only path that clears `addonUnresolved` back to false is `new Node(addonTypeId, x, y)` construction (fresh placement). The restore path used by all save/load, clipboard, and snapshot paths never resets it, meaning any node that was ever loaded while its addon was absent will permanently display as missing even after the addon is later installed.

**Fix:** Add `node.setAddonUnresolved(false)` in the successful-deserialization branch (before or after the script-key injection, but inside the try block):
```java
try {
    ser.deserialize(ctx, nodeData.getExtraFields());
    node.setAddonUnresolved(false);  // <-- add this
    // ... rest of success path
}
```

---

## Info

### IN-01: `AddonNodeContext` setters are public but contract says "read-only during rendering"

**File:** `common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java:37-62`

**Issue:** The Javadoc on `AddonNodeBodyRenderer` says the context is "read-only during rendering", but `AddonNodeContext` exposes public `setAddonTypeId` and `setScriptText` setters with no visibility restriction or Javadoc warning. Addon renderer implementations can call `ctx.setAddonTypeId("injected")` or `ctx.setScriptText(null)` during `render()`. Since the context object is freshly constructed per render call, this cannot corrupt shared state today, but the contract is misleading for future readers and could become a real issue if the context object is ever cached or reused.

**Fix:** Add Javadoc to the setters: "For Pathmind internal use during context construction — addon implementations must not call this setter." No code change is required unless the API contract is to be enforced at the type level (making the setter package-private or removing it).

---

### IN-02: `AddonNodeCreationTest` uses fragile reflection to bypass install-once registry guard

**File:** `common/src/test/java/com/pathmind/nodes/AddonNodeCreationTest.java:84-118`

**Issue:** `installSyntheticTypeViaReflection` directly accesses the private `definitions`, `executors`, and `serializers` fields of `NodeTypeRegistry.INSTANCE` via `getDeclaredField`. This works today but breaks silently if the field names change or if the maps are wrapped in an unmodifiable view. The comment acknowledges this as intentional, but the approach is more brittle than necessary.

**Fix:** A package-private `@VisibleForTesting` method on `NodeTypeRegistry` that bypasses the `installed` guard (marked clearly as test-only) would be less fragile. Alternatively, since the three sibling test classes all have the same `@BeforeAll` install-once pattern, structuring the tests to use fresh `NodeTypeRegistry` instances (as `NodeTypeRegistryTest` correctly does) would entirely eliminate the problem.

---

### IN-03: `LuaScriptNodeRenderer.LINE_HEIGHT` constant is decoupled from actual `textRenderer.fontHeight`

**File:** `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java:25`

**Issue:** `LINE_HEIGHT = 10` hard-codes a value that is derived from `textRenderer.fontHeight` (typically 9) plus 1px spacing. Resource packs that alter the default font height will cause line-to-line overlap or excess gaps in the Lua script preview. The `Sidebar` class computes the analogous value dynamically as `textRenderer.fontHeight + NODE_LINE_SPACING`.

**Fix:**
```java
// Remove LINE_HEIGHT constant. Inside render():
int lineHeight = textRenderer.fontHeight + 1;
for (int i = 0; i < lineCount; i++) {
    // ...
    draw.drawTextWithShadow(textRenderer, line, x, y + i * lineHeight, TEXT_COLOR);
}
```

---

_Reviewed: 2026-06-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
