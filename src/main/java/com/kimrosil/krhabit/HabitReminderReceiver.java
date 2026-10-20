package com.kimrosil.krhabit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * BroadcastReceiver triggered by AlarmManager to post daily habit reminders,
 * and to process direct check-ins from the notification action.
 */
public class HabitReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (context == null || intent == null) return;

        final String action = intent.getAction();
        final String habitUuid = intent.getStringExtra(ReminderManager.EXTRA_HABIT_UUID);
        if (habitUuid == null || habitUuid.trim().isEmpty()) return;

        final DatabaseHelper dbHelper = new DatabaseHelper(context);

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

    private void showNotification(Context context, DatabaseHelper.HabitItem habit) {
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
                : "Time to complete your habit today!";

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, ReminderManager.CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setContentTitle(habit.title)
                .setContentText(message)
                .setSmallIcon(R.drawable.btn_checkin_done)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#059669"))
                .addAction(new Notification.Action.Builder(
                        null,
                        "✓ Check In",
                        checkInPi
                ).build());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setPriority(Notification.PRIORITY_HIGH);
        }

        nm.notify(reqCode, builder.build());
    }
}
