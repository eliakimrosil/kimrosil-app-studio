package com.kimrosil.weldbead;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Interactive bespoke welding arc and groove telemetry canvas.
 * Renders V-groove joint geometry and dynamic molten bead reinforcement.
 */
public class WeldArcView extends View {

    private final Paint platePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plateStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beadStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private double heatInput = 18.0; // kJ/in
    private double depositionRate = 3.5; // lb/hr
    private int process = 1; // 0=SMAW, 1=GMAW, 2=GTAW, 3=FCAW, 4=SAW
    private boolean isMetric = false;

    public WeldArcView(Context context) {
        super(context);
        init();
    }

    public WeldArcView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WeldArcView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        platePaint.setStyle(Paint.Style.FILL);
        plateStroke.setStyle(Paint.Style.STROKE);
        plateStroke.setStrokeWidth(2.5f);

        beadPaint.setStyle(Paint.Style.FILL);
        beadStroke.setStyle(Paint.Style.STROKE);
        beadStroke.setStrokeWidth(2.0f);

        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeWidth(1.8f);
        ripplePaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setTextSize(26f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void updateTelemetry(double heatInput, double depositionRate, int process, boolean isMetric) {
        this.heatInput = heatInput;
        this.depositionRate = depositionRate;
        this.process = process;
        this.isMetric = isMetric;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = (int) (160 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float cx = width / 2f;
        float cy = height * 0.62f;

        // Joint dimensions
        float plateThick = height * 0.32f;
        float grooveTopHalfW = width * 0.16f;
        float rootHalfW = width * 0.035f;

        // Dynamic bead height based on heat input / deposition
        float normalizedHi = (float) Math.min(Math.max(heatInput, 5.0), 60.0);
        float beadHeight = plateThick * (0.35f + (normalizedHi / 60.0f) * 0.45f);
        float beadPenetration = plateThick * (0.20f + (normalizedHi / 60.0f) * 0.35f);

        // Steel plates color
        int plateColor = 0xFF243044;
        int plateEdgeColor = 0xFF475569;
        platePaint.setColor(plateColor);
        plateStroke.setColor(plateEdgeColor);

        // Draw Left Plate (60 deg bevel)
        Path leftPlate = new Path();
        leftPlate.moveTo(16f, cy - plateThick / 2);
        leftPlate.lineTo(cx - grooveTopHalfW, cy - plateThick / 2);
        leftPlate.lineTo(cx - rootHalfW, cy + plateThick / 2);
        leftPlate.lineTo(16f, cy + plateThick / 2);
        leftPlate.close();
        canvas.drawPath(leftPlate, platePaint);
        canvas.drawPath(leftPlate, plateStroke);

        // Draw Right Plate
        Path rightPlate = new Path();
        rightPlate.moveTo(width - 16f, cy - plateThick / 2);
        rightPlate.lineTo(cx + grooveTopHalfW, cy - plateThick / 2);
        rightPlate.lineTo(cx + rootHalfW, cy + plateThick / 2);
        rightPlate.lineTo(width - 16f, cy + plateThick / 2);
        rightPlate.close();
        canvas.drawPath(rightPlate, platePaint);
        canvas.drawPath(rightPlate, plateStroke);

        // Arc Glow Shader
        RadialGradient glow = new RadialGradient(
                cx, cy - plateThick / 2,
                width * 0.28f,
                new int[]{0x9900E5FF, 0x44FF9500, 0x00000000},
                new float[]{0.0f, 0.45f, 1.0f},
                Shader.TileMode.CLAMP
        );
        arcGlowPaint.setShader(glow);
        canvas.drawCircle(cx, cy - plateThick / 2, width * 0.28f, arcGlowPaint);

        // Weld Bead Gradient (Molten Amber to Golden Crust)
        LinearGradient beadGrad = new LinearGradient(
                cx, cy - plateThick / 2 - beadHeight,
                cx, cy + plateThick / 2 + beadPenetration,
                new int[]{0xFFFFD54F, 0xFFFF9500, 0xFFE65100},
                null,
                Shader.TileMode.CLAMP
        );
        beadPaint.setShader(beadGrad);
        beadStroke.setColor(0xFFFFB74D);

        // Draw Weld Bead Contour
        Path beadPath = new Path();
        // Top reinforcement cap
        beadPath.moveTo(cx - grooveTopHalfW * 1.05f, cy - plateThick / 2 + 2);
        beadPath.quadTo(cx, cy - plateThick / 2 - beadHeight, cx + grooveTopHalfW * 1.05f, cy - plateThick / 2 + 2);
        // Right fusion line down into root
        beadPath.lineTo(cx + rootHalfW * 1.1f, cy + plateThick / 2);
        // Penetration root underside
        beadPath.quadTo(cx, cy + plateThick / 2 + beadPenetration, cx - rootHalfW * 1.1f, cy + plateThick / 2);
        // Left fusion line up to top
        beadPath.close();

        canvas.drawPath(beadPath, beadPaint);
        canvas.drawPath(beadPath, beadStroke);

        // Solidified Ripple Rings
        ripplePaint.setColor(0xFFFFF9C4);
        for (int i = 1; i <= 3; i++) {
            float frac = i * 0.25f;
            float rx = grooveTopHalfW * (0.9f - frac * 0.25f);
            float ry = (cy - plateThick / 2) - beadHeight * (1.0f - frac);
            canvas.drawLine(cx - rx, ry, cx + rx, ry, ripplePaint);
        }

        // Telemetry annotations
        textPaint.setColor(0xFF94A3B8);
        textPaint.setTextSize(24f);
        canvas.drawText("ASME QW-409.1 BEAD CROSS SECTION", cx, height * 0.16f, textPaint);

        String statStr = String.format("Penetration: ~%.1f%%  |  Reinforcement: ~%.1f mm",
                (beadPenetration / plateThick) * 100f,
                beadHeight * 0.25f);
        textPaint.setTextSize(22f);
        textPaint.setColor(0xFFFF9500);
        canvas.drawText(statStr, cx, height * 0.94f, textPaint);
    }
}
