package com.pathmind.nodes;

/**
 * Fabric entrypoint contract for mods that add Pathmind node definitions.
 *
 * <p>Addons declare implementations under the {@code pathmind_nodes} entrypoint.
 */
@FunctionalInterface
public interface PathmindNodePlugin {
    void registerNodes(PathmindNodeRegistrar registry);
}
