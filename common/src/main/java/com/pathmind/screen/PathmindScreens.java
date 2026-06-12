package com.pathmind.screen;

import com.pathmind.PathmindCommon;
import com.pathmind.execution.AddonLoader;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.util.BaritoneDependencyChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * Centralized helpers for opening Pathmind screens without crashing when dependencies are missing.
 */
public final class PathmindScreens {
    private static final String VISUAL_EDITOR_CLASS = "com.pathmind.screen.PathmindVisualEditorScreen";

    private PathmindScreens() {
    }

    /**
     * Opens the visual editor if Baritone is present, otherwise shows a warning screen.
     *
     * @param client Minecraft client instance
     * @param parent Screen to return to when closing the warning
     */
    public static void openVisualEditorOrWarn(MinecraftClient client, Screen parent) {
        if (client == null) {
            return;
        }

        if (isVisualEditor(client.currentScreen)) {
            return;
        }

        try {
            client.setScreen(instantiateVisualEditor());
            // D-08: surface any addon-load failures that occurred during mod initialization
            surfaceAddonLoadFailures();
        } catch (ReflectiveOperationException | LinkageError e) {
            PathmindCommon.LOGGER.error("Failed to open Pathmind visual editor", e);
        }
    }

    /**
     * Surfaces addon-load failures recorded by {@link AddonLoader} as HUD notifications
     * when the editor opens (D-08 in-game warning).
     * Each failure is shown once per editor open.
     */
    private static void surfaceAddonLoadFailures() {
        Map<String, Throwable> failedAddons = AddonLoader.getFailedAddons();
        for (Map.Entry<String, Throwable> entry : failedAddons.entrySet()) {
            String addonId = entry.getKey();
            Throwable cause = entry.getValue();
            String message = "[Pathmind] Addon '" + addonId + "' failed to load: "
                + (cause != null ? cause.getMessage() : "unknown error");
            NodeErrorNotificationOverlay.getInstance().show(message, 0xFFFF5722);
        }
    }

    public static void showMissingScreen(MinecraftClient client) {
        showMissingScreen(client, client != null ? client.currentScreen : null);
    }

    public static boolean isVisualEditorScreen(Screen screen) {
        return isVisualEditor(screen);
    }

    private static void showMissingScreen(MinecraftClient client, Screen parent) {
        if (client == null) {
            return;
        }

        if (!(client.currentScreen instanceof MissingBaritoneApiScreen)) {
            client.setScreen(new MissingBaritoneApiScreen(parent));
        }
    }

    private static boolean isVisualEditor(Screen currentScreen) {
        return currentScreen != null && VISUAL_EDITOR_CLASS.equals(currentScreen.getClass().getName());
    }

    private static Screen instantiateVisualEditor() throws ReflectiveOperationException {
        Class<?> clazz = Class.forName(VISUAL_EDITOR_CLASS);
        if (!Screen.class.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Pathmind visual editor screen does not extend Screen");
        }

        Constructor<? extends Screen> ctor = clazz.asSubclass(Screen.class).getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }
}
