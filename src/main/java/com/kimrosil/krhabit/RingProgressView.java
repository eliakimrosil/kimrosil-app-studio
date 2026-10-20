package com.kimrosil.krhabit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class RingProgressView extends View {
    private Paint trackPaint;
    private Paint progressPaint;
    private final RectF arcBounds = new RectF();
    private float progress = 0f; // 0 to 100

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
        float strokeWidth = 5f * density;

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

    public void setProgress(float progressPercent) {
        this.progress = Math.max(0f, Math.min(100f, progressPercent));
        invalidate();
    }

    public float getProgress() {
        return this.progress;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        float strokeWidth = 5f * density;
        float halfStroke = (strokeWidth / 2f) + (2f * density);
        arcBounds.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draw complete background circular track
        canvas.drawOval(arcBounds, trackPaint);

        // Draw progress arc sweeping clockwise from top (-90 degrees)
        if (progress > 0f) {
            float sweepAngle = (progress / 100f) * 360f;
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint);
        }
    }
}
