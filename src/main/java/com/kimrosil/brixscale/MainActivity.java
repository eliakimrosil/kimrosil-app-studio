package com.kimrosil.brixscale;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "brix_scale_prefs";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String KEY_UNIT_OUNCES = "unit_ounces";
    private static final String KEY_HAPTICS = "haptics_enabled";
    private static final String KEY_ACTIVE_TAB = "active_tab";
    private static final String KEY_METHOD_NAME = "method_name";
    private static final String KEY_DRAFT_DOSE = "draft_dose";
    private static final String KEY_DRAFT_RATIO = "draft_ratio";
    private static final String KEY_DRAFT_TDS = "draft_tds";
    private static final String KEY_DRAFT_RET = "draft_ret";
    private static final String KEY_DRAFT_BEAN = "draft_bean";

    private SharedPreferences mPrefs;
    private BrixDatabaseHelper mDbHelper;

    private ScrollView mMainScrollView;
    private ImageButton mBtnThemeToggle;
    private ImageButton mBtnSettings;

    // Hero HUD
    private TextView mTvActiveMethodBadge;
    private TextView mTvExtractionZoneBadge;
    private TextView mTvHeroWaterWeight;
    private TextView mTvHeroBeverageSub;
    private TextView mTvHeroEyValue;
    private TextView mTvHeroTdsValue;
    private ExtractionChartView mChartExtraction;

    // Navigation Tabs
    private Button mBtnTabRatio;
    private Button mBtnTabLab;
    private Button mBtnTabLog;
    private LinearLayout mSectionRatio;
    private LinearLayout mSectionLab;
    private LinearLayout mSectionLog;

    // Method Presets
    private Button mBtnPresetV60;
    private Button mBtnPresetChemex;
    private Button mBtnPresetAero;
    private Button mBtnPresetEspresso;

    // Tab 1 Controls
    private TextView mTvLabelDose;
    private Button mBtnDoseMinus;
    private Button mBtnDosePlus;
    private EditText mEtCoffeeDose;
    private Button mBtnRatioMinus;
    private Button mBtnRatioPlus;
    private EditText mEtBrewRatio;
    private Button mBtnCalcRecipe;
    private Button mBtnQuickSaveLog;

    // 4-Stage Pour Pacer & Timer
    private TextView mTvChronoReadout;
    private TextView mTvActiveStageCue;
    private TextView mTvStage1Label;
    private TextView mTvStage1Target;
    private TextView mTvStage2Label;
    private TextView mTvStage2Target;
    private TextView mTvStage3Label;
    private TextView mTvStage3Target;
    private TextView mTvStage4Label;
    private TextView mTvStage4Target;
    private Button mBtnTimerToggle;
    private Button mBtnTimerNextStage;
    private Button mBtnTimerReset;

    // Tab 2 Controls (Refractometer Lab)
    private Button mBtnTdsMinus;
    private Button mBtnTdsPlus;
    private EditText mEtMeasuredTds;
    private Button mBtnRetMinus;
    private Button mBtnRetPlus;
    private EditText mEtRetentionRatio;
    private TextView mTvLabBrixOut;
    private TextView mTvLabSolublesMass;
    private TextView mTvLabBypassWater;
    private TextView mTvLabDialAdvice;
    private Button mBtnCopyLabReport;

    // Tab 3 Controls (Brew Log)
    private EditText mEtRoastBeanName;
    private Button mBtnSaveBeanLog;
    private LinearLayout mLlSavedLogsContainer;

    // State
    private int mCurrentTab = 0;
    private String mActiveMethod = "V60 CONE";
    private boolean mUseOunces = false;
    private boolean mHapticsEnabled = true;
    private boolean mIsUpdatingInputs = false;

    private BrixEngine.BrewResult mLastResult = new BrixEngine.BrewResult();

    // Chronometer State
    private final Handler mTimerHandler = new Handler(Looper.getMainLooper());
    private boolean mTimerRunning = false;
    private long mTimerStartRealtime = 0L;
    private long mTimerAccumulatedMs = 0L;
    private int mManualStageOverride = 0; // 0 = auto by elapsed seconds, 1..4 = manual stage

    private final Runnable mTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mTimerRunning) return;
            long elapsedMs = mTimerAccumulatedMs + (SystemClock.elapsedRealtime() - mTimerStartRealtime);
            int totalSec = (int) (elapsedMs / 1000L);
            if (totalSec > 3599) {
                totalSec = 3599;
            }
            updateChronometerUI(totalSec);
            mTimerHandler.postDelayed(this, 200L);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = sp.getInt(KEY_THEME_MODE, 0);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        if (overrideConfiguration != null) {
            int mode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_THEME_MODE, 0);
            if (mode == 1) {
                overrideConfiguration.uiMode = (overrideConfiguration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            } else if (mode == 2) {
                overrideConfiguration.uiMode = (overrideConfiguration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            }
        }
        super.applyOverrideConfiguration(overrideConfiguration);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int mode = mPrefs.getInt(KEY_THEME_MODE, 0);
        if (mode == 1) {
            setTheme(R.style.AppTheme_Light);
        } else if (mode == 2) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDbHelper = new BrixDatabaseHelper(this);
        mUseOunces = mPrefs.getBoolean(KEY_UNIT_OUNCES, false);
        mHapticsEnabled = mPrefs.getBoolean(KEY_HAPTICS, true);
        mCurrentTab = mPrefs.getInt(KEY_ACTIVE_TAB, 0);
        mActiveMethod = mPrefs.getString(KEY_METHOD_NAME, "V60 CONE");

        bindViews();

        boolean isDark = isDarkActive(mode);
        mChartExtraction.updateTheme(isDark);

        restoreDraftInputs(savedInstanceState);
        setupListeners();
        selectTab(mCurrentTab);
        updateMethodPresetVisuals();
        recalculateAll();
        renderSavedLogs();
    }

    private boolean isDarkActive(int mode) {
        if (mode == 2) return true;
        if (mode == 1) return false;
        int nightMask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMask == Configuration.UI_MODE_NIGHT_YES;
    }

    private void bindViews() {
        mMainScrollView = findViewById(R.id.mainScrollView);
        mBtnThemeToggle = findViewById(R.id.btnThemeToggle);
        mBtnSettings = findViewById(R.id.btnSettings);

        mTvActiveMethodBadge = findViewById(R.id.tvActiveMethodBadge);
        mTvExtractionZoneBadge = findViewById(R.id.tvExtractionZoneBadge);
        mTvHeroWaterWeight = findViewById(R.id.tvHeroWaterWeight);
        mTvHeroBeverageSub = findViewById(R.id.tvHeroBeverageSub);
        mTvHeroEyValue = findViewById(R.id.tvHeroEyValue);
        mTvHeroTdsValue = findViewById(R.id.tvHeroTdsValue);
        mChartExtraction = findViewById(R.id.chartExtraction);

        mBtnTabRatio = findViewById(R.id.btnTabRatio);
        mBtnTabLab = findViewById(R.id.btnTabLab);
        mBtnTabLog = findViewById(R.id.btnTabLog);
        mSectionRatio = findViewById(R.id.sectionRatio);
        mSectionLab = findViewById(R.id.sectionLab);
        mSectionLog = findViewById(R.id.sectionLog);

        mBtnPresetV60 = findViewById(R.id.btnPresetV60);
        mBtnPresetChemex = findViewById(R.id.btnPresetChemex);
        mBtnPresetAero = findViewById(R.id.btnPresetAero);
        mBtnPresetEspresso = findViewById(R.id.btnPresetEspresso);

        mTvLabelDose = findViewById(R.id.tvLabelDose);
        mBtnDoseMinus = findViewById(R.id.btnDoseMinus);
        mBtnDosePlus = findViewById(R.id.btnDosePlus);
        mEtCoffeeDose = findViewById(R.id.etCoffeeDose);
        mBtnRatioMinus = findViewById(R.id.btnRatioMinus);
        mBtnRatioPlus = findViewById(R.id.btnRatioPlus);
        mEtBrewRatio = findViewById(R.id.etBrewRatio);
        mBtnCalcRecipe = findViewById(R.id.btnCalcRecipe);
        mBtnQuickSaveLog = findViewById(R.id.btnQuickSaveLog);

        mTvChronoReadout = findViewById(R.id.tvChronoReadout);
        mTvActiveStageCue = findViewById(R.id.tvActiveStageCue);
        mTvStage1Label = findViewById(R.id.tvStage1Label);
        mTvStage1Target = findViewById(R.id.tvStage1Target);
        mTvStage2Label = findViewById(R.id.tvStage2Label);
        mTvStage2Target = findViewById(R.id.tvStage2Target);
        mTvStage3Label = findViewById(R.id.tvStage3Label);
        mTvStage3Target = findViewById(R.id.tvStage3Target);
        mTvStage4Label = findViewById(R.id.tvStage4Label);
        mTvStage4Target = findViewById(R.id.tvStage4Target);
        mBtnTimerToggle = findViewById(R.id.btnTimerToggle);
        mBtnTimerNextStage = findViewById(R.id.btnTimerNextStage);
        mBtnTimerReset = findViewById(R.id.btnTimerReset);

        mBtnTdsMinus = findViewById(R.id.btnTdsMinus);
        mBtnTdsPlus = findViewById(R.id.btnTdsPlus);
        mEtMeasuredTds = findViewById(R.id.etMeasuredTds);
        mBtnRetMinus = findViewById(R.id.btnRetMinus);
        mBtnRetPlus = findViewById(R.id.btnRetPlus);
        mEtRetentionRatio = findViewById(R.id.etRetentionRatio);
        mTvLabBrixOut = findViewById(R.id.tvLabBrixOut);
        mTvLabSolublesMass = findViewById(R.id.tvLabSolublesMass);
        mTvLabBypassWater = findViewById(R.id.tvLabBypassWater);
        mTvLabDialAdvice = findViewById(R.id.tvLabDialAdvice);
        mBtnCopyLabReport = findViewById(R.id.btnCopyLabReport);

        mEtRoastBeanName = findViewById(R.id.etRoastBeanName);
        mBtnSaveBeanLog = findViewById(R.id.btnSaveBeanLog);
        mLlSavedLogsContainer = findViewById(R.id.llSavedLogsContainer);
    }

    private void restoreDraftInputs(Bundle savedInstanceState) {
        mIsUpdatingInputs = true;
        String dose = mPrefs.getString(KEY_DRAFT_DOSE, "18.0");
        String ratio = mPrefs.getString(KEY_DRAFT_RATIO, "16.0");
        String tds = mPrefs.getString(KEY_DRAFT_TDS, "1.38");
        String ret = mPrefs.getString(KEY_DRAFT_RET, "2.0");
        String bean = mPrefs.getString(KEY_DRAFT_BEAN, "Ethiopia Washed V60");

        if (savedInstanceState != null) {
            dose = savedInstanceState.getString(KEY_DRAFT_DOSE, dose);
            ratio = savedInstanceState.getString(KEY_DRAFT_RATIO, ratio);
            tds = savedInstanceState.getString(KEY_DRAFT_TDS, tds);
            ret = savedInstanceState.getString(KEY_DRAFT_RET, ret);
            bean = savedInstanceState.getString(KEY_DRAFT_BEAN, bean);
            mCurrentTab = savedInstanceState.getInt(KEY_ACTIVE_TAB, mCurrentTab);
        } else {
            // On cold boot, sanitize any out-of-range fuzz strings left in prefs
            double dVal = BrixEngine.parseSafeDouble(dose, 18.0, 5.0, 120.0);
            if (dose.length() > 6 || dVal < 5.0 || dVal > 120.0) {
                dose = "18.0";
            }
            double rVal = BrixEngine.parseSafeDouble(ratio, 16.0, 1.0, 30.0);
            if (ratio.length() > 6 || rVal < 1.0 || rVal > 30.0) {
                ratio = "16.0";
            }
        }

        mEtCoffeeDose.setText(dose);
        mEtBrewRatio.setText(ratio);
        mEtMeasuredTds.setText(tds);
        mEtRetentionRatio.setText(ret);
        mEtRoastBeanName.setText(bean);
        mIsUpdatingInputs = false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTIVE_TAB, mCurrentTab);
        outState.putString(KEY_DRAFT_DOSE, mEtCoffeeDose.getText().toString());
        outState.putString(KEY_DRAFT_RATIO, mEtBrewRatio.getText().toString());
        outState.putString(KEY_DRAFT_TDS, mEtMeasuredTds.getText().toString());
        outState.putString(KEY_DRAFT_RET, mEtRetentionRatio.getText().toString());
        outState.putString(KEY_DRAFT_BEAN, mEtRoastBeanName.getText().toString());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && mMainScrollView != null && mEtCoffeeDose != null) {
            mMainScrollView.post(() -> {
                int targetY = Math.max(0, mSectionRatio.getTop() - dpToPx(20));
                mMainScrollView.scrollTo(0, targetY);
            });
        }
    }

    private void setupListeners() {
        mBtnThemeToggle.setOnClickListener(v -> {
            haptic(v);
            toggleThemeDirect();
        });

        mBtnSettings.setOnClickListener(v -> {
            haptic(v);
            showSettingsDialog();
        });

        // Navigation Tabs
        mBtnTabRatio.setOnClickListener(v -> {
            haptic(v);
            selectTab(0);
        });
        mBtnTabLab.setOnClickListener(v -> {
            haptic(v);
            selectTab(1);
        });
        mBtnTabLog.setOnClickListener(v -> {
            haptic(v);
            selectTab(2);
        });

        // Brew Method Presets
        mBtnPresetV60.setOnClickListener(v -> {
            haptic(v);
            applyMethodPreset("V60 CONE", 18.0, 16.0, 1.38, 2.0);
        });
        mBtnPresetChemex.setOnClickListener(v -> {
            haptic(v);
            applyMethodPreset("CHEMEX CARAFE", 22.0, 17.0, 1.32, 2.1);
        });
        mBtnPresetAero.setOnClickListener(v -> {
            haptic(v);
            applyMethodPreset("AEROPRESS", 15.0, 13.0, 1.48, 1.6);
        });
        mBtnPresetEspresso.setOnClickListener(v -> {
            haptic(v);
            applyMethodPreset("ESPRESSO 9-BAR", 18.5, 2.0, 10.0, 1.0);
        });

        // Steppers: Dose
        mBtnDoseMinus.setOnClickListener(v -> {
            haptic(v);
            stepInput(mEtCoffeeDose, -0.5, 1.0, 500.0, "%.1f");
        });
        mBtnDosePlus.setOnClickListener(v -> {
            haptic(v);
            stepInput(mEtCoffeeDose, 0.5, 1.0, 500.0, "%.1f");
        });

        // Steppers: Ratio
        mBtnRatioMinus.setOnClickListener(v -> {
            haptic(v);
            stepInput(mEtBrewRatio, -0.5, 1.0, 40.0, "%.1f");
        });
        mBtnRatioPlus.setOnClickListener(v -> {
            haptic(v);
            stepInput(mEtBrewRatio, 0.5, 1.0, 40.0, "%.1f");
        });

        // Steppers: TDS
        mBtnTdsMinus.setOnClickListener(v -> {
            haptic(v);
            stepInput(mEtMeasuredTds, -0.02, 0.10, 18.0, "%.2f");
        });
        mBtnTdsPlus.setOnClickListener(v -> {
            haptic(v);
            stepInput(mEtMeasuredTds, 0.02, 0.10, 18.0, "%.2f");
        });

        // Steppers: Retention
        mBtnRetMinus.setOnClickListener(v -> {
            haptic(v);
            stepInput(mEtRetentionRatio, -0.1, 0.2, 4.0, "%.1f");
        });
        mBtnRetPlus.setOnClickListener(v -> {
            haptic(v);
            stepInput(mEtRetentionRatio, 0.1, 0.2, 4.0, "%.1f");
        });

        // TextWatchers for live calculation & draft state saving
        TextWatcher liveWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (mIsUpdatingInputs) return;
                saveDraftsToPrefs();
                recalculateAll();
            }
        };

        mEtCoffeeDose.addTextChangedListener(liveWatcher);
        mEtBrewRatio.addTextChangedListener(liveWatcher);
        mEtMeasuredTds.addTextChangedListener(liveWatcher);
        mEtRetentionRatio.addTextChangedListener(liveWatcher);
        mEtRoastBeanName.addTextChangedListener(liveWatcher);

        // Keep primary action buttons un-occluded above soft keyboard when inputs are focused
        View.OnFocusChangeListener focusScroller = (v, hasFocus) -> {
            if (hasFocus && mMainScrollView != null) {
                mMainScrollView.postDelayed(() -> {
                    int scrollY = Math.max(0, mSectionRatio.getTop() - dpToPx(24));
                    mMainScrollView.smoothScrollTo(0, scrollY);
                }, 120L);
            }
        };
        mEtCoffeeDose.setOnFocusChangeListener(focusScroller);
        mEtBrewRatio.setOnFocusChangeListener(focusScroller);

        // Primary Action Buttons
        mBtnCalcRecipe.setOnClickListener(v -> {
            haptic(v);
            recalculateAll();
            Toast.makeText(this, "Pour schedule calibrated (" + String.format(Locale.US, "%.0f g water", mLastResult.totalWaterGrams) + ")", Toast.LENGTH_SHORT).show();
        });

        mBtnQuickSaveLog.setOnClickListener(v -> {
            haptic(v);
            saveCurrentBrewToDatabase();
        });

        mBtnSaveBeanLog.setOnClickListener(v -> {
            haptic(v);
            saveCurrentBrewToDatabase();
        });

        mBtnCopyLabReport.setOnClickListener(v -> {
            haptic(v);
            copyBrewReportToClipboard();
        });

        // Chronometer Controls
        mBtnTimerToggle.setOnClickListener(v -> {
            haptic(v);
            toggleTimer();
        });
        mBtnTimerNextStage.setOnClickListener(v -> {
            haptic(v);
            advanceStageManually();
        });
        mBtnTimerReset.setOnClickListener(v -> {
            haptic(v);
            resetTimer();
        });
    }

    private void saveDraftsToPrefs() {
        mPrefs.edit()
                .putString(KEY_DRAFT_DOSE, mEtCoffeeDose.getText().toString())
                .putString(KEY_DRAFT_RATIO, mEtBrewRatio.getText().toString())
                .putString(KEY_DRAFT_TDS, mEtMeasuredTds.getText().toString())
                .putString(KEY_DRAFT_RET, mEtRetentionRatio.getText().toString())
                .putString(KEY_DRAFT_BEAN, mEtRoastBeanName.getText().toString())
                .apply();
    }

    private void stepInput(EditText et, double delta, double min, double max, String fmt) {
        double current = BrixEngine.parseSafeDouble(et.getText().toString(), min, min, max);
        double next = Math.max(min, Math.min(max, current + delta));
        mIsUpdatingInputs = true;
        String formatted = String.format(Locale.US, fmt, next);
        et.setText(formatted);
        et.setSelection(formatted.length());
        mIsUpdatingInputs = false;
        saveDraftsToPrefs();
        recalculateAll();
    }

    private void applyMethodPreset(String methodTitle, double dose, double ratio, double tds, double retention) {
        mActiveMethod = methodTitle;
        mPrefs.edit().putString(KEY_METHOD_NAME, methodTitle).apply();
        mIsUpdatingInputs = true;
        mEtCoffeeDose.setText(String.format(Locale.US, "%.1f", dose));
        mEtBrewRatio.setText(String.format(Locale.US, "%.1f", ratio));
        mEtMeasuredTds.setText(String.format(Locale.US, "%.2f", tds));
        mEtRetentionRatio.setText(String.format(Locale.US, "%.1f", retention));
        mIsUpdatingInputs = false;
        saveDraftsToPrefs();
        updateMethodPresetVisuals();
        recalculateAll();
    }

    private void updateMethodPresetVisuals() {
        styleChip(mBtnPresetV60, mActiveMethod.startsWith("V60"));
        styleChip(mBtnPresetChemex, mActiveMethod.startsWith("CHEMEX"));
        styleChip(mBtnPresetAero, mActiveMethod.startsWith("AERO"));
        styleChip(mBtnPresetEspresso, mActiveMethod.startsWith("ESPRESSO") || mActiveMethod.startsWith("ESP"));
    }

    private void selectTab(int tabIndex) {
        mCurrentTab = tabIndex;
        mPrefs.edit().putInt(KEY_ACTIVE_TAB, tabIndex).apply();

        mSectionRatio.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        mSectionLab.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        mSectionLog.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        styleChip(mBtnTabRatio, tabIndex == 0);
        styleChip(mBtnTabLab, tabIndex == 1);
        styleChip(mBtnTabLog, tabIndex == 2);
    }

    private void styleChip(Button btn, boolean selected) {
        btn.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btn.setTextColor(getColor(selected ? R.color.m3_on_primary : R.color.m3_on_surface));
    }

    private void recalculateAll() {
        double dose = BrixEngine.parseSafeDouble(mEtCoffeeDose.getText().toString(), 18.0, 1.0, 500.0);
        double ratio = BrixEngine.parseSafeDouble(mEtBrewRatio.getText().toString(), 16.0, 1.0, 40.0);
        double tds = BrixEngine.parseSafeDouble(mEtMeasuredTds.getText().toString(), 1.38, 0.10, 18.0);
        double ret = BrixEngine.parseSafeDouble(mEtRetentionRatio.getText().toString(), 2.0, 0.1, 4.0);

        mLastResult = BrixEngine.compute(dose, ratio, tds, ret);

        // Hero Header Badges
        mTvActiveMethodBadge.setText(String.format(Locale.US, "%s • 1:%.1f", mActiveMethod, mLastResult.brewRatio));
        mTvExtractionZoneBadge.setText(mLastResult.zoneLabel);

        if (mLastResult.zoneStatus == 0) {
            mTvExtractionZoneBadge.setTextColor(getColor(R.color.brix_emerald));
            mTvHeroEyValue.setTextColor(getColor(R.color.brix_emerald));
        } else if (mLastResult.zoneStatus == 1) {
            mTvExtractionZoneBadge.setTextColor(getColor(R.color.brix_amber));
            mTvHeroEyValue.setTextColor(getColor(R.color.brix_amber));
        } else {
            mTvExtractionZoneBadge.setTextColor(getColor(R.color.brix_ruby));
            mTvHeroEyValue.setTextColor(getColor(R.color.brix_ruby));
        }

        // Unit formatting (Metric g vs US oz)
        mTvLabelDose.setText(mUseOunces ? "COFFEE DOSE (g)" : "COFFEE DOSE (g)");
        if (mUseOunces) {
            double waterOz = mLastResult.totalWaterGrams / 28.3495;
            double yieldOz = mLastResult.beverageYieldGrams / 28.3495;
            mTvHeroWaterWeight.setText(String.format(Locale.US, "%.1f oz", waterOz));
            mTvHeroBeverageSub.setText(String.format(Locale.US, "Yield: %.1f oz (%.0f g) • Dose: %.1f g", yieldOz, mLastResult.beverageYieldGrams, mLastResult.doseGrams));
        } else {
            mTvHeroWaterWeight.setText(String.format(Locale.US, "%.1f g", mLastResult.totalWaterGrams));
            mTvHeroBeverageSub.setText(String.format(Locale.US, "Yield: %.1f g • Dose: %.1f g", mLastResult.beverageYieldGrams, mLastResult.doseGrams));
        }

        mTvHeroEyValue.setText(String.format(Locale.US, "%.1f%%", mLastResult.extractionYieldPercent));
        mTvHeroTdsValue.setText(String.format(Locale.US, "%.2f%% / %.1f°", mLastResult.tdsPercent, mLastResult.brixDegrees));

        // Update Custom SCA Extraction Chart
        mChartExtraction.setExtractionData(mLastResult.extractionYieldPercent, mLastResult.tdsPercent);

        // Update 4-Stage Pour Targets
        boolean isEspresso = mLastResult.brewRatio < 6.0;
        if (isEspresso) {
            mTvStage1Label.setText("1. Pre-Infusion (0:00–0:08)");
            mTvStage2Label.setText("2. Pressure Ramp (0:08–0:16)");
            mTvStage3Label.setText("3. Heart Body Pull (0:16–0:24)");
            mTvStage4Label.setText("4. Blonding Cut (0:24–0:30)");
        } else {
            mTvStage1Label.setText("1. Degas Bloom (0:00–0:45)");
            mTvStage2Label.setText("2. Sweet Acidity Pour (0:45–1:15)");
            mTvStage3Label.setText("3. Spiral Body Pour (1:15–1:55)");
            mTvStage4Label.setText("4. Final Drawdown (1:55–3:00)");
        }

        mTvStage1Target.setText(String.format(Locale.US, "%.1f g (%d%%)", mLastResult.stage1Grams, mLastResult.stage1Pct));
        mTvStage2Target.setText(String.format(Locale.US, "%.1f g (%d%%)", mLastResult.stage2Grams, mLastResult.stage2Pct));
        mTvStage3Target.setText(String.format(Locale.US, "%.1f g (%d%%)", mLastResult.stage3Grams, mLastResult.stage3Pct));
        mTvStage4Target.setText(String.format(Locale.US, "%.1f g (%d%%)", mLastResult.stage4Grams, mLastResult.stage4Pct));

        int currentSec = (int) (mTimerAccumulatedMs / 1000L);
        updateChronometerUI(currentSec);

        // Update Lab Readouts
        mTvLabBrixOut.setText(String.format(Locale.US, "%.2f °Bx", mLastResult.brixDegrees));
        mTvLabSolublesMass.setText(String.format(Locale.US, "%.2f g", mLastResult.dissolvedSolidsGrams));
        if (mLastResult.bypassWaterGrams > 0.5) {
            mTvLabBypassWater.setText(String.format(Locale.US, "+%.1f g water", mLastResult.bypassWaterGrams));
        } else {
            mTvLabBypassWater.setText("0.0 g (On Target)");
        }
        mTvLabDialAdvice.setText(mLastResult.dialInAdvice);
    }

    private void toggleTimer() {
        if (mTimerRunning) {
            mTimerAccumulatedMs += (SystemClock.elapsedRealtime() - mTimerStartRealtime);
            mTimerRunning = false;
            mTimerHandler.removeCallbacks(mTimerRunnable);
            mBtnTimerToggle.setText("RESUME POUR TIMER");
        } else {
            mTimerStartRealtime = SystemClock.elapsedRealtime();
            mTimerRunning = true;
            mManualStageOverride = 0;
            mBtnTimerToggle.setText("PAUSE POUR TIMER");
            mTimerHandler.post(mTimerRunnable);
        }
    }

    private void advanceStageManually() {
        mManualStageOverride = (mManualStageOverride % 4) + 1;
        int sec = (int) ((mTimerRunning
                ? mTimerAccumulatedMs + (SystemClock.elapsedRealtime() - mTimerStartRealtime)
                : mTimerAccumulatedMs) / 1000L);
        updateChronometerUI(sec);
    }

    private void resetTimer() {
        mTimerRunning = false;
        mTimerHandler.removeCallbacks(mTimerRunnable);
        mTimerAccumulatedMs = 0L;
        mManualStageOverride = 0;
        mBtnTimerToggle.setText("START POUR TIMER");
        updateChronometerUI(0);
    }

    private void updateChronometerUI(int elapsedSec) {
        int mins = elapsedSec / 60;
        int secs = elapsedSec % 60;
        mTvChronoReadout.setText(String.format(Locale.US, "%02d:%02d", mins, secs));

        int activeStage;
        if (mManualStageOverride > 0) {
            activeStage = mManualStageOverride;
        } else if (mLastResult.brewRatio < 6.0) {
            if (elapsedSec < 8) activeStage = 1;
            else if (elapsedSec < 16) activeStage = 2;
            else if (elapsedSec < 24) activeStage = 3;
            else activeStage = 4;
        } else {
            if (elapsedSec < 45) activeStage = 1;
            else if (elapsedSec < 75) activeStage = 2;
            else if (elapsedSec < 115) activeStage = 3;
            else activeStage = 4;
        }

        if (activeStage == 1) {
            mTvActiveStageCue.setText(String.format(Locale.US, "STAGE 1 (BLOOM): Pour to %.1f g & swirl gently", mLastResult.stage1Grams));
        } else if (activeStage == 2) {
            mTvActiveStageCue.setText(String.format(Locale.US, "STAGE 2 (CENTER POUR): Pour steady to %.1f g", mLastResult.stage2Grams));
        } else if (activeStage == 3) {
            mTvActiveStageCue.setText(String.format(Locale.US, "STAGE 3 (SPIRAL BODY): Pour circles to %.1f g", mLastResult.stage3Grams));
        } else {
            mTvActiveStageCue.setText(String.format(Locale.US, "STAGE 4 (FINAL RINSE): Finish at %.1f g & drawdown", mLastResult.stage4Grams));
        }
    }

    private void saveCurrentBrewToDatabase() {
        String bean = mEtRoastBeanName.getText().toString().trim();
        if (bean.isEmpty()) {
            bean = mActiveMethod + " Session";
        }
        mDbHelper.insertLog(
                bean,
                mActiveMethod,
                mLastResult.doseGrams,
                mLastResult.brewRatio,
                mLastResult.totalWaterGrams,
                mLastResult.tdsPercent,
                mLastResult.extractionYieldPercent
        );
        renderSavedLogs();
        Toast.makeText(this, "Saved to Brew Log: " + bean, Toast.LENGTH_SHORT).show();
    }

    private void renderSavedLogs() {
        mLlSavedLogsContainer.removeAllViews();
        List<BrixDatabaseHelper.BrewLogEntry> logs = mDbHelper.getAllLogs();

        for (final BrixDatabaseHelper.BrewLogEntry entry : logs) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card);
            int pad = dpToPx(10);
            row.setPadding(pad, pad, pad, pad);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowLp.bottomMargin = dpToPx(8);
            row.setLayoutParams(rowLp);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textCol.setLayoutParams(colLp);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(entry.beanName + " (" + entry.method + ")");
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvTitle.setTextColor(getColor(R.color.m3_on_surface));

            TextView tvDetails = new TextView(this);
            tvDetails.setText(String.format(Locale.US,
                    "Dose: %.1fg • Water: %.0fg (1:%.1f) • TDS: %.2f%% • EY: %.1f%%",
                    entry.doseGrams, entry.waterGrams, entry.ratio, entry.tdsPercent, entry.eyPercent));
            tvDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            tvDetails.setTextColor(getColor(R.color.m3_on_surface_variant));

            textCol.addView(tvTitle);
            textCol.addView(tvDetails);

            Button btnLoad = new Button(this);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dpToPx(68), dpToPx(48));
            btnLp.leftMargin = dpToPx(6);
            btnLoad.setLayoutParams(btnLp);
            btnLoad.setMinWidth(dpToPx(48));
            btnLoad.setMinHeight(dpToPx(48));
            btnLoad.setText("LOAD");
            btnLoad.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            btnLoad.setTypeface(Typeface.DEFAULT_BOLD);
            btnLoad.setBackgroundResource(R.drawable.bg_btn_secondary);
            btnLoad.setTextColor(getColor(R.color.m3_primary));
            btnLoad.setOnClickListener(v -> {
                haptic(v);
                applyMethodPreset(entry.method, entry.doseGrams, entry.ratio, entry.tdsPercent, 2.0);
                selectTab(0);
                Toast.makeText(this, "Loaded " + entry.beanName, Toast.LENGTH_SHORT).show();
            });

            Button btnDel = new Button(this);
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48));
            delLp.leftMargin = dpToPx(6);
            btnDel.setLayoutParams(delLp);
            btnDel.setMinWidth(dpToPx(48));
            btnDel.setMinHeight(dpToPx(48));
            btnDel.setText("×");
            btnDel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            btnDel.setTypeface(Typeface.DEFAULT_BOLD);
            btnDel.setBackgroundResource(R.drawable.bg_stepper);
            btnDel.setTextColor(getColor(R.color.brix_ruby));
            btnDel.setOnClickListener(v -> {
                haptic(v);
                mDbHelper.deleteLog(entry.id);
                renderSavedLogs();
            });

            row.addView(textCol);
            row.addView(btnLoad);
            row.addView(btnDel);
            mLlSavedLogsContainer.addView(row);
        }
    }

    private void copyBrewReportToClipboard() {
        String report = String.format(Locale.US,
                "BrixScale Extraction Report (%s)\n" +
                        "• Dose: %.1f g | Ratio: 1:%.1f | Water: %.1f g\n" +
                        "• Beverage Yield: %.1f g | Retention: %.1f mL/g\n" +
                        "• Strength: %.2f%% TDS (%.2f °Brix)\n" +
                        "• Extraction Yield: %.1f%% (%s)\n" +
                        "• Guidance: %s",
                mActiveMethod,
                mLastResult.doseGrams,
                mLastResult.brewRatio,
                mLastResult.totalWaterGrams,
                mLastResult.beverageYieldGrams,
                mLastResult.retentionRatio,
                mLastResult.tdsPercent,
                mLastResult.brixDegrees,
                mLastResult.extractionYieldPercent,
                mLastResult.zoneLabel,
                mLastResult.dialInAdvice
        );

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("BrixScale Report", report));
            Toast.makeText(this, "Extraction report copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleThemeDirect() {
        int currentMode = mPrefs.getInt(KEY_THEME_MODE, 0);
        boolean currentlyDark = isDarkActive(currentMode);
        int nextMode = currentlyDark ? 1 : 2; // Flip directly between Light (1) and Dark (2)
        mPrefs.edit().putInt(KEY_THEME_MODE, nextMode).apply();
        recreate();
    }

    private void showSettingsDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_settings);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnThemeLight = dialog.findViewById(R.id.btnThemeLight);
        Button btnThemeDark = dialog.findViewById(R.id.btnThemeDark);
        Button btnThemeAuto = dialog.findViewById(R.id.btnThemeAuto);
        Button btnUnitGrams = dialog.findViewById(R.id.btnUnitGrams);
        Button btnUnitOunces = dialog.findViewById(R.id.btnUnitOunces);
        Switch switchHaptics = dialog.findViewById(R.id.switchHaptics);
        Button btnPrivacyPolicy = dialog.findViewById(R.id.btnPrivacyPolicy);
        Button btnResetDefaults = dialog.findViewById(R.id.btnResetDefaults);
        Button btnCloseSettings = dialog.findViewById(R.id.btnCloseSettings);

        int mode = mPrefs.getInt(KEY_THEME_MODE, 0);
        styleChip(btnThemeLight, mode == 1);
        styleChip(btnThemeDark, mode == 2);
        styleChip(btnThemeAuto, mode == 0);

        styleChip(btnUnitGrams, !mUseOunces);
        styleChip(btnUnitOunces, mUseOunces);
        switchHaptics.setChecked(mHapticsEnabled);

        btnThemeLight.setOnClickListener(v -> {
            haptic(v);
            mPrefs.edit().putInt(KEY_THEME_MODE, 1).apply();
            dialog.dismiss();
            recreate();
        });

        btnThemeDark.setOnClickListener(v -> {
            haptic(v);
            mPrefs.edit().putInt(KEY_THEME_MODE, 2).apply();
            dialog.dismiss();
            recreate();
        });

        btnThemeAuto.setOnClickListener(v -> {
            haptic(v);
            mPrefs.edit().putInt(KEY_THEME_MODE, 0).apply();
            dialog.dismiss();
            recreate();
        });

        btnUnitGrams.setOnClickListener(v -> {
            haptic(v);
            mUseOunces = false;
            mPrefs.edit().putBoolean(KEY_UNIT_OUNCES, false).apply();
            styleChip(btnUnitGrams, true);
            styleChip(btnUnitOunces, false);
            recalculateAll();
        });

        btnUnitOunces.setOnClickListener(v -> {
            haptic(v);
            mUseOunces = true;
            mPrefs.edit().putBoolean(KEY_UNIT_OUNCES, true).apply();
            styleChip(btnUnitGrams, false);
            styleChip(btnUnitOunces, true);
            recalculateAll();
        });

        switchHaptics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mHapticsEnabled = isChecked;
            mPrefs.edit().putBoolean(KEY_HAPTICS, isChecked).apply();
        });

        btnPrivacyPolicy.setOnClickListener(v -> {
            haptic(v);
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com/brix-scale/privacy.html"));
                startActivity(intent);
            } catch (Exception ignored) {}
        });

        btnResetDefaults.setOnClickListener(v -> {
            haptic(v);
            resetTimer();
            applyMethodPreset("V60 CONE", 18.0, 16.0, 1.38, 2.0);
            dialog.dismiss();
            Toast.makeText(this, "Restored V60 1:16 Gold Cup defaults", Toast.LENGTH_SHORT).show();
        });

        btnCloseSettings.setOnClickListener(v -> {
            haptic(v);
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_PLUS || code == KeyEvent.KEYCODE_EQUALS) {
                stepInput(mEtCoffeeDose, 0.5, 1.0, 500.0, "%.1f");
                return true;
            } else if (code == KeyEvent.KEYCODE_MINUS) {
                stepInput(mEtCoffeeDose, -0.5, 1.0, 500.0, "%.1f");
                return true;
            } else if (code == KeyEvent.KEYCODE_ESCAPE) {
                resetTimer();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        mTimerHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void haptic(View v) {
        if (mHapticsEnabled && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
