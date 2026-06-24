package com.pathmind.api.addon;

/**
 * Immutable descriptor for an addon-provided node type. Carries the metadata Pathmind
 * needs to display, categorize, and invoke the node — without exposing any impl classes.
 *
 * <p>Create instances through the builder factory:
 * <pre>{@code
 * AddonNodeDefinition def = AddonNodeDefinition.builder("pathmind_lua:script")
 *     .displayName("Lua Script")
 *     .category(scriptingCategory)
 *     .color(0xFF7986CB)
 *     .provenanceLabel("Pathmind Lua")
 *     .build();
 * }</pre>
 *
 * <p>The {@code id} must pass {@link NodeTypeRegistrar}'s format validation before use.
 *
 * <p>Part of the Pathmind addon API — D-07 provenance badge, D-02 registrar pattern.
 */
public final class AddonNodeDefinition {

    private final String id;
    private final String displayName;
    private final AddonNodeCategory category;
    private final int color;
    private final String provenanceLabel;
    private final AddonNodeBodyRenderer bodyRenderer;
    private final AddonNodeInputHandler inputHandler;
    private final int bodyHeight;

    private AddonNodeDefinition(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.category = b.category;
        this.color = b.color;
        this.provenanceLabel = b.provenanceLabel;
        this.bodyRenderer = b.bodyRenderer;
        this.inputHandler = b.inputHandler;
        this.bodyHeight = b.bodyHeight;
    }

    /**
     * Returns the namespaced type id for this node (e.g. {@code "pathmind_lua:script"}).
     *
     * @return addon type id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable display name shown in the node header and sidebar.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the sidebar category this node belongs to.
     *
     * @return node category
     */
    public AddonNodeCategory getCategory() {
        return category;
    }

    /**
     * Returns the ARGB color used for the node header.
     *
     * @return color as ARGB integer
     */
    public int getColor() {
        return color;
    }

    /**
     * Returns the provenance label shown as a badge on the node (D-07).
     * Empty string if no badge is desired.
     *
     * @return provenance label, never null
     */
    public String getProvenanceLabel() {
        return provenanceLabel;
    }

    /**
     * Returns the optional body renderer for custom node content (API-07).
     * May be null if the node uses the default empty body.
     *
     * @return body renderer, or null
     */
    public AddonNodeBodyRenderer getBodyRenderer() {
        return bodyRenderer;
    }

    /**
     * Returns the optional input handler for interactive addon node bodies (Phase 3 API extension).
     * May be null if the node body is render-only (no interactive input).
     *
     * @return input handler, or null
     */
    public AddonNodeInputHandler getInputHandler() {
        return inputHandler;
    }

    /**
     * Returns the declared body height for this node in pixels, or {@code -1} if no override
     * is specified (Phase 3 API extension). When {@code -1}, NodeDimensionCalculator uses the
     * default TEMPLATE_NODE_HEIGHT (108 px).
     *
     * @return body height in pixels, or -1 for default
     */
    public int getBodyHeight() {
        return bodyHeight;
    }

    /**
     * Creates a new builder for an addon node definition with the given type id.
     *
     * @param id the namespaced type id (e.g. {@code "pathmind_lua:script"})
     * @return a new builder
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Builder for {@link AddonNodeDefinition}.
     */
    public static final class Builder {

        private final String id;
        private String displayName;
        private AddonNodeCategory category;
        private int color = 0xFF888888;
        private String provenanceLabel = "";
        private AddonNodeBodyRenderer bodyRenderer = null;
        private AddonNodeInputHandler inputHandler = null;
        private int bodyHeight = -1; // -1 = let NodeDimensionCalculator use default field logic

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Sets the human-readable display name for this node type.
         *
         * @param v display name
         * @return this builder
         */
        public Builder displayName(String v) {
            this.displayName = v;
            return this;
        }

        /**
         * Sets the sidebar category for this node type.
         *
         * @param v category
         * @return this builder
         */
        public Builder category(AddonNodeCategory v) {
            this.category = v;
            return this;
        }

        /**
         * Sets the ARGB color for the node header.
         *
         * @param v ARGB color
         * @return this builder
         */
        public Builder color(int v) {
            this.color = v;
            return this;
        }

        /**
         * Sets the provenance label shown as a badge on the node (D-07).
         *
         * @param v provenance label
         * @return this builder
         */
        public Builder provenanceLabel(String v) {
            this.provenanceLabel = v;
            return this;
        }

        /**
         * Sets a custom body renderer for this node type (API-07).
         *
         * @param v body renderer, or null for the default empty body
         * @return this builder
         */
        public Builder bodyRenderer(AddonNodeBodyRenderer v) {
            this.bodyRenderer = v;
            return this;
        }

        /**
         * Sets the input handler for interactive addon node bodies (Phase 3 API extension).
         *
         * @param v input handler, or null for render-only nodes
         * @return this builder
         */
        public Builder inputHandler(AddonNodeInputHandler v) {
            this.inputHandler = v;
            return this;
        }

        /**
         * Sets the fixed body height for this node type in pixels (Phase 3 API extension).
         *
         * <p>Use {@code -1} (the default) to let NodeDimensionCalculator determine height
         * using its normal field-based logic. Positive values override the dimension.
         *
         * @param px body height in pixels; use -1 for the default (108 px)
         * @return this builder
         */
        public Builder bodyHeight(int px) {
            this.bodyHeight = px;
            return this;
        }

        /**
         * Builds the {@link AddonNodeDefinition}.
         *
         * @return the built definition
         * @throws NullPointerException if id, displayName, or category is missing
         */
        public AddonNodeDefinition build() {
            if (id == null || id.isBlank()) {
                throw new NullPointerException("id is required");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new NullPointerException("displayName is required");
            }
            if (category == null) {
                throw new NullPointerException("category is required");
            }
            return new AddonNodeDefinition(this);
        }
    }
}
