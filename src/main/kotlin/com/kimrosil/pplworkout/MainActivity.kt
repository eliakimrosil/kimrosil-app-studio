package com.kimrosil.pplworkout

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dbHelper: DatabaseHelper

    // Top Bar Views
    private lateinit var tvAppTitle: TextView
    private lateinit var tvAppSubtitle: TextView
    private lateinit var btnHistory: ImageButton
    private lateinit var btnSettings: ImageButton

    // Day Chips
    private lateinit var btnDayPush: Button
    private lateinit var btnDayPull: Button
    private lateinit var btnDayLegs: Button

    // Progress Telemetry
    private lateinit var tvProgressStatus: TextView
    private lateinit var tvVolumeStats: TextView
    private lateinit var pbWorkoutProgress: ProgressBar

    // Compact Glancable Live Rest Bar
    private lateinit var cardRestTimer: LinearLayout
    private lateinit var ivTimerIcon: ImageView
    private lateinit var tvTimerCountdown: TextView
    private lateinit var tvTimerStatus: TextView
    private lateinit var btnRestToggle: ImageButton
    private lateinit var btnRestPlus30: Button
    private lateinit var btnRestSkip: Button
    private lateinit var pbRestProgress: ProgressBar

    // Personal Record (PR) Celebration Banner
    private lateinit var llPrBanner: LinearLayout
    private lateinit var tvPrText: TextView

    // Active Exercise Deck
    private lateinit var btnPrevExercise: ImageButton
    private lateinit var tvExerciseName: TextView
    private lateinit var tvTargetMuscles: TextView
    private lateinit var btnEditExercise: ImageButton
    private lateinit var btnNextExercise: ImageButton
    private lateinit var tvSetCounter: TextView
    private lateinit var tvPreviousBest: TextView
    private lateinit var tvWeightLabel: TextView
    private lateinit var btnWeightMinus: Button
    private lateinit var etWeight: EditText
    private lateinit var btnWeightPlus: Button
    private lateinit var btnRepsMinus: Button
    private lateinit var etReps: EditText
    private lateinit var btnRepsPlus: Button
    private lateinit var tvOneRepMax: TextView
    private lateinit var tvPlateMath: TextView
    private lateinit var tvIntensityPct: TextView
    private lateinit var btnLogSet: Button

    // Routine Overview
    private lateinit var btnManageRoutine: Button
    private lateinit var btnAddExercise: Button
    private lateinit var llRoutineList: LinearLayout

    // State Variables
    private var currentDay = "PUSH"
    private var currentExerciseIndex = 0
    private var sessionId: Long = -1
    private var currentUnit = "KG"
    private var themeMode = 2 // 0=Auto, 1=Light, 2=Dark (Default: 2=Dark)
    private var defaultRestSeconds: Long = 90L
    private var totalRestSeconds: Long = 90L
    private var restEndTimeMillis: Long = 0L
    private var isSoundEnabled: Boolean = true
    private var isWorkoutCompleteCelebrated: Boolean = false

    // Dynamic Routine Exercises loaded from SQLite
    private var currentExercises = mutableListOf<Exercise>()

    // Completed sets counter per exercise index
    private val completedSets = mutableMapOf<Int, Int>()

    // Rest Timer fields
    private var timerExecutor: ScheduledExecutorService? = null
    private var restSecondsRemaining: Long = 90
    private var isTimerRunning = false

    override fun attachBaseContext(newBase: Context) {
        val p = newBase.getSharedPreferences("ppl-workout_prefs", MODE_PRIVATE)
        val mode = p.getInt("pref_theme_mode", 2)
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

        prefs = getSharedPreferences("ppl-workout_prefs", MODE_PRIVATE)
        dbHelper = DatabaseHelper(this)
        themeMode = prefs.getInt("pref_theme_mode", 2)
        currentUnit = prefs.getString("pref_unit", "KG") ?: "KG"
        currentDay = prefs.getString("pref_last_day", "PUSH") ?: "PUSH"
        defaultRestSeconds = prefs.getLong("pref_default_rest", 90L)
        isSoundEnabled = prefs.getBoolean("pref_sound_enabled", true)

        bindViews()
        setupListeners()
        applyStatusBarAppearance()
        startNewOrResumeSession()
    }

    override fun onResume() {
        super.onResume()
        // Accurate elapsed time recovery when returning from background or locked screen
        if (isTimerRunning && restEndTimeMillis > 0) {
            val remaining = (restEndTimeMillis - SystemClock.elapsedRealtime()) / 1000L
            if (remaining > 0) {
                restSecondsRemaining = remaining
                updateTimerDisplay(remaining)
                pbRestProgress.progress = remaining.toInt().coerceIn(0, totalRestSeconds.toInt())
            } else {
                onRestTimerFinish()
            }
        }
    }

    private fun bindViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle)
        btnHistory = findViewById(R.id.btnHistory)
        btnSettings = findViewById(R.id.btnSettings)

        btnDayPush = findViewById(R.id.btnDayPush)
        btnDayPull = findViewById(R.id.btnDayPull)
        btnDayLegs = findViewById(R.id.btnDayLegs)

        tvProgressStatus = findViewById(R.id.tvProgressStatus)
        tvVolumeStats = findViewById(R.id.tvVolumeStats)
        pbWorkoutProgress = findViewById(R.id.pbWorkoutProgress)

        cardRestTimer = findViewById(R.id.cardRestTimer)
        ivTimerIcon = findViewById(R.id.ivTimerIcon)
        tvTimerCountdown = findViewById(R.id.tvTimerCountdown)
        tvTimerStatus = findViewById(R.id.tvTimerStatus)
        btnRestToggle = findViewById(R.id.btnRestToggle)
        btnRestPlus30 = findViewById(R.id.btnRestPlus30)
        btnRestSkip = findViewById(R.id.btnRestSkip)
        pbRestProgress = findViewById(R.id.pbRestProgress)

        llPrBanner = findViewById(R.id.llPrBanner)
        tvPrText = findViewById(R.id.tvPrText)

        btnPrevExercise = findViewById(R.id.btnPrevExercise)
        tvExerciseName = findViewById(R.id.tvExerciseName)
        tvTargetMuscles = findViewById(R.id.tvTargetMuscles)
        btnEditExercise = findViewById(R.id.btnEditExercise)
        btnNextExercise = findViewById(R.id.btnNextExercise)
        tvSetCounter = findViewById(R.id.tvSetCounter)
        tvPreviousBest = findViewById(R.id.tvPreviousBest)

        tvWeightLabel = findViewById(R.id.tvWeightLabel)
        btnWeightMinus = findViewById(R.id.btnWeightMinus)
        etWeight = findViewById(R.id.etWeight)
        btnWeightPlus = findViewById(R.id.btnWeightPlus)
        btnRepsMinus = findViewById(R.id.btnRepsMinus)
        etReps = findViewById(R.id.etReps)
        btnRepsPlus = findViewById(R.id.btnRepsPlus)

        tvOneRepMax = findViewById(R.id.tvOneRepMax)
        tvPlateMath = findViewById(R.id.tvPlateMath)
        tvIntensityPct = findViewById(R.id.tvIntensityPct)

        btnLogSet = findViewById(R.id.btnLogSet)

        btnManageRoutine = findViewById(R.id.btnManageRoutine)
        btnAddExercise = findViewById(R.id.btnAddExercise)
        llRoutineList = findViewById(R.id.llRoutineList)

        tvWeightLabel.text = "WEIGHT ($currentUnit)"
        updateSubtitle()
    }

    private fun updateSubtitle() {
        val splitDesc = when (currentDay) {
            "PUSH" -> "PUSH DAY • CHEST & TRICEPS"
            "PULL" -> "PULL DAY • BACK & BICEPS"
            else -> "LEGS DAY • QUADS & POSTERIOR"
        }
        tvAppSubtitle.text = splitDesc
    }

    private fun setupListeners() {
        // Workout History Modal
        btnHistory.setOnClickListener {
            showHistoryDialog()
        }

        // Settings & About Modal Launcher
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Live text change listeners for real-time 1RM & plate math
        val telemetryWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePowerliftingTelemetry()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        etWeight.addTextChangedListener(telemetryWatcher)
        etReps.addTextChangedListener(telemetryWatcher)

        // PPL Day Switchers
        btnDayPush.setOnClickListener { switchDay("PUSH") }
        btnDayPull.setOnClickListener { switchDay("PULL") }
        btnDayLegs.setOnClickListener { switchDay("LEGS") }

        // Exercise Navigation with smooth directional slide animations
        btnPrevExercise.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (currentExerciseIndex > 0) {
                currentExerciseIndex--
                animateExerciseTransition(isForward = false)
            }
        }

        btnNextExercise.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (currentExerciseIndex < currentExercises.size - 1) {
                currentExerciseIndex++
                animateExerciseTransition(isForward = true)
            }
        }

        // Quick Edit Current Exercise
        btnEditExercise.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val ex = currentExercises.getOrNull(currentExerciseIndex) ?: return@setOnClickListener
            showEditExerciseDialog(ex)
        }

        // Routine Management & Add Exercise Launchers
        btnManageRoutine.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showManageRoutineDialog()
        }

        btnAddExercise.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showAddExerciseDialog()
        }

        // Steppers with robust boundary defenses
        btnWeightMinus.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val w = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val step = if (currentUnit == "LBS") 5.0 else 2.5
            val newW = if (w.isNaN() || w.isInfinite()) 0.0 else (w - step).coerceIn(0.0, 2000.0)
            etWeight.setText(String.format(Locale.US, "%.1f", newW))
        }

        btnWeightPlus.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val w = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val step = if (currentUnit == "LBS") 5.0 else 2.5
            val newW = if (w.isNaN() || w.isInfinite()) 0.0 else (w + step).coerceIn(0.0, 2000.0)
            etWeight.setText(String.format(Locale.US, "%.1f", newW))
        }

        btnRepsMinus.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val r = etReps.text.toString().toIntOrNull() ?: 1
            val newR = (r - 1).coerceIn(1, 500)
            etReps.setText(newR.toString())
        }

        btnRepsPlus.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val r = etReps.text.toString().toIntOrNull() ?: 1
            val newR = (r + 1).coerceIn(1, 500)
            etReps.setText(newR.toString())
        }

        // Log Set Action
        btnLogSet.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            handleLogSet()
        }

        // Rest Timer Play/Pause Toggle
        btnRestToggle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isTimerRunning) {
                pauseRestTimer()
            } else {
                resumeRestTimer()
            }
        }

        // Rest Timer Quick Actions
        btnRestPlus30.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val newTime = if (isTimerRunning && restSecondsRemaining > 0) restSecondsRemaining + 30 else 30L
            totalRestSeconds = maxOf(totalRestSeconds, newTime)
            startRestTimer(newTime)
        }

        btnRestSkip.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            cancelRestTimer()
            tvTimerStatus.text = "Rest skipped • Ready for next set!"
            tvTimerCountdown.text = "00:00"
            tvTimerCountdown.setTextColor(getColor(R.color.m3_primary))
            pbRestProgress.progress = 0
            btnRestToggle.setImageResource(R.drawable.ic_play)
        }
    }

    private fun animateExerciseTransition(isForward: Boolean) {
        val offset = if (isForward) 30f else -30f
        tvExerciseName.animate()
            .translationX(-offset)
            .alpha(0f)
            .setDuration(90)
            .withEndAction {
                refreshUiForCurrentExercise()
                tvExerciseName.translationX = offset
                tvExerciseName.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(120)
                    .start()
            }
            .start()
    }

    private fun configureDialogWindow(dialog: Dialog, maxPercentHeight: Float = 0.78f) {
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            val dm = resources.displayMetrics
            val screenWidth = dm.widthPixels
            val screenHeight = dm.heightPixels
            val maxDialogWidth = (460 * dm.density).toInt()
            val targetWidth = minOf((screenWidth * 0.92).toInt(), maxDialogWidth)

            window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
        }

        dialog.setOnShowListener {
            val dm = resources.displayMetrics
            val screenWidth = dm.widthPixels
            val screenHeight = dm.heightPixels
            val maxDialogWidth = (460 * dm.density).toInt()
            val targetWidth = minOf((screenWidth * 0.92).toInt(), maxDialogWidth)
            val maxHeight = (screenHeight * maxPercentHeight).toInt()

            val decor = dialog.window?.decorView
            val contentH = decor?.height ?: 0
            if (contentH > maxHeight) {
                dialog.window?.setLayout(targetWidth, maxHeight)
            } else {
                dialog.window?.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }
    }

    private fun showSettingsDialog() {
        if (isTimerRunning) pauseRestTimer()
        btnSettings.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_settings)

        val btnCloseSettingsIcon: ImageButton? = dialog.findViewById(R.id.btnCloseSettingsIcon)
        btnCloseSettingsIcon?.setOnClickListener {
            dialog.dismiss()
        }

        val tvAboutAppVersion: TextView? = dialog.findViewById(R.id.tvAboutAppVersion)
        val vn = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "2.3.5" }
        tvAboutAppVersion?.text = "PPL Workout v$vn"

        val btnThemeLight: Button = dialog.findViewById(R.id.btnThemeLight)
        val btnThemeDark: Button = dialog.findViewById(R.id.btnThemeDark)
        val btnThemeAuto: Button = dialog.findViewById(R.id.btnThemeAuto)

        val btnDefault60: Button = dialog.findViewById(R.id.btnDefault60)
        val btnDefault90: Button = dialog.findViewById(R.id.btnDefault90)
        val btnDefault120: Button = dialog.findViewById(R.id.btnDefault120)

        val btnUnitKg: Button = dialog.findViewById(R.id.btnUnitKg)
        val btnUnitLbs: Button = dialog.findViewById(R.id.btnUnitLbs)

        val btnResetSessionDialog: Button = dialog.findViewById(R.id.btnResetSessionDialog)
        val btnCloseDialog: Button = dialog.findViewById(R.id.btnCloseDialog)

        // Theme Mode Highlighting
        fun updateThemeButtons(mode: Int) {
            btnThemeLight.setBackgroundResource(if (mode == 1) R.drawable.chip_active else R.drawable.chip_inactive)
            btnThemeLight.setTextColor(getColor(if (mode == 1) R.color.m3_on_primary else R.color.m3_on_surface))

            btnThemeDark.setBackgroundResource(if (mode == 2) R.drawable.chip_active else R.drawable.chip_inactive)
            btnThemeDark.setTextColor(getColor(if (mode == 2) R.color.m3_on_primary else R.color.m3_on_surface))

            btnThemeAuto.setBackgroundResource(if (mode == 0) R.drawable.chip_active else R.drawable.chip_inactive)
            btnThemeAuto.setTextColor(getColor(if (mode == 0) R.color.m3_on_primary else R.color.m3_on_surface))
        }
        updateThemeButtons(themeMode)

        btnThemeLight.setOnClickListener {
            themeMode = 1
            prefs.edit().putInt("pref_theme_mode", 1).apply()
            dialog.dismiss()
            recreate()
        }
        btnThemeDark.setOnClickListener {
            themeMode = 2
            prefs.edit().putInt("pref_theme_mode", 2).apply()
            dialog.dismiss()
            recreate()
        }
        btnThemeAuto.setOnClickListener {
            themeMode = 0
            prefs.edit().putInt("pref_theme_mode", 0).apply()
            dialog.dismiss()
            recreate()
        }

        // Rest Interval Highlighting
        fun updateRestButtons(sec: Long) {
            btnDefault60.setBackgroundResource(if (sec == 60L) R.drawable.chip_active else R.drawable.chip_inactive)
            btnDefault60.setTextColor(getColor(if (sec == 60L) R.color.m3_on_primary else R.color.m3_on_surface))

            btnDefault90.setBackgroundResource(if (sec == 90L) R.drawable.chip_active else R.drawable.chip_inactive)
            btnDefault90.setTextColor(getColor(if (sec == 90L) R.color.m3_on_primary else R.color.m3_on_surface))

            btnDefault120.setBackgroundResource(if (sec == 120L) R.drawable.chip_active else R.drawable.chip_inactive)
            btnDefault120.setTextColor(getColor(if (sec == 120L) R.color.m3_on_primary else R.color.m3_on_surface))
        }
        updateRestButtons(defaultRestSeconds)

        btnDefault60.setOnClickListener {
            defaultRestSeconds = 60
            prefs.edit().putLong("pref_default_rest", 60).apply()
            updateRestButtons(60)
        }
        btnDefault90.setOnClickListener {
            defaultRestSeconds = 90
            prefs.edit().putLong("pref_default_rest", 90).apply()
            updateRestButtons(90)
        }
        btnDefault120.setOnClickListener {
            defaultRestSeconds = 120
            prefs.edit().putLong("pref_default_rest", 120).apply()
            updateRestButtons(120)
        }

        // Unit Preference Highlighting
        fun updateUnitButtons(unit: String) {
            btnUnitKg.setBackgroundResource(if (unit == "KG") R.drawable.chip_active else R.drawable.chip_inactive)
            btnUnitKg.setTextColor(getColor(if (unit == "KG") R.color.m3_on_primary else R.color.m3_on_surface))

            btnUnitLbs.setBackgroundResource(if (unit == "LBS") R.drawable.chip_active else R.drawable.chip_inactive)
            btnUnitLbs.setTextColor(getColor(if (unit == "LBS") R.color.m3_on_primary else R.color.m3_on_surface))
        }
        updateUnitButtons(currentUnit)

        btnUnitKg.setOnClickListener {
            if (currentUnit != "KG") {
                convertWeightDisplay("KG")
                updateUnitButtons("KG")
            }
        }
        btnUnitLbs.setOnClickListener {
            if (currentUnit != "LBS") {
                convertWeightDisplay("LBS")
                updateUnitButtons("LBS")
            }
        }

        // Sound Preference Controls
        val btnSoundOn: Button = dialog.findViewById(R.id.btnSoundOn)
        val btnSoundOff: Button = dialog.findViewById(R.id.btnSoundOff)

        fun updateSoundButtons(enabled: Boolean) {
            btnSoundOn.setBackgroundResource(if (enabled) R.drawable.chip_active else R.drawable.chip_inactive)
            btnSoundOn.setTextColor(getColor(if (enabled) R.color.m3_on_primary else R.color.m3_on_surface))

            btnSoundOff.setBackgroundResource(if (!enabled) R.drawable.chip_active else R.drawable.chip_inactive)
            btnSoundOff.setTextColor(getColor(if (!enabled) R.color.m3_on_primary else R.color.m3_on_surface))
        }
        updateSoundButtons(isSoundEnabled)

        btnSoundOn.setOnClickListener {
            isSoundEnabled = true
            prefs.edit().putBoolean("pref_sound_enabled", true).apply()
            updateSoundButtons(true)
        }
        btnSoundOff.setOnClickListener {
            isSoundEnabled = false
            prefs.edit().putBoolean("pref_sound_enabled", false).apply()
            updateSoundButtons(false)
        }

        btnResetSessionDialog.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Session")
                .setMessage("Reset today's $currentDay workout? Logged sets for this active session will be cleared.")
                .setPositiveButton("Reset") { _, _ ->
                    completedSets.clear()
                    isWorkoutCompleteCelebrated = false
                    llPrBanner.visibility = View.GONE
                    cancelRestTimer()
                    sessionId = dbHelper.startSession(currentDay)
                    currentExerciseIndex = 0
                    refreshUiForCurrentExercise()
                    dialog.dismiss()
                    Toast.makeText(this, "Session reset for $currentDay", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        configureDialogWindow(dialog, 0.85f)
        dialog.show()
    }

    private fun showHistoryDialog() {
        if (isTimerRunning) pauseRestTimer()
        btnHistory.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_history)

        val llHistoryItems: LinearLayout = dialog.findViewById(R.id.llHistoryItems)
        val tvEmptyHistory: TextView = dialog.findViewById(R.id.tvEmptyHistory)
        val btnClearHistory: Button = dialog.findViewById(R.id.btnClearHistory)
        val btnCloseHistory: Button = dialog.findViewById(R.id.btnCloseHistory)

        fun loadHistoryItems() {
            llHistoryItems.removeAllViews()
            val sessions = dbHelper.getAllSessions()
            if (sessions.isEmpty()) {
                tvEmptyHistory.visibility = View.VISIBLE
                llHistoryItems.addView(tvEmptyHistory)
            } else {
                tvEmptyHistory.visibility = View.GONE
                for (s in sessions) {
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 4, 0, 8)
                        }
                        setBackgroundResource(R.drawable.card_m3)
                        setPadding(16, 12, 16, 12)

                        val row1 = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                        val tag = TextView(context).apply {
                            text = "${s.dayType} WORKOUT"
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            textSize = 13f
                            setTextColor(getColor(R.color.m3_primary))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        val date = TextView(context).apply {
                            text = s.date
                            textSize = 11f
                            setTextColor(getColor(R.color.m3_on_surface_variant))
                        }
                        row1.addView(tag)
                        row1.addView(date)
                        addView(row1)

                        val row2 = TextView(context).apply {
                            text = "${s.totalSets} sets logged • Total Volume: ${String.format(Locale.US, "%,.0f", s.totalVolume)} $currentUnit"
                            textSize = 12f
                            setTextColor(getColor(R.color.m3_on_surface))
                            setPadding(0, 4, 0, 0)
                        }
                        addView(row2)
                    }
                    llHistoryItems.addView(card)
                }
            }
        }

        loadHistoryItems()

        btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to delete all past session logs? This cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    dbHelper.clearAllHistory()
                    loadHistoryItems()
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnCloseHistory.setOnClickListener {
            dialog.dismiss()
        }

        configureDialogWindow(dialog, 0.88f)
        dialog.show()
    }

    private fun updatePowerliftingTelemetry() {
        val rawW = etWeight.text.toString().toDoubleOrNull() ?: 0.0
        val rawR = etReps.text.toString().toIntOrNull() ?: 1

        if (rawW.isNaN() || rawW.isInfinite() || rawW <= 0.0 || rawW > 2000.0 || rawR <= 0 || rawR > 500) {
            tvOneRepMax.text = "EST. 1RM: 0.0 $currentUnit"
            tvIntensityPct.text = "0% 1RM"
            tvPlateMath.text = "Enter valid weight (1-2000 $currentUnit)"
            return
        }

        // Epley 1RM formula: weight * (1 + reps/30)
        val oneRepMax = if (rawR <= 1) rawW else rawW * (1.0 + rawR / 30.0)
        tvOneRepMax.text = "EST. 1RM: ${String.format(Locale.US, "%.1f", oneRepMax)} $currentUnit"

        val intensity = ((rawW / oneRepMax) * 100).toInt().coerceIn(1, 100)
        tvIntensityPct.text = "$intensity% 1RM"

        // Barbell Plate Math (Instant division without while loops)
        val barWeight = if (currentUnit == "LBS") 45.0 else 20.0
        if (rawW < barWeight) {
            tvPlateMath.text = "Sub-bar weight (${String.format(Locale.US, "%.1f", rawW)} $currentUnit) • Dumbbell/Cable"
        } else if (rawW == barWeight) {
            tvPlateMath.text = "Bar only (${barWeight.toInt()} $currentUnit) • No plates needed"
        } else {
            val sideWeight = (rawW - barWeight) / 2.0
            val availablePlates = if (currentUnit == "LBS") {
                doubleArrayOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
            } else {
                doubleArrayOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
            }

            var rem = sideWeight
            val platesUsed = mutableListOf<String>()
            for (p in availablePlates) {
                val count = (rem / p).toInt()
                if (count > 0) {
                    val pLabel = if (p % 1.0 == 0.0) p.toInt().toString() else p.toString()
                    platesUsed.add(if (count > 1) "${count}×${pLabel}" else pLabel)
                    rem -= count * p
                }
            }

            val plateListStr = if (platesUsed.isNotEmpty()) platesUsed.joinToString(", ") else "None"
            tvPlateMath.text = "Bar: ${barWeight.toInt()} $currentUnit • Side: [$plateListStr] (${String.format(Locale.US, "%.1f", sideWeight)}/side)"
        }
    }

    private fun convertWeightDisplay(newUnit: String) {
        val currentW = etWeight.text.toString().toDoubleOrNull() ?: 0.0
        val converted = if (newUnit == "LBS") currentW * 2.20462 else currentW / 2.20462
        currentUnit = newUnit
        prefs.edit().putString("pref_unit", newUnit).apply()
        tvWeightLabel.text = "WEIGHT ($newUnit)"
        etWeight.setText(String.format(Locale.US, "%.1f", converted))
        updateProgressTelemetry()
        refreshUiForCurrentExercise()
    }

    private fun switchDay(day: String) {
        if (currentDay == day) return
        btnSettings.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        currentDay = day
        prefs.edit().putString("pref_last_day", day).apply()
        completedSets.clear()
        isWorkoutCompleteCelebrated = false
        llPrBanner.visibility = View.GONE
        cancelRestTimer()
        currentExerciseIndex = 0
        sessionId = dbHelper.startSession(currentDay)
        updateDayChipStyles()
        updateSubtitle()
        refreshUiForCurrentExercise()
    }

    private fun updateDayChipStyles() {
        val activeBg = R.drawable.chip_active
        val inactiveBg = R.drawable.chip_inactive

        btnDayPush.setBackgroundResource(if (currentDay == "PUSH") activeBg else inactiveBg)
        btnDayPush.setTextColor(if (currentDay == "PUSH") getColor(R.color.m3_on_primary) else getColor(R.color.m3_on_surface))

        btnDayPull.setBackgroundResource(if (currentDay == "PULL") activeBg else inactiveBg)
        btnDayPull.setTextColor(if (currentDay == "PULL") getColor(R.color.m3_on_primary) else getColor(R.color.m3_on_surface))

        btnDayLegs.setBackgroundResource(if (currentDay == "LEGS") activeBg else inactiveBg)
        btnDayLegs.setTextColor(if (currentDay == "LEGS") getColor(R.color.m3_on_primary) else getColor(R.color.m3_on_surface))
    }

    private fun startNewOrResumeSession() {
        updateDayChipStyles()
        sessionId = dbHelper.startSession(currentDay)
        refreshUiForCurrentExercise()
    }

    private fun refreshUiForCurrentExercise() {
        currentExercises = dbHelper.getExercises(currentDay)
        if (currentExercises.isEmpty()) {
            tvExerciseName.text = "No Exercises"
            tvTargetMuscles.text = "Tap '+ Add' to create an exercise"
            tvSetCounter.text = "SET 0 OF 0"
            tvPreviousBest.text = ""
            etWeight.setText("0.0")
            etReps.setText("0")
            renderRoutineList()
            updateProgressTelemetry()
            return
        }

        if (currentExerciseIndex !in currentExercises.indices) {
            currentExerciseIndex = 0
        }
        val ex = currentExercises[currentExerciseIndex]

        tvExerciseName.text = ex.name
        tvTargetMuscles.text = ex.targetMuscles

        val doneSets = completedSets.getOrDefault(currentExerciseIndex, 0)
        val currentSetNum = (doneSets + 1).coerceAtMost(ex.totalSets)
        tvSetCounter.text = "SET $currentSetNum OF ${ex.totalSets}"

        // Previous best query
        val prev = dbHelper.getLastPerformance(ex.name)
        if (prev != null) {
            tvPreviousBest.text = "Last: ${String.format(Locale.US, "%.1f", prev.first)} $currentUnit × ${prev.second}"
        } else {
            val defW = if (currentUnit == "LBS") ex.defaultWeight * 2.20462 else ex.defaultWeight
            tvPreviousBest.text = "Default: ${String.format(Locale.US, "%.1f", defW)} $currentUnit × ${ex.defaultReps}"
        }

        // Set inputs to previous or default
        val curW = etWeight.text.toString().toDoubleOrNull()
        if (curW == null || curW == 0.0) {
            val w = prev?.first ?: (if (currentUnit == "LBS") ex.defaultWeight * 2.20462 else ex.defaultWeight)
            etWeight.setText(String.format(Locale.US, "%.1f", w))
        }
        val curR = etReps.text.toString().toIntOrNull()
        if (curR == null || curR == 0) {
            val r = prev?.second ?: ex.defaultReps
            etReps.setText(r.toString())
        }

        updateProgressTelemetry()
        updatePowerliftingTelemetry()
        renderRoutineList()
    }

    private fun handleLogSet() {
        if (currentExercises.isEmpty() || currentExerciseIndex !in currentExercises.indices) return
        val ex = currentExercises[currentExerciseIndex]

        val rawWeight = etWeight.text.toString().trim()
        val rawReps = etReps.text.toString().trim()

        val weight = rawWeight.toDoubleOrNull()
        val reps = rawReps.toIntOrNull()

        if (weight == null || weight.isNaN() || weight.isInfinite() || weight < 0.0 || weight > 2000.0) {
            Toast.makeText(this, "Please enter valid weight (0 - 2,000)", Toast.LENGTH_SHORT).show()
            return
        }

        if (reps == null || reps <= 0 || reps > 500) {
            Toast.makeText(this, "Please enter valid reps (1 - 500)", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()

        // Check for Personal Record before logging
        val prevBest = dbHelper.getLastPerformance(ex.name)
        if (prevBest != null) {
            if (weight > prevBest.first) {
                showPrBanner("NEW WEIGHT PR! +${String.format(Locale.US, "%.1f", weight - prevBest.first)} $currentUnit (${String.format(Locale.US, "%.1f", weight)} $currentUnit)")
            } else if (weight == prevBest.first && reps > prevBest.second) {
                showPrBanner("NEW REP PR! +${reps - prevBest.second} Reps (${reps} reps @ ${String.format(Locale.US, "%.1f", weight)} $currentUnit)")
            }
        }

        val currentDone = completedSets.getOrDefault(currentExerciseIndex, 0)
        val setNumber = currentDone + 1
        completedSets[currentExerciseIndex] = setNumber

        // Log to SQLite
        dbHelper.logSet(sessionId, ex.name, setNumber, weight, currentUnit, reps)

        // Check if exercise completed
        if (setNumber >= ex.totalSets) {
            Toast.makeText(this, "${ex.name} finished! Great work!", Toast.LENGTH_SHORT).show()
            if (currentExerciseIndex < currentExercises.size - 1) {
                currentExerciseIndex++
            }
        }

        refreshUiForCurrentExercise()

        // Start rest interval with user's preferred duration
        totalRestSeconds = defaultRestSeconds
        startRestTimer(defaultRestSeconds)
    }

    private fun showPrBanner(message: String) {
        tvPrText.text = message
        llPrBanner.visibility = View.VISIBLE
        llPrBanner.alpha = 0f
        llPrBanner.scaleY = 0.8f
        llPrBanner.animate()
            .alpha(1f)
            .scaleY(1f)
            .setDuration(220)
            .withEndAction {
                llPrBanner.postDelayed({
                    llPrBanner.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction { llPrBanner.visibility = View.GONE }
                        .start()
                }, 3500)
            }
            .start()

        // Celebratory double-haptic pulse
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 140), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(longArrayOf(0, 80, 60, 140), -1)
            }
        } catch (_: Exception) {}
    }

    private fun startRestTimer(seconds: Long) {
        cancelRestTimer()
        btnLogSet.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        restSecondsRemaining = seconds
        totalRestSeconds = maxOf(totalRestSeconds, seconds)
        pbRestProgress.max = totalRestSeconds.toInt()
        pbRestProgress.progress = seconds.toInt()
        restEndTimeMillis = SystemClock.elapsedRealtime() + seconds * 1000L
        isTimerRunning = true
        btnRestToggle.setImageResource(R.drawable.ic_pause)
        tvTimerCountdown.setTextColor(getColor(R.color.m3_primary))
        val nextExName = currentExercises.getOrNull(currentExerciseIndex)?.name ?: "Next Set"
        tvTimerStatus.text = "Resting • Next: $nextExName"
        updateTimerDisplay(seconds)

        val executor = Executors.newSingleThreadScheduledExecutor()
        timerExecutor = executor
        executor.scheduleAtFixedRate({
            val secLeft = (restEndTimeMillis - SystemClock.elapsedRealtime() + 999) / 1000L
            if (secLeft > 0) {
                runOnUiThread {
                    if (isTimerRunning) {
                        restSecondsRemaining = secLeft
                        updateTimerDisplay(secLeft)
                        pbRestProgress.progress = secLeft.toInt()
                        if (secLeft <= 10) {
                            tvTimerCountdown.setTextColor(getColor(R.color.m3_error))
                            if (secLeft in 1..3) {
                                pulseCountdownTick()
                            }
                        } else {
                            tvTimerCountdown.setTextColor(getColor(R.color.m3_primary))
                        }
                    }
                }
            } else {
                runOnUiThread {
                    if (isTimerRunning) {
                        onRestTimerFinish()
                    }
                }
                executor.shutdown()
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun pauseRestTimer() {
        if (!isTimerRunning) return
        timerExecutor?.shutdownNow()
        timerExecutor = null
        isTimerRunning = false
        btnRestToggle.setImageResource(R.drawable.ic_play)
        tvTimerStatus.text = "Rest Paused • Tap Play to Resume"
        tvTimerCountdown.setTextColor(getColor(R.color.m3_on_surface_variant))
    }

    private fun resumeRestTimer() {
        val resumeTime = if (restSecondsRemaining > 0) restSecondsRemaining else defaultRestSeconds
        startRestTimer(resumeTime)
    }

    private fun cancelRestTimer() {
        timerExecutor?.shutdownNow()
        timerExecutor = null
        isTimerRunning = false
    }

    private fun onRestTimerFinish() {
        isTimerRunning = false
        timerExecutor?.shutdownNow()
        timerExecutor = null
        restSecondsRemaining = 0
        pbRestProgress.progress = 0
        updateTimerDisplay(0)
        btnRestToggle.setImageResource(R.drawable.ic_play)
        tvTimerCountdown.setTextColor(getColor(R.color.m3_success))
        tvTimerStatus.text = "⚡ Rest Complete! Ready to Lift!"
        triggerRestAlert()
    }

    private fun pulseCountdownTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            }
        } catch (_: Exception) {}
    }

    private fun updateTimerDisplay(sec: Long) {
        val m = sec / 60
        val s = sec % 60
        tvTimerCountdown.text = String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun triggerRestAlert() {
        // 1. Triple Haptic Vibration Pulse
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 350), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(longArrayOf(0, 200, 100, 200, 100, 350), -1)
            }
        } catch (_: Exception) {}

        // 2. Audible notification tone if enabled
        if (isSoundEnabled) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
            } catch (_: Exception) {}
        }
    }

    private fun updateProgressTelemetry() {
        val totalRoutineSets = currentExercises.sumOf { it.totalSets }
        val totalCompleted = completedSets.values.sum()

        val pct = if (totalRoutineSets > 0) ((totalCompleted * 100) / totalRoutineSets).coerceIn(0, 100) else 0

        tvProgressStatus.text = "Progress: $totalCompleted of $totalRoutineSets ($pct%)"
        pbWorkoutProgress.progress = pct

        val vol = dbHelper.getSessionVolume(sessionId)
        tvVolumeStats.text = "Volume: ${String.format(Locale.US, "%,.0f", vol)} $currentUnit"

        if (totalCompleted >= totalRoutineSets && totalRoutineSets > 0) {
            dbHelper.finishSession(sessionId)
            tvProgressStatus.text = "🎉 Session Complete! All $totalRoutineSets sets crushed!"
            if (!isWorkoutCompleteCelebrated) {
                isWorkoutCompleteCelebrated = true
                showWorkoutCrushedDialog(totalRoutineSets, vol)
            }
        }
    }

    private fun showWorkoutCrushedDialog(totalSets: Int, totalVolume: Double) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_workout_crushed)

        val tvCrushedTitle: TextView = dialog.findViewById(R.id.tvCrushedTitle)
        val tvCrushedStats: TextView = dialog.findViewById(R.id.tvCrushedStats)
        val btnDismissCrushed: Button = dialog.findViewById(R.id.btnDismissCrushed)

        tvCrushedTitle.text = "$currentDay DAY CRUSHED!"
        tvCrushedStats.text = "You conquered all $totalSets sets with an epic total volume of ${String.format(Locale.US, "%,.0f", totalVolume)} $currentUnit.\n\nTake recovery seriously, refuel, and prepare to attack your next split!"

        btnDismissCrushed.setOnClickListener {
            btnDismissCrushed.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dialog.dismiss()
        }

        configureDialogWindow(dialog, 0.85f)
        dialog.show()
    }

    private fun showAddExerciseDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_edit_exercise)

        val tvEditExTitle: TextView = dialog.findViewById(R.id.tvEditExTitle)
        val tvEditExSubtitle: TextView = dialog.findViewById(R.id.tvEditExSubtitle)
        val etExName: EditText = dialog.findViewById(R.id.etExName)
        val etExMuscles: EditText = dialog.findViewById(R.id.etExMuscles)

        val btnExSetsMinus: Button = dialog.findViewById(R.id.btnExSetsMinus)
        val etExSets: EditText = dialog.findViewById(R.id.etExSets)
        val btnExSetsPlus: Button = dialog.findViewById(R.id.btnExSetsPlus)

        val tvExWeightLabel: TextView = dialog.findViewById(R.id.tvExWeightLabel)
        val btnExWeightMinus: Button = dialog.findViewById(R.id.btnExWeightMinus)
        val etExWeight: EditText = dialog.findViewById(R.id.etExWeight)
        val btnExWeightPlus: Button = dialog.findViewById(R.id.btnExWeightPlus)

        val btnExRepsMinus: Button = dialog.findViewById(R.id.btnExRepsMinus)
        val etExReps: EditText = dialog.findViewById(R.id.etExReps)
        val btnExRepsPlus: Button = dialog.findViewById(R.id.btnExRepsPlus)

        val btnSaveExercise: Button = dialog.findViewById(R.id.btnSaveExercise)
        val btnDeleteExerciseFromEdit: Button = dialog.findViewById(R.id.btnDeleteExerciseFromEdit)
        val btnCancelEditExercise: Button = dialog.findViewById(R.id.btnCancelEditExercise)

        tvEditExTitle.text = "ADD NEW EXERCISE"
        tvEditExSubtitle.text = "Adding to $currentDay Workout"
        tvExWeightLabel.text = "DEFAULT WEIGHT ($currentUnit)"
        btnDeleteExerciseFromEdit.visibility = View.GONE
        btnSaveExercise.text = "ADD TO $currentDay"

        etExSets.setText("3")
        etExWeight.setText(if (currentUnit == "LBS") "135.0" else "60.0")
        etExReps.setText("10")

        btnExSetsMinus.setOnClickListener {
            val s = etExSets.text.toString().toIntOrNull() ?: 3
            etExSets.setText(maxOf(1, s - 1).toString())
        }
        btnExSetsPlus.setOnClickListener {
            val s = etExSets.text.toString().toIntOrNull() ?: 3
            etExSets.setText(minOf(20, s + 1).toString())
        }

        btnExWeightMinus.setOnClickListener {
            val w = etExWeight.text.toString().toDoubleOrNull() ?: 50.0
            val step = if (currentUnit == "LBS") 5.0 else 2.5
            etExWeight.setText(String.format(Locale.US, "%.1f", maxOf(0.0, w - step)))
        }
        btnExWeightPlus.setOnClickListener {
            val w = etExWeight.text.toString().toDoubleOrNull() ?: 50.0
            val step = if (currentUnit == "LBS") 5.0 else 2.5
            etExWeight.setText(String.format(Locale.US, "%.1f", minOf(2000.0, w + step)))
        }

        btnExRepsMinus.setOnClickListener {
            val r = etExReps.text.toString().toIntOrNull() ?: 10
            etExReps.setText(maxOf(1, r - 1).toString())
        }
        btnExRepsPlus.setOnClickListener {
            val r = etExReps.text.toString().toIntOrNull() ?: 10
            etExReps.setText(minOf(100, r + 1).toString())
        }

        btnSaveExercise.setOnClickListener {
            val name = etExName.text.toString().trim()
            if (name.isEmpty()) {
                etExName.error = "Please enter exercise name"
                return@setOnClickListener
            }
            val muscles = etExMuscles.text.toString().trim().ifEmpty { "$currentDay MUSCLES" }
            val sets = (etExSets.text.toString().toIntOrNull() ?: 3).coerceIn(1, 20)
            val weight = (etExWeight.text.toString().toDoubleOrNull() ?: 50.0).coerceIn(0.0, 2000.0)
            val reps = (etExReps.text.toString().toIntOrNull() ?: 10).coerceIn(1, 100)

            dbHelper.addExercise(currentDay, name, muscles, sets, weight, reps)
            refreshUiForCurrentExercise()
            dialog.dismiss()
            Toast.makeText(this, "Added '$name' to $currentDay", Toast.LENGTH_SHORT).show()
        }

        btnCancelEditExercise.setOnClickListener {
            dialog.dismiss()
        }

        configureDialogWindow(dialog, 0.88f)
        dialog.show()
    }

    private fun showEditExerciseDialog(ex: Exercise, onUpdatedOrDeleted: (() -> Unit)? = null) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_edit_exercise)

        val tvEditExTitle: TextView = dialog.findViewById(R.id.tvEditExTitle)
        val tvEditExSubtitle: TextView = dialog.findViewById(R.id.tvEditExSubtitle)
        val etExName: EditText = dialog.findViewById(R.id.etExName)
        val etExMuscles: EditText = dialog.findViewById(R.id.etExMuscles)

        val btnExSetsMinus: Button = dialog.findViewById(R.id.btnExSetsMinus)
        val etExSets: EditText = dialog.findViewById(R.id.etExSets)
        val btnExSetsPlus: Button = dialog.findViewById(R.id.btnExSetsPlus)

        val tvExWeightLabel: TextView = dialog.findViewById(R.id.tvExWeightLabel)
        val btnExWeightMinus: Button = dialog.findViewById(R.id.btnExWeightMinus)
        val etExWeight: EditText = dialog.findViewById(R.id.etExWeight)
        val btnExWeightPlus: Button = dialog.findViewById(R.id.btnExWeightPlus)

        val btnExRepsMinus: Button = dialog.findViewById(R.id.btnExRepsMinus)
        val etExReps: EditText = dialog.findViewById(R.id.etExReps)
        val btnExRepsPlus: Button = dialog.findViewById(R.id.btnExRepsPlus)

        val btnSaveExercise: Button = dialog.findViewById(R.id.btnSaveExercise)
        val btnDeleteExerciseFromEdit: Button = dialog.findViewById(R.id.btnDeleteExerciseFromEdit)
        val btnCancelEditExercise: Button = dialog.findViewById(R.id.btnCancelEditExercise)

        tvEditExTitle.text = "EDIT EXERCISE"
        tvEditExSubtitle.text = "${ex.dayType} Split"
        tvExWeightLabel.text = "DEFAULT WEIGHT ($currentUnit)"
        btnDeleteExerciseFromEdit.visibility = View.VISIBLE
        btnSaveExercise.text = "SAVE CHANGES"

        etExName.setText(ex.name)
        etExMuscles.setText(ex.targetMuscles)
        etExSets.setText(ex.totalSets.toString())
        etExWeight.setText(String.format(Locale.US, "%.1f", ex.defaultWeight))
        etExReps.setText(ex.defaultReps.toString())

        btnExSetsMinus.setOnClickListener {
            val s = etExSets.text.toString().toIntOrNull() ?: ex.totalSets
            etExSets.setText(maxOf(1, s - 1).toString())
        }
        btnExSetsPlus.setOnClickListener {
            val s = etExSets.text.toString().toIntOrNull() ?: ex.totalSets
            etExSets.setText(minOf(20, s + 1).toString())
        }

        btnExWeightMinus.setOnClickListener {
            val w = etExWeight.text.toString().toDoubleOrNull() ?: ex.defaultWeight
            val step = if (currentUnit == "LBS") 5.0 else 2.5
            etExWeight.setText(String.format(Locale.US, "%.1f", maxOf(0.0, w - step)))
        }
        btnExWeightPlus.setOnClickListener {
            val w = etExWeight.text.toString().toDoubleOrNull() ?: ex.defaultWeight
            val step = if (currentUnit == "LBS") 5.0 else 2.5
            etExWeight.setText(String.format(Locale.US, "%.1f", minOf(2000.0, w + step)))
        }

        btnExRepsMinus.setOnClickListener {
            val r = etExReps.text.toString().toIntOrNull() ?: ex.defaultReps
            etExReps.setText(maxOf(1, r - 1).toString())
        }
        btnExRepsPlus.setOnClickListener {
            val r = etExReps.text.toString().toIntOrNull() ?: ex.defaultReps
            etExReps.setText(minOf(100, r + 1).toString())
        }

        btnSaveExercise.setOnClickListener {
            val name = etExName.text.toString().trim()
            if (name.isEmpty()) {
                etExName.error = "Please enter exercise name"
                return@setOnClickListener
            }
            val muscles = etExMuscles.text.toString().trim().ifEmpty { "${ex.dayType} MUSCLES" }
            val sets = (etExSets.text.toString().toIntOrNull() ?: ex.totalSets).coerceIn(1, 20)
            val weight = (etExWeight.text.toString().toDoubleOrNull() ?: ex.defaultWeight).coerceIn(0.0, 2000.0)
            val reps = (etExReps.text.toString().toIntOrNull() ?: ex.defaultReps).coerceIn(1, 100)

            ex.name = name
            ex.targetMuscles = muscles
            ex.totalSets = sets
            ex.defaultWeight = weight
            ex.defaultReps = reps

            dbHelper.updateExercise(ex.id, name, muscles, sets, weight, reps)
            refreshUiForCurrentExercise()
            onUpdatedOrDeleted?.invoke()
            dialog.dismiss()
            Toast.makeText(this, "Updated '$name'", Toast.LENGTH_SHORT).show()
        }

        btnDeleteExerciseFromEdit.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Exercise")
                .setMessage("Are you sure you want to remove '${ex.name}' from your ${ex.dayType} routine?")
                .setPositiveButton("Delete") { _, _ ->
                    dbHelper.deleteExercise(ex.id)
                    currentExercises.remove(ex)
                    if (currentExerciseIndex >= currentExercises.size) {
                        currentExerciseIndex = maxOf(0, currentExercises.size - 1)
                    }
                    refreshUiForCurrentExercise()
                    onUpdatedOrDeleted?.invoke()
                    dialog.dismiss()
                    Toast.makeText(this, "Removed '${ex.name}'", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnCancelEditExercise.setOnClickListener {
            dialog.dismiss()
        }

        configureDialogWindow(dialog, 0.88f)
        dialog.show()
    }

    private fun showManageRoutineDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_manage_routine)

        val tvManageRoutineTitle: TextView = dialog.findViewById(R.id.tvManageRoutineTitle)
        val llManageExerciseList: LinearLayout = dialog.findViewById(R.id.llManageExerciseList)
        val btnAddExerciseFromManage: Button = dialog.findViewById(R.id.btnAddExerciseFromManage)
        val btnResetSplitDefaults: Button = dialog.findViewById(R.id.btnResetSplitDefaults)
        val btnCloseManageRoutine: Button = dialog.findViewById(R.id.btnCloseManageRoutine)

        tvManageRoutineTitle.text = "MANAGE $currentDay ROUTINE"

        fun loadList() {
            llManageExerciseList.removeAllViews()
            currentExercises = dbHelper.getExercises(currentDay)

            for ((idx, ex) in currentExercises.withIndex()) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 4, 0, 8)
                    }
                    setPadding(14, 10, 14, 10)
                    setBackgroundResource(R.drawable.card_m3)
                }

                // Header Row: Order Number + Exercise Name + Sets/Reps Summary
                val rowHeader = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val tvName = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = "${idx + 1}. ${ex.name}"
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(getColor(R.color.m3_on_surface))
                }

                val tvSpecs = TextView(this).apply {
                    text = "${ex.totalSets} sets × ${ex.defaultReps} reps"
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(getColor(R.color.m3_primary))
                }

                rowHeader.addView(tvName)
                rowHeader.addView(tvSpecs)
                card.addView(rowHeader)

                // Subtitle Row: Target Muscles on left, Action buttons on right
                val rowActions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 6, 0, 0)
                    }
                }

                val tvMuscles = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = ex.targetMuscles
                    textSize = 10f
                    setTextColor(getColor(R.color.m3_on_surface_variant))
                }
                rowActions.addView(tvMuscles)

                val btnUp = ImageButton(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (resources.displayMetrics.density * 36).toInt(),
                        (resources.displayMetrics.density * 36).toInt()
                    ).apply {
                        setMargins(2, 0, 2, 0)
                    }
                    setBackgroundResource(R.drawable.bg_topbar_button)
                    setImageResource(R.drawable.ic_arrow_up)
                    setPadding(8, 8, 8, 8)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isEnabled = idx > 0
                    alpha = if (idx > 0) 1.0f else 0.3f
                    contentDescription = "Move Up"
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        dbHelper.moveExercise(currentDay, ex.id, moveUp = true)
                        loadList()
                        refreshUiForCurrentExercise()
                    }
                }

                val btnDown = ImageButton(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (resources.displayMetrics.density * 36).toInt(),
                        (resources.displayMetrics.density * 36).toInt()
                    ).apply {
                        setMargins(2, 0, 2, 0)
                    }
                    setBackgroundResource(R.drawable.bg_topbar_button)
                    setImageResource(R.drawable.ic_arrow_down)
                    setPadding(8, 8, 8, 8)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isEnabled = idx < currentExercises.size - 1
                    alpha = if (idx < currentExercises.size - 1) 1.0f else 0.3f
                    contentDescription = "Move Down"
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        dbHelper.moveExercise(currentDay, ex.id, moveUp = false)
                        loadList()
                        refreshUiForCurrentExercise()
                    }
                }

                val btnEdit = ImageButton(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (resources.displayMetrics.density * 36).toInt(),
                        (resources.displayMetrics.density * 36).toInt()
                    ).apply {
                        setMargins(2, 0, 2, 0)
                    }
                    setBackgroundResource(R.drawable.bg_topbar_button)
                    setImageResource(R.drawable.ic_edit)
                    setPadding(8, 8, 8, 8)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Edit Exercise"
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showEditExerciseDialog(ex) {
                            loadList()
                        }
                    }
                }

                val btnDelete = ImageButton(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (resources.displayMetrics.density * 36).toInt(),
                        (resources.displayMetrics.density * 36).toInt()
                    ).apply {
                        setMargins(2, 0, 2, 0)
                    }
                    setBackgroundResource(R.drawable.bg_topbar_button)
                    setImageResource(R.drawable.ic_delete)
                    setPadding(8, 8, 8, 8)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Delete Exercise"
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Delete Exercise")
                            .setMessage("Remove '${ex.name}' from $currentDay routine?")
                            .setPositiveButton("Delete") { _, _ ->
                                dbHelper.deleteExercise(ex.id)
                                loadList()
                                refreshUiForCurrentExercise()
                                Toast.makeText(this@MainActivity, "Removed '${ex.name}'", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }

                rowActions.addView(btnUp)
                rowActions.addView(btnDown)
                rowActions.addView(btnEdit)
                rowActions.addView(btnDelete)
                card.addView(rowActions)

                llManageExerciseList.addView(card)
            }
        }

        loadList()

        btnAddExerciseFromManage.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showAddExerciseDialog()
            dialog.dismiss()
        }

        btnResetSplitDefaults.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset to Defaults")
                .setMessage("Reset $currentDay routine back to the standard 5 exercises?")
                .setPositiveButton("Reset") { _, _ ->
                    dbHelper.resetRoutineToDefaults(currentDay)
                    loadList()
                    currentExerciseIndex = 0
                    refreshUiForCurrentExercise()
                    Toast.makeText(this, "Reset $currentDay routine", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnCloseManageRoutine.setOnClickListener {
            dialog.dismiss()
        }

        configureDialogWindow(dialog, 0.88f)
        dialog.show()
    }

    private fun renderRoutineList() {
        llRoutineList.removeAllViews()
        for ((idx, ex) in currentExercises.withIndex()) {
            val done = completedSets.getOrDefault(idx, 0)
            val isCurrent = (idx == currentExerciseIndex)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = (resources.displayMetrics.density * 48).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
                setPadding(14, 10, 14, 10)
                setBackgroundResource(
                    if (isCurrent) R.drawable.card_m3_high else R.drawable.card_background
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentExerciseIndex = idx
                    refreshUiForCurrentExercise()
                }
            }

            val tvName = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "${idx + 1}. ${ex.name}"
                textSize = 13f
                setTextColor(
                    if (isCurrent) getColor(R.color.m3_primary) else getColor(R.color.m3_on_surface)
                )
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val tvBadge = TextView(this).apply {
                text = if (done >= ex.totalSets) "$done/${ex.totalSets} ✓" else "$done/${ex.totalSets}"
                textSize = 12f
                setTextColor(
                    if (done >= ex.totalSets) getColor(R.color.m3_success) else getColor(R.color.m3_on_surface_variant)
                )
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            row.addView(tvName)
            row.addView(tvBadge)
            llRoutineList.addView(row)
        }
    }

    private fun applyStatusBarAppearance() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            val isDark = when (themeMode) {
                1 -> false
                2 -> true
                else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            if (isDark) {
                controller?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            } else {
                controller?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val v = currentFocus ?: window.decorView
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    handleLogSet()
                    return true
                }
                KeyEvent.KEYCODE_SPACE -> {
                    if (isTimerRunning) {
                        pauseRestTimer()
                    } else {
                        resumeRestTimer()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelRestTimer()
    }
}
