package com.kimrosil.ductflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "ductflow_prefs";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_MODE = "sizing_mode";
    private static final String KEY_MATERIAL = "duct_material";
    private static final String KEY_METRIC = "is_metric";
    private static final String PREF_CFM = "saved_cfm";
    private static final String PREF_FRICTION = "saved_friction";
    private static final String PREF_VELOCITY = "saved_velocity";
    private static final String PREF_HEIGHT_IDX = "saved_height_idx";

    private SharedPreferences prefs;
    private DuctDatabaseHelper dbHelper;

    private int currentMode = DuctFlowEngine.MODE_EQUAL_FRICTION;
    private int currentMaterial = DuctFlowEngine.MATERIAL_GALVANIZED;
    private boolean isMetric = false;

    private double currentCfm = 800.0;
    private double currentFriction = 0.080;
    private double currentVelocity = 1000.0;

    private final int[] standardHeights = {6, 8, 10, 12, 14, 16, 18, 20, 24};
    private int selectedHeightIdx = 2; // Default 10 inches

    // UI Elements
    private TextView tvHeaderSubtitle;
    private Button btnUnitToggle;
    private ImageButton btnTheme;
    private ImageButton btnSettings;

    private TextView tvRoundDia;
    private TextView tvAspectBadge;
    private TextView tvRectSize;
    private TextView tvRectSecondary;
    private TextView tvVelocity;
    private TextView tvVelocitySecondary;
    private TextView tvFrictionRate;
    private TextView tvNominalRound;
    private DuctCrossSectionView ductView;

    private Button btnCopySchedule;
    private Button btnSaveDuct;
    private Button btnExportCsv;

    private Button btnModeFriction;
    private Button btnModeVelocity;
    private Button btnMatGalvanized;
    private Button btnMatFlex;

    private LinearLayout layoutPresetsSection;
    private Button btnPresetSupplyMain;
    private Button btnPresetSupplyBranch;
    private Button btnPresetReturn;
    private Button btnPresetQuiet;

    private TextView tvCfmLabel;
    private EditText etCfm;
    private Button btnCfmMinus100;
    private Button btnCfmMinus10;
    private Button btnCfmPlus10;
    private Button btnCfmPlus100;

    private TextView tvParamLabel;
    private EditText etParam;
    private Button btnParamMinus;
    private Button btnParamPlus;

    private TextView tvHeightTitle;
    private LinearLayout layoutHeightChips;

    private TextView tvRecordCount;
    private LinearLayout layoutRecentList;

    private DuctFlowEngine.DuctResult currentResult;
    private boolean isUpdatingUi = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = sp.getInt(KEY_THEME, 0);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        if (overrideConfiguration != null) {
            int mode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_THEME, 0);
            if (mode == 1) {
                overrideConfiguration.uiMode = (overrideConfiguration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            } else if (mode == 2) {
                overrideConfiguration.uiMode = (overrideConfiguration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            }
        }
        super.applyOverrideConfiguration(overrideConfiguration);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(KEY_THEME, 0);
        if (themeMode == 1) {
            setTheme(R.style.AppTheme_Light);
        } else if (themeMode == 2) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DuctDatabaseHelper(this);

        restorePreferences();
        if (savedInstanceState != null) {
            restoreInstanceStateBundle(savedInstanceState);
        }

        initViews();
        setupListeners();
        buildHeightChips();
        updateModeVisuals();
        updateMaterialVisuals();
        updateUnitVisuals();
        calculateAndUpdate();
        loadRecentDucts();
    }

    private void setThemePreference(int mode) {
        prefs.edit().putInt(KEY_THEME, mode).apply();
    }

    private void restorePreferences() {
        currentMode = prefs.getInt(KEY_MODE, DuctFlowEngine.MODE_EQUAL_FRICTION);
        currentMaterial = prefs.getInt(KEY_MATERIAL, DuctFlowEngine.MATERIAL_GALVANIZED);
        isMetric = prefs.getBoolean(KEY_METRIC, false);

        currentCfm = Double.longBitsToDouble(prefs.getLong(PREF_CFM, Double.doubleToLongBits(800.0)));
        currentFriction = Double.longBitsToDouble(prefs.getLong(PREF_FRICTION, Double.doubleToLongBits(0.080)));
        currentVelocity = Double.longBitsToDouble(prefs.getLong(PREF_VELOCITY, Double.doubleToLongBits(1000.0)));
        selectedHeightIdx = prefs.getInt(PREF_HEIGHT_IDX, 2);
        if (selectedHeightIdx < 0 || selectedHeightIdx >= standardHeights.length) {
            selectedHeightIdx = 2;
        }
    }

    private void savePreferences() {
        prefs.edit()
                .putInt(KEY_MODE, currentMode)
                .putInt(KEY_MATERIAL, currentMaterial)
                .putBoolean(KEY_METRIC, isMetric)
                .putLong(PREF_CFM, Double.doubleToLongBits(currentCfm))
                .putLong(PREF_FRICTION, Double.doubleToLongBits(currentFriction))
                .putLong(PREF_VELOCITY, Double.doubleToLongBits(currentVelocity))
                .putInt(PREF_HEIGHT_IDX, selectedHeightIdx)
                .apply();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_MODE, currentMode);
        outState.putInt(KEY_MATERIAL, currentMaterial);
        outState.putBoolean(KEY_METRIC, isMetric);
        outState.putDouble(PREF_CFM, currentCfm);
        outState.putDouble(PREF_FRICTION, currentFriction);
        outState.putDouble(PREF_VELOCITY, currentVelocity);
        outState.putInt(PREF_HEIGHT_IDX, selectedHeightIdx);
    }

    private void restoreInstanceStateBundle(Bundle b) {
        currentMode = b.getInt(KEY_MODE, currentMode);
        currentMaterial = b.getInt(KEY_MATERIAL, currentMaterial);
        isMetric = b.getBoolean(KEY_METRIC, isMetric);
        currentCfm = b.getDouble(PREF_CFM, currentCfm);
        currentFriction = b.getDouble(PREF_FRICTION, currentFriction);
        currentVelocity = b.getDouble(PREF_VELOCITY, currentVelocity);
        selectedHeightIdx = b.getInt(PREF_HEIGHT_IDX, selectedHeightIdx);
    }

    private void initViews() {
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        btnTheme = findViewById(R.id.btnTheme);
        btnSettings = findViewById(R.id.btnSettings);

        tvRoundDia = findViewById(R.id.tvRoundDia);
        tvAspectBadge = findViewById(R.id.tvAspectBadge);
        tvRectSize = findViewById(R.id.tvRectSize);
        tvRectSecondary = findViewById(R.id.tvRectSecondary);
        tvVelocity = findViewById(R.id.tvVelocity);
        tvVelocitySecondary = findViewById(R.id.tvVelocitySecondary);
        tvFrictionRate = findViewById(R.id.tvFrictionRate);
        tvNominalRound = findViewById(R.id.tvNominalRound);
        ductView = findViewById(R.id.ductView);

        btnCopySchedule = findViewById(R.id.btnCopySchedule);
        btnSaveDuct = findViewById(R.id.btnSaveDuct);
        btnExportCsv = findViewById(R.id.btnExportCsv);

        btnModeFriction = findViewById(R.id.btnModeFriction);
        btnModeVelocity = findViewById(R.id.btnModeVelocity);
        btnMatGalvanized = findViewById(R.id.btnMatGalvanized);
        btnMatFlex = findViewById(R.id.btnMatFlex);

        layoutPresetsSection = findViewById(R.id.layoutPresetsSection);
        btnPresetSupplyMain = findViewById(R.id.btnPresetSupplyMain);
        btnPresetSupplyBranch = findViewById(R.id.btnPresetSupplyBranch);
        btnPresetReturn = findViewById(R.id.btnPresetReturn);
        btnPresetQuiet = findViewById(R.id.btnPresetQuiet);

        tvCfmLabel = findViewById(R.id.tvCfmLabel);
        etCfm = findViewById(R.id.etCfm);
        btnCfmMinus100 = findViewById(R.id.btnCfmMinus100);
        btnCfmMinus10 = findViewById(R.id.btnCfmMinus10);
        btnCfmPlus10 = findViewById(R.id.btnCfmPlus10);
        btnCfmPlus100 = findViewById(R.id.btnCfmPlus100);

        tvParamLabel = findViewById(R.id.tvParamLabel);
        etParam = findViewById(R.id.etParam);
        btnParamMinus = findViewById(R.id.btnParamMinus);
        btnParamPlus = findViewById(R.id.btnParamPlus);

        tvHeightTitle = findViewById(R.id.tvHeightTitle);
        layoutHeightChips = findViewById(R.id.layoutHeightChips);

        tvRecordCount = findViewById(R.id.tvRecordCount);
        layoutRecentList = findViewById(R.id.layoutRecentList);
    }

    private void setupListeners() {
        btnUnitToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            isMetric = !isMetric;
            updateUnitVisuals();
            calculateAndUpdate();
            Toast.makeText(this, isMetric ? "Metric Units Active" : "Imperial Units Active", Toast.LENGTH_SHORT).show();
        });

        btnTheme.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            int currentT = prefs.getInt(KEY_THEME, 0);
            int nextT = (currentT + 1) % 3;
            setThemePreference(nextT);
            String modeName = (nextT == 1) ? "Light Mode" : (nextT == 2 ? "Dark Mode" : "Auto (System)");
            Toast.makeText(this, "Theme: " + modeName, Toast.LENGTH_SHORT).show();
            recreate();
        });

        btnSettings.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showSettingsDialog();
        });

        // Mode buttons
        btnModeFriction.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentMode = DuctFlowEngine.MODE_EQUAL_FRICTION;
            updateModeVisuals();
            calculateAndUpdate();
        });

        btnModeVelocity.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentMode = DuctFlowEngine.MODE_VELOCITY;
            updateModeVisuals();
            calculateAndUpdate();
        });

        // Material buttons
        btnMatGalvanized.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentMaterial = DuctFlowEngine.MATERIAL_GALVANIZED;
            updateMaterialVisuals();
            calculateAndUpdate();
        });

        btnMatFlex.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentMaterial = DuctFlowEngine.MATERIAL_FLEX_DUCT;
            updateMaterialVisuals();
            calculateAndUpdate();
        });

        // Friction Presets (2x2 grid)
        btnPresetSupplyMain.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentFriction = 0.100;
            updatePresetButtonStyles();
            calculateAndUpdate();
        });

        btnPresetSupplyBranch.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentFriction = 0.080;
            updatePresetButtonStyles();
            calculateAndUpdate();
        });

        btnPresetReturn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentFriction = 0.060;
            updatePresetButtonStyles();
            calculateAndUpdate();
        });

        btnPresetQuiet.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentFriction = 0.050;
            updatePresetButtonStyles();
            calculateAndUpdate();
        });

        // CFM Steppers
        btnCfmMinus100.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentCfm > 150) {
                currentCfm -= 100;
                calculateAndUpdate();
            }
        });

        btnCfmMinus10.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentCfm > 20) {
                currentCfm -= 10;
                calculateAndUpdate();
            }
        });

        btnCfmPlus10.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentCfm < 99000) {
                currentCfm += 10;
                calculateAndUpdate();
            }
        });

        btnCfmPlus100.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentCfm < 99000) {
                currentCfm += 100;
                calculateAndUpdate();
            }
        });

        // Parameter Steppers
        btnParamMinus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentMode == DuctFlowEngine.MODE_VELOCITY) {
                if (currentVelocity > 200) currentVelocity -= 50;
            } else {
                if (currentFriction > 0.015) currentFriction -= 0.010;
                updatePresetButtonStyles();
            }
            calculateAndUpdate();
        });

        btnParamPlus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentMode == DuctFlowEngine.MODE_VELOCITY) {
                if (currentVelocity < 5000) currentVelocity += 50;
            } else {
                if (currentFriction < 0.900) currentFriction += 0.010;
                updatePresetButtonStyles();
            }
            calculateAndUpdate();
        });

        // Direct CFM input
        etCfm.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingUi) return;
                try {
                    String str = s.toString().trim();
                    if (!str.isEmpty()) {
                        double val = Double.parseDouble(str);
                        if (!Double.isNaN(val) && val >= 10 && val <= 100000) {
                            currentCfm = val;
                            calculateAndUpdateFromDirectInput();
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        etCfm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                calculateAndUpdate();
                return true;
            }
            return false;
        });

        // Direct Parameter input
        etParam.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingUi) return;
                try {
                    String str = s.toString().trim();
                    if (!str.isEmpty()) {
                        double val = Double.parseDouble(str);
                        if (!Double.isNaN(val)) {
                            if (currentMode == DuctFlowEngine.MODE_VELOCITY) {
                                if (val >= 100 && val <= 6000) {
                                    currentVelocity = val;
                                    calculateAndUpdateFromDirectInput();
                                }
                            } else {
                                if (val >= 0.01 && val <= 1.0) {
                                    currentFriction = val;
                                    updatePresetButtonStyles();
                                    calculateAndUpdateFromDirectInput();
                                }
                            }
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        etParam.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                calculateAndUpdate();
                return true;
            }
            return false;
        });

        // Quick Actions
        btnCopySchedule.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentResult != null) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("DuctFlow Spec", currentResult.getSummary());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSaveDuct.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentResult != null) {
                String defaultLabel = String.format(Locale.US, "Run %.0f CFM", currentResult.cfm);
                dbHelper.insertDuct(
                        defaultLabel,
                        currentResult.mode,
                        currentResult.material,
                        isMetric,
                        currentResult.cfm,
                        currentResult.frictionRate,
                        currentResult.knownHeightInches,
                        currentResult.roundDiaInches,
                        currentResult.rectWidthInches,
                        currentResult.rectVelocityFpm,
                        currentResult.aspectRatio
                );
                Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show();
                loadRecentDucts();
            }
        });

        btnExportCsv.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportScheduleCsv();
        });
    }

    private void updateModeVisuals() {
        boolean isFriction = (currentMode == DuctFlowEngine.MODE_EQUAL_FRICTION);
        btnModeFriction.setBackgroundResource(isFriction ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnModeFriction.setTextColor(isFriction ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnModeVelocity.setBackgroundResource(!isFriction ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnModeVelocity.setTextColor(!isFriction ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        layoutPresetsSection.setVisibility(isFriction ? View.VISIBLE : View.GONE);

        if (isFriction) {
            tvParamLabel.setText(isMetric ? "Design Friction: Pa/m" : "Design Friction Rate");
            btnParamMinus.setText("-.01");
            btnParamPlus.setText("+.01");
            btnParamMinus.setContentDescription("Decrease friction by 0.01");
            btnParamPlus.setContentDescription("Increase friction by 0.01");
        } else {
            tvParamLabel.setText(isMetric ? "Velocity Limit: m/s" : "Max Velocity Limit");
            btnParamMinus.setText("-50");
            btnParamPlus.setText("+50");
            btnParamMinus.setContentDescription("Decrease velocity limit by 50 FPM");
            btnParamPlus.setContentDescription("Increase velocity limit by 50 FPM");
        }
    }

    private void updateMaterialVisuals() {
        boolean isSteel = (currentMaterial == DuctFlowEngine.MATERIAL_GALVANIZED);
        btnMatGalvanized.setBackgroundResource(isSteel ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnMatGalvanized.setTextColor(isSteel ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnMatFlex.setBackgroundResource(!isSteel ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnMatFlex.setTextColor(!isSteel ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));
    }

    private void updateUnitVisuals() {
        btnUnitToggle.setText(isMetric ? "SI" : "IP");
        tvHeaderSubtitle.setText(isMetric ? "HVAC Airflow & SI Duct Sizer" : "HVAC Equal Friction Sizer");
        tvHeightTitle.setText(isMetric ? "Known Duct Height (H mm)" : "Known Duct Height (H in.)");
        buildHeightChips();
    }

    private void updatePresetButtonStyles() {
        btnPresetSupplyMain.setBackgroundResource(Math.abs(currentFriction - 0.100) < 0.005 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPresetSupplyMain.setTextColor(Math.abs(currentFriction - 0.100) < 0.005 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnPresetSupplyBranch.setBackgroundResource(Math.abs(currentFriction - 0.080) < 0.005 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPresetSupplyBranch.setTextColor(Math.abs(currentFriction - 0.080) < 0.005 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnPresetReturn.setBackgroundResource(Math.abs(currentFriction - 0.060) < 0.005 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPresetReturn.setTextColor(Math.abs(currentFriction - 0.060) < 0.005 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnPresetQuiet.setBackgroundResource(Math.abs(currentFriction - 0.050) < 0.005 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPresetQuiet.setTextColor(Math.abs(currentFriction - 0.050) < 0.005 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));
    }

    private void buildHeightChips() {
        layoutHeightChips.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int minH = Math.round(48 * density);
        int minW = Math.round(54 * density);
        int padH = Math.round(12 * density);
        int margin = Math.round(4 * density);

        for (int i = 0; i < standardHeights.length; i++) {
            final int index = i;
            int heightVal = standardHeights[i];

            Button chip = new Button(this);
            chip.setId(View.generateViewId());
            String label = isMetric ? String.format(Locale.US, "%.0f mm", heightVal * 25.4) : (heightVal + "\" H");
            chip.setText(label);
            chip.setTextSize(11);
            chip.setContentDescription("Select duct height " + heightVal + " inches");
            chip.setMinimumHeight(minH);
            chip.setMinimumWidth(minW);
            chip.setPadding(padH, 0, padH, 0);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    minH
            );
            lp.setMargins(margin, 0, margin, 0);
            chip.setLayoutParams(lp);

            if (i == selectedHeightIdx) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected);
                chip.setTextColor(0xFFFFFFFF);
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected);
                chip.setTextColor(getColor(R.color.colorTextPrimary));
            }

            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                selectedHeightIdx = index;
                buildHeightChips();
                calculateAndUpdate();
            });

            layoutHeightChips.addView(chip);
        }
    }

    private void calculateAndUpdateFromDirectInput() {
        int knownH = standardHeights[selectedHeightIdx];
        currentResult = DuctFlowEngine.calculate(
                currentMode,
                currentMaterial,
                isMetric ? DuctFlowEngine.UNIT_METRIC : DuctFlowEngine.UNIT_IMPERIAL,
                currentCfm,
                currentFriction,
                currentVelocity,
                knownH
        );
        updateReadoutsOnly();
    }

    private void calculateAndUpdate() {
        int knownH = standardHeights[selectedHeightIdx];
        currentResult = DuctFlowEngine.calculate(
                currentMode,
                currentMaterial,
                isMetric ? DuctFlowEngine.UNIT_METRIC : DuctFlowEngine.UNIT_IMPERIAL,
                currentCfm,
                currentFriction,
                currentVelocity,
                knownH
        );

        isUpdatingUi = true;
        etCfm.setText(String.format(Locale.US, "%.0f", currentCfm));
        if (currentMode == DuctFlowEngine.MODE_VELOCITY) {
            etParam.setText(String.format(Locale.US, "%.0f", currentVelocity));
        } else {
            etParam.setText(String.format(Locale.US, "%.3f", currentFriction));
        }
        isUpdatingUi = false;

        updateReadoutsOnly();
        savePreferences();
    }

    private void updateReadoutsOnly() {
        if (currentResult == null) return;
        int knownH = standardHeights[selectedHeightIdx];

        if (isMetric) {
            tvRoundDia.setText(String.format(Locale.US, "Ø %.0f mm", currentResult.roundDiaMm));
            tvRectSize.setText(String.format(Locale.US, "%.0f x %.0f mm", currentResult.rectWidthMm, currentResult.knownHeightMm));
            tvRectSecondary.setText(String.format(Locale.US, "%.1f\" W x %d\" H", currentResult.rectWidthInches, knownH));
            tvVelocity.setText(String.format(Locale.US, "%.2f m/s", currentResult.velocityMps));
            tvVelocitySecondary.setText(String.format(Locale.US, "%.0f FPM (Round: %.0f)", currentResult.rectVelocityFpm, currentResult.roundVelocityFpm));
            tvFrictionRate.setText(String.format(Locale.US, "Loss: %.1f Pa/m (%.3f in.)", currentResult.frictionPaPerM, currentResult.frictionRate));
            tvNominalRound.setText(String.format(Locale.US, "Nominal: %.0f mm Spiral", Math.round(currentResult.roundDiaInches) * 25.4));
            tvCfmLabel.setText(String.format(Locale.US, "Airflow: %.0f m³/h (%.0f CFM)", currentResult.airflowM3h, currentResult.cfm));
        } else {
            tvRoundDia.setText(String.format(Locale.US, "Ø %.1f in.", currentResult.roundDiaInches));
            tvRectSize.setText(String.format(Locale.US, "%.1f\" W x %d\" H", currentResult.rectWidthInches, knownH));
            tvRectSecondary.setText(String.format(Locale.US, "%.0f x %.0f mm", currentResult.rectWidthMm, currentResult.knownHeightMm));
            tvVelocity.setText(String.format(Locale.US, "%.0f FPM", currentResult.rectVelocityFpm));
            tvVelocitySecondary.setText(String.format(Locale.US, "%.2f m/s (Round: %.0f)", currentResult.velocityMps, currentResult.roundVelocityFpm));
            tvFrictionRate.setText(String.format(Locale.US, "Loss: %.3f in. wg/100'", currentResult.frictionRate));
            tvNominalRound.setText(String.format(Locale.US, "Nominal: %d\" Spiral", (int) Math.round(currentResult.roundDiaInches)));
            tvCfmLabel.setText(String.format(Locale.US, "Airflow Volume: %.0f CFM", currentResult.cfm));
        }

        if (currentResult.isAspectSafe) {
            tvAspectBadge.setBackgroundResource(R.drawable.bg_badge_safe);
            tvAspectBadge.setText(String.format(Locale.US, "AR %.1f:1 Safe", currentResult.aspectRatio));
        } else {
            tvAspectBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            tvAspectBadge.setText(String.format(Locale.US, "AR %.1f:1 > 4:1 High", currentResult.aspectRatio));
        }

        ductView.updateDuct(
                currentResult.rectWidthInches,
                currentResult.knownHeightInches,
                currentResult.roundDiaInches,
                currentResult.rectVelocityFpm,
                currentResult.isAspectSafe,
                isMetric
        );
    }

    private void loadRecentDucts() {
        List<DuctDatabaseHelper.DuctRecord> records = dbHelper.getAllDucts();
        layoutRecentList.removeAllViews();
        tvRecordCount.setText(records.size() + (records.size() == 1 ? " duct" : " ducts"));

        if (records.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText(R.string.no_saved_records);
            emptyTv.setTextColor(getColor(R.color.colorTextSecondary));
            emptyTv.setTextSize(12);
            emptyTv.setPadding(0, 8, 0, 8);
            layoutRecentList.addView(emptyTv);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, HH:mm", Locale.US);
        int maxShow = Math.min(records.size(), 8);

        for (int i = 0; i < maxShow; i++) {
            DuctDatabaseHelper.DuctRecord record = records.get(i);
            View itemView = inflater.inflate(R.layout.item_duct_run, layoutRecentList, false);

            TextView tvItemTitle = itemView.findViewById(R.id.tvItemTitle);
            TextView tvItemRound = itemView.findViewById(R.id.tvItemRound);
            TextView tvItemDetails = itemView.findViewById(R.id.tvItemDetails);
            TextView tvItemDate = itemView.findViewById(R.id.tvItemDate);
            ImageButton btnDelete = itemView.findViewById(R.id.btnItemDelete);

            tvItemTitle.setText(String.format(Locale.US, "%.0f CFM • %.1f\" x %.0f\"",
                    record.cfm, record.rectWidth, record.height));
            tvItemRound.setText(String.format(Locale.US, "Ø %.1f\"", record.roundDia));
            tvItemDetails.setText(String.format(Locale.US, "Fric: %.3f in. • %.0f FPM • AR %.1f:1",
                    record.frictionRate, record.velocity, record.aspectRatio));
            tvItemDate.setText(sdf.format(new Date(record.createdAt)));

            // Restore configuration on item click
            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                currentCfm = record.cfm;
                currentFriction = record.frictionRate;
                currentMode = record.mode;
                currentMaterial = record.material;

                for (int h = 0; h < standardHeights.length; h++) {
                    if (standardHeights[h] == (int) Math.round(record.height)) {
                        selectedHeightIdx = h;
                        break;
                    }
                }

                buildHeightChips();
                updateModeVisuals();
                updateMaterialVisuals();
                updatePresetButtonStyles();
                calculateAndUpdate();
                Toast.makeText(this, "Restored " + (int) record.cfm + " CFM duct run", Toast.LENGTH_SHORT).show();
            });

            // Delete item button
            btnDelete.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                dbHelper.deleteDuct(record.id);
                loadRecentDucts();
                Toast.makeText(this, R.string.deleted_toast, Toast.LENGTH_SHORT).show();
            });

            layoutRecentList.addView(itemView);
        }
    }

    private void exportScheduleCsv() {
        String csvContent = dbHelper.exportToCsv();
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, csvContent);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "DuctFlow Schedule Export.csv");
        sendIntent.setType("text/csv");
        startActivity(Intent.createChooser(sendIntent, "Export Duct Schedule CSV"));
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
        Button btnUnitImp = dialogView.findViewById(R.id.btnUnitImperial);
        Button btnUnitMet = dialogView.findViewById(R.id.btnUnitMetric);
        Button btnClear = dialogView.findViewById(R.id.btnClearHistory);
        Button btnClose = dialogView.findViewById(R.id.btnCloseSettings);

        btnAuto.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            setThemePreference(0);
            dialog.dismiss();
            recreate();
        });

        btnLight.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            setThemePreference(1);
            dialog.dismiss();
            recreate();
        });

        btnDark.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            setThemePreference(2);
            dialog.dismiss();
            recreate();
        });

        btnUnitImp.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            isMetric = false;
            updateUnitVisuals();
            calculateAndUpdate();
            dialog.dismiss();
        });

        btnUnitMet.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            isMetric = true;
            updateUnitVisuals();
            calculateAndUpdate();
            dialog.dismiss();
        });

        btnClear.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            dbHelper.clearAll();
            loadRecentDucts();
            Toast.makeText(this, "Saved ducts cleared", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            dialog.dismiss();
        });

        dialog.show();
    }
}
