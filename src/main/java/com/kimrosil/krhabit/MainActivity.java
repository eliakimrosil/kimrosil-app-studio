package com.kimrosil.krhabit;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private DatabaseHelper dbHelper;
    private FirebaseSyncEngine syncEngine;
    private int themeMode = 0; // 0 = Auto, 1 = Light, 2 = Dark

    private TextView tvSyncIndicator;
    private RingProgressView ringProgressView;
    private TextView tvDailyProgressValue;
    private TextView tvTotalHabitsValue;
    private TextView tvTopStreakValue;
    private ReorderableLinearLayout habitListContainer;
    private LinearLayout layoutEmptyState;
    private PullToRefreshLayout pullToRefreshLayout;
    private View btnAccount;
    private TextView tvTopBarAvatarText;
    private ImageView ivTopBarAvatar;

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
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        syncEngine = new FirebaseSyncEngine(this, dbHelper);

        initViews();
        ReminderManager.createNotificationChannel(this);
        ReminderManager.rescheduleAllReminders(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        refreshDashboard();
        triggerAutoSync();

        SharedPreferences runPrefs = getSharedPreferences("kr_habit_meta", Context.MODE_PRIVATE);
        boolean firstRunDone = runPrefs.getBoolean("first_run_done", false);
        if (!firstRunDone && !syncEngine.isSignedIn()) {
            runPrefs.edit().putBoolean("first_run_done", true).apply();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) {
                        promptAccountSelection();
                    }
                }
            }, 400);
        }
    }

    private static final int REQ_CODE_ACCOUNT_PICKER = 1001;

    private void promptAccountSelection() {
        try {
            Intent intent = AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    new String[]{"com.google"},
                    null,
                    null,
                    null,
                    null
            );
            startActivityForResult(intent, REQ_CODE_ACCOUNT_PICKER);
        } catch (Exception e) {
            showCloudSyncDialog();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_ACCOUNT_PICKER && resultCode == RESULT_OK && data != null) {
            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            if (accountName != null && !accountName.trim().isEmpty()) {
                syncEngine.signInWithEmail(accountName.trim());
                Toast.makeText(this, "Connected: " + accountName.trim(), Toast.LENGTH_SHORT).show();
                triggerAutoSync();
                refreshDashboard();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
        triggerAutoSync();
    }

    private void triggerAutoSync() {
        triggerAutoSync(null);
    }

    private void triggerAutoSync(final Runnable onComplete) {
        if (!syncEngine.isSignedIn()) {
            tvSyncIndicator.setText("● Local Storage");
            tvSyncIndicator.setTextColor(getColor(R.color.text_tertiary));
            refreshDashboard();
            if (onComplete != null) {
                new Handler(Looper.getMainLooper()).postDelayed(onComplete, 350);
            }
            return;
        }

        tvSyncIndicator.setText("● Syncing…");
        tvSyncIndicator.setTextColor(getColor(R.color.sync_pending));

        syncEngine.syncWithFirestore(new FirebaseSyncEngine.SyncCallback() {
            @Override
            public void onSyncStarted() {
                tvSyncIndicator.setText("● Syncing…");
                tvSyncIndicator.setTextColor(getColor(R.color.sync_pending));
            }

            @Override
            public void onSyncSuccess(int habitsSynced, int logsSynced, String message) {
                tvSyncIndicator.setText("● Synced with Google");
                tvSyncIndicator.setTextColor(getColor(R.color.sync_online));
                refreshDashboard();
                if (onComplete != null) {
                    onComplete.run();
                }
            }

            @Override
            public void onSyncError(String error) {
                tvSyncIndicator.setText("● Synced (Offline)");
                tvSyncIndicator.setTextColor(getColor(R.color.sync_offline));
                refreshDashboard();
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private void initViews() {
        tvSyncIndicator = findViewById(R.id.tvSyncIndicator);
        ringProgressView = findViewById(R.id.ringProgressView);
        tvDailyProgressValue = findViewById(R.id.tvDailyProgressValue);
        tvTotalHabitsValue = findViewById(R.id.tvTotalHabitsValue);
        tvTopStreakValue = findViewById(R.id.tvTopStreakValue);
        habitListContainer = findViewById(R.id.habitListContainer);
        if (habitListContainer != null) {
            habitListContainer.setOnOrderChangedListener(new ReorderableLinearLayout.OnOrderChangedListener() {
                @Override
                public void onOrderChanged(List<String> reorderedUuids) {
                    long now = System.currentTimeMillis();
                    dbHelper.updateHabitPositions(reorderedUuids);
                    syncEngine.markOrderNeedsSync(now);
                    triggerAutoSync();
                }
            });
        }
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        // Pull to refresh / resync
        pullToRefreshLayout = findViewById(R.id.pullToRefreshLayout);
        if (pullToRefreshLayout != null) {
            pullToRefreshLayout.setOnRefreshListener(new PullToRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    triggerAutoSync(new Runnable() {
                        @Override
                        public void run() {
                            if (pullToRefreshLayout != null) {
                                pullToRefreshLayout.setRefreshing(false);
                            }
                        }
                    });
                }
            });
        }

        // Header click -> Settings
        View layoutBrandHeader = findViewById(R.id.layoutBrandHeader);
        if (layoutBrandHeader != null) {
            layoutBrandHeader.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    showSettingsDialog();
                }
            });
        }

        // Unified Avatar Button -> Settings & Account
        btnAccount = findViewById(R.id.btnAccount);
        tvTopBarAvatarText = findViewById(R.id.tvTopBarAvatarText);
        ivTopBarAvatar = findViewById(R.id.ivTopBarAvatar);
        if (btnAccount != null) {
            btnAccount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    showSettingsDialog();
                }
            });
        }

        View btnAddNewHabit = findViewById(R.id.btnAddNewHabit);
        if (btnAddNewHabit != null) {
            btnAddNewHabit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    showAddHabitDialog();
                }
            });
        }
    }

    private void refreshDashboard() {
        // Update Sync Indicator & Mini Avatar
        if (syncEngine.isSignedIn()) {
            tvSyncIndicator.setText("● Synced with Google");
            tvSyncIndicator.setTextColor(getColor(R.color.sync_online));

            // Load Google Profile Picture into Mini Top Bar Avatar
            final String userEmail = syncEngine.getUserEmail();
            ProfileImageHelper.loadProfileImage(this, userEmail, ivTopBarAvatar, new ProfileImageHelper.ImageCallback() {
                @Override
                public void onLoaded(Bitmap bitmap) {
                    if (ivTopBarAvatar != null) {
                        ivTopBarAvatar.setVisibility(View.VISIBLE);
                    }
                    if (tvTopBarAvatarText != null) {
                        tvTopBarAvatarText.setVisibility(View.GONE);
                    }
                }
            });
        } else {
            tvSyncIndicator.setText("● Local Storage");
            tvSyncIndicator.setTextColor(getColor(R.color.text_tertiary));
            if (ivTopBarAvatar != null) {
                ivTopBarAvatar.setVisibility(View.GONE);
            }
            if (tvTopBarAvatarText != null) {
                tvTopBarAvatarText.setVisibility(View.VISIBLE);
                tvTopBarAvatarText.setText("👤");
            }
        }

        List<DatabaseHelper.HabitItem> habits = dbHelper.getAllHabits(null);

        // Calculate aggregate telemetry
        int total = habits.size();
        int completedToday = 0;
        int topStreak = 0;

        for (DatabaseHelper.HabitItem h : habits) {
            if (h.isCompletedToday) completedToday++;
            if (h.bestStreak > topStreak) topStreak = h.bestStreak;
        }

        int progressPct = total > 0 ? (int) Math.round((completedToday * 100.0) / total) : 0;
        tvDailyProgressValue.setText(progressPct + "%");
        if (ringProgressView != null) {
            ringProgressView.setProgress(progressPct);
        }
        tvTotalHabitsValue.setText(String.valueOf(total));
        tvTopStreakValue.setText(topStreak + "d");

        // Populate Habits UI
        habitListContainer.removeAllViews();
        if (habits.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);
            String todayStr = DatabaseHelper.getTodayDateString();

            for (final DatabaseHelper.HabitItem habit : habits) {
                View itemView = inflater.inflate(R.layout.item_habit, habitListContainer, false);
                itemView.setTag(habit.uuid);

                TextView tvTitle = itemView.findViewById(R.id.tvHabitTitle);
                TextView tvDesc = itemView.findViewById(R.id.tvHabitDesc);
                TextView tvReminderBadge = itemView.findViewById(R.id.tvReminderBadge);
                TextView tvFreq = itemView.findViewById(R.id.tvFrequencyTarget);
                TextView tvStreak = itemView.findViewById(R.id.tvStreakBadge);
                TextView tvStrength = itemView.findViewById(R.id.tvStrengthScore);
                final Button btnCheckIn = itemView.findViewById(R.id.btnCheckIn);
                LinearLayout layoutMicroHeatmap = itemView.findViewById(R.id.layoutMicroHeatmap);

                final int habitColor = HabitColors.parseColor(habit.colorHex);

                tvTitle.setText(habit.title);
                if (tvDesc != null) {
                    if (habit.description != null && !habit.description.trim().isEmpty()) {
                        tvDesc.setText(habit.description);
                    } else {
                        tvDesc.setVisibility(View.GONE);
                    }
                }

                if (habit.reminderEnabled) {
                    tvReminderBadge.setVisibility(View.VISIBLE);
                    tvReminderBadge.setText("🔔 " + ReminderManager.formatReminderTime(habit.reminderHour, habit.reminderMinute));
                } else {
                    tvReminderBadge.setVisibility(View.GONE);
                }

                tvFreq.setText(habit.targetDaysPerWeek == 7 ? "Daily" : habit.targetDaysPerWeek + "x / week");
                tvStreak.setText("🔥 " + habit.currentStreak + "d");
                tvStrength.setText(HabitMath.formatStrength(habit.strengthScore));
                tvStrength.setTextColor(habitColor);

                // Style Check-In Button
                updateCheckInButtonStyle(btnCheckIn, habit.isCompletedToday, habitColor);

                btnCheckIn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                        boolean newState = dbHelper.toggleCheckIn(habit.uuid, DatabaseHelper.getTodayDateString());
                        habit.isCompletedToday = newState;
                        updateCheckInButtonStyle(btnCheckIn, newState, habitColor);
                        refreshDashboard();
                        triggerAutoSync();
                    }
                });

                // Micro 14-day heatmap dots
                populateMicroHeatmap(layoutMicroHeatmap, habit.thirtyDayHistory, habitColor);

                // Card Click -> Open Detail Activity
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (habitListContainer.isDragging()) return;
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                        intent.putExtra(DetailActivity.EXTRA_HABIT_UUID, habit.uuid);
                        startActivity(intent);
                    }
                });

                // Card Long-Click -> Fluid Drag & Drop Reordering
                itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        return habitListContainer.startDragForChild(v);
                    }
                });

                habitListContainer.addView(itemView);
            }
        }
    }

    private void updateCheckInButtonStyle(Button btn, boolean completed, int habitColor) {
        if (completed) {
            btn.setBackground(HabitColors.createCheckInDoneDrawable(this, habitColor, 10f));
            btn.setText("✓");
            btn.setTextColor(Color.WHITE);
        } else {
            btn.setBackground(HabitColors.createCheckInPendingDrawable(this, habitColor, 10f));
            btn.setText("✓");
            btn.setTextColor(getColor(R.color.text_tertiary));
        }
    }

    private void populateMicroHeatmap(LinearLayout container, boolean[] thirtyDayHistory, int habitColor) {
        container.removeAllViews();
        // Show last 14 days (index 16 to 29)
        int start = Math.max(0, thirtyDayHistory.length - 14);
        float density = getResources().getDisplayMetrics().density;
        int dotSize = (int) (8 * density);
        int dotMargin = (int) (1.5f * density);
        int cornerRadius = (int) (2.5f * density);
        int inactiveColor = getColor(R.color.heatmap_0);

        for (int i = start; i < thirtyDayHistory.length; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.setMargins(dotMargin, 0, dotMargin, 0);
            dot.setLayoutParams(lp);

            boolean done = thirtyDayHistory[i];
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            gd.setCornerRadius(cornerRadius);
            gd.setColor(done ? habitColor : inactiveColor);
            dot.setBackground(gd);
            container.addView(dot);
        }
    }

    private void setThemeMode(int mode) {
        if (themeMode != mode) {
            themeMode = mode;
            SharedPreferences sp = getSharedPreferences("kr_habit_theme", Context.MODE_PRIVATE);
            sp.edit().putInt("theme_mode", themeMode).apply();
            recreate();
        }
    }

    private void cycleTheme() {
        setThemeMode((themeMode + 1) % 3);
    }

    // -------------------------------------------------------------
    // Dialogs: Add Habit, Cloud Sync, Firebase Config, About
    // -------------------------------------------------------------

    private void showAddHabitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_habit, null);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();

        final EditText etTitle = dialogView.findViewById(R.id.etHabitTitle);
        final EditText etDesc = dialogView.findViewById(R.id.etHabitDesc);
        final Switch switchReminder = dialogView.findViewById(R.id.switchReminder);
        final TextView tvReminderLabel = dialogView.findViewById(R.id.tvReminderLabel);
        final Button btnPickTime = dialogView.findViewById(R.id.btnPickReminderTime);
        final LinearLayout layoutColorPicker = dialogView.findViewById(R.id.layoutAddColorPicker);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelAdd);
        Button btnSave = dialogView.findViewById(R.id.btnSaveHabit);

        final int[] reminderTime = new int[]{8, 0}; // [hour, minute]
        final String[] selectedColor = new String[]{HabitColors.getColorForIndex(dbHelper.getNextHabitPosition())};

        if (layoutColorPicker != null) {
            HabitColors.populateColorPicker(MainActivity.this, layoutColorPicker, selectedColor[0], new HabitColors.OnColorSelectedListener() {
                @Override
                public void onColorSelected(String colorHex) {
                    selectedColor[0] = colorHex;
                }
            });
        }

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
                TimePickerDialog tpd = new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        reminderTime[0] = hourOfDay;
                        reminderTime[1] = minute;
                        btnPickTime.setText("🔔 " + ReminderManager.formatReminderTime(hourOfDay, minute));
                    }
                }, reminderTime[0], reminderTime[1], false);
                tpd.show();
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
                String title = etTitle.getText().toString().trim();
                String desc = etDesc.getText().toString().trim();
                boolean reminderOn = switchReminder.isChecked();

                if (title.isEmpty()) {
                    etTitle.setError("Please enter a habit title");
                    return;
                }

                long rowId = dbHelper.addHabit(title, desc, 7, reminderOn, reminderTime[0], reminderTime[1], selectedColor[0]);
                if (reminderOn) {
                    ReminderManager.rescheduleAllReminders(MainActivity.this);
                }
                dialog.dismiss();
                refreshDashboard();
                triggerAutoSync();
                Toast.makeText(MainActivity.this, "Habit created!", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showCloudSyncDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cloud_sync, null);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();

        final TextView tvUserName = dialogView.findViewById(R.id.tvUserName);
        final TextView tvUserEmail = dialogView.findViewById(R.id.tvUserEmail);
        final TextView tvUserAvatar = dialogView.findViewById(R.id.tvUserAvatar);
        final ImageView ivUserAvatar = dialogView.findViewById(R.id.ivUserAvatar);
        final TextView tvSyncStatus = dialogView.findViewById(R.id.tvSyncStatusDetails);
        final View layoutSignedOut = dialogView.findViewById(R.id.layoutSignedOutActions);
        final View layoutSignedIn = dialogView.findViewById(R.id.layoutSignedInActions);
        final Button btnPickDevice = dialogView.findViewById(R.id.btnPickGoogleAccountDevice);
        final EditText etEmail = dialogView.findViewById(R.id.etGoogleAccountEmail);
        final Button btnConnectManual = dialogView.findViewById(R.id.btnConnectManualEmail);
        final Button btnSwitch = dialogView.findViewById(R.id.btnSwitchGoogleAccount);
        final Button btnSignOut = dialogView.findViewById(R.id.btnGoogleSignOut);
        Button btnClose = dialogView.findViewById(R.id.btnCloseSyncDialog);

        final Runnable updateUi = new Runnable() {
            @Override
            public void run() {
                if (syncEngine.isSignedIn()) {
                    layoutSignedOut.setVisibility(View.GONE);
                    layoutSignedIn.setVisibility(View.VISIBLE);
                    tvUserName.setText(syncEngine.getUserName());
                    tvUserEmail.setText(syncEngine.getUserEmail());
                    String name = syncEngine.getUserName();
                    tvUserAvatar.setText(name.length() > 0 ? String.valueOf(name.charAt(0)).toUpperCase(Locale.US) : "👤");
                    tvSyncStatus.setText("● Auto-sync active with Google Cloud\nLast synced: " + syncEngine.getLastSyncTimeFormatted());

                    // Load User's Google Profile Picture
                    final String userEmail = syncEngine.getUserEmail();
                    ProfileImageHelper.loadProfileImage(MainActivity.this, userEmail, ivUserAvatar, new ProfileImageHelper.ImageCallback() {
                        @Override
                        public void onLoaded(Bitmap bitmap) {
                            if (ivUserAvatar != null) {
                                ivUserAvatar.setVisibility(View.VISIBLE);
                            }
                            if (tvUserAvatar != null) {
                                tvUserAvatar.setVisibility(View.GONE);
                            }
                        }
                    });
                } else {
                    layoutSignedOut.setVisibility(View.VISIBLE);
                    layoutSignedIn.setVisibility(View.GONE);
                    tvUserName.setText("Not Signed In");
                    tvUserEmail.setText("Sign in to automatically sync habits with Google");
                    tvUserAvatar.setText("👤");
                    tvUserAvatar.setVisibility(View.VISIBLE);
                    if (ivUserAvatar != null) {
                        ivUserAvatar.setVisibility(View.GONE);
                    }
                    tvSyncStatus.setText("● Offline Mode (Local Storage only)");
                }
            }
        };

        updateUi.run();

        btnPickDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                dialog.dismiss();
                promptAccountSelection();
            }
        });

        btnConnectManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                String email = etEmail.getText().toString().trim();
                if (email.isEmpty()) {
                    email = "eliakimrosilarts@gmail.com";
                }
                syncEngine.signInWithEmail(email);
                Toast.makeText(MainActivity.this, "Connected: " + email, Toast.LENGTH_SHORT).show();
                triggerAutoSync();
                updateUi.run();
                refreshDashboard();
            }
        });

        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                dialog.dismiss();
                promptAccountSelection();
            }
        });

        btnSignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                syncEngine.signOut();
                Toast.makeText(MainActivity.this, "Signed out of Google", Toast.LENGTH_SHORT).show();
                updateUi.run();
                refreshDashboard();
            }
        });

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Dynamic App Version Header
        TextView tvAppVersion = dialogView.findViewById(R.id.tvSettingsAppVersion);
        if (tvAppVersion != null) {
            try {
                String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvAppVersion.setText("KR Habit v" + ver);
            } catch (Exception e) {
                tvAppVersion.setText("KR Habit v1.2.24");
            }
        }

        // 1. Appearance / Theme Controls
        final Button btnAuto = dialogView.findViewById(R.id.btnThemeAuto);
        final Button btnLight = dialogView.findViewById(R.id.btnThemeLight);
        final Button btnDark = dialogView.findViewById(R.id.btnThemeDark);
        updateThemeButtons(btnAuto, btnLight, btnDark);

        btnAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                dialog.dismiss();
                setThemeMode(0);
            }
        });

        btnLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                dialog.dismiss();
                setThemeMode(1);
            }
        });

        btnDark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                dialog.dismiss();
                setThemeMode(2);
            }
        });

        // 2. Google Account & Sync Controls
        final TextView tvUserName = dialogView.findViewById(R.id.tvSettingsUserName);
        final TextView tvUserEmail = dialogView.findViewById(R.id.tvSettingsUserEmail);
        final TextView tvAvatar = dialogView.findViewById(R.id.tvSettingsAvatar);
        final ImageView ivAvatar = dialogView.findViewById(R.id.ivSettingsAvatar);
        final TextView tvSyncStatus = dialogView.findViewById(R.id.tvSettingsSyncStatus);
        final View layoutSignedInActions = dialogView.findViewById(R.id.layoutSettingsSignedInActions);
        final Button btnSignIn = dialogView.findViewById(R.id.btnSettingsSignIn);
        final Button btnSwitch = dialogView.findViewById(R.id.btnSettingsSwitchAccount);
        final Button btnSignOut = dialogView.findViewById(R.id.btnSettingsSignOut);
        Button btnClose = dialogView.findViewById(R.id.btnCloseSettings);
        TextView tvWebLink = dialogView.findViewById(R.id.tvSettingsWebLink);

        final Runnable updateAccountUi = new Runnable() {
            @Override
            public void run() {
                if (syncEngine.isSignedIn()) {
                    layoutSignedInActions.setVisibility(View.VISIBLE);
                    btnSignIn.setVisibility(View.GONE);
                    tvUserName.setText(syncEngine.getUserName());
                    tvUserEmail.setText(syncEngine.getUserEmail());
                    String name = syncEngine.getUserName();
                    tvAvatar.setText(name.length() > 0 ? String.valueOf(name.charAt(0)).toUpperCase(Locale.US) : "👤");
                    tvSyncStatus.setText("● Synced with Google Account (" + syncEngine.getLastSyncTimeFormatted() + ")");

                    // Load User Google Avatar into Settings Modal
                    ProfileImageHelper.loadProfileImage(MainActivity.this, syncEngine.getUserEmail(), ivAvatar, new ProfileImageHelper.ImageCallback() {
                        @Override
                        public void onLoaded(Bitmap bitmap) {
                            if (ivAvatar != null) ivAvatar.setVisibility(View.VISIBLE);
                            if (tvAvatar != null) tvAvatar.setVisibility(View.GONE);
                        }
                    });
                } else {
                    layoutSignedInActions.setVisibility(View.GONE);
                    btnSignIn.setVisibility(View.VISIBLE);
                    tvUserName.setText("Not Signed In");
                    tvUserEmail.setText("Sign in to automatically sync habits with Google");
                    tvAvatar.setText("👤");
                    tvAvatar.setVisibility(View.VISIBLE);
                    if (ivAvatar != null) ivAvatar.setVisibility(View.GONE);
                    tvSyncStatus.setText("● Offline Mode (Local Storage only)");
                }
            }
        };

        updateAccountUi.run();

        btnSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                dialog.dismiss();
                promptAccountSelection();
            }
        });

        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                dialog.dismiss();
                promptAccountSelection();
            }
        });

        btnSignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                syncEngine.signOut();
                Toast.makeText(MainActivity.this, "Signed out of Google", Toast.LENGTH_SHORT).show();
                updateAccountUi.run();
                refreshDashboard();
            }
        });

        if (tvWebLink != null) {
            tvWebLink.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    try {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.kimrosil.com/kr-habit/"));
                        startActivity(browserIntent);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void updateThemeButtons(Button btnAuto, Button btnLight, Button btnDark) {
        Button[] buttons = {btnAuto, btnLight, btnDark};
        for (int i = 0; i < 3; i++) {
            boolean active = (themeMode == i);
            buttons[i].setBackgroundResource(active ? R.drawable.bg_segmented_active : R.drawable.bg_segmented_inactive);
            buttons[i].setTextColor(getColor(active ? R.color.text_primary : R.color.text_secondary));
            buttons[i].setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_about, null);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();

        Button btnClose = dialogView.findViewById(R.id.btnCloseAbout);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}
