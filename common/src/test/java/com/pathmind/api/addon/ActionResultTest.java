package com.pathmind.api.addon;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the {@link ActionResult} envelope — the value the addon-facing
 * {@code invokeAction} future resolves to (action-result-envelopes design).
 */
class ActionResultTest {

    @Test
    void successCarriesFieldsAndNoStatusOrMessage() {
        ActionResult r = ActionResult.success(Map.of("produced", 4));

        assertTrue(r.isOk());
        assertNull(r.getStatus());
        assertNull(r.getMessage());
        assertEquals(4, r.getFields().get("produced"));
    }

    @Test
    void successWithoutFieldsHasEmptyNonNullFields() {
        assertNotNull(ActionResult.success(null).getFields());
        assertTrue(ActionResult.success(null).getFields().isEmpty());
        assertTrue(ActionResult.success(Map.of()).getFields().isEmpty());
    }

    @Test
    void failureCarriesStatusMessageAndDetailFields() {
        ActionResult r = ActionResult.failure("missing_resource",
            "Cannot craft Stone Pickaxe: missing required ingredients.",
            Map.of("missing", List.of("minecraft:stick")));

        assertFalse(r.isOk());
        assertEquals("missing_resource", r.getStatus());
        assertEquals("Cannot craft Stone Pickaxe: missing required ingredients.", r.getMessage());
        assertEquals(List.of("minecraft:stick"), r.getFields().get("missing"));
    }

    @Test
    void failureWithoutDetailHasEmptyNonNullFields() {
        ActionResult r = ActionResult.failure("failed", "boom", null);
        assertNotNull(r.getFields());
        assertTrue(r.getFields().isEmpty());
    }

    @Test
    void fieldsAreUnmodifiable() {
        ActionResult r = ActionResult.success(new HashMap<>(Map.of("produced", 1)));
        assertThrows(UnsupportedOperationException.class, () -> r.getFields().put("x", 1));
    }

    @Test
    void fieldsAreCopiedNotAliased() {
        Map<String, Object> source = new HashMap<>();
        source.put("produced", 1);
        ActionResult r = ActionResult.success(source);

        source.put("produced", 99);
        source.put("extra", true);

        assertEquals(1, r.getFields().get("produced"));
        assertFalse(r.getFields().containsKey("extra"));
    }
}
