package com.kimrosil.fastarc;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.util.Locale;

/**
 * FastingWidgetProvider: Keyguard Lock-Screen and Home-Screen AppWidget.
 * Updates seamlessly via broadcast or standard scheduler.
 */
public class FastingWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_UPDATE_WIDGET = "com.kimrosil.fastarc.ACTION_UPDATE_WIDGET";
    private static final String PREFS_NAME = "fastarc_prefs";
    private static final String KEY_IS_FASTING = "is_fasting";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_TARGET_HOURS = "target_hours";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_UPDATE_WIDGET.equals(intent.getAction()) ||
            AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, FastingWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isFasting = prefs.getBoolean(KEY_IS_FASTING, false);
        long startTime = prefs.getLong(KEY_START_TIME, 0);
        int targetHours = prefs.getInt(KEY_TARGET_HOURS, 16);
        long targetMillis = targetHours * 3600L * 1000L;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_fasting);

        if (!isFasting || startTime <= 0) {
            views.setTextViewText(R.id.tv_widget_status_badge, "EATING");
            views.setTextColor(R.id.tv_widget_status_badge, 0xFFFB923C);
            views.setTextViewText(R.id.tv_widget_timer, "16:8 Window");
            views.setTextViewText(R.id.tv_widget_stage, "Tap to start fasting arc");
            views.setProgressBar(R.id.widget_progress_bar, 1000, 0, false);
        } else {
            long now = System.currentTimeMillis();
            long elapsed = Math.max(0, now - startTime);
            long remaining = Math.max(0, targetMillis - elapsed);
            float progressFraction = (float) elapsed / (float) targetMillis;
            int progressInt = Math.min(1000, (int) (progressFraction * 1000f));

            String elapsedStr = formatHoursMinutes(elapsed);
            String stageDesc;

            long elapsedHours = elapsed / (3600L * 1000L);
            if (elapsedHours < 4) {
                stageDesc = "Digestion • " + formatHoursMinutes(remaining) + " left";
            } else if (elapsedHours < 12) {
                stageDesc = "Glycogen Burn • " + formatHoursMinutes(remaining) + " left";
            } else if (elapsedHours < 16) {
                stageDesc = "Ketosis Active • " + formatHoursMinutes(remaining) + " left";
            } else {
                long over = elapsed - targetMillis;
                stageDesc = "Autophagy • +" + formatHoursMinutes(over) + " extended";
                progressInt = 1000;
            }

            views.setTextViewText(R.id.tv_widget_status_badge, "FASTING");
            views.setTextColor(R.id.tv_widget_status_badge, 0xFF10B981);
            views.setTextViewText(R.id.tv_widget_timer, elapsedStr);
            views.setTextViewText(R.id.tv_widget_stage, stageDesc);
            views.setProgressBar(R.id.widget_progress_bar, 1000, progressInt, false);
        }

        // Tap opens app
        Intent clickIntent = new Intent(context, MainActivity.class);
        clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static String formatHoursMinutes(long millis) {
        long totalMinutes = millis / (60 * 1000);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format(Locale.US, "%dh %02dm", hours, minutes);
    }
}
