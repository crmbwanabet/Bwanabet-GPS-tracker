# BwanaBet GPS Tracker — Claude Instructions

## First Steps Every Session

1. Read `.claude/PROJECT_MEMORY.md` — project state, architecture, changelog, open issues
2. Read `.claude/PERSONALITY.md` — who the user is and how to work with them
3. Reference skill files in `.claude/skills/` as needed for the task at hand

## Skill Files

| File | When to Reference |
|------|-------------------|
| `.claude/skills/CODING_STANDARDS.md` | Before writing any code |
| `.claude/skills/DEPLOYMENT.md` | Before deploying anything |
| `.claude/skills/DEBUGGING.md` | When troubleshooting issues |
| `.claude/skills/FEATURE_DEVELOPMENT.md` | Before building new features |
| `.claude/skills/SUPABASE_OPERATIONS.md` | For any database work |
| `.claude/skills/ANDROID_BUILD.md` | For Android app changes |

## Project Quick Reference

- **Repo:** `crmbwanabet/Bwanabet-GPS-tracker` (GitHub)
- **Dashboard:** `bwanabet-gps-tracker.vercel.app` (auto-deploys from `main`)
- **Supabase project:** `izgpyefzkyrtzjsnglvu`
- **Android package:** `com.bwanabet.tracker`
- **Current Android version:** v2.0 (versionCode 11)

## Key Rules

- Dashboard is a single HTML file (`public/index.html`). No frameworks, no build tools.
- Android app uses raw SQLite, HttpURLConnection, JSONObject. No Room, no Retrofit, no Gson.
- All Android config constants live in `TrackerApp.kt`.
- Credentials are hardcoded in Android (by design — can't call Vercel at boot).
- Always update `.claude/PROJECT_MEMORY.md` changelog after significant changes.
- Be direct. Implement first, explain briefly. The user prefers action over discussion.

## Available MCP Servers (project-scoped)

- **Playwright** — browser automation, screenshots, UI testing of dashboard
- **Vercel** — deployment logs and status (OAuth on first use)
- **Mapbox** — geocoding, reverse geocoding, route matching (OAuth on first use)
- **Android MCP** — ADB device control (needs USB connection)
- **Supabase** — database queries, migrations, edge functions (global, always available)
- **GitHub** — repo management, PRs, releases (global, always available)
