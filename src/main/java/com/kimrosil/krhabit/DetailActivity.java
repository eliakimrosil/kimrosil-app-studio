package com.kimrosil.krhabit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
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
        if (item.reminderEnabled) {
            tvCategory.setText(freqText + " • 🔔 " + ReminderManager.formatReminderTime(item.reminderHour, item.reminderMinute));
        } else {
            tvCategory.setText(freqText + " • No reminder");
        }

        tvStrength.setText(HabitMath.formatStrength(item.strengthScore));
        int habitColor = HabitColors.parseColor(item.colorHex);
        heatmapView.setAccentColor(habitColor);
        tvStrength.setTextColor(habitColor);
        tvCurrentStreak.setText(item.currentStreak + "d");
        tvBestStreak.setText(item.bestStreak + "d");

        if (item.description != null && !item.description.trim().isEmpty()) {
            tvNotes.setText(item.description);
        } else {
            tvNotes.setText("No ritual notes specified.");
        }

        // Setup Reminder Card
        final DatabaseHelper.HabitItem currentItem = item;
        final Switch switchReminder = findViewById(R.id.switchDetailReminder);
        final TextView tvReminderStatus = findViewById(R.id.tvDetailReminderStatus);
        final Button btnPickTime = findViewById(R.id.btnDetailPickTime);

        if (switchReminder != null) {
            switchReminder.setOnCheckedChangeListener(null);
            switchReminder.setChecked(item.reminderEnabled);
            btnPickTime.setVisibility(item.reminderEnabled ? View.VISIBLE : View.GONE);
            btnPickTime.setText("🔔 " + ReminderManager.formatReminderTime(item.reminderHour, item.reminderMinute));
            tvReminderStatus.setText(item.reminderEnabled ? "Active at " + ReminderManager.formatReminderTime(item.reminderHour, item.reminderMinute) : "Reminder Off");

            switchReminder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                        }
                    }
                    currentItem.reminderEnabled = isChecked;
                    dbHelper.updateReminder(habitUuid, isChecked, currentItem.reminderHour, currentItem.reminderMinute);
                    if (isChecked) {
                        ReminderManager.scheduleReminder(DetailActivity.this, currentItem);
                    } else {
                        ReminderManager.cancelReminder(DetailActivity.this, habitUuid);
                    }
                    loadHabitData();
                    FirebaseSyncEngine syncEngine = new FirebaseSyncEngine(DetailActivity.this, dbHelper);
                    if (syncEngine.isSignedIn()) syncEngine.syncWithFirestore(null);
                }
            });

            btnPickTime.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new TimePickerDialog(DetailActivity.this, new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                            currentItem.reminderHour = hourOfDay;
                            currentItem.reminderMinute = minute;
                            currentItem.reminderEnabled = true;
                            dbHelper.updateReminder(habitUuid, true, hourOfDay, minute);
                            ReminderManager.scheduleReminder(DetailActivity.this, currentItem);
                            loadHabitData();
                            FirebaseSyncEngine syncEngine = new FirebaseSyncEngine(DetailActivity.this, dbHelper);
                            if (syncEngine.isSignedIn()) syncEngine.syncWithFirestore(null);
                        }
                    }, currentItem.reminderHour, currentItem.reminderMinute, false).show();
                }
            });
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
        final Switch switchReminder = dialogView.findViewById(R.id.switchEditReminder);
        final TextView tvReminderLabel = dialogView.findViewById(R.id.tvEditReminderLabel);
        final Button btnPickTime = dialogView.findViewById(R.id.btnEditPickReminderTime);
        final LinearLayout layoutColorPicker = dialogView.findViewById(R.id.layoutEditColorPicker);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelEdit);
        Button btnSave = dialogView.findViewById(R.id.btnSaveEditHabit);

        final String[] selectedColor = new String[]{item.colorHex};
        if (layoutColorPicker != null) {
            HabitColors.populateColorPicker(DetailActivity.this, layoutColorPicker, selectedColor[0], new HabitColors.OnColorSelectedListener() {
                @Override
                public void onColorSelected(String colorHex) {
                    selectedColor[0] = colorHex;
                }
            });
        }

        etTitle.setText(item.title);
        etTitle.setSelection(item.title.length());
        if (item.description != null) {
            etDesc.setText(item.description);
        }

        final int[] reminderTime = new int[]{item.reminderHour, item.reminderMinute};
        switchReminder.setChecked(item.reminderEnabled);
        btnPickTime.setVisibility(item.reminderEnabled ? View.VISIBLE : View.GONE);
        btnPickTime.setText("🔔 " + ReminderManager.formatReminderTime(reminderTime[0], reminderTime[1]));
        tvReminderLabel.setText(item.reminderEnabled ? "Daily at" : "Reminder Off");

        switchReminder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                btnPickTime.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                tvReminderLabel.setText(isChecked ? "Daily at" : "Reminder Off");
                if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                    }
                }
            }
        });

        btnPickTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(DetailActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        reminderTime[0] = hourOfDay;
                        reminderTime[1] = minute;
                        btnPickTime.setText("🔔 " + ReminderManager.formatReminderTime(hourOfDay, minute));
                    }
                }, reminderTime[0], reminderTime[1], false).show();
            }
        });

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
                boolean reminderOn = switchReminder.isChecked();

                if (newTitle.isEmpty()) {
                    etTitle.setError("Please enter a habit title");
                    return;
                }

                dbHelper.updateHabit(habitUuid, newTitle, newDesc, item.targetDaysPerWeek, reminderOn, reminderTime[0], reminderTime[1], selectedColor[0]);
                item.colorHex = selectedColor[0];
                if (reminderOn) {
                    item.reminderEnabled = true;
                    item.reminderHour = reminderTime[0];
                    item.reminderMinute = reminderTime[1];
                    ReminderManager.scheduleReminder(DetailActivity.this, item);
                } else {
                    item.reminderEnabled = false;
                    ReminderManager.cancelReminder(DetailActivity.this, habitUuid);
                }

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
