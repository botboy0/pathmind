package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-call {@code AllowBreak}/{@code AllowPlace} for script-invoked GOTO
 * (fork-only): the invoker intercepts the two special arguments and sets a
 * per-node override; {@code isGotoAllowBreakWhileExecuting()} /
 * {@code isGotoAllowPlaceWhileExecuting()} consult the override before the
 * global settings. Graph nodes never set the override, so graph behavior and
 * the global defaults (both false) are untouched.
 */
class AddonGotoGuardOverrideTest {

    @Test
    void allowBreakOverrideWinsOverSettings() {
        Node node = new Node(NodeType.GOTO, 0, 0);
        node.setAddonGotoAllowBreakOverride(Boolean.TRUE);
        assertTrue(node.isGotoAllowBreakWhileExecuting());

        node.setAddonGotoAllowBreakOverride(Boolean.FALSE);
        assertFalse(node.isGotoAllowBreakWhileExecuting());
    }

    @Test
    void allowPlaceOverrideWinsOverSettings() {
        Node node = new Node(NodeType.GOTO, 0, 0);
        node.setAddonGotoAllowPlaceOverride(Boolean.TRUE);
        assertTrue(node.isGotoAllowPlaceWhileExecuting());

        node.setAddonGotoAllowPlaceOverride(Boolean.FALSE);
        assertFalse(node.isGotoAllowPlaceWhileExecuting());
    }

    @Test
    void overrideIsInertOnNonNavigationNodes() {
        Node node = new Node(NodeType.JUMP, 0, 0);
        node.setAddonGotoAllowBreakOverride(Boolean.TRUE);
        node.setAddonGotoAllowPlaceOverride(Boolean.TRUE);

        assertFalse(node.isGotoAllowBreakWhileExecuting(),
            "the existing type guard must keep non-GOTO/TRAVEL nodes at false");
        assertFalse(node.isGotoAllowPlaceWhileExecuting());
    }

    @Test
    void invokerAppliesGuardOverrideArguments() {
        Node node = new Node(NodeType.GOTO, 0, 0);

        assertNull(AddonActionInvoker.applyGotoGuardOverrideArg(node, "AllowBreak", Boolean.TRUE));
        assertTrue(node.isGotoAllowBreakWhileExecuting());

        assertNull(AddonActionInvoker.applyGotoGuardOverrideArg(node, "allowplace", Boolean.TRUE));
        assertTrue(node.isGotoAllowPlaceWhileExecuting(),
            "guard-override argument keys must be case-insensitive");
    }

    @Test
    void guardOverrideArgumentsRequireBooleans() {
        Node node = new Node(NodeType.GOTO, 0, 0);

        String error = AddonActionInvoker.applyGotoGuardOverrideArg(node, "AllowBreak", "yes");
        assertNotNull(error, "non-boolean AllowBreak must be rejected as a caller error");
        assertTrue(error.contains("AllowBreak"), "error must name the argument, got: " + error);
        assertFalse(node.isGotoAllowBreakWhileExecuting());

        assertNotNull(AddonActionInvoker.applyGotoGuardOverrideArg(node, "AllowPlace", 1.0));
    }
}
