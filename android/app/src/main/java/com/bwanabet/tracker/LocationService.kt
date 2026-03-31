package com.bwanabet.tracker

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class LocationService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationDb: LocationDatabase
    private lateinit var deviceId: String
    private lateinit var locationCallback: LocationCallback

    private val syncExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val isSyncing = AtomicBoolean(false)

    private var isTrackingStarted = false
    private var isSyncTimerRunning = false

    private val consecutiveFailures = AtomicInteger(0)
    private val nextRetryTime = AtomicLong(0)

    private var lastLocation: Location? = null
    private var stationaryCount = 0
    private var currentIntervalMs = TrackerApp.MOVING_INTERVAL_MS
    private var isStationary = false

    private var prevSavedLat = 0.0
    private var prevSavedLng = 0.0
    private var lastSavedLat = 0.0
    private var lastSavedLng = 0.0
    private var savedPointCount = 0

    private var syncTimerRunnable: Runnable? = null

    companion object {
        private const val TAG = "LocationService"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationDb = LocationDatabase.getInstance(this)
        deviceId = getSharedPreferences("tracker", MODE_PRIVATE)
            .getString("device_id", "unknown") ?: "unknown"

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    processLocation(location)
                }
            }
        }

        Log.d(TAG, "Service created for device: $deviceId")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        if (!isTrackingStarted) {
            isTrackingStarted = true
            startLocationUpdates()
            Log.d(TAG, "Location updates started")
        }

        if (!isSyncTimerRunning) {
            isSyncTimerRunning = true
            startSyncTimer()
            Log.d(TAG, "Sync timer started")
        }

        scheduleSyncAttempt()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroying")
        isTrackingStarted = false
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing updates", e)
        }
        stopSyncTimer()

        if (isNetworkAvailable()) {
            val latch = CountDownLatch(1)
            syncExecutor.execute {
                try {
                    syncAllPending()
                } catch (e: Exception) {
                    Log.e(TAG, "Final flush error", e)
                } finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(10, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Flush interrupted")
            }
        }

        syncExecutor.shutdownNow()
        getSharedPreferences("tracker", MODE_PRIVATE).edit().putBoolean("tracking_enabled", false).apply()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pending = try {
            locationDb.pendingCount()
        } catch (e: Exception) {
            0
        }
        val mode = if (isStationary) "Stationary" else "Moving"
        val interval = currentIntervalMs / 1000

        return NotificationCompat.Builder(this, TrackerApp.CHANNEL_ID)
            .setContentTitle("BwanaBet Tracker")
            .setContentText("$mode · ${interval}s · $pending pending")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        try {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            mgr.notify(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Notification update failed", e)
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            currentIntervalMs
        ).apply {
            setMinUpdateIntervalMillis(TrackerApp.FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "No location permission", e)
            stopSelf()
        }
    }

    private fun processLocation(location: Location) {
        Log.d(TAG, "GPS fix: ${location.latitude}, ${location.longitude} acc=${location.accuracy}")

        val prev = lastLocation
        if (prev != null) {
            val distMoved = prev.distanceTo(location)

            // Reject GPS spikes: if implied speed exceeds MAX_SPEED_MS, discard
            val timeDeltaS = (location.time - prev.time) / 1000.0
            if (timeDeltaS > 0) {
                val impliedSpeed = distMoved / timeDeltaS
                if (impliedSpeed > TrackerApp.MAX_SPEED_MS) {
                    Log.d(TAG, "Spike rejected: ${distMoved.toInt()}m in ${timeDeltaS.toInt()}s = ${(impliedSpeed * 3.6).toInt()} km/h")
                    return
                }
            }

            if (distMoved < TrackerApp.STATIONARY_THRESHOLD_M) {
                stationaryCount++
                if (stationaryCount >= TrackerApp.STATIONARY_COUNT_TRIGGER && !isStationary) {
                    isStationary = true
                    switchInterval(TrackerApp.STATIONARY_INTERVAL_MS)
                }
            } else {
                if (isStationary) {
                    isStationary = false
                    switchInterval(TrackerApp.MOVING_INTERVAL_MS)
                }
                stationaryCount = 0
            }

            if (isStationary && distMoved < TrackerApp.STATIONARY_THRESHOLD_M
                && stationaryCount > TrackerApp.STATIONARY_COUNT_TRIGGER
            ) {
                if (stationaryCount % 2 == 0) {
                    Log.d(TAG, "Heartbeat point (stationary)")
                } else {
                    lastLocation = location
                    return
                }
            }

            if (savedPointCount >= 2 && isCollinear(
                    prevSavedLat, prevSavedLng,
                    lastSavedLat, lastSavedLng,
                    location.latitude, location.longitude,
                    TrackerApp.COLLINEAR_TOLERANCE_M
                )
            ) {
                lastLocation = location
                return
            }
        }

        lastLocation = location

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val point = JSONObject().apply {
            put("device_id", deviceId)
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy)
            put("speed", location.speed)
            put("bearing", location.bearing)
            put("altitude", location.altitude)
            put("battery_level", getBatteryLevel())
            put("network_type", if (location.accuracy < 20) "gps" else "network")
            put("recorded_at", dateFormat.format(Date(location.time)))
        }

        prevSavedLat = lastSavedLat
        prevSavedLng = lastSavedLng
        lastSavedLat = location.latitude
        lastSavedLng = location.longitude
        savedPointCount++

        locationDb.insertLocation(point)
        Log.d(TAG, "Saved point #$savedPointCount for $deviceId")
        updateNotification()
    }

    @Suppress("MissingPermission")
    private fun switchInterval(newIntervalMs: Long) {
        if (newIntervalMs == currentIntervalMs) return
        Log.d(TAG, "Interval: ${currentIntervalMs / 1000}s -> ${newIntervalMs / 1000}s")
        currentIntervalMs = newIntervalMs
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing updates", e)
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            currentIntervalMs
        ).apply {
            setMinUpdateIntervalMillis(TrackerApp.FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission lost", e)
        }
        updateNotification()
    }

    private fun isCollinear(
        aLat: Double, aLng: Double, bLat: Double, bLng: Double,
        cLat: Double, cLng: Double, toleranceM: Double
    ): Boolean {
        val mPerDeg = 111320.0
        val cosLat = Math.cos(Math.toRadians((aLat + cLat) / 2.0))
        val ax = aLng * mPerDeg * cosLat
        val ay = aLat * mPerDeg
        val bx = bLng * mPerDeg * cosLat
        val by = bLat * mPerDeg
        val cx = cLng * mPerDeg * cosLat
        val cy = cLat * mPerDeg
        val abLen = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
        if (abLen < 1.0) return true
        val dist = Math.abs((by - ay) * cx - (bx - ax) * cy + bx * ay - by * ax) / abLen
        return dist < toleranceM
    }

    private fun startSyncTimer() {
        syncTimerRunnable = object : Runnable {
            override fun run() {
                scheduleSyncAttempt()
                handler.postDelayed(this, TrackerApp.SYNC_TIMER_MS)
            }
        }
        handler.postDelayed(syncTimerRunnable!!, TrackerApp.SYNC_TIMER_MS)
    }

    private fun stopSyncTimer() {
        isSyncTimerRunning = false
        syncTimerRunnable?.let { handler.removeCallbacks(it) }
        syncTimerRunnable = null
    }

    private fun scheduleSyncAttempt() {
        val now = System.currentTimeMillis()
        val retryAt = nextRetryTime.get()
        if (now < retryAt) {
            handler.postDelayed({ triggerSync() }, retryAt - now)
            return
        }
        triggerSync()
    }

    private fun triggerSync() {
        if (isSyncing.getAndSet(true)) return
        syncExecutor.execute {
            try {
                if (!isNetworkAvailable()) {
                    Log.d(TAG, "No network - skipping sync")
                    return@execute
                }
                syncPendingBatch()
            } catch (e: Exception) {
                Log.e(TAG, "Sync exception", e)
                applyBackoff()
            } finally {
                isSyncing.set(false)
            }
        }
    }

    private fun syncPendingBatch() {
        val batch = locationDb.getPendingBatch(TrackerApp.SYNC_BATCH_SIZE)
        if (batch.isEmpty()) {
            Log.d(TAG, "No pending points to sync")
            return
        }

        val ids = batch.map { it.first }
        val jsonArray = JSONArray()
        batch.forEach { jsonArray.put(it.second) }

        Log.d(TAG, "Syncing ${batch.size} points to Supabase...")

        val url = URL("${TrackerApp.SUPABASE_URL}/rest/v1/locations")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", TrackerApp.SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${TrackerApp.SUPABASE_KEY}")
            conn.setRequestProperty("x-dashboard-key", TrackerApp.DASHBOARD_KEY)
            conn.setRequestProperty("Prefer", "return=minimal,resolution=ignore-duplicates")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            conn.outputStream.bufferedWriter().use { it.write(jsonArray.toString()) }
            val code = conn.responseCode

            when {
                code in 200..299 -> {
                    locationDb.deleteSynced(ids)
                    consecutiveFailures.set(0)
                    nextRetryTime.set(0)
                    Log.d(TAG, "Synced ${batch.size} points OK (${locationDb.pendingCount()} remaining)")
                    if (locationDb.pendingCount() > 0) syncPendingBatch()
                    handler.post { updateNotification() }
                }
                code == 409 -> {
                    locationDb.deleteSynced(ids)
                    consecutiveFailures.set(0)
                    nextRetryTime.set(0)
                    Log.d(TAG, "Duplicates (409) - cleared locally")
                }
                else -> {
                    Log.e(TAG, "Sync failed: HTTP $code")
                    applyBackoff()
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun syncAllPending() {
        var remaining = locationDb.pendingCount()
        var attempts = 0
        while (remaining > 0 && attempts < 10) {
            syncPendingBatch()
            val newRemaining = locationDb.pendingCount()
            if (newRemaining >= remaining) break
            remaining = newRemaining
            attempts++
        }
        Log.d(TAG, "Final flush: $remaining points still pending")
    }

    private fun applyBackoff() {
        val failures = consecutiveFailures.incrementAndGet()
        val delay = minOf(
            TrackerApp.INITIAL_RETRY_DELAY_MS * (1L shl minOf(failures - 1, 6)),
            TrackerApp.MAX_RETRY_DELAY_MS
        )
        nextRetryTime.set(System.currentTimeMillis() + delay)
        Log.d(TAG, "Backoff #$failures - next retry in ${delay / 1000}s")
    }

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = cm.activeNetworkInfo
            info != null && info.isConnected
        } catch (e: Exception) {
            true
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1
        }
    }
}