package com.pathmind.api.addon;

import com.pathmind.data.SettingsManager;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Addon settings extension: declaration validation, value resolution, dynamic node sizing. */
class AddonSettingsTest {

    @Test
    void intSettingClampsDefaultAndValuesIntoBounds() {
        AddonSetting s = AddonSetting.intSetting("width", "Width", "", 900, 160, 600);
        assertEquals(600, s.defaultInt(), "out-of-range default clamps into bounds");
        assertEquals(160, s.clamp(1));
        assertEquals(600, s.clamp(9999));
        assertEquals(300, s.clamp(300));
    }

    @Test
    void invalidDeclarationsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> AddonSetting.intSetting("bad key!", "Label", "", 1, 0, 2));
        assertThrows(IllegalArgumentException.class,
            () -> AddonSetting.intSetting("key", " ", "", 1, 0, 2));
        assertThrows(IllegalArgumentException.class,
            () -> AddonSetting.intSetting("key", "Label", "", 1, 5, 2), "min > max");
    }

    @Test
    void readIntFallsBackToDefaultOnMissingOrGarbageAndClampsStoredValues() {
        AddonSetting s = AddonSetting.intSetting("height", "Height", "", 200, 128, 500);
        assertEquals(200, AddonSettings.readInt(s, null), "unset -> default");
        assertEquals(200, AddonSettings.readInt(s, "not-a-number"), "garbage -> default");
        assertEquals(500, AddonSettings.readInt(s, "12345"), "stored out-of-range -> clamp");
        assertEquals(300, AddonSettings.readInt(s, " 300 "), "whitespace tolerated");
    }

    @Test
    void rawValueReadsTheNestedAddonSettingsMap() {
        SettingsManager.Settings settings = new SettingsManager.Settings();
        settings.addonSettings = new LinkedHashMap<>();
        settings.addonSettings.put("my_addon", new LinkedHashMap<>(Map.of("width", "320")));

        assertEquals("320", AddonSettings.rawValue(settings, "my_addon", "width"));
        assertEquals(null, AddonSettings.rawValue(settings, "my_addon", "missing"));
        assertEquals(null, AddonSettings.rawValue(settings, "other_addon", "width"));
        assertEquals(null, AddonSettings.rawValue(new SettingsManager.Settings(), "my_addon", "width"));
    }

    @Test
    void registrationsPreserveOrderAndSupportReRegistration() {
        AddonSettings.register("test_addon_order", AddonSetting.intSetting("a", "A", "", 1, 0, 9));
        AddonSettings.register("test_addon_order", AddonSetting.boolSetting("b", "B", "", true));
        AddonSettings.register("test_addon_order", AddonSetting.intSetting("a", "A2", "", 2, 0, 9));

        var mine = AddonSettings.registrations().stream()
            .filter(r -> r.addonId().equals("test_addon_order")).toList();
        assertEquals(2, mine.size(), "re-registration replaces, not duplicates");
        assertEquals("A2", mine.get(0).setting().label(), "last registration wins in place");
        assertEquals("b", mine.get(1).setting().key());
        assertTrue(AddonSettings.anyRegistered());
    }

    @Test
    void supplierBackedBodySizeIsReEvaluatedPerCall() {
        AtomicInteger height = new AtomicInteger(128);
        AddonNodeDefinition def = AddonNodeDefinition.builder("test:sizing")
            .displayName("Sizing")
            .category(new AddonNodeCategory("test", "Test", 0xFFFFFFFF, "*"))
            .bodyHeight(height::get)
            .bodyWidth(320)
            .build();

        assertEquals(128, def.getBodyHeight());
        height.set(400);
        assertEquals(400, def.getBodyHeight(), "supplier declarations resize live");
        assertEquals(320, def.getBodyWidth());

        AddonNodeDefinition defaults = AddonNodeDefinition.builder("test:defaults")
            .displayName("Defaults")
            .category(new AddonNodeCategory("test", "Test", 0xFFFFFFFF, "*"))
            .build();
        assertEquals(-1, defaults.getBodyHeight());
        assertEquals(-1, defaults.getBodyWidth());
    }
}
