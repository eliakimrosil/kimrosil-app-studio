package com.kimrosil.flowstep;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Visual Pacer Canvas View for ADHD Calm & Executive Momentum.
 * Draws a soothing, breathing circular progress arc using Pure Android Canvas.
 * Intentionally hides all numeric countdown seconds to eliminate time-blindness anxiety.
 */
public class VisualPacerView extends View {

    private Paint trackPaint;
    private Paint arcPaint;
    private Paint textPaint;
    private Paint subTextPaint;
    private RectF arcBounds;

    private float sweepAngle = 90f;
    private float pulseFraction = 0f;
    private ValueAnimator breathAnimator;

    private String centerStatus = "Breathe & Focus";
    private String stepBadge = "Step 1 of 4";

    public VisualPacerView(Context context) {
        super(context);
        init();
    }

    public VisualPacerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VisualPacerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(12));
        trackPaint.setColor(getResources().getColor(R.color.pacer_track));

        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(12));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(getResources().getColor(R.color.pacer_arc));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(getResources().getColor(R.color.textPrimary));
        textPaint.setTextSize(sp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subTextPaint.setColor(getResources().getColor(R.color.textSecondary));
        subTextPaint.setTextSize(sp(13));
        subTextPaint.setTextAlign(Paint.Align.CENTER);

        arcBounds = new RectF();

        // Calm breathing animation cycle (4s in, 4s out = 8s smooth loop)
        breathAnimator = ValueAnimator.ofFloat(0f, 1f);
        breathAnimator.setDuration(4000);
        breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathAnimator.setRepeatMode(ValueAnimator.REVERSE);
        breathAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        breathAnimator.addUpdateListener(animation -> {
            pulseFraction = (float) animation.getAnimatedValue();
            sweepAngle = 120f + (pulseFraction * 180f);
            if (pulseFraction < 0.5f) {
                centerStatus = "Gentle breath in";
            } else {
                centerStatus = "Release and focus";
            }
            invalidate();
        });
    }

    public void setStepProgress(int currentStep, int totalSteps) {
        if (totalSteps > 0) {
            this.stepBadge = "Step " + currentStep + " of " + totalSteps;
        } else {
            this.stepBadge = "Single Step";
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (breathAnimator != null && !breathAnimator.isRunning()) {
            breathAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (breathAnimator != null) {
            breathAnimator.cancel();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = dp(18);
        float size = Math.min(w, h);
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = (size / 2f) - padding;

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = (Math.min(getWidth(), getHeight()) / 2f) - dp(18);

        // 1. Draw soothing full track circle
        canvas.drawCircle(cx, cy, radius, trackPaint);

        // 2. Draw organic breathing arc (starts at -90 = top)
        float startAngle = -90f + (pulseFraction * 30f);
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, arcPaint);

        // 3. Draw Center Status & Step Badge (No ticking numbers)
        float textY = cy - dp(4);
        canvas.drawText(stepBadge, cx, textY, textPaint);
        canvas.drawText(centerStatus, cx, textY + dp(22), subTextPaint);
    }

    private float dp(float val) {
        return val * getResources().getDisplayMetrics().density;
    }

    private float sp(float val) {
        return val * getResources().getDisplayMetrics().scaledDensity;
    }
}
