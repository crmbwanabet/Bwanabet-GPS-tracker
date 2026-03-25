export default async function handler(req, res) {
  const url = process.env.SUPABASE_URL;
  const serviceKey = process.env.SUPABASE_SERVICE_KEY;

  if (!url || !serviceKey) {
    return res.status(500).json({ error: 'Missing SUPABASE_URL or SUPABASE_SERVICE_KEY' });
  }

  const cutoff = new Date(Date.now() - 18 * 60 * 60 * 1000).toISOString();

  // Count rows to delete first
  const countRes = await fetch(
    `${url}/rest/v1/locations?recorded_at=lt.${cutoff}&select=id`,
    {
      method: 'HEAD',
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${serviceKey}`,
        'Prefer': 'count=exact'
      }
    }
  );
  const total = parseInt(countRes.headers.get('content-range')?.split('/')[1] || '0');

  if (total === 0) {
    return res.status(200).json({ deleted: 0, cutoff, message: 'Nothing to clean up' });
  }

  // Delete old locations
  const deleteRes = await fetch(
    `${url}/rest/v1/locations?recorded_at=lt.${cutoff}`,
    {
      method: 'DELETE',
      headers: {
        'apikey': serviceKey,
        'Authorization': `Bearer ${serviceKey}`,
        'Prefer': 'return=minimal'
      }
    }
  );

  if (!deleteRes.ok) {
    const err = await deleteRes.text();
    return res.status(500).json({ error: 'Delete failed', details: err });
  }

  return res.status(200).json({
    deleted: total,
    cutoff,
    message: `Deleted ${total} location points older than 18 hours`
  });
}
