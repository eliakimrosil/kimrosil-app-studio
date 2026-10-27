package com.kimrosil.rebarlap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class RebarDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "rebarlap.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SPLICES = "rebar_splices";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_BAR = "bar_name";
    public static final String COLUMN_FC = "fc_psi";
    public static final String COLUMN_FY = "fy_psi";
    public static final String COLUMN_IS_TOP = "is_top_bar";
    public static final String COLUMN_IS_EPOXY = "is_epoxy";
    public static final String COLUMN_LAP_B = "class_b_lap";
    public static final String COLUMN_LD = "ld_tension";
    public static final String COLUMN_TIMESTAMP = "created_at";

    public static class RebarRecord {
        public long id;
        public String barName;
        public double fcPsi;
        public double fyPsi;
        public boolean isTop;
        public boolean isEpoxy;
        public double classBLap;
        public double ldTension;
        public long createdAt;
    }

    public RebarDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_SPLICES + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_BAR + " TEXT, " +
                COLUMN_FC + " REAL, " +
                COLUMN_FY + " REAL, " +
                COLUMN_IS_TOP + " INTEGER, " +
                COLUMN_IS_EPOXY + " INTEGER, " +
                COLUMN_LAP_B + " REAL, " +
                COLUMN_LD + " REAL, " +
                COLUMN_TIMESTAMP + " INTEGER" +
                ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SPLICES);
        onCreate(db);
    }

    public long insertRecord(String barName, double fc, double fy,
                             boolean isTop, boolean isEpoxy, double lapB, double ld) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_BAR, barName);
        cv.put(COLUMN_FC, fc);
        cv.put(COLUMN_FY, fy);
        cv.put(COLUMN_IS_TOP, isTop ? 1 : 0);
        cv.put(COLUMN_IS_EPOXY, isEpoxy ? 1 : 0);
        cv.put(COLUMN_LAP_B, lapB);
        cv.put(COLUMN_LD, ld);
        cv.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        return db.insert(TABLE_SPLICES, null, cv);
    }

    public List<RebarRecord> getAllRecords() {
        List<RebarRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_SPLICES, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID);
                int barIdx = cursor.getColumnIndexOrThrow(COLUMN_BAR);
                int fcIdx = cursor.getColumnIndexOrThrow(COLUMN_FC);
                int fyIdx = cursor.getColumnIndexOrThrow(COLUMN_FY);
                int topIdx = cursor.getColumnIndexOrThrow(COLUMN_IS_TOP);
                int epIdx = cursor.getColumnIndexOrThrow(COLUMN_IS_EPOXY);
                int lapIdx = cursor.getColumnIndexOrThrow(COLUMN_LAP_B);
                int ldIdx = cursor.getColumnIndexOrThrow(COLUMN_LD);
                int timeIdx = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP);

                while (cursor.moveToNext()) {
                    RebarRecord r = new RebarRecord();
                    r.id = cursor.getLong(idIdx);
                    r.barName = cursor.getString(barIdx);
                    r.fcPsi = cursor.getDouble(fcIdx);
                    r.fyPsi = cursor.getDouble(fyIdx);
                    r.isTop = cursor.getInt(topIdx) == 1;
                    r.isEpoxy = cursor.getInt(epIdx) == 1;
                    r.classBLap = cursor.getDouble(lapIdx);
                    r.ldTension = cursor.getDouble(ldIdx);
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
        db.delete(TABLE_SPLICES, null, null);
    }
}
