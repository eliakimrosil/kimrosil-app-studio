package com.kimrosil.skimskim;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryDetailActivity extends Activity {

    private static final String PREFS_NAME = "skim_skim_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode";

    private DatabaseHelper dbHelper;
    private LinearLayout llLogContainer;
    private Button btnBack;
    private Button btnExportCsv;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = sp.getInt(PREF_THEME_MODE, 0);

        int nightMode;
        if (themeMode == 1) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            int sysMode = 0;
            try {
                sysMode = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {
            }
            nightMode = (sysMode == 2) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_detail);

        dbHelper = new DatabaseHelper(this);
        llLogContainer = findViewById(R.id.llLogContainer);
        btnBack = findViewById(R.id.btnHistoryBack);
        btnExportCsv = findViewById(R.id.btnExportCsv);

        btnBack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });

        btnExportCsv.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            exportLogsAsCsv();
        });

        populateLogs();
    }

    private void populateLogs() {
        llLogContainer.removeAllViews();
        List<DatabaseHelper.CycleRecord> records = dbHelper.getAllLogs();

        if (records == null || records.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("No filter history records saved yet.");
            tvEmpty.setTextColor(getColor(R.color.m3_on_surface_variant));
            tvEmpty.setTextSize(16);
            tvEmpty.setPadding(32, 64, 32, 64);
            llLogContainer.addView(tvEmpty);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

        for (final DatabaseHelper.CycleRecord rec : records) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(getColor(R.color.m3_surface_container));
            card.setPadding(32, 28, 32, 28);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 24);
            card.setLayoutParams(lp);

            TextView tvHeader = new TextView(this);
            tvHeader.setText(String.format(Locale.US, "%s • %s", rec.filterProfile, sdf.format(new Date(rec.timestamp))));
            tvHeader.setTextSize(16);
            tvHeader.setTextColor(getColor(R.color.m3_primary));
            card.addView(tvHeader);

            TextView tvTelemetry = new TextView(this);
            double deltaPsi = rec.dirtyPsi - rec.cleanPsi;
            tvTelemetry.setText(String.format(Locale.US,
                    "Backwash: %ds | Rinse: %ds\nDE Powder: %.1f lbs | Filter: %.1f sq ft\nClean PSI: %.1f | Dirty PSI: %.1f (Δ%.1f PSI)",
                    rec.backwashSeconds, rec.rinseSeconds, rec.deDosageLbs, rec.filterAreaSqFt, rec.cleanPsi, rec.dirtyPsi, deltaPsi));
            tvTelemetry.setTextColor(getColor(R.color.m3_on_surface));
            tvTelemetry.setTextSize(14);
            tvTelemetry.setPadding(0, 12, 0, 12);
            card.addView(tvTelemetry);

            final EditText etNotes = new EditText(this);
            etNotes.setHint("Notes / Chemical additions");
            etNotes.setText(rec.notes);
            etNotes.setTextColor(getColor(R.color.m3_on_surface));
            etNotes.setHintTextColor(getColor(R.color.m3_on_surface_variant));
            etNotes.setBackgroundColor(getColor(R.color.m3_surface_container_high));
            etNotes.setPadding(20, 14, 20, 14);
            card.addView(etNotes);

            LinearLayout actionRow = new LinearLayout(this);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setPadding(0, 16, 0, 0);

            Button btnSaveNote = new Button(this);
            btnSaveNote.setText("Update Note");
            btnSaveNote.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                String updatedNotes = etNotes.getText().toString();
                dbHelper.updateNotes(rec.id, updatedNotes);
                Toast.makeText(HistoryDetailActivity.this, "Note updated", Toast.LENGTH_SHORT).show();
            });
            actionRow.addView(btnSaveNote);

            Button btnDelete = new Button(this);
            btnDelete.setText("Delete");
            btnDelete.setTextColor(getColor(R.color.color_danger_red));
            btnDelete.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                dbHelper.deleteLog(rec.id);
                populateLogs();
            });
            actionRow.addView(btnDelete);

            card.addView(actionRow);
            llLogContainer.addView(card);
        }
    }

    private void exportLogsAsCsv() {
        List<DatabaseHelper.CycleRecord> records = dbHelper.getAllLogs();
        if (records == null || records.isEmpty()) {
            Toast.makeText(this, "No records to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Timestamp,Profile,Backwash_Sec,Rinse_Sec,Area_SqFt,DE_Lbs,Clean_PSI,Dirty_PSI,Notes\n");
        for (DatabaseHelper.CycleRecord r : records) {
            csv.append(r.id).append(",")
                    .append(r.timestamp).append(",")
                    .append("\"").append(r.filterProfile).append("\",")
                    .append(r.backwashSeconds).append(",")
                    .append(r.rinseSeconds).append(",")
                    .append(r.filterAreaSqFt).append(",")
                    .append(r.deDosageLbs).append(",")
                    .append(r.cleanPsi).append(",")
                    .append(r.dirtyPsi).append(",")
                    .append("\"").append(r.notes.replace("\"", "\"\"")).append("\"\n");
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/csv");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "SkimSkim Pool Filter Backwash Log");
        sendIntent.putExtra(Intent.EXTRA_TEXT, csv.toString());
        startActivity(Intent.createChooser(sendIntent, "Export Service Log CSV"));
    }
}