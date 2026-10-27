package com.kimrosil.plumbbob;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * PlumbBob MainActivity
 * Drain pipe slope, invert depth elevation, and trench fall workbench.
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "plumbbob_prefs";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_UNIT_METRIC = "unit_metric"; // false=Imperial, true=Metric
    private static final String KEY_CODE_STANDARD = "code_standard"; // 0=IPC, 1=UPC
    private static final String KEY_HAPTICS = "haptics_enabled";

    private static final int MODE_RUN_PITCH = 0;
    private static final int MODE_INVERTS_RUN = 1;
    private static final int MODE_DROP_PITCH = 2;

    private SharedPreferences prefs;
    private PlumbDatabaseHelper dbHelper;

    private int themeMode = 2; // Default Dark Mode
    private boolean isMetric = false;
    private int codeStandard = PlumbingEngine.CODE_IPC;
    private boolean hapticsEnabled = true;

    private int currentMode = MODE_RUN_PITCH;
    private int selectedPipeSize = PlumbingEngine.PIPE_SIZE_3_INCH;
    private double currentPitch = 0.25; // 1/4" per ft

    // UI Views
    private TextView tvBrandTitle;
    private TextView tvBrandSubtitle;
    private ImageButton btnThemeToggle;
    private ImageButton btnSettings;

    // Mode Buttons
    private Button btnModeFall;
    private Button btnModeSlope;
    private Button btnModeRun;

    // Hero HUD
    private TextView tvHeroFall;
    private TextView tvHeroFallSub;
    private TextView tvHeroPitch;
    private TextView tvHeroGrade;
    private TextView tvHeroInverts;
    private TextView tvHeroInvertSub;

    // Compliance Banner
    private LinearLayout layoutComplianceBadge;
    private ImageView ivComplianceIcon;
    private TextView tvComplianceStatus;
    private TextView tvComplianceReason;

    // Canvas Diagram
    private PipeSlopeDiagramView pipeDiagramView;

    // Pipe Diameter Chips
    private Button btnPipe2;
    private Button btnPipe3;
    private Button btnPipe6;
    private Button btnPipe8;

    // Pitch Presets
    private TextView tvPitchPresetsLabel;
    private LinearLayout layoutPitchPresets;
    private Button btnPitch1_16;
    private Button btnPitch1_8;
    private Button btnPitch1_4;
    private Button btnPitch1_2;

    // Inputs & Steppers
    private TextView tvLabelRunLength;
    private EditText etRunLength;
    private Button btnRunDec;
    private Button btnRunInc;

    private LinearLayout layoutPitchInputGroup;
    private TextView tvLabelPitch;
    private EditText etPitch;
    private Button btnPitchDec;
    private Button btnPitchInc;

    private TextView tvLabelStartInvert;
    private EditText etStartInvert;
    private Button btnStartInvDec;
    private Button btnStartInvInc;

    private LinearLayout layoutEndInvertGroup;
    private TextView tvLabelEndInvert;
    private EditText etEndInvert;
    private Button btnEndInvDec;
    private Button btnEndInvInc;

    private LinearLayout layoutDropInputGroup;
    private TextView tvLabelTargetDrop;
    private EditText etTargetDrop;
    private Button btnDropDec;
    private Button btnDropInc;

    // Action CTAs
    private Button btnSaveRun;
    private Button btnCopyNote;
    private Button btnReset;

    // Saved Runs Log
    private TextView tvEmptyLog;
    private LinearLayout llSavedRunsContainer;

    private boolean isUpdatingInputs = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = sp.getInt(KEY_THEME_MODE, 2); // Default to Dark Mode
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        int nightFlags = config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK;
        if (mode == 1) {
            config.uiMode = nightFlags | Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            config.uiMode = nightFlags | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        themeMode = prefs.getInt(KEY_THEME_MODE, 2);
        isMetric = prefs.getBoolean(KEY_UNIT_METRIC, false);
        codeStandard = prefs.getInt(KEY_CODE_STANDARD, PlumbingEngine.CODE_IPC);
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true);

        dbHelper = new PlumbDatabaseHelper(this);
        setContentView(R.layout.activity_main);

        bindViews();
        setupListeners();
        updateUnitLabels();
        updateModeUI();
        updatePipeSizeUI();
        updatePitchPresetUI();
        recalculateAndRender();
        loadSavedRuns();
    }

    private void bindViews() {
        tvBrandTitle = findViewById(R.id.tvBrandTitle);
        tvBrandSubtitle = findViewById(R.id.tvBrandSubtitle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        btnModeFall = findViewById(R.id.btnModeFall);
        btnModeSlope = findViewById(R.id.btnModeSlope);
        btnModeRun = findViewById(R.id.btnModeRun);

        tvHeroFall = findViewById(R.id.tvHeroFall);
        tvHeroFallSub = findViewById(R.id.tvHeroFallSub);
        tvHeroPitch = findViewById(R.id.tvHeroPitch);
        tvHeroGrade = findViewById(R.id.tvHeroGrade);
        tvHeroInverts = findViewById(R.id.tvHeroInverts);
        tvHeroInvertSub = findViewById(R.id.tvHeroInvertSub);

        layoutComplianceBadge = findViewById(R.id.layoutComplianceBadge);
        ivComplianceIcon = findViewById(R.id.ivComplianceIcon);
        tvComplianceStatus = findViewById(R.id.tvComplianceStatus);
        tvComplianceReason = findViewById(R.id.tvComplianceReason);

        pipeDiagramView = findViewById(R.id.pipeDiagramView);

        btnPipe2 = findViewById(R.id.btnPipe2);
        btnPipe3 = findViewById(R.id.btnPipe3);
        btnPipe6 = findViewById(R.id.btnPipe6);
        btnPipe8 = findViewById(R.id.btnPipe8);

        tvPitchPresetsLabel = findViewById(R.id.tvPitchPresetsLabel);
        layoutPitchPresets = findViewById(R.id.layoutPitchPresets);
        btnPitch1_16 = findViewById(R.id.btnPitch1_16);
        btnPitch1_8 = findViewById(R.id.btnPitch1_8);
        btnPitch1_4 = findViewById(R.id.btnPitch1_4);
        btnPitch1_2 = findViewById(R.id.btnPitch1_2);

        tvLabelRunLength = findViewById(R.id.tvLabelRunLength);
        etRunLength = findViewById(R.id.etRunLength);
        btnRunDec = findViewById(R.id.btnRunDec);
        btnRunInc = findViewById(R.id.btnRunInc);

        layoutPitchInputGroup = findViewById(R.id.layoutPitchInputGroup);
        tvLabelPitch = findViewById(R.id.tvLabelPitch);
        etPitch = findViewById(R.id.etPitch);
        btnPitchDec = findViewById(R.id.btnPitchDec);
        btnPitchInc = findViewById(R.id.btnPitchInc);

        tvLabelStartInvert = findViewById(R.id.tvLabelStartInvert);
        etStartInvert = findViewById(R.id.etStartInvert);
        btnStartInvDec = findViewById(R.id.btnStartInvDec);
        btnStartInvInc = findViewById(R.id.btnStartInvInc);

        layoutEndInvertGroup = findViewById(R.id.layoutEndInvertGroup);
        tvLabelEndInvert = findViewById(R.id.tvLabelEndInvert);
        etEndInvert = findViewById(R.id.etEndInvert);
        btnEndInvDec = findViewById(R.id.btnEndInvDec);
        btnEndInvInc = findViewById(R.id.btnEndInvInc);

        layoutDropInputGroup = findViewById(R.id.layoutDropInputGroup);
        tvLabelTargetDrop = findViewById(R.id.tvLabelTargetDrop);
        etTargetDrop = findViewById(R.id.etTargetDrop);
        btnDropDec = findViewById(R.id.btnDropDec);
        btnDropInc = findViewById(R.id.btnDropInc);

        btnSaveRun = findViewById(R.id.btnSaveRun);
        btnCopyNote = findViewById(R.id.btnCopyNote);
        btnReset = findViewById(R.id.btnReset);

        tvEmptyLog = findViewById(R.id.tvEmptyLog);
        llSavedRunsContainer = findViewById(R.id.llSavedRunsContainer);
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                int nextMode = (themeMode == 2) ? 1 : 2; // Quick toggle between Dark and Light
                setThemeMode(nextMode);
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                showSettingsDialog();
            }
        });

        // Mode Switching
        btnModeFall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                currentMode = MODE_RUN_PITCH;
                updateModeUI();
                recalculateAndRender();
            }
        });

        btnModeSlope.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                currentMode = MODE_INVERTS_RUN;
                updateModeUI();
                recalculateAndRender();
            }
        });

        btnModeRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                currentMode = MODE_DROP_PITCH;
                updateModeUI();
                recalculateAndRender();
            }
        });

        // Pipe Size Selection
        View.OnClickListener pipeSizeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                if (v == btnPipe2) selectedPipeSize = PlumbingEngine.PIPE_SIZE_LE_2_INCH;
                else if (v == btnPipe3) selectedPipeSize = PlumbingEngine.PIPE_SIZE_3_INCH;
                else if (v == btnPipe6) selectedPipeSize = PlumbingEngine.PIPE_SIZE_4_TO_6_INCH;
                else if (v == btnPipe8) selectedPipeSize = PlumbingEngine.PIPE_SIZE_GE_8_INCH;

                updatePipeSizeUI();
                recalculateAndRender();
            }
        };
        btnPipe2.setOnClickListener(pipeSizeListener);
        btnPipe3.setOnClickListener(pipeSizeListener);
        btnPipe6.setOnClickListener(pipeSizeListener);
        btnPipe8.setOnClickListener(pipeSizeListener);

        // Pitch Presets Selection
        View.OnClickListener pitchPresetListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                if (v == btnPitch1_16) currentPitch = 0.0625;
                else if (v == btnPitch1_8) currentPitch = 0.125;
                else if (v == btnPitch1_4) currentPitch = 0.25;
                else if (v == btnPitch1_2) currentPitch = 0.50;

                etPitch.setText(String.format(Locale.US, "%.3f", currentPitch));
                updatePitchPresetUI();
                recalculateAndRender();
            }
        };
        btnPitch1_16.setOnClickListener(pitchPresetListener);
        btnPitch1_8.setOnClickListener(pitchPresetListener);
        btnPitch1_4.setOnClickListener(pitchPresetListener);
        btnPitch1_2.setOnClickListener(pitchPresetListener);

        // Micro-Steppers
        btnRunDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etRunLength, isMetric ? 10.0 : 25.0);
                val = Math.max(isMetric ? 0.5 : 1.0, val - (isMetric ? 0.5 : 1.0));
                etRunLength.setText(String.format(Locale.US, isMetric ? "%.1f" : "%.1f", val));
            }
        });
        btnRunInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etRunLength, isMetric ? 10.0 : 25.0);
                val += (isMetric ? 0.5 : 1.0);
                etRunLength.setText(String.format(Locale.US, isMetric ? "%.1f" : "%.1f", val));
            }
        });

        btnPitchDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etPitch, 0.25);
                val = Math.max(0.01, val - 0.0625);
                currentPitch = val;
                etPitch.setText(String.format(Locale.US, "%.3f", val));
                updatePitchPresetUI();
            }
        });
        btnPitchInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etPitch, 0.25);
                val += 0.0625;
                currentPitch = val;
                etPitch.setText(String.format(Locale.US, "%.3f", val));
                updatePitchPresetUI();
            }
        });

        btnStartInvDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etStartInvert, isMetric ? 600.0 : 24.0);
                val = Math.max(0.0, val - (isMetric ? 25.0 : 1.0));
                etStartInvert.setText(String.format(Locale.US, isMetric ? "%.0f" : "%.1f", val));
            }
        });
        btnStartInvInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etStartInvert, isMetric ? 600.0 : 24.0);
                val += (isMetric ? 25.0 : 1.0);
                etStartInvert.setText(String.format(Locale.US, isMetric ? "%.0f" : "%.1f", val));
            }
        });

        btnEndInvDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etEndInvert, isMetric ? 750.0 : 30.25);
                val = Math.max(0.0, val - (isMetric ? 25.0 : 1.0));
                etEndInvert.setText(String.format(Locale.US, isMetric ? "%.0f" : "%.2f", val));
            }
        });
        btnEndInvInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etEndInvert, isMetric ? 750.0 : 30.25);
                val += (isMetric ? 25.0 : 1.0);
                etEndInvert.setText(String.format(Locale.US, isMetric ? "%.0f" : "%.2f", val));
            }
        });

        btnDropDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etTargetDrop, isMetric ? 150.0 : 6.25);
                val = Math.max(0.1, val - (isMetric ? 10.0 : 0.5));
                etTargetDrop.setText(String.format(Locale.US, isMetric ? "%.1f" : "%.2f", val));
            }
        });
        btnDropInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etTargetDrop, isMetric ? 150.0 : 6.25);
                val += (isMetric ? 10.0 : 0.5);
                etTargetDrop.setText(String.format(Locale.US, isMetric ? "%.1f" : "%.2f", val));
            }
        });

        // Defensive TextWatchers
        TextWatcher autoCalculateWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingInputs) {
                    recalculateAndRender();
                }
            }
        };

        etRunLength.addTextChangedListener(autoCalculateWatcher);
        etPitch.addTextChangedListener(autoCalculateWatcher);
        etStartInvert.addTextChangedListener(autoCalculateWatcher);
        etEndInvert.addTextChangedListener(autoCalculateWatcher);
        etTargetDrop.addTextChangedListener(autoCalculateWatcher);

        // Action Buttons
        btnSaveRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                saveCurrentRunToLog();
            }
        });

        btnCopyNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                copyFieldNoteToClipboard();
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                resetInputsToDefaults();
            }
        });
    }

    private void updateUnitLabels() {
        if (isMetric) {
            tvLabelRunLength.setText("TRENCH RUN LENGTH (M)");
            tvLabelPitch.setText("PIPE PITCH (IN/FT OR MM/M)");
            tvLabelStartInvert.setText("START INVERT DEPTH (MM)");
            tvLabelEndInvert.setText("END INVERT DEPTH (MM)");
            tvLabelTargetDrop.setText("TARGET TOTAL DROP (MM)");
        } else {
            tvLabelRunLength.setText("TRENCH RUN LENGTH (FT)");
            tvLabelPitch.setText("PIPE PITCH / SLOPE (IN/FT)");
            tvLabelStartInvert.setText("START INVERT DEPTH (IN)");
            tvLabelEndInvert.setText("END INVERT DEPTH (IN)");
            tvLabelTargetDrop.setText("TARGET TOTAL DROP (IN)");
        }
    }

    private void updateModeUI() {
        btnModeFall.setBackgroundResource(currentMode == MODE_RUN_PITCH ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnModeFall.setTextColor(getResources().getColor(currentMode == MODE_RUN_PITCH ? R.color.text_on_accent : R.color.text_primary));

        btnModeSlope.setBackgroundResource(currentMode == MODE_INVERTS_RUN ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnModeSlope.setTextColor(getResources().getColor(currentMode == MODE_INVERTS_RUN ? R.color.text_on_accent : R.color.text_primary));

        btnModeRun.setBackgroundResource(currentMode == MODE_DROP_PITCH ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnModeRun.setTextColor(getResources().getColor(currentMode == MODE_DROP_PITCH ? R.color.text_on_accent : R.color.text_primary));

        if (currentMode == MODE_RUN_PITCH) {
            layoutPitchInputGroup.setVisibility(View.VISIBLE);
            tvPitchPresetsLabel.setVisibility(View.VISIBLE);
            layoutPitchPresets.setVisibility(View.VISIBLE);
            layoutEndInvertGroup.setVisibility(View.GONE);
            layoutDropInputGroup.setVisibility(View.GONE);
            tvLabelRunLength.setVisibility(View.VISIBLE);
            etRunLength.getParent().bringChildToFront(etRunLength);
            ((View) etRunLength.getParent()).setVisibility(View.VISIBLE);
        } else if (currentMode == MODE_INVERTS_RUN) {
            layoutPitchInputGroup.setVisibility(View.GONE);
            tvPitchPresetsLabel.setVisibility(View.GONE);
            layoutPitchPresets.setVisibility(View.GONE);
            layoutEndInvertGroup.setVisibility(View.VISIBLE);
            layoutDropInputGroup.setVisibility(View.GONE);
            ((View) etRunLength.getParent()).setVisibility(View.VISIBLE);
            tvLabelRunLength.setVisibility(View.VISIBLE);
        } else if (currentMode == MODE_DROP_PITCH) {
            layoutPitchInputGroup.setVisibility(View.VISIBLE);
            tvPitchPresetsLabel.setVisibility(View.VISIBLE);
            layoutPitchPresets.setVisibility(View.VISIBLE);
            layoutEndInvertGroup.setVisibility(View.GONE);
            layoutDropInputGroup.setVisibility(View.VISIBLE);
            ((View) etRunLength.getParent()).setVisibility(View.GONE);
            tvLabelRunLength.setVisibility(View.GONE);
        }
    }

    private void updatePipeSizeUI() {
        btnPipe2.setBackgroundResource(selectedPipeSize == PlumbingEngine.PIPE_SIZE_LE_2_INCH ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPipe2.setTextColor(getResources().getColor(selectedPipeSize == PlumbingEngine.PIPE_SIZE_LE_2_INCH ? R.color.text_on_accent : R.color.text_primary));

        btnPipe3.setBackgroundResource(selectedPipeSize == PlumbingEngine.PIPE_SIZE_3_INCH ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPipe3.setTextColor(getResources().getColor(selectedPipeSize == PlumbingEngine.PIPE_SIZE_3_INCH ? R.color.text_on_accent : R.color.text_primary));

        btnPipe6.setBackgroundResource(selectedPipeSize == PlumbingEngine.PIPE_SIZE_4_TO_6_INCH ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPipe6.setTextColor(getResources().getColor(selectedPipeSize == PlumbingEngine.PIPE_SIZE_4_TO_6_INCH ? R.color.text_on_accent : R.color.text_primary));

        btnPipe8.setBackgroundResource(selectedPipeSize == PlumbingEngine.PIPE_SIZE_GE_8_INCH ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPipe8.setTextColor(getResources().getColor(selectedPipeSize == PlumbingEngine.PIPE_SIZE_GE_8_INCH ? R.color.text_on_accent : R.color.text_primary));
    }

    private void updatePitchPresetUI() {
        boolean is1_16 = Math.abs(currentPitch - 0.0625) < 0.005;
        boolean is1_8 = Math.abs(currentPitch - 0.125) < 0.005;
        boolean is1_4 = Math.abs(currentPitch - 0.25) < 0.005;
        boolean is1_2 = Math.abs(currentPitch - 0.50) < 0.005;

        btnPitch1_16.setBackgroundResource(is1_16 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPitch1_16.setTextColor(getResources().getColor(is1_16 ? R.color.text_on_accent : R.color.text_primary));

        btnPitch1_8.setBackgroundResource(is1_8 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPitch1_8.setTextColor(getResources().getColor(is1_8 ? R.color.text_on_accent : R.color.text_primary));

        btnPitch1_4.setBackgroundResource(is1_4 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPitch1_4.setTextColor(getResources().getColor(is1_4 ? R.color.text_on_accent : R.color.text_primary));

        btnPitch1_2.setBackgroundResource(is1_2 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnPitch1_2.setTextColor(getResources().getColor(is1_2 ? R.color.text_on_accent : R.color.text_primary));
    }

    private void recalculateAndRender() {
        try {
            double run = parseDouble(etRunLength, isMetric ? 10.0 : 25.0);
            double pitch = parseDouble(etPitch, currentPitch);
            double startInv = parseDouble(etStartInvert, isMetric ? 600.0 : 24.0);
            double endInv = parseDouble(etEndInvert, isMetric ? 750.0 : 30.25);
            double drop = parseDouble(etTargetDrop, isMetric ? 150.0 : 6.25);

            PlumbingEngine.CalculationResult res;
            if (currentMode == MODE_RUN_PITCH) {
                res = PlumbingEngine.solveFallAndInvert(run, pitch, startInv, selectedPipeSize, codeStandard, isMetric);
            } else if (currentMode == MODE_INVERTS_RUN) {
                res = PlumbingEngine.solvePitchFromInverts(startInv, endInv, run, selectedPipeSize, codeStandard, isMetric);
            } else {
                res = PlumbingEngine.solveRunFromDropAndPitch(drop, pitch, startInv, selectedPipeSize, codeStandard, isMetric);
            }

            // Update Hero HUD Readouts
            tvHeroFall.setText(res.fractionalFall);
            if (isMetric) {
                tvHeroFallSub.setText(String.format(Locale.US, "%.1f mm (Total Fall)", res.totalFall));
                tvHeroPitch.setText(String.format(Locale.US, "%.1f mm/m", res.pitchMmPerM));
                tvHeroGrade.setText(String.format(Locale.US, "%.2f%% • 1:%d Ratio", res.gradePercent, res.ratioDenominator));
                tvHeroInverts.setText(String.format(Locale.US, "%.0f → %.0f mm", res.startInvert, res.endInvert));
            } else {
                tvHeroFallSub.setText(String.format(Locale.US, "%.2f in • %.1f mm", res.totalFall, res.totalFall * 25.4));
                tvHeroPitch.setText(String.format(Locale.US, "%.3f\" / ft", res.pitchInPerFt));
                tvHeroGrade.setText(String.format(Locale.US, "%.2f%% • 1:%d Ratio", res.gradePercent, res.ratioDenominator));
                tvHeroInverts.setText(String.format(Locale.US, "%.1f\" → %.1f\"", res.startInvert, res.endInvert));
            }

            // Update Compliance Banner
            if (res.complianceLevel == 0) { // Pass
                layoutComplianceBadge.setBackgroundResource(R.drawable.bg_badge_pass);
                ivComplianceIcon.setImageResource(R.drawable.ic_check);
                tvComplianceStatus.setText(res.complianceTitle);
                tvComplianceStatus.setTextColor(getResources().getColor(R.color.badge_text_pass));
                tvComplianceReason.setText(res.complianceReason);
                tvComplianceReason.setTextColor(getResources().getColor(R.color.badge_text_pass));
            } else if (res.complianceLevel == 1) { // Warning: Steep
                layoutComplianceBadge.setBackgroundResource(R.drawable.bg_badge_warn);
                ivComplianceIcon.setImageResource(R.drawable.ic_warning);
                tvComplianceStatus.setText(res.complianceTitle);
                tvComplianceStatus.setTextColor(getResources().getColor(R.color.badge_text_warn));
                tvComplianceReason.setText(res.complianceReason);
                tvComplianceReason.setTextColor(getResources().getColor(R.color.badge_text_warn));
            } else { // Fail: Deficient
                layoutComplianceBadge.setBackgroundResource(R.drawable.bg_badge_alert);
                ivComplianceIcon.setImageResource(R.drawable.ic_warning);
                tvComplianceStatus.setText(res.complianceTitle);
                tvComplianceStatus.setTextColor(getResources().getColor(R.color.badge_text_alert));
                tvComplianceReason.setText(res.complianceReason);
                tvComplianceReason.setTextColor(getResources().getColor(R.color.badge_text_alert));
            }

            // Update Live Diagram View
            pipeDiagramView.updateData(res.runLength, res.pitchInPerFt, res.startInvert,
                    res.endInvert, res.totalFall, res.gradePercent, isMetric, res.isCompliant, res.complianceTitle);

        } catch (Exception ex) {
            // Defensive input safeguarding
        }
    }

    private void saveCurrentRunToLog() {
        try {
            double run = parseDouble(etRunLength, isMetric ? 10.0 : 25.0);
            double pitch = parseDouble(etPitch, currentPitch);
            double startInv = parseDouble(etStartInvert, isMetric ? 600.0 : 24.0);
            double endInv = parseDouble(etEndInvert, isMetric ? 750.0 : 30.25);
            double drop = parseDouble(etTargetDrop, isMetric ? 150.0 : 6.25);

            PlumbingEngine.CalculationResult res;
            if (currentMode == MODE_RUN_PITCH) {
                res = PlumbingEngine.solveFallAndInvert(run, pitch, startInv, selectedPipeSize, codeStandard, isMetric);
            } else if (currentMode == MODE_INVERTS_RUN) {
                res = PlumbingEngine.solvePitchFromInverts(startInv, endInv, run, selectedPipeSize, codeStandard, isMetric);
            } else {
                res = PlumbingEngine.solveRunFromDropAndPitch(drop, pitch, startInv, selectedPipeSize, codeStandard, isMetric);
            }

            String pipeTag = getPipeSizeLabel(selectedPipeSize);
            String runName = "Trench #" + (dbHelper.getRecentRuns(100).size() + 1);

            dbHelper.insertRun(runName, res.runLength, res.pitchInPerFt, res.startInvert,
                    res.endInvert, res.totalFall, res.gradePercent, pipeTag, isMetric);

            Toast.makeText(this, "Run saved to Job Trench Log", Toast.LENGTH_SHORT).show();
            loadSavedRuns();
        } catch (Exception ex) {
            Toast.makeText(this, "Error saving run", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSavedRuns() {
        List<PlumbDatabaseHelper.TrenchRunRecord> list = dbHelper.getRecentRuns(20);
        llSavedRunsContainer.removeAllViews();

        if (list == null || list.isEmpty()) {
            tvEmptyLog.setVisibility(View.VISIBLE);
        } else {
            tvEmptyLog.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (final PlumbDatabaseHelper.TrenchRunRecord r : list) {
                View itemView = inflater.inflate(R.layout.item_saved_run, llSavedRunsContainer, false);
                TextView tvTitle = itemView.findViewById(R.id.tvItemTitle);
                TextView tvSpecs = itemView.findViewById(R.id.tvItemSpecs);
                TextView tvInverts = itemView.findViewById(R.id.tvItemInverts);
                ImageButton btnCopy = itemView.findViewById(R.id.btnItemCopy);
                ImageButton btnDel = itemView.findViewById(R.id.btnItemDelete);

                tvTitle.setText(r.name + " (" + r.pipeTag + ")");
                if (r.isMetric) {
                    tvSpecs.setText(String.format(Locale.US, "%.1f m @ %.2f%% Grade (%.1f mm/m)",
                            r.runLength, r.gradePercent, r.gradePercent * 10));
                    tvInverts.setText(String.format(Locale.US, "Drop: %.1f mm • Inv: %.0f → %.0f mm",
                            r.totalFall, r.startInvert, r.endInvert));
                } else {
                    tvSpecs.setText(String.format(Locale.US, "%.1f ft @ %.3f\"/ft (%.2f%%)",
                            r.runLength, r.pitchInPerFt, r.gradePercent));
                    tvInverts.setText(String.format(Locale.US, "Drop: %s (%.2f\") • Inv: %.1f\" → %.1f\"",
                            PlumbingEngine.formatFractionalInches(r.totalFall), r.totalFall, r.startInvert, r.endInvert));
                }

                btnCopy.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hapticClick(v);
                        String note = String.format(Locale.US,
                                "PlumbBob Trench Spec:\n- Run: %s (%s)\n- Length: %.1f %s\n- Pitch: %.3f\"/ft (%.2f%%)\n- Drop: %.2f %s\n- Inverts: %.1f → %.1f %s",
                                r.name, r.pipeTag, r.runLength, r.isMetric ? "m" : "ft",
                                r.pitchInPerFt, r.gradePercent, r.totalFall, r.isMetric ? "mm" : "in",
                                r.startInvert, r.endInvert, r.isMetric ? "mm" : "in");

                        copyToClipboard("Trench Run", note);
                        Toast.makeText(MainActivity.this, "Copied run note to clipboard", Toast.LENGTH_SHORT).show();
                    }
                });

                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hapticClick(v);
                        dbHelper.deleteRun(r.id);
                        loadSavedRuns();
                    }
                });

                llSavedRunsContainer.addView(itemView);
            }
        }
    }

    private void copyFieldNoteToClipboard() {
        try {
            double run = parseDouble(etRunLength, isMetric ? 10.0 : 25.0);
            double pitch = parseDouble(etPitch, currentPitch);
            double startInv = parseDouble(etStartInvert, isMetric ? 600.0 : 24.0);
            double endInv = parseDouble(etEndInvert, isMetric ? 750.0 : 30.25);
            double drop = parseDouble(etTargetDrop, isMetric ? 150.0 : 6.25);

            PlumbingEngine.CalculationResult res;
            if (currentMode == MODE_RUN_PITCH) {
                res = PlumbingEngine.solveFallAndInvert(run, pitch, startInv, selectedPipeSize, codeStandard, isMetric);
            } else if (currentMode == MODE_INVERTS_RUN) {
                res = PlumbingEngine.solvePitchFromInverts(startInv, endInv, run, selectedPipeSize, codeStandard, isMetric);
            } else {
                res = PlumbingEngine.solveRunFromDropAndPitch(drop, pitch, startInv, selectedPipeSize, codeStandard, isMetric);
            }

            String note = String.format(Locale.US,
                    "📐 PlumbBob Field Trench Report\n" +
                    "───────────────────────\n" +
                    "Pipe Spec: %s\n" +
                    "Code Standard: %s\n" +
                    "Run Length: %.1f %s\n" +
                    "Slope Rate: %.3f\"/ft (%.2f%% Grade, 1:%d)\n" +
                    "Total Fall / Drop: %s (%.2f in)\n" +
                    "Start Invert Depth: %.1f %s\n" +
                    "End Invert Depth: %.1f %s\n" +
                    "Compliance Status: %s\n" +
                    "Notes: %s\n" +
                    "───────────────────────\n" +
                    "Verified offline via PlumbBob",
                    getPipeSizeLabel(selectedPipeSize),
                    codeStandard == PlumbingEngine.CODE_UPC ? "Uniform Plumbing Code (UPC)" : "International Plumbing Code (IPC)",
                    res.runLength, isMetric ? "m" : "ft",
                    res.pitchInPerFt, res.gradePercent, res.ratioDenominator,
                    res.fractionalFall, res.totalFall,
                    res.startInvert, isMetric ? "mm" : "in",
                    res.endInvert, isMetric ? "mm" : "in",
                    res.complianceTitle,
                    res.complianceReason);

            copyToClipboard("PlumbBob Trench Report", note);
            Toast.makeText(this, "Plumber's field note copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Error generating note", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetInputsToDefaults() {
        isUpdatingInputs = true;
        currentMode = MODE_RUN_PITCH;
        selectedPipeSize = PlumbingEngine.PIPE_SIZE_3_INCH;
        currentPitch = 0.25;

        if (isMetric) {
            etRunLength.setText("10.0");
            etPitch.setText("0.250");
            etStartInvert.setText("600.0");
            etEndInvert.setText("750.0");
            etTargetDrop.setText("150.0");
        } else {
            etRunLength.setText("25.0");
            etPitch.setText("0.250");
            etStartInvert.setText("24.0");
            etEndInvert.setText("30.25");
            etTargetDrop.setText("6.25");
        }

        isUpdatingInputs = false;
        updateModeUI();
        updatePipeSizeUI();
        updatePitchPresetUI();
        recalculateAndRender();
        Toast.makeText(this, "Inputs reset to standard benchmarks", Toast.LENGTH_SHORT).show();
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Theme buttons
        final Button btnThemeLight = dialogView.findViewById(R.id.btnThemeLight);
        final Button btnThemeDark = dialogView.findViewById(R.id.btnThemeDark);
        final Button btnThemeAuto = dialogView.findViewById(R.id.btnThemeAuto);

        updateDialogThemeButtons(btnThemeLight, btnThemeDark, btnThemeAuto, themeMode);

        btnThemeLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                setThemeMode(1);
                dialog.dismiss();
            }
        });
        btnThemeDark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                setThemeMode(2);
                dialog.dismiss();
            }
        });
        btnThemeAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                setThemeMode(0);
                dialog.dismiss();
            }
        });

        // Unit system buttons
        final Button btnUnitImperial = dialogView.findViewById(R.id.btnUnitImperial);
        final Button btnUnitMetric = dialogView.findViewById(R.id.btnUnitMetric);

        updateDialogUnitButtons(btnUnitImperial, btnUnitMetric, isMetric);

        btnUnitImperial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                isMetric = false;
                prefs.edit().putBoolean(KEY_UNIT_METRIC, false).apply();
                updateDialogUnitButtons(btnUnitImperial, btnUnitMetric, isMetric);
                updateUnitLabels();
                recalculateAndRender();
                loadSavedRuns();
            }
        });

        btnUnitMetric.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                isMetric = true;
                prefs.edit().putBoolean(KEY_UNIT_METRIC, true).apply();
                updateDialogUnitButtons(btnUnitImperial, btnUnitMetric, isMetric);
                updateUnitLabels();
                recalculateAndRender();
                loadSavedRuns();
            }
        });

        // Code Standard buttons
        final Button btnCodeIPC = dialogView.findViewById(R.id.btnCodeIPC);
        final Button btnCodeUPC = dialogView.findViewById(R.id.btnCodeUPC);

        updateDialogCodeButtons(btnCodeIPC, btnCodeUPC, codeStandard);

        btnCodeIPC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                codeStandard = PlumbingEngine.CODE_IPC;
                prefs.edit().putInt(KEY_CODE_STANDARD, PlumbingEngine.CODE_IPC).apply();
                updateDialogCodeButtons(btnCodeIPC, btnCodeUPC, codeStandard);
                recalculateAndRender();
            }
        });

        btnCodeUPC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                codeStandard = PlumbingEngine.CODE_UPC;
                prefs.edit().putInt(KEY_CODE_STANDARD, PlumbingEngine.CODE_UPC).apply();
                updateDialogCodeButtons(btnCodeIPC, btnCodeUPC, codeStandard);
                recalculateAndRender();
            }
        });

        // Haptics switch
        Switch swHaptics = dialogView.findViewById(R.id.swHaptics);
        swHaptics.setChecked(hapticsEnabled);
        swHaptics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticsEnabled = ((Switch) v).isChecked();
                prefs.edit().putBoolean(KEY_HAPTICS, hapticsEnabled).apply();
            }
        });

        // Dialog Footer Buttons
        Button btnPrivacyPolicy = dialogView.findViewById(R.id.btnPrivacyPolicy);
        btnPrivacyPolicy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                showPrivacyPolicyDialog();
            }
        });

        Button btnClearLog = dialogView.findViewById(R.id.btnClearLog);
        btnClearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Clear Trench Log")
                        .setMessage("Are you sure you want to delete all saved jobsite trench runs?")
                        .setPositiveButton("Clear All", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                dbHelper.clearAllRuns();
                                loadSavedRuns();
                                Toast.makeText(MainActivity.this, "Trench log cleared", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        Button btnCloseSettings = dialogView.findViewById(R.id.btnCloseSettings);
        btnCloseSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void updateDialogThemeButtons(Button btnLight, Button btnDark, Button btnAuto, int mode) {
        btnLight.setBackgroundResource(mode == 1 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnLight.setTextColor(getResources().getColor(mode == 1 ? R.color.text_on_accent : R.color.text_primary));

        btnDark.setBackgroundResource(mode == 2 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnDark.setTextColor(getResources().getColor(mode == 2 ? R.color.text_on_accent : R.color.text_primary));

        btnAuto.setBackgroundResource(mode == 0 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnAuto.setTextColor(getResources().getColor(mode == 0 ? R.color.text_on_accent : R.color.text_primary));
    }

    private void updateDialogUnitButtons(Button btnImp, Button btnMet, boolean metric) {
        btnImp.setBackgroundResource(!metric ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnImp.setTextColor(getResources().getColor(!metric ? R.color.text_on_accent : R.color.text_primary));

        btnMet.setBackgroundResource(metric ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnMet.setTextColor(getResources().getColor(metric ? R.color.text_on_accent : R.color.text_primary));
    }

    private void updateDialogCodeButtons(Button btnIPC, Button btnUPC, int standard) {
        btnIPC.setBackgroundResource(standard == PlumbingEngine.CODE_IPC ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnIPC.setTextColor(getResources().getColor(standard == PlumbingEngine.CODE_IPC ? R.color.text_on_accent : R.color.text_primary));

        btnUPC.setBackgroundResource(standard == PlumbingEngine.CODE_UPC ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnUPC.setTextColor(getResources().getColor(standard == PlumbingEngine.CODE_UPC ? R.color.text_on_accent : R.color.text_primary));
    }

    private void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        recreate();
    }

    private void showPrivacyPolicyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("PlumbBob Privacy Policy")
                .setMessage("100% Offline Plumbing Utility\n\n" +
                        "PlumbBob by KimRosil App Studio is built with zero third-party SDKs, zero telemetry bloat, and zero network calls.\n\n" +
                        "• All calculation presets and trench logs reside purely in your device's local SQLite database.\n" +
                        "• No personal data, location, or identifiers are ever logged, transmitted, or sold.\n" +
                        "• Free forever. Zero ads, zero subscriptions.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            cm.setPrimaryClip(clip);
        }
    }

    private void hapticClick(View v) {
        if (hapticsEnabled && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private double parseDouble(EditText et, double fallback) {
        if (et == null) return fallback;
        String s = et.getText().toString().trim();
        if (s.isEmpty()) return fallback;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String getPipeSizeLabel(int size) {
        switch (size) {
            case PlumbingEngine.PIPE_SIZE_LE_2_INCH: return "≤ 2\" PVC Drain";
            case PlumbingEngine.PIPE_SIZE_3_INCH: return "3\" - 4\" Building Sewer";
            case PlumbingEngine.PIPE_SIZE_4_TO_6_INCH: return "6\" Lateral Sewer";
            case PlumbingEngine.PIPE_SIZE_GE_8_INCH: return "≥ 8\" Main Trunk";
            default: return "Sanitary Pipe";
        }
    }
}
