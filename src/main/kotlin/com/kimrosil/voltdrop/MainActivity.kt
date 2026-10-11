package com.kimrosil.voltdrop

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    // Top Header Views
    private lateinit var btnThemeToggle: Button
    private lateinit var btnOpenReference: Button

    // Hero HUD Telemetry
    private lateinit var tvHeroGauge: TextView
    private lateinit var tvHeroMm2: TextView
    private lateinit var tvHeroStatus: TextView
    private lateinit var tvVoltageDrop: TextView
    private lateinit var tvLoadVoltage: TextView
    private lateinit var tvPowerLoss: TextView
    private lateinit var tvCircuitResistance: TextView
    private lateinit var tvStatusMessage: TextView

    // Quick Presets
    private lateinit var llPresetsContainer: LinearLayout

    // Controls
    private lateinit var btnVolt12: Button
    private lateinit var btnVolt24: Button
    private lateinit var btnVolt48: Button
    private lateinit var btnVolt120: Button
    private lateinit var btnVolt240: Button
    private lateinit var etCustomVoltage: EditText

    private lateinit var btnToggleConductor: Button
    private lateinit var btnToggleLimit: Button

    private lateinit var etDistance: EditText
    private lateinit var btnToggleDistanceUnit: Button
    private lateinit var btnDistMinus: Button
    private lateinit var btnDistPlus: Button

    private lateinit var etCurrent: EditText
    private lateinit var btnAmpMinus: Button
    private lateinit var btnAmpPlus: Button
    private lateinit var btnAmpPlus10: Button

    private lateinit var btnPrevGauge: Button
    private lateinit var tvManualGaugeDisplay: TextView
    private lateinit var btnNextGauge: Button

    // Actions
    private lateinit var btnCalculate: Button
    private lateinit var btnSaveRecord: Button
    private lateinit var btnShare: Button
    private lateinit var tvAdNotice: TextView

    // Engine & State
    private var currentVoltage: Double = 12.0
    private var isCopper: Boolean = true
    private var maxDropPercent: Double = 3.0
    private var isFeet: Boolean = true
    private var manualGaugeIndex: Int? = null // null = Auto recommendation
    private var lastResult: CalculationResult? = null

    private lateinit var prefs: SharedPreferences
    private lateinit var dbHelper: DatabaseHelper

    override fun attachBaseContext(newBase: Context) {
        val sp = newBase.getSharedPreferences("voltdrop_prefs", Context.MODE_PRIVATE)
        val mode = sp.getInt("pref_theme_mode", 0)
        val config = Configuration(newBase.resources.configuration)

        val nightMode = when (mode) {
            1 -> Configuration.UI_MODE_NIGHT_NO
            2 -> Configuration.UI_MODE_NIGHT_YES
            else -> {
                val sysMode = try {
                    Settings.Secure.getInt(newBase.contentResolver, "ui_night_mode", 0)
                } catch (e: Exception) {
                    0
                }
                if (sysMode == 2) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
        }
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("voltdrop_prefs", Context.MODE_PRIVATE)
        dbHelper = DatabaseHelper(this)

        initViews()
        setupListeners()
        updateThemeButtonLabel()
        populatePresets()

        // Configure system bars contrast
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.insetsController?.setSystemBarsAppearance(
            if (!isNight) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )

        // Initial calculation
        performCalculation()
    }

    private fun initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        btnOpenReference = findViewById(R.id.btnOpenReference)

        tvHeroGauge = findViewById(R.id.tvHeroGauge)
        tvHeroMm2 = findViewById(R.id.tvHeroMm2)
        tvHeroStatus = findViewById(R.id.tvHeroStatus)
        tvVoltageDrop = findViewById(R.id.tvVoltageDrop)
        tvLoadVoltage = findViewById(R.id.tvLoadVoltage)
        tvPowerLoss = findViewById(R.id.tvPowerLoss)
        tvCircuitResistance = findViewById(R.id.tvCircuitResistance)
        tvStatusMessage = findViewById(R.id.tvStatusMessage)

        llPresetsContainer = findViewById(R.id.llPresetsContainer)

        btnVolt12 = findViewById(R.id.btnVolt12)
        btnVolt24 = findViewById(R.id.btnVolt24)
        btnVolt48 = findViewById(R.id.btnVolt48)
        btnVolt120 = findViewById(R.id.btnVolt120)
        btnVolt240 = findViewById(R.id.btnVolt240)
        etCustomVoltage = findViewById(R.id.etCustomVoltage)

        btnToggleConductor = findViewById(R.id.btnToggleConductor)
        btnToggleLimit = findViewById(R.id.btnToggleLimit)

        etDistance = findViewById(R.id.etDistance)
        btnToggleDistanceUnit = findViewById(R.id.btnToggleDistanceUnit)
        btnDistMinus = findViewById(R.id.btnDistMinus)
        btnDistPlus = findViewById(R.id.btnDistPlus)

        etCurrent = findViewById(R.id.etCurrent)
        btnAmpMinus = findViewById(R.id.btnAmpMinus)
        btnAmpPlus = findViewById(R.id.btnAmpPlus)
        btnAmpPlus10 = findViewById(R.id.btnAmpPlus10)

        btnPrevGauge = findViewById(R.id.btnPrevGauge)
        tvManualGaugeDisplay = findViewById(R.id.tvManualGaugeDisplay)
        btnNextGauge = findViewById(R.id.btnNextGauge)

        btnCalculate = findViewById(R.id.btnCalculate)
        btnSaveRecord = findViewById(R.id.btnSaveRecord)
        btnShare = findViewById(R.id.btnShare)
        tvAdNotice = findViewById(R.id.tvAdNotice)
    }

    private fun setupListeners() {
        btnThemeToggle.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            cycleThemeMode()
        }

        btnOpenReference.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, DetailActivity::class.java))
        }

        // Voltage Selectors
        val voltButtons = listOf(btnVolt12 to 12.0, btnVolt24 to 24.0, btnVolt48 to 48.0, btnVolt120 to 120.0, btnVolt240 to 240.0)
        voltButtons.forEach { (btn, vVal) ->
            btn.setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                currentVoltage = vVal
                etCustomVoltage.setText("")
                highlightVoltageButton(btn)
                performCalculation()
            }
        }

        etCustomVoltage.setOnEditorActionListener { _, _, _ ->
            val custom = etCustomVoltage.text.toString().toDoubleOrNull()
            if (custom != null && custom > 0) {
                currentVoltage = custom
                resetVoltageButtons()
                performCalculation()
            }
            false
        }

        btnToggleConductor.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isCopper = !isCopper
            btnToggleConductor.text = if (isCopper) "Copper (Cu)" else "Aluminum (Al)"
            btnToggleConductor.setTextColor(if (isCopper) getColor(R.color.copper_accent) else getColor(R.color.telemetry_accent))
            performCalculation()
        }

        btnToggleLimit.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            maxDropPercent = if (maxDropPercent == 3.0) 5.0 else 3.0
            btnToggleLimit.text = if (maxDropPercent == 3.0) "3.0% (Critical)" else "5.0% (General)"
            performCalculation()
        }

        btnToggleDistanceUnit.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isFeet = !isFeet
            btnToggleDistanceUnit.text = if (isFeet) "FEET" else "METERS"
            // Convert current input value
            val curDist = etDistance.text.toString().toDoubleOrNull() ?: 15.0
            val converted = if (isFeet) CalculatorEngine.metersToFeet(curDist) else CalculatorEngine.feetToMeters(curDist)
            etDistance.setText(String.format("%.1f", converted))
            performCalculation()
        }

        btnDistMinus.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            adjustDistance(-5.0)
        }

        btnDistPlus.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            adjustDistance(5.0)
        }

        btnAmpMinus.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            adjustAmps(-5.0)
        }

        btnAmpPlus.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            adjustAmps(5.0)
        }

        btnAmpPlus10.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            adjustAmps(10.0)
        }

        btnPrevGauge.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            stepGauge(-1) // Thicker gauge (lower index)
        }

        btnNextGauge.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            stepGauge(1) // Thinner gauge (higher index)
        }

        btnCalculate.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            performCalculation()
            Toast.makeText(this, "Telemetry Updated", Toast.LENGTH_SHORT).show()
        }

        btnSaveRecord.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            saveCurrentCalculation()
        }

        btnShare.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            shareCalculationSpec()
        }

        tvAdNotice.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com"))
            startActivity(i)
        }
    }

    private fun highlightVoltageButton(selected: Button) {
        val buttons = listOf(btnVolt12, btnVolt24, btnVolt48, btnVolt120, btnVolt240)
        buttons.forEach { b ->
            if (b == selected) {
                b.setBackgroundResource(R.drawable.chip_active)
                b.setTextColor(getColor(R.color.m3_on_primary))
            } else {
                b.setBackgroundResource(R.drawable.chip_inactive)
                b.setTextColor(getColor(R.color.m3_on_surface))
            }
        }
    }

    private fun resetVoltageButtons() {
        listOf(btnVolt12, btnVolt24, btnVolt48, btnVolt120, btnVolt240).forEach { b ->
            b.setBackgroundResource(R.drawable.chip_inactive)
            b.setTextColor(getColor(R.color.m3_on_surface))
        }
    }

    private fun adjustDistance(delta: Double) {
        val current = etDistance.text.toString().toDoubleOrNull() ?: 15.0
        val next = Math.max(1.0, current + delta)
        etDistance.setText(String.format("%.1f", next))
        performCalculation()
    }

    private fun adjustAmps(delta: Double) {
        val current = etCurrent.text.toString().toDoubleOrNull() ?: 40.0
        val next = Math.max(1.0, current + delta)
        etCurrent.setText(String.format("%.1f", next))
        performCalculation()
    }

    private fun stepGauge(step: Int) {
        val currentIndex = manualGaugeIndex ?: (lastResult?.let { CalculatorEngine.WIRE_GAUGES.indexOf(it.recommendedGauge) } ?: 6)
        val nextIndex = (currentIndex + step).coerceIn(0, CalculatorEngine.WIRE_GAUGES.lastIndex)
        manualGaugeIndex = nextIndex
        tvManualGaugeDisplay.text = "Manual: ${CalculatorEngine.WIRE_GAUGES[nextIndex].name}"
        performCalculation()
    }

    private fun populatePresets() {
        llPresetsContainer.removeAllViews()
        for (preset in PresetManager.PRESETS) {
            val chip = Button(this).apply {
                text = preset.title
                textSize = 11f
                setBackgroundResource(R.drawable.chip_inactive)
                setTextColor(getColor(R.color.m3_on_surface))
                setPadding(24, 0, 24, 0)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    resources.getDimensionPixelSize(android.R.dimen.app_icon_size).coerceAtMost(90)
                ).apply {
                    setMargins(0, 0, 12, 0)
                }
                layoutParams = params
                setOnClickListener { v ->
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    applyPreset(preset)
                }
            }
            llPresetsContainer.addView(chip)
        }
    }

    private fun applyPreset(preset: CircuitPreset) {
        currentVoltage = preset.voltage
        etCustomVoltage.setText("")
        val voltBtns = mapOf(12.0 to btnVolt12, 24.0 to btnVolt24, 48.0 to btnVolt48, 120.0 to btnVolt120, 240.0 to btnVolt240)
        voltBtns[currentVoltage]?.let { highlightVoltageButton(it) } ?: resetVoltageButtons()

        isCopper = preset.isCopper
        btnToggleConductor.text = if (isCopper) "Copper (Cu)" else "Aluminum (Al)"

        maxDropPercent = preset.maxDropPercent
        btnToggleLimit.text = "${preset.maxDropPercent}% Target"

        isFeet = preset.isFeet
        btnToggleDistanceUnit.text = if (isFeet) "FEET" else "METERS"
        etDistance.setText(preset.distanceVal.toString())

        etCurrent.setText(preset.currentAmps.toString())
        manualGaugeIndex = null
        tvManualGaugeDisplay.text = "Auto (Recommended)"

        performCalculation()
    }

    private fun performCalculation() {
        val customV = etCustomVoltage.text.toString().toDoubleOrNull()
        val vSource = if (customV != null && customV > 0) customV else currentVoltage

        val distVal = etDistance.text.toString().toDoubleOrNull() ?: 15.0
        val distMeters = if (isFeet) CalculatorEngine.feetToMeters(distVal) else distVal

        val amps = etCurrent.text.toString().toDoubleOrNull() ?: 40.0

        val res = CalculatorEngine.calculate(
            sourceVoltage = vSource,
            distanceMeters = distMeters,
            currentAmps = amps,
            isCopper = isCopper,
            maxDropPercent = maxDropPercent,
            manualGaugeIndex = manualGaugeIndex
        )
        lastResult = res
        displayResults(res)
    }

    private fun displayResults(res: CalculationResult) {
        val displayGauge = res.evaluatedGauge
        tvHeroGauge.text = displayGauge.name
        tvHeroMm2.text = "${displayGauge.areaMm2} mm² • ${if (res.isCopper) "Copper" else "Aluminum"} • Rating: ${displayGauge.getAmpacity(75)}A"

        when (res.status) {
            is DropStatus.Safe -> {
                tvHeroStatus.text = "COMPLIANT"
                tvHeroStatus.setBackgroundResource(R.drawable.badge_safe)
            }
            is DropStatus.Caution -> {
                tvHeroStatus.text = "CAUTION"
                tvHeroStatus.setBackgroundResource(R.drawable.badge_warning)
            }
            is DropStatus.Danger -> {
                tvHeroStatus.text = "DANGER"
                tvHeroStatus.setBackgroundResource(R.drawable.badge_danger)
            }
        }

        tvVoltageDrop.text = "${String.format("%.2f", res.voltageDropVolts)} V (${String.format("%.1f", res.voltageDropPercent)}%)"
        tvLoadVoltage.text = "${String.format("%.2f", res.loadVoltage)} V"
        tvPowerLoss.text = "${String.format("%.1f", res.powerLossWatts)} W"
        tvCircuitResistance.text = "${String.format("%.4f", res.circuitResistanceOhms)} Ω"
        tvStatusMessage.text = res.statusMessage
    }

    private fun saveCurrentCalculation() {
        val r = lastResult ?: return
        val distVal = etDistance.text.toString().toDoubleOrNull() ?: 15.0
        val unitStr = if (isFeet) "ft" else "m"
        val conductorStr = if (isCopper) "Copper" else "Aluminum"

        val title = "${r.sourceVoltage.toInt()}V @ ${r.currentAmps.toInt()}A Run"
        val id = dbHelper.insertRecord(
            title = title,
            voltage = r.sourceVoltage,
            current = r.currentAmps,
            distance = distVal,
            unit = unitStr,
            conductor = conductorStr,
            gauge = r.evaluatedGauge.name,
            dropVolts = r.voltageDropVolts,
            dropPercent = r.voltageDropPercent,
            loadVolts = r.loadVoltage,
            powerLoss = r.powerLossWatts
        )
        if (id > 0) {
            Toast.makeText(this, "Calculation saved to offline history!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCalculationSpec() {
        val r = lastResult ?: return
        val distVal = etDistance.text.toString().toDoubleOrNull() ?: 15.0
        val unitStr = if (isFeet) "ft" else "m"
        val conductorStr = if (isCopper) "Copper" else "Aluminum"

        val spec = """
            ⚡ VoltDrop Electrical Specification
            • Conductor: ${r.evaluatedGauge.name} (${r.evaluatedGauge.areaMm2} mm² ${conductorStr})
            • Supply: ${r.sourceVoltage}V | Load: ${r.currentAmps}A
            • One-Way Distance: $distVal $unitStr
            • Voltage Drop: ${String.format("%.2f", r.voltageDropVolts)}V (${String.format("%.1f", r.voltageDropPercent)}%)
            • End Load Voltage: ${String.format("%.2f", r.loadVoltage)}V
            • Heat Dissipated: ${String.format("%.1f", r.powerLossWatts)} Watts
            • Circuit Resistance: ${String.format("%.4f", r.circuitResistanceOhms)} Ω
            • NEC Compliance: ${r.statusMessage}
            Generated offline by VoltDrop (apps.kimrosil.com)
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "VoltDrop Wiring Spec - ${r.evaluatedGauge.name}")
            putExtra(Intent.EXTRA_TEXT, spec)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Wiring Specification"))
    }

    private fun cycleThemeMode() {
        val current = prefs.getInt("pref_theme_mode", 0)
        val next = (current + 1) % 3
        prefs.edit().putInt("pref_theme_mode", next).apply()
        recreate()
    }

    private fun updateThemeButtonLabel() {
        val mode = prefs.getInt("pref_theme_mode", 0)
        btnThemeToggle.text = when (mode) {
            1 -> "Theme: Light"
            2 -> "Theme: Dark"
            else -> "Theme: Auto"
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    performCalculation()
                    return true
                }
                KeyEvent.KEYCODE_SPACE -> {
                    btnToggleDistanceUnit.performClick()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
