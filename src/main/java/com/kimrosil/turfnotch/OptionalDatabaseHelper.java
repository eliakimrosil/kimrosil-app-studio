package com.kimrosil.turfnotch;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "turfnotch.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SESSIONS = "sessions";
    public static final String COL_ID = "_id";
    public static final String COL_SPREADER = "spreader";
    public static final String COL_RATE = "rate";
    public static final String COL_AREA = "area";
    public static final String COL_NOTCH = "notch";
    public static final String COL_PASSES = "passes";
    public static final String COL_TIMESTAMP = "timestamp";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SESSIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SPREADER + " TEXT, "
                + COL_RATE + " TEXT, "
                + COL_AREA + " TEXT, "
                + COL_NOTCH + " TEXT, "
                + COL_PASSES + " INTEGER, "
                + COL_TIMESTAMP + " INTEGER);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        onCreate(db);
    }

    public long insertSession(String spreader, String rate, String area, String notch, int passes) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SPREADER, spreader);
        cv.put(COL_RATE, rate);
        cv.put(COL_AREA, area);
        cv.put(COL_NOTCH, notch);
        cv.put(COL_PASSES, passes);
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        return db.insert(TABLE_SESSIONS, null, cv);
    }

    public Cursor getAllSessions() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_SESSIONS, null, null, null, null, null, COL_TIMESTAMP + " DESC");
    }

    public int deleteSession(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_SESSIONS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }
}