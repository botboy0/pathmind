package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeWorldActionCommandExecutorTest {

    @Test
    void confirmedPlacementCompletesNormally() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        NodeWorldActionCommandExecutor.completeVerifiedPlacement(future, true, "not placed");

        assertTrue(future.isDone());
        assertDoesNotThrow(future::join);
    }

    @Test
    void unconfirmedPlacementFailsWithClearMessage() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        NodeWorldActionCommandExecutor.completeVerifiedPlacement(future, false,
            "crafting table did not appear at 34, 116, 55");

        CompletionException failure = org.junit.jupiter.api.Assertions.assertThrows(
            CompletionException.class, future::join);
        assertInstanceOf(Node.PlacementFailure.class, failure.getCause());
        assertEquals("crafting table did not appear at 34, 116, 55", failure.getCause().getMessage());
    }

    @Test
    void placementFacePointsFromSupportIntoRequestedTarget() {
        BlockPos target = new BlockPos(34, 116, 55);

        for (Direction supportDirection : Direction.values()) {
            BlockPos support = target.offset(supportDirection);
            Direction placementSide = NodeWorldActionCommandExecutor.placementSideTowardTarget(supportDirection);

            assertEquals(target, support.offset(placementSide),
                "support face must place into the requested target for " + supportDirection);
        }
    }
}
