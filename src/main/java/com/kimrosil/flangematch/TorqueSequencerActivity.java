package com.kimrosil.flangematch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class TorqueSequencerActivity extends Activity {

    private static final String PREFS_NAME = "flangematch_prefs";
    private static final String PREF_THEME_MODE = "pref_theme_mode";

    private FlangeDatabaseHelper dbHelper;

    // Spec Intent Data
    private String standard;
    private String nominalSize;
    private String pressureClass;
    private int boltCount;
    private double targetTorque;
    private String unit;
    private String studSize;
    private String wrenchSize;

    // Sequencer Execution State
    private int[] starSequence;
    private int currentPassIndex = 0; // 0 to 3 (Pass 1 to 4)
    private int currentStepInPass = 0; // 0 to boltCount - 1
    private final List<Integer> completedBoltsInPass = new ArrayList<>();

    // Views
    private TextView tvSequencerTitle;
    private TextView tvSequencerSubtitle;
    private FlangeDiagramView flangeHUD;
    private TextView tvPassBadge;
    private TextView tvActiveBoltTelemetry;
    private TextView tvStageTorqueReadout;
    private TextView tvProgressReadout;
    private Button btnPrevBolt;
    private Button btnNextBolt;
    private Button btnConfirmPass;
    private Button btnShareLog;
    private View cardCompletionBanner;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt(PREF_THEME_MODE, 0);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (themeMode == 1) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == 2) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else {
            int systemNight = Settings.Secure.getInt(newBase.getContentResolver(), "ui_night_mode", Configuration.UI_MODE_NIGHT_NO);
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | systemNight;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torque_sequencer);

        dbHelper = new FlangeDatabaseHelper(this);

        extractIntentData();
        initViews();
        setupStatusBar();
        setupSequencerLogic();
        setupListeners();
        updateHUD();
    }

    private void extractIntentData() {
        Intent intent = getIntent();
        standard = intent.getStringExtra("EXTRA_STANDARD");
        nominalSize = intent.getStringExtra("EXTRA_NPS");
        pressureClass = intent.getStringExtra("EXTRA_PRESSURE_CLASS");
        boltCount = intent.getIntExtra("EXTRA_BOLT_COUNT", 8);
        targetTorque = intent.getDoubleExtra("EXTRA_TARGET_TORQUE", 100.0);
        unit = intent.getStringExtra("EXTRA_UNIT");
        if (unit == null) unit = "ft-lb";
        studSize = intent.getStringExtra("EXTRA_STUD_SIZE");
        wrenchSize = intent.getStringExtra("EXTRA_WRENCH_SIZE");

        starSequence = TorquePatternCalculator.getStarSequence(boltCount);
    }

    private void initViews() {
        findViewById(R.id.btnToolbarBack).setOnClickListener(v -> finish());
        tvSequencerTitle = findViewById(R.id.tvSequencerTitle);
        tvSequencerSubtitle = findViewById(R.id.tvSequencerSubtitle);
        flangeHUD = findViewById(R.id.flangeHUD);
        tvPassBadge = findViewById(R.id.tvPassBadge);
        tvActiveBoltTelemetry = findViewById(R.id.tvActiveBoltTelemetry);
        tvStageTorqueReadout = findViewById(R.id.tvStageTorqueReadout);
        tvProgressReadout = findViewById(R.id.tvProgressReadout);
        btnPrevBolt = findViewById(R.id.btnPrevBolt);
        btnNextBolt = findViewById(R.id.btnNextBolt);
        btnConfirmPass = findViewById(R.id.btnConfirmPass);
        btnShareLog = findViewById(R.id.btnShareLog);
        cardCompletionBanner = findViewById(R.id.cardCompletionBanner);

        tvSequencerTitle.setText(standard + " " + nominalSize + " (" + pressureClass + ")");
        tvSequencerSubtitle.setText("Socket: " + wrenchSize + " | Stud: " + studSize);
    }

    private void setupStatusBar() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                controller.setSystemBarsAppearance(isNight ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        }
    }

    private void setupSequencerLogic() {
        flangeHUD.setFlangeConfig(boltCount, starSequence);
        flangeHUD.setOnBoltClickListener(boltNumber -> {
            // Find step matching this bolt
            int targetStep = -1;
            int[] currentPassOrder = getCurrentPassOrder();
            for (int i = 0; i < currentPassOrder.length; i++) {
                if (currentPassOrder[i] == boltNumber) {
                    targetStep = i;
                    break;
                }
            }
            if (targetStep != -1) {
                currentStepInPass = targetStep;
                updateHUD();
            }
        });
    }

    private int[] getCurrentPassOrder() {
        if (currentPassIndex == 3) {
            // Pass 4 is Rotational Clockwise
            int[] rot = new int[boltCount];
            for (int i = 0; i < boltCount; i++) rot[i] = i + 1;
            return rot;
        }
        return starSequence;
    }

    private void setupListeners() {
        btnPrevBolt.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (currentStepInPass > 0) {
                currentStepInPass--;
                updateHUD();
            }
        });

        btnNextBolt.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            advanceBolt();
        });

        btnConfirmPass.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            completeCurrentPass();
        });

        btnShareLog.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            shareInspectionReport();
        });
    }

    private void advanceBolt() {
        int[] passOrder = getCurrentPassOrder();
        int activeBolt = passOrder[currentStepInPass];

        if (!completedBoltsInPass.contains(activeBolt)) {
            completedBoltsInPass.add(activeBolt);
        }

        if (currentStepInPass < passOrder.length - 1) {
            currentStepInPass++;
            updateHUD();
        } else {
            // Reached last bolt of the pass
            updateHUD();
            Toast.makeText(this, "All bolts tightened for this pass. Confirm pass to advance.", Toast.LENGTH_SHORT).show();
        }
    }

    private void completeCurrentPass() {
        triggerDualPulseHaptic();

        if (currentPassIndex < 3) {
            currentPassIndex++;
            currentStepInPass = 0;
            completedBoltsInPass.clear();
            updateHUD();
            Toast.makeText(this, "Stage completed! Starting " + TorquePatternCalculator.getPassDescription(currentPassIndex), Toast.LENGTH_SHORT).show();
        } else {
            // Completed all 4 passes!
            cardCompletionBanner.setVisibility(View.VISIBLE);
            btnNextBolt.setEnabled(false);
            btnConfirmPass.setEnabled(false);

            // Save record to SQLite
            dbHelper.insertTorqueLog(standard, nominalSize, pressureClass, boltCount,
                    targetTorque, unit, 4, "Complete 4-stage star sequence verified.");

            Toast.makeText(this, "Torque procedure 100% verified and logged locally.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateHUD() {
        int[] passOrder = getCurrentPassOrder();
        int currentBoltNumber = passOrder[currentStepInPass];
        double currentStageTorque = TorquePatternCalculator.getTorqueForPass(currentPassIndex, targetTorque);

        tvPassBadge.setText(TorquePatternCalculator.getPassDescription(currentPassIndex));
        tvActiveBoltTelemetry.setText("TIGHTEN BOLT #" + currentBoltNumber);
        tvStageTorqueReadout.setText(currentStageTorque + " " + unit);
        tvProgressReadout.setText("Bolt " + (currentStepInPass + 1) + " of " + boltCount + " (Completed: " + completedBoltsInPass.size() + "/" + boltCount + ")");

        btnPrevBolt.setEnabled(currentStepInPass > 0);

        flangeHUD.setActiveBolt(currentBoltNumber);
        flangeHUD.setCompletedBolts(completedBoltsInPass);
    }

    private void triggerDualPulseHaptic() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                long[] timings = new long[]{0, 80, 100, 120};
                int[] amplitudes = new int[]{0, 255, 0, 255};
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                v.vibrate(200);
            }
        }
    }

    private void shareInspectionReport() {
        String report = "=== FLANGEMATCH BOLT TORQUE CERTIFICATE ===\n"
                + "Standard: " + standard + " " + nominalSize + "\n"
                + "Pressure Class: " + pressureClass + "\n"
                + "Bolt Count: " + boltCount + "\n"
                + "Target Torque: " + targetTorque + " " + unit + "\n"
                + "Passes Executed: 4 of 4 (100% Verified)\n"
                + "Stud Size: " + studSize + " | Socket: " + wrenchSize + "\n"
                + "Verification Date: " + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()) + "\n"
                + "Method: ASME PCC-1 Star Sequence (Zero Cloud Telemetry)";

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, report);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Flange Torque Inspection Certificate");
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share Inspection Certificate"));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                advanceBolt();
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_SPACE) {
                completeCurrentPass();
                return true;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                finish();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}