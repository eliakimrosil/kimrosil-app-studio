package com.kimrosil.gaussmeter

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * GaussMeter Main 3-Axis Magnetometer & Ferrous Stud Locator Activity (Pure Native Kotlin).
 */
class MainActivity : Activity(), SensorEventListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var dbHelper: GaussDatabaseHelper
    private var sensorManager: SensorManager? = null
    private var magneticSensor: Sensor? = null

    private var activeTab = 0 // 0=Flux, 1=Stud, 2=Log
    private var couplingMode = GaussEngine.MODE_3D_VECTOR
    private var isHoldLocked = false
    private var useMilligaussPrimary = false
    private var hapticsEnabled = true

    private var hasHardwareReading = false
    private var liveBxUt = 28.0
    private var liveByUt = -34.0
    private var liveBzUt = 106.0
    private var sessionPeakUt = 140.0
    private var manualCalLockUntilMs = 0L
    private var lastSensorUiUpdateMs = 0L

    private var latestTelemetry: GaussEngine.GaussTelemetry? = null

    // Hero HUD Views
    private lateinit var tvSensorState: TextView
    private lateinit var tvZoneBadge: TextView
    private lateinit var gaussScopeView: GaussScopeView
    private lateinit var tvHeroFlux: TextView
    private lateinit var tvHeroMgSub: TextView
    private lateinit var tvHeroDelta: TextView
    private lateinit var tvHeroProximitySub: TextView
    private lateinit var tvHeroAxes: TextView
    private lateinit var tvHeroTareSub: TextView
    private lateinit var tvHeroDepthPeak: TextView
    private lateinit var tvHeroTargetSub: TextView

    // Tab Buttons & Panels
    private lateinit var btnTabMeter: Button
    private lateinit var btnTabStud: Button
    private lateinit var btnTabLog: Button
    private lateinit var panelMeter: View
    private lateinit var panelStud: View
    private lateinit var panelLog: View

    // Panel 1: Flux & Tare Controls
    private lateinit var etRefFlux: EditText
    private lateinit var etBaselineTare: EditText
    private lateinit var btnHoldSensor: Button
    private lateinit var btnModeVector: Button
    private lateinit var btnModeZAxis: Button
    private lateinit var btnModeXYPlane: Button
    private lateinit var tvSensorHardwareInfo: TextView

    // Panel 2: Stud & Alarm Controls
    private lateinit var etAlertThreshold: EditText
    private lateinit var tvGaussDiagnosis: TextView

    // Panel 3: SQLite Scan Log Controls
    private lateinit var etZoneName: EditText
    private lateinit var llScanLogList: LinearLayout

    private var suppressTextWatcher = false

    override fun attachBaseContext(newBase: Context) {
        val sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode = sp.getInt(KEY_THEME_MODE, 2) // Default Magnetite Obsidian Dark Mode
        val config = Configuration(newBase.resources.configuration)
        val nightFlags = config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()
        if (mode == 1) {
            config.uiMode = nightFlags or Configuration.UI_MODE_NIGHT_NO
        } else if (mode == 2) {
            config.uiMode = nightFlags or Configuration.UI_MODE_NIGHT_YES
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        dbHelper = GaussDatabaseHelper(this)

        useMilligaussPrimary = prefs.getBoolean(KEY_PRIMARY_UNIT_MG, false)
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true)
        couplingMode = prefs.getInt(KEY_COUPLING_MODE, GaussEngine.MODE_3D_VECTOR)

        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        magneticSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        bindViews()
        if (savedInstanceState == null) {
            restoreDraftInputs()
        }
        updateTabSelection(activeTab)
        updateModeChips()
        recalculateAndRender(true)
        refreshScanLogList()
    }

    private fun bindViews() {
        tvSensorState = findViewById(R.id.tvSensorState)
        tvZoneBadge = findViewById(R.id.tvZoneBadge)
        gaussScopeView = findViewById(R.id.gaussScopeView)
        tvHeroFlux = findViewById(R.id.tvHeroFlux)
        tvHeroMgSub = findViewById(R.id.tvHeroMgSub)
        tvHeroDelta = findViewById(R.id.tvHeroDelta)
        tvHeroProximitySub = findViewById(R.id.tvHeroProximitySub)
        tvHeroAxes = findViewById(R.id.tvHeroAxes)
        tvHeroTareSub = findViewById(R.id.tvHeroTareSub)
        tvHeroDepthPeak = findViewById(R.id.tvHeroDepthPeak)
        tvHeroTargetSub = findViewById(R.id.tvHeroTargetSub)

        btnTabMeter = findViewById(R.id.btnTabMeter)
        btnTabStud = findViewById(R.id.btnTabStud)
        btnTabLog = findViewById(R.id.btnTabLog)
        panelMeter = findViewById(R.id.panelMeter)
        panelStud = findViewById(R.id.panelStud)
        panelLog = findViewById(R.id.panelLog)

        etRefFlux = findViewById(R.id.etRefFlux)
        etBaselineTare = findViewById(R.id.etBaselineTare)
        btnHoldSensor = findViewById(R.id.btnHoldSensor)
        btnModeVector = findViewById(R.id.btnModeVector)
        btnModeZAxis = findViewById(R.id.btnModeZAxis)
        btnModeXYPlane = findViewById(R.id.btnModeXYPlane)
        tvSensorHardwareInfo = findViewById(R.id.tvSensorHardwareInfo)

        etAlertThreshold = findViewById(R.id.etAlertThreshold)
        tvGaussDiagnosis = findViewById(R.id.tvGaussDiagnosis)

        etZoneName = findViewById(R.id.etZoneName)
        llScanLogList = findViewById(R.id.llScanLogList)

        val btnThemeToggle = findViewById<ImageButton>(R.id.btnThemeToggle)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)

        btnThemeToggle.setOnClickListener { v ->
            triggerHaptic(v)
            val current = prefs.getInt(KEY_THEME_MODE, 2)
            val next = if (current == 1) 2 else 1
            prefs.edit().putInt(KEY_THEME_MODE, next).apply()
            recreate()
        }

        btnSettings.setOnClickListener { v ->
            triggerHaptic(v)
            showSettingsDialog()
        }

        btnTabMeter.setOnClickListener { v ->
            triggerHaptic(v)
            updateTabSelection(0)
        }
        btnTabStud.setOnClickListener { v ->
            triggerHaptic(v)
            updateTabSelection(1)
        }
        btnTabLog.setOnClickListener { v ->
            triggerHaptic(v)
            updateTabSelection(2)
        }

        findViewById<Button>(R.id.btnCalcGauss).setOnClickListener { v ->
            triggerHaptic(v)
            manualCalLockUntilMs = SystemClock.elapsedRealtime() + 10000L
            sanitizeInputsOnExplicitApply()
            hideKeyboard(v)
            recalculateAndRender(true)
        }

        btnHoldSensor.setOnClickListener { v ->
            triggerHaptic(v)
            isHoldLocked = !isHoldLocked
            btnHoldSensor.text = if (isHoldLocked) "Resume Live" else getString(R.string.btn_hold_flux)
            recalculateAndRender(false)
        }

        findViewById<Button>(R.id.btnFluxMinus).setOnClickListener { v ->
            triggerHaptic(v)
            stepEditText(etRefFlux, -10.0, 1.0, 2000.0, "%.1f")
        }
        findViewById<Button>(R.id.btnFluxPlus).setOnClickListener { v ->
            triggerHaptic(v)
            stepEditText(etRefFlux, 10.0, 1.0, 2000.0, "%.1f")
        }

        findViewById<Button>(R.id.btnTareMinus).setOnClickListener { v ->
            triggerHaptic(v)
            stepEditText(etBaselineTare, -5.0, 0.0, 1000.0, "%.1f")
        }
        findViewById<Button>(R.id.btnTarePlus).setOnClickListener { v ->
            triggerHaptic(v)
            stepEditText(etBaselineTare, 5.0, 0.0, 1000.0, "%.1f")
        }

        btnModeVector.setOnClickListener { v ->
            triggerHaptic(v)
            setCouplingMode(GaussEngine.MODE_3D_VECTOR)
        }
        btnModeZAxis.setOnClickListener { v ->
            triggerHaptic(v)
            setCouplingMode(GaussEngine.MODE_Z_DEPTH)
        }
        btnModeXYPlane.setOnClickListener { v ->
            triggerHaptic(v)
            setCouplingMode(GaussEngine.MODE_XY_PLANE)
        }

        findViewById<Button>(R.id.btnResetTare).setOnClickListener { v ->
            triggerHaptic(v)
            suppressTextWatcher = true
            val currentTotal = latestTelemetry?.totalFluxUt ?: 48.0
            etBaselineTare.setText(String.format(Locale.US, "%.1f", GaussEngine.clamp(currentTotal, 0.0, 999.0)))
            suppressTextWatcher = false
            saveDraftInputs()
            recalculateAndRender(false)
        }

        // Panel 2: Stud & Alarm Threshold Controls
        findViewById<Button>(R.id.btnAlarmMinus).setOnClickListener { v ->
            triggerHaptic(v)
            stepEditText(etAlertThreshold, -10.0, 10.0, 1000.0, "%.1f")
        }
        findViewById<Button>(R.id.btnAlarmPlus).setOnClickListener { v ->
            triggerHaptic(v)
            stepEditText(etAlertThreshold, 10.0, 10.0, 1000.0, "%.1f")
        }
        findViewById<Button>(R.id.btnApplyAlarm).setOnClickListener { v ->
            triggerHaptic(v)
            hideKeyboard(v)
            recalculateAndRender(true)
        }

        findViewById<Button>(R.id.btnPresetEarthField).setOnClickListener { v ->
            triggerHaptic(v)
            applyFluxPreset(48.0, 48.0, 85.0)
        }
        findViewById<Button>(R.id.btnPresetDrywallStud).setOnClickListener { v ->
            triggerHaptic(v)
            applyFluxPreset(115.0, 48.0, 65.0)
        }
        findViewById<Button>(R.id.btnPresetAcConduit).setOnClickListener { v ->
            triggerHaptic(v)
            applyFluxPreset(190.0, 48.0, 120.0)
        }
        findViewById<Button>(R.id.btnPresetRareMagnet).setOnClickListener { v ->
            triggerHaptic(v)
            applyFluxPreset(420.0, 48.0, 200.0)
        }

        // Panel 3: Save Scan Log
        findViewById<Button>(R.id.btnSaveScanLog).setOnClickListener { v ->
            triggerHaptic(v)
            hideKeyboard(v)
            saveCurrentScanToDb()
        }

        val liveWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressTextWatcher) return
                manualCalLockUntilMs = SystemClock.elapsedRealtime() + 8000L
                saveDraftInputs()
                recalculateAndRender(true)
            }
        }

        etRefFlux.addTextChangedListener(liveWatcher)
        etBaselineTare.addTextChangedListener(liveWatcher)
        etAlertThreshold.addTextChangedListener(liveWatcher)
    }

    private fun sanitizeInputsOnExplicitApply() {
        suppressTextWatcher = true
        try {
            val flux = GaussEngine.parseSafeDouble(etRefFlux.text.toString(), 115.0, 1.0, 2000.0)
            val tare = GaussEngine.parseSafeDouble(etBaselineTare.text.toString(), 48.0, 0.0, 1000.0)
            val alarm = GaussEngine.parseSafeDouble(etAlertThreshold.text.toString(), 85.0, 10.0, 1000.0)

            val rawFlux = etRefFlux.text.toString().trim()
            if (rawFlux.isEmpty() || rawFlux.length > 5 || rawFlux.startsWith("-")) {
                etRefFlux.setText(String.format(Locale.US, "%.1f", flux))
            }
            val rawTare = etBaselineTare.text.toString().trim()
            if (rawTare.isEmpty() || rawTare.length > 5 || rawTare.startsWith("-")) {
                etBaselineTare.setText(String.format(Locale.US, "%.1f", tare))
            }
            val rawAlarm = etAlertThreshold.text.toString().trim()
            if (rawAlarm.isEmpty() || rawAlarm.length > 4 || rawAlarm.startsWith("-")) {
                etAlertThreshold.setText(String.format(Locale.US, "%.1f", alarm))
            }
            saveDraftInputs()
        } finally {
            suppressTextWatcher = false
        }
    }

    private fun stepEditText(target: EditText, delta: Double, min: Double, max: Double, fmt: String) {
        manualCalLockUntilMs = SystemClock.elapsedRealtime() + 8000L
        val current = GaussEngine.parseSafeDouble(target.text.toString(), min, min, max)
        val next = GaussEngine.clamp(current + delta, min, max)
        suppressTextWatcher = true
        target.setText(String.format(Locale.US, fmt, next))
        suppressTextWatcher = false
        saveDraftInputs()
        recalculateAndRender(true)
    }

    private fun setCouplingMode(mode: Int) {
        couplingMode = mode
        prefs.edit().putInt(KEY_COUPLING_MODE, couplingMode).apply()
        updateModeChips()
        recalculateAndRender(false)
    }

    private fun applyFluxPreset(fluxUt: Double, tareUt: Double, alarmUt: Double) {
        manualCalLockUntilMs = SystemClock.elapsedRealtime() + 8000L
        suppressTextWatcher = true
        etRefFlux.setText(String.format(Locale.US, "%.1f", fluxUt))
        etBaselineTare.setText(String.format(Locale.US, "%.1f", tareUt))
        etAlertThreshold.setText(String.format(Locale.US, "%.1f", alarmUt))
        suppressTextWatcher = false
        saveDraftInputs()
        recalculateAndRender(true)
    }

    private fun updateTabSelection(tab: Int) {
        activeTab = tab
        panelMeter.visibility = if (tab == 0) View.VISIBLE else View.GONE
        panelStud.visibility = if (tab == 1) View.VISIBLE else View.GONE
        panelLog.visibility = if (tab == 2) View.VISIBLE else View.GONE

        styleChipButton(btnTabMeter, tab == 0)
        styleChipButton(btnTabStud, tab == 1)
        styleChipButton(btnTabLog, tab == 2)
    }

    private fun updateModeChips() {
        styleChipButton(btnModeVector, couplingMode == GaussEngine.MODE_3D_VECTOR)
        styleChipButton(btnModeZAxis, couplingMode == GaussEngine.MODE_Z_DEPTH)
        styleChipButton(btnModeXYPlane, couplingMode == GaussEngine.MODE_XY_PLANE)
    }

    private fun styleChipButton(btn: Button, selected: Boolean) {
        btn.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        btn.setTextColor(getColor(if (selected) R.color.text_on_accent else R.color.text_primary))
    }

    private fun recalculateAndRender(forceFromReference: Boolean) {
        val refFlux = GaussEngine.parseSafeDouble(etRefFlux.text.toString(), 115.0, 0.0, 5000.0)
        val tareUt = GaussEngine.parseSafeDouble(etBaselineTare.text.toString(), 48.0, 0.0, 2000.0)
        val alarmUt = GaussEngine.parseSafeDouble(etAlertThreshold.text.toString(), 85.0, 5.0, 2000.0)

        val useManual = forceFromReference ||
            (SystemClock.elapsedRealtime() < manualCalLockUntilMs) ||
            !hasHardwareReading

        val bx: Double
        val by: Double
        val bz: Double
        if (useManual) {
            // Decompose reference magnitude into realistic 3D components so all axis modes work
            bx = refFlux * 0.24
            by = -refFlux * 0.30
            bz = refFlux * 0.922
        } else {
            bx = liveBxUt
            by = liveByUt
            bz = liveBzUt
        }

        val telemetry = GaussEngine.evaluate(
            bxInput = bx,
            byInput = by,
            bzInput = bz,
            baselineTareInput = tareUt,
            alarmDeltaInput = alarmUt,
            couplingMode = couplingMode
        )
        latestTelemetry = telemetry
        sessionPeakUt = max(sessionPeakUt, telemetry.totalFluxUt)

        val statePrefix = when {
            isHoldLocked -> "HOLD LOCKED"
            hasHardwareReading && !useManual -> "LIVE 3-AXIS"
            else -> "REF MODE"
        }
        tvSensorState.text = "$statePrefix • ${telemetry.modeLabel}"
        tvZoneBadge.text = telemetry.zoneBadge

        if (useMilligaussPrimary) {
            tvHeroFlux.text = String.format(Locale.US, "%.0f mG", telemetry.totalFluxMg)
            tvHeroMgSub.text = String.format(Locale.US, "%.1f µT • Microtesla", telemetry.totalFluxUt)
        } else {
            tvHeroFlux.text = String.format(Locale.US, "%.1f µT", telemetry.totalFluxUt)
            tvHeroMgSub.text = String.format(Locale.US, "%.0f mG • Milligauss", telemetry.totalFluxMg)
        }

        tvHeroDelta.text = String.format(Locale.US, "+%.1f µT", telemetry.netDeltaUt)
        tvHeroProximitySub.text = String.format(
            Locale.US,
            "%.0f%% of %.0f µT Alarm",
            telemetry.alarmPercent,
            telemetry.alarmThresholdUt
        )

        tvHeroAxes.text = String.format(
            Locale.US,
            "X%+.0f Y%+.0f Z%+.0f µT",
            telemetry.bxUt,
            telemetry.byUt,
            telemetry.bzUt
        )
        tvHeroTareSub.text = String.format(Locale.US, "Earth Tare: %.1f µT", telemetry.baselineTareUt)

        tvHeroDepthPeak.text = String.format(
            Locale.US,
            "~%.1f cm • Peak %.0f µT",
            telemetry.estDepthCm,
            sessionPeakUt
        )
        tvHeroTargetSub.text = if (telemetry.netDeltaUt >= telemetry.alarmThresholdUt) {
            "ALERT: Ferrous Target Hit"
        } else {
            "Ferrous Stud / Pipe Zone"
        }

        tvSensorHardwareInfo.text = if (magneticSensor != null) {
            String.format(Locale.US, "Hall: %.0f µT • Tare %.0f µT", telemetry.totalFluxUt, tareUt)
        } else {
            String.format(Locale.US, "Ref Mode • Tare %.0f µT", tareUt)
        }

        tvGaussDiagnosis.text = telemetry.diagnosisText

        gaussScopeView.updateReadings(
            totalFluxUt = telemetry.totalFluxUt,
            netDeltaUt = telemetry.netDeltaUt,
            alarmThresholdUt = telemetry.alarmThresholdUt
        )
    }

    private fun saveCurrentScanToDb() {
        val telemetry = latestTelemetry ?: run {
            recalculateAndRender(false)
            latestTelemetry!!
        }
        val zone = etZoneName.text.toString().trim().ifEmpty { "Wall Scan" }
        dbHelper.insertScan(
            zoneName = zone,
            totalFluxUt = telemetry.totalFluxUt,
            netDeltaUt = telemetry.netDeltaUt,
            baselineTareUt = telemetry.baselineTareUt,
            alarmThresholdUt = telemetry.alarmThresholdUt,
            couplingMode = couplingMode
        )
        refreshScanLogList()
        Toast.makeText(this, "Saved scan for $zone", Toast.LENGTH_SHORT).show()
    }

    private fun refreshScanLogList() {
        llScanLogList.removeAllViews()
        val records = dbHelper.getAllScans()
        val padPx = dpToPx(8f)
        val marginPx = dpToPx(6f)

        for (rec in records) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card_elevated)
                setPadding(padPx, padPx, padPx, padPx)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = marginPx }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvTitle = TextView(this).apply {
                text = rec.zoneName
                setTextColor(getColor(R.color.text_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                paint.isFakeBoldText = true
            }

            val tvSub = TextView(this).apply {
                text = String.format(
                    Locale.US,
                    "%.1f µT (%.0f mG) • Delta +%.1f µT • Tare %.1f µT",
                    rec.totalFluxUt,
                    rec.totalFluxUt * 10.0,
                    rec.netDeltaUt,
                    rec.baselineTareUt
                )
                setTextColor(getColor(R.color.text_secondary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }

            textCol.addView(tvTitle)
            textCol.addView(tvSub)

            val btnLoad = Button(this).apply {
                text = "Load"
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(getColor(R.color.text_primary))
                setBackgroundResource(R.drawable.bg_btn_secondary)
                layoutParams = LinearLayout.LayoutParams(dpToPx(58f), dpToPx(48f)).apply {
                    rightMargin = dpToPx(4f)
                }
                setOnClickListener { v ->
                    triggerHaptic(v)
                    suppressTextWatcher = true
                    etRefFlux.setText(String.format(Locale.US, "%.1f", rec.totalFluxUt))
                    etBaselineTare.setText(String.format(Locale.US, "%.1f", rec.baselineTareUt))
                    etAlertThreshold.setText(String.format(Locale.US, "%.1f", rec.alarmThresholdUt))
                    etZoneName.setText(rec.zoneName)
                    suppressTextWatcher = false
                    couplingMode = rec.couplingMode
                    updateModeChips()
                    manualCalLockUntilMs = SystemClock.elapsedRealtime() + 8000L
                    recalculateAndRender(true)
                    updateTabSelection(0)
                }
            }

            val btnDel = Button(this).apply {
                text = "Del"
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(getColor(R.color.accent_danger))
                setBackgroundResource(R.drawable.bg_btn_secondary)
                layoutParams = LinearLayout.LayoutParams(dpToPx(52f), dpToPx(48f))
                setOnClickListener { v ->
                    triggerHaptic(v)
                    dbHelper.deleteScan(rec.id)
                    refreshScanLogList()
                }
            }

            row.addView(textCol)
            row.addView(btnLoad)
            row.addView(btnDel)
            llScanLogList.addView(row)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val btnThemeLight = dialogView.findViewById<Button>(R.id.btnThemeLight)
        val btnThemeDark = dialogView.findViewById<Button>(R.id.btnThemeDark)
        val btnThemeAuto = dialogView.findViewById<Button>(R.id.btnThemeAuto)
        val btnUnitUt = dialogView.findViewById<Button>(R.id.btnUnitUt)
        val btnUnitMg = dialogView.findViewById<Button>(R.id.btnUnitMg)
        val swHaptics = dialogView.findViewById<Switch>(R.id.swHaptics)
        val btnPrivacy = dialogView.findViewById<Button>(R.id.btnPrivacyPolicy)
        val btnReset = dialogView.findViewById<Button>(R.id.btnResetDefaults)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseSettings)

        val themeMode = prefs.getInt(KEY_THEME_MODE, 2)
        styleChipButton(btnThemeLight, themeMode == 1)
        styleChipButton(btnThemeDark, themeMode == 2)
        styleChipButton(btnThemeAuto, themeMode == 0)

        styleChipButton(btnUnitUt, !useMilligaussPrimary)
        styleChipButton(btnUnitMg, useMilligaussPrimary)
        swHaptics.isChecked = hapticsEnabled

        btnThemeLight.setOnClickListener {
            prefs.edit().putInt(KEY_THEME_MODE, 1).apply()
            dialog.dismiss()
            recreate()
        }
        btnThemeDark.setOnClickListener {
            prefs.edit().putInt(KEY_THEME_MODE, 2).apply()
            dialog.dismiss()
            recreate()
        }
        btnThemeAuto.setOnClickListener {
            prefs.edit().putInt(KEY_THEME_MODE, 0).apply()
            dialog.dismiss()
            recreate()
        }

        btnUnitUt.setOnClickListener {
            useMilligaussPrimary = false
            prefs.edit().putBoolean(KEY_PRIMARY_UNIT_MG, false).apply()
            styleChipButton(btnUnitUt, true)
            styleChipButton(btnUnitMg, false)
            recalculateAndRender(false)
        }
        btnUnitMg.setOnClickListener {
            useMilligaussPrimary = true
            prefs.edit().putBoolean(KEY_PRIMARY_UNIT_MG, true).apply()
            styleChipButton(btnUnitUt, false)
            styleChipButton(btnUnitMg, true)
            recalculateAndRender(false)
        }

        swHaptics.setOnCheckedChangeListener { _, isChecked ->
            hapticsEnabled = isChecked
            prefs.edit().putBoolean(KEY_HAPTICS, isChecked).apply()
        }

        btnPrivacy.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Offline Privacy Guarantee")
                .setMessage(
                    "GaussMeter is built in Pure Native Kotlin and operates 100% offline. " +
                        "Hardware 3-axis magnetometer readings and SQLite scan logs never leave your device. " +
                        "Zero network permissions, zero trackers, zero ads."
                )
                .setPositiveButton("Close", null)
                .show()
        }

        btnReset.setOnClickListener {
            suppressTextWatcher = true
            etRefFlux.setText("115.0")
            etBaselineTare.setText("48.0")
            etAlertThreshold.setText("85.0")
            etZoneName.setText("North Wall Stud")
            suppressTextWatcher = false
            couplingMode = GaussEngine.MODE_3D_VECTOR
            sessionPeakUt = 140.0
            saveDraftInputs()
            updateModeChips()
            recalculateAndRender(true)
            dialog.dismiss()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun saveDraftInputs() {
        val flux = etRefFlux.text.toString().trim()
        val tare = etBaselineTare.text.toString().trim()
        val alarm = etAlertThreshold.text.toString().trim()
        val zone = etZoneName.text.toString().trim()

        val editor = prefs.edit()
        if (flux.length <= 6 && !flux.startsWith("-")) editor.putString(KEY_DRAFT_FLUX, flux)
        if (tare.length <= 6 && !tare.startsWith("-")) editor.putString(KEY_DRAFT_TARE, tare)
        if (alarm.length <= 5 && !alarm.startsWith("-")) editor.putString(KEY_DRAFT_ALARM, alarm)
        if (zone.length <= 22) editor.putString(KEY_DRAFT_ZONE, zone)
        editor.apply()
    }

    private fun restoreDraftInputs() {
        suppressTextWatcher = true
        try {
            val flux = prefs.getString(KEY_DRAFT_FLUX, "115.0")
            val tare = prefs.getString(KEY_DRAFT_TARE, "48.0")
            val alarm = prefs.getString(KEY_DRAFT_ALARM, "85.0")
            val zone = prefs.getString(KEY_DRAFT_ZONE, "North Wall Stud")

            val safeFlux = GaussEngine.parseSafeDouble(flux, 115.0, 1.0, 2000.0)
            val safeTare = GaussEngine.parseSafeDouble(tare, 48.0, 0.0, 1000.0)
            val safeAlarm = GaussEngine.parseSafeDouble(alarm, 85.0, 10.0, 1000.0)

            etRefFlux.setText(String.format(Locale.US, "%.1f", safeFlux))
            etBaselineTare.setText(String.format(Locale.US, "%.1f", safeTare))
            etAlertThreshold.setText(String.format(Locale.US, "%.1f", safeAlarm))
            etZoneName.setText(if (zone.isNullOrEmpty() || zone.length > 22) "North Wall Stud" else zone)
        } finally {
            suppressTextWatcher = false
        }
    }

    override fun onResume() {
        super.onResume()
        magneticSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values == null || event.values.size < 3) return
        if (isHoldLocked) return
        val rx = event.values[0].toDouble()
        val ry = event.values[1].toDouble()
        val rz = event.values[2].toDouble()
        if (rx.isNaN() || ry.isNaN() || rz.isNaN()) return

        val now = SystemClock.elapsedRealtime()
        val mag = sqrt(rx * rx + ry * ry + rz * rz)
        val prevMag = sqrt(liveBxUt * liveBxUt + liveByUt * liveByUt + liveBzUt * liveBzUt)
        if (!hasHardwareReading || (abs(mag - prevMag) >= 8.0 && now - lastSensorUiUpdateMs >= 900L)) {
            hasHardwareReading = true
            lastSensorUiUpdateMs = now
            liveBxUt = GaussEngine.clamp(rx, -5000.0, 5000.0)
            liveByUt = GaussEngine.clamp(ry, -5000.0, 5000.0)
            liveBzUt = GaussEngine.clamp(rz, -5000.0, 5000.0)
            if (now >= manualCalLockUntilMs) {
                recalculateAndRender(false)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerHaptic(v: View?) {
        if (hapticsEnabled && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun hideKeyboard(v: View?) {
        if (v == null) return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).roundToInt()

    companion object {
        private const val PREFS_NAME = "gauss_meter_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PRIMARY_UNIT_MG = "primary_unit_mg"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_COUPLING_MODE = "coupling_mode"
        private const val KEY_DRAFT_FLUX = "draft_ref_flux"
        private const val KEY_DRAFT_TARE = "draft_baseline_tare"
        private const val KEY_DRAFT_ALARM = "draft_alarm_threshold"
        private const val KEY_DRAFT_ZONE = "draft_zone_name"
    }
}
