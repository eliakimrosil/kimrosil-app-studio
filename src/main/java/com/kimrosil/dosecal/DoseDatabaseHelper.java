package com.kimrosil.dosecal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DoseDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "dosecal.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_RECORDS = "dose_records";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_DRUG_NAME = "drug_name";
    public static final String COLUMN_WEIGHT_KG = "weight_kg";
    public static final String COLUMN_DOSE_RATE = "dose_rate";
    public static final String COLUMN_VOLUME_ML = "volume_ml";
    public static final String COLUMN_SINGLE_MG = "single_dose_mg";
    public static final String COLUMN_INTERVAL = "interval_hours";
    public static final String COLUMN_TIMESTAMP = "created_at";

    public static class DoseRecord {
        public long id;
        public String drugName;
        public double weightKg;
        public double doseRate;
        public double volumeMl;
        public double singleDoseMg;
        public int intervalHours;
        public long createdAt;
    }

    public DoseDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_RECORDS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_DRUG_NAME + " TEXT, " +
                COLUMN_WEIGHT_KG + " REAL, " +
                COLUMN_DOSE_RATE + " REAL, " +
                COLUMN_VOLUME_ML + " REAL, " +
                COLUMN_SINGLE_MG + " REAL, " +
                COLUMN_INTERVAL + " INTEGER, " +
                COLUMN_TIMESTAMP + " INTEGER" +
                ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDS);
        onCreate(db);
    }

    public long insertRecord(String drugName, double weightKg, double doseRate,
                             double volumeMl, double singleDoseMg, int intervalHours) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_DRUG_NAME, drugName);
        cv.put(COLUMN_WEIGHT_KG, weightKg);
        cv.put(COLUMN_DOSE_RATE, doseRate);
        cv.put(COLUMN_VOLUME_ML, volumeMl);
        cv.put(COLUMN_SINGLE_MG, singleDoseMg);
        cv.put(COLUMN_INTERVAL, intervalHours);
        cv.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        return db.insert(TABLE_RECORDS, null, cv);
    }

    public List<DoseRecord> getAllRecords() {
        List<DoseRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_RECORDS, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID);
                int nameIdx = cursor.getColumnIndexOrThrow(COLUMN_DRUG_NAME);
                int wtIdx = cursor.getColumnIndexOrThrow(COLUMN_WEIGHT_KG);
                int rateIdx = cursor.getColumnIndexOrThrow(COLUMN_DOSE_RATE);
                int volIdx = cursor.getColumnIndexOrThrow(COLUMN_VOLUME_ML);
                int mgIdx = cursor.getColumnIndexOrThrow(COLUMN_SINGLE_MG);
                int intIdx = cursor.getColumnIndexOrThrow(COLUMN_INTERVAL);
                int timeIdx = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP);

                while (cursor.moveToNext()) {
                    DoseRecord r = new DoseRecord();
                    r.id = cursor.getLong(idIdx);
                    r.drugName = cursor.getString(nameIdx);
                    r.weightKg = cursor.getDouble(wtIdx);
                    r.doseRate = cursor.getDouble(rateIdx);
                    r.volumeMl = cursor.getDouble(volIdx);
                    r.singleDoseMg = cursor.getDouble(mgIdx);
                    r.intervalHours = cursor.getInt(intIdx);
                    r.createdAt = cursor.getLong(timeIdx);
                    list.add(r);
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_RECORDS, null, null);
    }
}
