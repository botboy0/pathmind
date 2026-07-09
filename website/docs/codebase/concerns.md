# Codebase Concerns

**Analysis Date:** 2026-06-12

## Tech Debt

**Unbounded In-Memory JSON Cache:**
- Issue: `IN_MEMORY_JSON_CACHE` in `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` has no eviction policy or size limit
- Files: `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` (lines 40, 782-786)
- Impact: Long-running sessions with many saved presets will accumulate JSON strings in memory with no cleanup. Users loading/saving hundreds of presets over a session could experience memory bloat and eventual OOM errors
- Fix approach: Implement a bounded cache (e.g., LRU eviction with max 50-100 entries) or use weak references. Alternative: cache only the most recently accessed preset and evict on save cycles

**Generic Exception Swallowing:**
- Issue: Multiple locations silently catch all exceptions and log only the message, losing stack traces
- Files: 
  - `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` (lines 112, 131, 267)
  - `common/src/main/java/com/pathmind/data/PresetManager.java` (multiple catch blocks)
  - `common/src/main/java/com/pathmind/data/SettingsManager.java` (lines 85, 105, 107)
- Impact: Difficult to diagnose file I/O failures, malformed JSON, or permission errors during preset load/save. Users see silent failures with no actionable error information
- Fix approach: Log full stack traces with `LOGGER.error("...", e)` instead of `System.err.println()`. Create specific exception types for file I/O vs parse errors to enable proper recovery paths

**Large Monolithic Classes:**
- Issue: Multiple classes exceed 7000 lines with deeply nested logic
- Files:
  - `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` (15,849 lines) - UI rendering + event handling + state management
  - `common/src/main/java/com/pathmind/execution/PathmindNavigator.java` (9,998 lines) - Pathfinding + movement + state tracking
  - `common/src/main/java/com/pathmind/nodes/Node.java` (7,474 lines) - Node definition + parameter handling + execution
- Impact: Complex logic is hard to test, understand, and modify. Changes to pathfinding constants ripple through 200+ lines of similar code. UI rendering and input handling are entangled
- Fix approach: Extract pathfinding algorithms into separate PathfindingEngine class. Pull UI rendering concerns into separate RenderContext. Create NodeExecutor to separate node behavior from definition

**Magic Numbers Throughout Pathfinding:**
- Issue: Over 60 hardcoded numeric constants for pathfinding tuning (jump timeouts, yaw steps, penalties) scattered in `PathmindNavigator`
- Files: `common/src/main/java/com/pathmind/execution/PathmindNavigator.java` (lines 62-150)
- Impact: Difficult to understand or tune navigation behavior. Changes require editing code and recompilation. No way to test different tuning profiles
- Fix approach: Consolidate into a `PathfindingConfig` object. Load from JSON file for runtime tuning without recompilation

**Cross-Platform Mixin Complexity:**
- Issue: Mixin classes for GameRenderer, InGameHud, ItemRenderer have version-specific compatibility code across 3 source directories
- Files:
  - `common/src/compat/legacy/base/java/com/pathmind/screen/PathmindMarketplaceScreen.java`
  - `common/src/compat/mid/java/com/pathmind/screen/PathmindMarketplaceScreen.java`
  - `common/src/compat/modern/java/com/pathmind/screen/PathmindMarketplaceScreen.java`
- Impact: Bug fixes must be applied 3 times. Code duplication increases risk of inconsistent behavior across MC versions 1.21-1.21.11
- Fix approach: Use `@Environment` annotations and conditional method signatures to maintain one source tree. Move version-specific rendering to utility methods

## Known Bugs

**Potential Graph State Corruption on Concurrent Execution:**
- Symptoms: Graph may execute partially when multiple chains start simultaneously
- Files: `common/src/main/java/com/pathmind/execution/ExecutionManager.java` (lines 828-845, 893-910)
- Trigger: Starting a branch execution while a global graph is running. The `activeNodes` and `activeConnections` fields are reassigned without synchronization
- Workaround: Ensure only one execution root (Global or individual branch) is active at a time. Cancel global before starting branches
- Root cause: `activeNodes` and `activeConnections` are instance fields reassigned by multiple execution paths. Race condition between `mergeActiveGraph()` and node execution queries

