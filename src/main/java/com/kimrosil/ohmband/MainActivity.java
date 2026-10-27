package com.kimrosil.ohmband;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "ohm_prefs";
    private static final String KEY_THEME = "theme_mode";

    private static final int MODE_4_BAND = 4;
    private static final int MODE_5_BAND = 5;
    private static final int MODE_6_BAND = 6;
    private static final int MODE_SMD = 0;

    private int currentMode = MODE_4_BAND;
    private int currentBandTab = 0; // 0=B1, 1=B2, 2=B3, 3=Mult, 4=Tol, 5=TCR

    // Active band colors
    private int band1 = OhmEngine.COLOR_BROWN;
    private int band2 = OhmEngine.COLOR_BLACK;
    private int band3 = OhmEngine.COLOR_BLACK;
    private int bandMult = OhmEngine.COLOR_RED;
    private int bandTol = OhmEngine.COLOR_GOLD;
    private int bandTcr = OhmEngine.COLOR_RED;

    private OhmDatabaseHelper dbHelper;
    private OhmEngine.ResistorResult lastResult;
    private OhmEngine.SmdResult lastSmdResult;

    // Views
    private ResistorCanvasView resistorCanvas;
    private TextView tvNominalOhms;
    private TextView tvToleranceRange;
    private TextView tvEiaSeries;
    private TextView tvTcrValue;

    private Button btnMode4Band;
    private Button btnMode5Band;
    private Button btnMode6Band;
    private Button btnModeSmd;

    private LinearLayout layoutHeroCard;
    private LinearLayout layoutBandWorkArea;
    private LinearLayout layoutReverseSynth;
    private LinearLayout layoutSmdArea;

    private Button tabBand1;
    private Button tabBand2;
    private Button tabBand3;
    private View spaceBand3;
    private Button tabBandMult;
    private Button tabBandTol;
    private Button tabBandTcr;
    private View spaceBandTcr;

    private LinearLayout layoutColorPalette;
    private EditText etDirectOhms;
    private Button btnSynthesize;

    private EditText etSmdCode;
    private Button btnDecodeSmd;
    private TextView tvSmdResultOhms;
    private TextView tvSmdResultType;

    private Button btnSavePart;
    private Button btnCopySpec;
    private Button btnClearParts;
    private LinearLayout layoutPartsList;
    private TextView tvEmptyParts;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String themeMode = sp.getString(KEY_THEME, "auto");

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if ("light".equals(themeMode)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if ("dark".equals(themeMode)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new OhmDatabaseHelper(this);
        bindViews();
        setupListeners();
        updateMode(MODE_4_BAND);
        refreshPartsList();
    }

    private void bindViews() {
        resistorCanvas = findViewById(R.id.resistorCanvas);
        tvNominalOhms = findViewById(R.id.tvNominalOhms);
        tvToleranceRange = findViewById(R.id.tvToleranceRange);
        tvEiaSeries = findViewById(R.id.tvEiaSeries);
        tvTcrValue = findViewById(R.id.tvTcrValue);

        btnMode4Band = findViewById(R.id.btnMode4Band);
        btnMode5Band = findViewById(R.id.btnMode5Band);
        btnMode6Band = findViewById(R.id.btnMode6Band);
        btnModeSmd = findViewById(R.id.btnModeSmd);

        layoutHeroCard = findViewById(R.id.layoutHeroCard);
        layoutBandWorkArea = findViewById(R.id.layoutBandWorkArea);
        layoutReverseSynth = findViewById(R.id.layoutReverseSynth);
        layoutSmdArea = findViewById(R.id.layoutSmdArea);

        tabBand1 = findViewById(R.id.tabBand1);
        tabBand2 = findViewById(R.id.tabBand2);
        tabBand3 = findViewById(R.id.tabBand3);
        spaceBand3 = findViewById(R.id.spaceBand3);
        tabBandMult = findViewById(R.id.tabBandMult);
        tabBandTol = findViewById(R.id.tabBandTol);
        tabBandTcr = findViewById(R.id.tabBandTcr);
        spaceBandTcr = findViewById(R.id.spaceBandTcr);

        layoutColorPalette = findViewById(R.id.layoutColorPalette);
        etDirectOhms = findViewById(R.id.etDirectOhms);
        btnSynthesize = findViewById(R.id.btnSynthesize);

        etSmdCode = findViewById(R.id.etSmdCode);
        btnDecodeSmd = findViewById(R.id.btnDecodeSmd);
        tvSmdResultOhms = findViewById(R.id.tvSmdResultOhms);
        tvSmdResultType = findViewById(R.id.tvSmdResultType);

        btnSavePart = findViewById(R.id.btnSavePart);
        btnCopySpec = findViewById(R.id.btnCopySpec);
        btnClearParts = findViewById(R.id.btnClearParts);
        layoutPartsList = findViewById(R.id.layoutPartsList);
        tvEmptyParts = findViewById(R.id.tvEmptyParts);

        ImageButton btnTheme = findViewById(R.id.btnTheme);
        ImageButton btnSettings = findViewById(R.id.btnSettings);

        btnTheme.setOnClickListener(v -> cycleTheme());
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void setupListeners() {
        btnMode4Band.setOnClickListener(v -> updateMode(MODE_4_BAND));
        btnMode5Band.setOnClickListener(v -> updateMode(MODE_5_BAND));
        btnMode6Band.setOnClickListener(v -> updateMode(MODE_6_BAND));
        btnModeSmd.setOnClickListener(v -> updateMode(MODE_SMD));

        tabBand1.setOnClickListener(v -> selectBandTab(0));
        tabBand2.setOnClickListener(v -> selectBandTab(1));
        tabBand3.setOnClickListener(v -> selectBandTab(2));
        tabBandMult.setOnClickListener(v -> selectBandTab(3));
        tabBandTol.setOnClickListener(v -> selectBandTab(4));
        tabBandTcr.setOnClickListener(v -> selectBandTab(5));

        btnSynthesize.setOnClickListener(v -> performReverseSynthesis());
        btnDecodeSmd.setOnClickListener(v -> performSmdDecode());

        btnSavePart.setOnClickListener(v -> saveCurrentPart());
        btnCopySpec.setOnClickListener(v -> copySpecificationsToClipboard());
        btnClearParts.setOnClickListener(v -> clearAllParts());
    }

    private void updateMode(int mode) {
        this.currentMode = mode;

        btnMode4Band.setBackgroundResource(mode == MODE_4_BAND ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnMode4Band.setTextColor(getColor(mode == MODE_4_BAND ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnMode5Band.setBackgroundResource(mode == MODE_5_BAND ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnMode5Band.setTextColor(getColor(mode == MODE_5_BAND ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnMode6Band.setBackgroundResource(mode == MODE_6_BAND ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnMode6Band.setTextColor(getColor(mode == MODE_6_BAND ? R.color.chip_text_selected : R.color.chip_text_unselected));

        btnModeSmd.setBackgroundResource(mode == MODE_SMD ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        btnModeSmd.setTextColor(getColor(mode == MODE_SMD ? R.color.chip_text_selected : R.color.chip_text_unselected));

        if (mode == MODE_SMD) {
            layoutHeroCard.setVisibility(View.GONE);
            layoutBandWorkArea.setVisibility(View.GONE);
            layoutReverseSynth.setVisibility(View.GONE);
            layoutSmdArea.setVisibility(View.VISIBLE);
            performSmdDecode();
        } else {
            layoutHeroCard.setVisibility(View.VISIBLE);
            layoutBandWorkArea.setVisibility(View.VISIBLE);
            layoutReverseSynth.setVisibility(View.VISIBLE);
            layoutSmdArea.setVisibility(View.GONE);

            boolean hasBand3 = (mode == MODE_5_BAND || mode == MODE_6_BAND);
            tabBand3.setVisibility(hasBand3 ? View.VISIBLE : View.GONE);
            spaceBand3.setVisibility(hasBand3 ? View.VISIBLE : View.GONE);

            boolean hasTcr = (mode == MODE_6_BAND);
            tabBandTcr.setVisibility(hasTcr ? View.VISIBLE : View.GONE);
            spaceBandTcr.setVisibility(hasTcr ? View.VISIBLE : View.GONE);
            tvTcrValue.setVisibility(hasTcr ? View.VISIBLE : View.GONE);

            selectBandTab(0);
            calculateCurrentResistor();
        }
    }

    private void selectBandTab(int tabIndex) {
        this.currentBandTab = tabIndex;

        tabBand1.setBackgroundResource(tabIndex == 0 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        tabBand1.setTextColor(getColor(tabIndex == 0 ? R.color.chip_text_selected : R.color.chip_text_unselected));

        tabBand2.setBackgroundResource(tabIndex == 1 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        tabBand2.setTextColor(getColor(tabIndex == 1 ? R.color.chip_text_selected : R.color.chip_text_unselected));

        tabBand3.setBackgroundResource(tabIndex == 2 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        tabBand3.setTextColor(getColor(tabIndex == 2 ? R.color.chip_text_selected : R.color.chip_text_unselected));

        tabBandMult.setBackgroundResource(tabIndex == 3 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        tabBandMult.setTextColor(getColor(tabIndex == 3 ? R.color.chip_text_selected : R.color.chip_text_unselected));

        tabBandTol.setBackgroundResource(tabIndex == 4 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        tabBandTol.setTextColor(getColor(tabIndex == 4 ? R.color.chip_text_selected : R.color.chip_text_unselected));

        tabBandTcr.setBackgroundResource(tabIndex == 5 ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        tabBandTcr.setTextColor(getColor(tabIndex == 5 ? R.color.chip_text_selected : R.color.chip_text_unselected));

        buildColorPalette(tabIndex);
        updateCanvas();
    }

    private void buildColorPalette(int tabIndex) {
        layoutColorPalette.removeAllViews();

        int[] availableColors;
        if (tabIndex == 0 || tabIndex == 1 || tabIndex == 2) {
            // Significant digits: Black to White (0-9)
            availableColors = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        } else if (tabIndex == 3) {
            // Multiplier: Silver, Gold, Black, Brown, Red, Orange, Yellow, Green, Blue, Violet
            availableColors = new int[]{11, 10, 0, 1, 2, 3, 4, 5, 6, 7};
        } else if (tabIndex == 4) {
            // Tolerance: Brown (1%), Red (2%), Green (0.5%), Blue (0.25%), Violet (0.1%), Gray (0.05%), Gold (5%), Silver (10%)
            availableColors = new int[]{1, 2, 5, 6, 7, 8, 10, 11};
        } else {
            // TCR: Brown (100), Red (50), Orange (15), Yellow (25), Blue (10), Violet (5)
            availableColors = new int[]{1, 2, 3, 4, 6, 7};
        }

        // Layout into rows of 3 buttons
        LinearLayout currentRow = null;
        for (int i = 0; i < availableColors.length; i++) {
            if (i % 3 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                if (i > 0) {
                    ((LinearLayout.LayoutParams) currentRow.getLayoutParams()).topMargin = dpToPx(6);
                }
                layoutColorPalette.addView(currentRow);
            }

            final int colorCode = availableColors[i];
            Button btnColor = new Button(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(48), 1.0f);
            if (i % 3 != 0) {
                lp.leftMargin = dpToPx(6);
            }
            btnColor.setLayoutParams(lp);

            // Shape background with swatch indicator
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(dpToPx(8));
            shape.setColor(getColor(R.color.bg_card_elevated));
            shape.setStroke(dpToPx(1.5f), OhmEngine.COLOR_HEX[colorCode]);
            btnColor.setBackground(shape);

            String label = OhmEngine.COLOR_NAMES[colorCode];
            if (tabIndex == 3) {
                label += " (×" + formatMult(OhmEngine.getMultiplier(colorCode)) + ")";
            } else if (tabIndex == 4) {
                label += " (" + String.format(Locale.US, "±%.2f%%", OhmEngine.getTolerance(colorCode)).replace(".00", "") + ")";
            } else if (tabIndex == 5) {
                label += " (" + OhmEngine.getTcr(colorCode) + "ppm)";
            } else {
                label += " (" + colorCode + ")";
            }

            btnColor.setText(label);
            btnColor.setTextSize(10.5f);
            btnColor.setTextColor(getColor(R.color.text_primary));
            btnColor.setContentDescription("Color " + OhmEngine.COLOR_NAMES[colorCode]);

            btnColor.setOnClickListener(v -> {
                applyColorToCurrentBand(colorCode);
                calculateCurrentResistor();
            });

            if (currentRow != null) {
                currentRow.addView(btnColor);
            }
        }
    }

    private String formatMult(double m) {
        if (m >= 1e6) return (int) (m / 1e6) + "M";
        if (m >= 1e3) return (int) (m / 1e3) + "k";
        if (m == 0.1) return "0.1";
        if (m == 0.01) return "0.01";
        return String.valueOf((int) m);
    }

    private void applyColorToCurrentBand(int colorCode) {
        switch (currentBandTab) {
            case 0: band1 = colorCode; break;
            case 1: band2 = colorCode; break;
            case 2: band3 = colorCode; break;
            case 3: bandMult = colorCode; break;
            case 4: bandTol = colorCode; break;
            case 5: bandTcr = colorCode; break;
        }
    }

    private void calculateCurrentResistor() {
        if (currentMode == MODE_4_BAND) {
            lastResult = OhmEngine.calculate4Band(band1, band2, bandMult, bandTol);
        } else if (currentMode == MODE_5_BAND) {
            lastResult = OhmEngine.calculate5Band(band1, band2, band3, bandMult, bandTol);
        } else {
            lastResult = OhmEngine.calculate6Band(band1, band2, band3, bandMult, bandTol, bandTcr);
        }

        tvNominalOhms.setText(lastResult.formattedNominal + " " + lastResult.formattedTolerance);
        tvToleranceRange.setText("Range: " + lastResult.formattedMin + " — " + lastResult.formattedMax);
        tvEiaSeries.setText(lastResult.eiaSeriesMatch);

        if (currentMode == MODE_6_BAND) {
            tvTcrValue.setVisibility(View.VISIBLE);
            tvTcrValue.setText("Temperature Coefficient: " + lastResult.formattedTcr + " (" + OhmEngine.COLOR_NAMES[bandTcr] + ")");
        } else {
            tvTcrValue.setVisibility(View.GONE);
        }

        updateCanvas();
    }

    private void updateCanvas() {
        int[] activeBands;
        int activeIdx = currentBandTab;

        if (currentMode == MODE_4_BAND) {
            activeBands = new int[]{band1, band2, bandMult, bandTol, 0, 0};
            if (activeIdx == 3) activeIdx = 2; // mult is 3rd on 4-band
            else if (activeIdx == 4) activeIdx = 3; // tol is 4th on 4-band
        } else if (currentMode == MODE_5_BAND) {
            activeBands = new int[]{band1, band2, band3, bandMult, bandTol, 0};
            if (activeIdx == 3) activeIdx = 3;
            else if (activeIdx == 4) activeIdx = 4;
        } else {
            activeBands = new int[]{band1, band2, band3, bandMult, bandTol, bandTcr};
        }

        resistorCanvas.setBands(currentMode, activeBands, activeIdx);
    }

    private void performReverseSynthesis() {
        String input = etDirectOhms.getText().toString();
        double parsed = OhmEngine.parseResistanceText(input);

        if (parsed <= 0) {
            Toast.makeText(this, "Enter a valid resistance (e.g. 4.7k, 220, 10M)", Toast.LENGTH_SHORT).show();
            return;
        }

        int[] bands = OhmEngine.synthesize4Band(parsed, bandTol);
        band1 = bands[0];
        band2 = bands[1];
        bandMult = bands[2];
        bandTol = bands[3];

        updateMode(MODE_4_BAND);
        selectBandTab(0);
        calculateCurrentResistor();
        Toast.makeText(this, "Synthesized: " + lastResult.formattedNominal, Toast.LENGTH_SHORT).show();
    }

    private void performSmdDecode() {
        String code = etSmdCode.getText().toString();
        if (code.isEmpty()) {
            code = "472"; // Default demo code
            etSmdCode.setText(code);
        }

        lastSmdResult = OhmEngine.decodeSmd(code);
        if (lastSmdResult.isValid) {
            tvSmdResultOhms.setText(lastSmdResult.formattedOhms + String.format(Locale.US, " (±%.1f%%)", lastSmdResult.tolerancePercent));
            tvSmdResultType.setText(lastSmdResult.type);
        } else {
            tvSmdResultOhms.setText("Invalid SMD Code");
            tvSmdResultType.setText("Format: 3-Digit (472), 4-Digit (1002), or EIA-96 (01A)");
        }
    }

    private void saveCurrentPart() {
        OhmDatabaseHelper.PartRecord record = new OhmDatabaseHelper.PartRecord();
        record.timestamp = System.currentTimeMillis();

        if (currentMode == MODE_SMD) {
            if (lastSmdResult == null || !lastSmdResult.isValid) {
                Toast.makeText(this, "Decode a valid SMD part first", Toast.LENGTH_SHORT).show();
                return;
            }
            record.type = "SMD " + lastSmdResult.code;
            record.nominalOhms = lastSmdResult.ohms;
            record.formattedValue = lastSmdResult.formattedOhms;
            record.tolerancePercent = lastSmdResult.tolerancePercent;
            record.bandsCsv = "SMD Marking: " + lastSmdResult.code;
            record.eiaSeries = lastSmdResult.type;
        } else {
            if (lastResult == null) calculateCurrentResistor();
            record.type = currentMode + "-Band Axial";
            record.nominalOhms = lastResult.nominalOhms;
            record.formattedValue = lastResult.formattedNominal + " " + lastResult.formattedTolerance;
            record.tolerancePercent = lastResult.tolerancePercent;
            record.eiaSeries = lastResult.eiaSeriesMatch;

            if (currentMode == MODE_4_BAND) {
                record.bandsCsv = OhmEngine.COLOR_NAMES[band1] + ", " + OhmEngine.COLOR_NAMES[band2] + ", "
                        + OhmEngine.COLOR_NAMES[bandMult] + ", " + OhmEngine.COLOR_NAMES[bandTol];
            } else if (currentMode == MODE_5_BAND) {
                record.bandsCsv = OhmEngine.COLOR_NAMES[band1] + ", " + OhmEngine.COLOR_NAMES[band2] + ", "
                        + OhmEngine.COLOR_NAMES[band3] + ", " + OhmEngine.COLOR_NAMES[bandMult] + ", " + OhmEngine.COLOR_NAMES[bandTol];
            } else {
                record.bandsCsv = OhmEngine.COLOR_NAMES[band1] + ", " + OhmEngine.COLOR_NAMES[band2] + ", "
                        + OhmEngine.COLOR_NAMES[band3] + ", " + OhmEngine.COLOR_NAMES[bandMult] + ", "
                        + OhmEngine.COLOR_NAMES[bandTol] + ", " + OhmEngine.COLOR_NAMES[bandTcr];
            }
        }

        dbHelper.insertPart(record);
        Toast.makeText(this, "Saved: " + record.formattedValue, Toast.LENGTH_SHORT).show();
        refreshPartsList();
    }

    private void refreshPartsList() {
        layoutPartsList.removeAllViews();
        List<OhmDatabaseHelper.PartRecord> list = dbHelper.getAllParts();

        if (list.isEmpty()) {
            tvEmptyParts.setVisibility(View.VISIBLE);
        } else {
            tvEmptyParts.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (OhmDatabaseHelper.PartRecord rec : list) {
                View itemView = inflater.inflate(R.layout.item_ohm_part, layoutPartsList, false);

                TextView tvValue = itemView.findViewById(R.id.itemTvValue);
                TextView tvType = itemView.findViewById(R.id.itemTvType);
                TextView tvBands = itemView.findViewById(R.id.itemTvBands);
                TextView tvSeries = itemView.findViewById(R.id.itemTvSeries);
                ImageButton btnDelete = itemView.findViewById(R.id.itemBtnDelete);

                tvValue.setText(rec.formattedValue);
                tvType.setText(rec.type);
                tvBands.setText(rec.bandsCsv);
                tvSeries.setText(rec.eiaSeries);

                btnDelete.setOnClickListener(v -> {
                    dbHelper.deletePart(rec.id);
                    refreshPartsList();
                });

                layoutPartsList.addView(itemView);
            }
        }
    }

    private void clearAllParts() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Workbench Parts")
                .setMessage("Are you sure you want to clear all saved resistor records?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    dbHelper.clearAllParts();
                    refreshPartsList();
                    Toast.makeText(this, "Parts list cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void copySpecificationsToClipboard() {
        String text;
        if (currentMode == MODE_SMD && lastSmdResult != null && lastSmdResult.isValid) {
            text = "OhmBand SMD Component Spec\n"
                    + "-------------------------\n"
                    + "Marking Code: " + lastSmdResult.code + "\n"
                    + "Nominal Resistance: " + lastSmdResult.formattedOhms + "\n"
                    + "Tolerance: ±" + lastSmdResult.tolerancePercent + "%\n"
                    + "Package Type: " + lastSmdResult.type + "\n"
                    + "https://apps.kimrosil.com/ohmband/";
        } else {
            if (lastResult == null) calculateCurrentResistor();
            text = "OhmBand Resistor Spec\n"
                    + "---------------------\n"
                    + "Nominal Resistance: " + lastResult.formattedNominal + "\n"
                    + "Tolerance: " + lastResult.formattedTolerance + " (" + lastResult.formattedMin + " to " + lastResult.formattedMax + ")\n"
                    + "EIA Match: " + lastResult.eiaSeriesMatch + "\n"
                    + "TCR: " + lastResult.formattedTcr + "\n"
                    + "https://apps.kimrosil.com/ohmband/";
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("OhmBand Resistor", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Specifications copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void cycleTheme() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String current = sp.getString(KEY_THEME, "auto");
        String next = "dark";
        if ("dark".equals(current)) next = "light";
        else if ("light".equals(current)) next = "auto";
        else next = "dark";

        sp.edit().putString(KEY_THEME, next).apply();
        recreate();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        Button btnThemeDark = dialogView.findViewById(R.id.btnThemeDark);
        Button btnThemeLight = dialogView.findViewById(R.id.btnThemeLight);
        Button btnThemeAuto = dialogView.findViewById(R.id.btnThemeAuto);
        Button btnDialogClose = dialogView.findViewById(R.id.btnDialogClose);

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        btnThemeDark.setOnClickListener(v -> {
            sp.edit().putString(KEY_THEME, "dark").apply();
            dialog.dismiss();
            recreate();
        });
        btnThemeLight.setOnClickListener(v -> {
            sp.edit().putString(KEY_THEME, "light").apply();
            dialog.dismiss();
            recreate();
        });
        btnThemeAuto.setOnClickListener(v -> {
            sp.edit().putString(KEY_THEME, "auto").apply();
            dialog.dismiss();
            recreate();
        });

        btnDialogClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
