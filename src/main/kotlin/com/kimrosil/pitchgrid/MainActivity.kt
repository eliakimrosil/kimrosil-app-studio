package com.kimrosil.pitchgrid

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.log10

class MainActivity : Activity() {

    // UI - Header
    private lateinit var btnThemeToggle: Button
    private lateinit var btnOpenPresets: Button

    // UI - Scope HUD
    private lateinit var visualizerView: WaveformVisualizerView
    private lateinit var tvTallyStatus: TextView
    private lateinit var tvFrequencyDisplay: TextView
    private lateinit var tvNoteDisplay: TextView
    private lateinit var tvCentsOffsetDisplay: TextView
    private lateinit var btnMasterToggle: Button

    // UI - Waveforms
    private lateinit var btnWaveSine: Button
    private lateinit var btnWaveTriangle: Button
    private lateinit var btnWaveSawtooth: Button
    private lateinit var btnWaveSquare: Button
    private lateinit var btnWaveNoise: Button

    // UI - Frequency Tuning
    private lateinit var btnEnterDirectFreq: Button
    private lateinit var sbFrequencyLog: SeekBar
    private lateinit var btnNudgeMinus100: Button
    private lateinit var btnNudgeMinus10: Button
    private lateinit var btnNudgeMinus1: Button
    private lateinit var btnNudgePlus1: Button
    private lateinit var btnNudgePlus10: Button
    private lateinit var btnNudgePlus100: Button

    // UI - Pitch & Cents
    private lateinit var btnOctaveDown: Button
    private lateinit var btnNoteDown: Button
    private lateinit var btnNoteUp: Button
    private lateinit var btnOctaveUp: Button
    private lateinit var btnRef440: Button
    private lateinit var btnRef432: Button
    private lateinit var btnRef444: Button
    private lateinit var btnResetCents: Button
    private lateinit var sbCents: SeekBar

    // UI - Intervals
    private lateinit var btnIntervalMajorThird: Button
    private lateinit var btnIntervalFourth: Button
    private lateinit var btnIntervalFifth: Button
    private lateinit var btnIntervalOctave: Button

    // UI - Output Level & Actions
    private lateinit var tvVolumeDisplay: TextView
    private lateinit var sbVolume: SeekBar
    private lateinit var btnSweepTest: Button
    private lateinit var btnSavePreset: Button

    // Engine & State
    private val audioEngine = AudioSynthesisEngine()
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentFrequency = 440.0
    private var baseReferenceA4 = 440.0
    private var activeWaveform = WaveformType.SINE
    private var currentCents = 0
    private var currentVolume = 0.5f

    private var isSweeping = false
    private var sweepRunnable: Runnable? = null

    companion object {
        private const val REQUEST_PRESET = 1001
        private const val PREF_NAME = "pitchgrid_prefs"
        private const val KEY_THEME = "pref_theme_mode"
        private const val KEY_LAST_FREQ = "pref_last_freq"
        private const val KEY_LAST_WAVE = "pref_last_wave"
    }

    override fun attachBaseContext(newBase: Context) {
        val sp = newBase.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val mode = sp.getInt(KEY_THEME, 0)
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

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        dbHelper = DatabaseHelper(this)

        initViews()
        loadPersistedState()
        setupListeners()
        setupAudioVisualizer()
        updateUIState()
    }

    private fun initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        btnOpenPresets = findViewById(R.id.btnOpenPresets)

        visualizerView = findViewById(R.id.visualizerView)
        tvTallyStatus = findViewById(R.id.tvTallyStatus)
        tvFrequencyDisplay = findViewById(R.id.tvFrequencyDisplay)
        tvNoteDisplay = findViewById(R.id.tvNoteDisplay)
        tvCentsOffsetDisplay = findViewById(R.id.tvCentsOffsetDisplay)
        btnMasterToggle = findViewById(R.id.btnMasterToggle)

        btnWaveSine = findViewById(R.id.btnWaveSine)
        btnWaveTriangle = findViewById(R.id.btnWaveTriangle)
        btnWaveSawtooth = findViewById(R.id.btnWaveSawtooth)
        btnWaveSquare = findViewById(R.id.btnWaveSquare)
        btnWaveNoise = findViewById(R.id.btnWaveNoise)

