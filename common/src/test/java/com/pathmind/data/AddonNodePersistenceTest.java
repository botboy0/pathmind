package com.pathmind.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeSerializer;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence round-trip tests for ADDON node serialization and deserialization (API-05).
 *
 * <p>Tests are deliberately isolated from the full saveGraph/loadGraph path (which requires
 * Minecraft client state) — instead they exercise the serializer interface and NodeData GSON
 * cycle directly.
 */
class AddonNodePersistenceTest {

    // Shared constant from the consolidated test registry (order-independent)
    private static final String TEST_ADDON_ID = AddonTestRegistry.SCRIPT_ADDON_ID;

    /**
     * Class-local serializer used by the serializer contract tests in this class
     * (serialize_*, deserialize_*). These tests call TEST_SERIALIZER directly — they do
     * NOT go through NodeTypeRegistry — so this serializer is independent of the shared
     * registry serializer installed by AddonTestRegistry.ensureInstalled().
     *
     * <p>In particular, {@link #deserialize_toleratesNullFields} asserts that
     * {@code ctx.getScriptText()} is {@code null} after {@code deserialize(ctx, null)}.
     * The shared registry serializer seeds DEFAULT_SCRIPT on null-fields (NEW-CR-02);
     * using a class-local serializer here preserves the original contract assertion
     * for this serializer-interface test.
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
                // version-aware migration could go here
                assert version >= 1;
            }
            Object scriptObj = fields.get("script");
            if (scriptObj != null) {
                ctx.setScriptText(scriptObj.toString());
            }
        }
    };

    @BeforeAll
    static void installSyntheticRegistry() {
        // Delegate to the shared registry helper — ensures order-independence across the
        // full test suite. Only the first call installs; subsequent calls are no-ops.
        // Note: the shared registry serializer (AddonTestRegistry.SHARED_SERIALIZER) seeds
        // DEFAULT_SCRIPT on null-fields; the class-local TEST_SERIALIZER above is used
        // only for direct serializer contract tests in this class (not via registry).
        AddonTestRegistry.ensureInstalled();
    }

    // -------------------------------------------------------------------------
    // Serializer contract tests (no Minecraft runtime required)
    // -------------------------------------------------------------------------

    @Test
    void serialize_producesSchemaVersionAndScriptText() {
        AddonNodeContext ctx = new AddonNodeContext();
        ctx.setAddonTypeId(TEST_ADDON_ID);
        ctx.setScriptText("print('hello')");

        Map<String, Object> result = TEST_SERIALIZER.serialize(ctx);

        assertNotNull(result, "serialize must not return null");
        assertEquals(1, ((Number) result.get("_schema_version")).intValue(),
            "_schema_version must be 1");
        assertEquals("print('hello')", result.get("script"),
            "script text must round-trip");
    }

    @Test
    void deserialize_roundTripsScriptText() {
        // Arrange
        AddonNodeContext serCtx = new AddonNodeContext();
        serCtx.setAddonTypeId(TEST_ADDON_ID);
        serCtx.setScriptText("return 42");
        Map<String, Object> blob = TEST_SERIALIZER.serialize(serCtx);

        // Act
        AddonNodeContext deCtx = new AddonNodeContext();
        TEST_SERIALIZER.deserialize(deCtx, blob);

        // Assert
        assertEquals("return 42", deCtx.getScriptText(),
            "script text must survive serialize/deserialize round-trip");
    }

    @Test
    void deserialize_handlesGsonDoubleErasureWithoutClassCastException() {
        // GSON reads JSON numbers as Double when target type is Object — simulate that
        Map<String, Object> blob = new HashMap<>();
        blob.put("_schema_version", 1.0); // Double as GSON would produce
        blob.put("script", "test");

        AddonNodeContext ctx = new AddonNodeContext();
        // Must not throw ClassCastException (Pitfall 4 / _schema_version contract)
        TEST_SERIALIZER.deserialize(ctx, blob);
        assertEquals("test", ctx.getScriptText());
    }

    @Test
    void deserialize_toleratesNullFields() {
        AddonNodeContext ctx = new AddonNodeContext();
        // Must not throw NPE when fields is null
        TEST_SERIALIZER.deserialize(ctx, null);
        assertNull(ctx.getScriptText());
    }

    // -------------------------------------------------------------------------
    // NodeData GSON round-trip test (no Minecraft runtime required)
    // -------------------------------------------------------------------------

    @Test
    void nodeData_addonTypeIdAndExtraFieldsSurviveGsonRoundTrip() {
        // Build GSON with the same adapter as NodeGraphPersistence uses
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(NodeType.class, new NodeTypeAdapter())
            .registerTypeAdapter(com.pathmind.nodes.NodeMode.class, new NodeModeAdapter())
            .create();

        // Create a NodeData representing an ADDON node
        NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
        nodeData.setId("abc-123");
        nodeData.setType(NodeType.ADDON);
        nodeData.setAddonTypeId(TEST_ADDON_ID);
        Map<String, Object> extraFields = new LinkedHashMap<>();
        extraFields.put("_schema_version", 1);
        extraFields.put("script", "return 'hello'");
        nodeData.setExtraFields(extraFields);

        // Wrap in a graph and GSON round-trip
        NodeGraphData graph = new NodeGraphData();
        graph.getNodes().add(nodeData);
        String json = gson.toJson(graph);

        // Verify JSON contains the expected fields
        assertNotNull(json, "serialized JSON must not be null");
        assertTrue(json.contains("addonTypeId"), "JSON must contain addonTypeId");
        assertTrue(json.contains(TEST_ADDON_ID), "JSON must contain the addon type id");
        assertTrue(json.contains("_schema_version"), "JSON must contain _schema_version");
        assertTrue(json.contains("extraFields"), "JSON must contain extraFields");

        // Deserialize back and verify
        NodeGraphData restored = gson.fromJson(json, NodeGraphData.class);
        assertNotNull(restored, "deserialized graph must not be null");
        assertEquals(1, restored.getNodes().size(), "must have exactly one node");
        NodeGraphData.NodeData restoredNode = restored.getNodes().get(0);
        assertEquals(NodeType.ADDON, restoredNode.getType(),
            "node type must be ADDON after round-trip");
        assertEquals(TEST_ADDON_ID, restoredNode.getAddonTypeId(),
            "addonTypeId must survive round-trip");
        assertNotNull(restoredNode.getExtraFields(),
            "extraFields must survive round-trip");
        // GSON Double erasure: _schema_version will come back as Double
        assertEquals(1, ((Number) restoredNode.getExtraFields().get("_schema_version")).intValue(),
            "_schema_version must be 1 after GSON round-trip (using Number.intValue())");
        assertEquals("return 'hello'", restoredNode.getExtraFields().get("script"),
            "script text must survive round-trip");
    }

    @Test
    void nodeData_builtinNodeHasNoAddonFields() {
        // Verify that a non-ADDON node has null addonTypeId / extraFields (API-09)
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(NodeType.class, new NodeTypeAdapter())
            .registerTypeAdapter(com.pathmind.nodes.NodeMode.class, new NodeModeAdapter())
            .create();

        NodeGraphData.NodeData nodeData = new NodeGraphData.NodeData();
        nodeData.setId("xyz-456");
        nodeData.setType(NodeType.START);
        // addonTypeId and extraFields NOT set

        NodeGraphData graph = new NodeGraphData();
        graph.getNodes().add(nodeData);
        String json = gson.toJson(graph);

        // GSON omits null fields (no serializeNulls) — JSON must NOT contain addon keys
        assertFalse(json.contains("addonTypeId"), "Built-in node JSON must not contain addonTypeId");
        assertFalse(json.contains("extraFields"), "Built-in node JSON must not contain extraFields");

        NodeGraphData restored = gson.fromJson(json, NodeGraphData.class);
        NodeGraphData.NodeData restoredNode = restored.getNodes().get(0);
        assertNull(restoredNode.getAddonTypeId(), "addonTypeId must be null for built-in nodes");
        assertNull(restoredNode.getExtraFields(), "extraFields must be null for built-in nodes");
    }
}
