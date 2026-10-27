package com.kimrosil.weldbead;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class WeldDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "weld_bead.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_PASSES = "weld_passes";
    public static final String COL_ID = "id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_PROCESS = "process";
    public static final String COL_AMPS = "amps";
    public static final String COL_VOLTS = "volts";
    public static final String COL_SPEED = "speed";
    public static final String COL_HEAT_INPUT = "heat_input";
    public static final String COL_UNIT = "unit";
    public static final String COL_DEPOSITION = "deposition";
    public static final String COL_STATUS = "status";

    public static class WeldRecord {
        public long id;
        public String timestamp;
        public String process;
        public double amps;
        public double volts;
        public double speed;
        public double heatInput;
        public String unit;
        public double deposition;
        public String status;
    }

    public WeldDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_PASSES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TIMESTAMP + " TEXT NOT NULL, "
                + COL_PROCESS + " TEXT NOT NULL, "
                + COL_AMPS + " REAL NOT NULL, "
                + COL_VOLTS + " REAL NOT NULL, "
                + COL_SPEED + " REAL NOT NULL, "
                + COL_HEAT_INPUT + " REAL NOT NULL, "
                + COL_UNIT + " TEXT NOT NULL, "
                + COL_DEPOSITION + " REAL NOT NULL, "
                + COL_STATUS + " TEXT NOT NULL)";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PASSES);
        onCreate(db);
    }

    public long insertPass(String timestamp, String process, double amps, double volts,
                           double speed, double heatInput, String unit, double deposition, String status) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIMESTAMP, timestamp);
        cv.put(COL_PROCESS, process);
        cv.put(COL_AMPS, amps);
        cv.put(COL_VOLTS, volts);
        cv.put(COL_SPEED, speed);
        cv.put(COL_HEAT_INPUT, heatInput);
        cv.put(COL_UNIT, unit);
        cv.put(COL_DEPOSITION, deposition);
        cv.put(COL_STATUS, status);
        return db.insert(TABLE_PASSES, null, cv);
    }

    public List<WeldRecord> getRecentPasses(int limit) {
        List<WeldRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_PASSES, null, null, null, null, null, COL_ID + " DESC", String.valueOf(limit));
        if (c != null) {
            try {
                int idIdx = c.getColumnIndexOrThrow(COL_ID);
                int timeIdx = c.getColumnIndexOrThrow(COL_TIMESTAMP);
                int procIdx = c.getColumnIndexOrThrow(COL_PROCESS);
                int ampsIdx = c.getColumnIndexOrThrow(COL_AMPS);
                int voltsIdx = c.getColumnIndexOrThrow(COL_VOLTS);
                int speedIdx = c.getColumnIndexOrThrow(COL_SPEED);
                int hiIdx = c.getColumnIndexOrThrow(COL_HEAT_INPUT);
                int unitIdx = c.getColumnIndexOrThrow(COL_UNIT);
                int depIdx = c.getColumnIndexOrThrow(COL_DEPOSITION);
                int statIdx = c.getColumnIndexOrThrow(COL_STATUS);

                while (c.moveToNext()) {
                    WeldRecord r = new WeldRecord();
                    r.id = c.getLong(idIdx);
                    r.timestamp = c.getString(timeIdx);
                    r.process = c.getString(procIdx);
                    r.amps = c.getDouble(ampsIdx);
                    r.volts = c.getDouble(voltsIdx);
                    r.speed = c.getDouble(speedIdx);
                    r.heatInput = c.getDouble(hiIdx);
                    r.unit = c.getString(unitIdx);
                    r.deposition = c.getDouble(depIdx);
                    r.status = c.getString(statIdx);
                    list.add(r);
                }
            } finally {
                c.close();
            }
        }
        return list;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_PASSES, null, null);
    }
}
