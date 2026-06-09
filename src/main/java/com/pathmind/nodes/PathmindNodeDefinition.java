package com.pathmind.nodes;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Public metadata for a Pathmind node type registered by Pathmind or an addon.
 */
public final class PathmindNodeDefinition {
    private final Identifier id;
    private final NodeType builtInType;
    private final NodeCategory category;
    private final String translationKey;
    private final String descriptionKey;
    private final int color;
    private final boolean hasParameters;
    private final boolean draggableFromSidebar;
    private final boolean requiresBaritone;
    private final boolean requiresUiUtils;
    private final Set<NodeValueTrait> providedTraits;
    private final List<ParameterSlot> parameterSlots;

    private PathmindNodeDefinition(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.builtInType = builder.builtInType;
        this.category = Objects.requireNonNull(builder.category, "category");
        this.translationKey = Objects.requireNonNull(builder.translationKey, "translationKey");
        this.descriptionKey = Objects.requireNonNull(builder.descriptionKey, "descriptionKey");
        this.color = builder.color;
        this.hasParameters = builder.hasParameters;
        this.draggableFromSidebar = builder.draggableFromSidebar;
        this.requiresBaritone = builder.requiresBaritone;
        this.requiresUiUtils = builder.requiresUiUtils;
        this.providedTraits = Set.copyOf(builder.providedTraits);
        this.parameterSlots = List.copyOf(builder.parameterSlots);
    }

    public Identifier id() {
        return id;
    }

    public Optional<NodeType> builtInType() {
        return Optional.ofNullable(builtInType);
    }

    public NodeCategory category() {
        return category;
    }

    public String translationKey() {
        return translationKey;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public int color() {
        return color;
    }

    public boolean hasParameters() {
        return hasParameters;
    }

    public boolean draggableFromSidebar() {
        return draggableFromSidebar;
    }

    public boolean requiresBaritone() {
        return requiresBaritone;
    }

    public boolean requiresUiUtils() {
        return requiresUiUtils;
    }

    public Set<NodeValueTrait> providedTraits() {
        return providedTraits;
    }

    public List<ParameterSlot> parameterSlots() {
        return parameterSlots;
    }

    static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class ParameterSlot {
        private final String label;
        private final boolean required;
        private final Set<NodeValueTrait> acceptedTraits;

        private ParameterSlot(String label, boolean required, Set<NodeValueTrait> acceptedTraits) {
            this.label = requireText(label, "label");
            this.required = required;
            this.acceptedTraits = Set.copyOf(acceptedTraits);
        }

        public String label() {
            return label;
        }

        public boolean required() {
            return required;
        }

        public Set<NodeValueTrait> acceptedTraits() {
            return acceptedTraits;
        }
    }

    public static final class Builder {
        private final Identifier id;
        private NodeType builtInType;
        private NodeCategory category;
        private String translationKey;
        private String descriptionKey;
        private int color;
        private boolean hasParameters;
        private boolean draggableFromSidebar = true;
        private boolean requiresBaritone;
        private boolean requiresUiUtils;
        private final Set<NodeValueTrait> providedTraits = EnumSet.noneOf(NodeValueTrait.class);
        private final List<ParameterSlot> parameterSlots = new ArrayList<>();

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        Builder builtInType(NodeType builtInType) {
            this.builtInType = builtInType;
            return this;
        }

        public Builder category(NodeCategory category) {
            this.category = Objects.requireNonNull(category, "category");
            return this;
        }

        public Builder translationKey(String translationKey) {
            this.translationKey = requireText(translationKey, "translationKey");
            return this;
        }

        public Builder descriptionKey(String descriptionKey) {
            this.descriptionKey = requireText(descriptionKey, "descriptionKey");
            return this;
        }

        public Builder color(int color) {
            this.color = color;
            return this;
        }

        public Builder hasParameters(boolean hasParameters) {
            this.hasParameters = hasParameters;
            return this;
        }

        public Builder draggableFromSidebar(boolean draggableFromSidebar) {
            this.draggableFromSidebar = draggableFromSidebar;
            return this;
        }

        public Builder requiresBaritone(boolean requiresBaritone) {
            this.requiresBaritone = requiresBaritone;
            return this;
        }

        public Builder requiresUiUtils(boolean requiresUiUtils) {
            this.requiresUiUtils = requiresUiUtils;
            return this;
        }

        public Builder providesTraits(NodeValueTrait... traits) {
            Objects.requireNonNull(traits, "traits");
            this.providedTraits.addAll(Arrays.asList(traits));
            return this;
        }

        public Builder parameterSlot(String label, boolean required, NodeValueTrait... acceptedTraits) {
            Objects.requireNonNull(acceptedTraits, "acceptedTraits");
            Set<NodeValueTrait> traits = acceptedTraits.length == 0
                ? EnumSet.noneOf(NodeValueTrait.class)
                : EnumSet.copyOf(Arrays.asList(acceptedTraits));
            this.parameterSlots.add(new ParameterSlot(label, required, traits));
            return this;
        }

        PathmindNodeDefinition build() {
            return new PathmindNodeDefinition(this);
        }

    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
