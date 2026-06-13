package com.pathmind.data;

import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeSerializer;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.NodeTypeRegistry;

import java.util.HashMap;

/**
 * Shared utility for copying ADDON-specific fields between live {@link Node} instances
 * and their serializable {@link NodeGraphData.NodeData} counterparts.
 *
 * <p>Centralises the ADDON field copy/restore logic that was previously duplicated
 * across every Node&lt;&gt;NodeData conversion site. The four sites that convert nodes
 * (NodeGraphPersistence, NodeGraph.applyLoadedData, NodeGraphClipboardSupport,
 * ExecutionManager.createGraphSnapshot) all need the same two operations; this class
 * is the single canonical encoding for all four conversion sites.
 * NodeGraphPersistence delegates its ADDON branches to this class, keeping only the
 * null-addonTypeId {@code continue} skip decision inline at the save call site.
 *
 * <p>Mirrors the exact behaviour implemented in NodeGraphPersistence:
 * <ul>
 *   <li>Save direction ({@code buildNodeGraphData}): {@link #copyAddonFieldsToNodeData}</li>
 *   <li>Load direction ({@code convertToNodes}): {@link #restoreAddonFieldsToNode}</li>
 * </ul>
 *
 * @see NodeGraphPersistence
 */
public final class AddonNodeDataCopy {

    private AddonNodeDataCopy() {
        // Utility class — do not instantiate.
    }

    /**
     * Copies the ADDON identity fields from a live {@link Node} onto a
     * {@link NodeGraphData.NodeData} record (the save/clone direction).
     *
     * <p>Delegates to the same encoding implemented in
     * {@code NodeGraphPersistence.buildNodeGraphData}.
     *
     * <p>Guards on {@code node.getType() == NodeType.ADDON}; silently no-ops for
     * all other node types so callers can invoke this unconditionally per node.
     *
     * <p>When {@code addonTypeId} is {@code null} this method logs a warning via
     * {@code System.err} and returns without setting any addon fields.
     * <strong>Callers SHOULD skip null-addonTypeId ADDON nodes before calling this
     * method</strong> (matching the on-disk save path's {@code continue} policy) —
     * this method omits addon fields for such nodes as a defensive fallback, but it
     * does not remove the node from any collection. Use a {@code continue} guard
     * before adding the node to any snapshot or clipboard list.
     *
     * @param node     the source live node
     * @param nodeData the target serializable record to populate
     */
    public static void copyAddonFieldsToNodeData(Node node, NodeGraphData.NodeData nodeData) {
        if (node.getType() != NodeType.ADDON) {
            return;
        }
        String addonTypeId = node.getAddonTypeId();
        if (addonTypeId == null) {
            System.err.println("[Pathmind] Skipping ADDON node with null addonTypeId during save (T-01-09)");
            return;
        }
        nodeData.setAddonTypeId(addonTypeId);
        AddonNodeSerializer ser = NodeTypeRegistry.INSTANCE.serializerFor(addonTypeId);
        if (ser != null) {
            // Addon is installed — serialize current state
            AddonNodeContext ctx = new AddonNodeContext();
            ctx.setAddonTypeId(addonTypeId);
            if (node.getAddonExtraFields() != null && node.getAddonExtraFields().containsKey("script")) {
                Object scriptObj = node.getAddonExtraFields().get("script");
                ctx.setScriptText(scriptObj != null ? scriptObj.toString() : null);
            }
            try {
                nodeData.setExtraFields(ser.serialize(ctx));
            } catch (Throwable t) {
                System.err.println("[Pathmind] Addon serializer threw for " + addonTypeId + ": " + t.getMessage());
                // Fall back to retained blob if any — defensive copy (WR-02)
                nodeData.setExtraFields(node.getAddonExtraFields() != null ? new HashMap<>(node.getAddonExtraFields()) : null);
            }
        } else {
            // Addon absent — placeholder re-save: write back retained blob verbatim (D-09)
            // Defensive copy to prevent aliasing across clipboard/snapshot restores (WR-02)
            nodeData.setExtraFields(node.getAddonExtraFields() != null ? new HashMap<>(node.getAddonExtraFields()) : null);
        }
    }

    /**
     * Restores the ADDON identity fields from a {@link NodeGraphData.NodeData} record
     * onto a freshly-constructed {@link Node} (the load direction).
     *
     * <p>Implements the ADDON load logic used by
     * {@code NodeGraphPersistence.convertToNodes}, including the
     * deserializer round-trip, the {@code "script"} key injection, the
     * {@code catch(Throwable)} fallback, and the missing-addon placeholder branch.
     *
     * <p>Guards on {@code nodeData.getType() == NodeType.ADDON && nodeData.getAddonTypeId() != null};
     * silently no-ops otherwise so callers can invoke this unconditionally per node.
     *
     * @param nodeData the source serializable record
     * @param node     the target live node to restore addon state onto
     */
    public static void restoreAddonFieldsToNode(NodeGraphData.NodeData nodeData, Node node) {
        if (nodeData.getType() != NodeType.ADDON || nodeData.getAddonTypeId() == null) {
            return;
        }
        String addonTypeId = nodeData.getAddonTypeId();
        node.setAddonTypeId(addonTypeId);
        if (NodeTypeRegistry.INSTANCE.hasType(addonTypeId)) {
            // Addon installed — deserialize into context and apply to node
            AddonNodeSerializer ser = NodeTypeRegistry.INSTANCE.serializerFor(addonTypeId);
            if (ser != null) {
                AddonNodeContext ctx = new AddonNodeContext();
                ctx.setAddonTypeId(addonTypeId);
                try {
                    ser.deserialize(ctx, nodeData.getExtraFields());
                    // Build the base map unconditionally so addonExtraFields is always non-null
                    // on the success path — mirrors the seeding pattern in Node(String,int,int).
                    // When on-disk extraFields is null (a freshly-placed never-edited node),
                    // start from an empty map so the serializer-seeded default survives (NEW-CR-02).
                    HashMap<String, Object> base = nodeData.getExtraFields() != null
                        ? new HashMap<>(nodeData.getExtraFields())
                        : new HashMap<>();
                    node.setAddonExtraFields(base);
                    // WR-05: clear any stale unresolved flag set by a prior addon-absent load
                    node.setAddonUnresolved(false);
                    // Inject serializer-seeded default (e.g. DEFAULT_SCRIPT) into the map so it
                    // survives even when the on-disk extraFields was null.
                    if (ctx.getScriptText() != null) {
                        node.getAddonExtraFields().put("script", ctx.getScriptText());
                    }
                } catch (Throwable t) {
                    System.err.println("[Pathmind] Addon deserializer threw for " + addonTypeId + ": " + t.getMessage());
                    // Defensive copy to prevent aliasing across multiple restores (WR-02)
                    node.setAddonExtraFields(nodeData.getExtraFields() != null ? new HashMap<>(nodeData.getExtraFields()) : null);
                    node.setAddonUnresolved(true);
                }
            }
        } else {
            // Addon absent — retain extraFields verbatim as placeholder (D-09)
            // Defensive copy to prevent aliasing across clipboard/snapshot restores (WR-02)
            node.setAddonExtraFields(nodeData.getExtraFields() != null ? new HashMap<>(nodeData.getExtraFields()) : null);
            node.setAddonUnresolved(true);
        }
    }
}
