# Phase 1: API Foundation + Script Node Registration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-12
**Phase:** 1-API Foundation + Script Node Registration
**Areas discussed:** Addon identity & repo, Palette presentation, Addon failure UX, API versioning policy

---

## Addon identity & repo

| Option | Description | Selected |
|--------|-------------|----------|
| Pathmind Lua | mod id `pathmind-lua`, REI-style "<host> <capability>" naming | ✓ |
| Pathmind Scripting | broader name for multi-language future | |
| Standalone brand name | own identity, hides Pathmind relationship | |

**User's choice:** Pathmind Lua
**Notes:** Repo location: sibling at `../pathmind-lua`. Maven coords for the API: "You decide". Key clarification volunteered by user: this is a fork maintained with the original author's permission; the addon-API work is an upstream contribution and keeps Pathmind's original `com.pathmind` Maven identity, while the Lua addon is the user's own greenlit project under their long-standing namespace `com.mrmysterium`, author credit `mr_mysterium` (Discord name).

---

## Palette presentation

| Option | Description | Selected |
|--------|-------------|----------|
| Addon-declared category | addons declare their own categories | ✓ |
| Existing category | slot into built-in Logic/Utility | |
| Generic "Addons" bucket | one fixed category for all addons | |

**User's choice:** Addon-declared category; read-only script preview as the Phase 1 node body (vs plain name+status or early text field); subtle provenance badge/accent on addon nodes (vs identical styling).

---

## Addon failure UX

| Option | Description | Selected |
|--------|-------------|----------|
| Disable addon, keep playing | log + skip addon registrations + in-game warning (REI/JEI behavior) | ✓ |
| Hard crash with clear report | fail-fast crash screen | |
| Per-registration skip | only failing node type skipped | |

**User's choice:** Disable addon, keep playing; warning via existing error overlay (vs chat message or both); presets with missing addon nodes load as inert data-preserving placeholders (vs strip-with-warning or refuse-to-load).

---

## API versioning policy

| Option | Description | Selected |
|--------|-------------|----------|
| Independent API semver | 0.x during co-evolution, 1.0 at stable milestone | ✓ |
| Track Pathmind version | API version = mod version | |
| You decide | | |

**User's choice:** Independent API semver; mismatch handling = fabric.mod.json version ranges + runtime registration check that triggers the standard failure UX (vs loader-only or warn-and-try).

## Claude's Discretion

- Exact Maven coordinates / artifact naming for the API (plain vs per-MC-version)
- Addon Java package layout under `com.mrmysterium`
- API package boundary mechanics and NodeType enum integration strategy (research recommendations stand)

## Deferred Ideas

None — discussion stayed within phase scope.
