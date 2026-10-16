package com.kimrosil.fastarc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.Locale;

/**
 * FastArcView: Custom Canvas-based 16:8 circular metabolic arc gauge.
 * Ultra-lightweight, hardware-accelerated, zero external dependencies.
 */
public class FastArcView extends View {

    private Paint mTrackPaint;
    private Paint mEatingTrackPaint;
    private Paint mArcPaint;
    private Paint mBeaconPaint;
    private Paint mBeaconGlowPaint;
    private Paint mMarkerPaint;

    private Paint mTimerTextPaint;
    private Paint mStageTextPaint;
    private Paint mPercentTextPaint;
    private Paint mHintTextPaint;

    private RectF mArcBounds = new RectF();

    // Fasting State
    private boolean mIsFasting = false;
    private long mElapsedMillis = 0;
    private long mTargetMillis = 16L * 3600L * 1000L;
    private int mStageIndex = 0; // 0=Digestion, 1=Glycogen, 2=Ketosis, 3=Autophagy
    private String mStageName = "Ready to Fast";
    private boolean mShowRemaining = false;

    // Default colors (will be updated via theme)
    private int mColorPrimary = 0xFF10B981;
    private int mColorTextPrimary = 0xFFFFFFFF;
    private int mColorTextSecondary = 0xFF94A3B8;
    private int mColorTrackBase = 0xFF1E293B;
    private int mColorTrackEating = 0xFF2D2319;
    private int mColorStage = 0xFF10B981;

    public FastArcView(Context context) {
        super(context);
        init();
    }

