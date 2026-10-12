package com.kimrosil.pitchgrid

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TonePreset(
    val id: Long,
    val name: String,
    val frequency: Double,
    val waveform: WaveformType,
    val cents: Int,
    val isFactory: Boolean
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "pitchgrid.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_PRESETS = "presets"
        private const val COL_ID = "id"
        private const val COL_NAME = "name"
        private const val COL_FREQ = "frequency"
        private const val COL_WAVE = "waveform"
        private const val COL_CENTS = "cents"
        private const val COL_FACTORY = "is_factory"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PRESETS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_FREQ REAL NOT NULL,
                $COL_WAVE TEXT NOT NULL,
                $COL_CENTS INTEGER NOT NULL DEFAULT 0,
                $COL_FACTORY INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // Seed Factory Presets
        seedFactoryPresets(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PRESETS")
        onCreate(db)
    }

    private fun seedFactoryPresets(db: SQLiteDatabase) {
        val factoryData = listOf(
            Triple("Concert Pitch A4 (440 Hz)", 440.0, WaveformType.SINE),
            Triple("Pythagorean A4 (432 Hz)", 432.0, WaveformType.SINE),
            Triple("Studio Line Alignment (1 kHz)", 1000.0, WaveformType.SINE),
            Triple("Subwoofer Ground Hum (60 Hz)", 60.0, WaveformType.SINE),
            Triple("Sub Bass Resonance (40 Hz)", 40.0, WaveformType.SINE),
            Triple("Pink Noise Room Calibration", 1000.0, WaveformType.NOISE),
            Triple("Vintage Analog Saw Lead (A3)", 220.0, WaveformType.SAWTOOTH),
            Triple("Chiptune Pulse Lead (440 Hz)", 440.0, WaveformType.SQUARE),
            Triple("Guitar Low E2 String (82.4 Hz)", 82.41, WaveformType.SINE),
            Triple("Tweeter Air Dispersion (10 kHz)", 10000.0, WaveformType.SINE)
        )

        for ((name, freq, wave) in factoryData) {
            val cv = ContentValues().apply {
                put(COL_NAME, name)
                put(COL_FREQ, freq)
                put(COL_WAVE, wave.name)
                put(COL_CENTS, 0)
                put(COL_FACTORY, 1)
            }
            db.insert(TABLE_PRESETS, null, cv)
        }
    }

    fun getAllPresets(): List<TonePreset> {
        val list = mutableListOf<TonePreset>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PRESETS,
            null,
            null,
            null,
            null,
            null,
            "$COL_FACTORY DESC, $COL_ID ASC"
        )

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val nameIdx = c.getColumnIndexOrThrow(COL_NAME)
            val freqIdx = c.getColumnIndexOrThrow(COL_FREQ)
            val waveIdx = c.getColumnIndexOrThrow(COL_WAVE)
            val centsIdx = c.getColumnIndexOrThrow(COL_CENTS)
            val factoryIdx = c.getColumnIndexOrThrow(COL_FACTORY)

            while (c.moveToNext()) {
                val waveStr = c.getString(waveIdx)
                val waveType = try {
                    WaveformType.valueOf(waveStr)
                } catch (e: Exception) {
                    WaveformType.SINE
                }

                list.add(
                    TonePreset(
                        id = c.getLong(idIdx),
                        name = c.getString(nameIdx),
                        frequency = c.getDouble(freqIdx),
                        waveform = waveType,
                        cents = c.getInt(centsIdx),
                        isFactory = c.getInt(factoryIdx) == 1
                    )
                )
            }
        }
        return list
    }

    fun insertPreset(name: String, freq: Double, wave: WaveformType, cents: Int): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_NAME, name)
            put(COL_FREQ, freq)
            put(COL_WAVE, wave.name)
            put(COL_CENTS, cents)
            put(COL_FACTORY, 0)
        }
        return db.insert(TABLE_PRESETS, null, cv)
    }

    fun deletePreset(id: Long): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_PRESETS, "$COL_ID = ? AND $COL_FACTORY = 0", arrayOf(id.toString()))
        return rows > 0
    }
}
