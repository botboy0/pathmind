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
    private final String status;
    private final Map<String, Object> fields;

    /** Required items/blocks absent. Detail field {@code missing}: list of ids. */
    public static FailureDetail missingResource(List<String> missingItemIds) {
        List<String> missing = missingItemIds == null ? List.of() : List.copyOf(missingItemIds);
        return new FailureDetail("missing_resource", Map.of("missing", missing));
    }

    /** Required state absent (GUI not open, wrong screen); establish it and retry. */
    public static FailureDetail precondition() {
        return new FailureDetail("precondition", Map.of());
    }

    /** Timing/desync/blocked route; wait and retry. */
    public static FailureDetail transientFailure() {
        return new FailureDetail("transient", Map.of());
    }

    /** The request cannot work this way (e.g. 3x3 recipe in the 2x2 player grid). */
    public static FailureDetail unsupported() {
        return new FailureDetail("unsupported", Map.of());
    }

    /** Target entity/item/player not found nearby. */
    public static FailureDetail notFound() {
        return new FailureDetail("not_found", Map.of());
    }

    private FailureDetail(String status, Map<String, Object> fields) {
        this.status = status;
        this.fields = Map.copyOf(fields);
    }

    /** Symbolic status string (v1 vocabulary). Never null. */
    public String getStatus() {
        return status;
    }

    /** Machine-readable detail fields. Never null; unmodifiable. */
    public Map<String, Object> getFields() {
        return fields;
    }
}
