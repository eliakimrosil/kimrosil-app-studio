package com.kimrosil.weldbead;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
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

    public static final String PREFS_NAME = "weldbead_prefs";
    public static final String KEY_THEME = "theme_mode"; // 0=Auto, 1=Light, 2=Dark
    public static final String KEY_UNIT = "is_metric";
    public static final String KEY_PROCESS = "process_idx";
    public static final String KEY_AMPS = "saved_amps";
    public static final String KEY_VOLTS = "saved_volts";
    public static final String KEY_SPEED = "saved_speed";
    public static final String KEY_RAW_AMPS = "raw_amps";
    public static final String KEY_RAW_VOLTS = "raw_volts";
    public static final String KEY_RAW_SPEED = "raw_speed";

    public static final int PROC_SMAW = 0;
    public static final int PROC_GMAW = 1;
    public static final int PROC_GTAW = 2;
    public static final int PROC_FCAW = 3;
    public static final int PROC_SAW = 4;

    private static final double[] PROCESS_EFFICIENCIES = {0.80, 0.80, 0.60, 0.80, 1.00};
    private static final String[] PROCESS_NAMES = {"SMAW", "GMAW", "GTAW", "FCAW", "SAW"};
    private static final double[] DEPOSITION_FACTORS = {0.020, 0.026, 0.012, 0.028, 0.035}; // lb/hr per amp

    private SharedPreferences prefs;
    private WeldDatabaseHelper dbHelper;

    private int currentProcess = PROC_GMAW;
    private boolean isMetric = false;
    private double currentAmps = 180.0;
    private double currentVolts = 24.0;
    private double currentSpeed = 12.0; // in/min or mm/min

    private String rawAmps = "180";
    private String rawVolts = "24.0";
    private String rawSpeed = "12.0";

    private double calculatedHeatInput = 0.0;
    private double calculatedArcEnergy = 0.0;
    private double calculatedDeposition = 0.0;
    private double calculatedArcPower = 0.0;

    // Views
    private Button btnUnitToggle;
    private ImageButton btnTheme;
    private ImageButton btnSettings;

    private Button btnProcSmaw;
    private Button btnProcGmaw;
    private Button btnProcGtaw;
    private Button btnProcFcaw;
    private Button btnProcSaw;

    private TextView tvAmpsLabel;
    private EditText etAmps;
    private Button btnAmpsMinus10;
    private Button btnAmpsMinus1;
    private Button btnAmpsPlus1;
    private Button btnAmpsPlus10;

    private TextView tvVoltsLabel;
    private EditText etVolts;
    private Button btnVoltsMinus1;
    private Button btnVoltsMinusHalf;
    private Button btnVoltsPlusHalf;
    private Button btnVoltsPlus1;

    private TextView tvSpeedLabel;
    private EditText etSpeed;
    private Button btnSpeedMinus1;
    private Button btnSpeedMinusHalf;
    private Button btnSpeedPlusHalf;
    private Button btnSpeedPlus1;

    private Button btnCalculate;
    private TextView tvHeatInput;
    private TextView tvHeatInputSec;
    private TextView tvStatusBadge;
    private TextView tvDepositionRate;
    private TextView tvDepositionMetric;
    private TextView tvArcPower;
    private TextView tvEfficiency;
    private WeldArcView weldArcView;

    private Button btnSavePass;
    private Button btnCopyLog;
    private Button btnClearLog;
    private LinearLayout layoutRecentList;

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

        dbHelper = new WeldDatabaseHelper(this);

        restoreState(savedInstanceState);
        initViews();
        setupListeners();
        updateProcessChips();
        updateUnitLabels();
        calculateAndRender();
        refreshRecentList();
    }

    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentProcess = savedInstanceState.getInt(KEY_PROCESS, prefs.getInt(KEY_PROCESS, PROC_GMAW));
            isMetric = savedInstanceState.getBoolean(KEY_UNIT, prefs.getBoolean(KEY_UNIT, false));
            rawAmps = savedInstanceState.getString(KEY_RAW_AMPS, prefs.getString(KEY_RAW_AMPS, "180"));
            rawVolts = savedInstanceState.getString(KEY_RAW_VOLTS, prefs.getString(KEY_RAW_VOLTS, "24.0"));
            rawSpeed = savedInstanceState.getString(KEY_RAW_SPEED, prefs.getString(KEY_RAW_SPEED, "12.0"));
        } else {
            currentProcess = prefs.getInt(KEY_PROCESS, PROC_GMAW);
            isMetric = prefs.getBoolean(KEY_UNIT, false);
            rawAmps = prefs.getString(KEY_RAW_AMPS, "180");
            rawVolts = prefs.getString(KEY_RAW_VOLTS, "24.0");
            rawSpeed = prefs.getString(KEY_RAW_SPEED, "12.0");
        }
        currentAmps = parseSafeDouble(rawAmps, 180.0);
        currentVolts = parseSafeDouble(rawVolts, 24.0);
        currentSpeed = parseSafeDouble(rawSpeed, 12.0);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        readInputsDirectly();
        outState.putInt(KEY_PROCESS, currentProcess);
        outState.putBoolean(KEY_UNIT, isMetric);
        outState.putDouble(KEY_AMPS, currentAmps);
        outState.putDouble(KEY_VOLTS, currentVolts);
        outState.putDouble(KEY_SPEED, currentSpeed);
        outState.putString(KEY_RAW_AMPS, rawAmps);
        outState.putString(KEY_RAW_VOLTS, rawVolts);
        outState.putString(KEY_RAW_SPEED, rawSpeed);
    }

    @Override
    protected void onPause() {
        super.onPause();
        readInputsDirectly();
        prefs.edit()
                .putInt(KEY_PROCESS, currentProcess)
                .putBoolean(KEY_UNIT, isMetric)
                .putFloat(KEY_AMPS, (float) currentAmps)
                .putFloat(KEY_VOLTS, (float) currentVolts)
                .putFloat(KEY_SPEED, (float) currentSpeed)
                .putString(KEY_RAW_AMPS, rawAmps)
                .putString(KEY_RAW_VOLTS, rawVolts)
                .putString(KEY_RAW_SPEED, rawSpeed)
                .commit();
    }

    private void initViews() {
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        btnTheme = findViewById(R.id.btnTheme);
        btnSettings = findViewById(R.id.btnSettings);

        btnProcSmaw = findViewById(R.id.btnProcSmaw);
        btnProcGmaw = findViewById(R.id.btnProcGmaw);
        btnProcGtaw = findViewById(R.id.btnProcGtaw);
        btnProcFcaw = findViewById(R.id.btnProcFcaw);
        btnProcSaw = findViewById(R.id.btnProcSaw);

        tvAmpsLabel = findViewById(R.id.tvAmpsLabel);
        etAmps = findViewById(R.id.etAmps);
        btnAmpsMinus10 = findViewById(R.id.btnAmpsMinus10);
        btnAmpsMinus1 = findViewById(R.id.btnAmpsMinus1);
        btnAmpsPlus1 = findViewById(R.id.btnAmpsPlus1);
        btnAmpsPlus10 = findViewById(R.id.btnAmpsPlus10);

        tvVoltsLabel = findViewById(R.id.tvVoltsLabel);
        etVolts = findViewById(R.id.etVolts);
        btnVoltsMinus1 = findViewById(R.id.btnVoltsMinus1);
        btnVoltsMinusHalf = findViewById(R.id.btnVoltsMinusHalf);
        btnVoltsPlusHalf = findViewById(R.id.btnVoltsPlusHalf);
        btnVoltsPlus1 = findViewById(R.id.btnVoltsPlus1);

        tvSpeedLabel = findViewById(R.id.tvSpeedLabel);
        etSpeed = findViewById(R.id.etSpeed);
        btnSpeedMinus1 = findViewById(R.id.btnSpeedMinus1);
        btnSpeedMinusHalf = findViewById(R.id.btnSpeedMinusHalf);
        btnSpeedPlusHalf = findViewById(R.id.btnSpeedPlusHalf);
        btnSpeedPlus1 = findViewById(R.id.btnSpeedPlus1);

        btnCalculate = findViewById(R.id.btnCalculate);
        tvHeatInput = findViewById(R.id.tvHeatInput);
        tvHeatInputSec = findViewById(R.id.tvHeatInputSec);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvDepositionRate = findViewById(R.id.tvDepositionRate);
        tvDepositionMetric = findViewById(R.id.tvDepositionMetric);
        tvArcPower = findViewById(R.id.tvArcPower);
        tvEfficiency = findViewById(R.id.tvEfficiency);
        weldArcView = findViewById(R.id.weldArcView);

        btnSavePass = findViewById(R.id.btnSavePass);
        btnCopyLog = findViewById(R.id.btnCopyLog);
        btnClearLog = findViewById(R.id.btnClearLog);
        layoutRecentList = findViewById(R.id.layoutRecentList);

        setInputValues();
    }

    private void setInputValues() {
        isUpdatingUi = true;
        etAmps.setText(rawAmps);
        etVolts.setText(rawVolts);
        etSpeed.setText(rawSpeed);
        isUpdatingUi = false;
    }

    private void setupListeners() {
        btnUnitToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isMetric = !isMetric;
                // Convert speed between in/min and mm/min
                if (isMetric) {
                    currentSpeed = currentSpeed * 25.4;
                } else {
                    currentSpeed = currentSpeed / 25.4;
                }
                updateUnitLabels();
                setInputValues();
                calculateAndRender();
            }
        });

        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleTheme();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        View.OnClickListener procListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.btnProcSmaw) currentProcess = PROC_SMAW;
                else if (id == R.id.btnProcGmaw) currentProcess = PROC_GMAW;
                else if (id == R.id.btnProcGtaw) currentProcess = PROC_GTAW;
                else if (id == R.id.btnProcFcaw) currentProcess = PROC_FCAW;
                else if (id == R.id.btnProcSaw) currentProcess = PROC_SAW;
                updateProcessChips();
                calculateAndRender();
            }
        };
        btnProcSmaw.setOnClickListener(procListener);
        btnProcGmaw.setOnClickListener(procListener);
        btnProcGtaw.setOnClickListener(procListener);
        btnProcFcaw.setOnClickListener(procListener);
        btnProcSaw.setOnClickListener(procListener);

        // Amperage Steppers
        btnAmpsMinus10.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustAmps(-10.0); }
        });
        btnAmpsMinus1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustAmps(-1.0); }
        });
        btnAmpsPlus1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustAmps(1.0); }
        });
        btnAmpsPlus10.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustAmps(10.0); }
        });

        // Voltage Steppers
        btnVoltsMinus1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustVolts(-1.0); }
        });
        btnVoltsMinusHalf.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustVolts(-0.5); }
        });
        btnVoltsPlusHalf.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustVolts(0.5); }
        });
        btnVoltsPlus1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustVolts(1.0); }
        });

        // Speed Steppers
        btnSpeedMinus1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustSpeed(-1.0); }
        });
        btnSpeedMinusHalf.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustSpeed(-0.5); }
        });
        btnSpeedPlusHalf.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustSpeed(0.5); }
        });
        btnSpeedPlus1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustSpeed(1.0); }
        });

        // TextWatchers for direct manual entry
        etAmps.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingUi) return;
                rawAmps = s.toString();
                currentAmps = parseSafeDouble(rawAmps, currentAmps);
                prefs.edit().putString(KEY_RAW_AMPS, rawAmps).putFloat(KEY_AMPS, (float) currentAmps).commit();
                calculateAndRender();
            }
        });
        etVolts.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingUi) return;
                rawVolts = s.toString();
                currentVolts = parseSafeDouble(rawVolts, currentVolts);
                prefs.edit().putString(KEY_RAW_VOLTS, rawVolts).putFloat(KEY_VOLTS, (float) currentVolts).commit();
                calculateAndRender();
            }
        });
        etSpeed.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingUi) return;
                rawSpeed = s.toString();
                currentSpeed = parseSafeDouble(rawSpeed, currentSpeed);
                prefs.edit().putString(KEY_RAW_SPEED, rawSpeed).putFloat(KEY_SPEED, (float) currentSpeed).commit();
                calculateAndRender();
            }
        });

        btnCalculate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && getCurrentFocus() != null) {
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }
                readInputsDirectly();
                calculateAndRender();
                Toast.makeText(MainActivity.this, "Heat Input Computed", Toast.LENGTH_SHORT).show();
            }
        });

        btnSavePass.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePassRecord();
            }
        });

        btnCopyLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyLogToClipboard();
            }
        });

        btnClearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dbHelper.clearAll();
                refreshRecentList();
                Toast.makeText(MainActivity.this, "Pass history cleared", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void adjustAmps(double delta) {
        readInputsDirectly();
        currentAmps = Math.max(10.0, Math.min(800.0, currentAmps + delta));
        if (currentAmps == (long) currentAmps) {
            rawAmps = String.valueOf((long) currentAmps);
        } else {
            rawAmps = String.format(Locale.US, "%.1f", currentAmps);
        }
        setInputValues();
        calculateAndRender();
    }

    private void adjustVolts(double delta) {
        readInputsDirectly();
        currentVolts = Math.max(8.0, Math.min(60.0, currentVolts + delta));
        if (currentVolts == (long) currentVolts) {
            rawVolts = String.valueOf((long) currentVolts);
        } else {
            rawVolts = String.format(Locale.US, "%.1f", currentVolts);
        }
        setInputValues();
        calculateAndRender();
    }

    private void adjustSpeed(double delta) {
        readInputsDirectly();
        double minSpd = isMetric ? 25.0 : 1.0;
        double maxSpd = isMetric ? 2500.0 : 100.0;
        currentSpeed = Math.max(minSpd, Math.min(maxSpd, currentSpeed + delta));
        if (currentSpeed == (long) currentSpeed) {
            rawSpeed = String.valueOf((long) currentSpeed);
        } else {
            rawSpeed = String.format(Locale.US, "%.1f", currentSpeed);
        }
        setInputValues();
        calculateAndRender();
    }

    private void readInputsDirectly() {
        if (etAmps != null && etAmps.getText() != null) {
            rawAmps = etAmps.getText().toString();
            currentAmps = parseSafeDouble(rawAmps, currentAmps);
        }
        if (etVolts != null && etVolts.getText() != null) {
            rawVolts = etVolts.getText().toString();
            currentVolts = parseSafeDouble(rawVolts, currentVolts);
        }
        if (etSpeed != null && etSpeed.getText() != null) {
            rawSpeed = etSpeed.getText().toString();
            currentSpeed = parseSafeDouble(rawSpeed, currentSpeed);
        }
    }

    private double parseSafeDouble(String str, double fallback) {
        if (str == null) return fallback;
        String clean = str.trim().replace(",", ".");
        if (clean.isEmpty()) return fallback;
        try {
            double v = Double.parseDouble(clean);
            if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
                return fallback;
            }
            return v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void updateProcessChips() {
        Button[] chips = {btnProcSmaw, btnProcGmaw, btnProcGtaw, btnProcFcaw, btnProcSaw};
        for (int i = 0; i < chips.length; i++) {
            if (i == currentProcess) {
                chips[i].setBackgroundResource(R.drawable.bg_chip_selected);
                chips[i].setTextColor(0xFFFFFFFF);
            } else {
                chips[i].setBackgroundResource(R.drawable.bg_chip_unselected);
                chips[i].setTextColor(getResources().getColor(R.color.colorTextPrimary));
            }
        }
    }

    private void updateUnitLabels() {
        if (isMetric) {
            btnUnitToggle.setText(R.string.btn_unit_metric);
            tvSpeedLabel.setText(R.string.label_speed_metric);
        } else {
            btnUnitToggle.setText(R.string.btn_unit_imperial);
            tvSpeedLabel.setText(R.string.label_speed);
        }
    }

    private void calculateAndRender() {
        double efficiency = PROCESS_EFFICIENCIES[currentProcess];
        String procName = PROCESS_NAMES[currentProcess];

        // Safe defense against zero or negative speed
        double safeSpeed = Math.max(0.1, currentSpeed);
        double safeAmps = Math.max(1.0, currentAmps);
        double safeVolts = Math.max(1.0, currentVolts);

        // Arc Power in kW
        calculatedArcPower = (safeAmps * safeVolts) / 1000.0;

        // Travel speed normalized to inches per minute for standard ASME calculation
        double speedInchesPerMin = isMetric ? (safeSpeed / 25.4) : safeSpeed;
        double speedMmPerMin = isMetric ? safeSpeed : (safeSpeed * 25.4);

        // Arc Energy (kJ/in) = (Volts * Amps * 60) / (1000 * TS_in_min)
        double arcEnergyKjIn = (safeVolts * safeAmps * 60.0) / (1000.0 * speedInchesPerMin);

        // Heat Input (ASME IX / AWS D1.1): HI = Arc Energy * Efficiency
        double heatInputKjIn = arcEnergyKjIn * efficiency;
        double heatInputKjMm = heatInputKjIn / 25.4;

        calculatedHeatInput = isMetric ? heatInputKjMm : heatInputKjIn;
        calculatedArcEnergy = arcEnergyKjIn;

        // Deposition Rate
        double depFactor = DEPOSITION_FACTORS[currentProcess];
        double depLbHr = safeAmps * depFactor;
        double depKgHr = depLbHr * 0.453592;
        calculatedDeposition = depLbHr;

        // Render Hero Readout
        if (isMetric) {
            tvHeatInput.setText(String.format(Locale.US, "%.2f kJ/mm", heatInputKjMm));
            tvHeatInputSec.setText(String.format(Locale.US, "%.2f kJ/in | Arc Energy: %.2f kJ/mm", heatInputKjIn, arcEnergyKjIn / 25.4));
        } else {
            tvHeatInput.setText(String.format(Locale.US, "%.2f kJ/in", heatInputKjIn));
            tvHeatInputSec.setText(String.format(Locale.US, "%.2f kJ/mm | Arc Energy: %.2f kJ/in", heatInputKjMm, arcEnergyKjIn));
        }

        // Evaluate Metallurgical HAZ Risk Status
        if (heatInputKjIn < 15.0) {
            tvStatusBadge.setText("RAPID COOLING / HARDNESS HAZ");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_danger);
            tvStatusBadge.setTextColor(getResources().getColor(R.color.colorDanger));
        } else if (heatInputKjIn <= 50.0) {
            tvStatusBadge.setText("OPTIMUM TOUGHNESS WINDOW");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_optimal);
            tvStatusBadge.setTextColor(getResources().getColor(R.color.colorOptimal));
        } else {
            tvStatusBadge.setText("HIGH HEAT INPUT / GRAIN GROWTH");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            tvStatusBadge.setTextColor(getResources().getColor(R.color.colorWarning));
        }

        // Telemetry Grid
        tvDepositionRate.setText(String.format(Locale.US, "%.2f lb/hr", depLbHr));
        tvDepositionMetric.setText(String.format(Locale.US, "%.2f kg/hr", depKgHr));
        tvArcPower.setText(String.format(Locale.US, "%.2f kW", calculatedArcPower));
        tvEfficiency.setText(String.format(Locale.US, "Efficiency: %.0f%% (%s)", efficiency * 100.0, procName));

        // Update interactive bead visualizer
        if (weldArcView != null) {
            weldArcView.updateTelemetry(heatInputKjIn, depLbHr, currentProcess, isMetric);
        }
    }

    private void savePassRecord() {
        readInputsDirectly();
        calculateAndRender();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        String procName = PROCESS_NAMES[currentProcess];
        String unitStr = isMetric ? "kJ/mm" : "kJ/in";
        String statusStr = tvStatusBadge.getText().toString();

        long id = dbHelper.insertPass(
                timestamp,
                procName,
                currentAmps,
                currentVolts,
                currentSpeed,
                calculatedHeatInput,
                unitStr,
                calculatedDeposition,
                statusStr
        );

        if (id > 0) {
            Toast.makeText(this, "Pass #" + id + " saved to local ledger", Toast.LENGTH_SHORT).show();
            refreshRecentList();
        }
    }

    private void refreshRecentList() {
        layoutRecentList.removeAllViews();
        List<WeldDatabaseHelper.WeldRecord> records = dbHelper.getRecentPasses(8);

        if (records.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No weld passes logged yet. Tap 'LOG WELD PASS' to record telemetry.");
            emptyTv.setTextColor(getResources().getColor(R.color.colorTextMuted));
            emptyTv.setTextSize(12f);
            emptyTv.setPadding(8, 12, 8, 12);
            layoutRecentList.addView(emptyTv);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (WeldDatabaseHelper.WeldRecord r : records) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setPadding(14, 10, 14, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 8);
            row.setLayoutParams(lp);

            LinearLayout leftCol = new LinearLayout(this);
            leftCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            leftCol.setLayoutParams(leftLp);

            TextView titleTv = new TextView(this);
            titleTv.setText(String.format(Locale.US, "Pass #%d: %s | %.0f A \u2022 %.1f V", r.id, r.process, r.amps, r.volts));
            titleTv.setTextColor(getResources().getColor(R.color.colorTextPrimary));
            titleTv.setTextSize(13f);
            titleTv.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView subTv = new TextView(this);
            subTv.setText(String.format(Locale.US, "Speed: %.1f | %s | %s", r.speed, r.timestamp, r.status));
            subTv.setTextColor(getResources().getColor(R.color.colorTextSecondary));
            subTv.setTextSize(11f);

            leftCol.addView(titleTv);
            leftCol.addView(subTv);

            TextView hiTv = new TextView(this);
            hiTv.setText(String.format(Locale.US, "%.2f\n%s", r.heatInput, r.unit));
            hiTv.setTextColor(getResources().getColor(R.color.colorPrimary));
            hiTv.setTextSize(14f);
            hiTv.setTypeface(null, android.graphics.Typeface.BOLD);
            hiTv.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);

            row.addView(leftCol);
            row.addView(hiTv);
            layoutRecentList.addView(row);
        }
    }

    private void copyLogToClipboard() {
        List<WeldDatabaseHelper.WeldRecord> records = dbHelper.getRecentPasses(50);
        if (records.isEmpty()) {
            Toast.makeText(this, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("WeldBead Welding Run Schedule (ASME Section IX / AWS D1.1)\n");
        sb.append("=========================================================\n");
        for (WeldDatabaseHelper.WeldRecord r : records) {
            sb.append(String.format(Locale.US, "Pass #%d [%s] Process: %s\n", r.id, r.timestamp, r.process));
            sb.append(String.format(Locale.US, "  Current: %.1f A | Voltage: %.1f V | Speed: %.1f\n", r.amps, r.volts, r.speed));
            sb.append(String.format(Locale.US, "  Heat Input: %.2f %s | Deposition: %.2f lb/hr\n", r.heatInput, r.unit, r.deposition));
            sb.append(String.format(Locale.US, "  Status: %s\n\n", r.status));
        }

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("WeldBead Schedule", sb.toString());
        if (cm != null) {
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Schedule copied to clipboard (" + records.size() + " passes)", Toast.LENGTH_SHORT).show();
        }
    }

    private void cycleTheme() {
        int curTheme = prefs.getInt(KEY_THEME, 0);
        int nextTheme = (curTheme + 1) % 3;
        prefs.edit().putInt(KEY_THEME, nextTheme).apply();
        recreate();
    }

    private void showSettingsDialog() {
        String[] options = {"Auto (System Theme)", "Light Theme", "Dark Studio Theme", "ASME Section IX Reference Guide", "About KimRosil App Studio"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_settings);
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0 || which == 1 || which == 2) {
                    prefs.edit().putInt(KEY_THEME, which).apply();
                    recreate();
                } else if (which == 3) {
                    showAsmeReferenceDialog();
                } else if (which == 4) {
                    showAboutDialog();
                }
            }
        });
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void showAsmeReferenceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("ASME Section IX & AWS D1.1");
        builder.setMessage(
                "HEAT INPUT FORMULA (QW-409.1):\n" +
                "Heat Input (kJ/in) = (V \u00D7 A \u00D7 60 \u00D7 \u03B7) / (1000 \u00D7 TS)\n\n" +
                "THERMAL EFFICIENCY FACTORS (\u03B7):\n" +
                "\u2022 SMAW (Stick): 0.80\n" +
                "\u2022 GMAW / MIG: 0.80\n" +
                "\u2022 GTAW / TIG: 0.60\n" +
                "\u2022 FCAW (Flux-Cored): 0.80\n" +
                "\u2022 SAW (Submerged Arc): 1.00\n\n" +
                "HAZ METALLURGICAL CRITERIA:\n" +
                "\u2022 < 15 kJ/in: Fast cooling, risk of brittle martensite.\n" +
                "\u2022 15 - 50 kJ/in: Optimum impact toughness range.\n" +
                "\u2022 > 50 kJ/in: High heat, risk of grain coarsening & burn-through."
        );
        builder.setPositiveButton("Done", null);
        builder.show();
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("About WeldBead");
        builder.setMessage(
                "WeldBead v1.0.0\n" +
                "Offline Welding Heat Input & Telemetry Suite\n\n" +
                "KimRosil App Studio Company\n" +
                "https://apps.kimrosil.com/weld-bead/\n\n" +
                "100% Offline, Zero Ads, Zero Tracking Freeware."
        );
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int count, int after) {}
    }
}
