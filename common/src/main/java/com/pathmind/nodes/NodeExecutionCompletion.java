package com.pathmind.nodes;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;

final class NodeExecutionCompletion {
    static void complete(CompletableFuture<Void> future) {
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
    }

    static void fail(Node owner, MinecraftClient client, CompletableFuture<Void> future, String message) {
        fail(owner, client, future, message, null);
    }

    /**
     * Like {@link #fail(Node, MinecraftClient, CompletableFuture, String)}, but records a
     * structured {@link com.pathmind.execution.FailureDetail} alongside the message for
     * the addon envelope layer. Graph behavior is identical (notification + normal
     * completion); a null detail behaves exactly like the plain overload.
     */
    static void fail(Node owner, MinecraftClient client, CompletableFuture<Void> future, String message,
                     com.pathmind.execution.FailureDetail detail) {
        if (owner != null && client != null && message != null && !message.isEmpty()) {
            com.pathmind.execution.ExecutionManager.getInstance().recordNodeFailure(message, detail);
            owner.sendNodeErrorMessage(client, message);
        }
        complete(future);
    }

    static void failWithCurrentClient(Node owner, CompletableFuture<Void> future, String message) {
        fail(owner, MinecraftClient.getInstance(), future, message);
    }

    static void completeExceptionally(CompletableFuture<Void> future, Throwable throwable) {
        if (future != null && !future.isDone()) {
            future.completeExceptionally(throwable);
        }
    }

    private NodeExecutionCompletion() {
    }
}
