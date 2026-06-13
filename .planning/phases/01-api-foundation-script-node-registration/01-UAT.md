---
status: testing
phase: 01-api-foundation-script-node-registration
source: [01-VERIFICATION.md]
started: 2026-06-13T00:28:17Z
updated: 2026-06-13T00:28:17Z
---

## Current Test

number: 1
name: Addon category visible in sidebar
expected: |
  Launch Minecraft 1.21.4 with both pathmind.jar and pathmind-lua jar installed.
  Open the Pathmind editor — an addon category tab/section (Scripting) appears in the sidebar.
awaiting: user response

## Tests

### 1. Addon category visible in sidebar
expected: With both jars installed, the addon-declared category (Scripting) appears as a tab/section in the editor sidebar.
result: [pending]

### 2. Script node drag-to-canvas
expected: Dragging the Script node entry from the Scripting category places an ADDON node on the graph canvas.
result: [pending]

### 3. Preset round-trip
expected: A placed Script node (with script text set) survives save → close editor → reopen → reload preset without being silently dropped; script text is intact.
result: [pending]

### 4. Execution pass-through
expected: Running a graph containing the Script node activates and completes the node gracefully (no-op executor) with no "null addonTypeId" warning in the log.
result: [pending]

### 5. Standalone mode
expected: Pathmind loads cleanly and the editor works normally with NO addon jar installed (spot-check on 1.21.4; full range 1.21–1.21.11 as available).
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
