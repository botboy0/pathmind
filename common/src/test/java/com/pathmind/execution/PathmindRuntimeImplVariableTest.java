package com.pathmind.execution;

import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PathmindRuntimeImpl variable marshaling (Plan 02).
 *
 * <p>Covers the six behavior cases from 02-02-PLAN.md:
 * <ul>
 *   <li>Double round-trip through PARAM_AMOUNT / "Amount"</li>
 *   <li>Boolean round-trip through PARAM_BOOLEAN / "Toggle"</li>
 *   <li>String round-trip through PARAM_MESSAGE / "Text"</li>
 *   <li>getVariable on absent name returns null</li>
 *   <li>setVariable with unsupported type throws IllegalArgumentException</li>
 *   <li>Variable set via PathmindRuntimeImpl is readable via
 *       ExecutionManager.getRuntimeVariableFromAnyActiveChain (cross-node sharing)</li>
 * </ul>
 *
 * <p>In a headless JVM there are no active chains; setRuntimeVariableForAnyActiveChain
 * falls through to globalRuntimeVariables, and getRuntimeVariableFromAnyActiveChain
 * reads from the same map — the round-trip is valid.
 */
class PathmindRuntimeImplVariableTest {

    private ExecutionManager manager;
    private PathmindRuntimeImpl runtime;

    @BeforeEach
    void setUp() throws Exception {
        manager = ExecutionManager.getInstance();
        // Stop any lingering execution state and clear runtime variables
        manager.requestStopAll();
        clearGlobalRuntimeVariables();
    }

    @AfterEach
    void tearDown() throws Exception {
        manager.requestStopAll();
        clearGlobalRuntimeVariables();
    }

    /** Clears globalRuntimeVariables via reflection so tests are isolated. */
    private void clearGlobalRuntimeVariables() throws Exception {
        Field field = ExecutionManager.class.getDeclaredField("globalRuntimeVariables");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ExecutionManager.RuntimeVariable> globalVars =
            (Map<String, ExecutionManager.RuntimeVariable>) field.get(manager);
        globalVars.clear();
    }

    private PathmindRuntimeImpl createRuntime() {
        return new PathmindRuntimeImpl(manager);
    }

    // -------------------------------------------------------------------------
    // Behavior case 1: Double round-trip (PARAM_AMOUNT / "Amount")
    // -------------------------------------------------------------------------

    @Test
    void numberRoundTripReturnsSameDoubleValue() {
        runtime = createRuntime();
        runtime.setVariable("n", 42.0);
        Object result = runtime.getVariable("n");
        assertNotNull(result, "getVariable must return a value for a set Double");
        assertInstanceOf(Double.class, result, "getVariable must return Double for a numeric variable");
        assertEquals(42.0, (Double) result, 0.0001, "getVariable must return the same Double that was set");
    }

    // -------------------------------------------------------------------------
    // Behavior case 2: Boolean round-trip (PARAM_BOOLEAN / "Toggle")
    // -------------------------------------------------------------------------

    @Test
    void booleanRoundTripReturnsSameBooleanValue() {
        runtime = createRuntime();
        runtime.setVariable("b", true);
        Object result = runtime.getVariable("b");
        assertNotNull(result, "getVariable must return a value for a set Boolean");
        assertInstanceOf(Boolean.class, result, "getVariable must return Boolean for a boolean variable");
        assertEquals(Boolean.TRUE, result, "getVariable must return true when true was set");
    }

    @Test
    void booleanFalseRoundTripReturnsFalse() {
        runtime = createRuntime();
        runtime.setVariable("bf", false);
        Object result = runtime.getVariable("bf");
        assertNotNull(result, "getVariable must return a value for Boolean false");
        assertInstanceOf(Boolean.class, result, "getVariable must return Boolean for boolean false");
        assertEquals(Boolean.FALSE, result, "getVariable must return false when false was set");
    }

    // -------------------------------------------------------------------------
    // Behavior case 3: String round-trip (PARAM_MESSAGE / "Text")
    // -------------------------------------------------------------------------

    @Test
    void stringRoundTripReturnsSameStringValue() {
        runtime = createRuntime();
        runtime.setVariable("s", "hi");
        Object result = runtime.getVariable("s");
        assertNotNull(result, "getVariable must return a value for a set String");
        assertInstanceOf(String.class, result, "getVariable must return String for a string variable");
        assertEquals("hi", result, "getVariable must return the same String that was set");
    }

    // -------------------------------------------------------------------------
    // Behavior case 4: absent variable returns null
    // -------------------------------------------------------------------------

    @Test
    void getVariableReturnsNullForAbsentName() {
        runtime = createRuntime();
        Object result = runtime.getVariable("does_not_exist");
        assertNull(result, "getVariable must return null for a variable that was never set");
    }

    // -------------------------------------------------------------------------
    // Behavior case 5: unsupported type throws IllegalArgumentException
    // -------------------------------------------------------------------------

