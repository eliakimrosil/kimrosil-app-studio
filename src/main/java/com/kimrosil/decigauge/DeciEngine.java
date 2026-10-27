package com.kimrosil.decigauge;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.util.Arrays;

public class DeciEngine {

    public static final int WEIGHTING_A = 0;
    public static final int WEIGHTING_C = 1;
    public static final int WEIGHTING_Z = 2; // Flat

    public static final int RESPONSE_FAST = 0;    // 125 ms
    public static final int RESPONSE_SLOW = 1;    // 1000 ms
    public static final int RESPONSE_IMPULSE = 2; // 35ms rise / 1500ms decay

    public static final int STANDARD_NIOSH = 0; // 85 dB baseline, 3 dB exchange
    public static final int STANDARD_OSHA = 1;  // 90 dB baseline, 5 dB exchange

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 4096;

    public interface Listener {
        void onAcousticUpdate(
                double currentDb,
                double leqDb,
                double maxDb,
                double minDb,
                double peakDb,
                double dosePercent,
                double safeTimeRemainingSec,
                String riskStatus,
                float[] spectrumBands
        );
    }

    private AudioRecord audioRecord;
    private Thread workerThread;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private Listener listener;

    private int weightingMode = WEIGHTING_A;
    private int responseMode = RESPONSE_FAST;
    private int standardMode = STANDARD_NIOSH;
    private double calibrationOffset = 0.0; // dB user trim

    // Filter biquads for A and C weighting
    private final Biquad aFilter1 = new Biquad(0.9979, -1.9958, 0.9979, -1.9958, 0.9958);
    private final Biquad aFilter2 = new Biquad(1.0, 0.0, -1.0, -1.8906, 0.8953);
    private final Biquad aFilter3 = new Biquad(0.3424, 0.6848, 0.3424, -0.1983, 0.5679);

    private final Biquad cFilter1 = new Biquad(0.9979, -1.9958, 0.9979, -1.9958, 0.9958);
    private final Biquad cFilter2 = new Biquad(0.3424, 0.6848, 0.3424, -0.1983, 0.5679);

    // Dynamic measurements
    private double currentSmoothedDb = 40.0;
    private double maxDb = 40.0;
    private double minDb = 120.0;
    private double peakDb = 40.0;
    private double sumEnergy = 0.0;
    private long sampleCount = 0;

    // Dose accumulation
    private double accumulatedDosePercent = 0.0;
    private long sessionStartTime = 0;
    private long lastDoseUpdateTime = 0;

    // 8-band spectrum energy
    private final float[] spectrumBands = new float[8];
    private long lastUiDispatchTime = 0;

    // Synthetic test mode fallback
    private boolean syntheticMode = false;
    private double simPhase = 0.0;

