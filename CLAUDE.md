
## Project

**Pathmind Addon API + Lua Scripting Addon**

Two co-developed pieces of work: (1) refactoring Pathmind — a visual node-based Minecraft automation editor — to expose a proper addon API, and (2) building the first addon against that API: a Lua Script node that ships a Java-based Lua VM with access to Pathmind's actions, states, and node-tree variables. The addon lives in a sibling repo and is a pure addon mod: Pathmind works with or without it, but the addon requires Pathmind — the same relationship Fabric API or Roughly Enough Items has with mods that build on them.

**Core Value:** A third party can drop `pathmind.jar` + `pathmind-lua-addon.jar` into a mods folder and get a working Lua script node — proving the addon API is real, stable, and consumable by external developers.

### Constraints

- **Tech stack**: Java 21, Gradle 8.x, Fabric Loader, Architectury Loom — addon must match Pathmind's build ecosystem
- **Target platform**: Fabric on Minecraft 1.21.4 for addon v1 — single version to keep API iteration fast
- **Repo layout**: Addon is a sibling repo (true external consumer) — proves the API honestly; Pathmind must publish/expose a consumable API artifact
- **Compatibility**: Pathmind must remain fully functional standalone (no addon installed) across its existing 1.21–1.21.11 range
- **Dependency direction**: Addon depends on Pathmind, never the reverse

### Fork discipline — upstream semantics are a guardrail

This repo is a **fork of `soymods/pathmind`** (remote `upstream`); the upstream developer's internal logic must stay intact so the fork can keep tracking upstream. Concretely:

- **Do not change upstream execution semantics.** The upstream pattern for node failures is deliberate: *show an error notification, then complete the node future NORMALLY so the graph chain continues* (see `NodeExecutionCompletion.fail` and the "node-failure observability" comment in `ExecutionManager`). Never switch upstream code paths to `completeExceptionally` or otherwise alter continue-on-error graph behavior.
- **Strictness belongs in the fork's API layer.** Files that are fork-only additions (e.g. `com.pathmind.api.addon.*`, `PathmindRuntimeImpl`, `AddonActionInvoker`, `AddonListEntryCodec`) are where addon-facing behavior changes go. Example precedent: the PLACE false-success fix (2026-07-12) kept the executor behaviorally identical for graph nodes (routing failure paths through the existing `NodeExecutionCompletion.fail`, which fires the fork's `recordNodeFailure` hook) and implemented strict fail-on-error semantics only in `AddonActionInvoker` via the failure-counter snapshot.
- **Check before you touch:** `git diff upstream/main main --numstat -- <file>` tells you whether a file is upstream code (unchanged/small delta) or fork-owned (all-insertions). For upstream files, keep any touch minimal and behavior-preserving; if a real behavior change in upstream code seems unavoidable, stop and discuss it with the user (the fork maintainer — they coordinate with the upstream developer directly) instead of implementing it.
- Beware line-ending noise: huge symmetric diffs vs upstream (e.g. `Node.java`) are CRLF artifacts, not real divergence. Never commit full-file line-ending rewrites.



## Technology Stack

## Languages

- Java 21+ - Core mod implementation and all gameplay logic
- Kotlin (DSL) - Gradle build configuration in `.kts` files
- SQL - Supabase marketplace backend schemas and RPC functions (`supabase/*.sql`)
- JSON - Configuration and data serialization

## Runtime

- Minecraft Java Edition 1.21 through 1.21.11
- JVM (Java 21+)
- Gradle 8.x (wrapper: `gradlew.bat` / `./gradlew`)
- Lockfile: Not applicable (Gradle uses `gradle.properties` for version pinning)

## Frameworks

- Architectury API 13.0.2–19.0.1 (version-dependent) - Multi-platform abstraction layer
- Fabric Loader 0.17.3 - Fabric mod loader
- Fabric API 0.102.0–0.140.2 (version-dependent) - Fabric modding utilities
- NeoForge 21.0.166–21.11.42 (version-dependent) - Alternative mod loader (optional per MC version)
- dev.architectury.loom 1.14.473 - Fabric/NeoForge IDE setup and remapping
- architectury-plugin 3.4.161 - Multi-platform build coordination
- com.gradleup.shadow 9.4.1 - JAR shadowing for dependency bundling
- JUnit Jupiter 5.11.4 - Unit testing framework
- JUnit Platform Launcher - Test runner

