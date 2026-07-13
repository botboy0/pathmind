package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Script access to GUI container slots (fork-only, invoker layer — mirrors the
 * per-call AllowBreak/AllowPlace pattern).
 *
 * <p>Without attached parameter nodes, MOVE_ITEM resolves both slots in the
 * PLAYER_INVENTORY space — a script could never address a furnace's
 * input/fuel/output slots (raw handler indices 0/1/2), which blocks smelting
 * and chest interaction from Lua. The invoker now accepts the synthetic
 * arguments {@code SourceSpace}/{@code TargetSpace} with values
 * {@code "player"}/{@code "gui"} and records them as fork-only per-node
 * overrides that the executor's slot-selection resolution consults first.
 * Graph nodes never set the overrides, so graph behavior is unchanged.
 */
class AddonMoveItemSlotSpaceTest {

    @Test
    void slotSpaceArgumentsSetTheNodeOverrides() {
        Node node = new Node(NodeType.MOVE_ITEM, 0, 0);

        assertNull(AddonActionInvoker.applySlotSpaceArg(node, "SourceSpace", "gui"));
        assertEquals(SlotSelectionType.GUI_CONTAINER, node.getAddonMoveItemSourceSpace());

        assertNull(AddonActionInvoker.applySlotSpaceArg(node, "targetspace", "PLAYER"));
        assertEquals(SlotSelectionType.PLAYER_INVENTORY, node.getAddonMoveItemTargetSpace(),
            "argument keys and values must be case-insensitive");
    }

    @Test
    void invalidSlotSpaceValuesAreRejectedWithoutSideEffects() {
        Node node = new Node(NodeType.MOVE_ITEM, 0, 0);

        String unknown = AddonActionInvoker.applySlotSpaceArg(node, "SourceSpace", "hotbar");
        assertNotNull(unknown, "unknown space values must be rejected as caller errors");
        assertTrue(unknown.contains("SourceSpace"), "error must name the argument, got: " + unknown);
        assertNull(node.getAddonMoveItemSourceSpace());

        assertNotNull(AddonActionInvoker.applySlotSpaceArg(node, "TargetSpace", 2.0));
        assertNull(node.getAddonMoveItemTargetSpace());
    }

    @Test
    void executorResolutionPrefersTheOverrides() {
        Node node = new Node(NodeType.MOVE_ITEM, 0, 0);
        NodeInventoryCommandExecutor executor = new NodeInventoryCommandExecutor(node);

        // Default without attached parameters: the player-inventory space.
        assertEquals(SlotSelectionType.PLAYER_INVENTORY,
            executor.resolveMoveItemSlotSelectionTypeForTests(null, 0));
        assertEquals(SlotSelectionType.PLAYER_INVENTORY,
            executor.resolveMoveItemSlotSelectionTypeForTests(null, 1));

        node.setAddonMoveItemSourceSpace(SlotSelectionType.GUI_CONTAINER);
        assertEquals(SlotSelectionType.GUI_CONTAINER,
            executor.resolveMoveItemSlotSelectionTypeForTests(null, 0),
            "source override must win for parameter slot 0");
        assertEquals(SlotSelectionType.PLAYER_INVENTORY,
            executor.resolveMoveItemSlotSelectionTypeForTests(null, 1),
            "target resolution must not read the source override");

        node.setAddonMoveItemTargetSpace(SlotSelectionType.GUI_CONTAINER);
        assertEquals(SlotSelectionType.GUI_CONTAINER,
            executor.resolveMoveItemSlotSelectionTypeForTests(null, 1));
    }
}
