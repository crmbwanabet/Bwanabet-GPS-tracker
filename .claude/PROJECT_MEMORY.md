# BwanaBet GPS Tracker — Project Memory

> This file is the living memory of the GPS Tracker project. Claude reads this at the start of every session to understand the project state, architecture, decisions, and history. Update after every significant change.

---

## Project Overview

**What:** Real-time GPS fleet/staff tracking system for BwanaBet (Zambian betting platform)
**Owner:** BwanaBet owner (see PERSONALITY.md)
**Status:** Production — deployed on Vercel + Android APK sideloaded to devices
**Repo:** `crmbwanabet/Bwanabet-GPS-tracker` (GitHub, `main` branch)
**Local path:** `C:\Users\USER\Desktop\Claude projects\GPS tracker`

---

## Architecture

### Three Components

| Component | Tech | Location | Deployed To |
|-----------|------|----------|-------------|
| **Dashboard** | Vanilla HTML/CSS/JS + Leaflet maps + Supabase JS SDK | `public/index.html` | Vercel (static) |
| **Simulator** | Vanilla HTML/JS + Leaflet | `public/simulator.html` | Vercel (static, testing only) |
| **Android App** | Kotlin, FusedLocationProvider, raw SQLite, no Room/kapt | `android/` | Sideloaded APK |
| **API** | Vercel serverless functions (Node.js) | `api/` | Vercel |

### Backend: Supabase

- **Project ID:** `izgpyefzkyrtzjsnglvu`
- **Tables used:** `devices`, `locations`, `app_settings`, `app_versions`
- **Auth model:** Anon key + `x-dashboard-key` header (password from `app_settings`)
- **No RLS enforcement mentioned** — relies on dashboard key for access control

### Data Flow

```
Phone (GPS) → LocationService → SQLite buffer → HTTP POST → Supabase `locations` table
                                                                    ↓
Dashboard (Vercel) ← Supabase JS client ← realtime subscription / polling
```

### Vercel Config

- `vercel.json`: static output from `public/`, API rewrites, daily cron at midnight (`/api/cleanup`)
- **Environment vars:** `SUPABASE_URL`, `SUPABASE_KEY`, `DASHBOARD_KEY`, `SUPABASE_SERVICE_KEY` (for cleanup)
- `api/config.js`: serves env vars to frontend (cached 5 min)
- `api/cleanup.js`: deletes location points older than 18 hours (uses service key)

---

## Android App Architecture (v2.0)

### Files

| File | Purpose |
|------|---------|
| `TrackerApp.kt` | Application entry, constants, notification channel. All config constants live here. |
| `MainActivity.kt` | UI, permission flow (notification → location → background → battery), service control, auto-resume after update |
| `LocationService.kt` | Foreground service: GPS via FusedLocationProvider, consensus filter, collinearity filter, adaptive intervals, sync with exponential backoff |
| `LocationDatabase.kt` | Raw SQLite singleton, offline buffer (15K cap), dedup on (device_id, recorded_at) |
| `BootReceiver.kt` | Restarts tracking after device reboot if it was enabled |
| `UpdateChecker.kt` | Checks `app_versions` table in Supabase, downloads APK via DownloadManager, prompts install |

### Key Constants (TrackerApp.kt)

| Setting | Value | Notes |
|---------|-------|-------|
| MOVING_INTERVAL_MS | 1,000ms | 1s GPS when moving (high-density trail) |
| STATIONARY_INTERVAL_MS | 30,000ms | 30s when still |
| FASTEST_INTERVAL_MS | 500ms | GPS floor |
| SYNC_TIMER_MS | 15,000ms | Sync every 15s |
| SYNC_BATCH_SIZE | 100 | Points per upload |
| STATIONARY_THRESHOLD_M | 10m | Movement below = stationary |
| COLLINEAR_TOLERANCE_M | 2m | Skip mid-points within 2m of line |
| CONSENSUS_COUNT | 3 | Need 3 GPS fixes agreeing |
| CONSENSUS_RADIUS_M | 25m | Fixes must be within 25m |
| MAX_PENDING (DB) | 15,000 | SQLite buffer cap |

### Credentials (hardcoded in TrackerApp.kt)

- `SUPABASE_URL`: `https://izgpyefzkyrtzjsnglvu.supabase.co`
- `SUPABASE_KEY`: anon key (JWT)
- `DASHBOARD_KEY`: `bwanabet2026!`

> Android app can't call Vercel API for config — credentials stay hardcoded in Kotlin.

