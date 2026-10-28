package com.kimrosil.clamppitch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Vibrator;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "ClampPitchPrefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_ACTIVE_TAB = "active_tab";

    // Header Views
    private TextView tvAppTitle;
    private Button btnThemeToggle;
    private ImageButton btnSettings;

    // Navigation Tabs
    private Button chipTabPitch;
    private Button chipTabTimer;
    private Button chipTabSquarer;

    // Sections
    private LinearLayout layoutSectionPitch;
    private LinearLayout layoutSectionTimer;
    private LinearLayout layoutSectionSquarer;

    // Section 1: Pitch & Pressure Views
    private TextView tvPressureStatus;
    private TextView tvClampCount;
    private TextView tvClampArrangement;
    private TextView tvClampPitch;
    private TextView tvClampPitchMetric;
    private TextView tvGluelinePsi;
    private TextView tvTargetPsiBadge;
    private TextView tvDistributionNote;

    private Spinner spnWoodSpecies;
    private Button btnLengthMinus;
    private EditText etPanelLength;
    private Button btnLengthPlus;
    private Button btnThickMinus;
    private EditText etBoardThickness;
    private Button btnThickPlus;
    private Spinner spnClampType;
    private Button btnCalculatePitch;

    // Section 2: Timer Views
    private TextView tvTimerStatusBadge;
    private TextView tvTimerCountdown;
    private TextView tvTimerCompensationNote;
    private Button btnTimerStart;
    private Button btnTimerReset;
    private Spinner spnGlueType;
    private Button btnTempMinus;
    private EditText etShopTemp;
    private Button btnTempPlus;

    // Section 3: Squarer Views
    private TextView tvSquareStatusBadge;
    private TextView tvSquareDiff;
    private TextView tvSquareDiffMetric;
    private TextView tvCorrectionMagnitude;
    private TextView tvCorrectionDirection;
    private TextView tvSquareInstruction;
    private EditText etDiagonalA;
    private EditText etDiagonalB;
    private Button btnCalculateSquare;

    // State Variables
    private int themeMode = 0; // 0: Auto, 1: Light, 2: Dark
    private int activeTab = 0;  // 0: Pitch, 1: Timer, 2: Squarer

    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private long timerMillisRemaining = 0;

    // Wood Species Data: Name, Janka Hardness, Target PSI Min, Target PSI Max
    private static class WoodSpecies {
        final String name;
        final int targetPsiMin;
        final int targetPsiMax;

        WoodSpecies(String name, int min, int max) {
            this.name = name;
            this.targetPsiMin = min;
            this.targetPsiMax = max;
        }

        @Override
        public String toString() {
            return name + " (" + targetPsiMin + "-" + targetPsiMax + " PSI)";
        }
    }

    private final WoodSpecies[] woodSpeciesList = new WoodSpecies[]{
            new WoodSpecies("Hard Maple / Rock Maple", 200, 250),
            new WoodSpecies("White Oak / Red Oak", 180, 230),
            new WoodSpecies("Black Walnut", 150, 200),
            new WoodSpecies("Black Cherry", 140, 190),
            new WoodSpecies("White Pine / Softwood", 100, 150),
            new WoodSpecies("Ash / Hickory", 200, 260),
            new WoodSpecies("Teak / Mahogany", 150, 200)
    };

    // Clamp Hardware: Name, Force (lbs)
    private static class ClampModel {
        final String name;
        final int forceLbs;

        ClampModel(String name, int forceLbs) {
            this.name = name;
            this.forceLbs = forceLbs;
        }

        @Override
        public String toString() {
            return name + " (~" + forceLbs + " lbs force)";
        }
    }

    private final ClampModel[] clampModels = new ClampModel[]{
            new ClampModel("Parallel / K-Body Clamp", 1000),
            new ClampModel("Heavy 3/4\" Pipe Clamp", 1200),
            new ClampModel("Standard F-Style Clamp", 600),
            new ClampModel("Aluminum Bar Clamp", 750),
            new ClampModel("Lightweight Quick-Grip", 300)
    };

    // Glue Formulations: Name, Base Open Time (Minutes at 70°F)
    private static class GlueType {
        final String name;
        final int baseOpenMinutes;

        GlueType(String name, int baseOpenMinutes) {
            this.name = name;
            this.baseOpenMinutes = baseOpenMinutes;
        }

        @Override
        public String toString() {
            return name + " (" + baseOpenMinutes + "m open)";
        }
    }

    private final GlueType[] glueTypes = new GlueType[]{
            new GlueType("Titebond II (Cross-Linking PVA)", 8),
            new GlueType("Titebond Original (Standard PVA)", 7),
            new GlueType("Titebond III (Extended Open PVA)", 10),
            new GlueType("Liquid Hide Glue (Slow Tack)", 20),
            new GlueType("Polyurethane Adhesive (Gorilla)", 15),
            new GlueType("5-Minute Fast Epoxy", 4)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadPreferences();
        applyCurrentTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupSpinners();
        setupListeners();

        // Restore active tab
        switchTab(activeTab);

        // Run initial calculations
        calculateGluelinePitch();
        updateTimerEstimate();
        calculateCaseSquare();
    }

    private void bindViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        chipTabPitch = findViewById(R.id.chipTabPitch);
        chipTabTimer = findViewById(R.id.chipTabTimer);
        chipTabSquarer = findViewById(R.id.chipTabSquarer);

        layoutSectionPitch = findViewById(R.id.layoutSectionPitch);
        layoutSectionTimer = findViewById(R.id.layoutSectionTimer);
        layoutSectionSquarer = findViewById(R.id.layoutSectionSquarer);

        // Pitch & Pressure
        tvPressureStatus = findViewById(R.id.tvPressureStatus);
        tvClampCount = findViewById(R.id.tvClampCount);
        tvClampArrangement = findViewById(R.id.tvClampArrangement);
        tvClampPitch = findViewById(R.id.tvClampPitch);
        tvClampPitchMetric = findViewById(R.id.tvClampPitchMetric);
        tvGluelinePsi = findViewById(R.id.tvGluelinePsi);
        tvTargetPsiBadge = findViewById(R.id.tvTargetPsiBadge);
        tvDistributionNote = findViewById(R.id.tvDistributionNote);

        spnWoodSpecies = findViewById(R.id.spnWoodSpecies);
        btnLengthMinus = findViewById(R.id.btnLengthMinus);
        etPanelLength = findViewById(R.id.etPanelLength);
        btnLengthPlus = findViewById(R.id.btnLengthPlus);
        btnThickMinus = findViewById(R.id.btnThickMinus);
        etBoardThickness = findViewById(R.id.etBoardThickness);
        btnThickPlus = findViewById(R.id.btnThickPlus);
        spnClampType = findViewById(R.id.spnClampType);
        btnCalculatePitch = findViewById(R.id.btnCalculatePitch);

        // Timer
        tvTimerStatusBadge = findViewById(R.id.tvTimerStatusBadge);
        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);
        tvTimerCompensationNote = findViewById(R.id.tvTimerCompensationNote);
        btnTimerStart = findViewById(R.id.btnTimerStart);
        btnTimerReset = findViewById(R.id.btnTimerReset);
        spnGlueType = findViewById(R.id.spnGlueType);
        btnTempMinus = findViewById(R.id.btnTempMinus);
        etShopTemp = findViewById(R.id.etShopTemp);
        btnTempPlus = findViewById(R.id.btnTempPlus);

        // Squarer
        tvSquareStatusBadge = findViewById(R.id.tvSquareStatusBadge);
        tvSquareDiff = findViewById(R.id.tvSquareDiff);
        tvSquareDiffMetric = findViewById(R.id.tvSquareDiffMetric);
        tvCorrectionMagnitude = findViewById(R.id.tvCorrectionMagnitude);
        tvCorrectionDirection = findViewById(R.id.tvCorrectionDirection);
        tvSquareInstruction = findViewById(R.id.tvSquareInstruction);
        etDiagonalA = findViewById(R.id.etDiagonalA);
        etDiagonalB = findViewById(R.id.etDiagonalB);
        btnCalculateSquare = findViewById(R.id.btnCalculateSquare);

        updateThemeToggleButton();
    }

    private void setupSpinners() {
        ArrayAdapter<WoodSpecies> woodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, woodSpeciesList);
        woodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnWoodSpecies.setAdapter(woodAdapter);

        ArrayAdapter<ClampModel> clampAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, clampModels);
        clampAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnClampType.setAdapter(clampAdapter);

        ArrayAdapter<GlueType> glueAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, glueTypes);
        glueAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnGlueType.setAdapter(glueAdapter);
    }

    private void setupListeners() {
        // Navigation Tabs
        chipTabPitch.setOnClickListener(v -> switchTab(0));
        chipTabTimer.setOnClickListener(v -> switchTab(1));
        chipTabSquarer.setOnClickListener(v -> switchTab(2));

        // Pitch Steppers
        btnLengthMinus.setOnClickListener(v -> adjustLength(-2.0));
        btnLengthPlus.setOnClickListener(v -> adjustLength(2.0));
        btnThickMinus.setOnClickListener(v -> adjustThickness(-0.125));
        btnThickPlus.setOnClickListener(v -> adjustThickness(0.125));
        btnCalculatePitch.setOnClickListener(v -> calculateGluelinePitch());

        // Timer Steppers & Action
        btnTempMinus.setOnClickListener(v -> adjustTemp(-5));
        btnTempPlus.setOnClickListener(v -> adjustTemp(5));
        btnTimerStart.setOnClickListener(v -> toggleTimer());
        btnTimerReset.setOnClickListener(v -> resetTimer());

        // Squarer
        btnCalculateSquare.setOnClickListener(v -> calculateCaseSquare());

        // Theme and Settings
        btnThemeToggle.setOnClickListener(v -> toggleTheme());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void switchTab(int tabIndex) {
        this.activeTab = tabIndex;
        layoutSectionPitch.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutSectionTimer.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        layoutSectionSquarer.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        // Update tab button styles
        updateTabStyles(chipTabPitch, tabIndex == 0);
        updateTabStyles(chipTabTimer, tabIndex == 1);
        updateTabStyles(chipTabSquarer, tabIndex == 2);

        saveState();
    }

    private void updateTabStyles(Button btn, boolean isActive) {
        if (isActive) {
            btn.setBackgroundResource(R.drawable.chip_active);
            btn.setTextColor(getColor(R.color.m3_on_primary));
        } else {
            btn.setBackgroundResource(R.drawable.chip_inactive);
            btn.setTextColor(getColor(R.color.m3_on_surface_variant));
        }
    }

    // =========================================================================
    // 1. Glueline Pitch & Pressure Calculation
    // =========================================================================
    private void adjustLength(double delta) {
        double current = 36.0;
        try {
            current = Double.parseDouble(etPanelLength.getText().toString().trim());
        } catch (NumberFormatException ignored) {}
        current = Math.max(6.0, Math.min(240.0, current + delta));
        etPanelLength.setText(String.format(Locale.US, "%.1f", current));
        calculateGluelinePitch();
    }

    private void adjustThickness(double delta) {
        double current = 0.75;
        try {
            current = Double.parseDouble(etBoardThickness.getText().toString().trim());
        } catch (NumberFormatException ignored) {}
        current = Math.max(0.25, Math.min(6.0, current + delta));
        etBoardThickness.setText(String.format(Locale.US, "%.3f", current));
        calculateGluelinePitch();
    }

    private void calculateGluelinePitch() {
        double length = 36.0;
        double thickness = 0.75;

        try {
            length = Double.parseDouble(etPanelLength.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        try {
            thickness = Double.parseDouble(etBoardThickness.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        length = Math.max(4.0, length);
        thickness = Math.max(0.2, thickness);

        WoodSpecies species = (WoodSpecies) spnWoodSpecies.getSelectedItem();
        if (species == null) species = woodSpeciesList[0];

        ClampModel clamp = (ClampModel) spnClampType.getSelectedItem();
        if (clamp == null) clamp = clampModels[0];

        // Glueline Area in square inches
        double areaSqIn = length * thickness;
        int targetPsiMid = (species.targetPsiMin + species.targetPsiMax) / 2;
        double totalRequiredLbs = areaSqIn * targetPsiMid;

        // Number of clamps needed
        int clampsByForce = (int) Math.ceil(totalRequiredLbs / clamp.forceLbs);

        // 45 degree stress cone spacing rule: max pitch should not exceed 10 inches for thin boards
        double maxPitchAllowed = Math.min(12.0, Math.max(6.0, thickness * 10.0));
        int clampsByPitch = (int) Math.ceil(length / maxPitchAllowed) + 1;

        int finalClampCount = Math.max(2, Math.max(clampsByForce, clampsByPitch));

        // Pitch between clamps
        double pitchInches = length / (finalClampCount - 1);
        double pitchCm = pitchInches * 2.54;

        // Actual PSI generated
        double totalActualForce = finalClampCount * clamp.forceLbs;
        double actualPsi = totalActualForce / areaSqIn;

        // Alternating clamp layout recommendation
        int topClamps = (finalClampCount + 1) / 2;
        int bottomClamps = finalClampCount - topClamps;

        // Display results
        tvClampCount.setText(String.format(Locale.US, "%d Clamps", finalClampCount));
        tvClampArrangement.setText(String.format(Locale.US, "%d Over / %d Under alternating", topClamps, bottomClamps));

        tvClampPitch.setText(String.format(Locale.US, "%.1f in", pitchInches));
        tvClampPitchMetric.setText(String.format(Locale.US, "%.1f cm center pitch", pitchCm));

        tvGluelinePsi.setText(String.format(Locale.US, "%.0f PSI", actualPsi));
        tvTargetPsiBadge.setText(String.format(Locale.US, "Target: %d-%d PSI", species.targetPsiMin, species.targetPsiMax));

        // Pressure Status Evaluation
        if (actualPsi < species.targetPsiMin) {
            tvPressureStatus.setText("LOW PRESSURE");
            tvPressureStatus.setTextColor(getColor(R.color.badge_warning));
            tvPressureStatus.setBackgroundResource(R.drawable.bg_badge_warning);
            tvDistributionNote.setText("Warning: Insufficient clamping force. Glue line may experience voids or joint failure.");
        } else if (actualPsi > (species.targetPsiMax + 120)) {
            tvPressureStatus.setText("HIGH PRESSURE");
            tvPressureStatus.setTextColor(getColor(R.color.badge_critical));
            tvPressureStatus.setBackgroundResource(R.drawable.bg_badge_critical);
            tvDistributionNote.setText("Caution: Excessive pressure risk. Excessive squeeze-out may starve glueline of adhesive.");
        } else {
            tvPressureStatus.setText("OPTIMAL PRESSURE");
            tvPressureStatus.setTextColor(getColor(R.color.badge_safe));
            tvPressureStatus.setBackgroundResource(R.drawable.bg_badge_safe);
            tvDistributionNote.setText("45° Pressure Cone Coverage: Complete overlap along entire glue line with zero starvation dead zones.");
        }
    }

    // =========================================================================
    // 2. Adhesive Open-Time Countdown Timer
    // =========================================================================
    private void adjustTemp(int delta) {
        int temp = 75;
        try {
            temp = Integer.parseInt(etShopTemp.getText().toString().trim());
        } catch (NumberFormatException ignored) {}
        temp = Math.max(50, Math.min(115, temp + delta));
        etShopTemp.setText(String.valueOf(temp));
        updateTimerEstimate();
    }

    private void updateTimerEstimate() {
        int temp = 75;
        try {
            temp = Integer.parseInt(etShopTemp.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        GlueType glue = (GlueType) spnGlueType.getSelectedItem();
        if (glue == null) glue = glueTypes[0];

        // Temperature compensation: every 5°F above 70°F reduces open time by 6%
        double factor = 1.0;
        if (temp > 70) {
            factor = Math.max(0.4, 1.0 - ((temp - 70) / 5.0 * 0.06));
        } else if (temp < 70) {
            factor = Math.min(1.5, 1.0 + ((70 - temp) / 5.0 * 0.05));
        }

        int adjustedMinutes = (int) Math.round(glue.baseOpenMinutes * factor);
        adjustedMinutes = Math.max(1, adjustedMinutes);

        tvTimerCompensationNote.setText(String.format(Locale.US, "%s Open Window: ~%d min at %d°F", glue.name, adjustedMinutes, temp));

        if (!isTimerRunning) {
            timerMillisRemaining = adjustedMinutes * 60 * 1000L;
            updateCountdownText(timerMillisRemaining);
        }
    }

    private void toggleTimer() {
        if (isTimerRunning) {
            pauseTimer();
        } else {
            startTimer();
        }
    }

    private void startTimer() {
        if (timerMillisRemaining <= 0) {
            updateTimerEstimate();
        }

        countDownTimer = new CountDownTimer(timerMillisRemaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerMillisRemaining = millisUntilFinished;
                updateCountdownText(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                timerMillisRemaining = 0;
                updateCountdownText(0);
                btnTimerStart.setText("Start Glue Timer");
                tvTimerStatusBadge.setText("EXPIRED - CLAMP NOW");
                tvTimerStatusBadge.setTextColor(getColor(R.color.badge_critical));
                tvTimerStatusBadge.setBackgroundResource(R.drawable.bg_badge_critical);

                // Haptic buzzer
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(new long[]{0, 400, 200, 400}, -1);
                }
                Toast.makeText(MainActivity.this, "Glue open time expired! Clamp panel immediately.", Toast.LENGTH_LONG).show();
            }
        }.start();

        isTimerRunning = true;
        btnTimerStart.setText("Pause Timer");
        tvTimerStatusBadge.setText("GLUING IN PROGRESS");
        tvTimerStatusBadge.setTextColor(getColor(R.color.badge_warning));
        tvTimerStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        btnTimerStart.setText("Resume Timer");
        tvTimerStatusBadge.setText("TIMER PAUSED");
    }

    private void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isTimerRunning = false;
        btnTimerStart.setText("Start Glue Timer");
        tvTimerStatusBadge.setText("READY TO APPLY");
        tvTimerStatusBadge.setTextColor(getColor(R.color.badge_safe));
        tvTimerStatusBadge.setBackgroundResource(R.drawable.bg_badge_safe);
        updateTimerEstimate();
    }

    private void updateCountdownText(long millis) {
        int seconds = (int) (millis / 1000);
        int minutes = seconds / 60;
        int remSec = seconds % 60;
        tvTimerCountdown.setText(String.format(Locale.US, "%02d:%02d", minutes, remSec));
    }

    // =========================================================================
    // 3. Cabinet & Case Squarer
    // =========================================================================
    private void calculateCaseSquare() {
        double diagA = 48.0;
        double diagB = 48.0;

        try {
            diagA = Double.parseDouble(etDiagonalA.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        try {
            diagB = Double.parseDouble(etDiagonalB.getText().toString().trim());
        } catch (NumberFormatException ignored) {}

        double diff = Math.abs(diagA - diagB);
        double diffMetric = diff * 25.4;
        double shiftCorrection = diff / 2.0;

        tvSquareDiff.setText(String.format(Locale.US, "%.3f in", diff));
        tvSquareDiffMetric.setText(String.format(Locale.US, "%.1f mm differential", diffMetric));
        tvCorrectionMagnitude.setText(String.format(Locale.US, "%.3f in", shiftCorrection));

        if (diff < 0.035) { // Within 1/32" tolerance
            tvSquareStatusBadge.setText("PERFECTLY SQUARE");
            tvSquareStatusBadge.setTextColor(getColor(R.color.badge_safe));
            tvSquareStatusBadge.setBackgroundResource(R.drawable.bg_badge_safe);
            tvCorrectionDirection.setText("Square corners verified");
            tvSquareInstruction.setText("Equal diagonal lengths confirm 90° square corners. Assembly is ready to pin or clamp.");
        } else {
            tvSquareStatusBadge.setText("OUT OF SQUARE");
            tvSquareStatusBadge.setTextColor(getColor(R.color.badge_warning));
            tvSquareStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);

            if (diagA > diagB) {
                tvCorrectionDirection.setText("Clamp Diagonal A-D");
                tvSquareInstruction.setText(String.format(Locale.US,
                        "Diagonal A-D is longer. Apply a diagonal clamp across Corners A & D and squeeze ~%.3f\" to pull into square.", shiftCorrection));
            } else {
                tvCorrectionDirection.setText("Clamp Diagonal B-C");
                tvSquareInstruction.setText(String.format(Locale.US,
                        "Diagonal B-C is longer. Apply a diagonal clamp across Corners B & C and squeeze ~%.3f\" to pull into square.", shiftCorrection));
            }
        }
    }

    // =========================================================================
    // 4. Working Day / Night / Auto Theme Switching
    // =========================================================================
    private void toggleTheme() {
        themeMode = (themeMode + 1) % 3;
        saveState();
        applyCurrentTheme();
        recreate();
    }

    private void applyCurrentTheme() {
        int nightMode;
        if (themeMode == 1) {
            setTheme(R.style.AppTheme_Light);
            nightMode = UiModeManager.MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            setTheme(R.style.AppTheme_Dark);
            nightMode = UiModeManager.MODE_NIGHT_YES;
        } else {
            setTheme(R.style.AppTheme);
            nightMode = UiModeManager.MODE_NIGHT_AUTO;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            if (uiModeManager != null) {
                uiModeManager.setApplicationNightMode(nightMode);
            }
        }

        Configuration config = getResources().getConfiguration();
        int currentUiNight = (themeMode == 2) ? Configuration.UI_MODE_NIGHT_YES :
                (themeMode == 1) ? Configuration.UI_MODE_NIGHT_NO :
                        Configuration.UI_MODE_NIGHT_UNDEFINED;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | currentUiNight;
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void updateThemeToggleButton() {
        if (btnThemeToggle != null) {
            if (themeMode == 1) {
                btnThemeToggle.setText("Theme: Light");
            } else if (themeMode == 2) {
                btnThemeToggle.setText("Theme: Dark");
            } else {
                btnThemeToggle.setText("Theme: Auto");
            }
        }
    }

    // =========================================================================
    // 5. Settings & About Modal Dialog
    // =========================================================================
    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("ClampPitch")
                .setMessage("Woodworking Glueline & Squarer Aide\nVersion 1.0.0\n\nKimRosil App Studio Portfolio freeware.\n100% Offline, on-device calculation.\n\nPrecise glueline pressure, open-time countdown, and case squarer for woodworkers.")
                .setPositiveButton("Close", null)
                .setNeutralButton("Reset Sample Data", (dialog, which) -> {
                    etPanelLength.setText("36.0");
                    etBoardThickness.setText("0.75");
                    etShopTemp.setText("75");
                    etDiagonalA.setText("48.25");
                    etDiagonalB.setText("48.00");
                    spnWoodSpecies.setSelection(0);
                    spnClampType.setSelection(0);
                    spnGlueType.setSelection(0);
                    calculateGluelinePitch();
                    updateTimerEstimate();
                    calculateCaseSquare();
                    Toast.makeText(MainActivity.this, "Reset to standard workshop defaults", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void saveState() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit()
                .putInt(KEY_THEME_MODE, themeMode)
                .putInt(KEY_ACTIVE_TAB, activeTab)
                .apply();
    }

    private void loadPreferences() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        themeMode = sp.getInt(KEY_THEME_MODE, 0);
        activeTab = sp.getInt(KEY_ACTIVE_TAB, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
