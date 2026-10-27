package com.kimrosil.conduitfill;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class ConduitCrossSectionView extends View {

    private final Paint conduitWallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint conduitInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wireInsulationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wireCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();

    private double fillPercent = 35.0;
    private double maxAllowedPercent = 40.0;
    private int primaryCount = 3;
    private int groundCount = 1;
    private double conduitIdInches = 1.049;
    private String racewayLabel = "1\" EMT";

    private static final int[] PHASE_COLORS = {
            0xFF1E293B, // Phase A: Black
            0xFFEF4444, // Phase B: Red
            0xFF3B82F6, // Phase C: Blue
            0xFFF8FAFC, // Neutral: White
            0xFF10B981  // Ground: Green
    };

    public ConduitCrossSectionView(Context context) {
        super(context);
        init();
    }

    public ConduitCrossSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ConduitCrossSectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        conduitWallPaint.setStyle(Paint.Style.STROKE);
        conduitInnerPaint.setStyle(Paint.Style.FILL);

        arcTrackPaint.setStyle(Paint.Style.STROKE);
        arcTrackPaint.setStrokeCap(Paint.Cap.ROUND);

        arcFillPaint.setStyle(Paint.Style.STROKE);
        arcFillPaint.setStrokeCap(Paint.Cap.ROUND);

        wireInsulationPaint.setStyle(Paint.Style.FILL);
        wireCorePaint.setStyle(Paint.Style.FILL);
        wireCorePaint.setColor(Color.parseColor("#F97316")); // Copper

        textValuePaint.setTextAlign(Paint.Align.CENTER);
        textValuePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        textSubPaint.setTextAlign(Paint.Align.CENTER);
        textSubPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void updateTelemetry(double fill, double allowed, int primaries, int grounds, double id, String label) {
        this.fillPercent = fill;
        this.maxAllowedPercent = allowed;
        this.primaryCount = primaries;
        this.groundCount = grounds;
        this.conduitIdInches = id;
        this.racewayLabel = (label != null) ? label : "Conduit";
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = dpToPx(320);
        int desiredHeight = dpToPx(125);

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
        int colorPipeWall = isDark ? Color.parseColor("#475569") : Color.parseColor("#94A3B8");
        int colorPipeInner = isDark ? Color.parseColor("#0F141C") : Color.parseColor("#F1F5F9");
        int colorTrack = isDark ? Color.parseColor("#1E293B") : Color.parseColor("#E2E8F0");
        int colorTextPrim = isDark ? Color.parseColor("#E6EDF5") : Color.parseColor("#0F172A");
        int colorTextSec = isDark ? Color.parseColor("#8B9BB0") : Color.parseColor("#475569");

        // Status fill color
        int activeFillColor;
        if (fillPercent <= maxAllowedPercent) {
            if (fillPercent >= maxAllowedPercent * 0.92) {
                activeFillColor = isDark ? Color.parseColor("#FBBF24") : Color.parseColor("#D97706");
            } else {
                activeFillColor = isDark ? Color.parseColor("#34D399") : Color.parseColor("#059669");
            }
        } else {
            activeFillColor = isDark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626");
        }

        float centerX = w * 0.32f;
        float centerY = h * 0.5f;
        float conduitRadius = Math.min(centerX - dpToPx(16), centerY - dpToPx(14));
        if (conduitRadius < dpToPx(35)) conduitRadius = dpToPx(35);

        // 1. Galvanized Conduit Wall (Outer Ring)
        float wallThickness = dpToPx(5);
        conduitWallPaint.setStrokeWidth(wallThickness);
        conduitWallPaint.setColor(colorPipeWall);
        conduitInnerPaint.setColor(colorPipeInner);

        canvas.drawCircle(centerX, centerY, conduitRadius, conduitInnerPaint);
        canvas.drawCircle(centerX, centerY, conduitRadius, conduitWallPaint);

        // 2. Bundled Conductors Inside Bore
        int totalWires = primaryCount + groundCount;
        float innerR = conduitRadius - wallThickness * 0.5f;

        if (totalWires > 0) {
            float wireRadius = innerR * 0.30f;
            if (totalWires > 6) wireRadius = innerR * 0.20f;
            else if (totalWires == 1) wireRadius = innerR * 0.50f;

            double angleStep = 2.0 * Math.PI / Math.max(1, totalWires);
            float orbitRadius = (totalWires == 1) ? 0.0f : innerR * 0.45f;

            for (int i = 0; i < totalWires; i++) {
                double rad = i * angleStep;
                float wx = (float) (centerX + orbitRadius * Math.cos(rad));
                float wy = (float) (centerY + orbitRadius * Math.sin(rad));

                int colorIdx = (i < primaryCount) ? (i % 3) : 4; // Grounds are green (index 4)
                wireInsulationPaint.setColor(PHASE_COLORS[colorIdx]);

                // Insulation circle
                canvas.drawCircle(wx, wy, wireRadius, wireInsulationPaint);

                // Wire outline for visibility
                Paint outlinePaint = new Paint();
                outlinePaint.setStyle(Paint.Style.STROKE);
                outlinePaint.setStrokeWidth(dpToPx(0.8f));
                outlinePaint.setColor(isDark ? Color.parseColor("#64748B") : Color.parseColor("#CBD5E1"));
                canvas.drawCircle(wx, wy, wireRadius, outlinePaint);

                // Copper core
                canvas.drawCircle(wx, wy, wireRadius * 0.45f, wireCorePaint);
            }
        }

        // 3. Right Telemetry Area
        float readoutCenterX = w * 0.72f;

        // Fill Progress Radial Gauge Arc
        float gaugeRadius = dpToPx(34);
        arcRect.set(readoutCenterX - gaugeRadius, centerY - gaugeRadius - dpToPx(14), readoutCenterX + gaugeRadius, centerY + gaugeRadius - dpToPx(14));

        arcTrackPaint.setStrokeWidth(dpToPx(6));
        arcTrackPaint.setColor(colorTrack);
        canvas.drawArc(arcRect, 135, 270, false, arcTrackPaint);

        arcFillPaint.setStrokeWidth(dpToPx(6));
        arcFillPaint.setColor(activeFillColor);
        float fillFrac = (float) Math.min(1.0, fillPercent / 100.0);
        canvas.drawArc(arcRect, 135, fillFrac * 270.0f, false, arcFillPaint);

        // Numeric Readout Inside Gauge
        textValuePaint.setTextSize(spToPx(18.0f));
        textValuePaint.setColor(colorTextPrim);
        String pctStr = String.format(java.util.Locale.US, "%.1f%%", fillPercent);
        canvas.drawText(pctStr, readoutCenterX, centerY - dpToPx(10), textValuePaint);

        textSubPaint.setTextSize(spToPx(9.5f));
        textSubPaint.setColor(colorTextSec);
        String subStr = "Max: " + String.format(java.util.Locale.US, "%.0f%%", maxAllowedPercent);
        canvas.drawText(subStr, readoutCenterX, centerY + dpToPx(3), textSubPaint);

        // Conduit Spec Below
        textSubPaint.setTextSize(spToPx(11.0f));
        textSubPaint.setColor(colorTextPrim);
        canvas.drawText(racewayLabel + " (ID " + String.format(java.util.Locale.US, "%.3f\"", conduitIdInches) + ")", readoutCenterX, centerY + dpToPx(32), textSubPaint);
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
