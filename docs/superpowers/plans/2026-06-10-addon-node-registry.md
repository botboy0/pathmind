# Addon Node Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a public registry foundation so Pathmind addons can register namespaced node definitions without adding `NodeType` enum constants.

**Architecture:** Keep existing enum-backed nodes working by bootstrapping them into a new ID-backed registry. Expose a small `PathmindNodes.register(...)` API for addon code, and defer editor/sidebar and execution routing to later slices.

**Tech Stack:** Java 21, Fabric Loader identifiers and entrypoint conventions, JUnit 5.

---

### Task 1: ID-Backed Registry Foundation

**Files:**
- Create: `src/main/java/com/pathmind/nodes/PathmindNodeDefinition.java`
- Create: `src/main/java/com/pathmind/nodes/PathmindNodes.java`
- Test: `src/test/java/com/pathmind/nodes/PathmindNodesTest.java`

- [x] **Step 1: Write the failing test**

```java
package com.pathmind.nodes;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathmindNodesTest {
    @Test
    void builtInNodeTypesAreRegisteredByPersistenceId() {
        PathmindNodeDefinition definition = PathmindNodes.get(Identifier.of("pathmind", "start")).orElseThrow();

        assertEquals(Identifier.of("pathmind", "start"), definition.id());
        assertEquals(NodeType.START, definition.builtInType().orElseThrow());
        assertEquals(NodeCategory.FLOW, definition.category());
        assertFalse(definition.hasParameters());
    }

    @Test
    void addonNodeCanRegisterWithoutEnumConstant() {
        Identifier id = Identifier.of("myaddon", "scan_area");

        PathmindNodes.register(id, builder -> builder
            .category(NodeCategory.SENSORS)
            .translationKey("myaddon.node.scan_area")
            .descriptionKey("myaddon.node.scan_area.desc")
            .color(0xFF336699)
            .hasParameters(true));

        PathmindNodeDefinition definition = PathmindNodes.get(id).orElseThrow();
        assertEquals(id, definition.id());
        assertTrue(definition.builtInType().isEmpty());
        assertEquals(NodeCategory.SENSORS, definition.category());
        assertEquals("myaddon.node.scan_area", definition.translationKey());
        assertEquals("myaddon.node.scan_area.desc", definition.descriptionKey());
        assertEquals(0xFF336699, definition.color());
        assertTrue(definition.hasParameters());
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest`

Expected: compilation fails because `PathmindNodes` and `PathmindNodeDefinition` do not exist.

- [x] **Step 3: Write minimal implementation**

Create `PathmindNodeDefinition` as an immutable public value plus builder, and create `PathmindNodes` with a concurrent ID map, duplicate-ID protection, built-in bootstrap from `NodeType.values()`, and lookup/listing methods.

- [x] **Step 4: Run focused tests**

Run: `.\gradlew.bat test --tests com.pathmind.nodes.PathmindNodesTest`

Expected: pass.

- [x] **Step 5: Run adjacent tests**

Run: `.\gradlew.bat test --tests com.pathmind.nodes.NodeTypeDefinitionTest --tests com.pathmind.data.NodeGraphPersistenceTest`

Expected: pass.
