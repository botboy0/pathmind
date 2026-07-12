package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * COLLECT success-field extension (Baritone-integration milestone): the
 * {@code collected} inventory-delta becomes available for multiple mode too, by
 * falling back to the {@code Blocks} list argument (sum of per-id deltas).
 *
 * <p>Contract note pinned here deliberately: {@code collected} is a purely
 * informational aid — COLLECT's completion does not guarantee pickup, and the
 * delta counts the literal requested ids (no drop translation: mining stone
 * yields cobblestone and reports 0). Scripts that need certainty verify
 * inventory themselves. This is documented in the Lua scripting guide.
 */
class AddonCollectSuccessFieldsTest {

    @Test
    void blocksListFallbackSumsPerIdDeltas() {
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.COLLECT, Map.of("Mode", "collect_multiple", "Blocks", "stone, dirt"),
            Map.of("minecraft:dirt", 1),
            Map.of("minecraft:stone", 2, "minecraft:dirt", 4),
            null);

        assertEquals(5, fields.get("collected"),
            "multiple mode must sum the deltas of every listed block id");
    }

    @Test
    void blockArgumentStillTakesPrecedenceOverBlocks() {
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.COLLECT, Map.of("Block", "oak_log", "Blocks", "stone"),
            Map.of(),
            Map.of("minecraft:oak_log", 3, "minecraft:stone", 7),
            null);

        assertEquals(3, fields.get("collected"));
    }

    @Test
    void blocksEntriesAreNamespacedAndTrimmed() {
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.COLLECT, Map.of("Blocks", " minecraft:stone , dirt "),
            Map.of(),
            Map.of("minecraft:stone", 1, "minecraft:dirt", 1),
            null);

        assertEquals(2, fields.get("collected"));
    }

    @Test
    void collectWithoutAnyTargetArgumentStaysFieldless() {
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.COLLECT, Map.of(), Map.of(), Map.of("minecraft:stone", 2), null);

        assertNotNull(fields);
        assertTrue(fields.isEmpty());
    }
}
