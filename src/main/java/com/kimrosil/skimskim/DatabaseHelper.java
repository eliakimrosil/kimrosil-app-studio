package com.kimrosil.skimskim;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "skim_skim_pool.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_CYCLE_LOGS = "cycle_logs";
    public static final String COL_ID = "_id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_FILTER_PROFILE = "filter_profile";
    public static final String COL_BACKWASH_SEC = "backwash_seconds";
    public static final String COL_RINSE_SEC = "rinse_seconds";
    public static final String COL_FILTER_AREA = "filter_area_sqft";
    public static final String COL_DE_DOSAGE_LBS = "de_dosage_lbs";
    public static final String COL_CLEAN_PSI = "clean_psi";
    public static final String COL_DIRTY_PSI = "dirty_psi";
    public static final String COL_NOTES = "notes";

    public static class CycleRecord {
        public long id;
        public long timestamp;
        public String filterProfile;
        public int backwashSeconds;
        public int rinseSeconds;
        public double filterAreaSqFt;
        public double deDosageLbs;
        public double cleanPsi;
        public double dirtyPsi;
        public String notes;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createCycleLogs = "CREATE TABLE " + TABLE_CYCLE_LOGS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_FILTER_PROFILE + " TEXT NOT NULL, "
                + COL_BACKWASH_SEC + " INTEGER NOT NULL, "
                + COL_RINSE_SEC + " INTEGER NOT NULL, "
                + COL_FILTER_AREA + " REAL NOT NULL, "
                + COL_DE_DOSAGE_LBS + " REAL NOT NULL, "
                + COL_CLEAN_PSI + " REAL NOT NULL, "
                + COL_DIRTY_PSI + " REAL NOT NULL, "
                + COL_NOTES + " TEXT)";
        db.execSQL(createCycleLogs);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CYCLE_LOGS);
        onCreate(db);
    }

    public long insertLog(String profile, int bwSec, int rinseSec, double area, double dosage, double cleanPsi, double dirtyPsi, String notes) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        cv.put(COL_FILTER_PROFILE, profile != null ? profile : "Main DE Filter");
        cv.put(COL_BACKWASH_SEC, bwSec);
        cv.put(COL_RINSE_SEC, rinseSec);
        cv.put(COL_FILTER_AREA, area);
        cv.put(COL_DE_DOSAGE_LBS, dosage);
        cv.put(COL_CLEAN_PSI, cleanPsi);
        cv.put(COL_DIRTY_PSI, dirtyPsi);
        cv.put(COL_NOTES, notes != null ? notes : "");
        return db.insert(TABLE_CYCLE_LOGS, null, cv);
    }

    public List<CycleRecord> getAllLogs() {
        List<CycleRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_CYCLE_LOGS, null, null, null, null, null, COL_TIMESTAMP + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    CycleRecord record = new CycleRecord();
                    record.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                    record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                    record.filterProfile = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILTER_PROFILE));
                    record.backwashSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BACKWASH_SEC));
                    record.rinseSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(COL_RINSE_SEC));
                    record.filterAreaSqFt = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_FILTER_AREA));
                    record.deDosageLbs = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_DE_DOSAGE_LBS));
                    record.cleanPsi = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_CLEAN_PSI));
                    record.dirtyPsi = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_DIRTY_PSI));
                    record.notes = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTES));
                    list.add(record);
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    public CycleRecord getLogById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_CYCLE_LOGS, null, COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                CycleRecord record = new CycleRecord();
                record.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                record.filterProfile = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILTER_PROFILE));
                record.backwashSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BACKWASH_SEC));
                record.rinseSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(COL_RINSE_SEC));
                record.filterAreaSqFt = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_FILTER_AREA));
                record.deDosageLbs = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_DE_DOSAGE_LBS));
                record.cleanPsi = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_CLEAN_PSI));
                record.dirtyPsi = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_DIRTY_PSI));
                record.notes = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTES));
                return record;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    public boolean updateNotes(long id, String newNotes) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NOTES, newNotes);
        return db.update(TABLE_CYCLE_LOGS, cv, COL_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean deleteLog(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_CYCLE_LOGS, COL_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }
}