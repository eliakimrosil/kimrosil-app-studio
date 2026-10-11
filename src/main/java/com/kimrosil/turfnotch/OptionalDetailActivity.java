package com.kimrosil.turfnotch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class DetailActivity extends Activity {

    private static final String PREFS_NAME = "turf-notch_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode";

    private EditText etBagWeight;
    private EditText etTestTrayCatch;
    private TextView tvTotalBagsNeeded;
    private TextView tvCatchCalibrationResult;
    private TextView tvSpreaderContext;
    private Button btnCalculateHopper;
    private Button btnSharePlan;
    private Button btnBack;

    private int spreaderIndex = 0;
    private String targetRateStr = "3.5";
    private String lawnAreaStr = "5000";
    private boolean isMetric = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2 || themeMode == 3) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNightMode = 0;
            try {
                systemNightMode = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {}
            if (systemNightMode == 2) {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            } else {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            }
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        initExtras();
        initViews();
        setupStatusBarTheme();
        performHopperCalculations();
    }

    private void initExtras() {
        Intent intent = getIntent();
        if (intent != null) {
            spreaderIndex = intent.getIntExtra("EXTRA_SPREADER_INDEX", 0);
            targetRateStr = intent.getStringExtra("EXTRA_TARGET_RATE");
            lawnAreaStr = intent.getStringExtra("EXTRA_LAWN_AREA");
            isMetric = intent.getBooleanExtra("EXTRA_IS_METRIC", false);
            if (TextUtils.isEmpty(targetRateStr)) targetRateStr = "3.5";
            if (TextUtils.isEmpty(lawnAreaStr)) lawnAreaStr = "5000";
        }
    }

    private void initViews() {
        tvSpreaderContext = findViewById(R.id.tvSpreaderContext);
        etBagWeight = findViewById(R.id.etBagWeight);
        etTestTrayCatch = findViewById(R.id.etTestTrayCatch);
        tvTotalBagsNeeded = findViewById(R.id.tvTotalBagsNeeded);
        tvCatchCalibrationResult = findViewById(R.id.tvCatchCalibrationResult);
        btnCalculateHopper = findViewById(R.id.btnCalculateHopper);
        btnSharePlan = findViewById(R.id.btnSharePlan);
        btnBack = findViewById(R.id.btnBack);

        String unitLabel = isMetric ? "g/m² | Area: " + lawnAreaStr + " m²" : "lbs/1,000 sq ft | Area: " + lawnAreaStr + " sq ft";
        tvSpreaderContext.setText(String.format(Locale.US,
                "Configured: Target Rate %s %s",
                targetRateStr, unitLabel));
        tvSpreaderContext.setContentDescription(tvSpreaderContext.getText());

        btnCalculateHopper.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            performHopperCalculations();
        });

        btnSharePlan.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            sharePlanSummary();
        });

        btnBack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            finish();
        });
    }

    private void performHopperCalculations() {
        double rate = 3.5;
        double area = 5000;
        double bagWeight = isMetric ? 15.0 : 32.0;

        try {
            rate = Double.parseDouble(targetRateStr);
            area = Double.parseDouble(lawnAreaStr);
        } catch (NumberFormatException ignored) {}

        String bagWeightInput = etBagWeight.getText().toString().trim();
        if (!TextUtils.isEmpty(bagWeightInput)) {
            try {
                bagWeight = Double.parseDouble(bagWeightInput);
            } catch (NumberFormatException e) {
                Toast.makeText(this, isMetric ? "Defaulting to 15 kg/bag" : "Defaulting to 32 lbs/bag", Toast.LENGTH_SHORT).show();
            }
        }

        double totalMaterial = isMetric ? ((area * rate) / 1000.0) : ((area / 1000.0) * rate);
        double bags = bagWeight > 0 ? (totalMaterial / bagWeight) : 1;
        String unitStr = isMetric ? "kg" : "lbs";

        tvTotalBagsNeeded.setText(String.format(Locale.US,
                "Total Required: %.1f %s (%.1f Bags @ %.0f %s/bag)",
                totalMaterial, unitStr, bags, bagWeight, unitStr));
        tvTotalBagsNeeded.setContentDescription(tvTotalBagsNeeded.getText());

        // Catch tray calibration (for 100 sq ft or 10 m² test strip)
        double expectedCatch = isMetric ? (rate * 0.01) : (rate / 10.0);
        String catchInput = etTestTrayCatch.getText().toString().trim();
        if (!TextUtils.isEmpty(catchInput)) {
            try {
                double catchVal = Double.parseDouble(catchInput);
                double variancePct = ((catchVal - expectedCatch) / expectedCatch) * 100.0;

                if (Math.abs(variancePct) <= 10.0) {
                    tvCatchCalibrationResult.setText(String.format(Locale.US,
                            "Catch Verified (%.2f %s caught vs %.2f %s expected): On Target (%.1f%% variance)",
                            catchVal, unitStr, expectedCatch, unitStr, variancePct));
                } else if (variancePct > 10.0) {
                    tvCatchCalibrationResult.setText(String.format(Locale.US,
                            "Catch Too Heavy (+%.1f%%): Adjust dial DOWN by 0.5 - 1 notch", variancePct));
                } else {
                    tvCatchCalibrationResult.setText(String.format(Locale.US,
                            "Catch Too Light (%.1f%%): Adjust dial UP by 0.5 - 1 notch", variancePct));
                }
            } catch (NumberFormatException e) {
                tvCatchCalibrationResult.setText("Enter valid tray weight to verify test catch");
            }
        } else {
            if (isMetric) {
                tvCatchCalibrationResult.setText(String.format(Locale.US,
                        "10 m² Test Catch Target: %.2f kg (%.0f grams)", expectedCatch, expectedCatch * 1000.0));
            } else {
                tvCatchCalibrationResult.setText(String.format(Locale.US,
                        "100 sq ft Test Catch Target: %.2f lbs (%.1f oz)", expectedCatch, expectedCatch * 16.0));
            }
        }
        tvCatchCalibrationResult.setContentDescription(tvCatchCalibrationResult.getText());
    }

    private void sharePlanSummary() {
        String summary = String.format(Locale.US,
                "TurfNotch Application Summary:\nTarget Rate: %s\nLawn Area: %s\n%s\nCalculated offline via TurfNotch.",
                targetRateStr, lawnAreaStr, tvTotalBagsNeeded.getText().toString());

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, summary);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "Share Yard Calibration Plan");
        startActivity(shareIntent);
    }

    private void setupStatusBarTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (nightMode == Configuration.UI_MODE_NIGHT_NO) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}