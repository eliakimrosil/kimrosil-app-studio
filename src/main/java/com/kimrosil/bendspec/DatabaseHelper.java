package com.kimrosil.bendspec;

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
 * 100% Offline SQLite database for storing sheet metal bend specifications and developed blanks.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "bendspec.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_BENDS = "saved_bends";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "profile_name";
    public static final String COL_MATERIAL = "material";
    public static final String COL_THICKNESS = "thickness";
    public static final String COL_RADIUS = "inside_radius";
    public static final String COL_ANGLE = "bend_angle";
    public static final String COL_KFACTOR = "k_factor";
    public static final String COL_FLANGE_A = "flange_a";
    public static final String COL_FLANGE_B = "flange_b";
    public static final String COL_BLANK_LENGTH = "developed_length";
    public static final String COL_BA = "bend_allowance";
    public static final String COL_BD = "bend_deduction";
    public static final String COL_CREATED_AT = "created_at";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_BENDS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_MATERIAL + " TEXT NOT NULL, "
                + COL_THICKNESS + " REAL, "
                + COL_RADIUS + " REAL, "
                + COL_ANGLE + " REAL, "
                + COL_KFACTOR + " REAL, "
                + COL_FLANGE_A + " REAL, "
                + COL_FLANGE_B + " REAL, "
                + COL_BLANK_LENGTH + " REAL, "
                + COL_BA + " REAL, "
                + COL_BD + " REAL, "
                + COL_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BENDS);
        onCreate(db);
    }

    public long insertBend(String name, String mat, double thick, double rad, double angle,
                           double k, double fA, double fB, double blank, double ba, double bd) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, name);
        cv.put(COL_MATERIAL, mat);
        cv.put(COL_THICKNESS, thick);
        cv.put(COL_RADIUS, rad);
        cv.put(COL_ANGLE, angle);
        cv.put(COL_KFACTOR, k);
        cv.put(COL_FLANGE_A, fA);
        cv.put(COL_FLANGE_B, fB);
        cv.put(COL_BLANK_LENGTH, blank);
        cv.put(COL_BA, ba);
        cv.put(COL_BD, bd);
        return db.insert(TABLE_BENDS, null, cv);
    }

    public List<Map<String, Object>> getAllBends() {
        List<Map<String, Object>> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_BENDS + " ORDER BY " + COL_ID + " DESC", null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                Map<String, Object> map = new HashMap<>();
                map.put(COL_ID, cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
                map.put(COL_NAME, cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)));
                map.put(COL_MATERIAL, cursor.getString(cursor.getColumnIndexOrThrow(COL_MATERIAL)));
                map.put(COL_THICKNESS, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_THICKNESS)));
                map.put(COL_RADIUS, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_RADIUS)));
                map.put(COL_ANGLE, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_ANGLE)));
                map.put(COL_KFACTOR, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_KFACTOR)));
                map.put(COL_FLANGE_A, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_FLANGE_A)));
                map.put(COL_FLANGE_B, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_FLANGE_B)));
                map.put(COL_BLANK_LENGTH, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_BLANK_LENGTH)));
                map.put(COL_BA, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_BA)));
                map.put(COL_BD, cursor.getDouble(cursor.getColumnIndexOrThrow(COL_BD)));
                map.put(COL_CREATED_AT, cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT)));
                list.add(map);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return list;
    }

    public boolean deleteBend(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_BENDS, COL_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_BENDS, null, null);
    }
}
