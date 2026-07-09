# External Integrations

**Analysis Date:** 2026-06-12

## APIs & External Services

**Supabase Marketplace Backend:**
- Service: Supabase (PostgreSQL + REST API + Storage)
  - Project URL: `https://gadzentglfdzhylcchmk.supabase.co`
  - What it's used for: In-game marketplace for browsing, publishing, and managing community presets/graphs
  - SDK/Client: Java 11+ `java.net.http.HttpClient` (standard library)
  - Auth: Supabase OAuth2 (browser-based with local callback server)
  - Publishable Key (public, safe to embed): `sb_publishable_ZJbCFcG5Yh4QM9W9as4jVA_daD3fwnn`
  - Implementation: `com.pathmind.marketplace.MarketplaceService` and `com.pathmind.marketplace.MarketplaceAuthManager`

**Authentication Details:**
- OAuth2 flow with PKCE
- Callback server: Local HTTP listener on `127.0.0.1:38451` (path: `/auth/callback`)
- Callback completion page: `http://127.0.0.1:38451/auth/complete`
- Browser integration: Automatic fallback to manual login if Desktop.open() unavailable
- Session storage: Local file `pathmind/marketplace_auth.json` in game data directory
- Token refresh: Automatic with configurable refresh threshold
- Expiration handling: Graceful fallback to anonymous mode if refresh fails

## Data Storage

**Databases:**
- Type: Supabase PostgreSQL
  - Connection: HTTPS REST API (`https://gadzentglfdzhylcchmk.supabase.co/rest/v1/`)
  - Tables:
    - `marketplace_presets` - Published preset metadata (id, slug, author, name, description, tags, game_version, pathmind_version, likes_count, downloads_count, storage_bucket, file_path, published timestamp, updated timestamp)
    - `marketplace_roles` - User moderation roles (user_id, role: moderator/admin)
    - `marketplace_author_profiles` - Author profile data (avatar_url, display name, bio)
    - `preset_likes` - Like tracking (user_id, preset_id, created_at)
  - Access: Authenticated via Bearer token (OAuth2 access token) or anonymous (publishable key only)
  - RPC Functions (stored procedures):
    - `publish_marketplace_preset` - Publish new preset
    - `delete_marketplace_preset` - Delete owned preset
    - `update_marketplace_preset_metadata` - Update preset metadata (name, description, tags)
    - `list_manageable_marketplace_presets` - List presets user owns/can manage
    - `toggle_marketplace_preset_like` - Like/unlike a preset
    - `increment_preset_downloads` - Track download count

**File Storage:**
- Supabase Storage buckets:
  - `graphs` (public) - Shared/published preset files
  - `private_graphs` (authenticated) - Private/draft preset files
  - Access: HTTPS (public: `/storage/v1/object/public/`, authenticated: `/storage/v1/object/authenticated/`)
  - Upload: Via authenticated API with preset author validation
  - Download: Via HTTP GET with Bearer token for private files, no auth for public

**Local File Storage:**
- Workspace root: OS-specific game data directory
  - Windows: `%APPDATA%/.minecraft/`
  - macOS: `~/Library/Application Support/minecraft/`
  - Linux: `~/.minecraft/`
- Pathmind config directories:
  - Workspaces: `pathmind/workspaces/`
  - Presets: `pathmind/presets/`
  - Settings: `pathmind/settings.json`
  - Marketplace session: `pathmind/marketplace_auth.json`
- Implementation: `com.pathmind.data.WorkspaceFileAccess`, `com.pathmind.data.PresetManager`, `com.pathmind.data.SettingsManager`

**Caching:**
- In-memory preset cache in `MarketplaceService` (CompletableFuture-based async caching)
- No persistent HTTP cache headers/ETags currently used
- Rate limit state: Tracked in `MarketplaceRateLimitManager` (per-request quotas)

## Authentication & Identity

**Auth Provider:**
- Supabase Auth (OAuth2 + PKCE)
  - Implementation: `com.pathmind.marketplace.MarketplaceAuthManager`
  - Custom local HTTP callback server for OAuth redirect
  - Browser-based login flow (Desktop.open for default browser, manual fallback)

**Session Management:**
- Access token (JWT) stored locally in `pathmind/marketplace_auth.json`
- Refresh token stored for automatic renewal
- Session expiration: Automatic refresh 5 minutes before token expires (configurable)
- Cache: Single volatile field in `MarketplaceAuthManager` with optional disk reload
- Anonymous fallback: Marketplace browsing works without authentication (limited features)

**Authorization:**
- Role-based access control (RBAC) via Supabase RLS policies
  - Roles: `user` (default), `moderator` (preset management), `admin` (full control)
  - Checked at API level; UI respects role visibility