    public DeciEngine() {
        resetMeasurements();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setWeightingMode(int mode) {
        this.weightingMode = mode;
        resetFilters();
    }

    public int getWeightingMode() {
        return weightingMode;
    }

    public void setResponseMode(int mode) {
        this.responseMode = mode;
    }

    public int getResponseMode() {
        return responseMode;
    }

    public void setStandardMode(int mode) {
        this.standardMode = mode;
    }

    public int getStandardMode() {
        return standardMode;
    }

    public void setCalibrationOffset(double offset) {
        this.calibrationOffset = Math.max(-20.0, Math.min(20.0, offset));
    }

    public double getCalibrationOffset() {
        return calibrationOffset;
    }

    public synchronized void resetMeasurements() {
        currentSmoothedDb = 40.0;
        maxDb = 0.0;
        minDb = 140.0;
        peakDb = 0.0;
        sumEnergy = 0.0;
        sampleCount = 0;
        accumulatedDosePercent = 0.0;
        sessionStartTime = SystemClock.elapsedRealtime();
        lastDoseUpdateTime = sessionStartTime;
        Arrays.fill(spectrumBands, 0.05f);
        resetFilters();
    }

    private void resetFilters() {
        aFilter1.reset();
        aFilter2.reset();
        aFilter3.reset();
        cFilter1.reset();
        cFilter2.reset();
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        isPaused = false;
        resetMeasurements();

        workerThread = new Thread(this::audioLoop, "DeciGauge-AudioThread");
        workerThread.setPriority(Thread.MAX_PRIORITY);
        workerThread.start();
    }

    public synchronized void pause() {
        isPaused = true;
    }

    public synchronized void resume() {
        isPaused = false;
        lastDoseUpdateTime = SystemClock.elapsedRealtime();
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public synchronized void stop() {
        isRunning = false;
        if (workerThread != null) {
            try {
                workerThread.join(400);
            } catch (InterruptedException ignored) {}
            workerThread = null;
        }
        releaseAudioRecord();
    }

    private void releaseAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
    }

    private void audioLoop() {
        short[] buffer = new short[BUFFER_SIZE];
        boolean recordInitialized = false;

        try {
            int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(minBuf * 2, BUFFER_SIZE * 2);
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
            );
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                recordInitialized = true;
                syntheticMode = false;
            } else {
                syntheticMode = true;
            }
        } catch (SecurityException | IllegalArgumentException e) {
            syntheticMode = true;
        }

        long lastFrameTime = SystemClock.elapsedRealtime();

