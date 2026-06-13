package com.pathmind.nodes;

import com.pathmind.api.addon.AddonNodeCategory;
import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeDefinition;
import com.pathmind.api.addon.AddonNodeSerializer;
import com.pathmind.api.addon.NodeResult;
import com.pathmind.api.addon.NodeTypeRegistrar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * <p><b>Test isolation note:</b> {@code NodeTypeRegistry.INSTANCE} is a JVM-wide install-once
 * singleton shared across all test classes. This class uses reflection to add its unique type
 * ({@code test_mod:creation_test_type}) directly into the already-installed registry maps,
 * bypassing the install-once guard. This is test-only code and does not affect production
 * behavior — the production path always installs the registry once at mod startup.
 */
class AddonNodeCreationTest {

    // Use a distinct ID that no other test class registers, so there is no risk of
    // a conflicting serializer being installed for this type by a sibling test class.
    private static final String TEST_ADDON_ID = "test_mod:creation_test_type";
    private static final String DEFAULT_SCRIPT = "-- default script";

    /**
     * Serializer that seeds a default script on the null-fields path (mimicking the real
     * Lua addon serializer's constructor-seeding behavior).
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
                // null-fields path: seed the default script (GAP-3 production behavior)
                ctx.setScriptText(DEFAULT_SCRIPT);
                return;
            }
            Object scriptObj = fields.get("script");
            if (scriptObj != null) {
                ctx.setScriptText(scriptObj.toString());
            }
        }
    };

    /**
     * Injects the creation-test type directly into the already-installed
     * {@code NodeTypeRegistry.INSTANCE} via reflection. This is necessary because
     * {@code INSTANCE.install()} is a one-shot operation — whichever test class runs
     * first (AddonNodePersistenceTest or AddonNodeConversionRoundTripTest) exhausts
     * the install slot. Using reflection to populate the internal maps is the least
     * invasive way to add a test-only type without modifying production code or the
     * existing sibling test classes.
     */
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void installSyntheticTypeViaReflection() throws Exception {
        if (NodeTypeRegistry.INSTANCE.hasType(TEST_ADDON_ID)) {
            return; // already installed by a prior run in the same JVM — safe no-op
        }

        // Build the definition
        AddonNodeCategory category = new AddonNodeCategory(
            "test_mod.scripting", "Scripting", 0xFF112233, "S");
        AddonNodeDefinition def = AddonNodeDefinition.builder(TEST_ADDON_ID)
            .displayName("Test Script")
            .category(category)
            .build();

        // Inject directly into the private maps of NodeTypeRegistry.INSTANCE.
        // This bypasses the install-once guard intentionally — test code only.
        NodeTypeRegistry registry = NodeTypeRegistry.INSTANCE;

        Field definitionsField = NodeTypeRegistry.class.getDeclaredField("definitions");
        definitionsField.setAccessible(true);
        Map<String, AddonNodeDefinition> definitions =
            (Map<String, AddonNodeDefinition>) definitionsField.get(registry);
        definitions.put(TEST_ADDON_ID, def);

        Field executorsField = NodeTypeRegistry.class.getDeclaredField("executors");
        executorsField.setAccessible(true);
        Map<String, com.pathmind.api.addon.AddonNodeExecutor> executors =
            (Map<String, com.pathmind.api.addon.AddonNodeExecutor>) executorsField.get(registry);
        executors.put(TEST_ADDON_ID, ctx -> CompletableFuture.completedFuture(NodeResult.SUCCESS));

        Field serializersField = NodeTypeRegistry.class.getDeclaredField("serializers");
        serializersField.setAccessible(true);
        Map<String, AddonNodeSerializer> serializers =
            (Map<String, AddonNodeSerializer>) serializersField.get(registry);
        serializers.put(TEST_ADDON_ID, TEST_SERIALIZER);
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
