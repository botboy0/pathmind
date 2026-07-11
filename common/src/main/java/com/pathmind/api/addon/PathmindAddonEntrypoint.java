package com.pathmind.api.addon;

/**
 * Implement this interface and declare it under the {@code "pathmind"} entrypoint key
 * in {@code fabric.mod.json} to register custom node types with Pathmind.
 *
 * <p>Example {@code fabric.mod.json} entry:
 * <pre>{@code
 * "entrypoints": {
 *   "pathmind": ["com.example.myaddon.MyAddonEntrypoint"]
 * }
 * }</pre>
 *
 * <p>Throwing from {@link #registerNodes} disables the entire addon. Pathmind
 * continues initializing normally with remaining addons.
 *
 * <p>Part of the Pathmind addon API — D-02 registrar pattern, D-03 upstream code style.
 */
@FunctionalInterface
public interface PathmindAddonEntrypoint {

    /**
     * Called by Pathmind during mod initialization. Register all custom node types
     * through the provided registrar. Throwing from this method disables the entire addon.
     *
     * @param registrar the mutable collector; sealed after all entrypoints have run
     */
    void registerNodes(NodeTypeRegistrar registrar);

    /**
     * Optional hook called right after {@link #registerNodes} for the same addon.
     * Register user-configurable settings here; they appear in the editor settings
     * popup under "Addon Settings" and are read back via {@link AddonSettings}.
     * The registrar is pre-bound to this addon's mod id. Default no-op.
     *
     * @param registrar collector for this addon's settings declarations
     */
    default void registerSettings(AddonSettingsRegistrar registrar) {
    }
}