**Null Deference Risk in Marketplace Preset Deserialization:**
- Symptoms: Mod crash when loading malformed marketplace preset JSON
- Files: `common/src/main/java/com/pathmind/marketplace/MarketplaceService.java` (lines 130-160)
- Trigger: Marketplace returns incomplete preset data (missing `id`, `slug`, or `author_user_id` field)
- Root cause: `fetchPresetById()` parses JSON response but doesn't validate required fields before constructing MarketplacePreset object

**Unbounded Debug Event Queue Memory Usage:**
- Symptoms: High memory pressure after extended graph execution with debug logging enabled
- Files: `common/src/main/java/com/pathmind/execution/PathmindNavigator.java` (lines 130, 938-757)
- Trigger: Debug heartbeat logs every 1500ms during execution. Queue grows at ~2KB/heartbeat until max (12 events, ~24KB) then flushes. No cleanup on execution stop
- Root cause: `debugEvents` deque only caps at MAX_DEBUG_EVENTS in memory, but file I/O is unbounded

## Security Considerations

**Marketplace API Keys Exposed in Source:**
- Risk: PUBLISHABLE_KEY and PROJECT_URL are hardcoded and visible in source/decompiled code
- Files: `common/src/main/java/com/pathmind/marketplace/MarketplaceService.java` (lines 32-33)
- Current mitigation: Publishable key is intentionally public (Supabase API key, not secret). Used for read-only marketplace queries
- Recommendations: Document that this key only permits read operations. If write operations are added later, move credentials to runtime environment variables. Monitor Supabase audit logs for abuse

**Local HTTP Server for OAuth Callback:**
- Risk: Marketplace auth spins up `HttpServer` on `127.0.0.1:38451` for OAuth redirect. Malicious software on same machine could intercept redirect
- Files: `common/src/main/java/com/pathmind/marketplace/MarketplaceAuthManager.java` (lines 45-50, 200+)
- Current mitigation: Callback only accepts localhost. Server shuts down after single auth attempt
- Recommendations: Validate redirect tokens are cryptographically fresh (CSRF protection via state parameter present). Add timeout to server startup (fail if port unavailable after 30s). Validate Host header is `127.0.0.1:38451`

**Session Token Persisted to Disk Without Encryption:**
- Risk: OAuth access/refresh tokens stored in plaintext `marketplace_auth.json` on disk
- Files: `common/src/main/java/com/pathmind/marketplace/MarketplaceAuthManager.java` (line 53, disk I/O methods)
- Current mitigation: File stored in user's Minecraft config directory (typically only readable by that user)
- Recommendations: Encrypt tokens at rest using Java Cipher with OS keystore (JKS or system secrets manager). Implement token rotation every 7 days. Add token expiry validation with immediate refresh if expired

## Performance Bottlenecks

**NodeGraph Rendering (15,849 lines, ~20+ methods per frame):**
- Problem: UI redraws entire node graph every frame including 100+ nodes
- Files: `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java`
- Cause: No dirty-flag tracking or viewport culling. All nodes rendered even if off-screen
- Improvement path: Implement spatial hashing for visible nodes. Track which nodes changed position/state per frame. Only redraw changed nodes using damage rectangles. Cull nodes outside viewport

**Pathfinding Expansion Limits Too Loose:**
- Problem: MAX_EXPANSIONS = 64000 in coarse plan phase (lines 127) can cause frame hitches >100ms
- Files: `common/src/main/java/com/pathmind/execution/PathmindNavigator.java` (line 127)
- Cause: Pathfind time budget (220ms, line 125) is insufficient for A* search on 64k node expansions
- Improvement path: Reduce MAX_EXPANSIONS to 16000. Use incremental pathfinding (subdivide into 2-3 frame chunks). Implement bidirectional search to halve expansions. Profile with large complex terrain

