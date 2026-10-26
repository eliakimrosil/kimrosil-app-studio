package com.kimrosil.tiltlevel;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Local SQLite Storage for Machinist Surface, ADA Ramp, and Roof Pitch Inspection Logs.
 */
public class TiltDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "tilt_level_logs.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_LOGS = "slope_logs";

    public static final class SlopeRecord {
        public final long id;
        public final String siteName;
        public final double netAngleDeg;
        public final double gradePercent;
        public final double roofPitchX12;
        public final double tareOffsetDeg;
        public final double spanMeters;
        public final int axisMode;

        public SlopeRecord(
                long id,
                String siteName,
                double netAngleDeg,
                double gradePercent,
                double roofPitchX12,
                double tareOffsetDeg,
                double spanMeters,
                int axisMode) {
            this.id = id;
            this.siteName = siteName;
            this.netAngleDeg = netAngleDeg;
            this.gradePercent = gradePercent;
            this.roofPitchX12 = roofPitchX12;
            this.tareOffsetDeg = tareOffsetDeg;
            this.spanMeters = spanMeters;
            this.axisMode = axisMode;
        }
    }

    public TiltDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_LOGS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "site_name TEXT NOT NULL, "
                + "net_angle REAL NOT NULL, "
                + "grade_pct REAL NOT NULL, "
                + "pitch_x12 REAL NOT NULL, "
                + "tare_offset REAL NOT NULL, "
                + "span_meters REAL NOT NULL, "
                + "axis_mode INTEGER NOT NULL"
                + ")");

        seedRecord(db, "CNC Mill Bed Table", 0.05, 0.09, 0.01, 0.00, 1.50, TiltEngine.AXIS_PITCH);
        seedRecord(db, "Entryway ADA Ramp", 4.76, 8.33, 1.00, 0.00, 2.40, TiltEngine.AXIS_PITCH);
        seedRecord(db, "Porch Gable Roof 4:12", 18.43, 33.33, 4.00, 0.00, 3.60, TiltEngine.AXIS_PITCH);
    }

    private void seedRecord(
            SQLiteDatabase db,
            String name,
            double angle,
            double grade,
            double pitch,
            double tare,
            double span,
            int axis) {
        ContentValues cv = new ContentValues();
        cv.put("site_name", name);
        cv.put("net_angle", angle);
        cv.put("grade_pct", grade);
        cv.put("pitch_x12", pitch);
        cv.put("tare_offset", tare);
        cv.put("span_meters", span);
        cv.put("axis_mode", axis);
        db.insert(TABLE_LOGS, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        onCreate(db);
    }

    public long insertSlopeLog(
            String siteName,
            double netAngle,
            double gradePct,
            double pitchX12,
            double tareOffset,
            double spanMeters,
            int axisMode) {
        String safeName = (siteName == null || siteName.trim().isEmpty()) ? "Surface Check" : siteName.trim();
        if (safeName.length() > 24) {
            safeName = safeName.substring(0, 24);
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("site_name", safeName);
        cv.put("net_angle", TiltEngine.clamp(netAngle, -88.0, 88.0));
        cv.put("grade_pct", TiltEngine.clamp(gradePct, 0.0, 999.9));
        cv.put("pitch_x12", TiltEngine.clamp(pitchX12, 0.0, 120.0));
        cv.put("tare_offset", TiltEngine.clamp(tareOffset, -45.0, 45.0));
        cv.put("span_meters", TiltEngine.clamp(spanMeters, 0.1, 500.0));
        cv.put("axis_mode", axisMode);
        return db.insert(TABLE_LOGS, null, cv);
    }

    public void deleteSlopeLog(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_LOGS, "id = ?", new String[]{String.valueOf(id)});
    }

    public List<SlopeRecord> getAllLogs() {
        List<SlopeRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_LOGS, null, null, null, null, null, "id DESC", "15");
        try {
            while (c.moveToNext()) {
                list.add(new SlopeRecord(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("site_name")),
                        c.getDouble(c.getColumnIndexOrThrow("net_angle")),
                        c.getDouble(c.getColumnIndexOrThrow("grade_pct")),
                        c.getDouble(c.getColumnIndexOrThrow("pitch_x12")),
                        c.getDouble(c.getColumnIndexOrThrow("tare_offset")),
                        c.getDouble(c.getColumnIndexOrThrow("span_meters")),
                        c.getInt(c.getColumnIndexOrThrow("axis_mode"))
                ));
            }
        } finally {
            c.close();
        }
        return list;
    }
}
