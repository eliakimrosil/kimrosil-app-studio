package com.aistudio.sourdoughriseferment;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "sourdough-rise-ferment-sentinel_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_HISTORY = "history_entries";
    private static final String KEY_INITIAL_HEIGHT = "initial_height";
    private static final String KEY_TARGET_RISE = "target_rise_pct";
    private static final String KEY_AMBIENT_TEMP = "ambient_temp";
    private static final String KEY_HYDRATION = "hydration_pct";
    private static final String KEY_FLOUR_TYPE_INDEX = "flour_type_idx";
    private static final String KEY_TIMER_RUNNING = "timer_running";
    private static final String KEY_TIMER_START_TIME = "timer_start_time";
    private static final String KEY_TIMER_ELAPSED_OFFSET = "timer_elapsed_offset";
    private static final String KEY_ALARM_ENABLED = "alarm_enabled";

    // Header views
    private Button btnThemeToggle;
    private TextView tvStatusBadge;

    // Calculator inputs & controls
    private EditText etInitialHeight;
    private EditText etCurrentHeight;
    private TextView tvTargetRiseDisplay;
    private SeekBar sbTargetRise;
    private TextView tvTempDisplay;
    private SeekBar sbTemp;
    private TextView tvHydrationDisplay;
    private SeekBar sbHydration;
    private Button btnFlourType;
    private Switch swAlarmAlert;

    // Telemetry displays
    private TextView tvCalculatedRise;
    private TextView tvEstimatedRemaining;
    private TextView tvFermentStage;
    private TextView tvTimerCountdown;

    // Action buttons
    private Button btnStartStopTimer;
    private Button btnResetTimer;
    private Button btnLogBatch;
    private Button btnShareTelemetry;
    private Button btnClearHistory;

    // History container
    private LinearLayout llHistoryContainer;

    // State Variables
    private int themeMode = 0;
    private int flourTypeIdx = 0; // 0=White/AP, 1=Whole Wheat, 2=Rye, 3=Blend
    private final String[] flourNames = {"White / Bread Flour (1.0x)", "Whole Wheat (1.25x)", "Rye (1.40x)", "Multi-Grain Blend (1.15x)"};
    private final double[] flourSpeedFactors = {1.0, 1.25, 1.40, 1.15};

    private boolean isTimerRunning = false;
    private long timerStartTime = 0;
    private long elapsedMillisBeforePause = 0;
    private boolean alarmTriggered = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                updateTimerAndCalculations();
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int mode = sp.getInt(KEY_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark
        int nightMode = Configuration.UI_MODE_NIGHT_NO;

        if (mode == 0) { // Auto
            int sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (sysNight == 2) {
                nightMode = Configuration.UI_MODE_NIGHT_YES;
            } else if (sysNight == 1) {
                nightMode = Configuration.UI_MODE_NIGHT_NO;
            } else {
                int currentNight = newBase.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                nightMode = (currentNight == Configuration.UI_MODE_NIGHT_YES) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
            }
        } else if (mode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initSystemServices();
        initViews();
        setupListeners();
        restoreState();
        applyWindowInsetsTheme();
        updateThemeButtonLabel();
        renderHistory();

        if (isTimerRunning) {
            timerHandler.post(timerRunnable);
        } else {
            updateTimerAndCalculations();
        }
    }

    private void initSystemServices() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
        } catch (Exception ignored) {
            toneGenerator = null;
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void applyWindowInsetsTheme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int currentNight = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (currentNight == Configuration.UI_MODE_NIGHT_NO) {
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
        }
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);

        etInitialHeight = findViewById(R.id.etInitialHeight);
        etCurrentHeight = findViewById(R.id.etCurrentHeight);
        tvTargetRiseDisplay = findViewById(R.id.tvTargetRiseDisplay);
        sbTargetRise = findViewById(R.id.sbTargetRise);
        tvTempDisplay = findViewById(R.id.tvTempDisplay);
        sbTemp = findViewById(R.id.sbTemp);
        tvHydrationDisplay = findViewById(R.id.tvHydrationDisplay);
        sbHydration = findViewById(R.id.sbHydration);
        btnFlourType = findViewById(R.id.btnFlourType);
        swAlarmAlert = findViewById(R.id.swAlarmAlert);

        tvCalculatedRise = findViewById(R.id.tvCalculatedRise);
        tvEstimatedRemaining = findViewById(R.id.tvEstimatedRemaining);
        tvFermentStage = findViewById(R.id.tvFermentStage);
        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);

        btnStartStopTimer = findViewById(R.id.btnStartStopTimer);
        btnResetTimer = findViewById(R.id.btnResetTimer);
        btnLogBatch = findViewById(R.id.btnLogBatch);
        btnShareTelemetry = findViewById(R.id.btnShareTelemetry);
        btnClearHistory = findViewById(R.id.btnClearHistory);

        llHistoryContainer = findViewById(R.id.llHistoryContainer);
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnFlourType.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            flourTypeIdx = (flourTypeIdx + 1) % flourNames.length;
            btnFlourType.setText(flourNames[flourTypeIdx]);
            persistState();
            updateTimerAndCalculations();
        });

        sbTargetRise.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int riseVal = progress + 20; // 20% to 200%
                tvTargetRiseDisplay.setText(riseVal + "% Target Vol");
                if (fromUser) {
                    persistState();
                    updateTimerAndCalculations();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbTemp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int tempVal = progress + 15; // 15°C to 35°C (59°F to 95°F)
                int fahrenheit = (int) Math.round((tempVal * 9.0 / 5.0) + 32);
                tvTempDisplay.setText(tempVal + "°C / " + fahrenheit + "°F");
                if (fromUser) {
                    persistState();
                    updateTimerAndCalculations();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbHydration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int hydration = progress + 50; // 50% to 100%
                tvHydrationDisplay.setText(hydration + "% Hydration");
                if (fromUser) {
                    persistState();
                    updateTimerAndCalculations();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnStartStopTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleTimer();
        });

        btnResetTimer.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetTimer();
        });

        btnLogBatch.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            logCurrentBatch();
        });

        btnShareTelemetry.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareTelemetryReport();
        });

        btnClearHistory.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            clearHistory();
        });

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (!hasFocus) {
                persistState();
                updateTimerAndCalculations();
            }
        };
        etInitialHeight.setOnFocusChangeListener(focusListener);
        etCurrentHeight.setOnFocusChangeListener(focusListener);

        swAlarmAlert.setOnCheckedChangeListener((buttonView, isChecked) -> persistState());
    }

    private void cycleThemeMode() {
        themeMode = (themeMode + 1) % 3;
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit().putInt(KEY_THEME_MODE, themeMode).apply();
        recreate();
    }

    private void updateThemeButtonLabel() {
        if (themeMode == 0) {
            btnThemeToggle.setText("Auto Theme");
        } else if (themeMode == 1) {
            btnThemeToggle.setText("Light Theme");
        } else {
            btnThemeToggle.setText("Dark Theme");
        }
    }

    private void toggleTimer() {
        if (isTimerRunning) {
            isTimerRunning = false;
            elapsedMillisBeforePause += (System.currentTimeMillis() - timerStartTime);
            btnStartStopTimer.setText("Resume Monitor");
            tvStatusBadge.setText("PAUSED");
            timerHandler.removeCallbacks(timerRunnable);
        } else {
            isTimerRunning = true;
            timerStartTime = System.currentTimeMillis();
            btnStartStopTimer.setText("Pause Monitor");
            tvStatusBadge.setText("ACTIVE");
            alarmTriggered = false;
            timerHandler.post(timerRunnable);
        }
        persistState();
    }

    private void resetTimer() {
        isTimerRunning = false;
        timerStartTime = 0;
        elapsedMillisBeforePause = 0;
        alarmTriggered = false;
        timerHandler.removeCallbacks(timerRunnable);
        btnStartStopTimer.setText("Start Monitor");
        tvStatusBadge.setText("IDLE");
        persistState();
        updateTimerAndCalculations();
    }

    private long getTotalElapsedMillis() {
        if (isTimerRunning) {
            return elapsedMillisBeforePause + (System.currentTimeMillis() - timerStartTime);
        } else {
            return elapsedMillisBeforePause;
        }
    }

    private void updateTimerAndCalculations() {
        long elapsedSec = getTotalElapsedMillis() / 1000;
        long hours = elapsedSec / 3600;
        long minutes = (elapsedSec % 3600) / 60;
        long seconds = elapsedSec % 60;
        tvTimerCountdown.setText(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));

        double initialHeight = parseDouble(etInitialHeight.getText().toString(), 50.0);
        double currentHeight = parseDouble(etCurrentHeight.getText().toString(), initialHeight);

        double riseRatio = 0.0;
        if (initialHeight > 0) {
            riseRatio = ((currentHeight - initialHeight) / initialHeight) * 100.0;
            if (riseRatio < 0) riseRatio = 0;
        }

        int targetRisePct = sbTargetRise.getProgress() + 20;
        tvCalculatedRise.setText(String.format(Locale.US, "%.1f%% Rise (Target: %d%%)", riseRatio, targetRisePct));

        // Biological fermentation dynamics model
        // Base hours to double (100% rise) at 24°C standard AP flour: ~6.0 hours
        int tempC = sbTemp.getProgress() + 15;
        int hydration = sbHydration.getProgress() + 50;
        double flourFactor = flourSpeedFactors[flourTypeIdx];

        // Q10 temperature coefficient approximation (rate doubles every 10°C)
        double tempFactor = Math.pow(2.0, (tempC - 24.0) / 10.0);
        // Hydration: faster fermentation above 70%, sluggish below 60%
        double hydrationFactor = 1.0 + ((hydration - 70.0) / 100.0) * 0.5;

        double overallRateMultiplier = Math.max(0.3, tempFactor * hydrationFactor * flourFactor);
        double estimatedTotalHoursToTarget = (6.0 * (targetRisePct / 100.0)) / overallRateMultiplier;
        long estimatedTotalSeconds = (long) (estimatedTotalHoursToTarget * 3600);

        long remainingSeconds = Math.max(0, estimatedTotalSeconds - elapsedSec);
        long remHours = remainingSeconds / 3600;
        long remMins = (remainingSeconds % 3600) / 60;

        if (riseRatio >= targetRisePct || remainingSeconds == 0) {
            tvEstimatedRemaining.setText("PEAK REACHED! READY FOR OVEN / RETARD");
            tvFermentStage.setText("Stage: Maximum Fermentation / Peak Activity");
            if (swAlarmAlert.isChecked() && !alarmTriggered && isTimerRunning) {
                triggerFermentAlarm();
            }
        } else {
            tvEstimatedRemaining.setText(String.format(Locale.US, "~%02dh %02dm remaining to reach %d%%", remHours, remMins, targetRisePct));

            if (riseRatio < 25) {
                tvFermentStage.setText("Stage: Lag Phase (Yeast Colonization)");
            } else if (riseRatio < 65) {
                tvFermentStage.setText("Stage: Exponential Rise (Bulk Aeration)");
            } else {
                tvFermentStage.setText("Stage: Final Expansion (Gluten Dome Maturing)");
            }
        }
    }

    private void triggerFermentAlarm() {
        alarmTriggered = true;
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 1000);
            }
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(800);
                }
            }
        } catch (Exception ignored) {}
        Toast.makeText(this, "CrumbWatch Alert: Target Ferment Peak Reached!", Toast.LENGTH_LONG).show();
    }

    private void logCurrentBatch() {
        String initHStr = etInitialHeight.getText().toString().trim();
        String currHStr = etCurrentHeight.getText().toString().trim();
        if (initHStr.isEmpty()) {
            Toast.makeText(this, "Enter initial dough height first", Toast.LENGTH_SHORT).show();
            return;
        }

        double initH = parseDouble(initHStr, 50.0);
        double currH = parseDouble(currHStr.isEmpty() ? initHStr : currHStr, initH);
        double rise = ((currH - initH) / initH) * 100.0;
        if (rise < 0) rise = 0;

        long elapsedSec = getTotalElapsedMillis() / 1000;
        long hours = elapsedSec / 3600;
        long mins = (elapsedSec % 3600) / 60;

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.US);
        String timestamp = sdf.format(new Date());

        int tempC = sbTemp.getProgress() + 15;
        int hydration = sbHydration.getProgress() + 50;
        int target = sbTargetRise.getProgress() + 20;

        try {
            JSONObject item = new JSONObject();
            item.put("timestamp", timestamp);
            item.put("risePct", String.format(Locale.US, "%.1f%%", rise));
            item.put("target", target + "%");
            item.put("temp", tempC + "°C");
            item.put("flour", flourNames[flourTypeIdx]);
            item.put("duration", String.format(Locale.US, "%dh %dm", hours, mins));
            item.put("hydration", hydration + "%");

            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            JSONArray array = new JSONArray(sp.getString(KEY_HISTORY, "[]"));
            array.put(item);
            sp.edit().putString(KEY_HISTORY, array.toString()).apply();

            renderHistory();
            Toast.makeText(this, "Batch telemetry logged to history", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void renderHistory() {
        llHistoryContainer.removeAllViews();
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(sp.getString(KEY_HISTORY, "[]"));
            if (array.length() == 0) {
                TextView tvEmpty = new TextView(this);
                tvEmpty.setText("No ferment sessions logged yet. Tap 'Log Batch Telemetry' to store.");
                tvEmpty.setTextColor(getResources().getColor(android.R.color.darker_gray));
                tvEmpty.setTextSize(13);
                llHistoryContainer.addView(tvEmpty);
                return;
            }

            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject obj = array.getJSONObject(i);
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(24, 20, 24, 20);
                card.setBackgroundResource(R.drawable.bg_history_card);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.setMargins(0, 0, 0, 16);
                card.setLayoutParams(lp);

                TextView tvHeader = new TextView(this);
                tvHeader.setText(obj.getString("timestamp") + " — " + obj.getString("risePct") + " Rise (Target " + obj.getString("target") + ")");
                tvHeader.setTextSize(14);
                tvHeader.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                tvHeader.setTextColor(getResources().getColor(R.color.m3_on_surface));

                TextView tvDetails = new TextView(this);
                tvDetails.setText("Temp: " + obj.getString("temp") + " | " + obj.getString("flour") + "\nHydration: " + obj.getString("hydration") + " | Elapsed: " + obj.getString("duration"));
                tvDetails.setTextSize(12);
                tvDetails.setTextColor(getResources().getColor(R.color.m3_on_surface_variant));
                tvDetails.setPadding(0, 6, 0, 0);

                card.addView(tvHeader);
                card.addView(tvDetails);
                llHistoryContainer.addView(card);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void clearHistory() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit().putString(KEY_HISTORY, "[]").apply();
        renderHistory();
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
    }

    private void shareTelemetryReport() {
        double initH = parseDouble(etInitialHeight.getText().toString(), 50.0);
        double currH = parseDouble(etCurrentHeight.getText().toString(), initH);
        double rise = ((currH - initH) / initH) * 100.0;
        int target = sbTargetRise.getProgress() + 20;
        int tempC = sbTemp.getProgress() + 15;
        int hydration = sbHydration.getProgress() + 50;

        String telemetry = "🍞 CrumbWatch Ferment Sentinel Telemetry Report:\n"
                + "• Measured Rise: " + String.format(Locale.US, "%.1f%%", rise) + " (Target: " + target + "%)\n"
                + "• Dough Height: " + currH + "mm (Initial: " + initH + "mm)\n"
                + "• Ambient Temp: " + tempC + "°C\n"
                + "• Hydration: " + hydration + "%\n"
                + "• Flour Blend: " + flourNames[flourTypeIdx] + "\n"
                + "• Active Duration: " + tvTimerCountdown.getText().toString() + "\n"
                + "• Phase: " + tvFermentStage.getText().toString() + "\n"
                + "Generated by CrumbWatch Sourdough Sentinel.";

        // Clipboard backup
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("CrumbWatch Telemetry", telemetry);
        cb.setPrimaryClip(clip);

        // System Share Chooser
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, telemetry);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "Share Fermentation Telemetry");
        startActivity(shareIntent);
    }

    private void persistState() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString(KEY_INITIAL_HEIGHT, etInitialHeight.getText().toString());
        ed.putString("crumb_current_height", etCurrentHeight.getText().toString());
        ed.putInt(KEY_TARGET_RISE, sbTargetRise.getProgress());
        ed.putInt(KEY_AMBIENT_TEMP, sbTemp.getProgress());
        ed.putInt(KEY_HYDRATION, sbHydration.getProgress());
        ed.putInt(KEY_FLOUR_TYPE_INDEX, flourTypeIdx);
        ed.putBoolean(KEY_TIMER_RUNNING, isTimerRunning);
        ed.putLong(KEY_TIMER_START_TIME, timerStartTime);
        ed.putLong(KEY_TIMER_ELAPSED_OFFSET, elapsedMillisBeforePause);
        ed.putBoolean(KEY_ALARM_ENABLED, swAlarmAlert.isChecked());
        ed.commit();
    }

    private void restoreState() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        themeMode = sp.getInt(KEY_THEME_MODE, 0);
        etInitialHeight.setText(sp.getString(KEY_INITIAL_HEIGHT, "50"));
        etCurrentHeight.setText(sp.getString("crumb_current_height", sp.getString(KEY_INITIAL_HEIGHT, "50")));

        int targetProgress = sp.getInt(KEY_TARGET_RISE, 80); // Default 100% (80 + 20)
        sbTargetRise.setProgress(targetProgress);
        tvTargetRiseDisplay.setText((targetProgress + 20) + "% Target Vol");

        int tempProgress = sp.getInt(KEY_AMBIENT_TEMP, 9); // Default 24°C (9 + 15)
        sbTemp.setProgress(tempProgress);
        int tempC = tempProgress + 15;
        tvTempDisplay.setText(tempC + "°C / " + (int) Math.round((tempC * 9.0 / 5.0) + 32) + "°F");

        int hydProgress = sp.getInt(KEY_HYDRATION, 25); // Default 75% (25 + 50)
        sbHydration.setProgress(hydProgress);
        tvHydrationDisplay.setText((hydProgress + 50) + "% Hydration");

        flourTypeIdx = sp.getInt(KEY_FLOUR_TYPE_INDEX, 0);
        btnFlourType.setText(flourNames[flourTypeIdx]);

        swAlarmAlert.setChecked(sp.getBoolean(KEY_ALARM_ENABLED, true));

        isTimerRunning = sp.getBoolean(KEY_TIMER_RUNNING, false);
        timerStartTime = sp.getLong(KEY_TIMER_START_TIME, System.currentTimeMillis());
        elapsedMillisBeforePause = sp.getLong(KEY_TIMER_ELAPSED_OFFSET, 0);

        if (isTimerRunning) {
            btnStartStopTimer.setText("Pause Monitor");
            tvStatusBadge.setText("ACTIVE");
        } else if (elapsedMillisBeforePause > 0) {
            btnStartStopTimer.setText("Resume Monitor");
            tvStatusBadge.setText("PAUSED");
        } else {
            btnStartStopTimer.setText("Start Monitor");
            tvStatusBadge.setText("IDLE");
        }
    }

    private double parseDouble(String str, double defVal) {
        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return defVal;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                btnStartStopTimer.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                btnStartStopTimer.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                sbTargetRise.setProgress(Math.min(sbTargetRise.getMax(), sbTargetRise.getProgress() + 5));
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
                sbTargetRise.setProgress(Math.max(0, sbTargetRise.getProgress() - 5));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyWindowInsetsTheme();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}