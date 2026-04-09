# Skill: Debugging

> How to diagnose and fix issues in this project. Reference when troubleshooting.

---

## General Approach

1. **Read the error first.** User often sends screenshots or console output.
2. **Reproduce mentally.** Understand the data flow that led to the error.
3. **Fix the root cause.** Don't patch symptoms.
4. **Test the fix.** Describe how to verify (or verify yourself if possible).
5. **Keep it brief.** One sentence explaining what went wrong, then the fix.

---

## Dashboard Issues

### "Failed to load config" on boot
- **Cause:** `/api/config` returned error or missing env vars
- **Check:** Vercel env vars (`SUPABASE_URL`, `SUPABASE_KEY`, `DASHBOARD_KEY`)
- **Fix:** Set env vars in Vercel Dashboard → Redeploy

### Map not showing
- **Cause:** Leaflet CSS/JS CDN blocked, or map container has 0 height
- **Check:** Browser console for 404s on Leaflet resources
- **Fix:** Ensure CSS `height` is set on map container, check CDN availability

### Devices showing "offline" when they're sending data
- **Cause:** Timezone mismatch, or `last_seen` threshold too aggressive
- **Check:** Compare `recorded_at` timestamps in Supabase with current UTC time
- **Fix:** Ensure Android sends UTC timestamps, dashboard compares in UTC

### Login not working
- **Cause:** `DASHBOARD_KEY` env var mismatch between Vercel and what user enters
- **Check:** `/api/config` response in browser devtools
- **Fix:** Update env var in Vercel, redeploy

### CORS errors
- **Cause:** Supabase key invalid, or wrong Supabase URL
- **Check:** Network tab for actual error response
- **Fix:** Verify `SUPABASE_URL` and `SUPABASE_KEY` env vars

---

## Android Issues

### App killed after a few minutes
- **Cause:** Battery optimization
- **Fix:** Settings → Battery → App → "Don't optimize" (or "Unrestricted")

### "Tracking Active" but no data on dashboard
- **Cause 1:** Location set to "Only while using" → needs "Allow all the time"
- **Cause 2:** No internet → data buffered in SQLite, will sync when connected
- **Cause 3:** Supabase key mismatch → check `TrackerApp.kt` constants
- **Debug:** Check Logcat for `LocationService` tag

### Device ID changed unexpectedly
- **Cause:** App data cleared, or installed on different device
- **Fix:** Re-register the new ID on the dashboard. Device ID is based on `ANDROID_ID`.

### Points accumulating but not syncing
- **Cause:** Network issue or Supabase errors
- **Debug:** Logcat filter `LocationService` — look for HTTP error codes
- **Check:** Is Supabase accessible from the device's network?

### Exponential backoff stuck
- **Cause:** Persistent server error, backoff grows to 5 min max
- **Fix:** Fix server-side issue. Backoff resets on next successful sync.

### GPS accuracy issues / jumping points
- **Cause:** Consensus filter too loose, or collinear filter too aggressive
- **Tune:** `CONSENSUS_RADIUS_M` (25m), `COLLINEAR_TOLERANCE_M` (2m) in `TrackerApp.kt`

---

## API Issues

### Cleanup cron not running
- **Cause:** Vercel cron misconfigured or `SUPABASE_SERVICE_KEY` not set
- **Check:** Vercel Dashboard → Crons tab
- **Fix:** Ensure `vercel.json` has cron entry, env var `SUPABASE_SERVICE_KEY` is set

### Cleanup deleting too much/too little
- **Cause:** 18-hour cutoff in `cleanup.js` (`18 * 60 * 60 * 1000`)
- **Fix:** Adjust the multiplier in `api/cleanup.js`

---

## Supabase Issues

### "relation does not exist" error
- **Cause:** Table hasn't been created yet
- **Fix:** Run the CREATE TABLE SQL in Supabase SQL Editor

### Duplicate key violations
- **Cause:** Normal — handled by `Prefer: resolution=ignore-duplicates` header
- **Not a bug** if using the correct Prefer header. Only investigate if inserts are actually failing.

### Rate limiting
- **Cause:** Too many requests from Android devices
- **Check:** Supabase Dashboard → API usage
- **Fix:** Increase `SYNC_TIMER_MS`, decrease `SYNC_BATCH_SIZE`, or upgrade Supabase plan

---

## Debugging Tools

| Tool | What For |
|------|----------|
| Browser DevTools → Console | Dashboard JS errors |
| Browser DevTools → Network | API calls, Supabase requests |
| Vercel Dashboard → Logs | Serverless function errors |
| Vercel Dashboard → Deployments | Deploy status, build errors |
| Android Studio → Logcat | Filter by `LocationService`, `LocationDatabase`, `UpdateChecker` |
| Supabase Dashboard → Table Editor | Inspect data directly |
| Supabase Dashboard → SQL Editor | Run queries, check data |
| `/simulator.html` | Test the full pipeline without a real phone |
