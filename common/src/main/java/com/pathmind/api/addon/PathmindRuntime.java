package com.pathmind.api.addon;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Runtime services provided to addon node executors by Pathmind.
 *
 * <p>Obtained via {@link AddonNodeContext#getRuntime()}. Implementations are
 * provided by Pathmind — addons must not implement this interface.
 *
 * <p>All methods are safe to call from any thread. Methods that must run on
 * the Minecraft client thread dispatch internally and return results via
 * {@link CompletableFuture} or block the caller safely.
 *
 * <p>Part of the Pathmind addon API — Phase 2 runtime bridge (API-09: version-agnostic).
 */
public interface PathmindRuntime {

    /**
     * Reads a node-tree variable by name.
     *
     * <p>Supported return types: {@link Double}, {@link Boolean}, {@link String}.
     * Returns {@code null} if the variable does not exist or its type cannot be
     * marshaled to a supported type.
     *
     * @param name the variable name
     * @return the variable value, or null if not found
     */
    Object getVariable(String name);

    /**
     * Writes a node-tree variable.
     *
     * <p>Supported value types: {@link Double}, {@link Boolean}, {@link String}.
     * Throws {@link IllegalArgumentException} if the value is an unsupported type
     * (e.g. Lua tables or userdata — not marshaled in v1).
     *
     * @param name  the variable name
     * @param value the value to store; must be Double, Boolean, or String
     * @throws IllegalArgumentException if value type is unsupported or null
     */
    void setVariable(String name, Object value);

    /**
     * Starts navigation to the given world coordinates.
     *
     * <p>Returns a future that completes normally when the player arrives, or
     * completes exceptionally if navigation is aborted or fails. The Lua worker
     * thread may safely block on this future via {@code .get()} — the game tick
     * thread is not blocked.
     *
     * @param x target X coordinate
     * @param y target Y coordinate
     * @param z target Z coordinate
     * @return a future that resolves when navigation is complete
     */
    CompletableFuture<Void> moveTo(double x, double y, double z);

    /**
     * Returns the player's current position as {@code [x, y, z]}.
     *
     * <p>Dispatched to the Minecraft client thread internally. Returns
     * {@code [0, 0, 0]} if the client or player is unavailable.
     *
     * @return double array of length 3: [x, y, z]
     */
    double[] getPosition();

    /**
     * Returns non-empty inventory slots as an array of records.
     *
     * <p>Each element is a {@code Map<String, Object>} with keys:
     * {@code slot} (Integer), {@code item} (String namespaced id), {@code count} (Integer).
     *
     * <p>Dispatched to the Minecraft client thread internally. Returns an empty
     * array if the client or player is unavailable.
     *
     * @return array of slot records; never null
     */
    Object[] getInventory();

    /**
     * Returns the namespaced block id at the given world coordinates.
     *
     * <p>Returns {@code null} if the chunk at those coordinates is not loaded.
     * Dispatched to the Minecraft client thread internally.
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     * @param z world Z coordinate
     * @return namespaced block id (e.g. {@code "minecraft:stone"}), or null if unloaded
     */
    String getBlock(double x, double y, double z);

    /**
     * Invokes a single Pathmind action node by name — the generic action dispatch
     * that opens the full world/player action surface to addon scripts (v2).
     *
     * <p><strong>Action names</strong> are Pathmind node-type names, case-insensitive:
     * e.g. {@code "jump"}, {@code "look"}, {@code "message"}, {@code "equip_hand"},
     * {@code "drop_item"}. Only concrete world/player/interface actions are invocable;
     * flow control, sensors, data operations, and parameter nodes are rejected
     * (scripts have Lua control flow and the variable API instead).
     *
     * <p><strong>Arguments</strong> map parameter names (as shown on the node in the
     * editor, case-insensitive) to values of type {@link Double}, {@link Boolean}, or
     * {@link String}. Special case: for {@code "message"}, the key {@code "text"} sets
     * the message text. Unknown argument names fail the future — typos surface to the
     * script instead of being silently ignored.
     *
     * <p>Returns a future that completes when the action finishes (some actions, e.g.
     * navigation, may take seconds), or completes exceptionally on an unknown action,
     * a disallowed action, a bad argument, or when the client is unavailable. The
     * caller may safely block on the future from a worker thread.
     *
     * @param actionName case-insensitive node-type name of the action
     * @param args       parameter values by name; may be null or empty
     * @return a future that resolves when the action completes
     */
    CompletableFuture<Void> invokeAction(String actionName, Map<String, Object> args);

    /**
     * Sends an error message to the player chat with the Pathmind prefix.
     *
     * <p>Safe to call from any thread — dispatches to the Minecraft client
     * thread internally. Returns quietly if the Minecraft client is not available
     * (e.g. in a headless test context).
     *
     * @param message the error message to display; null is handled gracefully
     */
    void sendErrorToChat(String message);
}
