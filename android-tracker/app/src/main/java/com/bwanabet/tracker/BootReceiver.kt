package com.bwanabet.tracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("tracker", Context.MODE_PRIVATE)
        val wasTrackingEnabled = prefs.getBoolean("tracking_enabled", false)

        if (!wasTrackingEnabled) {
            Log.d(TAG, "Boot completed but tracking was not enabled \u2014 skipping")
            return
        }

        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) {
            Log.w(TAG, "Boot completed but location permission revoked \u2014 disabling tracking")
            prefs.edit().putBoolean("tracking_enabled", false).apply()
            return
        }

        Log.d(TAG, "Boot completed \u2014 restarting tracking service")
        val serviceIntent = Intent(context, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