## Key Dependencies

- com.google.code.gson:gson:2.10.1 - JSON serialization/deserialization for node graphs, presets, and Supabase communication
- Minecraft:minecraft (1.21–1.21.11) - Game library with deobfuscated source via Yarn mappings
- net.fabricmc:yarn (1.21+build.9 through 1.21.11+build.3) - Deobfuscation mappings for Fabric
- Baritone API (optional, JAR-based) - Pathfinding and movement control integration
- UI Utils mod - For UI automation nodes (runtime dependency, not built-in)

## Configuration

- `gradle.properties` - Root-level build properties:
- `gradle/wrapper/gradle-wrapper.properties` - Gradle version pinning
- `build.gradle.kts` - Root build script with multi-version Minecraft support (1.21–1.21.11)
- `settings.gradle.kts` - Subproject includes: common, fabric, neoforge
- `common/build.gradle.kts` - Shared code compilation, Yarn mappings, access wideners
- `fabric/build.gradle.kts` - Fabric-specific build (shadowJar, remapping)
- `neoforge/build.gradle.kts` - NeoForge-specific build (official Mojang mappings)
- `buildSrc/build.gradle.kts` - Custom Gradle tasks for remapping and JAR manipulation
- Target Java version: 21
- Source encoding: UTF-8
- Access widener: `common/src/main/resources/pathmind.accesswidener` (grants private field/method access for mixins)
- Loom IDE configuration with customizable client JVM args:
- JVM args for build: `-Xmx4G -XX:MaxMetaspaceSize=1G -Dfile.encoding=UTF-8`
- Parallel builds enabled with 2 worker threads

## Version Management

- 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11
- **Legacy Input APIs** (1.21–1.21.8): `src/compat/legacy/base/java`
- **Legacy Input with Typed UseItem** (1.21–1.21.1): `src/compat/legacy/useitem/typed/java`
- **Legacy Render APIs** (1.21–1.21.4): `src/compat/legacy/render/*`
- **Transitional Render APIs** (1.21.5): `src/compat/legacy/render/transitional/java`
- **Mid-Version Input APIs** (1.21.9–1.21.10): `src/compat/mid/java`
- **Modern APIs** (1.21.11+): `src/compat/modern/java`
- 1.21: 13.0.2
- 1.21.1–1.21.3: 13.0.6
- 1.21.4: 14.0.4
- 1.21.5: 14.0.4
- 1.21.6–1.21.7: 17.0.x
- 1.21.8: 17.0.8
- 1.21.9–1.21.10: 18.0.x
- 1.21.11: 19.0.1

## Platform Requirements

- Java 21+ (required at build-time)
- Gradle 8.x (via wrapper)
- IDE with Gradle and Loom support (IntelliJ IDEA, VSCode with Gradle extension, Eclipse)
- Optional: Baritone API JAR for pathfinding features
- Minecraft 1.21–1.21.11 (Java Edition)
- Java 21+ (via Minecraft launcher)
- Architectury API (required, downloads automatically via mod loader)
- Fabric Loader 0.17.3+ OR NeoForge 21.0.166+ (depending on platform choice)
- Fabric API (required for Fabric; downloads automatically) OR no additional deps (NeoForge)
- Optional: Baritone API mod (for expanded pathfinding)
- Optional: UI Utils mod (for UI automation features)

## Build Outputs

- Fabric JAR: `fabric/build/libs/pathmind-fabric-[version]-mc[x.xx.xx].jar`
- NeoForge JAR: `neoforge/build/libs/pathmind-neoforge-[version]-mc[x.xx.xx].jar`
- Source JARs: `*-mc[x.xx.xx]-sources.jar` (for each platform)
- Task: `buildAllTargets` - Compiles all 11–12 Minecraft versions sequentially
- Output directory: `build/multiVersion/[version]/` for each supported MC version

## Compiler Configuration

- Deprecation warnings suppressed (`-Xlint:-deprecation`)
- Removal warnings suppressed (`-Xlint:-removal`)
- Fabric: Yarn mappings (human-readable names)
- NeoForge: Official Mojang mappings (obfuscated param names, deobfuscated class/method names)
- Custom remapping task for NeoForge common jars: `RemapJarToMojangTask` in `buildSrc/`



