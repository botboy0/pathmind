package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit tests for {@link AddonActionInvoker} — the validation layer of the
 * v2 {@code invokeAction} script dispatch. Everything testable without a Minecraft
 * client: name resolution, the category gate, and the headless-client failure mode.
 * (The client-thread dispatch itself is covered by the in-game vision spec
 * {@code lua-invoke-action.yaml} in mc-testkit.)
 */
class AddonActionInvokerTest {

    private static Throwable failureOf(CompletableFuture<Void> future) {
        assertTrue(future.isCompletedExceptionally(), "future must complete exceptionally");
        try {
            future.get();
            return null; // unreachable
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    @Test
    void unknownActionNameFailsWithClearMessage() {
        Throwable cause = failureOf(AddonActionInvoker.invoke("frobnicate", Map.of()));
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("frobnicate"),
            "error must name the unknown action, got: " + cause.getMessage());
    }

    @Test
    void nullAndBlankNamesFail() {
        assertInstanceOf(IllegalArgumentException.class, failureOf(AddonActionInvoker.invoke(null, null)));
        assertInstanceOf(IllegalArgumentException.class, failureOf(AddonActionInvoker.invoke("  ", null)));
    }

    @Test
    void flowAndSensorActionsAreRejected() {
        // Flow control, sensors, data ops, and parameter nodes are graph-only surface.
        for (String blocked : new String[]{
                "control_forever", "start_chain", "stop_all", "run_preset",
                "sensor_is_daytime", "set_variable", "create_list", "param_amount",
                "sticky_note", "addon"}) {
            Throwable cause = failureOf(AddonActionInvoker.invoke(blocked, null));
            assertInstanceOf(IllegalArgumentException.class, cause,
                blocked + " must be rejected");
            assertTrue(cause.getMessage().contains("not invocable"),
                blocked + " rejection must say not-invocable, got: " + cause.getMessage());
        }
    }

    @Test
    void invocableCategoriesAreWorldPlayerInterface() {
        assertTrue(AddonActionInvoker.isInvocable(NodeType.JUMP));
        assertTrue(AddonActionInvoker.isInvocable(NodeType.MESSAGE));
        assertTrue(AddonActionInvoker.isInvocable(NodeType.GOTO));
        assertFalse(AddonActionInvoker.isInvocable(NodeType.STICKY_NOTE));
        assertFalse(AddonActionInvoker.isInvocable(NodeType.ADDON));
        assertFalse(AddonActionInvoker.isInvocable(NodeType.CONTROL_IF));
        assertFalse(AddonActionInvoker.isInvocable(NodeType.SENSOR_IS_RAINING));
    }

    @Test
    void waitIsTheSingleInvocableFlowException() {
        // Scripts need a native timed pause (e.g. letting a GUI open between an
        // interact and a craft); every other FLOW node stays graph-only.
        assertTrue(AddonActionInvoker.isInvocable(NodeType.WAIT));
        assertFalse(AddonActionInvoker.isInvocable(NodeType.START));
        assertFalse(AddonActionInvoker.isInvocable(NodeType.STOP_CHAIN));
        assertFalse(AddonActionInvoker.isInvocable(NodeType.CONTROL_WAIT_UNTIL));
    }

    @Test
    void actionNamesAreCaseInsensitive() {
        // In a headless test the client is null, so a VALID name+category must fail
        // with the client-unavailable IllegalStateException — proving it passed the
        // name and category gates first.
        Throwable cause = failureOf(AddonActionInvoker.invoke("JuMp", null));
        assertInstanceOf(IllegalStateException.class, cause,
            "valid action in headless context must fail on the missing client, got: " + cause);
    }

    @Test
    void recordedNodeFailureFailsAddonInvocation() {
        CompletableFuture<Void> invocationFuture = new CompletableFuture<>();

        AddonActionInvoker.completeInvocation(invocationFuture, null, 7L, 8L,
            "Attempted placement did not appear at 34, 116, 55");

        Throwable cause = failureOf(invocationFuture);
        assertEquals("Attempted placement did not appear at 34, 116, 55", cause.getMessage());
    }

    @Test
    void unchangedFailureCountCompletesAddonInvocationNormally() {
        CompletableFuture<Void> invocationFuture = new CompletableFuture<>();

        AddonActionInvoker.completeInvocation(invocationFuture, null, 7L, 7L, null);

        assertTrue(invocationFuture.isDone());
        assertFalse(invocationFuture.isCompletedExceptionally());
    }

    @Test
    void exceptionalActionFailureIsPreserved() {
        CompletableFuture<Void> invocationFuture = new CompletableFuture<>();
        IllegalStateException actionFailure = new IllegalStateException("dispatch failed");

        AddonActionInvoker.completeInvocation(invocationFuture, actionFailure, 7L, 8L, "node failed");

        assertSame(actionFailure, failureOf(invocationFuture));
    }
}
