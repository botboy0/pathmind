package com.pathmind.api.addon;

/**
 * Runtime context passed to addon node executors and serializers.
 *
 * <p>Contains only what the addon legitimately needs — Pathmind implementation classes
 * ({@code Node}, {@code ExecutionManager}, {@code NodeGraph}) are deliberately excluded
 * from this API surface (RESEARCH.md anti-pattern: "Exposing impl classes in API surface").
 *
 * <p>Phase 1 exposes the addon type id and a single {@code scriptText} field for the
 * Lua scripting addon. Additional fields will be added in future API versions as
 * new addon types require them.
 *
 * <p>Part of the Pathmind addon API — API-02 registration contract.
 */
public final class AddonNodeContext {

    private String addonTypeId;
    private String scriptText;

    /**
     * Constructs an empty addon node context.
     */
    public AddonNodeContext() {
    }

    /**
     * Returns the namespaced type id of the addon node (e.g. {@code "pathmind_lua:script"}).
     *
     * @return addon type id
     */
    public String getAddonTypeId() {
        return addonTypeId;
    }

    /**
     * Sets the addon type id.
     *
     * @param addonTypeId the namespaced type id
     */
    public void setAddonTypeId(String addonTypeId) {
        this.addonTypeId = addonTypeId;
    }

    /**
     * Returns the script text stored on this node. Used by the Lua scripting addon
     * to carry the Lua source code between the serializer and executor.
     *
     * @return script text, or null if not set
     */
    public String getScriptText() {
        return scriptText;
    }

    /**
     * Sets the script text on this node.
     *
     * @param scriptText the script text to set
     */
    public void setScriptText(String scriptText) {
        this.scriptText = scriptText;
    }
}