## Conventions

## Naming Patterns

- Classes: PascalCase (e.g., `NodeGraphData.java`, `ExecutionManager.java`)
- Package structure: lowercase hierarchy (e.g., `com.pathmind.data`, `com.pathmind.execution`)
- Test files: ClassName + Test suffix (e.g., `NodeAttachmentsTest.java`)
- Data classes (POJOs): Descriptive names with "Data" suffix for serialization targets (`NodeGraphData`, `ConnectionData`)
- Manager classes: Descriptive + "Manager" suffix (`ExecutionManager`, `PresetManager`, `SettingsManager`)
- Proxy/Wrapper classes: Descriptive + "Proxy" suffix (`BaritoneApiProxy`)
- Mixin classes: ClassName + "Mixin" suffix (`GameRendererMixin`, `InGameHudMixin`)
- Service classes: Descriptive + "Service" suffix (`MarketplaceService`)
- camelCase, verb-first for most methods (`saveNodeGraph`, `loadNodeGraph`, `attachParameter`)
- Getters: `get` + PropertyName (e.g., `getNodes()`, `getConnections()`, `getActiveNode()`)
- Setters: `set` + PropertyName (e.g., `setNodes()`, `setConnections()`)
- Boolean predicates: `is` + Condition (e.g., `isExecuting()`) or `has` + Object (e.g., `hasParameters()`)
- Static utility methods: descriptive camelCase (e.g., `getPrimaryBaritone()`, `getAllowBreak()`)
- Instance fields: private camelCase (e.g., `activeNode`, `executionStartTime`, `globalRuntimeVariables`)
- Constants: UPPER_SNAKE_CASE (e.g., `MINIMUM_DISPLAY_DURATION`, `CHAT_MESSAGE_EVENT_NAME`, `CONNECTION_DOT_SPACING`)
- Local variables: camelCase (e.g., `presetName`, `nodeMap`, `savePath`)
- Collections: plural names (e.g., `nodes`, `connections`, `activeChains`, `globalRuntimeVariables`)
- Enums: PascalCase (e.g., `NodeType`, `NodeMode`, `ParameterType`)
- Inner classes: PascalCase prefixed with outer class context (e.g., `NodeGraphData.CustomNodeDefinition`)
- Generic type parameters: Single uppercase letter (e.g., `<T>`, `<K, V>`)

## Code Style

- Indentation: 4 spaces (standard Java convention)
- Line length: No strict limit observed, files contain methods up to 7474 lines
- Brace style: Egyptian (opening brace on same line as statement)
- No automated formatter configured (no `.editorconfig`, `.prettierrc`, or `checkstyle.xml` found)
- No eslint or checkstyle configuration detected
- Compilation flags: `-Xlint:-deprecation -Xlint:-removal` (warnings suppressed for deprecation and removal)
- Java version: 21 target (`options.release.set(21)`)
- public: Exposed APIs and data classes
- private: Field encapsulation (4230+ private declarations found)
- protected: Limited use for mixin/inheritance scenarios
- package-private: Default for internal test classes

## Import Organization

- No aliases used; fully qualified imports throughout

## Error Handling

- Try-catch blocks wrap I/O operations (`NodeGraphPersistence.java`)
- Broad exception catching: `catch (Exception e)` typical pattern
- Silent failures with System.err logging: `System.err.println("Failed to load node graph: " + e.getMessage())`
- Null checks before operations: `if (data == null || savePath == null) { return false; }`
- Reflective operations: catch `ReflectiveOperationException | LinkageError` (`BaritoneApiProxy.java`)
- No custom exception types; uses standard Java exceptions
- Try-with-resources for file operations (`try (Reader reader = Files.newBufferedReader(savePath))`)
- Method returns boolean or null to indicate success/failure
- Error messages printed to System.err rather than using logging framework
- No exception propagation to caller; methods handle gracefully with safe defaults

## Logging

- Use System.err.println for error messages: `System.err.println("Failed to deserialize cached node graph: " + e.getMessage())`
- SLF4J imported in some classes but not consistently used:
- No logging for successful operations
- Error context limited to exception message
- File I/O failures
- Deserialization errors
- Data conversion issues
- External API call failures (Baritone reflection)

