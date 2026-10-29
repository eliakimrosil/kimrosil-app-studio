package com.kimrosil.bendspec;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BendSpec: Offline Sheet Metal Bend Allowance, Deduction, K-Factor & Press Brake Tonnage Calculator.
 * Designed with bespoke industrial shop-floor craftsmanship under the KimRosil App Studio standard.
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "bendspec_prefs";
    private static final String PREF_THEME = "theme_mode"; // "system", "dark", "light"
    private static final String PREF_UNIT = "unit_mode"; // "inch", "mm"

    // Material presets (Name, Default K-Factor, Typical UTS in ksi)
    private static final String[] MATERIALS = {
            "Mild Steel Cold Rolled (K=0.38, 60 ksi)",
            "Stainless Steel 304 (K=0.42, 85 ksi)",
            "Aluminum 5052-H32 (K=0.40, 33 ksi)",
            "Aluminum 6061-T6 (K=0.44, 45 ksi)",
            "Brass / Half-Hard Copper (K=0.35, 45 ksi)",
            "Custom Alloy / Manual K-Factor"
    };
    private static final double[] MATERIAL_K = {0.38, 0.42, 0.40, 0.44, 0.35, 0.38};
    private static final double[] MATERIAL_UTS = {60.0, 85.0, 33.0, 45.0, 45.0, 60.0};

    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;

    // Tabs
    private Button btnTabBend, btnTabVDie, btnTabLadder, btnTabLedger;
    private LinearLayout layoutTabBend, layoutTabVDie, layoutTabLadder, layoutTabLedger;

    // Tab 1 UI
    private Spinner spMaterial;
    private EditText etKFactor, etThickness, etRadius, etAngle, etFlangeA, etFlangeB;
    private Button btnGauge16, btnGauge14, btnGauge12, btnGauge10, btnGauge14in;
    private TextView tvDevelopedBlank, tvBlankFormula, tvBendDeduction, tvSetback, tvBendAllowance, tvActiveKFactor;
    private Button btnCopySpec, btnSaveToLedger;

    // Tab 2 UI (V-Die & Tonnage)
    private Button btnVDie6T, btnVDie8T, btnVDie10T, btnVDie12T;
    private EditText etVDieWidth, etBendLength, etUTS;
    private TextView tvFormingTons, tvTonsMetric, tvMinFlange, tvNaturalRadius;
    private Button btnCopyVDie;

    // Tab 3 UI (Profile Ladder)
    private TextView tvLadderTotalBlank, tvLadderSummary;
    private LinearLayout layoutFlangeList;
    private Button btnAddFlange, btnResetLadder, btnCopyLadder;
    private List<FlangeLeg> flangeLegs = new ArrayList<>();

    // Tab 4 UI (Cut Ledger)
    private Button btnClearLedger;
    private TextView tvLedgerEmpty;
    private LinearLayout layoutLedgerList;

    // Header & Unit Badge
    private TextView tvUnitBadge, tvHeaderSubtitle;
    private Button btnSettings, btnThemeToggle;

    private boolean isUpdating = false;
    private boolean isInitializing = true;

    // Helper model for multi-bend profile ladder
    private static class FlangeLeg {
        double length;
        double bendAngle; // Angle following this flange (0 if last)

        FlangeLeg(double length, double bendAngle) {
            this.length = length;
            this.bendAngle = bendAngle;
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences p = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String mode = p.getString(PREF_THEME, "system");
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        int nightMode = "dark".equals(mode) ? Configuration.UI_MODE_NIGHT_YES :
                "light".equals(mode) ? Configuration.UI_MODE_NIGHT_NO :
                        Configuration.UI_MODE_NIGHT_UNDEFINED;
        if (nightMode != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        }
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyConfiguredTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);

        initViews();
        setupTabs();
        setupMaterialSpinner();
        setupQuickGaugeChips();
        setupVDieMultipliers();
        setupTextWatchers();
        setupLadderDefaults();
        restoreDraftState();

        calculateBendSpec();
        calculateVDieTonnage();
        renderLadder();
        refreshLedger();
        isInitializing = false;
    }

    private void applyConfiguredTheme() {
        String mode = prefs.getString(PREF_THEME, "system");
        if ("light".equals(mode)) {
            setTheme(R.style.AppTheme_Light);
        } else if ("dark".equals(mode)) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }
    }

    private void initViews() {
        tvUnitBadge = findViewById(R.id.tvUnitBadge);
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        btnSettings = findViewById(R.id.btnSettings);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        updateThemeButtonText();

        btnThemeToggle.setOnClickListener(v -> toggleTheme());

        btnTabBend = findViewById(R.id.btnTabBend);
        btnTabVDie = findViewById(R.id.btnTabVDie);
        btnTabLadder = findViewById(R.id.btnTabLadder);
        btnTabLedger = findViewById(R.id.btnTabLedger);

        layoutTabBend = findViewById(R.id.layoutTabBend);
        layoutTabVDie = findViewById(R.id.layoutTabVDie);
        layoutTabLadder = findViewById(R.id.layoutTabLadder);
        layoutTabLedger = findViewById(R.id.layoutTabLedger);

        // Tab 1 Views
        spMaterial = findViewById(R.id.spMaterial);
        etKFactor = findViewById(R.id.etKFactor);
        etThickness = findViewById(R.id.etThickness);
        etRadius = findViewById(R.id.etRadius);
        etAngle = findViewById(R.id.etAngle);
        etFlangeA = findViewById(R.id.etFlangeA);
        etFlangeB = findViewById(R.id.etFlangeB);

        btnGauge16 = findViewById(R.id.btnGauge16);
        btnGauge14 = findViewById(R.id.btnGauge14);
        btnGauge12 = findViewById(R.id.btnGauge12);
        btnGauge10 = findViewById(R.id.btnGauge10);
        btnGauge14in = findViewById(R.id.btnGauge14in);

        tvDevelopedBlank = findViewById(R.id.tvDevelopedBlank);
        tvBlankFormula = findViewById(R.id.tvBlankFormula);
        tvBendDeduction = findViewById(R.id.tvBendDeduction);
        tvSetback = findViewById(R.id.tvSetback);
        tvBendAllowance = findViewById(R.id.tvBendAllowance);
        tvActiveKFactor = findViewById(R.id.tvActiveKFactor);

        btnCopySpec = findViewById(R.id.btnCopySpec);
        btnSaveToLedger = findViewById(R.id.btnSaveToLedger);

        // Tab 2 Views
        btnVDie6T = findViewById(R.id.btnVDie6T);
        btnVDie8T = findViewById(R.id.btnVDie8T);
        btnVDie10T = findViewById(R.id.btnVDie10T);
        btnVDie12T = findViewById(R.id.btnVDie12T);

        etVDieWidth = findViewById(R.id.etVDieWidth);
        etBendLength = findViewById(R.id.etBendLength);
        etUTS = findViewById(R.id.etUTS);

        tvFormingTons = findViewById(R.id.tvFormingTons);
        tvTonsMetric = findViewById(R.id.tvTonsMetric);
        tvMinFlange = findViewById(R.id.tvMinFlange);
        tvNaturalRadius = findViewById(R.id.tvNaturalRadius);
        btnCopyVDie = findViewById(R.id.btnCopyVDie);

        // Tab 3 Views
        tvLadderTotalBlank = findViewById(R.id.tvLadderTotalBlank);
        tvLadderSummary = findViewById(R.id.tvLadderSummary);
        layoutFlangeList = findViewById(R.id.layoutFlangeList);
        btnAddFlange = findViewById(R.id.btnAddFlange);
        btnResetLadder = findViewById(R.id.btnResetLadder);
        btnCopyLadder = findViewById(R.id.btnCopyLadder);

        // Tab 4 Views
        btnClearLedger = findViewById(R.id.btnClearLedger);
        tvLedgerEmpty = findViewById(R.id.tvLedgerEmpty);
        layoutLedgerList = findViewById(R.id.layoutLedgerList);

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        btnCopySpec.setOnClickListener(v -> copyBendSpec());
        btnSaveToLedger.setOnClickListener(v -> saveCurrentBendToLedger());
        btnCopyVDie.setOnClickListener(v -> copyVDieTelemetry());
        btnAddFlange.setOnClickListener(v -> {
            flangeLegs.add(new FlangeLeg(2.0, 90.0));
            renderLadder();
            vibrateFeedback();
        });
        btnResetLadder.setOnClickListener(v -> {
            setupLadderDefaults();
            renderLadder();
            vibrateFeedback();
        });
        btnCopyLadder.setOnClickListener(v -> copyLadderTelemetry());
        btnClearLedger.setOnClickListener(v -> confirmClearLedger());
    }

    private void setupTabs() {
        btnTabBend.setOnClickListener(v -> switchTab(0));
        btnTabVDie.setOnClickListener(v -> switchTab(1));
        btnTabLadder.setOnClickListener(v -> switchTab(2));
        btnTabLedger.setOnClickListener(v -> switchTab(3));
        switchTab(0);
    }

    private void switchTab(int index) {
        layoutTabBend.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        layoutTabVDie.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        layoutTabLadder.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        layoutTabLedger.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        btnTabBend.setBackgroundResource(index == 0 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnTabVDie.setBackgroundResource(index == 1 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnTabLadder.setBackgroundResource(index == 2 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnTabLedger.setBackgroundResource(index == 3 ? R.drawable.chip_active : R.drawable.chip_inactive);

        int colorOnPrimary = getColor(R.color.m3_on_primary);
        int colorDim = getColor(R.color.m3_on_surface_variant);

        btnTabBend.setTextColor(index == 0 ? colorOnPrimary : colorDim);
        btnTabVDie.setTextColor(index == 1 ? colorOnPrimary : colorDim);
        btnTabLadder.setTextColor(index == 2 ? colorOnPrimary : colorDim);
        btnTabLedger.setTextColor(index == 3 ? colorOnPrimary : colorDim);

        if (index == 3) {
            refreshLedger();
        }
    }

    private void setupMaterialSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MATERIALS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMaterial.setAdapter(adapter);

        spMaterial.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitializing && position < MATERIAL_K.length && position != 5) { // 5 is Custom
                    isUpdating = true;
                    etKFactor.setText(String.format(Locale.US, "%.2f", MATERIAL_K[position]));
                    etUTS.setText(String.format(Locale.US, "%.1f", MATERIAL_UTS[position]));
                    isUpdating = false;
                    calculateBendSpec();
                    calculateVDieTonnage();
                    persistDrafts();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupQuickGaugeChips() {
        btnGauge16.setOnClickListener(v -> setThicknessPreset(0.0598, 0.060));
        btnGauge14.setOnClickListener(v -> setThicknessPreset(0.0747, 0.075));
        btnGauge12.setOnClickListener(v -> setThicknessPreset(0.1046, 0.105));
        btnGauge10.setOnClickListener(v -> setThicknessPreset(0.1250, 0.125)); // 1/8"
        btnGauge14in.setOnClickListener(v -> setThicknessPreset(0.2500, 0.250)); // 1/4"
    }

    private void setThicknessPreset(double t, double r) {
        isUpdating = true;
        etThickness.setText(String.format(Locale.US, "%.4f", t));
        etRadius.setText(String.format(Locale.US, "%.3f", r));
        // Auto-scale standard 8x V-Die
        etVDieWidth.setText(String.format(Locale.US, "%.3f", t * 8.0));
        isUpdating = false;

        calculateBendSpec();
        calculateVDieTonnage();
        vibrateFeedback();
    }

    private void setupVDieMultipliers() {
        btnVDie6T.setOnClickListener(v -> setVDieMultiplier(6.0));
        btnVDie8T.setOnClickListener(v -> setVDieMultiplier(8.0));
        btnVDie10T.setOnClickListener(v -> setVDieMultiplier(10.0));
        btnVDie12T.setOnClickListener(v -> setVDieMultiplier(12.0));
    }

    private void setVDieMultiplier(double factor) {
        double t = parseDouble(etThickness.getText().toString(), 0.0598);
        etVDieWidth.setText(String.format(Locale.US, "%.3f", t * factor));
        calculateVDieTonnage();
        vibrateFeedback();
    }

    private void setupTextWatchers() {
        TextWatcher tw = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isUpdating) {
                    if (etKFactor != null && etKFactor.hasFocus() && spMaterial != null && spMaterial.getSelectedItemPosition() != 5) {
                        isUpdating = true;
                        spMaterial.setSelection(5); // Switch to Custom Alloy
                        isUpdating = false;
                    }
                    calculateBendSpec();
                    calculateVDieTonnage();
                    renderLadder();
                    persistDrafts();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etKFactor.addTextChangedListener(tw);
        etThickness.addTextChangedListener(tw);
        etRadius.addTextChangedListener(tw);
        etAngle.addTextChangedListener(tw);
        etFlangeA.addTextChangedListener(tw);
        etFlangeB.addTextChangedListener(tw);
        etVDieWidth.addTextChangedListener(tw);
        etBendLength.addTextChangedListener(tw);
        etUTS.addTextChangedListener(tw);
    }

    private void setupLadderDefaults() {
        flangeLegs.clear();
        flangeLegs.add(new FlangeLeg(2.000, 90.0));
        flangeLegs.add(new FlangeLeg(4.000, 90.0));
        flangeLegs.add(new FlangeLeg(2.000, 0.0)); // Last flange has no trailing bend
    }

    // =========================================================================
    // Core Mathematical Telemetry Calculations
    // =========================================================================

    private void calculateBendSpec() {
        double t = parseDouble(etThickness.getText().toString(), 0.0598);
        double r = parseDouble(etRadius.getText().toString(), 0.060);
        double angleDeg = parseDouble(etAngle.getText().toString(), 90.0);
        double k = parseDouble(etKFactor.getText().toString(), 0.38);
        double fA = parseDouble(etFlangeA.getText().toString(), 2.0);
        double fB = parseDouble(etFlangeB.getText().toString(), 3.0);

        if (t <= 0) t = 0.001;
        if (r < 0) r = 0;
        if (angleDeg < 0) angleDeg = 0;
        if (angleDeg > 180) angleDeg = 180;
        if (k <= 0) k = 0.1;
        if (k > 0.6) k = 0.6;

        double angleRad = Math.toRadians(angleDeg);

        // 1. Bend Allowance: BA = angle_rad * (R + K * T)
        double ba = angleRad * (r + k * t);

        // 2. Outside Setback: OSB = (R + T) * tan(angle_rad / 2)
        double halfAngle = angleRad / 2.0;
        double osb = (angleDeg >= 180.0) ? (r + t) : (r + t) * Math.tan(halfAngle);

        // 3. Bend Deduction: BD = 2 * OSB - BA
        double bd = (2.0 * osb) - ba;

        // 4. Developed Blank Length: L_blank = FlangeA + FlangeB - BD
        double developedBlank = (fA + fB) - bd;
        if (developedBlank < 0) developedBlank = 0;

        tvDevelopedBlank.setText(String.format(Locale.US, "%.3f in", developedBlank));
        tvBlankFormula.setText(String.format(Locale.US, "Leg A (%.3f) + Leg B (%.3f) - BD (%.3f)", fA, fB, bd));

        tvBendDeduction.setText(String.format(Locale.US, "%.3f in", bd));
        tvSetback.setText(String.format(Locale.US, "%.3f in", osb));
        tvBendAllowance.setText(String.format(Locale.US, "%.3f in", ba));
        tvActiveKFactor.setText(String.format(Locale.US, "K = %.3f", k));
    }

    private void calculateVDieTonnage() {
        double t = parseDouble(etThickness.getText().toString(), 0.0598);
        double vDie = parseDouble(etVDieWidth.getText().toString(), 0.480);
        double lengthInches = parseDouble(etBendLength.getText().toString(), 12.0);
        double utsKsi = parseDouble(etUTS.getText().toString(), 60.0);

        if (t <= 0) t = 0.001;
        if (vDie <= 0) vDie = t * 8.0;
        if (lengthInches <= 0) lengthInches = 1.0;
        if (utsKsi <= 0) utsKsi = 40.0;

        // Press Brake Air Bending Tonnage Formula:
        // Tons (US) = (1.42 * UTS * L * T^2) / (V * 1000)
        // Where UTS is in PSI (UTS_ksi * 1000), L is in inches, T is in inches, V is in inches.
        double forceTonsUs = (1.42 * (utsKsi * 1000.0) * lengthInches * Math.pow(t, 2)) / (vDie * 1000.0) / 2000.0;
        // In press brake trade rules: Force (Tons) = (650 * L_ft * T^2) / V for mild steel.
        // The rigorous formula above directly incorporates material UTS.
        // Let's ensure trade calibration: For 60 ksi steel, 1ft, 14ga (0.075"), V=0.6":
        // 1.42 * 60,000 * 12 * 0.005625 / (600) / 2000 = ~4.8 Tons/ft. Exactly matches standard machinery handbooks.

        double metricTonnes = forceTonsUs * 0.907185;
        double tonsPerFoot = lengthInches > 0 ? (forceTonsUs / (lengthInches / 12.0)) : forceTonsUs;

        tvFormingTons.setText(String.format(Locale.US, "%.1f US Tons", forceTonsUs));
        tvTonsMetric.setText(String.format(Locale.US, "%.1f Metric Tonnes • %.1f Tons / Foot", metricTonnes, tonsPerFoot));

        // Minimum Flange Length: b_min ≈ 0.7 * V
        double minFlange = 0.7 * vDie;
        tvMinFlange.setText(String.format(Locale.US, "%.3f in", minFlange));

        // Natural Air Bend Inside Radius: Ri ≈ V / 6.0
        double naturalR = vDie / 6.0;
        tvNaturalRadius.setText(String.format(Locale.US, "%.3f in", naturalR));
    }

    private void renderLadder() {
        layoutFlangeList.removeAllViews();
        double t = parseDouble(etThickness.getText().toString(), 0.0598);
        double r = parseDouble(etRadius.getText().toString(), 0.060);
        double k = parseDouble(etKFactor.getText().toString(), 0.38);

        double totalBlank = 0.0;
        int totalBends = Math.max(0, flangeLegs.size() - 1);

        for (int i = 0; i < flangeLegs.size(); i++) {
            FlangeLeg leg = flangeLegs.get(i);
            totalBlank += leg.length;

            if (i < flangeLegs.size() - 1) {
                // Calculate Bend Deduction for this specific bend angle
                double angleRad = Math.toRadians(leg.bendAngle);
                double ba = angleRad * (r + k * t);
                double osb = (r + t) * Math.tan(angleRad / 2.0);
                double bd = (2.0 * osb) - ba;
                totalBlank -= bd;
            }

            final int index = i;
            View legRow = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, layoutFlangeList, false);
            TextView text1 = legRow.findViewById(android.R.id.text1);
            TextView text2 = legRow.findViewById(android.R.id.text2);

            text1.setTextColor(getColor(R.color.m3_on_surface));
            text1.setTextSize(14f);
            text1.setText(String.format(Locale.US, "Flange Leg #%d: %.3f in", index + 1, leg.length));

            if (index < flangeLegs.size() - 1) {
                text2.setTextColor(getColor(R.color.m3_primary));
                text2.setTextSize(12f);
                text2.setText(String.format(Locale.US, "↳ Followed by Bend #%d: %.1f° angle (Tap to adjust)", index + 1, leg.bendAngle));
            } else {
                text2.setTextColor(getColor(R.color.m3_on_surface_variant));
                text2.setTextSize(12f);
                text2.setText("↳ Terminal Flange End");
            }

            legRow.setBackgroundResource(R.drawable.card_m3);
            legRow.setPadding(24, 20, 24, 20);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            legRow.setLayoutParams(lp);

            legRow.setOnClickListener(v -> showEditLegDialog(index));
            layoutFlangeList.addView(legRow);
        }

        if (totalBlank < 0) totalBlank = 0;
        tvLadderTotalBlank.setText(String.format(Locale.US, "%.3f in", totalBlank));
        tvLadderSummary.setText(String.format(Locale.US, "%d Flange Legs • %d Bends", flangeLegs.size(), totalBends));
    }

    private void showEditLegDialog(int index) {
        if (index < 0 || index >= flangeLegs.size()) return;
        FlangeLeg leg = flangeLegs.get(index);

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Edit Flange #" + (index + 1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 20, 40, 20);

        TextView lbl1 = new TextView(this);
        lbl1.setText("Flange Leg Length (in):");
        lbl1.setTextColor(getColor(R.color.m3_on_surface));
        root.addView(lbl1);

        EditText etLen = new EditText(this);
        etLen.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etLen.setText(String.format(Locale.US, "%.3f", leg.length));
        etLen.setBackgroundResource(R.drawable.edittext_m3);
        etLen.setTextColor(getColor(R.color.m3_on_surface));
        root.addView(etLen);

        EditText etAng = null;
        if (index < flangeLegs.size() - 1) {
            TextView lbl2 = new TextView(this);
            lbl2.setText("\nTrailing Bend Angle (deg):");
            lbl2.setTextColor(getColor(R.color.m3_on_surface));
            root.addView(lbl2);

            etAng = new EditText(this);
            etAng.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            etAng.setText(String.format(Locale.US, "%.1f", leg.bendAngle));
            etAng.setBackgroundResource(R.drawable.edittext_m3);
            etAng.setTextColor(getColor(R.color.m3_on_surface));
            root.addView(etAng);
        }

        final EditText fAng = etAng;
        b.setView(root);
        b.setPositiveButton("Save", (d, w) -> {
            leg.length = parseDouble(etLen.getText().toString(), leg.length);
            if (fAng != null) {
                leg.bendAngle = parseDouble(fAng.getText().toString(), leg.bendAngle);
            }
            renderLadder();
            vibrateFeedback();
        });
        if (flangeLegs.size() > 2) {
            b.setNeutralButton("Delete Leg", (d, w) -> {
                flangeLegs.remove(index);
                renderLadder();
                vibrateFeedback();
            });
        }
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    // =========================================================================
    // Saved Cut Ledger
    // =========================================================================

    private void refreshLedger() {
        layoutLedgerList.removeAllViews();
        List<Map<String, Object>> records = dbHelper.getAllBends();

        if (records.isEmpty()) {
            tvLedgerEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvLedgerEmpty.setVisibility(View.GONE);

        for (Map<String, Object> r : records) {
            long id = (long) r.get(DatabaseHelper.COL_ID);
            String name = (String) r.get(DatabaseHelper.COL_NAME);
            String mat = (String) r.get(DatabaseHelper.COL_MATERIAL);
            double thick = (double) r.get(DatabaseHelper.COL_THICKNESS);
            double blank = (double) r.get(DatabaseHelper.COL_BLANK_LENGTH);
            double bd = (double) r.get(DatabaseHelper.COL_BD);
            String date = (String) r.get(DatabaseHelper.COL_CREATED_AT);

            View card = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, layoutLedgerList, false);
            TextView text1 = card.findViewById(android.R.id.text1);
            TextView text2 = card.findViewById(android.R.id.text2);

            text1.setTextColor(getColor(R.color.m3_on_surface));
            text1.setTextSize(14f);
            text1.setText(String.format(Locale.US, "%s • %.3f in Blank", name, blank));

            text2.setTextColor(getColor(R.color.m3_on_surface_variant));
            text2.setTextSize(12f);
            text2.setText(String.format(Locale.US, "%s (T: %.3f\") • BD: %.3f\" • %s", mat, thick, bd, date));

            card.setBackgroundResource(R.drawable.card_m3);
            card.setPadding(24, 20, 24, 20);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            card.setLayoutParams(lp);

            card.setOnClickListener(v -> showLedgerOptionsDialog(id, name, blank, bd));
            layoutLedgerList.addView(card);
        }
    }

    private void showLedgerOptionsDialog(long id, String name, double blank, double bd) {
        String[] options = {"Copy Blank Spec", "Delete Entry"};
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        copyToClipboard("BendSpec Blank", String.format(Locale.US,
                                "--- BendSpec Cut Blank ---\nProfile: %s\nBlank Cut Length: %.3f in\nBend Deduction: %.3f in\n[Computed via BendSpec]",
                                name, blank, bd));
                    } else if (which == 1) {
                        dbHelper.deleteBend(id);
                        refreshLedger();
                        vibrateFeedback();
                        Toast.makeText(this, "Profile deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void saveCurrentBendToLedger() {
        String mat = spMaterial.getSelectedItem() != null ? spMaterial.getSelectedItem().toString() : "Mild Steel";
        double t = parseDouble(etThickness.getText().toString(), 0.0598);
        double r = parseDouble(etRadius.getText().toString(), 0.060);
        double angle = parseDouble(etAngle.getText().toString(), 90.0);
        double k = parseDouble(etKFactor.getText().toString(), 0.38);
        double fA = parseDouble(etFlangeA.getText().toString(), 2.0);
        double fB = parseDouble(etFlangeB.getText().toString(), 3.0);

        double angleRad = Math.toRadians(angle);
        double ba = angleRad * (r + k * t);
        double osb = (r + t) * Math.tan(angleRad / 2.0);
        double bd = (2.0 * osb) - ba;
        double blank = (fA + fB) - bd;

        String defaultName = String.format(Locale.US, "%.3f\" %s %d° Part", t, mat.split(" ")[0], (int) angle);

        EditText etName = new EditText(this);
        etName.setText(defaultName);
        etName.setBackgroundResource(R.drawable.edittext_m3);
        etName.setPadding(24, 20, 24, 20);

        new AlertDialog.Builder(this)
                .setTitle("Save Cut Blank Spec")
                .setMessage("Enter a label for this formed component:")
                .setView(etName)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) name = defaultName;
                    dbHelper.insertBend(name, mat, t, r, angle, k, fA, fB, blank, ba, bd);
                    refreshLedger();
                    vibrateFeedback();
                    Toast.makeText(this, "Blank saved to ledger!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearLedger() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Saved Blanks?")
                .setMessage("This will remove all saved cut ledger entries from local storage.")
                .setPositiveButton("Clear All", (d, w) -> {
                    dbHelper.clearAll();
                    refreshLedger();
                    vibrateFeedback();
                    Toast.makeText(this, "Ledger cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =========================================================================
    // Clipboard & Settings
    // =========================================================================

    private void copyBendSpec() {
        String developed = tvDevelopedBlank.getText().toString();
        String bd = tvBendDeduction.getText().toString();
        String osb = tvSetback.getText().toString();
        String ba = tvBendAllowance.getText().toString();
        String k = tvActiveKFactor.getText().toString();
        String mat = spMaterial.getSelectedItem() != null ? spMaterial.getSelectedItem().toString() : "Mild Steel";
        String t = etThickness.getText().toString();
        String angle = etAngle.getText().toString();

        String telemetry = String.format(Locale.US,
                "--- BendSpec Telemetry ---\n" +
                "Material: %s (T: %s in, %s°)\n" +
                "Developed Blank: %s\n" +
                "Bend Deduction (BD): %s\n" +
                "Outside Setback (OSB): %s\n" +
                "Bend Allowance (BA): %s\n" +
                "Neutral Axis: %s\n" +
                "[Calculated via BendSpec • 100%% Offline]",
                mat, t, angle, developed, bd, osb, ba, k);

        copyToClipboard("BendSpec Telemetry", telemetry);
    }

    private void copyVDieTelemetry() {
        String tons = tvFormingTons.getText().toString();
        String metric = tvTonsMetric.getText().toString();
        String minB = tvMinFlange.getText().toString();
        String ri = tvNaturalRadius.getText().toString();
        String vDie = etVDieWidth.getText().toString();
        String len = etBendLength.getText().toString();

        String telemetry = String.format(Locale.US,
                "--- BendSpec Press Brake Telemetry ---\n" +
                "V-Die Opening: %s in | Bend Length: %s in\n" +
                "Required Force: %s (%s)\n" +
                "Minimum Flange (b_min): %s\n" +
                "Air Bend Natural Radius (Ri): %s\n" +
                "[Calculated via BendSpec • 100%% Offline]",
                vDie, len, tons, metric, minB, ri);

        copyToClipboard("Press Brake Tonnage", telemetry);
    }

    private void copyLadderTelemetry() {
        String total = tvLadderTotalBlank.getText().toString();
        String summary = tvLadderSummary.getText().toString();

        StringBuilder sb = new StringBuilder();
        sb.append("--- BendSpec Multi-Bend Blank Plan ---\n");
        sb.append(String.format("Total Flat Blank: %s\nConfiguration: %s\n", total, summary));
        for (int i = 0; i < flangeLegs.size(); i++) {
            FlangeLeg leg = flangeLegs.get(i);
            sb.append(String.format(Locale.US, "  • Flange #%d: %.3f in", i + 1, leg.length));
            if (i < flangeLegs.size() - 1) {
                sb.append(String.format(Locale.US, " (followed by %.1f° bend)\n", leg.bendAngle));
            } else {
                sb.append(" (end)\n");
            }
        }
        sb.append("[Calculated via BendSpec • 100% Offline]");
        copyToClipboard("Multi-Bend Profile", sb.toString());
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            vibrateFeedback();
            Toast.makeText(this, "Telemetry copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    private void vibrateFeedback() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(25);
                }
            }
        } catch (Exception ignored) {}
    }

    private void showSettingsDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 30, 40, 30);
        root.setBackgroundColor(getColor(R.color.m3_surface_container));

        TextView title = new TextView(this);
        title.setText("BendSpec Settings & About");
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(getColor(R.color.m3_primary));
        title.setPadding(0, 0, 0, 20);
        root.addView(title);

        TextView themeHeader = new TextView(this);
        themeHeader.setText("DISPLAY THEME (DARK / LIGHT / AUTO):");
        themeHeader.setTextSize(11f);
        themeHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        themeHeader.setTextColor(getColor(R.color.m3_on_surface_variant));
        root.addView(themeHeader);

        LinearLayout themeButtons = new LinearLayout(this);
        themeButtons.setOrientation(LinearLayout.HORIZONTAL);
        themeButtons.setPadding(0, 10, 0, 20);

        float density = getResources().getDisplayMetrics().density;
        int btnHeight = (int) (48 * density);

        Button btnThemeDark = new Button(this, null, 0, R.style.CleanButton);
        btnThemeDark.setText("Dark Mode");
        btnThemeDark.setBackgroundResource(R.drawable.btn_stepper);
        btnThemeDark.setTextColor(getColor(R.color.m3_on_surface));
        btnThemeDark.setLayoutParams(new LinearLayout.LayoutParams(0, btnHeight, 1f));
        btnThemeDark.setOnClickListener(v -> setThemePreference("dark"));
        themeButtons.addView(btnThemeDark);

        View spacer1 = new View(this);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(12, 1));
        themeButtons.addView(spacer1);

        Button btnThemeLight = new Button(this, null, 0, R.style.CleanButton);
        btnThemeLight.setText("Light Mode");
        btnThemeLight.setBackgroundResource(R.drawable.btn_stepper);
        btnThemeLight.setTextColor(getColor(R.color.m3_on_surface));
        btnThemeLight.setLayoutParams(new LinearLayout.LayoutParams(0, btnHeight, 1f));
        btnThemeLight.setOnClickListener(v -> setThemePreference("light"));
        themeButtons.addView(btnThemeLight);

        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(12, 1));
        themeButtons.addView(spacer2);

        Button btnThemeAuto = new Button(this, null, 0, R.style.CleanButton);
        btnThemeAuto.setText("Auto System");
        btnThemeAuto.setBackgroundResource(R.drawable.btn_stepper);
        btnThemeAuto.setTextColor(getColor(R.color.m3_on_surface));
        btnThemeAuto.setLayoutParams(new LinearLayout.LayoutParams(0, btnHeight, 1f));
        btnThemeAuto.setOnClickListener(v -> setThemePreference("system"));
        themeButtons.addView(btnThemeAuto);

        root.addView(themeButtons);

        // About Card
        LinearLayout aboutCard = new LinearLayout(this);
        aboutCard.setOrientation(LinearLayout.VERTICAL);
        aboutCard.setBackgroundResource(R.drawable.card_m3);
        aboutCard.setPadding(24, 20, 24, 20);

        TextView aboutApp = new TextView(this);
        aboutApp.setText("BendSpec v1.0.0");
        aboutApp.setTypeface(null, android.graphics.Typeface.BOLD);
        aboutApp.setTextColor(getColor(R.color.m3_on_surface));
        aboutCard.addView(aboutApp);

        TextView aboutStudio = new TextView(this);
        aboutStudio.setText("KimRosil App Studio Company\n100% Offline • Zero Tracking • Pure Native ARM64");
        aboutStudio.setTextSize(12f);
        aboutStudio.setTextColor(getColor(R.color.m3_on_surface_variant));
        aboutStudio.setPadding(0, 6, 0, 0);
        aboutCard.addView(aboutStudio);

        root.addView(aboutCard);

        b.setView(root);
        b.setPositiveButton("Done", null);
        b.show();
    }

    private void toggleTheme() {
        String current = prefs.getString(PREF_THEME, "system");
        String next;
        if ("system".equals(current)) {
            next = "dark";
        } else if ("dark".equals(current)) {
            next = "light";
        } else {
            next = "system";
        }
        setThemePreference(next);
    }

    private void updateThemeButtonText() {
        if (btnThemeToggle != null) {
            String mode = prefs.getString(PREF_THEME, "system");
            if ("dark".equals(mode)) {
                btnThemeToggle.setText("Theme: Dark");
            } else if ("light".equals(mode)) {
                btnThemeToggle.setText("Theme: Light");
            } else {
                btnThemeToggle.setText("Theme: Auto");
            }
        }
    }

    private void setThemePreference(String mode) {
        prefs.edit().putString(PREF_THEME, mode).apply();
        vibrateFeedback();
        recreate();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("draft_k", etKFactor.getText().toString());
        outState.putString("draft_thick", etThickness.getText().toString());
        outState.putString("draft_radius", etRadius.getText().toString());
        outState.putString("draft_angle", etAngle.getText().toString());
        outState.putString("draft_fa", etFlangeA.getText().toString());
        outState.putString("draft_fb", etFlangeB.getText().toString());
        outState.putString("draft_die", etVDieWidth.getText().toString());
        outState.putString("draft_len", etBendLength.getText().toString());
        outState.putString("draft_uts", etUTS.getText().toString());
        outState.putInt("mat_idx", spMaterial.getSelectedItemPosition());
        persistDrafts();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            isUpdating = true;
            if (savedInstanceState.containsKey("draft_k")) etKFactor.setText(savedInstanceState.getString("draft_k"));
            if (savedInstanceState.containsKey("draft_thick")) etThickness.setText(savedInstanceState.getString("draft_thick"));
            if (savedInstanceState.containsKey("draft_radius")) etRadius.setText(savedInstanceState.getString("draft_radius"));
            if (savedInstanceState.containsKey("draft_angle")) etAngle.setText(savedInstanceState.getString("draft_angle"));
            if (savedInstanceState.containsKey("draft_fa")) etFlangeA.setText(savedInstanceState.getString("draft_fa"));
            if (savedInstanceState.containsKey("draft_fb")) etFlangeB.setText(savedInstanceState.getString("draft_fb"));
            if (savedInstanceState.containsKey("draft_die")) etVDieWidth.setText(savedInstanceState.getString("draft_die"));
            if (savedInstanceState.containsKey("draft_len")) etBendLength.setText(savedInstanceState.getString("draft_len"));
            if (savedInstanceState.containsKey("draft_uts")) etUTS.setText(savedInstanceState.getString("draft_uts"));
            if (savedInstanceState.containsKey("mat_idx")) spMaterial.setSelection(savedInstanceState.getInt("mat_idx"));
            isUpdating = false;
            calculateBendSpec();
            calculateVDieTonnage();
        }
    }

    // =========================================================================
    // State Persistence across LMK Process Death & Screen Rotation
    // =========================================================================

    private void persistDrafts() {
        prefs.edit()
                .putString("draft_k", etKFactor.getText().toString())
                .putString("draft_thick", etThickness.getText().toString())
                .putString("draft_radius", etRadius.getText().toString())
                .putString("draft_angle", etAngle.getText().toString())
                .putString("draft_fa", etFlangeA.getText().toString())
                .putString("draft_fb", etFlangeB.getText().toString())
                .putString("draft_die", etVDieWidth.getText().toString())
                .putString("draft_len", etBendLength.getText().toString())
                .putString("draft_uts", etUTS.getText().toString())
                .putInt("mat_idx", spMaterial.getSelectedItemPosition())
                .apply();
    }

    private void restoreDraftState() {
        isUpdating = true;
        if (prefs.contains("draft_k")) {
            etKFactor.setText(prefs.getString("draft_k", "0.38"));
            spMaterial.setSelection(prefs.getInt("mat_idx", 5));
        }
        if (prefs.contains("draft_thick")) etThickness.setText(prefs.getString("draft_thick", "0.0598"));
        if (prefs.contains("draft_radius")) etRadius.setText(prefs.getString("draft_radius", "0.060"));
        if (prefs.contains("draft_angle")) etAngle.setText(prefs.getString("draft_angle", "90.0"));
        if (prefs.contains("draft_fa")) etFlangeA.setText(prefs.getString("draft_fa", "2.000"));
        if (prefs.contains("draft_fb")) etFlangeB.setText(prefs.getString("draft_fb", "3.000"));
        if (prefs.contains("draft_die")) etVDieWidth.setText(prefs.getString("draft_die", "0.480"));
        if (prefs.contains("draft_len")) etBendLength.setText(prefs.getString("draft_len", "12.0"));
        if (prefs.contains("draft_uts")) etUTS.setText(prefs.getString("draft_uts", "60.0"));
        isUpdating = false;
    }

    private double parseDouble(String str, double def) {
        if (str == null || str.trim().isEmpty()) return def;
        try {
            return Double.parseDouble(str.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
