package com.pathmind.execution;

import com.pathmind.api.addon.PathmindRuntime;
import com.pathmind.nodes.AddonListEntryCodec;
import com.pathmind.nodes.NodeType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Pathmind-side implementation of the {@link PathmindRuntime} services interface.
 *
 * <p>Wired into every addon node execution in {@code Node.executeAddonNode()} so that
 * addon executors (e.g. the Lua scripting addon) can access Pathmind runtime state
 * without depending on implementation classes.
 *
 * <p>Threading contract:
 * <ul>
 *   <li>{@link #sendErrorToChat} — any thread; dispatches to the client thread via
 *       {@code MinecraftClient.execute}.</li>
 *   <li>{@link #getVariable} / {@link #setVariable} — any thread; delegates to
 *       {@link ExecutionManager} which is thread-safe for these operations.</li>
 *   <li>{@link #moveTo} — any thread; returns a future; game tick thread drives navigation.</li>
 *   <li>{@link #getPosition} / {@link #getInventory} / {@link #getBlock} — any thread;
 *       dispatched to the client thread internally (wired in Plan 03/04).</li>
 * </ul>
 *
 * <p>Part of the Pathmind addon API implementation — Phase 2 runtime bridge.
 */
public class PathmindRuntimeImpl implements PathmindRuntime {

    /**
     * Chat message prefix matching the Pathmind brand style.
     * Verified against Node.java line 169.
     */
    private static final String CHAT_MESSAGE_PREFIX = "§4[§cPathmind§4] §7";

    private final ExecutionManager executionManager;

    /**
     * Constructs a PathmindRuntimeImpl backed by the given ExecutionManager.
     *
     * @param executionManager the ExecutionManager singleton for this game session
     */
    public PathmindRuntimeImpl(ExecutionManager executionManager) {
        this.executionManager = executionManager;
    }

    // -------------------------------------------------------------------------
    // Variable access — backed by ExecutionManager global runtime variables
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Reads from the active chain scope first, then falls back to global variables,
     * then to the runtime-list namespace. Unmarshals {@code PARAM_AMOUNT} /
     * {@code PARAM_DISTANCE} → Double, {@code PARAM_BOOLEAN} → Boolean,
     * {@code PARAM_MESSAGE} → String, {@code PARAM_COORDINATE} →
     * {@code Map{"x","y","z"}} (Doubles), and runtime lists → {@code List<Object>}
     * (element marshaling described on {@link #marshalRuntimeList}).
     */
    @Override
    public Object getVariable(String name) {
        ExecutionManager.RuntimeVariable rv =
            executionManager.getRuntimeVariableFromAnyActiveChain(name);
        if (rv == null) {
            // Not a variable — the name may refer to a runtime list (separate namespace,
            // shared with the Create List / Add to List / List Item nodes).
            return marshalRuntimeList(executionManager.getRuntimeListFromAnyActiveChain(name));
        }
        Map<String, String> values = rv.getValues();
        return switch (rv.getType()) {
            case PARAM_AMOUNT -> parseDoubleOrNull(getValueIgnoreCase(values, "Amount"));
            case PARAM_BOOLEAN -> {
                String raw = getValueIgnoreCase(values, "Toggle");
                yield raw != null ? Boolean.parseBoolean(raw) : null;
            }
            case PARAM_MESSAGE -> getValueIgnoreCase(values, "Text");
            case PARAM_DISTANCE -> parseDoubleOrNull(getValueIgnoreCase(values, "Distance"));
            case PARAM_COORDINATE -> marshalCoordinateValues(values);
            default -> null;
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p>Marshals Double → {@code PARAM_AMOUNT} (key "Amount"),
     * Boolean → {@code PARAM_BOOLEAN} (keys "Mode"/"Toggle"/"Variable"),
     * String → {@code PARAM_MESSAGE} (key "Text"),
     * coordinate-shaped {@code Map{"x","y","z"}} → {@code PARAM_COORDINATE}
     * (keys "X"/"Y"/"Z"), and {@code List} → a runtime list (separate namespace,
     * consumable by the List Item / Remove from List / … nodes). List elements must
     * be uniformly numbers, strings, booleans, or coordinate maps.
     */
    @Override
    public void setVariable(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                "Unsupported variable value: null (use an explicit Double, Boolean, or String)");
        }
        if (value instanceof Map<?, ?> map) {
            setCoordinateVariable(name, map);
            return;
        }
        if (value instanceof List<?> list) {
            setListVariable(name, list);
            return;
        }
        Map<String, String> vals = new HashMap<>();
        NodeType type;
        if (value instanceof Double d) {
            type = NodeType.PARAM_AMOUNT;
            vals.put("Amount", d.toString());
        } else if (value instanceof Boolean b) {
            type = NodeType.PARAM_BOOLEAN;
            vals.put("Mode", "literal");
            vals.put("Toggle", b.toString());
            vals.put("Variable", "");
        } else if (value instanceof String s) {
            type = NodeType.PARAM_MESSAGE;
            vals.put("Text", s);
        } else {
            throw new IllegalArgumentException(
                "Unsupported variable type: " + value.getClass().getSimpleName()
                    + " — only Double, Boolean, String, coordinate Map{x,y,z}, and List are supported");
        }
        executionManager.setRuntimeVariableForAnyActiveChain(
            name, new ExecutionManager.RuntimeVariable(type, vals));
    }

    // -------------------------------------------------------------------------
    // Table marshaling — coordinate maps and runtime lists (v2)
    // -------------------------------------------------------------------------

    /**
     * Stores a coordinate-shaped map as a {@code PARAM_COORDINATE} variable.
     * The map must contain exactly the keys x, y, z (case-insensitive) with
     * {@link Number} values — anything else is rejected so typos surface instead
     * of silently dropping data.
     */
    private void setCoordinateVariable(String name, Map<?, ?> map) {
        Double x = null, y = null, z = null;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(
                    "Unsupported map variable: keys must be strings");
            }
            if (!(entry.getValue() instanceof Number n)) {
                throw new IllegalArgumentException(
                    "Unsupported map variable: value for '" + key + "' must be a number"
                        + " (only coordinate maps {x, y, z} are supported)");
            }
            switch (key.toLowerCase(Locale.ROOT)) {
                case "x" -> x = n.doubleValue();
                case "y" -> y = n.doubleValue();
                case "z" -> z = n.doubleValue();
                default -> throw new IllegalArgumentException(
                    "Unsupported map variable: unknown key '" + key
                        + "' (only coordinate maps {x, y, z} are supported)");
            }
        }
        if (x == null || y == null || z == null) {
            throw new IllegalArgumentException(
                "Unsupported map variable: coordinate maps need all of x, y, z");
        }
        executionManager.setRuntimeVariableForAnyActiveChain(name,
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_COORDINATE, coordinateValues(x, y, z)));
    }

    /**
     * Stores a list as a runtime list. Elements must be uniformly one of:
     * {@link Number} (→ {@code PARAM_AMOUNT} entries), {@link String} (→
     * {@code PARAM_MESSAGE}), {@link Boolean} (→ {@code PARAM_BOOLEAN}), or
     * coordinate maps (→ {@code PARAM_COORDINATE}). Mixed or empty lists are
     * rejected — the element type is part of the runtime list and the node-side
     * list operations enforce it.
     */
    private void setListVariable(String name, List<?> list) {
        if (list.isEmpty()) {
            throw new IllegalArgumentException(
                "Cannot store an empty list as variable '" + name
                    + "' — the element type cannot be inferred");
        }
        NodeType elementType = null;
        List<String> entries = new ArrayList<>(list.size());
        for (Object element : list) {
            NodeType entryType;
            Map<String, String> vals = new HashMap<>();
            if (element instanceof Number n) {
                entryType = NodeType.PARAM_AMOUNT;
                vals.put("Amount", stringifyNumber(n.doubleValue()));
            } else if (element instanceof String s) {
                entryType = NodeType.PARAM_MESSAGE;
                vals.put("Text", s);
            } else if (element instanceof Boolean b) {
                entryType = NodeType.PARAM_BOOLEAN;
                vals.put("Mode", "literal");
                vals.put("Toggle", b.toString());
                vals.put("Variable", "");
            } else if (element instanceof Map<?, ?> map) {
                entryType = NodeType.PARAM_COORDINATE;
                Double[] xyz = requireCoordinateMap(name, map);
                vals.putAll(coordinateValues(xyz[0], xyz[1], xyz[2]));
            } else {
                throw new IllegalArgumentException(
                    "Unsupported list element for variable '" + name + "': "
                        + (element == null ? "null" : element.getClass().getSimpleName())
                        + " — only numbers, strings, booleans, and coordinate maps are supported");
            }
            if (elementType == null) {
                elementType = entryType;
            } else if (elementType != entryType) {
                throw new IllegalArgumentException(
                    "Mixed list for variable '" + name + "' — all elements must have the same type");
            }
            entries.add(AddonListEntryCodec.serialize(vals));
        }
        executionManager.setRuntimeListForAnyActiveChain(
            name, new ExecutionManager.RuntimeList(elementType, entries));
    }

    /** Validates a list element as a coordinate map and returns {x, y, z}. */
    private static Double[] requireCoordinateMap(String name, Map<?, ?> map) {
        Double x = null, y = null, z = null;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof Number n) {
                switch (key.toLowerCase(Locale.ROOT)) {
                    case "x" -> x = n.doubleValue();
                    case "y" -> y = n.doubleValue();
                    case "z" -> z = n.doubleValue();
                    default -> throw new IllegalArgumentException(
                        "Unsupported list element for variable '" + name
                            + "': map key '" + key + "' (only coordinate maps {x, y, z} are supported)");
                }
            } else {
                throw new IllegalArgumentException(
                    "Unsupported list element for variable '" + name
                        + "': maps must have string keys and number values");
            }
        }
        if (x == null || y == null || z == null) {
            throw new IllegalArgumentException(
                "Unsupported list element for variable '" + name
                    + "': coordinate maps need all of x, y, z");
        }
        return new Double[]{x, y, z};
    }

    /**
     * Builds the {@code PARAM_COORDINATE} values-map for the given coordinates.
     * Both key cases are written, matching what the node-side list executors emit
     * (e.g. Create List block scans).
     */
    private static Map<String, String> coordinateValues(double x, double y, double z) {
        Map<String, String> vals = new HashMap<>();
        vals.put("X", stringifyNumber(x));
        vals.put("Y", stringifyNumber(y));
        vals.put("Z", stringifyNumber(z));
        vals.put("x", stringifyNumber(x));
        vals.put("y", stringifyNumber(y));
        vals.put("z", stringifyNumber(z));
        return vals;
    }

    /**
     * Marshals a runtime list into the plain-Java list form of the API contract,
     * or returns null when the list does not exist. Serialized entries unmarshal
     * by element type ({@code PARAM_COORDINATE} → {@code Map{x,y,z}} of Doubles,
     * {@code PARAM_AMOUNT} → Double, {@code PARAM_BOOLEAN} → Boolean,
     * {@code PARAM_MESSAGE} → String, anything else → {@code Map<String,String>}
     * of its raw values); raw entries (entity UUIDs, item ids, GUI slot tokens)
     * stay strings. Unmarshalable entries are skipped rather than erroring.
     */
    private Object marshalRuntimeList(ExecutionManager.RuntimeList list) {
        if (list == null) {
            return null;
        }
        List<Object> result = new ArrayList<>(list.size());
        for (String entry : list.getEntries()) {
            Object marshaled = marshalListEntry(list.getElementType(), entry);
            if (marshaled != null) {
                result.add(marshaled);
            }
        }
        return result;
    }

    /** Marshals a single runtime-list entry; returns null for unmarshalable entries. */
    private Object marshalListEntry(NodeType elementType, String entry) {
        if (entry == null || entry.isEmpty()) {
            return null;
        }
        if (!AddonListEntryCodec.isSerialized(entry)) {
            return entry;
        }
        Map<String, String> values = AddonListEntryCodec.deserialize(entry);
        if (values.isEmpty()) {
            return null;
        }
        if (elementType == null) {
            return dedupeValuesMap(values);
        }
        return switch (elementType) {
            case PARAM_COORDINATE -> marshalCoordinateValues(values);
            case PARAM_AMOUNT -> parseDoubleOrNull(getValueIgnoreCase(values, "Amount"));
            case PARAM_BOOLEAN -> {
                String raw = getValueIgnoreCase(values, "Toggle");
                yield raw != null ? Boolean.parseBoolean(raw) : null;
            }
            case PARAM_MESSAGE -> getValueIgnoreCase(values, "Text");
            default -> dedupeValuesMap(values);
        };
    }

    /**
     * Marshals a coordinate values-map to {@code Map{"x","y","z"}} of Doubles,
     * or null when any component is missing/unparsable.
     */
    private static Object marshalCoordinateValues(Map<String, String> values) {
        Double x = parseDoubleOrNull(getValueIgnoreCase(values, "X"));
        Double y = parseDoubleOrNull(getValueIgnoreCase(values, "Y"));
        Double z = parseDoubleOrNull(getValueIgnoreCase(values, "Z"));
        if (x == null || y == null || z == null) {
            return null;
        }
        Map<String, Object> coord = new HashMap<>();
        coord.put("x", x);
        coord.put("y", y);
        coord.put("z", z);
        return coord;
    }

    /**
     * Generic fallback for unrecognized serialized entries: the raw values-map with
     * case-duplicate keys collapsed (values maps often carry both "X" and "x") and
     * internal {@code __pathmind} metadata keys dropped.
     */
    private static Object dedupeValuesMap(Map<String, String> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.startsWith("__pathmind") || entry.getValue() == null) {
                continue;
            }
            if (seen.add(key.toLowerCase(Locale.ROOT))) {
                result.put(key, entry.getValue());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static String getValueIgnoreCase(Map<String, String> values, String key) {
        String direct = values.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Double parseDoubleOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Stringifies a double for node-parameter storage: integral values lose the
     * trailing {@code .0} so integer-typed parameters (e.g. coordinates) parse.
     */
    private static String stringifyNumber(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    // -------------------------------------------------------------------------
    // Navigation — wired in Phase 2 Plan 03
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Wraps {@link PathmindNavigator#startGoto(net.minecraft.util.math.BlockPos, String, CompletableFuture)}
     * and returns the same future that PathmindNavigator will complete when the player
     * arrives or when navigation fails.
     *
     * <p>Threading contract: this method does NOT block — it returns the future immediately.
     * Blocking is the addon worker thread's responsibility (it calls {@code navFuture.get()}).
     * The game tick thread drives navigation and completes the future on arrival or failure.
     *
     * <p>Note (Pitfall 6): a second concurrent {@code moveTo} call will cancel the prior
     * navigation because {@link PathmindNavigator} calls {@code stopInternal(false, "replaced")}
     * inside {@code startGoto}. For v1 scripting, one moveTo at a time per script is expected.
     *
     * <p>Baritone-absent behavior: PathmindNavigator has its own A* pathfinding fallback
     * used when Baritone is absent. No Baritone-presence gate is needed here — the Navigator
     * handles the fallback internally (RESEARCH.md Finding 2).
     */
    @Override
    public CompletableFuture<Void> moveTo(double x, double y, double z) {
        CompletableFuture<Void> navFuture = new CompletableFuture<>();
        BlockPos target = BlockPos.ofFloored(x, y, z);
        boolean started = PathmindNavigator.getInstance().startGoto(target, "Lua moveTo", navFuture);
        if (!started) {
            // startGoto returns false only when targetPos or future is null — impossible here,
            // but complete exceptionally so the worker's .get() surfaces a clear error.
            navFuture.completeExceptionally(
                new RuntimeException("moveTo: PathmindNavigator.startGoto returned false"));
        }
        return navFuture;
    }

    // -------------------------------------------------------------------------
    // Game-state reads — wired in Phase 2 Plan 03/04
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Dispatches to the client thread via {@link MinecraftClient#execute} and reads the
     * player's current position. The worker thread blocks on a {@link CompletableFuture} with
     * a 30-second timeout (Pitfall 3 — game paused / tick queue stalled; never hangs forever).
     *
     * <p>If the client or player is null (loading screen, headless tests) the future completes
     * with {@code null} and the safe default {@code {0, 0, 0}} is returned without throwing.
     *
     * <p>Threading: the worker thread calls this; MC reads happen exclusively on the client
     * thread (T-02-13).
     *
     * @return {@code double[]{x, y, z}} of the player's current position, or {@code {0,0,0}}
     *         on null/timeout.
     */
    @Override
    public double[] getPosition() {
        CompletableFuture<double[]> result = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                result.complete(null);
                return;
            }
            result.complete(new double[]{
                client.player.getX(),
                client.player.getY(),
                client.player.getZ()
            });
        });
        try {
            double[] pos = result.get(30, TimeUnit.SECONDS);
            return pos != null ? pos : new double[]{0, 0, 0};
        } catch (Exception e) {
            return new double[]{0, 0, 0};
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Dispatches to the client thread via {@link MinecraftClient#execute} and reads the
     * player's main inventory. The worker thread blocks on a {@link CompletableFuture} with
     * a 30-second timeout (Pitfall 3 — T-02-14).
     *
     * <p>Each non-empty slot is returned as an {@code Object[3]} carrier:
     * <pre>
     *   Object[0] = Integer  — slot index (0-based, matches PlayerInventory main slot numbering)
     *   Object[1] = String   — namespaced item id (e.g. {@code "minecraft:diamond_sword"})
     *   Object[2] = Integer  — stack count
     * </pre>
     * Only non-empty stacks ({@link ItemStack#isEmpty()} == false) are included.
     * The array is 1-indexed in Lua (handled by the addon binding layer).
     *
     * <p>Element shape is scalar-only ({@code Integer}, {@code String}, {@code Integer}) so
     * the addon binding can read it positionally without any shared Pathmind types
     * (version-agnostic API contract — CONTEXT decision).
     *
     * <p>Threading: MC reads happen exclusively on the client thread (T-02-13).
     *
     * @return {@code Object[]} where each element is an {@code Object[3]} carrier as described
     *         above, or an empty array on null/timeout.
     */
    @Override
    public Object[] getInventory() {
        CompletableFuture<Object[]> result = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                result.complete(new Object[0]);
                return;
            }
            PlayerInventory inventory = client.player.getInventory();
            List<Object> slots = new ArrayList<>();
            // Iterate main inventory slots only (0..MAIN_SIZE-1 = 0..35)
            int mainSize = Math.min(PlayerInventory.MAIN_SIZE, inventory.size());
            for (int i = 0; i < mainSize; i++) {
                ItemStack stack = inventory.getStack(i);
                if (stack == null || stack.isEmpty()) continue;
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                slots.add(new Object[]{i, itemId, stack.getCount()});
            }
            result.complete(slots.toArray());
        });
        try {
            Object[] inv = result.get(30, TimeUnit.SECONDS);
            return inv != null ? inv : new Object[0];
        } catch (Exception e) {
            return new Object[0];
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Dispatches to the client thread via {@link MinecraftClient#execute} and looks up
     * the block state at the given coordinates. The worker thread blocks on a
     * {@link CompletableFuture} with a 30-second timeout (Pitfall 3 — T-02-14).
     *
     * <p>Returns {@code null} (mapped to Lua {@code nil} by the binding) when:
     * <ul>
     *   <li>The client or world is null (loading screen / headless test).</li>
     *   <li>The chunk containing the requested position is not loaded.</li>
     * </ul>
     * This matches the CONTEXT contract: {@code getBlock} returns {@code nil} for unloaded
     * chunks rather than erroring (safe default; T-02-15 accepted).
     *
     * <p>Threading: MC reads happen exclusively on the client thread (T-02-13).
     *
     * @param x  X coordinate of the block
     * @param y  Y coordinate of the block
     * @param z  Z coordinate of the block
     * @return namespaced block id string (e.g. {@code "minecraft:stone"}), or {@code null}
     *         if the chunk is unloaded or the client/world is unavailable.
     */
    @Override
    public String getBlock(double x, double y, double z) {
        CompletableFuture<String> result = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) {
                result.complete(null);
                return;
            }
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            WorldChunk chunk = client.world.getChunk(chunkX, chunkZ);
            if (chunk == null || chunk.isEmpty()) {
                result.complete(null);
                return;
            }
            String blockId = Registries.BLOCK.getId(
                chunk.getBlockState(pos).getBlock()).toString();
            result.complete(blockId);
        });
        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Generic action dispatch — v2 invokeAction
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link com.pathmind.nodes.AddonActionInvoker}, which builds a
     * synthetic node of the requested type and routes it through the normal
     * {@code NodeCommandDispatcher} path on the client thread.
     */
    @Override
    public CompletableFuture<Void> invokeAction(String actionName, Map<String, Object> args) {
        return com.pathmind.nodes.AddonActionInvoker.invoke(actionName, args);
    }

    // -------------------------------------------------------------------------
    // Error surfacing — BIND-04: fully implemented
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Null-safe: returns quietly when the Minecraft client is null (headless context)
     * or when the player is null (loading screen / between worlds). The message is sent
     * with the Pathmind chat prefix.
     */
    @Override
    public void sendErrorToChat(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        // Dispatch to client thread — MinecraftClient.execute queues for the next tick
        client.execute(() -> {
            if (client.player == null) return;
            String safeMessage = message != null ? message : "(null)";
            client.player.sendMessage(
                Text.literal(CHAT_MESSAGE_PREFIX + safeMessage), false);
        });
    }
}
