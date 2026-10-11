package com.kimrosil.grainrun;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "grainrun_workshop.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SESSIONS = "sessions";
    public static final String COL_ID = "id";
    public static final String COL_SPECIES = "species";
    public static final String COL_JANKA = "janka";
    public static final String COL_CUTTING_ANGLE = "cutting_angle";
    public static final String COL_TOOL_TYPE = "tool_type";
    public static final String COL_TEAROUT_RISK = "tearout_risk";
    public static final String COL_ADVICE = "advice";
    public static final String COL_TIMESTAMP = "created_at";

    public static class PlaningSession {
        public long id;
        public String species;
        public int janka;
        public int cuttingAngle;
        public String toolType;
        public int tearoutRisk;
        public String advice;
        public String timestamp;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createSessions = "CREATE TABLE " + TABLE_SESSIONS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SPECIES + " TEXT NOT NULL, " +
                COL_JANKA + " INTEGER NOT NULL, " +
                COL_CUTTING_ANGLE + " INTEGER NOT NULL, " +
                COL_TOOL_TYPE + " TEXT NOT NULL, " +
                COL_TEAROUT_RISK + " INTEGER NOT NULL, " +
                COL_ADVICE + " TEXT, " +
                COL_TIMESTAMP + " TEXT NOT NULL);";
        db.execSQL(createSessions);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        onCreate(db);
    }

    public long insertSession(String species, int janka, int cuttingAngle, String toolType, int risk, String advice) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SPECIES, species);
        cv.put(COL_JANKA, janka);
        cv.put(COL_CUTTING_ANGLE, cuttingAngle);
        cv.put(COL_TOOL_TYPE, toolType);
        cv.put(COL_TEAROUT_RISK, risk);
        cv.put(COL_ADVICE, advice);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.US);
        cv.put(COL_TIMESTAMP, sdf.format(new Date()));

        try {
            return db.insert(TABLE_SESSIONS, null, cv);
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean updateSession(long id, String species, int janka, int cuttingAngle, String toolType, int risk, String advice) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SPECIES, species);
        cv.put(COL_JANKA, janka);
        cv.put(COL_CUTTING_ANGLE, cuttingAngle);
        cv.put(COL_TOOL_TYPE, toolType);
        cv.put(COL_TEAROUT_RISK, risk);
        cv.put(COL_ADVICE, advice);

        try {
            int rows = db.update(TABLE_SESSIONS, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
            return rows > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deleteSession(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            int rows = db.delete(TABLE_SESSIONS, COL_ID + "=?", new String[]{String.valueOf(id)});
            return rows > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<PlaningSession> getAllSessions(int limit) {
        List<PlaningSession> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            String query = "SELECT * FROM " + TABLE_SESSIONS + " ORDER BY " + COL_ID + " DESC LIMIT " + limit;
            cursor = db.rawQuery(query, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    PlaningSession s = new PlaningSession();
                    s.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                    s.species = cursor.getString(cursor.getColumnIndexOrThrow(COL_SPECIES));
                    s.janka = cursor.getInt(cursor.getColumnIndexOrThrow(COL_JANKA));
                    s.cuttingAngle = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CUTTING_ANGLE));
                    s.toolType = cursor.getString(cursor.getColumnIndexOrThrow(COL_TOOL_TYPE));
                    s.tearoutRisk = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TEAROUT_RISK));
                    s.advice = cursor.getString(cursor.getColumnIndexOrThrow(COL_ADVICE));
                    s.timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                    list.add(s);
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }
}
