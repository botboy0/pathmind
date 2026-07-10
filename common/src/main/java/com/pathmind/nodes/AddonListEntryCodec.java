package com.pathmind.nodes;

import java.util.Collections;
import java.util.Map;

/**
 * Serialization helper for {@link com.pathmind.execution.ExecutionManager.RuntimeList}
 * entries, shared between the node-side list executors and the addon runtime bridge.
 *
 * <p>Runtime-list entries are plain strings. Structured entries (coordinates, scalar
 * values) are stored as {@code "pm_list:" + <json map>} — the same format
 * {@code NodeVariableListCommandExecutor} writes when Create List / Add to List build
 * entries from parameter nodes. Raw entries (entity UUIDs, item ids, GUI slot tokens)
 * carry no prefix.
 *
 * <p>Exists so {@code PathmindRuntimeImpl} (in the {@code execution} package) can
 * marshal Lua tables ↔ runtime lists without duplicating the wire format, which is
 * defined by package-private constants on {@link Node}.
 */
public final class AddonListEntryCodec {

    private AddonListEntryCodec() {
    }

    /** Returns whether the entry is a serialized values-map (vs. a raw string entry). */
    public static boolean isSerialized(String entry) {
        return entry != null && entry.startsWith(Node.LIST_ENTRY_SERIALIZED_PREFIX);
    }

    /** Serializes a parameter values-map into the prefixed list-entry string form. */
    public static String serialize(Map<String, String> values) {
        return Node.LIST_ENTRY_SERIALIZED_PREFIX
            + Node.LIST_ENTRY_GSON.toJson(values == null ? Collections.emptyMap() : values);
    }

    /**
     * Deserializes a prefixed list-entry string back into its values-map.
     * Returns an empty map for non-serialized or malformed entries.
     */
    public static Map<String, String> deserialize(String entry) {
        if (!isSerialized(entry)) {
            return Collections.emptyMap();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> parsed = Node.LIST_ENTRY_GSON.fromJson(
                entry.substring(Node.LIST_ENTRY_SERIALIZED_PREFIX.length()), Map.class);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
