package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the missing-ingredient derivation behind the craft executor's
 * {@code missing_resource} envelope detail (simulated-inventory twin, same
 * convention as {@link NodeCompatibilityTest}'s source-slot planning tests):
 * each satisfiable ingredient consumes one count; the distinct keys of the
 * unsatisfiable ingredients come back in first-seen order.
 */
class NodeCraftMissingIngredientsTest {

    @Test
    void unsatisfiableIngredientsAreListedDistinctInFirstSeenOrder() {
        // 1 plank in the inventory; recipe needs plank, plank, stick, stick:
        // the second plank and both sticks are unsatisfiable.
        List<String> missing = NodeCraftCommandExecutor.missingIngredientKeysForTests(
            List.of(new NodeCraftCommandExecutor.TestIngredientStack("minecraft:oak_planks", 1)),
            List.of("minecraft:oak_planks", "minecraft:oak_planks", "minecraft:stick", "minecraft:stick"));

        assertEquals(List.of("minecraft:oak_planks", "minecraft:stick"), missing);
    }

    @Test
    void fullySatisfiableRecipeYieldsEmptyMissingList() {
        List<String> missing = NodeCraftCommandExecutor.missingIngredientKeysForTests(
            List.of(new NodeCraftCommandExecutor.TestIngredientStack("minecraft:oak_planks", 2)),
            List.of("minecraft:oak_planks", "minecraft:oak_planks"));

        assertTrue(missing.isEmpty());
    }

    @Test
    void emptyInventoryReportsEveryIngredientOnce() {
        List<String> missing = NodeCraftCommandExecutor.missingIngredientKeysForTests(
            List.of(),
            List.of("minecraft:cobblestone", "minecraft:cobblestone", "minecraft:stick"));

        assertEquals(List.of("minecraft:cobblestone", "minecraft:stick"), missing);
    }
}
