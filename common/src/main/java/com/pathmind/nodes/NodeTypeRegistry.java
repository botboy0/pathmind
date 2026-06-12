package com.pathmind.nodes;

import com.pathmind.api.addon.AddonNodeDefinition;
import com.pathmind.api.addon.AddonNodeExecutor;
import com.pathmind.api.addon.AddonNodeSerializer;
import com.pathmind.api.addon.NodeTypeRegistrar;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Singleton registry for addon-provided node types.
 *
 * <p>Populated once by {@link com.pathmind.execution.AddonLoader} during mod initialization.
 * After installation the registry is immutable — the registrar is sealed before
 * {@link #install} is called, guaranteeing no late registrations (API-04, T-01-05).
 *
 * <p>All lookup methods return {@code null} when the requested id is not registered.
 * Callers should check {@link #hasType} before calling definitionFor/executorFor/serializerFor.
 */
public final class NodeTypeRegistry {

    /**
     * The singleton registry instance. Populated by AddonLoader during mod init.
     */
    public static final NodeTypeRegistry INSTANCE = new NodeTypeRegistry();

    private final Map<String, AddonNodeDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AddonNodeExecutor> executors = new LinkedHashMap<>();
    private final Map<String, AddonNodeSerializer> serializers = new LinkedHashMap<>();
    private boolean installed = false;

    /**
     * Package-private constructor — use {@link #INSTANCE} for the singleton,
     * or create fresh instances in unit tests.
     */
    NodeTypeRegistry() {
    }

    /**
     * Installs registrations from the given registrar. May only be called once.
     *
     * <p>Called by {@code AddonLoader.discoverAndLoad()} after all entrypoints have
     * run and the registrar has been sealed.
     *
     * @param registrar the sealed registrar containing all addon registrations
     * @throws IllegalStateException if the registry has already been installed
     */
    public void install(NodeTypeRegistrar registrar) {
        if (installed) {
            throw new IllegalStateException("NodeTypeRegistry already installed");
        }
        // Seal the registrar before reading it — guarantees no concurrent registration
        // can sneak in between the install call and the putAll calls (ASVS V4, T-01-05).
        registrar.seal();
        definitions.putAll(registrar.getDefinitions());
        executors.putAll(registrar.getExecutors());
        serializers.putAll(registrar.getSerializers());
        installed = true;
    }

    /**
     * Returns {@code true} if a node type with the given id is registered.
     *
     * @param addonTypeId the namespaced type id (e.g. {@code "pathmind_lua:script"})
     * @return true if registered
     */
    public boolean hasType(String addonTypeId) {
        return definitions.containsKey(addonTypeId);
    }

    /**
     * Returns the definition for the given addon type id, or {@code null} if not registered.
     *
     * @param id the namespaced type id
     * @return the definition, or null
     */
    public AddonNodeDefinition definitionFor(String id) {
        return definitions.get(id);
    }

    /**
     * Returns the executor for the given addon type id, or {@code null} if not registered.
     *
     * @param id the namespaced type id
     * @return the executor, or null
     */
    public AddonNodeExecutor executorFor(String id) {
        return executors.get(id);
    }

    /**
     * Returns the serializer for the given addon type id, or {@code null} if not registered.
     *
     * @param id the namespaced type id
     * @return the serializer, or null
     */
    public AddonNodeSerializer serializerFor(String id) {
        return serializers.get(id);
    }

    /**
     * Returns an unmodifiable view of all registered addon node definitions.
     *
     * @return unmodifiable collection of all definitions
     */
    public Collection<AddonNodeDefinition> allDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }
}
