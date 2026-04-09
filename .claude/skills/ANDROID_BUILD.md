# Skill: Android Build & Distribution

> How to build, test, and distribute the Android tracking app. Reference for any Android task.

---

## Project Structure

```
android/
├── build.gradle                    # Project-level: AGP + Kotlin plugins
├── settings.gradle                 # Module configuration
├── gradle.properties               # JVM + AndroidX settings
├── gradlew / gradlew.bat           # Gradle wrapper
├── app/
│   ├── build.gradle                # App-level: dependencies, SDK versions, versionCode
│   ├── proguard-rules.pro          # R8 rules for release builds
│   └── src/main/
│       ├── AndroidManifest.xml     # Permissions, service, receiver declarations
│       ├── java/com/bwanabet/tracker/
│       │   ├── TrackerApp.kt       # App class + ALL config constants
│       │   ├── MainActivity.kt     # UI + permissions + service control
│       │   ├── LocationService.kt  # GPS + filters + sync (the core)
│       │   ├── LocationDatabase.kt # SQLite offline buffer
│       │   ├── BootReceiver.kt     # Reboot auto-restart
│       │   └── UpdateChecker.kt    # OTA update from Supabase
│       └── res/
│           ├── drawable/           # Icons
│           ├── layout/             # activity_main.xml
│           └── values/             # strings.xml, themes.xml
```

---

## Building

### Debug APK (for testing)
1. Open `android/` in Android Studio
2. Wait for Gradle sync to complete
3. Build → Build Bundle(s)/APK(s) → Build APK(s)
4. Output: `android/app/build/outputs/apk/debug/app-debug.apk`

### Release APK (for distribution)
1. Build → Generate Signed Bundle/APK
2. Choose APK
3. Create or select keystore file
4. Choose release build variant
5. Output: `android/app/build/outputs/apk/release/app-release.apk`

### Version Bumping
In `android/app/build.gradle`:
```groovy
versionCode 2     // Integer, increment for each release
versionName "2.1" // User-facing version string
```

> **Always increment `versionCode`** for OTA updates to work. `UpdateChecker` compares this value.

---

## Configuration Changes

All runtime constants are in `TrackerApp.kt` companion object. Common changes:

| What | Where | Impact |
|------|-------|--------|
| Supabase URL/key | `TrackerApp.kt` | Requires APK rebuild |
| Dashboard password | `TrackerApp.kt` | Requires APK rebuild |
| GPS interval (moving) | `MOVING_INTERVAL_MS` | Battery vs accuracy trade-off |
| GPS interval (stationary) | `STATIONARY_INTERVAL_MS` | Battery savings when still |
| Sync frequency | `SYNC_TIMER_MS` | Data freshness vs battery |
| Sync batch size | `SYNC_BATCH_SIZE` | Network efficiency |
| Motion threshold | `STATIONARY_THRESHOLD_M` | Sensitivity to movement |
| Collinear tolerance | `COLLINEAR_TOLERANCE_M` | Trail detail vs data volume |
| Consensus count | `CONSENSUS_COUNT` | GPS stabilization time |
| Consensus radius | `CONSENSUS_RADIUS_M` | Tolerance for GPS jitter |

---

## Permissions Required

| Permission | Why | Fallback |
|------------|-----|----------|
| `POST_NOTIFICATIONS` (Android 13+) | Foreground service notification | Service works without, but no visible indicator |
| `ACCESS_FINE_LOCATION` | GPS tracking | App cannot function without this |
| `ACCESS_COARSE_LOCATION` | Required alongside fine | Same |
| `ACCESS_BACKGROUND_LOCATION` | Track when app is closed | Works only in foreground |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Keep service alive | Required for foreground service |
| `RECEIVE_BOOT_COMPLETED` | Restart after reboot | No auto-restart |
| `REQUEST_INSTALL_PACKAGES` | OTA update install | User must install manually |
| Battery optimization exemption | Prevent OS from killing service | Service may be killed |

---

## Distribution

### Sideloading (Current Method)
1. Transfer APK to phone (USB, WhatsApp, email, cloud link)
2. Phone: Settings → Install unknown apps → Allow for file manager
3. Open APK → Install
4. First launch: grant all permissions

### OTA Updates (Built-in)
1. Build new APK with higher `versionCode`
2. Host APK at a public URL
3. Insert into Supabase `app_versions` table:
   ```sql
   INSERT INTO app_versions (version_code, version_name, apk_url)
   VALUES (3, '2.1', 'https://url-to-apk/tracker.apk');
   ```
4. App checks on launch → shows dialog → downloads via DownloadManager → prompts install

---

## Testing on Device

### Logcat Filters
```
Tag: LocationService    — GPS processing, sync status
Tag: LocationDatabase   — SQLite operations
Tag: BootReceiver       — Reboot recovery
Tag: UpdateChecker      — OTA update checks
Tag: TrackerApp         — App initialization
```

### What to Verify
- [ ] App installs and opens
- [ ] Device ID displayed
- [ ] Permissions granted (all the time, not just while using)
- [ ] Battery optimization disabled
- [ ] "Tracking Active" shown after start
- [ ] Points appear on dashboard within 15-30 seconds
- [ ] App survives screen off (check after 5 minutes)
- [ ] App survives reboot (if tracking was enabled)
- [ ] Pending count goes to 0 when syncing
- [ ] Offline mode: points buffer, sync when reconnected

---

## Gotchas

1. **Samsung devices** aggressively kill background services. Need "Unrestricted" battery setting.
2. **Xiaomi/MIUI** has additional "autostart" permission. May need manual enable.
3. **Google Play Protect** warns about sideloaded APKs. Users must whitelist.
4. **Android 14+** requires `FOREGROUND_SERVICE_LOCATION` type in manifest AND at runtime.
5. **Database migration** in `onUpgrade` currently drops and recreates. All buffered data is lost on schema change.
