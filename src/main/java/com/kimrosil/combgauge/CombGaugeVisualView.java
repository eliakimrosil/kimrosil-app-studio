package com.kimrosil.combgauge;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public class CombGaugeVisualView extends View {

    public interface OnToothSelectedListener {
        void onToothSelected(double thicknessValue);
    }

    private Paint substratePaint;
    private Paint paintMeniscusPaint;
    private Paint combBodyPaint;
    private Paint toothStrokePaint;
    private Paint toothActivePaint;
    private Paint textPaint;
    private Paint guideLinePaint;

    private Path substratePath;
    private Path paintPath;
    private RectF tempRect;

    private int unit = CoatingCalculatorEngine.UNIT_MICRONS;
    private double currentThickness = 125.0;
    private int profileType = CoatingCalculatorEngine.PROFILE_FINE_25;
    private boolean isWftMode = true;

    private OnToothSelectedListener listener;
    private int highlightedToothIndex = 3;

    private static final int TOOTH_COUNT = 8;
    private final double[] teethValuesMicrons = {25, 50, 75, 100, 150, 200, 250, 300};
    private final double[] teethValuesMils = {1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 10.0, 12.0};

    public CombGaugeVisualView(Context context) {
        super(context);
        init();
    }

    public CombGaugeVisualView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CombGaugeVisualView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        tempRect = new RectF();
        substratePath = new Path();
        paintPath = new Path();

        float density = getResources().getDisplayMetrics().density;

        substratePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        substratePaint.setColor(0xFF475569);
        substratePaint.setStyle(Paint.Style.FILL);

        paintMeniscusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintMeniscusPaint.setStyle(Paint.Style.FILL);

        combBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        combBodyPaint.setColor(0xFFD97706);
        combBodyPaint.setStyle(Paint.Style.FILL);

        toothStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        toothStrokePaint.setStyle(Paint.Style.STROKE);
        toothStrokePaint.setStrokeWidth(2.0f * density);
        toothStrokePaint.setColor(0xFF0F172A);

        toothActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        toothActivePaint.setStyle(Paint.Style.STROKE);
        toothActivePaint.setStrokeWidth(3.5f * density);
        toothActivePaint.setColor(0xFF0284C7);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFF8FAFC);
        textPaint.setTextSize(11.0f * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        guideLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guideLinePaint.setStyle(Paint.Style.STROKE);
        guideLinePaint.setStrokeWidth(1.5f * density);
        guideLinePaint.setColor(0xFF38BDF8);
    }

    public void setOnToothSelectedListener(OnToothSelectedListener l) {
        this.listener = l;
    }

    public void setParameters(double thickness, int unit, int profileType, boolean isWftMode) {
        this.currentThickness = Math.max(0.0, thickness);
        this.unit = unit;
        this.profileType = profileType;
        this.isWftMode = isWftMode;

        updateClosestToothIndex();
        invalidate();
    }

    private void updateClosestToothIndex() {
        double[] vals = (unit == CoatingCalculatorEngine.UNIT_MICRONS) ? teethValuesMicrons : teethValuesMils;
        double minDiff = Double.MAX_VALUE;
        int closest = 0;
        for (int i = 0; i < vals.length; i++) {
            double diff = Math.abs(vals[i] - currentThickness);
            if (diff < minDiff) {
                minDiff = diff;
                closest = i;
            }
        }
        highlightedToothIndex = closest;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minH = (int) (190 * getResources().getDisplayMetrics().density);
        int h = resolveSize(minH, heightMeasureSpec);
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, Math.max(minH, h));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float padding = 16.0f * density;
        float usableWidth = w - (padding * 2.0f);

        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        // Visual layout bounds
        float groundTop = h - (36.0f * density);
        float combTop = padding;
        float combBackBottom = groundTop - (65.0f * density);

        // Substrate Anchor Roughness visualization
        drawSubstrate(canvas, w, h, groundTop, density, isDark);

        // Wet paint film visualization
        drawPaintLayer(canvas, padding, usableWidth, groundTop, density, isDark);

        // Comb structure and notched teeth
        drawComb(canvas, padding, usableWidth, combTop, combBackBottom, groundTop, density, isDark);
    }

    private void drawSubstrate(Canvas canvas, int w, int h, float groundTop, float density, boolean isDark) {
        substratePath.reset();
        substratePath.moveTo(0, groundTop);

        float amplitude = 0f;
        switch (profileType) {
            case CoatingCalculatorEngine.PROFILE_FINE_25:
                amplitude = 4.0f * density;
                break;
            case CoatingCalculatorEngine.PROFILE_MEDIUM_50:
                amplitude = 8.0f * density;
                break;
            case CoatingCalculatorEngine.PROFILE_COARSE_75:
                amplitude = 13.0f * density;
                break;
            case CoatingCalculatorEngine.PROFILE_SMOOTH:
            default:
                amplitude = 1.0f * density;
                break;
        }

        int step = (int) (12.0f * density);
        boolean peak = true;
        for (float x = 0; x <= w + step; x += step) {
            float y = groundTop + (peak ? -amplitude : amplitude);
            substratePath.lineTo(x, y);
            peak = !peak;
        }

        substratePath.lineTo(w, h);
        substratePath.lineTo(0, h);
        substratePath.close();

        substratePaint.setColor(isDark ? 0xFF1E293B : 0xFF64748B);
        canvas.drawPath(substratePath, substratePaint);
    }

    private void drawPaintLayer(Canvas canvas, float left, float width, float groundTop, float density, boolean isDark) {
        float maxScale = (unit == CoatingCalculatorEngine.UNIT_MICRONS) ? 350.0f : 14.0f;
        float norm = (float) Math.min(1.0, currentThickness / maxScale);
        float paintHeight = Math.max(6.0f * density, norm * (48.0f * density));
        float paintTop = groundTop - paintHeight;

        paintPath.reset();
        paintPath.moveTo(left, groundTop + (4.0f * density));
        paintPath.lineTo(left, paintTop);
        paintPath.quadTo(left + (width * 0.5f), paintTop - (3.0f * density), left + width, paintTop);
        paintPath.lineTo(left + width, groundTop + (4.0f * density));
        paintPath.close();

        int colorStart = isDark ? 0xB338BDF8 : 0xCC0284C7;
        int colorEnd = isDark ? 0x800284C7 : 0x990369A1;
        paintMeniscusPaint.setShader(new LinearGradient(left, paintTop, left, groundTop,
                colorStart, colorEnd, Shader.TileMode.CLAMP));

        canvas.drawPath(paintPath, paintMeniscusPaint);

        // Meniscus surface tension shine line
        paintMeniscusPaint.setShader(null);
        paintMeniscusPaint.setColor(0xE6FFFFFF);
        paintMeniscusPaint.setStyle(Paint.Style.STROKE);
        paintMeniscusPaint.setStrokeWidth(1.5f * density);

        Path line = new Path();
        line.moveTo(left, paintTop);
        line.quadTo(left + (width * 0.5f), paintTop - (3.0f * density), left + width, paintTop);
        canvas.drawPath(line, paintMeniscusPaint);
        paintMeniscusPaint.setStyle(Paint.Style.FILL);
    }

    private void drawComb(Canvas canvas, float left, float width, float top, float backBottom,
                          float groundTop, float density, boolean isDark) {
        combBodyPaint.setColor(isDark ? 0xFFF59E0B : 0xFFD97706);

        // Draw solid upper comb spine
        tempRect.set(left, top, left + width, backBottom);
        canvas.drawRoundRect(tempRect, 8.0f * density, 8.0f * density, combBodyPaint);

        float toothWidth = width / (TOOTH_COUNT * 2 - 1);
        double[] vals = (unit == CoatingCalculatorEngine.UNIT_MICRONS) ? teethValuesMicrons : teethValuesMils;

        // Outer base datum shoulders
        float leftShoulderX = left;
        tempRect.set(leftShoulderX, backBottom - (2.0f * density), leftShoulderX + (toothWidth * 0.8f), groundTop);
        canvas.drawRect(tempRect, combBodyPaint);

        float rightShoulderX = left + width - (toothWidth * 0.8f);
        tempRect.set(rightShoulderX, backBottom - (2.0f * density), rightShoulderX + (toothWidth * 0.8f), groundTop);
        canvas.drawRect(tempRect, combBodyPaint);

        // Draw calibrated teeth stepped down in length
        float maxToothDrop = groundTop - backBottom - (6.0f * density);

        for (int i = 0; i < TOOTH_COUNT; i++) {
            float toothLeft = left + (toothWidth * (i * 2 + 0.5f));
            float toothRight = toothLeft + toothWidth;

            // Progressive notch depth: tooth 0 is longest clearance (shortest tooth), last tooth descends nearest to steel
            float clearanceFraction = (float) (1.0 - ((double) (i + 1) / (TOOTH_COUNT + 1)));
            float toothBottom = groundTop - (clearanceFraction * maxToothDrop);

            tempRect.set(toothLeft, backBottom - (2.0f * density), toothRight, toothBottom);

            boolean isCurrentTooth = (i == highlightedToothIndex);

            combBodyPaint.setColor(isCurrentTooth ? (isDark ? 0xFFFBBF24 : 0xFFB45309) : (isDark ? 0xFFD97706 : 0xFFB45309));
            canvas.drawRect(tempRect, combBodyPaint);

            // High-visibility notch outline
            if (isCurrentTooth) {
                toothActivePaint.setColor(isDark ? 0xFF38BDF8 : 0xFF0284C7);
                canvas.drawRect(tempRect, toothActivePaint);

                // Indicator beacon arrow above tooth
                float arrowX = (toothLeft + toothRight) / 2.0f;
                float arrowY = top - (2.0f * density);
                guideLinePaint.setColor(isDark ? 0xFF38BDF8 : 0xFF0284C7);
                canvas.drawLine(arrowX, top + (2.0f * density), arrowX, backBottom, guideLinePaint);
            }

            // Tooth label in spine area
            String label = (unit == CoatingCalculatorEngine.UNIT_MICRONS)
                    ? String.valueOf((int) vals[i])
                    : String.format(java.util.Locale.US, "%.0f", vals[i]);
            textPaint.setColor(isDark ? 0xFF0F172A : 0xFFFFFFFF);
            canvas.drawText(label, (toothLeft + toothRight) / 2.0f, backBottom - (8.0f * density), textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float x = event.getX();
            float density = getResources().getDisplayMetrics().density;
            float padding = 16.0f * density;
            float usableWidth = getWidth() - (padding * 2.0f);
            float toothSlotWidth = usableWidth / TOOTH_COUNT;

            int index = (int) ((x - padding) / toothSlotWidth);
            if (index < 0) index = 0;
            if (index >= TOOTH_COUNT) index = TOOTH_COUNT - 1;

            if (index != highlightedToothIndex) {
                highlightedToothIndex = index;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);

                double[] vals = (unit == CoatingCalculatorEngine.UNIT_MICRONS) ? teethValuesMicrons : teethValuesMils;
                double selectedVal = vals[index];
                if (listener != null) {
                    listener.onToothSelected(selectedVal);
                }
                invalidate();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}