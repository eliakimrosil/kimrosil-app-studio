package com.kimrosil.rebarlap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class RebarSpliceView extends View {

    private String barName = "#5";
    private double lapLengthInches = 30.5;
    private double barDiaInches = 0.625;

    private Paint concretePaint;
    private Paint concreteBorderPaint;
    private Paint rebarTopPaint;
    private Paint rebarBottomPaint;
    private Paint ribPaint;
    private Paint tieWirePaint;
    private Paint dimLinePaint;
    private Paint dimTextPaint;

    private final Path topBarPath = new Path();
    private final Path btmBarPath = new Path();
    private final RectF concreteRect = new RectF();

    public RebarSpliceView(Context context) {
        super(context);
        init();
    }

    public RebarSpliceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RebarSpliceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        concretePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        concretePaint.setStyle(Paint.Style.FILL);

        concreteBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        concreteBorderPaint.setStyle(Paint.Style.STROKE);
        concreteBorderPaint.setStrokeWidth(dpToPx(1f));

        rebarTopPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rebarTopPaint.setStyle(Paint.Style.STROKE);
        rebarTopPaint.setStrokeCap(Paint.Cap.ROUND);
        rebarTopPaint.setStrokeJoin(Paint.Join.ROUND);

        rebarBottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rebarBottomPaint.setStyle(Paint.Style.STROKE);
        rebarBottomPaint.setStrokeCap(Paint.Cap.ROUND);
        rebarBottomPaint.setStrokeJoin(Paint.Join.ROUND);

        ribPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ribPaint.setStyle(Paint.Style.STROKE);
        ribPaint.setStrokeCap(Paint.Cap.ROUND);
        ribPaint.setStrokeWidth(dpToPx(1.8f));

        tieWirePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tieWirePaint.setStyle(Paint.Style.STROKE);
        tieWirePaint.setStrokeWidth(dpToPx(2.2f));

        dimLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimLinePaint.setStyle(Paint.Style.STROKE);
        dimLinePaint.setStrokeWidth(dpToPx(1.2f));

        dimTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimTextPaint.setTextSize(spToPx(11f));
        dimTextPaint.setFakeBoldText(true);
        dimTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void updateSplice(String barName, double lapInches, double diaInches) {
        this.barName = barName;
        this.lapLengthInches = Math.max(12.0, lapInches);
        this.barDiaInches = Math.max(0.375, diaInches);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        boolean isDark = (getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES;

        int colorConcrete = isDark ? 0xFF141D30 : 0xFFE2E8F0;
        int colorConcreteBorder = isDark ? 0xFF243656 : 0xFFCBD5E1;
        int colorRebarTop = isDark ? 0xFF60A5FA : 0xFF2563EB;
        int colorRebarBtm = isDark ? 0xFF3B82F6 : 0xFF1D4ED8;
        int colorRibs = isDark ? 0xFF93C5FD : 0xFFDBEAFE;
        int colorTieWire = isDark ? 0xFFF59E0B : 0xFFD97706;
        int colorDim = isDark ? 0xFFFBBF24 : 0xFFB45309;

        concretePaint.setColor(colorConcrete);
        concreteBorderPaint.setColor(colorConcreteBorder);
        rebarTopPaint.setColor(colorRebarTop);
        rebarBottomPaint.setColor(colorRebarBtm);
        ribPaint.setColor(colorRibs);
        tieWirePaint.setColor(colorTieWire);
        dimLinePaint.setColor(colorDim);
        dimTextPaint.setColor(colorDim);

        float barThick = Math.min(dpToPx(9f), Math.max(dpToPx(4.5f), (float) (barDiaInches * dpToPx(8.5f))));
        rebarTopPaint.setStrokeWidth(barThick);
        rebarBottomPaint.setStrokeWidth(barThick);

        float padLeft = dpToPx(16);
        float padRight = dpToPx(16);
        float padTop = dpToPx(14);
        float padBottom = dpToPx(24); // Room for dimension line at bottom

        concreteRect.set(padLeft, padTop, w - padRight, h - padBottom);
        canvas.drawRoundRect(concreteRect, dpToPx(6), dpToPx(6), concretePaint);
        canvas.drawRoundRect(concreteRect, dpToPx(6), dpToPx(6), concreteBorderPaint);

        float centerY = (padTop + h - padBottom) / 2f;
        float barSpacing = barThick * 1.05f;
        float topBarY = centerY - (barSpacing / 2f);
        float btmBarY = centerY + (barSpacing / 2f);

        // Splice overlap zone (center 65% of width)
        float spliceLeft = padLeft + dpToPx(40);
        float spliceRight = w - padRight - dpToPx(40);

        // 1. Draw Top Rebar (Enters from left, hooks down on right)
        topBarPath.reset();
        topBarPath.moveTo(padLeft + dpToPx(10), topBarY);
        topBarPath.lineTo(spliceRight, topBarY);
        topBarPath.quadTo(spliceRight + dpToPx(12), topBarY, spliceRight + dpToPx(12), topBarY + dpToPx(16));
        canvas.drawPath(topBarPath, rebarTopPaint);

        // Ribs for top rebar
        float ribStep = dpToPx(14);
        for (float x = padLeft + dpToPx(18); x < spliceRight; x += ribStep) {
            canvas.drawLine(x - dpToPx(3), topBarY - barThick * 0.7f, x + dpToPx(3), topBarY + barThick * 0.7f, ribPaint);
        }

        // 2. Draw Bottom Rebar (Enters from right, hooks up on left)
        btmBarPath.reset();
        btmBarPath.moveTo(w - padRight - dpToPx(10), btmBarY);
        btmBarPath.lineTo(spliceLeft, btmBarY);
        btmBarPath.quadTo(spliceLeft - dpToPx(12), btmBarY, spliceLeft - dpToPx(12), btmBarY - dpToPx(16));
        canvas.drawPath(btmBarPath, rebarBottomPaint);

        // Ribs for bottom rebar
        for (float x = spliceLeft + dpToPx(8); x < w - padRight - dpToPx(18); x += ribStep) {
            canvas.drawLine(x + dpToPx(3), btmBarY - barThick * 0.7f, x - dpToPx(3), btmBarY + barThick * 0.7f, ribPaint);
        }

        // 3. Draw Tie Wire Wraps in splice zone
        float tie1 = spliceLeft + (spliceRight - spliceLeft) * 0.25f;
        float tie2 = spliceLeft + (spliceRight - spliceLeft) * 0.75f;
        canvas.drawLine(tie1 - dpToPx(3), topBarY - barThick, tie1 + dpToPx(3), btmBarY + barThick, tieWirePaint);
        canvas.drawLine(tie2 - dpToPx(3), topBarY - barThick, tie2 + dpToPx(3), btmBarY + barThick, tieWirePaint);

        // 4. Draw Splice Lap Dimension Line (Ls)
        float dimY = h - dpToPx(10);
        canvas.drawLine(spliceLeft, dimY, spliceRight, dimY, dimLinePaint);
        canvas.drawLine(spliceLeft, dimY - dpToPx(4), spliceLeft, dimY + dpToPx(4), dimLinePaint);
        canvas.drawLine(spliceRight, dimY - dpToPx(4), spliceRight, dimY + dpToPx(4), dimLinePaint);

        String dimLabel = String.format(Locale.US, "Lap Splice (Ls) = %.1f in (%s)", lapLengthInches, barName);
        canvas.drawText(dimLabel, (spliceLeft + spliceRight) / 2f, dimY - dpToPx(4), dimTextPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