    public FastArcView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FastArcView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);

        mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setStrokeCap(Paint.Cap.ROUND);

        mEatingTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mEatingTrackPaint.setStyle(Paint.Style.STROKE);
        mEatingTrackPaint.setStrokeCap(Paint.Cap.ROUND);

        mArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mArcPaint.setStyle(Paint.Style.STROKE);
        mArcPaint.setStrokeCap(Paint.Cap.ROUND);

        mBeaconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBeaconPaint.setStyle(Paint.Style.FILL);

        mBeaconGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBeaconGlowPaint.setStyle(Paint.Style.FILL);

        mMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mMarkerPaint.setStyle(Paint.Style.STROKE);
        mMarkerPaint.setStrokeCap(Paint.Cap.ROUND);

        mTimerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTimerTextPaint.setTextAlign(Paint.Align.CENTER);
        mTimerTextPaint.setFakeBoldText(true);

        mStageTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStageTextPaint.setTextAlign(Paint.Align.CENTER);
        mStageTextPaint.setFakeBoldText(true);

        mPercentTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPercentTextPaint.setTextAlign(Paint.Align.CENTER);

        mHintTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHintTextPaint.setTextAlign(Paint.Align.CENTER);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsFasting) {
                    mShowRemaining = !mShowRemaining;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    invalidate();
                }
            }
        });
    }

    public void setThemeTokens(int primary, int textPrimary, int textSecondary, int trackBase, int trackEating, int stageColor) {
        mColorPrimary = primary;
        mColorTextPrimary = textPrimary;
        mColorTextSecondary = textSecondary;
        mColorTrackBase = trackBase;
        mColorTrackEating = trackEating;
        mColorStage = stageColor;
        invalidate();
    }

    public void updateData(boolean isFasting, long elapsedMillis, long targetMillis, int stageIndex, String stageName, int stageColor) {
        mIsFasting = isFasting;
        mElapsedMillis = Math.max(0, elapsedMillis);
        mTargetMillis = Math.max(1, targetMillis);
        mStageIndex = stageIndex;
        mStageName = stageName;
        mColorStage = stageColor;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = (int) (width * 0.95f);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int height = (heightMode == MeasureSpec.EXACTLY) ? heightSize : desiredHeight;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        float strokeWidth = 14f * density;
        float padding = strokeWidth + (16f * density);

        float size = Math.min(w, h) - (padding * 2f);
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        mArcBounds.set(left, top, left + size, top + size);

        mTrackPaint.setStrokeWidth(strokeWidth);
        mEatingTrackPaint.setStrokeWidth(strokeWidth * 0.65f);
        mArcPaint.setStrokeWidth(strokeWidth);
        mMarkerPaint.setStrokeWidth(2f * density);

        mTimerTextPaint.setTextSize(36f * density);
        mStageTextPaint.setTextSize(13f * density);
        mPercentTextPaint.setTextSize(13f * density);
        mHintTextPaint.setTextSize(11f * density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = mArcBounds.centerX();
        float cy = mArcBounds.centerY();
        float radius = mArcBounds.width() / 2f;
        float density = getResources().getDisplayMetrics().density;

        // 1. Draw 16:8 Track Bases
        // Start angle: -90 degrees (12 o'clock).
        // 16 hours = 240 degrees sweep.
        // 8 hours eating window = 120 degrees sweep (from 150 deg to 270 deg).
        mTrackPaint.setColor(mColorTrackBase);
        canvas.drawArc(mArcBounds, -90f, 240f, false, mTrackPaint);

        mEatingTrackPaint.setColor(mColorTrackEating);
        canvas.drawArc(mArcBounds, 150f, 120f, false, mEatingTrackPaint);

        // 2. Cardinal Milestones: 4h, 8h, 12h, 16h (target), and 24h
        mMarkerPaint.setColor(mColorTextSecondary);
        mMarkerPaint.setAlpha(90);
        float[] milestoneHours = new float[]{4f, 8f, 12f, 16f};
        for (float h : milestoneHours) {
            float angleDeg = -90f + (h / 24f * 360f);
            double angleRad = Math.toRadians(angleDeg);
            float innerR = radius - (12f * density);
            float outerR = radius + (12f * density);
            float x1 = (float) (cx + innerR * Math.cos(angleRad));
            float y1 = (float) (cy + innerR * Math.sin(angleRad));
            float x2 = (float) (cx + outerR * Math.cos(angleRad));
            float y2 = (float) (cy + outerR * Math.sin(angleRad));
            canvas.drawLine(x1, y1, x2, y2, mMarkerPaint);
        }

        // 3. Draw Active Progress Arc
        float progressFraction = (float) mElapsedMillis / (float) mTargetMillis;
        if (!mIsFasting) {
            progressFraction = 0f;
        }

        float sweepAngle;
        if (progressFraction <= 1.0f) {
            // Sweeps within the 16h fasting window (0 to 240 degrees)
            sweepAngle = progressFraction * 240f;
        } else {
            // Extended Fast beyond 16h: sweeps past 240 degrees into 360 degrees
            float overRatio = Math.min(2.0f, progressFraction);
            sweepAngle = 240f + ((overRatio - 1.0f) * 120f);
        }

        if (mIsFasting && sweepAngle > 1f) {
            mArcPaint.setColor(mColorStage);
            canvas.drawArc(mArcBounds, -90f, sweepAngle, false, mArcPaint);

            // Beacon needle dot at head of arc
            double beaconAngleRad = Math.toRadians(-90f + sweepAngle);
            float beaconX = (float) (cx + radius * Math.cos(beaconAngleRad));
            float beaconY = (float) (cy + radius * Math.sin(beaconAngleRad));

            mBeaconGlowPaint.setColor(mColorStage);
            mBeaconGlowPaint.setAlpha(70);
            canvas.drawCircle(beaconX, beaconY, 11f * density, mBeaconGlowPaint);

            mBeaconPaint.setColor(Color.WHITE);
            canvas.drawCircle(beaconX, beaconY, 4.5f * density, mBeaconPaint);
        }

        // 4. Center Telemetry Typography
        // A. Timer Digits (Elapsed or Remaining)
        String timeDisplay;
        long targetDisplayMillis;
        if (!mIsFasting) {
            timeDisplay = "16:00:00";
        } else if (mShowRemaining) {
            long remaining = Math.max(0, mTargetMillis - mElapsedMillis);
            timeDisplay = formatHMS(remaining);
        } else {
            timeDisplay = formatHMS(mElapsedMillis);
        }

        mTimerTextPaint.setColor(mColorTextPrimary);
        canvas.drawText(timeDisplay, cx, cy - (10f * density), mTimerTextPaint);

        // B. Stage Badge / Title
        mStageTextPaint.setColor(mIsFasting ? mColorStage : mColorTextSecondary);
        String stageLabel = mIsFasting ? ("⚡ " + mStageName.toUpperCase(Locale.US)) : "16:8 FAST READY";
        canvas.drawText(stageLabel, cx, cy + (18f * density), mStageTextPaint);

        // C. Percentage & Mode
        mPercentTextPaint.setColor(mColorTextSecondary);
        String percentStr;
        if (!mIsFasting) {
            percentStr = "Tap Start Fast to Begin";
        } else if (mShowRemaining) {
            long remaining = mTargetMillis - mElapsedMillis;
            if (remaining > 0) {
                percentStr = String.format(Locale.US, "%.1f%% • Remaining", (progressFraction * 100f));
            } else {
                percentStr = "16h Goal Achieved • Autophagy";
            }
        } else {
            percentStr = String.format(Locale.US, "%.1f%% • Elapsed", (progressFraction * 100f));
        }
        canvas.drawText(percentStr, cx, cy + (40f * density), mPercentTextPaint);

        // D. Tap Hint
        if (mIsFasting) {
            mHintTextPaint.setColor(mColorTextSecondary);
            mHintTextPaint.setAlpha(120);
            String hint = mShowRemaining ? "(Tap to show elapsed)" : "(Tap to show remaining)";
            canvas.drawText(hint, cx, cy + (62f * density), mHintTextPaint);
        }
    }

    private static String formatHMS(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
