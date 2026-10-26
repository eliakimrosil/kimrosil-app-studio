package com.kimrosil.brixscale;

import java.util.Locale;

public class BrixEngine {

    public static class BrewResult {
        public double doseGrams;
        public double brewRatio;
        public double totalWaterGrams;
        public double retentionRatio;
        public double beverageYieldGrams;
        public double tdsPercent;
        public double brixDegrees;
        public double dissolvedSolidsGrams;
        public double extractionYieldPercent;
        public double bypassWaterGrams;

        public double stage1Grams;
        public double stage2Grams;
        public double stage3Grams;
        public double stage4Grams;
        public int stage1Pct;
        public int stage2Pct;
        public int stage3Pct;
        public int stage4Pct;

        public String zoneLabel;
        public String dialInAdvice;
        public int zoneStatus; // 0 = Ideal Emerald, 1 = Under Amber, 2 = Over Ruby
    }

    /**
     * Defensive parser that handles NaN, Infinity, multiple decimals, symbols, and extreme exponents.
     */
    public static double parseSafeDouble(String raw, double fallback, double minVal, double maxVal) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.contains("nan") || lower.contains("inf")) {
            return fallback;
        }

        // Keep only digits, first decimal point, and leading minus
        StringBuilder cleaned = new StringBuilder();
        boolean seenDot = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') {
                cleaned.append(c);
            } else if (c == '.' && !seenDot) {
                cleaned.append('.');
                seenDot = true;
            } else if (c == '-' && cleaned.length() == 0) {
                cleaned.append('-');
            }
        }

        String cleanStr = cleaned.toString();
        if (cleanStr.isEmpty() || cleanStr.equals("-") || cleanStr.equals(".") || cleanStr.equals("-.")) {
            return fallback;
        }

        try {
            double val = Double.parseDouble(cleanStr);
            if (Double.isNaN(val) || Double.isInfinite(val)) {
                return fallback;
            }
            if (val < minVal) {
                return minVal;
            }
            if (val > maxVal) {
                return maxVal;
            }
            return val;
        } catch ( Exception e) {
            return fallback;
        }
    }

    public static BrewResult compute(double rawDose, double rawRatio, double rawTds, double rawRetention) {
        BrewResult r = new BrewResult();

        r.doseGrams = clamp(rawDose, 5.0, 120.0, 18.0);
        r.brewRatio = clamp(rawRatio, 1.0, 30.0, 16.0);
        r.tdsPercent = clamp(rawTds, 0.50, 14.0, 1.38);
        r.retentionRatio = clamp(rawRetention, 0.5, 3.5, 2.0);

        r.totalWaterGrams = r.doseGrams * r.brewRatio;
        double retainedGrams = r.doseGrams * r.retentionRatio;
        r.beverageYieldGrams = Math.max(r.doseGrams * 0.85, r.totalWaterGrams - retainedGrams);

        r.dissolvedSolidsGrams = r.beverageYieldGrams * (r.tdsPercent / 100.0);
        double rawEy = (r.dissolvedSolidsGrams / r.doseGrams) * 100.0;
        r.extractionYieldPercent = clamp(rawEy, 1.0, 34.0, 20.0);

        // Refractometer Brix conversion: Sucrose Brix ~ TDS / 0.85
        r.brixDegrees = clamp(r.tdsPercent / 0.85, 0.1, 22.0, 1.62);

        // Bypass water needed to dilute filter coffee to 1.25% TDS
        if (r.tdsPercent > 1.25 && r.brewRatio >= 6.0) {
            r.bypassWaterGrams = clamp(r.beverageYieldGrams * ((r.tdsPercent / 1.25) - 1.0), 0.0, 2000.0, 0.0);
        } else {
            r.bypassWaterGrams = 0.0;
        }

        // 4-Stage Pour Schedule
        double bloomWater = Math.min(r.totalWaterGrams * 0.25, r.doseGrams * 2.5);
        if (r.brewRatio < 6.0) {
            // Espresso / Ristretto ratios
            bloomWater = r.totalWaterGrams * 0.20;
        }
        r.stage1Grams = bloomWater;
        r.stage2Grams = r.totalWaterGrams * 0.40;
        r.stage3Grams = r.totalWaterGrams * 0.70;
        r.stage4Grams = r.totalWaterGrams;

        r.stage1Pct = (int) Math.round((r.stage1Grams / Math.max(1.0, r.totalWaterGrams)) * 100.0);
        r.stage2Pct = 40;
        r.stage3Pct = 70;
        r.stage4Pct = 100;

        // Zone classification & Dial-In guidance (never containing "nan" or "infinity")
        if (r.extractionYieldPercent >= 18.0 && r.extractionYieldPercent <= 22.0) {
            r.zoneStatus = 0;
            r.zoneLabel = String.format(Locale.US, "SCA GOLD CUP (%.1f%%)", r.extractionYieldPercent);
            if (r.tdsPercent > 1.50 && r.brewRatio >= 8.0) {
                r.dialInAdvice = "Ideal Yield • High Strength";
            } else if (r.tdsPercent < 1.15 && r.brewRatio >= 8.0) {
                r.dialInAdvice = "Ideal Yield • Light Body";
            } else {
                r.dialInAdvice = "Sweet Spot Locked";
            }
        } else if (r.extractionYieldPercent < 18.0) {
            r.zoneStatus = 1;
            r.zoneLabel = String.format(Locale.US, "UNDER-EXT (%.1f%%)", r.extractionYieldPercent);
            r.dialInAdvice = "Grind Finer or Add Water";
        } else {
            r.zoneStatus = 2;
            r.zoneLabel = String.format(Locale.US, "OVER-EXT (%.1f%%)", r.extractionYieldPercent);
            r.dialInAdvice = "Grind Coarser or Cooler";
        }

        return r;
    }

    private static double clamp(double val, double min, double max, double fallback) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return fallback;
        }
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }
}