## Comments

- Class-level: Javadoc for public classes and complex responsibilities
- Method-level: Javadoc for public methods with complex logic or non-obvious behavior
- Inline comments: Sparse; used for tricky algorithm explanations or version-specific workarounds
- Limited Javadoc coverage (45+ @Override annotations found but minimal Javadoc)
- Class-level documentation present for major components
- Method documentation sparse; code is expected to be self-documenting
- No standardized format across codebase

## Function Design

- `Node.java`: 7474 lines (extreme monolithic class)
- `NodeGraph.java`: 15849 lines
- `PathmindNavigator.java`: 9998 lines
- Methods in these classes vary from 10 to 300+ lines
- Typically 0-5 parameters per method
- Data classes used when many parameters needed (e.g., `NodeGraphData`)
- No varargs observed; collections passed explicitly
- Primitive/wrapper types: int, boolean, String, List<T>
- Objects: Node, NodeGraphData, ExecutionManager
- Optional: null used instead of Optional<T>
- Void for mutation operations
- Null checks at entry: `if (presetName == null || presetName.isBlank()) { return false; }`
- Null returns permitted for "not found" scenarios
- Defensive checks before property access

## Module Design

- Public classes exposed; private/package-private used for internal implementation
- Data classes (POJO): Full constructor and getter/setter pairs
- Service/Manager classes: Static factory methods (`getInstance()`, `getProvider()`)
- No aggregating import files (star imports)
- Each class imported explicitly
- `com.pathmind.data`: Persistence, configuration, state management
- `com.pathmind.execution`: Graph execution, navigation, state tracking
- `com.pathmind.nodes`: Node definitions, types, behaviors
- `com.pathmind.ui.*`: UI rendering, screen handlers, animations
- `com.pathmind.util`: Utilities, proxies, compatibility bridges
- `com.pathmind.mixin`: Minecraft integration via Mixin injection
- `com.pathmind.screen`: Custom screen implementations
- `com.pathmind.marketplace`: Marketplace integration



## Architecture

## System Overview

```text

```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| **PathmindMod** | Fabric/NeoForge mod initialization and event registration | `fabric/src/main/java/com/pathmind/PathmindMod.java` |
| **NodeGraph** | Visual graph rendering and editor UI interaction handling | `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` |
| **ExecutionManager** | Graph execution lifecycle, active node tracking, runtime state management | `common/src/main/java/com/pathmind/execution/ExecutionManager.java` |
| **Node** | Single node execution, parameter evaluation, Minecraft action dispatch | `common/src/main/java/com/pathmind/nodes/Node.java` |
| **GraphValidator** | Validates graph structure, detects circular dependencies, type checking | `common/src/main/java/com/pathmind/validation/GraphValidator.java` |
| **PresetManager** | Loads/saves node graph presets to `pathmind/presets/` directory | `common/src/main/java/com/pathmind/data/PresetManager.java` |
| **NodeGraphData** | Serializable data model for nodes and connections (JSON) | `common/src/main/java/com/pathmind/data/NodeGraphData.java` |
| **SettingsManager** | Manages user settings persisted to `pathmind/settings.json` | `common/src/main/java/com/pathmind/data/SettingsManager.java` |
| **PathmindScreens** | Screen creation and management for editor and marketplace | `common/src/main/java/com/pathmind/screen/PathmindScreens.java` |
| **MarketplaceService** | In-game marketplace browsing, preset publishing, authentication | `common/src/main/java/com/pathmind/marketplace/MarketplaceService.java` |

## Pattern Overview

- **Immediate-mode UI rendering**: NodeGraph renders and handles all user interaction per-frame via DrawContext
- **Event-driven execution**: Node execution driven by ExecutionManager state machine, registered event listeners react to Minecraft events
- **Abstraction bridges**: Utility classes bridge Minecraft version differences across 1.21-1.21.11 (mixin + compatibility packages)
- **No persistent game state**: Graphs only exist in player's preset files or memory during execution; no server/world data modifications
- **Stateful execution model**: ExecutionManager tracks execution chains, paused/active nodes, and runtime variables per graph run

## Layers

