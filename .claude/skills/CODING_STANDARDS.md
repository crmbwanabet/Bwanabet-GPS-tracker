# Skill: Coding Standards

> How code should be written in this project. Reference before writing any code.

---

## General Rules

1. **No frameworks, no build tools.** Dashboard is vanilla HTML/CSS/JS in a single file. Keep it that way.
2. **No new dependencies** unless absolutely necessary. The Android app deliberately avoids Room, kapt, and heavy libraries.
3. **Match existing style.** Read the file before editing. Copy the patterns already there.
4. **Inline everything on the frontend.** CSS in `<style>`, JS in `<script>`, all in one HTML file.
5. **Serverless functions are minimal.** `api/*.js` files are single-purpose. Keep them small.

---

## Frontend (Dashboard & Simulator)

### HTML/CSS
- Dark theme: `--bg-dark: #0a0a0f`, `--bg-card: #12121a`, `--yellow: #f5c518`
- Fonts: `DM Sans` for UI text, `JetBrains Mono` for data/code
- CSS custom properties for all colors — use variables, not hardcoded hex
- Mobile-first: test on narrow viewports. Use `grid` and `flex`, not fixed widths
- Compact CSS: the project uses shorthand and minimal whitespace in styles

### JavaScript
- **No ES modules, no imports.** Script tags with global functions.
- **`var` is used throughout** (not `const`/`let`). Match existing convention.
- **Supabase JS SDK** loaded via CDN (`@supabase/supabase-js@2`)
- **Leaflet** loaded via CDN for maps
- **Fetch API** for all HTTP calls — no axios, no jQuery
- **Error handling:** Show errors in UI (toast/log), log to console
- **Config loaded from `/api/config`** at boot — never hardcode Supabase keys in frontend HTML

### Naming
- HTML IDs: camelCase (`deviceIdText`, `loadingScreen`)
- CSS classes: kebab-case (`sim-device`, `btn-main`)
- JS functions: camelCase (`startDevice`, `renderDeviceCards`)
- JS variables: camelCase, `var` keyword

---

## Android (Kotlin)

### Architecture
- **No MVVM, no ViewModel, no LiveData.** Simple Activity + Service pattern.
- **Raw SQLite** via `SQLiteOpenHelper`. No Room.
- **FusedLocationProviderClient** for GPS.
- **JSONObject/JSONArray** for JSON — no Gson, no Moshi, no kotlinx.serialization.
- **HttpURLConnection** for network calls — no Retrofit, no OkHttp.
- **SharedPreferences** for simple state (device_id, tracking_enabled).

### Patterns
- All config constants in `TrackerApp.kt` companion object
- `LocationDatabase` is a thread-safe singleton (`@Volatile` + `synchronized`)
- All public DB methods are `@Synchronized`
- Service uses `START_STICKY` for OS restart after kill
- Exponential backoff with jitter for sync failures
- `CountDownLatch` for synchronous flush on service destroy

### Naming
- Classes: PascalCase (`LocationService`, `TrackerApp`)
- Functions: camelCase (`processLocation`, `syncPendingBatch`)
- Constants: UPPER_SNAKE_CASE (`SYNC_TIMER_MS`, `MAX_PENDING`)
- Package: `com.bwanabet.tracker`

### Build
- Gradle with AGP + Kotlin plugins
- Min SDK: check `app/build.gradle`
- No ProGuard rules that need special attention (basic config)
- Build APK: Build → Build Bundle(s)/APK(s) → Build APK(s)
- Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## API (Vercel Serverless)

- ES module syntax (`export default function handler`)
- Direct Supabase REST API calls via `fetch`
- Environment variables via `process.env`
- Return JSON responses with appropriate status codes
- Cache headers where appropriate (`s-maxage`)
- Service key (`SUPABASE_SERVICE_KEY`) only in server-side code, never exposed to frontend

---

## Supabase

- Use REST API directly (PostgREST syntax)
- Headers: `apikey`, `Authorization: Bearer`, `Prefer` for conflict handling
- `x-dashboard-key` custom header for auth
- Timestamps as ISO 8601 strings
- Dedup via `Prefer: resolution=ignore-duplicates` and unique constraints

---

## What NOT to Do

- Don't add TypeScript
- Don't add a bundler (webpack, vite, etc.)
- Don't add React, Vue, or any frontend framework
- Don't add Room or any ORM to Android
- Don't add Retrofit or OkHttp to Android
- Don't split the dashboard HTML into multiple files
- Don't add authentication beyond the dashboard key
- Don't add environment variable files to git
- Don't change the Supabase project or keys without explicit instruction
