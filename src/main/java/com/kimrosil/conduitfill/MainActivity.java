package com.kimrosil.conduitfill;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "conduit_prefs";
    private static final String KEY_THEME = "theme_mode";

    private ConduitDatabaseHelper dbHelper;
    private ConduitEngine.FillResult lastResult;

    // Raceway & Conductor State
    private int racewayType = ConduitEngine.RACEWAY_EMT;
    private int conduitSizeIdx = 2; // 1"
    private int insulationType = ConduitEngine.INSULATION_THHN;
    private int primaryGaugeIdx = 2; // 10 AWG
    private int primaryCount = 3;
    private int groundGaugeIdx = 2; // 10 AWG
    private int groundCount = 1;
    private boolean isNippleRule = false;

    // Views
    private ConduitCrossSectionView conduitView;
    private TextView tvComplianceStatus;
    private TextView tvJamStatus;

    private Button btnTypeEmt;
    private Button btnTypePvc40;
    private Button btnTypePvc80;
    private Button btnTypeRmc;

    private Button btnConduitMinus;
    private Button btnConduitPlus;
    private TextView tvConduitSizeLabel;

    private Button btnInsThhn;
    private Button btnInsXhhw;
    private Button btnInsRhw;

    private Button btnPrimaryGaugeMinus;
    private Button btnPrimaryGaugePlus;
    private TextView tvPrimaryGauge;
    private Button btnPrimaryCountMinus;
    private Button btnPrimaryCountPlus;
    private TextView tvPrimaryCount;

    private Button btnGroundGaugeMinus;
    private Button btnGroundGaugePlus;
    private TextView tvGroundGauge;
    private Button btnGroundCountMinus;
    private Button btnGroundCountPlus;
    private TextView tvGroundCount;

    private Button btnToggleNipple;
    private TextView tvStraightPull;
    private TextView tvAnglePull;
    private TextView tvAreaBreakdown;

    private Button btnSaveRun;
    private Button btnCopyReport;
    private Button btnClearRuns;
    private LinearLayout layoutRunsList;
    private TextView tvEmptyRuns;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String themeMode = sp.getString(KEY_THEME, "auto");

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if ("light".equals(themeMode)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if ("dark".equals(themeMode)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new ConduitDatabaseHelper(this);
        bindViews();
        setupListeners();
        recalculate();
        refreshRunsList();
    }

    private void bindViews() {
        conduitView = findViewById(R.id.conduitView);
        tvComplianceStatus = findViewById(R.id.tvComplianceStatus);
        tvJamStatus = findViewById(R.id.tvJamStatus);

        btnTypeEmt = findViewById(R.id.btnTypeEmt);
        btnTypePvc40 = findViewById(R.id.btnTypePvc40);
        btnTypePvc80 = findViewById(R.id.btnTypePvc80);
        btnTypeRmc = findViewById(R.id.btnTypeRmc);

        btnConduitMinus = findViewById(R.id.btnConduitMinus);
        btnConduitPlus = findViewById(R.id.btnConduitPlus);
        tvConduitSizeLabel = findViewById(R.id.tvConduitSizeLabel);

        btnInsThhn = findViewById(R.id.btnInsThhn);
        btnInsXhhw = findViewById(R.id.btnInsXhhw);
        btnInsRhw = findViewById(R.id.btnInsRhw);

        btnPrimaryGaugeMinus = findViewById(R.id.btnPrimaryGaugeMinus);
        btnPrimaryGaugePlus = findViewById(R.id.btnPrimaryGaugePlus);
        tvPrimaryGauge = findViewById(R.id.tvPrimaryGauge);
        btnPrimaryCountMinus = findViewById(R.id.btnPrimaryCountMinus);
        btnPrimaryCountPlus = findViewById(R.id.btnPrimaryCountPlus);
        tvPrimaryCount = findViewById(R.id.tvPrimaryCount);

        btnGroundGaugeMinus = findViewById(R.id.btnGroundGaugeMinus);
        btnGroundGaugePlus = findViewById(R.id.btnGroundGaugePlus);
        tvGroundGauge = findViewById(R.id.tvGroundGauge);
        btnGroundCountMinus = findViewById(R.id.btnGroundCountMinus);
        btnGroundCountPlus = findViewById(R.id.btnGroundCountPlus);
        tvGroundCount = findViewById(R.id.tvGroundCount);

        btnToggleNipple = findViewById(R.id.btnToggleNipple);
        tvStraightPull = findViewById(R.id.tvStraightPull);
        tvAnglePull = findViewById(R.id.tvAnglePull);
        tvAreaBreakdown = findViewById(R.id.tvAreaBreakdown);

        btnSaveRun = findViewById(R.id.btnSaveRun);
        btnCopyReport = findViewById(R.id.btnCopyReport);
        btnClearRuns = findViewById(R.id.btnClearRuns);
        layoutRunsList = findViewById(R.id.layoutRunsList);
        tvEmptyRuns = findViewById(R.id.tvEmptyRuns);

        ImageButton btnTheme = findViewById(R.id.btnTheme);
        ImageButton btnSettings = findViewById(R.id.btnSettings);

        btnTheme.setOnClickListener(v -> cycleTheme());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void setupListeners() {
        btnTypeEmt.setOnClickListener(v -> setRacewayType(ConduitEngine.RACEWAY_EMT));
        btnTypePvc40.setOnClickListener(v -> setRacewayType(ConduitEngine.RACEWAY_PVC_40));
        btnTypePvc80.setOnClickListener(v -> setRacewayType(ConduitEngine.RACEWAY_PVC_80));
        btnTypeRmc.setOnClickListener(v -> setRacewayType(ConduitEngine.RACEWAY_RMC));

        btnConduitMinus.setOnClickListener(v -> {
            if (conduitSizeIdx > 0) {
                conduitSizeIdx--;
                recalculate();
            }
        });
        btnConduitPlus.setOnClickListener(v -> {
            if (conduitSizeIdx < ConduitEngine.TRADE_SIZES.length - 1) {
                conduitSizeIdx++;
                recalculate();
            }
        });

        btnInsThhn.setOnClickListener(v -> setInsulationType(ConduitEngine.INSULATION_THHN));
        btnInsXhhw.setOnClickListener(v -> setInsulationType(ConduitEngine.INSULATION_XHHW));
        btnInsRhw.setOnClickListener(v -> setInsulationType(ConduitEngine.INSULATION_RHW));

        btnPrimaryGaugeMinus.setOnClickListener(v -> {
            if (primaryGaugeIdx > 0) {
                primaryGaugeIdx--;
                recalculate();
            }
        });
        btnPrimaryGaugePlus.setOnClickListener(v -> {
            if (primaryGaugeIdx < ConduitEngine.WIRE_GAUGES.length - 1) {
                primaryGaugeIdx++;
                recalculate();
            }
        });

        btnPrimaryCountMinus.setOnClickListener(v -> {
            if (primaryCount > 0) {
                primaryCount--;
                recalculate();
            }
        });
        btnPrimaryCountPlus.setOnClickListener(v -> {
            if (primaryCount < 30) {
                primaryCount++;
                recalculate();
            }
        });

        btnGroundGaugeMinus.setOnClickListener(v -> {
            if (groundGaugeIdx > 0) {
                groundGaugeIdx--;
                recalculate();
            }
        });
        btnGroundGaugePlus.setOnClickListener(v -> {
            if (groundGaugeIdx < ConduitEngine.WIRE_GAUGES.length - 1) {
                groundGaugeIdx++;
                recalculate();
            }
        });

        btnGroundCountMinus.setOnClickListener(v -> {
            if (groundCount > 0) {
                groundCount--;
                recalculate();
            }
        });
        btnGroundCountPlus.setOnClickListener(v -> {
            if (groundCount < 10) {
                groundCount++;
                recalculate();
            }
        });

        btnToggleNipple.setOnClickListener(v -> {
            isNippleRule = !isNippleRule;
            if (isNippleRule) {
                btnToggleNipple.setText("Nipple Rule Enabled (60% Max Fill)");
                btnToggleNipple.setTextColor(getColor(R.color.accent_primary));
            } else {
                btnToggleNipple.setText("Standard Raceway (40% Max Fill)");
                btnToggleNipple.setTextColor(getColor(R.color.text_secondary));
            }
            recalculate();
        });

        btnSaveRun.setOnClickListener(v -> saveCurrentRun());
        btnCopyReport.setOnClickListener(v -> copyReportToClipboard());
        btnClearRuns.setOnClickListener(v -> clearAllRuns());
    }

    private void setRacewayType(int type) {
        this.racewayType = type;
        btnTypeEmt.setBackgroundResource(type == ConduitEngine.RACEWAY_EMT ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnTypeEmt.setTextColor(getColor(type == ConduitEngine.RACEWAY_EMT ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnTypePvc40.setBackgroundResource(type == ConduitEngine.RACEWAY_PVC_40 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnTypePvc40.setTextColor(getColor(type == ConduitEngine.RACEWAY_PVC_40 ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnTypePvc80.setBackgroundResource(type == ConduitEngine.RACEWAY_PVC_80 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnTypePvc80.setTextColor(getColor(type == ConduitEngine.RACEWAY_PVC_80 ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnTypeRmc.setBackgroundResource(type == ConduitEngine.RACEWAY_RMC ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnTypeRmc.setTextColor(getColor(type == ConduitEngine.RACEWAY_RMC ? R.color.chip_text_selected : R.color.chip_text_unselected));

        recalculate();
    }

    private void setInsulationType(int type) {
        this.insulationType = type;
        btnInsThhn.setBackgroundResource(type == ConduitEngine.INSULATION_THHN ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnInsThhn.setTextColor(getColor(type == ConduitEngine.INSULATION_THHN ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnInsXhhw.setBackgroundResource(type == ConduitEngine.INSULATION_XHHW ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnInsXhhw.setTextColor(getColor(type == ConduitEngine.INSULATION_XHHW ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnInsRhw.setBackgroundResource(type == ConduitEngine.INSULATION_RHW ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnInsRhw.setTextColor(getColor(type == ConduitEngine.INSULATION_RHW ? R.color.chip_text_selected : R.color.chip_text_unselected));

        recalculate();
    }

    private void recalculate() {
        lastResult = ConduitEngine.calculate(
                racewayType,
                conduitSizeIdx,
                insulationType,
                primaryGaugeIdx,
                primaryCount,
                groundGaugeIdx,
                groundCount,
                isNippleRule
        );

        String conduitShortName = (racewayType == ConduitEngine.RACEWAY_EMT) ? "EMT" :
                (racewayType == ConduitEngine.RACEWAY_PVC_40) ? "PVC-40" :
                (racewayType == ConduitEngine.RACEWAY_PVC_80) ? "PVC-80" : "RMC";
        String conduitLabel = ConduitEngine.TRADE_SIZES[conduitSizeIdx] + " " + conduitShortName;
        tvConduitSizeLabel.setText(conduitLabel + " (ID: " + String.format(Locale.US, "%.3f\"", lastResult.conduitId) + ")");

        tvPrimaryGauge.setText(ConduitEngine.WIRE_GAUGES[primaryGaugeIdx]);
        tvPrimaryCount.setText(String.valueOf(primaryCount));

        tvGroundGauge.setText("GND: " + ConduitEngine.WIRE_GAUGES[groundGaugeIdx]);
        tvGroundCount.setText(String.valueOf(groundCount));

        conduitView.updateTelemetry(
                lastResult.actualFillPercent,
                lastResult.allowablePercent,
                primaryCount,
                groundCount,
                lastResult.conduitId,
                conduitLabel
        );

        tvComplianceStatus.setText(lastResult.complianceStatus);
        if ("CODE COMPLIANT".equals(lastResult.complianceStatus)) {
            tvComplianceStatus.setBackgroundResource(R.drawable.bg_badge_pass);
            tvComplianceStatus.setTextColor(getColor(R.color.badge_text_pass));
        } else if ("OVERFILL VIOLATION".equals(lastResult.complianceStatus)) {
            tvComplianceStatus.setBackgroundResource(R.drawable.bg_badge_hazard);
            tvComplianceStatus.setTextColor(getColor(R.color.badge_text_hazard));
        } else {
            tvComplianceStatus.setBackgroundResource(R.drawable.bg_badge_alert);
            tvComplianceStatus.setTextColor(getColor(R.color.badge_text_alert));
        }

        if ("CRITICAL JAM RISK".equals(lastResult.jamStatus)) {
            tvJamStatus.setText(String.format(Locale.US, "Jam Risk (J: %.1f)", lastResult.jamRatio));
            tvJamStatus.setBackgroundResource(R.drawable.bg_badge_hazard);
            tvJamStatus.setTextColor(getColor(R.color.badge_text_hazard));
        } else {
            tvJamStatus.setText(String.format(Locale.US, "Safe Pull (J: %.1f)", lastResult.jamRatio));
            tvJamStatus.setBackgroundResource(R.drawable.bg_badge_pass);
            tvJamStatus.setTextColor(getColor(R.color.badge_text_pass));
        }

        tvStraightPull.setText(String.format(Locale.US, "%.1f\" Length (8 × D)", lastResult.straightPullBoxInches));
        tvAnglePull.setText(String.format(Locale.US, "%.1f\" Distance (6 × D)", lastResult.anglePullBoxInches));

        tvAreaBreakdown.setText("Conductors: " + lastResult.formattedWireArea + "  •  Conduit Area: " + lastResult.formattedConduitArea + "  •  Remain: " + lastResult.formattedRemaining);
    }

    private void saveCurrentRun() {
        if (lastResult == null) recalculate();

        String conduitShortName = (racewayType == ConduitEngine.RACEWAY_EMT) ? "EMT" :
                (racewayType == ConduitEngine.RACEWAY_PVC_40) ? "PVC-40" :
                (racewayType == ConduitEngine.RACEWAY_PVC_80) ? "PVC-80" : "RMC";
        String conduitDesc = ConduitEngine.TRADE_SIZES[conduitSizeIdx] + " " + conduitShortName;

        String insName = (insulationType == ConduitEngine.INSULATION_THHN) ? "THHN" :
                (insulationType == ConduitEngine.INSULATION_XHHW) ? "XHHW" : "RHW";
        String wiresDesc = primaryCount + " × " + ConduitEngine.WIRE_GAUGES[primaryGaugeIdx] + " " + insName;
        if (groundCount > 0) {
            wiresDesc += " + " + groundCount + " × " + ConduitEngine.WIRE_GAUGES[groundGaugeIdx] + " GND";
        }

        ConduitDatabaseHelper.RunRecord rec = new ConduitDatabaseHelper.RunRecord();
        rec.timestamp = System.currentTimeMillis();
        rec.conduitDesc = conduitDesc;
        rec.wiresDesc = wiresDesc;
        rec.fillPercent = lastResult.actualFillPercent;
        rec.maxPercent = lastResult.allowablePercent;
        rec.jamRatio = lastResult.jamRatio;
        rec.status = lastResult.complianceStatus;

        dbHelper.insertRun(rec);
        Toast.makeText(this, "Saved Run: " + conduitDesc + " (" + String.format(Locale.US, "%.1f%%", rec.fillPercent) + ")", Toast.LENGTH_SHORT).show();
        refreshRunsList();
    }

    private void refreshRunsList() {
        layoutRunsList.removeAllViews();
        List<ConduitDatabaseHelper.RunRecord> list = dbHelper.getAllRuns();

        if (list.isEmpty()) {
            tvEmptyRuns.setVisibility(View.VISIBLE);
        } else {
            tvEmptyRuns.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (ConduitDatabaseHelper.RunRecord rec : list) {
                View itemView = inflater.inflate(R.layout.item_conduit_run, layoutRunsList, false);

                TextView itemTvConduit = itemView.findViewById(R.id.itemTvConduit);
                TextView itemTvStatus = itemView.findViewById(R.id.itemTvStatus);
                TextView itemTvWires = itemView.findViewById(R.id.itemTvWires);
                TextView itemTvJam = itemView.findViewById(R.id.itemTvJam);
                ImageButton itemBtnDelete = itemView.findViewById(R.id.itemBtnDelete);

                itemTvConduit.setText(rec.conduitDesc + " (" + String.format(Locale.US, "%.1f%% Fill", rec.fillPercent) + ")");
                itemTvStatus.setText(rec.status);

                if ("CODE COMPLIANT".equals(rec.status)) {
                    itemTvStatus.setBackgroundResource(R.drawable.bg_badge_pass);
                    itemTvStatus.setTextColor(getColor(R.color.badge_text_pass));
                } else if ("OVERFILL VIOLATION".equals(rec.status)) {
                    itemTvStatus.setBackgroundResource(R.drawable.bg_badge_hazard);
                    itemTvStatus.setTextColor(getColor(R.color.badge_text_hazard));
                } else {
                    itemTvStatus.setBackgroundResource(R.drawable.bg_badge_alert);
                    itemTvStatus.setTextColor(getColor(R.color.badge_text_alert));
                }

                itemTvWires.setText(rec.wiresDesc);
                itemTvJam.setText(String.format(Locale.US, "Jam Ratio: %.1f", rec.jamRatio));

                itemBtnDelete.setOnClickListener(v -> {
                    dbHelper.deleteRun(rec.id);
                    refreshRunsList();
                });

                layoutRunsList.addView(itemView);
            }
        }
    }

    private void clearAllRuns() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Conduit Runs")
                .setMessage("Are you sure you want to clear all logged conduit fill runs?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    dbHelper.clearAllRuns();
                    refreshRunsList();
                    Toast.makeText(this, "All runs cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void copyReportToClipboard() {
        if (lastResult == null) recalculate();

        String conduitShortName = (racewayType == ConduitEngine.RACEWAY_EMT) ? "EMT" :
                (racewayType == ConduitEngine.RACEWAY_PVC_40) ? "PVC-40" :
                (racewayType == ConduitEngine.RACEWAY_PVC_80) ? "PVC-80" : "RMC";
        String insName = (insulationType == ConduitEngine.INSULATION_THHN) ? "THHN" :
                (insulationType == ConduitEngine.INSULATION_XHHW) ? "XHHW" : "RHW";

        String report = "ConduitFill Engineering Report (NEC Chapter 9)\n"
                + "--------------------------------------------\n"
                + "Raceway: " + ConduitEngine.TRADE_SIZES[conduitSizeIdx] + " " + conduitShortName + " (ID: " + String.format(Locale.US, "%.3f\"", lastResult.conduitId) + ")\n"
                + "Primary Conductors: " + primaryCount + " × " + ConduitEngine.WIRE_GAUGES[primaryGaugeIdx] + " " + insName + "\n"
                + "Ground Conductors: " + groundCount + " × " + ConduitEngine.WIRE_GAUGES[groundGaugeIdx] + " " + insName + "\n"
                + "Actual Fill: " + lastResult.formattedFill + " (Allowable: " + lastResult.formattedAllowed + ")\n"
                + "Compliance: " + lastResult.complianceStatus + "\n"
                + "Jam Ratio (D/d): " + String.format(Locale.US, "%.2f", lastResult.jamRatio) + " (" + lastResult.jamStatus + ")\n"
                + "Straight Pull Box: " + String.format(Locale.US, "%.1f\"", lastResult.straightPullBoxInches) + " (NEC 314.28)\n"
                + "Angle/U Pull Box: " + String.format(Locale.US, "%.1f\"", lastResult.anglePullBoxInches) + "\n"
                + "https://apps.kimrosil.com/conduitfill/";

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("ConduitFill Report", report);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Report copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void cycleTheme() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String current = sp.getString(KEY_THEME, "auto");
        String next = "dark";
        if ("dark".equals(current)) next = "light";
        else if ("light".equals(current)) next = "auto";
        else next = "dark";

        sp.edit().putString(KEY_THEME, next).apply();
        recreate();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        Button btnThemeDark = dialogView.findViewById(R.id.btnThemeDark);
        Button btnThemeLight = dialogView.findViewById(R.id.btnThemeLight);
        Button btnThemeAuto = dialogView.findViewById(R.id.btnThemeAuto);
        Button btnDialogClose = dialogView.findViewById(R.id.btnDialogClose);

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        btnThemeDark.setOnClickListener(v -> {
            sp.edit().putString(KEY_THEME, "dark").apply();
            dialog.dismiss();
            recreate();
        });
        btnThemeLight.setOnClickListener(v -> {
            sp.edit().putString(KEY_THEME, "light").apply();
            dialog.dismiss();
            recreate();
        });
        btnThemeAuto.setOnClickListener(v -> {
            sp.edit().putString(KEY_THEME, "auto").apply();
            dialog.dismiss();
            recreate();
        });

        btnDialogClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
