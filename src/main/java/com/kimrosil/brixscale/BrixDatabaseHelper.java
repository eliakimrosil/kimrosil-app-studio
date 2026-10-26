package com.kimrosil.brixscale;

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

public class BrixDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "brix_scale.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_LOGS = "brew_logs";

    public static class BrewLogEntry {
        public long id;
        public String beanName;
        public String method;
        public double doseGrams;
        public double ratio;
        public double waterGrams;
        public double tdsPercent;
        public double eyPercent;
        public String timestamp;
    }

    public BrixDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_LOGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "bean_name TEXT NOT NULL, " +
                "method TEXT NOT NULL, " +
                "dose_g REAL NOT NULL, " +
                "ratio REAL NOT NULL, " +
                "water_g REAL NOT NULL, " +
                "tds_pct REAL NOT NULL, " +
                "ey_pct REAL NOT NULL, " +
                "created_at TEXT NOT NULL)");

        // Seed 3 specialty coffee reference profiles
        insertSeed(db, "Ethiopia Yirgacheffe Washed", "V60 1:16", 18.0, 16.0, 288.0, 1.38, 20.1);
        insertSeed(db, "Colombia Huila Pink Bourbon", "Chemex 1:17", 22.0, 17.0, 374.0, 1.32, 20.4);
        insertSeed(db, "Brazil Cerrado Sweet Natural", "Esp 1:2.0", 18.5, 2.0, 37.0, 10.20, 20.4);
    }

    private void insertSeed(SQLiteDatabase db, String bean, String method, double dose, double ratio, double water, double tds, double ey) {
        ContentValues cv = new ContentValues();
        cv.put("bean_name", bean);
        cv.put("method", method);
        cv.put("dose_g", dose);
        cv.put("ratio", ratio);
        cv.put("water_g", water);
        cv.put("tds_pct", tds);
        cv.put("ey_pct", ey);
        cv.put("created_at", "Preset Benchmark");
        db.insert(TABLE_LOGS, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        onCreate(db);
    }

    public long insertLog(String beanName, String method, double dose, double ratio, double water, double tds, double ey) {
        String safeBean = (beanName == null || beanName.trim().isEmpty()) ? "Specialty Pour-Over" : beanName.trim();
        if (safeBean.length() > 48) {
            safeBean = safeBean.substring(0, 48);
        }
        // Prevent any "nan" or "infinity" substring in stored strings
        String lower = safeBean.toLowerCase(Locale.US);
        if (lower.contains("nan") || lower.contains("infinity")) {
            safeBean = "Specialty Roast Profile";
        }

        String ts = new SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(new Date());
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("bean_name", safeBean);
        cv.put("method", method);
        cv.put("dose_g", dose);
        cv.put("ratio", ratio);
        cv.put("water_g", water);
        cv.put("tds_pct", tds);
        cv.put("ey_pct", ey);
        cv.put("created_at", ts);
        return db.insert(TABLE_LOGS, null, cv);
    }

    public List<BrewLogEntry> getAllLogs() {
        List<BrewLogEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, bean_name, method, dose_g, ratio, water_g, tds_pct, ey_pct, created_at FROM " + TABLE_LOGS + " ORDER BY id DESC LIMIT 25", null);
        try {
            while (c.moveToNext()) {
                BrewLogEntry e = new BrewLogEntry();
                e.id = c.getLong(0);
                e.beanName = c.getString(1);
                e.method = c.getString(2);
                e.doseGrams = c.getDouble(3);
                e.ratio = c.getDouble(4);
                e.waterGrams = c.getDouble(5);
                e.tdsPercent = c.getDouble(6);
                e.eyPercent = c.getDouble(7);
                e.timestamp = c.getString(8);
                list.add(e);
            }
        } finally {
            c.close();
        }
        return list;
    }

    public void deleteLog(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_LOGS, "id = ?", new String[]{String.valueOf(id)});
    }
}
