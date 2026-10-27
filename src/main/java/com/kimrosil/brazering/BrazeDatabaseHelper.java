package com.kimrosil.brazering;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class BrazeDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "brazering.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_JOBS = "braze_jobs";
    public static final String COL_ID = "_id";
    public static final String COL_NAME = "name";
    public static final String COL_TUBE_OD = "tube_od";
    public static final String COL_DEPTH = "socket_depth";
    public static final String COL_CLEARANCE = "clearance";
    public static final String COL_ALLOY = "alloy_type";
    public static final String COL_COUNT = "joint_count";
    public static final String COL_TOTAL_GRAMS = "total_grams";
    public static final String COL_ROD_INCHES = "rod_inches";
    public static final String COL_PREFORM_GAUGE = "preform_gauge";
    public static final String COL_CREATED_AT = "created_at";

    public static class BrazeJobRecord {
        public long id;
        public String name;
        public double tubeOd;
        public double depth;
        public double clearance;
        public int alloy;
        public int count;
        public double totalGrams;
        public double rodInches;
        public String preformGauge;
        public long createdAt;
    }

    public BrazeDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_JOBS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT, " +
                COL_TUBE_OD + " REAL, " +
                COL_DEPTH + " REAL, " +
                COL_CLEARANCE + " REAL, " +
                COL_ALLOY + " INTEGER, " +
                COL_COUNT + " INTEGER, " +
                COL_TOTAL_GRAMS + " REAL, " +
                COL_ROD_INCHES + " REAL, " +
                COL_PREFORM_GAUGE + " TEXT, " +
                COL_CREATED_AT + " INTEGER);";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_JOBS);
        onCreate(db);
    }

    public long insertJob(String name, double tubeOd, double depth, double clearance,
                          int alloy, int count, double grams, double rodInches, String gauge) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, (name == null || name.trim().isEmpty()) ? "Braze Joint Run" : name.trim());
        cv.put(COL_TUBE_OD, tubeOd);
        cv.put(COL_DEPTH, depth);
        cv.put(COL_CLEARANCE, clearance);
        cv.put(COL_ALLOY, alloy);
        cv.put(COL_COUNT, count);
        cv.put(COL_TOTAL_GRAMS, grams);
        cv.put(COL_ROD_INCHES, rodInches);
        cv.put(COL_PREFORM_GAUGE, gauge);
        cv.put(COL_CREATED_AT, System.currentTimeMillis());
        return db.insert(TABLE_JOBS, null, cv);
    }

    public List<BrazeJobRecord> getRecentJobs(int limit) {
        List<BrazeJobRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_JOBS, null, null, null, null, null,
                COL_CREATED_AT + " DESC", String.valueOf(limit));

        if (c != null) {
            try {
                int idIdx = c.getColumnIndex(COL_ID);
                int nameIdx = c.getColumnIndex(COL_NAME);
                int odIdx = c.getColumnIndex(COL_TUBE_OD);
                int depthIdx = c.getColumnIndex(COL_DEPTH);
                int clIdx = c.getColumnIndex(COL_CLEARANCE);
                int alloyIdx = c.getColumnIndex(COL_ALLOY);
                int countIdx = c.getColumnIndex(COL_COUNT);
                int gramsIdx = c.getColumnIndex(COL_TOTAL_GRAMS);
                int rodIdx = c.getColumnIndex(COL_ROD_INCHES);
                int gaugeIdx = c.getColumnIndex(COL_PREFORM_GAUGE);
                int dateIdx = c.getColumnIndex(COL_CREATED_AT);

                while (c.moveToNext()) {
                    BrazeJobRecord r = new BrazeJobRecord();
                    r.id = c.getLong(idIdx);
                    r.name = c.getString(nameIdx);
                    r.tubeOd = c.getDouble(odIdx);
                    r.depth = c.getDouble(depthIdx);
                    r.clearance = c.getDouble(clIdx);
                    r.alloy = c.getInt(alloyIdx);
                    r.count = c.getInt(countIdx);
                    r.totalGrams = c.getDouble(gramsIdx);
                    r.rodInches = c.getDouble(rodIdx);
                    r.preformGauge = c.getString(gaugeIdx);
                    r.createdAt = c.getLong(dateIdx);
                    list.add(r);
                }
            } finally {
                c.close();
            }
        }
        return list;
    }

    public void deleteJob(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_JOBS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_JOBS, null, null);
    }
}
