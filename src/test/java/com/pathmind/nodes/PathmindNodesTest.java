package com.pathmind.nodes;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathmindNodesTest {
    @Test
    void builtInNodeTypesAreRegisteredByPersistenceId() {
        PathmindNodeDefinition definition = PathmindNodes.get(Identifier.of("pathmind", "start")).orElseThrow();

        assertEquals(Identifier.of("pathmind", "start"), definition.id());
        assertEquals(NodeType.START, definition.builtInType().orElseThrow());
        assertEquals(NodeCategory.FLOW, definition.category());
        assertFalse(definition.hasParameters());
    }

    @Test
    void addonNodeCanRegisterWithoutEnumConstant() {
        Identifier id = Identifier.of("myaddon", "scan_area");

        PathmindNodes.register(id, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("myaddon.node.scan_area")
            .descriptionKey("myaddon.node.scan_area.desc")
            .color(0xFF336699)
            .hasParameters(true));

        PathmindNodeDefinition definition = PathmindNodes.get(id).orElseThrow();
        assertEquals(id, definition.id());
        assertTrue(definition.builtInType().isEmpty());
        assertEquals(NodeCategory.SENSORS, definition.category());
        assertEquals("myaddon.node.scan_area", definition.translationKey());
        assertEquals("myaddon.node.scan_area.desc", definition.descriptionKey());
        assertEquals(0xFF336699, definition.color());
        assertTrue(definition.hasParameters());
    }

    @Test
    void addonPluginEntrypointCanRegisterNodeDefinitions() {
        Identifier id = Identifier.of("entrypointaddon", "scan_area");

        PathmindNodes.loadEntrypoints(List.of(registry -> registry.register(id, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("entrypointaddon.node.scan_area")
            .descriptionKey("entrypointaddon.node.scan_area.desc")
            .color(0xFF224466))));

        PathmindNodeDefinition definition = PathmindNodes.get(id).orElseThrow();
        assertEquals(id, definition.id());
        assertTrue(definition.builtInType().isEmpty());
        assertEquals(NodeCategory.SENSORS, definition.category());
    }
}
