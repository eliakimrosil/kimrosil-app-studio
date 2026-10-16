package com.kimrosil.kerfnotch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DadoJointPreviewView extends View {

    public static final int MODE_DADO_STACK = 0;
    public static final int MODE_BOX_JOINT = 1;

    public interface OnSliceSelectedListener {
        void onSliceSelected(String componentName, double thicknessInch);
    }

    private static class SliceBounds {
        RectF rect;
        String name;
        double thickness;

        SliceBounds(RectF rect, String name, double thickness) {
            this.rect = rect;
            this.name = name;
            this.thickness = thickness;
        }
    }

    private int mMode = MODE_DADO_STACK;
    private DadoStackSolver.DadoRecipe mDadoRecipe;
    private BoxJointCalculator.BoxJointResult mBoxJointResult;
    private OnSliceSelectedListener mSliceListener;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<SliceBounds> mSlices = new ArrayList<>();
    private float mScaleFactor = 1.0f;
    private ScaleGestureDetector mScaleDetector;

    public DadoJointPreviewView(Context context) {
        super(context);
        init(context);
    }

    public DadoJointPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DadoJointPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mDashedPaint.setStyle(Paint.Style.STROKE);
        mDashedPaint.setStrokeWidth(2f);
        mDashedPaint.setPathEffect(new DashPathEffect(new float[]{8f, 6f}, 0));
        mDashedPaint.setColor(Color.parseColor("#8D7B68"));

        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setColor(Color.parseColor("#D97706"));
        mTextPaint.setTextSize(26f);

        mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                mScaleFactor *= detector.getScaleFactor();
                mScaleFactor = Math.max(0.8f, Math.min(mScaleFactor, 2.5f));
                invalidate();
                return true;
            }
        });
    }

    public void setMode(int mode) {
        mMode = mode;
        invalidate();
    }

    public void setDadoRecipe(DadoStackSolver.DadoRecipe recipe) {
        mDadoRecipe = recipe;
        invalidate();
    }

    public void setBoxJointResult(BoxJointCalculator.BoxJointResult result) {
        mBoxJointResult = result;
        invalidate();
    }

    public void setOnSliceSelectedListener(OnSliceSelectedListener listener) {
        mSliceListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_UP && !mScaleDetector.isInProgress()) {
            float x = event.getX();
            float y = event.getY();
            for (SliceBounds sb : mSlices) {
                if (sb.rect.contains(x, y)) {
                    if (mSliceListener != null) {
                        mSliceListener.onSliceSelected(sb.name, sb.thickness);
                    }
                    performClick();
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        mSlices.clear();

        if (mMode == MODE_DADO_STACK) {
            drawDadoStack(canvas, width, height);
        } else {
            drawBoxJoint(canvas, width, height);
        }
    }

    private void drawDadoStack(Canvas canvas, int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f + 10f;

        // Draw Arbor Spindle Shaft (Chamfer Bar)
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.parseColor("#334155"));
        RectF arborRect = new RectF(cx - (w * 0.42f), cy - 14f, cx + (w * 0.42f), cy + 14f);
        canvas.drawRoundRect(arborRect, 8f, 8f, mPaint);

        // Arbor threads detail
        mPaint.setColor(Color.parseColor("#475569"));
        mPaint.setStrokeWidth(2f);
        for (float tx = arborRect.left + 15; tx < arborRect.right - 15; tx += 12) {
            canvas.drawLine(tx, cy - 12f, tx + 6, cy + 12f, mPaint);
        }

        if (mDadoRecipe == null) return;

        // Scaling factor for display: map total target kerf to ~55% of width
        double totalKerf = mDadoRecipe.totalKerfInch;
        if (totalKerf <= 0) totalKerf = 0.750;
        float basePixelWidth = (w * 0.55f) * mScaleFactor;
        float pxPerInch = (float) (basePixelWidth / totalKerf);

        float bladeHeight = h * 0.70f;
        float halfH = bladeHeight / 2f;

        // Left outside blade: 1/8"
        float currentX = cx - (float) (totalKerf * pxPerInch) / 2f;
        float leftBladeWidth = (float) (0.125 * pxPerInch);

        RectF leftBladeRect = new RectF(currentX, cy - halfH, currentX + leftBladeWidth, cy + halfH);
        drawBlade(canvas, leftBladeRect, "Left Blade 1/8\"", 0.125, true);
        currentX += leftBladeWidth;

        // Chippers
        for (int i = 0; i < mDadoRecipe.chippers18; i++) {
            float sliceW = (float) (0.125 * pxPerInch);
            RectF sliceRect = new RectF(currentX, cy - (halfH * 0.88f), currentX + sliceW, cy + (halfH * 0.88f));
            drawChipper(canvas, sliceRect, "Chipper 1/8\"", 0.125);
            currentX += sliceW;
        }

        for (int i = 0; i < mDadoRecipe.chippers332; i++) {
            float sliceW = (float) (0.09375 * pxPerInch);
            RectF sliceRect = new RectF(currentX, cy - (halfH * 0.88f), currentX + sliceW, cy + (halfH * 0.88f));
            drawChipper(canvas, sliceRect, "Chipper 3/32\"", 0.09375);
            currentX += sliceW;
        }

        for (int i = 0; i < mDadoRecipe.chippers116; i++) {
            float sliceW = (float) (0.0625 * pxPerInch);
            RectF sliceRect = new RectF(currentX, cy - (halfH * 0.88f), currentX + sliceW, cy + (halfH * 0.88f));
            drawChipper(canvas, sliceRect, "Chipper 1/16\"", 0.0625);
            currentX += sliceW;
        }

        // Shims
        for (DadoStackSolver.ShimEntry shim : mDadoRecipe.shims) {
            for (int s = 0; s < shim.count; s++) {
                float shimW = Math.max(3f, (float) (shim.thicknessInch * pxPerInch));
                RectF shimRect = new RectF(currentX, cy - (halfH * 0.50f), currentX + shimW, cy + (halfH * 0.50f));
                drawShim(canvas, shimRect, shim);
                currentX += shimW;
            }
        }

        // Right outside blade: 1/8"
        float rightBladeWidth = (float) (0.125 * pxPerInch);
        RectF rightBladeRect = new RectF(currentX, cy - halfH, currentX + rightBladeWidth, cy + halfH);
        drawBlade(canvas, rightBladeRect, "Right Blade 1/8\"", 0.125, false);
        currentX += rightBladeWidth;

        // Dimension lines and markers
        float startX = cx - (float) (totalKerf * pxPerInch) / 2f;
        float endX = currentX;
        float dimY = cy - halfH - 18f;

        canvas.drawLine(startX, dimY, endX, dimY, mDashedPaint);
        canvas.drawLine(startX, dimY - 8f, startX, dimY + 8f, mDashedPaint);
        canvas.drawLine(endX, dimY - 8f, endX, dimY + 8f, mDashedPaint);

        String dimText = String.format(Locale.US, "Stack Kerf: %.4f\" (%.2f mm)", totalKerf, totalKerf * 25.4);
        mTextPaint.setTextSize(22f);
        canvas.drawText(dimText, cx, dimY - 10f, mTextPaint);
    }

    private void drawBlade(Canvas canvas, RectF r, String name, double thickness, boolean isLeft) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.parseColor("#475569")); // Weathered carbide steel
        canvas.drawRoundRect(r, 4f, 4f, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1.5f);
        mPaint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawRoundRect(r, 4f, 4f, mPaint);

        // Carbide Tooth detail
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.parseColor("#D97706")); // Carbide tip amber
        float toothW = r.width() * 1.3f;
        float toothH = 10f;
        if (isLeft) {
            canvas.drawRect(r.left - (toothW - r.width()), r.top, r.right, r.top + toothH, mPaint);
            canvas.drawRect(r.left - (toothW - r.width()), r.bottom - toothH, r.right, r.bottom, mPaint);
        } else {
            canvas.drawRect(r.left, r.top, r.right + (toothW - r.width()), r.top + toothH, mPaint);
            canvas.drawRect(r.left, r.bottom - toothH, r.right + (toothW - r.width()), r.bottom, mPaint);
        }

        mSlices.add(new SliceBounds(new RectF(r), name, thickness));
    }

    private void drawChipper(Canvas canvas, RectF r, String name, double thickness) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.parseColor("#64748B"));
        canvas.drawRoundRect(r, 3f, 3f, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1.5f);
        mPaint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawRoundRect(r, 3f, 3f, mPaint);

        mSlices.add(new SliceBounds(new RectF(r), name, thickness));
    }

    private void drawShim(Canvas canvas, RectF r, DadoStackSolver.ShimEntry shim) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(shim.colorHex);
        canvas.drawRect(r, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1f);
        mPaint.setColor(Color.parseColor("#1E293B"));
        canvas.drawRect(r, mPaint);

        mSlices.add(new SliceBounds(new RectF(r), shim.name + " (" + shim.colorName + ")", shim.thicknessInch));
    }

    private void drawBoxJoint(Canvas canvas, int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f;

        if (mBoxJointResult == null) return;

        int totalFingers = mBoxJointResult.pinCount + mBoxJointResult.socketCount;
        if (totalFingers <= 0) return;

        float boardDrawWidth = w * 0.85f * mScaleFactor;
        float fingerDrawWidth = boardDrawWidth / totalFingers;
        float startX = cx - (boardDrawWidth / 2f);
        float jointHeight = h * 0.50f;
        float halfJointH = jointHeight / 2f;

        // Timber Colors
        int maleWoodColor = Color.parseColor("#D97706"); // Warm oak male tenon
        int femaleWoodColor = Color.parseColor("#92400E"); // Dark walnut female tenon

        for (int i = 0; i < totalFingers; i++) {
            float fx = startX + (i * fingerDrawWidth);
            boolean isMale = (i % 2 == 0);

            RectF fingerRect;
            if (isMale) {
                fingerRect = new RectF(fx, cy - halfJointH, fx + fingerDrawWidth, cy + 8f);
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(maleWoodColor);
                canvas.drawRect(fingerRect, mPaint);

                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(2f);
                mPaint.setColor(Color.parseColor("#451A03"));
                canvas.drawRect(fingerRect, mPaint);

                mSlices.add(new SliceBounds(fingerRect, "Male Pin #" + (i / 2 + 1), mBoxJointResult.fingerWidthInch));
            } else {
                fingerRect = new RectF(fx, cy - 8f, fx + fingerDrawWidth, cy + halfJointH);
                mPaint.setStyle(Paint.Style.FILL);
                mPaint.setColor(femaleWoodColor);
                canvas.drawRect(fingerRect, mPaint);

                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(2f);
                mPaint.setColor(Color.parseColor("#271B17"));
                canvas.drawRect(fingerRect, mPaint);

                mSlices.add(new SliceBounds(fingerRect, "Female Socket #" + ((i - 1) / 2 + 1), mBoxJointResult.fingerWidthInch));
            }
        }

        // Interlocking Dimension marker
        float dimY = cy - halfJointH - 18f;
        canvas.drawLine(startX, dimY, startX + boardDrawWidth, dimY, mDashedPaint);
        canvas.drawLine(startX, dimY - 8f, startX, dimY + 8f, mDashedPaint);
        canvas.drawLine(startX + boardDrawWidth, dimY - 8f, startX + boardDrawWidth, dimY + 8f, mDashedPaint);

        String dimText = String.format(Locale.US, "Board: %.3f\" | %d Pins / %d Sockets",
                mBoxJointResult.boardWidthInch, mBoxJointResult.pinCount, mBoxJointResult.socketCount);
        mTextPaint.setTextSize(22f);
        canvas.drawText(dimText, cx, dimY - 10f, mTextPaint);
    }
}