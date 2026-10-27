package com.kimrosil.plumbbob;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * PlumbDatabaseHelper
 * Offline SQLite repository managing persistent trench runs and pipe slope calculations.
 */
public class PlumbDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "plumbbob.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_RUNS = "trench_runs";
    public static final String COL_ID = "_id";
    public static final String COL_NAME = "name";
    public static final String COL_RUN_LENGTH = "run_length";
    public static final String COL_PITCH = "pitch_in_per_ft";
    public static final String COL_START_INVERT = "start_invert";
    public static final String COL_END_INVERT = "end_invert";
    public static final String COL_TOTAL_FALL = "total_fall";
    public static final String COL_GRADE_PERCENT = "grade_percent";
    public static final String COL_PIPE_TAG = "pipe_tag";
    public static final String COL_IS_METRIC = "is_metric";
    public static final String COL_CREATED_AT = "created_at";

    public static class TrenchRunRecord {
        public long id;
        public String name;
        public double runLength;
        public double pitchInPerFt;
        public double startInvert;
        public double endInvert;
        public double totalFall;
        public double gradePercent;
        public String pipeTag;
        public boolean isMetric;
        public long createdAt;
    }

    public PlumbDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_RUNS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT, " +
                COL_RUN_LENGTH + " REAL, " +
                COL_PITCH + " REAL, " +
                COL_START_INVERT + " REAL, " +
                COL_END_INVERT + " REAL, " +
                COL_TOTAL_FALL + " REAL, " +
                COL_GRADE_PERCENT + " REAL, " +
                COL_PIPE_TAG + " TEXT, " +
                COL_IS_METRIC + " INTEGER, " +
                COL_CREATED_AT + " INTEGER);";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RUNS);
        onCreate(db);
    }

    public long insertRun(String name, double runLength, double pitch, double startInv,
                          double endInv, double fall, double grade, String pipeTag, boolean isMetric) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, (name == null || name.trim().isEmpty()) ? "Trench Run" : name.trim());
        cv.put(COL_RUN_LENGTH, runLength);
        cv.put(COL_PITCH, pitch);
        cv.put(COL_START_INVERT, startInv);
        cv.put(COL_END_INVERT, endInv);
        cv.put(COL_TOTAL_FALL, fall);
        cv.put(COL_GRADE_PERCENT, grade);
        cv.put(COL_PIPE_TAG, pipeTag);
        cv.put(COL_IS_METRIC, isMetric ? 1 : 0);
        cv.put(COL_CREATED_AT, System.currentTimeMillis());
        return db.insert(TABLE_RUNS, null, cv);
    }

    public List<TrenchRunRecord> getRecentRuns(int limit) {
        List<TrenchRunRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_RUNS, null, null, null, null, null,
                COL_CREATED_AT + " DESC", String.valueOf(limit));

        if (c != null) {
            try {
                int idIdx = c.getColumnIndex(COL_ID);
                int nameIdx = c.getColumnIndex(COL_NAME);
                int runIdx = c.getColumnIndex(COL_RUN_LENGTH);
                int pitchIdx = c.getColumnIndex(COL_PITCH);
                int sInvIdx = c.getColumnIndex(COL_START_INVERT);
                int eInvIdx = c.getColumnIndex(COL_END_INVERT);
                int fallIdx = c.getColumnIndex(COL_TOTAL_FALL);
                int gradeIdx = c.getColumnIndex(COL_GRADE_PERCENT);
                int tagIdx = c.getColumnIndex(COL_PIPE_TAG);
                int metricIdx = c.getColumnIndex(COL_IS_METRIC);
                int dateIdx = c.getColumnIndex(COL_CREATED_AT);

                while (c.moveToNext()) {
                    TrenchRunRecord r = new TrenchRunRecord();
                    r.id = c.getLong(idIdx);
                    r.name = c.getString(nameIdx);
                    r.runLength = c.getDouble(runIdx);
                    r.pitchInPerFt = c.getDouble(pitchIdx);
                    r.startInvert = c.getDouble(sInvIdx);
                    r.endInvert = c.getDouble(eInvIdx);
                    r.totalFall = c.getDouble(fallIdx);
                    r.gradePercent = c.getDouble(gradeIdx);
                    r.pipeTag = c.getString(tagIdx);
                    r.isMetric = c.getInt(metricIdx) == 1;
                    r.createdAt = c.getLong(dateIdx);
                    list.add(r);
                }
            } finally {
                c.close();
            }
        }
        return list;
    }

    public void deleteRun(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RUNS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void clearAllRuns() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RUNS, null, null);
    }
}
