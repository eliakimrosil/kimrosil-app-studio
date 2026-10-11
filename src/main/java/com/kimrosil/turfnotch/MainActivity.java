package com.kimrosil.turfnotch;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "turf-notch_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String NOTIF_CHANNEL_ID = "turfnotch_hud_channel";
    private static final int NOTIF_ID = 1001;

    // Views
    private Button btnThemeToggle;
    private Spinner spSpreaderModel;
    private EditText etTargetRate;
    private EditText etLawnArea;
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

    // Database & Helper
    private DatabaseHelper dbHelper;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;
    private NotificationManager notificationManager;

    // Pacing Metronome State
    private final Handler pacerHandler = new Handler(Looper.getMainLooper());
    private boolean isPacingActive = false;
    private double targetSpeedMph = 3.0; // default 3.0 mph = ~88 paces/min
    private int currentPasses = 0;
    private int totalPassesNeeded = 1;

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

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
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
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
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
        recalculateNotch();
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        spSpreaderModel = findViewById(R.id.spSpreaderModel);
        etTargetRate = findViewById(R.id.etTargetRate);
        etLawnArea = findViewById(R.id.etLawnArea);
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

        // Preload defaults
        etTargetRate.setText("3.5");
        etLawnArea.setText("5000");
        sbTargetSpeed.setMax(20); // 2.0 to 4.0 mph (step = 0.1)
        sbTargetSpeed.setProgress(10); // 3.0 mph
        updateSpeedLabel();
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
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        spSpreaderModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                recalculateNotch();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        View.OnFocusChangeListener focusChangeListener = (v, hasFocus) -> {
            if (!hasFocus) recalculateNotch();
        };
        etTargetRate.setOnFocusChangeListener(focusChangeListener);
        etLawnArea.setOnFocusChangeListener(focusChangeListener);

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
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            togglePacer();
        });

        btnPlusPass.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentPasses++;
            updatePassUI();
        });

        btnMinusPass.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentPasses > 0) {
                currentPasses--;
                updatePassUI();
            }
        });

        btnResetPasses.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            currentPasses = 0;
            updatePassUI();
            Toast.makeText(this, "Pass counter reset", Toast.LENGTH_SHORT).show();
        });

        btnOpenDetail.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            openDetailActivity();
        });

        btnSaveSession.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveCurrentSession();
        });
    }

    private void recalculateNotch() {
        String rateStr = etTargetRate.getText().toString().trim();
        String areaStr = etLawnArea.getText().toString().trim();

        if (TextUtils.isEmpty(rateStr) || TextUtils.isEmpty(areaStr)) {
            llEmptyState.setVisibility(View.VISIBLE);
            llActiveDeck.setVisibility(View.GONE);
            tvNotchResult.setText("--");
            tvDialDescription.setText("Enter rate and yard area to calibrate");
            return;
        }

        double targetRate;
        double lawnArea;
        try {
            targetRate = Double.parseDouble(rateStr);
            lawnArea = Double.parseDouble(areaStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numeric values", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetRate <= 0 || lawnArea <= 0) {
            llEmptyState.setVisibility(View.VISIBLE);
            llActiveDeck.setVisibility(View.GONE);
            tvNotchResult.setText("--");
            return;
        }

        llEmptyState.setVisibility(View.GONE);
        llActiveDeck.setVisibility(View.VISIBLE);

        int spreaderIndex = spSpreaderModel.getSelectedItemPosition();
        PresetManager.NotchResult result = PresetManager.calculateNotch(spreaderIndex, targetRate);

        tvNotchResult.setText(result.dialSetting);
        tvDialDescription.setText(result.guidance);

        // Estimate total passes assuming a 5 ft spreader swath
        double swathWidthFt = 5.0;
        double linearFeet = lawnArea / swathWidthFt;
        totalPassesNeeded = Math.max(1, (int) Math.ceil(linearFeet / 100.0));
        updatePassUI();
    }

    private void updateSpeedLabel() {
        int cadenceBpm = (int) Math.round(targetSpeedMph * 29.33);
        tvSpeedLabel.setText(String.format(Locale.US, "Target Walking Pace: %.1f mph (%d steps/min)", targetSpeedMph, cadenceBpm));
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
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(55);
                }
            }
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 40);
            }
        } catch (Exception ignored) {}
    }

    private void updatePassUI() {
        tvPassCount.setText(String.format(Locale.US, "%d / ~%d Passes", currentPasses, totalPassesNeeded));
        if (isPacingActive) {
            updateOngoingNotification();
        }
    }

    private void openDetailActivity() {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("EXTRA_SPREADER_INDEX", spSpreaderModel.getSelectedItemPosition());
        intent.putExtra("EXTRA_TARGET_RATE", etTargetRate.getText().toString());
        intent.putExtra("EXTRA_LAWN_AREA", etLawnArea.getText().toString());
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
        int next = (current + 1) % 3; // 0 -> 1 -> 2 -> 0
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
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
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
            channel.setDescription("Shows active walking pace and pass count on lockscreen");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void updateOngoingNotification() {
        if (notificationManager == null) return;
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(this, NOTIF_CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(this);
        }

        builder.setContentTitle("TurfNotch Pace HUD (3.0 mph)")
                .setContentText(String.format(Locale.US, "Current Passes: %d / ~%d | Dial: %s", currentPasses, totalPassesNeeded, tvNotchResult.getText()))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pendingIntent);

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
        // Preserves UI state without reloading activity during freeform resizing
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