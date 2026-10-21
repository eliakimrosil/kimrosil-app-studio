package com.kimrosil.pplworkout

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionSummary(
    val id: Long,
    val dayType: String,
    val date: String,
    val totalSets: Int,
    val totalVolume: Double,
    val isCompleted: Boolean
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ppl_workout.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_SESSIONS = "sessions"
        const val TABLE_SETS = "logged_sets"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SESSIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                day_type TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                total_sets INTEGER DEFAULT 0,
                total_volume REAL DEFAULT 0.0,
                is_completed INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SETS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                exercise_name TEXT NOT NULL,
                set_number INTEGER NOT NULL,
                weight REAL NOT NULL,
                unit TEXT NOT NULL,
                reps INTEGER NOT NULL,
                timestamp TEXT NOT NULL,
                FOREIGN KEY (session_id) REFERENCES $TABLE_SESSIONS(id)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SETS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    fun startSession(dayType: String): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("day_type", dayType)
            put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            put("total_sets", 0)
            put("total_volume", 0.0)
            put("is_completed", 0)
        }
        return db.insert(TABLE_SESSIONS, null, cv)
    }

    fun logSet(sessionId: Long, exerciseName: String, setNumber: Int, weight: Double, unit: String, reps: Int): Long {
        val db = writableDatabase
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val cv = ContentValues().apply {
            put("session_id", sessionId)
            put("exercise_name", exerciseName)
            put("set_number", setNumber)
            put("weight", weight)
            put("unit", unit)
            put("reps", reps)
            put("timestamp", now)
        }
        val setRowId = db.insert(TABLE_SETS, null, cv)

        // Update session totals defensively
        val cursor = db.rawQuery(
            "SELECT COUNT(*), SUM(weight * reps) FROM $TABLE_SETS WHERE session_id = ?",
            arrayOf(sessionId.toString())
        )
        if (cursor.moveToFirst()) {
            val totalSets = cursor.getInt(0)
            val totalVolume = cursor.getDouble(1)
            val updateCv = ContentValues().apply {
                put("total_sets", totalSets)
                put("total_volume", totalVolume)
            }
            db.update(TABLE_SESSIONS, updateCv, "id = ?", arrayOf(sessionId.toString()))
        }
        cursor.close()
        return setRowId
    }

    fun getLastPerformance(exerciseName: String): Pair<Double, Int>? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT weight, reps FROM $TABLE_SETS WHERE exercise_name = ? ORDER BY id DESC LIMIT 1",
            arrayOf(exerciseName)
        )
        return try {
            if (cursor.moveToFirst()) {
                val weight = cursor.getDouble(0)
                val reps = cursor.getInt(1)
                Pair(weight, reps)
            } else {
                null
            }
        } finally {
            cursor.close()
        }
    }

    fun getSessionCompletedSetsCount(sessionId: Long): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_SETS WHERE session_id = ?", arrayOf(sessionId.toString()))
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    fun getSessionVolume(sessionId: Long): Double {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT SUM(weight * reps) FROM $TABLE_SETS WHERE session_id = ?", arrayOf(sessionId.toString()))
        val vol = if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        cursor.close()
        return vol
    }

    fun finishSession(sessionId: Long) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("is_completed", 1)
        }
        db.update(TABLE_SESSIONS, cv, "id = ?", arrayOf(sessionId.toString()))
    }
}
