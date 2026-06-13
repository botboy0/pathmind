package com.pathmind.ui.sidebar;

import com.pathmind.api.addon.AddonNodeCategory;
import com.pathmind.api.addon.AddonNodeDefinition;
import com.pathmind.nodes.NodeTypeRegistry;
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.UiUtilsDependencyChecker;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.ScrollbarHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the sidebar with categorized draggable nodes.
 * Uses a nested, beveled panel style with category tabs and grouped node lists.
 */
public class Sidebar {
    // Outer sidebar dimensions
    private static final int OUTER_SIDEBAR_WIDTH = 180;
    private static final int INNER_SIDEBAR_WIDTH = 40;
    private static final int TAB_SIZE = 24;
    private static final int TAB_SPACING = 8;
    private static final int TOP_PADDING = 8;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MARGIN = 4;
    private static final int SCROLLBAR_MIN_KNOB_HEIGHT = 20;
    
    // Node display dimensions
    private static final int NODE_HEIGHT = 18;
    private static final int PADDING = 4;
    private static final int CATEGORY_HEADER_HEIGHT = 20;
    private static final int CATEGORY_HEADER_LINE_SPACING = 2;
    private static final int GROUP_HEADER_HEIGHT = 16;
    private static final int GROUP_HEADER_LINE_SPACING = 2;
    private static final int NODE_LINE_SPACING = 1;

    private final Map<NodeCategory, List<NodeType>> categoryNodes;
    private final Map<NodeCategory, List<NodeGroup>> groupedCategoryNodes;
    private final Map<NodeCategory, Boolean> categoryExpanded;
    private final Map<NodeCategory, AnimatedValue> tabHoverAnimations;
    private final AnimatedValue categoryOpenAnimation;
    private final boolean baritoneAvailable;
    private final boolean uiUtilsAvailable;
    private NodeType hoveredNodeType = null;
    private CustomNodeEntry hoveredCustomNode = null;
    private NodeCategory hoveredCategory = null;
    private NodeCategory selectedCategory = null;
    private final List<CustomNodeEntry> customNodes = new ArrayList<>();
    // Addon-declared categories, populated from NodeTypeRegistry after install (D-05)
    private final Map<AddonNodeCategory, List<AddonNodeDefinition>> addonCategoryNodes = new LinkedHashMap<>();
    private AddonNodeDefinition hoveredAddonDefinition = null;
    private AddonNodeCategory hoveredAddonCategory = null;
    private AddonNodeCategory selectedAddonCategory = null;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean scrollDragging = false;
    private int scrollDragOffset = 0;
    // GAP-A: separate vertical scroll for the category ICON BAR (the inner strip of
    // category tabs), independent of the content-panel scroll above. Mirrors the
    // scrollOffset/maxScroll pattern. Recomputed every render frame from the tab layout.
    private int iconBarScrollOffset = 0;
    private int iconBarMaxScroll = 0;
    // Geometry of the icon-bar viewport for the current frame, captured during render so
    // mouseScrolled / mouseClicked can hit-test against the scrolled tab strip (GAP-A).
    private int iconBarViewportTop = 0;
    private int iconBarViewportHeight = 0;
    private int currentSidebarHeight = 400; // Store current sidebar height
    private int currentSidebarStartY = 0;
    private int currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH;
    private int currentRenderedWidth = INNER_SIDEBAR_WIDTH;
    
    public Sidebar() {
        this(BaritoneDependencyChecker.isBaritonePresent(), UiUtilsDependencyChecker.isUiUtilsPresent());
    }

    public Sidebar(boolean baritoneAvailable, boolean uiUtilsAvailable) {
        this.categoryExpanded = new HashMap<>();
        this.categoryNodes = new HashMap<>();
        this.groupedCategoryNodes = new HashMap<>();
        this.tabHoverAnimations = new HashMap<>();
        this.categoryOpenAnimation = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
        this.baritoneAvailable = baritoneAvailable;
        this.uiUtilsAvailable = uiUtilsAvailable;
        
        // Initialize categories as expanded by default
        for (NodeCategory category : NodeCategory.values()) {
            categoryExpanded.put(category, true);
        }

        // Organize nodes by category
        initializeCategoryNodes();
        // Populate addon-declared categories from the installed registry (D-05)
        initializeAddonCategoryNodes();
        calculateMaxScroll(400); // Default height for initialization
    }
    
    private void initializeCategoryNodes() {
        for (NodeCategory category : NodeCategory.values()) {
            List<NodeGroup> groups = createGroupsForCategory(category);
            List<NodeType> nodes = new ArrayList<>();
            for (NodeGroup group : groups) {
                nodes.addAll(group.getNodes());
            }
            groupedCategoryNodes.put(category, groups);
            categoryNodes.put(category, nodes);
        }
        refreshCustomNodes();
    }

    /**
     * Populates the addon-category palette from the installed NodeTypeRegistry (D-05).
     * Groups all registered AddonNodeDefinitions by their declared AddonNodeCategory.
     * Called during sidebar construction and whenever the registry changes.
     *
     * <p>With no addons installed the map is left empty — no addon categories appear in the
     * sidebar (API-09 standalone path).
     */
    public void initializeAddonCategoryNodes() {
        addonCategoryNodes.clear();
        hoveredAddonDefinition = null;
        hoveredAddonCategory = null;
        selectedAddonCategory = null;
        addonCategoryNodes.putAll(groupByCategory(NodeTypeRegistry.INSTANCE.allDefinitions()));
    }

    /**
     * Groups the given addon definitions by their declared category. Exposed as a
     * package-private pure static helper so AddonSidebarTest can test the grouping logic
     * directly without constructing a full Sidebar (which may pull in client-only classes).
     *
     * @param definitions the addon node definitions to group
     * @return an ordered map from AddonNodeCategory to the list of definitions in that category
     */
    static Map<AddonNodeCategory, List<AddonNodeDefinition>> groupByCategory(
            Collection<AddonNodeDefinition> definitions) {
        Map<AddonNodeCategory, List<AddonNodeDefinition>> result = new LinkedHashMap<>();
        for (AddonNodeDefinition def : definitions) {
            result.computeIfAbsent(def.getCategory(), k -> new ArrayList<>()).add(def);
        }
        return result;
    }

    /**
     * Returns the addon definition currently hovered by the user in the addon-category
     * section of the sidebar, or {@code null} if no addon entry is hovered.
     * Mirrors {@link #getHoveredNodeType()} for addon entries (D-06).
     *
     * @return hovered addon definition, or null
     */
    public AddonNodeDefinition getHoveredAddonDefinition() {
        return hoveredAddonDefinition;
    }

    /**
     * Returns the addon category node map for testing. Package-private.
     */
    Map<AddonNodeCategory, List<AddonNodeDefinition>> getAddonCategoryNodesForTest() {
        return addonCategoryNodes;
    }

    private List<NodeGroup> createGroupsForCategory(NodeCategory category) {
        if (category == null || category == NodeCategory.CUSTOM) {
            return java.util.Collections.emptyList();
        }
        switch (category) {
            case FLOW:
                return createFlowGroups();
            case CONTROL:
                return createControlGroups();
            case WORLD:
                return createWorldGroups();
            case PLAYER:
                return createPlayerGroups();
            case INTERFACE:
                return createInterfaceGroups();
            case DATA:
                return createDataGroups();
            case SENSORS:
                return createSensorGroups();
            case PARAMETERS:
                return createParameterGroups();
            default:
                return java.util.Collections.emptyList();
        }
    }

    private void refreshCustomNodes() {
        customNodes.clear();
        for (String presetName : PresetManager.getAvailablePresets()) {
            if (presetName == null || presetName.isBlank()) {
                continue;
            }
            customNodes.add(new CustomNodeEntry(presetName.trim()));
        }
    }