- **Purpose**: Renders the node graph editor and runtime overlays
- **Location**: `common/src/main/java/com/pathmind/ui/`
- **Contains**: NodeGraph rendering, overlay rendering (NodeErrorNotificationOverlay, ActiveNodeOverlay), theme system, animation, menus
- **Depends on**: ExecutionManager (read active node state), GraphValidator (validate before execution), SettingsManager (user preferences)
- **Used by**: PathmindScreens, PathmindClientMod
- **Purpose**: Executes node chains, manages runtime state, coordinates node behavior
- **Location**: `common/src/main/java/com/pathmind/execution/`
- **Contains**: ExecutionManager (lifecycle, state tracking), PathmindNavigator (pathfinding), BackgroundStartRunner (async execution)
- **Depends on**: Node (execute individual nodes), GraphValidator (validate before run), Minecraft client state
- **Used by**: UI layer (status display), Node layer (report execution results)
- **Purpose**: Defines all node types and their execution behavior
- **Location**: `common/src/main/java/com/pathmind/nodes/` (99 node definition files)
- **Contains**: Node class (base + all concrete node implementations), NodeType enum, NodeParameter/NodeConnection models, NodeBehaviorDefinition registry
- **Depends on**: Minecraft API (actions like mining, placing blocks, inventory manipulation)
- **Used by**: ExecutionManager, UI layer (node rendering + parameter UI)
- **Purpose**: Validates graph structure before execution
- **Location**: `common/src/main/java/com/pathmind/validation/`
- **Contains**: GraphValidator, GraphValidationResult, GraphValidationIssue
- **Depends on**: Node, NodeConnection, PresetManager
- **Used by**: ExecutionManager (validate before run), UI layer (display validation feedback)
- **Purpose**: Loads/saves graphs, presets, and user settings
- **Location**: `common/src/main/java/com/pathmind/data/`
- **Contains**: NodeGraphData (serializable model), NodeGraphPersistence (JSON I/O), PresetManager (file management), SettingsManager (user config)
- **Depends on**: GSON (JSON serialization), WorkspaceFileAccess (file I/O)
- **Used by**: All layers (load/save graphs), ExecutionManager (save execution snapshots)
- **Purpose**: Hide Minecraft version differences and mod compatibility concerns
- **Location**: `common/src/main/java/com/pathmind/util/`, `common/src/compat/`
- **Contains**: Bridge classes (DrawContextBridge, MatrixStackBridge, etc.), dependency checkers (BaritoneDependencyChecker), event trackers
- **Depends on**: Minecraft API (version-specific)
- **Used by**: All layers (UI, execution, node behavior)
- **Purpose**: Register events, provide mod initialization hooks
- **Location**: `fabric/src/main/java/com/pathmind/`, `neoforge/src/main/java/com/pathmind/`
- **Contains**: PathmindMod (implements ModInitializer), PathmindClientMod (Fabric/NeoForge-specific event registration)
- **Depends on**: Fabric API or NeoForge (loader, events)
- **Used by**: Minecraft game engine (mod lifecycle)

## Data Flow

### Primary Request Path (Graph Execution)

### Graph Editing Path

### Marketplace Preset Download

- **In-Memory Graph**: List<Node> + List<NodeConnection> held in NodeGraph during editing
- **Active Execution State**: ExecutionManager holds activeNode, activeNodes list, global runtime variables per execution chain
- **Persistent Settings**: SettingsManager holds user preferences (keybinds, UI theme, etc.) loaded from `settings.json`
- **Preset Registry**: PresetManager scans `pathmind/presets/` directory on startup and caches available presets

## Key Abstractions

- **Purpose**: Represents a single operation (move, mine, place, if/then, loop, etc.)
- **Examples**: `common/src/main/java/com/pathmind/nodes/Node.java` (base class + 99 implementations)
- **Pattern**: Concrete node types override execute() to perform Minecraft actions; parameters evaluated via getParameterValue()
- **Purpose**: Directed connection between node output and input
- **Examples**: `common/src/main/java/com/pathmind/nodes/NodeConnection.java`
- **Pattern**: Carries output socket ID, input socket ID, source/destination node references
- **Purpose**: Configurable input to a node (e.g., "Block to mine", "Duration")
- **Examples**: 99 ParameterDefinition classes in `common/src/main/java/com/pathmind/nodes/`
- **Pattern**: Each parameter type (Block, Item, Coordinate, Duration) has a definition class; Node stores evaluated values in a map
- **Purpose**: Singleton state machine managing graph execution
- **Examples**: `common/src/main/java/com/pathmind/execution/ExecutionManager.java`
- **Pattern**: Holds activeNode, isExecuting flag, runtime variables; advanced per-frame or in background thread; provides observers access to state
- **Purpose**: Serializable representation of a graph (JSON-friendly)
- **Examples**: `common/src/main/java/com/pathmind/data/NodeGraphData.java`
- **Pattern**: Contains List<NodeData> (flat node records) and List<ConnectionData> (flat connection records); deserialized to Node/NodeConnection objects on load

