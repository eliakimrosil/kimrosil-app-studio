package com.kimrosil.crowncut;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 100% Offline SQLite database for storing room cuts, crown molding project measurements,
 * and compound miter settings.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "crowncut.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_CUTS = "saved_cuts";
    public static final String COL_ID = "id";
    public static final String COL_LABEL = "label";
    public static final String COL_MODE = "cut_mode";
    public static final String COL_CORNER_ANGLE = "corner_angle";
    public static final String COL_SPRING_ANGLE = "spring_angle";
    public static final String COL_CUT_TYPE = "cut_type";
    public static final String COL_MITER_ANGLE = "miter_angle";
    public static final String COL_BEVEL_ANGLE = "bevel_angle";
    public static final String COL_FENCE_PLACEMENT = "fence_placement";
    public static final String COL_CREATED_AT = "created_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_CUTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_LABEL + " TEXT NOT NULL, "
                + COL_MODE + " TEXT NOT NULL, "
                + COL_CORNER_ANGLE + " REAL, "
                + COL_SPRING_ANGLE + " REAL, "
                + COL_CUT_TYPE + " TEXT, "
                + COL_MITER_ANGLE + " REAL, "
                + COL_BEVEL_ANGLE + " REAL, "
                + COL_FENCE_PLACEMENT + " TEXT, "
                + COL_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CUTS);
        onCreate(db);
    }

    public long insertCut(String label, String mode, double cornerAngle, double springAngle,
                          String cutType, double miterAngle, double bevelAngle, String fence) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LABEL, label);
        cv.put(COL_MODE, mode);
        cv.put(COL_CORNER_ANGLE, cornerAngle);
        cv.put(COL_SPRING_ANGLE, springAngle);
        cv.put(COL_CUT_TYPE, cutType);
        cv.put(COL_MITER_ANGLE, miterAngle);
        cv.put(COL_BEVEL_ANGLE, bevelAngle);
        cv.put(COL_FENCE_PLACEMENT, fence);
        return db.insert(TABLE_CUTS, null, cv);
    }

    public List<Map<String, Object>> getAllCuts() {
        List<Map<String, Object>> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_CUTS + " ORDER BY " + COL_ID + " DESC", null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                Map<String, Object> map = new HashMap<>();
                map.put(COL_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
                map.put(COL_LABEL, cursor.getString(cursor.getColumnIndexOrThrow(COL_LABEL)));
                map.put(COL_MODE, cursor.getString(cursor.getColumnIndexOrThrow(COL_MODE)));
                map.put(COL_CORNER_ANGLE, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_CORNER_ANGLE)));
                map.put(COL_SPRING_ANGLE, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_SPRING_ANGLE)));
                map.put(COL_CUT_TYPE, cursor.getString(cursor.getColumnIndexOrThrow(COL_CUT_TYPE)));
                map.put(COL_MITER_ANGLE, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_MITER_ANGLE)));
                map.put(COL_BEVEL_ANGLE, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_BEVEL_ANGLE)));
                map.put(COL_FENCE_PLACEMENT, cursor.getString(cursor.getColumnIndexOrThrow(COL_FENCE_PLACEMENT)));
                map.put(COL_CREATED_AT, cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT)));
                list.add(map);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return list;
    }

    public boolean deleteCut(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_CUTS, COL_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CUTS, null, null);
    }
}
