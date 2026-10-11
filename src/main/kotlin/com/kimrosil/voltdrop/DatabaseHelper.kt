package com.kimrosil.voltdrop

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CalculationRecord(
    val id: Long,
    val timestamp: String,
    val title: String,
    val voltage: Double,
    val current: Double,
    val distance: Double,
    val unit: String,
    val conductor: String,
    val gauge: String,
    val dropVolts: Double,
    val dropPercent: Double,
    val loadVolts: Double,
    val powerLoss: Double
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "voltdrop_history.db"
        const val DATABASE_VERSION = 1
        const val TABLE_HISTORY = "history"

        const val COL_ID = "id"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_TITLE = "title"
        const val COL_VOLTAGE = "voltage"
        const val COL_CURRENT = "current"
        const val COL_DISTANCE = "distance"
        const val COL_UNIT = "unit"
        const val COL_CONDUCTOR = "conductor"
        const val COL_GAUGE = "gauge"
        const val COL_DROP_VOLTS = "drop_volts"
        const val COL_DROP_PERCENT = "drop_percent"
        const val COL_LOAD_VOLTS = "load_volts"
        const val COL_POWER_LOSS = "power_loss"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TIMESTAMP TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_VOLTAGE REAL NOT NULL,
                $COL_CURRENT REAL NOT NULL,
                $COL_DISTANCE REAL NOT NULL,
                $COL_UNIT TEXT NOT NULL,
                $COL_CONDUCTOR TEXT NOT NULL,
                $COL_GAUGE TEXT NOT NULL,
                $COL_DROP_VOLTS REAL NOT NULL,
                $COL_DROP_PERCENT REAL NOT NULL,
                $COL_LOAD_VOLTS REAL NOT NULL,
                $COL_POWER_LOSS REAL NOT NULL
            )
        """.trimIndent()
        db.execSQL(createSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    fun insertRecord(
        title: String,
        voltage: Double,
        current: Double,
        distance: Double,
        unit: String,
        conductor: String,
        gauge: String,
        dropVolts: Double,
        dropPercent: Double,
        loadVolts: Double,
        powerLoss: Double
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TIMESTAMP, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
            put(COL_TITLE, title.ifEmpty { "Circuit Calculation" })
            put(COL_VOLTAGE, voltage)
            put(COL_CURRENT, current)
            put(COL_DISTANCE, distance)
            put(COL_UNIT, unit)
            put(COL_CONDUCTOR, conductor)
            put(COL_GAUGE, gauge)
            put(COL_DROP_VOLTS, dropVolts)
            put(COL_DROP_PERCENT, dropPercent)
            put(COL_LOAD_VOLTS, loadVolts)
            put(COL_POWER_LOSS, powerLoss)
        }
        return db.insert(TABLE_HISTORY, null, values)
    }

    fun getAllRecords(): List<CalculationRecord> {
        val list = mutableListOf<CalculationRecord>()
        val db = readableDatabase
        val cursor = db.query(TABLE_HISTORY, null, null, null, null, null, "$COL_ID DESC", "50")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CalculationRecord(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        timestamp = it.getString(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                        title = it.getString(it.getColumnIndexOrThrow(COL_TITLE)),
                        voltage = it.getDouble(it.getColumnIndexOrThrow(COL_VOLTAGE)),
                        current = it.getDouble(it.getColumnIndexOrThrow(COL_CURRENT)),
                        distance = it.getDouble(it.getColumnIndexOrThrow(COL_DISTANCE)),
                        unit = it.getString(it.getColumnIndexOrThrow(COL_UNIT)),
                        conductor = it.getString(it.getColumnIndexOrThrow(COL_CONDUCTOR)),
                        gauge = it.getString(it.getColumnIndexOrThrow(COL_GAUGE)),
                        dropVolts = it.getDouble(it.getColumnIndexOrThrow(COL_DROP_VOLTS)),
                        dropPercent = it.getDouble(it.getColumnIndexOrThrow(COL_DROP_PERCENT)),
                        loadVolts = it.getDouble(it.getColumnIndexOrThrow(COL_LOAD_VOLTS)),
                        powerLoss = it.getDouble(it.getColumnIndexOrThrow(COL_POWER_LOSS))
                    )
                )
            }
        }
        return list
    }

    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, null, null)
    }
}
