package com.pathmind.api.addon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable collector for addon node type registrations. An instance is passed to each
 * {@link PathmindAddonEntrypoint#registerNodes} call during Pathmind initialization.
 * The registrar is sealed after all entrypoints have run — no further registrations
 * are accepted after that point (ASVS V4, T-01-05).
 *
 * <p>Part of the Pathmind addon API — D-02 registrar pattern.
 */
public final class NodeTypeRegistrar {

    private final Map<String, AddonNodeDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AddonNodeExecutor> executors = new LinkedHashMap<>();
    private final Map<String, AddonNodeSerializer> serializers = new LinkedHashMap<>();
    private boolean sealed = false;

    /**
     * Register a custom node type. All three arguments must be non-null.
     *
     * <p>The {@code def.getId()} must match the namespaced format
     * {@code ^[a-z0-9_-]+:[a-z0-9_/.-]+$} (e.g. {@code "pathmind_lua:script"}).
     * This validation blocks path-traversal and id-shadowing attacks (ASVS V5, T-01-01).
     *
     * @param def the node definition (id, display name, category, color, provenance)
     * @param exec the executor invoked when this node runs in a graph
     * @param ser the serializer for saving/loading this node's state in presets
     * @throws IllegalStateException if the registrar is already sealed
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if the id is malformed or a duplicate
     */
    public void register(AddonNodeDefinition def, AddonNodeExecutor exec, AddonNodeSerializer ser) {
        if (sealed) {
            throw new IllegalStateException(
                "NodeTypeRegistrar is sealed — registration phase has ended");
        }
        if (def == null || exec == null || ser == null) {
            throw new NullPointerException(
                "All three registration arguments must be non-null");
        }
        if (!def.getId().matches("^[a-z0-9_-]+:[a-z0-9_/.-]+$")) {
            throw new IllegalArgumentException(
                "Addon node type ID must match 'modid:name' format (lowercase, no path traversal): "
                + def.getId());
        }
        if (definitions.containsKey(def.getId())) {
            throw new IllegalArgumentException(
                "Duplicate addon node type ID: " + def.getId());
        }
        definitions.put(def.getId(), def);
        executors.put(def.getId(), exec);
        serializers.put(def.getId(), ser);
    }

    /**
     * Seals this registrar so no further registrations are accepted.
     * Package-private — only {@code AddonLoader} calls this.
     */
    void seal() {
        this.sealed = true;
    }

    /**
     * Returns an unmodifiable view of registered definitions, keyed by addon type id.
     * Addons must not call this method — it is intended for use by {@code NodeTypeRegistry}.
     *
     * @return unmodifiable map of definitions
     */
    public Map<String, AddonNodeDefinition> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    /**
     * Returns an unmodifiable view of registered executors, keyed by addon type id.
     * Addons must not call this method — it is intended for use by {@code NodeTypeRegistry}.
     *
     * @return unmodifiable map of executors
     */
    public Map<String, AddonNodeExecutor> getExecutors() {
        return Collections.unmodifiableMap(executors);
    }

    /**
     * Returns an unmodifiable view of registered serializers, keyed by addon type id.
     * Addons must not call this method — it is intended for use by {@code NodeTypeRegistry}.
     *
     * @return unmodifiable map of serializers
     */
    public Map<String, AddonNodeSerializer> getSerializers() {
        return Collections.unmodifiableMap(serializers);
    }
}