        btnEnterDirectFreq = findViewById(R.id.btnEnterDirectFreq)
        sbFrequencyLog = findViewById(R.id.sbFrequencyLog)
        btnNudgeMinus100 = findViewById(R.id.btnNudgeMinus100)
        btnNudgeMinus10 = findViewById(R.id.btnNudgeMinus10)
        btnNudgeMinus1 = findViewById(R.id.btnNudgeMinus1)
        btnNudgePlus1 = findViewById(R.id.btnNudgePlus1)
        btnNudgePlus10 = findViewById(R.id.btnNudgePlus10)
        btnNudgePlus100 = findViewById(R.id.btnNudgePlus100)

        btnOctaveDown = findViewById(R.id.btnOctaveDown)
        btnNoteDown = findViewById(R.id.btnNoteDown)
        btnNoteUp = findViewById(R.id.btnNoteUp)
        btnOctaveUp = findViewById(R.id.btnOctaveUp)
        btnRef440 = findViewById(R.id.btnRef440)
        btnRef432 = findViewById(R.id.btnRef432)
        btnRef444 = findViewById(R.id.btnRef444)
        btnResetCents = findViewById(R.id.btnResetCents)
        sbCents = findViewById(R.id.sbCents)

        btnIntervalMajorThird = findViewById(R.id.btnIntervalMajorThird)
        btnIntervalFourth = findViewById(R.id.btnIntervalFourth)
        btnIntervalFifth = findViewById(R.id.btnIntervalFifth)
        btnIntervalOctave = findViewById(R.id.btnIntervalOctave)

        tvVolumeDisplay = findViewById(R.id.tvVolumeDisplay)
        sbVolume = findViewById(R.id.sbVolume)
        btnSweepTest = findViewById(R.id.btnSweepTest)
        btnSavePreset = findViewById(R.id.btnSavePreset)

