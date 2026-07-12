package com.pathmind.api.addon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result envelope of a script-invoked action ({@link PathmindRuntime#invokeAction}).
 *
 * <p>Expected action failures — the world saying no (missing ingredients, no matching
 * hotbar item, target not found) — are <em>returned</em> as an ActionResult with
 * {@code ok == false} and a symbolic {@link #getStatus() status}, so scripts can plan
 * around them. Exceptions on the invocation future remain reserved for caller errors
 * (unknown action, bad argument) and mod-internal errors.
 *
 * <p><strong>Status vocabulary v1</strong> (symbolic strings, never numbers):
 * {@code missing_resource}, {@code precondition}, {@code transient},
 * {@code unsupported}, {@code not_found}, and the unclassified fallback
 * {@code failed}. Per-status machine-readable detail lives in {@link #getFields()}
 * (e.g. {@code missing} — the list of missing ingredient ids for
 * {@code missing_resource}). Successful results carry action-specific fields
 * (e.g. {@code produced} for craft) and no status.
 */
public final class ActionResult {
    private final boolean ok;
    private final String status;
    private final String message;
    private final Map<String, Object> fields;

    /**
     * Creates a successful result. {@code fields} carries action-specific data
     * (may be null or empty for actions without success data).
     */
    public static ActionResult success(Map<String, Object> fields) {
        return new ActionResult(true, null, null, fields);
    }

    /**
     * Creates an expected-failure result with a symbolic status, a human-readable
     * message, and optional machine-readable detail fields (may be null or empty).
     */
    public static ActionResult failure(String status, String message, Map<String, Object> fields) {
        return new ActionResult(false, status, message, fields);
    }

    private ActionResult(boolean ok, String status, String message, Map<String, Object> fields) {
        this.ok = ok;
        this.status = status;
        this.message = message;
        this.fields = fields == null || fields.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /** Whether the action succeeded. */
    public boolean isOk() {
        return ok;
    }

    /** Symbolic failure status (v1 vocabulary), or null on success. */
    public String getStatus() {
        return status;
    }

    /** Human-readable failure message, or null on success. */
    public String getMessage() {
        return message;
    }

    /**
     * Action-specific data fields — success data (e.g. {@code produced}) or
     * per-status failure detail (e.g. {@code missing}). Never null; unmodifiable.
     */
    public Map<String, Object> getFields() {
        return fields;
    }
}
