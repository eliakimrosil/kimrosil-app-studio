package com.kimrosil.plumbbob;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * PipeSlopeDiagramView
 * Real-time 2D architectural profile diagram showing trench ground level,
 * invert depth offsets, sloped pipe drainage channel, and fall triangle.
 */
public class PipeSlopeDiagramView extends View {

    private double runLength = 20.0;     // feet or meters
    private double pitchInPerFt = 0.25;  // inches per foot
    private double startInvert = 24.0;   // inches or mm
    private double endInvert = 29.0;     // inches or mm
    private double totalFall = 5.0;      // inches or mm
    private double gradePercent = 2.08;
    private boolean isMetric = false;
    private boolean isCompliant = true;
    private String complianceText = "CODE COMPLIANT";

    private final Paint groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint datumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pipeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint liquidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();

    public PipeSlopeDiagramView(Context context) {
        super(context);
        init();
    }

    public PipeSlopeDiagramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PipeSlopeDiagramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        groundPaint.setStyle(Paint.Style.STROKE);
        groundPaint.setStrokeWidth(dp(1.8f));
        groundPaint.setColor(Color.parseColor("#64748B"));

        datumPaint.setStyle(Paint.Style.STROKE);
        datumPaint.setStrokeWidth(dp(1.2f));
        datumPaint.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));
        datumPaint.setColor(Color.parseColor("#94A3B8"));

        pipePaint.setStyle(Paint.Style.STROKE);
        pipePaint.setStrokeWidth(dp(14f));
        pipePaint.setStrokeCap(Paint.Cap.ROUND);
        pipePaint.setColor(Color.parseColor("#0284C7"));

        pipeFillPaint.setStyle(Paint.Style.STROKE);
        pipeFillPaint.setStrokeWidth(dp(10f));
        pipeFillPaint.setStrokeCap(Paint.Cap.ROUND);
        pipeFillPaint.setColor(Color.parseColor("#BAE6FD"));

        liquidPaint.setStyle(Paint.Style.STROKE);
        liquidPaint.setStrokeWidth(dp(2.5f));
        liquidPaint.setColor(Color.parseColor("#0EA5E9"));

        textPaint.setTextSize(sp(11f));
        textPaint.setColor(Color.parseColor("#94A3B8"));
        textPaint.setTextAlign(Paint.Align.CENTER);

        badgePaint.setStyle(Paint.Style.FILL);
        badgeTextPaint.setTextSize(sp(10.5f));
        badgeTextPaint.setFakeBoldText(true);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void updateData(double runLength, double pitchInPerFt, double startInvert,
                           double endInvert, double totalFall, double gradePercent,
                           boolean isMetric, boolean isCompliant, String complianceText) {
        this.runLength = runLength;
        this.pitchInPerFt = pitchInPerFt;
        this.startInvert = startInvert;
        this.endInvert = endInvert;
        this.totalFall = totalFall;
        this.gradePercent = gradePercent;
        this.isMetric = isMetric;
        this.isCompliant = isCompliant;
        this.complianceText = complianceText;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padX = dp(24);
        float groundY = dp(28);
        float pipeX1 = padX;
        float pipeX2 = w - padX;

        // Dynamic slope angle calculation based on grade
        float maxDropPx = h - groundY - dp(45);
        float minDropPx = dp(16);
        float slopeRatio = (float) Math.min(Math.max(gradePercent / 5.0, 0.1), 1.0);
        float dropPx = minDropPx + (maxDropPx - minDropPx) * slopeRatio;

        float pipeY1 = groundY + dp(24);
        float pipeY2 = pipeY1 + dropPx;

        // 1. Draw Trench Ground Surface Line with hatch marks
        canvas.drawLine(padX * 0.5f, groundY, w - (padX * 0.5f), groundY, groundPaint);
        for (float hx = padX * 0.6f; hx < w - (padX * 0.6f); hx += dp(14)) {
            canvas.drawLine(hx, groundY, hx - dp(5), groundY - dp(6), groundPaint);
        }

        // Label: TRENCH SURFACE
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(9.5f));
        textPaint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawText("SURFACE GRADE", padX * 0.5f, groundY - dp(9), textPaint);

        // 2. Vertical Invert Dimension Drop Lines
        canvas.drawLine(pipeX1, groundY, pipeX1, pipeY1, datumPaint);
        canvas.drawLine(pipeX2, groundY, pipeX2, pipeY2, datumPaint);

        // Draw Invert Depth callout badges
        String startInvStr = isMetric ? String.format("%.0f mm", startInvert) : String.format("%.1f\"", startInvert);
        String endInvStr = isMetric ? String.format("%.0f mm", endInvert) : String.format("%.1f\"", endInvert);

        drawCalloutPill(canvas, pipeX1, (groundY + pipeY1) * 0.5f, "INVERT START", startInvStr, true);
        drawCalloutPill(canvas, pipeX2, (groundY + pipeY2) * 0.5f, "INVERT END", endInvStr, false);

        // 3. Horizontal Fall Reference Triangle (Run and Drop)
        canvas.drawLine(pipeX1, pipeY1, pipeX2, pipeY1, datumPaint);
        canvas.drawLine(pipeX2, pipeY1, pipeX2, pipeY2, datumPaint);

        // Fall drop label on vertical leg
        String fallStr = isMetric ? String.format("DROP: %.0f mm", totalFall) : String.format("DROP: %.2f\"", totalFall);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(sp(10f));
        textPaint.setColor(isCompliant ? Color.parseColor("#38BDF8") : Color.parseColor("#EF4444"));
        canvas.drawText(fallStr, pipeX2 - dp(6), (pipeY1 + pipeY2) * 0.5f + dp(3), textPaint);

        // 4. Sloped Pipe Body
        int themeColor = isCompliant ? Color.parseColor("#38BDF8") : Color.parseColor("#F59E0B");
        int fillColor = isCompliant ? Color.parseColor("#0F355A") : Color.parseColor("#451A03");

        pipePaint.setColor(themeColor);
        pipeFillPaint.setColor(fillColor);

        // Draw outer pipe casing and inner core
        canvas.drawLine(pipeX1, pipeY1, pipeX2, pipeY2, pipePaint);
        canvas.drawLine(pipeX1, pipeY1, pipeX2, pipeY2, pipeFillPaint);

        // 5. Downhill Flow Direction Arrow
        float midX = (pipeX1 + pipeX2) * 0.5f;
        float midY = (pipeY1 + pipeY2) * 0.5f;
        double angle = Math.atan2(pipeY2 - pipeY1, pipeX2 - pipeX1);

        liquidPaint.setColor(isCompliant ? Color.parseColor("#00E5FF") : Color.parseColor("#F59E0B"));
        canvas.save();
        canvas.translate(midX, midY);
        canvas.rotate((float) Math.toDegrees(angle));

        // Arrow geometry
        arrowPath.reset();
        arrowPath.moveTo(dp(12), 0);
        arrowPath.lineTo(dp(4), -dp(4));
        arrowPath.lineTo(dp(4), dp(4));
        arrowPath.close();

        canvas.drawLine(-dp(16), 0, dp(12), 0, liquidPaint);
        canvas.drawPath(arrowPath, liquidPaint);
        canvas.restore();

        // 6. Center Pipe Pitch Tag Pill
        String pitchTag = isMetric ? String.format("%.2f %% Grade  (%.1f mm/m)", gradePercent, gradePercent * 10)
                : String.format("%.3f\" / ft  •  %.2f%% Grade", pitchInPerFt, gradePercent);

        float tagW = textPaint.measureText(pitchTag) + dp(18);
        float tagH = dp(20);
        float tagX = midX - tagW * 0.5f;
        float tagY = h - dp(24);

        RectF tagRect = new RectF(tagX, tagY, tagX + tagW, tagY + tagH);
        badgePaint.setColor(Color.parseColor("#111A29"));
        canvas.drawRoundRect(tagRect, dp(6), dp(6), badgePaint);

        // Outline border
        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(dp(1));
        outline.setColor(themeColor);
        canvas.drawRoundRect(tagRect, dp(6), dp(6), outline);

        badgeTextPaint.setColor(themeColor);
        canvas.drawText(pitchTag, midX, tagY + dp(14), badgeTextPaint);

        // Run Length footer label
        String runStr = isMetric ? String.format("RUN: %.2f m", runLength) : String.format("RUN: %.1f ft", runLength);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(10f));
        textPaint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawText(runStr, midX, pipeY1 - dp(6), textPaint);
    }

    private void drawCalloutPill(Canvas canvas, float x, float y, String header, String value, boolean isLeft) {
        float pillW = dp(84);
        float pillH = dp(32);
        float left = isLeft ? x + dp(6) : x - pillW - dp(6);
        float top = y - pillH * 0.5f;

        RectF rect = new RectF(left, top, left + pillW, top + pillH);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.parseColor("#182438"));
        canvas.drawRoundRect(rect, dp(6), dp(6), bg);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sp(8.5f));
        text.setColor(Color.parseColor("#94A3B8"));
        canvas.drawText(header, left + pillW * 0.5f, top + dp(12), text);

        text.setTextSize(sp(11.5f));
        text.setFakeBoldText(true);
        text.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawText(value, left + pillW * 0.5f, top + dp(26), text);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
