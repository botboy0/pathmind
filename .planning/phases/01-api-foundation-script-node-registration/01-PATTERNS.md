# Phase 1: API Foundation + Script Node Registration - Pattern Map

**Mapped:** 2026-06-12
**Files analyzed:** 17 (10 new, 7 modified)
**Analogs found:** 17 / 17

---

## File Classification

| New/Modified File | Repo | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|------|-----------|----------------|---------------|
| `common/src/main/java/com/pathmind/api/addon/PathmindAddonEntrypoint.java` | pathmind | interface/contract | request-response | `fabric/src/main/java/com/pathmind/PathmindMod.java` (ModInitializer) | role-match |
| `common/src/main/java/com/pathmind/api/addon/NodeTypeRegistrar.java` | pathmind | registry/collector | request-response | `common/src/main/java/com/pathmind/nodes/NodeBehaviorDefinitionRegistry.java` | role-match |
| `common/src/main/java/com/pathmind/api/addon/AddonNodeDefinition.java` | pathmind | model/POJO | — | `common/src/main/java/com/pathmind/nodes/NodeCategory.java` | role-match |
| `common/src/main/java/com/pathmind/api/addon/AddonNodeCategory.java` | pathmind | model/POJO | — | `common/src/main/java/com/pathmind/nodes/NodeCategory.java` | exact |
| `common/src/main/java/com/pathmind/api/addon/AddonNodeExecutor.java` | pathmind | interface/contract | async | `common/src/main/java/com/pathmind/nodes/NodeBehaviorDefinitionRegistry.java` | role-match |
| `common/src/main/java/com/pathmind/api/addon/AddonNodeSerializer.java` | pathmind | interface/contract | transform | `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` | role-match |
| `common/src/main/java/com/pathmind/api/addon/AddonNodeContext.java` | pathmind | model/POJO | request-response | `common/src/main/java/com/pathmind/data/NodeGraphData.NodeData` | role-match |
| `common/src/main/java/com/pathmind/api/addon/NodeResult.java` | pathmind | enum | — | `common/src/main/java/com/pathmind/nodes/NodeType.java` | role-match |
| `common/src/main/java/com/pathmind/api/PathmindApiVersion.java` | pathmind | config/constant | — | `fabric/src/main/java/com/pathmind/PathmindMod.java` (MOD_ID constant) | role-match |
| `common/src/main/java/com/pathmind/nodes/NodeTypeRegistry.java` | pathmind | registry/singleton | CRUD | `common/src/main/java/com/pathmind/nodes/NodeBehaviorDefinitionRegistry.java` | exact |
| `common/src/main/java/com/pathmind/execution/AddonLoader.java` | pathmind | service | request-response | `fabric/src/main/java/com/pathmind/PathmindMod.java` | role-match |
| `common/src/main/java/com/pathmind/nodes/NodeType.java` | pathmind | enum (MODIFIED) | — | itself | exact |
| `common/src/main/java/com/pathmind/data/NodeGraphData.java` | pathmind | model (MODIFIED) | CRUD | itself | exact |
| `common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java` | pathmind | component (MODIFIED) | request-response | itself | exact |
| `fabric/src/main/java/com/pathmind/PathmindMod.java` | pathmind | entrypoint (MODIFIED) | request-response | itself | exact |
| `fabric/build.gradle.kts` | pathmind | config (MODIFIED) | — | itself | exact |
| `fabric/src/main/resources/fabric.mod.json` | pathmind | config (MODIFIED) | — | itself | exact |
| `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaAddonEntrypoint.java` | pathmind-lua | entrypoint | request-response | `fabric/src/main/java/com/pathmind/PathmindMod.java` | role-match |
| `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeExecutor.java` | pathmind-lua | service | async | `common/src/main/java/com/pathmind/execution/AddonLoader.java` (new) | role-match |
| `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaNodeSerializer.java` | pathmind-lua | service | transform | `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` | role-match |
| `pathmind-lua/src/main/java/com/mrmysterium/pathmindlua/LuaScriptNodeRenderer.java` | pathmind-lua | component | request-response | `common/src/main/java/com/pathmind/ui/overlay/NodeErrorNotificationOverlay.java` | role-match |
| `pathmind-lua/fabric.mod.json` | pathmind-lua | config | — | `fabric/src/main/resources/fabric.mod.json` | exact |
| `pathmind-lua/build.gradle.kts` | pathmind-lua | config | — | `fabric/build.gradle.kts` | exact |
| `common/src/test/java/com/pathmind/nodes/NodeTypeRegistryTest.java` | pathmind | test | — | `common/src/test/java/com/pathmind/nodes/NodeBehaviorDefinitionRegistryTest.java` | exact |

---

## Pattern Assignments

### `PathmindAddonEntrypoint.java` (new interface)

**Analog:** `net.fabricmc.api.ModInitializer` (via usage in `PathmindMod.java`)

**Imports pattern** — match the existing Fabric interface import style:
```java
package com.pathmind.api.addon;

// No Minecraft imports in API surface — keep version-agnostic
```

**Core interface pattern** — mirror how Fabric's `ModInitializer` is a single-method interface; the registrar is passed in (REI/JEI pattern):
```java
/**
 * Implement this interface and declare it under the {@code "pathmind"} entrypoint key
 * in {@code fabric.mod.json} to register custom node types with Pathmind.
 */
@FunctionalInterface
public interface PathmindAddonEntrypoint {
    /**
     * Called by Pathmind during mod initialization. Register all custom node types
     * through the provided registrar. Throwing from this method disables the entire addon.
     *
     * @param registrar the mutable collector; sealed after all entrypoints have run
     */
    void registerNodes(NodeTypeRegistrar registrar);
}
```

