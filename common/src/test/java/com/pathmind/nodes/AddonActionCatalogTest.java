package com.pathmind.nodes;

import com.pathmind.api.addon.ActionInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link AddonActionCatalog} — the derived catalog behind
 * {@code PathmindRuntime.listActions()}.
 *
 * <p><strong>Completeness contract:</strong> the catalog must mirror
 * {@link AddonActionInvoker#isInvocable} exactly in both directions — every
 * invocable {@link NodeType} appears, and nothing else does. This is the
 * guarantee that lets the Lua addon generate bindings and completions from the
 * catalog without missing any part of the action surface.
 */
class AddonActionCatalogTest {

    @Test
    void catalogMirrorsInvocableSetExactly() {
        Set<String> expected = java.util.Arrays.stream(NodeType.values())
            .filter(AddonActionInvoker::isInvocable)
            .map(t -> t.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        Set<String> actual = AddonActionCatalog.list().stream()
            .map(ActionInfo::name)
            .collect(Collectors.toSet());

        assertEquals(expected, actual,
            "catalog must contain exactly the invocable node types (both directions)");
        assertFalse(actual.isEmpty(), "the invocable action surface must not be empty");
    }

    @Test
    void entriesAreSortedAndWellFormed() {
        List<ActionInfo> catalog = AddonActionCatalog.list();
        String previous = "";
        for (ActionInfo action : catalog) {
            assertNotNull(action.name());
            assertFalse(action.name().isBlank(), "action name must not be blank");
            assertTrue(action.name().compareTo(previous) > 0, "catalog must be sorted by name");
            previous = action.name();
            assertNotNull(action.displayName(), action.name() + ": displayName must not be null");
            assertFalse(action.displayName().isBlank(), action.name() + ": displayName must not be blank");
            assertNotNull(action.description(), action.name() + ": description must not be null");
            assertNotNull(action.params(), action.name() + ": params must not be null");
            for (ActionInfo.Param param : action.params()) {
                assertFalse(param.name().isBlank(), action.name() + ": param name must not be blank");
                assertNotNull(param.type(), action.name() + "." + param.name() + ": type must not be null");
                assertNotNull(param.defaultValue(), action.name() + "." + param.name() + ": default must not be null");
            }
        }
    }

    @Test
    void gotoReportsDefaultModeCoordinateParams() {
        ActionInfo gotoAction = find("goto");
        List<String> names = gotoAction.params().stream().map(ActionInfo.Param::name).toList();
        assertTrue(names.containsAll(List.of("X", "Y", "Z")),
            "goto (default mode GOTO_XYZ) must report X, Y, Z; got: " + names);
    }

    @Test
    void messageReportsSyntheticTextParam() {
        ActionInfo message = find("message");
        assertEquals("text", message.params().get(0).name(),
            "message must lead with the synthetic 'text' argument the invoker accepts");
    }

    @Test
    void parameterlessActionReportsEmptyParams() {
        ActionInfo jump = find("jump");
        assertTrue(jump.params().isEmpty(),
            "jump takes no parameters; got: " + jump.params());
    }

    /**
     * Every catalog parameter name must be accepted by the invoker's argument
     * matching — i.e. applying a value under each reported name onto a fresh
     * synthetic node must find a matching parameter. This closes the loop
     * catalog → invokeAction (the reverse of the completeness test).
     */
    @Test
    void everyReportedParamIsAcceptedByTheInvoker() {
        for (ActionInfo action : AddonActionCatalog.list()) {
            NodeType type = NodeType.valueOf(action.name().toUpperCase(Locale.ROOT));
            Node node = new Node(type, 0, 0);
            for (ActionInfo.Param param : action.params()) {
                if (type == NodeType.MESSAGE && param.name().equals("text")) {
                    continue; // synthetic argument, handled specially by the invoker
                }
                boolean matched = node.getParameters().stream()
                    .anyMatch(p -> p.getName().equalsIgnoreCase(param.name()));
                assertTrue(matched, action.name() + ": catalog reports parameter '"
                    + param.name() + "' but the synthetic node has no such parameter");
            }
        }
    }

    @Test
    void listIsCachedAndImmutable() {
        List<ActionInfo> first = AddonActionCatalog.list();
        assertTrue(first == AddonActionCatalog.list(), "catalog must be cached (same instance)");
        try {
            first.add(new ActionInfo("x", "x", "", List.of()));
            throw new AssertionError("catalog list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // immutable as required
        }
    }

    private static ActionInfo find(String name) {
        return AddonActionCatalog.list().stream()
            .filter(a -> a.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("catalog is missing action '" + name + "'"));
    }
}
