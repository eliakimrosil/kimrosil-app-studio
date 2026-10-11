package com.kimrosil.grainline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite OpenHelper handling zero-data-loss local persistence
 * for GrainLine cut calculations, bolt inventory, and project sessions.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "grainline.db";
    private static final int DATABASE_VERSION = 1;

    // Table: sessions (cut projects & yardage calculations)
    public static final String TABLE_SESSIONS = "sessions";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_PROJECT_NAME = "project_name";
    public static final String COLUMN_FABRIC_TYPE = "fabric_type";
    public static final String COLUMN_FABRIC_WIDTH = "fabric_width";
    public static final String COLUMN_PIECE_WIDTH = "piece_width";
    public static final String COLUMN_PIECE_LENGTH = "piece_length";
    public static final String COLUMN_PIECE_COUNT = "piece_count";
    public static final String COLUMN_SEAM_ALLOWANCE = "seam_allowance";
    public static final String COLUMN_SHRINKAGE_PCT = "shrinkage_pct";
    public static final String COLUMN_ALLOW_ROTATION = "allow_rotation";
    public static final String COLUMN_TOTAL_YARDS = "total_yards";
    public static final String COLUMN_TOTAL_METERS = "total_meters";
    public static final String COLUMN_OFFCUT_PCT = "offcut_pct";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_NOTES = "notes";

    // Table: presets (quick fabric types & standard bolt configs)
    public static final String TABLE_PRESETS = "presets";
    public static final String COLUMN_PRESET_ID = "_id";
    public static final String COLUMN_PRESET_NAME = "preset_name";
    public static final String COLUMN_PRESET_FABRIC_WIDTH = "default_width";
    public static final String COLUMN_PRESET_SHRINKAGE = "default_shrinkage";
    public static final String COLUMN_PRESET_SEAM = "default_seam";

    private static DatabaseHelper sInstance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createSessionsTable = "CREATE TABLE " + TABLE_SESSIONS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PROJECT_NAME + " TEXT NOT NULL, "
                + COLUMN_FABRIC_TYPE + " TEXT, "
                + COLUMN_FABRIC_WIDTH + " REAL NOT NULL, "
                + COLUMN_PIECE_WIDTH + " REAL NOT NULL, "
                + COLUMN_PIECE_LENGTH + " REAL NOT NULL, "
                + COLUMN_PIECE_COUNT + " INTEGER NOT NULL, "
                + COLUMN_SEAM_ALLOWANCE + " REAL NOT NULL, "
                + COLUMN_SHRINKAGE_PCT + " REAL NOT NULL, "
                + COLUMN_ALLOW_ROTATION + " INTEGER NOT NULL DEFAULT 1, "
                + COLUMN_TOTAL_YARDS + " REAL NOT NULL, "
                + COLUMN_TOTAL_METERS + " REAL NOT NULL, "
                + COLUMN_OFFCUT_PCT + " REAL NOT NULL, "
                + COLUMN_TIMESTAMP + " INTEGER NOT NULL, "
                + COLUMN_NOTES + " TEXT"
                + ");";

        String createPresetsTable = "CREATE TABLE " + TABLE_PRESETS + " ("
                + COLUMN_PRESET_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_PRESET_NAME + " TEXT NOT NULL, "
                + COLUMN_PRESET_FABRIC_WIDTH + " REAL NOT NULL, "
                + COLUMN_PRESET_SHRINKAGE + " REAL NOT NULL, "
                + COLUMN_PRESET_SEAM + " REAL NOT NULL"
                + ");";

        db.execSQL(createSessionsTable);
        db.execSQL(createPresetsTable);

        // Prepopulate standard sewing/quilting bolt defaults
        insertDefaultPreset(db, "Quilting Cotton (44\")", 44.0, 3.0, 0.25);
        insertDefaultPreset(db, "Apparel Linen (54\")", 54.0, 5.0, 0.50);
        insertDefaultPreset(db, "Wide Backing Cotton (108\")", 108.0, 3.0, 0.25);
        insertDefaultPreset(db, "Garment Wool / Suiting (60\")", 60.0, 2.0, 0.625);
    }

    private void insertDefaultPreset(SQLiteDatabase db, String name, double width, double shrinkage, double seam) {
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_PRESET_NAME, name);
        cv.put(COLUMN_PRESET_FABRIC_WIDTH, width);
        cv.put(COLUMN_PRESET_SHRINKAGE, shrinkage);
        cv.put(COLUMN_PRESET_SEAM, seam);
        db.insert(TABLE_PRESETS, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRESETS);
        onCreate(db);
    }

    public static class SessionRecord {
        public long id;
        public String projectName;
        public String fabricType;
        public double fabricWidth;
        public double pieceWidth;
        public double pieceLength;
        public int pieceCount;
        public double seamAllowance;
        public double shrinkagePct;
        public boolean allowRotation;
        public double totalYards;
        public double totalMeters;
        public double offcutPct;
        public long timestamp;
        public String notes;
    }

    public long insertSession(SessionRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_PROJECT_NAME, record.projectName);
        cv.put(COLUMN_FABRIC_TYPE, record.fabricType);
        cv.put(COLUMN_FABRIC_WIDTH, record.fabricWidth);
        cv.put(COLUMN_PIECE_WIDTH, record.pieceWidth);
        cv.put(COLUMN_PIECE_LENGTH, record.pieceLength);
        cv.put(COLUMN_PIECE_COUNT, record.pieceCount);
        cv.put(COLUMN_SEAM_ALLOWANCE, record.seamAllowance);
        cv.put(COLUMN_SHRINKAGE_PCT, record.shrinkagePct);
        cv.put(COLUMN_ALLOW_ROTATION, record.allowRotation ? 1 : 0);
        cv.put(COLUMN_TOTAL_YARDS, record.totalYards);
        cv.put(COLUMN_TOTAL_METERS, record.totalMeters);
        cv.put(COLUMN_OFFCUT_PCT, record.offcutPct);
        cv.put(COLUMN_TIMESTAMP, record.timestamp);
        cv.put(COLUMN_NOTES, record.notes != null ? record.notes : "");
        return db.insert(TABLE_SESSIONS, null, cv);
    }

    public boolean updateSession(SessionRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_PROJECT_NAME, record.projectName);
        cv.put(COLUMN_FABRIC_TYPE, record.fabricType);
        cv.put(COLUMN_FABRIC_WIDTH, record.fabricWidth);
        cv.put(COLUMN_PIECE_WIDTH, record.pieceWidth);
        cv.put(COLUMN_PIECE_LENGTH, record.pieceLength);
        cv.put(COLUMN_PIECE_COUNT, record.pieceCount);
        cv.put(COLUMN_SEAM_ALLOWANCE, record.seamAllowance);
        cv.put(COLUMN_SHRINKAGE_PCT, record.shrinkagePct);
        cv.put(COLUMN_ALLOW_ROTATION, record.allowRotation ? 1 : 0);
        cv.put(COLUMN_TOTAL_YARDS, record.totalYards);
        cv.put(COLUMN_TOTAL_METERS, record.totalMeters);
        cv.put(COLUMN_OFFCUT_PCT, record.offcutPct);
        cv.put(COLUMN_NOTES, record.notes != null ? record.notes : "");
        int rows = db.update(TABLE_SESSIONS, cv, COLUMN_ID + " = ?", new String[]{String.valueOf(record.id)});
        return rows > 0;
    }

    public List<SessionRecord> getAllSessions() {
        List<SessionRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_SESSIONS, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    SessionRecord record = new SessionRecord();
                    record.id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                    record.projectName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROJECT_NAME));
                    record.fabricType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FABRIC_TYPE));
                    record.fabricWidth = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_FABRIC_WIDTH));
                    record.pieceWidth = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PIECE_WIDTH));
                    record.pieceLength = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PIECE_LENGTH));
                    record.pieceCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PIECE_COUNT));
                    record.seamAllowance = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SEAM_ALLOWANCE));
                    record.shrinkagePct = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SHRINKAGE_PCT));
                    record.allowRotation = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ALLOW_ROTATION)) == 1;
                    record.totalYards = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_YARDS));
                    record.totalMeters = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_METERS));
                    record.offcutPct = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_OFFCUT_PCT));
                    record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                    record.notes = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES));
                    list.add(record);
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    public SessionRecord getSessionById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        SessionRecord record = null;
        try {
            cursor = db.query(TABLE_SESSIONS, null, COLUMN_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                record = new SessionRecord();
                record.id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                record.projectName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROJECT_NAME));
                record.fabricType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FABRIC_TYPE));
                record.fabricWidth = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_FABRIC_WIDTH));
                record.pieceWidth = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PIECE_WIDTH));
                record.pieceLength = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PIECE_LENGTH));
                record.pieceCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PIECE_COUNT));
                record.seamAllowance = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SEAM_ALLOWANCE));
                record.shrinkagePct = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SHRINKAGE_PCT));
                record.allowRotation = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ALLOW_ROTATION)) == 1;
                record.totalYards = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_YARDS));
                record.totalMeters = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_METERS));
                record.offcutPct = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_OFFCUT_PCT));
                record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                record.notes = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return record;
    }

    public boolean deleteSession(long id) {
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(TABLE_SESSIONS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        return rows > 0;
    }

    public void clearAllSessions() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_SESSIONS, null, null);
    }
}