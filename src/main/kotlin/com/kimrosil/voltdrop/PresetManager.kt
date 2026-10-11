package com.kimrosil.voltdrop

data class CircuitPreset(
    val title: String,
    val voltage: Double,
    val currentAmps: Double,
    val distanceVal: Double,
    val isFeet: Boolean,
    val isCopper: Boolean,
    val maxDropPercent: Double
)

object PresetManager {
    val PRESETS = listOf(
        CircuitPreset(
            title = "12V RV Inverter (100A)",
            voltage = 12.0,
            currentAmps = 100.0,
            distanceVal = 10.0,
            isFeet = true,
            isCopper = true,
            maxDropPercent = 3.0
        ),
        CircuitPreset(
            title = "24V Solar Charge Run (30A)",
            voltage = 24.0,
            currentAmps = 30.0,
            distanceVal = 30.0,
            isFeet = true,
            isCopper = true,
            maxDropPercent = 3.0
        ),
        CircuitPreset(
            title = "48V Off-Grid Storage (50A)",
            voltage = 48.0,
            currentAmps = 50.0,
            distanceVal = 20.0,
            isFeet = true,
            isCopper = true,
            maxDropPercent = 3.0
        ),
        CircuitPreset(
            title = "12V Marine Trolling Motor (45A)",
            voltage = 12.0,
            currentAmps = 45.0,
            distanceVal = 16.0,
            isFeet = true,
            isCopper = true,
            maxDropPercent = 3.0
        ),
        CircuitPreset(
            title = "12V Car Audio Amp (80A)",
            voltage = 12.0,
            currentAmps = 80.0,
            distanceVal = 18.0,
            isFeet = true,
            isCopper = true,
            maxDropPercent = 3.0
        ),
        CircuitPreset(
            title = "120V Workshop Feed (20A)",
            voltage = 120.0,
            currentAmps = 20.0,
            distanceVal = 65.0,
            isFeet = true,
            isCopper = true,
            maxDropPercent = 3.0
        ),
        CircuitPreset(
            title = "240V Level 2 EV Charger (40A)",
            voltage = 240.0,
            currentAmps = 40.0,
            distanceVal = 45.0,
            isFeet = true,
            isCopper = false, // Aluminum feeder wire
            maxDropPercent = 3.0
        )
    )
}
