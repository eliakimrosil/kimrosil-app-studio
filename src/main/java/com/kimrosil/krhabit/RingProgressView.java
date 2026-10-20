package com.kimrosil.krhabit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * RingProgressView renders a sleek circular gauge for Today's Progress,
 * matching the visual style from Pictures/krhabit.jpg.
 */
public class RingProgressView extends View {

    private Paint trackPaint;
    private Paint progressPaint;
    private final RectF arcBounds = new RectF();
    private float currentProgress = 0f;
    private ValueAnimator animator;

    public RingProgressView(Context context) {
        super(context);
        init();
    }

    public RingProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RingProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        float strokeWidth = 4.5f * density;

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(getContext().getColor(R.color.progress_track));

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(getContext().getColor(R.color.primary_accent));
    }

    public void setProgress(float targetProgress, boolean animate) {
        final float clamped = Math.max(0f, Math.min(100f, targetProgress));
        if (!animate) {
            if (animator != null && animator.isRunning()) animator.cancel();
            this.currentProgress = clamped;
            invalidate();
            return;
        }

        if (animator != null && animator.isRunning()) animator.cancel();
        animator = ValueAnimator.ofFloat(this.currentProgress, clamped);
        animator.setDuration(450);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                currentProgress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    public void setProgress(float targetProgress) {
        setProgress(targetProgress, true);
    }

    public float getProgress() {
        return this.currentProgress;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        float strokeWidth = 4.5f * density;
        float halfStroke = (strokeWidth / 2f) + (2f * density);
        arcBounds.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw 360-degree background track
        canvas.drawOval(arcBounds, trackPaint);

        // Draw progress arc starting from top (-90 degrees)
        if (currentProgress > 0f) {
            float sweepAngle = (currentProgress / 100f) * 360f;
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint);
        }
    }
}
