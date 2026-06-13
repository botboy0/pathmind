package com.pathmind.ui.sidebar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automated tests for {@link Sidebar#computeAddonContentHeight(int, List, int, int)}.
 *
 * <p>Tests exercise the pure static helper directly with integer line counts — no Minecraft
 * TextRenderer required. This locks the wrap-aware height formula under an automated gate
 * (GAP-1 / WR-04 fix).
 *
 * <p>Constants used in assertions:
 * <ul>
 *   <li>CATEGORY_HEADER_HEIGHT = 20 (Sidebar constant)</li>
 *   <li>NODE_HEIGHT = 18 (Sidebar constant)</li>
 *   <li>PADDING = 4 (Sidebar constant)</li>
 * </ul>
 */
class AddonSidebarScrollTest {

    // Mirror the Sidebar constants so tests are self-documenting
    private static final int CATEGORY_HEADER_HEIGHT = 20;
    private static final int NODE_HEIGHT = 18;
    private static final int PADDING = 4;

    // Concrete line heights used as inputs (typical in-game values)
    private static final int HEADER_LINE_HEIGHT = 11; // textRenderer.fontHeight(9) + CATEGORY_HEADER_LINE_SPACING(2)
    private static final int NODE_LINE_HEIGHT = 10;   // textRenderer.fontHeight(9) + NODE_LINE_SPACING(1)

    /**
     * Single-line header + three single-line rows:
     * header = max(20, max(1,1)*11) = max(20,11) = 20
     * each row = max(18, 1*10+4) = max(18,14) = 18
     * total = 20 + 3*18 = 74
     */
    @Test
    void singleLineHeader_threeRows_returnsExpectedHeight() {
        List<Integer> rowLineCounts = List.of(1, 1, 1);
        int expected = Math.max(CATEGORY_HEADER_HEIGHT, Math.max(1, 1) * HEADER_LINE_HEIGHT)
                + 3 * Math.max(NODE_HEIGHT, 1 * NODE_LINE_HEIGHT + PADDING);

        int result = Sidebar.computeAddonContentHeight(1, rowLineCounts, HEADER_LINE_HEIGHT, NODE_LINE_HEIGHT);

        assertEquals(expected, result,
                "Single-line header + 3 single-line rows must equal header-max + 3*NODE_HEIGHT");
    }

    /**
     * A row whose wrapped line count causes lines*nodeLineHeight+PADDING to exceed NODE_HEIGHT
     * must contribute the larger wrapped height.
     * e.g. 3 lines: 3*10+4=34 > 18 -> rowHeight=34
     */
    @Test
    void wrappedRow_contributesTallerHeight_notNodeHeight() {
        // 3 wrapped lines: 3*10+4=34 which exceeds NODE_HEIGHT=18
        List<Integer> rowLineCounts = List.of(3);
        int wrappedRowHeight = 3 * NODE_LINE_HEIGHT + PADDING; // 34

        int result = Sidebar.computeAddonContentHeight(1, rowLineCounts, HEADER_LINE_HEIGHT, NODE_LINE_HEIGHT);

        // header = max(20, max(1,1)*11) = 20
        // row = max(18, 34) = 34
        int expected = Math.max(CATEGORY_HEADER_HEIGHT, HEADER_LINE_HEIGHT) + Math.max(NODE_HEIGHT, wrappedRowHeight);
        assertEquals(expected, result,
                "Wrapped row (3 lines) must contribute 3*nodeLineHeight+PADDING, not NODE_HEIGHT");
        assertTrue(result > CATEGORY_HEADER_HEIGHT + NODE_HEIGHT,
                "Result must exceed the flat (non-wrap-aware) calculation");
    }

    /**
     * Empty row list returns just the header height.
     * header = max(20, max(1,1)*11) = max(20,11) = 20
     */
    @Test
    void emptyRowList_returnsHeaderHeightOnly() {
        List<Integer> rowLineCounts = new ArrayList<>();

        int result = Sidebar.computeAddonContentHeight(1, rowLineCounts, HEADER_LINE_HEIGHT, NODE_LINE_HEIGHT);

        int expectedHeader = Math.max(CATEGORY_HEADER_HEIGHT, Math.max(1, 1) * HEADER_LINE_HEIGHT);
        assertEquals(expectedHeader, result,
                "Empty row list must return only the header height");
    }

    /**
     * Multi-line header: headerLineCount=2 -> max(20, max(1,2)*11) = max(20,22) = 22.
     * Two single-line rows: 2*max(18,14)=2*18=36.
     * total = 22 + 36 = 58
     */
    @Test
    void multiLineHeader_twoRows_usesLargerHeaderHeight() {
        List<Integer> rowLineCounts = List.of(1, 1);
        // header = max(20, max(1,2)*11) = max(20, 22) = 22
        int expectedHeader = Math.max(CATEGORY_HEADER_HEIGHT, Math.max(1, 2) * HEADER_LINE_HEIGHT);
        // rows: 2 * max(18, 1*10+4) = 2 * 18 = 36
        int expectedRows = 2 * Math.max(NODE_HEIGHT, 1 * NODE_LINE_HEIGHT + PADDING);

        int result = Sidebar.computeAddonContentHeight(2, rowLineCounts, HEADER_LINE_HEIGHT, NODE_LINE_HEIGHT);

        assertEquals(expectedHeader + expectedRows, result,
                "Multi-line header (2 lines) must use max(CATEGORY_HEADER_HEIGHT, 2*headerLineHeight)");
    }
}
