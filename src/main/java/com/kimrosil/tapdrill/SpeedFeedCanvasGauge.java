package com.kimrosil.tapdrill;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.Locale;

public class SpeedFeedCanvasGauge extends View {

    private Paint mPaintDialBg;
    private Paint mPaintSafeArc;
    private Paint mPaintWarnArc;
    private Paint mPaintTicks;
    private Paint mPaintNeedle;
    private Paint mPaintHub;
    private Paint mPaintHubRim;
    private Paint mPaintText;
    private Paint mPaintBorder;

    private int mCurrentRpm = 1528;
    private int mMaxRpm = 6000;
    private final RectF mArcRect = new RectF();

    public SpeedFeedCanvasGauge(Context context) {
        super(context);
        init();
    }

    public SpeedFeedCanvasGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mPaintDialBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintDialBg.setColor(0xFF0A0F1D); // Deep hardened obsidian dial background
        mPaintDialBg.setStyle(Paint.Style.FILL);

        mPaintBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintBorder.setColor(0xFF2A374A);
        mPaintBorder.setStyle(Paint.Style.STROKE);
        mPaintBorder.setStrokeWidth(dp(1.5f));

        mPaintSafeArc = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintSafeArc.setColor(0xFF00E5FF); // Electric cyan safe operating zone
        mPaintSafeArc.setStyle(Paint.Style.STROKE);
        mPaintSafeArc.setStrokeWidth(dp(10f));
        mPaintSafeArc.setStrokeCap(Paint.Cap.ROUND);

        mPaintWarnArc = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintWarnArc.setColor(0xFFEF4444); // Amber-red thermal limit
        mPaintWarnArc.setStyle(Paint.Style.STROKE);
        mPaintWarnArc.setStrokeWidth(dp(10f));
        mPaintWarnArc.setStrokeCap(Paint.Cap.ROUND);

        mPaintTicks = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintTicks.setColor(0xFF64748B);
        mPaintTicks.setStrokeWidth(dp(2f));

        mPaintNeedle = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintNeedle.setColor(0xFFF8FAFC);
        mPaintNeedle.setStyle(Paint.Style.FILL);

        mPaintHub = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintHub.setColor(0xFF1E293B);
        mPaintHub.setStyle(Paint.Style.FILL);

        mPaintHubRim = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintHubRim.setColor(0xFF38BDF8);
        mPaintHubRim.setStyle(Paint.Style.STROKE);
        mPaintHubRim.setStrokeWidth(dp(2.5f));

        mPaintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintText.setColor(0xFF94A3B8);
        mPaintText.setTextSize(dp(10f));
        mPaintText.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float val) {
        return val * getResources().getDisplayMetrics().density;
    }

    public void setRpm(int rpm, int maxSafeRpm) {
        this.mCurrentRpm = Math.max(0, rpm);
        this.mMaxRpm = Math.max(2000, maxSafeRpm > 0 ? (int)(maxSafeRpm * 1.5) : 6000);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawRoundRect(0, 0, w, h, dp(12f), dp(12f), mPaintDialBg);
        canvas.drawRoundRect(dp(0.75f), dp(0.75f), w - dp(0.75f), h - dp(0.75f), dp(12f), dp(12f), mPaintBorder);

        float cx = w / 2f;
        float cy = h / 2f + dp(10f);
        float radius = Math.min(w, h) / 2f - dp(22f);

        mArcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Sweep from 135 deg to 405 deg (270 deg total arc)
        canvas.drawArc(mArcRect, 135f, 190f, false, mPaintSafeArc);
        canvas.drawArc(mArcRect, 325f, 80f, false, mPaintWarnArc);

        // Dial Ticks
        for (int i = 0; i <= 10; i++) {
            float angle = 135f + (i / 10f) * 270f;
            double rad = Math.toRadians(angle);
            float innerR = radius - dp(8f);
            float x1 = (float) (cx + innerR * Math.cos(rad));
            float y1 = (float) (cy + innerR * Math.sin(rad));
            float x2 = (float) (cx + radius * Math.cos(rad));
            float y2 = (float) (cy + radius * Math.sin(rad));
            canvas.drawLine(x1, y1, x2, y2, mPaintTicks);
        }

        // Needle calculation
        float fraction = Math.min(1.0f, (float) mCurrentRpm / mMaxRpm);
        float needleAngle = 135f + fraction * 270f;
        double needleRad = Math.toRadians(needleAngle);

        float tipX = (float) (cx + (radius - dp(12f)) * Math.cos(needleRad));
        float tipY = (float) (cy + (radius - dp(12f)) * Math.sin(needleRad));

        double baseRad1 = Math.toRadians(needleAngle + 90);
        double baseRad2 = Math.toRadians(needleAngle - 90);
        float b1x = (float) (cx + dp(5f) * Math.cos(baseRad1));
        float b1y = (float) (cy + dp(5f) * Math.sin(baseRad1));
        float b2x = (float) (cx + dp(5f) * Math.cos(baseRad2));
        float b2y = (float) (cy + dp(5f) * Math.sin(baseRad2));

        Path needlePath = new Path();
        needlePath.moveTo(tipX, tipY);
        needlePath.lineTo(b1x, b1y);
        needlePath.lineTo(b2x, b2y);
        needlePath.close();
        canvas.drawPath(needlePath, mPaintNeedle);

        // Center hub
        canvas.drawCircle(cx, cy, dp(12f), mPaintHub);
        canvas.drawCircle(cx, cy, dp(12f), mPaintHubRim);

        // Dial bottom readout
        canvas.drawText(String.format(Locale.US, "MAX SAFE: %d RPM", mMaxRpm), cx, cy + dp(32f), mPaintText);
    }
}