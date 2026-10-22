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

data class Exercise(
    val id: Long = 0,
    val dayType: String = "PUSH",
    var name: String,
    var targetMuscles: String,
    var totalSets: Int,
    var defaultWeight: Double,
    var defaultReps: Int,
    var sortOrder: Int = 0
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ppl_workout.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_SESSIONS = "sessions"
        const val TABLE_SETS = "logged_sets"
        const val TABLE_EXERCISES = "exercises"
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

        createExercisesTable(db)
        seedDefaultExercises(db)
    }

    private fun createExercisesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_EXERCISES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                day_type TEXT NOT NULL,
                name TEXT NOT NULL,
                target_muscles TEXT NOT NULL,
                total_sets INTEGER NOT NULL,
                default_weight REAL NOT NULL,
                default_reps INTEGER NOT NULL,
                sort_order INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createExercisesTable(db)
            seedDefaultExercises(db)
        }
    }

    fun seedDefaultExercises(db: SQLiteDatabase) {
        val defaults = listOf(
            // PUSH
            Exercise(0, "PUSH", "Barbell Bench Press", "CHEST • SHOULDERS • TRICEPS", 4, 80.0, 8, 1),
            Exercise(0, "PUSH", "Overhead Barbell Press", "FRONT DELTS • TRICEPS", 3, 50.0, 8, 2),
            Exercise(0, "PUSH", "Incline Dumbbell Press", "UPPER CHEST • SHOULDERS", 3, 28.0, 10, 3),
            Exercise(0, "PUSH", "Cable Lateral Raise", "SIDE DELTS", 4, 12.0, 15, 4),
            Exercise(0, "PUSH", "Tricep Rope Pushdown", "TRICEPS LATERAL HEAD", 3, 30.0, 12, 5),

            // PULL
            Exercise(0, "PULL", "Barbell Bent-Over Row", "LATS • UPPER BACK", 4, 75.0, 8, 1),
            Exercise(0, "PULL", "Lat Pulldown", "LATS • TERES MAJOR", 3, 65.0, 10, 2),
            Exercise(0, "PULL", "Seated Cable Row", "RHOMBOIDS • MID TRAPS", 3, 60.0, 10, 3),
            Exercise(0, "PULL", "Face Pulls", "REAR DELTS • ROTATOR CUFF", 3, 25.0, 15, 4),
            Exercise(0, "PULL", "Incline Dumbbell Curl", "BICEPS BRACHII", 4, 14.0, 10, 5),

            // LEGS
            Exercise(0, "LEGS", "Barbell Back Squat", "QUADS • GLUTES • CORE", 4, 100.0, 6, 1),
            Exercise(0, "LEGS", "Romanian Deadlift", "HAMSTRINGS • POSTERIOR CHAIN", 3, 90.0, 8, 2),
            Exercise(0, "LEGS", "Leg Press", "QUADS • ADDUCTORS", 3, 180.0, 10, 3),
            Exercise(0, "LEGS", "Standing Calf Raise", "GASTROCNEMIUS", 4, 60.0, 15, 4),
            Exercise(0, "LEGS", "Hanging Knee Raise", "ABDOMINALS • HIP FLEXORS", 3, 0.0, 15, 5)
        )

        for (ex in defaults) {
            val cv = ContentValues().apply {
                put("day_type", ex.dayType)
                put("name", ex.name)
                put("target_muscles", ex.targetMuscles)
                put("total_sets", ex.totalSets)
                put("default_weight", ex.defaultWeight)
                put("default_reps", ex.defaultReps)
                put("sort_order", ex.sortOrder)
            }
            db.insert(TABLE_EXERCISES, null, cv)
        }
    }

    fun getExercises(dayType: String): MutableList<Exercise> {
        val list = mutableListOf<Exercise>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, day_type, name, target_muscles, total_sets, default_weight, default_reps, sort_order FROM $TABLE_EXERCISES WHERE day_type = ? ORDER BY sort_order ASC, id ASC",
            arrayOf(dayType)
        )
        try {
            while (cursor.moveToNext()) {
                list.add(
                    Exercise(
                        id = cursor.getLong(0),
                        dayType = cursor.getString(1),
                        name = cursor.getString(2),
                        targetMuscles = cursor.getString(3),
                        totalSets = cursor.getInt(4),
                        defaultWeight = cursor.getDouble(5),
                        defaultReps = cursor.getInt(6),
                        sortOrder = cursor.getInt(7)
                    )
                )
            }
        } finally {
            cursor.close()
        }

        if (list.isEmpty()) {
            val wdb = writableDatabase
            seedDefaultExercises(wdb)
            return getExercises(dayType)
        }
        return list
    }

    fun addExercise(dayType: String, name: String, targetMuscles: String, totalSets: Int, defaultWeight: Double, defaultReps: Int): Exercise {
        val db = writableDatabase
        var maxOrder = 0
        val cursor = db.rawQuery(
            "SELECT MAX(sort_order) FROM $TABLE_EXERCISES WHERE day_type = ?",
            arrayOf(dayType)
        )
        if (cursor.moveToFirst()) {
            maxOrder = cursor.getInt(0)
        }
        cursor.close()

        val nextOrder = maxOrder + 1
        val cv = ContentValues().apply {
            put("day_type", dayType)
            put("name", name)
            put("target_muscles", targetMuscles)
            put("total_sets", totalSets)
            put("default_weight", defaultWeight)
            put("default_reps", defaultReps)
            put("sort_order", nextOrder)
        }
        val id = db.insert(TABLE_EXERCISES, null, cv)
        return Exercise(id, dayType, name, targetMuscles, totalSets, defaultWeight, defaultReps, nextOrder)
    }

    fun updateExercise(id: Long, name: String, targetMuscles: String, totalSets: Int, defaultWeight: Double, defaultReps: Int): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", name)
            put("target_muscles", targetMuscles)
            put("total_sets", totalSets)
            put("default_weight", defaultWeight)
            put("default_reps", defaultReps)
        }
        return db.update(TABLE_EXERCISES, cv, "id = ?", arrayOf(id.toString())) > 0
    }

    fun updateExerciseTotalSets(id: Long, newTotalSets: Int): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("total_sets", newTotalSets)
        }
        return db.update(TABLE_EXERCISES, cv, "id = ?", arrayOf(id.toString())) > 0
    }

    fun deleteExercise(id: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_EXERCISES, "id = ?", arrayOf(id.toString())) > 0
    }

    fun moveExercise(dayType: String, exerciseId: Long, moveUp: Boolean): Boolean {
        val exercises = getExercises(dayType)
        val currentIndex = exercises.indexOfFirst { it.id == exerciseId }
        if (currentIndex == -1) return false

        val targetIndex = if (moveUp) currentIndex - 1 else currentIndex + 1
        if (targetIndex !in exercises.indices) return false

        val currentEx = exercises[currentIndex]
        val targetEx = exercises[targetIndex]

        val db = writableDatabase
        db.beginTransaction()
        try {
            val cvCurrent = ContentValues().apply { put("sort_order", targetEx.sortOrder) }
            val cvTarget = ContentValues().apply { put("sort_order", currentEx.sortOrder) }

            db.update(TABLE_EXERCISES, cvCurrent, "id = ?", arrayOf(currentEx.id.toString()))
            db.update(TABLE_EXERCISES, cvTarget, "id = ?", arrayOf(targetEx.id.toString()))
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun resetRoutineToDefaults(dayType: String) {
        val db = writableDatabase
        db.delete(TABLE_EXERCISES, "day_type = ?", arrayOf(dayType))
        seedDefaultExercises(db)
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

    fun getAllSessions(): List<SessionSummary> {
        val list = mutableListOf<SessionSummary>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, day_type, timestamp, total_sets, total_volume, is_completed FROM $TABLE_SESSIONS WHERE total_sets > 0 ORDER BY id DESC LIMIT 50",
            null
        )
        try {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val dayType = cursor.getString(1)
                val date = cursor.getString(2)
                val sets = cursor.getInt(3)
                val vol = cursor.getDouble(4)
                val completed = cursor.getInt(5) == 1
                list.add(SessionSummary(id, dayType, date, sets, vol, completed))
            }
        } finally {
            cursor.close()
        }
        return list
    }

    fun clearAllHistory() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_SETS")
        db.execSQL("DELETE FROM $TABLE_SESSIONS")
    }
}