---

### `NodeTypeRegistrar.java` (new sealed collector)

**Analog:** `common/src/main/java/com/pathmind/nodes/NodeBehaviorDefinitionRegistry.java`

**Core pattern from analog** (lines 1–18 of NodeBehaviorDefinitionRegistry.java) — the registry holds an `EnumMap` built in a static initializer; `NodeTypeRegistrar` uses the same data-structure idiom but with a `LinkedHashMap<String, ...>` and a write-guard:
```java
// Analog: static initializer + map construction pattern
static {
    for (NodeType type : NodeType.values()) {
        NodeBehaviorDefinition definition = directDefinition(type).orElseGet(() -> composedDefinition(type));
        if (definition.hasAnyBehavior()) {
            DEFINITIONS.put(type, definition);
        }
    }
}
```

**Core pattern to copy for NodeTypeRegistrar:**
```java
package com.pathmind.api.addon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NodeTypeRegistrar {
    private final Map<String, AddonNodeDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AddonNodeExecutor> executors = new LinkedHashMap<>();
    private final Map<String, AddonNodeSerializer> serializers = new LinkedHashMap<>();
    private boolean sealed = false;

    /**
     * Register a custom node type. All three arguments must be non-null.
     * The {@code def.getId()} must match the pattern {@code ^[a-z0-9_-]+:[a-z0-9_/.-]+$}.
     *
     * @throws IllegalStateException if the registrar is already sealed
     * @throws IllegalArgumentException if the ID is duplicate or malformed
     * @throws NullPointerException if any argument is null
     */
    public void register(AddonNodeDefinition def, AddonNodeExecutor exec, AddonNodeSerializer ser) {
        if (sealed) throw new IllegalStateException(
            "NodeTypeRegistrar is sealed — registration phase has ended");
        if (def == null || exec == null || ser == null) throw new NullPointerException(
            "All three registration arguments must be non-null");
        if (!def.getId().matches("^[a-z0-9_-]+:[a-z0-9_/.-]+$")) throw new IllegalArgumentException(
            "Addon node type ID must match 'modid:name' format (lowercase, no path traversal): " + def.getId());
        if (definitions.containsKey(def.getId())) throw new IllegalArgumentException(
            "Duplicate addon node type ID: " + def.getId());
        definitions.put(def.getId(), def);
        executors.put(def.getId(), exec);
        serializers.put(def.getId(), ser);
    }

    // Package-private: only AddonLoader calls this.
    void seal() { this.sealed = true; }

    Map<String, AddonNodeDefinition> getDefinitions() { return Collections.unmodifiableMap(definitions); }
    Map<String, AddonNodeExecutor> getExecutors() { return Collections.unmodifiableMap(executors); }
    Map<String, AddonNodeSerializer> getSerializers() { return Collections.unmodifiableMap(serializers); }
}
```

---

### `AddonNodeCategory.java` (new POJO)

**Analog:** `common/src/main/java/com/pathmind/nodes/NodeCategory.java` (lines 1–46)

**Core pattern to copy** — NodeCategory's field layout and accessor style; AddonNodeCategory replaces the enum with a POJO (same fields, same accessor names):
```java
// NodeCategory enum fields (lines 19–23) — copy this field set:
private final String translationKey;  // → replace with plain displayName String
private final int color;
private final String descriptionKey;  // → replace with plain description String
private final String icon;

// NodeCategory accessors (lines 31–45) — copy accessor naming:
public String getDisplayName() { ... }
public int getColor() { return color; }
public String getDescription() { ... }
public String getIcon() { return icon; }
```

**AddonNodeCategory constructor pattern:**
```java
package com.pathmind.api.addon;

/**
 * Runtime node category declared by an addon. Not an enum — instances are created
 * by the addon and registered through {@link NodeTypeRegistrar}.
 */
public final class AddonNodeCategory {
    private final String id;          // "pathmind_lua.scripting"
    private final String displayName; // "Scripting"
    private final int color;          // 0xFF7986CB
    private final String icon;        // "✦"

    public AddonNodeCategory(String id, String displayName, int color, String icon) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getColor() { return color; }
    public String getIcon() { return icon; }
}
```

---

### `AddonNodeDefinition.java` (new POJO)

**Analog:** `common/src/main/java/com/pathmind/nodes/NodeCategory.java` (field/accessor style) + `common/src/main/java/com/pathmind/data/NodeGraphData.NodeData` (POJO with builder pattern)

**Builder pattern** — use a static builder to keep construction readable (matches `NodeBehaviorDefinition.builder(type)` idiom seen in NodeBehaviorDefinitionRegistry line 28):
```java
// Analog: builder usage from NodeBehaviorDefinitionRegistry.java lines 27–31:
return NodeBehaviorDefinition.builder(type)
    .comparableBehavior(NodeComparableBehaviorRegistry.get(type))
    .build();
```

