package com.kimrosil.tapdrill;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tapdrill_local.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_PRESETS = "machining_presets";
    public static final String COL_ID = "id";
    public static final String COL_STANDARD = "thread_standard";
    public static final String COL_NOMINAL = "nominal_size";
    public static final String COL_PITCH = "pitch";
    public static final String COL_ENGAGEMENT = "engagement_percent";
    public static final String COL_WP_MATERIAL = "workpiece_material";
    public static final String COL_TOOL_MATERIAL = "tool_material";
    public static final String COL_SFM = "surface_speed_sfm";
    public static final String COL_RPM = "calculated_rpm";
    public static final String COL_IPM = "calculated_ipm";
    public static final String COL_PINNED = "is_pinned";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sqlPresets = "CREATE TABLE " + TABLE_PRESETS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_STANDARD + " TEXT, "
                + COL_NOMINAL + " TEXT, "
                + COL_PITCH + " REAL, "
                + COL_ENGAGEMENT + " INTEGER, "
                + COL_WP_MATERIAL + " TEXT, "
                + COL_TOOL_MATERIAL + " TEXT, "
                + COL_SFM + " REAL, "
                + COL_RPM + " INTEGER, "
                + COL_IPM + " REAL, "
                + COL_PINNED + " INTEGER DEFAULT 0);";
        db.execSQL(sqlPresets);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRESETS);
        onCreate(db);
    }

    public long insertPreset(String std, String nominal, double pitch, int engage,
                             String wpMat, String toolMat, double sfm, int rpm, double ipm, int pinned) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_STANDARD, std);
        cv.put(COL_NOMINAL, nominal);
        cv.put(COL_PITCH, pitch);
        cv.put(COL_ENGAGEMENT, engage);
        cv.put(COL_WP_MATERIAL, wpMat);
        cv.put(COL_TOOL_MATERIAL, toolMat);
        cv.put(COL_SFM, sfm);
        cv.put(COL_RPM, rpm);
        cv.put(COL_IPM, ipm);
        cv.put(COL_PINNED, pinned);
        return db.insert(TABLE_PRESETS, null, cv);
    }

    public Cursor getAllPresets() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_PRESETS, null, null, null, null, null, COL_ID + " DESC");
    }

    public void deletePreset(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_PRESETS, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }
}