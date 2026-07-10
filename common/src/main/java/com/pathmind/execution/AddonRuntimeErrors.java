package com.pathmind.execution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges last-run error state from executing ADDON nodes back to the workspace
 * nodes the editor renders.
 *
 * <p>Execution runs on cloned branch nodes ({@code ExecutionManager.createBranchLaunchData}),
 * so error state an addon executor writes to its {@code AddonNodeContext} never reaches
 * the workspace node the user sees. Entries here are keyed by the stable addon
 * {@code _node_id} (persisted in the node's extra-fields blob and preserved across
 * clones): {@code Node.executeAddonNode} publishes error mutations via the context's
 * error-change listener, and {@code NodeGraph.buildAddonContext} consumes pending
 * entries into the workspace node's extra fields on the next editor frame — from where
 * the error strip renders and preset persistence picks them up.
 *
 * <p>A cleared error (successful run) is stored as an entry with a {@code null} message
 * so the editor also clears its strip; absence of an entry means "no news since the
 * last consume".
 */
public final class AddonRuntimeErrors {

    /**
     * One pending error write-back.
     *
     * @param message error message, or null for "cleared on success"
     * @param line    1-based error line, or 0 if unknown / no error
     */
    public record Entry(String message, int line) {
    }

    private static final Map<String, Entry> PENDING = new ConcurrentHashMap<>();

    private AddonRuntimeErrors() {
        // Static utility — do not instantiate.
    }

    /**
     * Records the latest error state for a node, replacing any earlier pending entry.
     *
     * @param nodeId  stable addon node id ({@code _node_id}); ignored if null
     * @param message error message, or null for "cleared on success"
     * @param line    1-based error line, or 0
     */
    public static void put(String nodeId, String message, int line) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        PENDING.put(nodeId, new Entry(message, line));
    }

    /**
     * Removes and returns the pending entry for a node, or null if none.
     *
     * @param nodeId stable addon node id
     * @return the pending entry, or null
     */
    public static Entry consume(String nodeId) {
        return nodeId == null ? null : PENDING.remove(nodeId);
    }
}
