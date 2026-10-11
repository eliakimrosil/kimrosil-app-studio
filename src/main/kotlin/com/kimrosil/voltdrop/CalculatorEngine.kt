package com.kimrosil.voltdrop

/**
 * High-precision Electrical Wire Gauge and Voltage Drop Engine.
 * Adheres to National Electrical Code (NEC) Chapter 9 Table 8 & Table 310.16.
 */
data class WireGauge(
    val name: String,
    val gaugeNumber: Int, // e.g. -3 for 4/0, 4 for 4 AWG
    val areaMm2: Double,
    val copperResistancePerKm: Double,    // Ohms/km at 75°C
    val aluminumResistancePerKm: Double,  // Ohms/km at 75°C
    val ampacity60C: Int,
    val ampacity75C: Int,
    val ampacity90C: Int
) {
    fun getResistance(isCopper: Boolean): Double {
        return if (isCopper) copperResistancePerKm else aluminumResistancePerKm
    }

    fun getAmpacity(tempRating: Int = 75): Int {
        return when (tempRating) {
            60 -> ampacity60C
            90 -> ampacity90C
            else -> ampacity75C
        }
    }
}

sealed class DropStatus {
    object Safe : DropStatus()
    object Caution : DropStatus()
    object Danger : DropStatus()
}

data class CalculationResult(
    val recommendedGauge: WireGauge,
    val evaluatedGauge: WireGauge,
    val sourceVoltage: Double,
    val currentAmps: Double,
    val oneWayDistanceMeters: Double,
    val isCopper: Boolean,
    val voltageDropVolts: Double,
    val voltageDropPercent: Double,
    val loadVoltage: Double,
    val powerLossWatts: Double,
    val circuitResistanceOhms: Double,
    val status: DropStatus,
    val statusMessage: String,
    val ampacitySafetyMarginPercent: Double
)

object CalculatorEngine {

    // Standard NEC Wire Gauge Library (Sorted thickest to thinnest)
    val WIRE_GAUGES: List<WireGauge> = listOf(
        WireGauge("4/0 AWG", -3, 107.2, 0.199, 0.325, 195, 230, 260),
        WireGauge("3/0 AWG", -2, 85.0,  0.252, 0.410, 165, 200, 225),
        WireGauge("2/0 AWG", -1, 67.4,  0.318, 0.518, 145, 175, 195),
        WireGauge("1/0 AWG", 0,  53.5,  0.400, 0.653, 125, 150, 170),
        WireGauge("1 AWG",   1,  42.4,  0.505, 0.823, 110, 130, 145),
        WireGauge("2 AWG",   2,  33.6,  0.636, 1.040, 95,  115, 130),
        WireGauge("4 AWG",   4,  21.2,  1.010, 1.650, 70,  85,  95),
        WireGauge("6 AWG",   6,  13.3,  1.610, 2.620, 55,  65,  75),
        WireGauge("8 AWG",   8,  8.37,  2.560, 4.170, 40,  50,  55),
        WireGauge("10 AWG",  10, 5.26,  4.070, 6.630, 30,  35,  40),
        WireGauge("12 AWG",  12, 3.31,  6.460, 10.50, 20,  25,  30),
        WireGauge("14 AWG",  14, 2.08,  10.27, 16.70, 15,  20,  25),
        WireGauge("16 AWG",  16, 1.31,  16.30, 26.60, 10,  13,  15),
        WireGauge("18 AWG",  18, 0.82,  26.00, 42.30, 7,   10,  12),
        WireGauge("20 AWG",  20, 0.52,  41.30, 67.20, 5,   7,   8),
        WireGauge("22 AWG",  22, 0.33,  65.60, 107.0, 3,   5,   6)
    )

    fun feetToMeters(feet: Double): Double = feet * 0.3048

    fun metersToFeet(meters: Double): Double = meters / 0.3048

