package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import com.pathmind.api.addon.ActionResult;
import com.pathmind.execution.FailureDetail;

import java.util.List;
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

    private static Throwable failureOf(CompletableFuture<?> future) {
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
    void modeArgumentSelectsANonDefaultMode() {
        Node node = new Node(NodeType.GOTO, 0, 0);
        assertNull(AddonActionInvoker.applyMode(node, NodeType.GOTO, "goto_block"));
        assertEquals(NodeMode.GOTO_BLOCK, node.getMode());
        assertTrue(node.getParameters().stream().anyMatch(p -> p.getName().equals("Block")),
            "goto_block mode must expose the Block parameter");
    }

    @Test
    void invalidModeArgumentsFailWithTheValidList() {
        Node node = new Node(NodeType.GOTO, 0, 0);
        String unknown = AddonActionInvoker.applyMode(node, NodeType.GOTO, "warp_drive");
        assertNotNull(unknown);
        assertTrue(unknown.contains("goto_xz"), "error must list valid modes, got: " + unknown);

        String wrongType = AddonActionInvoker.applyMode(node, NodeType.GOTO, "collect_single");
        assertNotNull(wrongType);
        assertTrue(wrongType.contains("does not belong"), "got: " + wrongType);

        assertNotNull(AddonActionInvoker.applyMode(node, NodeType.GOTO, 42.0));
    }

    // ---------------------------------------------------------------------
    // completeInvocation — the three-class envelope mapping
    // (action-result-envelopes design: class 2 returns, classes 1/3 raise)
    // ---------------------------------------------------------------------

    @Test
    void recordedNodeFailureResolvesToFailureEnvelope() {
        CompletableFuture<ActionResult> invocationFuture = new CompletableFuture<>();

        AddonActionInvoker.completeInvocation(invocationFuture, null, 7L, 8L,
            "Attempted placement did not appear at 34, 116, 55", null, null);

        ActionResult result = invocationFuture.join();
        assertFalse(result.isOk());
        assertEquals("failed", result.getStatus(),
            "an unclassified node failure must map to the 'failed' fallback status");
        assertEquals("Attempted placement did not appear at 34, 116, 55", result.getMessage());
        assertTrue(result.getFields().isEmpty());
    }

    @Test
    void classifiedNodeFailureCarriesStatusAndDetailFields() {
        CompletableFuture<ActionResult> invocationFuture = new CompletableFuture<>();
        FailureDetail detail = FailureDetail.missingResource(List.of("minecraft:stick"));

        AddonActionInvoker.completeInvocation(invocationFuture, null, 7L, 8L,
            "Cannot craft Stone Pickaxe: missing required ingredients.", detail, null);

        ActionResult result = invocationFuture.join();
        assertFalse(result.isOk());
        assertEquals("missing_resource", result.getStatus());
        assertEquals("Cannot craft Stone Pickaxe: missing required ingredients.", result.getMessage());
        assertEquals(List.of("minecraft:stick"), result.getFields().get("missing"));
    }

    @Test
    void blankFailureMessageFallsBackToGenericText() {
        CompletableFuture<ActionResult> invocationFuture = new CompletableFuture<>();

        AddonActionInvoker.completeInvocation(invocationFuture, null, 7L, 8L, "  ", null, null);

        assertEquals("Pathmind action failed", invocationFuture.join().getMessage());
    }

    @Test
    void unchangedFailureCountResolvesToSuccessEnvelope() {
        CompletableFuture<ActionResult> invocationFuture = new CompletableFuture<>();

        // A stale message/detail from an EARLIER failure must be ignored when the
        // counter did not move during this action.
        AddonActionInvoker.completeInvocation(invocationFuture, null, 7L, 7L,
            "stale message from a previous failure", FailureDetail.notFound(),
            Map.of("produced", 4));

        ActionResult result = invocationFuture.join();
        assertTrue(result.isOk());
        assertNull(result.getStatus());
        assertNull(result.getMessage());
        assertEquals(4, result.getFields().get("produced"));
    }

    @Test
    void successWithoutFieldsResolvesToBareOkEnvelope() {
        CompletableFuture<ActionResult> invocationFuture = new CompletableFuture<>();

        AddonActionInvoker.completeInvocation(invocationFuture, null, 7L, 7L, null, null, null);

        ActionResult result = invocationFuture.join();
        assertTrue(result.isOk());
        assertNotNull(result.getFields());
        assertTrue(result.getFields().isEmpty());
    }

    @Test
    void exceptionalActionFailureIsPreserved() {
        CompletableFuture<ActionResult> invocationFuture = new CompletableFuture<>();
        IllegalStateException actionFailure = new IllegalStateException("dispatch failed");

        AddonActionInvoker.completeInvocation(invocationFuture, actionFailure, 7L, 8L,
            "node failed", null, null);

        assertSame(actionFailure, failureOf(invocationFuture));
    }

    // ---------------------------------------------------------------------
    // successFields — the pure snapshot-diff seam (v1: craft/collect/goto)
    // ---------------------------------------------------------------------

    @Test
    void craftSuccessFieldsReportProducedDelta() {
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.CRAFT, Map.of("Item", "oak_planks"),
            Map.of("minecraft:oak_planks", 2), Map.of("minecraft:oak_planks", 6), null);

        assertEquals(4, fields.get("produced"));
    }

    @Test
    void craftItemArgumentIsCaseInsensitiveAndNamespaced() {
        // Bare id + lowercase key, item absent from the before-snapshot (counts as 0).
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.CRAFT, Map.of("item", "stick"),
            Map.of(), Map.of("minecraft:stick", 4), null);

        assertEquals(4, fields.get("produced"));

        Map<String, Object> namespaced = AddonActionInvoker.successFields(
            NodeType.CRAFT, Map.of("Item", "minecraft:stick"),
            Map.of(), Map.of("minecraft:stick", 4), null);

        assertEquals(4, namespaced.get("produced"));
    }

    @Test
    void craftProducedDeltaIsFlooredAtZero() {
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.CRAFT, Map.of("Item", "oak_planks"),
            Map.of("minecraft:oak_planks", 5), Map.of("minecraft:oak_planks", 3), null);

        assertEquals(0, fields.get("produced"));
    }

    @Test
    void collectSuccessFieldsReportCollectedDeltaOfTheBlockArgument() {
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.COLLECT, Map.of("Block", "oak_log", "Amount", 2.0),
            Map.of(), Map.of("minecraft:oak_log", 2), null);

        assertEquals(2, fields.get("collected"));
    }

    @Test
    void gotoSuccessFieldsReportTheFinalPosition() {
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.GOTO, Map.of("X", 1.0, "Z", -3.0),
            null, null, new double[]{1.5, 64.0, -3.5});

        assertEquals(1.5, fields.get("x"));
        assertEquals(64.0, fields.get("y"));
        assertEquals(-3.5, fields.get("z"));
    }

    @Test
    void gotoWithoutPositionSnapshotYieldsEmptyFields() {
        Map<String, Object> fields = AddonActionInvoker.successFields(
            NodeType.GOTO, Map.of("X", 1.0), null, null, null);

        assertNotNull(fields);
        assertTrue(fields.isEmpty());
    }

    @Test
    void otherActionsYieldEmptySuccessFields() {
        for (NodeType type : new NodeType[]{NodeType.JUMP, NodeType.MESSAGE, NodeType.WAIT}) {
            Map<String, Object> fields = AddonActionInvoker.successFields(
                type, Map.of(), Map.of(), Map.of("minecraft:stick", 4), new double[]{1, 2, 3});
            assertNotNull(fields, type.name());
            assertTrue(fields.isEmpty(), type.name() + " must carry no success fields in v1");
        }
    }
}
