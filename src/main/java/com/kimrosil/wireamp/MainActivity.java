package com.kimrosil.wireamp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "wireamp_prefs";
    private static final String KEY_THEME = "theme_mode";
    private static final String PREF_GAUGE_INDEX = "gauge_idx";
    private static final String PREF_IS_COPPER = "is_copper";
    private static final String PREF_TEMP_INDEX = "temp_idx";
    private static final String PREF_AMBIENT = "ambient_f";
    private static final String PREF_BUNDLE = "bundle_count";

    private SharedPreferences prefs;
    private WireDatabaseHelper dbHelper;

    private List<WireAmpEngine.WireGauge> gaugeList;
    private int selectedGaugeIdx = 5; // Default: 4 AWG
    private boolean isCopper = true;
    private WireAmpEngine.TempRating selectedTemp = WireAmpEngine.TempRating.TEMP_75C;
    private int ambientTempF = 86; // 30°C standard base
    private int bundleCount = 3;   // Standard 3 current-carrying conductors

    // UI Elements
    private TextView tvDeratedAmpacity;
    private TextView tvBreakerBadge;
    private TextView tvBaseAmpacity;
    private TextView tvContinuousLimit;
    private TextView tvTempFactor;
    private TextView tvBundleFactor;
    private CableThermalView thermalView;

    private Button btnCopyCircuit;
    private Button btnSaveFeeder;

    private LinearLayout layoutGaugeChips;
    private Button btnMetalCopper;
    private Button btnMetalAluminum;
    private Button btnTemp60;
    private Button btnTemp75;
    private Button btnTemp90;

    private TextView tvAmbientLabel;
    private Button btnTempMinus10;
    private Button btnTempPlus10;

    private TextView tvConductorsLabel;
    private Button btnBundleMinus1;
    private Button btnBundlePlus1;

    private TextView tvRecordCount;
    private LinearLayout layoutRecentList;

    private ImageButton btnTheme;
    private ImageButton btnSettings;

    private WireAmpEngine.AmpResult currentResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyThemeFromPreferences();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new WireDatabaseHelper(this);
        gaugeList = WireAmpEngine.getStandardGauges();

        restorePreferences();
        initViews();
        setupListeners();
        buildGaugeChips();
        updateMetalButtonStyles();
        updateTempButtonStyles();
        calculateAndUpdate();
        loadRecentFeeders();
    }

    private void applyThemeFromPreferences() {
        int mode = prefs.getInt(KEY_THEME, 0);
        int nightMode;
        if (mode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
        }

        if (nightMode != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            Configuration config = getResources().getConfiguration();
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }
    }

    private void setThemePreference(int mode) {
        prefs.edit().putInt(KEY_THEME, mode).apply();
    }

    private void restorePreferences() {
        selectedGaugeIdx = prefs.getInt(PREF_GAUGE_INDEX, 5); // 4 AWG
        if (selectedGaugeIdx < 0 || selectedGaugeIdx >= gaugeList.size()) {
            selectedGaugeIdx = 5;
        }
        isCopper = prefs.getBoolean(PREF_IS_COPPER, true);

        int tempIdx = prefs.getInt(PREF_TEMP_INDEX, 1);
        if (tempIdx == 0) selectedTemp = WireAmpEngine.TempRating.TEMP_60C;
        else if (tempIdx == 2) selectedTemp = WireAmpEngine.TempRating.TEMP_90C;
        else selectedTemp = WireAmpEngine.TempRating.TEMP_75C;

        ambientTempF = prefs.getInt(PREF_AMBIENT, 86);
        bundleCount = prefs.getInt(PREF_BUNDLE, 3);
    }

    private void savePreferences() {
        int tempIdx = (selectedTemp == WireAmpEngine.TempRating.TEMP_60C) ? 0 :
                (selectedTemp == WireAmpEngine.TempRating.TEMP_90C ? 2 : 1);
        prefs.edit()
                .putInt(PREF_GAUGE_INDEX, selectedGaugeIdx)
                .putBoolean(PREF_IS_COPPER, isCopper)
                .putInt(PREF_TEMP_INDEX, tempIdx)
                .putInt(PREF_AMBIENT, ambientTempF)
                .putInt(PREF_BUNDLE, bundleCount)
                .apply();
    }

    private void initViews() {
        btnTheme = findViewById(R.id.btnTheme);
        btnSettings = findViewById(R.id.btnSettings);

        tvDeratedAmpacity = findViewById(R.id.tvDeratedAmpacity);
        tvBreakerBadge = findViewById(R.id.tvBreakerBadge);
        tvBaseAmpacity = findViewById(R.id.tvBaseAmpacity);
        tvContinuousLimit = findViewById(R.id.tvContinuousLimit);
        tvTempFactor = findViewById(R.id.tvTempFactor);
        tvBundleFactor = findViewById(R.id.tvBundleFactor);
        thermalView = findViewById(R.id.thermalView);

        btnCopyCircuit = findViewById(R.id.btnCopyCircuit);
        btnSaveFeeder = findViewById(R.id.btnSaveFeeder);

        layoutGaugeChips = findViewById(R.id.layoutGaugeChips);
        btnMetalCopper = findViewById(R.id.btnMetalCopper);
        btnMetalAluminum = findViewById(R.id.btnMetalAluminum);
        btnTemp60 = findViewById(R.id.btnTemp60);
        btnTemp75 = findViewById(R.id.btnTemp75);
        btnTemp90 = findViewById(R.id.btnTemp90);

        tvAmbientLabel = findViewById(R.id.tvAmbientLabel);
        btnTempMinus10 = findViewById(R.id.btnTempMinus10);
        btnTempPlus10 = findViewById(R.id.btnTempPlus10);

        tvConductorsLabel = findViewById(R.id.tvConductorsLabel);
        btnBundleMinus1 = findViewById(R.id.btnBundleMinus1);
        btnBundlePlus1 = findViewById(R.id.btnBundlePlus1);

        tvRecordCount = findViewById(R.id.tvRecordCount);
        layoutRecentList = findViewById(R.id.layoutRecentList);
    }

    private void setupListeners() {
        btnTheme.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            int currentMode = prefs.getInt(KEY_THEME, 0);
            int nextMode = (currentMode + 1) % 3;
            setThemePreference(nextMode);
            String modeName = (nextMode == 1) ? "Light Mode" : (nextMode == 2 ? "Dark Mode" : "Auto (System)");
            Toast.makeText(this, "Theme: " + modeName, Toast.LENGTH_SHORT).show();
            recreate();
        });

        btnSettings.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            showSettingsDialog();
        });

        btnMetalCopper.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            isCopper = true;
            updateMetalButtonStyles();
            calculateAndUpdate();
        });

        btnMetalAluminum.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            isCopper = false;
            // 14 AWG aluminum is not permitted in NEC Table 310.16
            if (selectedGaugeIdx == 0) {
                selectedGaugeIdx = 1; // shift to 12 AWG
                buildGaugeChips();
                Toast.makeText(this, "14 AWG aluminum not permitted under NEC", Toast.LENGTH_SHORT).show();
            }
            updateMetalButtonStyles();
            calculateAndUpdate();
        });

        btnTemp60.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            selectedTemp = WireAmpEngine.TempRating.TEMP_60C;
            updateTempButtonStyles();
            calculateAndUpdate();
        });

        btnTemp75.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            selectedTemp = WireAmpEngine.TempRating.TEMP_75C;
            updateTempButtonStyles();
            calculateAndUpdate();
        });

        btnTemp90.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            selectedTemp = WireAmpEngine.TempRating.TEMP_90C;
            updateTempButtonStyles();
            calculateAndUpdate();
        });

        btnTempMinus10.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (ambientTempF > 56) {
                ambientTempF -= 10;
                calculateAndUpdate();
            }
        });

        btnTempPlus10.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (ambientTempF < 140) {
                ambientTempF += 10;
                calculateAndUpdate();
            }
        });

        btnBundleMinus1.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (bundleCount > 1) {
                bundleCount--;
                calculateAndUpdate();
            }
        });

        btnBundlePlus1.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (bundleCount < 40) {
                bundleCount++;
                calculateAndUpdate();
            }
        });

        btnCopyCircuit.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (currentResult != null) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("WireAmp Spec", currentResult.getSummary());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSaveFeeder.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (currentResult != null) {
                dbHelper.insertRun(
                        currentResult.gauge.name,
                        isCopper ? "Cu" : "Al",
                        selectedTemp.name(),
                        ambientTempF,
                        bundleCount,
                        currentResult.deratedAmpacity,
                        currentResult.standardBreakerSize
                );
                Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show();
                loadRecentFeeders();
            }
        });
    }

    private void buildGaugeChips() {
        layoutGaugeChips.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int minH = Math.round(48 * density);
        int minW = Math.round(58 * density);
        int padH = Math.round(12 * density);
        int margin = Math.round(4 * density);

        for (int i = 0; i < gaugeList.size(); i++) {
            final int index = i;
            WireAmpEngine.WireGauge gauge = gaugeList.get(i);

            Button chip = new Button(this);
            chip.setId(View.generateViewId());
            chip.setText(gauge.name);
            chip.setTextSize(12);
            chip.setContentDescription("Select wire gauge " + gauge.name);
            chip.setMinimumHeight(minH);
            chip.setMinimumWidth(minW);
            chip.setPadding(padH, 0, padH, 0);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    minH
            );
            lp.setMargins(margin, 0, margin, 0);
            chip.setLayoutParams(lp);

            if (i == selectedGaugeIdx) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected);
                chip.setTextColor(0xFFFFFFFF);
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected);
                chip.setTextColor(getColor(R.color.colorTextPrimary));
            }

            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (!isCopper && index == 0) {
                    Toast.makeText(this, "14 AWG Aluminum not permitted", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedGaugeIdx = index;
                buildGaugeChips();
                calculateAndUpdate();
            });

            layoutGaugeChips.addView(chip);
        }
    }

    private void updateMetalButtonStyles() {
        if (isCopper) {
            btnMetalCopper.setBackgroundResource(R.drawable.bg_chip_selected);
            btnMetalCopper.setTextColor(0xFFFFFFFF);
            btnMetalAluminum.setBackgroundResource(R.drawable.bg_chip_unselected);
            btnMetalAluminum.setTextColor(getColor(R.color.colorTextPrimary));
        } else {
            btnMetalAluminum.setBackgroundResource(R.drawable.bg_chip_selected);
            btnMetalAluminum.setTextColor(0xFFFFFFFF);
            btnMetalCopper.setBackgroundResource(R.drawable.bg_chip_unselected);
            btnMetalCopper.setTextColor(getColor(R.color.colorTextPrimary));
        }
    }

    private void updateTempButtonStyles() {
        btnTemp60.setBackgroundResource(selectedTemp == WireAmpEngine.TempRating.TEMP_60C ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnTemp60.setTextColor(selectedTemp == WireAmpEngine.TempRating.TEMP_60C ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnTemp75.setBackgroundResource(selectedTemp == WireAmpEngine.TempRating.TEMP_75C ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnTemp75.setTextColor(selectedTemp == WireAmpEngine.TempRating.TEMP_75C ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnTemp90.setBackgroundResource(selectedTemp == WireAmpEngine.TempRating.TEMP_90C ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnTemp90.setTextColor(selectedTemp == WireAmpEngine.TempRating.TEMP_90C ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));
    }

    private void calculateAndUpdate() {
        if (selectedGaugeIdx < 0 || selectedGaugeIdx >= gaugeList.size()) {
            selectedGaugeIdx = 0;
        }
        WireAmpEngine.WireGauge gauge = gaugeList.get(selectedGaugeIdx);
        WireAmpEngine.Metal metal = isCopper ? WireAmpEngine.Metal.COPPER : WireAmpEngine.Metal.ALUMINUM;

        currentResult = WireAmpEngine.calculate(gauge, metal, selectedTemp, ambientTempF, bundleCount);

        tvDeratedAmpacity.setText(String.format(Locale.US, "%.1f A", currentResult.deratedAmpacity));
        tvBreakerBadge.setText(String.format(Locale.US, "Max Breaker: %d A", currentResult.standardBreakerSize));
        tvBaseAmpacity.setText(String.format(Locale.US, "%d A", currentResult.baseAmpacity));
        tvContinuousLimit.setText(String.format(Locale.US, "%.1f A", currentResult.continuousLoadLimit));
        tvTempFactor.setText(String.format(Locale.US, "Temp Factor (FT): %.2f", currentResult.tempFactor));
        tvBundleFactor.setText(String.format(Locale.US, "Bundle Factor (FB): %.2f", currentResult.bundleFactor));

        // Stepper labels
        int ambientC = (int) Math.round((ambientTempF - 32) * 5.0 / 9.0);
        tvAmbientLabel.setText(String.format(Locale.US, "Ambient Temp: %d°F (%d°C)", ambientTempF, ambientC));
        tvConductorsLabel.setText(String.format(Locale.US, "Raceway Bundle: %d Conductors", bundleCount));

        // Canvas graphic update
        double deratingRatio = (currentResult.baseAmpacity > 0) ?
                (currentResult.deratedAmpacity / (double) currentResult.baseAmpacity) * 100.0 : 0.0;
        thermalView.updateThermal(deratingRatio, bundleCount, isCopper);

        savePreferences();
    }

    private void loadRecentFeeders() {
        List<WireDatabaseHelper.FeederRecord> records = dbHelper.getAllRuns();
        layoutRecentList.removeAllViews();
        tvRecordCount.setText(records.size() + (records.size() == 1 ? " feeder" : " feeders"));

        if (records.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No saved feeders yet. Tap 'Save Feeder Run' to store offline calculations.");
            emptyTv.setTextColor(getColor(R.color.colorTextSecondary));
            emptyTv.setTextSize(12);
            emptyTv.setPadding(0, 8, 0, 8);
            layoutRecentList.addView(emptyTv);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, HH:mm", Locale.US);
        int maxShow = Math.min(records.size(), 6);

        for (int i = 0; i < maxShow; i++) {
            WireDatabaseHelper.FeederRecord record = records.get(i);
            View itemView = inflater.inflate(R.layout.item_feeder_run, layoutRecentList, false);

            TextView tvItemTitle = itemView.findViewById(R.id.tvItemTitle);
            TextView tvItemAmpacity = itemView.findViewById(R.id.tvItemAmpacity);
            TextView tvItemDetails = itemView.findViewById(R.id.tvItemDetails);
            TextView tvItemDate = itemView.findViewById(R.id.tvItemDate);

            String tempClean = record.tempRating.replace("TEMP_", "");
            tvItemTitle.setText(record.gaugeName + " " + record.metal + " • " + tempClean);
            tvItemAmpacity.setText(String.format(Locale.US, "%.1f A", record.deratedAmps));
            tvItemDetails.setText(String.format(Locale.US, "Amb: %d°F • Bundle: %d • Breaker: %d A",
                    record.ambientF, record.conductorCount, record.breakerSize));
            tvItemDate.setText(sdf.format(new Date(record.createdAt)));

            // Restore configuration on click
            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                for (int g = 0; g < gaugeList.size(); g++) {
                    if (gaugeList.get(g).name.equalsIgnoreCase(record.gaugeName)) {
                        selectedGaugeIdx = g;
                        break;
                    }
                }
                isCopper = "Cu".equalsIgnoreCase(record.metal);
                if (record.tempRating.contains("60")) selectedTemp = WireAmpEngine.TempRating.TEMP_60C;
                else if (record.tempRating.contains("90")) selectedTemp = WireAmpEngine.TempRating.TEMP_90C;
                else selectedTemp = WireAmpEngine.TempRating.TEMP_75C;

                ambientTempF = record.ambientF;
                bundleCount = record.conductorCount;

                buildGaugeChips();
                updateMetalButtonStyles();
                updateTempButtonStyles();
                calculateAndUpdate();
                Toast.makeText(this, "Restored " + record.gaugeName + " feeder run", Toast.LENGTH_SHORT).show();
            });

            layoutRecentList.addView(itemView);
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnAuto = dialogView.findViewById(R.id.btnThemeAuto);
        Button btnLight = dialogView.findViewById(R.id.btnThemeLight);
        Button btnDark = dialogView.findViewById(R.id.btnThemeDark);
        Button btnClear = dialogView.findViewById(R.id.btnClearHistory);
        Button btnClose = dialogView.findViewById(R.id.btnCloseSettings);

        btnAuto.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setThemePreference(0);
            dialog.dismiss();
            recreate();
        });

        btnLight.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setThemePreference(1);
            dialog.dismiss();
            recreate();
        });

        btnDark.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setThemePreference(2);
            dialog.dismiss();
            recreate();
        });

        btnClear.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            dbHelper.clearAll();
            loadRecentFeeders();
            Toast.makeText(this, "Saved feeders cleared", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            dialog.dismiss();
        });

        dialog.show();
    }
}
