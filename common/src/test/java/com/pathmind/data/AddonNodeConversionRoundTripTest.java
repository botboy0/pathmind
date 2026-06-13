package com.pathmind.data;

import com.pathmind.api.addon.AddonNodeCategory;
import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeDefinition;
import com.pathmind.api.addon.AddonNodeSerializer;
import com.pathmind.api.addon.NodeResult;
import com.pathmind.api.addon.NodeTypeRegistrar;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.NodeTypeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-shaped regression tests for ADDON node conversion paths (CR-02, CR-03).
 *
 * <p>These tests exercise the actual conversion sites used by the editor and execution paths
 * — specifically {@link NodeGraphPersistence#convertToNodes} and
 * {@link AddonNodeDataCopy#copyAddonFieldsToNodeData}/{@link AddonNodeDataCopy#restoreAddonFieldsToNode}
 * — rather than the serializer interface in isolation.
 *
 * <p>This is the regression gate the phase-01 verifier identified as missing: an automated test
 * that would have caught CR-02 (addonTypeId dropped on editor-load/clipboard) and CR-03
 * (addonTypeId dropped on createGraphSnapshot/convertToNodes).
 */
class AddonNodeConversionRoundTripTest {

    private static final String TEST_ADDON_ID = "test_mod:script";
    // Use a deliberately-unregistered ID for the missing-addon (D-09) test path
    private static final String UNREGISTERED_ADDON_ID = "test_mod:unknown_addon_type";

    /**
     * Serializer shared by the round-trip tests. Mirrors the one in AddonNodePersistenceTest
     * so the install-once guard can tolerate whichever test class runs first.
     */
    private static final AddonNodeSerializer TEST_SERIALIZER = new AddonNodeSerializer() {
        @Override
        public Map<String, Object> serialize(AddonNodeContext ctx) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("_schema_version", 1);
            map.put("script", ctx.getScriptText() != null ? ctx.getScriptText() : "");
            return map;
        }

        @Override
        public void deserialize(AddonNodeContext ctx, Map<String, Object> fields) {
            if (fields == null) {
                return;
            }
            // GSON Double-erasure handling (Pitfall 4): use ((Number)).intValue()
            Object versionObj = fields.get("_schema_version");
            if (versionObj instanceof Number) {
                int version = ((Number) versionObj).intValue();
                // version-aware migration would go here
                assertTrue(version >= 1, "_schema_version must be >= 1");
            }
            Object scriptObj = fields.get("script");
            if (scriptObj != null) {
                ctx.setScriptText(scriptObj.toString());
            }
        }
    };

    @BeforeAll
    static void installSyntheticRegistry() {
        // Guard: install-once singleton — tolerate prior installation from AddonNodePersistenceTest
        // or from a parallel test run (NodeTypeRegistry is JVM-wide install-once).
        if (!NodeTypeRegistry.INSTANCE.hasType(TEST_ADDON_ID)) {
            try {
                NodeTypeRegistrar registrar = new NodeTypeRegistrar();
                AddonNodeCategory category = new AddonNodeCategory(
                    "test_mod.scripting", "Scripting", 0xFF112233, "S");
                AddonNodeDefinition def = AddonNodeDefinition.builder(TEST_ADDON_ID)
                    .displayName("Test Script")
                    .category(category)
                    .build();
                registrar.register(def,
                    ctx -> CompletableFuture.completedFuture(NodeResult.SUCCESS),
                    TEST_SERIALIZER);
                registrar.seal();
                NodeTypeRegistry.INSTANCE.install(registrar);
            } catch (IllegalStateException e) {
                // Already installed by a parallel test run — acceptable
            }
        }
        // Confirm test_mod:unknown_addon_type is NOT registered so the missing-addon branch runs
        assertTrue(!NodeTypeRegistry.INSTANCE.hasType(UNREGISTERED_ADDON_ID),
            "UNREGISTERED_ADDON_ID must not be registered for Test 4 to exercise the placeholder branch");
    }

    // -------------------------------------------------------------------------
    // Test 1: snapshot→convertToNodes round-trip (CR-03 regression gate)
    // -------------------------------------------------------------------------

    /**
     * Builds a live Node, copies its addon fields into a NodeData (the snapshot direction),
     * wraps it in a NodeGraphData, and passes it through NodeGraphPersistence.convertToNodes
     * (the load direction). Asserts addonTypeId and script text survive the full cycle.
     *
     * <p>This is the path that was broken by CR-03: createGraphSnapshot called
     * buildNodeGraphData without setting addonTypeId on the NodeData, so convertToNodes
     * could never restore it. Plan 01-04 fixed this by calling AddonNodeDataCopy at all
     * four conversion sites.
     */
    @Test
    void snapshotToConvertToNodes_addonTypeIdAndScriptSurviveRoundTrip() {
        // Arrange — build a live ADDON node with script text
        Node sourceNode = new Node(TEST_ADDON_ID, 100, 200);
        Map<String, Object> extraFields = new LinkedHashMap<>();
        extraFields.put("_schema_version", 1);
        extraFields.put("script", "return 'hello world'");
        sourceNode.setAddonExtraFields(extraFields);

        // Act (save direction) — copy addon fields into NodeData
        NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
        nodeData.setId("test-node-1");
        nodeData.setType(NodeType.ADDON);
        AddonNodeDataCopy.copyAddonFieldsToNodeData(sourceNode, nodeData);

        // Wrap in a graph and drive through convertToNodes (CR-03 proof)
        NodeGraphData graphData = new NodeGraphData();
        graphData.getNodes().add(nodeData);
        List<Node> restored = NodeGraphPersistence.convertToNodes(graphData);

        // Assert — addonTypeId must be non-null after the round-trip
        assertEquals(1, restored.size(), "Must restore exactly one node");
        Node restoredNode = restored.get(0);
        assertNotNull(restoredNode.getAddonTypeId(),
            "addonTypeId must be non-null after convertToNodes round-trip (CR-03 regression gate)");
        assertEquals(TEST_ADDON_ID, restoredNode.getAddonTypeId(),
            "addonTypeId value must match after round-trip");
        assertNotNull(restoredNode.getAddonExtraFields(),
            "addonExtraFields must be non-null after round-trip");
        assertEquals("return 'hello world'", restoredNode.getAddonExtraFields().get("script"),
            "script text must survive the full snapshot→convertToNodes round-trip");
    }

    // -------------------------------------------------------------------------
    // Test 2: editor-load restore path (CR-02 regression gate)
    // -------------------------------------------------------------------------

    /**
     * Builds a NodeData with type ADDON and addonTypeId set, then calls
     * AddonNodeDataCopy.restoreAddonFieldsToNode onto a freshly-constructed Node.
     * Asserts addonTypeId is non-null and script key is present in extraFields.
     *
     * <p>This is the editor-load path (NodeGraph.applyLoadedData) that was broken by CR-02:
     * restoreAddonFieldsToNode was not called, so addonTypeId was never set on the live node.
     */
    @Test
    void editorLoad_restoreAddonFieldsToNode_addonTypeIdAndScriptPreserved() {
        // Arrange — build a NodeData representing a saved ADDON node
        NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
        nodeData.setId("test-node-2");
        nodeData.setType(NodeType.ADDON);
        nodeData.setAddonTypeId(TEST_ADDON_ID);
        Map<String, Object> savedFields = new LinkedHashMap<>();
        savedFields.put("_schema_version", 1);
        savedFields.put("script", "print('editor load')");
        nodeData.setExtraFields(savedFields);

        // Act — construct a fresh node and restore addon fields (the editor-load path)
        Node freshNode = new Node(NodeType.ADDON, 50, 75);
        AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, freshNode);

        // Assert — addonTypeId must be non-null after restore (CR-02 regression gate)
        assertNotNull(freshNode.getAddonTypeId(),
            "addonTypeId must be non-null after restoreAddonFieldsToNode (CR-02 regression gate)");
        assertEquals(TEST_ADDON_ID, freshNode.getAddonTypeId(),
            "addonTypeId value must match the NodeData source");
        assertNotNull(freshNode.getAddonExtraFields(),
            "addonExtraFields must be set after restore");
        assertTrue(freshNode.getAddonExtraFields().containsKey("script"),
            "script key must be present in restored addonExtraFields");
        assertEquals("print('editor load')", freshNode.getAddonExtraFields().get("script"),
            "script text must match after restore");
    }

    // -------------------------------------------------------------------------
    // Test 3: clipboard copy→restore round-trip (CR-02 regression gate + deep-copy proof)
    // -------------------------------------------------------------------------

    /**
     * Performs a clipboard-style round-trip: copyAddonFieldsToNodeData from a live node
     * into a NodeData, then restoreAddonFieldsToNode into a new node. Asserts addonTypeId
     * and script survive, and that the restored extraFields map is a distinct instance
     * (deep copy, not aliased).
     */
    @Test
    void clipboardCopyRestore_addonTypeIdAndScriptSurvive_extraFieldsIsDeepCopy() {
        // Arrange — source node with extra fields
        Node sourceNode = new Node(TEST_ADDON_ID, 10, 20);
        Map<String, Object> originalFields = new LinkedHashMap<>();
        originalFields.put("_schema_version", 1);
        originalFields.put("script", "local x = 1");
        sourceNode.setAddonExtraFields(originalFields);

        // Act (copy direction) — simulate clipboard save
        NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
        nodeData.setId("clipboard-node-1");
        nodeData.setType(NodeType.ADDON);
        AddonNodeDataCopy.copyAddonFieldsToNodeData(sourceNode, nodeData);

        // Act (restore direction) — simulate clipboard paste
        Node pastedNode = new Node(NodeType.ADDON, 200, 300);
        AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, pastedNode);

        // Assert — addonTypeId and script survive the clipboard round-trip
        assertNotNull(pastedNode.getAddonTypeId(),
            "addonTypeId must survive clipboard copy→restore round-trip");
        assertEquals(TEST_ADDON_ID, pastedNode.getAddonTypeId(),
            "addonTypeId value must match after clipboard round-trip");
        assertNotNull(pastedNode.getAddonExtraFields(),
            "addonExtraFields must be non-null after clipboard restore");
        assertEquals("local x = 1", pastedNode.getAddonExtraFields().get("script"),
            "script text must survive clipboard round-trip");

        // Assert deep copy — restored map must be a distinct instance (not aliased)
        // to prevent mutations on the pasted node from corrupting the clipboard data
        assertNotSame(sourceNode.getAddonExtraFields(), pastedNode.getAddonExtraFields(),
            "Restored extraFields must be a distinct map instance (deep copy, not aliased)");
    }

    // -------------------------------------------------------------------------
    // Test 4: missing-addon placeholder path (D-09)
    // -------------------------------------------------------------------------

    /**
     * With an unregistered addonTypeId, verifies that restoreAddonFieldsToNode retains
     * the extraFields verbatim (placeholder for future re-serialization) and marks the
     * node as addonUnresolved — the D-09 graceful-degradation contract.
     */
    @Test
    void missingAddon_restoreAddonFieldsToNode_retainsExtraFieldsAndMarksUnresolved() {
        // Arrange — NodeData for an ADDON type that is NOT installed
        NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
        nodeData.setId("unknown-addon-node");
        nodeData.setType(NodeType.ADDON);
        nodeData.setAddonTypeId(UNREGISTERED_ADDON_ID);
        Map<String, Object> savedFields = new LinkedHashMap<>();
        savedFields.put("_schema_version", 2);
        savedFields.put("script", "-- saved by unknown addon");
        savedFields.put("custom_key", "some_value");
        nodeData.setExtraFields(savedFields);

        // Act — restore onto a fresh node (addon is absent from registry)
        Node freshNode = new Node(NodeType.ADDON, 0, 0);
        AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, freshNode);

        // Assert — addonTypeId is still set (retained for re-serialization)
        assertNotNull(freshNode.getAddonTypeId(),
            "addonTypeId must be retained even when addon is not installed");
        assertEquals(UNREGISTERED_ADDON_ID, freshNode.getAddonTypeId(),
            "addonTypeId must match the NodeData source even for uninstalled addons");

        // Assert — extraFields retained verbatim (placeholder blob)
        assertNotNull(freshNode.getAddonExtraFields(),
            "extraFields must be retained verbatim for missing addon (D-09 placeholder)");
        assertEquals("-- saved by unknown addon", freshNode.getAddonExtraFields().get("script"),
            "script text must be preserved in the placeholder blob");
        assertEquals("some_value", freshNode.getAddonExtraFields().get("custom_key"),
            "all custom keys must be preserved verbatim");

        // Assert — node is marked as unresolved (D-09 contract)
        assertTrue(freshNode.isAddonUnresolved(),
            "isAddonUnresolved must be true when addon type is not installed (D-09)");
    }
}