    private List<NodeGroup> createFlowGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(createGroup(
            "pathmind.sidebar.group.entryPoints",
            NodeType.START,
            NodeType.START_CHAIN,
            NodeType.EVENT_FUNCTION,
            NodeType.EVENT_CALL
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.timingStops",
            NodeType.WAIT,
            NodeType.STOP_CHAIN,
            NodeType.STOP_ALL
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.presets",
            NodeType.RUN_PRESET,
            NodeType.TEMPLATE
        ));
        return groups;
    }

    private List<NodeGroup> createControlGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(createGroup(
            "pathmind.sidebar.group.branchingLoops",
            NodeType.CONTROL_IF,
            NodeType.CONTROL_IF_ELSE,
            NodeType.CONTROL_REPEAT,
            NodeType.CONTROL_REPEAT_UNTIL,
            NodeType.CONTROL_FOREVER
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.parallel",
            NodeType.CONTROL_FORK,
            NodeType.CONTROL_JOIN_ANY,
            NodeType.CONTROL_JOIN_ALL
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.conditionsWaiting",
            NodeType.CONTROL_WAIT_UNTIL
        ));
        return groups;
    }

    private List<NodeGroup> createWorldGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(createGroup(
            "pathmind.sidebar.group.navigation",
            NodeType.GOTO,
            NodeType.TRAVEL,
            NodeType.GOAL,
            NodeType.PATH,
            NodeType.INVERT,
            NodeType.COME,
            NodeType.SURFACE,
            NodeType.STOP
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.exploration",
            NodeType.EXPLORE,
            NodeType.FOLLOW
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.gathering",
            NodeType.COLLECT,
            NodeType.FARM,
            NodeType.TUNNEL
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.buildingCrafting",
            NodeType.BUILD,
            NodeType.PLACE,
            NodeType.CRAFT
        ));
        return groups;
    }

    private List<NodeGroup> createPlayerGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(createGroup(
            "pathmind.sidebar.group.movement",
            NodeType.WALK,
            NodeType.JUMP,
            NodeType.CRAWL,
            NodeType.CROUCH,
            NodeType.SPRINT,
            NodeType.FLY
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.viewInput",
            NodeType.LOOK,
            NodeType.PRESS_KEY
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.interaction",
            NodeType.USE,
            NodeType.INTERACT,
            NodeType.BREAK,
            NodeType.PLACE_HAND
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.combatTrading",
            NodeType.SWING,
            NodeType.TRADE
        ));
        return groups;
    }

    private List<NodeGroup> createInterfaceGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(createGroup(
            "pathmind.sidebar.group.inventory",
            NodeType.HOTBAR,
            NodeType.MOVE_ITEM,
            NodeType.DROP_ITEM,
            NodeType.CLICK_SLOT,
            NodeType.OPEN_INVENTORY,
            NodeType.EQUIP_HAND,
            NodeType.EQUIP_ARMOR
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.screensUi",
            NodeType.CLICK_SCREEN,
            NodeType.CLOSE_GUI,
            NodeType.UI_UTILS
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.writingOutput",
            NodeType.MESSAGE,
            NodeType.WRITE_BOOK,
            NodeType.WRITE_SIGN
        ));
        return groups;
    }

    private List<NodeGroup> createDataGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(createGroup(
            "pathmind.sidebar.group.variables",
            NodeType.VARIABLE,
            NodeType.SET_VARIABLE,
            NodeType.CHANGE_VARIABLE
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.lists",
            NodeType.CREATE_LIST,
            NodeType.ADD_TO_LIST,
            NodeType.REMOVE_FIRST_FROM_LIST,
            NodeType.REMOVE_LAST_FROM_LIST,
            NodeType.REMOVE_LIST_ITEM,
            NodeType.REMOVE_FROM_LIST,
            NodeType.LIST_ITEM,
            NodeType.LIST_LENGTH
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.comparisonBoolean",
            NodeType.OPERATOR_EQUALS,
            NodeType.OPERATOR_NOT,
            NodeType.OPERATOR_BOOLEAN_NOT,
            NodeType.OPERATOR_BOOLEAN_OR,
            NodeType.OPERATOR_BOOLEAN_AND,
            NodeType.OPERATOR_BOOLEAN_XOR,
            NodeType.OPERATOR_GREATER,
            NodeType.OPERATOR_LESS
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.mathRandom",
            NodeType.OPERATOR_MOD,
            NodeType.OPERATOR_RANDOM
        ));
        return groups;
    }

    private List<NodeGroup> createParameterGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(createGroup(
            "pathmind.sidebar.group.spatialData",
            NodeType.PARAM_COORDINATE,
            NodeType.PARAM_ROTATION,
            NodeType.PARAM_DIRECTION,
            NodeType.PARAM_BLOCK_FACE,
            NodeType.PARAM_RANGE,
            NodeType.PARAM_DISTANCE,
            NodeType.PARAM_CLOSEST
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.targetsObjects",
            NodeType.PARAM_BLOCK,
            NodeType.PARAM_ITEM,
            NodeType.PARAM_ENTITY,
            NodeType.PARAM_PLAYER,
            NodeType.PARAM_WAYPOINT,
            NodeType.PARAM_SCHEMATIC
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.inventoryGui",
            NodeType.PARAM_INVENTORY_SLOT,
            NodeType.PARAM_HAND,
            NodeType.PARAM_GUI
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.inputText",
            NodeType.PARAM_KEY,
            NodeType.PARAM_MOUSE_BUTTON,
            NodeType.PARAM_MESSAGE
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.utilityValues",
            NodeType.PARAM_DURATION,
            NodeType.PARAM_AMOUNT,
            NodeType.PARAM_BOOLEAN
        ));
        return groups;
    }

    private List<NodeGroup> createSensorGroups() {
        List<NodeGroup> groups = new ArrayList<>();
        groups.add(createGroup(
            "pathmind.sidebar.group.playerState",
            NodeType.SENSOR_IS_SWIMMING,
            NodeType.SENSOR_IS_IN_LAVA,
            NodeType.SENSOR_IS_UNDERWATER,
            NodeType.SENSOR_IS_ON_GROUND,
            NodeType.SENSOR_IS_FALLING,
            NodeType.SENSOR_HEALTH_BELOW,
            NodeType.SENSOR_HUNGER_BELOW,
            NodeType.SENSOR_CURRENT_HAND
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.eventsInput",
            NodeType.SENSOR_KEY_PRESSED,
            NodeType.SENSOR_CHAT_MESSAGE,
            NodeType.SENSOR_JOINED_SERVER,
            NodeType.SENSOR_FABRIC_EVENT
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.spatialTargeting",
            NodeType.SENSOR_POSITION_OF,
            NodeType.SENSOR_DISTANCE_BETWEEN,
            NodeType.SENSOR_LOOK_DIRECTION,
            NodeType.SENSOR_TARGETED_BLOCK,
            NodeType.SENSOR_TARGETED_ENTITY,
            NodeType.SENSOR_TOUCHING_BLOCK
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.blocksFacesVisibility",
            NodeType.SENSOR_AT_COORDINATES,
            NodeType.SENSOR_TARGETED_BLOCK_FACE,
            NodeType.SENSOR_TOUCHING_ENTITY,
            NodeType.SENSOR_ATTRIBUTE_DETECTION,
            NodeType.SENSOR_IS_RENDERED,
            NodeType.SENSOR_IS_VISIBLE
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.inventoryItems",
            NodeType.SENSOR_ITEM_IN_INVENTORY,
            NodeType.SENSOR_ITEM_IN_SLOT,
            NodeType.SENSOR_SLOT_ITEM_COUNT,
            NodeType.SENSOR_GUI_FILLED
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.trading",
            NodeType.SENSOR_FIND_TRADE,
            NodeType.SENSOR_VILLAGER_TRADE,
            NodeType.SENSOR_IN_STOCK
        ));
        groups.add(createGroup(
            "pathmind.sidebar.group.worldWeather",
            NodeType.SENSOR_IS_DAYTIME,
            NodeType.SENSOR_IS_RAINING
        ));
        return groups;
    }

    private NodeGroup createGroup(String titleKey, NodeType... nodeTypes) {
        List<NodeType> nodes = new ArrayList<>();
        if (nodeTypes != null) {
            for (NodeType type : nodeTypes) {
                if (type == null || type == NodeType.PARAM_PLACE_TARGET) {
                    continue;
                }
                if (shouldIncludeNode(type)) {
                    nodes.add(type);
                }
            }
        }
        return new NodeGroup(titleKey, nodes);
    }

    private boolean shouldIncludeNode(NodeType nodeType) {
        if (nodeType == null || !nodeType.isDraggableFromSidebar()) {
            return false;
        }
        if (!uiUtilsAvailable && nodeType.requiresUiUtils()) {
            return false;
        }
        if (baritoneAvailable) {
            return true;
        }
        return !nodeType.requiresBaritone();
    }

    public boolean isNodeAvailable(NodeType nodeType) {
        return shouldIncludeNode(nodeType);
    }

    private boolean hasGroupedContent(NodeCategory category) {
        List<NodeGroup> groups = groupedCategoryNodes.get(category);
        if (groups == null) {
            return false;
        }
        for (NodeGroup group : groups) {
            if (!group.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<NodeGroup> getGroupsForCategory(NodeCategory category) {
        List<NodeGroup> groups = groupedCategoryNodes.get(category);
        return groups != null ? groups : java.util.Collections.emptyList();
    }
    
    private void calculateMaxScroll(int sidebarHeight) {
        calculateMaxScroll(sidebarHeight, 0, null, null, null);
    }

    private void calculateMaxScroll(int sidebarHeight, int headerHeight, List<GroupHeaderInfo> groupHeaders, List<NodeRowInfo> customNodeRows, List<Integer> addonRowLineCounts) {
        int totalHeight = 0;

        // Add space for category header and nodes (content starts at top)
        if (selectedCategory != null) {
            totalHeight += Math.max(CATEGORY_HEADER_HEIGHT, headerHeight);

            if (selectedCategory == NodeCategory.CUSTOM && customNodeRows != null) {
                for (NodeRowInfo info : customNodeRows) {
                    totalHeight += info.height();
                }
            } else if (groupHeaders != null && !groupHeaders.isEmpty()) {
                for (GroupHeaderInfo info : groupHeaders) {
                    totalHeight += info.getHeight();
                    for (NodeRowInfo row : info.getNodeRows()) {
                        totalHeight += row.height();
                    }
                }
            } else if (hasGroupedContent(selectedCategory)) {
                for (NodeGroup group : getGroupsForCategory(selectedCategory)) {
                    if (group.isEmpty()) {
                        continue;
                    }
                    totalHeight += GROUP_HEADER_HEIGHT;
                }
            } else {
                List<NodeType> nodes = categoryNodes.get(selectedCategory);
                if (nodes != null) {
                    totalHeight += nodes.size() * NODE_HEIGHT;
                }
            }
        }

        // Add space for selected addon category entries (wrap-aware, mirrors render-pass formulas).
        // addonRowLineCounts is always non-null when selectedAddonCategory != null — precomputed in render().
        if (selectedAddonCategory != null && addonRowLineCounts != null) {
            // UAT-GAP-A: compute addon maxScroll via the shared wrap-aware formula (no magic +100).
            // headerLineCount derived from headerHeight: resolve via Math.max(1, ...) mirroring
            // the render pass. We use headerHeight (already max(CATEGORY_HEADER_HEIGHT, ...)) to
            // back-compute the header line count as ceil(headerHeight / headerLineHeight), but
            // the simplest correct approach is to delegate entirely to computeAddonMaxScroll using
            // a headerLineCount of 1 (the common case) — the CATEGORY_HEADER_HEIGHT floor in
            // computeAddonContentHeight ensures the min is always respected.
            //
            // Use the same nodeLineHeight constant (~10) as the previous inline code but now
            // via the shared helper so the two paths stay in sync (WR-03 closure).
            int nodeLineHeight = 10; // textRenderer.fontHeight(9) + NODE_LINE_SPACING(1)
            int headerLineCount = headerHeight > CATEGORY_HEADER_HEIGHT
                    ? (int) Math.ceil((double) headerHeight / nodeLineHeight)
                    : 1;
            maxScroll = computeAddonMaxScroll(headerLineCount, addonRowLineCounts, headerHeight, nodeLineHeight, sidebarHeight);
            return;
        }

        // Add padding
        totalHeight += PADDING * 2;

        // Calculate max scroll with proper room for scrolling
        maxScroll = Math.max(0, totalHeight - sidebarHeight + 100); // Extra 100px for better scrolling (built-in categories)
    }

    /**
     * Pure helper that computes the total rendered content height for an open addon category,
     * using the SAME formulas as the render pass (GAP-1 / WR-04 fix).
     *
     * <p>Header contribution: {@code Math.max(CATEGORY_HEADER_HEIGHT, Math.max(1, headerLineCount) * headerLineHeight)}.
     * Each row contribution: {@code Math.max(NODE_HEIGHT, rowLineCount * nodeLineHeight + PADDING)}.
     *
     * @param headerLineCount number of text lines the category header wraps to (>=1 for single-line)
     * @param rowLineCounts   per-row wrap line counts (empty list = no rows)
     * @param headerLineHeight pixel height per header text line (textRenderer.fontHeight + spacing)
     * @param nodeLineHeight   pixel height per node label line (textRenderer.fontHeight + spacing)
     * @return total content height in pixels
     */
    static int computeAddonContentHeight(int headerLineCount, java.util.List<Integer> rowLineCounts, int headerLineHeight, int nodeLineHeight) {
        int total = Math.max(CATEGORY_HEADER_HEIGHT, Math.max(1, headerLineCount) * headerLineHeight);
        for (int lineCount : rowLineCounts) {
            total += Math.max(NODE_HEIGHT, lineCount * nodeLineHeight + PADDING);
        }
        return total;
    }

    /**
     * Pure helper that computes the maximum scroll offset for an open addon category,
     * derived entirely from {@link #computeAddonContentHeight} — no magic {@code +100}
     * offset (UAT-GAP-A / WR-03 closure).
     *
     * <p>Returns {@code Math.max(0, computeAddonContentHeight(...) + PADDING*2 - sidebarHeight)}.
     * A result of 0 means the content fits in the viewport and no scrollbar is needed.
     *
     * @param headerLineCount  number of text lines the category header wraps to (>=1)
     * @param rowLineCounts    per-row wrap line counts
     * @param headerLineHeight pixel height per header text line
     * @param nodeLineHeight   pixel height per node label line
     * @param sidebarHeight    visible height of the sidebar viewport in pixels
     * @return max scroll offset in pixels (0 = no scroll needed)
     */
    static int computeAddonMaxScroll(int headerLineCount, java.util.List<Integer> rowLineCounts,
            int headerLineHeight, int nodeLineHeight, int sidebarHeight) {
        return Math.max(0, computeAddonContentHeight(headerLineCount, rowLineCounts, headerLineHeight, nodeLineHeight)
                + PADDING * 2 - sidebarHeight);
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY,
                       int sidebarStartY, int sidebarHeight, boolean interactionsEnabled, boolean showTooltips) {
        refreshCustomNodes();
        int effectiveMouseX = interactionsEnabled ? mouseX : Integer.MIN_VALUE;
        int effectiveMouseY = interactionsEnabled ? mouseY : Integer.MIN_VALUE;
        // Store current sidebar height so scroll can be recalculated
        this.currentSidebarStartY = sidebarStartY;
        this.currentSidebarHeight = sidebarHeight;
        categoryOpenAnimation.animateTo((selectedCategory != null || selectedAddonCategory != null) ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
        categoryOpenAnimation.tick();
        float openProgress = categoryOpenAnimation.getValue();
        
        NodeCategory[] categories = NodeCategory.values();
        int totalVisibleTabs = 0;
        for (NodeCategory category : categories) {
            if (hasNodesInCategory(category)) {
                totalVisibleTabs++;
            }
        }

        int availableTabHeight = Math.max(TAB_SIZE, sidebarHeight - TOP_PADDING * 2);
        int tabSize = TAB_SIZE;
        int tabSpacing = TAB_SPACING;
        // GAP-A: count addon tabs alongside built-in tabs for icon-bar overflow math.
        int totalIconBarTabs = totalVisibleTabs + addonCategoryNodes.size();
        // GAP-A: keep tabs at a readable size (no more shrinking toward single-digit px).
        // When the icon strip overflows the available height we SCROLL it instead of
        // squashing every tab. Compute the icon-bar scroll bounds here so render and
        // hit-testing share one source of truth.
        int totalTabHeight = totalIconBarTabs > 0
            ? totalIconBarTabs * tabSize + (totalIconBarTabs - 1) * tabSpacing
            : 0;
        iconBarMaxScroll = Math.max(0, totalTabHeight - availableTabHeight);
        iconBarScrollOffset = ScrollbarHelper.clampScroll(iconBarScrollOffset, iconBarMaxScroll);

        currentInnerSidebarWidth = INNER_SIDEBAR_WIDTH;

        int totalWidth = currentInnerSidebarWidth
            + Math.round((OUTER_SIDEBAR_WIDTH - currentInnerSidebarWidth) * openProgress);
        if (totalWidth < currentInnerSidebarWidth) {
            totalWidth = currentInnerSidebarWidth;
        } else if (totalWidth > OUTER_SIDEBAR_WIDTH) {
            totalWidth = OUTER_SIDEBAR_WIDTH;
        }
        currentRenderedWidth = totalWidth;
        int contentTextX = currentInnerSidebarWidth + 8;
        int contentTextRight = totalWidth - SCROLLBAR_MARGIN - SCROLLBAR_WIDTH - 4;
        int maxContentWidth = Math.max(1, contentTextRight - contentTextX);
        int nodeLabelX = currentInnerSidebarWidth + 8 + 12 + 4;
        int nodeTextWidth = Math.max(1, contentTextRight - nodeLabelX);

        List<String> headerLines = null;
        int headerHeight = 0;
        final int headerLineHeight = textRenderer.fontHeight + CATEGORY_HEADER_LINE_SPACING;
        final int groupLineHeight = textRenderer.fontHeight + GROUP_HEADER_LINE_SPACING;
        final int nodeLineHeight = textRenderer.fontHeight + NODE_LINE_SPACING;
        if (selectedCategory != null) {
            headerLines = wrapText(selectedCategory.getDisplayName(), textRenderer, maxContentWidth);
            headerHeight = Math.max(CATEGORY_HEADER_HEIGHT, headerLines.size() * headerLineHeight);
        } else if (selectedAddonCategory != null) {
            headerLines = wrapText(selectedAddonCategory.getDisplayName(), textRenderer, maxContentWidth);
            headerHeight = Math.max(CATEGORY_HEADER_HEIGHT, headerLines.size() * headerLineHeight);
        }


        List<GroupHeaderInfo> groupHeaders = null;
        List<NodeRowInfo> customNodeRows = null;
        if (selectedCategory != null && hasGroupedContent(selectedCategory)) {
            groupHeaders = new ArrayList<>();
            for (NodeGroup group : getGroupsForCategory(selectedCategory)) {
                if (group.isEmpty()) {
                    continue;
                }
                List<String> lines = wrapText(group.getTitle(), textRenderer, maxContentWidth);
                int height = Math.max(GROUP_HEADER_HEIGHT, lines.size() * groupLineHeight);
                groupHeaders.add(new GroupHeaderInfo(group, lines, height, buildNodeRows(group.getNodes(), textRenderer, nodeTextWidth, nodeLineHeight)));
            }
        } else if (selectedCategory == NodeCategory.CUSTOM) {
            customNodeRows = buildCustomNodeRows(textRenderer, nodeTextWidth, nodeLineHeight);
        }

        // Precompute wrap-aware line counts for addon category rows (mirrors the render pass)
        List<Integer> addonRowLineCounts = null;
        if (selectedAddonCategory != null) {
            addonRowLineCounts = new ArrayList<>();
            List<AddonNodeDefinition> addonDefsForScroll = addonCategoryNodes.get(selectedAddonCategory);
            if (addonDefsForScroll != null) {
                for (AddonNodeDefinition def : addonDefsForScroll) {
                    List<String> lines = wrapText(def.getDisplayName(), textRenderer, nodeTextWidth);
                    addonRowLineCounts.add(lines.size());
                }
            }
        }

        calculateMaxScroll(sidebarHeight, headerHeight, groupHeaders, customNodeRows, addonRowLineCounts);
        
        // Outer sidebar background
        int outerColor = totalWidth > currentInnerSidebarWidth ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR;
        UIStyleHelper.drawPanel(context, 0, sidebarStartY, totalWidth, sidebarHeight, outerColor, UITheme.BORDER_SUBTLE);

        // Inner sidebar background (for tabs)
        context.fill(0, sidebarStartY, currentInnerSidebarWidth, sidebarStartY + sidebarHeight, UITheme.BACKGROUND_SIDEBAR);
        context.drawVerticalLine(currentInnerSidebarWidth, sidebarStartY, sidebarStartY + sidebarHeight, UITheme.BORDER_SUBTLE);

        // GAP-A: the icon bar scrolls independently of the content panel. Tabs start at
        // TOP_PADDING and are shifted up by iconBarScrollOffset; the strip is scissor-clipped
        // to its viewport so scrolled tabs clip cleanly instead of bleeding into the content.
        iconBarViewportTop = sidebarStartY + TOP_PADDING;
        iconBarViewportHeight = availableTabHeight;
        int currentY = iconBarViewportTop - iconBarScrollOffset;

        // Clip the icon strip to its viewport (full inner-strip width).
        context.enableScissor(0, iconBarViewportTop, currentInnerSidebarWidth, iconBarViewportTop + iconBarViewportHeight);

        // Render colored tabs
        hoveredCategory = null;
        hoveredCustomNode = null;
        int tabX = Math.max(0, (currentInnerSidebarWidth - tabSize) / 2);
        int visibleTabIndex = 0;
        for (int i = 0; i < categories.length; i++) {
            NodeCategory category = categories[i];

            // Skip if category has no nodes
            if (!hasNodesInCategory(category)) {
                continue;
            }

            int tabY = currentY + visibleTabIndex * (tabSize + tabSpacing);
            visibleTabIndex++;

            // Check if tab is hovered
            boolean tabHovered = effectiveMouseX >= tabX && effectiveMouseX <= tabX + tabSize &&
                               effectiveMouseY >= tabY && effectiveMouseY < tabY + tabSize;

            // Check if tab is selected
            boolean tabSelected = category == selectedCategory;

            // Get or create hover animation for this tab
            AnimatedValue hoverAnim = tabHoverAnimations.computeIfAbsent(category, k -> AnimatedValue.forHover());
            hoverAnim.animateTo(tabHovered ? 1f : 0f, UITheme.HOVER_ANIM_MS);
            hoverAnim.tick();
            float hoverProgress = hoverAnim.getValue();

            // Tab background color with smooth hover transition
            int baseColor = category.getColor();
            int normalColor = tabSelected ? darkenColor(baseColor, 0.75f) : baseColor;
            int hoverColor = lightenColor(baseColor, 1.2f);
            int tabColor = tabSelected ? normalColor : AnimationHelper.lerpColor(normalColor, hoverColor, hoverProgress);

            // Render square tab
            int outlineColor = darkenColor(baseColor, 0.7f);
            UIStyleHelper.drawBeveledPanel(context, tabX, tabY, tabSize, tabSize, tabColor, outlineColor, UITheme.PANEL_INNER_BORDER);

            // Render centered icon
            String icon = category.getIcon();
            int iconX = tabX + (tabSize - textRenderer.getWidth(icon)) / 2;
            int iconY = tabY + (tabSize - textRenderer.fontHeight) / 2 + 1;

            context.drawTextWithShadow(textRenderer, icon, iconX, iconY, UITheme.TEXT_HEADER);

            // Update hover state
            if (tabHovered) {
                hoveredCategory = category;
            }
        }

        // Render addon category tabs in the inner strip after built-in tabs (D-05)
        hoveredAddonCategory = null;
        if (!addonCategoryNodes.isEmpty()) {
            int addonTabY = currentY + visibleTabIndex * (tabSize + tabSpacing);
            for (Map.Entry<AddonNodeCategory, List<AddonNodeDefinition>> entry : addonCategoryNodes.entrySet()) {
                AddonNodeCategory addonCat = entry.getKey();

                boolean addonTabHovered = effectiveMouseX >= tabX && effectiveMouseX <= tabX + tabSize
                    && effectiveMouseY >= addonTabY && effectiveMouseY < addonTabY + tabSize;
                boolean addonTabSelected = addonCat == selectedAddonCategory;

                int baseColor = addonCat.getColor();
                int normalColor = addonTabSelected ? darkenColor(baseColor, 0.75f) : baseColor;
                int hoverColor = lightenColor(baseColor, 1.2f);
                int tabColor = addonTabSelected ? normalColor
                    : (addonTabHovered ? AnimationHelper.lerpColor(normalColor, hoverColor, 1f) : normalColor);

                int outlineColor = darkenColor(baseColor, 0.7f);
                UIStyleHelper.drawBeveledPanel(context, tabX, addonTabY, tabSize, tabSize, tabColor, outlineColor, UITheme.PANEL_INNER_BORDER);

                String icon = addonCat.getIcon();
                int iconX = tabX + (tabSize - textRenderer.getWidth(icon)) / 2;
                int iconY = addonTabY + (tabSize - textRenderer.fontHeight) / 2 + 1;
                context.drawTextWithShadow(textRenderer, icon, iconX, iconY, UITheme.TEXT_HEADER);

                if (addonTabHovered) {
                    hoveredAddonCategory = addonCat;
                }
                addonTabY += tabSize + tabSpacing;
            }
        }

        // GAP-A: end the icon-strip clip and draw the icon-bar scrollbar when it overflows.
        context.disableScissor();
        renderIconBarScrollbar(context);

        // Render category name and nodes for selected category
        if (selectedCategory != null && openProgress > 0.001f) {
            // No addon category is open in this path — clear stale hover state
            hoveredAddonDefinition = null;
            int contentTop = sidebarStartY + PADDING;
            int contentBottom = sidebarStartY + sidebarHeight - PADDING;
            // Start content area at the very top of the sidebar, right after the title bar
            int contentY = contentTop - scrollOffset;
            int sidebarBottom = sidebarStartY + sidebarHeight;
            int nodeBackgroundLeft = currentInnerSidebarWidth + 1; // Keep divider line visible by offsetting fills
            ScrollbarHelper.Metrics scrollMetrics = getCategoryScrollMetrics();
            int nodeBackgroundRight = scrollMetrics != null && scrollMetrics.maxScroll() > 0
                ? scrollMetrics.trackLeft() - 2
                : totalWidth;
            int contentClipLeft = nodeBackgroundLeft;
            int contentClipRight = Math.min(totalWidth, contentTextRight + 2);
            if (contentClipRight <= contentClipLeft) {
                contentClipRight = contentClipLeft + 1;
            }

            context.enableScissor(contentClipLeft, contentTop, contentClipRight, contentBottom);
            
            // Category header
            int headerTextX = contentTextX;
            int headerTextY = contentY + 4;
            if (headerLines != null && !headerLines.isEmpty()) {
                for (String line : headerLines) {
                    context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(line),
                        headerTextX,
                        headerTextY,
                        selectedCategory.getColor()
                    );
                    headerTextY += headerLineHeight;
                }
            }

            contentY += headerHeight;
            
            hoveredNodeType = null;

            if (selectedCategory == NodeCategory.CUSTOM) {
                if (customNodeRows != null) {
                    for (NodeRowInfo customNode : customNodeRows) {
                        int rowHeight = customNode.height();
                    if (contentY >= sidebarBottom) {
                        break;
                    }
                    boolean nodeHovered = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight
                        && effectiveMouseY >= contentY && effectiveMouseY < contentY + rowHeight;
                    if (nodeHovered) {
                        hoveredCustomNode = customNode.customNode();
                        context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + rowHeight, UITheme.BACKGROUND_TERTIARY);
                    }

                    int indicatorSize = 12;
                    int indicatorX = currentInnerSidebarWidth + 8;
                    int indicatorY = contentY + Math.max(3, (rowHeight - indicatorSize) / 2);
                    UIStyleHelper.drawBeveledPanel(context, indicatorX, indicatorY, indicatorSize, indicatorSize,
                        NodeType.TEMPLATE.getColor(), UITheme.BORDER_SUBTLE, UITheme.PANEL_INNER_BORDER);

                        int lineY = contentY + 4;
                        for (String line : customNode.lines()) {
                            context.drawTextWithShadow(
                                textRenderer,
                                Text.literal(line),
                                indicatorX + indicatorSize + 4,
                                lineY,
                                UITheme.TEXT_PRIMARY
                            );
                            lineY += nodeLineHeight;
                        }
                        contentY += rowHeight;
                    }
                }
            } else if (hasGroupedContent(selectedCategory)) {
                outer:
                if (groupHeaders != null) {
                    for (GroupHeaderInfo groupInfo : groupHeaders) {
                        NodeGroup group = groupInfo.getGroup();

                        if (contentY >= sidebarBottom) {
                            break;
                        }

                        int groupTextY = contentY + 2;
                        for (String line : groupInfo.getLines()) {
                            context.drawTextWithShadow(
                                textRenderer,
                                Text.literal(line),
                                contentTextX,
                                groupTextY,
                                getSidebarGroupHeaderColor(selectedCategory)
                            );
                            groupTextY += groupLineHeight;
                        }

                        contentY += groupInfo.getHeight();

                        List<NodeRowInfo> groupNodes = groupInfo.getNodeRows();
                        for (int nodeIndex = 0; nodeIndex < groupNodes.size(); nodeIndex++) {
                            NodeRowInfo row = groupNodes.get(nodeIndex);
                            NodeType nodeType = row.nodeType();
                            int rowHeight = row.height();
                            if (contentY >= sidebarBottom) {
                                break outer;
                            }

                            boolean nodeHovered = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight &&
                                                effectiveMouseY >= contentY && effectiveMouseY < contentY + rowHeight;

                            if (nodeHovered) {
                                hoveredNodeType = nodeType;
                                context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + rowHeight, UITheme.BACKGROUND_TERTIARY);
                            }

                            int indicatorSize = 12;
                            int indicatorX = currentInnerSidebarWidth + 8;
                            int indicatorY = contentY + Math.max(3, (rowHeight - indicatorSize) / 2);
                            UIStyleHelper.drawBeveledPanel(
                                context,
                                indicatorX,
                                indicatorY,
                                indicatorSize,
                                indicatorSize,
                                getSidebarNodeIndicatorColor(selectedCategory, nodeType, nodeIndex),
                                UITheme.BORDER_SUBTLE,
                                UITheme.PANEL_INNER_BORDER
                            );

                            int lineY = contentY + 4;
                            for (String line : row.lines()) {
                                context.drawTextWithShadow(
                                    textRenderer,
                                    Text.literal(line),
                                    indicatorX + indicatorSize + 4,
                                    lineY,
                                    getSidebarNodeTextColor(selectedCategory, nodeHovered)
                                );
                                lineY += nodeLineHeight;
                            }

                            contentY += rowHeight;
                        }
                    }
                }
            } else {
                // Render nodes in selected category
                List<NodeRowInfo> nodes = buildNodeRowsForCategory(selectedCategory, textRenderer, nodeTextWidth, nodeLineHeight);
                if (nodes != null) {
                    for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
                        NodeRowInfo row = nodes.get(nodeIndex);
                        NodeType nodeType = row.nodeType();
                        int rowHeight = row.height();
                        if (contentY >= sidebarBottom) break; // Don't render beyond sidebar
                        
                        boolean nodeHovered = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight &&
                                            effectiveMouseY >= contentY && effectiveMouseY < contentY + rowHeight;

                        if (nodeHovered) {
                            hoveredNodeType = nodeType;
                            context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + rowHeight, UITheme.BACKGROUND_TERTIARY);
                        }

                        int indicatorSize = 12;
                        int indicatorX = currentInnerSidebarWidth + 8; // Align with category title
                        int indicatorY = contentY + Math.max(3, (rowHeight - indicatorSize) / 2);
                        UIStyleHelper.drawBeveledPanel(
                            context,
                            indicatorX,
                            indicatorY,
                            indicatorSize,
                            indicatorSize,
                            getSidebarNodeIndicatorColor(selectedCategory, nodeType, nodeIndex),
                            UITheme.BORDER_SUBTLE,
                            UITheme.PANEL_INNER_BORDER
                        );

                        int lineY = contentY + 4;
                        for (String line : row.lines()) {
                            context.drawTextWithShadow(
                                textRenderer,
                                Text.literal(line),
                                indicatorX + indicatorSize + 4, // Position after the indicator with some spacing
                                lineY,
                                getSidebarNodeTextColor(selectedCategory, nodeHovered)
                            );
                            lineY += nodeLineHeight;
                        }
                        
                        contentY += rowHeight;
                    }
                }
            }
            context.disableScissor();
            ScrollbarHelper.renderCutoffDividers(
                context,
                nodeBackgroundLeft,
                totalWidth - 1,
                contentTop,
                contentBottom,
                scrollOffset,
                maxScroll,
                UITheme.BORDER_SUBTLE
            );
            renderCategoryScrollbar(context, totalWidth, contentTop, contentBottom);
            DrawContextBridge.flush(context);
        }

        // Render addon category content panel when an addon tab is selected (D-05)
        if (selectedAddonCategory != null && openProgress > 0.001f) {
            int contentTop = sidebarStartY + PADDING;
            int contentBottom = sidebarStartY + sidebarHeight - PADDING;
            int contentY = contentTop - scrollOffset;
            int sidebarBottom = sidebarStartY + sidebarHeight;
            int nodeBackgroundLeft = currentInnerSidebarWidth + 1;
            ScrollbarHelper.Metrics addonScrollMetrics = getCategoryScrollMetrics();
            int nodeBackgroundRight = addonScrollMetrics != null && addonScrollMetrics.maxScroll() > 0
                ? addonScrollMetrics.trackLeft() - 2
                : totalWidth;
            int contentClipLeft = nodeBackgroundLeft;
            int contentClipRight = Math.min(totalWidth, contentTextRight + 2);
            if (contentClipRight <= contentClipLeft) {
                contentClipRight = contentClipLeft + 1;
            }

            context.enableScissor(contentClipLeft, contentTop, contentClipRight, contentBottom);

            // Category header
            if (headerLines != null && !headerLines.isEmpty()) {
                int headerTextY = contentY + 4;
                for (String line : headerLines) {
                    context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(line),
                        contentTextX,
                        headerTextY,
                        selectedAddonCategory.getColor()
                    );
                    headerTextY += headerLineHeight;
                }
            }
            contentY += headerHeight;

            // Reset addon hover at start of addon content pass (mirrors hoveredNodeType = null at line 758)
            hoveredAddonDefinition = null;

            // Render addon node entry rows with hit-test
            List<AddonNodeDefinition> addonDefs = addonCategoryNodes.get(selectedAddonCategory);
            if (addonDefs != null) {
                for (AddonNodeDefinition def : addonDefs) {
                    List<String> lines = wrapText(def.getDisplayName(), textRenderer, nodeTextWidth);
                    int rowHeight = Math.max(NODE_HEIGHT, lines.size() * nodeLineHeight + PADDING);

                    if (contentY >= sidebarBottom) {
                        break;
                    }

                    boolean nodeHovered = effectiveMouseX >= nodeBackgroundLeft && effectiveMouseX <= nodeBackgroundRight
                        && effectiveMouseY >= contentY && effectiveMouseY < contentY + rowHeight;

                    if (nodeHovered) {
                        hoveredAddonDefinition = def;
                        context.fill(nodeBackgroundLeft, contentY, nodeBackgroundRight, contentY + rowHeight, UITheme.BACKGROUND_TERTIARY);
                    }

                    int indicatorSize = 12;
                    int indicatorX = currentInnerSidebarWidth + 8;
                    int indicatorY = contentY + Math.max(3, (rowHeight - indicatorSize) / 2);
                    UIStyleHelper.drawBeveledPanel(context, indicatorX, indicatorY, indicatorSize, indicatorSize,
                        def.getColor(), UITheme.BORDER_SUBTLE, UITheme.PANEL_INNER_BORDER);

                    int lineY = contentY + 4;
                    for (String line : lines) {
                        context.drawTextWithShadow(
                            textRenderer,
                            Text.literal(line),
                            indicatorX + indicatorSize + 4,
                            lineY,
                            nodeHovered ? UITheme.TEXT_HEADER : UITheme.TEXT_PRIMARY
                        );
                        lineY += nodeLineHeight;
                    }

                    contentY += rowHeight;
                }
            }

            context.disableScissor();
            renderCategoryScrollbar(context, totalWidth, contentTop, contentBottom);
            DrawContextBridge.flush(context);
        }

        if (interactionsEnabled && showTooltips && hoveredCustomNode != null) {
            TooltipRenderer.render(
                context,
                textRenderer,
                hoveredCustomNode.getDescription(),
                mouseX,
                mouseY,
                MinecraftClient.getInstance().getWindow().getScaledWidth(),
                MinecraftClient.getInstance().getWindow().getScaledHeight()
            );
        } else if (interactionsEnabled && showTooltips && hoveredNodeType != null) {
            TooltipRenderer.render(
                context,
                textRenderer,
                hoveredNodeType.getDescription(),
                mouseX,
                mouseY,
                MinecraftClient.getInstance().getWindow().getScaledWidth(),
                MinecraftClient.getInstance().getWindow().getScaledHeight()
            );
        }
        
        // Reset hover states if mouse is not in sidebar
        if (effectiveMouseX < 0 || effectiveMouseX > currentRenderedWidth) {
            hoveredNodeType = null;
            hoveredCustomNode = null;
            hoveredCategory = null;
            hoveredAddonDefinition = null;
            hoveredAddonCategory = null;
        }
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < 0 || mouseX > currentRenderedWidth) {
            return false;
        }

        if (button == 0) { // Left click
            ScrollbarHelper.Metrics scrollMetrics = getCategoryScrollMetrics();
            if (scrollMetrics != null
                && mouseX >= scrollMetrics.trackLeft() - 3 && mouseX <= scrollMetrics.trackRight() + 3
                && mouseY >= scrollMetrics.trackTop() && mouseY <= scrollMetrics.trackBottom()) {
                scrollDragging = true;
                scrollDragOffset = (int) mouseY - scrollMetrics.thumbTop();
                return true;
            }
            // Check tab clicks — built-in categories
            if (mouseX >= 0 && mouseX <= currentInnerSidebarWidth && hoveredCategory != null) {
                if (selectedCategory != null && hoveredCategory == selectedCategory) {
                    selectedCategory = null;
                } else {
                    selectedCategory = hoveredCategory;
                    selectedAddonCategory = null; // collapse any open addon category
                }
                // Clear any hovered node when switching or collapsing categories
                hoveredNodeType = null;
                hoveredAddonDefinition = null;
                // Reset scroll to top when changing categories
                scrollOffset = 0;
                calculateMaxScroll(currentSidebarHeight);
                return true;
            }
            // Check tab clicks — addon categories
            if (mouseX >= 0 && mouseX <= currentInnerSidebarWidth && hoveredAddonCategory != null) {
                if (selectedAddonCategory != null && hoveredAddonCategory == selectedAddonCategory) {
                    selectedAddonCategory = null;
                } else {
                    selectedAddonCategory = hoveredAddonCategory;
                    selectedCategory = null; // collapse any open built-in category
                }
                // Clear any hovered node/addon when switching or collapsing addon categories
                hoveredNodeType = null;
                hoveredAddonDefinition = null;
                // Reset scroll to top when changing categories
                scrollOffset = 0;
                calculateMaxScroll(currentSidebarHeight);
                return true;
            }

            // Check node clicks for dragging
            if (hoveredNodeType != null || hoveredCustomNode != null || hoveredAddonDefinition != null) {
                return true; // Signal that dragging should start
            }
        }
        
        return false;
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // GAP-A: wheel over the icon-bar column scrolls the icon strip, not the content panel.
        if (iconBarMaxScroll > 0 && mouseX >= 0 && mouseX <= currentInnerSidebarWidth) {
            iconBarScrollOffset = ScrollbarHelper.applyWheel(iconBarScrollOffset, amount, 20, iconBarMaxScroll);
            return true;
        }
        if (mouseX >= 0 && mouseX <= currentRenderedWidth) {
            scrollOffset = ScrollbarHelper.applyWheel(scrollOffset, amount, 20, maxScroll);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || !scrollDragging) {
            return false;
        }
        ScrollbarHelper.Metrics scrollMetrics = getCategoryScrollMetrics();
        if (scrollMetrics == null) {
            return true;
        }
        scrollOffset = ScrollbarHelper.scrollFromThumb(scrollMetrics, (int) mouseY - scrollDragOffset);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button == 0 && scrollDragging) {
            scrollDragging = false;
            return true;
        }
        return false;
    }
    
    public boolean isHoveringNode() {
        return hoveredNodeType != null || hoveredCustomNode != null || hoveredAddonDefinition != null;
    }

    public Node createNodeFromSidebar(int x, int y) {
        if (hoveredCustomNode != null) {
            return hoveredCustomNode.createNode(x, y);
        }
        if (hoveredAddonDefinition != null) {
            // Build an ADDON node via the String constructor (Task 1) — routes through
            // existing previewSidebarDrag(Node,...) / handleSidebarDrop(Node,...) overloads
            return new Node(hoveredAddonDefinition.getId(), x, y);
        }
        if (hoveredNodeType != null) {
            return new Node(hoveredNodeType, x, y);
        }
        return null;
    }
    
    public int getWidth() {
        return currentRenderedWidth;
    }

    /**
     * Returns the width of the sidebar when no category is expanded.
     * This is used for layout calculations that should remain stable even
     * when a category is opened (which visually overlays the workspace).
     */
    public static int getCollapsedWidth() {
        return INNER_SIDEBAR_WIDTH;
    }

    /**
     * Returns the width currently rendered (including category open animation).
     */
    public int getRenderedWidth() {
        return currentRenderedWidth;
    }

    /**
     * Returns true if the specified category has any nodes.
     */
    public boolean hasNodesInCategory(NodeCategory category) {
        if (category == NodeCategory.CUSTOM) {
            return !customNodes.isEmpty();
        }
        List<NodeType> nodes = categoryNodes.get(category);
        return nodes != null && !nodes.isEmpty();
    }

    /**
     * Returns the list of nodes for the specified category (non-grouped).
     */
    public List<NodeType> getNodesForCategory(NodeCategory category) {
        if (category == NodeCategory.CUSTOM) {
            return java.util.Collections.emptyList();
        }
        List<NodeType> nodes = categoryNodes.get(category);
        return nodes != null ? nodes : java.util.Collections.emptyList();
    }

    /**
     * Returns the grouped nodes for the specified category (SENSORS, PARAMETERS).
     * Returns null if the category doesn't have groups.
     */
    public List<NodeGroup> getGroupedNodesForCategory(NodeCategory category) {
        return groupedCategoryNodes.get(category);
    }

    public boolean isHoveringCustomNode() {
        return hoveredCustomNode != null;
    }

    public NodeType getHoveredNodeType() {
        return hoveredCustomNode != null ? NodeType.CUSTOM_NODE : hoveredNodeType;
    }

    private static final class CustomNodeEntry {
        private final String presetName;

        private CustomNodeEntry(String presetName) {
            this.presetName = presetName;
        }

        private String getLabel() {
            return presetName;
        }

        private String getDescription() {
            return "Reusable custom node from preset \"" + presetName + "\".";
        }

        private Node createNode(int x, int y) {
            Node node = new Node(NodeType.CUSTOM_NODE, x, y);
            if (node.getParameter("Preset") != null) {
                node.getParameter("Preset").setStringValue(presetName);
            }
            NodeGraphData data = NodeGraphPersistence.loadNodeGraphForPreset(presetName);
            NodeGraphData.CustomNodeDefinition definition = NodeGraphPersistence.resolveCustomNodeDefinition(presetName, data);
            node.setTemplateName(definition != null ? definition.getName() : presetName);
            node.setTemplateVersion(definition != null && definition.getVersion() != null ? definition.getVersion() : 0);
            node.setTemplateGraphData(data);
            node.recalculateDimensions();
            return node;
        }
    }
    
    /**
     * Darkens a color by the specified factor
     */
    private int darkenColor(int color, float factor) {
        return AnimationHelper.darken(color, factor);
    }

    /**
     * Lightens a color by the specified factor
     */
    private int lightenColor(int color, float factor) {
        return AnimationHelper.brighten(color, factor);
    }

    private int getSidebarCategoryAccent(NodeCategory category) {
        return category != null ? category.getColor() : UITheme.BORDER_DEFAULT;
    }

    private int getSidebarGroupHeaderColor(NodeCategory category) {
        return AnimationHelper.lerpColor(UITheme.TEXT_TERTIARY, getSidebarCategoryAccent(category), 0.55f);
    }

    private int getSidebarNodeIndicatorColor(NodeCategory category, NodeType nodeType, int indexInGroup) {
        if (nodeType == null) {
            return getSidebarCategoryAccent(category);
        }
        if (indexInGroup == 0) {
            return nodeType.getColor();
        }
        return getSidebarCategoryAccent(category);
    }

    private int getSidebarNodeTextColor(NodeCategory category, boolean hovered) {
        int baseColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, getSidebarCategoryAccent(category), 0.14f);
        return hovered ? AnimationHelper.lerpColor(baseColor, UITheme.TEXT_HEADER, 0.18f) : baseColor;
    }

    private List<String> wrapText(String text, TextRenderer textRenderer, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        if (maxWidth <= 0) {
            lines.add(text);
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (currentLine.length() > 0) {
                String candidate = currentLine + " " + word;
                if (textRenderer.getWidth(candidate) <= maxWidth) {
                    currentLine.append(" ").append(word);
                    continue;
                }
            }

            if (textRenderer.getWidth(word) <= maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                String remaining = word;
                while (!remaining.isEmpty()) {
                    int breakIndex = findBreakIndex(remaining, textRenderer, maxWidth);
                    String part = remaining.substring(0, breakIndex);
                    lines.add(part);
                    remaining = remaining.substring(breakIndex);
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        if (lines.isEmpty()) {
            lines.add(text);
        }

        return lines;
    }

    private int findBreakIndex(String text, TextRenderer textRenderer, int maxWidth) {
        if (text.isEmpty() || maxWidth <= 0) {
            return Math.max(1, text.length());
        }

        int breakIndex = 1;
        while (breakIndex <= text.length() &&
            textRenderer.getWidth(text.substring(0, breakIndex)) <= maxWidth) {
            breakIndex++;
        }

        if (breakIndex > text.length()) {
            return text.length();
        }

        return Math.max(1, breakIndex - 1);
    }

    private List<NodeRowInfo> buildNodeRowsForCategory(NodeCategory category, TextRenderer textRenderer, int maxWidth, int lineHeight) {
        List<NodeType> nodes = categoryNodes.get(category);
        if (nodes == null || textRenderer == null) {
            return java.util.Collections.emptyList();
        }
        return buildNodeRows(nodes, textRenderer, maxWidth, lineHeight);
    }

    private List<NodeRowInfo> buildCustomNodeRows(TextRenderer textRenderer, int maxWidth, int lineHeight) {
        if (textRenderer == null) {
            return java.util.Collections.emptyList();
        }
        List<NodeRowInfo> rows = new ArrayList<>();
        for (CustomNodeEntry customNode : customNodes) {
            List<String> lines = wrapText(customNode.getLabel(), textRenderer, maxWidth);
            rows.add(new NodeRowInfo(null, customNode, lines, getWrappedNodeRowHeight(lines.size(), lineHeight)));
        }
        return rows;
    }

    private List<NodeRowInfo> buildNodeRows(List<NodeType> nodes, TextRenderer textRenderer, int maxWidth, int lineHeight) {
        List<NodeRowInfo> rows = new ArrayList<>();
        for (NodeType nodeType : nodes) {
            List<String> lines = wrapText(nodeType.getDisplayName(), textRenderer, maxWidth);
            rows.add(new NodeRowInfo(nodeType, null, lines, getWrappedNodeRowHeight(lines.size(), lineHeight)));
        }
        return rows;
    }

    private int getWrappedNodeRowHeight(int lineCount, int lineHeight) {
        return Math.max(NODE_HEIGHT, Math.max(1, lineCount) * lineHeight + 7);
    }

    private void renderCategoryScrollbar(DrawContext context, int totalWidth, int contentTop, int contentBottom) {
        if (maxScroll <= 0 || contentBottom <= contentTop) {
            return;
        }
        ScrollbarHelper.renderSettingsStyle(
            context,
            ScrollbarHelper.metrics(totalWidth - SCROLLBAR_MARGIN - UITheme.SCROLLBAR_WIDTH, contentTop, UITheme.SCROLLBAR_WIDTH,
                Math.max(1, contentBottom - contentTop), maxScroll, scrollOffset, SCROLLBAR_MIN_KNOB_HEIGHT),
            UITheme.BACKGROUND_SIDEBAR,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_DEFAULT
        );
    }

    /**
     * GAP-A: draw a thin scrollbar in the icon-bar column when the category tabs overflow
     * the available vertical space. Reuses the content-panel scrollbar visual style.
     */
    private void renderIconBarScrollbar(DrawContext context) {
        if (iconBarMaxScroll <= 0 || iconBarViewportHeight <= 0) {
            return;
        }
        ScrollbarHelper.renderSettingsStyle(
            context,
            getIconBarScrollMetrics(),
            UITheme.BACKGROUND_SIDEBAR,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_DEFAULT
        );
    }

    /**
     * GAP-A: scrollbar metrics for the icon bar. The track sits at the right edge of the
     * inner icon strip so it never overlaps the tabs.
     */
    private ScrollbarHelper.Metrics getIconBarScrollMetrics() {
        int trackLeft = currentInnerSidebarWidth - SCROLLBAR_MARGIN - UITheme.SCROLLBAR_WIDTH;
        return ScrollbarHelper.metrics(trackLeft, iconBarViewportTop, UITheme.SCROLLBAR_WIDTH,
            Math.max(1, iconBarViewportHeight), iconBarMaxScroll, iconBarScrollOffset, SCROLLBAR_MIN_KNOB_HEIGHT);
    }

    private ScrollbarHelper.Metrics getCategoryScrollMetrics() {
        if ((selectedCategory == null && selectedAddonCategory == null) || maxScroll <= 0) {
            return null;
        }
        int contentTop = currentSidebarStartY + PADDING;
        int contentBottom = currentSidebarStartY + currentSidebarHeight - PADDING;
        int viewportHeight = Math.max(1, contentBottom - contentTop);
        int trackLeft = currentRenderedWidth - SCROLLBAR_MARGIN - UITheme.SCROLLBAR_WIDTH;
        return ScrollbarHelper.metrics(trackLeft, contentTop, UITheme.SCROLLBAR_WIDTH, viewportHeight, maxScroll, scrollOffset, SCROLLBAR_MIN_KNOB_HEIGHT);
    }

    private static final class GroupHeaderInfo {
        private final NodeGroup group;
        private final List<String> lines;
        private final int height;
        private final List<NodeRowInfo> nodeRows;

        private GroupHeaderInfo(NodeGroup group, List<String> lines, int height, List<NodeRowInfo> nodeRows) {
            this.group = group;
            this.lines = lines;
            this.height = height;
            this.nodeRows = nodeRows;
        }

        public NodeGroup getGroup() {
            return group;
        }

        public List<String> getLines() {
            return lines;
        }

        public int getHeight() {
            return height;
        }

        public List<NodeRowInfo> getNodeRows() {
            return nodeRows;
        }
    }

    private record NodeRowInfo(NodeType nodeType, CustomNodeEntry customNode, List<String> lines, int height) {}

    public static class NodeGroup {
        private final String titleKey;
        private final List<NodeType> nodes;

        NodeGroup(String titleKey, List<NodeType> nodeTypes) {
            this.titleKey = titleKey;
            this.nodes = new ArrayList<>();
            if (nodeTypes != null) {
                this.nodes.addAll(nodeTypes);
            }
        }

        public String getTitle() {
            return Text.translatable(titleKey).getString();
        }

        public List<NodeType> getNodes() {
            return nodes;
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }
    }
}
