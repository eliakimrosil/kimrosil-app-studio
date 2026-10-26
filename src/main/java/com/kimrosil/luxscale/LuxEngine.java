package com.kimrosil.luxscale;

import java.util.Locale;

/**
 * Pure Native Photometric, Plant PAR (PPFD / DLI), and Cine EV100 Calculation Engine.
 * Hardened against NaN, Infinity, malformed decimals, negatives, and extreme overflow inputs.
 */
public final class LuxEngine {

    public static final int SPECTRUM_DAYLIGHT_6500K = 0;
    public static final int SPECTRUM_WARM_LED_3500K = 1;
    public static final int SPECTRUM_GROW_PAR_LED = 2;

    // McCree / Apogee empirical conversion factors (µmol/m²/s per Lux)
    private static final double FACTOR_DAYLIGHT_6500K = 0.0185;
    private static final double FACTOR_WARM_LED_3500K = 0.0156;
    private static final double FACTOR_GROW_PAR_LED = 0.0218;

    private LuxEngine() {
    }

    public static final class LuxTelemetry {
        public final double rawLux;
        public final double calFactor;
        public final double calibratedLux;
        public final double footCandles;
        public final double ppfd;
        public final double dli;
        public final double ev100;
        public final double targetLux;
        public final double targetPercent;
        public final String spectrumLabel;
        public final String zoneBadge;
        public final String diagnosisText;

        public LuxTelemetry(
                double rawLux,
                double calFactor,
                double calibratedLux,
                double footCandles,
                double ppfd,
                double dli,
                double ev100,
                double targetLux,
                double targetPercent,
                String spectrumLabel,
                String zoneBadge,
                String diagnosisText) {
            this.rawLux = rawLux;
            this.calFactor = calFactor;
            this.calibratedLux = calibratedLux;
            this.footCandles = footCandles;
            this.ppfd = ppfd;
            this.dli = dli;
            this.ev100 = ev100;
            this.targetLux = targetLux;
            this.targetPercent = targetPercent;
            this.spectrumLabel = spectrumLabel;
            this.zoneBadge = zoneBadge;
            this.diagnosisText = diagnosisText;
        }
    }

    /**
     * Parses arbitrary user or fuzzer input safely into a bounded finite double.
     */
    public static double parseSafeDouble(String raw, double fallback, double min, double max) {
        if (raw == null) {
            return clamp(fallback, min, max);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return clamp(fallback, min, max);
        }
        StringBuilder clean = new StringBuilder(trimmed.length());
        boolean seenDot = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') {
                clean.append(c);
            } else if ((c == '.' || c == ',') && !seenDot) {
                clean.append('.');
                seenDot = true;
            }
        }
        if (clean.length() == 0 || ".".equals(clean.toString())) {
            return clamp(fallback, min, max);
        }
        try {
            double val = Double.parseDouble(clean.toString());
            if (Double.isNaN(val) || Double.isInfinite(val)) {
                return clamp(fallback, min, max);
            }
            return clamp(val, min, max);
        } catch (NumberFormatException e) {
            return clamp(fallback, min, max);
        }
    }

    public static double clamp(double val, double min, double max) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return min;
        }
        if (val < min) {
            return min;
        }
        if (val > max) {
            return max;
        }
        return val;
    }

    public static double getSpectrumFactor(int spectrumMode) {
        switch (spectrumMode) {
            case SPECTRUM_WARM_LED_3500K:
                return FACTOR_WARM_LED_3500K;
            case SPECTRUM_GROW_PAR_LED:
                return FACTOR_GROW_PAR_LED;
            case SPECTRUM_DAYLIGHT_6500K:
            default:
                return FACTOR_DAYLIGHT_6500K;
        }
    }

    public static String getSpectrumName(int spectrumMode) {
        switch (spectrumMode) {
            case SPECTRUM_WARM_LED_3500K:
                return "3500K WARM LED";
            case SPECTRUM_GROW_PAR_LED:
                return "GROW PAR LED";
            case SPECTRUM_DAYLIGHT_6500K:
            default:
                return "6500K DAYLIGHT";
        }
    }

    public static LuxTelemetry evaluate(
            double baseLuxInput,
            double calMultiplierInput,
            double photoperiodHoursInput,
            double targetBenchmarkLuxInput,
            int spectrumMode) {

        double safeRawLux = clamp(baseLuxInput, 0.0, 200000.0);
        double safeCal = clamp(calMultiplierInput, 0.05, 100.0);
        double safeHours = clamp(photoperiodHoursInput, 0.5, 24.0);
        double safeTarget = clamp(targetBenchmarkLuxInput, 10.0, 100000.0);

        double calibratedLux = clamp(safeRawLux * safeCal, 0.0, 500000.0);
        double footCandles = calibratedLux / 10.7639104;

        double factor = getSpectrumFactor(spectrumMode);
        double ppfd = calibratedLux * factor;
        double dli = (ppfd * 3600.0 * safeHours) / 1000000.0;

        double ev100 = 0.0;
        if (calibratedLux > 0.25) {
            ev100 = clamp(Math.log(calibratedLux / 2.5) / Math.log(2.0), -4.0, 22.0);
        }

        double targetPercent = clamp((calibratedLux / safeTarget) * 100.0, 0.0, 999.0);
        String zoneBadge = classifyZoneBadge(calibratedLux);
        String diagnosis = buildDiagnosis(calibratedLux, ppfd, dli, safeHours, safeTarget, targetPercent);

        return new LuxTelemetry(
                safeRawLux,
                safeCal,
                calibratedLux,
                footCandles,
                ppfd,
                dli,
                ev100,
                safeTarget,
                targetPercent,
                getSpectrumName(spectrumMode),
                zoneBadge,
                diagnosis
        );
    }

    private static String classifyZoneBadge(double lux) {
        if (lux < 100.0) {
            return "DIM AMBIENT";
        } else if (lux < 350.0) {
            return "HOME INDOOR";
        } else if (lux < 1000.0) {
            return "DESK OPTIMAL";
        } else if (lux < 5000.0) {
            return "FOLIAGE PAR";
        } else if (lux < 15000.0) {
            return "CANOPY HIGH";
        } else {
            return "FULL SOLAR";
        }
    }

    private static String buildDiagnosis(
            double lux,
            double ppfd,
            double dli,
            double hours,
            double targetLux,
            double targetPct) {
        String advice;
        if (lux < 150.0) {
            advice = "Low ambient level. Add task lamp for desk work or supplemental grow diode for plants.";
        } else if (lux < 750.0) {
            advice = "Meets IES office & studio desk comfort. Suitable for shade-tolerant foliage.";
        } else if (lux < 4000.0) {
            advice = "Strong indoor brightness. Ideal for aroids, ferns, and broadcast key lighting.";
        } else if (lux < 15000.0) {
            advice = "High vegetative PAR flux. Supports dense culinary herbs, leafy greens, and seedlings.";
        } else {
            advice = "Direct solar / high-output array flux. Supports fruiting crops and high-speed shutter.";
        }
        return String.format(
                Locale.US,
                "Target %.0f lx (%.0f%%) • PPFD %.1f µmol/s • DLI %.2f mol/m²/d (%.1fh)\n%s",
                targetLux,
                targetPct,
                ppfd,
                dli,
                hours,
                advice
        );
    }
}
