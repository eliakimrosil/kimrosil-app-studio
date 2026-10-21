package com.kimrosil.swagedie;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class SwageDieCanvasView extends View {

    private int mMode = 0; // 0=Punch, 1=Swage, 2=Brake

    // Telemetry Values
    private double mPunchDim1 = 20.0;
    private double mThickness = 2.0;
    private double mClearancePerSide = 0.16;
    private double mTotalClearance = 0.32;
    private double mDieOpening = 20.32;

    // Swage values
    private double mInitialOd = 25.4;
    private double mTargetOd = 20.0;
    private double mSwageAngle = 7.5;
    private double mAreaReductionPct = 21.3;

    // Brake values
    private double mVDieWidth = 16.0;
    private double mInsideRadius = 2.5;

    // Machine Tonnage Load Bar
    private double mRequiredTonnage = 12.5;
    private double mRatedMachineTonnage = 30.0;

    // Paints
    private Paint mPaintPunch;
    private Paint mPaintDie;
    private Paint mPaintSheet;
    private Paint mPaintAccent;
    private Paint mPaintGrid;
    private Paint mPaintText;
    private Paint mPaintSubtext;
    private Paint mPaintBarBg;
    private Paint mPaintBarFill;

    private boolean mIsDarkTheme = true;

    public SwageDieCanvasView(Context context) {
        super(context);
        init();
    }

    public SwageDieCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwageDieCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        mIsDarkTheme = (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES);

        mPaintPunch = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintPunch.setStyle(Paint.Style.FILL_AND_STROKE);

        mPaintDie = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintDie.setStyle(Paint.Style.FILL_AND_STROKE);

        mPaintSheet = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintSheet.setStyle(Paint.Style.FILL_AND_STROKE);

        mPaintAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintAccent.setStyle(Paint.Style.STROKE);
        mPaintAccent.setStrokeWidth(dpToPx(1.5f));

        mPaintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintGrid.setStyle(Paint.Style.STROKE);
        mPaintGrid.setStrokeWidth(dpToPx(1.0f));
        mPaintGrid.setPathEffect(new DashPathEffect(new float[]{dpToPx(4), dpToPx(4)}, 0));

        mPaintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintText.setTextSize(dpToPx(12f));
        mPaintText.setTypeface(android.graphics.Typeface.MONOSPACE);

        mPaintSubtext = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintSubtext.setTextSize(dpToPx(10f));
        mPaintSubtext.setTypeface(android.graphics.Typeface.MONOSPACE);

        mPaintBarBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintBarBg.setStyle(Paint.Style.FILL);

        mPaintBarFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintBarFill.setStyle(Paint.Style.FILL);

        updateColors();
    }

    public void updateTheme(boolean isDark) {
        mIsDarkTheme = isDark;
        updateColors();
        invalidate();
    }

    private void updateColors() {
        if (mIsDarkTheme) {
            mPaintPunch.setColor(Color.parseColor("#0C4A6E"));
            mPaintDie.setColor(Color.parseColor("#1E293B"));
            mPaintSheet.setColor(Color.parseColor("#475569"));
            mPaintAccent.setColor(Color.parseColor("#00E5FF"));
            mPaintGrid.setColor(Color.parseColor("#334155"));
            mPaintText.setColor(Color.parseColor("#38BDF8"));
            mPaintSubtext.setColor(Color.parseColor("#94A3B8"));
            mPaintBarBg.setColor(Color.parseColor("#1E293B"));
        } else {
            mPaintPunch.setColor(Color.parseColor("#BAE6FD"));
            mPaintDie.setColor(Color.parseColor("#E2E8F0"));
            mPaintSheet.setColor(Color.parseColor("#94A3B8"));
            mPaintAccent.setColor(Color.parseColor("#0284C7"));
            mPaintGrid.setColor(Color.parseColor("#CBD5E1"));
            mPaintText.setColor(Color.parseColor("#0284C7"));
            mPaintSubtext.setColor(Color.parseColor("#64748B"));
            mPaintBarBg.setColor(Color.parseColor("#E2E8F0"));
        }
    }

    public void setPunchData(double punchDim, double thickness, double clrPerSide, double totalClr,
                             double dieOpening, double reqTonnage, double machineTonnage) {
        mMode = 0;
        mPunchDim1 = Math.max(0.1, punchDim);
        mThickness = Math.max(0.1, thickness);
        mClearancePerSide = Math.max(0.001, clrPerSide);
        mTotalClearance = Math.max(0.002, totalClr);
        mDieOpening = Math.max(0.1, dieOpening);
        mRequiredTonnage = reqTonnage;
        mRatedMachineTonnage = Math.max(1.0, machineTonnage);
        invalidate();
    }

    public void setSwageData(double initOd, double targetOd, double angle, double reductionPct,
                             double reqTonnage, double machineTonnage) {
        mMode = 1;
        mInitialOd = Math.max(0.1, initOd);
        mTargetOd = Math.max(0.1, targetOd);
        mSwageAngle = angle;
        mAreaReductionPct = reductionPct;
        mRequiredTonnage = reqTonnage;
        mRatedMachineTonnage = Math.max(1.0, machineTonnage);
        invalidate();
    }

    public void setBrakeData(double thickness, double vDie, double insideRadius,
                             double reqTonnage, double machineTonnage) {
        mMode = 2;
        mThickness = Math.max(0.1, thickness);
        mVDieWidth = Math.max(1.0, vDie);
        mInsideRadius = Math.max(0.1, insideRadius);
        mRequiredTonnage = reqTonnage;
        mRatedMachineTonnage = Math.max(1.0, machineTonnage);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        int gaugeHeight = (int) dpToPx(38);
        int schematicHeight = height - gaugeHeight;

        // Draw HUD Schematics based on Mode
        if (mMode == 0) {
            drawPunchDieSchematic(canvas, width, schematicHeight);
        } else if (mMode == 1) {
            drawSwageSchematic(canvas, width, schematicHeight);
        } else {
            drawBrakeSchematic(canvas, width, schematicHeight);
        }

        // Draw Tonnage Load Telemetry Bar at bottom
        drawTonnageLoadBar(canvas, width, height, gaugeHeight);
    }

    private void drawPunchDieSchematic(Canvas canvas, int width, int height) {
        float cx = width / 2.0f;
        float sheetY = height * 0.52f;
        float sheetThicknessPx = Math.max(dpToPx(6), Math.min(dpToPx(16), (float)(mThickness * dpToPx(3.5f))));

        // 1. Draw Centerline Grid
        canvas.drawLine(cx, dpToPx(10), cx, height - dpToPx(10), mPaintGrid);

        // 2. Punch Representation (Upper tool)
        float punchWidthPx = Math.max(dpToPx(48), Math.min(width * 0.45f, (float)(mPunchDim1 * dpToPx(3.0f))));
        float punchLeft = cx - (punchWidthPx / 2.0f);
        float punchRight = cx + (punchWidthPx / 2.0f);
        float punchTop = dpToPx(14);
        float punchBottom = sheetY - dpToPx(4); // Punch tip poised just above sheet

        Path punchPath = new Path();
        punchPath.moveTo(punchLeft - dpToPx(10), punchTop);
        punchPath.lineTo(punchRight + dpToPx(10), punchTop);
        punchPath.lineTo(punchRight, punchTop + dpToPx(18));
        punchPath.lineTo(punchRight, punchBottom);
        punchPath.lineTo(punchLeft, punchBottom);
        punchPath.lineTo(punchLeft, punchTop + dpToPx(18));
        punchPath.close();
        canvas.drawPath(punchPath, mPaintPunch);

        // Punch Outline & Accent
        mPaintAccent.setStyle(Paint.Style.STROKE);
        canvas.drawPath(punchPath, mPaintAccent);

        // 3. Sheet Metal Blank
        float sheetLeft = dpToPx(24);
        float sheetRight = width - dpToPx(24);
        RectF sheetRect = new RectF(sheetLeft, sheetY, sheetRight, sheetY + sheetThicknessPx);
        canvas.drawRoundRect(sheetRect, dpToPx(2), dpToPx(2), mPaintSheet);

        // 4. Lower Die Block
        float clrPx = Math.max(dpToPx(4), (float)(mClearancePerSide * dpToPx(12.0f)));
        float dieOpeningPx = punchWidthPx + (clrPx * 2.0f);
        float dieHoleLeft = cx - (dieOpeningPx / 2.0f);
        float dieHoleRight = cx + (dieOpeningPx / 2.0f);
        float dieTop = sheetY + sheetThicknessPx + dpToPx(2);
        float dieBottom = height - dpToPx(6);

        // Left Die Block
        Path leftDiePath = new Path();
        leftDiePath.moveTo(sheetLeft, dieTop);
        leftDiePath.lineTo(dieHoleLeft, dieTop);
        leftDiePath.lineTo(dieHoleLeft, dieTop + dpToPx(16)); // straight land
        leftDiePath.lineTo(dieHoleLeft - dpToPx(8), dieBottom); // relief angle taper
        leftDiePath.lineTo(sheetLeft, dieBottom);
        leftDiePath.close();
        canvas.drawPath(leftDiePath, mPaintDie);
        canvas.drawPath(leftDiePath, mPaintAccent);

        // Right Die Block
        Path rightDiePath = new Path();
        rightDiePath.moveTo(dieHoleRight, dieTop);
        rightDiePath.lineTo(sheetRight, dieTop);
        rightDiePath.lineTo(sheetRight, dieBottom);
        rightDiePath.lineTo(dieHoleRight + dpToPx(8), dieBottom); // relief angle taper
        rightDiePath.lineTo(dieHoleRight, dieTop + dpToPx(16)); // straight land
        rightDiePath.close();
        canvas.drawPath(rightDiePath, mPaintDie);
        canvas.drawPath(rightDiePath, mPaintAccent);

        // Clearance Dimension Markers
        mPaintAccent.setStrokeWidth(dpToPx(1.0f));
        // Left clearance arrow
        canvas.drawLine(punchLeft, sheetY - dpToPx(8), dieHoleLeft, sheetY - dpToPx(8), mPaintAccent);
        // Right clearance arrow
        canvas.drawLine(punchRight, sheetY - dpToPx(8), dieHoleRight, sheetY - dpToPx(8), mPaintAccent);

        // Technical HUD Labels
        mPaintText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(String.format(java.util.Locale.US, "PUNCH: %.2f mm", mPunchDim1), dpToPx(14), dpToPx(22), mPaintText);
        canvas.drawText(String.format(java.util.Locale.US, "DIE OP: %.2f mm", mDieOpening), dpToPx(14), dpToPx(38), mPaintText);

        mPaintSubtext.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(java.util.Locale.US, "CLR/SIDE: %.3f mm", mClearancePerSide), width - dpToPx(14), dpToPx(22), mPaintSubtext);
        canvas.drawText(String.format(java.util.Locale.US, "LAND: 3.0mm | DRAFT: 0.5°", mClearancePerSide), width - dpToPx(14), dpToPx(38), mPaintSubtext);
    }

    private void drawSwageSchematic(Canvas canvas, int width, int height) {
        float cx = width / 2.0f;
        float cy = height * 0.48f;

        // Horizontal Centerline Grid
        canvas.drawLine(dpToPx(10), cy, width - dpToPx(10), cy, mPaintGrid);

        float initRadiusPx = Math.max(dpToPx(24), Math.min(height * 0.35f, (float)(mInitialOd * dpToPx(1.6f))));
        float targetRadiusPx = Math.max(dpToPx(14), initRadiusPx * (float)(mTargetOd / Math.max(0.1, mInitialOd)));

        float xStart = dpToPx(20);
        float xDieStart = width * 0.38f;
        float xDieEnd = width * 0.65f;
        float xEnd = width - dpToPx(20);

        // Upper Tube Profile
        Path topTube = new Path();
        topTube.moveTo(xStart, cy - initRadiusPx);
        topTube.lineTo(xDieStart, cy - initRadiusPx);
        topTube.lineTo(xDieEnd, cy - targetRadiusPx);
        topTube.lineTo(xEnd, cy - targetRadiusPx);
        topTube.lineTo(xEnd, cy - targetRadiusPx + dpToPx(6));
        topTube.lineTo(xDieEnd, cy - targetRadiusPx + dpToPx(6));
        topTube.lineTo(xDieStart, cy - initRadiusPx + dpToPx(6));
        topTube.lineTo(xStart, cy - initRadiusPx + dpToPx(6));
        topTube.close();
        canvas.drawPath(topTube, mPaintPunch);
        canvas.drawPath(topTube, mPaintAccent);

        // Lower Tube Profile (Symmetrical)
        Path bottomTube = new Path();
        bottomTube.moveTo(xStart, cy + initRadiusPx);
        bottomTube.lineTo(xDieStart, cy + initRadiusPx);
        bottomTube.lineTo(xDieEnd, cy + targetRadiusPx);
        bottomTube.lineTo(xEnd, cy + targetRadiusPx);
        bottomTube.lineTo(xEnd, cy + targetRadiusPx - dpToPx(6));
        bottomTube.lineTo(xDieEnd, cy + targetRadiusPx - dpToPx(6));
        bottomTube.lineTo(xDieStart, cy + initRadiusPx - dpToPx(6));
        bottomTube.lineTo(xStart, cy + initRadiusPx - dpToPx(6));
        bottomTube.close();
        canvas.drawPath(bottomTube, mPaintPunch);
        canvas.drawPath(bottomTube, mPaintAccent);

        // Upper & Lower Swage Die Blocks (Enclosing the taper zone)
        Path topDie = new Path();
        topDie.moveTo(xDieStart - dpToPx(6), dpToPx(14));
        topDie.lineTo(xDieEnd + dpToPx(10), dpToPx(14));
        topDie.lineTo(xDieEnd + dpToPx(10), cy - targetRadiusPx - dpToPx(2));
        topDie.lineTo(xDieEnd, cy - targetRadiusPx - dpToPx(2));
        topDie.lineTo(xDieStart, cy - initRadiusPx - dpToPx(2));
        topDie.lineTo(xDieStart - dpToPx(6), cy - initRadiusPx - dpToPx(2));
        topDie.close();
        canvas.drawPath(topDie, mPaintDie);
        canvas.drawPath(topDie, mPaintAccent);

        Path bottomDie = new Path();
        bottomDie.moveTo(xDieStart - dpToPx(6), height - dpToPx(14));
        bottomDie.lineTo(xDieEnd + dpToPx(10), height - dpToPx(14));
        bottomDie.lineTo(xDieEnd + dpToPx(10), cy + targetRadiusPx + dpToPx(2));
        bottomDie.lineTo(xDieEnd, cy + targetRadiusPx + dpToPx(2));
        bottomDie.lineTo(xDieStart, cy + initRadiusPx + dpToPx(2));
        bottomDie.lineTo(xDieStart - dpToPx(6), cy + initRadiusPx + dpToPx(2));
        bottomDie.close();
        canvas.drawPath(bottomDie, mPaintDie);
        canvas.drawPath(bottomDie, mPaintAccent);

        // Swage Labels
        mPaintText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(String.format(java.util.Locale.US, "INIT Ø: %.2f mm", mInitialOd), dpToPx(14), dpToPx(22), mPaintText);
        canvas.drawText(String.format(java.util.Locale.US, "SWAGED Ø: %.2f mm", mTargetOd), dpToPx(14), dpToPx(38), mPaintText);

        mPaintSubtext.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(java.util.Locale.US, "AREA RED: %.1f%%", mAreaReductionPct), width - dpToPx(14), dpToPx(22), mPaintSubtext);
        canvas.drawText(String.format(java.util.Locale.US, "DIE ANGLE: %.1f°", mSwageAngle), width - dpToPx(14), dpToPx(38), mPaintSubtext);
    }

    private void drawBrakeSchematic(Canvas canvas, int width, int height) {
        float cx = width / 2.0f;
        float vDieWidthPx = Math.max(dpToPx(44), Math.min(width * 0.48f, (float)(mVDieWidth * dpToPx(2.5f))));
        float dieTopY = height * 0.55f;
        float vDepth = vDieWidthPx * 0.6f;

        // Centerline
        canvas.drawLine(cx, dpToPx(10), cx, height - dpToPx(8), mPaintGrid);

        // Lower V-Die Block
        float leftX = cx - (vDieWidthPx / 2.0f);
        float rightX = cx + (vDieWidthPx / 2.0f);

        Path vDiePath = new Path();
        vDiePath.moveTo(dpToPx(20), dieTopY);
        vDiePath.lineTo(leftX, dieTopY);
        vDiePath.lineTo(cx, dieTopY + vDepth);
        vDiePath.lineTo(rightX, dieTopY);
        vDiePath.lineTo(width - dpToPx(20), dieTopY);
        vDiePath.lineTo(width - dpToPx(20), height - dpToPx(8));
        vDiePath.lineTo(dpToPx(20), height - dpToPx(8));
        vDiePath.close();
        canvas.drawPath(vDiePath, mPaintDie);
        canvas.drawPath(vDiePath, mPaintAccent);

        // Sheet blank laid horizontally
        float thkPx = Math.max(dpToPx(4), (float)(mThickness * dpToPx(2.5f)));
        RectF sheetRect = new RectF(dpToPx(30), dieTopY - thkPx, width - dpToPx(30), dieTopY);
        canvas.drawRect(sheetRect, mPaintSheet);

        // Upper Punch Blade V-Tip
        Path punchBlade = new Path();
        punchBlade.moveTo(cx - dpToPx(16), dpToPx(14));
        punchBlade.lineTo(cx + dpToPx(16), dpToPx(14));
        punchBlade.lineTo(cx + dpToPx(16), dieTopY - thkPx - dpToPx(16));
        punchBlade.lineTo(cx, dieTopY - thkPx - dpToPx(2));
        punchBlade.lineTo(cx - dpToPx(16), dieTopY - thkPx - dpToPx(16));
        punchBlade.close();
        canvas.drawPath(punchBlade, mPaintPunch);
        canvas.drawPath(punchBlade, mPaintAccent);

        // Labels
        mPaintText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(String.format(java.util.Locale.US, "V-DIE: %.1f mm", mVDieWidth), dpToPx(14), dpToPx(22), mPaintText);
        canvas.drawText(String.format(java.util.Locale.US, "THICKNESS: %.2f mm", mThickness), dpToPx(14), dpToPx(38), mPaintText);

        mPaintSubtext.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(java.util.Locale.US, "INSIDE R: %.2f mm", mInsideRadius), width - dpToPx(14), dpToPx(22), mPaintSubtext);
        canvas.drawText(String.format(java.util.Locale.US, "MIN FLANGE: %.1f mm", mVDieWidth * 0.70), width - dpToPx(14), dpToPx(38), mPaintSubtext);
    }

    private void drawTonnageLoadBar(Canvas canvas, int width, int height, int gaugeHeight) {
        float barTop = height - gaugeHeight + dpToPx(4);
        float barBottom = height - dpToPx(8);
        float barLeft = dpToPx(16);
        float barRight = width - dpToPx(16);
        float barW = barRight - barLeft;

        // Ratio of required tonnage vs machine rated tonnage
        float ratio = (float)(mRequiredTonnage / Math.max(0.01, mRatedMachineTonnage));
        float fillW = Math.max(0, Math.min(barW, barW * Math.min(1.0f, ratio)));

        // Background Track
        RectF trackRect = new RectF(barLeft, barTop, barRight, barBottom);
        canvas.drawRoundRect(trackRect, dpToPx(6), dpToPx(6), mPaintBarBg);

        // Color coding for safety load:
        // Green < 70%, Amber 70-90%, Crimson > 90%
        int barColor;
        String statusText;
        if (ratio < 0.70f) {
            barColor = Color.parseColor("#16A34A"); // Safe Green
            statusText = "SAFE";
        } else if (ratio <= 0.90f) {
            barColor = Color.parseColor("#D97706"); // Warning Amber
            statusText = "HEAVY";
        } else {
            barColor = Color.parseColor("#DC2626"); // Hazard Red
            statusText = "OVERLOAD";
        }

        mPaintBarFill.setColor(barColor);
        RectF fillRect = new RectF(barLeft, barTop, barLeft + fillW, barBottom);
        canvas.drawRoundRect(fillRect, dpToPx(6), dpToPx(6), mPaintBarFill);

        // Monospace Load Telemetry Text overlay
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dpToPx(11f));
        textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        textPaint.setTextAlign(Paint.Align.CENTER);

        float textY = barTop + ((barBottom - barTop) / 2.0f) + dpToPx(3.5f);
        String label = String.format(java.util.Locale.US, "PRESS LOAD: %.1f T / %.0f T (%.0f%%) [%s]",
                mRequiredTonnage, mRatedMachineTonnage, ratio * 100.0f, statusText);
        canvas.drawText(label, width / 2.0f, textY, textPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
