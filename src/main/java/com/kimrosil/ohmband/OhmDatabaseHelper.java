package com.kimrosil.ohmband;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class OhmDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ohmband.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_PARTS = "parts";
    public static final String COL_ID = "_id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_TYPE = "type";
    public static final String COL_NOMINAL = "nominal_ohms";
    public static final String COL_FORMATTED = "formatted_value";
    public static final String COL_TOLERANCE = "tolerance_percent";
    public static final String COL_BANDS_CSV = "bands_csv";
    public static final String COL_EIA = "eia_series";

    public static class PartRecord {
        public long id;
        public long timestamp;
        public String type;
        public double nominalOhms;
        public String formattedValue;
        public double tolerancePercent;
        public String bandsCsv;
        public String eiaSeries;
    }

    public OhmDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_PARTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TIMESTAMP + " INTEGER NOT NULL, "
                + COL_TYPE + " TEXT NOT NULL, "
                + COL_NOMINAL + " REAL NOT NULL, "
                + COL_FORMATTED + " TEXT NOT NULL, "
                + COL_TOLERANCE + " REAL NOT NULL, "
                + COL_BANDS_CSV + " TEXT NOT NULL, "
                + COL_EIA + " TEXT NOT NULL"
                + ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PARTS);
        onCreate(db);
    }

    public long insertPart(PartRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIMESTAMP, record.timestamp);
        cv.put(COL_TYPE, record.type);
        cv.put(COL_NOMINAL, record.nominalOhms);
        cv.put(COL_FORMATTED, record.formattedValue);
        cv.put(COL_TOLERANCE, record.tolerancePercent);
        cv.put(COL_BANDS_CSV, record.bandsCsv);
        cv.put(COL_EIA, record.eiaSeries);
        return db.insert(TABLE_PARTS, null, cv);
    }

    public List<PartRecord> getAllParts() {
        List<PartRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PARTS, null, null, null, null, null, COL_TIMESTAMP + " DESC", "30");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COL_ID);
                int timeIdx = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
                int typeIdx = cursor.getColumnIndexOrThrow(COL_TYPE);
                int nomIdx = cursor.getColumnIndexOrThrow(COL_NOMINAL);
                int fmtIdx = cursor.getColumnIndexOrThrow(COL_FORMATTED);
                int tolIdx = cursor.getColumnIndexOrThrow(COL_TOLERANCE);
                int csvIdx = cursor.getColumnIndexOrThrow(COL_BANDS_CSV);
                int eiaIdx = cursor.getColumnIndexOrThrow(COL_EIA);

                while (cursor.moveToNext()) {
                    PartRecord rec = new PartRecord();
                    rec.id = cursor.getLong(idIdx);
                    rec.timestamp = cursor.getLong(timeIdx);
                    rec.type = cursor.getString(typeIdx);
                    rec.nominalOhms = cursor.getDouble(nomIdx);
                    rec.formattedValue = cursor.getString(fmtIdx);
                    rec.tolerancePercent = cursor.getDouble(tolIdx);
                    rec.bandsCsv = cursor.getString(csvIdx);
                    rec.eiaSeries = cursor.getString(eiaIdx);
                    list.add(rec);
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public void deletePart(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_PARTS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void clearAllParts() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_PARTS, null, null);
    }
}
