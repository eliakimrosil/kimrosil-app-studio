package com.kimrosil.rebarlap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
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

    private static final String PREFS_NAME = "rebarlap_prefs";
    private static final String KEY_THEME = "theme_mode";

    private TextView tvClassBLap;
    private TextView tvSpliceClassBadge;
    private TextView tvDevLength;
    private TextView tvCompLap;
    private TextView tvHook90;
    private TextView tvPinDia;
    private RebarSpliceView rebarView;
    private Button btnCopySchedule;
    private Button btnSaveDetail;
    private ImageButton btnTheme;
    private ImageButton btnSettings;

    private LinearLayout layoutBarChips;
    private Button btnFc3000;
    private Button btnFc4000;
    private Button btnFc5000;
    private Button btnFc6000;

    private Button btnGrade60;
    private Button btnGrade80;

    private Button btnToggleTopBar;
    private Button btnToggleEpoxy;

    private LinearLayout layoutRecentList;
    private TextView tvRecordCount;

    private RebarDatabaseHelper dbHelper;
    private List<RebarEngine.BarSpec> standardBars;
    private RebarEngine.BarSpec currentBar;
    private double currentFc = 4000.0;
    private double currentFy = 60000.0;
    private boolean isTopBar = false;
    private boolean isEpoxy = false;
    private RebarEngine.SpliceResult lastResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyStoredTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new RebarDatabaseHelper(this);
        standardBars = RebarEngine.getStandardBars();
        currentBar = standardBars.get(2); // Default: #5 (16M)

        initViews();
        setupBars();
        setupListeners();
        loadRecentRecords();

        recalculate();
    }

    private void applyStoredTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME, 0);
        int nightMode;
        if (mode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
        }

        if (nightMode != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            Configuration config = getResources().getConfiguration();
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }
    }

    private void initViews() {
        tvClassBLap = findViewById(R.id.tvClassBLap);
        tvSpliceClassBadge = findViewById(R.id.tvSpliceClassBadge);
        tvDevLength = findViewById(R.id.tvDevLength);
        tvCompLap = findViewById(R.id.tvCompLap);
        tvHook90 = findViewById(R.id.tvHook90);
        tvPinDia = findViewById(R.id.tvPinDia);
        rebarView = findViewById(R.id.rebarView);
        btnCopySchedule = findViewById(R.id.btnCopySchedule);
        btnSaveDetail = findViewById(R.id.btnSaveDetail);
        btnTheme = findViewById(R.id.btnTheme);
        btnSettings = findViewById(R.id.btnSettings);

        layoutBarChips = findViewById(R.id.layoutBarChips);
        btnFc3000 = findViewById(R.id.btnFc3000);
        btnFc4000 = findViewById(R.id.btnFc4000);
        btnFc5000 = findViewById(R.id.btnFc5000);
        btnFc6000 = findViewById(R.id.btnFc6000);

        btnGrade60 = findViewById(R.id.btnGrade60);
        btnGrade80 = findViewById(R.id.btnGrade80);

        btnToggleTopBar = findViewById(R.id.btnToggleTopBar);
        btnToggleEpoxy = findViewById(R.id.btnToggleEpoxy);

        layoutRecentList = findViewById(R.id.layoutRecentList);
        tvRecordCount = findViewById(R.id.tvRecordCount);
    }

    private void setupBars() {
        layoutBarChips.removeAllViews();
        for (int i = 0; i < standardBars.size(); i++) {
            final RebarEngine.BarSpec b = standardBars.get(i);
            Button btn = new Button(this);
            btn.setText(b.name);
            btn.setTextSize(13);
            btn.setContentDescription("Select rebar size " + b.name);
            btn.setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(48)
            );
            lp.setMarginEnd(dpToPx(6));
            btn.setLayoutParams(lp);

            if (b == currentBar) {
                btn.setBackgroundResource(R.drawable.bg_chip_selected);
                btn.setTextColor(0xFFFFFFFF);
            } else {
                btn.setBackgroundResource(R.drawable.bg_chip_unselected);
                btn.setTextColor(getColor(R.color.colorTextPrimary));
            }

            btn.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                currentBar = b;
                refreshBarChips();
                recalculate();
            });

            layoutBarChips.addView(btn);
        }
    }

    private void refreshBarChips() {
        int count = layoutBarChips.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = layoutBarChips.getChildAt(i);
            if (v instanceof Button) {
                Button b = (Button) v;
                RebarEngine.BarSpec bar = standardBars.get(i);
                if (bar == currentBar) {
                    b.setBackgroundResource(R.drawable.bg_chip_selected);
                    b.setTextColor(0xFFFFFFFF);
                } else {
                    b.setBackgroundResource(R.drawable.bg_chip_unselected);
                    b.setTextColor(getColor(R.color.colorTextPrimary));
                }
            }
        }
    }

    private void setupListeners() {
        // Theme switcher
        btnTheme.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int currentMode = prefs.getInt(KEY_THEME, 0);
            int nextMode = (currentMode + 1) % 3;
            setThemePreference(nextMode);
            String modeName = (nextMode == 1) ? "Light Mode" : (nextMode == 2 ? "Dark Mode" : "Auto (System)");
            Toast.makeText(MainActivity.this, "Theme: " + modeName, Toast.LENGTH_SHORT).show();
            recreate();
        });

        // Concrete strengths
        btnFc3000.setOnClickListener(v -> setConcreteStrength(3000.0));
        btnFc4000.setOnClickListener(v -> setConcreteStrength(4000.0));
        btnFc5000.setOnClickListener(v -> setConcreteStrength(5000.0));
        btnFc6000.setOnClickListener(v -> setConcreteStrength(6000.0));

        // Steel grades
        btnGrade60.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            currentFy = 60000.0;
            btnGrade60.setBackgroundResource(R.drawable.bg_chip_selected);
            btnGrade60.setTextColor(0xFFFFFFFF);
            btnGrade80.setBackgroundResource(R.drawable.bg_chip_unselected);
            btnGrade80.setTextColor(getColor(R.color.colorTextPrimary));
            recalculate();
        });

        btnGrade80.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            currentFy = 80000.0;
            btnGrade80.setBackgroundResource(R.drawable.bg_chip_selected);
            btnGrade80.setTextColor(0xFFFFFFFF);
            btnGrade60.setBackgroundResource(R.drawable.bg_chip_unselected);
            btnGrade60.setTextColor(getColor(R.color.colorTextPrimary));
            recalculate();
        });

        // Placement conditions
        btnToggleTopBar.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            isTopBar = !isTopBar;
            btnToggleTopBar.setBackgroundResource(isTopBar ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            btnToggleTopBar.setTextColor(isTopBar ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));
            recalculate();
        });

        btnToggleEpoxy.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            isEpoxy = !isEpoxy;
            btnToggleEpoxy.setBackgroundResource(isEpoxy ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            btnToggleEpoxy.setTextColor(isEpoxy ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));
            recalculate();
        });

        // Actions
        btnCopySchedule.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            copyScheduleToClipboard();
        });
        btnSaveDetail.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            saveDetailToDatabase();
        });
        btnSettings.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            showSettingsDialog();
        });
    }

    private void setConcreteStrength(double fc) {
        currentFc = fc;
        btnFc3000.setBackgroundResource(fc == 3000 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnFc3000.setTextColor(fc == 3000 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnFc4000.setBackgroundResource(fc == 4000 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnFc4000.setTextColor(fc == 4000 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnFc5000.setBackgroundResource(fc == 5000 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnFc5000.setTextColor(fc == 5000 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        btnFc6000.setBackgroundResource(fc == 6000 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnFc6000.setTextColor(fc == 6000 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

        recalculate();
    }

    private void recalculate() {
        lastResult = RebarEngine.calculate(currentBar, currentFc, currentFy, isTopBar, isEpoxy);

        tvClassBLap.setText(lastResult.getFormattedClassBLap());
        tvDevLength.setText(String.format(Locale.US, "%.1f in", lastResult.ldInches));
        tvCompLap.setText(lastResult.getFormattedCompLap());
        tvHook90.setText(String.format(Locale.US, "90° Hook: %.1f in", lastResult.hook90ExtInches));
        tvPinDia.setText(String.format(Locale.US, "Pin Dia: %.1f in", lastResult.pinDiaInches));

        rebarView.updateSplice(currentBar.name, lastResult.classBLapInches, currentBar.diaInches);
    }

    private void copyScheduleToClipboard() {
        if (lastResult == null) return;
        String report = lastResult.getSummary();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("RebarLap ACI 318-19 Splice Schedule", report);
        if (cm != null) {
            cm.setPrimaryClip(clip);
            Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveDetailToDatabase() {
        if (lastResult == null) return;
        dbHelper.insertRecord(
                currentBar.name,
                lastResult.fcPsi,
                lastResult.fyPsi,
                lastResult.isTopBar,
                lastResult.isEpoxyCoated,
                lastResult.classBLapInches,
                lastResult.ldInches
        );
        Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show();
        loadRecentRecords();
    }

    private void loadRecentRecords() {
        layoutRecentList.removeAllViews();
        List<RebarDatabaseHelper.RebarRecord> records = dbHelper.getAllRecords();
        tvRecordCount.setText(String.format(Locale.US, "%d splices", records.size()));

        int maxShow = Math.min(5, records.size());
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < maxShow; i++) {
            RebarDatabaseHelper.RebarRecord r = records.get(i);
            View itemView = inflater.inflate(R.layout.item_rebar_splice, layoutRecentList, false);

            TextView tvName = itemView.findViewById(R.id.tvItemBarName);
            TextView tvCond = itemView.findViewById(R.id.tvItemConditions);
            TextView tvLap = itemView.findViewById(R.id.tvItemLapB);

            tvName.setText(r.barName);
            tvCond.setText(String.format(Locale.US, "fc: %.0f psi • fy: %.0f ksi • %s",
                    r.fcPsi, r.fyPsi / 1000.0, r.isTop ? "Top Bar" : "Bottom Bar"));
            tvLap.setText(String.format(Locale.US, "%.1f in", r.classBLap));

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                for (RebarEngine.BarSpec b : standardBars) {
                    if (b.name.equals(r.barName)) {
                        currentBar = b;
                        break;
                    }
                }
                setConcreteStrength(r.fcPsi);
                currentFy = r.fyPsi;
                btnGrade60.setBackgroundResource(currentFy == 60000 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                btnGrade60.setTextColor(currentFy == 60000 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));
                btnGrade80.setBackgroundResource(currentFy == 80000 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                btnGrade80.setTextColor(currentFy == 80000 ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

                isTopBar = r.isTop;
                btnToggleTopBar.setBackgroundResource(isTopBar ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                btnToggleTopBar.setTextColor(isTopBar ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

                isEpoxy = r.isEpoxy;
                btnToggleEpoxy.setBackgroundResource(isEpoxy ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                btnToggleEpoxy.setTextColor(isEpoxy ? 0xFFFFFFFF : getColor(R.color.colorTextPrimary));

                refreshBarChips();
                recalculate();
                Toast.makeText(MainActivity.this, "Loaded " + r.barName + " Splice", Toast.LENGTH_SHORT).show();
            });

            layoutRecentList.addView(itemView);
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        Button btnAuto = dialogView.findViewById(R.id.btnThemeAuto);
        Button btnLight = dialogView.findViewById(R.id.btnThemeLight);
        Button btnDark = dialogView.findViewById(R.id.btnThemeDark);
        Button btnClear = dialogView.findViewById(R.id.btnClearHistory);
        Button btnClose = dialogView.findViewById(R.id.btnCloseSettings);

        btnAuto.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setThemePreference(0);
            dialog.dismiss();
            recreate();
        });

        btnLight.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setThemePreference(1);
            dialog.dismiss();
            recreate();
        });

        btnDark.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            setThemePreference(2);
            dialog.dismiss();
            recreate();
        });

        btnClear.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            dbHelper.clearAll();
            loadRecentRecords();
            Toast.makeText(MainActivity.this, "Saved splices cleared", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void setThemePreference(int mode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, mode).apply();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
