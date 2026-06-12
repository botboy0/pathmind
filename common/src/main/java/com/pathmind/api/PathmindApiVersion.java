package com.pathmind.api;

/**
 * Semver constants for the Pathmind addon API surface, independent of the mod version.
 *
 * <p>The API version starts at {@code "0.1.0"}; version {@code "1.0.0"} marks the
 * stable API milestone. Addons declare a dependency on this version range in their
 * {@code fabric.mod.json}:
 * <pre>{@code
 * "depends": {
 *   "pathmind": ">=0.1.0"
 * }
 * }</pre>
 *
 * <p>{@link #MIN_COMPATIBLE} is the oldest API version an addon may declare and still
 * be loaded. {@code AddonLoader}'s D-11 runtime check compares an addon's declared
 * {@code pathmind} dependency range against this constant before calling
 * {@code registerNodes}.
 *
 * <p>Part of the Pathmind addon API — D-10 independent semver, D-11 runtime check.
 */
public final class PathmindApiVersion {

    /**
     * Current API version string. Starts at {@code "0.1.0"}; {@code "1.0.0"} marks
     * the stable API milestone.
     */
    public static final String VERSION = "0.1.0";

    /**
     * Minimum API version an addon must declare to be compatible with this build.
     * Addons declaring a lower minimum are disabled via the standard failure UX (D-08)
     * before {@code registerNodes} is called.
     */
    public static final String MIN_COMPATIBLE = "0.1.0";

    private PathmindApiVersion() {
    }
}
