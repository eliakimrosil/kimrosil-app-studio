package com.kimrosil.shimpack;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private MotorAlignmentCanvasView alignmentMotorView;
    private Button btnUnitImperial;
    private Button btnUnitMetric;
    private Button btnMethodRimFace;
    private Button btnMethodReverse;
    private Button btnThemeToggle;
    private Button btnStockManager;
    private Button btnPreset1;
    private Button btnPreset2;
    private Button btnPreset3;
    private Button btnCalculateShims;
    private Button btnSaveRecord;
    private Button btnShareReport;
    private Button btnReset;

    private Button btnDial12;
    private Button btnDial3;
    private Button btnDial6;
    private Button btnDial9;

    private EditText etMachineTag;
    private EditText etRpm;
    private EditText etD1Front;
    private EditText etD2Back;
    private EditText etD3DialDiam;
    private EditText etDialRimTir;
    private EditText etDialFaceTir;
    private EditText etIndicatorSag;

    private TextView tvToleranceBadge;
    private TextView tvF1ActionDirection;
    private TextView tvF1ShimTarget;
    private TextView tvF1ShimSubtitle;
    private TextView tvF1StackRecipe;
    private TextView tvF2ActionDirection;
    private TextView tvF2ShimTarget;
    private TextView tvF2ShimSubtitle;
    private TextView tvF2StackRecipe;
    private TextView tvAngularErrorVal;
    private TextView tvAngularErrorSub;
    private TextView tvParallelErrorVal;
    private TextView tvParallelErrorSub;

    private LinearLayout llEmptyState;
    private LinearLayout llResultDeck;
    private LinearLayout cardF1;
    private LinearLayout cardF2;

    private boolean isMetric = false;
    private boolean isReverseDial = false;
    private DatabaseHelper dbHelper;

    private AlignmentMathEngine.AlignmentResult lastResult = null;
    private ShimStackOptimizer.StackSolution lastF1Stack = null;
    private ShimStackOptimizer.StackSolution lastF2Stack = null;

    private final Set<Double> depletedStock = new HashSet<>();

    private static final String PREF_DEPLETED_STOCK = "pref_depleted_stock";
    private static final String KEY_IS_METRIC = "state_is_metric";
    private static final String KEY_IS_REVERSE = "state_is_reverse";
    private static final String KEY_MACHINE_TAG = "state_machine_tag";
    private static final String KEY_RPM = "state_rpm";
    private static final String KEY_D1 = "state_d1";
    private static final String KEY_D2 = "state_d2";
    private static final String KEY_D3 = "state_d3";
    private static final String KEY_RIM_TIR = "state_rim_tir";
    private static final String KEY_FACE_TIR = "state_face_tir";
    private static final String KEY_SAG = "state_sag";

    @Override
    protected void attachBaseContext(Context newBase) {
        try {
            SharedPreferences sp = newBase.getSharedPreferences("shimpack_prefs", Context.MODE_PRIVATE);
            int themeMode = sp.getInt("pref_theme_mode", 0); // 0=Auto, 1=Light, 2=Dark
            Configuration config = new Configuration(newBase.getResources().getConfiguration());

            if (themeMode == 0) {
                int currentNightMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                    config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
                } else {
                    config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
                }
            } else if (themeMode == 1) {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            } else {
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            }
            super.attachBaseContext(newBase.createConfigurationContext(config));
        } catch (Throwable t) {
            super.attachBaseContext(newBase);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        loadDepletedStockPreferences();
        initViews();
        setupInputFilters();
        setupThemeBar();
        setupListeners();

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            loadSavedInputs();
        }
    }

    private void initViews() {
        alignmentMotorView = findViewById(R.id.alignmentMotorView);
        btnUnitImperial = findViewById(R.id.btnUnitImperial);
        btnUnitMetric = findViewById(R.id.btnUnitMetric);
        btnMethodRimFace = findViewById(R.id.btnMethodRimFace);
        btnMethodReverse = findViewById(R.id.btnMethodReverse);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnStockManager = findViewById(R.id.btnStockManager);
        btnPreset1 = findViewById(R.id.btnPreset1);
        btnPreset2 = findViewById(R.id.btnPreset2);
        btnPreset3 = findViewById(R.id.btnPreset3);
        btnCalculateShims = findViewById(R.id.btnCalculateShims);
        btnSaveRecord = findViewById(R.id.btnSaveRecord);
        btnShareReport = findViewById(R.id.btnShareReport);
        btnReset = findViewById(R.id.btnReset);

        btnDial12 = findViewById(R.id.btnDial12);
        btnDial3 = findViewById(R.id.btnDial3);
        btnDial6 = findViewById(R.id.btnDial6);
        btnDial9 = findViewById(R.id.btnDial9);

        etMachineTag = findViewById(R.id.etMachineTag);
        etRpm = findViewById(R.id.etRpm);
        etD1Front = findViewById(R.id.etD1Front);
        etD2Back = findViewById(R.id.etD2Back);
        etD3DialDiam = findViewById(R.id.etD3DialDiam);
        etDialRimTir = findViewById(R.id.etDialRimTir);
        etDialFaceTir = findViewById(R.id.etDialFaceTir);
        etIndicatorSag = findViewById(R.id.etIndicatorSag);

        tvToleranceBadge = findViewById(R.id.tvToleranceBadge);
        tvF1ActionDirection = findViewById(R.id.tvF1ActionDirection);
        tvF1ShimTarget = findViewById(R.id.tvF1ShimTarget);
        tvF1ShimSubtitle = findViewById(R.id.tvF1ShimSubtitle);
        tvF1StackRecipe = findViewById(R.id.tvF1StackRecipe);
        tvF2ActionDirection = findViewById(R.id.tvF2ActionDirection);
        tvF2ShimTarget = findViewById(R.id.tvF2ShimTarget);
        tvF2ShimSubtitle = findViewById(R.id.tvF2ShimSubtitle);
        tvF2StackRecipe = findViewById(R.id.tvF2StackRecipe);
        tvAngularErrorVal = findViewById(R.id.tvAngularErrorVal);
        tvAngularErrorSub = findViewById(R.id.tvAngularErrorSub);
        tvParallelErrorVal = findViewById(R.id.tvParallelErrorVal);
        tvParallelErrorSub = findViewById(R.id.tvParallelErrorSub);

        llEmptyState = findViewById(R.id.llEmptyState);
        llResultDeck = findViewById(R.id.llResultDeck);
        cardF1 = findViewById(R.id.cardF1);
        cardF2 = findViewById(R.id.cardF2);
    }

    private void setupInputFilters() {
        InputFilter numericLengthFilter = new InputFilter.LengthFilter(8);

        InputFilter positiveDecimalFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                String candidate = dest.subSequence(0, dstart).toString() +
                        source.subSequence(start, end).toString() +
                        dest.subSequence(dend, dest.length()).toString();
                if (candidate.isEmpty()) return null;
                if (candidate.startsWith("-") || candidate.startsWith("+")) return "";
                if (!candidate.matches("^\\d*(\\.\\d{0,4})?$")) {
                    return "";
                }
                return null;
            }
        };

        InputFilter signedDecimalFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                String candidate = dest.subSequence(0, dstart).toString() +
                        source.subSequence(start, end).toString() +
                        dest.subSequence(dend, dest.length()).toString();
                if (candidate.isEmpty() || candidate.equals("-") || candidate.equals("+")) return null;
                if (!candidate.matches("^[+-]?\\d*(\\.\\d{0,4})?$")) {
                    return "";
                }
                return null;
            }
        };

        if (etD1Front != null) etD1Front.setFilters(new InputFilter[]{numericLengthFilter, positiveDecimalFilter});
        if (etD2Back != null) etD2Back.setFilters(new InputFilter[]{numericLengthFilter, positiveDecimalFilter});
        if (etD3DialDiam != null) etD3DialDiam.setFilters(new InputFilter[]{numericLengthFilter, positiveDecimalFilter});
        if (etIndicatorSag != null) etIndicatorSag.setFilters(new InputFilter[]{numericLengthFilter, positiveDecimalFilter});
        if (etRpm != null) etRpm.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        if (etMachineTag != null) etMachineTag.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});

        if (etDialRimTir != null) etDialRimTir.setFilters(new InputFilter[]{numericLengthFilter, signedDecimalFilter});
        if (etDialFaceTir != null) etDialFaceTir.setFilters(new InputFilter[]{numericLengthFilter, signedDecimalFilter});
    }

    private void setupThemeBar() {
        SharedPreferences sp = getSharedPreferences("shimpack_prefs", Context.MODE_PRIVATE);
        int themeMode = sp.getInt("pref_theme_mode", 0);
        if (btnThemeToggle != null) {
            if (themeMode == 1) {
                btnThemeToggle.setText("LIGHT");
            } else if (themeMode == 2) {
                btnThemeToggle.setText("DARK");
            } else {
                btnThemeToggle.setText("AUTO");
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && getWindow() != null) {
            WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                if (!isNight) {
                    wic.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                } else {
                    wic.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }
    }

    private void setupListeners() {
        if (btnThemeToggle != null) {
            btnThemeToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    cycleThemeMode();
                }
            });
        }

        if (btnStockManager != null) {
            btnStockManager.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    showStockInventoryDialog();
                }
            });
        }

        if (btnUnitImperial != null) {
            btnUnitImperial.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    setUnitSystem(false);
                }
            });
        }

        if (btnUnitMetric != null) {
            btnUnitMetric.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    setUnitSystem(true);
                }
            });
        }

        if (btnMethodRimFace != null) {
            btnMethodRimFace.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    setAlignmentMethod(false);
                }
            });
        }

        if (btnMethodReverse != null) {
            btnMethodReverse.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    setAlignmentMethod(true);
                }
            });
        }

        // Clock sweep position listeners with tactile CLOCK_TICK feedback
        if (btnDial12 != null) {
            btnDial12.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    Toast.makeText(MainActivity.this, "12:00 Top Zero Position (0° Reference)", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnDial3 != null) {
            btnDial3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    Toast.makeText(MainActivity.this, "3:00 Sweep Position (+90° Horizontal)", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnDial6 != null) {
            btnDial6.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    Toast.makeText(MainActivity.this, "6:00 Bottom Sweep Position (180° Vertical TIR)", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnDial9 != null) {
            btnDial9.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    Toast.makeText(MainActivity.this, "9:00 Sweep Position (270° Horizontal)", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Tactile feedback on foot solution cards
        if (cardF1 != null) {
            cardF1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    showFootDetailDialog(1, lastResult != null ? lastResult.frontFootTarget : 0.0, lastF1Stack);
                }
            });
        }

        if (cardF2 != null) {
            cardF2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    showFootDetailDialog(2, lastResult != null ? lastResult.backFootTarget : 0.0, lastF2Stack);
                }
            });
        }

        if (btnPreset1 != null) {
            btnPreset1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    loadPreset("Pump P-101 (1800 RPM)", 8.5, 14.0, 5.0, 1800);
                }
            });
        }
        if (btnPreset2 != null) {
            btnPreset2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    loadPreset("Compressor C-204 (3600 RPM)", 12.0, 22.0, 6.5, 3600);
                }
            });
        }
        if (btnPreset3 != null) {
            btnPreset3.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    loadPreset("Slurry Pump F-501 (1200 RPM)", 15.0, 28.0, 8.0, 1200);
                }
            });
        }

        if (btnCalculateShims != null) {
            btnCalculateShims.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    dismissKeyboard();
                    performCalculation();
                }
            });
        }

        if (btnReset != null) {
            btnReset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    resetInputs();
                }
            });
        }

        if (btnSaveRecord != null) {
            btnSaveRecord.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                    saveCurrentAlignment();
                }
            });
        }

        if (btnShareReport != null) {
            btnShareReport.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    shareAuditReport();
                }
            });
        }

        if (alignmentMotorView != null) {
            alignmentMotorView.setOnFootSelectedListener(new MotorAlignmentCanvasView.OnFootSelectedListener() {
                @Override
                public void onFootTapped(int footIndex, double target, List<Double> stack) {
                    if (alignmentMotorView != null) {
                        alignmentMotorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    ShimStackOptimizer.StackSolution solution = (footIndex == 1) ? lastF1Stack : lastF2Stack;
                    showFootDetailDialog(footIndex, target, solution);
                }

                @Override
                public void onCouplingToggled() {
                    if (alignmentMotorView != null) {
                        alignmentMotorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    setAlignmentMethod(!isReverseDial);
                }
            });
        }
    }

    private void showFootDetailDialog(int footIndex, double target, ShimStackOptimizer.StackSolution solution) {
        String unit = isMetric ? "mm" : "in";
        String action = target >= 0 ? "ADD" : "REMOVE";
        String recipe = (solution != null) ? solution.formatRecipe(isMetric) : "[None]";
        String softFoot = (solution != null && solution.softFootRisk) ? "\n\n⚠️ Warning: Stack exceeds 4 shims or has residual error. Elastic soft foot risk!" : "";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle((footIndex == 1 ? "Inboard Foot (F1)" : "Outboard Foot (F2)") + " Stack Detail")
                .setMessage("Target Thickness: " + String.format("%.3f %s", Math.abs(target), unit) + "\n" +
                        "Correction Action: " + action + "\n\n" +
                        "Slotted Shim Stack:\n" + recipe + softFoot)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showStockInventoryDialog() {
        double[] stockArray = isMetric ? ShimStackOptimizer.METRIC_STOCK : ShimStackOptimizer.IMPERIAL_STOCK;
        String[] stockLabels = new String[stockArray.length];
        boolean[] checkedItems = new boolean[stockArray.length];

        for (int i = 0; i < stockArray.length; i++) {
            double thickness = stockArray[i];
            stockLabels[i] = isMetric ? String.format("%.2f mm", thickness) : String.format("%.3f in", thickness);
            boolean isDepleted = false;
            for (Double d : depletedStock) {
                if (Math.abs(d - thickness) < 0.00001) {
                    isDepleted = true;
                    break;
                }
            }
            checkedItems[i] = isDepleted;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Stock Depletion Manager")
                .setMultiChoiceItems(stockLabels, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        double val = stockArray[which];
                        if (isChecked) {
                            depletedStock.add(val);
                        } else {
                            Double toRemove = null;
                            for (Double d : depletedStock) {
                                if (Math.abs(d - val) < 0.00001) {
                                    toRemove = d;
                                    break;
                                }
                            }
                            if (toRemove != null) depletedStock.remove(toRemove);
                        }
                    }
                })
                .setPositiveButton("APPLY & RE-SOLVE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveDepletedStockPreferences();
                        performCalculation();
                        Toast.makeText(MainActivity.this, "Depleted inventory updated. Stacks recalculated.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void loadDepletedStockPreferences() {
        SharedPreferences sp = getSharedPreferences("shimpack_prefs", Context.MODE_PRIVATE);
        Set<String> set = sp.getStringSet(PREF_DEPLETED_STOCK, null);
        depletedStock.clear();
        if (set != null) {
            for (String s : set) {
                try {
                    depletedStock.add(Double.parseDouble(s));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void saveDepletedStockPreferences() {
        SharedPreferences sp = getSharedPreferences("shimpack_prefs", Context.MODE_PRIVATE);
        Set<String> set = new HashSet<>();
        for (Double d : depletedStock) {
            set.add(String.valueOf(d));
        }
        sp.edit().putStringSet(PREF_DEPLETED_STOCK, set).apply();
    }

    private void cycleThemeMode() {
        SharedPreferences sp = getSharedPreferences("shimpack_prefs", Context.MODE_PRIVATE);
        int current = sp.getInt("pref_theme_mode", 0);
        int next = (current + 1) % 3;
        sp.edit().putInt("pref_theme_mode", next).apply();
        recreate();
    }

    private void setUnitSystem(boolean metric) {
        if (this.isMetric == metric) return;
        this.isMetric = metric;
        if (btnUnitImperial != null) {
            btnUnitImperial.setBackgroundResource(metric ? R.drawable.bg_pill_unselected : R.drawable.bg_pill_selected);
        }
        if (btnUnitMetric != null) {
            btnUnitMetric.setBackgroundResource(metric ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
        }
        if (etD1Front != null) etD1Front.setHint(metric ? "D1 Front (mm)" : "D1 Front (in)");
        if (etD2Back != null) etD2Back.setHint(metric ? "D2 Back (mm)" : "D2 Back (in)");
        if (etD3DialDiam != null) etD3DialDiam.setHint(metric ? "Dial Dia (mm)" : "Dial Dia (in)");
        if (etDialRimTir != null) etDialRimTir.setHint(metric ? "Rim TIR (mm)" : "Rim TIR (mil)");
        if (etDialFaceTir != null) etDialFaceTir.setHint(metric ? "Face TIR (mm)" : "Face TIR (mil)");
        performCalculation();
    }

    private void setAlignmentMethod(boolean reverse) {
        this.isReverseDial = reverse;
        if (btnMethodRimFace != null) {
            btnMethodRimFace.setBackgroundResource(reverse ? R.drawable.bg_pill_unselected : R.drawable.bg_pill_selected);
        }
        if (btnMethodReverse != null) {
            btnMethodReverse.setBackgroundResource(reverse ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
        }
        if (etD3DialDiam != null) etD3DialDiam.setHint(reverse ? "Indicator Span" : "Dial Dia");
        if (etDialFaceTir != null) etDialFaceTir.setHint(reverse ? "Movable Rim TIR" : "Face TIR");
        performCalculation();
    }

    private void loadPreset(String tag, double d1, double d2, double d3, int rpm) {
        if (etMachineTag != null) etMachineTag.setText(tag);
        if (etD1Front != null) etD1Front.setText(String.valueOf(isMetric ? d1 * 25.4 : d1));
        if (etD2Back != null) etD2Back.setText(String.valueOf(isMetric ? d2 * 25.4 : d2));
        if (etD3DialDiam != null) etD3DialDiam.setText(String.valueOf(isMetric ? d3 * 25.4 : d3));
        if (etRpm != null) etRpm.setText(String.valueOf(rpm));
        if (etDialRimTir != null) etDialRimTir.setText(isMetric ? "0.15" : "6.0");
        if (etDialFaceTir != null) etDialFaceTir.setText(isMetric ? "0.08" : "3.5");
        if (etIndicatorSag != null) etIndicatorSag.setText(isMetric ? "0.02" : "1.0");
        performCalculation();
    }

    private void performCalculation() {
        if (etD1Front == null || etD2Back == null || etD3DialDiam == null ||
                etRpm == null || etDialRimTir == null || etDialFaceTir == null || etIndicatorSag == null) {
            return;
        }

        double d1 = parseDouble(etD1Front.getText().toString(), 10.0);
        double d2 = parseDouble(etD2Back.getText().toString(), 15.0);
        double d3 = parseDouble(etD3DialDiam.getText().toString(), 5.0);
        int rpm = parseInt(etRpm.getText().toString(), 1800);
        double rimTir = parseDouble(etDialRimTir.getText().toString(), 0.0);
        double faceTir = parseDouble(etDialFaceTir.getText().toString(), 0.0);
        double sag = parseDouble(etIndicatorSag.getText().toString(), 0.0);

        // Input Sanity Check
        if (d1 <= 0.01 || d2 <= 0.01 || d3 <= 0.01) {
            if (getWindow() != null && getWindow().getDecorView() != null) {
                getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.REJECT);
            }
            Toast.makeText(this, "Distances D1, D2, and Dial Span must be positive numbers.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Sag deduction on rim readings (Sag drops dial reading at 6 o'clock)
        double correctedRim = rimTir - sag;

        // Convert Mils to Inches for math solver if imperial
        double calcRim = isMetric ? correctedRim : (correctedRim / 1000.0);
        double calcFace = isMetric ? faceTir : (faceTir / 1000.0);

        if (isReverseDial) {
            lastResult = AlignmentMathEngine.solveReverseDial(d1, d2, d3, calcRim, calcFace, rpm, isMetric);
        } else {
            lastResult = AlignmentMathEngine.solveRimFace(d1, d2, d3, calcRim, calcFace, rpm, isMetric);
        }

        if (lastResult != null) {
            lastF1Stack = ShimStackOptimizer.solve(lastResult.frontFootTarget, isMetric, depletedStock);
            lastF2Stack = ShimStackOptimizer.solve(lastResult.backFootTarget, isMetric, depletedStock);
        }

        renderResults();
        updateNotificationHud();
    }

    private void renderResults() {
        if (lastResult == null || lastF1Stack == null || lastF2Stack == null) return;

        if (llEmptyState != null) llEmptyState.setVisibility(View.GONE);
        if (llResultDeck != null) llResultDeck.setVisibility(View.VISIBLE);

        // Update Motor Telemetry Canvas
        if (alignmentMotorView != null) {
            List<Double> f1Shims = lastF1Stack.shims != null ? lastF1Stack.shims : Collections.emptyList();
            List<Double> f2Shims = lastF2Stack.shims != null ? lastF2Stack.shims : Collections.emptyList();
            alignmentMotorView.updateTelemetry(
                    lastResult.frontFootTarget,
                    lastResult.backFootTarget,
                    lastResult.angularMisalignment,
                    isMetric,
                    isReverseDial,
                    f1Shims,
                    f2Shims
            );
        }

        // Tolerance Badge
        if (tvToleranceBadge != null) {
            tvToleranceBadge.setText("STATUS: " + lastResult.toleranceStatus);
            if ("ACCEPTABLE".equals(lastResult.toleranceStatus)) {
                tvToleranceBadge.setBackgroundColor(Color.parseColor("#065F46")); // Forest Green
                tvToleranceBadge.setTextColor(Color.parseColor("#A7F3D0"));
            } else if ("MARGINAL".equals(lastResult.toleranceStatus)) {
                tvToleranceBadge.setBackgroundColor(Color.parseColor("#92400E")); // Amber
                tvToleranceBadge.setTextColor(Color.parseColor("#FDE68A"));
            } else {
                tvToleranceBadge.setBackgroundColor(Color.parseColor("#991B1B")); // Red
                tvToleranceBadge.setTextColor(Color.parseColor("#FECACA"));
            }
        }

        // F1 Readout (Stacked numbers and secondary metric equivalents)
        double absF1 = Math.abs(lastResult.frontFootTarget);
        if (tvF1ActionDirection != null) {
            tvF1ActionDirection.setText(lastResult.frontFootTarget >= 0 ? "ADD SHIM (+)" : "REMOVE SHIM (-)");
            tvF1ActionDirection.setTextColor(lastResult.frontFootTarget >= 0 ? Color.parseColor("#38BDF8") : Color.parseColor("#F43F5E"));
        }
        if (tvF1ShimTarget != null) {
            tvF1ShimTarget.setText(String.format(lastResult.frontFootTarget >= 0 ? "+%.3f" : "-%.3f", absF1));
        }
        if (tvF1ShimSubtitle != null) {
            if (isMetric) {
                double inEquiv = absF1 / 25.4;
                tvF1ShimSubtitle.setText(String.format("mm (%.3f in)", inEquiv));
            } else {
                double mmEquiv = absF1 * 25.4;
                tvF1ShimSubtitle.setText(String.format("in (%.3f mm)", mmEquiv));
            }
        }
        if (tvF1StackRecipe != null) {
            tvF1StackRecipe.setText(lastF1Stack.formatRecipe(isMetric) +
                    (lastF1Stack.softFootRisk ? " [WARN: Soft Foot Risk! >4 Shims]" : ""));
        }

        // F2 Readout (Stacked numbers and secondary metric equivalents)
        double absF2 = Math.abs(lastResult.backFootTarget);
        if (tvF2ActionDirection != null) {
            tvF2ActionDirection.setText(lastResult.backFootTarget >= 0 ? "ADD SHIM (+)" : "REMOVE SHIM (-)");
            tvF2ActionDirection.setTextColor(lastResult.backFootTarget >= 0 ? Color.parseColor("#38BDF8") : Color.parseColor("#F43F5E"));
        }
        if (tvF2ShimTarget != null) {
            tvF2ShimTarget.setText(String.format(lastResult.backFootTarget >= 0 ? "+%.3f" : "-%.3f", absF2));
        }
        if (tvF2ShimSubtitle != null) {
            if (isMetric) {
                double inEquiv = absF2 / 25.4;
                tvF2ShimSubtitle.setText(String.format("mm (%.3f in)", inEquiv));
            } else {
                double mmEquiv = absF2 * 25.4;
                tvF2ShimSubtitle.setText(String.format("in (%.3f mm)", mmEquiv));
            }
        }
        if (tvF2StackRecipe != null) {
            tvF2StackRecipe.setText(lastF2Stack.formatRecipe(isMetric) +
                    (lastF2Stack.softFootRisk ? " [WARN: Soft Foot Risk! >4 Shims]" : ""));
        }

        // Misalignment Readouts
        if (tvAngularErrorVal != null) {
            tvAngularErrorVal.setText(String.format("%.3f°", lastResult.angularMisalignment));
        }
        if (tvAngularErrorSub != null) {
            double mrad = Math.toRadians(lastResult.angularMisalignment) * 1000.0;
            tvAngularErrorSub.setText(String.format("%.2f mrad", mrad));
        }

        if (tvParallelErrorVal != null) {
            tvParallelErrorVal.setText(String.format("%.3f %s", lastResult.parallelMisalignment, isMetric ? "mm" : "in"));
        }
        if (tvParallelErrorSub != null) {
            if (isMetric) {
                tvParallelErrorSub.setText(String.format("%.3f in", lastResult.parallelMisalignment / 25.4));
            } else {
                tvParallelErrorSub.setText(String.format("%.3f mm", lastResult.parallelMisalignment * 25.4));
            }
        }
    }

    private void updateNotificationHud() {
        if (lastResult == null) return;
        String tag = etMachineTag != null ? etMachineTag.getText().toString().trim() : "Pump Shaft";
        String unit = isMetric ? "mm" : "in";
        String f1 = (lastResult.frontFootTarget >= 0 ? "+" : "-") + String.format("%.3f %s", Math.abs(lastResult.frontFootTarget), unit);
        String f2 = (lastResult.backFootTarget >= 0 ? "+" : "-") + String.format("%.3f %s", Math.abs(lastResult.backFootTarget), unit);
        AlignmentNotificationHud.showHud(this, tag, f1, f2, lastResult.toleranceStatus);
    }

    private void saveCurrentAlignment() {
        if (lastResult == null || lastF1Stack == null || lastF2Stack == null) {
            Toast.makeText(this, "Perform calculation before saving.", Toast.LENGTH_SHORT).show();
            return;
        }
        String tag = etMachineTag != null ? etMachineTag.getText().toString().trim() : "";
        if (TextUtils.isEmpty(tag)) tag = "UNTAGGED_MACHINE";

        long id = dbHelper.saveRecord(
                tag,
                isReverseDial ? "Reverse Dial" : "Rim-Face",
                lastResult.frontFootTarget,
                lastResult.backFootTarget,
                lastF1Stack.formatRecipe(isMetric),
                lastF2Stack.formatRecipe(isMetric),
                lastResult.toleranceStatus
        );

        if (id > 0) {
            Toast.makeText(this, "Record saved to local log (ID: " + id + ")", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to save record.", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareAuditReport() {
        if (lastResult == null || lastF1Stack == null || lastF2Stack == null) {
            Toast.makeText(this, "No alignment data to share.", Toast.LENGTH_SHORT).show();
            return;
        }

        String tag = etMachineTag != null ? etMachineTag.getText().toString().trim() : "";
        if (TextUtils.isEmpty(tag)) tag = "PUMP/MOTOR SHAFT";

        String report = "=== SHIMPACK ALIGNMENT AUDIT LOG ===\n" +
                "Machine Tag: " + tag + "\n" +
                "Method: " + (isReverseDial ? "Reverse Dial" : "Rim-Face") + "\n" +
                "Tolerance Status: " + lastResult.toleranceStatus + "\n" +
                "Front Foot (F1): " + (lastResult.frontFootTarget >= 0 ? "ADD " : "REMOVE ") +
                String.format("%.3f", Math.abs(lastResult.frontFootTarget)) + (isMetric ? " mm" : " in") + "\n" +
                "F1 Shim Pack: " + lastF1Stack.formatRecipe(isMetric) + "\n" +
                "Back Foot (F2): " + (lastResult.backFootTarget >= 0 ? "ADD " : "REMOVE ") +
                String.format("%.3f", Math.abs(lastResult.backFootTarget)) + (isMetric ? " mm" : " in") + "\n" +
                "F2 Shim Pack: " + lastF2Stack.formatRecipe(isMetric) + "\n" +
                "Angular Error: " + String.format("%.3f°", lastResult.angularMisalignment) + "\n" +
                "Parallel Error: " + String.format("%.3f %s", lastResult.parallelMisalignment, isMetric ? "mm" : "in") + "\n" +
                "Timestamp: " + System.currentTimeMillis();

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("ShimPack Report", report));
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "ShimPack Alignment: " + tag);
        shareIntent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(shareIntent, "Share Alignment Audit"));
    }

    private void resetInputs() {
        if (etDialRimTir != null) etDialRimTir.setText("0.0");
        if (etDialFaceTir != null) etDialFaceTir.setText("0.0");
        if (etIndicatorSag != null) etIndicatorSag.setText("0.0");
        if (llResultDeck != null) llResultDeck.setVisibility(View.GONE);
        if (llEmptyState != null) llEmptyState.setVisibility(View.VISIBLE);
        if (alignmentMotorView != null) {
            alignmentMotorView.updateTelemetry(0, 0, 0, isMetric, isReverseDial, null, null);
        }
        AlignmentNotificationHud.dismissHud(this);
        Toast.makeText(this, "Dial sweep inputs cleared.", Toast.LENGTH_SHORT).show();
    }

    private void loadSavedInputs() {
        if (etMachineTag != null) etMachineTag.setText("P-101 End Suction Pump");
        if (etRpm != null) etRpm.setText("1800");
        if (etD1Front != null) etD1Front.setText("8.5");
        if (etD2Back != null) etD2Back.setText("14.0");
        if (etD3DialDiam != null) etD3DialDiam.setText("5.0");
        if (etDialRimTir != null) etDialRimTir.setText("5.0");
        if (etDialFaceTir != null) etDialFaceTir.setText("3.0");
        if (etIndicatorSag != null) etIndicatorSag.setText("0.5");
        performCalculation();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_METRIC, isMetric);
        outState.putBoolean(KEY_IS_REVERSE, isReverseDial);
        if (etMachineTag != null) outState.putString(KEY_MACHINE_TAG, etMachineTag.getText().toString());
        if (etRpm != null) outState.putString(KEY_RPM, etRpm.getText().toString());
        if (etD1Front != null) outState.putString(KEY_D1, etD1Front.getText().toString());
        if (etD2Back != null) outState.putString(KEY_D2, etD2Back.getText().toString());
        if (etD3DialDiam != null) outState.putString(KEY_D3, etD3DialDiam.getText().toString());
        if (etDialRimTir != null) outState.putString(KEY_RIM_TIR, etDialRimTir.getText().toString());
        if (etDialFaceTir != null) outState.putString(KEY_FACE_TIR, etDialFaceTir.getText().toString());
        if (etIndicatorSag != null) outState.putString(KEY_SAG, etIndicatorSag.getText().toString());
    }

    private void restoreInstanceState(Bundle state) {
        this.isMetric = state.getBoolean(KEY_IS_METRIC, false);
        this.isReverseDial = state.getBoolean(KEY_IS_REVERSE, false);

        if (etMachineTag != null) etMachineTag.setText(state.getString(KEY_MACHINE_TAG, ""));
        if (etRpm != null) etRpm.setText(state.getString(KEY_RPM, "1800"));
        if (etD1Front != null) etD1Front.setText(state.getString(KEY_D1, "8.5"));
        if (etD2Back != null) etD2Back.setText(state.getString(KEY_D2, "14.0"));
        if (etD3DialDiam != null) etD3DialDiam.setText(state.getString(KEY_D3, "5.0"));
        if (etDialRimTir != null) etDialRimTir.setText(state.getString(KEY_RIM_TIR, "0.0"));
        if (etDialFaceTir != null) etDialFaceTir.setText(state.getString(KEY_FACE_TIR, "0.0"));
        if (etIndicatorSag != null) etIndicatorSag.setText(state.getString(KEY_SAG, "0.0"));

        if (btnUnitImperial != null) {
            btnUnitImperial.setBackgroundResource(isMetric ? R.drawable.bg_pill_unselected : R.drawable.bg_pill_selected);
        }
        if (btnUnitMetric != null) {
            btnUnitMetric.setBackgroundResource(isMetric ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
        }
        if (btnMethodRimFace != null) {
            btnMethodRimFace.setBackgroundResource(isReverseDial ? R.drawable.bg_pill_unselected : R.drawable.bg_pill_selected);
        }
        if (btnMethodReverse != null) {
            btnMethodReverse.setBackgroundResource(isReverseDial ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
        }

        performCalculation();
    }

    private void dismissKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View v = getCurrentFocus();
        if (imm != null && v != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                performCalculation();
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_SPACE && (getCurrentFocus() == null || !(getCurrentFocus() instanceof EditText))) {
                setUnitSystem(!isMetric);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private double parseDouble(String str, double def) {
        if (TextUtils.isEmpty(str)) return def;
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int parseInt(String str, int def) {
        if (TextUtils.isEmpty(str)) return def;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}