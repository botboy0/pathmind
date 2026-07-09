package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;

import java.util.Collection;
import java.util.Locale;

final class NodeGraphSearchSupport {
    private NodeGraphSearchSupport() {
    }

    static Node findBestMatchingNode(Collection<Node> nodes, String query) {
        if (nodes == null || query == null) {
            return null;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            return null;
        }

        Node bestNode = null;
        int bestScore = 0;
        for (Node node : nodes) {
            if (node == null || node.getType() == null) {
                continue;
            }
            int score = scoreNodeSearch(node, normalizedQuery);
            if (score > bestScore) {
                bestScore = score;
                bestNode = node;
            }
        }
        return bestNode;
    }

    static int scoreNodeSearch(Node node, String query) {
        int bestScore = 0;
        bestScore = Math.max(bestScore, scoreSearchCandidate(node.getType().getDisplayName(), query));
        if (node.getMode() != null) {
            bestScore = Math.max(bestScore, scoreSearchCandidate(node.getMode().getDisplayName(), query) - 20);
        }
        if (node.getId() != null) {
            bestScore = Math.max(bestScore, scoreSearchCandidate(node.getId(), query) - 40);
        }
        return bestScore;
    }

    static int scoreSearchCandidate(String candidate, String query) {
        if (candidate == null || query == null) {
            return 0;
        }
        String normalizedCandidate = candidate.trim().toLowerCase(Locale.ROOT);
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (normalizedCandidate.isEmpty() || normalizedQuery.isEmpty()) {
            return 0;
        }
        if (normalizedCandidate.equals(normalizedQuery)) {
            return 1000;
        }
        if (normalizedCandidate.startsWith(normalizedQuery)) {
            return 800 - Math.max(0, normalizedCandidate.length() - normalizedQuery.length());
        }
        int containsIndex = normalizedCandidate.indexOf(normalizedQuery);
        if (containsIndex >= 0) {
            return 650 - containsIndex * 6;
        }

        int fuzzyScore = fuzzySubsequenceScore(normalizedCandidate, normalizedQuery);
        return fuzzyScore > 0 ? 300 + fuzzyScore : 0;
    }

    static int fuzzySubsequenceScore(String candidate, String query) {
        int score = 0;
        int streak = 0;
        int queryIndex = 0;
        for (int i = 0; i < candidate.length() && queryIndex < query.length(); i++) {
            if (candidate.charAt(i) == query.charAt(queryIndex)) {
                score += 8 + streak * 4;
                streak++;
                queryIndex++;
            } else {
                streak = 0;
            }
        }
        if (queryIndex != query.length()) {
            return 0;
        }
        return Math.max(1, score - Math.max(0, candidate.length() - query.length()));
    }
}
