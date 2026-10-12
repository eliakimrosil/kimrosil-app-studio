package com.kimrosil.flowstep;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * FlowStep Main Coordinator Activity.
 * Single-screen Tunnel Vision, Stray Thought Buffer, and Lifetime Momentum Ledger.
 */
public class MainActivity extends Activity {

    public static final String PREFS_NAME = "flowstep_settings";
    public static final String PREF_THEME_MODE = "theme_mode"; // 0: Auto, 1: Light, 2: Dark

    private DatabaseHelper dbHelper;
    private TaskAtomizerEngine atomizerEngine;

    // Header & Tabs
    private Button btnHeaderTheme;
    private Button btnHeaderSettings;
    private Button tabBtnFocus;
    private Button tabBtnThoughts;
    private Button tabBtnMomentum;

    // Primary Container Views
    private LinearLayout layoutFocus;
    private LinearLayout layoutThoughts;
    private LinearLayout layoutMomentum;

    // Focus: Intake Section
    private LinearLayout sectionIntake;
    private EditText etGoalInput;
    private Button btnAtomizeGoal;
    private TextView tvAtomizerMode;
    private Button chipDesk;
    private Button chipBills;
    private Button chipEmail;
    private Button chipLaundry;
    private Button chipWorkout;

    // Focus: Tunnel Vision Section
    private LinearLayout sectionTunnelVision;
    private TextView tvActiveGoalTitle;
    private Button btnPauseSession;
    private VisualPacerView visualPacerView;
    private TextView tvActionVerbBadge;
    private TextView tvStepInstruction;
    private TextView tvStepProgressInfo;
    private Button btnCompleteStep;
    private Button btnBreakDown;

    // Thoughts Tab Views
    private EditText etDirectThoughtInput;
    private Button btnDirectParkThought;
    private LinearLayout containerThoughtsList;

    // Momentum Tab Views
    private TextView tvMetricSteps;
    private TextView tvMetricGoals;
    private TextView tvMetricThoughts;

    // Bottom Quick Action
    private Button btnQuickParkThought;

