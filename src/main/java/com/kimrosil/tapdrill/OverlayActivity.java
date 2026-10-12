package com.kimrosil.tapdrill;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class OverlayActivity extends Activity {

    private String mStandard = "METRIC";
    private List<MachiningStandardsRepository.ThreadSpec> mSpecs;
    private int mCurrentIndex = 0;

    private Button mBtnOverStdMetric;
    private Button mBtnOverStdUnc;
    private Button mBtnPrev;
    private Button mBtnNext;
    private TextView mTvOverNominal;
    private TextView mTvOverPitch;
    private TextView mTvOverDrill75;
    private TextView mTvOverDrill50;
    private TextView mTvOverFractional;
    private TextView mTvOverClearance;
    private Button mBtnOverCopy;
    private Button mBtnOverClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_overlay);

        // Make window look like a floating HUD
        getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        mBtnOverStdMetric = findViewById(R.id.btnOverStdMetric);
        mBtnOverStdUnc = findViewById(R.id.btnOverStdUnc);
        mBtnPrev = findViewById(R.id.btnOverPrev);
        mBtnNext = findViewById(R.id.btnOverNext);
        mTvOverNominal = findViewById(R.id.tvOverNominal);
        mTvOverPitch = findViewById(R.id.tvOverPitch);
        mTvOverDrill75 = findViewById(R.id.tvOverDrill75);
        mTvOverDrill50 = findViewById(R.id.tvOverDrill50);
        mTvOverFractional = findViewById(R.id.tvOverFractional);
        mTvOverClearance = findViewById(R.id.tvOverClearance);
        mBtnOverCopy = findViewById(R.id.btnOverCopy);
        mBtnOverClose = findViewById(R.id.btnOverClose);

        String initStd = getIntent().getStringExtra("EXTRA_STANDARD");
        if (initStd != null && !initStd.isEmpty()) {
            mStandard = initStd;
        }

        setupButtons();
        switchStandard(mStandard);
    }

    private void setupButtons() {
        mBtnOverStdMetric.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            switchStandard("METRIC");
        });

        mBtnOverStdUnc.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            switchStandard("UNC_UNF");
        });

        mBtnPrev.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mSpecs != null && !mSpecs.isEmpty()) {
                mCurrentIndex = (mCurrentIndex - 1 + mSpecs.size()) % mSpecs.size();
                renderSpec();
            }
        });

        mBtnNext.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (mSpecs != null && !mSpecs.isEmpty()) {
                mCurrentIndex = (mCurrentIndex + 1) % mSpecs.size();
                renderSpec();
            }
        });

        mBtnOverCopy.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            if (mSpecs != null && mCurrentIndex < mSpecs.size()) {
                MachiningStandardsRepository.ThreadSpec s = mSpecs.get(mCurrentIndex);
                String clip = String.format(Locale.US, "%s -> Tap Drill 75%%: .%04d\" (Frac: %s, Wire: %s)",
                        s.nominalDisplay, (int) Math.round(s.tapDrill75Inch * 10000), s.nearestFractional, s.wireLetterGauge);
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("TapDrill HUD", clip));
                    Toast.makeText(this, "Copied HUD Data", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mBtnOverClose.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            finish();
        });
    }

    private void switchStandard(String std) {
        mStandard = std;
        mSpecs = MachiningStandardsRepository.getThreads(std, "");
        mCurrentIndex = 0;

        boolean isMetric = "METRIC".equals(std);
        mBtnOverStdMetric.setBackgroundResource(isMetric ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
        mBtnOverStdMetric.setTextColor(getColor(isMetric ? R.color.m3_on_primary : R.color.m3_on_surface));

        mBtnOverStdUnc.setBackgroundResource(!isMetric ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
        mBtnOverStdUnc.setTextColor(getColor(!isMetric ? R.color.m3_on_primary : R.color.m3_on_surface));

        renderSpec();
    }

    private void renderSpec() {
        if (mSpecs == null || mSpecs.isEmpty()) return;
        MachiningStandardsRepository.ThreadSpec s = mSpecs.get(mCurrentIndex);

        mTvOverNominal.setText(s.nominalDisplay);
        mTvOverPitch.setText(s.pitchDisplay);

        double d75mm = s.tapDrill75Inch * 25.4;
        double d50mm = s.tapDrill50Inch * 25.4;

        if (s.isMetric) {
            mTvOverDrill75.setText(String.format(Locale.US, "Ø %.2f mm", d75mm));
            mTvOverDrill50.setText(String.format(Locale.US, "Ø %.2f mm", d50mm));
        } else {
            mTvOverDrill75.setText(String.format(Locale.US, ".%04d\"", (int) Math.round(s.tapDrill75Inch * 10000)));
            mTvOverDrill50.setText(String.format(Locale.US, ".%04d\"", (int) Math.round(s.tapDrill50Inch * 10000)));
        }

        mTvOverFractional.setText(String.format(Locale.US, "%s / %s", s.nearestFractional, s.wireLetterGauge));
        mTvOverClearance.setText(String.format(Locale.US, "Close: %s", s.clearanceClose));
    }
}