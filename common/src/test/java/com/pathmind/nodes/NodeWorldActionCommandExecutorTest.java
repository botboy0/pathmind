package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeWorldActionCommandExecutorTest {

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
