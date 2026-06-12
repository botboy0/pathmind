# Stack Research

**Domain:** Minecraft mod addon API + embedded Lua scripting addon (Fabric / Architectury / Java 21)
**Researched:** 2026-06-12
**Confidence:** HIGH (addon API patterns), HIGH (Lua VM choice), MEDIUM (in-game text editor widget)

---

## Context: What This Research Covers

Pathmind already runs on Java 21, Gradle 8.x, Architectury, Fabric Loader 0.17.3, Fabric API, and Architectury Loom 1.14.x. This document does NOT re-research that base. It covers three new dimensions:

1. **Addon API surface** — how to let external mods register against Pathmind (entrypoint discovery, API/impl separation, publishing)
2. **Lua VM** — which JVM-based Lua runtime to embed in the scripting addon
3. **In-game code editor widget** — what vanilla MC 1.21.4 provides for multiline text editing in a screen

---

## Recommended Stack

### A. Addon API — Entrypoint Discovery

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Fabric Loader custom entrypoints | Loader 0.15+ (already used) | Addon mod discovery at init time | The standard Fabric mechanism for mod-to-mod plugin APIs; used by JEI (`jei_mod_plugin` key), REI, and Fabric API itself. Zero runtime overhead — entrypoints are lazy-loaded only when `FabricLoader.getInstance().getEntrypointContainers(key, type)` is called. |
| `fabric.mod.json` entrypoints block | spec v1 | Addon declares its plugin class under Pathmind's key | Declarative, no reflection at call-site, no classpath scanning. Addon jars simply add `"pathmind": ["com.example.addon.MyPathmindAddon"]` to their `fabric.mod.json`. |

**How the pattern works:**

Pathmind defines an interface — e.g. `PathmindAddonInitializer` — in its API module. During `ModInitializer.onInitialize()`, Pathmind calls:

```java
FabricLoader.getInstance()
    .getEntrypointContainers("pathmind", PathmindAddonInitializer.class)
    .forEach(container -> container.getEntrypoint().onPathmindInit(registry));
```

An addon declares in its `fabric.mod.json`:
```json
"entrypoints": {
  "pathmind": ["com.example.mymod.PathmindPlugin"]
}
```

**Why NOT ServiceLoader:** Fabric's entrypoint system supersedes `java.util.ServiceLoader` for Minecraft mods. ServiceLoader works on the system classloader; Fabric mods each have their own `FabricClassLoader` isolate, and ServiceLoader cannot see across them reliably. Entrypoints are loader-aware and mod-metadata-backed.

**Why NOT reflection scanning:** Classpath scanning (reflections, guava ClassPath) is fragile in modded Minecraft where classloaders are non-standard and obfuscated. The entrypoint system is the correct abstraction.

**Confidence:** HIGH — this is the documented, production-proven pattern used by JEI, REI, Fabric API, and most major library mods.

---

### B. Addon API — API / Impl Module Separation

| Pattern | Details | Why |
|---------|---------|-----|
| `common/api` source set OR separate `api` subproject | All public-facing interfaces live in `pathmind-api` jar; implementation lives in `pathmind-impl` | Prevents addons from accidentally depending on internal classes that will change. REI does this with a dedicated `RoughlyEnoughItems-api` module. |
| Architectury `common` module as API home | API interfaces defined in `common/src/main/java` under a dedicated `api` package | Consistent with Pathmind's existing multi-module layout; avoids a 4th subproject for the first iteration. A formal split into a separate `api` subproject is a future concern. |

**Recommended approach for v1:** Create an `api` package within Pathmind's existing `common` module (`common/src/main/java/com/pathmind/api/`). All types in this package form the public contract. Types outside it are implementation. Document this boundary in a package-info.java or convention. A hard module boundary via a separate Gradle subproject can be deferred until the API stabilizes.