    // Active Task State
    private DatabaseHelper.Goal currentGoal = null;
    private DatabaseHelper.MicroStep currentStep = null;
    private int currentTotalSteps = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0); // Default Auto
        int nightMode = Configuration.UI_MODE_NIGHT_NO;

        if (themeMode == 0) { // Auto
            int systemNight = 0;
            try {
                systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", 0);
            } catch (Exception ignored) {
            }
            if (systemNight == 2) {
                nightMode = Configuration.UI_MODE_NIGHT_YES;
            } else {
                int currentNight = newBase.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                nightMode = (currentNight == Configuration.UI_MODE_NIGHT_YES) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
            }
        } else if (themeMode == 2) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        } else {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        }

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        atomizerEngine = new TaskAtomizerEngine(this);

        initViews();
        setupHeaderAndTheme();
        setupTabs();
        setupIntakeActions();
        setupTunnelVisionActions();
        setupThoughtBufferActions();

        // Render active task state on startup
        renderActiveTaskState();
    }

    private void initViews() {
        btnHeaderTheme = findViewById(R.id.btnHeaderTheme);
        btnHeaderSettings = findViewById(R.id.btnHeaderSettings);
        tabBtnFocus = findViewById(R.id.tabBtnFocus);
        tabBtnThoughts = findViewById(R.id.tabBtnThoughts);
        tabBtnMomentum = findViewById(R.id.tabBtnMomentum);

        layoutFocus = findViewById(R.id.layoutFocus);
        layoutThoughts = findViewById(R.id.layoutThoughts);
        layoutMomentum = findViewById(R.id.layoutMomentum);

        sectionIntake = findViewById(R.id.sectionIntake);
        etGoalInput = findViewById(R.id.etGoalInput);
        btnAtomizeGoal = findViewById(R.id.btnAtomizeGoal);
        tvAtomizerMode = findViewById(R.id.tvAtomizerMode);
        chipDesk = findViewById(R.id.chipDesk);
        chipBills = findViewById(R.id.chipBills);
        chipEmail = findViewById(R.id.chipEmail);
        chipLaundry = findViewById(R.id.chipLaundry);
        chipWorkout = findViewById(R.id.chipWorkout);

        sectionTunnelVision = findViewById(R.id.sectionTunnelVision);
        tvActiveGoalTitle = findViewById(R.id.tvActiveGoalTitle);
        btnPauseSession = findViewById(R.id.btnPauseSession);
        visualPacerView = findViewById(R.id.visualPacerView);
        tvActionVerbBadge = findViewById(R.id.tvActionVerbBadge);
        tvStepInstruction = findViewById(R.id.tvStepInstruction);
        tvStepProgressInfo = findViewById(R.id.tvStepProgressInfo);
        btnCompleteStep = findViewById(R.id.btnCompleteStep);
        btnBreakDown = findViewById(R.id.btnBreakDown);

        etDirectThoughtInput = findViewById(R.id.etDirectThoughtInput);
        btnDirectParkThought = findViewById(R.id.btnDirectParkThought);
        containerThoughtsList = findViewById(R.id.containerThoughtsList);

        tvMetricSteps = findViewById(R.id.tvMetricSteps);
        tvMetricGoals = findViewById(R.id.tvMetricGoals);
        tvMetricThoughts = findViewById(R.id.tvMetricThoughts);

        btnQuickParkThought = findViewById(R.id.btnQuickParkThought);
    }

    private void setupHeaderAndTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);
        updateThemeButtonLabel(themeMode);

        btnHeaderTheme.setOnClickListener(v -> {
            HapticFeedbackUtil.performClickFeedback(v);
            int current = prefs.getInt(PREF_THEME_MODE, 0);
            int next = (current + 1) % 3;
            prefs.edit().putInt(PREF_THEME_MODE, next).apply();
            recreate();
        });

        btnHeaderSettings.setOnClickListener(v -> {
            HapticFeedbackUtil.performClickFeedback(v);
            showSettingsDialog();
        });

        // Configure system bar contrast on Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                if (!isNight) {
                    controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                } else {
                    controller.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
        }
    }

    private void updateThemeButtonLabel(int mode) {
        if (mode == 1) {
            btnHeaderTheme.setText("☀");
        } else if (mode == 2) {
            btnHeaderTheme.setText("🌙");
        } else {
            btnHeaderTheme.setText("◐");
        }
    }

    private void setupTabs() {
        tabBtnFocus.setOnClickListener(v -> {
            HapticFeedbackUtil.performClickFeedback(v);
            selectTab(0);
        });

        tabBtnThoughts.setOnClickListener(v -> {
            HapticFeedbackUtil.performClickFeedback(v);
            selectTab(1);
        });

        tabBtnMomentum.setOnClickListener(v -> {
            HapticFeedbackUtil.performClickFeedback(v);
            selectTab(2);
        });
    }

    private void selectTab(int tabIndex) {
        layoutFocus.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        layoutThoughts.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        layoutMomentum.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        tabBtnFocus.setBackgroundResource(tabIndex == 0 ? R.drawable.chip_active : R.drawable.chip_inactive);
        tabBtnFocus.setTextColor(getResources().getColor(tabIndex == 0 ? R.color.textPrimary : R.color.textSecondary));

        tabBtnThoughts.setBackgroundResource(tabIndex == 1 ? R.drawable.chip_active : R.drawable.chip_inactive);
        tabBtnThoughts.setTextColor(getResources().getColor(tabIndex == 1 ? R.color.textPrimary : R.color.textSecondary));

        tabBtnMomentum.setBackgroundResource(tabIndex == 2 ? R.drawable.chip_active : R.drawable.chip_inactive);
        tabBtnMomentum.setTextColor(getResources().getColor(tabIndex == 2 ? R.color.textPrimary : R.color.textSecondary));

        if (tabIndex == 1) {
            renderParkedThoughtsList();
        } else if (tabIndex == 2) {
            renderMomentumLedger();
        }
    }

    private void setupIntakeActions() {
        View.OnClickListener chipListener = v -> {
            HapticFeedbackUtil.performClickFeedback(v);
            Button b = (Button) v;
            etGoalInput.setText(b.getText().toString());
            etGoalInput.setSelection(etGoalInput.getText().length());
        };

        chipDesk.setOnClickListener(chipListener);
        chipBills.setOnClickListener(chipListener);
        chipEmail.setOnClickListener(chipListener);
        chipLaundry.setOnClickListener(chipListener);
        chipWorkout.setOnClickListener(chipListener);

        btnAtomizeGoal.setOnClickListener(v -> {
            HapticFeedbackUtil.performClickFeedback(v);
            String input = etGoalInput.getText().toString().trim();
            if (input.isEmpty()) {
                etGoalInput.setError("Please enter a task to begin");
                return;
            }

            btnAtomizeGoal.setEnabled(false);
            btnAtomizeGoal.setText("Atomizing into physical steps...");

            atomizerEngine.atomize(input, (steps, usedCloud) -> {
                btnAtomizeGoal.setEnabled(true);
                btnAtomizeGoal.setText(R.string.atomize_action);

                if (steps == null || steps.isEmpty()) {
                    Toast.makeText(this, "Could not break down task. Please try another.", Toast.LENGTH_SHORT).show();
                    return;
                }

                dbHelper.createGoalWithSteps(input, steps);
                etGoalInput.setText("");
                HapticFeedbackUtil.performStepComplete(this);
                renderActiveTaskState();
            });
        });

        // Update atomizer status notice
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String apiKey = prefs.getString(TaskAtomizerEngine.PREF_GEMINI_API_KEY, "").trim();
        if (!apiKey.isEmpty()) {
            tvAtomizerMode.setText("✨ Gemini 3.8 Flash Cloud Bridge Configured");
        } else {
            tvAtomizerMode.setText("⚡ 100% Offline Rule Decomposer Active");
        }
    }

    private void setupTunnelVisionActions() {
        btnCompleteStep.setOnClickListener(v -> {
            if (currentStep == null || currentGoal == null) return;
            HapticFeedbackUtil.performClickFeedback(v);

            boolean goalFinished = dbHelper.completeStep(currentStep.id, currentGoal.id);
            if (goalFinished) {
                HapticFeedbackUtil.performGoalComplete(this);
                Toast.makeText(this, "🎉 Goal Conquered! Lifetime win logged.", Toast.LENGTH_LONG).show();
            } else {
                HapticFeedbackUtil.performStepComplete(this);
            }
            renderActiveTaskState();
        });

        btnBreakDown.setOnClickListener(v -> {
            if (currentStep == null || currentGoal == null) return;
            HapticFeedbackUtil.performClickFeedback(v);

            atomizerEngine.subdivide(currentStep.instruction, (sub1, sub2) -> {
                dbHelper.subdivideStep(currentStep.id, currentGoal.id, sub1, sub2);
                HapticFeedbackUtil.performClickFeedback(v);
                Toast.makeText(this, "Step simplified into smaller actions.", Toast.LENGTH_SHORT).show();
                renderActiveTaskState();
            });
        });

        btnPauseSession.setOnClickListener(v -> {
            if (currentGoal == null) return;
            HapticFeedbackUtil.performClickFeedback(v);
            dbHelper.pauseGoal(currentGoal.id);
            Toast.makeText(this, "Task paused. Ready whenever you are.", Toast.LENGTH_SHORT).show();
            renderActiveTaskState();
        });
    }

    private void setupThoughtBufferActions() {
        btnQuickParkThought.setOnClickListener(v -> {
            HapticFeedbackUtil.performClickFeedback(v);
            showRapidThoughtDialog();
        });

        btnDirectParkThought.setOnClickListener(v -> {
            String text = etDirectThoughtInput.getText().toString().trim();
            if (text.isEmpty()) return;
            HapticFeedbackUtil.performClickFeedback(v);
            dbHelper.addStrayThought(text);
            etDirectThoughtInput.setText("");
            HapticFeedbackUtil.performParkThought(this);
            renderParkedThoughtsList();
        });
    }

    private void renderActiveTaskState() {
        currentGoal = dbHelper.getActiveGoal();
        if (currentGoal == null) {
            sectionTunnelVision.setVisibility(View.GONE);
            sectionIntake.setVisibility(View.VISIBLE);
            return;
        }

        List<DatabaseHelper.MicroStep> allSteps = dbHelper.getStepsForGoal(currentGoal.id);
        currentTotalSteps = allSteps.size();
        currentStep = dbHelper.getNextActiveStep(currentGoal.id);

        if (currentStep == null) {
            // All steps completed
            sectionTunnelVision.setVisibility(View.GONE);
            sectionIntake.setVisibility(View.VISIBLE);
            return;
        }

        // Render strict Tunnel-Vision
        sectionIntake.setVisibility(View.GONE);
        sectionTunnelVision.setVisibility(View.VISIBLE);

        tvActiveGoalTitle.setText(currentGoal.title);
        visualPacerView.setStepProgress(currentStep.stepOrder, currentTotalSteps);

        String verb = currentStep.actionVerb != null ? currentStep.actionVerb.toUpperCase(Locale.US) : "ACT";
        tvActionVerbBadge.setText(verb);
        tvStepInstruction.setText(currentStep.instruction);
        tvStepProgressInfo.setText("Step " + currentStep.stepOrder + " of " + currentTotalSteps + " • " + currentStep.estimatedMinutes + " min physical action");
    }

    private void renderParkedThoughtsList() {
        containerThoughtsList.removeAllViews();
        List<DatabaseHelper.StrayThought> thoughts = dbHelper.getStrayThoughts();

        if (thoughts.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No parked thoughts. Working memory is clean.");
            emptyTv.setTextColor(getResources().getColor(R.color.textSecondary));
            emptyTv.setTextSize(14);
            emptyTv.setPadding(0, 24, 0, 0);
            containerThoughtsList.addView(emptyTv);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());

        for (DatabaseHelper.StrayThought t : thoughts) {
            View itemView = inflater.inflate(R.layout.item_stray_thought, containerThoughtsList, false);
            TextView tvText = itemView.findViewById(R.id.tvThoughtText);
            TextView tvTime = itemView.findViewById(R.id.tvThoughtTime);
            Button btnToggle = itemView.findViewById(R.id.btnToggleResolved);
            Button btnDelete = itemView.findViewById(R.id.btnDeleteThought);

            tvText.setText(t.content);
            tvTime.setText(sdf.format(new Date(t.createdAt)));

            if (t.isResolved) {
                tvText.setPaintFlags(tvText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvText.setAlpha(0.5f);
                btnToggle.setText("↩");
            } else {
                tvText.setPaintFlags(tvText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                tvText.setAlpha(1.0f);
                btnToggle.setText("✓");
            }

            btnToggle.setOnClickListener(v -> {
                HapticFeedbackUtil.performClickFeedback(v);
                dbHelper.toggleThoughtResolved(t.id, !t.isResolved);
                renderParkedThoughtsList();
            });

            btnDelete.setOnClickListener(v -> {
                HapticFeedbackUtil.performClickFeedback(v);
                dbHelper.deleteStrayThought(t.id);
                renderParkedThoughtsList();
            });

            containerThoughtsList.addView(itemView);
        }
    }

    private void renderMomentumLedger() {
        DatabaseHelper.LifetimeStats stats = dbHelper.getLifetimeStats();
        tvMetricSteps.setText(String.valueOf(stats.totalStepsCompleted));
        tvMetricGoals.setText(String.valueOf(stats.totalGoalsCompleted));
        tvMetricThoughts.setText(String.valueOf(stats.totalThoughtsParked));
    }

    private void showRapidThoughtDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_stray_thought, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        EditText etInput = dialogView.findViewById(R.id.etThoughtInput);
        Button btnSave = dialogView.findViewById(R.id.btnSaveThought);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelThought);

        btnSave.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                dbHelper.addStrayThought(text);
                HapticFeedbackUtil.performParkThought(this);
                Toast.makeText(this, "Thought parked safely.", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        EditText etApiKey = view.findViewById(R.id.etApiKey);
        Button btnSaveKey = view.findViewById(R.id.btnSaveApiKey);
        Button btnClearKey = view.findViewById(R.id.btnClearApiKey);
        Button btnAuto = view.findViewById(R.id.btnThemeAuto);
        Button btnLight = view.findViewById(R.id.btnThemeLight);
        Button btnDark = view.findViewById(R.id.btnThemeDark);
        Button btnDone = view.findViewById(R.id.btnCloseSettings);

        String currentKey = prefs.getString(TaskAtomizerEngine.PREF_GEMINI_API_KEY, "");
        etApiKey.setText(currentKey);

        int curTheme = prefs.getInt(PREF_THEME_MODE, 0);
        btnAuto.setBackgroundResource(curTheme == 0 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnLight.setBackgroundResource(curTheme == 1 ? R.drawable.chip_active : R.drawable.chip_inactive);
        btnDark.setBackgroundResource(curTheme == 2 ? R.drawable.chip_active : R.drawable.chip_inactive);

        View.OnClickListener themeClick = v -> {
            int newTheme = 0;
            if (v == btnLight) newTheme = 1;
            else if (v == btnDark) newTheme = 2;
            prefs.edit().putInt(PREF_THEME_MODE, newTheme).apply();
            dialog.dismiss();
            recreate();
        };
        btnAuto.setOnClickListener(themeClick);
        btnLight.setOnClickListener(themeClick);
        btnDark.setOnClickListener(themeClick);

        btnSaveKey.setOnClickListener(v -> {
            String newKey = etApiKey.getText().toString().trim();
            prefs.edit().putString(TaskAtomizerEngine.PREF_GEMINI_API_KEY, newKey).apply();
            Toast.makeText(this, "API Key saved. Cloud bridge active.", Toast.LENGTH_SHORT).show();
            tvAtomizerMode.setText(!newKey.isEmpty() ? "✨ Gemini 3.8 Flash Cloud Bridge Configured" : "⚡ 100% Offline Rule Decomposer Active");
            dialog.dismiss();
        });

        btnClearKey.setOnClickListener(v -> {
            prefs.edit().remove(TaskAtomizerEngine.PREF_GEMINI_API_KEY).apply();
            etApiKey.setText("");
            Toast.makeText(this, "Reverted to 100% Offline Rule Decomposer.", Toast.LENGTH_SHORT).show();
            tvAtomizerMode.setText("⚡ 100% Offline Rule Decomposer Active");
            dialog.dismiss();
        });

        btnDone.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
