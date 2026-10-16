package com.kimrosil.kerfnotch;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DadoStackDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "kerfnotch_workshop.db";
    private static final int DATABASE_VERSION = 1;

    public static class SavedSetup {
        public long id;
        public String name;
        public double targetKerf;
        public String details;
        public double deviation;

        public SavedSetup(long id, String name, double targetKerf, String details, double deviation) {
            this.id = id;
            this.name = name;
            this.targetKerf = targetKerf;
            this.details = details;
            this.deviation = deviation;
        }
    }

    public DadoStackDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE saved_setups (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "target_kerf REAL, " +
                "recipe_text TEXT, " +
                "deviation REAL, " +
                "timestamp INTEGER);");

        db.execSQL("CREATE TABLE box_joint_presets (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "board_width REAL, " +
                "blade_kerf REAL, " +
                "finger_width REAL, " +
                "pins INTEGER, " +
                "timestamp INTEGER);");

        // Seed common default plywood and joinery presets
        insertSeedData(db);
    }

    private void insertSeedData(SQLiteDatabase db) {
        ContentValues cv = new ContentValues();
        cv.put("name", "Baltic Birch 18mm Nominal");
        cv.put("target_kerf", 0.7087);
        cv.put("recipe_text", "2x 1/8 Blades + 3x 1/8 Chippers + 0.015 Shim + 0.010 Shim");
        cv.put("deviation", 0.0000);
        cv.put("timestamp", System.currentTimeMillis());
        db.insert("saved_setups", null, cv);

        cv.clear();
        cv.put("name", "Domestic 3/4\" Hardwood Dado");
        cv.put("target_kerf", 0.7500);
        cv.put("recipe_text", "2x 1/8 Blades + 4x 1/8 Chippers");
        cv.put("deviation", 0.0000);
        cv.put("timestamp", System.currentTimeMillis());
        db.insert("saved_setups", null, cv);

        cv.clear();
        cv.put("name", "1/4\" Drawer Bottom Groove");
        cv.put("target_kerf", 0.2500);
        cv.put("recipe_text", "2x 1/8 Outside Blades only");
        cv.put("deviation", 0.0000);
        cv.put("timestamp", System.currentTimeMillis());
        db.insert("saved_setups", null, cv);

        ContentValues bj = new ContentValues();
        bj.put("name", "Small Drawer Box (1x4 Stock)");
        bj.put("board_width", 3.500);
        bj.put("blade_kerf", 0.125);
        bj.put("finger_width", 0.250);
        bj.put("pins", 7);
        bj.put("timestamp", System.currentTimeMillis());
        db.insert("box_joint_presets", null, bj);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS saved_setups;");
        db.execSQL("DROP TABLE IF EXISTS box_joint_presets;");
        onCreate(db);
    }

    public long insertSetup(String name, double targetKerf, String recipeText, double deviation) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("target_kerf", targetKerf);
        cv.put("recipe_text", recipeText);
        cv.put("deviation", deviation);
        cv.put("timestamp", System.currentTimeMillis());
        return db.insert("saved_setups", null, cv);
    }

    public long insertBoxJointPreset(String name, double boardWidth, double bladeKerf, double fingerWidth, int pins) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("board_width", boardWidth);
        cv.put("blade_kerf", bladeKerf);
        cv.put("finger_width", fingerWidth);
        cv.put("pins", pins);
        cv.put("timestamp", System.currentTimeMillis());
        return db.insert("box_joint_presets", null, cv);
    }

    public List<SavedSetup> getAllSetups() {
        List<SavedSetup> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("saved_setups", null, null, null, null, null, "timestamp DESC");
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow("_id"));
                String name = c.getString(c.getColumnIndexOrThrow("name"));
                double target = c.getDouble(c.getColumnIndexOrThrow("target_kerf"));
                String recipe = c.getString(c.getColumnIndexOrThrow("recipe_text"));
                double dev = c.getDouble(c.getColumnIndexOrThrow("deviation"));
                list.add(new SavedSetup(id, name, target, recipe, dev));
            }
            c.close();
        }
        return list;
    }

    public List<SavedSetup> getAllBoxJointPresets() {
        List<SavedSetup> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("box_joint_presets", null, null, null, null, null, "timestamp DESC");
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow("_id"));
                String name = c.getString(c.getColumnIndexOrThrow("name"));
                double boardW = c.getDouble(c.getColumnIndexOrThrow("board_width"));
                double bladeK = c.getDouble(c.getColumnIndexOrThrow("blade_kerf"));
                int pins = c.getInt(c.getColumnIndexOrThrow("pins"));
                String details = String.format("Board: %.3f\", Kerf: %.4f\", %d Pins", boardW, bladeK, pins);
                list.add(new SavedSetup(id, name, boardW, details, bladeK));
            }
            c.close();
        }
        return list;
    }
}