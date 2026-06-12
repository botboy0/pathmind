# Codebase Structure

**Analysis Date:** 2026-06-12

## Directory Layout

```
pathmind/
├── buildSrc/                           # Gradle build logic and custom tasks
├── fabric/                             # Fabric loader–specific implementation
│   ├── build.gradle.kts                # Fabric build config
│   └── src/main/java/com/pathmind/     # Platform entry points (PathmindMod, PathmindClientMod)
├── neoforge/                           # NeoForge loader–specific implementation
│   ├── build.gradle.kts                # NeoForge build config
│   └── src/main/java/com/pathmind/     # Platform entry points (NeoForge adapters)
├── common/                             # Shared code (both Fabric and NeoForge)
│   ├── build.gradle.kts                # Common project build config
│   ├── src/main/java/com/pathmind/     # Core implementation (507 total .java files)
│   │   ├── PathmindCommon.java         # Shared constants (MOD_ID, logger)
│   │   ├── PathmindHud.java            # HUD overlay manager
│   │   ├── data/                       # Data persistence and file I/O
│   │   ├── execution/                  # Graph execution engine
│   │   ├── marketplace/                # Marketplace browser and publishing
│   │   ├── mixin/                      # Mixin hooks for Minecraft patches
│   │   ├── nodes/                      # Node definitions (99 node types)
│   │   ├── screen/                     # Screen/GUI management
│   │   ├── ui/                         # UI rendering and components
│   │   ├── util/                       # Utilities and bridges
│   │   └── validation/                 # Graph validation logic
│   ├── src/compat/                     # Version-specific compatibility code
│   │   ├── legacy/                     # 1.21 compatibility
│   │   ├── mid/                        # 1.21.1-1.21.7 compatibility
│   │   └── modern/                     # 1.21.8+ compatibility
│   └── src/test/                       # Test code (if any)
├── com/pathmind/nodes/                 # Compiled node definitions (generated at build)
├── docs/                               # User and developer documentation
├── mappings/                           # Yarn mappings cache
├── gradle/                             # Gradle wrapper and plugins
├── .planning/codebase/                 # Architecture documentation (this file)
├── .claude/                            # Claude skills and agents
├── .codex/                             # Codex index
├── build.gradle.kts                    # Root Gradle build (multi-module config, version specs)
├── settings.gradle.kts                 # Gradle settings (project tree)
├── gradle.properties                   # Gradle properties (version, group, etc.)
├── README.md                           # User-facing documentation
├── RELEASE_GATE.md                     # Release readiness checklist
└── LICENSE.txt                         # Project license
```

## Directory Purposes

**buildSrc/:**
- **Purpose:** Contains Gradle plugin and build logic for the multi-version build system
- **Contains:** Custom tasks (checkArchitecturyVersions), build helpers
- **Key files:** `buildSrc/src/main/java/com/pathmind/build/`

