package com.kimrosil.solderwick;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.media.AudioManager;
import android.media.ToneGenerator;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "solderwick_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode";
    private static final String PREF_TIP_OFFSET = "pref_tip_offset";

    private ToneGenerator toneGenerator;

    private static final String STATE_ALLOY_INDEX = "state_alloy_index";
    private static final String STATE_LAYERS = "state_layers";
    private static final String STATE_COPPER_OZ = "state_copper_oz";
    private static final String STATE_GROUND_PLANE = "state_ground_plane";
    private static final String STATE_TIP_OFFSET = "state_tip_offset";
    private static final String STATE_FLUX_INDEX = "state_flux_index";
    private static final String STATE_PKG_INDEX = "state_pkg_index";
    private static final String STATE_TIP_GEO_INDEX = "state_tip_geo_index";
    private static final String STATE_WIRE_INDEX = "state_wire_index";
    private static final String STATE_PRESET_ID = "state_preset_id";

    private DatabaseHelper dbHelper;
    private Handler timerHandler;
    private Runnable timerRunnable;

    // View References
    private Button btnThemeToggle;
    private Button btnSavePreset;
    private Button btnQuickPreset;
    private Button btnShareResult;

    private ThermalArcGaugeView thermalGauge;
    private TextView tvTipSetpoint;
    private Button btnTempMinus;
    private Button btnTempPlus;
    private TextView tvLiveChronometer;
    private TextView tvDwellState;

    private Button btnDwellTrigger;
    private Button btnResetDwell;

    // Alloy Chips (5)
    private Button chipAlloy6337;
    private Button chipAlloy6040;
    private Button chipAlloySAC305;
    private Button chipAlloySn42Bi58;
    private Button chipAlloyChipQuik;

    // SMD Package Chips (4)
    private Button chipPkg0402;
    private Button chipPkg0805;
    private Button chipPkgSOIC;
    private Button chipPkgDPAK;

    // Tip Geometry Chips (4)
    private chipTipGeometryViews tipViews;
    private static class chipTipGeometryViews {
        Button chisel;
        Button bevel;
        Button conical;
        Button knife;
    }

    // PCB Substrate Chips
    private Button chipLayer1;
    private Button chipLayer2;
    private Button chipLayer4;
    private Button chipLayer8;

    private Button chipCopper1oz;
    private Button chipCopper2oz;
    private Switch switchGroundPlane;

    // Wire Diameter Chips (4)
    private Button chipWire03;
    private Button chipWire05;
    private Button chipWire08;
    private Button chipWire10;

    // Tip Offset Steppers
    private EditText etTipOffset;
    private Button btnOffsetMinus;
    private Button btnOffsetPlus;

    // Flux Chemistry Deck
    private Button chipFluxRma;
    private Button chipFluxNoClean;
    private Button chipFluxWaterSoluble;

    private TextView tvFluxDesignation;
    private TextView tvFluxActivation;
    private TextView tvFluxSolids;
    private TextView tvFluxCleaning;
    private TextView tvFluxApplication;

    // State Containers & Inspection
    private LinearLayout llEmptyState;
    private LinearLayout llActiveState;
    private LinearLayout llResultCard;
    private TextView tvResultImcSummary;
    private TextView tvResultDwellPrimary;
    private TextView tvResultDwellSub;
    private TextView tvResultTempPrimary;
    private TextView tvResultTempSub;
    private TextView tvResultMetrics;
    private TextView tvJointCountBadge;

    // Live Physics State
    private int selectedAlloyIndex = 0; // 0=63/37, 1=60/40, 2=SAC305, 3=Sn42Bi58, 4=ChipQuik
    private int selectedPkgIndex = 1;   // 0=0402, 1=0805, 2=SOIC, 3=DPAK
    private int selectedTipGeoIndex = 0; // 0=Chisel, 1=Bevel, 2=Conical, 3=Knife
    private int selectedWireIndex = 1;   // 0=0.3mm, 1=0.5mm, 2=0.8mm, 3=1.0mm
    private int selectedLayers = 2;
    private double selectedCopperOz = 1.0;
    private boolean heavyGroundPlane = false;
    private int tipThermalOffset = 0;
    private int selectedFluxIndex = 0;

    private int calculatedTipTemp = 320;
    private int calculatedSafeDwellMs = 2500;

    private boolean isTimerRunning = false;
    private long timerStartTimeMs = 0;
    private int elapsedDwellMs = 0;
    private boolean hasBreachedLimit = false;
    private long currentPresetId = 1;

    private static final ThermalPhysicsEngine.SolderProfile[] ALLOY_PROFILES = {
            ThermalPhysicsEngine.ALLOY_63_37,
            ThermalPhysicsEngine.ALLOY_60_40,
            ThermalPhysicsEngine.ALLOY_SAC305,
            ThermalPhysicsEngine.ALLOY_SN42BI58,
            ThermalPhysicsEngine.ALLOY_CHIPQUIK
    };

    private static final ThermalPhysicsEngine.ComponentPackage[] PKG_PROFILES = {
            ThermalPhysicsEngine.PKG_0402,
            ThermalPhysicsEngine.PKG_0805,
            ThermalPhysicsEngine.PKG_SOIC,
            ThermalPhysicsEngine.PKG_DPAK
    };

    private static final ThermalPhysicsEngine.TipGeometry[] TIP_PROFILES = {
            ThermalPhysicsEngine.TIP_CHISEL,
            ThermalPhysicsEngine.TIP_BEVEL,
            ThermalPhysicsEngine.TIP_CONICAL,
            ThermalPhysicsEngine.TIP_KNIFE
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = sp.getInt(PREF_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark
        int targetNightMode;

        if (mode == 1) {
            targetNightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            targetNightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int sysNight = 0;
            try {
                sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {
            }
            targetNightMode = (sysNight == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | targetNightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        timerHandler = new Handler(Looper.getMainLooper());
        DwellNotificationManager.createNotificationChannel(this);

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
        } catch (Exception ignored) {
        }

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        tipThermalOffset = sp.getInt(PREF_TIP_OFFSET, 0);

        if (savedInstanceState != null) {
            selectedAlloyIndex = savedInstanceState.getInt(STATE_ALLOY_INDEX, 0);
            selectedLayers = savedInstanceState.getInt(STATE_LAYERS, 2);
            selectedCopperOz = savedInstanceState.getDouble(STATE_COPPER_OZ, 1.0);
            heavyGroundPlane = savedInstanceState.getBoolean(STATE_GROUND_PLANE, false);
            tipThermalOffset = savedInstanceState.getInt(STATE_TIP_OFFSET, 0);
            selectedFluxIndex = savedInstanceState.getInt(STATE_FLUX_INDEX, 0);
            selectedPkgIndex = savedInstanceState.getInt(STATE_PKG_INDEX, 1);
            selectedTipGeoIndex = savedInstanceState.getInt(STATE_TIP_GEO_INDEX, 0);
            selectedWireIndex = savedInstanceState.getInt(STATE_WIRE_INDEX, 1);
            currentPresetId = savedInstanceState.getLong(STATE_PRESET_ID, 1);
        }

        bindViews();
        setupStatusBarAppearance();
        setupListeners();
        setupFluxChemistryDeck();
        refreshAllPills();
        recalculateThermalParameters();
        updateThemeButtonLabel();
        updatePresetButtonLabel();
        updateJointLogBadge();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_ALLOY_INDEX, selectedAlloyIndex);
        outState.putInt(STATE_LAYERS, selectedLayers);
        outState.putDouble(STATE_COPPER_OZ, selectedCopperOz);
        outState.putBoolean(STATE_GROUND_PLANE, heavyGroundPlane);
        outState.putInt(STATE_TIP_OFFSET, tipThermalOffset);
        outState.putInt(STATE_FLUX_INDEX, selectedFluxIndex);
        outState.putInt(STATE_PKG_INDEX, selectedPkgIndex);
        outState.putInt(STATE_TIP_GEO_INDEX, selectedTipGeoIndex);
        outState.putInt(STATE_WIRE_INDEX, selectedWireIndex);
        outState.putLong(STATE_PRESET_ID, currentPresetId);
    }

    private void bindViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSavePreset = findViewById(R.id.btnSavePreset);
        btnQuickPreset = findViewById(R.id.btnQuickPreset);
        btnShareResult = findViewById(R.id.btnShareResult);

        thermalGauge = findViewById(R.id.thermalGauge);
        tvTipSetpoint = findViewById(R.id.tvTipSetpoint);
        btnTempMinus = findViewById(R.id.btnTempMinus);
        btnTempPlus = findViewById(R.id.btnTempPlus);
        tvLiveChronometer = findViewById(R.id.tvLiveChronometer);
        tvDwellState = findViewById(R.id.tvDwellState);

        btnDwellTrigger = findViewById(R.id.btnDwellTrigger);
        btnResetDwell = findViewById(R.id.btnResetDwell);

        // Alloy chips
        chipAlloy6337 = findViewById(R.id.chipAlloy6337);
        chipAlloy6040 = findViewById(R.id.chipAlloy6040);
        chipAlloySAC305 = findViewById(R.id.chipAlloySAC305);
        chipAlloySn42Bi58 = findViewById(R.id.chipAlloySn42Bi58);
        chipAlloyChipQuik = findViewById(R.id.chipAlloyChipQuik);

        // SMD Package chips
        chipPkg0402 = findViewById(R.id.chipPkg0402);
        chipPkg0805 = findViewById(R.id.chipPkg0805);
        chipPkgSOIC = findViewById(R.id.chipPkgSOIC);
        chipPkgDPAK = findViewById(R.id.chipPkgDPAK);

        // Tip Geometry chips
        tipViews = new chipTipGeometryViews();
        tipViews.chisel = findViewById(R.id.chipTipChisel);
        tipViews.bevel = findViewById(R.id.chipTipBevel);
        tipViews.conical = findViewById(R.id.chipTipConical);
        tipViews.knife = findViewById(R.id.chipTipKnife);

        // PCB Substrate chips
        chipLayer1 = findViewById(R.id.chipLayer1);
        chipLayer2 = findViewById(R.id.chipLayer2);
        chipLayer4 = findViewById(R.id.chipLayer4);
        chipLayer8 = findViewById(R.id.chipLayer8);

        chipCopper1oz = findViewById(R.id.chipCopper1oz);
        chipCopper2oz = findViewById(R.id.chipCopper2oz);
        switchGroundPlane = findViewById(R.id.switchGroundPlane);
        switchGroundPlane.setChecked(heavyGroundPlane);

        // Wire chips
        chipWire03 = findViewById(R.id.chipWire03);
        chipWire05 = findViewById(R.id.chipWire05);
        chipWire08 = findViewById(R.id.chipWire08);
        chipWire10 = findViewById(R.id.chipWire10);

        // Tip Offset numeric controls
        etTipOffset = findViewById(R.id.etTipOffset);
        btnOffsetMinus = findViewById(R.id.btnOffsetMinus);
        btnOffsetPlus = findViewById(R.id.btnOffsetPlus);
        etTipOffset.setText(String.valueOf(tipThermalOffset));

        // Flux Chemistry chips & card
        chipFluxRma = findViewById(R.id.chipFluxRma);
        chipFluxNoClean = findViewById(R.id.chipFluxNoClean);
        chipFluxWaterSoluble = findViewById(R.id.chipFluxWaterSoluble);

        tvFluxDesignation = findViewById(R.id.tvFluxDesignation);
        tvFluxActivation = findViewById(R.id.tvFluxActivation);
        tvFluxSolids = findViewById(R.id.tvFluxSolids);
        tvFluxCleaning = findViewById(R.id.tvFluxCleaning);
        tvFluxApplication = findViewById(R.id.tvFluxApplication);

        // State Containers & Result Card
        llEmptyState = findViewById(R.id.llEmptyState);
        llActiveState = findViewById(R.id.llActiveState);
        llResultCard = findViewById(R.id.llResultCard);
        tvResultImcSummary = findViewById(R.id.tvResultImcSummary);
        tvResultDwellPrimary = findViewById(R.id.tvResultDwellPrimary);
        tvResultDwellSub = findViewById(R.id.tvResultDwellSub);
        tvResultTempPrimary = findViewById(R.id.tvResultTempPrimary);
        tvResultTempSub = findViewById(R.id.tvResultTempSub);
        tvResultMetrics = findViewById(R.id.tvResultMetrics);
        tvJointCountBadge = findViewById(R.id.tvJointCountBadge);
    }

    private void setupStatusBarAppearance() {
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

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnQuickPreset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showPresetPicker();
        });

        btnSavePreset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showSavePresetDialog();
        });

        btnDwellTrigger.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleDwellChronometer();
        });

        btnResetDwell.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetDwellState();
        });

        // Direct Temperature Stepper Buttons
        btnTempMinus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            applyOffsetDelta(-5);
        });
        btnTempMinus.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            applyOffsetDelta(-10);
            return true;
        });

        btnTempPlus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            applyOffsetDelta(5);
        });
        btnTempPlus.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            applyOffsetDelta(10);
            return true;
        });

        // Alloy chips
        chipAlloy6337.setOnClickListener(v -> selectAlloy(0));
        chipAlloy6040.setOnClickListener(v -> selectAlloy(1));
        chipAlloySAC305.setOnClickListener(v -> selectAlloy(2));
        chipAlloySn42Bi58.setOnClickListener(v -> selectAlloy(3));
        chipAlloyChipQuik.setOnClickListener(v -> selectAlloy(4));

        // SMD Package chips
        chipPkg0402.setOnClickListener(v -> selectPkg(0));
        chipPkg0805.setOnClickListener(v -> selectPkg(1));
        chipPkgSOIC.setOnClickListener(v -> selectPkg(2));
        chipPkgDPAK.setOnClickListener(v -> selectPkg(3));

        // Tip Geometry chips
        tipViews.chisel.setOnClickListener(v -> selectTipGeo(0));
        tipViews.bevel.setOnClickListener(v -> selectTipGeo(1));
        tipViews.conical.setOnClickListener(v -> selectTipGeo(2));
        tipViews.knife.setOnClickListener(v -> selectTipGeo(3));

        // Layer chips
        chipLayer1.setOnClickListener(v -> selectLayers(1));
        chipLayer2.setOnClickListener(v -> selectLayers(2));
        chipLayer4.setOnClickListener(v -> selectLayers(4));
        chipLayer8.setOnClickListener(v -> selectLayers(8));

        // Copper weight chips
        chipCopper1oz.setOnClickListener(v -> selectCopper(1.0));
        chipCopper2oz.setOnClickListener(v -> selectCopper(2.0));

        switchGroundPlane.setOnCheckedChangeListener((buttonView, isChecked) -> {
            heavyGroundPlane = isChecked;
            buttonView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            recalculateThermalParameters();
        });

        // Wire chips
        chipWire03.setOnClickListener(v -> selectWire(0));
        chipWire05.setOnClickListener(v -> selectWire(1));
        chipWire08.setOnClickListener(v -> selectWire(2));
        chipWire10.setOnClickListener(v -> selectWire(3));

        // Tip calibration numeric controls
        btnOffsetMinus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            applyOffsetDelta(-5);
        });
        btnOffsetPlus.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            applyOffsetDelta(5);
        });

        etTipOffset.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                commitTipOffsetFromInput();
                hideKeyboard(v);
                return true;
            }
            return false;
        });

        // Flux chemistry chips
        chipFluxRma.setOnClickListener(v -> selectFlux(0));
        chipFluxNoClean.setOnClickListener(v -> selectFlux(1));
        chipFluxWaterSoluble.setOnClickListener(v -> selectFlux(2));

        // Thermal Gauge touch budget update
        thermalGauge.setOnDwellBudgetChangeListener(newBudgetMs -> {
            calculatedSafeDwellMs = newBudgetMs;
            refreshGaugeUI();
        });

        btnShareResult.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareJointReport();
        });
    }

    private void setChipState(Button chip, boolean isActive) {
        if (chip == null) return;
        if (isActive) {
            chip.setBackgroundResource(R.drawable.chip_active);
            chip.setTextColor(getColor(R.color.m3_on_primary));
        } else {
            chip.setBackgroundResource(R.drawable.chip_inactive);
            chip.setTextColor(getColor(R.color.m3_on_surface));
        }
    }

    private void selectAlloy(int index) {
        if (index >= 0 && index < ALLOY_PROFILES.length) {
            selectedAlloyIndex = index;
            chipAlloy6337.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            refreshAllPills();
            recalculateThermalParameters();
        }
    }

    private void selectPkg(int index) {
        if (index >= 0 && index < PKG_PROFILES.length) {
            selectedPkgIndex = index;
            chipPkg0805.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            refreshAllPills();
            recalculateThermalParameters();
        }
    }

    private void selectTipGeo(int index) {
        if (index >= 0 && index < TIP_PROFILES.length) {
            selectedTipGeoIndex = index;
            tipViews.chisel.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            refreshAllPills();
            recalculateThermalParameters();
        }
    }

    private void selectWire(int index) {
        selectedWireIndex = index;
        chipWire05.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        refreshAllPills();
    }

    private void selectLayers(int layers) {
        selectedLayers = layers;
        chipLayer2.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        refreshAllPills();
        recalculateThermalParameters();
    }

    private void selectCopper(double oz) {
        selectedCopperOz = oz;
        chipCopper1oz.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        refreshAllPills();
        recalculateThermalParameters();
    }

    private void selectFlux(int index) {
        selectedFluxIndex = index;
        chipFluxRma.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        refreshAllPills();
        setupFluxChemistryDeck();
    }

    private void refreshAllPills() {
        // Alloy Pills
        setChipState(chipAlloy6337, selectedAlloyIndex == 0);
        setChipState(chipAlloy6040, selectedAlloyIndex == 1);
        setChipState(chipAlloySAC305, selectedAlloyIndex == 2);
        setChipState(chipAlloySn42Bi58, selectedAlloyIndex == 3);
        setChipState(chipAlloyChipQuik, selectedAlloyIndex == 4);

        // Package Pills
        setChipState(chipPkg0402, selectedPkgIndex == 0);
        setChipState(chipPkg0805, selectedPkgIndex == 1);
        setChipState(chipPkgSOIC, selectedPkgIndex == 2);
        setChipState(chipPkgDPAK, selectedPkgIndex == 3);

        // Tip Geometry Pills
        setChipState(tipViews.chisel, selectedTipGeoIndex == 0);
        setChipState(tipViews.bevel, selectedTipGeoIndex == 1);
        setChipState(tipViews.conical, selectedTipGeoIndex == 2);
        setChipState(tipViews.knife, selectedTipGeoIndex == 3);

        // PCB Layer Pills
        setChipState(chipLayer1, selectedLayers == 1);
        setChipState(chipLayer2, selectedLayers == 2);
        setChipState(chipLayer4, selectedLayers == 4);
        setChipState(chipLayer8, selectedLayers == 8);

        // Copper Weight Pills
        setChipState(chipCopper1oz, selectedCopperOz == 1.0);
        setChipState(chipCopper2oz, selectedCopperOz == 2.0);

        // Wire Diameter Pills
        setChipState(chipWire03, selectedWireIndex == 0);
        setChipState(chipWire05, selectedWireIndex == 1);
        setChipState(chipWire08, selectedWireIndex == 2);
        setChipState(chipWire10, selectedWireIndex == 3);

        // Flux Pills
        setChipState(chipFluxRma, selectedFluxIndex == 0);
        setChipState(chipFluxNoClean, selectedFluxIndex == 1);
        setChipState(chipFluxWaterSoluble, selectedFluxIndex == 2);
    }

    private void applyOffsetDelta(int delta) {
        tipThermalOffset = Math.max(-30, Math.min(30, tipThermalOffset + delta));
        etTipOffset.setText(String.valueOf(tipThermalOffset));
        if (btnTempMinus != null) {
            btnTempMinus.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        persistOffset();
        recalculateThermalParameters();
    }

    private void commitTipOffsetFromInput() {
        String raw = etTipOffset.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            tipThermalOffset = 0;
            etTipOffset.setText("0");
            Toast.makeText(this, "Empty offset. Reset to standard 0°C.", Toast.LENGTH_SHORT).show();
        } else {
            try {
                int parsed = Integer.parseInt(raw);
                if (parsed < -30) {
                    parsed = -30;
                    Toast.makeText(this, "Clamped to minimum -30°C offset", Toast.LENGTH_SHORT).show();
                } else if (parsed > 30) {
                    parsed = 30;
                    Toast.makeText(this, "Clamped to maximum +30°C offset", Toast.LENGTH_SHORT).show();
                }
                tipThermalOffset = parsed;
                etTipOffset.setText(String.valueOf(tipThermalOffset));
            } catch (NumberFormatException e) {
                tipThermalOffset = 0;
                etTipOffset.setText("0");
                Toast.makeText(this, "Invalid offset value. Reset to 0°C.", Toast.LENGTH_SHORT).show();
            }
        }
        persistOffset();
        recalculateThermalParameters();
    }

    private void persistOffset() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_TIP_OFFSET, tipThermalOffset)
                .apply();
    }

    private void recalculateThermalParameters() {
        ThermalPhysicsEngine.SolderProfile alloy = ALLOY_PROFILES[selectedAlloyIndex];
        ThermalPhysicsEngine.ComponentPackage pkg = PKG_PROFILES[selectedPkgIndex];
        ThermalPhysicsEngine.TipGeometry tip = TIP_PROFILES[selectedTipGeoIndex];

        calculatedTipTemp = ThermalPhysicsEngine.calculateRecommendedTipTemp(
                alloy, selectedLayers, selectedCopperOz, heavyGroundPlane, tipThermalOffset, tip.tempOffsetC
        );

        if (!isTimerRunning) {
            calculatedSafeDwellMs = ThermalPhysicsEngine.calculateMaxSafeDwellMs(
                    alloy, selectedLayers, selectedCopperOz, heavyGroundPlane, pkg.maxDwellCapMs
            );
        }

        int tempF = (int) Math.round(calculatedTipTemp * 1.8 + 32);
        tvTipSetpoint.setText(String.format(Locale.US, "%d°C  /  %d°F", calculatedTipTemp, tempF));

        refreshGaugeUI();
    }

    private void refreshGaugeUI() {
        float remainingMs = Math.max(0, calculatedSafeDwellMs - elapsedDwellMs);
        thermalGauge.setParameters(
                isTimerRunning ? remainingMs : calculatedSafeDwellMs,
                calculatedSafeDwellMs,
                calculatedTipTemp,
                isTimerRunning,
                hasBreachedLimit
        );

        // Update Digital Countdown Chronometer display
        if (isTimerRunning) {
            if (hasBreachedLimit) {
                int overrunMs = elapsedDwellMs - calculatedSafeDwellMs;
                tvLiveChronometer.setText(String.format(Locale.US, "+%.2fs OVERHEAT", overrunMs / 1000f));
                tvLiveChronometer.setTextColor(0xFFEF4444);
            } else {
                tvLiveChronometer.setText(String.format(Locale.US, "%.2fs", remainingMs / 1000f));
                tvLiveChronometer.setTextColor(0xFFF59E0B);
            }
        } else {
            tvLiveChronometer.setText(String.format(Locale.US, "%.2fs", calculatedSafeDwellMs / 1000f));
            tvLiveChronometer.setTextColor(getColor(R.color.m3_primary));
        }
    }

    private void toggleDwellChronometer() {
        if (!isTimerRunning) {
            startDwellChronometer();
        } else {
            completeJointDwell();
        }
    }

    private void startDwellChronometer() {
        isTimerRunning = true;
        hasBreachedLimit = false;
        elapsedDwellMs = 0;
        timerStartTimeMs = System.currentTimeMillis();

        btnDwellTrigger.setText("RELEASE JOINT (COMPLETE)");
        btnDwellTrigger.setBackgroundResource(R.drawable.btn_m3_primary);
        tvDwellState.setText("ACTIVE CONTACT: COUNTDOWN TICKING");
        tvDwellState.setTextColor(0xFFF59E0B);

        llEmptyState.setVisibility(View.GONE);
        llResultCard.setVisibility(View.GONE);
        llActiveState.setVisibility(View.VISIBLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            } else {
                DwellNotificationManager.showLiveChronometer(this, calculatedSafeDwellMs, calculatedTipTemp);
            }
        } else {
            DwellNotificationManager.showLiveChronometer(this, calculatedSafeDwellMs, calculatedTipTemp);
        }

        timerRunnable = new Runnable() {
            int lastSecondTick = 0;

            @Override
            public void run() {
                if (!isTimerRunning) return;

                long now = System.currentTimeMillis();
                elapsedDwellMs = (int) (now - timerStartTimeMs);

                int currentSecond = elapsedDwellMs / 1000;
                if (currentSecond > lastSecondTick) {
                    lastSecondTick = currentSecond;
                    thermalGauge.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }

                if (elapsedDwellMs >= calculatedSafeDwellMs && !hasBreachedLimit) {
                    hasBreachedLimit = true;
                    thermalGauge.performHapticFeedback(HapticFeedbackConstants.REJECT);
                    if (toneGenerator != null) {
                        try {
                            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 250);
                        } catch (Exception ignored) {}
                    }
                    tvDwellState.setText("OVERHEAT BREACH: RELEASE SOLDERING IRON NOW!");
                    tvDwellState.setTextColor(0xFFEF4444);
                }

                refreshGaugeUI();
                timerHandler.postDelayed(this, 30);
            }
        };

        timerHandler.post(timerRunnable);
    }

    private void completeJointDwell() {
        isTimerRunning = false;
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        DwellNotificationManager.cancelNotification(this);

        if (hasBreachedLimit) {
            thermalGauge.performHapticFeedback(HapticFeedbackConstants.REJECT);
            if (toneGenerator != null) {
                try {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 250);
                } catch (Exception ignored) {}
            }
        } else {
            thermalGauge.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            if (toneGenerator != null) {
                try {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                } catch (Exception ignored) {}
            }
        }

        btnDwellTrigger.setText("START JOINT DWELL");
        btnDwellTrigger.setBackgroundResource(R.drawable.btn_m3_primary);
        tvDwellState.setText("JOINT COMPLETED & PERSISTED");
        tvDwellState.setTextColor(hasBreachedLimit ? 0xFFEF4444 : 0xFF22C55E);

        // Record into SQLite
        dbHelper.insertLog(currentPresetId, elapsedDwellMs, hasBreachedLimit);
        updateJointLogBadge();

        // Show Result card
        showResultSummary();
    }

    private void resetDwellState() {
        isTimerRunning = false;
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        DwellNotificationManager.cancelNotification(this);

        elapsedDwellMs = 0;
        hasBreachedLimit = false;

        btnDwellTrigger.setText("START JOINT DWELL");
        btnDwellTrigger.setBackgroundResource(R.drawable.btn_m3_primary);
        tvDwellState.setText("BENCH PROFILE READY");
        tvDwellState.setTextColor(getColor(R.color.m3_on_surface_variant));

        llActiveState.setVisibility(View.GONE);
        llResultCard.setVisibility(View.GONE);
        llEmptyState.setVisibility(View.VISIBLE);

        recalculateThermalParameters();
    }

    private void showResultSummary() {
        llActiveState.setVisibility(View.GONE);
        llResultCard.setVisibility(View.VISIBLE);

        String imcText = ThermalPhysicsEngine.assessJointBond(elapsedDwellMs, calculatedSafeDwellMs);
        tvResultImcSummary.setText(imcText);
        if (hasBreachedLimit) {
            tvResultImcSummary.setTextColor(0xFFEF4444);
        } else {
            tvResultImcSummary.setTextColor(0xFF10B981);
        }

        // Tabular Telemetry
        tvResultDwellPrimary.setText(String.format(Locale.US, "%.2fs", elapsedDwellMs / 1000f));
        tvResultDwellSub.setText(String.format(Locale.US, "Actual Dwell (Budget %.2fs)", calculatedSafeDwellMs / 1000f));

        int tempF = (int) Math.round(calculatedTipTemp * 1.8 + 32);
        tvResultTempPrimary.setText(String.format(Locale.US, "%d°C", calculatedTipTemp));
        tvResultTempSub.setText(String.format(Locale.US, "Tip Setpoint (%d°F)", tempF));

        ThermalPhysicsEngine.SolderProfile alloy = ALLOY_PROFILES[selectedAlloyIndex];
        ThermalPhysicsEngine.ComponentPackage pkg = PKG_PROFILES[selectedPkgIndex];
        ThermalPhysicsEngine.TipGeometry tip = TIP_PROFILES[selectedTipGeoIndex];

        String metrics = String.format(
                Locale.US,
                "Stackup: %dL %.1foz Cu %s | Alloy: %s | Pkg: %s | Tip: %s",
                selectedLayers,
                selectedCopperOz,
                heavyGroundPlane ? "(GND Plane)" : "",
                alloy.alloyName,
                pkg.name,
                tip.name
        );
        tvResultMetrics.setText(metrics);
    }

    private void shareJointReport() {
        String report = "=== SolderWick Joint Telemetry Report ===\n" +
                tvResultImcSummary.getText() + "\n" +
                "Actual Dwell: " + tvResultDwellPrimary.getText() + " / " + tvResultDwellSub.getText() + "\n" +
                "Tip Temperature: " + tvResultTempPrimary.getText() + " (" + tvResultTempSub.getText() + ")\n" +
                tvResultMetrics.getText() + "\n" +
                "Offline verified via SolderWick Android HUD";

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Solder Joint Quality Report");
        sendIntent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(sendIntent, "Share Joint Inspection Report"));
    }

    private void setupFluxChemistryDeck() {
        FluxChemistryDatabase.FluxSpec spec = FluxChemistryDatabase.getSpecByType(selectedFluxIndex);
        tvFluxDesignation.setText(spec.designation + " (" + spec.name + ")");
        tvFluxActivation.setText("Activation Window: " + spec.activationTempRange);
        tvFluxSolids.setText("Solids Content: " + spec.solidsPercentage);
        tvFluxCleaning.setText("Residue Cleanliness: " + spec.cleaningProtocol);
        tvFluxApplication.setText("Recommended Application: " + spec.typicalUse);
    }

    private void updateJointLogBadge() {
        int count = dbHelper.getLogCount();
        tvJointCountBadge.setText("Joints Logged: " + count);
    }

    private void updatePresetButtonLabel() {
        List<DatabaseHelper.Preset> presets = dbHelper.getAllPresets();
        for (DatabaseHelper.Preset p : presets) {
            if (p.id == currentPresetId) {
                btnQuickPreset.setText("Profile: " + p.name);
                return;
            }
        }
        btnQuickPreset.setText("Profile: Custom Preset");
    }

    private void showPresetPicker() {
        List<DatabaseHelper.Preset> presets = dbHelper.getAllPresets();
        if (presets.isEmpty()) {
            Toast.makeText(this, "No saved presets found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            names[i] = presets.get(i).name;
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Bench Soldering Preset")
                .setItems(names, (dialog, which) -> {
                    DatabaseHelper.Preset p = presets.get(which);
                    applyPreset(p);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyPreset(DatabaseHelper.Preset p) {
        currentPresetId = p.id;
        if (p.alloyType.contains("63/37")) {
            selectedAlloyIndex = 0;
        } else if (p.alloyType.contains("60/40")) {
            selectedAlloyIndex = 1;
        } else if (p.alloyType.contains("SAC305")) {
            selectedAlloyIndex = 2;
        } else if (p.alloyType.contains("ChipQuik")) {
            selectedAlloyIndex = 4;
        } else {
            selectedAlloyIndex = 3;
        }

        selectedLayers = p.boardLayers;
        selectedCopperOz = p.copperOz;
        calculatedSafeDwellMs = p.maxDwellMs;

        refreshAllPills();
        updatePresetButtonLabel();
        recalculateThermalParameters();
        Toast.makeText(this, "Loaded: " + p.name, Toast.LENGTH_SHORT).show();
    }

    private void showSavePresetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Custom Bench Preset");

        final EditText input = new EditText(this);
        input.setHint("Preset Name (e.g., 6L QFP Rework)");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                name = "Bench Profile " + (dbHelper.getAllPresets().size() + 1);
            }
            ThermalPhysicsEngine.SolderProfile alloy = ALLOY_PROFILES[selectedAlloyIndex];
            long id = dbHelper.insertPreset(
                    name,
                    alloy.alloyName,
                    selectedLayers,
                    selectedCopperOz,
                    calculatedTipTemp,
                    calculatedSafeDwellMs
            );
            currentPresetId = id;
            updatePresetButtonLabel();
            Toast.makeText(this, "Saved profile: " + name, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void cycleThemeMode() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int current = sp.getInt(PREF_THEME_MODE, 0);
        int next = (current + 1) % 3; // 0 (Auto) -> 1 (Light) -> 2 (Dark) -> 0
        sp.edit().putInt(PREF_THEME_MODE, next).apply();
        recreate();
    }

    private void updateThemeButtonLabel() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = sp.getInt(PREF_THEME_MODE, 0);
        if (mode == 1) {
            btnThemeToggle.setText("Theme: Light");
        } else if (mode == 2) {
            btnThemeToggle.setText("Theme: Dark");
        } else {
            btnThemeToggle.setText("Theme: Auto");
        }
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                btnDwellTrigger.performClick();
                return true;
            } else if (code == KeyEvent.KEYCODE_SPACE) {
                btnDwellTrigger.performClick();
                return true;
            } else if (code == KeyEvent.KEYCODE_ESCAPE) {
                btnResetDwell.performClick();
                return true;
            } else if (code == KeyEvent.KEYCODE_PLUS || code == KeyEvent.KEYCODE_EQUALS) {
                applyOffsetDelta(5);
                return true;
            } else if (code == KeyEvent.KEYCODE_MINUS) {
                applyOffsetDelta(-5);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshGaugeUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        DwellNotificationManager.cancelNotification(this);
        if (toneGenerator != null) {
            try {
                toneGenerator.release();
            } catch (Exception ignored) {
            }
            toneGenerator = null;
        }
    }
}