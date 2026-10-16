package com.kimrosil.fastarc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Locale;

/**
 * FastingNotificationManager: Handles silent, lock-screen-visible (VISIBILITY_PUBLIC)
 * ongoing notification for 16:8 fasting status and live telemetry.
 */
public class FastingNotificationManager {

    public static final String CHANNEL_ID = "fastarc_status_channel";
    public static final int NOTIFICATION_ID = 1608;
    public static final String ACTION_LOG_WATER_NOTIF = "com.kimrosil.fastarc.ACTION_LOG_WATER_NOTIF";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(context.getString(R.string.notif_channel_desc));
                channel.setShowBadge(false);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void showOrUpdateNotification(Context context, boolean isFasting, long startTime, int targetHours, int waterMl) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        createNotificationChannel(context);

        if (!isFasting || startTime <= 0) {
            manager.cancel(NOTIFICATION_ID);
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - startTime);
        long targetMillis = targetHours * 3600L * 1000L;
        long remaining = Math.max(0, targetMillis - elapsed);

        long elapsedHours = elapsed / (3600L * 1000L);
        String stageName;
        if (elapsedHours < 4) {
            stageName = "Digestion";
        } else if (elapsedHours < 12) {
            stageName = "Glycogen Depletion";
        } else if (elapsedHours < 16) {
            stageName = "Ketosis Active";
        } else {
            stageName = "Autophagy Threshold";
        }

        String elapsedStr = formatHoursMinutes(elapsed);
        String remainingStr = formatHoursMinutes(remaining);

        String title = "FastArc 16:8 • " + stageName;
        String content = elapsedStr + " elapsed • " + remainingStr + " remaining (" + waterMl + "ml logged)";

        // App Open Intent
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Water Logging Action Intent
        Intent waterIntent = new Intent(context, FastingBroadcastReceiver.class);
        waterIntent.setAction(ACTION_LOG_WATER_NOTIF);
        PendingIntent waterPendingIntent = PendingIntent.getBroadcast(
                context, 1, waterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        null,
                        "+250ml Water",
                        waterPendingIntent
                ).build());

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    public static void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    private static String formatHoursMinutes(long millis) {
        long totalMinutes = millis / (60 * 1000);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format(Locale.US, "%dh %02dm", hours, minutes);
    }
}
