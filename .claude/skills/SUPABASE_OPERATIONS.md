# Skill: Supabase Operations

> How to work with Supabase in this project. Reference for any database-related task.

---

## Project Details

- **Project ID:** `izgpyefzkyrtzjsnglvu`
- **URL:** `https://izgpyefzkyrtzjsnglvu.supabase.co`
- **Auth model:** Anon key (public, safe in frontend) + dashboard key (custom header)

---

## Known Tables

| Table | Purpose | Accessed By |
|-------|---------|-------------|
| `devices` | Registered devices (device_id, name, assigned_to, phone) | Dashboard, Simulator, Android (indirectly) |
| `locations` | GPS location points (device_id, lat, lng, accuracy, speed, battery, etc.) | Android (write), Dashboard (read), Cleanup API (delete) |
| `app_settings` | App configuration (dashboard password, etc.) | Dashboard |
| `app_versions` | APK version tracking for OTA updates (version_code, version_name, apk_url) | Android UpdateChecker |

---

## REST API Patterns

### Standard Headers
```javascript
// Frontend (anon key)
{
  'Content-Type': 'application/json',
  'apikey': SUPABASE_KEY,
  'Authorization': 'Bearer ' + SUPABASE_KEY,
  'x-dashboard-key': DASHBOARD_KEY,
  'Prefer': 'return=representation'
}

// Server-side (service key - elevated)
{
  'apikey': SUPABASE_SERVICE_KEY,
  'Authorization': 'Bearer ' + SUPABASE_SERVICE_KEY,
  'Prefer': 'return=minimal'
}
```

### Common Operations

**Insert (with dedup):**
```
POST /rest/v1/tablename
Prefer: return=representation,resolution=ignore-duplicates
Body: JSON object or array
```

**Select with filters:**
```
GET /rest/v1/tablename?column=eq.value&select=col1,col2&order=col.desc&limit=N
```

**Delete with filter:**
```
DELETE /rest/v1/tablename?column=lt.value
Prefer: return=minimal
```

**Count:**
```
HEAD /rest/v1/tablename?filter
Prefer: count=exact
→ Read content-range header
```

---

## PostgREST Filter Syntax

| Operator | Meaning | Example |
|----------|---------|---------|
| `eq` | Equals | `?status=eq.active` |
| `neq` | Not equals | `?status=neq.deleted` |
| `gt` | Greater than | `?battery=gt.20` |
| `lt` | Less than | `?recorded_at=lt.2026-04-09T00:00:00Z` |
| `gte` | Greater or equal | `?speed=gte.5` |
| `lte` | Less or equal | `?accuracy=lte.50` |
| `like` | LIKE pattern | `?name=like.*lusaka*` |
| `in` | IN list | `?device_id=in.(dev1,dev2)` |
| `is` | IS NULL/TRUE/FALSE | `?deleted_at=is.null` |

---

## Realtime Subscriptions (Dashboard)

The dashboard uses Supabase JS SDK for realtime:
```javascript
supabase.channel('locations')
  .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'locations' }, handler)
  .subscribe()
```

---

## Data Retention

- **Cleanup cron** runs daily at midnight (UTC)
- Deletes location points older than **18 hours**
- Uses service key (elevated permissions)
- Defined in `api/cleanup.js`

---

## Schema Guidelines

- Timestamps: ISO 8601 strings (`recorded_at TEXT`)
- Coordinates: floating point (`latitude REAL`, `longitude REAL`)
- Money: DECIMAL(12,2) — not applicable in GPS tracker, but a BwanaBet-wide rule
- IDs: Use `device_id` (text, e.g., `bw-a1b2c3d4`) not auto-increment for device identity
- Dedup: Unique constraint on `(device_id, recorded_at)` in locations table

---

## Common Gotchas

1. **409 Conflict** on insert = duplicate. Not an error if using `resolution=ignore-duplicates`.
2. **Service key vs anon key:** Service key bypasses RLS. Only use server-side.
3. **Prefer header order matters:** `return=representation,resolution=ignore-duplicates` (comma-separated).
4. **PostgREST returns 200 for empty results.** Check the array length, not the status code.
5. **content-range header** for counts: format is `0-N/total` or `*/total` for HEAD requests.
