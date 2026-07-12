package com.pathmind.execution;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fork-only side channel between {@link PreciseCompletionTracker} and the addon
 * layer: when the tracker "fails a task gracefully" (e.g. a goto that never
 * became active — path unavailable), it completes the node future NORMALLY for
 * graph semantics, which the addon invoker would otherwise read as success
 * (the false-positive GOTO envelope found by the mission-primitives research).
 *
 * <p>{@code GracefulTaskStops.mark(future, message)} records that graceful stop
 * against the future's identity; {@code consume(future)} retrieves the message
 * exactly once so the invoker can turn the outcome into a classified
 * {@code no_route} failure envelope. Graph execution never consumes the marker,
 * so graph behavior is unchanged.
 */
class GracefulTaskStopsTest {

    @Test
    void markThenConsumeReturnsTheMessageExactlyOnce() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        GracefulTaskStops.mark(future, "Goto task never became active");

        assertEquals("Goto task never became active", GracefulTaskStops.consume(future));
        assertNull(GracefulTaskStops.consume(future), "second consume must return null");
    }

    @Test
    void unmarkedFutureConsumesToNull() {
        assertNull(GracefulTaskStops.consume(new CompletableFuture<Void>()));
    }

    @Test
    void nullArgumentsAreSafeNoOps() {
        assertNull(GracefulTaskStops.consume(null));
        // Must not throw:
        GracefulTaskStops.mark(null, "message");
        GracefulTaskStops.mark(new CompletableFuture<Void>(), null);
    }

    @Test
    void markersAreScopedPerFuture() {
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> second = new CompletableFuture<>();
        GracefulTaskStops.mark(first, "first stop");

        assertNull(GracefulTaskStops.consume(second), "marker must not leak across futures");
        assertEquals("first stop", GracefulTaskStops.consume(first));
    }
}
