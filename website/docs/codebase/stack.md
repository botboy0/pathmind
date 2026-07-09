# Technology Stack

**Analysis Date:** 2026-06-12

## Languages

**Primary:**
- Java 21+ - Core mod implementation and all gameplay logic
- Kotlin (DSL) - Gradle build configuration in `.kts` files

**Secondary:**
- SQL - Supabase marketplace backend schemas and RPC functions (`supabase/*.sql`)
- JSON - Configuration and data serialization

## Runtime

**Environment:**
- Minecraft Java Edition 1.21 through 1.21.11
- JVM (Java 21+)

**Package Manager:**
- Gradle 8.x (wrapper: `gradlew.bat` / `./gradlew`)
- Lockfile: Not applicable (Gradle uses `gradle.properties` for version pinning)

## Frameworks

**Core:**
- Architectury API 13.0.2–19.0.1 (version-dependent) - Multi-platform abstraction layer
- Fabric Loader 0.17.3 - Fabric mod loader
- Fabric API 0.102.0–0.140.2 (version-dependent) - Fabric modding utilities
- NeoForge 21.0.166–21.11.42 (version-dependent) - Alternative mod loader (optional per MC version)

**Modding Build Tools:**
- dev.architectury.loom 1.14.473 - Fabric/NeoForge IDE setup and remapping
- architectury-plugin 3.4.161 - Multi-platform build coordination
- com.gradleup.shadow 9.4.1 - JAR shadowing for dependency bundling

**Testing:**
- JUnit Jupiter 5.11.4 - Unit testing framework
- JUnit Platform Launcher - Test runner

## Key Dependencies

**Critical:**
- com.google.code.gson:gson:2.10.1 - JSON serialization/deserialization for node graphs, presets, and Supabase communication
- Minecraft:minecraft (1.21–1.21.11) - Game library with deobfuscated source via Yarn mappings
- net.fabricmc:yarn (1.21+build.9 through 1.21.11+build.3) - Deobfuscation mappings for Fabric

**Infrastructure:**
- Baritone API (optional, JAR-based) - Pathfinding and movement control integration
  - Versions: baritone-api-fabric-1.15.0.jar (for MC 1.21.6, 1.21.7, 1.21.8 with `-PwithBaritoneRuntime`)
  - Located: `libs/baritone-api-fabric-1.15.0.jar` or `run/mods/baritone-api-fabric-1.15.0.jar`
  - Environment: `BARITONE_API_JAR` env var or `baritoneApiPath` Gradle property

**Optional:**
- UI Utils mod - For UI automation nodes (runtime dependency, not built-in)

## Configuration

**Build Configuration:**
- `gradle.properties` - Root-level build properties:
  - `minecraft_version` - Default Minecraft target (1.21.11)
  - `loader_version` - Fabric Loader version (0.17.3)
  - `mod_version` - Pathmind mod version (1.1.5)
  - `maven_group` - Package namespace (com.pathmind)
  - `archives_base_name` - JAR base name (pathmind)
- `gradle/wrapper/gradle-wrapper.properties` - Gradle version pinning
- `build.gradle.kts` - Root build script with multi-version Minecraft support (1.21–1.21.11)
- `settings.gradle.kts` - Subproject includes: common, fabric, neoforge
- `common/build.gradle.kts` - Shared code compilation, Yarn mappings, access wideners
- `fabric/build.gradle.kts` - Fabric-specific build (shadowJar, remapping)
- `neoforge/build.gradle.kts` - NeoForge-specific build (official Mojang mappings)
- `buildSrc/build.gradle.kts` - Custom Gradle tasks for remapping and JAR manipulation

**Compilation:**
- Target Java version: 21
- Source encoding: UTF-8
- Access widener: `common/src/main/resources/pathmind.accesswidener` (grants private field/method access for mixins)

**Development Environment:**
- Loom IDE configuration with customizable client JVM args:
  - Fabric: `-Xms1G -Xmx3G`
  - NeoForge: `-Xms1G -Xmx3G`
- JVM args for build: `-Xmx4G -XX:MaxMetaspaceSize=1G -Dfile.encoding=UTF-8`
- Parallel builds enabled with 2 worker threads

## Version Management

**Multi-Version Architecture:**
Pathmind supports 11 concurrent Minecraft versions with version-specific configurations:
- 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11

**Version-Specific Variants:**
- **Legacy Input APIs** (1.21–1.21.8): `src/compat/legacy/base/java`
- **Legacy Input with Typed UseItem** (1.21–1.21.1): `src/compat/legacy/useitem/typed/java`
- **Legacy Render APIs** (1.21–1.21.4): `src/compat/legacy/render/*`
- **Transitional Render APIs** (1.21.5): `src/compat/legacy/render/transitional/java`
- **Mid-Version Input APIs** (1.21.9–1.21.10): `src/compat/mid/java`
- **Modern APIs** (1.21.11+): `src/compat/modern/java`

**Architectury API Versions by MC Version:**
- 1.21: 13.0.2
- 1.21.1–1.21.3: 13.0.6
- 1.21.4: 14.0.4
- 1.21.5: 14.0.4
- 1.21.6–1.21.7: 17.0.x
- 1.21.8: 17.0.8
- 1.21.9–1.21.10: 18.0.x
- 1.21.11: 19.0.1

**NeoForge Availability:**
NeoForge is not supported for all versions; see `supportedMinecraftVersions` in `build.gradle.kts` for per-version status.

## Platform Requirements

**Development:**
- Java 21+ (required at build-time)
- Gradle 8.x (via wrapper)
- IDE with Gradle and Loom support (IntelliJ IDEA, VSCode with Gradle extension, Eclipse)
- Optional: Baritone API JAR for pathfinding features

**Production (Client-Side Only):**
- Minecraft 1.21–1.21.11 (Java Edition)
- Java 21+ (via Minecraft launcher)
- Architectury API (required, downloads automatically via mod loader)
- Fabric Loader 0.17.3+ OR NeoForge 21.0.166+ (depending on platform choice)
- Fabric API (required for Fabric; downloads automatically) OR no additional deps (NeoForge)
- Optional: Baritone API mod (for expanded pathfinding)
- Optional: UI Utils mod (for UI automation features)

## Build Outputs

**Artifacts:**
- Fabric JAR: `fabric/build/libs/pathmind-fabric-[version]-mc[x.xx.xx].jar`
- NeoForge JAR: `neoforge/build/libs/pathmind-neoforge-[version]-mc[x.xx.xx].jar`
- Source JARs: `*-mc[x.xx.xx]-sources.jar` (for each platform)

**Multi-Version Builds:**
- Task: `buildAllTargets` - Compiles all 11–12 Minecraft versions sequentially
- Output directory: `build/multiVersion/[version]/` for each supported MC version

## Compiler Configuration

**Warnings:**
- Deprecation warnings suppressed (`-Xlint:-deprecation`)
- Removal warnings suppressed (`-Xlint:-removal`)

**Remapping:**
- Fabric: Yarn mappings (human-readable names)
- NeoForge: Official Mojang mappings (obfuscated param names, deobfuscated class/method names)
- Custom remapping task for NeoForge common jars: `RemapJarToMojangTask` in `buildSrc/`

---

*Stack analysis: 2026-06-12*
