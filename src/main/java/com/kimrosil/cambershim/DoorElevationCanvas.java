package com.kimrosil.cambershim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Blueprint Workshop canvas rendering dynamic door elevation, hinge pivot points,
 * and magnified sag angle deflection arc.
 */
public class DoorElevationCanvas extends View {

    private Paint gridPaint;
    private Paint framePaint;
    private Paint doorPaint;
    private Paint sagArcPaint;
    private Paint hingePaint;
    private Paint textPaint;

    private double sagDeflectionMm = 0.0;
    private double sagAngleDeg = 0.0;
    private String status = "CLEARANCE";

    public DoorElevationCanvas(Context context) {
        super(context);
        init();
    }

    public DoorElevationCanvas(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#1E3A5F"));
        gridPaint.setStrokeWidth(1.5f);
        gridPaint.setStyle(Paint.Style.STROKE);

        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setColor(Color.parseColor("#94A3B8"));
        framePaint.setStrokeWidth(4f);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0f));

        doorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        doorPaint.setColor(Color.parseColor("#38BDF8"));
        doorPaint.setStrokeWidth(5f);
        doorPaint.setStyle(Paint.Style.STROKE);

        sagArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sagArcPaint.setStrokeWidth(4f);
        sagArcPaint.setStyle(Paint.Style.STROKE);

        hingePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hingePaint.setColor(Color.parseColor("#F59E0B"));
        hingePaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#38BDF8"));
        textPaint.setTextSize(26f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void updateTelemetry(double deflectionMm, double angleDeg, String status) {
        this.sagDeflectionMm = deflectionMm;
        this.sagAngleDeg = angleDeg;
        this.status = status;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Draw blueprint drafting background grid lines
        int step = 32;
        for (int x = 0; x < w; x += step) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int y = 0; y < h; y += step) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        // Draw Frame outline (Plumb standard)
        float padX = w * 0.22f;
        float padY = h * 0.12f;
        float frameW = w * 0.56f;
        float frameH = h * 0.76f;

        canvas.drawRect(padX, padY, padX + frameW, padY + frameH, framePaint);

        // Render Hinges on left jamb
        float hingeTopY = padY + frameH * 0.12f;
        float hingeMidY = padY + frameH * 0.50f;
        float hingeBotY = padY + frameH * 0.88f;
        float hingeR = 7f;

        canvas.drawCircle(padX, hingeTopY, hingeR, hingePaint);
        canvas.drawCircle(padX, hingeMidY, hingeR, hingePaint);
        canvas.drawCircle(padX, hingeBotY, hingeR, hingePaint);

        // Calculate visual tilt angle (magnified for intuitive perception)
        float visualTiltDeg = (float) Math.min(sagAngleDeg * 3.5, 12.0);

        canvas.save();
        // Pivot around the top hinge
        canvas.rotate(visualTiltDeg, padX, hingeTopY);

        // Dynamic status stroke color
        if ("HINGE_BOUND".equals(status)) {
            doorPaint.setColor(Color.parseColor("#EF4444"));
        } else if ("TIGHT".equals(status)) {
            doorPaint.setColor(Color.parseColor("#F59E0B"));
        } else {
            doorPaint.setColor(Color.parseColor("#10B981"));
        }

        // Draw rotated door leaf
        canvas.drawRect(padX, padY, padX + frameW, padY + frameH, doorPaint);
        canvas.restore();

        // If sagging, show sag arc & deflection text at top-right latch edge
        if (sagDeflectionMm > 0) {
            sagArcPaint.setColor(doorPaint.getColor());
            float latchX = padX + frameW;
            RectF arcBounds = new RectF(latchX - 40, padY - 20, latchX + 40, padY + 60);
            canvas.drawArc(arcBounds, 0, (float) Math.max(sagAngleDeg * 4, 15), false, sagArcPaint);
            
            String readout = String.format("Sag: %.1f mm (%.2f°)", sagDeflectionMm, sagAngleDeg);
            canvas.drawText(readout, latchX, padY - 14, textPaint);
        } else {
            canvas.drawText("Plumb Baseline (0.0° Sag)", w / 2f, padY - 14, textPaint);
        }
    }
}