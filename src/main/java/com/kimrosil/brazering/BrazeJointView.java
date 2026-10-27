package com.kimrosil.brazering;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/**
 * BrazeJointView
 * Real-time 2D architectural cross-section diagram of copper tube and fitting socket,
 * capillary gap penetration, alloy meniscus fillet, and preform wire ring placement.
 */
public class BrazeJointView extends View {

    private double tubeOdInches = 0.875;
    private double socketDepthInches = 0.75;
    private double clearanceInches = 0.003;
    private double alloyGrams = 1.85;
    private boolean isOptimal = true;

    private final Paint copperFittingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint copperTubePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint alloyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint silverHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimensionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BrazeJointView(Context context) {
        super(context);
        init();
    }

    public BrazeJointView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BrazeJointView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        copperFittingPaint.setStyle(Paint.Style.FILL);
        copperFittingPaint.setColor(Color.parseColor("#9A3412"));

        copperTubePaint.setStyle(Paint.Style.FILL);
        copperTubePaint.setColor(Color.parseColor("#EA580C"));

        alloyPaint.setStyle(Paint.Style.FILL);
        alloyPaint.setColor(Color.parseColor("#E2E8F0"));

        silverHighlightPaint.setStyle(Paint.Style.STROKE);
        silverHighlightPaint.setStrokeWidth(dp(1.5f));
        silverHighlightPaint.setColor(Color.parseColor("#FDE047"));

        dimensionPaint.setStyle(Paint.Style.STROKE);
        dimensionPaint.setStrokeWidth(dp(1.2f));
        dimensionPaint.setColor(Color.parseColor("#38BDF8"));

        textPaint.setTextSize(sp(10f));
        textPaint.setColor(Color.parseColor("#94A3B8"));
        textPaint.setTextAlign(Paint.Align.CENTER);

        badgePaint.setStyle(Paint.Style.FILL);
    }

    public void updateData(double tubeOd, double socketDepth, double clearance, double grams, boolean optimal) {
        this.tubeOdInches = tubeOd;
        this.socketDepthInches = socketDepth;
        this.clearanceInches = clearance;
        this.alloyGrams = grams;
        this.isOptimal = optimal;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        // Scaling factors
        float tubeHalfH = dp(24);
        float fittingWall = dp(10);
        float tubeWall = dp(7);
        float insertW = dp(80);

        float cupLeft = cx - insertW * 0.5f;
        float cupRight = cx + insertW * 0.5f + dp(30);
        float tubeLeft = cupLeft - dp(50);
        float tubeRight = cx + insertW * 0.5f;

        // 1. Draw Outer Fitting Socket (Top & Bottom Walls)
        // Top fitting wall
        RectF fitTop = new RectF(cupLeft, cy - tubeHalfH - fittingWall, cupRight, cy - tubeHalfH);
        canvas.drawRect(fitTop, copperFittingPaint);

        // Bottom fitting wall
        RectF fitBottom = new RectF(cupLeft, cy + tubeHalfH, cupRight, cy + tubeHalfH + fittingWall);
        canvas.drawRect(fitBottom, copperFittingPaint);

        // Fitting internal stop shoulder at tubeRight
        RectF shoulder = new RectF(tubeRight, cy - tubeHalfH - fittingWall, cupRight, cy + tubeHalfH + fittingWall);
        canvas.drawRect(shoulder, copperFittingPaint);

        // 2. Draw Inserted Copper Tube (Top & Bottom Walls)
        RectF tubeTop = new RectF(tubeLeft, cy - tubeHalfH, tubeRight, cy - tubeHalfH + tubeWall);
        canvas.drawRect(tubeTop, copperTubePaint);

        RectF tubeBottom = new RectF(tubeLeft, cy + tubeHalfH - tubeWall, tubeRight, cy + tubeHalfH);
        canvas.drawRect(tubeBottom, copperTubePaint);

        // Tube inner refrigerant hollow
        Paint hollowPaint = new Paint();
        hollowPaint.setColor(Color.parseColor("#0C1017"));
        RectF hollow = new RectF(tubeLeft, cy - tubeHalfH + tubeWall, tubeRight, cy + tubeHalfH - tubeWall);
        canvas.drawRect(hollow, hollowPaint);

        // 3. Draw Capillary Silver Brazing Alloy Layer
        float alloyGapH = dp(3.5f);
        // Top capillary penetration channel
        RectF alloyTop = new RectF(cupLeft, cy - tubeHalfH - alloyGapH, tubeRight, cy - tubeHalfH);
        canvas.drawRect(alloyTop, alloyPaint);

        // Bottom capillary penetration channel
        RectF alloyBottom = new RectF(cupLeft, cy + tubeHalfH, tubeRight, cy + tubeHalfH + alloyGapH);
        canvas.drawRect(alloyBottom, alloyPaint);

        // External Concave Braze Fillet Cap at socket entrance (cupLeft)
        Path filletPath = new Path();
        filletPath.moveTo(cupLeft - dp(6), cy - tubeHalfH);
        filletPath.lineTo(cupLeft, cy - tubeHalfH - fittingWall * 0.7f);
        filletPath.lineTo(cupLeft, cy - tubeHalfH);
        filletPath.close();
        canvas.drawPath(filletPath, alloyPaint);

        Path filletBottom = new Path();
        filletBottom.moveTo(cupLeft - dp(6), cy + tubeHalfH);
        filletBottom.lineTo(cupLeft, cy + tubeHalfH + fittingWall * 0.7f);
        filletBottom.lineTo(cupLeft, cy + tubeHalfH);
        filletBottom.close();
        canvas.drawPath(filletBottom, alloyPaint);

        // Silver highlight line
        canvas.drawLine(cupLeft, cy - tubeHalfH - alloyGapH * 0.5f, tubeRight, cy - tubeHalfH - alloyGapH * 0.5f, silverHighlightPaint);

        // 4. Dimension Markers & Labels
        // Socket Depth dimension line
        dimensionPaint.setColor(Color.parseColor("#38BDF8"));
        float dimY = cy + tubeHalfH + fittingWall + dp(12);
        canvas.drawLine(cupLeft, dimY, tubeRight, dimY, dimensionPaint);
        canvas.drawLine(cupLeft, dimY - dp(3), cupLeft, dimY + dp(3), dimensionPaint);
        canvas.drawLine(tubeRight, dimY - dp(3), tubeRight, dimY + dp(3), dimensionPaint);

        textPaint.setTextSize(sp(9.5f));
        textPaint.setColor(Color.parseColor("#38BDF8"));
        canvas.drawText(String.format(Locale.US, "DEPTH: %.2f\"", socketDepthInches), cx, dimY + dp(11), textPaint);

        // Capillary Clearance callout tag
        float tagX = cupLeft + dp(28);
        float tagY = cy - tubeHalfH - fittingWall - dp(8);
        textPaint.setColor(isOptimal ? Color.parseColor("#10B981") : Color.parseColor("#F59E0B"));
        canvas.drawText(String.format(Locale.US, "GAP: %.3f\" (CAPILLARY DRAW)", clearanceInches), tagX, tagY, textPaint);

        // Tube OD header tag
        textPaint.setTextSize(sp(9f));
        textPaint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawText(String.format(Locale.US, "COPPER TUBE %.3f\" OD", tubeOdInches), tubeLeft + dp(20), cy - tubeHalfH - dp(6), textPaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
