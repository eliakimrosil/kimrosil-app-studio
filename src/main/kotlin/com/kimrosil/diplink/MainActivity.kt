package com.kimrosil.diplink

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    // Header & Settings
    private lateinit var btnSettings: ImageButton

    // Tank Type Selectors
    private lateinit var btnTankDiesel: Button
    private lateinit var btnTankPrem91: Button
    private lateinit var btnTankPrem95: Button

    // Hero HUD Readout
    private lateinit var cardHeroVolume: View
    private lateinit var tvHeroTankBadge: TextView
    private lateinit var tvVolumeDisplay: TextView
    private lateinit var tvFillPercentage: TextView
    private lateinit var tvUllageHeadroom: TextView
    private lateinit var viewGaugeFill: View

    // Depth Input & Steppers
    private lateinit var etDepthInput: EditText
    private lateinit var tvMaxDepthLimit: TextView
    private lateinit var btnStepMinus10: Button
    private lateinit var btnStepMinus5: Button
    private lateinit var btnStepMinus1: Button
    private lateinit var btnStepPlus1: Button
    private lateinit var btnStepPlus5: Button
    private lateinit var btnStepPlus10: Button
    private lateinit var btnPreset25: Button
    private lateinit var btnPreset50: Button
    private lateinit var btnPreset75: Button
    private lateinit var btnPreset100: Button
    private lateinit var btnClearDepth: Button

    // Telemetry Table
    private lateinit var tvSpecModel: TextView
    private lateinit var tvSpecCapacity: TextView
    private lateinit var tvSpecDimensions: TextView
    private lateinit var tvSpecCurve: TextView

    // State
    private var currentTank: TankType = TankType.DIESEL
    private var currentDepth: Float = 0f
    private var isUpdatingText: Boolean = false
    private var hapticsEnabled: Boolean = true

    override fun attachBaseContext(newBase: Context) {
        val sp = newBase.getSharedPreferences("diplink_prefs", Context.MODE_PRIVATE)
        val mode = sp.getInt("theme_mode", 0) // 0: Auto, 1: Light, 2: Dark
        val config = Configuration(newBase.resources.configuration)

        if (mode == 1) {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        } else if (mode == 2) {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }

        val overrideContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(overrideContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("diplink_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("theme_mode", 0)
        setTheme(if (mode == 1) R.style.AppTheme_Light else if (mode == 2) R.style.AppTheme_Dark else R.style.AppTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
        val savedTank = prefs.getString("selected_tank", TankType.DIESEL.name) ?: TankType.DIESEL.name
        currentTank = try {
            TankType.valueOf(savedTank)
        } catch (e: Exception) {
            TankType.DIESEL
        }

        bindViews()
        setupListeners()
        updateTankSelectionUI()
        recalculateAndRender()
    }

    private fun bindViews() {
        btnSettings = findViewById(R.id.btnSettings)

        btnTankDiesel = findViewById(R.id.btnTankDiesel)
        btnTankPrem91 = findViewById(R.id.btnTankPrem91)
        btnTankPrem95 = findViewById(R.id.btnTankPrem95)

        cardHeroVolume = findViewById(R.id.cardHeroVolume)
        tvHeroTankBadge = findViewById(R.id.tvHeroTankBadge)
        tvVolumeDisplay = findViewById(R.id.tvVolumeDisplay)
        tvFillPercentage = findViewById(R.id.tvFillPercentage)
        tvUllageHeadroom = findViewById(R.id.tvUllageHeadroom)
        viewGaugeFill = findViewById(R.id.viewGaugeFill)

        etDepthInput = findViewById(R.id.etDepthInput)
        tvMaxDepthLimit = findViewById(R.id.tvMaxDepthLimit)
        btnStepMinus10 = findViewById(R.id.btnStepMinus10)
        btnStepMinus5 = findViewById(R.id.btnStepMinus5)
        btnStepMinus1 = findViewById(R.id.btnStepMinus1)
        btnStepPlus1 = findViewById(R.id.btnStepPlus1)
        btnStepPlus5 = findViewById(R.id.btnStepPlus5)
        btnStepPlus10 = findViewById(R.id.btnStepPlus10)
        btnPreset25 = findViewById(R.id.btnPreset25)
        btnPreset50 = findViewById(R.id.btnPreset50)
        btnPreset75 = findViewById(R.id.btnPreset75)
        btnPreset100 = findViewById(R.id.btnPreset100)
        btnClearDepth = findViewById(R.id.btnClearDepth)

        tvSpecModel = findViewById(R.id.tvSpecModel)
        tvSpecCapacity = findViewById(R.id.tvSpecCapacity)
        tvSpecDimensions = findViewById(R.id.tvSpecDimensions)
        tvSpecCurve = findViewById(R.id.tvSpecCurve)
    }

    private fun setupListeners() {
        btnSettings.setOnClickListener {
            triggerHaptic(it)
            showSettingsDialog()
        }

        btnTankDiesel.setOnClickListener {
            triggerHaptic(it)
            selectTank(TankType.DIESEL)
        }
        btnTankPrem91.setOnClickListener {
            triggerHaptic(it)
            selectTank(TankType.PREMIUM91)
        }
        btnTankPrem95.setOnClickListener {
            triggerHaptic(it)
            selectTank(TankType.PREMIUM95)
        }

        etDepthInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingText) return
                val text = s?.toString()?.trim() ?: ""
                val depth = text.toFloatOrNull() ?: 0f
                currentDepth = depth.coerceAtLeast(0f)
                recalculateAndRender()
            }
        })

        // Stepper Buttons
        btnStepMinus10.setOnClickListener { triggerHaptic(it); adjustDepth(-10f) }
        btnStepMinus5.setOnClickListener { triggerHaptic(it); adjustDepth(-5f) }
        btnStepMinus1.setOnClickListener { triggerHaptic(it); adjustDepth(-1f) }
        btnStepPlus1.setOnClickListener { triggerHaptic(it); adjustDepth(1f) }
        btnStepPlus5.setOnClickListener { triggerHaptic(it); adjustDepth(5f) }
        btnStepPlus10.setOnClickListener { triggerHaptic(it); adjustDepth(10f) }

        // Presets
        btnPreset25.setOnClickListener { triggerHaptic(it); setDepthPreset(0.25f) }
        btnPreset50.setOnClickListener { triggerHaptic(it); setDepthPreset(0.50f) }
        btnPreset75.setOnClickListener { triggerHaptic(it); setDepthPreset(0.75f) }
        btnPreset100.setOnClickListener { triggerHaptic(it); setDepthPreset(1.00f) }

        btnClearDepth.setOnClickListener {
            triggerHaptic(it)
            currentDepth = 0f
            updateDepthInputText(0f)
            recalculateAndRender()
        }
    }

    private fun selectTank(tank: TankType) {
        if (currentTank == tank) return
        currentTank = tank
        prefs.edit().putString("selected_tank", tank.name).apply()

        // Clamp depth if it exceeds new tank's maximum
        if (currentDepth > tank.maxDepthCm) {
            currentDepth = tank.maxDepthCm
            updateDepthInputText(currentDepth)
        }

        updateTankSelectionUI()
        recalculateAndRender()
    }

    private fun adjustDepth(delta: Float) {
        val newDepth = (currentDepth + delta).coerceIn(0f, currentTank.maxDepthCm)
        currentDepth = String.format(Locale.US, "%.1f", newDepth).toFloat()
        updateDepthInputText(currentDepth)
        recalculateAndRender()
    }

    private fun setDepthPreset(fraction: Float) {
        val targetDepth = (currentTank.maxDepthCm * fraction).coerceIn(0f, currentTank.maxDepthCm)
        currentDepth = String.format(Locale.US, "%.1f", targetDepth).toFloat()
        updateDepthInputText(currentDepth)
        recalculateAndRender()
    }

    private fun updateDepthInputText(depth: Float) {
        isUpdatingText = true
        if (depth <= 0f) {
            etDepthInput.setText("")
        } else if (depth == depth.toInt().toFloat()) {
            etDepthInput.setText(depth.toInt().toString())
        } else {
            etDepthInput.setText(String.format(Locale.US, "%.1f", depth))
        }
        etDepthInput.setSelection(etDepthInput.text.length)
        isUpdatingText = false
    }

    private fun updateTankSelectionUI() {
        btnTankDiesel.setBackgroundResource(if (currentTank == TankType.DIESEL) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        btnTankDiesel.setTextColor(getColor(if (currentTank == TankType.DIESEL) R.color.m3_on_primary else R.color.m3_on_surface))

        btnTankPrem91.setBackgroundResource(if (currentTank == TankType.PREMIUM91) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        btnTankPrem91.setTextColor(getColor(if (currentTank == TankType.PREMIUM91) R.color.m3_on_primary else R.color.m3_on_surface))

        btnTankPrem95.setBackgroundResource(if (currentTank == TankType.PREMIUM95) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        btnTankPrem95.setTextColor(getColor(if (currentTank == TankType.PREMIUM95) R.color.m3_on_primary else R.color.m3_on_surface))

        tvMaxDepthLimit.text = String.format(Locale.US, "Max: %.1f cm", currentTank.maxDepthCm)
        tvHeroTankBadge.text = "${currentTank.displayName.uppercase(Locale.US)} CALIBRATION READOUT"

        // Update Spec Table
        tvSpecModel.text = currentTank.tankModel
        tvSpecCapacity.text = String.format(Locale.US, "%,.0f Liters", currentTank.capacityLiters)
        tvSpecDimensions.text = String.format(Locale.US, "D = %.2fm × L = %.2fm", currentTank.diameterMeters, currentTank.lengthMeters)
        tvSpecCurve.text = if (currentTank == TankType.DIESEL) "242-Point Empirical Table" else "203-Point Empirical Table"
    }

    private fun recalculateAndRender() {
        val result = CalibrationEngine.calculate(currentTank, currentDepth)

        // Volume Readout
        tvVolumeDisplay.text = String.format(Locale.US, "%,.0f", result.volumeLiters)
        tvFillPercentage.text = String.format(Locale.US, "%.1f%% FULL", result.fillPercent)
        tvUllageHeadroom.text = String.format(Locale.US, "Ullage: %,.0f L", result.ullageLiters)

        // Visual Gauge Fill Bar
        viewGaugeFill.post {
            val parent = viewGaugeFill.parent as? ViewGroup ?: return@post
            val totalWidth = parent.width
            val fillFraction = (result.fillPercent / 100f).coerceIn(0f, 1f)
            val fillWidth = (totalWidth * fillFraction).toInt()

            val params = viewGaugeFill.layoutParams
            params.width = fillWidth
            viewGaugeFill.layoutParams = params

            // Colorize gauge fill according to fuel type
            try {
                val fuelColor = Color.parseColor(currentTank.fuelColorHex)
                val fillBg = GradientDrawable()
                fillBg.setColor(fuelColor)
                fillBg.cornerRadius = 16f
                viewGaugeFill.background = fillBg
            } catch (ignored: Exception) {}
        }
    }

    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_settings)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnThemeAuto = dialog.findViewById<Button>(R.id.btnThemeAuto)
        val btnThemeLight = dialog.findViewById<Button>(R.id.btnThemeLight)
        val btnThemeDark = dialog.findViewById<Button>(R.id.btnThemeDark)
        val switchHaptics = dialog.findViewById<Switch>(R.id.switchHaptics)
        val tvPrivacyPolicyLink = dialog.findViewById<TextView>(R.id.tvPrivacyPolicyLink)
        val btnCloseSettings = dialog.findViewById<Button>(R.id.btnCloseSettings)

        var selectedTheme = prefs.getInt("theme_mode", 0)

        fun updateThemeButtons() {
            btnThemeAuto.setBackgroundResource(if (selectedTheme == 0) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            btnThemeAuto.setTextColor(getColor(if (selectedTheme == 0) R.color.m3_on_primary else R.color.m3_on_surface))

            btnThemeLight.setBackgroundResource(if (selectedTheme == 1) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            btnThemeLight.setTextColor(getColor(if (selectedTheme == 1) R.color.m3_on_primary else R.color.m3_on_surface))

            btnThemeDark.setBackgroundResource(if (selectedTheme == 2) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            btnThemeDark.setTextColor(getColor(if (selectedTheme == 2) R.color.m3_on_primary else R.color.m3_on_surface))
        }

        updateThemeButtons()
        switchHaptics.isChecked = hapticsEnabled

        btnThemeAuto.setOnClickListener {
            selectedTheme = 0
            updateThemeButtons()
            prefs.edit().putInt("theme_mode", 0).apply()
            dialog.dismiss()
            recreate()
        }
        btnThemeLight.setOnClickListener {
            selectedTheme = 1
            updateThemeButtons()
            prefs.edit().putInt("theme_mode", 1).apply()
            dialog.dismiss()
            recreate()
        }
        btnThemeDark.setOnClickListener {
            selectedTheme = 2
            updateThemeButtons()
            prefs.edit().putInt("theme_mode", 2).apply()
            dialog.dismiss()
            recreate()
        }

        switchHaptics.setOnCheckedChangeListener { _, isChecked ->
            hapticsEnabled = isChecked
            prefs.edit().putBoolean("haptics_enabled", isChecked).apply()
        }

        tvPrivacyPolicyLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com/dip-link/privacy.html"))
            startActivity(intent)
        }

        btnCloseSettings.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun triggerHaptic(view: View) {
        if (hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}
