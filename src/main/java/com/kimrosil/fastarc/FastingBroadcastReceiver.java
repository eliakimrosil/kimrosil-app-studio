package com.kimrosil.fastarc;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * FastingBroadcastReceiver: Handles quick actions from notifications (e.g. log water)
 * and system boot to maintain live widget & lock-screen status.
 */
public class FastingBroadcastReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "fastarc_prefs";
    private static final String KEY_IS_FASTING = "is_fasting";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_TARGET_HOURS = "target_hours";
    private static final String KEY_WATER_ML = "water_ml";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (FastingNotificationManager.ACTION_LOG_WATER_NOTIF.equals(action)) {
            int currentWater = prefs.getInt(KEY_WATER_ML, 0);
            int newWater = currentWater + 250;
            prefs.edit().putInt(KEY_WATER_ML, newWater).apply();

            boolean isFasting = prefs.getBoolean(KEY_IS_FASTING, false);
            long startTime = prefs.getLong(KEY_START_TIME, 0);
            int targetHours = prefs.getInt(KEY_TARGET_HOURS, 16);

            FastingNotificationManager.showOrUpdateNotification(context, isFasting, startTime, targetHours, newWater);

            // Notify activity if active
            Intent updateIntent = new Intent(FastingWidgetProvider.ACTION_UPDATE_WIDGET);
            context.sendBroadcast(updateIntent);

        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            boolean isFasting = prefs.getBoolean(KEY_IS_FASTING, false);
            long startTime = prefs.getLong(KEY_START_TIME, 0);
            int targetHours = prefs.getInt(KEY_TARGET_HOURS, 16);
            int waterMl = prefs.getInt(KEY_WATER_ML, 0);

            if (isFasting && startTime > 0) {
                FastingNotificationManager.showOrUpdateNotification(context, isFasting, startTime, targetHours, waterMl);
            }

            // Update widget
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, FastingWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            for (int id : appWidgetIds) {
                FastingWidgetProvider.updateAppWidget(context, appWidgetManager, id);
            }
        }
    }
}