    @Test
    void setVariableThrowsIllegalArgumentExceptionForUnsupportedType() {
        runtime = createRuntime();
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> runtime.setVariable("x", new Object()),
            "setVariable must throw IllegalArgumentException for an unsupported value type"
        );
        assertTrue(ex.getMessage() != null && !ex.getMessage().isEmpty(),
            "IllegalArgumentException message must not be empty");
        // The type name should appear in the message
        assertTrue(ex.getMessage().contains("Object"),
            "IllegalArgumentException message must name the unsupported type, got: " + ex.getMessage());
    }

    @Test
    void setVariableThrowsIllegalArgumentExceptionForNull() {
        runtime = createRuntime();
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> runtime.setVariable("x", null),
            "setVariable must throw IllegalArgumentException for null value"
        );
        assertNotNull(ex.getMessage(), "IllegalArgumentException message must not be null");
    }

    // -------------------------------------------------------------------------
    // Behavior case 6: cross-node sharing — readable via ExecutionManager directly
    // -------------------------------------------------------------------------

    @Test
    void variableSetViaRuntimeIsReadableViaExecutionManagerDirectly() {
        runtime = createRuntime();
        runtime.setVariable("shared", 99.5);

        // Read back directly through ExecutionManager (proves it lands in the shared store)
        ExecutionManager.RuntimeVariable rv =
            manager.getRuntimeVariableFromAnyActiveChain("shared");

        assertNotNull(rv, "RuntimeVariable must be retrievable via ExecutionManager after being set through PathmindRuntimeImpl");
        assertEquals(NodeType.PARAM_AMOUNT, rv.getType(),
            "RuntimeVariable type must be PARAM_AMOUNT for a Double value");
        assertEquals("99.5", rv.getValues().get("Amount"),
            "RuntimeVariable 'Amount' key must hold the string representation of the Double value");
    }

    @Test
    void booleanVariableSetViaRuntimeHasCorrectKeysInExecutionManager() {
        runtime = createRuntime();
        runtime.setVariable("flag", true);

        ExecutionManager.RuntimeVariable rv =
            manager.getRuntimeVariableFromAnyActiveChain("flag");

        assertNotNull(rv, "Boolean RuntimeVariable must be retrievable via ExecutionManager");
        assertEquals(NodeType.PARAM_BOOLEAN, rv.getType(),
            "RuntimeVariable type must be PARAM_BOOLEAN for a Boolean value");
        Map<String, String> values = rv.getValues();
        assertEquals("literal", values.get("Mode"),
            "PARAM_BOOLEAN 'Mode' key must be 'literal'");
        assertEquals("true", values.get("Toggle"),
            "PARAM_BOOLEAN 'Toggle' key must hold the string representation of the Boolean value");
        assertEquals("", values.get("Variable"),
            "PARAM_BOOLEAN 'Variable' key must be empty string");
    }

    @Test
    void stringVariableSetViaRuntimeHasCorrectKeyInExecutionManager() {
        runtime = createRuntime();
        runtime.setVariable("greeting", "hello");

        ExecutionManager.RuntimeVariable rv =
            manager.getRuntimeVariableFromAnyActiveChain("greeting");

        assertNotNull(rv, "String RuntimeVariable must be retrievable via ExecutionManager");
        assertEquals(NodeType.PARAM_MESSAGE, rv.getType(),
            "RuntimeVariable type must be PARAM_MESSAGE for a String value");
        assertEquals("hello", rv.getValues().get("Text"),
            "PARAM_MESSAGE 'Text' key must hold the String value");
    }

    // -------------------------------------------------------------------------
    // Key convention verification — no wrong keys should appear
    // -------------------------------------------------------------------------

    @Test
    void doubleVariableUsesOnlyAmountKey() {
        runtime = createRuntime();
        runtime.setVariable("num", 7.0);

        ExecutionManager.RuntimeVariable rv =
            manager.getRuntimeVariableFromAnyActiveChain("num");

        assertNotNull(rv);
        Map<String, String> values = rv.getValues();
        assertNotNull(values.get("Amount"), "'Amount' key must be present for PARAM_AMOUNT");
        assertEquals(1, values.size(), "PARAM_AMOUNT variable must have exactly 1 key ('Amount')");
    }

    @Test
    void stringVariableUsesOnlyTextKey() {
        runtime = createRuntime();
        runtime.setVariable("txt", "world");

        ExecutionManager.RuntimeVariable rv =
            manager.getRuntimeVariableFromAnyActiveChain("txt");

        assertNotNull(rv);
        Map<String, String> values = rv.getValues();
        assertNotNull(values.get("Text"), "'Text' key must be present for PARAM_MESSAGE");
        assertEquals(1, values.size(), "PARAM_MESSAGE variable must have exactly 1 key ('Text')");
    }
}
