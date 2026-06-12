# Coding Conventions

**Analysis Date:** 2026-06-12

## Naming Patterns

**Files:**
- Classes: PascalCase (e.g., `NodeGraphData.java`, `ExecutionManager.java`)
- Package structure: lowercase hierarchy (e.g., `com.pathmind.data`, `com.pathmind.execution`)
- Test files: ClassName + Test suffix (e.g., `NodeAttachmentsTest.java`)

**Classes:**
- Data classes (POJOs): Descriptive names with "Data" suffix for serialization targets (`NodeGraphData`, `ConnectionData`)
- Manager classes: Descriptive + "Manager" suffix (`ExecutionManager`, `PresetManager`, `SettingsManager`)
- Proxy/Wrapper classes: Descriptive + "Proxy" suffix (`BaritoneApiProxy`)
- Mixin classes: ClassName + "Mixin" suffix (`GameRendererMixin`, `InGameHudMixin`)
- Service classes: Descriptive + "Service" suffix (`MarketplaceService`)

**Functions:**
- camelCase, verb-first for most methods (`saveNodeGraph`, `loadNodeGraph`, `attachParameter`)
- Getters: `get` + PropertyName (e.g., `getNodes()`, `getConnections()`, `getActiveNode()`)
- Setters: `set` + PropertyName (e.g., `setNodes()`, `setConnections()`)
- Boolean predicates: `is` + Condition (e.g., `isExecuting()`) or `has` + Object (e.g., `hasParameters()`)
- Static utility methods: descriptive camelCase (e.g., `getPrimaryBaritone()`, `getAllowBreak()`)

**Variables:**
- Instance fields: private camelCase (e.g., `activeNode`, `executionStartTime`, `globalRuntimeVariables`)
- Constants: UPPER_SNAKE_CASE (e.g., `MINIMUM_DISPLAY_DURATION`, `CHAT_MESSAGE_EVENT_NAME`, `CONNECTION_DOT_SPACING`)
- Local variables: camelCase (e.g., `presetName`, `nodeMap`, `savePath`)
- Collections: plural names (e.g., `nodes`, `connections`, `activeChains`, `globalRuntimeVariables`)

**Types:**
- Enums: PascalCase (e.g., `NodeType`, `NodeMode`, `ParameterType`)
- Inner classes: PascalCase prefixed with outer class context (e.g., `NodeGraphData.CustomNodeDefinition`)
- Generic type parameters: Single uppercase letter (e.g., `<T>`, `<K, V>`)

## Code Style

**Formatting:**
- Indentation: 4 spaces (standard Java convention)
- Line length: No strict limit observed, files contain methods up to 7474 lines
- Brace style: Egyptian (opening brace on same line as statement)
- No automated formatter configured (no `.editorconfig`, `.prettierrc`, or `checkstyle.xml` found)

**Linting:**
- No eslint or checkstyle configuration detected
- Compilation flags: `-Xlint:-deprecation -Xlint:-removal` (warnings suppressed for deprecation and removal)
- Java version: 21 target (`options.release.set(21)`)

**Access Modifiers:**
- public: Exposed APIs and data classes
- private: Field encapsulation (4230+ private declarations found)
- protected: Limited use for mixin/inheritance scenarios
- package-private: Default for internal test classes

## Import Organization

**Order:**
1. Package declaration
2. Standard Java imports (java.*, javax.*)
3. Third-party imports (com.google.*, org.*)
4. Minecraft/Fabric imports (net.minecraft.*)
5. Project-specific imports (com.pathmind.*)

**Examples from codebase:**
```java
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.NodeMode;
import com.pathmind.execution.ExecutionManager;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.List;
```

**Path Aliases:**
- No aliases used; fully qualified imports throughout

## Error Handling

