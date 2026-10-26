package com.kimrosil.luxscale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * LuxScale Main Optical Workbench Activity.
 * Integrates live hardware ambient light sensor (Sensor.TYPE_LIGHT), calibration multiplier,
 * Plant PAR (PPFD & DLI) converter, IES benchmarks, and local SQLite room audit log.
 */
public class MainActivity extends Activity implements SensorEventListener {

    private static final String PREFS_NAME = "lux_scale_prefs";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_PRIMARY_UNIT_FC = "primary_unit_fc";
    private static final String KEY_HAPTICS = "haptics_enabled";
    private static final String KEY_SPECTRUM_MODE = "spectrum_mode";
    private static final String KEY_TARGET_LUX = "target_benchmark_lux";
    private static final String KEY_DRAFT_CAL = "draft_cal_factor";
    private static final String KEY_DRAFT_REF_LUX = "draft_ref_lux";
    private static final String KEY_DRAFT_HOURS = "draft_photo_hours";
    private static final String KEY_DRAFT_ROOM = "draft_room_name";

    private SharedPreferences prefs;
    private LuxDatabaseHelper dbHelper;
    private SensorManager sensorManager;
    private Sensor lightSensor;

    private int activeTab = 0; // 0=Meter, 1=Plant, 2=Log
    private int spectrumMode = LuxEngine.SPECTRUM_DAYLIGHT_6500K;
    private double targetBenchmarkLux = 500.0;
    private boolean isHoldLocked = false;
    private boolean useFootCandlesPrimary = false;
    private boolean hapticsEnabled = true;

    private float lastHardwareLux = -1.0f;
    private long manualCalLockUntilMs = 0L;

    // Session statistics
    private double sessionMinLux = 450.0;
    private double sessionPeakLux = 680.0;
    private double sessionSumLux = 450.0;
    private long sessionSampleCount = 1L;
    private LuxEngine.LuxTelemetry latestTelemetry;

    // Hero HUD Views
    private TextView tvSensorState;
    private TextView tvZoneBadge;
    private LuxGaugeView luxGaugeView;
    private TextView tvHeroLux;
    private TextView tvHeroFcSub;
    private TextView tvHeroPpfd;
    private TextView tvHeroDliSub;
    private TextView tvHeroMinMax;
    private TextView tvHeroAvgSub;
    private TextView tvHeroEv;
    private TextView tvHeroTargetSub;

    // Tab Buttons & Panels
    private Button btnTabMeter;
    private Button btnTabPlant;
    private Button btnTabLog;
    private View panelMeter;
    private View panelPlant;
    private View panelLog;

    // Panel 1: Meter & Calibration Controls
    private EditText etCalFactor;
    private EditText etRefLux;
    private Button btnHoldSensor;
    private Button btnSpecDaylight;
    private Button btnSpecWarmLed;
    private Button btnSpecGrowLed;
    private TextView tvSensorHardwareInfo;

    // Panel 2: Plant & IES Controls
    private EditText etPhotoHours;
    private TextView tvPlantDiagnosis;

    // Panel 3: SQLite Audit Log Controls
    private EditText etRoomName;
    private LinearLayout llAuditLogList;

    private boolean suppressTextWatcher = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = sp.getInt(KEY_THEME_MODE, 2); // Default Graphite Optical Lab Dark Mode
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
        dbHelper = new LuxDatabaseHelper(this);

