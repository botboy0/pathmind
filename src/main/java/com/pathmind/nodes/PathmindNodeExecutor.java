package com.pathmind.nodes;

import java.util.concurrent.CompletableFuture;

/**
 * Executes a Pathmind node command and completes the supplied future when finished.
 */
@FunctionalInterface
public interface PathmindNodeExecutor {
    void execute(Node node, CompletableFuture<Void> future);
}
