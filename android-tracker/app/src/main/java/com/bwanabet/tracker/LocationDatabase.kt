package com.bwanabet.tracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject

class LocationDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION
) {
    companion object {
        private const val TAG = "LocationDatabase"
        private const val DB_NAME = "tracker.db"
        private const val DB_VERSION = 1
        private const val TABLE = "pending_locations"
        private const val MAX_PENDING = 5000

        @Volatile
        private var instance: LocationDatabase? = null

        fun getInstance(context: Context): LocationDatabase {
            return instance ?: synchronized(this) {
                instance ?: LocationDatabase(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                accuracy REAL,
                speed REAL,
                bearing REAL,
                altitude REAL,
                battery_level INTEGER,
                network_type TEXT,
                recorded_at TEXT NOT NULL,
                created_at INTEGER DEFAULT (strftime('%s','now'))
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX idx_dedup ON $TABLE(device_id, recorded_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    @Synchronized
    fun insertLocation(point: JSONObject) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("device_id", point.getString("device_id"))
                put("latitude", point.getDouble("latitude"))
                put("longitude", point.getDouble("longitude"))
                put("accuracy", point.optDouble("accuracy", 0.0))
                put("speed", point.optDouble("speed", 0.0))
                put("bearing", point.optDouble("bearing", 0.0))
                put("altitude", point.optDouble("altitude", 0.0))
                put("battery_level", point.optInt("battery_level", -1))
                put("network_type", point.optString("network_type", "unknown"))
                put("recorded_at", point.getString("recorded_at"))
            }
            db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)

            val count = countInternal(db)
            if (count > MAX_PENDING) {
                val excess = count - MAX_PENDING
                db.execSQL(
                    "DELETE FROM $TABLE WHERE id IN (SELECT id FROM $TABLE ORDER BY id ASC LIMIT ?)",
                    arrayOf(excess.toString())
                )
                Log.w(TAG, "Evicted $excess oldest points (buffer was $count)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Insert failed: ${e.message}", e)
        }
    }

    @Synchronized
    fun getPendingBatch(limit: Int): List<Pair<Long, JSONObject>> {
        val results = mutableListOf<Pair<Long, JSONObject>>()
        var cursor: android.database.Cursor? = null
        try {
            val db = readableDatabase
            cursor = db.rawQuery(
                "SELECT * FROM $TABLE ORDER BY id ASC LIMIT ?",
                arrayOf(limit.toString())
            )
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val json = JSONObject().apply {
                    put("device_id", cursor.getString(cursor.getColumnIndexOrThrow("device_id")))
                    put("latitude", cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")))
                    put("longitude", cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")))
                    put("accuracy", cursor.getDouble(cursor.getColumnIndexOrThrow("accuracy")))
                    put("speed", cursor.getDouble(cursor.getColumnIndexOrThrow("speed")))
                    put("bearing", cursor.getDouble(cursor.getColumnIndexOrThrow("bearing")))
                    put("altitude", cursor.getDouble(cursor.getColumnIndexOrThrow("altitude")))
                    put("battery_level", cursor.getInt(cursor.getColumnIndexOrThrow("battery_level")))
                    put("network_type", cursor.getString(cursor.getColumnIndexOrThrow("network_type")))
                    put("recorded_at", cursor.getString(cursor.getColumnIndexOrThrow("recorded_at")))
                }
                results.add(Pair(id, json))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPendingBatch failed: ${e.message}", e)
        } finally {
            cursor?.close()
        }
        return results
    }

    @Synchronized
    fun deleteSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        try {
            val db = writableDatabase
            ids.chunked(500).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                db.execSQL(
                    "DELETE FROM $TABLE WHERE id IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteSynced failed: ${e.message}", e)
        }
    }

    @Synchronized
    fun pendingCount(): Int {
        var cursor: android.database.Cursor? = null
        return try {
            cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } catch (e: Exception) {
            Log.e(TAG, "pendingCount failed: ${e.message}", e)
            0
        } finally {
            cursor?.close()
        }
    }

    private fun countInternal(db: SQLiteDatabase): Int {
        var cursor: android.database.Cursor? = null
        return try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } finally {
            cursor?.close()
        }
    }
}
