package com.kimrosil.gaussmeter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom Native Kotlin 180-Degree Magnetic Flux Gauge & Ferrous Proximity Bar View.
 * Pre-allocates all Paint and RectF objects to guarantee zero heap allocations inside onDraw().
 */
class GaussScopeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emeraldZonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val amberZonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crimsonZonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val alarmTickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barWellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val summaryPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val arcBounds = RectF()
    private val barWellBounds = RectF()
    private val barFillBounds = RectF()

    private var currentFluxUt: Double = 115.0
    private var currentDeltaUt: Double = 67.0
    private var alarmDeltaUt: Double = 85.0

    init {
        val strokePx = dpToPx(8.5f)

        trackPaint.style = Paint.Style.STROKE
        trackPaint.strokeWidth = strokePx
        trackPaint.strokeCap = Paint.Cap.ROUND
        trackPaint.color = context.getColor(R.color.scope_track)

        emeraldZonePaint.style = Paint.Style.STROKE
        emeraldZonePaint.strokeWidth = strokePx
        emeraldZonePaint.strokeCap = Paint.Cap.BUTT
        emeraldZonePaint.color = context.getColor(R.color.scope_emerald)

        amberZonePaint.style = Paint.Style.STROKE
        amberZonePaint.strokeWidth = strokePx
        amberZonePaint.strokeCap = Paint.Cap.BUTT
        amberZonePaint.color = context.getColor(R.color.scope_amber)

        crimsonZonePaint.style = Paint.Style.STROKE
        crimsonZonePaint.strokeWidth = strokePx
        crimsonZonePaint.strokeCap = Paint.Cap.BUTT
        crimsonZonePaint.color = context.getColor(R.color.scope_crimson)

        needlePaint.style = Paint.Style.STROKE
        needlePaint.strokeWidth = dpToPx(2.6f)
        needlePaint.strokeCap = Paint.Cap.ROUND
        needlePaint.color = context.getColor(R.color.scope_needle)

        alarmTickPaint.style = Paint.Style.STROKE
        alarmTickPaint.strokeWidth = dpToPx(2.2f)
        alarmTickPaint.strokeCap = Paint.Cap.ROUND
        alarmTickPaint.color = context.getColor(R.color.scope_amber)

        hubPaint.style = Paint.Style.FILL
        hubPaint.color = context.getColor(R.color.accent_primary)

        barWellPaint.style = Paint.Style.FILL
        barWellPaint.color = context.getColor(R.color.scope_well)

        barFillPaint.style = Paint.Style.FILL
        barFillPaint.color = context.getColor(R.color.accent_primary)

        barBorderPaint.style = Paint.Style.STROKE
        barBorderPaint.strokeWidth = dpToPx(1.2f)
        barBorderPaint.color = context.getColor(R.color.accent_primary)

        labelPaint.textSize = spToPx(9.5f)
        labelPaint.color = context.getColor(R.color.text_muted)
        labelPaint.textAlign = Paint.Align.CENTER

        summaryPaint.textSize = spToPx(11f)
        summaryPaint.isFakeBoldText = true
        summaryPaint.color = context.getColor(R.color.text_primary)
        summaryPaint.textAlign = Paint.Align.CENTER
    }

    fun updateReadings(totalFluxUt: Double, netDeltaUt: Double, alarmThresholdUt: Double) {
        this.currentFluxUt = GaussEngine.clamp(totalFluxUt, 0.0, 2000.0)
        this.currentDeltaUt = GaussEngine.clamp(netDeltaUt, 0.0, 2000.0)
        this.alarmDeltaUt = GaussEngine.clamp(alarmThresholdUt, 5.0, 2000.0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val topReserved = dpToPx(20f)
        val bottomReserved = dpToPx(28f)
        val radius = min(w * 0.30f, h - topReserved - bottomReserved).coerceAtLeast(18f)
        val cx = w * 0.5f
        val cy = h - bottomReserved

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 180-Degree Flux Scale (0 .. 400 µT)
        canvas.drawArc(arcBounds, 180f, 180f, false, trackPaint)
        // Earth Baseline Zone (0..65 µT -> ~45 deg)
        canvas.drawArc(arcBounds, 180f, 48f, false, emeraldZonePaint)
        // Ferrous Stud / Conduit Zone (65..220 µT -> 80 deg)
        canvas.drawArc(arcBounds, 230f, 78f, false, amberZonePaint)
        // Strong Magnet Zone (220..400+ µT -> 50 deg)
        canvas.drawArc(arcBounds, 310f, 50f, false, crimsonZonePaint)

        // Scale Endpoint Labels
        canvas.drawText("0 µT", cx - radius - dpToPx(18f), cy - dpToPx(2f), labelPaint)
        canvas.drawText("150 µT", cx, cy - radius - dpToPx(7f), labelPaint)
        canvas.drawText("400 µT", cx + radius + dpToPx(20f), cy - dpToPx(2f), labelPaint)

        // Live Flux Needle (0..400 µT mapped to 180..360 deg)
        val fluxNorm = (currentFluxUt / 400.0).coerceIn(0.0, 1.0).toFloat()
        val angleRad = Math.toRadians(180.0 + fluxNorm * 180.0)
        val nx = cx + cos(angleRad).toFloat() * (radius - dpToPx(5f))
        val ny = cy + sin(angleRad).toFloat() * (radius - dpToPx(5f))
        canvas.drawLine(cx, cy, nx, ny, needlePaint)
        canvas.drawCircle(cx, cy, dpToPx(4f), hubPaint)

        // Lower Ferrous Proximity Progress Bar
        val barW = min(w * 0.54f, dpToPx(186f))
        val barH = dpToPx(14f)
        val barTop = cy + dpToPx(8f)
        val barLeft = cx - barW * 0.5f
        barWellBounds.set(barLeft, barTop, barLeft + barW, barTop + barH)
        val cornerR = barH * 0.5f
        canvas.drawRoundRect(barWellBounds, cornerR, cornerR, barWellPaint)

        val proxRatio = (currentDeltaUt / (alarmDeltaUt * 1.25)).coerceIn(0.04, 1.0).toFloat()
        barFillBounds.set(barLeft, barTop, barLeft + barW * proxRatio, barTop + barH)
        barFillPaint.color = when {
            currentDeltaUt >= alarmDeltaUt -> context.getColor(R.color.scope_crimson)
            currentDeltaUt >= alarmDeltaUt * 0.5 -> context.getColor(R.color.scope_amber)
            else -> context.getColor(R.color.accent_primary)
        }
        canvas.drawRoundRect(barFillBounds, cornerR, cornerR, barFillPaint)
        canvas.drawRoundRect(barWellBounds, cornerR, cornerR, barBorderPaint)

        // Side Readout Captions beside Ferrous Proximity Bar
        val by = barTop + barH * 0.5f + dpToPx(4f)
        val leftTxt = String.format(Locale.US, "%.0f µT", currentFluxUt)
        val rightTxt = String.format(Locale.US, "+%.0f ∆", currentDeltaUt)
        canvas.drawText(leftTxt, barWellBounds.left - dpToPx(26f), by, summaryPaint)
        canvas.drawText(rightTxt, barWellBounds.right + dpToPx(26f), by, summaryPaint)
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    private fun spToPx(sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
}
