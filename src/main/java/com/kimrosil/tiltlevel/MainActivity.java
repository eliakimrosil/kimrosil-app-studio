package com.kimrosil.tiltlevel;

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
 * TiltLevel Main Clinometer & Spirit Level Workbench Activity.
 * Integrates live 2-axis accelerometer gravity sensing (Sensor.TYPE_ACCELEROMETER),
 * relative tare zeroing, percent grade (%), roof pitch (X:12), span rise/drop solver,
 * and local SQLite surface inspection logs.
 */
public class MainActivity extends Activity implements SensorEventListener {

    private static final String PREFS_NAME = "tilt_level_prefs";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_PRIMARY_UNIT_GRADE = "primary_unit_grade";
    private static final String KEY_HAPTICS = "haptics_enabled";
    private static final String KEY_AXIS_MODE = "axis_mode";
    private static final String KEY_DRAFT_ANGLE = "draft_ref_angle";
    private static final String KEY_DRAFT_TARE = "draft_tare_offset";
    private static final String KEY_DRAFT_SPAN = "draft_span_length";
    private static final String KEY_DRAFT_SITE = "draft_site_name";

    private SharedPreferences prefs;
    private TiltDatabaseHelper dbHelper;
    private SensorManager sensorManager;
    private Sensor accelSensor;

    private int activeTab = 0; // 0=Meter, 1=Roof, 2=Log
    private int axisMode = TiltEngine.AXIS_PITCH;
    private boolean isHoldLocked = false;
    private boolean useGradePrimary = false;
    private boolean hapticsEnabled = true;

    private boolean hasHardwareReading = false;
    private double livePitchDeg = 4.76;
    private double liveRollDeg = 0.00;
    private long manualCalLockUntilMs = 0L;

    private TiltEngine.TiltTelemetry latestTelemetry;

    // Hero HUD Views
    private TextView tvSensorState;
    private TextView tvZoneBadge;
    private TiltDialView tiltDialView;
    private TextView tvHeroAngle;
    private TextView tvHeroGradeSub;
    private TextView tvHeroPitch;
    private TextView tvHeroDropSub;
    private TextView tvHeroAxes;
    private TextView tvHeroTareSub;
    private TextView tvHeroSpanDrop;
    private TextView tvHeroTargetSub;

    // Tab Buttons & Panels
    private Button btnTabMeter;
    private Button btnTabRoof;
    private Button btnTabLog;
    private View panelMeter;
    private View panelRoof;
    private View panelLog;

    // Panel 1: Angle & Tare Controls
    private EditText etRefAngle;
    private EditText etTareOffset;
    private Button btnHoldSensor;
    private Button btnAxisPitch;
    private Button btnAxisRoll;
    private Button btnAxisComposite;
    private TextView tvSensorHardwareInfo;

    // Panel 2: Roof & Grade Controls
    private EditText etSpanLength;
    private TextView tvSlopeDiagnosis;

    // Panel 3: SQLite Log Controls
    private EditText etSiteName;
    private LinearLayout llSlopeLogList;

    private boolean suppressTextWatcher = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = sp.getInt(KEY_THEME_MODE, 2); // Default Anodized Machinist Dark Mode
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
        dbHelper = new TiltDatabaseHelper(this);

