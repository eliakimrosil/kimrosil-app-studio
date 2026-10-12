package com.kimrosil.flangematch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "flangematch_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode"; // 0: Auto, 1: Light, 2: Dark
    private static final String PREF_UNIT_MODE = "pref_unit_mode";   // "FTLB" or "NM"

    private FlangeDatabaseHelper dbHelper;
    private FlangeDatabaseHelper.FlangeRecord selectedRecord;

    // Views
    private Button btnThemeToggle;
    private Button btnUnitToggle;
    private Spinner spStandard;
    private Spinner spPressureClass;
    private Spinner spNominalSize;
    private FlangeDiagramView flangePreview;

    private TextView tvHeroSpecTitle;
    private TextView tvHeroBoltSummary;
    private TextView tvBcdValue;
    private TextView tvHoleDiaValue;
    private TextView tvStudSizeValue;
    private TextView tvWrenchSizeValue;
    private TextView tvRecommendedTorque;

    private EditText etCustomTorque;
    private Button btnStartSequencer;

    private LinearLayout llLogHistoryContainer;
    private LinearLayout llEmptyLogs;

    private String currentUnit = "ft-lb";

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
        populateStandardSpinners();
        setupListeners();
        refreshRecentLogs();
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        spStandard = findViewById(R.id.spStandard);
        spPressureClass = findViewById(R.id.spPressureClass);
        spNominalSize = findViewById(R.id.spNominalSize);
        flangePreview = findViewById(R.id.flangePreview);

        tvHeroSpecTitle = findViewById(R.id.tvHeroSpecTitle);
        tvHeroBoltSummary = findViewById(R.id.tvHeroBoltSummary);
        tvBcdValue = findViewById(R.id.tvBcdValue);
        tvHoleDiaValue = findViewById(R.id.tvHoleDiaValue);
        tvStudSizeValue = findViewById(R.id.tvStudSizeValue);
        tvWrenchSizeValue = findViewById(R.id.tvWrenchSizeValue);
        tvRecommendedTorque = findViewById(R.id.tvRecommendedTorque);

        etCustomTorque = findViewById(R.id.etCustomTorque);
        btnStartSequencer = findViewById(R.id.btnStartSequencer);

        llLogHistoryContainer = findViewById(R.id.llLogHistoryContainer);
        llEmptyLogs = findViewById(R.id.llEmptyLogs);
    }

    private void setupThemingAndStatusBar() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);

        if (themeMode == 1) {
            btnThemeToggle.setText("Light Mode");
        } else if (themeMode == 2) {
            btnThemeToggle.setText("Dark Mode");
        } else {
            btnThemeToggle.setText("Auto Mode");
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
        btnUnitToggle.setText(currentUnit.equals("ft-lb") ? "Unit: ft-lb" : "Unit: N·m");

        btnUnitToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentUnit.equals("ft-lb")) {
                currentUnit = "N·m";
            } else {
                currentUnit = "ft-lb";
            }
            prefs.edit().putString(PREF_UNIT_MODE, currentUnit).apply();
            btnUnitToggle.setText(currentUnit.equals("ft-lb") ? "Unit: ft-lb" : "Unit: N·m");
            updateCalculatedSpecs();
        });
    }

    private void populateStandardSpinners() {
        List<String> standards = dbHelper.getDistinctStandards();
        if (standards.isEmpty()) return;

        ArrayAdapter<String> stdAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, standards);
        spStandard.setAdapter(stdAdapter);

        spStandard.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String std = standards.get(position);
                loadClassesForStandard(std);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadClassesForStandard(String standard) {
        List<String> classes = dbHelper.getClassesForStandard(standard);
        if (classes.isEmpty()) return;

        ArrayAdapter<String> classAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, classes);
        spPressureClass.setAdapter(classAdapter);

        spPressureClass.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String pClass = classes.get(position);
                loadSizesForClass(standard, pClass);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadSizesForClass(String standard, String pClass) {
        List<String> sizes = dbHelper.getSizesForStandardAndClass(standard, pClass);
        if (sizes.isEmpty()) return;

        ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sizes);
        spNominalSize.setAdapter(sizeAdapter);

        spNominalSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String size = sizes.get(position);
                selectedRecord = dbHelper.getFlangeSpec(standard, pClass, size);
                updateCalculatedSpecs();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateCalculatedSpecs() {
        if (selectedRecord == null) return;

        tvHeroSpecTitle.setText(selectedRecord.standard + " " + selectedRecord.nominalSize + " " + selectedRecord.pressureClass);
        tvHeroBoltSummary.setText(selectedRecord.boltCount + " BOLTS — BCD " + selectedRecord.bcdInches + "\"");

        tvBcdValue.setText(selectedRecord.bcdInches + " in (" + Math.round(selectedRecord.bcdInches * 25.4 * 10.0) / 10.0 + " mm)");
        tvHoleDiaValue.setText(selectedRecord.holeDiaInches + " in (" + Math.round(selectedRecord.holeDiaInches * 25.4 * 10.0) / 10.0 + " mm)");
        tvStudSizeValue.setText(selectedRecord.studDiameter);
        tvWrenchSizeValue.setText(selectedRecord.wrenchSize);

        double torqueDisplay = selectedRecord.baseTorqueFtLb;
        if (currentUnit.equals("N·m")) {
            torqueDisplay = Math.round(torqueDisplay * 1.355818 * 10.0) / 10.0;
        }

        tvRecommendedTorque.setText(torqueDisplay + " " + currentUnit);
        etCustomTorque.setText(String.valueOf(torqueDisplay));

        // Update preview diagram
        flangePreview.setFlangeConfig(selectedRecord.boltCount, TorquePatternCalculator.getStarSequence(selectedRecord.boltCount));
    }

    private void setupListeners() {
        etCustomTorque.setOnEditorActionListener((v, actionId, event) -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            return true;
        });

        btnStartSequencer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            launchSequencer();
        });
    }

    private void launchSequencer() {
        if (selectedRecord == null) {
            Toast.makeText(this, "Select a valid flange standard and size first", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Invalid torque number format", Toast.LENGTH_SHORT).show();
            return;
        }

        if (torqueValue < 5.0 || torqueValue > 25000.0) {
            Toast.makeText(this, "Torque must be between 5.0 and 25,000 " + currentUnit, Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, TorqueSequencerActivity.class);
        intent.putExtra("EXTRA_STANDARD", selectedRecord.standard);
        intent.putExtra("EXTRA_NPS", selectedRecord.nominalSize);
        intent.putExtra("EXTRA_PRESSURE_CLASS", selectedRecord.pressureClass);
        intent.putExtra("EXTRA_BOLT_COUNT", selectedRecord.boltCount);
        intent.putExtra("EXTRA_TARGET_TORQUE", torqueValue);
        intent.putExtra("EXTRA_UNIT", currentUnit);
        intent.putExtra("EXTRA_STUD_SIZE", selectedRecord.studDiameter);
        intent.putExtra("EXTRA_WRENCH_SIZE", selectedRecord.wrenchSize);
        startActivity(intent);
    }

    private void refreshRecentLogs() {
        llLogHistoryContainer.removeAllViews();
        List<FlangeDatabaseHelper.TorqueLogRecord> logs = dbHelper.getRecentLogs(5);

        if (logs.isEmpty()) {
            llEmptyLogs.setVisibility(View.VISIBLE);
            return;
        }

        llEmptyLogs.setVisibility(View.GONE);
        for (FlangeDatabaseHelper.TorqueLogRecord log : logs) {
            View card = getLayoutInflater().inflate(R.layout.item_torque_log, llLogHistoryContainer, false);
            TextView tvTitle = card.findViewById(R.id.tvLogTitle);
            TextView tvSubtitle = card.findViewById(R.id.tvLogSubtitle);
            TextView tvDate = card.findViewById(R.id.tvLogDate);

            tvTitle.setText(log.standard + " " + log.nominalSize + " (" + log.pressureClass + ")");
            tvSubtitle.setText(log.boltCount + " Bolts — " + log.targetTorque + " " + log.unit + " (" + log.passesCompleted + "/4 Passes)");
            tvDate.setText(log.timestamp);

            llLogHistoryContainer.addView(card);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecentLogs();
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