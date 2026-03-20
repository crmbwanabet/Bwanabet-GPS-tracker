export default function handler(req, res) {
  // These are read from Vercel environment variables
  const config = {
    supabaseUrl: process.env.SUPABASE_URL,
    supabaseKey: process.env.SUPABASE_KEY,
    dashboardKey: process.env.DASHBOARD_KEY
  };

  // Only return config if env vars are set
  if (!config.supabaseUrl || !config.supabaseKey) {
    return res.status(500).json({ error: 'Environment variables not configured' });
  }

  res.setHeader('Cache-Control', 's-maxage=60');
  res.status(200).json(config);
}
