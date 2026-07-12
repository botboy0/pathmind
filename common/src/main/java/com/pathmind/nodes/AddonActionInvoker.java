package com.pathmind.nodes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.pathmind.execution.ExecutionManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Executes a single Pathmind action node on behalf of an addon script — the bridge
 * behind {@link com.pathmind.api.addon.PathmindRuntime#invokeAction} (v2: generic
 * action dispatch, opening the action surface to scripts).
 *
 * <p>Builds a synthetic {@link Node} of the requested type (default parameters via the
 * normal constructor), applies the caller's arguments onto the node's parameters, and
 * routes it through {@link NodeCommandDispatcher} on the client thread — the same
 * dispatch path graph execution uses.
 *
 * <p><strong>Action names</strong> are {@link NodeType} constants, case-insensitive
 * (e.g. {@code "jump"}, {@code "message"}, {@code "equip_hand"}).
 *
 * <p><strong>Allowed actions:</strong> only categories {@link NodeCategory#WORLD},
 * {@link NodeCategory#PLAYER}, and {@link NodeCategory#INTERFACE} — concrete
 * player/world actions — plus {@link NodeType#WAIT} as the single FLOW exception:
 * a timed pause is a primitive scripts genuinely need (e.g. giving the client a tick
 * to open a GUI between an interact and a craft), and exposing the native node keeps
 * wait semantics identical to graphs. Other flow control, sensors, data/list
 * operations, parameter nodes, and custom/addon nodes are rejected: they only make
 * sense wired into a graph (scripts have Lua's own control flow and the variable API
 * instead). {@code STICKY_NOTE} and {@code ADDON} are rejected explicitly.
 *
 * <p><strong>Arguments</strong> are matched case-insensitively against the node's
 * parameter names (the same names the node shows in the editor). Values may be
 * {@link Double}, {@link Boolean}, or {@link String}; integral doubles are stringified
 * without the trailing {@code .0} so integer-typed parameters parse cleanly.
 * Special case: for {@code MESSAGE}, the key {@code "text"} sets the message line
 * (the Message node stores its text outside the parameter list).
 * An argument that matches no parameter fails the future — surfacing typos to the
 * script instead of silently ignoring them.
 *
 * <p><strong>Limitation (v1):</strong> the synthetic node dispatches without a chain
 * execution context, so parameter values must be concrete — {@code %variable%}
 * placeholders that resolve via the active chain scope are only supported where the
 * executor itself resolves them (e.g. Message text).
 */
public final class AddonActionInvoker {

    private AddonActionInvoker() {
    }

    /**
     * Invokes the named action with the given arguments.
     *
     * <p><strong>Result envelope:</strong> the future resolves to an
     * {@link com.pathmind.api.addon.ActionResult}. Expected action failures — a node
     * failure recorded while the action runs (class 2: the world says no) — resolve
     * to {@code ok == false} with a symbolic status ({@code "failed"} when the failure
     * site recorded no classification). Caller errors (class 1: unknown action,
     * disallowed category, unknown parameter or mode) and mod-internal errors
     * (class 3, incl. missing client) still complete the future exceptionally.
     *
     * @param actionName case-insensitive {@link NodeType} name (e.g. {@code "jump"})
     * @param args       parameter-name → value map; may be null or empty. Values must be
     *                   Double, Boolean, or String.
     * @return a future resolving to the action's result envelope
     */
    public static CompletableFuture<com.pathmind.api.addon.ActionResult> invoke(String actionName, Map<String, Object> args) {
        CompletableFuture<com.pathmind.api.addon.ActionResult> future = new CompletableFuture<>();

        if (actionName == null || actionName.isBlank()) {
            future.completeExceptionally(new IllegalArgumentException("Action name must not be empty"));
            return future;
        }
        NodeType type;
        try {
            type = NodeType.valueOf(actionName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            future.completeExceptionally(new IllegalArgumentException(
                "Unknown action '" + actionName + "' (action names are Pathmind node types, e.g. 'jump', 'message')"));
            return future;
        }
        if (!isInvocable(type)) {
            future.completeExceptionally(new IllegalArgumentException(
                "Action '" + actionName + "' is not invocable from scripts (category "
                    + type.getCategory() + " — only world/player/interface actions and 'wait' are allowed)"));
            return future;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            future.completeExceptionally(new IllegalStateException("Minecraft client not available"));
            return future;
        }
        // Build + dispatch on the client thread — the same thread graph execution
        // dispatches on (Node.execute wraps NodeCommandDispatcher in client.execute).
        client.execute(() -> {
            try {
                Node node = new Node(type, 0, 0);
                Map<String, Object> remainingArgs = args;
                if (args != null && !args.isEmpty()) {
                    // Optional "Mode" argument selects a non-default node mode
                    // (e.g. goto_xz / goto_y / goto_block on GOTO). Must be applied
                    // before the parameter args: setMode reinitializes the list.
                    String modeError = null;
                    remainingArgs = new java.util.LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : args.entrySet()) {
                        if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Mode")) {
                            modeError = applyMode(node, type, entry.getValue());
                        } else {
                            remainingArgs.put(entry.getKey(), entry.getValue());
                        }
                    }
                    if (modeError != null) {
                        future.completeExceptionally(new IllegalArgumentException(modeError));
                        return;
                    }
                }
                String argError = applyArgs(node, type, remainingArgs);
                if (argError != null) {
                    future.completeExceptionally(new IllegalArgumentException(argError));
                    return;
                }
                Map<String, Object> actionArgs = remainingArgs == null
                    ? Map.of()
                    : new LinkedHashMap<>(remainingArgs);
                Map<String, Integer> inventoryBefore = usesInventorySnapshot(type)
                    ? inventorySnapshot(client)
                    : null;
                ExecutionManager executionManager = ExecutionManager.getInstance();
                long failureCountBefore = executionManager.getNodeFailureCount();
                CompletableFuture<Void> actionFuture = new CompletableFuture<>();
                actionFuture.whenComplete((ignored, throwable) -> {
                    try {
                        client.execute(() -> {
                            try {
                                Map<String, Integer> inventoryAfter = usesInventorySnapshot(type)
                                    ? inventorySnapshot(client)
                                    : null;
                                double[] positionAfter = type == NodeType.GOTO
                                    ? positionSnapshot(client)
                                    : null;
                                completeInvocation(
                                    future,
                                    throwable,
                                    failureCountBefore,
                                    executionManager.getNodeFailureCount(),
                                    executionManager.getLastNodeFailureMessage(),
                                    executionManager.getLastNodeFailureDetail(),
                                    successFields(type, actionArgs, inventoryBefore, inventoryAfter, positionAfter)
                                );
                            } catch (Throwable t) {
                                if (!future.isDone()) {
                                    future.completeExceptionally(t);
                                }
                            }
                        });
                    } catch (Throwable t) {
                        if (!future.isDone()) {
                            future.completeExceptionally(t);
                        }
                    }
                });
                NodeCommandDispatcher.execute(node, actionFuture);
            } catch (Throwable t) {
                if (!future.isDone()) {
                    future.completeExceptionally(t);
                }
            }
        });
        return future;
    }

    /**
     * Maps the outcome of a dispatched action onto the invocation future's result
     * envelope. Package-visible for unit tests — this method IS the three-class
     * contract:
     *
     * <ul>
     *   <li>{@code actionFailure != null} (class 1/3): the future completes
     *       exceptionally with that failure, unchanged.</li>
     *   <li>failure counter bumped (class 2): the future resolves to
     *       {@code ok == false} with the recorded message; status and detail fields
     *       come from {@code failureDetail} when the failure site classified itself,
     *       else status {@code "failed"} with no detail fields.</li>
     *   <li>otherwise: the future resolves to {@code ok == true} carrying
     *       {@code successFields} (never null on the result, empty when absent).</li>
     * </ul>
     */
    static void completeInvocation(CompletableFuture<com.pathmind.api.addon.ActionResult> invocationFuture,
                                   Throwable actionFailure,
                                   long failureCountBefore, long failureCountAfter, String failureMessage,
                                   com.pathmind.execution.FailureDetail failureDetail,
                                   Map<String, Object> successFields) {
        if (actionFailure != null) {
            invocationFuture.completeExceptionally(actionFailure);
            return;
        }
        if (failureCountAfter > failureCountBefore) {
            String message = failureMessage == null || failureMessage.isBlank()
                ? "Pathmind action failed"
                : failureMessage;
            String status = failureDetail == null ? "failed" : failureDetail.getStatus();
            Map<String, Object> fields = failureDetail == null ? Map.of() : failureDetail.getFields();
            invocationFuture.complete(com.pathmind.api.addon.ActionResult.failure(status, message, fields));
            return;
        }
        invocationFuture.complete(com.pathmind.api.addon.ActionResult.success(successFields));
    }

    /**
     * Computes the action-specific success fields from before/after snapshots taken
     * around the dispatch on the client thread. Pure; package-visible for unit tests.
     *
     * <p>v1 contract: CRAFT → {@code produced} (inventory-count delta of the "Item"
     * argument, floored at 0); COLLECT → {@code collected} (delta of the "Block"
     * argument, falling back to "Item"); GOTO → final {@code x}, {@code y}, {@code z}
     * from {@code positionAfter}. All other actions: empty map. Argument keys are
     * case-insensitive; bare item/block ids get the {@code minecraft:} namespace.
     * Inventory snapshots map namespaced item ids to total counts; missing entries
     * count as 0. Never returns null.
     */
    static Map<String, Object> successFields(NodeType type, Map<String, Object> args,
                                             Map<String, Integer> inventoryBefore,
                                             Map<String, Integer> inventoryAfter,
                                             double[] positionAfter) {
        if (type == NodeType.CRAFT) {
            String itemId = namespacedArgument(args, "Item");
            return itemId == null
                ? Map.of()
                : Map.of("produced", inventoryDelta(itemId, inventoryBefore, inventoryAfter));
        }
        if (type == NodeType.COLLECT) {
            String itemId = namespacedArgument(args, "Block");
            if (itemId == null) {
                itemId = namespacedArgument(args, "Item");
            }
            return itemId == null
                ? Map.of()
                : Map.of("collected", inventoryDelta(itemId, inventoryBefore, inventoryAfter));
        }
        if (type == NodeType.GOTO && positionAfter != null && positionAfter.length >= 3) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("x", positionAfter[0]);
            fields.put("y", positionAfter[1]);
            fields.put("z", positionAfter[2]);
            return fields;
        }
        return Map.of();
    }

    private static boolean usesInventorySnapshot(NodeType type) {
        return type == NodeType.CRAFT || type == NodeType.COLLECT;
    }

    private static Map<String, Integer> inventorySnapshot(MinecraftClient client) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        if (client == null || client.player == null) {
            return counts;
        }
        PlayerInventory inventory = client.player.getInventory();
        int mainSize = Math.min(PlayerInventory.MAIN_SIZE, inventory.size());
        for (int slot = 0; slot < mainSize; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            counts.merge(itemId, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static double[] positionSnapshot(MinecraftClient client) {
        if (client == null || client.player == null) {
            return null;
        }
        return new double[]{client.player.getX(), client.player.getY(), client.player.getZ()};
    }

    private static int inventoryDelta(String itemId, Map<String, Integer> before, Map<String, Integer> after) {
        int beforeCount = before == null ? 0 : before.getOrDefault(itemId, 0);
        int afterCount = after == null ? 0 : after.getOrDefault(itemId, 0);
        return Math.max(0, afterCount - beforeCount);
    }

    private static String namespacedArgument(Map<String, Object> args, String name) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        Object value = null;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                value = entry.getValue();
                break;
            }
        }
        if (!(value instanceof String itemId) || itemId.isBlank()) {
            return null;
        }
        String normalized = itemId.trim();
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    /**
     * Returns whether the given node type may be invoked from a script.
     * Package-visible for {@link AddonActionInvoker}-focused unit tests.
     */
    static boolean isInvocable(NodeType type) {
        if (type == NodeType.STICKY_NOTE || type == NodeType.ADDON) {
            return false;
        }
        if (type == NodeType.WAIT) {
            // The single FLOW exception: a native timed pause, identical to the graph node.
            return true;
        }
        NodeCategory cat = type.getCategory();
        return cat == NodeCategory.WORLD || cat == NodeCategory.PLAYER || cat == NodeCategory.INTERFACE;
    }

    /**
     * Applies the optional "Mode" argument: selects one of the node type's modes by
     * case-insensitive {@link NodeMode} name (e.g. {@code "goto_block"} on GOTO).
     * Package-visible for unit tests.
     *
     * @return an error message on failure (non-string value, unknown mode, or a mode
     *         that does not belong to this node type), or null on success
     */
    static String applyMode(Node node, NodeType type, Object modeValue) {
        if (!(modeValue instanceof String modeName) || modeName.isBlank()) {
            return "Argument 'Mode' must be a non-empty string naming a node mode";
        }
        NodeMode requested;
        try {
            requested = NodeMode.valueOf(modeName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "Unknown mode '" + modeName + "' for action '" + type.name().toLowerCase(Locale.ROOT)
                + "' (available: " + availableModeNames(type) + ")";
        }
        NodeMode[] modes = NodeMode.getModesForNodeType(type);
        boolean valid = false;
        if (modes != null) {
            for (NodeMode mode : modes) {
                if (mode == requested) {
                    valid = true;
                    break;
                }
            }
        }
        if (!valid) {
            return "Mode '" + modeName + "' does not belong to action '" + type.name().toLowerCase(Locale.ROOT)
                + "' (available: " + availableModeNames(type) + ")";
        }
        node.setMode(requested);
        return null;
    }

    private static String availableModeNames(NodeType type) {
        NodeMode[] modes = NodeMode.getModesForNodeType(type);
        if (modes == null || modes.length == 0) {
            return "none";
        }
        StringBuilder names = new StringBuilder();
        for (NodeMode mode : modes) {
            if (!names.isEmpty()) names.append(", ");
            names.append(mode.name().toLowerCase(Locale.ROOT));
        }
        return names.toString();
    }

    /**
     * Applies the argument map onto the synthetic node's parameters.
     *
     * @return an error message on failure (unknown parameter / unsupported value type),
     *         or null on success
     */
    private static String applyArgs(Node node, NodeType type, Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || key.isBlank()) {
                return "Argument names must be non-empty strings";
            }
            String stringValue = stringify(value);
            if (stringValue == null) {
                return "Unsupported value type for argument '" + key + "' (only number, string, boolean)";
            }
            // MESSAGE stores its text in messageLines, not in the parameter list.
            if (type == NodeType.MESSAGE && key.equalsIgnoreCase("text")) {
                node.setMessageLines(List.of(stringValue));
                continue;
            }
            NodeParameter match = null;
            for (NodeParameter param : node.getParameters()) {
                if (param.getName().equalsIgnoreCase(key)) {
                    match = param;
                    break;
                }
            }
            if (match == null) {
                StringBuilder names = new StringBuilder();
                for (NodeParameter param : node.getParameters()) {
                    if (!names.isEmpty()) names.append(", ");
                    names.append(param.getName());
                }
                return "Unknown argument '" + key + "' for action '" + type.name().toLowerCase(Locale.ROOT)
                    + "' (available: " + (names.isEmpty() ? "none" : names) + ")";
            }
            match.setStringValue(stringValue);
        }
        return null;
    }

    /**
     * Converts a Double/Boolean/String argument value to the node-parameter string form.
     * Integral doubles lose the trailing {@code .0} (Lua numbers are all doubles, but
     * integer-typed parameters must parse). Returns null for unsupported types.
     */
    private static String stringify(Object value) {
        if (value instanceof Double d) {
            if (d == Math.floor(d) && !d.isInfinite()) {
                return Long.toString(d.longValue());
            }
            return d.toString();
        }
        if (value instanceof Boolean b) {
            return b.toString();
        }
        if (value instanceof String s) {
            return s;
        }
        return null;
    }
}
