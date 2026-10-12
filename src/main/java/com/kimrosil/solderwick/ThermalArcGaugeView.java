package com.kimrosil.solderwick;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class ThermalArcGaugeView extends View {

    private Paint trackPaint;
    private Paint progressPaint;
    private Paint dangerPaint;
    private Paint textPrimaryPaint;
    private Paint textSecondaryPaint;
    private Paint tickPaint;
    private RectF arcBounds;

    private float maxDwellMs = 2500f;
    private float currentDwellMs = 2500f;
    private int recommendedTempC = 315;
    private boolean isCountdownActive = false;
    private boolean isOverheated = false;

    private static final float START_ANGLE = 135f;
    private static final float SWEEP_ANGLE = 270f;

    private OnDwellBudgetChangeListener dwellBudgetListener;
    private float touchSlop;
    private float lastTouchAngle = -1f;

    public interface OnDwellBudgetChangeListener {
        void onDwellBudgetChanged(int newBudgetMs);
    }

    public ThermalArcGaugeView(Context context) {
        super(context);
        init(context);
    }

    public ThermalArcGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ThermalArcGaugeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        arcBounds = new RectF();

        float density = getResources().getDisplayMetrics().density;
        float strokeWidth = 14f * density;

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.parseColor("#334155"));

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Color.parseColor("#F59E0B"));

        dangerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dangerPaint.setStyle(Paint.Style.STROKE);
        dangerPaint.setStrokeWidth(strokeWidth);
        dangerPaint.setStrokeCap(Paint.Cap.ROUND);
        dangerPaint.setColor(Color.parseColor("#EF4444"));

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(2f * density);
        tickPaint.setColor(Color.parseColor("#64748B"));

        textPrimaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPrimaryPaint.setTextAlign(Paint.Align.CENTER);
        textPrimaryPaint.setTextSize(36f * density);
        textPrimaryPaint.setFakeBoldText(true);
        textPrimaryPaint.setColor(Color.parseColor("#F8FAFC"));

        textSecondaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textSecondaryPaint.setTextAlign(Paint.Align.CENTER);
        textSecondaryPaint.setTextSize(13f * density);
        textSecondaryPaint.setColor(Color.parseColor("#94A3B8"));
    }

    public void setOnDwellBudgetChangeListener(OnDwellBudgetChangeListener listener) {
        this.dwellBudgetListener = listener;
    }

    public void setParameters(float currentMs, float maxMs, int tempC, boolean active, boolean overheated) {
        this.currentDwellMs = Math.max(0f, currentMs);
        this.maxDwellMs = Math.max(500f, maxMs);
        this.recommendedTempC = tempC;
        this.isCountdownActive = active;
        this.isOverheated = overheated;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        if (size <= 0) {
            size = (int) (240 * getResources().getDisplayMetrics().density);
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        float stroke = trackPaint.getStrokeWidth();
        float pad = stroke / 2f + 16f;

        arcBounds.set(pad, pad, width - pad, height - pad);

        // Draw track
        canvas.drawArc(arcBounds, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        // Calculate progress sweep
        float ratio = Math.min(1.0f, Math.max(0.0f, currentDwellMs / maxDwellMs));
        float currentSweep = SWEEP_ANGLE * ratio;

        if (isOverheated) {
            canvas.drawArc(arcBounds, START_ANGLE, SWEEP_ANGLE, false, dangerPaint);
        } else if (currentSweep > 0) {
            float heatRatio = 1.0f - ratio;
            int sweepColor = heatRatio > 0.75f ? Color.parseColor("#EF4444") :
                    (heatRatio > 0.45f ? Color.parseColor("#EA580C") : Color.parseColor("#F59E0B"));
            progressPaint.setColor(sweepColor);
            canvas.drawArc(arcBounds, START_ANGLE, currentSweep, false, progressPaint);
        }

        float cx = width / 2f;
        float cy = height / 2f;

        // Draw center text
        float density = getResources().getDisplayMetrics().density;
        String timeString;
        if (isCountdownActive) {
            timeString = String.format(java.util.Locale.US, "%.1fs", currentDwellMs / 1000f);
        } else {
            timeString = String.format(java.util.Locale.US, "%.1fs", maxDwellMs / 1000f);
        }

        if (isOverheated) {
            textPrimaryPaint.setColor(Color.parseColor("#EF4444"));
        } else if (isCountdownActive) {
            textPrimaryPaint.setColor(Color.parseColor("#F59E0B"));
        } else {
            textPrimaryPaint.setColor(Color.parseColor("#F8FAFC"));
        }

        canvas.drawText(timeString, cx, cy + (10f * density), textPrimaryPaint);

        int tempF = (int) Math.round(recommendedTempC * 1.8 + 32);
        String label = isOverheated ? "CRITICAL OVERHEAT" :
                (isCountdownActive ? "LIVE CONTACT DWELL" : "SAFE DWELL BUDGET");
        canvas.drawText(label, cx, cy - (26f * density), textSecondaryPaint);

        String tempStr = recommendedTempC + "°C / " + tempF + "°F SETPOINT";
        canvas.drawText(tempStr, cx, cy + (34f * density), textSecondaryPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isCountdownActive) {
            return super.onTouchEvent(event);
        }

        float x = event.getX() - (getWidth() / 2f);
        float y = event.getY() - (getHeight() / 2f);
        double dist = Math.sqrt(x * x + y * y);
        float radius = getWidth() / 2f;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (dist < (radius * 0.45f) || dist > (radius * 1.15f)) {
                return false;
            }
            getParent().requestDisallowInterceptTouchEvent(true);
            updateBudgetFromTouch(x, y);
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            updateBudgetFromTouch(x, y);
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }

        return super.onTouchEvent(event);
    }

    private void updateBudgetFromTouch(float x, float y) {
        double rad = Math.atan2(y, x);
        double deg = Math.toDegrees(rad);
        if (deg < 0) deg += 360;

        float touchAngle = (float) deg;
        float normalizedAngle = touchAngle - START_ANGLE;
        if (normalizedAngle < 0) normalizedAngle += 360;

        if (normalizedAngle <= SWEEP_ANGLE) {
            float ratio = normalizedAngle / SWEEP_ANGLE;
            int newBudget = Math.round((1000f + ratio * 7000f) / 100f) * 100; // 1.0s to 8.0s step 100ms
            newBudget = Math.max(1000, Math.min(8000, newBudget));

            if (Math.abs(newBudget - maxDwellMs) >= 100) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (dwellBudgetListener != null) {
                    dwellBudgetListener.onDwellBudgetChanged(newBudget);
                }
            }
        }
    }
}