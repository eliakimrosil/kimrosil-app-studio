package com.kimrosil.gaussmeter

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local SQLite Storage for Magnetic Flux & Ferrous Wall Stud Inspection Scans.
 */
class GaussDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    data class ScanRecord(
        val id: Long,
        val zoneName: String,
        val totalFluxUt: Double,
        val netDeltaUt: Double,
        val baselineTareUt: Double,
        val alarmThresholdUt: Double,
        val couplingMode: Int
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SCANS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                zone_name TEXT NOT NULL,
                total_flux_ut REAL NOT NULL,
                net_delta_ut REAL NOT NULL,
                baseline_tare_ut REAL NOT NULL,
                alarm_threshold_ut REAL NOT NULL,
                coupling_mode INTEGER NOT NULL
            )
            """.trimIndent()
        )

        seedRecord(db, "North Drywall Stud 16in", 115.0, 67.0, 48.0, 85.0, GaussEngine.MODE_3D_VECTOR)
        seedRecord(db, "Breaker Subpanel Conduit", 188.0, 140.0, 48.0, 85.0, GaussEngine.MODE_3D_VECTOR)
        seedRecord(db, "Studio Desk Baseline", 48.5, 0.5, 48.0, 85.0, GaussEngine.MODE_3D_VECTOR)
    }

    private fun seedRecord(
        db: SQLiteDatabase,
        name: String,
        totalUt: Double,
        deltaUt: Double,
        tareUt: Double,
        alarmUt: Double,
        mode: Int
    ) {
        val cv = ContentValues().apply {
            put("zone_name", name)
            put("total_flux_ut", totalUt)
            put("net_delta_ut", deltaUt)
            put("baseline_tare_ut", tareUt)
            put("alarm_threshold_ut", alarmUt)
            put("coupling_mode", mode)
        }
        db.insert(TABLE_SCANS, null, cv)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SCANS")
        onCreate(db)
    }

    fun insertScan(
        zoneName: String?,
        totalFluxUt: Double,
        netDeltaUt: Double,
        baselineTareUt: Double,
        alarmThresholdUt: Double,
        couplingMode: Int
    ): Long {
        val trimmed = zoneName?.trim().orEmpty()
        val safeName = if (trimmed.isEmpty()) "Wall Scan" else trimmed.take(24)
        val cv = ContentValues().apply {
            put("zone_name", safeName)
            put("total_flux_ut", GaussEngine.clamp(totalFluxUt, 0.0, 9999.0))
            put("net_delta_ut", GaussEngine.clamp(netDeltaUt, 0.0, 9999.0))
            put("baseline_tare_ut", GaussEngine.clamp(baselineTareUt, 0.0, 2000.0))
            put("alarm_threshold_ut", GaussEngine.clamp(alarmThresholdUt, 5.0, 2000.0))
            put("coupling_mode", couplingMode)
        }
        return writableDatabase.insert(TABLE_SCANS, null, cv)
    }

    fun deleteScan(id: Long) {
        writableDatabase.delete(TABLE_SCANS, "id = ?", arrayOf(id.toString()))
    }

    fun getAllScans(): List<ScanRecord> {
        val list = ArrayList<ScanRecord>()
        val c = readableDatabase.query(TABLE_SCANS, null, null, null, null, null, "id DESC", "15")
        c.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    ScanRecord(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        zoneName = cursor.getString(cursor.getColumnIndexOrThrow("zone_name")),
                        totalFluxUt = cursor.getDouble(cursor.getColumnIndexOrThrow("total_flux_ut")),
                        netDeltaUt = cursor.getDouble(cursor.getColumnIndexOrThrow("net_delta_ut")),
                        baselineTareUt = cursor.getDouble(cursor.getColumnIndexOrThrow("baseline_tare_ut")),
                        alarmThresholdUt = cursor.getDouble(cursor.getColumnIndexOrThrow("alarm_threshold_ut")),
                        couplingMode = cursor.getInt(cursor.getColumnIndexOrThrow("coupling_mode"))
                    )
                )
            }
        }
        return list
    }

    companion object {
        private const val DB_NAME = "gauss_meter_logs.db"
        private const val DB_VERSION = 1
        private const val TABLE_SCANS = "magnetic_scans"
    }
}
