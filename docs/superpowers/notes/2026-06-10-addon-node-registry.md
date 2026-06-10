# Addon Node Registry Branch Note

## Intent

Start the public addon-node API by giving Pathmind and addon mods an ID-backed registry that can represent namespaced node definitions without adding `NodeType` enum constants.

## Scope

- Added public node metadata and registration types under `com.pathmind.nodes`.
- Bootstrapped existing built-in `NodeType` values into the new `PathmindNodes` registry by their durable `pathmind:*` persistence IDs.
- Extended `PathmindNodeDefinition` with registry-visible sidebar/dependency flags, provided value traits, and parameter slot metadata.
- Bridged built-in trait and parameter slot metadata from the existing enum-backed `NodeTraitRegistry` and `NodeTypeDefinition` sources.
- Wired `PathmindMod` to load Fabric entrypoints declared under `pathmind_nodes`.
- Exposed exact built-in translation and description keys from `NodeType` for registry bootstrap.
- Added focused tests for built-in lookup, direct addon registration, entrypoint-style addon registration, built-in metadata lookup, and addon trait/slot registration without enum constants.
- Started moving metadata consumers onto the registry by routing sidebar availability checks and resolved compatibility checks through `PathmindNodeDefinition`.
- Added addon-ID sidebar availability coverage for dependency flags and hidden helper nodes, plus addon-ID compatibility coverage for registered parameter-slot and provided-trait metadata.
- Added an ID-backed sidebar category entry view so visible addon definitions can appear in sidebar category metadata without adding `NodeType` enum constants, including render-ready translation key, description key, and color metadata.
- Added a metadata-backed sidebar search bridge so visible built-in and addon definitions can be discovered by ID, translation key, description key, or category metadata without adding `NodeType` enum constants.
- Bridged modern, mid, and legacy compat editor context search to consume the sidebar search metadata through `NodeSearchMapper`, while preserving built-in `NodeType` instantiation and keeping addon-only results non-instantiating.
- Bridged sidebar category row rendering to consume `Sidebar.SidebarNodeEntry` metadata through row facts, so visible addon entries can contribute deterministic row labels and indicator colors while staying non-instantiating.
- Added a saved-graph validation entry point that checks persisted `NodeGraphData` type IDs against the registry and reports unsupported saved node type IDs without instantiating addon live nodes.
- Bridged sidebar category visibility so non-`CUSTOM` categories with visible addon-only entries remain selectable even when no built-in `NodeType` entries are available after sidebar dependency filtering.
- Added a persistence-only live placeholder bridge for registered addon-only saved type IDs: `NodeGraphPersistence.convertToNodes(...)` now converts registered addon IDs without built-in `NodeType` values to explicit unsupported-addon placeholder nodes that preserve their original namespaced type ID on save.
- Added registry-only mode metadata: `PathmindNodeDefinition` now exposes `ModeOption` values, built-in definitions bridge existing `NodeMode` options/defaults, and addon definitions can register metadata-only mode option IDs without enum constants.
- Added an ID-keyed executor registry: `PathmindNodeExecutor`, `PathmindNodes.registerExecutor(Identifier, PathmindNodeExecutor)`, and `NodeCommandDispatcher` lookup by `Node#getTypeId()` let registered addon IDs route unsupported-addon placeholders to addon executor callbacks while built-ins stay registered under their existing persistence IDs.
- Bridged sidebar entry creation to unsupported-addon placeholders: registered addon sidebar rows now report `instantiating`, `Sidebar.createNodeFromEntry(...)` creates a `NodeType.STICKY_NOTE`-backed placeholder preserving the addon ID, and built-in sidebar entries still create normal built-in nodes.
- Bridged live placeholder parameter compatibility queries: `NodeCompatibility.canAttachToSlot(...)` and `Node#canAcceptParameterNode(...)` can consult registered `PathmindNodeDefinition` slot/trait metadata by `Node#getTypeId()` when either side is an unsupported-addon placeholder.

