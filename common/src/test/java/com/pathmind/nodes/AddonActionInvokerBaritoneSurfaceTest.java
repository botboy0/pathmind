package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import com.pathmind.api.addon.ActionInfo;
import com.pathmind.api.addon.ActionResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless contract tests for the Baritone side of the script action surface
 * (Baritone-integration milestone, agreed 2026-07-13):
 *
 * <ul>
 *   <li><strong>Missing-Baritone gate:</strong> script calls to Baritone-requiring
 *       actions without a usable Baritone API return a classified
 *       {@code unsupported} envelope (class 2) instead of raising or toasting.
 *       Graph nodes keep their notification behavior — this gate lives purely in
 *       the fork's invoker layer.</li>
 *   <li><strong>{@code baritone_command}:</strong> a fork-owned synthetic catalog
 *       action (not a {@link NodeType}) that dispatches a raw {@code #...} command
 *       through Baritone's command manager. Caller errors raise; missing Baritone
 *       returns {@code unsupported}.</li>
 * </ul>
 *
 * <p>These run headless, where {@code BaritoneDependencyChecker.isBaritoneApiPresent()}
 * is false — exactly the state the gate classifies.
 */
class AddonActionInvokerBaritoneSurfaceTest {

    private static Throwable failureOf(CompletableFuture<?> future) {
        assertTrue(future.isCompletedExceptionally(), "future must complete exceptionally");
        try {
            future.get();
            return null; // unreachable
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    // ------------------------------------------------------------------
    // Missing-Baritone gate: Baritone-requiring node actions
    // ------------------------------------------------------------------

    @Test
    void gotoWithoutBaritoneReturnsUnsupportedEnvelope() {
        ActionResult result = AddonActionInvoker
            .invoke("goto", Map.of("X", 1.0, "Y", 64.0, "Z", 1.0))
            .join();

        assertFalse(result.isOk());
        assertEquals("unsupported", result.getStatus(),
            "a Baritone-requiring action without Baritone must classify as unsupported, not raise");
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().toLowerCase().contains("baritone"),
            "message must name Baritone, got: " + result.getMessage());
    }

    @Test
    void collectWithoutBaritoneReturnsUnsupportedEnvelope() {
        ActionResult result = AddonActionInvoker
            .invoke("collect", Map.of("Block", "oak_log", "Amount", 2.0))
            .join();

        assertFalse(result.isOk());
        assertEquals("unsupported", result.getStatus());
    }

    @Test
    void nonBaritoneActionsAreNotGated() {
        // JUMP does not require Baritone: headless it must still fail on the
        // missing client (class 3), proving the gate is scoped to requiresBaritone().
        Throwable cause = failureOf(AddonActionInvoker.invoke("jump", null));
        assertInstanceOf(IllegalStateException.class, cause);
    }

    // ------------------------------------------------------------------
    // baritone_command — synthetic fork action
    // ------------------------------------------------------------------

    @Test
    void baritoneCommandResolvesAsAnActionAndGatesOnBaritone() {
        // Headless there is no Baritone, so the call must resolve the synthetic
        // action name (NOT "unknown action") and return the unsupported envelope.
        ActionResult result = AddonActionInvoker
            .invoke("baritone_command", Map.of("Command", "#goto 100 64 100"))
            .join();

        assertFalse(result.isOk());
        assertEquals("unsupported", result.getStatus());
        assertTrue(result.getMessage().toLowerCase().contains("baritone"));
    }

    @Test
    void baritoneCommandActionNameIsCaseInsensitive() {
        ActionResult result = AddonActionInvoker
            .invoke("BARITONE_COMMAND", Map.of("Command", "#stop"))
            .join();

        assertFalse(result.isOk());
        assertEquals("unsupported", result.getStatus());
    }

    @Test
    void baritoneCommandWithoutCommandArgumentRaises() {
        // Caller error (class 1) — validated before the Baritone gate so typos
        // surface even in Baritone-less environments.
        Throwable missing = failureOf(AddonActionInvoker.invoke("baritone_command", Map.of()));
        assertInstanceOf(IllegalArgumentException.class, missing);
        assertTrue(missing.getMessage().contains("Command"),
            "error must name the missing argument, got: " + missing.getMessage());

        Throwable blank = failureOf(AddonActionInvoker.invoke("baritone_command", Map.of("Command", "  #  ")));
        assertInstanceOf(IllegalArgumentException.class, blank);

        Throwable nonString = failureOf(AddonActionInvoker.invoke("baritone_command", Map.of("Command", 42.0)));
        assertInstanceOf(IllegalArgumentException.class, nonString);
    }

    @Test
    void baritoneCommandArgumentKeyIsCaseInsensitive() {
        ActionResult result = AddonActionInvoker
            .invoke("baritone_command", Map.of("command", "#goto 100 64 100"))
            .join();

        assertFalse(result.isOk());
        assertEquals("unsupported", result.getStatus(),
            "lowercase 'command' key must be accepted (all action args are case-insensitive)");
    }

    @Test
    void unknownArgumentsOnBaritoneCommandRaise() {
        Throwable cause = failureOf(AddonActionInvoker.invoke(
            "baritone_command", Map.of("Command", "#stop", "Speed", 2.0)));
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("Speed"),
            "error must name the unknown argument, got: " + cause.getMessage());
    }

    @Test
    void normalizeBaritoneCommandStripsPrefixAndTrims() {
        assertEquals("goto 100 64 100", AddonActionInvoker.normalizeBaritoneCommand("#goto 100 64 100"));
        assertEquals("stop", AddonActionInvoker.normalizeBaritoneCommand("  #stop "));
        // The leading # is optional — Baritone's command manager takes the bare string.
        assertEquals("mine 16 iron_ore", AddonActionInvoker.normalizeBaritoneCommand("mine 16 iron_ore"));
        assertNull(AddonActionInvoker.normalizeBaritoneCommand("#"));
        assertNull(AddonActionInvoker.normalizeBaritoneCommand("   "));
        assertNull(AddonActionInvoker.normalizeBaritoneCommand(null));
    }

    // ------------------------------------------------------------------
    // Catalog exposure — the addon generates pathmind.baritone_command_ from this
    // ------------------------------------------------------------------

    @Test
    void catalogListsBaritoneCommandWithItsCommandParameter() {
        ActionInfo entry = AddonActionCatalog.list().stream()
            .filter(a -> "baritone_command".equals(a.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("catalog must contain baritone_command"));

        assertNotNull(entry.displayName());
        assertFalse(entry.displayName().isBlank());
        assertTrue(entry.params().stream().anyMatch(p -> "Command".equals(p.name())),
            "baritone_command must expose the Command parameter");
    }
}
