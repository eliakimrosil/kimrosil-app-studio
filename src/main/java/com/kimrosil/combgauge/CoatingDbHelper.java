package com.kimrosil.combgauge;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class CoatingDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "comb_gauge.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_PRESETS = "coating_presets";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_GENERIC_TYPE = "generic_type";
    public static final String COLUMN_VOLUME_SOLIDS = "volume_solids";
    public static final String COLUMN_MAX_THINNER = "max_thinner";
    public static final String COLUMN_IS_CUSTOM = "is_custom";

    public static final String TABLE_LOGS = "job_logs";
    public static final String COLUMN_LOG_ID = "id";
    public static final String COLUMN_LOG_TIMESTAMP = "timestamp";
    public static final String COLUMN_LOG_JOB_REF = "job_ref";
    public static final String COLUMN_LOG_COATING_NAME = "coating_name";
    public static final String COLUMN_LOG_TARGET_DFT = "target_dft";
    public static final String COLUMN_LOG_REQ_WFT = "req_wft";
    public static final String COLUMN_LOG_SOLIDS_PCT = "solids_pct";
    public static final String COLUMN_LOG_THINNER_PCT = "thinner_pct";
    public static final String COLUMN_LOG_PROFILE_LOSS = "anchor_profile_loss";
    public static final String COLUMN_LOG_UNIT = "unit";

    public static class CoatingPreset {
        public long id;
        public String name;
        public String genericType;
        public double volumeSolids;
        public double maxThinner;
        public boolean isCustom;

        public CoatingPreset(long id, String name, String genericType, double volumeSolids, double maxThinner, boolean isCustom) {
            this.id = id;
            this.name = name;
            this.genericType = genericType;
            this.volumeSolids = volumeSolids;
            this.maxThinner = maxThinner;
            this.isCustom = isCustom;
        }
    }

    public static class JobLog {
        public long id;
        public long timestamp;
        public String jobRef;
        public String coatingName;
        public double targetDft;
        public double reqWft;
        public double solidsPct;
        public double thinnerPct;
        public double anchorProfileLoss;
        public String unit;

        public JobLog(long id, long timestamp, String jobRef, String coatingName, double targetDft,
                      double reqWft, double solidsPct, double thinnerPct, double anchorProfileLoss, String unit) {
            this.id = id;
            this.timestamp = timestamp;
            this.jobRef = jobRef;
            this.coatingName = coatingName;
            this.targetDft = targetDft;
            this.reqWft = reqWft;
            this.solidsPct = solidsPct;
            this.thinnerPct = thinnerPct;
            this.anchorProfileLoss = anchorProfileLoss;
            this.unit = unit;
        }
    }

    public CoatingDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createPresets = "CREATE TABLE " + TABLE_PRESETS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_NAME + " TEXT NOT NULL, "
                + COLUMN_GENERIC_TYPE + " TEXT NOT NULL, "
                + COLUMN_VOLUME_SOLIDS + " REAL NOT NULL, "
                + COLUMN_MAX_THINNER + " REAL NOT NULL, "
                + COLUMN_IS_CUSTOM + " INTEGER NOT NULL DEFAULT 0);";

        String createLogs = "CREATE TABLE " + TABLE_LOGS + " ("
                + COLUMN_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_LOG_TIMESTAMP + " INTEGER NOT NULL, "
                + COLUMN_LOG_JOB_REF + " TEXT NOT NULL, "
                + COLUMN_LOG_COATING_NAME + " TEXT NOT NULL, "
                + COLUMN_LOG_TARGET_DFT + " REAL NOT NULL, "
                + COLUMN_LOG_REQ_WFT + " REAL NOT NULL, "
                + COLUMN_LOG_SOLIDS_PCT + " REAL NOT NULL, "
                + COLUMN_LOG_THINNER_PCT + " REAL NOT NULL, "
                + COLUMN_LOG_PROFILE_LOSS + " REAL NOT NULL, "
                + COLUMN_LOG_UNIT + " TEXT NOT NULL);";

        db.execSQL(createPresets);
        db.execSQL(createLogs);

        seedDefaultPresets(db);
    }

    private void seedDefaultPresets(SQLiteDatabase db) {
        insertPresetInternal(db, "Inorganic Zinc Primer", "Zinc", 62.0, 10.0, 0);
        insertPresetInternal(db, "Polyamide Epoxy Intermediate", "Epoxy", 75.0, 15.0, 0);
        insertPresetInternal(db, "Aliphatic Acrylic Polyurethane", "Polyurethane", 58.0, 10.0, 0);
        insertPresetInternal(db, "High-Solids Elastomeric", "High-Solids", 90.0, 5.0, 0);
        insertPresetInternal(db, "Solvent-Free Novolac Tank Lining", "Epoxy", 100.0, 0.0, 0);
        insertPresetInternal(db, "Zinc-Rich Epoxy Primer", "Zinc", 68.0, 10.0, 0);
        insertPresetInternal(db, "Surface-Tolerant Mastic", "Epoxy", 80.0, 10.0, 0);
        insertPresetInternal(db, "High-Build Polysiloxane Topcoat", "High-Solids", 82.0, 5.0, 0);
    }

    private void insertPresetInternal(SQLiteDatabase db, String name, String genericType,
                                      double volumeSolids, double maxThinner, int isCustom) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_NAME, name);
        cv.put(COLUMN_GENERIC_TYPE, genericType);
        cv.put(COLUMN_VOLUME_SOLIDS, volumeSolids);
        cv.put(COLUMN_MAX_THINNER, maxThinner);
        cv.put(COLUMN_IS_CUSTOM, isCustom);
        db.insert(TABLE_PRESETS, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRESETS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        onCreate(db);
    }

    public long insertCustomPreset(String name, String genericType, double volumeSolids, double maxThinner) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_NAME, name);
        cv.put(COLUMN_GENERIC_TYPE, genericType);
        cv.put(COLUMN_VOLUME_SOLIDS, volumeSolids);
        cv.put(COLUMN_MAX_THINNER, maxThinner);
        cv.put(COLUMN_IS_CUSTOM, 1);
        return db.insert(TABLE_PRESETS, null, cv);
    }

    public List<CoatingPreset> getPresets(String categoryFilter) {
        List<CoatingPreset> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selection = null;
        String[] args = null;
        if (categoryFilter != null && !categoryFilter.equalsIgnoreCase("All")) {
            selection = COLUMN_GENERIC_TYPE + " = ?";
            args = new String[]{categoryFilter};
        }

        Cursor c = db.query(TABLE_PRESETS, null, selection, args, null, null, COLUMN_ID + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow(COLUMN_ID));
                String name = c.getString(c.getColumnIndexOrThrow(COLUMN_NAME));
                String type = c.getString(c.getColumnIndexOrThrow(COLUMN_GENERIC_TYPE));
                double solids = c.getDouble(c.getColumnIndexOrThrow(COLUMN_VOLUME_SOLIDS));
                double thinner = c.getDouble(c.getColumnIndexOrThrow(COLUMN_MAX_THINNER));
                boolean isCustom = c.getInt(c.getColumnIndexOrThrow(COLUMN_IS_CUSTOM)) == 1;
                list.add(new CoatingPreset(id, name, type, solids, thinner, isCustom));
            }
            c.close();
        }
        return list;
    }

    public long insertJobLog(String jobRef, String coatingName, double targetDft, double reqWft,
                             double solidsPct, double thinnerPct, double anchorProfileLoss, String unit) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_LOG_TIMESTAMP, System.currentTimeMillis());
        cv.put(COLUMN_LOG_JOB_REF, jobRef);
        cv.put(COLUMN_LOG_COATING_NAME, coatingName);
        cv.put(COLUMN_LOG_TARGET_DFT, targetDft);
        cv.put(COLUMN_LOG_REQ_WFT, reqWft);
        cv.put(COLUMN_LOG_SOLIDS_PCT, solidsPct);
        cv.put(COLUMN_LOG_THINNER_PCT, thinnerPct);
        cv.put(COLUMN_LOG_PROFILE_LOSS, anchorProfileLoss);
        cv.put(COLUMN_LOG_UNIT, unit);
        return db.insert(TABLE_LOGS, null, cv);
    }

    public List<JobLog> getRecentJobLogs(int limit) {
        List<JobLog> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_LOGS, null, null, null, null, null,
                COLUMN_LOG_TIMESTAMP + " DESC", String.valueOf(limit));
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow(COLUMN_LOG_ID));
                long timestamp = c.getLong(c.getColumnIndexOrThrow(COLUMN_LOG_TIMESTAMP));
                String ref = c.getString(c.getColumnIndexOrThrow(COLUMN_LOG_JOB_REF));
                String coating = c.getString(c.getColumnIndexOrThrow(COLUMN_LOG_COATING_NAME));
                double targetDft = c.getDouble(c.getColumnIndexOrThrow(COLUMN_LOG_TARGET_DFT));
                double reqWft = c.getDouble(c.getColumnIndexOrThrow(COLUMN_LOG_REQ_WFT));
                double solids = c.getDouble(c.getColumnIndexOrThrow(COLUMN_LOG_SOLIDS_PCT));
                double thinner = c.getDouble(c.getColumnIndexOrThrow(COLUMN_LOG_THINNER_PCT));
                double loss = c.getDouble(c.getColumnIndexOrThrow(COLUMN_LOG_PROFILE_LOSS));
                String unit = c.getString(c.getColumnIndexOrThrow(COLUMN_LOG_UNIT));
                list.add(new JobLog(id, timestamp, ref, coating, targetDft, reqWft, solids, thinner, loss, unit));
            }
            c.close();
        }
        return list;
    }
}