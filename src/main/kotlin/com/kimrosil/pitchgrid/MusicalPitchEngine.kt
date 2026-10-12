package com.kimrosil.pitchgrid

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class NotePitchInfo(
    val noteName: String,
    val octave: Int,
    val centsDeviation: Int,
    val standardFrequency: Double
)

object MusicalPitchEngine {

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * Converts MIDI note number (0..127) and cents offset (-50..+50) to exact frequency (Hz).
     * Reference A4 (MIDI 69) defaults to 440.0 Hz.
     */
    fun noteToFrequency(midiNote: Int, cents: Int = 0, refA4: Double = 440.0): Double {
        val semitoneDelta = (midiNote - 69).toDouble()
        val totalDelta = semitoneDelta + (cents.toDouble() / 100.0)
        return refA4 * 2.0.pow(totalDelta / 12.0)
    }

    /**
     * Analyzes any continuous frequency (Hz) and returns the nearest musical note,
     * octave, cents deviation (-50..+50), and the exact tempered pitch.
     */
    fun frequencyToNote(freq: Double, refA4: Double = 440.0): NotePitchInfo {
        if (freq <= 0.0) return NotePitchInfo("C", 0, 0, 16.35)

        val semitonesFromA4 = 12.0 * (ln(freq / refA4) / ln(2.0))
        val rawMidi = 69.0 + semitonesFromA4
        val nearestMidi = rawMidi.roundToInt()

        val cents = ((rawMidi - nearestMidi) * 100.0).roundToInt().coerceIn(-50, 50)
        val noteIndex = ((nearestMidi % 12) + 12) % 12
        val octave = (nearestMidi / 12) - 1

        val stdFreq = noteToFrequency(nearestMidi, 0, refA4)
        val name = NOTE_NAMES[noteIndex]

        return NotePitchInfo(
            noteName = name,
            octave = octave,
            centsDeviation = cents,
            standardFrequency = stdFreq
        )
    }

    /**
     * Maps a 0..1000 linear slider value logarithmically to 20.0 Hz .. 20,000.0 Hz.
     */
    fun progressToFrequency(progress: Int): Double {
        val ratio = progress.toDouble().coerceIn(0.0, 1000.0) / 1000.0
        // freq = 20 * (1000)^ratio (from 20 Hz to 20,000 Hz)
        return 20.0 * 1000.0.pow(ratio)
    }

    /**
     * Maps a continuous frequency (20..20,000 Hz) back to a 0..1000 slider progress.
     */
    fun frequencyToProgress(freq: Double): Int {
        val clamped = freq.coerceIn(20.0, 20000.0)
        val ratio = log10(clamped / 20.0) / log10(1000.0)
        return (ratio * 1000.0).roundToInt().coerceIn(0, 1000)
    }

    /**
     * Calculates logarithmic step interval frequency (e.g. Major 3rd, 4th, 5th, Octave).
     */
    fun transposeSemitones(baseFreq: Double, semitones: Int): Double {
        return (baseFreq * 2.0.pow(semitones.toDouble() / 12.0)).coerceIn(20.0, 20000.0)
    }
}
