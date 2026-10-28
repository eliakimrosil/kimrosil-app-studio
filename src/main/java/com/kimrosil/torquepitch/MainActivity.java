package com.kimrosil.torquepitch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "TorquePitchPrefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_ACTIVE_TAB = "active_tab";
    private static final String KEY_STANDARD = "fastener_standard";
    private static final String KEY_UNIT_MODE = "unit_mode";
    private static final String KEY_YIELD_PERCENT = "yield_percent";

    // Header Views
    private TextView tvAppTitle;
    private TextView tvAppSubtitle;
    private Button btnThemeToggle;
    private ImageButton btnSettings;

    // Navigation Tabs
    private Button chipTabTorque;
    private Button chipTabTap;
    private Button chipTabSequence;

    // Sections
    private ScrollView mainScrollView;
    private LinearLayout layoutSectionTorque;
    private LinearLayout layoutSectionTap;
    private LinearLayout layoutSectionSequence;

    // Section 1: Torque Views
    private Button chipStandardMetric;
    private Button chipStandardImperial;
    private Spinner spnFastenerSize;
    private Button chipPitchCoarse;
    private Button chipPitchFine;
    private Spinner spnFastenerGrade;
    private Spinner spnLubricant;
    private Button btnYieldMinus;
    private EditText etYieldPercent;
    private Button btnYieldPlus;
    private Button btnCalculateTorque;

    // Hero Torque HUD
    private TextView tvTorqueStatusBadge;
    private TextView tvFastenerTag;
    private TextView tvPrimaryTorque;
    private TextView tvSecondaryTorque;
    private Button chipUnitFtLb;
    private Button chipUnitNm;
    private Button chipUnitInLb;
    private TextView tvClampForce;
    private TextView tvClampForceLbs;
    private TextView tvStressArea;
    private TextView tvStressAreaSqIn;
    private TextView tvLubeImpactNote;

    // Section 2: Tap & Pitch Views
    private TextView tvTapFastenerTitle;
    private TextView tvTapFastenerSubtitle;
    private TextView tvTapDrillSteel;
    private TextView tvTapDrillSteelAlt;
    private TextView tvTapDepthNote;
    private TextView tvTapDrillAlum;
    private TextView tvTapDrillAlumAlt;
    private TextView tvClearanceNormal;
    private TextView tvClearanceNormalAlt;
    private TextView tvClearanceClose;
    private TextView tvClearanceCloseAlt;
    private TextView tvSpecMajorDia;
    private TextView tvSpecPitch;
    private TextView tvSpecPitchDia;
    private TextView tvSpecMinorDia;

    // Section 3: TTY & Sequence Views
    private TextView tvStage1Snug;
    private Button btnSnugMinus;
    private Button btnSnugPlus;
    private TextView tvStage2Angle;
    private Button btnAngle2Minus;
    private Button btnAngle2Plus;
    private TextView tvStage3Angle;
    private Button btnAngle3Minus;
    private Button btnAngle3Plus;
    private TextView tvSequenceProgress;
    private Button chipPatternSpiral;
    private Button chipPatternStar;
    private TextView tvSequenceGuide;
    private final Button[] boltButtons = new Button[10];
    private final boolean[] boltCompleted = new boolean[10];
    private Button btnResetSequence;

    // State Variables
    private int themeMode = 0; // 0: Auto, 1: Light, 2: Dark
    private int activeTab = 0;  // 0: Torque, 1: Tap & Pitch, 2: Sequence
    private boolean isMetric = true; // true = Metric, false = Imperial
    private boolean isCoarse = true; // true = Coarse, false = Fine
    private int unitMode = 0; // 0 = ft-lb, 1 = Nm, 2 = in-lb
    private int yieldPercent = 75; // default 75%
    private int stage1SnugFtLb = 30;
    private int stage2Degrees = 90;
    private int stage3Degrees = 90;
    private boolean isSpiralPattern = true;

    private Vibrator vibrator;

    // =========================================================================
    // Domain Models: Fastener Tables
    // =========================================================================
    public static class MetricFastener {
        final String name;
        final double diaMm;
        final double pitchCoarseMm;
        final double pitchFineMm;
        final double areaCoarseMm2;
        final double areaFineMm2;

        public MetricFastener(String name, double diaMm, double pCoarse, double pFine, double aCoarse, double aFine) {
            this.name = name;
            this.diaMm = diaMm;
            this.pitchCoarseMm = pCoarse;
            this.pitchFineMm = pFine;
            this.areaCoarseMm2 = aCoarse;
            this.areaFineMm2 = aFine;
        }

        @Override
        public String toString() {
            return name + " (" + diaMm + " mm)";
        }
    }

    public static class ImperialFastener {
        final String name;
        final double diaInches;
        final int tpiCoarse;
        final int tpiFine;
        final double areaCoarseSqIn;
        final double areaFineSqIn;

        public ImperialFastener(String name, double diaInches, int tpiCoarse, int tpiFine, double aCoarse, double aFine) {
            this.name = name;
            this.diaInches = diaInches;
            this.tpiCoarse = tpiCoarse;
            this.tpiFine = tpiFine;
            this.areaCoarseSqIn = aCoarse;
            this.areaFineSqIn = aFine;
        }

        @Override
        public String toString() {
            return name + " (" + diaInches + "\")";
        }
    }

    public static class FastenerGrade {
        final String name;
        final double proofMpa; // Metric proof strength in N/mm²
        final double proofPsi; // Imperial proof strength in PSI

        public FastenerGrade(String name, double proofMpa, double proofPsi) {
            this.name = name;
            this.proofMpa = proofMpa;
            this.proofPsi = proofPsi;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class LubricantType {
        final String name;
        final double kFactor;
        final String description;

        public LubricantType(String name, double kFactor, String description) {
            this.name = name;
            this.kFactor = kFactor;
            this.description = description;
        }

        @Override
        public String toString() {
            return name + " (K=" + String.format(Locale.US, "%.2f", kFactor) + ")";
        }
    }

    // Fastener Data Lists
    private final MetricFastener[] metricFasteners = new MetricFastener[]{
            new MetricFastener("M3", 3.0, 0.50, 0.35, 5.03, 5.61),
            new MetricFastener("M4", 4.0, 0.70, 0.50, 8.78, 9.79),
            new MetricFastener("M5", 5.0, 0.80, 0.50, 14.2, 16.1),
            new MetricFastener("M6", 6.0, 1.00, 0.75, 20.1, 22.0),
            new MetricFastener("M8", 8.0, 1.25, 1.00, 36.6, 39.2),
            new MetricFastener("M10", 10.0, 1.50, 1.25, 58.0, 61.2),
            new MetricFastener("M12", 12.0, 1.75, 1.25, 84.3, 92.1),
            new MetricFastener("M14", 14.0, 2.00, 1.50, 115.0, 125.0),
            new MetricFastener("M16", 16.0, 2.00, 1.50, 157.0, 167.0),
            new MetricFastener("M18", 18.0, 2.50, 1.50, 192.0, 216.0),
            new MetricFastener("M20", 20.0, 2.50, 1.50, 245.0, 272.0),
            new MetricFastener("M22", 22.0, 2.50, 1.50, 303.0, 333.0),
            new MetricFastener("M24", 24.0, 3.00, 2.00, 353.0, 384.0),
            new MetricFastener("M27", 27.0, 3.00, 2.00, 459.0, 496.0),
            new MetricFastener("M30", 30.0, 3.50, 2.00, 561.0, 621.0),
            new MetricFastener("M36", 36.0, 4.00, 3.00, 817.0, 865.0)
    };

    private final ImperialFastener[] imperialFasteners = new ImperialFastener[]{
            new ImperialFastener("#4 (0.112\")", 0.1120, 40, 48, 0.00604, 0.00661),
            new ImperialFastener("#6 (0.138\")", 0.1380, 32, 40, 0.00909, 0.01015),
            new ImperialFastener("#8 (0.164\")", 0.1640, 32, 36, 0.01400, 0.01470),
            new ImperialFastener("#10 (0.190\")", 0.1900, 24, 32, 0.01750, 0.02000),
            new ImperialFastener("1/4\"", 0.2500, 20, 28, 0.03180, 0.03640),
            new ImperialFastener("5/16\"", 0.3125, 18, 24, 0.05240, 0.05800),
            new ImperialFastener("3/8\"", 0.3750, 16, 24, 0.07750, 0.08780),
            new ImperialFastener("7/16\"", 0.4375, 14, 20, 0.10630, 0.11870),
            new ImperialFastener("1/2\"", 0.5000, 13, 20, 0.14190, 0.15990),
            new ImperialFastener("9/16\"", 0.5625, 12, 18, 0.18200, 0.20300),
            new ImperialFastener("5/8\"", 0.6250, 11, 18, 0.22600, 0.25600),
            new ImperialFastener("3/4\"", 0.7500, 10, 16, 0.33400, 0.37300),
            new ImperialFastener("7/8\"", 0.8750, 9, 14, 0.46200, 0.50900),
            new ImperialFastener("1\"", 1.0000, 8, 12, 0.60600, 0.66300),
            new ImperialFastener("1-1/8\"", 1.1250, 7, 12, 0.76300, 0.85600),
            new ImperialFastener("1-1/4\"", 1.2500, 7, 12, 0.96900, 1.07300),
            new ImperialFastener("1-1/2\"", 1.5000, 6, 12, 1.40500, 1.58100)
    };

    private final FastenerGrade[] metricGrades = new FastenerGrade[]{
            new FastenerGrade("Class 8.8 (High Tensile Automotive)", 600, 87022),
            new FastenerGrade("Class 10.9 (Heat Treated Alloy)", 830, 120381),
            new FastenerGrade("Class 12.9 (Extreme Tensile Socket)", 970, 140686),
            new FastenerGrade("Class 4.8 (Low Carbon Commercial)", 310, 44961),
            new FastenerGrade("Stainless A2-70 / A4-80 (Marine)", 450, 65267)
    };

    private final FastenerGrade[] imperialGrades = new FastenerGrade[]{
            new FastenerGrade("Grade 8 (High Tensile Alloy / 6-Hash)", 827, 120000),
            new FastenerGrade("Grade 5 (Automotive Med Carbon / 3-Hash)", 586, 85000),
            new FastenerGrade("Grade 2 (Commercial Low Carbon / No Hash)", 379, 55000),
            new FastenerGrade("Stainless 18-8 / 316 (Marine)", 448, 65000),
            new FastenerGrade("Alloy Steel Socket Head (170k PSI)", 1172, 170000)
    };

    private final LubricantType[] lubricantTypes = new LubricantType[]{
            new LubricantType("Light Engine / Machine Oil", 0.15, "Standard oiled fastener torque"),
            new LubricantType("Dry / Zinc Plated (Unlubricated)", 0.20, "High friction bare/cadmium threads"),
            new LubricantType("Anti-Seize / Copper Paste", 0.12, "Extreme pressure thread compound"),
            new LubricantType("ARP Moly Assembly Lube", 0.10, "Ultra-low friction racing engine lube"),
            new LubricantType("Medium Threadlocker (Blue)", 0.16, "Wet anaerobic thread locking fluid"),
            new LubricantType("Waxed / Phosphate & Oil", 0.14, "Factory coated black oxide coating")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadPreferences();
        applyCurrentTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        bindViews();
        setupSpinners();
        setupListeners();

        // Restore state if available
        if (savedInstanceState != null) {
            activeTab = savedInstanceState.getInt("saved_active_tab", activeTab);
            isMetric = savedInstanceState.getBoolean("saved_is_metric", isMetric);
            isCoarse = savedInstanceState.getBoolean("saved_is_coarse", isCoarse);
            unitMode = savedInstanceState.getInt("saved_unit_mode", unitMode);
            yieldPercent = savedInstanceState.getInt("saved_yield_percent", yieldPercent);
            stage1SnugFtLb = savedInstanceState.getInt("saved_stage1_snug", stage1SnugFtLb);
            stage2Degrees = savedInstanceState.getInt("saved_stage2_degrees", stage2Degrees);
            stage3Degrees = savedInstanceState.getInt("saved_stage3_degrees", stage3Degrees);
            isSpiralPattern = savedInstanceState.getBoolean("saved_pattern_spiral", isSpiralPattern);
            boolean[] savedBolts = savedInstanceState.getBooleanArray("saved_bolt_completed");
            if (savedBolts != null && savedBolts.length == 10) {
                System.arraycopy(savedBolts, 0, boltCompleted, 0, 10);
            }
        }

        updateStandardChips();
        updatePitchChips();
        updateUnitChips();
        updatePatternChips();
        updateSequenceTrackerUI();
        updateTTYText();
        switchTab(activeTab);

        calculateAllTelemetry();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("saved_active_tab", activeTab);
        outState.putBoolean("saved_is_metric", isMetric);
        outState.putBoolean("saved_is_coarse", isCoarse);
        outState.putInt("saved_unit_mode", unitMode);
        outState.putInt("saved_yield_percent", yieldPercent);
        outState.putInt("saved_stage1_snug", stage1SnugFtLb);
        outState.putInt("saved_stage2_degrees", stage2Degrees);
        outState.putInt("saved_stage3_degrees", stage3Degrees);
        outState.putBoolean("saved_pattern_spiral", isSpiralPattern);
        outState.putBooleanArray("saved_bolt_completed", boltCompleted);
    }

    private void bindViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle);
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        chipTabTorque = findViewById(R.id.chipTabTorque);
        chipTabTap = findViewById(R.id.chipTabTap);
        chipTabSequence = findViewById(R.id.chipTabSequence);

        mainScrollView = findViewById(R.id.mainScrollView);
        layoutSectionTorque = findViewById(R.id.layoutSectionTorque);
        layoutSectionTap = findViewById(R.id.layoutSectionTap);
        layoutSectionSequence = findViewById(R.id.layoutSectionSequence);

        // Section 1: Torque
        chipStandardMetric = findViewById(R.id.chipStandardMetric);
        chipStandardImperial = findViewById(R.id.chipStandardImperial);
        spnFastenerSize = findViewById(R.id.spnFastenerSize);
        chipPitchCoarse = findViewById(R.id.chipPitchCoarse);
        chipPitchFine = findViewById(R.id.chipPitchFine);
        spnFastenerGrade = findViewById(R.id.spnFastenerGrade);
        spnLubricant = findViewById(R.id.spnLubricant);
        btnYieldMinus = findViewById(R.id.btnYieldMinus);
        etYieldPercent = findViewById(R.id.etYieldPercent);
        btnYieldPlus = findViewById(R.id.btnYieldPlus);
        btnCalculateTorque = findViewById(R.id.btnCalculateTorque);

        // Hero HUD
        tvTorqueStatusBadge = findViewById(R.id.tvTorqueStatusBadge);
        tvFastenerTag = findViewById(R.id.tvFastenerTag);
        tvPrimaryTorque = findViewById(R.id.tvPrimaryTorque);
        tvSecondaryTorque = findViewById(R.id.tvSecondaryTorque);
        chipUnitFtLb = findViewById(R.id.chipUnitFtLb);
        chipUnitNm = findViewById(R.id.chipUnitNm);
        chipUnitInLb = findViewById(R.id.chipUnitInLb);
        tvClampForce = findViewById(R.id.tvClampForce);
        tvClampForceLbs = findViewById(R.id.tvClampForceLbs);
        tvStressArea = findViewById(R.id.tvStressArea);
        tvStressAreaSqIn = findViewById(R.id.tvStressAreaSqIn);
        tvLubeImpactNote = findViewById(R.id.tvLubeImpactNote);

        // Section 2: Tap & Pitch
        tvTapFastenerTitle = findViewById(R.id.tvTapFastenerTitle);
        tvTapFastenerSubtitle = findViewById(R.id.tvTapFastenerSubtitle);
        tvTapDrillSteel = findViewById(R.id.tvTapDrillSteel);
        tvTapDrillSteelAlt = findViewById(R.id.tvTapDrillSteelAlt);
        tvTapDepthNote = findViewById(R.id.tvTapDepthNote);
        tvTapDrillAlum = findViewById(R.id.tvTapDrillAlum);
        tvTapDrillAlumAlt = findViewById(R.id.tvTapDrillAlumAlt);
        tvClearanceNormal = findViewById(R.id.tvClearanceNormal);
        tvClearanceNormalAlt = findViewById(R.id.tvClearanceNormalAlt);
        tvClearanceClose = findViewById(R.id.tvClearanceClose);
        tvClearanceCloseAlt = findViewById(R.id.tvClearanceCloseAlt);
        tvSpecMajorDia = findViewById(R.id.tvSpecMajorDia);
        tvSpecPitch = findViewById(R.id.tvSpecPitch);
        tvSpecPitchDia = findViewById(R.id.tvSpecPitchDia);
        tvSpecMinorDia = findViewById(R.id.tvSpecMinorDia);

        // Section 3: TTY & Sequence
        tvStage1Snug = findViewById(R.id.tvStage1Snug);
        btnSnugMinus = findViewById(R.id.btnSnugMinus);
        btnSnugPlus = findViewById(R.id.btnSnugPlus);
        tvStage2Angle = findViewById(R.id.tvStage2Angle);
        btnAngle2Minus = findViewById(R.id.btnAngle2Minus);
        btnAngle2Plus = findViewById(R.id.btnAngle2Plus);
        tvStage3Angle = findViewById(R.id.tvStage3Angle);
        btnAngle3Minus = findViewById(R.id.btnAngle3Minus);
        btnAngle3Plus = findViewById(R.id.btnAngle3Plus);
        tvSequenceProgress = findViewById(R.id.tvSequenceProgress);
        chipPatternSpiral = findViewById(R.id.chipPatternSpiral);
        chipPatternStar = findViewById(R.id.chipPatternStar);
        tvSequenceGuide = findViewById(R.id.tvSequenceGuide);

        boltButtons[0] = findViewById(R.id.btnBolt1);
        boltButtons[1] = findViewById(R.id.btnBolt2);
        boltButtons[2] = findViewById(R.id.btnBolt3);
        boltButtons[3] = findViewById(R.id.btnBolt4);
        boltButtons[4] = findViewById(R.id.btnBolt5);
        boltButtons[5] = findViewById(R.id.btnBolt6);
        boltButtons[6] = findViewById(R.id.btnBolt7);
        boltButtons[7] = findViewById(R.id.btnBolt8);
        boltButtons[8] = findViewById(R.id.btnBolt9);
        boltButtons[9] = findViewById(R.id.btnBolt10);

        btnResetSequence = findViewById(R.id.btnResetSequence);

        updateThemeToggleButton();
    }

    private void setupSpinners() {
        if (isMetric) {
            ArrayAdapter<MetricFastener> sizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, metricFasteners);
            sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spnFastenerSize.setAdapter(sizeAdapter);
            spnFastenerSize.setSelection(5); // Default M10

            ArrayAdapter<FastenerGrade> gradeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, metricGrades);
            gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spnFastenerGrade.setAdapter(gradeAdapter);
            spnFastenerGrade.setSelection(0); // Default Class 8.8
        } else {
            ArrayAdapter<ImperialFastener> sizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, imperialFasteners);
            sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spnFastenerSize.setAdapter(sizeAdapter);
            spnFastenerSize.setSelection(6); // Default 3/8"

            ArrayAdapter<FastenerGrade> gradeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, imperialGrades);
            gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spnFastenerGrade.setAdapter(gradeAdapter);
            spnFastenerGrade.setSelection(1); // Default Grade 5
        }

        ArrayAdapter<LubricantType> lubeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, lubricantTypes);
        lubeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnLubricant.setAdapter(lubeAdapter);
        spnLubricant.setSelection(0); // Default Light Oil (K=0.15)
    }

    private void setupListeners() {
        // Theme & Settings
        btnThemeToggle.setOnClickListener(v -> toggleTheme());
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // Navigation Tabs
        chipTabTorque.setOnClickListener(v -> switchTab(0));
        chipTabTap.setOnClickListener(v -> switchTab(1));
        chipTabSequence.setOnClickListener(v -> switchTab(2));

        // Standard Switcher
        chipStandardMetric.setOnClickListener(v -> {
            if (!isMetric) {
                isMetric = true;
                updateStandardChips();
                setupSpinners();
                calculateAllTelemetry();
            }
        });

        chipStandardImperial.setOnClickListener(v -> {
            if (isMetric) {
                isMetric = false;
                updateStandardChips();
                setupSpinners();
                calculateAllTelemetry();
            }
        });

        // Pitch Selection
        chipPitchCoarse.setOnClickListener(v -> {
            if (!isCoarse) {
                isCoarse = true;
                updatePitchChips();
                calculateAllTelemetry();
            }
        });

        chipPitchFine.setOnClickListener(v -> {
            if (isCoarse) {
                isCoarse = false;
                updatePitchChips();
                calculateAllTelemetry();
            }
        });

        // Unit Selection
        chipUnitFtLb.setOnClickListener(v -> setUnitMode(0));
        chipUnitNm.setOnClickListener(v -> setUnitMode(1));
        chipUnitInLb.setOnClickListener(v -> setUnitMode(2));

        // Yield Steppers
        btnYieldMinus.setOnClickListener(v -> adjustYieldPercent(-5));
        btnYieldPlus.setOnClickListener(v -> adjustYieldPercent(5));
        btnCalculateTorque.setOnClickListener(v -> calculateAllTelemetry());

        // Spinner Selection Listeners
        AdapterView.OnItemSelectedListener autoCalcListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                calculateAllTelemetry();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spnFastenerSize.setOnItemSelectedListener(autoCalcListener);
        spnFastenerGrade.setOnItemSelectedListener(autoCalcListener);
        spnLubricant.setOnItemSelectedListener(autoCalcListener);

        // Section 3: TTY Steppers
        btnSnugMinus.setOnClickListener(v -> adjustSnug(-5));
        btnSnugPlus.setOnClickListener(v -> adjustSnug(5));
        btnAngle2Minus.setOnClickListener(v -> adjustAngle2(-15));
        btnAngle2Plus.setOnClickListener(v -> adjustAngle2(15));
        btnAngle3Minus.setOnClickListener(v -> adjustAngle3(-15));
        btnAngle3Plus.setOnClickListener(v -> adjustAngle3(15));

        // Pattern Selection
        chipPatternSpiral.setOnClickListener(v -> {
            isSpiralPattern = true;
            updatePatternChips();
        });
        chipPatternStar.setOnClickListener(v -> {
            isSpiralPattern = false;
            updatePatternChips();
        });

        // Bolt Sequence Buttons
        for (int i = 0; i < 10; i++) {
            final int index = i;
            boltButtons[i].setOnClickListener(v -> toggleBolt(index));
        }

        btnResetSequence.setOnClickListener(v -> resetBoltSequence());
    }

    private void switchTab(int tabIndex) {
        activeTab = tabIndex;
        saveState();

        // Update tab button visual state
        chipTabTorque.setBackgroundResource(tabIndex == 0 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipTabTorque.setTextColor(getColor(tabIndex == 0 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipTabTap.setBackgroundResource(tabIndex == 1 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipTabTap.setTextColor(getColor(tabIndex == 1 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipTabSequence.setBackgroundResource(tabIndex == 2 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipTabSequence.setTextColor(getColor(tabIndex == 2 ? R.color.m3_on_primary : R.color.m3_on_surface));

        // Toggle section visibility
        layoutSectionTorque.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutSectionTap.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        layoutSectionSequence.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
    }

    private void updateStandardChips() {
        chipStandardMetric.setBackgroundResource(isMetric ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipStandardMetric.setTextColor(getColor(isMetric ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipStandardImperial.setBackgroundResource(!isMetric ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipStandardImperial.setTextColor(getColor(!isMetric ? R.color.m3_on_primary : R.color.m3_on_surface));
    }

    private void updatePitchChips() {
        chipPitchCoarse.setBackgroundResource(isCoarse ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipPitchCoarse.setTextColor(getColor(isCoarse ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipPitchFine.setBackgroundResource(!isCoarse ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipPitchFine.setTextColor(getColor(!isCoarse ? R.color.m3_on_primary : R.color.m3_on_surface));
    }

    private void updateUnitChips() {
        chipUnitFtLb.setBackgroundResource(unitMode == 0 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipUnitFtLb.setTextColor(getColor(unitMode == 0 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipUnitNm.setBackgroundResource(unitMode == 1 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipUnitNm.setTextColor(getColor(unitMode == 1 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipUnitInLb.setBackgroundResource(unitMode == 2 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipUnitInLb.setTextColor(getColor(unitMode == 2 ? R.color.m3_on_primary : R.color.m3_on_surface));
    }

    private void setUnitMode(int mode) {
        unitMode = mode;
        saveState();
        updateUnitChips();
        calculateAllTelemetry();
    }

    private void adjustYieldPercent(int delta) {
        int parsed = yieldPercent;
        try {
            String text = etYieldPercent.getText().toString().trim().replaceAll("[^0-9]", "");
            if (!text.isEmpty()) {
                parsed = Integer.parseInt(text);
            }
        } catch (Exception ignored) {}

        parsed = Math.max(30, Math.min(95, parsed + delta));
        yieldPercent = parsed;
        etYieldPercent.setText(String.valueOf(yieldPercent));
        calculateAllTelemetry();
    }

    // =========================================================================
    // Core Engineering Calculation Engine
    // =========================================================================
    private void calculateAllTelemetry() {
        // Defensive Yield Percent parse
        try {
            String text = etYieldPercent.getText().toString().trim().replaceAll("[^0-9]", "");
            if (!text.isEmpty()) {
                yieldPercent = Math.max(30, Math.min(95, Integer.parseInt(text)));
            }
        } catch (Exception ignored) {}

        double nominalDiaMeters;
        double nominalDiaMm;
        double stressAreaMm2;
        double stressAreaSqIn;
        double proofStressMpa;
        String fastenerLabel;
        String gradeLabel;
        String pitchLabel;
        double pitchMm;
        int tpi = 0;

        // 1. Gather Fastener Geometry & Strength
        LubricantType lube = (LubricantType) spnLubricant.getSelectedItem();
        if (lube == null && lubricantTypes.length > 0) {
            lube = lubricantTypes[0];
        }
        double kFactor = lube != null ? lube.kFactor : 0.15;

        if (isMetric) {
            MetricFastener f = (MetricFastener) spnFastenerSize.getSelectedItem();
            if (f == null && metricFasteners.length > 0) {
                f = metricFasteners[5]; // M10
            }
            nominalDiaMm = f != null ? f.diaMm : 10.0;
            nominalDiaMeters = nominalDiaMm / 1000.0;
            pitchMm = isCoarse ? (f != null ? f.pitchCoarseMm : 1.5) : (f != null ? f.pitchFineMm : 1.25);
            stressAreaMm2 = isCoarse ? (f != null ? f.areaCoarseMm2 : 58.0) : (f != null ? f.areaFineMm2 : 61.2);
            stressAreaSqIn = stressAreaMm2 / 645.16;

            FastenerGrade g = (FastenerGrade) spnFastenerGrade.getSelectedItem();
            if (g == null && metricGrades.length > 0) {
                g = metricGrades[0];
            }
            proofStressMpa = g != null ? g.proofMpa : 600.0;

            fastenerLabel = (f != null ? f.name : "M10") + " × " + String.format(Locale.US, "%.2f", pitchMm);
            gradeLabel = g != null ? g.name : "Class 8.8";
            pitchLabel = isCoarse ? "Coarse" : "Fine";
        } else {
            ImperialFastener f = (ImperialFastener) spnFastenerSize.getSelectedItem();
            if (f == null && imperialFasteners.length > 0) {
                f = imperialFasteners[6]; // 3/8"
            }
            double diaInches = f != null ? f.diaInches : 0.375;
            nominalDiaMm = diaInches * 25.4;
            nominalDiaMeters = nominalDiaMm / 1000.0;
            tpi = isCoarse ? (f != null ? f.tpiCoarse : 16) : (f != null ? f.tpiFine : 24);
            pitchMm = 25.4 / (tpi > 0 ? tpi : 16);
            stressAreaSqIn = isCoarse ? (f != null ? f.areaCoarseSqIn : 0.0775) : (f != null ? f.areaFineSqIn : 0.0878);
            stressAreaMm2 = stressAreaSqIn * 645.16;

            FastenerGrade g = (FastenerGrade) spnFastenerGrade.getSelectedItem();
            if (g == null && imperialGrades.length > 0) {
                g = imperialGrades[1];
            }
            proofStressMpa = g != null ? g.proofMpa : 586.0;

            fastenerLabel = (f != null ? f.name : "3/8\"") + " - " + tpi + " " + (isCoarse ? "UNC" : "UNF");
            gradeLabel = g != null ? g.name : "Grade 5";
            pitchLabel = isCoarse ? "UNC Coarse" : "UNF Fine";
        }

        // 2. Preload Clamping Force (Newtons)
        // Fp = (Yield% / 100) * As (mm2) * ProofStress (N/mm2)
        double clampForceNewtons = (yieldPercent / 100.0) * stressAreaMm2 * proofStressMpa;
        double clampForceKn = clampForceNewtons / 1000.0;
        double clampForceLbs = clampForceNewtons * 0.224809;

        // 3. Torque (T = K * D * Fp)
        double torqueNm = kFactor * nominalDiaMeters * clampForceNewtons;
        double torqueFtLb = torqueNm * 0.737562;
        double torqueInLb = torqueFtLb * 12.0;

        // Defensive checks against NaN/Infinity
        if (Double.isNaN(torqueNm) || Double.isInfinite(torqueNm) || torqueNm < 0) torqueNm = 0.0;
        if (Double.isNaN(torqueFtLb) || Double.isInfinite(torqueFtLb) || torqueFtLb < 0) torqueFtLb = 0.0;
        if (Double.isNaN(torqueInLb) || Double.isInfinite(torqueInLb) || torqueInLb < 0) torqueInLb = 0.0;

        // Update Hero HUD Readout
        tvTorqueStatusBadge.setText(String.format(Locale.US, "TARGET TORQUE (%d%% YIELD)", yieldPercent));
        tvFastenerTag.setText(fastenerLabel + " • " + (lube != null ? lube.name.split(" ")[0] : "Lube"));

        if (unitMode == 0) {
            tvPrimaryTorque.setText(String.format(Locale.US, "%.1f ft-lb", torqueFtLb));
            tvSecondaryTorque.setText(String.format(Locale.US, "%.1f Nm  •  %.0f in-lb", torqueNm, torqueInLb));
        } else if (unitMode == 1) {
            tvPrimaryTorque.setText(String.format(Locale.US, "%.1f Nm", torqueNm));
            tvSecondaryTorque.setText(String.format(Locale.US, "%.1f ft-lb  •  %.0f in-lb", torqueFtLb, torqueInLb));
        } else {
            tvPrimaryTorque.setText(String.format(Locale.US, "%.0f in-lb", torqueInLb));
            tvSecondaryTorque.setText(String.format(Locale.US, "%.1f ft-lb  •  %.1f Nm", torqueFtLb, torqueNm));
        }

        tvClampForce.setText(String.format(Locale.US, "%.1f kN", clampForceKn));
        tvClampForceLbs.setText(String.format(Locale.US, "~%,.0f lbs force", clampForceLbs));
        tvStressArea.setText(String.format(Locale.US, "%.1f mm²", stressAreaMm2));
        tvStressAreaSqIn.setText(String.format(Locale.US, "%.4f sq in", stressAreaSqIn));

        // Lube Friction Impact Callout
        double dryTorqueNm = 0.20 * nominalDiaMeters * clampForceNewtons;
        double percentReduction = ((dryTorqueNm - torqueNm) / (dryTorqueNm > 0 ? dryTorqueNm : 1.0)) * 100.0;
        if (percentReduction > 0) {
            tvLubeImpactNote.setText(String.format(Locale.US,
                    "%s (K=%.2f) achieves full clamping preload with %.0f%% less wrench torque compared to dry threads (K=0.20).",
                    lube != null ? lube.name : "Lubricant", kFactor, percentReduction));
        } else {
            tvLubeImpactNote.setText(String.format(Locale.US,
                    "Dry threads (K=%.2f) experience high thread friction; 90%% of applied torque is lost overcoming friction.",
                    kFactor));
        }

        // 4. Update Tap & Pitch Reference Tab
        updateTapSpecs(nominalDiaMm, pitchMm, tpi, fastenerLabel, pitchLabel);
    }

    private void updateTapSpecs(double nominalDiaMm, double pitchMm, int tpi, String fastenerLabel, String pitchLabel) {
        tvTapFastenerTitle.setText(fastenerLabel + " " + pitchLabel);
        tvTapFastenerSubtitle.setText(String.format(Locale.US, "Major Diameter: %.3f mm (%.4f in)", nominalDiaMm, nominalDiaMm / 25.4));

        // Steel / Iron (75% Thread Engagement)
        double tapSteelMm;
        double tapAlumMm;
        String steelAlt;
        String alumAlt;

        if (isMetric) {
            tapSteelMm = nominalDiaMm - pitchMm;
            tapAlumMm = nominalDiaMm - (0.80 * pitchMm);
            steelAlt = String.format(Locale.US, "%.3f in / %s drill", tapSteelMm / 25.4, getClosestFractionalInch(tapSteelMm / 25.4));
            alumAlt = String.format(Locale.US, "%.3f in / %s drill", tapAlumMm / 25.4, getClosestFractionalInch(tapAlumMm / 25.4));
        } else {
            double diaInches = nominalDiaMm / 25.4;
            double tapSteelIn = diaInches - (0.9743 / (tpi > 0 ? tpi : 16));
            double tapAlumIn = diaInches - (0.8444 / (tpi > 0 ? tpi : 16));
            tapSteelMm = tapSteelIn * 25.4;
            tapAlumMm = tapAlumIn * 25.4;
            steelAlt = String.format(Locale.US, "%.4f in / %s drill", tapSteelIn, getClosestFractionalInch(tapSteelIn));
            alumAlt = String.format(Locale.US, "%.4f in / %s drill", tapAlumIn, getClosestFractionalInch(tapAlumIn));
        }

        tvTapDrillSteel.setText(String.format(Locale.US, "%.1f mm", tapSteelMm));
        tvTapDrillSteelAlt.setText(steelAlt);
        tvTapDepthNote.setText(String.format(Locale.US, "Tapping depth: ~%.1f mm (1.5×D)", nominalDiaMm * 1.5));

        tvTapDrillAlum.setText(String.format(Locale.US, "%.1f mm", tapAlumMm));
        tvTapDrillAlumAlt.setText(alumAlt);

        // Through Hole Clearance
        double clearNormalMm = nominalDiaMm + (nominalDiaMm <= 10.0 ? 1.0 : (nominalDiaMm <= 16.0 ? 1.5 : 2.0));
        double clearCloseMm = nominalDiaMm + (nominalDiaMm <= 10.0 ? 0.5 : (nominalDiaMm <= 16.0 ? 0.8 : 1.0));

        tvClearanceNormal.setText(String.format(Locale.US, "%.1f mm", clearNormalMm));
        tvClearanceNormalAlt.setText(String.format(Locale.US, "%.3f in (%s)", clearNormalMm / 25.4, getClosestFractionalInch(clearNormalMm / 25.4)));

        tvClearanceClose.setText(String.format(Locale.US, "%.1f mm", clearCloseMm));
        tvClearanceCloseAlt.setText(String.format(Locale.US, "%.3f in (%s)", clearCloseMm / 25.4, getClosestFractionalInch(clearCloseMm / 25.4)));

        // Geometry Specs
        double pitchDiaMm = nominalDiaMm - (0.6495 * pitchMm);
        double minorDiaMm = nominalDiaMm - (1.2268 * pitchMm);

        tvSpecMajorDia.setText(String.format(Locale.US, "%.3f mm (%.4f in)", nominalDiaMm, nominalDiaMm / 25.4));
        tvSpecPitch.setText(String.format(Locale.US, "%.3f mm (%.1f TPI)", pitchMm, 25.4 / pitchMm));
        tvSpecPitchDia.setText(String.format(Locale.US, "%.3f mm (%.4f in)", pitchDiaMm, pitchDiaMm / 25.4));
        tvSpecMinorDia.setText(String.format(Locale.US, "%.3f mm (%.4f in)", minorDiaMm, minorDiaMm / 25.4));
    }

    private String getClosestFractionalInch(double decInches) {
        int sixteenths = (int) Math.round(decInches * 16.0);
        if (sixteenths <= 0) return "1/32\"";
        if (sixteenths == 16) return "1\"";
        if (sixteenths % 8 == 0) return (sixteenths / 8) + "/2\"";
        if (sixteenths % 4 == 0) return (sixteenths / 4) + "/4\"";
        if (sixteenths % 2 == 0) return (sixteenths / 2) + "/8\"";
        return sixteenths + "/16\"";
    }

    // =========================================================================
    // Section 3: TTY & Fastener Tightening Sequence
    // =========================================================================
    private void adjustSnug(int delta) {
        stage1SnugFtLb = Math.max(10, Math.min(150, stage1SnugFtLb + delta));
        updateTTYText();
    }

    private void adjustAngle2(int delta) {
        stage2Degrees = Math.max(0, Math.min(180, stage2Degrees + delta));
        updateTTYText();
    }

    private void adjustAngle3(int delta) {
        stage3Degrees = Math.max(0, Math.min(180, stage3Degrees + delta));
        updateTTYText();
    }

    private void updateTTYText() {
        double nm = stage1SnugFtLb / 0.737562;
        tvStage1Snug.setText(String.format(Locale.US, "%d ft-lb (%.1f Nm)", stage1SnugFtLb, nm));
        tvStage2Angle.setText(String.format(Locale.US, "+%d° Turn (%s)", stage2Degrees, getAngleFraction(stage2Degrees)));
        tvStage3Angle.setText(String.format(Locale.US, "+%d° Turn (%s)", stage3Degrees, getAngleFraction(stage3Degrees)));
    }

    private String getAngleFraction(int deg) {
        if (deg == 0) return "None";
        if (deg == 45) return "1/8 turn";
        if (deg == 60) return "1/6 turn";
        if (deg == 90) return "1/4 turn";
        if (deg == 120) return "1/3 turn";
        if (deg == 180) return "1/2 turn";
        return deg + " degrees";
    }

    private void updatePatternChips() {
        chipPatternSpiral.setBackgroundResource(isSpiralPattern ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipPatternSpiral.setTextColor(getColor(isSpiralPattern ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipPatternStar.setBackgroundResource(!isSpiralPattern ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipPatternStar.setTextColor(getColor(!isSpiralPattern ? R.color.m3_on_primary : R.color.m3_on_surface));

        if (isSpiralPattern) {
            tvSequenceGuide.setText("Cylinder Spiral Pattern: Tighten starting at center studs and spiral outward to avoid head gasket crimping or head warpage.");
        } else {
            tvSequenceGuide.setText("Star / Cross Pattern: Tighten opposing cross fasteners (12 o'clock, 6 o'clock, 3 o'clock, 9 o'clock) for uniform flange clamping.");
        }
    }

    private void toggleBolt(int index) {
        if (index < 0 || index >= 10) return;
        boltCompleted[index] = !boltCompleted[index];

        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(30);
        }

        updateSequenceTrackerUI();
    }

    private void resetBoltSequence() {
        for (int i = 0; i < 10; i++) {
            boltCompleted[i] = false;
        }
        updateSequenceTrackerUI();
        Toast.makeText(this, "Sequence reset to Bolt #1", Toast.LENGTH_SHORT).show();
    }

    private void updateSequenceTrackerUI() {
        int completedCount = 0;
        for (int i = 0; i < 10; i++) {
            boolean done = boltCompleted[i];
            if (done) completedCount++;

            boltButtons[i].setText(done ? "✓ #" + (i + 1) : "#" + (i + 1));
            boltButtons[i].setBackgroundResource(done ? R.drawable.chip_active : R.drawable.btn_stepper);
            boltButtons[i].setTextColor(getColor(done ? R.color.m3_on_primary : R.color.m3_on_surface));
        }

        tvSequenceProgress.setText(completedCount + "/10 Done");
    }

    // =========================================================================
    // Working Day / Night / Auto Theme Switching
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
    // Settings & About Modal Dialog
    // =========================================================================
    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("TorquePitch")
                .setMessage("Bolt Torque, Thread Pitch & Tap Spec Matrix\nVersion 1.0.0\n\nKimRosil App Studio Portfolio freeware.\n100% Offline, on-device calculation.\n\nPrecise ISO 898-1 and SAE J429 fastener clamping calculations, lubricant K-factor adjustment, and TTY sequence tracking.")
                .setPositiveButton("Close", null)
                .setNeutralButton("Reset Sample Data", (dialog, which) -> {
                    isMetric = true;
                    isCoarse = true;
                    unitMode = 0;
                    yieldPercent = 75;
                    etYieldPercent.setText("75");
                    stage1SnugFtLb = 30;
                    stage2Degrees = 90;
                    stage3Degrees = 90;
                    isSpiralPattern = true;
                    for (int i = 0; i < 10; i++) boltCompleted[i] = false;

                    updateStandardChips();
                    updatePitchChips();
                    updateUnitChips();
                    updatePatternChips();
                    updateSequenceTrackerUI();
                    setupSpinners();
                    calculateAllTelemetry();
                    Toast.makeText(MainActivity.this, "Reset to standard workshop specs", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        activeTab = prefs.getInt(KEY_ACTIVE_TAB, 0);
        isMetric = prefs.getBoolean(KEY_STANDARD, true);
        unitMode = prefs.getInt(KEY_UNIT_MODE, 0);
        yieldPercent = prefs.getInt(KEY_YIELD_PERCENT, 75);
    }

    private void saveState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(KEY_THEME_MODE, themeMode);
        editor.putInt(KEY_ACTIVE_TAB, activeTab);
        editor.putBoolean(KEY_STANDARD, isMetric);
        editor.putInt(KEY_UNIT_MODE, unitMode);
        editor.putInt(KEY_YIELD_PERCENT, yieldPercent);
        editor.apply();
    }
}