**Core pattern:**
```java
package com.pathmind.api.addon;

public final class AddonNodeDefinition {
    private final String id;              // "pathmind_lua:script"
    private final String displayName;
    private final AddonNodeCategory category;
    private final int color;
    private final String provenanceLabel; // shown as provenance badge (D-07)
    private final AddonNodeBodyRenderer bodyRenderer; // nullable for Phase 1 default

    private AddonNodeDefinition(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.category = b.category;
        this.color = b.color;
        this.provenanceLabel = b.provenanceLabel;
        this.bodyRenderer = b.bodyRenderer;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public AddonNodeCategory getCategory() { return category; }
    public int getColor() { return color; }
    public String getProvenanceLabel() { return provenanceLabel; }
    public AddonNodeBodyRenderer getBodyRenderer() { return bodyRenderer; }

    public static Builder builder(String id) { return new Builder(id); }

    public static final class Builder {
        private final String id;
        private String displayName;
        private AddonNodeCategory category;
        private int color = 0xFF888888;
        private String provenanceLabel = "";
        private AddonNodeBodyRenderer bodyRenderer = null;

        private Builder(String id) { this.id = id; }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder category(AddonNodeCategory v) { this.category = v; return this; }
        public Builder color(int v) { this.color = v; return this; }
        public Builder provenanceLabel(String v) { this.provenanceLabel = v; return this; }
        public Builder bodyRenderer(AddonNodeBodyRenderer v) { this.bodyRenderer = v; return this; }

        public AddonNodeDefinition build() {
            if (id == null || id.isBlank()) throw new NullPointerException("id is required");
            if (displayName == null || displayName.isBlank()) throw new NullPointerException("displayName is required");
            if (category == null) throw new NullPointerException("category is required");
            return new AddonNodeDefinition(this);
        }
    }
}
```

---

### `AddonNodeExecutor.java` (new functional interface)

**Analog:** `PathmindAddonEntrypoint.java` (same single-method interface idiom); async contract mirrors `BackgroundStartRunner` usage pattern in execution layer.

**Core pattern:**
```java
package com.pathmind.api.addon;

import java.util.concurrent.CompletableFuture;

/**
 * Executes a custom addon node. Must never block the game thread — return a
 * {@link CompletableFuture} immediately and complete it off-thread (or return
 * {@code CompletableFuture.completedFuture(NodeResult.SUCCESS)} for no-ops).
 */
@FunctionalInterface
public interface AddonNodeExecutor {
    CompletableFuture<NodeResult> execute(AddonNodeContext ctx);
}
```

---

### `AddonNodeSerializer.java` (new interface)

**Analog:** `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` (GSON serialization pattern, lines 33–38)

**GSON pattern from analog** (lines 33–38):
```java
// NodeGraphPersistence.java lines 33–38 — GSON instance construction to match:
private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(NodeType.class, new NodeTypeAdapter())
        .registerTypeAdapter(com.pathmind.nodes.NodeMode.class, new NodeModeAdapter())
        .create();
```

**Core pattern:**
```java
package com.pathmind.api.addon;

import java.util.Map;

/**
 * Serializes and deserializes addon-specific node state into the preset JSON.
 *
 * <p>IMPORTANT: When reading numeric values from {@code fields}, use
 * {@code ((Number) fields.get("key")).intValue()} — GSON deserializes JSON numbers
 * as {@code Double} when the target type is {@code Object}.
 *
 * <p>The serialize map MUST include a {@code "_schema_version"} integer key.
 */
public interface AddonNodeSerializer {
    /**
     * @return a map to be stored as the opaque addon blob in {@code NodeData.extraFields}.
     *         Must include {@code "_schema_version"} key with an integer value.
     */
    Map<String, Object> serialize(AddonNodeContext ctx);

    /**
     * Restore node state from the previously serialized map.
     *
     * @param fields the map as deserialized by GSON; numeric values will be {@code Double}.
     */
    void deserialize(AddonNodeContext ctx, Map<String, Object> fields);
}
```

---

### `AddonNodeContext.java` (new POJO)

**Analog:** `common/src/main/java/com/pathmind/data/NodeGraphData.NodeData` (lines 112–200) — same getter/setter POJO style.

**POJO pattern from analog** (lines 144–200 of NodeGraphData.java):
```java
// NodeData POJO style to match — private fields + getter/setter pairs:
private String id;
private NodeType type;
// ...
public String getId() { return id; }
public void setId(String id) { this.id = id; }
```

**Core pattern — narrow API surface only (no ExecutionManager, Node, or NodeGraph):**
```java
package com.pathmind.api.addon;

/**
 * Runtime context passed to addon node executors and serializers.
 * Contains only what the addon legitimately needs — Pathmind impl classes
 * ({@code Node}, {@code ExecutionManager}, {@code NodeGraph}) are deliberately excluded.
 */
public final class AddonNodeContext {
    private String addonTypeId;
    private String scriptText;    // Phase 1: only field used by LuaNodeExecutor/Serializer

    public AddonNodeContext() {}

    public String getAddonTypeId() { return addonTypeId; }
    public void setAddonTypeId(String addonTypeId) { this.addonTypeId = addonTypeId; }

    public String getScriptText() { return scriptText; }
    public void setScriptText(String scriptText) { this.scriptText = scriptText; }
}
```

---

### `AddonNodeBodyRenderer.java` (new functional interface)

**Analog:** `common/src/main/java/com/pathmind/ui/overlay/NodeErrorNotificationOverlay.java` (immediate-mode rendering style)