**fabric/**
- **Purpose:** Fabric loader entry point and Fabric-specific code
- **Contains:** PathmindMod (ModInitializer), PathmindClientMod (event registration with Fabric API)
- **Key files:** `fabric/src/main/java/com/pathmind/PathmindMod.java`, `fabric/src/main/java/com/pathmind/PathmindClientMod.java`

**neoforge/**
- **Purpose:** NeoForge loader entry point and NeoForge-specific code
- **Contains:** PathmindMod (ForgeEventHandler), NeoForge event listeners
- **Key files:** `neoforge/src/main/java/com/pathmind/PathmindMod.java`

**common/src/main/java/com/pathmind/data/**
- **Purpose:** Save/load graphs, presets, and user settings
- **Contains:** NodeGraphData (JSON model), PresetManager (file I/O), SettingsManager (user config), WorkspaceFileAccess (directory management)
- **Key files:** 
  - `NodeGraphData.java` - Serializable graph representation
  - `NodeGraphPersistence.java` - JSON serialize/deserialize
  - `PresetManager.java` - Load/save presets from `pathmind/presets/`
  - `SettingsManager.java` - Load/save `pathmind/settings.json`

**common/src/main/java/com/pathmind/execution/**
- **Purpose:** Execute node graphs, track execution state
- **Contains:** ExecutionManager (lifecycle + state), PathmindNavigator (pathfinding), BackgroundStartRunner (async execution)
- **Key files:** 
  - `ExecutionManager.java` - Main execution engine; tracks activeNode, isExecuting, runtime variables
  - `PathmindNavigator.java` - Movement and pathfinding
  - `PreciseCompletionTracker.java` - Completion detection

**common/src/main/java/com/pathmind/nodes/**
- **Purpose:** Node type definitions and node execution logic (99 files)
- **Contains:** 
  - `Node.java` - Base node class; all execution logic
  - 99 concrete node types and parameter definitions (AmountParameterDefinition, BlockParameterDefinition, etc.)
  - `NodeType.java` - Enum of all node types
  - `NodeBehaviorDefinition.java` / `NodeBehaviorDefinitionRegistry.java` - Node metadata
  - `NodeConnection.java` - Graph edge representation
  - `NodeParameter.java` - Parameter model
- **Key files:** 
  - `Node.java` (1000+ lines) - Execute individual nodes
  - `NodeType.java` - Node type registry
  - `NodeConnection.java` - Edge representation

**common/src/main/java/com/pathmind/screen/**
- **Purpose:** Minecraft screen creation and lifecycle
- **Contains:** PathmindScreens (factory), GuiTextureRenderer (asset rendering), PathmindCursor (cursor management)
- **Key files:** 
  - `PathmindScreens.java` - Creates editor, marketplace, settings screens
  - `GuiTextureRenderer.java` - Renders UI textures

**common/src/main/java/com/pathmind/ui/**
- **Purpose:** All visual rendering and user interaction
- **Contains:**
  - `ui/graph/NodeGraph.java` - Main editor rendering and interaction (3000+ lines)
  - `ui/animation/` - AnimatedValue, AnimationHelper, HoverAnimator
  - `ui/control/` - Custom UI controls (StyledButton, PathmindTextField, ToggleSwitch)
  - `ui/menu/` - Context menus (ContextMenu, NodeContextMenu)
  - `ui/overlay/` - HUD overlays (ActiveNodeOverlay, NodeErrorNotificationOverlay, VariablesOverlay)
  - `ui/sidebar/` - Editor sidebar panels
  - `ui/theme/` - UI styling and colors
  - `ui/tooltip/` - Tooltip rendering
- **Key files:** 
  - `ui/graph/NodeGraph.java` - Editor viewport rendering + interaction
  - `ui/overlay/ActiveNodeOverlay.java` - Runtime HUD display

**common/src/main/java/com/pathmind/marketplace/**
- **Purpose:** In-game marketplace for browsing and sharing presets
- **Contains:** MarketplaceService, MarketplaceAuthManager, MarketplacePreset, MarketplaceRateLimitManager
- **Key files:** 
  - `MarketplaceService.java` - API client + UI integration
  - `MarketplaceAuthManager.java` - Session management (`pathmind/marketplace_auth.json`)

**common/src/main/java/com/pathmind/util/**
- **Purpose:** Utilities, bridges, compatibility shims
- **Contains:**
  - Bridge classes (DrawContextBridge, MatrixStackBridge, etc.) - Hide MC version API differences
  - Dependency checkers (BaritoneDependencyChecker, UiUtilsDependencyChecker)
  - Event trackers (FabricEventTracker, ChatMessageTracker)
  - Helpers (TextRenderUtil, DropdownLayoutHelper, BlockSelection, EntityStateOptions)
- **Key files:** 
  - `DrawContextBridge.java` - Abstract DrawContext API changes
  - `MatrixStackBridge.java` - Abstract matrix stack API changes
  - `BaritoneApiProxy.java` - Optional Baritone integration

**common/src/main/java/com/pathmind/validation/**
- **Purpose:** Validate graphs before execution
- **Contains:** GraphValidator (main logic), GraphValidationResult, GraphValidationIssue, GraphValidationSeverity
- **Key files:** 
  - `GraphValidator.java` - Detects missing start nodes, circular deps, invalid connections

**common/src/main/java/com/pathmind/mixin/**
- **Purpose:** Inject behavior into Minecraft classes via Mixin
- **Contains:** Mixins for PlayerEntity, ClientPlayerInteractionManager, etc.
- **Key files:** `*.java` - One mixin per patched Minecraft class

**common/src/compat/***
- **Purpose:** Version-specific patches for MC 1.21–1.21.11
- **Contains:**
  - `base/` - Patches for 1.21
  - `mid/` - Patches for 1.21.1–1.21.7
  - `modern/` - Patches for 1.21.8+
- **Key files:** Compatible versions of classes referenced by main source

**docs/**
- **Purpose:** User guides, tutorials, architecture diagrams
- **Contains:** Markdown documentation for users and developers

## Key File Locations

**Entry Points:**
- `fabric/src/main/java/com/pathmind/PathmindMod.java` - Fabric mod initialization (ModInitializer)
- `fabric/src/main/java/com/pathmind/PathmindClientMod.java` - Fabric client-side event registration
- `neoforge/src/main/java/com/pathmind/PathmindMod.java` - NeoForge mod initialization

**Configuration:**
- `build.gradle.kts` - Root build config; defines MC versions, dependencies, publication
- `gradle.properties` - Project version, maven group
- `settings.gradle.kts` - Gradle project tree (fabric, neoforge, common subprojects)

**Core Logic:**
- `common/src/main/java/com/pathmind/execution/ExecutionManager.java` - Graph execution state machine
- `common/src/main/java/com/pathmind/nodes/Node.java` - Node base + all execution logic
- `common/src/main/java/com/pathmind/data/NodeGraphData.java` - Graph serialization model
- `common/src/main/java/com/pathmind/validation/GraphValidator.java` - Graph validation rules
- `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` - Editor viewport rendering

**Testing:**
- `common/src/test/` - Unit tests (if present)

## Naming Conventions

**Files:**
- **Classes:** PascalCase (e.g., `ExecutionManager.java`, `NodeGraphData.java`)
- **Interfaces:** PascalCase with "able" or "able" suffix (e.g., `RelativeInputSupport`, `NodeCompatibility`)
- **Enums:** PascalCase (e.g., `NodeType.java`, `NodeCategory.java`)
- **Utilities:** PascalCase, often with "Helper" suffix (e.g., `DropdownLayoutHelper.java`, `TextRenderUtil.java`)
- **Data classes:** PascalCase with "Data" suffix (e.g., `NodeData.java`, `ConnectionData.java`)

**Directories:**
- **Feature packages:** Lowercase (e.g., `ui`, `nodes`, `execution`, `marketplace`)
- **Sub-packages:** Lowercase descriptive (e.g., `ui/overlay`, `ui/animation`, `ui/control`)
- **Version-specific:** `compat/{base,mid,modern}/` for MC version-specific code

**Java Code:**
- **Methods:** camelCase (e.g., getParameterValue(), executeNode())
- **Fields:** camelCase (e.g., activeNode, isExecuting)
- **Constants:** UPPER_SNAKE_CASE (e.g., CONNECTION_DOT_SPACING, MOD_ID)
- **Local variables:** camelCase (e.g., startNodes, nodeGraph)

## Where to Add New Code

**New Node Type:**
1. Create a new *NodeTypeName* subclass of `Node.java` **or** add logic to `Node.java` execute() method
2. Add to `com/pathmind/nodes/NodeType.java` enum
3. Add parameter definitions (if needed) in `com/pathmind/nodes/` (e.g., `AmountParameterDefinition.java`)
4. Register in `NodeBehaviorDefinitionRegistry.java`
5. Add translations to resource files (lang files if localization needed)

**New UI Component:**
- Add to `common/src/main/java/com/pathmind/ui/control/` (reusable components like buttons, text fields)
- Or extend `NodeGraph.java` directly if editor-specific (node rendering, input handling)

**New Overlay/HUD:**
- Create class extending appropriate base in `common/src/main/java/com/pathmind/ui/overlay/`
- Register with `PathmindHud.initialize()` in `PathmindHud.java`
- Hook into render event in platform layer (`PathmindClientMod.java`)

**New Marketplace Feature:**
- Add to `common/src/main/java/com/pathmind/marketplace/` (MarketplaceService, MarketplacePreset, etc.)
- Integrate screen creation in `PathmindScreens.java`

**New Data Type:**
- Add serialization class to `common/src/main/java/com/pathmind/data/` (model + persistence)
- Add parameter definition if used in nodes (e.g., `NewTypeParameterDefinition.java`)

**Version Compatibility Shim:**
- Create abstract bridge class in `common/src/main/java/com/pathmind/util/` (e.g., `DrawContextBridge.java`)
- Implement version-specific logic in `common/src/compat/{base,mid,modern}/java/com/pathmind/util/`
- Call abstract method from main code

**Utility/Helper:**
- Add to `common/src/main/java/com/pathmind/util/` with descriptive name (e.g., `DropdownLayoutHelper.java`)
- Document public API clearly

## Special Directories

**buildSrc/:**
- **Purpose:** Custom Gradle tasks and build configuration
- **Generated:** No
- **Committed:** Yes

**.planning/codebase/:**
- **Purpose:** Architecture and structure documentation
- **Generated:** No
- **Committed:** Yes

**com/pathmind/nodes/:**
- **Purpose:** Compiled node classes (.class files)
- **Generated:** Yes (at build time)
- **Committed:** No (in .gitignore)

**build/, fabric/build/, neoforge/build/, common/build/:**
- **Purpose:** Build artifacts (compiled jars, intermediates)
- **Generated:** Yes (gradle build)
- **Committed:** No (in .gitignore)

**.gradle/:**
- **Purpose:** Gradle cache and metadata
- **Generated:** Yes
- **Committed:** No (in .gitignore)

**gradle/ (wrapper):**
- **Purpose:** Gradle wrapper binaries
- **Generated:** No
- **Committed:** Yes (gradlew, gradlew.bat, gradle/wrapper/)

---

*Structure analysis: 2026-06-12*
