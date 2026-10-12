package com.kimrosil.pitchgrid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.floor
import kotlin.math.sin

enum class WaveformType {
    SINE,
    TRIANGLE,
    SAWTOOTH,
    SQUARE,
    NOISE
}

class AudioSynthesisEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE_FRAMES = 1024
        private const val RAMP_FACTOR = 0.05f
    }

    @Volatile
    private var isRunning = false

    @Volatile
    private var targetFrequency = 440.0

    @Volatile
    private var currentFrequency = 440.0

    @Volatile
    private var activeWaveform = WaveformType.SINE

    @Volatile
    private var targetVolume = 0.5f

    @Volatile
    private var currentVolume = 0.0f

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    // Real-time oscilloscope tap buffer
    private val scopeVisualizerBuffer = FloatArray(256)
    var onScopeBufferUpdate: ((FloatArray) -> Unit)? = null

    // Pink noise state variables (Kellet filter approximation)
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0
    private val random = Random()

    fun isPlaying(): Boolean = isRunning

    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, BUFFER_SIZE_FRAMES * 2)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        audioThread = Thread({ audioSynthesisLoop() }, "PitchGridAudioEngine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            audioThread?.join(500)
        } catch (ignored: Exception) {
        }
        audioThread = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (ignored: Exception) {
        }
        audioTrack = null
        currentVolume = 0.0f
    }

    fun setFrequency(freq: Double) {
        targetFrequency = freq.coerceIn(20.0, 20000.0)
    }

    fun setWaveform(waveform: WaveformType) {
        activeWaveform = waveform
    }

    fun setVolume(volFraction: Float) {
        targetVolume = volFraction.coerceIn(0.0f, 1.0f)
    }

    private fun audioSynthesisLoop() {
        val audioBuffer = ShortArray(BUFFER_SIZE_FRAMES)
        var phase = 0.0
        val twoPi = 2.0 * PI
        var scopeSampleCounter = 0

        while (isRunning) {
            val track = audioTrack ?: break

            // Frequency smoothing to prevent audible clicks on discrete jumps
            currentFrequency += (targetFrequency - currentFrequency) * 0.1
            val phaseIncrement = twoPi * currentFrequency / SAMPLE_RATE

            for (i in 0 until BUFFER_SIZE_FRAMES) {
                // Smooth amplitude envelope
                currentVolume += (targetVolume - currentVolume) * RAMP_FACTOR

                val sampleFloat: Double = when (activeWaveform) {
                    WaveformType.SINE -> sin(phase)

                    WaveformType.TRIANGLE -> {
                        val norm = (phase / twoPi) - floor(phase / twoPi)
                        if (norm < 0.5) {
                            -1.0 + 4.0 * norm
                        } else {
                            3.0 - 4.0 * norm
                        }
                    }

                    WaveformType.SAWTOOTH -> {
                        val norm = (phase / twoPi) - floor(phase / twoPi)
                        2.0 * norm - 1.0
                    }

                    WaveformType.SQUARE -> {
                        if (sin(phase) >= 0.0) 0.95 else -0.95
                    }

                    WaveformType.NOISE -> {
                        // Pink noise generation (-3dB/octave filtered white noise)
                        val white = random.nextDouble() * 2.0 - 1.0
                        b0 = 0.99886 * b0 + white * 0.0555179
                        b1 = 0.99332 * b1 + white * 0.0750759
                        b2 = 0.96900 * b2 + white * 0.1538520
                        b3 = 0.86650 * b3 + white * 0.3104856
                        b4 = 0.55000 * b4 + white * 0.5329522
                        b5 = -0.7616 * b5 - white * 0.0168980
                        val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
                        b6 = white * 0.115926
                        pink.coerceIn(-1.0, 1.0)
                    }
                }

                phase += phaseIncrement
                if (phase >= twoPi) {
                    phase -= twoPi
                }

                // Apply amplitude and write to PCM 16-bit
                val finalSample = (sampleFloat * currentVolume * 32767.0).coerceIn(-32767.0, 32767.0)
                audioBuffer[i] = finalSample.toInt().toShort()

                // Decimate for oscilloscope view
                if (i % 4 == 0 && scopeSampleCounter < scopeVisualizerBuffer.size) {
                    scopeVisualizerBuffer[scopeSampleCounter] = (sampleFloat * currentVolume).toFloat()
                    scopeSampleCounter++
                }
            }

            track.write(audioBuffer, 0, BUFFER_SIZE_FRAMES)

            // Dispatch visualizer buffer
            if (scopeSampleCounter >= scopeVisualizerBuffer.size) {
                val copy = scopeVisualizerBuffer.copyOf()
                onScopeBufferUpdate?.invoke(copy)
                scopeSampleCounter = 0
            }
        }
    }
}
