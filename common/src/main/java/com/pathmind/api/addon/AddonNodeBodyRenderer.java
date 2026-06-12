package com.pathmind.api.addon;

import net.minecraft.client.gui.DrawContext;

/**
 * Renders custom content inside an addon node's body area (API-07).
 *
 * <p>Called per-frame from the NodeGraph rendering loop inside
 * {@code NodeGraph.renderNode()}. Implementations must be fast and stateless —
 * no heavy computation or I/O should occur inside {@link #render}.
 *
 * <p>This is the only API type that imports {@code net.minecraft.client.gui.DrawContext}
 * because rendering inherently requires Minecraft's draw API.
 *
 * <p>Part of the Pathmind addon API — API-07 body render hook.
 */
@FunctionalInterface
public interface AddonNodeBodyRenderer {

    /**
     * Renders custom content inside the node body area.
     *
     * @param ctx    the addon node's runtime context (read-only during rendering)
     * @param draw   Minecraft DrawContext for immediate-mode rendering
     * @param x      left edge of the node body content area in screen coordinates
     * @param y      top edge of the node body content area in screen coordinates
     * @param width  available width in pixels
     * @param height available height in pixels
     */
    void render(AddonNodeContext ctx, DrawContext draw, int x, int y, int width, int height);
}
