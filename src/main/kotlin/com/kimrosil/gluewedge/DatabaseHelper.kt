package com.kimrosil.gluewedge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PanelSession(
    val id: Long = 0,
    val title: String,
    val lengthInches: Double,
    val widthInches: Double,
    val thicknessInches: Double,
    val speciesName: String,
    val targetPsi: Int,
    val clampCount: Int,
    val clampSpacingInches: Double,
    val totalForceLbf: Int,
    val openTimeSec: Int,
    val timestamp: Long
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "gluewedge.db"
        const val DATABASE_VERSION = 1
        const val TABLE_SESSIONS = "sessions"
        const val COL_ID = "_id"
        const val COL_TITLE = "title"
        const val COL_LENGTH = "length"
        const val COL_WIDTH = "width"
        const val COL_THICKNESS = "thickness"
        const val COL_SPECIES = "species"
        const val COL_PSI = "target_psi"
        const val COL_CLAMP_COUNT = "clamp_count"
        const val COL_SPACING = "spacing"
        const val COL_FORCE = "force_lbf"
        const val COL_OPEN_TIME = "open_time"
        const val COL_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_SESSIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TITLE TEXT NOT NULL,
                $COL_LENGTH REAL NOT NULL,
                $COL_WIDTH REAL NOT NULL,
                $COL_THICKNESS REAL NOT NULL,
                $COL_SPECIES TEXT NOT NULL,
                $COL_PSI INTEGER NOT NULL,
                $COL_CLAMP_COUNT INTEGER NOT NULL,
                $COL_SPACING REAL NOT NULL,
                $COL_FORCE INTEGER NOT NULL,
                $COL_OPEN_TIME INTEGER NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    fun insertSession(session: PanelSession): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_TITLE, session.title)
            put(COL_LENGTH, session.lengthInches)
            put(COL_WIDTH, session.widthInches)
            put(COL_THICKNESS, session.thicknessInches)
            put(COL_SPECIES, session.speciesName)
            put(COL_PSI, session.targetPsi)
            put(COL_CLAMP_COUNT, session.clampCount)
            put(COL_SPACING, session.clampSpacingInches)
            put(COL_FORCE, session.totalForceLbf)
            put(COL_OPEN_TIME, session.openTimeSec)
            put(COL_TIMESTAMP, session.timestamp)
        }
        return db.insert(TABLE_SESSIONS, null, cv)
    }

    fun getRecentSessions(limit: Int = 10): List<PanelSession> {
        val list = mutableListOf<PanelSession>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC",
            limit.toString()
        )
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val titleIdx = c.getColumnIndexOrThrow(COL_TITLE)
            val lIdx = c.getColumnIndexOrThrow(COL_LENGTH)
            val wIdx = c.getColumnIndexOrThrow(COL_WIDTH)
            val tIdx = c.getColumnIndexOrThrow(COL_THICKNESS)
            val spIdx = c.getColumnIndexOrThrow(COL_SPECIES)
            val psiIdx = c.getColumnIndexOrThrow(COL_PSI)
            val ccIdx = c.getColumnIndexOrThrow(COL_CLAMP_COUNT)
            val spcIdx = c.getColumnIndexOrThrow(COL_SPACING)
            val fIdx = c.getColumnIndexOrThrow(COL_FORCE)
            val otIdx = c.getColumnIndexOrThrow(COL_OPEN_TIME)
            val tsIdx = c.getColumnIndexOrThrow(COL_TIMESTAMP)

            while (c.moveToNext()) {
                list.add(
                    PanelSession(
                        id = c.getLong(idIdx),
                        title = c.getString(titleIdx),
                        lengthInches = c.getDouble(lIdx),
                        widthInches = c.getDouble(wIdx),
                        thicknessInches = c.getDouble(tIdx),
                        speciesName = c.getString(spIdx),
                        targetPsi = c.getInt(psiIdx),
                        clampCount = c.getInt(ccIdx),
                        clampSpacingInches = c.getDouble(spcIdx),
                        totalForceLbf = c.getInt(fIdx),
                        openTimeSec = c.getInt(otIdx),
                        timestamp = c.getLong(tsIdx)
                    )
                )
            }
        }
        return list
    }

    fun deleteAllSessions(): Int {
        val db = writableDatabase
        return db.delete(TABLE_SESSIONS, null, null)
    }
}