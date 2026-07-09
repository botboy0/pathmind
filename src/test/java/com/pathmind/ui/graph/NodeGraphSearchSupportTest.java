package com.pathmind.ui.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;

import java.util.List;

import org.junit.jupiter.api.Test;

class NodeGraphSearchSupportTest {
    @Test
    void findsBestMatchingNodeByDisplayName() {
        Node wait = new Node(NodeType.WAIT, 0, 0);
        Node message = new Node(NodeType.MESSAGE, 0, 0);

        assertSame(message, NodeGraphSearchSupport.findBestMatchingNode(List.of(wait, message), "message"));
    }

    @Test
    void fuzzyMatchScoresSubsequenceButRejectsMissingCharacters() {
        assertEquals(1000, NodeGraphSearchSupport.scoreSearchCandidate("Message", "message"));
        assertEquals(0, NodeGraphSearchSupport.fuzzySubsequenceScore("message", "mxz"));
    }

    @Test
    void blankQueryDoesNotMatch() {
        Node wait = new Node(NodeType.WAIT, 0, 0);

        assertNull(NodeGraphSearchSupport.findBestMatchingNode(List.of(wait), "   "));
    }
}
