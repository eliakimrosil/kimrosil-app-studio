package com.kimrosil.pebbledrift;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Zen Sanctuary Ledger & Contemplations Activity.
 * Displays lifetime relaxation metrics and unlocked Zen proverbs with sharing.
 */
public class StatsActivity extends Activity {

    private DatabaseHelper dbHelper;
    private TextView tvStatMinutes;
    private TextView tvStatSessions;
    private TextView tvStatPebbles;
    private TextView tvStatCairns;
    private LinearLayout llAphorismsContainer;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("pebble-drift_prefs", MODE_PRIVATE);
        int themeMode = prefs.getInt("pref_theme_mode", 0); // 0=Auto, 1=Light, 2=Dark

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
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
        setContentView(R.layout.activity_stats);

        dbHelper = new DatabaseHelper(this);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnShareStats = findViewById(R.id.btnShareStats);
        tvStatMinutes = findViewById(R.id.tvStatMinutes);
        tvStatSessions = findViewById(R.id.tvStatSessions);
        tvStatPebbles = findViewById(R.id.tvStatPebbles);
        tvStatCairns = findViewById(R.id.tvStatCairns);
        llAphorismsContainer = findViewById(R.id.llAphorismsContainer);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        loadStats();

        btnShareStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareSanctuarySummary();
            }
        });
    }

    private void loadStats() {
        DatabaseHelper.LifetimeStats stats = dbHelper.getLifetimeStats();

        tvStatMinutes.setText(String.valueOf(stats.totalMinutes));
        tvStatSessions.setText(String.valueOf(stats.totalSessions));
        tvStatPebbles.setText(String.valueOf(stats.totalPebbles));
        tvStatCairns.setText(String.valueOf(stats.totalCairns));

        populateAphorisms();
    }

    private void populateAphorisms() {
        llAphorismsContainer.removeAllViews();
        List<DatabaseHelper.Aphorism> list = dbHelper.getAllAphorisms();

        for (final DatabaseHelper.Aphorism item : list) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_m3);
            card.setPadding(32, 24, 32, 24);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = 18;
            card.setLayoutParams(lp);

            TextView tvQuote = new TextView(this);
            tvQuote.setTextSize(14f);
            tvQuote.setTextColor(getColor(R.color.m3_on_surface));

            TextView tvSource = new TextView(this);
            tvSource.setTextSize(12f);
            tvSource.setTextColor(getColor(R.color.m3_primary));
            tvSource.setPadding(0, 8, 0, 0);

            if (item.isUnlocked) {
                tvQuote.setText("\"" + item.quote + "\"");
                tvSource.setText("— " + item.source + (item.isFavorite ? " ★" : ""));

                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        copyOrShareQuote(item);
                    }
                });
            } else {
                tvQuote.setText("🔒 Balanced more cairns to reveal this ancient contemplation.");
                tvQuote.setTextColor(getColor(R.color.m3_outline));
                tvSource.setText("— Hidden Contemplation");
                tvSource.setTextColor(getColor(R.color.m3_outline));
            }

            card.addView(tvQuote);
            card.addView(tvSource);
            llAphorismsContainer.addView(card);
        }
    }

    private void copyOrShareQuote(DatabaseHelper.Aphorism item) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = ClipData.newPlainText("Zen Aphorism", "\"" + item.quote + "\" — " + item.source);
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "Aphorism copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSanctuarySummary() {
        DatabaseHelper.LifetimeStats stats = dbHelper.getLifetimeStats();
        String shareText = "🌿 PebbleDrift: Zen Oasis Sanctuary\n" +
                "• Mindful Minutes: " + stats.totalMinutes + "\n" +
                "• Cairns Balanced: " + stats.totalCairns + "\n" +
                "• Pebbles Dropped: " + stats.totalPebbles + "\n" +
                "• Wisdom Unlocked: " + stats.unlockedQuotes + " aphorisms\n\n" +
                "Find peace within the quiet waters: https://apps.kimrosil.com";

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "My PebbleDrift Zen Journey");
        i.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(i, "Share Zen Journey"));
    }
}