- Preset ownership validated server-side on publish/delete/update

## Monitoring & Observability

**Error Tracking:**
- In-game error display via HUD overlays and notification system
- No external error tracking service integrated
- Exceptions logged to console (Minecraft logger): `com.pathmind.*` classes
- Implementation: `com.pathmind.PathmindCommon` and per-module loggers

**Logs:**
- Approach: Java `System.out` / Minecraft logger
- Key events logged:
  - Marketplace API calls (request/response times)
  - Authentication state changes
  - Graph execution events (start, stop, errors)
  - Preset import/export operations
- Log levels: INFO (default), DEBUG (when dev mode enabled), ERROR (exceptions)

**Rate Limiting & Quotas:**
- Marketplace API: Custom rate limit manager in `com.pathmind.marketplace.MarketplaceRateLimitManager`
- Per-player download quota enforcement
- Per-player publish quota enforcement
- Quota resets on server-side schedule (hourly/daily)

## CI/CD & Deployment

**Hosting:**
- No backend hosting required (Supabase is fully managed)
- Mod distribution: Modrinth (primary), CurseForge (secondary)
- Release management: GitHub Actions workflows in `.github/workflows/`

**CI Pipeline:**
- Build pipeline: GitHub Actions (`build.yml`)
- Triggers: Push to main, pull requests
- Tasks:
  - Compile Fabric and NeoForge JARs
  - Run unit tests (JUnit Jupiter)
  - Generate multi-version JAR outputs
  - Upload artifacts (build/multiVersion/)
  - Optional: Auto-publish to Modrinth/CurseForge on release tag

**Source Control:**
- Git repository hosted on GitHub (private or public)
- Main branch: Production-ready code

## Environment Configuration

**Required Environment Variables:**
- None required for mod operation (all config baked into JAR)
- Optional for development:
  - `BARITONE_API_JAR` - Path to Baritone API JAR for optional integration testing

**Secrets Location:**
- Supabase publishable key: Hard-coded in `MarketplaceService.java` (safe—it's public)
- Supabase project URL: Hard-coded in `MarketplaceService.java` (safe—it's the API endpoint)
- No private/sensitive keys stored in code
- User access tokens: Stored in OS-specific game data directory (`pathmind/marketplace_auth.json`)

**Configuration Files:**
- `settings.json` - User-configurable mod settings (keybinds, UI preferences, execution timeout)
- Mod loader config: Auto-generated by Fabric/NeoForge
- Loom IDE config: Generated in `build/` on first run

## Webhooks & Callbacks

**Incoming:**
- None (mod is client-side only, no server component)

**Outgoing:**
- OAuth2 callback: `http://127.0.0.1:38451/auth/callback` (handled by embedded HTTP server)
- Marketplace preset download: Triggered by user action (manual, not automatic)
- Marketplace analytics: Download/like counts sent on user interaction (optional)

## Optional Integrations

**Baritone Integration:**
- Purpose: Pathfinding and automated movement beyond built-in navigator
- Library: Baritone API (v1.15.0 for compatible MC versions)
- How it integrates:
  - Compile-only dependency (modCompileOnly) to avoid hard requirement
  - Runtime optional (modLocalRuntime) only when `-PwithBaritoneRuntime` enabled
  - Nodes: Baritone-specific pathfinding and building nodes (fallback to no-op if unavailable)
  - Detection: Runtime class loading check for `baritone.api.IBaritone`
  - Supported MC versions: 1.21.6, 1.21.7, 1.21.8

**UI Utils Integration:**
- Purpose: Enhanced UI automation nodes (keyboard input, mouse control, screen interaction)
- Library: UI Utils mod (external dependency, user-installed)
- How it integrates:
  - Nodes check for UI Utils presence at runtime
  - Fallback to basic screen interaction if unavailable
  - No special build config required

## Data Formats

**Node Graph Storage:**
- Format: JSON (via GSON)
- File extension: `.json`
- Schema: Defined in `com.pathmind.data.NodeGraphData` (internal DSL)
- Serialization: GSON custom serializer for node tree, connections, parameters

**Preset Metadata:**
- Format: JSON (embedded in marketplace database)
- Fields: author, description, tags, pathmind_version, game_version, created_at, updated_at, likes, downloads

**Marketplace Session (Auth Token):**
- Format: JSON
- Contents: access_token, refresh_token, expires_at, user_id, user_email
- Location: `pathmind/marketplace_auth.json` (plaintext on disk—encrypted by OS)

---

*Integration audit: 2026-06-12*
