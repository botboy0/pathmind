package com.pathmind.nodes;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Public registry facade for node types supplied by Pathmind or addon mods.
 */
public final class PathmindNodes {
    public static final String ENTRYPOINT_KEY = "pathmind_nodes";
    private static final ConcurrentMap<Identifier, PathmindNodeDefinition> DEFINITIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Identifier, PathmindNodeExecutor> EXECUTORS = new ConcurrentHashMap<>();

    static {
        registerBuiltIns();
    }

    private PathmindNodes() {
    }

    public static PathmindNodeDefinition register(Identifier id, Consumer<PathmindNodeDefinition.Builder> configurer) {
        Objects.requireNonNull(configurer, "configurer");
        PathmindNodeDefinition.Builder builder = PathmindNodeDefinition.builder(normalize(id));
        configurer.accept(builder);
        return register(builder.build());
    }

    public static int loadEntrypoints(Collection<? extends PathmindNodePlugin> plugins) {
        if (plugins == null || plugins.isEmpty()) {
            return 0;
        }
        PathmindNodeRegistrar registrar = PathmindNodes::register;
        int loaded = 0;
        for (PathmindNodePlugin plugin : plugins) {
            if (plugin == null) {
                continue;
            }
            plugin.registerNodes(registrar);
            loaded++;
        }
        return loaded;
    }

    public static Optional<PathmindNodeDefinition> get(Identifier id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static void registerExecutor(Identifier id, PathmindNodeExecutor executor) {
        Identifier normalizedId = normalize(id);
        Objects.requireNonNull(executor, "executor");
        PathmindNodeExecutor previous = EXECUTORS.putIfAbsent(normalizedId, executor);
        if (previous != null) {
            throw new IllegalArgumentException("Node executor is already registered: " + normalizedId);
        }
    }

    static Optional<PathmindNodeExecutor> getExecutor(Identifier id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(EXECUTORS.get(normalize(id)));
    }

    public static Collection<PathmindNodeDefinition> all() {
        List<PathmindNodeDefinition> definitions = new ArrayList<>(DEFINITIONS.values());
        definitions.sort((left, right) -> left.id().compareTo(right.id()));
        return List.copyOf(definitions);
    }

    private static void registerBuiltIns() {
        for (NodeType type : NodeType.values()) {
            Identifier id = Identifier.of(type.getPersistenceId());
            PathmindNodeDefinition.Builder builder = PathmindNodeDefinition.builder(id)
                .builtInType(type)
                .category(type.getCategory())
                .translationKey(type.getTranslationKey())
                .descriptionKey(type.getDescriptionKey())
                .color(type.getColor())
                .hasParameters(type.hasParameters())
                .draggableFromSidebar(type.isDraggableFromSidebar())
                .requiresBaritone(type.requiresBaritone())
                .requiresUiUtils(type.requiresUiUtils());
            addBuiltInTraitMetadata(builder, type);
            addBuiltInModeMetadata(builder, type);
            register(builder.build());
            registerExecutor(id, NodeCommandDispatcher::executeBuiltIn);
        }
    }

    private static void addBuiltInTraitMetadata(PathmindNodeDefinition.Builder builder, NodeType type) {
        for (NodeValueTrait trait : NodeTraitRegistry.getProvidedTraits(type)) {
            builder.providesTraits(trait);
        }
        int slotCount = NodeTraitRegistry.getParameterSlotCount(type);
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            builder.parameterSlot(
                NodeTraitRegistry.getParameterSlotLabel(type, slotIndex),
                NodeTraitRegistry.isParameterSlotAlwaysRequired(type, slotIndex),
                NodeTraitRegistry.getAcceptedTraits(type, slotIndex).toArray(NodeValueTrait[]::new));
        }
    }

    private static void addBuiltInModeMetadata(PathmindNodeDefinition.Builder builder, NodeType type) {
        NodeMode defaultMode = NodeMode.getDefaultModeForNodeType(type);
        for (NodeMode mode : NodeMode.getModesForNodeType(type)) {
            builder.modeOption(
                Identifier.of("pathmind", mode.name().toLowerCase(Locale.ROOT)),
                mode,
                mode == defaultMode);
        }
    }

    private static PathmindNodeDefinition register(PathmindNodeDefinition definition) {
        PathmindNodeDefinition previous = DEFINITIONS.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalArgumentException("Node type is already registered: " + definition.id());
        }
        return definition;
    }

    private static Identifier normalize(Identifier id) {
        return Objects.requireNonNull(id, "id");
    }
}