**Why NOT a full separate api subproject immediately:** Pathmind's build already manages 11 MC versions with separate Fabric/NeoForge subprojects. Adding a 4th subproject now before the API shape is known creates complexity with minimal benefit — the package boundary is sufficient for v1 with one addon.

---

### C. Addon API — API Artifact Publishing

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Gradle `maven-publish` plugin | Built into Gradle 8.x | Produce a consumable Maven artifact for the sibling addon repo | Standard Gradle mechanism; Fabric Loom auto-configures the `remapJar` output as the publication artifact |
| `publishToMavenLocal` task | Gradle built-in | Dev workflow: sibling addon repo consumes Pathmind API locally without a remote Maven | Run `./gradlew :fabric:publishToMavenLocal` during development; addon declares `mavenLocal()` repository. Keeps the dev loop fast while the API is in flux. |
| Modrinth Maven as optional remote | `https://api.modrinth.com/maven` | After release: addon declares Pathmind as a Modrinth Maven dep, works out of the box for users | Modrinth automatically exposes all uploaded mod versions as Maven artifacts. No separate Maven hosting needed post-release. |
| `me.modmuss50.mod-publish-plugin` | 1.1.0 | Publish release JARs to Modrinth/CurseForge via Gradle | Already the Fabric ecosystem standard; Pathmind may already use it or can adopt it. |

**Loom + maven-publish contract:** Fabric Loom's `remapJar` task output is automatically added as the `archives` artifact when `maven-publish` is applied. The POM is auto-populated with mod-augmented dependency scopes. No manual `artifact(remapJar)` configuration is needed.

**Dev workflow (sibling repo):**
```kotlin
// pathmind/fabric/build.gradle.kts
plugins {
    id("maven-publish")
}
publishing {
    publications {
        create<MavenPublication>("mavenFabric") {
            artifactId = "pathmind-fabric"
            from(components["java"])
        }
    }
}
// Run: ./gradlew :fabric:publishToMavenLocal

// pathmind-lua-addon/build.gradle.kts
repositories {
    mavenLocal() // during dev
    maven("https://api.modrinth.com/maven") // after release
}
dependencies {
    modCompileOnly("com.pathmind:pathmind-fabric:${pathmind_version}")
}
```

**Confidence:** HIGH — standard Loom + maven-publish pattern, documented in Fabric wiki and used across the ecosystem.

---

### D. Lua VM — Recommended Choice

**Recommendation: `org.squiddev:Cobalt:0.7.3`**

| Library | Coordinates | Latest Version | Pure JVM | Java 21 | Maintained | Verdict |
|---------|------------|----------------|----------|---------|------------|---------|
| **Cobalt (cc-tweaked fork)** | `org.squiddev:Cobalt` | **0.7.3** (May 2024) | Yes | Yes (CC:T runs on MC 1.21 / Java 21) | Active (CC:T driven) | **USE THIS** |
| party.iroiro.luajava (LuaJ backend) | `party.iroiro.luajava:luajava` + `party.iroiro.luajava:luaj` | 4.1.0 (Jan 2026) | Yes (LuaJ backend only) | Likely (Java 8+ stated, no 21 issues reported) | Active | Viable fallback |
| Original LuaJ | `org.luaj:luaj-jse` | 3.0.1 (2015) | Yes | Unknown — no Java 9+ updates | Dead | Do not use |
| gudzpoz/luajava (native backends) | `party.iroiro.luajava:lua54` | 4.1.0 | No (requires native) | N/A for Minecraft | Active | Wrong tool — needs bundled native .so/.dll |

**Why Cobalt:**

