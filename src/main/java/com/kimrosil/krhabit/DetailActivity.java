package com.kimrosil.krhabit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class DetailActivity extends Activity {

    public static final String EXTRA_HABIT_UUID = "extra_habit_uuid";

    private DatabaseHelper dbHelper;
    private String habitUuid;
    private int themeMode = 0; // 0 = Auto, 1 = Light, 2 = Dark

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = newBase.getSharedPreferences("kr_habit_theme", Context.MODE_PRIVATE);
        themeMode = sp.getInt("theme_mode", 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        }
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (themeMode == 1) {
            setTheme(R.style.AppTheme_Light);
        } else if (themeMode == 2) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        dbHelper = new DatabaseHelper(this);
        habitUuid = getIntent().getStringExtra(EXTRA_HABIT_UUID);

        if (habitUuid == null) {
            Toast.makeText(this, "Habit not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadHabitData();
    }

    private void initViews() {
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                finish();
            }
        });

        Button btnEdit = findViewById(R.id.btnEditHabit);
        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                showEditHabitDialog();
            }
        });

        Button btnDelete = findViewById(R.id.btnDeleteHabit);
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                confirmDelete();
            }
        });
    }

    private void loadHabitData() {
        DatabaseHelper.HabitItem item = dbHelper.getHabitByUuid(habitUuid);
        if (item == null) {
            finish();
            return;
        }

        TextView tvTitle = findViewById(R.id.tvDetailTitle);
        TextView tvCategory = findViewById(R.id.tvDetailCategory);
        TextView tvStrength = findViewById(R.id.tvDetailStrength);
        TextView tvCurrentStreak = findViewById(R.id.tvDetailCurrentStreak);
        TextView tvBestStreak = findViewById(R.id.tvDetailBestStreak);
        TextView tvNotes = findViewById(R.id.tvDetailNotes);
        HeatmapGridView heatmapView = findViewById(R.id.heatmapView);

        tvTitle.setText(item.title);
        String freqText = item.targetDaysPerWeek == 7 ? "Daily" : item.targetDaysPerWeek + "x / week";
        tvCategory.setText(item.category + " • " + freqText);

        tvStrength.setText(HabitMath.formatStrength(item.strengthScore));
        tvCurrentStreak.setText(item.currentStreak + "d");
        tvBestStreak.setText(item.bestStreak + "d");

        if (item.description != null && !item.description.trim().isEmpty()) {
            tvNotes.setText(item.description);
        } else {
            tvNotes.setText("No ritual notes specified.");
        }

        // Load 60-day chronological history into heatmap
        List<Boolean> history = dbHelper.getChronologicalHistory(habitUuid, 63);
        heatmapView.setHistory(history);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Habit")
                .setMessage("Are you sure you want to delete this habit and all its history? This action cannot be undone.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dbHelper.deleteHabit(habitUuid);
                        Toast.makeText(DetailActivity.this, "Habit deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditHabitDialog() {
        final DatabaseHelper.HabitItem item = dbHelper.getHabitByUuid(habitUuid);
        if (item == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_habit, null);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();

        final EditText etTitle = dialogView.findViewById(R.id.etEditHabitTitle);
        final EditText etDesc = dialogView.findViewById(R.id.etEditHabitDesc);
        final Spinner spinnerCategory = dialogView.findViewById(R.id.spinnerEditCategory);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelEdit);
        Button btnSave = dialogView.findViewById(R.id.btnSaveEditHabit);

        String[] categories = {"Health", "Mind", "Body", "Craft", "Focus"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
        spinnerCategory.setAdapter(adapter);

        etTitle.setText(item.title);
        etTitle.setSelection(item.title.length());
        if (item.description != null) {
            etDesc.setText(item.description);
        }

        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equalsIgnoreCase(item.category)) {
                spinnerCategory.setSelection(i);
                break;
            }
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                String newTitle = etTitle.getText().toString().trim();
                String newDesc = etDesc.getText().toString().trim();
                String newCat = (String) spinnerCategory.getSelectedItem();

                if (newTitle.isEmpty()) {
                    etTitle.setError("Please enter a habit title");
                    return;
                }

                dbHelper.updateHabit(habitUuid, newTitle, newDesc, newCat, item.targetDaysPerWeek);
                dialog.dismiss();
                loadHabitData();
                Toast.makeText(DetailActivity.this, "Habit updated!", Toast.LENGTH_SHORT).show();

                // Trigger cloud auto-sync in background if signed in
                FirebaseSyncEngine syncEngine = new FirebaseSyncEngine(DetailActivity.this, dbHelper);
                if (syncEngine.isSignedIn()) {
                    syncEngine.syncWithFirestore(null);
                }
            }
        });

        dialog.show();
    }
}
