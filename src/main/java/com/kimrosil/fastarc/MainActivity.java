package com.kimrosil.fastarc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String PREFS_NAME = "fastarc_prefs";
    public static final String KEY_THEME_MODE = "pref_theme_mode"; // 0=Auto, 1=Light, 2=Dark
    public static final String KEY_IS_FASTING = "is_fasting";
    public static final String KEY_START_TIME = "start_time";
    public static final String KEY_TARGET_HOURS = "target_hours";
    public static final String KEY_WATER_ML = "water_ml";

    private SharedPreferences mPrefs;
    private Handler mHandler;
    private Runnable mTimerRunnable;

    // State
    private boolean mIsFasting = false;
    private long mStartTimeMillis = 0;
    private int mTargetHours = 16;
    private int mWaterIntakeMl = 0;
    private int mThemeMode = 0; // 0=Auto, 1=Light, 2=Dark

    // Views
    private Button mBtnThemeToggle;
    private FastArcView mFastArcView;
    private Button mBtnToggleFast;

    // Adjust Card & Chips
    private LinearLayout mCardStartAdjust;
    private TextView mTvCustomTimeBtn;
    private Button mChipNow;
    private Button mChip15m;
    private Button mChip30m;
    private Button mChip1h;
    private Button mChip2h;

    // Telemetry Views
    private TextView mTvStartTime;
    private TextView mTvStartDate;
    private TextView mTvGoalTime;
    private TextView mTvGoalDate;
    private TextView mTvWaterCount;
    private Button mBtnWaterAdd;
    private Button mBtnWaterReset;

    // Stage Rows
    private LinearLayout mRowStage1;
    private LinearLayout mRowStage2;
    private LinearLayout mRowStage3;
    private LinearLayout mRowStage4;

    private final SimpleDateFormat mTimeFmt = new SimpleDateFormat("hh:mm a", Locale.US);
    private final SimpleDateFormat mDateFmt = new SimpleDateFormat("EEE, MMM d", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mThemeMode = mPrefs.getInt(KEY_THEME_MODE, 0);
        applyThemeMode(false);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler(Looper.getMainLooper());

        loadSavedState();
        bindViews();
        setupListeners();

        // Notification Channel setup & Android 13+ permission request
        FastingNotificationManager.createNotificationChannel(this);
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
            }
        }

        updateThemeButtonLabel();
        syncFastArcThemeTokens();
        updateUI();
    }

    private void loadSavedState() {
        mIsFasting = mPrefs.getBoolean(KEY_IS_FASTING, false);
        mStartTimeMillis = mPrefs.getLong(KEY_START_TIME, 0);
        mTargetHours = mPrefs.getInt(KEY_TARGET_HOURS, 16);
        mWaterIntakeMl = mPrefs.getInt(KEY_WATER_ML, 0);

        // Sanity checks on time
        long now = System.currentTimeMillis();
        if (mStartTimeMillis > now) {
            mStartTimeMillis = now;
            mPrefs.edit().putLong(KEY_START_TIME, mStartTimeMillis).apply();
        }
    }

    private void bindViews() {
        mBtnThemeToggle = findViewById(R.id.btn_theme_toggle);
        mFastArcView = findViewById(R.id.fast_arc_view);
        mBtnToggleFast = findViewById(R.id.btn_toggle_fast);

        mCardStartAdjust = findViewById(R.id.card_start_adjust);
        mTvCustomTimeBtn = findViewById(R.id.tv_custom_time_btn);
        mChipNow = findViewById(R.id.chip_adjust_now);
        mChip15m = findViewById(R.id.chip_adjust_15m);
        mChip30m = findViewById(R.id.chip_adjust_30m);
        mChip1h = findViewById(R.id.chip_adjust_1h);
        mChip2h = findViewById(R.id.chip_adjust_2h);

        mTvStartTime = findViewById(R.id.tv_telemetry_start_time);
        mTvStartDate = findViewById(R.id.tv_telemetry_start_date);
        mTvGoalTime = findViewById(R.id.tv_telemetry_goal_time);
        mTvGoalDate = findViewById(R.id.tv_telemetry_goal_date);
        mTvWaterCount = findViewById(R.id.tv_water_count);
        mBtnWaterAdd = findViewById(R.id.btn_water_add);
        mBtnWaterReset = findViewById(R.id.btn_water_reset);

        mRowStage1 = findViewById(R.id.row_stage_1);
        mRowStage2 = findViewById(R.id.row_stage_2);
        mRowStage3 = findViewById(R.id.row_stage_3);
        mRowStage4 = findViewById(R.id.row_stage_4);
    }

    private void setupListeners() {
        mBtnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleThemeMode();
            }
        });

        mBtnToggleFast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleFastToggle();
            }
        });

        // Quick Adjust Chips
        mChipNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustStartTime(0);
            }
        });
        mChip15m.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustStartTime(15);
            }
        });
        mChip30m.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustStartTime(30);
            }
        });
        mChip1h.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustStartTime(60);
            }
        });
        mChip2h.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustStartTime(120);
            }
        });

        mTvCustomTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCustomTimePicker();
            }
        });

        // Hydration
        mBtnWaterAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                mWaterIntakeMl += 250;
                mPrefs.edit().putInt(KEY_WATER_ML, mWaterIntakeMl).apply();
                updateUI();
                syncWidgetAndNotification();
            }
        });

        mBtnWaterReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                mWaterIntakeMl = 0;
                mPrefs.edit().putInt(KEY_WATER_ML, 0).apply();
                updateUI();
                syncWidgetAndNotification();
            }
        });
    }

    private void handleFastToggle() {
        mBtnToggleFast.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        if (!mIsFasting) {
            // Start Fast
            mIsFasting = true;
            mStartTimeMillis = System.currentTimeMillis();
            mPrefs.edit()
                    .putBoolean(KEY_IS_FASTING, true)
                    .putLong(KEY_START_TIME, mStartTimeMillis)
                    .apply();

            Toast.makeText(this, "16:8 Fast Started!", Toast.LENGTH_SHORT).show();
        } else {
            // Confirm End Fast
            new AlertDialog.Builder(this)
                    .setTitle("End Fast & Open Window?")
                    .setMessage("Great job! Ready to break your fast and begin your 8-hour eating window?")
                    .setPositiveButton("End Fast", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mIsFasting = false;
                            mPrefs.edit()
                                    .putBoolean(KEY_IS_FASTING, false)
                                    .apply();
                            updateUI();
                            syncWidgetAndNotification();
                            Toast.makeText(MainActivity.this, "Fast Completed! Eating Window Open.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Keep Fasting", null)
                    .show();
            return;
        }

        updateUI();
        syncWidgetAndNotification();
    }

    private void adjustStartTime(int minutesAgo) {
        if (!mIsFasting) {
            Toast.makeText(this, "Start the fast first to adjust time", Toast.LENGTH_SHORT).show();
            return;
        }
        mStartTimeMillis = System.currentTimeMillis() - (minutesAgo * 60L * 1000L);
        mPrefs.edit().putLong(KEY_START_TIME, mStartTimeMillis).apply();
        updateUI();
        syncWidgetAndNotification();
        Toast.makeText(this, "Start time set to " + (minutesAgo == 0 ? "now" : (minutesAgo + "m ago")), Toast.LENGTH_SHORT).show();
    }

    private void openCustomTimePicker() {
        if (!mIsFasting) {
            Toast.makeText(this, "Start the fast first to set time", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar cal = Calendar.getInstance();
        if (mStartTimeMillis > 0) {
            cal.setTimeInMillis(mStartTimeMillis);
        }

        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                Calendar chosen = Calendar.getInstance();
                chosen.set(Calendar.HOUR_OF_DAY, hourOfDay);
                chosen.set(Calendar.MINUTE, minute);
                chosen.set(Calendar.SECOND, 0);
                chosen.set(Calendar.MILLISECOND, 0);

                long now = System.currentTimeMillis();
                // If chosen time is ahead of now, it must have been yesterday
                if (chosen.getTimeInMillis() > now) {
                    chosen.add(Calendar.DAY_OF_YEAR, -1);
                }

                mStartTimeMillis = chosen.getTimeInMillis();
                mPrefs.edit().putLong(KEY_START_TIME, mStartTimeMillis).apply();
                updateUI();
                syncWidgetAndNotification();
                Toast.makeText(MainActivity.this, "Start time updated!", Toast.LENGTH_SHORT).show();
            }
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
    }

    private void updateUI() {
        long targetMillis = mTargetHours * 3600L * 1000L;
        long now = System.currentTimeMillis();
        long elapsed = 0;
        if (mIsFasting && mStartTimeMillis > 0) {
            elapsed = Math.max(0, now - mStartTimeMillis);
        }

        long elapsedHours = elapsed / (3600L * 1000L);
        int stageIndex;
        String stageName;
        int stageColor;

        boolean isDark = isDarkThemeActive();
        if (elapsedHours < 4) {
            stageIndex = 0;
            stageName = "Digestion & Sugar Drop";
            stageColor = isDark ? 0xFF38BDF8 : 0xFF0284C7;
        } else if (elapsedHours < 12) {
            stageIndex = 1;
            stageName = "Glycogen Depletion";
            stageColor = isDark ? 0xFF4ADE80 : 0xFF16A34A;
        } else if (elapsedHours < 16) {
            stageIndex = 2;
            stageName = "Ketosis & Fat Oxidation";
            stageColor = isDark ? 0xFFFBBF24 : 0xFFD97706;
        } else {
            stageIndex = 3;
            stageName = "Autophagy Threshold";
            stageColor = isDark ? 0xFFA78BFA : 0xFF7C3AED;
        }

        // FastArcView Custom Rendering
        mFastArcView.updateData(mIsFasting, elapsed, targetMillis, stageIndex, stageName, stageColor);

        // Action Button
        if (!mIsFasting) {
            mBtnToggleFast.setText(R.string.btn_start_fast);
            mBtnToggleFast.setBackgroundResource(R.drawable.btn_fast_primary);
            mCardStartAdjust.setVisibility(View.GONE);
        } else {
            mBtnToggleFast.setText(R.string.btn_end_fast);
            mBtnToggleFast.setBackgroundResource(R.drawable.btn_fast_stop);
            mCardStartAdjust.setVisibility(View.VISIBLE);
        }

        // Telemetry Timestamps
        if (mIsFasting && mStartTimeMillis > 0) {
            Date startDate = new Date(mStartTimeMillis);
            Date goalDate = new Date(mStartTimeMillis + targetMillis);

            mTvStartTime.setText(mTimeFmt.format(startDate));
            mTvStartDate.setText(mDateFmt.format(startDate));

            mTvGoalTime.setText(mTimeFmt.format(goalDate));
            mTvGoalDate.setText(mDateFmt.format(goalDate));
        } else {
            mTvStartTime.setText("--:--");
            mTvStartDate.setText("Not Fasting");
            mTvGoalTime.setText("--:--");
            mTvGoalDate.setText("16h Goal Target");
        }

        // Water
        mTvWaterCount.setText(String.format(Locale.US, "%,d ml", mWaterIntakeMl));

        // Highlight Active Stage
        highlightStageRow(mRowStage1, stageIndex == 0 && mIsFasting);
        highlightStageRow(mRowStage2, stageIndex == 1 && mIsFasting);
        highlightStageRow(mRowStage3, stageIndex == 2 && mIsFasting);
        highlightStageRow(mRowStage4, stageIndex == 3 && mIsFasting);
    }

    private void highlightStageRow(LinearLayout row, boolean active) {
        if (active) {
            row.setAlpha(1.0f);
            row.setBackgroundColor(isDarkThemeActive() ? 0x2210B981 : 0x1816A34A);
        } else {
            row.setAlpha(0.65f);
            row.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void syncWidgetAndNotification() {
        // Widget
        Intent updateIntent = new Intent(this, FastingWidgetProvider.class);
        updateIntent.setAction(FastingWidgetProvider.ACTION_UPDATE_WIDGET);
        sendBroadcast(updateIntent);

        // Lock-screen Notification
        FastingNotificationManager.showOrUpdateNotification(
                this, mIsFasting, mStartTimeMillis, mTargetHours, mWaterIntakeMl
        );
    }

    // =========================================================================
    // Theme Management
    // =========================================================================

    private void cycleThemeMode() {
        mThemeMode = (mThemeMode + 1) % 3;
        mPrefs.edit().putInt(KEY_THEME_MODE, mThemeMode).apply();
        applyThemeMode(true);
    }

    private void applyThemeMode(boolean recreateIfChanged) {
        if (recreateIfChanged) {
            recreate();
        }
    }

    private boolean isDarkThemeActive() {
        if (mThemeMode == 2) return true;
        if (mThemeMode == 1) return false;
        int nightFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void updateThemeButtonLabel() {
        if (mThemeMode == 1) {
            mBtnThemeToggle.setText("Theme: Light");
        } else if (mThemeMode == 2) {
            mBtnThemeToggle.setText("Theme: Dark");
        } else {
            mBtnThemeToggle.setText("Theme: Auto");
        }
    }

    private void syncFastArcThemeTokens() {
        boolean isDark = isDarkThemeActive();
        int primary = isDark ? 0xFFA3E635 : 0xFF4D7C0F;
        int textPrimary = isDark ? 0xFFF4F4F5 : 0xFF09090B;
        int textSecondary = isDark ? 0xFFA1A1AA : 0xFF52525B;
        int trackBase = isDark ? 0xFF18181B : 0xFFE4E4E7;
        int trackEating = isDark ? 0xFF3F2010 : 0xFFFED7AA;
        int stageColor = isDark ? 0xFF4ADE80 : 0xFF16A34A;

        mFastArcView.setThemeTokens(primary, textPrimary, textSecondary, trackBase, trackEating, stageColor);
    }

    // =========================================================================
    // Lifecycle Management
    // =========================================================================

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedState();
        updateUI();
        startTimerLoop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimerLoop();
    }

    private void startTimerLoop() {
        stopTimerLoop();
        mTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (mIsFasting) {
                    updateUI();
                }
                mHandler.postDelayed(this, 1000);
            }
        };
        mHandler.postDelayed(mTimerRunnable, 1000);
    }

    private void stopTimerLoop() {
        if (mTimerRunnable != null) {
            mHandler.removeCallbacks(mTimerRunnable);
            mTimerRunnable = null;
        }
    }
}
