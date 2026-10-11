package com.kimrosil.pebbledrift;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

/**
 * Main Controller for PebbleDrift: Ambient Zen Water & Stone Garden.
 * Orchestrates multi-mode Canvas simulation, procedural AudioTrack audio,
 * tactile haptics, dynamic theming, and multi-screen navigation.
 */
public class MainActivity extends Activity {

    private PebbleZenCanvasView zenCanvasView;
    private ZenChimeSynthesizer synthesizer;
    private DatabaseHelper dbHelper;

    private Button chipRipple;
    private Button chipStone;
    private Button chipSand;

    private Button btnSoundToggle;
    private Button btnRainToggle;
    private Button btnBreatheToggle;
    private Button btnThemeToggle;
    private Button btnStats;

    private Button btnCleanSweep;
    private Button btnDropPebble;
    private TextView tvQuoteBanner;
    private TextView tvAdNotice;

    private long sessionStartTime = 0;
    private int sessionPebbles = 0;
    private int sessionCairns = 0;
    private int themeMode = 0; // 0=Auto, 1=Light, 2=Dark
    private final Random random = new Random();

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("pebble-drift_prefs", MODE_PRIVATE);
        int mode = prefs.getInt("pref_theme_mode", 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            if (systemNight == 2) {
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

        sessionStartTime = System.currentTimeMillis();
        dbHelper = new DatabaseHelper(this);
        synthesizer = new ZenChimeSynthesizer();

        SharedPreferences prefs = getSharedPreferences("pebble-drift_prefs", MODE_PRIVATE);
        themeMode = prefs.getInt("pref_theme_mode", 0);

        initViews();
        setupListeners();
        setupStatusBar();
        cycleAphorismBanner();
    }

    private void initViews() {
        zenCanvasView = findViewById(R.id.zenCanvasView);
        zenCanvasView.setSynthesizer(synthesizer);

        chipRipple = findViewById(R.id.chipRipple);
        chipStone = findViewById(R.id.chipStone);
        chipSand = findViewById(R.id.chipSand);

        btnSoundToggle = findViewById(R.id.btnSoundToggle);
        btnRainToggle = findViewById(R.id.btnRainToggle);
        btnBreatheToggle = findViewById(R.id.btnBreatheToggle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnStats = findViewById(R.id.btnStats);

        btnCleanSweep = findViewById(R.id.btnCleanSweep);
        btnDropPebble = findViewById(R.id.btnDropPebble);
        tvQuoteBanner = findViewById(R.id.tvQuoteBanner);
        tvAdNotice = findViewById(R.id.tvAdNotice);

        // Update Theme Button Icon
        updateThemeButtonLabel();
    }

    private void setupStatusBar() {
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        zenCanvasView.setNightMode(isNight);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (!isNight) {
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

    private void setupListeners() {
        // Mode Switcher Chips
        chipRipple.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectMode(PebbleZenCanvasView.ZenMode.RIPPLE_OASIS);
            }
        });

        chipStone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectMode(PebbleZenCanvasView.ZenMode.STONE_STACK);
            }
        });

        chipSand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectMode(PebbleZenCanvasView.ZenMode.SAND_GARDEN);
            }
        });

        // Sound Toggle
        btnSoundToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synthesizer.toggleMute();
                btnSoundToggle.setText(synthesizer.isMuted() ? "🔕" : "🔔");
                Toast.makeText(MainActivity.this, synthesizer.isMuted() ? "Sound Muted" : "Chimes Enabled", Toast.LENGTH_SHORT).show();
            }
        });

        // Rain Toggle
        btnRainToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zenCanvasView.toggleRain();
                btnRainToggle.setAlpha(zenCanvasView.isRainEnabled() ? 1.0f : 0.5f);
                Toast.makeText(MainActivity.this, zenCanvasView.isRainEnabled() ? "Gentle Rain Begun" : "Rain Paused", Toast.LENGTH_SHORT).show();
            }
        });

        // Breathing Pacer Toggle
        btnBreatheToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zenCanvasView.toggleBreathing();
                btnBreatheToggle.setAlpha(zenCanvasView.isBreathingEnabled() ? 1.0f : 0.5f);
                Toast.makeText(MainActivity.this, zenCanvasView.isBreathingEnabled() ? "Breathing Guide Active" : "Breathing Guide Hidden", Toast.LENGTH_SHORT).show();
            }
        });

        // Theme Toggle (Auto -> Light -> Dark -> Auto)
        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                themeMode = (themeMode + 1) % 3;
                SharedPreferences prefs = getSharedPreferences("pebble-drift_prefs", MODE_PRIVATE);
                prefs.edit().putInt("pref_theme_mode", themeMode).apply();
                recreate();
            }
        });

        // Stats Sanctuary Screen
        btnStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, StatsActivity.class);
                startActivity(i);
            }
        });

        // Clean Sweep
        btnCleanSweep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zenCanvasView.triggerCleanSweep();
                Toast.makeText(MainActivity.this, "Oasis Cleansed", Toast.LENGTH_SHORT).show();
            }
        });

        // Drop Pebble
        btnDropPebble.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float cx = zenCanvasView.getWidth() * 0.5f + (random.nextFloat() * 100f - 50f);
                float cy = zenCanvasView.getHeight() * 0.45f + (random.nextFloat() * 100f - 50f);
                zenCanvasView.dropPebbleAt(cx, cy);
            }
        });

        // Zen Canvas Listener
        zenCanvasView.setZenListener(new PebbleZenCanvasView.ZenListener() {
            @Override
            public void onPebbleDropped(int totalPebbles) {
                sessionPebbles++;
            }

            @Override
            public void onCairnBalanced(int stackHeight) {
                sessionCairns++;
                tvQuoteBanner.setText("Balanced " + stackHeight + " Stones: Finding Stillness in Motion.");
            }

            @Override
            public void onCairnToppled() {
                tvQuoteBanner.setText("“To fall is simply to return to the source. Begin anew.”");
            }

            @Override
            public void onZenAphorismUnlocked() {
                DatabaseHelper.Aphorism unlocked = dbHelper.unlockNextAphorism();
                if (unlocked != null) {
                    tvQuoteBanner.setText("“" + unlocked.quote + "” — " + unlocked.source);
                    Toast.makeText(MainActivity.this, "New Zen Wisdom Unlocked!", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Pro Notice Link
        tvAdNotice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com"));
                    startActivity(browserIntent);
                } catch (Exception ignored) {}
            }
        });
    }

    private void selectMode(PebbleZenCanvasView.ZenMode mode) {
        zenCanvasView.setMode(mode);

        chipRipple.setBackgroundResource(mode == PebbleZenCanvasView.ZenMode.RIPPLE_OASIS ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipRipple.setTextColor(getColor(mode == PebbleZenCanvasView.ZenMode.RIPPLE_OASIS ? R.color.m3_on_primary : R.color.m3_on_surface_variant));

        chipStone.setBackgroundResource(mode == PebbleZenCanvasView.ZenMode.STONE_STACK ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipStone.setTextColor(getColor(mode == PebbleZenCanvasView.ZenMode.STONE_STACK ? R.color.m3_on_primary : R.color.m3_on_surface_variant));

        chipSand.setBackgroundResource(mode == PebbleZenCanvasView.ZenMode.SAND_GARDEN ? R.drawable.chip_active : R.drawable.chip_inactive);
        chipSand.setTextColor(getColor(mode == PebbleZenCanvasView.ZenMode.SAND_GARDEN ? R.color.m3_on_primary : R.color.m3_on_surface_variant));

        if (mode == PebbleZenCanvasView.ZenMode.STONE_STACK) {
            btnCleanSweep.setText("↺ Reset Cairn");
            btnCleanSweep.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    zenCanvasView.resetCairnStack(zenCanvasView.getWidth(), zenCanvasView.getHeight());
                    zenCanvasView.invalidate();
                }
            });
        } else {
            btnCleanSweep.setText("✨ Clean Sweep");
            btnCleanSweep.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    zenCanvasView.triggerCleanSweep();
                }
            });
        }

        if (synthesizer != null) {
            synthesizer.playRandomChime();
        }
    }

    private void updateThemeButtonLabel() {
        if (themeMode == 1) {
            btnThemeToggle.setText("☀️");
        } else if (themeMode == 2) {
            btnThemeToggle.setText("🌙");
        } else {
            btnThemeToggle.setText("🌓");
        }
    }

    private void cycleAphorismBanner() {
        DatabaseHelper.LifetimeStats stats = dbHelper.getLifetimeStats();
        if (stats.unlockedQuotes > 0) {
            java.util.List<DatabaseHelper.Aphorism> list = dbHelper.getAllAphorisms();
            if (!list.isEmpty()) {
                DatabaseHelper.Aphorism a = list.get(random.nextInt(Math.min(list.size(), stats.unlockedQuotes)));
                tvQuoteBanner.setText("“" + a.quote + "” — " + a.source);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_SPACE:
                case KeyEvent.KEYCODE_D:
                    btnDropPebble.performClick();
                    return true;
                case KeyEvent.KEYCODE_1:
                    selectMode(PebbleZenCanvasView.ZenMode.RIPPLE_OASIS);
                    return true;
                case KeyEvent.KEYCODE_2:
                    selectMode(PebbleZenCanvasView.ZenMode.STONE_STACK);
                    return true;
                case KeyEvent.KEYCODE_3:
                    selectMode(PebbleZenCanvasView.ZenMode.SAND_GARDEN);
                    return true;
                case KeyEvent.KEYCODE_R:
                    btnCleanSweep.performClick();
                    return true;
                case KeyEvent.KEYCODE_S:
                    btnSoundToggle.performClick();
                    return true;
                case KeyEvent.KEYCODE_B:
                    btnBreatheToggle.performClick();
                    return true;
                case KeyEvent.KEYCODE_T:
                    btnThemeToggle.performClick();
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        zenCanvasView.setNightMode(isNight);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Record Mindful Session in SQLite
        long duration = (System.currentTimeMillis() - sessionStartTime) / 1000;
        if (duration > 3) {
            dbHelper.logSession(duration, zenCanvasView.getMode().name(), sessionPebbles, sessionCairns);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (synthesizer != null) {
            synthesizer.release();
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
