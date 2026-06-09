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
- Added an ID-backed sidebar category entry view so visible addon definitions can appear in sidebar category metadata without adding `NodeType` enum constants.

## Design

The public addon path follows the Fabric/REI pattern of named entrypoint contracts rather than requiring addons to mix into Pathmind internals. Internal mixins remain available to Pathmind or addons for their own implementation details, but the semantic extension point is `PathmindNodePlugin`.

This slice does not update editor search, live graph creation, or execution dispatch. It starts at call sites that can consume registered metadata without changing node creation semantics: `Sidebar.isNodeAvailable(Identifier)`, `Sidebar.getEntriesForCategory(NodeCategory)`, and `NodeCompatibility.canAttachResolvedNode(Identifier, Identifier, int)`.

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

## Review Risks

- The registry currently captures metadata only; addon node creation, full editor listing, execution routing, and many runtime call-site lookups are still enum-backed in later call sites.
- `Sidebar` can now answer availability for addon IDs and expose addon IDs in category entry metadata, but dragging/instantiating sidebar rows into the live graph still depends on built-in `NodeType`.
- `NodeCompatibility` can now compare registered resolved IDs by trait metadata, but live graph attachment still depends on `Node` instances backed by `NodeType`.
- Built-in required-slot metadata is bridged from `NodeTraitRegistry.isParameterSlotAlwaysRequired`; dynamic runtime rules such as placement target requirements remain in `Node`.
- Duplicate registration throws immediately, which is intentional for now but may need richer diagnostics once third-party addons are loaded in the wild.
