package com.kimrosil.dosecal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "dosecal_prefs";
    private static final String KEY_THEME = "theme_mode"; // 0: Auto, 1: Light, 2: Dark
    private static final String KEY_UNIT = "weight_unit"; // "KG" or "LBS"

    private TextView tvVolumeDisplay;
    private TextView tvSafetyBadge;
    private TextView tvSingleDoseMg;
    private TextView tvFrequencyTitle;
    private TextView tvDailyTotalMg;
    private SyringeGaugeView syringeView;
    private Button btnCopyReport;
    private Button btnSaveRecord;
    private ImageButton btnTheme;
    private ImageButton btnSettings;

    private LinearLayout layoutPresetChips;
    private Button btnWeightUnit;
    private EditText etWeight;
    private Button btnWeightMinus5;
    private Button btnWeightMinus1;
    private Button btnWeightPlus1;
    private Button btnWeightPlus5;

    private EditText etDoseRate;
    private Button btnInterval4h;
    private Button btnInterval6h;
    private Button btnInterval8h;
    private Button btnInterval12h;
    private Button btnInterval24h;

    private EditText etConcMg;
    private EditText etConcMl;

    private LinearLayout layoutRecentList;
    private TextView tvRecordCount;

    private DoseDatabaseHelper dbHelper;
    private List<DoseEngine.DrugPreset> presets;
    private DoseEngine.DrugPreset currentPreset;
    private DoseEngine.WeightUnit currentUnit = DoseEngine.WeightUnit.KG;
    private int selectedInterval = 6;
    private DoseEngine.DoseResult lastResult;
    private boolean isUpdatingUi = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyStoredTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DoseDatabaseHelper(this);
        presets = DoseEngine.getBuiltInPresets();
        currentPreset = presets.get(0); // Default: Acetaminophen

        initViews();
        setupPresets();
        setupListeners();
        loadRecentRecords();

        // Initial default calculation
        isUpdatingUi = true;
        etWeight.setText("15.0");
        loadPreset(currentPreset);
        isUpdatingUi = false;
        recalculate();
    }

    private void applyStoredTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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

    private void initViews() {
        tvVolumeDisplay = findViewById(R.id.tvVolumeDisplay);
        tvSafetyBadge = findViewById(R.id.tvSafetyBadge);
        tvSingleDoseMg = findViewById(R.id.tvSingleDoseMg);
        tvFrequencyTitle = findViewById(R.id.tvFrequencyTitle);
        tvDailyTotalMg = findViewById(R.id.tvDailyTotalMg);
        syringeView = findViewById(R.id.syringeView);
        btnCopyReport = findViewById(R.id.btnCopyReport);
        btnSaveRecord = findViewById(R.id.btnSaveRecord);
        btnTheme = findViewById(R.id.btnTheme);
        btnSettings = findViewById(R.id.btnSettings);

        layoutPresetChips = findViewById(R.id.layoutPresetChips);
        btnWeightUnit = findViewById(R.id.btnWeightUnit);
        etWeight = findViewById(R.id.etWeight);
        btnWeightMinus5 = findViewById(R.id.btnWeightMinus5);
        btnWeightMinus1 = findViewById(R.id.btnWeightMinus1);
        btnWeightPlus1 = findViewById(R.id.btnWeightPlus1);
        btnWeightPlus5 = findViewById(R.id.btnWeightPlus5);

        etDoseRate = findViewById(R.id.etDoseRate);
        btnInterval4h = findViewById(R.id.btnInterval4h);
        btnInterval6h = findViewById(R.id.btnInterval6h);
        btnInterval8h = findViewById(R.id.btnInterval8h);
        btnInterval12h = findViewById(R.id.btnInterval12h);
        btnInterval24h = findViewById(R.id.btnInterval24h);

        etConcMg = findViewById(R.id.etConcMg);
        etConcMl = findViewById(R.id.etConcMl);

        layoutRecentList = findViewById(R.id.layoutRecentList);
        tvRecordCount = findViewById(R.id.tvRecordCount);
    }

    private void setupPresets() {
        layoutPresetChips.removeAllViews();
        for (int i = 0; i < presets.size(); i++) {
            final DoseEngine.DrugPreset p = presets.get(i);
            Button btn = new Button(this);
            btn.setText(p.name);
            btn.setTextSize(12);
            btn.setContentDescription("Select clinical preset: " + p.name);
            btn.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(48)
            );
            lp.setMarginEnd(dpToPx(6));
            btn.setLayoutParams(lp);

            if (p == currentPreset) {
                btn.setBackgroundResource(R.drawable.bg_chip_selected);
                btn.setTextColor(0xFFFFFFFF);
            } else {
                btn.setBackgroundResource(R.drawable.bg_chip_unselected);
                btn.setTextColor(getColor(R.color.colorTextPrimary));
            }

            btn.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                currentPreset = p;
                loadPreset(p);
                refreshPresetChips();
                recalculate();
            });

            layoutPresetChips.addView(btn);
        }
    }

    private void refreshPresetChips() {
        int count = layoutPresetChips.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = layoutPresetChips.getChildAt(i);
            if (v instanceof Button) {
                Button b = (Button) v;
                DoseEngine.DrugPreset p = presets.get(i);
                if (p == currentPreset) {
                    b.setBackgroundResource(R.drawable.bg_chip_selected);
                    b.setTextColor(0xFFFFFFFF);
                } else {
                    b.setBackgroundResource(R.drawable.bg_chip_unselected);
                    b.setTextColor(getColor(R.color.colorTextPrimary));
                }
            }
        }
    }

    private void loadPreset(DoseEngine.DrugPreset p) {
        if (p == null) return;
        isUpdatingUi = true;
        etDoseRate.setText(String.format(Locale.US, "%.1f", p.defaultDoseRateMgPerKg));
        etConcMg.setText(String.format(Locale.US, "%.0f", p.defaultConcMg));
        etConcMl.setText(String.format(Locale.US, "%.0f", p.defaultConcMl));
        setInterval(p.defaultIntervalHours);
        isUpdatingUi = false;
    }

    private void setInterval(int hours) {
        selectedInterval = hours;
        btnInterval4h.setBackgroundResource(hours == 4 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnInterval4h.setTextColor(hours == 4 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnInterval6h.setBackgroundResource(hours == 6 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnInterval6h.setTextColor(hours == 6 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnInterval8h.setBackgroundResource(hours == 8 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnInterval8h.setTextColor(hours == 8 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnInterval12h.setBackgroundResource(hours == 12 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnInterval12h.setTextColor(hours == 12 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnInterval24h.setBackgroundResource(hours == 24 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnInterval24h.setTextColor(hours == 24 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        tvFrequencyTitle.setText(String.format(Locale.US, "Daily Total (q%dh)", selectedInterval));
    }

    private void setupListeners() {
        // Theme switcher button
        btnTheme.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int currentMode = prefs.getInt(KEY_THEME, 0);
            int nextMode = (currentMode + 1) % 3; // 0 (Auto) -> 1 (Light) -> 2 (Dark)
            setThemePreference(nextMode);
            String modeName = (nextMode == 1) ? "Light Mode" : (nextMode == 2 ? "Dark Mode" : "Auto (System)");
            Toast.makeText(MainActivity.this, "Theme: " + modeName, Toast.LENGTH_SHORT).show();
            recreate();
        });

        // Unit switch
        btnWeightUnit.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            double currentVal = parseDouble(etWeight.getText().toString(), 15.0);
            if (currentUnit == DoseEngine.WeightUnit.KG) {
                currentUnit = DoseEngine.WeightUnit.LBS;
                btnWeightUnit.setText("lbs");
                double lbs = DoseEngine.kgToLbs(currentVal);
                isUpdatingUi = true;
                etWeight.setText(String.format(Locale.US, "%.1f", lbs));
                isUpdatingUi = false;
            } else {
                currentUnit = DoseEngine.WeightUnit.KG;
                btnWeightUnit.setText("kg");
                double kg = DoseEngine.lbsToKg(currentVal);
                isUpdatingUi = true;
                etWeight.setText(String.format(Locale.US, "%.1f", kg));
                isUpdatingUi = false;
            }
            recalculate();
        });

        // Steppers
        btnWeightMinus5.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            adjustWeight(-5.0);
        });
        btnWeightMinus1.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            adjustWeight(-1.0);
        });
        btnWeightPlus1.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            adjustWeight(1.0);
        });
        btnWeightPlus5.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            adjustWeight(5.0);
        });

        // Intervals
        btnInterval4h.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setInterval(4);
            recalculate();
        });
        btnInterval6h.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setInterval(6);
            recalculate();
        });
        btnInterval8h.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setInterval(8);
            recalculate();
        });
        btnInterval12h.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setInterval(12);
            recalculate();
        });
        btnInterval24h.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setInterval(24);
            recalculate();
        });

        // TextWatcher for live recalculation
        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingUi) {
                    recalculate();
                }
            }
        };

        etWeight.addTextChangedListener(tw);
        etDoseRate.addTextChangedListener(tw);
        etConcMg.addTextChangedListener(tw);
        etConcMl.addTextChangedListener(tw);

        // Actions
        btnCopyReport.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            copyReportToClipboard();
        });
        btnSaveRecord.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            saveRecordToDatabase();
        });
        btnSettings.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            showSettingsDialog();
        });
    }

    private void adjustWeight(double delta) {
        double currentVal = parseDouble(etWeight.getText().toString(), 10.0);
        double newVal = Math.max(0.5, currentVal + delta);
        isUpdatingUi = true;
        etWeight.setText(String.format(Locale.US, "%.1f", newVal));
        isUpdatingUi = false;
        recalculate();
    }

    private void recalculate() {
        double rawWeight = parseDouble(etWeight.getText().toString(), 0);
        double weightKg = (currentUnit == DoseEngine.WeightUnit.LBS) ? DoseEngine.lbsToKg(rawWeight) : rawWeight;
        double doseRate = parseDouble(etDoseRate.getText().toString(), 0);
        double concMg = parseDouble(etConcMg.getText().toString(), 0);
        double concMl = parseDouble(etConcMl.getText().toString(), 0);

        lastResult = DoseEngine.calculate(weightKg, doseRate, concMg, concMl, selectedInterval, currentPreset);

        // Display results
        tvVolumeDisplay.setText(lastResult.getFormattedVolume());
        tvSingleDoseMg.setText(lastResult.getFormattedSingleDose());
        tvDailyTotalMg.setText(lastResult.getFormattedDailyTotal());

        if (lastResult.isExceedingDailyMax) {
            tvSafetyBadge.setText(R.string.status_warning);
            tvSafetyBadge.setBackgroundResource(R.drawable.bg_badge_warning);
        } else {
            tvSafetyBadge.setText(R.string.status_safe);
            tvSafetyBadge.setBackgroundResource(R.drawable.bg_badge_safe);
        }

        syringeView.setVolume(lastResult.singleDoseMl);
    }

    private void copyReportToClipboard() {
        if (lastResult == null) return;
        String drugName = currentPreset != null ? currentPreset.name : "Custom Formulation";
        String report = lastResult.getSummary(drugName);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("DoseCal Prescription Report", report);
        if (cm != null) {
            cm.setPrimaryClip(clip);
            Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveRecordToDatabase() {
        if (lastResult == null || lastResult.singleDoseMl <= 0) {
            Toast.makeText(this, "Enter valid dose parameters to save", Toast.LENGTH_SHORT).show();
            return;
        }
        String drugName = currentPreset != null ? currentPreset.name : "Custom Formulation";
        dbHelper.insertRecord(
                drugName,
                lastResult.weightKg,
                lastResult.doseRateMgPerKg,
                lastResult.singleDoseMl,
                lastResult.singleDoseMg,
                lastResult.intervalHours
        );
        Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show();
        loadRecentRecords();
    }

    private void loadRecentRecords() {
        layoutRecentList.removeAllViews();
        List<DoseDatabaseHelper.DoseRecord> records = dbHelper.getAllRecords();
        tvRecordCount.setText(String.format(Locale.US, "%d records", records.size()));

        int maxShow = Math.min(5, records.size());
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < maxShow; i++) {
            DoseDatabaseHelper.DoseRecord r = records.get(i);
            View itemView = inflater.inflate(R.layout.item_dose_record, layoutRecentList, false);

            TextView tvName = itemView.findViewById(R.id.tvItemDrugName);
            TextView tvDetails = itemView.findViewById(R.id.tvItemDetails);
            TextView tvVol = itemView.findViewById(R.id.tvItemVolume);

            tvName.setText(r.drugName);
            tvDetails.setText(String.format(Locale.US, "%.1f kg • %.1f mg/kg • q%dh",
                    r.weightKg, r.doseRate, r.intervalHours));
            tvVol.setText(String.format(Locale.US, "%.1f mL", r.volumeMl));

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                isUpdatingUi = true;
                currentUnit = DoseEngine.WeightUnit.KG;
                btnWeightUnit.setText("kg");
                etWeight.setText(String.format(Locale.US, "%.1f", r.weightKg));
                etDoseRate.setText(String.format(Locale.US, "%.1f", r.doseRate));
                setInterval(r.intervalHours);
                isUpdatingUi = false;
                recalculate();
                Toast.makeText(MainActivity.this, "Loaded " + r.drugName, Toast.LENGTH_SHORT).show();
            });

            layoutRecentList.addView(itemView);
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

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
            loadRecentRecords();
            Toast.makeText(MainActivity.this, "Saved records cleared", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setThemePreference(int mode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, mode).apply();
    }

    private double parseDouble(String str, double def) {
        if (str == null || str.trim().isEmpty()) return def;
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
