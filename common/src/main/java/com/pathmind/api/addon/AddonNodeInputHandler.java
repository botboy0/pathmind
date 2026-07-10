package com.pathmind.api.addon;

import net.minecraft.client.gui.DrawContext;

/**
 * Optional input-routing hook for addon nodes with interactive bodies (Phase 3 API extension).
 *
 * <p>Pathmind calls these methods when a focused addon node needs to process screen-level input.
 * Return {@code true} to consume the event (prevent NodeGraph from processing it);
 * return {@code false} to pass through.
 *
 * <p>Focus lifecycle: NodeGraph tracks which addon node (if any) holds focus via an internal
 * {@code focusedAddonNode} field. Clicking the node body focuses it; pressing Esc or clicking
 * away blurs it, firing {@link #onFocusLost}.
 *
 * <p>Registered via {@link AddonNodeDefinition.Builder#inputHandler(AddonNodeInputHandler)}.
 *
 * <p>Part of the Pathmind addon API — Phase 3 API extension.
 */
public interface AddonNodeInputHandler {

    /**
     * Called when the user clicks inside the addon body area.
     * Return {@code true} if the click was handled (consumed by the addon).
     *
     * @param ctx    the addon node's runtime context
     * @param mouseX mouse X position in screen coordinates
     * @param mouseY mouse Y position in screen coordinates
     * @param button mouse button index (0 = left, 1 = right, 2 = middle)
     * @return {@code true} to consume the click; {@code false} to pass through
     */
    boolean mouseClicked(AddonNodeContext ctx, double mouseX, double mouseY, int button);

    /**
     * Called for keyboard key presses while this node holds focus.
     *
     * <p>Returning {@code true} is critical for all keypresses while focused — including
     * Backspace, Delete, arrow keys, and Esc. Failure to return {@code true} allows the
     * event to leak to NodeGraph where it may delete the selected node or pan the graph.
     *
     * @param ctx       the addon node's runtime context
     * @param keyCode   GLFW key code
     * @param scanCode  platform-specific key scan code
     * @param modifiers bitmask of active modifier keys (GLFW modifier constants)
     * @return {@code true} to consume the key press; {@code false} to pass through
     */
    boolean keyPressed(AddonNodeContext ctx, int keyCode, int scanCode, int modifiers);

    /**
     * Called for printable character input while this node holds focus.
     *
     * <p>Return {@code true} for all characters while focused to prevent the character from
     * leaking to NodeGraph's shortcut handlers.
     *
     * @param ctx       the addon node's runtime context
     * @param chr       the character typed
     * @param modifiers bitmask of active modifier keys (GLFW modifier constants)
     * @return {@code true} to consume the character; {@code false} to pass through
     */
    boolean charTyped(AddonNodeContext ctx, char chr, int modifiers);

    /**
     * Called for scroll events while this node holds focus.
     *
     * @param ctx              the addon node's runtime context
     * @param mouseX           mouse X position in screen coordinates
     * @param mouseY           mouse Y position in screen coordinates
     * @param horizontalAmount horizontal scroll delta
     * @param verticalAmount   vertical scroll delta
     * @return {@code true} to consume the scroll event; {@code false} to pass through
     */
    boolean mouseScrolled(AddonNodeContext ctx, double mouseX, double mouseY,
                          double horizontalAmount, double verticalAmount);

    /**
     * Called when this node gains keyboard focus (e.g. user clicked inside the editor area).
     *
     * @param ctx the addon node's runtime context
     */
    void onFocusGained(AddonNodeContext ctx);

    /**
     * Called when this node loses keyboard focus (Esc, click away, or a graph event).
     *
     * @param ctx the addon node's runtime context
     */
    void onFocusLost(AddonNodeContext ctx);

    /**
     * Called when this node has been removed from the graph — either a discrete deletion
     * (Delete key, context menu, cut, cascade delete, drag-to-sidebar) or a wholesale graph
     * replacement (preset load/import, undo/redo restore, workspace clear).
     *
     * <p>Addons that key per-node state on {@link AddonNodeContext#getNodeId()} (editor
     * widgets, caches) should evict that state here — without this hook such entries leak
     * for the session lifetime.
     *
     * <p>Only the stable node id is passed (not a full {@link AddonNodeContext}): the node
     * no longer exists in the graph, so there is no live script/error state to expose.
     * Nodes that never rendered have no persisted id and produce no callback.
     *
     * <p>Default no-op — addons without per-node state need not implement this
     * (v2 API extension).
     *
     * @param nodeId the removed node's stable identity, as previously seen via
     *               {@link AddonNodeContext#getNodeId()}
     */
    default void onNodeRemoved(String nodeId) {
    }

    /**
     * Called by NodeGraph after the scissor clip has been disabled, allowing the addon to
     * render popups or overlays that must escape the node body clip region.
     *
     * <p>Default no-op — existing renderers that do not need an unclipped overlay pass
     * do not need to implement this method (Phase 3 API extension, open-question #3).
     *
     * @param ctx    the addon node's runtime context
     * @param draw   Minecraft DrawContext for immediate-mode rendering
     * @param x      left edge of the node body content area in screen coordinates
     * @param y      top edge of the node body content area in screen coordinates
     * @param width  available width in pixels
     * @param height available height in pixels
     */
    default void renderOverlay(AddonNodeContext ctx, DrawContext draw,
                               int x, int y, int width, int height) {
    }
}