**Patterns:**
- Try-catch blocks wrap I/O operations (`NodeGraphPersistence.java`)
- Broad exception catching: `catch (Exception e)` typical pattern
- Silent failures with System.err logging: `System.err.println("Failed to load node graph: " + e.getMessage())`
- Null checks before operations: `if (data == null || savePath == null) { return false; }`
- Reflective operations: catch `ReflectiveOperationException | LinkageError` (`BaritoneApiProxy.java`)
- No custom exception types; uses standard Java exceptions

**Strategy:**
- Try-with-resources for file operations (`try (Reader reader = Files.newBufferedReader(savePath))`)
- Method returns boolean or null to indicate success/failure
- Error messages printed to System.err rather than using logging framework
- No exception propagation to caller; methods handle gracefully with safe defaults

## Logging

**Framework:** System.err (not SLF4J/Log4j despite some SLF4J imports found)

**Patterns:**
- Use System.err.println for error messages: `System.err.println("Failed to deserialize cached node graph: " + e.getMessage())`
- SLF4J imported in some classes but not consistently used:
  - `ExecutionManager.java`: `LOGGER.getLogger(ExecutionManager.class)` defined but rarely used
  - Most errors go to System.err instead
- No logging for successful operations
- Error context limited to exception message

**When to log:**
- File I/O failures
- Deserialization errors
- Data conversion issues
- External API call failures (Baritone reflection)

## Comments

**When to Comment:**
- Class-level: Javadoc for public classes and complex responsibilities
  - `/** Serializable data structure for saving and loading node graphs. */`
  - `/** Manages the execution state of the node graph. */`
  - `/** Reflection-based wrapper for Baritone API usage when the dependency is optional. */`
- Method-level: Javadoc for public methods with complex logic or non-obvious behavior
- Inline comments: Sparse; used for tricky algorithm explanations or version-specific workarounds

**Javadoc/TSDoc:**
- Limited Javadoc coverage (45+ @Override annotations found but minimal Javadoc)
- Class-level documentation present for major components
- Method documentation sparse; code is expected to be self-documenting
- No standardized format across codebase

**Examples:**
```java
/**
 * Serializable data structure for saving and loading node graphs.
 */
public class NodeGraphData { ... }

/**
 * Manages the execution state of the node graph.
 * Tracks which node is currently active and provides state information for overlays.
 */
public class ExecutionManager { ... }
```

## Function Design

**Size:** No enforced limits; large methods observed
- `Node.java`: 7474 lines (extreme monolithic class)
- `NodeGraph.java`: 15849 lines
- `PathmindNavigator.java`: 9998 lines
- Methods in these classes vary from 10 to 300+ lines

**Parameters:**
- Typically 0-5 parameters per method
- Data classes used when many parameters needed (e.g., `NodeGraphData`)
- No varargs observed; collections passed explicitly

**Return Values:**
- Primitive/wrapper types: int, boolean, String, List<T>
- Objects: Node, NodeGraphData, ExecutionManager
- Optional: null used instead of Optional<T>
- Void for mutation operations

**Null Handling:**
- Null checks at entry: `if (presetName == null || presetName.isBlank()) { return false; }`
- Null returns permitted for "not found" scenarios
- Defensive checks before property access

## Module Design

**Exports:**
- Public classes exposed; private/package-private used for internal implementation
- Data classes (POJO): Full constructor and getter/setter pairs
- Service/Manager classes: Static factory methods (`getInstance()`, `getProvider()`)

**Barrel Files:**
- No aggregating import files (star imports)
- Each class imported explicitly

**Package Organization:**
- `com.pathmind.data`: Persistence, configuration, state management
- `com.pathmind.execution`: Graph execution, navigation, state tracking
- `com.pathmind.nodes`: Node definitions, types, behaviors
- `com.pathmind.ui.*`: UI rendering, screen handlers, animations
- `com.pathmind.util`: Utilities, proxies, compatibility bridges
- `com.pathmind.mixin`: Minecraft integration via Mixin injection
- `com.pathmind.screen`: Custom screen implementations
- `com.pathmind.marketplace`: Marketplace integration

---

*Convention analysis: 2026-06-12*
