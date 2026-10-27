package com.kimrosil.tapergauge;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class TaperDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tapergauge.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SETUPS = "taper_setups";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_NAME = "setup_name";
    public static final String COLUMN_MAJOR_D = "major_dia";
    public static final String COLUMN_MINOR_D = "minor_dia";
    public static final String COLUMN_TAPER_L = "taper_len";
    public static final String COLUMN_TOTAL_L = "total_len";
    public static final String COLUMN_ANGLE = "compound_angle";
    public static final String COLUMN_OFFSET = "tailstock_offset";
    public static final String COLUMN_UNIT = "unit_system";
    public static final String COLUMN_TIMESTAMP = "created_at";

    public static class TaperRecord {
        public long id;
        public String setupName;
        public double majorD;
        public double minorD;
        public double taperL;
        public double totalL;
        public double compoundAngle;
        public double tailstockOffset;
        public String unit;
        public long createdAt;
    }

    public TaperDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_SETUPS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_MAJOR_D + " REAL, " +
                COLUMN_MINOR_D + " REAL, " +
                COLUMN_TAPER_L + " REAL, " +
                COLUMN_TOTAL_L + " REAL, " +
                COLUMN_ANGLE + " REAL, " +
                COLUMN_OFFSET + " REAL, " +
                COLUMN_UNIT + " TEXT, " +
                COLUMN_TIMESTAMP + " INTEGER" +
                ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETUPS);
        onCreate(db);
    }

    public long insertSetup(String name, double dMajor, double dMinor,
                            double lTaper, double lTotal, double angle,
                            double offset, String unit) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_NAME, name);
        cv.put(COLUMN_MAJOR_D, dMajor);
        cv.put(COLUMN_MINOR_D, dMinor);
        cv.put(COLUMN_TAPER_L, lTaper);
        cv.put(COLUMN_TOTAL_L, lTotal);
        cv.put(COLUMN_ANGLE, angle);
        cv.put(COLUMN_OFFSET, offset);
        cv.put(COLUMN_UNIT, unit);
        cv.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        return db.insert(TABLE_SETUPS, null, cv);
    }

    public List<TaperRecord> getAllSetups() {
        List<TaperRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_SETUPS, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID);
                int nameIdx = cursor.getColumnIndexOrThrow(COLUMN_NAME);
                int majIdx = cursor.getColumnIndexOrThrow(COLUMN_MAJOR_D);
                int minIdx = cursor.getColumnIndexOrThrow(COLUMN_MINOR_D);
                int tapIdx = cursor.getColumnIndexOrThrow(COLUMN_TAPER_L);
                int totIdx = cursor.getColumnIndexOrThrow(COLUMN_TOTAL_L);
                int angIdx = cursor.getColumnIndexOrThrow(COLUMN_ANGLE);
                int offIdx = cursor.getColumnIndexOrThrow(COLUMN_OFFSET);
                int unitIdx = cursor.getColumnIndexOrThrow(COLUMN_UNIT);
                int timeIdx = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP);

                while (cursor.moveToNext()) {
                    TaperRecord r = new TaperRecord();
                    r.id = cursor.getLong(idIdx);
                    r.setupName = cursor.getString(nameIdx);
                    r.majorD = cursor.getDouble(majIdx);
                    r.minorD = cursor.getDouble(minIdx);
                    r.taperL = cursor.getDouble(tapIdx);
                    r.totalL = cursor.getDouble(totIdx);
                    r.compoundAngle = cursor.getDouble(angIdx);
                    r.tailstockOffset = cursor.getDouble(offIdx);
                    r.unit = cursor.getString(unitIdx);
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
        db.delete(TABLE_SETUPS, null, null);
    }
}
