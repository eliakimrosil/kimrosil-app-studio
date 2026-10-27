package com.kimrosil.dosecal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class SyringeGaugeView extends View {

    private double targetVolumeMl = 3.2;
    private double maxCapacityMl = 5.0;

    private Paint barrelPaint;
    private Paint barrelBorderPaint;
    private Paint fluidPaint;
    private Paint meniscusPaint;
    private Paint tickMajorPaint;
    private Paint tickMinorPaint;
    private Paint textPaint;
    private Paint badgePaint;
    private Paint badgeTextPaint;
    private Paint plungerPaint;
    private Paint stopperPaint;

    private final RectF barrelRect = new RectF();
    private final RectF fluidRect = new RectF();
    private final Path nozzlePath = new Path();
    private final Path badgePath = new Path();

    public SyringeGaugeView(Context context) {
        super(context);
        init();
    }

    public SyringeGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SyringeGaugeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barrelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barrelPaint.setStyle(Paint.Style.FILL);

        barrelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barrelBorderPaint.setStyle(Paint.Style.STROKE);
        barrelBorderPaint.setStrokeWidth(dpToPx(1.5f));

        fluidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fluidPaint.setStyle(Paint.Style.FILL);

        meniscusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        meniscusPaint.setStyle(Paint.Style.STROKE);
        meniscusPaint.setStrokeWidth(dpToPx(2.5f));

        tickMajorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickMajorPaint.setStyle(Paint.Style.STROKE);
        tickMajorPaint.setStrokeWidth(dpToPx(1.5f));

        tickMinorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickMinorPaint.setStyle(Paint.Style.STROKE);
        tickMinorPaint.setStrokeWidth(dpToPx(1f));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(spToPx(11));
        textPaint.setTextAlign(Paint.Align.CENTER);

        badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setStyle(Paint.Style.FILL);

        badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeTextPaint.setColor(0xFFFFFFFF);
        badgeTextPaint.setTextSize(spToPx(12));
        badgeTextPaint.setFakeBoldText(true);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);

        plungerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        plungerPaint.setStyle(Paint.Style.FILL);

        stopperPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stopperPaint.setStyle(Paint.Style.FILL);
    }

    public void setVolume(double volumeMl) {
        this.targetVolumeMl = Math.max(0, volumeMl);
        // Dynamically choose optimal oral syringe capacity
        if (targetVolumeMl <= 1.0) {
            maxCapacityMl = 1.0;
        } else if (targetVolumeMl <= 3.0) {
            maxCapacityMl = 3.0;
        } else if (targetVolumeMl <= 5.0) {
            maxCapacityMl = 5.0;
        } else if (targetVolumeMl <= 10.0) {
            maxCapacityMl = 10.0;
        } else if (targetVolumeMl <= 20.0) {
            maxCapacityMl = 20.0;
        } else {
            maxCapacityMl = Math.ceil(targetVolumeMl / 10.0) * 10.0;
        }
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

        // Theme colors
        int colorBarrelBg = isDark ? 0xFF14241D : 0xFFEAF5EE;
        int colorBorder = isDark ? 0xFF2A4839 : 0xFFBEDFD0;
        int colorFluidGrad1 = isDark ? 0xFF34D399 : 0xFF10B981;
        int colorFluidGrad2 = isDark ? 0xFF059669 : 0xFF047857;
        int colorMeniscus = isDark ? 0xFF6EE7B7 : 0xFF065F46;
        int colorTicks = isDark ? 0xFF7DA895 : 0xFF3B6B55;
        int colorText = isDark ? 0xFF9DC4B3 : 0xFF2E5343;
        int colorPlunger = isDark ? 0xFF475569 : 0xFF94A3B8;
        int colorStopper = isDark ? 0xFF1E293B : 0xFF334155;
        int colorBadge = isDark ? 0xFF059669 : 0xFF047857;

        barrelPaint.setColor(colorBarrelBg);
        barrelBorderPaint.setColor(colorBorder);
        meniscusPaint.setColor(colorMeniscus);
        tickMajorPaint.setColor(colorTicks);
        tickMinorPaint.setColor(colorTicks);
        textPaint.setColor(colorText);
        plungerPaint.setColor(colorPlunger);
        stopperPaint.setColor(colorStopper);
        badgePaint.setColor(colorBadge);

        // Layout Geometry
        float pLeft = dpToPx(16);
        float pRight = dpToPx(16);
        float pTop = dpToPx(38); // Room above barrel for volume badge
        float pBottom = dpToPx(24);

        float nozzleWidth = dpToPx(12);
        float nozzleHeight = dpToPx(10);
        float flangeWidth = dpToPx(8);
        float flangeHeight = dpToPx(56);
        float thumbRestWidth = dpToPx(8);
        float thumbRestHeight = dpToPx(44);

        float barrelLeft = pLeft + nozzleWidth;
        float barrelRight = w - pRight - dpToPx(36); // Reserve space for plunger draw
        float barrelTop = pTop;
        float barrelBottom = h - pBottom;
        float barrelHeight = barrelBottom - barrelTop;
        float barrelLength = barrelRight - barrelLeft;

        barrelRect.set(barrelLeft, barrelTop, barrelRight, barrelBottom);

        // 1. Draw Syringe Luer Nozzle (left tip)
        float nozzleTop = barrelTop + (barrelHeight - nozzleHeight) / 2f;
        float nozzleBottom = nozzleTop + nozzleHeight;
        nozzlePath.reset();
        nozzlePath.moveTo(pLeft, nozzleTop + dpToPx(1.5f));
        nozzlePath.lineTo(barrelLeft, nozzleTop);
        nozzlePath.lineTo(barrelLeft, nozzleBottom);
        nozzlePath.lineTo(pLeft, nozzleBottom - dpToPx(1.5f));
        nozzlePath.close();

        canvas.drawPath(nozzlePath, barrelPaint);
        canvas.drawPath(nozzlePath, barrelBorderPaint);

        // 2. Draw Syringe Barrel Background
        canvas.drawRoundRect(barrelRect, dpToPx(4), dpToPx(4), barrelPaint);

        // 3. Draw Fluid Fill
        float fillRatio = (float) Math.min(1.0, Math.max(0.0, targetVolumeMl / maxCapacityMl));
        float fillRight = barrelLeft + (barrelLength * fillRatio);

        if (fillRatio > 0.001f) {
            fluidRect.set(barrelLeft, barrelTop + dpToPx(1.5f), fillRight, barrelBottom - dpToPx(1.5f));
            fluidPaint.setShader(new LinearGradient(
                    barrelLeft, barrelTop, barrelLeft, barrelBottom,
                    colorFluidGrad1, colorFluidGrad2, Shader.TileMode.CLAMP
            ));
            canvas.drawRect(fluidRect, fluidPaint);

            // Meniscus line
            canvas.drawLine(fillRight, barrelTop + dpToPx(1.5f), fillRight, barrelBottom - dpToPx(1.5f), meniscusPaint);
        }

        // 4. Draw Stopper and Plunger Shaft
        float stopperLeft = fillRight;
        float stopperRight = Math.min(barrelRight, stopperLeft + dpToPx(7));
        if (stopperRight > stopperLeft) {
            canvas.drawRect(stopperLeft, barrelTop + dpToPx(2), stopperRight, barrelBottom - dpToPx(2), stopperPaint);
        }

        // Plunger shaft extending to the right
        float shaftTop = barrelTop + barrelHeight * 0.38f;
        float shaftBottom = barrelBottom - barrelHeight * 0.38f;
        float shaftLeft = Math.max(stopperRight, barrelLeft + dpToPx(4));
        float shaftRight = barrelRight + dpToPx(24);
        canvas.drawRect(shaftLeft, shaftTop, shaftRight, shaftBottom, plungerPaint);

        // Plunger thumb rest
        float thumbRestTop = barrelTop + (barrelHeight - thumbRestHeight) / 2f;
        float thumbRestBottom = thumbRestTop + thumbRestHeight;
        canvas.drawRoundRect(
                shaftRight, thumbRestTop, shaftRight + thumbRestWidth, thumbRestBottom,
                dpToPx(3), dpToPx(3), plungerPaint
        );

        // 5. Draw Barrel Outer Stroke & Finger Flange
        canvas.drawRoundRect(barrelRect, dpToPx(4), dpToPx(4), barrelBorderPaint);

        // Finger Flange at right edge of barrel
        float flangeTop = barrelTop + (barrelHeight - flangeHeight) / 2f;
        float flangeBottom = flangeTop + flangeHeight;
        canvas.drawRoundRect(
                barrelRight - dpToPx(1), flangeTop, barrelRight + flangeWidth, flangeBottom,
                dpToPx(3), dpToPx(3), barrelPaint
        );
        canvas.drawRoundRect(
                barrelRight - dpToPx(1), flangeTop, barrelRight + flangeWidth, flangeBottom,
                dpToPx(3), dpToPx(3), barrelBorderPaint
        );

        // 6. Draw Syringe Graduation Ticks and Scale Numbers
        int majorSteps = (int) Math.round(maxCapacityMl);
        if (majorSteps <= 0) majorSteps = 5;

        // Subdivisions: 5 or 10 subdivisions per major step
        int subDiv = (maxCapacityMl <= 3.0) ? 10 : (maxCapacityMl <= 5.0 ? 5 : 2);
        int totalTicks = majorSteps * subDiv;

        for (int i = 0; i <= totalTicks; i++) {
            float tickFraction = (float) i / totalTicks;
            float tickX = barrelLeft + (barrelLength * tickFraction);
            boolean isMajor = (i % subDiv == 0);
            boolean isMid = (subDiv % 2 == 0) && (i % (subDiv / 2) == 0) && !isMajor;

            float tickLength;
            if (isMajor) {
                tickLength = dpToPx(11);
                canvas.drawLine(tickX, barrelBottom, tickX, barrelBottom - tickLength, tickMajorPaint);

                // Label below barrel
                double val = (i / (double) subDiv);
                String labelStr = (maxCapacityMl <= 1.0) ? String.format(Locale.US, "%.1f", val) : String.format(Locale.US, "%.0f", val);
                canvas.drawText(labelStr, tickX, barrelBottom + dpToPx(15), textPaint);
            } else if (isMid) {
                tickLength = dpToPx(7);
                canvas.drawLine(tickX, barrelBottom, tickX, barrelBottom - tickLength, tickMinorPaint);
            } else {
                tickLength = dpToPx(4.5f);
                canvas.drawLine(tickX, barrelBottom, tickX, barrelBottom - tickLength, tickMinorPaint);
            }
        }

        // 7. Draw Floating Volume Callout Badge above Meniscus
        if (targetVolumeMl > 0) {
            String badgeStr = String.format(Locale.US, "%.1f mL", targetVolumeMl);
            float badgeTextWidth = badgeTextPaint.measureText(badgeStr);
            float badgeWidth = badgeTextWidth + dpToPx(16);
            float badgeHeight = dpToPx(24);

            float badgeCenterX = Math.max(barrelLeft + badgeWidth / 2f, Math.min(barrelRight, fillRight));
            float badgeLeft = badgeCenterX - badgeWidth / 2f;
            float badgeRight = badgeCenterX + badgeWidth / 2f;
            float badgeTop = barrelTop - badgeHeight - dpToPx(8);
            float badgeBottom = barrelTop - dpToPx(8);

            // Badge capsule
            canvas.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, dpToPx(12), dpToPx(12), badgePaint);

            // Little pointer triangle towards meniscus
            badgePath.reset();
            badgePath.moveTo(badgeCenterX - dpToPx(4), badgeBottom);
            badgePath.lineTo(badgeCenterX + dpToPx(4), badgeBottom);
            badgePath.lineTo(badgeCenterX, barrelTop - dpToPx(2));
            badgePath.close();
            canvas.drawPath(badgePath, badgePaint);

            // Badge text
            float textY = badgeTop + (badgeHeight / 2f) - ((badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f);
            canvas.drawText(badgeStr, badgeCenterX, textY, badgeTextPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