        updateThemeButtonLabel()
    }

    private fun loadPersistedState() {
        currentFrequency = prefs.getFloat(KEY_LAST_FREQ, 440.0f).toDouble()
        val waveName = prefs.getString(KEY_LAST_WAVE, WaveformType.SINE.name) ?: WaveformType.SINE.name
        activeWaveform = try {
            WaveformType.valueOf(waveName)
        } catch (e: Exception) {
            WaveformType.SINE
        }
        audioEngine.setFrequency(currentFrequency)
        audioEngine.setWaveform(activeWaveform)
        audioEngine.setVolume(currentVolume)
    }

    private fun setupAudioVisualizer() {
        audioEngine.onScopeBufferUpdate = { buffer ->
            visualizerView.setWaveformData(buffer, audioEngine.isPlaying())
        }
    }

    private fun setupListeners() {
        // Theme Switcher
        btnThemeToggle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val currentMode = prefs.getInt(KEY_THEME, 0)
            val nextMode = (currentMode + 1) % 3
            prefs.edit().putInt(KEY_THEME, nextMode).apply()
            recreate()
        }

        // Presets Activity
        btnOpenPresets.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val intent = Intent(this, PresetActivity::class.java)
            startActivityForResult(intent, REQUEST_PRESET)
        }

        // Master Audio Play / Mute
        btnMasterToggle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            toggleAudioPlayback()
        }

        // Waveforms
        btnWaveSine.setOnClickListener { selectWaveform(WaveformType.SINE) }
        btnWaveTriangle.setOnClickListener { selectWaveform(WaveformType.TRIANGLE) }
        btnWaveSawtooth.setOnClickListener { selectWaveform(WaveformType.SAWTOOTH) }
        btnWaveSquare.setOnClickListener { selectWaveform(WaveformType.SQUARE) }
        btnWaveNoise.setOnClickListener { selectWaveform(WaveformType.NOISE) }

        // Frequency SeekBar (Logarithmic)
        sbFrequencyLog.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isSweeping) {
                    currentFrequency = MusicalPitchEngine.progressToFrequency(progress)
                    applyFrequencyChange(currentFrequency, updateSlider = false)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Nudge Steppers
        btnNudgeMinus100.setOnClickListener { nudgeFrequency(-100.0) }
        btnNudgeMinus10.setOnClickListener { nudgeFrequency(-10.0) }
        btnNudgeMinus1.setOnClickListener { nudgeFrequency(-1.0) }
        btnNudgePlus1.setOnClickListener { nudgeFrequency(1.0) }
        btnNudgePlus10.setOnClickListener { nudgeFrequency(10.0) }
        btnNudgePlus100.setOnClickListener { nudgeFrequency(100.0) }

        // Direct Frequency Input Dialog
        btnEnterDirectFreq.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showDirectFrequencyDialog()
        }

        // Pitch & Cents
        btnOctaveDown.setOnClickListener { transposeSemitones(-12) }
        btnNoteDown.setOnClickListener { transposeSemitones(-1) }
        btnNoteUp.setOnClickListener { transposeSemitones(1) }
        btnOctaveUp.setOnClickListener { transposeSemitones(12) }

        // Reference Pitch
        btnRef440.setOnClickListener { setReferencePitch(440.0) }
        btnRef432.setOnClickListener { setReferencePitch(432.0) }
        btnRef444.setOnClickListener { setReferencePitch(444.0) }

        // Cents Detuning (-50..+50)
        sbCents.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentCents = progress - 50
                    applyCentsOffset()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnResetCents.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            currentCents = 0
            sbCents.progress = 50
            applyCentsOffset()
        }

        // Intervals
        btnIntervalMajorThird.setOnClickListener { transposeSemitones(4) }
        btnIntervalFourth.setOnClickListener { transposeSemitones(5) }
        btnIntervalFifth.setOnClickListener { transposeSemitones(7) }
        btnIntervalOctave.setOnClickListener { transposeSemitones(12) }

        // Volume / Attenuation Slider
        sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentVolume = (progress / 100.0f).coerceIn(0.0f, 1.0f)
                audioEngine.setVolume(currentVolume)
                updateVolumeLabel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Sweep Test
        btnSweepTest.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            toggleFrequencySweep()
        }

        // Save Preset Dialog
        btnSavePreset.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showSavePresetDialog()
        }
    }

    private fun toggleAudioPlayback() {
        if (audioEngine.isPlaying()) {
            audioEngine.stop()
            visualizerView.setMutedState(true)
            btnMasterToggle.text = getString(R.string.btn_play)
            tvTallyStatus.text = "MUTED"
            tvTallyStatus.setBackgroundResource(R.drawable.tally_badge_inactive)
        } else {
            audioEngine.setFrequency(currentFrequency)
            audioEngine.setWaveform(activeWaveform)
            audioEngine.setVolume(currentVolume)
            audioEngine.start()
            visualizerView.setMutedState(false)
            btnMasterToggle.text = getString(R.string.btn_stop)
            tvTallyStatus.text = "LIVE"
            tvTallyStatus.setBackgroundResource(R.drawable.tally_badge_active)
        }
    }

    private fun selectWaveform(type: WaveformType) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        activeWaveform = type
        audioEngine.setWaveform(type)
        prefs.edit().putString(KEY_LAST_WAVE, type.name).apply()
        updateWaveformButtons()
    }

    private fun updateWaveformButtons() {
        val buttons = mapOf(
            WaveformType.SINE to btnWaveSine,
            WaveformType.TRIANGLE to btnWaveTriangle,
            WaveformType.SAWTOOTH to btnWaveSawtooth,
            WaveformType.SQUARE to btnWaveSquare,
            WaveformType.NOISE to btnWaveNoise
        )

        for ((type, btn) in buttons) {
            if (type == activeWaveform) {
                btn.setBackgroundResource(R.drawable.btn_tactile_selector_active)
            } else {
                btn.setBackgroundResource(R.drawable.btn_tactile_selector_normal)
            }
        }
    }

    private fun nudgeFrequency(delta: Double) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val newFreq = (currentFrequency + delta).coerceIn(20.0, 20000.0)
        applyFrequencyChange(newFreq)
    }

    private fun transposeSemitones(semitones: Int) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val transposed = MusicalPitchEngine.transposeSemitones(currentFrequency, semitones)
        applyFrequencyChange(transposed)
    }

    private fun setReferencePitch(ref: Double) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        baseReferenceA4 = ref
        btnRef440.setTextColor(if (ref == 440.0) Color.parseColor("#FFB300") else Color.parseColor("#90A4AE"))
        btnRef432.setTextColor(if (ref == 432.0) Color.parseColor("#FFB300") else Color.parseColor("#90A4AE"))
        btnRef444.setTextColor(if (ref == 444.0) Color.parseColor("#FFB300") else Color.parseColor("#90A4AE"))
        updateNoteDisplay()
    }

    private fun applyCentsOffset() {
        // Recalculate note with offset
        val noteInfo = MusicalPitchEngine.frequencyToNote(currentFrequency, baseReferenceA4)
        val baseStd = noteInfo.standardFrequency
        val shifted = baseStd * Math.pow(2.0, currentCents.toDouble() / 1200.0)
        applyFrequencyChange(shifted, updateCentsSlider = false)
    }

    private fun applyFrequencyChange(
        freq: Double,
        updateSlider: Boolean = true,
        updateCentsSlider: Boolean = true
    ) {
        currentFrequency = freq.coerceIn(20.0, 20000.0)
        audioEngine.setFrequency(currentFrequency)
        prefs.edit().putFloat(KEY_LAST_FREQ, currentFrequency.toFloat()).apply()

        tvFrequencyDisplay.text = String.format("%.2f Hz", currentFrequency)

        if (updateSlider) {
            sbFrequencyLog.progress = MusicalPitchEngine.frequencyToProgress(currentFrequency)
        }

        updateNoteDisplay(updateCentsSlider)
    }

    private fun updateNoteDisplay(updateCentsSlider: Boolean = true) {
        val noteInfo = MusicalPitchEngine.frequencyToNote(currentFrequency, baseReferenceA4)
        tvNoteDisplay.text = "${noteInfo.noteName}${noteInfo.octave}"
        val sign = if (noteInfo.centsDeviation >= 0) "+" else ""
        tvCentsOffsetDisplay.text = "$sign${noteInfo.centsDeviation} ct"

        if (updateCentsSlider) {
            currentCents = noteInfo.centsDeviation
            sbCents.progress = (currentCents + 50).coerceIn(0, 100)
        }
    }

    private fun updateVolumeLabel() {
        val percent = (currentVolume * 100).toInt()
        val dB = if (currentVolume > 0) (20.0 * log10(currentVolume.toDouble())).toInt() else -96
        tvVolumeDisplay.text = "$dB dB ($percent%)"
    }

    private fun showDirectFrequencyDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "e.g. 440.0"
            setText(String.format("%.2f", currentFrequency))
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_enter_freq)
            .setView(input)
            .setPositiveButton("SET") { _, _ ->
                val entered = input.text.toString().toDoubleOrNull()
                if (entered != null && entered in 20.0..20000.0) {
                    applyFrequencyChange(entered)
                } else {
                    Toast.makeText(this, "Enter a valid frequency (20 - 20,000 Hz)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSavePresetDialog() {
        val input = EditText(this).apply {
            hint = "e.g. Studio Lead A4"
            setText("${tvNoteDisplay.text} ${activeWaveform.name}")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_save_preset)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val id = dbHelper.insertPreset(name, currentFrequency, activeWaveform, currentCents)
                    if (id > 0) {
                        Toast.makeText(this, "Preset saved: $name", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toggleFrequencySweep() {
        if (isSweeping) {
            isSweeping = false
            sweepRunnable?.let { mainHandler.removeCallbacks(it) }
            btnSweepTest.text = "20Hz-20kHz SWEEP"
            return
        }

        isSweeping = true
        btnSweepTest.text = "STOP SWEEP"
        if (!audioEngine.isPlaying()) {
            toggleAudioPlayback()
        }

        var sweepProgress = 0
        val totalSteps = 200
        val stepDelayMs = 40L // 8 seconds sweep

        sweepRunnable = object : Runnable {
            override fun run() {
                if (!isSweeping) return
                sweepProgress++
                if (sweepProgress > totalSteps) {
                    isSweeping = false
                    btnSweepTest.text = "20Hz-20kHz SWEEP"
                    return
                }

                val ratio = sweepProgress.toDouble() / totalSteps.toDouble()
                val sweepFreq = 20.0 * Math.pow(1000.0, ratio)
                applyFrequencyChange(sweepFreq)
                mainHandler.postDelayed(this, stepDelayMs)
            }
        }
        mainHandler.post(sweepRunnable!!)
    }

    private fun updateThemeButtonLabel() {
        val mode = prefs.getInt(KEY_THEME, 0)
        btnThemeToggle.text = when (mode) {
            1 -> getString(R.string.theme_light)
            2 -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_auto)
        }
    }

    private fun updateUIState() {
        updateWaveformButtons()
        applyFrequencyChange(currentFrequency)
        updateVolumeLabel()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PRESET && resultCode == RESULT_OK && data != null) {
            val freq = data.getDoubleExtra("freq", 440.0)
            val waveStr = data.getStringExtra("waveform") ?: WaveformType.SINE.name
            val wave = try {
                WaveformType.valueOf(waveStr)
            } catch (e: Exception) {
                WaveformType.SINE
            }
            selectWaveform(wave)
            applyFrequencyChange(freq)
            Toast.makeText(this, "Loaded: ${String.format("%.2f Hz", freq)} ($waveStr)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isSweeping = false
        sweepRunnable?.let { mainHandler.removeCallbacks(it) }
        audioEngine.stop()
    }
}
