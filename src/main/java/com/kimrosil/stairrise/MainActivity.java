package com.kimrosil.stairrise;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "stairrise_prefs";
    private static final String KEY_THEME_STR = "theme_mode"; // "system", "dark", "light"
    private static final String KEY_TOTAL_RISE = "total_rise_mm";
    private static final String KEY_TREAD_RUN = "tread_run_mm";
    private static final String KEY_FLUSH_MOUNT = "flush_mount";
    private static final String KEY_TREAD_THICK = "tread_thick_mm";
    private static final String KEY_RAIL_SPAN = "rail_span_mm";
    private static final String KEY_BALUSTER_WIDTH = "baluster_width_mm";
    private static final String KEY_ACTIVE_TAB = "active_tab";

    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;

    // Header
    private Button btnThemeToggle;
    private Button btnSettings;

    // Tabs
    private Button tabStringer;
    private Button tabLayout;
    private Button tabBaluster;
    private Button tabSaved;
    private LinearLayout containerStringer;
    private LinearLayout containerLayout;
    private LinearLayout containerBaluster;
    private LinearLayout containerSaved;

    // Tab 1: Stringer Controls & Telemetry
    private Button presetRise914;
    private Button presetRise1829;
    private Button presetRise2642;
    private Button presetRise2743;
    private Button btnRiseMinus;
    private Button btnRisePlus;
    private EditText etTotalRiseMm;

    private Button chipRun254;
    private Button chipRun267;
    private Button chipRun280;
    private Button chipMountToggle;

    private TextView tvStepRisePrimary;
    private TextView tvStepRiseSub;
    private TextView tvRiserCountPrimary;
    private TextView tvTreadCountSub;
    private TextView tvStringerLenPrimary;
    private TextView tvStringerLenSub;
    private TextView tvTotalRunPrimary;
    private TextView tvPitchSub;
    private TextView tvCodeBadge;
    private Button btnCopySpec;
    private Button btnSaveJob;

    // Tab 2: Framing Square & Throat Layout
    private Button chipTread25;
    private Button chipTread38;
    private Button chipTread50;
    private TextView tvSquareTongue;
    private TextView tvSquareBlade;
    private TextView tvBottomDrop;
    private TextView tvThroatDepth;
    private TextView tvHypotenuseStep;

    // Tab 3: Baluster Spacing
    private Button btnSpanMinus;
    private Button btnSpanPlus;
    private TextView tvRailSpanDisplay;
    private Button chipBaluster19;
    private Button chipBaluster38;
    private Button chipBaluster44;
    private TextView tvBalusterCountOut;
    private TextView tvBalusterGapOut;

    // Tab 4: Saved Jobs
    private TextView tvSavedHeader;
    private Button btnClearAllJobs;
    private TextView tvEmptySavedNotice;
    private LinearLayout llSavedJobsList;

    // Calculation State
    private double totalRiseMm = 2642.0;
    private double treadRunMm = 267.0;
    private boolean isFlushMount = false;
    private double treadThickMm = 38.0;
    private double railSpanMm = 1800.0;
    private double balusterWidthMm = 38.0;
    private int currentTab = 0;
    private boolean isUpdatingInput = false;

    // Computed outputs
    private int calcRiserCount = 15;
    private int calcTreadCount = 14;
    private double calcStepRiseMm = 176.1;
    private double calcTotalRunMm = 3738.0;
    private double calcStringerLenMm = 4573.0;
    private double calcPitchDeg = 33.4;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences p = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String mode = p.getString(KEY_THEME_STR, "system");
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        int nightMode = "dark".equals(mode) ? Configuration.UI_MODE_NIGHT_YES :
                "light".equals(mode) ? Configuration.UI_MODE_NIGHT_NO :
                        Configuration.UI_MODE_NIGHT_UNDEFINED;
        if (nightMode != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        }
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applySavedTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        initViews();
        restoreState(savedInstanceState);
        setupListeners();

        recalculateAll();
        refreshSavedJobsList();
    }

    private void applySavedTheme() {
        String mode = prefs.getString(KEY_THEME_STR, "system");
        if ("light".equals(mode)) {
            setTheme(R.style.AppTheme_Light);
        } else if ("dark".equals(mode)) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        tabStringer = findViewById(R.id.tabStringer);
        tabLayout = findViewById(R.id.tabLayout);
        tabBaluster = findViewById(R.id.tabBaluster);
        tabSaved = findViewById(R.id.tabSaved);

        containerStringer = findViewById(R.id.containerStringer);
        containerLayout = findViewById(R.id.containerLayout);
        containerBaluster = findViewById(R.id.containerBaluster);
        containerSaved = findViewById(R.id.containerSaved);

        presetRise914 = findViewById(R.id.presetRise914);
        presetRise1829 = findViewById(R.id.presetRise1829);
        presetRise2642 = findViewById(R.id.presetRise2642);
        presetRise2743 = findViewById(R.id.presetRise2743);
        btnRiseMinus = findViewById(R.id.btnRiseMinus);
        btnRisePlus = findViewById(R.id.btnRisePlus);
        etTotalRiseMm = findViewById(R.id.etTotalRiseMm);

        chipRun254 = findViewById(R.id.chipRun254);
        chipRun267 = findViewById(R.id.chipRun267);
        chipRun280 = findViewById(R.id.chipRun280);
        chipMountToggle = findViewById(R.id.chipMountToggle);

        tvStepRisePrimary = findViewById(R.id.tvStepRisePrimary);
        tvStepRiseSub = findViewById(R.id.tvStepRiseSub);
        tvRiserCountPrimary = findViewById(R.id.tvRiserCountPrimary);
        tvTreadCountSub = findViewById(R.id.tvTreadCountSub);
        tvStringerLenPrimary = findViewById(R.id.tvStringerLenPrimary);
        tvStringerLenSub = findViewById(R.id.tvStringerLenSub);
        tvTotalRunPrimary = findViewById(R.id.tvTotalRunPrimary);
        tvPitchSub = findViewById(R.id.tvPitchSub);
        tvCodeBadge = findViewById(R.id.tvCodeBadge);
        btnCopySpec = findViewById(R.id.btnCopySpec);
        btnSaveJob = findViewById(R.id.btnSaveJob);

        chipTread25 = findViewById(R.id.chipTread25);
        chipTread38 = findViewById(R.id.chipTread38);
        chipTread50 = findViewById(R.id.chipTread50);
        tvSquareTongue = findViewById(R.id.tvSquareTongue);
        tvSquareBlade = findViewById(R.id.tvSquareBlade);
        tvBottomDrop = findViewById(R.id.tvBottomDrop);
        tvThroatDepth = findViewById(R.id.tvThroatDepth);
        tvHypotenuseStep = findViewById(R.id.tvHypotenuseStep);

        btnSpanMinus = findViewById(R.id.btnSpanMinus);
        btnSpanPlus = findViewById(R.id.btnSpanPlus);
        tvRailSpanDisplay = findViewById(R.id.tvRailSpanDisplay);
        chipBaluster19 = findViewById(R.id.chipBaluster19);
        chipBaluster38 = findViewById(R.id.chipBaluster38);
        chipBaluster44 = findViewById(R.id.chipBaluster44);
        tvBalusterCountOut = findViewById(R.id.tvBalusterCountOut);
        tvBalusterGapOut = findViewById(R.id.tvBalusterGapOut);

        tvSavedHeader = findViewById(R.id.tvSavedHeader);
        btnClearAllJobs = findViewById(R.id.btnClearAllJobs);
        tvEmptySavedNotice = findViewById(R.id.tvEmptySavedNotice);
        llSavedJobsList = findViewById(R.id.llSavedJobsList);
    }

    private void restoreState(Bundle savedInstanceState) {
        totalRiseMm = clampDouble(prefs.getFloat(KEY_TOTAL_RISE, 2642.0f), 150.0, 12000.0);
        treadRunMm = clampDouble(prefs.getFloat(KEY_TREAD_RUN, 267.0f), 200.0, 450.0);
        isFlushMount = prefs.getBoolean(KEY_FLUSH_MOUNT, false);
        treadThickMm = clampDouble(prefs.getFloat(KEY_TREAD_THICK, 38.0f), 15.0, 75.0);
        railSpanMm = clampDouble(prefs.getFloat(KEY_RAIL_SPAN, 1800.0f), 300.0, 6000.0);
        balusterWidthMm = clampDouble(prefs.getFloat(KEY_BALUSTER_WIDTH, 38.0f), 12.0, 90.0);
        currentTab = prefs.getInt(KEY_ACTIVE_TAB, 0);

        if (savedInstanceState != null) {
            totalRiseMm = clampDouble(savedInstanceState.getDouble(KEY_TOTAL_RISE, totalRiseMm), 150.0, 12000.0);
            treadRunMm = clampDouble(savedInstanceState.getDouble(KEY_TREAD_RUN, treadRunMm), 200.0, 450.0);
            isFlushMount = savedInstanceState.getBoolean(KEY_FLUSH_MOUNT, isFlushMount);
            treadThickMm = clampDouble(savedInstanceState.getDouble(KEY_TREAD_THICK, treadThickMm), 15.0, 75.0);
            railSpanMm = clampDouble(savedInstanceState.getDouble(KEY_RAIL_SPAN, railSpanMm), 300.0, 6000.0);
            balusterWidthMm = clampDouble(savedInstanceState.getDouble(KEY_BALUSTER_WIDTH, balusterWidthMm), 12.0, 90.0);
            currentTab = savedInstanceState.getInt(KEY_ACTIVE_TAB, currentTab);
        }

        isUpdatingInput = true;
        etTotalRiseMm.setText(String.format(Locale.US, "%.0f", totalRiseMm));
        isUpdatingInput = false;

        switchTab(currentTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(KEY_TOTAL_RISE, totalRiseMm);
        outState.putDouble(KEY_TREAD_RUN, treadRunMm);
        outState.putBoolean(KEY_FLUSH_MOUNT, isFlushMount);
        outState.putDouble(KEY_TREAD_THICK, treadThickMm);
        outState.putDouble(KEY_RAIL_SPAN, railSpanMm);
        outState.putDouble(KEY_BALUSTER_WIDTH, balusterWidthMm);
        outState.putInt(KEY_ACTIVE_TAB, currentTab);
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit()
                .putFloat(KEY_TOTAL_RISE, (float) totalRiseMm)
                .putFloat(KEY_TREAD_RUN, (float) treadRunMm)
                .putBoolean(KEY_FLUSH_MOUNT, isFlushMount)
                .putFloat(KEY_TREAD_THICK, (float) treadThickMm)
                .putFloat(KEY_RAIL_SPAN, (float) railSpanMm)
                .putFloat(KEY_BALUSTER_WIDTH, (float) balusterWidthMm)
                .putInt(KEY_ACTIVE_TAB, currentTab)
                .apply();
    }

    private double clampDouble(double val, double min, double max) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return min;
        return Math.max(min, Math.min(max, val));
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleThemeDirect();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        tabStringer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchTab(0); }
        });
        tabLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchTab(1); }
        });
        tabBaluster.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchTab(2); }
        });
        tabSaved.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchTab(3); }
        });

        // Total Rise Presets
        presetRise914.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setTotalRise(914.0); }
        });
        presetRise1829.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setTotalRise(1829.0); }
        });
        presetRise2642.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setTotalRise(2642.0); }
        });
        presetRise2743.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setTotalRise(2743.0); }
        });

        btnRiseMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTotalRise(clampDouble(totalRiseMm - 10.0, 150.0, 12000.0));
            }
        });
        btnRisePlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTotalRise(clampDouble(totalRiseMm + 10.0, 150.0, 12000.0));
            }
        });

        etTotalRiseMm.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingInput) return;
                String raw = s.toString().trim();
                if (raw.isEmpty() || ".".equals(raw) || "-".equals(raw)) return;
                try {
                    double parsed = Double.parseDouble(raw);
                    totalRiseMm = clampDouble(parsed, 150.0, 12000.0);
                    recalculateAll();
                } catch (NumberFormatException ignored) {
                }
            }
        });

        // Tread Run & Mount
        chipRun254.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                treadRunMm = 254.0;
                recalculateAll();
            }
        });
        chipRun267.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                treadRunMm = 267.0;
                recalculateAll();
            }
        });
        chipRun280.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                treadRunMm = 280.0;
                recalculateAll();
            }
        });
        chipMountToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isFlushMount = !isFlushMount;
                recalculateAll();
            }
        });

        btnCopySpec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copySpecToClipboard();
            }
        });

        btnSaveJob.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptSaveJob();
            }
        });

        // Tab 2: Tread Thickness
        chipTread25.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                treadThickMm = 25.0;
                recalculateAll();
            }
        });
        chipTread38.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                treadThickMm = 38.0;
                recalculateAll();
            }
        });
        chipTread50.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                treadThickMm = 50.0;
                recalculateAll();
            }
        });

        // Tab 3: Baluster Spacing
        btnSpanMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                railSpanMm = clampDouble(railSpanMm - 50.0, 300.0, 6000.0);
                recalculateAll();
            }
        });
        btnSpanPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                railSpanMm = clampDouble(railSpanMm + 50.0, 300.0, 6000.0);
                recalculateAll();
            }
        });
        chipBaluster19.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                balusterWidthMm = 19.0;
                recalculateAll();
            }
        });
        chipBaluster38.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                balusterWidthMm = 38.0;
                recalculateAll();
            }
        });
        chipBaluster44.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                balusterWidthMm = 44.0;
                recalculateAll();
            }
        });

        // Tab 4: Clear All
        btnClearAllJobs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dbHelper.clearAll();
                refreshSavedJobsList();
                Toast.makeText(MainActivity.this, "Cleared saved stair jobs", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setTotalRise(double mm) {
        totalRiseMm = clampDouble(mm, 150.0, 12000.0);
        isUpdatingInput = true;
        etTotalRiseMm.setText(String.format(Locale.US, "%.0f", totalRiseMm));
        etTotalRiseMm.setSelection(etTotalRiseMm.getText().length());
        isUpdatingInput = false;
        recalculateAll();
    }

    private void switchTab(int index) {
        currentTab = index;
        containerStringer.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        containerLayout.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        containerBaluster.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        containerSaved.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        setTabStyle(tabStringer, index == 0);
        setTabStyle(tabLayout, index == 1);
        setTabStyle(tabBaluster, index == 2);
        setTabStyle(tabSaved, index == 3);
    }

    private void setTabStyle(Button btn, boolean active) {
        btn.setBackgroundResource(active ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btn.setTextColor(getResources().getColor(active ? R.color.m3_on_primary : R.color.textPrimary));
    }

    private void setChipStyle(Button btn, boolean selected) {
        btn.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btn.setTextColor(getResources().getColor(selected ? R.color.m3_on_primary : R.color.chipTextUnselected));
    }

    private void recalculateAll() {
        // Highlight active rise preset
        setChipStyle(presetRise914, Math.abs(totalRiseMm - 914.0) < 1.0);
        setChipStyle(presetRise1829, Math.abs(totalRiseMm - 1829.0) < 1.0);
        setChipStyle(presetRise2642, Math.abs(totalRiseMm - 2642.0) < 1.0);
        setChipStyle(presetRise2743, Math.abs(totalRiseMm - 2743.0) < 1.0);

        // Highlight active tread run preset
        setChipStyle(chipRun254, Math.abs(treadRunMm - 254.0) < 1.0);
        setChipStyle(chipRun267, Math.abs(treadRunMm - 267.0) < 1.0);
        setChipStyle(chipRun280, Math.abs(treadRunMm - 280.0) < 1.0);
        setChipStyle(chipMountToggle, isFlushMount);
        chipMountToggle.setText(isFlushMount ? "Flush Top" : "Sub-Drop");

        // 1. Optimal Riser Count (Target ideal step rise ~ 180 mm / 7.1 in, strictly <= 196 mm IRC max)
        int risers = (int) Math.round(totalRiseMm / 180.0);
        if (risers < 1) risers = 1;
        while ((totalRiseMm / risers) > 196.85 && risers < 100) {
            risers++;
        }
        calcRiserCount = risers;
        calcStepRiseMm = totalRiseMm / calcRiserCount;
        calcTreadCount = isFlushMount ? calcRiserCount : Math.max(1, calcRiserCount - 1);
        calcTotalRunMm = calcTreadCount * treadRunMm;

        double effectiveRiseForStringer = calcTreadCount * calcStepRiseMm;
        calcStringerLenMm = Math.hypot(effectiveRiseForStringer, calcTotalRunMm);
        calcPitchDeg = Math.toDegrees(Math.atan2(calcStepRiseMm, treadRunMm));

        double stepRiseIn = calcStepRiseMm / 25.4;
        double totalRunIn = calcTotalRunMm / 25.4;
        double stringerIn = calcStringerLenMm / 25.4;

        tvStepRisePrimary.setText(String.format(Locale.US, "%.1f mm", calcStepRiseMm));
        tvStepRiseSub.setText(String.format(Locale.US, "%.2f in (Target ≤ 7.75\")", stepRiseIn));

        tvRiserCountPrimary.setText(String.format(Locale.US, "%d Risers", calcRiserCount));
        tvTreadCountSub.setText(String.format(Locale.US, "%d Treads @ %.0f mm", calcTreadCount, treadRunMm));

        tvStringerLenPrimary.setText(String.format(Locale.US, "%.0f mm", calcStringerLenMm));
        tvStringerLenSub.setText(String.format(Locale.US, "%.1f in (%.1f ft)", stringerIn, stringerIn / 12.0));

        tvTotalRunPrimary.setText(String.format(Locale.US, "%.0f mm", calcTotalRunMm));
        tvPitchSub.setText(String.format(Locale.US, "Slope: %.1f° (%.1f in)", calcPitchDeg, totalRunIn));

        double comfortIndex = (2.0 * calcStepRiseMm) + treadRunMm;
        boolean ircPass = (calcStepRiseMm >= 102.0 && calcStepRiseMm <= 196.85 && treadRunMm >= 254.0);
        if (ircPass) {
            tvCodeBadge.setText(String.format(Locale.US,
                    "IRC R311.7 PASS: Rise %.1f mm (≤196) • Run %.0f mm (≥254) • 2R+T = %.0f mm",
                    calcStepRiseMm, treadRunMm, comfortIndex));
        } else {
            tvCodeBadge.setText(String.format(Locale.US,
                    "CUSTOM SPEC: Rise %.1f mm • Run %.0f mm • 2R+T = %.0f mm",
                    calcStepRiseMm, treadRunMm, comfortIndex));
        }

        // 2. Framing Square & Throat Depth (Tab 2)
        setChipStyle(chipTread25, Math.abs(treadThickMm - 25.0) < 1.0);
        setChipStyle(chipTread38, Math.abs(treadThickMm - 38.0) < 1.0);
        setChipStyle(chipTread50, Math.abs(treadThickMm - 50.0) < 1.0);

        double bottomFirstRiserMm = Math.max(20.0, calcStepRiseMm - treadThickMm);
        double stepHypotMm = Math.hypot(calcStepRiseMm, treadRunMm);
        double notchDepthMm = (calcStepRiseMm * treadRunMm) / stepHypotMm;
        double boardWidth2x12Mm = 285.75; // 11.25 inches standard 2x12 lumber
        double remainingThroatMm = boardWidth2x12Mm - notchDepthMm;

        tvSquareTongue.setText(String.format(Locale.US, "• Square Tongue (Rise Mark): %.1f mm (%.2f in)", calcStepRiseMm, stepRiseIn));
        tvSquareBlade.setText(String.format(Locale.US, "• Square Blade (Run Mark): %.1f mm (%.2f in)", treadRunMm, treadRunMm / 25.4));
        tvBottomDrop.setText(String.format(Locale.US, "• 1st Step Bottom Cut (Drop %.0f mm): %.1f mm (%.2f in)", treadThickMm, bottomFirstRiserMm, bottomFirstRiserMm / 25.4));
        tvThroatDepth.setText(String.format(Locale.US, "• Remaining 2x12 Throat Depth: %.1f mm (%.2f in) — %s",
                remainingThroatMm, remainingThroatMm / 25.4, remainingThroatMm >= 88.9 ? "PASS (≥ 89 mm)" : "REINFORCE"));
        tvHypotenuseStep.setText(String.format(Locale.US, "• Per-Step Stringer Pitch Hypotenuse: %.1f mm (%.2f in)", stepHypotMm, stepHypotMm / 25.4));

        // 3. Baluster 100mm / 4-inch Sphere Spacing (Tab 3)
        setChipStyle(chipBaluster19, Math.abs(balusterWidthMm - 19.0) < 1.0);
        setChipStyle(chipBaluster38, Math.abs(balusterWidthMm - 38.0) < 1.0);
        setChipStyle(chipBaluster44, Math.abs(balusterWidthMm - 44.0) < 1.0);

        tvRailSpanDisplay.setText(String.format(Locale.US, "%.0f mm (%.1f in)", railSpanMm, railSpanMm / 25.4));
        double maxGapMm = 99.0; // Strictly under 102 mm (4.0 in) IRC sphere limit
        int spaces = (int) Math.ceil((railSpanMm + balusterWidthMm) / (maxGapMm + balusterWidthMm));
        if (spaces < 2) spaces = 2;
        int balusters = spaces - 1;
        double actualGapMm = (railSpanMm - (balusters * balusterWidthMm)) / spaces;
        double onCenterMm = actualGapMm + balusterWidthMm;

        tvBalusterCountOut.setText(String.format(Locale.US, "Required Balusters: %d Spindles (%d Spaces)", balusters, spaces));
        tvBalusterGapOut.setText(String.format(Locale.US, "Clear Air Gap: %.1f mm (%.2f in) • On-Center: %.1f mm (%.2f in)",
                actualGapMm, actualGapMm / 25.4, onCenterMm, onCenterMm / 25.4));
    }

    private void copySpecToClipboard() {
        String mountStr = isFlushMount ? "Flush Top" : "Standard Sub-Landing";
        String summary = String.format(Locale.US,
                "StairRise Stringer Spec (%s)\nTotal Rise: %.0f mm | Tread Run: %.0f mm\nRisers: %d @ %.1f mm (%.2f in)\nTreads: %d | Total Run: %.0f mm\nStringer Length: %.0f mm | Pitch: %.1f°",
                mountStr, totalRiseMm, treadRunMm, calcRiserCount, calcStepRiseMm,
                calcStepRiseMm / 25.4, calcTreadCount, calcTotalRunMm, calcStringerLenMm, calcPitchDeg);

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("StairRise Spec", summary));
            Toast.makeText(this, "Stair stringer spec copied!", Toast.LENGTH_SHORT).show();
        }
    }

    private void promptSaveJob() {
        final String defaultLabel = String.format(Locale.US, "Stair %.0f mm (%d Steps)", totalRiseMm, calcRiserCount);
        final EditText etInput = new EditText(this);
        etInput.setText(defaultLabel);
        etInput.setSelection(defaultLabel.length());
        etInput.setBackgroundResource(R.drawable.edittext_bg);
        etInput.setPadding(24, 20, 24, 20);
        etInput.setTextColor(getResources().getColor(R.color.textPrimary));
        etInput.setHint("Job label");
        etInput.setHintTextColor(getResources().getColor(R.color.textSecondary));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 20, 40, 10);
        box.addView(etInput);

        new AlertDialog.Builder(this)
                .setTitle("Save Stair Stringer Job")
                .setMessage("Enter a project or staircase label:")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String label = etInput.getText().toString().trim();
                        if (label.isEmpty()) label = defaultLabel;
                        long id = dbHelper.insertStairJob(
                                label,
                                totalRiseMm,
                                treadRunMm,
                                calcRiserCount,
                                calcStepRiseMm,
                                calcTotalRunMm,
                                calcStringerLenMm,
                                calcPitchDeg,
                                isFlushMount ? "Flush Top" : "Sub-Drop"
                        );
                        if (id > 0) {
                            Toast.makeText(MainActivity.this, "Saved to Stair Jobs!", Toast.LENGTH_SHORT).show();
                            refreshSavedJobsList();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshSavedJobsList() {
        llSavedJobsList.removeAllViews();
        List<Map<String, Object>> jobs = dbHelper.getAllJobs();
        if (jobs.isEmpty()) {
            tvEmptySavedNotice.setVisibility(View.VISIBLE);
            tvSavedHeader.setText("SAVED STAIR STRINGER JOBS (0)");
            return;
        }
        tvEmptySavedNotice.setVisibility(View.GONE);
        tvSavedHeader.setText(String.format(Locale.US, "SAVED STAIR STRINGER JOBS (%d)", jobs.size()));

        for (final Map<String, Object> job : jobs) {
            final long id = (Long) job.get(DatabaseHelper.COL_ID);
            String label = (String) job.get(DatabaseHelper.COL_LABEL);
            double totRise = (Double) job.get(DatabaseHelper.COL_TOTAL_RISE_MM);
            int risers = (Integer) job.get(DatabaseHelper.COL_RISER_COUNT);
            double stepRise = (Double) job.get(DatabaseHelper.COL_STEP_RISE_MM);
            double strLen = (Double) job.get(DatabaseHelper.COL_STRINGER_LEN_MM);
            double pitch = (Double) job.get(DatabaseHelper.COL_PITCH_DEG);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_m3);
            card.setPadding(32, 26, 32, 26);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, 16);
            card.setLayoutParams(cp);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(label);
            tvTitle.setTextSize(15);
            tvTitle.setTextColor(getResources().getColor(R.color.textPrimary));
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            topRow.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button btnDel = new Button(this, null, 0, R.style.CleanButton);
            btnDel.setText("Delete");
            btnDel.setTextSize(11);
            btnDel.setTextColor(getResources().getColor(R.color.m3_error));
            btnDel.setBackgroundResource(R.drawable.bg_chip_unselected);
            btnDel.setMinHeight(110);
            btnDel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dbHelper.deleteJob(id);
                    refreshSavedJobsList();
                }
            });
            topRow.addView(btnDel);
            card.addView(topRow);

            TextView tvDetails = new TextView(this);
            tvDetails.setText(String.format(Locale.US,
                    "Total Rise: %.0f mm • %d Risers @ %.1f mm • Stringer: %.0f mm (%.1f°)",
                    totRise, risers, stepRise, strLen, pitch));
            tvDetails.setTextSize(12.5f);
            tvDetails.setTextColor(getResources().getColor(R.color.textSecondary));
            tvDetails.setPadding(0, 8, 0, 0);
            card.addView(tvDetails);

            llSavedJobsList.addView(card);
        }
    }

    private void toggleThemeDirect() {
        String current = prefs.getString(KEY_THEME_STR, "system");
        String next;
        if ("dark".equals(current)) {
            next = "light";
        } else if ("light".equals(current)) {
            next = "dark";
        } else {
            int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            next = (uiMode == Configuration.UI_MODE_NIGHT_YES) ? "light" : "dark";
        }
        setThemePreference(next);
    }

    private void setThemePreference(String mode) {
        prefs.edit().putString(KEY_THEME_STR, mode).commit();
        recreate();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 36, 48, 28);
        layout.setBackgroundResource(R.drawable.dialog_background);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Settings & Studio About");
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(getResources().getColor(R.color.textPrimary));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvTitle);

        TextView tvThemeLabel = new TextView(this);
        tvThemeLabel.setText("DISPLAY THEME (AUTO / LIGHT / DARK):");
        tvThemeLabel.setTextSize(11);
        tvThemeLabel.setTextColor(getResources().getColor(R.color.textSecondary));
        tvThemeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvThemeLabel.setPadding(0, 22, 0, 10);
        layout.addView(tvThemeLabel);

        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setBackgroundResource(R.drawable.bg_segmented_track);
        themeRow.setPadding(6, 6, 6, 6);

        final String curTheme = prefs.getString(KEY_THEME_STR, "system");

        final Button btnAuto = new Button(this, null, 0, R.style.CleanButton);
        btnAuto.setText("Auto");
        btnAuto.setTextSize(12);
        btnAuto.setMinHeight(120);
        btnAuto.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnAuto.setBackgroundResource("system".equals(curTheme) ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btnAuto.setTextColor(getResources().getColor("system".equals(curTheme) ? R.color.m3_on_primary : R.color.textPrimary));

        final Button btnLight = new Button(this, null, 0, R.style.CleanButton);
        btnLight.setText("Light");
        btnLight.setTextSize(12);
        btnLight.setMinHeight(120);
        btnLight.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnLight.setBackgroundResource("light".equals(curTheme) ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btnLight.setTextColor(getResources().getColor("light".equals(curTheme) ? R.color.m3_on_primary : R.color.textPrimary));

        final Button btnDark = new Button(this, null, 0, R.style.CleanButton);
        btnDark.setText("Dark");
        btnDark.setTextSize(12);
        btnDark.setMinHeight(120);
        btnDark.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnDark.setBackgroundResource("dark".equals(curTheme) ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btnDark.setTextColor(getResources().getColor("dark".equals(curTheme) ? R.color.m3_on_primary : R.color.textPrimary));

        themeRow.addView(btnAuto);
        themeRow.addView(btnLight);
        themeRow.addView(btnDark);
        layout.addView(themeRow);

        // About Card with Clickable Storefront Link
        LinearLayout aboutCard = new LinearLayout(this);
        aboutCard.setOrientation(LinearLayout.VERTICAL);
        aboutCard.setBackgroundResource(R.drawable.card_background);
        aboutCard.setPadding(28, 20, 28, 20);
        LinearLayout.LayoutParams acParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acParams.setMargins(0, 24, 0, 14);
        aboutCard.setLayoutParams(acParams);

        TextView tvAboutTitle = new TextView(this);
        tvAboutTitle.setText("StairRise v1.0.0 — KimRosil App Studio");
        tvAboutTitle.setTextSize(14);
        tvAboutTitle.setTextColor(getResources().getColor(R.color.textPrimary));
        tvAboutTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        aboutCard.addView(tvAboutTitle);

        TextView tvAboutBody = new TextView(this);
        tvAboutBody.setText("100% Offline Freeware • Zero Ads • Zero Telemetry\nSandboxed Local SQLite Storage");
        tvAboutBody.setTextSize(12);
        tvAboutBody.setTextColor(getResources().getColor(R.color.textSecondary));
        tvAboutBody.setPadding(0, 6, 0, 10);
        aboutCard.addView(tvAboutBody);

        Button btnStorefront = new Button(this, null, 0, R.style.CleanButton);
        btnStorefront.setText("🌐 https://apps.kimrosil.com/stair-rise/");
        btnStorefront.setAllCaps(false);
        btnStorefront.setTextSize(11.5f);
        btnStorefront.setMinHeight(110);
        btnStorefront.setBackgroundResource(R.drawable.bg_chip_unselected);
        btnStorefront.setTextColor(getResources().getColor(R.color.colorAccent));
        btnStorefront.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com/stair-rise/"));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "https://apps.kimrosil.com/stair-rise/", Toast.LENGTH_SHORT).show();
                }
            }
        });
        aboutCard.addView(btnStorefront);
        layout.addView(aboutCard);

        // Reset Button
        Button btnReset = new Button(this, null, 0, R.style.CleanButton);
        btnReset.setText("Reset Default Stair Parameters");
        btnReset.setAllCaps(false);
        btnReset.setTextSize(12.5f);
        btnReset.setMinHeight(115);
        btnReset.setBackgroundResource(R.drawable.bg_chip_unselected);
        btnReset.setTextColor(getResources().getColor(R.color.m3_error));

        builder.setView(layout);
        final AlertDialog dialog = builder.create();

        btnAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                setThemePreference("system");
            }
        });
        btnLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                setThemePreference("light");
            }
        });
        btnDark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                setThemePreference("dark");
            }
        });
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                treadRunMm = 267.0;
                isFlushMount = false;
                treadThickMm = 38.0;
                railSpanMm = 1800.0;
                balusterWidthMm = 38.0;
                setTotalRise(2642.0);
                dialog.dismiss();
                Toast.makeText(MainActivity.this, "Restored default stair parameters", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnReset);

        dialog.show();
    }
}
