package com.kimrosil.gluewedge

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var dbHelper: DatabaseHelper

    // Views
    private lateinit var btnSettings: ImageButton
    private lateinit var btnThemeToggle: Button
    private lateinit var etLength: EditText
    private lateinit var etWidth: EditText
    private lateinit var etThickness: EditText
    private lateinit var btnUnitImperial: Button
    private lateinit var btnUnitMetric: Button
    private lateinit var spSpecies: Spinner
    private lateinit var spAdhesive: Spinner
    private lateinit var etTargetPsi: EditText
    private lateinit var btnCalculate: Button
    private lateinit var btnSaveSession: Button
    private lateinit var btnShare: Button
    private lateinit var btnCopySummary: Button

    // Telemetry Outputs
    private lateinit var tvTotalForce: TextView
    private lateinit var tvTotalForceUnit: TextView
    private lateinit var tvClampCount: TextView
    private lateinit var tvClampCountSub: TextView
    private lateinit var tvClampSpacing: TextView
    private lateinit var tvClampSpacingSub: TextView
    private lateinit var tvJointArea: TextView
    private lateinit var tvJointAreaSub: TextView

    // Canvas Visualizer
    private lateinit var visualizerView: PressureConeVisualizerView

    // Assembly Timer
    private lateinit var tvTimerDigits: TextView
    private lateinit var tvTimerStateBadge: TextView
    private lateinit var btnStartTimer: Button
    private lateinit var btnResetTimer: Button

    // History Deck
    private lateinit var llRecentList: LinearLayout
    private lateinit var llEmptyState: LinearLayout
    private lateinit var btnClearHistory: Button

    // State
    private var isMetric: Boolean = false
    private var calculatedTotalForceLbf: Int = 0
    private var calculatedClampCount: Int = 4
    private var calculatedSpacingInches: Double = 8.0
    private var currentAssemblyTimeSec: Int = 15 * 60 // 15 mins default
    private var initialAssemblyTimeSec: Int = 15 * 60
    private var isTimerRunning: Boolean = false
    private var countDownTimer: CountDownTimer? = null
    private var toneGenerator: ToneGenerator? = null

    // Species data map: Hardness & standard PSI baseline (Softwood: 100-150, Hardwood: 150-250)
    private val speciesProfiles = listOf(
        SpeciesItem("Oak (White / Red)", 220),
        SpeciesItem("Hard Maple", 240),
        SpeciesItem("Soft Maple", 180),
        SpeciesItem("Black Walnut", 175),
        SpeciesItem("Cherry", 160),
        SpeciesItem("Ash", 210),
        SpeciesItem("Pine / Softwoods", 120),
        SpeciesItem("Mahogany", 150)
    )

    // Adhesive profiles with standard shop open assembly time (in minutes)
    private val adhesiveProfiles = listOf(
        AdhesiveItem("PVA Type I (Standard Interior)", 10),
        AdhesiveItem("PVA Type II (Water-Resistant)", 15),
        AdhesiveItem("PVA Type III (Waterproof Outdoor)", 12),
        AdhesiveItem("Polyurethane Liquid", 20),
        AdhesiveItem("Liquid Hide Glue", 25)
    )

    data class SpeciesItem(val name: String, val psi: Int) {
        override fun toString(): String = "$name ($psi PSI)"
    }

    data class AdhesiveItem(val name: String, val openMinutes: Int) {
        override fun toString(): String = "$name (~$openMinutes m open)"
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("gluewedge_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("pref_theme_mode", 0) // 0=Auto, 1=Light, 2=Dark
        val config = Configuration(newBase.resources.configuration)
        when (mode) {
            1 -> config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            2 -> config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (_: Exception) {}

        setupWindowAppearance()
        initViews()
        setupSpinners()
        setupListeners()
        loadPersistedInputs()
        recomputeClampingMetrics()
        refreshHistoryList()
    }

    private fun setupWindowAppearance() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            window.insetsController?.setSystemBarsAppearance(
                if (isNight) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
    }

    private fun initViews() {
        btnSettings = findViewById(R.id.btnSettings)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        etLength = findViewById(R.id.etLength)
        etWidth = findViewById(R.id.etWidth)
        etThickness = findViewById(R.id.etThickness)
        btnUnitImperial = findViewById(R.id.btnUnitImperial)
        btnUnitMetric = findViewById(R.id.btnUnitMetric)
        spSpecies = findViewById(R.id.spSpecies)
        spAdhesive = findViewById(R.id.spAdhesive)
        etTargetPsi = findViewById(R.id.etTargetPsi)
        btnCalculate = findViewById(R.id.btnCalculate)
        btnSaveSession = findViewById(R.id.btnSaveSession)
        btnShare = findViewById(R.id.btnShare)
        btnCopySummary = findViewById(R.id.btnCopySummary)

        tvTotalForce = findViewById(R.id.tvTotalForce)
        tvTotalForceUnit = findViewById(R.id.tvTotalForceUnit)
        tvClampCount = findViewById(R.id.tvClampCount)
        tvClampCountSub = findViewById(R.id.tvClampCountSub)
        tvClampSpacing = findViewById(R.id.tvClampSpacing)
        tvClampSpacingSub = findViewById(R.id.tvClampSpacingSub)
        tvJointArea = findViewById(R.id.tvJointArea)
        tvJointAreaSub = findViewById(R.id.tvJointAreaSub)

        visualizerView = findViewById(R.id.visualizerView)

        tvTimerDigits = findViewById(R.id.tvTimerDigits)
        tvTimerStateBadge = findViewById(R.id.tvTimerStateBadge)
        btnStartTimer = findViewById(R.id.btnStartTimer)
        btnResetTimer = findViewById(R.id.btnResetTimer)

        llRecentList = findViewById(R.id.llRecentList)
        llEmptyState = findViewById(R.id.llEmptyState)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        val prefs = getSharedPreferences("gluewedge_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("pref_theme_mode", 0)
        btnThemeToggle.text = when (mode) {
            1 -> "Light Mode"
            2 -> "Dark Mode"
            else -> "Auto Theme"
        }
    }

    private fun setupSpinners() {
        val speciesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, speciesProfiles)
        spSpecies.adapter = speciesAdapter
        spSpecies.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, id: Long) {
                val selected = speciesProfiles[position]
                etTargetPsi.setText(selected.psi.toString())
                recomputeClampingMetrics()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val adhesiveAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, adhesiveProfiles)
        spAdhesive.adapter = adhesiveAdapter
        spAdhesive.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, id: Long) {
                val selected = adhesiveProfiles[position]
                if (!isTimerRunning) {
                    initialAssemblyTimeSec = selected.openMinutes * 60
                    currentAssemblyTimeSec = initialAssemblyTimeSec
                    updateTimerDisplay()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        btnSettings.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showSettingsAboutDialog()
        }

        btnThemeToggle.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            cycleThemeMode()
        }

        btnUnitImperial.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            setUnitSystem(false)
        }

        btnUnitMetric.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            setUnitSystem(true)
        }

        val editorWatcher = TextView.OnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideSoftKeyboard(v)
                recomputeClampingMetrics()
                true
            } else false
        }

        etLength.setOnEditorActionListener(editorWatcher)
        etWidth.setOnEditorActionListener(editorWatcher)
        etThickness.setOnEditorActionListener(editorWatcher)
        etTargetPsi.setOnEditorActionListener(editorWatcher)

        btnCalculate.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            hideSoftKeyboard(v)
            recomputeClampingMetrics()
            Toast.makeText(this, "Pressure and spacing updated", Toast.LENGTH_SHORT).show()
        }

        btnSaveSession.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            saveCurrentSession()
        }

        btnShare.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            shareSummary()
        }

        btnCopySummary.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            copySummaryToClipboard()
        }

        btnStartTimer.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            toggleTimer()
        }

        btnResetTimer.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            resetTimer()
        }

        btnClearHistory.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            confirmClearHistory()
        }
    }

    private fun setUnitSystem(metric: Boolean) {
        if (isMetric == metric) return
        isMetric = metric
        val lenVal = etLength.text.toString().toDoubleOrNull() ?: 36.0
        val widVal = etWidth.text.toString().toDoubleOrNull() ?: 18.0
        val thkVal = etThickness.text.toString().toDoubleOrNull() ?: 0.75

        if (isMetric) {
            btnUnitMetric.setBackgroundResource(R.drawable.chip_active)
            btnUnitImperial.setBackgroundResource(R.drawable.chip_inactive)
            etLength.hint = "Length (cm)"
            etWidth.hint = "Width (cm)"
            etThickness.hint = "Thickness (mm)"
            etLength.setText(String.format(Locale.US, "%.1f", lenVal * 2.54))
            etWidth.setText(String.format(Locale.US, "%.1f", widVal * 2.54))
            etThickness.setText(String.format(Locale.US, "%.1f", thkVal * 25.4))
        } else {
            btnUnitImperial.setBackgroundResource(R.drawable.chip_active)
            btnUnitMetric.setBackgroundResource(R.drawable.chip_inactive)
            etLength.hint = "Length (in)"
            etWidth.hint = "Width (in)"
            etThickness.hint = "Thickness (in)"
            etLength.setText(String.format(Locale.US, "%.2f", lenVal / 2.54))
            etWidth.setText(String.format(Locale.US, "%.2f", widVal / 2.54))
            etThickness.setText(String.format(Locale.US, "%.3f", thkVal / 25.4))
        }
        recomputeClampingMetrics()
    }

    private fun recomputeClampingMetrics() {
        val rawL = etLength.text.toString().toDoubleOrNull() ?: if (isMetric) 91.4 else 36.0
        val rawW = etWidth.text.toString().toDoubleOrNull() ?: if (isMetric) 45.7 else 18.0
        val rawT = etThickness.text.toString().toDoubleOrNull() ?: if (isMetric) 19.0 else 0.75
        val psi = etTargetPsi.text.toString().toIntOrNull() ?: 175

        // Normalize to inches for physical woodworking equations
        val lengthInches = if (isMetric) rawL / 2.54 else rawL
        val widthInches = if (isMetric) rawW / 2.54 else rawW
        val thicknessInches = if (isMetric) rawT / 25.4 else rawT

        val edgeAreaSqInches = lengthInches * thicknessInches
        calculatedTotalForceLbf = (edgeAreaSqInches * psi).roundToInt()

        // Woodworking standard parallel bar clamp rating ~ 600 - 900 lbf dependable
        val perClampForceLbf = 700.0
        val countByForce = ceil(calculatedTotalForceLbf / perClampForceLbf).toInt()

        // 45-degree load distribution cone rule: clamp spacing <= 2 * thickness * tan(45) + clamp face (~2")
        // In real panel fabrication, max on-center spacing is governed by panel thickness & buckling prevention
        val maxSpacingByCone = (thicknessInches * 6.0).coerceIn(4.0, 14.0)
        val countBySpacing = ceil(lengthInches / maxSpacingByCone).toInt() + 1
        calculatedClampCount = maxOf(countByForce, countBySpacing, 2)

        calculatedSpacingInches = if (calculatedClampCount > 1) {
            lengthInches / (calculatedClampCount - 1)
        } else {
            lengthInches
        }

        // Render Outputs
        if (isMetric) {
            val totalForceKn = calculatedTotalForceLbf * 0.00444822
            tvTotalForce.text = String.format(Locale.US, "%.1f kN", totalForceKn)
            tvTotalForceUnit = findViewById(R.id.tvTotalForceUnit)
            tvTotalForceUnit.text = "$calculatedTotalForceLbf lbf equiv • $psi PSI"

            val spacingCm = calculatedSpacingInches * 2.54
            tvClampSpacing.text = String.format(Locale.US, "%.1f cm", spacingCm)
            tvClampSpacingSub.text = String.format(Locale.US, "%.1f in on-center", calculatedSpacingInches)

            val edgeAreaSqCm = edgeAreaSqInches * 6.4516
            tvJointArea.text = String.format(Locale.US, "%.1f cm²", edgeAreaSqCm)
            tvJointAreaSub.text = String.format(Locale.US, "%.2f sq in", edgeAreaSqInches)
        } else {
            tvTotalForce.text = String.format(Locale.US, "%,d lbf", calculatedTotalForceLbf)
            val totalForceKn = calculatedTotalForceLbf * 0.00444822
            tvTotalForceUnit.text = String.format(Locale.US, "%.1f kN equivalent • %d PSI", totalForceKn, psi)

            tvClampSpacing.text = String.format(Locale.US, "%.1f in", calculatedSpacingInches)
            val spacingCm = calculatedSpacingInches * 2.54
            tvClampSpacingSub.text = String.format(Locale.US, "%.1f cm on-center", spacingCm)

            tvJointArea.text = String.format(Locale.US, "%.2f sq in", edgeAreaSqInches)
            val edgeAreaSqCm = edgeAreaSqInches * 6.4516
            tvJointAreaSub.text = String.format(Locale.US, "%.1f cm²", edgeAreaSqCm)
        }

        val topClamps = ceil(calculatedClampCount / 2.0).toInt()
        val botClamps = calculatedClampCount - topClamps
        tvClampCount.text = "$calculatedClampCount Clamps"
        tvClampCountSub.text = "$topClamps Top / $botClamps Underneath (Anti-Bow)"

        // Update 2D Canvas Visualizer
        visualizerView.updateMetrics(calculatedClampCount, lengthInches, thicknessInches)
    }

    private fun toggleTimer() {
        if (isTimerRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (currentAssemblyTimeSec <= 0) {
            currentAssemblyTimeSec = initialAssemblyTimeSec
        }
        isTimerRunning = true
        btnStartTimer.text = "Pause Timer"
        tvTimerStateBadge.text = "ASSEMBLY IN PROGRESS"
        tvTimerStateBadge.setTextColor(0xFF0EA5E9.toInt())

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer((currentAssemblyTimeSec * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                currentAssemblyTimeSec = (millisUntilFinished / 1000).toInt()
                updateTimerDisplay()

                // Warning sound/haptic at 2 minutes
                if (currentAssemblyTimeSec == 120) {
                    try {
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                    } catch (_: Exception) {}
                    tvTimerStateBadge.text = "WARNING: 2M TO SKIN OVER"
                    tvTimerStateBadge.setTextColor(0xFFF59E0B.toInt())
                }
            }

            override fun onFinish() {
                isTimerRunning = false
                currentAssemblyTimeSec = 0
                updateTimerDisplay()
                btnStartTimer.text = "Start Timer"
                tvTimerStateBadge.text = "EXPIRED - GLUE HAS SKINNED"
                tvTimerStateBadge.setTextColor(0xFFEF4444.toInt())
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 800)
                } catch (_: Exception) {}
                Toast.makeText(this@MainActivity, "Open Assembly Time Expired!", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        btnStartTimer.text = "Resume Timer"
        tvTimerStateBadge.text = "PAUSED"
        tvTimerStateBadge.setTextColor(0xFF94A3B8.toInt())
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        currentAssemblyTimeSec = initialAssemblyTimeSec
        updateTimerDisplay()
        btnStartTimer.text = "Start Timer"
        tvTimerStateBadge.text = "READY TO GLUE"
        tvTimerStateBadge.setTextColor(0xFF38BDF8.toInt())
    }

    private fun updateTimerDisplay() {
        val minutes = currentAssemblyTimeSec / 60
        val seconds = currentAssemblyTimeSec % 60
        tvTimerDigits.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun saveCurrentSession() {
        val selectedSpecies = spSpecies.selectedItem?.toString() ?: "Hardwood"
        val rawL = etLength.text.toString().toDoubleOrNull() ?: 36.0
        val rawW = etWidth.text.toString().toDoubleOrNull() ?: 18.0
        val rawT = etThickness.text.toString().toDoubleOrNull() ?: 0.75
        val psi = etTargetPsi.text.toString().toIntOrNull() ?: 175

        val lengthInches = if (isMetric) rawL / 2.54 else rawL
        val widthInches = if (isMetric) rawW / 2.54 else rawW
        val thicknessInches = if (isMetric) rawT / 25.4 else rawT

        val title = "${selectedSpecies.substringBefore(" ")} Panel (${lengthInches.roundToInt()}x${widthInches.roundToInt()}\")"
        val session = PanelSession(
            title = title,
            lengthInches = lengthInches,
            widthInches = widthInches,
            thicknessInches = thicknessInches,
            speciesName = selectedSpecies,
            targetPsi = psi,
            clampCount = calculatedClampCount,
            clampSpacingInches = calculatedSpacingInches,
            totalForceLbf = calculatedTotalForceLbf,
            openTimeSec = initialAssemblyTimeSec,
            timestamp = System.currentTimeMillis()
        )

        dbHelper.insertSession(session)
        Toast.makeText(this, "Session saved to offline history", Toast.LENGTH_SHORT).show()
        refreshHistoryList()
    }

    private fun refreshHistoryList() {
        llRecentList.removeAllViews()
        val sessions = dbHelper.getRecentSessions(8)
        if (sessions.isEmpty()) {
            llEmptyState.visibility = View.VISIBLE
            llRecentList.visibility = View.GONE
        } else {
            llEmptyState.visibility = View.GONE
            llRecentList.visibility = View.VISIBLE
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
            for (item in sessions) {
                val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_2, llRecentList, false)
                val text1 = itemView.findViewById<TextView>(android.R.id.text1)
                val text2 = itemView.findViewById<TextView>(android.R.id.text2)

                text1.setTextColor(getColor(android.R.color.white).let { 0xFF0284C7.toInt() })
                text1.textSize = 15f
                text1.text = item.title

                val dateStr = sdf.format(Date(item.timestamp))
                text2.setTextColor(0xFF94A3B8.toInt())
                text2.textSize = 12f
                text2.text = "${item.clampCount} Clamps • ${item.totalForceLbf} lbf • ${String.format(Locale.US, "%.1f\"", item.clampSpacingInches)} o.c. • $dateStr"

                itemView.setPadding(12, 16, 12, 16)
                itemView.setOnClickListener {
                    restoreSession(item)
                }
                llRecentList.addView(itemView)
            }
        }
    }

    private fun restoreSession(item: PanelSession) {
        if (isMetric) {
            etLength.setText(String.format(Locale.US, "%.1f", item.lengthInches * 2.54))
            etWidth.setText(String.format(Locale.US, "%.1f", item.widthInches * 2.54))
            etThickness.setText(String.format(Locale.US, "%.1f", item.thicknessInches * 25.4))
        } else {
            etLength.setText(String.format(Locale.US, "%.2f", item.lengthInches))
            etWidth.setText(String.format(Locale.US, "%.2f", item.widthInches))
            etThickness.setText(String.format(Locale.US, "%.3f", item.thicknessInches))
        }
        etTargetPsi.setText(item.targetPsi.toString())
        recomputeClampingMetrics()
        Toast.makeText(this, "Loaded: ${item.title}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Clear Saved Assemblies")
            .setMessage("Do you want to delete all offline saved panel presets?")
            .setPositiveButton("Clear All") { _, _ ->
                dbHelper.deleteAllSessions()
                refreshHistoryList()
                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateMonospaceSummary(): String {
        val species = spSpecies.selectedItem?.toString() ?: "Hardwood"
        val adhesive = spAdhesive.selectedItem?.toString() ?: "PVA Type II"
        val l = etLength.text.toString()
        val w = etWidth.text.toString()
        val t = etThickness.text.toString()
        val unit = if (isMetric) "Metric (cm/mm)" else "Imperial (inches)"

        return """
            --- GlueWedge Clamping Spec ---
            Species: $species
            Adhesive: $adhesive
            Panel Dimensions: $l x $w x $t ($unit)
            Total Force Required: $calculatedTotalForceLbf lbf (${String.format(Locale.US, "%.1f", calculatedTotalForceLbf * 0.00444822)} kN)
            Clamps Required: $calculatedClampCount (Alternating top/bottom)
            Max On-Center Spacing: ${String.format(Locale.US, "%.1f in (%.1f cm)", calculatedSpacingInches, calculatedSpacingInches * 2.54)}
            Open Assembly Time: ${initialAssemblyTimeSec / 60} minutes
            
            [Calculated via GlueWedge • https://apps.kimrosil.com/gluewedge]
        """.trimIndent()
    }

    private fun shareSummary() {
        val summary = generateMonospaceSummary()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "GlueWedge Clamping Layout")
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        startActivity(Intent.createChooser(intent, "Share Clamping Layout"))
    }

    private fun copySummaryToClipboard() {
        val summary = generateMonospaceSummary()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("GlueWedge Spec", summary)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Clamping spec copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showSettingsAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null, false)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val btnDialogTheme = dialogView.findViewById<Button>(R.id.btnDialogTheme)
        val btnExploreTools = dialogView.findViewById<Button>(R.id.btnExploreTools)
        val btnResetDefaults = dialogView.findViewById<Button>(R.id.btnResetDefaults)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseSettings)

        val prefs = getSharedPreferences("gluewedge_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("pref_theme_mode", 0)
        btnDialogTheme.text = when (mode) {
            1 -> "Theme: Light Mode"
            2 -> "Theme: Dark Mode"
            else -> "Theme: System Auto"
        }

        btnDialogTheme.setOnClickListener {
            cycleThemeMode()
            dialog.dismiss()
        }

        btnExploreTools.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com/"))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Visit https://apps.kimrosil.com/", Toast.LENGTH_SHORT).show()
            }
        }

        btnResetDefaults.setOnClickListener {
            resetToDefaults()
            dialog.dismiss()
            Toast.makeText(this, "Reset to standard 36x18x0.75\" panel defaults", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun resetToDefaults() {
        isMetric = false
        btnUnitImperial.setBackgroundResource(R.drawable.chip_active)
        btnUnitMetric.setBackgroundResource(R.drawable.chip_inactive)
        etLength.setText("36.0")
        etWidth.setText("18.0")
        etThickness.setText("0.75")
        spSpecies.setSelection(0)
        spAdhesive.setSelection(1)
        recomputeClampingMetrics()
        resetTimer()
    }

    private fun cycleThemeMode() {
        val prefs = getSharedPreferences("gluewedge_prefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("pref_theme_mode", 0)
        val next = (current + 1) % 3
        prefs.edit().putInt("pref_theme_mode", next).apply()
        recreate()
    }

    private fun hideSoftKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun loadPersistedInputs() {
        val prefs = getSharedPreferences("gluewedge_prefs", Context.MODE_PRIVATE)
        etLength.setText(prefs.getString("last_len", "36.0"))
        etWidth.setText(prefs.getString("last_wid", "18.0"))
        etThickness.setText(prefs.getString("last_thk", "0.75"))
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("gluewedge_prefs", Context.MODE_PRIVATE).edit()
            .putString("last_len", etLength.text.toString())
            .putString("last_wid", etWidth.text.toString())
            .putString("last_thk", etThickness.text.toString())
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    recomputeClampingMetrics()
                    return true
                }
                KeyEvent.KEYCODE_SPACE -> {
                    toggleTimer()
                    return true
                }
                KeyEvent.KEYCODE_ESCAPE -> {
                    resetToDefaults()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}