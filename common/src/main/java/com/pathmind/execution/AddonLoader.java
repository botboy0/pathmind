package com.pathmind.execution;

import com.pathmind.PathmindCommon;
import com.pathmind.api.PathmindApiVersion;
import com.pathmind.api.addon.NodeTypeRegistrar;
import com.pathmind.api.addon.PathmindAddonEntrypoint;
import com.pathmind.nodes.NodeTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers {@code "pathmind"} Fabric entrypoints at mod initialization and loads
 * addon node type registrations with per-addon failure isolation.
 *
 * <p><strong>D-11 runtime API-version check:</strong> Before invoking
 * {@link PathmindAddonEntrypoint#registerNodes}, AddonLoader reads the addon's declared
 * {@code pathmind} dependency range from its {@code fabric.mod.json} metadata and
 * compares it against {@link PathmindApiVersion#MIN_COMPATIBLE}. An addon whose declared
 * range excludes {@code MIN_COMPATIBLE} is disabled via {@link #markFailed} and skipped
 * (D-08 failure UX). This complements the Fabric loader's hard-mismatch block.
 *
 * <p><strong>Failure isolation (API-03/D-08):</strong> Any exception thrown by an addon's
 * {@code registerNodes}, or by dependency-introspection itself, disables only that addon.
 * The offending addon id is logged and recorded via {@link #markFailed}. All other addons
 * continue loading normally.
 *
 * <p><strong>Sealing (API-04/T-01-05):</strong> The registrar is sealed (via
 * {@link NodeTypeRegistry#install}) after all entrypoints have run. No addon can register
 * new types after {@code discoverAndLoad} returns.
 *
 * <p><strong>Standalone path (API-09):</strong> When no addon is installed,
 * {@code getEntrypointContainers} returns an empty list. The empty registrar is installed
 * cleanly — Pathmind functions normally with no addon present.
 */
public final class AddonLoader {

    private static final Logger LOGGER = PathmindCommon.LOGGER;
    private static final Map<String, Throwable> failedAddons = new LinkedHashMap<>();

    /**
     * Discovers all addons declaring a {@code "pathmind"} entrypoint, runs the D-11
     * runtime API-version check, invokes {@code registerNodes} for compatible addons,
     * and installs the sealed registrar into {@link NodeTypeRegistry#INSTANCE}.
     *
     * <p>Called from {@code PathmindMod.onInitialize} after all Pathmind internal state
     * is ready (Pattern 5: deferred-registration guard).
     */
    public static void discoverAndLoad() {
        NodeTypeRegistrar registrar = new NodeTypeRegistrar();

        List<EntrypointContainer<PathmindAddonEntrypoint>> containers =
            FabricLoader.getInstance()
                .getEntrypointContainers("pathmind", PathmindAddonEntrypoint.class);

        for (EntrypointContainer<PathmindAddonEntrypoint> container : containers) {
            String addonId = "unknown";
            try {
                addonId = container.getProvider().getMetadata().getId();

                // D-11 runtime API-version check: read the addon's declared pathmind dependency
                // and ensure it accepts our MIN_COMPATIBLE version before calling registerNodes.
                if (!checkApiCompatibility(addonId, container.getProvider().getMetadata().getDependencies())) {
                    // Disabled via markFailed — routes through D-08 failure UX in Plan 02
                    continue;
                }

                container.getEntrypoint().registerNodes(registrar);
                LOGGER.info("[Pathmind] Addon '{}' registered nodes successfully", addonId);

            } catch (Throwable t) {
                LOGGER.error("[Pathmind] Addon '{}' failed node registration — addon disabled",
                    addonId, t);
                markFailed(addonId, t);
            }
        }

        // Seal and install — NodeTypeRegistry.install also calls registrar.seal() (defense in depth)
        NodeTypeRegistry.INSTANCE.install(registrar);
    }

    /**
     * Performs the D-11 runtime API-version check for a single addon.
     *
     * <p>Reads the addon's declared {@code pathmind} dependency from its metadata and checks
     * whether the constraint accepts {@link PathmindApiVersion#MIN_COMPATIBLE}. If the addon
     * declares no {@code pathmind} dependency, the check is a soft-pass (returns true) and a
     * debug log is emitted — the Fabric loader's hard-mismatch block handles dependency-absent
     * cases at load time; this runtime layer specifically targets declared-but-incompatible ranges.
     *
     * @param addonId     the addon's mod id (for logging)
     * @param dependencies the addon's declared dependency collection from its metadata
     * @return true if the addon is API-compatible, false if it should be disabled
     */
    private static boolean checkApiCompatibility(String addonId,
                                                  Collection<ModDependency> dependencies) {
        ModDependency pathmindDep = null;
        for (ModDependency dep : dependencies) {
            if ("pathmind".equals(dep.getModId())) {
                pathmindDep = dep;
                break;
            }
        }

        if (pathmindDep == null) {
            // Soft-pass: no pathmind dependency declared. The Fabric loader's hard block
            // handles missing-dependency enforcement; this runtime check is for version ranges.
            LOGGER.debug("[Pathmind] Addon '{}' declares no pathmind dependency — allowing (soft-pass)", addonId);
            return true;
        }

        try {
            SemanticVersion minCompatible = SemanticVersion.parse(PathmindApiVersion.MIN_COMPATIBLE);
            boolean compatible = isApiCompatible(pathmindDep, minCompatible);
            if (!compatible) {
                String declaredRange = pathmindDep.getVersionRequirements().toString();
                LOGGER.error(
                    "[Pathmind] Addon '{}' targets an incompatible Pathmind API version "
                    + "(requires {}, this build provides {}) — addon disabled",
                    addonId, declaredRange, PathmindApiVersion.VERSION);
                markFailed(addonId, new IllegalStateException(
                    "Incompatible Pathmind API version: addon requires " + declaredRange
                    + ", this build provides " + PathmindApiVersion.VERSION));
            }
            return compatible;
        } catch (VersionParsingException e) {
            // If we cannot parse MIN_COMPATIBLE as a semver, log and allow (don't block on our own bug)
            LOGGER.error("[Pathmind] Failed to parse PathmindApiVersion.MIN_COMPATIBLE '{}' — "
                + "skipping D-11 check for addon '{}'", PathmindApiVersion.MIN_COMPATIBLE, addonId, e);
            return true;
        } catch (Throwable t) {
            // Any exception in dependency-introspection disables only this addon (API-03)
            LOGGER.error("[Pathmind] Addon '{}' failed API version check — addon disabled", addonId, t);
            markFailed(addonId, t);
            return false;
        }
    }

    /**
     * Tests whether the given dependency's version constraint accepts the given version.
     * Extracted as a package-private helper for unit testing without FabricLoader containers.
     *
     * @param dep     the addon's declared pathmind dependency
     * @param version the version to test (typically {@code PathmindApiVersion.MIN_COMPATIBLE})
     * @return true if the dependency accepts the version
     */
    static boolean isApiCompatible(ModDependency dep, SemanticVersion version) {
        return dep.matches(version);
    }

    /**
     * Records an addon as failed with the given cause. Recorded failures are surfaced
     * via the D-08 failure UX when the editor opens (Plan 02).
     *
     * @param addonId the failed addon's mod id
     * @param t       the cause
     */
    public static void markFailed(String addonId, Throwable t) {
        failedAddons.put(addonId, t);
    }

    /**
     * Returns the failure cause for the given addon id, or {@code null} if not failed.
     *
     * @param addonId the addon's mod id
     * @return the failure cause, or null
     */
    public static Throwable getFailure(String addonId) {
        return failedAddons.get(addonId);
    }

    /**
     * Returns an unmodifiable view of all failed addons with their causes.
     * Used by the D-08 failure UX to surface errors when the editor opens (Plan 02).
     *
     * @return unmodifiable map of addonId to failure cause
     */
    public static Map<String, Throwable> getFailedAddons() {
        return Collections.unmodifiableMap(failedAddons);
    }

    private AddonLoader() {
    }
}
