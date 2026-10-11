package com.kimrosil.turfnotch;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CombinedVibration;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    public static final String ACTION_DECREMENT_PASS = "com.kimrosil.turfnotch.ACTION_DECREMENT_PASS";
    public static final String ACTION_INCREMENT_PASS = "com.kimrosil.turfnotch.ACTION_INCREMENT_PASS";
    public static final String ACTION_TOGGLE_PACER = "com.kimrosil.turfnotch.ACTION_TOGGLE_PACER";

    private static final String PREFS_NAME = "turf-notch_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark, 3=Sunlight HUD
    private static final String NOTIF_CHANNEL_ID = "turfnotch_hud_channel";
    private static final int NOTIF_ID = 1001;

    // Views
    private Button btnThemeToggle;
    private Spinner spSpreaderModel;
    private EditText etTargetRate;
    private EditText etLawnArea;
    private Button btnUnitToggle;
    private TextView tvNotchResult;
    private TextView tvDialDescription;
    private TextView tvPassCount;
    private Button btnMinusPass;
    private Button btnPlusPass;
    private Button btnResetPasses;
    private Button btnTogglePacer;
    private TextView tvPaceStatus;
    private SeekBar sbTargetSpeed;
    private TextView tvSpeedLabel;
    private Button btnOpenDetail;
    private Button btnSaveSession;
    private LinearLayout llEmptyState;
    private LinearLayout llActiveDeck;
    private LinearLayout llAdContainer;
    private TextView tvAdNotice;
    private DialView dialViewControl;
    private Button btnDialMinus;
    private Button btnDialPlus;

    // Database & System Hardware Services
    private DatabaseHelper dbHelper;
    private Vibrator vibrator;
    private VibratorManager vibratorManager;
    private ToneGenerator toneGenerator;
    private NotificationManager notificationManager;

    // Pacing Metronome State
    private final Handler pacerHandler = new Handler(Looper.getMainLooper());
    private boolean isPacingActive = false;
    private double targetSpeedMph = 3.0; // default 3.0 mph = ~88 paces/min
    private int currentPasses = 0;
    private int totalPassesNeeded = 1;
    private boolean isMetricUnits = false; // false = lbs/1k sq ft, true = g/m²
    private double manualDialOffset = 0.0;

    private final Runnable pacerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPacingActive) return;
            triggerCadencePulse();
            // 3.0 mph is approx 88 paces/min. Interval (ms) = 60000 / (mph * 29.33)
            int cadenceBpm = (int) Math.round(targetSpeedMph * 29.33);
            if (cadenceBpm < 40) cadenceBpm = 40;
            if (cadenceBpm > 160) cadenceBpm = 160;
            long intervalMs = 60000L / cadenceBpm;
            pacerHandler.postDelayed(this, intervalMs);
        }
    };

    /**
     * Tactile Circular Dial Wheel Control for Spreader Gate Notches
     */
    public static class DialView extends View {
        private Paint rimPaint;
        private Paint tickPaint;
        private Paint needlePaint;
        private Paint centerPaint;
        private Paint textPaint;
        private RectF dialRect = new RectF();
        private float currentAngle = 0f;
        private float maxAngle = 270f;
        private OnDialChangeListener listener;

        public interface OnDialChangeListener {
            void onDialAdjusted(float normalizedValue, boolean fromUser);
        }

        public DialView(Context context) {
            super(context);
            init();
        }

        public DialView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public DialView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        private void init() {
            rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setStrokeWidth(12f);
            rimPaint.setColor(0xFF006495);

            tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tickPaint.setStrokeWidth(3f);
            tickPaint.setColor(0xFF72787E);

            needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            needlePaint.setStrokeWidth(8f);
            needlePaint.setStrokeCap(Paint.Cap.ROUND);
            needlePaint.setColor(0xFFBA1A1A);

            centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            centerPaint.setStyle(Paint.Style.FILL);
            centerPaint.setColor(0xFF191C1F);

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(26f);
            textPaint.setColor(0xFF41474D);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        public void setOnDialChangeListener(OnDialChangeListener listener) {
            this.listener = listener;
        }

        public void setProgress(float fraction) {
            if (fraction < 0f) fraction = 0f;
            if (fraction > 1f) fraction = 1f;
            this.currentAngle = fraction * maxAngle;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
            if (size <= 0) size = 260;
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float cx = width / 2f;
            float cy = height / 2f;
            float radius = Math.min(cx, cy) - 24f;

            dialRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

            // Rim arc (135 deg to 405 deg)
            canvas.drawArc(dialRect, 135f, maxAngle, false, rimPaint);

            // Circular tick marks
            int ticks = 18;
            for (int i = 0; i <= ticks; i++) {
                float tickAngle = 135f + ((float) i / ticks) * maxAngle;
                double rad = Math.toRadians(tickAngle);
                float innerR = radius - (i % 3 == 0 ? 20f : 10f);
                float x1 = (float) (cx + Math.cos(rad) * radius);
                float y1 = (float) (cy + Math.sin(rad) * radius);
                float x2 = (float) (cx + Math.cos(rad) * innerR);
                float y2 = (float) (cy + Math.sin(rad) * innerR);
                canvas.drawLine(x1, y1, x2, y2, tickPaint);
            }

            // Indicator Needle
            float needleAngle = 135f + currentAngle;
            double nRad = Math.toRadians(needleAngle);
            float needleLen = radius - 26f;
            float nx = (float) (cx + Math.cos(nRad) * needleLen);
            float ny = (float) (cy + Math.sin(nRad) * needleLen);
            canvas.drawLine(cx, cy, nx, ny, needlePaint);

            // Hub center
            canvas.drawCircle(cx, cy, 14f, centerPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float dx = event.getX() - cx;
                float dy = event.getY() - cy;
                double theta = Math.toDegrees(Math.atan2(dy, dx));
                if (theta < 0) theta += 360.0;

                // Adjust relative to start angle (135 degrees)
                double shifted = theta - 135.0;
                if (shifted < 0) shifted += 360.0;
                if (shifted <= maxAngle) {
                    currentAngle = (float) shifted;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    invalidate();
                    if (listener != null) {
                        listener.onDialAdjusted(currentAngle / maxAngle, true);
                    }
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else if (themeMode == 3) {
            // High-Contrast Sunlight Mode (Dark base with yellow accents)
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNightMode = 0;
            try {
                systemNightMode = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {}
            if (systemNightMode == 2) {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            } else {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            }
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vibratorManager != null) {
                vibrator = vibratorManager.getDefaultVibrator();
            }
        }
        if (vibrator == null) {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75);
        } catch (Exception ignored) {}

        createNotificationChannel();
        initViews();
        setupSpreaderSpinner();
        setupListeners();
        updateThemeToggleButtonText();
        setupStatusBarTheme();
        initMonetization();
        handleIntent(getIntent());
        recalculateNotch();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (ACTION_DECREMENT_PASS.equals(action)) {
            if (currentPasses > 0) {
                currentPasses--;
                updatePassUI();
            }
        } else if (ACTION_INCREMENT_PASS.equals(action)) {
            currentPasses++;
            updatePassUI();
        } else if (ACTION_TOGGLE_PACER.equals(action)) {
            togglePacer();
        }
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        spSpreaderModel = findViewById(R.id.spSpreaderModel);
        etTargetRate = findViewById(R.id.etTargetRate);
        etLawnArea = findViewById(R.id.etLawnArea);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        tvNotchResult = findViewById(R.id.tvNotchResult);
        tvDialDescription = findViewById(R.id.tvDialDescription);
        tvPassCount = findViewById(R.id.tvPassCount);
        btnMinusPass = findViewById(R.id.btnMinusPass);
        btnPlusPass = findViewById(R.id.btnPlusPass);
        btnResetPasses = findViewById(R.id.btnResetPasses);
        btnTogglePacer = findViewById(R.id.btnTogglePacer);
        tvPaceStatus = findViewById(R.id.tvPaceStatus);
        sbTargetSpeed = findViewById(R.id.sbTargetSpeed);
        tvSpeedLabel = findViewById(R.id.tvSpeedLabel);
        btnOpenDetail = findViewById(R.id.btnOpenDetail);
        btnSaveSession = findViewById(R.id.btnSaveSession);
        llEmptyState = findViewById(R.id.llEmptyState);
        llActiveDeck = findViewById(R.id.llActiveDeck);
        llAdContainer = findViewById(R.id.llAdContainer);
        tvAdNotice = findViewById(R.id.tvAdNotice);
        dialViewControl = findViewById(R.id.dialViewControl);
        btnDialMinus = findViewById(R.id.btnDialMinus);
        btnDialPlus = findViewById(R.id.btnDialPlus);

        // Preload defaults
        etTargetRate.setText("3.5");
        etLawnArea.setText("5000");
        sbTargetSpeed.setMax(20); // 2.0 to 4.0 mph (step = 0.1)
        sbTargetSpeed.setProgress(10); // 3.0 mph
        updateSpeedLabel();

        // Enforce Accessibility Live Regions & Descriptions
        tvNotchResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        tvDialDescription.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
    }

    private void setupSpreaderSpinner() {
        String[] spreaderModels = {
                "Scotts Standard Rotary / EdgeGuard (0 - 18)",
                "Scotts Drop Spreader (0 - 18)",
                "Earthway EV-N-SPRED Rotary (0 - 30)",
                "Chapin Residential Broadcast (0 - 30)",
                "Agri-Fab Tow Broadcast (0 - 10)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spreaderModels);
        spSpreaderModel.setAdapter(adapter);
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            cycleThemeMode();
        });

        if (btnUnitToggle != null) {
            btnUnitToggle.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                toggleUnitMeasurement();
            });
        }

        spSpreaderModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                manualDialOffset = 0.0;
                recalculateNotch();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                recalculateNotch();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };
        etTargetRate.addTextChangedListener(textWatcher);
        etLawnArea.addTextChangedListener(textWatcher);

        if (dialViewControl != null) {
            dialViewControl.setOnDialChangeListener((fraction, fromUser) -> {
                if (fromUser) {
                    double maxScale = getSpreaderMaxScale(spSpreaderModel.getSelectedItemPosition());
                    double calculatedSetting = fraction * maxScale;
                    manualDialOffset = calculatedSetting;
                    applyCustomDialSetting(calculatedSetting);
                }
            });
        }

        if (btnDialMinus != null) {
            btnDialMinus.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                adjustDialManually(-0.5);
            });
        }

        if (btnDialPlus != null) {
            btnDialPlus.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                adjustDialManually(0.5);
            });
        }

        sbTargetSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                targetSpeedMph = 2.0 + (progress * 0.1);
                updateSpeedLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isPacingActive) {
                    pacerHandler.removeCallbacks(pacerRunnable);
                    pacerHandler.post(pacerRunnable);
                }
            }
        });

        btnTogglePacer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            togglePacer();
        });

        btnPlusPass.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            currentPasses++;
            updatePassUI();
        });

        btnMinusPass.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (currentPasses > 0) {
                currentPasses--;
                updatePassUI();
            }
        });

        btnResetPasses.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            currentPasses = 0;
            updatePassUI();
            Toast.makeText(this, "Pass counter reset", Toast.LENGTH_SHORT).show();
        });

        btnOpenDetail.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            openDetailActivity();
        });

        btnSaveSession.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            saveCurrentSession();
        });
    }

    private void toggleUnitMeasurement() {
        isMetricUnits = !isMetricUnits;
        String rateStr = etTargetRate.getText().toString().trim();
        String areaStr = etLawnArea.getText().toString().trim();

        try {
            if (!TextUtils.isEmpty(rateStr) && !TextUtils.isEmpty(areaStr)) {
                double r = Double.parseDouble(rateStr);
                double a = Double.parseDouble(areaStr);
                if (isMetricUnits) {
                    // lbs/1k sq ft -> g/m² (1 lb/1000 sq ft ≈ 4.8824 g/m²)
                    double gRate = r * 4.8824;
                    // sq ft -> m² (1 sq ft ≈ 0.0929 m²)
                    double mArea = a * 0.0929;
                    etTargetRate.setText(String.format(Locale.US, "%.1f", gRate));
                    etLawnArea.setText(String.format(Locale.US, "%.0f", mArea));
                    btnUnitToggle.setText("Metric (g/m²)");
                } else {
                    // g/m² -> lbs/1k sq ft
                    double lbsRate = r / 4.8824;
                    double sqftArea = a / 0.0929;
                    etTargetRate.setText(String.format(Locale.US, "%.1f", lbsRate));
                    etLawnArea.setText(String.format(Locale.US, "%.0f", sqftArea));
                    btnUnitToggle.setText("Imperial (lbs/1k sq ft)");
                }
            }
        } catch (Exception ignored) {}
        recalculateNotch();
    }

    private double getSpreaderMaxScale(int spreaderIndex) {
        switch (spreaderIndex) {
            case 0:
            case 1:
                return 18.0;
            case 2:
            case 3:
                return 30.0;
            case 4:
                return 10.0;
            default:
                return 18.0;
        }
    }

    private void adjustDialManually(double delta) {
        String notchStr = tvNotchResult.getText().toString();
        double currentVal = 4.0;
        try {
            currentVal = Double.parseDouble(notchStr);
        } catch (Exception ignored) {}
        double maxScale = getSpreaderMaxScale(spSpreaderModel.getSelectedItemPosition());
        double updatedVal = Math.max(0.0, Math.min(maxScale, currentVal + delta));
        applyCustomDialSetting(updatedVal);
    }

    private void applyCustomDialSetting(double dialVal) {
        String dialFormatted = String.format(Locale.US, "%.1f", dialVal);
        tvNotchResult.setText(dialFormatted);
        String desc = "Manual Notch Override: " + dialFormatted + ". Verify flow rate with 100 sq ft catch tray.";
        tvDialDescription.setText(desc);
        tvNotchResult.setContentDescription("Calculated gate notch dial setting: " + dialFormatted);
        tvDialDescription.setContentDescription(desc);
        tvNotchResult.announceForAccessibility("Notch updated to " + dialFormatted);

        if (dialViewControl != null) {
            double maxScale = getSpreaderMaxScale(spSpreaderModel.getSelectedItemPosition());
            dialViewControl.setProgress((float) (dialVal / maxScale));
        }
    }

    private void recalculateNotch() {
        String rateStr = etTargetRate.getText().toString().trim();
        String areaStr = etLawnArea.getText().toString().trim();

        if (TextUtils.isEmpty(rateStr) || TextUtils.isEmpty(areaStr)) {
            llEmptyState.setVisibility(View.VISIBLE);
            llActiveDeck.setVisibility(View.GONE);
            tvNotchResult.setText("--");
            tvDialDescription.setText("Enter rate and yard area to calibrate");
            tvNotchResult.setContentDescription("Recommended gate notch is currently empty");
            tvDialDescription.setContentDescription("Enter rate and yard area to calibrate");
            return;
        }

        double targetRate;
        double lawnArea;
        try {
            targetRate = Double.parseDouble(rateStr);
            lawnArea = Double.parseDouble(areaStr);
        } catch (NumberFormatException e) {
            return;
        }

        if (targetRate <= 0 || lawnArea <= 0) {
            llEmptyState.setVisibility(View.VISIBLE);
            llActiveDeck.setVisibility(View.GONE);
            tvNotchResult.setText("--");
            tvNotchResult.setContentDescription("Recommended gate notch is currently empty");
            return;
        }

        llEmptyState.setVisibility(View.GONE);
        llActiveDeck.setVisibility(View.VISIBLE);

        // Convert to imperial rate for algorithm if in metric
        double rateInLbsPer1k = isMetricUnits ? (targetRate / 4.8824) : targetRate;
        double areaInSqFt = isMetricUnits ? (lawnArea / 0.0929) : lawnArea;

        int spreaderIndex = spSpreaderModel.getSelectedItemPosition();
        PresetManager.NotchResult result = PresetManager.calculateNotch(spreaderIndex, rateInLbsPer1k);

        tvNotchResult.setText(result.dialSetting);
        tvDialDescription.setText(result.guidance);

        // Accessibility content descriptions and announcements
        tvNotchResult.setContentDescription("Recommended gate notch dial: " + result.dialSetting);
        tvDialDescription.setContentDescription(result.guidance);
        tvNotchResult.announceForAccessibility("Recommended gate notch is " + result.dialSetting);

        // Update circular Dial View
        try {
            double parsedSetting = Double.parseDouble(result.dialSetting);
            double maxScale = getSpreaderMaxScale(spreaderIndex);
            if (dialViewControl != null) {
                dialViewControl.setProgress((float) (parsedSetting / maxScale));
            }
        } catch (Exception ignored) {}

        // Estimate total passes assuming a 5 ft spreader swath
        double swathWidthFt = 5.0;
        double linearFeet = areaInSqFt / swathWidthFt;
        totalPassesNeeded = Math.max(1, (int) Math.ceil(linearFeet / 100.0));
        updatePassUI();
    }

    private void updateSpeedLabel() {
        int cadenceBpm = (int) Math.round(targetSpeedMph * 29.33);
        tvSpeedLabel.setText(String.format(Locale.US, "Target Walking Pace: %.1f mph (%d steps/min)", targetSpeedMph, cadenceBpm));
        sbTargetSpeed.setContentDescription(String.format(Locale.US, "Adjust target walking pace. Current pace is %.1f miles per hour or %d steps per minute", targetSpeedMph, cadenceBpm));
    }

    private void togglePacer() {
        if (isPacingActive) {
            isPacingActive = false;
            pacerHandler.removeCallbacks(pacerRunnable);
            btnTogglePacer.setText("Start Cadence Metronome");
            tvPaceStatus.setText("Metronome Paused");
            clearOngoingNotification();
        } else {
            isPacingActive = true;
            pacerHandler.post(pacerRunnable);
            btnTogglePacer.setText("Stop Cadence Metronome");
            tvPaceStatus.setText("Pacer Active (Keep in pocket for rhythm)");
            updateOngoingNotification();
        }
    }

    private void triggerCadencePulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManager != null) {
                CombinedVibration combinedVibration = CombinedVibration.createParallel(
                        VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE)
                );
                vibratorManager.vibrate(combinedVibration);
            } else if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(55);
                }
            }
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 35);
            }
        } catch (Exception ignored) {}
    }

    private void updatePassUI() {
        tvPassCount.setText(String.format(Locale.US, "%d / ~%d Passes", currentPasses, totalPassesNeeded));
        tvPassCount.setContentDescription(String.format(Locale.US, "Pass counter: %d out of %d estimated passes completed", currentPasses, totalPassesNeeded));
        if (isPacingActive) {
            updateOngoingNotification();
        }
    }

    private void openDetailActivity() {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("EXTRA_SPREADER_INDEX", spSpreaderModel.getSelectedItemPosition());
        intent.putExtra("EXTRA_TARGET_RATE", etTargetRate.getText().toString());
        intent.putExtra("EXTRA_LAWN_AREA", etLawnArea.getText().toString());
        intent.putExtra("EXTRA_IS_METRIC", isMetricUnits);
        startActivity(intent);
    }

    private void saveCurrentSession() {
        String spreader = (String) spSpreaderModel.getSelectedItem();
        String rate = etTargetRate.getText().toString();
        String area = etLawnArea.getText().toString();
        String notch = tvNotchResult.getText().toString();

        long id = dbHelper.insertSession(spreader, rate, area, notch, currentPasses);
        if (id != -1) {
            Toast.makeText(this, "Session saved to offline logbook", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to save session", Toast.LENGTH_SHORT).show();
        }
    }

    private void cycleThemeMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int current = prefs.getInt(PREF_THEME_MODE, 0);
        int next = (current + 1) % 4; // 0=Auto -> 1=Light -> 2=Dark -> 3=Sunlight HUD -> 0
        prefs.edit().putInt(PREF_THEME_MODE, next).apply();
        recreate();
    }

    private void updateThemeToggleButtonText() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int mode = prefs.getInt(PREF_THEME_MODE, 0);
        if (mode == 1) {
            btnThemeToggle.setText("Theme: Light");
        } else if (mode == 2) {
            btnThemeToggle.setText("Theme: Dark");
        } else if (mode == 3) {
            btnThemeToggle.setText("Theme: Sunlight HUD");
        } else {
            btnThemeToggle.setText("Theme: Auto");
        }
    }

    private void setupStatusBarTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (nightMode == Configuration.UI_MODE_NIGHT_NO) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }
    }

    private void initMonetization() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isPro = prefs.getBoolean("is_pro_user", false);
        if (isPro) {
            llAdContainer.setVisibility(View.GONE);
        } else {
            llAdContainer.setVisibility(View.VISIBLE);
            tvAdNotice.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com"));
                try {
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Opening KimRosil App Studio...", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "TurfNotch Spreader Metronome",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active walking pace and pass count on lockscreen with remote actions");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void updateOngoingNotification() {
        if (notificationManager == null) return;

        // Content Open Intent
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingOpenIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Remote Action Intent: Decrement Pass
        Intent decIntent = new Intent(this, MainActivity.class);
        decIntent.setAction(ACTION_DECREMENT_PASS);
        PendingIntent decPending = PendingIntent.getActivity(
                this, 1, decIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Remote Action Intent: Increment Pass
        Intent incIntent = new Intent(this, MainActivity.class);
        incIntent.setAction(ACTION_INCREMENT_PASS);
        PendingIntent incPending = PendingIntent.getActivity(
                this, 2, incIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Remote Action Intent: Toggle Pacer
        Intent toggleIntent = new Intent(this, MainActivity.class);
        toggleIntent.setAction(ACTION_TOGGLE_PACER);
        PendingIntent togglePending = PendingIntent.getActivity(
                this, 3, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, NOTIF_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(String.format(Locale.US, "TurfNotch Pace HUD (%.1f mph / 88 bpm)", targetSpeedMph))
                .setContentText(String.format(Locale.US, "Passes: %d / ~%d | Dial: %s", currentPasses, totalPassesNeeded, tvNotchResult.getText()))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pendingOpenIntent)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_previous, "-1 Pass", decPending).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_next, "+1 Pass", incPending).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, isPacingActive ? "Pause" : "Start", togglePending).build());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
            builder.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2));
        }

        notificationManager.notify(NOTIF_ID, builder.build());
    }

    private void clearOngoingNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIF_ID);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                recalculateNotch();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                togglePacer();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                currentPasses++;
                updatePassUI();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
                if (currentPasses > 0) {
                    currentPasses--;
                    updatePassUI();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                etTargetRate.setText("");
                etLawnArea.setText("");
                recalculateNotch();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isPacingActive = false;
        pacerHandler.removeCallbacks(pacerRunnable);
        clearOngoingNotification();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}