**Immediate-mode rendering pattern from analog** (NodeErrorNotificationOverlay.java line 46 method signature):
```java
// NodeErrorNotificationOverlay.java line 46 — rendering method signature style:
public synchronized void show(String message, int nodeColor)
// The overlay uses DrawContext passed in — same for the body renderer hook:
```

**Core pattern:**
```java
package com.pathmind.api.addon;

import net.minecraft.client.gui.DrawContext;

/**
 * Renders custom content inside an addon node's body area.
 * Called per-frame from the NodeGraph rendering loop — must be fast and stateless.
 */
@FunctionalInterface
public interface AddonNodeBodyRenderer {
    /**
     * @param ctx     the addon node's runtime context (read-only during rendering)
     * @param draw    Minecraft DrawContext for immediate-mode rendering
     * @param x       left edge of the node body content area
     * @param y       top edge of the node body content area
     * @param width   available width in pixels
     * @param height  available height in pixels
     */
    void render(AddonNodeContext ctx, DrawContext draw, int x, int y, int width, int height);
}
```

---

### `NodeResult.java` (new enum)

**Analog:** `common/src/main/java/com/pathmind/nodes/NodeType.java` (enum style with named constants, no constructor args needed)

**Enum pattern from analog** (NodeType.java lines 9–11):
```java
// NodeType enum declaration style:
public enum NodeType {
    START("pathmind.node.type.start", 0xFF4CAF50, "pathmind.node.type.start.desc"),
    ...
}
```

**Core pattern (simpler — no constructor args needed):**
```java
package com.pathmind.api.addon;

/** Result of an addon node execution. */
public enum NodeResult {
    /** Node completed successfully; graph advances to the next node. */
    SUCCESS,
    /** Node failed; graph follows the failure path if present, else halts. */
    FAILURE,
    /** Node was skipped (e.g., unresolved type); graph advances as if SUCCESS. */
    SKIPPED
}
```

---

### `PathmindApiVersion.java` (new constant class)

**Analog:** `fabric/src/main/java/com/pathmind/PathmindMod.java` lines 10–11 (static final constant pattern)

**Constant pattern from analog:**
```java
// PathmindMod.java lines 10–11:
public static final String MOD_ID = PathmindCommon.MOD_ID;
public static final Logger LOGGER = PathmindCommon.LOGGER;
```

**Core pattern:**
```java
package com.pathmind.api;

/** Semver for the Pathmind addon API surface, independent of the mod version. */
public final class PathmindApiVersion {
    /** Current API version string. Starts at "0.1.0"; 1.0.0 marks stable milestone. */
    public static final String VERSION = "0.1.0";

    /**
     * Minimum API version an addon must declare to be compatible with this build.
     * Addons declaring a lower minimum are disabled via the standard failure UX.
     */
    public static final String MIN_COMPATIBLE = "0.1.0";

    private PathmindApiVersion() {}
}
```

---

### `NodeTypeRegistry.java` (new singleton)

**Analog:** `common/src/main/java/com/pathmind/nodes/NodeBehaviorDefinitionRegistry.java` (entire file, lines 1–89)

**Singleton + EnumMap pattern from analog** (lines 1–26):
```java
// NodeBehaviorDefinitionRegistry.java lines 7–25 — singleton + map pattern:
final class NodeBehaviorDefinitionRegistry {
    private static final Map<NodeType, NodeBehaviorDefinition> DEFINITIONS = new EnumMap<>(NodeType.class);

    static {
        for (NodeType type : NodeType.values()) {
            NodeBehaviorDefinition definition = directDefinition(type).orElseGet(() -> composedDefinition(type));
            if (definition.hasAnyBehavior()) {
                DEFINITIONS.put(type, definition);
            }
        }
    }

    static NodeBehaviorDefinition get(NodeType type) {
        return DEFINITIONS.get(type);
    }

    static Map<NodeType, NodeBehaviorDefinition> snapshot() {
        return new EnumMap<>(DEFINITIONS);
    }
    // ...
    private NodeBehaviorDefinitionRegistry() {}
}
```

**Core pattern to copy — adapt to public singleton + String keys + install-guard:**
```java
package com.pathmind.nodes;

import com.pathmind.api.addon.AddonNodeDefinition;
import com.pathmind.api.addon.AddonNodeExecutor;
import com.pathmind.api.addon.AddonNodeSerializer;
import com.pathmind.api.addon.NodeTypeRegistrar;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Singleton registry for addon-provided node types.
 * Populated once by {@link com.pathmind.execution.AddonLoader} during mod init.
 */
public final class NodeTypeRegistry {
    public static final NodeTypeRegistry INSTANCE = new NodeTypeRegistry();

    private final Map<String, AddonNodeDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AddonNodeExecutor> executors = new LinkedHashMap<>();
    private final Map<String, AddonNodeSerializer> serializers = new LinkedHashMap<>();
    private boolean installed = false;

    private NodeTypeRegistry() {}

    /** Called once by AddonLoader after all entrypoints have run. */
    public void install(NodeTypeRegistrar registrar) {
        if (installed) throw new IllegalStateException("NodeTypeRegistry already installed");
        definitions.putAll(registrar.getDefinitions());
        executors.putAll(registrar.getExecutors());
        serializers.putAll(registrar.getSerializers());
        installed = true;
    }

    public boolean hasType(String addonTypeId) { return definitions.containsKey(addonTypeId); }
    public AddonNodeDefinition definitionFor(String id) { return definitions.get(id); }
    public AddonNodeExecutor executorFor(String id) { return executors.get(id); }
    public AddonNodeSerializer serializerFor(String id) { return serializers.get(id); }
    public Collection<AddonNodeDefinition> allDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }
}
```

