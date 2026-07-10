package com.pathmind.execution;

import com.pathmind.api.addon.PathmindRuntime;
import com.pathmind.nodes.NodeType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

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
     * <p>Reads from the active chain scope first, then falls back to global variables.
     * Unmarshals {@code PARAM_AMOUNT} → Double, {@code PARAM_BOOLEAN} → Boolean,
     * {@code PARAM_MESSAGE} → String.
     */
    @Override
    public Object getVariable(String name) {
        ExecutionManager.RuntimeVariable rv =
            executionManager.getRuntimeVariableFromAnyActiveChain(name);
        if (rv == null) return null;
        Map<String, String> values = rv.getValues();
        return switch (rv.getType()) {
            case PARAM_AMOUNT -> {
                String raw = values.get("Amount");
                if (raw == null) yield null;
                try {
                    yield Double.parseDouble(raw);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case PARAM_BOOLEAN -> {
                String raw = values.get("Toggle");
                yield raw != null ? Boolean.parseBoolean(raw) : null;
            }
            case PARAM_MESSAGE -> values.get("Text");
            default -> null;
        };
    }

    /**
     * {@inheritDoc}
     *
     * <p>Marshals Double → {@code PARAM_AMOUNT} (key "Amount"),
     * Boolean → {@code PARAM_BOOLEAN} (keys "Mode"/"Toggle"/"Variable"),
     * String → {@code PARAM_MESSAGE} (key "Text").
     */
    @Override
    public void setVariable(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                "Unsupported variable value: null (use an explicit Double, Boolean, or String)");
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
                    + " — only Double, Boolean, and String are supported in v1");
        }
        executionManager.setRuntimeVariableForAnyActiveChain(
            name, new ExecutionManager.RuntimeVariable(type, vals));
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
