package com.kimrosil.combgauge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "combgauge_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode";

    private SharedPreferences prefs;
    private CoatingDbHelper dbHelper;

    // Header and Global Controls
    private Button btnUnitMicrons;
    private Button btnUnitMils;
    private Button btnThemeToggle;
    private Button btnOpenPresets;
    private Button btnTileSettings;

    // Calculation Direction and Active Spec
    private Button btnDirDftToWft;
    private Button btnDirWftToDft;
    private TextView txtActivePreset;

    // Visual HUD & Hero Card
    private CombGaugeVisualView visualGauge;
    private TextView txtPrimaryResultValue;
    private TextView txtPrimaryResultUnit;
    private TextView txtPrimaryResultLabel;
    private TextView txtSpreadingRate;
    private TextView txtDeadVolumeLoss;
    private TextView txtShrinkageFactor;

    // Control Deck: Thickness Stepper
    private TextView txtThicknessInputLabel;
    private EditText edtThickness;
    private Button btnStepMinus;
    private Button btnStepPlus;

    // Control Deck: Solids Stepper & Slider
    private EditText edtSolidsPercent;
    private SeekBar seekSolidsPercent;
    private Button btnSolidsMinus;
    private Button btnSolidsPlus;

    // Control Deck: Thinner Stepper
    private EditText edtThinnerPercent;
    private Button btnThinnerMinus;
    private Button btnThinnerPlus;
    private TextView txtThinnerWarning;

    // Anchor Profile Selection Chips
    private Button chipSmooth;
    private Button chipFine25;
    private Button chipMed50;
    private Button chipCoarse75;

    // State Layouts & Actions
    private LinearLayout llEmptyState;
    private LinearLayout llActiveDeck;
    private Button btnQuickPresetEpoxy;
    private Button btnQuickPresetPoly;
    private Button btnSaveJobBatch;
    private Button btnShareCalculation;

    // State variables
    private int currentUnit = CoatingCalculatorEngine.UNIT_MICRONS;
    private int calculationMode = CoatingCalculatorEngine.MODE_DFT_TO_WFT;
    private int currentProfile = CoatingCalculatorEngine.PROFILE_FINE_25;

    private double currentThicknessValue = 100.0; // Target DFT or Measured WFT
    private double currentSolidsPercent = 75.0;
    private double currentThinnerPercent = 0.0;
    private double maxThinnerLimit = 15.0;
    private String currentCoatingName = "Polyamide Epoxy Intermediate";

    private boolean isUpdatingInputs = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = sp.getInt(PREF_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = Configuration.UI_MODE_NIGHT_NO | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        } else if (themeMode == 2) {
            config.uiMode = Configuration.UI_MODE_NIGHT_YES | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        } else {
            int sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (sysNight == 2) {
                config.uiMode = Configuration.UI_MODE_NIGHT_YES | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
            } else {
                config.uiMode = Configuration.UI_MODE_NIGHT_NO | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
            }
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        dbHelper = new CoatingDbHelper(this);

        initViews();
        restoreState();
        setupListeners();
        updateThemeToggleButton();
        configureStatusBar();
        recalculateAndRender();
    }

    private void initViews() {
        btnUnitMicrons = findViewById(R.id.btnUnitMicrons);
        btnUnitMils = findViewById(R.id.btnUnitMils);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnOpenPresets = findViewById(R.id.btnOpenPresets);
        btnTileSettings = findViewById(R.id.btnTileSettings);

        btnDirDftToWft = findViewById(R.id.btnDirDftToWft);
        btnDirWftToDft = findViewById(R.id.btnDirWftToDft);
        txtActivePreset = findViewById(R.id.txtActivePreset);

        visualGauge = findViewById(R.id.visualGauge);
        txtPrimaryResultValue = findViewById(R.id.txtPrimaryResultValue);
        txtPrimaryResultUnit = findViewById(R.id.txtPrimaryResultUnit);
        txtPrimaryResultLabel = findViewById(R.id.txtPrimaryResultLabel);
        txtSpreadingRate = findViewById(R.id.txtSpreadingRate);
        txtDeadVolumeLoss = findViewById(R.id.txtDeadVolumeLoss);
        txtShrinkageFactor = findViewById(R.id.txtShrinkageFactor);

        txtThicknessInputLabel = findViewById(R.id.txtThicknessInputLabel);
        edtThickness = findViewById(R.id.edtThickness);
        btnStepMinus = findViewById(R.id.btnStepMinus);
        btnStepPlus = findViewById(R.id.btnStepPlus);

        edtSolidsPercent = findViewById(R.id.edtSolidsPercent);
        seekSolidsPercent = findViewById(R.id.seekSolidsPercent);
        btnSolidsMinus = findViewById(R.id.btnSolidsMinus);
        btnSolidsPlus = findViewById(R.id.btnSolidsPlus);

        edtThinnerPercent = findViewById(R.id.edtThinnerPercent);
        btnThinnerMinus = findViewById(R.id.btnThinnerMinus);
        btnThinnerPlus = findViewById(R.id.btnThinnerPlus);
        txtThinnerWarning = findViewById(R.id.txtThinnerWarning);

        chipSmooth = findViewById(R.id.chipSmooth);
        chipFine25 = findViewById(R.id.chipFine25);
        chipMed50 = findViewById(R.id.chipMed50);
        chipCoarse75 = findViewById(R.id.chipCoarse75);

        llEmptyState = findViewById(R.id.llEmptyState);
        llActiveDeck = findViewById(R.id.llActiveDeck);
        btnQuickPresetEpoxy = findViewById(R.id.btnQuickPresetEpoxy);
        btnQuickPresetPoly = findViewById(R.id.btnQuickPresetPoly);
        btnSaveJobBatch = findViewById(R.id.btnSaveJobBatch);
        btnShareCalculation = findViewById(R.id.btnShareCalculation);
    }

    private void restoreState() {
        currentUnit = prefs.getInt("last_unit", CoatingCalculatorEngine.UNIT_MICRONS);
        calculationMode = prefs.getInt("last_mode", CoatingCalculatorEngine.MODE_DFT_TO_WFT);
        currentProfile = prefs.getInt("last_profile", CoatingCalculatorEngine.PROFILE_FINE_25);

        currentThicknessValue = prefs.getFloat("last_thickness", (currentUnit == CoatingCalculatorEngine.UNIT_MICRONS ? 100.0f : 4.0f));
        currentSolidsPercent = prefs.getFloat("last_solids", 75.0f);
        currentThinnerPercent = prefs.getFloat("last_thinner", 0.0f);
        currentCoatingName = prefs.getString("last_coating_name", "Polyamide Epoxy Intermediate");
        maxThinnerLimit = prefs.getFloat("last_max_thinner", 15.0f);

        updateDirectionButtons();
        updateUnitPills();
        updateProfileChips();
        updateInputFields();
        txtActivePreset.setText(currentCoatingName);
    }

    private void saveCurrentState() {
        prefs.edit()
                .putInt("last_unit", currentUnit)
                .putInt("last_mode", calculationMode)
                .putInt("last_profile", currentProfile)
                .putFloat("last_thickness", (float) currentThicknessValue)
                .putFloat("last_solids", (float) currentSolidsPercent)
                .putFloat("last_thinner", (float) currentThinnerPercent)
                .putString("last_coating_name", currentCoatingName)
                .putFloat("last_max_thinner", (float) maxThinnerLimit)
                .apply();
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                cycleThemeMode();
            }
        });

        btnUnitMicrons.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentUnit != CoatingCalculatorEngine.UNIT_MICRONS) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    convertUnits(CoatingCalculatorEngine.UNIT_MICRONS);
                }
            }
        });

        btnUnitMils.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentUnit != CoatingCalculatorEngine.UNIT_MILS) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    convertUnits(CoatingCalculatorEngine.UNIT_MILS);
                }
            }
        });

        btnDirDftToWft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calculationMode = CoatingCalculatorEngine.MODE_DFT_TO_WFT;
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                updateDirectionButtons();
                recalculateAndRender();
            }
        });

        btnDirWftToDft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calculationMode = CoatingCalculatorEngine.MODE_WFT_TO_DFT;
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                updateDirectionButtons();
                recalculateAndRender();
            }
        });

        btnOpenPresets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                openPresetsDialog();
            }
        });

        btnTileSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                showQuickSettingsHintDialog();
            }
        });

        // Thickness controls
        btnStepMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                double step = (currentUnit == CoatingCalculatorEngine.UNIT_MICRONS) ? 10.0 : 0.5;
                currentThicknessValue = Math.max(step, currentThicknessValue - step);
                updateThicknessField();
                recalculateAndRender();
            }
        });

        btnStepPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                double step = (currentUnit == CoatingCalculatorEngine.UNIT_MICRONS) ? 10.0 : 0.5;
                currentThicknessValue += step;
                updateThicknessField();
                recalculateAndRender();
            }
        });

        edtThickness.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingInputs) return;
                String val = s.toString().trim();
                if (!TextUtils.isEmpty(val)) {
                    try {
                        currentThicknessValue = Double.parseDouble(val);
                        recalculateAndRender();
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        edtThickness.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    edtSolidsPercent.requestFocus();
                    return true;
                }
                return false;
            }
        });

        // Solids controls
        seekSolidsPercent.setMax(99); // 1% to 100%
        seekSolidsPercent.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentSolidsPercent = progress + 1;
                    updateSolidsField();
                    recalculateAndRender();
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSolidsMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                currentSolidsPercent = Math.max(1.0, currentSolidsPercent - 1.0);
                updateSolidsField();
                recalculateAndRender();
            }
        });

        btnSolidsPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                currentSolidsPercent = Math.min(100.0, currentSolidsPercent + 1.0);
                updateSolidsField();
                recalculateAndRender();
            }
        });

        edtSolidsPercent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingInputs) return;
                String val = s.toString().trim();
                if (!TextUtils.isEmpty(val)) {
                    try {
                        double parsed = Double.parseDouble(val);
                        currentSolidsPercent = Math.max(1.0, Math.min(100.0, parsed));
                        seekSolidsPercent.setProgress((int) Math.round(currentSolidsPercent) - 1);
                        recalculateAndRender();
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        // Thinner controls
        btnThinnerMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                currentThinnerPercent = Math.max(0.0, currentThinnerPercent - 1.0);
                updateThinnerField();
                recalculateAndRender();
            }
        });

        btnThinnerPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                currentThinnerPercent = Math.min(50.0, currentThinnerPercent + 1.0);
                if (currentThinnerPercent > maxThinnerLimit) {
                    v.performHapticFeedback(HapticFeedbackConstants.REJECT);
                }
                updateThinnerField();
                recalculateAndRender();
            }
        });

        edtThinnerPercent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingInputs) return;
                String val = s.toString().trim();
                if (!TextUtils.isEmpty(val)) {
                    try {
                        currentThinnerPercent = Math.max(0.0, Math.min(50.0, Double.parseDouble(val)));
                        recalculateAndRender();
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        edtThinnerPercent.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    dismissKeyboard();
                    return true;
                }
                return false;
            }
        });

        // Profile chips
        chipSmooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectProfile(CoatingCalculatorEngine.PROFILE_SMOOTH);
            }
        });
        chipFine25.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectProfile(CoatingCalculatorEngine.PROFILE_FINE_25);
            }
        });
        chipMed50.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectProfile(CoatingCalculatorEngine.PROFILE_MEDIUM_50);
            }
        });
        chipCoarse75.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectProfile(CoatingCalculatorEngine.PROFILE_COARSE_75);
            }
        });

        // Quick Presets in Empty/Header State
        btnQuickPresetEpoxy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyPresetValues("Polyamide Epoxy Intermediate", 75.0, 15.0);
            }
        });
        btnQuickPresetPoly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyPresetValues("Aliphatic Acrylic Polyurethane", 58.0, 10.0);
            }
        });

        // Canvas touch listener
        visualGauge.setOnToothSelectedListener(new CombGaugeVisualView.OnToothSelectedListener() {
            @Override
            public void onToothSelected(double thicknessValue) {
                currentThicknessValue = thicknessValue;
                updateThicknessField();
                recalculateAndRender();
            }
        });

        btnSaveJobBatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                saveJobBatchDialog();
            }
        });

        btnShareCalculation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                shareCalculationSummary();
            }
        });
    }

    private void selectProfile(int profile) {
        currentProfile = profile;
        if (getWindow() != null && getWindow().getDecorView() != null) {
            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        updateProfileChips();
        recalculateAndRender();
    }

    private void updateProfileChips() {
        chipSmooth.setSelected(currentProfile == CoatingCalculatorEngine.PROFILE_SMOOTH);
        chipFine25.setSelected(currentProfile == CoatingCalculatorEngine.PROFILE_FINE_25);
        chipMed50.setSelected(currentProfile == CoatingCalculatorEngine.PROFILE_MEDIUM_50);
        chipCoarse75.setSelected(currentProfile == CoatingCalculatorEngine.PROFILE_COARSE_75);
    }

    private void updateDirectionButtons() {
        boolean isDftToWft = (calculationMode == CoatingCalculatorEngine.MODE_DFT_TO_WFT);
        btnDirDftToWft.setSelected(isDftToWft);
        btnDirWftToDft.setSelected(!isDftToWft);

        if (isDftToWft) {
            txtThicknessInputLabel.setText("SPECIFIED TARGET DFT");
            txtPrimaryResultLabel.setText("REQUIRED TARGET WFT (BEFORE CURING)");
        } else {
            txtThicknessInputLabel.setText("MEASURED RAW COMB WFT");
            txtPrimaryResultLabel.setText("RESULTING DRY FILM THICKNESS (DFT)");
        }
    }

    private void updateUnitPills() {
        boolean isMicrons = (currentUnit == CoatingCalculatorEngine.UNIT_MICRONS);
        btnUnitMicrons.setSelected(isMicrons);
        btnUnitMils.setSelected(!isMicrons);
        txtPrimaryResultUnit.setText(CoatingCalculatorEngine.getUnitLabel(currentUnit));
    }

    private void convertUnits(int newUnit) {
        if (newUnit == currentUnit) return;
        if (newUnit == CoatingCalculatorEngine.UNIT_MILS) {
            currentThicknessValue = currentThicknessValue / CoatingCalculatorEngine.MICRONS_PER_MIL;
        } else {
            currentThicknessValue = currentThicknessValue * CoatingCalculatorEngine.MICRONS_PER_MIL;
        }
        currentUnit = newUnit;
        updateUnitPills();
        updateThicknessField();
        recalculateAndRender();
    }

    private void updateInputFields() {
        updateThicknessField();
        updateSolidsField();
        updateThinnerField();
    }

    private void updateThicknessField() {
        isUpdatingInputs = true;
        edtThickness.setText(CoatingCalculatorEngine.formatThickness(currentThicknessValue, currentUnit));
        isUpdatingInputs = false;
    }

    private void updateSolidsField() {
        isUpdatingInputs = true;
        edtSolidsPercent.setText(String.format(Locale.US, "%.0f", currentSolidsPercent));
        seekSolidsPercent.setProgress((int) Math.round(currentSolidsPercent) - 1);
        isUpdatingInputs = false;
    }

    private void updateThinnerField() {
        isUpdatingInputs = true;
        edtThinnerPercent.setText(String.format(Locale.US, "%.0f", currentThinnerPercent));
        isUpdatingInputs = false;
    }

    private void recalculateAndRender() {
        boolean isDftToWft = (calculationMode == CoatingCalculatorEngine.MODE_DFT_TO_WFT);
        double primaryResult;
        double targetDft;
        double reqWft;

        if (isDftToWft) {
            targetDft = currentThicknessValue;
            reqWft = CoatingCalculatorEngine.calculateRequiredWft(
                    targetDft, currentSolidsPercent, currentThinnerPercent, currentProfile, currentUnit);
            primaryResult = reqWft;
        } else {
            reqWft = currentThicknessValue;
            targetDft = CoatingCalculatorEngine.calculateResultingDft(
                    reqWft, currentSolidsPercent, currentThinnerPercent, currentProfile, currentUnit);
            primaryResult = targetDft;
        }

        // Update Hero HUD Readout
        txtPrimaryResultValue.setText(CoatingCalculatorEngine.formatThickness(primaryResult, currentUnit));
        txtPrimaryResultUnit.setText(CoatingCalculatorEngine.getUnitLabel(currentUnit));

        // Theoretical Spreading Rate
        double spreadingRate = CoatingCalculatorEngine.calculateTheoreticalSpreadingRate(
                targetDft, currentSolidsPercent, currentUnit);
        txtSpreadingRate.setText(String.format(Locale.US, "%.2f %s",
                spreadingRate, CoatingCalculatorEngine.getSpreadingRateUnit(currentUnit)));

        // Profile Dead Volume Deduction
        double profileLoss = CoatingCalculatorEngine.getEffectiveDeadVolumeLoss(currentProfile, currentUnit);
        txtDeadVolumeLoss.setText(String.format(Locale.US, "+%s %s loss",
                CoatingCalculatorEngine.formatThickness(profileLoss, currentUnit),
                CoatingCalculatorEngine.getUnitLabel(currentUnit)));

        // Shrinkage Factor
        double shrinkage = CoatingCalculatorEngine.getShrinkagePercent(currentSolidsPercent, currentThinnerPercent);
        txtShrinkageFactor.setText(String.format(Locale.US, "%.1f%% solvent loss", shrinkage));

        // Thinner Warning Indicator
        if (currentThinnerPercent > maxThinnerLimit) {
            txtThinnerWarning.setVisibility(View.VISIBLE);
            txtThinnerWarning.setText(String.format(Locale.US,
                    "Caution: Exceeds manufacturer limit (Max %.0f%%). Risk of sags & runs.", maxThinnerLimit));
        } else if (currentThinnerPercent > 20.0) {
            txtThinnerWarning.setVisibility(View.VISIBLE);
            txtThinnerWarning.setText("Warning: High thinner dilution reduces film build durability.");
        } else {
            txtThinnerWarning.setVisibility(View.GONE);
        }

        // Empty state vs Active state
        if (currentThicknessValue <= 0.0) {
            llEmptyState.setVisibility(View.VISIBLE);
            llActiveDeck.setVisibility(View.GONE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            llActiveDeck.setVisibility(View.VISIBLE);
        }

        // Pass calculated WFT to visual comb view
        visualGauge.setParameters(reqWft, currentUnit, currentProfile, isDftToWft);

        // Update persistence for Quick Settings Tile & Session resume
        prefs.edit().putFloat("last_calc_wft", (float) reqWft)
                .putInt("last_calc_unit", currentUnit)
                .putFloat("last_calc_solids", (float) currentSolidsPercent)
                .putFloat("last_calc_thinner", (float) currentThinnerPercent)
                .putInt("last_calc_profile", currentProfile)
                .apply();
        saveCurrentState();
    }

    private void openPresetsDialog() {
        PresetBottomSheetDialog dialog = new PresetBottomSheetDialog(this, dbHelper,
                new PresetBottomSheetDialog.OnPresetSelectedListener() {
                    @Override
                    public void onPresetSelected(CoatingDbHelper.CoatingPreset preset) {
                        applyPresetValues(preset.name, preset.volumeSolids, preset.maxThinner);
                    }
                });
        dialog.show();
    }

    private void applyPresetValues(String name, double solids, double maxThinner) {
        currentCoatingName = name;
        currentSolidsPercent = solids;
        maxThinnerLimit = maxThinner;
        if (currentThinnerPercent > maxThinnerLimit) {
            currentThinnerPercent = 0.0;
        }
        txtActivePreset.setText(currentCoatingName);
        updateSolidsField();
        updateThinnerField();
        recalculateAndRender();
        Toast.makeText(this, "Loaded: " + name, Toast.LENGTH_SHORT).show();
    }

    private void showQuickSettingsHintDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Quick Settings Tile Shortcut")
                .setMessage("CombGauge provides an Android Quick Settings Tile.\n\nSwipe down your Android notification shade, tap Edit Tiles (pencil icon), and drag 'CombGauge' to your active tiles.\n\nInspect wet comb readings directly over spec sheets and camera inspection photos without leaving your work.")
                .setPositiveButton("Got It", null)
                .show();
    }

    private void saveJobBatchDialog() {
        final EditText edtJobRef = new EditText(this);
        edtJobRef.setHint("e.g. Tank 4-B Shell / Batch 892");
        edtJobRef.setPadding(32, 24, 32, 24);

        new AlertDialog.Builder(this)
                .setTitle("Save Coating Inspection Entry")
                .setMessage("Log current stoichiometry and comb gauge parameters into offline SQLite database.")
                .setView(edtJobRef)
                .setPositiveButton("Save Entry", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String ref = edtJobRef.getText().toString().trim();
                        if (TextUtils.isEmpty(ref)) {
                            ref = "Job #" + System.currentTimeMillis() % 10000;
                        }

                        boolean isDftToWft = (calculationMode == CoatingCalculatorEngine.MODE_DFT_TO_WFT);
                        double targetDft = isDftToWft ? currentThicknessValue :
                                CoatingCalculatorEngine.calculateResultingDft(currentThicknessValue, currentSolidsPercent, currentThinnerPercent, currentProfile, currentUnit);
                        double reqWft = isDftToWft ?
                                CoatingCalculatorEngine.calculateRequiredWft(currentThicknessValue, currentSolidsPercent, currentThinnerPercent, currentProfile, currentUnit) : currentThicknessValue;

                        long id = dbHelper.insertJobLog(ref, currentCoatingName, targetDft, reqWft,
                                currentSolidsPercent, currentThinnerPercent,
                                CoatingCalculatorEngine.getEffectiveDeadVolumeLoss(currentProfile, currentUnit),
                                CoatingCalculatorEngine.getUnitLabel(currentUnit));

                        if (id != -1) {
                            Toast.makeText(MainActivity.this, "Batch log saved offline (" + ref + ")", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareCalculationSummary() {
        boolean isDftToWft = (calculationMode == CoatingCalculatorEngine.MODE_DFT_TO_WFT);
        double targetDft = isDftToWft ? currentThicknessValue :
                CoatingCalculatorEngine.calculateResultingDft(currentThicknessValue, currentSolidsPercent, currentThinnerPercent, currentProfile, currentUnit);
        double reqWft = isDftToWft ?
                CoatingCalculatorEngine.calculateRequiredWft(currentThicknessValue, currentSolidsPercent, currentThinnerPercent, currentProfile, currentUnit) : currentThicknessValue;

        String unitStr = CoatingCalculatorEngine.getUnitLabel(currentUnit);
        String report = String.format(Locale.US,
                "CombGauge Field Inspection Report\n" +
                        "Coating: %s\n" +
                        "Volume Solids: %.0f%%\n" +
                        "Thinner Added: %.0f%%\n" +
                        "Target DFT: %s %s\n" +
                        "Required Comb WFT: %s %s\n" +
                        "Theoretical Spread Rate: %s\n" +
                        "Surface Dead Volume Deduction: %s %s\n" +
                        "Verified offline via CombGauge (ASTM D4414)",
                currentCoatingName, currentSolidsPercent, currentThinnerPercent,
                CoatingCalculatorEngine.formatThickness(targetDft, currentUnit), unitStr,
                CoatingCalculatorEngine.formatThickness(reqWft, currentUnit), unitStr,
                txtSpreadingRate.getText().toString(),
                CoatingCalculatorEngine.formatThickness(CoatingCalculatorEngine.getEffectiveDeadVolumeLoss(currentProfile, currentUnit), currentUnit), unitStr);

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("CombGauge Report", report);
            clipboard.setPrimaryClip(clip);
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "CombGauge Calculation - " + currentCoatingName);
        shareIntent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(shareIntent, "Share Inspection Report"));
    }

    private void cycleThemeMode() {
        int mode = prefs.getInt(PREF_THEME_MODE, 0);
        mode = (mode + 1) % 3;
        prefs.edit().putInt(PREF_THEME_MODE, mode).apply();
        recreate();
    }

    private void updateThemeToggleButton() {
        int mode = prefs.getInt(PREF_THEME_MODE, 0);
        if (mode == 1) {
            btnThemeToggle.setText("Light");
        } else if (mode == 2) {
            btnThemeToggle.setText("Dark");
        } else {
            btnThemeToggle.setText("Auto");
        }
    }

    private void configureStatusBar() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (getWindow() != null && getWindow().getInsetsController() != null) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            } else {
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        }
    }

    private void dismissKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                dismissKeyboard();
                recalculateAndRender();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                calculationMode = (calculationMode == CoatingCalculatorEngine.MODE_DFT_TO_WFT) ?
                        CoatingCalculatorEngine.MODE_WFT_TO_DFT : CoatingCalculatorEngine.MODE_DFT_TO_WFT;
                updateDirectionButtons();
                recalculateAndRender();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                btnStepPlus.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
                btnStepMinus.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                applyPresetValues("Polyamide Epoxy Intermediate", 75.0, 15.0);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configureStatusBar();
    }
}