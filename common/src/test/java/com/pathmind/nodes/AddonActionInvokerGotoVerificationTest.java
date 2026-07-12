package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import com.pathmind.api.addon.ActionResult;
import com.pathmind.execution.FailureDetail;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GOTO arrival verification (fork-only, invoker layer — graph nodes untouched).
 *
 * <p>The mission-primitives research showed GOTO can resolve {@code ok=true}
 * without arriving: the tracker watches process inactivity, never position.
 * The fix asks Baritone's own goal criterion: the navigation executor stores the
 * goal object it created on the synthetic node ({@code setAddonGotoGoal}), and
 * after a nominally successful run the invoker checks
 * {@code goal.isInGoal(finalBlockPos)} reflectively (duck-typed, like every
 * other Baritone touchpoint). A miss classifies as {@code off_target}; a
 * tracker-recorded graceful stop ("never became active") classifies as
 * {@code no_route}.
 */
class AddonActionInvokerGotoVerificationTest {

    /** Duck-typed stand-in for a Baritone {@code Goal} — reflection matches by name. */
    static final class RecordingGoal {
        final boolean inGoal;
        Integer sawX, sawY, sawZ;

        RecordingGoal(boolean inGoal) {
            this.inGoal = inGoal;
        }

        public boolean isInGoal(int x, int y, int z) {
            this.sawX = x;
            this.sawY = y;
            this.sawZ = z;
            return inGoal;
        }
    }

    // ------------------------------------------------------------------
    // verifyGotoArrival — pure seam
    // ------------------------------------------------------------------

    @Test
    void arrivalInsideTheGoalVerifiesClean() {
        Node node = new Node(NodeType.GOTO, 0, 0);
        node.setAddonGotoGoal(new RecordingGoal(true));

        assertNull(AddonActionInvoker.verifyGotoArrival(node, new double[]{10.5, 64.0, -3.5}),
            "a final position satisfying the goal must not produce a failure detail");
    }

    @Test
    void arrivalOutsideTheGoalClassifiesAsOffTarget() {
        Node node = new Node(NodeType.GOTO, 0, 0);
        node.setAddonGotoGoal(new RecordingGoal(false));

        FailureDetail detail = AddonActionInvoker.verifyGotoArrival(node, new double[]{10.5, 64.0, -3.5});
        assertNotNull(detail, "missing the goal must produce a failure detail");
        assertEquals("off_target", detail.getStatus());
    }

    @Test
    void verificationFloorsThePositionToBlockCoordinates() {
        Node node = new Node(NodeType.GOTO, 0, 0);
        RecordingGoal goal = new RecordingGoal(true);
        node.setAddonGotoGoal(goal);

        AddonActionInvoker.verifyGotoArrival(node, new double[]{1.9, 64.0, -2.1});

        assertEquals(1, goal.sawX);
        assertEquals(64, goal.sawY);
        assertEquals(-3, goal.sawZ, "negative coordinates must floor, not truncate");
    }

    @Test
    void verificationSkipsWhenNoGoalWasStored() {
        // The getToBlock fallback path has no goal object; verification must not
        // false-fail there (documented non-guarantee instead).
        Node node = new Node(NodeType.GOTO, 0, 0);

        assertNull(AddonActionInvoker.verifyGotoArrival(node, new double[]{0.0, 64.0, 0.0}));
        assertNull(AddonActionInvoker.verifyGotoArrival(node, null),
            "missing position snapshot must skip verification");
    }

    @Test
    void goalObjectsWithoutIsInGoalSkipVerification() {
        // A goal whose reflective check cannot run (unexpected shape) must not
        // turn a successful action into a failure.
        Node node = new Node(NodeType.GOTO, 0, 0);
        node.setAddonGotoGoal(new Object());

        assertNull(AddonActionInvoker.verifyGotoArrival(node, new double[]{0.0, 64.0, 0.0}));
    }

    // ------------------------------------------------------------------
    // completeInvocation overload — post-success verification mapping
    // ------------------------------------------------------------------

    @Test
    void postSuccessFailureDowngradesSuccessToClassifiedFailure() {
        CompletableFuture<ActionResult> future = new CompletableFuture<>();

        AddonActionInvoker.completeInvocation(future, null, 7L, 7L, null, null,
            Map.of("x", 10.5, "y", 64.0, "z", -3.5),
            FailureDetail.offTarget(), "GOTO finished off target at 10, 64, -4");

        ActionResult result = future.join();
        assertFalse(result.isOk());
        assertEquals("off_target", result.getStatus());
        assertEquals("GOTO finished off target at 10, 64, -4", result.getMessage());
    }

    @Test
    void neverStartedGracefulStopMapsToNoRoute() {
        CompletableFuture<ActionResult> future = new CompletableFuture<>();

        AddonActionInvoker.completeInvocation(future, null, 7L, 7L, null, null,
            Map.of(), FailureDetail.noRoute(),
            "Baritone could not start the goto task (path unavailable)");

        ActionResult result = future.join();
        assertFalse(result.isOk());
        assertEquals("no_route", result.getStatus());
    }

    @Test
    void recordedNodeFailureTakesPrecedenceOverPostSuccessCheck() {
        CompletableFuture<ActionResult> future = new CompletableFuture<>();

        AddonActionInvoker.completeInvocation(future, null, 7L, 8L,
            "target block not found nearby", FailureDetail.notFound(),
            Map.of(), FailureDetail.offTarget(), "should not appear");

        ActionResult result = future.join();
        assertEquals("not_found", result.getStatus(),
            "an actually recorded failure must win over the arrival check");
        assertEquals("target block not found nearby", result.getMessage());
    }

    @Test
    void withoutPostSuccessFailureTheOverloadBehavesLikeSuccess() {
        CompletableFuture<ActionResult> future = new CompletableFuture<>();

        AddonActionInvoker.completeInvocation(future, null, 7L, 7L, null, null,
            Map.of("x", 1.0), null, null);

        ActionResult result = future.join();
        assertTrue(result.isOk());
        assertEquals(1.0, result.getFields().get("x"));
    }
}
