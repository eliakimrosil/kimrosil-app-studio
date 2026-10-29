package com.kimrosil.torquespec;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "torquespec.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SAVED_SPECS = "saved_specs";
    public static final String COL_ID = "id";
    public static final String COL_BOLT_SIZE = "bolt_size";
    public static final String COL_STANDARD = "standard";
    public static final String COL_GRADE = "grade";
    public static final String COL_LUBRICANT = "lubricant";
    public static final String COL_TORQUE_NM = "torque_nm";
    public static final String COL_TORQUE_FTLB = "torque_ftlb";
    public static final String COL_CLAMP_FORCE_KN = "clamp_force_kn";
    public static final String COL_TIMESTAMP = "timestamp";

    public static class SavedSpec {
        public long id;
        public String boltSize;
        public String standard;
        public String grade;
        public String lubricant;
        public double torqueNm;
        public double torqueFtLb;
        public double clampForceKn;
        public String timestamp;

        public SavedSpec(long id, String boltSize, String standard, String grade,
                         String lubricant, double torqueNm, double torqueFtLb,
                         double clampForceKn, String timestamp) {
            this.id = id;
            this.boltSize = boltSize;
            this.standard = standard;
            this.grade = grade;
            this.lubricant = lubricant;
            this.torqueNm = torqueNm;
            this.torqueFtLb = torqueFtLb;
            this.clampForceKn = clampForceKn;
            this.timestamp = timestamp;
        }
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_SAVED_SPECS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_BOLT_SIZE + " TEXT NOT NULL, "
                + COL_STANDARD + " TEXT NOT NULL, "
                + COL_GRADE + " TEXT NOT NULL, "
                + COL_LUBRICANT + " TEXT NOT NULL, "
                + COL_TORQUE_NM + " REAL NOT NULL, "
                + COL_TORQUE_FTLB + " REAL NOT NULL, "
                + COL_CLAMP_FORCE_KN + " REAL NOT NULL, "
                + COL_TIMESTAMP + " TEXT NOT NULL"
                + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SAVED_SPECS);
        onCreate(db);
    }

    public long insertSpec(String boltSize, String standard, String grade,
                           String lubricant, double torqueNm, double torqueFtLb,
                           double clampForceKn, String timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_BOLT_SIZE, boltSize);
        values.put(COL_STANDARD, standard);
        values.put(COL_GRADE, grade);
        values.put(COL_LUBRICANT, lubricant);
        values.put(COL_TORQUE_NM, torqueNm);
        values.put(COL_TORQUE_FTLB, torqueFtLb);
        values.put(COL_CLAMP_FORCE_KN, clampForceKn);
        values.put(COL_TIMESTAMP, timestamp);
        return db.insert(TABLE_SAVED_SPECS, null, values);
    }

    public List<SavedSpec> getAllSpecs() {
        List<SavedSpec> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SAVED_SPECS, null, null, null, null, null, COL_ID + " DESC", "50");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COL_ID);
                int sizeIdx = cursor.getColumnIndexOrThrow(COL_BOLT_SIZE);
                int stdIdx = cursor.getColumnIndexOrThrow(COL_STANDARD);
                int gradeIdx = cursor.getColumnIndexOrThrow(COL_GRADE);
                int lubeIdx = cursor.getColumnIndexOrThrow(COL_LUBRICANT);
                int nmIdx = cursor.getColumnIndexOrThrow(COL_TORQUE_NM);
                int ftlbIdx = cursor.getColumnIndexOrThrow(COL_TORQUE_FTLB);
                int knIdx = cursor.getColumnIndexOrThrow(COL_CLAMP_FORCE_KN);
                int timeIdx = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);

                while (cursor.moveToNext()) {
                    list.add(new SavedSpec(
                            cursor.getLong(idIdx),
                            cursor.getString(sizeIdx),
                            cursor.getString(stdIdx),
                            cursor.getString(gradeIdx),
                            cursor.getString(lubeIdx),
                            cursor.getDouble(nmIdx),
                            cursor.getDouble(ftlbIdx),
                            cursor.getDouble(knIdx),
                            cursor.getString(timeIdx)
                    ));
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public int deleteSpec(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_SAVED_SPECS, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SAVED_SPECS, null, null);
    }
}
