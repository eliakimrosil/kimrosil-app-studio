package com.kimrosil.gaussmeter

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Pure Native Kotlin 3-Axis Magnetometer (µT / mG), Baseline Tare, and Ferrous Stud Proximity Engine.
 * Hardened against NaN, Infinity, malformed decimals, negatives, and extreme overflow inputs.
 */
object GaussEngine {

    const val MODE_3D_VECTOR = 0
    const val MODE_Z_DEPTH = 1
    const val MODE_XY_PLANE = 2

    data class GaussTelemetry(
        val bxUt: Double,
        val byUt: Double,
        val bzUt: Double,
        val totalFluxUt: Double,
        val totalFluxMg: Double,
        val baselineTareUt: Double,
        val netDeltaUt: Double,
        val netDeltaMg: Double,
        val alarmThresholdUt: Double,
        val alarmPercent: Double,
        val estDepthCm: Double,
        val modeLabel: String,
        val zoneBadge: String,
        val diagnosisText: String
    )

    fun parseSafeDouble(raw: String?, fallback: Double, min: Double, max: Double): Double {
        if (raw == null) return clamp(fallback, min, max)
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return clamp(fallback, min, max)

        val clean = StringBuilder(trimmed.length)
        var seenDot = false
        for (c in trimmed) {
            if (c in '0'..'9') {
                clean.append(c)
            } else if ((c == '.' || c == ',') && !seenDot) {
                clean.append('.')
                seenDot = true
            }
        }
        val str = clean.toString()
        if (str.isEmpty() || str == ".") return clamp(fallback, min, max)

        return try {
            val v = str.toDouble()
            if (v.isNaN() || v.isInfinite()) clamp(fallback, min, max) else clamp(v, min, max)
        } catch (e: NumberFormatException) {
            clamp(fallback, min, max)
        }
    }

    fun clamp(value: Double, min: Double, max: Double): Double {
        if (value.isNaN() || value.isInfinite()) return min
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    fun getModeName(mode: Int): String = when (mode) {
        MODE_Z_DEPTH -> "Z-DEPTH AXIS"
        MODE_XY_PLANE -> "XY PLANE"
        else -> "3D VECTOR"
    }

    fun evaluate(
        bxInput: Double,
        byInput: Double,
        bzInput: Double,
        baselineTareInput: Double,
        alarmDeltaInput: Double,
        couplingMode: Int
    ): GaussTelemetry {
        val safeBx = clamp(bxInput, -5000.0, 5000.0)
        val safeBy = clamp(byInput, -5000.0, 5000.0)
        val safeBz = clamp(bzInput, -5000.0, 5000.0)
        val safeTare = clamp(baselineTareInput, 0.0, 2000.0)
        val safeAlarm = clamp(alarmDeltaInput, 5.0, 2000.0)

        val activeFluxUt = when (couplingMode) {
            MODE_Z_DEPTH -> abs(safeBz)
            MODE_XY_PLANE -> hypot(safeBx, safeBy)
            else -> sqrt(safeBx * safeBx + safeBy * safeBy + safeBz * safeBz)
        }

        val totalFluxUt = clamp(activeFluxUt, 0.0, 9999.0)
        val totalFluxMg = totalFluxUt * 10.0

        val netDeltaUt = clamp(abs(totalFluxUt - safeTare), 0.0, 9999.0)
        val netDeltaMg = netDeltaUt * 10.0

        val alarmPercent = clamp((netDeltaUt / safeAlarm) * 100.0, 0.0, 999.0)

        // Empirical inverse-cube ferrous dipole depth estimate in cm (1.0 .. 18.0 cm)
        val estDepthCm = if (netDeltaUt <= 1.0) {
            15.0
        } else {
            clamp(12.5 / cbrt(netDeltaUt), 0.8, 16.0)
        }

        val badge = classifyZoneBadge(netDeltaUt, safeAlarm)
        val diagnosis = buildDiagnosis(totalFluxUt, totalFluxMg, netDeltaUt, safeAlarm, alarmPercent, estDepthCm)

        return GaussTelemetry(
            bxUt = safeBx,
            byUt = safeBy,
            bzUt = safeBz,
            totalFluxUt = totalFluxUt,
            totalFluxMg = totalFluxMg,
            baselineTareUt = safeTare,
            netDeltaUt = netDeltaUt,
            netDeltaMg = netDeltaMg,
            alarmThresholdUt = safeAlarm,
            alarmPercent = alarmPercent,
            estDepthCm = estDepthCm,
            modeLabel = getModeName(couplingMode),
            zoneBadge = badge,
            diagnosisText = diagnosis
        )
    }

    private fun classifyZoneBadge(deltaUt: Double, alarmUt: Double): String = when {
        deltaUt < 12.0 -> "EARTH NORMAL"
        deltaUt < alarmUt * 0.65 -> "MILD FERROUS"
        deltaUt < alarmUt * 1.45 -> "STUD / FERROUS"
        else -> "STRONG MAGNET"
    }

    private fun buildDiagnosis(
        totalUt: Double,
        totalMg: Double,
        deltaUt: Double,
        alarmUt: Double,
        alarmPct: Double,
        depthCm: Double
    ): String {
        val note = when {
            deltaUt < 12.0 ->
                "Within natural geomagnetic background (25–65 µT). No shallow ferrous stud or live coil detected."
            deltaUt < 45.0 ->
                "Moderate magnetic perturbation. Consistent with deep drywall fastener, rebar, or nearby DC cable."
            deltaUt < 140.0 ->
                "Clear ferrous anomaly signature. Matches steel stud flange, drywall screw head, or EMT conduit."
            else ->
                "High-intensity magnetic flux density. Indicates permanent magnet, speaker driver, or energized transformer."
        }
        return String.format(
            Locale.US,
            "Total %.1f µT (%.0f mG) • Net Delta +%.1f µT (%.0f%% of %.0f µT Alarm) • Depth ~%.1f cm\n%s",
            totalUt,
            totalMg,
            deltaUt,
            alarmPct,
            alarmUt,
            depthCm,
            note
        )
    }
}