        while (isRunning) {
            if (isPaused) {
                SystemClock.sleep(50);
                lastFrameTime = SystemClock.elapsedRealtime();
                continue;
            }

            int readSamples = 0;
            if (!syntheticMode && recordInitialized && audioRecord != null) {
                readSamples = audioRecord.read(buffer, 0, buffer.length);
                if (readSamples <= 0) {
                    SystemClock.sleep(20);
                    continue;
                }
            } else {
                // Synthetic generator (ambient office sound + pink noise drift)
                readSamples = 1024;
                simPhase += 0.08;
                double baseAmp = 300.0 + 200.0 * Math.sin(simPhase * 0.3) + 150.0 * Math.cos(simPhase * 0.7);
                for (int i = 0; i < readSamples; i++) {
                    double noise = (Math.random() - 0.5) * baseAmp;
                    buffer[i] = (short) Math.max(-32767, Math.min(32767, noise));
                }
                SystemClock.sleep(23); // ~44.1kHz rate chunk
            }

            long now = SystemClock.elapsedRealtime();
            double dtSec = (now - lastFrameTime) / 1000.0;
            if (dtSec <= 0 || dtSec > 0.5) dtSec = 0.025;
            lastFrameTime = now;

            // Process audio buffer
            double sumSquare = 0.0;
            double instantPeak = 0.0;

            // Simple 8-band energy bins (approximate octave bands)
            double[] bandEnergies = new double[8];

            for (int i = 0; i < readSamples; i++) {
                double raw = buffer[i] / 32768.0;
                double weighted;

                if (weightingMode == WEIGHTING_A) {
                    weighted = aFilter3.process(aFilter2.process(aFilter1.process(raw))) * 1.259;
                } else if (weightingMode == WEIGHTING_C) {
                    weighted = cFilter2.process(cFilter1.process(raw));
                } else {
                    weighted = raw;
                }

                sumSquare += weighted * weighted;
                double absVal = Math.abs(weighted);
                if (absVal > instantPeak) {
                    instantPeak = absVal;
                }

                // Rough octave distribution based on sample delta/slope
                if (i > 0) {
                    double delta = Math.abs(raw - (buffer[i - 1] / 32768.0));
                    int bandIndex = Math.min(7, (int) (delta * 12.0));
                    bandEnergies[bandIndex] += delta * delta;
                }
            }

            double rms = Math.sqrt(sumSquare / readSamples);
            double dbFs = 20.0 * Math.log10(Math.max(1e-6, rms));

            // Calibration: baseline ~94 dB SPL calibrator produces ~ -26 dBFS on typical Android mic
            double instantDb = dbFs + 94.0 + 26.0 + calibrationOffset;
            instantDb = Math.max(25.0, Math.min(130.0, instantDb));

            // Ballistics smoothing
            double alpha;
            if (responseMode == RESPONSE_FAST) {
                alpha = 1.0 - Math.exp(-dtSec / 0.125);
            } else if (responseMode == RESPONSE_SLOW) {
                alpha = 1.0 - Math.exp(-dtSec / 1.0);
            } else { // Impulse
                if (instantDb > currentSmoothedDb) {
                    alpha = 1.0 - Math.exp(-dtSec / 0.035); // Fast rise
                } else {
                    alpha = 1.0 - Math.exp(-dtSec / 1.5);   // Slow decay
                }
            }

            currentSmoothedDb += alpha * (instantDb - currentSmoothedDb);

            // Peak, Max, Min tracking
            double currentDb = Math.round(currentSmoothedDb * 10.0) / 10.0;
            if (currentDb > maxDb) maxDb = currentDb;
            if (minDb > 130.0 || currentDb < minDb) minDb = currentDb;

            // Fast peak hold with slow decay
            if (currentDb > peakDb) {
                peakDb = currentDb;
            } else {
                peakDb = Math.max(currentDb, peakDb - (0.6 * dtSec));
            }

            // Leq accumulation (Energy average)
            sumEnergy += Math.pow(10.0, currentDb / 10.0);
            sampleCount++;
            double leqDb = 10.0 * Math.log10(sumEnergy / sampleCount);
            leqDb = Math.round(leqDb * 10.0) / 10.0;

            // NIOSH / OSHA Dose Calculation
            double critLevel = (standardMode == STANDARD_NIOSH) ? 85.0 : 90.0;
            double exchangeRate = (standardMode == STANDARD_NIOSH) ? 3.0 : 5.0;

            // Permissible exposure duration T in hours for current level
            double allowedHours = 8.0 / Math.pow(2.0, (currentDb - critLevel) / exchangeRate);
            allowedHours = Math.max(0.001, allowedHours);

            // Incremental dose rate
            double doseRatePerHour = (1.0 / allowedHours) * 100.0;
            accumulatedDosePercent += doseRatePerHour * (dtSec / 3600.0);

            // Remaining safe exposure time in seconds at current level
            double safeTimeSec = Math.max(0.0, (1.0 - (accumulatedDosePercent / 100.0)) * allowedHours * 3600.0);

            // Risk status badge evaluation
            String riskStatus;
            if (currentDb < 75.0) {
                riskStatus = "SAFE";
            } else if (currentDb < 85.0) {
                riskStatus = "CAUTION";
            } else if (currentDb < 90.0) {
                riskStatus = "ACTION REQ";
            } else {
                riskStatus = "HAZARD";
            }

            // Update spectrum bands
            for (int b = 0; b < 8; b++) {
                float norm = (float) Math.min(1.0, Math.sqrt(bandEnergies[b] / (readSamples * 0.05)));
                spectrumBands[b] = spectrumBands[b] * 0.7f + norm * 0.3f;
            }

            // Dispatch update to UI listener (throttled to 10 Hz to allow OS idle state)
            if (listener != null && (now - lastUiDispatchTime >= 100)) {
                lastUiDispatchTime = now;
                listener.onAcousticUpdate(
                        currentDb,
                        leqDb,
                        maxDb,
                        minDb,
                        Math.round(peakDb * 10.0) / 10.0,
                        Math.round(accumulatedDosePercent * 100.0) / 100.0,
                        safeTimeSec,
                        riskStatus,
                        spectrumBands.clone()
                );
            }
        }

        releaseAudioRecord();
    }

    public boolean isSyntheticMode() {
        return syntheticMode;
    }

    // Direct Form II Biquad Filter
    private static class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        public Biquad(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a1 = a1;
            this.a2 = a2;
            reset();
        }

        public double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }

        public void reset() {
            x1 = x2 = y1 = y2 = 0.0;
        }
    }
}
