package com.bwanabet.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var deviceIdText: TextView
    private lateinit var pendingText: TextView
    private lateinit var toggleBtn: Button

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateUI()
            refreshHandler.postDelayed(this, 5000) // Refresh every 5 seconds
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_CODE = 1001
        private const val BACKGROUND_LOCATION_CODE = 1002
        private const val NOTIFICATION_PERMISSION_CODE = 1003
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        deviceIdText = findViewById(R.id.deviceIdText)
        pendingText = findViewById(R.id.pendingText)
        toggleBtn = findViewById(R.id.toggleBtn)

        deviceIdText.text = "Device ID: ${getOrCreateDeviceId()}"

        toggleBtn.setOnClickListener {
            if (isTrackingEnabled()) stopTracking() else checkPermissionsAndStart()
        }

        updateUI()

        UpdateChecker(this).checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        refreshHandler.postDelayed(refreshRunnable, 5000)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    // ============================================================
    // DEVICE ID — Stable across reinstalls via ANDROID_ID
    // ============================================================

    private fun getOrCreateDeviceId(): String {
        val prefs = getSharedPreferences("tracker", MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                "bw-${androidId.take(8)}"
            } else {
                "bw-${java.util.UUID.randomUUID().toString().take(8)}"
            }
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    // ============================================================
    // PERMISSIONS — Progressive: notification → location → background → battery
    // ============================================================

    private fun checkPermissionsAndStart() {
        // Step 1: Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_CODE
            )
            return
        }

        // Step 2: Fine + coarse location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
            return
        }

        // Step 3: Background location (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Toast.makeText(this, "Please select 'Allow all the time' on the next screen", Toast.LENGTH_LONG).show()
            }
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                BACKGROUND_LOCATION_CODE
            )
            return
        }

        // Step 4: Battery optimization exemption
        requestBatteryExemption()

        // Step 5: All good — start
        startTracking()
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    Toast.makeText(this,
                        "Please disable battery optimization for this app manually in Settings",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_CODE -> {
                // Notification is nice-to-have, continue regardless
                checkPermissionsAndStart()
            }
            LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkPermissionsAndStart()
                } else {
                    Toast.makeText(this, "Location permission is required for tracking", Toast.LENGTH_LONG).show()
                }
            }
            BACKGROUND_LOCATION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkPermissionsAndStart()
                } else {
                    Toast.makeText(this, "Background location recommended \u2014 tracking may stop when app is closed", Toast.LENGTH_LONG).show()
                    startTracking() // Start anyway, works in foreground
                }
            }
        }
    }

    // ============================================================
    // SERVICE CONTROL
    // ============================================================

    private fun startTracking() {
        getSharedPreferences("tracker", MODE_PRIVATE)
            .edit().putBoolean("tracking_enabled", true).apply()

        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI()
    }

    private fun stopTracking() {
        // The service will set tracking_enabled=false in its onDestroy
        stopService(Intent(this, LocationService::class.java))
        // Also set it here for immediate UI feedback
        getSharedPreferences("tracker", MODE_PRIVATE)
            .edit().putBoolean("tracking_enabled", false).apply()
        updateUI()
    }

    private fun isTrackingEnabled(): Boolean {
        return getSharedPreferences("tracker", MODE_PRIVATE)
            .getBoolean("tracking_enabled", false)
    }

    // ============================================================
    // UI UPDATE
    // ============================================================

    private fun updateUI() {
        val running = isTrackingEnabled()

        statusText.text = if (running) "\u25cf Tracking Active" else "\u25cb Tracking Stopped"
        statusText.setTextColor(if (running) 0xFF22C55E.toInt() else 0xFFEF4444.toInt())
        toggleBtn.text = if (running) "Stop Tracking" else "Start Tracking"

        // Update pending count on a background thread
        Thread {
            val count = try {
                LocationDatabase.getInstance(this@MainActivity).pendingCount()
            } catch (e: Exception) {
                -1
            }
            runOnUiThread {
                pendingText.text = when {
                    count < 0 -> "Pending: \u2014"
                    count == 0 -> "All synced"
                    else -> "$count points pending sync"
                }
                pendingText.setTextColor(
                    if (count > 0) 0xFFF5C518.toInt() else 0xFF52525B.toInt()
                )
            }
        }.start()
    }
}
