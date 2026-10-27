package com.kimrosil.tapergauge;

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

    private static final String PREFS_NAME = "tapergauge_prefs";
    private static final String KEY_THEME = "theme_mode"; // 0: Auto, 1: Light, 2: Dark
    private static final String KEY_UNIT = "unit_system"; // 0: INCHES, 1: MM

    private TextView tvCompoundAngle;
    private TextView tvTpfBadge;
    private TextView tvIncludedAngle;
    private TextView tvTailstockOffset;
    private TaperCrossSectionView taperView;
    private Button btnCopySpec;
    private Button btnSaveSetup;
    private ImageButton btnTheme;
    private ImageButton btnSettings;

    private LinearLayout layoutPresetChips;
    private Button btnUnitToggle;
    private TextView tvMajorDiaLabel;
    private EditText etMajorD;
    private Button btnMajorMinus01;
    private Button btnMajorPlus01;

    private TextView tvMinorDiaLabel;
    private EditText etMinorD;
    private Button btnMinorMinus01;
    private Button btnMinorPlus01;

    private TextView tvTaperLengthLabel;
    private EditText etTaperL;
    private TextView tvTotalLengthLabel;
    private EditText etTotalL;

    private LinearLayout layoutRecentList;
    private TextView tvRecordCount;

    private TaperDatabaseHelper dbHelper;
    private List<TaperEngine.TaperPreset> presets;
    private TaperEngine.TaperPreset currentPreset;
    private TaperEngine.UnitSystem currentUnit = TaperEngine.UnitSystem.INCHES;
    private TaperEngine.TaperResult lastResult;
    private boolean isUpdatingUi = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyStoredTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new TaperDatabaseHelper(this);
        presets = TaperEngine.getBuiltInPresets();
        currentPreset = presets.get(1); // Default: Morse #2

        initViews();
        setupPresets();
        setupListeners();
        loadRecentSetups();

        // Initial default calculation
        isUpdatingUi = true;
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
        tvCompoundAngle = findViewById(R.id.tvCompoundAngle);
        tvTpfBadge = findViewById(R.id.tvTpfBadge);
        tvIncludedAngle = findViewById(R.id.tvIncludedAngle);
        tvTailstockOffset = findViewById(R.id.tvTailstockOffset);
        taperView = findViewById(R.id.taperView);
        btnCopySpec = findViewById(R.id.btnCopySpec);
        btnSaveSetup = findViewById(R.id.btnSaveSetup);
        btnTheme = findViewById(R.id.btnTheme);
        btnSettings = findViewById(R.id.btnSettings);

        layoutPresetChips = findViewById(R.id.layoutPresetChips);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        tvMajorDiaLabel = findViewById(R.id.tvMajorDiaLabel);
        etMajorD = findViewById(R.id.etMajorD);
        btnMajorMinus01 = findViewById(R.id.btnMajorMinus01);
        btnMajorPlus01 = findViewById(R.id.btnMajorPlus01);

        tvMinorDiaLabel = findViewById(R.id.tvMinorDiaLabel);
        etMinorD = findViewById(R.id.etMinorD);
        btnMinorMinus01 = findViewById(R.id.btnMinorMinus01);
        btnMinorPlus01 = findViewById(R.id.btnMinorPlus01);

        tvTaperLengthLabel = findViewById(R.id.tvTaperLengthLabel);
        etTaperL = findViewById(R.id.etTaperL);
        tvTotalLengthLabel = findViewById(R.id.tvTotalLengthLabel);
        etTotalL = findViewById(R.id.etTotalL);

        layoutRecentList = findViewById(R.id.layoutRecentList);
        tvRecordCount = findViewById(R.id.tvRecordCount);
    }

    private void setupPresets() {
        layoutPresetChips.removeAllViews();
        for (int i = 0; i < presets.size(); i++) {
            final TaperEngine.TaperPreset p = presets.get(i);
            Button btn = new Button(this);
            btn.setText(p.name);
            btn.setTextSize(12);
            btn.setContentDescription("Select taper preset: " + p.name);
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
                TaperEngine.TaperPreset p = presets.get(i);
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

    private void loadPreset(TaperEngine.TaperPreset p) {
        if (p == null) return;
        isUpdatingUi = true;
        double mult = (currentUnit == TaperEngine.UnitSystem.MM) ? TaperEngine.MM_PER_INCH : 1.0;
        etMajorD.setText(String.format(Locale.US, "%.4f", p.majorDiaInches * mult));
        etMinorD.setText(String.format(Locale.US, "%.4f", p.minorDiaInches * mult));
        etTaperL.setText(String.format(Locale.US, "%.4f", p.lengthInches * mult));
        etTotalL.setText(String.format(Locale.US, "%.4f", p.lengthInches * mult * 1.5));
        isUpdatingUi = false;
    }

    private void setupListeners() {
        // Theme switcher
        btnTheme.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int currentMode = prefs.getInt(KEY_THEME, 0);
            int nextMode = (currentMode + 1) % 3;
            setThemePreference(nextMode);
            String modeName = (nextMode == 1) ? "Light Mode" : (nextMode == 2 ? "Dark Mode" : "Auto (System)");
            Toast.makeText(MainActivity.this, "Theme: " + modeName, Toast.LENGTH_SHORT).show();
            recreate();
        });

        // Unit Switcher
        btnUnitToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            double dMaj = parseDouble(etMajorD.getText().toString(), 1.0);
            double dMin = parseDouble(etMinorD.getText().toString(), 0.8);
            double lTap = parseDouble(etTaperL.getText().toString(), 2.0);
            double lTot = parseDouble(etTotalL.getText().toString(), 3.0);

            if (currentUnit == TaperEngine.UnitSystem.INCHES) {
                currentUnit = TaperEngine.UnitSystem.MM;
                btnUnitToggle.setText("Metric (mm)");
                tvMajorDiaLabel.setText("Major Diameter (D) - mm");
                tvMinorDiaLabel.setText("Minor Diameter (d) - mm");
                tvTaperLengthLabel.setText("Taper Length (l) - mm");
                tvTotalLengthLabel.setText("Total Length (L) - mm");

                isUpdatingUi = true;
                etMajorD.setText(String.format(Locale.US, "%.3f", dMaj * TaperEngine.MM_PER_INCH));
                etMinorD.setText(String.format(Locale.US, "%.3f", dMin * TaperEngine.MM_PER_INCH));
                etTaperL.setText(String.format(Locale.US, "%.3f", lTap * TaperEngine.MM_PER_INCH));
                etTotalL.setText(String.format(Locale.US, "%.3f", lTot * TaperEngine.MM_PER_INCH));
                isUpdatingUi = false;
            } else {
                currentUnit = TaperEngine.UnitSystem.INCHES;
                btnUnitToggle.setText("Imperial (in)");
                tvMajorDiaLabel.setText("Major Diameter (D) - in");
                tvMinorDiaLabel.setText("Minor Diameter (d) - in");
                tvTaperLengthLabel.setText("Taper Length (l) - in");
                tvTotalLengthLabel.setText("Total Length (L) - in");

                isUpdatingUi = true;
                etMajorD.setText(String.format(Locale.US, "%.4f", dMaj / TaperEngine.MM_PER_INCH));
                etMinorD.setText(String.format(Locale.US, "%.4f", dMin / TaperEngine.MM_PER_INCH));
                etTaperL.setText(String.format(Locale.US, "%.4f", lTap / TaperEngine.MM_PER_INCH));
                etTotalL.setText(String.format(Locale.US, "%.4f", lTot / TaperEngine.MM_PER_INCH));
                isUpdatingUi = false;
            }
            recalculate();
        });

        // Steppers
        btnMajorMinus01.setOnClickListener(v -> adjustMajorDia(-0.1));
        btnMajorPlus01.setOnClickListener(v -> adjustMajorDia(0.1));
        btnMinorMinus01.setOnClickListener(v -> adjustMinorDia(-0.1));
        btnMinorPlus01.setOnClickListener(v -> adjustMinorDia(0.1));

        // Live Text Watcher
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

        etMajorD.addTextChangedListener(tw);
        etMinorD.addTextChangedListener(tw);
        etTaperL.addTextChangedListener(tw);
        etTotalL.addTextChangedListener(tw);

        // Actions
        btnCopySpec.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            copySpecToClipboard();
        });
        btnSaveSetup.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            saveSetupToDatabase();
        });
        btnSettings.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            showSettingsDialog();
        });
    }

    private void adjustMajorDia(double delta) {
        double currentVal = parseDouble(etMajorD.getText().toString(), 1.0);
        double newVal = Math.max(0.01, currentVal + delta);
        isUpdatingUi = true;
        etMajorD.setText(String.format(Locale.US, (currentUnit == TaperEngine.UnitSystem.MM ? "%.3f" : "%.4f"), newVal));
        isUpdatingUi = false;
        recalculate();
    }

    private void adjustMinorDia(double delta) {
        double currentVal = parseDouble(etMinorD.getText().toString(), 0.8);
        double newVal = Math.max(0.005, currentVal + delta);
        isUpdatingUi = true;
        etMinorD.setText(String.format(Locale.US, (currentUnit == TaperEngine.UnitSystem.MM ? "%.3f" : "%.4f"), newVal));
        isUpdatingUi = false;
        recalculate();
    }

    private void recalculate() {
        double dMajor = parseDouble(etMajorD.getText().toString(), 0);
        double dMinor = parseDouble(etMinorD.getText().toString(), 0);
        double lTaper = parseDouble(etTaperL.getText().toString(), 0);
        double lTotal = parseDouble(etTotalL.getText().toString(), 0);

        lastResult = TaperEngine.calculate(dMajor, dMinor, lTaper, lTotal, currentUnit);

        tvCompoundAngle.setText(lastResult.getFormattedCompoundAngle());
        tvTpfBadge.setText(lastResult.getFormattedTpf());
        tvIncludedAngle.setText(lastResult.getFormattedIncludedAngle());
        tvTailstockOffset.setText(lastResult.getFormattedTailstockOffset());

        String unitStr = (currentUnit == TaperEngine.UnitSystem.MM) ? "mm" : "in";
        taperView.updateTaper(lastResult.majorDia, lastResult.minorDia, lastResult.taperLength, lastResult.halfAngleDeg, unitStr);
    }

    private void copySpecToClipboard() {
        if (lastResult == null) return;
        String name = (currentPreset != null) ? currentPreset.name : "Custom Taper";
        String report = lastResult.getSummary(name);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("TaperGauge Machining Specification", report);
        if (cm != null) {
            cm.setPrimaryClip(clip);
            Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSetupToDatabase() {
        if (lastResult == null || lastResult.halfAngleDeg <= 0) {
            Toast.makeText(this, "Enter valid taper dimensions to save setup", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = (currentPreset != null) ? currentPreset.name : "Custom Taper";
        String unitStr = (currentUnit == TaperEngine.UnitSystem.MM) ? "mm" : "in";
        dbHelper.insertSetup(
                name,
                lastResult.majorDia,
                lastResult.minorDia,
                lastResult.taperLength,
                lastResult.totalWorkpieceLength,
                lastResult.halfAngleDeg,
                lastResult.tailstockOffset,
                unitStr
        );
        Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show();
        loadRecentSetups();
    }

    private void loadRecentSetups() {
        layoutRecentList.removeAllViews();
        List<TaperDatabaseHelper.TaperRecord> records = dbHelper.getAllSetups();
        tvRecordCount.setText(String.format(Locale.US, "%d setups", records.size()));

        int maxShow = Math.min(5, records.size());
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < maxShow; i++) {
            TaperDatabaseHelper.TaperRecord r = records.get(i);
            View itemView = inflater.inflate(R.layout.item_taper_setup, layoutRecentList, false);

            TextView tvName = itemView.findViewById(R.id.tvItemSetupName);
            TextView tvDims = itemView.findViewById(R.id.tvItemDimensions);
            TextView tvAng = itemView.findViewById(R.id.tvItemAngle);

            tvName.setText(r.setupName);
            tvDims.setText(String.format(Locale.US, "D: %.3f %s • d: %.3f • L: %.3f",
                    r.majorD, r.unit, r.minorD, r.taperL));
            tvAng.setText(String.format(Locale.US, "%.3f°", r.compoundAngle));

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                isUpdatingUi = true;
                if ("mm".equals(r.unit)) {
                    currentUnit = TaperEngine.UnitSystem.MM;
                    btnUnitToggle.setText("Metric (mm)");
                } else {
                    currentUnit = TaperEngine.UnitSystem.INCHES;
                    btnUnitToggle.setText("Imperial (in)");
                }
                etMajorD.setText(String.format(Locale.US, "%.4f", r.majorD));
                etMinorD.setText(String.format(Locale.US, "%.4f", r.minorD));
                etTaperL.setText(String.format(Locale.US, "%.4f", r.taperL));
                etTotalL.setText(String.format(Locale.US, "%.4f", r.totalL));
                isUpdatingUi = false;
                recalculate();
                Toast.makeText(MainActivity.this, "Loaded " + r.setupName, Toast.LENGTH_SHORT).show();
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
            loadRecentSetups();
            Toast.makeText(MainActivity.this, "Saved setups cleared", Toast.LENGTH_SHORT).show();
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
