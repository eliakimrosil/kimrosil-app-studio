package com.kimrosil.stairrise;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 100% Offline SQLite database for storing stair stringer jobs, riser/tread specs,
 * pitch angles, and building code verification logs.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "stairrise.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_JOBS = "saved_stairs";
    public static final String COL_ID = "id";
    public static final String COL_LABEL = "label";
    public static final String COL_TOTAL_RISE_MM = "total_rise_mm";
    public static final String COL_TREAD_RUN_MM = "tread_run_mm";
    public static final String COL_RISER_COUNT = "riser_count";
    public static final String COL_STEP_RISE_MM = "step_rise_mm";
    public static final String COL_TOTAL_RUN_MM = "total_run_mm";
    public static final String COL_STRINGER_LEN_MM = "stringer_len_mm";
    public static final String COL_PITCH_DEG = "pitch_deg";
    public static final String COL_MOUNT_TYPE = "mount_type";
    public static final String COL_CREATED_AT = "created_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_JOBS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_LABEL + " TEXT NOT NULL, "
                + COL_TOTAL_RISE_MM + " REAL, "
                + COL_TREAD_RUN_MM + " REAL, "
                + COL_RISER_COUNT + " INTEGER, "
                + COL_STEP_RISE_MM + " REAL, "
                + COL_TOTAL_RUN_MM + " REAL, "
                + COL_STRINGER_LEN_MM + " REAL, "
                + COL_PITCH_DEG + " REAL, "
                + COL_MOUNT_TYPE + " TEXT, "
                + COL_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_JOBS);
        onCreate(db);
    }

    public long insertStairJob(String label, double totalRiseMm, double treadRunMm,
                               int riserCount, double stepRiseMm, double totalRunMm,
                               double stringerLenMm, double pitchDeg, String mountType) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LABEL, label);
        cv.put(COL_TOTAL_RISE_MM, totalRiseMm);
        cv.put(COL_TREAD_RUN_MM, treadRunMm);
        cv.put(COL_RISER_COUNT, riserCount);
        cv.put(COL_STEP_RISE_MM, stepRiseMm);
        cv.put(COL_TOTAL_RUN_MM, totalRunMm);
        cv.put(COL_STRINGER_LEN_MM, stringerLenMm);
        cv.put(COL_PITCH_DEG, pitchDeg);
        cv.put(COL_MOUNT_TYPE, mountType);
        return db.insert(TABLE_JOBS, null, cv);
    }

    public List<Map<String, Object>> getAllJobs() {
        List<Map<String, Object>> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_JOBS + " ORDER BY " + COL_ID + " DESC", null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                Map<String, Object> map = new HashMap<>();
                map.put(COL_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
                map.put(COL_LABEL, cursor.getString(cursor.getColumnIndexOrThrow(COL_LABEL)));
                map.put(COL_TOTAL_RISE_MM, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_TOTAL_RISE_MM)));
                map.put(COL_TREAD_RUN_MM, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_TREAD_RUN_MM)));
                map.put(COL_RISER_COUNT, cursor.getInt(cursor.getColumnIndexOrThrow(COL_RISER_COUNT)));
                map.put(COL_STEP_RISE_MM, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_STEP_RISE_MM)));
                map.put(COL_TOTAL_RUN_MM, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_TOTAL_RUN_MM)));
                map.put(COL_STRINGER_LEN_MM, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_STRINGER_LEN_MM)));
                map.put(COL_PITCH_DEG, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PITCH_DEG)));
                map.put(COL_MOUNT_TYPE, cursor.getString(cursor.getColumnIndexOrThrow(COL_MOUNT_TYPE)));
                list.add(map);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return list;
    }

    public boolean deleteJob(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_JOBS, COL_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_JOBS, null, null);
    }
}
