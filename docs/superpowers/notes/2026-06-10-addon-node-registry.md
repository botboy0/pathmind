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

## Design

The public addon path follows the Fabric/REI pattern of named entrypoint contracts rather than requiring addons to mix into Pathmind internals. Internal mixins remain available to Pathmind or addons for their own implementation details, but the semantic extension point is `PathmindNodePlugin`.

The compat editor search loop now consumes registered metadata through `Sidebar.searchEntries(String)`, and sidebar category row rendering now consumes registered entry metadata through `Sidebar.toRowFacts(SidebarNodeEntry)`. Live graph creation and execution dispatch remain built-in-only. Addon-only editor search results deliberately return without adding graph nodes, and addon-only sidebar rows deliberately leave `hoveredNodeType` empty so clicking or dragging them cannot create or preview graph nodes until the graph structure can represent external node IDs. Current metadata consumers are `Sidebar.isNodeAvailable(Identifier)`, `Sidebar.getEntriesForCategory(NodeCategory)`, `Sidebar.toRowFacts(SidebarNodeEntry)`, `Sidebar.searchEntries(String)`, compat editor search mapping, and `NodeCompatibility.canAttachResolvedNode(Identifier, Identifier, int)`.

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
- `.\gradlew.bat assemble "-Pmc_version=1.21"` passed for the legacy compat source set.
- `.\gradlew.bat assemble "-Pmc_version=1.21.11"` passed for the modern compat source set.
- `.\gradlew.bat assemble "-Pmc_version=1.21.10"` compiled the mid compat source set but failed later in `remapJar` because `libraries.minecraft.net` DNS resolution failed while downloading `jtracy-1.0.36-natives-windows.jar`.
- `.\gradlew.bat compileJava "-Pmc_version=1.21.10"` passed for the mid compat source set.

## Review Risks

- The registry currently captures metadata only; addon node creation, execution routing, and many runtime call-site lookups are still enum-backed in later call sites.
- Compat editor search can show addon metadata, but selecting an addon-only result is intentionally a no-op until graph node structure supports external IDs.
- `Sidebar` can now answer availability for addon IDs and expose addon IDs plus render/search metadata. Addon-only rows can be visible and hoverable, but dragging/instantiating sidebar rows into the live graph still depends on built-in `NodeType` or custom-node entries.
- `NodeCompatibility` can now compare registered resolved IDs by trait metadata, but live graph attachment still depends on `Node` instances backed by `NodeType`.
- Built-in required-slot metadata is bridged from `NodeTraitRegistry.isParameterSlotAlwaysRequired`; dynamic runtime rules such as placement target requirements remain in `Node`.
- Duplicate registration throws immediately, which is intentional for now but may need richer diagnostics once third-party addons are loaded in the wild.