---

### `AddonLoader.java` (new service)

**Analog:** `fabric/src/main/java/com/pathmind/PathmindMod.java` (the FabricLoader usage pattern, lines 18–30)

**FabricLoader pattern from analog** (PathmindMod.java lines 20–27):
```java
// PathmindMod.java lines 20–27 — FabricLoader.getInstance() usage + Logger pattern:
String minecraftVersion = FabricLoader.getInstance()
    .getModContainer("minecraft")
    .map(container -> container.getMetadata().getVersion().getFriendlyString())
    .orElse("unknown");
// ...
LOGGER.warn("Pathmind targets Minecraft {} but detected {}", ...);
```

**Error logging pattern from analog** (PathmindMod.java lines 19, 29):
```java
LOGGER.info("Initializing Pathmind mod");
LOGGER.info("Pathmind mod initialized successfully");
```

**Core pattern to write AddonLoader against:**
```java
package com.pathmind.execution;

import com.pathmind.PathmindCommon;
import com.pathmind.api.PathmindApiVersion;
import com.pathmind.api.addon.NodeTypeRegistrar;
import com.pathmind.api.addon.PathmindAddonEntrypoint;
import com.pathmind.nodes.NodeTypeRegistry;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AddonLoader {
    private static final Logger LOGGER = PathmindCommon.LOGGER;
    private static final Map<String, Throwable> failedAddons = new LinkedHashMap<>();

    public static void discoverAndLoad() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();
        List<EntrypointContainer<PathmindAddonEntrypoint>> containers =
            FabricLoader.getInstance()
                .getEntrypointContainers("pathmind", PathmindAddonEntrypoint.class);

        for (EntrypointContainer<PathmindAddonEntrypoint> container : containers) {
            String addonId = container.getProvider().getMetadata().getId();
            try {
                container.getEntrypoint().registerNodes(registrar);
                LOGGER.info("[Pathmind] Addon '{}' registered nodes successfully", addonId);
            } catch (Throwable t) {
                LOGGER.error("[Pathmind] Addon '{}' failed node registration — addon disabled", addonId, t);
                markFailed(addonId, t);
            }
        }

        registrar.seal();
        NodeTypeRegistry.INSTANCE.install(registrar);
    }

    public static void markFailed(String addonId, Throwable t) {
        failedAddons.put(addonId, t);
    }

    /** Returns the failure cause for {@code addonId}, or null if not failed. */
    public static Throwable getFailure(String addonId) {
        return failedAddons.get(addonId);
    }

    public static Map<String, Throwable> getFailedAddons() {
        return java.util.Collections.unmodifiableMap(failedAddons);
    }

    private AddonLoader() {}
}
```

**Note:** `NodeErrorNotificationOverlay.getInstance().show(message, color)` (overlay lines 46–55) should be called from the editor-open path (e.g., `PathmindScreens`) to surface queued failures — not from `AddonLoader` directly (AddonLoader runs before the client is ready).

---

### `NodeType.java` — MODIFICATION

**Analog:** itself (`common/src/main/java/com/pathmind/nodes/NodeType.java` lines 1–11)

**Enum addition pattern** — add one constant at the end of the enum body, before the constructor/methods. Match existing format exactly (lines 9–11):
```java
// Existing pattern (NodeType.java line 11):
START("pathmind.node.type.start", 0xFF4CAF50, "pathmind.node.type.start.desc"),

// New constant to add — place at end of enum constant list, before the semicolon:
ADDON("pathmind.node.type.addon", 0xFF888888, "pathmind.node.type.addon.desc");
```

The `NodeType` enum also needs `Node.java` to gain `private String addonTypeId` — see Node.java modification note in Shared Patterns.

---

### `NodeGraphData.java` — MODIFICATION (NodeData inner class)

**Analog:** itself — `NodeGraphData.NodeData` (lines 112–200)

**Field addition pattern** — add two new private fields after the existing last field on `NodeData` (line ~142), following the identical private-field style:
```java
// Existing last fields of NodeData (lines 140–142):
private Integer templateVersion;
private Boolean customNodeInstance;
private NodeGraphData templateGraph;

// New fields to add (after templateGraph):
private String addonTypeId;                  // "pathmind_lua:script" — only set when type == ADDON
private java.util.Map<String, Object> extraFields;  // addon blob; GSON handles nested JSON automatically
```

**Getter/setter pattern to match** (lines 166–200):
```java
// Match existing getter/setter style:
public String getAddonTypeId() { return addonTypeId; }
public void setAddonTypeId(String addonTypeId) { this.addonTypeId = addonTypeId; }

public java.util.Map<String, Object> getExtraFields() { return extraFields; }
public void setExtraFields(java.util.Map<String, Object> extraFields) { this.extraFields = extraFields; }
```

---

### `Sidebar.java` — MODIFICATION

**Analog:** itself (`common/src/main/java/com/pathmind/ui/sidebar/Sidebar.java` lines 52–107)

