package com.kimrosil.shimpack;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Ongoing status bar notification HUD tracking live bolt-down progression,
 * target shim thickness, and tolerance status without needing the full app open.
 */
public class AlignmentNotificationHud {

    public static final String CHANNEL_ID = "shimpack_boltdown_hud";
    public static final int NOTIFICATION_ID = 5601;

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(context.getString(R.string.notification_channel_desc));
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    public static void showHud(Context context, String machineTag, String f1Target, String f2Target, String status) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        createNotificationChannel(context);

        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        String title = "ShimPack HUD: " + (machineTag != null && !machineTag.isEmpty() ? machineTag : "Active Machine");
        String text = "F1: " + f1Target + " | F2: " + f2Target + " [" + status + "]";

        builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setShowWhen(true);
        }
        builder.setWhen(System.currentTimeMillis());

        try {
            nm.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Android 13+ POST_NOTIFICATIONS permission guard
        }
    }

    public static void dismissHud(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                nm.cancel(NOTIFICATION_ID);
            } catch (Exception ignored) {
            }
        }
    }
}