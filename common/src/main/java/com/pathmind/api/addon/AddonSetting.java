package com.pathmind.api.addon;

/**
 * Declares one user-configurable setting owned by an addon. Registered through
 * {@link PathmindAddonEntrypoint#registerSettings} and rendered by Pathmind in the
 * editor settings popup under an "Addon Settings" section; values persist in
 * Pathmind's {@code settings.json} next to the built-in settings.
 *
 * <p>Two kinds are supported: bounded integers (rendered as a slider) and booleans
 * (rendered as a toggle). Use the static factories; the canonical constructor is
 * not meant to be called directly.
 *
 * <p>Part of the Pathmind addon API — settings extension.
 *
 * @param key          setting key, unique within the addon (letters, digits, {@code _})
 * @param label        human-readable row label shown in the settings popup
 * @param description  optional longer description (tooltip / documentation); may be empty
 * @param type         the value kind
 * @param defaultInt   default for {@link Type#INT} settings (clamped into min..max)
 * @param minInt       inclusive lower bound for {@link Type#INT} settings
 * @param maxInt       inclusive upper bound for {@link Type#INT} settings
 * @param defaultBool  default for {@link Type#BOOLEAN} settings
 */
public record AddonSetting(String key, String label, String description, Type type,
                           int defaultInt, int minInt, int maxInt, boolean defaultBool) {

    public enum Type { INT, BOOLEAN }

    public AddonSetting {
        if (key == null || !key.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Addon setting key must match [a-zA-Z0-9_]+: " + key);
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Addon setting '" + key + "' needs a label");
        }
        description = description == null ? "" : description;
        if (type == Type.INT) {
            if (minInt > maxInt) {
                throw new IllegalArgumentException("Addon setting '" + key + "': min > max");
            }
            defaultInt = Math.max(minInt, Math.min(maxInt, defaultInt));
        }
    }

    /** Declares a bounded integer setting rendered as a slider row. */
    public static AddonSetting intSetting(String key, String label, String description,
                                          int defaultValue, int min, int max) {
        return new AddonSetting(key, label, description, Type.INT, defaultValue, min, max, false);
    }

    /** Declares a boolean setting rendered as a toggle row. */
    public static AddonSetting boolSetting(String key, String label, String description,
                                           boolean defaultValue) {
        return new AddonSetting(key, label, description, Type.BOOLEAN, 0, 0, 0, defaultValue);
    }

    /** Clamps a raw value into this INT setting's declared bounds. */
    public int clamp(int value) {
        return Math.max(minInt, Math.min(maxInt, value));
    }
}
