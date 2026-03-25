# BwanaBet GPS Tracker — Android App (v2.0 Production)

## Architecture

```
TrackerApp.kt           App entry point, constants, notification channel
MainActivity.kt         UI, permissions, service control
LocationService.kt      Foreground service: GPS, adaptive intervals, sync
LocationDatabase.kt     SQLite offline buffer (singleton, thread-safe)
BootReceiver.kt         Restarts tracking after device reboot
```

There is ONE database implementation (`LocationDatabase` using raw SQLite).
There is ONE sync path (inside `LocationService`).
No Room, no kapt, no dead code.

## Data Flow

```
GPS (FusedLocationProvider)
  → processLocation() filters redundant points
  → LocationDatabase.insertLocation() writes to SQLite
  → Sync timer (every 2 min) reads batch from SQLite
  → HTTP POST to Supabase REST API
  → On success: delete synced rows from SQLite
  → On failure: exponential backoff (5s → 5min)
```

## Key Features

- Adaptive intervals: 30s when moving, 2min when stationary
- Collinearity filter: skips points on straight paths (within 5m tolerance)
- Stationary dedup: saves one "still here" point, skips the rest
- Network check: won't attempt sync without connectivity
- Exponential backoff: 5s → 10s → 20s → ... → 5min max on failures
- Synchronous flush: up to 10s blocking sync on service destroy
- SQLite buffer: survives app kill, reboot, network outages (5,000 point cap)
- Boot restart: auto-resumes tracking after reboot (with permission check)
- Modern battery API: uses BatteryManager.getIntProperty, not registerReceiver

## Setup

### 1. Open in Android Studio

Open the `android-tracker` folder. Wait for Gradle sync.

### 2. Update Supabase credentials (if needed)

In `TrackerApp.kt`, update:
```kotlin
const val SUPABASE_URL = "https://your-project.supabase.co"
const val SUPABASE_KEY = "your-anon-key"
const val DASHBOARD_KEY = "your-password"
```

### 3. Build APK

Build → Build Bundle(s)/APK(s) → Build APK(s)

Output: `app/build/outputs/apk/debug/app-debug.apk`

For release: Build → Generate Signed APK (requires keystore).

### 4. Install on phone

Transfer APK → enable "Install from unknown sources" → install → open.

### 5. Grant permissions

The app will request in order:
1. Notification permission (Android 13+)
2. Location → must select "Allow all the time"
3. Battery optimization → select "Don't optimize"

### 6. Register on dashboard

Copy the Device ID shown in the app → open dashboard → Add Device → paste it.

## Configuration

All constants are in `TrackerApp.kt`:

| Setting | Default | Description |
|---------|---------|-------------|
| MOVING_INTERVAL_MS | 30,000 | GPS interval when moving (ms) |
| STATIONARY_INTERVAL_MS | 120,000 | GPS interval when still (ms) |
| SYNC_TIMER_MS | 120,000 | How often to sync to server (ms) |
| SYNC_BATCH_SIZE | 20 | Points per upload batch |
| STATIONARY_THRESHOLD_M | 10 | Movement < this = stationary (meters) |
| COLLINEAR_TOLERANCE_M | 5 | Skip points within this of a straight line (meters) |

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| App killed after a few minutes | Battery optimization | Disable for this app in Android Settings |
| "Tracking Active" but no data | Location set to "Only while using" | Change to "Allow all the time" |
| Points accumulating, not syncing | No internet | Will auto-sync when connectivity returns |
| Google Play Protect warning | Sideloaded APK | Whitelist in Play Protect settings |
| Device ID changed | App data cleared or different device | Re-register the new ID on the dashboard |

## Files

```
android-tracker/
├── .gitignore
├── build.gradle                    # Project-level: AGP + Kotlin plugins
├── settings.gradle                 # Module configuration
├── gradle.properties               # JVM + AndroidX settings
├── app/
│   ├── build.gradle                # App-level: dependencies (no Room, no kapt)
│   ├── proguard-rules.pro          # R8 rules for release builds
│   └── src/main/
│       ├── AndroidManifest.xml     # Permissions, service, receiver
│       ├── java/com/bwanabet/tracker/
│       │   ├── TrackerApp.kt       # Application class + config
│       │   ├── MainActivity.kt     # UI + permissions + service control
│       │   ├── LocationService.kt  # GPS + adaptive intervals + sync
│       │   ├── LocationDatabase.kt # SQLite buffer (singleton)
│       │   └── BootReceiver.kt     # Reboot auto-restart
│       └── res/
│           ├── drawable/
│           │   ├── ic_notification.xml  # Notification icon (vector)
│           │   └── logo_bg.xml          # App logo background
│           ├── layout/
│           │   └── activity_main.xml    # Main screen layout
│           └── values/
│               ├── strings.xml
│               └── themes.xml
```
