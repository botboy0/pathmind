---
phase: 01-api-foundation-script-node-registration
plan: 13
subsystem: testing
tags: [junit5, assertions, test-integrity, addon-persistence, json-contract]

requires: []
provides:
  - "Six JSON-field presence/absence checks in AddonNodePersistenceTest converted from silent Java assert to executable JUnit assertTrue/assertFalse — build now fails on persistence-contract regression without -ea"
affects: []

tech-stack:
  added: []
  patterns: ["Use JUnit Jupiter assertTrue/assertFalse (static import) for JSON-field presence checks; never use Java assert keyword in test bodies"]

key-files:
  created: []
  modified:
    - common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java

key-decisions:
  - "Convert Java assert to JUnit assertions (assertTrue/assertFalse) to close WR-01: the assert keyword is a no-op without -ea, so JSON-contract regressions would pass silently under Gradle without this fix"

patterns-established:
  - "JSON-field presence: assertTrue(json.contains(field), message) / assertFalse(json.contains(field), message)"

requirements-completed: [API-05, LUA-05]

duration: 1min
completed: 2026-06-13
---

# Phase 01 Plan 13: Test Integrity — AddonNodePersistenceTest assert Remediation Summary

**Six Java assert statements converted to JUnit assertTrue/assertFalse in AddonNodePersistenceTest, making the API-05/LUA-05 JSON-contract checks executable without -ea so preset-persistence regressions fail the build**

## Performance

- **Duration:** 1 min
- **Started:** 2026-06-13T10:05:49Z
- **Completed:** 2026-06-13T10:06:41Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Added `import static org.junit.jupiter.api.Assertions.assertTrue` and `assertFalse` to AddonNodePersistenceTest
- Replaced four `assert json.contains(...)` calls in `nodeData_addonTypeIdAndExtraFieldsSurviveGsonRoundTrip` with `assertTrue(json.contains(...), message)` preserving all original failure messages verbatim
- Replaced two `assert !json.contains(...)` calls in `nodeData_builtinNodeHasNoAddonFields` with `assertFalse(json.contains(...), message)` preserving all original failure messages verbatim
- All 6 AddonNodePersistenceTest tests pass under `./gradlew.bat :common:test`

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace Java assert with JUnit assertions in AddonNodePersistenceTest** - `4eedc20` (fix)

**Plan metadata:** (pending docs commit)

## Files Created/Modified
- `common/src/test/java/com/pathmind/data/AddonNodePersistenceTest.java` - Added assertTrue/assertFalse static imports; converted 6 Java assert statements to JUnit assertions

## Decisions Made
None - followed plan as specified. The `assert version >= 1;` inside the inline TEST_SERIALIZER.deserialize (line ~56) was intentionally left as-is per the plan's explicit scope note: it is a serializer-internal sanity check, not a JSON-field presence test under remediation.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - this is a test-only change. No production code or stub patterns introduced.

## Threat Flags
None - test-only change with no production code, network endpoints, auth paths, file access patterns, or schema changes at trust boundaries.

## Next Phase Readiness
- NEW-WR-01 / WR-01 closed: the six JSON-field presence/absence checks now execute unconditionally under Gradle (no -ea required) and will fail the build if addonTypeId, _schema_version, or extraFields disappear from serialized preset JSON
- AddonNodePersistenceTest remains fully passing; no test logic changed beyond the assertion mechanism
- Phase 01 plan 13 of 13 complete — api-foundation-script-node-registration phase fully executed

---
*Phase: 01-api-foundation-script-node-registration*
*Completed: 2026-06-13*
