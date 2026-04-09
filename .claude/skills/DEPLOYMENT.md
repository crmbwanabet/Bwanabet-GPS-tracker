# Skill: Deployment

> Step-by-step procedures for deploying changes. Reference before any deployment task.

---

## Dashboard & API (Vercel)

### Auto-Deploy (Standard)
1. Commit changes to `main` branch
2. Push to GitHub: `git push origin main`
3. Vercel auto-deploys within ~30 seconds
4. Verify at the Vercel project URL

### Manual Deploy (Vercel CLI)
```bash
cd "C:\Users\USER\Desktop\Claude projects\GPS tracker"
vercel --prod
```

### Environment Variables
Set in **Vercel Dashboard → Project → Settings → Environment Variables**:

| Variable | Purpose | Where Used |
|----------|---------|------------|
| `SUPABASE_URL` | Supabase project URL | `api/config.js`, `api/cleanup.js` |
| `SUPABASE_KEY` | Supabase anon/publishable key | `api/config.js` |
| `SUPABASE_SERVICE_KEY` | Supabase service role key (elevated) | `api/cleanup.js` only |
| `DASHBOARD_KEY` | Password for dashboard login | `api/config.js` |

> After changing env vars, **redeploy** (Vercel Dashboard → Deployments → Redeploy).

### Post-Deploy Checklist
- [ ] Dashboard loads at `/`
- [ ] Login works with dashboard key
- [ ] Devices show up with correct status
- [ ] Map renders with markers
- [ ] Simulator works at `/simulator.html`

---

## Android App

### Build APK
1. Open `android/` folder in Android Studio
2. Wait for Gradle sync
3. Build → Build Bundle(s)/APK(s) → Build APK(s)
4. APK at: `android/app/build/outputs/apk/debug/app-debug.apk`

### Release APK
1. Build → Generate Signed Bundle/APK
2. Select APK
3. Use or create a keystore
4. Build release variant
5. APK at: `android/app/build/outputs/apk/release/app-release.apk`

### Install on Phone
1. Transfer APK to phone (USB, email, cloud link, or Supabase `app_versions` table)
2. Enable "Install from unknown sources" for the file manager
3. Open APK → Install
4. Grant permissions in order: Notification → Location ("Allow all the time") → Battery ("Don't optimize")
5. Copy Device ID from app → Register on dashboard

### OTA Update Flow
1. Build new APK with incremented `versionCode` in `android/app/build.gradle`
2. Upload APK to accessible URL (GitHub releases, Supabase storage, etc.)
3. Insert row into `app_versions` table:
   ```sql
   INSERT INTO app_versions (version_code, version_name, apk_url)
   VALUES (3, '2.1', 'https://example.com/tracker-v2.1.apk');
   ```
4. App checks on next launch and prompts user to update

### When to Rebuild APK
- Any change to files in `android/` directory
- Changed Supabase credentials in `TrackerApp.kt`
- Changed tracking intervals or thresholds
- Changed sync batch size or timing
- Added new features to the Android app

> Dashboard changes do NOT require APK rebuild — they deploy via Vercel.

---

## Supabase Changes

### Adding/Modifying Tables
1. Go to Supabase Dashboard → SQL Editor
2. Run migration SQL
3. Document the change in PROJECT_MEMORY.md changelog
4. If new table is accessed by Android: no change needed (uses REST API)
5. If new table is accessed by dashboard: update the HTML

### Adding RLS Policies
- Currently no RLS. If adding, test thoroughly — the Android app uses anon key directly.

---

## Git Workflow

```bash
# Standard flow
git add <specific-files>
git commit -m "descriptive message"
git push origin main
# Vercel auto-deploys
```

- Commit specific files, not `git add .` (avoid committing .env files)
- Never commit `.env` or `.env.local`
- Push to `main` for production deploy
