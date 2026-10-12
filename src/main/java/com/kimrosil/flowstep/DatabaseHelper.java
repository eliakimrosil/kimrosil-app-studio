package com.kimrosil.flowstep;

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
import java.util.UUID;

/**
 * 100% Offline-First Pure Android SQLite Persistence Helper for FlowStep.
 * Zero external ORM/Room overhead, thread-safe transactional updates.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "flowstep.db";
    private static final int DATABASE_VERSION = 1;

    // Table: Goals
    public static final String TABLE_GOALS = "goals";
    public static final String COL_GOAL_ID = "id";
    public static final String COL_GOAL_TITLE = "title";
    public static final String COL_GOAL_STATUS = "status"; // ACTIVE, COMPLETED, ABANDONED
    public static final String COL_GOAL_CREATED_AT = "created_at";
    public static final String COL_GOAL_COMPLETED_AT = "completed_at";

    // Table: MicroSteps
    public static final String TABLE_MICRO_STEPS = "micro_steps";
    public static final String COL_STEP_ID = "id";
    public static final String COL_STEP_GOAL_ID = "goal_id";
    public static final String COL_STEP_ORDER = "step_order";
    public static final String COL_STEP_INSTRUCTION = "instruction";
    public static final String COL_STEP_ACTION_VERB = "action_verb";
    public static final String COL_STEP_ESTIMATED_MINUTES = "estimated_minutes";
    public static final String COL_STEP_IS_COMPLETED = "is_completed";
    public static final String COL_STEP_COMPLETED_AT = "completed_at";

    // Table: Stray Thoughts
    public static final String TABLE_STRAY_THOUGHTS = "stray_thoughts";
    public static final String COL_THOUGHT_ID = "id";
    public static final String COL_THOUGHT_CONTENT = "content";
    public static final String COL_THOUGHT_CREATED_AT = "created_at";
    public static final String COL_THOUGHT_IS_RESOLVED = "is_resolved";

    // Table: Momentum Logs
    public static final String TABLE_MOMENTUM = "momentum_logs";
    public static final String COL_LOG_DATE = "log_date"; // YYYY-MM-DD
    public static final String COL_LOG_COMPLETED_STEPS = "completed_steps";
    public static final String COL_LOG_COMPLETED_GOALS = "completed_goals";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_GOALS + " (" +
                COL_GOAL_ID + " TEXT PRIMARY KEY, " +
                COL_GOAL_TITLE + " TEXT NOT NULL, " +
                COL_GOAL_STATUS + " TEXT NOT NULL, " +
                COL_GOAL_CREATED_AT + " INTEGER NOT NULL, " +
                COL_GOAL_COMPLETED_AT + " INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_MICRO_STEPS + " (" +
                COL_STEP_ID + " TEXT PRIMARY KEY, " +
                COL_STEP_GOAL_ID + " TEXT NOT NULL, " +
                COL_STEP_ORDER + " INTEGER NOT NULL, " +
                COL_STEP_INSTRUCTION + " TEXT NOT NULL, " +
                COL_STEP_ACTION_VERB + " TEXT, " +
                COL_STEP_ESTIMATED_MINUTES + " INTEGER DEFAULT 2, " +
                COL_STEP_IS_COMPLETED + " INTEGER NOT NULL DEFAULT 0, " +
                COL_STEP_COMPLETED_AT + " INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_STRAY_THOUGHTS + " (" +
                COL_THOUGHT_ID + " TEXT PRIMARY KEY, " +
                COL_THOUGHT_CONTENT + " TEXT NOT NULL, " +
                COL_THOUGHT_CREATED_AT + " INTEGER NOT NULL, " +
                COL_THOUGHT_IS_RESOLVED + " INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_MOMENTUM + " (" +
                COL_LOG_DATE + " TEXT PRIMARY KEY, " +
                COL_LOG_COMPLETED_STEPS + " INTEGER NOT NULL DEFAULT 0, " +
                COL_LOG_COMPLETED_GOALS + " INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_GOALS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MICRO_STEPS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STRAY_THOUGHTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MOMENTUM);
        onCreate(db);
    }

    // --- GOAL OPERATIONS ---

    public String createGoalWithSteps(String title, List<StepBlueprint> steps) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        String goalId = UUID.randomUUID().toString();
        try {
            // Deactivate any currently active goals to keep strict Tunnel Vision
            ContentValues pauseCv = new ContentValues();
            pauseCv.put(COL_GOAL_STATUS, "PAUSED");
            db.update(TABLE_GOALS, pauseCv, COL_GOAL_STATUS + " = ?", new String[]{"ACTIVE"});

            ContentValues goalCv = new ContentValues();
            goalCv.put(COL_GOAL_ID, goalId);
            goalCv.put(COL_GOAL_TITLE, title);
            goalCv.put(COL_GOAL_STATUS, "ACTIVE");
            goalCv.put(COL_GOAL_CREATED_AT, System.currentTimeMillis());
            db.insert(TABLE_GOALS, null, goalCv);

            int order = 1;
            for (StepBlueprint blueprint : steps) {
                ContentValues stepCv = new ContentValues();
                stepCv.put(COL_STEP_ID, UUID.randomUUID().toString());
                stepCv.put(COL_STEP_GOAL_ID, goalId);
                stepCv.put(COL_STEP_ORDER, order++);
                stepCv.put(COL_STEP_INSTRUCTION, blueprint.instruction);
                stepCv.put(COL_STEP_ACTION_VERB, blueprint.actionVerb);
                stepCv.put(COL_STEP_ESTIMATED_MINUTES, blueprint.minutes);
                stepCv.put(COL_STEP_IS_COMPLETED, 0);
                db.insert(TABLE_MICRO_STEPS, null, stepCv);
            }
            db.setTransactionSuccessful();
            return goalId;
        } finally {
            db.endTransaction();
        }
    }

    public Goal getActiveGoal() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_GOALS + " WHERE " + COL_GOAL_STATUS + " = ? ORDER BY " + COL_GOAL_CREATED_AT + " DESC LIMIT 1", new String[]{"ACTIVE"});
        try {
            if (c.moveToFirst()) {
                return new Goal(
                        c.getString(c.getColumnIndexOrThrow(COL_GOAL_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_GOAL_TITLE)),
                        c.getString(c.getColumnIndexOrThrow(COL_GOAL_STATUS)),
                        c.getLong(c.getColumnIndexOrThrow(COL_GOAL_CREATED_AT)),
                        c.isNull(c.getColumnIndexOrThrow(COL_GOAL_COMPLETED_AT)) ? null : c.getLong(c.getColumnIndexOrThrow(COL_GOAL_COMPLETED_AT))
                );
            }
        } finally {
            c.close();
        }
        return null;
    }

    public List<MicroStep> getStepsForGoal(String goalId) {
        List<MicroStep> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_MICRO_STEPS + " WHERE " + COL_STEP_GOAL_ID + " = ? ORDER BY " + COL_STEP_ORDER + " ASC", new String[]{goalId});
        try {
            while (c.moveToNext()) {
                list.add(new MicroStep(
                        c.getString(c.getColumnIndexOrThrow(COL_STEP_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_STEP_GOAL_ID)),
                        c.getInt(c.getColumnIndexOrThrow(COL_STEP_ORDER)),
                        c.getString(c.getColumnIndexOrThrow(COL_STEP_INSTRUCTION)),
                        c.getString(c.getColumnIndexOrThrow(COL_STEP_ACTION_VERB)),
                        c.getInt(c.getColumnIndexOrThrow(COL_STEP_ESTIMATED_MINUTES)),
                        c.getInt(c.getColumnIndexOrThrow(COL_STEP_IS_COMPLETED)) == 1,
                        c.isNull(c.getColumnIndexOrThrow(COL_STEP_COMPLETED_AT)) ? null : c.getLong(c.getColumnIndexOrThrow(COL_STEP_COMPLETED_AT))
                ));
            }
        } finally {
            c.close();
        }
        return list;
    }

    public MicroStep getNextActiveStep(String goalId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_MICRO_STEPS + " WHERE " + COL_STEP_GOAL_ID + " = ? AND " + COL_STEP_IS_COMPLETED + " = 0 ORDER BY " + COL_STEP_ORDER + " ASC LIMIT 1", new String[]{goalId});
        try {
            if (c.moveToFirst()) {
                return new MicroStep(
                        c.getString(c.getColumnIndexOrThrow(COL_STEP_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_STEP_GOAL_ID)),
                        c.getInt(c.getColumnIndexOrThrow(COL_STEP_ORDER)),
                        c.getString(c.getColumnIndexOrThrow(COL_STEP_INSTRUCTION)),
                        c.getString(c.getColumnIndexOrThrow(COL_STEP_ACTION_VERB)),
                        c.getInt(c.getColumnIndexOrThrow(COL_STEP_ESTIMATED_MINUTES)),
                        false,
                        null
                );
            }
        } finally {
            c.close();
        }
        return null;
    }

    public boolean completeStep(String stepId, String goalId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            ContentValues cv = new ContentValues();
            cv.put(COL_STEP_IS_COMPLETED, 1);
            cv.put(COL_STEP_COMPLETED_AT, now);
            db.update(TABLE_MICRO_STEPS, cv, COL_STEP_ID + " = ?", new String[]{stepId});

            // Increment daily momentum
            recordMomentumWin(1, 0);

            // Check if all steps in this goal are completed
            Cursor remaining = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MICRO_STEPS + " WHERE " + COL_STEP_GOAL_ID + " = ? AND " + COL_STEP_IS_COMPLETED + " = 0", new String[]{goalId});
            boolean goalFinished = false;
            if (remaining.moveToFirst() && remaining.getInt(0) == 0) {
                ContentValues goalCv = new ContentValues();
                goalCv.put(COL_GOAL_STATUS, "COMPLETED");
                goalCv.put(COL_GOAL_COMPLETED_AT, now);
                db.update(TABLE_GOALS, goalCv, COL_GOAL_ID + " = ?", new String[]{goalId});
                recordMomentumWin(0, 1);
                goalFinished = true;
            }
            remaining.close();

            db.setTransactionSuccessful();
            return goalFinished;
        } finally {
            db.endTransaction();
        }
    }

    public void subdivideStep(String stepId, String goalId, StepBlueprint sub1, StepBlueprint sub2) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // Find current step's order
            Cursor c = db.rawQuery("SELECT " + COL_STEP_ORDER + " FROM " + TABLE_MICRO_STEPS + " WHERE " + COL_STEP_ID + " = ?", new String[]{stepId});
            if (c.moveToFirst()) {
                int currentOrder = c.getInt(0);
                c.close();

                // Shift all subsequent steps forward by 1
                db.execSQL("UPDATE " + TABLE_MICRO_STEPS + " SET " + COL_STEP_ORDER + " = " + COL_STEP_ORDER + " + 1 WHERE " + COL_STEP_GOAL_ID + " = ? AND " + COL_STEP_ORDER + " > ?", new Object[]{goalId, currentOrder});

                // Replace current step with sub1
                ContentValues cv1 = new ContentValues();
                cv1.put(COL_STEP_INSTRUCTION, sub1.instruction);
                cv1.put(COL_STEP_ACTION_VERB, sub1.actionVerb);
                cv1.put(COL_STEP_ESTIMATED_MINUTES, sub1.minutes);
                db.update(TABLE_MICRO_STEPS, cv1, COL_STEP_ID + " = ?", new String[]{stepId});

                // Insert sub2 at currentOrder + 1
                ContentValues cv2 = new ContentValues();
                cv2.put(COL_STEP_ID, UUID.randomUUID().toString());
                cv2.put(COL_STEP_GOAL_ID, goalId);
                cv2.put(COL_STEP_ORDER, currentOrder + 1);
                cv2.put(COL_STEP_INSTRUCTION, sub2.instruction);
                cv2.put(COL_STEP_ACTION_VERB, sub2.actionVerb);
                cv2.put(COL_STEP_ESTIMATED_MINUTES, sub2.minutes);
                cv2.put(COL_STEP_IS_COMPLETED, 0);
                db.insert(TABLE_MICRO_STEPS, null, cv2);
            } else {
                c.close();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void pauseGoal(String goalId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_GOAL_STATUS, "PAUSED");
        db.update(TABLE_GOALS, cv, COL_GOAL_ID + " = ?", new String[]{goalId});
    }

    // --- STRAY THOUGHT BUFFER ---

    public void addStrayThought(String content) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_THOUGHT_ID, UUID.randomUUID().toString());
        cv.put(COL_THOUGHT_CONTENT, content.trim());
        cv.put(COL_THOUGHT_CREATED_AT, System.currentTimeMillis());
        cv.put(COL_THOUGHT_IS_RESOLVED, 0);
        db.insert(TABLE_STRAY_THOUGHTS, null, cv);
    }

    public List<StrayThought> getStrayThoughts() {
        List<StrayThought> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_STRAY_THOUGHTS + " ORDER BY " + COL_THOUGHT_CREATED_AT + " DESC", null);
        try {
            while (c.moveToNext()) {
                list.add(new StrayThought(
                        c.getString(c.getColumnIndexOrThrow(COL_THOUGHT_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_THOUGHT_CONTENT)),
                        c.getLong(c.getColumnIndexOrThrow(COL_THOUGHT_CREATED_AT)),
                        c.getInt(c.getColumnIndexOrThrow(COL_THOUGHT_IS_RESOLVED)) == 1
                ));
            }
        } finally {
            c.close();
        }
        return list;
    }

    public void toggleThoughtResolved(String id, boolean resolved) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_THOUGHT_IS_RESOLVED, resolved ? 1 : 0);
        db.update(TABLE_STRAY_THOUGHTS, cv, COL_THOUGHT_ID + " = ?", new String[]{id});
    }

    public void deleteStrayThought(String id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_STRAY_THOUGHTS, COL_THOUGHT_ID + " = ?", new String[]{id});
    }

    // --- MOMENTUM & LIFETIME WINS ---

    private void recordMomentumWin(int steps, int goals) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT INTO " + TABLE_MOMENTUM + " (" + COL_LOG_DATE + ", " + COL_LOG_COMPLETED_STEPS + ", " + COL_LOG_COMPLETED_GOALS + ") " +
                "VALUES ('" + today + "', " + steps + ", " + goals + ") " +
                "ON CONFLICT(" + COL_LOG_DATE + ") DO UPDATE SET " +
                COL_LOG_COMPLETED_STEPS + " = " + COL_LOG_COMPLETED_STEPS + " + " + steps + ", " +
                COL_LOG_COMPLETED_GOALS + " = " + COL_LOG_COMPLETED_GOALS + " + " + goals);
    }

    public LifetimeStats getLifetimeStats() {
        SQLiteDatabase db = getReadableDatabase();
        int totalSteps = 0;
        int totalGoals = 0;
        int totalThoughts = 0;

        Cursor c1 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MICRO_STEPS + " WHERE " + COL_STEP_IS_COMPLETED + " = 1", null);
        if (c1.moveToFirst()) totalSteps = c1.getInt(0);
        c1.close();

        Cursor c2 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_GOALS + " WHERE " + COL_GOAL_STATUS + " = 'COMPLETED'", null);
        if (c2.moveToFirst()) totalGoals = c2.getInt(0);
        c2.close();

        Cursor c3 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_STRAY_THOUGHTS, null);
        if (c3.moveToFirst()) totalThoughts = c3.getInt(0);
        c3.close();

        return new LifetimeStats(totalSteps, totalGoals, totalThoughts);
    }

    // --- DATA MODELS ---

    public static class StepBlueprint {
        public final String actionVerb;
        public final String instruction;
        public final int minutes;

        public StepBlueprint(String actionVerb, String instruction, int minutes) {
            this.actionVerb = actionVerb;
            this.instruction = instruction;
            this.minutes = minutes;
        }
    }

    public static class Goal {
        public final String id;
        public final String title;
        public final String status;
        public final long createdAt;
        public final Long completedAt;

        public Goal(String id, String title, String status, long createdAt, Long completedAt) {
            this.id = id;
            this.title = title;
            this.status = status;
            this.createdAt = createdAt;
            this.completedAt = completedAt;
        }
    }

    public static class MicroStep {
        public final String id;
        public final String goalId;
        public final int stepOrder;
        public final String instruction;
        public final String actionVerb;
        public final int estimatedMinutes;
        public final boolean isCompleted;
        public final Long completedAt;

        public MicroStep(String id, String goalId, int stepOrder, String instruction, String actionVerb, int estimatedMinutes, boolean isCompleted, Long completedAt) {
            this.id = id;
            this.goalId = goalId;
            this.stepOrder = stepOrder;
            this.instruction = instruction;
            this.actionVerb = actionVerb;
            this.estimatedMinutes = estimatedMinutes;
            this.isCompleted = isCompleted;
            this.completedAt = completedAt;
        }
    }

    public static class StrayThought {
        public final String id;
        public final String content;
        public final long createdAt;
        public final boolean isResolved;

        public StrayThought(String id, String content, long createdAt, boolean isResolved) {
            this.id = id;
            this.content = content;
            this.createdAt = createdAt;
            this.isResolved = isResolved;
        }
    }

    public static class LifetimeStats {
        public final int totalStepsCompleted;
        public final int totalGoalsCompleted;
        public final int totalThoughtsParked;

        public LifetimeStats(int steps, int goals, int thoughts) {
            this.totalStepsCompleted = steps;
            this.totalGoalsCompleted = goals;
            this.totalThoughtsParked = thoughts;
        }
    }
}
