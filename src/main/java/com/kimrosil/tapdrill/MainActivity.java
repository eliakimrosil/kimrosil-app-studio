package com.kimrosil.tapdrill;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREF_NAME = "tap_drill_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    private static final String PREF_ACTIVE_STANDARD = "pref_active_standard";
    private static final String STATE_STANDARD = "state_standard";
    private static final String STATE_ENGAGEMENT = "state_engagement";
    private static final String STATE_SEARCH_QUERY = "state_search_query";
    private static final String STATE_ACTIVE_TAB = "state_active_tab";
    private static final String STATE_SELECTED_NOMINAL = "state_selected_nominal";

    private SharedPreferences mPrefs;
    private DatabaseHelper mDbHelper;

    // UI - Header
    private Button mBtnThemeToggle;
    private Button mBtnPinGlance;
    private Button mBtnOverlayHUD;

    // UI - Mode Tabs
    private Button mBtnTabTapDrill;
    private Button mBtnTabSpeedsFeeds;
    private LinearLayout mLlTapDrillSection;
    private LinearLayout mLlSpeedsFeedsSection;
    private int mCurrentModeTab = 0; // 0=Tap & Drill, 1=Speeds & Feeds

    // UI - Standard Tabs
    private Button mBtnStdMetric;
    private Button mBtnStdUncUnf;
    private Button mBtnStdPipe;
    private Button mBtnStdAcme;

    // UI - Search & Selector
    private EditText mEtSearchThread;
    private Button mBtnClearSearch;
    private Spinner mSpnThreadList;

    // UI - Engagement Controls & Canvas
    private ThreadEngagementCanvasView mCvThreadCanvas;
    private Button mBtnEngage50;
    private Button mBtnEngage75;
    private TextView mTvEngagementPercentBadge;

    // UI - Readout Cards
    private LinearLayout mLlEmptyState;
    private LinearLayout mLlResultDeck;
    private TextView mTvSelectedThreadTitle;
    private TextView mTvPitchSpec;
    private TextView mTvDecimalDrillSize;
    private TextView mTvFractionalDrill;
    private TextView mTvWireLetterDrill;
    private TextView mTvClearanceClose;
    private TextView mTvClearanceFree;
    private Button mBtnCopyThreadData;
    private Button mBtnCopyClearanceSpecs;

    // UI - Speeds & Feeds
    private Spinner mSpnWorkpieceMat;
    private Spinner mSpnToolMat;
    private EditText mEtCutterDiameter;
    private Switch mSwDepthAllowance;
    private SpeedFeedCanvasGauge mCvSpeedFeedGauge;
    private TextView mTvCalculatedRpm;
    private TextView mTvCalculatedIpm;
    private TextView mTvSfmMetricVal;
    private EditText mEtTargetThreadDepth;
    private TextView mTvDepthBreakdown118;
    private TextView mTvDepthBreakdown135;
    private Button mBtnCopyGcode;
    private Button mBtnSavePreset;

    // Data Models & State
    private String mCurrentStandard = "METRIC";
    private List<MachiningStandardsRepository.ThreadSpec> mCurrentThreadList = new ArrayList<>();
    private MachiningStandardsRepository.ThreadSpec mSelectedSpec = null;
    private int mCurrentEngagement = 75; // 50% or 75%
    private SpeedsFeedsEngine.WorkpieceMaterial mSelectedWorkpiece = SpeedsFeedsEngine.WorkpieceMaterial.STEEL_1018;
    private SpeedsFeedsEngine.ToolMaterial mSelectedToolMat = SpeedsFeedsEngine.ToolMaterial.HSS;
    private String mSavedNominal = null;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);

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
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        mDbHelper = new DatabaseHelper(this);
        mCurrentStandard = mPrefs.getString(PREF_ACTIVE_STANDARD, "METRIC");

        String restoredSearch = null;
        if (savedInstanceState != null) {
            mCurrentStandard = savedInstanceState.getString(STATE_STANDARD, mCurrentStandard);
            mCurrentEngagement = savedInstanceState.getInt(STATE_ENGAGEMENT, 75);
            restoredSearch = savedInstanceState.getString(STATE_SEARCH_QUERY, null);
            mCurrentModeTab = savedInstanceState.getInt(STATE_ACTIVE_TAB, 0);
            mSavedNominal = savedInstanceState.getString(STATE_SELECTED_NOMINAL, null);
        }

        setupWindowAppearance();
        initViews();
        setupNavigationTabs();
        setupStandardTabs();
        setupThreadSelector();
        setupEngagementControls();
        setupSpeedsFeedsEngineControls();
        setupActionButtons();

        selectStandard(mCurrentStandard);

        if (restoredSearch != null && !restoredSearch.isEmpty()) {
            mEtSearchThread.setText(restoredSearch);
            mEtSearchThread.setSelection(restoredSearch.length());
        }

        setEngagement(mCurrentEngagement);
        switchModeTab(mCurrentModeTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_STANDARD, mCurrentStandard);
        outState.putInt(STATE_ENGAGEMENT, mCurrentEngagement);
        outState.putInt(STATE_ACTIVE_TAB, mCurrentModeTab);
        if (mEtSearchThread != null) {
            outState.putString(STATE_SEARCH_QUERY, mEtSearchThread.getText().toString());
        }
        if (mSelectedSpec != null) {
            outState.putString(STATE_SELECTED_NOMINAL, mSelectedSpec.nominalDisplay);
        }
    }

    private void setupWindowAppearance() {
        int themeMode = mPrefs.getInt(PREF_THEME_MODE, 0);
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
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

    private void initViews() {
        mBtnThemeToggle = findViewById(R.id.btnThemeToggle);
        mBtnPinGlance = findViewById(R.id.btnPinGlance);
        mBtnOverlayHUD = findViewById(R.id.btnOverlayHUD);

        mBtnTabTapDrill = findViewById(R.id.btnTabTapDrill);
        mBtnTabSpeedsFeeds = findViewById(R.id.btnTabSpeedsFeeds);
        mLlTapDrillSection = findViewById(R.id.llTapDrillSection);
        mLlSpeedsFeedsSection = findViewById(R.id.llSpeedsFeedsSection);

        mBtnStdMetric = findViewById(R.id.btnStdMetric);
        mBtnStdUncUnf = findViewById(R.id.btnStdUncUnf);
        mBtnStdPipe = findViewById(R.id.btnStdPipe);
        mBtnStdAcme = findViewById(R.id.btnStdAcme);

        mEtSearchThread = findViewById(R.id.etSearchThread);
        mBtnClearSearch = findViewById(R.id.btnClearSearch);
        mSpnThreadList = findViewById(R.id.spnThreadList);

        mCvThreadCanvas = findViewById(R.id.cvThreadCanvas);
        mBtnEngage50 = findViewById(R.id.btnEngage50);
        mBtnEngage75 = findViewById(R.id.btnEngage75);
        mTvEngagementPercentBadge = findViewById(R.id.tvEngagementPercentBadge);

        mLlEmptyState = findViewById(R.id.llEmptyState);
        mLlResultDeck = findViewById(R.id.llResultDeck);
        mTvSelectedThreadTitle = findViewById(R.id.tvSelectedThreadTitle);
        mTvPitchSpec = findViewById(R.id.tvPitchSpec);
        mTvDecimalDrillSize = findViewById(R.id.tvDecimalDrillSize);
        mTvFractionalDrill = findViewById(R.id.tvFractionalDrill);
        mTvWireLetterDrill = findViewById(R.id.tvWireLetterDrill);
        mTvClearanceClose = findViewById(R.id.tvClearanceClose);
        mTvClearanceFree = findViewById(R.id.tvClearanceFree);
        mBtnCopyThreadData = findViewById(R.id.btnCopyThreadData);
        mBtnCopyClearanceSpecs = findViewById(R.id.btnCopyClearanceSpecs);

        mSpnWorkpieceMat = findViewById(R.id.spnWorkpieceMat);
        mSpnToolMat = findViewById(R.id.spnToolMat);
        mEtCutterDiameter = findViewById(R.id.etCutterDiameter);
        mSwDepthAllowance = findViewById(R.id.swDepthAllowance);
        mCvSpeedFeedGauge = findViewById(R.id.cvSpeedFeedGauge);
        mTvCalculatedRpm = findViewById(R.id.tvCalculatedRpm);
        mTvCalculatedIpm = findViewById(R.id.tvCalculatedIpm);
        mTvSfmMetricVal = findViewById(R.id.tvSfmMetricVal);
        mEtTargetThreadDepth = findViewById(R.id.etTargetThreadDepth);
        mTvDepthBreakdown118 = findViewById(R.id.tvDepthBreakdown118);
        mTvDepthBreakdown135 = findViewById(R.id.tvDepthBreakdown135);
        mBtnCopyGcode = findViewById(R.id.btnCopyGcode);
        mBtnSavePreset = findViewById(R.id.btnSavePreset);

        // Update theme toggle label
        int themeMode = mPrefs.getInt(PREF_THEME_MODE, 0);
        if (themeMode == 1) {
            mBtnThemeToggle.setText(R.string.theme_light);
        } else if (themeMode == 2) {
            mBtnThemeToggle.setText(R.string.theme_dark);
        } else {
            mBtnThemeToggle.setText(R.string.theme_auto);
        }
    }

    private void setupNavigationTabs() {
        mBtnTabTapDrill.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            switchModeTab(0);
        });

        mBtnTabSpeedsFeeds.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            switchModeTab(1);
        });
    }

    private void switchModeTab(int tabIndex) {
        mCurrentModeTab = tabIndex;
        if (tabIndex == 0) {
            mBtnTabTapDrill.setBackgroundResource(R.drawable.bg_pill_selected);
            mBtnTabTapDrill.setTextColor(getColor(R.color.m3_on_primary));
            mBtnTabSpeedsFeeds.setBackgroundResource(R.drawable.bg_pill_unselected);
            mBtnTabSpeedsFeeds.setTextColor(getColor(R.color.m3_on_surface));

            mLlTapDrillSection.setVisibility(View.VISIBLE);
            mLlSpeedsFeedsSection.setVisibility(View.GONE);
        } else {
            mBtnTabSpeedsFeeds.setBackgroundResource(R.drawable.bg_pill_selected);
            mBtnTabSpeedsFeeds.setTextColor(getColor(R.color.m3_on_primary));
            mBtnTabTapDrill.setBackgroundResource(R.drawable.bg_pill_unselected);
            mBtnTabTapDrill.setTextColor(getColor(R.color.m3_on_surface));

            mLlTapDrillSection.setVisibility(View.GONE);
            mLlSpeedsFeedsSection.setVisibility(View.VISIBLE);
            calculateSpeedsAndFeeds();
        }
    }

    private void setupStandardTabs() {
        View.OnClickListener listener = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            int id = v.getId();
            if (id == R.id.btnStdMetric) {
                selectStandard("METRIC");
            } else if (id == R.id.btnStdUncUnf) {
                selectStandard("UNC_UNF");
            } else if (id == R.id.btnStdPipe) {
                selectStandard("PIPE");
            } else if (id == R.id.btnStdAcme) {
                selectStandard("ACME");
            }
        };

        mBtnStdMetric.setOnClickListener(listener);
        mBtnStdUncUnf.setOnClickListener(listener);
        mBtnStdPipe.setOnClickListener(listener);
        mBtnStdAcme.setOnClickListener(listener);
    }

    private void selectStandard(String standard) {
        mCurrentStandard = standard;
        mPrefs.edit().putString(PREF_ACTIVE_STANDARD, standard).apply();

        // Update Tab visual styling
        updateTabButtonHighlight(mBtnStdMetric, "METRIC".equals(standard));
        updateTabButtonHighlight(mBtnStdUncUnf, "UNC_UNF".equals(standard));
        updateTabButtonHighlight(mBtnStdPipe, "PIPE".equals(standard));
        updateTabButtonHighlight(mBtnStdAcme, "ACME".equals(standard));

        loadThreadListForCurrentStandard(mEtSearchThread != null ? mEtSearchThread.getText().toString().trim() : "");
    }

    private void updateTabButtonHighlight(Button btn, boolean selected) {
        if (selected) {
            btn.setBackgroundResource(R.drawable.bg_pill_selected);
            btn.setTextColor(getColor(R.color.m3_on_primary));
        } else {
            btn.setBackgroundResource(R.drawable.bg_pill_unselected);
            btn.setTextColor(getColor(R.color.m3_on_surface));
        }
    }

    private void loadThreadListForCurrentStandard(String filter) {
        mCurrentThreadList = MachiningStandardsRepository.getThreads(mCurrentStandard, filter);
        List<String> labels = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < mCurrentThreadList.size(); i++) {
            MachiningStandardsRepository.ThreadSpec spec = mCurrentThreadList.get(i);
            labels.add(spec.nominalDisplay);
            if (mSavedNominal != null && spec.nominalDisplay.equalsIgnoreCase(mSavedNominal)) {
                selectedIndex = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        mSpnThreadList.setAdapter(adapter);

        if (!mCurrentThreadList.isEmpty()) {
            mSpnThreadList.setSelection(selectedIndex);
            onThreadSelected(mCurrentThreadList.get(selectedIndex));
            mLlEmptyState.setVisibility(View.GONE);
            mLlResultDeck.setVisibility(View.VISIBLE);
        } else {
            mSelectedSpec = null;
            mLlEmptyState.setVisibility(View.VISIBLE);
            mLlResultDeck.setVisibility(View.GONE);
        }
    }

    private void setupThreadSelector() {
        mEtSearchThread.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadThreadListForCurrentStandard(s.toString().trim());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        mEtSearchThread.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                hideSoftKeyboard(v);
                return true;
            }
            return false;
        });

        mBtnClearSearch.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            mEtSearchThread.setText("");
            hideSoftKeyboard(mEtSearchThread);
        });

        mSpnThreadList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < mCurrentThreadList.size()) {
                    onThreadSelected(mCurrentThreadList.get(position));
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void onThreadSelected(MachiningStandardsRepository.ThreadSpec spec) {
        mSelectedSpec = spec;
        mSavedNominal = spec.nominalDisplay;
        mTvSelectedThreadTitle.setText(spec.nominalDisplay);
        mTvPitchSpec.setText(String.format(Locale.US, "Pitch: %s", spec.pitchDisplay));

        updateDrillCalculations();

        // Auto default cutter diameter in speeds & feeds tab
        double defaultDia = spec.isMetric ? spec.majorDiameterMm / 25.4 : spec.majorDiameterInch;
        if (defaultDia > 0.0) {
            mEtCutterDiameter.setText(String.format(Locale.US, "%.4f", defaultDia));
            calculateSpeedsAndFeeds();
        }
    }

    private void setupEngagementControls() {
        mCvThreadCanvas.setOnEngagementChangeListener(percent -> {
            mCurrentEngagement = percent;
            mTvEngagementPercentBadge.setText(String.format(Locale.US, "%d%% THREAD", percent));
            updateEngagementButtonHighlight();
            updateDrillCalculations();
        });

        mBtnEngage50.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            setEngagement(50);
        });

        mBtnEngage75.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            setEngagement(75);
        });

        setEngagement(75);
    }

    private void setEngagement(int percent) {
        mCurrentEngagement = percent;
        mCvThreadCanvas.setEngagementPercentage(percent);
        mTvEngagementPercentBadge.setText(String.format(Locale.US, "%d%% THREAD", percent));
        updateEngagementButtonHighlight();
        updateDrillCalculations();
    }

    private void updateEngagementButtonHighlight() {
        boolean is50 = (mCurrentEngagement <= 62);
        mBtnEngage50.setBackgroundResource(is50 ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
        mBtnEngage50.setTextColor(getColor(is50 ? R.color.m3_on_primary : R.color.m3_on_surface));

        mBtnEngage75.setBackgroundResource(!is50 ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
        mBtnEngage75.setTextColor(getColor(!is50 ? R.color.m3_on_primary : R.color.m3_on_surface));
    }

    private void updateDrillCalculations() {
        if (mSelectedSpec == null) return;

        double drillDia = (mCurrentEngagement <= 62)
                ? mSelectedSpec.tapDrill50Inch
                : mSelectedSpec.tapDrill75Inch;

        double drillDiaMm = drillDia * 25.4;

        // Calculate delta readout compared to nominal 75%
        double deltaMm = Math.abs((drillDia - mSelectedSpec.tapDrill75Inch) * 25.4);
        String deltaTag = (mCurrentEngagement <= 62)
                ? String.format(Locale.US, " [Δ +%.3f mm]", deltaMm)
                : "";

        if (mSelectedSpec.isMetric) {
            mTvDecimalDrillSize.setText(String.format(Locale.US, "Ø %.2f mm / .%04d\"%s", drillDiaMm, (int) Math.round(drillDia * 10000), deltaTag));
        } else {
            mTvDecimalDrillSize.setText(String.format(Locale.US, ".%04d\" / %.2f mm%s", (int) Math.round(drillDia * 10000), drillDiaMm, deltaTag));
        }

        mTvFractionalDrill.setText(mSelectedSpec.nearestFractional);
        mTvWireLetterDrill.setText(mSelectedSpec.wireLetterGauge);
        mTvClearanceClose.setText(String.format(Locale.US, "Close Fit: %s", mSelectedSpec.clearanceClose));
        mTvClearanceFree.setText(String.format(Locale.US, "Free Fit: %s", mSelectedSpec.clearanceFree));

        mCvThreadCanvas.setThreadParameters(
                mSelectedSpec.majorDiameterInch,
                mSelectedSpec.pitchInch,
                drillDia,
                mCurrentEngagement
        );
    }

    private void setupSpeedsFeedsEngineControls() {
        // Workpiece materials
        SpeedsFeedsEngine.WorkpieceMaterial[] wpValues = SpeedsFeedsEngine.WorkpieceMaterial.values();
        String[] wpLabels = new String[wpValues.length];
        for (int i = 0; i < wpValues.length; i++) {
            wpLabels[i] = wpValues[i].displayName;
        }
        ArrayAdapter<String> wpAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, wpLabels);
        mSpnWorkpieceMat.setAdapter(wpAdapter);
        mSpnWorkpieceMat.setSelection(1); // 1018 Steel default

        // Tool materials
        SpeedsFeedsEngine.ToolMaterial[] tmValues = SpeedsFeedsEngine.ToolMaterial.values();
        String[] tmLabels = new String[tmValues.length];
        for (int i = 0; i < tmValues.length; i++) {
            tmLabels[i] = tmValues[i].displayName;
        }
        ArrayAdapter<String> tmAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, tmLabels);
        mSpnToolMat.setAdapter(tmAdapter);
        mSpnToolMat.setSelection(0); // HSS default

        AdapterView.OnItemSelectedListener spinListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSelectedWorkpiece = SpeedsFeedsEngine.WorkpieceMaterial.values()[mSpnWorkpieceMat.getSelectedItemPosition()];
                mSelectedToolMat = SpeedsFeedsEngine.ToolMaterial.values()[mSpnToolMat.getSelectedItemPosition()];
                calculateSpeedsAndFeeds();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        mSpnWorkpieceMat.setOnItemSelectedListener(spinListener);
        mSpnToolMat.setOnItemSelectedListener(spinListener);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculateSpeedsAndFeeds();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        mEtCutterDiameter.addTextChangedListener(watcher);
        mEtTargetThreadDepth.addTextChangedListener(watcher);

        mEtCutterDiameter.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideSoftKeyboard(v);
                calculateSpeedsAndFeeds();
                return true;
            }
            return false;
        });

        mEtTargetThreadDepth.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideSoftKeyboard(v);
                calculateSpeedsAndFeeds();
                return true;
            }
            return false;
        });

        mSwDepthAllowance.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            calculateSpeedsAndFeeds();
        });
    }

    private void calculateSpeedsAndFeeds() {
        String diaStr = mEtCutterDiameter.getText().toString().trim();
        if (TextUtils.isEmpty(diaStr)) {
            mCvSpeedFeedGauge.setRpm(0, 0);
            mTvCalculatedRpm.setText("0 RPM");
            mTvCalculatedIpm.setText("0.0 IPM");
            mTvSfmMetricVal.setText("SFM: 0");
            mTvDepthBreakdown118.setText("118° Tip Chamfer: +0.000\" | Blind Bore: 0.000\"");
            mTvDepthBreakdown135.setText("135° Tip Chamfer: +0.000\" | Blind Bore: 0.000\"");
            return;
        }

        double diameter;
        try {
            diameter = Double.parseDouble(diaStr);
        } catch (NumberFormatException e) {
            diameter = 0.250;
        }

        // Clamp diameter between 0.010" and 12.0"
        if (diameter < 0.010) diameter = 0.010;
        if (diameter > 12.000) diameter = 12.000;

        double targetDepth = 0.500;
        try {
            String depthStr = mEtTargetThreadDepth.getText().toString().trim();
            if (!TextUtils.isEmpty(depthStr)) {
                targetDepth = Double.parseDouble(depthStr);
            }
        } catch (NumberFormatException ignored) {}

        double pitch = (mSelectedSpec != null && mSelectedSpec.pitchInch > 0) ? mSelectedSpec.pitchInch : 0.050;

        SpeedsFeedsEngine.CalculationResult result = SpeedsFeedsEngine.compute(
                mSelectedWorkpiece,
                mSelectedToolMat,
                diameter,
                mSwDepthAllowance.isChecked(),
                targetDepth,
                pitch
        );

        mTvCalculatedRpm.setText(String.format(Locale.US, "%d RPM", result.spindleRpm));
        mTvCalculatedIpm.setText(String.format(Locale.US, "%.1f IPM (%.0f mm/min)", result.feedIpm, result.feedMmpm));
        mTvSfmMetricVal.setText(String.format(Locale.US, "Base SFM: %.0f | Active Point Comp: +%.3f\"", result.sfm, result.depthCompInch));

        mTvDepthBreakdown118.setText(String.format(Locale.US,
                "118° Standard Tip Comp: +%.3f\" (%.2f mm) | Total Blind Bore: %.3f\"",
                result.tip118CompInch, result.tip118CompInch * 25.4, targetDepth + (3.5 * pitch) + result.tip118CompInch + 0.040));

        mTvDepthBreakdown135.setText(String.format(Locale.US,
                "135° Split Point Tip Comp: +%.3f\" (%.2f mm) | Total Blind Bore: %.3f\"",
                result.tip135CompInch, result.tip135CompInch * 25.4, targetDepth + (3.5 * pitch) + result.tip135CompInch + 0.040));

        mCvSpeedFeedGauge.setRpm(result.spindleRpm, result.maxSafeRpm);
    }

    private void setupActionButtons() {
        mBtnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            cycleThemeMode();
        });

        mBtnOverlayHUD.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Intent intent = new Intent(MainActivity.this, OverlayActivity.class);
            if (mSelectedSpec != null) {
                intent.putExtra("EXTRA_NOMINAL", mSelectedSpec.nominalDisplay);
                intent.putExtra("EXTRA_STANDARD", mCurrentStandard);
            }
            startActivity(intent);
        });

        mBtnPinGlance.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (mSelectedSpec != null) {
                pinToWidget(mSelectedSpec);
                Toast.makeText(this, "Pinned " + mSelectedSpec.nominalDisplay + " to Glance Widget", Toast.LENGTH_SHORT).show();
            }
        });

        mBtnCopyThreadData.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mSelectedSpec == null) return;
            String text = String.format(Locale.US,
                    "TapDrill Spec: %s (%s)\nPitch: %s\nTap Drill (%d%%): %s\nFractional: %s | Wire/Letter: %s\nClearance (Close): %s | (Free): %s",
                    mSelectedSpec.nominalDisplay, mCurrentStandard, mSelectedSpec.pitchDisplay,
                    mCurrentEngagement, mTvDecimalDrillSize.getText(), mSelectedSpec.nearestFractional,
                    mSelectedSpec.wireLetterGauge, mSelectedSpec.clearanceClose, mSelectedSpec.clearanceFree);
            copyToClipboard("TapDrill Specification", text);
        });

        mBtnCopyClearanceSpecs.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mSelectedSpec == null) return;
            String comment = String.format(Locale.US,
                    "( CNC HOLE SPEC: %s | TAP DRILL %d%%: %s | CLOSE FIT: %s | FREE FIT: %s )",
                    mSelectedSpec.nominalDisplay, mCurrentEngagement, mTvDecimalDrillSize.getText(),
                    mSelectedSpec.clearanceClose, mSelectedSpec.clearanceFree);
            copyToClipboard("CNC Clearance Comments", comment);
        });

        mBtnCopyGcode.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mSelectedSpec == null) return;
            String gcode = SpeedsFeedsEngine.generateGCodeSnippet(
                    mSelectedSpec.nominalDisplay,
                    mTvCalculatedRpm.getText().toString(),
                    mTvCalculatedIpm.getText().toString(),
                    mSelectedSpec.pitchInch
            );
            copyToClipboard("Tap G-Code", gcode);
        });

        mBtnSavePreset.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (mSelectedSpec == null) return;
            double sfm = SpeedsFeedsEngine.getSfm(mSelectedWorkpiece, mSelectedToolMat);
            int rpm = 0;
            double ipm = 0;
            try {
                String rpmClean = mTvCalculatedRpm.getText().toString().replace(" RPM", "").trim();
                rpm = Integer.parseInt(rpmClean);
                String ipmClean = mTvCalculatedIpm.getText().toString().split(" ")[0].trim();
                ipm = Double.parseDouble(ipmClean);
            } catch (Exception ignored) {}

            mDbHelper.insertPreset(
                    mCurrentStandard,
                    mSelectedSpec.nominalDisplay,
                    mSelectedSpec.pitchInch,
                    mCurrentEngagement,
                    mSelectedWorkpiece.displayName,
                    mSelectedToolMat.displayName,
                    sfm,
                    rpm,
                    ipm,
                    1
            );
            Toast.makeText(this, "Job Preset Saved to Local DB", Toast.LENGTH_SHORT).show();
        });
    }

    private void pinToWidget(MachiningStandardsRepository.ThreadSpec spec) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString("pinned_nominal", spec.nominalDisplay);
        editor.putString("pinned_pitch", spec.pitchDisplay);
        editor.putString("pinned_drill", mTvDecimalDrillSize.getText().toString());
        editor.putString("pinned_wire", spec.wireLetterGauge);
        editor.putString("pinned_clearance", spec.clearanceClose);
        editor.apply();

        // Broadcast to Glance widget receiver
        Intent widgetIntent = new Intent(this, GlanceTapWidgetReceiver.class);
        widgetIntent.setAction("com.kimrosil.tapdrill.ACTION_UPDATE_WIDGET");
        sendBroadcast(widgetIntent);
    }

    private void cycleThemeMode() {
        int current = mPrefs.getInt(PREF_THEME_MODE, 0);
        int next = (current + 1) % 3;
        mPrefs.edit().putInt(PREF_THEME_MODE, next).apply();
        recreate();
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, "Copied to Clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideSoftKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && v != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_SPACE && !mEtSearchThread.hasFocus() && !mEtCutterDiameter.hasFocus() && !mEtTargetThreadDepth.hasFocus()) {
                setEngagement(mCurrentEngagement == 75 ? 50 : 75);
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                mEtSearchThread.setText("");
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_TAB && (event.isCtrlPressed() || event.isAltPressed())) {
                switchModeTab(mCurrentModeTab == 0 ? 1 : 0);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Retain view tree state during freeform window resize
    }
}