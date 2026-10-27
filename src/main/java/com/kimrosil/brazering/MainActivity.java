package com.kimrosil.brazering;

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
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "brazering_prefs";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_HAPTICS = "haptics_enabled";

    private SharedPreferences prefs;
    private BrazeDatabaseHelper dbHelper;

    private int themeMode = 2; // Default Dark Mode
    private boolean hapticsEnabled = true;
    private int currentAlloy = BrazeEngine.ALLOY_BCUP5_15_SILVER;

    // UI Elements
    private TextView tvBrandTitle;
    private TextView tvBrandSubtitle;
    private ImageButton btnThemeToggle;
    private ImageButton btnSettings;

    // Alloy Chips
    private Button btnAlloyBcup5;
    private Button btnAlloyBcup2;
    private Button btnAlloyBag36;

    // Hero HUD
    private TextView tvHeroGrams;
    private TextView tvHeroGramsSub;
    private TextView tvHeroRodLength;
    private TextView tvHeroPreformGauge;
    private TextView tvHeroN2Flow;
    private TextView tvHeroN2Pressure;

    // Clearance Banner
    private LinearLayout layoutClearanceBadge;
    private ImageView ivClearanceIcon;
    private TextView tvClearanceStatus;
    private TextView tvClearanceReason;

    // Canvas View
    private BrazeJointView brazeJointView;

    // Tube OD Chips
    private Button btnOd3_8;
    private Button btnOd1_2;
    private Button btnOd3_4;
    private Button btnOd7_8;

    // Steppers & Inputs
    private EditText etTubeOd;
    private Button btnOdDec;
    private Button btnOdInc;

    private EditText etSocketDepth;
    private Button btnDepthDec;
    private Button btnDepthInc;

    private EditText etClearance;
    private Button btnClrDec;
    private Button btnClrInc;

    private EditText etJointCount;
    private Button btnCountDec;
    private Button btnCountInc;

    // Action CTAs
    private Button btnSaveJob;
    private Button btnCopySpec;
    private Button btnReset;

    // Saved Jobs List
    private TextView tvEmptyLog;
    private LinearLayout llSavedJobsContainer;

    private boolean isUpdating = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = sp.getInt(KEY_THEME_MODE, 2);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        int nightFlags = config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK;
        if (mode == 1) {
            config.uiMode = nightFlags | Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            config.uiMode = nightFlags | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        themeMode = prefs.getInt(KEY_THEME_MODE, 2);
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true);
        dbHelper = new BrazeDatabaseHelper(this);

        setContentView(R.layout.activity_main);

        bindViews();
        setupListeners();
        updateAlloyUI();
        recalculateAndRender();
        loadSavedJobs();
    }

    private void bindViews() {
        tvBrandTitle = findViewById(R.id.tvBrandTitle);
        tvBrandSubtitle = findViewById(R.id.tvBrandSubtitle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        btnAlloyBcup5 = findViewById(R.id.btnAlloyBcup5);
        btnAlloyBcup2 = findViewById(R.id.btnAlloyBcup2);
        btnAlloyBag36 = findViewById(R.id.btnAlloyBag36);

        tvHeroGrams = findViewById(R.id.tvHeroGrams);
        tvHeroGramsSub = findViewById(R.id.tvHeroGramsSub);
        tvHeroRodLength = findViewById(R.id.tvHeroRodLength);
        tvHeroPreformGauge = findViewById(R.id.tvHeroPreformGauge);
        tvHeroN2Flow = findViewById(R.id.tvHeroN2Flow);
        tvHeroN2Pressure = findViewById(R.id.tvHeroN2Pressure);

        layoutClearanceBadge = findViewById(R.id.layoutClearanceBadge);
        ivClearanceIcon = findViewById(R.id.ivClearanceIcon);
        tvClearanceStatus = findViewById(R.id.tvClearanceStatus);
        tvClearanceReason = findViewById(R.id.tvClearanceReason);

        brazeJointView = findViewById(R.id.brazeJointView);

        btnOd3_8 = findViewById(R.id.btnOd3_8);
        btnOd1_2 = findViewById(R.id.btnOd1_2);
        btnOd3_4 = findViewById(R.id.btnOd3_4);
        btnOd7_8 = findViewById(R.id.btnOd7_8);

        etTubeOd = findViewById(R.id.etTubeOd);
        btnOdDec = findViewById(R.id.btnOdDec);
        btnOdInc = findViewById(R.id.btnOdInc);

        etSocketDepth = findViewById(R.id.etSocketDepth);
        btnDepthDec = findViewById(R.id.btnDepthDec);
        btnDepthInc = findViewById(R.id.btnDepthInc);

        etClearance = findViewById(R.id.etClearance);
        btnClrDec = findViewById(R.id.btnClrDec);
        btnClrInc = findViewById(R.id.btnClrInc);

        etJointCount = findViewById(R.id.etJointCount);
        btnCountDec = findViewById(R.id.btnCountDec);
        btnCountInc = findViewById(R.id.btnCountInc);

        btnSaveJob = findViewById(R.id.btnSaveJob);
        btnCopySpec = findViewById(R.id.btnCopySpec);
        btnReset = findViewById(R.id.btnReset);

        tvEmptyLog = findViewById(R.id.tvEmptyLog);
        llSavedJobsContainer = findViewById(R.id.llSavedJobsContainer);
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                int next = (themeMode == 2) ? 1 : 2;
                setThemeMode(next);
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                showSettingsDialog();
            }
        });

        // Alloy Chips
        btnAlloyBcup5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                currentAlloy = BrazeEngine.ALLOY_BCUP5_15_SILVER;
                updateAlloyUI();
                recalculateAndRender();
            }
        });
        btnAlloyBcup2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                currentAlloy = BrazeEngine.ALLOY_BCUP2_0_SILVER;
                updateAlloyUI();
                recalculateAndRender();
            }
        });
        btnAlloyBag36.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                currentAlloy = BrazeEngine.ALLOY_BAG36_45_SILVER;
                updateAlloyUI();
                recalculateAndRender();
            }
        });

        // Tube OD Chips
        View.OnClickListener odChipListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double od = 0.875;
                if (v == btnOd3_8) od = 0.375;
                else if (v == btnOd1_2) od = 0.500;
                else if (v == btnOd3_4) od = 0.750;
                else if (v == btnOd7_8) od = 0.875;

                etTubeOd.setText(String.format(Locale.US, "%.3f", od));
                double stdDepth = Math.max(0.38, od * 0.75);
                etSocketDepth.setText(String.format(Locale.US, "%.2f", stdDepth));
                updateOdChipsUI(od);
            }
        };
        btnOd3_8.setOnClickListener(odChipListener);
        btnOd1_2.setOnClickListener(odChipListener);
        btnOd3_4.setOnClickListener(odChipListener);
        btnOd7_8.setOnClickListener(odChipListener);

        // Steppers
        btnOdDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etTubeOd, 0.875);
                val = Math.max(0.125, val - 0.125);
                etTubeOd.setText(String.format(Locale.US, "%.3f", val));
                updateOdChipsUI(val);
            }
        });
        btnOdInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etTubeOd, 0.875);
                val += 0.125;
                etTubeOd.setText(String.format(Locale.US, "%.3f", val));
                updateOdChipsUI(val);
            }
        });

        btnDepthDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etSocketDepth, 0.75);
                val = Math.max(0.20, val - 0.05);
                etSocketDepth.setText(String.format(Locale.US, "%.2f", val));
            }
        });
        btnDepthInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etSocketDepth, 0.75);
                val += 0.05;
                etSocketDepth.setText(String.format(Locale.US, "%.2f", val));
            }
        });

        btnClrDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etClearance, 0.003);
                val = Math.max(0.001, val - 0.0005);
                etClearance.setText(String.format(Locale.US, "%.4f", val));
            }
        });
        btnClrInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                double val = parseDouble(etClearance, 0.003);
                val += 0.0005;
                etClearance.setText(String.format(Locale.US, "%.4f", val));
            }
        });

        btnCountDec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                int val = (int) parseDouble(etJointCount, 1.0);
                val = Math.max(1, val - 1);
                etJointCount.setText(String.valueOf(val));
            }
        });
        btnCountInc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                int val = (int) parseDouble(etJointCount, 1.0);
                val += 1;
                etJointCount.setText(String.valueOf(val));
            }
        });

        // Defensive TextWatchers
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdating) {
                    recalculateAndRender();
                }
            }
        };

        etTubeOd.addTextChangedListener(watcher);
        etSocketDepth.addTextChangedListener(watcher);
        etClearance.addTextChangedListener(watcher);
        etJointCount.addTextChangedListener(watcher);

        // Action CTAs
        btnSaveJob.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                saveCurrentJobToLog();
            }
        });

        btnCopySpec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                copyBrazeSpecToClipboard();
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                resetDefaults();
            }
        });
    }

    private void updateAlloyUI() {
        btnAlloyBcup5.setBackgroundResource(currentAlloy == BrazeEngine.ALLOY_BCUP5_15_SILVER ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnAlloyBcup5.setTextColor(getResources().getColor(currentAlloy == BrazeEngine.ALLOY_BCUP5_15_SILVER ? R.color.text_on_accent : R.color.text_primary));

        btnAlloyBcup2.setBackgroundResource(currentAlloy == BrazeEngine.ALLOY_BCUP2_0_SILVER ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnAlloyBcup2.setTextColor(getResources().getColor(currentAlloy == BrazeEngine.ALLOY_BCUP2_0_SILVER ? R.color.text_on_accent : R.color.text_primary));

        btnAlloyBag36.setBackgroundResource(currentAlloy == BrazeEngine.ALLOY_BAG36_45_SILVER ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnAlloyBag36.setTextColor(getResources().getColor(currentAlloy == BrazeEngine.ALLOY_BAG36_45_SILVER ? R.color.text_on_accent : R.color.text_primary));
    }

    private void updateOdChipsUI(double od) {
        btnOd3_8.setBackgroundResource(Math.abs(od - 0.375) < 0.02 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnOd3_8.setTextColor(getResources().getColor(Math.abs(od - 0.375) < 0.02 ? R.color.text_on_accent : R.color.text_primary));

        btnOd1_2.setBackgroundResource(Math.abs(od - 0.500) < 0.02 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnOd1_2.setTextColor(getResources().getColor(Math.abs(od - 0.500) < 0.02 ? R.color.text_on_accent : R.color.text_primary));

        btnOd3_4.setBackgroundResource(Math.abs(od - 0.750) < 0.02 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnOd3_4.setTextColor(getResources().getColor(Math.abs(od - 0.750) < 0.02 ? R.color.text_on_accent : R.color.text_primary));

        btnOd7_8.setBackgroundResource(Math.abs(od - 0.875) < 0.02 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnOd7_8.setTextColor(getResources().getColor(Math.abs(od - 0.875) < 0.02 ? R.color.text_on_accent : R.color.text_primary));
    }

    private void recalculateAndRender() {
        try {
            double od = parseDouble(etTubeOd, 0.875);
            double depth = parseDouble(etSocketDepth, 0.75);
            double cl = parseDouble(etClearance, 0.003);
            int count = (int) parseDouble(etJointCount, 1.0);

            BrazeEngine.BrazeResult res = BrazeEngine.calculateJoint(od, depth, cl, currentAlloy, count);

            // Update Hero HUD
            if (count > 1) {
                tvHeroGrams.setText(String.format(Locale.US, "%.2f g (%d Joints)", res.totalJobMassGrams, count));
                tvHeroGramsSub.setText(String.format(Locale.US, "%.2f g per joint • %.4f in³ Annular Vol", res.alloyMassGrams, res.jointVolumeCuIn));
            } else {
                tvHeroGrams.setText(String.format(Locale.US, "%.2f g", res.alloyMassGrams));
                tvHeroGramsSub.setText(String.format(Locale.US, "%.3f oz • %.4f in³ Annular Vol", res.alloyMassGrams / 28.3495, res.jointVolumeCuIn));
            }

            tvHeroRodLength.setText(String.format(Locale.US, "%.1f in (1/16\" Wire)", res.rodLength1_16_Inches * count));
            tvHeroPreformGauge.setText("Preform: " + res.recommendedPreformGauge);

            tvHeroN2Flow.setText(String.format(Locale.US, "%.1f SCFH", res.nitrogenSweepScfh));
            tvHeroN2Pressure.setText("1 - 2 PSI Low Flow");

            // Update Clearance Badge
            if (res.isClearanceOptimal) {
                layoutClearanceBadge.setBackgroundResource(R.drawable.bg_badge_pass);
                ivClearanceIcon.setImageResource(R.drawable.ic_check);
                tvClearanceStatus.setText(res.clearanceStatusTitle);
                tvClearanceStatus.setTextColor(getResources().getColor(R.color.badge_text_pass));
                tvClearanceReason.setText(res.clearanceAdvice);
                tvClearanceReason.setTextColor(getResources().getColor(R.color.badge_text_pass));
            } else {
                layoutClearanceBadge.setBackgroundResource(R.drawable.bg_badge_warn);
                ivClearanceIcon.setImageResource(R.drawable.ic_warning);
                tvClearanceStatus.setText(res.clearanceStatusTitle);
                tvClearanceStatus.setTextColor(getResources().getColor(R.color.badge_text_warn));
                tvClearanceReason.setText(res.clearanceAdvice);
                tvClearanceReason.setTextColor(getResources().getColor(R.color.badge_text_warn));
            }

            // Update Diagram View
            brazeJointView.updateData(res.tubeOdInches, res.socketDepthInches, res.radialClearanceInches, res.alloyMassGrams, res.isClearanceOptimal);

        } catch (Exception ex) {
            // Defensive input safeguarding
        }
    }

    private void saveCurrentJobToLog() {
        try {
            double od = parseDouble(etTubeOd, 0.875);
            double depth = parseDouble(etSocketDepth, 0.75);
            double cl = parseDouble(etClearance, 0.003);
            int count = (int) parseDouble(etJointCount, 1.0);

            BrazeEngine.BrazeResult res = BrazeEngine.calculateJoint(od, depth, cl, currentAlloy, count);
            String jobName = String.format(Locale.US, "Line %.3f\" OD (%d Jts)", od, count);

            dbHelper.insertJob(jobName, od, depth, cl, currentAlloy, count,
                    res.totalJobMassGrams, res.rodLength1_16_Inches * count, res.recommendedPreformGauge);

            Toast.makeText(this, "Braze job logged to database", Toast.LENGTH_SHORT).show();
            loadSavedJobs();
        } catch (Exception ex) {
            Toast.makeText(this, "Error saving braze job", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSavedJobs() {
        List<BrazeDatabaseHelper.BrazeJobRecord> list = dbHelper.getRecentJobs(20);
        llSavedJobsContainer.removeAllViews();

        if (list == null || list.isEmpty()) {
            tvEmptyLog.setVisibility(View.VISIBLE);
        } else {
            tvEmptyLog.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (final BrazeDatabaseHelper.BrazeJobRecord r : list) {
                View v = inflater.inflate(R.layout.item_braze_job, llSavedJobsContainer, false);
                TextView tvTitle = v.findViewById(R.id.tvItemTitle);
                TextView tvSpecs = v.findViewById(R.id.tvItemSpecs);
                TextView tvDetails = v.findViewById(R.id.tvItemDetails);
                ImageButton btnCopy = v.findViewById(R.id.btnItemCopy);
                ImageButton btnDel = v.findViewById(R.id.btnItemDelete);

                tvTitle.setText(r.name);
                tvSpecs.setText(String.format(Locale.US, "%d Joints • %.2f g Total • %s", r.count, r.totalGrams, r.preformGauge));
                tvDetails.setText(String.format(Locale.US, "OD: %.3f\" • Depth: %.2f\" • Gap: %.4f\"", r.tubeOd, r.depth, r.clearance));

                btnCopy.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hapticClick(v);
                        String note = String.format(Locale.US,
                                "BrazeRing Job Record:\n- Run: %s\n- OD: %.3f in\n- Socket Depth: %.2f in\n- Clearance: %.4f in\n- Total Alloy: %.2f g\n- Wire Needed: %.1f in (1/16\")",
                                r.name, r.tubeOd, r.depth, r.clearance, r.totalGrams, r.rodInches);
                        copyToClipboard("Braze Job", note);
                        Toast.makeText(MainActivity.this, "Copied braze note", Toast.LENGTH_SHORT).show();
                    }
                });

                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hapticClick(v);
                        dbHelper.deleteJob(r.id);
                        loadSavedJobs();
                    }
                });

                llSavedJobsContainer.addView(v);
            }
        }
    }

    private void copyBrazeSpecToClipboard() {
        try {
            double od = parseDouble(etTubeOd, 0.875);
            double depth = parseDouble(etSocketDepth, 0.75);
            double cl = parseDouble(etClearance, 0.003);
            int count = (int) parseDouble(etJointCount, 1.0);

            BrazeEngine.BrazeResult res = BrazeEngine.calculateJoint(od, depth, cl, currentAlloy, count);

            String report = String.format(Locale.US,
                    "🔥 BrazeRing Field HVAC/R Brazing Spec\n" +
                    "───────────────────────────\n" +
                    "Tube OD: %.3f in\n" +
                    "Alloy: %s\n" +
                    "Joint Depth: %.2f in\n" +
                    "Radial Clearance: %.4f in\n" +
                    "Joints Quantity: %d\n" +
                    "Alloy Required: %.2f g total (%.2f g/joint)\n" +
                    "Rod Length: %.1f in (1/16\" Wire)\n" +
                    "Preform Ring: %s\n" +
                    "N2 Sweep Flow: %.1f SCFH (1-2 PSI)\n" +
                    "Status: %s\n" +
                    "───────────────────────────\n" +
                    "Calculated offline via BrazeRing",
                    res.tubeOdInches,
                    BrazeEngine.getAlloyName(res.alloyType),
                    res.socketDepthInches,
                    res.radialClearanceInches,
                    res.jointCount,
                    res.totalJobMassGrams,
                    res.alloyMassGrams,
                    res.rodLength1_16_Inches * res.jointCount,
                    res.recommendedPreformGauge,
                    res.nitrogenSweepScfh,
                    res.clearanceStatusTitle);

            copyToClipboard("Braze Spec", report);
            Toast.makeText(this, "Braze spec copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Error copying spec", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetDefaults() {
        isUpdating = true;
        currentAlloy = BrazeEngine.ALLOY_BCUP5_15_SILVER;
        etTubeOd.setText("0.875");
        etSocketDepth.setText("0.75");
        etClearance.setText("0.003");
        etJointCount.setText("1");
        isUpdating = false;

        updateAlloyUI();
        updateOdChipsUI(0.875);
        recalculateAndRender();
        Toast.makeText(this, "Reset to standard 7/8\" ACR defaults", Toast.LENGTH_SHORT).show();
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        Button btnThemeLight = dialogView.findViewById(R.id.btnThemeLight);
        Button btnThemeDark = dialogView.findViewById(R.id.btnThemeDark);
        Button btnThemeAuto = dialogView.findViewById(R.id.btnThemeAuto);

        updateDialogThemeButtons(btnThemeLight, btnThemeDark, btnThemeAuto, themeMode);

        btnThemeLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                setThemeMode(1);
                dialog.dismiss();
            }
        });
        btnThemeDark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                setThemeMode(2);
                dialog.dismiss();
            }
        });
        btnThemeAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                setThemeMode(0);
                dialog.dismiss();
            }
        });

        Switch swHaptics = dialogView.findViewById(R.id.swHaptics);
        swHaptics.setChecked(hapticsEnabled);
        swHaptics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticsEnabled = ((Switch) v).isChecked();
                prefs.edit().putBoolean(KEY_HAPTICS, hapticsEnabled).apply();
            }
        });

        Button btnPrivacy = dialogView.findViewById(R.id.btnPrivacyPolicy);
        btnPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                showPrivacyDialog();
            }
        });

        Button btnClearLog = dialogView.findViewById(R.id.btnClearLog);
        btnClearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Clear Braze Job Log")
                        .setMessage("Are you sure you want to delete all saved job records?")
                        .setPositiveButton("Clear All", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                dbHelper.clearAll();
                                loadSavedJobs();
                                Toast.makeText(MainActivity.this, "Log cleared", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        Button btnDone = dialogView.findViewById(R.id.btnCloseSettings);
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hapticClick(v);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void updateDialogThemeButtons(Button btnLight, Button btnDark, Button btnAuto, int mode) {
        btnLight.setBackgroundResource(mode == 1 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnLight.setTextColor(getResources().getColor(mode == 1 ? R.color.text_on_accent : R.color.text_primary));

        btnDark.setBackgroundResource(mode == 2 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnDark.setTextColor(getResources().getColor(mode == 2 ? R.color.text_on_accent : R.color.text_primary));

        btnAuto.setBackgroundResource(mode == 0 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnAuto.setTextColor(getResources().getColor(mode == 0 ? R.color.text_on_accent : R.color.text_primary));
    }

    private void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        recreate();
    }

    private void showPrivacyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("BrazeRing Privacy Policy")
                .setMessage("100% Offline Copper Brazing Utility\n\n" +
                        "BrazeRing by KimRosil App Studio contains zero tracking SDKs, zero ad networks, and zero cloud telemetry.\n\n" +
                        "• All job specifications and calculation logs reside purely in your device's local SQLite database.\n" +
                        "• No personal data, location, or device identifiers are ever collected.\n" +
                        "• Free forever.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            cm.setPrimaryClip(clip);
        }
    }

    private void hapticClick(View v) {
        if (hapticsEnabled && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private double parseDouble(EditText et, double fallback) {
        if (et == null) return fallback;
        String s = et.getText().toString().trim();
        if (s.isEmpty()) return fallback;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
