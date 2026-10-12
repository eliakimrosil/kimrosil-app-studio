package com.kimrosil.flangematch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class FlangeDiagramView extends View {

    private int boltCount = 8;
    private int activeBoltIndex = 1; // 1-based bolt ID (current focus)
    private final List<Integer> completedBoltsInPass = new ArrayList<>();
    private int[] torqueSequence = new int[]{1, 5, 3, 7, 2, 6, 4, 8};

    // Styling paints
    private Paint flangeBodyPaint;
    private Paint flangeRimPaint;
    private Paint pipeBorePaint;
    private Paint bcdGuidePaint;
    private Paint boltPaint;
    private Paint boltCompletedPaint;
    private Paint boltActivePaint;
    private Paint boltStrokePaint;
    private Paint textPaint;
    private Paint sequenceLinePaint;
    private Paint crosshairPaint;

    private final RectF bounds = new RectF();
    private final PointF center = new PointF();
    private float outerRadius;
    private float bcdRadius;
    private float boreRadius;
    private float boltRadius;

    private OnBoltClickListener boltClickListener;

    public interface OnBoltClickListener {
        void onBoltClicked(int boltNumber);
    }

    public FlangeDiagramView(Context context) {
        super(context);
        init();
    }

    public FlangeDiagramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FlangeDiagramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        flangeBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flangeBodyPaint.setStyle(Paint.Style.FILL);
        flangeBodyPaint.setColor(0xFF1E2836); // Steel Slate dark

        flangeRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flangeRimPaint.setStyle(Paint.Style.STROKE);
        flangeRimPaint.setColor(0xFF4A6572);
        flangeRimPaint.setStrokeWidth(6f);

        pipeBorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipeBorePaint.setStyle(Paint.Style.FILL);
        pipeBorePaint.setColor(0xFF0F141C); // Deep bore interior

        bcdGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bcdGuidePaint.setStyle(Paint.Style.STROKE);
        bcdGuidePaint.setColor(0xFF00ADB5); // Cyan technical datum
        bcdGuidePaint.setStrokeWidth(2.5f);
        bcdGuidePaint.setPathEffect(new DashPathEffect(new float[]{12f, 8f}, 0));

        crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setColor(0x4000ADB5);
        crosshairPaint.setStrokeWidth(1.5f);

        boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boltPaint.setStyle(Paint.Style.FILL);
        boltPaint.setColor(0xFF34495E); // Inactive bolt steel

        boltCompletedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boltCompletedPaint.setStyle(Paint.Style.FILL);
        boltCompletedPaint.setColor(0xFF2ECC71); // Verified green

        boltActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boltActivePaint.setStyle(Paint.Style.FILL);
        boltActivePaint.setColor(0xFFFFA500); // High-vis amber warning

        boltStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boltStrokePaint.setStyle(Paint.Style.STROKE);
        boltStrokePaint.setColor(0xFFFFFFFF);
        boltStrokePaint.setStrokeWidth(3f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        sequenceLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sequenceLinePaint.setStyle(Paint.Style.STROKE);
        sequenceLinePaint.setColor(0x88FFA500);
        sequenceLinePaint.setStrokeWidth(3f);
        sequenceLinePaint.setPathEffect(new DashPathEffect(new float[]{10f, 6f}, 0));
    }

    public void setFlangeConfig(int boltCount, int[] sequence) {
        this.boltCount = Math.max(4, boltCount);
        this.torqueSequence = sequence != null ? sequence : TorquePatternCalculator.getStarSequence(this.boltCount);
        this.completedBoltsInPass.clear();
        this.activeBoltIndex = this.torqueSequence.length > 0 ? this.torqueSequence[0] : 1;
        invalidate();
    }

    public void setActiveBolt(int boltNumber) {
        this.activeBoltIndex = boltNumber;
        invalidate();
    }

    public void setCompletedBolts(List<Integer> completed) {
        this.completedBoltsInPass.clear();
        if (completed != null) {
            this.completedBoltsInPass.addAll(completed);
        }
        invalidate();
    }

    public void setOnBoltClickListener(OnBoltClickListener listener) {
        this.boltClickListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounds.set(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
        center.set(bounds.centerX(), bounds.centerY());

        float size = Math.min(bounds.width(), bounds.height());
        outerRadius = size * 0.46f;
        bcdRadius = outerRadius * 0.72f;
        boreRadius = outerRadius * 0.38f;
        boltRadius = Math.max(14f, (float) (2 * Math.PI * bcdRadius / (boltCount * 4.2)));
        textPaint.setTextSize(boltRadius * 1.1f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (outerRadius <= 0) return;

        // Draw crosshair axes
        canvas.drawLine(center.x - outerRadius - 15, center.y, center.x + outerRadius + 15, center.y, crosshairPaint);
        canvas.drawLine(center.x, center.y - outerRadius - 15, center.x, center.y + outerRadius + 15, crosshairPaint);

        // Draw outer flange steel ring
        canvas.drawCircle(center.x, center.y, outerRadius, flangeBodyPaint);
        canvas.drawCircle(center.x, center.y, outerRadius, flangeRimPaint);

        // Draw Bolt Circle Diameter guide (BCD)
        canvas.drawCircle(center.x, center.y, bcdRadius, bcdGuidePaint);

        // Draw internal pipe bore (inner fluid path)
        canvas.drawCircle(center.x, center.y, boreRadius, pipeBorePaint);
        canvas.drawCircle(center.x, center.y, boreRadius, flangeRimPaint);

        // Draw criss-cross sequencing guide vectors between completed nodes and active node
        if (torqueSequence != null && torqueSequence.length > 1) {
            for (int i = 0; i < torqueSequence.length - 1; i++) {
                int b1 = torqueSequence[i];
                int b2 = torqueSequence[i + 1];
                PointF p1 = getBoltCoordinates(b1);
                PointF p2 = getBoltCoordinates(b2);
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, sequenceLinePaint);
            }
        }

        // Draw individual bolt nodes
        for (int i = 1; i <= boltCount; i++) {
            PointF p = getBoltCoordinates(i);

            Paint currentPaint = boltPaint;
            if (i == activeBoltIndex) {
                currentPaint = boltActivePaint;
            } else if (completedBoltsInPass.contains(i)) {
                currentPaint = boltCompletedPaint;
            }

            canvas.drawCircle(p.x, p.y, boltRadius, currentPaint);
            canvas.drawCircle(p.x, p.y, boltRadius, boltStrokePaint);

            // Bolt Number text
            String numStr = String.valueOf(i);
            float textOffset = (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(numStr, p.x, p.y - textOffset, textPaint);
        }
    }

    private PointF getBoltCoordinates(int boltNumber) {
        // Bolt 1 starts at top (270 degrees / -PI/2) and goes clockwise
        double angleStep = 2.0 * Math.PI / boltCount;
        double angle = -Math.PI / 2.0 + (boltNumber - 1) * angleStep;

        float x = (float) (center.x + bcdRadius * Math.cos(angle));
        float y = (float) (center.y + bcdRadius * Math.sin(angle));
        return new PointF(x, y);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && boltClickListener != null) {
            float tx = event.getX();
            float ty = event.getY();

            for (int i = 1; i <= boltCount; i++) {
                PointF p = getBoltCoordinates(i);
                float distSq = (tx - p.x) * (tx - p.x) + (ty - p.y) * (ty - p.y);
                float hitRadius = boltRadius * 1.5f;
                if (distSq <= hitRadius * hitRadius) {
                    boltClickListener.onBoltClicked(i);
                    return true;
                }
            }
        }
        return true;
    }
}