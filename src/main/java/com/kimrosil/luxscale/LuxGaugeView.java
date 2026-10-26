package com.kimrosil.luxscale;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.Locale;

/**
 * Custom 180-Degree Logarithmic Optical Photometer & PAR Spectrum Gauge View.
 * Pre-allocates all Paint and RectF objects to maintain zero allocations inside onDraw().
 */
public class LuxGaugeView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zoneCyanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zoneEmeraldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zoneAmberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private double currentLux = 450.0;
    private double peakLux = 680.0;
    private double currentPpfd = 8.3;

    public LuxGaugeView(Context context) {
        super(context);
        init(context);
    }

    public LuxGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LuxGaugeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float strokePx = dpToPx(context, 9f);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokePx);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(context.getColor(R.color.gauge_track));

        zoneCyanPaint.setStyle(Paint.Style.STROKE);
        zoneCyanPaint.setStrokeWidth(strokePx);
        zoneCyanPaint.setStrokeCap(Paint.Cap.BUTT);
        zoneCyanPaint.setColor(context.getColor(R.color.gauge_cyan));

        zoneEmeraldPaint.setStyle(Paint.Style.STROKE);
        zoneEmeraldPaint.setStrokeWidth(strokePx);
        zoneEmeraldPaint.setStrokeCap(Paint.Cap.BUTT);
        zoneEmeraldPaint.setColor(context.getColor(R.color.gauge_emerald));

        zoneAmberPaint.setStyle(Paint.Style.STROKE);
        zoneAmberPaint.setStrokeWidth(strokePx);
        zoneAmberPaint.setStrokeCap(Paint.Cap.BUTT);
        zoneAmberPaint.setColor(context.getColor(R.color.gauge_amber));

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeWidth(dpToPx(context, 2.8f));
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needlePaint.setColor(context.getColor(R.color.gauge_needle));

        peakPaint.setStyle(Paint.Style.STROKE);
        peakPaint.setStrokeWidth(dpToPx(context, 2.2f));
        peakPaint.setStrokeCap(Paint.Cap.ROUND);
        peakPaint.setColor(context.getColor(R.color.accent_amber));

        hubPaint.setStyle(Paint.Style.FILL);
        hubPaint.setColor(context.getColor(R.color.accent_primary));

        labelPaint.setTextSize(spToPx(context, 9.5f));
        labelPaint.setColor(context.getColor(R.color.text_muted));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        centerValuePaint.setTextSize(spToPx(context, 11.5f));
        centerValuePaint.setFakeBoldText(true);
        centerValuePaint.setColor(context.getColor(R.color.text_primary));
        centerValuePaint.setTextAlign(Paint.Align.CENTER);
    }

    public void updateReadings(double lux, double peak, double ppfd) {
        this.currentLux = LuxEngine.clamp(lux, 0.0, 200000.0);
        this.peakLux = LuxEngine.clamp(Math.max(lux, peak), 0.0, 200000.0);
        this.currentPpfd = LuxEngine.clamp(ppfd, 0.0, 5000.0);
        invalidate();
    }

    /**
     * Maps 0..50,000 Lux onto a normalized 0..1 logarithmic progress scale.
     */
    private float luxToNormalized(double lux) {
        if (lux <= 1.0) {
            return 0f;
        }
        double logMax = Math.log10(50000.0);
        double logVal = Math.log10(Math.min(50000.0, Math.max(1.0, lux)));
        return (float) LuxEngine.clamp(logVal / logMax, 0.0, 1.0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float topReserved = dpToPx(getContext(), 22f);
        float bottomReserved = dpToPx(getContext(), 18f);
        float radius = Math.min(w * 0.34f, h - topReserved - bottomReserved);
        if (radius < 20f) {
            radius = 20f;
        }
        float cx = w * 0.5f;
        float cy = h - bottomReserved;

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Background 180-degree track
        canvas.drawArc(arcBounds, 180f, 180f, false, trackPaint);

        // 3 Color-Coded Logarithmic Spectrum Zones:
        // Zone 1: 0..300 lx (0..52% log scale -> 94 deg)
        canvas.drawArc(arcBounds, 180f, 92f, false, zoneCyanPaint);
        // Zone 2: 300..8,000 lx (52%..83% log scale -> 56 deg)
        canvas.drawArc(arcBounds, 274f, 54f, false, zoneEmeraldPaint);
        // Zone 3: 8,000..50,000 lx (83%..100% log scale -> 30 deg)
        canvas.drawArc(arcBounds, 330f, 30f, false, zoneAmberPaint);

        // Scale Endpoint Labels
        canvas.drawText("0 lx", cx - radius - dpToPx(getContext(), 18f), cy - dpToPx(getContext(), 2f), labelPaint);
        canvas.drawText("500 lx", cx, cy - radius - dpToPx(getContext(), 8f), labelPaint);
        canvas.drawText("50k lx", cx + radius + dpToPx(getContext(), 20f), cy - dpToPx(getContext(), 2f), labelPaint);

        // Peak Marker Tick on Arc
        float peakNorm = luxToNormalized(peakLux);
        double peakRad = Math.toRadians(180.0 + peakNorm * 180.0);
        float px1 = cx + (float) Math.cos(peakRad) * (radius - dpToPx(getContext(), 7f));
        float py1 = cy + (float) Math.sin(peakRad) * (radius - dpToPx(getContext(), 7f));
        float px2 = cx + (float) Math.cos(peakRad) * (radius + dpToPx(getContext(), 7f));
        float py2 = cy + (float) Math.sin(peakRad) * (radius + dpToPx(getContext(), 7f));
        canvas.drawLine(px1, py1, px2, py2, peakPaint);

        // Live Photometer Needle
        float norm = luxToNormalized(currentLux);
        double angleRad = Math.toRadians(180.0 + norm * 180.0);
        float nx = cx + (float) Math.cos(angleRad) * (radius - dpToPx(getContext(), 6f));
        float ny = cy + (float) Math.sin(angleRad) * (radius - dpToPx(getContext(), 6f));
        canvas.drawLine(cx, cy, nx, ny, needlePaint);
        canvas.drawCircle(cx, cy, dpToPx(getContext(), 4.5f), hubPaint);

        // Compact Digital Readout Caption under Hub
        String summary = String.format(Locale.US, "%.0f lx  •  %.1f PPFD", currentLux, currentPpfd);
        canvas.drawText(summary, cx, cy + dpToPx(getContext(), 13f), centerValuePaint);
    }

    private static float dpToPx(Context ctx, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }

    private static float spToPx(Context ctx, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, ctx.getResources().getDisplayMetrics());
    }
}
