package com.bwanabet.tracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class TrackerApp : Application() {

    companion object {
        const val CHANNEL_ID = "bwanabet_tracker_channel"

        // ---- Supabase Config ----
        const val SUPABASE_URL = "https://izgpyefzkyrtzjsnglvu.supabase.co"
        const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml6Z3B5ZWZ6a3lydHpqc25nbHZ1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM5NjYzNTcsImV4cCI6MjA4OTU0MjM1N30.8gopVKKHk7xVirR--eqqDlyAJSdiaWCttQLINqqQEGI"
        const val DASHBOARD_KEY = "bwanabet2026!"

        // ---- Location Intervals ----
        const val MOVING_INTERVAL_MS = 1000L        // 1s when moving (high-density trail)
        const val STATIONARY_INTERVAL_MS = 30000L   // 30s when still
        const val FASTEST_INTERVAL_MS = 500L        // 500ms floor for GPS updates

        // ---- Sync Settings ----
        const val SYNC_TIMER_MS = 15000L            // Sync every 15s (handles 1s density)
        const val SYNC_BATCH_SIZE = 100             // Points per upload
        const val MAX_RETRY_DELAY_MS = 300000L      // 5 min max backoff
        const val INITIAL_RETRY_DELAY_MS = 5000L    // 5 sec initial backoff

        // ---- Motion Detection ----
        const val STATIONARY_THRESHOLD_M = 10.0     // <10m moved = stationary
        const val STATIONARY_COUNT_TRIGGER = 3      // 3 readings to confirm

        // ---- Point Filtering ----
        const val ACCURACY_THRESHOLD_M = 50.0f      // Reject fixes with accuracy worse than 50m
        const val COLLINEAR_TOLERANCE_M = 2.0       // Skip mid-points within 2m of line
        const val CONSENSUS_COUNT = 2               // Need 2 fixes agreeing to confirm position
        const val CONSENSUS_RADIUS_M = 20.0         // Fixes must be within 20m of each other
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BwanaBet GPS tracking service"
                setShowBadge(false)
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }
}
