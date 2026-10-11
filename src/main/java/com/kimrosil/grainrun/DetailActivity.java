package com.kimrosil.grainrun;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class DetailActivity extends Activity {

    public static final String EXTRA_ID = "EXTRA_ID";
    public static final String EXTRA_SPECIES_NAME = "EXTRA_SPECIES_NAME";
    public static final String EXTRA_JANKA = "EXTRA_JANKA";
    public static final String EXTRA_CUTTING_ANGLE = "EXTRA_CUTTING_ANGLE";
    public static final String EXTRA_TOOL_TYPE = "EXTRA_TOOL_TYPE";

    private SharedPreferences mPrefs;
    private DatabaseHelper mDbHelper;

    private long mSessionId = -1;
    private String mSpecies = "Black Walnut";
    private int mJanka = 1010;
    private int mCuttingAngle = 45;
    private int mToolType = 0;

    private EditText etSpeciesName;
    private EditText etJankaInput;
    private SeekBar sbDetailCuttingAngle;
    private TextView tvDetailAngleValue;
    private TextView tvCalculatedGuidance;
    private Button btnSaveDetail;
    private Button btnExportShare;
    private Button btnBack;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = sp.getInt(MainActivity.KEY_THEME_MODE, 0);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());

        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNight = 0;
            try {
                systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {
            }
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
        setContentView(R.layout.activity_detail);

        mPrefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        mDbHelper = new DatabaseHelper(this);

        etSpeciesName = findViewById(R.id.etSpeciesName);
        etJankaInput = findViewById(R.id.etJankaInput);
        sbDetailCuttingAngle = findViewById(R.id.sbDetailCuttingAngle);
        tvDetailAngleValue = findViewById(R.id.tvDetailAngleValue);
        tvCalculatedGuidance = findViewById(R.id.tvCalculatedGuidance);
        btnSaveDetail = findViewById(R.id.btnSaveDetail);
        btnExportShare = findViewById(R.id.btnExportShare);
        btnBack = findViewById(R.id.btnBack);

        Intent intent = getIntent();
        if (intent != null) {
            mSessionId = intent.getLongExtra(EXTRA_ID, -1);
            if (intent.hasExtra(EXTRA_SPECIES_NAME)) {
                mSpecies = intent.getStringExtra(EXTRA_SPECIES_NAME);
            }
            mJanka = intent.getIntExtra(EXTRA_JANKA, 1010);
            mCuttingAngle = intent.getIntExtra(EXTRA_CUTTING_ANGLE, 45);
            mToolType = intent.getIntExtra(EXTRA_TOOL_TYPE, 0);
        }

        etSpeciesName.setText(mSpecies);
        etJankaInput.setText(String.valueOf(mJanka));
        sbDetailCuttingAngle.setMax(75);
        sbDetailCuttingAngle.setProgress(Math.max(30, mCuttingAngle));
        tvDetailAngleValue.setText(mCuttingAngle + "° Bed / Hone");

        sbDetailCuttingAngle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCuttingAngle = Math.max(30, progress);
                tvDetailAngleValue.setText(mCuttingAngle + "° Bed / Hone");
                updateGuidanceText();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        updateGuidanceText();

        btnBack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });

        btnSaveDetail.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveChanges();
        });

        btnExportShare.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportPlaningSpecs();
        });
    }

    private void updateGuidanceText() {
        String toolStr = (mToolType == 0) ? "Hand Plane" : (mToolType == 1 ? "Surface Planer" : "Router");
        StringBuilder sb = new StringBuilder();
        sb.append("CUTTER MECHANICS & FIBER ANALYSIS:\n");
        sb.append("• Tool in Use: ").append(toolStr).append("\n");
        sb.append("• Cutting Bed: ").append(mCuttingAngle).append("°\n");
        sb.append("• Janka Hardness: ").append(mJanka).append(" lbf\n\n");

        if (mCuttingAngle >= 55) {
            sb.append("RECOMMENDATION (York/Middle Pitch):\nHigh cutting angle creates scraping action that prevents deep tearout in curly, bird's-eye, and burl lumber. Expect higher pushing resistance.\n");
        } else if (mCuttingAngle >= 48) {
            sb.append("RECOMMENDATION (Cabinet Pitch):\nIdeal balance for moderate interlocked timber (White Oak, Hard Maple). Reduces cathedral tearout significantly.\n");
        } else {
            sb.append("RECOMMENDATION (Standard Pitch 45°):\nOptimum ease of push. For severe wild grain, skew sole 20° to reduce effective cutting wedge angle and slice diagonally.\n");
        }

        tvCalculatedGuidance.setText(sb.toString());
    }

    private void saveChanges() {
        String name = etSpeciesName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Species name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        int jankaVal = mJanka;
        try {
            jankaVal = Integer.parseInt(etJankaInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid Janka numeric input. Using previous value.", Toast.LENGTH_SHORT).show();
        }

        mSpecies = name;
        mJanka = jankaVal;

        String toolStr = (mToolType == 0) ? "Hand Plane" : (mToolType == 1 ? "Surface Planer" : "Router");
        if (mSessionId > 0) {
            mDbHelper.updateSession(mSessionId, mSpecies, mJanka, mCuttingAngle, toolStr, 30, tvCalculatedGuidance.getText().toString());
            Toast.makeText(this, "Session updated successfully", Toast.LENGTH_SHORT).show();
        } else {
            mSessionId = mDbHelper.insertSession(mSpecies, mJanka, mCuttingAngle, toolStr, 30, tvCalculatedGuidance.getText().toString());
            Toast.makeText(this, "New setup saved to database", Toast.LENGTH_SHORT).show();
        }
        updateGuidanceText();
    }

    private void exportPlaningSpecs() {
        String exportText = "=== GRAINRUN WOOD PLANING SPECIFICATION ===\n" +
                "Species: " + etSpeciesName.getText().toString() + "\n" +
                "Janka Hardness: " + etJankaInput.getText().toString() + " lbf\n" +
                "Cutter Angle: " + mCuttingAngle + "°\n" +
                "Planing Direction: Plane downhill with cathedral apex forward\n" +
                "Workshop Notes:\n" + tvCalculatedGuidance.getText().toString() + "\n" +
                "Generated via GrainRun Offline Workshop Aide";

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, exportText);
        sendIntent.putExtra(Intent.EXTRA_TITLE, "GrainRun Planing Direction Spec");
        sendIntent.setType("text/plain");

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Planing Spec", exportText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Spec copied to clipboard & opening share sheet", Toast.LENGTH_SHORT).show();
        }

        startActivity(Intent.createChooser(sendIntent, "Export Planing Direction Specs"));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
