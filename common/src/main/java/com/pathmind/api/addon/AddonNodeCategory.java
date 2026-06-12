package com.pathmind.api.addon;

/**
 * Runtime node category declared by an addon. Not an enum — instances are created
 * by the addon and registered through {@link NodeTypeRegistrar}.
 *
 * <p>Addon categories cannot be added to Pathmind's built-in {@code NodeCategory} enum
 * at runtime. This POJO is the correct model for runtime-declared categories (D-05,
 * Pitfall 3 from RESEARCH.md).
 *
 * <p>Example:
 * <pre>{@code
 * new AddonNodeCategory("pathmind_lua.scripting", "Scripting", 0xFF7986CB, "✦")
 * }</pre>
 */
public final class AddonNodeCategory {

    private final String id;
    private final String displayName;
    private final int color;
    private final String icon;

    /**
     * Constructs an addon node category.
     *
     * @param id          unique category identifier (e.g. {@code "pathmind_lua.scripting"})
     * @param displayName human-readable category name shown in the sidebar
     * @param color       ARGB color used for the category header (e.g. {@code 0xFF7986CB})
     * @param icon        single character icon displayed next to the category name
     */
    public AddonNodeCategory(String id, String displayName, int color, String icon) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
    }

    /**
     * Returns the unique identifier for this category.
     *
     * @return category id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable display name for this category.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the ARGB color for this category.
     *
     * @return color as ARGB integer
     */
    public int getColor() {
        return color;
    }

    /**
     * Returns the single character icon for this category.
     *
     * @return icon string
     */
    public String getIcon() {
        return icon;
    }
}