## Entry Points

- **Location**: `fabric/src/main/java/com/pathmind/PathmindMod.java`
- **Triggers**: Fabric loader calls onInitialize() at mod load time
- **Responsibilities**: Logs startup, validates MC version support, initializes common systems
- **Location**: `fabric/src/main/java/com/pathmind/PathmindClientMod.java`
- **Triggers**: ClientTickEvents, KeyInputEvents (registered with Fabric API)
- **Responsibilities**: Poll keybinds (open editor, play/stop graph), update HUD overlays
- **Location**: `common/src/main/java/com/pathmind/screen/PathmindScreens.java`
- **Triggers**: User presses keybind or main menu button
- **Responsibilities**: Create and show editor/marketplace screens, manage screen lifecycle
- **Location**: `common/src/main/java/com/pathmind/marketplace/MarketplaceAuthManager.java`
- **Triggers**: User logs in or marketplace API endpoints require auth
- **Responsibilities**: Store/retrieve session tokens from `pathmind/marketplace_auth.json`

## Architectural Constraints

- **Threading:** Single-threaded UI thread (Minecraft client); ExecutionManager supports background executor for async node chains (ForkJoinPool); graph validation runs synchronously on UI thread
- **Global state:** ExecutionManager is a singleton (volatile instance); NodeGraph holds mutable nodes/connections list; no circular node references allowed by validator
- **Circular imports:** Rare; most cycles avoided through abstraction layers (Node doesn't know about UI, UI reads ExecutionManager state)
- **Minecraft version support:** 1.21–1.21.11; achieved via mixin + compatibility packages (`common/src/compat/{base,mid,modern}/`) selectively applying version patches
- **Stateless node execution:** Each node.execute() call is atomic; ExecutionManager context (local variables, state) passed as parameters; no node-to-node shared mutable state except global variables
- **No server modifications:** Mod is client-side only; does not modify world save or install server-side code

## Anti-Patterns

### God Objects (Node class)

### String-based Parameter Lookup

### Immediate-Mode UI Rendering in NodeGraph

### ExecutionManager Singleton



## Project Skills

- `deploy` (`.claude/skills/deploy/SKILL.md`) — build and deploy the mod jars into the Modrinth "Pathmind" profile for manual in-game testing.

## Documentation

Project documentation lives in the Docusaurus site at `website/` (docs in `website/docs/`): project overview/requirements/roadmap under `website/docs/project/`, codebase and research docs under `website/docs/codebase/` and `website/docs/research/`, historical planning artifacts under `website/docs/archive/`. Run the site with `cd website && npm start`; build with `npm run build`. Keep durable docs in the site.

**Documentation policy — every major addition gets documented in the Docusaurus site, in the same session that lands it.** "Major" means: new infrastructure or tooling, new API surface or contracts, new subsystems, or behavior changes a user or addon developer would notice. Requirements:

- **Human-readable prose** written for a reader who wasn't there — what it is, why it exists, how to use it; not command dumps or commit-message summaries.
- **Diagrams where they genuinely help** (flows, state machines, architectures): Mermaid is enabled site-wide — use ```` ```mermaid ```` code blocks (`flowchart`, `stateDiagram-v2`, `sequenceDiagram`). Screenshots/images go in `website/static/img/`.
- **Right home:** how-to material in `docs/guides/`, architecture in `docs/codebase/`, and a dated one-line entry in `docs/project/roadmap.md` whenever a milestone item or infrastructure piece lands.
- **Update, don't duplicate:** if an existing page covers the area (e.g. the addon getting-started guide), extend it rather than creating a parallel page.
- Verify the site still builds after doc changes: `cd website && npm run build`.
