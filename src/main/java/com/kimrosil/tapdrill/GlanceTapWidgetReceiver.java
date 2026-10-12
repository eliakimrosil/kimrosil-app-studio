package com.kimrosil.tapdrill;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class GlanceTapWidgetReceiver extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidgetView(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("com.kimrosil.tapdrill.ACTION_UPDATE_WIDGET".equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, GlanceTapWidgetReceiver.class);
            int[] allIds = manager.getAppWidgetIds(thisWidget);
            for (int widgetId : allIds) {
                updateWidgetView(context, manager, widgetId);
            }
        }
    }

    private void updateWidgetView(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences("tap_drill_prefs", Context.MODE_PRIVATE);
        String nominal = prefs.getString("pinned_nominal", "1/4-20 UNC");
        String pitch = prefs.getString("pinned_pitch", "20 TPI");
        String drill = prefs.getString("pinned_drill", ".2010\"");
        String wire = prefs.getString("pinned_wire", "#7");

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_tap_glance);
        views.setTextViewText(R.id.tvWidgetNominal, nominal);
        views.setTextViewText(R.id.tvWidgetPitch, pitch);
        views.setTextViewText(R.id.tvWidgetDrill, drill);
        views.setTextViewText(R.id.tvWidgetWire, wire);

        Intent clickIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.llWidgetRoot, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}