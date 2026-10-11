package com.kimrosil.cambershim;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class DetailActivity extends Activity {

    private DatabaseHelper dbHelper;
    private long projectId = -1;

    private TextView tvDetailTitle, tvDetailSpecs, tvDetailShimThickness, tvMaterialGuide;
    private RadioGroup rgMaterialPresets;
    private Button btnExportCutSheet, btnDeleteProject, btnBackToolbar;

    private double currentShimMm = 1.2;
    private double standardStockThicknessMm = 0.5; // Cardstock default

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
        setContentView(R.layout.activity_detail);

        dbHelper = new DatabaseHelper(this);
        projectId = getIntent().getLongExtra("EXTRA_PROJECT_ID", -1);

        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvDetailSpecs = findViewById(R.id.tvDetailSpecs);
        tvDetailShimThickness = findViewById(R.id.tvDetailShimThickness);
        tvMaterialGuide = findViewById(R.id.tvMaterialGuide);
        rgMaterialPresets = findViewById(R.id.rgMaterialPresets);
        btnExportCutSheet = findViewById(R.id.btnExportCutSheet);
        btnDeleteProject = findViewById(R.id.btnDeleteProject);
        btnBackToolbar = findViewById(R.id.btnBackToolbar);

        btnBackToolbar.setOnClickListener(v -> finish());

        loadProjectData();
        setupMaterialSelector();
        setupExportActions();
    }

    private void loadProjectData() {
        if (projectId == -1) {
            finish();
            return;
        }
        DatabaseHelper.ProjectRecord rec = dbHelper.getProjectById(projectId);
        if (rec == null) {
            Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvDetailTitle.setText(rec.name);
        currentShimMm = rec.topShimMm;
        tvDetailSpecs.setText(String.format("Door: %.0f × %.0f mm | Hinge Spread: %.0f mm | Latch Deflection: %.1f mm | Bevel: %.2f°",
                rec.heightMm, rec.widthMm, rec.spreadMm, rec.deflectionMm, rec.angleDeg));

        updateShimRecommendation();
    }

    private void setupMaterialSelector() {
        rgMaterialPresets.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbCardstock) {
                standardStockThicknessMm = 0.45; // Thick cardstock (~0.018")
            } else if (checkedId == R.id.rbBrass) {
                standardStockThicknessMm = 0.50; // Solid brass shim sheet (0.020")
            } else if (checkedId == R.id.rbVeneer) {
                standardStockThicknessMm = 0.80; // Hardwood veneer leaf (~1/32")
            }
            updateShimRecommendation();
        });
    }

    private void updateShimRecommendation() {
        int sheetsRequired = (int) Math.ceil(currentShimMm / standardStockThicknessMm);
        if (sheetsRequired < 1) sheetsRequired = 1;

        tvDetailShimThickness.setText(String.format("Required Shim: +%.2f mm", currentShimMm));
        tvMaterialGuide.setText(String.format("Cut %d leaf layer(s) using selected stock (%.2f mm/layer).\nPlace behind top hinge leaf towards jamb barrel to lift latch latch margin.",
                sheetsRequired, standardStockThicknessMm));
    }

    private void setupExportActions() {
        btnExportCutSheet.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            String cutSheet = "=== CAMBERSHIM CUT & MORTISE SPECIFICATION ===\n" +
                    "Project: " + tvDetailTitle.getText() + "\n" +
                    "Specs: " + tvDetailSpecs.getText() + "\n" +
                    "Target Top Shim: " + currentShimMm + " mm\n" +
                    "Instruction: " + tvMaterialGuide.getText() + "\n" +
                    "Generated offline via CamberShim Precision Carpentry Engine.";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "CamberShim Cut Sheet: " + tvDetailTitle.getText());
            shareIntent.putExtra(Intent.EXTRA_TEXT, cutSheet);
            startActivity(Intent.createChooser(shareIntent, "Export Cut Sheet"));
        });

        btnDeleteProject.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            dbHelper.deleteProject(projectId);
            Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}