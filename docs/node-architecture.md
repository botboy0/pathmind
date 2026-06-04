# Pathmind Node Architecture

This document describes how the current Pathmind node system is wired together. It is intended as an orientation guide for refactors: where graph data lives, how presets become node graphs, how the editor mutates those graphs, and how execution walks them.

## High-Level Model

Pathmind is built around saved preset graphs. A preset is a JSON file managed by `PresetManager`, deserialized into `NodeGraphData`, rebuilt into live `Node` and `NodeConnection` objects by `NodeGraphPersistence`, edited by `NodeGraph`, and executed by `ExecutionManager`.

The shortest version of the lifecycle is:

1. `PresetManager` decides which preset file is active.
2. `NodeGraphPersistence` loads that file into `NodeGraphData`.
3. `NodeGraphPersistence.convertToNodes` and `convertToConnections` rebuild runtime objects.
4. `NodeGraph` owns editor state: nodes, connections, selections, camera, drag state, history, rendering caches, and active preset name.
5. The visual editor screen delegates most graph editing to `NodeGraph` and starts execution through `ExecutionManager`.
6. `ExecutionManager` snapshots, validates, clones, scopes, and walks executable branches.
7. Each executable `Node` delegates command behavior to `NodeCommandDispatcher`, which forwards to focused executor classes.

## Presets And Files

`PresetManager` is the workspace-level file manager. It creates the base `pathmind` directory, creates the `presets` directory, tracks the active preset in `active_preset.txt`, sanitizes preset names, lists JSON preset files, imports and exports presets, and keeps marketplace link metadata.

The important point is that "active preset" is global file state, while `NodeGraph` also keeps a local `activePreset` string for the editor instance. Screens update both when switching tabs.

Preset graph data is stored as `NodeGraphData`:

- `nodes`: serialized `NodeData` records.
- `connections`: serialized `ConnectionData` records.
- `customNodeDefinition`: generated metadata that lets a preset behave like a reusable custom/template node.

`NodeGraphPersistence.saveNodeGraphForPreset` writes live nodes and connections to the preset file returned by `PresetManager.getPresetPath`. During save it also rebuilds `customNodeDefinition`, including discovered custom-node inputs, outputs, signature, and version.

`NodeGraphPersistence.loadNodeGraphForPreset` reads the preset JSON. If disk load fails but the graph was recently saved in-process, it can fall back to an in-memory JSON cache keyed by preset name.

## Serialization Shape

`NodeGraphData.NodeData` is broader than just type and position. It stores:

- identity: `id`, `type`, and `mode`;
- layout: `x` and `y`;
- inline parameters: `ParameterData` entries;
- attachment links: sensor, action, and parameter host/child ids;
- special per-node state such as start number, message lines, sticky note size/text, template graph, template version, custom node flag, goto flags, and key sensor GUI behavior.

Connections are serialized separately as output node id/socket to input node id/socket.

Attachments are not normal flow connections. Sensors, child action nodes, and parameter nodes are restored after all nodes are created so id references can be resolved.

When loading, `NodeGraphPersistence.convertToNodes`:

1. creates a new `Node` for each serialized type;
2. restores the old id by reflection so saved connections still match;
3. restores mode and parameters;
4. restores special node-specific fields;
5. repairs legacy or missing parameters;
6. recalculates dimensions;
7. restores sensor/action/parameter attachments in separate passes.

`convertToConnections` then restores normal graph edges. It skips sensor-linked nodes and uses conflict replacement so an input socket or output socket does not accumulate multiple restored connections.

## Node Types, Metadata, And Traits

`NodeType` is the stable enum used in save data. It should remain the durable id for a node kind.

Metadata that does not need to live directly on the enum has been pushed into registries:

- `NodeTypeDefinition`: category, sidebar visibility, whether the type has parameters, and whether it requires Baritone or UI Utils.
- `NodeParameterDefinitionRegistry`: default parameters by node type and by `NodeMode`.
- `NodeTraitRegistry`: value traits, boolean sensor membership, parameter-node membership, parameter slot counts, parameter slot labels, and accepted parameter traits.
- `NodeCompatibility`: slot compatibility rules for sensor, action, and parameter attachments.
- `NodeBehaviorDefinitionRegistry`: newer behavior definitions for parameter/comparable behavior.

