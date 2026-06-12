<!-- refreshed: 2026-06-12 -->
# Architecture

**Analysis Date:** 2026-06-12

## System Overview

Pathmind is a visual node-based editor for building Minecraft automation workflows. The architecture follows a multi-layered design supporting cross-platform compilation (Fabric and NeoForge) with a unified core logic layer.

```text
┌───────────────────────────────────────────────────────────────────┐
│              Platform Entry Points (Fabric/NeoForge)              │
│  `fabric/src/main/java/com/pathmind/PathmindMod.java`             │
│  `neoforge/src/main/java/com/pathmind/PathmindMod.java`           │
└───────────────┬─────────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────────┐
│                    UI/Rendering Layer                            │
│  `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java`     │
│  `common/src/main/java/com/pathmind/screen/PathmindScreens.java` │
│  `common/src/main/java/com/pathmind/ui/overlay/`                 │
└───────────────┬─────────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────────┐
│           Execution & Node Behavior Layer                        │
│  `common/src/main/java/com/pathmind/execution/ExecutionManager.java` │
│  `common/src/main/java/com/pathmind/nodes/Node.java`             │
│  `common/src/main/java/com/pathmind/validation/GraphValidator.java` │
└───────────────┬─────────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────────┐
│         Data Management & Persistence Layer                      │
│  `common/src/main/java/com/pathmind/data/NodeGraphData.java`    │
│  `common/src/main/java/com/pathmind/data/PresetManager.java`    │
│  `common/src/main/java/com/pathmind/data/SettingsManager.java`  │
└───────────────┬─────────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────────┐
│         Minecraft API Abstraction & Bridge Layer                │
│  `common/src/main/java/com/pathmind/util/` (Bridge classes)    │
│  `common/src/main/java/com/pathmind/mixin/` (Mixins)            │
│  `common/src/compat/` (Version-specific compatibility)          │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌───────────────────────────────────────────────────────────────────┐
│         Minecraft Game State & Server                             │
│  Player inventory, world chunks, game events, command execution   │
└───────────────────────────────────────────────────────────────────┘
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

**Overall:** Multi-layered client-side mod with separation between UI presentation, execution logic, and data persistence. Uses Architectury API for cross-platform abstraction.

**Key Characteristics:**
- **Immediate-mode UI rendering**: NodeGraph renders and handles all user interaction per-frame via DrawContext
- **Event-driven execution**: Node execution driven by ExecutionManager state machine, registered event listeners react to Minecraft events
- **Abstraction bridges**: Utility classes bridge Minecraft version differences across 1.21-1.21.11 (mixin + compatibility packages)
- **No persistent game state**: Graphs only exist in player's preset files or memory during execution; no server/world data modifications
- **Stateful execution model**: ExecutionManager tracks execution chains, paused/active nodes, and runtime variables per graph run

## Layers

**Presentation Layer (UI):**
- **Purpose**: Renders the node graph editor and runtime overlays
- **Location**: `common/src/main/java/com/pathmind/ui/`
- **Contains**: NodeGraph rendering, overlay rendering (NodeErrorNotificationOverlay, ActiveNodeOverlay), theme system, animation, menus
- **Depends on**: ExecutionManager (read active node state), GraphValidator (validate before execution), SettingsManager (user preferences)
- **Used by**: PathmindScreens, PathmindClientMod

**Execution Layer:**
- **Purpose**: Executes node chains, manages runtime state, coordinates node behavior
- **Location**: `common/src/main/java/com/pathmind/execution/`
- **Contains**: ExecutionManager (lifecycle, state tracking), PathmindNavigator (pathfinding), BackgroundStartRunner (async execution)
- **Depends on**: Node (execute individual nodes), GraphValidator (validate before run), Minecraft client state
- **Used by**: UI layer (status display), Node layer (report execution results)

**Node Definition & Behavior Layer:**
- **Purpose**: Defines all node types and their execution behavior
- **Location**: `common/src/main/java/com/pathmind/nodes/` (99 node definition files)
- **Contains**: Node class (base + all concrete node implementations), NodeType enum, NodeParameter/NodeConnection models, NodeBehaviorDefinition registry
- **Depends on**: Minecraft API (actions like mining, placing blocks, inventory manipulation)
- **Used by**: ExecutionManager, UI layer (node rendering + parameter UI)

**Validation Layer:**
- **Purpose**: Validates graph structure before execution
- **Location**: `common/src/main/java/com/pathmind/validation/`
- **Contains**: GraphValidator, GraphValidationResult, GraphValidationIssue
- **Depends on**: Node, NodeConnection, PresetManager
- **Used by**: ExecutionManager (validate before run), UI layer (display validation feedback)

**Data Persistence Layer:**
- **Purpose**: Loads/saves graphs, presets, and user settings
- **Location**: `common/src/main/java/com/pathmind/data/`
- **Contains**: NodeGraphData (serializable model), NodeGraphPersistence (JSON I/O), PresetManager (file management), SettingsManager (user config)
- **Depends on**: GSON (JSON serialization), WorkspaceFileAccess (file I/O)
- **Used by**: All layers (load/save graphs), ExecutionManager (save execution snapshots)

**Abstraction/Bridge Layer:**
- **Purpose**: Hide Minecraft version differences and mod compatibility concerns
- **Location**: `common/src/main/java/com/pathmind/util/`, `common/src/compat/`
- **Contains**: Bridge classes (DrawContextBridge, MatrixStackBridge, etc.), dependency checkers (BaritoneDependencyChecker), event trackers
- **Depends on**: Minecraft API (version-specific)
- **Used by**: All layers (UI, execution, node behavior)

**Platform Layer:**
- **Purpose**: Register events, provide mod initialization hooks
- **Location**: `fabric/src/main/java/com/pathmind/`, `neoforge/src/main/java/com/pathmind/`
- **Contains**: PathmindMod (implements ModInitializer), PathmindClientMod (Fabric/NeoForge-specific event registration)
- **Depends on**: Fabric API or NeoForge (loader, events)
- **Used by**: Minecraft game engine (mod lifecycle)

## Data Flow

### Primary Request Path (Graph Execution)

1. **User presses play key** (`fabric/src/main/java/com/pathmind/PathmindClientMod.java` line ~KeyBinding listener)
2. **ExecutionManager.startExecution()** validates and initializes execution (`common/src/main/java/com/pathmind/execution/ExecutionManager.java` line ~180)
3. **ExecutionManager main loop** repeatedly advances active nodes (per-frame or async)
4. **Node.execute()** evaluates parameters, performs Minecraft action, returns result (`common/src/main/java/com/pathmind/nodes/Node.java` line ~200)
5. **ExecutionManager tracks completion** updates activeNode, processes outgoing connections
6. **Next node begins execution** or loop terminates
7. **PathmindHud.renderHudOverlays()** displays active node name and debug info (`common/src/main/java/com/pathmind/PathmindHud.java` line ~33)

### Graph Editing Path

1. **User opens editor** (keybind triggers PathmindScreens.openEditor)
2. **PresetManager loads active preset** from `pathmind/presets/{preset}.json`
3. **NodeGraphData deserialized** via GSON into Node and NodeConnection objects
4. **NodeGraph renders** nodes, connections, and UI controls per frame
5. **User drags/connects nodes** → NodeGraph.updateNodePositions(), NodeGraph.addConnection()
6. **User saves** → NodeGraphPersistence.saveGraph() writes JSON to file
7. **PresetManager.setActivePreset()** updates `pathmind/active_preset.txt`

### Marketplace Preset Download

1. **User browses marketplace** in MarketplaceService UI
2. **User imports preset** → MarketplaceService.downloadPreset()
3. **Preset written to** `pathmind/presets/{imported_name}.json`
4. **PresetManager detects new file** and adds to available presets list

**State Management:**
- **In-Memory Graph**: List<Node> + List<NodeConnection> held in NodeGraph during editing
- **Active Execution State**: ExecutionManager holds activeNode, activeNodes list, global runtime variables per execution chain
- **Persistent Settings**: SettingsManager holds user preferences (keybinds, UI theme, etc.) loaded from `settings.json`
- **Preset Registry**: PresetManager scans `pathmind/presets/` directory on startup and caches available presets

## Key Abstractions

**Node:**
- **Purpose**: Represents a single operation (move, mine, place, if/then, loop, etc.)
- **Examples**: `common/src/main/java/com/pathmind/nodes/Node.java` (base class + 99 implementations)
- **Pattern**: Concrete node types override execute() to perform Minecraft actions; parameters evaluated via getParameterValue()

**NodeConnection:**
- **Purpose**: Directed connection between node output and input
- **Examples**: `common/src/main/java/com/pathmind/nodes/NodeConnection.java`
- **Pattern**: Carries output socket ID, input socket ID, source/destination node references

**NodeParameter:**
- **Purpose**: Configurable input to a node (e.g., "Block to mine", "Duration")
- **Examples**: 99 ParameterDefinition classes in `common/src/main/java/com/pathmind/nodes/`
- **Pattern**: Each parameter type (Block, Item, Coordinate, Duration) has a definition class; Node stores evaluated values in a map

**ExecutionManager:**
- **Purpose**: Singleton state machine managing graph execution
- **Examples**: `common/src/main/java/com/pathmind/execution/ExecutionManager.java`
- **Pattern**: Holds activeNode, isExecuting flag, runtime variables; advanced per-frame or in background thread; provides observers access to state

**NodeGraphData:**
- **Purpose**: Serializable representation of a graph (JSON-friendly)
- **Examples**: `common/src/main/java/com/pathmind/data/NodeGraphData.java`
- **Pattern**: Contains List<NodeData> (flat node records) and List<ConnectionData> (flat connection records); deserialized to Node/NodeConnection objects on load

## Entry Points

**Mod Initialization:**
- **Location**: `fabric/src/main/java/com/pathmind/PathmindMod.java`
- **Triggers**: Fabric loader calls onInitialize() at mod load time
- **Responsibilities**: Logs startup, validates MC version support, initializes common systems

**Client Events:**
- **Location**: `fabric/src/main/java/com/pathmind/PathmindClientMod.java`
- **Triggers**: ClientTickEvents, KeyInputEvents (registered with Fabric API)
- **Responsibilities**: Poll keybinds (open editor, play/stop graph), update HUD overlays

**Game Screen Entry:**
- **Location**: `common/src/main/java/com/pathmind/screen/PathmindScreens.java`
- **Triggers**: User presses keybind or main menu button
- **Responsibilities**: Create and show editor/marketplace screens, manage screen lifecycle

**Marketplace Auth:**
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

**What happens:** The `Node` class in `common/src/main/java/com/pathmind/nodes/Node.java` is very large (1000+ lines) with execute() method containing inline implementations of all node behaviors (mining, placing, moving, inventory manipulation, etc.)

**Why it's wrong:** Hard to test individual node behaviors, high complexity makes maintenance difficult, encourages adding more logic directly to Node rather than extracting behavior

**Do this instead:** Extract node-type-specific execution into separate strategy classes (e.g., MineNodeExecutor, PlaceNodeExecutor); keep Node as a data container + router; delegate execute() to strategy

### String-based Parameter Lookup

**What happens:** Parameters accessed via getParameterValue(node, "ParameterName") lookups scattered throughout execution code. No type safety.

**Why it's wrong:** Refactoring parameter names breaks silently; easy to typo a parameter name; no IDE autocompletion or compile-time checking

**Do this instead:** Generate typed accessor methods for each node type; use enums for parameter keys; add parameter validation in NodeBehaviorDefinition

### Immediate-Mode UI Rendering in NodeGraph

**What happens:** `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` renders and handles all interaction in a single 3000+ line class with nested loops and state scattered across fields

**Why it's wrong:** Hard to reason about rendering order, input handling, and state updates; impossible to unit test UI logic; refactoring requires touching entire class

**Do this instead:** Separate rendering (draw nodes, connections, grid) from interaction (mouse clicks, drags, keyboard). Use a proper UI component tree or layout system. Extract node rendering to a NodeRenderer class

### ExecutionManager Singleton

**What happens:** `ExecutionManager.getInstance()` returns a volatile static instance; all execution state lives in this one object

**Why it's wrong:** Hard to run multiple graphs concurrently; difficult to test (singleton state persists between tests); tight coupling to singleton pattern

**Do this instead:** Make ExecutionManager non-static, hold in an ExecutionContext dependency-injected to UI/node layers; support multiple concurrent execution instances

---

*Architecture analysis: 2026-06-12*
