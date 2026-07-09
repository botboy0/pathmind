package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.menu.ContextMenu;
import com.pathmind.ui.menu.ContextMenuSelection;
import com.pathmind.ui.menu.NodeContextMenu;
import com.pathmind.ui.menu.NodeContextMenuAction;
import com.pathmind.ui.sidebar.Sidebar;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

final class NodeGraphContextMenuSupport {
    private final NodeGraph graph;
    private ContextMenu contextMenu;
    private NodeContextMenu nodeContextMenu;
    private int contextMenuWorldX;
    private int contextMenuWorldY;
    private int nodeContextMenuWorldX;
    private int nodeContextMenuWorldY;
    private Node nodeContextMenuTarget;

    NodeGraphContextMenuSupport(NodeGraph graph) {
        this.graph = graph;
    }

    void showContextMenu(int screenX, int screenY, Sidebar sidebar, int screenWidth, int screenHeight) {
        closeNodeContextMenu();
        if (contextMenu == null) {
            contextMenu = new ContextMenu(sidebar);
        }
        contextMenu.setScale(graph.getZoomScale());
        contextMenuWorldX = graph.screenToWorldX(screenX);
        contextMenuWorldY = graph.screenToWorldY(screenY);
        contextMenu.setAnchorScreen(screenX, screenY);
        contextMenu.showAt(screenX, screenY, screenWidth, screenHeight);
    }

    void showNodeContextMenu(int screenX, int screenY, Node targetNode, int screenWidth, int screenHeight) {
        closeContextMenu();
        if (nodeContextMenu == null) {
            nodeContextMenu = new NodeContextMenu();
        }
        nodeContextMenuTarget = targetNode;
        nodeContextMenuWorldX = graph.screenToWorldX(screenX);
        nodeContextMenuWorldY = graph.screenToWorldY(screenY);
        nodeContextMenu.setScale(graph.getZoomScale());
        nodeContextMenu.setAnchorScreen(screenX, screenY);
        nodeContextMenu.showAt(screenX, screenY, screenWidth, screenHeight);
    }

    void closeContextMenu() {
        if (contextMenu != null) {
            contextMenu.close();
        }
    }

    void closeNodeContextMenu() {
        if (nodeContextMenu != null) {
            nodeContextMenu.close();
        }
        nodeContextMenuTarget = null;
    }

    boolean isContextMenuOpen() {
        return contextMenu != null && contextMenu.isOpen();
    }

    boolean isNodeContextMenuOpen() {
        return nodeContextMenu != null && nodeContextMenu.isOpen();
    }

    void updateContextMenuHover(int mouseX, int mouseY) {
        if (contextMenu == null || !contextMenu.isOpen()) {
            return;
        }
        syncContextMenuAnchor();
        contextMenu.updateHover(mouseX, mouseY);
    }

    void updateNodeContextMenuHover(int mouseX, int mouseY) {
        if (nodeContextMenu == null || !nodeContextMenu.isOpen()) {
            return;
        }
        syncNodeContextMenuAnchor();
        nodeContextMenu.updateHover(mouseX, mouseY);
    }

    ContextMenuSelection handleContextMenuClick(int mouseX, int mouseY) {
        if (contextMenu == null || !contextMenu.isOpen()) {
            return null;
        }
        syncContextMenuAnchor();
        return contextMenu.handleClick(mouseX, mouseY);
    }

    boolean handleNodeContextMenuClick(int mouseX, int mouseY) {
        if (nodeContextMenu == null || !nodeContextMenu.isOpen()) {
            return false;
        }
        syncNodeContextMenuAnchor();
        NodeContextMenuAction action = nodeContextMenu.handleClick(mouseX, mouseY);
        if (action == null) {
            closeNodeContextMenu();
            return true;
        }

        if (nodeContextMenuTarget != null && !graph.isNodeSelected(nodeContextMenuTarget)) {
            graph.selectNode(nodeContextMenuTarget);
        }

        switch (action) {
            case COPY -> graph.copySelectedNodeToClipboard();
            case DUPLICATE -> graph.duplicateSelectedNode();
            case PASTE -> graph.pasteClipboardNode();
            case DELETE -> graph.deleteSelectedNode();
        }
        closeNodeContextMenu();
        return true;
    }

    void renderContextMenu(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (contextMenu == null || !contextMenu.isOpen()) {
            return;
        }
        syncContextMenuAnchor();
        contextMenu.render(context, textRenderer, mouseX, mouseY);
    }

    void renderNodeContextMenu(DrawContext context, TextRenderer textRenderer) {
        if (nodeContextMenu == null || !nodeContextMenu.isOpen()) {
            return;
        }
        syncNodeContextMenuAnchor();
        nodeContextMenu.render(context, textRenderer);
    }

    Node addNodeFromContextMenu(NodeType type) {
        return graph.addNodeAtPosition(type, contextMenuWorldX, contextMenuWorldY);
    }

    boolean handleContextMenuScroll(int mouseX, int mouseY, double amount) {
        if (contextMenu == null || !contextMenu.isOpen()) {
            return false;
        }
        syncContextMenuAnchor();
        if (!contextMenu.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        contextMenu.handleScroll(amount);
        return true;
    }

    private void syncContextMenuAnchor() {
        int anchorScreenX = graph.worldToScreenX(contextMenuWorldX);
        int anchorScreenY = graph.worldToScreenY(contextMenuWorldY);
        contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
        contextMenu.setScale(graph.getZoomScale());
    }

    private void syncNodeContextMenuAnchor() {
        int anchorScreenX = graph.worldToScreenX(nodeContextMenuWorldX);
        int anchorScreenY = graph.worldToScreenY(nodeContextMenuWorldY);
        nodeContextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
        nodeContextMenu.setScale(graph.getZoomScale());
    }
}
