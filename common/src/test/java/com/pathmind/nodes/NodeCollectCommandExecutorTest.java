package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCollectCommandExecutorTest {

    @Test
    void fullyEmbeddedTargetIsNotApproachable() {
        BlockPos target = new BlockPos(12, -54, 7);

        assertFalse(NodeCollectCommandExecutor.hasPassableFaceNeighbor(pos -> false, target),
            "a target with six solid face neighbours has no goal cell reachable without breaking");
    }

    @Test
    void anySinglePassableFaceNeighborMakesTargetApproachable() {
        BlockPos target = new BlockPos(-3, 60, 141);

        for (Direction openSide : Direction.values()) {
            Set<BlockPos> passable = new HashSet<>();
            passable.add(target.offset(openSide));

            assertTrue(NodeCollectCommandExecutor.hasPassableFaceNeighbor(passable::contains, target),
                "one open face neighbour (" + openSide + ") must keep the approach goto plausible");
        }
    }

    @Test
    void diagonalOrDistantOpeningsDoNotCount() {
        BlockPos target = new BlockPos(0, 10, 0);
        Set<BlockPos> passable = new HashSet<>();
        passable.add(target.add(1, 1, 0));
        passable.add(target.add(0, 2, 0));

        assertFalse(NodeCollectCommandExecutor.hasPassableFaceNeighbor(passable::contains, target),
            "only the six face neighbours lie within the GoalNear(target, 1) radius");
    }

    @Test
    void nullTargetIsNotApproachable() {
        assertFalse(NodeCollectCommandExecutor.hasPassableFaceNeighbor(pos -> true, null));
    }
}
