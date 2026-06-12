# Testing Patterns

**Analysis Date:** 2026-06-12

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) 5.11.4
- Config: `common/build.gradle.kts` declares `testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")`
- Launch provider: `org.junit.platform:junit-platform-launcher`

**Assertion Library:**
- JUnit 5 built-in assertions (org.junit.jupiter.api.Assertions)

**Run Commands:**
```bash
./gradlew test                 # Run all tests
./gradlew test --continuous   # Watch mode (implicit from Gradle)
./gradlew test --info         # Verbose output
```

Configuration in `common/build.gradle.kts`:
```kotlin
tasks.test {
    useJUnitPlatform()
}
```

## Test File Organization

**Location:**
- Co-located in parallel source tree: `common/src/test/java/com/pathmind/...`
- Mirrors main source structure: `common/src/main/java/com/pathmind/...`
- 20+ test files found covering core functionality

**Naming:**
- ClassName + Test suffix (e.g., `NodeAttachmentsTest.java`, `NodeGraphPersistenceTest.java`)
- Test class located in same package as source: `com.pathmind.nodes.NodeAttachmentsTest` tests `com.pathmind.nodes.Node`

**Structure:**
```
common/src/test/java/
├── com/pathmind/data/
│   └── NodeGraphPersistenceTest.java
├── com/pathmind/execution/
│   └── ExecutionManagerValidationTest.java
├── com/pathmind/nodes/
│   ├── NodeAttachmentsTest.java
│   ├── NodeCraftTest.java
│   ├── NodeGeometryTest.java
│   ├── NodeHotbarTest.java
│   └── ... (20 total)
└── com/pathmind/util/
    └── (if applicable)
```

## Test Structure

**Suite Organization:**
```java
class NodeAttachmentsTest {
    @Test
    void parameterAttachmentRelationshipsAreStoredInNodeAttachments() {
        // Arrange
        Node look = new Node(NodeType.LOOK, 0, 0);
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);

        // Act
        assertTrue(look.attachParameter(amount, 0));

        // Assert
        assertSame(amount, look.getAttachedParameter(0));
        assertSame(look, amount.getParentParameterHost());
        assertEquals(0, amount.getParentParameterSlotIndex());
        assertTrue(look.getAttachedParameters().containsKey(0));
    }
}
```

**Patterns:**
- Arrange-Act-Assert (AAA) pattern used throughout
- Method names describe test intent (not generic names like "test1")
- No shared setup/teardown observed (simple unit test scope)
- Direct Node/NodeConnection instantiation; no test fixtures

**Assertions:**
```java
// Equality checks
assertEquals(expectedValue, actualValue);
assertEquals(1, definition.getInputs().size());

// Reference checks
assertSame(expectedObject, actualObject);
assertSame(amount, look.getAttachedParameter(0));

// Null checks
assertNull(firstAmount.getParentParameterHost());
assertNotNull(definition);

// Boolean checks
assertTrue(look.attachParameter(amount, 0));
assertFalse(removedConnection);
```

## Mocking

**Framework:** No mocking framework detected (Mockito not in dependencies)

**Patterns:**
- Direct object instantiation: `new Node(NodeType.LOOK, 0, 0)`
- Hand-rolled test doubles/stubs: Minimal
- No spy/mock behavior; tests work with real implementations

**What to Mock:**
- External Minecraft API calls would require mocks but none observed in tests
- Baritone integration mocked via reflection checks, not object mocks

**What NOT to Mock:**
- Core business logic classes (Node, NodeConnection, etc.)
- Persistence layer — real file I/O tested with @TempDir
- Graph validation and execution logic

## Fixtures and Factories

**Test Data:**
```java
// Example: Create test nodes and connections
Node start = new Node(NodeType.START, 0, 0);
Node setVariable = new Node(NodeType.SET_VARIABLE, 100, 0);
Node variable = new Node(NodeType.VARIABLE, 0, 0);
variable.getParameter("Variable").setStringValue("variable");

// Configure parameters
amount.getParameter("Amount").setStringValue("1.0");

// Build graph
List.of(start, setVariable, variable, amount)
List.of(new NodeConnection(start, setVariable, 0, 0))
```

**Location:**
- No separate fixture/factory files; test data created inline in test methods
- Minimal reuse; each test builds its own graph

**Temporary Resources:**
- JUnit 5 `@TempDir` annotation used for file I/O tests:
  ```java
  @TempDir
  Path tempDir;
  ```

## Coverage

**Requirements:** No enforced target (no Jacoco/coverage plugin configured)

