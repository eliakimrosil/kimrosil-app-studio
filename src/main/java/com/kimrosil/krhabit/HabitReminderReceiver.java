package com.kimrosil.krhabit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.List;

/**
 * BroadcastReceiver triggered by AlarmManager to post daily habit reminders,
 * and to process direct check-ins from the notification action.
 */
public class HabitReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_TEST_NOTIFICATION = "com.kimrosil.krhabit.ACTION_TEST_NOTIFICATION";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (context == null || intent == null) return;

        final String action = intent.getAction();
        final String habitUuid = intent.getStringExtra(ReminderManager.EXTRA_HABIT_UUID);
        final DatabaseHelper dbHelper = new DatabaseHelper(context);

        if (ACTION_TEST_NOTIFICATION.equals(action)) {
            DatabaseHelper.HabitItem testHabit = null;
            if (habitUuid != null && !habitUuid.trim().isEmpty()) {
                testHabit = dbHelper.getHabitByUuid(habitUuid);
            }
            if (testHabit == null) {
                List<DatabaseHelper.HabitItem> habits = dbHelper.getAllHabits(null);
                if (habits != null && !habits.isEmpty()) {
                    testHabit = habits.get(0);
                } else {
                    testHabit = new DatabaseHelper.HabitItem();
                    testHabit.uuid = "demo-preview-uuid";
                    testHabit.title = "Daily Focus Ritual";
                    testHabit.description = "Time to complete your habit today! Keep your momentum going.";
                    testHabit.currentStreak = 7;
                    testHabit.reminderEnabled = true;
                }
            }
            showNotification(context, testHabit);
            return;
        }

        if (habitUuid == null || habitUuid.trim().isEmpty()) return;

        if (ReminderManager.ACTION_CHECK_IN.equals(action)) {
            // User tapped "✓ Check In" button right inside notification
            dbHelper.toggleCheckIn(habitUuid, DatabaseHelper.getTodayDateString());

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(ReminderManager.getRequestCode(habitUuid));
            }

            DatabaseHelper.HabitItem habit = dbHelper.getHabitByUuid(habitUuid);
            final String habitTitle = habit != null ? habit.title : "Habit";

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "✓ " + habitTitle + " completed!", Toast.LENGTH_SHORT).show();
                }
            });

            // Trigger cloud synchronization in background
            FirebaseSyncEngine syncEngine = new FirebaseSyncEngine(context, dbHelper);
            syncEngine.syncWithFirestore(null);

        } else if (ReminderManager.ACTION_REMINDER.equals(action)) {
            // Alarm fired
            DatabaseHelper.HabitItem habit = dbHelper.getHabitByUuid(habitUuid);
            if (habit == null || !habit.reminderEnabled) return;

            // If already completed today, skip reminder
            if (!habit.isCompletedToday) {
                showNotification(context, habit);
            }

            // Reschedule for next day
            ReminderManager.scheduleReminder(context, habit);
        }
    }

    public static void showNotification(Context context, DatabaseHelper.HabitItem habit) {
        if (context == null || habit == null) return;

        ReminderManager.createNotificationChannel(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        int reqCode = ReminderManager.getRequestCode(habit.uuid);

        // Content Intent: Open MainActivity
        Intent contentIntent = new Intent(context, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(
                context,
                reqCode,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action Intent: Check In directly from notification
        Intent checkInIntent = new Intent(context, HabitReminderReceiver.class);
        checkInIntent.setAction(ReminderManager.ACTION_CHECK_IN);
        checkInIntent.putExtra(ReminderManager.EXTRA_HABIT_UUID, habit.uuid);
        PendingIntent checkInPi = PendingIntent.getBroadcast(
                context,
                reqCode + 1000000,
                checkInIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String message = (habit.description != null && !habit.description.trim().isEmpty())
                ? habit.description
                : "Time to complete your habit today! Keep your momentum going.";

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, ReminderManager.CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setContentTitle(habit.title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification_habit)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setColor(Color.parseColor("#059669"));

        // BigTextStyle for expandable description
        Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle()
                .bigText(message)
                .setBigContentTitle(habit.title);
        if (habit.currentStreak > 0) {
            bigTextStyle.setSummaryText("🔥 " + habit.currentStreak + "d streak");
        } else {
            bigTextStyle.setSummaryText("KR Habit");
        }
        builder.setStyle(bigTextStyle);

        // Subtext / Category telemetry
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (habit.currentStreak > 0) {
                builder.setSubText("🔥 " + habit.currentStreak + "d streak");
            } else if (habit.category != null && !habit.category.trim().isEmpty()) {
                builder.setSubText(habit.category);
            }
        }

        // Action Button: Direct check-in with custom checkmark icon
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Icon checkIcon = Icon.createWithResource(context, R.drawable.ic_notification_check);
            builder.addAction(new Notification.Action.Builder(
                    checkIcon,
                    "✓ Check In",
                    checkInPi
            ).build());
        } else {
            builder.addAction(new Notification.Action.Builder(
                    R.drawable.ic_notification_check,
                    "✓ Check In",
                    checkInPi
            ).build());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setPriority(Notification.PRIORITY_HIGH);
        }

        nm.notify(reqCode, builder.build());
    }
}
