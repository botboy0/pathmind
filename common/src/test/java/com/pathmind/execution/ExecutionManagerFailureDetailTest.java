package com.pathmind.execution;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the failure-detail side of the node-failure observability record in
 * {@link ExecutionManager}: the detail always belongs to the most recent failure —
 * a classified failure stores it, an unclassified one clears it (the envelope layer
 * snapshots it right after the action and must never see a stale detail).
 */
class ExecutionManagerFailureDetailTest {

    @Test
    void classifiedFailureRecordsDetailMessageAndBumpsCount() {
        ExecutionManager manager = ExecutionManager.getInstance();
        long countBefore = manager.getNodeFailureCount();
        FailureDetail detail = FailureDetail.missingResource(List.of("minecraft:stick"));

        manager.recordNodeFailure("Cannot craft Stone Pickaxe: missing required ingredients.", detail);

        assertEquals(countBefore + 1, manager.getNodeFailureCount());
        assertEquals("Cannot craft Stone Pickaxe: missing required ingredients.", manager.getLastNodeFailureMessage());
        assertSame(detail, manager.getLastNodeFailureDetail());
    }

    @Test
    void unclassifiedFailureClearsAnyEarlierDetail() {
        ExecutionManager manager = ExecutionManager.getInstance();
        manager.recordNodeFailure("classified", FailureDetail.notFound());

        long countBefore = manager.getNodeFailureCount();
        manager.recordNodeFailure("plain unclassified failure");

        assertEquals(countBefore + 1, manager.getNodeFailureCount());
        assertEquals("plain unclassified failure", manager.getLastNodeFailureMessage());
        assertNull(manager.getLastNodeFailureDetail());
    }

    @Test
    void classifiedFailureWithNullDetailBehavesLikeUnclassified() {
        ExecutionManager manager = ExecutionManager.getInstance();
        manager.recordNodeFailure("classified", FailureDetail.precondition());

        manager.recordNodeFailure("null detail", null);

        assertNull(manager.getLastNodeFailureDetail());
        assertEquals("null detail", manager.getLastNodeFailureMessage());
    }
}
