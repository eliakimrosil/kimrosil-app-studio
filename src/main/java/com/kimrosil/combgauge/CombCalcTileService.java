package com.kimrosil.combgauge;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

public class CombCalcTileService extends TileService {

    private static final String PREFS_NAME = "combgauge_prefs";

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("CombGauge");
            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            double lastWft = sp.getFloat("last_calc_wft", 160.0f);
            int unit = sp.getInt("last_calc_unit", CoatingCalculatorEngine.UNIT_MICRONS);
            tile.setSubtitle(String.format(Locale.US, "%.0f %s", lastWft, CoatingCalculatorEngine.getUnitLabel(unit)));
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        showQuickCalcDialog();
    }

    private void showQuickCalcDialog() {
        final SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final int unit = sp.getInt("last_calc_unit", CoatingCalculatorEngine.UNIT_MICRONS);
        final double[] currentThickness = new double[]{sp.getFloat("last_calc_wft", 160.0f)};
        final double solids = sp.getFloat("last_calc_solids", 75.0f);
        final double thinner = sp.getFloat("last_calc_thinner", 0.0f);
        final int profile = sp.getInt("last_calc_profile", CoatingCalculatorEngine.PROFILE_FINE_25);

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_quick_tile, null);

        final TextView tvTileValue = dialogView.findViewById(R.id.tvTileValue);
        final TextView tvTileDftResult = dialogView.findViewById(R.id.tvTileDftResult);
        final TextView tvTileUnit = dialogView.findViewById(R.id.tvTileUnit);
        Button btnTileMinus = dialogView.findViewById(R.id.btnTileMinus);
        Button btnTilePlus = dialogView.findViewById(R.id.btnTilePlus);

        tvTileUnit.setText(CoatingCalculatorEngine.getUnitLabel(unit));

        final Runnable updateUi = new Runnable() {
            @Override
            public void run() {
                tvTileValue.setText(CoatingCalculatorEngine.formatThickness(currentThickness[0], unit));
                double resultingDft = CoatingCalculatorEngine.calculateResultingDft(
                        currentThickness[0], solids, thinner, profile, unit);
                tvTileDftResult.setText("Expected DFT: " + CoatingCalculatorEngine.formatThickness(resultingDft, unit)
                        + " " + CoatingCalculatorEngine.getUnitLabel(unit));
            }
        };

        btnTileMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double step = (unit == CoatingCalculatorEngine.UNIT_MICRONS) ? 10.0 : 0.5;
                currentThickness[0] = Math.max(step, currentThickness[0] - step);
                updateUi.run();
            }
        });

        btnTilePlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double step = (unit == CoatingCalculatorEngine.UNIT_MICRONS) ? 10.0 : 0.5;
                currentThickness[0] += step;
                updateUi.run();
            }
        });

        updateUi.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("CombGauge Quick Inspector")
                .setView(dialogView)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        sp.edit().putFloat("last_calc_wft", (float) currentThickness[0]).apply();
                        Tile tile = getQsTile();
                        if (tile != null) {
                            tile.setSubtitle(String.format(Locale.US, "%.0f %s", currentThickness[0],
                                    CoatingCalculatorEngine.getUnitLabel(unit)));
                            tile.updateTile();
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        showDialog(dialog);
    }
}