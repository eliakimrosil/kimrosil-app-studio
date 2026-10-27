package com.kimrosil.conduitfill;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class ConduitDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "conduitfill.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_RUNS = "runs";
    public static final String COL_ID = "_id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_CONDUIT = "conduit_desc";
    public static final String COL_WIRES = "wires_desc";
    public static final String COL_FILL = "fill_percent";
    public static final String COL_MAX = "max_percent";
    public static final String COL_JAM = "jam_ratio";
    public static final String COL_STATUS = "status";

    public static class RunRecord {
        public long id;
        public long timestamp;
        public String conduitDesc;
        public String wiresDesc;
        public double fillPercent;
        public double maxPercent;
        public double jamRatio;
        public String status;
    }

    public ConduitDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_RUNS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_CONDUIT + " TEXT NOT NULL, "
                + COL_WIRES + " TEXT NOT NULL, "
                + COL_FILL + " REAL NOT NULL, "
                + COL_MAX + " REAL NOT NULL, "
                + COL_JAM + " REAL NOT NULL, "
                + COL_STATUS + " TEXT NOT NULL"
                + ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RUNS);
        onCreate(db);
    }

    public long insertRun(RunRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIMESTAMP, record.timestamp);
        cv.put(COL_CONDUIT, record.conduitDesc);
        cv.put(COL_WIRES, record.wiresDesc);
        cv.put(COL_FILL, record.fillPercent);
        cv.put(COL_MAX, record.maxPercent);
        cv.put(COL_JAM, record.jamRatio);
        cv.put(COL_STATUS, record.status);
        return db.insert(TABLE_RUNS, null, cv);
    }

    public List<RunRecord> getAllRuns() {
        List<RunRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_RUNS, null, null, null, null, null, COL_TIMESTAMP + " DESC", "30");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COL_ID);
                int timeIdx = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
                int conduitIdx = cursor.getColumnIndexOrThrow(COL_CONDUIT);
                int wiresIdx = cursor.getColumnIndexOrThrow(COL_WIRES);
                int fillIdx = cursor.getColumnIndexOrThrow(COL_FILL);
                int maxIdx = cursor.getColumnIndexOrThrow(COL_MAX);
                int jamIdx = cursor.getColumnIndexOrThrow(COL_JAM);
                int statusIdx = cursor.getColumnIndexOrThrow(COL_STATUS);

                while (cursor.moveToNext()) {
                    RunRecord rec = new RunRecord();
                    rec.id = cursor.getLong(idIdx);
                    rec.timestamp = cursor.getLong(timeIdx);
                    rec.conduitDesc = cursor.getString(conduitIdx);
                    rec.wiresDesc = cursor.getString(wiresIdx);
                    rec.fillPercent = cursor.getDouble(fillIdx);
                    rec.maxPercent = cursor.getDouble(maxIdx);
                    rec.jamRatio = cursor.getDouble(jamIdx);
                    rec.status = cursor.getString(statusIdx);
                    list.add(rec);
                }
            } finally {
                cursor.close();
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
