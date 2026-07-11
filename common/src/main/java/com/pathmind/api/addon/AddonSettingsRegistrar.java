package com.pathmind.api.addon;

/**
 * Collector handed to {@link PathmindAddonEntrypoint#registerSettings} during Pathmind
 * initialization. Each registrar instance is already bound to the registering addon's
 * mod id, so addons only supply the settings themselves.
 *
 * <p>Registered settings appear in the editor settings popup and are read back through
 * {@link AddonSettings}.
 *
 * <p>Part of the Pathmind addon API — settings extension.
 */
@FunctionalInterface
public interface AddonSettingsRegistrar {

    /**
     * Registers one setting for the calling addon. Re-registering an existing key
     * replaces the earlier declaration (last registration wins).
     *
     * @param setting the setting declaration
     */
    void register(AddonSetting setting);
}
