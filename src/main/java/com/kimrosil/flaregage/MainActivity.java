package com.kimrosil.flaregage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREF_NAME = "flaregage_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode";
    private static final String KEY_STD = "pref_standard";
    private static final String KEY_SIZE = "pref_size";
    private static final String KEY_MAT = "pref_material";
    private static final String KEY_UNIT = "pref_unit_is_inch";

    private int mThemeMode = 0; // 0=Auto, 1=Light, 2=Dark
    private boolean mIsInchUnit = false;

    private FlareSpec.Standard mCurrentStandard = FlareSpec.Standard.SAE_45_DOUBLE;
    private FlareSpec.TubingSize mCurrentSize = FlareSpec.TubingSize.SIZE_3_16;
    private FlareSpec.Material mCurrentMaterial = FlareSpec.Material.CUNI;
    private FlareSpec.SpecData mCurrentSpec;

    private DatabaseHelper mDbHelper;

    // UI Controls
    private TextView tvAppTitle;
    private Button btnSignoffLogs;
    private Button btnSpecsDetail;
    private Button btnThemeToggle;

    // Standards
    private Button btnStdSae45Double;
    private Button btnStdSae45Single;
    private Button btnStdAn37;
    private Button btnStdDinBubble;

    // Sizes
    private Button btnSize316;
    private Button btnSize14;
    private Button btnSize516;
    private Button btnSize38;
    private Button btnSize12;
    private Button btnSize58;
    private Button btnSize34;

    // Materials
    private Button btnMatCuni;
    private Button btnMatSoftCopper;
    private Button btnMatSteel;
    private Button btnMatStainless;
    private Button btnMatHardCopper;
    private Button btnMatAluminum;

    // Metallurgy Alert
    private LinearLayout llMaterialAlert;
    private TextView tvMaterialAlertTitle;
    private TextView tvMaterialAlertBody;

    // Visuals & Telemetry
    private TextView tvProfileSubtitle;
    private FlareProfileView flareProfileView;
    private TextView tvSpecFlareOdMm;
    private TextView tvSpecFlareOdIn;
    private TextView tvSpecStickoutMm;
    private TextView tvSpecStickoutFrac;
    private TextView tvSpecTorqueNm;
    private TextView tvSpecTorqueFtLb;
    private TextView tvSpecThread;
    private TextView tvSpecFft;

    // Caliper Verification
    private EditText etCaliperInput;
    private Button btnUnitToggle;
    private Button btnVerify;
    private LinearLayout llVerificationResult;
    private TextView tvResultBadge;
    private TextView tvResultDetail;
    private LinearLayout llSignoffContainer;
    private EditText etJobRef;
    private Button btnSaveSignoff;

    private FlareSpec.VerificationResult mLastResult;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mThemeMode = prefs.getInt(KEY_THEME_MODE, 0);

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
        setContentView(R.layout.activity_main);

        mDbHelper = new DatabaseHelper(this);
        loadPreferences();

        initViews();
        setupListeners();
        updateThemeUI();
        refreshAllSpecs();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            mCurrentStandard = FlareSpec.Standard.valueOf(prefs.getString(KEY_STD, FlareSpec.Standard.SAE_45_DOUBLE.name()));
        } catch (Exception ignored) {}

        try {
            mCurrentSize = FlareSpec.TubingSize.valueOf(prefs.getString(KEY_SIZE, FlareSpec.TubingSize.SIZE_3_16.name()));
        } catch (Exception ignored) {}

        try {
            mCurrentMaterial = FlareSpec.Material.valueOf(prefs.getString(KEY_MAT, FlareSpec.Material.CUNI.name()));
        } catch (Exception ignored) {}

        mIsInchUnit = prefs.getBoolean(KEY_UNIT, false);
    }

    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_STD, mCurrentStandard.name())
                .putString(KEY_SIZE, mCurrentSize.name())
                .putString(KEY_MAT, mCurrentMaterial.name())
                .putBoolean(KEY_UNIT, mIsInchUnit)
                .apply();
    }

    private void initViews() {
        tvAppTitle = findViewById(R.id.tvAppTitle);
        btnSignoffLogs = findViewById(R.id.btnSignoffLogs);
        btnSpecsDetail = findViewById(R.id.btnSpecsDetail);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);

        btnStdSae45Double = findViewById(R.id.btnStdSae45Double);
        btnStdSae45Single = findViewById(R.id.btnStdSae45Single);
        btnStdAn37 = findViewById(R.id.btnStdAn37);
        btnStdDinBubble = findViewById(R.id.btnStdDinBubble);

        btnSize316 = findViewById(R.id.btnSize316);
        btnSize14 = findViewById(R.id.btnSize14);
        btnSize516 = findViewById(R.id.btnSize516);
        btnSize38 = findViewById(R.id.btnSize38);
        btnSize12 = findViewById(R.id.btnSize12);
        btnSize58 = findViewById(R.id.btnSize58);
        btnSize34 = findViewById(R.id.btnSize34);

        btnMatCuni = findViewById(R.id.btnMatCuni);
        btnMatSoftCopper = findViewById(R.id.btnMatSoftCopper);
        btnMatSteel = findViewById(R.id.btnMatSteel);
        btnMatStainless = findViewById(R.id.btnMatStainless);
        btnMatHardCopper = findViewById(R.id.btnMatHardCopper);
        btnMatAluminum = findViewById(R.id.btnMatAluminum);

        llMaterialAlert = findViewById(R.id.llMaterialAlert);
        tvMaterialAlertTitle = findViewById(R.id.tvMaterialAlertTitle);
        tvMaterialAlertBody = findViewById(R.id.tvMaterialAlertBody);

        tvProfileSubtitle = findViewById(R.id.tvProfileSubtitle);
        flareProfileView = findViewById(R.id.flareProfileView);

        tvSpecFlareOdMm = findViewById(R.id.tvSpecFlareOdMm);
        tvSpecFlareOdIn = findViewById(R.id.tvSpecFlareOdIn);
        tvSpecStickoutMm = findViewById(R.id.tvSpecStickoutMm);
        tvSpecStickoutFrac = findViewById(R.id.tvSpecStickoutFrac);
        tvSpecTorqueNm = findViewById(R.id.tvSpecTorqueNm);
        tvSpecTorqueFtLb = findViewById(R.id.tvSpecTorqueFtLb);
        tvSpecThread = findViewById(R.id.tvSpecThread);
        tvSpecFft = findViewById(R.id.tvSpecFft);

        etCaliperInput = findViewById(R.id.etCaliperInput);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        btnVerify = findViewById(R.id.btnVerify);
        llVerificationResult = findViewById(R.id.llVerificationResult);
        tvResultBadge = findViewById(R.id.tvResultBadge);
        tvResultDetail = findViewById(R.id.tvResultDetail);

        llSignoffContainer = findViewById(R.id.llSignoffContainer);
        etJobRef = findViewById(R.id.etJobRef);
        btnSaveSignoff = findViewById(R.id.btnSaveSignoff);

        btnUnitToggle.setText(mIsInchUnit ? "in" : "mm");
        etCaliperInput.setHint(mIsInchUnit ? "e.g. 0.285" : "e.g. 7.25");
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleTheme();
        });

        btnSpecsDetail.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            startActivity(new Intent(this, DetailActivity.class));
        });

        btnSignoffLogs.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showSignoffLogsDialog();
        });

        // Standard Chips
        btnStdSae45Double.setOnClickListener(v -> selectStandard(FlareSpec.Standard.SAE_45_DOUBLE));
        btnStdSae45Single.setOnClickListener(v -> selectStandard(FlareSpec.Standard.SAE_45_SINGLE));
        btnStdAn37.setOnClickListener(v -> selectStandard(FlareSpec.Standard.AN_37_JIC));
        btnStdDinBubble.setOnClickListener(v -> selectStandard(FlareSpec.Standard.DIN_BUBBLE));

        // Size Chips
        btnSize316.setOnClickListener(v -> selectSize(FlareSpec.TubingSize.SIZE_3_16));
        btnSize14.setOnClickListener(v -> selectSize(FlareSpec.TubingSize.SIZE_1_4));
        btnSize516.setOnClickListener(v -> selectSize(FlareSpec.TubingSize.SIZE_5_16));
        btnSize38.setOnClickListener(v -> selectSize(FlareSpec.TubingSize.SIZE_3_8));
        btnSize12.setOnClickListener(v -> selectSize(FlareSpec.TubingSize.SIZE_1_2));
        btnSize58.setOnClickListener(v -> selectSize(FlareSpec.TubingSize.SIZE_5_8));
        btnSize34.setOnClickListener(v -> selectSize(FlareSpec.TubingSize.SIZE_3_4));

        // Material Chips
        btnMatCuni.setOnClickListener(v -> selectMaterial(FlareSpec.Material.CUNI));
        btnMatSoftCopper.setOnClickListener(v -> selectMaterial(FlareSpec.Material.SOFT_COPPER));
        btnMatSteel.setOnClickListener(v -> selectMaterial(FlareSpec.Material.STEEL_BUNDY));
        btnMatStainless.setOnClickListener(v -> selectMaterial(FlareSpec.Material.STAINLESS_304));
        btnMatHardCopper.setOnClickListener(v -> selectMaterial(FlareSpec.Material.HARD_COPPER));
        btnMatAluminum.setOnClickListener(v -> selectMaterial(FlareSpec.Material.ALUMINUM));

        // Caliper Controls
        btnUnitToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            mIsInchUnit = !mIsInchUnit;
            btnUnitToggle.setText(mIsInchUnit ? "in" : "mm");
            etCaliperInput.setHint(mIsInchUnit ? "e.g. 0.285" : "e.g. 7.25");
            savePreferences();
            runCaliperCheck(false);
        });

        btnVerify.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            dismissKeyboard();
            runCaliperCheck(true);
        });

        etCaliperInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                dismissKeyboard();
                runCaliperCheck(true);
                return true;
            }
            return false;
        });

        etCaliperInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s == null || s.toString().trim().isEmpty()) {
                    flareProfileView.setMeasuredCaliper(null);
                    llVerificationResult.setVisibility(View.GONE);
                    llSignoffContainer.setVisibility(View.GONE);
                }
            }
        });

        btnSaveSignoff.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveSignoffRecord();
        });
    }

    private void selectStandard(FlareSpec.Standard std) {
        mCurrentStandard = std;
        savePreferences();
        refreshAllSpecs();
    }

    private void selectSize(FlareSpec.TubingSize size) {
        mCurrentSize = size;
        savePreferences();
        refreshAllSpecs();
    }

    private void selectMaterial(FlareSpec.Material mat) {
        mCurrentMaterial = mat;
        savePreferences();
        refreshAllSpecs();
    }

    private void updateChipSelection(Button chip, boolean isSelected) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_pill_selected);
            chip.setTextColor(getColor(R.color.m3_on_primary));
        } else {
            chip.setBackgroundResource(R.drawable.bg_pill_unselected);
            chip.setTextColor(getColor(R.color.m3_on_surface));
        }
    }

    private void refreshAllSpecs() {
        mCurrentSpec = FlareSpec.getSpec(mCurrentStandard, mCurrentSize, mCurrentMaterial);

        // Update Standard Chips
        updateChipSelection(btnStdSae45Double, mCurrentStandard == FlareSpec.Standard.SAE_45_DOUBLE);
        updateChipSelection(btnStdSae45Single, mCurrentStandard == FlareSpec.Standard.SAE_45_SINGLE);
        updateChipSelection(btnStdAn37, mCurrentStandard == FlareSpec.Standard.AN_37_JIC);
        updateChipSelection(btnStdDinBubble, mCurrentStandard == FlareSpec.Standard.DIN_BUBBLE);

        // Update Size Chips
        updateChipSelection(btnSize316, mCurrentSize == FlareSpec.TubingSize.SIZE_3_16);
        updateChipSelection(btnSize14, mCurrentSize == FlareSpec.TubingSize.SIZE_1_4);
        updateChipSelection(btnSize516, mCurrentSize == FlareSpec.TubingSize.SIZE_5_16);
        updateChipSelection(btnSize38, mCurrentSize == FlareSpec.TubingSize.SIZE_3_8);
        updateChipSelection(btnSize12, mCurrentSize == FlareSpec.TubingSize.SIZE_1_2);
        updateChipSelection(btnSize58, mCurrentSize == FlareSpec.TubingSize.SIZE_5_8);
        updateChipSelection(btnSize34, mCurrentSize == FlareSpec.TubingSize.SIZE_3_4);

        // Update Material Chips
        updateChipSelection(btnMatCuni, mCurrentMaterial == FlareSpec.Material.CUNI);
        updateChipSelection(btnMatSoftCopper, mCurrentMaterial == FlareSpec.Material.SOFT_COPPER);
        updateChipSelection(btnMatSteel, mCurrentMaterial == FlareSpec.Material.STEEL_BUNDY);
        updateChipSelection(btnMatStainless, mCurrentMaterial == FlareSpec.Material.STAINLESS_304);
        updateChipSelection(btnMatHardCopper, mCurrentMaterial == FlareSpec.Material.HARD_COPPER);
        updateChipSelection(btnMatAluminum, mCurrentMaterial == FlareSpec.Material.ALUMINUM);

        // Metallurgy Alert
        if (mCurrentMaterial.requiresAnneal) {
            llMaterialAlert.setVisibility(View.VISIBLE);
            llMaterialAlert.setBackgroundResource(R.drawable.bg_badge_fail);
            tvMaterialAlertTitle.setText("METALLURGY WARNING: HARD DRAWN COPPER");
            tvMaterialAlertBody.setText(mCurrentMaterial.tip);
        } else if (mCurrentMaterial == FlareSpec.Material.STAINLESS_304) {
            llMaterialAlert.setVisibility(View.VISIBLE);
            llMaterialAlert.setBackgroundResource(R.drawable.bg_badge_warn);
            tvMaterialAlertTitle.setText("WORK-HARDENING ALLOY: 304 STAINLESS");
            tvMaterialAlertBody.setText(mCurrentMaterial.tip);
        } else {
            llMaterialAlert.setVisibility(View.GONE);
        }

        // Subtitle & Visual HUD
        String subtitle = String.format("%s • %s OD • %s",
                mCurrentStandard.displayName, mCurrentSize.fraction, mCurrentMaterial.displayName);
        tvProfileSubtitle.setText(subtitle);
        flareProfileView.updateSpec(mCurrentStandard, mCurrentSize, mCurrentMaterial);

        // Telemetry Readouts
        double minIn = mCurrentSpec.flareOdMinMm / 25.4;
        double maxIn = mCurrentSpec.flareOdMaxMm / 25.4;
        tvSpecFlareOdMm.setText(String.format(Locale.US, "%.2f - %.2f mm", mCurrentSpec.flareOdMinMm, mCurrentSpec.flareOdMaxMm));
        tvSpecFlareOdIn.setText(String.format(Locale.US, "%.3f - %.3f in", minIn, maxIn));

        tvSpecStickoutMm.setText(String.format(Locale.US, "%.1f - %.1f mm", mCurrentSpec.stickoutMinMm, mCurrentSpec.stickoutMaxMm));
        tvSpecStickoutFrac.setText(String.format("%s stickout depth", mCurrentSpec.stickoutFraction));

        tvSpecTorqueNm.setText(String.format(Locale.US, "%d - %d N·m", mCurrentSpec.torqueMinNm, mCurrentSpec.torqueMaxNm));
        tvSpecTorqueFtLb.setText(String.format(Locale.US, "%d - %d ft·lb", mCurrentSpec.torqueMinFtLb, mCurrentSpec.torqueMaxFtLb));

        tvSpecThread.setText(mCurrentSpec.threadSpec);
        tvSpecFft.setText(mCurrentSpec.fftTurns);

        // Re-evaluate caliper if value already typed
        if (!TextUtils.isEmpty(etCaliperInput.getText())) {
            runCaliperCheck(false);
        }
    }

    private void runCaliperCheck(boolean showToastIfEmpty) {
        String raw = etCaliperInput.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            flareProfileView.setMeasuredCaliper(null);
            llVerificationResult.setVisibility(View.GONE);
            llSignoffContainer.setVisibility(View.GONE);
            if (showToastIfEmpty) {
                Toast.makeText(this, "Enter measured flare caliper diameter", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        double val;
        try {
            val = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid caliper numeric format", Toast.LENGTH_SHORT).show();
            return;
        }

        double measuredMm = mIsInchUnit ? (val * 25.4) : val;
        mLastResult = FlareSpec.verifyCaliper(measuredMm, mCurrentSpec);
        flareProfileView.setMeasuredCaliper(measuredMm);

        llVerificationResult.setVisibility(View.VISIBLE);
        llSignoffContainer.setVisibility(View.VISIBLE);

        tvResultBadge.setText(mLastResult.headline);
        tvResultDetail.setText(mLastResult.detail);

        if (mLastResult.status == FlareSpec.VerificationResult.Status.PASS) {
            llVerificationResult.setBackgroundResource(R.drawable.bg_badge_pass);
            tvResultDetail.setTextColor(0xFFDCFCE7);
        } else if (mLastResult.status == FlareSpec.VerificationResult.Status.MARGINAL) {
            llVerificationResult.setBackgroundResource(R.drawable.bg_badge_warn);
            tvResultDetail.setTextColor(0xFFFFEDD5);
        } else {
            llVerificationResult.setBackgroundResource(R.drawable.bg_badge_fail);
            tvResultDetail.setTextColor(0xFFFFE4E6);
        }
    }

    private void saveSignoffRecord() {
        if (mLastResult == null) {
            Toast.makeText(this, "Run caliper check first before logging", Toast.LENGTH_SHORT).show();
            return;
        }

        String jobRef = etJobRef.getText().toString().trim();
        if (jobRef.isEmpty()) {
            jobRef = "Shop Inspection";
        }

        String torqueRange = String.format(Locale.US, "%d-%d Nm (%d-%d ft-lb)",
                mCurrentSpec.torqueMinNm, mCurrentSpec.torqueMaxNm,
                mCurrentSpec.torqueMinFtLb, mCurrentSpec.torqueMaxFtLb);

        long id = mDbHelper.insertSignoff(
                jobRef,
                mCurrentStandard.displayName,
                mCurrentSize.fraction,
                mCurrentMaterial.displayName,
                mLastResult.measuredMm,
                mCurrentSpec.flareOdMinMm,
                mCurrentSpec.flareOdMaxMm,
                mLastResult.status.name(),
                mLastResult.headline,
                torqueRange
        );

        if (id > 0) {
            Toast.makeText(this, "Inspection signoff saved to log (#" + id + ")", Toast.LENGTH_SHORT).show();
            etJobRef.setText("");
            dismissKeyboard();
        } else {
            Toast.makeText(this, "Error saving inspection signoff", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSignoffLogsDialog() {
        List<DatabaseHelper.InspectionRecord> records = mDbHelper.getAllSignoffs();
        if (records.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Inspection Signoff Log")
                    .setMessage("No inspection records logged yet.\n\nEnter a caliper measurement and tap 'Sign Off' to log field QA.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (DatabaseHelper.InspectionRecord r : records) {
            sb.append("• [").append(r.statusText).append("] ").append(r.jobRef).append("\n");
            sb.append("   ").append(r.standard).append(" | ").append(r.size).append(" | ").append(r.material).append("\n");
            sb.append(String.format(Locale.US, "   Measured: %.2f mm (Nom: %.2f-%.2f mm) | %s\n",
                    r.measuredOd, r.minOd, r.maxOd, r.timestamp));
            sb.append("--------------------------------------------------\n");
        }

        final String reportText = sb.toString();

        new AlertDialog.Builder(this)
                .setTitle("Inspection Logs (" + records.size() + ")")
                .setMessage(reportText)
                .setPositiveButton("Close", null)
                .setNeutralButton("Share Report", (dialog, which) -> {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, "=== FlareGage Inspection Signoffs ===\n\n" + reportText);
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, "Share Field Signoffs"));
                })
                .setNegativeButton("Clear All", (dialog, which) -> {
                    mDbHelper.clearAllSignoffs();
                    Toast.makeText(MainActivity.this, "Signoff logs cleared", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void cycleTheme() {
        mThemeMode = (mThemeMode + 1) % 3;
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME_MODE, mThemeMode).apply();
        recreate();
    }

    private void updateThemeUI() {
        if (mThemeMode == 0) {
            btnThemeToggle.setText("Auto");
        } else if (mThemeMode == 1) {
            btnThemeToggle.setText("Light");
        } else {
            btnThemeToggle.setText("Dark");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isLight = (mThemeMode == 1) || (mThemeMode == 0 && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES);
                controller.setSystemBarsAppearance(
                        isLight ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        }
    }

    private void dismissKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                runCaliperCheck(true);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
