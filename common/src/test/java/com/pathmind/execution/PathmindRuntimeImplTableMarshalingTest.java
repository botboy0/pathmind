package com.pathmind.execution;

import com.pathmind.nodes.AddonListEntryCodec;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PathmindRuntimeImpl table marshaling (v2) — coordinate maps and
 * runtime lists crossing the addon API as plain {@code Map}/{@code List} values.
 *
 * <p>In a headless JVM there are no active chains; the ForAnyActiveChain accessors
 * fall through to the global variable/list stores, so round-trips are valid.
 */
class PathmindRuntimeImplTableMarshalingTest {

    private ExecutionManager manager;
    private PathmindRuntimeImpl runtime;

    @BeforeEach
    void setUp() throws Exception {
        manager = ExecutionManager.getInstance();
        manager.requestStopAll();
        clearGlobalStore("globalRuntimeVariables");
        clearGlobalStore("globalRuntimeLists");
        runtime = new PathmindRuntimeImpl(manager);
    }

    @AfterEach
    void tearDown() throws Exception {
        manager.requestStopAll();
        clearGlobalStore("globalRuntimeVariables");
        clearGlobalStore("globalRuntimeLists");
    }

    /** Clears a global runtime store via reflection so tests are isolated. */
    private void clearGlobalStore(String fieldName) throws Exception {
        Field field = ExecutionManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((Map<?, ?>) field.get(manager)).clear();
    }

    // -------------------------------------------------------------------------
    // Coordinate maps ↔ PARAM_COORDINATE variables
    // -------------------------------------------------------------------------

    @Test
    void coordinateMapRoundTripsThroughParamCoordinate() {
        runtime.setVariable("home", Map.of("x", 10.0, "y", 64.0, "z", -5.0));

        ExecutionManager.RuntimeVariable rv = manager.getRuntimeVariableFromAnyActiveChain("home");
        assertNotNull(rv, "coordinate map must be stored as a runtime variable");
        assertEquals(NodeType.PARAM_COORDINATE, rv.getType());
        assertEquals("10", rv.getValues().get("X"), "integral coordinates must be stored without .0");
        assertEquals("-5", rv.getValues().get("Z"));

        Object read = runtime.getVariable("home");
        assertInstanceOf(Map.class, read, "coordinate variable must read back as a Map");
        Map<?, ?> coord = (Map<?, ?>) read;
        assertEquals(10.0, coord.get("x"));
        assertEquals(64.0, coord.get("y"));
        assertEquals(-5.0, coord.get("z"));
    }

    @Test
    void coordinateMapAcceptsUppercaseKeysAndIntegers() {
        runtime.setVariable("spot", Map.of("X", 1, "Y", 2, "Z", 3));
        Map<?, ?> coord = (Map<?, ?>) runtime.getVariable("spot");
        assertNotNull(coord);
        assertEquals(1.0, coord.get("x"));
    }

