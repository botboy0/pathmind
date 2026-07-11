package com.pathmind.api.addon;

import com.pathmind.data.SettingsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read/write access to addon-registered settings, backed by Pathmind's
 * {@code settings.json} ({@code addonSettings} map, keyed addon id → setting key →
 * string value). Values are validated against the registered {@link AddonSetting}
 * declarations: unknown keys fall back to nothing, out-of-range integers clamp,
 * unparsable values fall back to the declared default.
 *
 * <p>Addons read their settings through the typed getters (cheap enough for
 * per-frame use, e.g. inside an {@link AddonNodeDefinition.Builder#bodyHeight(java.util.function.IntSupplier)}
 * supplier). Pathmind's settings UI uses {@link #registrations()} to render rows and
 * the setters to persist changes.
 *
 * <p>Part of the Pathmind addon API — settings extension.
 */
public final class AddonSettings {

    /** Registration order preserved per addon for stable UI listing. */
    private static final Map<String, Map<String, AddonSetting>> REGISTRY = new LinkedHashMap<>();

    /** Bumped on every write; consumers with settings-derived caches re-evaluate on change. */
    private static volatile int version = 0;

    private AddonSettings() {
    }

    /**
     * Monotonic change counter, incremented by every {@link #setInt}/{@link #setBoolean}.
     * Cheap to poll from hot paths (e.g. node layout) to notice settings changes without
     * an observer wiring — supplier-based node sizes resize live this way.
     */
    public static int version() {
        return version;
    }

    /** Registers a setting for an addon; called by Pathmind's addon loader. */
    public static synchronized void register(String addonId, AddonSetting setting) {
        if (addonId == null || addonId.isBlank() || setting == null) {
            return;
        }
        REGISTRY.computeIfAbsent(addonId, k -> new LinkedHashMap<>()).put(setting.key(), setting);
    }

    /** Returns every registered (addonId, setting) pair in registration order. */
    public static synchronized List<Registration> registrations() {
        List<Registration> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, AddonSetting>> addon : REGISTRY.entrySet()) {
            for (AddonSetting setting : addon.getValue().values()) {
                result.add(new Registration(addon.getKey(), setting));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Whether any addon registered settings (drives the settings-popup section). */
    public static synchronized boolean anyRegistered() {
        return REGISTRY.values().stream().anyMatch(m -> !m.isEmpty());
    }

    /** Returns the current value of an INT setting (declared default when unset). */
    public static int getInt(String addonId, String key) {
        AddonSetting setting = find(addonId, key, AddonSetting.Type.INT);
        return readInt(setting, rawValue(SettingsManager.getCurrent(), addonId, key));
    }

    /** Returns the current value of a BOOLEAN setting (declared default when unset). */
    public static boolean getBoolean(String addonId, String key) {
        AddonSetting setting = find(addonId, key, AddonSetting.Type.BOOLEAN);
        String raw = rawValue(SettingsManager.getCurrent(), addonId, key);
        return raw == null ? setting.defaultBool() : Boolean.parseBoolean(raw);
    }

    /** Persists a new INT value (clamped into the declared bounds). */
    public static void setInt(String addonId, String key, int value) {
        AddonSetting setting = find(addonId, key, AddonSetting.Type.INT);
        write(addonId, key, Integer.toString(setting.clamp(value)));
    }

    /** Persists a new BOOLEAN value. */
    public static void setBoolean(String addonId, String key, boolean value) {
        find(addonId, key, AddonSetting.Type.BOOLEAN);
        write(addonId, key, Boolean.toString(value));
    }

    // ---- pure helpers (unit-testable without Minecraft) ---------------------

    /** Resolves an INT setting's effective value from a raw stored string. */
    static int readInt(AddonSetting setting, String raw) {
        if (raw == null) {
            return setting.defaultInt();
        }
        try {
            return setting.clamp(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return setting.defaultInt();
        }
    }

    static String rawValue(SettingsManager.Settings settings, String addonId, String key) {
        if (settings == null || settings.addonSettings == null) {
            return null;
        }
        Map<String, String> perAddon = settings.addonSettings.get(addonId);
        return perAddon == null ? null : perAddon.get(key);
    }

    private static synchronized AddonSetting find(String addonId, String key, AddonSetting.Type expected) {
        Map<String, AddonSetting> perAddon = REGISTRY.get(addonId);
        AddonSetting setting = perAddon == null ? null : perAddon.get(key);
        if (setting == null) {
            throw new IllegalArgumentException(
                "No registered addon setting '" + addonId + ":" + key + "'");
        }
        if (setting.type() != expected) {
            throw new IllegalArgumentException(
                "Addon setting '" + addonId + ":" + key + "' is " + setting.type() + ", not " + expected);
        }
        return setting;
    }

    private static void write(String addonId, String key, String value) {
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        if (settings.addonSettings == null) {
            settings.addonSettings = new LinkedHashMap<>();
        }
        settings.addonSettings.computeIfAbsent(addonId, k -> new LinkedHashMap<>()).put(key, value);
        SettingsManager.save(settings);
        version++;
    }

    /** One registered setting together with its owning addon id. */
    public record Registration(String addonId, AddonSetting setting) {
    }
}
