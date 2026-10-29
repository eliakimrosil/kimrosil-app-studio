package com.kimrosil.torquespec;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "TorqueSpecPrefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_IS_METRIC = "is_metric";
    private static final String KEY_PRELOAD_PCT = "preload_pct";
    private static final String KEY_K_FACTOR = "k_factor";
    private static final String KEY_BOLT_IDX = "bolt_idx";
    private static final String KEY_GRADE_IDX = "grade_idx";
    private static final String KEY_LUBE_IDX = "lube_idx";
    private static final String KEY_ACTIVE_TAB = "active_tab";

    // Fastener Data Structures
    private static class MetricBolt {
        final String name;
        final double diaMm;
        final double coarsePitch;
        final double finePitch;
        final double stressAreaMm2;
        final double tapDrillMm;
        final String socketHex;

        MetricBolt(String name, double diaMm, double coarsePitch, double finePitch,
                   double stressAreaMm2, double tapDrillMm, String socketHex) {
            this.name = name;
            this.diaMm = diaMm;
            this.coarsePitch = coarsePitch;
            this.finePitch = finePitch;
            this.stressAreaMm2 = stressAreaMm2;
            this.tapDrillMm = tapDrillMm;
            this.socketHex = socketHex;
        }
    }

    private static class ImperialBolt {
        final String name;
        final double diaIn;
        final int coarseTpi;
        final int fineTpi;
        final double stressAreaIn2;
        final String tapDrill;
        final String socketHex;

        ImperialBolt(String name, double diaIn, int coarseTpi, int fineTpi,
                     double stressAreaIn2, String tapDrill, String socketHex) {
            this.name = name;
            this.diaIn = diaIn;
            this.coarseTpi = coarseTpi;
            this.fineTpi = fineTpi;
            this.stressAreaIn2 = stressAreaIn2;
            this.tapDrill = tapDrill;
            this.socketHex = socketHex;
        }
    }

    private static class BoltGrade {
        final String name;
        final double proofStrength; // MPa for Metric, PSI for Imperial
        final String desc;

        BoltGrade(String name, double proofStrength, String desc) {
            this.name = name;
            this.proofStrength = proofStrength;
            this.desc = desc;
        }
    }

    private static class LubeOption {
        final String name;
        final double kFactor;

        LubeOption(String name, double kFactor) {
            this.name = name;
            this.kFactor = kFactor;
        }
    }

    private static final MetricBolt[] METRIC_BOLTS = new MetricBolt[]{
            new MetricBolt("M3 x 0.50", 3.0, 0.50, 0.35, 5.03, 2.50, "5.5 mm Hex"),
            new MetricBolt("M4 x 0.70", 4.0, 0.70, 0.50, 8.78, 3.30, "7.0 mm Hex"),
            new MetricBolt("M5 x 0.80", 5.0, 0.80, 0.50, 14.20, 4.20, "8.0 mm Hex"),
            new MetricBolt("M6 x 1.00", 6.0, 1.00, 0.75, 20.10, 5.00, "10.0 mm Hex"),
            new MetricBolt("M8 x 1.25", 8.0, 1.25, 1.00, 36.60, 6.80, "13.0 mm Hex"),
            new MetricBolt("M10 x 1.50", 10.0, 1.50, 1.25, 58.00, 8.50, "17.0 mm (16 ISO)"),
            new MetricBolt("M12 x 1.75", 12.0, 1.75, 1.25, 84.30, 10.20, "19.0 mm (18 ISO)"),
            new MetricBolt("M14 x 2.00", 14.0, 2.00, 1.50, 115.00, 12.00, "22.0 mm (21 ISO)"),
            new MetricBolt("M16 x 2.00", 16.0, 2.00, 1.50, 157.00, 14.00, "24.0 mm Hex"),
            new MetricBolt("M18 x 2.50", 18.0, 2.50, 1.50, 192.00, 15.50, "27.0 mm Hex"),
            new MetricBolt("M20 x 2.50", 20.0, 2.50, 1.50, 245.00, 17.50, "30.0 mm Hex"),
            new MetricBolt("M22 x 2.50", 22.0, 2.50, 1.50, 303.00, 19.50, "32.0 mm Hex"),
            new MetricBolt("M24 x 3.00", 24.0, 3.00, 2.00, 353.00, 21.00, "36.0 mm Hex"),
            new MetricBolt("M27 x 3.00", 27.0, 3.00, 2.00, 459.00, 24.00, "41.0 mm Hex"),
            new MetricBolt("M30 x 3.50", 30.0, 3.50, 2.00, 561.00, 26.50, "46.0 mm Hex")
    };

    private static final ImperialBolt[] IMPERIAL_BOLTS = new ImperialBolt[]{
            new ImperialBolt("#6-32 UNC", 0.1380, 32, 40, 0.00909, "#36 (0.1065\")", "5/16\" Hex"),
            new ImperialBolt("#8-32 UNC", 0.1640, 32, 36, 0.0140, "#29 (0.1360\")", "11/32\" Hex"),
            new ImperialBolt("#10-24 UNC", 0.1900, 24, 32, 0.0175, "#25 (0.1495\")", "3/8\" Hex"),
            new ImperialBolt("1/4\"-20 UNC", 0.2500, 20, 28, 0.0318, "#7 (0.2010\")", "7/16\" Hex"),
            new ImperialBolt("5/16\"-18 UNC", 0.3125, 18, 24, 0.0524, "F (0.2570\")", "1/2\" Hex"),
            new ImperialBolt("3/8\"-16 UNC", 0.3750, 16, 24, 0.0775, "5/16\" (0.3125\")", "9/16\" Hex"),
            new ImperialBolt("7/16\"-14 UNC", 0.4375, 14, 20, 0.1063, "U (0.3680\")", "5/8\" Hex"),
            new ImperialBolt("1/2\"-13 UNC", 0.5000, 13, 20, 0.1419, "27/64\" (0.4219\")", "3/4\" Hex"),
            new ImperialBolt("9/16\"-12 UNC", 0.5625, 12, 18, 0.1820, "31/64\" (0.4844\")", "13/16\" Hex"),
            new ImperialBolt("5/8\"-11 UNC", 0.6250, 11, 18, 0.2260, "17/32\" (0.5312\")", "15/16\" Hex"),
            new ImperialBolt("3/4\"-10 UNC", 0.7500, 10, 16, 0.3340, "21/32\" (0.6562\")", "1-1/8\" Hex"),
            new ImperialBolt("7/8\"-9 UNC", 0.8750, 9, 14, 0.4620, "49/64\" (0.7656\")", "1-5/16\" Hex"),
            new ImperialBolt("1\"-8 UNC", 1.0000, 8, 12, 0.6060, "7/8\" (0.8750\")", "1-1/2\" Hex")
    };

    private static final BoltGrade[] METRIC_GRADES = new BoltGrade[]{
            new BoltGrade("Class 4.6 (Low Carbon)", 225.0, "225 MPa proof strength"),
            new BoltGrade("Class 8.8 (Standard High Tensile)", 600.0, "600 MPa proof strength"),
            new BoltGrade("Class 10.9 (Automotive / Chassis)", 830.0, "830 MPa proof strength"),
            new BoltGrade("Class 12.9 (Cylinder Head / Extreme)", 970.0, "970 MPa proof strength"),
            new BoltGrade("Stainless A2-70 (Corrosion Resist)", 450.0, "450 MPa proof strength")
    };

    private static final BoltGrade[] IMPERIAL_GRADES = new BoltGrade[]{
            new BoltGrade("Grade 2 (Commercial / Low Carbon)", 55000.0, "55,000 PSI proof"),
            new BoltGrade("Grade 5 (Automotive / 3-Radial)", 85000.0, "85,000 PSI proof"),
            new BoltGrade("Grade 8 (Severe Heavy Duty / 6-Radial)", 120000.0, "120,000 PSI proof"),
            new BoltGrade("Stainless 304/316 (Marine)", 65000.0, "65,000 PSI proof")
    };

    private static final LubeOption[] LUBE_OPTIONS = new LubeOption[]{
            new LubeOption("Light Engine / Machine Oil (K = 0.15)", 0.15),
            new LubeOption("Dry / Clean Bare Steel (K = 0.20)", 0.20),
            new LubeOption("Anti-Seize / Copper Paste (K = 0.12)", 0.12),
            new LubeOption("Moly Paste / Assembly Lube (K = 0.11)", 0.11),
            new LubeOption("Threadlocker / Loctite (K = 0.16)", 0.16),
            new LubeOption("Zinc-Plated Dry (K = 0.22)", 0.22),
            new LubeOption("Cadmium Plated (K = 0.14)", 0.14),
            new LubeOption("Custom K-Factor", 0.15)
    };

    // UI Elements
    private TextView tvAppTitle;
    private TextView tvAppSubtitle;
    private Button btnThemeToggle;
    private ImageButton btnSettings;

    // Navigation Tabs
    private Button chipTabTorque;
    private Button chipTabPitch;
    private Button chipTabLadder;
    private Button chipTabLedger;

    // Sections
    private LinearLayout layoutSectionTorque;
    private LinearLayout layoutSectionPitch;
    private LinearLayout layoutSectionLadder;
    private LinearLayout layoutSectionLedger;

    // Section 1: Torque
    private Button chipStdMetric;
    private Button chipStdImperial;
    private TextView tvSizeLabel;
    private Spinner spnBoltSize;
    private TextView tvGradeLabel;
    private Spinner spnBoltGrade;
    private Spinner spnLubricant;
    private Button btnKMinus;
    private EditText etKFactor;
    private Button btnKPlus;
    private Button chipPreload65;
    private Button chipPreload75;
    private Button chipPreload90;

    // Telemetry Hero
    private TextView tvStatusBadge;
    private TextView tvTorquePrimary;
    private TextView tvTorqueSecondary;
    private TextView tvClampForce;
    private TextView tvClampForceSub;
    private TextView tvStressArea;
    private TextView tvProofStress;
    private TextView tvTapDrill;
    private TextView tvTapDrillSub;
    private TextView tvSocketHex;
    private TextView tvSocketHexSub;
    private Button btnSaveSpec;
    private Button btnSendToLadder;

    // Section 2: Pitch Reference
    private TextView tvPitchCardTitle;
    private TextView tvPitchCardSubtitle;
    private TextView tvCoarsePitch;
    private TextView tvFinePitch;
    private TextView tvEngage75;
    private TextView tvEngage50;
    private TextView tvClearanceClose;
    private TextView tvClearanceFree;

    // Section 3: Ladder
    private TextView tvLadderPass1;
    private TextView tvLadderPass2;
    private TextView tvLadderPass3;
    private Button btnCheckPass1;
    private Button btnCheckPass2;
    private Button btnCheckPass3;
    private Button btnResetLadder;

    // Section 4: Ledger
    private Button btnClearLedger;
    private TextView tvEmptyLedger;
    private LinearLayout layoutLedgerItems;

    // State Variables
    private int themeMode = 0; // 0=Auto, 1=Light, 2=Dark
    private boolean isMetric = true;
    private double currentKFactor = 0.15;
    private double currentPreloadPct = 75.0;
    private int selectedBoltIndex = 5; // Default M10 or 3/8"
    private int selectedGradeIndex = 1; // Default 8.8 or Grade 5
    private int selectedLubeIndex = 0; // Default Light Engine Oil
    private int activeTab = 0;

    private double calculatedTorqueNm = 0.0;
    private double calculatedTorqueFtLb = 0.0;
    private double calculatedClampKn = 0.0;

    private boolean pass1Done = false;
    private boolean pass2Done = false;
    private boolean pass3Done = false;

    private DatabaseHelper dbHelper;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME_MODE, 0);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadPreferences();
        applyCurrentTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);

        initViews();
        restoreDraftState(savedInstanceState);
        setupNavigationTabs();
        setupSpinners();
        setupControls();
        switchSection(activeTab);
        calculateAll();
        refreshLedger();
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

    private void initViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle);
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        chipTabTorque = findViewById(R.id.chipTabTorque);
        chipTabPitch = findViewById(R.id.chipTabPitch);
        chipTabLadder = findViewById(R.id.chipTabLadder);
        chipTabLedger = findViewById(R.id.chipTabLedger);

        layoutSectionTorque = findViewById(R.id.layoutSectionTorque);
        layoutSectionPitch = findViewById(R.id.layoutSectionPitch);
        layoutSectionLadder = findViewById(R.id.layoutSectionLadder);
        layoutSectionLedger = findViewById(R.id.layoutSectionLedger);

        chipStdMetric = findViewById(R.id.chipStdMetric);
        chipStdImperial = findViewById(R.id.chipStdImperial);
        tvSizeLabel = findViewById(R.id.tvSizeLabel);
        spnBoltSize = findViewById(R.id.spnBoltSize);
        tvGradeLabel = findViewById(R.id.tvGradeLabel);
        spnBoltGrade = findViewById(R.id.spnBoltGrade);
        spnLubricant = findViewById(R.id.spnLubricant);
        btnKMinus = findViewById(R.id.btnKMinus);
        etKFactor = findViewById(R.id.etKFactor);
        btnKPlus = findViewById(R.id.btnKPlus);
        chipPreload65 = findViewById(R.id.chipPreload65);
        chipPreload75 = findViewById(R.id.chipPreload75);
        chipPreload90 = findViewById(R.id.chipPreload90);

        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvTorquePrimary = findViewById(R.id.tvTorquePrimary);
        tvTorqueSecondary = findViewById(R.id.tvTorqueSecondary);
        tvClampForce = findViewById(R.id.tvClampForce);
        tvClampForceSub = findViewById(R.id.tvClampForceSub);
        tvStressArea = findViewById(R.id.tvStressArea);
        tvProofStress = findViewById(R.id.tvProofStress);
        tvTapDrill = findViewById(R.id.tvTapDrill);
        tvTapDrillSub = findViewById(R.id.tvTapDrillSub);
        tvSocketHex = findViewById(R.id.tvSocketHex);
        tvSocketHexSub = findViewById(R.id.tvSocketHexSub);
        btnSaveSpec = findViewById(R.id.btnSaveSpec);
        btnSendToLadder = findViewById(R.id.btnSendToLadder);

        tvPitchCardTitle = findViewById(R.id.tvPitchCardTitle);
        tvPitchCardSubtitle = findViewById(R.id.tvPitchCardSubtitle);
        tvCoarsePitch = findViewById(R.id.tvCoarsePitch);
        tvFinePitch = findViewById(R.id.tvFinePitch);
        tvEngage75 = findViewById(R.id.tvEngage75);
        tvEngage50 = findViewById(R.id.tvEngage50);
        tvClearanceClose = findViewById(R.id.tvClearanceClose);
        tvClearanceFree = findViewById(R.id.tvClearanceFree);

        tvLadderPass1 = findViewById(R.id.tvLadderPass1);
        tvLadderPass2 = findViewById(R.id.tvLadderPass2);
        tvLadderPass3 = findViewById(R.id.tvLadderPass3);
        btnCheckPass1 = findViewById(R.id.btnCheckPass1);
        btnCheckPass2 = findViewById(R.id.btnCheckPass2);
        btnCheckPass3 = findViewById(R.id.btnCheckPass3);
        btnResetLadder = findViewById(R.id.btnResetLadder);

        btnClearLedger = findViewById(R.id.btnClearLedger);
        tvEmptyLedger = findViewById(R.id.tvEmptyLedger);
        layoutLedgerItems = findViewById(R.id.layoutLedgerItems);
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        isMetric = prefs.getBoolean(KEY_IS_METRIC, true);
        currentPreloadPct = prefs.getFloat(KEY_PRELOAD_PCT, 75.0f);
        currentKFactor = prefs.getFloat(KEY_K_FACTOR, 0.15f);
        selectedBoltIndex = prefs.getInt(KEY_BOLT_IDX, 5);
        selectedGradeIndex = prefs.getInt(KEY_GRADE_IDX, 1);
        selectedLubeIndex = prefs.getInt(KEY_LUBE_IDX, 0);
        activeTab = prefs.getInt(KEY_ACTIVE_TAB, 0);
    }

    private void restoreDraftState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            isMetric = savedInstanceState.getBoolean(KEY_IS_METRIC, isMetric);
            currentPreloadPct = savedInstanceState.getDouble(KEY_PRELOAD_PCT, currentPreloadPct);
            currentKFactor = savedInstanceState.getDouble(KEY_K_FACTOR, currentKFactor);
            selectedBoltIndex = savedInstanceState.getInt(KEY_BOLT_IDX, selectedBoltIndex);
            selectedGradeIndex = savedInstanceState.getInt(KEY_GRADE_IDX, selectedGradeIndex);
            selectedLubeIndex = savedInstanceState.getInt(KEY_LUBE_IDX, selectedLubeIndex);
            activeTab = savedInstanceState.getInt(KEY_ACTIVE_TAB, activeTab);
        }

        if (btnThemeToggle != null) {
            if (themeMode == 1) {
                btnThemeToggle.setText("Theme: Light");
            } else if (themeMode == 2) {
                btnThemeToggle.setText("Theme: Dark");
            } else {
                btnThemeToggle.setText("Theme: Auto");
            }
        }

        if (etKFactor != null) {
            SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String draftK = sp.getString("draft_k_factor", null);
            if (draftK != null && !draftK.isEmpty()) {
                etKFactor.setText(draftK);
            } else {
                etKFactor.setText(String.format(Locale.US, "%.2f", currentKFactor));
            }
        }

        updateTabStyle(chipStdMetric, isMetric);
        updateTabStyle(chipStdImperial, !isMetric);
        updateTabStyle(chipPreload65, currentPreloadPct == 65.0);
        updateTabStyle(chipPreload75, currentPreloadPct == 75.0);
        updateTabStyle(chipPreload90, currentPreloadPct == 90.0);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_METRIC, isMetric);
        outState.putDouble(KEY_PRELOAD_PCT, currentPreloadPct);
        outState.putDouble(KEY_K_FACTOR, currentKFactor);
        outState.putInt(KEY_BOLT_IDX, selectedBoltIndex);
        outState.putInt(KEY_GRADE_IDX, selectedGradeIndex);
        outState.putInt(KEY_LUBE_IDX, selectedLubeIndex);
        outState.putInt(KEY_ACTIVE_TAB, activeTab);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveState();
    }

    private void setupNavigationTabs() {
        chipTabTorque.setOnClickListener(v -> switchSection(0));
        chipTabPitch.setOnClickListener(v -> switchSection(1));
        chipTabLadder.setOnClickListener(v -> switchSection(2));
        chipTabLedger.setOnClickListener(v -> switchSection(3));

        btnThemeToggle.setOnClickListener(v -> cycleThemeMode());
        btnSettings.setOnClickListener(v -> showSettingsModal());
    }

    private void switchSection(int index) {
        hapticTick();
        activeTab = index;
        layoutSectionTorque.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        layoutSectionPitch.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        layoutSectionLadder.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        layoutSectionLedger.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        updateTabStyle(chipTabTorque, index == 0);
        updateTabStyle(chipTabPitch, index == 1);
        updateTabStyle(chipTabLadder, index == 2);
        updateTabStyle(chipTabLedger, index == 3);

        if (index == 3) {
            refreshLedger();
        }
        saveState();
    }

    private void updateTabStyle(Button btn, boolean active) {
        if (btn == null) return;
        if (active) {
            btn.setBackgroundResource(R.drawable.chip_active);
            btn.setTextColor(getColor(R.color.m3_on_primary));
        } else {
            btn.setBackgroundResource(R.drawable.chip_inactive);
            btn.setTextColor(getColor(R.color.m3_on_surface));
        }
    }

    private void setupSpinners() {
        updateBoltSizeAdapter();
        spnBoltSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBoltIndex = position;
                calculateAll();
                saveState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateBoltGradeAdapter();
        spnBoltGrade.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedGradeIndex = position;
                calculateAll();
                saveState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        String[] lubeNames = new String[LUBE_OPTIONS.length];
        for (int i = 0; i < LUBE_OPTIONS.length; i++) {
            lubeNames[i] = LUBE_OPTIONS[i].name;
        }
        ArrayAdapter<String> lubeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, lubeNames);
        spnLubricant.setAdapter(lubeAdapter);
        if (selectedLubeIndex < LUBE_OPTIONS.length) {
            spnLubricant.setSelection(selectedLubeIndex);
        }
        spnLubricant.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLubeIndex = position;
                if (position < LUBE_OPTIONS.length - 1) {
                    currentKFactor = LUBE_OPTIONS[position].kFactor;
                    etKFactor.setText(String.format(Locale.US, "%.2f", currentKFactor));
                }
                calculateAll();
                saveState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateBoltSizeAdapter() {
        if (isMetric) {
            String[] names = new String[METRIC_BOLTS.length];
            for (int i = 0; i < METRIC_BOLTS.length; i++) {
                names[i] = METRIC_BOLTS[i].name;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            spnBoltSize.setAdapter(adapter);
            if (selectedBoltIndex >= names.length) selectedBoltIndex = 5;
            spnBoltSize.setSelection(selectedBoltIndex);
        } else {
            String[] names = new String[IMPERIAL_BOLTS.length];
            for (int i = 0; i < IMPERIAL_BOLTS.length; i++) {
                names[i] = IMPERIAL_BOLTS[i].name;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            spnBoltSize.setAdapter(adapter);
            if (selectedBoltIndex >= names.length) selectedBoltIndex = 5;
            spnBoltSize.setSelection(selectedBoltIndex);
        }
    }

    private void updateBoltGradeAdapter() {
        if (isMetric) {
            String[] names = new String[METRIC_GRADES.length];
            for (int i = 0; i < METRIC_GRADES.length; i++) {
                names[i] = METRIC_GRADES[i].name;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            spnBoltGrade.setAdapter(adapter);
            if (selectedGradeIndex >= names.length) selectedGradeIndex = 1;
            spnBoltGrade.setSelection(selectedGradeIndex);
        } else {
            String[] names = new String[IMPERIAL_GRADES.length];
            for (int i = 0; i < IMPERIAL_GRADES.length; i++) {
                names[i] = IMPERIAL_GRADES[i].name;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            spnBoltGrade.setAdapter(adapter);
            if (selectedGradeIndex >= names.length) selectedGradeIndex = 1;
            spnBoltGrade.setSelection(selectedGradeIndex);
        }
    }

    private void setupControls() {
        chipStdMetric.setOnClickListener(v -> {
            if (!isMetric) {
                hapticTick();
                isMetric = true;
                updateTabStyle(chipStdMetric, true);
                updateTabStyle(chipStdImperial, false);
                tvSizeLabel.setText("Fastener Size & Pitch (ISO Metric)");
                tvGradeLabel.setText("Metric Strength Class (ISO 898-1)");
                selectedBoltIndex = 5; // M10
                selectedGradeIndex = 1; // 8.8
                updateBoltSizeAdapter();
                updateBoltGradeAdapter();
                calculateAll();
                saveState();
            }
        });

        chipStdImperial.setOnClickListener(v -> {
            if (isMetric) {
                hapticTick();
                isMetric = false;
                updateTabStyle(chipStdMetric, false);
                updateTabStyle(chipStdImperial, true);
                tvSizeLabel.setText("Fastener Size & TPI (Imperial UNC)");
                tvGradeLabel.setText("SAE Strength Grade (SAE J429)");
                selectedBoltIndex = 5; // 3/8"
                selectedGradeIndex = 1; // Grade 5
                updateBoltSizeAdapter();
                updateBoltGradeAdapter();
                calculateAll();
                saveState();
            }
        });

        btnKMinus.setOnClickListener(v -> {
            hapticTick();
            currentKFactor = Math.max(0.08, currentKFactor - 0.01);
            etKFactor.setText(String.format(Locale.US, "%.2f", currentKFactor));
            spnLubricant.setSelection(LUBE_OPTIONS.length - 1);
            calculateAll();
            saveState();
        });

        btnKPlus.setOnClickListener(v -> {
            hapticTick();
            currentKFactor = Math.min(0.35, currentKFactor + 0.01);
            etKFactor.setText(String.format(Locale.US, "%.2f", currentKFactor));
            spnLubricant.setSelection(LUBE_OPTIONS.length - 1);
            calculateAll();
            saveState();
        });

        etKFactor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String raw = s.toString();
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString("draft_k_factor", raw)
                        .apply();
                try {
                    double val = Double.parseDouble(raw.trim());
                    if (val >= 0.05 && val <= 0.50) {
                        currentKFactor = val;
                        calculateAll();
                        saveState();
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipPreload65.setOnClickListener(v -> setPreloadTarget(65.0));
        chipPreload75.setOnClickListener(v -> setPreloadTarget(75.0));
        chipPreload90.setOnClickListener(v -> setPreloadTarget(90.0));

        btnSaveSpec.setOnClickListener(v -> saveCurrentSpecToDb());

        btnSendToLadder.setOnClickListener(v -> {
            hapticTick();
            switchSection(2);
        });

        btnCheckPass1.setOnClickListener(v -> {
            hapticTick();
            pass1Done = !pass1Done;
            btnCheckPass1.setText(pass1Done ? "Done ✓" : "Complete");
            btnCheckPass1.setBackgroundResource(pass1Done ? R.drawable.btn_m3_primary : R.drawable.btn_m3_outlined);
            btnCheckPass1.setTextColor(getColor(pass1Done ? R.color.m3_on_primary : R.color.m3_on_surface));
        });

        btnCheckPass2.setOnClickListener(v -> {
            hapticTick();
            pass2Done = !pass2Done;
            btnCheckPass2.setText(pass2Done ? "Done ✓" : "Complete");
            btnCheckPass2.setBackgroundResource(pass2Done ? R.drawable.btn_m3_primary : R.drawable.btn_m3_outlined);
            btnCheckPass2.setTextColor(getColor(pass2Done ? R.color.m3_on_primary : R.color.m3_on_surface));
        });

        btnCheckPass3.setOnClickListener(v -> {
            hapticTick();
            pass3Done = !pass3Done;
            btnCheckPass3.setText(pass3Done ? "Done ✓" : "Complete");
            btnCheckPass3.setBackgroundResource(pass3Done ? R.drawable.btn_m3_primary : R.drawable.btn_m3_outlined);
            btnCheckPass3.setTextColor(getColor(pass3Done ? R.color.m3_on_primary : R.color.m3_on_surface));
            if (pass3Done) {
                Toast.makeText(this, "Final Torque Pass Completed!", Toast.LENGTH_SHORT).show();
            }
        });

        btnResetLadder.setOnClickListener(v -> resetLadder());

        btnClearLedger.setOnClickListener(v -> {
            hapticTick();
            new AlertDialog.Builder(this)
                    .setTitle("Clear Ledger")
                    .setMessage("Delete all saved fastener specifications?")
                    .setPositiveButton("Clear All", (dialog, which) -> {
                        dbHelper.clearAll();
                        refreshLedger();
                        Toast.makeText(this, "Ledger cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setPreloadTarget(double pct) {
        hapticTick();
        currentPreloadPct = pct;
        updateTabStyle(chipPreload65, pct == 65.0);
        updateTabStyle(chipPreload75, pct == 75.0);
        updateTabStyle(chipPreload90, pct == 90.0);
        calculateAll();
        saveState();
    }

    private void calculateAll() {
        if (isMetric) {
            calculateMetric();
        } else {
            calculateImperial();
        }
        updatePitchReference();
        updateLadderTelemetry();
    }

    private void calculateMetric() {
        if (selectedBoltIndex >= METRIC_BOLTS.length) selectedBoltIndex = 0;
        if (selectedGradeIndex >= METRIC_GRADES.length) selectedGradeIndex = 0;

        MetricBolt bolt = METRIC_BOLTS[selectedBoltIndex];
        BoltGrade grade = METRIC_GRADES[selectedGradeIndex];

        double dMeters = bolt.diaMm / 1000.0;
        double atMm2 = bolt.stressAreaMm2;
        double spMpa = grade.proofStrength; // MPa = N/mm²

        double clampForceN = (currentPreloadPct / 100.0) * atMm2 * spMpa;
        calculatedClampKn = clampForceN / 1000.0;

        calculatedTorqueNm = currentKFactor * dMeters * clampForceN;
        calculatedTorqueFtLb = calculatedTorqueNm * 0.737562;
        double torqueInLb = calculatedTorqueFtLb * 12.0;

        tvTorquePrimary.setText(String.format(Locale.US, "%.1f N·m", calculatedTorqueNm));
        tvTorqueSecondary.setText(String.format(Locale.US, "%.1f ft-lb • %.0f in-lb", calculatedTorqueFtLb, torqueInLb));

        tvClampForce.setText(String.format(Locale.US, "%.1f kN", calculatedClampKn));
        double lbf = calculatedClampKn * 224.809;
        tvClampForceSub.setText(String.format(Locale.US, "%,.0f lbf clamp", lbf));

        tvStressArea.setText(String.format(Locale.US, "%.1f mm²", atMm2));
        tvProofStress.setText(String.format(Locale.US, "Proof: %.0f MPa (%s)", spMpa, grade.name.split(" ")[1]));

        tvTapDrill.setText(String.format(Locale.US, "%.1f mm", bolt.tapDrillMm));
        double drillIn = bolt.tapDrillMm / 25.4;
        tvTapDrillSub.setText(String.format(Locale.US, "%.3f in (75%% tap)", drillIn));

        tvSocketHex.setText(bolt.socketHex);
        tvSocketHexSub.setText("Standard ISO Hex Socket");

        updateStatusBadge(currentPreloadPct);
    }

    private void calculateImperial() {
        if (selectedBoltIndex >= IMPERIAL_BOLTS.length) selectedBoltIndex = 0;
        if (selectedGradeIndex >= IMPERIAL_GRADES.length) selectedGradeIndex = 0;

        ImperialBolt bolt = IMPERIAL_BOLTS[selectedBoltIndex];
        BoltGrade grade = IMPERIAL_GRADES[selectedGradeIndex];

        double dIn = bolt.diaIn;
        double atIn2 = bolt.stressAreaIn2;
        double spPsi = grade.proofStrength; // PSI

        double clampForceLbf = (currentPreloadPct / 100.0) * atIn2 * spPsi;
        calculatedClampKn = clampForceLbf * 0.00444822;

        double torqueInLb = currentKFactor * dIn * clampForceLbf;
        calculatedTorqueFtLb = torqueInLb / 12.0;
        calculatedTorqueNm = calculatedTorqueFtLb / 0.737562;

        tvTorquePrimary.setText(String.format(Locale.US, "%.1f ft-lb", calculatedTorqueFtLb));
        tvTorqueSecondary.setText(String.format(Locale.US, "%.1f N·m • %.0f in-lb", calculatedTorqueNm, torqueInLb));

        tvClampForce.setText(String.format(Locale.US, "%,.0f lbf", clampForceLbf));
        tvClampForceSub.setText(String.format(Locale.US, "%.1f kN clamp", calculatedClampKn));

        tvStressArea.setText(String.format(Locale.US, "%.4f in²", atIn2));
        tvProofStress.setText(String.format(Locale.US, "Proof: %,.0f PSI (%s)", spPsi, grade.name.split(" ")[0]));

        tvTapDrill.setText(bolt.tapDrill);
        tvTapDrillSub.setText("Standard UNC 75% thread");

        tvSocketHex.setText(bolt.socketHex);
        tvSocketHexSub.setText("Standard SAE Hex Wrench");

        updateStatusBadge(currentPreloadPct);
    }

    private void updateStatusBadge(double pct) {
        if (pct >= 85.0) {
            tvStatusBadge.setText("High Preload (90%)");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            tvStatusBadge.setTextColor(getColor(R.color.badge_warning));
        } else if (pct <= 65.0) {
            tvStatusBadge.setText("Light Preload (65%)");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            tvStatusBadge.setTextColor(getColor(R.color.badge_warning));
        } else {
            tvStatusBadge.setText("Optimal Preload (75%)");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_safe);
            tvStatusBadge.setTextColor(getColor(R.color.badge_safe));
        }
    }

    private void updatePitchReference() {
        if (isMetric) {
            MetricBolt bolt = METRIC_BOLTS[selectedBoltIndex];
            tvPitchCardTitle.setText(bolt.name);
            tvPitchCardSubtitle.setText("ISO 261 Standard Metric External/Internal Thread");

            tvCoarsePitch.setText(String.format(Locale.US, "%.2f mm", bolt.coarsePitch));
            tvFinePitch.setText(String.format(Locale.US, "%.2f mm fine", bolt.finePitch));

            tvEngage75.setText(String.format(Locale.US, "%.2f mm (Steel)", bolt.tapDrillMm));
            double drill50 = bolt.diaMm - (bolt.coarsePitch * 0.7);
            tvEngage50.setText(String.format(Locale.US, "%.2f mm (Alum)", drill50));

            tvClearanceClose.setText(String.format(Locale.US, "%.1f mm", bolt.diaMm + 0.5));
            tvClearanceFree.setText(String.format(Locale.US, "%.1f mm", bolt.diaMm + 1.0));
        } else {
            ImperialBolt bolt = IMPERIAL_BOLTS[selectedBoltIndex];
            tvPitchCardTitle.setText(bolt.name);
            tvPitchCardSubtitle.setText("ASME B1.1 Standard Unified Coarse/Fine Thread");

            tvCoarsePitch.setText(String.format(Locale.US, "%d TPI Coarse", bolt.coarseTpi));
            tvFinePitch.setText(String.format(Locale.US, "%d TPI Fine", bolt.fineTpi));

            tvEngage75.setText(bolt.tapDrill);
            tvEngage50.setText(String.format(Locale.US, "%.3f in (Alum)", bolt.diaIn - (0.54 / bolt.coarseTpi)));

            tvClearanceClose.setText(String.format(Locale.US, "%.3f in (Close)", bolt.diaIn + 0.015));
            tvClearanceFree.setText(String.format(Locale.US, "%.3f in (Free)", bolt.diaIn + 0.031));
        }
    }

    private void updateLadderTelemetry() {
        double pass1Nm = calculatedTorqueNm * 0.30;
        double pass1Ft = calculatedTorqueFtLb * 0.30;
        double pass2Nm = calculatedTorqueNm * 0.70;
        double pass2Ft = calculatedTorqueFtLb * 0.70;

        if (isMetric) {
            tvLadderPass1.setText(String.format(Locale.US, "%.1f N·m (%.1f ft-lb)", pass1Nm, pass1Ft));
            tvLadderPass2.setText(String.format(Locale.US, "%.1f N·m (%.1f ft-lb)", pass2Nm, pass2Ft));
            tvLadderPass3.setText(String.format(Locale.US, "%.1f N·m (%.1f ft-lb)", calculatedTorqueNm, calculatedTorqueFtLb));
        } else {
            tvLadderPass1.setText(String.format(Locale.US, "%.1f ft-lb (%.1f N·m)", pass1Ft, pass1Nm));
            tvLadderPass2.setText(String.format(Locale.US, "%.1f ft-lb (%.1f N·m)", pass2Ft, pass2Nm));
            tvLadderPass3.setText(String.format(Locale.US, "%.1f ft-lb (%.1f N·m)", calculatedTorqueFtLb, calculatedTorqueNm));
        }
    }

    private void resetLadder() {
        hapticTick();
        pass1Done = false;
        pass2Done = false;
        pass3Done = false;

        btnCheckPass1.setText("Complete");
        btnCheckPass1.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnCheckPass1.setTextColor(getColor(R.color.m3_on_surface));

        btnCheckPass2.setText("Complete");
        btnCheckPass2.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnCheckPass2.setTextColor(getColor(R.color.m3_on_surface));

        btnCheckPass3.setText("Complete");
        btnCheckPass3.setBackgroundResource(R.drawable.btn_m3_outlined);
        btnCheckPass3.setTextColor(getColor(R.color.m3_on_surface));

        Toast.makeText(this, "Ladder progress reset", Toast.LENGTH_SHORT).show();
    }

    private void saveCurrentSpecToDb() {
        hapticTick();
        String boltName = isMetric ? METRIC_BOLTS[selectedBoltIndex].name : IMPERIAL_BOLTS[selectedBoltIndex].name;
        String stdName = isMetric ? "ISO Metric" : "Imperial SAE";
        String gradeName = isMetric ? METRIC_GRADES[selectedGradeIndex].name.split(" ")[1] : IMPERIAL_GRADES[selectedGradeIndex].name.split(" ")[0];
        String lubeName = LUBE_OPTIONS[selectedLubeIndex].name.split("\\(")[0].trim();
        String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());

        long rowId = dbHelper.insertSpec(boltName, stdName, gradeName, lubeName,
                calculatedTorqueNm, calculatedTorqueFtLb, calculatedClampKn, timeStr);

        if (rowId > 0) {
            Toast.makeText(this, "Saved " + boltName + " spec to Ledger!", Toast.LENGTH_SHORT).show();
            refreshLedger();
        } else {
            Toast.makeText(this, "Failed to save spec", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshLedger() {
        layoutLedgerItems.removeAllViews();
        List<DatabaseHelper.SavedSpec> specs = dbHelper.getAllSpecs();

        if (specs.isEmpty()) {
            tvEmptyLedger.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyLedger.setVisibility(View.GONE);

        for (DatabaseHelper.SavedSpec s : specs) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_m3);
            card.setPadding(32, 28, 32, 28);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            card.setLayoutParams(lp);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);

            TextView tvBolt = new TextView(this);
            tvBolt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            tvBolt.setText(s.boltSize + " • " + s.grade);
            tvBolt.setTextSize(16);
            tvBolt.setTextColor(getColor(R.color.m3_on_surface));
            tvBolt.setTypeface(null, android.graphics.Typeface.BOLD);
            topRow.addView(tvBolt);

            ImageButton btnDelete = new ImageButton(this);
            btnDelete.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
            btnDelete.setImageResource(android.R.drawable.ic_menu_delete);
            btnDelete.setBackgroundResource(R.drawable.bg_topbar_button);
            btnDelete.setContentDescription("Delete item");
            btnDelete.setOnClickListener(v -> {
                hapticTick();
                dbHelper.deleteSpec(s.id);
                refreshLedger();
            });
            topRow.addView(btnDelete);
            card.addView(topRow);

            TextView tvTorque = new TextView(this);
            tvTorque.setText(String.format(Locale.US, "%.1f N·m  |  %.1f ft-lb", s.torqueNm, s.torqueFtLb));
            tvTorque.setTextSize(18);
            tvTorque.setTextColor(getColor(R.color.m3_primary));
            tvTorque.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTorque.setPadding(0, 8, 0, 4);
            card.addView(tvTorque);

            TextView tvMeta = new TextView(this);
            tvMeta.setText(String.format(Locale.US, "%s • Preload: %.1f kN • %s", s.lubricant, s.clampForceKn, s.timestamp));
            tvMeta.setTextSize(11);
            tvMeta.setTextColor(getColor(R.color.m3_on_surface_variant));
            card.addView(tvMeta);

            layoutLedgerItems.addView(card);
        }
    }

    private void showSettingsModal() {
        hapticTick();
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnDialogTheme = view.findViewById(R.id.btnDialogTheme);
        Button btnResetDefaults = view.findViewById(R.id.btnResetDefaults);
        Button btnExploreTools = view.findViewById(R.id.btnExploreTools);
        Button btnCloseSettings = view.findViewById(R.id.btnCloseSettings);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME_MODE, 0);
        String[] modes = new String[]{"Theme: System Auto", "Theme: Light", "Theme: Dark"};
        btnDialogTheme.setText(modes[mode % 3]);

        btnDialogTheme.setOnClickListener(v -> {
            hapticTick();
            dialog.dismiss();
            cycleThemeMode();
        });

        btnResetDefaults.setOnClickListener(v -> {
            hapticTick();
            isMetric = true;
            currentKFactor = 0.15;
            currentPreloadPct = 75.0;
            selectedBoltIndex = 5;
            selectedGradeIndex = 1;
            selectedLubeIndex = 0;

            updateTabStyle(chipStdMetric, true);
            updateTabStyle(chipStdImperial, false);
            updateTabStyle(chipPreload65, false);
            updateTabStyle(chipPreload75, true);
            updateTabStyle(chipPreload90, false);

            updateBoltSizeAdapter();
            updateBoltGradeAdapter();
            spnLubricant.setSelection(0);
            etKFactor.setText("0.15");

            calculateAll();
            saveState();
            dialog.dismiss();
            Toast.makeText(this, "Standard workshop defaults restored", Toast.LENGTH_SHORT).show();
        });

        btnExploreTools.setOnClickListener(v -> {
            hapticTick();
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com/"));
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show();
            }
        });

        btnCloseSettings.setOnClickListener(v -> {
            hapticTick();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void cycleThemeMode() {
        hapticTick();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int current = prefs.getInt(KEY_THEME_MODE, 0);
        int next;
        if (current == 0) {
            next = 2; // Dark first so light-mode default device immediately jumps to dark
        } else if (current == 2) {
            next = 1; // Light
        } else {
            next = 0; // Auto
        }
        prefs.edit().putInt(KEY_THEME_MODE, next).apply();
        themeMode = next;
        applyCurrentTheme();
        recreate();
    }

    private void saveState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_THEME_MODE, themeMode)
                .putBoolean(KEY_IS_METRIC, isMetric)
                .putFloat(KEY_PRELOAD_PCT, (float) currentPreloadPct)
                .putFloat(KEY_K_FACTOR, (float) currentKFactor)
                .putInt(KEY_BOLT_IDX, selectedBoltIndex)
                .putInt(KEY_GRADE_IDX, selectedGradeIndex)
                .putInt(KEY_LUBE_IDX, selectedLubeIndex)
                .putInt(KEY_ACTIVE_TAB, activeTab)
                .apply();
    }

    private void hapticTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                View root = getWindow().getDecorView();
                root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            } else {
                Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vib != null && vib.hasVibrator()) {
                    vib.vibrate(20);
                }
            }
        } catch (Exception ignored) {}
    }
}
