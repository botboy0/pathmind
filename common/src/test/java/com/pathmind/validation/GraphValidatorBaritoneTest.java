package com.pathmind.validation;

import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Missing-Baritone graph validation (Baritone-integration milestone): the
 * validator has always received {@code baritoneAvailable} but ignored it —
 * a graph full of Baritone nodes validated clean and failed only at runtime.
 * Baritone nodes must now surface a {@code missing_baritone} ERROR, mirroring
 * the existing {@code missing_ui_utils} pattern.
 */
class GraphValidatorBaritoneTest {

    @Test
    void baritoneNodeWithoutBaritoneReportsMissingBaritoneError() {
        Node start = new Node(NodeType.START, 0, 0);
        Node gotoNode = new Node(NodeType.GOTO, 100, 0);
        NodeConnection connection = new NodeConnection(start, gotoNode, 0, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, gotoNode),
            List.of(connection),
            PresetManager.getDefaultPresetName(),
            false,
            true
        );

        assertTrue(hasIssueCode(result, "missing_baritone"),
            "a Baritone-requiring node without Baritone must produce missing_baritone");
        assertTrue(result.hasErrors());
    }

    @Test
    void baritoneNodeWithBaritonePresentValidatesClean() {
        Node start = new Node(NodeType.START, 0, 0);
        Node gotoNode = new Node(NodeType.GOTO, 100, 0);
        NodeConnection connection = new NodeConnection(start, gotoNode, 0, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, gotoNode),
            List.of(connection),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertFalse(hasIssueCode(result, "missing_baritone"));
    }

    @Test
    void nonBaritoneGraphIsUnaffectedByMissingBaritone() {
        Node start = new Node(NodeType.START, 0, 0);
        Node jump = new Node(NodeType.JUMP, 100, 0);
        NodeConnection connection = new NodeConnection(start, jump, 0, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, jump),
            List.of(connection),
            PresetManager.getDefaultPresetName(),
            false,
            true
        );

        assertFalse(hasIssueCode(result, "missing_baritone"));
    }

    private boolean hasIssueCode(GraphValidationResult result, String code) {
        return result.getIssues().stream().anyMatch(issue -> issue != null && code.equals(issue.getCode()));
    }
}
