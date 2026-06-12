package com.pathmind.api.addon;

/**
 * Result of an addon node execution. Returned by {@link AddonNodeExecutor#execute}.
 *
 * <p>Part of the Pathmind addon API — API-06 async execution contract.
 */
public enum NodeResult {

    /**
     * Node completed successfully; the graph advances to the next node.
     */
    SUCCESS,

    /**
     * Node failed; the graph follows the failure path if one is connected, else halts.
     */
    FAILURE,

    /**
     * Node was skipped (e.g., unresolved type or no-op condition);
     * the graph advances as if SUCCESS.
     */
    SKIPPED
}
