package com.kimrosil.skimskim;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements BackwashCycleManager.CycleListener {

    private static final String PREFS_NAME = "skim_skim_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String PREF_FILTER_AREA = "pref_filter_area";
    private static final String PREF_CLEAN_PSI = "pref_clean_psi";
    private static final String PREF_DIRTY_PSI = "pref_dirty_psi";
    private static final String PREF_IS_PRO = "is_pro_user";

    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    private BackwashCycleManager cycleManager;

    // UI Elements
    private TextView tvAppTitle;
    private Button btnThemeToggle;
    private Button btnSoundToggle;
    private Button btnWaterLockToggle;
    private Button btnHistory;

    // Hero Stage & Gauge
    private TextView tvStageBanner;
    private TextView tvTimerCountdown;
    private TextView tvStageSubtext;
    private ProgressBar progressStageGauge;

    // Control Deck
    private Button btnPlayPause;
    private Button btnAdd30s;
    private Button btnSkipStage;
    private Button btnResetCycle;

    // DE Calculator Deck
    private EditText etFilterArea;
    private SeekBar seekFilterArea;
    private TextView tvDeDosageLbs;
    private TextView tvDeDosageScoops;
    private Button btnModeFull;
    private Button btnModeBump;
    private Button btnSightGlassCheck;

    // PSI Input & Results Card
    private LinearLayout llResultCard;
    private EditText etCleanPsi;
    private EditText etDirtyPsi;
    private Button btnSaveLog;

    // Empty State Card
    private LinearLayout llEmptyState;
    private Button btnQuickStartPreset;

    // Water Lock Overlay
    private LinearLayout llWaterLockOverlay;
    private TextView tvLockTimerDisplay;
    private Button btnUnlockWater;

    // Ad Banner
    private LinearLayout llAdContainer;
    private TextView tvAdNotice;

    // States
    private boolean isPartialBumpMode = false;
    private boolean isWaterLocked = false;
    private long lastTouchTime = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = sp.getInt(PREF_THEME_MODE, 0);

        int nightMode;
        if (themeMode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int sysMode = 0;
            try {
                sysMode = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {
            }
            nightMode = (sysMode == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep screen on during backwash telemetry inspection
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        dbHelper = new DatabaseHelper(this);
        cycleManager = new BackwashCycleManager(this, this);

        initViews();
        setupListeners();
        updateThemeButtonLabel();
        applyStatusBarAppearance();
        loadSavedInputs();
        recalculateDosage();
        refreshEmptyState();
        initMonetization();
    }

    private void initViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSoundToggle = findViewById(R.id.btnSoundToggle);
        btnWaterLockToggle = findViewById(R.id.btnWaterLockToggle);
        btnHistory = findViewById(R.id.btnHistory);

        tvStageBanner = findViewById(R.id.tvStageBanner);
        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);
        tvStageSubtext = findViewById(R.id.tvStageSubtext);
        progressStageGauge = findViewById(R.id.progressStageGauge);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnAdd30s = findViewById(R.id.btnAdd30s);
        btnSkipStage = findViewById(R.id.btnSkipStage);
        btnResetCycle = findViewById(R.id.btnResetCycle);

        etFilterArea = findViewById(R.id.etFilterArea);
        seekFilterArea = findViewById(R.id.seekFilterArea);
        tvDeDosageLbs = findViewById(R.id.tvDeDosageLbs);
        tvDeDosageScoops = findViewById(R.id.tvDeDosageScoops);
        btnModeFull = findViewById(R.id.btnModeFull);
        btnModeBump = findViewById(R.id.btnModeBump);
        btnSightGlassCheck = findViewById(R.id.btnSightGlassCheck);

        llResultCard = findViewById(R.id.llResultCard);
        etCleanPsi = findViewById(R.id.etCleanPsi);
        etDirtyPsi = findViewById(R.id.etDirtyPsi);
        btnSaveLog = findViewById(R.id.btnSaveLog);

        llEmptyState = findViewById(R.id.llEmptyState);
        btnQuickStartPreset = findViewById(R.id.btnQuickStartPreset);

        llWaterLockOverlay = findViewById(R.id.llWaterLockOverlay);
        tvLockTimerDisplay = findViewById(R.id.tvLockTimerDisplay);
        btnUnlockWater = findViewById(R.id.btnUnlockWater);

        llAdContainer = findViewById(R.id.llAdContainer);
        tvAdNotice = findViewById(R.id.tvAdNotice);
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnSoundToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            boolean isMuted = !cycleManager.isMuted();
            cycleManager.setMuted(isMuted);
            btnSoundToggle.setText(isMuted ? "Audio: OFF" : "Audio: ON");
        });

        btnWaterLockToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            enableWaterLock(true);
        });

        btnUnlockWater.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            enableWaterLock(false);
        });

        btnHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            Intent intent = new Intent(MainActivity.this, HistoryDetailActivity.class);
            startActivity(intent);
        });

        btnPlayPause.setOnClickListener(v -> {
            if (isDebounced()) return;
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleManager.togglePause();
            updatePlayPauseButtonLabel();
            refreshEmptyState();
        });

        btnAdd30s.setOnClickListener(v -> {
            if (isDebounced()) return;
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleManager.addSeconds(30);
        });

        btnSkipStage.setOnClickListener(v -> {
            if (isDebounced()) return;
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleManager.skipCurrentStage();
        });

        btnResetCycle.setOnClickListener(v -> {
            if (isDebounced()) return;
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleManager.reset();
            llResultCard.setVisibility(View.GONE);
            updatePlayPauseButtonLabel();
            refreshEmptyState();
        });

        btnQuickStartPreset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleManager.setDurations(120, 30);
            cycleManager.startCycle();
            updatePlayPauseButtonLabel();
            refreshEmptyState();
        });

        seekFilterArea.setMax(140); // 10 to 150
        seekFilterArea.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    double area = DeDosageCalculator.MIN_AREA_SQFT + progress;
                    etFilterArea.setText(String.format(Locale.US, "%.1f", area));
                    recalculateDosage();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        etFilterArea.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                syncAreaFromInput();
            }
        });

        btnModeFull.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            isPartialBumpMode = false;
            btnModeFull.setSelected(true);
            btnModeBump.setSelected(false);
            recalculateDosage();
        });

        btnModeBump.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            isPartialBumpMode = true;
            btnModeBump.setSelected(true);
            btnModeFull.setSelected(false);
            recalculateDosage();
        });
        btnModeFull.setSelected(true);

        btnSightGlassCheck.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showSightGlassDialog();
        });

        btnSaveLog.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            saveCurrentRunToLog();
        });
    }

    private boolean isDebounced() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTouchTime < 300) {
            return true;
        }
        lastTouchTime = now;
        return false;
    }

    private void syncAreaFromInput() {
        String text = etFilterArea.getText() != null ? etFilterArea.getText().toString().trim() : "";
        double area = DeDosageCalculator.DEFAULT_AREA_SQFT;
        if (!TextUtils.isEmpty(text)) {
            try {
                area = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid area, reset to 48 sq ft", Toast.LENGTH_SHORT).show();
            }
        }
        area = DeDosageCalculator.clampArea(area);
        etFilterArea.setText(String.format(Locale.US, "%.1f", area));
        seekFilterArea.setProgress((int) (area - DeDosageCalculator.MIN_AREA_SQFT));
        recalculateDosage();
    }

    private void recalculateDosage() {
        String areaStr = etFilterArea.getText() != null ? etFilterArea.getText().toString().trim() : "";
        double area = DeDosageCalculator.DEFAULT_AREA_SQFT;
        if (!TextUtils.isEmpty(areaStr)) {
            try {
                area = Double.parseDouble(areaStr);
            } catch (NumberFormatException ignored) {
            }
        }
        area = DeDosageCalculator.clampArea(area);
        double lbs = isPartialBumpMode ? DeDosageCalculator.calculateBumpOrPartialLbs(area) : DeDosageCalculator.calculateFullRechargeLbs(area);
        double scoops = DeDosageCalculator.lbsToStandardScoops(lbs);

        tvDeDosageLbs.setText(String.format(Locale.US, "%.1f LBS", lbs));
        tvDeDosageScoops.setText(String.format(Locale.US, "%.1f Scoops / Cans (1-lb standard)", scoops));
    }

    private void enableWaterLock(boolean enable) {
        isWaterLocked = enable;
        llWaterLockOverlay.setVisibility(enable ? View.VISIBLE : View.GONE);
        if (enable) {
            Toast.makeText(this, "Water Lock Active. Use Vol Keys for timer.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePlayPauseButtonLabel() {
        BackwashCycleManager.Stage stage = cycleManager.getCurrentStage();
        if (stage == BackwashCycleManager.Stage.IDLE || stage == BackwashCycleManager.Stage.COMPLETED) {
            btnPlayPause.setText("START BACKWASH");
        } else if (cycleManager.isPaused()) {
            btnPlayPause.setText("RESUME CYCLE");
        } else {
            btnPlayPause.setText("PAUSE CYCLE");
        }
    }

    private void refreshEmptyState() {
        List<DatabaseHelper.CycleRecord> logs = dbHelper.getAllLogs();
        boolean hasNoLogs = (logs == null || logs.isEmpty());
        boolean isIdle = (cycleManager.getCurrentStage() == BackwashCycleManager.Stage.IDLE);
        llEmptyState.setVisibility((hasNoLogs && isIdle) ? View.VISIBLE : View.GONE);
    }

    private void showSightGlassDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Sight-Glass Clarity Inspection")
                .setMessage("Check the glass bulb on your multiport valve line:\n\n• Dirty/Cloudy: Keep backwashing.\n• Crystal Clear: Backwash complete! Advance immediately to 30s RINSE stage.")
                .setPositiveButton("Advance to Rinse", (d, w) -> {
                    if (cycleManager.getCurrentStage() == BackwashCycleManager.Stage.BACKWASH) {
                        cycleManager.skipCurrentStage();
                    } else {
                        Toast.makeText(this, "Rinse is queued next.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Keep Running", null)
                .show();
    }

    private void saveCurrentRunToLog() {
        String cleanPsiStr = etCleanPsi.getText() != null ? etCleanPsi.getText().toString().trim() : "";
        String dirtyPsiStr = etDirtyPsi.getText() != null ? etDirtyPsi.getText().toString().trim() : "";

        double cleanPsi = 15.0;
        double dirtyPsi = 25.0;

        try {
            if (!TextUtils.isEmpty(cleanPsiStr)) cleanPsi = Double.parseDouble(cleanPsiStr);
            if (!TextUtils.isEmpty(dirtyPsiStr)) dirtyPsi = Double.parseDouble(dirtyPsiStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numeric PSI readings", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clamp PSI
        cleanPsi = Math.max(0.0, Math.min(60.0, cleanPsi));
        dirtyPsi = Math.max(0.0, Math.min(60.0, dirtyPsi));

        if (cleanPsi > 35.0) {
            Toast.makeText(this, "Warning: Clean PSI > 35 suggests line restriction or manifold obstruction", Toast.LENGTH_LONG).show();
        }

        double area = DeDosageCalculator.clampArea(Double.parseDouble(etFilterArea.getText().toString()));
        double dosage = isPartialBumpMode ? DeDosageCalculator.calculateBumpOrPartialLbs(area) : DeDosageCalculator.calculateFullRechargeLbs(area);

        long rowId = dbHelper.insertLog("Main DE Filter", 120, 30, area, dosage, cleanPsi, dirtyPsi, "Valve cycle executed cleanly.");
        if (rowId != -1) {
            Toast.makeText(this, "Cycle successfully recorded to database!", Toast.LENGTH_SHORT).show();
            prefs.edit()
                    .putFloat(PREF_CLEAN_PSI, (float) cleanPsi)
                    .putFloat(PREF_DIRTY_PSI, (float) dirtyPsi)
                    .putFloat(PREF_FILTER_AREA, (float) area)
                    .apply();
            llResultCard.setVisibility(View.GONE);
            refreshEmptyState();
        } else {
            Toast.makeText(this, "Failed to persist log", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSavedInputs() {
        float area = prefs.getFloat(PREF_FILTER_AREA, 48.0f);
        etFilterArea.setText(String.format(Locale.US, "%.1f", area));
        seekFilterArea.setProgress((int) (area - DeDosageCalculator.MIN_AREA_SQFT));

        float cleanPsi = prefs.getFloat(PREF_CLEAN_PSI, 15.0f);
        float dirtyPsi = prefs.getFloat(PREF_DIRTY_PSI, 25.0f);
        etCleanPsi.setText(String.format(Locale.US, "%.1f", cleanPsi));
        etDirtyPsi.setText(String.format(Locale.US, "%.1f", dirtyPsi));
    }

    // CycleListener Callbacks
    @Override
    public void onTick(BackwashCycleManager.Stage stage, int remainingSeconds, int totalSeconds) {
        runOnUiThread(() -> {
            String timeFormatted = String.format(Locale.US, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
            tvTimerCountdown.setText(timeFormatted);
            tvLockTimerDisplay.setText(timeFormatted);

            if (totalSeconds > 0) {
                int progress = (int) (((totalSeconds - remainingSeconds) / (float) totalSeconds) * 100);
                progressStageGauge.setProgress(progress);
            }
        });
    }

    @Override
    public void onStageChanged(BackwashCycleManager.Stage newStage) {
        runOnUiThread(() -> {
            updatePlayPauseButtonLabel();
            switch (newStage) {
                case IDLE:
                    tvStageBanner.setText("STAGE: READY / IDLE");
                    tvStageBanner.setBackgroundColor(getColor(R.color.m3_surface_container));
                    tvStageSubtext.setText("Set multiport valve to BACKWASH, roll out discharge hose.");
                    progressStageGauge.setProgress(0);
                    break;
                case BACKWASH:
                    tvStageBanner.setText("STAGE: BACKWASHING");
                    tvStageBanner.setBackgroundColor(getColor(R.color.color_amber_pulse));
                    tvStageSubtext.setText("Pump running in reverse. Inspect sight-glass for clear effluent.");
                    break;
                case RINSE:
                    tvStageBanner.setText("STAGE: RINSE CYCLE");
                    tvStageBanner.setBackgroundColor(getColor(R.color.color_cyan_glow));
                    tvStageSubtext.setText("Turn pump off, set valve to RINSE. Reseats sand/grid bed.");
                    break;
                case RECHARGE:
                    tvStageBanner.setText("STAGE: MEDIA RECHARGE");
                    tvStageBanner.setBackgroundColor(getColor(R.color.color_recharge_green));
                    tvStageSubtext.setText("Switch valve to FILTER. Slurry DE powder into skimmer now.");
                    llResultCard.setVisibility(View.VISIBLE);
                    break;
                case COMPLETED:
                    tvStageBanner.setText("STAGE: COMPLETE");
                    tvStageSubtext.setText("Filter running clean. Log baseline pressure.");
                    break;
            }
        });
    }

    @Override
    public void onCycleComplete() {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Backwash and Rinse finished! Add DE Slurry.", Toast.LENGTH_LONG).show();
            if (isWaterLocked) {
                enableWaterLock(false);
            }
        });
    }

    // Theme Engine
    private void cycleThemeMode() {
        int current = prefs.getInt(PREF_THEME_MODE, 0);
        int next = (current + 1) % 3;
        prefs.edit().putInt(PREF_THEME_MODE, next).apply();
        recreate();
    }

    private void updateThemeButtonLabel() {
        int mode = prefs.getInt(PREF_THEME_MODE, 0);
        if (mode == 1) {
            btnThemeToggle.setText("Theme: Light");
        } else if (mode == 2) {
            btnThemeToggle.setText("Theme: Dark");
        } else {
            btnThemeToggle.setText("Theme: Auto");
        }
    }

    private void applyStatusBarAppearance() {
        int mode = prefs.getInt(PREF_THEME_MODE, 0);
        boolean isLight = (mode == 1);
        try {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (isLight) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    controller.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                }
            }
        } catch (Exception ignored) {
        }
    }

    // Physical Hardware & Desktop Keyboard Controls
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_EQUALS:
                case KeyEvent.KEYCODE_PLUS:
                    cycleManager.addSeconds(30);
                    Toast.makeText(this, "+30s added via hardware key", Toast.LENGTH_SHORT).show();
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_MINUS:
                    cycleManager.addSeconds(-15);
                    Toast.makeText(this, "-15s subtracted via hardware key", Toast.LENGTH_SHORT).show();
                    return true;
                case KeyEvent.KEYCODE_SPACE:
                    cycleManager.togglePause();
                    updatePlayPauseButtonLabel();
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (cycleManager.getCurrentStage() == BackwashCycleManager.Stage.IDLE) {
                        cycleManager.startCycle();
                    } else {
                        cycleManager.skipCurrentStage();
                    }
                    return true;
                case KeyEvent.KEYCODE_ESCAPE:
                    if (isWaterLocked) {
                        enableWaterLock(false);
                    } else {
                        cycleManager.reset();
                    }
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserves ongoing timer state during desktop split-screen or orientation shifts
    }

    private void initMonetization() {
        boolean isPro = prefs.getBoolean(PREF_IS_PRO, false);
        if (isPro) {
            llAdContainer.setVisibility(View.GONE);
        } else {
            llAdContainer.setVisibility(View.VISIBLE);
            tvAdNotice.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com"));
                startActivity(browserIntent);
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cycleManager != null) {
            cycleManager.release();
        }
    }
}