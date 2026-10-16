package com.kimrosil.lashgage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "lashgage.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_LOGS = "setup_logs";
    public static final String COL_ID = "_id";
    public static final String COL_TYPE = "setup_type";
    public static final String COL_NAME = "axle_or_cylinder";
    public static final String COL_MEASURED = "measured_val";
    public static final String COL_TARGET = "target_val";
    public static final String COL_UNIT = "unit";
    public static final String COL_STATUS = "status";
    public static final String COL_NOTE = "note";
    public static final String COL_TIMESTAMP = "timestamp";

    public static class LogEntry {
        public long id;
        public String type;
        public String name;
        public double measured;
        public double target;
        public String unit;
        public String status;
        public String note;
        public long timestamp;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_LOGS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TYPE + " TEXT, "
                + COL_NAME + " TEXT, "
                + COL_MEASURED + " REAL, "
                + COL_TARGET + " REAL, "
                + COL_UNIT + " TEXT, "
                + COL_STATUS + " TEXT, "
                + COL_NOTE + " TEXT, "
                + COL_TIMESTAMP + " INTEGER"
                + ")";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        onCreate(db);
    }

    public long insertLog(String type, String name, double measured, double target, String unit, String status, String note) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TYPE, type);
        cv.put(COL_NAME, name);
        cv.put(COL_MEASURED, measured);
        cv.put(COL_TARGET, target);
        cv.put(COL_UNIT, unit);
        cv.put(COL_STATUS, status);
        cv.put(COL_NOTE, note);
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        return db.insert(TABLE_LOGS, null, cv);
    }

    public List<LogEntry> getAllLogs() {
        List<LogEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_LOGS, null, null, null, null, null, COL_TIMESTAMP + " DESC", "50");
        if (c != null) {
            while (c.moveToNext()) {
                LogEntry entry = new LogEntry();
                entry.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                entry.type = c.getString(c.getColumnIndexOrThrow(COL_TYPE));
                entry.name = c.getString(c.getColumnIndexOrThrow(COL_NAME));
                entry.measured = c.getDouble(c.getColumnIndexOrThrow(COL_MEASURED));
                entry.target = c.getDouble(c.getColumnIndexOrThrow(COL_TARGET));
                entry.unit = c.getString(c.getColumnIndexOrThrow(COL_UNIT));
                entry.status = c.getString(c.getColumnIndexOrThrow(COL_STATUS));
                entry.note = c.getString(c.getColumnIndexOrThrow(COL_NOTE));
                entry.timestamp = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                list.add(entry);
            }
            c.close();
        }
        return list;
    }

    public void clearAllLogs() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_LOGS, null, null);
    }
}
