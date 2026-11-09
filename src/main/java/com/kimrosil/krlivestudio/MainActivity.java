package com.kimrosil.krlivestudio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_CODE_PERMISSIONS = 1002;
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1003;
    private static final String PREFS_NAME = "kim_live_studio_prefs";
    private static final String LEGACY_PREFS_NAME = "omnistream-live-studio_prefs";

    // Header & Theming
    private ImageButton btnSettings;

    // Platform Presets
    private Button btnPlatformYoutube;
    private Button btnPlatformFacebook;
    private Button btnPlatformTwitch;
    private Button btnPlatformTiktok;
    private Button btnPlatformKick;
    private Button btnPlatformCustom;

    // Configuration Inputs
    private TextView tvUrlLockBadge;
    private EditText etServerUrl;
    private EditText etStreamKey;
    private Button btnToggleKeyVisibility;
    private Button btnPasteKey;

    // Platform Helper Portal & Quick Key
    private View layoutPlatformKeyHelper;
    private TextView tvPlatformHelperTitle;
    private TextView tvPlatformHelperHint;
    private Button btnOpenPlatformPortal;

    // Video Resolution Chips
    private Button btnRes1080p;
    private Button btnRes720p;
    private Button btnRes480p;

    // Video Frame Cadence (FPS) Chips
    private Button btnFps60;
    private Button btnFps30;
    private Button btnFps24;
    private TextView tvFpsBadge;

    // Stream Orientation Chips
    private Button btnOrientLandscape;
    private Button btnOrientPortrait;
    private Button btnOrientAuto;
    private TextView tvOrientationBadge;

    public static final int ORIENT_LANDSCAPE = 0;
    public static final int ORIENT_PORTRAIT = 1;
    public static final int ORIENT_AUTO = 2;
    private int selectedOrientation = ORIENT_LANDSCAPE;
    private int selectedResPreset = 1080;

    // Bitrate Slider & Presets
    private SeekBar seekBitrate;
    private TextView tvBitrateValue;
    private Button btnBitrate1500;
    private Button btnBitrate2500;
    private Button btnBitrate4500;
    private Button btnBitrate6000;
    private TextView tvBitrateHint;
    private Switch switchAbr;
    private TextView tvAbrHint;

    // Audio & Facecam Controls
    private Switch switchMicAudio;
    private TextView tvMicStatusHint;
    private View layoutAudioModeRow;
    private Button btnAudioMic;
    private Button btnAudioInternal;
    private Button btnAudioDual;
    public static final int AUDIO_MODE_MIC = 0;
    public static final int AUDIO_MODE_INTERNAL = 1;
    public static final int AUDIO_MODE_DUAL = 2;
    private int selectedAudioMode = AUDIO_MODE_MIC;

    // Dual-Audio Mixer & Local Recording Controls
    private View layoutAudioMixer;
    private View layoutMicGainGroup;
    private View layoutInternalGainGroup;
    private View layoutAutoDuckGroup;
    private SeekBar seekMicGain;
    private TextView tvMicGainValue;
    private SeekBar seekInternalGain;
    private TextView tvInternalGainValue;
    private Switch switchAutoDuck;
    private Switch switchLocalRecord;
    private TextView tvLocalRecordHint;

    private Switch switchFacecam;
    private Button btnFlipCamera;
    private TextView tvOverlayStatus;

    // Telemetry Display, Hardware Health & VU Meter
    private ImageView ivLiveBeacon;
    private TextView tvLiveStatus;
    private TextView tvUptime;
    private TextView tvLiveBitrate;
    private TextView tvLiveFps;
    private TextView tvDroppedFrames;
    private View[] vuSegments;
    private TextView tvVuLevel;
    private TextView tvStreamHealth;
    private TextView tvThermalStatus;
    private Button btnPrivacyShield;

    // Action Controls
    private Button btnStartStopStream;
    public static final String ACTION_REQUEST_START_STREAM = "com.kimrosil.krlivestudio.REQUEST_START_STREAM";
    private boolean wasStartedFromFloatingCam = false;

    // Internal State
    private static final String PREF_STREAM_KEY_PREFIX = "pref_stream_key_platform_";
    private boolean isKeyVisible = false;
    private SharedPreferences prefs;
    private int selectedWidth = 1280;
    private int selectedHeight = 720;
    private int selectedFps = 30;
    private int targetBitrateKbps = 2500;
    private int selectedPlatformIndex = 0; // 0: YT, 1: FB, 2: Twitch, 3: TikTok, 4: Kick, 5: Custom

    private static final String[] PRESET_URLS = {
            "rtmp://a.rtmp.youtube.com/live2",
            "rtmps://live-api-s.facebook.com:443/rtmp/",
            "rtmp://live.twitch.tv/app/",
            "rtmp://live-push.tiktok.com/live/",
            "rtmps://fa723794b6f7.global-contribute.live-video.net:443/app/",
            ""
    };

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable telemetryRunnable = new Runnable() {
        @Override
        public void run() {
            updateTelemetryDisplay();
            uiHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!sp.contains("pref_theme_mode")) {
            SharedPreferences legacy = newBase.getSharedPreferences(LEGACY_PREFS_NAME, MODE_PRIVATE);
            if (legacy.contains("pref_theme_mode")) {
                sp.edit().putInt("pref_theme_mode", legacy.getInt("pref_theme_mode", 2)).apply();
            } else {
                sp.edit().putInt("pref_theme_mode", 2).apply(); // Dark Mode Default
            }
        }
        int mode = sp.getInt("pref_theme_mode", 2);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mode == 0) {
            int sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (sysNight == 2) {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            } else {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            }
        } else if (mode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        migrateLegacyPreferencesIfNeeded();
        initViews();
        setupTheming();
        setupFacecamStateListener();
        loadSavedPreferences();
        setupListeners();
        requestNecessaryPermissions();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (ACTION_REQUEST_START_STREAM.equals(intent.getAction())) {
            wasStartedFromFloatingCam = true;
            uiHandler.post(this::handleStartStreamRequest);
        }
    }

    private void setupFacecamStateListener() {
        FloatingCamManager.getInstance(this).setStateListener(new FloatingCamManager.StateListener() {
            @Override
            public void onFacecamToggled(boolean isShowing) {
                switchFacecam.setChecked(isShowing);
                updateOverlayStatus();
                saveCurrentPreferences();
            }

            @Override
            public void onCameraFlipped(boolean isFront) {
                btnFlipCamera.setText(isFront ? "FLIP: REAR" : "FLIP: FRONT");
            }

            @Override
            public void onRequestStartBroadcast() {
                runOnUiThread(() -> {
                    wasStartedFromFloatingCam = true;
                    handleStartStreamRequest();
                });
            }

            @Override
            public void onBroadcastToggled(boolean isLive) {
                runOnUiThread(() -> updateUiStreamState(isLive));
            }

            @Override
            public void onMicMuteToggled(boolean isMuted) {
                runOnUiThread(() -> {
                    if (switchMicAudio != null && switchMicAudio.isChecked() == isMuted) {
                        switchMicAudio.setChecked(!isMuted);
                    }
                    updateMicHint(!isMuted);
                });
            }

            @Override
            public void onPrivacyShieldToggled(boolean isShield) {
                runOnUiThread(() -> {
                    if (btnPrivacyShield != null) {
                        btnPrivacyShield.setText(isShield ? "🛡️ SHIELD: ON" : "🛡️ SHIELD: OFF");
                        btnPrivacyShield.setTextColor(getColor(isShield ? R.color.studio_ruby : R.color.studio_cyan));
                    }
                });
            }
        });
    }

    private void startFacecamService() {
        Intent fIntent = new Intent(this, StreamService.class);
        fIntent.setAction(StreamService.ACTION_START_FACECAM);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(fIntent);
        } else {
            startService(fIntent);
        }
    }

    private void stopFacecamService() {
        Intent fIntent = new Intent(this, StreamService.class);
        fIntent.setAction(StreamService.ACTION_STOP_FACECAM);
        startService(fIntent);
    }

    private void migrateLegacyPreferencesIfNeeded() {
        try {
            SharedPreferences legacy = getSharedPreferences(LEGACY_PREFS_NAME, MODE_PRIVATE);
            if (!legacy.getAll().isEmpty() && !prefs.contains("pref_stream_key") && legacy.contains("pref_stream_key")) {
                SharedPreferences.Editor editor = prefs.edit();
                for (Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof String) {
                        editor.putString(entry.getKey(), (String) val);
                    } else if (val instanceof Integer) {
                        editor.putInt(entry.getKey(), (Integer) val);
                    } else if (val instanceof Boolean) {
                        editor.putBoolean(entry.getKey(), (Boolean) val);
                    } else if (val instanceof Float) {
                        editor.putFloat(entry.getKey(), (Float) val);
                    } else if (val instanceof Long) {
                        editor.putLong(entry.getKey(), (Long) val);
                    }
                }
                editor.apply();
            }
        } catch (Exception ignored) {
        }
    }

    private void initViews() {
        btnSettings = findViewById(R.id.btnSettings);
        btnPlatformYoutube = findViewById(R.id.btnPlatformYoutube);
        btnPlatformFacebook = findViewById(R.id.btnPlatformFacebook);
        btnPlatformTwitch = findViewById(R.id.btnPlatformTwitch);
        btnPlatformTiktok = findViewById(R.id.btnPlatformTiktok);
        btnPlatformKick = findViewById(R.id.btnPlatformKick);
        btnPlatformCustom = findViewById(R.id.btnPlatformCustom);

        tvUrlLockBadge = findViewById(R.id.tvUrlLockBadge);
        etServerUrl = findViewById(R.id.etServerUrl);
        etStreamKey = findViewById(R.id.etStreamKey);
        btnToggleKeyVisibility = findViewById(R.id.btnToggleKeyVisibility);
        btnPasteKey = findViewById(R.id.btnPasteKey);

        layoutPlatformKeyHelper = findViewById(R.id.layoutPlatformKeyHelper);
        tvPlatformHelperTitle = findViewById(R.id.tvPlatformHelperTitle);
        tvPlatformHelperHint = findViewById(R.id.tvPlatformHelperHint);
        btnOpenPlatformPortal = findViewById(R.id.btnOpenPlatformPortal);
        if (btnOpenPlatformPortal != null) {
            btnOpenPlatformPortal.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                openPlatformPortal();
            });
        }

        btnRes1080p = findViewById(R.id.btnRes1080p);
        btnRes720p = findViewById(R.id.btnRes720p);
        btnRes480p = findViewById(R.id.btnRes480p);

        btnFps60 = findViewById(R.id.btnFps60);
        btnFps30 = findViewById(R.id.btnFps30);
        btnFps24 = findViewById(R.id.btnFps24);
        tvFpsBadge = findViewById(R.id.tvFpsBadge);

        btnOrientLandscape = findViewById(R.id.btnOrientLandscape);
        btnOrientPortrait = findViewById(R.id.btnOrientPortrait);
        btnOrientAuto = findViewById(R.id.btnOrientAuto);
        tvOrientationBadge = findViewById(R.id.tvOrientationBadge);

        seekBitrate = findViewById(R.id.seekBitrate);
        tvBitrateValue = findViewById(R.id.tvBitrateValue);
        btnBitrate1500 = findViewById(R.id.btnBitrate1500);
        btnBitrate2500 = findViewById(R.id.btnBitrate2500);
        btnBitrate4500 = findViewById(R.id.btnBitrate4500);
        btnBitrate6000 = findViewById(R.id.btnBitrate6000);
        tvBitrateHint = findViewById(R.id.tvBitrateHint);
        switchAbr = findViewById(R.id.switchAbr);
        tvAbrHint = findViewById(R.id.tvAbrHint);

        switchMicAudio = findViewById(R.id.switchMicAudio);
        tvMicStatusHint = findViewById(R.id.tvMicStatusHint);
        layoutAudioModeRow = findViewById(R.id.layoutAudioModeRow);
        layoutAudioMixer = findViewById(R.id.layoutAudioMixer);
        layoutMicGainGroup = findViewById(R.id.layoutMicGainGroup);
        layoutInternalGainGroup = findViewById(R.id.layoutInternalGainGroup);
        layoutAutoDuckGroup = findViewById(R.id.layoutAutoDuckGroup);
        seekMicGain = findViewById(R.id.seekMicGain);
        tvMicGainValue = findViewById(R.id.tvMicGainValue);
        seekInternalGain = findViewById(R.id.seekInternalGain);
        tvInternalGainValue = findViewById(R.id.tvInternalGainValue);
        switchAutoDuck = findViewById(R.id.switchAutoDuck);
        switchLocalRecord = findViewById(R.id.switchLocalRecord);
        tvLocalRecordHint = findViewById(R.id.tvLocalRecordHint);

        switchFacecam = findViewById(R.id.switchFacecam);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus);

        ivLiveBeacon = findViewById(R.id.ivLiveBeacon);
        tvLiveStatus = findViewById(R.id.tvLiveStatus);
        tvUptime = findViewById(R.id.tvUptime);
        tvLiveBitrate = findViewById(R.id.tvLiveBitrate);
        tvLiveFps = findViewById(R.id.tvLiveFps);
        tvDroppedFrames = findViewById(R.id.tvDroppedFrames);

        vuSegments = new View[] {
                findViewById(R.id.vuSeg1),
                findViewById(R.id.vuSeg2),
                findViewById(R.id.vuSeg3),
                findViewById(R.id.vuSeg4),
                findViewById(R.id.vuSeg5),
                findViewById(R.id.vuSeg6),
                findViewById(R.id.vuSeg7),
                findViewById(R.id.vuSeg8),
                findViewById(R.id.vuSeg9),
                findViewById(R.id.vuSeg10)
        };
        tvVuLevel = findViewById(R.id.tvVuLevel);
        tvStreamHealth = findViewById(R.id.tvStreamHealth);
        tvThermalStatus = findViewById(R.id.tvThermalStatus);
        btnPrivacyShield = findViewById(R.id.btnPrivacyShield);

        btnAudioMic = findViewById(R.id.btnAudioMic);
        btnAudioInternal = findViewById(R.id.btnAudioInternal);
        btnAudioDual = findViewById(R.id.btnAudioDual);

        btnStartStopStream = findViewById(R.id.btnStartStopStream);
    }

    private void setupTheming() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                if (!isNight) {
                    controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                } else {
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Version Label
        TextView tvVersion = dialogView.findViewById(R.id.tvSettingsVersion);
        if (tvVersion != null) {
            try {
                String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvVersion.setText("v" + ver + " • Native Live Streaming Studio");
            } catch (Exception ignored) {}
        }

        // Theme Mode Buttons
        Button btnThemeDark = dialogView.findViewById(R.id.btnThemeDark);
        Button btnThemeLight = dialogView.findViewById(R.id.btnThemeLight);
        Button btnThemeAuto = dialogView.findViewById(R.id.btnThemeAuto);

        int currentTheme = prefs.getInt("pref_theme_mode", 2); // 2: Dark, 1: Light, 0: Auto

        updateThemeDialogButtons(btnThemeDark, btnThemeLight, btnThemeAuto, currentTheme);

        btnThemeDark.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            dialog.dismiss();
            setThemeMode(2);
        });

        btnThemeLight.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            dialog.dismiss();
            setThemeMode(1);
        });

        btnThemeAuto.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            dialog.dismiss();
            setThemeMode(0);
        });

        // Close Button
        Button btnClose = dialogView.findViewById(R.id.btnCloseSettings);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        // Privacy Policy Link
        Button btnPrivacy = dialogView.findViewById(R.id.btnSettingsPrivacy);
        if (btnPrivacy != null) {
            btnPrivacy.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                openWebUrl("https://apps.kimrosil.com/kim-live-studio/privacy.html");
            });
        }

        // Studio Portal Link
        Button btnShowcase = dialogView.findViewById(R.id.btnSettingsShowcase);
        if (btnShowcase != null) {
            btnShowcase.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                openWebUrl("https://apps.kimrosil.com/kim-live-studio/");
            });
        }

        dialog.show();
    }

    private void updateThemeDialogButtons(Button btnDark, Button btnLight, Button btnAuto, int currentTheme) {
        if (btnDark == null || btnLight == null || btnAuto == null) return;

        boolean isDark = (currentTheme == 2);
        boolean isLight = (currentTheme == 1);
        boolean isAuto = (currentTheme == 0);

        btnDark.setBackgroundResource(isDark ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnDark.setTextColor(getColor(isDark ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnLight.setBackgroundResource(isLight ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnLight.setTextColor(getColor(isLight ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnAuto.setBackgroundResource(isAuto ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnAuto.setTextColor(getColor(isAuto ? R.color.studio_chip_active_text : R.color.studio_chip_text));
    }

    private void setThemeMode(int newMode) {
        prefs.edit().putInt("pref_theme_mode", newMode).apply();
        recreate();
    }

    private void openWebUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSavedPreferences() {
        selectedPlatformIndex = prefs.getInt("pref_platform_index", 0);
        String customUrl = prefs.getString("pref_custom_url", "");
        selectedOrientation = prefs.getInt("pref_orientation", ORIENT_LANDSCAPE);
        selectedResPreset = prefs.getInt("pref_res_preset", 1080);
        if (!prefs.contains("pref_res_preset")) {
            int savedHeight = prefs.getInt("pref_height", 1080);
            int savedWidth = prefs.getInt("pref_width", 1920);
            int minDim = Math.min(savedHeight, savedWidth);
            selectedResPreset = (minDim >= 1000) ? 1080 : ((minDim >= 700) ? 720 : 480);
        }
        computeResolutionDimensions();
        selectedFps = prefs.getInt("pref_fps", 30);
        targetBitrateKbps = prefs.getInt("pref_bitrate_kbps", 2500);
        selectedAudioMode = prefs.getInt("pref_audio_mode", AUDIO_MODE_MIC);
        boolean micEnabled = prefs.getBoolean("pref_mic_enabled", true);
        boolean facecamEnabled = prefs.getBoolean("pref_facecam_enabled", true);
        boolean abrEnabled = prefs.getBoolean("pref_abr_enabled", true);
        int micGain = prefs.getInt("pref_mic_gain", 100);
        int internalGain = prefs.getInt("pref_internal_gain", 80);
        boolean autoDuck = prefs.getBoolean("pref_auto_duck", true);
        boolean localRecord = prefs.getBoolean("pref_local_record", false);

        StreamService.setMicMuted(!micEnabled);
        StreamService.setAudioMode(selectedAudioMode);
        StreamService.setAbrEnabled(abrEnabled);
        StreamService.setMicGainPercent(micGain);
        StreamService.setInternalGainPercent(internalGain);
        StreamService.setAutoDuckEnabled(autoDuck);
        StreamService.setLocalRecordingEnabled(localRecord);
        FloatingCamManager.getInstance(this).setMicMutedState(!micEnabled);

        selectPlatform(selectedPlatformIndex);
        if (selectedPlatformIndex == 5 && !customUrl.isEmpty()) {
            etServerUrl.setText(customUrl);
        }

        seekBitrate.setProgress(targetBitrateKbps);
        tvBitrateValue.setText(String.format(Locale.US, "%,d kbps", targetBitrateKbps));

        if (switchAbr != null) switchAbr.setChecked(abrEnabled);
        if (seekMicGain != null) seekMicGain.setProgress(micGain);
        if (tvMicGainValue != null) tvMicGainValue.setText(micGain + "%");
        if (seekInternalGain != null) seekInternalGain.setProgress(internalGain);
        if (tvInternalGainValue != null) tvInternalGainValue.setText(internalGain + "%");
        if (switchAutoDuck != null) switchAutoDuck.setChecked(autoDuck);
        if (switchLocalRecord != null) switchLocalRecord.setChecked(localRecord);
        updateLocalRecordHint(localRecord);

        switchMicAudio.setChecked(micEnabled);
        switchFacecam.setChecked(facecamEnabled);

        updateResolutionButtons();
        updateFpsButtons();
        updateOrientationButtons();
        updateBitrateHint();
        updateAudioModeButtons();

        if (facecamEnabled && checkOverlayPermission()) {
            startFacecamService();
        }
        updateOverlayStatus();
        updateMicHint(micEnabled);
    }

    private void saveCurrentPreferences() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("pref_platform_index", selectedPlatformIndex);
        String currentKey = etStreamKey.getText().toString().trim();
        editor.putString("pref_stream_key", currentKey);
        editor.putString(PREF_STREAM_KEY_PREFIX + selectedPlatformIndex, currentKey);
        if (selectedPlatformIndex == 5) {
            editor.putString("pref_custom_url", etServerUrl.getText().toString().trim());
        }
        editor.putInt("pref_orientation", selectedOrientation);
        editor.putInt("pref_res_preset", selectedResPreset);
        editor.putInt("pref_width", selectedWidth);
        editor.putInt("pref_height", selectedHeight);
        editor.putInt("pref_fps", selectedFps);
        editor.putInt("pref_bitrate_kbps", targetBitrateKbps);
        editor.putInt("pref_audio_mode", selectedAudioMode);
        editor.putBoolean("pref_mic_enabled", switchMicAudio.isChecked());
        editor.putBoolean("pref_facecam_enabled", switchFacecam.isChecked());
        if (switchAbr != null) editor.putBoolean("pref_abr_enabled", switchAbr.isChecked());
        if (seekMicGain != null) editor.putInt("pref_mic_gain", seekMicGain.getProgress());
        if (seekInternalGain != null) editor.putInt("pref_internal_gain", seekInternalGain.getProgress());
        if (switchAutoDuck != null) editor.putBoolean("pref_auto_duck", switchAutoDuck.isChecked());
        if (switchLocalRecord != null) editor.putBoolean("pref_local_record", switchLocalRecord.isChecked());
        editor.apply();
    }

    private void setupListeners() {
        btnSettings.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showSettingsDialog();
        });

        btnPlatformYoutube.setOnClickListener(v -> selectPlatform(0));
        btnPlatformFacebook.setOnClickListener(v -> selectPlatform(1));
        btnPlatformTwitch.setOnClickListener(v -> selectPlatform(2));
        btnPlatformTiktok.setOnClickListener(v -> selectPlatform(3));
        btnPlatformKick.setOnClickListener(v -> selectPlatform(4));
        btnPlatformCustom.setOnClickListener(v -> selectPlatform(5));

        btnToggleKeyVisibility.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            isKeyVisible = !isKeyVisible;
            if (isKeyVisible) {
                etStreamKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnToggleKeyVisibility.setText("HIDE");
            } else {
                etStreamKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnToggleKeyVisibility.setText("SHOW");
            }
            etStreamKey.setSelection(etStreamKey.getText().length());
        });

        btnPasteKey.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null && text.length() > 0) {
                    String cleanKey = text.toString().trim();
                    etStreamKey.setText(cleanKey);
                    prefs.edit().putString(PREF_STREAM_KEY_PREFIX + selectedPlatformIndex, cleanKey).apply();
                    saveCurrentPreferences();
                    Toast.makeText(this, "🔑 Stream key pasted from clipboard!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        });

        btnRes1080p.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedResPreset = 1080;
            computeResolutionDimensions();
            updateResolutionButtons();
            saveCurrentPreferences();
        });

        btnRes720p.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedResPreset = 720;
            computeResolutionDimensions();
            updateResolutionButtons();
            saveCurrentPreferences();
        });

        btnRes480p.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedResPreset = 480;
            computeResolutionDimensions();
            updateResolutionButtons();
            saveCurrentPreferences();
        });

        btnFps60.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedFps = 60;
            updateFpsButtons();
            saveCurrentPreferences();
        });

        btnFps30.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedFps = 30;
            updateFpsButtons();
            saveCurrentPreferences();
        });

        btnFps24.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedFps = 24;
            updateFpsButtons();
            saveCurrentPreferences();
        });

        btnOrientLandscape.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedOrientation = ORIENT_LANDSCAPE;
            computeResolutionDimensions();
            updateOrientationButtons();
            updateBitrateHint();
            saveCurrentPreferences();
        });

        btnOrientPortrait.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedOrientation = ORIENT_PORTRAIT;
            computeResolutionDimensions();
            updateOrientationButtons();
            updateBitrateHint();
            saveCurrentPreferences();
        });

        btnOrientAuto.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            selectedOrientation = ORIENT_AUTO;
            computeResolutionDimensions();
            updateOrientationButtons();
            updateBitrateHint();
            saveCurrentPreferences();
        });

        seekBitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 500) progress = 500;
                targetBitrateKbps = progress;
                tvBitrateValue.setText(String.format(Locale.US, "%,d kbps", targetBitrateKbps));
                updateBitrateHint();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveCurrentPreferences();
            }
        });

        btnBitrate1500.setOnClickListener(v -> setBitrate(1500));
        btnBitrate2500.setOnClickListener(v -> setBitrate(2500));
        btnBitrate4500.setOnClickListener(v -> setBitrate(4500));
        btnBitrate6000.setOnClickListener(v -> setBitrate(6000));

        if (switchAbr != null) {
            switchAbr.setOnCheckedChangeListener((buttonView, isChecked) -> {
                buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                StreamService.setAbrEnabled(isChecked);
                if (tvAbrHint != null) {
                    tvAbrHint.setText(isChecked
                            ? "Auto-scales encoder bitrate on network congestion"
                            : "Fixed constant bitrate (CBR) locked");
                }
                saveCurrentPreferences();
            });
        }

        switchMicAudio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            StreamService.setMicMuted(!isChecked);
            FloatingCamManager.getInstance(this).setMicMutedState(!isChecked);
            updateAudioModeButtons();
            saveCurrentPreferences();
        });

        if (seekMicGain != null) {
            seekMicGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    StreamService.setMicGainPercent(progress);
                    if (tvMicGainValue != null) tvMicGainValue.setText(progress + "%");
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    saveCurrentPreferences();
                }
            });
        }

        if (seekInternalGain != null) {
            seekInternalGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    StreamService.setInternalGainPercent(progress);
                    if (tvInternalGainValue != null) tvInternalGainValue.setText(progress + "%");
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    saveCurrentPreferences();
                }
            });
        }

        if (switchAutoDuck != null) {
            switchAutoDuck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                StreamService.setAutoDuckEnabled(isChecked);
                saveCurrentPreferences();
            });
        }

        if (switchLocalRecord != null) {
            switchLocalRecord.setOnCheckedChangeListener((buttonView, isChecked) -> {
                buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                StreamService.setLocalRecordingEnabled(isChecked);
                updateLocalRecordHint(isChecked);
                saveCurrentPreferences();
            });
        }

        switchFacecam.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (isChecked) {
                if (checkOverlayPermission()) {
                    startFacecamService();
                    updateOverlayStatus();
                } else {
                    switchFacecam.setChecked(false);
                    requestOverlayPermission();
                }
            } else {
                stopFacecamService();
                updateOverlayStatus();
            }
            saveCurrentPreferences();
        });

        btnFlipCamera.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            FloatingCamManager.getInstance(this).flipCamera();
        });

        if (btnPrivacyShield != null) {
            btnPrivacyShield.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                togglePrivacyShield();
            });
        }

        if (btnAudioMic != null) {
            btnAudioMic.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                selectedAudioMode = AUDIO_MODE_MIC;
                updateAudioModeButtons();
                saveCurrentPreferences();
            });
        }

        if (btnAudioInternal != null) {
            btnAudioInternal.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    selectedAudioMode = AUDIO_MODE_INTERNAL;
                    updateAudioModeButtons();
                    saveCurrentPreferences();
                } else {
                    Toast.makeText(this, "Internal audio capture requires Android 10+", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnAudioDual != null) {
            btnAudioDual.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    selectedAudioMode = AUDIO_MODE_DUAL;
                    updateAudioModeButtons();
                    saveCurrentPreferences();
                } else {
                    Toast.makeText(this, "Dual Mix audio capture requires Android 10+", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnStartStopStream.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleStreaming();
        });
    }

    private void setBitrate(int kbps) {
        seekBitrate.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        targetBitrateKbps = kbps;
        seekBitrate.setProgress(kbps);
        tvBitrateValue.setText(String.format(Locale.US, "%,d kbps", targetBitrateKbps));
        updateBitrateHint();
        saveCurrentPreferences();
    }

    private void updateAudioModeButtons() {
        if (btnAudioMic == null || btnAudioInternal == null || btnAudioDual == null) return;
        boolean isMic = (selectedAudioMode == AUDIO_MODE_MIC);
        boolean isInt = (selectedAudioMode == AUDIO_MODE_INTERNAL);
        boolean isDual = (selectedAudioMode == AUDIO_MODE_DUAL);
        boolean masterEnabled = (switchMicAudio == null || switchMicAudio.isChecked());

        StreamService.setAudioMode(selectedAudioMode);

        btnAudioMic.setBackgroundResource(isMic ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnAudioMic.setTextColor(getColor(isMic ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnAudioInternal.setBackgroundResource(isInt ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnAudioInternal.setTextColor(getColor(isInt ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnAudioDual.setBackgroundResource(isDual ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnAudioDual.setTextColor(getColor(isDual ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        if (layoutAudioModeRow != null) {
            layoutAudioModeRow.setVisibility(masterEnabled ? View.VISIBLE : View.GONE);
        }
        if (layoutAudioMixer != null) {
            layoutAudioMixer.setVisibility(masterEnabled ? View.VISIBLE : View.GONE);
        }
        if (layoutMicGainGroup != null) {
            layoutMicGainGroup.setVisibility((isMic || isDual) ? View.VISIBLE : View.GONE);
        }
        if (layoutInternalGainGroup != null) {
            layoutInternalGainGroup.setVisibility((isInt || isDual) ? View.VISIBLE : View.GONE);
        }
        if (layoutAutoDuckGroup != null) {
            layoutAutoDuckGroup.setVisibility(isDual ? View.VISIBLE : View.GONE);
        }

        updateMicHint(masterEnabled);
    }

    private void updateMicHint(boolean enabled) {
        if (tvMicStatusHint != null) {
            if (!enabled) {
                tvMicStatusHint.setText("MUTED: Broadcast audio track silenced");
                tvMicStatusHint.setTextColor(getColor(R.color.studio_amber));
            } else {
                if (selectedAudioMode == AUDIO_MODE_MIC) {
                    tvMicStatusHint.setText("MIC: AAC 128 kbps • 44.1 kHz Voice Commentary");
                } else if (selectedAudioMode == AUDIO_MODE_INTERNAL) {
                    tvMicStatusHint.setText("GAME SOUND: Clean system playback audio");
                } else {
                    tvMicStatusHint.setText("DUAL MIX: Live Voice Commentary + Game Audio");
                }
                tvMicStatusHint.setTextColor(getColor(R.color.studio_text_secondary));
            }
        }
    }

    private void updateLocalRecordHint(boolean enabled) {
        if (tvLocalRecordHint == null) return;
        if (enabled) {
            String activeFile = StreamService.getLastRecordedFilePath();
            if (StreamService.isStreamingActive() && activeFile != null && !activeFile.isEmpty()) {
                tvLocalRecordHint.setText("🔴 REC VOD: " + new File(activeFile).getName());
                tvLocalRecordHint.setTextColor(getColor(R.color.studio_ruby));
            } else {
                tvLocalRecordHint.setText("ARMED: Saves MP4 VOD to Movies/KRLiveStudio/");
                tvLocalRecordHint.setTextColor(getColor(R.color.studio_cyan));
            }
        } else {
            String lastFile = StreamService.getLastRecordedFilePath();
            if (lastFile != null && !lastFile.isEmpty()) {
                tvLocalRecordHint.setText("Saved: " + new File(lastFile).getName());
                tvLocalRecordHint.setTextColor(getColor(R.color.studio_green));
            } else {
                tvLocalRecordHint.setText("Zero-CPU tee to Movies/KRLiveStudio/");
                tvLocalRecordHint.setTextColor(getColor(R.color.studio_text_secondary));
            }
        }
    }

    private void togglePrivacyShield() {
        boolean nextState = !StreamService.isPrivacyShieldActive();
        StreamService.setPrivacyShieldActive(nextState);
        FloatingCamManager.getInstance(this).setPrivacyShieldState(nextState);
        if (btnPrivacyShield != null) {
            btnPrivacyShield.setText(nextState ? "🛡️ SHIELD: ON" : "🛡️ SHIELD: OFF");
            btnPrivacyShield.setTextColor(getColor(nextState ? R.color.studio_ruby : R.color.studio_cyan));
        }
        if (StreamService.isStreamingActive()) {
            Intent pIntent = new Intent(this, StreamService.class);
            pIntent.setAction(StreamService.ACTION_TOGGLE_PRIVACY);
            pIntent.putExtra("privacyActive", nextState);
            startService(pIntent);
            Toast.makeText(this, nextState ? "Privacy Shield ENABLED (BRB Slate & Muted)" : "Privacy Shield DISABLED (Live broadcast resumed)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, nextState ? "Privacy Shield armed for broadcast" : "Privacy Shield disarmed", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateOverlayStatus() {
        if (tvOverlayStatus != null) {
            boolean hasPermission = checkOverlayPermission();
            boolean isShowing = FloatingCamManager.getInstance(this).isShowing();
            if (hasPermission) {
                if (isShowing) {
                    tvOverlayStatus.setText("Facecam: ACTIVE ON SCREEN (Drag to bottom to close)");
                    tvOverlayStatus.setTextColor(getColor(R.color.studio_cyan));
                } else {
                    tvOverlayStatus.setText("Overlay Permission: Ready & Granted");
                    tvOverlayStatus.setTextColor(getColor(R.color.studio_green));
                }
            } else {
                tvOverlayStatus.setText("Overlay Permission: Tap Switch to Grant");
                tvOverlayStatus.setTextColor(getColor(R.color.studio_amber));
            }
        }
    }

    private void computeResolutionDimensions() {
        boolean isPortrait;
        if (selectedOrientation == ORIENT_PORTRAIT) {
            isPortrait = true;
        } else if (selectedOrientation == ORIENT_AUTO) {
            int configOrientation = getResources().getConfiguration().orientation;
            isPortrait = (configOrientation != Configuration.ORIENTATION_LANDSCAPE);
        } else {
            isPortrait = false;
        }

        if (selectedResPreset == 1080) {
            selectedWidth = isPortrait ? 1080 : 1920;
            selectedHeight = isPortrait ? 1920 : 1080;
        } else if (selectedResPreset == 720) {
            selectedWidth = isPortrait ? 720 : 1280;
            selectedHeight = isPortrait ? 1280 : 720;
        } else {
            selectedWidth = isPortrait ? 480 : 854;
            selectedHeight = isPortrait ? 854 : 480;
        }
    }

    private void updateOrientationButtons() {
        boolean isLand = (selectedOrientation == ORIENT_LANDSCAPE);
        boolean isPort = (selectedOrientation == ORIENT_PORTRAIT);
        boolean isAuto = (selectedOrientation == ORIENT_AUTO);

        btnOrientLandscape.setBackgroundResource(isLand ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnOrientLandscape.setTextColor(getColor(isLand ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnOrientPortrait.setBackgroundResource(isPort ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnOrientPortrait.setTextColor(getColor(isPort ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnOrientAuto.setBackgroundResource(isAuto ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnOrientAuto.setTextColor(getColor(isAuto ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        if (tvOrientationBadge != null) {
            if (isLand) {
                tvOrientationBadge.setText("16:9 LANDSCAPE");
                tvOrientationBadge.setTextColor(getColor(R.color.studio_cyan));
            } else if (isPort) {
                tvOrientationBadge.setText("9:16 PORTRAIT");
                tvOrientationBadge.setTextColor(getColor(R.color.studio_ruby));
            } else {
                tvOrientationBadge.setText("AUTO SENSOR");
                tvOrientationBadge.setTextColor(getColor(R.color.studio_green));
            }
        }
    }

    private void updateBitrateHint() {
        if (tvBitrateHint == null) return;
        String orientDesc;
        if (selectedOrientation == ORIENT_PORTRAIT) {
            orientDesc = "Portrait (9:16)";
        } else if (selectedOrientation == ORIENT_AUTO) {
            orientDesc = "Auto Canvas";
        } else {
            orientDesc = "Landscape (16:9)";
        }

        if (selectedResPreset >= 1080) {
            if (selectedFps == 60) {
                tvBitrateHint.setText("Pro 1080p 60fps " + orientDesc + " • Recommended: 5,000–6,000 kbps for high-motion gaming");
            } else {
                tvBitrateHint.setText("Crisp 1080p " + selectedFps + "fps " + orientDesc + " • Recommended: 4,000–4,500 kbps for studio streams");
            }
        } else if (selectedResPreset >= 720) {
            if (selectedFps == 60) {
                tvBitrateHint.setText("Smooth 720p 60fps " + orientDesc + " • Recommended: 3,000–3,500 kbps for fast gameplay");
            } else {
                tvBitrateHint.setText("Balanced 720p " + selectedFps + "fps " + orientDesc + " • Recommended: 2,000–2,500 kbps standard profile");
            }
        } else {
            tvBitrateHint.setText("Mobile 480p " + selectedFps + "fps " + orientDesc + " • Target: 1,000–1,500 kbps low-bandwidth profile");
        }
    }

    private void selectPlatform(int index) {
        if (etStreamKey != null && selectedPlatformIndex >= 0) {
            String currentKey = etStreamKey.getText().toString().trim();
            if (!currentKey.isEmpty()) {
                prefs.edit().putString(PREF_STREAM_KEY_PREFIX + selectedPlatformIndex, currentKey).apply();
            }
        }
        selectedPlatformIndex = index;
        Button[] platformButtons = {
                btnPlatformYoutube, btnPlatformFacebook, btnPlatformTwitch,
                btnPlatformTiktok, btnPlatformKick, btnPlatformCustom
        };

        for (int i = 0; i < platformButtons.length; i++) {
            if (i == index) {
                platformButtons[i].setBackgroundResource(R.drawable.chip_active);
                platformButtons[i].setTextColor(getColor(R.color.studio_chip_active_text));
            } else {
                platformButtons[i].setBackgroundResource(R.drawable.chip_inactive);
                platformButtons[i].setTextColor(getColor(R.color.studio_chip_text));
            }
        }

        if (index == 3) {
            selectedOrientation = ORIENT_PORTRAIT;
            computeResolutionDimensions();
            updateOrientationButtons();
            updateResolutionButtons();
        }

        if (index < 5) {
            etServerUrl.setText(PRESET_URLS[index]);
            etServerUrl.setEnabled(false);
            if (tvUrlLockBadge != null) {
                tvUrlLockBadge.setText("PRESET LOCKED");
                tvUrlLockBadge.setTextColor(getColor(R.color.studio_cyan));
            }
        } else {
            etServerUrl.setEnabled(true);
            if (tvUrlLockBadge != null) {
                tvUrlLockBadge.setText("CUSTOM RTMP");
                tvUrlLockBadge.setTextColor(getColor(R.color.studio_ruby));
            }
        }

        if (etStreamKey != null) {
            String key = prefs.getString(PREF_STREAM_KEY_PREFIX + index, "");
            if (key.isEmpty() && index == 0) {
                key = prefs.getString("pref_stream_key", "");
            }
            etStreamKey.setText(key);
        }

        if (index == 1) { // Facebook Live recommendation
            if (targetBitrateKbps > 4500 || targetBitrateKbps < 3000) {
                targetBitrateKbps = 4000;
                if (seekBitrate != null) seekBitrate.setProgress(targetBitrateKbps);
                if (tvBitrateValue != null) tvBitrateValue.setText(String.format(Locale.US, "%,d kbps", targetBitrateKbps));
                updateBitrateHint();
            }
        }
        updatePlatformHelperCard();
        saveCurrentPreferences();
    }

    private void updatePlatformHelperCard() {
        if (layoutPlatformKeyHelper == null) return;
        if (selectedPlatformIndex == 5) { // Custom RTMP
            layoutPlatformKeyHelper.setVisibility(View.GONE);
            return;
        }

        layoutPlatformKeyHelper.setVisibility(View.VISIBLE);
        switch (selectedPlatformIndex) {
            case 1: // Facebook
                if (tvPlatformHelperTitle != null) tvPlatformHelperTitle.setText("FACEBOOK LIVE PRODUCER (COMPATIBLE)");
                if (btnOpenPlatformPortal != null) btnOpenPlatformPortal.setText("🌐 GET FB KEY");
                if (tvPlatformHelperHint != null) {
                    tvPlatformHelperHint.setText("1. Tap [GET FB KEY] (In browser: request desktop site).\n2. Tap 'Set up live video' -> select 'Streaming software'.\n3. Copy key -> tap [PASTE] in KR Live Studio.");
                }
                break;
            case 0: // YouTube
                if (tvPlatformHelperTitle != null) tvPlatformHelperTitle.setText("YOUTUBE LIVE CONTROL ROOM (COMPATIBLE)");
                if (btnOpenPlatformPortal != null) btnOpenPlatformPortal.setText("🌐 GET YT KEY");
                if (tvPlatformHelperHint != null) {
                    tvPlatformHelperHint.setText("Copy your Stream Key from YouTube Studio Live dashboard, then tap [PASTE] here.");
                }
                break;
            case 2: // Twitch
                if (tvPlatformHelperTitle != null) tvPlatformHelperTitle.setText("TWITCH CREATOR DASHBOARD (COMPATIBLE)");
                if (btnOpenPlatformPortal != null) btnOpenPlatformPortal.setText("🌐 GET TWITCH KEY");
                if (tvPlatformHelperHint != null) {
                    tvPlatformHelperHint.setText("Copy your Primary Stream Key from Twitch Settings > Stream, then tap [PASTE] here.");
                }
                break;
            case 3: // TikTok
                if (tvPlatformHelperTitle != null) tvPlatformHelperTitle.setText("TIKTOK LIVE CREATOR (COMPATIBLE)");
                if (btnOpenPlatformPortal != null) btnOpenPlatformPortal.setText("🌐 TIKTOK PORTAL");
                if (tvPlatformHelperHint != null) {
                    tvPlatformHelperHint.setText("Requires TikTok Live Studio / RTMP key access from your TikTok creator account.");
                }
                break;
            case 4: // Kick
                if (tvPlatformHelperTitle != null) tvPlatformHelperTitle.setText("KICK CREATOR DASHBOARD (COMPATIBLE)");
                if (btnOpenPlatformPortal != null) btnOpenPlatformPortal.setText("🌐 GET KICK KEY");
                if (tvPlatformHelperHint != null) {
                    tvPlatformHelperHint.setText("Copy your Stream Key from Kick Creator Dashboard > Settings, then tap [PASTE] here.");
                }
                break;
        }
    }

    private void openPlatformPortal() {
        String url;
        String platformName;
        switch (selectedPlatformIndex) {
            case 1: // Facebook
                url = "https://www.facebook.com/live/producer";
                platformName = "Facebook Live Producer";
                break;
            case 0: // YouTube
                url = "https://studio.youtube.com/channel/UC/livestreaming";
                platformName = "YouTube Live Control Room";
                break;
            case 2: // Twitch
                url = "https://dashboard.twitch.tv/settings/stream";
                platformName = "Twitch Dashboard";
                break;
            case 3: // TikTok
                url = "https://www.tiktok.com/live/creators/en-us/";
                platformName = "TikTok Creator Portal";
                break;
            case 4: // Kick
                url = "https://kick.com/dashboard/settings/stream";
                platformName = "Kick Stream Settings";
                break;
            default:
                return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (selectedPlatformIndex == 1) {
                Toast.makeText(this, "In browser, request 'Desktop site' to view your Stream Key!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Opening " + platformName + "...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open web browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void updateResolutionButtons() {
        boolean is1080 = (selectedResPreset == 1080);
        boolean is720 = (selectedResPreset == 720);
        boolean is480 = (selectedResPreset == 480);

        btnRes1080p.setBackgroundResource(is1080 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnRes1080p.setTextColor(getColor(is1080 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnRes720p.setBackgroundResource(is720 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnRes720p.setTextColor(getColor(is720 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnRes480p.setBackgroundResource(is480 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnRes480p.setTextColor(getColor(is480 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        updateBitrateHint();
    }

    private void updateFpsButtons() {
        boolean is60 = (selectedFps == 60);
        boolean is30 = (selectedFps == 30);
        boolean is24 = (selectedFps == 24);

        btnFps60.setBackgroundResource(is60 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnFps60.setTextColor(getColor(is60 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnFps30.setBackgroundResource(is30 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnFps30.setTextColor(getColor(is30 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        btnFps24.setBackgroundResource(is24 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnFps24.setTextColor(getColor(is24 ? R.color.studio_chip_active_text : R.color.studio_chip_text));

        if (tvFpsBadge != null) {
            if (is60) {
                tvFpsBadge.setText("60 FPS SMOOTH");
                tvFpsBadge.setTextColor(getColor(R.color.studio_cyan));
            } else if (is30) {
                tvFpsBadge.setText("30 FPS STANDARD");
                tvFpsBadge.setTextColor(getColor(R.color.studio_green));
            } else {
                tvFpsBadge.setText("24 FPS CINEMA");
                tvFpsBadge.setTextColor(getColor(R.color.studio_amber));
            }
        }

        updateBitrateHint();
    }

    private void requestNecessaryPermissions() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            }, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean facecamPref = prefs.getBoolean("pref_facecam_enabled", true);
            if (facecamPref) {
                if (checkOverlayPermission()) {
                    startFacecamService();
                } else {
                    requestOverlayPermission();
                }
            }
        }
    }

    private boolean checkOverlayPermission() {
        return Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
    }

    private void toggleStreaming() {
        if (StreamService.isStreamingActive()) {
            Intent stopIntent = new Intent(this, StreamService.class);
            stopIntent.setAction(StreamService.ACTION_STOP);
            startService(stopIntent);
            updateUiStreamState(false);
        } else {
            wasStartedFromFloatingCam = false;
            handleStartStreamRequest();
        }
    }

    private void handleStartStreamRequest() {
        if (StreamService.isStreamingActive()) {
            Toast.makeText(this, "Broadcast is already active", Toast.LENGTH_SHORT).show();
            return;
        }

        String serverUrl = etServerUrl != null ? etServerUrl.getText().toString().trim() : "";
        String streamKey = etStreamKey != null ? etStreamKey.getText().toString().trim() : "";

        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Enter RTMP Ingest URL", Toast.LENGTH_SHORT).show();
            return;
        }
        if (streamKey.isEmpty()) {
            Toast.makeText(this, "Enter Stream Key", Toast.LENGTH_SHORT).show();
            return;
        }

        if (switchMicAudio != null && switchMicAudio.isChecked() &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_PERMISSIONS);
            Toast.makeText(this, "Microphone permission required for audio streaming", Toast.LENGTH_SHORT).show();
            return;
        }
        if (switchFacecam != null && switchFacecam.isChecked() &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_PERMISSIONS);
            Toast.makeText(this, "Camera permission required for facecam overlay", Toast.LENGTH_SHORT).show();
            return;
        }

        saveCurrentPreferences();

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            try {
                startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE_MEDIA_PROJECTION);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to prompt screen capture: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            updateOverlayStatus();
            if (checkOverlayPermission()) {
                switchFacecam.setChecked(true);
                startFacecamService();
                Toast.makeText(this, "Facecam overlay enabled!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Overlay permission is required for floating facecam.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                computeResolutionDimensions();
                Intent serviceIntent = new Intent(this, StreamService.class);
                serviceIntent.setAction(StreamService.ACTION_START);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("resultData", data);
                serviceIntent.putExtra("serverUrl", etServerUrl.getText().toString().trim());
                serviceIntent.putExtra("streamKey", etStreamKey.getText().toString().trim());
                serviceIntent.putExtra("videoWidth", selectedWidth);
                serviceIntent.putExtra("videoHeight", selectedHeight);
                serviceIntent.putExtra("videoFps", selectedFps);
                serviceIntent.putExtra("videoOrientation", selectedOrientation);
                serviceIntent.putExtra("bitrateKbps", targetBitrateKbps);
                serviceIntent.putExtra("enableMic", switchMicAudio.isChecked());
                serviceIntent.putExtra("enableFacecam", switchFacecam.isChecked());
                serviceIntent.putExtra("audioMode", selectedAudioMode);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                updateUiStreamState(true);
                if (wasStartedFromFloatingCam) {
                    wasStartedFromFloatingCam = false;
                    moveTaskToBack(true);
                }
            } else {
                wasStartedFromFloatingCam = false;
                Toast.makeText(this, "Screen recording permission was denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateUiStreamState(boolean isStreaming) {
        FloatingCamManager.getInstance(this).setLiveState(isStreaming);
        if (isStreaming) {
            btnStartStopStream.setText("■ STOP BROADCAST (ON AIR)");
            btnStartStopStream.setBackgroundResource(R.drawable.btn_m3_outlined);
            btnStartStopStream.setTextColor(getColor(R.color.studio_ruby));
            tvLiveStatus.setText("LIVE BROADCASTING (ON AIR)");
            tvLiveStatus.setTextColor(getColor(R.color.studio_ruby));
            if (ivLiveBeacon != null) ivLiveBeacon.setVisibility(View.VISIBLE);
        } else {
            btnStartStopStream.setText("● START BROADCAST (GO LIVE)");
            btnStartStopStream.setBackgroundResource(R.drawable.btn_m3_primary);
            btnStartStopStream.setTextColor(getColor(R.color.studio_chip_active_text));
            tvLiveStatus.setText("STUDIO READY (STANDBY)");
            tvLiveStatus.setTextColor(getColor(R.color.studio_text_secondary));
        }
    }

    private void updateTelemetryDisplay() {
        boolean active = StreamService.isStreamingActive();
        updateUiStreamState(active);
        if (active) {
            long uptimeMs = StreamService.getStreamUptimeMs();
            long seconds = (uptimeMs / 1000) % 60;
            long minutes = (uptimeMs / (1000 * 60)) % 60;
            long hours = uptimeMs / (1000 * 60 * 60);
            tvUptime.setText(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));

            tvLiveBitrate.setText(String.format(Locale.US, "%,d kbps", StreamService.getCurrentBitrateKbps()));
            tvLiveFps.setText(String.format(Locale.US, "%.1f fps", StreamService.getCurrentFps()));
            tvDroppedFrames.setText(String.valueOf(StreamService.getDroppedFramesCount()));

            int peak = StreamService.getCurrentAudioPeak();
            int level = 0;
            if (peak > 80) {
                double ratio = (double) peak / 32767.0;
                double db = 20.0 * Math.log10(ratio);
                if (db > -3.0) level = 10;
                else if (db > -6.0) level = 9;
                else if (db > -10.0) level = 8;
                else if (db > -14.0) level = 7;
                else if (db > -18.0) level = 6;
                else if (db > -24.0) level = 5;
                else if (db > -30.0) level = 4;
                else if (db > -36.0) level = 3;
                else if (db > -42.0) level = 2;
                else level = 1;
            }
            updateVuMeter(level, peak);
        } else {
            tvUptime.setText("00:00:00");
            tvLiveBitrate.setText("0 kbps");
            tvLiveFps.setText("0.0 fps");
            tvDroppedFrames.setText("0");
            updateVuMeter(0, 0);
        }

        // Hardware Health, Thermal & Privacy Shield Telemetry
        if (tvThermalStatus != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                int thermal = pm.getCurrentThermalStatus();
                if (thermal == PowerManager.THERMAL_STATUS_NONE) {
                    tvThermalStatus.setText("THERMAL: OPTIMAL");
                    tvThermalStatus.setTextColor(getColor(R.color.studio_green));
                } else if (thermal <= PowerManager.THERMAL_STATUS_LIGHT) {
                    tvThermalStatus.setText("THERMAL: WARM");
                    tvThermalStatus.setTextColor(getColor(R.color.studio_cyan));
                } else if (thermal <= PowerManager.THERMAL_STATUS_MODERATE) {
                    tvThermalStatus.setText("THERMAL: HEATED");
                    tvThermalStatus.setTextColor(getColor(R.color.studio_amber));
                } else {
                    tvThermalStatus.setText("THERMAL: THROTTLING");
                    tvThermalStatus.setTextColor(getColor(R.color.studio_ruby));
                }
            }
        }

        if (tvStreamHealth != null) {
            if (active) {
                boolean shielded = StreamService.isPrivacyShieldActive();
                String rtmpSt = StreamService.getRtmpStatus();
                long drops = StreamService.getDroppedFramesCount();
                int abrKbps = StreamService.getAbrTargetKbps();
                if (shielded) {
                    tvStreamHealth.setText("HEALTH: SHIELDED (BRB SLATE)");
                    tvStreamHealth.setTextColor(getColor(R.color.studio_cyan));
                } else if (rtmpSt != null && rtmpSt.startsWith("RECONNECTING")) {
                    tvStreamHealth.setText("HEALTH: " + rtmpSt);
                    tvStreamHealth.setTextColor(getColor(R.color.studio_ruby));
                } else if (abrKbps > 0 && abrKbps < targetBitrateKbps) {
                    tvStreamHealth.setText("HEALTH: ABR SCALED (" + abrKbps + "k)");
                    tvStreamHealth.setTextColor(getColor(R.color.studio_amber));
                } else if (drops > 50) {
                    tvStreamHealth.setText("HEALTH: CONGESTED (" + drops + " drops)");
                    tvStreamHealth.setTextColor(getColor(R.color.studio_amber));
                } else {
                    tvStreamHealth.setText("HEALTH: EXCELLENT");
                    tvStreamHealth.setTextColor(getColor(R.color.studio_green));
                }
            } else {
                tvStreamHealth.setText("HEALTH: READY (STANDBY)");
                tvStreamHealth.setTextColor(getColor(R.color.studio_text_muted));
            }
        }

        if (btnPrivacyShield != null) {
            boolean isShield = StreamService.isPrivacyShieldActive();
            btnPrivacyShield.setText(isShield ? "🛡️ SHIELD: ON" : "🛡️ SHIELD: OFF");
            btnPrivacyShield.setTextColor(getColor(isShield ? R.color.studio_ruby : R.color.studio_cyan));
        }

        if (switchLocalRecord != null) {
            updateLocalRecordHint(switchLocalRecord.isChecked());
        }
    }

    private void updateVuMeter(int level, int peak) {
        if (vuSegments == null) return;
        int onGreen = getColor(R.color.studio_vu_green);
        int onYellow = getColor(R.color.studio_vu_yellow);
        int onRed = getColor(R.color.studio_vu_red);
        int offColor = getColor(R.color.studio_vu_off);

        for (int i = 0; i < vuSegments.length; i++) {
            if (vuSegments[i] == null) continue;
            if (i < level) {
                if (i < 5) {
                    vuSegments[i].setBackgroundColor(onGreen);
                } else if (i < 8) {
                    vuSegments[i].setBackgroundColor(onYellow);
                } else {
                    vuSegments[i].setBackgroundColor(onRed);
                }
            } else {
                vuSegments[i].setBackgroundColor(offColor);
            }
        }
        if (tvVuLevel != null) {
            if (peak > 80) {
                double db = 20.0 * Math.log10((double) peak / 32767.0);
                tvVuLevel.setText(String.format(Locale.US, "%.0f dB", db));
            } else {
                tvVuLevel.setText("-inf dB");
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                btnStartStopStream.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                if (getCurrentFocus() == null || !(getCurrentFocus() instanceof EditText)) {
                    switchMicAudio.toggle();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                seekBitrate.setProgress(Math.min(seekBitrate.getMax(), seekBitrate.getProgress() + 250));
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
                seekBitrate.setProgress(Math.max(500, seekBitrate.getProgress() - 250));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserves layout without activity rebuild on desktop window resizing
        if (selectedOrientation == ORIENT_AUTO) {
            computeResolutionDimensions();
            updateOrientationButtons();
            updateBitrateHint();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean facecamPref = prefs.getBoolean("pref_facecam_enabled", true);
        if (facecamPref && checkOverlayPermission() && !FloatingCamManager.getInstance(this).isShowing()) {
            startFacecamService();
        }
        updateOverlayStatus();
        uiHandler.post(telemetryRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(telemetryRunnable);
    }

    // =========================================================================
    // Embedded Pure-Java Foreground Streaming Service
    // =========================================================================
    public static class StreamService extends Service {

        public static final String ACTION_START = "com.kimrosil.krlivestudio.START";
        public static final String ACTION_STOP = "com.kimrosil.krlivestudio.STOP";
        public static final String ACTION_START_FACECAM = "com.kimrosil.krlivestudio.START_FACECAM";
        public static final String ACTION_STOP_FACECAM = "com.kimrosil.krlivestudio.STOP_FACECAM";
        public static final String ACTION_TOGGLE_PRIVACY = "com.kimrosil.krlivestudio.TOGGLE_PRIVACY";
        private static final String CHANNEL_ID = "kim_live_studio_channel_live";

        private static volatile boolean isStreaming = false;
        private static volatile boolean isPrivacyShieldActive = false;
        private static volatile boolean isMicMuted = false;
        private static volatile int activeAudioMode = AUDIO_MODE_MIC;
        private static volatile boolean isAbrEnabled = true;
        private static volatile int abrTargetKbps = 0;
        private static volatile int micGainPercent = 100;
        private static volatile int internalGainPercent = 80;
        private static volatile boolean isAutoDuckEnabled = true;
        private static volatile boolean isLocalRecordingEnabled = false;
        private static volatile String lastRecordedFilePath = "";
        private static volatile String rtmpStatus = "STANDBY";
        public static volatile long masterStreamStartNs = 0;
        private static volatile long streamStartTime = 0;
        static volatile int currentBitrateKbps = 0;
        static volatile float currentFps = 0.0f;
        static volatile long droppedFrames = 0;
        public static volatile int currentAudioPeak = 0;

        private MediaProjection mediaProjection;
        private VideoEncoder videoEncoder;
        private AudioEncoder audioEncoder;
        private RtmpMuxerClient rtmpClient;
        private LocalMp4Recorder localRecorder;

        public static boolean isStreamingActive() {
            return isStreaming;
        }

        public static boolean isPrivacyShieldActive() {
            return isPrivacyShieldActive;
        }

        public static void setPrivacyShieldActive(boolean active) {
            isPrivacyShieldActive = active;
        }

        public static boolean isMicMuted() {
            return isMicMuted;
        }

        public static void setMicMuted(boolean muted) {
            isMicMuted = muted;
        }

        public static int getAudioMode() {
            return activeAudioMode;
        }

        public static void setAudioMode(int mode) {
            activeAudioMode = mode;
        }

        public static boolean isAbrEnabled() {
            return isAbrEnabled;
        }

        public static void setAbrEnabled(boolean enabled) {
            isAbrEnabled = enabled;
        }

        public static int getAbrTargetKbps() {
            return abrTargetKbps;
        }

        public static void setAbrTargetKbps(int kbps) {
            abrTargetKbps = kbps;
        }

        public static int getMicGainPercent() {
            return micGainPercent;
        }

        public static void setMicGainPercent(int percent) {
            micGainPercent = Math.max(0, Math.min(200, percent));
        }

        public static int getInternalGainPercent() {
            return internalGainPercent;
        }

        public static void setInternalGainPercent(int percent) {
            internalGainPercent = Math.max(0, Math.min(200, percent));
        }

        public static boolean isAutoDuckEnabled() {
            return isAutoDuckEnabled;
        }

        public static void setAutoDuckEnabled(boolean enabled) {
            isAutoDuckEnabled = enabled;
        }

        public static boolean isLocalRecordingEnabled() {
            return isLocalRecordingEnabled;
        }

        public static void setLocalRecordingEnabled(boolean enabled) {
            isLocalRecordingEnabled = enabled;
        }

        public static String getLastRecordedFilePath() {
            return lastRecordedFilePath;
        }

        public static String getRtmpStatus() {
            return rtmpStatus;
        }

        public static void setRtmpStatus(String status) {
            rtmpStatus = status;
        }

        public static long getStreamUptimeMs() {
            return isStreaming ? (SystemClock.elapsedRealtime() - streamStartTime) : 0;
        }

        public static int getCurrentBitrateKbps() {
            return currentBitrateKbps;
        }

        public static float getCurrentFps() {
            return currentFps;
        }

        public static long getDroppedFramesCount() {
            return droppedFrames;
        }

        public static int getCurrentAudioPeak() {
            return currentAudioPeak;
        }

        @Override
        public void onCreate() {
            super.onCreate();
            createNotificationChannel();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent == null) return START_NOT_STICKY;
            String action = intent.getAction();

            if (ACTION_START.equals(action)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    int fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                    boolean enableMic = intent.getBooleanExtra("enableMic", true);
                    boolean enableFacecam = intent.getBooleanExtra("enableFacecam", true);
                    if (enableMic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        fgsType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                    }
                    if (enableFacecam && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        fgsType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                    }
                    startForeground(101, buildNotification("KR Live Studio is ON AIR..."), fgsType);
                } else {
                    startForeground(101, buildNotification("KR Live Studio is ON AIR..."));
                }
                startStreamingPipeline(intent);
            } else if (ACTION_STOP.equals(action)) {
                stopStreamingPipeline();
                if (!FloatingCamManager.getInstance(this).isShowing()) {
                    stopForeground(true);
                    stopSelf();
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        int fgsType = 0;
                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                        }
                        if (fgsType != 0) {
                            startForeground(101, buildNotification("KR Live Studio Facecam Active"), fgsType);
                        } else {
                            startForeground(101, buildNotification("KR Live Studio Facecam Active"));
                        }
                    } else {
                        startForeground(101, buildNotification("KR Live Studio Facecam Active"));
                    }
                }
            } else if (ACTION_START_FACECAM.equals(action)) {
                if (!isStreaming) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        int fgsType = 0;
                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                        }
                        if (fgsType != 0) {
                            startForeground(101, buildNotification("KR Live Studio Facecam Active"), fgsType);
                        } else {
                            startForeground(101, buildNotification("KR Live Studio Facecam Active"));
                        }
                    } else {
                        startForeground(101, buildNotification("KR Live Studio Facecam Active"));
                    }
                }
                FloatingCamManager.getInstance(this).showOverlay();
            } else if (ACTION_STOP_FACECAM.equals(action)) {
                FloatingCamManager.getInstance(this).hideOverlay();
                if (!isStreaming) {
                    stopForeground(true);
                    stopSelf();
                }
            } else if (ACTION_TOGGLE_PRIVACY.equals(action)) {
                if (intent.hasExtra("privacyActive")) {
                    isPrivacyShieldActive = intent.getBooleanExtra("privacyActive", false);
                } else {
                    isPrivacyShieldActive = !isPrivacyShieldActive;
                }
                FloatingCamManager.getInstance(this).setPrivacyShieldState(isPrivacyShieldActive);
                if (videoEncoder != null) {
                    videoEncoder.onPrivacyShieldToggled(isPrivacyShieldActive);
                }
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.notify(101, buildNotification(isPrivacyShieldActive ? "KR Live Studio: PRIVACY SHIELD ACTIVE (BRB Slate)" : "KR Live Studio is ON AIR..."));
                }
            }
            return START_NOT_STICKY;
        }

        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "KR Live Studio Broadcast",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Shows active live streaming status & telemetry");
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
        }

        private Notification buildNotification(String contentText) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
            );

            Intent stopIntent = new Intent(this, StreamService.class);
            stopIntent.setAction(ACTION_STOP);
            PendingIntent stopPendingIntent = PendingIntent.getService(
                    this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
            );

            Intent privIntent = new Intent(this, StreamService.class);
            privIntent.setAction(ACTION_TOGGLE_PRIVACY);
            PendingIntent privPendingIntent = PendingIntent.getService(
                    this, 2, privIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }

            return builder.setContentTitle("KR Live Studio")
                    .setContentText(contentText)
                    .setSmallIcon(android.R.drawable.presence_video_online)
                    .setContentIntent(pendingIntent)
                    .addAction(android.R.drawable.ic_lock_lock, isPrivacyShieldActive ? "Resume" : "Shield", privPendingIntent)
                    .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
                    .setOngoing(true)
                    .build();
        }

        private void startStreamingPipeline(Intent intent) {
            if (isStreaming) return;

            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent resultData = intent.getParcelableExtra("resultData");
            String serverUrl = intent.getStringExtra("serverUrl");
            String streamKey = intent.getStringExtra("streamKey");
            int width = intent.getIntExtra("videoWidth", 1280);
            int height = intent.getIntExtra("videoHeight", 720);
            int fps = intent.getIntExtra("videoFps", 30);
            int bitrate = intent.getIntExtra("bitrateKbps", 2500);
            boolean enableMic = intent.getBooleanExtra("enableMic", true);
            boolean enableFacecam = intent.getBooleanExtra("enableFacecam", true);
            int audioMode = intent.getIntExtra("audioMode", AUDIO_MODE_MIC);

            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm == null || resultData == null) return;

            mediaProjection = mpm.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) return;

            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopStreamingPipeline();
                }
            }, new Handler(Looper.getMainLooper()));

            isStreaming = true;
            activeAudioMode = audioMode;
            masterStreamStartNs = SystemClock.elapsedRealtimeNanos();
            streamStartTime = SystemClock.elapsedRealtime();
            droppedFrames = 0;
            currentBitrateKbps = bitrate;
            abrTargetKbps = bitrate;
            currentFps = (float) fps;
            rtmpStatus = "CONNECTING...";

            FloatingCamManager.getInstance(this).setLiveState(true);
            FloatingCamManager.getInstance(this).setMicMutedState(!enableMic || isMicMuted);
            FloatingCamManager.getInstance(this).setPrivacyShieldState(isPrivacyShieldActive);
            if (enableFacecam && Settings.canDrawOverlays(this)) {
                FloatingCamManager.getInstance(this).showOverlay();
            }

            if (isLocalRecordingEnabled) {
                localRecorder = new LocalMp4Recorder(getApplicationContext(), enableMic);
                String outPath = localRecorder.getOutputPath();
                if (outPath != null) {
                    lastRecordedFilePath = outPath;
                }
            } else {
                localRecorder = null;
            }

            rtmpClient = new RtmpMuxerClient(serverUrl, streamKey, width, height, fps, bitrate);

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            videoEncoder = new VideoEncoder(mediaProjection, width, height, fps, bitrate, metrics.densityDpi, rtmpClient, localRecorder);
            rtmpClient.setSyncFrameListener(() -> {
                if (videoEncoder != null) {
                    videoEncoder.requestKeyframe();
                }
            });
            rtmpClient.setBitrateAdjustListener(newKbps -> {
                if (videoEncoder != null) {
                    videoEncoder.setDynamicBitrate(newKbps);
                }
            });

            rtmpClient.start();
            videoEncoder.start();
            if (isPrivacyShieldActive) {
                videoEncoder.onPrivacyShieldToggled(true);
            }

            if (enableMic) {
                audioEncoder = new AudioEncoder(rtmpClient, mediaProjection, audioMode, localRecorder);
                audioEncoder.start();
            }
        }

        private void stopStreamingPipeline() {
            isStreaming = false;
            isPrivacyShieldActive = false;
            rtmpStatus = "STANDBY";
            masterStreamStartNs = 0;
            currentAudioPeak = 0;
            abrTargetKbps = 0;
            FloatingCamManager.getInstance(this).setLiveState(false);
            FloatingCamManager.getInstance(this).setPrivacyShieldState(false);

            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder = null;
            }
            if (audioEncoder != null) {
                audioEncoder.stop();
                audioEncoder = null;
            }
            if (localRecorder != null) {
                localRecorder.stopAndRelease();
                localRecorder = null;
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
            if (rtmpClient != null) {
                rtmpClient.stop();
                rtmpClient = null;
            }
        }

        @Override
        public void onDestroy() {
            stopStreamingPipeline();
            super.onDestroy();
        }
    }

    // =========================================================================
    // Video Encoder (MediaProjection + MediaCodec H.264 Live Broadcaster)
    // =========================================================================
    public static class VideoEncoder implements Runnable {
        private final MediaProjection projection;
        private final int width;
        private final int height;
        private final int fps;
        private final int bitrate;
        private final int dpi;
        private final RtmpMuxerClient rtmp;
        private final LocalMp4Recorder localRecorder;
        private final AtomicBoolean isRunning = new AtomicBoolean(false);
        private final Object surfaceLock = new Object();
        private volatile boolean slateActive = false;
        private Thread slateThread;
        private long frameCount = 0;
        private long lastFpsCalcTime = 0;
        private long baseVideoPtsUs = -1;
        private long lastVideoPtsMs = 0;

        private MediaCodec mediaCodec;
        private Surface inputSurface;
        private VirtualDisplay virtualDisplay;
        private Thread thread;

        public VideoEncoder(MediaProjection projection, int width, int height, int fps, int bitrateKbps, int dpi, RtmpMuxerClient rtmp, LocalMp4Recorder localRecorder) {
            this.projection = projection;
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrate = bitrateKbps * 1000;
            this.dpi = Math.max(160, dpi);
            this.rtmp = rtmp;
            this.localRecorder = localRecorder;
        }

        public void start() {
            isRunning.set(true);
            thread = new Thread(this, "KimLive-VideoEncoder");
            thread.start();
        }

        public void setDynamicBitrate(int targetKbps) {
            if (mediaCodec != null) {
                try {
                    Bundle b = new Bundle();
                    b.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, Math.max(300, targetKbps) * 1000);
                    mediaCodec.setParameters(b);
                } catch (Exception ignored) {}
            }
        }

        @Override
        public void run() {
            try {
                MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1s GOP for instant start & recovery
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);

                // Keep screen streaming alive even if the display is static
                format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000L / fps);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    format.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1);
                }

                int targetProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
                int targetLevel = (fps >= 60 || width >= 1920)
                        ? MediaCodecInfo.CodecProfileLevel.AVCLevel51
                        : MediaCodecInfo.CodecProfileLevel.AVCLevel41;
                format.setInteger(MediaFormat.KEY_PROFILE, targetProfile);
                format.setInteger(MediaFormat.KEY_LEVEL, targetLevel);

                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                try {
                    mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                } catch (Exception e) {
                    Log.w("KimLive-VideoEncoder", "Falling back to Baseline Profile: " + e.getMessage());
                    format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
                    format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41);
                    mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                }

                inputSurface = mediaCodec.createInputSurface();
                mediaCodec.start();

                // Request initial sync frame immediately
                try {
                    Bundle syncBundle = new Bundle();
                    syncBundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                    mediaCodec.setParameters(syncBundle);
                } catch (Exception ignored) {}

                // Use VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR so the screen contents are mirrored to inputSurface
                virtualDisplay = projection.createVirtualDisplay(
                        "KimLiveScreenCapture",
                        width, height, dpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        slateActive ? null : inputSurface, null, null
                );

                if (slateActive) {
                    startSlateRenderer();
                }

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                while (isRunning.get()) {
                    int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = mediaCodec.getOutputFormat();
                        if (localRecorder != null) {
                            localRecorder.setVideoFormat(newFormat);
                        }
                        ByteBuffer csd0Buf = newFormat.getByteBuffer("csd-0");
                        ByteBuffer csd1Buf = newFormat.getByteBuffer("csd-1");
                        byte[] sps = null;
                        byte[] pps = null;
                        if (csd0Buf != null) {
                            ByteBuffer dup0 = csd0Buf.duplicate();
                            byte[] csd0 = new byte[dup0.remaining()];
                            dup0.get(csd0);
                            List<byte[]> nals0 = RtmpMuxerClient.extractNalUnits(csd0);
                            for (byte[] nal : nals0) {
                                int type = nal[0] & 0x1F;
                                if (type == 7) sps = nal;
                                else if (type == 8) pps = nal;
                            }
                        }
                        if (csd1Buf != null) {
                            ByteBuffer dup1 = csd1Buf.duplicate();
                            byte[] csd1 = new byte[dup1.remaining()];
                            dup1.get(csd1);
                            List<byte[]> nals1 = RtmpMuxerClient.extractNalUnits(csd1);
                            for (byte[] nal : nals1) {
                                int type = nal[0] & 0x1F;
                                if (type == 7 && sps == null) sps = nal;
                                else if (type == 8) pps = nal;
                            }
                        }
                        if (sps != null && pps != null) {
                            rtmp.sendAvcSequenceHeader(sps, pps);
                        }
                    } else if (outputIndex >= 0) {
                        ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputIndex);
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            byte[] chunk = new byte[bufferInfo.size];
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            outputBuffer.get(chunk);

                            boolean isConfig = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                            boolean isKeyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;

                            if (isConfig) {
                                List<byte[]> nals = RtmpMuxerClient.extractNalUnits(chunk);
                                byte[] spsC = null;
                                byte[] ppsC = null;
                                for (byte[] nal : nals) {
                                    int type = nal[0] & 0x1F;
                                    if (type == 7) spsC = nal;
                                    else if (type == 8) ppsC = nal;
                                }
                                if (spsC != null && ppsC != null) {
                                    rtmp.sendAvcSequenceHeader(spsC, ppsC);
                                }
                            } else {
                                long masterStartNs = StreamService.masterStreamStartNs;
                                long videoPtsMs;
                                if (masterStartNs > 0) {
                                    videoPtsMs = Math.max(0, (SystemClock.elapsedRealtimeNanos() - masterStartNs) / 1000000L);
                                } else {
                                    if (baseVideoPtsUs < 0) {
                                        baseVideoPtsUs = bufferInfo.presentationTimeUs;
                                    }
                                    videoPtsMs = Math.max(0, (bufferInfo.presentationTimeUs - baseVideoPtsUs) / 1000);
                                }
                                if (videoPtsMs < lastVideoPtsMs) {
                                    videoPtsMs = lastVideoPtsMs + 1;
                                }
                                lastVideoPtsMs = videoPtsMs;

                                rtmp.sendVideoFrame(chunk, isKeyFrame, videoPtsMs);

                                if (localRecorder != null) {
                                    localRecorder.writeVideoSample(outputBuffer, bufferInfo, videoPtsMs * 1000L);
                                }

                                frameCount++;
                                long now = SystemClock.elapsedRealtime();
                                if (lastFpsCalcTime == 0) {
                                    lastFpsCalcTime = now;
                                } else if (now - lastFpsCalcTime >= 1000) {
                                    StreamService.currentFps = (float) (frameCount * 1000.0 / (now - lastFpsCalcTime));
                                    frameCount = 0;
                                    lastFpsCalcTime = now;
                                }
                            }
                        }
                        mediaCodec.releaseOutputBuffer(outputIndex, false);
                    }
                }
            } catch (Exception e) {
                Log.e("KimLive-VideoEncoder", "VideoEncoder error: " + e.getMessage(), e);
            } finally {
                release();
            }
        }

        public void requestKeyframe() {
            if (mediaCodec != null) {
                try {
                    Bundle syncBundle = new Bundle();
                    syncBundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                    mediaCodec.setParameters(syncBundle);
                } catch (Exception ignored) {}
            }
        }

        public void onPrivacyShieldToggled(boolean isShield) {
            synchronized (surfaceLock) {
                slateActive = isShield;
                if (virtualDisplay != null && inputSurface != null) {
                    try {
                        if (isShield) {
                            virtualDisplay.setSurface(null);
                            startSlateRenderer();
                        } else {
                            stopSlateRenderer();
                            virtualDisplay.setSurface(inputSurface);
                            requestKeyframe();
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        private void startSlateRenderer() {
            stopSlateRenderer();
            slateThread = new Thread(() -> {
                Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                Paint metaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(Math.max(2f, Math.min(width, height) * 0.004f));
                borderPaint.setColor(Color.parseColor("#00E676"));

                titlePaint.setColor(Color.parseColor("#00E676"));
                titlePaint.setTextAlign(Paint.Align.CENTER);
                titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

                subPaint.setColor(Color.parseColor("#F0F4F8"));
                subPaint.setTextAlign(Paint.Align.CENTER);
                subPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

                metaPaint.setColor(Color.parseColor("#9BA3B0"));
                metaPaint.setTextAlign(Paint.Align.CENTER);
                metaPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

                boolean firstSlateFrame = true;
                while (slateActive && isRunning.get() && !Thread.currentThread().isInterrupted()) {
                    synchronized (surfaceLock) {
                        if (!slateActive || inputSurface == null || !inputSurface.isValid()) {
                            break;
                        }
                        try {
                            Canvas canvas = inputSurface.lockCanvas(null);
                            if (canvas != null) {
                                int w = canvas.getWidth();
                                int h = canvas.getHeight();
                                float minDim = Math.min(w, h);

                                bgPaint.setColor(Color.parseColor("#0B0E14"));
                                canvas.drawRect(0, 0, w, h, bgPaint);

                                float cardW = w * 0.80f;
                                float cardH = h * 0.42f;
                                float left = (w - cardW) / 2f;
                                float top = (h - cardH) / 2f;

                                cardPaint.setColor(Color.parseColor("#141923"));
                                canvas.drawRoundRect(left, top, left + cardW, top + cardH, 24f, 24f, cardPaint);
                                canvas.drawRoundRect(left, top, left + cardW, top + cardH, 24f, 24f, borderPaint);

                                titlePaint.setTextSize(minDim * 0.048f);
                                subPaint.setTextSize(minDim * 0.036f);
                                metaPaint.setTextSize(minDim * 0.028f);

                                float centerY = h / 2f;
                                canvas.drawText("PRIVACY SHIELD ACTIVE", w / 2f, centerY - (minDim * 0.06f), titlePaint);
                                canvas.drawText("STREAM PAUSED • BE RIGHT BACK", w / 2f, centerY + (minDim * 0.01f), subPaint);

                                long uptimeSec = StreamService.getStreamUptimeMs() / 1000L;
                                String clock = String.format(Locale.US, "LIVE %02d:%02d:%02d • KR LIVE STUDIO",
                                        uptimeSec / 3600, (uptimeSec % 3600) / 60, uptimeSec % 60);
                                canvas.drawText(clock, w / 2f, centerY + (minDim * 0.08f), metaPaint);

                                inputSurface.unlockCanvasAndPost(canvas);
                                if (firstSlateFrame) {
                                    firstSlateFrame = false;
                                    requestKeyframe();
                                }
                            }
                        } catch (Exception ignored) {
                            break;
                        }
                    }
                    try {
                        Thread.sleep(100); // 10 FPS keep-alive slate
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "KimLive-SlateRenderer");
            slateThread.setDaemon(true);
            slateThread.start();
        }

        private void stopSlateRenderer() {
            slateActive = false;
            if (slateThread != null) {
                slateThread.interrupt();
                try {
                    slateThread.join(150);
                } catch (InterruptedException ignored) {}
                slateThread = null;
            }
        }

        public void stop() {
            isRunning.set(false);
            stopSlateRenderer();
            if (thread != null) {
                thread.interrupt();
            }
        }

        private void release() {
            stopSlateRenderer();
            try {
                if (virtualDisplay != null) {
                    virtualDisplay.release();
                    virtualDisplay = null;
                }
                if (inputSurface != null) {
                    inputSurface.release();
                    inputSurface = null;
                }
                if (mediaCodec != null) {
                    mediaCodec.stop();
                    mediaCodec.release();
                    mediaCodec = null;
                }
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // Audio Encoder (AudioRecord + MediaCodec AAC-LC + Internal Game Dual Mixer)
    // =========================================================================
    public static class AudioEncoder implements Runnable {
        private static final int SAMPLE_RATE = 44100;
        private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
        private static final int BITRATE = 128000;

        private final RtmpMuxerClient rtmp;
        private final MediaProjection projection;
        private final int audioMode;
        private final LocalMp4Recorder localRecorder;
        private final AtomicBoolean isRunning = new AtomicBoolean(false);
        private Thread thread;
        private AudioRecord micRecord;
        private AudioRecord internalRecord;
        private MediaCodec mediaCodec;
        private int channelCount = 2;
        private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        private long lastAudioPtsMs = 0;

        public AudioEncoder(RtmpMuxerClient rtmp, MediaProjection projection, int audioMode, LocalMp4Recorder localRecorder) {
            this.rtmp = rtmp;
            this.projection = projection;
            this.audioMode = audioMode;
            this.localRecorder = localRecorder;
        }

        public void start() {
            isRunning.set(true);
            thread = new Thread(this, "KimLive-AudioEncoder");
            thread.start();
        }

        @Override
        public void run() {
            int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AUDIO_FORMAT);
            channelConfig = AudioFormat.CHANNEL_IN_STEREO;
            channelCount = 2;

            try {
                // Initialize both mic and internal capture when available so audio modes can be hot-swapped live
                try {
                    micRecord = new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            channelConfig,
                            AUDIO_FORMAT,
                            Math.max(minBufSize * 2, 8192)
                    );
                    if (micRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        channelConfig = AudioFormat.CHANNEL_IN_MONO;
                        channelCount = 1;
                        micRecord = new AudioRecord(
                                MediaRecorder.AudioSource.MIC,
                                SAMPLE_RATE,
                                channelConfig,
                                AUDIO_FORMAT,
                                Math.max(minBufSize * 2, 8192)
                        );
                    }
                } catch (Exception e) {
                    Log.w("KimLive-AudioEncoder", "Microphone audio capture init failed: " + e.getMessage());
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && projection != null) {
                    try {
                        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                                .build();

                        internalRecord = new AudioRecord.Builder()
                                .setAudioPlaybackCaptureConfig(config)
                                .setAudioFormat(new AudioFormat.Builder()
                                        .setEncoding(AUDIO_FORMAT)
                                        .setSampleRate(SAMPLE_RATE)
                                        .setChannelMask(channelConfig)
                                        .build())
                                .setBufferSizeInBytes(Math.max(minBufSize * 2, 8192))
                                .build();
                    } catch (Exception e) {
                        Log.w("KimLive-AudioEncoder", "Internal audio capture init failed: " + e.getMessage());
                    }
                }

                if (micRecord == null && internalRecord == null) {
                    micRecord = new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            channelConfig,
                            AUDIO_FORMAT,
                            Math.max(minBufSize * 2, 8192)
                    );
                }

                MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, channelCount);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
                format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);

                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
                mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mediaCodec.start();

                if (micRecord != null && micRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    micRecord.startRecording();
                }
                if (internalRecord != null && internalRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    internalRecord.startRecording();
                }

                byte[] defaultHeader = (channelCount == 2) ? new byte[]{0x12, 0x10} : new byte[]{0x12, 0x08};
                rtmp.sendAacSequenceHeader(defaultHeader, channelCount == 2);

                byte[] pcmBuffer = new byte[2048];
                byte[] micPcm = new byte[2048];
                byte[] intPcm = new byte[2048];
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                long pts = 0;
                int bytesPerFrame = channelCount * 2;
                float duckFactor = 1.0f;

                while (isRunning.get()) {
                    int readBytes = 0;
                    int currentMode = StreamService.getAudioMode();
                    boolean isPrivacyMuted = StreamService.isPrivacyShieldActive();
                    boolean isMasterMuted = StreamService.isMicMuted() || isPrivacyMuted;
                    boolean useMic = (currentMode == AUDIO_MODE_MIC || currentMode == AUDIO_MODE_DUAL);
                    boolean useInternal = (currentMode == AUDIO_MODE_INTERNAL || currentMode == AUDIO_MODE_DUAL);

                    float micGain = (isMasterMuted || !useMic) ? 0.0f : (StreamService.getMicGainPercent() / 100.0f);
                    float baseIntGain = (isMasterMuted || !useInternal) ? 0.0f : (StreamService.getInternalGainPercent() / 100.0f);

                    if (micRecord != null && internalRecord != null) {
                        int mRead = micRecord.read(micPcm, 0, micPcm.length);
                        int targetInternalBytes = (mRead > 0) ? mRead : intPcm.length;
                        int iRead;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // Non-blocking read prevents silent games from stalling mic audio
                            iRead = internalRecord.read(intPcm, 0, targetInternalBytes, AudioRecord.READ_NON_BLOCKING);
                        } else {
                            iRead = internalRecord.read(intPcm, 0, targetInternalBytes);
                        }
                        if (iRead < 0) iRead = 0;

                        readBytes = (mRead > 0) ? mRead : iRead;
                        if (readBytes > 0) {
                            int micPeak = 0;
                            if (micGain > 0.0f && mRead > 0) {
                                for (int i = 0; i < mRead - 1; i += 2) {
                                    short sM = (short) ((micPcm[i] & 0xFF) | (micPcm[i + 1] << 8));
                                    int absM = Math.abs((int) (sM * micGain));
                                    if (absM > micPeak) micPeak = absM;
                                }
                            }

                            if (currentMode == AUDIO_MODE_DUAL && StreamService.isAutoDuckEnabled()) {
                                if (micGain > 0.0f && micPeak > 1800) {
                                    duckFactor = Math.max(0.30f, duckFactor * 0.82f);
                                } else {
                                    duckFactor = Math.min(1.0f, duckFactor + 0.04f);
                                }
                            } else {
                                duckFactor = 1.0f;
                            }

                            float effectiveIntGain = baseIntGain * duckFactor;
                            for (int i = 0; i < readBytes - 1; i += 2) {
                                short sM = (i < mRead - 1) ? (short) ((micPcm[i] & 0xFF) | (micPcm[i + 1] << 8)) : 0;
                                short sI = (i < iRead - 1) ? (short) ((intPcm[i] & 0xFF) | (intPcm[i + 1] << 8)) : 0;
                                int mixed = Math.round(sM * micGain + sI * effectiveIntGain);
                                if (mixed > 32767) mixed = 32767;
                                else if (mixed < -32768) mixed = -32768;
                                pcmBuffer[i] = (byte) (mixed & 0xFF);
                                pcmBuffer[i + 1] = (byte) ((mixed >> 8) & 0xFF);
                            }
                        }
                    } else if (internalRecord != null) {
                        int iRead;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            iRead = internalRecord.read(pcmBuffer, 0, pcmBuffer.length, AudioRecord.READ_NON_BLOCKING);
                        } else {
                            iRead = internalRecord.read(pcmBuffer, 0, pcmBuffer.length);
                        }
                        if (iRead <= 0) {
                            // Game/system audio is currently silent; emit paced silence so AAC encoder doesn't stall
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException ie) {
                                break;
                            }
                            readBytes = pcmBuffer.length;
                            for (int i = 0; i < readBytes; i++) {
                                pcmBuffer[i] = 0;
                            }
                        } else {
                            readBytes = iRead;
                            if (baseIntGain != 1.0f) {
                                for (int i = 0; i < readBytes - 1; i += 2) {
                                    short sI = (short) ((pcmBuffer[i] & 0xFF) | (pcmBuffer[i + 1] << 8));
                                    int scaled = Math.round(sI * baseIntGain);
                                    if (scaled > 32767) scaled = 32767;
                                    else if (scaled < -32768) scaled = -32768;
                                    pcmBuffer[i] = (byte) (scaled & 0xFF);
                                    pcmBuffer[i + 1] = (byte) ((scaled >> 8) & 0xFF);
                                }
                            }
                        }
                    } else if (micRecord != null) {
                        readBytes = micRecord.read(pcmBuffer, 0, pcmBuffer.length);
                        if (readBytes > 0 && micGain != 1.0f) {
                            for (int i = 0; i < readBytes - 1; i += 2) {
                                short sM = (short) ((pcmBuffer[i] & 0xFF) | (pcmBuffer[i + 1] << 8));
                                int scaled = Math.round(sM * micGain);
                                if (scaled > 32767) scaled = 32767;
                                else if (scaled < -32768) scaled = -32768;
                                pcmBuffer[i] = (byte) (scaled & 0xFF);
                                pcmBuffer[i + 1] = (byte) ((scaled >> 8) & 0xFF);
                            }
                        }
                    }

                    if (isPrivacyMuted && readBytes > 0) {
                        for (int i = 0; i < readBytes; i++) {
                            pcmBuffer[i] = 0;
                        }
                    }

                    if (readBytes > 0) {
                        int maxAmp = 0;
                        for (int i = 0; i < readBytes - 1; i += 2) {
                            short sample = (short) ((pcmBuffer[i] & 0xFF) | (pcmBuffer[i + 1] << 8));
                            int abs = Math.abs((int) sample);
                            if (abs > maxAmp) maxAmp = abs;
                        }
                        StreamService.currentAudioPeak = maxAmp;

                        int inputIndex = mediaCodec.dequeueInputBuffer(10000);
                        if (inputIndex >= 0) {
                            ByteBuffer inBuf = mediaCodec.getInputBuffer(inputIndex);
                            if (inBuf != null) {
                                inBuf.clear();
                                inBuf.put(pcmBuffer, 0, readBytes);
                                long masterStartNs = StreamService.masterStreamStartNs;
                                long samplePtsUs = (masterStartNs > 0)
                                        ? Math.max(pts, (SystemClock.elapsedRealtimeNanos() - masterStartNs) / 1000L)
                                        : pts;
                                mediaCodec.queueInputBuffer(inputIndex, 0, readBytes, samplePtsUs, 0);
                                pts = samplePtsUs + (readBytes * 1000000L) / (SAMPLE_RATE * bytesPerFrame);
                            }
                        }
                    }

                    int outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat outFmt = mediaCodec.getOutputFormat();
                        if (localRecorder != null) {
                            localRecorder.setAudioFormat(outFmt);
                        }
                        ByteBuffer csd0 = outFmt.getByteBuffer("csd-0");
                        if (csd0 != null) {
                            ByteBuffer dup = csd0.duplicate();
                            byte[] config = new byte[dup.remaining()];
                            dup.get(config);
                            rtmp.sendAacSequenceHeader(config, channelCount == 2);
                        }
                    } else {
                        while (outputIndex >= 0) {
                            ByteBuffer outBuf = mediaCodec.getOutputBuffer(outputIndex);
                            if (outBuf != null && bufferInfo.size > 0) {
                                byte[] chunk = new byte[bufferInfo.size];
                                outBuf.position(bufferInfo.offset);
                                outBuf.limit(bufferInfo.offset + bufferInfo.size);
                                outBuf.get(chunk);

                                boolean isConfig = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                                if (isConfig) {
                                    rtmp.sendAacSequenceHeader(chunk, channelCount == 2);
                                } else {
                                    long audioPtsMs = Math.max(0, bufferInfo.presentationTimeUs / 1000);
                                    if (audioPtsMs < lastAudioPtsMs) {
                                        audioPtsMs = lastAudioPtsMs + 1;
                                    }
                                    lastAudioPtsMs = audioPtsMs;
                                    rtmp.sendAudioFrame(chunk, audioPtsMs, channelCount == 2);
                                    if (localRecorder != null) {
                                        localRecorder.writeAudioSample(outBuf, bufferInfo, audioPtsMs * 1000L);
                                    }
                                }
                            }
                            mediaCodec.releaseOutputBuffer(outputIndex, false);
                            outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                release();
            }
        }

        public void stop() {
            isRunning.set(false);
            if (thread != null) {
                thread.interrupt();
            }
        }

        private void release() {
            StreamService.currentAudioPeak = 0;
            try {
                if (micRecord != null) {
                    micRecord.stop();
                    micRecord.release();
                    micRecord = null;
                }
                if (internalRecord != null) {
                    internalRecord.stop();
                    internalRecord.release();
                    internalRecord = null;
                }
                if (mediaCodec != null) {
                    mediaCodec.stop();
                    mediaCodec.release();
                    mediaCodec = null;
                }
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // Simultaneous Local MP4 Recorder (Zero-Copy Hardware Tee via MediaMuxer)
    // =========================================================================
    public static class LocalMp4Recorder {
        private final Context appContext;
        private final boolean expectAudio;
        private MediaMuxer muxer;
        private String outputPath;
        private int videoTrackIndex = -1;
        private int audioTrackIndex = -1;
        private boolean isStarted = false;
        private boolean isReleased = false;
        private int pendingVideoFrames = 0;

        public LocalMp4Recorder(Context context, boolean expectAudio) {
            this.appContext = context.getApplicationContext();
            this.expectAudio = expectAudio;
            try {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "KRLiveStudio");
                if (!dir.exists() && !dir.mkdirs()) {
                    File fallback = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
                    if (fallback != null) {
                        dir = fallback;
                    }
                }
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File outFile = new File(dir, "KRLive_" + ts + ".mp4");
                outputPath = outFile.getAbsolutePath();
                muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                Log.i("KimLive-Recorder", "Local MP4 recorder initialized: " + outputPath);
            } catch (Exception e) {
                Log.e("KimLive-Recorder", "Failed to initialize LocalMp4Recorder: " + e.getMessage());
                muxer = null;
                outputPath = null;
            }
        }

        public String getOutputPath() {
            return outputPath;
        }

        public synchronized void setVideoFormat(MediaFormat format) {
            if (muxer == null || isStarted || isReleased || videoTrackIndex != -1) return;
            try {
                videoTrackIndex = muxer.addTrack(format);
                checkStartMuxer();
            } catch (Exception e) {
                Log.w("KimLive-Recorder", "setVideoFormat error: " + e.getMessage());
            }
        }

        public synchronized void setAudioFormat(MediaFormat format) {
            if (muxer == null || isStarted || isReleased || audioTrackIndex != -1) return;
            try {
                audioTrackIndex = muxer.addTrack(format);
                checkStartMuxer();
            } catch (Exception e) {
                Log.w("KimLive-Recorder", "setAudioFormat error: " + e.getMessage());
            }
        }

        private void checkStartMuxer() {
            if (isStarted || muxer == null) return;
            if (videoTrackIndex != -1 && (!expectAudio || audioTrackIndex != -1 || pendingVideoFrames > 45)) {
                try {
                    muxer.start();
                    isStarted = true;
                    Log.i("KimLive-Recorder", "MediaMuxer started (videoTrack=" + videoTrackIndex + ", audioTrack=" + audioTrackIndex + ")");
                } catch (Exception e) {
                    Log.e("KimLive-Recorder", "MediaMuxer start failed: " + e.getMessage());
                }
            }
        }

        public synchronized void writeVideoSample(ByteBuffer codecOutBuf, MediaCodec.BufferInfo origInfo, long normalizedPtsUs) {
            if (muxer == null || isReleased || videoTrackIndex == -1) return;
            if (!isStarted) {
                pendingVideoFrames++;
                checkStartMuxer();
                if (!isStarted) return;
            }
            try {
                ByteBuffer dup = codecOutBuf.duplicate();
                dup.position(origInfo.offset);
                dup.limit(origInfo.offset + origInfo.size);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                info.set(origInfo.offset, origInfo.size, normalizedPtsUs, origInfo.flags);
                muxer.writeSampleData(videoTrackIndex, dup, info);
            } catch (Exception ignored) {}
        }

        public synchronized void writeAudioSample(ByteBuffer codecOutBuf, MediaCodec.BufferInfo origInfo, long normalizedPtsUs) {
            if (muxer == null || !isStarted || isReleased || audioTrackIndex == -1) return;
            try {
                ByteBuffer dup = codecOutBuf.duplicate();
                dup.position(origInfo.offset);
                dup.limit(origInfo.offset + origInfo.size);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                info.set(origInfo.offset, origInfo.size, normalizedPtsUs, origInfo.flags);
                muxer.writeSampleData(audioTrackIndex, dup, info);
            } catch (Exception ignored) {}
        }

        public synchronized void stopAndRelease() {
            if (isReleased) return;
            isReleased = true;
            try {
                if (muxer != null) {
                    if (isStarted) {
                        muxer.stop();
                    }
                    muxer.release();
                }
            } catch (Exception e) {
                Log.w("KimLive-Recorder", "MediaMuxer stop error: " + e.getMessage());
            } finally {
                muxer = null;
                isStarted = false;
            }
            if (outputPath != null) {
                try {
                    MediaScannerConnection.scanFile(appContext, new String[]{outputPath}, new String[]{"video/mp4"}, null);
                } catch (Exception ignored) {}
            }
        }
    }

    // =========================================================================
    // Pure Java RTMP / RTMPS Streaming Protocol Engine
    // Supports SSL & standard sockets, AMF0 Handshake, Ping/Pong & ABR
    // =========================================================================
    public static class RtmpMuxerClient implements Runnable {
        private final String ingestUrl;
        private final String streamKey;
        private final int videoWidth;
        private final int videoHeight;
        private final int videoFps;
        private final int videoBitrateKbps;

        private final BlockingQueue<RtmpPacket> packetQueue = new LinkedBlockingQueue<>(1000);
        private final AtomicBoolean isRunning = new AtomicBoolean(false);
        private final Object writeLock = new Object();

        private Socket socket;
        private OutputStream out;
        private InputStream in;
        private Thread thread;
        private Thread readerThread;

        private byte[] aacHeader;
        private byte[] avcHeader;
        private byte[] cachedSps;
        private byte[] cachedPps;
        private boolean hasSentAvcHeader = false;

        private long bytesSentWindow = 0;
        private long lastBitrateCalcMs = 0;
        private int currentAbrKbps;
        private long lastAbrAdjustMs = 0;
        private long lowQueueSinceMs = 0;

        public static class RtmpPacket {
            int csid;
            int type;
            int streamId;
            long timestamp;
            byte[] data;

            public RtmpPacket(int csid, int type, int streamId, long timestamp, byte[] data) {
                this.csid = csid;
                this.type = type;
                this.streamId = streamId;
                this.timestamp = timestamp;
                this.data = data;
            }
        }

        public RtmpMuxerClient(String ingestUrl, String streamKey, int videoWidth, int videoHeight, int videoFps, int videoBitrateKbps) {
            this.ingestUrl = ingestUrl;
            this.streamKey = streamKey;
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            this.videoFps = videoFps;
            this.videoBitrateKbps = videoBitrateKbps;
            this.currentAbrKbps = videoBitrateKbps;
        }

        public interface SyncFrameListener {
            void requestSyncFrame();
        }

        public interface BitrateAdjustListener {
            void onAdjustBitrate(int newBitrateKbps);
        }

        private SyncFrameListener syncFrameListener;
        private BitrateAdjustListener bitrateAdjustListener;

        public void setSyncFrameListener(SyncFrameListener listener) {
            this.syncFrameListener = listener;
        }

        public void setBitrateAdjustListener(BitrateAdjustListener listener) {
            this.bitrateAdjustListener = listener;
        }

        public void start() {
            isRunning.set(true);
            thread = new Thread(this, "KimLive-RtmpWorker");
            thread.start();
        }

        public synchronized void sendAvcSequenceHeader(byte[] sps, byte[] pps) {
            if (sps == null || pps == null || sps.length < 4 || pps.length < 1) return;
            cachedSps = sps;
            cachedPps = pps;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(0x17); // Keyframe, AVC
            baos.write(0x00); // AVC sequence header
            baos.write(0x00); // Composition time (3 bytes)
            baos.write(0x00);
            baos.write(0x00);

            // AVCDecoderConfigurationRecord
            baos.write(0x01); // configurationVersion
            baos.write(sps[1]); // AVCProfileIndication
            baos.write(sps[2]); // profile_compatibility
            baos.write(sps[3]); // AVCLevelIndication
            baos.write(0xFF); // lengthSizeMinusOne: 3 (4 bytes NALU length)
            baos.write(0xE1); // numOfSequenceParameterSets: 1
            baos.write((sps.length >> 8) & 0xFF);
            baos.write(sps.length & 0xFF);
            baos.write(sps, 0, sps.length);

            baos.write(0x01); // numOfPictureParameterSets: 1
            baos.write((pps.length >> 8) & 0xFF);
            baos.write(pps.length & 0xFF);
            baos.write(pps, 0, pps.length);

            avcHeader = baos.toByteArray();
            hasSentAvcHeader = true;
            packetQueue.offer(new RtmpPacket(0x06, 0x09, 1, 0, avcHeader));
        }

        public void sendVideoFrame(byte[] data, boolean isKeyFrame, long timestamp) {
            if (data == null || data.length < 4) return;
            List<byte[]> nals = extractNalUnits(data);
            if (nals.isEmpty()) return;

            boolean keyFrame = isKeyFrame;
            for (byte[] nal : nals) {
                int nalType = nal[0] & 0x1F;
                if (nalType == 5) keyFrame = true;
                if (nalType == 7) cachedSps = nal;
                if (nalType == 8) cachedPps = nal;
            }

            if (!hasSentAvcHeader && cachedSps != null && cachedPps != null) {
                sendAvcSequenceHeader(cachedSps, cachedPps);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length + 64);
            baos.write(keyFrame ? 0x17 : 0x27);
            baos.write(0x01); // AVC NALU
            baos.write(0x00); // Composition time offset (3 bytes: 0)
            baos.write(0x00);
            baos.write(0x00);

            boolean hasSlice = false;
            for (byte[] nal : nals) {
                int nalType = nal[0] & 0x1F;
                // Exclude parameter sets from AVCC sample slice payload
                if (nalType == 7 || nalType == 8) {
                    continue;
                }
                int nalLen = nal.length;
                baos.write((nalLen >> 24) & 0xFF);
                baos.write((nalLen >> 16) & 0xFF);
                baos.write((nalLen >> 8) & 0xFF);
                baos.write(nalLen & 0xFF);
                baos.write(nal, 0, nalLen);
                hasSlice = true;
            }

            if (!hasSlice) {
                return; // Buffer contained only parameter sets
            }

            if (!packetQueue.offer(new RtmpPacket(0x06, 0x09, 1, timestamp, baos.toByteArray()))) {
                StreamService.droppedFrames++;
                if (syncFrameListener != null && (StreamService.droppedFrames % 15 == 1)) {
                    syncFrameListener.requestSyncFrame();
                }
            }
        }

        public synchronized void sendAacSequenceHeader(byte[] configData, boolean isStereo) {
            if (configData == null || configData.length == 0) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(isStereo ? 0xAF : 0xAE); // 44kHz, 16-bit, stereo/mono, AAC
            baos.write(0x00); // AAC sequence header
            baos.write(configData, 0, configData.length);

            aacHeader = baos.toByteArray();
            packetQueue.offer(new RtmpPacket(0x04, 0x08, 1, 0, aacHeader));
        }

        public void sendAudioFrame(byte[] audioData, long timestamp, boolean isStereo) {
            if (audioData == null || audioData.length == 0) return;
            int offset = 0;
            int len = audioData.length;
            // Strip ADTS header if present (7 or 9 bytes)
            if (len >= 7 && (audioData[0] & 0xFF) == 0xFF && (audioData[1] & 0xF0) == 0xF0) {
                int headerLen = ((audioData[1] & 0x01) == 0) ? 9 : 7;
                if (len > headerLen) {
                    offset = headerLen;
                    len -= headerLen;
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream(len + 2);
            baos.write(isStereo ? 0xAF : 0xAE); // 44kHz, 16-bit, stereo/mono, AAC
            baos.write(0x01); // AAC raw data
            baos.write(audioData, offset, len);

            if (!packetQueue.offer(new RtmpPacket(0x04, 0x08, 1, timestamp, baos.toByteArray()))) {
                StreamService.droppedFrames++;
            }
        }

        public static List<byte[]> extractNalUnits(byte[] data) {
            List<byte[]> units = new ArrayList<>();
            if (data == null || data.length < 4) return units;
            List<Integer> starts = new ArrayList<>();
            List<Integer> lens = new ArrayList<>();
            int len = data.length;
            int i = 0;
            while (i < len - 2) {
                if (data[i] == 0 && data[i + 1] == 0) {
                    if (data[i + 2] == 1) {
                        starts.add(i + 3);
                        lens.add(3);
                        i += 3;
                        continue;
                    } else if (data[i + 2] == 0 && i < len - 3 && data[i + 3] == 1) {
                        starts.add(i + 4);
                        lens.add(4);
                        i += 4;
                        continue;
                    }
                }
                i++;
            }
            int count = starts.size();
            if (count == 0) {
                units.add(data);
                return units;
            }
            for (int idx = 0; idx < count; idx++) {
                int start = starts.get(idx);
                int end = (idx + 1 < count) ? (starts.get(idx + 1) - lens.get(idx + 1)) : len;
                int nLen = end - start;
                if (nLen > 0) {
                    byte[] nal = new byte[nLen];
                    System.arraycopy(data, start, nal, 0, nLen);
                    units.add(nal);
                }
            }
            return units;
        }

        @Override
        public void run() {
            int retryCount = 0;
            final int maxRetries = 5;

            while (isRunning.get()) {
                try {
                    StreamService.setRtmpStatus(retryCount == 0 ? "CONNECTING..." : "RECONNECTING (" + retryCount + "/" + maxRetries + ")...");
                    connectAndPublish();
                    retryCount = 0;
                    StreamService.setRtmpStatus("ONLINE (LIVE)");
                    if (syncFrameListener != null) {
                        syncFrameListener.requestSyncFrame();
                    }
                    lastBitrateCalcMs = SystemClock.elapsedRealtime();
                    bytesSentWindow = 0;

                    while (isRunning.get()) {
                        RtmpPacket packet = packetQueue.poll(200, TimeUnit.MILLISECONDS);
                        if (packet != null) {
                            writeChunk(packet.csid, packet.type, packet.streamId, packet.timestamp, packet.data);
                        }
                        evaluateTelemetryAndAbr();
                    }
                } catch (Exception e) {
                    Log.e("KimLive-RTMP", "RTMP Streaming Exception: " + e.getMessage(), e);
                    closeSockets();

                    if (!isRunning.get()) {
                        break;
                    }

                    retryCount++;
                    if (retryCount > maxRetries) {
                        Log.e("KimLive-RTMP", "Max RTMP reconnect attempts (" + maxRetries + ") reached. Stopping.");
                        StreamService.setRtmpStatus("CONNECTION FAILED");
                        break;
                    }

                    long backoffMs = Math.min(10000, 1500L * retryCount);
                    StreamService.setRtmpStatus("RETRYING IN " + (backoffMs / 1000) + "s...");
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            closeSockets();
            isRunning.set(false);
        }

        private void evaluateTelemetryAndAbr() {
            long now = SystemClock.elapsedRealtime();
            long elapsed = now - lastBitrateCalcMs;
            if (elapsed >= 1000) {
                int measuredKbps = (int) ((bytesSentWindow * 8L) / Math.max(1L, elapsed));
                StreamService.currentBitrateKbps = measuredKbps;
                bytesSentWindow = 0;
                lastBitrateCalcMs = now;
            }

            if (!StreamService.isAbrEnabled()) {
                if (currentAbrKbps != videoBitrateKbps) {
                    currentAbrKbps = videoBitrateKbps;
                    StreamService.setAbrTargetKbps(currentAbrKbps);
                    if (bitrateAdjustListener != null) {
                        bitrateAdjustListener.onAdjustBitrate(currentAbrKbps);
                    }
                }
                return;
            }

            int qSize = packetQueue.size();
            if (qSize > 80 && now - lastAbrAdjustMs >= 2000) {
                int minBitrate = Math.max(500, (videoBitrateKbps * 40) / 100);
                int nextKbps = Math.max(minBitrate, (currentAbrKbps * 80) / 100);
                if (nextKbps < currentAbrKbps) {
                    currentAbrKbps = nextKbps;
                    StreamService.setAbrTargetKbps(currentAbrKbps);
                    lastAbrAdjustMs = now;
                    lowQueueSinceMs = 0;
                    if (bitrateAdjustListener != null) {
                        bitrateAdjustListener.onAdjustBitrate(currentAbrKbps);
                    }
                    Log.i("KimLive-ABR", "Congestion detected (queue=" + qSize + "), stepping down bitrate to " + currentAbrKbps + " kbps");
                }
            } else if (qSize < 15) {
                if (lowQueueSinceMs == 0) {
                    lowQueueSinceMs = now;
                } else if (currentAbrKbps < videoBitrateKbps && now - lowQueueSinceMs >= 6000 && now - lastAbrAdjustMs >= 3000) {
                    int nextKbps = Math.min(videoBitrateKbps, currentAbrKbps + Math.max(150, videoBitrateKbps / 10));
                    currentAbrKbps = nextKbps;
                    StreamService.setAbrTargetKbps(currentAbrKbps);
                    lastAbrAdjustMs = now;
                    lowQueueSinceMs = now;
                    if (bitrateAdjustListener != null) {
                        bitrateAdjustListener.onAdjustBitrate(currentAbrKbps);
                    }
                    Log.i("KimLive-ABR", "Network stable, stepping up bitrate to " + currentAbrKbps + " kbps");
                }
            } else {
                lowQueueSinceMs = 0;
            }
        }

        private void connectAndPublish() throws Exception {
            boolean isSsl = ingestUrl.startsWith("rtmps://");
            String urlWithoutScheme = ingestUrl.replace("rtmps://", "").replace("rtmp://", "");
            String host = urlWithoutScheme;
            int port = isSsl ? 443 : 1935;

            int colonIdx = host.indexOf(':');
            int slashIdx = host.indexOf('/');
            String app = "live";

            if (slashIdx != -1) {
                app = host.substring(slashIdx + 1);
                host = host.substring(0, slashIdx);
            }

            if (colonIdx != -1) {
                port = Integer.parseInt(host.substring(colonIdx + 1));
                host = host.substring(0, colonIdx);
            }

            app = app.replaceAll("/+$", "");

            Log.i("KimLive-RTMP", "Connecting to " + host + ":" + port + " app=" + app + " (ssl=" + isSsl + ")...");
            if (isSsl) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) factory.createSocket();
                sslSocket.connect(new InetSocketAddress(host, port), 10000);
                sslSocket.setTcpNoDelay(true);
                sslSocket.setSendBufferSize(128 * 1024);
                sslSocket.startHandshake();
                socket = sslSocket;
            } else {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 10000);
                socket.setTcpNoDelay(true);
                socket.setSendBufferSize(128 * 1024);
            }
            Log.i("KimLive-RTMP", "Connected! Performing RTMP handshake...");

            out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);
            in = new BufferedInputStream(socket.getInputStream(), 64 * 1024);

            performRtmpHandshake();
            Log.i("KimLive-RTMP", "RTMP Handshake successful! Sending commands...");
            sendSetChunkSize(4096);
            sendConnectCommand(app);
            sendReleaseStream(streamKey);
            sendFCPublish(streamKey);
            sendCreateStream();
            sendPublish(streamKey, app);
            sendMetaData(videoWidth, videoHeight, videoFps, videoBitrateKbps);

            // Write sequence headers proactively if available
            if (avcHeader != null) {
                writeChunk(0x06, 0x09, 1, 0, avcHeader);
            }
            if (aacHeader != null) {
                writeChunk(0x04, 0x08, 1, 0, aacHeader);
            }

            Log.i("KimLive-RTMP", "Broadcast published! Streaming video & audio chunks...");

            startReaderThread();
        }

        private void startReaderThread() {
            readerThread = new Thread(() -> {
                byte[] buf = new byte[4096];
                long totalBytesRead = 0;
                long lastAckSentBytes = 0;
                try {
                    while (isRunning.get() && in != null) {
                        int r = in.read(buf);
                        if (r < 0) break;
                        if (r > 0) {
                            totalBytesRead += r;
                            // Send RTMP Acknowledgement (Type 3) every 250 KB received
                            if (totalBytesRead - lastAckSentBytes >= 250000) {
                                lastAckSentBytes = totalBytesRead;
                                sendAcknowledgement((int) (totalBytesRead & 0xFFFFFFFFL));
                            }
                            // Detect RTMP User Control PingRequest (Msg Type 0x04, Event Type 0x0006)
                            for (int i = 0; i <= r - 18; i++) {
                                int basicHeader = buf[i] & 0xFF;
                                // CSID 2 (Protocol Control), fmt 0 (0x02) or fmt 1 (0x42)
                                if ((basicHeader == 0x02 || basicHeader == 0x42)
                                        && (buf[i + 4] & 0xFF) == 0x00
                                        && (buf[i + 5] & 0xFF) == 0x00
                                        && (buf[i + 6] & 0xFF) == 0x06
                                        && (buf[i + 7] & 0xFF) == 0x04) {
                                    int payloadOffset = (basicHeader == 0x02) ? (i + 12) : (i + 8);
                                    if (payloadOffset + 6 <= r) {
                                        int eventType = ((buf[payloadOffset] & 0xFF) << 8) | (buf[payloadOffset + 1] & 0xFF);
                                        if (eventType == 0x0006) { // PingRequest
                                            byte[] pingData = new byte[4];
                                            System.arraycopy(buf, payloadOffset + 2, pingData, 0, 4);
                                            sendPingResponse(pingData);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }, "KimLive-RtmpReader");
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void sendAcknowledgement(int sequenceNumber) {
            synchronized (writeLock) {
                if (out == null) return;
                try {
                    out.write(0x02); // CSID 2, fmt 0
                    out.write(0x00); out.write(0x00); out.write(0x00); // Timestamp 0
                    out.write(0x00); out.write(0x00); out.write(0x04); // Length 4
                    out.write(0x03); // Message Type 3: Acknowledgement
                    out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00); // Stream ID 0
                    out.write((sequenceNumber >> 24) & 0xFF);
                    out.write((sequenceNumber >> 16) & 0xFF);
                    out.write((sequenceNumber >> 8) & 0xFF);
                    out.write(sequenceNumber & 0xFF);
                    out.flush();
                } catch (Exception ignored) {}
            }
        }

        private void sendPingResponse(byte[] fourByteTimestamp) {
            synchronized (writeLock) {
                if (out == null || fourByteTimestamp == null || fourByteTimestamp.length < 4) return;
                try {
                    out.write(0x02); // CSID 2, fmt 0
                    out.write(0x00); out.write(0x00); out.write(0x00); // Timestamp 0
                    out.write(0x00); out.write(0x00); out.write(0x06); // Length 6
                    out.write(0x04); // Message Type 4: User Control Message
                    out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00); // Stream ID 0
                    out.write(0x00); out.write(0x07); // Event Type 7: PingResponse
                    out.write(fourByteTimestamp, 0, 4);
                    out.flush();
                    Log.d("KimLive-RTMP", "Sent RTMP PingResponse (Pong)");
                } catch (Exception ignored) {}
            }
        }

        private void performRtmpHandshake() throws Exception {
            byte[] c0c1 = new byte[1537];
            c0c1[0] = 0x03; // RTMP Version 3
            new SecureRandom().nextBytes(c0c1);
            c0c1[0] = 0x03;
            // Zero timestamp
            c0c1[1] = 0; c0c1[2] = 0; c0c1[3] = 0; c0c1[4] = 0;
            synchronized (writeLock) {
                out.write(c0c1);
                out.flush();
            }

            byte[] s0s1s2 = new byte[3073];
            int read = 0;
            while (read < s0s1s2.length) {
                int r = in.read(s0s1s2, read, s0s1s2.length - read);
                if (r < 0) throw new Exception("Handshake socket closed early");
                read += r;
            }

            // Write C2
            byte[] c2 = new byte[1536];
            System.arraycopy(s0s1s2, 1, c2, 0, 1536);
            synchronized (writeLock) {
                out.write(c2);
                out.flush();
            }
        }

        private void sendSetChunkSize(int chunkSize) throws Exception {
            synchronized (writeLock) {
                out.write(0x02); // CSID 2, Type 0
                out.write(0x00); out.write(0x00); out.write(0x00); // Timestamp 0
                out.write(0x00); out.write(0x00); out.write(0x04); // Length 4
                out.write(0x01); // Message Type 1: Set Chunk Size
                out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00); // Stream ID 0
                out.write((chunkSize >> 24) & 0xFF);
                out.write((chunkSize >> 16) & 0xFF);
                out.write((chunkSize >> 8) & 0xFF);
                out.write(chunkSize & 0xFF);
                out.flush();
            }
        }

        private void sendConnectCommand(String app) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "connect");
            writeAmfNumber(amf, 1.0); // Transaction ID

            // Command Object (AMF0 Object)
            amf.write(0x03);
            writeAmfProperty(amf, "app", app);
            writeAmfProperty(amf, "flashVer", "FMLE/3.0 (compatible; FMSc/1.0)");
            writeAmfProperty(amf, "swfUrl", "");
            writeAmfProperty(amf, "tcUrl", ingestUrl);
            writeAmfProperty(amf, "fpad", false);
            writeAmfProperty(amf, "capabilities", 15.0);
            writeAmfProperty(amf, "audioCodecs", 3191.0);
            writeAmfProperty(amf, "videoCodecs", 252.0);
            writeAmfProperty(amf, "videoFunction", 1.0);
            amf.write(0x00);
            amf.write(0x00);
            amf.write(0x09); // End Object

            writeChunk(0x03, 0x14, 0, 0, amf.toByteArray());
        }

        private void sendReleaseStream(String key) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "releaseStream");
            writeAmfNumber(amf, 2.0);
            amf.write(0x05); // NULL
            writeAmfString(amf, key);
            writeChunk(0x03, 0x14, 0, 0, amf.toByteArray());
        }

        private void sendFCPublish(String key) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "FCPublish");
            writeAmfNumber(amf, 3.0);
            amf.write(0x05); // NULL
            writeAmfString(amf, key);
            writeChunk(0x03, 0x14, 0, 0, amf.toByteArray());
        }

        private void sendCreateStream() throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "createStream");
            writeAmfNumber(amf, 4.0);
            amf.write(0x05); // NULL
            writeChunk(0x03, 0x14, 0, 0, amf.toByteArray());
        }

        private void sendPublish(String key, String app) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "publish");
            writeAmfNumber(amf, 5.0);
            amf.write(0x05); // NULL
            writeAmfString(amf, key);
            writeAmfString(amf, "live");
            writeChunk(0x03, 0x14, 1, 0, amf.toByteArray());
        }

        private void sendMetaData(int width, int height, int fps, int bitrateKbps) throws Exception {
            ByteArrayOutputStream amf = new ByteArrayOutputStream();
            writeAmfString(amf, "@setDataFrame");
            writeAmfString(amf, "onMetaData");

            // ECMA Array (0x08)
            amf.write(0x08);
            // Array length = 10
            amf.write(0x00); amf.write(0x00); amf.write(0x00); amf.write(0x0A);

            writeAmfProperty(amf, "width", (double) width);
            writeAmfProperty(amf, "height", (double) height);
            writeAmfProperty(amf, "framerate", (double) fps);
            writeAmfProperty(amf, "videocodecid", 7.0); // AVC
            writeAmfProperty(amf, "videodatarate", (double) bitrateKbps);
            writeAmfProperty(amf, "audiocodecid", 10.0); // AAC
            writeAmfProperty(amf, "audiodatarate", 128.0);
            writeAmfProperty(amf, "audiosamplerate", 44100.0);
            writeAmfProperty(amf, "audiosamplesize", 16.0);
            writeAmfProperty(amf, "stereo", true);
            // End object marker: 00 00 09
            amf.write(0x00); amf.write(0x00); amf.write(0x09);

            writeChunk(0x05, 0x12, 1, 0, amf.toByteArray()); // Msg Type 0x12 = AMF0 Data
        }

        private void writeChunk(int csid, int messageType, int streamId, long timestamp, byte[] payload) throws Exception {
            synchronized (writeLock) {
                int length = payload.length;

                boolean hasExtendedTs = timestamp >= 0xFFFFFF;
                int tsField = hasExtendedTs ? 0xFFFFFF : (int) timestamp;

                // Chunk Header Type 0 (12 bytes)
                out.write((byte) (csid & 0x3F));
                out.write((byte) ((tsField >> 16) & 0xFF));
                out.write((byte) ((tsField >> 8) & 0xFF));
                out.write((byte) (tsField & 0xFF));

                out.write((byte) ((length >> 16) & 0xFF));
                out.write((byte) ((length >> 8) & 0xFF));
                out.write((byte) (length & 0xFF));

                out.write((byte) (messageType & 0xFF));

                // Stream ID (little-endian)
                out.write((byte) (streamId & 0xFF));
                out.write((byte) ((streamId >> 8) & 0xFF));
                out.write((byte) ((streamId >> 16) & 0xFF));
                out.write((byte) ((streamId >> 24) & 0xFF));

                if (hasExtendedTs) {
                    out.write((byte) ((timestamp >> 24) & 0xFF));
                    out.write((byte) ((timestamp >> 16) & 0xFF));
                    out.write((byte) ((timestamp >> 8) & 0xFF));
                    out.write((byte) (timestamp & 0xFF));
                }

                // Chunk Body with Type 3 Continuation (Chunk size = 4096)
                int offset = 0;
                int chunkSize = 4096;
                while (offset < length) {
                    int sendBytes = Math.min(chunkSize, length - offset);
                    out.write(payload, offset, sendBytes);
                    offset += sendBytes;
                    if (offset < length) {
                        out.write((byte) (0xC0 | (csid & 0x3F))); // Header Type 3
                        if (hasExtendedTs) {
                            out.write((byte) ((timestamp >> 24) & 0xFF));
                            out.write((byte) ((timestamp >> 16) & 0xFF));
                            out.write((byte) ((timestamp >> 8) & 0xFF));
                            out.write((byte) (timestamp & 0xFF));
                        }
                    }
                }
                out.flush();
                bytesSentWindow += length + 12;
            }
        }

        private void writeAmfString(ByteArrayOutputStream baos, String s) throws Exception {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            baos.write(0x02); // String type
            baos.write((bytes.length >> 8) & 0xFF);
            baos.write(bytes.length & 0xFF);
            baos.write(bytes);
        }

        private void writeAmfNumber(ByteArrayOutputStream baos, double val) throws Exception {
            baos.write(0x00); // Number type
            long bits = Double.doubleToRawLongBits(val);
            for (int i = 7; i >= 0; i--) {
                baos.write((byte) ((bits >> (i * 8)) & 0xFF));
            }
        }

        private void writeAmfProperty(ByteArrayOutputStream baos, String key, Object value) throws Exception {
            byte[] kBytes = key.getBytes(StandardCharsets.UTF_8);
            baos.write((kBytes.length >> 8) & 0xFF);
            baos.write(kBytes.length & 0xFF);
            baos.write(kBytes);

            if (value instanceof String) {
                writeAmfString(baos, (String) value);
            } else if (value instanceof Double) {
                writeAmfNumber(baos, (Double) value);
            } else if (value instanceof Boolean) {
                baos.write(0x01); // Boolean type
                baos.write(((Boolean) value) ? 0x01 : 0x00);
            }
        }

        private void closeSockets() {
            if (readerThread != null) {
                readerThread.interrupt();
                readerThread = null;
            }
            synchronized (writeLock) {
                try {
                    if (out != null) out.close();
                } catch (Exception ignored) {}
                out = null;
            }
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {}
            in = null;
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {}
            socket = null;
        }

        public void stop() {
            if (!isRunning.getAndSet(false)) return;
            if (thread != null) {
                thread.interrupt();
            }
            closeSockets();
            packetQueue.clear();
        }
    }
}