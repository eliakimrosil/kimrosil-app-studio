package com.kimrosil.pitchgrid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class WaveformVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A222D")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val centerGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263547")
        strokeWidth = 2.0f
        style = Paint.Style.STROKE
    }

    private val waveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5500E5FF")
        strokeWidth = 6.0f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val waveTracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 2.8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val wavePath = Path()
    private var waveformBuffer: FloatArray? = null
    private var isMuted = true

    fun setWaveformData(buffer: FloatArray, active: Boolean) {
        waveformBuffer = buffer
        isMuted = !active
        postInvalidate()
    }

    fun setMutedState(muted: Boolean) {
        isMuted = muted
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2.0f

        // Draw oscilloscope background grid divisions (8x6 divisions)
        val divX = w / 8.0f
        val divY = h / 6.0f

        for (i in 1 until 8) {
            val x = i * divX
            canvas.drawLine(x, 0f, x, h, if (i == 4) centerGridPaint else gridPaint)
        }

        for (j in 1 until 6) {
            val y = j * divY
            canvas.drawLine(0f, y, w, y, if (j == 3) centerGridPaint else gridPaint)
        }

        wavePath.reset()

        val buffer = waveformBuffer
        if (isMuted || buffer == null || buffer.isEmpty()) {
            // Draw resting baseline
            wavePath.moveTo(0f, centerY)
            wavePath.lineTo(w, centerY)
            canvas.drawPath(wavePath, waveTracePaint)
            return
        }

        // Draw active waveform
        val numSamples = buffer.size
        val stepX = w / (numSamples - 1).toFloat()
        val ampScale = (h * 0.42f)

        for (i in 0 until numSamples) {
            val x = i * stepX
            val sample = buffer[i].coerceIn(-1.0f, 1.0f)
            val y = centerY - (sample * ampScale)

            if (i == 0) {
                wavePath.moveTo(x, y)
            } else {
                wavePath.lineTo(x, y)
            }
        }

        // Draw phosphor glow and primary sharp trace
        canvas.drawPath(wavePath, waveGlowPaint)
        canvas.drawPath(wavePath, waveTracePaint)
    }
}
