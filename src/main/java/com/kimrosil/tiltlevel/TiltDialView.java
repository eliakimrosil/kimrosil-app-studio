package com.kimrosil.tiltlevel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.Locale;

/**
 * Custom 180-Degree Machinist Clinometer Protractor & Illuminated Spirit Vial View.
 * Pre-allocates all Paint and RectF objects to maintain zero allocations inside onDraw().
 */
public class TiltDialView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint levelZonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slopeZonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vialBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vialBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint summaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcBounds = new RectF();
    private final RectF vialBounds = new RectF();

    private double currentAngleDeg = 4.76;
    private double currentGradePct = 8.33;
    private double currentPitchX12 = 1.00;

    public TiltDialView(Context context) {
        super(context);
        init(context);
    }

    public TiltDialView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TiltDialView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float strokePx = dpToPx(context, 8.5f);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokePx);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(context.getColor(R.color.dial_track));

        slopeZonePaint.setStyle(Paint.Style.STROKE);
        slopeZonePaint.setStrokeWidth(strokePx);
        slopeZonePaint.setStrokeCap(Paint.Cap.ROUND);
        slopeZonePaint.setColor(context.getColor(R.color.dial_cyan));

        levelZonePaint.setStyle(Paint.Style.STROKE);
        levelZonePaint.setStrokeWidth(strokePx + dpToPx(context, 1f));
        levelZonePaint.setStrokeCap(Paint.Cap.BUTT);
        levelZonePaint.setColor(context.getColor(R.color.dial_lime));

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeWidth(dpToPx(context, 2.6f));
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needlePaint.setColor(context.getColor(R.color.dial_needle));

        hubPaint.setStyle(Paint.Style.FILL);
        hubPaint.setColor(context.getColor(R.color.accent_primary));

        vialBgPaint.setStyle(Paint.Style.FILL);
        vialBgPaint.setColor(context.getColor(R.color.vial_fill));

        vialBorderPaint.setStyle(Paint.Style.STROKE);
        vialBorderPaint.setStrokeWidth(dpToPx(context, 1.4f));
        vialBorderPaint.setColor(context.getColor(R.color.accent_primary));

        bubblePaint.setStyle(Paint.Style.FILL);
        bubblePaint.setColor(context.getColor(R.color.dial_lime));

        labelPaint.setTextSize(spToPx(context, 9.5f));
        labelPaint.setColor(context.getColor(R.color.text_muted));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        summaryPaint.setTextSize(spToPx(context, 11f));
        summaryPaint.setFakeBoldText(true);
        summaryPaint.setColor(context.getColor(R.color.text_primary));
        summaryPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void updateReadings(double netAngleDeg, double gradePct, double pitchX12) {
        this.currentAngleDeg = TiltEngine.clamp(netAngleDeg, -88.0, 88.0);
        this.currentGradePct = TiltEngine.clamp(gradePct, 0.0, 999.9);
        this.currentPitchX12 = TiltEngine.clamp(pitchX12, 0.0, 120.0);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float topReserved = dpToPx(getContext(), 20f);
        float bottomReserved = dpToPx(getContext(), 28f);
        float radius = Math.min(w * 0.30f, h - topReserved - bottomReserved);
        if (radius < 18f) {
            radius = 18f;
        }

        float cx = w * 0.5f;
        float cy = h - bottomReserved;

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Protractor Arc (-45° left to +45° right mapped onto 180..360 deg canvas arc)
        canvas.drawArc(arcBounds, 180f, 180f, false, trackPaint);
        canvas.drawArc(arcBounds, 210f, 120f, false, slopeZonePaint);
        // True Plumb 0.0° Center Segment (260..280 deg)
        canvas.drawArc(arcBounds, 258f, 24f, false, levelZonePaint);

        // Scale Labels
        canvas.drawText("-45°", cx - radius - dpToPx(getContext(), 18f), cy - dpToPx(getContext(), 2f), labelPaint);
        canvas.drawText("0.0° LEVEL", cx, cy - radius - dpToPx(getContext(), 7f), labelPaint);
        canvas.drawText("+45°", cx + radius + dpToPx(getContext(), 18f), cy - dpToPx(getContext(), 2f), labelPaint);

        // Clinometer Plumb Needle (-45..+45 mapped to 180..360 deg; 0 deg = 270 deg top)
        float clamped45 = (float) TiltEngine.clamp(currentAngleDeg, -45.0, 45.0);
        double needleDeg = 270.0 + (clamped45 / 45.0) * 90.0;
        double needleRad = Math.toRadians(needleDeg);
        float nx = cx + (float) Math.cos(needleRad) * (radius - dpToPx(getContext(), 5f));
        float ny = cy + (float) Math.sin(needleRad) * (radius - dpToPx(getContext(), 5f));
        canvas.drawLine(cx, cy, nx, ny, needlePaint);
        canvas.drawCircle(cx, cy, dpToPx(getContext(), 4f), hubPaint);

        // Horizontal Spirit Vial Tube below Protractor Pivot
        float vialW = Math.min(w * 0.56f, dpToPx(getContext(), 190f));
        float vialH = dpToPx(getContext(), 16f);
        float vialTop = cy + dpToPx(getContext(), 7f);
        vialBounds.set(cx - vialW * 0.5f, vialTop, cx + vialW * 0.5f, vialTop + vialH);
        float cornerR = vialH * 0.5f;
        canvas.drawRoundRect(vialBounds, cornerR, cornerR, vialBgPaint);
        canvas.drawRoundRect(vialBounds, cornerR, cornerR, vialBorderPaint);

        // Floating Spirit Bubble (displaced opposite to tilt angle, clamped inside vial)
        float maxTravel = vialW * 0.36f;
        float bubbleOffset = -(clamped45 / 45.0f) * maxTravel;
        float bx = cx + bubbleOffset;
        float by = vialTop + vialH * 0.5f;
        canvas.drawCircle(bx, by, dpToPx(getContext(), 5.5f), bubblePaint);

        // Center Vial Hairlines
        float markOffset = dpToPx(getContext(), 10f);
        canvas.drawLine(cx - markOffset, vialTop, cx - markOffset, vialTop + vialH, vialBorderPaint);
        canvas.drawLine(cx + markOffset, vialTop, cx + markOffset, vialTop + vialH, vialBorderPaint);

        // Side Readout Badges beside Spirit Vial
        String leftTxt = String.format(Locale.US, "%.2f°", Math.abs(currentAngleDeg));
        String rightTxt = String.format(Locale.US, "%.1f%%", currentGradePct);
        canvas.drawText(leftTxt, vialBounds.left - dpToPx(getContext(), 28f), by + dpToPx(getContext(), 4f), summaryPaint);
        canvas.drawText(rightTxt, vialBounds.right + dpToPx(getContext(), 28f), by + dpToPx(getContext(), 4f), summaryPaint);
    }

    private static float dpToPx(Context ctx, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }

    private static float spToPx(Context ctx, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, ctx.getResources().getDisplayMetrics());
    }
}
