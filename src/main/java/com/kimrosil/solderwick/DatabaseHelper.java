package com.kimrosil.solderwick;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "solderwick.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_PRESETS = "bench_presets";
    public static final String COLUMN_PRESET_ID = "id";
    public static final String COLUMN_PRESET_NAME = "name";
    public static final String COLUMN_ALLOY_TYPE = "alloy_type";
    public static final String COLUMN_BOARD_LAYERS = "board_layers";
    public static final String COLUMN_COPPER_OZ = "copper_oz";
    public static final String COLUMN_TARGET_TEMP_C = "target_temp_c";
    public static final String COLUMN_MAX_DWELL_MS = "max_dwell_ms";

    public static final String TABLE_LOGS = "joint_dwell_logs";
    public static final String COLUMN_LOG_ID = "id";
    public static final String COLUMN_LOG_TIMESTAMP = "timestamp";
    public static final String COLUMN_LOG_PRESET_ID = "preset_id";
    public static final String COLUMN_LOG_ACTUAL_DWELL = "actual_dwell_ms";
    public static final String COLUMN_LOG_OVERHEAT = "overheat_flag";

    public static class Preset {
        public long id;
        public String name;
        public String alloyType;
        public int boardLayers;
        public double copperOz;
        public int targetTempC;
        public int maxDwellMs;

        public Preset(long id, String name, String alloyType, int boardLayers, double copperOz, int targetTempC, int maxDwellMs) {
            this.id = id;
            this.name = name;
            this.alloyType = alloyType;
            this.boardLayers = boardLayers;
            this.copperOz = copperOz;
            this.targetTempC = targetTempC;
            this.maxDwellMs = maxDwellMs;
        }
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PRESETS + " (" +
                COLUMN_PRESET_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_PRESET_NAME + " TEXT, " +
                COLUMN_ALLOY_TYPE + " TEXT, " +
                COLUMN_BOARD_LAYERS + " INTEGER, " +
                COLUMN_COPPER_OZ + " REAL, " +
                COLUMN_TARGET_TEMP_C + " INTEGER, " +
                COLUMN_MAX_DWELL_MS + " INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_LOGS + " (" +
                COLUMN_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_LOG_TIMESTAMP + " INTEGER, " +
                COLUMN_LOG_PRESET_ID + " INTEGER, " +
                COLUMN_LOG_ACTUAL_DWELL + " INTEGER, " +
                COLUMN_LOG_OVERHEAT + " INTEGER)");

        // Insert default initial factory presets
        ContentValues cv1 = new ContentValues();
        cv1.put(COLUMN_PRESET_NAME, "Standard 63/37 (2-Layer)");
        cv1.put(COLUMN_ALLOY_TYPE, "63/37 SnPb");
        cv1.put(COLUMN_BOARD_LAYERS, 2);
        cv1.put(COLUMN_COPPER_OZ, 1.0);
        cv1.put(COLUMN_TARGET_TEMP_C, 320);
        cv1.put(COLUMN_MAX_DWELL_MS, 2500);
        db.insert(TABLE_PRESETS, null, cv1);

        ContentValues cv2 = new ContentValues();
        cv2.put(COLUMN_PRESET_NAME, "Heavy SAC305 (4-Layer 2oz Ground)");
        cv2.put(COLUMN_ALLOY_TYPE, "SAC305");
        cv2.put(COLUMN_BOARD_LAYERS, 4);
        cv2.put(COLUMN_COPPER_OZ, 2.0);
        cv2.put(COLUMN_TARGET_TEMP_C, 375);
        cv2.put(COLUMN_MAX_DWELL_MS, 4500);
        db.insert(TABLE_PRESETS, null, cv2);

        ContentValues cv3 = new ContentValues();
        cv3.put(COLUMN_PRESET_NAME, "Sensitive Low-Temp Sn42Bi58");
        cv3.put(COLUMN_ALLOY_TYPE, "Sn42Bi58");
        cv3.put(COLUMN_BOARD_LAYERS, 2);
        cv3.put(COLUMN_COPPER_OZ, 1.0);
        cv3.put(COLUMN_TARGET_TEMP_C, 220);
        cv3.put(COLUMN_MAX_DWELL_MS, 2000);
        db.insert(TABLE_PRESETS, null, cv3);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRESETS);
        onCreate(db);
    }

    public long insertPreset(String name, String alloy, int layers, double oz, int temp, int dwellMs) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_PRESET_NAME, name);
        cv.put(COLUMN_ALLOY_TYPE, alloy);
        cv.put(COLUMN_BOARD_LAYERS, layers);
        cv.put(COLUMN_COPPER_OZ, oz);
        cv.put(COLUMN_TARGET_TEMP_C, temp);
        cv.put(COLUMN_MAX_DWELL_MS, dwellMs);
        return db.insert(TABLE_PRESETS, null, cv);
    }

    public List<Preset> getAllPresets() {
        List<Preset> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PRESETS, null, null, null, null, null, COLUMN_PRESET_ID + " ASC");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COLUMN_PRESET_ID);
                int nameIdx = cursor.getColumnIndexOrThrow(COLUMN_PRESET_NAME);
                int alloyIdx = cursor.getColumnIndexOrThrow(COLUMN_ALLOY_TYPE);
                int layerIdx = cursor.getColumnIndexOrThrow(COLUMN_BOARD_LAYERS);
                int ozIdx = cursor.getColumnIndexOrThrow(COLUMN_COPPER_OZ);
                int tempIdx = cursor.getColumnIndexOrThrow(COLUMN_TARGET_TEMP_C);
                int dwellIdx = cursor.getColumnIndexOrThrow(COLUMN_MAX_DWELL_MS);

                while (cursor.moveToNext()) {
                    list.add(new Preset(
                            cursor.getLong(idIdx),
                            cursor.getString(nameIdx),
                            cursor.getString(alloyIdx),
                            cursor.getInt(layerIdx),
                            cursor.getDouble(ozIdx),
                            cursor.getInt(tempIdx),
                            cursor.getInt(dwellIdx)
                    ));
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public void insertLog(long presetId, int actualDwellMs, boolean overheated) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_LOG_TIMESTAMP, System.currentTimeMillis());
        cv.put(COLUMN_LOG_PRESET_ID, presetId);
        cv.put(COLUMN_LOG_ACTUAL_DWELL, actualDwellMs);
        cv.put(COLUMN_LOG_OVERHEAT, overheated ? 1 : 0);
        db.insert(TABLE_LOGS, null, cv);
    }

    public int getLogCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_LOGS, null);
        int count = 0;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }
        return count;
    }
}