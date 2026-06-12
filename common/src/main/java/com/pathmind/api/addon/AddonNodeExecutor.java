package com.pathmind.api.addon;

import java.util.concurrent.CompletableFuture;

/**
 * Executes a custom addon node. Invoked by Pathmind's execution manager when
 * a node of the matching addon type is reached in the graph.
 *
 * <p><strong>Threading contract:</strong> This method must never block the game thread.
 * Return a {@link CompletableFuture} immediately and complete it off-thread when the
 * work is done (or return {@code CompletableFuture.completedFuture(NodeResult.SUCCESS)}
 * for no-op nodes).
 *
 * <p>Part of the Pathmind addon API — API-06 async execution contract.
 */
@FunctionalInterface
public interface AddonNodeExecutor {

    /**
     * Executes this node asynchronously.
     *
     * @param ctx the runtime context for this node (script text, addon type id)
     * @return a future that completes with the execution result; must never return null
     */
    CompletableFuture<NodeResult> execute(AddonNodeContext ctx);
}