This is why the system can feel spread out. A node's behavior is not defined in one class:

- `NodeType` names the kind.
- `NodeTypeDefinition` categorizes it.
- `NodeParameterDefinitionRegistry` creates its editable fields.
- `NodeTraitRegistry` says what value it provides or accepts.
- `NodeCompatibility` decides whether it can attach to another node.
- `NodeCommandDispatcher` decides which executor runs it.
- The executor class contains most command-side behavior.

## Live Node Objects

`Node` is still the central compatibility shell. It represents one live editor/runtime node and owns stable APIs used by older call sites, but much of its state has been split out:

- `NodeRuntimeState`: transient execution and resolved runtime parameter data.
- `NodeLayoutState`: x/y/width/height and geometry.
- `NodeInteractionState`: selection, dragging, and interaction flags.
- `NodeAttachments`: parent/child attachment bookkeeping.

`Node` still handles a lot:

- rendering helpers and dimensions;
- socket counts and socket positions;
- mode and parameter initialization;
- parameter editing helpers;
- sensor/action/parameter attachment operations;
- validation before command execution;
- dispatch into command executors.

The current direction is for `Node.java` to keep shrinking. New behavior should usually be placed in the smallest owner that matches the concern.

## Graph Editing

`NodeGraph` is the visual editor's graph model. It owns:

- `List<Node> nodes`;
- `List<NodeConnection> connections`;
- selection state;
- camera/panning state;
- connection drag and connection cutting state;
- hierarchy/layout caches;
- context menu state;
- inline parameter editing state;
- history, clipboard, and persistence entry points.

The version-specific `PathmindVisualEditorScreen` classes handle screen integration and UI chrome, then delegate graph-specific actions to `NodeGraph`. For example, the screen creates `NodeGraph`, sets the active preset, routes mouse/key events, switches preset tabs, imports/exports, and calls `ExecutionManager` when the user presses play.

`NodeGraph` is therefore both a model and a large editor controller. It is not only a plain data structure.

## Attachments Versus Connections

There are two relationship systems:

1. Flow connections: `NodeConnection` edges between output and input sockets. Execution follows these.
2. Attachments: embedded child nodes inside a host slot.

Attachments cover:

- sensors attached to control/action nodes;
- action nodes attached inside control nodes;
- parameter nodes attached to parameter slots.

Attachments are persisted on `NodeData` as parent/child ids and restored by `NodeGraphPersistence.convertToNodes`. This distinction matters because attached nodes are visually and semantically part of the host node, while `NodeConnection` edges represent executable graph flow.

## Validation

`GraphValidator.validate` analyzes the current graph before execution and for editor feedback. It checks, among other things:

- at least one `START` node exists;
- required dependencies are present for node types that need Baritone or UI Utils;
- entry nodes are not dead;
- regular nodes are reachable from starts or event functions;
- input sockets do not have multiple incoming connections;
- required parameter slots are filled;
- event function and event call names resolve;
- run-preset/template/custom-node targets resolve;
- variable parameter usage has plausible inferred types.

`ExecutionManager` currently logs validation errors but still attempts execution so runtime errors can surface through overlays.

## Execution Lifecycle

Execution starts in `ExecutionManager`.

`executeGraph(nodes, connections)` is the "run all starts" path:

1. validate/log graph issues;
2. store workspace nodes/connections for replay and keybind starts;
3. find all `START` nodes;
4. cancel stale navigation commands;
5. filter connections;
6. create a `NodeGraphData` snapshot;
7. build isolated branch data for each start;
8. start global execution state;
9. create a `ChainController` for each branch;
10. call `runChain` for each branch.

`executeFromNode` and `executeBranch` are narrower launch paths used by node-level play controls and start-specific execution. They still snapshot, clone branch data, create a `ChainController`, seed runtime variables, and run a chain.

`ChainController` is the runtime scope for a branch. It tracks:

- root start node and execution id;
- cancellation;
- runtime variables and runtime lists;
- join-barrier input tracking;
- function handler templates;
- branch graph nodes/connections;
- parent scope for nested executions.

This scope is why `RUN_PRESET`, `CUSTOM_NODE`, and `TEMPLATE` can start nested graphs without simply merging all state into the top-level editor graph.

## Command Dispatch

