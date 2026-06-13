package com.pathmind.execution;

import com.pathmind.api.addon.PathmindRuntime;
import com.pathmind.nodes.NodeType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;
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
     * <p>TODO: wired in plan 03 — full PathmindNavigator/Baritone awaitable integration.
     *
     * @throws UnsupportedOperationException always (stub for plan 03)
     */
    @Override
    public CompletableFuture<Void> moveTo(double x, double y, double z) {
        // TODO: wired in plan 03 (PathmindNavigator.startGoto + Baritone integration)
        throw new UnsupportedOperationException(
            "pathmind.moveTo is not yet wired — implemented in plan 03");
    }

    // -------------------------------------------------------------------------
    // Game-state reads — wired in Phase 2 Plan 03/04
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>TODO: wired in plan 03/04 — main-thread dispatch via MinecraftClient.execute.
     *
     * @return [0, 0, 0] (stub for plan 03)
     */
    @Override
    public double[] getPosition() {
        // TODO: wired in plan 03/04 (MinecraftClient.execute dispatch + CompletableFuture handback)
        return new double[]{0, 0, 0};
    }

    /**
     * {@inheritDoc}
     *
     * <p>TODO: wired in plan 04 — main-thread dispatch + inventory slot marshaling.
     *
     * @return empty array (stub for plan 04)
     */
    @Override
    public Object[] getInventory() {
        // TODO: wired in plan 04 (client-thread dispatch + inventory marshaling)
        return new Object[0];
    }

    /**
     * {@inheritDoc}
     *
     * <p>TODO: wired in plan 04 — main-thread dispatch + block state lookup.
     *
     * @return null (stub for plan 04)
     */
    @Override
    public String getBlock(double x, double y, double z) {
        // TODO: wired in plan 04 (client-thread dispatch + block registry lookup)
        return null;
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
