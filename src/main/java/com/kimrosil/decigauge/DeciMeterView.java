package com.kimrosil.decigauge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class DeciMeterView extends View {

    private final Paint arcBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textUnitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();
    private float currentDb = 40.0f;
    private float peakDb = 40.0f;
    private float leqDb = 40.0f;
    private String modeLabel = "dBA • FAST";
    private final float[] spectrumBands = new float[8];

    private static final float MIN_DB = 30.0f;
    private static final float MAX_DB = 130.0f;
    private static final float START_ANGLE = 145.0f;
    private static final float SWEEP_ANGLE = 250.0f;

    public DeciMeterView(Context context) {
        super(context);
        init();
    }

    public DeciMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DeciMeterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arcBgPaint.setStyle(Paint.Style.STROKE);
        arcBgPaint.setStrokeCap(Paint.Cap.ROUND);

        arcValuePaint.setStyle(Paint.Style.STROKE);
        arcValuePaint.setStrokeCap(Paint.Cap.ROUND);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);

        needleKnobPaint.setStyle(Paint.Style.FILL);

        peakPaint.setStyle(Paint.Style.STROKE);
        peakPaint.setStrokeCap(Paint.Cap.ROUND);
        peakPaint.setColor(Color.parseColor("#FF3B30"));

        textValuePaint.setTextAlign(Paint.Align.CENTER);
        textValuePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        textUnitPaint.setTextAlign(Paint.Align.CENTER);
        textUnitPaint.setTypeface(Typeface.DEFAULT_BOLD);

        textTickPaint.setTextAlign(Paint.Align.CENTER);
        textTickPaint.setTextSize(spToPx(9.0f));

        spectrumPaint.setStyle(Paint.Style.FILL);
    }

    public void updateTelemetry(float current, float peak, float leq, String mode, float[] bands) {
        float newCur = Math.max(MIN_DB, Math.min(MAX_DB, current));
        float newPeak = Math.max(MIN_DB, Math.min(MAX_DB, peak));
        if (Math.abs(newCur - this.currentDb) < 0.2f && Math.abs(newPeak - this.peakDb) < 0.2f && this.modeLabel.equals(mode)) {
            return;
        }
        this.currentDb = newCur;
        this.peakDb = newPeak;
        this.leqDb = leq;
        this.modeLabel = (mode != null) ? mode : "dBA • FAST";
        if (bands != null && bands.length >= 8) {
            System.arraycopy(bands, 0, this.spectrumBands, 0, 8);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = dpToPx(320);
        int desiredHeight = dpToPx(140);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width = (widthMode == MeasureSpec.EXACTLY) ? widthSize : desiredWidth;
        int height = (heightMode == MeasureSpec.EXACTLY) ? heightSize : desiredHeight;

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean isDark = isDarkTheme();
        int colorBgTrack = isDark ? Color.parseColor("#1A2530") : Color.parseColor("#E2E8F0");
        int colorTextPrim = isDark ? Color.parseColor("#E2F1F8") : Color.parseColor("#0D1B2A");
        int colorTextSec = isDark ? Color.parseColor("#7E9BB3") : Color.parseColor("#50667F");
        int colorTick = isDark ? Color.parseColor("#2E4357") : Color.parseColor("#CBD5E1");

        // Dynamic zone color based on current dB
        int activeColor;
        if (currentDb < 75.0f) {
            activeColor = isDark ? Color.parseColor("#00F2FE") : Color.parseColor("#008B94");
        } else if (currentDb < 85.0f) {
            activeColor = isDark ? Color.parseColor("#FFB300") : Color.parseColor("#E65100");
        } else if (currentDb < 90.0f) {
            activeColor = Color.parseColor("#FF9100");
        } else {
            activeColor = Color.parseColor("#FF3B30");
        }

        float strokeWidth = dpToPx(7);
        arcBgPaint.setStrokeWidth(strokeWidth);
        arcBgPaint.setColor(colorBgTrack);

        arcValuePaint.setStrokeWidth(strokeWidth);
        arcValuePaint.setColor(activeColor);

        needlePaint.setColor(activeColor);
        needlePaint.setStrokeWidth(dpToPx(2.5f));
        needleKnobPaint.setColor(activeColor);

        textValuePaint.setColor(colorTextPrim);
        textValuePaint.setTextSize(spToPx(24.0f));

        textUnitPaint.setColor(colorTextSec);
        textUnitPaint.setTextSize(spToPx(10.0f));

        textTickPaint.setColor(colorTextSec);

        // Center calculation
        float centerX = w * 0.5f;
        float centerY = h * 0.72f;
        float radius = Math.min(centerX - dpToPx(28), centerY - dpToPx(14));
        if (radius < dpToPx(40)) radius = dpToPx(40);

        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        // 1. Background Arc Track
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, arcBgPaint);

        // 2. Active Value Arc
        float progressFraction = (currentDb - MIN_DB) / (MAX_DB - MIN_DB);
        progressFraction = Math.max(0.0f, Math.min(1.0f, progressFraction));
        float currentSweep = progressFraction * SWEEP_ANGLE;
        canvas.drawArc(arcRect, START_ANGLE, currentSweep, false, arcValuePaint);

        // 3. Ticks and Labels (30, 50, 70, 90, 110, 130 dB)
        tickPaint.setColor(colorTick);
        tickPaint.setStrokeWidth(dpToPx(1.5f));
        int[] majorTicks = {30, 50, 70, 90, 110, 130};
        for (int t : majorTicks) {
            float frac = (t - MIN_DB) / (MAX_DB - MIN_DB);
            double rad = Math.toRadians(START_ANGLE + frac * SWEEP_ANGLE);
            float innerR = radius - dpToPx(10);
            float outerR = radius + dpToPx(3);
            float x1 = (float) (centerX + innerR * Math.cos(rad));
            float y1 = (float) (centerY + innerR * Math.sin(rad));
            float x2 = (float) (centerX + outerR * Math.cos(rad));
            float y2 = (float) (centerY + outerR * Math.sin(rad));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);

            // Tick text
            float textR = innerR - dpToPx(9);
            float tx = (float) (centerX + textR * Math.cos(rad));
            float ty = (float) (centerY + textR * Math.sin(rad) + dpToPx(3));
            canvas.drawText(String.valueOf(t), tx, ty, textTickPaint);
        }

        // 4. Peak Hold Marker Tick
        float peakFrac = (peakDb - MIN_DB) / (MAX_DB - MIN_DB);
        peakFrac = Math.max(0.0f, Math.min(1.0f, peakFrac));
        double peakRad = Math.toRadians(START_ANGLE + peakFrac * SWEEP_ANGLE);
        peakPaint.setStrokeWidth(dpToPx(3.5f));
        float px1 = (float) (centerX + (radius - dpToPx(12)) * Math.cos(peakRad));
        float py1 = (float) (centerY + (radius - dpToPx(12)) * Math.sin(peakRad));
        float px2 = (float) (centerX + (radius + dpToPx(4)) * Math.cos(peakRad));
        float py2 = (float) (centerY + (radius + dpToPx(4)) * Math.sin(peakRad));
        canvas.drawLine(px1, py1, px2, py2, peakPaint);

        // 5. Analog Needle Pointer
        double needleRad = Math.toRadians(START_ANGLE + currentSweep);
        float needleLen = radius - dpToPx(14);
        float nx = (float) (centerX + needleLen * Math.cos(needleRad));
        float ny = (float) (centerY + needleLen * Math.sin(needleRad));
        canvas.drawLine(centerX, centerY, nx, ny, needlePaint);
        canvas.drawCircle(centerX, centerY, dpToPx(4.5f), needleKnobPaint);

        // 6. Primary Monospace Numeric Readout
        String dbText = String.format("%.1f", currentDb);
        canvas.drawText(dbText, centerX, centerY - dpToPx(18), textValuePaint);

        // 7. Subtitle Telemetry Badge
        String subBadge = modeLabel + " • PEAK " + String.format("%.1f", peakDb);
        canvas.drawText(subBadge, centerX, centerY - dpToPx(4), textUnitPaint);

        // 8. Bottom 8-Band Frequency Spectrum Visualizer
        float barAreaWidth = w * 0.7f;
        float barStartX = centerX - barAreaWidth * 0.5f;
        float barYBottom = h - dpToPx(6);
        float maxBarH = dpToPx(14);
        float barSpacing = barAreaWidth / 8.0f;
        float barWidth = barSpacing * 0.6f;

        for (int b = 0; b < 8; b++) {
            float barFrac = Math.max(0.08f, Math.min(1.0f, spectrumBands[b]));
            float barH = maxBarH * barFrac;
            float bx = barStartX + b * barSpacing + (barSpacing - barWidth) * 0.5f;

            if (b >= 6 && currentDb > 85.0f) {
                spectrumPaint.setColor(Color.parseColor("#FF3B30"));
            } else if (b >= 4) {
                spectrumPaint.setColor(isDark ? Color.parseColor("#FFB300") : Color.parseColor("#E65100"));
            } else {
                spectrumPaint.setColor(isDark ? Color.parseColor("#00F2FE") : Color.parseColor("#008B94"));
            }
            canvas.drawRoundRect(bx, barYBottom - barH, bx + barWidth, barYBottom, dpToPx(2), dpToPx(2), spectrumPaint);
        }
    }

    private boolean isDarkTheme() {
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int spToPx(float sp) {
        return Math.round(sp * getResources().getDisplayMetrics().scaledDensity);
    }
}
