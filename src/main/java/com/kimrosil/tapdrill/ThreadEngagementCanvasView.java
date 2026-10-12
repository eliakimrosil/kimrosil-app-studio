package com.kimrosil.tapdrill;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

public class ThreadEngagementCanvasView extends View {

    public interface OnEngagementChangeListener {
        void onEngagementChanged(int percent);
    }

    private OnEngagementChangeListener mListener;

    private Paint mPaintBlueprintBg;
    private Paint mPaintThreadFlank;
    private Paint mPaintBoreDashed;
    private Paint mPaintEngagementFill;
    private Paint mPaintDimensionText;
    private Paint mPaintSliderTrack;
    private Paint mPaintSliderThumb;

    private double mMajorDiaInch = 0.250;
    private double mPitchInch = 0.050;
    private double mTapDrillDiaInch = 0.201;
    private int mEngagementPercent = 75; // 40 to 90

    private float mSliderX = 0f;
    private float mSliderY = 0f;
    private float mSliderWidth = 0f;
    private boolean mIsDraggingSlider = false;

    public ThreadEngagementCanvasView(Context context) {
        super(context);
        init();
    }

    public ThreadEngagementCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);

        mPaintBlueprintBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintBlueprintBg.setColor(0xFF0F172A); // Dark slate blueprint background
        mPaintBlueprintBg.setStyle(Paint.Style.FILL);

        mPaintThreadFlank = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintThreadFlank.setColor(0xFF38BDF8); // Cyan flank stroke
        mPaintThreadFlank.setStyle(Paint.Style.STROKE);
        mPaintThreadFlank.setStrokeWidth(dp(2.5f));
        mPaintThreadFlank.setStrokeCap(Paint.Cap.ROUND);
        mPaintThreadFlank.setStrokeJoin(Paint.Join.ROUND);

        mPaintBoreDashed = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintBoreDashed.setColor(0xFFFFB74D); // Amber pilot hole line
        mPaintBoreDashed.setStyle(Paint.Style.STROKE);
        mPaintBoreDashed.setStrokeWidth(dp(2.0f));
        mPaintBoreDashed.setPathEffect(new DashPathEffect(new float[]{dp(6f), dp(4f)}, 0f));

        mPaintEngagementFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintEngagementFill.setColor(0x3300E5FF); // Semi-transparent cyan hatch/fill
        mPaintEngagementFill.setStyle(Paint.Style.FILL);

        mPaintDimensionText = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintDimensionText.setColor(0xFFE2E8F0);
        mPaintDimensionText.setTextSize(dp(10f));
        mPaintDimensionText.setStyle(Paint.Style.FILL);

        mPaintSliderTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintSliderTrack.setColor(0xFF334155);
        mPaintSliderTrack.setStyle(Paint.Style.STROKE);
        mPaintSliderTrack.setStrokeWidth(dp(4f));
        mPaintSliderTrack.setStrokeCap(Paint.Cap.ROUND);

        mPaintSliderThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintSliderThumb.setColor(0xFF0284C7);
        mPaintSliderThumb.setStyle(Paint.Style.FILL);
    }

    private float dp(float val) {
        return val * getResources().getDisplayMetrics().density;
    }

    public void setOnEngagementChangeListener(OnEngagementChangeListener l) {
        this.mListener = l;
    }

    public void setThreadParameters(double majorDia, double pitch, double tapDrillDia, int engagementPercent) {
        this.mMajorDiaInch = Math.max(0.010, majorDia);
        this.mPitchInch = Math.max(0.005, pitch);
        this.mTapDrillDiaInch = Math.max(0.008, tapDrillDia);
        this.mEngagementPercent = Math.min(90, Math.max(40, engagementPercent));
        invalidate();
    }

    public void setEngagementPercentage(int percent) {
        this.mEngagementPercent = Math.min(90, Math.max(40, percent));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Blueprint card background
        canvas.drawRoundRect(0, 0, w, h, dp(12f), dp(12f), mPaintBlueprintBg);

        float marginSide = dp(20f);
        float centerY = (h - dp(40f)) / 2f;
        float halfMajor = (h - dp(70f)) / 2f;

        // Scale thread profile to canvas
        float pitchPixel = dp(28f);
        int threadVCount = (int) ((w - marginSide * 2) / pitchPixel);
        if (threadVCount < 2) threadVCount = 2;

        float startX = marginSide;
        float endX = w - marginSide;

        // Draw pilot tap drill bore line
        float boreRatio = (float) (mTapDrillDiaInch / mMajorDiaInch);
        if (boreRatio > 1.0f) boreRatio = 0.95f;
        float halfBore = halfMajor * boreRatio;

        // Draw top bore and bottom bore dashed boundary
        canvas.drawLine(startX, centerY - halfBore, endX, centerY - halfBore, mPaintBoreDashed);
        canvas.drawLine(startX, centerY + halfBore, endX, centerY + halfBore, mPaintBoreDashed);

        // Draw Thread 60-degree crests & roots path
        Path topThreadPath = new Path();
        Path botThreadPath = new Path();
        Path topEngagementArea = new Path();

        topThreadPath.moveTo(startX, centerY - halfMajor);
        botThreadPath.moveTo(startX, centerY + halfMajor);
        topEngagementArea.moveTo(startX, centerY - halfBore);

        for (int i = 0; i < threadVCount; i++) {
            float x1 = startX + i * pitchPixel;
            float xMid = x1 + pitchPixel / 2f;
            float x2 = x1 + pitchPixel;

            // 60-deg V thread flanks
            topThreadPath.lineTo(xMid, centerY - (halfMajor * (1.0f - (mEngagementPercent / 100f * 0.4f))));
            topThreadPath.lineTo(x2, centerY - halfMajor);

            botThreadPath.lineTo(xMid, centerY + (halfMajor * (1.0f - (mEngagementPercent / 100f * 0.4f))));
            botThreadPath.lineTo(x2, centerY + halfMajor);
        }

        canvas.drawPath(topThreadPath, mPaintThreadFlank);
        canvas.drawPath(botThreadPath, mPaintThreadFlank);

        // Engagement Hatch Area Fill
        topEngagementArea.lineTo(endX, centerY - halfBore);
        topEngagementArea.lineTo(endX, centerY - halfMajor);
        topEngagementArea.lineTo(startX, centerY - halfMajor);
        topEngagementArea.close();
        canvas.drawPath(topEngagementArea, mPaintEngagementFill);

        // Dimensions Annotations
        String labelMajor = String.format(Locale.US, "Major: .%04d\"", (int) Math.round(mMajorDiaInch * 10000));
        String labelBore = String.format(Locale.US, "Pilot: .%04d\" (%d%%)", (int) Math.round(mTapDrillDiaInch * 10000), mEngagementPercent);
        canvas.drawText(labelMajor, marginSide, centerY - halfMajor - dp(6f), mPaintDimensionText);
        canvas.drawText(labelBore, marginSide, centerY - halfBore + dp(12f), mPaintDimensionText);

        // Bottom Interactive Engagement Stepper/Slider Track
        mSliderY = h - dp(20f);
        mSliderWidth = w - dp(60f);
        float trackLeft = dp(30f);
        float trackRight = trackLeft + mSliderWidth;
        canvas.drawLine(trackLeft, mSliderY, trackRight, mSliderY, mPaintSliderTrack);

        // Slider Thumb
        float fraction = (mEngagementPercent - 40) / 50.0f;
        mSliderX = trackLeft + fraction * mSliderWidth;
        canvas.drawCircle(mSliderX, mSliderY, dp(9f), mPaintSliderThumb);

        // 50% & 75% tick marks
        float x50 = trackLeft + (10f / 50f) * mSliderWidth;
        float x75 = trackLeft + (35f / 50f) * mSliderWidth;
        canvas.drawCircle(x50, mSliderY, dp(2f), mPaintDimensionText);
        canvas.drawCircle(x75, mSliderY, dp(2f), mPaintDimensionText);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (Math.abs(y - mSliderY) < dp(28f)) {
                    mIsDraggingSlider = true;
                    updateSliderFromTouch(x);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mIsDraggingSlider) {
                    updateSliderFromTouch(x);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDraggingSlider = false;
                break;
        }
        return super.onTouchEvent(event);
    }

    private void updateSliderFromTouch(float touchX) {
        float trackLeft = dp(30f);
        float clampedX = Math.max(trackLeft, Math.min(trackLeft + mSliderWidth, touchX));
        float fraction = (clampedX - trackLeft) / mSliderWidth;
        int newPercent = 40 + Math.round(fraction * 50f);

        // Snap points near 50% and 75%
        if (Math.abs(newPercent - 50) <= 2) newPercent = 50;
        if (Math.abs(newPercent - 75) <= 2) newPercent = 75;

        if (newPercent != mEngagementPercent) {
            if (newPercent % 5 == 0) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            mEngagementPercent = newPercent;
            invalidate();
            if (mListener != null) {
                mListener.onEngagementChanged(mEngagementPercent);
            }
        }
    }
}