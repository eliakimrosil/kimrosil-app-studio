package com.kimrosil.brixscale;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public class ExtractionChartView extends View {

    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIdealZonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIdealStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCrosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mBoundsRect = new RectF();
    private final RectF mIdealRect = new RectF();

    private double mEyPct = 20.1;
    private double mTdsPct = 1.38;
    private boolean mIsDark = true;

    public ExtractionChartView(Context context) {
        super(context);
        init();
    }

    public ExtractionChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExtractionChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(1.2f * density);

        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(0.8f * density);

        mIdealZonePaint.setStyle(Paint.Style.FILL);
        mIdealStrokePaint.setStyle(Paint.Style.STROKE);
        mIdealStrokePaint.setStrokeWidth(1.2f * density);

        mRayPaint.setStyle(Paint.Style.STROKE);
        mRayPaint.setStrokeWidth(1.4f * density);
        mRayPaint.setPathEffect(new DashPathEffect(new float[]{4f * density, 3f * density}, 0));

        mCrosshairPaint.setStyle(Paint.Style.STROKE);
        mCrosshairPaint.setStrokeWidth(1.0f * density);

        mDotPaint.setStyle(Paint.Style.FILL);
        mDotHaloPaint.setStyle(Paint.Style.FILL);

        mTextPaint.setTextSize(8.5f * density);
        mTextPaint.setFakeBoldText(true);

        updateTheme(true);
    }

    public void updateTheme(boolean isDark) {
        mIsDark = isDark;
        if (isDark) {
            mBgPaint.setColor(Color.parseColor("#140F0C"));
            mBorderPaint.setColor(Color.parseColor("#3E2E24"));
            mGridPaint.setColor(Color.parseColor("#2A1F18"));
            mIdealZonePaint.setColor(Color.parseColor("#282DD4BF"));
            mIdealStrokePaint.setColor(Color.parseColor("#2DD4BF"));
            mRayPaint.setColor(Color.parseColor("#88F59E0B"));
            mCrosshairPaint.setColor(Color.parseColor("#BBF7EFE6"));
            mTextPaint.setColor(Color.parseColor("#B59E8C"));
        } else {
            mBgPaint.setColor(Color.parseColor("#F7EFE6"));
            mBorderPaint.setColor(Color.parseColor("#C8B5A2"));
            mGridPaint.setColor(Color.parseColor("#E3D5C5"));
            mIdealZonePaint.setColor(Color.parseColor("#280D9488"));
            mIdealStrokePaint.setColor(Color.parseColor("#0D9488"));
            mRayPaint.setColor(Color.parseColor("#88B45309"));
            mCrosshairPaint.setColor(Color.parseColor("#991E140E"));
            mTextPaint.setColor(Color.parseColor("#695344"));
        }
        invalidate();
    }

    public void setExtractionData(double eyPct, double tdsPct) {
        mEyPct = eyPct;
        mTdsPct = tdsPct;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float d = getResources().getDisplayMetrics().density;
        float pad = 4f * d;
        mBoundsRect.set(pad, pad, w - pad, h - pad);

        // Background card
        canvas.drawRoundRect(mBoundsRect, 10f * d, 10f * d, mBgPaint);
        canvas.drawRoundRect(mBoundsRect, 10f * d, 10f * d, mBorderPaint);

        float plotLeft = pad + 8f * d;
        float plotRight = w - pad - 8f * d;
        float plotTop = pad + 14f * d;
        float plotBottom = h - pad - 12f * d;
        float plotW = Math.max(10f, plotRight - plotLeft);
        float plotH = Math.max(10f, plotBottom - plotTop);

        // Grid lines
        for (int i = 1; i <= 3; i++) {
            float gx = plotLeft + (plotW * i / 4f);
            float gy = plotTop + (plotH * i / 4f);
            canvas.drawLine(gx, plotTop, gx, plotBottom, mGridPaint);
            canvas.drawLine(plotLeft, gy, plotRight, gy, mGridPaint);
        }

        // X-axis maps EY% from 14.0% to 26.0%
        float minEy = 14.0f;
        float maxEy = 26.0f;

        // Y-axis adapts to Filter (0.8% - 1.8% TDS) vs Espresso (4.0% - 14.0% TDS)
        boolean isEspressoScale = mTdsPct > 2.5;
        float minTds = isEspressoScale ? 4.0f : 0.85f;
        float maxTds = isEspressoScale ? 14.0f : 1.75f;
        float idealLowTds = isEspressoScale ? 7.5f : 1.15f;
        float idealHighTds = isEspressoScale ? 11.5f : 1.45f;

        float idealLeft = plotLeft + ((18.0f - minEy) / (maxEy - minEy)) * plotW;
        float idealRight = plotLeft + ((22.0f - minEy) / (maxEy - minEy)) * plotW;
        float idealBottom = plotBottom - ((idealLowTds - minTds) / (maxTds - minTds)) * plotH;
        float idealTop = plotBottom - ((idealHighTds - minTds) / (maxTds - minTds)) * plotH;

        mIdealRect.set(idealLeft, idealTop, idealRight, idealBottom);
        canvas.drawRoundRect(mIdealRect, 4f * d, 4f * d, mIdealZonePaint);
        canvas.drawRoundRect(mIdealRect, 4f * d, 4f * d, mIdealStrokePaint);

        // Compute active point
        float normX = (float) ((mEyPct - minEy) / (maxEy - minEy));
        float normY = (float) ((mTdsPct - minTds) / (maxTds - minTds));
        normX = Math.max(0.04f, Math.min(0.96f, normX));
        normY = Math.max(0.06f, Math.min(0.94f, normY));

        float px = plotLeft + normX * plotW;
        float py = plotBottom - normY * plotH;

        // Brew ratio trajectory line from bottom-left origin through point
        canvas.drawLine(plotLeft, plotBottom, px, py, mRayPaint);

        // Crosshairs
        canvas.drawLine(px, plotTop, px, plotBottom, mCrosshairPaint);
        canvas.drawLine(plotLeft, py, plotRight, py, mCrosshairPaint);

        // Active Dot Color
        if (mEyPct >= 18.0 && mEyPct <= 22.0) {
            mDotPaint.setColor(Color.parseColor("#2DD4BF"));
            mDotHaloPaint.setColor(Color.parseColor("#442DD4BF"));
        } else if (mEyPct < 18.0) {
            mDotPaint.setColor(Color.parseColor("#FBBF24"));
            mDotHaloPaint.setColor(Color.parseColor("#44FBBF24"));
        } else {
            mDotPaint.setColor(Color.parseColor("#F87171"));
            mDotHaloPaint.setColor(Color.parseColor("#44F87171"));
        }

        canvas.drawCircle(px, py, 7.5f * d, mDotHaloPaint);
        canvas.drawCircle(px, py, 4.0f * d, mDotPaint);

        // Labels
        canvas.drawText("SCA CHART", plotLeft, pad + 11f * d, mTextPaint);
        String coordText = String.format(Locale.US, "18-22%% EY");
        canvas.drawText(coordText, plotLeft, h - pad - 3f * d, mTextPaint);
    }
}
