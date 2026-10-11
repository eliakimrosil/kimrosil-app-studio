package com.kimrosil.grainline;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class DetailActivity extends Activity {

    public static final String EXTRA_SESSION_ID = "EXTRA_SESSION_ID";

    private TextView tvDetailTitle;
    private Button btnDetailBack;
    private EditText etDetailName;
    private EditText etDetailFabricType;
    private EditText etDetailFabricWidth;
    private EditText etDetailPieceWidth;
    private EditText etDetailPieceLength;
    private EditText etDetailPieceCount;
    private EditText etDetailSeam;
    private EditText etDetailShrinkage;
    private Switch swDetailRotation;
    private EditText etDetailNotes;

    private TextView tvDetailYards;
    private TextView tvDetailMeters;
    private TextView tvDetailWaste;
    private TextView tvDetailTimestamp;

    private Button btnDetailRecalculate;
    private Button btnDetailSave;
    private Button btnDetailDelete;
    private Button btnDetailExport;

    private DatabaseHelper mDbHelper;
    private DatabaseHelper.SessionRecord mCurrentRecord;
    private long mSessionId = -1;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("grainline_prefs", MODE_PRIVATE);
        int mode = prefs.getInt("pref_theme_mode", 0);
        int nightMode;
        if (mode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int sysNight = 0;
            try {
                sysNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {
            }
            nightMode = (sysNight == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        mDbHelper = DatabaseHelper.getInstance(this);
        mSessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);

        initViews();
        setupLightStatusBar();
        loadSessionData();
        setupListeners();
    }

    private void initViews() {
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        btnDetailBack = findViewById(R.id.btnDetailBack);
        etDetailName = findViewById(R.id.etDetailName);
        etDetailFabricType = findViewById(R.id.etDetailFabricType);
        etDetailFabricWidth = findViewById(R.id.etDetailFabricWidth);
        etDetailPieceWidth = findViewById(R.id.etDetailPieceWidth);
        etDetailPieceLength = findViewById(R.id.etDetailPieceLength);
        etDetailPieceCount = findViewById(R.id.etDetailPieceCount);
        etDetailSeam = findViewById(R.id.etDetailSeam);
        etDetailShrinkage = findViewById(R.id.etDetailShrinkage);
        swDetailRotation = findViewById(R.id.swDetailRotation);
        etDetailNotes = findViewById(R.id.etDetailNotes);

        tvDetailYards = findViewById(R.id.tvDetailYards);
        tvDetailMeters = findViewById(R.id.tvDetailMeters);
        tvDetailWaste = findViewById(R.id.tvDetailWaste);
        tvDetailTimestamp = findViewById(R.id.tvDetailTimestamp);

        btnDetailRecalculate = findViewById(R.id.btnDetailRecalculate);
        btnDetailSave = findViewById(R.id.btnDetailSave);
        btnDetailDelete = findViewById(R.id.btnDetailDelete);
        btnDetailExport = findViewById(R.id.btnDetailExport);
    }

    private void setupLightStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
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

    private void loadSessionData() {
        if (mSessionId == -1) {
            finish();
            return;
        }

        mCurrentRecord = mDbHelper.getSessionById(mSessionId);
        if (mCurrentRecord == null) {
            Toast.makeText(this, "Session not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvDetailTitle.setText(mCurrentRecord.projectName);
        etDetailName.setText(mCurrentRecord.projectName);
        etDetailFabricType.setText(mCurrentRecord.fabricType);
        etDetailFabricWidth.setText(String.format(Locale.US, "%.1f", mCurrentRecord.fabricWidth));
        etDetailPieceWidth.setText(String.format(Locale.US, "%.1f", mCurrentRecord.pieceWidth));
        etDetailPieceLength.setText(String.format(Locale.US, "%.1f", mCurrentRecord.pieceLength));
        etDetailPieceCount.setText(String.valueOf(mCurrentRecord.pieceCount));
        etDetailSeam.setText(String.format(Locale.US, "%.2f", mCurrentRecord.seamAllowance));
        etDetailShrinkage.setText(String.format(Locale.US, "%.1f", mCurrentRecord.shrinkagePct));
        swDetailRotation.setChecked(mCurrentRecord.allowRotation);
        etDetailNotes.setText(mCurrentRecord.notes);

        updateMetricsReadout(mCurrentRecord.totalYards, mCurrentRecord.totalMeters, mCurrentRecord.offcutPct);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        tvDetailTimestamp.setText("Created: " + sdf.format(new java.util.Date(mCurrentRecord.timestamp)));
    }

    private void updateMetricsReadout(double yards, double meters, double offcut) {
        tvDetailYards.setText(String.format(Locale.US, "%.2f yds", yards));
        tvDetailMeters.setText(String.format(Locale.US, "%.2f m", meters));
        tvDetailWaste.setText(String.format(Locale.US, "%.1f%% waste", offcut));
    }

    private void setupListeners() {
        btnDetailBack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });

        btnDetailRecalculate.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            recalculateParameters();
        });

        btnDetailSave.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            saveUpdatedSession();
        });

        btnDetailDelete.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            mDbHelper.deleteSession(mSessionId);
            Toast.makeText(this, "Session removed", Toast.LENGTH_SHORT).show();
            finish();
        });

        btnDetailExport.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            exportSessionData();
        });
    }

    private void recalculateParameters() {
        double fabricWidth;
        double pieceW;
        double pieceL;
        int count;
        double seam;
        double shrinkage;

        try {
            fabricWidth = Double.parseDouble(etDetailFabricWidth.getText().toString().trim());
            pieceW = Double.parseDouble(etDetailPieceWidth.getText().toString().trim());
            pieceL = Double.parseDouble(etDetailPieceLength.getText().toString().trim());
            count = Integer.parseInt(etDetailPieceCount.getText().toString().trim());
            seam = Double.parseDouble(etDetailSeam.getText().toString().trim());
            shrinkage = Double.parseDouble(etDetailShrinkage.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid numeric parameters", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean allowRotation = swDetailRotation.isChecked();
        double cutPieceW = pieceW + (2.0 * seam);
        double cutPieceL = pieceL + (2.0 * seam);

        int piecesAcrossA = (int) Math.floor(fabricWidth / cutPieceW);
        double reqLengthA = (piecesAcrossA > 0) ? Math.ceil((double) count / piecesAcrossA) * cutPieceL : Double.MAX_VALUE;

        double reqLengthB = Double.MAX_VALUE;
        int piecesAcrossB = 0;
        if (allowRotation) {
            piecesAcrossB = (int) Math.floor(fabricWidth / cutPieceL);
            if (piecesAcrossB > 0) {
                reqLengthB = Math.ceil((double) count / piecesAcrossB) * cutPieceW;
            }
        }

        double optimalLen = Math.min(reqLengthA, reqLengthB);
        if (optimalLen >= Double.MAX_VALUE) {
            Toast.makeText(this, "Piece exceeds bolt width!", Toast.LENGTH_SHORT).show();
            return;
        }

        double shrinkFactor = 1.0 - (shrinkage / 100.0);
        if (shrinkFactor <= 0.01) shrinkFactor = 0.97;
        double totalLinearInches = optimalLen / shrinkFactor;

        double yards = totalLinearInches / 36.0;
        double meters = totalLinearInches * 0.0254;

        double totalArea = fabricWidth * optimalLen;
        double pieceArea = (pieceW * pieceL) * count;
        double eff = (totalArea > 0) ? (pieceArea / totalArea) * 100.0 : 0.0;
        double offcut = Math.max(0.0, 100.0 - eff);

        mCurrentRecord.fabricWidth = fabricWidth;
        mCurrentRecord.pieceWidth = pieceW;
        mCurrentRecord.pieceLength = pieceL;
        mCurrentRecord.pieceCount = count;
        mCurrentRecord.seamAllowance = seam;
        mCurrentRecord.shrinkagePct = shrinkage;
        mCurrentRecord.allowRotation = allowRotation;
        mCurrentRecord.totalYards = yards;
        mCurrentRecord.totalMeters = meters;
        mCurrentRecord.offcutPct = offcut;

        updateMetricsReadout(yards, meters, offcut);
        Toast.makeText(this, "Updated metrics calculated", Toast.LENGTH_SHORT).show();
    }

    private void saveUpdatedSession() {
        if (mCurrentRecord == null) return;

        recalculateParameters();

        String name = etDetailName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) name = "Project";
        mCurrentRecord.projectName = name;
        mCurrentRecord.fabricType = etDetailFabricType.getText().toString().trim();
        mCurrentRecord.notes = etDetailNotes.getText().toString().trim();

        boolean updated = mDbHelper.updateSession(mCurrentRecord);
        if (updated) {
            Toast.makeText(this, "Changes saved successfully", Toast.LENGTH_SHORT).show();
            tvDetailTitle.setText(name);
        } else {
            Toast.makeText(this, "Failed to update record", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportSessionData() {
        if (mCurrentRecord == null) return;

        String csvData = String.format(Locale.US,
                "Project,FabricType,BoltWidthIn,PieceWidthIn,PieceLengthIn,Count,SeamIn,ShrinkagePct,TotalYards,TotalMeters,WastePct\n"
                        + "\"%s\",\"%s\",%.1f,%.1f,%.1f,%d,%.2f,%.1f,%.2f,%.2f,%.1f\n",
                mCurrentRecord.projectName,
                mCurrentRecord.fabricType,
                mCurrentRecord.fabricWidth,
                mCurrentRecord.pieceWidth,
                mCurrentRecord.pieceLength,
                mCurrentRecord.pieceCount,
                mCurrentRecord.seamAllowance,
                mCurrentRecord.shrinkagePct,
                mCurrentRecord.totalYards,
                mCurrentRecord.totalMeters,
                mCurrentRecord.offcutPct);

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("GrainLine CSV", csvData));
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, mCurrentRecord.projectName + " - Fabric Cut Data");
        shareIntent.putExtra(Intent.EXTRA_TEXT, csvData);
        startActivity(Intent.createChooser(shareIntent, "Export Cut CSV"));
    }
}