**View Coverage:**
- No command found; coverage not integrated into build

## Test Types

**Unit Tests:**
- Scope: Single node behavior, parameter attachment, graph persistence
- Approach: Direct instantiation, assertion on state mutations
- Examples:
  - `NodeAttachmentsTest`: Tests node attachment relationships
  - `NodeCraftTest`: Tests craft quantity calculations
  - `NodeGeometryTest`: Tests geometry calculations
  - `RelativeInputSupportTest`: Tests relative input handling

**Integration Tests:**
- Scope: Graph loading/saving, custom node definitions
- Approach: Real file I/O via `@TempDir`, full graph reconstruction
- Examples:
  - `NodeGraphPersistenceTest`: Tests serialization round-trips, custom node resolution
  - `ExecutionManagerValidationTest`: Tests graph validation logic

**E2E Tests:**
- Framework: Not used
- Rationale: Game-specific functionality (player interaction, rendering) requires mod environment

## Common Patterns

**Unit Test Example:**
```java
@Test
void craftQuantityEvaluatesArithmeticAmountExpression() {
    // Arrange
    Node craft = new Node(NodeType.CRAFT, 0, 0);

    // Act
    craft.setParameterValueAndPropagate("Amount", "4*64/3");

    // Assert
    assertEquals(85, craft.getRequestedCraftQuantity());
}
```

**Integration Test Example:**
```java
@Test
void customNodeDefinitionDoesNotExposePureSetVariableTargetAsInput() {
    // Arrange
    Node start = new Node(NodeType.START, 0, 0);
    Node setVariable = new Node(NodeType.SET_VARIABLE, 100, 0);
    Node variable = new Node(NodeType.VARIABLE, 0, 0);
    variable.getParameter("Variable").setStringValue("variable");
    Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);
    amount.getParameter("Amount").setStringValue("1.0");

    assertTrue(setVariable.attachParameter(variable, 0));
    assertTrue(setVariable.attachParameter(amount, 1));

    // Act
    NodeGraphData.CustomNodeDefinition definition = 
        NodeGraphPersistence.resolveCustomNodeDefinition(
            "variable",
            List.of(start, setVariable, variable, amount),
            List.of(new NodeConnection(start, setVariable, 0, 0))
        );

    // Assert
    assertNotNull(definition);
    assertTrue(definition.getInputs().isEmpty());
}
```

## Test File Locations

Test files cover these core modules:

- `common/src/test/java/com/pathmind/data/NodeGraphPersistenceTest.java` — Persistence layer
- `common/src/test/java/com/pathmind/execution/ExecutionManagerValidationTest.java` — Execution validation
- `common/src/test/java/com/pathmind/nodes/NodeAttachmentsTest.java` — Node attachment relationships
- `common/src/test/java/com/pathmind/nodes/NodeBehaviorDefinitionRegistryTest.java` — Behavior registry
- `common/src/test/java/com/pathmind/nodes/NodeCraftTest.java` — Crafting logic
- `common/src/test/java/com/pathmind/nodes/NodeDimensionCalculatorTest.java` — Dimension calculations
- `common/src/test/java/com/pathmind/nodes/NodeFallingTest.java` — Falling mechanics
- `common/src/test/java/com/pathmind/nodes/NodeGeometryTest.java` — Geometry calculations
- `common/src/test/java/com/pathmind/nodes/NodeHotbarTest.java` — Hotbar management
- `common/src/test/java/com/pathmind/nodes/NodeInteractionStateTest.java` — Interaction states
- `common/src/test/java/com/pathmind/nodes/NodeLayoutStateTest.java` — Layout state
- `common/src/test/java/com/pathmind/nodes/NodeRuntimeStateTest.java` — Runtime state
- `common/src/test/java/com/pathmind/nodes/RelativeInputSupportTest.java` — Relative input support

## Test Conventions

**Naming Conventions:**
- Test methods describe behavior: `void craftQuantityEvaluatesArithmeticAmountExpression()`
- Not test-focused verbs: Use business language, not "testX" or "shouldX"
- Consistent: Multiple conditions in single test name (e.g., `parameterAttachmentRelationshipsAreStoredInNodeAttachments`)

**Assertions per Test:**
- Single-responsibility: Most tests have 1-4 assertions
- Related assertions grouped: State checks before relationship checks

**Test Independence:**
- No shared state between tests
- Each test creates fresh Node/NodeConnection instances
- No test ordering dependencies detected

---

*Testing analysis: 2026-06-12*
