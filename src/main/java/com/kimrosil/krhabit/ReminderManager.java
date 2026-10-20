package com.kimrosil.krhabit;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Handles scheduling, canceling, and managing daily habit reminder alarms
 * and notifications, inspired by Loop Habit Tracker.
 */
public class ReminderManager {

    public static final String CHANNEL_ID = "kr_habit_reminders";
    public static final String CHANNEL_NAME = "Habit Reminders";
    public static final String ACTION_REMINDER = "com.kimrosil.krhabit.ACTION_REMINDER";
    public static final String ACTION_CHECK_IN = "com.kimrosil.krhabit.ACTION_CHECK_IN";
    public static final String EXTRA_HABIT_UUID = "habit_uuid";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Daily reminders to complete your positive habits");
                channel.enableVibration(true);
                channel.setShowBadge(true);
                nm.createNotificationChannel(channel);
            }
        }
    }

    public static void scheduleReminder(Context context, DatabaseHelper.HabitItem habit) {
        if (context == null || habit == null || habit.uuid == null) return;

        createNotificationChannel(context);

        if (!habit.reminderEnabled) {
            cancelReminder(context, habit.uuid);
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, HabitReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        intent.putExtra(EXTRA_HABIT_UUID, habit.uuid);

        int requestCode = getRequestCode(habit.uuid);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, habit.reminderHour);
        target.set(Calendar.MINUTE, habit.reminderMinute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        // If target time today has already elapsed, schedule for tomorrow
        if (target.getTimeInMillis() <= now.getTimeInMillis()) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        long triggerMs = target.getTimeInMillis();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            }
        } catch (SecurityException ignored) {
            // Fallback for non-exact alarm if permission not held
            am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        }
    }

    public static void cancelReminder(Context context, String habitUuid) {
        if (context == null || habitUuid == null) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, HabitReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        int requestCode = getRequestCode(habitUuid);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    public static void rescheduleAllReminders(Context context) {
        if (context == null) return;
        DatabaseHelper db = new DatabaseHelper(context);
        List<DatabaseHelper.HabitItem> habits = db.getAllHabits(null);
        for (DatabaseHelper.HabitItem habit : habits) {
            if (habit.reminderEnabled) {
                scheduleReminder(context, habit);
            } else {
                cancelReminder(context, habit.uuid);
            }
        }
    }

    public static int getRequestCode(String uuid) {
        if (uuid == null) return 0;
        return Math.abs(uuid.hashCode() % 1000000);
    }

    public static String formatReminderTime(int hour, int minute) {
        int h12 = hour % 12;
        if (h12 == 0) h12 = 12;
        String ampm = hour < 12 ? "AM" : "PM";
        return String.format(Locale.US, "%d:%02d %s", h12, minute, ampm);
    }
}
