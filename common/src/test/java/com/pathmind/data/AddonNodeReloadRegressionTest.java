package com.pathmind.data;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the null-extraFields reload path (NEW-CR-02) and the
 * unresolved-clear path (WR-05) in {@link AddonNodeDataCopy#restoreAddonFieldsToNode}.
 *
 * <p>NEW-CR-02: when {@code nodeData.getExtraFields()} is null (a freshly-placed Script node
 * saved before the user typed anything), the success branch must still produce a non-null
 * {@code addonExtraFields} containing the serializer-seeded default script.
 *
 * <p>WR-05: the success branch must call {@code node.setAddonUnresolved(false)} so a node
 * previously marked unresolved (loaded while the addon was absent) clears the indicator
 * after a successful restore.
 */
class AddonNodeReloadRegressionTest {

    // Shared constants from the consolidated test registry (order-independent)
    private static final String TEST_ADDON_ID = AddonTestRegistry.SCRIPT_ADDON_ID;
    private static final String DEFAULT_SCRIPT = AddonTestRegistry.DEFAULT_SCRIPT;

    @BeforeAll
    static void installSyntheticRegistry() {
        // Delegate to the shared registry helper — ensures order-independence across the
        // full test suite. Only the first call installs; subsequent calls are no-ops.
        AddonTestRegistry.ensureInstalled();
    }

    // -------------------------------------------------------------------------
    // Test 1: NEW-CR-02 — null-extraFields path must seed non-null default script
    // -------------------------------------------------------------------------

    /**
     * Verifies that when a NodeData of type ADDON has a registered addonTypeId but
     * NULL extraFields (a freshly-placed Script node saved before the user typed anything),
     * restoreAddonFieldsToNode produces a non-null addonExtraFields containing the
     * serializer-seeded default script value.
     *
     * <p>This is the close-and-reopen observable failure from re-verification round 3
     * (truth #4): the node rendered empty after reload because the seeded default was
     * discarded by the null-guard at the setAddonExtraFields step (NEW-CR-02).
     */
    @Test
    void restoreWithNullExtraFields_seedsNonNullDefaultScript() {
        // Arrange — NodeData representing a freshly-placed Script node (never edited):
        // extraFields is null because the user saved before typing anything.
        NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
        nodeData.setId("fresh-script-node-1");
        nodeData.setType(NodeType.ADDON);
        nodeData.setAddonTypeId(TEST_ADDON_ID);
        nodeData.setExtraFields(null); // null = never edited / freshly placed

        // Act — restore addon fields from the null-extraFields NodeData
        Node freshNode = new Node(NodeType.ADDON, 100, 200);
        AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, freshNode);

        // Assert — addonExtraFields must be non-null after restore (NEW-CR-02 gate)
        assertNotNull(freshNode.getAddonExtraFields(),
            "NEW-CR-02: addonExtraFields must be non-null after restoring a null-extraFields NodeData");

        // Assert — the map must contain the "script" key seeded by the serializer
        assertTrue(freshNode.getAddonExtraFields().containsKey("script"),
            "NEW-CR-02: addonExtraFields must contain 'script' key after restore from null-extraFields");

        // Assert — the script value must equal the serializer's seeded default
        assertEquals(DEFAULT_SCRIPT, freshNode.getAddonExtraFields().get("script"),
            "NEW-CR-02: script value must equal the serializer-seeded default after null-extraFields restore");
    }

    // -------------------------------------------------------------------------
    // Test 2: WR-05 — success branch must clear addonUnresolved flag
    // -------------------------------------------------------------------------

    /**
     * Verifies that when a node was previously marked addonUnresolved (e.g., loaded while
     * the addon was absent), a subsequent successful restore via restoreAddonFieldsToNode
     * clears the flag so the missing-addon indicator is removed.
     *
     * <p>WR-05: the success branch never called setAddonUnresolved(false), so a node that
     * was ever marked unresolved permanently showed the indicator even after a successful
     * restore on addon re-install.
     */
    @Test
    void restoreSuccess_clearsAddonUnresolved() {
        // Arrange — NodeData with registered addonTypeId and pre-populated extraFields
        NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
        nodeData.setId("previously-unresolved-node");
        nodeData.setType(NodeType.ADDON);
        nodeData.setAddonTypeId(TEST_ADDON_ID);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("_schema_version", 1);
        fields.put("script", "print('restored')");
        nodeData.setExtraFields(fields);

        // Act — construct a node, mark it unresolved (simulates a prior addon-absent load),
        // then restore from a NodeData with a registered addonTypeId
        Node freshNode = new Node(NodeType.ADDON, 50, 75);
        freshNode.setAddonUnresolved(true); // simulate prior addon-absent load
        assertTrue(freshNode.isAddonUnresolved(),
            "Pre-condition: node must be addonUnresolved=true before restore");

        AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, freshNode);

        // Assert — addonUnresolved must be cleared after a successful restore (WR-05 gate)
        assertFalse(freshNode.isAddonUnresolved(),
            "WR-05: isAddonUnresolved must be false after a successful restoreAddonFieldsToNode");
    }

    // -------------------------------------------------------------------------
    // Test 3: no-regression — pre-populated extraFields path preserves script
    // -------------------------------------------------------------------------

    /**
     * Verifies that restoreAddonFieldsToNode continues to preserve an existing script value
     * from pre-populated extraFields. Guards against regression to the existing behavior
     * tested by AddonNodeConversionRoundTripTest.
     */
    @Test
    void restoreWithPrepopulatedExtraFields_preservesScript() {
        // Arrange — NodeData with extraFields already containing a user-written script
        NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
        nodeData.setId("prepopulated-node");
        nodeData.setType(NodeType.ADDON);
        nodeData.setAddonTypeId(TEST_ADDON_ID);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("_schema_version", 1);
        fields.put("script", "x");
        nodeData.setExtraFields(fields);

        // Act
        Node freshNode = new Node(NodeType.ADDON, 0, 0);
        AddonNodeDataCopy.restoreAddonFieldsToNode(nodeData, freshNode);

        // Assert — the pre-populated script value must be preserved unchanged
        assertNotNull(freshNode.getAddonExtraFields(),
            "addonExtraFields must be non-null after restore from pre-populated NodeData");
        assertEquals("x", freshNode.getAddonExtraFields().get("script"),
            "Pre-populated script value 'x' must be preserved unchanged after restore");
    }
}
