package com.kimrosil.grainrun;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "grain-run_prefs";
    public static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    public static final String KEY_TOOL_TYPE = "pref_tool_type"; // 0=Hand Plane, 1=Surface Planer, 2=Router Bit

    private static final long TORCH_SAFETY_TIMEOUT_MS = 180000; // 3-minute thermal guard

    private SharedPreferences mPrefs;
    private DatabaseHelper mDbHelper;
    private PresetManager mPresetManager;

    // Header & Tool Controls
    private Button btnThemeToggle;
    private Button btnToolHandPlane;
    private Button btnToolSurfacePlaner;
    private Button btnToolRouter;
    private Button btnQuickTorch;

    // Search & Timber selection
    private EditText etSearchTimber;
    private TextView tvSelectedSpecies;
    private TextView tvJankaBadge;

    // Hero Direction Readout Card
    private TextView tvToolModeLabel;
    private TextView tvFeedDirectionHeading;
    private TextView tvFeedExplanation;
    private TextView tvCathedralRule;
    private TextView tvRingOrientationRule;
    private TextView tvRiskBadge;

    // Visual Grain Diagram Indicators
    private TextView tvGrainDiagramPattern;
    private TextView tvGrainDiagramDescription;

    // Calculator Inputs & Sliders
    private SeekBar sbCuttingAngle;
    private TextView tvCuttingAngleVal;
    private SeekBar sbJankaHardness;
    private TextView tvJankaHardnessVal;
    private TextView tvRiskPercentage;
    private TextView tvRiskAdvice;
    private Button btnCalculateSave;
    private Button btnOpenDetail;

    // Quick Nominal Lumber Thickness Glance HUD
    private Button btnThicknessQuarter;
    private Button btnThicknessHalf;
    private Button btnThicknessFourQuarter;
    private Button btnThicknessEightQuarter;
    private TextView tvNominalActualDisplay;

    // Auxiliary & State Containers
    private LinearLayout llEmptyState;
    private LinearLayout llRecentSessionsContainer;
    private LinearLayout llAdContainer;
    private TextView tvAdNotice;

    // State
    private int mSelectedTool = 0; // 0=Hand Plane, 1=Surface Planer, 2=Router
    private PresetManager.TimberSpecies mCurrentSpecies;
    private int mCuttingAngle = 45; // Default standard bench plane bed angle
    private int mJankaRating = 1450; // Hard Maple default
    private boolean mTorchActive = false;
    private String mTorchCameraId = null;

    private final Handler mTorchHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTorchTimeoutRunnable = () -> {
        if (mTorchActive) {
            turnOffTorchInternal();
            Toast.makeText(MainActivity.this, "Raking torch auto-off (thermal guard active)", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = sp.getInt(KEY_THEME_MODE, 0); // 0=Auto, 1=Light, 2=Dark
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
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mDbHelper = new DatabaseHelper(this);
        mPresetManager = new PresetManager(this);

        initViews();
        setupStatusBar();
        setupTorchCamera();
        setupThemeToggle();
        setupToolToggles();
        setupTimberSearch();
        setupCalculatorControls();
        setupLumberQuickThicknessGlance();
        initMonetization();

        // Restore active selection
        mSelectedTool = mPrefs.getInt(KEY_TOOL_TYPE, 0);
        updateToolSelection(mSelectedTool);

        List<PresetManager.TimberSpecies> speciesList = mPresetManager.getAllSpecies();
        if (!speciesList.isEmpty()) {
            selectTimber(speciesList.get(0));
        }

        refreshRecentSessions();
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnToolHandPlane = findViewById(R.id.btnToolHandPlane);
        btnToolSurfacePlaner = findViewById(R.id.btnToolSurfacePlaner);
        btnToolRouter = findViewById(R.id.btnToolRouter);
        btnQuickTorch = findViewById(R.id.btnQuickTorch);

        etSearchTimber = findViewById(R.id.etSearchTimber);
        tvSelectedSpecies = findViewById(R.id.tvSelectedSpecies);
        tvJankaBadge = findViewById(R.id.tvJankaBadge);

        tvToolModeLabel = findViewById(R.id.tvToolModeLabel);
        tvFeedDirectionHeading = findViewById(R.id.tvFeedDirectionHeading);
        tvFeedExplanation = findViewById(R.id.tvFeedExplanation);
        tvCathedralRule = findViewById(R.id.tvCathedralRule);
        tvRingOrientationRule = findViewById(R.id.tvRingOrientationRule);
        tvRiskBadge = findViewById(R.id.tvRiskBadge);

        tvGrainDiagramPattern = findViewById(R.id.tvGrainDiagramPattern);
        tvGrainDiagramDescription = findViewById(R.id.tvGrainDiagramDescription);

        sbCuttingAngle = findViewById(R.id.sbCuttingAngle);
        tvCuttingAngleVal = findViewById(R.id.tvCuttingAngleVal);
        sbJankaHardness = findViewById(R.id.sbJankaHardness);
        tvJankaHardnessVal = findViewById(R.id.tvJankaHardnessVal);
        tvRiskPercentage = findViewById(R.id.tvRiskPercentage);
        tvRiskAdvice = findViewById(R.id.tvRiskAdvice);
        btnCalculateSave = findViewById(R.id.btnCalculateSave);
        btnOpenDetail = findViewById(R.id.btnOpenDetail);

        btnThicknessQuarter = findViewById(R.id.btnThicknessQuarter);
        btnThicknessHalf = findViewById(R.id.btnThicknessHalf);
        btnThicknessFourQuarter = findViewById(R.id.btnThicknessFourQuarter);
        btnThicknessEightQuarter = findViewById(R.id.btnThicknessEightQuarter);
        tvNominalActualDisplay = findViewById(R.id.tvNominalActualDisplay);

        llEmptyState = findViewById(R.id.llEmptyState);
        llRecentSessionsContainer = findViewById(R.id.llRecentSessionsContainer);
        llAdContainer = findViewById(R.id.llAdContainer);
        tvAdNotice = findViewById(R.id.tvAdNotice);
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                int themeMode = mPrefs.getInt(KEY_THEME_MODE, 0);
                boolean isLight = (themeMode == 1);
                if (isLight) {
                    wic.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    );
                } else {
                    wic.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }
    }

    private void setupTorchCamera() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            btnQuickTorch.setVisibility(View.GONE);
            return;
        }

        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (camManager != null) {
            try {
                for (String id : camManager.getCameraIdList()) {
                    CameraCharacteristics chars = camManager.getCameraCharacteristics(id);
                    Boolean flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                    if (flashAvailable != null && flashAvailable && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        mTorchCameraId = id;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        btnQuickTorch.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            toggleInspectionTorch();
        });
    }

    private void toggleInspectionTorch() {
        if (mTorchCameraId == null) {
            Toast.makeText(this, "Hardware flash unavailable for raking light inspection", Toast.LENGTH_SHORT).show();
            return;
        }
        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (camManager == null) return;

        try {
            mTorchActive = !mTorchActive;
            camManager.setTorchMode(mTorchCameraId, mTorchActive);
            btnQuickTorch.setText(mTorchActive ? "Torch: ON (Raking)" : "Raking Torch: OFF");
            btnQuickTorch.setSelected(mTorchActive);

            mTorchHandler.removeCallbacks(mTorchTimeoutRunnable);
            if (mTorchActive) {
                mTorchHandler.postDelayed(mTorchTimeoutRunnable, TORCH_SAFETY_TIMEOUT_MS);
                Toast.makeText(this, "Raking shadow inspection active (3m auto-guard)", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Torch deactivated", Toast.LENGTH_SHORT).show();
            }
        } catch (CameraAccessException e) {
            mTorchActive = false;
            Toast.makeText(this, "Camera torch busy: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void turnOffTorchInternal() {
        if (mTorchCameraId != null && mTorchActive) {
            try {
                CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (camManager != null) {
                    camManager.setTorchMode(mTorchCameraId, false);
                }
            } catch (Exception ignored) {
            }
            mTorchActive = false;
            btnQuickTorch.setText("Raking Torch: OFF");
            btnQuickTorch.setSelected(false);
        }
    }

    private void setupThemeToggle() {
        int themeMode = mPrefs.getInt(KEY_THEME_MODE, 0);
        updateThemeToggleText(themeMode);

        btnThemeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            cycleThemeMode();
        });
    }

    private void updateThemeToggleText(int mode) {
        if (mode == 1) {
            btnThemeToggle.setText("Light Theme");
        } else if (mode == 2) {
            btnThemeToggle.setText("Dark Theme");
        } else {
            btnThemeToggle.setText("Auto Theme");
        }
    }

    private void cycleThemeMode() {
        int current = mPrefs.getInt(KEY_THEME_MODE, 0);
        int next = (current + 1) % 3; // 0 -> 1 -> 2 -> 0
        mPrefs.edit().putInt(KEY_THEME_MODE, next).apply();
        recreate();
    }

    private void setupToolToggles() {
        View.OnClickListener listener = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (v.getId() == R.id.btnToolHandPlane) {
                mSelectedTool = 0;
            } else if (v.getId() == R.id.btnToolSurfacePlaner) {
                mSelectedTool = 1;
            } else if (v.getId() == R.id.btnToolRouter) {
                mSelectedTool = 2;
            }
            mPrefs.edit().putInt(KEY_TOOL_TYPE, mSelectedTool).apply();
            updateToolSelection(mSelectedTool);
            recalculateDirectionsAndTearout();
        };

        btnToolHandPlane.setOnClickListener(listener);
        btnToolSurfacePlaner.setOnClickListener(listener);
        btnToolRouter.setOnClickListener(listener);
    }

    private void updateToolSelection(int tool) {
        btnToolHandPlane.setSelected(tool == 0);
        btnToolSurfacePlaner.setSelected(tool == 1);
        btnToolRouter.setSelected(tool == 2);

        if (tool == 0) {
            tvToolModeLabel.setText("TOOL: BENCH HAND PLANE (Direction of Sole)");
        } else if (tool == 1) {
            tvToolModeLabel.setText("TOOL: ROTARY SURFACE PLANER (Timber Feed In)");
        } else {
            tvToolModeLabel.setText("TOOL: TABLE ROUTER / SPINDLE (Climb vs Conventional)");
        }
    }

    private void setupTimberSearch() {
        etSearchTimber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (!TextUtils.isEmpty(query)) {
                    PresetManager.TimberSpecies match = mPresetManager.fuzzyFindSpecies(query);
                    if (match != null) {
                        selectTimber(match);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void selectTimber(PresetManager.TimberSpecies species) {
        mCurrentSpecies = species;
        tvSelectedSpecies.setText(species.getName() + " (" + species.getCategories() + ")");
        tvJankaBadge.setText("Janka: " + species.getJanka() + " lbf");

        mJankaRating = species.getJanka();
        sbJankaHardness.setProgress(Math.min(3000, Math.max(300, mJankaRating)));
        tvJankaHardnessVal.setText(mJankaRating + " lbf");

        recalculateDirectionsAndTearout();
    }

    private void setupCalculatorControls() {
        sbCuttingAngle.setMax(70);
        sbCuttingAngle.setProgress(mCuttingAngle);
        tvCuttingAngleVal.setText(mCuttingAngle + "°");

        sbCuttingAngle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCuttingAngle = Math.max(30, progress); // Min bed/hone 30 deg
                tvCuttingAngleVal.setText(mCuttingAngle + "°");
                recalculateDirectionsAndTearout();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbJankaHardness.setMax(3000);
        sbJankaHardness.setProgress(mJankaRating);
        tvJankaHardnessVal.setText(mJankaRating + " lbf");

        sbJankaHardness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mJankaRating = Math.max(300, progress);
                tvJankaHardnessVal.setText(mJankaRating + " lbf");
                recalculateDirectionsAndTearout();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnCalculateSave.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveCurrentPlaningSession();
        });

        btnOpenDetail.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            Intent i = new Intent(MainActivity.this, DetailActivity.class);
            if (mCurrentSpecies != null) {
                i.putExtra(DetailActivity.EXTRA_SPECIES_NAME, mCurrentSpecies.getName());
                i.putExtra(DetailActivity.EXTRA_JANKA, mJankaRating);
                i.putExtra(DetailActivity.EXTRA_CUTTING_ANGLE, mCuttingAngle);
                i.putExtra(DetailActivity.EXTRA_TOOL_TYPE, mSelectedTool);
            }
            startActivity(i);
        });
    }

    private void recalculateDirectionsAndTearout() {
        // Grain direction guidance rules based on tool mechanics
        double tearoutRiskBase = 0.0;

        // Base risk from Janka hardness (higher hardness + interlocked grain = higher risk)
        if (mJankaRating < 800) {
            tearoutRiskBase = 15.0; // Softwood / light diffuse porous
        } else if (mJankaRating < 1400) {
            tearoutRiskBase = 32.0; // Medium hardwood
        } else if (mJankaRating < 2000) {
            tearoutRiskBase = 58.0; // Dense hardwood (Maple, Oak)
        } else {
            tearoutRiskBase = 75.0; // Exotic / extreme hardness (Ipe, Rosewood)
        }

        // Cutting angle compensation
        if (mCuttingAngle < 40) {
            tearoutRiskBase += 25.0; // Extreme risk of levering fibers
        } else if (mCuttingAngle <= 45) {
            tearoutRiskBase += 10.0;
        } else if (mCuttingAngle <= 50) {
            tearoutRiskBase -= 12.0;
        } else if (mCuttingAngle <= 55) {
            tearoutRiskBase -= 24.0;
        } else {
            tearoutRiskBase -= 35.0; // High angle scrapers/planes suppress tearout
        }

        // Tool specific risk multipliers and visual pattern guidance
        if (mSelectedTool == 0) { // Hand plane
            tvFeedDirectionHeading.setText("PLANING DIRECTION: \"WITH THE RUNNING APEX\"");
            tvFeedExplanation.setText("Feed the hand plane along the timber so blade enters downhill with grain slope. Never plane into the cathedral arches.");
            tvCathedralRule.setText("• Face Cathedrals: Plane TOWARDS the pointy apex of the cathedral arches.");
            tvRingOrientationRule.setText("• Side Grain: Growth rings should run up & away from cutter, peeling fibers forward rather than levering.");
            
            if (tvGrainDiagramPattern != null) {
                tvGrainDiagramPattern.setText("PLANE VECTOR ➔➔ [ ^ ^ ^ Cathedral Apex ^ ^ ^ ]");
                tvGrainDiagramDescription.setText("Face view: Plane in the direction the cathedral points. Side view: Rings slope upward in cut direction.");
            }
        } else if (mSelectedTool == 1) { // Surface planer (Thicknesser)
            tearoutRiskBase += 8.0;
            tvFeedDirectionHeading.setText("THICKNESSER FEED: FEED APEX FIRST INTO INFEED");
            tvFeedExplanation.setText("Reversed relative to hand planes! The rotary knives sweep down and lift fibers; feed timber so knives exit downhill.");
            tvCathedralRule.setText("• Cathedral Face: Feed Pointy Apex INTO the planer infeed bed first.");
            tvRingOrientationRule.setText("• Edge Growth Rays: Bark-side growth rings should slope downward toward outfeed.");
            
            if (tvGrainDiagramPattern != null) {
                tvGrainDiagramPattern.setText("INFEED ROLLER ➔ [ ◄ ◄ Cathedral Points Inward ]");
                tvGrainDiagramDescription.setText("Rotary head lifts wood chips: Enter timber apex-first so rotary knives slice downhill along slope.");
            }
        } else { // Router Bit Feed
            tearoutRiskBase += 15.0;
            tvFeedDirectionHeading.setText("ROUTER PASS: CONVENTIONAL FEED AGAINST BIT ROTATION");
            tvFeedExplanation.setText("Maintain positive control. For downhill end-grain transitions, execute light climb-cut skim passes first.");
            tvCathedralRule.setText("• End Grain Transitions: Pre-score bevel shoulder with marking gauge to prevent edge blowouts.");
            tvRingOrientationRule.setText("• Fluted Bits: Utilize spiral shear up-cut or compression bit to stabilize fibers.");
            
            if (tvGrainDiagramPattern != null) {
                tvGrainDiagramPattern.setText("WORKPIECE PUSH ➔ ⟲ ROTATING CUTTER BIT");
                tvGrainDiagramDescription.setText("Feed against cutter spin. If end-grain blowout is imminent, back cut entry with sacrificial backing block.");
            }
        }

        int finalRisk = (int) Math.max(5, Math.min(98, Math.round(tearoutRiskBase)));
        tvRiskPercentage.setText(finalRisk + "% TEAROUT RISK");

        if (finalRisk < 25) {
            tvRiskBadge.setText("STABLE (LOW RISK)");
            tvRiskBadge.setTextColor(0xFF388E3C);
            tvRiskAdvice.setText("Smooth gliding expected. Standard 45° bed angle with fresh hone will produce lustrous ribbons.");
        } else if (finalRisk < 55) {
            tvRiskBadge.setText("MODERATE ATTENTION");
            tvRiskBadge.setTextColor(0xFFE65100);
            tvRiskAdvice.setText("Tighten chipbreaker cap iron within 0.4mm of cutting edge. Close plane mouth throat to 0.5mm.");
        } else {
            tvRiskBadge.setText("HIGH TEAROUT RISK");
            tvRiskBadge.setTextColor(0xFFD32F2F);
            tvRiskAdvice.setText("Increase hone micro-bevel to 50°-55° or skew plane sole by 20°-30° across cut vector. Take whisper shavings (<0.03mm).");
        }
    }

    private void saveCurrentPlaningSession() {
        String speciesName = (mCurrentSpecies != null) ? mCurrentSpecies.getName() : "Unspecified Lumber";
        String toolName = (mSelectedTool == 0) ? "Hand Plane" : (mSelectedTool == 1 ? "Surface Planer" : "Router Table");
        int risk = 35;
        try {
            String riskText = tvRiskPercentage.getText().toString().replace("% TEAROUT RISK", "").trim();
            risk = Integer.parseInt(riskText);
        } catch (NumberFormatException ignored) {
        }

        long rowId = mDbHelper.insertSession(speciesName, mJankaRating, mCuttingAngle, toolName, risk, tvRiskAdvice.getText().toString());
        if (rowId > 0) {
            Toast.makeText(this, "Session logged to offline workshop ledger", Toast.LENGTH_SHORT).show();
            refreshRecentSessions();
        } else {
            Toast.makeText(this, "Failed to record session", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshRecentSessions() {
        llRecentSessionsContainer.removeAllViews();
        List<DatabaseHelper.PlaningSession> sessions = mDbHelper.getAllSessions(5);

        if (sessions.isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
            llRecentSessionsContainer.setVisibility(View.GONE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            llRecentSessionsContainer.setVisibility(View.VISIBLE);

            for (DatabaseHelper.PlaningSession s : sessions) {
                View row = getLayoutInflater().inflate(R.layout.item_session_card, llRecentSessionsContainer, false);
                TextView tvTitle = row.findViewById(R.id.tvItemSpecies);
                TextView tvMeta = row.findViewById(R.id.tvItemMeta);
                TextView tvRisk = row.findViewById(R.id.tvItemRisk);
                Button btnDelete = row.findViewById(R.id.btnItemDelete);

                tvTitle.setText(s.species + " — " + s.toolType);
                tvMeta.setText("Janka " + s.janka + " lbf • " + s.cuttingAngle + "° Bed • " + s.timestamp);
                tvRisk.setText(s.tearoutRisk + "% Risk");

                if (s.tearoutRisk >= 55) {
                    tvRisk.setTextColor(0xFFD32F2F);
                } else if (s.tearoutRisk >= 25) {
                    tvRisk.setTextColor(0xFFE65100);
                } else {
                    tvRisk.setTextColor(0xFF388E3C);
                }

                row.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    Intent i = new Intent(MainActivity.this, DetailActivity.class);
                    i.putExtra(DetailActivity.EXTRA_ID, s.id);
                    i.putExtra(DetailActivity.EXTRA_SPECIES_NAME, s.species);
                    i.putExtra(DetailActivity.EXTRA_JANKA, s.janka);
                    i.putExtra(DetailActivity.EXTRA_CUTTING_ANGLE, s.cuttingAngle);
                    i.putExtra(DetailActivity.EXTRA_TOOL_TYPE, s.toolType.contains("Hand") ? 0 : (s.toolType.contains("Surface") ? 1 : 2));
                    startActivity(i);
                });

                btnDelete.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    mDbHelper.deleteSession(s.id);
                    refreshRecentSessions();
                    Toast.makeText(MainActivity.this, "Session removed", Toast.LENGTH_SHORT).show();
                });

                llRecentSessionsContainer.addView(row);
            }
        }
    }

    private void setupLumberQuickThicknessGlance() {
        View.OnClickListener listener = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            int id = v.getId();
            if (id == R.id.btnThicknessQuarter) {
                tvNominalActualDisplay.setText("NOMINAL: 1/4\" (2/4)  ==>  SURFACED ACTUAL: 1/4\" (6.35 mm) Dressed S4S");
            } else if (id == R.id.btnThicknessHalf) {
                tvNominalActualDisplay.setText("NOMINAL: 1/2\"  ==>  SURFACED ACTUAL: 7/16\" (11.1 mm) Dressed S4S");
            } else if (id == R.id.btnThicknessFourQuarter) {
                tvNominalActualDisplay.setText("NOMINAL: 4/4 (1\")  ==>  SURFACED ACTUAL: 3/4\" (19.05 mm) Standard Board");
            } else if (id == R.id.btnThicknessEightQuarter) {
                tvNominalActualDisplay.setText("NOMINAL: 8/4 (2\")  ==>  SURFACED ACTUAL: 1-3/4\" (44.45 mm) Heavy Stock");
            }
        };

        btnThicknessQuarter.setOnClickListener(listener);
        btnThicknessHalf.setOnClickListener(listener);
        btnThicknessFourQuarter.setOnClickListener(listener);
        btnThicknessEightQuarter.setOnClickListener(listener);
    }

    private void initMonetization() {
        boolean isPro = mPrefs.getBoolean("is_pro_user", false);
        if (isPro) {
            llAdContainer.setVisibility(View.GONE);
        } else {
            llAdContainer.setVisibility(View.VISIBLE);
            tvAdNotice.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com"));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Unable to launch browser", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                btnCalculateSave.performClick();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
                // Toggle tool mode
                mSelectedTool = (mSelectedTool + 1) % 3;
                updateToolSelection(mSelectedTool);
                recalculateDirectionsAndTearout();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                etSearchTimber.setText("");
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS) {
                sbCuttingAngle.setProgress(Math.min(70, sbCuttingAngle.getProgress() + 1));
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
                sbCuttingAngle.setProgress(Math.max(30, sbCuttingAngle.getProgress() - 1));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecentSessions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTorchHandler.removeCallbacks(mTorchTimeoutRunnable);
        turnOffTorchInternal();
        if (mDbHelper != null) {
            mDbHelper.close();
        }
    }
}
