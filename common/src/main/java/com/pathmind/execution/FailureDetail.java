package com.pathmind.execution;

import java.util.List;
import java.util.Map;

/**
 * Structured classification of a node failure, recorded alongside the failure
 * message at {@code NodeExecutionCompletion.fail} call sites that opt into
 * classification (fork-side action-result-envelope support; see the
 * action-result-envelopes design doc).
 *
 * <p>Carries the symbolic status string of the v1 vocabulary plus optional
 * machine-readable detail fields. Unclassified failures record no detail —
 * the addon layer maps those to status {@code "failed"}.
 */
public final class FailureDetail {

    /** Required items/blocks absent. Detail field {@code missing}: list of ids. */
    public static FailureDetail missingResource(List<String> missingItemIds) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** Required state absent (GUI not open, wrong screen); establish it and retry. */
    public static FailureDetail precondition() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** Timing/desync/blocked route; wait and retry. */
    public static FailureDetail transientFailure() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** The request cannot work this way (e.g. 3x3 recipe in the 2x2 player grid). */
    public static FailureDetail unsupported() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** Target entity/item/player not found nearby. */
    public static FailureDetail notFound() {
        throw new UnsupportedOperationException("not implemented");
    }

    private FailureDetail() {
    }

    /** Symbolic status string (v1 vocabulary). Never null. */
    public String getStatus() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** Machine-readable detail fields. Never null; unmodifiable. */
    public Map<String, Object> getFields() {
        throw new UnsupportedOperationException("not implemented");
    }
}
