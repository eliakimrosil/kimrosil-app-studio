package com.kimrosil.pebbledrift;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.Random;

/**
 * Pure Java Procedural Sound Engine for PebbleDrift.
 * Synthesizes peaceful Tibetan singing bowl overtones, water droplet glides,
 * and stone taps natively using Android AudioTrack without any external audio files.
 */
public class ZenChimeSynthesizer {

    private static final String TAG = "ZenChimeSynthesizer";
    private static final int SAMPLE_RATE = 22050; // High efficiency, low latency

    // Serene Japanese Akebono / Pentatonic Scale Frequencies (Hz)
    // C4, D4, Eb4, G4, Ab4, C5, D5, Eb5, G5, C6
    private static final float[] PENTATONIC_FREQS = {
            261.63f, // C4 (Deep bowl)
            293.66f, // D4
            311.13f, // Eb4
            392.00f, // G4 (Harmonic center)
            415.30f, // Ab4
            523.25f, // C5
            587.33f, // D5
            622.25f, // Eb5
            783.99f, // G5 (Bright chime)
            1046.50f // C6 (Crystal bell)
    };

    private boolean isMuted = false;
    private final Random random = new Random();
    private HandlerThread audioThread;
    private Handler audioHandler;

    public ZenChimeSynthesizer() {
        startAudioThread();
    }

    private void startAudioThread() {
        try {
            audioThread = new HandlerThread("PebbleAudioWorker");
            audioThread.start();
            audioHandler = new Handler(audioThread.getLooper());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize audio thread", e);
        }
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void toggleMute() {
        this.isMuted = !this.isMuted;
    }

    /**
     * Plays a resonant singing bowl / meditation chime at a random pentatonic pitch.
     */
    public void playRandomChime() {
        if (isMuted) return;
        int noteIndex = random.nextInt(PENTATONIC_FREQS.length);
        playChimeNote(PENTATONIC_FREQS[noteIndex], 1.4f);
    }

    /**
     * Plays a resonant chime at a specific scale step (0 to 9).
     */
    public void playScaleStep(int step) {
        if (isMuted) return;
        int index = Math.max(0, Math.min(step, PENTATONIC_FREQS.length - 1));
        playChimeNote(PENTATONIC_FREQS[index], 1.6f);
    }

    /**
     * Plays a delicate water droplet 'plop' with gentle pitch glide.
     */
    public void playWaterDrop() {
        if (isMuted || audioHandler == null) return;
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                generateAndPlayWaterDrop();
            }
        });
    }

    /**
     * Plays a soft natural stone 'clack' when pebbles balance or tap.
     */
    public void playStoneTap() {
        if (isMuted || audioHandler == null) return;
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                generateAndPlayStoneTap();
            }
        });
    }

    /**
     * Synthesizes and plays a dual-overtone meditation chime.
     */
    private void playChimeNote(final float baseFreq, final float durationSec) {
        if (isMuted || audioHandler == null) return;
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                generateAndPlayChime(baseFreq, durationSec);
            }
        });
    }

    private void generateAndPlayChime(float freq, float durationSec) {
        int numSamples = (int) (SAMPLE_RATE * durationSec);
        short[] buffer = new short[numSamples];

        float overtoneFreq = freq * 2.76f; // Natural bell/bowl non-harmonic overtone
        float decayRate = 3.5f / durationSec;

        for (int i = 0; i < numSamples; i++) {
            float t = (float) i / SAMPLE_RATE;
            // Exponential decay envelope
            float envelope = (float) Math.exp(-decayRate * t);

            // Fundamental tone + subtle shimmering overtone
            float sample = (float) (Math.sin(2.0 * Math.PI * freq * t) * 0.75f +
                    Math.sin(2.0 * Math.PI * overtoneFreq * t) * 0.25f);

            // Subtle bell attack smoothing (5ms fade-in)
            if (t < 0.005f) {
                envelope *= (t / 0.005f);
            }

            buffer[i] = (short) (sample * envelope * 24000);
        }

        playRawPcm(buffer);
    }

    private void generateAndPlayWaterDrop() {
        float duration = 0.18f;
        int numSamples = (int) (SAMPLE_RATE * duration);
        short[] buffer = new short[numSamples];

        float startFreq = 480.0f + (random.nextFloat() * 140.0f);
        float endFreq = startFreq * 0.75f; // Gentle downward pitch glide

        for (int i = 0; i < numSamples; i++) {
            float t = (float) i / SAMPLE_RATE;
            float currentFreq = startFreq + (endFreq - startFreq) * (t / duration);
            float envelope = (float) Math.exp(-14.0 * t);

            float sample = (float) Math.sin(2.0 * Math.PI * currentFreq * t);
            buffer[i] = (short) (sample * envelope * 20000);
        }

        playRawPcm(buffer);
    }

    private void generateAndPlayStoneTap() {
        float duration = 0.12f;
        int numSamples = (int) (SAMPLE_RATE * duration);
        short[] buffer = new short[numSamples];

        float freq = 220.0f + (random.nextFloat() * 60.0f);

        for (int i = 0; i < numSamples; i++) {
            float t = (float) i / SAMPLE_RATE;
            float envelope = (float) Math.exp(-28.0 * t); // Very fast percussive decay
            float noise = (random.nextFloat() * 2.0f - 1.0f) * 0.3f;
            float tone = (float) Math.sin(2.0 * Math.PI * freq * t) * 0.7f;

            buffer[i] = (short) ((tone + noise) * envelope * 22000);
        }

        playRawPcm(buffer);
    }

    private void playRawPcm(short[] pcmData) {
        AudioTrack track = null;
        try {
            int bufferSize = pcmData.length * 2;
            track = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STATIC
            );

            if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                track.write(pcmData, 0, pcmData.length);
                track.play();
                // Let the track play to completion before auto-releasing
                int sleepMs = (pcmData.length * 1000) / SAMPLE_RATE + 20;
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "AudioTrack playback error", e);
        } finally {
            if (track != null) {
                try {
                    track.stop();
                    track.release();
                } catch (Exception ignored) {}
            }
        }
    }

    public void release() {
        if (audioThread != null) {
            audioThread.quitSafely();
            audioThread = null;
            audioHandler = null;
        }
    }
}
