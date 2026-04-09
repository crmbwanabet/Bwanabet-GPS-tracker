# Skill: Feature Development

> How to approach building new features in this project. Reference before starting any feature work.

---

## Before You Start

1. **Read the relevant files.** Don't guess what's there — open and read.
2. **Understand the data flow.** Where does data come from? Where does it go?
3. **Check PROJECT_MEMORY.md** for context on past decisions.
4. **Match existing patterns.** Don't introduce new paradigms.

---

## Planning a Feature

### Questions to Answer First
- Does it affect Android, Dashboard, API, or Supabase (or multiple)?
- Does it require a new Supabase table or column?
- Does it require a new env var?
- Does it require an APK rebuild?
- Can it be done without adding dependencies?

### Scope Control
- **Do the minimum that works.** Don't gold-plate.
- **Ship incremental.** Get v1 working, then iterate if the user wants more.
- **Don't add "nice to haves"** unless asked. No extra config options, no feature flags.

---

## Dashboard Features

### Adding a New Panel/Section
1. Add HTML structure inside `<body>` in `public/index.html`
2. Add CSS in the existing `<style>` block
3. Add JS logic in the existing `<script>` block
4. Follow the existing component patterns (cards, sections, modals)
5. Use CSS variables for colors
6. Test on mobile viewport (375px width)

### Adding a New Data View
1. Query Supabase via the JS SDK (already initialized)
2. Render results using DOM manipulation (no template engine)
3. If realtime needed: subscribe to Supabase channel

### Adding a New Page
- Rare. Prefer adding to `index.html`.
- If truly needed: create `public/newpage.html`, same pattern as `simulator.html`

---

## Android Features

### Adding a New Feature to the App
1. Check if it needs new permissions → add to `AndroidManifest.xml`
2. Check if it needs new UI → update `activity_main.xml`
3. Check if it needs new data → add to `LocationDatabase` schema
4. Check if it needs new config → add constant to `TrackerApp.kt`
5. Implement in the appropriate class (don't create new classes unless truly needed)

### Adding a New Data Field
1. Add constant/column to `LocationDatabase.kt` (CREATE TABLE)
2. Add to `insertLocation()` method
3. Add to `getPendingBatch()` method
4. Add to `processLocation()` in `LocationService.kt`
5. Increment `DB_VERSION` and handle migration in `onUpgrade`
6. **Important:** Database version bump means existing data is lost (current onUpgrade drops table)

### Adding a New API Call
- Use `HttpURLConnection` (no OkHttp)
- Use `JSONObject`/`JSONArray` for JSON (no Gson)
- Run on background thread (never on main thread)
- Handle network errors gracefully
- Include Supabase headers: `apikey`, `Authorization`, `x-dashboard-key`

---

## API Features

### Adding a New Serverless Function
1. Create `api/functionname.js`
2. Export default handler: `export default function handler(req, res) { ... }`
3. Add to `vercel.json` rewrites if needed
4. If it needs elevated access: use `SUPABASE_SERVICE_KEY`
5. If it should run on schedule: add to `vercel.json` crons

---

## Supabase Schema Changes

### Adding a New Table
1. Write CREATE TABLE SQL
2. Run in Supabase SQL Editor
3. Test with a manual INSERT
4. Document in PROJECT_MEMORY.md

### Adding a Column to Existing Table
1. ALTER TABLE in Supabase SQL Editor
2. Update all code that queries/inserts into that table
3. Make column nullable or have a default (for backward compat with older APKs)

---

## Testing Checklist

### Dashboard Change
- [ ] Page loads without console errors
- [ ] Login works
- [ ] Feature works on desktop
- [ ] Feature works on mobile (375px)
- [ ] Existing features still work

### Android Change
- [ ] App builds without errors
- [ ] APK installs on phone
- [ ] Permissions flow works
- [ ] Tracking starts and data appears on dashboard
- [ ] App survives reboot
- [ ] Offline → online sync works

### API Change
- [ ] Function responds correctly (test with curl or browser)
- [ ] Error cases handled
- [ ] Env vars documented

---

## Deployment Steps for Features

1. **Dashboard only:** Commit → Push → Auto-deploy → Verify
2. **API only:** Commit → Push → Auto-deploy → Set env vars if new → Verify
3. **Android only:** Edit code → Build APK → Sideload → Test
4. **Supabase only:** Run SQL → Verify in table editor
5. **Multi-component:** Do Supabase first, then API, then Dashboard, then Android (dependency order)
