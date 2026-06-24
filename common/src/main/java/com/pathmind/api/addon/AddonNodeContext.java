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
    private PathmindRuntime runtime;
    private String nodeId;
    private String lastError;
    private int lastErrorLine;

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

    /**
     * Returns the runtime services for this execution context.
     * Set by Pathmind before invoking the executor.
     *
     * @return the runtime services, or null if not yet wired
     */
    public PathmindRuntime getRuntime() {
        return runtime;
    }

    /**
     * Sets the runtime services for this execution context.
     *
     * @param runtime the runtime services provided by Pathmind
     */
    public void setRuntime(PathmindRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Returns the stable per-node identity string used by addon renderers and input handlers
     * to key their per-node state maps (Phase 3 API extension, open-question #2).
     *
     * <p>Pathmind generates a stable UUID once per ADDON node and persists it in the node's
     * extra-fields blob under key {@code "_node_id"}. Guaranteed non-null when called from
     * a render or input callback; may be {@code null} in executor contexts.
     *
     * @return node identity string, or null if not yet populated
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Sets the stable per-node identity string.
     *
     * @param nodeId the identity string (typically a UUID)
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * Returns the most recent Lua error message for this node, or {@code null} if none.
     *
     * <p>Persisted in the node's extra-fields blob so that the error strip survives
     * preset save and reload.
     *
     * @return last error message, or null if no error
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Sets the most recent error message for this node.
     *
     * @param lastError error message string, or null to clear
     */
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * Returns the 1-based source line number associated with {@link #getLastError()},
     * or {@code 0} if no error or the line is unknown.
     *
     * @return 1-based error line, or 0
     */
    public int getLastErrorLine() {
        return lastErrorLine;
    }

    /**
     * Sets the 1-based source line number for the last error.
     *
     * @param lastErrorLine 1-based line number, or 0 if unknown
     */
    public void setLastErrorLine(int lastErrorLine) {
        this.lastErrorLine = lastErrorLine;
    }
}