        useFootCandlesPrimary = prefs.getBoolean(KEY_PRIMARY_UNIT_FC, false);
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true);
        spectrumMode = prefs.getInt(KEY_SPECTRUM_MODE, LuxEngine.SPECTRUM_DAYLIGHT_6500K);
        targetBenchmarkLux = Double.longBitsToDouble(
                prefs.getLong(KEY_TARGET_LUX, Double.doubleToLongBits(500.0)));

        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        bindViews();
        if (savedInstanceState == null) {
            restoreDraftInputs();
        }
        updateTabSelection(activeTab);
        updateSpectrumChips();
        recalculateAndRender(false);
        refreshAuditLogList();
    }

    private void bindViews() {
        tvSensorState = findViewById(R.id.tvSensorState);
        tvZoneBadge = findViewById(R.id.tvZoneBadge);
        luxGaugeView = findViewById(R.id.luxGaugeView);
        tvHeroLux = findViewById(R.id.tvHeroLux);
        tvHeroFcSub = findViewById(R.id.tvHeroFcSub);
        tvHeroPpfd = findViewById(R.id.tvHeroPpfd);
        tvHeroDliSub = findViewById(R.id.tvHeroDliSub);
        tvHeroMinMax = findViewById(R.id.tvHeroMinMax);
        tvHeroAvgSub = findViewById(R.id.tvHeroAvgSub);
        tvHeroEv = findViewById(R.id.tvHeroEv);
        tvHeroTargetSub = findViewById(R.id.tvHeroTargetSub);

        btnTabMeter = findViewById(R.id.btnTabMeter);
        btnTabPlant = findViewById(R.id.btnTabPlant);
        btnTabLog = findViewById(R.id.btnTabLog);
        panelMeter = findViewById(R.id.panelMeter);
        panelPlant = findViewById(R.id.panelPlant);
        panelLog = findViewById(R.id.panelLog);

        etCalFactor = findViewById(R.id.etCalFactor);
        etRefLux = findViewById(R.id.etRefLux);
        btnHoldSensor = findViewById(R.id.btnHoldSensor);
        btnSpecDaylight = findViewById(R.id.btnSpecDaylight);
        btnSpecWarmLed = findViewById(R.id.btnSpecWarmLed);
        btnSpecGrowLed = findViewById(R.id.btnSpecGrowLed);
        tvSensorHardwareInfo = findViewById(R.id.tvSensorHardwareInfo);

        etPhotoHours = findViewById(R.id.etPhotoHours);
        tvPlantDiagnosis = findViewById(R.id.tvPlantDiagnosis);

        etRoomName = findViewById(R.id.etRoomName);
        llAuditLogList = findViewById(R.id.llAuditLogList);

        ImageButton btnThemeToggle = findViewById(R.id.btnThemeToggle);
        ImageButton btnSettings = findViewById(R.id.btnSettings);

        btnThemeToggle.setOnClickListener(v -> {
            triggerHaptic(v);
            int current = prefs.getInt(KEY_THEME_MODE, 2);
            int next = (current == 1) ? 2 : 1;
            prefs.edit().putInt(KEY_THEME_MODE, next).apply();
            recreate();
        });

        btnSettings.setOnClickListener(v -> {
            triggerHaptic(v);
            showSettingsDialog();
        });

        btnTabMeter.setOnClickListener(v -> {
            triggerHaptic(v);
            updateTabSelection(0);
        });
        btnTabPlant.setOnClickListener(v -> {
            triggerHaptic(v);
            updateTabSelection(1);
        });
        btnTabLog.setOnClickListener(v -> {
            triggerHaptic(v);
            updateTabSelection(2);
        });

        Button btnCalcMeter = findViewById(R.id.btnCalcMeter);
        btnCalcMeter.setOnClickListener(v -> {
            triggerHaptic(v);
            manualCalLockUntilMs = SystemClock.elapsedRealtime() + 10000L;
            sanitizeInputsOnExplicitApply();
            hideKeyboard(v);
            recalculateAndRender(true);
        });

        btnHoldSensor.setOnClickListener(v -> {
            triggerHaptic(v);
            isHoldLocked = !isHoldLocked;
            btnHoldSensor.setText(isHoldLocked ? "Resume Live" : getString(R.string.btn_hold_sensor));
            recalculateAndRender(false);
        });

        findViewById(R.id.btnCalMinus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etCalFactor, -0.05, 0.05, 50.0, "%.2f");
        });
        findViewById(R.id.btnCalPlus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etCalFactor, 0.05, 0.05, 50.0, "%.2f");
        });

        findViewById(R.id.btnRefMinus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etRefLux, -50.0, 0.0, 99999.0, "%.0f");
        });
        findViewById(R.id.btnRefPlus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etRefLux, 50.0, 0.0, 99999.0, "%.0f");
        });

        btnSpecDaylight.setOnClickListener(v -> {
            triggerHaptic(v);
            setSpectrumMode(LuxEngine.SPECTRUM_DAYLIGHT_6500K);
        });
        btnSpecWarmLed.setOnClickListener(v -> {
            triggerHaptic(v);
            setSpectrumMode(LuxEngine.SPECTRUM_WARM_LED_3500K);
        });
        btnSpecGrowLed.setOnClickListener(v -> {
            triggerHaptic(v);
            setSpectrumMode(LuxEngine.SPECTRUM_GROW_PAR_LED);
        });

        findViewById(R.id.btnResetStats).setOnClickListener(v -> {
            triggerHaptic(v);
            if (latestTelemetry != null) {
                sessionMinLux = latestTelemetry.calibratedLux;
                sessionPeakLux = latestTelemetry.calibratedLux;
                sessionSumLux = latestTelemetry.calibratedLux;
                sessionSampleCount = 1L;
            }
            recalculateAndRender(false);
        });

        // Panel 2: Plant & IES Controls
        findViewById(R.id.btnHoursMinus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etPhotoHours, -1.0, 1.0, 24.0, "%.1f");
        });
        findViewById(R.id.btnHoursPlus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etPhotoHours, 1.0, 1.0, 24.0, "%.1f");
        });
        findViewById(R.id.btnApplyPlant).setOnClickListener(v -> {
            triggerHaptic(v);
            hideKeyboard(v);
            recalculateAndRender(true);
        });

        findViewById(R.id.btnPresetOfficeIes).setOnClickListener(v -> {
            triggerHaptic(v);
            applyTargetPreset(500.0, LuxEngine.SPECTRUM_DAYLIGHT_6500K);
        });
        findViewById(R.id.btnPresetLowPlant).setOnClickListener(v -> {
            triggerHaptic(v);
            applyTargetPreset(2000.0, LuxEngine.SPECTRUM_DAYLIGHT_6500K);
        });
        findViewById(R.id.btnPresetVegCanopy).setOnClickListener(v -> {
            triggerHaptic(v);
            applyTargetPreset(8000.0, LuxEngine.SPECTRUM_GROW_PAR_LED);
        });
        findViewById(R.id.btnPresetHighCanopy).setOnClickListener(v -> {
            triggerHaptic(v);
            applyTargetPreset(20000.0, LuxEngine.SPECTRUM_GROW_PAR_LED);
        });

        // Panel 3: Save Room Audit
        findViewById(R.id.btnSaveAuditLog).setOnClickListener(v -> {
            triggerHaptic(v);
            hideKeyboard(v);
            saveCurrentAuditToDb();
        });

        TextWatcher liveWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressTextWatcher) {
                    return;
                }
                manualCalLockUntilMs = SystemClock.elapsedRealtime() + 8000L;
                saveDraftInputs();
                recalculateAndRender(true);
            }
        };

        etCalFactor.addTextChangedListener(liveWatcher);
        etRefLux.addTextChangedListener(liveWatcher);
        etPhotoHours.addTextChangedListener(liveWatcher);
    }

    private void sanitizeInputsOnExplicitApply() {
        suppressTextWatcher = true;
        try {
            double cal = LuxEngine.parseSafeDouble(etCalFactor.getText().toString(), 1.00, 0.05, 50.0);
            double ref = LuxEngine.parseSafeDouble(etRefLux.getText().toString(), 450.0, 0.0, 99999.0);
            double hrs = LuxEngine.parseSafeDouble(etPhotoHours.getText().toString(), 14.0, 0.5, 24.0);

            String rawCal = etCalFactor.getText().toString().trim();
            if (rawCal.isEmpty() || rawCal.length() > 5 || rawCal.startsWith("-")) {
                etCalFactor.setText(String.format(Locale.US, "%.2f", cal));
            }
            String rawRef = etRefLux.getText().toString().trim();
            if (rawRef.isEmpty() || rawRef.length() > 5 || rawRef.startsWith("-")) {
                etRefLux.setText(String.format(Locale.US, "%.0f", ref));
            }
            String rawHrs = etPhotoHours.getText().toString().trim();
            if (rawHrs.isEmpty() || rawHrs.length() > 4 || rawHrs.startsWith("-")) {
                etPhotoHours.setText(String.format(Locale.US, "%.1f", hrs));
            }
            saveDraftInputs();
        } finally {
            suppressTextWatcher = false;
        }
    }

    private void stepEditText(EditText target, double delta, double min, double max, String fmt) {
        manualCalLockUntilMs = SystemClock.elapsedRealtime() + 8000L;
        double current = LuxEngine.parseSafeDouble(target.getText().toString(), min, min, max);
        double next = LuxEngine.clamp(current + delta, min, max);
        suppressTextWatcher = true;
        target.setText(String.format(Locale.US, fmt, next));
        suppressTextWatcher = false;
        saveDraftInputs();
        recalculateAndRender(true);
    }

    private void setSpectrumMode(int mode) {
        spectrumMode = mode;
        prefs.edit().putInt(KEY_SPECTRUM_MODE, spectrumMode).apply();
        updateSpectrumChips();
        recalculateAndRender(false);
    }

    private void applyTargetPreset(double benchmarkLux, int suggestedSpectrum) {
        targetBenchmarkLux = benchmarkLux;
        spectrumMode = suggestedSpectrum;
        prefs.edit()
                .putLong(KEY_TARGET_LUX, Double.doubleToLongBits(targetBenchmarkLux))
                .putInt(KEY_SPECTRUM_MODE, spectrumMode)
                .apply();
        updateSpectrumChips();
        recalculateAndRender(false);
    }

    private void updateTabSelection(int tab) {
        activeTab = tab;
        panelMeter.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        panelPlant.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        panelLog.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);

        styleChipButton(btnTabMeter, tab == 0);
        styleChipButton(btnTabPlant, tab == 1);
        styleChipButton(btnTabLog, tab == 2);
    }

    private void updateSpectrumChips() {
        styleChipButton(btnSpecDaylight, spectrumMode == LuxEngine.SPECTRUM_DAYLIGHT_6500K);
        styleChipButton(btnSpecWarmLed, spectrumMode == LuxEngine.SPECTRUM_WARM_LED_3500K);
        styleChipButton(btnSpecGrowLed, spectrumMode == LuxEngine.SPECTRUM_GROW_PAR_LED);
    }

    private void styleChipButton(Button btn, boolean selected) {
        btn.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btn.setTextColor(getColor(selected ? R.color.text_on_accent : R.color.text_primary));
    }

    private void recalculateAndRender(boolean forceFromReference) {
        double calFactor = LuxEngine.parseSafeDouble(etCalFactor.getText().toString(), 1.00, 0.05, 50.0);
        double refLux = LuxEngine.parseSafeDouble(etRefLux.getText().toString(), 450.0, 0.0, 99999.0);
        double photoHours = LuxEngine.parseSafeDouble(etPhotoHours.getText().toString(), 14.0, 0.5, 24.0);

        boolean useManual = forceFromReference
                || (SystemClock.elapsedRealtime() < manualCalLockUntilMs)
                || (lastHardwareLux < 0.0f);

        double baseLux = useManual ? refLux : (double) lastHardwareLux;

        latestTelemetry = LuxEngine.evaluate(
                baseLux,
                calFactor,
                photoHours,
                targetBenchmarkLux,
                spectrumMode
        );

        double calLux = latestTelemetry.calibratedLux;
        if (sessionSampleCount <= 1L) {
            sessionMinLux = Math.min(sessionMinLux, calLux);
            sessionPeakLux = Math.max(sessionPeakLux, calLux);
            sessionSumLux = calLux;
            sessionSampleCount = 2L;
        } else {
            if (calLux < sessionMinLux) {
                sessionMinLux = calLux;
            }
            if (calLux > sessionPeakLux) {
                sessionPeakLux = calLux;
            }
            sessionSumLux += calLux;
            sessionSampleCount++;
        }
        double avgLux = sessionSumLux / (double) Math.max(1L, sessionSampleCount);

        String modeLabel = isHoldLocked ? "HOLD LOCKED" : (lastHardwareLux >= 0f ? "LIVE SENSOR" : "REF MODE");
        tvSensorState.setText(modeLabel + " • " + latestTelemetry.spectrumLabel);
        tvZoneBadge.setText(latestTelemetry.zoneBadge);

        if (useFootCandlesPrimary) {
            tvHeroLux.setText(String.format(Locale.US, "%.1f fc", latestTelemetry.footCandles));
            tvHeroFcSub.setText(String.format(Locale.US, "%.0f lx • SI Light Level", latestTelemetry.calibratedLux));
        } else {
            tvHeroLux.setText(String.format(Locale.US, "%.0f lx", latestTelemetry.calibratedLux));
            tvHeroFcSub.setText(String.format(Locale.US, "%.1f fc • Foot-Candles", latestTelemetry.footCandles));
        }

        tvHeroPpfd.setText(String.format(Locale.US, "%.1f µmol/s", latestTelemetry.ppfd));
        tvHeroDliSub.setText(String.format(Locale.US, "DLI %.2f mol/m²/d (%.0fh)", latestTelemetry.dli, photoHours));

        tvHeroMinMax.setText(String.format(Locale.US, "Min %.0f • Peak %.0f lx", sessionMinLux, sessionPeakLux));
        tvHeroAvgSub.setText(String.format(Locale.US, "Session Avg: %.0f lx", avgLux));

        tvHeroEv.setText(String.format(Locale.US, "EV100 %.1f • ISO 100", latestTelemetry.ev100));
        tvHeroTargetSub.setText(String.format(
                Locale.US,
                "%.0f%% of %.0f lx Target",
                latestTelemetry.targetPercent,
                latestTelemetry.targetLux));

        String hwStatus = (lightSensor != null)
                ? String.format(Locale.US, "Photodiode: %.0f lx • Cal ×%.2f", Math.max(0f, lastHardwareLux), calFactor)
                : String.format(Locale.US, "Ref Simulation • Cal ×%.2f", calFactor);
        tvSensorHardwareInfo.setText(hwStatus);

        tvPlantDiagnosis.setText(latestTelemetry.diagnosisText);

        luxGaugeView.updateReadings(latestTelemetry.calibratedLux, sessionPeakLux, latestTelemetry.ppfd);
    }

    private void saveCurrentAuditToDb() {
        if (latestTelemetry == null) {
            recalculateAndRender(false);
        }
        String room = etRoomName.getText().toString().trim();
        if (room.isEmpty()) {
            room = "Studio Room";
        }
        dbHelper.insertAudit(
                room,
                latestTelemetry.calibratedLux,
                latestTelemetry.ppfd,
                latestTelemetry.dli,
                latestTelemetry.calFactor,
                spectrumMode
        );
        refreshAuditLogList();
        Toast.makeText(this, "Saved audit for " + room, Toast.LENGTH_SHORT).show();
    }

    private void refreshAuditLogList() {
        llAuditLogList.removeAllViews();
        List<LuxDatabaseHelper.AuditRecord> records = dbHelper.getAllAudits();
        int padPx = dpToPx(8f);
        int marginPx = dpToPx(6f);

        for (final LuxDatabaseHelper.AuditRecord rec : records) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card_elevated);
            row.setPadding(padPx, padPx, padPx, padPx);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = marginPx;
            row.setLayoutParams(rowLp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f);
            textCol.setLayoutParams(colLp);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(rec.roomName);
            tvTitle.setTextColor(getColor(R.color.text_primary));
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            tvTitle.getPaint().setFakeBoldText(true);

            TextView tvSub = new TextView(this);
            tvSub.setText(String.format(
                    Locale.US,
                    "%.0f lx • %.1f PPFD • DLI %.2f • Cal ×%.2f",
                    rec.calLux,
                    rec.ppfd,
                    rec.dli,
                    rec.calFactor));
            tvSub.setTextColor(getColor(R.color.text_secondary));
            tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);

            textCol.addView(tvTitle);
            textCol.addView(tvSub);

            Button btnLoad = new Button(this);
            btnLoad.setText("Load");
            btnLoad.setAllCaps(false);
            btnLoad.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            btnLoad.setTextColor(getColor(R.color.text_primary));
            btnLoad.setBackgroundResource(R.drawable.bg_btn_secondary);
            LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dpToPx(58f), dpToPx(48f));
            loadLp.rightMargin = dpToPx(4f);
            btnLoad.setLayoutParams(loadLp);
            btnLoad.setOnClickListener(v -> {
                triggerHaptic(v);
                suppressTextWatcher = true;
                etCalFactor.setText(String.format(Locale.US, "%.2f", rec.calFactor));
                double rawEst = rec.calLux / Math.max(0.05, rec.calFactor);
                etRefLux.setText(String.format(Locale.US, "%.0f", rawEst));
                etRoomName.setText(rec.roomName);
                suppressTextWatcher = false;
                spectrumMode = rec.spectrumMode;
                updateSpectrumChips();
                manualCalLockUntilMs = SystemClock.elapsedRealtime() + 8000L;
                recalculateAndRender(true);
                updateTabSelection(0);
            });

            Button btnDel = new Button(this);
            btnDel.setText("Del");
            btnDel.setAllCaps(false);
            btnDel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            btnDel.setTextColor(getColor(R.color.accent_danger));
            btnDel.setBackgroundResource(R.drawable.bg_btn_secondary);
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dpToPx(52f), dpToPx(48f));
            btnDel.setLayoutParams(delLp);
            btnDel.setOnClickListener(v -> {
                triggerHaptic(v);
                dbHelper.deleteAudit(rec.id);
                refreshAuditLogList();
            });

            row.addView(textCol);
            row.addView(btnLoad);
            row.addView(btnDel);
            llAuditLogList.addView(row);
        }
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        Button btnThemeLight = dialogView.findViewById(R.id.btnThemeLight);
        Button btnThemeDark = dialogView.findViewById(R.id.btnThemeDark);
        Button btnThemeAuto = dialogView.findViewById(R.id.btnThemeAuto);
        Button btnUnitLux = dialogView.findViewById(R.id.btnUnitLux);
        Button btnUnitFc = dialogView.findViewById(R.id.btnUnitFc);
        Switch swHaptics = dialogView.findViewById(R.id.swHaptics);
        Button btnPrivacy = dialogView.findViewById(R.id.btnPrivacyPolicy);
        Button btnReset = dialogView.findViewById(R.id.btnResetDefaults);
        Button btnClose = dialogView.findViewById(R.id.btnCloseSettings);

        int themeMode = prefs.getInt(KEY_THEME_MODE, 2);
        styleChipButton(btnThemeLight, themeMode == 1);
        styleChipButton(btnThemeDark, themeMode == 2);
        styleChipButton(btnThemeAuto, themeMode == 0);

        styleChipButton(btnUnitLux, !useFootCandlesPrimary);
        styleChipButton(btnUnitFc, useFootCandlesPrimary);
        swHaptics.setChecked(hapticsEnabled);

        btnThemeLight.setOnClickListener(v -> {
            prefs.edit().putInt(KEY_THEME_MODE, 1).apply();
            dialog.dismiss();
            recreate();
        });
        btnThemeDark.setOnClickListener(v -> {
            prefs.edit().putInt(KEY_THEME_MODE, 2).apply();
            dialog.dismiss();
            recreate();
        });
        btnThemeAuto.setOnClickListener(v -> {
            prefs.edit().putInt(KEY_THEME_MODE, 0).apply();
            dialog.dismiss();
            recreate();
        });

        btnUnitLux.setOnClickListener(v -> {
            useFootCandlesPrimary = false;
            prefs.edit().putBoolean(KEY_PRIMARY_UNIT_FC, false).apply();
            styleChipButton(btnUnitLux, true);
            styleChipButton(btnUnitFc, false);
            recalculateAndRender(false);
        });
        btnUnitFc.setOnClickListener(v -> {
            useFootCandlesPrimary = true;
            prefs.edit().putBoolean(KEY_PRIMARY_UNIT_FC, true).apply();
            styleChipButton(btnUnitLux, false);
            styleChipButton(btnUnitFc, true);
            recalculateAndRender(false);
        });

        swHaptics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            hapticsEnabled = isChecked;
            prefs.edit().putBoolean(KEY_HAPTICS, isChecked).apply();
        });

        btnPrivacy.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Offline Privacy Guarantee")
                    .setMessage("LuxScale operates 100% offline on your device. Hardware photodiode readings and SQLite room logs never leave your phone. Zero network permissions, zero trackers, zero ads.")
                    .setPositiveButton("Close", null)
                    .show();
        });

        btnReset.setOnClickListener(v -> {
            suppressTextWatcher = true;
            etCalFactor.setText("1.00");
            etRefLux.setText("450");
            etPhotoHours.setText("14.0");
            etRoomName.setText("Studio Desk");
            suppressTextWatcher = false;
            spectrumMode = LuxEngine.SPECTRUM_DAYLIGHT_6500K;
            targetBenchmarkLux = 500.0;
            saveDraftInputs();
            updateSpectrumChips();
            recalculateAndRender(true);
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void saveDraftInputs() {
        String cal = etCalFactor.getText().toString().trim();
        String ref = etRefLux.getText().toString().trim();
        String hrs = etPhotoHours.getText().toString().trim();
        String room = etRoomName.getText().toString().trim();

        if (cal.length() <= 6 && !cal.startsWith("-")) {
            prefs.edit().putString(KEY_DRAFT_CAL, cal).apply();
        }
        if (ref.length() <= 6 && !ref.startsWith("-")) {
            prefs.edit().putString(KEY_DRAFT_REF_LUX, ref).apply();
        }
        if (hrs.length() <= 5 && !hrs.startsWith("-")) {
            prefs.edit().putString(KEY_DRAFT_HOURS, hrs).apply();
        }
        if (room.length() <= 22) {
            prefs.edit().putString(KEY_DRAFT_ROOM, room).apply();
        }
    }

    private void restoreDraftInputs() {
        suppressTextWatcher = true;
        try {
            String cal = prefs.getString(KEY_DRAFT_CAL, "1.00");
            String ref = prefs.getString(KEY_DRAFT_REF_LUX, "450");
            String hrs = prefs.getString(KEY_DRAFT_HOURS, "14.0");
            String room = prefs.getString(KEY_DRAFT_ROOM, "Studio Desk");

            double safeCal = LuxEngine.parseSafeDouble(cal, 1.00, 0.05, 50.0);
            double safeRef = LuxEngine.parseSafeDouble(ref, 450.0, 1.0, 99999.0);
            double safeHrs = LuxEngine.parseSafeDouble(hrs, 14.0, 1.0, 24.0);

            etCalFactor.setText(String.format(Locale.US, "%.2f", safeCal));
            etRefLux.setText(String.format(Locale.US, "%.0f", safeRef));
            etPhotoHours.setText(String.format(Locale.US, "%.1f", safeHrs));
            etRoomName.setText((room == null || room.isEmpty() || room.length() > 22) ? "Studio Desk" : room);
        } finally {
            suppressTextWatcher = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length == 0) {
            return;
        }
        if (isHoldLocked) {
            return;
        }
        float raw = event.values[0];
        if (Float.isNaN(raw) || Float.isInfinite(raw) || raw < 0f) {
            return;
        }
        if (lastHardwareLux < 0f || Math.abs(raw - lastHardwareLux) >= 10.0f) {
            lastHardwareLux = raw;
            if (SystemClock.elapsedRealtime() >= manualCalLockUntilMs) {
                recalculateAndRender(false);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void triggerHaptic(View v) {
        if (hapticsEnabled && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void hideKeyboard(View v) {
        if (v == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    private int dpToPx(float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()));
    }
}
