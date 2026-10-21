package com.kimrosil.pplworkout

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

data class Exercise(
    val name: String,
    val targetMuscles: String,
    val totalSets: Int,
    val defaultWeight: Double,
    val defaultReps: Int
)

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dbHelper: DatabaseHelper

    // Top Bar Views
    private lateinit var tvAppTitle: TextView
    private lateinit var tvAppSubtitle: TextView
    private lateinit var btnResetSession: Button
    private lateinit var btnThemeToggle: Button

    // Day Chips
    private lateinit var btnDayPush: Button
    private lateinit var btnDayPull: Button
    private lateinit var btnDayLegs: Button

    // Progress Telemetry
    private lateinit var tvProgressStatus: TextView
    private lateinit var tvVolumeStats: TextView
    private lateinit var pbWorkoutProgress: ProgressBar

    // Rest Timer
    private lateinit var tvTimerCountdown: TextView
    private lateinit var tvTimerStatus: TextView
    private lateinit var btnRest60: Button
    private lateinit var btnRest90: Button
    private lateinit var btnRest120: Button
    private lateinit var btnRestPlus30: Button
    private lateinit var btnRestSkip: Button

    // Active Exercise Deck
    private lateinit var btnPrevExercise: Button
    private lateinit var tvExerciseName: TextView
    private lateinit var tvTargetMuscles: TextView
    private lateinit var btnNextExercise: Button
    private lateinit var tvSetCounter: TextView
    private lateinit var tvPreviousBest: TextView
    private lateinit var btnUnitToggle: Button
    private lateinit var btnWeightMinus: Button
    private lateinit var etWeight: EditText
    private lateinit var btnWeightPlus: Button
    private lateinit var btnRepsMinus: Button
    private lateinit var etReps: EditText
    private lateinit var btnRepsPlus: Button
    private lateinit var btnLogSet: Button

    // Routine Overview
    private lateinit var llRoutineList: LinearLayout

    // State Variables
    private var currentDay = "PUSH"
    private var currentExerciseIndex = 0
    private var sessionId: Long = -1
    private var currentUnit = "KG"
    private var themeMode = 0 // 0=Auto, 1=Light, 2=Dark

    // Routine definitions
    private val routines = mapOf(
        "PUSH" to listOf(
            Exercise("Barbell Bench Press", "CHEST • SHOULDERS • TRICEPS", 4, 80.0, 8),
            Exercise("Overhead Barbell Press", "FRONT DELTS • TRICEPS", 3, 50.0, 8),
            Exercise("Incline Dumbbell Press", "UPPER CHEST • SHOULDERS", 3, 28.0, 10),
            Exercise("Cable Lateral Raise", "SIDE DELTS", 4, 12.0, 15),
            Exercise("Tricep Rope Pushdown", "TRICEPS LATERAL HEAD", 3, 30.0, 12)
        ),
        "PULL" to listOf(
            Exercise("Barbell Bent-Over Row", "LATS • UPPER BACK", 4, 75.0, 8),
            Exercise("Lat Pulldown", "LATS • TERES MAJOR", 3, 65.0, 10),
            Exercise("Seated Cable Row", "RHOMBOIDS • MID TRAPS", 3, 60.0, 10),
            Exercise("Face Pulls", "REAR DELTS • ROTATOR CUFF", 3, 25.0, 15),
            Exercise("Incline Dumbbell Curl", "BICEPS BRACHII", 4, 14.0, 10)
        ),
        "LEGS" to listOf(
            Exercise("Barbell Back Squat", "QUADS • GLUTES • CORE", 4, 100.0, 6),
            Exercise("Romanian Deadlift", "HAMSTRINGS • POSTERIOR CHAIN", 3, 90.0, 8),
            Exercise("Leg Press", "QUADS • ADDUCTORS", 3, 180.0, 10),
            Exercise("Standing Calf Raise", "GASTROCNEMIUS", 4, 60.0, 15),
            Exercise("Hanging Knee Raise", "ABDOMINALS • HIP FLEXORS", 3, 0.0, 15)
        )
    )

    // Completed sets counter per exercise index
    private val completedSets = mutableMapOf<Int, Int>()

    // Rest Timer fields
    private var countDownTimer: CountDownTimer? = null
    private var restSecondsRemaining: Long = 90
    private var isTimerRunning = false

    override fun attachBaseContext(newBase: Context) {
        val p = newBase.getSharedPreferences("ppl-workout_prefs", MODE_PRIVATE)
        val mode = p.getInt("pref_theme_mode", 0)
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
        themeMode = prefs.getInt("pref_theme_mode", 0)
        currentUnit = prefs.getString("pref_unit", "KG") ?: "KG"
        currentDay = prefs.getString("pref_last_day", "PUSH") ?: "PUSH"

        bindViews()
        setupListeners()
        applyStatusBarAppearance()
        startNewOrResumeSession()
    }

    private fun bindViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle)
        btnResetSession = findViewById(R.id.btnResetSession)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)

        btnDayPush = findViewById(R.id.btnDayPush)
        btnDayPull = findViewById(R.id.btnDayPull)
        btnDayLegs = findViewById(R.id.btnDayLegs)

        tvProgressStatus = findViewById(R.id.tvProgressStatus)
        tvVolumeStats = findViewById(R.id.tvVolumeStats)
        pbWorkoutProgress = findViewById(R.id.pbWorkoutProgress)

        tvTimerCountdown = findViewById(R.id.tvTimerCountdown)
        tvTimerStatus = findViewById(R.id.tvTimerStatus)
        btnRest60 = findViewById(R.id.btnRest60)
        btnRest90 = findViewById(R.id.btnRest90)
        btnRest120 = findViewById(R.id.btnRest120)
        btnRestPlus30 = findViewById(R.id.btnRestPlus30)
        btnRestSkip = findViewById(R.id.btnRestSkip)

        btnPrevExercise = findViewById(R.id.btnPrevExercise)
        tvExerciseName = findViewById(R.id.tvExerciseName)
        tvTargetMuscles = findViewById(R.id.tvTargetMuscles)
        btnNextExercise = findViewById(R.id.btnNextExercise)
        tvSetCounter = findViewById(R.id.tvSetCounter)
        tvPreviousBest = findViewById(R.id.tvPreviousBest)
        btnUnitToggle = findViewById(R.id.btnUnitToggle)
        btnWeightMinus = findViewById(R.id.btnWeightMinus)
        etWeight = findViewById(R.id.etWeight)
        btnWeightPlus = findViewById(R.id.btnWeightPlus)
        btnRepsMinus = findViewById(R.id.btnRepsMinus)
        etReps = findViewById(R.id.etReps)
        btnRepsPlus = findViewById(R.id.btnRepsPlus)
        btnLogSet = findViewById(R.id.btnLogSet)

        llRoutineList = findViewById(R.id.llRoutineList)

        btnUnitToggle.text = currentUnit
        updateThemeButtonLabel()
    }

    private fun setupListeners() {
        // Theme Switcher
        btnThemeToggle.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            themeMode = (themeMode + 1) % 3
            prefs.edit().putInt("pref_theme_mode", themeMode).apply()
            recreate()
        }

        // Session Reset
        btnResetSession.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            completedSets.clear()
            cancelRestTimer()
            sessionId = dbHelper.startSession(currentDay)
            currentExerciseIndex = 0
            refreshUiForCurrentExercise()
            Toast.makeText(this, "Session reset for $currentDay", Toast.LENGTH_SHORT).show()
        }

        // PPL Day Switchers
        btnDayPush.setOnClickListener { switchDay("PUSH") }
        btnDayPull.setOnClickListener { switchDay("PULL") }
        btnDayLegs.setOnClickListener { switchDay("LEGS") }

        // Exercise Navigation
        btnPrevExercise.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val list = routines[currentDay] ?: return@setOnClickListener
            if (currentExerciseIndex > 0) {
                currentExerciseIndex--
                refreshUiForCurrentExercise()
            }
        }

        btnNextExercise.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val list = routines[currentDay] ?: return@setOnClickListener
            if (currentExerciseIndex < list.size - 1) {
                currentExerciseIndex++
                refreshUiForCurrentExercise()
            }
        }

        // Steppers
        btnWeightMinus.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val w = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val newW = if (w.isNaN() || w.isInfinite()) 0.0 else (w - 5.0).coerceIn(0.0, 2000.0)
            etWeight.setText(String.format(Locale.US, "%.1f", newW))
        }

        btnWeightPlus.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val w = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            val newW = if (w.isNaN() || w.isInfinite()) 0.0 else (w + 5.0).coerceIn(0.0, 2000.0)
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

        // Unit Toggle
        btnUnitToggle.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            currentUnit = if (currentUnit == "KG") "LBS" else "KG"
            btnUnitToggle.text = currentUnit
            prefs.edit().putString("pref_unit", currentUnit).apply()

            // Convert displayed weight defensively
            val currentW = etWeight.text.toString().toDoubleOrNull() ?: 0.0
            if (!currentW.isNaN() && !currentW.isInfinite() && currentW >= 0.0 && currentW <= 2000.0) {
                val converted = if (currentUnit == "LBS") currentW * 2.20462 else currentW / 2.20462
                etWeight.setText(String.format(Locale.US, "%.1f", converted))
            } else {
                etWeight.setText("0.0")
            }
        }

        // Log Set Action
        btnLogSet.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            handleLogSet()
        }

        // Rest Timer Presets
        btnRest60.setOnClickListener { startRestTimer(60) }
        btnRest90.setOnClickListener { startRestTimer(90) }
        btnRest120.setOnClickListener { startRestTimer(120) }
        btnRestPlus30.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val newTime = if (isTimerRunning && restSecondsRemaining > 0) restSecondsRemaining + 30 else 30L
            startRestTimer(newTime)
        }
        btnRestSkip.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            cancelRestTimer()
            tvTimerStatus.text = "Rest skipped • Ready for next set!"
            tvTimerCountdown.text = "00:00"
        }
    }

    private fun switchDay(day: String) {
        if (currentDay == day) return
        btnResetSession.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        currentDay = day
        prefs.edit().putString("pref_last_day", day).apply()
        completedSets.clear()
        cancelRestTimer()
        currentExerciseIndex = 0
        sessionId = dbHelper.startSession(currentDay)
        updateDayChipStyles()
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
        val exerciseList = routines[currentDay] ?: return
        if (currentExerciseIndex !in exerciseList.indices) {
            currentExerciseIndex = 0
        }
        val ex = exerciseList[currentExerciseIndex]

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
            tvPreviousBest.text = "Default: ${ex.defaultWeight} $currentUnit × ${ex.defaultReps}"
        }

        // Set inputs to previous or default
        if (etWeight.text.isNullOrEmpty() || etWeight.text.toString() == "0.0") {
            val w = prev?.first ?: ex.defaultWeight
            etWeight.setText(String.format(Locale.US, "%.1f", w))
        }
        if (etReps.text.isNullOrEmpty() || etReps.text.toString() == "0") {
            val r = prev?.second ?: ex.defaultReps
            etReps.setText(r.toString())
        }

        updateProgressTelemetry()
        renderRoutineList()
    }

    private fun handleLogSet() {
        val exerciseList = routines[currentDay] ?: return
        val ex = exerciseList[currentExerciseIndex]

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

        val currentDone = completedSets.getOrDefault(currentExerciseIndex, 0)
        val setNumber = currentDone + 1
        completedSets[currentExerciseIndex] = setNumber

        // Log to SQLite
        dbHelper.logSet(sessionId, ex.name, setNumber, weight, currentUnit, reps)

        // Check if exercise completed
        if (setNumber >= ex.totalSets) {
            Toast.makeText(this, "${ex.name} finished! Great work!", Toast.LENGTH_SHORT).show()
            if (currentExerciseIndex < exerciseList.size - 1) {
                currentExerciseIndex++
            }
        }

        refreshUiForCurrentExercise()

        // Start standard 90s rest interval
        startRestTimer(90)
    }

    private fun startRestTimer(seconds: Long) {
        cancelRestTimer()
        btnLogSet.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        restSecondsRemaining = seconds
        isTimerRunning = true
        tvTimerStatus.text = "Resting • Next: ${routines[currentDay]?.getOrNull(currentExerciseIndex)?.name ?: "Next Set"}"

        countDownTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                restSecondsRemaining = millisUntilFinished / 1000
                updateTimerDisplay(restSecondsRemaining)
            }

            override fun onFinish() {
                isTimerRunning = false
                updateTimerDisplay(0)
                tvTimerStatus.text = "⚡ Rest Complete! Hit your next set!"
                triggerRestAlert()
            }
        }.start()
    }

    private fun cancelRestTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        isTimerRunning = false
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
                    VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250, 150, 400), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(longArrayOf(0, 250, 150, 250, 150, 400), -1)
            }
        } catch (_: Exception) {}

        // 2. Audible notification tone
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        } catch (_: Exception) {}
    }

    private fun updateProgressTelemetry() {
        val exerciseList = routines[currentDay] ?: return
        val totalRoutineSets = exerciseList.sumOf { it.totalSets }
        val totalCompleted = completedSets.values.sum()

        val pct = if (totalRoutineSets > 0) ((totalCompleted * 100) / totalRoutineSets).coerceIn(0, 100) else 0

        tvProgressStatus.text = "Session Progress: $totalCompleted of $totalRoutineSets ($pct%)"
        pbWorkoutProgress.progress = pct

        val vol = dbHelper.getSessionVolume(sessionId)
        tvVolumeStats.text = "Volume: ${String.format(Locale.US, "%,.0f", vol)} $currentUnit"

        if (totalCompleted >= totalRoutineSets && totalRoutineSets > 0) {
            dbHelper.finishSession(sessionId)
            tvProgressStatus.text = "🎉 Session Complete! All $totalRoutineSets sets crushed!"
        }
    }

    private fun renderRoutineList() {
        llRoutineList.removeAllViews()
        val exerciseList = routines[currentDay] ?: return

        for ((idx, ex) in exerciseList.withIndex()) {
            val done = completedSets.getOrDefault(idx, 0)
            val isCurrent = (idx == currentExerciseIndex)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
                setPadding(12, 10, 12, 10)
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
                textSize = 14f
                setTextColor(
                    if (isCurrent) getColor(R.color.m3_primary) else getColor(R.color.m3_on_surface)
                )
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val tvBadge = TextView(this).apply {
                text = if (done >= ex.totalSets) "$done/${ex.totalSets} ✓" else "$done/${ex.totalSets}"
                textSize = 13f
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

    private fun updateThemeButtonLabel() {
        btnThemeToggle.text = when (themeMode) {
            1 -> "Light"
            2 -> "Dark"
            else -> "Auto"
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
                        cancelRestTimer()
                        tvTimerStatus.text = "Timer paused"
                    } else {
                        startRestTimer(90)
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
