package com.pathmind.ui.search;

import com.pathmind.nodes.NodeType;
import com.pathmind.ui.sidebar.Sidebar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NodeSearchMapper {
    private NodeSearchMapper() {
    }

    public static List<NodeSearchEntry> map(List<Sidebar.SidebarSearchResult> sidebarResults) {
        if (sidebarResults == null || sidebarResults.isEmpty()) {
            return List.of();
        }

        List<NodeSearchEntry> entries = new ArrayList<>();
        for (Sidebar.SidebarSearchResult result : sidebarResults) {
            entries.add(new NodeSearchEntry(
                result.entry(),
                labelFor(result.entry()),
                result.category().name().replace('_', ' '),
                result.score()
            ));
        }
        entries.sort(Comparator
            .comparingInt(NodeSearchEntry::score)
            .reversed()
            .thenComparing(NodeSearchEntry::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    private static String labelFor(Sidebar.SidebarNodeEntry entry) {
        return entry.builtInType()
            .map(NodeType::getDisplayName)
            .orElseGet(() -> entry.id().toString());
    }
}
