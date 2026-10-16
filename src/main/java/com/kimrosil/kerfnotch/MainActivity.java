package com.kimrosil.kerfnotch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "kerf_notch_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_UNIT_MODE = "pref_unit_mode"; // 0=Fractional, 1=Decimal Inch, 2=Metric mm
    private static final String KEY_MODE = "pref_active_mode"; // 0=Dado, 1=Box Joint
    private static final String KEY_TARGET_KERF = "pref_target_kerf";
    private static final String KEY_BOARD_WIDTH = "pref_board_width";
    private static final String KEY_BLADE_KERF = "pref_blade_kerf";

    // Hardware safety thresholds
    private static final double MIN_DADO_INCH = 0.250; // 1/4" outside blade pair
    private static final double MAX_DADO_INCH = 0.8125; // 13/16" safe arbor thread depth limit

    private SharedPreferences mPrefs;
    private DadoStackDbHelper mDbHelper;

    // State Variables
    private int mThemeMode = 0;
    private int mUnitMode = 0; // 0=Frac, 1=Dec, 2=Mm
    private int mWorkMode = 0; // 0=Dado Stack, 1=Box Joint

    private double mTargetKerfInch = 0.71875; // 23/32" nominal plywood
    private double mBoardWidthInch = 5.500;   // 1x6 nominal board
    private double mBladeKerfInch = 0.125;    // 1/8" standard blade

    // Views
    private ScrollView mScrollView;
    private Button btnUnitToggle;
    private Button btnThemeToggle;
    private Button btnSavedPresets;

    private DadoJointPreviewView dadoPreviewView;
    private TextView tvWarningBanner;

    private Button btnModeDado;
    private Button btnModeBoxJoint;

    private LinearLayout layoutDadoControls;
    private LinearLayout layoutBoxJointControls;

    // Quick Chips
    private Button chipPly14;
    private Button chipPly12;
    private Button chipPly2332;
    private Button chipPly34;

    // Steppers & Inputs
    private EditText etTargetKerf;
    private TextView tvTargetKerfUnit;
    private Button btnDecTarget;
    private Button btnIncTarget;

    private EditText etBoardWidth;
    private TextView tvBoardWidthUnit;
    private Button btnDecBoard;
    private Button btnIncBoard;

    private EditText etBladeKerf;
    private TextView tvBladeKerfUnit;
    private Button btnDecBlade;
    private Button btnIncBlade;

    private Button btnCalculate;
    private Button btnSavePreset;
    private Button btnShareSetup;

    // Readout Cards & Containers
    private LinearLayout cardDadoResults;
    private LinearLayout cardBoxJointResults;
    private LinearLayout llShimBreakdownContainer;

    private TextView tvPrimaryTargetReadout;
    private TextView tvSecondaryTargetReadout;
    private TextView tvPrimaryDeviationReadout;
    private TextView tvSecondaryDeviationReadout;
    private TextView tvChipperSummaryReadout;

    private TextView tvBoxJointPinsReadout;
    private TextView tvBoxJointSocketsReadout;
    private TextView tvBoxJointFingerWidthReadout;
    private TextView tvBoxJointPassesReadout;
    private TextView tvBoxJointStepOffReadout;

    private DadoStackSolver.DadoRecipe mLastDadoRecipe;
    private BoxJointCalculator.BoxJointResult mLastBoxJointResult;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = sp.getInt(KEY_THEME_MODE, 0);

        int nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
        if (themeMode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            nightMode = (systemNight == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mDbHelper = new DadoStackDbHelper(this);

        loadSavedState();
        initializeViews();
        setupListeners();
        updateThemeButtonLabel();
        updateUnitButtonLabel();
        applyModeSelection(false);
        triggerRecalculate(false);
        setupWindowInsets();
    }

    private void setupWindowInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
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

    private void loadSavedState() {
        mThemeMode = mPrefs.getInt(KEY_THEME_MODE, 0);
        mUnitMode = mPrefs.getInt(KEY_UNIT_MODE, 0);
        mWorkMode = mPrefs.getInt(KEY_MODE, 0);
        mTargetKerfInch = Double.longBitsToDouble(mPrefs.getLong(KEY_TARGET_KERF, Double.doubleToLongBits(0.71875)));
        mBoardWidthInch = Double.longBitsToDouble(mPrefs.getLong(KEY_BOARD_WIDTH, Double.doubleToLongBits(5.500)));
        mBladeKerfInch = Double.longBitsToDouble(mPrefs.getLong(KEY_BLADE_KERF, Double.doubleToLongBits(0.125)));
    }

    private void saveCurrentState() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt(KEY_THEME_MODE, mThemeMode);
        editor.putInt(KEY_UNIT_MODE, mUnitMode);
        editor.putInt(KEY_MODE, mWorkMode);
        editor.putLong(KEY_TARGET_KERF, Double.doubleToLongBits(mTargetKerfInch));
        editor.putLong(KEY_BOARD_WIDTH, Double.doubleToLongBits(mBoardWidthInch));
        editor.putLong(KEY_BLADE_KERF, Double.doubleToLongBits(mBladeKerfInch));
        editor.apply();
    }

    private void initializeViews() {
        mScrollView = findViewById(R.id.scrollContainer);
        btnUnitToggle = findViewById(R.id.btnUnitToggle);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSavedPresets = findViewById(R.id.btnSavedPresets);

        dadoPreviewView = findViewById(R.id.dadoPreviewView);
        tvWarningBanner = findViewById(R.id.tvWarningBanner);

        btnModeDado = findViewById(R.id.btnModeDado);
        btnModeBoxJoint = findViewById(R.id.btnModeBoxJoint);

        layoutDadoControls = findViewById(R.id.layoutDadoControls);
        layoutBoxJointControls = findViewById(R.id.layoutBoxJointControls);

        chipPly14 = findViewById(R.id.chipPly14);
        chipPly12 = findViewById(R.id.chipPly12);
        chipPly2332 = findViewById(R.id.chipPly2332);
        chipPly34 = findViewById(R.id.chipPly34);

        etTargetKerf = findViewById(R.id.etTargetKerf);
        tvTargetKerfUnit = findViewById(R.id.tvTargetKerfUnit);
        btnDecTarget = findViewById(R.id.btnDecTarget);
        btnIncTarget = findViewById(R.id.btnIncTarget);

        etBoardWidth = findViewById(R.id.etBoardWidth);
        tvBoardWidthUnit = findViewById(R.id.tvBoardWidthUnit);
        btnDecBoard = findViewById(R.id.btnDecBoard);
        btnIncBoard = findViewById(R.id.btnIncBoard);

        etBladeKerf = findViewById(R.id.etBladeKerf);
        tvBladeKerfUnit = findViewById(R.id.tvBladeKerfUnit);
        btnDecBlade = findViewById(R.id.btnDecBlade);
        btnIncBlade = findViewById(R.id.btnIncBlade);

        btnCalculate = findViewById(R.id.btnCalculate);
        btnSavePreset = findViewById(R.id.btnSavePreset);
        btnShareSetup = findViewById(R.id.btnShareSetup);

        cardDadoResults = findViewById(R.id.cardDadoResults);
        cardBoxJointResults = findViewById(R.id.cardBoxJointResults);
        llShimBreakdownContainer = findViewById(R.id.llShimBreakdownContainer);

        tvPrimaryTargetReadout = findViewById(R.id.tvPrimaryTargetReadout);
        tvSecondaryTargetReadout = findViewById(R.id.tvSecondaryTargetReadout);
        tvPrimaryDeviationReadout = findViewById(R.id.tvPrimaryDeviationReadout);
        tvSecondaryDeviationReadout = findViewById(R.id.tvSecondaryDeviationReadout);
        tvChipperSummaryReadout = findViewById(R.id.tvChipperSummaryReadout);

        tvBoxJointPinsReadout = findViewById(R.id.tvBoxJointPinsReadout);
        tvBoxJointSocketsReadout = findViewById(R.id.tvBoxJointSocketsReadout);
        tvBoxJointFingerWidthReadout = findViewById(R.id.tvBoxJointFingerWidthReadout);
        tvBoxJointPassesReadout = findViewById(R.id.tvBoxJointPassesReadout);
        tvBoxJointStepOffReadout = findViewById(R.id.tvBoxJointStepOffReadout);

        populateInputFields();
    }

    private void populateInputFields() {
        if (mUnitMode == 2) {
            etTargetKerf.setText(String.format(Locale.US, "%.2f", mTargetKerfInch * 25.4));
            tvTargetKerfUnit.setText("mm");
            etBoardWidth.setText(String.format(Locale.US, "%.1f", mBoardWidthInch * 25.4));
            tvBoardWidthUnit.setText("mm");
            etBladeKerf.setText(String.format(Locale.US, "%.2f", mBladeKerfInch * 25.4));
            tvBladeKerfUnit.setText("mm");
        } else {
            etTargetKerf.setText(String.format(Locale.US, "%.4f", mTargetKerfInch));
            tvTargetKerfUnit.setText("in");
            etBoardWidth.setText(String.format(Locale.US, "%.3f", mBoardWidthInch));
            tvBoardWidthUnit.setText("in");
            etBladeKerf.setText(String.format(Locale.US, "%.4f", mBladeKerfInch));
            tvBladeKerfUnit.setText("in");
        }
    }

    private void setupListeners() {
        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });

        btnUnitToggle.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            cycleUnitMode();
        });

        btnSavedPresets.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            showSavedPresetsDialog();
        });

        btnModeDado.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            mWorkMode = 0;
            saveCurrentState();
            applyModeSelection(true);
        });

        btnModeBoxJoint.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            mWorkMode = 1;
            saveCurrentState();
            applyModeSelection(true);
        });

        // Quick Preset Chips for Nominal Plywood
        chipPly14.setOnClickListener(v -> setPresetKerf(0.250, "1/4\" Nominal"));
        chipPly12.setOnClickListener(v -> setPresetKerf(0.46875, "15/32\" (1/2\" Plywood)"));
        chipPly2332.setOnClickListener(v -> setPresetKerf(0.71875, "23/32\" (3/4\" Plywood)"));
        chipPly34.setOnClickListener(v -> setPresetKerf(0.750, "3/4\" Hardwood"));

        // Steppers
        btnDecTarget.setOnClickListener(v -> stepTargetKerf(-1));
        btnIncTarget.setOnClickListener(v -> stepTargetKerf(1));

        btnDecBoard.setOnClickListener(v -> stepBoardWidth(-1));
        btnIncBoard.setOnClickListener(v -> stepBoardWidth(1));

        btnDecBlade.setOnClickListener(v -> stepBladeKerf(-1));
        btnIncBlade.setOnClickListener(v -> stepBladeKerf(1));

        // Keyboard Done Actions
        setupImeAction(etTargetKerf);
        setupImeAction(etBoardWidth);
        setupImeAction(etBladeKerf);

        btnCalculate.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            hideSoftKeyboard();
            triggerRecalculate(true);
        });

        btnSavePreset.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            promptSavePreset();
        });

        btnShareSetup.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            shareCurrentRecipe();
        });

        dadoPreviewView.setOnSliceSelectedListener((component, thickness) -> {
            String desc = component + ": " + FractionFormatter.format(thickness, mUnitMode);
            Toast.makeText(MainActivity.this, desc, Toast.LENGTH_SHORT).show();
        });
    }

    private void setupImeAction(EditText editText) {
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                hideSoftKeyboard();
                triggerRecalculate(true);
                return true;
            }
            return false;
        });
    }

    private void hideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View focused = getCurrentFocus();
        if (imm != null && focused != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private void stepTargetKerf(int direction) {
        hideSoftKeyboard();
        double step = (mUnitMode == 2) ? (1.0 / 25.4) : 0.015625; // 1mm or 1/64"
        parseInputsFromFields();
        mTargetKerfInch += direction * step;
        mTargetKerfInch = Math.max(MIN_DADO_INCH, Math.min(MAX_DADO_INCH + 0.125, mTargetKerfInch));
        populateInputFields();
        triggerRecalculate(true);
        performHapticTick();
    }

    private void stepBoardWidth(int direction) {
        hideSoftKeyboard();
        double step = (mUnitMode == 2) ? (5.0 / 25.4) : 0.125; // 5mm or 1/8"
        parseInputsFromFields();
        mBoardWidthInch += direction * step;
        mBoardWidthInch = Math.max(0.5, Math.min(48.0, mBoardWidthInch));
        populateInputFields();
        triggerRecalculate(true);
        performHapticTick();
    }

    private void stepBladeKerf(int direction) {
        hideSoftKeyboard();
        double step = (mUnitMode == 2) ? (0.2 / 25.4) : 0.0078125; // ~1/128"
        parseInputsFromFields();
        mBladeKerfInch += direction * step;
        mBladeKerfInch = Math.max(0.0625, Math.min(0.250, mBladeKerfInch));
        populateInputFields();
        triggerRecalculate(true);
        performHapticTick();
    }

    private void setPresetKerf(double kerfInch, String label) {
        mTargetKerfInch = kerfInch;
        populateInputFields();
        triggerRecalculate(true);
        performHapticHeavy();
        Toast.makeText(this, "Preset applied: " + label, Toast.LENGTH_SHORT).show();
    }

    private void applyModeSelection(boolean animate) {
        if (mWorkMode == 0) {
            btnModeDado.setBackgroundResource(R.drawable.chip_active);
            btnModeDado.setTextColor(getColorSafe(R.color.m3_on_primary));
            btnModeBoxJoint.setBackgroundResource(R.drawable.chip_inactive);
            btnModeBoxJoint.setTextColor(getColorSafe(R.color.m3_on_surface));

            layoutDadoControls.setVisibility(View.VISIBLE);
            layoutBoxJointControls.setVisibility(View.GONE);

            cardDadoResults.setVisibility(View.VISIBLE);
            cardBoxJointResults.setVisibility(View.GONE);
            dadoPreviewView.setMode(DadoJointPreviewView.MODE_DADO_STACK);
        } else {
            btnModeBoxJoint.setBackgroundResource(R.drawable.chip_active);
            btnModeBoxJoint.setTextColor(getColorSafe(R.color.m3_on_primary));
            btnModeDado.setBackgroundResource(R.drawable.chip_inactive);
            btnModeDado.setTextColor(getColorSafe(R.color.m3_on_surface));

            layoutDadoControls.setVisibility(View.GONE);
            layoutBoxJointControls.setVisibility(View.VISIBLE);

            cardDadoResults.setVisibility(View.GONE);
            cardBoxJointResults.setVisibility(View.VISIBLE);
            dadoPreviewView.setMode(DadoJointPreviewView.MODE_BOX_JOINT);
        }
    }

    private int getColorSafe(int colorRes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getColor(colorRes);
        }
        return getResources().getColor(colorRes);
    }

    private void cycleThemeMode() {
        mThemeMode = (mThemeMode + 1) % 3;
        mPrefs.edit().putInt(KEY_THEME_MODE, mThemeMode).apply();
        recreate();
    }

    private void updateThemeButtonLabel() {
        if (mThemeMode == 1) {
            btnThemeToggle.setText("Light");
        } else if (mThemeMode == 2) {
            btnThemeToggle.setText("Dark");
        } else {
            btnThemeToggle.setText("Auto");
        }
    }

    private void cycleUnitMode() {
        mUnitMode = (mUnitMode + 1) % 3;
        saveCurrentState();
        updateUnitButtonLabel();
        populateInputFields();
        triggerRecalculate(false);
    }

    private void updateUnitButtonLabel() {
        if (mUnitMode == 1) {
            btnUnitToggle.setText("Dec In");
        } else if (mUnitMode == 2) {
            btnUnitToggle.setText("Metric");
        } else {
            btnUnitToggle.setText("Frac In");
        }
    }

    private void parseInputsFromFields() {
        try {
            String targetStr = etTargetKerf.getText().toString().trim();
            if (!TextUtils.isEmpty(targetStr)) {
                double val = Double.parseDouble(targetStr);
                mTargetKerfInch = (mUnitMode == 2) ? (val / 25.4) : val;
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            String boardStr = etBoardWidth.getText().toString().trim();
            if (!TextUtils.isEmpty(boardStr)) {
                double val = Double.parseDouble(boardStr);
                mBoardWidthInch = (mUnitMode == 2) ? (val / 25.4) : val;
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            String bladeStr = etBladeKerf.getText().toString().trim();
            if (!TextUtils.isEmpty(bladeStr)) {
                double val = Double.parseDouble(bladeStr);
                mBladeKerfInch = (mUnitMode == 2) ? (val / 25.4) : val;
            }
        } catch (NumberFormatException ignored) {
        }

        // Defensive guardrails
        mTargetKerfInch = Math.max(MIN_DADO_INCH, mTargetKerfInch);
        mBoardWidthInch = Math.max(0.5, mBoardWidthInch);
        mBladeKerfInch = Math.max(0.0625, mBladeKerfInch);
    }

    private void triggerRecalculate(boolean triggerHaptic) {
        parseInputsFromFields();
        saveCurrentState();

        if (mWorkMode == 0) {
            // Safety check for arbor capacity
            if (mTargetKerfInch > MAX_DADO_INCH) {
                tvWarningBanner.setVisibility(View.VISIBLE);
                tvWarningBanner.setText(String.format(Locale.US,
                        "SAFETY HAZARD: Target width %.4f\" exceeds safe arbor capacity (13/16\" or 0.8125\"). Arbor nut will not fully engage threads!",
                        mTargetKerfInch));
            } else if (mTargetKerfInch < MIN_DADO_INCH) {
                tvWarningBanner.setVisibility(View.VISIBLE);
                tvWarningBanner.setText(String.format(Locale.US,
                        "MINIMUM LIMIT: Stack kerf cannot be narrower than two outside blades (0.250\" or 1/4\").",
                        mTargetKerfInch));
            } else {
                tvWarningBanner.setVisibility(View.GONE);
            }

            mLastDadoRecipe = DadoStackSolver.solve(mTargetKerfInch);
            updateDadoReadouts(mLastDadoRecipe);
            dadoPreviewView.setDadoRecipe(mLastDadoRecipe);

            if (triggerHaptic) {
                if (Math.abs(mLastDadoRecipe.deviationInch) < 0.001) {
                    performHapticHeavy();
                } else {
                    performHapticTick();
                }
            }
        } else {
            tvWarningBanner.setVisibility(View.GONE);
            mLastBoxJointResult = BoxJointCalculator.calculate(mBoardWidthInch, mBladeKerfInch);
            updateBoxJointReadouts(mLastBoxJointResult);
            dadoPreviewView.setBoxJointResult(mLastBoxJointResult);

            if (triggerHaptic) {
                performHapticTick();
            }
        }
    }

    private void updateDadoReadouts(DadoStackSolver.DadoRecipe recipe) {
        if (recipe == null) return;

        // Primary Target Stack
        tvPrimaryTargetReadout.setText(FractionFormatter.format(recipe.totalKerfInch, mUnitMode));
        tvSecondaryTargetReadout.setText(FractionFormatter.formatSecondary(recipe.totalKerfInch, mUnitMode));

        // Deviation
        double dev = recipe.deviationInch;
        String sign = (dev > 0) ? "+" : "";
        tvPrimaryDeviationReadout.setText(sign + String.format(Locale.US, "%.4f\"", dev));
        tvSecondaryDeviationReadout.setText(String.format(Locale.US, "%s%.3f mm", sign, dev * 25.4));

        if (Math.abs(dev) <= 0.0005) {
            tvPrimaryDeviationReadout.setTextColor(getColorSafe(R.color.m3_success));
        } else if (Math.abs(dev) <= 0.002) {
            tvPrimaryDeviationReadout.setTextColor(getColorSafe(R.color.m3_primary));
        } else {
            tvPrimaryDeviationReadout.setTextColor(getColorSafe(R.color.m3_error));
        }

        // Chipper Summary
        tvChipperSummaryReadout.setText(recipe.getChipperSummary());

        // Render Shim Breakdown Badges
        renderShimBadges(recipe);
    }

    private void renderShimBadges(DadoStackSolver.DadoRecipe recipe) {
        llShimBreakdownContainer.removeAllViews();

        if (recipe.shims.isEmpty()) {
            TextView tvNone = new TextView(this);
            tvNone.setText("No fine shims needed (exact chipper match)");
            tvNone.setTextSize(13);
            tvNone.setTextColor(getColorSafe(R.color.m3_on_surface_variant));
            llShimBreakdownContainer.addView(tvNone);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (DadoStackSolver.ShimEntry entry : recipe.shims) {
            View badge = inflater.inflate(R.layout.item_shim_badge, llShimBreakdownContainer, false);
            View colorPip = badge.findViewById(R.id.viewShimColorPip);
            TextView tvTitle = badge.findViewById(R.id.tvShimTitle);
            TextView tvSub = badge.findViewById(R.id.tvShimSub);

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(entry.colorHex);
            shape.setStroke(2, Color.parseColor("#475569"));
            colorPip.setBackground(shape);

            tvTitle.setText(String.format(Locale.US, "%dx %s (%s)", entry.count, entry.name, entry.colorName));
            tvSub.setText(String.format(Locale.US, "Thickness: %.4f\" / %.3f mm each", entry.thicknessInch, entry.thicknessInch * 25.4));

            llShimBreakdownContainer.addView(badge);
        }
    }

    private void updateBoxJointReadouts(BoxJointCalculator.BoxJointResult result) {
        if (result == null) return;

        tvBoxJointPinsReadout.setText(String.valueOf(result.pinCount));
        tvBoxJointSocketsReadout.setText(String.valueOf(result.socketCount));
        tvBoxJointFingerWidthReadout.setText(FractionFormatter.format(result.fingerWidthInch, mUnitMode));
        tvBoxJointPassesReadout.setText(result.passesPerSocket + " pass" + (result.passesPerSocket > 1 ? "es" : ""));
        tvBoxJointStepOffReadout.setText(FractionFormatter.format(result.stepOffDistanceInch, mUnitMode));
    }

    private void performHapticTick() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(18);
            }
        }
    }

    private void performHapticHeavy() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(55);
            }
        }
    }

    private void promptSavePreset() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Cut Preset");

        final EditText input = new EditText(this);
        input.setHint("Project or material name (e.g. Baltic Birch 18mm)");
        input.setSingleLine(true);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                name = (mWorkMode == 0 ? "Dado " : "Box Joint ") + FractionFormatter.format(mTargetKerfInch, mUnitMode);
            }

            long id;
            if (mWorkMode == 0) {
                id = mDbHelper.insertSetup(name, mTargetKerfInch,
                        mLastDadoRecipe != null ? mLastDadoRecipe.getRecipeDescription() : "",
                        mLastDadoRecipe != null ? mLastDadoRecipe.deviationInch : 0.0);
            } else {
                id = mDbHelper.insertBoxJointPreset(name, mBoardWidthInch, mBladeKerfInch,
                        mLastBoxJointResult != null ? mLastBoxJointResult.fingerWidthInch : 0.0,
                        mLastBoxJointResult != null ? mLastBoxJointResult.pinCount : 0);
            }

            if (id != -1) {
                performHapticHeavy();
                Toast.makeText(MainActivity.this, "Preset saved to workshop database!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Failed to save preset.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showSavedPresetsDialog() {
        final List<DadoStackDbHelper.SavedSetup> setups = (mWorkMode == 0)
                ? mDbHelper.getAllSetups()
                : mDbHelper.getAllBoxJointPresets();

        if (setups.isEmpty()) {
            Toast.makeText(this, "No saved presets for current mode yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] items = new CharSequence[setups.size()];
        for (int i = 0; i < setups.size(); i++) {
            DadoStackDbHelper.SavedSetup s = setups.get(i);
            items[i] = s.name + "\n" + s.details;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(mWorkMode == 0 ? "Saved Dado Stack Setups" : "Saved Box Joint Presets");
        builder.setItems(items, (dialog, which) -> {
            DadoStackDbHelper.SavedSetup selected = setups.get(which);
            if (mWorkMode == 0) {
                mTargetKerfInch = selected.targetKerf;
            } else {
                mBoardWidthInch = selected.targetKerf;
                mBladeKerfInch = selected.deviation;
            }
            populateInputFields();
            triggerRecalculate(true);
            Toast.makeText(MainActivity.this, "Loaded: " + selected.name, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void shareCurrentRecipe() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== KerfNotch Workshop Cut Sheet ===\n");
        if (mWorkMode == 0 && mLastDadoRecipe != null) {
            sb.append("Mode: Dado Stack Cut\n");
            sb.append(String.format(Locale.US, "Target Kerf: %s (%.4f\" / %.2f mm)\n",
                    FractionFormatter.format(mTargetKerfInch, 0), mTargetKerfInch, mTargetKerfInch * 25.4));
            sb.append(String.format(Locale.US, "Actual Kerf: %.4f\" (Deviation: %+.4f\")\n",
                    mLastDadoRecipe.totalKerfInch, mLastDadoRecipe.deviationInch));
            sb.append("Outer Blades: 2x 1/8\" (Carbide)\n");
            sb.append("Chippers: ").append(mLastDadoRecipe.getChipperSummary()).append("\n");
            sb.append("Fine Shims:\n");
            if (mLastDadoRecipe.shims.isEmpty()) {
                sb.append("  - None\n");
            } else {
                for (DadoStackSolver.ShimEntry entry : mLastDadoRecipe.shims) {
                    sb.append(String.format(Locale.US, "  - %dx %s (%s, %.4f\")\n",
                            entry.count, entry.name, entry.colorName, entry.thicknessInch));
                }
            }
        } else if (mLastBoxJointResult != null) {
            sb.append("Mode: Box Joint Finger Spacing\n");
            sb.append(String.format(Locale.US, "Board Width: %.3f\" (%.1f mm)\n",
                    mBoardWidthInch, mBoardWidthInch * 25.4));
            sb.append(String.format(Locale.US, "Blade Kerf: %.4f\" (%.2f mm)\n",
                    mBladeKerfInch, mBladeKerfInch * 25.4));
            sb.append("Pins: ").append(mLastBoxJointResult.pinCount).append("\n");
            sb.append("Sockets: ").append(mLastBoxJointResult.socketCount).append("\n");
            sb.append(String.format(Locale.US, "Finger Width: %.4f\"\n", mLastBoxJointResult.fingerWidthInch));
            sb.append("Passes Per Socket: ").append(mLastBoxJointResult.passesPerSocket).append("\n");
            sb.append(String.format(Locale.US, "Step-off Distance: %.4f\"\n", mLastBoxJointResult.stepOffDistanceInch));
        }

        sb.append("\nCalculated offline with KerfNotch");

        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("KerfNotch Recipe", sb.toString()));
        }

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "Share Cut Setup Sheet");
        startActivity(shareIntent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                hideSoftKeyboard();
                triggerRecalculate(true);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                if (getCurrentFocus() == null || !(getCurrentFocus() instanceof EditText)) {
                    cycleUnitMode();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                mTargetKerfInch = 0.71875;
                mBoardWidthInch = 5.500;
                mBladeKerfInch = 0.125;
                populateInputFields();
                triggerRecalculate(true);
                Toast.makeText(this, "Reset to standard 23/32\" plywood default", Toast.LENGTH_SHORT).show();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserves UI state dynamically during desktop window resizing
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDbHelper != null) {
            mDbHelper.close();
        }
    }
}