package com.pathmind.nodes;

import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Optional;

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

    private PathmindNodeDefinition(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.builtInType = builder.builtInType;
        this.category = Objects.requireNonNull(builder.category, "category");
        this.translationKey = Objects.requireNonNull(builder.translationKey, "translationKey");
        this.descriptionKey = Objects.requireNonNull(builder.descriptionKey, "descriptionKey");
        this.color = builder.color;
        this.hasParameters = builder.hasParameters;
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

    static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private NodeType builtInType;
        private NodeCategory category;
        private String translationKey;
        private String descriptionKey;
        private int color;
        private boolean hasParameters;

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

        PathmindNodeDefinition build() {
            return new PathmindNodeDefinition(this);
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value;
        }
    }
}
