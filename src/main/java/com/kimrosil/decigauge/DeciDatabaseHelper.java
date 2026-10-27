package com.kimrosil.decigauge;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DeciDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "decigauge.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SESSIONS = "sessions";
    public static final String COL_ID = "_id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_DURATION_SEC = "duration_sec";
    public static final String COL_WEIGHTING = "weighting";
    public static final String COL_STANDARD = "standard";
    public static final String COL_LEQ = "leq_db";
    public static final String COL_MAX = "max_db";
    public static final String COL_MIN = "min_db";
    public static final String COL_DOSE = "dose_percent";
    public static final String COL_STATUS = "status";

    public static class SessionRecord {
        public long id;
        public long timestamp;
        public int durationSec;
        public String weighting;
        public String standard;
        public double leqDb;
        public double maxDb;
        public double minDb;
        public double dosePercent;
        public String status;
    }

    public DeciDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_SESSIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_DURATION_SEC + " INTEGER NOT NULL, "
                + COL_WEIGHTING + " TEXT NOT NULL, "
                + COL_STANDARD + " TEXT NOT NULL, "
                + COL_LEQ + " REAL NOT NULL, "
                + COL_MAX + " REAL NOT NULL, "
                + COL_MIN + " REAL NOT NULL, "
                + COL_DOSE + " REAL NOT NULL, "
                + COL_STATUS + " TEXT NOT NULL"
                + ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        onCreate(db);
    }

    public long insertSession(SessionRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIMESTAMP, record.timestamp);
        cv.put(COL_DURATION_SEC, record.durationSec);
        cv.put(COL_WEIGHTING, record.weighting);
        cv.put(COL_STANDARD, record.standard);
        cv.put(COL_LEQ, record.leqDb);
        cv.put(COL_MAX, record.maxDb);
        cv.put(COL_MIN, record.minDb);
        cv.put(COL_DOSE, record.dosePercent);
        cv.put(COL_STATUS, record.status);
        return db.insert(TABLE_SESSIONS, null, cv);
    }

    public List<SessionRecord> getAllSessions() {
        List<SessionRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_SESSIONS, null, null, null, null, null, COL_TIMESTAMP + " DESC", "30");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COL_ID);
                int timeIdx = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
                int durIdx = cursor.getColumnIndexOrThrow(COL_DURATION_SEC);
                int weightIdx = cursor.getColumnIndexOrThrow(COL_WEIGHTING);
                int stdIdx = cursor.getColumnIndexOrThrow(COL_STANDARD);
                int leqIdx = cursor.getColumnIndexOrThrow(COL_LEQ);
                int maxIdx = cursor.getColumnIndexOrThrow(COL_MAX);
                int minIdx = cursor.getColumnIndexOrThrow(COL_MIN);
                int doseIdx = cursor.getColumnIndexOrThrow(COL_DOSE);
                int statusIdx = cursor.getColumnIndexOrThrow(COL_STATUS);

                while (cursor.moveToNext()) {
                    SessionRecord rec = new SessionRecord();
                    rec.id = cursor.getLong(idIdx);
                    rec.timestamp = cursor.getLong(timeIdx);
                    rec.durationSec = cursor.getInt(durIdx);
                    rec.weighting = cursor.getString(weightIdx);
                    rec.standard = cursor.getString(stdIdx);
                    rec.leqDb = cursor.getDouble(leqIdx);
                    rec.maxDb = cursor.getDouble(maxIdx);
                    rec.minDb = cursor.getDouble(minIdx);
                    rec.dosePercent = cursor.getDouble(doseIdx);
                    rec.status = cursor.getString(statusIdx);
                    list.add(rec);
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public void deleteSession(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SESSIONS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void clearAllSessions() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SESSIONS, null, null);
    }
}
