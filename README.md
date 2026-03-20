# BwanaBet GPS Tracker — Vercel Deployment

## Project Structure

```
gps-tracker/
├── api/
│   └── config.js          # Serverless function — serves env vars to the frontend
├── public/
│   ├── index.html          # Dashboard (main page)
│   └── simulator.html      # Device simulator (testing)
├── vercel.json             # Vercel config
└── package.json
```

## Deploy to Vercel

### Option A: Vercel CLI

```bash
cd gps-tracker
vercel
```

When prompted, link to a new project or an existing one.

### Option B: GitHub

1. Push this folder to a GitHub repo
2. Go to vercel.com → New Project → Import the repo
3. It auto-deploys

## Environment Variables

Set these in **Vercel Dashboard → Project → Settings → Environment Variables**:

| Variable        | Value                                          | Example                                           |
|----------------|------------------------------------------------|---------------------------------------------------|
| `SUPABASE_URL` | Your Supabase project URL                      | `https://izgpyefzkyrtzjsnglvu.supabase.co`       |
| `SUPABASE_KEY` | Your Supabase anon/publishable key             | `eyJhbGciOiJIUzI1Ni...`                          |
| `DASHBOARD_KEY`| The password set in `app_settings` table        | `bwanabet2026!`                                   |

After adding env vars, redeploy (Vercel → Deployments → Redeploy).

## URLs After Deploy

- **Dashboard**: `https://your-project.vercel.app/`
- **Simulator**: `https://your-project.vercel.app/simulator.html`

## Android App

The Android app still has credentials hardcoded in `TrackerApp.kt` since it runs on-device and can't call your Vercel API. Update these directly in the Kotlin file before building the APK.
