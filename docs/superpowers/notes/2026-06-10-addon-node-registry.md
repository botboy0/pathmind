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

## Design

The public addon path follows the Fabric/REI pattern of named entrypoint contracts rather than requiring addons to mix into Pathmind internals. Internal mixins remain available to Pathmind or addons for their own implementation details, but the semantic extension point is `PathmindNodePlugin`.

This slice intentionally does not update editor/sidebar enumeration or execution dispatch. Those will move to the registry in later slices after this API foundation and metadata bridge are in place.

## Verification

- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` failed before implementation because `PathmindNodes` and `PathmindNodeDefinition` did not exist.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` passed after implementation.
- `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeDefinitionTest --tests com.pathmind.data.NodeGraphPersistenceTest` passed.
- `.\gradlew.bat test` passed.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` failed before the metadata bridge because `PathmindNodeDefinition` did not expose `draggableFromSidebar`, dependency flags, provided traits, or parameter slots.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest` passed after adding definition-level metadata.
- `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest --tests com.pathmind.nodes.NodeTypeDefinitionTest --tests com.pathmind.nodes.NodeSlotLayoutTest --tests com.pathmind.nodes.NodeCompatibilityTest --tests com.pathmind.data.NodeGraphPersistenceTest --tests com.pathmind.validation.GraphValidatorTest` passed.

## Review Risks

- The registry currently captures metadata only; addon node creation, editor listing, execution routing, and most call-site lookups are still enum-backed in later call sites.
- Built-in required-slot metadata is bridged from `NodeTraitRegistry.isParameterSlotAlwaysRequired`; dynamic runtime rules such as placement target requirements remain in `Node`.
- Duplicate registration throws immediately, which is intentional for now but may need richer diagnostics once third-party addons are loaded in the wild.
