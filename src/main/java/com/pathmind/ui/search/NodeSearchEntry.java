package com.pathmind.ui.search;

import com.pathmind.nodes.NodeType;
import com.pathmind.ui.sidebar.Sidebar;

import java.util.Optional;

public record NodeSearchEntry(
    Sidebar.SidebarNodeEntry entry,
    String label,
    String categoryLabel,
    int score
) {
    public Optional<NodeType> builtInType() {
        return entry.builtInType();
    }
}
