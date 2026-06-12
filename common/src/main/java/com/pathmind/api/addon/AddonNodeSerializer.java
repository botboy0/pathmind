package com.pathmind.api.addon;

import java.util.Map;

/**
 * Serializes and deserializes addon-specific node state into Pathmind's JSON presets.
 *
 * <p>The map returned by {@link #serialize} is stored as an opaque blob under
 * {@code NodeData.extraFields}. GSON handles the nested JSON automatically.
 *
 * <p><strong>Schema versioning:</strong> The serialize map MUST include a
 * {@code "_schema_version"} integer key. In the first version of an addon this
 * should be {@code 1}. Increment it when the data format changes to enable
 * migration in future versions.
 *
 * <p><strong>GSON type erasure warning:</strong> When reading numeric values from
 * {@code fields} in {@link #deserialize}, always use
 * {@code ((Number) fields.get("key")).intValue()} rather than a direct cast to
 * {@code Integer}. GSON deserializes JSON numbers as {@code Double} when the
 * target type is {@code Object} (Pitfall 4 from RESEARCH.md).
 *
 * <p>Part of the Pathmind addon API — API-05 persistence contract.
 */
public interface AddonNodeSerializer {

    /**
     * Serializes this node's state into a map to be stored in the preset JSON.
     *
     * <p>The returned map MUST include a {@code "_schema_version"} key with an
     * integer value.
     *
     * @param ctx the current runtime context of the node
     * @return a map to be stored as the opaque addon blob in {@code NodeData.extraFields}
     */
    Map<String, Object> serialize(AddonNodeContext ctx);

    /**
     * Restores node state from a previously serialized map.
     *
     * <p>Numeric values in {@code fields} will be {@code Double} due to GSON type
     * erasure — use {@code ((Number) fields.get("key")).intValue()} to read integers.
     *
     * @param ctx    the node context to populate
     * @param fields the map as deserialized by GSON; may be null if no data was stored
     */
    void deserialize(AddonNodeContext ctx, Map<String, Object> fields);
}