        useGradePrimary = prefs.getBoolean(KEY_PRIMARY_UNIT_GRADE, false);
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true);
        axisMode = prefs.getInt(KEY_AXIS_MODE, TiltEngine.AXIS_PITCH);

        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        bindViews();
        if (savedInstanceState == null) {
            restoreDraftInputs();
        }
        updateTabSelection(activeTab);
        updateAxisChips();
        recalculateAndRender(true);
        refreshSlopeLogList();
    }

    private void bindViews() {
        tvSensorState = findViewById(R.id.tvSensorState);
        tvZoneBadge = findViewById(R.id.tvZoneBadge);
        tiltDialView = findViewById(R.id.tiltDialView);
        tvHeroAngle = findViewById(R.id.tvHeroAngle);
        tvHeroGradeSub = findViewById(R.id.tvHeroGradeSub);
        tvHeroPitch = findViewById(R.id.tvHeroPitch);
        tvHeroDropSub = findViewById(R.id.tvHeroDropSub);
        tvHeroAxes = findViewById(R.id.tvHeroAxes);
        tvHeroTareSub = findViewById(R.id.tvHeroTareSub);
        tvHeroSpanDrop = findViewById(R.id.tvHeroSpanDrop);
        tvHeroTargetSub = findViewById(R.id.tvHeroTargetSub);

        btnTabMeter = findViewById(R.id.btnTabMeter);
        btnTabRoof = findViewById(R.id.btnTabRoof);
        btnTabLog = findViewById(R.id.btnTabLog);
        panelMeter = findViewById(R.id.panelMeter);
        panelRoof = findViewById(R.id.panelRoof);
        panelLog = findViewById(R.id.panelLog);

        etRefAngle = findViewById(R.id.etRefAngle);
        etTareOffset = findViewById(R.id.etTareOffset);
        btnHoldSensor = findViewById(R.id.btnHoldSensor);
        btnAxisPitch = findViewById(R.id.btnAxisPitch);
        btnAxisRoll = findViewById(R.id.btnAxisRoll);
        btnAxisComposite = findViewById(R.id.btnAxisComposite);
        tvSensorHardwareInfo = findViewById(R.id.tvSensorHardwareInfo);

        etSpanLength = findViewById(R.id.etSpanLength);
        tvSlopeDiagnosis = findViewById(R.id.tvSlopeDiagnosis);

        etSiteName = findViewById(R.id.etSiteName);
        llSlopeLogList = findViewById(R.id.llSlopeLogList);

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
        btnTabRoof.setOnClickListener(v -> {
            triggerHaptic(v);
            updateTabSelection(1);
        });
        btnTabLog.setOnClickListener(v -> {
            triggerHaptic(v);
            updateTabSelection(2);
        });

        Button btnCalcTilt = findViewById(R.id.btnCalcTilt);
        btnCalcTilt.setOnClickListener(v -> {
            triggerHaptic(v);
            manualCalLockUntilMs = SystemClock.elapsedRealtime() + 10000L;
            sanitizeInputsOnExplicitApply();
            hideKeyboard(v);
            recalculateAndRender(true);
        });

        btnHoldSensor.setOnClickListener(v -> {
            triggerHaptic(v);
            isHoldLocked = !isHoldLocked;
            btnHoldSensor.setText(isHoldLocked ? "Resume Live" : getString(R.string.btn_hold_level));
            recalculateAndRender(false);
        });

        findViewById(R.id.btnAngleMinus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etRefAngle, -0.50, 0.0, 85.0, "%.2f");
        });
        findViewById(R.id.btnAnglePlus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etRefAngle, 0.50, 0.0, 85.0, "%.2f");
        });

        findViewById(R.id.btnTareMinus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etTareOffset, -0.25, 0.0, 45.0, "%.2f");
        });
        findViewById(R.id.btnTarePlus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etTareOffset, 0.25, 0.0, 45.0, "%.2f");
        });

        btnAxisPitch.setOnClickListener(v -> {
            triggerHaptic(v);
            setAxisMode(TiltEngine.AXIS_PITCH);
        });
        btnAxisRoll.setOnClickListener(v -> {
            triggerHaptic(v);
            setAxisMode(TiltEngine.AXIS_ROLL);
        });
        btnAxisComposite.setOnClickListener(v -> {
            triggerHaptic(v);
            setAxisMode(TiltEngine.AXIS_COMPOSITE);
        });

        findViewById(R.id.btnResetTare).setOnClickListener(v -> {
            triggerHaptic(v);
            suppressTextWatcher = true;
            etTareOffset.setText("0.00");
            suppressTextWatcher = false;
            saveDraftInputs();
            recalculateAndRender(false);
        });

        // Panel 2: Span Run & Presets
        findViewById(R.id.btnSpanMinus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etSpanLength, -0.20, 0.20, 100.0, "%.2f");
        });
        findViewById(R.id.btnSpanPlus).setOnClickListener(v -> {
            triggerHaptic(v);
            stepEditText(etSpanLength, 0.20, 0.20, 100.0, "%.2f");
        });
        findViewById(R.id.btnApplySpan).setOnClickListener(v -> {
            triggerHaptic(v);
            hideKeyboard(v);
            recalculateAndRender(true);
        });

        findViewById(R.id.btnPresetTrueLevel).setOnClickListener(v -> {
            triggerHaptic(v);
            applyAnglePreset(0.05);
        });
        findViewById(R.id.btnPresetDrainSlope).setOnClickListener(v -> {
            triggerHaptic(v);
            applyAnglePreset(1.15);
        });
        findViewById(R.id.btnPresetAdaRamp).setOnClickListener(v -> {
            triggerHaptic(v);
            applyAnglePreset(4.76);
        });
        findViewById(R.id.btnPresetRoof412).setOnClickListener(v -> {
            triggerHaptic(v);
            applyAnglePreset(18.43);
        });

        // Panel 3: Save Slope Log
        findViewById(R.id.btnSaveSlopeLog).setOnClickListener(v -> {
            triggerHaptic(v);
            hideKeyboard(v);
            saveCurrentSlopeToDb();
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

        etRefAngle.addTextChangedListener(liveWatcher);
        etTareOffset.addTextChangedListener(liveWatcher);
        etSpanLength.addTextChangedListener(liveWatcher);
    }

    private void sanitizeInputsOnExplicitApply() {
        suppressTextWatcher = true;
        try {
            double ang = TiltEngine.parseSafeDouble(etRefAngle.getText().toString(), 4.76, 0.0, 85.0);
            double tare = TiltEngine.parseSafeDouble(etTareOffset.getText().toString(), 0.00, 0.0, 45.0);
            double span = TiltEngine.parseSafeDouble(etSpanLength.getText().toString(), 2.40, 0.10, 100.0);

            String rawAng = etRefAngle.getText().toString().trim();
            if (rawAng.isEmpty() || rawAng.length() > 5 || rawAng.startsWith("-")) {
                etRefAngle.setText(String.format(Locale.US, "%.2f", ang));
            }
            String rawTare = etTareOffset.getText().toString().trim();
            if (rawTare.isEmpty() || rawTare.length() > 5 || rawTare.startsWith("-")) {
                etTareOffset.setText(String.format(Locale.US, "%.2f", tare));
            }
            String rawSpan = etSpanLength.getText().toString().trim();
            if (rawSpan.isEmpty() || rawSpan.length() > 4 || rawSpan.startsWith("-")) {
                etSpanLength.setText(String.format(Locale.US, "%.2f", span));
            }
            saveDraftInputs();
        } finally {
            suppressTextWatcher = false;
        }
    }

    private void stepEditText(EditText target, double delta, double min, double max, String fmt) {
        manualCalLockUntilMs = SystemClock.elapsedRealtime() + 8000L;
        double current = TiltEngine.parseSafeDouble(target.getText().toString(), min, min, max);
        double next = TiltEngine.clamp(current + delta, min, max);
        suppressTextWatcher = true;
        target.setText(String.format(Locale.US, fmt, next));
        suppressTextWatcher = false;
        saveDraftInputs();
        recalculateAndRender(true);
    }

    private void setAxisMode(int mode) {
        axisMode = mode;
        prefs.edit().putInt(KEY_AXIS_MODE, axisMode).apply();
        updateAxisChips();
        recalculateAndRender(false);
    }

    private void applyAnglePreset(double angleDeg) {
        manualCalLockUntilMs = SystemClock.elapsedRealtime() + 8000L;
        suppressTextWatcher = true;
        etRefAngle.setText(String.format(Locale.US, "%.2f", angleDeg));
        etTareOffset.setText("0.00");
        suppressTextWatcher = false;
        saveDraftInputs();
        recalculateAndRender(true);
    }

    private void updateTabSelection(int tab) {
        activeTab = tab;
        panelMeter.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        panelRoof.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        panelLog.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);

        styleChipButton(btnTabMeter, tab == 0);
        styleChipButton(btnTabRoof, tab == 1);
        styleChipButton(btnTabLog, tab == 2);
    }

    private void updateAxisChips() {
        styleChipButton(btnAxisPitch, axisMode == TiltEngine.AXIS_PITCH);
        styleChipButton(btnAxisRoll, axisMode == TiltEngine.AXIS_ROLL);
        styleChipButton(btnAxisComposite, axisMode == TiltEngine.AXIS_COMPOSITE);
    }

    private void styleChipButton(Button btn, boolean selected) {
        btn.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btn.setTextColor(getColor(selected ? R.color.text_on_accent : R.color.text_primary));
    }

    private void recalculateAndRender(boolean forceFromReference) {
        double refAngle = TiltEngine.parseSafeDouble(etRefAngle.getText().toString(), 4.76, 0.0, 85.0);
        double tareOffset = TiltEngine.parseSafeDouble(etTareOffset.getText().toString(), 0.00, 0.0, 45.0);
        double spanMeters = TiltEngine.parseSafeDouble(etSpanLength.getText().toString(), 2.40, 0.10, 100.0);

        boolean useManual = forceFromReference
                || (SystemClock.elapsedRealtime() < manualCalLockUntilMs)
                || !hasHardwareReading;

        double pitchIn = useManual ? refAngle : livePitchDeg;
        double rollIn = useManual ? (refAngle * 0.25) : liveRollDeg;

        latestTelemetry = TiltEngine.evaluate(
                pitchIn,
                rollIn,
                tareOffset,
                spanMeters,
                axisMode
        );

        String modeLabel = isHoldLocked ? "HOLD LOCKED" : (hasHardwareReading && !useManual ? "LIVE 2-AXIS" : "REF MODE");
        tvSensorState.setText(modeLabel + " • " + latestTelemetry.axisLabel);
        tvZoneBadge.setText(latestTelemetry.zoneBadge);

        if (useGradePrimary) {
            tvHeroAngle.setText(String.format(Locale.US, "%.2f%%", latestTelemetry.gradePercent));
            tvHeroGradeSub.setText(String.format(Locale.US, "%.2f° • Net Angle", latestTelemetry.absAngleDeg));
        } else {
            tvHeroAngle.setText(String.format(Locale.US, "%.2f°", latestTelemetry.absAngleDeg));
            tvHeroGradeSub.setText(String.format(Locale.US, "%.2f%% Grade • Slope", latestTelemetry.gradePercent));
        }

        tvHeroPitch.setText(String.format(Locale.US, "%.2f : 12", latestTelemetry.roofPitchX12));
        tvHeroDropSub.setText(String.format(
                Locale.US,
                "%.1f mm/m • %.2f in/ft",
                latestTelemetry.dropMmPerMeter,
                latestTelemetry.dropInPerFoot));

        tvHeroAxes.setText(String.format(
                Locale.US,
                "P %+.1f° • R %+.1f°",
                latestTelemetry.rawPitchDeg,
                latestTelemetry.rawRollDeg));
        tvHeroTareSub.setText(String.format(Locale.US, "Tare Zero: %+.2f°", latestTelemetry.tareOffsetDeg));

        tvHeroSpanDrop.setText(String.format(
                Locale.US,
                "Rise %.0f mm (%.1f in)",
                latestTelemetry.spanRiseMm,
                latestTelemetry.spanRiseInches));
        tvHeroTargetSub.setText(String.format(Locale.US, "Over %.2f m Span", latestTelemetry.spanMeters));

        String hwStatus = (accelSensor != null)
                ? String.format(Locale.US, "MEMS Gravity: %+.1f° • Tare %.2f°", pitchIn, tareOffset)
                : String.format(Locale.US, "Ref Simulation • Tare %.2f°", tareOffset);
        tvSensorHardwareInfo.setText(hwStatus);

        tvSlopeDiagnosis.setText(latestTelemetry.diagnosisText);

        tiltDialView.updateReadings(
                latestTelemetry.netAngleDeg,
                latestTelemetry.gradePercent,
                latestTelemetry.roofPitchX12);
    }

    private void saveCurrentSlopeToDb() {
        if (latestTelemetry == null) {
            recalculateAndRender(false);
        }
        String site = etSiteName.getText().toString().trim();
        if (site.isEmpty()) {
            site = "Surface Check";
        }
        dbHelper.insertSlopeLog(
                site,
                latestTelemetry.absAngleDeg,
                latestTelemetry.gradePercent,
                latestTelemetry.roofPitchX12,
                latestTelemetry.tareOffsetDeg,
                latestTelemetry.spanMeters,
                axisMode
        );
        refreshSlopeLogList();
        Toast.makeText(this, "Saved slope log for " + site, Toast.LENGTH_SHORT).show();
    }

    private void refreshSlopeLogList() {
        llSlopeLogList.removeAllViews();
        List<TiltDatabaseHelper.SlopeRecord> records = dbHelper.getAllLogs();
        int padPx = dpToPx(8f);
        int marginPx = dpToPx(6f);

        for (final TiltDatabaseHelper.SlopeRecord rec : records) {
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
            tvTitle.setText(rec.siteName);
            tvTitle.setTextColor(getColor(R.color.text_primary));
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            tvTitle.getPaint().setFakeBoldText(true);

            TextView tvSub = new TextView(this);
            tvSub.setText(String.format(
                    Locale.US,
                    "%.2f° • %.2f%% Grade • %.2f:12 Pitch • Span %.1fm",
                    rec.netAngleDeg,
                    rec.gradePercent,
                    rec.roofPitchX12,
                    rec.spanMeters));
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
                etRefAngle.setText(String.format(Locale.US, "%.2f", rec.netAngleDeg + rec.tareOffsetDeg));
                etTareOffset.setText(String.format(Locale.US, "%.2f", rec.tareOffsetDeg));
                etSpanLength.setText(String.format(Locale.US, "%.2f", rec.spanMeters));
                etSiteName.setText(rec.siteName);
                suppressTextWatcher = false;
                axisMode = rec.axisMode;
                updateAxisChips();
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
                dbHelper.deleteSlopeLog(rec.id);
                refreshSlopeLogList();
            });

            row.addView(textCol);
            row.addView(btnLoad);
            row.addView(btnDel);
            llSlopeLogList.addView(row);
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
        Button btnUnitDeg = dialogView.findViewById(R.id.btnUnitDeg);
        Button btnUnitGrade = dialogView.findViewById(R.id.btnUnitGrade);
        Switch swHaptics = dialogView.findViewById(R.id.swHaptics);
        Button btnPrivacy = dialogView.findViewById(R.id.btnPrivacyPolicy);
        Button btnReset = dialogView.findViewById(R.id.btnResetDefaults);
        Button btnClose = dialogView.findViewById(R.id.btnCloseSettings);

        int themeMode = prefs.getInt(KEY_THEME_MODE, 2);
        styleChipButton(btnThemeLight, themeMode == 1);
        styleChipButton(btnThemeDark, themeMode == 2);
        styleChipButton(btnThemeAuto, themeMode == 0);

        styleChipButton(btnUnitDeg, !useGradePrimary);
        styleChipButton(btnUnitGrade, useGradePrimary);
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

        btnUnitDeg.setOnClickListener(v -> {
            useGradePrimary = false;
            prefs.edit().putBoolean(KEY_PRIMARY_UNIT_GRADE, false).apply();
            styleChipButton(btnUnitDeg, true);
            styleChipButton(btnUnitGrade, false);
            recalculateAndRender(false);
        });
        btnUnitGrade.setOnClickListener(v -> {
            useGradePrimary = true;
            prefs.edit().putBoolean(KEY_PRIMARY_UNIT_GRADE, true).apply();
            styleChipButton(btnUnitDeg, false);
            styleChipButton(btnUnitGrade, true);
            recalculateAndRender(false);
        });

        swHaptics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            hapticsEnabled = isChecked;
            prefs.edit().putBoolean(KEY_HAPTICS, isChecked).apply();
        });

        btnPrivacy.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Offline Privacy Guarantee")
                    .setMessage("TiltLevel operates 100% offline on your device. Hardware accelerometer tilt readings and SQLite surface logs never leave your phone. Zero network permissions, zero trackers, zero ads.")
                    .setPositiveButton("Close", null)
                    .show();
        });

        btnReset.setOnClickListener(v -> {
            suppressTextWatcher = true;
            etRefAngle.setText("4.76");
            etTareOffset.setText("0.00");
            etSpanLength.setText("2.40");
            etSiteName.setText("Workshop Table");
            suppressTextWatcher = false;
            axisMode = TiltEngine.AXIS_PITCH;
            saveDraftInputs();
            updateAxisChips();
            recalculateAndRender(true);
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void saveDraftInputs() {
        String ang = etRefAngle.getText().toString().trim();
        String tare = etTareOffset.getText().toString().trim();
        String span = etSpanLength.getText().toString().trim();
        String site = etSiteName.getText().toString().trim();

        if (ang.length() <= 6 && !ang.startsWith("-")) {
            prefs.edit().putString(KEY_DRAFT_ANGLE, ang).apply();
        }
        if (tare.length() <= 6 && !tare.startsWith("-")) {
            prefs.edit().putString(KEY_DRAFT_TARE, tare).apply();
        }
        if (span.length() <= 5 && !span.startsWith("-")) {
            prefs.edit().putString(KEY_DRAFT_SPAN, span).apply();
        }
        if (site.length() <= 22) {
            prefs.edit().putString(KEY_DRAFT_SITE, site).apply();
        }
    }

    private void restoreDraftInputs() {
        suppressTextWatcher = true;
        try {
            String ang = prefs.getString(KEY_DRAFT_ANGLE, "4.76");
            String tare = prefs.getString(KEY_DRAFT_TARE, "0.00");
            String span = prefs.getString(KEY_DRAFT_SPAN, "2.40");
            String site = prefs.getString(KEY_DRAFT_SITE, "Workshop Table");

            double safeAng = TiltEngine.parseSafeDouble(ang, 4.76, 0.0, 85.0);
            double safeTare = TiltEngine.parseSafeDouble(tare, 0.00, 0.0, 45.0);
            double safeSpan = TiltEngine.parseSafeDouble(span, 2.40, 0.10, 100.0);

            etRefAngle.setText(String.format(Locale.US, "%.2f", safeAng));
            etTareOffset.setText(String.format(Locale.US, "%.2f", safeTare));
            etSpanLength.setText(String.format(Locale.US, "%.2f", safeSpan));
            etSiteName.setText((site == null || site.isEmpty() || site.length() > 22) ? "Workshop Table" : site);
        } finally {
            suppressTextWatcher = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelSensor != null) {
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI);
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
        if (event == null || event.values == null || event.values.length < 3) {
            return;
        }
        if (isHoldLocked) {
            return;
        }
        float gx = event.values[0];
        float gy = event.values[1];
        float gz = event.values[2];
        if (Float.isNaN(gx) || Float.isNaN(gy) || Float.isNaN(gz)) {
            return;
        }
        double norm = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (norm < 0.1) {
            return;
        }
        double pitch = Math.toDegrees(Math.atan2(gy, Math.hypot(gx, gz)));
        double roll = Math.toDegrees(Math.atan2(-gx, Math.hypot(gy, gz)));

        if (!hasHardwareReading || Math.abs(pitch - livePitchDeg) >= 0.8 || Math.abs(roll - liveRollDeg) >= 0.8) {
            hasHardwareReading = true;
            livePitchDeg = TiltEngine.clamp(pitch, -88.0, 88.0);
            liveRollDeg = TiltEngine.clamp(roll, -88.0, 88.0);
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