    @Test
    void mapWithUnknownKeyIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> runtime.setVariable("bad", Map.of("x", 1.0, "y", 2.0, "z", 3.0, "w", 4.0)));
        assertTrue(ex.getMessage().contains("w"), "error must name the offending key, got: " + ex.getMessage());
    }

    @Test
    void mapMissingComponentIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> runtime.setVariable("bad", Map.of("x", 1.0, "y", 2.0)));
    }

    @Test
    void mapWithNonNumericValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> runtime.setVariable("bad", Map.of("x", 1.0, "y", 2.0, "z", "three")));
    }

    // -------------------------------------------------------------------------
    // PARAM_DISTANCE read support
    // -------------------------------------------------------------------------

    @Test
    void distanceVariableReadsAsDouble() {
        Map<String, String> vals = new HashMap<>();
        vals.put("Distance", "12.5");
        manager.setRuntimeVariableForAnyActiveChain("dist",
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_DISTANCE, vals));

        assertEquals(12.5, runtime.getVariable("dist"));
    }

    // -------------------------------------------------------------------------
    // Lists ↔ runtime lists
    // -------------------------------------------------------------------------

    @Test
    void numberListRoundTripsThroughRuntimeList() {
        runtime.setVariable("nums", List.of(1.0, 2.5, 3.0));

        ExecutionManager.RuntimeList list = manager.getRuntimeListFromAnyActiveChain("nums");
        assertNotNull(list, "list value must land in the runtime-list namespace");
        assertEquals(NodeType.PARAM_AMOUNT, list.getElementType());
        assertEquals(3, list.size());
        assertTrue(AddonListEntryCodec.isSerialized(list.getEntry(0)),
            "list entries must use the serialized pm_list: format");

        Object read = runtime.getVariable("nums");
        assertEquals(List.of(1.0, 2.5, 3.0), read);
    }

    @Test
    void stringListRoundTrips() {
        runtime.setVariable("words", List.of("a", "b"));
        assertEquals(List.of("a", "b"), runtime.getVariable("words"));
        assertEquals(NodeType.PARAM_MESSAGE,
            manager.getRuntimeListFromAnyActiveChain("words").getElementType());
    }

    @Test
    void booleanListRoundTrips() {
        runtime.setVariable("flags", List.of(true, false, true));
        assertEquals(List.of(true, false, true), runtime.getVariable("flags"));
        assertEquals(NodeType.PARAM_BOOLEAN,
            manager.getRuntimeListFromAnyActiveChain("flags").getElementType());
    }

    @Test
    void coordinateListRoundTrips() {
        runtime.setVariable("path", List.of(
            Map.of("x", 1.0, "y", 2.0, "z", 3.0),
            Map.of("x", 4.0, "y", 5.0, "z", 6.0)));

        ExecutionManager.RuntimeList list = manager.getRuntimeListFromAnyActiveChain("path");
        assertNotNull(list);
        assertEquals(NodeType.PARAM_COORDINATE, list.getElementType());

        Object read = runtime.getVariable("path");
        assertInstanceOf(List.class, read);
        List<?> coords = (List<?>) read;
        assertEquals(2, coords.size());
        Map<?, ?> first = (Map<?, ?>) coords.get(0);
        assertEquals(1.0, first.get("x"));
        assertEquals(3.0, first.get("z"));
    }

    @Test
    void nodeSideCoordinateListReadsAsCoordinateMaps() {
        // Simulate what Create List (block scan) stores: serialized X/Y/Z values maps.
        Map<String, String> vals = new HashMap<>();
        vals.put("X", "7");
        vals.put("Y", "70");
        vals.put("Z", "-3");
        vals.put("x", "7");
        vals.put("y", "70");
        vals.put("z", "-3");
        manager.setRuntimeListForAnyActiveChain("ores", new ExecutionManager.RuntimeList(
            NodeType.PARAM_COORDINATE, List.of(AddonListEntryCodec.serialize(vals))));

        List<?> read = (List<?>) runtime.getVariable("ores");
        assertEquals(1, read.size());
        Map<?, ?> coord = (Map<?, ?>) read.get(0);
        assertEquals(7.0, coord.get("x"));
        assertEquals(70.0, coord.get("y"));
        assertEquals(-3.0, coord.get("z"));
    }

    @Test
    void rawListEntriesReadAsStrings() {
        // Entity/player lists store raw UUID strings without the serialized prefix.
        manager.setRuntimeListForAnyActiveChain("mobs", new ExecutionManager.RuntimeList(
            NodeType.PARAM_ENTITY, List.of("123e4567-e89b-12d3-a456-426614174000")));

        assertEquals(List.of("123e4567-e89b-12d3-a456-426614174000"), runtime.getVariable("mobs"));
    }

    @Test
    void emptyListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> runtime.setVariable("empty", List.of()));
    }

    @Test
    void mixedListIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> runtime.setVariable("mixed", List.of(1.0, "two")));
        assertTrue(ex.getMessage().contains("Mixed"), "got: " + ex.getMessage());
    }

    @Test
    void listWithUnsupportedElementIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> runtime.setVariable("bad", List.of(new Object())));
    }

    // -------------------------------------------------------------------------
    // Namespace precedence
    // -------------------------------------------------------------------------

    @Test
    void variableShadowsListOfSameName() {
        runtime.setVariable("thing", List.of(1.0));
        runtime.setVariable("thing2", 5.0);
        // Same name in both namespaces: variable wins.
        runtime.setVariable("dual", List.of(1.0, 2.0));
        runtime.setVariable("dual", 9.0);
        assertEquals(9.0, runtime.getVariable("dual"),
            "the variable namespace must shadow the list namespace");
    }

    @Test
    void getVariableReturnsNullWhenNeitherVariableNorListExists() {
        assertNull(runtime.getVariable("nope"));
    }
}