**Parallel map pattern** — the existing maps (lines 52–54) show the data structure to mirror:
```java
// Existing state to mirror (Sidebar.java lines 52–54):
private final Map<NodeCategory, List<NodeType>> categoryNodes;
private final Map<NodeCategory, List<NodeGroup>> groupedCategoryNodes;
private final Map<NodeCategory, Boolean> categoryExpanded;
```

**New parallel state to add:**
```java
// Add after line 63 (after customNodes field):
private final Map<com.pathmind.api.addon.AddonNodeCategory,
    List<com.pathmind.api.addon.AddonNodeDefinition>> addonCategoryNodes = new java.util.LinkedHashMap<>();
```

**initializeCategoryNodes pattern** (lines 96–107):
```java
// Existing init pattern (lines 96–106):
private void initializeCategoryNodes() {
    for (NodeCategory category : NodeCategory.values()) {
        List<NodeGroup> groups = createGroupsForCategory(category);
        List<NodeType> nodes = new ArrayList<>();
        for (NodeGroup group : groups) { nodes.addAll(group.getNodes()); }
        groupedCategoryNodes.put(category, groups);
        categoryNodes.put(category, nodes);
    }
    refreshCustomNodes();
}

// New method to add alongside it — called after NodeTypeRegistry.INSTANCE is installed:
public void initializeAddonCategoryNodes() {
    addonCategoryNodes.clear();
    for (com.pathmind.api.addon.AddonNodeDefinition def :
            com.pathmind.nodes.NodeTypeRegistry.INSTANCE.allDefinitions()) {
        addonCategoryNodes
            .computeIfAbsent(def.getCategory(), k -> new ArrayList<>())
            .add(def);
    }
}
```

---

### `PathmindMod.java` — MODIFICATION

**Analog:** itself (lines 18–30)

**Insertion pattern** — add one line before the final LOGGER.info:
```java
// Existing onInitialize (lines 18–30) — insert AddonLoader call before final log line:
@Override
public void onInitialize() {
    LOGGER.info("Initializing Pathmind mod");

    String minecraftVersion = FabricLoader.getInstance()
        .getModContainer("minecraft")
        .map(container -> container.getMetadata().getVersion().getFriendlyString())
        .orElse("unknown");
    if (!VersionSupport.isSupported(minecraftVersion)) {
        LOGGER.warn("Pathmind targets Minecraft {} but detected {}", VersionSupport.SUPPORTED_RANGE, minecraftVersion);
    }

    // NEW — must be last; all Pathmind internal state is ready at this point
    com.pathmind.execution.AddonLoader.discoverAndLoad();

    LOGGER.info("Pathmind mod initialized successfully");
}
```

---

### `fabric/build.gradle.kts` — MODIFICATION

**Analog:** itself (lines 1–5 plugins block)

**Plugin addition pattern** (lines 1–5):
```kotlin
// Existing plugins block (lines 1–5):
plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

// Add maven-publish:
plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
    id("maven-publish")      // NEW
}
```

**Publishing block to add at end of file:**
```kotlin
publishing {
    publications {
        create<MavenPublication>("mavenFabric") {
            groupId = "com.pathmind"
            artifactId = "pathmind-fabric"
            // version comes from gradle.properties "mod_version"
            from(components["java"])
        }
    }
}
// Dev loop: ./gradlew :fabric:publishToMavenLocal
// After every Pathmind rebuild, delete: pathmind-lua/.gradle/loom-cache/remapped_mods
```

---

### `fabric/src/main/resources/fabric.mod.json` — MODIFICATION

**Analog:** itself (lines 16–18 entrypoints block)

**Entrypoint addition pattern** (lines 16–18):
```json
// Existing entrypoints block (lines 16–18):
"entrypoints": {
  "main": ["com.pathmind.PathmindMod"],
  "client": ["com.pathmind.PathmindClientMod"]
}

// Add pathmind key to declare the addon plugin point (no values — Pathmind itself doesn't implement its own entrypoint):
"entrypoints": {
  "main": ["com.pathmind.PathmindMod"],
  "client": ["com.pathmind.PathmindClientMod"]
}
// Note: No "pathmind" key needed in Pathmind's own fabric.mod.json.
// The key is used by ADDON mods in their fabric.mod.json. Document in API javadoc.
```

---

### `LuaAddonEntrypoint.java` (addon, new)

**Analog:** `fabric/src/main/java/com/pathmind/PathmindMod.java` (implements a Fabric interface, same lifecycle pattern)

**Imports pattern** — only API imports, never impl:
```java
package com.mrmysterium.pathmindlua;

import com.pathmind.api.addon.AddonNodeCategory;
import com.pathmind.api.addon.AddonNodeDefinition;
import com.pathmind.api.addon.NodeTypeRegistrar;
import com.pathmind.api.addon.PathmindAddonEntrypoint;
// NO: import com.pathmind.execution.*, import com.pathmind.ui.*, import com.pathmind.nodes.Node
```

**Core pattern:**
```java
public class LuaAddonEntrypoint implements PathmindAddonEntrypoint {
    private static final AddonNodeCategory SCRIPTING_CATEGORY =
        new AddonNodeCategory("pathmind_lua.scripting", "Scripting", 0xFF7986CB, "✦");

    @Override
    public void registerNodes(NodeTypeRegistrar registrar) {
        registrar.register(
            AddonNodeDefinition.builder("pathmind_lua:script")
                .displayName("Lua Script")
                .category(SCRIPTING_CATEGORY)
                .color(0xFF7986CB)
                .provenanceLabel("Pathmind Lua")
                .bodyRenderer(new LuaScriptNodeRenderer())
                .build(),
            new LuaNodeExecutor(),
            new LuaNodeSerializer()
        );
    }
}
```

