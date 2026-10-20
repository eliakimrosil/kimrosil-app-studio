package com.kimrosil.krhabit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * HeatmapGridView renders a multi-week consistency matrix.
 */
public class HeatmapGridView extends View {

    private Paint paintCell;
    private Paint paintText;
    private RectF rectCell;

    private List<Boolean> historyData = new ArrayList<>();
    private int rows = 7; // 7 days of the week
    private int cols = 9; // ~9 weeks (63 days)

    private int colorLevel0 = 0xFF18241D;
    private int colorLevel1 = 0xFF114528;
    private int colorLevel2 = 0xFF059669;
    private int colorLevel3 = 0xFF00E676;

    public HeatmapGridView(Context context) {
        super(context);
        init();
    }

    public HeatmapGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HeatmapGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintCell = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText.setTextSize(26f);
        paintText.setColor(0xFF94A3B8);

        rectCell = new RectF();

        // Resolve colors from resources if available
        try {
            colorLevel0 = getContext().getColor(R.color.heatmap_0);
            colorLevel1 = getContext().getColor(R.color.heatmap_1);
            colorLevel2 = getContext().getColor(R.color.heatmap_2);
            colorLevel3 = getContext().getColor(R.color.heatmap_3);
        } catch (Throwable ignored) {
        }
    }

    public void setHistory(List<Boolean> history) {
        if (history != null) {
            this.historyData = new ArrayList<>(history);
        } else {
            this.historyData.clear();
        }
        invalidate();
    }

    public void setAccentColor(int color) {
        this.colorLevel3 = color;
        this.colorLevel2 = blendColors(color, colorLevel0, 0.65f);
        this.colorLevel1 = blendColors(color, colorLevel0, 0.35f);
        invalidate();
    }

    private int blendColors(int fg, int bg, float ratio) {
        float inv = 1f - ratio;
        int r = (int) (android.graphics.Color.red(fg) * ratio + android.graphics.Color.red(bg) * inv);
        int g = (int) (android.graphics.Color.green(fg) * ratio + android.graphics.Color.green(bg) * inv);
        int b = (int) (android.graphics.Color.blue(fg) * ratio + android.graphics.Color.blue(bg) * inv);
        return android.graphics.Color.rgb(r, g, b);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int totalDays = rows * cols; // 63 days
        float width = getWidth();
        float height = getHeight();

        if (width <= 0 || height <= 0) return;

        float paddingLeft = 16f;
        float paddingTop = 12f;
        float paddingRight = 16f;
        float paddingBottom = 12f;

        float availableWidth = width - paddingLeft - paddingRight;
        float availableHeight = height - paddingTop - paddingBottom;

        float spacing = 8f;
        float cellWidth = (availableWidth - (cols - 1) * spacing) / cols;
        float cellHeight = (availableHeight - (rows - 1) * spacing) / rows;
        float cellSize = Math.min(cellWidth, cellHeight);

        // Center matrix
        float startX = paddingLeft + (availableWidth - (cols * cellSize + (cols - 1) * spacing)) / 2f;
        float startY = paddingTop + (availableHeight - (rows * cellSize + (rows - 1) * spacing)) / 2f;

        int dataSize = historyData.size();

        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int dayIndex = c * rows + r;
                int dataIndex = dataSize - totalDays + dayIndex;

                boolean completed = false;
                if (dataIndex >= 0 && dataIndex < dataSize) {
                    Boolean val = historyData.get(dataIndex);
                    completed = val != null && val;
                }

                float left = startX + c * (cellSize + spacing);
                float top = startY + r * (cellSize + spacing);
                float right = left + cellSize;
                float bottom = top + cellSize;

                rectCell.set(left, top, right, bottom);

                if (completed) {
                    paintCell.setColor(colorLevel3);
                } else if (dataIndex >= 0 && dataIndex < dataSize) {
                    paintCell.setColor(colorLevel0);
                } else {
                    // Pre-creation historical dates
                    paintCell.setColor(colorLevel0);
                    paintCell.setAlpha(80);
                }

                canvas.drawRoundRect(rectCell, 8f, 8f, paintCell);
                paintCell.setAlpha(255);
            }
        }
    }
}
