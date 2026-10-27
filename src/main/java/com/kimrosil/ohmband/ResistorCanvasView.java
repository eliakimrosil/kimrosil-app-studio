package com.kimrosil.ohmband;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class ResistorCanvasView extends View {

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int bandCount = 4; // 4, 5, or 6
    private int[] bandColors = {
            OhmEngine.COLOR_BROWN,
            OhmEngine.COLOR_BLACK,
            OhmEngine.COLOR_RED,
            OhmEngine.COLOR_GOLD,
            0,
            0
    };
    private int selectedBandIndex = -1;

    private final Path bodyPath = new Path();
    private final RectF bandRect = new RectF();

    public ResistorCanvasView(Context context) {
        super(context);
        init();
    }

    public ResistorCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ResistorCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bodyPaint.setStyle(Paint.Style.FILL);

        leadPaint.setStyle(Paint.Style.STROKE);
        leadPaint.setStrokeCap(Paint.Cap.ROUND);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(1.5f));

        bandPaint.setStyle(Paint.Style.FILL);

        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(Color.argb(80, 255, 255, 255));

        selectGlowPaint.setStyle(Paint.Style.STROKE);
        selectGlowPaint.setStrokeWidth(dpToPx(2.5f));
        selectGlowPaint.setColor(Color.parseColor("#F59E0B"));
    }

    public void setBands(int count, int[] colors, int activeIndex) {
        this.bandCount = count;
        if (colors != null) {
            System.arraycopy(colors, 0, this.bandColors, 0, Math.min(colors.length, bandColors.length));
        }
        this.selectedBandIndex = activeIndex;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = dpToPx(320);
        int desiredHeight = dpToPx(115);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width = (widthMode == MeasureSpec.EXACTLY) ? widthSize : desiredWidth;
        int height = (heightMode == MeasureSpec.EXACTLY) ? heightSize : desiredHeight;

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean isDark = isDarkTheme();
        int bodyColor = isDark ? Color.parseColor("#262016") : Color.parseColor("#EADBC0");
        int leadColor = isDark ? Color.parseColor("#64748B") : Color.parseColor("#94A3B8");
        int borderColor = isDark ? Color.parseColor("#3E3524") : Color.parseColor("#C2B194");

        float centerY = h * 0.5f;
        float leadWidth = dpToPx(5);
        leadPaint.setStrokeWidth(leadWidth);
        leadPaint.setColor(leadColor);

        // 1. Axial Metallic Leads
        float leadStartX = dpToPx(16);
        float leadEndX = w - dpToPx(16);
        canvas.drawLine(leadStartX, centerY, leadEndX, centerY, leadPaint);

        // 2. Resistor Body Dimensions
        float bodyWidth = Math.min(w * 0.65f, dpToPx(240));
        float bodyHeight = dpToPx(44);
        float bodyLeft = (w - bodyWidth) * 0.5f;
        float bodyRight = bodyLeft + bodyWidth;
        float bodyTop = centerY - bodyHeight * 0.5f;
        float bodyBottom = centerY + bodyHeight * 0.5f;

        float capBulgeW = dpToPx(18);
        float capBulgeH = dpToPx(6);

        bodyPath.reset();
        // Top-left bulged cap
        bodyPath.moveTo(bodyLeft, centerY);
        bodyPath.cubicTo(bodyLeft, bodyTop - capBulgeH, bodyLeft + capBulgeW, bodyTop - capBulgeH, bodyLeft + capBulgeW, bodyTop);
        // Top edge
        bodyPath.lineTo(bodyRight - capBulgeW, bodyTop);
        // Top-right bulged cap
        bodyPath.cubicTo(bodyRight - capBulgeW, bodyTop - capBulgeH, bodyRight, bodyTop - capBulgeH, bodyRight, centerY);
        // Bottom-right bulged cap
        bodyPath.cubicTo(bodyRight, bodyBottom + capBulgeH, bodyRight - capBulgeW, bodyBottom + capBulgeH, bodyRight - capBulgeW, bodyBottom);
        // Bottom edge
        bodyPath.lineTo(bodyLeft + capBulgeW, bodyBottom);
        // Bottom-left bulged cap
        bodyPath.cubicTo(bodyLeft + capBulgeW, bodyBottom + capBulgeH, bodyLeft, bodyBottom + capBulgeH, bodyLeft, centerY);
        bodyPath.close();

        // Shaded Body Fill
        LinearGradient grad = new LinearGradient(
                bodyLeft, bodyTop, bodyLeft, bodyBottom,
                isDark ? Color.parseColor("#382E20") : Color.parseColor("#F5EADB"),
                bodyColor,
                Shader.TileMode.CLAMP
        );
        bodyPaint.setShader(grad);
        canvas.drawPath(bodyPath, bodyPaint);
        bodyPaint.setShader(null);

        // Border
        borderPaint.setColor(borderColor);
        canvas.drawPath(bodyPath, borderPaint);

        // 3. Highlight Glare across upper body
        canvas.save();
        canvas.clipPath(bodyPath);
        canvas.drawRect(bodyLeft, bodyTop + dpToPx(3), bodyRight, bodyTop + dpToPx(9), highlightPaint);
        canvas.restore();

        // 4. Color Bands
        float usableWidth = bodyWidth - dpToPx(16);
        float bandW = dpToPx(10);
        float startX = bodyLeft + dpToPx(8);

        // Distribute bands evenly across the body
        float[] bandPositions = new float[bandCount];
        if (bandCount == 4) {
            bandPositions[0] = startX + usableWidth * 0.12f;
            bandPositions[1] = startX + usableWidth * 0.32f;
            bandPositions[2] = startX + usableWidth * 0.54f;
            bandPositions[3] = startX + usableWidth * 0.85f; // Tolerance band separated
        } else if (bandCount == 5) {
            bandPositions[0] = startX + usableWidth * 0.10f;
            bandPositions[1] = startX + usableWidth * 0.28f;
            bandPositions[2] = startX + usableWidth * 0.46f;
            bandPositions[3] = startX + usableWidth * 0.64f;
            bandPositions[4] = startX + usableWidth * 0.86f; // Tolerance separated
        } else { // 6-band
            bandPositions[0] = startX + usableWidth * 0.08f;
            bandPositions[1] = startX + usableWidth * 0.24f;
            bandPositions[2] = startX + usableWidth * 0.40f;
            bandPositions[3] = startX + usableWidth * 0.56f;
            bandPositions[4] = startX + usableWidth * 0.74f;
            bandPositions[5] = startX + usableWidth * 0.90f; // TCR
        }

        canvas.save();
        canvas.clipPath(bodyPath);
        for (int i = 0; i < bandCount; i++) {
            float bx = bandPositions[i];
            int colorCode = (i < bandColors.length) ? bandColors[i] : 0;
            int hexColor = OhmEngine.COLOR_HEX[Math.min(colorCode, OhmEngine.COLOR_HEX.length - 1)];

            bandPaint.setColor(hexColor);
            bandRect.set(bx, bodyTop - capBulgeH, bx + bandW, bodyBottom + capBulgeH);
            canvas.drawRect(bandRect, bandPaint);

            // Subtle band shading line
            Paint bandBorder = new Paint();
            bandBorder.setStyle(Paint.Style.STROKE);
            bandBorder.setStrokeWidth(dpToPx(0.8f));
            bandBorder.setColor(Color.argb(70, 0, 0, 0));
            canvas.drawRect(bandRect, bandBorder);
        }
        canvas.restore();

        // 5. Active Band Glow Outline (if selected)
        if (selectedBandIndex >= 0 && selectedBandIndex < bandCount) {
            float bx = bandPositions[selectedBandIndex];
            canvas.drawRoundRect(bx - dpToPx(2), bodyTop - dpToPx(3), bx + bandW + dpToPx(2), bodyBottom + dpToPx(3), dpToPx(3), dpToPx(3), selectGlowPaint);
        }
    }

    private boolean isDarkTheme() {
        int nightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
