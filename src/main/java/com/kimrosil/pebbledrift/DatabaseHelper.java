package com.kimrosil.pebbledrift;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite Local Persistence for PebbleDrift.
 * Stores mindful relaxation logs, lifetime zen statistics, and unlocked proverbs.
 * 100% offline, privacy-first, zero telemetry.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "pebble_drift_zen.db";
    private static final int DATABASE_VERSION = 1;

    // Table: Sessions
    public static final String TABLE_SESSIONS = "zen_sessions";
    public static final String COL_SESSION_ID = "_id";
    public static final String COL_SESSION_TIMESTAMP = "timestamp";
    public static final String COL_SESSION_DURATION = "duration_seconds";
    public static final String COL_SESSION_MODE = "mode";
    public static final String COL_SESSION_PEBBLES = "pebbles_dropped";
    public static final String COL_SESSION_CAIRNS = "cairns_balanced";

    // Table: Aphorisms
    public static final String TABLE_APHORISMS = "zen_aphorisms";
    public static final String COL_QUOTE_ID = "_id";
    public static final String COL_QUOTE_TEXT = "quote_text";
    public static final String COL_QUOTE_SOURCE = "quote_source";
    public static final String COL_QUOTE_UNLOCKED = "is_unlocked";
    public static final String COL_QUOTE_FAVORITE = "is_favorite";

    public static class Aphorism {
        public int id;
        public String quote;
        public String source;
        public boolean isUnlocked;
        public boolean isFavorite;

        public Aphorism(int id, String quote, String source, boolean isUnlocked, boolean isFavorite) {
            this.id = id;
            this.quote = quote;
            this.source = source;
            this.isUnlocked = isUnlocked;
            this.isFavorite = isFavorite;
        }
    }

    public static class LifetimeStats {
        public int totalSessions;
        public long totalMinutes;
        public int totalPebbles;
        public int totalCairns;
        public int unlockedQuotes;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SESSIONS + " (" +
                COL_SESSION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SESSION_TIMESTAMP + " INTEGER NOT NULL, " +
                COL_SESSION_DURATION + " INTEGER NOT NULL, " +
                COL_SESSION_MODE + " TEXT NOT NULL, " +
                COL_SESSION_PEBBLES + " INTEGER DEFAULT 0, " +
                COL_SESSION_CAIRNS + " INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_APHORISMS + " (" +
                COL_QUOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_QUOTE_TEXT + " TEXT NOT NULL, " +
                COL_QUOTE_SOURCE + " TEXT NOT NULL, " +
                COL_QUOTE_UNLOCKED + " INTEGER DEFAULT 0, " +
                COL_QUOTE_FAVORITE + " INTEGER DEFAULT 0)");

        seedAphorisms(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_APHORISMS);
        onCreate(db);
    }

    private void seedAphorisms(SQLiteDatabase db) {
        String[][] quotes = {
                {"Silence is not the absence of sound, but the presence of stillness.", "Zen Wisdom", "1"},
                {"The river shapes the stone not by force, but by persistence.", "Lao Tzu", "1"},
                {"In the circle of a single breath, the whole universe rests.", "Zen Proverb", "1"},
                {"Leave the water still, and it will become clear on its own.", "Alan Watts", "1"},
                {"Be like water making its way through cracks. Adjust with effortless grace.", "Bruce Lee", "0"},
                {"When you touch a smooth river stone with awareness, you touch the cosmos.", "Thich Nhat Hanh", "0"},
                {"Sitting quietly, doing nothing, spring comes, and the grass grows by itself.", "Matsuo Basho", "0"},
                {"Balance is not a rigid posture, but a continuous gentle adjustment.", "Zen Principle", "0"},
                {"The moon shines on the water; the water reflects the moon. Neither is troubled.", "Dogen Zenji", "0"},
                {"Let things be as they are, and the world will unfold in peace.", "Tao Te Ching", "0"},
                {"A single falling petal stirs the surface of the quietest pond.", "Ryokan", "0"},
                {"When you reach the top of the mountain, keep climbing with stillness.", "Zen Koan", "0"},
                {"Peace comes from within. Do not seek it without.", "Shakyamuni", "0"},
                {"Empty your mind of yesterday's ripples; today is a calm pool.", "Zen Aphorism", "0"}
        };

        for (String[] q : quotes) {
            ContentValues cv = new ContentValues();
            cv.put(COL_QUOTE_TEXT, q[0]);
            cv.put(COL_QUOTE_SOURCE, q[1]);
            cv.put(COL_QUOTE_UNLOCKED, Integer.parseInt(q[2]));
            cv.put(COL_QUOTE_FAVORITE, 0);
            db.insert(TABLE_APHORISMS, null, cv);
        }
    }

    public void logSession(long durationSeconds, String mode, int pebbles, int cairns) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SESSION_TIMESTAMP, System.currentTimeMillis());
        cv.put(COL_SESSION_DURATION, durationSeconds);
        cv.put(COL_SESSION_MODE, mode);
        cv.put(COL_SESSION_PEBBLES, pebbles);
        cv.put(COL_SESSION_CAIRNS, cairns);
        db.insert(TABLE_SESSIONS, null, cv);
    }

    public LifetimeStats getLifetimeStats() {
        LifetimeStats stats = new LifetimeStats();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor c = db.rawQuery("SELECT COUNT(*), SUM(" + COL_SESSION_DURATION + "), " +
                "SUM(" + COL_SESSION_PEBBLES + "), SUM(" + COL_SESSION_CAIRNS + ") FROM " + TABLE_SESSIONS, null);
        if (c != null && c.moveToFirst()) {
            stats.totalSessions = c.getInt(0);
            stats.totalMinutes = (c.getLong(1) + 59) / 60;
            stats.totalPebbles = c.getInt(2);
            stats.totalCairns = c.getInt(3);
            c.close();
        }

        Cursor c2 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_APHORISMS + " WHERE " + COL_QUOTE_UNLOCKED + " = 1", null);
        if (c2 != null && c2.moveToFirst()) {
            stats.unlockedQuotes = c2.getInt(0);
            c2.close();
        }

        return stats;
    }

    public List<Aphorism> getAllAphorisms() {
        List<Aphorism> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_QUOTE_ID + ", " + COL_QUOTE_TEXT + ", " +
                COL_QUOTE_SOURCE + ", " + COL_QUOTE_UNLOCKED + ", " + COL_QUOTE_FAVORITE +
                " FROM " + TABLE_APHORISMS + " ORDER BY " + COL_QUOTE_UNLOCKED + " DESC, " + COL_QUOTE_ID + " ASC", null);

        if (c != null) {
            while (c.moveToNext()) {
                list.add(new Aphorism(
                        c.getInt(0),
                        c.getString(1),
                        c.getString(2),
                        c.getInt(3) == 1,
                        c.getInt(4) == 1
                ));
            }
            c.close();
        }
        return list;
    }

    public Aphorism unlockNextAphorism() {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = db.rawQuery("SELECT " + COL_QUOTE_ID + ", " + COL_QUOTE_TEXT + ", " + COL_QUOTE_SOURCE +
                " FROM " + TABLE_APHORISMS + " WHERE " + COL_QUOTE_UNLOCKED + " = 0 LIMIT 1", null);

        if (c != null && c.moveToFirst()) {
            int id = c.getInt(0);
            String text = c.getString(1);
            String source = c.getString(2);
            c.close();

            ContentValues cv = new ContentValues();
            cv.put(COL_QUOTE_UNLOCKED, 1);
            db.update(TABLE_APHORISMS, cv, COL_QUOTE_ID + " = ?", new String[]{String.valueOf(id)});
            return new Aphorism(id, text, source, true, false);
        }
        if (c != null) c.close();
        return null;
    }

    public void toggleFavorite(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_APHORISMS + " SET " + COL_QUOTE_FAVORITE + " = 1 - " + COL_QUOTE_FAVORITE +
                " WHERE " + COL_QUOTE_ID + " = ?", new Object[]{id});
    }
}