---

## Dashboard Features (public/index.html — 55KB monolith)

- Login screen with dashboard key
- Device list with status (online/offline/stale), battery, last seen
- Leaflet map with device markers, trail polylines
- Add/edit/delete devices
- Geofence alerts (likely)
- Dark theme, BwanaBet branding (yellow #f5c518 on dark)
- JetBrains Mono + DM Sans fonts
- Supabase realtime subscriptions for live updates

---

## Simulator Features (public/simulator.html)

- 5 pre-configured test devices on Lusaka routes
- Configurable: update interval (2-30s), speed (5-120 km/h), GPS jitter (0-50m)
- Seeds devices to Supabase, sends fake location points
- Map visualization with trails
- Activity log panel

---

## Deployment

- **Dashboard/API:** Push to `main` → auto-deploy on Vercel
- **Android:** Build APK in Android Studio → sideload to phones
- **Update flow:** Upload APK URL to `app_versions` table → app checks on launch → DownloadManager → install prompt

---

## Known Patterns & Decisions

1. **No frameworks** — dashboard is a single HTML file with inline CSS/JS. Keep it that way.
2. **No Room ORM** — raw SQLite chosen deliberately to avoid kapt overhead. Don't add Room.
3. **Credentials in Android** — hardcoded because the app can't hit Vercel serverless at boot. This is by design.
4. **18-hour cleanup** — cron deletes old location data daily. Short retention is intentional.
5. **Supabase anon key** — not a secret, safe in frontend. Service key used only in cleanup API.
6. **INSERT OR IGNORE** — dedup strategy on both Android (SQLite) and Supabase (Prefer header with ignore-duplicates).

---

## Changelog

| Date | Change | Details |
|------|--------|---------|
| 2026-04-09 | Project cloned | Fresh clone to `C:\Users\USER\Desktop\Claude projects\GPS tracker` |
| 2026-04-09 | Dashboard: fix offline status | Removed `is_online` DB dependency, use `last_seen` freshness only |
| 2026-04-09 | Dashboard: fix realtime flapping | Unique channel names, gentler backoff, no collisions |
| 2026-04-09 | Android: zombie service fix | Heartbeat every 30s, auto-restart on stale heartbeat. Version bumped to v2.0 (code 11) |
| 2026-04-09 | Dashboard: performance | Throttled renders (3s), point count (10s), reduced query columns |
| 2026-04-09 | Dashboard: trail accuracy | Disabled OSRM road snapping (fake loops), accuracy filter 50m→30m |
| 2026-04-09 | Dashboard: red GPS dots | Red circle markers at every GPS fix on live trails |
| 2026-04-09 | MCP servers installed | Playwright, Vercel, Mapbox, Android MCP |

---

## Installed Tools & MCP Servers

| Server | Status | Purpose |
|--------|--------|---------|
| **Supabase** (x2) | Connected | DB queries, migrations, edge functions, logs |
| **GitHub** | Connected | Repo management, PRs, releases |
| **Playwright** | Connected | Browser automation, screenshots, UI testing |
| **Vercel** | Needs OAuth (first use) | Deployment logs, status, debugging |
| **Mapbox** | Needs OAuth (first use) | Geocoding, reverse geocoding, route matching |
| **Android MCP** | Needs USB+ADB | Device control, screenshots, APK install |
| **CRM EC2 SSH** | Connected | Remote server access |

### Leaflet Plugins (available, not yet added)
- Leaflet.hotline — speed-colored trails
- Leaflet.markercluster — marker grouping at zoom-out
- Leaflet.PolylineDecorator — proper direction arrows
- Leaflet.MovingMarker — smooth live marker animation

### Supabase Extensions (available, not yet enabled)
- PostGIS — geospatial queries, geofencing
- pg_cron — scheduled tasks in Postgres

---

## Open Issues / TODO

- APK v2.0 (versionCode 11) needs to be built and pushed as GitHub release
- Firebase App Distribution migration (Google restricting sideloading Sept 2026)
- Reverse geocoding (show street names instead of coordinates)
- Geofencing support (PostGIS + Turf.js)

---

## Related Projects

- **BetPredict Chatbot:** `C:\Users\USER\Desktop\Claude projects\chatbot widget` (repo: `crmbwanabet/bet-assist`)
- **Payment Processor:** AWS-based, constitution at `C:\Users\USER\Downloads\BwanaBet_Payment_Processor_Constitution_v3.docx`
- **CRM:** `bwanabet-crm` Vercel project
