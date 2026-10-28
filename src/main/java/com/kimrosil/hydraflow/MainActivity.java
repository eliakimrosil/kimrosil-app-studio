package com.kimrosil.hydraflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
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

    private static final String PREFS_NAME = "HydraFlowPrefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_ACTIVE_TAB = "active_tab";
    private static final String KEY_IS_METRIC = "is_metric";
    private static final String KEY_LINE_SERVICE = "line_service";

    // Header Views
    private TextView tvAppTitle;
    private TextView tvAppSubtitle;
    private Button btnThemeToggle;
    private ImageButton btnSettings;

    // Navigation Tabs
    private Button chipTabCylinder;
    private Button chipTabSpeed;
    private Button chipTabVelocity;

    // Sections
    private ScrollView mainScrollView;
    private LinearLayout layoutSectionCylinder;
    private LinearLayout layoutSectionSpeed;
    private LinearLayout layoutSectionVelocity;

    // Section 1: Cylinder Views
    private Button chipUnitImperial;
    private Button chipUnitMetric;
    private TextView tvBoreLabel;
    private Button btnBoreMinus;
    private EditText etBoreDia;
    private Button btnBorePlus;
    private TextView tvRodLabel;
    private Button btnRodMinus;
    private EditText etRodDia;
    private Button btnRodPlus;
    private TextView tvPressureLabel;
    private Button btnPressureMinus;
    private EditText etPressure;
    private Button btnPressurePlus;
    private Button btnPresetLow;
    private Button btnPresetMid;
    private Button btnPresetHigh;
    private Button btnCalculateCylinder;

    // Hero Force HUD Views
    private TextView tvForceStatusBadge;
    private TextView tvCylinderSummaryTag;
    private TextView tvPrimaryPushForce;
    private TextView tvSecondaryPushForce;
    private TextView tvPullForce;
    private TextView tvPullForceAlt;
    private TextView tvAreaPush;
    private TextView tvAreaPull;
    private TextView tvDifferentialNote;

    // Section 2: Speed Views
    private TextView tvStrokeLabel;
    private Button btnStrokeMinus;
    private EditText etStrokeLength;
    private Button btnStrokePlus;
    private TextView tvFlowLabel;
    private Button btnFlowMinus;
    private EditText etPumpFlow;
    private Button btnFlowPlus;
    private Button btnCalculateSpeed;

    // Hero Speed HUD Views
    private TextView tvCycleStatusBadge;
    private TextView tvTotalCycleTime;
    private TextView tvSpeedBreakdown;
    private TextView tvFluidHp;
    private TextView tvFluidKw;
    private TextView tvVolumeExtend;
    private TextView tvVolumeRetract;
    private TextView tvPowerGuidance;

    // Section 3: Velocity Views
    private Button chipLinePressure;
    private Button chipLineReturn;
    private Button chipLineSuction;
    private Spinner spnHoseSize;
    private Button btnCalculateVelocity;

    // Hero Velocity HUD Views
    private TextView tvVelocityStatusBadge;
    private TextView tvVelocityFlowTag;
    private TextView tvPrimaryVelocity;
    private TextView tvSecondaryVelocity;
    private TextView tvRecommendedLimit;
    private TextView tvServiceLimitLabel;
    private TextView tvComplianceStatus;
    private TextView tvCavitationRisk;
    private TextView tvVelocityGuidance;

    // State Variables
    private int themeMode = 0; // 0: Auto, 1: Light, 2: Dark
    private int activeTab = 0;  // 0: Cylinder, 1: Speed, 2: Velocity
    private boolean isMetric = false; // false = US Customary (PSI/in), true = Metric (Bar/mm)
    private int lineServiceMode = 0; // 0: Pressure, 1: Return, 2: Suction

    private Vibrator vibrator;

    // Hose Size Model: Dash, Inch ID, mm ID
    public static class HoseSize {
        final String dashName;
        final double idInches;
        final double idMm;

        public HoseSize(String dashName, double idInches, double idMm) {
            this.dashName = dashName;
            this.idInches = idInches;
            this.idMm = idMm;
        }

        @Override
        public String toString() {
            return dashName + " (" + String.format(Locale.US, "%.3f\" / %.1f mm", idInches, idMm) + ")";
        }
    }

    private final HoseSize[] hoseSizes = new HoseSize[]{
            new HoseSize("-04 (1/4\" Hose)", 0.250, 6.35),
            new HoseSize("-06 (3/8\" Hose)", 0.375, 9.53),
            new HoseSize("-08 (1/2\" Hose)", 0.500, 12.70),
            new HoseSize("-10 (5/8\" Hose)", 0.625, 15.88),
            new HoseSize("-12 (3/4\" Hose)", 0.750, 19.05),
            new HoseSize("-16 (1\" Hose)", 1.000, 25.40),
            new HoseSize("-20 (1-1/4\" Hose)", 1.250, 31.75),
            new HoseSize("-24 (1-1/2\" Hose)", 1.500, 38.10),
            new HoseSize("-32 (2\" Hose)", 2.000, 50.80)
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

        // Restore instance state if rotated or recreated
        if (savedInstanceState != null) {
            activeTab = savedInstanceState.getInt("saved_active_tab", activeTab);
            isMetric = savedInstanceState.getBoolean("saved_is_metric", isMetric);
            lineServiceMode = savedInstanceState.getInt("saved_line_service", lineServiceMode);

            String bore = savedInstanceState.getString("saved_bore");
            String rod = savedInstanceState.getString("saved_rod");
            String press = savedInstanceState.getString("saved_press");
            String stroke = savedInstanceState.getString("saved_stroke");
            String flow = savedInstanceState.getString("saved_flow");
            int hoseIdx = savedInstanceState.getInt("saved_hose_idx", 2);

            if (bore != null) etBoreDia.setText(bore);
            if (rod != null) etRodDia.setText(rod);
            if (press != null) etPressure.setText(press);
            if (stroke != null) etStrokeLength.setText(stroke);
            if (flow != null) etPumpFlow.setText(flow);
            if (hoseIdx >= 0 && hoseIdx < hoseSizes.length) spnHoseSize.setSelection(hoseIdx);
        }

        updateUnitChips();
        updateLineServiceChips();
        switchTab(activeTab);

        calculateAllTelemetry();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("saved_active_tab", activeTab);
        outState.putBoolean("saved_is_metric", isMetric);
        outState.putInt("saved_line_service", lineServiceMode);
        outState.putString("saved_bore", etBoreDia.getText().toString());
        outState.putString("saved_rod", etRodDia.getText().toString());
        outState.putString("saved_press", etPressure.getText().toString());
        outState.putString("saved_stroke", etStrokeLength.getText().toString());
        outState.putString("saved_flow", etPumpFlow.getText().toString());
        outState.putInt("saved_hose_idx", spnHoseSize.getSelectedItemPosition());
    }

    private void bindViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle);
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        chipTabCylinder = findViewById(R.id.chipTabCylinder);
        chipTabSpeed = findViewById(R.id.chipTabSpeed);
        chipTabVelocity = findViewById(R.id.chipTabVelocity);

        mainScrollView = findViewById(R.id.mainScrollView);
        layoutSectionCylinder = findViewById(R.id.layoutSectionCylinder);
        layoutSectionSpeed = findViewById(R.id.layoutSectionSpeed);
        layoutSectionVelocity = findViewById(R.id.layoutSectionVelocity);

        // Section 1: Cylinder
        chipUnitImperial = findViewById(R.id.chipUnitImperial);
        chipUnitMetric = findViewById(R.id.chipUnitMetric);
        tvBoreLabel = findViewById(R.id.tvBoreLabel);
        btnBoreMinus = findViewById(R.id.btnBoreMinus);
        etBoreDia = findViewById(R.id.etBoreDia);
        btnBorePlus = findViewById(R.id.btnBorePlus);
        tvRodLabel = findViewById(R.id.tvRodLabel);
        btnRodMinus = findViewById(R.id.btnRodMinus);
        etRodDia = findViewById(R.id.etRodDia);
        btnRodPlus = findViewById(R.id.btnRodPlus);
        tvPressureLabel = findViewById(R.id.tvPressureLabel);
        btnPressureMinus = findViewById(R.id.btnPressureMinus);
        etPressure = findViewById(R.id.etPressure);
        btnPressurePlus = findViewById(R.id.btnPressurePlus);
        btnPresetLow = findViewById(R.id.btnPresetLow);
        btnPresetMid = findViewById(R.id.btnPresetMid);
        btnPresetHigh = findViewById(R.id.btnPresetHigh);
        btnCalculateCylinder = findViewById(R.id.btnCalculateCylinder);

        // Hero Force HUD
        tvForceStatusBadge = findViewById(R.id.tvForceStatusBadge);
        tvCylinderSummaryTag = findViewById(R.id.tvCylinderSummaryTag);
        tvPrimaryPushForce = findViewById(R.id.tvPrimaryPushForce);
        tvSecondaryPushForce = findViewById(R.id.tvSecondaryPushForce);
        tvPullForce = findViewById(R.id.tvPullForce);
        tvPullForceAlt = findViewById(R.id.tvPullForceAlt);
        tvAreaPush = findViewById(R.id.tvAreaPush);
        tvAreaPull = findViewById(R.id.tvAreaPull);
        tvDifferentialNote = findViewById(R.id.tvDifferentialNote);

        // Section 2: Speed
        tvStrokeLabel = findViewById(R.id.tvStrokeLabel);
        btnStrokeMinus = findViewById(R.id.btnStrokeMinus);
        etStrokeLength = findViewById(R.id.etStrokeLength);
        btnStrokePlus = findViewById(R.id.btnStrokePlus);
        tvFlowLabel = findViewById(R.id.tvFlowLabel);
        btnFlowMinus = findViewById(R.id.btnFlowMinus);
        etPumpFlow = findViewById(R.id.etPumpFlow);
        btnFlowPlus = findViewById(R.id.btnFlowPlus);
        btnCalculateSpeed = findViewById(R.id.btnCalculateSpeed);

        // Hero Speed HUD
        tvCycleStatusBadge = findViewById(R.id.tvCycleStatusBadge);
        tvTotalCycleTime = findViewById(R.id.tvTotalCycleTime);
        tvSpeedBreakdown = findViewById(R.id.tvSpeedBreakdown);
        tvFluidHp = findViewById(R.id.tvFluidHp);
        tvFluidKw = findViewById(R.id.tvFluidKw);
        tvVolumeExtend = findViewById(R.id.tvVolumeExtend);
        tvVolumeRetract = findViewById(R.id.tvVolumeRetract);
        tvPowerGuidance = findViewById(R.id.tvPowerGuidance);

        // Section 3: Velocity
        chipLinePressure = findViewById(R.id.chipLinePressure);
        chipLineReturn = findViewById(R.id.chipLineReturn);
        chipLineSuction = findViewById(R.id.chipLineSuction);
        spnHoseSize = findViewById(R.id.spnHoseSize);
        btnCalculateVelocity = findViewById(R.id.btnCalculateVelocity);

        // Hero Velocity HUD
        tvVelocityStatusBadge = findViewById(R.id.tvVelocityStatusBadge);
        tvVelocityFlowTag = findViewById(R.id.tvVelocityFlowTag);
        tvPrimaryVelocity = findViewById(R.id.tvPrimaryVelocity);
        tvSecondaryVelocity = findViewById(R.id.tvSecondaryVelocity);
        tvRecommendedLimit = findViewById(R.id.tvRecommendedLimit);
        tvServiceLimitLabel = findViewById(R.id.tvServiceLimitLabel);
        tvComplianceStatus = findViewById(R.id.tvComplianceStatus);
        tvCavitationRisk = findViewById(R.id.tvCavitationRisk);
        tvVelocityGuidance = findViewById(R.id.tvVelocityGuidance);

        updateThemeToggleButton();
    }

    private void setupSpinners() {
        ArrayAdapter<HoseSize> hoseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hoseSizes);
        hoseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnHoseSize.setAdapter(hoseAdapter);
        spnHoseSize.setSelection(2); // Default -08 (1/2" Hose)
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> toggleTheme());
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        chipTabCylinder.setOnClickListener(v -> switchTab(0));
        chipTabSpeed.setOnClickListener(v -> switchTab(1));
        chipTabVelocity.setOnClickListener(v -> switchTab(2));

        chipUnitImperial.setOnClickListener(v -> {
            if (isMetric) {
                isMetric = false;
                convertToImperial();
                updateUnitChips();
                calculateAllTelemetry();
            }
        });

        chipUnitMetric.setOnClickListener(v -> {
            if (!isMetric) {
                isMetric = true;
                convertToMetric();
                updateUnitChips();
                calculateAllTelemetry();
            }
        });

        // Section 1 Steppers
        btnBoreMinus.setOnClickListener(v -> adjustBore(isMetric ? -5.0 : -0.25));
        btnBorePlus.setOnClickListener(v -> adjustBore(isMetric ? 5.0 : 0.25));
        btnRodMinus.setOnClickListener(v -> adjustRod(isMetric ? -5.0 : -0.25));
        btnRodPlus.setOnClickListener(v -> adjustRod(isMetric ? 5.0 : 0.25));
        btnPressureMinus.setOnClickListener(v -> adjustPressure(isMetric ? -20 : -250));
        btnPressurePlus.setOnClickListener(v -> adjustPressure(isMetric ? 20 : 250));

        // Preset Pressure Buttons
        btnPresetLow.setOnClickListener(v -> setPressurePreset(isMetric ? 140 : 2000));
        btnPresetMid.setOnClickListener(v -> setPressurePreset(isMetric ? 210 : 3000));
        btnPresetHigh.setOnClickListener(v -> setPressurePreset(isMetric ? 350 : 5000));
        btnCalculateCylinder.setOnClickListener(v -> calculateAllTelemetry());

        // Section 2 Steppers
        btnStrokeMinus.setOnClickListener(v -> adjustStroke(isMetric ? -50.0 : -2.0));
        btnStrokePlus.setOnClickListener(v -> adjustStroke(isMetric ? 50.0 : 2.0));
        btnFlowMinus.setOnClickListener(v -> adjustFlow(isMetric ? -5.0 : -1.0));
        btnFlowPlus.setOnClickListener(v -> adjustFlow(isMetric ? 5.0 : 1.0));
        btnCalculateSpeed.setOnClickListener(v -> calculateAllTelemetry());

        // Section 3 Line Service Chips
        chipLinePressure.setOnClickListener(v -> setLineService(0));
        chipLineReturn.setOnClickListener(v -> setLineService(1));
        chipLineSuction.setOnClickListener(v -> setLineService(2));

        spnHoseSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                calculateAllTelemetry();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnCalculateVelocity.setOnClickListener(v -> calculateAllTelemetry());

        // Auto calculation on input text change
        TextWatcher autoWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                calculateAllTelemetry();
            }
        };

        etBoreDia.addTextChangedListener(autoWatcher);
        etRodDia.addTextChangedListener(autoWatcher);
        etPressure.addTextChangedListener(autoWatcher);
        etStrokeLength.addTextChangedListener(autoWatcher);
        etPumpFlow.addTextChangedListener(autoWatcher);
    }

    private void switchTab(int tabIndex) {
        activeTab = tabIndex;
        saveState();

        chipTabCylinder.setBackgroundResource(tabIndex == 0 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipTabCylinder.setTextColor(getColor(tabIndex == 0 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipTabSpeed.setBackgroundResource(tabIndex == 1 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipTabSpeed.setTextColor(getColor(tabIndex == 1 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipTabVelocity.setBackgroundResource(tabIndex == 2 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipTabVelocity.setTextColor(getColor(tabIndex == 2 ? R.color.m3_on_primary : R.color.m3_on_surface));

        layoutSectionCylinder.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutSectionSpeed.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        layoutSectionVelocity.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
    }

    private void updateUnitChips() {
        chipUnitImperial.setBackgroundResource(!isMetric ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipUnitImperial.setTextColor(getColor(!isMetric ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipUnitMetric.setBackgroundResource(isMetric ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipUnitMetric.setTextColor(getColor(isMetric ? R.color.m3_on_primary : R.color.m3_on_surface));

        if (!isMetric) {
            tvBoreLabel.setText("Cylinder Bore Diameter (in)");
            tvRodLabel.setText("Cylinder Piston Rod Diameter (in)");
            tvPressureLabel.setText("System Operating Pressure (PSI)");
            btnPresetLow.setText("2,000 PSI");
            btnPresetMid.setText("3,000 PSI");
            btnPresetHigh.setText("5,000 PSI");

            tvStrokeLabel.setText("Cylinder Stroke Length (in)");
            tvFlowLabel.setText("Pump Flow Rate (GPM)");
        } else {
            tvBoreLabel.setText("Cylinder Bore Diameter (mm)");
            tvRodLabel.setText("Cylinder Piston Rod Diameter (mm)");
            tvPressureLabel.setText("System Operating Pressure (Bar)");
            btnPresetLow.setText("140 Bar");
            btnPresetMid.setText("210 Bar");
            btnPresetHigh.setText("350 Bar");

            tvStrokeLabel.setText("Cylinder Stroke Length (mm)");
            tvFlowLabel.setText("Pump Flow Rate (L/min)");
        }
    }

    private void updateLineServiceChips() {
        chipLinePressure.setBackgroundResource(lineServiceMode == 0 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipLinePressure.setTextColor(getColor(lineServiceMode == 0 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipLineReturn.setBackgroundResource(lineServiceMode == 1 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipLineReturn.setTextColor(getColor(lineServiceMode == 1 ? R.color.m3_on_primary : R.color.m3_on_surface));

        chipLineSuction.setBackgroundResource(lineServiceMode == 2 ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipLineSuction.setTextColor(getColor(lineServiceMode == 2 ? R.color.m3_on_primary : R.color.m3_on_surface));
    }

    private void setLineService(int mode) {
        lineServiceMode = mode;
        saveState();
        updateLineServiceChips();
        calculateAllTelemetry();
    }

    private void convertToMetric() {
        double bore = parseDouble(etBoreDia.getText().toString(), 4.0) * 25.4;
        double rod = parseDouble(etRodDia.getText().toString(), 2.0) * 25.4;
        double press = parseDouble(etPressure.getText().toString(), 3000.0) / 14.5038;
        double stroke = parseDouble(etStrokeLength.getText().toString(), 24.0) * 25.4;
        double flow = parseDouble(etPumpFlow.getText().toString(), 10.0) * 3.78541;

        etBoreDia.setText(String.format(Locale.US, "%.1f", bore));
        etRodDia.setText(String.format(Locale.US, "%.1f", rod));
        etPressure.setText(String.format(Locale.US, "%.0f", press));
        etStrokeLength.setText(String.format(Locale.US, "%.0f", stroke));
        etPumpFlow.setText(String.format(Locale.US, "%.1f", flow));
    }

    private void convertToImperial() {
        double bore = parseDouble(etBoreDia.getText().toString(), 100.0) / 25.4;
        double rod = parseDouble(etRodDia.getText().toString(), 50.0) / 25.4;
        double press = parseDouble(etPressure.getText().toString(), 210.0) * 14.5038;
        double stroke = parseDouble(etStrokeLength.getText().toString(), 600.0) / 25.4;
        double flow = parseDouble(etPumpFlow.getText().toString(), 40.0) / 3.78541;

        etBoreDia.setText(String.format(Locale.US, "%.2f", bore));
        etRodDia.setText(String.format(Locale.US, "%.2f", rod));
        etPressure.setText(String.format(Locale.US, "%.0f", press));
        etStrokeLength.setText(String.format(Locale.US, "%.1f", stroke));
        etPumpFlow.setText(String.format(Locale.US, "%.1f", flow));
    }

    private void adjustBore(double delta) {
        double cur = parseDouble(etBoreDia.getText().toString(), isMetric ? 100.0 : 4.0);
        double min = isMetric ? 20.0 : 1.0;
        double max = isMetric ? 500.0 : 20.0;
        cur = Math.max(min, Math.min(max, cur + delta));
        etBoreDia.setText(isMetric ? String.format(Locale.US, "%.1f", cur) : String.format(Locale.US, "%.2f", cur));
    }

    private void adjustRod(double delta) {
        double cur = parseDouble(etRodDia.getText().toString(), isMetric ? 50.0 : 2.0);
        double min = isMetric ? 10.0 : 0.5;
        double max = isMetric ? 400.0 : 16.0;
        cur = Math.max(min, Math.min(max, cur + delta));
        etRodDia.setText(isMetric ? String.format(Locale.US, "%.1f", cur) : String.format(Locale.US, "%.2f", cur));
    }

    private void adjustPressure(double delta) {
        double cur = parseDouble(etPressure.getText().toString(), isMetric ? 210.0 : 3000.0);
        double min = isMetric ? 10.0 : 100.0;
        double max = isMetric ? 700.0 : 10000.0;
        cur = Math.max(min, Math.min(max, cur + delta));
        etPressure.setText(String.format(Locale.US, "%.0f", cur));
    }

    private void setPressurePreset(double val) {
        etPressure.setText(String.format(Locale.US, "%.0f", val));
        calculateAllTelemetry();
    }

    private void adjustStroke(double delta) {
        double cur = parseDouble(etStrokeLength.getText().toString(), isMetric ? 600.0 : 24.0);
        double min = isMetric ? 25.0 : 1.0;
        double max = isMetric ? 3000.0 : 120.0;
        cur = Math.max(min, Math.min(max, cur + delta));
        etStrokeLength.setText(isMetric ? String.format(Locale.US, "%.0f", cur) : String.format(Locale.US, "%.1f", cur));
    }

    private void adjustFlow(double delta) {
        double cur = parseDouble(etPumpFlow.getText().toString(), isMetric ? 40.0 : 10.0);
        double min = isMetric ? 1.0 : 0.5;
        double max = isMetric ? 500.0 : 150.0;
        cur = Math.max(min, Math.min(max, cur + delta));
        etPumpFlow.setText(String.format(Locale.US, "%.1f", cur));
    }

    // =========================================================================
    // Core Engineering Calculation Engine
    // =========================================================================
    private void calculateAllTelemetry() {
        // 1. Parse Inputs Safely
        double rawBore = parseDouble(etBoreDia != null ? etBoreDia.getText().toString() : "4.0", isMetric ? 100.0 : 4.0);
        double rawRod = parseDouble(etRodDia != null ? etRodDia.getText().toString() : "2.0", isMetric ? 50.0 : 2.0);
        double rawPress = parseDouble(etPressure != null ? etPressure.getText().toString() : "3000", isMetric ? 210.0 : 3000.0);
        double rawStroke = parseDouble(etStrokeLength != null ? etStrokeLength.getText().toString() : "24.0", isMetric ? 600.0 : 24.0);
        double rawFlow = parseDouble(etPumpFlow != null ? etPumpFlow.getText().toString() : "10.0", isMetric ? 40.0 : 10.0);

        // Defensive Guards: Rod cannot be larger than or equal to Bore
        if (rawRod >= rawBore) {
            rawRod = rawBore * 0.5;
        }

        // Canonical Imperial Units for unified internal processing
        double boreInches = isMetric ? (rawBore / 25.4) : rawBore;
        double rodInches = isMetric ? (rawRod / 25.4) : rawRod;
        double pressurePsi = isMetric ? (rawPress * 14.5038) : rawPress;
        double strokeInches = isMetric ? (rawStroke / 25.4) : rawStroke;
        double flowGpm = isMetric ? (rawFlow / 3.78541) : rawFlow;

        if (boreInches <= 0) boreInches = 1.0;
        if (rodInches <= 0) rodInches = 0.5;
        if (pressurePsi <= 0) pressurePsi = 100.0;
        if (strokeInches <= 0) strokeInches = 1.0;
        if (flowGpm <= 0) flowGpm = 1.0;

        // =====================================================================
        // SECTION 1: CYLINDER FORCES & AREAS
        // =====================================================================
        double areaPushSqIn = (Math.PI / 4.0) * Math.pow(boreInches, 2);
        double areaRodSqIn = (Math.PI / 4.0) * Math.pow(rodInches, 2);
        double areaPullSqIn = Math.max(0.01, areaPushSqIn - areaRodSqIn);

        double pushForceLbs = pressurePsi * areaPushSqIn;
        double pushForceUsTons = pushForceLbs / 2000.0;
        double pushForceKn = pushForceLbs * 0.00444822;
        double pushForceMetricTonnes = pushForceKn / 9.80665;

        double pullForceLbs = pressurePsi * areaPullSqIn;
        double pullForceUsTons = pullForceLbs / 2000.0;
        double pullForceKn = pullForceLbs * 0.00444822;
        double pullForceMetricTonnes = pullForceKn / 9.80665;

        double areaRatio = (areaPullSqIn / areaPushSqIn) * 100.0;
        double rodRatio = (areaRodSqIn / areaPushSqIn) * 100.0;

        // Section 1 UI updates
        if (tvForceStatusBadge != null) {
            tvForceStatusBadge.setText(isMetric ?
                    String.format(Locale.US, "FORCE @ %.0f BAR", rawPress) :
                    String.format(Locale.US, "FORCE @ %.0f PSI", rawPress));
        }

        if (tvCylinderSummaryTag != null) {
            tvCylinderSummaryTag.setText(isMetric ?
                    String.format(Locale.US, "Ø%.0f × Ø%.0f mm", rawBore, rawRod) :
                    String.format(Locale.US, "Ø%.2f\" × Ø%.2f\"", boreInches, rodInches));
        }

        if (tvPrimaryPushForce != null) {
            if (!isMetric) {
                tvPrimaryPushForce.setText(String.format(Locale.US, "%.1f US Tons", pushForceUsTons));
            } else {
                tvPrimaryPushForce.setText(String.format(Locale.US, "%.1f Tonnes", pushForceMetricTonnes));
            }
        }

        if (tvSecondaryPushForce != null) {
            if (!isMetric) {
                tvSecondaryPushForce.setText(String.format(Locale.US, "%,.0f lbs force  •  %.1f kN", pushForceLbs, pushForceKn));
            } else {
                tvSecondaryPushForce.setText(String.format(Locale.US, "%.1f kN  •  %,.0f lbs force", pushForceKn, pushForceLbs));
            }
        }

        if (tvPullForce != null) {
            if (!isMetric) {
                tvPullForce.setText(String.format(Locale.US, "%.1f US Tons", pullForceUsTons));
            } else {
                tvPullForce.setText(String.format(Locale.US, "%.1f Tonnes", pullForceMetricTonnes));
            }
        }

        if (tvPullForceAlt != null) {
            if (!isMetric) {
                tvPullForceAlt.setText(String.format(Locale.US, "%,.0f lbs  •  %.1f kN", pullForceLbs, pullForceKn));
            } else {
                tvPullForceAlt.setText(String.format(Locale.US, "%.1f kN  •  %,.0f lbs", pullForceKn, pullForceLbs));
            }
        }

        if (tvAreaPush != null && tvAreaPull != null) {
            if (!isMetric) {
                tvAreaPush.setText(String.format(Locale.US, "Push: %.2f sq in", areaPushSqIn));
                tvAreaPull.setText(String.format(Locale.US, "Pull: %.2f sq in (%.0f%%)", areaPullSqIn, areaRatio));
            } else {
                double areaPushCm2 = areaPushSqIn * 6.4516;
                double areaPullCm2 = areaPullSqIn * 6.4516;
                tvAreaPush.setText(String.format(Locale.US, "Push: %.1f cm²", areaPushCm2));
                tvAreaPull.setText(String.format(Locale.US, "Pull: %.1f cm² (%.0f%%)", areaPullCm2, areaRatio));
            }
        }

        if (tvDifferentialNote != null) {
            tvDifferentialNote.setText(String.format(Locale.US,
                    "Rod diameter consumes %.0f%% of bore face area. Retraction generates %.0f%% of push tonnage but cycles %.2fx faster.",
                    rodRatio, areaRatio, areaPushSqIn / areaPullSqIn));
        }

        // =====================================================================
        // SECTION 2: CYCLE TIME, SPEED & POWER
        // =====================================================================
        double volumeExtendGal = (areaPushSqIn * strokeInches) / 231.0;
        double volumeRetractGal = (areaPullSqIn * strokeInches) / 231.0;

        double timeExtendSec = (volumeExtendGal / flowGpm) * 60.0;
        double timeRetractSec = (volumeRetractGal / flowGpm) * 60.0;
        double totalCycleSec = timeExtendSec + timeRetractSec;

        double speedExtendInSec = strokeInches / (timeExtendSec > 0 ? timeExtendSec : 1.0);
        double speedRetractInSec = strokeInches / (timeRetractSec > 0 ? timeRetractSec : 1.0);

        // Fluid Horsepower = (PSI * GPM) / 1714
        double fluidHp = (pressurePsi * flowGpm) / 1714.0;
        double fluidKw = fluidHp * 0.7457;

        if (tvTotalCycleTime != null) {
            tvTotalCycleTime.setText(String.format(Locale.US, "%.1f Seconds", totalCycleSec));
        }

        if (tvSpeedBreakdown != null) {
            if (!isMetric) {
                tvSpeedBreakdown.setText(String.format(Locale.US,
                        "Extend: %.1fs (%.1f in/s)  •  Retract: %.1fs (%.1f in/s)",
                        timeExtendSec, speedExtendInSec, timeRetractSec, speedRetractInSec));
            } else {
                tvSpeedBreakdown.setText(String.format(Locale.US,
                        "Extend: %.1fs (%.0f mm/s)  •  Retract: %.1fs (%.0f mm/s)",
                        timeExtendSec, speedExtendInSec * 25.4, timeRetractSec, speedRetractInSec * 25.4));
            }
        }

        if (tvFluidHp != null) {
            tvFluidHp.setText(String.format(Locale.US, "%.1f HP", fluidHp));
        }

        if (tvFluidKw != null) {
            tvFluidKw.setText(String.format(Locale.US, "%.1f kW fluid power", fluidKw));
        }

        if (tvVolumeExtend != null && tvVolumeRetract != null) {
            if (!isMetric) {
                tvVolumeExtend.setText(String.format(Locale.US, "Extend: %.2f gal", volumeExtendGal));
                tvVolumeRetract.setText(String.format(Locale.US, "Retract: %.2f gal", volumeRetractGal));
            } else {
                tvVolumeExtend.setText(String.format(Locale.US, "Extend: %.1f L", volumeExtendGal * 3.78541));
                tvVolumeRetract.setText(String.format(Locale.US, "Retract: %.1f L", volumeRetractGal * 3.78541));
            }
        }

        if (tvPowerGuidance != null) {
            double recEngineHp = fluidHp / 0.85; // 85% mechanical pump efficiency
            tvPowerGuidance.setText(String.format(Locale.US,
                    "Allow 15%% pump mechanical loss: Recommend >= %.0f HP engine or %.0f HP electric motor at target operating pressure.",
                    Math.ceil(recEngineHp), Math.ceil(recEngineHp * 0.75)));
        }

        // =====================================================================
        // SECTION 3: SAE J517 HOSE LINE VELOCITY AUDITOR
        // =====================================================================
        HoseSize selectedHose = (HoseSize) spnHoseSize.getSelectedItem();
        if (selectedHose == null && hoseSizes.length > 2) {
            selectedHose = hoseSizes[2]; // -08 (1/2")
        }
        double hoseIdInches = selectedHose != null ? selectedHose.idInches : 0.500;

        // V (ft/s) = (0.4085 * GPM) / (ID_in)^2
        double velocityFtSec = (0.4085 * flowGpm) / Math.pow(hoseIdInches, 2);
        double velocityMSec = velocityFtSec * 0.3048;

        if (tvVelocityFlowTag != null) {
            tvVelocityFlowTag.setText(String.format(Locale.US, "%.1f GPM • %s", flowGpm, selectedHose != null ? selectedHose.dashName.split(" ")[0] : "-08"));
        }

        if (tvPrimaryVelocity != null) {
            tvPrimaryVelocity.setText(String.format(Locale.US, "%.1f ft/s", velocityFtSec));
        }

        if (tvSecondaryVelocity != null) {
            tvSecondaryVelocity.setText(String.format(Locale.US, "%.2f m/s fluid velocity", velocityMSec));
        }

        // SAE J517 Compliance Evaluation
        double maxRecommendedFtSec;
        double maxAllowableFtSec;
        String serviceDesc;

        if (lineServiceMode == 0) { // Working Pressure
            maxRecommendedFtSec = 15.0;
            maxAllowableFtSec = 20.0;
            serviceDesc = "Working Pressure circuit";
            tvRecommendedLimit.setText("10 – 15 ft/s");
        } else if (lineServiceMode == 1) { // Tank Return
            maxRecommendedFtSec = 10.0;
            maxAllowableFtSec = 15.0;
            serviceDesc = "Tank Return circuit";
            tvRecommendedLimit.setText("5 – 10 ft/s");
        } else { // Pump Suction
            maxRecommendedFtSec = 4.0;
            maxAllowableFtSec = 5.0;
            serviceDesc = "Pump Suction / Inlet";
            tvRecommendedLimit.setText("2 – 4 ft/s");
        }
        tvServiceLimitLabel.setText(serviceDesc);

        if (velocityFtSec <= maxRecommendedFtSec) {
            tvComplianceStatus.setText("COMPLIANT");
            tvComplianceStatus.setTextColor(getColor(R.color.badge_safe));
            tvVelocityStatusBadge.setText("OPTIMAL VELOCITY");
            tvVelocityStatusBadge.setTextColor(getColor(R.color.badge_safe));
            tvVelocityStatusBadge.setBackgroundResource(R.drawable.bg_badge_safe);
            tvCavitationRisk.setText("Zero cavitation risk");
            tvVelocityGuidance.setText(String.format(Locale.US,
                    "Velocity is within SAE J517 limits for %s. Laminar fluid flow minimizes oil overheating and friction pressure drops.",
                    serviceDesc.toLowerCase()));
        } else if (velocityFtSec <= maxAllowableFtSec) {
            tvComplianceStatus.setText("CAUTION");
            tvComplianceStatus.setTextColor(getColor(R.color.badge_warning));
            tvVelocityStatusBadge.setText("ELEVATED VELOCITY");
            tvVelocityStatusBadge.setTextColor(getColor(R.color.badge_warning));
            tvVelocityStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            tvCavitationRisk.setText("Mild friction & heat");
            tvVelocityGuidance.setText(String.format(Locale.US,
                    "Velocity exceeds recommended bounds for %s. Consider stepping up to the next larger hose dash size to reduce operating heat.",
                    serviceDesc.toLowerCase()));
        } else {
            tvComplianceStatus.setText("EXCESSIVE");
            tvComplianceStatus.setTextColor(getColor(R.color.badge_critical));
            tvVelocityStatusBadge.setText("HIGH VELOCITY RISK");
            tvVelocityStatusBadge.setTextColor(getColor(R.color.badge_critical));
            tvVelocityStatusBadge.setBackgroundResource(R.drawable.bg_badge_critical);
            tvCavitationRisk.setText(lineServiceMode == 2 ? "HIGH CAVITATION RISK!" : "High turbulence & heat");
            tvVelocityGuidance.setText(lineServiceMode == 2 ?
                    "CRITICAL: Excessive suction velocity causes pump cavitation, rapid vapor bubble collapse, and catastrophic pump pitting. Increase suction hose diameter immediately." :
                    "WARNING: Extreme flow velocity causes severe fluid turbulence, rapid oil breakdown, and premature hydraulic hose blowout. Upsize hose diameter.");
        }
    }

    private double parseDouble(String text, double fallback) {
        if (text == null) return fallback;
        try {
            String cleaned = text.trim().replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) return fallback;
            double val = Double.parseDouble(cleaned);
            if (Double.isNaN(val) || Double.isInfinite(val)) return fallback;
            return val;
        } catch (Exception e) {
            return fallback;
        }
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
                .setTitle("HydraFlow")
                .setMessage("Fluid Power & Cylinder Calculator\nVersion 1.0.0\n\nKimRosil App Studio Portfolio freeware.\n100% Offline, on-device calculation.\n\nPrecise hydraulic push/pull force tonnage, cycle speeds, fluid horsepower, and SAE J517 hose velocity auditing.")
                .setPositiveButton("Close", null)
                .setNeutralButton("Reset Defaults", (dialog, which) -> {
                    isMetric = false;
                    lineServiceMode = 0;
                    etBoreDia.setText("4.00");
                    etRodDia.setText("2.00");
                    etPressure.setText("3000");
                    etStrokeLength.setText("24.0");
                    etPumpFlow.setText("10.0");
                    spnHoseSize.setSelection(2); // -08 (1/2")

                    updateUnitChips();
                    updateLineServiceChips();
                    calculateAllTelemetry();
                    Toast.makeText(MainActivity.this, "Reset to standard heavy equipment specs", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        themeMode = prefs.getInt(KEY_THEME_MODE, 0);
        activeTab = prefs.getInt(KEY_ACTIVE_TAB, 0);
        isMetric = prefs.getBoolean(KEY_IS_METRIC, false);
        lineServiceMode = prefs.getInt(KEY_LINE_SERVICE, 0);
    }

    private void saveState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(KEY_THEME_MODE, themeMode);
        editor.putInt(KEY_ACTIVE_TAB, activeTab);
        editor.putBoolean(KEY_IS_METRIC, isMetric);
        editor.putInt(KEY_LINE_SERVICE, lineServiceMode);
        editor.apply();
    }
}
