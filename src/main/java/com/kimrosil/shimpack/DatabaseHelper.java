package com.kimrosil.shimpack;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite Local Data Persistence for Machine Alignment Records and Geometry Presets.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "shimpack.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_PRESETS = "machine_presets";
    public static final String TABLE_RECORDS = "alignment_records";

    // Presets columns
    public static final String COL_ID = "id";
    public static final String COL_MACHINE_TAG = "machine_tag";
    public static final String COL_D1 = "d1_front";
    public static final String COL_D2 = "d2_back";
    public static final String COL_DIAL_DIAM = "dial_diam";
    public static final String COL_RPM = "rpm";

    // Records columns
    public static final String COL_REC_ID = "id";
    public static final String COL_REC_TAG = "machine_tag";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_METHOD = "method";
    public static final String COL_F1_TARGET = "f1_shim_target";
    public static final String COL_F2_TARGET = "f2_shim_target";
    public static final String COL_F1_STACK = "f1_stack";
    public static final String COL_F2_STACK = "f2_stack";
    public static final String COL_TOLERANCE_STATUS = "tolerance_status";

    public static class AlignmentRecord {
        public long id;
        public String machineTag;
        public long timestamp;
        public String method;
        public double f1Target;
        public double f2Target;
        public String f1Stack;
        public String f2Stack;
        public String toleranceStatus;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PRESETS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_MACHINE_TAG + " TEXT, "
                + COL_D1 + " REAL, "
                + COL_D2 + " REAL, "
                + COL_DIAL_DIAM + " REAL, "
                + COL_RPM + " INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_RECORDS + " ("
                + COL_REC_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_REC_TAG + " TEXT, "
                + COL_TIMESTAMP + " INTEGER, "
                + COL_METHOD + " TEXT, "
                + COL_F1_TARGET + " REAL, "
                + COL_F2_TARGET + " REAL, "
                + COL_F1_STACK + " TEXT, "
                + COL_F2_STACK + " TEXT, "
                + COL_TOLERANCE_STATUS + " TEXT)");

        // Seed demo presets
        insertPreset(db, "P-101 End Suction (1800 RPM)", 8.5, 14.0, 5.0, 1800);
        insertPreset(db, "C-204 Screw Compressor (3600 RPM)", 12.0, 22.0, 6.5, 3600);
        insertPreset(db, "F-501 Slurry Pump (1200 RPM)", 15.0, 28.0, 8.0, 1200);
    }

    private void insertPreset(SQLiteDatabase db, String tag, double d1, double d2, double dial, int rpm) {
        ContentValues cv = new ContentValues();
        cv.put(COL_MACHINE_TAG, tag);
        cv.put(COL_D1, d1);
        cv.put(COL_D2, d2);
        cv.put(COL_DIAL_DIAM, dial);
        cv.put(COL_RPM, rpm);
        db.insert(TABLE_PRESETS, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRESETS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDS);
        onCreate(db);
    }

    public long saveRecord(String tag, String method, double f1, double f2, String s1, String s2, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_REC_TAG, tag);
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        cv.put(COL_METHOD, method);
        cv.put(COL_F1_TARGET, f1);
        cv.put(COL_F2_TARGET, f2);
        cv.put(COL_F1_STACK, s1);
        cv.put(COL_F2_STACK, s2);
        cv.put(COL_TOLERANCE_STATUS, status);
        return db.insert(TABLE_RECORDS, null, cv);
    }

    public List<AlignmentRecord> getAllRecords() {
        List<AlignmentRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_RECORDS + " ORDER BY " + COL_REC_ID + " DESC LIMIT 20", null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                AlignmentRecord rec = new AlignmentRecord();
                rec.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_REC_ID));
                rec.machineTag = cursor.getString(cursor.getColumnIndexOrThrow(COL_REC_TAG));
                rec.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                rec.method = cursor.getString(cursor.getColumnIndexOrThrow(COL_METHOD));
                rec.f1Target = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_F1_TARGET));
                rec.f2Target = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_F2_TARGET));
                rec.f1Stack = cursor.getString(cursor.getColumnIndexOrThrow(COL_F1_STACK));
                rec.f2Stack = cursor.getString(cursor.getColumnIndexOrThrow(COL_F2_STACK));
                rec.toleranceStatus = cursor.getString(cursor.getColumnIndexOrThrow(COL_TOLERANCE_STATUS));
                list.add(rec);
            }
            cursor.close();
        }
        return list;
    }
}