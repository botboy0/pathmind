package com.pathmind.execution;

import com.pathmind.api.addon.PathmindRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for PathmindRuntimeImpl.
 *
 * <p>Tests that are meaningful in a headless (no Minecraft client) context:
 * <ul>
 *   <li>sendErrorToChat in a headless context must NOT throw NPE (null-safe guard).</li>
 *   <li>PathmindRuntimeImpl can be constructed with an ExecutionManager instance.</li>
 *   <li>getRuntime/setRuntime round-trips through AddonNodeContext.</li>
 * </ul>
 *
 * <p>Phase 2, Plan 01 — covers the BIND-04 null-safety requirement.
 */
class PathmindRuntimeImplTest {

    /**
     * Constructing PathmindRuntimeImpl with a real ExecutionManager must not throw.
     */
    @Test
    void constructorAcceptsExecutionManagerInstance() {
        assertDoesNotThrow(() -> new PathmindRuntimeImpl(ExecutionManager.getInstance()),
            "PathmindRuntimeImpl constructor must not throw");
    }

    /**
     * sendErrorToChat in a headless context (MinecraftClient.getInstance() returns null)
     * must return quietly without throwing NPE.
     *
     * <p>In the test JVM, MinecraftClient is never initialised so
     * MinecraftClient.getInstance() returns null, exercising the null-guard.
     */
    @Test
    void sendErrorToChatIsNullSafeInHeadlessContext() {
        PathmindRuntimeImpl impl = new PathmindRuntimeImpl(ExecutionManager.getInstance());
        // Must not throw — MinecraftClient.getInstance() returns null in headless JVM
        assertDoesNotThrow(() -> impl.sendErrorToChat("headless test error"),
            "sendErrorToChat must not throw when MinecraftClient is null");
    }

    /**
     * sendErrorToChat with a null message must not throw.
     */
    @Test
    void sendErrorToChatIsNullSafeForNullMessage() {
        PathmindRuntimeImpl impl = new PathmindRuntimeImpl(ExecutionManager.getInstance());
        assertDoesNotThrow(() -> impl.sendErrorToChat(null),
            "sendErrorToChat must not throw on null message");
    }

    /**
     * AddonNodeContext.getRuntime/setRuntime round-trips a PathmindRuntime instance.
     */
    @Test
    void addonNodeContextRoundTripsRuntime() {
        com.pathmind.api.addon.AddonNodeContext ctx = new com.pathmind.api.addon.AddonNodeContext();
        PathmindRuntime runtime = new PathmindRuntimeImpl(ExecutionManager.getInstance());

        ctx.setRuntime(runtime);
        PathmindRuntime retrieved = ctx.getRuntime();

        assertNotNull(retrieved, "getRuntime() must return the value that was set");
        // Same instance
        org.junit.jupiter.api.Assertions.assertSame(runtime, retrieved,
            "getRuntime() must return the exact instance that was set");
    }
}
