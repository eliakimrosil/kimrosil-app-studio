package com.kimrosil.swagedie;

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
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREF_NAME = "swage_die_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String PREF_UNIT_MODE = "pref_unit_mode"; // 0=Metric, 1=Imperial

    // State Keys
    private static final String STATE_TAB = "state_tab";
    private static final String STATE_UNIT = "state_unit";
    private static final String STATE_PUNCH_SHAPE = "state_punch_shape";
    private static final String STATE_PUNCH_MAT = "state_punch_mat";
    private static final String STATE_PUNCH_DIM1 = "state_punch_dim1";
    private static final String STATE_PUNCH_DIM2 = "state_punch_dim2";
    private static final String STATE_PUNCH_THK = "state_punch_thk";
    private static final String STATE_PUNCH_CLR = "state_punch_clr";
    private static final String STATE_SWAGE_MODE = "state_swage_mode";
    private static final String STATE_SWAGE_D0 = "state_swage_d0";
    private static final String STATE_SWAGE_D1 = "state_swage_d1";
    private static final String STATE_SWAGE_WALL = "state_swage_wall";
    private static final String STATE_SWAGE_ANGLE = "state_swage_angle";
    private static final String STATE_BRAKE_THK = "state_brake_thk";
    private static final String STATE_BRAKE_LEN = "state_brake_len";
    private static final String STATE_BRAKE_VDIE = "state_brake_vdie";
    private static final String STATE_ACTIVE_MACHINE = "state_active_machine";

    private SharedPreferences mPrefs;
    private DatabaseHelper mDbHelper;

    // Active Modes
    private int mCurrentTab = 0; // 0=Punch, 1=Swage, 2=Brake, 3=Presets
    private boolean mIsImperial = false; // false=Metric, true=Imperial
    private int mSelectedGauge = 16; // default 16 ga

    // UI - Header
    private Button mBtnUnitToggle;
    private Button mBtnThemeToggle;

    // UI - HUD Canvas & Machine Banner
    private SwageDieCanvasView mCanvasHUD;
    private Spinner mSpnActiveMachine;
    private TextView mTvOverloadAlert;
    private List<DatabaseHelper.MachineProfile> mMachinesList = new ArrayList<>();
    private DatabaseHelper.MachineProfile mActiveMachine = null;

    // UI - Navigation Tabs
    private Button mBtnTabPunch;
    private Button mBtnTabSwage;
    private Button mBtnTabBrake;
    private Button mBtnTabPresets;
    private LinearLayout mLlSectionPunch;
    private LinearLayout mLlSectionSwage;
    private LinearLayout mLlSectionBrake;
    private LinearLayout mLlSectionPresets;

    // UI - Tab 0 (Punch)
    private Spinner mSpnPunchMaterial;
    private Spinner mSpnPunchShape;
    private LinearLayout mLlCustomShearRow;
    private EditText mEtCustomShear;
    private TextView mTvLabelDim1;
    private TextView mTvLabelDim2;
    private LinearLayout mLlContainerDim2;
    private EditText mEtPunchDim1;
    private EditText mEtPunchDim2;
    private TextView mTvLabelThickness;
    private EditText mEtPunchThickness;
    private TextView mTvLabelClearance;
    private EditText mEtClearancePct;
    private TextView mTvValDieOpening;
    private TextView mTvSubDieOpening;
    private TextView mTvValClearanceSide;
    private TextView mTvSubClearanceSide;
    private TextView mTvValPunchTonnage;
    private TextView mTvSubPunchTonnage;
    private TextView mTvValStrippingForce;
    private TextView mTvSubStrippingForce;
    private Button mBtnCopyPunchReport;
    private Button mBtnSavePunchPreset;

    // Gauge Buttons
    private Button[] mGaugeButtons;
    private int[] mGaugeValues = {10, 12, 14, 16, 18, 20, 22, 24, 26};

    // UI - Tab 1 (Swage)
    private Spinner mSpnSwageMode;
    private EditText mEtSwageInitialOd;
    private EditText mEtSwageTargetOd;
    private LinearLayout mLlSwageTubeWallRow;
    private EditText mEtSwageWall;
    private EditText mEtSwageAngle;
    private TextView mTvValSwageAreaRed;
    private TextView mTvSubSwageAreaRed;
    private TextView mTvValSwageDiamRed;
    private TextView mTvSubSwageDiamRed;
    private TextView mTvValSwageForce;
    private TextView mTvSubSwageForce;
    private TextView mTvValSwageContactLen;
    private TextView mTvSubSwageContactLen;
    private TextView mTvAnnealNotice;
    private Button mBtnCopySwageReport;
    private Button mBtnSaveSwagePreset;

    // UI - Tab 2 (Brake)
    private EditText mEtBrakeThickness;
    private EditText mEtBrakeLength;
    private EditText mEtBrakeVDie;
    private Button mBtnBrakePreset6t;
    private Button mBtnBrakePreset8t;
    private Button mBtnBrakePreset10t;
    private TextView mTvValBrakeMinFlange;
    private TextView mTvSubBrakeMinFlange;
    private TextView mTvValBrakeInsideRadius;
    private TextView mTvSubBrakeInsideRadius;
    private TextView mTvValBrakeTonnage;
    private TextView mTvSubBrakeTonnage;
    private TextView mTvValBrakeTonPerMeter;
    private TextView mTvSubBrakeTonPerMeter;
    private Button mBtnCopyBrakeReport;
    private Button mBtnSaveBrakePreset;

    // UI - Tab 3 (Presets & Machines)
    private EditText mEtNewMachineName;
    private EditText mEtNewMachineTonnage;
    private Button mBtnAddCustomMachine;
    private TextView mTvEmptyPresets;
    private LinearLayout mLlPresetsListContainer;

    // Cached Results
    private EngineeringEngine.PunchResult mLastPunchResult = new EngineeringEngine.PunchResult();
    private EngineeringEngine.SwageResult mLastSwageResult = new EngineeringEngine.SwageResult();
    private EngineeringEngine.BrakeResult mLastBrakeResult = new EngineeringEngine.BrakeResult();

    private boolean mIsUpdatingUi = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (systemNight == 2) {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            } else {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            }
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        if (overrideConfiguration != null) {
            int themeMode = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getInt(PREF_THEME_MODE, 0);
            int uiMode = overrideConfiguration.uiMode;
            if (themeMode == 1) {
                uiMode = (uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            } else if (themeMode == 2) {
                uiMode = (uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            }
            overrideConfiguration.uiMode = uiMode;
        }
        super.applyOverrideConfiguration(overrideConfiguration);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        int themeMode = mPrefs.getInt(PREF_THEME_MODE, 0);
        if (themeMode == 1) {
            setTheme(R.style.AppTheme_Light);
        } else if (themeMode == 2) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDbHelper = new DatabaseHelper(this);
        mIsImperial = (mPrefs.getInt(PREF_UNIT_MODE, 0) == 1);

        initViews();
        boolean isDark = (themeMode == 2) || (themeMode == 0 && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        mCanvasHUD.updateTheme(isDark);
        setupMachineProfiles();
        setupNavigationTabs();
        setupPunchControls();
        setupSwageControls();
        setupBrakeControls();
        setupPresetsSection();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            // Default initial calculation
            calculatePunch();
        }
    }

    private void initViews() {
        mBtnUnitToggle = findViewById(R.id.btnUnitToggle);
        mBtnThemeToggle = findViewById(R.id.btnThemeToggle);
        mCanvasHUD = findViewById(R.id.canvasHUD);
        mSpnActiveMachine = findViewById(R.id.spnActiveMachine);
        mTvOverloadAlert = findViewById(R.id.tvOverloadStatus);

        // Update Theme Button Text
        int themeMode = mPrefs.getInt(PREF_THEME_MODE, 0);
        if (themeMode == 1) {
            mBtnThemeToggle.setText(R.string.theme_light);
        } else if (themeMode == 2) {
            mBtnThemeToggle.setText(R.string.theme_dark);
        } else {
            mBtnThemeToggle.setText(R.string.theme_auto);
        }

        mBtnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            cycleTheme();
        });

        // Unit Mode Button Text
        updateUnitToggleLabel();
        mBtnUnitToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            toggleUnits();
        });

        // Tabs
        mBtnTabPunch = findViewById(R.id.btnTabPunch);
        mBtnTabSwage = findViewById(R.id.btnTabSwage);
        mBtnTabBrake = findViewById(R.id.btnTabBrake);
        mBtnTabPresets = findViewById(R.id.btnTabPresets);

        mLlSectionPunch = findViewById(R.id.llSectionPunch);
        mLlSectionSwage = findViewById(R.id.llSectionSwage);
        mLlSectionBrake = findViewById(R.id.llSectionBrake);
        mLlSectionPresets = findViewById(R.id.llSectionPresets);

        // Tab 0 Views
        mSpnPunchMaterial = findViewById(R.id.spnPunchMaterial);
        mSpnPunchShape = findViewById(R.id.spnPunchShape);
        mLlCustomShearRow = findViewById(R.id.llCustomShearRow);
        mEtCustomShear = findViewById(R.id.etCustomShear);
        mTvLabelDim1 = findViewById(R.id.tvLabelDim1);
        mTvLabelDim2 = findViewById(R.id.tvLabelDim2);
        mLlContainerDim2 = findViewById(R.id.llContainerDim2);
        mEtPunchDim1 = findViewById(R.id.etPunchDim1);
        mEtPunchDim2 = findViewById(R.id.etPunchDim2);
        mTvLabelThickness = findViewById(R.id.tvLabelThickness);
        mEtPunchThickness = findViewById(R.id.etPunchThickness);
        mTvLabelClearance = findViewById(R.id.tvLabelClearance);
        mEtClearancePct = findViewById(R.id.etClearancePct);

        mTvValDieOpening = findViewById(R.id.tvValDieOpening);
        mTvSubDieOpening = findViewById(R.id.tvSubDieOpening);
        mTvValClearanceSide = findViewById(R.id.tvValClearanceSide);
        mTvSubClearanceSide = findViewById(R.id.tvSubClearanceSide);
        mTvValPunchTonnage = findViewById(R.id.tvValPunchTonnage);
        mTvSubPunchTonnage = findViewById(R.id.tvSubPunchTonnage);
        mTvValStrippingForce = findViewById(R.id.tvValStrippingForce);
        mTvSubStrippingForce = findViewById(R.id.tvSubStrippingForce);
        mBtnCopyPunchReport = findViewById(R.id.btnCopyPunchReport);
        mBtnSavePunchPreset = findViewById(R.id.btnSavePunchPreset);

        // Gauge Buttons array
        mGaugeButtons = new Button[]{
                findViewById(R.id.btnGauge10),
                findViewById(R.id.btnGauge12),
                findViewById(R.id.btnGauge14),
                findViewById(R.id.btnGauge16),
                findViewById(R.id.btnGauge18),
                findViewById(R.id.btnGauge20),
                findViewById(R.id.btnGauge22),
                findViewById(R.id.btnGauge24),
                findViewById(R.id.btnGauge26)
        };

        for (int i = 0; i < mGaugeButtons.length; i++) {
            final int gauge = mGaugeValues[i];
            mGaugeButtons[i].setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                selectSheetGauge(gauge);
            });
        }

        // Tab 1 Views
        mSpnSwageMode = findViewById(R.id.spnSwageStockType);
        mEtSwageInitialOd = findViewById(R.id.etSwageInitialOd);
        mEtSwageTargetOd = findViewById(R.id.etSwageTargetOd);
        mLlSwageTubeWallRow = findViewById(R.id.llSwageTubeWallRow);
        mEtSwageWall = findViewById(R.id.etSwageWall);
        mEtSwageAngle = findViewById(R.id.etSwageAngle);
        mTvValSwageAreaRed = findViewById(R.id.tvValSwageAreaRed);
        mTvSubSwageAreaRed = findViewById(R.id.tvSubSwageAreaRed);
        mTvValSwageDiamRed = findViewById(R.id.tvValSwageDiamRed);
        mTvSubSwageDiamRed = findViewById(R.id.tvSubSwageDiamRed);
        mTvValSwageForce = findViewById(R.id.tvValSwageForce);
        mTvSubSwageForce = findViewById(R.id.tvSubSwageForce);
        mTvValSwageContactLen = findViewById(R.id.tvValSwageContactLen);
        mTvSubSwageContactLen = findViewById(R.id.tvSubSwageContactLen);
        mTvAnnealNotice = findViewById(R.id.tvAnnealNotice);
        mBtnCopySwageReport = findViewById(R.id.btnCopySwageReport);
        mBtnSaveSwagePreset = findViewById(R.id.btnSaveSwagePreset);

        // Tab 2 Views
        mEtBrakeThickness = findViewById(R.id.etBrakeThickness);
        mEtBrakeLength = findViewById(R.id.etBrakeLength);
        mEtBrakeVDie = findViewById(R.id.etBrakeVDie);
        mBtnBrakePreset6t = findViewById(R.id.btnBrakePreset6t);
        mBtnBrakePreset8t = findViewById(R.id.btnBrakePreset8t);
        mBtnBrakePreset10t = findViewById(R.id.btnBrakePreset10t);
        mTvValBrakeMinFlange = findViewById(R.id.tvValBrakeMinFlange);
        mTvSubBrakeMinFlange = findViewById(R.id.tvSubBrakeMinFlange);
        mTvValBrakeInsideRadius = findViewById(R.id.tvValBrakeInsideRadius);
        mTvSubBrakeInsideRadius = findViewById(R.id.tvSubBrakeInsideRadius);
        mTvValBrakeTonnage = findViewById(R.id.tvValBrakeTonnage);
        mTvSubBrakeTonnage = findViewById(R.id.tvSubBrakeTonnage);
        mTvValBrakeTonPerMeter = findViewById(R.id.tvValBrakeTonPerMeter);
        mTvSubBrakeTonPerMeter = findViewById(R.id.tvSubBrakeTonPerMeter);
        mBtnCopyBrakeReport = findViewById(R.id.btnCopyBrakeReport);
        mBtnSaveBrakePreset = findViewById(R.id.btnSaveBrakePreset);

        // Tab 3 Views
        mEtNewMachineName = findViewById(R.id.etNewMachineName);
        mEtNewMachineTonnage = findViewById(R.id.etNewMachineTonnage);
        mBtnAddCustomMachine = findViewById(R.id.btnAddCustomMachine);
        mTvEmptyPresets = findViewById(R.id.tvEmptyPresets);
        mLlPresetsListContainer = findViewById(R.id.llPresetsListContainer);

        // Pre-fill initial defaults so inputs are never empty
        mEtPunchDim1.setText(mIsImperial ? "0.750" : "20.0");
        mEtPunchDim2.setText(mIsImperial ? "1.500" : "40.0");
        mEtPunchThickness.setText(mIsImperial ? "0.0598" : "1.52");
        mEtClearancePct.setText("8.0");

        mEtSwageInitialOd.setText(mIsImperial ? "1.000" : "25.4");
        mEtSwageTargetOd.setText(mIsImperial ? "0.750" : "20.0");
        mEtSwageWall.setText(mIsImperial ? "0.065" : "1.65");
        mEtSwageAngle.setText("7.5");

        mEtBrakeThickness.setText(mIsImperial ? "0.0787" : "2.0");
        mEtBrakeLength.setText(mIsImperial ? "36.0" : "1000");
        mEtBrakeVDie.setText(mIsImperial ? "0.625" : "16.0");
    }

    private void setupMachineProfiles() {
        mMachinesList = mDbHelper.getAllMachines();
        ArrayAdapter<DatabaseHelper.MachineProfile> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, mMachinesList);
        mSpnActiveMachine.setAdapter(adapter);

        // Default to first or designated default
        for (int i = 0; i < mMachinesList.size(); i++) {
            if (mMachinesList.get(i).isDefault) {
                mSpnActiveMachine.setSelection(i);
                mActiveMachine = mMachinesList.get(i);
                break;
            }
        }
        if (mActiveMachine == null && !mMachinesList.isEmpty()) {
            mActiveMachine = mMachinesList.get(0);
        }

        mSpnActiveMachine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mActiveMachine = mMachinesList.get(position);
                refreshActiveCalculation();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupNavigationTabs() {
        mBtnTabPunch.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            switchTab(0);
        });
        mBtnTabSwage.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            switchTab(1);
        });
        mBtnTabBrake.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            switchTab(2);
        });
        mBtnTabPresets.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            switchTab(3);
        });
    }

    private void switchTab(int tabIndex) {
        mCurrentTab = tabIndex;

        mBtnTabPunch.setBackgroundResource(tabIndex == 0 ? R.drawable.tab_btn_active : R.drawable.tab_btn_inactive);
        mBtnTabPunch.setTextColor(getColor(tabIndex == 0 ? R.color.btnTextColor : R.color.textPrimary));

        mBtnTabSwage.setBackgroundResource(tabIndex == 1 ? R.drawable.tab_btn_active : R.drawable.tab_btn_inactive);
        mBtnTabSwage.setTextColor(getColor(tabIndex == 1 ? R.color.btnTextColor : R.color.textPrimary));

        mBtnTabBrake.setBackgroundResource(tabIndex == 2 ? R.drawable.tab_btn_active : R.drawable.tab_btn_inactive);
        mBtnTabBrake.setTextColor(getColor(tabIndex == 2 ? R.color.btnTextColor : R.color.textPrimary));

        mBtnTabPresets.setBackgroundResource(tabIndex == 3 ? R.drawable.tab_btn_active : R.drawable.tab_btn_inactive);
        mBtnTabPresets.setTextColor(getColor(tabIndex == 3 ? R.color.btnTextColor : R.color.textPrimary));

        mLlSectionPunch.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        mLlSectionSwage.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        mLlSectionBrake.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
        mLlSectionPresets.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);

        if (tabIndex == 3) {
            refreshPresetsList();
        } else {
            refreshActiveCalculation();
        }
    }

    private void setupPunchControls() {
        // Material Spinner
        List<EngineeringEngine.MaterialPreset> materials = EngineeringEngine.getStandardMaterials();
        ArrayAdapter<EngineeringEngine.MaterialPreset> matAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, materials);
        mSpnPunchMaterial.setAdapter(matAdapter);

        mSpnPunchMaterial.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EngineeringEngine.MaterialPreset mat = materials.get(position);
                if ("custom".equals(mat.id)) {
                    mLlCustomShearRow.setVisibility(View.VISIBLE);
                } else {
                    mLlCustomShearRow.setVisibility(View.GONE);
                }
                if (!mIsUpdatingUi) {
                    mEtClearancePct.setText(String.format(Locale.US, "%.1f", mat.defaultClearancePct));
                }
                calculatePunch();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Shape Spinner
        String[] shapes = {"Round (Hole Punch)", "Rectangle / Slot", "Square", "Custom Perimeter"};
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, shapes);
        mSpnPunchShape.setAdapter(shapeAdapter);

        mSpnPunchShape.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == EngineeringEngine.SHAPE_RECTANGLE) {
                    mLlContainerDim2.setVisibility(View.VISIBLE);
                    mTvLabelDim1.setText(mIsImperial ? "Width (in)" : "Width (mm)");
                    mTvLabelDim2.setText(mIsImperial ? "Length (in)" : "Length (mm)");
                } else if (position == EngineeringEngine.SHAPE_SQUARE) {
                    mLlContainerDim2.setVisibility(View.GONE);
                    mTvLabelDim1.setText(mIsImperial ? "Side (in)" : "Side (mm)");
                } else if (position == EngineeringEngine.SHAPE_CUSTOM_PERIMETER) {
                    mLlContainerDim2.setVisibility(View.GONE);
                    mTvLabelDim1.setText(mIsImperial ? "Perimeter (in)" : "Perimeter (mm)");
                } else {
                    mLlContainerDim2.setVisibility(View.GONE);
                    mTvLabelDim1.setText(mIsImperial ? "Diameter (in)" : "Diameter (mm)");
                }
                calculatePunch();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        TextWatcher punchWatcher = new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mIsUpdatingUi) {
                    calculatePunch();
                }
            }
        };

        mEtPunchDim1.addTextChangedListener(punchWatcher);
        mEtPunchDim2.addTextChangedListener(punchWatcher);
        mEtPunchThickness.addTextChangedListener(punchWatcher);
        mEtClearancePct.addTextChangedListener(punchWatcher);
        mEtCustomShear.addTextChangedListener(punchWatcher);

        mBtnCopyPunchReport.setOnClickListener(v -> copyPunchReport());
        mBtnSavePunchPreset.setOnClickListener(v -> savePunchPreset());
    }

    private void setupSwageControls() {
        String[] modes = {"Tube Swaging (Wall Reduction)", "Solid Rod / Wire Swaging"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        mSpnSwageMode.setAdapter(modeAdapter);

        mSpnSwageMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mLlSwageTubeWallRow.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                calculateSwage();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        TextWatcher swageWatcher = new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mIsUpdatingUi) {
                    calculateSwage();
                }
            }
        };

        mEtSwageInitialOd.addTextChangedListener(swageWatcher);
        mEtSwageTargetOd.addTextChangedListener(swageWatcher);
        mEtSwageWall.addTextChangedListener(swageWatcher);
        mEtSwageAngle.addTextChangedListener(swageWatcher);

        mBtnCopySwageReport.setOnClickListener(v -> copySwageReport());
        mBtnSaveSwagePreset.setOnClickListener(v -> saveSwagePreset());
    }

    private void setupBrakeControls() {
        TextWatcher brakeWatcher = new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mIsUpdatingUi) {
                    calculateBrake();
                }
            }
        };

        mEtBrakeThickness.addTextChangedListener(brakeWatcher);
        mEtBrakeLength.addTextChangedListener(brakeWatcher);
        mEtBrakeVDie.addTextChangedListener(brakeWatcher);

        mBtnBrakePreset6t.setOnClickListener(v -> applyBrakeMultiplier(6.0));
        mBtnBrakePreset8t.setOnClickListener(v -> applyBrakeMultiplier(8.0));
        mBtnBrakePreset10t.setOnClickListener(v -> applyBrakeMultiplier(10.0));

        mBtnCopyBrakeReport.setOnClickListener(v -> copyBrakeReport());
        mBtnSaveBrakePreset.setOnClickListener(v -> saveBrakePreset());
    }

    private void setupPresetsSection() {
        mBtnAddCustomMachine.setOnClickListener(v -> {
            String name = mEtNewMachineName.getText().toString().trim();
            String tonsStr = mEtNewMachineTonnage.getText().toString().trim();
            double tons = parseDoubleSafe(tonsStr, 0);
            if (TextUtils.isEmpty(name) || tons <= 0) {
                Toast.makeText(this, "Enter valid machine name and rated tonnage", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mIsImperial) {
                tons = tons * EngineeringEngine.US_TON_TO_METRIC_TON;
            }
            mDbHelper.addMachine(name, "CUSTOM", tons);
            mEtNewMachineName.setText("");
            mEtNewMachineTonnage.setText("");
            Toast.makeText(this, "Machine registered!", Toast.LENGTH_SHORT).show();
            setupMachineProfiles();
        });
    }

    private void selectSheetGauge(int gauge) {
        mSelectedGauge = gauge;
        for (int i = 0; i < mGaugeValues.length; i++) {
            boolean active = (mGaugeValues[i] == gauge);
            mGaugeButtons[i].setBackgroundResource(active ? R.drawable.chip_gauge_active : R.drawable.chip_gauge_inactive);
            mGaugeButtons[i].setTextColor(getColor(active ? R.color.btnTextColor : R.color.textPrimary));
        }

        // Find thickness
        double thk = 1.52; // default 16ga
        for (EngineeringEngine.GaugeEntry entry : EngineeringEngine.STANDARD_GAUGES) {
            if (entry.gauge == gauge) {
                thk = mIsImperial ? entry.thicknessInch : entry.thicknessMm;
                break;
            }
        }

        mIsUpdatingUi = true;
        mEtPunchThickness.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", thk));
        mEtBrakeThickness.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", thk));
        mIsUpdatingUi = false;

        if (mCurrentTab == 0) {
            calculatePunch();
        } else if (mCurrentTab == 2) {
            calculateBrake();
        }
    }

    // =========================================================================
    // Core Calculation Routines
    // =========================================================================

    private void calculatePunch() {
        int shape = mSpnPunchShape.getSelectedItemPosition();
        EngineeringEngine.MaterialPreset mat = (EngineeringEngine.MaterialPreset) mSpnPunchMaterial.getSelectedItem();
        if (mat == null) return;

        double dim1 = parseDoubleSafe(mEtPunchDim1.getText().toString(), 20.0);
        double dim2 = parseDoubleSafe(mEtPunchDim2.getText().toString(), 40.0);
        double thk = parseDoubleSafe(mEtPunchThickness.getText().toString(), 1.52);
        double clrPct = parseDoubleSafe(mEtClearancePct.getText().toString(), mat.defaultClearancePct);

        double shearMpa = mat.shearStrengthMpa;
        if ("custom".equals(mat.id)) {
            double cVal = parseDoubleSafe(mEtCustomShear.getText().toString(), 300.0);
            shearMpa = mIsImperial ? (cVal / EngineeringEngine.MPA_TO_PSI) : cVal;
        }

        // Convert to mm for calculation engine if imperial
        double dim1Mm = mIsImperial ? (dim1 * EngineeringEngine.INCH_TO_MM) : dim1;
        double dim2Mm = mIsImperial ? (dim2 * EngineeringEngine.INCH_TO_MM) : dim2;
        double thkMm = mIsImperial ? (thk * EngineeringEngine.INCH_TO_MM) : thk;

        mLastPunchResult = EngineeringEngine.calculatePunching(shape, dim1Mm, dim2Mm, thkMm, shearMpa, clrPct);

        // Display results
        if (mIsImperial) {
            double dieOp1In = mLastPunchResult.dieOpening1Mm * EngineeringEngine.MM_TO_INCH;
            double dieOp2In = mLastPunchResult.dieOpening2Mm * EngineeringEngine.MM_TO_INCH;
            double clrSideIn = mLastPunchResult.clearancePerSideMm * EngineeringEngine.MM_TO_INCH;
            double totalClrIn = mLastPunchResult.totalClearanceMm * EngineeringEngine.MM_TO_INCH;

            if (shape == EngineeringEngine.SHAPE_RECTANGLE) {
                mTvValDieOpening.setText(String.format(Locale.US, "%.3f x %.3f in", dieOp1In, dieOp2In));
            } else {
                mTvValDieOpening.setText(String.format(Locale.US, "%.4f in", dieOp1In));
            }
            mTvSubDieOpening.setText(String.format(Locale.US, "%.2f mm", mLastPunchResult.dieOpening1Mm));

            mTvValClearanceSide.setText(String.format(Locale.US, "%.4f in", clrSideIn));
            mTvSubClearanceSide.setText(String.format(Locale.US, "Total: %.4f in (%.1f%%)", totalClrIn, clrPct * 2.0));

            mTvValPunchTonnage.setText(String.format(Locale.US, "%.1f US T", mLastPunchResult.tonnageUs));
            mTvSubPunchTonnage.setText(String.format(Locale.US, "%.1f Metric T | %.0f kN", mLastPunchResult.tonnageMetric, mLastPunchResult.forceKn));

            mTvValStrippingForce.setText(String.format(Locale.US, "%.2f US T", mLastPunchResult.strippingForceUs));
            mTvSubStrippingForce.setText(String.format(Locale.US, "Land: %.3f\" | Draft: 0.5°", mLastPunchResult.recommendedDieLandMm * EngineeringEngine.MM_TO_INCH));
        } else {
            if (shape == EngineeringEngine.SHAPE_RECTANGLE) {
                mTvValDieOpening.setText(String.format(Locale.US, "%.2f x %.2f mm", mLastPunchResult.dieOpening1Mm, mLastPunchResult.dieOpening2Mm));
            } else {
                mTvValDieOpening.setText(String.format(Locale.US, "%.2f mm", mLastPunchResult.dieOpening1Mm));
            }
            mTvSubDieOpening.setText(String.format(Locale.US, "%.4f in", mLastPunchResult.dieOpening1Mm * EngineeringEngine.MM_TO_INCH));

            mTvValClearanceSide.setText(String.format(Locale.US, "%.3f mm", mLastPunchResult.clearancePerSideMm));
            mTvSubClearanceSide.setText(String.format(Locale.US, "Total: %.3f mm (%.1f%%)", mLastPunchResult.totalClearanceMm, clrPct * 2.0));

            mTvValPunchTonnage.setText(String.format(Locale.US, "%.1f T", mLastPunchResult.tonnageMetric));
            mTvSubPunchTonnage.setText(String.format(Locale.US, "%.1f US Ton | %.0f kN", mLastPunchResult.tonnageUs, mLastPunchResult.forceKn));

            mTvValStrippingForce.setText(String.format(Locale.US, "%.2f T", mLastPunchResult.strippingForceMetric));
            mTvSubStrippingForce.setText(String.format(Locale.US, "Land: %.1f mm | Draft: 0.5°", mLastPunchResult.recommendedDieLandMm));
        }

        double machineRating = (mActiveMachine != null) ? mActiveMachine.ratedTonnage : 30.0;
        mCanvasHUD.setPunchData(dim1Mm, thkMm, mLastPunchResult.clearancePerSideMm,
                mLastPunchResult.totalClearanceMm, mLastPunchResult.dieOpening1Mm,
                mLastPunchResult.tonnageMetric, machineRating);

        updateOverloadBanner(mLastPunchResult.tonnageMetric, machineRating);
    }

    private void calculateSwage() {
        int mode = mSpnSwageMode.getSelectedItemPosition();
        double d0 = parseDoubleSafe(mEtSwageInitialOd.getText().toString(), 25.4);
        double d1 = parseDoubleSafe(mEtSwageTargetOd.getText().toString(), 20.0);
        double wall = parseDoubleSafe(mEtSwageWall.getText().toString(), 1.65);
        double angle = parseDoubleSafe(mEtSwageAngle.getText().toString(), 7.5);

        double d0Mm = mIsImperial ? (d0 * EngineeringEngine.INCH_TO_MM) : d0;
        double d1Mm = mIsImperial ? (d1 * EngineeringEngine.INCH_TO_MM) : d1;
        double wallMm = mIsImperial ? (wall * EngineeringEngine.INCH_TO_MM) : wall;

        // Default yield strength for common steel/aluminum tube
        double yieldMpa = 260.0;

        mLastSwageResult = EngineeringEngine.calculateSwaging(mode, d0Mm, d1Mm, wallMm, angle, 0.10, yieldMpa);

        mTvValSwageAreaRed.setText(String.format(Locale.US, "%.1f %%", mLastSwageResult.areaReductionPct));
        if (mLastSwageResult.requiresAnneal) {
            mTvSubSwageAreaRed.setText("High work hardening!");
            mTvSubSwageAreaRed.setTextColor(getColor(R.color.hud_danger));
            mTvAnnealNotice.setBackgroundResource(R.drawable.badge_danger);
            mTvAnnealNotice.setTextColor(getColor(R.color.hud_danger));
        } else {
            mTvSubSwageAreaRed.setText("Safe single pass");
            mTvSubSwageAreaRed.setTextColor(getColor(R.color.hud_safe));
            mTvAnnealNotice.setBackgroundResource(R.drawable.badge_safe);
            mTvAnnealNotice.setTextColor(getColor(R.color.hud_safe));
        }
        mTvAnnealNotice.setText(mLastSwageResult.annealMessage);

        if (mIsImperial) {
            mTvValSwageDiamRed.setText(String.format(Locale.US, "%.3f in", mLastSwageResult.diameterReductionMm * EngineeringEngine.MM_TO_INCH));
            mTvSubSwageDiamRed.setText(String.format(Locale.US, "Ratio: %.2f:1", mLastSwageResult.diameterRatio));

            mTvValSwageForce.setText(String.format(Locale.US, "%.1f US T", mLastSwageResult.swageForceUs));
            mTvSubSwageForce.setText(String.format(Locale.US, "%.1f Metric T | %.0f kN", mLastSwageResult.swageForceMetric, mLastSwageResult.swageForceKn));

            mTvValSwageContactLen.setText(String.format(Locale.US, "%.3f in", mLastSwageResult.contactLengthMm * EngineeringEngine.MM_TO_INCH));
            mTvSubSwageContactLen.setText(String.format(Locale.US, "Die Half-Angle: %.1f°", angle));
        } else {
            mTvValSwageDiamRed.setText(String.format(Locale.US, "%.2f mm", mLastSwageResult.diameterReductionMm));
            mTvSubSwageDiamRed.setText(String.format(Locale.US, "Ratio: %.2f:1", mLastSwageResult.diameterRatio));

            mTvValSwageForce.setText(String.format(Locale.US, "%.1f T", mLastSwageResult.swageForceMetric));
            mTvSubSwageForce.setText(String.format(Locale.US, "%.1f US Ton | %.0f kN", mLastSwageResult.swageForceUs, mLastSwageResult.swageForceKn));

            mTvValSwageContactLen.setText(String.format(Locale.US, "%.1f mm", mLastSwageResult.contactLengthMm));
            mTvSubSwageContactLen.setText(String.format(Locale.US, "Die Half-Angle: %.1f°", angle));
        }

        double machineRating = (mActiveMachine != null) ? mActiveMachine.ratedTonnage : 30.0;
        mCanvasHUD.setSwageData(d0Mm, d1Mm, angle, mLastSwageResult.areaReductionPct,
                mLastSwageResult.swageForceMetric, machineRating);

        updateOverloadBanner(mLastSwageResult.swageForceMetric, machineRating);
    }

    private void calculateBrake() {
        double thk = parseDoubleSafe(mEtBrakeThickness.getText().toString(), 2.0);
        double len = parseDoubleSafe(mEtBrakeLength.getText().toString(), 1000.0);
        double vDie = parseDoubleSafe(mEtBrakeVDie.getText().toString(), 16.0);

        double thkMm = mIsImperial ? (thk * EngineeringEngine.INCH_TO_MM) : thk;
        double lenMm = mIsImperial ? (len * EngineeringEngine.INCH_TO_MM) : len;
        double vDieMm = mIsImperial ? (vDie * EngineeringEngine.INCH_TO_MM) : vDie;

        double utsMpa = 420.0; // Mild Steel UTS

        mLastBrakeResult = EngineeringEngine.calculatePressBrake(thkMm, lenMm, utsMpa, vDieMm);

        if (mIsImperial) {
            mTvValBrakeMinFlange.setText(String.format(Locale.US, "%.3f in", mLastBrakeResult.minFlangeMm * EngineeringEngine.MM_TO_INCH));
            mTvSubBrakeMinFlange.setText("0.7 x V-Die");

            mTvValBrakeInsideRadius.setText(String.format(Locale.US, "%.3f in", mLastBrakeResult.minInsideRadiusMm * EngineeringEngine.MM_TO_INCH));
            mTvSubBrakeInsideRadius.setText("Air bend 0.16V");

            mTvValBrakeTonnage.setText(String.format(Locale.US, "%.1f US T", mLastBrakeResult.totalTonnageUs));
            mTvSubBrakeTonnage.setText(String.format(Locale.US, "%.1f Metric T | %.0f kN", mLastBrakeResult.totalTonnageMetric, mLastBrakeResult.totalForceKn));

            double usTonPerFt = (mLastBrakeResult.totalTonnageUs / (lenMm * EngineeringEngine.MM_TO_INCH / 12.0));
            mTvValBrakeTonPerMeter.setText(String.format(Locale.US, "%.1f T/ft", usTonPerFt));
            mTvSubBrakeTonPerMeter.setText(String.format(Locale.US, "Across %.1f\" bend", lenMm * EngineeringEngine.MM_TO_INCH));
        } else {
            mTvValBrakeMinFlange.setText(String.format(Locale.US, "%.1f mm", mLastBrakeResult.minFlangeMm));
            mTvSubBrakeMinFlange.setText("0.7 x V-Die");

            mTvValBrakeInsideRadius.setText(String.format(Locale.US, "%.2f mm", mLastBrakeResult.minInsideRadiusMm));
            mTvSubBrakeInsideRadius.setText("Air bend 0.16V");

            mTvValBrakeTonnage.setText(String.format(Locale.US, "%.1f T", mLastBrakeResult.totalTonnageMetric));
            mTvSubBrakeTonnage.setText(String.format(Locale.US, "%.1f US Ton | %.0f kN", mLastBrakeResult.totalTonnageUs, mLastBrakeResult.totalForceKn));

            mTvValBrakeTonPerMeter.setText(String.format(Locale.US, "%.1f T/m", mLastBrakeResult.tonnagePerMeterMetric));
            mTvSubBrakeTonPerMeter.setText(String.format(Locale.US, "Across %.0f mm bed", lenMm));
        }

        double machineRating = (mActiveMachine != null) ? mActiveMachine.ratedTonnage : 60.0;
        mCanvasHUD.setBrakeData(thkMm, vDieMm, mLastBrakeResult.minInsideRadiusMm,
                mLastBrakeResult.totalTonnageMetric, machineRating);

        updateOverloadBanner(mLastBrakeResult.totalTonnageMetric, machineRating);
    }

    private void applyBrakeMultiplier(double factor) {
        double thk = parseDoubleSafe(mEtBrakeThickness.getText().toString(), 2.0);
        double vDie = thk * factor;
        mIsUpdatingUi = true;
        mEtBrakeVDie.setText(String.format(Locale.US, mIsImperial ? "%.3f" : "%.1f", vDie));
        mIsUpdatingUi = false;
        calculateBrake();
    }

    private void updateOverloadBanner(double requiredMetricTon, double machineRating) {
        double ratio = (machineRating > 0) ? (requiredMetricTon / machineRating) : 0;
        if (ratio < 0.70) {
            mTvOverloadAlert.setBackgroundResource(R.drawable.badge_safe);
            mTvOverloadAlert.setTextColor(getColor(R.color.hud_safe));
            mTvOverloadAlert.setText(String.format(Locale.US,
                    "SAFE LOAD: %.1f T / %.0f T (%.0f%% Capacity)",
                    requiredMetricTon, machineRating, ratio * 100.0));
        } else if (ratio <= 0.90) {
            mTvOverloadAlert.setBackgroundResource(R.drawable.badge_warn);
            mTvOverloadAlert.setTextColor(getColor(R.color.hud_warn));
            mTvOverloadAlert.setText(String.format(Locale.US,
                    "HEAVY LOAD: %.1f T / %.0f T (%.0f%% Capacity)",
                    requiredMetricTon, machineRating, ratio * 100.0));
        } else {
            mTvOverloadAlert.setBackgroundResource(R.drawable.badge_danger);
            mTvOverloadAlert.setTextColor(getColor(R.color.hud_danger));
            double deficit = requiredMetricTon - machineRating;
            mTvOverloadAlert.setText(String.format(Locale.US,
                    "OVERLOAD HAZARD: Exceeds machine rating by +%.1f T!", deficit));
        }
    }

    private void refreshActiveCalculation() {
        if (mCurrentTab == 0) {
            calculatePunch();
        } else if (mCurrentTab == 1) {
            calculateSwage();
        } else if (mCurrentTab == 2) {
            calculateBrake();
        }
    }

    // =========================================================================
    // Units & Theming
    // =========================================================================

    private void updateUnitToggleLabel() {
        mBtnUnitToggle.setText(mIsImperial ? "Imperial" : "Metric");
    }

    private void toggleUnits() {
        mIsImperial = !mIsImperial;
        mPrefs.edit().putInt(PREF_UNIT_MODE, mIsImperial ? 1 : 0).apply();
        updateUnitToggleLabel();

        // Convert active input values between mm and inches
        mIsUpdatingUi = true;

        // Punch inputs
        double p1 = parseDoubleSafe(mEtPunchDim1.getText().toString(), 20.0);
        double p2 = parseDoubleSafe(mEtPunchDim2.getText().toString(), 40.0);
        double pThk = parseDoubleSafe(mEtPunchThickness.getText().toString(), 1.52);

        if (mIsImperial) {
            mEtPunchDim1.setText(String.format(Locale.US, "%.4f", p1 * EngineeringEngine.MM_TO_INCH));
            mEtPunchDim2.setText(String.format(Locale.US, "%.4f", p2 * EngineeringEngine.MM_TO_INCH));
            mEtPunchThickness.setText(String.format(Locale.US, "%.4f", pThk * EngineeringEngine.MM_TO_INCH));
        } else {
            mEtPunchDim1.setText(String.format(Locale.US, "%.2f", p1 * EngineeringEngine.INCH_TO_MM));
            mEtPunchDim2.setText(String.format(Locale.US, "%.2f", p2 * EngineeringEngine.INCH_TO_MM));
            mEtPunchThickness.setText(String.format(Locale.US, "%.2f", pThk * EngineeringEngine.INCH_TO_MM));
        }

        // Swage inputs
        double swD0 = parseDoubleSafe(mEtSwageInitialOd.getText().toString(), 25.4);
        double swD1 = parseDoubleSafe(mEtSwageTargetOd.getText().toString(), 20.0);
        double swWall = parseDoubleSafe(mEtSwageWall.getText().toString(), 1.65);
        if (mIsImperial) {
            mEtSwageInitialOd.setText(String.format(Locale.US, "%.4f", swD0 * EngineeringEngine.MM_TO_INCH));
            mEtSwageTargetOd.setText(String.format(Locale.US, "%.4f", swD1 * EngineeringEngine.MM_TO_INCH));
            mEtSwageWall.setText(String.format(Locale.US, "%.4f", swWall * EngineeringEngine.MM_TO_INCH));
        } else {
            mEtSwageInitialOd.setText(String.format(Locale.US, "%.2f", swD0 * EngineeringEngine.INCH_TO_MM));
            mEtSwageTargetOd.setText(String.format(Locale.US, "%.2f", swD1 * EngineeringEngine.INCH_TO_MM));
            mEtSwageWall.setText(String.format(Locale.US, "%.2f", swWall * EngineeringEngine.INCH_TO_MM));
        }

        // Brake inputs
        double bThk = parseDoubleSafe(mEtBrakeThickness.getText().toString(), 2.0);
        double bLen = parseDoubleSafe(mEtBrakeLength.getText().toString(), 1000.0);
        double bV = parseDoubleSafe(mEtBrakeVDie.getText().toString(), 16.0);
        if (mIsImperial) {
            mEtBrakeThickness.setText(String.format(Locale.US, "%.4f", bThk * EngineeringEngine.MM_TO_INCH));
            mEtBrakeLength.setText(String.format(Locale.US, "%.2f", bLen * EngineeringEngine.MM_TO_INCH));
            mEtBrakeVDie.setText(String.format(Locale.US, "%.4f", bV * EngineeringEngine.MM_TO_INCH));
        } else {
            mEtBrakeThickness.setText(String.format(Locale.US, "%.2f", bThk * EngineeringEngine.INCH_TO_MM));
            mEtBrakeLength.setText(String.format(Locale.US, "%.0f", bLen * EngineeringEngine.INCH_TO_MM));
            mEtBrakeVDie.setText(String.format(Locale.US, "%.2f", bV * EngineeringEngine.INCH_TO_MM));
        }

        mIsUpdatingUi = false;

        // Update labels
        int shape = mSpnPunchShape.getSelectedItemPosition();
        if (shape == EngineeringEngine.SHAPE_RECTANGLE) {
            mTvLabelDim1.setText(mIsImperial ? "Width (in)" : "Width (mm)");
            mTvLabelDim2.setText(mIsImperial ? "Length (in)" : "Length (mm)");
        } else if (shape == EngineeringEngine.SHAPE_SQUARE) {
            mTvLabelDim1.setText(mIsImperial ? "Side (in)" : "Side (mm)");
        } else if (shape == EngineeringEngine.SHAPE_CUSTOM_PERIMETER) {
            mTvLabelDim1.setText(mIsImperial ? "Perimeter (in)" : "Perimeter (mm)");
        } else {
            mTvLabelDim1.setText(mIsImperial ? "Diameter (in)" : "Diameter (mm)");
        }
        mTvLabelThickness.setText(mIsImperial ? "Thickness (in)" : "Thickness (mm)");

        refreshActiveCalculation();
        Toast.makeText(this, mIsImperial ? "Switched to Imperial units" : "Switched to Metric units", Toast.LENGTH_SHORT).show();
    }

    private void cycleTheme() {
        int current = mPrefs.getInt(PREF_THEME_MODE, 0);
        int next = (current + 1) % 3; // 0=Auto -> 1=Light -> 2=Dark -> 0=Auto
        mPrefs.edit().putInt(PREF_THEME_MODE, next).commit();
        finish();
        startActivity(getIntent());
        overridePendingTransition(0, 0);
    }

    // =========================================================================
    // Report Export & Presets Persistence
    // =========================================================================

    private void copyPunchReport() {
        EngineeringEngine.MaterialPreset mat = (EngineeringEngine.MaterialPreset) mSpnPunchMaterial.getSelectedItem();
        String matName = (mat != null) ? mat.name : "Custom";

        String report = String.format(Locale.US,
                "=== SWAGEDIE PUNCH TRAVELER REPORT ===\n" +
                "Material: %s\n" +
                "Thickness: %s\n" +
                "Punch Tool: %s\n" +
                "Die Opening: %s\n" +
                "Clearance Per Side: %s (Total: %s)\n" +
                "Die Land: 3.0mm | Angular Relief: 0.5°\n" +
                "Punch Force Required: %.1f Metric T (%.1f US T / %.0f kN)\n" +
                "Stripping Force: %.2f Metric T\n" +
                "Selected Press: %s\n" +
                "Timestamp: %s\n",
                matName,
                mEtPunchThickness.getText().toString() + (mIsImperial ? " in" : " mm"),
                mEtPunchDim1.getText().toString() + (mIsImperial ? " in" : " mm"),
                mTvValDieOpening.getText().toString(),
                mTvValClearanceSide.getText().toString(),
                mTvSubClearanceSide.getText().toString(),
                mLastPunchResult.tonnageMetric, mLastPunchResult.tonnageUs, mLastPunchResult.forceKn,
                mLastPunchResult.strippingForceMetric,
                (mActiveMachine != null ? mActiveMachine.name : "None"),
                new java.util.Date().toString()
        );

        copyToClipboard("SwageDie Punch Report", report);
    }

    private void copySwageReport() {
        String report = String.format(Locale.US,
                "=== SWAGEDIE SWAGE TRAVELER REPORT ===\n" +
                "Mode: %s\n" +
                "Initial OD (D0): %s\n" +
                "Target OD (D1): %s\n" +
                "Wall Thickness: %s\n" +
                "Die Half-Angle: %s°\n" +
                "Area Reduction: %.1f%%\n" +
                "Diameter Reduction: %s (Ratio %.2f:1)\n" +
                "Axial Swage Load: %.1f Metric T (%.1f US T / %.0f kN)\n" +
                "Die Contact Length: %s\n" +
                "Anneal Status: %s\n" +
                "Timestamp: %s\n",
                mSpnSwageMode.getSelectedItem().toString(),
                mEtSwageInitialOd.getText().toString() + (mIsImperial ? " in" : " mm"),
                mEtSwageTargetOd.getText().toString() + (mIsImperial ? " in" : " mm"),
                mEtSwageWall.getText().toString() + (mIsImperial ? " in" : " mm"),
                mEtSwageAngle.getText().toString(),
                mLastSwageResult.areaReductionPct,
                mTvValSwageDiamRed.getText().toString(), mLastSwageResult.diameterRatio,
                mLastSwageResult.swageForceMetric, mLastSwageResult.swageForceUs, mLastSwageResult.swageForceKn,
                mTvValSwageContactLen.getText().toString(),
                mLastSwageResult.annealMessage,
                new java.util.Date().toString()
        );

        copyToClipboard("SwageDie Swage Report", report);
    }

    private void copyBrakeReport() {
        String report = String.format(Locale.US,
                "=== SWAGEDIE PRESS BRAKE REPORT ===\n" +
                "Sheet Thickness: %s\n" +
                "Bend Length: %s\n" +
                "V-Die Opening: %s\n" +
                "Min Flange Length: %s\n" +
                "Inside Bend Radius: %s\n" +
                "Total Bending Tonnage: %.1f Metric T (%.1f US T / %.0f kN)\n" +
                "Tonnage Per Meter: %s\n" +
                "Selected Press: %s\n" +
                "Timestamp: %s\n",
                mEtBrakeThickness.getText().toString() + (mIsImperial ? " in" : " mm"),
                mEtBrakeLength.getText().toString() + (mIsImperial ? " in" : " mm"),
                mEtBrakeVDie.getText().toString() + (mIsImperial ? " in" : " mm"),
                mTvValBrakeMinFlange.getText().toString(),
                mTvValBrakeInsideRadius.getText().toString(),
                mLastBrakeResult.totalTonnageMetric, mLastBrakeResult.totalTonnageUs, mLastBrakeResult.totalForceKn,
                mTvValBrakeTonPerMeter.getText().toString(),
                (mActiveMachine != null ? mActiveMachine.name : "None"),
                new java.util.Date().toString()
        );

        copyToClipboard("SwageDie Brake Report", report);
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Traveler report copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    private void savePunchPreset() {
        EngineeringEngine.MaterialPreset mat = (EngineeringEngine.MaterialPreset) mSpnPunchMaterial.getSelectedItem();
        String matName = (mat != null) ? mat.name : "Steel";

        double d1 = parseDoubleSafe(mEtPunchDim1.getText().toString(), 20.0);
        double d2 = parseDoubleSafe(mEtPunchDim2.getText().toString(), 0.0);
        double thk = parseDoubleSafe(mEtPunchThickness.getText().toString(), 1.52);
        double clr = parseDoubleSafe(mEtClearancePct.getText().toString(), 8.0);

        String title = String.format(Locale.US, "Punch %s%s (%s)",
                mIsImperial ? String.format(Locale.US, "%.3f\"", d1) : String.format(Locale.US, "%.1fmm", d1),
                (d2 > 0 ? (mIsImperial ? String.format(Locale.US, "x%.3f\"", d2) : String.format(Locale.US, "x%.1fmm", d2)) : ""),
                matName.split(" ")[0]);

        DatabaseHelper.ToolingPreset preset = new DatabaseHelper.ToolingPreset(
                0, title, 0, matName, thk, d1, d2, clr, mLastPunchResult.tonnageMetric, System.currentTimeMillis()
        );
        mDbHelper.addPreset(preset);
        Toast.makeText(this, "Punch setup saved to presets!", Toast.LENGTH_SHORT).show();
    }

    private void saveSwagePreset() {
        double d0 = parseDoubleSafe(mEtSwageInitialOd.getText().toString(), 25.4);
        double d1 = parseDoubleSafe(mEtSwageTargetOd.getText().toString(), 20.0);
        double wall = parseDoubleSafe(mEtSwageWall.getText().toString(), 1.65);

        String title = String.format(Locale.US, "Swage %s -> %s",
                mIsImperial ? String.format(Locale.US, "%.3f\"", d0) : String.format(Locale.US, "%.1fmm", d0),
                mIsImperial ? String.format(Locale.US, "%.3f\"", d1) : String.format(Locale.US, "%.1fmm", d1));

        DatabaseHelper.ToolingPreset preset = new DatabaseHelper.ToolingPreset(
                0, title, 1, "Tube", wall, d0, d1, 0, mLastSwageResult.swageForceMetric, System.currentTimeMillis()
        );
        mDbHelper.addPreset(preset);
        Toast.makeText(this, "Swage setup saved to presets!", Toast.LENGTH_SHORT).show();
    }

    private void saveBrakePreset() {
        double thk = parseDoubleSafe(mEtBrakeThickness.getText().toString(), 2.0);
        double len = parseDoubleSafe(mEtBrakeLength.getText().toString(), 1000.0);
        double vDie = parseDoubleSafe(mEtBrakeVDie.getText().toString(), 16.0);

        String title = String.format(Locale.US, "Brake V=%s (t=%s)",
                mIsImperial ? String.format(Locale.US, "%.3f\"", vDie) : String.format(Locale.US, "%.1fmm", vDie),
                mIsImperial ? String.format(Locale.US, "%.3f\"", thk) : String.format(Locale.US, "%.1fmm", thk));

        DatabaseHelper.ToolingPreset preset = new DatabaseHelper.ToolingPreset(
                0, title, 2, "Sheet", thk, vDie, len, 0, mLastBrakeResult.totalTonnageMetric, System.currentTimeMillis()
        );
        mDbHelper.addPreset(preset);
        Toast.makeText(this, "Press brake setup saved to presets!", Toast.LENGTH_SHORT).show();
    }

    private void refreshPresetsList() {
        mLlPresetsListContainer.removeAllViews();
        List<DatabaseHelper.ToolingPreset> presets = mDbHelper.getAllPresets();
        if (presets.isEmpty()) {
            mTvEmptyPresets.setVisibility(View.VISIBLE);
            return;
        }
        mTvEmptyPresets.setVisibility(View.GONE);

        for (DatabaseHelper.ToolingPreset preset : presets) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackgroundResource(R.drawable.card_background);
            row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dpToPx(8);
            row.setLayoutParams(lp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            textCol.setLayoutParams(textLp);

            TextView titleTv = new TextView(this);
            titleTv.setText(preset.title);
            titleTv.setTextSize(14f);
            titleTv.setTextColor(getColor(R.color.textPrimary));
            titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
            textCol.addView(titleTv);

            TextView subTv = new TextView(this);
            String modeName = (preset.mode == 0) ? "Punch" : (preset.mode == 1 ? "Swage" : "Brake");
            subTv.setText(String.format(Locale.US, "%s | %.1f Tons | %s", modeName, preset.tonnage, preset.materialName));
            subTv.setTextSize(11f);
            subTv.setTextColor(getColor(R.color.textSecondary));
            textCol.addView(subTv);

            row.addView(textCol);

            // Load button
            Button loadBtn = new Button(this);
            loadBtn.setText("Load");
            loadBtn.setTextSize(11f);
            loadBtn.setTextColor(getColor(R.color.btnTextColor));
            loadBtn.setBackgroundResource(R.drawable.btn_action);
            loadBtn.setPadding(dpToPx(12), 0, dpToPx(12), 0);
            LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36));
            loadLp.rightMargin = dpToPx(6);
            loadBtn.setLayoutParams(loadLp);
            loadBtn.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                loadPreset(preset);
            });
            row.addView(loadBtn);

            // Delete button
            Button delBtn = new Button(this);
            delBtn.setText("Delete");
            delBtn.setTextSize(11f);
            delBtn.setTextColor(getColor(R.color.hud_danger));
            delBtn.setBackgroundResource(R.drawable.tab_btn_inactive);
            delBtn.setPadding(dpToPx(10), 0, dpToPx(10), 0);
            delBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36)));
            delBtn.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                mDbHelper.deletePreset(preset.id);
                refreshPresetsList();
            });
            row.addView(delBtn);

            mLlPresetsListContainer.addView(row);
        }
    }

    private void loadPreset(DatabaseHelper.ToolingPreset preset) {
        mIsUpdatingUi = true;
        if (preset.mode == 0) {
            mEtPunchDim1.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", preset.dim1Mm));
            if (preset.dim2Mm > 0) {
                mEtPunchDim2.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", preset.dim2Mm));
            }
            mEtPunchThickness.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", preset.thicknessMm));
            mEtClearancePct.setText(String.format(Locale.US, "%.1f", preset.clearancePct));
            mIsUpdatingUi = false;
            switchTab(0);
            calculatePunch();
            Toast.makeText(this, "Punch setup loaded: " + preset.title, Toast.LENGTH_SHORT).show();
        } else if (preset.mode == 1) {
            mEtSwageInitialOd.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", preset.dim1Mm));
            mEtSwageTargetOd.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", preset.dim2Mm));
            mEtSwageWall.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", preset.thicknessMm));
            mIsUpdatingUi = false;
            switchTab(1);
            calculateSwage();
            Toast.makeText(this, "Swage setup loaded: " + preset.title, Toast.LENGTH_SHORT).show();
        } else {
            mEtBrakeVDie.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", preset.dim1Mm));
            mEtBrakeLength.setText(String.format(Locale.US, mIsImperial ? "%.2f" : "%.0f", preset.dim2Mm));
            mEtBrakeThickness.setText(String.format(Locale.US, mIsImperial ? "%.4f" : "%.2f", preset.thicknessMm));
            mIsUpdatingUi = false;
            switchTab(2);
            calculateBrake();
            Toast.makeText(this, "Press brake setup loaded: " + preset.title, Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // State Preservation Across Orientation Changes
    // =========================================================================

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, mCurrentTab);
        outState.putBoolean(STATE_UNIT, mIsImperial);
        outState.putInt(STATE_PUNCH_SHAPE, mSpnPunchShape.getSelectedItemPosition());
        outState.putInt(STATE_PUNCH_MAT, mSpnPunchMaterial.getSelectedItemPosition());
        outState.putString(STATE_PUNCH_DIM1, mEtPunchDim1.getText().toString());
        outState.putString(STATE_PUNCH_DIM2, mEtPunchDim2.getText().toString());
        outState.putString(STATE_PUNCH_THK, mEtPunchThickness.getText().toString());
        outState.putString(STATE_PUNCH_CLR, mEtClearancePct.getText().toString());
        outState.putInt(STATE_SWAGE_MODE, mSpnSwageMode.getSelectedItemPosition());
        outState.putString(STATE_SWAGE_D0, mEtSwageInitialOd.getText().toString());
        outState.putString(STATE_SWAGE_D1, mEtSwageTargetOd.getText().toString());
        outState.putString(STATE_SWAGE_WALL, mEtSwageWall.getText().toString());
        outState.putString(STATE_SWAGE_ANGLE, mEtSwageAngle.getText().toString());
        outState.putString(STATE_BRAKE_THK, mEtBrakeThickness.getText().toString());
        outState.putString(STATE_BRAKE_LEN, mEtBrakeLength.getText().toString());
        outState.putString(STATE_BRAKE_VDIE, mEtBrakeVDie.getText().toString());
        outState.putInt(STATE_ACTIVE_MACHINE, mSpnActiveMachine.getSelectedItemPosition());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
    }

    private void restoreState(Bundle state) {
        mIsUpdatingUi = true;
        mCurrentTab = state.getInt(STATE_TAB, 0);
        mIsImperial = state.getBoolean(STATE_UNIT, false);
        updateUnitToggleLabel();

        mSpnPunchShape.setSelection(state.getInt(STATE_PUNCH_SHAPE, 0));
        mSpnPunchMaterial.setSelection(state.getInt(STATE_PUNCH_MAT, 0));
        mEtPunchDim1.setText(state.getString(STATE_PUNCH_DIM1, "20.0"));
        mEtPunchDim2.setText(state.getString(STATE_PUNCH_DIM2, "40.0"));
        mEtPunchThickness.setText(state.getString(STATE_PUNCH_THK, "1.52"));
        mEtClearancePct.setText(state.getString(STATE_PUNCH_CLR, "8.0"));

        mSpnSwageMode.setSelection(state.getInt(STATE_SWAGE_MODE, 0));
        mEtSwageInitialOd.setText(state.getString(STATE_SWAGE_D0, "25.4"));
        mEtSwageTargetOd.setText(state.getString(STATE_SWAGE_D1, "20.0"));
        mEtSwageWall.setText(state.getString(STATE_SWAGE_WALL, "1.65"));
        mEtSwageAngle.setText(state.getString(STATE_SWAGE_ANGLE, "7.5"));

        mEtBrakeThickness.setText(state.getString(STATE_BRAKE_THK, "2.0"));
        mEtBrakeLength.setText(state.getString(STATE_BRAKE_LEN, "1000"));
        mEtBrakeVDie.setText(state.getString(STATE_BRAKE_VDIE, "16.0"));

        int machPos = state.getInt(STATE_ACTIVE_MACHINE, 0);
        if (machPos >= 0 && machPos < mMachinesList.size()) {
            mSpnActiveMachine.setSelection(machPos);
            mActiveMachine = mMachinesList.get(machPos);
        }

        mIsUpdatingUi = false;
        switchTab(mCurrentTab);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private double parseDoubleSafe(String val, double fallback) {
        if (TextUtils.isEmpty(val)) return fallback;
        try {
            double d = Double.parseDouble(val.trim());
            if (Double.isNaN(d) || Double.isInfinite(d)) return fallback;
            return Math.max(0.0, d);
        } catch (Exception e) {
            return fallback;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
