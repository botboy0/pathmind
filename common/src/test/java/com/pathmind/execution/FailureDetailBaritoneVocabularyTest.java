package com.pathmind.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the Baritone-integration additions to the status vocabulary
 * (action-result-envelopes design; agreed with the maintainer 2026-07-13):
 *
 * <ul>
 *   <li>{@code no_route} — Baritone never started the navigation task
 *       (path unavailable / task never became active). Waiting will not help;
 *       scripts should reposition or enable per-call {@code AllowBreak}.</li>
 *   <li>{@code off_target} — Baritone ran and stopped, but the final position
 *       does not satisfy the goal ({@code Goal.isInGoal} is false).</li>
 * </ul>
 */
class FailureDetailBaritoneVocabularyTest {

    @Test
    void noRouteCarriesItsStatusAndNoFields() {
        FailureDetail detail = FailureDetail.noRoute();
        assertEquals("no_route", detail.getStatus());
        assertNotNull(detail.getFields());
        assertTrue(detail.getFields().isEmpty());
    }

    @Test
    void offTargetCarriesItsStatusAndNoFields() {
        FailureDetail detail = FailureDetail.offTarget();
        assertEquals("off_target", detail.getStatus());
        assertNotNull(detail.getFields());
        assertTrue(detail.getFields().isEmpty());
    }
}
