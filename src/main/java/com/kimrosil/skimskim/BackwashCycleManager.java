package com.kimrosil.skimskim;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.CountDownTimer;
import android.os.Vibrator;

public class BackwashCycleManager {

    public enum Stage {
        IDLE,
        BACKWASH,
        RINSE,
        RECHARGE,
        COMPLETED
    }

    public interface CycleListener {
        void onTick(Stage stage, int remainingSeconds, int totalSeconds);
        void onStageChanged(Stage newStage);
        void onCycleComplete();
    }

    private final Context context;
    private final CycleListener listener;
    private Stage currentStage = Stage.IDLE;
    private CountDownTimer countDownTimer;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;

    private int backwashDuration = 120; // 120s standard
    private int rinseDuration = 30;      // 30s standard
    private int currentRemainingSeconds = 0;
    private boolean isPaused = false;
    private boolean isMuted = false;

    public BackwashCycleManager(Context context, CycleListener listener) {
        this.context = context;
        this.listener = listener;
        try {
            this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            this.toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
        } catch (Exception ignored) {
        }
    }

    public void setDurations(int bwSec, int rinseSec) {
        this.backwashDuration = Math.max(15, Math.min(300, bwSec));
        this.rinseDuration = Math.max(10, Math.min(180, rinseSec));
    }

    public Stage getCurrentStage() {
        return currentStage;
    }

    public int getRemainingSeconds() {
        return currentRemainingSeconds;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void startCycle() {
        startStage(Stage.BACKWASH, backwashDuration);
    }

    public void togglePause() {
        if (currentStage == Stage.IDLE || currentStage == Stage.COMPLETED) {
            startCycle();
            return;
        }
        if (isPaused) {
            resumeTimer();
        } else {
            pauseTimer();
        }
    }

    public void skipCurrentStage() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        advanceNextStage();
    }

    public void addSeconds(int secondsToAdd) {
        if (currentStage == Stage.BACKWASH || currentStage == Stage.RINSE) {
            currentRemainingSeconds = Math.max(5, Math.min(400, currentRemainingSeconds + secondsToAdd));
            if (!isPaused) {
                if (countDownTimer != null) countDownTimer.cancel();
                startInternalTimer(currentRemainingSeconds, getCurrentStageDuration());
            } else {
                if (listener != null) {
                    listener.onTick(currentStage, currentRemainingSeconds, getCurrentStageDuration());
                }
            }
        }
    }

    public void reset() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        currentStage = Stage.IDLE;
        currentRemainingSeconds = backwashDuration;
        isPaused = false;
        if (listener != null) {
            listener.onStageChanged(Stage.IDLE);
            listener.onTick(Stage.IDLE, backwashDuration, backwashDuration);
        }
    }

    private int getCurrentStageDuration() {
        return (currentStage == Stage.BACKWASH) ? backwashDuration : rinseDuration;
    }

    private void startStage(final Stage stage, final int durationSeconds) {
        this.currentStage = stage;
        this.currentRemainingSeconds = durationSeconds;
        this.isPaused = false;

        playStageAlert();
        if (listener != null) {
            listener.onStageChanged(stage);
        }
        startInternalTimer(durationSeconds, durationSeconds);
    }

    private void startInternalTimer(int durationSeconds, final int totalSeconds) {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(durationSeconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                currentRemainingSeconds = (int) Math.round(millisUntilFinished / 1000.0);
                if (listener != null) {
                    listener.onTick(currentStage, currentRemainingSeconds, totalSeconds);
                }
                // Tactile 10s heartbeat tick for wet hand sensory feedback
                if (currentRemainingSeconds > 0 && currentRemainingSeconds % 10 == 0) {
                    performSubtleTick();
                }
            }

            @Override
            public void onFinish() {
                currentRemainingSeconds = 0;
                advanceNextStage();
            }
        }.start();
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isPaused = true;
    }

    private void resumeTimer() {
        if (currentRemainingSeconds > 0) {
            isPaused = false;
            startInternalTimer(currentRemainingSeconds, getCurrentStageDuration());
        }
    }

    private void advanceNextStage() {
        if (currentStage == Stage.BACKWASH) {
            startStage(Stage.RINSE, rinseDuration);
        } else if (currentStage == Stage.RINSE) {
            currentStage = Stage.RECHARGE;
            playCompletionAlarm();
            if (listener != null) {
                listener.onStageChanged(Stage.RECHARGE);
                listener.onCycleComplete();
            }
        }
    }

    private void performSubtleTick() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(35);
        }
    }

    private void playStageAlert() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(150);
        }
        if (!isMuted && toneGenerator != null) {
            try {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            } catch (Exception ignored) {
            }
        }
    }

    private void playCompletionAlarm() {
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 200, 100, 200, 100, 350};
            vibrator.vibrate(pattern, -1);
        }
        if (!isMuted && toneGenerator != null) {
            try {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 400);
            } catch (Exception ignored) {
            }
        }
    }

    public void release() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}