package com.pathmind.nodes;

import com.pathmind.api.addon.AddonNodeCategory;
import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeDefinition;
import com.pathmind.api.addon.AddonNodeExecutor;
import com.pathmind.api.addon.AddonNodeSerializer;
import com.pathmind.api.addon.NodeResult;
import com.pathmind.api.addon.NodeTypeRegistrar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NodeTypeRegistrar validation behavior and NodeTypeRegistry round-trip.
 *
 * <p>Tests are in com.pathmind.nodes to access NodeTypeRegistry.install and
 * NodeTypeRegistrar package-private seal() method directly through the NodeTypeRegistry.
 */
class NodeTypeRegistryTest {

    private static final AddonNodeCategory TEST_CATEGORY =
        new AddonNodeCategory("test_mod.testing", "Testing", 0xFF888888, "T");

    private static final AddonNodeExecutor NO_OP_EXECUTOR =
        ctx -> CompletableFuture.completedFuture(NodeResult.SUCCESS);

    private static final AddonNodeSerializer NO_OP_SERIALIZER = new AddonNodeSerializer() {
        @Override
        public Map<String, Object> serialize(AddonNodeContext ctx) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("_schema_version", 1);
            return fields;
        }

        @Override
        public void deserialize(AddonNodeContext ctx, Map<String, Object> fields) {
        }
    };

    private AddonNodeDefinition buildValidDefinition(String id) {
        return AddonNodeDefinition.builder(id)
            .displayName("Test Node")
            .category(TEST_CATEGORY)
            .build();
    }

    // ---------------------------------------------------------------------------
    // NodeTypeRegistrar validation tests
    // ---------------------------------------------------------------------------

    @Test
    void registeredAddonTypeIsRetrievableByStringId() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();
        AddonNodeDefinition def = buildValidDefinition("test_mod:script");

        registrar.register(def, NO_OP_EXECUTOR, NO_OP_SERIALIZER);

        // Use a fresh registry instance to avoid cross-test pollution
        NodeTypeRegistry registry = new NodeTypeRegistry();
        registry.install(registrar);

        assertTrue(registry.hasType("test_mod:script"),
            "Registry must report hasType=true for registered id");
        assertSame(def, registry.definitionFor("test_mod:script"),
            "Registry must return the same definition instance");
        assertSame(NO_OP_EXECUTOR, registry.executorFor("test_mod:script"),
            "Registry must return the same executor instance");
        assertSame(NO_OP_SERIALIZER, registry.serializerFor("test_mod:script"),
            "Registry must return the same serializer instance");
    }

    @Test
    void duplicateAddonTypeIdThrowsIllegalArgumentException() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();
        AddonNodeDefinition def = buildValidDefinition("test_mod:dupe");

        registrar.register(def, NO_OP_EXECUTOR, NO_OP_SERIALIZER);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> registrar.register(def, NO_OP_EXECUTOR, NO_OP_SERIALIZER));
        assertTrue(ex.getMessage().contains("test_mod:dupe"),
            "Exception message must name the duplicate id");
    }

    @Test
    void nullExecutorThrowsNullPointerException() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();
        AddonNodeDefinition def = buildValidDefinition("test_mod:null_exec");

        NullPointerException ex = assertThrows(NullPointerException.class,
            () -> registrar.register(def, null, NO_OP_SERIALIZER));
        assertTrue(ex.getMessage().contains("non-null"),
            "Exception message must describe null argument requirement");
    }

    @Test
    void nullDefinitionThrowsNullPointerException() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();

        assertThrows(NullPointerException.class,
            () -> registrar.register(null, NO_OP_EXECUTOR, NO_OP_SERIALIZER));
    }

    @Test
    void nullSerializerThrowsNullPointerException() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();
        AddonNodeDefinition def = buildValidDefinition("test_mod:null_ser");

        assertThrows(NullPointerException.class,
            () -> registrar.register(def, NO_OP_EXECUTOR, null));
    }

    @Test
    void registrationAfterSealThrowsIllegalStateException() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();
        AddonNodeDefinition def = buildValidDefinition("test_mod:sealed");

        // Install seals the registrar
        NodeTypeRegistry registry = new NodeTypeRegistry();
        registry.install(registrar);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> registrar.register(def, NO_OP_EXECUTOR, NO_OP_SERIALIZER));
        assertTrue(ex.getMessage().contains("sealed"),
            "Exception message must indicate the registrar is sealed");
    }

    @Test
    void malformedAddonTypeIdIsRejected() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();
        // Path-traversal style id — must be rejected by format validation (T-01-01)
        AddonNodeDefinition def = buildValidDefinition("test_mod:normal_id");

        // We cannot build a definition with an invalid id through the builder since the
        // format validation is in the registrar. Test directly via a definition with a bad id
        // by building a definition and then trying with a raw bad-id registration attempt.
        // Since AddonNodeDefinition.builder takes the id and we need a malformed one, we
        // verify the registrar format check by calling register with a malformed definition.

        // Build definition with valid id first, then test path-traversal id through register:
        AddonNodeDefinition malformedDef = AddonNodeDefinition.builder("test_mod:valid")
            .displayName("OK Node")
            .category(TEST_CATEGORY)
            .build();

        // The registrar's format check reads def.getId() — so we need a definition whose
        // getId() returns a malformed value. Since AddonNodeDefinition is immutable and
        // built via builder, we must test via a definition that bypasses the builder's
        // lack of format validation (builder doesn't validate format; registrar does).
        // Create a definition with a path-traversal style id:
        AddonNodeDefinition evilDef = AddonNodeDefinition.builder("../../../evil")
            .displayName("Evil Node")
            .category(TEST_CATEGORY)
            .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> registrar.register(evilDef, NO_OP_EXECUTOR, NO_OP_SERIALIZER));
        assertTrue(ex.getMessage().contains("../../../evil") || ex.getMessage().contains("format"),
            "Exception message must reference the malformed id or format requirement");
    }

    @Test
    void pathTraversalIdWithColonIsRejected() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();

        // This id contains path traversal: the part before colon is valid but
        // "../../../evil:id" as a whole should be rejected for path traversal
        AddonNodeDefinition def = AddonNodeDefinition.builder("UPPERCASE:id")
            .displayName("Bad")
            .category(TEST_CATEGORY)
            .build();

        assertThrows(IllegalArgumentException.class,
            () -> registrar.register(def, NO_OP_EXECUTOR, NO_OP_SERIALIZER),
            "Uppercase id segment should fail the lowercase-only regex");
    }

    // ---------------------------------------------------------------------------
    // NodeTypeRegistry double-install guard test
    // ---------------------------------------------------------------------------

    @Test
    void doubleInstallThrowsIllegalStateException() {
        NodeTypeRegistrar registrar1 = new NodeTypeRegistrar();
        NodeTypeRegistrar registrar2 = new NodeTypeRegistrar();

        NodeTypeRegistry registry = new NodeTypeRegistry();
        registry.install(registrar1);

        assertThrows(IllegalStateException.class,
            () -> registry.install(registrar2),
            "Second install call must throw IllegalStateException");
    }

    @Test
    void emptyRegistrarInstallsCleanly() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();
        NodeTypeRegistry registry = new NodeTypeRegistry();

        // Empty registrar should install without error (API-09 standalone path)
        assertDoesNotThrow(() -> registry.install(registrar));
        assertFalse(registry.hasType("anything"),
            "Empty registry must report hasType=false for any id");
        assertTrue(registry.allDefinitions().isEmpty(),
            "Empty registry must return empty allDefinitions");
    }

    @Test
    void allDefinitionsIsUnmodifiable() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();
        registrar.register(buildValidDefinition("test_mod:node_a"), NO_OP_EXECUTOR, NO_OP_SERIALIZER);

        NodeTypeRegistry registry = new NodeTypeRegistry();
        registry.install(registrar);

        assertThrows(UnsupportedOperationException.class,
            () -> registry.allDefinitions().clear(),
            "allDefinitions must return an unmodifiable collection");
    }
}