1. **Proven in production Minecraft:** CC: Tweaked ships it in a mod that runs on MC 1.21.1 (Java 21). If there were Java 21 incompatibilities they would have surfaced years ago.
2. **Re-entrant coroutine model:** Cobalt's primary architectural contribution over vanilla LuaJ is full re-entrancy — the VM can be suspended and resumed at arbitrary points, including inside standard library calls and debug hooks. For a scripting node that integrates with Pathmind's execution lifecycle this is the right property even if coroutines aren't a v1 requirement.
3. **Pure JVM, no natives:** Ships as a single JAR. Shadow it into Pathmind's Lua addon without any platform-specific binary concerns.
4. **Lua 5.2 compliance + 5.3/5.4 stdlib backports:** Adequate for scripting use cases. Not bleeding-edge Lua 5.4 syntax, but good enough.
5. **Maven coordinates are stable:** Published to `https://squiddev.cc/maven/` (also `https://maven.squiddev.cc`). The group/artifact have been stable across versions.

**Why NOT party.iroiro.luajava (LuaJ backend):**

The luajava wrapper adds a C-API-style indirection layer on top of LuaJ that is designed for interop with native Lua runtimes. Its LuaJ backend is a thin wrapper over the original (largely stale) LuaJ 3.x library. The API surface is designed for generic Java-Lua bridging rather than embedded scripting control. Cobalt gives a cleaner, MC-centric embedding API (LuaState builder, explicit coroutine control). Use luajava if Cobalt's Maven proves unreliable or if Lua 5.4 compliance is required.

**Why NOT the original LuaJ:**

Last release was 2015, no Java module system support, no Java 9+ testing. The luaj-jse artifact is effectively abandoned. Cobalt is a maintained fork of LuaJ; use the fork.

**Why NOT Cobalt as a transitive CC:T dep:**

Do not declare a dependency on CC:T just to get Cobalt — that drags in the entire ComputerCraft mod. Depend directly on `org.squiddev:Cobalt`.

**Gradle configuration:**
```kotlin
repositories {
    maven("https://squiddev.cc/maven/") {
        content { includeModule("org.squiddev", "Cobalt") }
    }
}
dependencies {
    // Shadow into the addon jar — Cobalt is not available in the mod environment
    implementation("org.squiddev:Cobalt:0.7.3")
}
// In shadowJar config:
// relocate("org.squiddev.cobalt", "com.pathmind.luaaddon.shadow.cobalt")
```

**Relocation note:** Shadow Cobalt with a package relocation to avoid classpath conflicts if users also run CC:Tweaked alongside Pathmind. This is standard practice for shadowed libraries in mods.

**Confidence:** HIGH for the choice. MEDIUM for exact version — 0.7.3 is confirmed from Maven usages search; the upstream GitHub shows 0.7.0 as the tagged release but patch versions are published to squiddev maven.

---

### E. In-Game Code Editor Widget (MC 1.21.4)

**Recommendation: Vanilla `EditBoxWidget` as the base; custom line-number overlay drawn manually.**

| Widget | Class | Available in 1.21.4 | Multiline | Line Numbers | Verdict |
|--------|-------|---------------------|-----------|--------------|---------|
| **EditBoxWidget** | `net.minecraft.client.gui.widget.EditBoxWidget` | Yes (present since 1.19.1) | Yes — extends ScrollableWidget | No built-in | **USE THIS** |
| TextFieldWidget | `net.minecraft.client.gui.widget.TextFieldWidget` | Yes | No — single line only | No | Not suitable |
| EditBox (internal) | `net.minecraft.client.gui.EditBox` | Yes | Yes — backing model | No | Private implementation detail; use EditBoxWidget wrapper |

**EditBoxWidget capabilities in 1.21.4:**

- Truly multiline with scrolling (extends `ScrollableWidget` → `ScrollableTextFieldWidget`)
- Constructor: `EditBoxWidget(TextRenderer, x, y, width, height, Text placeholder, Text message)`
- `setText(String)` / `getText()` — full text access
- `setMaxLength(int)` — use `EditBox.UNLIMITED_LENGTH` for code
- `setChangeListener(Consumer<String>)` — react to changes
- `keyPressed` / `charTyped` — keyboard event pipeline with "basic keyboard shortcuts" (MC-standard cut/copy/paste/undo are not guaranteed but standard select-all / cursor movement work)
- `getContentsHeight()` / `getDeltaYPerScroll()` — scroll math
- No built-in syntax highlighting, no line numbers, no gutter

