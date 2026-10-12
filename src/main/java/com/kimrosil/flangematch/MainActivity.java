package com.kimrosil.flangematch;

import android.app.Activity;
import android.content.Context;
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

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "flangematch_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode"; // 0: Auto, 1: Light, 2: Dark
    private static final String PREF_UNIT_MODE = "pref_unit_mode";   // "ft-lb" or "N·m"

    private FlangeDatabaseHelper dbHelper;
    private FlangeDatabaseHelper.FlangeRecord selectedRecord;

    // Header & Theming Views
    private Button btnUnitFtLb;
    private Button btnUnitNm;
    private Button btnThemeToggle;

    // Step 1: Quick Search & Presets
    private EditText etQuickSearch;
    private Button btnClearSearch;
    private final List<Button> presetChips = new ArrayList<>();

    // Standard Dropdowns
    private Spinner spStandard;
    private Spinner spPressureClass;
    private Spinner spNominalSize;

    // Hero Blueprint Gauges
    private TextView tvHeroSpecTitle;
    private TextView tvHeroBoltSummary;
    private FlangeDiagramView flangePreview;
    private TextView tvBcdValuePrimary;
    private TextView tvBcdValueSecondary;
    private TextView tvHoleDiaValuePrimary;
    private TextView tvHoleDiaValueSecondary;
    private TextView tvStudSizeValuePrimary;
    private TextView tvStudSizeValueSecondary;
    private TextView tvWrenchSizeValuePrimary;
    private TextView tvWrenchSizeValueSecondary;

    // Step 2: Lubricant & Torque Calibration
    private Spinner spLubricant;
    private Spinner spGasketType;
    private TextView tvBaseTorqueValue;
    private TextView tvCalibratedTorqueValue;
    private EditText etCustomTorque;
    private Button btnStartSequencer;

    // Offline Logs
    private Button btnClearLogs;
    private LinearLayout llLogHistoryContainer;
    private LinearLayout llEmptyLogs;

    private String currentUnit = "ft-lb";
    private double currentKFactor = FlangeDatabaseHelper.K_COPPER;
    private boolean isUpdatingSpinners = false;

    private final String[] LUBRICANT_LABELS = new String[]{
            "Copper Anti-Seize (k=0.13 - Standard)",
            "Moly Paste (k=0.10 - High Friction Reduction)",
            "Machine Oil (k=0.15 - Light Viscosity)",
            "Dry / As-Received (k=0.20 - Unlubricated)"
    };

    private final double[] LUBRICANT_K_FACTORS = new double[]{
            FlangeDatabaseHelper.K_COPPER,
            FlangeDatabaseHelper.K_MOLY,
            FlangeDatabaseHelper.K_MACHINE_OIL,
            FlangeDatabaseHelper.K_DRY
    };

    private final String[] GASKET_STYLES = new String[]{
            "Spiral Wound (ASME B16.20 - Graphite)",
            "Ring Type Joint (RTJ Metallic)",
            "Sheet Gasket (PTFE / Compressed Fiber)"
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", Configuration.UI_MODE_NIGHT_NO);
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | systemNight;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new FlangeDatabaseHelper(this);

        initViews();
        setupThemingAndStatusBar();
        setupUnitPreference();
        setupQuickSearchAndPresets();
        setupLubricantAndGasketSpinners();
        populateStandardSpinners();
        setupListeners();
        refreshRecentLogs();

        // Restore saved instance if any
        if (savedInstanceState != null) {
            String savedStd = savedInstanceState.getString("SAVED_STD");
            String savedClass = savedInstanceState.getString("SAVED_CLASS");
            String savedSize = savedInstanceState.getString("SAVED_SIZE");
            if (savedStd != null && savedClass != null && savedSize != null) {
                selectFlangeByValues(savedStd, savedClass, savedSize);
            }
        }
    }

    private void initViews() {
        btnUnitFtLb = findViewById(R.id.btnUnitFtLb);
        btnUnitNm = findViewById(R.id.btnUnitNm);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);

        etQuickSearch = findViewById(R.id.etQuickSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);

        presetChips.clear();
        presetChips.add(findViewById(R.id.chipPreset2_150));
        presetChips.add(findViewById(R.id.chipPreset3_150));
        presetChips.add(findViewById(R.id.chipPreset4_150));
        presetChips.add(findViewById(R.id.chipPreset6_150));
        presetChips.add(findViewById(R.id.chipPreset8_150));
        presetChips.add(findViewById(R.id.chipPreset4_300));
        presetChips.add(findViewById(R.id.chipPreset6_300));
        presetChips.add(findViewById(R.id.chipPresetDN50));

        spStandard = findViewById(R.id.spStandard);
        spPressureClass = findViewById(R.id.spPressureClass);
        spNominalSize = findViewById(R.id.spNominalSize);

        tvHeroSpecTitle = findViewById(R.id.tvHeroSpecTitle);
        tvHeroBoltSummary = findViewById(R.id.tvHeroBoltSummary);
        flangePreview = findViewById(R.id.flangePreview);

        tvBcdValuePrimary = findViewById(R.id.tvBcdValuePrimary);
        tvBcdValueSecondary = findViewById(R.id.tvBcdValueSecondary);
        tvHoleDiaValuePrimary = findViewById(R.id.tvHoleDiaValuePrimary);
        tvHoleDiaValueSecondary = findViewById(R.id.tvHoleDiaValueSecondary);
        tvStudSizeValuePrimary = findViewById(R.id.tvStudSizeValuePrimary);
        tvStudSizeValueSecondary = findViewById(R.id.tvStudSizeValueSecondary);
        tvWrenchSizeValuePrimary = findViewById(R.id.tvWrenchSizeValuePrimary);
        tvWrenchSizeValueSecondary = findViewById(R.id.tvWrenchSizeValueSecondary);

        spLubricant = findViewById(R.id.spLubricant);
        spGasketType = findViewById(R.id.spGasketType);
        tvBaseTorqueValue = findViewById(R.id.tvBaseTorqueValue);
        tvCalibratedTorqueValue = findViewById(R.id.tvCalibratedTorqueValue);
        etCustomTorque = findViewById(R.id.etCustomTorque);
        btnStartSequencer = findViewById(R.id.btnStartSequencer);

        btnClearLogs = findViewById(R.id.btnClearLogs);
        llLogHistoryContainer = findViewById(R.id.llLogHistoryContainer);
        llEmptyLogs = findViewById(R.id.llEmptyLogs);
    }

    private void setupThemingAndStatusBar() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);

        if (themeMode == 1) {
            btnThemeToggle.setText("Light");
        } else if (themeMode == 2) {
            btnThemeToggle.setText("Dark");
        } else {
            btnThemeToggle.setText("Auto");
        }

        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                controller.setSystemBarsAppearance(isNight ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        }
    }

    private void cycleThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int current = prefs.getInt(PREF_THEME_MODE, 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt(PREF_THEME_MODE, next).apply();
        recreate();
    }

    private void setupUnitPreference() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentUnit = prefs.getString(PREF_UNIT_MODE, "ft-lb");
        updateUnitSegmentVisuals();

        btnUnitFtLb.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            currentUnit = "ft-lb";
            prefs.edit().putString(PREF_UNIT_MODE, currentUnit).apply();
            updateUnitSegmentVisuals();
            updateCalculatedSpecs();
        });

        btnUnitNm.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            currentUnit = "N·m";
            prefs.edit().putString(PREF_UNIT_MODE, currentUnit).apply();
            updateUnitSegmentVisuals();
            updateCalculatedSpecs();
        });
    }

    private void updateUnitSegmentVisuals() {
        if ("ft-lb".equals(currentUnit)) {
            btnUnitFtLb.setBackgroundResource(R.drawable.bg_unit_segment_active);
            btnUnitFtLb.setTextColor(getColor(R.color.m3_on_primary));
            btnUnitNm.setBackgroundResource(R.drawable.bg_unit_segment_inactive);
            btnUnitNm.setTextColor(getColor(R.color.m3_on_surface));
        } else {
            btnUnitFtLb.setBackgroundResource(R.drawable.bg_unit_segment_inactive);
            btnUnitFtLb.setTextColor(getColor(R.color.m3_on_surface));
            btnUnitNm.setBackgroundResource(R.drawable.bg_unit_segment_active);
            btnUnitNm.setTextColor(getColor(R.color.m3_on_primary));
        }
    }

    private void setupQuickSearchAndPresets() {
        etQuickSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performQuickSearch(etQuickSearch.getText().toString());
                hideKeyboard(v);
                return true;
            }
            return false;
        });

        etQuickSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 2) {
                    performQuickSearch(s.toString());
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            etQuickSearch.setText("");
            hideKeyboard(v);
        });

        // Setup preset chips
        View.OnClickListener chipListener = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            for (Button chip : presetChips) {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected);
                chip.setTextColor(getColor(R.color.m3_on_surface));
            }
            Button selected = (Button) v;
            selected.setBackgroundResource(R.drawable.bg_chip_selected);
            selected.setTextColor(getColor(R.color.m3_on_primary));

            int id = v.getId();
            if (id == R.id.chipPreset2_150) {
                selectFlangeByValues("ASME B16.5", "150#", "2\"");
            } else if (id == R.id.chipPreset3_150) {
                selectFlangeByValues("ASME B16.5", "150#", "3\"");
            } else if (id == R.id.chipPreset4_150) {
                selectFlangeByValues("ASME B16.5", "150#", "4\"");
            } else if (id == R.id.chipPreset6_150) {
                selectFlangeByValues("ASME B16.5", "150#", "6\"");
            } else if (id == R.id.chipPreset8_150) {
                selectFlangeByValues("ASME B16.5", "150#", "8\"");
            } else if (id == R.id.chipPreset4_300) {
                selectFlangeByValues("ASME B16.5", "300#", "4\"");
            } else if (id == R.id.chipPreset6_300) {
                selectFlangeByValues("ASME B16.5", "300#", "6\"");
            } else if (id == R.id.chipPresetDN50) {
                selectFlangeByValues("DIN EN 1092-1", "PN16", "DN50 (2\")");
            }
        };

        for (Button chip : presetChips) {
            chip.setOnClickListener(chipListener);
        }
    }

    private void performQuickSearch(String query) {
        if (TextUtils.isEmpty(query)) return;
        List<FlangeDatabaseHelper.FlangeRecord> results = dbHelper.searchFlangeSpecs(query);
        if (!results.isEmpty()) {
            FlangeDatabaseHelper.FlangeRecord match = results.get(0);
            selectFlangeByValues(match.standard, match.pressureClass, match.nominalSize);
        }
    }

    private void setupLubricantAndGasketSpinners() {
        ArrayAdapter<String> lubeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, LUBRICANT_LABELS);
        spLubricant.setAdapter(lubeAdapter);
        spLubricant.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentKFactor = LUBRICANT_K_FACTORS[position];
                updateCalculatedSpecs();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ArrayAdapter<String> gasketAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, GASKET_STYLES);
        spGasketType.setAdapter(gasketAdapter);
    }

    private void populateStandardSpinners() {
        List<String> standards = dbHelper.getDistinctStandards();
        if (standards.isEmpty()) return;

        ArrayAdapter<String> stdAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, standards);
        spStandard.setAdapter(stdAdapter);

        spStandard.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) return;
                String std = standards.get(position);
                loadClassesForStandard(std, null, null);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Initial default: ASME B16.5 4" 150#
        selectFlangeByValues("ASME B16.5", "150#", "4\"");
    }

    private void loadClassesForStandard(String standard, String targetClass, String targetSize) {
        List<String> classes = dbHelper.getClassesForStandard(standard);
        if (classes.isEmpty()) return;

        ArrayAdapter<String> classAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, classes);
        spPressureClass.setAdapter(classAdapter);

        int selectedClassIndex = 0;
        if (targetClass != null) {
            int idx = classes.indexOf(targetClass);
            if (idx >= 0) selectedClassIndex = idx;
        }
        spPressureClass.setSelection(selectedClassIndex);

        spPressureClass.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) return;
                String pClass = classes.get(position);
                loadSizesForClass(standard, pClass, null);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        String chosenClass = classes.get(selectedClassIndex);
        loadSizesForClass(standard, chosenClass, targetSize);
    }

    private void loadSizesForClass(String standard, String pClass, String targetSize) {
        List<String> sizes = dbHelper.getSizesForStandardAndClass(standard, pClass);
        if (sizes.isEmpty()) return;

        ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sizes);
        spNominalSize.setAdapter(sizeAdapter);

        int selectedSizeIndex = 0;
        if (targetSize != null) {
            int idx = sizes.indexOf(targetSize);
            if (idx >= 0) selectedSizeIndex = idx;
        }
        spNominalSize.setSelection(selectedSizeIndex);

        spNominalSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) return;
                String size = sizes.get(position);
                selectedRecord = dbHelper.getFlangeSpec(standard, pClass, size);
                updateCalculatedSpecs();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        String chosenSize = sizes.get(selectedSizeIndex);
        selectedRecord = dbHelper.getFlangeSpec(standard, pClass, chosenSize);
        updateCalculatedSpecs();
    }

    private void selectFlangeByValues(String standard, String pClass, String nominalSize) {
        isUpdatingSpinners = true;

        List<String> standards = dbHelper.getDistinctStandards();
        int stdIdx = standards.indexOf(standard);
        if (stdIdx >= 0) {
            spStandard.setSelection(stdIdx);
        }

        List<String> classes = dbHelper.getClassesForStandard(standard);
        ArrayAdapter<String> classAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, classes);
        spPressureClass.setAdapter(classAdapter);
        int classIdx = classes.indexOf(pClass);
        if (classIdx >= 0) {
            spPressureClass.setSelection(classIdx);
        }

        List<String> sizes = dbHelper.getSizesForStandardAndClass(standard, pClass);
        ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sizes);
        spNominalSize.setAdapter(sizeAdapter);
        int sizeIdx = sizes.indexOf(nominalSize);
        if (sizeIdx >= 0) {
            spNominalSize.setSelection(sizeIdx);
        }

        selectedRecord = dbHelper.getFlangeSpec(standard, pClass, nominalSize);
        isUpdatingSpinners = false;
        updateCalculatedSpecs();
    }

    private void updateCalculatedSpecs() {
        if (selectedRecord == null) return;

        tvHeroSpecTitle.setText(selectedRecord.standard + " " + selectedRecord.nominalSize + " (" + selectedRecord.pressureClass + ")");
        tvHeroBoltSummary.setText(selectedRecord.boltCount + " BOLTS — BCD " + selectedRecord.bcdInches + "\"");

        // BCD Stacked Cell
        tvBcdValuePrimary.setText(selectedRecord.bcdInches + " in");
        tvBcdValueSecondary.setText(Math.round(selectedRecord.bcdInches * 25.4 * 10.0) / 10.0 + " mm (Pitch Diameter)");

        // Hole Dia Stacked Cell
        tvHoleDiaValuePrimary.setText(selectedRecord.holeDiaInches + " in");
        tvHoleDiaValueSecondary.setText(Math.round(selectedRecord.holeDiaInches * 25.4 * 10.0) / 10.0 + " mm Hole");

        // Stud Size Stacked Cell
        tvStudSizeValuePrimary.setText(selectedRecord.studDiameter);
        tvStudSizeValueSecondary.setText("Standard UNC Thread");

        // Wrench Socket Stacked Cell
        tvWrenchSizeValuePrimary.setText(selectedRecord.wrenchSize);
        tvWrenchSizeValueSecondary.setText("Heavy Hex Nut Socket");

        // Torque calculations
        double baseTorque = selectedRecord.baseTorqueFtLb;
        double calibratedTorque = TorquePatternCalculator.calculateLubricatedTorque(baseTorque, currentKFactor);

        if ("N·m".equals(currentUnit)) {
            baseTorque = Math.round(baseTorque * 1.355818 * 10.0) / 10.0;
            calibratedTorque = Math.round(calibratedTorque * 1.355818 * 10.0) / 10.0;
        }

        tvBaseTorqueValue.setText(baseTorque + " " + currentUnit);
        tvCalibratedTorqueValue.setText(calibratedTorque + " " + currentUnit);
        etCustomTorque.setText(String.valueOf(calibratedTorque));

        // Update radial blueprint preview
        flangePreview.setFlangeConfig(selectedRecord.boltCount, TorquePatternCalculator.getStarSequence(selectedRecord.boltCount));
    }

    private void setupListeners() {
        etCustomTorque.setOnEditorActionListener((v, actionId, event) -> {
            hideKeyboard(v);
            return true;
        });

        btnStartSequencer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            launchSequencer();
        });

        btnClearLogs.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dbHelper.clearAllLogs();
            refreshRecentLogs();
            Toast.makeText(this, "All session logs cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void launchSequencer() {
        if (selectedRecord == null) {
            Toast.makeText(this, "Select a valid flange standard first", Toast.LENGTH_SHORT).show();
            return;
        }

        String torqueInput = etCustomTorque.getText().toString().trim();
        if (TextUtils.isEmpty(torqueInput)) {
            Toast.makeText(this, "Please enter target torque value", Toast.LENGTH_SHORT).show();
            return;
        }

        double torqueValue;
        try {
            torqueValue = Double.parseDouble(torqueInput);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid numeric torque format", Toast.LENGTH_SHORT).show();
            return;
        }

        if (torqueValue < 5.0 || torqueValue > 25000.0) {
            Toast.makeText(this, "Torque must be between 5.0 and 25,000 " + currentUnit, Toast.LENGTH_LONG).show();
            return;
        }

        String lubeName = spLubricant.getSelectedItem() != null ? spLubricant.getSelectedItem().toString() : "Copper Anti-Seize";
        String gasketName = spGasketType.getSelectedItem() != null ? spGasketType.getSelectedItem().toString() : "Spiral Wound";

        Intent intent = new Intent(this, TorqueSequencerActivity.class);
        intent.putExtra("EXTRA_STANDARD", selectedRecord.standard);
        intent.putExtra("EXTRA_NPS", selectedRecord.nominalSize);
        intent.putExtra("EXTRA_PRESSURE_CLASS", selectedRecord.pressureClass);
        intent.putExtra("EXTRA_BOLT_COUNT", selectedRecord.boltCount);
        intent.putExtra("EXTRA_TARGET_TORQUE", torqueValue);
        intent.putExtra("EXTRA_UNIT", currentUnit);
        intent.putExtra("EXTRA_STUD_SIZE", selectedRecord.studDiameter);
        intent.putExtra("EXTRA_WRENCH_SIZE", selectedRecord.wrenchSize);
        intent.putExtra("EXTRA_LUBRICANT", lubeName);
        intent.putExtra("EXTRA_GASKET", gasketName);
        startActivity(intent);
    }

    private void refreshRecentLogs() {
        llLogHistoryContainer.removeAllViews();
        List<FlangeDatabaseHelper.TorqueLogRecord> logs = dbHelper.getRecentLogs(5);

        if (logs.isEmpty()) {
            llEmptyLogs.setVisibility(View.VISIBLE);
            btnClearLogs.setVisibility(View.GONE);
            return;
        }

        llEmptyLogs.setVisibility(View.GONE);
        btnClearLogs.setVisibility(View.VISIBLE);

        for (FlangeDatabaseHelper.TorqueLogRecord log : logs) {
            View card = getLayoutInflater().inflate(R.layout.item_torque_log, llLogHistoryContainer, false);
            TextView tvTitle = card.findViewById(R.id.tvLogTitle);
            TextView tvSubtitle = card.findViewById(R.id.tvLogSubtitle);
            TextView tvDate = card.findViewById(R.id.tvLogDate);

            tvTitle.setText(log.standard + " " + log.nominalSize + " (" + log.pressureClass + ")");
            tvSubtitle.setText(log.boltCount + " Bolts — " + log.targetTorque + " " + log.unit + " (" + log.passesCompleted + "/4 Passes)");
            tvDate.setText(log.timestamp);

            card.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                shareSingleLog(log);
            });

            llLogHistoryContainer.addView(card);
        }
    }

    private void shareSingleLog(FlangeDatabaseHelper.TorqueLogRecord log) {
        String cert = "=== FLANGEMATCH TORQUE CERTIFICATE ===\n"
                + "Standard: " + log.standard + " " + log.nominalSize + "\n"
                + "Rating: " + log.pressureClass + "\n"
                + "Bolt Count: " + log.boltCount + "\n"
                + "Torque Setting: " + log.targetTorque + " " + log.unit + "\n"
                + "Passes Verified: " + log.passesCompleted + " of 4\n"
                + "Timestamp: " + log.timestamp + "\n"
                + "Status: ASME PCC-1 Verified (Offline)";
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, cert);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share Inspection Record"));
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecentLogs();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedRecord != null) {
            outState.putString("SAVED_STD", selectedRecord.standard);
            outState.putString("SAVED_CLASS", selectedRecord.pressureClass);
            outState.putString("SAVED_SIZE", selectedRecord.nominalSize);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                launchSequencer();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}