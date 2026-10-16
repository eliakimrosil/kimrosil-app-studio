package com.kimrosil.flaregage;

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

    private static final String DATABASE_NAME = "flaregage_signoffs.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SIGNOFFS = "inspection_signoffs";
    public static final String COL_ID = "id";
    public static final String COL_JOB_REF = "job_ref";
    public static final String COL_STANDARD = "standard";
    public static final String COL_SIZE = "tubing_size";
    public static final String COL_MATERIAL = "material";
    public static final String COL_MEASURED_OD = "measured_od";
    public static final String COL_MIN_OD = "min_od";
    public static final String COL_MAX_OD = "max_od";
    public static final String COL_STATUS = "status";
    public static final String COL_STATUS_TEXT = "status_text";
    public static final String COL_TORQUE = "torque_range";
    public static final String COL_TIMESTAMP = "timestamp";

    public static class InspectionRecord {
        public long id;
        public String jobRef;
        public String standard;
        public String size;
        public String material;
        public double measuredOd;
        public double minOd;
        public double maxOd;
        public String status;
        public String statusText;
        public String torqueRange;
        public String timestamp;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_SIGNOFFS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_JOB_REF + " TEXT, "
                + COL_STANDARD + " TEXT, "
                + COL_SIZE + " TEXT, "
                + COL_MATERIAL + " TEXT, "
                + COL_MEASURED_OD + " REAL, "
                + COL_MIN_OD + " REAL, "
                + COL_MAX_OD + " REAL, "
                + COL_STATUS + " TEXT, "
                + COL_STATUS_TEXT + " TEXT, "
                + COL_TORQUE + " TEXT, "
                + COL_TIMESTAMP + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SIGNOFFS);
        onCreate(db);
    }

    public long insertSignoff(String jobRef, String standard, String size, String material,
                              double measuredOd, double minOd, double maxOd,
                              String status, String statusText, String torqueRange) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_JOB_REF, jobRef.isEmpty() ? "Shop Benchmark" : jobRef);
        cv.put(COL_STANDARD, standard);
        cv.put(COL_SIZE, size);
        cv.put(COL_MATERIAL, material);
        cv.put(COL_MEASURED_OD, measuredOd);
        cv.put(COL_MIN_OD, minOd);
        cv.put(COL_MAX_OD, maxOd);
        cv.put(COL_STATUS, status);
        cv.put(COL_STATUS_TEXT, statusText);
        cv.put(COL_TORQUE, torqueRange);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        cv.put(COL_TIMESTAMP, sdf.format(new Date()));

        return db.insert(TABLE_SIGNOFFS, null, cv);
    }

    public List<InspectionRecord> getAllSignoffs() {
        List<InspectionRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_SIGNOFFS, null, null, null, null, null, COL_ID + " DESC", "100");
        if (c != null) {
            try {
                int idIdx = c.getColumnIndex(COL_ID);
                int jobIdx = c.getColumnIndex(COL_JOB_REF);
                int stdIdx = c.getColumnIndex(COL_STANDARD);
                int sizeIdx = c.getColumnIndex(COL_SIZE);
                int matIdx = c.getColumnIndex(COL_MATERIAL);
                int measIdx = c.getColumnIndex(COL_MEASURED_OD);
                int minIdx = c.getColumnIndex(COL_MIN_OD);
                int maxIdx = c.getColumnIndex(COL_MAX_OD);
                int statIdx = c.getColumnIndex(COL_STATUS);
                int statTextIdx = c.getColumnIndex(COL_STATUS_TEXT);
                int torqIdx = c.getColumnIndex(COL_TORQUE);
                int timeIdx = c.getColumnIndex(COL_TIMESTAMP);

                while (c.moveToNext()) {
                    InspectionRecord r = new InspectionRecord();
                    r.id = c.getLong(idIdx);
                    r.jobRef = c.getString(jobIdx);
                    r.standard = c.getString(stdIdx);
                    r.size = c.getString(sizeIdx);
                    r.material = c.getString(matIdx);
                    r.measuredOd = c.getDouble(measIdx);
                    r.minOd = c.getDouble(minIdx);
                    r.maxOd = c.getDouble(maxIdx);
                    r.status = c.getString(statIdx);
                    r.statusText = c.getString(statTextIdx);
                    r.torqueRange = c.getString(torqIdx);
                    r.timestamp = c.getString(timeIdx);
                    list.add(r);
                }
            } finally {
                c.close();
            }
        }
        return list;
    }

    public boolean deleteSignoff(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_SIGNOFFS, COL_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public void clearAllSignoffs() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SIGNOFFS, null, null);
    }
}
