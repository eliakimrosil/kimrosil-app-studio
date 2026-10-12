package com.kimrosil.flangematch;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class FlangeDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "flangematch.db";
    private static final int DATABASE_VERSION = 1;

    // Table: Flange Specifications
    public static final String TABLE_FLANGES = "flange_specs";
    public static final String COL_ID = "_id";
    public static final String COL_STANDARD = "standard";           // ASME B16.5, DIN EN 1092-1
    public static final String COL_NPS = "nominal_size";            // e.g. "2\"", "4\"", "DN50"
    public static final String COL_NPS_INCHES = "size_inches";      // numeric sort/filter
    public static final String COL_PRESSURE_CLASS = "pressure_class";// e.g. "150#", "300#", "PN16"
    public static final String COL_PRESSURE_PSI = "pressure_psi";   // numeric
    public static final String COL_BOLT_COUNT = "bolt_count";       // e.g. 4, 8, 12, 16, 20, 24
    public static final String COL_STUD_DIAMETER = "stud_diameter"; // e.g. "5/8\"", "M16"
    public static final String COL_WRENCH_SIZE = "wrench_size";     // e.g. "1-1/16\"", "24 mm"
    public static final String COL_BCD_INCHES = "bcd_inches";       // Bolt Circle Diameter (inches)
    public static final String COL_HOLE_DIA_INCHES = "hole_dia_inches"; // Bolt Hole Diameter (inches)
    public static final String COL_BASE_TORQUE_FTLB = "base_torque_ftlb"; // Typical lubricated B7 stud torque ft-lb

    // Table: Torque Inspection Logs
    public static final String TABLE_LOGS = "torque_logs";
    public static final String COL_LOG_ID = "_id";
    public static final String COL_LOG_STANDARD = "standard";
    public static final String COL_LOG_NPS = "nominal_size";
    public static final String COL_LOG_CLASS = "pressure_class";
    public static final String COL_LOG_BOLT_COUNT = "bolt_count";
    public static final String COL_LOG_TARGET_TORQUE = "target_torque";
    public static final String COL_LOG_UNIT = "unit";
    public static final String COL_LOG_PASSES_DONE = "passes_completed";
    public static final String COL_LOG_TIMESTAMP = "timestamp";
    public static final String COL_LOG_NOTES = "notes";

    public static class FlangeRecord {
        public long id;
        public String standard;
        public String nominalSize;
        public double sizeInches;
        public String pressureClass;
        public int pressurePsi;
        public int boltCount;
        public String studDiameter;
        public String wrenchSize;
        public double bcdInches;
        public double holeDiaInches;
        public double baseTorqueFtLb;
    }

    public static class TorqueLogRecord {
        public long id;
        public String standard;
        public String nominalSize;
        public String pressureClass;
        public int boltCount;
        public double targetTorque;
        public String unit;
        public int passesCompleted;
        public String timestamp;
        public String notes;
    }

    public FlangeDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createFlangeTable = "CREATE TABLE " + TABLE_FLANGES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_STANDARD + " TEXT NOT NULL, "
                + COL_NPS + " TEXT NOT NULL, "
                + COL_NPS_INCHES + " REAL NOT NULL, "
                + COL_PRESSURE_CLASS + " TEXT NOT NULL, "
                + COL_PRESSURE_PSI + " INTEGER NOT NULL, "
                + COL_BOLT_COUNT + " INTEGER NOT NULL, "
                + COL_STUD_DIAMETER + " TEXT NOT NULL, "
                + COL_WRENCH_SIZE + " TEXT NOT NULL, "
                + COL_BCD_INCHES + " REAL NOT NULL, "
                + COL_HOLE_DIA_INCHES + " REAL NOT NULL, "
                + COL_BASE_TORQUE_FTLB + " REAL NOT NULL);";

        String createLogTable = "CREATE TABLE " + TABLE_LOGS + " ("
                + COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_LOG_STANDARD + " TEXT NOT NULL, "
                + COL_LOG_NPS + " TEXT NOT NULL, "
                + COL_LOG_CLASS + " TEXT NOT NULL, "
                + COL_LOG_BOLT_COUNT + " INTEGER NOT NULL, "
                + COL_LOG_TARGET_TORQUE + " REAL NOT NULL, "
                + COL_LOG_UNIT + " TEXT NOT NULL, "
                + COL_LOG_PASSES_DONE + " INTEGER NOT NULL, "
                + COL_LOG_TIMESTAMP + " TEXT NOT NULL, "
                + COL_LOG_NOTES + " TEXT);";

        db.execSQL(createFlangeTable);
        db.execSQL(createLogTable);

        seedStandardFlanges(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FLANGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        onCreate(db);
    }

    private void seedStandardFlanges(SQLiteDatabase db) {
        // ASME B16.5 Class 150#
        insertFlangeRow(db, "ASME B16.5", "1/2\"", 0.5, "150#", 150, 4, "1/2\"", "7/8\"", 2.38, 0.62, 30.0);
        insertFlangeRow(db, "ASME B16.5", "3/4\"", 0.75, "150#", 150, 4, "1/2\"", "7/8\"", 2.75, 0.62, 30.0);
        insertFlangeRow(db, "ASME B16.5", "1\"", 1.0, "150#", 150, 4, "1/2\"", "7/8\"", 3.12, 0.62, 30.0);
        insertFlangeRow(db, "ASME B16.5", "1-1/2\"", 1.5, "150#", 150, 4, "1/2\"", "7/8\"", 3.88, 0.62, 30.0);
        insertFlangeRow(db, "ASME B16.5", "2\"", 2.0, "150#", 150, 4, "5/8\"", "1-1/16\"", 4.75, 0.75, 60.0);
        insertFlangeRow(db, "ASME B16.5", "3\"", 3.0, "150#", 150, 4, "5/8\"", "1-1/16\"", 6.00, 0.75, 60.0);
        insertFlangeRow(db, "ASME B16.5", "4\"", 4.0, "150#", 150, 8, "5/8\"", "1-1/16\"", 7.50, 0.75, 60.0);
        insertFlangeRow(db, "ASME B16.5", "6\"", 6.0, "150#", 150, 8, "3/4\"", "1-1/4\"", 9.50, 0.88, 100.0);
        insertFlangeRow(db, "ASME B16.5", "8\"", 8.0, "150#", 150, 8, "3/4\"", "1-1/4\"", 11.75, 0.88, 100.0);
        insertFlangeRow(db, "ASME B16.5", "10\"", 10.0, "150#", 150, 12, "7/8\"", "1-7/16\"", 14.25, 1.00, 160.0);
        insertFlangeRow(db, "ASME B16.5", "12\"", 12.0, "150#", 150, 12, "7/8\"", "1-7/16\"", 17.00, 1.00, 160.0);
        insertFlangeRow(db, "ASME B16.5", "14\"", 14.0, "150#", 150, 12, "1\"", "1-5/8\"", 18.75, 1.12, 245.0);
        insertFlangeRow(db, "ASME B16.5", "16\"", 16.0, "150#", 150, 16, "1\"", "1-5/8\"", 21.25, 1.12, 245.0);
        insertFlangeRow(db, "ASME B16.5", "18\"", 18.0, "150#", 150, 16, "1-1/8\"", "1-13/16\"", 22.75, 1.25, 355.0);
        insertFlangeRow(db, "ASME B16.5", "20\"", 20.0, "150#", 150, 20, "1-1/8\"", "1-13/16\"", 25.00, 1.25, 355.0);
        insertFlangeRow(db, "ASME B16.5", "24\"", 24.0, "150#", 150, 20, "1-1/4\"", "2\"", 29.50, 1.38, 500.0);

        // ASME B16.5 Class 300#
        insertFlangeRow(db, "ASME B16.5", "1/2\"", 0.5, "300#", 300, 4, "1/2\"", "7/8\"", 2.62, 0.62, 30.0);
        insertFlangeRow(db, "ASME B16.5", "3/4\"", 0.75, "300#", 300, 4, "5/8\"", "1-1/16\"", 3.25, 0.75, 60.0);
        insertFlangeRow(db, "ASME B16.5", "1\"", 1.0, "300#", 300, 4, "5/8\"", "1-1/16\"", 3.50, 0.75, 60.0);
        insertFlangeRow(db, "ASME B16.5", "1-1/2\"", 1.5, "300#", 300, 4, "3/4\"", "1-1/4\"", 4.50, 0.88, 100.0);
        insertFlangeRow(db, "ASME B16.5", "2\"", 2.0, "300#", 300, 8, "5/8\"", "1-1/16\"", 5.00, 0.75, 60.0);
        insertFlangeRow(db, "ASME B16.5", "3\"", 3.0, "300#", 300, 8, "3/4\"", "1-1/4\"", 6.62, 0.88, 100.0);
        insertFlangeRow(db, "ASME B16.5", "4\"", 4.0, "300#", 300, 8, "3/4\"", "1-1/4\"", 7.88, 0.88, 100.0);
        insertFlangeRow(db, "ASME B16.5", "6\"", 6.0, "300#", 300, 12, "3/4\"", "1-1/4\"", 10.62, 0.88, 100.0);
        insertFlangeRow(db, "ASME B16.5", "8\"", 8.0, "300#", 300, 12, "7/8\"", "1-7/16\"", 13.00, 1.00, 160.0);
        insertFlangeRow(db, "ASME B16.5", "10\"", 10.0, "300#", 300, 16, "1\"", "1-5/8\"", 15.25, 1.12, 245.0);
        insertFlangeRow(db, "ASME B16.5", "12\"", 12.0, "300#", 300, 16, "1-1/8\"", "1-13/16\"", 17.75, 1.25, 355.0);
        insertFlangeRow(db, "ASME B16.5", "16\"", 16.0, "300#", 300, 20, "1-1/4\"", "2\"", 22.50, 1.38, 500.0);
        insertFlangeRow(db, "ASME B16.5", "20\"", 20.0, "300#", 300, 24, "1-1/4\"", "2\"", 27.00, 1.38, 500.0);

        // ASME B16.5 Class 600#
        insertFlangeRow(db, "ASME B16.5", "2\"", 2.0, "600#", 600, 8, "5/8\"", "1-1/16\"", 5.00, 0.75, 80.0);
        insertFlangeRow(db, "ASME B16.5", "3\"", 3.0, "600#", 600, 8, "3/4\"", "1-1/4\"", 6.62, 0.88, 135.0);
        insertFlangeRow(db, "ASME B16.5", "4\"", 4.0, "600#", 600, 8, "7/8\"", "1-7/16\"", 8.50, 1.00, 215.0);
        insertFlangeRow(db, "ASME B16.5", "6\"", 6.0, "600#", 600, 12, "1\"", "1-5/8\"", 11.50, 1.12, 330.0);
        insertFlangeRow(db, "ASME B16.5", "8\"", 8.0, "600#", 600, 12, "1-1/8\"", "1-13/16\"", 13.75, 1.25, 480.0);
        insertFlangeRow(db, "ASME B16.5", "10\"", 10.0, "600#", 600, 16, "1-1/4\"", "2\"", 17.00, 1.38, 675.0);
        insertFlangeRow(db, "ASME B16.5", "12\"", 12.0, "600#", 600, 20, "1-1/4\"", "2\"", 19.25, 1.38, 675.0);

        // DIN EN 1092-1 PN16 & PN40 (European Metric Standard)
        insertFlangeRow(db, "DIN EN 1092-1", "DN25 (1\")", 1.0, "PN16", 232, 4, "M12", "19 mm", 3.35, 0.55, 35.0);
        insertFlangeRow(db, "DIN EN 1092-1", "DN50 (2\")", 2.0, "PN16", 232, 4, "M16", "24 mm", 4.92, 0.71, 75.0);
        insertFlangeRow(db, "DIN EN 1092-1", "DN80 (3\")", 3.0, "PN16", 232, 8, "M16", "24 mm", 6.30, 0.71, 75.0);
        insertFlangeRow(db, "DIN EN 1092-1", "DN100 (4\")", 4.0, "PN16", 232, 8, "M16", "24 mm", 7.09, 0.71, 75.0);
        insertFlangeRow(db, "DIN EN 1092-1", "DN150 (6\")", 6.0, "PN16", 232, 8, "M20", "30 mm", 9.45, 0.87, 140.0);
        insertFlangeRow(db, "DIN EN 1092-1", "DN200 (8\")", 8.0, "PN16", 232, 12, "M20", "30 mm", 11.61, 0.87, 140.0);
        insertFlangeRow(db, "DIN EN 1092-1", "DN250 (10\")", 10.0, "PN16", 232, 12, "M24", "36 mm", 13.98, 1.02, 240.0);
        insertFlangeRow(db, "DIN EN 1092-1", "DN300 (12\")", 12.0, "PN16", 232, 12, "M24", "36 mm", 16.14, 1.02, 240.0);
    }

    private void insertFlangeRow(SQLiteDatabase db, String std, String nps, double npsInch,
                                String pClass, int psi, int boltCount, String studDia,
                                String wrench, double bcd, double holeDia, double torqueFtLb) {
        ContentValues cv = new ContentValues();
        cv.put(COL_STANDARD, std);
        cv.put(COL_NPS, nps);
        cv.put(COL_NPS_INCHES, npsInch);
        cv.put(COL_PRESSURE_CLASS, pClass);
        cv.put(COL_PRESSURE_PSI, psi);
        cv.put(COL_BOLT_COUNT, boltCount);
        cv.put(COL_STUD_DIAMETER, studDia);
        cv.put(COL_WRENCH_SIZE, wrench);
        cv.put(COL_BCD_INCHES, bcd);
        cv.put(COL_HOLE_DIA_INCHES, holeDia);
        cv.put(COL_BASE_TORQUE_FTLB, torqueFtLb);
        db.insert(TABLE_FLANGES, null, cv);
    }

    public List<String> getDistinctStandards() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT DISTINCT " + COL_STANDARD + " FROM " + TABLE_FLANGES + " ORDER BY " + COL_STANDARD + " ASC", null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0));
            }
            cursor.close();
        }
        return list;
    }

    public List<String> getClassesForStandard(String standard) {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT DISTINCT " + COL_PRESSURE_CLASS + " FROM " + TABLE_FLANGES
                + " WHERE " + COL_STANDARD + " = ? ORDER BY " + COL_PRESSURE_PSI + " ASC", new String[]{standard});
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0));
            }
            cursor.close();
        }
        return list;
    }

    public List<String> getSizesForStandardAndClass(String standard, String pClass) {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_NPS + " FROM " + TABLE_FLANGES
                + " WHERE " + COL_STANDARD + " = ? AND " + COL_PRESSURE_CLASS + " = ? ORDER BY " + COL_NPS_INCHES + " ASC",
                new String[]{standard, pClass});
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0));
            }
            cursor.close();
        }
        return list;
    }

    public FlangeRecord getFlangeSpec(String standard, String pClass, String nps) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_FLANGES
                + " WHERE " + COL_STANDARD + " = ? AND " + COL_PRESSURE_CLASS + " = ? AND " + COL_NPS + " = ? LIMIT 1",
                new String[]{standard, pClass, nps});
        if (c != null && c.moveToFirst()) {
            FlangeRecord r = new FlangeRecord();
            r.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
            r.standard = c.getString(c.getColumnIndexOrThrow(COL_STANDARD));
            r.nominalSize = c.getString(c.getColumnIndexOrThrow(COL_NPS));
            r.sizeInches = c.getDouble(c.getColumnIndexOrThrow(COL_NPS_INCHES));
            r.pressureClass = c.getString(c.getColumnIndexOrThrow(COL_PRESSURE_CLASS));
            r.pressurePsi = c.getInt(c.getColumnIndexOrThrow(COL_PRESSURE_PSI));
            r.boltCount = c.getInt(c.getColumnIndexOrThrow(COL_BOLT_COUNT));
            r.studDiameter = c.getString(c.getColumnIndexOrThrow(COL_STUD_DIAMETER));
            r.wrenchSize = c.getString(c.getColumnIndexOrThrow(COL_WRENCH_SIZE));
            r.bcdInches = c.getDouble(c.getColumnIndexOrThrow(COL_BCD_INCHES));
            r.holeDiaInches = c.getDouble(c.getColumnIndexOrThrow(COL_HOLE_DIA_INCHES));
            r.baseTorqueFtLb = c.getDouble(c.getColumnIndexOrThrow(COL_BASE_TORQUE_FTLB));
            c.close();
            return r;
        }
        if (c != null) c.close();
        return null;
    }

    public long insertTorqueLog(String standard, String nps, String pClass, int boltCount,
                                double targetTorque, String unit, int passesDone, String notes) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LOG_STANDARD, standard);
        cv.put(COL_LOG_NPS, nps);
        cv.put(COL_LOG_CLASS, pClass);
        cv.put(COL_LOG_BOLT_COUNT, boltCount);
        cv.put(COL_LOG_TARGET_TORQUE, targetTorque);
        cv.put(COL_LOG_UNIT, unit);
        cv.put(COL_LOG_PASSES_DONE, passesDone);
        cv.put(COL_LOG_TIMESTAMP, java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()));
        cv.put(COL_LOG_NOTES, notes);
        return db.insert(TABLE_LOGS, null, cv);
    }

    public List<TorqueLogRecord> getRecentLogs(int limit) {
        List<TorqueLogRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_LOGS + " ORDER BY " + COL_LOG_ID + " DESC LIMIT " + limit, null);
        if (c != null) {
            while (c.moveToNext()) {
                TorqueLogRecord log = new TorqueLogRecord();
                log.id = c.getLong(c.getColumnIndexOrThrow(COL_LOG_ID));
                log.standard = c.getString(c.getColumnIndexOrThrow(COL_LOG_STANDARD));
                log.nominalSize = c.getString(c.getColumnIndexOrThrow(COL_LOG_NPS));
                log.pressureClass = c.getString(c.getColumnIndexOrThrow(COL_LOG_CLASS));
                log.boltCount = c.getInt(c.getColumnIndexOrThrow(COL_LOG_BOLT_COUNT));
                log.targetTorque = c.getDouble(c.getColumnIndexOrThrow(COL_LOG_TARGET_TORQUE));
                log.unit = c.getString(c.getColumnIndexOrThrow(COL_LOG_UNIT));
                log.passesCompleted = c.getInt(c.getColumnIndexOrThrow(COL_LOG_PASSES_DONE));
                log.timestamp = c.getString(c.getColumnIndexOrThrow(COL_LOG_TIMESTAMP));
                log.notes = c.getString(c.getColumnIndexOrThrow(COL_LOG_NOTES));
                list.add(log);
            }
            c.close();
        }
        return list;
    }
}