package com.pathmind.nodes;

import com.pathmind.ui.sidebar.Sidebar;
import com.pathmind.ui.search.NodeSearchEntry;
import com.pathmind.ui.search.NodeSearchMapper;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeTypeSearchLabelTest {

    private static final Pattern LANG_ENTRY_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    @Test
    void draggableNodeLabelsAreUniqueInEnglishSearch() throws Exception {
        Map<String, String> translations = loadEnglishTranslations();
        Map<String, NodeType> labelsByNormalizedName = new HashMap<>();
        Sidebar sidebar = new Sidebar(true, true);

        for (NodeType nodeType : NodeType.values()) {
            if (!sidebar.isNodeAvailable(nodeType)) {
                continue;
            }

            String label = getSearchLabel(nodeType, translations);
            String normalizedLabel = label.trim().toLowerCase(Locale.ROOT);
            NodeType existing = labelsByNormalizedName.putIfAbsent(normalizedLabel, nodeType);
            assertEquals(
                null,
                existing,
                () -> "Duplicate searchable node label \"" + label + "\" for " + existing + " and " + nodeType
            );
        }
    }

    @Test
    void unavailableDependencyNodesAreExcludedFromSidebarAvailability() {
        Sidebar sidebarWithoutDependencies = new Sidebar(false, false);

        for (NodeType nodeType : NodeType.values()) {
            if (nodeType == null) {
                continue;
            }
            if (nodeType.requiresBaritone() || nodeType.requiresUiUtils()) {
                assertEquals(
                    false,
                    sidebarWithoutDependencies.isNodeAvailable(nodeType),
                    () -> "Expected dependency-gated node to be hidden: " + nodeType
                );
            }
        }
    }

    @Test
    void addonDependencyMetadataControlsSidebarAvailabilityByRegistryId() {
        Identifier addonNode = Identifier.of("sidebartest", "baritone_scan");
        PathmindNodes.register(addonNode, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("sidebartest.node.baritone_scan")
            .descriptionKey("sidebartest.node.baritone_scan.desc")
            .color(0xFF335577)
            .requiresBaritone(true));

        assertFalse(new Sidebar(false, true).isNodeAvailable(addonNode));
        assertTrue(new Sidebar(true, true).isNodeAvailable(addonNode));
    }

    @Test
    void addonSidebarVisibilityMetadataControlsAvailabilityByRegistryId() {
        Identifier addonNode = Identifier.of("sidebartest", "hidden_helper");
        PathmindNodes.register(addonNode, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("sidebartest.node.hidden_helper")
            .descriptionKey("sidebartest.node.hidden_helper.desc")
            .color(0xFF775533)
            .draggableFromSidebar(false));

        assertFalse(new Sidebar(true, true).isNodeAvailable(addonNode));
    }

    @Test
    void addonNodeDefinitionsAppearInSidebarGroupsByRegistryId() {
        Identifier addonNode = Identifier.of("sidebartest", "visible_sensor");
        PathmindNodes.register(addonNode, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("sidebartest.node.visible_sensor")
            .descriptionKey("sidebartest.node.visible_sensor.desc")
            .color(0xFF337755));

        assertTrue(new Sidebar(true, true).getEntriesForCategory(NodeCategory.SENSORS).stream()
            .anyMatch(entry -> addonNode.equals(entry.id()) && entry.builtInType().isEmpty()));
    }

    @Test
    void addonSidebarEntriesExposeRegistryMetadataForRendering() {
        Identifier addonNode = Identifier.of("sidebartest", "renderable_sensor");
        PathmindNodes.register(addonNode, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("sidebartest.node.renderable_sensor")
            .descriptionKey("sidebartest.node.renderable_sensor.desc")
            .color(0xFF225588));

        Sidebar.SidebarNodeEntry entry = new Sidebar(true, true).getEntriesForCategory(NodeCategory.SENSORS).stream()
            .filter(candidate -> addonNode.equals(candidate.id()))
            .findFirst()
            .orElseThrow();

        assertEquals("sidebartest.node.renderable_sensor", entry.translationKey());
        assertEquals("sidebartest.node.renderable_sensor.desc", entry.descriptionKey());
        assertEquals(0xFF225588, entry.color());
    }

    @Test
    void sidebarSearchFindsBuiltInAndAddonEntriesFromMetadata() {
        Identifier addonNode = Identifier.of("sidebartest", "searchable_sensor");
        PathmindNodes.register(addonNode, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("sidebartest.node.searchable_sensor")
            .descriptionKey("sidebartest.node.searchable_sensor.desc")
            .color(0xFF114477));

        Sidebar sidebar = new Sidebar(true, true);

        Sidebar.SidebarSearchResult addonResult = sidebar.searchEntries("searchable_sensor").stream()
            .filter(result -> addonNode.equals(result.entry().id()))
            .findFirst()
            .orElseThrow();
        assertEquals(NodeCategory.SENSORS, addonResult.category());
        assertEquals(Optional.empty(), addonResult.entry().builtInType());

        assertTrue(sidebar.searchEntries("pathmind.node.type.start").stream()
            .anyMatch(result -> result.entry().builtInType()
                .filter(NodeType.START::equals)
                .isPresent()));
    }

    @Test
    void editorSearchMappingKeepsAddonResultsNonInstantiating() {
        Identifier addonNode = Identifier.of("sidebartest", "editor_search_sensor");
        PathmindNodes.register(addonNode, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("sidebartest.node.editor_search_sensor")
            .descriptionKey("sidebartest.node.editor_search_sensor.desc")
            .color(0xFF226699));

        Sidebar sidebar = new Sidebar(true, true);

        NodeSearchEntry addonEntry = NodeSearchMapper.map(sidebar.searchEntries("editor_search_sensor")).stream()
            .filter(result -> addonNode.equals(result.entry().id()))
            .findFirst()
            .orElseThrow();
        assertEquals(Optional.empty(), addonEntry.builtInType());
        assertEquals("sidebartest:editor_search_sensor", addonEntry.label());
        assertEquals("SENSORS", addonEntry.categoryLabel());

        NodeSearchEntry builtInEntry = NodeSearchMapper.map(sidebar.searchEntries("pathmind.node.type.start")).stream()
            .filter(result -> result.builtInType().filter(NodeType.START::equals).isPresent())
            .findFirst()
            .orElseThrow();
        assertEquals(NodeType.START.getDisplayName(), builtInEntry.label());
        assertTrue(builtInEntry.builtInType().isPresent());
    }

    private static String getSearchLabel(NodeType nodeType, Map<String, String> translations) throws Exception {
        if (nodeType == NodeType.DROP_SLOT) {
            return requireTranslation(translations, "pathmind.node.type.dropItem");
        }
        Field translationKeyField = NodeType.class.getDeclaredField("translationKey");
        translationKeyField.setAccessible(true);
        String translationKey = (String) translationKeyField.get(nodeType);
        return requireTranslation(translations, translationKey);
    }

    private static String requireTranslation(Map<String, String> translations, String key) {
        String value = translations.get(key);
        assertNotNull(value, () -> "Missing translation for " + key);
        return value;
    }

    private static Map<String, String> loadEnglishTranslations() throws IOException {
        Path langPath = Path.of("src/main/resources/assets/pathmind/lang/en_us.json");
        String json = Files.readString(langPath);
        Map<String, String> translations = new HashMap<>();
        Matcher matcher = LANG_ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            translations.put(matcher.group(1), matcher.group(2));
        }
        return translations;
    }
}
