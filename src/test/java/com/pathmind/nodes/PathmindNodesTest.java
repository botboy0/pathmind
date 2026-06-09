package com.pathmind.nodes;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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

    @Test
    void builtInNodeMetadataIsAvailableFromRegistryDefinition() {
        PathmindNodeDefinition definition = PathmindNodes.get(Identifier.of("pathmind", "set_variable")).orElseThrow();

        assertTrue(definition.draggableFromSidebar());
        assertFalse(definition.requiresBaritone());
        assertFalse(definition.requiresUiUtils());
        assertEquals(2, definition.parameterSlots().size());
        assertEquals("Variable", definition.parameterSlots().get(0).label());
        assertTrue(definition.parameterSlots().get(0).required());
        assertEquals(Set.of(NodeValueTrait.VARIABLE), definition.parameterSlots().get(0).acceptedTraits());
        assertEquals("Value", definition.parameterSlots().get(1).label());
        assertTrue(definition.parameterSlots().get(1).required());
        assertTrue(definition.parameterSlots().get(1).acceptedTraits().contains(NodeValueTrait.ANY));
    }

    @Test
    void addonNodeCanRegisterTraitsAndParameterSlotsWithoutEnumConstant() {
        Identifier id = Identifier.of("slotaddon", "nearby_signal");

        PathmindNodes.register(id, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("slotaddon.node.nearby_signal")
            .descriptionKey("slotaddon.node.nearby_signal.desc")
            .color(0xFF552244)
            .draggableFromSidebar(false)
            .requiresBaritone(true)
            .providesTraits(NodeValueTrait.BOOLEAN)
            .parameterSlot("Target", true, NodeValueTrait.ENTITY, NodeValueTrait.PLAYER)
            .parameterSlot("Range", false, NodeValueTrait.RANGE, NodeValueTrait.NUMBER));

        PathmindNodeDefinition definition = PathmindNodes.get(id).orElseThrow();
        assertTrue(definition.builtInType().isEmpty());
        assertFalse(definition.draggableFromSidebar());
        assertTrue(definition.requiresBaritone());
        assertFalse(definition.requiresUiUtils());
        assertEquals(Set.of(NodeValueTrait.BOOLEAN), definition.providedTraits());
        assertEquals(2, definition.parameterSlots().size());
        assertEquals("Target", definition.parameterSlots().get(0).label());
        assertTrue(definition.parameterSlots().get(0).required());
        assertEquals(Set.of(NodeValueTrait.ENTITY, NodeValueTrait.PLAYER), definition.parameterSlots().get(0).acceptedTraits());
        assertEquals("Range", definition.parameterSlots().get(1).label());
        assertFalse(definition.parameterSlots().get(1).required());
        assertEquals(Set.of(NodeValueTrait.RANGE, NodeValueTrait.NUMBER), definition.parameterSlots().get(1).acceptedTraits());
    }
}
