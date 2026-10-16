package com.kimrosil.lashgage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREF_NAME = "lashgage_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode";
    private static final String KEY_UNIT_INCH = "pref_unit_inch";

    private int mThemeMode = 0; // 0=Auto, 1=Light, 2=Dark
    private boolean mIsInch = true;
    private int mActiveTab = 0; // 0=Gear, 1=Valve, 2=Runout

    private LashEngine.PressureAngle mCurrentPa = LashEngine.PressureAngle.PA_20_0;
    private LashEngine.Metallurgy mCurrentMetallurgy = LashEngine.Metallurgy.MIXED_IRON_ALUM;
    private boolean mIsAlumPushrods = false;

    private DatabaseHelper mDbHelper;

    // Header controls
    private TextView tvAppTitle;
    private Button btnPatterns;
    private Button btnLogs;
    private Button btnThemeToggle;

    // Tabs
    private Button btnTabGear;
    private Button btnTabValve;
    private Button btnTabTir;

    // Visual Deck
    private LashDialView lashDialView;

    // Decks
    private LinearLayout llGearDeck;
    private LinearLayout llValveDeck;
    private LinearLayout llRunoutDeck;

    // Gear Deck Controls
    private Button btnPa14;
    private Button btnPa20;
    private Button btnPa25;
    private EditText etMeasuredBacklash;
    private Button btnUnitToggle;
    private EditText etTargetMin;
    private EditText etTargetMax;
    private Button btnComputeGear;
    private TextView tvGearStatusBadge;
    private TextView tvGearAction;
    private TextView tvMeasuredPrimary;
    private TextView tvMeasuredSecondary;
    private TextView tvShimPrimary;
    private TextView tvShimSecondary;

    // Valve Deck Controls
    private Button btnMatIronIron;
    private Button btnMatIronAlum;
    private Button btnMatAlumAlum;
    private Button btnPushrodToggle;
    private EditText etHotLashSpec;
    private Button btnComputeValve;
    private TextView tvColdTargetPrimary;
    private TextView tvColdTargetSecondary;
    private TextView tvFeelerGo;
    private TextView tvFeelerNoGo;
    private TextView tvValveThermalNote;

    // Runout Deck Controls
    private EditText etRunout1;
    private EditText etRunout2;
    private EditText etRunout3;
    private EditText etRunout4;
    private Button btnComputeRunout;
    private TextView tvRunoutResult;

    // Save Log
    private Button btnSaveRecord;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mThemeMode = prefs.getInt(KEY_THEME_MODE, 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mThemeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (mThemeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (mThemeMode == 1) {
            setTheme(R.style.AppTheme_Light);
        } else if (mThemeMode == 2) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDbHelper = new DatabaseHelper(this);
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mIsInch = prefs.getBoolean(KEY_UNIT_INCH, true);
        mActiveTab = 0; // Always start on primary gear tab

        bindViews();
        setupListeners();

        // Restore values if available
        if (savedInstanceState != null) {
            mActiveTab = savedInstanceState.getInt("saved_tab", 0);
            mIsInch = savedInstanceState.getBoolean("saved_unit", mIsInch);
            String savedMeasured = savedInstanceState.getString("saved_measured");
            if (!TextUtils.isEmpty(savedMeasured)) {
                etMeasuredBacklash.setText(savedMeasured);
            }
            String savedTargetMin = savedInstanceState.getString("saved_target_min");
            if (!TextUtils.isEmpty(savedTargetMin)) {
                etTargetMin.setText(savedTargetMin);
            }
            String savedTargetMax = savedInstanceState.getString("saved_target_max");
            if (!TextUtils.isEmpty(savedTargetMax)) {
                etTargetMax.setText(savedTargetMax);
            }
            String savedHot = savedInstanceState.getString("saved_hot");
            if (!TextUtils.isEmpty(savedHot)) {
                etHotLashSpec.setText(savedHot);
            }
            String sR1 = savedInstanceState.getString("saved_runout_1");
            if (!TextUtils.isEmpty(sR1)) etRunout1.setText(sR1);
            String sR2 = savedInstanceState.getString("saved_runout_2");
            if (!TextUtils.isEmpty(sR2)) etRunout2.setText(sR2);
            String sR3 = savedInstanceState.getString("saved_runout_3");
            if (!TextUtils.isEmpty(sR3)) etRunout3.setText(sR3);
            String sR4 = savedInstanceState.getString("saved_runout_4");
            if (!TextUtils.isEmpty(sR4)) etRunout4.setText(sR4);
        }

        switchTab(mActiveTab);
        updatePaSelection(mCurrentPa);
        updateMetallurgySelection(mCurrentMetallurgy);
        updateUnitToggleText();
        updateThemeUI();

        // Initial compute
        computeGearBacklash();
        computeValveLash();
    }

    private void bindViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle);
        btnPatterns = findViewById(R.id.btnPatterns);
        btnLogs = findViewById(R.id.btnLogs);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);

        btnTabGear = findViewById(R.id.btnTabGear);
        btnTabValve = findViewById(R.id.btnTabValve);
        btnTabTir = findViewById(R.id.btnTabTir);

        lashDialView = findViewById(R.id.lashDialView);

        llGearDeck = findViewById(R.id.llGearDeck);
        llValveDeck = findViewById(R.id.llValveDeck);
        llRunoutDeck = findViewById(R.id.llRunoutDeck);

        btnPa14 = findViewById(R.id.btnPa14);
        btnPa20 = findViewById(R.id.btnPa20);
        btnPa25 = findViewById(R.id.btnPa25);
        etMeasuredBacklash = findViewById(R.id.etMeasuredBacklash);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        etTargetMin = findViewById(R.id.etTargetMin);
        etTargetMax = findViewById(R.id.etTargetMax);
        btnComputeGear = findViewById(R.id.btnComputeGear);
        tvGearStatusBadge = findViewById(R.id.tvGearStatusBadge);
        tvGearAction = findViewById(R.id.tvGearAction);
        tvMeasuredPrimary = findViewById(R.id.tvMeasuredPrimary);
        tvMeasuredSecondary = findViewById(R.id.tvMeasuredSecondary);
        tvShimPrimary = findViewById(R.id.tvShimPrimary);
        tvShimSecondary = findViewById(R.id.tvShimSecondary);

        btnMatIronIron = findViewById(R.id.btnMatIronIron);
        btnMatIronAlum = findViewById(R.id.btnMatIronAlum);
        btnMatAlumAlum = findViewById(R.id.btnMatAlumAlum);
        btnPushrodToggle = findViewById(R.id.btnPushrodToggle);
        etHotLashSpec = findViewById(R.id.etHotLashSpec);
        btnComputeValve = findViewById(R.id.btnComputeValve);
        tvColdTargetPrimary = findViewById(R.id.tvColdTargetPrimary);
        tvColdTargetSecondary = findViewById(R.id.tvColdTargetSecondary);
        tvFeelerGo = findViewById(R.id.tvFeelerGo);
        tvFeelerNoGo = findViewById(R.id.tvFeelerNoGo);
        tvValveThermalNote = findViewById(R.id.tvValveThermalNote);

        etRunout1 = findViewById(R.id.etRunout1);
        etRunout2 = findViewById(R.id.etRunout2);
        etRunout3 = findViewById(R.id.etRunout3);
        etRunout4 = findViewById(R.id.etRunout4);
        btnComputeRunout = findViewById(R.id.btnComputeRunout);
        tvRunoutResult = findViewById(R.id.tvRunoutResult);

        btnSaveRecord = findViewById(R.id.btnSaveRecord);
    }

    private void updateThemeUI() {
        if (mThemeMode == 0) {
            btnThemeToggle.setText("Auto");
        } else if (mThemeMode == 1) {
            btnThemeToggle.setText("Light");
        } else {
            btnThemeToggle.setText("Dark");
        }

        boolean isDark;
        if (mThemeMode == 1) {
            isDark = false;
        } else if (mThemeMode == 2) {
            isDark = true;
        } else {
            isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }
        lashDialView.setThemeMode(isDark);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        isDark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        }
    }

    private void cycleTheme() {
        mThemeMode = (mThemeMode + 1) % 3;
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME_MODE, mThemeMode).apply();
        recreate();
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                cycleTheme();
            }
        });

        btnPatterns.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, DetailActivity.class);
                startActivity(i);
            }
        });

        btnLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogsDialog();
            }
        });

        // Tabs
        btnTabGear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(0);
            }
        });

        btnTabValve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(1);
            }
        });

        btnTabTir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(2);
            }
        });

        // Gear Pressure Angle selection
        btnPa14.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updatePaSelection(LashEngine.PressureAngle.PA_14_5);
                computeGearBacklash();
            }
        });

        btnPa20.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updatePaSelection(LashEngine.PressureAngle.PA_20_0);
                computeGearBacklash();
            }
        });

        btnPa25.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updatePaSelection(LashEngine.PressureAngle.PA_25_0);
                computeGearBacklash();
            }
        });

        // Unit toggle
        btnUnitToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsInch = !mIsInch;
                getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_UNIT_INCH, mIsInch).apply();
                updateUnitToggleText();

                double currentMeas = parseDoubleDefensive(etMeasuredBacklash.getText().toString(), mIsInch ? 0.203 : 0.0080);
                if (mIsInch) {
                    etMeasuredBacklash.setText(String.format(Locale.US, "%.4f", currentMeas / 25.4));
                    etTargetMin.setText("0.0060");
                    etTargetMax.setText("0.0100");
                } else {
                    etMeasuredBacklash.setText(String.format(Locale.US, "%.3f", currentMeas * 25.4));
                    etTargetMin.setText("0.150");
                    etTargetMax.setText("0.250");
                }

                computeGearBacklash();
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        });

        btnComputeGear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                computeGearBacklash();
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            }
        });

        // Valve metallurgy
        btnMatIronIron.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateMetallurgySelection(LashEngine.Metallurgy.ALL_IRON);
                computeValveLash();
            }
        });

        btnMatIronAlum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateMetallurgySelection(LashEngine.Metallurgy.MIXED_IRON_ALUM);
                computeValveLash();
            }
        });

        btnMatAlumAlum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateMetallurgySelection(LashEngine.Metallurgy.ALL_ALUMINUM);
                computeValveLash();
            }
        });

        btnPushrodToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsAlumPushrods = !mIsAlumPushrods;
                btnPushrodToggle.setText(mIsAlumPushrods
                        ? "Pushrod: Aluminum Alloy"
                        : "Pushrod: Chromoly / Steel");
                computeValveLash();
            }
        });

        btnComputeValve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                computeValveLash();
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            }
        });

        btnComputeRunout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                computeRunoutTir();
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            }
        });

        btnSaveRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentRecord();
            }
        });

        lashDialView.setOnLashChangedListener(new LashDialView.OnLashChangedListener() {
            @Override
            public void onLashChanged(double lashValue, boolean isInch) {
                if (mActiveTab == 0) {
                    etMeasuredBacklash.setText(isInch
                            ? String.format(Locale.US, "%.4f", lashValue)
                            : String.format(Locale.US, "%.3f", lashValue));
                    computeGearBacklash();
                }
            }
        });
    }

    private void updateUnitToggleText() {
        btnUnitToggle.setText(mIsInch ? getString(R.string.btn_unit_in) : getString(R.string.btn_unit_mm));
    }

    private void switchTab(int tab) {
        mActiveTab = tab;

        btnTabGear.setBackgroundResource(tab == 0 ? R.drawable.chip_selected : R.drawable.chip_bg);
        btnTabGear.setTextColor(getResources().getColor(tab == 0 ? R.color.chip_selected_text : R.color.chip_unselected_text));

        btnTabValve.setBackgroundResource(tab == 1 ? R.drawable.chip_selected : R.drawable.chip_bg);
        btnTabValve.setTextColor(getResources().getColor(tab == 1 ? R.color.chip_selected_text : R.color.chip_unselected_text));

        btnTabTir.setBackgroundResource(tab == 2 ? R.drawable.chip_selected : R.drawable.chip_bg);
        btnTabTir.setTextColor(getResources().getColor(tab == 2 ? R.color.chip_selected_text : R.color.chip_unselected_text));

        llGearDeck.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        llValveDeck.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        llRunoutDeck.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);

        if (tab == 0) {
            computeGearBacklash();
        } else if (tab == 1) {
            computeValveLash();
        }
    }

    private void updatePaSelection(LashEngine.PressureAngle pa) {
        mCurrentPa = pa;
        btnPa14.setBackgroundResource(pa == LashEngine.PressureAngle.PA_14_5 ? R.drawable.chip_selected : R.drawable.chip_bg);
        btnPa14.setTextColor(getResources().getColor(pa == LashEngine.PressureAngle.PA_14_5 ? R.color.chip_selected_text : R.color.chip_unselected_text));

        btnPa20.setBackgroundResource(pa == LashEngine.PressureAngle.PA_20_0 ? R.drawable.chip_selected : R.drawable.chip_bg);
        btnPa20.setTextColor(getResources().getColor(pa == LashEngine.PressureAngle.PA_20_0 ? R.color.chip_selected_text : R.color.chip_unselected_text));

        btnPa25.setBackgroundResource(pa == LashEngine.PressureAngle.PA_25_0 ? R.drawable.chip_selected : R.drawable.chip_bg);
        btnPa25.setTextColor(getResources().getColor(pa == LashEngine.PressureAngle.PA_25_0 ? R.color.chip_selected_text : R.color.chip_unselected_text));
    }

    private void updateMetallurgySelection(LashEngine.Metallurgy mat) {
        mCurrentMetallurgy = mat;
        btnMatIronIron.setBackgroundResource(mat == LashEngine.Metallurgy.ALL_IRON ? R.drawable.chip_selected : R.drawable.chip_bg);
        btnMatIronIron.setTextColor(getResources().getColor(mat == LashEngine.Metallurgy.ALL_IRON ? R.color.chip_selected_text : R.color.chip_unselected_text));

        btnMatIronAlum.setBackgroundResource(mat == LashEngine.Metallurgy.MIXED_IRON_ALUM ? R.drawable.chip_selected : R.drawable.chip_bg);
        btnMatIronAlum.setTextColor(getResources().getColor(mat == LashEngine.Metallurgy.MIXED_IRON_ALUM ? R.color.chip_selected_text : R.color.chip_unselected_text));

        btnMatAlumAlum.setBackgroundResource(mat == LashEngine.Metallurgy.ALL_ALUMINUM ? R.drawable.chip_selected : R.drawable.chip_bg);
        btnMatAlumAlum.setTextColor(getResources().getColor(mat == LashEngine.Metallurgy.ALL_ALUMINUM ? R.color.chip_selected_text : R.color.chip_unselected_text));
    }

    private void computeGearBacklash() {
        double measured = parseDoubleDefensive(etMeasuredBacklash.getText().toString(), mIsInch ? 0.0080 : 0.203);
        double minTarget = parseDoubleDefensive(etTargetMin.getText().toString(), mIsInch ? 0.0060 : 0.150);
        double maxTarget = parseDoubleDefensive(etTargetMax.getText().toString(), mIsInch ? 0.0100 : 0.250);

        LashEngine.BacklashResult res = LashEngine.computeBacklash(measured, mIsInch, minTarget, maxTarget, mCurrentPa);

        tvGearStatusBadge.setText(res.statusBadge);
        tvGearStatusBadge.setBackgroundResource(res.isInSpec ? R.drawable.badge_in_spec : R.drawable.badge_out_spec);
        tvGearAction.setText(res.actionInstruction);

        if (mIsInch) {
            tvMeasuredPrimary.setText(String.format(Locale.US, "%.4f in", res.measuredIn));
            tvMeasuredSecondary.setText(String.format(Locale.US, "%.3f mm", res.measuredMm));

            tvShimPrimary.setText(String.format(Locale.US, "%.4f in", res.requiredShimChangeIn));
            tvShimSecondary.setText(String.format(Locale.US, "%.3f mm", res.requiredShimChangeMm));
        } else {
            tvMeasuredPrimary.setText(String.format(Locale.US, "%.3f mm", res.measuredMm));
            tvMeasuredSecondary.setText(String.format(Locale.US, "%.4f in", res.measuredIn));

            tvShimPrimary.setText(String.format(Locale.US, "%.3f mm", res.requiredShimChangeMm));
            tvShimSecondary.setText(String.format(Locale.US, "%.4f in", res.requiredShimChangeIn));
        }

        lashDialView.updateLash(mIsInch ? res.measuredIn : res.measuredMm, mIsInch,
                mIsInch ? res.targetMinIn : res.targetMinMm,
                mIsInch ? res.targetMaxIn : res.targetMaxMm);
    }

    private void computeValveLash() {
        double hotSpec = parseDoubleDefensive(etHotLashSpec.getText().toString(), mIsInch ? 0.0220 : 0.550);
        LashEngine.ValveLashResult res = LashEngine.computeColdValveLash(hotSpec, mIsInch, mCurrentMetallurgy, mIsAlumPushrods);

        if (mIsInch) {
            tvColdTargetPrimary.setText(String.format(Locale.US, "%.4f in", res.coldTargetIn));
            tvColdTargetSecondary.setText(String.format(Locale.US, "%.3f mm", res.coldTargetMm));
            tvFeelerGo.setText(String.format(Locale.US, "GO: %.4f in", res.feelerGoIn));
            tvFeelerNoGo.setText(String.format(Locale.US, "NO-GO: %.4f in", res.feelerNoGoIn));
        } else {
            tvColdTargetPrimary.setText(String.format(Locale.US, "%.3f mm", res.coldTargetMm));
            tvColdTargetSecondary.setText(String.format(Locale.US, "%.4f in", res.coldTargetIn));
            tvFeelerGo.setText(String.format(Locale.US, "GO: %.3f mm", res.feelerGoMm));
            tvFeelerNoGo.setText(String.format(Locale.US, "NO-GO: %.3f mm", res.feelerNoGoMm));
        }

        tvValveThermalNote.setText(res.note);

        if (mActiveTab == 1) {
            lashDialView.updateLash(mIsInch ? res.coldTargetIn : res.coldTargetMm, mIsInch,
                    (mIsInch ? res.coldTargetIn : res.coldTargetMm) - (mIsInch ? 0.001 : 0.025),
                    (mIsInch ? res.coldTargetIn : res.coldTargetMm) + (mIsInch ? 0.001 : 0.025));
        }
    }

    private void computeRunoutTir() {
        double p1 = parseDoubleDefensive(etRunout1.getText().toString(), 0.008);
        double p2 = parseDoubleDefensive(etRunout2.getText().toString(), 0.0085);
        double p3 = parseDoubleDefensive(etRunout3.getText().toString(), 0.008);
        double p4 = parseDoubleDefensive(etRunout4.getText().toString(), 0.0075);

        LashEngine.RunoutResult res = LashEngine.computeTir(p1, p2, p3, p4, mIsInch);
        tvRunoutResult.setText(res.status);
        tvRunoutResult.setTextColor(getResources().getColor(res.isAcceptable ? R.color.status_in_spec : R.color.status_out_spec));
    }

    private void saveCurrentRecord() {
        if (mActiveTab == 0) {
            double measured = parseDoubleDefensive(etMeasuredBacklash.getText().toString(), 0.008);
            double target = parseDoubleDefensive(etTargetMin.getText().toString(), 0.006);
            mDbHelper.insertLog("GEAR_BACKLASH", "Differential Ring Gear", measured, target,
                    mIsInch ? "IN" : "MM", tvGearStatusBadge.getText().toString(), tvGearAction.getText().toString());
        } else if (mActiveTab == 1) {
            double hot = parseDoubleDefensive(etHotLashSpec.getText().toString(), 0.022);
            double cold = parseDoubleDefensive(tvColdTargetPrimary.getText().toString().replace(" in", "").replace(" mm", ""), 0.017);
            mDbHelper.insertLog("VALVE_LASH", mCurrentMetallurgy.label, hot, cold,
                    mIsInch ? "IN" : "MM", "COLD TARGET", tvValveThermalNote.getText().toString());
        } else {
            mDbHelper.insertLog("RUNOUT_TIR", "Ring Gear 4-Point", 0.0, 0.0,
                    mIsInch ? "IN" : "MM", "TIR", tvRunoutResult.getText().toString());
        }
        Toast.makeText(this, "Setup record saved to log sheet", Toast.LENGTH_SHORT).show();
    }

    private void showLogsDialog() {
        List<DatabaseHelper.LogEntry> logs = mDbHelper.getAllLogs();
        if (logs.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Setup History")
                    .setMessage("No setup records saved yet. Compute and tap 'SAVE SETUP TO LOG SHEET'.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (DatabaseHelper.LogEntry l : logs) {
            sb.append("• ").append(l.name).append(" (").append(l.type).append(")\n");
            sb.append("  Measured: ").append(l.measured).append(" ").append(l.unit);
            sb.append(" | Status: ").append(l.status).append("\n");
            if (!TextUtils.isEmpty(l.note)) {
                sb.append("  Action: ").append(l.note).append("\n");
            }
            sb.append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Setup Records (" + logs.size() + ")")
                .setMessage(sb.toString().trim())
                .setPositiveButton("CLOSE", null)
                .setNegativeButton("CLEAR ALL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mDbHelper.clearAllLogs();
                        Toast.makeText(MainActivity.this, "Logs cleared", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private double parseDoubleDefensive(String str, double defaultVal) {
        if (TextUtils.isEmpty(str)) return defaultVal;
        try {
            String clean = str.replaceAll("[^0-9.-]", "").trim();
            if (clean.isEmpty() || clean.equals(".") || clean.equals("-")) return defaultVal;
            double d = Double.parseDouble(clean);
            if (Double.isNaN(d) || Double.isInfinite(d)) return defaultVal;
            return d;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("saved_tab", mActiveTab);
        outState.putBoolean("saved_unit", mIsInch);
        if (etMeasuredBacklash != null && etMeasuredBacklash.getText() != null) {
            outState.putString("saved_measured", etMeasuredBacklash.getText().toString());
        }
        if (etTargetMin != null && etTargetMin.getText() != null) {
            outState.putString("saved_target_min", etTargetMin.getText().toString());
        }
        if (etTargetMax != null && etTargetMax.getText() != null) {
            outState.putString("saved_target_max", etTargetMax.getText().toString());
        }
        if (etHotLashSpec != null && etHotLashSpec.getText() != null) {
            outState.putString("saved_hot", etHotLashSpec.getText().toString());
        }
        if (etRunout1 != null && etRunout1.getText() != null) {
            outState.putString("saved_runout_1", etRunout1.getText().toString());
        }
        if (etRunout2 != null && etRunout2.getText() != null) {
            outState.putString("saved_runout_2", etRunout2.getText().toString());
        }
        if (etRunout3 != null && etRunout3.getText() != null) {
            outState.putString("saved_runout_3", etRunout3.getText().toString());
        }
        if (etRunout4 != null && etRunout4.getText() != null) {
            outState.putString("saved_runout_4", etRunout4.getText().toString());
        }
    }
}
