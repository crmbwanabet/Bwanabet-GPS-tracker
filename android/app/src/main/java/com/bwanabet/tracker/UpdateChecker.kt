package com.bwanabet.tracker

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class UpdateChecker(private val activity: AppCompatActivity) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val APK_FILENAME = "bwanabet-tracker-update.apk"
        private const val UPDATE_CHANNEL_ID = "bwanabet_update_channel"
        private const val UPDATE_NOTIFICATION_ID = 2001
    }

    private val executor = Executors.newSingleThreadExecutor()

    fun checkForUpdate() {
        executor.execute {
            try {
                val currentVersionCode = getCurrentVersionCode()
                Log.d(TAG, "Current versionCode: $currentVersionCode")

                val update = fetchLatestVersion()
                if (update == null) {
                    Log.d(TAG, "No version info from server")
                    return@execute
                }

                val serverVersionCode = update.first
                val apkUrl = update.second
                val versionName = update.third

                Log.d(TAG, "Server versionCode: $serverVersionCode, url: $apkUrl")

                if (serverVersionCode > currentVersionCode) {
                    activity.runOnUiThread {
                        showUpdateDialog(versionName, apkUrl)
                    }
                } else {
                    Log.d(TAG, "App is up to date")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
            }
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    private fun fetchLatestVersion(): Triple<Int, String, String>? {
        val url = URL(
            "${TrackerApp.SUPABASE_URL}/rest/v1/app_versions" +
                "?select=version_code,version_name,apk_url" +
                "&order=version_code.desc&limit=1"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", TrackerApp.SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${TrackerApp.SUPABASE_KEY}")
            conn.setRequestProperty("x-dashboard-key", TrackerApp.DASHBOARD_KEY)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val code = conn.responseCode
            if (code != 200) {
                Log.e(TAG, "Server returned HTTP $code")
                return null
            }

            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            if (arr.length() == 0) return null

            val obj = arr.getJSONObject(0)
            return Triple(
                obj.getInt("version_code"),
                obj.getString("apk_url"),
                obj.optString("version_name", "")
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun showUpdateDialog(versionName: String, apkUrl: String) {
        val label = if (versionName.isNotBlank()) " (v$versionName)" else ""
        AlertDialog.Builder(activity)
            .setTitle("Update Available")
            .setMessage("A new version$label of BwanaBet Tracker is available. Update now?")
            .setPositiveButton("Update") { _, _ -> startDownload(apkUrl) }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    private fun startDownload(apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(
                    activity,
                    "Please allow app installs from this source",
                    Toast.LENGTH_LONG
                ).show()
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
                return
            }
        }

        // Delete old APK if it exists
        val oldFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            APK_FILENAME
        )
        if (oldFile.exists()) oldFile.delete()

        Toast.makeText(activity, "Downloading update...", Toast.LENGTH_SHORT).show()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("BwanaBet Tracker Update")
            .setDescription("Downloading new version...")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_FILENAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        activity.unregisterReceiver(this)
                    } catch (_: Exception) {}
                    showInstallNotification(downloadId)
                    installApk(downloadId)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            activity.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun getInstallIntent(downloadId: Long): Intent {
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId)

        return if (uri != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } else {
            // Fallback: use content://downloads/public_downloads URI
            val contentUri = ContentUris.withAppendedId(
                Uri.parse("content://downloads/public_downloads"),
                downloadId
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        }
    }

    private fun showInstallNotification(downloadId: Long) {
        val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for app updates"
            }
            nm.createNotificationChannel(channel)
        }

        val installIntent = getInstallIntent(downloadId)
        val pendingIntent = PendingIntent.getActivity(
            activity, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(activity, UPDATE_CHANNEL_ID)
            .setContentTitle("BwanaBet Tracker")
            .setContentText("Update downloaded - tap to install")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(UPDATE_NOTIFICATION_ID, notification)
    }

    private fun installApk(downloadId: Long) {
        try {
            val intent = getInstallIntent(downloadId)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer", e)
            Toast.makeText(activity, "Tap the notification to install the update", Toast.LENGTH_LONG).show()
        }
    }
}
