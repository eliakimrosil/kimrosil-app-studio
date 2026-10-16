package com.kimrosil.flaregage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.widget.Button;

public class DetailActivity extends Activity {

    private int mThemeMode = 0; // 0=Auto, 1=Light, 2=Dark

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("flaregage_prefs", Context.MODE_PRIVATE);
        mThemeMode = prefs.getInt("pref_theme_mode", 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mThemeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (mThemeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (mThemeMode == 1) {
            setTheme(R.style.AppTheme_Light);
        } else if (mThemeMode == 2) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        Button btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                finish();
            });
        }

        Button btnShareGuide = findViewById(R.id.btnShareGuide);
        if (btnShareGuide != null) {
            btnShareGuide.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                shareGuideText();
            });
        }
    }

    private void shareGuideText() {
        String guide = "=== FlareGage: Flaring Field Guide ===\n\n"
                + "1. STANDARDS:\n"
                + "• 45° SAE Single: Mini-split & HVAC linesets, refrigeration flare brass.\n"
                + "• 45° SAE Double: Automotive hydraulic brake lines (SAE J533).\n"
                + "• 37° AN / JIC: Aviation, motorsport & high-pressure hydraulics.\n"
                + "• DIN / ISO Bubble: European automotive brake lines.\n\n"
                + "2. FIVE-STEP FIELD PROTOCOL:\n"
                + "1. Square cut with roller cutter.\n"
                + "2. Clean deburr inner and outer lip with tube pointing downward.\n"
                + "3. Slide flare nut first!\n"
                + "4. Match stickout gauge depth.\n"
                + "5. Apply 1 drop of oil to flare cone before spinning.\n\n"
                + "3. FFT (TURNS-FROM-FINGER-TIGHT):\n"
                + "Hand-tight until seated, then 1 hex flat (1/6 turn) past finger-tight for 3/16\"-3/8\" lines.\n"
                + "Never over-torque.\n\n"
                + "Calculated offline via FlareGage.";

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, guide);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "Share Flaring Guide");
        startActivity(shareIntent);
    }
}