---

### `LuaNodeExecutor.java` (addon, new)

**Analog:** `AddonNodeExecutor.java` (the interface it implements); Phase 1 is a no-op.

**Core pattern (Phase 1 — graceful pass-through):**
```java
package com.mrmysterium.pathmindlua;

import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeExecutor;
import com.pathmind.api.addon.NodeResult;

import java.util.concurrent.CompletableFuture;

public class LuaNodeExecutor implements AddonNodeExecutor {
    @Override
    public CompletableFuture<NodeResult> execute(AddonNodeContext ctx) {
        // Phase 1: graceful pass-through. Phase 2 replaces this with Cobalt VM execution.
        return CompletableFuture.completedFuture(NodeResult.SUCCESS);
    }
}
```

---

### `LuaNodeSerializer.java` (addon, new)

**Analog:** `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` (GSON serialization, lines 34–38) + `AddonNodeSerializer` interface

**GSON Double-cast warning** — from Pitfall 4: use `((Number) fields.get("_schema_version")).intValue()`, not `(Integer)`.

**Core pattern:**
```java
package com.mrmysterium.pathmindlua;

import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

public class LuaNodeSerializer implements AddonNodeSerializer {
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DEFAULT_SCRIPT = "-- Lua script\nprint(\"Hello from Pathmind Lua!\")";

    @Override
    public Map<String, Object> serialize(AddonNodeContext ctx) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("_schema_version", CURRENT_SCHEMA_VERSION);
        String script = ctx.getScriptText();
        fields.put("script", script != null ? script : DEFAULT_SCRIPT);
        return fields;
    }

    @Override
    public void deserialize(AddonNodeContext ctx, Map<String, Object> fields) {
        if (fields == null) {
            ctx.setScriptText(DEFAULT_SCRIPT);
            return;
        }
        // Use Number.intValue() — GSON deserializes JSON numbers as Double when target is Object
        Object script = fields.get("script");
        ctx.setScriptText(script instanceof String s ? s : DEFAULT_SCRIPT);
    }
}
```

---

### `LuaScriptNodeRenderer.java` (addon, new)

**Analog:** `common/src/main/java/com/pathmind/ui/overlay/NodeErrorNotificationOverlay.java` (immediate-mode rendering with DrawContext)

**DrawContext rendering pattern from analog** (NodeErrorNotificationOverlay.java — rendering style uses DrawContext passed in + text renderer from MinecraftClient):
```java
// NodeErrorNotificationOverlay — text rendering style (inferred from DrawContext usage):
// drawContext.drawTextWithShadow(textRenderer, text, x, y, color)
```

**Core pattern (Phase 1 — read-only script preview, first 3 lines):**
```java
package com.mrmysterium.pathmindlua;

import com.pathmind.api.addon.AddonNodeBodyRenderer;
import com.pathmind.api.addon.AddonNodeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class LuaScriptNodeRenderer implements AddonNodeBodyRenderer {
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int MAX_PREVIEW_LINES = 3;
    private static final int LINE_HEIGHT = 10;

    @Override
    public void render(AddonNodeContext ctx, DrawContext draw, int x, int y, int width, int height) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        String script = ctx.getScriptText();
        if (script == null || script.isBlank()) return;

        String[] lines = script.split("\n", MAX_PREVIEW_LINES + 1);
        for (int i = 0; i < Math.min(lines.length, MAX_PREVIEW_LINES); i++) {
            String line = lines[i];
            // Truncate if wider than available space
            while (line.length() > 1 && textRenderer.getWidth(line + "…") > width) {
                line = line.substring(0, line.length() - 1);
            }
            if (lines[i].length() > line.length()) line = line + "…";
            draw.drawTextWithShadow(textRenderer, line, x, y + i * LINE_HEIGHT, TEXT_COLOR);
        }
    }
}
```

---

### `pathmind-lua/fabric.mod.json` (addon, new)

**Analog:** `fabric/src/main/resources/fabric.mod.json` (lines 1–28 — entire file)

**Pattern to copy — adapt these fields only:**
```json
{
  "schemaVersion": 1,
  "id": "pathmind-lua",
  "version": "${version}",
  "name": "Pathmind Lua",
  "description": "Lua scripting addon for Pathmind — adds a Script node powered by a Lua VM.",
  "authors": ["mr_mysterium"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "pathmind": ["com.mrmysterium.pathmindlua.LuaAddonEntrypoint"]
  },
  "depends": {
    "fabricloader": ">=0.17.3",
    "minecraft": "1.21.4",
    "java": ">=21",
    "pathmind": ">=0.1.0"
  }
}
```

---

### `pathmind-lua/build.gradle.kts` (addon, new)

**Analog:** `fabric/build.gradle.kts` (lines 1–90)