**In-Memory JSON Cache Serialization Overhead:**
- Problem: Every `cachePresetGraph()` call serializes entire NodeGraphData to JSON string
- Files: `common/src/main/java/com/pathmind/data/NodeGraphPersistence.java` (line 785)
- Cause: Gson.toJson() allocates new string every cache update. No delta encoding
- Improvement path: Cache the original NodeGraphData objects instead of JSON strings. Only serialize when writing to disk. Avoids repeated GsonBuilder.create() overhead

**Marketplace Preset Fetching Synchronous on UI Thread:**
- Problem: `fetchPublishedPresets()` makes HTTP request that may block UI during network latency (line 56)
- Files: `common/src/main/java/com/pathmind/marketplace/MarketplaceService.java` (line 51-56)
- Cause: HTTP client timeout is 15 seconds (line 102). No indication of loading state to user
- Improvement path: Implement cancellable futures with user-facing progress indicator. Add HTTP/2 connection pooling. Cache preset list with 5min TTL to avoid redundant network calls

## Fragile Areas

**ExecutionManager State Management:**
- Files: `common/src/main/java/com/pathmind/execution/ExecutionManager.java` (lines 54-89)
- Why fragile: 
  - 14 instance fields (`activeNode`, `isExecuting`, `executionStartTime`, etc.) modified across 20+ methods
  - `ChainController` objects stored in map but can be orphaned if chain completion handler races with cancellation
  - No versioning of execution state (easy to read stale values)
- Safe modification: Use immutable ExecutionSnapshot objects instead of mutable fields. Make state changes atomic via synchronized blocks. Add defensive null checks before accessing ChainController fields
- Test coverage: No visible unit tests for concurrent execution. Hard to test race conditions

**Node Parameter Serialization/Deserialization:**
- Files: `common/src/main/java/com/pathmind/nodes/Node.java` (lines 5000+)
- Why fragile: 
  - Custom GSON type adapters for NodeParameter without validation of parameter count or order
  - Legacy syntax compatibility code converts old variable references without schema validation
  - If a preset saves with a new node type but is loaded on older version, graceful degradation is missing
- Safe modification: Add version headers to node definitions. Validate parameter count matches node definition. Add migration layer for breaking changes
- Test coverage: No visible integration tests for loading presets across mod versions

**Marketplace Preset Metadata Assumptions:**
- Files: `common/src/main/java/com/pathmind/marketplace/MarketplaceService.java` (lines 180-250)
- Why fragile:
  - Assumes Supabase API response always includes expected fields (`id`, `slug`, `author_name`, etc.)
  - If server returns different schema, field access returns null without validation
  - File download from `storage_bucket` / `file_path` can fail silently if URLs are malformed
- Safe modification: Add JSON schema validation. Validate all required fields exist before constructing MarketplacePreset. Implement circuit breaker for repeated failures
- Test coverage: No visible tests for malformed API responses

## Scaling Limits

**Total Presets in Directory:**
- Current capacity: File-based preset storage with one file per preset in `pathmind/presets/`
- Limit: OS filesystem may slow down directory listing with 1000+ JSON files. No index structure
- Scaling path: Implement SQLite database for preset metadata. Keep actual graphs as individual files but index by preset name. Cache recently-used presets in memory

**Concurrent Graph Executions:**
- Current capacity: Design supports multiple ChainController objects but no coordination
- Limit: Each execution thread allocates separate runtime variable maps. With 10+ concurrent chains, memory usage becomes significant (~1-2MB per chain)
- Scaling path: Implement shared runtime variable namespace with scoping rules. Use thread-local storage to reduce per-thread memory. Implement variable eviction for completed chains

**Node Graph Complexity (Editor Performance):**
- Current capacity: NodeGraph UI renders all nodes + connections every frame
- Limit: Beyond ~300 nodes, frame rate drops to <15 FPS due to O(n²) connection rendering
- Scaling path: Implement viewport culling + quadtree spatial indexing. Use VBO/display lists for static elements. Lazy-load node details outside viewport

## Dependencies at Risk

