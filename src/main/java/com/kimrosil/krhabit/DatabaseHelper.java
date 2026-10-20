package com.kimrosil.krhabit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "kr_habit.db";
    private static final int DATABASE_VERSION = 1;

    // Table habits
    public static final String TABLE_HABITS = "habits";
    public static final String COL_HABIT_ID = "id";
    public static final String COL_HABIT_UUID = "uuid";
    public static final String COL_HABIT_TITLE = "title";
    public static final String COL_HABIT_DESC = "description";
    public static final String COL_HABIT_CATEGORY = "category";
    public static final String COL_HABIT_FREQ = "frequency"; // days per week (1-7)
    public static final String COL_HABIT_CREATED_AT = "created_at";
    public static final String COL_HABIT_UPDATED_AT = "updated_at";
    public static final String COL_HABIT_SYNCED = "synced"; // 0 or 1

    // Table habit_logs
    public static final String TABLE_HABIT_LOGS = "habit_logs";
    public static final String COL_LOG_ID = "id";
    public static final String COL_LOG_HABIT_UUID = "habit_uuid";
    public static final String COL_LOG_DATE = "log_date"; // YYYY-MM-DD
    public static final String COL_LOG_COMPLETED = "completed"; // 1 or 0
    public static final String COL_LOG_UPDATED_AT = "updated_at";
    public static final String COL_LOG_SYNCED = "synced"; // 0 or 1

    public static class HabitItem {
        public long id;
        public String uuid;
        public String title;
        public String description;
        public String category;
        public int targetDaysPerWeek;
        public long createdAt;
        public long updatedAt;
        public boolean isCompletedToday;
        public double strengthScore;
        public int currentStreak;
        public int bestStreak;
        public int totalCompletions;
        public boolean[] thirtyDayHistory = new boolean[30]; // 0 = 29 days ago, 29 = today
    }

    public static class HabitLogEntry {
        public String habitUuid;
        public String dateStr;
        public boolean completed;
        public long updatedAt;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_HABITS + " ("
                + COL_HABIT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_HABIT_UUID + " TEXT UNIQUE NOT NULL, "
                + COL_HABIT_TITLE + " TEXT NOT NULL, "
                + COL_HABIT_DESC + " TEXT, "
                + COL_HABIT_CATEGORY + " TEXT NOT NULL, "
                + COL_HABIT_FREQ + " INTEGER DEFAULT 7, "
                + COL_HABIT_CREATED_AT + " INTEGER NOT NULL, "
                + COL_HABIT_UPDATED_AT + " INTEGER NOT NULL, "
                + COL_HABIT_SYNCED + " INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_HABIT_LOGS + " ("
                + COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_LOG_HABIT_UUID + " TEXT NOT NULL, "
                + COL_LOG_DATE + " TEXT NOT NULL, "
                + COL_LOG_COMPLETED + " INTEGER DEFAULT 1, "
                + COL_LOG_UPDATED_AT + " INTEGER NOT NULL, "
                + COL_LOG_SYNCED + " INTEGER DEFAULT 0, "
                + "UNIQUE (" + COL_LOG_HABIT_UUID + ", " + COL_LOG_DATE + ") ON CONFLICT REPLACE)");

        // Insert initial domain default habits if empty
        insertSampleHabits(db);
    }

    private void insertSampleHabits(SQLiteDatabase db) {
        long now = System.currentTimeMillis();
        String today = getTodayDateString();

        String uuid1 = UUID.randomUUID().toString();
        ContentValues cv1 = new ContentValues();
        cv1.put(COL_HABIT_UUID, uuid1);
        cv1.put(COL_HABIT_TITLE, "Hydrate (2 Liters)");
        cv1.put(COL_HABIT_DESC, "Drink water with morning routine");
        cv1.put(COL_HABIT_CATEGORY, "Health");
        cv1.put(COL_HABIT_FREQ, 7);
        cv1.put(COL_HABIT_CREATED_AT, now - 86400000L * 7);
        cv1.put(COL_HABIT_UPDATED_AT, now);
        cv1.put(COL_HABIT_SYNCED, 0);
        db.insert(TABLE_HABITS, null, cv1);

        String uuid2 = UUID.randomUUID().toString();
        ContentValues cv2 = new ContentValues();
        cv2.put(COL_HABIT_UUID, uuid2);
        cv2.put(COL_HABIT_TITLE, "Deep Focus Reading");
        cv2.put(COL_HABIT_DESC, "20 minutes reading before sleep");
        cv2.put(COL_HABIT_CATEGORY, "Mind");
        cv2.put(COL_HABIT_FREQ, 7);
        cv2.put(COL_HABIT_CREATED_AT, now - 86400000L * 5);
        cv2.put(COL_HABIT_UPDATED_AT, now);
        cv2.put(COL_HABIT_SYNCED, 0);
        db.insert(TABLE_HABITS, null, cv2);

        // Pre-populate some historical logs for realistic momentum
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (int i = 1; i <= 4; i++) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String pastDate = sdf.format(cal.getTime());

            ContentValues logCv = new ContentValues();
            logCv.put(COL_LOG_HABIT_UUID, uuid1);
            logCv.put(COL_LOG_DATE, pastDate);
            logCv.put(COL_LOG_COMPLETED, 1);
            logCv.put(COL_LOG_UPDATED_AT, now);
            logCv.put(COL_LOG_SYNCED, 0);
            db.insert(TABLE_HABIT_LOGS, null, logCv);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HABIT_LOGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HABITS);
        onCreate(db);
    }

    public static String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public long addHabit(String title, String description, String category, int freq) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put(COL_HABIT_UUID, UUID.randomUUID().toString());
        cv.put(COL_HABIT_TITLE, title);
        cv.put(COL_HABIT_DESC, description != null ? description : "");
        cv.put(COL_HABIT_CATEGORY, category);
        cv.put(COL_HABIT_FREQ, freq > 0 ? freq : 7);
        cv.put(COL_HABIT_CREATED_AT, now);
        cv.put(COL_HABIT_UPDATED_AT, now);
        cv.put(COL_HABIT_SYNCED, 0);
        return db.insert(TABLE_HABITS, null, cv);
    }

    public boolean updateHabit(String uuid, String title, String description, String category, int freq) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put(COL_HABIT_TITLE, title);
        cv.put(COL_HABIT_DESC, description != null ? description : "");
        cv.put(COL_HABIT_CATEGORY, category);
        cv.put(COL_HABIT_FREQ, freq > 0 ? freq : 7);
        cv.put(COL_HABIT_UPDATED_AT, now);
        cv.put(COL_HABIT_SYNCED, 0);
        return db.update(TABLE_HABITS, cv, COL_HABIT_UUID + " = ?", new String[]{uuid}) > 0;
    }

    public boolean deleteHabit(String uuid) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_HABIT_LOGS, COL_LOG_HABIT_UUID + " = ?", new String[]{uuid});
        return db.delete(TABLE_HABITS, COL_HABIT_UUID + " = ?", new String[]{uuid}) > 0;
    }

    public boolean toggleCheckIn(String habitUuid, String dateStr) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();

        boolean currentlyDone = false;
        Cursor c = db.query(TABLE_HABIT_LOGS, new String[]{COL_LOG_COMPLETED},
                COL_LOG_HABIT_UUID + " = ? AND " + COL_LOG_DATE + " = ?",
                new String[]{habitUuid, dateStr}, null, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                currentlyDone = c.getInt(0) == 1;
            }
            c.close();
        }

        boolean newState = !currentlyDone;
        ContentValues cv = new ContentValues();
        cv.put(COL_LOG_HABIT_UUID, habitUuid);
        cv.put(COL_LOG_DATE, dateStr);
        cv.put(COL_LOG_COMPLETED, newState ? 1 : 0);
        cv.put(COL_LOG_UPDATED_AT, now);
        cv.put(COL_LOG_SYNCED, 0);
        db.insertWithOnConflict(TABLE_HABIT_LOGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);

        // Also update habit's updated_at
        ContentValues habitCv = new ContentValues();
        habitCv.put(COL_HABIT_UPDATED_AT, now);
        habitCv.put(COL_HABIT_SYNCED, 0);
        db.update(TABLE_HABITS, habitCv, COL_HABIT_UUID + " = ?", new String[]{habitUuid});

        return newState;
    }

    public List<HabitItem> getAllHabits(String categoryFilter) {
        List<HabitItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = null;
        String[] selectionArgs = null;
        if (categoryFilter != null && !categoryFilter.equalsIgnoreCase("All")) {
            selection = COL_HABIT_CATEGORY + " = ?";
            selectionArgs = new String[]{categoryFilter};
        }

        Cursor c = db.query(TABLE_HABITS, null, selection, selectionArgs, null, null, COL_HABIT_ID + " ASC");
        if (c != null && c.moveToFirst()) {
            do {
                HabitItem item = new HabitItem();
                item.id = c.getLong(c.getColumnIndexOrThrow(COL_HABIT_ID));
                item.uuid = c.getString(c.getColumnIndexOrThrow(COL_HABIT_UUID));
                item.title = c.getString(c.getColumnIndexOrThrow(COL_HABIT_TITLE));
                item.description = c.getString(c.getColumnIndexOrThrow(COL_HABIT_DESC));
                item.category = c.getString(c.getColumnIndexOrThrow(COL_HABIT_CATEGORY));
                item.targetDaysPerWeek = c.getInt(c.getColumnIndexOrThrow(COL_HABIT_FREQ));
                item.createdAt = c.getLong(c.getColumnIndexOrThrow(COL_HABIT_CREATED_AT));
                item.updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_HABIT_UPDATED_AT));

                populateTelemetry(db, item);
                list.add(item);
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    public HabitItem getHabitByUuid(String uuid) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_HABITS, null, COL_HABIT_UUID + " = ?", new String[]{uuid}, null, null, null);
        if (c != null && c.moveToFirst()) {
            HabitItem item = new HabitItem();
            item.id = c.getLong(c.getColumnIndexOrThrow(COL_HABIT_ID));
            item.uuid = c.getString(c.getColumnIndexOrThrow(COL_HABIT_UUID));
            item.title = c.getString(c.getColumnIndexOrThrow(COL_HABIT_TITLE));
            item.description = c.getString(c.getColumnIndexOrThrow(COL_HABIT_DESC));
            item.category = c.getString(c.getColumnIndexOrThrow(COL_HABIT_CATEGORY));
            item.targetDaysPerWeek = c.getInt(c.getColumnIndexOrThrow(COL_HABIT_FREQ));
            item.createdAt = c.getLong(c.getColumnIndexOrThrow(COL_HABIT_CREATED_AT));
            item.updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_HABIT_UPDATED_AT));

            populateTelemetry(db, item);
            c.close();
            return item;
        }
        if (c != null) c.close();
        return null;
    }

    private void populateTelemetry(SQLiteDatabase db, HabitItem item) {
        String today = getTodayDateString();

        // Check if completed today
        Cursor todayCursor = db.query(TABLE_HABIT_LOGS, new String[]{COL_LOG_COMPLETED},
                COL_LOG_HABIT_UUID + " = ? AND " + COL_LOG_DATE + " = ?",
                new String[]{item.uuid, today}, null, null, null);
        if (todayCursor != null) {
            if (todayCursor.moveToFirst()) {
                item.isCompletedToday = todayCursor.getInt(0) == 1;
            }
            todayCursor.close();
        }

        // Get past 60 days completions for Loop Habit strength math & streaks
        Set<String> completedDates = new HashSet<>();
        Cursor logCursor = db.query(TABLE_HABIT_LOGS, new String[]{COL_LOG_DATE, COL_LOG_COMPLETED},
                COL_LOG_HABIT_UUID + " = ?", new String[]{item.uuid}, null, null, null);
        if (logCursor != null && logCursor.moveToFirst()) {
            do {
                if (logCursor.getInt(1) == 1) {
                    completedDates.add(logCursor.getString(0));
                }
            } while (logCursor.moveToNext());
            logCursor.close();
        }
        item.totalCompletions = completedDates.size();

        // Build chronological history for past 60 days
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        List<Boolean> chronological = new ArrayList<>(60);
        List<Boolean> reversed = new ArrayList<>(60);

        for (int i = 59; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String dateStr = sdf.format(cal.getTime());
            boolean done = completedDates.contains(dateStr);
            chronological.add(done);

            if (i < 30) {
                // 30-day mini history (index 29 is today, index 0 is 29 days ago)
                item.thirtyDayHistory[29 - i] = done;
            }
        }

        for (int i = 0; i < 60; i++) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String dateStr = sdf.format(cal.getTime());
            reversed.add(completedDates.contains(dateStr));
        }

        item.strengthScore = HabitMath.computeHabitStrength(chronological);
        item.currentStreak = HabitMath.computeCurrentStreak(reversed);
        item.bestStreak = Math.max(item.currentStreak, HabitMath.computeBestStreak(chronological));
    }

    public List<Boolean> getChronologicalHistory(String habitUuid, int days) {
        SQLiteDatabase db = getReadableDatabase();
        Set<String> completedDates = new HashSet<>();
        Cursor c = db.query(TABLE_HABIT_LOGS, new String[]{COL_LOG_DATE, COL_LOG_COMPLETED},
                COL_LOG_HABIT_UUID + " = ?", new String[]{habitUuid}, null, null, null);
        if (c != null && c.moveToFirst()) {
            do {
                if (c.getInt(1) == 1) completedDates.add(c.getString(0));
            } while (c.moveToNext());
            c.close();
        }

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        List<Boolean> list = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            list.add(completedDates.contains(sdf.format(cal.getTime())));
        }
        return list;
    }

    // -------------------------------------------------------------
    // Cloud Sync Helpers
    // -------------------------------------------------------------

    public List<HabitItem> getUnsyncedHabits() {
        List<HabitItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_HABITS, null, COL_HABIT_SYNCED + " = 0", null, null, null, null);
        if (c != null && c.moveToFirst()) {
            do {
                HabitItem item = new HabitItem();
                item.uuid = c.getString(c.getColumnIndexOrThrow(COL_HABIT_UUID));
                item.title = c.getString(c.getColumnIndexOrThrow(COL_HABIT_TITLE));
                item.description = c.getString(c.getColumnIndexOrThrow(COL_HABIT_DESC));
                item.category = c.getString(c.getColumnIndexOrThrow(COL_HABIT_CATEGORY));
                item.targetDaysPerWeek = c.getInt(c.getColumnIndexOrThrow(COL_HABIT_FREQ));
                item.createdAt = c.getLong(c.getColumnIndexOrThrow(COL_HABIT_CREATED_AT));
                item.updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_HABIT_UPDATED_AT));
                list.add(item);
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    public List<HabitLogEntry> getUnsyncedLogs() {
        List<HabitLogEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_HABIT_LOGS, null, COL_LOG_SYNCED + " = 0", null, null, null, null);
        if (c != null && c.moveToFirst()) {
            do {
                HabitLogEntry entry = new HabitLogEntry();
                entry.habitUuid = c.getString(c.getColumnIndexOrThrow(COL_LOG_HABIT_UUID));
                entry.dateStr = c.getString(c.getColumnIndexOrThrow(COL_LOG_DATE));
                entry.completed = c.getInt(c.getColumnIndexOrThrow(COL_LOG_COMPLETED)) == 1;
                entry.updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_LOG_UPDATED_AT));
                list.add(entry);
            } while (c.moveToNext());
            c.close();
        }
        return list;
    }

    public void markHabitSynced(String uuid) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_HABIT_SYNCED, 1);
        db.update(TABLE_HABITS, cv, COL_HABIT_UUID + " = ?", new String[]{uuid});
    }

    public void markLogSynced(String habitUuid, String dateStr) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LOG_SYNCED, 1);
        db.update(TABLE_HABIT_LOGS, cv, COL_LOG_HABIT_UUID + " = ? AND " + COL_LOG_DATE + " = ?",
                new String[]{habitUuid, dateStr});
    }

    public void mergeRemoteHabit(String uuid, String title, String description, String category, int freq, long createdAt, long updatedAt) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(TABLE_HABITS, new String[]{COL_HABIT_UPDATED_AT}, COL_HABIT_UUID + " = ?", new String[]{uuid}, null, null, null);
        if (c != null && c.moveToFirst()) {
            long localUpdated = c.getLong(0);
            c.close();
            if (updatedAt > localUpdated) {
                ContentValues cv = new ContentValues();
                cv.put(COL_HABIT_TITLE, title);
                cv.put(COL_HABIT_DESC, description != null ? description : "");
                cv.put(COL_HABIT_CATEGORY, category);
                cv.put(COL_HABIT_FREQ, freq);
                cv.put(COL_HABIT_UPDATED_AT, updatedAt);
                cv.put(COL_HABIT_SYNCED, 1);
                db.update(TABLE_HABITS, cv, COL_HABIT_UUID + " = ?", new String[]{uuid});
            }
        } else {
            if (c != null) c.close();
            ContentValues cv = new ContentValues();
            cv.put(COL_HABIT_UUID, uuid);
            cv.put(COL_HABIT_TITLE, title);
            cv.put(COL_HABIT_DESC, description != null ? description : "");
            cv.put(COL_HABIT_CATEGORY, category);
            cv.put(COL_HABIT_FREQ, freq);
            cv.put(COL_HABIT_CREATED_AT, createdAt);
            cv.put(COL_HABIT_UPDATED_AT, updatedAt);
            cv.put(COL_HABIT_SYNCED, 1);
            db.insert(TABLE_HABITS, null, cv);
        }
    }

    public void mergeRemoteLog(String habitUuid, String dateStr, boolean completed, long updatedAt) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(TABLE_HABIT_LOGS, new String[]{COL_LOG_UPDATED_AT},
                COL_LOG_HABIT_UUID + " = ? AND " + COL_LOG_DATE + " = ?",
                new String[]{habitUuid, dateStr}, null, null, null);
        if (c != null && c.moveToFirst()) {
            long localUpdated = c.getLong(0);
            c.close();
            if (updatedAt > localUpdated) {
                ContentValues cv = new ContentValues();
                cv.put(COL_LOG_COMPLETED, completed ? 1 : 0);
                cv.put(COL_LOG_UPDATED_AT, updatedAt);
                cv.put(COL_LOG_SYNCED, 1);
                db.update(TABLE_HABIT_LOGS, cv, COL_LOG_HABIT_UUID + " = ? AND " + COL_LOG_DATE + " = ?",
                        new String[]{habitUuid, dateStr});
            }
        } else {
            if (c != null) c.close();
            ContentValues cv = new ContentValues();
            cv.put(COL_LOG_HABIT_UUID, habitUuid);
            cv.put(COL_LOG_DATE, dateStr);
            cv.put(COL_LOG_COMPLETED, completed ? 1 : 0);
            cv.put(COL_LOG_UPDATED_AT, updatedAt);
            cv.put(COL_LOG_SYNCED, 1);
            db.insert(TABLE_HABIT_LOGS, null, cv);
        }
    }
}