    /**
     * Determines optimal recommended wire gauge and calculates exact electrical telemetry.
     */
    fun calculate(
        sourceVoltage: Double,
        distanceMeters: Double,
        currentAmps: Double,
        isCopper: Boolean,
        maxDropPercent: Double = 3.0,
        manualGaugeIndex: Int? = null
    ): CalculationResult {
        val safeVoltage = if (sourceVoltage <= 0.0) 12.0 else sourceVoltage
        val safeDistance = if (distanceMeters < 0.1) 0.1 else distanceMeters
        val safeAmps = if (currentAmps <= 0.0) 1.0 else currentAmps

        // 1. Find ideal recommended gauge
        val recommended = findRecommended(safeVoltage, safeDistance, safeAmps, isCopper, maxDropPercent)

        // 2. Select gauge to evaluate
        val evaluated = if (manualGaugeIndex != null && manualGaugeIndex in WIRE_GAUGES.indices) {
            WIRE_GAUGES[manualGaugeIndex]
        } else {
            recommended
        }

        // 3. Round-trip conductor length = 2 * distance
        val totalLengthKm = (safeDistance * 2.0) / 1000.0
        val resistancePerKm = evaluated.getResistance(isCopper)
        val circuitResistance = totalLengthKm * resistancePerKm

        // 4. Ohms law calculation
        val voltageDrop = safeAmps * circuitResistance
        val dropPercent = (voltageDrop / safeVoltage) * 100.0
        val loadVoltage = Math.max(0.0, safeVoltage - voltageDrop)
        val powerLoss = (safeAmps * safeAmps) * circuitResistance

        // 5. Ampacity check
        val ratedAmpacity = evaluated.getAmpacity(75).toDouble()
        val ampacityMargin = ((ratedAmpacity - safeAmps) / ratedAmpacity) * 100.0

        // 6. Safety Status classification
        val (status, message) = when {
            safeAmps > ratedAmpacity -> {
                Pair(DropStatus.Danger, "CRITICAL: Current (${String.format("%.1f", safeAmps)}A) exceeds conductor safe ampacity (${ratedAmpacity.toInt()}A)!")
            }
            dropPercent > 5.0 -> {
                Pair(DropStatus.Danger, "EXCESSIVE LOSS: Drop is ${String.format("%.1f", dropPercent)}% (Exceeds 5.0% maximum safety limit).")
            }
            dropPercent > maxDropPercent -> {
                Pair(DropStatus.Caution, "CAUTION: Drop is ${String.format("%.1f", dropPercent)}% (Exceeds target ${String.format("%.1f", maxDropPercent)}% threshold).")
            }
            else -> {
                Pair(DropStatus.Safe, "COMPLIANT: ${evaluated.name} operates safely within ${String.format("%.1f", maxDropPercent)}% NEC guideline.")
            }
        }

        return CalculationResult(
            recommendedGauge = recommended,
            evaluatedGauge = evaluated,
            sourceVoltage = safeVoltage,
            currentAmps = safeAmps,
            oneWayDistanceMeters = safeDistance,
            isCopper = isCopper,
            voltageDropVolts = voltageDrop,
            voltageDropPercent = dropPercent,
            loadVoltage = loadVoltage,
            powerLossWatts = powerLoss,
            circuitResistanceOhms = circuitResistance,
            status = status,
            statusMessage = message,
            ampacitySafetyMarginPercent = ampacityMargin
        )
    }

    private fun findRecommended(
        voltage: Double,
        distanceMeters: Double,
        amps: Double,
        isCopper: Boolean,
        maxDropPercent: Double
    ): WireGauge {
        // Iterate from thinnest to thickest to find minimal gauge meeting BOTH ampacity and drop criteria
        val candidates = WIRE_GAUGES.reversed()
        for (g in candidates) {
            val totalLengthKm = (distanceMeters * 2.0) / 1000.0
            val resistance = totalLengthKm * g.getResistance(isCopper)
            val dropVolts = amps * resistance
            val dropPct = (dropVolts / voltage) * 100.0
            val ratedAmpacity = g.getAmpacity(75)

            if (dropPct <= maxDropPercent && amps <= ratedAmpacity) {
                return g
            }
        }
        // If none thin enough met criteria, return thickest available (4/0 AWG)
        return WIRE_GAUGES.first()
    }
}
