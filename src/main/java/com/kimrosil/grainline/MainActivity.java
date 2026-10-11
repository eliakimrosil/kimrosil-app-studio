package com.kimrosil.grainline;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    // Header & HUD Controls
    private TextView tvAppTitle;
    private Button btnThemeToggle;
    private Button btnUnitToggle;

    // Hero Readout Card
    private LinearLayout cardHeroHUD;
    private TextView tvHeroYardage;
    private TextView tvHeroMeters;
    private TextView tvHeroCutEfficiency;
    private TextView tvHeroOffcutWaste;
    private TextView tvHeroLayoutPlan;

    // Control Deck Inputs
    private EditText etProjectName;
    private EditText etFabricWidth;
    private EditText etPieceWidth;
    private EditText etPieceLength;
    private EditText etPieceCount;
    private EditText etSeamAllowance;
    private EditText etShrinkagePct;
    private Switch swAllowRotation;

    // Stepper & Quick Action Buttons
    private Button btnCountMinus;
    private Button btnCountPlus;
    private Button btnPreset44;
    private Button btnPreset54;
    private Button btnPreset60;
    private Button btnPreset108;
    private Button btnCalculate;
    private Button btnReset;
    private Button btnSaveSession;
    private Button btnShareResult;

    // State & History Containers
    private LinearLayout llStateActiveIndicator;
    private TextView tvLiveTelemetry;
    private LinearLayout llEmptyState;
    private LinearLayout llRecentSessionsList;
    private ScrollView svMainScroll;

    // Monetization Footer
    private LinearLayout llAdContainer;
    private TextView tvAdNotice;

    // Dependencies
    private PresetManager mPresetManager;
    private DatabaseHelper mDbHelper;

    // Runtime state cache
    private double mLastCalculatedYards = 0.0;
    private double mLastCalculatedMeters = 0.0;
    private double mLastCalculatedOffcut = 0.0;
    private boolean mHasActiveResult = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("grainline_prefs", MODE_PRIVATE);
        int mode = prefs.getInt("pref_theme_mode", 0); // 0=Auto, 1=Light, 2=Dark
        int nightMode;
        if (mode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            // Auto: inspect system secure setting
            int sysNight = 0;
            try {
                sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {
            }
            nightMode = (sysNight == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPresetManager = new PresetManager(this);
        mDbHelper = DatabaseHelper.getInstance(this);

        initViews();
        setupLightStatusBar();
        setupThemeToggleButtonText();
        setupUnitToggleButtonText();
        setupClickListeners();
        initMonetization();
        loadLastPresetDefaults();
        refreshRecentSessionsList();
    }

    private void initViews() {
        svMainScroll = findViewById(R.id.svMainScroll);
        tvAppTitle = findViewById(R.id.tvAppTitle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);

        cardHeroHUD = findViewById(R.id.cardHeroHUD);
        tvHeroYardage = findViewById(R.id.tvHeroYardage);
        tvHeroMeters = findViewById(R.id.tvHeroMeters);
        tvHeroCutEfficiency = findViewById(R.id.tvHeroCutEfficiency);
        tvHeroOffcutWaste = findViewById(R.id.tvHeroOffcutWaste);
        tvHeroLayoutPlan = findViewById(R.id.tvHeroLayoutPlan);

        etProjectName = findViewById(R.id.etProjectName);
        etFabricWidth = findViewById(R.id.etFabricWidth);
        etPieceWidth = findViewById(R.id.etPieceWidth);
        etPieceLength = findViewById(R.id.etPieceLength);
        etPieceCount = findViewById(R.id.etPieceCount);
        etSeamAllowance = findViewById(R.id.etSeamAllowance);
        etShrinkagePct = findViewById(R.id.etShrinkagePct);
        swAllowRotation = findViewById(R.id.swAllowRotation);

        btnCountMinus = findViewById(R.id.btnCountMinus);
        btnCountPlus = findViewById(R.id.btnCountPlus);
        btnPreset44 = findViewById(R.id.btnPreset44);
        btnPreset54 = findViewById(R.id.btnPreset54);
        btnPreset60 = findViewById(R.id.btnPreset60);
        btnPreset108 = findViewById(R.id.btnPreset108);

        btnCalculate = findViewById(R.id.btnCalculate);
        btnReset = findViewById(R.id.btnReset);
        btnSaveSession = findViewById(R.id.btnSaveSession);
        btnShareResult = findViewById(R.id.btnShareResult);

        llStateActiveIndicator = findViewById(R.id.llStateActiveIndicator);
        tvLiveTelemetry = findViewById(R.id.tvLiveTelemetry);
        llEmptyState = findViewById(R.id.llEmptyState);
        llRecentSessionsList = findViewById(R.id.llRecentSessionsList);

        llAdContainer = findViewById(R.id.llAdContainer);
        tvAdNotice = findViewById(R.id.tvAdNotice);
    }

    private void setupLightStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
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

    private void setupThemeToggleButtonText() {
        int mode = mPresetManager.getThemeMode();
        if (mode == 1) {
            btnThemeToggle.setText("Light");
        } else if (mode == 2) {
            btnThemeToggle.setText("Dark");
        } else {
            btnThemeToggle.setText("Auto");
        }
    }

    private void setupUnitToggleButtonText() {
        boolean isMetric = mPresetManager.isMetric();
        btnUnitToggle.setText(isMetric ? "Metric (cm/m)" : "Imperial (in/yd)");
    }

    private void cycleThemeMode() {
        int current = mPresetManager.getThemeMode();
        int next = (current + 1) % 3;
        mPresetManager.setThemeMode(next);
        recreate();
    }

    private void toggleUnitSystem() {
        boolean isMetric = !mPresetManager.isMetric();
        mPresetManager.setMetric(isMetric);
        setupUnitToggleButtonText();
        Toast.makeText(this, isMetric ? "Switched to Metric (cm & meters)" : "Switched to Imperial (inches & yards)", Toast.LENGTH_SHORT).show();
        if (mHasActiveResult) {
            calculateFabricRequirements(false);
        }
    }

    private void loadLastPresetDefaults() {
        double width = mPresetManager.getLastBoltWidth(44.0);
        double shrinkage = mPresetManager.getLastShrinkage(3.0);
        double seam = mPresetManager.getLastSeam(0.25);

        etFabricWidth.setText(String.format(Locale.US, "%.1f", width));
        etShrinkagePct.setText(String.format(Locale.US, "%.1f", shrinkage));
        etSeamAllowance.setText(String.format(Locale.US, "%.2f", seam));
        etPieceCount.setText("4");
        etPieceWidth.setText("12.0");
        etPieceLength.setText("12.0");
    }

    private void setupClickListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnUnitToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleUnitSystem();
        });

        btnPreset44.setOnClickListener(v -> setFabricWidthPreset(44.0, v));
        btnPreset54.setOnClickListener(v -> setFabricWidthPreset(54.0, v));
        btnPreset60.setOnClickListener(v -> setFabricWidthPreset(60.0, v));
        btnPreset108.setOnClickListener(v -> setFabricWidthPreset(108.0, v));

        btnCountMinus.setOnClickListener(v -> adjustPieceCount(-1, v));
        btnCountPlus.setOnClickListener(v -> adjustPieceCount(1, v));

        btnCalculate.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            calculateFabricRequirements(true);
        });

        btnReset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            resetInputs();
        });

        btnSaveSession.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveCurrentSession();
        });

        btnShareResult.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareCurrentCutPlan();
        });
    }

    private void setFabricWidthPreset(double width, View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        etFabricWidth.setText(String.format(Locale.US, "%.1f", width));
        mPresetManager.saveLastBoltWidth(width);
        Toast.makeText(this, "Standard bolt width: " + width + "\"", Toast.LENGTH_SHORT).show();
    }

    private void adjustPieceCount(int delta, View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        int current = 1;
        try {
            String txt = etPieceCount.getText().toString().trim();
            if (!TextUtils.isEmpty(txt)) {
                current = Integer.parseInt(txt);
            }
        } catch (NumberFormatException ignored) {
            current = 1;
        }
        current = Math.max(1, current + delta);
        etPieceCount.setText(String.valueOf(current));
    }

    /**
     * Core cutting counter and bolt packing algorithm.
     * Calculates required raw length taking into account seam allowances, shrinkage,
     * piece orientation / 2D rotation optimization, and calculates scrap offcut percentage.
     */
    private void calculateFabricRequirements(boolean showToast) {
        // Defensive input parsing
        double fabricWidth;
        double pieceW;
        double pieceL;
        int count;
        double seam;
        double shrinkage;

        try {
            String sFw = etFabricWidth.getText().toString().trim();
            fabricWidth = TextUtils.isEmpty(sFw) ? 44.0 : Double.parseDouble(sFw);
            if (fabricWidth <= 0) fabricWidth = 44.0;
        } catch (NumberFormatException e) {
            fabricWidth = 44.0;
            etFabricWidth.setText("44.0");
        }

        try {
            String sPw = etPieceWidth.getText().toString().trim();
            pieceW = TextUtils.isEmpty(sPw) ? 10.0 : Double.parseDouble(sPw);
            if (pieceW <= 0) pieceW = 10.0;
        } catch (NumberFormatException e) {
            pieceW = 10.0;
            etPieceWidth.setText("10.0");
        }

        try {
            String sPl = etPieceLength.getText().toString().trim();
            pieceL = TextUtils.isEmpty(sPl) ? 10.0 : Double.parseDouble(sPl);
            if (pieceL <= 0) pieceL = 10.0;
        } catch (NumberFormatException e) {
            pieceL = 10.0;
            etPieceLength.setText("10.0");
        }

        try {
            String sCnt = etPieceCount.getText().toString().trim();
            count = TextUtils.isEmpty(sCnt) ? 1 : Integer.parseInt(sCnt);
            if (count <= 0) count = 1;
        } catch (NumberFormatException e) {
            count = 1;
            etPieceCount.setText("1");
        }

        try {
            String sSeam = etSeamAllowance.getText().toString().trim();
            seam = TextUtils.isEmpty(sSeam) ? 0.25 : Double.parseDouble(sSeam);
            if (seam < 0) seam = 0.0;
        } catch (NumberFormatException e) {
            seam = 0.25;
            etSeamAllowance.setText("0.25");
        }

        try {
            String sShrink = etShrinkagePct.getText().toString().trim();
            shrinkage = TextUtils.isEmpty(sShrink) ? 3.0 : Double.parseDouble(sShrink);
            if (shrinkage < 0) shrinkage = 0.0;
            if (shrinkage > 50) shrinkage = 50.0;
        } catch (NumberFormatException e) {
            shrinkage = 3.0;
            etShrinkagePct.setText("3.0");
        }

        boolean allowRotation = swAllowRotation.isChecked();

        // Save preferences
        mPresetManager.saveLastBoltWidth(fabricWidth);
        mPresetManager.saveLastShrinkage(shrinkage);
        mPresetManager.saveLastSeam(seam);

        // Include 2x seam allowance on cut dimensions
        double cutPieceW = pieceW + (2.0 * seam);
        double cutPieceL = pieceL + (2.0 * seam);

        // Packing Option A: Normal orientation (cutPieceW across fabric width)
        int piecesAcrossA = (int) Math.floor(fabricWidth / cutPieceW);
        double requiredLengthA = 0.0;
        if (piecesAcrossA > 0) {
            int rowsA = (int) Math.ceil((double) count / piecesAcrossA);
            requiredLengthA = rowsA * cutPieceL;
        } else {
            requiredLengthA = Double.MAX_VALUE;
        }

        // Packing Option B: Rotated 90 degrees (cutPieceL across fabric width)
        double requiredLengthB = Double.MAX_VALUE;
        int piecesAcrossB = 0;
        if (allowRotation) {
            piecesAcrossB = (int) Math.floor(fabricWidth / cutPieceL);
            if (piecesAcrossB > 0) {
                int rowsB = (int) Math.ceil((double) count / piecesAcrossB);
                requiredLengthB = rowsB * cutPieceW;
            }
        }

        double optimalLengthInches;
        int chosenAcross;
        String orientationNotice;

        if (requiredLengthB < requiredLengthA && piecesAcrossB > 0) {
            optimalLengthInches = requiredLengthB;
            chosenAcross = piecesAcrossB;
            orientationNotice = "Rotated 90° (" + chosenAcross + " pieces / width row)";
        } else if (piecesAcrossA > 0) {
            optimalLengthInches = requiredLengthA;
            chosenAcross = piecesAcrossA;
            orientationNotice = "Straight grain (" + chosenAcross + " pieces / width row)";
        } else {
            // Cut piece exceeds bolt width
            Toast.makeText(this, "Cut dimension exceeds bolt width (" + fabricWidth + "\")", Toast.LENGTH_LONG).show();
            return;
        }

        // Apply shrinkage buffer: requiredRaw = optimalLength / (1 - shrinkage%)
        double shrinkageFactor = 1.0 - (shrinkage / 100.0);
        if (shrinkageFactor <= 0.01) shrinkageFactor = 0.97;
        double finalLinearInches = optimalLengthInches / shrinkageFactor;

        // Convert to Yards and Meters
        mLastCalculatedYards = finalLinearInches / 36.0;
        mLastCalculatedMeters = finalLinearInches * 0.0254;

        // Compute Area Efficiency and Offcut Waste Percentage
        double totalBoltAreaSqInches = fabricWidth * optimalLengthInches;
        double netPieceAreaSqInches = (pieceW * pieceL) * count;
        double cutEfficiency = 0.0;
        if (totalBoltAreaSqInches > 0) {
            cutEfficiency = (netPieceAreaSqInches / totalBoltAreaSqInches) * 100.0;
            if (cutEfficiency > 100.0) cutEfficiency = 100.0;
        }
        mLastCalculatedOffcut = Math.max(0.0, 100.0 - cutEfficiency);

        // Update Hero HUD Readouts
        tvHeroYardage.setText(String.format(Locale.US, "%.2f yds", mLastCalculatedYards));
        tvHeroMeters.setText(String.format(Locale.US, "%.2f m", mLastCalculatedMeters));
        tvHeroCutEfficiency.setText(String.format(Locale.US, "%.1f%%", cutEfficiency));
        tvHeroOffcutWaste.setText(String.format(Locale.US, "%.1f%% scrap", mLastCalculatedOffcut));
        tvHeroLayoutPlan.setText(orientationNotice + " • " + count + " total cuts");

        // Update Live State Telemetry
        llStateActiveIndicator.setVisibility(View.VISIBLE);
        tvLiveTelemetry.setText(String.format(Locale.US, "Verified layout for %d pieces on %.1f\" bolt | %.2f linear in.", count, fabricWidth, finalLinearInches));
        mHasActiveResult = true;

        if (showToast) {
            Toast.makeText(this, String.format(Locale.US, "Calculated: %.2f yds (%.2f m)", mLastCalculatedYards, mLastCalculatedMeters), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCurrentSession() {
        if (!mHasActiveResult || mLastCalculatedYards <= 0.0) {
            calculateFabricRequirements(false);
            if (!mHasActiveResult) {
                Toast.makeText(this, "Please configure piece parameters first", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String name = etProjectName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            name = "Project #" + (mDbHelper.getAllSessions().size() + 1);
        }

        DatabaseHelper.SessionRecord rec = new DatabaseHelper.SessionRecord();
        rec.projectName = name;
        rec.fabricType = "Standard Woven";
        try {
            rec.fabricWidth = Double.parseDouble(etFabricWidth.getText().toString().trim());
            rec.pieceWidth = Double.parseDouble(etPieceWidth.getText().toString().trim());
            rec.pieceLength = Double.parseDouble(etPieceLength.getText().toString().trim());
            rec.pieceCount = Integer.parseInt(etPieceCount.getText().toString().trim());
            rec.seamAllowance = Double.parseDouble(etSeamAllowance.getText().toString().trim());
            rec.shrinkagePct = Double.parseDouble(etShrinkagePct.getText().toString().trim());
        } catch (Exception e) {
            rec.fabricWidth = 44.0;
            rec.pieceWidth = 10.0;
            rec.pieceLength = 10.0;
            rec.pieceCount = 1;
            rec.seamAllowance = 0.25;
            rec.shrinkagePct = 3.0;
        }
        rec.allowRotation = swAllowRotation.isChecked();
        rec.totalYards = mLastCalculatedYards;
        rec.totalMeters = mLastCalculatedMeters;
        rec.offcutPct = mLastCalculatedOffcut;
        rec.timestamp = System.currentTimeMillis();
        rec.notes = "Saved from GrainLine HUD";

        long id = mDbHelper.insertSession(rec);
        if (id > 0) {
            Toast.makeText(this, "Session saved: " + name, Toast.LENGTH_SHORT).show();
            refreshRecentSessionsList();
        } else {
            Toast.makeText(this, "Failed to save session", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshRecentSessionsList() {
        llRecentSessionsList.removeAllViews();
        List<DatabaseHelper.SessionRecord> sessions = mDbHelper.getAllSessions();

        if (sessions.isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        llEmptyState.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (final DatabaseHelper.SessionRecord session : sessions) {
            View itemView = inflater.inflate(android.R.layout.simple_list_item_2, llRecentSessionsList, false);
            TextView text1 = itemView.findViewById(android.R.id.text1);
            TextView text2 = itemView.findViewById(android.R.id.text2);

            text1.setText(session.projectName);
            text1.setTextColor(getColor(R.color.m3_on_surface));
            text1.setTextSize(16f);

            String summary = String.format(Locale.US, "%.2f yds (%.2f m) • Bolt: %.0f\" • %d pieces (%.1f\"x%.1f\")",
                    session.totalYards, session.totalMeters, session.fabricWidth, session.pieceCount, session.pieceWidth, session.pieceLength);
            text2.setText(summary);
            text2.setTextColor(getColor(R.color.m3_on_surface_variant));
            text2.setTextSize(13f);

            itemView.setPadding(24, 20, 24, 20);
            itemView.setBackgroundResource(R.drawable.card_m3);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 8, 0, 8);
            itemView.setLayoutParams(lp);

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                intent.putExtra(DetailActivity.EXTRA_SESSION_ID, session.id);
                startActivity(intent);
            });

            llRecentSessionsList.addView(itemView);
        }
    }

    private void shareCurrentCutPlan() {
        if (!mHasActiveResult) {
            calculateFabricRequirements(false);
        }

        String project = etProjectName.getText().toString().trim();
        if (TextUtils.isEmpty(project)) project = "Fabric Cut Plan";

        String shareBody = String.format(Locale.US,
                "GrainLine Cut Plan: %s\n"
                        + "Yardage Needed: %.2f yards (%.2f meters)\n"
                        + "Bolt Width: %s in\n"
                        + "Piece Size: %s\" x %s\" (Qty: %s)\n"
                        + "Seam Allowance: %s\" | Shrinkage: %s%%\n"
                        + "Waste Offcut: %.1f%%\n\n"
                        + "Calculated offline with GrainLine.",
                project,
                mLastCalculatedYards, mLastCalculatedMeters,
                etFabricWidth.getText().toString().trim(),
                etPieceWidth.getText().toString().trim(),
                etPieceLength.getText().toString().trim(),
                etPieceCount.getText().toString().trim(),
                etSeamAllowance.getText().toString().trim(),
                etShrinkagePct.getText().toString().trim(),
                mLastCalculatedOffcut);

        // Copy to clipboard
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("GrainLine Plan", shareBody);
            if (clipboard != null) clipboard.setPrimaryClip(clip);
        } catch (Exception ignored) {
        }

        // Trigger real Android Send Intent
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, project + " - Cut Plan");
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sendIntent, "Export Cut Plan"));
    }

    private void resetInputs() {
        etProjectName.setText("");
        loadLastPresetDefaults();
        mHasActiveResult = false;
        mLastCalculatedYards = 0.0;
        mLastCalculatedMeters = 0.0;
        mLastCalculatedOffcut = 0.0;

        tvHeroYardage.setText("0.00 yds");
        tvHeroMeters.setText("0.00 m");
        tvHeroCutEfficiency.setText("0%");
        tvHeroOffcutWaste.setText("0% offcut");
        tvHeroLayoutPlan.setText("Configure cutting dimensions below");
        llStateActiveIndicator.setVisibility(View.GONE);

        Toast.makeText(this, "Inputs reset to defaults", Toast.LENGTH_SHORT).show();
    }

    private void initMonetization() {
        boolean isPro = mPresetManager.isProUser();
        if (isPro) {
            llAdContainer.setVisibility(View.GONE);
            return;
        }

        llAdContainer.setVisibility(View.VISIBLE);
        tvAdNotice.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com"));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Visit https://apps.kimrosil.com for Artisan Pass", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecentSessionsList();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                calculateFabricRequirements(true);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                swAllowRotation.toggle();
                Toast.makeText(this, "Rotation: " + (swAllowRotation.isChecked() ? "Allowed" : "Locked to Grain"), Toast.LENGTH_SHORT).show();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                resetInputs();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Window resized in desktop mode - preserve live state cleanly
    }
}