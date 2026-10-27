package com.kimrosil.tapergauge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class TaperCrossSectionView extends View {

    private double majorD = 1.0;
    private double minorD = 0.6;
    private double taperL = 2.5;
    private double halfAngleDeg = 4.574;
    private String unitLabel = "in";

    private Paint workpiecePaint;
    private Paint workpieceStrokePaint;
    private Paint highlightPaint;
    private Paint axisPaint;
    private Paint dimensionPaint;
    private Paint dimensionLinePaint;
    private Paint angleArcPaint;
    private Paint angleTextPaint;

    private final Path workpiecePath = new Path();
    private final Path centerHolePath = new Path();
    private final RectF arcRect = new RectF();

    public TaperCrossSectionView(Context context) {
        super(context);
        init();
    }

    public TaperCrossSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TaperCrossSectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        workpiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        workpiecePaint.setStyle(Paint.Style.FILL);

        workpieceStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        workpieceStrokePaint.setStyle(Paint.Style.STROKE);
        workpieceStrokePaint.setStrokeWidth(dpToPx(1.8f));

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setStyle(Paint.Style.FILL);

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setStrokeWidth(dpToPx(1.2f));
        axisPaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(8), dpToPx(4), dpToPx(2), dpToPx(4)}, 0));

        dimensionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimensionPaint.setTextSize(spToPx(10.5f));
        dimensionPaint.setTextAlign(Paint.Align.CENTER);

        dimensionLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimensionLinePaint.setStyle(Paint.Style.STROKE);
        dimensionLinePaint.setStrokeWidth(dpToPx(1.2f));

        angleArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        angleArcPaint.setStyle(Paint.Style.STROKE);
        angleArcPaint.setStrokeWidth(dpToPx(2f));

        angleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        angleTextPaint.setTextSize(spToPx(11f));
        angleTextPaint.setFakeBoldText(true);
        angleTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void updateTaper(double dMajor, double dMinor, double length, double angleDeg, String unit) {
        this.majorD = Math.max(0.1, dMajor);
        this.minorD = Math.max(0.01, dMinor);
        this.taperL = Math.max(0.1, length);
        this.halfAngleDeg = angleDeg;
        this.unitLabel = unit;
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

        int colorMetal1 = isDark ? 0xFF1E293B : 0xFFE2E8F0;
        int colorMetal2 = isDark ? 0xFF0F172A : 0xFFCBD5E1;
        int colorStroke = isDark ? 0xFF38BDF8 : 0xFF0284C7;
        int colorAxis = isDark ? 0xFF64748B : 0xFF94A3B8;
        int colorDim = isDark ? 0xFFFBBF24 : 0xFFD97706;
        int colorAngle = isDark ? 0xFF38BDF8 : 0xFF0284C7;

        workpieceStrokePaint.setColor(colorStroke);
        axisPaint.setColor(colorAxis);
        dimensionPaint.setColor(colorDim);
        dimensionLinePaint.setColor(colorDim);
        angleArcPaint.setColor(colorAngle);
        angleTextPaint.setColor(colorAngle);

        float padLeft = dpToPx(38); // Room for Major D caliper
        float padRight = dpToPx(38); // Room for Minor d caliper
        float padTop = dpToPx(24);
        float padBottom = dpToPx(24);

        float workWidth = w - padLeft - padRight;
        float workHeight = h - padTop - padBottom;
        float centerY = padTop + workHeight / 2f;

        // Scaled geometry
        float maxAvailableH = workHeight * 0.75f;
        float ratioD = (float) (minorD / majorD);
        float rMajor = maxAvailableH / 2f;
        float rMinor = rMajor * Math.min(1.0f, Math.max(0.2f, ratioD));

        float xStart = padLeft;
        float xEnd = padLeft + workWidth * 0.85f;
        float tangWidth = workWidth * 0.15f;

        // 1. Draw Centerline Axis
        canvas.drawLine(dpToPx(10), centerY, w - dpToPx(10), centerY, axisPaint);

        // 2. Draw Tapered Workpiece Body
        workpiecePath.reset();
        workpiecePath.moveTo(xStart, centerY - rMajor);
        workpiecePath.lineTo(xEnd, centerY - rMinor);
        workpiecePath.lineTo(xEnd, centerY + rMinor);
        workpiecePath.lineTo(xStart, centerY + rMajor);
        workpiecePath.close();

        workpiecePaint.setShader(new LinearGradient(
                xStart, centerY - rMajor, xStart, centerY + rMajor,
                colorMetal1, colorMetal2, Shader.TileMode.CLAMP
        ));
        canvas.drawPath(workpiecePath, workpiecePaint);
        canvas.drawPath(workpiecePath, workpieceStrokePaint);

        // 3. Draw Tang / Shank on right
        float tangH = rMinor * 0.65f;
        canvas.drawRect(xEnd, centerY - tangH, xEnd + tangWidth, centerY + tangH, workpiecePaint);
        canvas.drawRect(xEnd, centerY - tangH, xEnd + tangWidth, centerY + tangH, workpieceStrokePaint);

        // 4. Draw Center Hole Drill Cone Indent on Left
        float holeDepth = dpToPx(8);
        centerHolePath.reset();
        centerHolePath.moveTo(xStart, centerY - dpToPx(7));
        centerHolePath.lineTo(xStart + holeDepth, centerY);
        centerHolePath.lineTo(xStart, centerY + dpToPx(7));
        centerHolePath.close();

        Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        holePaint.setColor(isDark ? 0xFF090D14 : 0xFF94A3B8);
        canvas.drawPath(centerHolePath, holePaint);
        canvas.drawPath(centerHolePath, axisPaint);

        // 5. Draw Major Diameter (D) Caliper on Left
        float calLeftX = xStart - dpToPx(12);
        canvas.drawLine(calLeftX, centerY - rMajor, calLeftX, centerY + rMajor, dimensionLinePaint);
        canvas.drawLine(calLeftX - dpToPx(3), centerY - rMajor, xStart + dpToPx(2), centerY - rMajor, dimensionLinePaint);
        canvas.drawLine(calLeftX - dpToPx(3), centerY + rMajor, xStart + dpToPx(2), centerY + rMajor, dimensionLinePaint);
        canvas.drawText(String.format(Locale.US, "D: %.3f", majorD), calLeftX - dpToPx(2), centerY + dpToPx(3), dimensionPaint);

        // 6. Draw Minor Diameter (d) Caliper on Right
        float calRightX = xEnd + tangWidth + dpToPx(10);
        canvas.drawLine(calRightX, centerY - rMinor, calRightX, centerY + rMinor, dimensionLinePaint);
        canvas.drawLine(xEnd - dpToPx(2), centerY - rMinor, calRightX + dpToPx(3), centerY - rMinor, dimensionLinePaint);
        canvas.drawLine(xEnd - dpToPx(2), centerY + rMinor, calRightX + dpToPx(3), centerY + rMinor, dimensionLinePaint);
        canvas.drawText(String.format(Locale.US, "d: %.3f", minorD), calRightX + dpToPx(2), centerY + dpToPx(3), dimensionPaint);

        // 7. Draw Swivel Angle Arc Indicator (α)
        float arcRadius = dpToPx(36);
        arcRect.set(xEnd - arcRadius, centerY - rMinor - arcRadius, xEnd + arcRadius, centerY - rMinor + arcRadius);
        canvas.drawArc(arcRect, 180, (float) Math.min(60.0, Math.max(3.0, halfAngleDeg * 3.0)), false, angleArcPaint);
        canvas.drawText(String.format(Locale.US, "α = %.2f°", halfAngleDeg), xEnd - dpToPx(55), centerY - rMinor - dpToPx(8), angleTextPaint);

        // 8. Taper Length Dimension at Bottom
        float lenY = centerY + rMajor + dpToPx(12);
        canvas.drawLine(xStart, lenY, xEnd, lenY, dimensionLinePaint);
        canvas.drawLine(xStart, lenY - dpToPx(3), xStart, lenY + dpToPx(3), dimensionLinePaint);
        canvas.drawLine(xEnd, lenY - dpToPx(3), xEnd, lenY + dpToPx(3), dimensionLinePaint);
        canvas.drawText(String.format(Locale.US, "L = %.3f %s", taperL, unitLabel), (xStart + xEnd) / 2f, lenY + dpToPx(10), dimensionPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
