package com.pathmind.execution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live script-text view for ADDON nodes — the forward counterpart of
 * {@link AddonRuntimeErrors}, enabling script hot-reload without a graph restart.
 *
 * <p>Execution runs on branch clones whose extra fields are frozen at chain start
 * ({@code ExecutionManager.createGraphSnapshot} → {@code convertToNodes}), so a script
 * edited in the node editor while a chain is running would otherwise only take effect
 * after stopping and restarting the graph. This registry closes that gap:
 *
 * <ul>
 *   <li>{@code NodeGraph} {@link #publish publishes} the workspace node's script here
 *       keyed by the stable {@code _node_id} (a UUID persisted in the extra-fields blob
 *       and preserved across clones and save/load) — from {@code buildAddonContext} on
 *       every rendered frame / input event, and from {@code syncAddonContextToNode}
 *       right after each edit. The per-frame republish makes the channel self-correct
 *       after any graph restore (screen reopen, preset load, undo/redo): it always
 *       converges to exactly what the editor shows.</li>
 *   <li>{@code Node.executeAddonNode} {@link #get reads} the published text at each
 *       execution and prefers it over the clone's frozen {@code "script"} field — so a
 *       loop re-executing a Script node picks up edits on its next iteration.</li>
 * </ul>
 *
 * <p>Entries are evicted ONLY on discrete node deletion ({@code NodeGraph.removeNodeInternal}
 * via {@code evictAddonLiveScript}) — deliberately NOT on the graph-replacement paths
 * (screen reopen, preset load, undo/redo restore), where the same {@code _node_id}
 * returns immediately and a chain running across the replacement must keep resolving
 * its live script (found by the lua-editor-v2 spec: closing the editor mid-run evicted
 * the entry and execution fell back to the frozen snapshot). Node ids are UUIDs and
 * never reused, so a leftover entry can never be read by a different node.
 *
 * <p>Hot-reload granularity is per-execution: an edit never interrupts a script run
 * already in flight; it applies from the next execution of that node onward.
 */
public final class AddonLiveScripts {

    private static final Map<String, String> LATEST = new ConcurrentHashMap<>();

    private AddonLiveScripts() {
        // Static utility — do not instantiate.
    }

    /**
     * Records the latest editor script text for a node, replacing any earlier value.
     *
     * @param nodeId stable addon node id ({@code _node_id}); ignored if null/blank
     * @param script current script text; ignored if null (context never saw a script)
     */
    public static void publish(String nodeId, String script) {
        if (nodeId == null || nodeId.isBlank() || script == null) {
            return;
        }
        LATEST.put(nodeId, script);
    }

    /**
     * Returns the latest published script text for a node, or null if the node was
     * never edited this session (callers fall back to the execution clone's frozen
     * {@code "script"} extra field).
     *
     * @param nodeId stable addon node id
     * @return latest script text, or null
     */
    public static String get(String nodeId) {
        return nodeId == null ? null : LATEST.get(nodeId);
    }

    /**
     * Evicts a node's entry when it leaves the graph (deletion, preset load, workspace
     * clear). Safe to call for ids that were never published.
     *
     * @param nodeId stable addon node id; ignored if null
     */
    public static void remove(String nodeId) {
        if (nodeId != null) {
            LATEST.remove(nodeId);
        }
    }
}
