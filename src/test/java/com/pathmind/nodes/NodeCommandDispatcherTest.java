package com.pathmind.nodes;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCommandDispatcherTest {
    @Test
    void addonExecutorRoutesUnsupportedAddonPlaceholderByTypeId() {
        Identifier id = Identifier.of("executoraddon", "scan_area");
        PathmindNodes.register(id, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("executoraddon.node.scan_area")
            .descriptionKey("executoraddon.node.scan_area.desc")
            .color(0xFF336699));
        AtomicBoolean executed = new AtomicBoolean(false);
        PathmindNodes.registerExecutor(id, (node, future) -> {
            executed.set(true);
            assertEquals(id.toString(), node.getTypeId());
            future.complete(null);
        });
        Node placeholder = Node.createUnsupportedAddonPlaceholder(id, 12, 34);
        CompletableFuture<Void> future = new CompletableFuture<>();

        NodeCommandDispatcher.execute(placeholder, future);

        assertTrue(executed.get());
        assertTrue(future.isDone());
        assertDoesNotThrow(future::join);
    }

    @Test
    void duplicateExecutorRegistrationThrows() {
        Identifier id = Identifier.of("executoraddon", "duplicate_executor");
        PathmindNodes.register(id, builder -> builder
            .category(NodeCategory.CUSTOM)
            .translationKey("executoraddon.node.duplicate_executor")
            .descriptionKey("executoraddon.node.duplicate_executor.desc")
            .color(0xFF224466));
        PathmindNodes.registerExecutor(id, (node, future) -> future.complete(null));

        assertThrows(IllegalArgumentException.class,
            () -> PathmindNodes.registerExecutor(id, (node, future) -> future.complete(null)));
    }

    @Test
    void builtInStickyNoteStillCompletesThroughDispatcherRouting() {
        Node node = new Node(NodeType.STICKY_NOTE, 0, 0);
        CompletableFuture<Void> future = new CompletableFuture<>();

        NodeCommandDispatcher.execute(node, future);

        assertTrue(future.isDone());
        assertDoesNotThrow(future::join);
    }
}