## Design

The public addon path follows the Fabric/REI pattern of named entrypoint contracts rather than requiring addons to mix into Pathmind internals. Internal mixins remain available to Pathmind or addons for their own implementation details, but the semantic extension point is `PathmindNodePlugin`.

The compat editor search loop now consumes registered metadata through `Sidebar.searchEntries(String)`, sidebar category row rendering now consumes registered entry metadata through `Sidebar.toRowFacts(SidebarNodeEntry)`, and sidebar category visibility now counts visible addon-only entries through the same dependency/sidebar gates. Saved-graph validation consumes persisted `NodeGraphData` type IDs through `GraphValidator.validate(NodeGraphData, String, boolean, boolean)` and reports unsupported IDs only when they are neither registered nor built-in. Saved graph live conversion can now represent registered addon-only IDs as unsupported placeholder nodes backed by `NodeType.STICKY_NOTE` while preserving the original namespaced type ID through save rebuilding. Registry definitions can also expose mode option metadata through `PathmindNodeDefinition.ModeOption`; built-ins bridge existing `NodeMode` metadata and addons can declare metadata-only mode IDs. Execution dispatch now resolves `PathmindNodeExecutor` callbacks by `Node#getTypeId()`, with built-ins registered under their `pathmind:*` IDs and registered addon placeholders able to route by their preserved addon ID. Sidebar entry creation can now create built-in nodes for built-in entries and unsupported-addon placeholders for addon-only entries; addon-only sidebar rows still leave `hoveredNodeType` empty, so compat preview remains built-in/custom-only. Live parameter compatibility queries can now consult registered slot/trait metadata by `Node#getTypeId()` when either the host or candidate is an unsupported-addon placeholder, while built-in live compatibility stays on the existing `NodeType` path when neither side is a placeholder. Addon-only editor search results deliberately return without adding graph nodes, addon mode options are not wired into live node mode selection or saved mode persistence, and actual addon parameter attachment/storage is not implemented. Current metadata/runtime consumers are `Sidebar.isNodeAvailable(Identifier)`, `Sidebar.hasNodesInCategory(NodeCategory)`, `Sidebar.getEntriesForCategory(NodeCategory)`, `Sidebar.toRowFacts(SidebarNodeEntry)`, `Sidebar.createNodeFromEntry(SidebarNodeEntry, int, int)`, `Sidebar.searchEntries(String)`, compat editor search mapping, saved-graph validation, saved-graph placeholder conversion, `PathmindNodeDefinition.modeOptions()`, `PathmindNodes.registerExecutor(Identifier, PathmindNodeExecutor)`, `NodeCommandDispatcher`, `NodeCompatibility.canAttachResolvedNode(Identifier, Identifier, int)`, `NodeCompatibility.canAttachToSlot(Node, Node, NodeSlotType.PARAMETER, int)` for placeholder-involved queries, and `Node#canAcceptParameterNode(Node, int)` through that compatibility bridge.

## Verification

- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` failed before implementation because `PathmindNodes` and `PathmindNodeDefinition` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` passed after implementation.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeDefinitionTest --tests com.pathmind.data.NodeGraphPersistenceTest` passed.
- `.\gradlew.bat test` passed.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` failed before the metadata bridge because `PathmindNodeDefinition` did not expose `draggableFromSidebar`, dependency flags, provided traits, or parameter slots.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` passed after adding definition-level metadata.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest --tests com.pathmind.nodes.NodeTypeDefinitionTest --tests com.pathmind.nodes.NodeSlotLayoutTest --tests com.pathmind.nodes.NodeCompatibilityTest --tests com.pathmind.data.NodeGraphPersistenceTest --tests com.pathmind.validation.GraphValidatorTest` passed.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` failed before sidebar registry bridging because `Sidebar.isNodeAvailable` only accepted `NodeType`.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` passed after adding registry-ID availability lookup.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeCompatibilityTest` failed before compatibility registry bridging because `NodeCompatibility.canAttachResolvedNode(Identifier, Identifier, int)` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeCompatibilityTest` passed after adding registry-backed resolved compatibility.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest --tests com.pathmind.nodes.NodeTypeDefinitionTest --tests com.pathmind.nodes.NodeSlotLayoutTest --tests com.pathmind.nodes.NodeCompatibilityTest --tests com.pathmind.nodes.NodeTypeSearchLabelTest --tests com.pathmind.data.NodeGraphPersistenceTest --tests com.pathmind.validation.GraphValidatorTest` passed.
- `.\gradlew.bat test` passed.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` failed before sidebar category entry metadata because `Sidebar.getEntriesForCategory(NodeCategory)` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` passed after adding the ID-backed sidebar entry view.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` failed before sidebar entry render metadata because `SidebarNodeEntry` did not expose `translationKey`, `descriptionKey`, or `color`.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` passed after adding render metadata to sidebar entries.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` failed before sidebar search metadata because `Sidebar.SidebarSearchResult` and `Sidebar.searchEntries(String)` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` passed after adding metadata-backed sidebar search.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest --tests com.pathmind.nodes.NodeTypeSearchLabelTest` passed.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` failed before compat editor search mapping because `NodeSearchEntry` and `NodeSearchMapper` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` passed after adding `NodeSearchMapper` and wiring compat editor search to sidebar metadata.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` failed before sidebar row rendering metadata because `Sidebar.SidebarRowFacts` and `Sidebar.toRowFacts(SidebarNodeEntry)` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` passed after adding row facts and building sidebar rows from `SidebarNodeEntry` metadata.
- `.\gradlew.bat test --tests com.pathmind.validation.GraphValidatorTest` failed before saved-graph validation because `GraphValidator.validate(NodeGraphData, String, boolean, boolean)` did not exist.
- `.\gradlew.bat test --tests com.pathmind.validation.GraphValidatorTest` passed after adding metadata-only saved type ID checks.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest --tests com.pathmind.data.NodeGraphPersistenceTest --tests com.pathmind.validation.GraphValidatorTest` passed after the saved-graph validation bridge.
- `npm run build` from `docs-trace` passed and generated static files in `build`.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` failed before sidebar addon category visibility because `Sidebar.hasNodesInCategory` ignored visible addon-only entries when the built-in category list was empty.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` passed after counting visible addon-only entries for non-`CUSTOM` categories.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest --tests com.pathmind.nodes.NodeTypeSearchLabelTest --tests com.pathmind.validation.GraphValidatorTest` passed after the sidebar category visibility bridge.
- `.\gradlew.bat assemble "-Pmc_version=1.21"` passed for the legacy compat source set.
- `.\gradlew.bat assemble "-Pmc_version=1.21.11"` passed for the modern compat source set.
- `.\gradlew.bat assemble "-Pmc_version=1.21.10"` compiled the mid compat source set but failed later in `remapJar` because `libraries.minecraft.net` DNS resolution failed while downloading `jtracy-1.0.36-natives-windows.jar`.
- `.\gradlew.bat compileJava "-Pmc_version=1.21.10"` passed for the mid compat source set.
- `.\gradlew.bat test --tests com.pathmind.data.NodeGraphPersistenceTest` failed before placeholder conversion with `NodeGraphPersistenceTest > convertToNodesCreatesPlaceholderForRegisteredAddonTypesOnly()` failing at the missing restored placeholder assertion.
- `.\gradlew.bat test --tests com.pathmind.data.NodeGraphPersistenceTest` passed after adding registered-addon placeholder conversion and save preservation.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest --tests com.pathmind.data.NodeGraphPersistenceTest --tests com.pathmind.validation.GraphValidatorTest` passed after the placeholder conversion bridge.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` failed before mode metadata implementation because `PathmindNodeDefinition.ModeOption`, `modeOptions()`, and builder `modeOption(...)` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` passed after adding registry-only mode metadata.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest --tests com.pathmind.nodes.NodeTypeDefinitionTest --tests com.pathmind.data.NodeGraphPersistenceTest --tests com.pathmind.validation.GraphValidatorTest` passed after the mode metadata bridge.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeCommandDispatcherTest` failed before executor routing implementation because `PathmindNodes.registerExecutor(Identifier, ...)` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeCommandDispatcherTest` passed after adding `PathmindNodeExecutor`, executor registration, built-in executor registrations, and ID-keyed dispatcher routing.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeCommandDispatcherTest --tests com.pathmind.nodes.PathmindNodesTest --tests com.pathmind.data.NodeGraphPersistenceTest --tests com.pathmind.validation.GraphValidatorTest` passed after the executor routing bridge.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` failed before sidebar placeholder creation because `Sidebar.createNodeFromEntry(SidebarNodeEntry, int, int)` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest` passed after adding sidebar entry placeholder creation and marking addon row facts instantiating.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeSearchLabelTest --tests com.pathmind.nodes.NodeCommandDispatcherTest --tests com.pathmind.data.NodeGraphPersistenceTest --tests com.pathmind.validation.GraphValidatorTest` passed after the sidebar placeholder creation bridge.
- `git status --short` produced no output before the placeholder compatibility slice, confirming a clean tracked tree.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeCompatibilityTest` failed before live placeholder compatibility implementation with `NodeCompatibilityTest > addonPlaceholderParameterCompatibilityUsesRegisteredDefinitions()` failing at the direct `NodeCompatibility.canAttachToSlot(...)` assertion because the placeholder host still used sticky-note/built-in metadata.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeCompatibilityTest` passed after adding the placeholder metadata bridge.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeCompatibilityTest --tests com.pathmind.nodes.NodeTypeSearchLabelTest --tests com.pathmind.data.NodeGraphPersistenceTest --tests com.pathmind.validation.GraphValidatorTest` passed after the placeholder metadata bridge.
- `npm run build` from `docs-trace` passed with `[SUCCESS] Generated static files in "build".`
- `git status --short` before commit showed only `docs/superpowers/notes/2026-06-10-addon-node-registry.md`, `src/main/java/com/pathmind/nodes/NodeCompatibility.java`, and `src/test/java/com/pathmind/nodes/NodeCompatibilityTest.java` modified among tracked files.

## Review Risks

- The registry now captures metadata and minimal executor callbacks; addon node creation and many runtime call-site lookups are still enum-backed in later call sites.
- Compat editor search can show addon metadata, but selecting an addon-only result is intentionally a no-op until graph node structure supports external IDs.
- `Sidebar` can now answer availability for addon IDs, expose addon IDs plus render/search metadata, keep categories visible when addon-only entries are the only visible entries, and create unsupported-addon placeholders from addon-only sidebar rows. Editor search addon selection remains non-instantiating.
- Saved-graph validation can report unsupported persisted addon IDs, registered addon-only saved IDs can reload as unsupported placeholders, registry definitions can expose addon mode metadata, registered addon executors can route by preserved placeholder type ID, sidebar addon rows can instantiate placeholders, and placeholder-involved parameter compatibility queries can consult registered trait/slot metadata, but Pathmind still does not validate addon-specific sockets, parameters, selected modes, or richer execution behavior.
- `NodeCompatibility` can now compare registered resolved IDs by trait metadata and can answer live placeholder-involved parameter compatibility queries by preserved type ID, but actual parameter attachment/storage and slot rendering still depend on built-in `NodeType` shapes.
- Built-in required-slot metadata is bridged from `NodeTraitRegistry.isParameterSlotAlwaysRequired`; dynamic runtime rules such as placement target requirements remain in `Node`.
- Duplicate registration throws immediately, which is intentional for now but may need richer diagnostics once third-party addons are loaded in the wild.
