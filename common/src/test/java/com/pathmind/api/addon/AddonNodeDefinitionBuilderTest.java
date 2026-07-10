package com.pathmind.api.addon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation contract of {@link AddonNodeDefinition.Builder#build()} (IN-01):
 * missing/blank required fields must fail with an {@link IllegalArgumentException}
 * whose message names the field (never an NPE), and optional fields keep their
 * documented never-null contracts.
 */
class AddonNodeDefinitionBuilderTest {

    private static final AddonNodeCategory TEST_CATEGORY =
        new AddonNodeCategory("test_mod.testing", "Testing", 0xFF888888, "T");

    @Test
    void blankIdFailsWithFieldNamedInMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> AddonNodeDefinition.builder("  ")
                .displayName("Node").category(TEST_CATEGORY).build());
        assertTrue(ex.getMessage().contains("id"), ex.getMessage());
    }

    @Test
    void nullIdFailsWithIllegalArgumentNotNpe() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> AddonNodeDefinition.builder(null)
                .displayName("Node").category(TEST_CATEGORY).build());
        assertTrue(ex.getMessage().contains("id"), ex.getMessage());
    }

    @Test
    void missingDisplayNameFailsAndNamesTheDefinition() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> AddonNodeDefinition.builder("test:node")
                .category(TEST_CATEGORY).build());
        assertTrue(ex.getMessage().contains("displayName"), ex.getMessage());
        assertTrue(ex.getMessage().contains("test:node"), ex.getMessage());
    }

    @Test
    void missingCategoryFailsAndNamesTheDefinition() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> AddonNodeDefinition.builder("test:node")
                .displayName("Node").build());
        assertTrue(ex.getMessage().contains("category"), ex.getMessage());
        assertTrue(ex.getMessage().contains("test:node"), ex.getMessage());
    }

    @Test
    void nullProvenanceLabelIsNormalizedToEmpty() {
        AddonNodeDefinition def = AddonNodeDefinition.builder("test:node")
            .displayName("Node")
            .category(TEST_CATEGORY)
            .provenanceLabel(null)
            .build();
        assertEquals("", def.getProvenanceLabel());
    }

    @Test
    void validDefinitionBuildsWithDefaults() {
        AddonNodeDefinition def = AddonNodeDefinition.builder("test:node")
            .displayName("Node")
            .category(TEST_CATEGORY)
            .build();
        assertEquals("test:node", def.getId());
        assertEquals("", def.getProvenanceLabel());
        assertEquals(-1, def.getBodyHeight());
    }
}
