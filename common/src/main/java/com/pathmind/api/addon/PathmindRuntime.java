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
     * Reads a node-tree variable (or runtime list) by name.
     *
     * <p>Supported return types:
     * <ul>
     *   <li>{@link Double} — numeric and distance variables</li>
     *   <li>{@link Boolean} — boolean variables</li>
     *   <li>{@link String} — text variables</li>
     *   <li>{@code Map<String, Object>} — coordinate variables, with keys
     *       {@code "x"}, {@code "y"}, {@code "z"} and Double values</li>
     *   <li>{@code List<Object>} — when the name refers to a runtime list (the
     *       namespace used by the Create List / Add to List / List Item nodes).
     *       Elements are Doubles, Booleans, Strings, coordinate maps as above,
     *       plain Strings for opaque entries (entity UUIDs, item ids, GUI slot
     *       tokens), or {@code Map<String, String>} for other structured entries.</li>
     * </ul>
     * Variables shadow lists: the variable namespace is checked first. Returns
     * {@code null} if neither exists or the value cannot be marshaled.
     *
     * @param name the variable or list name
     * @return the marshaled value, or null if not found
     */
    Object getVariable(String name);

    /**
     * Writes a node-tree variable (or runtime list).
     *
     * <p>Supported value types:
     * <ul>
     *   <li>{@link Double}, {@link Boolean}, {@link String} — scalar variables</li>
     *   <li>{@code Map} with exactly the keys {@code x}, {@code y}, {@code z}
     *       (case-insensitive) and {@link Number} values — stored as a coordinate
     *       variable</li>
     *   <li>{@code List} — stored as a runtime list consumable by the node-side
     *       list operations. Elements must be uniformly Numbers, Strings, Booleans,
     *       or coordinate maps; mixed or empty lists are rejected.</li>
     * </ul>
     *
     * @param name  the variable or list name
     * @param value the value to store
     * @throws IllegalArgumentException if the value (or a list element / map shape)
     *         is unsupported or null
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
     * <p>Returns a future that resolves to the action's {@link ActionResult} envelope
     * when the action finishes (some actions, e.g. navigation, may take seconds).
     * Expected action failures — the world saying no (missing ingredients, no matching
     * hotbar item, target not found) — resolve to {@code ok == false} with a symbolic
     * status; see {@link ActionResult}. The future completes exceptionally only on
     * caller errors (unknown action, disallowed action, bad argument) and mod-internal
     * errors (incl. the client being unavailable). The caller may safely block on the
     * future from a worker thread.
     *
     * @param actionName case-insensitive node-type name of the action
     * @param args       parameter values by name; may be null or empty
     * @return a future resolving to the action's result envelope
     */
    CompletableFuture<ActionResult> invokeAction(String actionName, Map<String, Object> args);

    /**
     * Returns the catalog of actions invocable via {@link #invokeAction} — one
     * {@link ActionInfo} per action, with the localized display name, description,
     * and the parameter names/types/defaults the action accepts.
     *
     * <p>The catalog is derived from Pathmind's node definitions (never
     * hand-maintained) and mirrors {@code invokeAction}'s accept set exactly:
     * every listed action is invocable and every invocable action is listed.
     * Addons use it to generate per-action bindings, editor completion entries,
     * and external editor stubs (e.g. LuaCATS definitions).
     *
     * <p>Safe to call from any thread; the result is cached after the first call.
     *
     * <p>Default implementation returns an empty list — keeps older runtime
     * implementations (and test fakes) source-compatible; Pathmind's real
     * runtime always overrides it.
     *
     * @return immutable list of invocable actions, sorted by name; never null
     */
    default java.util.List<ActionInfo> listActions() {
        return java.util.List.of();
    }

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
