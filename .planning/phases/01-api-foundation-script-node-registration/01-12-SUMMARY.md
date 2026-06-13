---
phase: 01-api-foundation-script-node-registration
plan: 12
subsystem: api
tags: [java, concurrency, thread-safety, collections, addon-loader]

# Dependency graph
requires: []
provides:
  - Thread-safe AddonLoader.failedAddons collection using Collections.synchronizedMap(new LinkedHashMap<>())
  - Insertion-order preserved for D-08 failure-surface UI
affects: [Phase 2 (increased concurrency with worker-thread addon execution)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Collections.synchronizedMap wrapping LinkedHashMap for thread-safe insertion-ordered map"

key-files:
  created: []
  modified:
    - common/src/main/java/com/pathmind/execution/AddonLoader.java

key-decisions:
  - "Use Collections.synchronizedMap(new LinkedHashMap<>()) over ConcurrentHashMap to preserve insertion order required by D-08 failure-surface UI"

patterns-established:
  - "Concurrency hardening pattern: wrap LinkedHashMap in Collections.synchronizedMap when both insertion order and thread safety are needed"

requirements-completed: [API-05]

# Metrics
duration: 1min
completed: 2026-06-13
---

# Phase 01 Plan 12: Make AddonLoader.failedAddons Concurrency-Safe Summary

**AddonLoader.failedAddons replaced with Collections.synchronizedMap(new LinkedHashMap<>()) — closes NEW-CR-01 before Phase 2 increases concurrency**

## Performance

- **Duration:** 1 min
- **Started:** 2026-06-13T10:02:00Z
- **Completed:** 2026-06-13T10:02:48Z
- **Tasks:** 1 of 1
- **Files modified:** 1

## Accomplishments
- Replaced non-thread-safe `static final LinkedHashMap` in `AddonLoader.failedAddons` with `Collections.synchronizedMap(new LinkedHashMap<>())`
- Preserved insertion-order semantics required by the D-08 failure-surface UI (rationale documented in code comment)
- Added synchronization-contract comment explaining that current callers do not iterate the live map, so no caller changes are needed
- No public API changes: `markFailed`, `getFailure`, and `getFailedAddons` signatures unchanged; compilation passes

## Task Commits

Each task was committed atomically:

1. **Task 1: Make failedAddons thread-safe with order-preserving synchronized map** - `2d34671` (fix)

**Plan metadata:** (see final commit below)

## Files Created/Modified
- `common/src/main/java/com/pathmind/execution/AddonLoader.java` - Changed `failedAddons` initializer from `new LinkedHashMap<>()` to `Collections.synchronizedMap(new LinkedHashMap<>())`; added comment documenting synchronization contract

## Decisions Made
- Used `Collections.synchronizedMap(new LinkedHashMap<>())` instead of `ConcurrentHashMap` because ConcurrentHashMap does not preserve insertion order; the D-08 failure-surface UI's ordering dependency cannot be disproven, so insertion-order is treated as a requirement

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- NEW-CR-01 is closed; `AddonLoader.failedAddons` is now safe for the init-write / UI-read pattern
- Phase 2 worker-thread addon execution can proceed without risk of ConcurrentModificationException or map structure corruption from concurrent `markFailed` calls

## Known Stubs
None — no stubs introduced.

## Threat Flags
None — change is a defensive concurrency hardening of an existing field with no new network, auth, file, or schema surface.

## Self-Check: PASSED
- `common/src/main/java/com/pathmind/execution/AddonLoader.java` exists and contains `Collections.synchronizedMap(new LinkedHashMap<>())`
- Task commit `2d34671` verified in git log

---
*Phase: 01-api-foundation-script-node-registration*
*Completed: 2026-06-13*