**Line numbers strategy:** Draw a fixed-width number gutter to the left of the EditBoxWidget. Count newlines in `getText()` on each change event, render line numbers with `TextRenderer.draw()` in the screen's render method, clipped to the widget bounds. This is straightforward custom rendering — no library needed.

**Autocomplete / autosuggestion:** Vanilla `SuggestionWindow` (used by the command bar) is designed for single-line suggestions. For Pathmind Lua API autosuggestions, implement a simple floating suggestion list as a custom Screen overlay or a `ClickableWidget` rendered above the cursor position. Parse the token before the cursor to match against a pre-built list of Pathmind API symbols. A full LSP integration is out of scope for v1.

**No third-party library recommended** for the editor widget in v1. The cost of integrating a third-party text editor library (finding one that works with MC's rendering pipeline, handles Minecraft's font renderer, doesn't conflict with game input) outweighs rolling a minimal custom widget. MC's `EditBoxWidget` is a multiline widget that works out of the box.

**Confidence:** MEDIUM — `EditBoxWidget` presence in 1.21.4 confirmed via official Yarn API docs. "Basic keyboard shortcuts" is vague; cut/copy/paste behavior needs in-game verification. The line-number-gutter approach is a common technique in MC modding but is custom work.

---

## Supporting Libraries Summary

| Library | Coordinates | Version | Purpose | When to Use |
|---------|------------|---------|---------|-------------|
| Cobalt | `org.squiddev:Cobalt` | `0.7.3` | Lua 5.2 VM for the scripting addon | Required for Lua addon |
| Gradle maven-publish | built-in | Gradle 8.x | Publish Pathmind API artifact | Required for sibling-repo addon dev |
| me.modmuss50.mod-publish-plugin | Gradle plugin | `1.1.0` | Publish to Modrinth / CurseForge | After first public release |
| Fabric Loader API | `net.fabricmc:fabric-loader` | already used | `FabricLoader.getEntrypointContainers` | Addon API discovery |
| Shadow plugin (already used) | `com.gradleup.shadow` | already used | Relocate Cobalt into addon jar | Required to avoid CC:T conflicts |

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Lua VM | Cobalt 0.7.3 | party.iroiro.luajava LuaJ backend 4.1.0 | Luajava's LuaJ backend wraps an old LuaJ core with a generic C-API abstraction; Cobalt is a purpose-built re-entrant fork with better MC pedigree |
| Lua VM | Cobalt 0.7.3 | Original LuaJ 3.0.1 | Dead project since 2015, no Java 9+ support, Cobalt is the maintained fork |
| Lua VM | Cobalt 0.7.3 | LuaJIT via native binding | Needs bundled platform natives (.so/.dll), unacceptable for a client mod that must work on all OS/arch |
| Entrypoint discovery | Fabric custom entrypoints | java.util.ServiceLoader | ServiceLoader cannot cross Fabric's per-mod classloaders reliably; entrypoints are the Fabric-correct abstraction |
| Entrypoint discovery | Fabric custom entrypoints | Annotation scanning / reflection | Fragile in obfuscated/remapped modded Minecraft; unmaintainable across MC versions |
| API publishing | publishToMavenLocal (dev) + Modrinth Maven (release) | Self-hosted Maven (Reposilite/Nexus) | Adds infrastructure burden; Modrinth Maven is free, zero-ops, and already familiar to mod devs |
| Text editor widget | Vanilla EditBoxWidget | Third-party mod UI library | No mature third-party text editor widget exists for MC 1.21.4's rendering pipeline; the overhead of finding/integrating one exceeds the cost of custom line-number rendering |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Original `org.luaj:luaj-jse` | Last release 2015; no Java 9+ updates; no coroutine re-entrancy; abandoned | `org.squiddev:Cobalt` — the maintained fork |
| CC:Tweaked as a dependency (to get Cobalt transitively) | Drags in the entire ComputerCraft mod as a hard dependency | Declare `org.squiddev:Cobalt` directly |
| `java.util.ServiceLoader` for addon discovery | Fails across Fabric's per-mod classloader boundaries | `FabricLoader.getInstance().getEntrypointContainers(...)` |
| Reflection-based classpath scanning for addons | Fragile, slow, and breaks with obfuscated/remapped classes | Fabric entrypoint system |
| `TextFieldWidget` for the code editor | Single-line only | `EditBoxWidget` — multiline, scrollable |
| Separate `api` Gradle subproject in the first milestone | Premature — API shape is unknown; package boundary in `common` is sufficient | Package convention (`com.pathmind.api.*`) enforced by team convention |

---

## Version Compatibility Notes

| Component | Version | Compatibility Notes |
|-----------|---------|---------------------|
| Cobalt 0.7.3 | Java 8+ | CC:T ships it on Java 21 (MC 1.21) with no issues; safe |
| party.iroiro.luajava 4.1.0 | Java 8+ stated | No reported Java 21 issues; fallback option if Cobalt unavailable |
| EditBoxWidget | MC 1.19.1+ | Present in 1.21.4; confirm scrolling behavior in-game before finalizing scroll UX |
| Fabric Loader custom entrypoints | 0.12+ | Pathmind already uses 0.17.3; getEntrypointContainers has been stable API for years |
| maven-publish + Loom | Gradle 7+ | Works with Loom 1.x (Pathmind uses Loom 1.14.473); remapJar auto-wired to publication |

---

## Sources

- Fabric Wiki — Entrypoints: https://wiki.fabricmc.net/documentation:entrypoint (HIGH confidence — official Fabric documentation)
- FabricLoader 0.14.22 Javadoc — `getEntrypointContainers`: https://maven.fabricmc.net/docs/fabric-loader-0.14.22/net/fabricmc/loader/api/FabricLoader.html (HIGH confidence — official API docs)
- Cobalt GitHub: https://github.com/cc-tweaked/Cobalt (MEDIUM confidence — README says "don't use outside CC:T" but the library is pure-JVM, Java-21-proven, and the warning is about API stability not capability)
- Cobalt Maven — org.squiddev:Cobalt:0.7.3: https://mvnrepository.com/artifact/org.squiddev/Cobalt/0.7.3 (HIGH confidence — confirmed from Maven usages search)
- SquidDev Maven repository: `https://squiddev.cc/maven/` (HIGH confidence — used in CC:T build configs)
- party.iroiro.luajava docs: https://luajava.iroiro.party/ (MEDIUM confidence — Java 21 not explicitly stated)
- EditBoxWidget 1.21.4 Yarn API: https://maven.fabricmc.net/docs/yarn-1.21.4-rc3+build.3/net/minecraft/client/gui/widget/EditBoxWidget.html (HIGH confidence — official Yarn API docs)
- Architectury publishing patterns: https://deepwiki.com/architectury/architectury-api/10-publishing-and-distribution (MEDIUM confidence — community documentation)
- REI GitHub structure: https://github.com/shedaniel/RoughlyEnoughItems (MEDIUM confidence — structural analysis of API/impl/runtime module split)
- modmuss50/mod-publish-plugin: https://github.com/modmuss50/mod-publish-plugin (HIGH confidence — Fabric ecosystem standard)
- Modrinth Maven: https://support.modrinth.com/en/articles/8801191-modrinth-maven (HIGH confidence — official Modrinth docs)

---

*Stack research for: Minecraft mod addon API + embedded Lua scripting addon*
*Researched: 2026-06-12*