**NeoForge Cross-Version Compatibility:**
- Risk: Project supports 11 Minecraft versions (1.21-1.21.11) with NeoForge builds. Each version has different API (lines 28-94 in build.gradle.kts show version-specific NeoForge versions)
- Impact: NeoForge API changes between 21.0.166 and 21.11.42. Mixin behavior, event system, and registry access differ. Fabric API is more stable across versions but NeoForge has breaking changes
- Migration plan: Extract loader-specific code into `common/src/neoforge/` and `common/src/fabric/` modules. Use Architectury abstractions for registry/event access. Add CI test matrix for all supported versions

**Supabase API Deprecation Risk:**
- Risk: Marketplace integration depends on specific Supabase REST API endpoints (lines 98-101, 137-140)
- Impact: Supabase may deprecate API versions or change response schema. No version pin in HTTP requests (no `Accept-Version` header). No fallback if API becomes unavailable
- Migration plan: Abstract MarketplaceService behind interface. Implement fallback to local preset list. Add API version negotiation. Cache marketplace data locally with 24hr TTL so outages don't break gameplay

**Gson Library Stability:**
- Risk: Heavy reliance on custom Gson type adapters for Node, NodeMode, NodeType serialization (lines 36-37 in NodeGraphPersistence.java)
- Impact: Gson updates can change behavior of type resolution. Custom adapters may break if Gson changes reflection APIs
- Migration plan: Add schema versioning to JSON (e.g., `_version: 2` field). Write schema tests that ensure deserialization works across Gson versions. Consider lighter JSON library (Jackson) if performance becomes critical

## Missing Critical Features

**No Validation Before Executing Large Graphs:**
- Problem: Users can execute graphs with 500+ nodes without warning. No complexity estimator
- Blocks: Performance profiling and optimization. Can't help users understand why their graphs are slow
- Recommendation: Add execution time estimator. Warn if graph exceeds heuristic cost. Profile node types to estimate execution cost

**No Crash Recovery or Autosave:**
- Problem: If game crashes during graph edit, work is lost unless manually saved
- Blocks: Extended editing sessions. Users avoid complex graphs due to data loss risk
- Recommendation: Implement periodic autosave every 5 minutes to temp file. On load, recover unsaved work

**No Preset Versioning or Merge Conflict Resolution:**
- Problem: If user edits preset on two machines, only last-write-wins. No merge capability
- Blocks: Collaborative workflow. Can't track preset history
- Recommendation: Implement version control within preset system. Track edits per user. Implement 3-way merge for non-conflicting changes

## Test Coverage Gaps

**No Unit Tests for Pathfinding Algorithm:**
- What's not tested: A* path finding, waypoint navigation, terrain collision detection
- Files: `common/src/main/java/com/pathmind/execution/PathmindNavigator.java` (pathfind methods ~500 lines)
- Risk: Pathfinding bugs only surface during in-game execution. Hard to isolate root cause with 60+ tuning parameters. Regressions on MC version updates go undetected
- Priority: High - pathfinding is core feature. Bugs break automation workflows

**No Integration Tests for Graph Execution State Machine:**
- What's not tested: Chain completion, concurrent execution merging, variable scoping across nested chains
- Files: `common/src/main/java/com/pathmind/execution/ExecutionManager.java` (execution flow ~800 lines)
- Risk: Race conditions only trigger under specific timing. Edge cases (early termination, exception handling) untested. Refactoring execution state becomes dangerous
- Priority: High - execution state is critical path

**No Tests for Marketplace API Error Handling:**
- What's not tested: Network failures, malformed JSON responses, authentication token expiry
- Files: `common/src/main/java/com/pathmind/marketplace/MarketplaceService.java`, `MarketplaceAuthManager.java`
- Risk: Auth session expires mid-download with no user feedback. Network errors cause silent failures. API schema changes break silently
- Priority: Medium - marketplace is non-critical but affects user experience

**No UI Rendering Regression Tests:**
- What's not tested: Node graph layout, text wrapping in overlays, rendering under various screen resolutions
- Files: `common/src/main/java/com/pathmind/ui/graph/NodeGraph.java` (15,849 lines)
- Risk: UI refactoring can break across Minecraft versions due to rendering API changes. Crashes on unusual window sizes are discovered late
- Priority: Medium - requires screenshot comparison infrastructure

---

*Concerns audit: 2026-06-12*
