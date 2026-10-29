package com.kimrosil.gluewedge

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.tan

class PressureConeVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var clampCount: Int = 4
    private var panelLength: Double = 36.0
    private var panelThickness: Double = 0.75

    private val timberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF332014.toInt()
    }

    private val timberStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF5C3E26.toInt()
    }

    private val conePaintTop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x4D0EA5E9.toInt() // 30% alpha sky cyan
    }

    private val conePaintBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x4DF59E0B.toInt() // 30% alpha amber
    }

    private val coneLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF38BDF8.toInt()
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val clampBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f
        color = 0xFFE2E8F0.toInt()
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    fun updateMetrics(count: Int, length: Double, thickness: Double) {
        clampCount = count.coerceAtLeast(1)
        panelLength = length.coerceAtLeast(1.0)
        panelThickness = thickness.coerceAtLeast(0.25)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val paddingX = 40f
        val paddingY = 60f
        val drawW = w - (paddingX * 2f)
        val drawH = h - (paddingY * 2f)

        // Draw Panel Edge representation in the vertical center
        val panelH = (drawH * 0.35f).coerceIn(36f, 90f)
        val panelTop = (h / 2f) - (panelH / 2f)
        val panelBottom = panelTop + panelH

        // Draw Wood Panel
        canvas.drawRoundRect(paddingX, panelTop, paddingX + drawW, panelBottom, 8f, 8f, timberPaint)
        canvas.drawRoundRect(paddingX, panelTop, paddingX + drawW, panelBottom, 8f, 8f, timberStrokePaint)

        // Calculate Clamp positions along length
        // Clamps are evenly distributed with inset overhang
        val stepX = if (clampCount > 1) drawW / (clampCount - 1) else drawW / 2f
        val coneAngleDeg = 45.0
        val tanCone = tan(Math.toRadians(coneAngleDeg)).toFloat()
        val coneSpread = (panelH * tanCone).coerceAtLeast(24f)

        for (i in 0 until clampCount) {
            val cx = if (clampCount == 1) paddingX + (drawW / 2f) else paddingX + (i * stepX)
            val isTopClamp = (i % 2 == 0)

            val conePath = Path()
            if (isTopClamp) {
                // Cone originates from top edge spreading downwards
                conePath.moveTo(cx, panelTop)
                conePath.lineTo((cx - coneSpread).coerceAtLeast(paddingX), panelBottom)
                conePath.lineTo((cx + coneSpread).coerceAtMost(paddingX + drawW), panelBottom)
                conePath.close()
                canvas.drawPath(conePath, conePaintTop)
                canvas.drawPath(conePath, coneLinePaint)

                // Clamp Head/Pad above
                canvas.drawRect(cx - 10f, panelTop - 22f, cx + 10f, panelTop, clampBarPaint)
                canvas.drawText("TOP #${i + 1}", cx, panelTop - 28f, textPaint)
            } else {
                // Alternating Underneath clamp spreading upwards
                conePath.moveTo(cx, panelBottom)
                conePath.lineTo((cx - coneSpread).coerceAtLeast(paddingX), panelTop)
                conePath.lineTo((cx + coneSpread).coerceAtMost(paddingX + drawW), panelTop)
                conePath.close()
                canvas.drawPath(conePath, conePaintBottom)
                canvas.drawPath(conePath, coneLinePaint)

                // Clamp Head/Pad below
                canvas.drawRect(cx - 10f, panelBottom, cx + 10f, panelBottom + 22f, clampBarPaint)
                canvas.drawText("BOT #${i + 1}", cx, panelBottom + 44f, textPaint)
            }
        }
    }
}