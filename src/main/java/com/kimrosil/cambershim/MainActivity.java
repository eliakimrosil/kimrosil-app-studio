package com.kimrosil.cambershim;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    private DoorElevationCanvas canvasDoorElevation;

    // Controls
    private EditText etDoorHeight, etDoorWidth, etHingeSpread, etLatchSag, etProjectName;
    private Button btnUnitToggle, btnCalculate, btnThemeToggle, btnPresetStandard, btnSaveProject;
    private TextView tvStatusBadge, tvResultTopShim, tvResultMortiseGain, tvAdNotice;
    private LinearLayout llEmptyState, llResultDeck, llProjectsContainer, llAdContainer;

    private boolean isMetric = true; // true = mm, false = fractional/decimal inches
    private int themeMode = 0; // 0=Auto, 1=Light, 2=Dark

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences("cambershim_prefs", MODE_PRIVATE);
        int mode = sp.getInt("pref_theme_mode", 0);
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

        prefs = getSharedPreferences("cambershim_prefs", MODE_PRIVATE);
        themeMode = prefs.getInt("pref_theme_mode", 0);
        isMetric = prefs.getBoolean("pref_is_metric", true);
        dbHelper = new DatabaseHelper(this);

        initViews();
        configureStatusBar();
        bindEvents();
        initMonetization();
        refreshSavedProjects();
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        btnPresetStandard = findViewById(R.id.btnPresetStandard);
        btnCalculate = findViewById(R.id.btnCalculate);
        btnSaveProject = findViewById(R.id.btnSaveProject);

        canvasDoorElevation = findViewById(R.id.canvasDoorElevation);
        etProjectName = findViewById(R.id.etProjectName);
        etDoorHeight = findViewById(R.id.etDoorHeight);
        etDoorWidth = findViewById(R.id.etDoorWidth);
        etHingeSpread = findViewById(R.id.etHingeSpread);
        etLatchSag = findViewById(R.id.etLatchSag);

        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvResultTopShim = findViewById(R.id.tvResultTopShim);
        tvResultMortiseGain = findViewById(R.id.tvResultMortiseGain);
        tvAdNotice = findViewById(R.id.tvAdNotice);

        llEmptyState = findViewById(R.id.llEmptyState);
        llResultDeck = findViewById(R.id.llResultDeck);
        llProjectsContainer = findViewById(R.id.llProjectsContainer);
        llAdContainer = findViewById(R.id.llAdContainer);

        updateThemeButtonLabel();
        updateUnitLabels();
    }

    private void configureStatusBar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
                controller.setSystemBarsAppearance(
                        isNight ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        }
    }

    private void bindEvents() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnUnitToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleUnitSystem();
        });

        btnPresetStandard.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            applyDefaultPreset();
        });

        btnCalculate.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            performCalculation();
        });

        btnSaveProject.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveCurrentCalculation();
        });
    }

    private void initMonetization() {
        boolean isProUser = prefs.getBoolean("is_pro_user", false);
        if (isProUser) {
            llAdContainer.setVisibility(View.GONE);
        } else {
            llAdContainer.setVisibility(View.VISIBLE);
            tvAdNotice.setOnClickListener(v -> {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com"));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Opening CamberShim Pro portal...", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void toggleUnitSystem() {
        isMetric = !isMetric;
        prefs.edit().putBoolean("pref_is_metric", isMetric).apply();
        updateUnitLabels();
        Toast.makeText(this, isMetric ? "Units: Millimeters (mm)" : "Units: Inches (in)", Toast.LENGTH_SHORT).show();
    }

    private void updateUnitLabels() {
        btnUnitToggle.setText(isMetric ? "Unit: mm" : "Unit: in");
        etDoorHeight.setHint(isMetric ? "Height (e.g. 2032 mm)" : "Height (e.g. 80.0 in)");
        etDoorWidth.setHint(isMetric ? "Width (e.g. 914 mm)" : "Width (e.g. 36.0 in)");
        etHingeSpread.setHint(isMetric ? "Spread (e.g. 1575 mm)" : "Spread (e.g. 62.0 in)");
        etLatchSag.setHint(isMetric ? "Latch Drop (e.g. 3.2 mm)" : "Latch Drop (e.g. 0.125 in)");
    }

    private void applyDefaultPreset() {
        PresetManager.DoorPreset standard = PresetManager.getStandardPresets().get(1); // 36x80
        if (isMetric) {
            etDoorHeight.setText(String.valueOf((int) standard.heightMm));
            etDoorWidth.setText(String.valueOf((int) standard.widthMm));
            etHingeSpread.setText(String.valueOf((int) standard.hingeSpreadMm));
            etLatchSag.setText("3.0");
        } else {
            etDoorHeight.setText(String.format("%.1f", standard.heightMm / 25.4));
            etDoorWidth.setText(String.format("%.1f", standard.widthMm / 25.4));
            etHingeSpread.setText(String.format("%.1f", standard.hingeSpreadMm / 25.4));
            etLatchSag.setText("0.12");
        }
        Toast.makeText(this, "Standard 36×80 in (914×2032 mm) entry door loaded", Toast.LENGTH_SHORT).show();
        performCalculation();
    }

    private void performCalculation() {
        double height = parseDefensiveDouble(etDoorHeight.getText().toString(), isMetric ? 2032.0 : 80.0);
        double width = parseDefensiveDouble(etDoorWidth.getText().toString(), isMetric ? 914.4 : 36.0);
        double spread = parseDefensiveDouble(etHingeSpread.getText().toString(), isMetric ? 1575.0 : 62.0);
        double deflection = parseDefensiveDouble(etLatchSag.getText().toString(), 0.0);

        // Convert to millimeters for underlying geometry formula
        double heightMm = isMetric ? height : height * 25.4;
        double widthMm = isMetric ? width : width * 25.4;
        double spreadMm = isMetric ? spread : spread * 25.4;
        double deflectionMm = isMetric ? deflection : deflection * 25.4;

        // Hardened clamp safeguards
        if (heightMm < 1000.0) heightMm = 1000.0;
        if (heightMm > 3000.0) heightMm = 3000.0;
        if (widthMm < 400.0) widthMm = 400.0;
        if (widthMm > 1500.0) widthMm = 1500.0;
        if (deflectionMm < 0.0) deflectionMm = 0.0;
        if (deflectionMm > 25.0) deflectionMm = 25.0;

        ShimGeometryCalculator.CalculationResult res =
                ShimGeometryCalculator.calculate(heightMm, widthMm, spreadMm, deflectionMm);

        // Display results
        llEmptyState.setVisibility(View.GONE);
        llResultDeck.setVisibility(View.VISIBLE);

        canvasDoorElevation.updateTelemetry(deflectionMm, res.sagAngleDegrees, res.bindStatus);

        tvStatusBadge.setText("Status: " + res.bindStatus.replace("_", " "));
        if ("HINGE_BOUND".equals(res.bindStatus)) {
            tvStatusBadge.setTextColor(getColor(R.color.gauge_bound_crimson));
        } else if ("TIGHT".equals(res.bindStatus)) {
            tvStatusBadge.setTextColor(getColor(R.color.gauge_tight_amber));
        } else {
            tvStatusBadge.setTextColor(getColor(R.color.gauge_clearance_emerald));
        }

        if (isMetric) {
            tvResultTopShim.setText(String.format("+%.2f mm (Top Hinge Rear)", res.topShimRequiredMm));
            tvResultMortiseGain.setText(String.format("-%.2f mm (Bottom Leaf Relieve)", res.bottomMortiseGainMm));
        } else {
            tvResultTopShim.setText(String.format("+%.3f in (Top Hinge Rear)", res.topShimRequiredMm / 25.4));
            tvResultMortiseGain.setText(String.format("-%.3f in (Bottom Leaf Relieve)", res.bottomMortiseGainMm / 25.4));
        }

        String announceMsg = "Shim calculation complete. Status " + res.bindStatus.replace("_", " ") + ". " + tvResultTopShim.getText();
        tvStatusBadge.announceForAccessibility(announceMsg);

        if (res.isExtremeAngle) {
            Toast.makeText(this, "Caution: Sag angle > 3.5°. Inspect frame squareness and wall plumb!", Toast.LENGTH_LONG).show();
        }
    }

    private void saveCurrentCalculation() {
        String name = etProjectName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            name = "Door " + (System.currentTimeMillis() % 10000);
        }

        double height = parseDefensiveDouble(etDoorHeight.getText().toString(), 2032.0);
        double width = parseDefensiveDouble(etDoorWidth.getText().toString(), 914.4);
        double spread = parseDefensiveDouble(etHingeSpread.getText().toString(), 1575.0);
        double deflection = parseDefensiveDouble(etLatchSag.getText().toString(), 0.0);

        double heightMm = isMetric ? height : height * 25.4;
        double widthMm = isMetric ? width : width * 25.4;
        double spreadMm = isMetric ? spread : spread * 25.4;
        double deflectionMm = isMetric ? deflection : deflection * 25.4;

        ShimGeometryCalculator.CalculationResult res =
                ShimGeometryCalculator.calculate(heightMm, widthMm, spreadMm, deflectionMm);

        long id = dbHelper.insertProject(name, heightMm, widthMm, spreadMm, deflectionMm,
                res.topShimRequiredMm, res.sagAngleDegrees);

        if (id > 0) {
            Toast.makeText(this, "Saved: " + name, Toast.LENGTH_SHORT).show();
            etProjectName.setText("");
            refreshSavedProjects();
        } else {
            Toast.makeText(this, "Failed to save project", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshSavedProjects() {
        llProjectsContainer.removeAllViews();
        List<DatabaseHelper.ProjectRecord> list = dbHelper.getAllProjects();

        if (list.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No saved door cut specs yet. Calculate and hit Save.");
            emptyText.setTextColor(getColor(R.color.m3_on_surface_variant));
            emptyText.setPadding(24, 24, 24, 24);
            llProjectsContainer.addView(emptyText);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (DatabaseHelper.ProjectRecord rec : list) {
            View card = inflater.inflate(R.layout.item_project_card, llProjectsContainer, false);
            TextView tvName = card.findViewById(R.id.tvCardProjectName);
            TextView tvDetails = card.findViewById(R.id.tvCardProjectDetails);
            Button btnOpen = card.findViewById(R.id.btnCardOpen);

            tvName.setText(rec.name);
            tvDetails.setText(String.format("Shim: +%.2f mm | Deflection: %.1f mm | Angle: %.2f°",
                    rec.topShimMm, rec.deflectionMm, rec.angleDeg));

            btnOpen.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                intent.putExtra("EXTRA_PROJECT_ID", rec.id);
                startActivity(intent);
            });

            llProjectsContainer.addView(card);
        }
    }

    private double parseDefensiveDouble(String val, double fallback) {
        if (TextUtils.isEmpty(val)) return fallback;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void cycleThemeMode() {
        themeMode = (themeMode + 1) % 3;
        prefs.edit().putInt("pref_theme_mode", themeMode).apply();
        recreate();
    }

    private void updateThemeButtonLabel() {
        if (themeMode == 1) {
            btnThemeToggle.setText("Light");
        } else if (themeMode == 2) {
            btnThemeToggle.setText("Dark");
        } else {
            btnThemeToggle.setText("Auto");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                performCalculation();
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_SPACE) {
                toggleUnitSystem();
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                etLatchSag.setText("0.0");
                performCalculation();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSavedProjects();
    }
}