`Node.execute` does preflight checks, verifies required parameter slots, checks empty parameters, moves to the Minecraft client thread when necessary, and then calls `NodeCommandDispatcher.execute`.

`NodeCommandDispatcher` is a type switch that maps node types to command families:

- `NodeInventoryCommandExecutor`: hotbar, drop, slot clicks, screen clicks, item movement.
- `NodeGuiCommandExecutor`: UI Utils integration and player GUI open/close.
- `NodeNavigationCommandExecutor`: Baritone/pathing commands and navigation guards.
- `NodeTextIoCommandExecutor`: message, book, and sign writing.
- `NodeFlowCommandExecutor`: waits, control flow, start/stop chain, run preset/custom/template, stop all.
- `NodeMovementCommandExecutor`: look, walk, jump, key press, crawl, crouch, sprint, fly.
- `NodeEntityActionCommandExecutor`: interact, trade, swing, armor/hand equip, break.
- `NodeWorldActionCommandExecutor`: use/place/build/explore/follow style world actions.
- `NodeCraftCommandExecutor`: crafting.
- `NodeCollectCommandExecutor`: collection.
- `NodeSensorCommandExecutor`: boolean sensor evaluation.
- `NodeVariableListCommandExecutor`: variables and runtime lists.

Adding a command node should generally mean adding metadata/parameters/traits plus one focused executor implementation, not adding more logic to `Node.java`.

## Presets As Custom Nodes

Saved presets can expose `customNodeDefinition`. `NodeGraphPersistence` discovers:

- inputs from eligible `VARIABLE` nodes and initialization patterns;
- outputs from graph output/value usage;
- a signature based on graph contents;
- a monotonically increasing version when the signature changes.

`RUN_PRESET` loads another preset and starts its `START` nodes externally.

`CUSTOM_NODE` and `TEMPLATE` also load preset graph data, but they wait for nested execution completion and use template/custom-node metadata to behave more like reusable subgraphs. Their node data can include `templateName`, `templateVersion`, `customNodeInstance`, and embedded `templateGraph`.

## Current Refactor Guidance

`Node.java` is the compatibility shell for editor state, serialization, and legacy call sites. New behavior should not be added there by default.

When adding or changing a node type, prefer the smallest owner:

1. Add stable type identity in `NodeType` only if a new persisted type is needed.
2. Add category/dependency/sidebar metadata in `NodeTypeDefinition`.
3. Add default fields in `NodeParameterDefinitionRegistry`.
4. Add value traits and slot requirements in `NodeTraitRegistry`.
5. Add slot compatibility in `NodeCompatibility` when attachment rules change.
6. Add parameter/comparable behavior through the behavior registries when possible.
7. Put command execution in the executor for that behavior family, or create a new executor if it is a distinct family.
8. Keep `Node.java` wrappers thin and behavior-free.
9. Update `NodeGraphPersistence` only when the node has new persisted state beyond ordinary parameters, mode, position, connections, and attachments.
10. Update `GraphValidator` when the new type introduces a new graph-level invariant.

The goal is for `Node.java` and `NodeGraph.java` to keep losing responsibilities over time while preserving old save data and public APIs.

## Practical Debugging Map

Use this map when tracing a bug:

- Preset not appearing or wrong file path: start in `PresetManager`.
- Graph JSON looks wrong: inspect `NodeGraphData` and `NodeGraphPersistence.buildNodeGraphData`.
- Saved graph loads incorrectly: inspect `NodeGraphPersistence.convertToNodes` and `convertToConnections`.
- Editor drag/drop, selection, rendering, connection creation, or inline edit issue: start in `NodeGraph`, then the version-specific `PathmindVisualEditorScreen`.
- Parameter defaults are wrong: inspect `NodeParameterDefinitionRegistry`.
- Parameter node cannot attach: inspect `NodeTraitRegistry` and `NodeCompatibility`.
- Node category/sidebar behavior is wrong: inspect `NodeTypeDefinition`.
- Validation warning/error is wrong: inspect `GraphValidator`.
- Node runs but does the wrong action: inspect `NodeCommandDispatcher`, then the executor for that node family.
- Nested preset/custom-node behavior is wrong: inspect `NodeFlowCommandExecutor.executeRunPresetNode`, `ExecutionManager.executeExternalBranch*`, and `NodeGraphPersistence.resolveCustomNodeDefinition`.
