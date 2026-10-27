package com.kimrosil.decigauge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements DeciEngine.Listener {

    private static final String PREFS_NAME = "deci_prefs";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_CALIBRATION = "calibration_offset";
    private static final int REQ_MIC_PERMISSION = 101;

    private DeciEngine engine;
    private DeciDatabaseHelper dbHelper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private DeciMeterView deciMeterView;
    private TextView tvLeqValue;
    private TextView tvMaxValue;
    private TextView tvMinValue;
    private TextView tvPeakValue;
    private TextView tvDosePercent;
    private TextView tvSafeTimeRemaining;
    private TextView tvRiskStatus;
    private TextView tvStandardRule;

    private Button btnWeightA;
    private Button btnWeightC;
    private Button btnWeightZ;
    private Button btnRespFast;
    private Button btnRespSlow;
    private Button btnRespImpulse;
    private Button btnStdNiosh;
    private Button btnStdOsha;

    private Button btnPlayPause;
    private Button btnReset;
    private Button btnSave;
    private Button btnCopy;
    private Button btnClearSessions;

    private LinearLayout layoutMicNotice;
    private Button btnGrantMic;
    private LinearLayout layoutSessionsList;
    private TextView tvEmptySessions;

    // Latest snapshot for saving and copying
    private double lastCurrentDb = 40.0;
    private double lastLeqDb = 40.0;
    private double lastMaxDb = 40.0;
    private double lastMinDb = 40.0;
    private double lastPeakDb = 40.0;
    private double lastDose = 0.0;
    private String lastStatus = "SAFE";
    private long sessionStartMs = System.currentTimeMillis();
    private long lastTextUpdateTime = 0;

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

        dbHelper = new DeciDatabaseHelper(this);
        engine = new DeciEngine();
        engine.setListener(this);

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float calOffset = sp.getFloat(KEY_CALIBRATION, 0.0f);
        engine.setCalibrationOffset(calOffset);

        bindViews();
        setupListeners();
        checkMicPermission();
        refreshSessionsList();
    }

    private void bindViews() {
        deciMeterView = findViewById(R.id.deciMeterView);
        tvLeqValue = findViewById(R.id.tvLeqValue);
        tvMaxValue = findViewById(R.id.tvMaxValue);
        tvMinValue = findViewById(R.id.tvMinValue);
        tvPeakValue = findViewById(R.id.tvPeakValue);
        tvDosePercent = findViewById(R.id.tvDosePercent);
        tvSafeTimeRemaining = findViewById(R.id.tvSafeTimeRemaining);
        tvRiskStatus = findViewById(R.id.tvRiskStatus);
        tvStandardRule = findViewById(R.id.tvStandardRule);

        btnWeightA = findViewById(R.id.btnWeightA);
        btnWeightC = findViewById(R.id.btnWeightC);
        btnWeightZ = findViewById(R.id.btnWeightZ);

        btnRespFast = findViewById(R.id.btnRespFast);
        btnRespSlow = findViewById(R.id.btnRespSlow);
        btnRespImpulse = findViewById(R.id.btnRespImpulse);

        btnStdNiosh = findViewById(R.id.btnStdNiosh);
        btnStdOsha = findViewById(R.id.btnStdOsha);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnReset = findViewById(R.id.btnReset);
        btnSave = findViewById(R.id.btnSave);
        btnCopy = findViewById(R.id.btnCopy);
        btnClearSessions = findViewById(R.id.btnClearSessions);

        layoutMicNotice = findViewById(R.id.layoutMicNotice);
        btnGrantMic = findViewById(R.id.btnGrantMic);
        layoutSessionsList = findViewById(R.id.layoutSessionsList);
        tvEmptySessions = findViewById(R.id.tvEmptySessions);

        ImageButton btnTheme = findViewById(R.id.btnTheme);
        ImageButton btnSettings = findViewById(R.id.btnSettings);

        btnTheme.setOnClickListener(v -> cycleTheme());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void setupListeners() {
        btnWeightA.setOnClickListener(v -> {
            engine.setWeightingMode(DeciEngine.WEIGHTING_A);
            updateWeightingButtons();
        });
        btnWeightC.setOnClickListener(v -> {
            engine.setWeightingMode(DeciEngine.WEIGHTING_C);
            updateWeightingButtons();
        });
        btnWeightZ.setOnClickListener(v -> {
            engine.setWeightingMode(DeciEngine.WEIGHTING_Z);
            updateWeightingButtons();
        });

        btnRespFast.setOnClickListener(v -> {
            engine.setResponseMode(DeciEngine.RESPONSE_FAST);
            updateResponseButtons();
        });
        btnRespSlow.setOnClickListener(v -> {
            engine.setResponseMode(DeciEngine.RESPONSE_SLOW);
            updateResponseButtons();
        });
        btnRespImpulse.setOnClickListener(v -> {
            engine.setResponseMode(DeciEngine.RESPONSE_IMPULSE);
            updateResponseButtons();
        });

        btnStdNiosh.setOnClickListener(v -> {
            engine.setStandardMode(DeciEngine.STANDARD_NIOSH);
            updateStandardButtons();
        });
        btnStdOsha.setOnClickListener(v -> {
            engine.setStandardMode(DeciEngine.STANDARD_OSHA);
            updateStandardButtons();
        });

        btnPlayPause.setOnClickListener(v -> {
            if (!engine.isRunning()) {
                engine.start();
                btnPlayPause.setText("Pause");
            } else if (engine.isPaused()) {
                engine.resume();
                btnPlayPause.setText("Pause");
            } else {
                engine.pause();
                btnPlayPause.setText("Resume");
            }
        });

        btnReset.setOnClickListener(v -> {
            engine.resetMeasurements();
            sessionStartMs = System.currentTimeMillis();
            Toast.makeText(this, "Peak & Baseline Reset", Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> saveCurrentSession());
        btnCopy.setOnClickListener(v -> copyMetricsToClipboard());
        btnClearSessions.setOnClickListener(v -> clearAllSessions());

        btnGrantMic.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERMISSION);
            }
        });
    }

    private void checkMicPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                layoutMicNotice.setVisibility(View.VISIBLE);
            } else {
                layoutMicNotice.setVisibility(View.GONE);
            }
        } else {
            layoutMicNotice.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                layoutMicNotice.setVisibility(View.GONE);
                engine.stop();
                engine.start();
                Toast.makeText(this, "Microphone enabled", Toast.LENGTH_SHORT).show();
            } else {
                layoutMicNotice.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Microphone access denied. Using test mode.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (engine.isRunning()) {
            engine.pause();
            btnPlayPause.setText("Resume");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) {
            engine.stop();
        }
    }

    @Override
    public void onAcousticUpdate(
            double currentDb,
            double leqDb,
            double maxDb,
            double minDb,
            double peakDb,
            double dosePercent,
            double safeTimeRemainingSec,
            String riskStatus,
            float[] spectrumBands
    ) {
        lastCurrentDb = currentDb;
        lastLeqDb = leqDb;
        lastMaxDb = maxDb;
        lastMinDb = minDb;
        lastPeakDb = peakDb;
        lastDose = dosePercent;
        lastStatus = riskStatus;

        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) return;

            // Formatted telemetry labels
            String weightStr = (engine.getWeightingMode() == DeciEngine.WEIGHTING_A) ? "dBA" :
                    (engine.getWeightingMode() == DeciEngine.WEIGHTING_C) ? "dBC" : "dBZ";
            String respStr = (engine.getResponseMode() == DeciEngine.RESPONSE_FAST) ? "FAST" :
                    (engine.getResponseMode() == DeciEngine.RESPONSE_SLOW) ? "SLOW" : "IMP";
            String headerBadge = weightStr + " • " + respStr;

            deciMeterView.updateTelemetry((float) currentDb, (float) peakDb, (float) leqDb, headerBadge, spectrumBands);

            long nowMs = android.os.SystemClock.elapsedRealtime();
            if (nowMs - lastTextUpdateTime >= 400) {
                lastTextUpdateTime = nowMs;

                tvLeqValue.setText(String.format(Locale.US, "%.1f", leqDb));
                tvMaxValue.setText(String.format(Locale.US, "%.1f", maxDb));
                tvMinValue.setText(String.format(Locale.US, "%.1f", minDb));
                tvPeakValue.setText(String.format(Locale.US, "%.1f", peakDb));

                tvDosePercent.setText(String.format(Locale.US, "%.1f%%", dosePercent));
                tvRiskStatus.setText(riskStatus);

                // Risk badge color
                if ("SAFE".equals(riskStatus)) {
                    tvRiskStatus.setBackgroundResource(R.drawable.bg_badge_safe);
                    tvRiskStatus.setTextColor(getColor(R.color.badge_safe_text));
                } else if ("CAUTION".equals(riskStatus)) {
                    tvRiskStatus.setBackgroundResource(R.drawable.bg_badge_caution);
                    tvRiskStatus.setTextColor(getColor(R.color.badge_caution_text));
                } else if ("ACTION REQ".equals(riskStatus)) {
                    tvRiskStatus.setBackgroundResource(R.drawable.bg_badge_action);
                    tvRiskStatus.setTextColor(getColor(R.color.badge_action_text));
                } else {
                    tvRiskStatus.setBackgroundResource(R.drawable.bg_badge_hazard);
                    tvRiskStatus.setTextColor(getColor(R.color.badge_hazard_text));
                }

                // Safe remaining time format
                if (safeTimeRemainingSec >= 28800) {
                    tvSafeTimeRemaining.setText("> 8 Hours");
                } else if (safeTimeRemainingSec <= 0) {
                    tvSafeTimeRemaining.setText("Limit Exceeded");
                } else {
                    int hours = (int) (safeTimeRemainingSec / 3600);
                    int mins = (int) ((safeTimeRemainingSec % 3600) / 60);
                    if (hours > 0) {
                        tvSafeTimeRemaining.setText(String.format(Locale.US, "%dh %02dm", hours, mins));
                    } else {
                        int secs = (int) (safeTimeRemainingSec % 60);
                        tvSafeTimeRemaining.setText(String.format(Locale.US, "%02dm %02ds", mins, secs));
                    }
                }
            }
        });
    }

    private void updateWeightingButtons() {
        int mode = engine.getWeightingMode();
        btnWeightA.setBackgroundResource(mode == DeciEngine.WEIGHTING_A ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnWeightA.setTextColor(getColor(mode == DeciEngine.WEIGHTING_A ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnWeightC.setBackgroundResource(mode == DeciEngine.WEIGHTING_C ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnWeightC.setTextColor(getColor(mode == DeciEngine.WEIGHTING_C ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnWeightZ.setBackgroundResource(mode == DeciEngine.WEIGHTING_Z ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnWeightZ.setTextColor(getColor(mode == DeciEngine.WEIGHTING_Z ? R.color.chip_text_selected : R.color.chip_text_unselected));
    }

    private void updateResponseButtons() {
        int mode = engine.getResponseMode();
        btnRespFast.setBackgroundResource(mode == DeciEngine.RESPONSE_FAST ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnRespFast.setTextColor(getColor(mode == DeciEngine.RESPONSE_FAST ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnRespSlow.setBackgroundResource(mode == DeciEngine.RESPONSE_SLOW ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnRespSlow.setTextColor(getColor(mode == DeciEngine.RESPONSE_SLOW ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnRespImpulse.setBackgroundResource(mode == DeciEngine.RESPONSE_IMPULSE ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnRespImpulse.setTextColor(getColor(mode == DeciEngine.RESPONSE_IMPULSE ? R.color.chip_text_selected : R.color.chip_text_unselected));
    }

    private void updateStandardButtons() {
        int mode = engine.getStandardMode();
        btnStdNiosh.setBackgroundResource(mode == DeciEngine.STANDARD_NIOSH ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnStdNiosh.setTextColor(getColor(mode == DeciEngine.STANDARD_NIOSH ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnStdOsha.setBackgroundResource(mode == DeciEngine.STANDARD_OSHA ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnStdOsha.setTextColor(getColor(mode == DeciEngine.STANDARD_OSHA ? R.color.chip_text_selected : R.color.chip_text_unselected));

        if (mode == DeciEngine.STANDARD_NIOSH) {
            tvStandardRule.setText("Criterion: 85 dB / 3 dB Exchange");
        } else {
            tvStandardRule.setText("Criterion: 90 dB / 5 dB Exchange");
        }
    }

    private void saveCurrentSession() {
        DeciDatabaseHelper.SessionRecord record = new DeciDatabaseHelper.SessionRecord();
        record.timestamp = System.currentTimeMillis();
        record.durationSec = Math.max(1, (int) ((record.timestamp - sessionStartMs) / 1000));
        record.weighting = (engine.getWeightingMode() == DeciEngine.WEIGHTING_A) ? "dBA" :
                (engine.getWeightingMode() == DeciEngine.WEIGHTING_C) ? "dBC" : "dBZ";
        record.standard = (engine.getStandardMode() == DeciEngine.STANDARD_NIOSH) ? "NIOSH" : "OSHA";
        record.leqDb = lastLeqDb;
        record.maxDb = lastMaxDb;
        record.minDb = lastMinDb;
        record.dosePercent = lastDose;
        record.status = lastStatus;

        dbHelper.insertSession(record);
        Toast.makeText(this, "Session Run Saved (" + record.weighting + " " + String.format(Locale.US, "%.1f", record.leqDb) + " dB)", Toast.LENGTH_SHORT).show();
        refreshSessionsList();
    }

    private void refreshSessionsList() {
        layoutSessionsList.removeAllViews();
        List<DeciDatabaseHelper.SessionRecord> list = dbHelper.getAllSessions();

        if (list.isEmpty()) {
            tvEmptySessions.setVisibility(View.VISIBLE);
        } else {
            tvEmptySessions.setVisibility(View.GONE);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());
            LayoutInflater inflater = LayoutInflater.from(this);

            for (DeciDatabaseHelper.SessionRecord rec : list) {
                View itemView = inflater.inflate(R.layout.item_deci_session, layoutSessionsList, false);

                TextView tvHeader = itemView.findViewById(R.id.itemTvHeader);
                TextView tvStatus = itemView.findViewById(R.id.itemTvStatus);
                TextView tvMetrics = itemView.findViewById(R.id.itemTvMetrics);
                TextView tvTimestamp = itemView.findViewById(R.id.itemTvTimestamp);
                ImageButton btnDelete = itemView.findViewById(R.id.itemBtnDelete);

                tvHeader.setText(rec.weighting + " • " + rec.standard);
                tvStatus.setText(rec.status);

                if ("SAFE".equals(rec.status)) {
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_safe);
                    tvStatus.setTextColor(getColor(R.color.badge_safe_text));
                } else if ("CAUTION".equals(rec.status)) {
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_caution);
                    tvStatus.setTextColor(getColor(R.color.badge_caution_text));
                } else if ("ACTION REQ".equals(rec.status)) {
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_action);
                    tvStatus.setTextColor(getColor(R.color.badge_action_text));
                } else {
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_hazard);
                    tvStatus.setTextColor(getColor(R.color.badge_hazard_text));
                }

                tvMetrics.setText(String.format(Locale.US, "LEQ: %.1f dB  •  MAX: %.1f dB  •  DOSE: %.1f%%", rec.leqDb, rec.maxDb, rec.dosePercent));
                tvTimestamp.setText(sdf.format(new Date(rec.timestamp)) + " • " + rec.durationSec + "s duration");

                btnDelete.setOnClickListener(v -> {
                    dbHelper.deleteSession(rec.id);
                    refreshSessionsList();
                });

                layoutSessionsList.addView(itemView);
            }
        }
    }

    private void clearAllSessions() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Sessions")
                .setMessage("Are you sure you want to clear all logged acoustic runs?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    dbHelper.clearAllSessions();
                    refreshSessionsList();
                    Toast.makeText(this, "All sessions cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void copyMetricsToClipboard() {
        String weightStr = (engine.getWeightingMode() == DeciEngine.WEIGHTING_A) ? "dBA" :
                (engine.getWeightingMode() == DeciEngine.WEIGHTING_C) ? "dBC" : "dBZ";
        String stdStr = (engine.getStandardMode() == DeciEngine.STANDARD_NIOSH) ? "NIOSH (85dB/3dB)" : "OSHA (90dB/5dB)";

        String text = "DeciGauge Acoustic Telemetry Report\n"
                + "-----------------------------------\n"
                + "Weighting: " + weightStr + "\n"
                + "Standard: " + stdStr + "\n"
                + "Current SPL: " + String.format(Locale.US, "%.1f dB", lastCurrentDb) + "\n"
                + "Equivalent (Leq): " + String.format(Locale.US, "%.1f dB", lastLeqDb) + "\n"
                + "Max Level: " + String.format(Locale.US, "%.1f dB", lastMaxDb) + "\n"
                + "Min Baseline: " + String.format(Locale.US, "%.1f dB", lastMinDb) + "\n"
                + "Peak Hold: " + String.format(Locale.US, "%.1f dB", lastPeakDb) + "\n"
                + "Accumulated Dose: " + String.format(Locale.US, "%.1f%%", lastDose) + "\n"
                + "Risk Status: " + lastStatus + "\n"
                + "Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "\n"
                + "https://apps.kimrosil.com/decigauge/";

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("DeciGauge Telemetry", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Telemetry copied to clipboard", Toast.LENGTH_SHORT).show();
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

        Button btnCalMinus = dialogView.findViewById(R.id.btnCalMinus);
        Button btnCalPlus = dialogView.findViewById(R.id.btnCalPlus);
        Button btnCalReset = dialogView.findViewById(R.id.btnCalReset);
        TextView tvCalValue = dialogView.findViewById(R.id.tvCalValue);
        Button btnDialogClose = dialogView.findViewById(R.id.btnDialogClose);

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final float[] cal = {sp.getFloat(KEY_CALIBRATION, 0.0f)};
        tvCalValue.setText(String.format(Locale.US, "%+.1f dB", cal[0]));

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

        btnCalMinus.setOnClickListener(v -> {
            cal[0] = Math.max(-20.0f, cal[0] - 0.5f);
            sp.edit().putFloat(KEY_CALIBRATION, cal[0]).apply();
            engine.setCalibrationOffset(cal[0]);
            tvCalValue.setText(String.format(Locale.US, "%+.1f dB", cal[0]));
        });

        btnCalPlus.setOnClickListener(v -> {
            cal[0] = Math.min(20.0f, cal[0] + 0.5f);
            sp.edit().putFloat(KEY_CALIBRATION, cal[0]).apply();
            engine.setCalibrationOffset(cal[0]);
            tvCalValue.setText(String.format(Locale.US, "%+.1f dB", cal[0]));
        });

        btnCalReset.setOnClickListener(v -> {
            cal[0] = 0.0f;
            sp.edit().putFloat(KEY_CALIBRATION, cal[0]).apply();
            engine.setCalibrationOffset(0.0);
            tvCalValue.setText("+0.0 dB");
        });

        btnDialogClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
