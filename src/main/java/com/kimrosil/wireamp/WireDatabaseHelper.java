package com.kimrosil.wireamp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class WireDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "wireamp.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_RUNS = "feeder_runs";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_GAUGE = "gauge_name";
    public static final String COLUMN_METAL = "metal";
    public static final String COLUMN_TEMP = "temp_rating";
    public static final String COLUMN_AMBIENT = "ambient_temp_f";
    public static final String COLUMN_COUNT = "conductor_count";
    public static final String COLUMN_DERATED_A = "derated_ampacity";
    public static final String COLUMN_BREAKER = "breaker_size";
    public static final String COLUMN_TIMESTAMP = "created_at";

    public static class FeederRecord {
        public long id;
        public String gaugeName;
        public String metal;
        public String tempRating;
        public int ambientF;
        public int conductorCount;
        public double deratedAmps;
        public int breakerSize;
        public long createdAt;
    }

    public WireDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_RUNS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_GAUGE + " TEXT, " +
                COLUMN_METAL + " TEXT, " +
                COLUMN_TEMP + " TEXT, " +
                COLUMN_AMBIENT + " INTEGER, " +
                COLUMN_COUNT + " INTEGER, " +
                COLUMN_DERATED_A + " REAL, " +
                COLUMN_BREAKER + " INTEGER, " +
                COLUMN_TIMESTAMP + " INTEGER" +
                ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RUNS);
        onCreate(db);
    }

    public long insertRun(String gauge, String metal, String temp, int ambientF,
                          int count, double deratedA, int breaker) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_GAUGE, gauge);
        cv.put(COLUMN_METAL, metal);
        cv.put(COLUMN_TEMP, temp);
        cv.put(COLUMN_AMBIENT, ambientF);
        cv.put(COLUMN_COUNT, count);
        cv.put(COLUMN_DERATED_A, deratedA);
        cv.put(COLUMN_BREAKER, breaker);
        cv.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        return db.insert(TABLE_RUNS, null, cv);
    }

    public List<FeederRecord> getAllRuns() {
        List<FeederRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_RUNS, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID);
                int gaugeIdx = cursor.getColumnIndexOrThrow(COLUMN_GAUGE);
                int metalIdx = cursor.getColumnIndexOrThrow(COLUMN_METAL);
                int tempIdx = cursor.getColumnIndexOrThrow(COLUMN_TEMP);
                int ambIdx = cursor.getColumnIndexOrThrow(COLUMN_AMBIENT);
                int countIdx = cursor.getColumnIndexOrThrow(COLUMN_COUNT);
                int deratedIdx = cursor.getColumnIndexOrThrow(COLUMN_DERATED_A);
                int brkIdx = cursor.getColumnIndexOrThrow(COLUMN_BREAKER);
                int timeIdx = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP);

                while (cursor.moveToNext()) {
                    FeederRecord r = new FeederRecord();
                    r.id = cursor.getLong(idIdx);
                    r.gaugeName = cursor.getString(gaugeIdx);
                    r.metal = cursor.getString(metalIdx);
                    r.tempRating = cursor.getString(tempIdx);
                    r.ambientF = cursor.getInt(ambIdx);
                    r.conductorCount = cursor.getInt(countIdx);
                    r.deratedAmps = cursor.getDouble(deratedIdx);
                    r.breakerSize = cursor.getInt(brkIdx);
                    r.createdAt = cursor.getLong(timeIdx);
                    list.add(r);
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RUNS, null, null);
    }
}
