package com.kimrosil.crowncut;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "crowncut_prefs";
    private static final String KEY_THEME_STR = "theme_mode"; // "system", "dark", "light"
    private static final String KEY_CORNER_ANGLE = "corner_angle";
    private static final String KEY_SPRING_ANGLE = "spring_angle";
    private static final String KEY_CUT_TYPE = "cut_type_index"; // 0: InL, 1: InR, 2: OutL, 3: OutR
    private static final String KEY_ACTIVE_TAB = "active_tab"; // 0: Flat, 1: Nested, 2: Polygon, 3: Saved
    private static final String KEY_POLY_SIDES = "poly_sides";

    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;

    // Header & Settings
    private Button btnThemeToggle;
    private Button btnSettings;

    // Tabs
    private Button tabCrownFlat;
    private Button tabCrownNested;
    private Button tabPolygon;
    private Button tabSavedCuts;
    private LinearLayout containerCrownFlat;
    private LinearLayout containerCrownNested;
    private LinearLayout containerPolygon;
    private LinearLayout containerSavedCuts;

    // Crown Flat: Corner Angle
    private Button presetCorner90;
    private Button presetCorner89;
    private Button presetCorner91;
    private Button presetCorner135;
    private Button btnCornerMinus;
    private Button btnCornerPlus;
    private EditText etCornerAngle;

    // Crown Flat: Spring Angle
    private Button chipSpring38;
    private Button chipSpring45;
    private Button chipSpring52;
    private Button chipSpringCustom;
    private LinearLayout rowCustomSpring;
    private Button btnSpringMinus;
    private Button btnSpringPlus;
    private EditText etCustomSpring;

    // Telemetry HUD
    private TextView tvMiterAngleValue;
    private TextView tvMiterDirectionSub;
    private TextView tvBevelAngleValue;
    private TextView tvBevelDirectionSub;

    // Cut Guide
    private Button btnCutInsideLeft;
    private Button btnCutInsideRight;
    private Button btnCutOutsideLeft;
    private Button btnCutOutsideRight;
    private TextView tvCutCornerBadge;
    private TextView tvInstructionFence;
    private TextView tvInstructionMiter;
    private TextView tvInstructionBevel;
    private TextView tvInstructionKeep;
    private Button btnCopyCutSpec;
    private Button btnSaveCutSpec;

    // Crown Nested
    private TextView tvNestedMiterValue;
    private TextView tvNestedInsideLeft;
    private TextView tvNestedInsideRight;
    private TextView tvNestedOutsideLeft;
    private TextView tvNestedOutsideRight;

    // Polygon Frame
    private Button btnPolygonSidesMinus;
    private Button btnPolygonSidesPlus;
    private TextView tvPolygonSidesCount;
    private TextView tvPolyMiterValue;
    private TextView tvPolyCornerValue;
    private Button polyPreset4;
    private Button polyPreset5;
    private Button polyPreset6;
    private Button polyPreset8;

    // Saved Cuts
    private TextView tvSavedCutsHeader;
    private Button btnClearAllCuts;
    private LinearLayout llSavedCutsList;
    private TextView tvEmptySavedNotice;

    // State
    private double currentCornerAngle = 90.0;
    private double currentSpringAngle = 38.0;
    private int currentCutTypeIndex = 0; // 0: Inside L, 1: Inside R, 2: Outside L, 3: Outside R
    private int currentActiveTab = 0; // 0: Flat, 1: Nested, 2: Poly, 3: Saved
    private int currentPolySides = 8;
    private boolean isUpdatingText = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences p = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String mode = p.getString(KEY_THEME_STR, "system");
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
        applySavedTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);

        initViews();
        restoreState(savedInstanceState);
        setupListeners();

        calculateFlatAngles();
        calculateNestedAngles();
        calculatePolygonAngles();
        refreshSavedCutsList();
    }

    private void applySavedTheme() {
        String mode = prefs.getString(KEY_THEME_STR, "system");
        if ("light".equals(mode)) {
            setTheme(R.style.AppTheme_Light);
        } else if ("dark".equals(mode)) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }
    }

    private void initViews() {
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnSettings = findViewById(R.id.btnSettings);

        tabCrownFlat = findViewById(R.id.tabCrownFlat);
        tabCrownNested = findViewById(R.id.tabCrownNested);
        tabPolygon = findViewById(R.id.tabPolygon);
        tabSavedCuts = findViewById(R.id.tabSavedCuts);
        containerCrownFlat = findViewById(R.id.containerCrownFlat);
        containerCrownNested = findViewById(R.id.containerCrownNested);
        containerPolygon = findViewById(R.id.containerPolygon);
        containerSavedCuts = findViewById(R.id.containerSavedCuts);

        // Corner Angle
        presetCorner90 = findViewById(R.id.presetCorner90);
        presetCorner89 = findViewById(R.id.presetCorner89);
        presetCorner91 = findViewById(R.id.presetCorner91);
        presetCorner135 = findViewById(R.id.presetCorner135);
        btnCornerMinus = findViewById(R.id.btnCornerMinus);
        btnCornerPlus = findViewById(R.id.btnCornerPlus);
        etCornerAngle = findViewById(R.id.etCornerAngle);

        // Spring Angle
        chipSpring38 = findViewById(R.id.chipSpring38);
        chipSpring45 = findViewById(R.id.chipSpring45);
        chipSpring52 = findViewById(R.id.chipSpring52);
        chipSpringCustom = findViewById(R.id.chipSpringCustom);
        rowCustomSpring = findViewById(R.id.rowCustomSpring);
        btnSpringMinus = findViewById(R.id.btnSpringMinus);
        btnSpringPlus = findViewById(R.id.btnSpringPlus);
        etCustomSpring = findViewById(R.id.etCustomSpring);

        // Telemetry HUD
        tvMiterAngleValue = findViewById(R.id.tvMiterAngleValue);
        tvMiterDirectionSub = findViewById(R.id.tvMiterDirectionSub);
        tvBevelAngleValue = findViewById(R.id.tvBevelAngleValue);
        tvBevelDirectionSub = findViewById(R.id.tvBevelDirectionSub);

        // Cut Guide
        btnCutInsideLeft = findViewById(R.id.btnCutInsideLeft);
        btnCutInsideRight = findViewById(R.id.btnCutInsideRight);
        btnCutOutsideLeft = findViewById(R.id.btnCutOutsideLeft);
        btnCutOutsideRight = findViewById(R.id.btnCutOutsideRight);
        tvCutCornerBadge = findViewById(R.id.tvCutCornerBadge);
        tvInstructionFence = findViewById(R.id.tvInstructionFence);
        tvInstructionMiter = findViewById(R.id.tvInstructionMiter);
        tvInstructionBevel = findViewById(R.id.tvInstructionBevel);
        tvInstructionKeep = findViewById(R.id.tvInstructionKeep);
        btnCopyCutSpec = findViewById(R.id.btnCopyCutSpec);
        btnSaveCutSpec = findViewById(R.id.btnSaveCutSpec);

        // Nested
        tvNestedMiterValue = findViewById(R.id.tvNestedMiterValue);
        tvNestedInsideLeft = findViewById(R.id.tvNestedInsideLeft);
        tvNestedInsideRight = findViewById(R.id.tvNestedInsideRight);
        tvNestedOutsideLeft = findViewById(R.id.tvNestedOutsideLeft);
        tvNestedOutsideRight = findViewById(R.id.tvNestedOutsideRight);

        // Polygon
        btnPolygonSidesMinus = findViewById(R.id.btnPolygonSidesMinus);
        btnPolygonSidesPlus = findViewById(R.id.btnPolygonSidesPlus);
        tvPolygonSidesCount = findViewById(R.id.tvPolygonSidesCount);
        tvPolyMiterValue = findViewById(R.id.tvPolyMiterValue);
        tvPolyCornerValue = findViewById(R.id.tvPolyCornerValue);
        polyPreset4 = findViewById(R.id.polyPreset4);
        polyPreset5 = findViewById(R.id.polyPreset5);
        polyPreset6 = findViewById(R.id.polyPreset6);
        polyPreset8 = findViewById(R.id.polyPreset8);

        // Saved Cuts
        tvSavedCutsHeader = findViewById(R.id.tvSavedCutsHeader);
        btnClearAllCuts = findViewById(R.id.btnClearAllCuts);
        llSavedCutsList = findViewById(R.id.llSavedCutsList);
        tvEmptySavedNotice = findViewById(R.id.tvEmptySavedNotice);
    }

    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentCornerAngle = savedInstanceState.getDouble(KEY_CORNER_ANGLE, 90.0);
            currentSpringAngle = savedInstanceState.getDouble(KEY_SPRING_ANGLE, 38.0);
            currentCutTypeIndex = savedInstanceState.getInt(KEY_CUT_TYPE, 0);
            currentActiveTab = savedInstanceState.getInt(KEY_ACTIVE_TAB, 0);
            currentPolySides = savedInstanceState.getInt(KEY_POLY_SIDES, 8);
        } else {
            currentCornerAngle = prefs.getFloat(KEY_CORNER_ANGLE, 90.0f);
            currentSpringAngle = prefs.getFloat(KEY_SPRING_ANGLE, 38.0f);
            currentCutTypeIndex = prefs.getInt(KEY_CUT_TYPE, 0);
            currentActiveTab = prefs.getInt(KEY_ACTIVE_TAB, 0);
            currentPolySides = prefs.getInt(KEY_POLY_SIDES, 8);
        }

        isUpdatingText = true;
        etCornerAngle.setText(String.format(Locale.US, "%.1f", currentCornerAngle));
        etCustomSpring.setText(String.format(Locale.US, "%.1f", currentSpringAngle));
        isUpdatingText = false;

        switchTab(currentActiveTab);
        updateCornerPresetButtons();
        updateSpringButtons();
        updateCutTypeButtons();
        updatePolygonDisplay();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(KEY_CORNER_ANGLE, currentCornerAngle);
        outState.putDouble(KEY_SPRING_ANGLE, currentSpringAngle);
        outState.putInt(KEY_CUT_TYPE, currentCutTypeIndex);
        outState.putInt(KEY_ACTIVE_TAB, currentActiveTab);
        outState.putInt(KEY_POLY_SIDES, currentPolySides);
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit()
                .putFloat(KEY_CORNER_ANGLE, (float) currentCornerAngle)
                .putFloat(KEY_SPRING_ANGLE, (float) currentSpringAngle)
                .putInt(KEY_CUT_TYPE, currentCutTypeIndex)
                .putInt(KEY_ACTIVE_TAB, currentActiveTab)
                .putInt(KEY_POLY_SIDES, currentPolySides)
                .apply();
    }

    private void setupListeners() {
        // Theme Toggle Direct
        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTheme();
            }
        });

        // Settings Button
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        // Main Navigation Tabs
        tabCrownFlat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(0);
            }
        });
        tabCrownNested.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(1);
            }
        });
        tabPolygon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(2);
            }
        });
        tabSavedCuts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(3);
            }
        });

        // Corner Angle Presets
        presetCorner90.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCornerAngle(90.0);
            }
        });
        presetCorner89.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCornerAngle(89.0);
            }
        });
        presetCorner91.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCornerAngle(91.0);
            }
        });
        presetCorner135.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCornerAngle(135.0);
            }
        });

        // Corner Angle Steppers
        btnCornerMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCornerAngle(Math.max(30.0, currentCornerAngle - 0.5));
            }
        });
        btnCornerPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCornerAngle(Math.min(179.5, currentCornerAngle + 0.5));
            }
        });

        etCornerAngle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingText) return;
                try {
                    String str = s.toString().trim();
                    if (!str.isEmpty()) {
                        double val = Double.parseDouble(str);
                        if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                            currentCornerAngle = Math.max(10.0, Math.min(179.9, val));
                            updateCornerPresetButtons();
                            calculateFlatAngles();
                            calculateNestedAngles();
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        // Spring Angle Chips
        chipSpring38.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSpringAngle(38.0);
            }
        });
        chipSpring45.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSpringAngle(45.0);
            }
        });
        chipSpring52.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSpringAngle(52.0);
            }
        });
        chipSpringCustom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rowCustomSpring.setVisibility(View.VISIBLE);
                updateSpringButtons();
            }
        });

        btnSpringMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSpringAngle(Math.max(15.0, currentSpringAngle - 1.0));
            }
        });
        btnSpringPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSpringAngle(Math.min(75.0, currentSpringAngle + 1.0));
            }
        });

        etCustomSpring.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingText) return;
                try {
                    String str = s.toString().trim();
                    if (!str.isEmpty()) {
                        double val = Double.parseDouble(str);
                        if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                            currentSpringAngle = Math.max(10.0, Math.min(80.0, val));
                            updateSpringButtons();
                            calculateFlatAngles();
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        // Cut Type Selector (4 Steps)
        btnCutInsideLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCutType(0);
            }
        });
        btnCutInsideRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCutType(1);
            }
        });
        btnCutOutsideLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCutType(2);
            }
        });
        btnCutOutsideRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCutType(3);
            }
        });

        // Copy Specs
        btnCopyCutSpec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyCurrentCutToClipboard();
            }
        });

        // Save Cut to List
        btnSaveCutSpec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentCutToDatabase();
            }
        });

        // Polygon Steppers & Presets
        btnPolygonSidesMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPolygonSides(Math.max(3, currentPolySides - 1));
            }
        });
        btnPolygonSidesPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPolygonSides(Math.min(24, currentPolySides + 1));
            }
        });

        polyPreset4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPolygonSides(4);
            }
        });
        polyPreset5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPolygonSides(5);
            }
        });
        polyPreset6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPolygonSides(6);
            }
        });
        polyPreset8.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPolygonSides(8);
            }
        });

        // Clear Saved Cuts
        btnClearAllCuts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Clear All Saved Cuts?")
                        .setMessage("Are you sure you want to delete all saved cuts from your room project?")
                        .setPositiveButton("Clear All", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dbHelper.clearAll();
                                refreshSavedCutsList();
                                Toast.makeText(MainActivity.this, "Cut list cleared", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void toggleTheme() {
        String current = prefs.getString(KEY_THEME_STR, "system");
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

    private void setThemePreference(String mode) {
        prefs.edit().putString(KEY_THEME_STR, mode).apply();
        recreate();
    }

    private void switchTab(int tabIndex) {
        currentActiveTab = tabIndex;
        tabCrownFlat.setBackgroundResource(tabIndex == 0 ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tabCrownFlat.setTextColor(getResources().getColor(tabIndex == 0 ? R.color.m3_on_primary : R.color.textPrimary));

        tabCrownNested.setBackgroundResource(tabIndex == 1 ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tabCrownNested.setTextColor(getResources().getColor(tabIndex == 1 ? R.color.m3_on_primary : R.color.textPrimary));

        tabPolygon.setBackgroundResource(tabIndex == 2 ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tabPolygon.setTextColor(getResources().getColor(tabIndex == 2 ? R.color.m3_on_primary : R.color.textPrimary));

        tabSavedCuts.setBackgroundResource(tabIndex == 3 ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tabSavedCuts.setTextColor(getResources().getColor(tabIndex == 3 ? R.color.m3_on_primary : R.color.textPrimary));

        containerCrownFlat.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        containerCrownNested.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        containerPolygon.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);
        containerSavedCuts.setVisibility(tabIndex == 3 ? View.VISIBLE : View.GONE);

        if (tabIndex == 3) {
            refreshSavedCutsList();
        }
    }

    private void setCornerAngle(double angle) {
        currentCornerAngle = angle;
        isUpdatingText = true;
        etCornerAngle.setText(String.format(Locale.US, "%.1f", angle));
        isUpdatingText = false;
        updateCornerPresetButtons();
        calculateFlatAngles();
        calculateNestedAngles();
    }

    private void updateCornerPresetButtons() {
        setPresetActive(presetCorner90, Math.abs(currentCornerAngle - 90.0) < 0.05);
        setPresetActive(presetCorner89, Math.abs(currentCornerAngle - 89.0) < 0.05);
        setPresetActive(presetCorner91, Math.abs(currentCornerAngle - 91.0) < 0.05);
        setPresetActive(presetCorner135, Math.abs(currentCornerAngle - 135.0) < 0.05);
    }

    private void setPresetActive(Button btn, boolean active) {
        btn.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btn.setTextColor(getResources().getColor(active ? R.color.m3_on_primary : R.color.chipTextUnselected));
    }

    private void setSpringAngle(double angle) {
        currentSpringAngle = angle;
        isUpdatingText = true;
        etCustomSpring.setText(String.format(Locale.US, "%.1f", angle));
        isUpdatingText = false;

        boolean isStandard = Math.abs(angle - 38.0) < 0.05 || Math.abs(angle - 45.0) < 0.05 || Math.abs(angle - 52.0) < 0.05;
        rowCustomSpring.setVisibility(isStandard ? View.GONE : View.VISIBLE);

        updateSpringButtons();
        calculateFlatAngles();
    }

    private void updateSpringButtons() {
        boolean is38 = Math.abs(currentSpringAngle - 38.0) < 0.05;
        boolean is45 = Math.abs(currentSpringAngle - 45.0) < 0.05;
        boolean is52 = Math.abs(currentSpringAngle - 52.0) < 0.05;
        boolean isCustom = !is38 && !is45 && !is52;

        setPresetActive(chipSpring38, is38);
        setPresetActive(chipSpring45, is45);
        setPresetActive(chipSpring52, is52);
        setPresetActive(chipSpringCustom, isCustom);
    }

    private void setCutType(int index) {
        currentCutTypeIndex = index;
        updateCutTypeButtons();
        updateCutGuideInstructions();
    }

    private void updateCutTypeButtons() {
        setPresetActive(btnCutInsideLeft, currentCutTypeIndex == 0);
        setPresetActive(btnCutInsideRight, currentCutTypeIndex == 1);
        setPresetActive(btnCutOutsideLeft, currentCutTypeIndex == 2);
        setPresetActive(btnCutOutsideRight, currentCutTypeIndex == 3);
    }

    // =========================================================================
    // TRIGONOMETRIC COMPOUND MITER & BEVEL FORMULAS
    // =========================================================================
    private double calculatedMiterAngle = 31.6;
    private double calculatedBevelAngle = 33.9;

    private void calculateFlatAngles() {
        if (currentCornerAngle <= 0 || currentCornerAngle >= 180.0 || currentSpringAngle <= 0 || currentSpringAngle >= 90.0) {
            tvMiterAngleValue.setText("--");
            tvBevelAngleValue.setText("--");
            return;
        }

        double halfCornerRad = Math.toRadians(currentCornerAngle / 2.0);
        double springRad = Math.toRadians(currentSpringAngle);

        // Miter Angle Formula: tan(Miter) = sin(Spring) / tan(Corner / 2)
        double tanMiter = Math.sin(springRad) / Math.tan(halfCornerRad);
        calculatedMiterAngle = Math.toDegrees(Math.atan(tanMiter));

        // Bevel Angle Formula: sin(Bevel) = cos(Spring) * cos(Corner / 2)
        double sinBevel = Math.cos(springRad) * Math.cos(halfCornerRad);
        if (sinBevel > 1.0) sinBevel = 1.0;
        if (sinBevel < -1.0) sinBevel = -1.0;
        calculatedBevelAngle = Math.toDegrees(Math.asin(sinBevel));

        tvMiterAngleValue.setText(String.format(Locale.US, "%.1f°", calculatedMiterAngle));
        tvBevelAngleValue.setText(String.format(Locale.US, "%.1f°", calculatedBevelAngle));

        updateCutGuideInstructions();
    }

    private void updateCutGuideInstructions() {
        String miterDegStr = String.format(Locale.US, "%.1f°", calculatedMiterAngle);
        String bevelDegStr = String.format(Locale.US, "%.1f°", calculatedBevelAngle);

        switch (currentCutTypeIndex) {
            case 0: // Inside Left
                tvCutCornerBadge.setText("INSIDE CORNER - LEFT PIECE");
                tvCutCornerBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                tvCutCornerBadge.setTextColor(getResources().getColor(R.color.badgeTextAmber));
                tvInstructionFence.setText("• Fence: Top edge of crown flat against saw fence");
                tvInstructionMiter.setText("• Miter Saw: Rotate table RIGHT to " + miterDegStr);
                tvInstructionBevel.setText("• Saw Bevel: Tilt blade LEFT to " + bevelDegStr);
                tvInstructionKeep.setText("• Keep Piece: Save LEFT side of cut");
                tvMiterDirectionSub.setText("Rotate saw Right");
                tvBevelDirectionSub.setText("Tilt blade Left");
                break;
            case 1: // Inside Right
                tvCutCornerBadge.setText("INSIDE CORNER - RIGHT PIECE");
                tvCutCornerBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                tvCutCornerBadge.setTextColor(getResources().getColor(R.color.badgeTextAmber));
                tvInstructionFence.setText("• Fence: Bottom edge of crown flat against saw fence");
                tvInstructionMiter.setText("• Miter Saw: Rotate table LEFT to " + miterDegStr);
                tvInstructionBevel.setText("• Saw Bevel: Tilt blade RIGHT to " + bevelDegStr);
                tvInstructionKeep.setText("• Keep Piece: Save RIGHT side of cut");
                tvMiterDirectionSub.setText("Rotate saw Left");
                tvBevelDirectionSub.setText("Tilt blade Right");
                break;
            case 2: // Outside Left
                tvCutCornerBadge.setText("OUTSIDE CORNER - LEFT PIECE");
                tvCutCornerBadge.setBackgroundResource(R.drawable.bg_badge_safe);
                tvCutCornerBadge.setTextColor(getResources().getColor(R.color.m3_success));
                tvInstructionFence.setText("• Fence: Bottom edge of crown flat against saw fence");
                tvInstructionMiter.setText("• Miter Saw: Rotate table LEFT to " + miterDegStr);
                tvInstructionBevel.setText("• Saw Bevel: Tilt blade RIGHT to " + bevelDegStr);
                tvInstructionKeep.setText("• Keep Piece: Save LEFT side of cut");
                tvMiterDirectionSub.setText("Rotate saw Left");
                tvBevelDirectionSub.setText("Tilt blade Right");
                break;
            case 3: // Outside Right
                tvCutCornerBadge.setText("OUTSIDE CORNER - RIGHT PIECE");
                tvCutCornerBadge.setBackgroundResource(R.drawable.bg_badge_safe);
                tvCutCornerBadge.setTextColor(getResources().getColor(R.color.m3_success));
                tvInstructionFence.setText("• Fence: Top edge of crown flat against saw fence");
                tvInstructionMiter.setText("• Miter Saw: Rotate table RIGHT to " + miterDegStr);
                tvInstructionBevel.setText("• Saw Bevel: Tilt blade LEFT to " + bevelDegStr);
                tvInstructionKeep.setText("• Keep Piece: Save RIGHT side of cut");
                tvMiterDirectionSub.setText("Rotate saw Right");
                tvBevelDirectionSub.setText("Tilt blade Left");
                break;
        }
    }

    private void calculateNestedAngles() {
        double nestedMiter = currentCornerAngle / 2.0;
        tvNestedMiterValue.setText(String.format(Locale.US, "%.1f°", nestedMiter));

        String angleStr = String.format(Locale.US, "%.1f°", nestedMiter);
        tvNestedInsideLeft.setText("• Inside Left: Miter RIGHT " + angleStr + " | Save LEFT of cut");
        tvNestedInsideRight.setText("• Inside Right: Miter LEFT " + angleStr + " | Save RIGHT of cut");
        tvNestedOutsideLeft.setText("• Outside Left: Miter LEFT " + angleStr + " | Save LEFT of cut");
        tvNestedOutsideRight.setText("• Outside Right: Miter RIGHT " + angleStr + " | Save RIGHT of cut");
    }

    private void setPolygonSides(int sides) {
        currentPolySides = sides;
        updatePolygonDisplay();
        calculatePolygonAngles();
    }

    private void updatePolygonDisplay() {
        String name;
        switch (currentPolySides) {
            case 3: name = "3 SIDES (TRIANGLE)"; break;
            case 4: name = "4 SIDES (SQUARE)"; break;
            case 5: name = "5 SIDES (PENTAGON)"; break;
            case 6: name = "6 SIDES (HEXAGON)"; break;
            case 7: name = "7 SIDES (HEPTAGON)"; break;
            case 8: name = "8 SIDES (OCTAGON)"; break;
            case 9: name = "9 SIDES (NONAGON)"; break;
            case 10: name = "10 SIDES (DECAGON)"; break;
            case 12: name = "12 SIDES (DODECAGON)"; break;
            default: name = currentPolySides + " SIDES (POLYGON)"; break;
        }
        tvPolygonSidesCount.setText(name);

        setPresetActive(polyPreset4, currentPolySides == 4);
        setPresetActive(polyPreset5, currentPolySides == 5);
        setPresetActive(polyPreset6, currentPolySides == 6);
        setPresetActive(polyPreset8, currentPolySides == 8);
    }

    private void calculatePolygonAngles() {
        if (currentPolySides < 3) return;
        double sawMiter = 180.0 / currentPolySides;
        double cornerAngle = (currentPolySides - 2.0) * 180.0 / currentPolySides;

        tvPolyMiterValue.setText(String.format(Locale.US, "%.1f°", sawMiter));
        tvPolyCornerValue.setText(String.format(Locale.US, "%.1f°", cornerAngle));
    }

    private void copyCurrentCutToClipboard() {
        String cutName = getCutTypeName(currentCutTypeIndex);
        String text = String.format(Locale.US,
                "CrownCut Spec: %s\nCorner Angle: %.1f° | Spring: %.1f°\nMiter Setting: %.1f°\nBevel Setting: %.1f°\n%s\n%s\n%s",
                cutName, currentCornerAngle, currentSpringAngle,
                calculatedMiterAngle, calculatedBevelAngle,
                tvInstructionFence.getText().toString(),
                tvInstructionMiter.getText().toString(),
                tvInstructionKeep.getText().toString());

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("CrownCut Spec", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Cut spec copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCurrentCutToDatabase() {
        final String defaultLabel = String.format(Locale.US, "Corner %.1f° - %s", currentCornerAngle, getCutTypeName(currentCutTypeIndex));

        final EditText etLabelInput = new EditText(this);
        etLabelInput.setText(defaultLabel);
        etLabelInput.setSelection(defaultLabel.length());
        etLabelInput.setBackgroundResource(R.drawable.edittext_bg);
        etLabelInput.setPadding(24, 20, 24, 20);
        etLabelInput.setTextColor(getResources().getColor(R.color.textPrimary));
        etLabelInput.setHint("Room / Corner label");
        etLabelInput.setHintTextColor(getResources().getColor(R.color.textSecondary));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 20, 40, 10);
        container.addView(etLabelInput);

        new AlertDialog.Builder(this)
                .setTitle("Save Cut to Room Project")
                .setMessage("Enter a label for this corner:")
                .setView(container)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String label = etLabelInput.getText().toString().trim();
                        if (label.isEmpty()) label = defaultLabel;

                        long id = dbHelper.insertCut(
                                label,
                                "Crown Flat",
                                currentCornerAngle,
                                currentSpringAngle,
                                getCutTypeName(currentCutTypeIndex),
                                calculatedMiterAngle,
                                calculatedBevelAngle,
                                tvInstructionFence.getText().toString()
                        );

                        if (id > 0) {
                            Toast.makeText(MainActivity.this, "Saved to project cut list!", Toast.LENGTH_SHORT).show();
                            refreshSavedCutsList();
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to save cut", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getCutTypeName(int index) {
        switch (index) {
            case 0: return "Inside Left";
            case 1: return "Inside Right";
            case 2: return "Outside Left";
            case 3: return "Outside Right";
            default: return "Crown Cut";
        }
    }

    private void refreshSavedCutsList() {
        llSavedCutsList.removeAllViews();
        List<Map<String, Object>> cuts = dbHelper.getAllCuts();

        if (cuts.isEmpty()) {
            tvEmptySavedNotice.setVisibility(View.VISIBLE);
            tvSavedCutsHeader.setText("SAVED PROJECT CUT LIST (0)");
            return;
        }

        tvEmptySavedNotice.setVisibility(View.GONE);
        tvSavedCutsHeader.setText(String.format(Locale.US, "SAVED PROJECT CUT LIST (%d)", cuts.size()));

        for (final Map<String, Object> cut : cuts) {
            final long id = (Long) cut.get(DatabaseHelper.COL_ID);
            String label = (String) cut.get(DatabaseHelper.COL_LABEL);
            String cutType = (String) cut.get(DatabaseHelper.COL_CUT_TYPE);
            double corner = (Double) cut.get(DatabaseHelper.COL_CORNER_ANGLE);
            double spring = (Double) cut.get(DatabaseHelper.COL_SPRING_ANGLE);
            double miter = (Double) cut.get(DatabaseHelper.COL_MITER_ANGLE);
            double bevel = (Double) cut.get(DatabaseHelper.COL_BEVEL_ANGLE);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.card_m3);
            card.setPadding(32, 28, 32, 28);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 16);
            card.setLayoutParams(cardParams);

            // Row 1: Label & Delete Button
            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvLabel = new TextView(this);
            tvLabel.setText(label);
            tvLabel.setTextSize(15);
            tvLabel.setTextColor(getResources().getColor(R.color.textPrimary));
            tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            headerRow.addView(tvLabel, lpLabel);

            Button btnDelete = new Button(this, null, 0, R.style.CleanButton);
            btnDelete.setText("Delete");
            btnDelete.setTextSize(11);
            btnDelete.setTextColor(getResources().getColor(R.color.m3_error));
            btnDelete.setBackgroundResource(R.drawable.bg_chip_unselected);
            btnDelete.setMinHeight(120);
            LinearLayout.LayoutParams lpDel = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnDelete.setLayoutParams(lpDel);
            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dbHelper.deleteCut(id);
                    refreshSavedCutsList();
                }
            });
            headerRow.addView(btnDelete);
            card.addView(headerRow);

            // Row 2: Specs & Badge
            TextView tvSpecs = new TextView(this);
            tvSpecs.setText(String.format(Locale.US, "%s | Corner: %.1f° | Spring: %.1f°", cutType, corner, spring));
            tvSpecs.setTextSize(12);
            tvSpecs.setTextColor(getResources().getColor(R.color.textSecondary));
            tvSpecs.setPadding(0, 6, 0, 8);
            card.addView(tvSpecs);

            // Row 3: Miter & Bevel High-Contrast Readout
            LinearLayout readoutRow = new LinearLayout(this);
            readoutRow.setOrientation(LinearLayout.HORIZONTAL);
            readoutRow.setBackgroundResource(R.drawable.card_background);
            readoutRow.setPadding(20, 14, 20, 14);

            TextView tvMiterOut = new TextView(this);
            tvMiterOut.setText(String.format(Locale.US, "Miter: %.1f°", miter));
            tvMiterOut.setTextSize(13);
            tvMiterOut.setTextColor(getResources().getColor(R.color.colorAccent));
            tvMiterOut.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lpM = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            readoutRow.addView(tvMiterOut, lpM);

            TextView tvBevelOut = new TextView(this);
            tvBevelOut.setText(String.format(Locale.US, "Bevel: %.1f°", bevel));
            tvBevelOut.setTextSize(13);
            tvBevelOut.setTextColor(getResources().getColor(R.color.colorAccent));
            tvBevelOut.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            readoutRow.addView(tvBevelOut, lpB);

            card.addView(readoutRow);
            llSavedCutsList.addView(card);
        }
    }

    // =========================================================================
    // SETTINGS MODAL & THEME MANAGEMENT
    // =========================================================================
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout customLayout = new LinearLayout(this);
        customLayout.setOrientation(LinearLayout.VERTICAL);
        customLayout.setPadding(48, 36, 48, 24);
        customLayout.setBackgroundResource(R.drawable.dialog_background);

        // Header Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Settings & Preferences");
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(getResources().getColor(R.color.textPrimary));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        customLayout.addView(tvTitle);

        // Theme Header
        TextView tvThemeLabel = new TextView(this);
        tvThemeLabel.setText("APP DISPLAY THEME (DARK / LIGHT / AUTO):");
        tvThemeLabel.setTextSize(11);
        tvThemeLabel.setTextColor(getResources().getColor(R.color.textSecondary));
        tvThemeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvThemeLabel.setPadding(0, 24, 0, 10);
        customLayout.addView(tvThemeLabel);

        // Theme Segmented Selector
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setBackgroundResource(R.drawable.bg_segmented_track);
        themeRow.setPadding(6, 6, 6, 6);

        final String currentTheme = prefs.getString(KEY_THEME_STR, "system");

        final Button btnThemeAuto = new Button(this, null, 0, R.style.CleanButton);
        btnThemeAuto.setText("Auto");
        btnThemeAuto.setTextSize(12);
        btnThemeAuto.setMinHeight(120);
        btnThemeAuto.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnThemeAuto.setBackgroundResource("system".equals(currentTheme) ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btnThemeAuto.setTextColor(getResources().getColor("system".equals(currentTheme) ? R.color.m3_on_primary : R.color.textPrimary));

        final Button btnThemeLight = new Button(this, null, 0, R.style.CleanButton);
        btnThemeLight.setText("Light");
        btnThemeLight.setTextSize(12);
        btnThemeLight.setMinHeight(120);
        btnThemeLight.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnThemeLight.setBackgroundResource("light".equals(currentTheme) ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btnThemeLight.setTextColor(getResources().getColor("light".equals(currentTheme) ? R.color.m3_on_primary : R.color.textPrimary));

        final Button btnThemeDark = new Button(this, null, 0, R.style.CleanButton);
        btnThemeDark.setText("Dark");
        btnThemeDark.setTextSize(12);
        btnThemeDark.setMinHeight(120);
        btnThemeDark.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnThemeDark.setBackgroundResource("dark".equals(currentTheme) ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        btnThemeDark.setTextColor(getResources().getColor("dark".equals(currentTheme) ? R.color.m3_on_primary : R.color.textPrimary));

        themeRow.addView(btnThemeAuto);
        themeRow.addView(btnThemeLight);
        themeRow.addView(btnThemeDark);
        customLayout.addView(themeRow);

        // About Card
        LinearLayout aboutCard = new LinearLayout(this);
        aboutCard.setOrientation(LinearLayout.VERTICAL);
        aboutCard.setBackgroundResource(R.drawable.card_background);
        aboutCard.setPadding(28, 20, 28, 20);
        LinearLayout.LayoutParams acParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acParams.setMargins(0, 28, 0, 16);
        aboutCard.setLayoutParams(acParams);

        TextView tvAboutTitle = new TextView(this);
        tvAboutTitle.setText("CrownCut v1.0.0");
        tvAboutTitle.setTextSize(14);
        tvAboutTitle.setTextColor(getResources().getColor(R.color.textPrimary));
        tvAboutTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        aboutCard.addView(tvAboutTitle);

        TextView tvAboutBody = new TextView(this);
        tvAboutBody.setText("KimRosil App Studio Company\n100% Offline Freeware • Zero Ads\nNo Accounts • Zero Data Collection");
        tvAboutBody.setTextSize(12);
        tvAboutBody.setTextColor(getResources().getColor(R.color.textSecondary));
        tvAboutBody.setPadding(0, 6, 0, 0);
        aboutCard.addView(tvAboutBody);
        customLayout.addView(aboutCard);

        builder.setView(customLayout);
        final AlertDialog dialog = builder.create();

        btnThemeAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setThemePreference("system");
                dialog.dismiss();
            }
        });
        btnThemeLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setThemePreference("light");
                dialog.dismiss();
            }
        });
        btnThemeDark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setThemePreference("dark");
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}
