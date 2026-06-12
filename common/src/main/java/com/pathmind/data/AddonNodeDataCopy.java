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
 * is the single canonical encoding for the three previously-broken sites.
 * NodeGraphPersistence keeps its own inline ADDON branches because it owns the
 * primary on-disk load/save path; this class is used by the secondary conversion sites.
 *
 * <p>Mirrors the exact behaviour documented in NodeGraphPersistence:
 * <ul>
 *   <li>Save direction (lines 854–882): {@link #copyAddonFieldsToNodeData}</li>
 *   <li>Load direction (lines 339–368): {@link #restoreAddonFieldsToNode}</li>
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
     * <p>Replicates the ADDON save logic from
     * {@code NodeGraphPersistence.buildNodeGraphData} lines 854–882.
     *
     * <p>Guards on {@code node.getType() == NodeType.ADDON}; silently no-ops for
     * all other node types so callers can invoke this unconditionally per node.
     *
     * <p>When {@code addonTypeId} is {@code null} this method logs a warning via
     * {@code System.err} and returns without setting any addon fields.
     * <strong>Callers in ExecutionManager that skip null-addonTypeId nodes must
     * continue to handle that case themselves</strong> — this method does not drop
     * the node from any collection; it only omits the addon fields from the record,
     * matching the behaviour described for the save path in the plan threat model.
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
                // Fall back to retained blob if any
                nodeData.setExtraFields(node.getAddonExtraFields());
            }
        } else {
            // Addon absent — placeholder re-save: write back retained blob verbatim (D-09)
            nodeData.setExtraFields(node.getAddonExtraFields());
        }
    }

    /**
     * Restores the ADDON identity fields from a {@link NodeGraphData.NodeData} record
     * onto a freshly-constructed {@link Node} (the load direction).
     *
     * <p>Replicates the ADDON load logic from
     * {@code NodeGraphPersistence.convertToNodes} lines 339–368, including the
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
                    // Store restored state in the node's retained blob for later re-serialization
                    if (nodeData.getExtraFields() != null) {
                        node.setAddonExtraFields(new HashMap<>(nodeData.getExtraFields()));
                    }
                    if (ctx.getScriptText() != null && node.getAddonExtraFields() != null) {
                        node.getAddonExtraFields().put("script", ctx.getScriptText());
                    }
                } catch (Throwable t) {
                    System.err.println("[Pathmind] Addon deserializer threw for " + addonTypeId + ": " + t.getMessage());
                    node.setAddonExtraFields(nodeData.getExtraFields());
                    node.setAddonUnresolved(true);
                }
            }
        } else {
            // Addon absent — retain extraFields verbatim as placeholder (D-09)
            node.setAddonExtraFields(nodeData.getExtraFields());
            node.setAddonUnresolved(true);
        }
    }
}
