package com.kimrosil.swagedie;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "swage_die.db";
    private static final int DATABASE_VERSION = 1;

    // Table: machine_profiles
    public static final String TABLE_MACHINES = "machine_profiles";
    public static final String COL_MACH_ID = "id";
    public static final String COL_MACH_NAME = "name";
    public static final String COL_MACH_TYPE = "type";
    public static final String COL_MACH_TONNAGE = "rated_tonnage";
    public static final String COL_MACH_DEFAULT = "is_default";

    // Table: tooling_presets
    public static final String TABLE_PRESETS = "tooling_presets";
    public static final String COL_PRE_ID = "id";
    public static final String COL_PRE_TITLE = "title";
    public static final String COL_PRE_MODE = "mode";
    public static final String COL_PRE_MAT = "material_name";
    public static final String COL_PRE_THICKNESS = "thickness_mm";
    public static final String COL_PRE_DIM1 = "dim1_mm";
    public static final String COL_PRE_DIM2 = "dim2_mm";
    public static final String COL_PRE_CLEARANCE = "clearance_pct";
    public static final String COL_PRE_TONNAGE = "tonnage";
    public static final String COL_PRE_CREATED_AT = "created_at";

    public static class MachineProfile {
        public long id;
        public String name;
        public String type;
        public double ratedTonnage;
        public boolean isDefault;

        public MachineProfile(long id, String name, String type, double tonnage, boolean isDef) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.ratedTonnage = tonnage;
            this.isDefault = isDef;
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.US, "%s (%.0f T)", name, ratedTonnage);
        }
    }

    public static class ToolingPreset {
        public long id;
        public String title;
        public int mode; // 0=Punch, 1=Swage, 2=Brake
        public String materialName;
        public double thicknessMm;
        public double dim1Mm;
        public double dim2Mm;
        public double clearancePct;
        public double tonnage;
        public long createdAt;

        public ToolingPreset() {}

        public ToolingPreset(long id, String title, int mode, String material,
                             double thickness, double dim1, double dim2,
                             double clearance, double tonnage, long created) {
            this.id = id;
            this.title = title;
            this.mode = mode;
            this.materialName = material;
            this.thicknessMm = thickness;
            this.dim1Mm = dim1;
            this.dim2Mm = dim2;
            this.clearancePct = clearance;
            this.tonnage = tonnage;
            this.createdAt = created;
        }
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_MACHINES + " (" +
                COL_MACH_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_MACH_NAME + " TEXT NOT NULL, " +
                COL_MACH_TYPE + " TEXT, " +
                COL_MACH_TONNAGE + " REAL NOT NULL, " +
                COL_MACH_DEFAULT + " INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_PRESETS + " (" +
                COL_PRE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PRE_TITLE + " TEXT NOT NULL, " +
                COL_PRE_MODE + " INTEGER NOT NULL, " +
                COL_PRE_MAT + " TEXT, " +
                COL_PRE_THICKNESS + " REAL, " +
                COL_PRE_DIM1 + " REAL, " +
                COL_PRE_DIM2 + " REAL, " +
                COL_PRE_CLEARANCE + " REAL, " +
                COL_PRE_TONNAGE + " REAL, " +
                COL_PRE_CREATED_AT + " INTEGER)");

        // Preload industry standard workshop machines
        preloadDefaultMachines(db);
    }

    private void preloadDefaultMachines(SQLiteDatabase db) {
        insertMachine(db, "Whitney 15T Turret Punch", "PUNCH", 15.0, 0);
        insertMachine(db, "Amada 30T CNC Punch", "PUNCH", 30.0, 1);
        insertMachine(db, "Trumpf 50T Hydraulic Punch", "PUNCH", 50.0, 0);
        insertMachine(db, "Fenn 10T Rotary Swager", "SWAGE", 10.0, 0);
        insertMachine(db, "Accurpress 60T Press Brake", "BRAKE", 60.0, 0);
        insertMachine(db, "Cincinnati 100T Press Brake", "BRAKE", 100.0, 0);
    }

    private void insertMachine(SQLiteDatabase db, String name, String type, double tonnage, int isDef) {
        ContentValues cv = new ContentValues();
        cv.put(COL_MACH_NAME, name);
        cv.put(COL_MACH_TYPE, type);
        cv.put(COL_MACH_TONNAGE, tonnage);
        cv.put(COL_MACH_DEFAULT, isDef);
        db.insert(TABLE_MACHINES, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MACHINES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRESETS);
        onCreate(db);
    }

    public List<MachineProfile> getAllMachines() {
        List<MachineProfile> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_MACHINES, null, null, null, null, null, COL_MACH_TONNAGE + " ASC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MACH_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(COL_MACH_NAME));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(COL_MACH_TYPE));
                double tonnage = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_MACH_TONNAGE));
                boolean isDef = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MACH_DEFAULT)) == 1;
                list.add(new MachineProfile(id, name, type, tonnage, isDef));
            }
            cursor.close();
        }
        return list;
    }

    public long addMachine(String name, String type, double tonnage) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_MACH_NAME, name);
        cv.put(COL_MACH_TYPE, type);
        cv.put(COL_MACH_TONNAGE, tonnage);
        cv.put(COL_MACH_DEFAULT, 0);
        return db.insert(TABLE_MACHINES, null, cv);
    }

    public void deleteMachine(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_MACHINES, COL_MACH_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<ToolingPreset> getAllPresets() {
        List<ToolingPreset> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PRESETS, null, null, null, null, null, COL_PRE_ID + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_PRE_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_PRE_TITLE));
                int mode = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PRE_MODE));
                String mat = cursor.getString(cursor.getColumnIndexOrThrow(COL_PRE_MAT));
                double thk = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRE_THICKNESS));
                double d1 = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRE_DIM1));
                double d2 = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRE_DIM2));
                double clr = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRE_CLEARANCE));
                double ton = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRE_TONNAGE));
                long created = cursor.getLong(cursor.getColumnIndexOrThrow(COL_PRE_CREATED_AT));
                list.add(new ToolingPreset(id, title, mode, mat, thk, d1, d2, clr, ton, created));
            }
            cursor.close();
        }
        return list;
    }

    public long addPreset(ToolingPreset preset) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_PRE_TITLE, preset.title);
        cv.put(COL_PRE_MODE, preset.mode);
        cv.put(COL_PRE_MAT, preset.materialName);
        cv.put(COL_PRE_THICKNESS, preset.thicknessMm);
        cv.put(COL_PRE_DIM1, preset.dim1Mm);
        cv.put(COL_PRE_DIM2, preset.dim2Mm);
        cv.put(COL_PRE_CLEARANCE, preset.clearancePct);
        cv.put(COL_PRE_TONNAGE, preset.tonnage);
        cv.put(COL_PRE_CREATED_AT, System.currentTimeMillis());
        return db.insert(TABLE_PRESETS, null, cv);
    }

    public void deletePreset(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_PRESETS, COL_PRE_ID + "=?", new String[]{String.valueOf(id)});
    }
}
