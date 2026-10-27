package com.kimrosil.ductflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class DuctCrossSectionView extends View {

    private double rectWidth = 16.0;
    private double rectHeight = 10.0;
    private double roundDia = 13.5;
    private double velocityFpm = 950.0;
    private boolean isAspectSafe = true;
    private boolean isMetric = false;

    private Paint ductBgPaint;
    private Paint ductStrokePaint;
    private Paint roundDuctStrokePaint;
    private Paint streamlinePaint;
    private Paint arrowHeadPaint;
    private Paint dimensionTextPaint;
    private Paint dimensionLinePaint;
    private Paint badgeBgPaint;
    private Paint badgeTextPaint;

    private final RectF ductRect = new RectF();
    private final RectF badgeRect = new RectF();
    private final Path arrowPath = new Path();

    public DuctCrossSectionView(Context context) {
        super(context);
        init();
    }

    public DuctCrossSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DuctCrossSectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ductBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ductBgPaint.setStyle(Paint.Style.FILL);

        ductStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ductStrokePaint.setStyle(Paint.Style.STROKE);
        ductStrokePaint.setStrokeWidth(dpToPx(2.2f));

        roundDuctStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        roundDuctStrokePaint.setStyle(Paint.Style.STROKE);
        roundDuctStrokePaint.setStrokeWidth(dpToPx(1.6f));
        roundDuctStrokePaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(6), dpToPx(4)}, 0));

        streamlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        streamlinePaint.setStyle(Paint.Style.STROKE);
        streamlinePaint.setStrokeWidth(dpToPx(1.4f));

        arrowHeadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowHeadPaint.setStyle(Paint.Style.FILL);

        dimensionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimensionTextPaint.setTextSize(spToPx(10.5f));
        dimensionTextPaint.setFakeBoldText(true);
        dimensionTextPaint.setTextAlign(Paint.Align.CENTER);

        dimensionLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimensionLinePaint.setStyle(Paint.Style.STROKE);
        dimensionLinePaint.setStrokeWidth(dpToPx(1.0f));

        badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeBgPaint.setStyle(Paint.Style.FILL);

        badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeTextPaint.setColor(0xFFFFFFFF);
        badgeTextPaint.setTextSize(spToPx(10.0f));
        badgeTextPaint.setFakeBoldText(true);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void updateDuct(double width, double height, double roundD, double velocity, boolean aspectSafe, boolean metric) {
        this.rectWidth = Math.max(2.0, width);
        this.rectHeight = Math.max(2.0, height);
        this.roundDia = Math.max(2.0, roundD);
        this.velocityFpm = Math.max(0, velocity);
        this.isAspectSafe = aspectSafe;
        this.isMetric = metric;
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

        // Colors
        int colorPrimary = isDark ? 0xFF38BDF8 : 0xFF0284C7;
        int colorAccent = isDark ? 0xFF67E8F9 : 0xFF0EA5E9;
        int colorText = isDark ? 0xFFF8FAFC : 0xFF0F172A;
        int colorMuted = isDark ? 0xFF94A3B8 : 0xFF64748B;
        int colorSafe = isDark ? 0xFF34D399 : 0xFF10B981;
        int colorWarning = isDark ? 0xFFFBBF24 : 0xFFF59E0B;

        ductBgPaint.setColor(isDark ? 0x2238BDF8 : 0x120284C7);
        ductStrokePaint.setColor(colorPrimary);
        roundDuctStrokePaint.setColor(colorAccent);
        streamlinePaint.setColor(isDark ? 0x8867E8F9 : 0x880284C7);
        arrowHeadPaint.setColor(isDark ? 0xFF67E8F9 : 0xFF0284C7);
        dimensionTextPaint.setColor(colorText);
        dimensionLinePaint.setColor(colorMuted);

        float centerX = w / 2f;
        float centerY = (h - dpToPx(18)) / 2f;

        // Scaling box to fit canvas
        float maxAvailableW = w * 0.65f;
        float maxAvailableH = (h - dpToPx(38)) * 0.75f;

        float maxDim = (float) Math.max(rectWidth, Math.max(rectHeight, roundDia));
        float scale = (maxDim > 0) ? Math.min(maxAvailableW / maxDim, maxAvailableH / maxDim) : 1f;

        float drawW = (float) rectWidth * scale;
        float drawH = (float) rectHeight * scale;
        float drawRoundR = ((float) roundDia * scale) / 2f;

        ductRect.set(centerX - drawW / 2f, centerY - drawH / 2f, centerX + drawW / 2f, centerY + drawH / 2f);

        // 1. Draw Rectangular Duct Cross-Section
        canvas.drawRoundRect(ductRect, dpToPx(6), dpToPx(6), ductBgPaint);
        canvas.drawRoundRect(ductRect, dpToPx(6), dpToPx(6), ductStrokePaint);

        // 2. Draw Equivalent Round Spiral Duct Inset Contour
        canvas.drawCircle(centerX, centerY, drawRoundR, roundDuctStrokePaint);

        // 3. Draw Center Airflow Streamlines
        float lineY1 = centerY - drawH * 0.22f;
        float lineY2 = centerY;
        float lineY3 = centerY + drawH * 0.22f;

        float arrowStartX = centerX - drawW * 0.28f;
        float arrowEndX = centerX + drawW * 0.28f;

        drawAirflowArrow(canvas, arrowStartX, lineY1, arrowEndX, lineY1);
        drawAirflowArrow(canvas, arrowStartX - dpToPx(4), lineY2, arrowEndX + dpToPx(4), lineY2);
        drawAirflowArrow(canvas, arrowStartX, lineY3, arrowEndX, lineY3);

        // 4. Dimensions Callouts
        String wText = isMetric ?
                String.format(Locale.US, "%.0f mm W", rectWidth * 25.4) :
                String.format(Locale.US, "%.1f\" W", rectWidth);
        canvas.drawText(wText, centerX, ductRect.top - dpToPx(6), dimensionTextPaint);

        String hText = isMetric ?
                String.format(Locale.US, "%.0f mm H", rectHeight * 25.4) :
                String.format(Locale.US, "%.0f\" H", rectHeight);
        float hTextX = ductRect.right + dpToPx(16);
        float hTextY = centerY + dpToPx(4);
        canvas.drawText(hText, hTextX, hTextY, dimensionTextPaint);

        // 5. Telemetry Badge at Bottom
        String badgeStr = isMetric ?
                String.format(Locale.US, "Ø %.0f mm Spiral • %.1f m/s • %s",
                        roundDia * 25.4, velocityFpm * 0.00508, isAspectSafe ? "Compliant AR" : "High AR Warning") :
                String.format(Locale.US, "Ø %.1f\" Spiral • %.0f FPM • %s",
                        roundDia, velocityFpm, isAspectSafe ? "Compliant AR" : "High AR Warning");
        badgeBgPaint.setColor(isAspectSafe ? colorSafe : colorWarning);

        float badgeW = badgeTextPaint.measureText(badgeStr) + dpToPx(16);
        float badgeH = dpToPx(20);
        badgeRect.set(centerX - badgeW / 2f, h - badgeH - dpToPx(3), centerX + badgeW / 2f, h - dpToPx(3));

        canvas.drawRoundRect(badgeRect, dpToPx(10), dpToPx(10), badgeBgPaint);
        float textY = badgeRect.top + (badgeH / 2f) - ((badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f);
        canvas.drawText(badgeStr, centerX, textY, badgeTextPaint);
    }

    private void drawAirflowArrow(Canvas canvas, float startX, float startY, float endX, float endY) {
        canvas.drawLine(startX, startY, endX, endY, streamlinePaint);

        arrowPath.reset();
        float headLen = dpToPx(5);
        float headHalfW = dpToPx(3.5f);
        arrowPath.moveTo(endX, endY);
        arrowPath.lineTo(endX - headLen, endY - headHalfW);
        arrowPath.lineTo(endX - headLen + dpToPx(1.5f), endY);
        arrowPath.lineTo(endX - headLen, endY + headHalfW);
        arrowPath.close();

        canvas.drawPath(arrowPath, arrowHeadPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
