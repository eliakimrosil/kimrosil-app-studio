package com.kimrosil.flaregage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class FlareProfileView extends View {

    private FlareSpec.Standard mStandard = FlareSpec.Standard.SAE_45_DOUBLE;
    private FlareSpec.TubingSize mSize = FlareSpec.TubingSize.SIZE_3_16;
    private FlareSpec.Material mMaterial = FlareSpec.Material.CUNI;
    private FlareSpec.SpecData mSpec;

    private Double mMeasuredOdMm = null;
    private FlareSpec.VerificationResult mVerification = null;

    private Paint mGridPaint;
    private Paint mAxisPaint;
    private Paint mTubeFillPaint;
    private Paint mTubeStrokePaint;
    private Paint mFlareLipPaint;
    private Paint mTolerancePaint;
    private Paint mCaliperPaint;
    private Paint mTextPaint;
    private Paint mSubTextPaint;

    private Path mLeftWallPath = new Path();
    private Path mRightWallPath = new Path();
    private Path mLeftLipPath = new Path();
    private Path mRightLipPath = new Path();

    public FlareProfileView(Context context) {
        super(context);
        init();
    }

    public FlareProfileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FlareProfileView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mSpec = FlareSpec.getSpec(mStandard, mSize, mMaterial);

        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setColor(0x22888888);
        mGridPaint.setStrokeWidth(1.5f);
        mGridPaint.setStyle(Paint.Style.STROKE);

        mAxisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAxisPaint.setColor(0x6600E5FF);
        mAxisPaint.setStrokeWidth(2f);
        mAxisPaint.setStyle(Paint.Style.STROKE);
        mAxisPaint.setPathEffect(new DashPathEffect(new float[]{10f, 8f}, 0));

        mTubeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTubeFillPaint.setColor(0x40D97706); // Amber/copper tinted fill
        mTubeFillPaint.setStyle(Paint.Style.FILL);

        mTubeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTubeStrokePaint.setColor(0xFFF59E0B);
        mTubeStrokePaint.setStrokeWidth(4f);
        mTubeStrokePaint.setStyle(Paint.Style.STROKE);
        mTubeStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        mTubeStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        mFlareLipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFlareLipPaint.setColor(0xFF06B6D4); // Cyan accent lip
        mFlareLipPaint.setStrokeWidth(3f);
        mFlareLipPaint.setStyle(Paint.Style.STROKE);

        mTolerancePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTolerancePaint.setColor(0x8894A3B8);
        mTolerancePaint.setStrokeWidth(2f);
        mTolerancePaint.setStyle(Paint.Style.STROKE);
        mTolerancePaint.setPathEffect(new DashPathEffect(new float[]{6f, 6f}, 0));

        mCaliperPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCaliperPaint.setColor(0xFF22C55E);
        mCaliperPaint.setStrokeWidth(3.5f);
        mCaliperPaint.setStyle(Paint.Style.STROKE);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(0xFFE2E8F0);
        mTextPaint.setTextSize(32f);
        mTextPaint.setFakeBoldText(true);

        mSubTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSubTextPaint.setColor(0xFF94A3B8);
        mSubTextPaint.setTextSize(24f);
    }

    public void updateSpec(FlareSpec.Standard standard, FlareSpec.TubingSize size, FlareSpec.Material material) {
        this.mStandard = standard;
        this.mSize = size;
        this.mMaterial = material;
        this.mSpec = FlareSpec.getSpec(standard, size, material);
        if (mMeasuredOdMm != null) {
            this.mVerification = FlareSpec.verifyCaliper(mMeasuredOdMm, mSpec);
        }
        invalidate();
    }

    public void setMeasuredCaliper(Double measuredOdMm) {
        this.mMeasuredOdMm = measuredOdMm;
        if (measuredOdMm != null) {
            this.mVerification = FlareSpec.verifyCaliper(measuredOdMm, mSpec);
        } else {
            this.mVerification = null;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int) (w * 0.58f); // 16:9.2 ergonomic aspect ratio for HUD
        if (h < 260) h = 260;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h * 0.52f;

        // 1. Draw Blueprint Background Grid
        int gridSpacing = w / 12;
        for (int x = gridSpacing; x < w; x += gridSpacing) {
            canvas.drawLine(x, 10, x, h - 10, mGridPaint);
        }
        for (int y = gridSpacing; y < h; y += gridSpacing) {
            canvas.drawLine(10, y, w - 10, y, mGridPaint);
        }

        // 2. Draw Centerline
        canvas.drawLine(cx, 16, cx, h - 16, mAxisPaint);

        // 3. Compute Profile Geometry Scaling
        // Scale so tube fits in roughly 40-70% of canvas width
        float maxExpectedFlareWidth = (float) mSpec.flareOdMaxMm * 1.35f;
        float pxPerMm = (w * 0.46f) / maxExpectedFlareWidth;

        float tubeRadiusPx = (float) (mSize.metricMm / 2.0) * pxPerMm;
        float wallThicknessMm = 0.89f; // Standard ~0.035"
        float innerRadiusPx = Math.max(tubeRadiusPx - (wallThicknessMm * pxPerMm), tubeRadiusPx * 0.55f);

        float flareRadiusPx = (float) (mSpec.flareOdNominalMm / 2.0) * pxPerMm;
        float minFlareRadiusPx = (float) (mSpec.flareOdMinMm / 2.0) * pxPerMm;
        float maxFlareRadiusPx = (float) (mSpec.flareOdMaxMm / 2.0) * pxPerMm;

        float flareTopY = cy - (h * 0.28f);
        float flareBaseY = cy - (h * 0.05f);
        float tubeBottomY = cy + (h * 0.36f);

        // Update copper/material color tone
        if (mMaterial == FlareSpec.Material.STAINLESS_304) {
            mTubeStrokePaint.setColor(0xFFCBD5E1);
            mTubeFillPaint.setColor(0x3394A3B8);
        } else if (mMaterial == FlareSpec.Material.STEEL_BUNDY) {
            mTubeStrokePaint.setColor(0xFF94A3B8);
            mTubeFillPaint.setColor(0x33475569);
        } else if (mMaterial == FlareSpec.Material.ALUMINUM) {
            mTubeStrokePaint.setColor(0xFFE2E8F0);
            mTubeFillPaint.setColor(0x22CBD5E1);
        } else {
            // Soft / Hard Copper / CuNi
            mTubeStrokePaint.setColor(0xFFF59E0B);
            mTubeFillPaint.setColor(0x40D97706);
        }

        // 4. Construct Cross Section Paths
        mLeftWallPath.reset();
        mRightWallPath.reset();

        if (mStandard == FlareSpec.Standard.DIN_BUBBLE) {
            // DIN ISO Bubble Mushroom Flare
            float bulbRadius = (flareRadiusPx - tubeRadiusPx);
            float bulbCenterY = (flareTopY + flareBaseY) / 2f;

            // Left Wall
            mLeftWallPath.moveTo(cx - innerRadiusPx, tubeBottomY);
            mLeftWallPath.lineTo(cx - tubeRadiusPx, tubeBottomY);
            mLeftWallPath.lineTo(cx - tubeRadiusPx, flareBaseY);
            // Outward bulb curve
            mLeftWallPath.quadTo(cx - flareRadiusPx - (bulbRadius * 0.2f), bulbCenterY, cx - flareRadiusPx, flareTopY);
            mLeftWallPath.lineTo(cx - innerRadiusPx, flareTopY);
            mLeftWallPath.close();

            // Right Wall
            mRightWallPath.moveTo(cx + innerRadiusPx, tubeBottomY);
            mRightWallPath.lineTo(cx + tubeRadiusPx, tubeBottomY);
            mRightWallPath.lineTo(cx + tubeRadiusPx, flareBaseY);
            mRightWallPath.quadTo(cx + flareRadiusPx + (bulbRadius * 0.2f), bulbCenterY, cx + flareRadiusPx, flareTopY);
            mRightWallPath.lineTo(cx + innerRadiusPx, flareTopY);
            mRightWallPath.close();

        } else if (mStandard == FlareSpec.Standard.SAE_45_DOUBLE) {
            // SAE 45 Double / Inverted (Doubled-back folded lip)
            float foldDepth = (flareBaseY - flareTopY) * 0.4f;

            // Left Wall
            mLeftWallPath.moveTo(cx - innerRadiusPx, tubeBottomY);
            mLeftWallPath.lineTo(cx - tubeRadiusPx, tubeBottomY);
            mLeftWallPath.lineTo(cx - tubeRadiusPx, flareBaseY);
            mLeftWallPath.lineTo(cx - flareRadiusPx, flareTopY);
            // Inverted fold back inward
            mLeftWallPath.lineTo(cx - flareRadiusPx + (tubeRadiusPx * 0.35f), flareTopY + foldDepth);
            mLeftWallPath.lineTo(cx - innerRadiusPx, flareTopY + (foldDepth * 0.5f));
            mLeftWallPath.close();

            // Right Wall
            mRightWallPath.moveTo(cx + innerRadiusPx, tubeBottomY);
            mRightWallPath.lineTo(cx + tubeRadiusPx, tubeBottomY);
            mRightWallPath.lineTo(cx + tubeRadiusPx, flareBaseY);
            mRightWallPath.lineTo(cx + flareRadiusPx, flareTopY);
            mRightWallPath.lineTo(cx + flareRadiusPx - (tubeRadiusPx * 0.35f), flareTopY + foldDepth);
            mRightWallPath.lineTo(cx + innerRadiusPx, flareTopY + (foldDepth * 0.5f));
            mRightWallPath.close();

        } else {
            // Standard 45 Single or 37 AN Conical Cone
            // Left Wall
            mLeftWallPath.moveTo(cx - innerRadiusPx, tubeBottomY);
            mLeftWallPath.lineTo(cx - tubeRadiusPx, tubeBottomY);
            mLeftWallPath.lineTo(cx - tubeRadiusPx, flareBaseY);
            mLeftWallPath.lineTo(cx - flareRadiusPx, flareTopY);
            mLeftWallPath.lineTo(cx - flareRadiusPx + (innerRadiusPx * 0.5f), flareTopY);
            mLeftWallPath.lineTo(cx - innerRadiusPx, flareBaseY);
            mLeftWallPath.close();

            // Right Wall
            mRightWallPath.moveTo(cx + innerRadiusPx, tubeBottomY);
            mRightWallPath.lineTo(cx + tubeRadiusPx, tubeBottomY);
            mRightWallPath.lineTo(cx + tubeRadiusPx, flareBaseY);
            mRightWallPath.lineTo(cx + flareRadiusPx, flareTopY);
            mRightWallPath.lineTo(cx + flareRadiusPx - (innerRadiusPx * 0.5f), flareTopY);
            mRightWallPath.lineTo(cx + innerRadiusPx, flareBaseY);
            mRightWallPath.close();
        }

        // Draw tube wall solid fills and strokes
        canvas.drawPath(mLeftWallPath, mTubeFillPaint);
        canvas.drawPath(mRightWallPath, mTubeFillPaint);
        canvas.drawPath(mLeftWallPath, mTubeStrokePaint);
        canvas.drawPath(mRightWallPath, mTubeStrokePaint);

        // 5. Draw Flare Angle Annotations
        float angleIndicatorY = flareBaseY + 12f;
        mSubTextPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(mStandard.angleDeg + "° Cone", cx - tubeRadiusPx - 16f, angleIndicatorY, mSubTextPaint);
        mSubTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(mSize.fraction + " OD", cx + tubeRadiusPx + 16f, angleIndicatorY, mSubTextPaint);

        // 6. Draw Min/Max Tolerance Brackets at Top of Flare
        float tolBracketY = flareTopY - 24f;
        // Min OD bracket
        canvas.drawLine(cx - minFlareRadiusPx, flareTopY - 10f, cx - minFlareRadiusPx, tolBracketY + 6f, mTolerancePaint);
        canvas.drawLine(cx + minFlareRadiusPx, flareTopY - 10f, cx + minFlareRadiusPx, tolBracketY + 6f, mTolerancePaint);

        // Max OD bracket
        canvas.drawLine(cx - maxFlareRadiusPx, flareTopY - 10f, cx - maxFlareRadiusPx, tolBracketY + 6f, mTolerancePaint);
        canvas.drawLine(cx + maxFlareRadiusPx, flareTopY - 10f, cx + maxFlareRadiusPx, tolBracketY + 6f, mTolerancePaint);

        // Tolerance horizontal connecting bar
        canvas.drawLine(cx - maxFlareRadiusPx, tolBracketY, cx + maxFlareRadiusPx, tolBracketY, mTolerancePaint);

        // Draw tolerance dimension text
        mSubTextPaint.setTextAlign(Paint.Align.CENTER);
        String tolText = String.format(Locale.US, "Nominal OD: %.2f - %.2f mm", mSpec.flareOdMinMm, mSpec.flareOdMaxMm);
        canvas.drawText(tolText, cx, tolBracketY - 12f, mSubTextPaint);

        // 7. Dynamic Measured Caliper Indicator
        if (mMeasuredOdMm != null && mVerification != null) {
            float measuredRadiusPx = (float) (mMeasuredOdMm / 2.0) * pxPerMm;
            float caliperY = flareTopY - 62f;

            if (mVerification.status == FlareSpec.VerificationResult.Status.PASS) {
                mCaliperPaint.setColor(0xFF22C55E); // Green
            } else if (mVerification.status == FlareSpec.VerificationResult.Status.MARGINAL) {
                mCaliperPaint.setColor(0xFFF59E0B); // Amber
            } else {
                mCaliperPaint.setColor(0xFFEF4444); // Red Danger
            }

            // Caliper dimension arrows
            canvas.drawLine(cx - measuredRadiusPx, caliperY, cx + measuredRadiusPx, caliperY, mCaliperPaint);
            // Left arrow tick
            canvas.drawLine(cx - measuredRadiusPx, caliperY - 8f, cx - measuredRadiusPx, caliperY + 8f, mCaliperPaint);
            canvas.drawLine(cx - measuredRadiusPx, caliperY, cx - measuredRadiusPx, flareTopY - 2f, mCaliperPaint);
            // Right arrow tick
            canvas.drawLine(cx + measuredRadiusPx, caliperY - 8f, cx + measuredRadiusPx, caliperY + 8f, mCaliperPaint);
            canvas.drawLine(cx + measuredRadiusPx, caliperY, cx + measuredRadiusPx, flareTopY - 2f, mCaliperPaint);

            // Caliper value text
            mTextPaint.setColor(mCaliperPaint.getColor());
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            String caliperText = String.format(Locale.US, "Caliper: %.2f mm (%s)", mMeasuredOdMm, mVerification.headline);
            canvas.drawText(caliperText, cx, caliperY - 14f, mTextPaint);
        } else {
            // Default guide prompt
            mTextPaint.setColor(0xFF94A3B8);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Enter Caliper Reading Below to Verify Fit", cx, flareTopY - 58f, mSubTextPaint);
        }
    }
}
