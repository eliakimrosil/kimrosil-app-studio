package com.kimrosil.wireamp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class CableThermalView extends View {

    private double deratedPercent = 80.0;
    private int conductorCount = 3;
    private String metalName = "Cu";

    private Paint conduitPaint;
    private Paint conduitStrokePaint;
    private Paint thermalRingPaint;
    private Paint conductorPaint;
    private Paint conductorStrokePaint;
    private Paint copperCorePaint;
    private Paint textBadgePaint;
    private Paint textBadgeBgPaint;

    private final RectF badgeRect = new RectF();
    private final Path lightningPath = new Path();

    public CableThermalView(Context context) {
        super(context);
        init();
    }

    public CableThermalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CableThermalView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        conduitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        conduitPaint.setStyle(Paint.Style.FILL);

        conduitStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        conduitStrokePaint.setStyle(Paint.Style.STROKE);
        conduitStrokePaint.setStrokeWidth(dpToPx(2.5f));

        thermalRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thermalRingPaint.setStyle(Paint.Style.STROKE);
        thermalRingPaint.setStrokeWidth(dpToPx(1.5f));

        conductorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        conductorPaint.setStyle(Paint.Style.FILL);

        conductorStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        conductorStrokePaint.setStyle(Paint.Style.STROKE);
        conductorStrokePaint.setStrokeWidth(dpToPx(1.2f));

        copperCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        copperCorePaint.setStyle(Paint.Style.FILL);

        textBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBadgePaint.setColor(0xFFFFFFFF);
        textBadgePaint.setTextSize(spToPx(11f));
        textBadgePaint.setFakeBoldText(true);
        textBadgePaint.setTextAlign(Paint.Align.CENTER);

        textBadgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBadgeBgPaint.setStyle(Paint.Style.FILL);
    }

    public void updateThermal(double deratedPct, int count, boolean isCopper) {
        this.deratedPercent = Math.max(0, Math.min(100.0, deratedPct));
        this.conductorCount = Math.max(1, count);
        this.metalName = isCopper ? "Cu" : "Al";
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean isDark = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // Thermal color shifts
        int colorThermal;
        if (deratedPercent >= 85.0) {
            colorThermal = isDark ? 0xFF38BDF8 : 0xFF0284C7; // Cool Cyan
        } else if (deratedPercent >= 65.0) {
            colorThermal = isDark ? 0xFFFBBF24 : 0xFFD97706; // Warm Amber
        } else {
            colorThermal = isDark ? 0xFFF43F5E : 0xFFE11D48; // Hot Danger Rose
        }

        int colorConduitBg = isDark ? 0xFF131D2E : 0xFFE2E8F0;
        int colorConduitWall = isDark ? 0xFF475569 : 0xFF94A3B8;
        int colorCore = metalName.equals("Cu") ? 0xFFF97316 : 0xFFCBD5E1;

        conduitPaint.setColor(colorConduitBg);
        conduitStrokePaint.setColor(colorConduitWall);
        copperCorePaint.setColor(colorCore);
        textBadgeBgPaint.setColor(colorThermal);

        float centerX = w / 2f;
        float centerY = h / 2f;
        float conduitRadius = Math.min(w, h) * 0.40f;

        // 1. Draw Thermal Radiation Ring
        thermalRingPaint.setColor(colorThermal);
        thermalRingPaint.setAlpha(60);
        canvas.drawCircle(centerX, centerY, conduitRadius + dpToPx(8), thermalRingPaint);
        thermalRingPaint.setAlpha(120);
        canvas.drawCircle(centerX, centerY, conduitRadius + dpToPx(4), thermalRingPaint);

        // 2. Draw Conduit Outer Shell
        canvas.drawCircle(centerX, centerY, conduitRadius, conduitPaint);
        canvas.drawCircle(centerX, centerY, conduitRadius, conduitStrokePaint);

        // 3. Draw Conductors in Conduit
        float condRadius = conduitRadius * 0.32f;
        float coreRadius = condRadius * 0.50f;

        // 3 Phase positions
        float[][] pos = {
                {centerX, centerY - condRadius * 0.7f},
                {centerX - condRadius * 0.7f, centerY + condRadius * 0.55f},
                {centerX + condRadius * 0.7f, centerY + condRadius * 0.55f}
        };

        int[] jacketColors = {
                isDark ? 0xFF0284C7 : 0xFF0369A1, // Blue
                isDark ? 0xFFDC2626 : 0xFFB91C1C, // Red
                isDark ? 0xFF334155 : 0xFF1E293B  // Black
        };

        for (int i = 0; i < 3; i++) {
            conductorPaint.setColor(jacketColors[i]);
            conductorStrokePaint.setColor(isDark ? 0xFF94A3B8 : 0xFFCBD5E1);

            canvas.drawCircle(pos[i][0], pos[i][1], condRadius, conductorPaint);
            canvas.drawCircle(pos[i][0], pos[i][1], condRadius, conductorStrokePaint);
            canvas.drawCircle(pos[i][0], pos[i][1], coreRadius, copperCorePaint);
        }

        // 4. Center Lightning Spark
        lightningPath.reset();
        lightningPath.moveTo(centerX + dpToPx(1), centerY - dpToPx(10));
        lightningPath.lineTo(centerX - dpToPx(5), centerY);
        lightningPath.lineTo(centerX, centerY);
        lightningPath.lineTo(centerX - dpToPx(2), centerY + dpToPx(10));
        lightningPath.lineTo(centerX + dpToPx(6), centerY - dpToPx(2));
        lightningPath.lineTo(centerX + dpToPx(1), centerY - dpToPx(2));
        lightningPath.close();

        Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sparkPaint.setColor(0xFFFBBF24);
        sparkPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(lightningPath, sparkPaint);

        // 5. Thermal Derating Badge Callout at Bottom
        String badgeStr = String.format(Locale.US, "%.0f%% Net Ampacity Capacity", deratedPercent);
        float badgeW = textBadgePaint.measureText(badgeStr) + dpToPx(18);
        float badgeH = dpToPx(22);
        badgeRect.set(centerX - badgeW / 2f, h - badgeH - dpToPx(4), centerX + badgeW / 2f, h - dpToPx(4));

        canvas.drawRoundRect(badgeRect, dpToPx(11), dpToPx(11), textBadgeBgPaint);
        float textY = badgeRect.top + (badgeH / 2f) - ((textBadgePaint.descent() + textBadgePaint.ascent()) / 2f);
        canvas.drawText(badgeStr, centerX, textY, textBadgePaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
