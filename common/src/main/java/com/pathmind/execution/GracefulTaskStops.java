package com.pathmind.execution;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Fork-only side channel for tracker stops that preserve normal graph completion.
 * Markers are weakly keyed by future identity and consumed once by addon invocation.
 */
public final class GracefulTaskStops {
    private static final Map<CompletableFuture<?>, String> STOPS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private GracefulTaskStops() {
    }

    /** Records a graceful stop for the given future. Null arguments are ignored. */
    public static void mark(CompletableFuture<?> future, String message) {
        if (future != null && message != null) {
            STOPS.put(future, message);
        }
    }

    /** Returns and removes the future's graceful-stop message, or null when absent. */
    public static String consume(CompletableFuture<?> future) {
        return future == null ? null : STOPS.remove(future);
    }
}
