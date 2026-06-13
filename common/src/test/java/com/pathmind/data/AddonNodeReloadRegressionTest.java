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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    private static final String TEST_ADDON_ID = "test_mod:script";

    /**
     * The default script text this test serializer seeds when the fields map is null.
     * Must be non-empty so the null-path produces verifiably observable data.
     */
    private static final String DEFAULT_SCRIPT = "-- default script (NEW-CR-02 test)";

    /**
     * Inline serializer that seeds DEFAULT_SCRIPT when fields is null, and reads the
     * "script" key when fields is non-null. Matches the contract the plan expects from
     * the real LuaScriptSerializer.
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
                // Null-fields path: seed the default so restoreAddonFieldsToNode can write it
                ctx.setScriptText(DEFAULT_SCRIPT);
                return;
            }
            Object scriptObj = fields.get("script");
            if (scriptObj != null) {
                ctx.setScriptText(scriptObj.toString());
            }
        }
    };

    @BeforeAll
    static void installSyntheticRegistry() {
        // Install-once guard: tolerate prior installation from AddonNodeConversionRoundTripTest
        // or parallel test runs (NodeTypeRegistry is JVM-wide install-once).
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
