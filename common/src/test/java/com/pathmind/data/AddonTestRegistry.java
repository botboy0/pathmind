package com.pathmind.data;

import com.pathmind.api.addon.AddonNodeCategory;
import com.pathmind.api.addon.AddonNodeContext;
import com.pathmind.api.addon.AddonNodeDefinition;
import com.pathmind.api.addon.AddonNodeSerializer;
import com.pathmind.api.addon.NodeResult;
import com.pathmind.api.addon.NodeTypeRegistrar;
import com.pathmind.nodes.NodeTypeRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared test registry helper for all addon node test classes.
 *
 * <p>Consolidates ALL install-once {@link NodeTypeRegistry#INSTANCE} registrations into a
 * single call so the full test suite is order-independent. Previously each test class
 * installed its own inline serializer guarded by the same {@code hasType} check.
 * Because install is once-per-JVM, whichever class ran first would exhaust the install slot
 * and every later class's install attempt would be silently swallowed (the
 * {@code IllegalStateException} was caught). This caused intermittent failures when
 * {@link AddonNodeReloadRegressionTest} ran after a non-null-seeding class had already
 * consumed the slot (post-merge gate failure, plan 01-11 deviation).
 *
 * <p><b>Addon IDs registered:</b>
 * <ul>
 *   <li>{@link #SCRIPT_ADDON_ID} ({@code test_mod:script}) — used by
 *       AddonNodePersistenceTest, AddonNodeConversionRoundTripTest, and
 *       AddonNodeReloadRegressionTest</li>
 *   <li>{@link #ALIASING_ADDON_ID} ({@code aliasing_test_mod:script}) — used by
 *       AddonNodeAliasingTest</li>
 * </ul>
 * All four classes call {@link #ensureInstalled()} from their {@code @BeforeAll}.
 * Only the first call installs; subsequent calls are no-ops (the {@code hasType} guard
 * on the primary ID short-circuits before reaching the install step).
 *
 * <p><b>Intentionally NOT registered here</b> (must remain absent from the registry):
 * <ul>
 *   <li>{@code test_mod:unknown_addon_type} — AddonNodeConversionRoundTripTest's
 *       UNREGISTERED_ADDON_ID, exercises the missing-addon placeholder branch (D-09)</li>
 *   <li>{@code aliasing_test_mod:missing_addon} — AddonNodeAliasingTest's
 *       UNREGISTERED_ADDON_ID, same purpose</li>
 * </ul>
 *
 * <p>The shared serializer for {@link #SCRIPT_ADDON_ID} seeds {@link #DEFAULT_SCRIPT} when
 * {@code fields} is {@code null} (required by NEW-CR-02 /
 * {@link AddonNodeReloadRegressionTest}). This does NOT affect
 * {@link AddonNodePersistenceTest#deserialize_toleratesNullFields}: that test invokes the
 * class-local {@code TEST_SERIALIZER} field directly (not via the registry).
 *
 * <p>{@link com.pathmind.nodes.AddonNodeCreationTest} uses a distinct addon id
 * ({@code test_mod:creation_test_type}) and injects it via reflection after install —
 * it is unaffected by this consolidation.
 */
final class AddonTestRegistry {

    /** The canonical addon type id used by the three {@code test_mod:*} test classes. */
    public static final String SCRIPT_ADDON_ID = "test_mod:script";

    /**
     * The alias addon type id used by {@link AddonNodeAliasingTest}.
     * Registered alongside {@link #SCRIPT_ADDON_ID} in the single shared install.
     */
    public static final String ALIASING_ADDON_ID = "aliasing_test_mod:script";

    /**
     * The default script text seeded when the shared serializer for
     * {@link #SCRIPT_ADDON_ID} receives a {@code null} fields map (NEW-CR-02).
     * Matches the constant previously private to {@link AddonNodeReloadRegressionTest}.
     */
    public static final String DEFAULT_SCRIPT = "-- default script (NEW-CR-02 test)";

    /**
     * The default script text seeded when the shared serializer for
     * {@link #ALIASING_ADDON_ID} receives a {@code null} fields map.
     */
    public static final String ALIASING_DEFAULT_SCRIPT = "-- default aliasing script";

    /**
     * The single shared serializer for {@link #SCRIPT_ADDON_ID}.
     *
     * <p>Null-fields contract (NEW-CR-02): when {@code fields} is {@code null},
     * seeds {@link #DEFAULT_SCRIPT} via {@code ctx.setScriptText}.
     * Non-null contract (round-trip / persistence): reads {@code _schema_version}
     * (GSON-safe {@code Number} cast) and {@code script} text.
     */
    public static final AddonNodeSerializer SHARED_SERIALIZER = new AddonNodeSerializer() {
        @Override
        public Map<String, Object> serialize(AddonNodeContext ctx) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("_schema_version", 1);
            map.put("script", ctx.getScriptText() != null ? ctx.getScriptText() : "");
            return map;
        }

        @Override
        public void deserialize(AddonNodeContext ctx, Map<String, Object> fields) {
            if (fields == null) {
                // Null-fields path: seed the default so restoreAddonFieldsToNode writes it
                // (NEW-CR-02 requirement).
                ctx.setScriptText(DEFAULT_SCRIPT);
                return;
            }
            // GSON Double-erasure handling (Pitfall 4): use Number.intValue()
            Object versionObj = fields.get("_schema_version");
            if (versionObj instanceof Number) {
                int version = ((Number) versionObj).intValue();
                // Version-aware migration would go here; assert minimum for test validity
                assertTrue(version >= 1, "_schema_version must be >= 1");
            }
            Object scriptObj = fields.get("script");
            if (scriptObj != null) {
                ctx.setScriptText(scriptObj.toString());
            }
        }
    };

    /**
     * The single shared serializer for {@link #ALIASING_ADDON_ID}.
     * Seeds {@link #ALIASING_DEFAULT_SCRIPT} on null fields.
     */
    public static final AddonNodeSerializer ALIASING_SERIALIZER = new AddonNodeSerializer() {
        @Override
        public Map<String, Object> serialize(AddonNodeContext ctx) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("_schema_version", 1);
            map.put("script", ctx.getScriptText() != null ? ctx.getScriptText() : "");
            return map;
        }

        @Override
        public void deserialize(AddonNodeContext ctx, Map<String, Object> fields) {
            if (fields == null) {
                ctx.setScriptText(ALIASING_DEFAULT_SCRIPT);
                return;
            }
            Object scriptObj = fields.get("script");
            if (scriptObj != null) {
                ctx.setScriptText(scriptObj.toString());
            }
        }
    };

    private AddonTestRegistry() {
        // utility class
    }

    /**
     * Idempotently ensures ALL test addon types are registered in
     * {@link NodeTypeRegistry#INSTANCE} via a single install.
     *
     * <p>Registered types:
     * <ul>
     *   <li>{@link #SCRIPT_ADDON_ID} with {@link #SHARED_SERIALIZER} (null-seeding)</li>
     *   <li>{@link #ALIASING_ADDON_ID} with {@link #ALIASING_SERIALIZER}</li>
     * </ul>
     *
     * <p>All addon test classes that install via {@link NodeTypeRegistry#INSTANCE} must call
     * this method from their {@code @BeforeAll} instead of building their own inline
     * registrar. After the first call installs, subsequent calls from other test classes
     * are no-ops (the {@code hasType} guard on {@link #SCRIPT_ADDON_ID} short-circuits).
     *
     * <p>The {@link IllegalStateException} catch is retained as a safety net for any
     * hypothetical scenario where two JVM-level threads race past the {@code hasType} check
     * simultaneously (not currently possible with sequential JUnit 5 execution, but
     * harmless to keep).
     */
    public static void ensureInstalled() {
        if (!NodeTypeRegistry.INSTANCE.hasType(SCRIPT_ADDON_ID)) {
            try {
                NodeTypeRegistrar registrar = new NodeTypeRegistrar();

                // --- test_mod:script ---
                AddonNodeCategory scriptCategory = new AddonNodeCategory(
                    "test_mod.scripting", "Scripting", 0xFF112233, "S");
                AddonNodeDefinition scriptDef = AddonNodeDefinition.builder(SCRIPT_ADDON_ID)
                    .displayName("Test Script")
                    .category(scriptCategory)
                    .build();
                registrar.register(scriptDef,
                    ctx -> CompletableFuture.completedFuture(NodeResult.SUCCESS),
                    SHARED_SERIALIZER);

                // --- aliasing_test_mod:script ---
                AddonNodeCategory aliasingCategory = new AddonNodeCategory(
                    "aliasing_test_mod.scripting", "Scripting", 0xFF334455, "A");
                AddonNodeDefinition aliasingDef = AddonNodeDefinition.builder(ALIASING_ADDON_ID)
                    .displayName("Aliasing Test Script")
                    .category(aliasingCategory)
                    .build();
                registrar.register(aliasingDef,
                    ctx -> CompletableFuture.completedFuture(NodeResult.SUCCESS),
                    ALIASING_SERIALIZER);

                registrar.seal();
                NodeTypeRegistry.INSTANCE.install(registrar);
            } catch (IllegalStateException e) {
                // Already installed by a concurrent call — acceptable
            }
        }
    }
}
