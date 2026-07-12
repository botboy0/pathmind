package com.pathmind.execution;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for {@link FailureDetail} — the structured classification recorded
 * at opted-in {@code NodeExecutionCompletion.fail} sites. Pins the v1 status
 * vocabulary strings exactly (they are the addon-visible wire values).
 */
class FailureDetailTest {

    @Test
    void missingResourceCarriesTheMissingIdList() {
        FailureDetail detail = FailureDetail.missingResource(List.of("minecraft:stick", "minecraft:oak_planks"));

        assertEquals("missing_resource", detail.getStatus());
        assertEquals(List.of("minecraft:stick", "minecraft:oak_planks"), detail.getFields().get("missing"));
    }

    @Test
    void missingListIsCopiedNotAliased() {
        List<String> source = new ArrayList<>(List.of("minecraft:stick"));
        FailureDetail detail = FailureDetail.missingResource(source);

        source.add("minecraft:diamond");

        assertEquals(List.of("minecraft:stick"), detail.getFields().get("missing"));
    }

    @Test
    void statusOnlyFactoriesUseTheV1VocabularyStrings() {
        assertEquals("precondition", FailureDetail.precondition().getStatus());
        assertEquals("transient", FailureDetail.transientFailure().getStatus());
        assertEquals("unsupported", FailureDetail.unsupported().getStatus());
        assertEquals("not_found", FailureDetail.notFound().getStatus());
    }

    @Test
    void statusOnlyFactoriesHaveEmptyNonNullFields() {
        for (FailureDetail detail : new FailureDetail[]{
                FailureDetail.precondition(), FailureDetail.transientFailure(),
                FailureDetail.unsupported(), FailureDetail.notFound()}) {
            assertNotNull(detail.getFields(), detail.getStatus());
            assertTrue(detail.getFields().isEmpty(), detail.getStatus());
        }
    }
}
