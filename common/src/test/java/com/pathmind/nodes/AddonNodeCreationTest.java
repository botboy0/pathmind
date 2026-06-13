package com.pathmind.nodes;

import com.pathmind.api.addon.AddonNodeCategory;
import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeDefinition;
import com.pathmind.api.addon.AddonNodeSerializer;
import com.pathmind.api.addon.NodeResult;
import com.pathmind.api.addon.NodeTypeRegistrar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for GAP-2 (display-name resolution) and GAP-3 (default-field seeding)
 * on freshly placed ADDON nodes.
 *
 * <p>GAP-2: {@code Node.getDisplayName()} must return the addon definition's display name
 * ("Test Script") for a registered addon, not the generic "Addon Node" translation.
 *
 * <p>GAP-3: {@code new Node(addonTypeId, x, y)} must seed {@code addonExtraFields}
 * (including the {@code "script"} key) at construction, before any save/reopen cycle.
 */
class AddonNodeCreationTest {

    private static final String TEST_ADDON_ID = "test_mod:script";
    private static final String DEFAULT_SCRIPT = "-- default script";

    /**
     * Serializer that mimics the Lua addon serializer: returns a default script on the
     * null-fields path and reads back the "script" key on the non-null path.
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
                // null-fields path: seed default script
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
        // Guard: install-once singleton — tolerate prior installation by sibling test classes
        // (NodeTypeRegistry is JVM-wide install-once).
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
    // Test (a): Registered addon node uses the definition's display name (GAP-2)
    // -------------------------------------------------------------------------

    /**
     * A freshly placed ADDON node with a registered addonTypeId must display the
     * addon definition's display name, not the generic "Addon Node" translation.
     */
    @Test
    void registeredAddonNode_getDisplayName_returnsDefinitionDisplayName() {
        Node node = new Node(TEST_ADDON_ID, 0, 0);
        String displayName = node.getDisplayName().getString();
        assertEquals("Test Script", displayName,
            "GAP-2: getDisplayName() must return the registered definition's display name, not 'Addon Node'");
    }

    // -------------------------------------------------------------------------
    // Test (b): Null or unregistered addonTypeId falls back to generic label (GAP-2 safety net)
    // -------------------------------------------------------------------------

    /**
     * When the addonTypeId is null or points to an unregistered addon, getDisplayName()
     * must fall back to the generic NodeType.ADDON display name without throwing.
     */
    @Test
    void unregisteredOrNullAddonNode_getDisplayName_fallsBackToGenericLabel() {
        // null addonTypeId — created via the NodeType constructor
        Node nullTypeNode = new Node(NodeType.ADDON, 0, 0);
        String nullDisplayName = nullTypeNode.getDisplayName().getString();
        // Must not be null or throw — must equal the NodeType.ADDON display name
        assertNotNull(nullDisplayName, "getDisplayName() must not return null for null addonTypeId");
        assertEquals(NodeType.ADDON.getDisplayName(), nullDisplayName,
            "GAP-2 fallback: null addonTypeId must yield NodeType.ADDON.getDisplayName()");

        // Unregistered addonTypeId
        Node unregisteredNode = new Node(NodeType.ADDON, 0, 0);
        unregisteredNode.setAddonTypeId("test_mod:does_not_exist");
        String unregisteredDisplayName = unregisteredNode.getDisplayName().getString();
        assertNotNull(unregisteredDisplayName,
            "getDisplayName() must not return null for unregistered addonTypeId");
        assertEquals(NodeType.ADDON.getDisplayName(), unregisteredDisplayName,
            "GAP-2 fallback: unregistered addonTypeId must yield NodeType.ADDON.getDisplayName()");
    }

    // -------------------------------------------------------------------------
    // Test (c): Default addonExtraFields seeded at construction (GAP-3)
    // -------------------------------------------------------------------------

    /**
     * After {@code new Node(addonTypeId, x, y)}, {@code getAddonExtraFields()} must be
     * non-null and contain the {@code "script"} key seeded by the serializer's null-fields
     * default path — no close→reopen cycle required.
     */
    @Test
    void registeredAddonNode_constructorSeedsDefaultExtraFields_includesScriptKey() {
        Node node = new Node(TEST_ADDON_ID, 0, 0);
        Map<String, Object> extraFields = node.getAddonExtraFields();

        assertNotNull(extraFields,
            "GAP-3: addonExtraFields must be non-null immediately after construction");
        assertTrue(extraFields.containsKey("script"),
            "GAP-3: addonExtraFields must contain the 'script' key seeded by the serializer default path");
        assertEquals(DEFAULT_SCRIPT, extraFields.get("script"),
            "GAP-3: the 'script' value must equal the serializer's default script");
    }

    // -------------------------------------------------------------------------
    // Test (d): Non-ADDON node display name is unchanged
    // -------------------------------------------------------------------------

    /**
     * getDisplayName() for a non-ADDON node must continue to return the node type's
     * own display name — the ADDON branch must not affect other node types.
     */
    @Test
    void nonAddonNode_getDisplayName_isUnchanged() {
        // Use a few representative non-ADDON types to confirm the branch is guarded
        for (NodeType type : new NodeType[]{NodeType.START, NodeType.MESSAGE, NodeType.GOTO}) {
            Node node = new Node(type, 0, 0);
            String displayName = node.getDisplayName().getString();
            assertEquals(type.getDisplayName(), displayName,
                "Non-ADDON node type " + type.name() + " must return type.getDisplayName() unchanged");
        }
    }
}
