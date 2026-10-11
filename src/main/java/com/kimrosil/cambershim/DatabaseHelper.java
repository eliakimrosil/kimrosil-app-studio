package com.kimrosil.cambershim;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "cambershim.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_PROJECTS = "door_projects";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "project_name";
    public static final String COL_HEIGHT = "height_mm";
    public static final String COL_WIDTH = "width_mm";
    public static final String COL_SPREAD = "spread_mm";
    public static final String COL_DEFLECTION = "deflection_mm";
    public static final String COL_TOP_SHIM = "top_shim_mm";
    public static final String COL_ANGLE = "angle_deg";
    public static final String COL_TIMESTAMP = "created_at";

    public static class ProjectRecord {
        public long id;
        public String name;
        public double heightMm;
        public double widthMm;
        public double spreadMm;
        public double deflectionMm;
        public double topShimMm;
        public double angleDeg;
        public long timestamp;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PROJECTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT, "
                + COL_HEIGHT + " REAL, "
                + COL_WIDTH + " REAL, "
                + COL_SPREAD + " REAL, "
                + COL_DEFLECTION + " REAL, "
                + COL_TOP_SHIM + " REAL, "
                + COL_ANGLE + " REAL, "
                + COL_TIMESTAMP + " INTEGER"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROJECTS);
        onCreate(db);
    }

    public long insertProject(String name, double height, double width, double spread, 
                              double deflection, double topShim, double angle) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, name);
        cv.put(COL_HEIGHT, height);
        cv.put(COL_WIDTH, width);
        cv.put(COL_SPREAD, spread);
        cv.put(COL_DEFLECTION, deflection);
        cv.put(COL_TOP_SHIM, topShim);
        cv.put(COL_ANGLE, angle);
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        return db.insert(TABLE_PROJECTS, null, cv);
    }

    public List<ProjectRecord> getAllProjects() {
        List<ProjectRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query(TABLE_PROJECTS, null, null, null, null, null, COL_TIMESTAMP + " DESC");
            if (c != null && c.moveToFirst()) {
                do {
                    ProjectRecord r = new ProjectRecord();
                    r.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                    r.name = c.getString(c.getColumnIndexOrThrow(COL_NAME));
                    r.heightMm = c.getDouble(c.getColumnIndexOrThrow(COL_HEIGHT));
                    r.widthMm = c.getDouble(c.getColumnIndexOrThrow(COL_WIDTH));
                    r.spreadMm = c.getDouble(c.getColumnIndexOrThrow(COL_SPREAD));
                    r.deflectionMm = c.getDouble(c.getColumnIndexOrThrow(COL_DEFLECTION));
                    r.topShimMm = c.getDouble(c.getColumnIndexOrThrow(COL_TOP_SHIM));
                    r.angleDeg = c.getDouble(c.getColumnIndexOrThrow(COL_ANGLE));
                    r.timestamp = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                    list.add(r);
                } while (c.moveToNext());
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return list;
    }

    public ProjectRecord getProjectById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query(TABLE_PROJECTS, null, COL_ID + "=?", new String[]{String.valueOf(id)}, 
                         null, null, null);
            if (c != null && c.moveToFirst()) {
                ProjectRecord r = new ProjectRecord();
                r.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                r.name = c.getString(c.getColumnIndexOrThrow(COL_NAME));
                r.heightMm = c.getDouble(c.getColumnIndexOrThrow(COL_HEIGHT));
                r.widthMm = c.getDouble(c.getColumnIndexOrThrow(COL_WIDTH));
                r.spreadMm = c.getDouble(c.getColumnIndexOrThrow(COL_SPREAD));
                r.deflectionMm = c.getDouble(c.getColumnIndexOrThrow(COL_DEFLECTION));
                r.topShimMm = c.getDouble(c.getColumnIndexOrThrow(COL_TOP_SHIM));
                r.angleDeg = c.getDouble(c.getColumnIndexOrThrow(COL_ANGLE));
                r.timestamp = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                return r;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    public boolean deleteProject(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_PROJECTS, COL_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }
}