package com.kimrosil.shimpack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom kinematic canvas rendering rotating machinery alignment geometry:
 * Stationary & movable shafts, coupling hub, dial sweep vectors, angular tilt,
 * and proportional foot shim stack elevations.
 */
public class MotorAlignmentCanvasView extends View {

    private Paint shaftPaint;
    private Paint centerLinePaint;
    private Paint footPaint;
    private Paint shimPaint;
    private Paint dialPaint;
    private Paint textPaint;
    private Paint accentPaint;
    private Paint gridPaint;

    private double f1Target = 0.0;
    private double f2Target = 0.0;
    private double angularError = 0.0;
    private boolean isMetric = false;
    private boolean isReverseDial = false;

    private List<Double> f1Stack = new ArrayList<>();
    private List<Double> f2Stack = new ArrayList<>();

    private final RectF f1Rect = new RectF();
    private final RectF f2Rect = new RectF();
    private final RectF couplingRect = new RectF();

    private OnFootSelectedListener footListener;
    private GestureDetector gestureDetector;

    public interface OnFootSelectedListener {
        void onFootTapped(int footIndex, double target, List<Double> stack);
        void onCouplingToggled();
    }

    public MotorAlignmentCanvasView(Context context) {
        super(context);
        init(context);
    }

    public MotorAlignmentCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MotorAlignmentCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        shaftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shaftPaint.setStyle(Paint.Style.FILL);

        centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerLinePaint.setStyle(Paint.Style.STROKE);
        centerLinePaint.setStrokeWidth(3f);
        centerLinePaint.setPathEffect(new DashPathEffect(new float[]{16, 10}, 0));

        footPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footPaint.setStyle(Paint.Style.FILL);

        shimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shimPaint.setStyle(Paint.Style.FILL);

        dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dialPaint.setStyle(Paint.Style.STROKE);
        dialPaint.setStrokeWidth(4f);
        dialPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        accentPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.5f);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                float x = e.getX();
                float y = e.getY();
                if (f1Rect.contains(x, y) && footListener != null) {
                    footListener.onFootTapped(1, f1Target, f1Stack);
                    return true;
                } else if (f2Rect.contains(x, y) && footListener != null) {
                    footListener.onFootTapped(2, f2Target, f2Stack);
                    return true;
                } else if (couplingRect.contains(x, y) && footListener != null) {
                    footListener.onCouplingToggled();
                    return true;
                }
                return super.onSingleTapConfirmed(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (footListener != null) {
                    footListener.onCouplingToggled();
                    return true;
                }
                return super.onDoubleTap(e);
            }
        });
    }

    public void setOnFootSelectedListener(OnFootSelectedListener listener) {
        this.footListener = listener;
    }

    public void updateTelemetry(double f1, double f2, double angleDeg, boolean metric, boolean reverseDial,
                                List<Double> stack1, List<Double> stack2) {
        this.f1Target = f1;
        this.f2Target = f2;
        this.angularError = angleDeg;
        this.isMetric = metric;
        this.isReverseDial = reverseDial;
        this.f1Stack = (stack1 != null) ? stack1 : new ArrayList<>();
        this.f2Stack = (stack2 != null) ? stack2 : new ArrayList<>();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Background Slate Grid
        gridPaint.setColor(Color.parseColor("#263238"));
        for (int x = 0; x < w; x += 50) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int y = 0; y < h; y += 50) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        float centerY = h * 0.48f;
        float groundY = h * 0.82f;

        // Shaft Centerline
        centerLinePaint.setColor(Color.parseColor("#64748B"));
        canvas.drawLine(20, centerY, w - 20, centerY, centerLinePaint);

        // Stationary Machine (Pump / Driver left side)
        shaftPaint.setColor(Color.parseColor("#475569"));
        float statLeft = 40;
        float statRight = w * 0.35f;
        float statTop = centerY - 55;
        float statBottom = centerY + 55;
        canvas.drawRoundRect(new RectF(statLeft, statTop, statRight, statBottom), 8, 8, shaftPaint);

        // Stationary shaft stanchion base
        footPaint.setColor(Color.parseColor("#334155"));
        canvas.drawRect(statLeft + 20, statBottom, statRight - 20, groundY, footPaint);

        // Coupling Hub
        float couplingX = w * 0.38f;
        couplingRect.set(couplingX - 16, centerY - 65, couplingX + 16, centerY + 65);
        accentPaint.setColor(Color.parseColor("#F59E0B"));
        canvas.drawRoundRect(couplingRect, 6, 6, accentPaint);

        // Movable Machine (Motor right side) with exaggerated angular tilt
        float movablePivotX = couplingX + 22;
        float movablePivotY = centerY;
        float tiltDeg = (float) Math.max(-6.0, Math.min(6.0, angularError * 35.0));

        canvas.save();
        canvas.rotate(tiltDeg, movablePivotX, movablePivotY);

        float movLeft = movablePivotX;
        float movRight = w - 40;
        float movTop = centerY - 50;
        float movBottom = centerY + 50;

        shaftPaint.setColor(Color.parseColor("#0284C7"));
        canvas.drawRoundRect(new RectF(movLeft, movTop, movRight, movBottom), 8, 8, shaftPaint);

        // Front Foot (F1)
        float f1Center = movLeft + (movRight - movLeft) * 0.32f;
        float f1Width = 54;
        f1Rect.set(f1Center - f1Width / 2, movBottom, f1Center + f1Width / 2, groundY - 20);
        footPaint.setColor(Color.parseColor("#1E293B"));
        canvas.drawRoundRect(f1Rect, 6, 6, footPaint);

        // Back Foot (F2)
        float f2Center = movLeft + (movRight - movLeft) * 0.80f;
        float f2Width = 54;
        f2Rect.set(f2Center - f2Width / 2, movBottom, f2Center + f2Width / 2, groundY - 20);
        canvas.drawRoundRect(f2Rect, 6, 6, footPaint);

        // Render Shim Stacks at feet
        drawShimPackLayers(canvas, f1Rect.left, f1Rect.right, groundY, f1Stack, f1Target >= 0);
        drawShimPackLayers(canvas, f2Rect.left, f2Rect.right, groundY, f2Stack, f2Target >= 0);

        canvas.restore();

        // Baseline Plate (Machine Bedplate)
        Paint bedplatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bedplatePaint.setColor(Color.parseColor("#0F172A"));
        canvas.drawRect(20, groundY, w - 20, groundY + 14, bedplatePaint);

        // Dial Indicator sweep vector / needle representation
        dialPaint.setColor(Color.parseColor("#10B981"));
        canvas.drawCircle(couplingX, centerY - 95, 24, dialPaint);
        canvas.drawLine(couplingX, centerY - 95, couplingX, centerY - 65, dialPaint);
        float needleAngle = isReverseDial ? 45f : 135f;
        float nx = (float) (couplingX + 16 * Math.cos(Math.toRadians(needleAngle)));
        float ny = (float) (centerY - 95 + 16 * Math.sin(Math.toRadians(needleAngle)));
        canvas.drawLine(couplingX, centerY - 95, nx, ny, dialPaint);

        // Foot Telemetry Text Overlay
        textPaint.setColor(Color.parseColor("#F8FAFC"));
        textPaint.setTextSize(26f);
        textPaint.setFakeBoldText(true);

        String unitStr = isMetric ? "mm" : "mil";
        String f1Text = String.format("F1: %+.3f %s", f1Target, unitStr);
        String f2Text = String.format("F2: %+.3f %s", f2Target, unitStr);

        canvas.drawText("PUMP (STATIONARY)", statLeft + (statRight - statLeft) / 2, statTop - 20, textPaint);
        canvas.drawText("MOTOR (MOVABLE)", (movLeft + movRight) / 2, movTop - 20, textPaint);

        textPaint.setTextSize(24f);
        textPaint.setColor(f1Target >= 0 ? Color.parseColor("#38BDF8") : Color.parseColor("#F43F5E"));
        canvas.drawText(f1Text, f1Center, groundY + 42, textPaint);

        textPaint.setColor(f2Target >= 0 ? Color.parseColor("#38BDF8") : Color.parseColor("#F43F5E"));
        canvas.drawText(f2Text, f2Center, groundY + 42, textPaint);
    }

    private void drawShimPackLayers(Canvas canvas, float left, float right, float baseGroundY, List<Double> stack, boolean isAdd) {
        if (stack == null || stack.isEmpty()) return;

        float currY = baseGroundY;
        int colors[] = {Color.parseColor("#FBBF24"), Color.parseColor("#34D399"), Color.parseColor("#60A5FA"), Color.parseColor("#F472B6")};

        for (int i = 0; i < stack.size(); i++) {
            double thickness = stack.get(i);
            float layerHeight = (float) Math.max(4.0, Math.min(18.0, thickness * (isMetric ? 10.0 : 400.0)));
            currY -= layerHeight;

            shimPaint.setColor(colors[i % colors.length]);
            RectF layerRect = new RectF(left - 4, currY, right + 4, currY + layerHeight - 1);
            canvas.drawRoundRect(layerRect, 3, 3, shimPaint);
        }
    }
}