**Core plugin + dependency pattern to copy** (lines 1–5, 58–67):
```kotlin
// Plugins block (copy from fabric/build.gradle.kts lines 1–5):
plugins {
    id("dev.architectury.loom") version "1.14.473"
    id("architectury-plugin") version "3.4.161"
}

// Repositories (add before dependencies):
repositories {
    mavenLocal()    // required for pathmind-fabric artifact
    maven("https://maven.architectury.dev/")
    maven("https://maven.fabricmc.net/")
}

// Dependencies (adapt from fabric/build.gradle.kts lines 58–67):
dependencies {
    minecraft("com.mojang:minecraft:1.21.4")
    mappings("net.fabricmc:yarn:1.21.4+build.8:v2")
    modImplementation("net.fabricmc:fabric-loader:0.17.3")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.102.0+1.21.4")

    // Pathmind API — compile-only (zero impl classes on classpath)
    modCompileOnly("com.pathmind:pathmind-fabric:${pathmind_version}")
}
```

---

### `NodeTypeRegistryTest.java` (test, new)

**Analog:** `common/src/test/java/com/pathmind/nodes/NodeBehaviorDefinitionRegistryTest.java` (entire file, lines 1–373)

**Test class pattern from analog** (lines 1–20):
```java
// NodeBehaviorDefinitionRegistryTest.java lines 1–16 — import and class structure:
package com.pathmind.nodes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeBehaviorDefinitionRegistryTest {
    @Test
    void targetParameterFamilyHasConsolidatedDefinitions() { ... }
```

**Test method naming pattern** — verb-phrase describing behavior, no "test" prefix:
```java
// From analog (lines 19–47): method name style is camelCase behavior description:
void targetParameterFamilyHasConsolidatedDefinitions()
void comparableAndRuntimeFamiliesAreAvailableThroughUnifiedDefinitions()
```

**Core test cases to write:**
```java
package com.pathmind.nodes;

import com.pathmind.api.addon.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NodeTypeRegistryTest {

    // Test registration round-trips
    @Test
    void registeredAddonTypeIsRetrievableByStringId() { ... }

    // Test duplicate ID rejection
    @Test
    void duplicateAddonTypeIdThrowsIllegalArgumentException() { ... }

    // Test null argument rejection
    @Test
    void nullExecutorThrowsNullPointerException() { ... }

    // Test sealed registrar rejects late registration
    @Test
    void registrationAfterSealThrowsIllegalStateException() { ... }

    // Test malformed ID rejection (security: path traversal)
    @Test
    void malformedAddonTypeIdIsRejected() { ... }
}
```

---

## Shared Patterns

### Error Logging (apply to AddonLoader.java and all new service classes)

**Source:** `fabric/src/main/java/com/pathmind/PathmindMod.java` lines 11–29

```java
// Logger acquisition pattern:
public static final Logger LOGGER = PathmindCommon.LOGGER;

// Error log style:
LOGGER.error("[Pathmind] Addon '{}' failed — addon disabled", addonId, throwable);

// Info log style:
LOGGER.info("[Pathmind] Addon '{}' registered nodes successfully", addonId);
```

### Error Notification Overlay (apply to editor-open path when surfacing addon failures)

**Source:** `common/src/main/java/com/pathmind/ui/overlay/NodeErrorNotificationOverlay.java` lines 42–55

```java
// Public API for surfacing addon-failure warnings (called from PathmindScreens when editor opens):
NodeErrorNotificationOverlay.getInstance().show(
    "[Pathmind] Addon '" + addonId + "' failed to load: " + failureCause.getMessage(),
    0xFFFF5722   // error accent color
);
```

### POJO Getter/Setter Style (apply to all new data classes)

**Source:** `common/src/main/java/com/pathmind/data/NodeGraphData.java` lines 165–200

```java
// Pattern: private field + public getter + public setter on same line
public String getId() { return id; }
public void setId(String id) { this.id = id; }
```

### Null Guard Before Operations (apply to all new service methods)

**Source:** `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` lines 62–65

```java
// Null-check pattern at top of public methods:
if (data == null || savePath == null) {
    return false;
}
```

### Node Field on `Node.java` (required for ADDON sentinel)

The `Node.java` class (`common/src/main/java/com/pathmind/nodes/Node.java`) must gain:
```java
private String addonTypeId; // non-null only when type == NodeType.ADDON

public String getAddonTypeId() { return addonTypeId; }
public void setAddonTypeId(String addonTypeId) { this.addonTypeId = addonTypeId; }
```
Follow existing field + getter/setter style in Node.java (same as NodeGraphData.NodeData pattern above).

---

## No Analog Found

All files have analogs. No files require falling back to RESEARCH.md patterns exclusively.

---

## Open Questions (for planner to resolve before writing task details)

Per RESEARCH.md Assumptions A4–A7 — these specific integration points need targeted reads before the plan can specify exact line numbers for Sidebar rendering, NodeGraph drag handling, and ExecutionManager ADDON branch insertion:

1. **A6: NodeErrorNotificationOverlay enqueue API** — `show(String, int)` exists at line 46 (verified). No additional enqueue method needed. Caller should use `getInstance().show(...)`.
2. **A7: NodeGraph node-body rendering hook location** — RESEARCH.md flags this as unverified. Read `NodeGraph.java` node-body rendering section before writing the NodeGraph modification task.
3. **A5: Drag-and-drop with NodeType.ADDON** — RESEARCH.md flags the drag payload path as unverified. Read NodeGraph drag-start handling before writing the drag-from-sidebar task.

---

## Metadata

**Analog search scope:** `common/src/main/java/com/pathmind/`, `fabric/src/main/java/com/pathmind/`, `common/src/test/java/com/pathmind/`, `fabric/src/main/resources/`
**Files read:** 12
**Pattern extraction date:** 2026-06-12
