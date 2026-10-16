package com.kimrosil.lashgage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * Custom Canvas View rendering an authentic workshop dial indicator.
 * Displays radial tick gradations, in-spec tolerance band, needle deflection,
 * and handles interactive drag tuning with haptic feedback.
 */
public class LashDialView extends View {

    public interface OnLashChangedListener {
        void onLashChanged(double lashValue, boolean isInch);
    }

    private Paint mBezelPaint;
    private Paint mFacePaint;
    private Paint mTickMajorPaint;
    private Paint mTickMinorPaint;
    private Paint mTextPaint;
    private Paint mToleranceBandPaint;
    private Paint mNeedlePaint;
    private Paint mHubPaint;
    private Paint mRubyPaint;
    private Paint mDigitalReadoutPaint;
    private Paint mStatusPaint;

    private RectF mDialBounds = new RectF();
    private RectF mToleranceArcBounds = new RectF();

    private double mCurrentValue = 0.008; // default in inches
    private boolean mIsInch = true;
    private double mTargetMin = 0.006;
    private double mTargetMax = 0.010;
    private boolean mIsInSpec = true;
    private float mNeedleAngle = 115.2f; // degrees

    private OnLashChangedListener mListener;

    public LashDialView(Context context) {
        super(context);
        init();
    }

    public LashDialView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LashDialView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBezelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBezelPaint.setStyle(Paint.Style.STROKE);
        mBezelPaint.setStrokeWidth(6f);
        mBezelPaint.setColor(Color.parseColor("#475569"));

        mFacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFacePaint.setStyle(Paint.Style.FILL);
        mFacePaint.setColor(Color.parseColor("#0F1318"));

        mTickMajorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTickMajorPaint.setStyle(Paint.Style.STROKE);
        mTickMajorPaint.setStrokeWidth(3f);
        mTickMajorPaint.setStrokeCap(Paint.Cap.ROUND);
        mTickMajorPaint.setColor(Color.parseColor("#00E5FF"));

        mTickMinorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTickMinorPaint.setStyle(Paint.Style.STROKE);
        mTickMinorPaint.setStrokeWidth(1.5f);
        mTickMinorPaint.setColor(Color.parseColor("#64748B"));

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.parseColor("#94A3B8"));
        mTextPaint.setTextSize(24f);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mToleranceBandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mToleranceBandPaint.setStyle(Paint.Style.STROKE);
        mToleranceBandPaint.setStrokeWidth(12f);
        mToleranceBandPaint.setStrokeCap(Paint.Cap.ROUND);
        mToleranceBandPaint.setColor(Color.parseColor("#10B981"));

        mNeedlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNeedlePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mNeedlePaint.setStrokeWidth(4f);
        mNeedlePaint.setStrokeCap(Paint.Cap.ROUND);
        mNeedlePaint.setColor(Color.parseColor("#FFB300"));

        mHubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHubPaint.setStyle(Paint.Style.FILL);
        mHubPaint.setColor(Color.parseColor("#1E293B"));

        mRubyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRubyPaint.setStyle(Paint.Style.FILL);
        mRubyPaint.setColor(Color.parseColor("#E11D48"));

        mDigitalReadoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDigitalReadoutPaint.setColor(Color.parseColor("#00E5FF"));
        mDigitalReadoutPaint.setTextSize(36f);
        mDigitalReadoutPaint.setFakeBoldText(true);
        mDigitalReadoutPaint.setTextAlign(Paint.Align.CENTER);

        mStatusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStatusPaint.setColor(Color.parseColor("#10B981"));
        mStatusPaint.setTextSize(22f);
        mStatusPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setThemeMode(boolean isDark) {
        if (isDark) {
            mFacePaint.setColor(Color.parseColor("#0F1318"));
            mBezelPaint.setColor(Color.parseColor("#475569"));
            mTextPaint.setColor(Color.parseColor("#94A3B8"));
            mTickMinorPaint.setColor(Color.parseColor("#64748B"));
        } else {
            mFacePaint.setColor(Color.parseColor("#FFFFFF"));
            mBezelPaint.setColor(Color.parseColor("#CBD5E1"));
            mTextPaint.setColor(Color.parseColor("#475569"));
            mTickMinorPaint.setColor(Color.parseColor("#94A3B8"));
        }
        invalidate();
    }

    public void setOnLashChangedListener(OnLashChangedListener l) {
        this.mListener = l;
    }

    public void updateLash(double value, boolean isInch, double targetMin, double targetMax) {
        this.mCurrentValue = Math.max(0.0, value);
        this.mIsInch = isInch;
        this.mTargetMin = targetMin;
        this.mTargetMax = targetMax;

        double maxScale = mIsInch ? 0.025 : 0.65;
        double clampedVal = Math.min(maxScale, mCurrentValue);
        this.mNeedleAngle = (float) ((clampedVal / maxScale) * 300.0) - 150.0f; // -150 to +150 deg

        this.mIsInSpec = (mCurrentValue >= mTargetMin && mCurrentValue <= mTargetMax);

        if (mIsInSpec) {
            mStatusPaint.setColor(Color.parseColor("#10B981"));
            mDigitalReadoutPaint.setColor(Color.parseColor("#00E5FF"));
        } else if (mCurrentValue < mTargetMin) {
            mStatusPaint.setColor(Color.parseColor("#EF4444"));
            mDigitalReadoutPaint.setColor(Color.parseColor("#EF4444"));
        } else {
            mStatusPaint.setColor(Color.parseColor("#F59E0B"));
            mDigitalReadoutPaint.setColor(Color.parseColor("#F59E0B"));
        }

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        if (size <= 0) size = 480;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 16f;
        if (radius < 40f) return;

        // Dial face background
        canvas.drawCircle(cx, cy, radius, mFacePaint);

        // Bezel stroke
        canvas.drawCircle(cx, cy, radius, mBezelPaint);

        // Tolerance Arc
        float arcRadius = radius - 24f;
        mToleranceArcBounds.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius);

        double maxScale = mIsInch ? 0.025 : 0.65;
        float minAngle = (float) ((mTargetMin / maxScale) * 300.0) - 60.0f; // start relative to 12 o'clock
        float sweepAngle = (float) (((mTargetMax - mTargetMin) / maxScale) * 300.0);
        canvas.drawArc(mToleranceArcBounds, -150f + (float) ((mTargetMin / maxScale) * 300.0), sweepAngle, false, mToleranceBandPaint);

        // Radial Ticks (from -150 deg to +150 deg = 300 deg total range)
        for (int i = 0; i <= 25; i++) {
            float angleDeg = -150f + (i * 12.0f);
            float angleRad = (float) Math.toRadians(angleDeg - 90f);

            boolean isMajor = (i % 5 == 0);
            float tickLen = isMajor ? 18f : 10f;
            float outerR = radius - 8f;
            float innerR = outerR - tickLen;

            float x1 = cx + (float) Math.cos(angleRad) * outerR;
            float y1 = cy + (float) Math.sin(angleRad) * outerR;
            float x2 = cx + (float) Math.cos(angleRad) * innerR;
            float y2 = cy + (float) Math.sin(angleRad) * innerR;

            canvas.drawLine(x1, y1, x2, y2, isMajor ? mTickMajorPaint : mTickMinorPaint);

            if (isMajor) {
                float textR = innerR - 16f;
                float tx = cx + (float) Math.cos(angleRad) * textR;
                float ty = cy + (float) Math.sin(angleRad) * textR + 8f;
                canvas.drawText(String.valueOf(i), tx, ty, mTextPaint);
            }
        }

        // Digital Readout in lower half
        String valStr = mIsInch
            ? String.format(Locale.US, "%.4f IN", mCurrentValue)
            : String.format(Locale.US, "%.3f MM", mCurrentValue);
        canvas.drawText(valStr, cx, cy + (radius * 0.42f), mDigitalReadoutPaint);

        String statusStr = mIsInSpec ? "IN SPEC" : (mCurrentValue < mTargetMin ? "TIGHT" : "LOOSE");
        canvas.drawText(statusStr, cx, cy + (radius * 0.60f), mStatusPaint);

        // Needle
        canvas.save();
        canvas.rotate(mNeedleAngle, cx, cy);

        // Pointer
        float needleLength = radius * 0.72f;
        canvas.drawLine(cx, cy + 20f, cx, cy - needleLength, mNeedlePaint);

        canvas.restore();

        // Center hub & jewel
        canvas.drawCircle(cx, cy, 18f, mHubPaint);
        canvas.drawCircle(cx, cy, 8f, mRubyPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float dx = event.getX() - cx;
            float dy = event.getY() - cy;

            double angleRad = Math.atan2(dy, dx);
            double angleDeg = Math.toDegrees(angleRad) + 90.0;
            if (angleDeg < -180.0) angleDeg += 360.0;
            if (angleDeg > 180.0) angleDeg -= 360.0;

            // Clamp between -150 and +150
            if (angleDeg < -150.0) angleDeg = -150.0;
            if (angleDeg > 150.0) angleDeg = 150.0;

            double fraction = (angleDeg + 150.0) / 300.0;
            double maxScale = mIsInch ? 0.025 : 0.65;
            double newVal = fraction * maxScale;

            updateLash(newVal, mIsInch, mTargetMin, mTargetMax);
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);

            if (mListener != null) {
                mListener.onLashChanged(newVal, mIsInch);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
